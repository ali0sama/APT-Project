# APT-Project

How to Run (VS Code)

1. Open the project folder in VS Code
2. Open src/Main.java
3. Click Run

How to Run (Terminal)

From project root (PowerShell):

1. Compile all sources
`$files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac $files`

2. Run full test runner
`java -cp src Main`

From src folder (PowerShell):

1. Compile all sources
`$files = Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac $files`

2. Run full test runner
`java Main`

Character CRDT Design

- Tree-based structure using parent-child relationships
- Each character has a unique CharId (counter + userID)
- Tombstones are used instead of physical deletion
- Deleted nodes are skipped in rendering but still traversed

Conflict Resolution

- Concurrent inserts are ordered using CharId
- Tie-breaker: lower counter, then lower userID
- Structure is deterministic across replicas
