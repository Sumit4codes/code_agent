package com.codeagent.feature.terminal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.terminal.AlpineBootstrapManager
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
    private val nativeBinaryManager: NativeBinaryManager,
    val alpineBootstrapManager: AlpineBootstrapManager? = null,
    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    constructor(
        projectDao: ProjectDao,
        fileSystem: ProjectFileSystem,
        terminalExecutor: TerminalExecutor,
        nativeBinaryManager: NativeBinaryManager,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) : this(projectDao, fileSystem, terminalExecutor, nativeBinaryManager, null, ioDispatcher)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        initializeTerminal()
    }

    fun initializeTerminal() {
        viewModelScope.launch(ioDispatcher) {
            val envInfo = nativeBinaryManager.inspectEnvironment(terminalExecutor)
            val isAlpine = nativeBinaryManager.isAlpineReady()
            val currentDir = terminalExecutor.activeDirectory?.absolutePath
                ?: (terminalExecutor as? PosixTerminalExecutor)?.currentWorkingDir?.absolutePath
                ?: System.getProperty("user.dir")
                ?: "/"

            val systemEntry = if (isAlpine) {
                TerminalEntry(
                    text = "CodeAgent POSIX Terminal Engine [Alpine Linux inside PRoot]\n" +
                           "Environment: ${envInfo.environmentType} | Arch: ${envInfo.architecture}\n" +
                           "Package Manager: apk (run 'apk add git python3 gcc g++ make' to install tools)\n" +
                           "Working Directory: $currentDir\n" +
                           "Storage Mounted: /sdcard, /storage\n" +
                           "Type 'help' or 'bins' for commands.",
                    type = TerminalEntryType.SYSTEM
                )
            } else {
                TerminalEntry(
                    text = "CodeAgent POSIX Terminal Engine [Native Toybox Shell]\n" +
                           "Architecture: ${envInfo.architecture} | Shell: ${envInfo.shellPath}\n" +
                           "Git: ${if (envInfo.isGitAvailable) envInfo.gitVersion ?: "Available" else "Not detected"}\n" +
                           "Working Directory: $currentDir\n" +
                           "Tip: Run 'setup-alpine' to install full Alpine Linux with python, gcc, make, and git.",
                    type = TerminalEntryType.SYSTEM
                )
            }

            _uiState.update { current ->
                val updatedEntries = if (current.entries.isEmpty()) {
                    listOf(systemEntry)
                } else if (current.entries.first().type == TerminalEntryType.SYSTEM) {
                    listOf(systemEntry) + current.entries.drop(1)
                } else {
                    listOf(systemEntry) + current.entries
                }
                current.copy(
                    envInfo = envInfo,
                    isAlpineReady = isAlpine,
                    workingDirectory = currentDir,
                    entries = updatedEntries
                )
            }
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch(ioDispatcher) {
            val project = projectDao.getById(projectId) ?: return@launch
            val rawUri = project.treeUri
            val treeUri = try { Uri.parse(rawUri) } catch (_: Exception) { null }
            val path = treeUri?.path ?: rawUri.removePrefix("file://")
            val workDir = File(path)

            terminalExecutor.bind(fileSystem, treeUri, if (workDir.exists()) workDir else null)
            val currentDir = terminalExecutor.activeDirectory?.absolutePath
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

    fun installAlpineEnvironment() {
        if (_uiState.value.isBootstrapping) return
        _uiState.update { it.copy(isBootstrapping = true, bootstrapProgress = 0f, bootstrapMessage = "Initializing Alpine Linux...") }
        executeCommand("setup-alpine")
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
            val isAlpine = _uiState.value.isAlpineReady
            val helpText = """
                Built-in Commands:
                  clear          - Clear terminal buffer
                  help           - Show this help message
                  bins           - List all available system utilities & commands
                  pwd            - Print current directory
                  cd <dir>       - Change directory (persists across commands)
                  env-info       - Show native shell & binary environment
                  setup-alpine   - Bootstrap/reinstall Alpine Linux distribution

                Alpine Linux Development Tools:
                  ${if (isAlpine) "Container Active: commands execute inside Alpine Linux with /sdcard mounted." else "Run 'setup-alpine' to activate Alpine Linux."}
                  apk update              - Refresh package repositories
                  apk add git             - Install Git version control
                  apk add python3 py3-pip - Install Python 3 and pip
                  apk add gcc g++ make    - Install C/C++ compiler and build tools
                  apk add nodejs npm      - Install Node.js & npm
                  apk search <query>      - Search for packages
            """.trimIndent()
            _uiState.update { it.copy(entries = it.entries + TerminalEntry(helpText, TerminalEntryType.SYSTEM)) }
            return
        }

        if (trimmed == "bins" || trimmed == "commands" || trimmed == "sys-bins") {
            val isAlpine = _uiState.value.isAlpineReady
            val binsText = """
                Available Commands in Terminal:

                1. System Utilities (Android Toybox):
                   cat, chmod, chown, clear, cp, cut, date, df, du, echo, env,
                   find, grep, head, id, kill, ln, logcat, ls, md5sum, mkdir,
                   mv, printenv, ps, pwd, rm, rmdir, sed, sleep, sort, stat,
                   tail, tar, tee, touch, tr, uname, uniq, wc, which, whoami, xargs...
                   (Run 'toybox' for full list of compiled Android applets)

                2. Package Manager (Alpine Linux):
                   ${if (isAlpine) "apk (Active) - use 'apk add <tool>' to install git, python3, gcc, make..." else "apk (Not installed - run 'setup-alpine' to enable)"}

                3. Development Environment:
                   Git: ${_uiState.value.envInfo?.gitVersion ?: "Install with 'apk add git'"}
                   Python: run 'apk add python3 py3-pip' to install
                   Compilers: run 'apk add gcc g++ make' to install C/C++
                   Environment: ${_uiState.value.envInfo?.environmentType ?: "Standard POSIX"}
            """.trimIndent()
            _uiState.update { it.copy(entries = it.entries + TerminalEntry(binsText, TerminalEntryType.SYSTEM)) }
            return
        }

        if (trimmed == "env-info") {
            val env = _uiState.value.envInfo
            val infoText = """
                Environment: ${env?.environmentType ?: "unknown"}
                Architecture: ${env?.architecture ?: "unknown"}
                PRoot Available: ${env?.isPRootAvailable ?: false}
                Alpine Installed: ${env?.isAlpineInstalled ?: false} (Version: ${env?.alpineVersion ?: "N/A"})
                Shell: ${env?.shellPath ?: "unknown"}
                Git: ${if (env?.isGitAvailable == true) env.gitVersion ?: "Available" else "Not detected"}
                Working Directory: ${_uiState.value.workingDirectory}
            """.trimIndent()
            _uiState.update { it.copy(entries = it.entries + TerminalEntry(infoText, TerminalEntryType.SYSTEM)) }
            return
        }

        // Launch process
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

            val currentDir = terminalExecutor.activeDirectory?.absolutePath
                ?: _uiState.value.workingDirectory

            when (result) {
                is TerminalResult.Success -> {
                    if (trimmed == "setup-alpine" || trimmed == "install-alpine") {
                        initializeTerminal()
                    }
                }
                is TerminalResult.Error -> {
                    val isSystemBinAccess = (trimmed.contains("/system/bin") || trimmed.contains("/system/xbin")) &&
                            (result.message.contains("Permission denied") || result.message.contains("error=13"))
                    val tip = if (isSystemBinAccess) {
                        "\n[Notice: Android SELinux blocks directory enumeration of /system/bin for apps. " +
                        "Commands in /system/bin are still executable directly via PATH. Run 'setup-alpine' for full Linux shell environment.]"
                    } else ""
                    _uiState.update { current ->
                        current.copy(
                            entries = current.entries + TerminalEntry(
                                text = "Exit ${result.exitCode ?: 1}: ${result.message}$tip",
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

            _uiState.update { it.copy(isRunning = false, isBootstrapping = false, workingDirectory = currentDir) }
        }
    }

    fun cancelRunningCommand() {
        if (_uiState.value.isRunning) {
            activeJob?.cancel()
            activeJob = null
            _uiState.update { current ->
                current.copy(
                    isRunning = false,
                    isBootstrapping = false,
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
