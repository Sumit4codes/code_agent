package com.koda.dev.core.agent

import android.net.Uri
import com.koda.dev.core.files.ProjectFileSystem

internal suspend fun resolveOrCreateParent(fs: ProjectFileSystem, rootUri: Uri, relPath: String): Uri? {
    val segments = relPath.split("/").filter { it.isNotEmpty() }
    if (segments.size <= 1) return rootUri
    val parentSegments = segments.dropLast(1)
    var currentUri = rootUri
    for (segment in parentSegments) {
        val children = fs.listChildren(currentUri)
        val existing = children.find { it.name == segment }
        if (existing != null) {
            if (!existing.isDirectory) return null
            currentUri = existing.uri
        } else {
            currentUri = fs.createDirectory(currentUri, segment) ?: return null
        }
    }
    return currentUri
}

internal fun mimeTypeForFileName(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "csv" -> "text/csv"
        "js", "mjs" -> "application/javascript"
        "py" -> "text/x-python"
        "c", "h" -> "text/x-c"
        "cpp", "hpp", "cc" -> "text/x-c++"
        "kt", "kts" -> "text/x-kotlin"
        "java" -> "text/x-java"
        "sh", "bash" -> "application/x-sh"
        else -> "text/plain"
    }
}
