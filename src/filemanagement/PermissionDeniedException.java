package filemanagement;

/*
  Custom exception  to handle unauthorized access attempts.
 */
public class PermissionDeniedException extends Exception {
    public PermissionDeniedException(String message) {
        super(message);
    }
}