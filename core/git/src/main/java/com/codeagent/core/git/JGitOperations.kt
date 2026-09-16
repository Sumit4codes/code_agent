package com.codeagent.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.dircache.DirCacheIterator
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JGitOperations @Inject constructor() : GitOperations {

    override suspend fun isGitRepo(workDir: File): Boolean = withContext(Dispatchers.IO) {
        val gitDir = File(workDir, ".git")
        if (gitDir.exists()) return@withContext true
        try {
            val repo = FileRepositoryBuilder().findGitDir(workDir).build()
            repo.objectDatabase.exists()
        } catch (_: Exception) {
            false
        }
    }

    private fun openGit(workDir: File): Git? {
        val gitDir = File(workDir, ".git")
        return try {
            if (gitDir.exists()) {
                Git.open(workDir)
            } else {
                val repo = FileRepositoryBuilder().findGitDir(workDir).build()
                if (repo.objectDatabase.exists()) Git(repo) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun initRepo(workDir: File): GitCommandResult = withContext(Dispatchers.IO) {
        try {
            Git.init().setDirectory(workDir).call().use { git ->
                GitCommandResult(
                    output = "Initialized empty Git repository in ${git.repository.directory.absolutePath}",
                    exitCode = 0
                )
            }
        } catch (e: Exception) {
            GitCommandResult("fatal: cannot init repository: ${e.message}", 1)
        }
    }

    override suspend fun status(workDir: File): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository (or any of the parent directories): .git", 128)
        git.use { g ->
            try {
                val status = g.status().call()
                val sb = StringBuilder()
                val branch = try { g.repository.branch ?: "HEAD" } catch (_: Exception) { "HEAD" }
                sb.append("On branch $branch\n")

                if (status.isClean) {
                    sb.append("nothing to commit, working tree clean\n")
                } else {
                    val staged = status.added + status.changed + status.removed
                    if (staged.isNotEmpty()) {
                        sb.append("Changes to be committed:\n")
                        status.added.forEach { sb.append("\tnew file:   $it\n") }
                        status.changed.forEach { sb.append("\tmodified:   $it\n") }
                        status.removed.forEach { sb.append("\tdeleted:    $it\n") }
                    }
                    val notStaged = status.modified + status.missing
                    if (notStaged.isNotEmpty()) {
                        sb.append("Changes not staged for commit:\n")
                        status.modified.forEach { sb.append("\tmodified:   $it\n") }
                        status.missing.forEach { sb.append("\tdeleted:    $it\n") }
                    }
                    if (status.untracked.isNotEmpty()) {
                        sb.append("Untracked files:\n")
                        status.untracked.forEach { sb.append("\t$it\n") }
                    }
                }
                GitCommandResult(sb.toString().trimEnd(), 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: status failed: ${e.message}", 1)
            }
        }
    }

    override suspend fun diff(workDir: File, cached: Boolean): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository: .git", 128)
        git.use { g ->
            try {
                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { formatter ->
                    formatter.setRepository(g.repository)
                    if (cached) {
                        val headTree = g.repository.resolve("HEAD^{tree}")
                        if (headTree != null) {
                            val headIter = CanonicalTreeParser()
                            g.repository.newObjectReader().use { reader ->
                                headIter.reset(reader, headTree)
                                val indexIter = DirCacheIterator(g.repository.readDirCache())
                                val entries = formatter.scan(headIter, indexIter)
                                for (entry in entries) {
                                    formatter.format(entry)
                                }
                            }
                        }
                    } else {
                        g.diff().setOutputStream(out).call()
                    }
                }
                val result = out.toString(Charsets.UTF_8.name()).trimEnd()
                GitCommandResult(result, 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: diff failed: ${e.message}", 1)
            }
        }
    }

    override suspend fun log(workDir: File, maxCount: Int): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository: .git", 128)
        git.use { g ->
            try {
                val logs = g.log().setMaxCount(maxCount).call()
                val sb = StringBuilder()
                for (commit in logs) {
                    sb.append("commit ${commit.id.name}\n")
                    sb.append("Author: ${commit.authorIdent.name} <${commit.authorIdent.emailAddress}>\n")
                    sb.append("Date:   ${commit.authorIdent.`when`}\n\n")
                    sb.append("    ${commit.fullMessage.trim()}\n\n")
                }
                val text = sb.toString().trimEnd()
                GitCommandResult(if (text.isEmpty()) "(no commits yet)" else text, 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: your current branch does not have any commits yet", 1)
            }
        }
    }

    override suspend fun branch(workDir: File): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository: .git", 128)
        git.use { g ->
            try {
                val branches = g.branchList().call()
                val current = try { g.repository.branch } catch (_: Exception) { null }
                val sb = StringBuilder()
                for (b in branches) {
                    val name = Repository.shortenRefName(b.name)
                    val isCurrent = name == current
                    sb.append(if (isCurrent) "* $name\n" else "  $name\n")
                }
                val text = sb.toString().trimEnd()
                GitCommandResult(if (text.isEmpty()) "* (no branch)" else text, 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: branch failed: ${e.message}", 1)
            }
        }
    }

    override suspend fun add(workDir: File, filePattern: String): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository: .git", 128)
        git.use { g ->
            try {
                val pattern = if (filePattern == "." || filePattern == "*") "." else filePattern
                g.add().addFilepattern(pattern).call()
                GitCommandResult("", 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: add failed: ${e.message}", 1)
            }
        }
    }

    override suspend fun commit(
        workDir: File,
        message: String,
        authorName: String,
        authorEmail: String
    ): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository: .git", 128)
        git.use { g ->
            try {
                val commit = g.commit()
                    .setMessage(message)
                    .setAuthor(authorName, authorEmail)
                    .call()
                val branch = try { g.repository.branch ?: "HEAD" } catch (_: Exception) { "HEAD" }
                val shortId = commit.id.abbreviate(7).name()
                GitCommandResult("[$branch $shortId] $message", 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: commit failed: ${e.message}", 1)
            }
        }
    }

    override suspend fun checkout(
        workDir: File,
        target: String,
        createNewBranch: Boolean
    ): GitCommandResult = withContext(Dispatchers.IO) {
        val git = openGit(workDir) ?: return@withContext GitCommandResult("fatal: not a git repository: .git", 128)
        git.use { g ->
            try {
                val cmd = g.checkout().setName(target)
                if (createNewBranch) {
                    cmd.setCreateBranch(true)
                }
                cmd.call()
                val msg = if (createNewBranch) "Switched to a new branch '$target'" else "Switched to branch '$target'"
                GitCommandResult(msg, 0)
            } catch (e: Exception) {
                GitCommandResult("fatal: checkout failed: ${e.message}", 1)
            }
        }
    }

    override suspend fun executeGit(workDir: File, args: List<String>): GitCommandResult = withContext(Dispatchers.IO) {
        if (args.isEmpty()) {
            return@withContext GitCommandResult("usage: git [--version] [--help] <command> [<args>]", 0)
        }

        val subCmd = args[0].lowercase()
        val rest = args.drop(1)

        when (subCmd) {
            "status" -> status(workDir)
            "diff" -> {
                val cached = "--staged" in rest || "--cached" in rest
                diff(workDir, cached)
            }
            "log" -> {
                var maxCount = 10
                for (i in rest.indices) {
                    if (rest[i] == "-n" || rest[i] == "--max-count") {
                        val parsed = rest.getOrNull(i + 1)?.toIntOrNull()
                        if (parsed != null) maxCount = parsed
                    } else if (rest[i].startsWith("-") && rest[i].drop(1).all { it.isDigit() }) {
                        val parsed = rest[i].drop(1).toIntOrNull()
                        if (parsed != null) maxCount = parsed
                    }
                }
                log(workDir, maxCount)
            }
            "branch" -> branch(workDir)
            "init" -> initRepo(workDir)
            "add" -> {
                val pattern = rest.firstOrNull { !it.startsWith("-") } ?: "."
                add(workDir, pattern)
            }
            "commit" -> {
                var msg = "Commit from CodeAgent"
                for (i in rest.indices) {
                    if (rest[i] == "-m" || rest[i] == "--message") {
                        val m = rest.getOrNull(i + 1)
                        if (m != null) msg = m
                    }
                }
                commit(workDir, msg)
            }
            "checkout" -> {
                val create = "-b" in rest
                val target = rest.firstOrNull { it != "-b" && !it.startsWith("-") }
                if (target == null) {
                    GitCommandResult("fatal: missing branch/commit target for checkout", 1)
                } else {
                    checkout(workDir, target, create)
                }
            }
            "version", "--version" -> GitCommandResult("git version 2.43.0 (JGit 6.9.0)", 0)
            else -> GitCommandResult("git: '$subCmd' is not supported in virtual environment. Supported: status, diff, log, add, commit, branch, checkout, init, version", 1)
        }
    }
}
