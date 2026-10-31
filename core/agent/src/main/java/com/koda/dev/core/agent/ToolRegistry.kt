package com.koda.dev.core.agent

import com.koda.dev.core.ai.ToolSpec
import com.koda.dev.core.ai.ToolParameters
import com.koda.dev.core.ai.PropertyDef

import com.koda.dev.core.model.ToolNames

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
                description = "Execute a shell, git, or build command in the workspace directory. Commands run in an Ubuntu 24.04 LTS (glibc) environment with bash and full access to apt (e.g. 'apt update && apt install -y <pkg>'), GCC/G++, Python 3, OpenJDK 21, Node.js, Rust, Go, Make, CMake, and build tools. If a command takes longer than wait_seconds (default 20s), it automatically moves to a background task so you regain control. Use is_background: true for long-running servers or watchers.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "command" to PropertyDef("string", "The shell command line to execute (e.g. 'git status', './build.sh', 'python3 main.py', 'gcc -o app main.c', 'apt install -y <pkg>')."),
                        "wait_seconds" to PropertyDef("integer", "Seconds to wait synchronously before detaching into a background task (default 20)."),
                        "is_background" to PropertyDef("boolean", "Set to true for long-running daemons or dev servers that should immediately run in the background.")
                    ),
                    required = listOf("command")
                )
            ),
            readOnly = false
        )
        register(
            ToolSpec(
                name = ToolNames.MANAGE_TASK,
                description = "Manage active and completed background tasks. Use action='status' to inspect real-time stdout/stderr, runtime, and stall warnings. Use action='kill' to terminate a stuck, looping, or unneeded task. Use action='send_input' to provide interactive input to a running task. Use action='list' to see all tasks.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "action" to PropertyDef("string", "The task action to perform: 'status' (get recent logs & status), 'kill' (terminate process), 'send_input' (write to stdin), or 'list' (list all active/recent tasks)."),
                        "task_id" to PropertyDef("string", "The task ID (e.g. 'task_1') returned when a command was backgrounded. Required for 'status', 'kill', and 'send_input'."),
                        "input" to PropertyDef("string", "The text input to send to the task's standard input. Required for 'send_input'.")
                    ),
                    required = listOf("action")
                )
            ),
            readOnly = false
        )
        register(
            ToolSpec(
                name = ToolNames.WEB_SEARCH,
                description = "Search the web for up-to-date documentation, APIs, library releases, guides, and error solutions. Returns search results with titles, URLs, and concise snippets.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to PropertyDef("string", "Search terms or keywords (e.g. 'jetpack compose navigation 3 example', 'ktor 3.0 migration guide')."),
                        "limit" to PropertyDef("integer", "Optional maximum number of search results to return (1-10, default 5).")
                    ),
                    required = listOf("query")
                )
            ),
            readOnly = true
        )
        register(
            ToolSpec(
                name = ToolNames.FETCH_WEB_PAGE,
                description = "Fetch and read the content of a web page URL. Converts HTML to clean, readable Markdown, stripping navigation, ads, and boilerplate. Supports pagination via start_line and end_line for large pages.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "url" to PropertyDef("string", "The HTTP or HTTPS URL to fetch (e.g. 'https://developer.android.com/...')."),
                        "start_line" to PropertyDef("integer", "Optional 1-based start line for pagination (inclusive). Defaults to 1."),
                        "end_line" to PropertyDef("integer", "Optional 1-based end line for pagination (inclusive). Maximum 600 lines per call.")
                    ),
                    required = listOf("url")
                )
            ),
            readOnly = true
        )
    }

    init {
        populateDefaults()
    }
}
