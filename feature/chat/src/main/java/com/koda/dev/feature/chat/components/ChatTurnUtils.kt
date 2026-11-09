package com.koda.dev.feature.chat.components

import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.Message
import com.koda.dev.core.model.MessageRole
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.model.ToolCallData
import com.koda.dev.core.model.ToolNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Consolidates consecutive assistant messages in a single turn so that intermediate tool-execution
 * steps are grouped together with the final turn response, preventing multiple redundant assistant
 * bubbles from cluttering the chat history.
 */
fun groupMessagesIntoTurns(rawMessages: List<Message>): List<Message> {
    val nonToolMessages = rawMessages.filter { it.role != MessageRole.TOOL }
    if (nonToolMessages.isEmpty()) return emptyList()

    val result = mutableListOf<Message>()
    val currentAssistantBatch = mutableListOf<Message>()

    fun flushAssistantBatch() {
        if (currentAssistantBatch.isEmpty()) return
        if (currentAssistantBatch.size == 1) {
            result.add(currentAssistantBatch.first())
        } else {
            // Combine all tool calls in chronological order
            val allToolCalls = currentAssistantBatch.flatMap { it.toolCalls }

            // Join non-blank content in chronological order, deduplicating consecutive identical thoughts
            val nonBlankContents = currentAssistantBatch
                .map { it.content.trim() }
                .filter { it.isNotBlank() }

            val distinctContents = mutableListOf<String>()
            for (c in nonBlankContents) {
                if (distinctContents.isEmpty() || distinctContents.last() != c) {
                    distinctContents.add(c)
                }
            }

            // If there are many intermediate progress updates, keep the initial intent and latest progress/answer
            val compactedContents = if (distinctContents.size > 3) {
                listOf(
                    distinctContents.first(),
                    "... (${distinctContents.size - 2} intermediate progress updates) ...",
                    distinctContents.last()
                )
            } else {
                distinctContents
            }

            val combinedContent = compactedContents.joinToString("\n\n")

            val firstMsg = currentAssistantBatch.first()
            val lastMsg = currentAssistantBatch.last()

            // Use firstMsg.id for stable compose identity across streaming steps,
            // while updating toolCalls, content, and timestamp to reflect the full turn.
            result.add(
                firstMsg.copy(
                    content = combinedContent,
                    toolCalls = allToolCalls,
                    timestamp = lastMsg.timestamp
                )
            )
        }
        currentAssistantBatch.clear()
    }

    for (msg in nonToolMessages) {
        if (msg.role == MessageRole.ASSISTANT) {
            currentAssistantBatch.add(msg)
        } else {
            flushAssistantBatch()
            result.add(msg)
        }
    }
    flushAssistantBatch()

    return result
}

/**
 * Formats a unix timestamp into a relative time string (e.g., "Just now", "2m ago", "1h ago").
 */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return ""
    val diff = maxOf(0L, now - timestamp)
    val seconds = diff / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> "${days / 7L}w ago"
    }
}

/**
 * Formats elapsed execution duration in milliseconds into a friendly string (e.g. "Worked for 1m", "Worked for 8s").
 */
fun formatWorkedDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "Worked for 1s"
    val seconds = durationMs / 1000L
    val minutes = seconds / 60L
    return when {
        minutes >= 1L -> "Worked for ${minutes}m"
        seconds > 0L -> "Worked for ${seconds}s"
        else -> "Worked for 1s"
    }
}

data class DiffStats(
    val filesCount: Int,
    val additions: Int,
    val deletions: Int
)

/**
 * Calculates modified files count, added lines (+X) and deleted lines (-Y) from pending changes.
 */
fun calculateDiffStats(pendingChanges: List<PendingChange>): DiffStats {
    if (pendingChanges.isEmpty()) return DiffStats(0, 0, 0)

    val distinctFiles = pendingChanges.map { it.filePath }.distinct()
    var additions = 0
    var deletions = 0

    for (change in pendingChanges) {
        val orig = change.originalContent
        val prop = change.proposedContent

        when {
            change.changeType == ChangeType.CREATE || orig == null -> {
                additions += prop?.lines()?.size ?: 0
            }
            change.changeType == ChangeType.DELETE || prop == null -> {
                deletions += orig.lines().size
            }
            else -> {
                val origLines = orig.lines()
                val propLines = prop.lines()
                val origSet = origLines.toSet()
                val propSet = propLines.toSet()

                val added = propLines.count { it !in origSet }
                val deleted = origLines.count { it !in propSet }

                if (added == 0 && deleted == 0 && orig != prop) {
                    additions += 1
                    deletions += 1
                } else {
                    additions += added
                    deletions += deleted
                }
            }
        }
    }

    return DiffStats(
        filesCount = distinctFiles.size,
        additions = additions,
        deletions = deletions
    )
}

data class HumanAction(
    val id: String,
    val category: String,
    val title: String,
    val commandLine: String? = null,
    val output: String? = null,
    val isTerminal: Boolean = false,
    val isError: Boolean = false,
    val durationMs: Long? = null
)

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Translates a low-level ToolCallData into a human-friendly timeline step.
 */
fun toHumanAction(toolCall: ToolCallData, workingDirectory: String = "~/code_agent"): HumanAction {
    val normName = ToolNames.normalize(toolCall.name)
    val argsJson = try {
        jsonParser.parseToJsonElement(toolCall.arguments).jsonObject
    } catch (_: Exception) {
        null
    }

    val isErr = toolCall.success == false || (toolCall.result?.startsWith("Error:") == true)
    val rawResult = toolCall.result ?: ""

    return when (normName) {
        "execute_command", "run_command", "bash", "exec_command", ToolNames.EXECUTE_COMMAND -> {
            val toolSummary = argsJson?.get("toolSummary")?.jsonPrimitive?.content
                ?: argsJson?.get("summary")?.jsonPrimitive?.content
                ?: argsJson?.get("description")?.jsonPrimitive?.content
            val cmd = argsJson?.get("command")?.jsonPrimitive?.content
                ?: argsJson?.get("CommandLine")?.jsonPrimitive?.content
                ?: argsJson?.get("cmd")?.jsonPrimitive?.content
                ?: argsJson?.get("commandLine")?.jsonPrimitive?.content
                ?: if (toolCall.arguments.isNotBlank() && !toolCall.arguments.trim().startsWith("{")) toolCall.arguments.trim() else ""

            val isGenericSummary = toolSummary.isNullOrBlank() ||
                toolSummary.equals("execute command", ignoreCase = true) ||
                toolSummary.equals("execute_command", ignoreCase = true) ||
                toolSummary.equals("run command", ignoreCase = true) ||
                toolSummary.equals("run_command", ignoreCase = true) ||
                toolSummary.equals("execute command finished", ignoreCase = true) ||
                toolSummary.equals("run command finished", ignoreCase = true)

            val title = when {
                !isGenericSummary && !toolSummary.isNullOrBlank() -> {
                    val cleaned = toolSummary.trim()
                    if (cleaned.endsWith("finished", ignoreCase = true) || cleaned.endsWith("completed", ignoreCase = true)) {
                        cleaned
                    } else {
                        "Ran ${cleaned.replaceFirstChar { it.lowercase() }}"
                    }
                }
                cmd.isNotBlank() -> {
                    val cleanCmd = cmd.lines().firstOrNull { it.isNotBlank() }?.trim() ?: cmd.trim()
                    val displayCmd = if (cleanCmd.length > 45) cleanCmd.take(42).trim() + "..." else cleanCmd
                    "Ran $displayCmd"
                }
                else -> "Ran terminal command"
            }

            HumanAction(
                id = toolCall.id,
                category = "Ran commands",
                title = title,
                commandLine = cmd.ifBlank { null },
                output = rawResult,
                isTerminal = true,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "read_file" -> {
            val path = argsJson?.get("Path")?.jsonPrimitive?.content
                ?: argsJson?.get("path")?.jsonPrimitive?.content
                ?: ""
            val fileName = path.substringAfterLast('/')
            val title = if (fileName.isNotBlank()) "Read $fileName" else "Read file"

            HumanAction(
                id = toolCall.id,
                category = "Explored codebase",
                title = title,
                commandLine = null,
                output = if (rawResult.length > 500) rawResult.take(500) + "\n... (truncated)" else rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "edit_file", "write_file", ToolNames.PROPOSE_FILE_EDIT, ToolNames.CREATE_FILE -> {
            val path = argsJson?.get("Path")?.jsonPrimitive?.content
                ?: argsJson?.get("path")?.jsonPrimitive?.content
                ?: ""
            val fileName = path.substringAfterLast('/')
            val actionVerb = if (normName == "edit_file" || normName == ToolNames.PROPOSE_FILE_EDIT) "Updated" else "Created"
            val title = if (fileName.isNotBlank()) "$actionVerb $fileName" else "$actionVerb file"

            HumanAction(
                id = toolCall.id,
                category = "Modified files",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "rename_file", ToolNames.RENAME_FILE -> {
            val oldPath = argsJson?.get("oldPath")?.jsonPrimitive?.content
                ?: argsJson?.get("old_path")?.jsonPrimitive?.content ?: ""
            val newPath = argsJson?.get("newPath")?.jsonPrimitive?.content
                ?: argsJson?.get("new_path")?.jsonPrimitive?.content ?: ""
            val oldName = oldPath.substringAfterLast('/')
            val newName = newPath.substringAfterLast('/')
            val title = if (oldName.isNotBlank() && newName.isNotBlank()) "Renamed $oldName → $newName" else "Renamed file"

            HumanAction(
                id = toolCall.id,
                category = "Modified files",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "delete_file", ToolNames.DELETE_FILE -> {
            val path = argsJson?.get("path")?.jsonPrimitive?.content
                ?: argsJson?.get("Path")?.jsonPrimitive?.content ?: ""
            val fileName = path.substringAfterLast('/')
            val title = if (fileName.isNotBlank()) "Deleted $fileName" else "Deleted file"

            HumanAction(
                id = toolCall.id,
                category = "Modified files",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "search_code", "find_files", ToolNames.SEARCH_CODE -> {
            val query = argsJson?.get("query")?.jsonPrimitive?.content
                ?: argsJson?.get("pattern")?.jsonPrimitive?.content
                ?: ""
            val title = if (query.isNotBlank()) "Searched for \"${query.take(30)}\"" else "Searched codebase"

            HumanAction(
                id = toolCall.id,
                category = "Explored codebase",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "list_files", ToolNames.LIST_FILES -> {
            val path = argsJson?.get("path")?.jsonPrimitive?.content
                ?: argsJson?.get("dirPath")?.jsonPrimitive?.content
                ?: ""
            val title = if (path.isNotBlank()) "Listed files in $path" else "Listed files"

            HumanAction(
                id = toolCall.id,
                category = "Explored codebase",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "get_file_summary", ToolNames.GET_FILE_SUMMARY -> {
            val path = argsJson?.get("path")?.jsonPrimitive?.content ?: ""
            val fileName = path.substringAfterLast('/')
            val title = if (fileName.isNotBlank()) "Summarized $fileName" else "Summarized file"

            HumanAction(
                id = toolCall.id,
                category = "Explored codebase",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "manage_task", ToolNames.MANAGE_TASK -> {
            val action = argsJson?.get("action")?.jsonPrimitive?.content ?: ""
            val taskId = argsJson?.get("task_id")?.jsonPrimitive?.content ?: ""
            val title = when {
                action.isNotBlank() && taskId.isNotBlank() -> "Task $taskId: $action"
                action.isNotBlank() -> "Task action: $action"
                else -> "Managed background task"
            }

            HumanAction(
                id = toolCall.id,
                category = "Managed tasks",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "web_search", ToolNames.WEB_SEARCH -> {
            val query = argsJson?.get("query")?.jsonPrimitive?.content ?: ""
            val title = if (query.isNotBlank()) "Web search: \"${query.take(30)}\"" else "Searched web"

            HumanAction(
                id = toolCall.id,
                category = "Web research",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "fetch_web_page", ToolNames.FETCH_WEB_PAGE -> {
            val url = argsJson?.get("url")?.jsonPrimitive?.content ?: ""
            val title = if (url.isNotBlank()) "Fetched ${url.take(35)}" else "Fetched web page"

            HumanAction(
                id = toolCall.id,
                category = "Web research",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        "schedule" -> {
            val duration = argsJson?.get("DurationSeconds")?.jsonPrimitive?.content ?: "5"
            val title = "Timed $duration seconds"
            HumanAction(
                id = toolCall.id,
                category = "Scheduled timers",
                title = title,
                commandLine = null,
                output = rawResult,
                isTerminal = false,
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
        else -> {
            val cmd = argsJson?.get("command")?.jsonPrimitive?.content
                ?: argsJson?.get("CommandLine")?.jsonPrimitive?.content
                ?: argsJson?.get("cmd")?.jsonPrimitive?.content
                ?: argsJson?.get("commandLine")?.jsonPrimitive?.content
            val cleanName = normName.replace('_', ' ').replaceFirstChar { it.uppercase() }
            val title = if (!cmd.isNullOrBlank()) {
                val cleanCmd = cmd.lines().firstOrNull { it.isNotBlank() }?.trim() ?: cmd.trim()
                val displayCmd = if (cleanCmd.length > 45) cleanCmd.take(42).trim() + "..." else cleanCmd
                "Ran $displayCmd"
            } else {
                "$cleanName finished"
            }

            HumanAction(
                id = toolCall.id,
                category = if (!cmd.isNullOrBlank()) "Ran commands" else "Agent actions",
                title = title,
                commandLine = cmd,
                output = rawResult,
                isTerminal = !cmd.isNullOrBlank(),
                isError = isErr,
                durationMs = toolCall.durationMs
            )
        }
    }
}

