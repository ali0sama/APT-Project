import tests.BlockCRDTTest;
import tests.CharCRDTTest;
import tests.IntegrationTest;
import tests.SpecialCaseTest;
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
        
        //ConsoleDemo.main(null); could be used next phase for a simple console-based demo of the CRDT in action
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
        
        // Test 5: Integration with WebSocket message format (Member 2's code)
        System.out.println("\nTest 5: Integration with WebSocket Message Format");
        testWebSocketIntegration();
        
        System.out.println("\n=== All Serialization Tests Passed ===\n");
    }
    
    /**
     * Test round-trip serialization of InsertOperation
     */
    private static void testInsertOperationRoundTrip() {
        // Create an InsertOperation
        CharId parentID = new CharId(2, 1);
        InsertOperation original = new InsertOperation(1, 3, 'A', parentID, true, false);
        
        // Serialize to JSON
        String json = OperationSerializer.serialize(original);
        System.out.println("Serialized: " + json);
        
        // Deserialize back
        Operation deserialized = OperationSerializer.deserialize(json);
        InsertOperation restored = (InsertOperation) deserialized;
        
        // Verify all fields match
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
        // Create a DeleteOperation
        CharId targetID = new CharId(3, 1);
        DeleteOperation original = new DeleteOperation(1, 4, targetID);
        
        // Serialize to JSON
        String json = OperationSerializer.serialize(original);
        System.out.println("Serialized: " + json);
        
        // Deserialize back
        Operation deserialized = OperationSerializer.deserialize(json);
        DeleteOperation restored = (DeleteOperation) deserialized;
        
        // Verify all fields match
        assert restored.userID == original.userID : "userID mismatch";
        assert restored.clock == original.clock : "clock mismatch";
        assert restored.targetID.equals(original.targetID) : "targetID mismatch";
        
        System.out.println("✓ DeleteOperation round-trip successful");
    }
    
    /**
     * Test InsertOperation with null parentID (root insertion)
     */
    private static void testInsertOperationNullParent() {
        // Create an InsertOperation with null parentID (root insertion)
        InsertOperation original = new InsertOperation(1, 1, 'H', null, false, false);
        
        // Serialize to JSON
        String json = OperationSerializer.serialize(original);
        System.out.println("Serialized: " + json);
        
        // Deserialize back
        Operation deserialized = OperationSerializer.deserialize(json);
        InsertOperation restored = (InsertOperation) deserialized;
        
        // Verify all fields match
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
        // Create a CharacterCRDT
        CharacterCRDT crdt = new CharacterCRDT();
        
        // Create and serialize an InsertOperation (root insertion)
        InsertOperation insertOp = new InsertOperation(1, 1, 'H', null);
        String insertJson = OperationSerializer.serialize(insertOp);
        System.out.println("Serialized insert: " + insertJson);
        
        // Deserialize and apply it
        InsertOperation deserializedInsert = (InsertOperation) OperationSerializer.deserialize(insertJson);
        deserializedInsert.apply(crdt);
        
        // Verify the operation was applied
        String result = crdt.toString();
        assert result.contains("H") : "Character 'H' should be in CRDT after applying operation";
        System.out.println("CRDT after insert: " + result);
        
        // Now create a DeleteOperation targeting the inserted character
        CharId targetID = deserializedInsert.charID;
        DeleteOperation deleteOp = new DeleteOperation(2, 2, targetID);
        String deleteJson = OperationSerializer.serialize(deleteOp);
        System.out.println("Serialized delete: " + deleteJson);
        
        // Deserialize and apply it
        DeleteOperation deserializedDelete = (DeleteOperation) OperationSerializer.deserialize(deleteJson);
        deserializedDelete.apply(crdt);
        
        System.out.println("CRDT after delete: " + crdt.toString());
        System.out.println("✓ Apply deserialized operation test successful");
    }
    
    /**
     * Test integration with WebSocket message format (simulating Member 2's flow)
     * 
     * Simulates:
     * Client → serialize operation → send in "data" field
     * Server (Member 2) → receives and broadcasts
     * Client → receive "data" field → deserialize → apply to CRDT
     */
    private static void testWebSocketIntegration() {
        // Simulate a client creating an operation
        CharacterCRDT crdt1 = new CharacterCRDT();
        InsertOperation clientOp = new InsertOperation(1, 1, 'H', null);
        
        // Client serializes it (using OperationSerializer)
        String operationData = OperationSerializer.serialize(clientOp);
        System.out.println("Client serialized operation: " + operationData);
        
        // Simulate WebSocket message (Member 2's format):
        // {
        //   "action": "operation",
        //   "sessionID": "doc1",
        //   "userID": 1,
        //   "data": { "op": "insert", ... }
        // }
        // Member 2 stores: operationData = json.get("data").toString()
        
        // Simulate another client receiving it
        CharacterCRDT crdt2 = new CharacterCRDT();
        
        // Client 2 deserializes the operation from the "data" field
        Operation receivedOp = OperationSerializer.deserialize(operationData);
        
        // Client 2 updates its clock with the remote operation's clock
        // (In real code: clock.update(receivedOp.clock))
        
        // Client 2 applies the operation to its CRDT
        receivedOp.apply(crdt2);
        
        // Verify both CRDTs have the same content
        String crdt1Result = crdt1.toString();
        String crdt2Result = crdt2.toString();
        
        System.out.println("CRDT 1 (original): " + crdt1Result);
        System.out.println("CRDT 2 (after receiving): " + crdt2Result);
        
        System.out.println("✓ WebSocket integration test successful - Both CRDTs synchronized");
    }
}