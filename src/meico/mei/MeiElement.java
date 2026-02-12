package meico.mei;

import nu.xom.Element;
import nu.xom.Attribute;

import java.util.*;

/**
 * This class is an object-oriented wrapper of some Helper function. It is meant as easy access of MEI element data.
 * @author Lars Engeln
 */
public class MeiElement {
    private Element element;
    private String id = "";

    public MeiElement(Element element) {
        this(element, false);
    }
    public MeiElement(Element element, boolean deepCopy) {
        if(deepCopy) {
            this.element = Helper.cloneElement(element, true);
        }
        else this.element = element;

        initId();
        this.element.setNamespaceURI("http://www.music-encoding.org/ns/mei");
    }
    public MeiElement(String localName)  {
        element = Helper.createElement(localName, true);
        initId();
        element.setNamespaceURI("http://www.music-encoding.org/ns/mei");
    }

    private void initId() {
        this.id = this.get("id");
        if(this.id == null) {
            this.id = Helper.addUUID(element, true);
        }
    }
    public String getId() {
        return id;
    }

    /**
     * returns the XML Element
     * @return
     */
    public Element getElement() { return element; }

    /**
     * returns the Elements tag-name, i.e. the name in MEI
     * @return
     */
    public String getName() { return element.getLocalName(); }

    /**
     * checks whether the Attribute with attributeName exists
     * @param attributeName
     * @return
     */
    public boolean has(String attributeName) { return Helper.getAttribute(attributeName, element) != null; }

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
            return Helper.getAttributeValue(attributeName + ".ges", element);
        return Helper.getAttributeValue(attributeName, element);
    }

    /**
     * returns the value of a boolean Attribute. Example: turn.is("delayed");
     * @param attributeName
     * @return defaults to false
     */
    public boolean is(String attributeName) {
        if(!has(attributeName))
            return false;
        return Boolean.parseBoolean(this.get(attributeName));
    }

    /**
     * sets (adds/overrides) the Attribute attributeName with value
     * @param attributeName
     * @param value
     */
    public void set(String attributeName, String value) { element.addAttribute(new Attribute(attributeName, value)); }

    public void appendChild(Element child) {
        child.setNamespaceURI("http://www.music-encoding.org/ns/mei");
        element.appendChild(child);
    }
    public void appendChild(MeiElement child) {
        child.removeParent();
        element.appendChild(child.getElement());
    }

    public MeiElement getFirstChildByName(String name) {
        Element child = Helper.getFirstChildElement(name, element);
        if(child == null)
            return null;
        return new MeiElement(child);
    }

    /**
     * returns all children as MeiElements
     * @return
     */
    public ArrayList<MeiElement> getChildren() {
        LinkedList<Element> elements = Helper.getAllChildElements(this.element);
        ArrayList<MeiElement> children = new ArrayList<>();
        elements.forEach(elem -> children.add(new MeiElement(elem)));
        return children;
    }

    /**
     * returns the Attribute's value from a child with same name like attributeName (Example: attributeName = "accid" -> find child <accid accid="n"/>)
     * @param attributeName
     * @return
     */
    public String getFromChild(String attributeName) {
        return getFromChild(attributeName, Collections.emptyList());
    }

    /**
     * returns the Attribute's value from a child with same name like attributeName (Example: attributeName = "accid" -> find child <accid accid="n"/>)
     * @param attributeName
     * @param ignoredElementNames a List of Elements where not to search for the correct child (e.g. if it is within <del/>, thereby marked as deleted)
     * @return
     */
    public String getFromChild(String attributeName, List<String> ignoredElementNames) {
        java.util.LinkedList<Element> children = Helper.getAllChildElements(this.element);

        for(Element child : children) {
            String elementName = child.getLocalName();
            if(elementName.equals(attributeName)) {
                return (new MeiElement(child)).get(attributeName);
            }

            boolean ignore = false;
            for(String ignoredElementName : ignoredElementNames)
                ignore = ignore || elementName.equals(ignoredElementName);
            if(!ignore)
                return (new MeiElement(child).getFromChild(attributeName, ignoredElementNames));
        }

        return null;
    }

    public MeiElement getParent() {
        Element parent = Helper.getParentElement(element);
        if(parent == null)
            return null;
        return new MeiElement(parent);
    }

    public boolean hasParent() {
        MeiElement parent = getParent();
        if(parent == null)
            return false;
        return true;
    }

    public void removeParent() {
        if(hasParent())
            getParent().element.removeChild(element);
    }
}
