package cursor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CursorTracker {

    private final Map<Integer, Integer> positions = new HashMap<>(); // userID → char offset

    /** Store or update the cursor position for a user. */
    public synchronized void update(int userID, int position) {
        positions.put(userID, position);
    }

    /** Returns the tracked position for userID, or -1 if not tracked. */
    public synchronized int getPosition(int userID) {
        return positions.getOrDefault(userID, -1);
    }

    /** Returns an unmodifiable snapshot of all tracked positions. */
    public synchronized Map<Integer, Integer> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(positions));
    }

    /** Returns true if userID has a tracked position. */
    public synchronized boolean hasUser(int userID) {
        return positions.containsKey(userID);
    }

    /** Remove a user's cursor — call on disconnect. */
    public synchronized void remove(int userID) {
        positions.remove(userID);
    }

    /** Remove all tracked positions. */
    public synchronized void clear() {
        positions.clear();
    }

    /** Number of users currently tracked. */
    public synchronized int size() {
        return positions.size();
    }

    @Override
    public synchronized String toString() {
        return "CursorTracker" + positions;
    }
}
