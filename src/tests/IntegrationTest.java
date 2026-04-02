package tests;

import crdt.character.CharId;
import crdt.character.CharacterCRDT;
import operations.DeleteOperation;
import operations.InsertOperation;

public class IntegrationTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		run();
		printResults();
	}

	public static void run() {
		passed = 0;
		failed = 0;
		test12_OperationApply();
		test13_ConcurrentInsertSamePosition();
		test14_DeleteWhileInsert();
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

	private static void printResults() {
		System.out.println("Results: " + passed + " passed, " + failed + " failed");
	}

	static void test12_OperationApply() {
		CharacterCRDT crdt = new CharacterCRDT();

		InsertOperation op1 = new InsertOperation(1, 1, 'H', null);
		InsertOperation op2 = new InsertOperation(1, 2, 'i', op1.charID);

		op1.apply(crdt);
		op2.apply(crdt);

		check("Test 12 - Operation apply", "Hi", crdt.getDocument());
	}

	static void test13_ConcurrentInsertSamePosition() {
		CharacterCRDT crdt = new CharacterCRDT();

		CharId root = new CharId(1, 1);
		crdt.insert(root, 'A', null);

		InsertOperation op1 = new InsertOperation(2, 2, 'X', root);
		InsertOperation op2 = new InsertOperation(1, 2, 'Y', root);

		op1.apply(crdt);
		op2.apply(crdt);

		check("Test 13 - Concurrent insert ordering", "AYX", crdt.getDocument());
	}

	static void test14_DeleteWhileInsert() {
		CharacterCRDT crdt = new CharacterCRDT();

		CharId a = new CharId(1, 1);
		crdt.insert(a, 'A', null);

		InsertOperation op1 = new InsertOperation(2, 2, 'B', a);
		DeleteOperation op2 = new DeleteOperation(1, 3, a);

		op1.apply(crdt);
		op2.apply(crdt);

		check("Test 14 - Delete + insert", "B", crdt.getDocument());
	}
}
