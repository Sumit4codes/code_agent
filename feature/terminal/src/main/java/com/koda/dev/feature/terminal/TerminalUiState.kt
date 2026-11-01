package com.koda.dev.feature.terminal

import com.koda.dev.core.terminal.ShellEnvironmentInfo
import java.util.UUID

enum class TerminalEntryType {
    COMMAND,
    STDOUT,
    STDERR,
    SYSTEM
}

data class TerminalEntry(
    val text: String,
    val type: TerminalEntryType,
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
)

data class TerminalSessionTab(
    val id: String,
    val title: String,
    val isRunning: Boolean = true
)

data class TerminalUiState(
    val projectName: String = "Terminal",
    val projectId: String? = null,
    val workingDirectory: String = "",
    val entries: List<TerminalEntry> = listOf(
        TerminalEntry(
            text = "Koda Dev Terminal Engine [Ubuntu 24.04 LTS (Default)]\nType commands below or use the quick key bar.",
            type = TerminalEntryType.SYSTEM
        )
    ),
    val commandInput: String = "",
    val isRunning: Boolean = false,
    val commandHistory: List<String> = emptyList(),
    val historyPointer: Int = -1,
    val envInfo: ShellEnvironmentInfo? = null,
    val isUbuntuReady: Boolean = false,
    val isAlpineReady: Boolean = false,
    val isBootstrapping: Boolean = false,
    val bootstrapProgress: Float = -1f,
    val bootstrapMessage: String = "",
    val fontSizeSp: Int = 14,
    val sessions: List<TerminalSessionTab> = emptyList(),
    val activeSessionId: String? = null
)
