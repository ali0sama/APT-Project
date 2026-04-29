package database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:collab_editor.db";
    private Connection connection;

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        createTablesIfNeeded();
    }

    public void disconnect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createTablesIfNeeded() throws SQLException {
        String docsTable =
            "CREATE TABLE IF NOT EXISTS documents (" +
            "  id          TEXT PRIMARY KEY," +
            "  name        TEXT NOT NULL," +
            "  editor_code TEXT NOT NULL," +
            "  viewer_code TEXT NOT NULL," +
            "  created_at  INTEGER NOT NULL" +
            ")";

        String crdtTable =
            "CREATE TABLE IF NOT EXISTS crdt_state (" +
            "  doc_id    TEXT NOT NULL," +
            "  crdt_json TEXT NOT NULL," +
            "  saved_at  INTEGER NOT NULL," +
            "  PRIMARY KEY (doc_id)" +
            ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(docsTable);
            stmt.execute(crdtTable);
        }
    }

    public void saveDocument(String docId, String name, String editorCode, String viewerCode)
            throws SQLException {
        String sql = "INSERT OR REPLACE INTO documents (id, name, editor_code, viewer_code, created_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, docId);
            ps.setString(2, name);
            ps.setString(3, editorCode);
            ps.setString(4, viewerCode);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<String[]> loadAllDocuments() throws SQLException {
        String sql = "SELECT id, name, editor_code, viewer_code FROM documents";
        List<String[]> records = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                records.add(new String[]{
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("editor_code"),
                    rs.getString("viewer_code")
                });
            }
        }
        return records;
    }

    public void deleteDocument(String docId) throws SQLException {
        try (PreparedStatement ps1 = connection.prepareStatement("DELETE FROM documents WHERE id = ?");
             PreparedStatement ps2 = connection.prepareStatement("DELETE FROM crdt_state WHERE doc_id = ?")) {
            ps1.setString(1, docId);
            ps1.executeUpdate();
            ps2.setString(1, docId);
            ps2.executeUpdate();
        }
    }

    public void saveCRDTState(String docId, String crdtJson) throws SQLException {
        String sql = "INSERT OR REPLACE INTO crdt_state (doc_id, crdt_json, saved_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, docId);
            ps.setString(2, crdtJson);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public String loadCRDTState(String docId) throws SQLException {
        String sql = "SELECT crdt_json FROM crdt_state WHERE doc_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("crdt_json");
                }
            }
        }
        return null;
    }
}
