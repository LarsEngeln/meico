package meico.xml;

import meico.mei.Helper;
import nu.xom.Element;
import nu.xom.Attribute;

import java.util.*;

/**
 * This class is an object-oriented wrapper of some Helper function. It is meant as easy access of MEI/MSM element data.
 * @author Lars Engeln
 */
public class RichElement {
    protected Element element;
    protected String id = null;

    public RichElement(Element element) {
        this(element, false);
    }
    public RichElement(Element element, boolean deepCopy) {
        if(deepCopy) {
            this.element = Helper.cloneElement(element, true);
        }
        else this.element = element;

        initId();
    }
    public RichElement(String localName)  {
        this.element = Helper.createElement(localName, true);
        initId();
    }

    private void initId() {
        this.id = this.get("id");
        if(this.id == null) {
            this.id = Helper.addUUID(this.element, true);
        }
    }
    public String getId() {
        return this.id;
    }
    public void setId(String id) {
        this.id = id;
        Attribute a = new Attribute("id", this.id);                              // create an attribute
        a.setNamespace("xml", "http://www.w3.org/XML/1998/namespace");      // set its namespace to xml
        this.element.addAttribute(a);                                                 // add attribute to the element
    }
    public String createNewId() {
        this.id = Helper.addUUID(this.element, true);
        return getId();
    }

    /**
     * returns the XML Element
     * @return
     */
    public Element getElement() { return element; }

    /**
     * returns a clone of the XML Element
     * @return
     */
    public Element getClonedElement() {
        return Helper.cloneElement(element, true);
    }

    /**
     * returns the Elements tag-name, i.e. the name in MEI
     * @return
     */
    public String getName() { return element.getLocalName(); }

    /**
     * sets namespace URI
     * @param namespace
     */
    public void setNamespace(String namespace) {
        element.setNamespaceURI(namespace);
    }

    /**
     * checks whether the Attribute with attributeName exists
     * @param attributeName
     * @return
     */
    public boolean has(String attributeName) { return Helper.getAttribute(attributeName, this.element) != null; }

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
     * returns the value of attributeName as Double if the element has it. Hereby ".ges" (e.g. "accid.ges") is preferred.
     * If the element does not have such an Attribute, the matching child (e.g. <accid/>) will be search.
     * @param attributeName
     * @return value of attributeName (preferring ".ges") or null if this attribute is not set.
     */
    public Double getAsDouble(String attributeName) {
        String value = get(attributeName);
        if(value == null)
            return null;
        return Double.valueOf(value);
    }
    /**
     * returns the value of attributeName as Integer if the element has it. Hereby ".ges" (e.g. "accid.ges") is preferred.
     * If the element does not have such an Attribute, the matching child (e.g. <accid/>) will be search.
     * @param attributeName
     * @return value of attributeName (preferring ".ges") or null if this attribute is not set.
     */
    public Integer getAsInteger(String attributeName) {
        String value = get(attributeName);
        if(value == null)
            return null;
        return Integer.valueOf(value);
    }

    /**
     * returns the value of a boolean Attribute. Example: turn.is("delayed");
     * @param attributeName
     * @return defaults to false
     */
    public boolean is(String attributeName) {
        if(!has(attributeName))
            return false;
        return Boolean.parseBoolean(get(attributeName));
    }

    /**
     * sets (adds/overrides) the Attribute attributeName with value
     * @param attributeName
     * @param value
     */
    public void set(String attributeName, String value) {
        this.element.addAttribute(new Attribute(attributeName, value));
    }
    /**
     * sets (adds/overrides) the Attribute attributeName with value
     * @param attributeName
     * @param value
     */
    public void set(String attributeName, double value) {
        this.element.addAttribute(new Attribute(attributeName, Double.toString(value)));
    }
    /**
     * sets (adds/overrides) the Attribute attributeName with value
     * @param attributeName
     * @param value
     */
    public void set(String attributeName, int value) {
        this.element.addAttribute(new Attribute(attributeName, Integer.toString(value)));
    }

    public void copyValue(String attributeName, RichElement fromThis) {
        String value = fromThis.get(attributeName);
        if(value != null) {
            set(attributeName, value);
        }
    }

    public void appendChild(Element child) {
        child.setNamespaceURI("http://www.music-encoding.org/ns/mei");
        this.element.appendChild(child);
    }
    public void appendChild(RichElement child) {
        child.removeParent();
        this.element.appendChild(child.getElement());
    }

    public RichElement getFirstChildByName(String name) {
        Element child = Helper.getFirstChildElement(name, this.element);
        if(child == null)
            return null;
        return new RichElement(child);
    }

    /**
     * returns all children as MeiElements
     * @return
     */
    public ArrayList<RichElement> getChildren() {
        LinkedList<Element> elements = Helper.getAllChildElements(this.element);
        ArrayList<RichElement> children = new ArrayList<>();
        elements.forEach(elem -> children.add(new RichElement(elem)));
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
                return (new RichElement(child)).get(attributeName);
            }

            boolean ignore = false;
            for(String ignoredElementName : ignoredElementNames)
                ignore = ignore || elementName.equals(ignoredElementName);
            if(!ignore)
                return (new RichElement(child).getFromChild(attributeName, ignoredElementNames));
        }

        return null;
    }

    public RichElement getParent() {
        Element parent = Helper.getParentElement(this.element);
        if(parent == null)
            return null;
        return new RichElement(parent);
    }

    public boolean hasParent() {
        RichElement parent = getParent();
        if(parent == null)
            return false;
        return true;
    }

    public void removeParent() {
        if(hasParent())
            getParent().element.removeChild(this.element);
    }
}
