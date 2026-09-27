# CodeAgent — Android AI Coding Agent

A clean-room, Android-native AI coding agent inspired by [OpenCode](https://github.com/opencode-ai/opencode). Open code projects on your device, chat with an AI agent that understands your repository, review proposed changes as diffs, and apply them safely.

## Features

- **Project Explorer** — Open any local folder on your device, browse the file tree, and view files with syntax highlighting
- **Interactive Terminal** — Built-in visual monospace console with command history, quick accessory keys (Tab, Ctrl-C, pipes, flags), and working directory persistence
- **Native POSIX Execution** — Real process execution engine (`sh -c`, pipelines, file redirection) and native Git CLI operations replacing mock shells
- **AI Chat** — Converse with an OpenAI-compatible LLM (GPT-4o, Claude, local models) about your code
- **Tool-Calling Agent** — The AI can read files, search code, list directories, run terminal commands, and propose edits through structured tool calls
- **Diff Review** — Every proposed change appears as a colored diff; approve or reject each one individually or in bulk
- **Session Persistence** — Chat history and pending changes survive app restarts (Room DB)
- **Cancel Generation** — Stop a streaming response or running terminal process at any time
- **Markdown Rendering** — AI responses render as rich Markdown (code blocks, lists, links, bold/italic)
- **Secure Key Storage** — API keys are encrypted at rest via EncryptedSharedPreferences

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
│ AgentOrch.  PosixExecutor  CliGitOps  AiProv.   File FS          │
│ ToolExec.   Preamble/W^X   Git CLI    SSE/Fetch PathSafe / Diff  │
├───────────────────────────────────────┬──────────────────────────┤
│              core/data                │         core/ui          │
│        Room DB · Secure KeyStore      │    Theme · MarkdownView  │
├───────────────────────────────────────┴──────────────────────────┤
│                           core/model                             │
│              Project · Session · Message · Tools                 │
└──────────────────────────────────────────────────────────────────┘
```

### Module Overview

| Module | Responsibility |
|---|---|
| `app` | Navigation, Hilt entry point, `AiProvider` and executor bindings |
| `feature/projects` | Directory browser & project management |
| `feature/chat` | AI chat with tool-call chips, pending changes banner |
| `feature/editor` | File tree + code viewer with line numbers and terminal shortcut |
| `feature/terminal` | Dedicated visual terminal screen, monospace console, accessory keyboard |
| `feature/settings` | Multi-provider management, live dynamic model fetching, and preferences |
| `core/model` | Domain models (Project, Message, PendingChange, ToolSpec, PopularProviders) |
| `core/ai` | `AiProvider` interface, OpenAI-compatible SSE implementation, `ModelFetcher` |
| `core/agent` | `AgentOrchestrator` loop, `ToolExecutor`, `PendingChangeManager` |
| `core/terminal` | Real POSIX process execution (`PosixTerminalExecutor`), native binary management |
| `core/git` | Native CLI Git operations (`CliGitOperations`), repository management |
| `core/files` | Direct file filesystem, path traversal protection, gitignore, diff engine |
| `core/data` | Room database, encrypted key store, settings repository |
| `core/ui` | Material3 theme, Markdown rendering composable |

### Key Design Decisions

- **Self-Contained POSIX Process Execution Engine (Option C)** — Deprecated `VirtualShell` and Java `JGit` in favor of real POSIX process execution (`/system/bin/sh`) and `CliGitOperations`. Complies with Android API 29+ `W^X` SELinux restrictions by routing native executables via `nativeLibraryDir` (`lib*.so`) and shell function preambles.
- **Dual-Surface Terminal Interface** — Headless execution streaming for the AI agent inside chat chips + dedicated interactive visual terminal screen (`feature:terminal`) for manual developer typing with quick-access developer keys (`Tab`, `Ctrl-C`, `|`, `git`, `clear`, history up/down).
- **Multi-Provider Architecture & Live Model Discovery** — Configure and switch between multiple providers (OpenAI, OpenRouter, Anthropic, DeepSeek, Groq, Gemini, Ollama, Custom), each with its own encrypted API key. Models are discovered automatically via dynamic endpoint querying.
- **Full Device File Access (`MANAGE_EXTERNAL_STORAGE`)** — Direct `java.io.File` access without SAF bottlenecks, enabling seamless path handling for AI agents, Git CLI, and terminal execution.
- **Path traversal protection** — `PathSafety.normalize()` rejects `..`, null bytes, and symlink escapes
- **Pending changes model** — Edits are never applied automatically; every change must be explicitly approved by the user
- **Hilt DI** — Single `@HiltAndroidApp` with module-per-layer bindings
- **Room + Flow** — Observable queries use `Flow<>`; one-shot queries use `suspend fun`

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

You can run unit tests using `./test.sh` (with automatic test result summaries and HTML report links) or `./gradlew`:

```bash
# Using test helper script:
./test.sh                             # Run all unit tests
./test.sh -m core:files               # Run tests for specific module
./test.sh -c PathSafetyTest           # Run a specific test class
./test.sh --fail-fast --report        # Stop on first failure and show reports

# Or directly via gradlew:
./gradlew test
```

### Install on Device

```bash
# USB-connected device with USB debugging enabled
./build.sh --install
# Or: ./gradlew installDebug
```

## Configuration

1. Open the app → **Settings** tab
2. Choose a provider (e.g., OpenAI, OpenRouter, local Ollama)
3. Enter your API key (stored encrypted)
4. Set the base URL and model name
5. Mark as default

## Tech Stack

- **Kotlin 2.4.20** + **Jetpack Compose** (BOM 2026.09.00)
- **AGP 9.4.0** (built-in Kotlin — no `kotlin-android` plugin)
- **Hilt 2.60.1** for dependency injection
- **Room 2.8.5** for local persistence
- **OkHttp 5.5.0** for SSE streaming
- **java-diff-utils 4.17** for diff generation
- **multiplatform-markdown-renderer 0.45.0** for Markdown rendering
- **EncryptedSharedPreferences** for API key security

## Milestones

1. ✅ **Scaffold** — Gradle + modules + navigation + theme
2. ✅ **Project Access** — Full device storage access, directory browser, code viewer
3. ✅ **AI Chat** — Settings, OpenAI-compatible provider, streaming chat UI
4. ✅ **Context System** — Agent context builder, file attachment
5. ✅ **Agent Tools** — Tool registry (7 tools), executor, propose edit
6. ✅ **Diff Approval** — Pending changes, diff viewer, apply/reject
7. ✅ **Persistence** — Room v2, session restore, cancel generation, Markdown
8. ✅ **Native Execution Engine** — POSIX process execution (`PosixTerminalExecutor`), `CliGitOperations`, Option C native CLI packaging, and interactive developer terminal (`feature:terminal`)

## License

Apache-2.0
