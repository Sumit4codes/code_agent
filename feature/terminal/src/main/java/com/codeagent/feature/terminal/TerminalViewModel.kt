package com.codeagent.feature.terminal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.terminal.NativeBinaryManager
import com.codeagent.core.terminal.PosixTerminalExecutor
import com.codeagent.core.terminal.TerminalExecutor
import com.codeagent.core.terminal.TerminalResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val fileSystem: ProjectFileSystem,
    val terminalExecutor: TerminalExecutor,
    private val nativeBinaryManager: NativeBinaryManager
) : ViewModel() {

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    constructor(
        projectDao: ProjectDao,
        fileSystem: ProjectFileSystem,
        terminalExecutor: TerminalExecutor,
        nativeBinaryManager: NativeBinaryManager,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) : this(projectDao, fileSystem, terminalExecutor, nativeBinaryManager) {
        this.ioDispatcher = ioDispatcher
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        initializeTerminal()
    }

    private fun initializeTerminal() {
        viewModelScope.launch(ioDispatcher) {
            val envInfo = nativeBinaryManager.inspectEnvironment(terminalExecutor)
            val currentDir = (terminalExecutor as? PosixTerminalExecutor)?.activeDirectory?.absolutePath
                ?: (terminalExecutor as? PosixTerminalExecutor)?.currentWorkingDir?.absolutePath
                ?: System.getProperty("user.dir")
                ?: "/"

            _uiState.update { current ->
                current.copy(
                    envInfo = envInfo,
                    workingDirectory = currentDir,
                    entries = listOf(
                        TerminalEntry(
                            text = "CodeAgent POSIX Terminal Engine [v1.0]\n" +
                                   "Architecture: ${envInfo.architecture} | Shell: ${envInfo.shellPath}\n" +
                                   "Git: ${if (envInfo.isGitAvailable) envInfo.gitVersion ?: "Available" else "Not detected"}\n" +
                                   "Working Directory: $currentDir\n" +
                                   "Type commands below or use the quick key bar.",
                            type = TerminalEntryType.SYSTEM
                        )
                    )
                )
            }
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch(ioDispatcher) {
            val project = projectDao.getById(projectId) ?: return@launch
            val treeUri = Uri.parse(project.treeUri)
            val path = treeUri.path ?: treeUri.toString().removePrefix("file://")
            val workDir = File(path)

            terminalExecutor.bind(fileSystem, treeUri, if (workDir.exists()) workDir else null)
            val currentDir = (terminalExecutor as? PosixTerminalExecutor)?.activeDirectory?.absolutePath
                ?: workDir.absolutePath

            _uiState.update { current ->
                current.copy(
                    projectName = project.name,
                    projectId = projectId,
                    workingDirectory = currentDir,
                    entries = current.entries + TerminalEntry(
                        text = "Workspace switched to: ${project.name} ($currentDir)",
                        type = TerminalEntryType.SYSTEM
                    )
                )
            }
        }
    }

    fun onCommandInputChange(newInput: String) {
        _uiState.update { it.copy(commandInput = newInput) }
    }

    fun executeCommand(commandText: String = _uiState.value.commandInput) {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) return

        // Record in command history
        val newHistory = _uiState.value.commandHistory.toMutableList()
        if (newHistory.isEmpty() || newHistory.last() != trimmed) {
            newHistory.add(trimmed)
        }

        // Add command prompt entry
        val cmdEntry = TerminalEntry(
            text = "$ $trimmed",
            type = TerminalEntryType.COMMAND
        )

        _uiState.update { current ->
            current.copy(
                commandInput = "",
                commandHistory = newHistory,
                historyPointer = -1,
                entries = current.entries + cmdEntry
            )
        }

        // Handle internal UI builtins
        if (trimmed == "clear" || trimmed == "cls") {
            _uiState.update { it.copy(entries = emptyList()) }
            return
        }

        if (trimmed == "help") {
            val helpText = """
                Built-in Commands:
                  clear          - Clear terminal buffer
                  help           - Show this help message
                  pwd            - Print current directory
                  cd <dir>       - Change directory (persists across commands)
                  env-info       - Show native shell & binary environment
                
                POSIX & Git Commands:
                  Supports standard shell execution (/system/bin/sh, pipelines, redirection).
                  Bundled Git & Busybox binaries are automatically resolved.
            """.trimIndent()
            _uiState.update { it.copy(entries = it.entries + TerminalEntry(helpText, TerminalEntryType.SYSTEM)) }
            return
        }

        if (trimmed == "env-info") {
            val env = _uiState.value.envInfo
            val infoText = """
                Shell: ${env?.shellPath ?: "unknown"}
                Architecture: ${env?.architecture ?: "unknown"}
                Git Available: ${env?.isGitAvailable ?: false} (${env?.gitVersion ?: "N/A"})
                Busybox Available: ${env?.isBusyboxAvailable ?: false} (${env?.busyboxVersion ?: "N/A"})
                Native Library Dir: ${env?.nativeLibraryDir ?: "None"}
                Current PWD: ${_uiState.value.workingDirectory}
            """.trimIndent()
            _uiState.update { it.copy(entries = it.entries + TerminalEntry(infoText, TerminalEntryType.SYSTEM)) }
            return
        }

        // Launch POSIX process
        activeJob?.cancel()
        activeJob = viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRunning = true) }

            val result = terminalExecutor.execute(trimmed) { line ->
                _uiState.update { current ->
                    current.copy(
                        entries = current.entries + TerminalEntry(
                            text = line,
                            type = TerminalEntryType.STDOUT
                        )
                    )
                }
            }

            val currentDir = (terminalExecutor as? PosixTerminalExecutor)?.activeDirectory?.absolutePath
                ?: _uiState.value.workingDirectory

            when (result) {
                is TerminalResult.Success -> {
                    // Output was already streamed line by line via callback
                }
                is TerminalResult.Error -> {
                    _uiState.update { current ->
                        current.copy(
                            entries = current.entries + TerminalEntry(
                                text = "Exit ${result.exitCode ?: 1}: ${result.message}",
                                type = TerminalEntryType.STDERR
                            )
                        )
                    }
                }
                is TerminalResult.Disabled -> {
                    _uiState.update { current ->
                        current.copy(
                            entries = current.entries + TerminalEntry(
                                text = "Terminal execution is disabled in settings.",
                                type = TerminalEntryType.STDERR
                            )
                        )
                    }
                }
            }

            _uiState.update { it.copy(isRunning = false, workingDirectory = currentDir) }
        }
    }

    fun cancelRunningCommand() {
        if (_uiState.value.isRunning) {
            activeJob?.cancel()
            activeJob = null
            _uiState.update { current ->
                current.copy(
                    isRunning = false,
                    entries = current.entries + TerminalEntry(
                        text = "^C (Command terminated)",
                        type = TerminalEntryType.STDERR
                    )
                )
            }
        }
    }

    fun navigateHistoryPrevious() {
        val history = _uiState.value.commandHistory
        if (history.isEmpty()) return
        val currentPointer = _uiState.value.historyPointer
        val newPointer = if (currentPointer == -1) {
            history.size - 1
        } else {
            (currentPointer - 1).coerceAtLeast(0)
        }
        _uiState.update { it.copy(historyPointer = newPointer, commandInput = history[newPointer]) }
    }

    fun navigateHistoryNext() {
        val history = _uiState.value.commandHistory
        if (history.isEmpty()) return
        val currentPointer = _uiState.value.historyPointer
        if (currentPointer == -1) return
        val newPointer = currentPointer + 1
        if (newPointer < history.size) {
            _uiState.update { it.copy(historyPointer = newPointer, commandInput = history[newPointer]) }
        } else {
            _uiState.update { it.copy(historyPointer = -1, commandInput = "") }
        }
    }

    fun insertAccessoryKey(token: String) {
        when (token) {
            "CLEAR" -> executeCommand("clear")
            "CTRL-C" -> cancelRunningCommand()
            "TAB" -> {
                val input = _uiState.value.commandInput
                _uiState.update { it.copy(commandInput = "$input\t") }
            }
            else -> {
                val input = _uiState.value.commandInput
                val separator = if (input.isNotEmpty() && !input.endsWith(" ")) " " else ""
                _uiState.update { it.copy(commandInput = "$input$separator$token") }
            }
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(entries = emptyList()) }
    }
}
