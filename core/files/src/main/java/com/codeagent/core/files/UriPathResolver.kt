package com.codeagent.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.net.URLDecoder

object UriPathResolver {

    /**
     * Resolves an Android SAF tree URI, document URI, or file URI into a real POSIX [File].
     * If direct POSIX resolution is not available or blocked, creates and returns an accessible
     * app-scoped workspace directory.
     */
    fun resolveLocalDirectory(
        context: Context? = null,
        uri: Uri,
        fallbackName: String = "default_workspace"
    ): File {
        val uriStr = uri.toString()
        val scheme = try { uri.scheme } catch (_: Throwable) { null }
        val path = try { uri.path } catch (_: Throwable) { null }

        // 1. Direct file URI or POSIX path
        if (scheme == "file" || scheme == null || path?.startsWith("/") == true) {
            val p = path ?: uriStr.removePrefix("file://")
            if (p.isNotBlank()) {
                val f = File(p)
                if (f.exists() || f.mkdirs()) {
                    return f
                }
            }
        }

        // 2. Extract Document ID from SAF content URI
        val decoded = try {
            URLDecoder.decode(uriStr, "UTF-8")
        } catch (_: Exception) {
            uriStr
        }

        var docId: String? = null
        if (context != null) {
            try {
                docId = if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.getDocumentId(uri)
                } else {
                    DocumentsContract.getTreeDocumentId(uri)
                }
            } catch (_: Throwable) { }
        }

        if (docId == null) {
            val marker = when {
                decoded.contains("/tree/") -> "/tree/"
                decoded.contains("/document/") -> "/document/"
                else -> null
            }
            if (marker != null) {
                docId = decoded.substringAfter(marker).substringBefore("?").substringBefore("/document/")
            }
        }

        if (docId != null) {
            val candidateFile = resolveDocIdToFile(docId)
            if (candidateFile != null && (candidateFile.exists() || candidateFile.mkdirs())) {
                return candidateFile
            }
        }

        // 3. Fallback to app-internal storage workspace
        val safeName = fallbackName.ifBlank { "default_workspace" }.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val fallbackDir = if (context != null) {
            File(context.filesDir, "workspaces/$safeName")
        } else {
            File(System.getProperty("java.io.tmpdir", "/tmp"), "codeagent_workspaces/$safeName")
        }
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    private fun resolveDocIdToFile(docId: String): File? {
        return when {
            docId.startsWith("primary:") -> {
                val rel = docId.removePrefix("primary:")
                val candidates = listOf(
                    File("/storage/emulated/0", rel),
                    File("/sdcard", rel)
                )
                candidates.firstOrNull { it.exists() || it.parentFile?.exists() == true }
                    ?: File("/storage/emulated/0", rel)
            }
            docId.startsWith("raw:") -> {
                val path = docId.removePrefix("raw:")
                File(path)
            }
            docId.contains(":") -> {
                val parts = docId.split(":", limit = 2)
                val storageId = parts[0]
                val rel = parts[1]
                File("/storage/$storageId/$rel")
            }
            else -> null
        }
    }
}
