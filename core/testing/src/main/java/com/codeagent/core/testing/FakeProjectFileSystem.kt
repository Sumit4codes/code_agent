package com.codeagent.core.testing

import android.net.FakeUri
import android.net.Uri
import com.codeagent.core.files.FileNode
import com.codeagent.core.files.ProjectFileSystem

class FakeProjectFileSystem(
    private val rootPath: String = "content://test/root"
) : ProjectFileSystem {
    private val files = mutableMapOf<String, String>()
    private val directories = mutableSetOf<String>()

    init {
        directories.add(normalize(rootPath))
    }

    fun putFile(relativePath: String, content: String) {
        val full = normalize("$rootPath/$relativePath")
        files[full] = content
        ensureParentDirs(full)
    }

    private fun ensureParentDirs(path: String) {
        var current = path.substringBeforeLast('/', "")
        while (current.isNotEmpty() && current != rootPath) {
            directories.add(current)
            current = current.substringBeforeLast('/', "")
        }
    }

    private fun normalize(path: String): String =
        path.replace(Regex("/+"), "/").removeSuffix("/")

    override suspend fun listChildren(uri: Uri): List<FileNode> {
        val parentPath = normalize(uri.toString())
        val children = mutableListOf<FileNode>()
        val seenNames = mutableSetOf<String>()

        for (dir in directories) {
            if (dir != parentPath && dir.startsWith("$parentPath/")) {
                val rel = dir.removePrefix("$parentPath/")
                val segment = rel.substringBefore('/')
                if (seenNames.add(segment)) {
                    children.add(
                        FileNode(
                            uri = FakeUri("$parentPath/$segment"),
                            name = segment,
                            isDirectory = true
                        )
                    )
                }
            }
        }

        for ((file, content) in files) {
            if (file.startsWith("$parentPath/")) {
                val rel = file.removePrefix("$parentPath/")
                val segment = rel.substringBefore('/')
                if (!rel.contains('/') && seenNames.add(segment)) {
                    children.add(
                        FileNode(
                            uri = FakeUri(file),
                            name = segment,
                            isDirectory = false,
                            size = content.length.toLong(),
                            lastModified = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return children
    }

    override suspend fun readTextFile(uri: Uri): String? {
        val path = normalize(uri.toString())
        return files[path]
    }

    override suspend fun writeTextFile(uri: Uri, content: String) {
        val path = normalize(uri.toString())
        files[path] = content
        ensureParentDirs(path)
    }

    override suspend fun createFile(parentUri: Uri, name: String, mimeType: String): Uri? {
        val parentPath = normalize(parentUri.toString())
        val filePath = normalize("$parentPath/$name")
        files[filePath] = ""
        directories.add(parentPath)
        return FakeUri(filePath)
    }

    override suspend fun createDirectory(parentUri: Uri, name: String): Uri? {
        val parentPath = normalize(parentUri.toString())
        val dirPath = normalize("$parentPath/$name")
        directories.add(dirPath)
        return FakeUri(dirPath)
    }

    override suspend fun deleteFile(uri: Uri): Boolean {
        val path = normalize(uri.toString())
        return files.remove(path) != null || directories.remove(path)
    }

    override suspend fun exists(uri: Uri): Boolean {
        val path = normalize(uri.toString())
        return files.containsKey(path) || directories.contains(path)
    }

    override suspend fun fileSize(uri: Uri): Long? {
        val path = normalize(uri.toString())
        return files[path]?.length?.toLong()
    }

    override suspend fun lastModified(uri: Uri): Long? = System.currentTimeMillis()

    override suspend fun resolveRelativeUri(rootUri: Uri, relPath: String): Uri? {
        val cleanRel = relPath.trim().removePrefix("/").removeSuffix("/")
        if (cleanRel.isEmpty()) return rootUri
        val segments = cleanRel.split("/")
        var currentUri = rootUri
        for (segment in segments) {
            if (segment.isEmpty()) continue
            val children = listChildren(currentUri)
            val child = children.find { it.name == segment } ?: return null
            currentUri = child.uri
        }
        return currentUri
    }
}
