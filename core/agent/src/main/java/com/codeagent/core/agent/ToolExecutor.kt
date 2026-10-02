package com.codeagent.core.agent

import android.net.Uri
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.files.PathSafety
import com.codeagent.core.model.PendingChange
import com.codeagent.core.model.ChangeType
import com.codeagent.core.model.ToolNames
import com.codeagent.core.terminal.TerminalExecutor
import com.codeagent.core.terminal.DefaultTerminalExecutor
import com.codeagent.core.terminal.TerminalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(
    val success: Boolean,
    val output: String,
    val pendingChange: PendingChange? = null
)

@Singleton
class ToolExecutor @Inject constructor(
    private val terminalExecutor: TerminalExecutor
) {
    // Secondary constructor for lightweight instantiations or tests
    constructor() : this(DefaultTerminalExecutor())

    private var fileSystem: ProjectFileSystem? = null
    private var projectRootUri: Uri? = null

    fun bind(fileSystem: ProjectFileSystem, rootUri: Uri, localWorkDir: File? = null) {
        this.fileSystem = fileSystem
        this.projectRootUri = rootUri
        val resolvedLocalDir = localWorkDir ?: run {
            val path = rootUri.path ?: rootUri.toString().removePrefix("file://")
            val f = File(path)
            if (f.exists()) f else null
        }
        terminalExecutor.bind(fileSystem, rootUri, resolvedLocalDir)
    }

    fun unbind() {
        this.fileSystem = null
        this.projectRootUri = null
        terminalExecutor.unbind()
    }

    private fun normalizeAgentPath(rawPath: String): String? {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty() || trimmed == ".") return "."

        val rootPath = projectRootUri?.let { uri ->
            val path = uri.path ?: uri.toString()
            if (path.startsWith("file://")) path.removePrefix("file://") else path
        }

        val withoutRoot = if (rootPath != null && trimmed.startsWith(rootPath)) {
            trimmed.removePrefix(rootPath).removePrefix("/")
        } else {
            trimmed.removePrefix("./").removePrefix("/")
        }

        val target = if (withoutRoot.isEmpty()) "." else withoutRoot
        return if (target == "." || PathSafety.isValidRelativePath(target)) target else null
    }

    suspend fun execute(
        name: String,
        argumentsJson: String,
        onOutput: ((String) -> Unit)? = null
    ): ToolResult = withContext(Dispatchers.IO) {
        val fs = fileSystem
        if (fs == null) {
            return@withContext ToolResult(false, "No project is open. Please open a project first.")
        }

        val args = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            return@withContext ToolResult(false, "Invalid JSON arguments: $argumentsJson")
        }

        val cleanName = ToolNames.normalize(name)
        when (cleanName) {
            ToolNames.LIST_FILES -> executeListFiles(fs, args)
            ToolNames.READ_FILE -> executeReadFile(fs, args)
            ToolNames.SEARCH_CODE -> executeSearchCode(fs, args)
            ToolNames.GET_FILE_SUMMARY -> executeGetFileSummary(fs, args)
            ToolNames.PROPOSE_FILE_EDIT -> executeProposeEdit(fs, args)
            ToolNames.CREATE_FILE -> executeCreateFile(fs, args)
            ToolNames.DELETE_FILE -> executeDeleteFile(fs, args)
            ToolNames.EXECUTE_COMMAND -> executeCommand(args, onOutput)
            else -> ToolResult(false, "Unknown tool: $name")
        }
    }

    private fun argString(args: JsonObject, key: String): String? {
        return args[key]?.jsonPrimitive?.content
    }

    private fun argInt(args: JsonObject, key: String): Int? {
        return args[key]?.jsonPrimitive?.content?.toIntOrNull()
    }

    private suspend fun executeListFiles(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val rawPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val safePath = normalizeAgentPath(rawPath) ?: return ToolResult(false, "Invalid path: $rawPath")

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val targetUri = if (safePath == ".") rootUri else {
            fs.resolveRelativeUri(rootUri, safePath) ?: return ToolResult(false, "Cannot resolve path: $rawPath")
        }

        val children = fs.listChildren(targetUri)
        val listing = children.joinToString("\n") { node ->
            val prefix = if (node.isDirectory) "DIR " else "    "
            val size = if (!node.isDirectory && node.size != null) " (${node.size}B)" else ""
            "${prefix}${node.name}$size"
        }
        return ToolResult(true, if (listing.isEmpty()) "(empty directory)" else listing)
    }

    private suspend fun executeReadFile(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val rawPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val relPath = normalizeAgentPath(rawPath) ?: return ToolResult(false, "Invalid path: $rawPath")

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $rawPath")

        // 1. Enforce 100 MB max file size limit
        val size = fs.fileSize(fileUri)
        if (size != null && size > MAX_FILE_SIZE_BYTES) {
            val sizeMb = size / (1024 * 1024)
            return ToolResult(
                false,
                "File '$relPath' exceeds maximum allowed size (${sizeMb} MB > 100 MB). Files larger than 100 MB cannot be opened."
            )
        }

        val content = fs.readTextFile(fileUri)
            ?: return ToolResult(false, "Cannot read file: $relPath (may be binary or unreadable)")

        if (content.isEmpty()) {
            return ToolResult(true, "(0 lines)\n(empty file)")
        }

        val allLines = content.lines()
        val totalLines = allLines.size
        val requestedStart = argInt(args, "start_line")
        val requestedEnd = argInt(args, "end_line")

        // 2. Line bounds & 800 lines limit
        val startLine = (requestedStart ?: 1).coerceAtLeast(1)
        if (startLine > totalLines && totalLines > 0) {
            return ToolResult(
                true,
                "(0 lines shown of $totalLines total)\n[start_line $startLine is beyond end of file ($totalLines lines total).]"
            )
        }

        val maxAllowedEnd = startLine + MAX_LINES_PER_READ - 1
        val targetEnd = requestedEnd ?: if (requestedStart != null) totalLines else minOf(totalLines, MAX_LINES_PER_READ)

        var lineTruncated = false
        val endLine = if (targetEnd > maxAllowedEnd) {
            lineTruncated = true
            maxAllowedEnd.coerceAtMost(totalLines)
        } else {
            targetEnd.coerceAtMost(totalLines)
        }

        if (requestedEnd == null && requestedStart == null && totalLines > MAX_LINES_PER_READ) {
            lineTruncated = true
        }

        val startIndex = (startLine - 1).coerceIn(0, totalLines)
        val endIndex = endLine.coerceIn(startIndex, totalLines)
        val slicedLines = allLines.subList(startIndex, endIndex)
        val linesText = slicedLines.joinToString("\n")

        // 3. Byte limit: 45 KB (46,080 bytes)
        var byteTruncated = false
        var outputText = linesText
        val utf8Bytes = linesText.toByteArray(Charsets.UTF_8)
        if (utf8Bytes.size > MAX_BYTES_PER_READ) {
            byteTruncated = true
            val truncatedString = String(utf8Bytes, 0, MAX_BYTES_PER_READ, Charsets.UTF_8)
            val lastNewline = truncatedString.lastIndexOf('\n')
            outputText = if (lastNewline > MAX_BYTES_PER_READ / 2) {
                truncatedString.substring(0, lastNewline)
            } else {
                truncatedString
            }
        }

        val displayedLineCount = outputText.lines().size
        val notice = buildString {
            if (byteTruncated) {
                append("\n\n[Content truncated at 45 KB (46,080 bytes) limit. Showing lines $startLine to ${startLine + displayedLineCount - 1} of $totalLines total lines. Specify a narrower line range using 'start_line' and 'end_line'.]")
            } else if (lineTruncated) {
                val nextStart = endLine + 1
                append("\n\n[Showing lines $startLine to $endLine of $totalLines total lines (capped at $MAX_LINES_PER_READ lines per read). Specify 'start_line=$nextStart' to view remaining lines.]")
            }
        }

        val header = if (lineTruncated || byteTruncated || requestedStart != null || requestedEnd != null) {
            "($displayedLineCount lines shown of $totalLines total)"
        } else {
            "($totalLines lines)"
        }

        return ToolResult(true, "$header\n$outputText$notice")
    }

    private suspend fun executeSearchCode(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val query = argString(args, "query") ?: return ToolResult(false, "Missing 'query' argument")
        val filePattern = argString(args, "file_pattern")

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val results = mutableListOf<String>()
        searchRecursive(fs, rootUri, query, filePattern, results, depth = 0, maxDepth = 10, maxResults = 30)

        return ToolResult(true, if (results.isEmpty()) "No matches found" else results.joinToString("\n"))
    }

    private suspend fun searchRecursive(
        fs: ProjectFileSystem,
        uri: Uri,
        query: String,
        filePattern: String?,
        results: MutableList<String>,
        depth: Int,
        maxDepth: Int,
        maxResults: Int,
        currentPath: String = ""
    ) {
        if (depth > maxDepth || results.size >= maxResults) return

        val children = fs.listChildren(uri)
        for (child in children) {
            if (results.size >= maxResults) break
            if (PathSafety.shouldIgnore(child.name, child.isDirectory)) continue

            val childPath = if (currentPath.isEmpty()) child.name else "$currentPath/${child.name}"

            if (child.isDirectory) {
                searchRecursive(fs, child.uri, query, filePattern, results, depth + 1, maxDepth, maxResults, childPath)
            } else {
                if (filePattern != null && !globMatch(filePattern, child.name)) continue
                val childSize = child.size
                if (childSize != null && childSize > 500_000) continue

                val content = fs.readTextFile(child.uri) ?: continue
                val lines = content.lines()
                for ((i, line) in lines.withIndex()) {
                    if (line.contains(query, ignoreCase = true)) {
                        results.add("$childPath:${i + 1}: ${line.trim().take(120)}")
                        if (results.size >= maxResults) return
                    }
                }
            }
        }
    }

    private fun globMatch(pattern: String, name: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regex).matches(name)
    }

    private suspend fun executeGetFileSummary(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val rawPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val relPath = normalizeAgentPath(rawPath) ?: return ToolResult(false, "Invalid path: $rawPath")

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $rawPath")

        val content = fs.readTextFile(fileUri)
            ?: return ToolResult(false, "Cannot read file: $rawPath")

        val lines = content.lines()
        val lineCount = lines.size
        val lang = languageFromPath(relPath)
        val head = lines.take(10).joinToString("\n")
        val tail = if (lineCount > 20) lines.takeLast(5).joinToString("\n") else ""

        val summary = buildString {
            append("File: $relPath\n")
            append("Language: $lang\n")
            append("Lines: $lineCount\n")
            append("Size: ${content.length} chars\n")
            append("\n--- First 10 lines ---\n")
            append(head)
            if (tail.isNotEmpty()) {
                append("\n\n--- Last 5 lines ---\n")
                append(tail)
            }
        }
        return ToolResult(true, summary)
    }

    private suspend fun executeProposeEdit(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val rawPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val newContent = argString(args, "content")
            ?: argString(args, "new_content")
            ?: argString(args, "replacement")
            ?: return ToolResult(false, "Missing 'content' argument")
        val oldContent = argString(args, "old_content")
            ?: argString(args, "target")
            ?: argString(args, "target_content")
            ?: argString(args, "search")

        val relPath = normalizeAgentPath(rawPath) ?: return ToolResult(false, "Invalid path: $rawPath")

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $rawPath")

        val originalContent = fs.readTextFile(fileUri) ?: ""

        val resolveResult = EditResolver.resolveEdit(
            originalContent = originalContent,
            newContent = newContent,
            oldContent = oldContent
        )

        if (!resolveResult.success) {
            return ToolResult(false, resolveResult.errorMessage ?: "Failed to resolve edit")
        }

        val proposedContent = resolveResult.proposedContent

        val change = PendingChange(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = "",
            filePath = relPath,
            changeType = ChangeType.EDIT,
            originalContent = originalContent,
            proposedContent = proposedContent,
            createdAt = System.currentTimeMillis()
        )

        return ToolResult(
            success = true,
            output = "Edit proposed for $relPath. Awaiting user approval.",
            pendingChange = change
        )
    }

    private suspend fun executeCreateFile(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val rawPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val content = argString(args, "content") ?: return ToolResult(false, "Missing 'content' argument")

        val relPath = normalizeAgentPath(rawPath) ?: return ToolResult(false, "Invalid path: $rawPath")

        val change = PendingChange(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = "",
            filePath = relPath,
            changeType = ChangeType.CREATE,
            originalContent = null,
            proposedContent = content,
            createdAt = System.currentTimeMillis()
        )

        return ToolResult(
            success = true,
            output = "File creation proposed for $relPath. Awaiting user approval.",
            pendingChange = change
        )
    }

    private suspend fun executeDeleteFile(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val rawPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")

        val relPath = normalizeAgentPath(rawPath) ?: return ToolResult(false, "Invalid path: $rawPath")

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $rawPath")

        val originalContent = fs.readTextFile(fileUri)

        val change = PendingChange(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = "",
            filePath = relPath,
            changeType = ChangeType.DELETE,
            originalContent = originalContent,
            proposedContent = null,
            createdAt = System.currentTimeMillis()
        )

        return ToolResult(
            success = true,
            output = "File deletion proposed for $relPath. Awaiting user approval.",
            pendingChange = change
        )
    }

    private fun languageFromPath(path: String): String {
        val ext = path.substringAfterLast('.', "")
        return when (ext.lowercase()) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "py" -> "Python"
            "js" -> "JavaScript"
            "ts", "tsx" -> "TypeScript"
            "jsx" -> "JSX"
            "rs" -> "Rust"
            "go" -> "Go"
            "c", "h" -> "C"
            "cpp", "cc", "cxx", "hpp" -> "C++"
            "rb" -> "Ruby"
            "swift" -> "Swift"
            "xml" -> "XML"
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            "json" -> "JSON"
            "yaml", "yml" -> "YAML"
            "toml" -> "TOML"
            "md" -> "Markdown"
            "sh", "bash" -> "Shell"
            "sql" -> "SQL"
            "gradle", "gradle.kts" -> "Gradle"
            else -> "Unknown ($ext)"
        }
    }

    private suspend fun executeCommand(args: JsonObject, onOutput: ((String) -> Unit)? = null): ToolResult {
        val command = argString(args, "command") ?: return ToolResult(false, "Missing 'command' argument")
        return when (val res = terminalExecutor.execute(command, onOutput)) {
            is TerminalResult.Success -> ToolResult(true, if (res.output.isEmpty()) "(command executed successfully with exit code 0)" else res.output)
            is TerminalResult.Error -> ToolResult(false, res.message)
            is TerminalResult.Disabled -> ToolResult(false, "Terminal execution is disabled")
        }
    }

    companion object {
        const val MAX_LINES_PER_READ = 800
        const val MAX_BYTES_PER_READ = 46_080 // 45 KB (46,080 bytes)
        const val MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB
    }
}
