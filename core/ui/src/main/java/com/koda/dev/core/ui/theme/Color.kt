package com.koda.dev.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------------------
// Primitive Brand Palette: Koda Neutral Charcoal & Developer Blue System
// -------------------------------------------------------------------------

// Canvas & Surfaces: Developer Charcoal
val VoidObsidian = Color(0xFF0D1117)           // Deep neutral charcoal background
val ObsidianSurface = Color(0xFF161B22)        // Base surface
val ObsidianSurfaceElevated = Color(0xFF1C2128)// Sheets, popups, menus
val ObsidianSurfaceVariant = Color(0xFF21262D) // Cards, code blocks, input capsule
val ObsidianBorder = Color(0xFF30363D)          // Crisp 1dp border
val ObsidianBorderSubtle = Color(0xFF21262D)    // Hairline border
val ObsidianBorderFocus = Color(0xFF1F6FEB)     // Focused element border

// Brand Primary (Developer Blue)
val CyberCyan = Color(0xFF2F81F7)               // Modern developer blue accent
val CyberCyanDim = Color(0xFF1F6FEB)            // Slightly deeper blue
val CyberCyanMuted = Color(0xFF16325C)          // Low-emphasis blue
val CyberCyanContainer = Color(0xFF11233F)      // Chip / container fill
val OnCyberCyanContainer = Color(0xFF79C0FF)    // Container text

// Brand Secondary (Muted Indigo / Slate)
val ElectricViolet = Color(0xFF58A6FF)          // Accent highlight
val ElectricVioletLight = Color(0xFF79C0FF)     // Accessible text on dark
val ElectricVioletContainer = Color(0xFF162032) // Container fill
val OnElectricVioletContainer = Color(0xFFD0E2FF)

// Brand Tertiary (Accent Indigo)
val HyperIndigo = Color(0xFF6E7681)             // Neutral transition accent
val HyperIndigoContainer = Color(0xFF21262D)
val OnHyperIndigoContainer = Color(0xFFC9D1D9)

// Functional / Developer Status
val TerminalEmerald = Color(0xFF3FB950)         // Clean green, tests pass, git clean
val TerminalEmeraldContainer = Color(0xFF12341A)
val TerminalError = Color(0xFFF85149)           // Stderr, test fail, syntax error
val TerminalErrorContainer = Color(0xFF381313)
val TerminalWarning = Color(0xFFD29922)         // Pending changes, warnings
val TerminalWarningContainer = Color(0xFF382607)
val TerminalInfo = Color(0xFF58A6FF)            // Tool calls, logs

// Diff Visual Tokens (GitHub style)
val DiffAddedGreen = Color(0xFF3FB950)
val DiffAddedBg = Color(0x263FB950)             // 15% opacity green
val DiffRemovedRed = Color(0xFFF85149)
val DiffRemovedBg = Color(0x26F85149)           // 15% opacity red
val DiffModifiedYellow = Color(0xFFD29922)
val DiffModifiedBg = Color(0x26D29922)          // 15% opacity yellow

// High-Contrast Text Tokens
val ContentPrimary = Color(0xFFF0F6FC)          // Off-white primary text
val ContentSecondary = Color(0xFF8B949E)        // Neutral secondary text
val ContentTertiary = Color(0xFF6E7681)         // Neutral muted text
val ContentDisabled = Color(0xFF484F58)         // Disabled text

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
    terminalBackground = Color(0xFF141618),
    terminalPrompt = TerminalEmerald,
    terminalStderr = TerminalError,
    terminalCursor = CyberCyan,
    editorBackground = Color(0xFF0D1117),
    editorGutter = Color(0xFF161B22),
    editorGutterText = ContentTertiary,
    editorCurrentLine = Color(0xFF21262D),
    diffAdded = DiffAddedGreen,
    diffAddedBackground = DiffAddedBg,
    diffRemoved = DiffRemovedRed,
    diffRemovedBackground = DiffRemovedBg,
    diffModified = DiffModifiedYellow,
    diffModifiedBackground = DiffModifiedBg,
    codeBlockBackground = Color(0xFF161B22),
    codeInlineBackground = Color(0xFF21262D),
    agentBubbleBackground = Color(0xFF161B22),
    userBubbleBackground = Color(0xFF21262D),
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
