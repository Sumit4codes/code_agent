package com.codeagent.core.terminal

interface TerminalExecutor {
    suspend fun execute(command: String): TerminalResult
    val isEnabled: Boolean
}

sealed class TerminalResult {
    data class Success(val output: String, val exitCode: Int) : TerminalResult()
    data class Error(val message: String, val exitCode: Int? = null) : TerminalResult()
    data object Disabled : TerminalResult()
}
