package com.codeagent.feature.terminal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.files.FileProjectFileSystem
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.terminal.AlpineBootstrapManager
import com.codeagent.core.terminal.CodeAgentTerminalSessionClient
import com.codeagent.core.terminal.CodeAgentTerminalViewClient
import com.codeagent.core.terminal.NativeBinaryManager
import com.codeagent.core.terminal.PosixTerminalExecutor
import com.codeagent.core.terminal.TerminalExecutor
import com.codeagent.core.terminal.TerminalResult
import com.codeagent.core.terminal.TermuxSessionManager
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.codeagent.core.data.TerminalPreferencesRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val fileSystem: ProjectFileSystem,
    val terminalExecutor: TerminalExecutor,
    private val nativeBinaryManager: NativeBinaryManager,
    val alpineBootstrapManager: AlpineBootstrapManager? = null,
    val termuxSessionManager: TermuxSessionManager? = null,
    val terminalPreferencesRepository: TerminalPreferencesRepository? = null,
    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    constructor(
        projectDao: ProjectDao,
        fileSystem: ProjectFileSystem,
        terminalExecutor: TerminalExecutor,
        nativeBinaryManager: NativeBinaryManager,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) : this(projectDao, fileSystem, terminalExecutor, nativeBinaryManager, null, null, null, ioDispatcher)

    private val _uiState = MutableStateFlow(
        TerminalUiState(
            fontSizeSp = terminalPreferencesRepository?.getFontSizeSp()
                ?: TerminalPreferencesRepository.DEFAULT_FONT_SIZE_SP
        )
    )
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    private data class SessionHolder(
        val id: String,
        var title: String,
        val session: TerminalSession?,
        val client: CodeAgentTerminalSessionClient,
        var isRunning: Boolean = true
    )

    private val sessionHolders = mutableListOf<SessionHolder>()
    private var sessionCounter = 1

    val terminalSession: TerminalSession?
        get() = sessionHolders.firstOrNull { it.id == _uiState.value.activeSessionId }?.session

    private val defaultSessionClient = CodeAgentTerminalSessionClient()
    val sessionClient: CodeAgentTerminalSessionClient
        get() = sessionHolders.firstOrNull { it.id == _uiState.value.activeSessionId }?.client ?: defaultSessionClient

    private var terminalViewRef: WeakReference<TerminalView>? = null
    val viewClient = CodeAgentTerminalViewClient(
        onScaleChange = { increase ->
            if (increase) increaseFontSize() else decreaseFontSize()
        },
        terminalViewProvider = { terminalViewRef?.get() }
    )

    init {
        observeTerminalPreferences()
        initializeTerminal()
        addNewSession()
    }

    private fun observeTerminalPreferences() {
        val repo = terminalPreferencesRepository ?: return
        viewModelScope.launch {
            repo.fontSizeSpFlow.collect { sizeSp ->
                _uiState.update { it.copy(fontSizeSp = sizeSp) }
            }
        }
    }

    fun setFontSize(sizeSp: Int) {
        val clamped = sizeSp.coerceIn(
            TerminalPreferencesRepository.MIN_FONT_SIZE_SP,
            TerminalPreferencesRepository.MAX_FONT_SIZE_SP
        )
        _uiState.update { it.copy(fontSizeSp = clamped) }
        viewModelScope.launch {
            terminalPreferencesRepository?.setFontSizeSp(clamped)
        }
    }

    fun increaseFontSize() {
        val current = _uiState.value.fontSizeSp
        setFontSize(current + 1)
    }

    fun decreaseFontSize() {
        val current = _uiState.value.fontSizeSp
        setFontSize(current - 1)
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
            val workDir = treeUri?.let { FileProjectFileSystem.uriToFile(it) }
                ?: File(rawUri.removePrefix("file://"))

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
            restartSession()
        }
    }

    fun addNewSession(title: String? = null): TerminalSession? {
        val id = UUID.randomUUID().toString()
        val sessionIndex = sessionCounter++
        val sessionTitle = if (!title.isNullOrBlank()) title else "Session $sessionIndex"

        val client = CodeAgentTerminalSessionClient(
            onTitleChangedCallback = { newTitle ->
                if (newTitle.isNotBlank()) {
                    updateSessionTitle(id, newTitle)
                }
            },
            onSessionFinishedCallback = { finishedSession ->
                onSessionFinished(id, finishedSession)
            }
        )

        terminalViewRef?.get()?.let { client.attachView(it) }

        var session: TerminalSession? = null
        val mgr = termuxSessionManager
        if (mgr != null) {
            val workDir = terminalExecutor.activeDirectory
            try {
                session = mgr.createSession(workDir, client)
            } catch (e: Throwable) {
                android.util.Log.e("TerminalViewModel", "Failed to create terminal session", e)
            }
        }

        val isRunning = session?.isRunning ?: false
        val holder = SessionHolder(
            id = id,
            title = sessionTitle,
            session = session,
            client = client,
            isRunning = isRunning
        )
        sessionHolders.add(holder)

        syncSessionsUi(activeId = id)

        terminalViewRef?.get()?.let { view ->
            if (session != null) {
                try {
                    view.attachSession(session)
                } catch (e: Throwable) {
                    android.util.Log.e("TerminalViewModel", "Failed to attach session to view", e)
                }
            }
        }

        return session
    }

    fun switchSession(sessionId: String) {
        val holder = sessionHolders.firstOrNull { it.id == sessionId } ?: return
        syncSessionsUi(activeId = sessionId)

        terminalViewRef?.get()?.let { view ->
            holder.session?.let { session ->
                if (view.currentSession != session) {
                    try {
                        view.attachSession(session)
                    } catch (e: Throwable) {
                        android.util.Log.e("TerminalViewModel", "Failed to attach session on switch", e)
                    }
                }
            }
        }
    }

    fun closeSession(sessionId: String) {
        val index = sessionHolders.indexOfFirst { it.id == sessionId }
        if (index == -1) return

        val holder = sessionHolders.removeAt(index)
        try {
            holder.session?.finishIfRunning()
        } catch (e: Throwable) {
            android.util.Log.w("TerminalViewModel", "Failed to finish session $sessionId", e)
        }

        if (sessionHolders.isEmpty()) {
            addNewSession()
            return
        }

        val currentActiveId = _uiState.value.activeSessionId
        val nextActiveId = if (currentActiveId == sessionId) {
            val nextIndex = index.coerceAtMost(sessionHolders.size - 1)
            sessionHolders[nextIndex].id
        } else {
            currentActiveId
        }

        syncSessionsUi(activeId = nextActiveId)

        if (currentActiveId == sessionId) {
            val nextHolder = sessionHolders.firstOrNull { it.id == nextActiveId }
            terminalViewRef?.get()?.let { view ->
                nextHolder?.session?.let { session ->
                    try {
                        view.attachSession(session)
                    } catch (e: Throwable) {
                        android.util.Log.e("TerminalViewModel", "Failed to attach session on close", e)
                    }
                }
            }
        }
    }

    fun updateSessionTitle(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        val holder = sessionHolders.firstOrNull { it.id == sessionId } ?: return
        holder.title = trimmed
        if (sessionId == _uiState.value.activeSessionId && _uiState.value.projectId == null) {
            _uiState.update { it.copy(projectName = trimmed) }
        }
        syncSessionsUi()
    }

    private fun onSessionFinished(sessionId: String, session: TerminalSession) {
        val holder = sessionHolders.firstOrNull { it.id == sessionId } ?: return
        holder.isRunning = false
        syncSessionsUi()
    }

    private fun syncSessionsUi(activeId: String? = _uiState.value.activeSessionId) {
        val resolvedActiveId = if (sessionHolders.any { it.id == activeId }) {
            activeId
        } else {
            sessionHolders.lastOrNull()?.id
        }

        val tabs = sessionHolders.map {
            TerminalSessionTab(
                id = it.id,
                title = it.title,
                isRunning = it.isRunning
            )
        }

        val activeHolder = sessionHolders.firstOrNull { it.id == resolvedActiveId }
        val activeRunning = activeHolder?.isRunning ?: false

        _uiState.update { current ->
            current.copy(
                sessions = tabs,
                activeSessionId = resolvedActiveId,
                isRunning = activeRunning
            )
        }
    }

    fun registerTerminalView(view: TerminalView) {
        terminalViewRef = WeakReference(view)
        view.setTerminalViewClient(viewClient)
        sessionHolders.forEach { it.client.attachView(view) }
        try {
            if (sessionHolders.isEmpty()) {
                val session = addNewSession()
                if (session != null) {
                    view.attachSession(session)
                }
            } else {
                val active = sessionHolders.firstOrNull { it.id == _uiState.value.activeSessionId }
                    ?: sessionHolders.first()
                if (active.session != null) {
                    view.attachSession(active.session)
                } else if (termuxSessionManager != null) {
                    restartSession(active.id)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("TerminalViewModel", "Failed to attach terminal session on register", e)
        }
    }

    fun getOrCreateSession(): TerminalSession? {
        val active = sessionHolders.firstOrNull { it.id == _uiState.value.activeSessionId }
        if (active?.session != null && active.session.isRunning) {
            return active.session
        }
        if (sessionHolders.isEmpty()) {
            return addNewSession()
        }
        return active?.session
    }

    fun restartSession(sessionId: String? = _uiState.value.activeSessionId) {
        viewModelScope.launch(Dispatchers.Main) {
            if (sessionHolders.isEmpty()) {
                addNewSession()
                return@launch
            }
            val targetId = sessionId ?: sessionHolders.first().id
            val index = sessionHolders.indexOfFirst { it.id == targetId }
            if (index == -1) return@launch

            val oldHolder = sessionHolders[index]
            try {
                oldHolder.session?.finishIfRunning()
            } catch (_: Throwable) {}

            val client = CodeAgentTerminalSessionClient(
                onTitleChangedCallback = { newTitle ->
                    if (newTitle.isNotBlank()) {
                        updateSessionTitle(targetId, newTitle)
                    }
                },
                onSessionFinishedCallback = { finishedSession ->
                    onSessionFinished(targetId, finishedSession)
                }
            )
            terminalViewRef?.get()?.let { client.attachView(it) }

            var newSession: TerminalSession? = null
            val mgr = termuxSessionManager
            if (mgr != null) {
                val workDir = terminalExecutor.activeDirectory
                try {
                    newSession = mgr.createSession(workDir, client)
                } catch (e: Throwable) {
                    android.util.Log.e("TerminalViewModel", "Failed to restart session $targetId", e)
                }
            }

            val isRunning = newSession?.isRunning ?: false
            sessionHolders[index] = oldHolder.copy(
                session = newSession,
                client = client,
                isRunning = isRunning
            )

            syncSessionsUi(activeId = targetId)

            if (_uiState.value.activeSessionId == targetId) {
                terminalViewRef?.get()?.let { view ->
                    if (newSession != null) {
                        try {
                            view.attachSession(newSession)
                        } catch (e: Throwable) {
                            android.util.Log.e("TerminalViewModel", "Failed to attach restarted session", e)
                        }
                    }
                }
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

    fun sendAccessoryKey(key: String) {
        val session = terminalSession
        if (session != null && session.isRunning) {
            when (key.uppercase()) {
                "ESC" -> session.write(byteArrayOf(0x1B), 0, 1)
                "TAB" -> session.write(byteArrayOf(0x09), 0, 1)
                "CTRL-C" -> session.write(byteArrayOf(0x03), 0, 1)
                "CTRL-D" -> session.write(byteArrayOf(0x04), 0, 1)
                "CTRL-Z" -> session.write(byteArrayOf(0x1A), 0, 1)
                "CTRL" -> {
                    viewClient.isControlKeyPressed = !viewClient.isControlKeyPressed
                }
                "ALT" -> {
                    viewClient.isAltKeyPressed = !viewClient.isAltKeyPressed
                }
                "↑", "UP" -> {
                    val bytes = "\u001b[A".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                "↓", "DOWN" -> {
                    val bytes = "\u001b[B".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                "→", "RIGHT" -> {
                    val bytes = "\u001b[C".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                "←", "LEFT" -> {
                    val bytes = "\u001b[D".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                "CLEAR" -> {
                    val bytes = "clear\r".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                "PWD" -> {
                    val bytes = "pwd\r".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                "LS -LA" -> {
                    val bytes = "ls -la\r".toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
                else -> {
                    val text = if (key.endsWith(" ") || key.startsWith("-") || key == "|" || key == "&&" || key == ";" || key == "/" || key == "~") key else "$key "
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    session.write(bytes, 0, bytes.size)
                }
            }
        } else {
            insertAccessoryKey(key)
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(entries = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        sessionHolders.forEach { holder ->
            try {
                holder.session?.finishIfRunning()
            } catch (_: Throwable) {}
        }
        sessionHolders.clear()
    }
}
