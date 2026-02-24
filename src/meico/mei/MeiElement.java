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

}
