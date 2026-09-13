package com.codeagent.core.agent

import android.net.Uri
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.files.PathSafety
import com.codeagent.core.model.PendingChange
import com.codeagent.core.model.ChangeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(
    val success: Boolean,
    val output: String,
    val pendingChange: PendingChange? = null
)

@Singleton
class ToolExecutor @Inject constructor() {

    private var fileSystem: ProjectFileSystem? = null
    private var projectRootUri: Uri? = null

    fun bind(fileSystem: ProjectFileSystem, rootUri: Uri) {
        this.fileSystem = fileSystem
        this.projectRootUri = rootUri
    }

    fun unbind() {
        this.fileSystem = null
        this.projectRootUri = null
    }

    suspend fun execute(name: String, argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val fs = fileSystem
        if (fs == null) {
            return@withContext ToolResult(false, "No project is open. Please open a project first.")
        }

        val args = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            return@withContext ToolResult(false, "Invalid JSON arguments: $argumentsJson")
        }

        when (name) {
            "list_files" -> executeListFiles(fs, args)
            "read_file" -> executeReadFile(fs, args)
            "search_code" -> executeSearchCode(fs, args)
            "get_file_summary" -> executeGetFileSummary(fs, args)
            "propose_file_edit" -> executeProposeEdit(fs, args)
            "create_file" -> executeCreateFile(fs, args)
            "delete_file" -> executeDeleteFile(fs, args)
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
        val relPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val safePath = if (relPath == ".") "" else relPath
        if (safePath.isNotEmpty() && !PathSafety.isValidRelativePath(safePath)) {
            return ToolResult(false, "Invalid path: $relPath")
        }

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val targetUri = if (safePath.isEmpty()) rootUri else {
            fs.resolveRelativeUri(rootUri, safePath) ?: return ToolResult(false, "Cannot resolve path: $relPath")
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
        val relPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        if (!PathSafety.isValidRelativePath(relPath)) {
            return ToolResult(false, "Invalid path: $relPath")
        }

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $relPath")

        val content = fs.readTextFile(fileUri)
            ?: return ToolResult(false, "Cannot read file: $relPath (may be binary or too large)")

        val startLine = argInt(args, "start_line")
        val endLine = argInt(args, "end_line")

        val result = if (startLine != null || endLine != null) {
            val lines = content.lines()
            val start = ((startLine ?: 1) - 1).coerceIn(0, lines.size)
            val end = (endLine ?: lines.size).coerceIn(0, lines.size)
            lines.subList(start, end).joinToString("\n")
        } else {
            content
        }

        val lineCount = content.lines().size
        return ToolResult(true, "($lineCount lines)\n$result")
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
        val relPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        if (!PathSafety.isValidRelativePath(relPath)) {
            return ToolResult(false, "Invalid path: $relPath")
        }

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $relPath")

        val content = fs.readTextFile(fileUri)
            ?: return ToolResult(false, "Cannot read file: $relPath")

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
        val relPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val newContent = argString(args, "content") ?: return ToolResult(false, "Missing 'content' argument")

        if (!PathSafety.isValidRelativePath(relPath)) {
            return ToolResult(false, "Invalid path: $relPath")
        }

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $relPath")

        val originalContent = fs.readTextFile(fileUri) ?: ""

        val change = PendingChange(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = "",
            filePath = relPath,
            changeType = ChangeType.EDIT,
            originalContent = originalContent,
            proposedContent = newContent,
            createdAt = System.currentTimeMillis()
        )

        return ToolResult(
            success = true,
            output = "Edit proposed for $relPath. Awaiting user approval.",
            pendingChange = change
        )
    }

    private suspend fun executeCreateFile(fs: ProjectFileSystem, args: JsonObject): ToolResult {
        val relPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")
        val content = argString(args, "content") ?: return ToolResult(false, "Missing 'content' argument")

        if (!PathSafety.isValidRelativePath(relPath)) {
            return ToolResult(false, "Invalid path: $relPath")
        }

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
        val relPath = argString(args, "path") ?: return ToolResult(false, "Missing 'path' argument")

        if (!PathSafety.isValidRelativePath(relPath)) {
            return ToolResult(false, "Invalid path: $relPath")
        }

        val rootUri = projectRootUri ?: return ToolResult(false, "No project root")
        val fileUri = fs.resolveRelativeUri(rootUri, relPath)
            ?: return ToolResult(false, "File not found: $relPath")

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
}
