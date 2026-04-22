package ui;

import session.CollaborationSession;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;

public class EditorWindow extends JFrame {

    private final EditorPane editorPane;
    private final UserPanel userPanel;
    private final JLabel statusLabel;
    private final JLabel sessionCodeLabel; // null when isEditor == false
    private CollaborationSession session;

    public EditorWindow(int userID, String sessionID, CollaborationSession session, boolean isEditor) {
        this.session = session;

        editorPane = new EditorPane(userID, sessionID, isEditor);
        userPanel  = new UserPanel(session);

        // ─── Toolbar ─────────────────────────────────────────────────────────
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton boldBtn = new JButton("B");
        boldBtn.setFont(boldBtn.getFont().deriveFont(Font.BOLD));
        boldBtn.setEnabled(isEditor);
        boldBtn.addActionListener(e -> editorPane.applyBold());

        JButton italicBtn = new JButton("I");
        italicBtn.setFont(italicBtn.getFont().deriveFont(Font.ITALIC));
        italicBtn.setEnabled(isEditor);
        italicBtn.addActionListener(e -> editorPane.applyItalic());

        JButton connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> showJoinDialog());

        JButton importBtn = new JButton("Import .txt");
        importBtn.addActionListener(e -> handleImport());

        JButton exportBtn = new JButton("Export .txt");
        exportBtn.addActionListener(e -> handleExport());

        toolbar.add(boldBtn);
        toolbar.add(italicBtn);
        toolbar.addSeparator();
        toolbar.add(connectBtn);
        toolbar.addSeparator();
        toolbar.add(importBtn);
        toolbar.add(exportBtn);

        // ─── Status bar ──────────────────────────────────────────────────────
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);
        southPanel.add(statusLabel);

        if (isEditor) {
            sessionCodeLabel = new JLabel("  |  Session: not connected");
            southPanel.add(sessionCodeLabel);
        } else {
            sessionCodeLabel = null; // viewers never see sharing codes
        }

        // ─── Layout ──────────────────────────────────────────────────────────
        setLayout(new BorderLayout());
        add(toolbar,   BorderLayout.NORTH);
        add(editorPane, BorderLayout.CENTER);
        add(userPanel,  BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        // ─── Frame setup ─────────────────────────────────────────────────────
        setTitle("Collaborative Text Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ─── Join Dialog ─────────────────────────────────────────────────────────

    private void showJoinDialog() {
        JDialog dialog = new JDialog(this, "Join Session", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        JTextField sessionField = new JTextField(16);
        JTextField userIdField  = new JTextField(16);
        JRadioButton editorRadio = new JRadioButton("Editor", true);
        JRadioButton viewerRadio = new JRadioButton("Viewer");
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(editorRadio);
        roleGroup.add(viewerRadio);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Session ID:"), gbc);
        gbc.gridx = 1;                dialog.add(sessionField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("User ID:"), gbc);
        gbc.gridx = 1;                dialog.add(userIdField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Role:"), gbc);
        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rolePanel.add(editorRadio);
        rolePanel.add(viewerRadio);
        gbc.gridx = 1; dialog.add(rolePanel, gbc);

        JButton ok = new JButton("Connect");
        ok.addActionListener(e -> {
            String sid    = sessionField.getText().trim();
            String uidStr = userIdField.getText().trim();
            if (sid.isEmpty() || uidStr.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill in all fields.");
                return;
            }
            try {
                int    uid  = Integer.parseInt(uidStr);
                String role = editorRadio.isSelected() ? "editor" : "viewer";
                dialog.dispose();
                editorPane.joinSession(sid, uid, role);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "User ID must be a number.");
            }
        });

        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST;
        dialog.add(ok, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ─── Import / Export ─────────────────────────────────────────────────────

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

    // ─── Public API (called by EditorPane's MessageCallbackImpl) ─────────────

    /** Shows the session ID as the sharing code (visible immediately, even without network). */
    public void setSessionId(String sessionId) {
        if (sessionCodeLabel != null) {
            sessionCodeLabel.setText("  |  Share this Session ID: \"" + sessionId + "\"");
        }
    }

    /** Updates the session codes display (editors only). */
    public void setSessionCodes(String editorCode, String viewerCode) {
        if (sessionCodeLabel != null) {
            sessionCodeLabel.setText("  |  Editor Code: " + editorCode + "   Viewer Code: " + viewerCode);
        }
    }

    /** Updates connection status label. */
    public void setStatus(String status) {
        statusLabel.setText(status);
        statusLabel.setForeground("Connected".equals(status) ? new Color(0, 140, 0) : Color.GRAY);
    }

    /** Replaces the local session mirror and refreshes the user list panel. */
    public void updateSession(CollaborationSession newSession) {
        this.session = newSession;
        userPanel.setSession(newSession);
    }

    /** Refreshes the user list panel without replacing the session object. */
    public void refreshUserPanel() {
        userPanel.refresh();
    }

    /** Returns the EditorPane for external wiring (e.g. attaching NetworkSender). */
    public EditorPane getEditorPane() {
        return editorPane;
    }

    // ─── Entry point for local testing ───────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CollaborationSession session = new CollaborationSession("test-session");
            new EditorWindow(1, "test-session", session, true);
        });
    }
}
