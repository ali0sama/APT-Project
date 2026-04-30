package filemanagement;

public class PermissionManager {
    public enum UserRole { EDITOR, VIEWER }

    public static boolean canEdit(UserRole role) {
        return role == UserRole.EDITOR; 
    }

    public static boolean canViewShareCodes(UserRole role) {
        return role == UserRole.EDITOR; 
    }

    /*
      throws the specific PermissionDeniedException
     */
    public static void enforce(UserRole role, String action) throws PermissionDeniedException {
        if (role == UserRole.VIEWER) {
            if (action.equals("EDIT") || action.equals("VIEW_CODES") || 
                action.equals("DELETE") || action.equals("RENAME")) {
                
                throw new PermissionDeniedException("Permission Denied: Viewers cannot " + action); 
            }
        }
    }
}