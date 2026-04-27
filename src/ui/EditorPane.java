package ui;

import crdt.character.CRDTChar;
import crdt.character.CharId;
import crdt.character.CharacterCRDT;
import crdt.utils.Clock;
import cursor.CursorTracker;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import operations.*;
import serializations.OperationSerializer;

public class EditorPane extends JPanel {

    // ─── Interfaces for Member 3 ──────────────────────────────────────────────

    /** Member 3's WebSocketClient must implement this. EditorPane calls it to send messages. */
    public interface NetworkSender {
        void sendMessage(String jsonMessage);
        void connect(String serverUrl);
        void disconnect();
        boolean isConnected();
    }

    // ─── Fields ──────────────────────────────────────────────────────────────

    private final JTextPane textPane;
    private CharacterCRDT crdt;
    private Clock clock;
    private int localUserID;
    private String sessionID;
    private boolean isEditor;

    private boolean suppressDocumentEvents = false;
    private CharId caretCharId = null;

    private NetworkSender networkSender;

    private final CursorTracker cursorTracker = new CursorTracker();

    private static final Color[] CURSOR_COLORS = {
        Color.RED,
        new Color(30, 100, 210),
        new Color(20, 150, 20),
        new Color(210, 120, 0),
        new Color(140, 30, 170),
        new Color(0, 160, 160)
    };

    // ─── Constructor ─────────────────────────────────────────────────────────

    public EditorPane(int userID, String sessionID, boolean isEditor) {
        this.localUserID = userID;
        this.sessionID = sessionID;
        this.isEditor = isEditor;
        this.crdt = new CharacterCRDT();
        this.clock = new Clock();

        textPane = new JTextPane();
        textPane.setEditable(isEditor);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);

        attachDocumentListener();
        attachCaretListener();
    }

    /** Updates whether the local user can edit the document. */
    public void setEditingEnabled(boolean enabled) {
        this.isEditor = enabled;
        textPane.setEditable(enabled);
    }

    // ─── Document Listener ───────────────────────────────────────────────────

    private void attachDocumentListener() {
        textPane.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!isEditor || suppressDocumentEvents) return; // to block writing when viewer
                try {
                    int offset = e.getOffset();
                    int length = e.getLength();
                    String inserted = e.getDocument().getText(offset, length);

                    // Snapshot CRDT visible chars before any modifications
                    List<CRDTChar> visible = crdt.getVisibleChars();

                    AttributeSet inputAttrs = textPane.getInputAttributes();
                    boolean bold = StyleConstants.isBold(inputAttrs);
                    boolean italic = StyleConstants.isItalic(inputAttrs);

                    for (int i = 0; i < length; i++) {
                        char ch = inserted.charAt(i);
                        int pos = offset + i;

                        CharId parentID = (pos == 0 || visible.isEmpty())
                                ? null
                                : visible.get(Math.min(pos - 1, visible.size() - 1)).id;

                        int t = clock.tick();
                        CharId id = new CharId(t, localUserID);

                        crdt.insert(id, ch, parentID);

                        if (networkSender != null && networkSender.isConnected()) {
                            InsertOperation op = new InsertOperation(localUserID, t, ch, parentID, bold, italic);
                            System.out.println("[EditorPane] Sending insert operation: user=" + localUserID + " char='" + ch + "' clock=" + t);
                            networkSender.sendMessage(buildOperationEnvelope(op));
                        } else {
                            System.out.println("[EditorPane] Cannot send: networkSender is null or disconnected");
                        }

                        caretCharId = id;
                        // Update snapshot so next char in the same paste uses the correct parent
                        visible = crdt.getVisibleChars();
                    }
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!isEditor || suppressDocumentEvents) return;
                int offset = e.getOffset();
                int length = e.getLength();

                // Snapshot before any CRDT deletes — CRDT still has all chars at this point
                List<CRDTChar> visible = crdt.getVisibleChars();

                for (int i = 0; i < length; i++) {
                    int idx = offset + i;
                    if (idx >= visible.size()) break;

                    CharId target = visible.get(idx).id;
                    crdt.delete(target);

                    if (networkSender != null && networkSender.isConnected()) {
                        int t = clock.tick();
                        networkSender.sendMessage(buildOperationEnvelope(new DeleteOperation(localUserID, t, target)));
                    }
                }

                List<CRDTChar> after = crdt.getVisibleChars();
                caretCharId = (offset == 0 || after.isEmpty())
                        ? null
                        : after.get(Math.min(offset - 1, after.size() - 1)).id;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Attribute-only changes triggered by programmatic refreshDisplay() — no CRDT action needed
            }
        });
    }

    // ─── Caret Listener ──────────────────────────────────────────────────────

    private void attachCaretListener() {
        textPane.addCaretListener(e -> {
            if (!isEditor || suppressDocumentEvents) return;
            int pos = e.getDot();
            List<CRDTChar> visible = crdt.getVisibleChars();
            caretCharId = (pos == 0 || visible.isEmpty())
                    ? null
                    : visible.get(Math.min(pos - 1, visible.size() - 1)).id;

            if (networkSender != null && networkSender.isConnected()) {
                networkSender.sendMessage(buildCursorEnvelope());
            }
        });
    }

    // ─── Display Refresh ─────────────────────────────────────────────────────

    /** Rebuilds JTextPane from CRDT state. Call after applying any remote operation. */
    public void refreshDisplay() {
        suppressDocumentEvents = true;
        try {
            List<CRDTChar> chars = crdt.getVisibleChars();
            StyledDocument doc = textPane.getStyledDocument();

            if (doc.getLength() > 0) {
                doc.remove(0, doc.getLength());
            }

            if (chars.isEmpty()) {
                renderRemoteCursors();
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (CRDTChar c : chars) sb.append(c.value);
            doc.insertString(0, sb.toString(), null);

            // Apply bold/italic in batched groups for efficiency
            int groupStart = 0;
            boolean groupBold = chars.get(0).isBold();
            boolean groupItalic = chars.get(0).isItalic();

            for (int i = 1; i <= chars.size(); i++) {
                boolean curBold = (i < chars.size()) ? chars.get(i).isBold() : !groupBold;
                boolean curItalic = (i < chars.size()) ? chars.get(i).isItalic() : !groupItalic;

                if (curBold != groupBold || curItalic != groupItalic) {
                    SimpleAttributeSet attrs = new SimpleAttributeSet();
                    StyleConstants.setBold(attrs, groupBold);
                    StyleConstants.setItalic(attrs, groupItalic);
                    doc.setCharacterAttributes(groupStart, i - groupStart, attrs, true);
                    groupStart = i;
                    if (i < chars.size()) {
                        groupBold = curBold;
                        groupItalic = curItalic;
                    }
                }
            }

            // Restore caret to the same logical CRDT position
            int newPos = 0;
            if (caretCharId != null) {
                for (int i = 0; i < chars.size(); i++) {
                    if (chars.get(i).id.equals(caretCharId)) {
                        newPos = i + 1;
                        break;
                    }
                }
            }
            textPane.setCaretPosition(Math.min(newPos, doc.getLength()));

        } catch (BadLocationException e) {
            e.printStackTrace();
        } finally {
            suppressDocumentEvents = false;
        }

        renderRemoteCursors();
    }

    // ─── Remote Cursor Rendering ─────────────────────────────────────────────

    private void renderRemoteCursors() {
        Highlighter hl = textPane.getHighlighter();
        hl.removeAllHighlights();

        int docLen = textPane.getDocument().getLength();
        if (docLen == 0) return;

        for (Map.Entry<Integer, Integer> entry : cursorTracker.getAll().entrySet()) {
            int uid = entry.getKey();
            int caretPos = Math.max(0, Math.min(entry.getValue(), docLen));
            int anchor = Math.max(0, Math.min(caretPos, docLen - 1));
            Color c = CURSOR_COLORS[uid % CURSOR_COLORS.length];
            try {
                hl.addHighlight(anchor, anchor + 1, new CursorPainter(c, caretPos));
            } catch (BadLocationException e) {
                // stale cursor position — skip silently
            }
        }
    }

    /** Draws a 2px vertical colored line at the character's left edge to represent a remote cursor. */
    private static class CursorPainter implements Highlighter.HighlightPainter {
        private final Color color;
        private final int caretPos;

        CursorPainter(Color color, int caretPos) {
            this.color = color;
            this.caretPos = caretPos;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                int len = c.getDocument().getLength();
                if (len == 0) return;

                int clamped = Math.max(0, Math.min(caretPos, len));
                Rectangle r;
                int x;

                if (clamped < len) {
                    r = c.modelToView(clamped);
                    if (r == null) return;
                    x = r.x;
                } else {
                    r = c.modelToView(len - 1);
                    if (r == null) return;
                    x = r.x + r.width;
                }

                g.setColor(color);
                g.fillRect(x, r.y, 2, r.height);
            } catch (BadLocationException e) {
                // ignore
            }
        }
    }

    // ─── Formatting ──────────────────────────────────────────────────────────

    public void applyBold() {
        if (!isEditor) return;
        applyFormatting(true, false);
    }

    public void applyItalic() {
        if (!isEditor) return;
        applyFormatting(false, true);
    }

    private void applyFormatting(boolean bold, boolean italic) {
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (start == end) return;

        List<CRDTChar> visible = crdt.getVisibleChars();
        for (int i = start; i < end && i < visible.size(); i++) {
            if (bold) crdt.setBold(visible.get(i).id, true);
            if (italic) crdt.setItalic(visible.get(i).id, true);
        }
        refreshDisplay();
    }

    // ─── Import / Export ─────────────────────────────────────────────────────

    public String getPlainText() {
        // Use the visible textPane content as the authoritative source for export —
        // avoids any CRDT sync edge cases and exports exactly what the user sees.
        return textPane.getText();
    }

    public void loadPlainText(String text) {
        crdt = new CharacterCRDT();
        clock = new Clock();
        cursorTracker.clear();
        caretCharId = null;

        for (int i = 0; i < text.length(); i++) {
            int t = clock.tick();
            CharId id = new CharId(t, localUserID);
            CharId parentID = (i == 0) ? null : new CharId(t - 1, localUserID);
            crdt.insert(id, text.charAt(i), parentID);
        }
        refreshDisplay();
    }

    // ─── Integration Points ───────────────────────────────────────────────────

    public void setNetworkSender(NetworkSender sender) {
        this.networkSender = sender;
    }

    /** Exposes the CRDT so EditorWindow can share it with WebSocketClient. */
    public CharacterCRDT getCRDT() {
        return crdt;
    }

    /** Exposes the Clock so EditorWindow can share it with WebSocketClient. */
    public Clock getClock() {
        return clock;
    }

    /** Updates session state without triggering a network connection. */
    public void setSessionInfo(String sid, int uid) {
        this.sessionID = sid;
        this.localUserID = uid;
    }

    /**
     * Receives remote cursor positions as CharIds (from WebSocketClient snapshot),
     * converts them to integer offsets using the local CRDT, and re-renders cursors.
     * Called from EditorWindow's refresh callback on the EDT.
     */
    public void updateRemoteCursorsFromCharIds(Map<Integer, CharId> charIdCursors) {
        cursorTracker.clear();
        List<CRDTChar> visible = crdt.getVisibleChars();
        for (Map.Entry<Integer, CharId> entry : charIdCursors.entrySet()) {
            CharId cid = entry.getValue();
            if (cid == null) {
                cursorTracker.update(entry.getKey(), 0);
            } else {
                boolean found = false;
                for (int i = 0; i < visible.size(); i++) {
                    if (visible.get(i).id.equals(cid)) {
                        cursorTracker.update(entry.getKey(), i + 1);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // If the referenced char is not visible yet (or got tombstoned),
                    // place cursor at end to avoid a stale one-char lag.
                    cursorTracker.update(entry.getKey(), visible.size());
                }
            }
        }
        renderRemoteCursors();
    }

    // ─── Envelope Builders ───────────────────────────────────────────────────

    private String buildOperationEnvelope(Operation op) {
        // OperationSerializer.serialize() returns a JSON object — embed directly as the "data" value
        String opJson = OperationSerializer.serialize(op);
        return "{\"action\":\"operation\""
            + ",\"sessionID\":\"" + sessionID + "\""
            + ",\"userID\":" + localUserID
            + ",\"role\":\"" + (isEditor ? "editor" : "viewer") + "\""
            + ",\"data\":" + opJson + "}";
    }

    private String buildCursorEnvelope() {
        // Encode cursor as CharId (afterUserID/afterClock) — WebSocketClient.sendCursorPosition() expects this format
        int afterUserID = (caretCharId == null) ? -1 : caretCharId.userID;
        int afterClock  = (caretCharId == null) ? -1 : caretCharId.counter;
        return "{\"action\":\"cursor\""
            + ",\"sessionID\":\"" + sessionID + "\""
            + ",\"userID\":" + localUserID
            + ",\"role\":\"" + (isEditor ? "editor" : "viewer") + "\""
            + ",\"data\":{\"afterUserID\":" + afterUserID + ",\"afterClock\":" + afterClock + "}}";
    }
}
