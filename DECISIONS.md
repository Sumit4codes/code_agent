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
