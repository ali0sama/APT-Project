package tests;

import crdt.character.CharId;
import crdt.character.CharacterCRDT;
import crdt.utils.Clock;

public class CharCRDTTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		run();
		printResults();
	}

	public static void run() {
		passed = 0;
		failed = 0;
		test1_UniqueIDs();
		test2_IDEquality();
		test3_BasicInsert();
		test4_BasicDelete();
		test5_TombstoneInvisible();
		test6_TombstoneChildrenSurvive();
		test7_DeleteIsIdempotent();
		test8_CharIdDeterministicOrdering();
	}

	public void runAll() {
		run();
	}

	private static void check(String testName, String expected, String actual) {
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
		System.out.println("Results: " + passed + " passed, " + failed + " failed");
	}

	static void test1_UniqueIDs() {
		Clock clock = new Clock();
		CharId id1 = new CharId(clock.tick(), 1);
		CharId id2 = new CharId(clock.tick(), 1);
		CharId id3 = new CharId(clock.tick(), 2);

		checkTrue("Test 1 - IDs are unique", !id1.equals(id2) && !id1.equals(id3) && !id2.equals(id3));
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

		check("Test 4 - Delete B -> AC", "AC", crdt.getDocument());
	}

	static void test5_TombstoneInvisible() {
		CharacterCRDT crdt = new CharacterCRDT();

		CharId idA = new CharId(1, 1);
		CharId idB = new CharId(2, 1);

		crdt.insert(idA, 'A', null);
		crdt.insert(idB, 'B', idA);

		crdt.delete(idA);

		checkTrue("Test 5 - Deleted char invisible", !crdt.getDocument().contains("A"));
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

		checkTrue("Test 6 - Children survive", crdt.getDocument().contains("A") && crdt.getDocument().contains("C") && !crdt.getDocument().contains("B"));
	}

	static void test7_DeleteIsIdempotent() {
		CharacterCRDT crdt = new CharacterCRDT();

		CharId idA = new CharId(1, 1);
		crdt.insert(idA, 'A', null);

		crdt.delete(idA);
		crdt.delete(idA);

		checkTrue("Test 7 - Delete idempotent", !crdt.getDocument().contains("A"));
	}

	static void test8_CharIdDeterministicOrdering() {
		CharacterCRDT crdt = new CharacterCRDT();

		CharId root = new CharId(1, 1);
		crdt.insert(root, 'A', null);

		CharId id1 = new CharId(2, 2);
		CharId id2 = new CharId(2, 1);

		crdt.insert(id1, 'Y', root);
		crdt.insert(id2, 'X', root);

		check("Test 8 - Same counter -> lower userID first", "AXY", crdt.getDocument());
	}
}
