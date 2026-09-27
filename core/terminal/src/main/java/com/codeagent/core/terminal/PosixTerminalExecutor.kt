package com.codeagent.core.terminal

import android.content.Context
import android.net.Uri
import com.codeagent.core.files.ProjectFileSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PosixTerminalExecutor @Inject constructor(
    @ApplicationContext private val context: Context? = null,
    val nativeBinaryManager: NativeBinaryManager? = null,
    val alpineBootstrapManager: AlpineBootstrapManager? = null
) : TerminalExecutor {

    // Secondary constructor for unit tests without Android Context
    constructor() : this(null, null, null)

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    private var fileSystem: ProjectFileSystem? = null
    private var projectRootUri: Uri? = null
    private var baseProjectDir: File? = null
    var currentWorkingDir: File? = null
        private set
    private var previousWorkingDir: File? = null

    override var isEnabled: Boolean = true

    fun isAlpineActive(): Boolean {
        return nativeBinaryManager?.isAlpineReady() == true
    }

    override fun bind(fileSystem: ProjectFileSystem, rootUri: Uri?, localWorkDir: File?) {
        this.fileSystem = fileSystem
        this.projectRootUri = rootUri
        val dir = localWorkDir ?: run {
            val path = rootUri?.path ?: rootUri?.toString()?.removePrefix("file://")
            val f = path?.let { File(it) }
            if (f != null && f.exists() && f.isDirectory) f else null
        }
        this.baseProjectDir = dir
        this.currentWorkingDir = dir
        this.previousWorkingDir = dir
    }

    override fun unbind() {
        this.fileSystem = null
        this.projectRootUri = null
        this.baseProjectDir = null
        this.currentWorkingDir = null
        this.previousWorkingDir = null
    }

    override val activeDirectory: File?
        get() = currentWorkingDir ?: baseProjectDir

    fun setWorkingDirectory(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            this.previousWorkingDir = this.currentWorkingDir ?: dir
            this.currentWorkingDir = try { dir.canonicalFile } catch (_: Exception) { dir.absoluteFile }
        }
    }

    private fun buildShellPreamble(): String {
        val nativeDir = try { context?.applicationInfo?.nativeLibraryDir } catch (_: Exception) { null }
        if (nativeDir.isNullOrBlank()) return ""
        val sb = StringBuilder()
        val gitLib = File(nativeDir, "libgit.so")
        if (gitLib.exists()) {
            sb.append("git() { \"${gitLib.absolutePath}\" \"\$@\"; }; export -f git 2>/dev/null || true;\n")
        }
        val busyboxLib = File(nativeDir, "libbusybox.so")
        if (busyboxLib.exists()) {
            sb.append("busybox() { \"${busyboxLib.absolutePath}\" \"\$@\"; }; export -f busybox 2>/dev/null || true;\n")
        }
        return sb.toString().trimEnd()
    }

    private fun resolveShellExecutable(): String {
        return when {
            File("/system/bin/sh").exists() -> "/system/bin/sh"
            File("/bin/sh").exists() -> "/bin/sh"
            else -> "sh"
        }
    }

    private fun buildPathEnvironment(): String {
        val pathEntries = mutableListOf<String>()
        val nativeDir = try { context?.applicationInfo?.nativeLibraryDir } catch (_: Exception) { null }
        if (!nativeDir.isNullOrBlank()) {
            pathEntries.add(nativeDir)
        }
        val filesBin = try { context?.filesDir?.let { File(it, "bin").absolutePath } } catch (_: Exception) { null }
        if (!filesBin.isNullOrBlank()) {
            pathEntries.add(filesBin)
        }
        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin:/bin:/usr/bin"
        pathEntries.add(systemPath)
        for (stdPath in listOf("/system/bin", "/system/xbin", "/vendor/bin", "/apex/com.android.runtime/bin")) {
            if (!pathEntries.any { it.contains(stdPath) }) {
                pathEntries.add(stdPath)
            }
        }
        return pathEntries.joinToString(":")
    }

    override suspend fun execute(command: String, onOutput: ((String) -> Unit)?): TerminalResult = withContext(ioDispatcher) {
        if (!isEnabled) {
            return@withContext TerminalResult.Disabled
        }
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext TerminalResult.Success("", 0)
        }

        // Special command to set up or reinstall Alpine Linux on device
        if (trimmed == "setup-alpine" || trimmed == "install-alpine") {
            return@withContext bootstrapAlpine(onOutput)
        }

        val workDir = currentWorkingDir ?: baseProjectDir ?: run {
            val fallback = try {
                context?.filesDir ?: File(System.getProperty("user.dir") ?: ".")
            } catch (_: Exception) {
                File(".")
            }
            currentWorkingDir = fallback
            fallback
        }

        // Handle standalone `cd` command to persist working directory
        if (trimmed == "cd" || trimmed.startsWith("cd ") || trimmed.startsWith("cd\t")) {
            val targetArg = trimmed.removePrefix("cd").trim()
            val changeResult = handleCd(targetArg, workDir)
            if (changeResult != null) {
                if (changeResult is TerminalResult.Error) {
                    onOutput?.invoke(changeResult.message)
                }
                return@withContext changeResult
            }
        }

        if (isAlpineActive()) {
            executeAlpineProcess(trimmed, workDir, onOutput)
        } else {
            executeNativeProcess(trimmed, workDir, onOutput)
        }
    }

    suspend fun bootstrapAlpine(onOutput: ((String) -> Unit)?): TerminalResult = withContext(ioDispatcher) {
        val mgr = alpineBootstrapManager ?: run {
            val msg = "Alpine bootstrap manager is not configured in this environment."
            onOutput?.invoke(msg)
            return@withContext TerminalResult.Error(msg, 1)
        }

        onOutput?.invoke("==> Initializing Alpine Linux environment for CodeAgent...")
        val result = mgr.bootstrap { stage, progress ->
            val pct = if (progress >= 0f) " [${(progress * 100).toInt()}%]" else ""
            onOutput?.invoke("$stage$pct")
        }

        if (result.isSuccess) {
            val successMsg = """
                [+] Alpine Linux initialized successfully!
                • Package manager available: apk
                • Install tools:
                    apk add git            (Git version control)
                    apk add python3 py3-pip (Python 3 + pip)
                    apk add gcc g++ make    (C/C++ compiler and build tools)
                • Type 'help' or 'bins' for more details.
            """.trimIndent()
            onOutput?.invoke(successMsg)
            TerminalResult.Success(successMsg, 0)
        } else {
            val err = "[-] Bootstrap failed: ${result.exceptionOrNull()?.message}"
            onOutput?.invoke(err)
            TerminalResult.Error(err, 1)
        }
    }

    private fun handleCd(targetArg: String, currentDir: File): TerminalResult? {
        val homeDir = baseProjectDir ?: context?.filesDir ?: File(System.getProperty("user.home") ?: ".")
        val targetFile = when {
            targetArg.isEmpty() || targetArg == "~" -> homeDir
            targetArg == "-" -> previousWorkingDir ?: homeDir
            targetArg.startsWith("/") -> File(targetArg)
            else -> File(currentDir, targetArg)
        }

        val canonical = try { targetFile.canonicalFile } catch (_: Exception) { targetFile.absoluteFile }
        if (!canonical.exists()) {
            return TerminalResult.Error("cd: $targetArg: No such file or directory", 1)
        }
        if (!canonical.isDirectory) {
            return TerminalResult.Error("cd: $targetArg: Not a directory", 1)
        }

        // Guard against navigating into directories without read/execute permissions for current app UID
        if (!canonical.canRead() && !canonical.canExecute()) {
            return TerminalResult.Error("cd: $targetArg: Permission denied", 1)
        }

        previousWorkingDir = currentDir
        currentWorkingDir = canonical
        return TerminalResult.Success("", 0)
    }

    private suspend fun executeAlpineProcess(
        command: String,
        workingDir: File,
        onOutput: ((String) -> Unit)?,
        timeoutMs: Long = 120_000L
    ): TerminalResult = withContext(ioDispatcher) {
        val prootExec = nativeBinaryManager?.resolvePRootExecutable()
            ?: return@withContext executeNativeProcess(command, workingDir, onOutput, timeoutMs)

        val nativeDir = context?.applicationInfo?.nativeLibraryDir?.let { File(it) }
        val rootDir = alpineBootstrapManager?.rootDir
            ?: context?.filesDir
            ?: File(".")
        val alpineDir = alpineBootstrapManager?.alpineDir ?: File(rootDir, "alpine")
        val tmpDir = alpineBootstrapManager?.tmpDir ?: File(rootDir, "tmp")
        val publicDir = alpineBootstrapManager?.publicDir ?: File(rootDir, "public")

        val args = mutableListOf<String>()
        args.add(prootExec)
        args.add("--kill-on-exit")
        args.add("-r")
        args.add(alpineDir.absolutePath)
        args.add("-0")
        args.add("--link2symlink")
        args.add("--sysvipc")
        args.add("-L")

        for (m in listOf("/dev", "/proc", "/sys")) {
            if (File(m).exists()) {
                args.add("-b")
                args.add(m)
            }
        }
        if (File("/dev/urandom").exists()) {
            args.add("-b")
            args.add("/dev/urandom:/dev/random")
        }

        for (storage in listOf("/sdcard", "/storage", "/mnt/sdcard")) {
            if (File(storage).exists()) {
                args.add("-b")
                args.add(storage)
            }
        }

        if (nativeDir != null && nativeDir.exists()) {
            args.add("-b")
            args.add(nativeDir.absolutePath)
        }
        args.add("-b")
        args.add(rootDir.absolutePath)

        args.add("-b")
        args.add("${publicDir.absolutePath}:/root")
        args.add("-b")
        args.add("${publicDir.absolutePath}:/home")

        val shm = File(alpineDir, "tmp")
        shm.mkdirs()
        args.add("-b")
        args.add("${shm.absolutePath}:/dev/shm")

        val hostWorkDir = workingDir.absolutePath
        if (File(hostWorkDir).exists()) {
            args.add("-b")
            args.add(hostWorkDir)
            args.add("-w")
            args.add(hostWorkDir)
        } else {
            args.add("-w")
            args.add("/root")
        }

        args.add("/bin/sh")
        args.add("-c")
        args.add(command)

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                val pb = ProcessBuilder(args)
                    .directory(if (workingDir.exists()) workingDir else rootDir)
                    .redirectErrorStream(true)

                val env = pb.environment()
                env["PROOT_TMP_DIR"] = tmpDir.absolutePath
                if (nativeDir != null) {
                    env["PROOT_LOADER"] = File(nativeDir, "libproot.so").absolutePath
                    val loader32 = File(nativeDir, "libproot32.so")
                    if (loader32.exists()) {
                        env["PROOT_LOADER32"] = loader32.absolutePath
                    }
                    env["LD_LIBRARY_PATH"] = "${rootDir.absolutePath}:${nativeDir.absolutePath}"
                } else {
                    env["LD_LIBRARY_PATH"] = rootDir.absolutePath
                }
                env["PATH"] = "/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin"
                env["HOME"] = "/root"
                env["USER"] = "root"
                env["SHELL"] = "/bin/sh"
                env["TERM"] = "xterm-256color"
                env["LANG"] = "C.UTF-8"
                env["LC_ALL"] = "C.UTF-8"

                val process = pb.start()
                runProcessStream(process, onOutput)
            }

            result ?: run {
                val timeoutMsg = "Command timed out after ${timeoutMs / 1000} seconds."
                onOutput?.invoke(timeoutMsg)
                TerminalResult.Error(timeoutMsg, 124)
            }
        } catch (e: Exception) {
            val errMsg = "PRoot execution error: ${e.message}"
            onOutput?.invoke(errMsg)
            TerminalResult.Error(errMsg, 1)
        }
    }

    private suspend fun executeNativeProcess(
        command: String,
        workingDir: File,
        onOutput: ((String) -> Unit)?,
        timeoutMs: Long = 30_000L
    ): TerminalResult = withContext(ioDispatcher) {
        val shell = resolveShellExecutable()
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                val preamble = buildShellPreamble()
                val finalCommand = if (preamble.isNotEmpty()) "$preamble\n$command" else command
                val pb = ProcessBuilder(shell, "-c", finalCommand)
                    .directory(workingDir)
                    .redirectErrorStream(true)

                val env = pb.environment()
                env["PATH"] = buildPathEnvironment()
                env["HOME"] = workingDir.absolutePath
                env["PWD"] = workingDir.absolutePath
                env["TMPDIR"] = try { context?.cacheDir?.absolutePath ?: "/data/local/tmp" } catch (_: Exception) { "/tmp" }
                env["TERM"] = "xterm-256color"
                env["GIT_TERMINAL_PROMPT"] = "0"
                env["LC_ALL"] = "C.UTF-8"

                val process = pb.start()
                runProcessStream(process, onOutput)
            }

            result ?: run {
                val timeoutMsg = "Command timed out after ${timeoutMs / 1000} seconds."
                onOutput?.invoke(timeoutMsg)
                TerminalResult.Error(timeoutMsg, 124)
            }
        } catch (e: Exception) {
            val errMsg = "Failed to execute command: ${e.message}"
            onOutput?.invoke(errMsg)
            if (e.message?.contains("error=13") == true || e.message?.contains("Permission denied") == true) {
                currentWorkingDir = baseProjectDir ?: context?.filesDir
            }
            TerminalResult.Error(errMsg, 1)
        }
    }

    private fun runProcessStream(
        process: Process,
        onOutput: ((String) -> Unit)?
    ): TerminalResult {
        try {
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val maxBytes = 65536
            var byteCount = 0
            var truncated = false

            var line = reader.readLine()
            while (line != null) {
                if (byteCount < maxBytes) {
                    output.append(line).append("\n")
                    onOutput?.invoke(line)
                    byteCount += line.length + 1
                } else if (!truncated) {
                    truncated = true
                    val truncMsg = "\n[... Output truncated at 64KB ...]\n"
                    output.append(truncMsg)
                    onOutput?.invoke(truncMsg)
                }
                line = reader.readLine()
            }

            val exitCode = process.waitFor()
            val outStr = output.toString().trimEnd()

            return if (exitCode == 0) {
                TerminalResult.Success(outStr, 0)
            } else {
                TerminalResult.Error(if (outStr.isNotEmpty()) outStr else "Process exited with code $exitCode", exitCode)
            }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}

typealias DefaultTerminalExecutor = PosixTerminalExecutor
