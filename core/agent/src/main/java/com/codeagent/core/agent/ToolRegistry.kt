package com.codeagent.core.agent

import com.codeagent.core.ai.ToolSpec
import com.codeagent.core.ai.ToolParameters
import com.codeagent.core.ai.PropertyDef

import com.codeagent.core.model.ToolNames

object ToolRegistry {

    data class RegisteredTool(
        val spec: ToolSpec,
        val readOnly: Boolean
    )

    private val tools = mutableMapOf<String, RegisteredTool>()

    fun register(spec: ToolSpec, readOnly: Boolean = true) {
        tools[spec.name] = RegisteredTool(spec, readOnly)
    }

    fun get(name: String): RegisteredTool? = tools[name] ?: tools[ToolNames.normalize(name)]

    fun allSpecs(): List<ToolSpec> = tools.values.map { it.spec }

    fun isReadOnly(name: String): Boolean = tools[name]?.readOnly ?: tools[ToolNames.normalize(name)]?.readOnly ?: true

    fun populateDefaults() {
        register(
            ToolSpec(
                name = "list_files",
                description = "List files and directories at the given path within the project. Returns file names, types, and sizes.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to PropertyDef("string", "Relative directory path from project root. Use '.' for root.")
                    ),
                    required = listOf("path")
                )
            ),
            readOnly = true
        )
        register(
            ToolSpec(
                name = "read_file",
                description = "Read the contents of a text file in the project. Returns up to 800 lines or 45 KB (46,080 bytes) at a time. If the file is larger, specify 'start_line' and 'end_line' to paginate through subsequent chunks. Maximum supported file size is 100 MB.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to PropertyDef("string", "Relative file path from project root."),
                        "start_line" to PropertyDef("integer", "Optional 1-based start line for pagination (inclusive). Defaults to 1."),
                        "end_line" to PropertyDef("integer", "Optional 1-based end line for pagination (inclusive). Maximum 800 lines per call.")
                    ),
                    required = listOf("path")
                )
            ),
            readOnly = true
        )
        register(
            ToolSpec(
                name = "search_code",
                description = "Search for a text pattern across project files. Returns matching file paths and line numbers.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to PropertyDef("string", "Text or regex pattern to search for."),
                        "file_pattern" to PropertyDef("string", "Optional glob pattern to filter files, e.g. '*.kt'.")
                    ),
                    required = listOf("query")
                )
            ),
            readOnly = true
        )
        register(
            ToolSpec(
                name = "get_file_summary",
                description = "Get a summary of a file: line count, language, and first/last few lines.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to PropertyDef("string", "Relative file path from project root.")
                    ),
                    required = listOf("path")
                )
            ),
            readOnly = true
        )
        register(
            ToolSpec(
                name = "propose_file_edit",
                description = "Propose an edit to an existing file. For partial edits, provide 'old_content' (the exact text to replace) and 'content' (the replacement text). For whole-file changes, provide 'content' with the entire file. The change will be shown as a diff for user approval before applying.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to PropertyDef("string", "Relative file path from project root."),
                        "content" to PropertyDef("string", "The replacement content. If old_content is specified, this replaces only old_content. If old_content is omitted, this must be the COMPLETE file content."),
                        "old_content" to PropertyDef("string", "Optional. The exact existing block of text from the file to replace. Highly recommended for targeted edits to avoid modifying or deleting unchanged code.")
                    ),
                    required = listOf("path", "content")
                )
            ),
            readOnly = false
        )
        register(
            ToolSpec(
                name = "create_file",
                description = "Create a new file in the project. The change will require user approval before applying.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to PropertyDef("string", "Relative file path from project root."),
                        "content" to PropertyDef("string", "Initial content for the new file.")
                    ),
                    required = listOf("path", "content")
                )
            ),
            readOnly = false
        )
        register(
            ToolSpec(
                name = "delete_file",
                description = "Delete a file from the project. Requires explicit user confirmation. Use with extreme caution.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to PropertyDef("string", "Relative file path from project root.")
                    ),
                    required = listOf("path")
                )
            ),
            readOnly = false
        )
        register(
            ToolSpec(
                name = ToolNames.EXECUTE_COMMAND,
                description = "Execute a shell, git, or build command in the workspace directory. Commands run in an Alpine Linux environment with access to git, python3, gcc, g++, make, and the apk package manager (e.g. 'apk add <pkg>').",
                parameters = ToolParameters(
                    properties = mapOf(
                        "command" to PropertyDef("string", "The shell command line to execute (e.g. 'git status', 'python3 main.py', 'gcc -o app main.c', 'apk add <pkg>').")
                    ),
                    required = listOf("command")
                )
            ),
            readOnly = false
        )
    }

    init {
        populateDefaults()
    }
}
