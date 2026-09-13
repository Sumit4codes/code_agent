# CodeAgent — Android AI Coding Agent

A clean-room, Android-native AI coding agent inspired by [OpenCode](https://github.com/opencode-ai/opencode). Open code projects on your device, chat with an AI agent that understands your repository, review proposed changes as diffs, and apply them safely.

## Features

- **Project Explorer** — Open any local folder via Android's SAF (Storage Access Framework), browse the file tree, and view files with syntax highlighting
- **AI Chat** — Converse with an OpenAI-compatible LLM (GPT-4o, Claude, local models) about your code
- **Tool-Calling Agent** — The AI can read files, search code, list directories, and propose edits through structured tool calls
- **Diff Review** — Every proposed change appears as a colored diff; approve or reject each one individually or in bulk
- **Session Persistence** — Chat history and pending changes survive app restarts (Room DB)
- **Cancel Generation** — Stop a streaming response at any time
- **Markdown Rendering** — AI responses render as rich Markdown (code blocks, lists, links, bold/italic)
- **Secure Key Storage** — API keys are encrypted at rest via EncryptedSharedPreferences

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                      app                            │
│  MainActivity · AppNavHost · AppModule (DI)         │
├──────────┬──────────┬──────────┬────────────────────┤
│ feature/ │ feature/ │ feature/ │ feature/           │
│ projects │  chat    │  editor  │  settings          │
├──────────┴──────────┴──────────┴────────────────────┤
│  core/agent  core/ai  core/files  core/data  core/ui│
│  AgentOrch.  AiProv.  SAF FS     Room DB    Theme   │
│  ToolExec.   SSE      Diff       SecureKSt  Markdown│
│  PendingChg  OpenAI   PathSafe   SettingsRepo       │
├─────────────────────────────────────────────────────┤
│                    core/model                        │
│        Project · Session · Message · Tools          │
└─────────────────────────────────────────────────────┘
```

### Module Overview

| Module | Responsibility |
|---|---|
| `app` | Navigation, Hilt entry point, `AiProvider` binding |
| `feature/projects` | SAF folder picker, project list |
| `feature/chat` | AI chat with tool-call chips, pending changes banner |
| `feature/editor` | File tree + code viewer with line numbers |
| `feature/settings` | Provider configuration form |
| `core/model` | Domain models (Project, Message, PendingChange, ToolSpec) |
| `core/ai` | `AiProvider` interface, OpenAI-compatible SSE implementation |
| `core/agent` | `AgentOrchestrator` loop, `ToolExecutor`, `PendingChangeManager` |
| `core/files` | SAF filesystem, path traversal protection, gitignore, diff engine |
| `core/data` | Room database, encrypted key store, settings repository |
| `core/ui` | Material3 theme, Markdown rendering composable |

### Key Design Decisions

- **SAF-only file access** — No `READ_EXTERNAL_STORAGE`; all file I/O goes through `ContentResolver` + `takePersistableUriPermission`
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

```bash
export JAVA_HOME=~/.local/opt/jdk-21   # or your JDK 21 path
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests

```bash
./gradlew test
```

### Install on Device

```bash
# USB-connected device with USB debugging enabled
./gradlew installDebug
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
2. ✅ **Project Access** — SAF picker, file tree, code viewer
3. ✅ **AI Chat** — Settings, OpenAI-compatible provider, streaming chat UI
4. ✅ **Context System** — Agent context builder, file attachment
5. ✅ **Agent Tools** — Tool registry (7 tools), executor, propose edit
6. ✅ **Diff Approval** — Pending changes, diff viewer, apply/reject
7. ✅ **Persistence** — Room v2, session restore, cancel generation, Markdown

## License

Apache-2.0
