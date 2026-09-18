# Bash Terminal Engine (In-App Linux Developer Runtime & Integrated Terminal)

## Problem Statement
How Might We provide a full-fledged, zero-root Linux development environment (Python, Node.js, Rust/C compilers, Git, and package managers) inside CodeAgent on Android, giving both the human developer and the AI agent an integrated, VS Code-grade terminal panel?

---

## Recommended Direction

We will implement an embedded, rootless Linux developer environment directly inside the CodeAgent Android app using an **Alpine Linux PRoot container** paired with a **Native JNI Pseudo-Terminal (PTY) bridge** and an **xterm.js Compose Web Panel**.

This turns CodeAgent into a standalone, full-stack IDE on Android—capable of running local build tools, package managers (`apk`, `pip`, `npm`), test suites, and interactive REPLs without requiring Termux, root access, or remote servers.

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI LAYER                                  │
│   Jetpack Compose Bottom Sheet / Drawer  ◄──►  Virtual Accessory Bar   │
│   └─ Android WebView (xterm.js 5.x + WebGL + FitAddon)                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ WebSocket / WebMessageBridge
┌──────────────────────────────────▼─────────────────────────────────────┐
│                           CORE ENGINE LAYER                            │
│   PtyBridge (Kotlin Coroutines)  ◄──►  AgentOrchestrator Tool Hook     │
│   └─ JNI libpty.so (openpty, forkpty, ioctl, winsize)                  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ ptmx / pts
┌──────────────────────────────────▼─────────────────────────────────────┐
│                          LINUX RUNTIME LAYER                           │
│   proot (ptrace syscall translation, rootless chroot/mount)            │
│   └─ Alpine Linux Rootfs ($FILESDIR/rootfs/alpine)                     │
│      ├── /bin/sh (ash/bash)                                            │
│      ├── /sbin/apk (package manager: python3, nodejs, git, gcc, rust)  │
│      └── /workspace (bind-mounted to project folder)                   │
└────────────────────────────────────────────────────────────────────────┘
```

### Architecture Breakdown

1. **Low-Level Native PTY Bridge (`core:terminal`)**:
   - Small native C library (`libpty.so`) compiled via Android NDK.
   - Provides true POSIX pseudo-terminal allocation (`posix_openpt()`, `grantpt()`, `unlockpt()`, `ioctl(TIOCSWINSZ)` for dynamic terminal resizing).
   - Supports raw mode, signal forwarding (`SIGINT`, `Ctrl+C`, `Ctrl+Z`), and interactive ncurses programs (`nano`, `vim`, `htop`, REPLs).

2. **Rootless Alpine Linux PRoot Container (`core:terminal`)**:
   - Bundles statically linked `proot` binaries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
   - Uses a minimal Alpine Linux mini-rootfs (~12MB compressed) extracted to `context.filesDir/rootfs/alpine`.
   - Mounts the active project folder to `/workspace` inside Alpine.
   - Grants access to Alpine's `apk` package repository (`apk add python3 nodejs git build-base cargo`).

3. **Integrated Terminal Panel (`feature:terminal` / `core:ui`)**:
   - Collapsible, resizable bottom panel embedded inside the Editor and Chat screens.
   - High-performance `xterm.js` rendering via Android WebView with hardware font acceleration and full ANSI 256 / Truecolor support.
   - Virtual accessory toolbar for mobile touch keyboards (`ESC`, `TAB`, `CTRL`, `ALT`, `|`, `~`, `↑`, `↓`, `←`, `→`).

4. **Agent-Human Co-Execution**:
   - The AI agent's `execute_command` tool executes directly within the Alpine environment.
   - When the agent runs builds or tests, standard output and errors stream live into both the AI chat log and the shared terminal console.

---

## Key Assumptions to Validate

- [ ] **Android W^X and Native Execution**: Validate that `proot` and Alpine ELF binaries execute cleanly on Android 10+ without SELinux denial.
- [ ] **First-Run Bootstrap Download**: Validate rootfs extraction speed and network download size on a minimal mobile connection (~15MB download, ~60MB extracted).
- [ ] **Package Manager Stability**: Verify that standard developer toolchains (`python3 -m venv`, `npm install`, `gcc`, `git`) install and execute inside Alpine PRoot without kernel syscall mismatches.

---

## MVP Scope

### In Scope (MVP)
- **Phase 1 (PTY + xterm.js Panel)**:
  - JNI `libpty.so` implementation with `openpty` and window resize hooks.
  - `xterm.js` Compose WebView component with bi-directional streaming bridge.
  - Virtual keys keyboard accessory toolbar.
  - Fallback subshell and built-in virtual shell integration.
- **Phase 2 (Alpine PRoot Runtime)**:
  - Multi-arch `proot` binary integration (`arm64-v8a`, `x86_64`).
  - Automatic download and extraction manager for Alpine mini-rootfs.
  - Bind-mounting active project directories to `/workspace`.
  - Default environment configuration (`PATH`, `HOME`, `PS1`).
- **Phase 3 (Agent Co-Execution)**:
  - Route agent `execute_command` invocations through the PRoot runtime.
  - Live terminal streaming during agent tool calls.

---

## Not Doing (and Why)

- **External Termux App Dependency / IPC Plugins**:
  - *Why*: Requiring users to install and configure separate external apps introduces broken permission flows, SAF file sync issues, and disjointed UX. Everything will run self-contained inside CodeAgent.
- **Full 1GB+ Ubuntu/Debian Image by Default**:
  - *Why*: Heavy distros waste mobile storage and take minutes to download. Alpine Linux provides standard toolchains (`apk add nodejs python3`) in less than 15MB.
- **X11 / Wayland Graphical Desktop Server**:
  - *Why*: CodeAgent is focused on coding, language runtimes, testing, and Git operations. Running full desktop GUIs on mobile is out of scope and battery-intensive.

---

## Open Questions

1. **Background Service Lifecycle**:
   - If a user starts a long-running development server (e.g. `npm run dev` or `python -m http.server`), should a foreground notification service keep the process alive when the app is minimized?
2. **Dynamic Toolchain Bundles**:
   - Should we offer one-tap "Packs" (e.g. *Python Data Pack*, *Node.js Web Pack*, *C/C++ Pack*) in the Settings UI to pre-install common toolchains?
