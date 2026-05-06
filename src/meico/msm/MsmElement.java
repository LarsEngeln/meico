package meico.msm;

import meico.mei.Helper;
import meico.xml.RichElement;
import nu.xom.Attribute;
import nu.xom.Element;

import java.util.*;

/**
 * This class is an object-oriented wrapper of some Helper function. It is meant as easy access of MSM element data.
 * @author Lars Engeln
 */
public class MsmElement extends RichElement {

    /**
     * constructor from XML element
     * @param element
     */
    public MsmElement(Element element) {
        super(element);
    }

    /**
     * constructor from XML element with option for deep copy
     * @param element
     * @param deepCopy
     */
    public MsmElement(Element element, boolean deepCopy) {
        super(element, deepCopy);
    }
    /**
     * constructor from local name
     * @param localName
     */
    public MsmElement(String localName)  {
        super(localName);
    }

    /**
     * initializes the id of the element. If there is no id, a new one is created and assigned to the element.
     */
    private void initId() {
        //this.id = this.get("id");
        //if(this.id == null) {
        //    this.id = Helper.addUUID(this.element, false, true);
        //}
    }

    /**
     * sets the id of the element and adds it as an attribute to the element
     * @param id
     */
    /*public void setId(String id) {
        this.id = id;
        Attribute a = new Attribute("id", this.id);                              // create an attribute
        this.element.addAttribute(a);                                                 // add attribute to the element
    }*/

    /**
     * creates a new id for the element and assigns it to the element. The new id is generated using the Helper.addUUID function.
     * @return
     */
    public String createNewId() {
        this.id = Helper.addUUID(this.element, true, false);
        return getId();
    }

    /**
     * returns the pitchname
     * @return
     */
    public String getNoteName() {
        return get("pitchname");
    }

    /**
     * returns the duration
     * @return
     */
    public Double getDuration() {
        return getAsDouble("duration");
    }

    /**
     * returns the octave
     * @return
     */
    public Double getOctave() {
        return getAsDouble("octave");
    }

    /**
     * compares this note to another note and returns true if they have the same pitchname and octave, false otherwise
     * @param note
     * @return
     */
    public boolean isSameNote(MsmElement note) {
        if (note == null) return false;
        if (!this.getNoteName().equals(note.getNoteName())) return false;
        if (!this.getOctave().equals(note.getOctave())) return false;
        return true;
    }

    /**
     * returns all children as MsmElements
     * @return
     */
    public ArrayList<MsmElement> getChildrenAsMsmElements() {
        LinkedList<Element> elements = Helper.getAllChildElements(this.element);
        ArrayList<MsmElement> children = new ArrayList<>();
        elements.forEach(elem -> children.add(new MsmElement(elem)));
        return children;
    }
}
