package ui;

import crdt.character.CharacterCRDT;
import crdt.character.CharId;
import crdt.character.CRDTChar;
import crdt.utils.Clock;
import operations.*;
import serializations.OperationSerializer;
import session.CollaborationSession;
import session.CollaborationSession.UserRole;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class EditorPane extends JPanel {

    // ─── Interfaces for Member 3 ──────────────────────────────────────────────

    /** Member 3's WebSocketClient must implement this. EditorPane calls it to send messages. */
    public interface NetworkSender {
        void sendMessage(String jsonMessage);
        void connect(String serverUrl);
        void disconnect();
        boolean isConnected();
    }

    /**
     * Member 3's MessageHandler must call these when server messages arrive.
     * Obtain the instance via editorPane.getMessageCallback().
     *
     * usersPipeSeparated format: "1:EDITOR|2:VIEWER|"
     * operationJson: raw op JSON from OperationSerializer (e.g. {"op":"insert",...})
     */
    public interface MessageCallback {
        void onRemoteOperation(String operationJson);
        void onRemoteCursorUpdate(int userID, int position);
        void onUserListUpdate(String usersPipeSeparated);
        void onHistoryReceived(List<String> operationJsonList);
        void onConnectionEstablished();
        void onConnectionLost(String reason);
    }

    // ─── Fields ──────────────────────────────────────────────────────────────

    private final JTextPane textPane;
    private CharacterCRDT crdt;
    private Clock clock;
    private int localUserID;
    private String sessionID;
    private final boolean isEditor;

    private boolean suppressDocumentEvents = false;
    private CharId caretCharId = null;

    private NetworkSender networkSender;
    private final MessageCallback messageCallback;

    private final Map<Integer, Integer> remoteCursorPositions = new HashMap<>();

    private static final Color[] CURSOR_COLORS = {
        Color.RED,
        new Color(30, 100, 210),
        new Color(20, 150, 20),
        new Color(210, 120, 0),
        new Color(140, 30, 170),
        new Color(0, 160, 160)
    };

    private static final String SERVER_URL = "ws://localhost:8080";

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

        messageCallback = new MessageCallbackImpl();

        attachDocumentListener();
        attachCaretListener();
    }

    // ─── Document Listener ───────────────────────────────────────────────────

    private void attachDocumentListener() {
        textPane.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                if (suppressDocumentEvents) return;
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
                            networkSender.sendMessage(buildOperationEnvelope(op));
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
                if (suppressDocumentEvents) return;
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
            if (suppressDocumentEvents) return;
            int pos = e.getDot();
            List<CRDTChar> visible = crdt.getVisibleChars();
            caretCharId = (pos == 0 || visible.isEmpty())
                    ? null
                    : visible.get(Math.min(pos - 1, visible.size() - 1)).id;

            if (networkSender != null && networkSender.isConnected()) {
                networkSender.sendMessage(buildCursorEnvelope(pos));
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

        for (Map.Entry<Integer, Integer> entry : remoteCursorPositions.entrySet()) {
            int uid = entry.getKey();
            int pos = Math.max(0, Math.min(entry.getValue(), docLen - 1));
            Color c = CURSOR_COLORS[uid % CURSOR_COLORS.length];
            try {
                hl.addHighlight(pos, pos + 1, new CursorPainter(c));
            } catch (BadLocationException e) {
                // stale cursor position — skip silently
            }
        }
    }

    /** Draws a 2px vertical colored line at the character's left edge to represent a remote cursor. */
    private static class CursorPainter implements Highlighter.HighlightPainter {
        private final Color color;

        CursorPainter(Color color) {
            this.color = color;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r = c.modelToView(p0);
                if (r != null) {
                    g.setColor(color);
                    g.fillRect(r.x, r.y, 2, r.height);
                }
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

    // ─── Session / Network ───────────────────────────────────────────────────

    public void joinSession(String sid, int uid, String role) {
        this.sessionID = sid;
        this.localUserID = uid;

        if (networkSender == null) {
            // Network layer not available yet (waiting for Member 3's WebSocketClient)
            JOptionPane.showMessageDialog(this,
                "Network layer not yet available.\n"
                + "Session ID saved: \"" + sid + "\"\n\n"
                + "Share this ID with others so they can join when the network is ready.\n"
                + "(Waiting for Member 3 - WebSocketClient implementation)",
                "No Network Connection",
                JOptionPane.INFORMATION_MESSAGE);
            // Notify EditorWindow to show the session ID in the status bar
            EditorWindow win = (EditorWindow) SwingUtilities.getWindowAncestor(this);
            if (win != null) win.setSessionId(sid);
            return;
        }

        networkSender.connect(SERVER_URL);
        networkSender.sendMessage(
            "{\"action\":\"join\",\"sessionID\":\"" + sid
            + "\",\"userID\":" + uid
            + ",\"role\":\"" + role.toLowerCase()
            + "\",\"data\":{}}"
        );
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
        remoteCursorPositions.clear();
        caretCharId = null;

        for (int i = 0; i < text.length(); i++) {
            int t = clock.tick();
            CharId id = new CharId(t, localUserID);
            CharId parentID = (i == 0) ? null : new CharId(t - 1, localUserID);
            crdt.insert(id, text.charAt(i), parentID);
        }
        refreshDisplay();
    }

    // ─── Integration Points for Member 3 ─────────────────────────────────────

    public void setNetworkSender(NetworkSender sender) {
        this.networkSender = sender;
    }

    public MessageCallback getMessageCallback() {
        return messageCallback;
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

    private String buildCursorEnvelope(int pos) {
        return "{\"action\":\"cursor\""
            + ",\"sessionID\":\"" + sessionID + "\""
            + ",\"userID\":" + localUserID
            + ",\"role\":\"" + (isEditor ? "editor" : "viewer") + "\""
            + ",\"data\":{\"position\":" + pos + "}}";
    }

    // ─── MessageCallback Implementation ──────────────────────────────────────

    private class MessageCallbackImpl implements MessageCallback {

        @Override
        public void onRemoteOperation(String operationJson) {
            SwingUtilities.invokeLater(() -> {
                try {
                    Operation op = OperationSerializer.deserialize(operationJson);
                    clock.update(op.clock);
                    op.apply(crdt);
                    refreshDisplay();
                } catch (Exception e) {
                    System.err.println("[EditorPane] Failed to apply remote op: " + e.getMessage());
                }
            });
        }

        @Override
        public void onRemoteCursorUpdate(int userID, int position) {
            SwingUtilities.invokeLater(() -> {
                remoteCursorPositions.put(userID, position);
                renderRemoteCursors();
            });
        }

        @Override
        public void onUserListUpdate(String usersPipeSeparated) {
            SwingUtilities.invokeLater(() -> {
                CollaborationSession updated = new CollaborationSession(sessionID);
                for (String part : usersPipeSeparated.split("\\|")) {
                    if (part.trim().isEmpty()) continue;
                    String[] kv = part.split(":");
                    if (kv.length == 2) {
                        try {
                            int uid = Integer.parseInt(kv[0].trim());
                            UserRole role = UserRole.valueOf(kv[1].trim().toUpperCase());
                            updated.addUser(uid, role);
                        } catch (Exception e) {
                            System.err.println("[EditorPane] Failed to parse user entry: " + part);
                        }
                    }
                }
                EditorWindow win = (EditorWindow) SwingUtilities.getWindowAncestor(EditorPane.this);
                if (win != null) win.updateSession(updated);
            });
        }

        @Override
        public void onHistoryReceived(List<String> operationJsonList) {
            SwingUtilities.invokeLater(() -> {
                for (String json : operationJsonList) {
                    try {
                        Operation op = OperationSerializer.deserialize(json);
                        clock.update(op.clock);
                        op.apply(crdt);
                    } catch (Exception e) {
                        System.err.println("[EditorPane] Failed to apply history op: " + e.getMessage());
                    }
                }
                refreshDisplay();
            });
        }

        @Override
        public void onConnectionEstablished() {
            SwingUtilities.invokeLater(() -> {
                EditorWindow win = (EditorWindow) SwingUtilities.getWindowAncestor(EditorPane.this);
                if (win != null) win.setStatus("Connected");
            });
        }

        @Override
        public void onConnectionLost(String reason) {
            SwingUtilities.invokeLater(() -> {
                EditorWindow win = (EditorWindow) SwingUtilities.getWindowAncestor(EditorPane.this);
                if (win != null) win.setStatus("Disconnected");
                JOptionPane.showMessageDialog(
                    EditorPane.this,
                    "Connection lost: " + reason,
                    "Disconnected",
                    JOptionPane.WARNING_MESSAGE
                );
            });
        }
    }
}
