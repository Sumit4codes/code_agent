package com.koda.dev.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------------------
// Primitive Brand Palette: Koda Obsidian Design System
// -------------------------------------------------------------------------

// Canvas & Obsidian Surfaces
val VoidObsidian = Color(0xFF0B0F19)           // Deep obsidian space background
val ObsidianSurface = Color(0xFF111726)        // Elevated base surface
val ObsidianSurfaceElevated = Color(0xFF161E31)// Sheets, popups, menus
val ObsidianSurfaceVariant = Color(0xFF1D263B) // Cards, code blocks, input bar
val ObsidianBorder = Color(0xFF26334D)          // Crisp 1dp border
val ObsidianBorderSubtle = Color(0xFF1E283C)    // Hairline border
val ObsidianBorderFocus = Color(0xFF00F0FF)     // Focused element border

// Brand Primary (Cyber Cyan)
val CyberCyan = Color(0xFF00F0FF)               // Core brand accent, glowing cursor
val CyberCyanDim = Color(0xFF00C8D6)            // Pressed / dimmed cyan
val CyberCyanMuted = Color(0xFF0891B2)          // Low-emphasis cyan
val CyberCyanContainer = Color(0xFF052E38)      // Chip / container fill
val OnCyberCyanContainer = Color(0xFF80F8FF)    // Container text

// Brand Secondary (Electric Violet)
val ElectricViolet = Color(0xFF7000FF)          // Agent / AI core
val ElectricVioletLight = Color(0xFF8B5CF6)     // Accessible text on dark
val ElectricVioletContainer = Color(0xFF260D4D) // AI bubble background
val OnElectricVioletContainer = Color(0xFFDDD6FE)

// Brand Tertiary (Hyper Indigo)
val HyperIndigo = Color(0xFF6366F1)             // Transition accent
val HyperIndigoContainer = Color(0xFF1E1B4B)
val OnHyperIndigoContainer = Color(0xFFC7D2FE)

// Functional / Developer Status
val TerminalEmerald = Color(0xFF10B981)         // Bash prompt, tests pass, git clean
val TerminalEmeraldContainer = Color(0xFF064E3B)
val TerminalError = Color(0xFFEF4444)           // Stderr, test fail, syntax error
val TerminalErrorContainer = Color(0xFF450A0A)
val TerminalWarning = Color(0xFFF59E0B)         // Pending changes, warnings
val TerminalWarningContainer = Color(0xFF451A03)
val TerminalInfo = Color(0xFF38BDF8)            // Tool calls, logs

// Diff Visual Tokens
val DiffAddedGreen = Color(0xFF10B981)
val DiffAddedBg = Color(0x2610B981)             // 15% opacity green
val DiffRemovedRed = Color(0xFFEF4444)
val DiffRemovedBg = Color(0x26EF4444)           // 15% opacity red
val DiffModifiedYellow = Color(0xFFF59E0B)
val DiffModifiedBg = Color(0x26F59E0B)          // 15% opacity yellow

// High-Contrast Text Tokens
val ContentPrimary = Color(0xFFF8FAFC)          // Slate 50
val ContentSecondary = Color(0xFF94A3B8)        // Slate 400
val ContentTertiary = Color(0xFF64748B)         // Slate 500
val ContentDisabled = Color(0xFF475569)         // Slate 600

// -------------------------------------------------------------------------
// Semantic Developer Colors Contract
// -------------------------------------------------------------------------
@Immutable
data class KodaSemanticColors(
    val terminalBackground: Color,
    val terminalPrompt: Color,
    val terminalStderr: Color,
    val terminalCursor: Color,
    val editorBackground: Color,
    val editorGutter: Color,
    val editorGutterText: Color,
    val editorCurrentLine: Color,
    val diffAdded: Color,
    val diffAddedBackground: Color,
    val diffRemoved: Color,
    val diffRemovedBackground: Color,
    val diffModified: Color,
    val diffModifiedBackground: Color,
    val codeBlockBackground: Color,
    val codeInlineBackground: Color,
    val agentBubbleBackground: Color,
    val userBubbleBackground: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val glowColor: Color
)

val KodaDarkSemanticColors = KodaSemanticColors(
    terminalBackground = Color(0xFF080C14),
    terminalPrompt = TerminalEmerald,
    terminalStderr = TerminalError,
    terminalCursor = CyberCyan,
    editorBackground = Color(0xFF0B0F19),
    editorGutter = Color(0xFF0F1523),
    editorGutterText = ContentTertiary,
    editorCurrentLine = Color(0xFF161E31),
    diffAdded = DiffAddedGreen,
    diffAddedBackground = DiffAddedBg,
    diffRemoved = DiffRemovedRed,
    diffRemovedBackground = DiffRemovedBg,
    diffModified = DiffModifiedYellow,
    diffModifiedBackground = DiffModifiedBg,
    codeBlockBackground = Color(0xFF111726),
    codeInlineBackground = Color(0xFF1D263B),
    agentBubbleBackground = Color(0xFF161B2E),
    userBubbleBackground = Color(0xFF1E283E),
    borderSubtle = ObsidianBorderSubtle,
    borderStrong = ObsidianBorder,
    glowColor = CyberCyan
)

val KodaLightSemanticColors = KodaSemanticColors(
    terminalBackground = Color(0xFF1E2430),
    terminalPrompt = TerminalEmerald,
    terminalStderr = TerminalError,
    terminalCursor = CyberCyan,
    editorBackground = Color(0xFFF8FAFC),
    editorGutter = Color(0xFFF1F5F9),
    editorGutterText = Color(0xFF94A3B8),
    editorCurrentLine = Color(0xFFE2E8F0),
    diffAdded = Color(0xFF059669),
    diffAddedBackground = Color(0x1F10B981),
    diffRemoved = Color(0xFFDC2626),
    diffRemovedBackground = Color(0x1FEF4444),
    diffModified = Color(0xFFD97706),
    diffModifiedBackground = Color(0x1FF59E0B),
    codeBlockBackground = Color(0xFFF1F5F9),
    codeInlineBackground = Color(0xFFE2E8F0),
    agentBubbleBackground = Color(0xFFF1F5F9),
    userBubbleBackground = Color(0xFFE0E7FF),
    borderSubtle = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    glowColor = CyberCyan
)

val LocalKodaSemanticColors = staticCompositionLocalOf {
    KodaDarkSemanticColors
}
