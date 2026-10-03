# Decision Log

## D1: AGP 9.4 with Built-in Kotlin
**Decision:** Use AGP 9.4.0's built-in Kotlin support. Add Kotlin 2.4.20 to `buildscript { dependencies {} }` as a classpath entry only, not as an applied plugin.

**Rationale:** AGP 9.0+ ships with Kotlin compiler built-in. Applying `org.jetbrains.kotlin.android` separately causes build failures. To use Kotlin 2.4.20, we add the kotlin-gradle-plugin to the buildscript classpath without applying it.

**Trade-off:** Less familiar DSL than AGP 8.x, but aligns with Google's direction and AGP 10.0 will make this mandatory.

## D2: Hand-rolled Code Viewer Instead of sora-editor
**Decision:** Implement a basic Compose-based code viewer with regex syntax highlighting for MVP, rather than using sora-editor 0.24.6.

**Rationale:** sora-editor is LGPL-2.1, which creates compliance burden for an Apache-2.0 app distributed as APK. For MVP read-only mode, a simple Compose `Text` with line numbers and basic highlighting is sufficient.

**Trade-off:** Less feature-rich than sora-editor, but avoids license complications. Can integrate sora-editor later if LGPL compliance is acceptable.

## D3: multiplatform-markdown-renderer 0.45.0
**Decision:** Use `com.mikepenz:multiplatform-markdown-renderer:0.45.0` for Markdown rendering.

**Rationale:** Actively maintained (Aug 2026), Apache-2.0, Compose-native. Alternatives: halilibo/compose-richtext (still alpha), Markwon (View-based, old).

## D4: No build-logic Convention Plugins (Initially)
**Decision:** Use per-module `build.gradle.kts` files with a shared version catalog, not `build-logic` convention plugins.

**Rationale:** With AGP 9.4's new variant API and built-in Kotlin, convention plugins add complexity. 14 explicit build files are transparent and easier to debug. Can refactor to `build-logic` once stable.

## D5: JDK 21 (Temurin)
**Decision:** Use JDK 21 for running Gradle, even though AGP 9.4 minimum is JDK 17.

**Rationale:** Gradle 9.7.1 supports JDK 21. Temurin 21 is the current LTS. JDK 21 provides better performance and is forward-compatible.

## D6: Package Name `com.codeagent.app`
**Decision:** Use `com.codeagent.app` as applicationId and package name.

**Rationale:** Neutral name, avoids OpenCode branding per requirements. Can be changed later by updating `namespace` and `applicationId` in `app/build.gradle.kts`.

## D7: Full Device File Access (MANAGE_EXTERNAL_STORAGE) Instead of SAF
**Decision:** Transition from Android's Storage Access Framework (SAF) to full device storage access (`MANAGE_EXTERNAL_STORAGE`).

**Rationale:** SAF document URIs (`content://...`) require complex cursor querying through `DocumentsContract` and cannot be operated on by standard POSIX-oriented tools like JGit and ProcessBuilder. By requesting `MANAGE_EXTERNAL_STORAGE` and utilizing direct `java.io.File` access via `FileProjectFileSystem`, file path handling for AI agents is vastly simplified. Agents can specify standard relative paths or absolute file paths without SAF lookup bottlenecks.

**Trade-off:** Requires users to grant All Files Access permission in system settings (Android 11+ / API 30+), which is standard for developer, file manager, and terminal tools on Android.

## D8: Multi-Provider Architecture with Dynamic Live Model Fetching
**Decision:** Transition the settings architecture from a single-provider form into a modular multi-provider configuration system. Each provider stores its own endpoint, protocol, and separate encrypted API key. Models are fetched dynamically via an automated `ModelFetcher` service querying provider `/models` endpoints.

**Rationale:** Users work across multiple LLM providers (e.g., OpenAI for flagship reasoning, DeepSeek for fast coding, OpenRouter for Claude/Llama gateways, Ollama for local privacy, and custom private proxies). A monolithic single-provider screen was cluttered and forced users to manually re-enter API keys and URLs whenever switching. Dynamic model fetching eliminates the need for users to memorize or manually type model identifiers.

**Trade-off:** Requires network access to query `/models` endpoints, mitigated with pre-configured fallback models for all popular providers so users can configure offline or behind firewalls.

## D9: Self-Contained POSIX Process Execution Engine & Interactive Visual Terminal (Option C)
**Decision:** Completely eliminate the mock in-memory `VirtualShell` and Java `JGit` in favor of a real native POSIX process execution engine (`PosixTerminalExecutor` and `CliGitOperations`), packaged as a self-contained APK architecture (Option C) with a dual-surface developer interface.

**Rationale:**
1. **Full Agentic Control:** Toy virtual shells and pure-Java git abstractions cannot run real development workflows (e.g. bash pipelines, find/grep filters, redirects, real git hooks, diffs, subshells, and builds). Real POSIX process execution via `/system/bin/sh` gives autonomous coding agents genuine environment control.
2. **Android W^X SELinux Compliance:** Under Android API 29+, executing binaries directly out of writable app storage (`/data/data/.../files`) is forbidden (`W^X` SELinux restriction). Option C addresses this cleanly by resolving binaries from `context.applicationInfo.nativeLibraryDir` (where `lib*.so` binaries are extracted with executable `r-xp` permissions) and system directories, with shell preamble function routing.
3. **Dual-Surface Developer Experience:** The architecture serves two distinct requirements:
   - *Headless Streaming Execution:* The AI agent invokes commands programmatically with output streaming line-by-line via real-time callbacks.
   - *Interactive Developer Terminal (`feature:terminal`):* A dedicated visual terminal screen with monospace dark console, persistent working directory tracking, command history buffer (`↑`/`↓`), and a mobile programmer keyboard accessory bar (`Tab`, `Ctrl-C`, `|`, `&&`, `/`, `git`, `clear`).

**Trade-off:** The execution environment operates within the Android app's sandbox user ID (UID) and available system/bundled CLI utilities.

## D10: Relicense to GNU General Public License v3.0 (GPL-3.0)
**Decision:** Relicense the CodeAgent project from Apache-2.0 to GNU General Public License v3.0 (GPL-3.0).

**Rationale:**
1. **Enables Termux Terminal Engine (`terminal-view` & `terminal-emulator`):** Termux's native terminal emulation ecosystem is licensed under GPL-3.0. Moving CodeAgent to GPL-3.0 allows direct integration of Termux's battle-tested Xterm/VT100 screen buffer, alternate screen handling (`\e[?1049h`), 24-bit truecolor, and JNI pseudo-terminal (PTY) lifecycle needed for full-screen CLI tools like `vim`, `nano`, `htop`, and interactive shells.
2. **Harmonizes LGPL-2.1 Components:** Removes license friction with LGPL-2.1 components (such as `sora-editor`, previously noted in D2), enabling future upgrades to full mobile code editing and LSP integration.
3. **Copyleft Protection:** Protects the open-source community by ensuring all forks, modifications, and distributed derivatives of CodeAgent remain free and open source.

**Trade-off:** Strong copyleft enforcement prevents proprietary, closed-source commercial distribution of derivative versions.

## D11: Zero-Knowledge E2EE Cloud Sync via GitHub Gist ($0 Backend Architecture)
**Decision:** Implement cross-device cloud synchronization for API keys, AI provider configurations, and settings by storing an encrypted vault in a secret GitHub Gist, authenticated via the GitHub OAuth Device Authorization Flow.

**Rationale:**
1. **$0 Infrastructure & Privacy:** Avoids standing up, maintaining, or paying for centralized cloud databases or backend servers. The user owns their data entirely inside their personal GitHub account.
2. **End-to-End Cryptography (`VaultCrypto`):** Encrypted with client-side AES-256-GCM using keys derived via PBKDF2 (SHA-256, 100,000 iterations, 128-bit random salt, 96-bit random IV) from a user-supplied encryption passphrase. Neither GitHub nor any intermediary can inspect or tamper with stored API keys.
3. **Frictionless Browser Sign-In:** 1-tap browser OAuth Device Flow allows authorization directly through the user's default mobile web browser, eliminating the need to manually generate or paste Personal Access Tokens.

**Trade-off:** If the user loses their encryption passphrase, the encrypted vault cannot be recovered by anyone.

## D12: Termux TerminalView Integration with PRoot Alpine Linux Container
**Decision:** Embed Termux's native `TerminalView` canvas and VT100 emulator backed by an Alpine Linux userland rootfs running inside PRoot.

**Rationale:**
1. **Full Linux Toolchain:** Mobile developers require genuine toolchains (`apk add git python3 gcc make vim nodejs`) and full interactive visual terminal capabilities (curses/alternate screen buffer `\e[?1049h` for `vim`/`nano`, PTY signals like `Ctrl-C`, soft keyboard input connection).
2. **Android W^X SELinux Compliance:** PRoot executes in userland within the app's sandboxed data directory, providing Linux filesystem emulation (`/`, `/usr/bin`, `/bin`) while mounting host storage (`/sdcard`, `/storage`) without requiring root access or violating Android API 29+ `W^X` SELinux restrictions.
3. **Resilience & Fallback:** Session creation runs strictly on the UI thread (`Dispatchers.Main`) to respect `Looper` and Android View thread requirements, with an automatic fallback to the native Android Toybox shell (`/system/bin/sh`) if PRoot is uninitialized.

**Trade-off:** PRoot introduces slight ptrace emulation overhead compared to bare-metal binaries, which is well worth the benefit of a full package manager (`apk`).

## D13: Agent Read-File Safety Bounds (800 Lines / 45 KB / 100 MB) & Unbounded Autonomous Loop
**Decision:** Remove the artificial tool iteration cap (`MAX_TOOL_ITERATIONS = 25`) from `AgentOrchestrator`, while enforcing strict safety bounds on file inspection in `ToolExecutor` (800 lines maximum per read, 45 KB byte limit, 100 MB maximum file size).

**Rationale:**
1. **Autonomous Task Completion:** Complex software engineering tasks (navigating unfamiliar codebases, refactoring multiple files, running builds, analyzing compiler errors, and iterating on fixes) regularly exceed 25 tool turns. Capping iterations leaves tasks half-finished. Removing the cap lets the agent run until task completion (`toolCalls.isEmpty()`) or explicit user cancellation.
2. **Context Window & Memory Protection:** Unbounded file reads risk exhausting LLM context limits (e.g. 100k tokens) or triggering Android JVM `OutOfMemoryError` on large source files or minified bundles.
3. **Antigravity-Inspired Pagination:** Enforcing an 800-line slice cap with explicit next-page guidance (`start_line=801`) and a 45 KB byte truncation guard ensures predictable token consumption while giving the model clear instructions on how to paginate through larger files.

**Trade-off:** The agent must make multiple paginated calls with `start_line` and `end_line` when inspecting very large files.

## D14: Terminal Display Scaling & User-Configurable Text Size (SP-to-Pixel Density Mapping, Real-Time Preview, and Gesture Zoom)
**Decision:** Store terminal font size preferences in `TerminalPreferencesRepository` (defaulting to 14 sp, clamped between 9 sp and 28 sp), translate SP values dynamically to Android device display density (`(fontSizeSp * scaledDensity).roundToInt()`), provide a dedicated settings panel with live terminal preview in `SettingsScreen`, and enable two-finger pinch-to-zoom and an in-terminal font size dialog in `TerminalScreen`.

**Rationale:**
1. **Raw Pixel Fix:** The underlying Termux `TerminalView.setTextSize(int)` expects raw pixels (`px`), rather than scale-independent points (`sp`). Passing unscaled integer literals (e.g. `14`) resulted in microscopic font rendering (~5 sp on modern 440 dpi screens). Multiplying the configured `fontSizeSp` by `displayMetrics.scaledDensity` ensures standard, crisp readability across all display densities.
2. **Settings Screen Discovery:** Developers need a dedicated setting to customize terminal text scaling according to their visual preference. `TerminalPreferencesCard` provides an interactive slider, step increment buttons, quick preset chips (`Compact (11 sp)`, `Default (14 sp)`, `Medium (16 sp)`, `Large (18 sp)`, `Huge (22 sp)`), and a real-time dark terminal preview box showing immediate text changes.
3. **In-Session Flexibility:** In addition to the Settings screen, developers can dynamically scale the terminal canvas during active coding sessions via two-finger pinch-to-zoom gestures (detected by `CodeAgentTerminalViewClient.onScale`) or via the top-bar `FormatSize` action dialog.
4. **Clean Decoupling:** `TerminalPreferencesRepository` encapsulates persistence via Android `SharedPreferences`, exposing both reactive `Flow<Int>` and synchronous getters, with graceful in-memory fallbacks during unit testing.

**Trade-off:** Very large font sizes (>24 sp) reduce the number of columns and lines visible on compact smartphone screens in portrait orientation.

## D15: Concurrent Multi-Session Terminal Architecture (Tabs, Independent PTYs, Dynamic View Attachment, and Background Process Isolation)
**Decision:** Support multiple concurrent terminal sessions (tabs) managed within `TerminalViewModel` and displayed via `TerminalSessionTabBar` in `TerminalScreen`. Each session maintains its own shell process, pseudoterminal (PTY), emulator buffer, and `CodeAgentTerminalSessionClient`. The single `TerminalView` canvas dynamically reattaches to whichever session is active via `TerminalView.attachSession(session)`.

**Rationale:**
1. **Developer Multitasking:** Developers on mobile frequently run multiple tasks simultaneously (e.g. running a build or local server in one tab while editing files in Vim or querying Git in another tab). Single-session terminals force users to exit tools or open external apps.
2. **Dynamic Canvas Reattachment:** Termux's `TerminalView.attachSession(newSession)` detaches from the current session and recalculates emulator dimensions, scrollback, and cursor for the incoming session without destroying the underlying PTY process. Sessions running in the background continue executing uninterrupted.
3. **Selective Screen Update Dispatching:** In `CodeAgentTerminalSessionClient.onTextChanged` and `onColorsChanged`, updates are only forwarded to `terminalView.onScreenUpdated()` if the calling session is the active attached session (`terminalView.currentSession == changedSession`). This isolates canvas redrawing and saves CPU cycles when background sessions emit large volumes of output.
4. **Automatic Title Sync & Clean Lifecycle:** Sessions automatically update tab labels when child processes emit OSC title escapes (e.g. `vim`, `bash`, `python3`). Closing a tab safely terminates its PTY process, and closing the only tab automatically provisions a fresh session so the user is never stranded. On ViewModel teardown (`onCleared()`), all running sessions are cleaned up to prevent orphan processes or PTY descriptor leaks.

**Trade-off:** Multiple concurrent shell processes consume RAM and CPU resources on low-end Android hardware.


