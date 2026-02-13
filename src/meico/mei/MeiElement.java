package meico.mei;

import meico.xml.RichElement;
import nu.xom.Element;

import java.util.*;

/**
 * This class is an object-oriented wrapper of some Helper function. It is meant as easy access of MEI element data.
 * @author Lars Engeln
 */
public class MeiElement extends RichElement {


    public MeiElement(Element element) {
        super(element);
    }
    public MeiElement(Element element, boolean deepCopy) {
        super(element, deepCopy);
        setNamespace("http://www.music-encoding.org/ns/mei");
    }
    public MeiElement(String localName)  {
        super(localName);
        setNamespace("http://www.music-encoding.org/ns/mei");
    }

}
