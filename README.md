# MCJARVPS — Java 21 Pterodactyl Linux VPS

**Author:** `xs7david`  
**Target Runtime:** Java 21+  
**Guest Environment:** Alpine Linux (PRoot userland virtualization)

---

## 🚀 Overview

**MCJARVPS** transforms any Minecraft or generic Java Docker container on **Pterodactyl Panel** into a fully functional Linux VPS environment without needing root host privileges or Docker socket access.

Built entirely for **Java 21**, it automatically detects container CPU architectures (`x86_64` vs `ARM64/aarch64`), bootstraps a static **PRoot** engine, extracts an **Alpine Linux rootfs** equipped with `apk` package management, and exposes two versatile remote control interfaces using **a single server port**.

---

## ✨ Key Features

1. **Multi-Architecture Auto-Detection (`ARM64` & `x86_64`)**
   - Automatically probes the host container environment (`os.arch` and `uname -m`).
   - Downloads the matching static PRoot binary and Alpine minirootfs archive.
2. **Single-Port Design**
   - Pterodactyl servers usually have only **one** primary network port allocated (e.g. `25565` or custom assigned port).
   - In MCJARVPS, both **`webssh`** and **`ssh`** share a **single port configuration** (`port`). Switching modes in the config immediately binds that port to the desired service.
3. **WebSSH with PuTTY-Style Pure Console**
   - Standard, clean login portal (`username` & `password`).
   - After authentication, transitions into a 100% fullscreen, black terminal powered by `xterm.js`.
   - No distracting web widgets or HTML borders.
   - Full keyboard shortcuts: `Ctrl+C`, `Ctrl+V` (clipboard paste), `Ctrl+Z`, `Ctrl+L`, `F1`–`F12`, arrow keys, and tab completion.
4. **Native Real SSH Server**
   - Embedded Apache MINA SSH daemon.
   - Connect directly using real desktop terminal clients: **PuTTY**, **OpenSSH**, **Termius**, etc.
   - Syntax: `ssh <username>@<server-ip> -p <port>`
5. **No Staircasing / Terminal Line Discipline (CRLF)**
   - Strict `CRLF` (`\r\n`) line normalization and `stty onlcr icrnl` handling eliminate the classic UNIX staircase indentation bug in PuTTY and WebSSH.
6. **Interactive Pterodactyl Console VPS**
   - Configurable direct terminal bridging: Type commands directly in Pterodactyl's **Console** tab without needing an external SSH or browser connection.
7. **Alpine Linux Package Manager (`apk`)**
   - Includes full access to Alpine Linux packages:
     ```bash
     apk update
     apk add python3 git curl nano htop build-base neofetch
     ```

---

## ⚙️ Configuration Reference (`setup.yml` / `config.yml`)

The configuration file is automatically generated on first startup with a randomized secure password and clear English comments. Both `setup.yml` and `config.yml` are supported.

```yaml
# ==============================================================================
#  MCJARVPS - Pterodactyl Java 21 Linux VPS Configuration
#  Author: xs7david
# ==============================================================================

# ------------------------------------------------------------------------------
# [AUTH] Access Credentials
# Used to authenticate when connecting via WebSSH or native SSH.
# ------------------------------------------------------------------------------
auth:
  username: admin
  password: <auto_generated_secure_password>

# ------------------------------------------------------------------------------
# [CONTROL] Access Modes & Single Port Configuration
#
# Available modes:
#   - 'webssh' : Web-based interactive terminal with login screen.
#                Access via your browser at http://<server-ip>:<port>/
#                Features a clean login page and a fullscreen, black PuTTY-style
#                xterm console. Supports Ctrl+C, Ctrl+V, F1-F12 and all keys.
#
#   - 'ssh'    : Real native SSH server powered by Apache MINA SSHD.
#                Connect using standard tools like PuTTY, Termius, or OpenSSH:
#                ssh admin@<server-ip> -p <port>
#
# Single Port Design:
#   Whichever mode you choose ('webssh' or 'ssh'), the service will bind to
#   the single 'port' configured below. Set this to your Pterodactyl allocated port!
# ------------------------------------------------------------------------------
control:
  mode: webssh
  port: 25565

# ------------------------------------------------------------------------------
# [CONSOLE] Pterodactyl Host Console Integration
#
# Settings:
#   - true  : Direct terminal access. You can type Linux commands directly
#             into the Pterodactyl panel's Console tab without needing SSH/Web.
#   - false : Status/Read-only mode. The panel console will only display server
#             logs and startup status, ignoring interactive input.
# ------------------------------------------------------------------------------
console:
  enable_console_vps: true
```

---

## 📦 Deployment Instructions (Pterodactyl Panel)

### Step 1: Upload `server.jar`
1. Open your Pterodactyl server dashboard.
2. Navigate to **File Manager**.
3. Upload `server.jar` (compiled in the project root) to the server's root folder.

### Step 2: Verify Startup Command
In the **Startup** tab of Pterodactyl, ensure your server startup command points to `server.jar`:
```bash
java -Xms128M -XX:MaxRAMPercentage=95.0 -jar server.jar
```
*(Make sure the Docker image assigned to your server is **Java 21**).*

### Step 3: Start the Server
1. Click **Start** in Pterodactyl.
2. On first boot, MCJARVPS will:
   - Identify your container CPU architecture (`x86_64` or `ARM64`).
   - Download the static PRoot binary and Alpine Linux rootfs.
   - Generate `setup.yml` with your initial login credentials.
   - Display the username and password in the server console:
     ```
     ---------------------------------------------------------------
      Config File       : setup.yml
      Host Architecture : X86_64
      Control Mode      : WEBSSH
      Server Port       : 25565
      Console VPS       : ENABLED (Interactive)
      Login Username    : admin
      Login Password    : <YOUR_GENERATED_PASSWORD>
     ---------------------------------------------------------------
     ```

### Step 4: Connect to Your VPS

#### If `mode: webssh`:
1. Open your web browser and navigate to:  
   `http://<SERVER_IP>:<PORT>/`  
   *(Replace `<PORT>` with your Pterodactyl server port).*
2. Log in with the `username` and `password` shown in the console.
3. Enjoy an authentic, full-screen PuTTY-style terminal!

#### If `mode: ssh`:
1. Open PuTTY or your terminal.
2. Connect to the server:
   ```bash
   ssh admin@<SERVER_IP> -p <PORT>
   ```
3. Enter your password when prompted.

---

## 🔄 Switching Between Modes

To switch between WebSSH and native SSH:
1. Stop the server in Pterodactyl.
2. In `setup.yml` (or `config.yml`), change:
   ```yaml
   control:
     mode: ssh      # or 'webssh'
     port: 25565    # keeps using the exact same Pterodactyl port
   ```
3. Start the server.

---

## 🛠️ Building From Source

Requirements:
- Java JDK 21+
- Apache Maven 3.8+

Run the following command in the project root:
```bash
mvn clean package
```
The standalone fat JAR is generated at:
```
target/server.jar
```
Copy it to your Pterodactyl server and run!

---

## 👤 Author
Developed by **xs7david**.
