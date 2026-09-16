package com.codeagent.core.terminal

import android.net.Uri
import com.codeagent.core.files.FileNode
import com.codeagent.core.files.PathSafety
import com.codeagent.core.files.ProjectFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VirtualShell(
    private val fileSystem: ProjectFileSystem,
    private val rootUri: Uri,
    private val workingDirName: String = ""
) {

    suspend fun execute(cmd: ParsedCommand): TerminalResult = withContext(Dispatchers.IO) {
        val exe = cmd.executable.lowercase()
        when (exe) {
            "ls" -> executeLs(cmd)
            "cat" -> executeCat(cmd)
            "head" -> executeHead(cmd)
            "tail" -> executeTail(cmd)
            "wc" -> executeWc(cmd)
            "grep" -> executeGrep(cmd)
            "find" -> executeFind(cmd)
            "pwd" -> executePwd()
            "echo" -> executeEcho(cmd)
            "mkdir" -> executeMkdir(cmd)
            "touch" -> executeTouch(cmd)
            else -> TerminalResult.Error("sh: $exe: command not found (virtual shell supports: ls, cat, head, tail, wc, grep, find, pwd, echo, mkdir, touch, git)", 127)
        }
    }

    private suspend fun resolveUri(path: String): Uri? {
        val safePath = if (path == "." || path.isEmpty()) "" else path
        if (safePath.isNotEmpty() && !PathSafety.isValidRelativePath(safePath)) return null
        return if (safePath.isEmpty()) rootUri else fileSystem.resolveRelativeUri(rootUri, safePath)
    }

    private suspend fun executeLs(cmd: ParsedCommand): TerminalResult {
        val targetPath = cmd.args.firstOrNull() ?: "."
        val targetUri = resolveUri(targetPath) ?: return TerminalResult.Error("ls: cannot access '$targetPath': No such file or directory", 2)

        val isLong = 'l' in cmd.flags
        val isAll = 'a' in cmd.flags

        val children = fileSystem.listChildren(targetUri)
        val filtered = if (isAll) children else children.filter { !it.name.startsWith(".") }
        val sorted = filtered.sortedWith(compareBy<FileNode> { !it.isDirectory }.thenBy { it.name.lowercase() })

        if (sorted.isEmpty()) {
            return TerminalResult.Success("", 0)
        }

        val output = if (isLong) {
            sorted.joinToString("\n") { node ->
                val type = if (node.isDirectory) "d" else "-"
                val size = node.size?.toString() ?: "0"
                String.format("%srwxr-xr-x  1 user  group  %8s %s", type, size, node.name)
            }
        } else {
            sorted.joinToString("\n") { it.name }
        }

        return TerminalResult.Success(output, 0)
    }

    private suspend fun executeCat(cmd: ParsedCommand): TerminalResult {
        if (cmd.args.isEmpty()) {
            return TerminalResult.Error("cat: missing file operand", 1)
        }
        val showLineNumbers = 'n' in cmd.flags
        val sb = StringBuilder()

        for (filePath in cmd.args) {
            val fileUri = resolveUri(filePath) ?: return TerminalResult.Error("cat: $filePath: No such file or directory", 1)
            val content = fileSystem.readTextFile(fileUri) ?: return TerminalResult.Error("cat: $filePath: Cannot read file or is binary", 1)

            if (showLineNumbers) {
                val lines = content.lines()
                lines.forEachIndexed { idx, line ->
                    sb.append(String.format("%6d  %s\n", idx + 1, line))
                }
            } else {
                sb.append(content)
                if (!content.endsWith("\n")) sb.append("\n")
            }
        }

        return TerminalResult.Success(sb.toString().trimEnd(), 0)
    }

    private suspend fun executeHead(cmd: ParsedCommand): TerminalResult {
        val raw = cmd.rawArgs
        if (raw.isEmpty()) return TerminalResult.Error("head: missing file operand", 1)
        var count = 10
        var filePath: String? = null

        var i = 0
        while (i < raw.size) {
            val token = raw[i]
            if (token == "-n" && i + 1 < raw.size) {
                count = raw[i + 1].toIntOrNull() ?: count
                i += 2
            } else if (token.startsWith("-") && token.drop(1).all { it.isDigit() }) {
                count = token.drop(1).toIntOrNull() ?: count
                i++
            } else if (token.all { it.isDigit() } && i == 0 && raw.size > 1) {
                count = token.toIntOrNull() ?: count
                i++
            } else if (!token.startsWith("-")) {
                filePath = token
                i++
            } else {
                i++
            }
        }

        if (filePath == null) return TerminalResult.Error("head: missing file operand", 1)
        val fileUri = resolveUri(filePath) ?: return TerminalResult.Error("head: $filePath: No such file or directory", 1)
        val content = fileSystem.readTextFile(fileUri) ?: return TerminalResult.Error("head: $filePath: Cannot read file", 1)
        val lines = content.lines().take(count)
        return TerminalResult.Success(lines.joinToString("\n"), 0)
    }

    private suspend fun executeTail(cmd: ParsedCommand): TerminalResult {
        val raw = cmd.rawArgs
        if (raw.isEmpty()) return TerminalResult.Error("tail: missing file operand", 1)
        var count = 10
        var filePath: String? = null

        var i = 0
        while (i < raw.size) {
            val token = raw[i]
            if (token == "-n" && i + 1 < raw.size) {
                count = raw[i + 1].toIntOrNull() ?: count
                i += 2
            } else if (token.startsWith("-") && token.drop(1).all { it.isDigit() }) {
                count = token.drop(1).toIntOrNull() ?: count
                i++
            } else if (token.all { it.isDigit() } && i == 0 && raw.size > 1) {
                count = token.toIntOrNull() ?: count
                i++
            } else if (!token.startsWith("-")) {
                filePath = token
                i++
            } else {
                i++
            }
        }

        if (filePath == null) return TerminalResult.Error("tail: missing file operand", 1)
        val fileUri = resolveUri(filePath) ?: return TerminalResult.Error("tail: $filePath: No such file or directory", 1)
        val content = fileSystem.readTextFile(fileUri) ?: return TerminalResult.Error("tail: $filePath: Cannot read file", 1)
        val lines = content.lines().takeLast(count)
        return TerminalResult.Success(lines.joinToString("\n"), 0)
    }

    private suspend fun executeWc(cmd: ParsedCommand): TerminalResult {
        val filePath = cmd.args.firstOrNull() ?: return TerminalResult.Error("wc: missing file operand", 1)
        val fileUri = resolveUri(filePath) ?: return TerminalResult.Error("wc: $filePath: No such file or directory", 1)
        val content = fileSystem.readTextFile(fileUri) ?: return TerminalResult.Error("wc: $filePath: Cannot read file", 1)

        val lines = content.lines().size
        val words = content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val bytes = content.toByteArray(Charsets.UTF_8).size

        val onlyLines = 'l' in cmd.flags
        val onlyWords = 'w' in cmd.flags
        val onlyBytes = 'c' in cmd.flags

        val output = when {
            onlyLines -> "$lines $filePath"
            onlyWords -> "$words $filePath"
            onlyBytes -> "$bytes $filePath"
            else -> String.format("%7d %7d %7d %s", lines, words, bytes, filePath)
        }

        return TerminalResult.Success(output, 0)
    }

    private suspend fun executeGrep(cmd: ParsedCommand): TerminalResult {
        if (cmd.args.isEmpty()) {
            return TerminalResult.Error("grep: missing search pattern", 2)
        }
        val pattern = cmd.args[0]
        val targetPath = cmd.args.getOrNull(1) ?: "."
        val targetUri = resolveUri(targetPath) ?: return TerminalResult.Error("grep: $targetPath: No such file or directory", 2)

        val isRecursive = 'r' in cmd.flags || 'R' in cmd.flags
        val isLineNum = 'n' in cmd.flags
        val isIgnoreCase = 'i' in cmd.flags

        val regexOption = if (isIgnoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val regex = try {
            pattern.toRegex(regexOption)
        } catch (_: Exception) {
            Regex.escape(pattern).toRegex(regexOption)
        }

        val results = mutableListOf<String>()

        suspend fun searchFile(uri: Uri, displayPath: String) {
            val content = fileSystem.readTextFile(uri) ?: return
            content.lines().forEachIndexed { index, line ->
                if (regex.containsMatchIn(line)) {
                    val prefix = if (isLineNum) "$displayPath:${index + 1}:" else "$displayPath:"
                    results.add("$prefix$line")
                }
            }
        }

        suspend fun searchDir(dirUri: Uri, prefixPath: String) {
            val children = fileSystem.listChildren(dirUri)
            for (child in children) {
                if (com.codeagent.core.files.PathSafety.shouldIgnore(child.name, child.isDirectory)) continue
                val childDisplay = if (prefixPath.isEmpty()) child.name else "$prefixPath/${child.name}"
                if (child.isDirectory && isRecursive) {
                    searchDir(child.uri, childDisplay)
                } else if (!child.isDirectory) {
                    searchFile(child.uri, childDisplay)
                }
            }
        }

        val exists = fileSystem.exists(targetUri)
        if (!exists) {
            return TerminalResult.Error("grep: $targetPath: No such file or directory", 2)
        }

        val targetContent = fileSystem.readTextFile(targetUri)
        if (targetContent != null) {
            // It's a single file
            targetContent.lines().forEachIndexed { idx, line ->
                if (regex.containsMatchIn(line)) {
                    val linePrefix = if (isLineNum) "${idx + 1}:" else ""
                    results.add("$linePrefix$line")
                }
            }
        } else {
            // It's a directory
            searchDir(targetUri, if (targetPath == ".") "" else targetPath)
        }

        return if (results.isEmpty()) {
            TerminalResult.Success("", 1) // grep returns 1 on no match
        } else {
            TerminalResult.Success(results.take(200).joinToString("\n"), 0)
        }
    }

    private suspend fun executeFind(cmd: ParsedCommand): TerminalResult {
        val startPath = cmd.args.firstOrNull { !it.startsWith("-") } ?: "."
        val startUri = resolveUri(startPath) ?: return TerminalResult.Error("find: '$startPath': No such file or directory", 1)

        var namePattern: String? = null
        for (i in cmd.args.indices) {
            if (cmd.args[i] == "-name" && i + 1 < cmd.args.size) {
                namePattern = cmd.args[i + 1]
            }
        }

        val matches = mutableListOf<String>()

        suspend fun scan(dirUri: Uri, relPrefix: String) {
            val children = fileSystem.listChildren(dirUri)
            for (child in children) {
                if (com.codeagent.core.files.PathSafety.shouldIgnore(child.name, child.isDirectory)) continue
                val display = if (relPrefix.isEmpty()) child.name else "$relPrefix/${child.name}"
                val match = namePattern == null || matchesPattern(child.name, namePattern)
                if (match) {
                    matches.add(display)
                }
                if (child.isDirectory) {
                    scan(child.uri, display)
                }
            }
        }

        scan(startUri, if (startPath == ".") "" else startPath)
        return TerminalResult.Success(matches.take(300).joinToString("\n"), 0)
    }

    private fun matchesPattern(name: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return name.matches(regex.toRegex(RegexOption.IGNORE_CASE))
    }

    private fun executePwd(): TerminalResult {
        val path = if (workingDirName.isNotBlank()) "/$workingDirName" else "/project"
        return TerminalResult.Success(path, 0)
    }

    private fun executeEcho(cmd: ParsedCommand): TerminalResult {
        return TerminalResult.Success(cmd.args.joinToString(" "), 0)
    }

    private suspend fun executeMkdir(cmd: ParsedCommand): TerminalResult {
        val dirPath = cmd.args.firstOrNull() ?: return TerminalResult.Error("mkdir: missing operand", 1)
        if (!PathSafety.isValidRelativePath(dirPath)) return TerminalResult.Error("mkdir: invalid path: $dirPath", 1)

        val parts = dirPath.split("/").filter { it.isNotBlank() }
        var currentUri = rootUri
        for (part in parts) {
            val resolved = fileSystem.resolveRelativeUri(currentUri, part)
            if (resolved != null && fileSystem.exists(resolved)) {
                currentUri = resolved
            } else {
                val created = fileSystem.createDirectory(currentUri, part)
                    ?: return TerminalResult.Error("mkdir: cannot create directory '$dirPath'", 1)
                currentUri = created
            }
        }
        return TerminalResult.Success("", 0)
    }

    private suspend fun executeTouch(cmd: ParsedCommand): TerminalResult {
        val filePath = cmd.args.firstOrNull() ?: return TerminalResult.Error("touch: missing file operand", 1)
        if (!PathSafety.isValidRelativePath(filePath)) return TerminalResult.Error("touch: invalid path: $filePath", 1)

        val existing = resolveUri(filePath)
        if (existing != null && fileSystem.exists(existing)) {
            return TerminalResult.Success("", 0)
        }

        val parentPath = filePath.substringBeforeLast('/', "")
        val fileName = filePath.substringAfterLast('/')
        val parentUri = if (parentPath.isEmpty()) rootUri else resolveUri(parentPath)
            ?: return TerminalResult.Error("touch: cannot touch '$filePath': No such file or directory", 1)

        fileSystem.createFile(parentUri, fileName, "text/plain")
            ?: return TerminalResult.Error("touch: failed to create file '$filePath'", 1)

        return TerminalResult.Success("", 0)
    }
}
