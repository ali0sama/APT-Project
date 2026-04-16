package session;

import java.util.*;

/**
 * CollaborationSession manages a single collaborative editing session for one
 * document.
 * 
 * Responsibilities:
 * - Store active users with their roles (EDITOR or VIEWER)
 * - Store the operation history so late-joining users can catch up
 * - Track WebSocket connections for each user
 * - Provide methods to add/remove users and manage operations
 */
public class CollaborationSession {

    public enum UserRole {
        EDITOR, // Can read and edit
        VIEWER // Can only read, cannot edit
    }

    private final String sessionID; // Document ID / Session ID
    private final Map<Integer, UserPresence> activeUsers; // userID -> UserPresence
    private final List<String> operationHistory; // Stores JSON operations for history
    private final long createdTime;

    public CollaborationSession(String sessionID) {
        this.sessionID = sessionID;
        this.activeUsers = new HashMap<>();
        this.operationHistory = new ArrayList<>();
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * Add a user to this collaboration session
     */
    public void addUser(int userID, UserRole role) {
        activeUsers.put(userID, new UserPresence(userID, role));
    }

    /**
     * Remove a user from this collaboration session (on disconnect)
     */
    public void removeUser(int userID) {
        activeUsers.remove(userID);
    }

    /**
     * Check if a user has editor permission
     */
    public boolean isEditor(int userID) {
        UserPresence user = activeUsers.get(userID);
        return user != null && user.getRole() == UserRole.EDITOR;
    }

    /**
     * Check if a user is a viewer
     */
    public boolean isViewer(int userID) {
        UserPresence user = activeUsers.get(userID);
        return user != null && user.getRole() == UserRole.VIEWER;
    }

    /**
     * Check if a user exists in this session
     */
    public boolean hasUser(int userID) {
        return activeUsers.containsKey(userID);
    }

    /**
     * Get all active users in this session
     */
    public List<UserPresence> getActiveUsers() {
        return new ArrayList<>(activeUsers.values());
    }

    /**
     * Add an operation to the history (for late-joiners to catch up)
     */
    public void addOperation(String operationJson) {
        operationHistory.add(operationJson);
    }

    /**
     * Get all operations (for late-joining users)
     */
    public List<String> getOperationHistory() {
        return new ArrayList<>(operationHistory);
    }

    /**
     * Get the session ID
     */
    public String getSessionID() {
        return sessionID;
    }

    /**
     * Get number of active users
     */
    public int getUserCount() {
        return activeUsers.size();
    }

    /**
     * Check if session is empty (no users)
     */
    public boolean isEmpty() {
        return activeUsers.isEmpty();
    }

    /**
     * Get time when session was created
     */
    public long getCreatedTime() {
        return createdTime;
    }

    /**
     * Get user by ID
     */
    public UserPresence getUser(int userID) {
        return activeUsers.get(userID);
    }

    @Override
    public String toString() {
        return String.format("CollaborationSession[id=%s, users=%d, ops=%d]",
                sessionID, activeUsers.size(), operationHistory.size());
    }
}
