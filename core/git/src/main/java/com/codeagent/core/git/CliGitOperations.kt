package com.codeagent.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CliGitOperations @Inject constructor() : GitOperations {

    private fun resolveGitExecutable(): String {
        // 1. Check if git is in PATH
        val pathEnv = System.getenv("PATH") ?: "/system/bin:/system/xbin:/bin:/usr/bin"
        for (dir in pathEnv.split(':')) {
            val candidate = File(dir, "git")
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
        // 2. Default binary invocation
        return "git"
    }

    override suspend fun executeGit(workDir: File, args: List<String>): GitCommandResult = withContext(Dispatchers.IO) {
        val gitExe = resolveGitExecutable()
        val command = mutableListOf(gitExe).apply { addAll(args) }

        try {
            val pb = ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(true)

            val env = pb.environment()
            env["GIT_TERMINAL_PROMPT"] = "0"
            env["LC_ALL"] = "C.UTF-8"
            env["HOME"] = workDir.absolutePath
            env["PWD"] = workDir.absolutePath

            val process = pb.start()
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            val maxBytes = 65536
            var byteCount = 0

            while (line != null && byteCount < maxBytes) {
                output.append(line).append("\n")
                byteCount += line.length + 1
                line = reader.readLine()
            }

            val exitCode = process.waitFor()
            GitCommandResult(output.toString().trimEnd(), exitCode)
        } catch (e: Exception) {
            GitCommandResult("fatal: failed to execute git (${e.message})", 127)
        }
    }

    override suspend fun isGitRepo(workDir: File): Boolean = withContext(Dispatchers.IO) {
        val gitDir = File(workDir, ".git")
        if (gitDir.exists()) return@withContext true
        val result = executeGit(workDir, listOf("rev-parse", "--is-inside-work-tree"))
        result.exitCode == 0 && result.output.trim() == "true"
    }

    override suspend fun initRepo(workDir: File): GitCommandResult =
        executeGit(workDir, listOf("init"))

    override suspend fun status(workDir: File): GitCommandResult =
        executeGit(workDir, listOf("status"))

    override suspend fun diff(workDir: File, cached: Boolean): GitCommandResult {
        val args = if (cached) listOf("diff", "--cached") else listOf("diff")
        return executeGit(workDir, args)
    }

    override suspend fun log(workDir: File, maxCount: Int): GitCommandResult =
        executeGit(workDir, listOf("log", "-n", maxCount.toString(), "--oneline"))

    override suspend fun branch(workDir: File): GitCommandResult =
        executeGit(workDir, listOf("branch", "-a"))

    override suspend fun add(workDir: File, filePattern: String): GitCommandResult =
        executeGit(workDir, listOf("add", filePattern))

    override suspend fun commit(
        workDir: File,
        message: String,
        authorName: String,
        authorEmail: String
    ): GitCommandResult {
        val args = listOf(
            "-c", "user.name=$authorName",
            "-c", "user.email=$authorEmail",
            "commit", "-m", message
        )
        return executeGit(workDir, args)
    }

    override suspend fun checkout(workDir: File, target: String, createNewBranch: Boolean): GitCommandResult {
        val args = if (createNewBranch) {
            listOf("checkout", "-b", target)
        } else {
            listOf("checkout", target)
        }
        return executeGit(workDir, args)
    }
}
