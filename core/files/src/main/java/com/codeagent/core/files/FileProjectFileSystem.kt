package com.codeagent.core.files

import android.net.Uri
import com.codeagent.core.common.AppDispatchers
import com.codeagent.core.common.DefaultAppDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct file-based implementation of [ProjectFileSystem] utilizing full device storage access.
 * Replaces the previous Storage Access Framework (SAF) implementation, providing standard POSIX
 * file operations and direct path compatibility with JGit, VirtualShell, and AI agents.
 */
@Singleton
class FileProjectFileSystem(
    private val dispatchers: AppDispatchers,
    private val uriFactory: (File) -> Uri
) : ProjectFileSystem {

    @Inject
    constructor() : this(
        dispatchers = DefaultAppDispatchers,
        uriFactory = { Uri.fromFile(it) }
    )

    private fun fileToUri(file: File): Uri = uriFactory(file)

    override suspend fun listChildren(uri: Uri): List<FileNode> = withContext(dispatchers.io) {
        val dir = uriToFile(uri)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        val files = dir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            FileNode(
                uri = fileToUri(file),
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else null,
                lastModified = if (file.exists()) file.lastModified() else null
            )
        }
    }

    override suspend fun readTextFile(uri: Uri): String? = withContext(dispatchers.io) {
        try {
            val file = uriToFile(uri)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeTextFile(uri: Uri, content: String) = withContext(dispatchers.io) {
        val file = uriToFile(uri)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun createFile(parentUri: Uri, name: String, mimeType: String): Uri? =
        withContext(dispatchers.io) {
            try {
                val parent = uriToFile(parentUri)
                parent.mkdirs()
                val file = File(parent, name)
                if (!file.exists()) {
                    val created = file.createNewFile()
                    if (!created && !file.exists()) return@withContext null
                }
                fileToUri(file)
            } catch (e: Exception) {
                android.util.Log.e("FileProjectFileSystem", "Failed to create file $name in $parentUri", e)
                null
            }
        }

    override suspend fun createDirectory(parentUri: Uri, name: String): Uri? =
        withContext(dispatchers.io) {
            try {
                val parent = uriToFile(parentUri)
                val dir = File(parent, name)
                if (dir.mkdirs() || dir.exists()) {
                    fileToUri(dir)
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("FileProjectFileSystem", "Failed to create directory $name in $parentUri", e)
                null
            }
        }

    override suspend fun deleteFile(uri: Uri): Boolean = withContext(dispatchers.io) {
        try {
            val file = uriToFile(uri)
            if (!file.exists()) return@withContext true
            file.deleteRecursively()
        } catch (e: Exception) {
            android.util.Log.e("FileProjectFileSystem", "Failed to delete $uri", e)
            false
        }
    }

    override suspend fun exists(uri: Uri): Boolean = withContext(dispatchers.io) {
        try {
            uriToFile(uri).exists()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fileSize(uri: Uri): Long? = withContext(dispatchers.io) {
        try {
            val file = uriToFile(uri)
            if (file.exists() && file.isFile) file.length() else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun lastModified(uri: Uri): Long? = withContext(dispatchers.io) {
        try {
            val file = uriToFile(uri)
            if (file.exists()) file.lastModified() else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun resolveRelativeUri(rootUri: Uri, relPath: String): Uri? =
        withContext(dispatchers.io) {
            try {
                val rootFile = uriToFile(rootUri).canonicalFile
                val trimmed = relPath.trim()
                if (trimmed.isEmpty() || trimmed == ".") {
                    return@withContext fileToUri(rootFile)
                }

                val resolvedFile = if (trimmed.startsWith(rootFile.absolutePath) || trimmed.startsWith(rootFile.canonicalPath)) {
                    File(trimmed).canonicalFile
                } else {
                    val cleanRel = trimmed.removePrefix("./").removePrefix("/")
                    File(rootFile, cleanRel).canonicalFile
                }

                val rootCanonical = rootFile.canonicalPath
                val resolvedCanonical = resolvedFile.canonicalPath

                // Security check: ensure target is within root directory boundary
                if (resolvedCanonical == rootCanonical || resolvedCanonical.startsWith(rootCanonical + File.separator)) {
                    fileToUri(resolvedFile)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

    companion object {
        fun uriToFile(uri: Uri): File {
            val path = try {
                uri.path
            } catch (_: RuntimeException) {
                null
            }
            val raw = path ?: uri.toString()
            val clean = when {
                raw.startsWith("file://") -> raw.removePrefix("file://")
                else -> raw
            }
            return File(clean)
        }
    }
}
