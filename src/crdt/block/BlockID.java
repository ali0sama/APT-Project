package crdt.block;

import java.util.Objects;

/**
 * Unique identifier for a Block.
 *
 * Mirrors CharId but for blocks:
 *   counter : Lamport clock value at the time the block was created
 *   userID  : integer ID of the user who created the block
 *
 * ─── Tie-Breaker / Deterministic Ordering ───────────────────────────────────
 *
 * Rule 1: lower counter  →  block appears EARLIER in the document
 * Rule 2: equal counters →  smaller userID comes first  (lexicographic on int)
 *
 * This gives a TOTAL ORDER on BlockIDs: every pair has a defined result,
 * and every peer computes the same result for the same pair.
 * This is the foundation of all conflict resolution in BlockCRDT.
 */
public class BlockID implements Comparable<BlockID> {

    public final int counter;
    public final int userID;

    public BlockID(int counter, int userID) {
        this.counter = counter;
        this.userID  = userID;
    }

    @Override
    public int compareTo(BlockID other) {
        if (this.counter != other.counter) {
            return Integer.compare(this.counter, other.counter);   // Rule 1
        }
        return Integer.compare(this.userID, other.userID);          // Rule 2
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockID)) return false;
        BlockID other = (BlockID) obj;
        return this.counter == other.counter && this.userID == other.userID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(counter, userID);
    }

    @Override
    public String toString() {
        return "[user=" + userID + ", clock=" + counter + "]";
    }
}