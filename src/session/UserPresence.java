package session;

/**
 * UserPresence represents a single user's presence in a collaboration session.
 * 
 * Stores:
 * - userID: Unique identifier for the user
 * - role: Either EDITOR or VIEWER
 * - joinTime: When the user joined the session
 */
public class UserPresence {
    private final int userID;
    private final CollaborationSession.UserRole role;
    private final long joinTime;

    public UserPresence(int userID, CollaborationSession.UserRole role) {
        this.userID = userID;
        this.role = role;
        this.joinTime = System.currentTimeMillis();
    }

    /**
     * Get the user's ID
     */
    public int getUserID() {
        return userID;
    }

    /**
     * Get the user's role
     */
    public CollaborationSession.UserRole getRole() {
        return role;
    }

    /**
     * Get when the user joined
     */
    public long getJoinTime() {
        return joinTime;
    }

    /**
     * Check if user is an editor
     */
    public boolean isEditor() {
        return role == CollaborationSession.UserRole.EDITOR;
    }

    /**
     * Check if user is a viewer
     */
    public boolean isViewer() {
        return role == CollaborationSession.UserRole.VIEWER;
    }

    @Override
    public String toString() {
        return String.format("UserPresence[id=%d, role=%s]", userID, role);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserPresence))
            return false;
        UserPresence other = (UserPresence) obj;
        return this.userID == other.userID;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(userID);
    }
}
