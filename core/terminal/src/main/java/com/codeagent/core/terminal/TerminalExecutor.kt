package com.codeagent.core.terminal

import android.net.Uri
import com.codeagent.core.files.ProjectFileSystem
import java.io.File

interface TerminalExecutor {
    fun bind(fileSystem: ProjectFileSystem, rootUri: Uri, localWorkDir: File? = null)
    fun unbind()
    suspend fun execute(command: String, onOutput: ((String) -> Unit)? = null): TerminalResult
    val isEnabled: Boolean
}

sealed class TerminalResult {
    data class Success(val output: String, val exitCode: Int = 0) : TerminalResult()
    data class Error(val message: String, val exitCode: Int? = null) : TerminalResult()
    data object Disabled : TerminalResult()
}
