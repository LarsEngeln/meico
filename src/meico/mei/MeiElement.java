package meico.mei;

import meico.xml.RichElement;
import nu.xom.Element;

import java.util.*;

/**
 * This class is an object-oriented wrapper of some Helper function. It is meant as easy access of MEI element data.
 * @author Lars Engeln
 */
public class MeiElement extends RichElement {

    /**
     * constructor from XML element
     * @param element
     */
    public MeiElement(Element element) {
        super(element);
    }

    /**
     * constructor from RichElement
     * @param element
     */
    public MeiElement(RichElement element) {
        super(element.getElement());
    }

    /**
     * constructor from XML element with deep copy option
     * @param element
     * @param deepCopy
     */
    public MeiElement(Element element, boolean deepCopy) {
        super(element, deepCopy);
        setNamespace("http://www.music-encoding.org/ns/mei");
    }

    /**
     * constructor from local name
     * @param localName
     */
    public MeiElement(String localName)  {
        super(localName);
        setNamespace("http://www.music-encoding.org/ns/mei");
    }

    /**
     * returns the value of attributeName if the element has it. Hereby ".ges" (e.g. "accid.ges") is preferred.
     * If the element does not have such an Attribute, the matching child (e.g. <accid/>) will be search.
     * @param attributeName
     * @return value of attributeName (preferring ".ges") or null if this attribute is not set.
     */
    public String get(String attributeName) {
        if(!has(attributeName) && !has(attributeName + ".ges"))
            return getFromChild(attributeName, Arrays.asList("damage","del","sic"));

        if(has(attributeName + ".ges"))
            return Helper.getAttributeValue(attributeName + ".ges", this.element);
        return Helper.getAttributeValue(attributeName, this.element);
    }

    /**
     * returns all children as MeiElements
     * @return
     */
    public ArrayList<MeiElement> getChildrenAsMeiElements() {
        LinkedList<Element> elements = Helper.getAllChildElements(this.element);
        ArrayList<MeiElement> children = new ArrayList<>();
        elements.forEach(elem -> children.add(new MeiElement(elem)));
        return children;
    }

    /**
     * returns all children with the given name as MeiElements
     * @param name
     * @return
     */
    public ArrayList<MeiElement> getChildrenAsMeiElements(String name) {
        LinkedList<Element> elements = Helper.getAllChildElements(name, this.element);
        ArrayList<MeiElement> children = new ArrayList<>();
        elements.forEach(elem -> children.add(new MeiElement(elem)));
        return children;
    }
}
