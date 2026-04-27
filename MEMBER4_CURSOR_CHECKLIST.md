# 🎯 MEMBER 4: CURSOR DISPLAY CHECKLIST

## 🔴 Issue You Reported
Connected two clients with session "123":
- Left: user ID 1  
- Right: user ID 2

**Problem**: No colored cursor visible, no connection indication.

---

## ✅ FIXED: Server Port Mismatch

**Root cause**: EditorWindow dialog was defaulting to `ws://localhost:8080` but server runs on `ws://localhost:8081`

**Fix Applied**:  
[EditorWindow.java](src/ui/EditorWindow.java#L102): Changed default from `8080` → `8081`

---

## 🧪 TEST YOUR CHANGES

### Step 1: Compile
```powershell
$files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -cp "src;lib/*" $files
```

### Step 2: Start the Server (Member 2)
```powershell
java -cp src network.WebSocketServer
```
Server should print:
```
Server started on ws://localhost:8081
```

### Step 3: Start Two Clients
**Terminal 1 (Left client)**:
```powershell
java -cp src Main
```

**Terminal 2 (Right client)** - same command, new window
```powershell
java -cp src Main
```

### Step 4: Connect Both Clients

**Left client**:
1. Click "Connect" button
2. Fill dialog:
   - Server URL: `ws://localhost:8081` ✅ (NOW CORRECT)
   - Session ID: `123`
   - User ID: `1`
   - Role: Editor
3. Click "Connect"

**Right client**:
1. Click "Connect" button
2. Fill dialog:
   - Server URL: `ws://localhost:8081` ✅ (NOW CORRECT)
   - Session ID: `123`
   - User ID: `2`
   - Role: Editor
3. Click "Connect"

### Step 5: Verify Cursor Display

After connecting:
- [ ] Status bar shows "Connected" (green text) on both clients
- [ ] User list panel (right side) shows both users
- [ ] Type in left editor → see text appear in right editor
- [ ] Move cursor in left → see colored line in right editor at that position
- [ ] Move cursor in right → see colored line in left editor at that position
- [ ] Colored cursors are different colors (red, blue, green, etc.)

---

## 📊 Complete Cursor Display Flow

```
User 1 moves cursor to position 5
    ↓
EditorPane.attachCaretListener() fires
    ↓
Calculates CharId from visible character at position
    ↓
Creates cursor envelope JSON with afterUserID/afterClock
    ↓
NetworkSender.sendMessage() → WebSocketClient.sendCursorPosition()
    ↓
Server broadcasts: { action: "cursor", userID: 1, data: {afterUserID, afterClock} }
    ↓
User 2 receives: MessageHandler.handleCursor()
    ↓
Updates remoteCursors map: remoteCursors.put(1, new CharId(...))
    ↓
Calls refreshCallback → EditorWindow.onRemoteUpdate()
    ↓
EditorPane.updateRemoteCursorsFromCharIds(wsClient.getRemoteCursorsSnapshot())
    ↓
Converts CharIds to text positions using CRDT.getVisibleChars()
    ↓
EditorPane.renderRemoteCursors() paints colored line using CursorPainter
    ↓
✅ 2px colored vertical line appears in User 2's editor
```

---

## 🎨 Cursor Colors

Each user gets a unique color:
```java
Color[] CURSOR_COLORS = {
    Color.RED,                           // User 1
    new Color(30, 100, 210),             // User 2 (dark blue)
    new Color(20, 150, 20),              // User 3 (dark green)
    new Color(210, 120, 0),              // User 4 (orange)
    new Color(140, 30, 170),             // User 5 (purple)
    new Color(0, 160, 160)               // User 6 (cyan)
}
```

---

## 🔍 If Cursors Still Don't Show

### Check 1: Network Connection
```
Look at console output for:
✅ "[WebSocketClient] Connected to server"
✅ "[MessageHandler] Updated cursor for user X: CharId(...)"
```

### Check 2: CRDT State
Cursor position is converted using `crdt.getVisibleChars()`. If text isn't synced, positions won't match.

### Check 3: Highlighter API
The `CursorPainter` uses deprecated `modelToView()`. On some Java versions this might fail silently. If so, replace with:
```java
Rectangle r = c.modelToView2D(p0).getBounds();
```

### Check 4: Remote Cursor Storage
Verify that `EditorPane.updateRemoteCursorsFromCharIds()` is being called:
- Add breakpoint at: [EditorPane.java](src/ui/EditorPane.java#L430)
- Check that `charIdCursors` map is not empty

---

## 📝 Your Tasks as Member 4

| Task | Status | Details |
|------|--------|---------|
| Fix server port | ✅ DONE | Changed to 8081 |
| Cursor rendering | ✅ IMPLEMENTED | CursorPainter class |
| User list display | ✅ IMPLEMENTED | UserPanel shows connected users |
| Connection feedback | ✅ IMPLEMENTED | Status bar + dialog |
| Formatting (B/I buttons) | ✅ IMPLEMENTED | EditorPane.applyBold/Italic |
| Import/Export | ✅ IMPLEMENTED | EditorWindow menu buttons |

---

## 🚀 Next Steps

1. **Recompile** with the port fix
2. **Run all three components** (Server + 2 Clients)
3. **Connect both clients** to the same session
4. **Test cursor movement** → colored lines should appear
5. **Report any new issues** with exact error messages

Good luck! 🎉
