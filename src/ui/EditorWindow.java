package ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crdt.character.CharId;
import network.WebSocketClient;
import operations.Operation;
import serializations.OperationSerializer;
import session.CollaborationSession;
import session.CollaborationSession.UserRole;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

public class EditorWindow extends JFrame {

    // ─── Document State ───────────────────────────────────────────────────────

    private String currentDocId   = null;
    private String currentDocName = "Untitled";
    private UserRole currentRole;

    // ─── UI Components ────────────────────────────────────────────────────────

    private final EditorPane editorPane;
    private final UserPanel  userPanel;

    // Toolbar buttons
    private final JToggleButton boldBtn;
    private final JToggleButton italicBtn;
    private final JButton undoBtn;
    private final JButton redoBtn;
    private final JButton shareBtn;

    // Status bar
    private final JLabel statusLabel;
    private final JLabel roleLabel;
    private final JLabel docNameLabel;
    private final JLabel savedLabel;

    // ─── Network ──────────────────────────────────────────────────────────────

    private WebSocketClient wsClient;

    // ─── Auto-Save ────────────────────────────────────────────────────────────

    private Timer autoSaveTimer;

    // ─── Constructor ─────────────────────────────────────────────────────────

    public EditorWindow(int userID, String sessionID, CollaborationSession session, boolean isEditor) {
        currentRole = isEditor ? UserRole.EDITOR : UserRole.VIEWER;

        editorPane = new EditorPane(userID, sessionID, isEditor);
        userPanel  = new UserPanel(session);

        // --- Menu bar
        setJMenuBar(buildMenuBar());

        // --- Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        boldBtn = new JToggleButton("B");
        boldBtn.setFont(boldBtn.getFont().deriveFont(Font.BOLD));
        boldBtn.setToolTipText("Bold");
        boldBtn.setFocusable(false);
        boldBtn.addActionListener(e -> editorPane.applyBold());

        italicBtn = new JToggleButton("I");
        italicBtn.setFont(italicBtn.getFont().deriveFont(Font.ITALIC));
        italicBtn.setToolTipText("Italic");
        italicBtn.setFocusable(false);
        italicBtn.addActionListener(e -> editorPane.applyItalic());

        undoBtn = new JButton("Undo");
        undoBtn.setToolTipText("Undo (Ctrl+Z)");
        undoBtn.addActionListener(e -> handleUndo());

        redoBtn = new JButton("Redo");
        redoBtn.setToolTipText("Redo (Ctrl+Y)");
        redoBtn.addActionListener(e -> handleRedo());

        shareBtn = new JButton("Share");
        shareBtn.setToolTipText("Show sharing codes for this document");
        shareBtn.addActionListener(e -> handleShare());

        JButton joinBtn = new JButton("Join");
        joinBtn.setToolTipText("Join a session by entering a share code");
        joinBtn.addActionListener(e -> showJoinByCodeDialog());

        JButton connectBtn = new JButton("Connect");
        connectBtn.setToolTipText("Connect to a collaboration server");
        connectBtn.addActionListener(e -> showConnectDialog());

        toolbar.add(boldBtn);
        toolbar.add(italicBtn);
        toolbar.addSeparator();
        toolbar.add(undoBtn);
        toolbar.add(redoBtn);
        toolbar.addSeparator();
        toolbar.add(shareBtn);
        toolbar.add(joinBtn);
        toolbar.addSeparator();
        toolbar.add(connectBtn);

        // --- Status bar
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        docNameLabel = new JLabel(currentDocName);
        docNameLabel.setFont(docNameLabel.getFont().deriveFont(Font.BOLD));

        roleLabel = new JLabel(isEditor ? "Editor" : "Viewer");
        roleLabel.setForeground(isEditor ? new Color(0, 100, 0) : new Color(110, 110, 110));

        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);

        leftStatus.add(docNameLabel);
        leftStatus.add(makeSep());
        leftStatus.add(roleLabel);
        leftStatus.add(makeSep());
        leftStatus.add(statusLabel);

        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        savedLabel = new JLabel("");
        savedLabel.setForeground(new Color(0, 140, 0));
        rightStatus.add(savedLabel);

        southPanel.add(leftStatus,  BorderLayout.WEST);
        southPanel.add(rightStatus, BorderLayout.EAST);

        // --- Layout
        setLayout(new BorderLayout());
        add(toolbar,    BorderLayout.NORTH);
        add(editorPane, BorderLayout.CENTER);
        add(userPanel,  BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        // --- Keyboard shortcuts (Ctrl+Z / Ctrl+Y)
        bindKeyboardShortcuts();

        // --- Sync Bold/Italic button highlight with caret position
        editorPane.setOnFormattingChange(() -> {
            boldBtn.setSelected(editorPane.isBoldAtCaret());
            italicBtn.setSelected(editorPane.isItalicAtCaret());
        });

        // --- Apply role restrictions to all buttons
        applyRoleMode(isEditor);

        // --- Start 30-second auto-save timer
        startAutoSave();

        // --- Frame setup
        updateTitle();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ─── Menu Bar ─────────────────────────────────────────────────────────────

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newItem    = new JMenuItem("New File");
        JMenuItem openItem   = new JMenuItem("Open File");
        JMenuItem renameItem = new JMenuItem("Rename File");
        JMenuItem deleteItem = new JMenuItem("Delete File");

        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));

        newItem.addActionListener(e    -> handleNewFile());
        openItem.addActionListener(e   -> handleOpenFile());
        renameItem.addActionListener(e -> handleRenameFile());
        deleteItem.addActionListener(e -> handleDeleteFile());

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(renameItem);
        fileMenu.add(deleteItem);
        fileMenu.addSeparator();

        JMenuItem importItem = new JMenuItem("Import .txt");
        JMenuItem exportItem = new JMenuItem("Export .txt");
        importItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
        importItem.addActionListener(e -> handleImport());
        exportItem.addActionListener(e -> handleExport());

        fileMenu.add(importItem);
        fileMenu.add(exportItem);

        menuBar.add(fileMenu);
        return menuBar;
    }

    // ─── Keyboard Shortcuts ───────────────────────────────────────────────────

    private void bindKeyboardShortcuts() {
        InputMap  im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");

        am.put("undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { handleUndo(); }
        });
        am.put("redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { handleRedo(); }
        });
    }

    // ─── File Menu Handlers ───────────────────────────────────────────────────

    private void handleNewFile() {
        String name = JOptionPane.showInputDialog(this, "Enter file name:", "New File", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        // TODO: wire to FileManager.createNewFile(name) once Member 2 delivers it
        // currentDocId = fileManager.createNewFile(name.trim());
        currentDocName = name.trim();
        editorPane.clearDocument();
        updateTitle();
        showSaved("New file created");
    }

    private void handleOpenFile() {
        // TODO: wire to FileManager.listAllFiles() + loadFile() once Member 2 delivers it
        JOptionPane.showMessageDialog(this,
            "Open File requires FileManager (Member 2).\nWill be connected when available.",
            "Coming Soon", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleRenameFile() {
        if (currentDocId == null) {
            JOptionPane.showMessageDialog(this, "No file is currently open.", "Rename File", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newName = JOptionPane.showInputDialog(this, "Enter new name:", currentDocName);
        if (newName == null || newName.trim().isEmpty()) return;

        // TODO: wire to FileManager.renameFile(currentDocId, newName) once Member 2 delivers it
        currentDocName = newName.trim();
        updateTitle();
        showSaved("Renamed");
    }

    private void handleDeleteFile() {
        if (currentDocId == null) {
            JOptionPane.showMessageDialog(this, "No file is currently open.", "Delete File", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
            "Delete \"" + currentDocName + "\"? This cannot be undone.",
            "Delete File", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        // TODO: wire to FileManager.deleteFile(currentDocId) once Member 2 delivers it
        currentDocId   = null;
        currentDocName = "Untitled";
        editorPane.clearDocument();
        updateTitle();
    }

    // ─── Import / Export ──────────────────────────────────────────────────────

    private void handleImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        // TODO: replace with ImportExportManager.importFromTxt() once Member 3 delivers it,
        //       which will also parse *bold* and _italic_ markers into the CRDT.
        try (BufferedReader reader = new BufferedReader(new FileReader(chooser.getSelectedFile()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            editorPane.loadPlainText(sb.toString());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to read file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        }
        SwingUtilities.invokeLater(editorPane::repaint);
    }

    private void handleExport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().endsWith(".txt")) file = new File(file.getPath() + ".txt");

        // TODO: replace with ImportExportManager.exportToTxt() once Member 3 delivers it,
        //       which will embed *bold* and _italic_ markers in the output.
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(editorPane.getPlainText());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to write file: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
        SwingUtilities.invokeLater(editorPane::repaint);
    }

    // ─── Undo / Redo ──────────────────────────────────────────────────────────

    private void handleUndo() {
        if (currentRole != UserRole.EDITOR) return;
        // TODO: wire to UndoRedoManager.undo(crdt) once Member 3 delivers it
        // undoRedoManager.undo(editorPane.getCRDT());
        // editorPane.refreshDisplay();
        System.out.println("[EditorWindow] Undo triggered — UndoRedoManager not yet connected");
    }

    private void handleRedo() {
        if (currentRole != UserRole.EDITOR) return;
        // TODO: wire to UndoRedoManager.redo(crdt) once Member 3 delivers it
        // undoRedoManager.redo(editorPane.getCRDT());
        // editorPane.refreshDisplay();
        System.out.println("[EditorWindow] Redo triggered — UndoRedoManager not yet connected");
    }

    // ─── Share Dialog ─────────────────────────────────────────────────────────

    private void handleShare() {
        if (currentDocId == null) {
            JOptionPane.showMessageDialog(this,
                "No file is open. Create or open a file first.",
                "Share", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // TODO: replace placeholder strings with ShareCodeManager.getCodesForDocument(currentDocId, EDITOR)
        //       once Member 2 delivers it.
        String editorCode = "????????";
        String viewerCode = "????????";

        JDialog dialog = new JDialog(this, "Share Codes", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        dialog.add(new JLabel("Editor Code:"), gbc);
        gbc.gridx = 1;
        JTextField editorField = new JTextField(editorCode, 12);
        editorField.setEditable(false);
        editorField.setFont(new Font("Monospaced", Font.BOLD, 15));
        dialog.add(editorField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        dialog.add(new JLabel("Viewer Code:"), gbc);
        gbc.gridx = 1;
        JTextField viewerField = new JTextField(viewerCode, 12);
        viewerField.setEditable(false);
        viewerField.setFont(new Font("Monospaced", Font.BOLD, 15));
        dialog.add(viewerField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        dialog.add(closeBtn, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ─── Join by Code Dialog ──────────────────────────────────────────────────

    private void showJoinByCodeDialog() {
        JDialog dialog = new JDialog(this, "Join by Share Code", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        dialog.add(new JLabel("Enter 8-character share code:"), gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JTextField codeField = new JTextField(12);
        codeField.setFont(new Font("Monospaced", Font.BOLD, 15));
        dialog.add(codeField, gbc);

        JButton joinBtn = new JButton("Join");
        joinBtn.addActionListener(e -> {
            String code = codeField.getText().trim().toUpperCase();
            if (code.length() != 8 || !code.matches("[A-Z0-9]+")) {
                JOptionPane.showMessageDialog(dialog,
                    "Code must be exactly 8 alphanumeric characters (A-Z, 0-9).",
                    "Invalid Code", JOptionPane.ERROR_MESSAGE);
                return;
            }
            dialog.dispose();

            // TODO: replace with ShareCodeManager.joinByCode(code) once Member 2 delivers it.
            //       That call returns a docId and a UserRole, then:
            //         currentDocId = result.docId;
            //         currentRole  = result.role;
            //         applyRoleMode(currentRole == UserRole.EDITOR);
            //         BlockCRDT crdt = fileManager.openFile(currentDocId);
            //         editorPane.loadFromCRDT(crdt);
            JOptionPane.showMessageDialog(this,
                "Join by code requires ShareCodeManager (Member 2).\nCode entered: " + code,
                "Coming Soon", JOptionPane.INFORMATION_MESSAGE);
        });

        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.CENTER;
        dialog.add(joinBtn, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ─── Connect to Server Dialog (WebSocket) ─────────────────────────────────

    private void showConnectDialog() {
        JDialog dialog = new JDialog(this, "Connect to Server", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        JTextField   serverField  = new JTextField("ws://127.0.0.1:8081", 20);
        JTextField   sessionField = new JTextField(16);
        JTextField   userIdField  = new JTextField(16);
        JRadioButton editorRadio  = new JRadioButton("Editor", true);
        JRadioButton viewerRadio  = new JRadioButton("Viewer");
        ButtonGroup  roleGroup    = new ButtonGroup();
        roleGroup.add(editorRadio);
        roleGroup.add(viewerRadio);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Server URL:"), gbc);
        gbc.gridx = 1;               dialog.add(serverField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Session ID:"), gbc);
        gbc.gridx = 1;               dialog.add(sessionField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("User ID:"), gbc);
        gbc.gridx = 1;               dialog.add(userIdField, gbc);
        gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("Role:"), gbc);
        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rolePanel.add(editorRadio);
        rolePanel.add(viewerRadio);
        gbc.gridx = 1; dialog.add(rolePanel, gbc);

        JButton ok = new JButton("Connect");
        ok.addActionListener(e -> {
            String serverUrl = serverField.getText().trim();
            String sid       = sessionField.getText().trim();
            String uidStr    = userIdField.getText().trim();
            if (serverUrl.isEmpty() || sid.isEmpty() || uidStr.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill in all fields.");
                return;
            }
            try {
                int    uid  = Integer.parseInt(uidStr);
                String role = editorRadio.isSelected() ? "editor" : "viewer";
                dialog.dispose();
                connectToServer(serverUrl, sid, uid, role);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "User ID must be a number.");
            }
        });

        gbc.gridx = 1; gbc.gridy = 4; gbc.anchor = GridBagConstraints.EAST;
        dialog.add(ok, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ─── Auto-Save ────────────────────────────────────────────────────────────

    private void startAutoSave() {
        autoSaveTimer = new Timer("auto-save", true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                SwingUtilities.invokeLater(() -> triggerSave());
            }
        }, 30_000, 30_000);
    }

    /** Called after every insert/delete/format and by the 30-second timer. */
    public void triggerSave() {
        if (currentDocId == null) return;
        // TODO: call FileRepository.saveFile(currentDocId, currentDocName, editorCode, viewerCode, blockCRDT)
        //       once Member 1 delivers FileRepository.
        showSaved("Saved");
    }

    private void showSaved(String message) {
        savedLabel.setText(message);
        new Timer("saved-fade", true).schedule(new TimerTask() {
            @Override public void run() {
                SwingUtilities.invokeLater(() -> savedLabel.setText(""));
            }
        }, 3_000);
    }

    // ─── Role Mode ────────────────────────────────────────────────────────────

    private void applyRoleMode(boolean canEdit) {
        editorPane.setEditingEnabled(canEdit);
        boldBtn.setEnabled(canEdit);
        italicBtn.setEnabled(canEdit);
        undoBtn.setEnabled(canEdit);
        redoBtn.setEnabled(canEdit);
        shareBtn.setVisible(canEdit);   // viewers never see the Share button

        roleLabel.setText(canEdit ? "Editor" : "Viewer");
        roleLabel.setForeground(canEdit ? new Color(0, 100, 0) : new Color(110, 110, 110));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void updateTitle() {
        setTitle("Collaborative Text Editor — " + currentDocName);
        docNameLabel.setText(currentDocName);
    }

    private static JLabel makeSep() {
        return new JLabel(" | ");
    }

    // ─── Server Connection ────────────────────────────────────────────────────

    private void connectToServer(String serverUrl, String sid, int uid, String role) {
        if (wsClient != null) {
            wsClient.disconnectFromServer();
            wsClient = null;
        }

        editorPane.setSessionInfo(sid, uid);
        setStatus("Connecting…");

        UserRole userRole = "editor".equalsIgnoreCase(role) ? UserRole.EDITOR : UserRole.VIEWER;
        currentRole = userRole;
        applyRoleMode(userRole == UserRole.EDITOR);

        Runnable onRemoteUpdate = () -> SwingUtilities.invokeLater(() -> {
            try {
                editorPane.refreshDisplay();
                editorPane.updateRemoteCursorsFromCharIds(wsClient.getRemoteCursorsSnapshot());

                CollaborationSession updated = new CollaborationSession(sid);
                wsClient.getActiveUsersSnapshot().forEach((userId, r) ->
                    updated.addUser(userId, "EDITOR".equalsIgnoreCase(r) ? UserRole.EDITOR : UserRole.VIEWER)
                );
                userPanel.setSession(updated);
                triggerSave();
            } catch (Exception ex) {
                System.err.println("[EditorWindow] Remote update error: " + ex.getMessage());
            }
            setStatus("Connected");
        });

        try {
            URI uri = new URI(serverUrl);
            wsClient = new WebSocketClient(
                uri, sid, uid, userRole,
                editorPane.getCRDT(),
                editorPane.getClock(),
                onRemoteUpdate
            );
            editorPane.setNetworkSender(new NetworkSenderAdapter(wsClient));
            wsClient.connectToServer();
        } catch (URISyntaxException e) {
            JOptionPane.showMessageDialog(this, "Invalid server URL.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public void setStatus(String status) {
        statusLabel.setText(status);
        statusLabel.setForeground("Connected".equals(status) ? new Color(0, 140, 0) : Color.GRAY);
    }

    public void updateSession(CollaborationSession newSession) {
        userPanel.setSession(newSession);
    }

    // ─── Network Sender Adapter ───────────────────────────────────────────────

    private static class NetworkSenderAdapter implements EditorPane.NetworkSender {

        private final WebSocketClient wsClient;

        NetworkSenderAdapter(WebSocketClient wsClient) {
            this.wsClient = wsClient;
        }

        @Override
        public void sendMessage(String jsonMessage) {
            try {
                JsonObject json   = JsonParser.parseString(jsonMessage).getAsJsonObject();
                String     action = json.get("action").getAsString();
                switch (action) {
                    case "operation": {
                        JsonObject data = json.getAsJsonObject("data");
                        Operation op = OperationSerializer.deserialize(data.toString());
                        wsClient.sendOperation(op);
                        break;
                    }
                    case "cursor": {
                        JsonObject data        = json.getAsJsonObject("data");
                        int        afterUserID = data.get("afterUserID").getAsInt();
                        int        afterClock  = data.get("afterClock").getAsInt();
                        CharId     charId      = (afterUserID == -1 || afterClock == -1)
                                ? null : new CharId(afterClock, afterUserID);
                        wsClient.sendCursorPosition(charId);
                        break;
                    }
                    default:
                        System.err.println("[NetworkSenderAdapter] Unknown action: " + action);
                }
            } catch (Exception e) {
                System.err.println("[NetworkSenderAdapter] Failed to route message: " + e.getMessage());
            }
        }

        @Override public void connect(String serverUrl) { wsClient.connectToServer(); }
        @Override public void disconnect()              { wsClient.disconnectFromServer(); }
        @Override public boolean isConnected()          { return wsClient.isOpen(); }
    }

    // ─── Entry Point (local testing) ─────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CollaborationSession session = new CollaborationSession("test-session");
            new EditorWindow(1, "test-session", session, true);
        });
    }
}
