package com.codeagent.core.terminal

import android.net.Uri
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.files.UriPathResolver
import com.codeagent.core.git.GitOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTerminalExecutor @Inject constructor(
    private val gitOperations: GitOperations
) : TerminalExecutor {

    private var fileSystem: ProjectFileSystem? = null
    private var projectRootUri: Uri? = null
    private var localWorkDir: File? = null
    private var virtualShell: VirtualShell? = null

    override var isEnabled: Boolean = true

    override fun bind(fileSystem: ProjectFileSystem, rootUri: Uri, localWorkDir: File?) {
        this.fileSystem = fileSystem
        this.projectRootUri = rootUri
        val resolvedLocal = localWorkDir ?: UriPathResolver.resolveLocalDirectory(null, rootUri, rootUri.lastPathSegment ?: "workspace")
        this.localWorkDir = resolvedLocal
        this.virtualShell = VirtualShell(fileSystem, rootUri, resolvedLocal.name)
    }

    override fun unbind() {
        this.fileSystem = null
        this.projectRootUri = null
        this.localWorkDir = null
        this.virtualShell = null
    }

    override suspend fun execute(command: String, onOutput: ((String) -> Unit)?): TerminalResult = withContext(Dispatchers.IO) {
        if (!isEnabled) {
            return@withContext TerminalResult.Disabled
        }
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext TerminalResult.Success("", 0)
        }

        val fs = fileSystem
        val rootUri = projectRootUri
        var localDir = localWorkDir
        if (localDir == null && rootUri != null) {
            localDir = UriPathResolver.resolveLocalDirectory(null, rootUri, rootUri.lastPathSegment ?: "workspace")
            this@DefaultTerminalExecutor.localWorkDir = localDir
        }

        if (fs == null && localDir == null) {
            val err = "No active project workspace bound to terminal."
            onOutput?.invoke(err)
            return@withContext TerminalResult.Error(err, 1)
        }

        var shell = virtualShell
        if (shell == null && fs != null && rootUri != null) {
            shell = VirtualShell(fs, rootUri, localDir?.name ?: "")
            this@DefaultTerminalExecutor.virtualShell = shell
        }

        val parsed = CommandParser.parse(trimmed) ?: return@withContext TerminalResult.Success("", 0)
        val exe = parsed.executable.lowercase()

        // 1. Route Git operations to JGit
        if (exe == "git") {
            val baseDir = localDir ?: (if (rootUri != null) UriPathResolver.resolveLocalDirectory(null, rootUri) else null)
            if (baseDir != null) {
                val targetDir = if (shell != null && shell.currentRelativePath.isNotEmpty()) {
                    File(baseDir, shell.currentRelativePath)
                } else {
                    baseDir
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val gitRes = gitOperations.executeGit(targetDir, parsed.rawArgs)
                if (gitRes.output.isNotEmpty()) {
                    onOutput?.invoke(gitRes.output)
                }
                return@withContext if (gitRes.exitCode == 0) {
                    TerminalResult.Success(gitRes.output, 0)
                } else {
                    TerminalResult.Error(gitRes.output, gitRes.exitCode)
                }
            } else {
                val err = "Git operations require a local directory workspace."
                onOutput?.invoke(err)
                return@withContext TerminalResult.Error(err, 128)
            }
        }

        // 2. Route VFS operations to VirtualShell
        val virtualCommands = setOf("cd", "ls", "cat", "head", "tail", "wc", "grep", "find", "pwd", "echo", "mkdir", "touch")
        if (exe in virtualCommands && shell != null) {
            val res = shell.execute(parsed)
            when (res) {
                is TerminalResult.Success -> if (res.output.isNotEmpty()) onOutput?.invoke(res.output)
                is TerminalResult.Error -> onOutput?.invoke(res.message)
                else -> {}
            }
            return@withContext res
        }

        // 3. If local POSIX dir exists, fallback to ProcessBuilder (/system/bin/sh) with timeout
        if (localDir != null && localDir.exists()) {
            val processWorkDir = if (shell != null && shell.currentRelativePath.isNotEmpty()) {
                File(localDir, shell.currentRelativePath).apply { if (!exists()) mkdirs() }
            } else {
                localDir
            }
            return@withContext executeProcess(trimmed, processWorkDir, onOutput)
        }

        // 4. Command not recognized in virtual shell
        val notFound = "sh: $exe: command not found (virtual commands available: cd, ls, cat, head, tail, wc, grep, find, pwd, echo, mkdir, touch, git)"
        onOutput?.invoke(notFound)
        TerminalResult.Error(notFound, 127)
    }

    private suspend fun executeProcess(command: String, workingDir: File, onOutput: ((String) -> Unit)?): TerminalResult = withContext(Dispatchers.IO) {
        try {
            val result = withTimeoutOrNull(15_000L) {
                val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(workingDir)
                    .redirectErrorStream(true)

                val env = pb.environment()
                env["HOME"] = workingDir.absolutePath
                env["PWD"] = workingDir.absolutePath

                val process = pb.start()

                val output = StringBuilder()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val maxChars = 65536
                var charCount = 0

                var line = reader.readLine()
                while (line != null && charCount < maxChars) {
                    output.append(line).append("\n")
                    onOutput?.invoke(line)
                    charCount += line.length + 1
                    line = reader.readLine()
                }

                if (charCount >= maxChars) {
                    val trunc = "\n[... Output truncated at 64KB ...]\n"
                    output.append(trunc)
                    onOutput?.invoke(trunc)
                }

                val exitCode = process.waitFor()
                val outStr = output.toString().trimEnd()

                if (exitCode == 0) {
                    TerminalResult.Success(outStr, 0)
                } else {
                    TerminalResult.Error(if (outStr.isNotEmpty()) outStr else "Command exited with code $exitCode", exitCode)
                }
            }

            result ?: run {
                val timeoutMsg = "Command timed out after 15 seconds."
                onOutput?.invoke(timeoutMsg)
                TerminalResult.Error(timeoutMsg, 124)
            }
        } catch (e: Exception) {
            val errMsg = "Failed to execute process: ${e.message}"
            onOutput?.invoke(errMsg)
            TerminalResult.Error(errMsg, 1)
        }
    }
}
