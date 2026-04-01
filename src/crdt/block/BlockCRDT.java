package crdt.block;

import crdt.character.CharId;
import crdt.character.CRDTChar;

import java.util.*;

/**
 * Block-Level CRDT
 * ================
 * Manages the ordered sequence of {@link Block} objects that form the document.
 *
 * ── Member 3 responsibilities ───────────────────────────────────────────────
 *
 *  (A) DETERMINISTIC BLOCK ORDERING
 *      Blocks are always sorted by {@link BlockID} using the tie-breaker in
 *      BlockID.compareTo():
 *        Rule 1. Lower counter  → block appears earlier in the document.
 *        Rule 2. Equal counters → smaller userID comes first.
 *      Because this is a total order, two peers receiving the same set of
 *      insertBlock() calls always end up with the same block sequence.
 *
 *  (B) CONCURRENT BLOCK SPLIT RESOLUTION
 *
 *      Problem: User A and User B both press Enter inside Block X at the
 *      same time. Each generates a splitBlock() call. The split that arrives
 *      second will find the original block already replaced — a naïve
 *      implementation would either crash or drop the second split entirely.
 *
 *      Solution (three cases):
 *
 *        Case 1 – Normal split
 *          The block still exists under its original ID and the anchor char
 *          is inside it. Partition at the anchor; right chars move to newBlock.
 *
 *        Case 2 – Late split (block already split, different anchor)
 *          The original block was already split. We scan all live blocks for
 *          the one that currently holds the anchor char and split that one.
 *          The split log records which original block each newBlock descended
 *          from, so this search is always unambiguous.
 *
 *        Case 3 – Concurrent split (same anchor char, different newBlockID)
 *          Two users split at the exact same character. Detected by finding
 *          a SplitRecord with the same (targetBlockID, anchorCharID) but a
 *          different newBlockID.
 *
 *          Resolution (deterministic tie-breaker):
 *            Winner  = the split whose newBlockID is SMALLER (compareTo < 0)
 *            Result  = three blocks:
 *              [LEFT:  chars up to & including anchor  — original block, trimmed]
 *              [MID:   winner's new block, empty       — the "claimed" split point]
 *              [RIGHT: loser's new block, right chars  — content after the anchor]
 *
 *          Every peer computes the same winner because BlockID.compareTo() is
 *          a deterministic total order — no randomness, no timestamps.
 *
 *  (C) BLOCK MERGE
 *      Merges two adjacent blocks. Right block is tombstoned after its
 *      characters (including tombstones) are appended to the left block.
 */
public class BlockCRDT {

    // =========================================================================
    // Inner class: SplitRecord
    // =========================================================================

    /**
     * Immutable record of one split operation.
     * Stored in splitLog so that:
     *   • Late-arriving splits can find which sub-block to target.
     *   • Concurrent splits on the same anchor are detected and resolved.
     */
    public static class SplitRecord {
        /** The block that was split (its ID at the time of the split). */
        public final BlockID targetBlockID;
        /** The last character of the left half (the split "anchor"). */
        public final CharId  anchorCharID;
        /** The BlockID assigned to the new right block. */
        public final BlockID newBlockID;

        public SplitRecord(BlockID targetBlockID, CharId anchorCharID, BlockID newBlockID) {
            this.targetBlockID = targetBlockID;
            this.anchorCharID  = anchorCharID;
            this.newBlockID    = newBlockID;
        }

        @Override
        public String toString() {
            return "SplitRecord{target=" + targetBlockID
                    + ", anchor=" + anchorCharID
                    + ", newBlock=" + newBlockID + "}";
        }
    }

    // =========================================================================
    // State
    // =========================================================================

    /** All blocks (including tombstoned), kept sorted by BlockID at all times. */
    private final List<Block>       blocks   = new ArrayList<>();

    /** Full history of every split applied to this document. */
    private final List<SplitRecord> splitLog = new ArrayList<>();

    // =========================================================================
    // Block Insert
    // =========================================================================

    /**
     * Insert a block into the document at the position determined by its BlockID.
     *
     * This is idempotent: a second call with the same BlockID is silently ignored.
     *
     * @return true if the block was newly inserted, false if it was a duplicate.
     */
    public boolean insertBlock(Block block) {
        // Duplicate check (idempotent)
        for (Block b : blocks) {
            if (b.getBlockID().equals(block.getBlockID())) return false;
        }
        int idx = findInsertionIndex(block.getBlockID());
        blocks.add(idx, block);
        return true;
    }

    /**
     * Binary search: find the index where a block with the given ID should
     * be inserted to keep {@code blocks} sorted by BlockID.
     */
    private int findInsertionIndex(BlockID id) {
        int lo = 0, hi = blocks.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (blocks.get(mid).getBlockID().compareTo(id) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    // =========================================================================
    // Block Delete
    // =========================================================================

    /**
     * Soft-delete (tombstone) a block.
     *
     * The block stays in the list so that the split log remains resolvable:
     * a later split targeting a child of this block still needs to find the
     * correct sub-block by searching character ownership.
     *
     * @return true if found and tombstoned, false if not found or already tombstoned.
     */
    public boolean deleteBlock(BlockID blockID) {
        Block b = getBlockByID(blockID);
        if (b == null || b.isTombstone()) return false;
        b.tombstone();
        return true;
    }

    // =========================================================================
    // Block Split  ← core Member 3 algorithm
    // =========================================================================

    /**
     * Split a block at {@code anchorCharID}.
     *
     * After the split:
     *   - The original block retains all characters up to and INCLUDING the anchor.
     *   - A new block (with ID {@code newBlockID}) receives all characters AFTER the anchor.
     *
     * Handles all three cases described in the class Javadoc.
     *
     * @param originalBlockID  the ID of the block to split (used to locate it, even
     *                         if it has already been split by an earlier operation)
     * @param anchorCharID     the charID of the last character in the left half
     * @param newBlockID       the BlockID to assign to the right half
     * @return true if the split was successfully applied; false if the anchor
     *         character could not be found in any live block.
     */
    public boolean splitBlock(BlockID originalBlockID, CharId anchorCharID, BlockID newBlockID) {

        // Idempotency: if we already have a split record with this exact newBlockID, skip.
        for (SplitRecord sr : splitLog) {
            if (sr.newBlockID.equals(newBlockID)) return false;
        }

        // ── Step 1: find the live block that currently holds the anchor char ──
        //    (may differ from originalBlockID if the block was already split)
        Block target = findBlockContaining(anchorCharID);
        if (target == null) return false;

        // ── Step 2: check for a concurrent split on the same anchor ──────────
        SplitRecord conflict = findConflictingSplit(target.getBlockID(), anchorCharID, newBlockID);

        if (conflict != null) {
            // ── Case 3: concurrent split — resolve with tie-breaker ───────────
            resolveConcurrentSplit(target, anchorCharID, newBlockID, conflict);
        } else {
            // ── Case 1 or 2: normal or late split ────────────────────────────
            performSplit(target, anchorCharID, newBlockID);
        }

        return true;
    }

    // ── Case 1 / Case 2: standard split ──────────────────────────────────────

    /**
     * Partition {@code target} at {@code anchorCharID}.
     *
     * Before:  [A  B  C  D  E]   anchor = C
     * After:   [A  B  C]   [D  E]   (newBlock gets D and E)
     */
    private void performSplit(Block target, CharId anchorCharID, BlockID newBlockID) {
        List<CRDTChar> all = target.getContent().getAllChars();

        int anchorIdx = indexOfChar(all, anchorCharID);
        if (anchorIdx == -1) return;

        // Chars that stay in the left block (up to and including anchor)
        List<CRDTChar> leftChars  = new ArrayList<>(all.subList(0, anchorIdx + 1));
        // Chars that move to the new right block
        List<CRDTChar> rightChars = new ArrayList<>(all.subList(anchorIdx + 1, all.size()));

        // Rebuild left block's content
       target.getContent().bulkLoadLinear(leftChars);

Block newBlock = new Block(newBlockID);
newBlock.getContent().bulkLoadLinear(rightChars);
        insertBlock(newBlock);

        // Record the split for future conflict detection
        splitLog.add(new SplitRecord(target.getBlockID(), anchorCharID, newBlockID));
    }

    // ── Case 3: concurrent split on the same anchor ───────────────────────────

    /**
     * Two users split the SAME block at the EXACT SAME character simultaneously.
     *
     * Tie-breaker: the split whose {@code newBlockID} is smaller (compareTo < 0)
     * wins the "middle" slot; the other gets the right-side content.
     *
     * Result (three blocks in document order):
     *
     *   [LEFT]         – original block, trimmed to chars before & including anchor
     *   [WINNER-MID]   – winner's new block, intentionally empty
     *   [LOSER-RIGHT]  – loser's new block, holds chars after anchor
     *
     * Why the winner gets an empty middle block:
     *   Both users pressed Enter at the same spot — they each intended to start
     *   a new paragraph at that point. The winner "claims" the empty new paragraph;
     *   the loser's content follows. The result is stable and identical on all peers.
     */
    private void resolveConcurrentSplit(Block target, CharId anchorCharID,
                                         BlockID incomingNewBlockID, SplitRecord existing) {

        // Tie-breaker: smaller BlockID wins (deterministic total order)
        boolean incomingWins = incomingNewBlockID.compareTo(existing.newBlockID) < 0;

        BlockID winnerID = incomingWins ? incomingNewBlockID : existing.newBlockID;
        BlockID loserID  = incomingWins ? existing.newBlockID : incomingNewBlockID;

        List<CRDTChar> all = target.getContent().getAllChars();
        int anchorIdx = indexOfChar(all, anchorCharID);
        if (anchorIdx == -1) return;

        List<CRDTChar> leftChars  = new ArrayList<>(all.subList(0, anchorIdx + 1));
        List<CRDTChar> rightChars = new ArrayList<>(all.subList(anchorIdx + 1, all.size()));

        // Rewrite the original (left) block to hold only left chars
       target.getContent().bulkLoadLinear(leftChars);

        // Winner's block: empty (claims the split point, no content)
        if (getBlockByID(winnerID) == null) {
            insertBlock(new Block(winnerID));
        }

        // Loser's block: receives the right-side characters
        Block loserBlock = getBlockByID(loserID);
        if (loserBlock == null) {
            loserBlock = new Block(loserID);
            insertBlock(loserBlock);
        }
        // bulkLoad is idempotent, so calling it again on an existing block is safe
        loserBlock.getContent().bulkLoadLinear(rightChars);

        // Record the incoming split for future conflict detection
        splitLog.add(new SplitRecord(target.getBlockID(), anchorCharID, incomingNewBlockID));
    }

    // =========================================================================
    // Block Merge
    // =========================================================================

    /**
     * Merge {@code blockB} into {@code blockA}  (A must precede B in document order).
     *
     * All of B's characters, including tombstones, are appended to A so that the
     * CRDT tree structure and relative ordering are preserved. B is then tombstoned.
     *
     * @return true if merged, false if either block was not found or already tombstoned.
     */
    public boolean mergeBlocks(BlockID blockAID, BlockID blockBID) {
        Block a = getBlockByID(blockAID);
        Block b = getBlockByID(blockBID);
        if (a == null || b == null || a.isTombstone() || b.isTombstone()) return false;

        // Move all of B's chars (including tombstones) into A
        a.getContent().bulkLoad(b.getContent().getAllChars());
        b.tombstone();
        return true;
    }

    // =========================================================================
    // Document reconstruction
    // =========================================================================

    /**
     * Reconstruct the full visible document text by concatenating all
     * non-tombstoned blocks in their sorted order.
     */
    public String getDocumentText() {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (!b.isTombstone()) sb.append(b.getContent().getDocument());
        }
        return sb.toString();
    }

    /** Returns only the live (non-tombstoned) blocks in document order. */
    public List<Block> getVisibleBlocks() {
        List<Block> result = new ArrayList<>();
        for (Block b : blocks) {
            if (!b.isTombstone()) result.add(b);
        }
        return Collections.unmodifiableList(result);
    }

    public List<SplitRecord> getSplitLog() {
        return Collections.unmodifiableList(splitLog);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Scan all live blocks and return the one whose CharacterCRDT contains
     * a character with the given ID.
     *
     * This handles Case 2 (late split) automatically: even if the original
     * block was already split, we find whichever sub-block now owns the anchor.
     */
    private Block findBlockContaining(CharId charID) {
        for (Block b : blocks) {
            if (!b.isTombstone() && b.getContent().contains(charID)) return b;
        }
        return null;
    }

    /**
     * Look for a SplitRecord that targets the same block and the same anchor
     * character as the incoming split, but has a DIFFERENT newBlockID.
     *
     * A different newBlockID means two distinct peers independently generated
     * a split at the same point → Case 3 (concurrent split conflict).
     */
    private SplitRecord findConflictingSplit(BlockID blockID, CharId anchorCharID,
                                              BlockID incomingNewBlockID) {
        for (SplitRecord sr : splitLog) {
            if (sr.targetBlockID.equals(blockID)
                    && sr.anchorCharID.equals(anchorCharID)
                    && !sr.newBlockID.equals(incomingNewBlockID)) {
                return sr;
            }
        }
        return null;
    }

    private Block getBlockByID(BlockID id) {
        for (Block b : blocks) {
            if (b.getBlockID().equals(id)) return b;
        }
        return null;
    }

    private int indexOfChar(List<CRDTChar> list, CharId targetID) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(targetID)) return i;
        }
        return -1;
    }

    // =========================================================================
    // Debug
    // =========================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BlockCRDT[\n");
        for (Block b : blocks) sb.append("  ").append(b).append("\n");
        return sb.append("]").toString();
    }
}