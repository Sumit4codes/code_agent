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

