package tests;

import crdt.block.Block;
import crdt.block.BlockCRDT;
import crdt.block.BlockID;
import crdt.character.CharId;

public class BlockCRDTTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		run();
		printResults();
	}

	public static void run() {
		passed = 0;
		failed = 0;
		test9_BlockInsertionDeterministicOrdering();
		test10_NormalBlockSplit();
		test11_ConcurrentSplitTieBreaker();
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

		boolean ok = crdt.getVisibleBlocks().size() == 2 && crdt.getVisibleBlocks().get(0).getContent().getDocument().equals("AB") && crdt.getVisibleBlocks().get(1).getContent().getDocument().equals("CD");

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

		checkTrue("Test 11 - Concurrent split tie-break", crdt.getVisibleBlocks().size() == 3);
	}
}
