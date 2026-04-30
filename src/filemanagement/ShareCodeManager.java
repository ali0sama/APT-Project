package filemanagement;

import database.FileRepository;
import java.sql.SQLException;
import java.util.Random;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import filemanagement.PermissionManager.UserRole;

public class ShareCodeManager {

    public static String generateEditorCode() {
        return generateRandomAlphanumeric(); // XK3P9MWQ style [cite: 84]
    }

    public static String generateViewerCode() {
        return generateRandomAlphanumeric(); 
    }

    private static String generateRandomAlphanumeric() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        while (sb.length() < 8) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // Task 2: Look up the code in the database and return docId and role [cite: 87]
    public static Map<String, Object> joinByCode(FileRepository repo, String code) throws SQLException {
        List<String[]> allDocs = repo.getAllFileRecords();
        for (String[] doc : allDocs) {
            if (doc[2].equals(code)) { // Editor code match
                Map<String, Object> result = new HashMap<>();
                result.put("docId", doc[0]);
                result.put("role", UserRole.EDITOR);
                return result;
            }
            if (doc[3].equals(code)) { // Viewer code match
                Map<String, Object> result = new HashMap<>();
                result.put("docId", doc[0]);
                result.put("role", UserRole.VIEWER);
                return result;
            }
        }
        return null;
    }
}