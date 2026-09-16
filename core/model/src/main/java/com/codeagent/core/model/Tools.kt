package com.codeagent.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, PropertyDef> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class PropertyDef(
    val type: String,
    val description: String
)

object ToolNames {
    const val LIST_FILES = "list_files"
    const val READ_FILE = "read_file"
    const val SEARCH_CODE = "search_code"
    const val GET_FILE_SUMMARY = "get_file_summary"
    const val PROPOSE_FILE_EDIT = "propose_file_edit"
    const val CREATE_FILE = "create_file"
    const val RENAME_FILE = "rename_file"
    const val DELETE_FILE = "delete_file"
    const val EXECUTE_COMMAND = "execute_command"

    val ALL = listOf(
        LIST_FILES,
        READ_FILE,
        SEARCH_CODE,
        GET_FILE_SUMMARY,
        PROPOSE_FILE_EDIT,
        CREATE_FILE,
        RENAME_FILE,
        DELETE_FILE,
        EXECUTE_COMMAND
    )

    fun normalize(rawName: String?): String {
        if (rawName == null) return ""
        val trimmed = rawName.trim().removeSurrounding("`").removeSurrounding("\"")
        if (trimmed in ALL) return trimmed

        // Strip Harmony / special token channel markers (e.g. <|channel|>commentary, <|call|>, etc.)
        var cleaned = trimmed.replace(Regex("""<\|.*?\|>.*$"""), "")
            .replace(Regex("""<\|.*?\|>"""), "")
            .trim()

        if (cleaned in ALL) return cleaned

        // Strip prefixes/suffixes like "functions." or "tools:" or ":commentary"
        cleaned = cleaned.substringBefore(":").substringAfterLast(".").trim()
        if (cleaned in ALL) return cleaned

        // Fallback: match known tools by containment (e.g. "propose_file_edit<|channel|>commentary" contains "propose_file_edit")
        val lower = trimmed.lowercase()
        for (tool in ALL) {
            if (lower.contains(tool)) {
                return tool
            }
        }

        return cleaned.ifBlank { trimmed }
    }
}
