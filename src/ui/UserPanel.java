package ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import session.CollaborationSession;
import session.UserPresence;

public class UserPanel extends JPanel {

    private CollaborationSession session;
    private final DefaultListModel<UserPresence> listModel; // holds the list of active users
    private final JList<UserPresence> userList; // visual component to display users

    private static final Color[] USER_COLORS = {
        new Color(200, 50,  50),
        new Color(50,  100, 200),
        new Color(30,  150, 30),
        new Color(200, 120, 0),
        new Color(150, 40,  170),
        new Color(0,   150, 150)
    };

    public UserPanel(CollaborationSession session) {
        this.session = session;
        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setCellRenderer(new UserCellRenderer());
        userList.setFixedCellHeight(28);
        userList.setSelectionModel(new DefaultListSelectionModel() {
            @Override public void setSelectionInterval(int i, int j) {} // non-interactive
        });

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(180, 0));
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Active Users",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));

        add(new JScrollPane(userList), BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        listModel.clear();
        if (session != null) {
            for (UserPresence u : session.getActiveUsers()) {
                listModel.addElement(u);
            }
        }
    }

    public void setSession(CollaborationSession session) {
        this.session = session;
        refresh();
    }

    // ─── Cell Renderer ───────────────────────────────────────────────────────

    private class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof UserPresence) {
                UserPresence u = (UserPresence) value;
                setText("User " + u.getUserID() + (u.isEditor() ? " [Editor]" : " [Viewer]"));

                Color userColor = USER_COLORS[u.getUserID() % USER_COLORS.length];
                if (!isSelected) setForeground(userColor);

                setFont(getFont().deriveFont(u.isEditor() ? Font.BOLD : Font.ITALIC));
                setIcon(new ColorSquareIcon(userColor, 10));
            }
            return this;
        }
    }

    // Colored square icon used as a visual user indicator
    private static class ColorSquareIcon implements Icon {
        private final Color color;
        private final int size;

        ColorSquareIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, size, size);
            g.setColor(color.darker());
            g.drawRect(x, y, size - 1, size - 1);
        }

        @Override public int getIconWidth()  { return size + 4; }
        @Override public int getIconHeight() { return size; }
    }
}
