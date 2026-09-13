package com.codeagent.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.codeagent.core.common.AppDispatchers
import com.codeagent.core.common.DefaultAppDispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class SafProjectFileSystem(
    private val context: Context,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers
) : ProjectFileSystem {

    override suspend fun listChildren(uri: Uri): List<FileNode> = withContext(dispatchers.io) {
        val children = mutableListOf<FileNode>()
        val docId = if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.getDocumentId(uri)
        } else {
            DocumentsContract.getTreeDocumentId(uri)
        }
        val treeUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
        context.contentResolver.query(
            treeUri,
            COLUMNS,
            null, null,
            Document.COLUMN_DISPLAY_NAME
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val name = cursor.getString(nameCol)
                val mime = cursor.getString(mimeCol)
                val isDir = mime == Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                children.add(
                    FileNode(
                        uri = childUri,
                        name = name,
                        isDirectory = isDir,
                        size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else null,
                        lastModified = if (!cursor.isNull(modifiedCol)) cursor.getLong(modifiedCol) else null
                    )
                )
            }
        }
        children
    }

    override suspend fun readTextFile(uri: Uri): String? = withContext(dispatchers.io) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeTextFile(uri: Uri, content: String) = withContext(dispatchers.io) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
            it.write(content)
        } ?: throw IOException("Cannot open output stream for $uri")
    }

    override suspend fun createFile(parentUri: Uri, name: String, mimeType: String): Uri? =
        withContext(dispatchers.io) {
            try {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    mimeType,
                    name
                )
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun createDirectory(parentUri: Uri, name: String): Uri? =
        withContext(dispatchers.io) {
            try {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    Document.MIME_TYPE_DIR,
                    name
                )
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun deleteFile(uri: Uri): Boolean = withContext(dispatchers.io) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun exists(uri: Uri): Boolean = withContext(dispatchers.io) {
        try {
            context.contentResolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fileSize(uri: Uri): Long? = withContext(dispatchers.io) {
        try {
            context.contentResolver.query(uri, arrayOf(Document.COLUMN_SIZE), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun lastModified(uri: Uri): Long? = withContext(dispatchers.io) {
        try {
            context.contentResolver.query(uri, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun resolveRelativeUri(rootUri: Uri, relPath: String): Uri? =
        withContext(dispatchers.io) {
            val segments = relPath.split("/").filter { it.isNotEmpty() }
            var currentUri = rootUri
            for (segment in segments) {
                val children = listChildren(currentUri)
                val child = children.find { it.name == segment } ?: return@withContext null
                currentUri = child.uri
            }
            currentUri
        }

    companion object {
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )
    }
}

object Document {
    const val COLUMN_DOCUMENT_ID = DocumentsContract.Document.COLUMN_DOCUMENT_ID
    const val COLUMN_DISPLAY_NAME = DocumentsContract.Document.COLUMN_DISPLAY_NAME
    const val COLUMN_MIME_TYPE = DocumentsContract.Document.COLUMN_MIME_TYPE
    const val COLUMN_SIZE = DocumentsContract.Document.COLUMN_SIZE
    const val COLUMN_LAST_MODIFIED = DocumentsContract.Document.COLUMN_LAST_MODIFIED
    const val MIME_TYPE_DIR = DocumentsContract.Document.MIME_TYPE_DIR
}
