package tests;

import crdt.block.Block;
import crdt.block.BlockCRDT;
import crdt.block.BlockID;
import crdt.character.CharId;
import crdt.character.CharacterCRDT;

/**

 Pitfall #1 — splitBlock()  (same split applied twice)
 Pitfall #2 — Insert into a deleted block (concurrent delete + insert on block)
 Pitfall #3 — moveBlock() vs getVisibleBlocks() ordering conflict
 Pitfall #4 — Clock package mismatch / delete() throws a bool to make sure it is handled gracefully when delete arrives before insert
 Pitfall #6 — delete() throws NoSuchElementException when delete arrives before insert
 */
public class SpecialCaseTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        run();
        printResults();
    }

    public static void run() {
        passed = 0;
        failed = 0;
        test_EC1_SplitIdempotent();
        test_EC2_InsertIntoDeletedBlock();
        test_EC3_MoveBlockRespectedInOrdering();
        test_EC4_DeleteBeforeInsertNoException();
        test_EC4b_DeleteBeforeInsertCharSurvives();
    }

    public void runAll() {
        run();
    }

    // -------------------------------------------------------------------------
    // Pitfall #1 — splitBlock()
    // If the same split operation is delivered twice the document must not be split a second time.
    // -------------------------------------------------------------------------
    static void test_EC1_SplitIdempotent() {
        BlockCRDT crdt = new BlockCRDT();
        Block block = new Block(new BlockID(1, 1));

        CharId a = new CharId(1, 1);
        CharId b = new CharId(2, 1);
        CharId c = new CharId(3, 1);
        CharId d = new CharId(4, 1);

        block.getContent().insert(a, 'A', null);
        block.getContent().insert(b, 'B', a);
        block.getContent().insert(c, 'C', b);
        block.getContent().insert(d, 'D', c);

        crdt.insertBlock(block);

        // Apply the same split twice (simulates duplicate delivery)
        crdt.splitBlock(new BlockID(1, 1), b, new BlockID(2, 1));
        crdt.splitBlock(new BlockID(1, 1), b, new BlockID(2, 1)); // duplicate

        checkTrue(
            "EC1 - Duplicate splitBlock is idempotent (still 2 blocks)",
            crdt.getVisibleBlocks().size() == 2
        );
    }

    // -------------------------------------------------------------------------
    // Pitfall #2 — Insert into a deleted block
    // User A deletes a block. Concurrently, User B inserts a character into that same block.
    // -------------------------------------------------------------------------
    static void test_EC2_InsertIntoDeletedBlock() {
        BlockCRDT crdt = new BlockCRDT();
        Block block = new Block(new BlockID(1, 1));

        CharId a = new CharId(1, 1);
        block.getContent().insert(a, 'A', null);
        crdt.insertBlock(block);

        // User A deletes the block
        crdt.deleteBlock(new BlockID(1, 1));

        // User B concurrently inserts into the now-deleted block
        boolean exceptionThrown = false;
        try {
            Block deletedBlock = null;
            for (Block b : crdt.getVisibleBlocks()) {
                if (b.getBlockId().equals(new BlockID(1, 1))) {
                    deletedBlock = b;
                    break;
                }
            }
            block.getContent().insert(new CharId(2, 2), 'B', a);
        } catch (Exception e) {
            exceptionThrown = true;
        }

        checkTrue(
            "EC2a - Deleted block not visible after concurrent insert",
            crdt.getVisibleBlocks().isEmpty()
        );
        checkTrue(
            "EC2b - Insert into deleted block does not throw",
            !exceptionThrown
        );
    }

    // -------------------------------------------------------------------------
    // Pitfall #3 — moveBlock() result must be reflected in getVisibleBlocks()
    // Currently getVisibleBlocks() re-sorts by BlockID, so moveBlock() has no
    // effect. This test exposes that bug: after moving block B before block A,
    // the document text must start with B's content.
    // -------------------------------------------------------------------------
    static void test_EC3_MoveBlockRespectedInOrdering() {
        BlockCRDT crdt = new BlockCRDT();

        Block blockA = new Block(new BlockID(1, 1));
        blockA.getContent().insert(new CharId(1, 1), 'A', null);
        blockA.getContent().insert(new CharId(2, 1), 'A', new CharId(1, 1));
        blockA.getContent().insert(new CharId(3, 1), 'A', new CharId(2, 1));

        Block blockB = new Block(new BlockID(2, 1));
        blockB.getContent().insert(new CharId(4, 2), 'B', null);
        blockB.getContent().insert(new CharId(5, 2), 'B', new CharId(4, 2));
        blockB.getContent().insert(new CharId(6, 2), 'B', new CharId(5, 2));

        crdt.insertBlock(blockA);
        crdt.insertBlock(blockB);

        // Before move: should be AAABBB
        String before = crdt.getDocumentText();

        // Move block B to index 0 (before block A)
        crdt.moveBlock(new BlockID(2, 1), 0);

        String after = crdt.getDocumentText();

        checkTrue(
            "EC3a - Before move, order is AAABBB",
            before.equals("AAABBB")
        );
        checkTrue(
            "EC3b - After moveBlock, order reflects the move (BBBAAA)",
            after.equals("BBBAAA")
        );
    }

    // -------------------------------------------------------------------------
    // Pitfall #4 / #6 — Out-of-order delivery: delete arrives before insert
    // In a real network, a DELETE for CharId X may arrive before the INSERT
    // for CharId X. Currently delete() throws NoSuchElementException in this
    // case. The system must handle it gracefully (no exception).
    // -------------------------------------------------------------------------
    static void test_EC4_DeleteBeforeInsertNoException() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId a = new CharId(1, 1);
        CharId b = new CharId(2, 1); // will be deleted before inserted

        crdt.insert(a, 'A', null);

        // DELETE for 'B' arrives before INSERT for 'B'
        boolean exceptionThrown = false;
        try {
            crdt.delete(b); // b doesn't exist yet — should NOT throw
        } catch (Exception e) {
            exceptionThrown = true;
        }

        checkTrue(
            "EC4 - Delete before insert does not throw",
            !exceptionThrown
        );
    }

    // -------------------------------------------------------------------------
    // Pitfall #6 continued — After the deferred delete, when the insert finally
    // arrives, the character must appear as deleted (tombstoned), not visible.
    // -------------------------------------------------------------------------
    static void test_EC4b_DeleteBeforeInsertCharSurvives() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId a = new CharId(1, 1);
        CharId b = new CharId(2, 1);

        crdt.insert(a, 'A', null);

        // Delete arrives first
        try { crdt.delete(b); } catch (Exception ignored) {}

        // Insert arrives late
        crdt.insert(b, 'B', a);

        // 'B' should be tombstoned, not visible
        checkTrue(
            "EC4b - Late insert after early delete results in tombstoned char",
            !crdt.getDocument().contains("B") && crdt.getDocument().equals("A")
        );
    }


    private static void checkTrue(String testName, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + testName);
            passed++;
        } else {
            System.out.println("[FAIL] " + testName);
            failed++;
        }
    }

    private static void printResults() {
        System.out.println("\nEdge Case Results: " + passed + " passed, " + failed + " failed");
    }
}