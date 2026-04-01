package crdt.block;

import crdt.character.CharacterCRDT;

/**
 * A Block is one logical paragraph-unit of the document.
 *
 * The document is an ordered sequence of Blocks managed by {@link BlockCRDT}.
 * Each Block wraps a {@link CharacterCRDT} that holds the characters inside it.
 *
 * Block size invariant (enforced during splits):
 *   Minimum: 2 lines    Maximum: 10 lines
 *
 * A tombstoned block is logically deleted but stays in memory so that the
 * split log can still resolve concurrent splits that reference it.
 */
public class Block {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final BlockID        blockID;
    private final CharacterCRDT  content;
    private boolean              tombstone;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /** Create a new empty block. */
    public Block(int counter, int userID) {
        this.blockID   = new BlockID(counter, userID);
        this.content   = new CharacterCRDT();
        this.tombstone = false;
    }

    /** Package-private: used internally by BlockCRDT when building split results. */
    Block(BlockID id) {
        this.blockID   = id;
        this.content   = new CharacterCRDT();
        this.tombstone = false;
    }

    // -----------------------------------------------------------------------
    // Tombstone
    // -----------------------------------------------------------------------

    public void tombstone()      { this.tombstone = true; }
    public boolean isTombstone() { return tombstone; }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public BlockID        getBlockID() { return blockID; }
    public CharacterCRDT  getContent() { return content; }

    /** Convenience: number of visible lines in this block's CharacterCRDT. */
    public int getLineCount() {
        return content.getLineCount();
    }

    // -----------------------------------------------------------------------
    // Debug
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return "Block{id=" + blockID
                + (tombstone ? " †" : "")
                + ", lines=" + getLineCount()
                + ", text=\"" + content.getDocument().replace("\n", "↵") + "\"}";
    }
}