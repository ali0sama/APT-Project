# APT-Project
How to Run (VS Code)

1.Open the project folder in VS Code
2.Open Main.java
3.Click the "Run" button


Character CRDT Design:

- Tree-based structure using parent-child relationships
- Each character has a unique CharId (counter + userID)
- Tombstones are used instead of physical deletion
- Deleted nodes are skipped in rendering but still traversed

Conflict Resolution:

- Concurrent inserts are ordered using CharId
- Tie-breaker: lower counter, then lower userID
- Structure is deterministic across all replicas


How to Run (Terminal)

1.Open terminal in project folder
2.Navigate to src:
cd src
3.Compile all files:
javac Main.java crdt/character/.java crdt/block/.java utils/*.java
4.Run the program:
java Main
