package meico.mei;

import nu.xom.Element;
import nu.xom.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * This class adds instructions for ornaments, by creating notes to be played within a <supplied></supplied> after the principal note.
 * This is mainly for rendering via MPM.
 * These instructions are inserted into the given MEI (the original MEI file stays untouched).
 * @author Lars Engeln
 */
public class MeiInstructifier {
    public Mei mei;
    private Map<String, Instruction> instructions = new HashMap<String, Instruction>(); // ornament's startid to instruction
    private Map<String, List<String>> ornamentLookup = new HashMap<String, List<String>>();

    private ArrayList<String> prevOrnams = new ArrayList<String>(); // already instructified ornams with that are "previous" to another ornam
    private Map<String, Element> nextOrnams = new HashMap<String, Element>(); // prevId to nextElement - remember "next" ornament to be processed, if "prev"-Id have not been processed yet - so, if "prev"/"next" is not well sorted in the MEI

    private Map<String, Map<String, String>> currentAccids = new HashMap<>(); // all accids in the current measure, "oct"->"pname"->"accid"
    private Map<String, String> currentKey = new HashMap<>(); // accids of the current key, "pname"->"accid"

    public MeiInstructifier(Mei mei) {
        try {
            createOrnamentLookUp();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MeiInstructifier()  {
        try {
            createOrnamentLookUp();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * adds instructive readings for ornaments
     * @param mei the MEI to be instructified
    * @result the instructive MEI
     */
    public Mei instructify(Mei mei) {
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

                case "keySig":
                    currentKey = new HashMap<>();
                    break;
                case "keyAccid":
                    MeiElement keyAccid = new MeiElement(e);
                    currentKey.put(keyAccid.get("pname"), keyAccid.get("accid"));
                    continue;
                case "measure":
                    currentAccids = new HashMap<>();
                case "note":
                    MeiElement note = new MeiElement(e);
                    String accid = note.get("accid");
                    if(accid == null || accid.isEmpty())
                       break;
                    if(!currentAccids.containsKey(note.get("oct")))
                        currentAccids.put(note.get("oct"), new HashMap<>());
                    currentAccids.get(note.get("oct")).put(note.get("pname"), accid);
                    break;
                case "accid":
                    break;

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

        if(ornament.get("staff") == null && ornament.get("part") == null) {
            MeiElement parent = principalNote;
            do {
                parent = parent.getParent();
                if(parent != null && (parent.getName().equals("staff") || parent.getName().equals("part")))
                    if(parent.has("n")) {
                        ornament.set(parent.getName(), parent.get("n"));
                        break;
                    }
            } while (parent != null && !parent.getName().equals("part"));
        }

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
        instruction.addCorrespondence(principalNote); // sets the corresponds of the Instruction to the ornament, as the ornament has a correspondence to the principalNote via "startid"

        instruction.setLabel(ornamentName);

        List<String> alterations = ornamentLookup.get(ornamentName);

        int pnDur = Integer.parseInt(principalNote.get("dur"));
        int noteDuration = 32; //pnDur * 4;
        //if (noteDuration <= 8)
        //     noteDuration = 16;

        String principalAccid = getCurrentAccid(principalNote);
        String upperAccid = ornament.get("accidupper");
        String lowerAccid = ornament.get("accidlower");
        boolean isFirstPrincipal = principalAccid != null;
        boolean isFirstUpper = upperAccid != null;
        boolean isFirstLower = lowerAccid != null;

        for (String alterationEntry : alterations) {
            if(alterationEntry.equals("|:")) {
                MeiElement repeat = new MeiElement("barLine");
                repeat.set("form", "rptstart");
                instruction.addElement(repeat);
                continue;
            }
            if(alterationEntry.equals(":|")) {
                MeiElement repeat = new MeiElement("barLine");
                repeat.set("form", "rptend");
                instruction.addElement(repeat);
                continue;
            }
            if(alterationEntry.equals(":|:")) {
                MeiElement repeat = new MeiElement("barLine");
                repeat.set("form", "rptboth");
                instruction.addElement(repeat);
                continue;
            }

            MeiElement note = new MeiElement("note");
            note.set("dur", String.valueOf(noteDuration));
            note.set("oct", String.valueOf(principalNote.get("oct")));
            note.set("pname", principalNote.get("pname"));

            int alteration = Integer.parseInt(alterationEntry);
            Helper.shiftNoteDiatonicly(note.getElement(), alteration);
            note.set("accid", getCurrentAccid(note));                   // explicitly set the accid

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

            double halfsteps = getHalfstepsBetween(principalNote, note);
            note.set("intm", String.valueOf(halfsteps)+"hs");

            instruction.addElement(note);
        }

        return instruction;
    }

    /**
     * returns the halfsteps between principalNote and auxiliryNote
     * @param principalNote
     * @param auxiliaryNote
     * @return
     */
    private double getHalfstepsBetween(MeiElement principalNote, MeiElement auxiliaryNote) {
        double halfsteps = 0.0;

        String priAccid = getCurrentAccid(principalNote);
        String auxAccid = getCurrentAccid(auxiliaryNote);

        halfsteps = Helper.getHalfstepsBetween(principalNote.get("pname"), auxiliaryNote.get("pname"));
        halfsteps = halfsteps + (12 * (Integer.parseInt(auxiliaryNote.get("oct")) - Integer.parseInt(principalNote.get("oct"))));

        halfsteps = halfsteps - Helper.accidString2decimal(priAccid);
        halfsteps = halfsteps + Helper.accidString2decimal(auxAccid);

        return halfsteps;
    }

    /**
     * returns the current accid for the note. If note has no accid, the measure's accid (with fallback to the current key) will be returned.
     * @param note
     */
    private String getCurrentAccid(MeiElement note) {
        if(note.has("accid"))
            return note.get("accid");

        String accid = "";
        if(currentAccids.containsKey(note.get("oct")) && currentAccids.get(note.get("oct")).containsKey(note.get("pname"))) {
            accid = currentAccids.get(note.get("oct")).get(note.get("pname"));
        }
        else if(currentKey.containsKey(note.get("pname"))) {
            accid = currentKey.get(note.get("pname"));
        }

        return accid;
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

    /**
     * creates the lookUp table with ornament descriptions. Names for lookUp are identical to getOrnamentFullName results
     */
    private void createOrnamentLookUp() throws IOException, NullPointerException {
        this.ornamentLookup = new HashMap<String, List<String>>();

        // open input stream
        InputStream is = getClass().getResourceAsStream("/resources/ornaments.dict");
        if(is == null)
            return;

        // initialize the readers with the input stream
        InputStreamReader ir = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(ir);

        // build (key, value) pairs where the key is the ornament name string and value is the List of alterations and add them to the dict map
        String ornamentName = "";
        List<String> alterations = new ArrayList<String>();
        for(String line = br.readLine(); line != null; line = br.readLine()) {  // read all the lines in *.dict
            if (line.isEmpty()                                                  // an empty line
                    || (line.charAt(0) == '%'))                                     // this is a comment line
                continue;                                                       // ignore it

            if (line.charAt(0) == '#') {                                        // this is an ornament name line, it specifies that all further lines will be associated with it until an ornament line is read
                ornamentName = line.substring(1).trim();                     // switch the ornamentName variable, delete any spaces in the string beforehand so that "# trill " -> "trill"
                alterations = new ArrayList<String>();
                ornamentLookup.put(ornamentName, alterations);
                continue;
            }

            line = line.replaceAll("\\s+", "");                           // replaces
            alterations.add(line);                                               // add alteration to current list
        }

        // close readers and input stream
        br.close();
        ir.close();
        is.close();
    }
}
