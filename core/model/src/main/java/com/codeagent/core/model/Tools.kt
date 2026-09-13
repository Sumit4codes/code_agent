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
}
