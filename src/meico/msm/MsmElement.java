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


    public MsmElement(Element element) {
        super(element);
    }
    public MsmElement(Element element, boolean deepCopy) {
        super(element, deepCopy);
    }
    public MsmElement(String localName)  {
        super(localName);
    }

    private void initId() {
        this.id = this.get("id");
        if(this.id == null) {
            this.id = Helper.addUUID(this.element, true, false);
        }
    }
    public void setId(String id) {
        this.id = id;
        Attribute a = new Attribute("id", this.id);                              // create an attribute
        this.element.addAttribute(a);                                                 // add attribute to the element
    }
    public String createNewId() {
        this.id = Helper.addUUID(this.element, true, false);
        return getId();
    }

    public String getNoteName() {
        return get("pitchname");
    }

    public Double getDuration() {
        return getAsDouble("duration");
    }

    public Integer getOctave() {
        return getAsInteger("octave");
    }
}
