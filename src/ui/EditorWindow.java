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
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

public class EditorWindow extends JFrame {

    private final EditorPane editorPane; //the text space in the middle
    private final UserPanel userPanel; // right side user
    private final JLabel statusLabel; // connected / disconnected status
    private final JLabel sessionCodeLabel; // sharable code
    private final JButton boldBtn;
    private final JButton italicBtn;
    private final JButton importBtn;
    private WebSocketClient wsClient; // socket of connection to the server

    public EditorWindow(int userID, String sessionID, CollaborationSession session, boolean isEditor) {
        editorPane = new EditorPane(userID, sessionID, isEditor);
        userPanel  = new UserPanel(session);

        // --- Toolbar UPPER bar of interactions of the user 
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        boldBtn = new JButton("B");
        boldBtn.setFont(boldBtn.getFont().deriveFont(Font.BOLD));
        boldBtn.setEnabled(isEditor);
        boldBtn.addActionListener(e -> editorPane.applyBold());

        italicBtn = new JButton("I");
        italicBtn.setFont(italicBtn.getFont().deriveFont(Font.ITALIC));
        italicBtn.setEnabled(isEditor);
        italicBtn.addActionListener(e -> editorPane.applyItalic());

        JButton connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> showJoinDialog());

        importBtn = new JButton("Import .txt");
        importBtn.addActionListener(e -> handleImport());
        importBtn.setEnabled(isEditor);

        JButton exportBtn = new JButton("Export .txt");
        exportBtn.addActionListener(e -> handleExport());

        toolbar.add(boldBtn);
        toolbar.add(italicBtn);
        toolbar.addSeparator();
        toolbar.add(connectBtn);
        toolbar.addSeparator();
        toolbar.add(importBtn);
        toolbar.add(exportBtn);

        // --- Status bar (the bar at the bottom indicating connection status and session code)
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);
        southPanel.add(statusLabel);

        if (isEditor) {
            sessionCodeLabel = new JLabel("  |  Session: not connected");
            southPanel.add(sessionCodeLabel);
        } else {
            sessionCodeLabel = null;
        }
        

        // --- Layout (app page placement)
        setLayout(new BorderLayout());
        add(toolbar,   BorderLayout.NORTH);
        add(editorPane, BorderLayout.CENTER);
        add(userPanel,  BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        // --- Frame setup (Title - size - close - visibility)
        setTitle("Collaborative Text Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // --- Join Dialog

    private void showJoinDialog() {
        JDialog dialog = new JDialog(this, "Join Session", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        JTextField serverField  = new JTextField("ws://127.0.0.1:8081", 20);
        JTextField sessionField = new JTextField(16);
        JTextField userIdField  = new JTextField(16);
        JRadioButton editorRadio = new JRadioButton("Editor", true);
        JRadioButton viewerRadio = new JRadioButton("Viewer");
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(editorRadio);
        roleGroup.add(viewerRadio);

        gbc.gridx = 0; 
        gbc.gridy = 0; 
        dialog.add(new JLabel("Server URL:"), gbc);
        
        gbc.gridx = 1;                
        dialog.add(serverField, gbc);
        
        gbc.gridx = 0; 
        gbc.gridy = 1; 
        dialog.add(new JLabel("Session ID:"), gbc);
        
        gbc.gridx = 1;                
        dialog.add(sessionField, gbc);
        
        gbc.gridx = 0; 
        gbc.gridy = 2; 
        dialog.add(new JLabel("User ID:"), gbc);
        
        gbc.gridx = 1;                
        dialog.add(userIdField, gbc);
        
        gbc.gridx = 0; 
        gbc.gridy = 3; 
        dialog.add(new JLabel("Role:"), gbc);
        
        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rolePanel.add(editorRadio);
        rolePanel.add(viewerRadio);
        gbc.gridx = 1; dialog.add(rolePanel, gbc); // editor/viewer selection

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
                connectToSession(serverUrl, sid, uid, role);
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

    // --- Import / Export 

    private void handleImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(chooser.getSelectedFile()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            editorPane.loadPlainText(sb.toString());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to read file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().endsWith(".txt")) {
            file = new File(file.getPath() + ".txt");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(editorPane.getPlainText());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to write file: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
        // Force repaint after JFileChooser closes to prevent visual glitch on Windows
        SwingUtilities.invokeLater(editorPane::repaint);
    }

    // --- Public API

    // Shows the session ID as the sharing code (visible immediately, even without network)
    public void setSessionId(String sessionId) {
        if (sessionCodeLabel != null) {
            sessionCodeLabel.setText("  |  Share this Session ID: \"" + sessionId + "\"");
        }
    }

    // Updates connection status label
    public void setStatus(String status) {
        statusLabel.setText(status);
        statusLabel.setForeground("Connected".equals(status) ? new Color(0, 140, 0) : Color.GRAY);
    }

    // Replaces the local session mirror and refreshes the user list panel
    public void updateSession(CollaborationSession newSession) {
        userPanel.setSession(newSession);
    }

    // --- Session Connect

    private void connectToSession(String serverUrl, String sid, int uid, String role) {
        // Tear down any existing connection first
        if (wsClient != null) {
            wsClient.disconnectFromServer();
            wsClient = null;
        } 

        editorPane.setSessionInfo(sid, uid);
        setSessionId(sid);
        setStatus("Connecting…");

        UserRole userRole = "editor".equalsIgnoreCase(role) ? UserRole.EDITOR : UserRole.VIEWER;
        applyRoleMode(userRole == UserRole.EDITOR);

        Runnable onRemoteUpdate = () -> SwingUtilities.invokeLater(() -> {
            try {
                editorPane.refreshDisplay();
                editorPane.updateRemoteCursorsFromCharIds(wsClient.getRemoteCursorsSnapshot());

                CollaborationSession updated = new CollaborationSession(sid);
                wsClient.getActiveUsersSnapshot().forEach((userId, r) ->
                    updated.addUser(userId, "EDITOR".equalsIgnoreCase(r) ? UserRole.EDITOR : UserRole.VIEWER)
                );
                updateSession(updated);
            } catch (Exception ex) {
                System.err.println("[EditorWindow] Remote update error: " + ex.getMessage());
                ex.printStackTrace();
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

    private void applyRoleMode(boolean canEdit) {
        editorPane.setEditingEnabled(canEdit);
        boldBtn.setEnabled(canEdit);
        italicBtn.setEnabled(canEdit);
        importBtn.setEnabled(canEdit);
        if (sessionCodeLabel != null) {
            sessionCodeLabel.setVisible(canEdit);
        }
    }

    // --- Network Sender Adapter

    // Bridges EditorPane.NetworkSender to Member 3's WebSocketClient API
    private static class NetworkSenderAdapter implements EditorPane.NetworkSender {

        private final WebSocketClient wsClient;

        NetworkSenderAdapter(WebSocketClient wsClient) {
            this.wsClient = wsClient;
        }

        @Override
        public void sendMessage(String jsonMessage) {
            try {
                JsonObject json = JsonParser.parseString(jsonMessage).getAsJsonObject();
                String action = json.get("action").getAsString();

                switch (action) {
                    case "operation": {
                        JsonObject data = json.getAsJsonObject("data");
                        Operation op = OperationSerializer.deserialize(data.toString());
                        wsClient.sendOperation(op);
                        break;
                    }
                    case "cursor": {
                        JsonObject data = json.getAsJsonObject("data");
                        int afterUserID = data.get("afterUserID").getAsInt();
                        int afterClock  = data.get("afterClock").getAsInt();
                        CharId charId = (afterUserID == -1 || afterClock == -1) ? null : new CharId(afterClock, afterUserID);
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

        @Override public void connect(String serverUrl)  { wsClient.connectToServer(); }
        @Override public void disconnect()               { wsClient.disconnectFromServer(); }
        @Override public boolean isConnected()           { return wsClient.isOpen(); }
    }

    // --- Entry point for local testing

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CollaborationSession session = new CollaborationSession("test-session");
            new EditorWindow(1, "test-session", session, true);
        });
    }
}
