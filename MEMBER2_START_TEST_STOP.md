# 🚀 MEMBER 2 - START, TEST, STOP SERVER

**Complete step-by-step guide for your WebSocket server**

---

## 📋 TABLE OF CONTENTS

1. [Start Server](#start-server)
2. [Test Server](#test-server)
3. [Stop Server](#stop-server)
4. [Troubleshooting](#troubleshooting)

---

## 🟢 START SERVER

### Step 1: Open PowerShell

Click Start menu → Type `PowerShell` → Press Enter

### Step 2: Navigate to Project

```powershell
cd "D:\Uni Semester 6\APT\APT-Project"
```

### Step 3: Compile Code (First Time Only)

**Only run this ONCE or when you modify Java files:**

```powershell
$files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "src;lib\*" $files
```

**Expected Output:**

```
(No output = Success ✅)
PS D:\Uni Semester 6\APT\APT-Project>
```

**If you see errors:**

- Check file syntax
- Verify all .java files are in `src/` folder
- Make sure `lib/` has 4 JAR files

### Step 4: Start Server

```powershell
java -cp "src;lib\*" network.WebSocketServer 8081
```

**Expected Output (SUCCESS ✅):**

```
[WebSocketServer] Initialized on port 8081
[WebSocketServer] Server running on port 8081
[WebSocketServer] Server started successfully
```

**If you see PORT ALREADY IN USE:**

```
java.net.BindException: Address already in use: bind
```

→ Go to [Stop Server](#stop-server) section and kill old process, then restart

### Step 5: Leave Running

**IMPORTANT: Do NOT close this PowerShell window!**

- Leave it open and visible
- Watch for connection messages
- Monitor logs while testing

---

## 🧪 TEST SERVER

### Test 1: Check Server is Listening (Easy)

**Open a NEW PowerShell window** (keep server running in first window):

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 8081
```

**Expected Output (SUCCESS ✅):**

```
ComputerName     : 127.0.0.1
RemoteAddress    : 127.0.0.1
RemotePort       : 8081
TcpTestSucceeded : True
```

**If you see FALSE:**

```
TcpTestSucceeded : False
```

→ Server crashed. Go to [Stop Server](#stop-server), then [Start Server](#start-server) again

---

### Test 2: Monitor Server Logs (Watch for Connections)

Keep the **server window visible** and watch for:

```
[WebSocketServer] New connection from 127.0.0.1:XXXXX
[WebSocketServer] User 1 joined session doc1 as EDITOR
[WebSocketServer] Operation broadcasted to N clients
[WebSocketServer] User disconnected
```

**What each message means:**

- `New connection` = Client connected ✅
- `User X joined session Y as EDITOR/VIEWER` = Join successful ✅
- `Operation broadcasted` = Edit operation sent to other clients ✅
- `User disconnected` = Client left ✅

---

### Test 3: Port Status Check

**In new PowerShell (keep server running):**

```powershell
netstat -ano | findstr ":8081"
```

**Expected Output (SUCCESS ✅):**

```
  TCP    0.0.0.0:8081           0.0.0.0:0              LISTENING       XXXXX
  TCP    [::]:8081              [::]:0                 LISTENING       XXXXX
```

If empty or no results = Server crashed

---

### Test 4: Integration Test (When Member 3 is Ready)

When Member 3 implements `WebSocketClient`:

1. **They run their client code**
2. **Watch your server window for:**
   ```
   [WebSocketServer] New connection from 127.0.0.1:XXXXX
   [WebSocketServer] User 1 joined session doc1 as EDITOR
   ```
3. **They send an INSERT operation**
4. **You should see:**
   ```
   [WebSocketServer] Operation received: {"type":"INSERT","content":"hello"...}
   [WebSocketServer] Operation broadcasted to N clients
   ```
5. **If you see this = YOUR SERVER IS WORKING! ✅**

---

## 🔴 STOP SERVER

### Method 1: Kill from New Terminal (RECOMMENDED)

**Open a NEW PowerShell window:**

```powershell
Get-Process java | Stop-Process -Force
```

**Expected Output:**

```
(No output = Success ✅)
PS C:\Users\Ali>
```

**Verify it's dead:**

```powershell
netstat -ano | findstr ":8081"
```

**Expected Output (SUCCESS ✅):**

```
(No output = Port is FREE ✅)
```

---

### Method 2: Task Manager

1. Press `Ctrl + Shift + Esc` (opens Task Manager)
2. Find `java.exe` in the list
3. Right-click → **End Task**
4. Close Task Manager

---

### Method 3: taskkill Command

**Open new PowerShell:**

```powershell
taskkill /IM java.exe /F
```

**Expected Output:**

```
SUCCESS: The process "java.exe" with PID XXXXX has been terminated.
```

---

## 🔧 TROUBLESHOOTING

### Problem: "Port already in use"

**Symptoms:**

```
java.net.BindException: Address already in use: bind
```

**Solution:**

```powershell
Get-Process java | Stop-Process -Force
Start-Sleep -Seconds 2
java -cp "src;lib\*" network.WebSocketServer 8081
```

---

### Problem: "package org.java_websocket does not exist"

**Symptoms:**

```
error: package org.java_websocket.server does not exist
```

**Solution:**

1. Check `lib/` folder has all 4 JARs:

   ```powershell
   Get-ChildItem lib\
   ```

   Should show:
   - `Java-WebSocket-1.5.4.jar`
   - `gson-2.10.1.jar`
   - `slf4j-api-2.0.5.jar`
   - `slf4j-simple-2.0.5.jar`

2. Recompile with correct path separator (semicolon):
   ```powershell
   $files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "src;lib\*" $files
   ```

---

### Problem: Server starts but immediately crashes

**Symptoms:**

```
[WebSocketServer] Initialized on port 8081
[WebSocketServer] Server running on port 8081
(then it exits)
```

**Solution:**

1. Kill all Java: `Get-Process java | Stop-Process -Force`
2. Wait 2 seconds: `Start-Sleep -Seconds 2`
3. Restart: `java -cp "src;lib\*" network.WebSocketServer 8081`

---

### Problem: TcpTestSucceeded = False

**Symptoms:**

```
TcpTestSucceeded : False
```

**Solution:**

1. Check if server is running:
   ```powershell
   netstat -ano | findstr ":8081"
   ```
2. If empty = server crashed
   - Restart server from [Start Server](#start-server) section

3. If shows LISTENING but TcpTestSucceeded is False:
   - Wait 3 seconds (server might be starting)
   - Try again

---

## ⚡ QUICK REFERENCE CHEAT SHEET

| Action              | Command                                                                                                              |
| ------------------- | -------------------------------------------------------------------------------------------------------------------- |
| **Navigate**        | `cd "D:\Uni Semester 6\APT\APT-Project"`                                                                             |
| **Compile**         | `$files = Get-ChildItem src -Recurse -Filter *.java \| ForEach-Object { $_.FullName }; javac -cp "src;lib\*" $files` |
| **Start**           | `java -cp "src;lib\*" network.WebSocketServer 8081`                                                                  |
| **Check Running**   | `netstat -ano \| findstr ":8081"`                                                                                    |
| **Test Connection** | `Test-NetConnection -ComputerName 127.0.0.1 -Port 8081`                                                              |
| **Kill Server**     | `Get-Process java \| Stop-Process -Force`                                                                            |
| **List Lib Files**  | `Get-ChildItem lib\`                                                                                                 |

---

## ✅ COMPLETE WORKFLOW EXAMPLE

### First Time Setup

```powershell
# 1. Navigate
cd "D:\Uni Semester 6\APT\APT-Project"

# 2. Compile
$files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "src;lib\*" $files

# 3. Start Server
java -cp "src;lib\*" network.WebSocketServer 8081

# 4. See this output:
# [WebSocketServer] Initialized on port 8081
# [WebSocketServer] Server running on port 8081
# [WebSocketServer] Server started successfully
```

### Testing

**In NEW PowerShell window:**

```powershell
# Check if listening
Test-NetConnection -ComputerName 127.0.0.1 -Port 8081

# Should see: TcpTestSucceeded : True

# Verify port
netstat -ano | findstr ":8081"

# Should see: LISTENING
```

### Stopping

**In NEW PowerShell window:**

```powershell
# Kill server
Get-Process java | Stop-Process -Force

# Verify dead
netstat -ano | findstr ":8081"

# Should see: (empty)
```

---

## 📝 IMPORTANT NOTES

⚠️ **DO NOT CLOSE SERVER WINDOW** until you're completely done testing

- Server must stay running for clients to connect
- If you close it, teammates can't connect

⚠️ **USE WINDOWS PATHS**

- Semicolon: `;` (not colon `:`)
- Backslash: `\` (not forward slash `/`)
- Example: `src;lib\*` (correct)
- Wrong: `src:lib/*` (incorrect)

⚠️ **COMPILATION ONLY NEEDED WHEN**

- First time setting up
- You modify Java code (.java files)
- You can skip if nothing changed

⚠️ **SERVER PORT 8081**

- Tell your teammates: "Connect to `localhost:8081`"
- NOT `localhost:8080` (that's blocked)
- NOT `127.0.0.1:8080`
- Use `8081` only!

---

## 🎯 SUCCESS INDICATORS

**Server Started ✅:**

- You see all 3 startup messages
- Window stays open (doesn't crash)
- No red error text

**Server Tested ✅:**

- `TcpTestSucceeded : True`
- `netstat` shows `LISTENING`
- No connection refused errors

**Server Stopped ✅:**

- `netstat` shows no results for `:8081`
- PowerShell returned to prompt
- Port is free to use again

---

## 🆘 NEED HELP?

If something breaks:

1. **Check this file** for matching symptoms
2. **Kill and restart** (kills old processes, then restarts clean)
3. **Verify compilation** (recompile all .java files)
4. **Check lib folder** (all 4 JARs present)
5. **Ask team lead** if still broken

---

**YOU'RE READY TO TEST! 🚀**
