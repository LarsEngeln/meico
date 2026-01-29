package meico.mei;

import nu.xom.Element;
import nu.xom.Elements;

import java.util.*;

public class MeiInstructifier {
    public Mei mei;
    private Map<String, Instruction> instructions = new HashMap<String, Instruction>(); // ornament's startid to instruction
    private Map<String, List<Integer>> ornamentLookup = new HashMap<String, List<Integer>>();

    private ArrayList<String> prevOrnams = new ArrayList<String>(); // already instructified ornams with that are "previous" to another ornam
    private Map<String, Element> nextOrnams = new HashMap<String, Element>(); // prevId to nextElement - remember "next" ornament to be processed, if "prev"-Id have not been processed yet - so, if "prev"/"next" is not well sorted in the MEI

    public MeiInstructifier(Mei mei) {
        createOrnamentLookUp();
    }

    public MeiInstructifier() {
        createOrnamentLookUp();
    }

    /**
     * creates the lookUp table with ornament descriptions. Names for lookUp are identical to getOrnamentFullName results
     */
    private void createOrnamentLookUp() {
        ornamentLookup.put("trill", Arrays.asList(0, 1, 0, 1));
        ornamentLookup.put("turn", Arrays.asList(1, 0, -1, 0));
        ornamentLookup.put("upper turn", Arrays.asList(1, 0, -1, 0));
        ornamentLookup.put("lower turn", Arrays.asList(-1, 0, 1, 0));
        ornamentLookup.put("mordent", Arrays.asList(0, 1, 0));
        ornamentLookup.put("upper mordent", Arrays.asList(0, 1, 0));
        ornamentLookup.put("lower mordent", Arrays.asList(0, -1, 0));
        ornamentLookup.put("trill with mordent", Arrays.asList(1, 0, 1, 0, 1, 0, -1, 0));
        ornamentLookup.put("double cadence lower prefix", Arrays.asList(-1, 0, 1, 0, 1, 0));
    }

    /**
     * adds instructive readings for ornaments
     * @param mei the MEI to be instructified
     * @param display decides whether to add a <lem> with a through-composed reading, or just adding a reading with graceNotes for the audio rendering
     * @result the instructive MEI
     */
    public Mei instructify(Mei mei, boolean display) {
        if (mei == null) {
            System.out.println("\nThe provided MEI object is null and cannot be instructified.");
            return null;
        }

        long startTime = System.currentTimeMillis();                            // we measure the time that the conversion consumes
        System.out.println("\nInstructifying " + ((mei.getFile() != null) ? mei.getFile().getName() : "MEI data") + ".");

        this.mei = mei;

        if (this.mei.isEmpty() || (this.mei.getMusic() == null) || (this.mei.getMusic().getFirstChildElement("body", this.mei.getMusic().getNamespaceURI()) == null))      // if no mei music data available
            return mei;

        Elements bodies = this.mei.getMusic().getChildElements("body", this.mei.getMusic().getNamespaceURI());  // get the list of body elements in the mei source
        for (int b = 0; b < bodies.size(); ++b)                                 // for each body
            this.instructify(bodies.get(b));                                        // convert each body to msm, the resulting Msms can then be found in this.movements

        for(Element element : nextOrnams.values()){  // do not forget someone, should never happen if MEI is well-defined
            this.instructifyElement(element);
        }

        System.out.println("MEI instructification finished. Time consumed: " + (System.currentTimeMillis() - startTime) + " milliseconds");

        return mei;
    }

    /**
     * iterates the MEI XML tree to identify supported elements to get instructified
     * @param root
     */
    private void instructify(Element root) {
        Elements es = root.getChildElements();                                  // all child elements of root

        for (int i = 0; i < es.size(); ++i) {                                   // element beginHere traverses the mei tree
            Element e = es.get(i);

            // process the element
            switch (e.getLocalName()) {
                case "turn":
                case "trill":
                case "mordent":
                case "ornam":
                    instructifyElement(e);
                    continue;

                //default:
                //    continue;                                                   // ignore it and its children
            }
            this.instructify(e);
        }
    }

    /**
     * returns the element's "full"name, e.g. "upper mordent", "lower turn", "double cadence lower prefix"
     * @param element
     * @return
     */
    private String getOrnamentFullName(MeiElement element) {
        String form = element.get("form");

        String name = element.getName();

        if(name.equals("ornam")) {
            MeiElement symbol = element.getFirstChildByName("symbol");
            return getOrnamentFullNameFromSymbol(symbol);
        }
        if(form != null && !form.isEmpty() && !form.equals("unknown"))
            name = form + " " + name;

        return name;
    }
    /**
     * returns the element's "full"name from the symbol's glyphName, e.g. "double cadence lower prefix"
     * @param symbol
     * @return
     */
    private String getOrnamentFullNameFromSymbol(MeiElement symbol) {
        if(symbol == null)
            return null;

        String glyphName = symbol.get("glyph.name");
        if(glyphName == null)
            return null;

        String prefix = "ornamentPrecomp";
        glyphName = glyphName.startsWith(prefix) ? glyphName.substring(prefix.length()) : glyphName;

        return String.join(" ", glyphName.split("(?=[A-Z])") ).toLowerCase();
    }

    /**
     * checks and prepares the Instruction creation, and calls (if sufficient) createInstruction
     * @param element
     */
    private void instructifyElement(Element element) {
        MeiElement ornament = new MeiElement(element);
        String ornamFullName = getOrnamentFullName(ornament);
        if(ornamFullName == null || ornamFullName.equals(""))
            return;     // if I am not yet supported

        if (checkForCombinedOrnaments(ornament))
            return;     // if I am in combination with another ornament that is previous to me, but has not been processed, yet

        String startid = ornament.get("startid");
        if(startid == null)
            return;     // if the corresponding note cannot be identified
        startid = startid.replace("#", "");

        Element principalNoteElement = Helper.findSibling(ornament.getElement(), startid);
        if (principalNoteElement == null)
            return;     // if the corresponding note is not available
        MeiElement principalNote = new MeiElement(principalNoteElement);

        Instruction instruction = createInstruction(ornamFullName, principalNote, ornament);
        appendInstruction(principalNote, instruction);

        checkForNextOrnament(ornament);
    }

    /**
     * creates an Instruction with ornamentName for the ornament, by creating notes to be played within a <supplied></supplied> addition after the principal note.
     * This Instruction is inserted into the given MEI (the original MEI file stays untouched).
     * @param ornamentName
     * @param principalNote
     * @param ornament
     * @return
     */
    private Instruction createInstruction(String ornamentName, MeiElement principalNote, MeiElement ornament) {
        Instruction instruction = new Instruction();
        instruction.addCorrespondence(ornament); // sets the corresponds of the Instruction to the ornament, as the ornament has a correspondence to the principalNote via "startid"

        instruction.setLabel(ornamentName);

        List<Integer> alterations = ornamentLookup.get(ornamentName);

        int pnDur = Integer.parseInt(principalNote.get("dur"));
        int noteDuration = 32; //pnDur * 4;
        //if (noteDuration <= 8)
        //     noteDuration = 16;

        String principalAccid = principalNote.get("accid");
        String upperAccid = ornament.get("accidupper");
        String lowerAccid = ornament.get("accidlower");
        boolean isFirstPrincipal = principalAccid != null;
        boolean isFirstUpper = upperAccid != null;
        boolean isFirstLower = lowerAccid != null;

        for (Integer alteration : alterations) {
            MeiElement note = new MeiElement("note");
            note.set("dur", String.valueOf(noteDuration));
            note.set("oct", String.valueOf(principalNote.get("oct")));
            note.set("pname", principalNote.get("pname"));

            Helper.shiftNoteDiatonicly(note.getElement(), alteration);

            switch (alteration) {
                case 0:
                    setAccidGes(note, principalAccid, isFirstPrincipal);
                    isFirstPrincipal = false;
                    break;
                case 1:
                    setAccidGes(note, upperAccid, isFirstUpper);
                    isFirstUpper = false;
                    break;
                case -1:
                    setAccidGes(note, lowerAccid, isFirstLower);
                    isFirstLower = false;
                    break;
            }

            instruction.addNote(note);
        }

        return instruction;
    }

    /**
     * sets the accid(.ges) Attribute for having these information explicitly in the resulting ornaments instruction notes. This simplifies Instruction merging.
     * @param note
     * @param accid
     * @param setAccidToo
     */
    private static void setAccidGes(MeiElement note, String accid, boolean setAccidToo) {
        if(accid == null)
            return;
        if(setAccidToo) {
            note.set("accid", accid);
        }
        note.set("accid.ges", accid);
    }

    /**
     * appends the instruction directly after the principal Note in the MEI (the original MEI file stays untouched).
     * @param principalNote
     * @param instruction
     */
    private void appendInstruction(MeiElement principalNote, Instruction instruction) {
        Instruction existingInstruction = instructions.get(principalNote.getId());
        if(existingInstruction != null) {
            existingInstruction.append(instruction);
        }
        else { // create new one
            instructions.put(principalNote.getId(), instruction);
            Helper.appendChildAfterSibling(instruction.getInstructionElement(), principalNote.getElement());
        }
    }

    /**
     * resolves combined ornaments (ornaments for the same principle note, that are written on top of each other) that refer with "prev"/"next" (hopefully) to each other
     * @param ornament
     * @return
     */
    private boolean checkForCombinedOrnaments(MeiElement ornament) {
        if(prevOrnams.contains(ornament.getId())) // I found myself, so I have been instructified already
            return true;
        if(ornament.has("prev")) {
            String prevId = ornament.get("prev");
            if(!prevOrnams.contains(prevId)) {
                nextOrnams.put(ornament.getId(), ornament.getElement()); // remember me for later, do not instructify me right now
                return true;
            }
        }
        if(ornament.has("next")) {
            prevOrnams.add(ornament.getId()); // put me in the list, that the next one does not wait for me
        }
        return false;
    }

    /**
     * checks and processes already occurred "next" ornament, if the just processed ornament has a "next".
     * @param ornament
     */
    private void checkForNextOrnament(MeiElement ornament) {
        if(nextOrnams.get(ornament.getId()) != null)
            nextOrnams.remove(ornament.getId());
        // if someone is already waiting for me
        Element nextOrnam = nextOrnams.get(ornament.get("next"));
        if(nextOrnam != null) {
            nextOrnams.remove(ornament.get("next")); // remove myself if I was in there
            instructifyElement(nextOrnam);
        }
    }
}
