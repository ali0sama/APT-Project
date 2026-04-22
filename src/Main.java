import tests.BlockCRDTTest;
import tests.CharCRDTTest;
import tests.IntegrationTest;
import tests.SpecialCaseTest;
import tests.NetworkIntegrationTest;

import serializations.OperationSerializer;
import operations.*;
import crdt.character.*;

public class Main {
    public static void main(String[] args) {
        // Run all Phase 1 tests
        new CharCRDTTest().runAll();
        new BlockCRDTTest().runAll();
        new IntegrationTest().runAll();
        new SpecialCaseTest().runAll();

        // Phase 2: Serialization tests
        testSerialization();

        // Phase 2: Network integration test (Member 3)
        try {
            new NetworkIntegrationTest().runAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tests serialization and deserialization of operations
     */
    private static void testSerialization() {
        System.out.println("\n=== Phase 2: Serialization Tests ===");

        // Test 1: InsertOperation serialization and deserialization
        System.out.println("\nTest 1: InsertOperation Serialization");
        testInsertOperationRoundTrip();

        // Test 2: DeleteOperation serialization and deserialization
        System.out.println("\nTest 2: DeleteOperation Serialization");
        testDeleteOperationRoundTrip();

        // Test 3: InsertOperation with null parentID
        System.out.println("\nTest 3: InsertOperation with null parentID (root insertion)");
        testInsertOperationNullParent();

        // Test 4: Apply deserialized operation to CRDT
        System.out.println("\nTest 4: Apply deserialized operation to CRDT");
        testApplyDeserializedOperation();

        // Test 5: Integration with WebSocket message format
        System.out.println("\nTest 5: Integration with WebSocket Message Format");
        testWebSocketIntegration();

        System.out.println("\n=== All Serialization Tests Passed ===\n");
    }

    /**
     * Test round-trip serialization of InsertOperation
     */
    private static void testInsertOperationRoundTrip() {
        CharId parentID = new CharId(2, 1);
        InsertOperation original = new InsertOperation(1, 3, 'A', parentID, true, false);

        String json = OperationSerializer.serialize(original);
        System.out.println("Serialized: " + json);

        Operation deserialized = OperationSerializer.deserialize(json);
        InsertOperation restored = (InsertOperation) deserialized;

        assert restored.userID == original.userID : "userID mismatch";
        assert restored.clock == original.clock : "clock mismatch";
        assert restored.value == original.value : "value mismatch";
        assert restored.parentID.equals(original.parentID) : "parentID mismatch";
        assert restored.bold == original.bold : "bold flag mismatch";
        assert restored.italic == original.italic : "italic flag mismatch";

        System.out.println("✓ InsertOperation round-trip successful");
    }

    /**
     * Test round-trip serialization of DeleteOperation
     */
    private static void testDeleteOperationRoundTrip() {
        CharId targetID = new CharId(3, 1);
        DeleteOperation original = new DeleteOperation(1, 4, targetID);

        String json = OperationSerializer.serialize(original);
        System.out.println("Serialized: " + json);

        Operation deserialized = OperationSerializer.deserialize(json);
        DeleteOperation restored = (DeleteOperation) deserialized;

        assert restored.userID == original.userID : "userID mismatch";
        assert restored.clock == original.clock : "clock mismatch";
        assert restored.targetID.equals(original.targetID) : "targetID mismatch";

        System.out.println("✓ DeleteOperation round-trip successful");
    }

    /**
     * Test InsertOperation with null parentID (root insertion)
     */
    private static void testInsertOperationNullParent() {
        InsertOperation original = new InsertOperation(1, 1, 'H', null, false, false);

        String json = OperationSerializer.serialize(original);
        System.out.println("Serialized: " + json);

        Operation deserialized = OperationSerializer.deserialize(json);
        InsertOperation restored = (InsertOperation) deserialized;

        assert restored.userID == original.userID : "userID mismatch";
        assert restored.clock == original.clock : "clock mismatch";
        assert restored.value == original.value : "value mismatch";
        assert restored.parentID == null : "parentID should be null";

        System.out.println("✓ InsertOperation with null parentID round-trip successful");
    }

    /**
     * Test applying deserialized operation to a CRDT and verifying the result
     */
    private static void testApplyDeserializedOperation() {
        CharacterCRDT crdt = new CharacterCRDT();

        InsertOperation insertOp = new InsertOperation(1, 1, 'H', null);
        String insertJson = OperationSerializer.serialize(insertOp);
        System.out.println("Serialized insert: " + insertJson);

        InsertOperation deserializedInsert = (InsertOperation) OperationSerializer.deserialize(insertJson);
        deserializedInsert.apply(crdt);

        String result = crdt.getDocument();
        assert result.contains("H") : "Character 'H' should be in CRDT after applying operation";
        System.out.println("CRDT after insert: " + result);

        CharId targetID = deserializedInsert.charID;
        DeleteOperation deleteOp = new DeleteOperation(2, 2, targetID);
        String deleteJson = OperationSerializer.serialize(deleteOp);
        System.out.println("Serialized delete: " + deleteJson);

        DeleteOperation deserializedDelete = (DeleteOperation) OperationSerializer.deserialize(deleteJson);
        deserializedDelete.apply(crdt);

        System.out.println("CRDT after delete: " + crdt.getDocument());
        System.out.println("✓ Apply deserialized operation test successful");
    }

    /**
     * Test integration with WebSocket message format
     */
    private static void testWebSocketIntegration() {
        CharacterCRDT crdt1 = new CharacterCRDT();
        InsertOperation clientOp = new InsertOperation(1, 1, 'H', null);

        String operationData = OperationSerializer.serialize(clientOp);
        System.out.println("Client serialized operation: " + operationData);

        CharacterCRDT crdt2 = new CharacterCRDT();

        Operation receivedOp = OperationSerializer.deserialize(operationData);
        receivedOp.apply(crdt2);

        // Apply locally too so the comparison is meaningful
        clientOp.apply(crdt1);

        String crdt1Result = crdt1.getDocument();
        String crdt2Result = crdt2.getDocument();

        System.out.println("CRDT 1 (original): " + crdt1Result);
        System.out.println("CRDT 2 (after receiving): " + crdt2Result);

        assert crdt1Result.equals(crdt2Result) : "CRDT states do not match";
        System.out.println("✓ WebSocket integration test successful - Both CRDTs synchronized");
    }
}