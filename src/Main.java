import crdt.character.CharId;
import crdt.character.CharacterCRDT;
import utils.Clock;

public class Main {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {

        // Member 1 Responsibility 1: Unique ID System
        test1_UniqueIDs();
        test2_IDEquality();

        // Member 1 Responsibility 2: Insert and Delete
        test3_BasicInsert();
        test4_BasicDelete();

        // Member 1 Responsibility 3: Tombstones
        test5_TombstoneInvisible();
        test6_TombstoneChildrenSurvive();
        test7_DeleteIsIdempotent();

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

    // ── Unique ID System ─────────────────────────────────────────────────

    static void test1_UniqueIDs() {
        Clock clock = new Clock();
        CharId id1 = new CharId(clock.tick(), 1);
        CharId id2 = new CharId(clock.tick(), 1);
        CharId id3 = new CharId(clock.tick(), 2);
        checkTrue("Test 1 - IDs are unique (different counters or users)",
                !id1.equals(id2) && !id1.equals(id3) && !id2.equals(id3));
    }

    static void test2_IDEquality() {
        CharId id1 = new CharId(5, 2);
        CharId id2 = new CharId(5, 2);
        checkTrue("Test 2 - Same counter and userID means equal IDs", id1.equals(id2));
    }

    // ── Insert and Delete ─────────────────────────────────────────────────

    static void test3_BasicInsert() {
        CharacterCRDT crdt = new CharacterCRDT();
        Clock clock = new Clock();

        CharId idA = new CharId(clock.tick(), 1);
        CharId idB = new CharId(clock.tick(), 1);
        CharId idC = new CharId(clock.tick(), 1);

        crdt.insert(idA, 'A', null);
        crdt.insert(idB, 'B', idA);
        crdt.insert(idC, 'C', idB);

        check("Test 3 - Insert three characters (ABC)", "ABC", crdt.getDocument());
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

        check("Test 4 - Delete middle character (AC)", "AC", crdt.getDocument());
    }

    // ── Tombstones ────────────────────────────────────────────────────────

    static void test5_TombstoneInvisible() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId idA = new CharId(1, 1);
        CharId idB = new CharId(2, 1);

        crdt.insert(idA, 'A', null);
        crdt.insert(idB, 'B', idA);
        crdt.delete(idA);

        checkTrue("Test 5 - Deleted character is invisible in document",
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

        CharId idD = new CharId(4, 1);
        crdt.insert(idD, 'D', idB);

        checkTrue("Test 6 - Children of tombstone still appear (A, C, D visible, B not)",
                crdt.getDocument().contains("A") &&
                        crdt.getDocument().contains("C") &&
                        crdt.getDocument().contains("D") &&
                        !crdt.getDocument().contains("B"));
    }

    static void test7_DeleteIsIdempotent() {
        CharacterCRDT crdt = new CharacterCRDT();

        CharId idA = new CharId(1, 1);
        crdt.insert(idA, 'A', null);
        crdt.delete(idA);
        crdt.delete(idA);

        checkTrue("Test 7 - Deleting same character twice has no extra effect",
                !crdt.getDocument().contains("A"));
    }
}