package com.codeagent.core.git

interface GitOperations {
    suspend fun status(projectUri: String): GitStatus?
    suspend fun diff(projectUri: String): String?
}

data class GitStatus(
    val branch: String,
    val modified: List<String>,
    val staged: List<String>,
    val untracked: List<String>
)
