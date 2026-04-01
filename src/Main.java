import crdt.block.Block;
import crdt.block.BlockCRDT;
import crdt.block.BlockID;
import crdt.character.CharId;
import crdt.character.CharacterCRDT;
import utils.Clock;

public class Main {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {

        // Member 1 tests
        test1_UniqueIDs();
        test2_IDEquality();
        test3_BasicInsert();
        test4_BasicDelete();
        test5_TombstoneInvisible();
        test6_TombstoneChildrenSurvive();
        test7_DeleteIsIdempotent();

        // Member 3 tests
        test8_CharIdDeterministicOrdering();
        test9_BlockInsertionDeterministicOrdering();
        test10_NormalBlockSplit();
        test11_ConcurrentSplitTieBreaker();

        System.out.println("\n==============================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        System.out.println("==============================");
    }

    static void check(String testName, String expected, String actual) {
        if (expected.equals(actual)) {
            System.out.println("[PASS] " + testName);
            passed++;
        } else {
            System.out.println("[FAIL] " + testName);
            System.out.println("       Expected : \"" + expected + "\"");
            System.out.println("       Got      : \"" + actual + "\"");
            failed++;
        }
    }

    static void checkTrue(String testName, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + testName);
            passed++;
        } else {
            System.out.println("[FAIL] " + testName);
            failed++;
        }
    }

    // ── Member 1 Tests ─────────────────────────────────────────────

    static void test1_UniqueIDs() {
        Clock clock = new Clock();
        CharId id1 = new CharId(clock.tick(), 1);
        CharId id2 = new CharId(clock.tick(), 1);
        CharId id3 = new CharId(clock.tick(), 2);

        checkTrue("Test 1 - IDs are unique",
                !id1.equals(id2) && !id1.equals(id3) && !id2.equals(id3));
    }

    static void test2_IDEquality() {
        CharId id1 = new CharId(5, 2);
        CharId id2 = new CharId(5, 2);

        checkTrue("Test 2 - Same IDs are equal", id1.equals(id2));
    }

    static void test3_BasicInsert() {
        CharacterCRDT crdt = new CharacterCRDT();
        Clock clock = new Clock();

        CharId idA = new CharId(clock.tick(), 1);
        CharId idB = new CharId(clock.tick(), 1);
        CharId idC = new CharId(clock.tick(), 1);

        crdt.insert(idA, 'A', null);
        crdt.insert(idB, 'B', idA);
        crdt.insert(idC, 'C', idB);

        check("Test 3 - Insert ABC", "ABC", crdt.getDocument());
    }

    static void test4_BasicDelete() {
        CharacterCRDT crdt = new CharacterCRDT();
        Clock clock = new Clock();

        CharId idA = new CharId(clock.tick(), 1);
        CharId idB = new CharId(clock.tick(), 1);
        CharId idC = new CharId(clock.tick(), 1);

        crdt.insert(idA, 'A', null);
        crdt.insert(idB, 'B', idA);
        crdt.insert(idC, 'C', idB);

        crdt.delete(idB);

        check("Test 4 - Delete B → AC", "AC", crdt.getDocument());
    }

    static void test5_TombstoneInvisible() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId idA = new CharId(1, 1);
        CharId idB = new CharId(2, 1);

        crdt.insert(idA, 'A', null);
        crdt.insert(idB, 'B', idA);

        crdt.delete(idA);

        checkTrue("Test 5 - Deleted char invisible",
                !crdt.getDocument().contains("A"));
    }

    static void test6_TombstoneChildrenSurvive() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId idA = new CharId(1, 1);
        CharId idB = new CharId(2, 1);
        CharId idC = new CharId(3, 1);

        crdt.insert(idA, 'A', null);
        crdt.insert(idB, 'B', idA);
        crdt.insert(idC, 'C', idB);

        crdt.delete(idB);

        checkTrue("Test 6 - Children survive",
                crdt.getDocument().contains("A") &&
                crdt.getDocument().contains("C") &&
                !crdt.getDocument().contains("B"));
    }

    static void test7_DeleteIsIdempotent() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId idA = new CharId(1, 1);
        crdt.insert(idA, 'A', null);

        crdt.delete(idA);
        crdt.delete(idA);

        checkTrue("Test 7 - Delete idempotent",
                !crdt.getDocument().contains("A"));
    }

    // ── Member 3 Tests ─────────────────────────────────────────────

    static void test8_CharIdDeterministicOrdering() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId root = new CharId(1, 1);
        crdt.insert(root, 'A', null);

        CharId id1 = new CharId(2, 2);
        CharId id2 = new CharId(2, 1);

        crdt.insert(id1, 'Y', root);
        crdt.insert(id2, 'X', root);

        check("Test 8 - Same counter → lower userID first", "AXY", crdt.getDocument());
    }

    static void test9_BlockInsertionDeterministicOrdering() {
        BlockCRDT crdt = new BlockCRDT();

        Block b = new Block(5, 2);
        Block a = new Block(5, 1);

        a.getContent().insert(new CharId(1, 1), 'A', null);
        b.getContent().insert(new CharId(1, 2), 'B', null);

        crdt.insertBlock(b);
        crdt.insertBlock(a);

        check("Test 9 - Block ordering", "AB", crdt.getDocumentText());
    }

    static void test10_NormalBlockSplit() {
        BlockCRDT crdt = new BlockCRDT();
        Block block = new Block(1, 1);

        CharId a = new CharId(1, 1);
        CharId b = new CharId(2, 1);
        CharId c = new CharId(3, 1);
        CharId d = new CharId(4, 1);

        block.getContent().insert(a, 'A', null);
        block.getContent().insert(b, 'B', a);
        block.getContent().insert(c, 'C', b);
        block.getContent().insert(d, 'D', c);

        crdt.insertBlock(block);

        crdt.splitBlock(block.getBlockID(), b, new BlockID(2, 1));

        boolean ok = crdt.getVisibleBlocks().size() == 2 &&
                crdt.getVisibleBlocks().get(0).getContent().getDocument().equals("AB") &&
                crdt.getVisibleBlocks().get(1).getContent().getDocument().equals("CD");

        checkTrue("Test 10 - Normal split", ok);
    }

    static void test11_ConcurrentSplitTieBreaker() {
        BlockCRDT crdt = new BlockCRDT();
        Block block = new Block(1, 1);

        CharId a = new CharId(1, 1);
        CharId b = new CharId(2, 1);
        CharId c = new CharId(3, 1);
        CharId d = new CharId(4, 1);

        block.getContent().insert(a, 'A', null);
        block.getContent().insert(b, 'B', a);
        block.getContent().insert(c, 'C', b);
        block.getContent().insert(d, 'D', c);

        crdt.insertBlock(block);

        crdt.splitBlock(block.getBlockID(), b, new BlockID(5, 2));
        crdt.splitBlock(block.getBlockID(), b, new BlockID(5, 1));

        boolean ok = crdt.getVisibleBlocks().size() == 3;

        checkTrue("Test 11 - Concurrent split tie-break", ok);
    }
}