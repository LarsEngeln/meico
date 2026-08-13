package meico.mei;

import meico.mpm.elements.Performance;
import meico.msm.MsmNoteElement;
import nu.xom.Element;

import java.util.ArrayList;
import java.util.HashMap;

public interface Context {
    Element getCurrentPart();
    Performance getCurrentPerformance();
    HashMap<String, Element> getAllNotesAndChords();

    ArrayList<Object> computeControlEventTiming(Element event, Element msmPartContext);
    MsmNoteElement meiNote2MsmNote(MeiNoteElement meiNote);
}
