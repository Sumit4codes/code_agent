package com.codeagent.core.files

import android.net.Uri

interface ProjectFileSystem {
    suspend fun listChildren(uri: Uri): List<FileNode>
    suspend fun readTextFile(uri: Uri): String?
    suspend fun writeTextFile(uri: Uri, content: String)
    suspend fun createFile(parentUri: Uri, name: String, mimeType: String): Uri?
    suspend fun createDirectory(parentUri: Uri, name: String): Uri?
    suspend fun deleteFile(uri: Uri): Boolean
    suspend fun exists(uri: Uri): Boolean
    suspend fun fileSize(uri: Uri): Long?
    suspend fun lastModified(uri: Uri): Long?
    suspend fun resolveRelativeUri(rootUri: Uri, relPath: String): Uri?
}

data class FileNode(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val lastModified: Long? = null
)
