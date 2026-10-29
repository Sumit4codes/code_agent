package com.koda.dev.core.git

import java.io.File

data class GitCommandResult(
    val output: String,
    val exitCode: Int
)

data class GitStatus(
    val branch: String,
    val modified: List<String>,
    val staged: List<String>,
    val untracked: List<String>
)

interface GitOperations {
    suspend fun executeGit(workDir: File, args: List<String>): GitCommandResult
    suspend fun isGitRepo(workDir: File): Boolean
    suspend fun initRepo(workDir: File): GitCommandResult
    suspend fun status(workDir: File): GitCommandResult
    suspend fun diff(workDir: File, cached: Boolean = false): GitCommandResult
    suspend fun log(workDir: File, maxCount: Int = 10): GitCommandResult
    suspend fun branch(workDir: File): GitCommandResult
    suspend fun add(workDir: File, filePattern: String = "."): GitCommandResult
    suspend fun commit(
        workDir: File,
        message: String,
        authorName: String = "Koda Dev",
        authorEmail: String = "agent@kodadev.local"
    ): GitCommandResult
    suspend fun checkout(workDir: File, target: String, createNewBranch: Boolean = false): GitCommandResult
}
