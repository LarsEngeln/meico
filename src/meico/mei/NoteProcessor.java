package meico.mei;

import nu.xom.Element;

public interface NoteProcessor {
    void processNote(Element xmlElement);
    void processChord(Element xmlElement);
}
