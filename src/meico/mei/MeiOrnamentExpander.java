package meico.mei;

import javafx.util.Pair;
import meico.xml.RichElement;
import nu.xom.Element;
import nu.xom.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class adds ornamentExpansions for ornaments, by creating notes to be played within a <supplied></supplied> after the principal note.
 * This is mainly for rendering via MPM.
 * These ornamentExpansions are inserted into the given MEI (the original MEI file stays untouched).
 * @author Lars Engeln
 */
public class MeiOrnamentExpander {
    public Mei mei;
    private Map<String, OrnamentExpansion> ornamentExpansions = new HashMap<String, OrnamentExpansion>(); // ornament's startid to ornamentExpansion
    private Map<String, List<String>> ornamentLookup = new HashMap<String, List<String>>();

    private ArrayList<String> prevOrnams = new ArrayList<String>(); // already expanded ornams with that are "previous" to another ornam
    private Map<String, Element> nextOrnams = new HashMap<String, Element>(); // prevId to nextElement - remember "next" ornament to be processed, if "prev"-Id have not been processed yet - so, if "prev"/"next" is not well sorted in the MEI

    private Map<String, Map<String, String>> currentAccids = new HashMap<>(); // all accids in the current measure, "oct"->"pname"->"accid"
    private Map<String, String> currentKey = new HashMap<>(); // accids of the current key, "pname"->"accid"
    private RichElement currentMeasure = null;

    private ArrayList<Pair<RichElement, OrnamentExpansion>> endingGraces = new ArrayList<>();

    public MeiOrnamentExpander(Mei mei) {
        try {
            createOrnamentLookUp();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MeiOrnamentExpander()  {
        try {
            createOrnamentLookUp();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * adds expanded readings for ornaments
     * @param mei the MEI to be expanded
    * @result the expanded MEI
     */
    public Mei expandOrnaments(Mei mei) {
        if (mei == null) {
            System.out.println("\nThe provided MEI object is null and cannot be expanded.");
            return null;
        }

        long startTime = System.currentTimeMillis();                            // we measure the time that the conversion consumes
        System.out.println("\nInstructifying " + ((mei.getFile() != null) ? mei.getFile().getName() : "MEI data") + ".");

        this.mei = mei;

        if (this.mei.isEmpty() || (this.mei.getMusic() == null) || (this.mei.getMusic().getFirstChildElement("body", this.mei.getMusic().getNamespaceURI()) == null))      // if no mei music data available
            return mei;

        Elements bodies = this.mei.getMusic().getChildElements("body", this.mei.getMusic().getNamespaceURI());  // get the list of body elements in the mei source
        for (int b = 0; b < bodies.size(); ++b)                                 // for each body
            this.expandOrnaments(bodies.get(b));                                        // convert each body to msm, the resulting Msms can then be found in this.movements

        for(Element element : nextOrnams.values()){  // do not forget someone, should never happen if MEI is well-defined
            this.expandOrnamentsElement(element);
        }

        System.out.println("MEI expansion of Ornaments finished. Time consumed: " + (System.currentTimeMillis() - startTime) + " milliseconds");

        return mei;
    }

    /**
     * iterates the MEI XML tree to identify supported elements to get expanded
     * @param root
     */
    private void expandOrnaments(Element root) {
        Elements es = root.getChildElements();                                  // all child elements of root

        for (int i = 0; i < es.size(); ++i) {                                   // element beginHere traverses the mei tree
            Element e = es.get(i);

            // process the element
            switch (e.getLocalName()) {
                case "turn":
                case "trill":
                case "mordent":
                case "ornam":
                    expandOrnamentsElement(e);
                    continue;

                case "keySig":
                    currentKey = new HashMap<>();
                    break;
                case "keyAccid":
                    RichElement keyAccid = new RichElement(e);
                    currentKey.put(keyAccid.get("pname"), keyAccid.get("accid"));
                    continue;
                case "measure":
                    for(Pair<RichElement, OrnamentExpansion> grace : endingGraces) {                // if we have ending graces, append them now before going to next measure
                        appendOrnamentExpansion(grace.getKey(), grace.getValue(), true);
                    }
                    endingGraces.clear();

                    currentMeasure = new RichElement(e);
                    currentAccids = new HashMap<>();
                case "note":
                    RichElement note = new RichElement(e);
                    String accid = note.get("accid");
                    if(accid == null || accid.isEmpty())
                       break;
                    if(!currentAccids.containsKey(note.get("oct")))
                        currentAccids.put(note.get("oct"), new HashMap<>());
                    currentAccids.get(note.get("oct")).put(note.get("pname"), accid);

                    if(note.has("grace"))
                        expandGrace(note);
                    break;
                case "chord":
                    RichElement chord = new RichElement(e);
                    if(chord.has("grace"))
                        expandGrace(chord);
                    break;
                case "graceGrp":
                    RichElement graceGrp = new RichElement(e);
                    expandGrace(graceGrp);
                    break;
                case "accid":
                    break;

                //default:
                //    continue;                                                   // ignore it and its children
            }
            this.expandOrnaments(e);
        }
    }

    private RichElement getElementWithId(ArrayList<RichElement> elements, String id) {
        for(RichElement element : elements) {
            if(element.getId() != null && element.getId().equals(id))
                return element;
        }
        return null;
    }

    /**
     *
     * @param element
     * @param graceIsBefore
     * @return true if grace is before corresponding, false if is after
     */
    private RichElement getCorrespondingNoteOfGrace(RichElement element, AtomicBoolean graceIsBefore) {
        RichElement principalNote = null;

        if(this.currentMeasure == null)
            return null;

        ArrayList<RichElement> slurs = this.currentMeasure.getChildrenOfType("slur");
        ArrayList<RichElement> notes = collectAllNotes(element);

        for(RichElement slur : slurs) {
            RichElement note = getElementWithId(notes, slur.get("startid"));
            if (note != null) {
                String endid = slur.get("endid");
                Element principalNoteElement = Helper.findSibling(element.getElement(), endid);
                if (principalNoteElement != null) {
                    principalNote = new RichElement(principalNoteElement);
                    graceIsBefore.set(true);
                    return principalNote;
                }
            }
            note = getElementWithId(notes, slur.get("endid"));
            if (note != null) {
                String startid = slur.get("startid");
                Element principalNoteElement = Helper.findSibling(element.getElement(), startid);
                if (principalNoteElement != null) {
                    principalNote = new RichElement(principalNoteElement);
                    graceIsBefore.set(false);
                    return principalNote;
                }
            }
        }

        // No slur found: search the surrounding
        Element previousElement = Helper.getPreviousSiblingElement(element.getElement());
        Element nextElement = Helper.getNextSiblingElement(element.getElement());

        if (nextElement == null && previousElement == null) {
            graceIsBefore.set(true);
            return null;
        }
        if (nextElement != null && previousElement == null) {
            principalNote = new RichElement(nextElement);
            graceIsBefore.set(true);
            return principalNote;
        }
        if (previousElement != null && nextElement == null) {
            principalNote = new RichElement(previousElement);
            graceIsBefore.set(false);
            return principalNote;
        }

        // if both != null
        String graceType = element.get("grace");
        if(graceType == null)
            graceType = "";

        if(!graceType.equals("unacc") && nextElement.getLocalName().equals("note") || nextElement.getLocalName().equals("chord")) {
            principalNote = new RichElement(nextElement);
            graceIsBefore.set(true);
        }
        if(previousElement.getLocalName().equals("note") || previousElement.getLocalName().equals("chord")) {
            principalNote = new RichElement(previousElement);
            graceIsBefore.set(false);
        }
        principalNote = new RichElement(nextElement);
        graceIsBefore.set(true);
        return principalNote;
    }

    private void expandGrace(RichElement element) {
        ArrayList<RichElement> notes = collectAllNotes(element);

        AtomicBoolean graceIsBefore = new AtomicBoolean(true);
        RichElement principalNote = getCorrespondingNoteOfGrace(element, graceIsBefore);

        if(principalNote == null)
            return;

        String graceType = element.get("grace");
        if(graceType == null || graceType.equals("unacc"))
            graceType = "acc";
        String ornamentName = "grace " + graceType;
        if(!graceIsBefore.get()) {
            ornamentName = ornamentName + " delayed";
        }


        OrnamentExpansion ornamentExpansion = new OrnamentExpansion();
        ornamentExpansion.addCorrespondence(principalNote); // sets the corresponds of the OrnamentExpansion to the ornament, as the ornament has a correspondence to the principalNote via "startid"
        ornamentExpansion.setLabel(ornamentName);

        boolean principalIsNote = principalNote.getName().equals("note");

        if(!graceIsBefore.get() && principalIsNote) {
            RichElement graceNote = new RichElement("note");
            graceNote.set("oct", String.valueOf(principalNote.get("oct")));
            graceNote.set("pname", principalNote.get("pname"));
            ornamentExpansion.addElement(graceNote);
        }

        RichElement halfStepsTo = principalNote;
        if(!principalIsNote) {
           halfStepsTo = notes.get(notes.size() - 1);
        }
        for (RichElement n : notes) {
            RichElement note = new RichElement(n.getElement(), true);

            if(halfStepsTo != null) {
                double halfsteps = getHalfstepsBetween(halfStepsTo, note);
                note.set("intm", String.valueOf(halfsteps) + "hs");
            }
            ornamentExpansion.addElement(note);
        }

        if(graceIsBefore.get()) {
            RichElement graceNote = new RichElement("note");
            graceNote.set("oct", String.valueOf(principalNote.get("oct")));
            graceNote.set("pname", principalNote.get("pname"));
            ornamentExpansion.addElement(graceNote);
        }

        if(graceIsBefore.get())
            appendOrnamentExpansion(principalNote, ornamentExpansion, !graceIsBefore.get());
        else
            endingGraces.add(new Pair(principalNote, ornamentExpansion));
    }

    /**
     * Recursively collects all note and chord elements from the given RichElement.
     * Traverses the entire element tree regardless of nesting depth.
     * @param element the element to collect notes from
     * @return a list of all RichElement notes and chords found
     */
    private ArrayList<RichElement> collectAllNotes(RichElement element) {
        ArrayList<RichElement> notes = new ArrayList<RichElement>();
        String elementName = element.getName();

        // If this element is a note or chord, add it to the list
        if (elementName.equals("note") || elementName.equals("chord")) {
            notes.add(element);
        }

        // Recursively traverse all children
        ArrayList<RichElement> children = element.getChildren();
        for (RichElement child : children) {
            notes.addAll(collectAllNotes(child));
        }

        return notes;
    }

    /**
     * returns the element's "full"name, e.g. "upper mordent", "lower turn", "double cadence lower prefix"
     * @param element
     * @return
     */
    private String getOrnamentFullName(RichElement element) {
        String form = element.get("form");

        String name = element.getName();

        if(name.equals("ornam")) {
            RichElement symbol = element.getFirstChildByName("symbol");
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
    private String getOrnamentFullNameFromSymbol(RichElement symbol) {
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
     * checks and prepares the OrnamentExpansion creation, and calls (if sufficient) createOrnamentExpansion
     * @param element
     */
    private void expandOrnamentsElement(Element element) {
        RichElement ornament = new RichElement(element);
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
        RichElement principalNote = new RichElement(principalNoteElement);

        if(ornament.get("staff") == null && ornament.get("part") == null) {
            RichElement parent = principalNote;
            do {
                parent = parent.getParent();
                if(parent != null && (parent.getName().equals("staff") || parent.getName().equals("part")))
                    if(parent.has("n")) {
                        ornament.set(parent.getName(), parent.get("n"));
                        break;
                    }
            } while (parent != null && !parent.getName().equals("part"));
        }

        OrnamentExpansion ornamentExpansion = createOrnamentExpansion(ornamFullName, principalNote, ornament);
        appendOrnamentExpansion(principalNote, ornamentExpansion, true);

        checkForNextOrnament(ornament);
    }

    /**
     * creates an OrnamentExpansion with ornamentName for the ornament, by creating notes to be played within a <supplied></supplied> addition after the principal note.
     * This OrnamentExpansion is inserted into the given MEI (the original MEI file stays untouched).
     * @param ornamentName
     * @param principalNote
     * @param ornament
     * @return
     */
    private OrnamentExpansion createOrnamentExpansion(String ornamentName, RichElement principalNote, RichElement ornament) {
        OrnamentExpansion ornamentExpansion = new OrnamentExpansion();
        ornamentExpansion.addCorrespondence(principalNote); // sets the corresponds of the OrnamentExpansion to the ornament, as the ornament has a correspondence to the principalNote via "startid"

        ornamentExpansion.setLabel(ornamentName);

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
                RichElement repeat = new RichElement("barLine");
                repeat.set("form", "rptstart");
                ornamentExpansion.addElement(repeat);
                continue;
            }
            if(alterationEntry.equals(":|")) {
                RichElement repeat = new RichElement("barLine");
                repeat.set("form", "rptend");
                ornamentExpansion.addElement(repeat);
                continue;
            }
            if(alterationEntry.equals(":|:")) {
                RichElement repeat = new RichElement("barLine");
                repeat.set("form", "rptboth");
                ornamentExpansion.addElement(repeat);
                continue;
            }

            RichElement note = new RichElement("note");
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

            ornamentExpansion.addElement(note);
        }

        return ornamentExpansion;
    }

    /**
     * returns the halfsteps between principalNote and auxiliryNote
     * @param principalNote
     * @param auxiliaryNote
     * @return
     */
    private double getHalfstepsBetween(RichElement principalNote, RichElement auxiliaryNote) {
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
    private String getCurrentAccid(RichElement note) {
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
     * sets the accid(.ges) Attribute for having these information explicitly in the resulting ornaments ornamentExpansion notes. This simplifies OrnamentExpansion merging.
     * @param note
     * @param accid
     * @param setAccidToo
     */
    private static void setAccidGes(RichElement note, String accid, boolean setAccidToo) {
        if(accid == null)
            return;
        if(setAccidToo) {
            note.set("accid", accid);
        }
        note.set("accid.ges", accid);
    }

    /**
     * flattens the XML tree of a graceGrp to a simple list of graceGrps. Elements like notes not being in a graceGrp will get grouped ("grp1 note1 note2 grp2 note3" will become "grp1 grp3 grp2 grp4").
     * @param element
     * @return
     */
    private ArrayList<RichElement> flattenGraceGrp(RichElement element) {
        ArrayList<RichElement> children = element.getChildren();
        ArrayList<RichElement> graceGrps = new ArrayList<>();

        RichElement graceGrp = new RichElement("graceGrp");
        for (RichElement child : children) {
            if(child.getName().equals("graceGrp") || child.getName().equals("beam")) {
                if(!graceGrp.getChildren().isEmpty()) {
                    graceGrps.add(graceGrp);
                    graceGrp = new RichElement("graceGrp");
                }

                graceGrps.addAll(flattenGraceGrp(child));                   // flatten all children
                continue;
            }

            graceGrp.appendChild(child);                                    // add element to a new graceGrp, if it was not a graceGrp itself
        }

        if(!graceGrp.getChildren().isEmpty()) {
            graceGrps.add(graceGrp);
        }
        return graceGrps;
    }

    /**
     * appends the ornamentExpansion directly after the principal Note in the MEI (the original MEI file stays untouched).
     * @param principalNote
     * @param ornamentExpansion
     * @param atEnd
     */
    private void appendOrnamentExpansion(RichElement principalNote, OrnamentExpansion ornamentExpansion, boolean atEnd) {
        OrnamentExpansion existingOrnamentExpansion = ornamentExpansions.get(principalNote.getId());
        if(existingOrnamentExpansion != null) {
            existingOrnamentExpansion.append(ornamentExpansion, atEnd);
        }
        else { // create new one
            ornamentExpansions.put(principalNote.getId(), ornamentExpansion);
            Helper.appendChildAfterSibling(ornamentExpansion.getOrnamentExpansionElement().getElement(), principalNote.getElement());
        }
    }

    /**
     * resolves combined ornaments (ornaments for the same principle note, that are written on top of each other) that refer with "prev"/"next" (hopefully) to each other
     * @param ornament
     * @return
     */
    private boolean checkForCombinedOrnaments(RichElement ornament) {
        if(prevOrnams.contains(ornament.getId())) // I found myself, so I have been expanded already
            return true;
        if(ornament.has("prev")) {
            String prevId = ornament.get("prev");
            if(!prevOrnams.contains(prevId)) {
                nextOrnams.put(ornament.getId(), ornament.getElement()); // remember me for later, do not expandOrnaments me right now
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
    private void checkForNextOrnament(RichElement ornament) {
        if(nextOrnams.get(ornament.getId()) != null)
            nextOrnams.remove(ornament.getId());
        // if someone is already waiting for me
        Element nextOrnam = nextOrnams.get(ornament.get("next"));
        if(nextOrnam != null) {
            nextOrnams.remove(ornament.get("next")); // remove myself if I was in there
            expandOrnamentsElement(nextOrnam);
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
