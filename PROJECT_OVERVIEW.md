# Complete Project Overview: Collaborative Real-Time Text Editor

## 🎯 Project Goal

Build a **real-time collaborative plain text editor** (like Google Docs) where multiple users can simultaneously edit the same document. All changes are synchronized instantly across all connected clients using **CRDT** (Conflict-free Replicated Data Type) to resolve conflicts deterministically.

---

## 📋 Project Requirements

### Document & Collaboration Management [20%]

- ✅ Import/Export .txt files with formatting (bold/italic)
- ✅ Sharing codes (editor code + viewer code)
- ✅ File management (create, open, rename, delete)
- ✅ Persistent storage in database

### Real-time Collaborative Editing [60%]

- ✅ Block-Based + Character-Based editing
- ✅ Two-layer CRDT system
- ✅ Concurrent edit conflict resolution
- ✅ Real-time operation broadcasting
- ✅ Cursor tracking & user presence
- ✅ Undo/Redo (last 10 operations)

### UI [20%]

- ✅ Text editor with formatting
- ✅ Shareable codes display
- ✅ Collaboration session join
- ✅ Import/Export menu
- ✅ Cursor display & user list
- ✅ Permission handling (viewers can't edit)

---

## 🏗️ Architecture: Two-Layer CRDT System

### Layer 1: Character-Level CRDT (Individual Characters)

```
CharacterCRDT (Tree-based)
├── Characters form a tree structure via parent-child relationships
├── Each character has unique CharId = (counter, userID)
├── Operations:
│   ├── insert(charId, value, parentID) - Add character
│   ├── delete(charId) - Mark deleted (tombstone)
│   └── merge() - Synchronize with remote replicas
└── Features:
    ├── Tree traversal for correct ordering
    ├── Tombstones for non-destructive deletion
    ├── Deterministic ordering on conflicts
    └── Supports formatting (bold/italic) per character
```

**Example**:

```
User 1 inserts 'A' at position 0      → CharId(1, 1) = 'A'
User 1 inserts 'B' after 'A'          → CharId(2, 1) = 'B'
User 2 inserts 'C' after 'A'          → CharId(1, 2) = 'C'
(both users have: A C B)  ✅ Deterministic despite concurrent edits
```

### Layer 2: Block-Level CRDT (Paragraph Structure)

```
BlockCRDT (Ordered sequence)
├── Document = ordered list of blocks
├── Each block:
│   ├── BlockId = (counter, userID)
│   ├── Contains CharacterCRDT (2-10 lines of text)
│   └── Can be: inserted, deleted, split, moved
├── Operations:
│   ├── insertBlock(blockId, index)
│   ├── deleteBlock(blockId)
│   ├── moveBlock(blockId, newIndex)
│   ├── splitBlock(blockId, charPosition)
│   └── merge()
└── Constraints:
    ├── Min 2 lines per block
    └── Max 10 lines per block
```

**Example**:

```
Block 1: "Hello world" (CharacterCRDT with 11 chars)
Block 2: "This is text" (CharacterCRDT with 12 chars)
Block 3: "Final block" (CharacterCRDT with 11 chars)
```

### Conflict Resolution Strategy

When two users edit concurrently:

```
Deterministic Ordering = (counter, userID)
  Lower counter wins
  If tie: Lower userID wins

Example:
User 1 inserts at position 0 → CharId(3, 1)
User 2 inserts at position 0 → CharId(3, 2)
Result: User 1's char comes first (lower userID)
```

---

## 🔄 System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI LAYER (Member 4)                  │
│  EditorPane │ UserPanel │ CursorDisplay │ EditorWindow      │
└──────────────────────┬──────────────────────────────────────┘
                       │ User types / clicks
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              OPERATION LAYER (Members 1 & 4)                │
│  InsertOperation │ DeleteOperation │ CreateFromUI          │
└──────────────────────────────────────────────────────────────┘
                       │ Create Operation
                       ▼
┌─────────────────────────────────────────────────────────────┐
│           SERIALIZATION LAYER (Member 1)                    │
│  OperationSerializer: Operation → JSON / JSON → Operation   │
│  Clock: Lamport clock for causality tracking                │
└──────────────────────────────────────────────────────────────┘
                       │ Convert to JSON
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              NETWORKING LAYER (Members 2 & 3)               │
│                                                             │
│  Client Side:                    Server Side:              │
│  WebSocketClient          WebSocketServer (Member 2)       │
│  ├─ Connect to server    ├─ Listen on port                │
│  ├─ Send operations      ├─ Manage sessions               │
│  └─ Receive updates      ├─ Validate permissions          │
│                          └─ Broadcast to clients          │
│  MessageHandler          CollaborationSession             │
│  ├─ Deserialize JSON     ├─ Store active users            │
│  └─ Apply to CRDT        ├─ Store operations              │
│                          └─ Track operation history       │
└──────────────────────────────────────────────────────────────┘
                       │ Send JSON
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  DATA LAYER (Phase 1 - Done)                │
│  CharacterCRDT │ BlockCRDT │ CharId │ BlockId              │
│  Clock │ InsertOp │ DeleteOp                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Three-Phase Development

### Phase 1: CRDT Foundation (COMPLETE ✅)

- ✅ Character-Level CRDT with tree structure
- ✅ Block-Level CRDT with ordering
- ✅ Insert/Delete/Move operations
- ✅ Tombstones for deletion
- ✅ Deterministic ordering
- ✅ Comprehensive tests

**Files**: All in src/crdt/, src/operations/, src/utils/

### Phase 2: UI + Networking (CURRENT - Your Part)

- ⏳ Text editor UI (Member 4)
- ⏳ Real-time communication via WebSocket (You + Member 3)
- ⏳ Sending/receiving CRDT operations (You + Members 1 & 3)
- ⏳ Cursor tracking (Member 4)
- ⏳ Basic collaboration session (You)

**Deadline**: April 18, 2026

### Phase 3: Final Delivery (Next Phase)

- ⬜ Database persistence
- ⬜ File management
- ⬜ Sharing codes
- ⬜ Permission handling
- ⬜ Full system integration

**Deadline**: May 3, 2026

---

## 👥 Team Workload Division

### Member 1: The Character Architect

**Phase 1**: Character CRDT implementation ✅
**Phase 2**:

- Serialization (Operation → JSON)
- Clock synchronization

### Member 2: The Structural Engineer (YOU)

**Phase 1**: Block CRDT implementation ✅
**Phase 2**:

- WebSocketServer.java - Central server
- CollaborationSession.java - Session management

### Member 3: The Conflict Resolution Specialist

**Phase 1**: Conflict resolution strategy ✅
**Phase 2**:

- WebSocketClient.java - Client networking
- MessageHandler.java - Deserialize and apply operations

### Member 4: The Integration & Test Lead

**Phase 1**: Testing ✅
**Phase 2**:

- EditorWindow.java - Main UI window
- EditorPane.java - Text editor
- UserPanel.java - User list
- CursorTracker.java - Cursor tracking

---

## 🔗 Data Flow: A Single Keystroke

### Complete Flow

```
1. USER TYPES 'A' IN EDITOR
   ↓
2. EditorPane (Member 4) captures keystroke
   ↓
3. EditorPane creates: InsertOperation(
     charId = CharId(clock=5, userID=1),
     value = 'A',
     parentID = CharId(clock=4, userID=1)
   )
   ↓
4. OperationSerializer (Member 1) converts to JSON:
   {
     "op": "insert",
     "userID": 1,
     "clock": 5,
     "value": "A",
     "parentUserID": 1,
     "parentClock": 4
   }
   ↓
5. WebSocketClient (Member 3) wraps and sends:
   {
     "action": "operation",
     "sessionID": "doc123",
     "userID": 1,
     "role": "editor",
     "data": { "op": "insert", ... }
   }
   ↓
6. MESSAGE TRAVELS OVER NETWORK TO SERVER
   ↓
7. YOUR SERVER (WebSocketServer) receives
   ↓
8. YOU validate: Is user 1 an EDITOR in session "doc123"? ✅
   ↓
9. YOU store operation in CollaborationSession.operationHistory
   ↓
10. YOU broadcast to all OTHER clients in "doc123"
    ↓
11. REMOTE CLIENT RECEIVES MESSAGE
    ↓
12. WebSocketClient (Member 3) receives
    ↓
13. MessageHandler (Member 3) deserializes JSON back to Operation
    ↓
14. MessageHandler applies to local CharacterCRDT:
    characterCRDT.insert(charId, 'A', parentID)
    ↓
15. EditorPane (Member 4) calls getDocument() to render
    ↓
16. USER SEES 'A' APPEAR ON THEIR SCREEN ✅
```

---

## 🎮 Example Scenario: Two Users Editing

### Setup

```
Document: "doc123"
User 1: EDITOR (Alice) - typing
User 2: EDITOR (Bob) - typing simultaneously
```

### Timeline

```
T0: Document is empty: ""

T1: Alice types 'H'
    InsertOperation(id=1, value='H')
    → Server stores: [op1]
    → Bob's client receives: [op1]
    → Bob sees: "H"

T2: Bob types 'i' (at same time Alice types 'e')
    CONCURRENT:
    Alice: InsertOperation(id=2, value='e', parent=1)
    Bob: InsertOperation(id=2, value='i', parent=1)
    → Server receives both and stores: [op1, op2_alice, op2_bob]
    → CRDT resolves: (2, Alice_ID=1) vs (2, Bob_ID=2)
    → Alice_ID < Bob_ID, so Alice's comes first
    → Both users see: "Hei" ✅ (deterministic!)

T3: Alice deletes 'i'
    DeleteOperation(id=2_bob)
    → Server stores
    → Bob sees it disappear: "He"
    → Tombstone marks it deleted but structure intact
```

---

## ⚙️ YOUR RESPONSIBILITIES (Member 2)

### WebSocketServer.java

**What you receive from clients:**

```json
{
  "action": "join|operation|cursor",
  "sessionID": "doc123",
  "userID": 1,
  "role": "editor",
  "data": {
    /* operation details */
  }
}
```

**What you do:**

1. **Route messages** to handlers based on action
2. **Manage sessions** - create/delete as needed
3. **Validate permissions** - reject operations from viewers
4. **Broadcast operations** - to all OTHER clients in same session
5. **Store history** - for late-joining users
6. **Handle disconnects** - cleanup connections and sessions

**Key methods:**

- `onMessage()` - Main handler
- `handleJoin()` - User joins
- `handleOperation()` - Edit received
- `broadcastToSession()` - Send to all others
- `onClose()` - User disconnects

### CollaborationSession.java

**What you manage:**

```
Per Document:
├── Active users with roles
├── Operation history
└── WebSocket connections
```

**Key methods:**

- `addUser(userID, role)` - Track new user
- `removeUser(userID)` - Track disconnect
- `isEditor(userID)` - Permission check
- `addOperation(json)` - Store for history
- `getOperationHistory()` - For late joiners

### UserPresence.java

**What you track:**

```
Per User:
├── userID
├── role (EDITOR/VIEWER)
└── joinTime
```

---

## 🧪 Testing Your Implementation

### Test 1: Single User Edit

```
1. Start server: java network.CollaborativeWebSocketServer 8080
2. Client connects (Member 3 implements WebSocketClient)
3. Client sends: { "action": "join", "sessionID": "doc1", "userID": 1, "role": "editor" }
4. Expected: User appears in session
5. Client sends: { "action": "operation", "sessionID": "doc1", "userID": 1, "data": {...} }
6. Expected: Operation stored in history
```

### Test 2: Two Users, Viewer Permission

```
1. User 1 (EDITOR) joins "doc1"
2. User 2 (VIEWER) joins "doc1"
3. User 2 tries to send operation
4. Expected: Server rejects and logs error
```

### Test 3: Late Joiner

```
1. User 1 edits "doc1" 5 times
2. User 2 joins "doc1"
3. Expected: User 2 receives all 5 operations
```

### Test 4: Disconnect & Cleanup

```
1. User 1 joins "doc1"
2. User 1 disconnects
3. Expected: Session "doc1" deleted (empty)
```

---

## 📦 Deliverables

Your branch: `phase2/ali`

**Files to implement:**

- ✅ src/network/WebSocketServer.java
- ✅ src/session/CollaborationSession.java
- ✅ src/session/UserPresence.java

**Files to document:**

- ✅ MEMBER2_IMPLEMENTATION_GUIDE.md
- ✅ MEMBER2_SETUP.md

**What NOT to do:**

- ❌ Push to remote repo (you implement locally only)
- ❌ Implement Members 1, 3, 4's work
- ❌ Deserialize operations (Member 3's job)
- ❌ Build UI (Member 4's job)

---

## 🚀 Quick Start

### 1. Download Dependencies

```bash
# Create lib folder
mkdir lib
cd lib

# Download Java-WebSocket
# Download GSON
cd ..
```

### 2. Compile

```bash
$files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -cp src:lib/* $files
```

### 3. Run Server

```bash
java -cp src:lib/* network.CollaborativeWebSocketServer 8080
```

### 4. Run Tests

```bash
java -cp src Main
```

---

## 📞 Integration with Other Members

**Member 1 (Serialization):**

- Provides: JSON format for operations
- Uses from you: Operation history storage

**Member 3 (WebSocket Client):**

- Sends: Operation JSON to your server
- Receives: Broadcast from your server
- Uses from you: Broadcasts to relay

**Member 4 (UI):**

- Sends: User join/operation actions
- Receives: User list updates and broadcasts
- Uses from you: User presence tracking

---

## ✅ Success Criteria

Your implementation is complete when:

1. ✅ Server accepts multiple WebSocket connections
2. ✅ Server creates and manages sessions per document
3. ✅ Server validates editor/viewer permissions
4. ✅ Server broadcasts operations to all OTHER clients
5. ✅ Server stores operation history
6. ✅ Server handles disconnects gracefully
7. ✅ Empty sessions are cleaned up
8. ✅ Late joiners receive full history
9. ✅ No compilation errors
10. ✅ All tests in Main.java pass

---

## 🎯 Summary

You are building the **heart** of the collaborative system. Your WebSocketServer is the central hub that:

- **Connects** all clients
- **Validates** permissions
- **Routes** operations
- **Manages** sessions
- **Broadcasts** changes

Without your implementation, users can't see each other's edits in real-time. You are the bridge between individual clients and the shared document state!

Good luck! 🚀
