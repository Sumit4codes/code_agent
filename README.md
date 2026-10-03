# CodeAgent — Android AI Coding Agent

A clean-room, Android-native AI coding agent inspired by [OpenCode](https://github.com/opencode-ai/opencode). Open code projects on your device, chat with an autonomous AI agent that understands your repository, review proposed changes as diffs, run real Linux terminal commands with full terminal emulation, and sync your configuration securely across devices.

## Features

- **Project Explorer** — Open any local folder on your device (`MANAGE_EXTERNAL_STORAGE`), browse the file tree with responsive split view, filter files instantly with a compact search bar, and inspect files with syntax highlighting.
- **Full Linux Terminal Emulator** — Real interactive Linux terminal powered by Termux's `TerminalView` and ANSI VT100 emulator with 24-bit truecolor, alternate screen buffers, PTY session lifecycle, and soft keyboard input management. Supports full-screen terminal editors like `vim`, `nano`, and shell utilities.
- **Alpine Linux inside PRoot** — Run real Linux package management on Android via `apk` (install `git`, `python3`, `gcc`, `g++`, `make`, `vim`, `nodejs`) with access to host storage (`/sdcard`, `/storage`). Automatic fallback to native Android Toybox shell (`/system/bin/sh`).
- **Developer Accessory Keyboard** — Monospace accessory bar with dedicated `ESC`, `TAB`, `CTRL`, `CTRL-C`, arrow keys (`↑`, `↓`, `←`, `→`), and quick command tokens (`sh`, `bash`, `chmod +x`, `git`, `python3`, `gcc`, `make`, `vim`).
- **Autonomous Tool-Calling Agent** — Converse with OpenAI-compatible LLMs (GPT-4o, Claude 3.7, DeepSeek-V3/R1, Qwen, Ollama). The agent autonomously iterates through tool calls—reading files, searching code, listing directories, proposing edits, and executing terminal commands—without artificial iteration caps.
- **Safety-Bounded File Reading** — Structured file reading with an 800-line pagination cap per call, 45 KB byte limit protection against minified bundles, explicit next-page guidance (`start_line=801`), and a 100 MB file size limit to prevent LLM context blowups or device OOMs.
- **Diff Review** — Every proposed change appears as a colored side-by-side / unified diff; approve or reject each change individually or in bulk before anything is applied to disk.
- **Zero-Knowledge E2EE Cloud Sync** — Synchronize API keys, provider configurations, and settings cross-device via encrypted secret GitHub Gists ($0 serverless infrastructure). Protected with AES-256-GCM and PBKDF2 passphrase key derivation (100,000 iterations); no third-party server ever sees your credentials.
- **1-Tap GitHub Browser Sign-In** — Authenticate securely with GitHub via OAuth Device Authorization Flow in your default web browser, with automatic background polling and token storage.
- **Multi-Provider Architecture & Live Model Discovery** — Configure and switch between multiple providers (OpenAI, OpenRouter, Anthropic, DeepSeek, Groq, Gemini, Ollama, custom proxies). Fetch models dynamically via automated `/models` querying.
- **Session Persistence** — Chat history, provider settings, and pending changes survive app restarts via encrypted local Room database.
- **Markdown & Code Rendering** — Rich Markdown rendering for assistant reasoning, code blocks, lists, links, and formatted tables.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                             app                                  │
│         MainActivity · AppNavHost · AppModule (DI)               │
├────────────┬────────────┬────────────┬────────────┬──────────────┤
│  feature/  │  feature/  │  feature/  │  feature/  │  feature/    │
│  projects  │   chat     │   editor   │  terminal  │  settings    │
├────────────┴────────────┴────────────┴────────────┴──────────────┤
│ core/agent  core/terminal  core/git   core/ai   core/files       │
│ AgentOrch.  TermuxSession  CliGitOps  AiProv.   File FS          │
│ ToolExec.   PosixExecutor  Git CLI    SSE/Fetch PathSafe / Diff  │
│ ToolReg.    Alpine PRoot   W^X Pream. ModelFetch                 │
├───────────────────────────────────────┬──────────────────────────┤
│              core/data                │         core/ui          │
│   Room DB · EncryptedKeyStore         │    Theme · MarkdownView  │
│   CloudSyncManager · VaultCrypto      │                          │
│   GitHubGistSyncClient · SyncAccount  │                          │
├───────────────────────────────────────┴──────────────────────────┤
│                           core/model                             │
│              Project · Session · Message · Tools                 │
└──────────────────────────────────────────────────────────────────┘
```

### Module Overview

| Module | Responsibility |
|---|---|
| `app` | Navigation (`AppNavHost`), Hilt application entry point, dependency injection bindings |
| `feature/projects` | Directory browser, project creation, recent workspaces |
| `feature/chat` | AI chat interface with streaming text deltas, tool-call event chips, diff banners |
| `feature/editor` | Responsive split view / full-screen code viewer, compact file search bar, breadcrumbs, terminal shortcut |
| `feature/terminal` | Dedicated Linux terminal screen, Termux `TerminalView` canvas, soft keyboard toggle, accessory bar |
| `feature/settings` | Multi-provider manager, live dynamic model discovery, Zero-Knowledge E2EE cloud sync, GitHub OAuth |
| `core/model` | Domain entities (`Project`, `Session`, `Message`, `PendingChange`, `ToolSpec`, `PopularProviders`) |
| `core/ai` | `AiProvider` interface, OpenAI-compatible SSE streaming, dynamic `ModelFetcher` |
| `core/agent` | Unbounded `AgentOrchestrator` loop, `ToolExecutor`, `ToolRegistry`, `EditResolver` |
| `core/terminal` | `TermuxSessionManager` (PTY & Termux terminal bridge), `AlpineBootstrapManager` (PRoot), `PosixTerminalExecutor` |
| `core/git` | Native CLI Git operations (`CliGitOperations`), repository status, branch tracking |
| `core/files` | Direct file filesystem (`FileProjectFileSystem`), path safety guards, diff engine |
| `core/data` | Room database, `VaultCrypto` (AES-256-GCM + PBKDF2), `CloudSyncManager`, `GitHubGistSyncClient`, `SyncAccountStorage` |
| `core/ui` | Material3 theme, monospace styling, Markdown rendering composable |

### Key Design Decisions

- **Zero-Knowledge E2EE Cloud Sync via GitHub Gist ($0 Backend Architecture)** — Cloud synchronization without maintaining private backend servers or subscription databases. Encrypted with client-side AES-256-GCM using keys derived via PBKDF2 (SHA-256, 100,000 iterations) from a user-supplied encryption passphrase, stored inside hidden GitHub Gists. 1-tap GitHub OAuth Device Flow handles authorization directly through the user's web browser.
- **Embedded PRoot Alpine Linux & Termux Terminal Engine** — Integrates Termux's `TerminalView` and VT100 terminal emulator for complete ANSI escape code, truecolor, and alternate screen handling (`vim`, `nano`, interactive TUIs). Runs inside an Alpine Linux rootfs via PRoot, giving users access to `apk` packages (`git`, `python3`, `gcc`, `make`, `vim`) while strictly respecting Android API 29+ `W^X` SELinux restrictions.
- **Autonomous Unbounded Agent Loop with Safety-Bounded Reading** — Removed artificial tool iteration limits so the agent can autonomously complete multi-step refactoring, builds, and verification workflows. Reading files is protected by an 800-line slice limit, 45 KB byte limit, and 100 MB max file size to prevent LLM context blowups while providing explicit pagination instructions.
- **Dual-Surface Developer Interface** — Headless streaming execution for AI agent tool calls + dedicated interactive visual terminal screen (`feature:terminal`) for developer control with programmer keys (`Esc`, `Tab`, `Ctrl-C`, `|`, `git`, `clear`, history up/down).
- **Terminal Display Scaling & Adjustable Text Size** — Full control over terminal font size (9 sp to 28 sp) with live preview in Settings, dynamic density-aware rendering (`scaledDensity` SP-to-pixel mapping), two-finger pinch-to-zoom in the terminal canvas, and a quick `FormatSize` action dialog in the terminal top bar.
- **Concurrent Multi-Session Terminal (Tabbed PTY Architecture)** — Run multiple independent shell sessions concurrently in tabbed views with live process status dots, custom/OSC-synced titles, quick tab switching, close management, and non-blocking background process execution.
- **Multi-Provider Architecture & Live Model Discovery** — Store and switch between multiple AI providers independently with encrypted API keys and dynamic `/models` querying.
- **Full Device File Access (`MANAGE_EXTERNAL_STORAGE`)** — Direct `java.io.File` access without Storage Access Framework bottlenecks, enabling seamless path handling for AI agents, Git CLI, and terminal execution.
- **Pending Changes Safety Model** — Edits are never applied automatically; every change must be explicitly reviewed and approved by the user.

## Build & Run

### Prerequisites

| Tool | Version | Install |
|---|---|---|
| JDK | 21 (Temurin) | `sdk install java 21.0.12-tem` |
| Android SDK | API 36+ | Android Studio SDK Manager |
| Gradle | 9.7+ | `sdk install gradle 9.7.1` or use `./gradlew` |

### Build

You can use the provided `./build.sh` helper script (which automatically detects JDK 21 and your Android SDK) or `./gradlew`:

```bash
# Using build helper script:
./build.sh                  # Build debug APK
./build.sh --release        # Build release APK
./build.sh --bundle         # Build Android App Bundle (AAB)
./build.sh --clean          # Clean and build

# Or directly via gradlew:
export JAVA_HOME=~/.local/opt/jdk-21   # or your JDK 21 path
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests

Run unit tests using `./test.sh` (with automatic test result summaries and HTML report links) or `./gradlew`:

```bash
# Using test helper script:
./test.sh                             # Run all 129 unit tests
./test.sh -m core:agent               # Run tests for specific module
./test.sh -c ToolExecutorTest         # Run a specific test class
./test.sh --fail-fast --report        # Stop on first failure and show reports

# Or directly via gradlew:
./gradlew test
```

### Install on Device

Install CodeAgent via ADB (USB / Wi-Fi debugging) or by serving the APK locally and scanning a QR code directly from your terminal:

```bash
# Interactive selection menu (ADB or QR code):
./install.sh

# Install directly via ADB to connected device:
./install.sh --adb
./install.sh --adb --launch      # Install and launch app immediately

# Start local Wi-Fi server with terminal QR code (scan with phone camera):
./install.sh --qr
./install.sh --qr --port 8080    # Custom port
```

## Configuration

1. Open the app → **Settings** tab.
2. Add or select an AI provider (e.g., OpenAI, OpenRouter, Anthropic, DeepSeek, local Ollama).
3. Enter your API key (stored encrypted at rest).
4. Tap **Fetch Models** to automatically discover available models.
5. *(Optional)* Set up **Cloud Sync** using 1-tap GitHub browser sign-in and an encryption passphrase to sync your configurations across devices.
6. Customize **Terminal Display & Font Size** (9 sp to 28 sp) using the interactive slider, steppers, and preset chips with live terminal preview.

## Tech Stack

- **Kotlin 2.4.20** + **Jetpack Compose** (BOM 2026.09.00)
- **AGP 9.4.0** (built-in Kotlin — no `kotlin-android` plugin)
- **Hilt 2.60.1** for dependency injection
- **Termux Terminal Engine (`termux-view`, `termux-emulator`)** for VT100 / PTY terminal emulation
- **PRoot + Alpine Linux rootfs** for userland Linux utilities (`apk`, `python3`, `gcc`, `make`, `git`)
- **Room 2.8.5** for local persistence
- **OkHttp 5.5.0** for SSE streaming and GitHub REST APIs
- **AES-256-GCM + PBKDF2 (SHA-256, 100k iterations)** for Zero-Knowledge cloud sync
- **java-diff-utils 4.17** for diff generation
- **multiplatform-markdown-renderer 0.45.0** for Markdown rendering
- **EncryptedSharedPreferences** for secure local storage

## Milestones

1. ✅ **Scaffold** — Gradle + multi-module architecture + navigation + Material3 theme
2. ✅ **Project Access** — Full device storage access (`MANAGE_EXTERNAL_STORAGE`), directory browser, syntax-highlighted code viewer
3. ✅ **AI Chat** — Settings, OpenAI-compatible streaming chat UI, token delta events
4. ✅ **Context System** — Agent context builder, file attachments, system prompts
5. ✅ **Agent Tools** — Tool registry, executor, propose edit, file summary, search code
6. ✅ **Diff Approval** — Pending changes review, colored diff viewer, individual/bulk apply and reject
7. ✅ **Persistence** — Room database v2, session restore, cancel generation, Markdown rendering
8. ✅ **Native Execution Engine** — POSIX process execution (`PosixTerminalExecutor`), `CliGitOperations`, Option C native binary packaging
9. ✅ **Full Linux Terminal & Alpine Container** — Termux `TerminalView`, ANSI truecolor PTY, PRoot Alpine Linux with `apk`, vim, gcc, python3
10. ✅ **Zero-Knowledge Cloud Sync & OAuth** — E2EE AES-256-GCM vault sync via GitHub Gist, 1-tap browser OAuth device flow
11. ✅ **Autonomous Agent & Safety Bounds** — Unbounded iterative tool execution, 800-line / 45 KB pagination guards, 100 MB file limit

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0).
