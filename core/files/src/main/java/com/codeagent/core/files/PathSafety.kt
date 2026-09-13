package com.codeagent.core.files

object PathSafety {
    private val DANGEROUS_SEGMENTS = setOf(
        ".", "..", "", ".git", ".idea", "build", ".gradle",
        "__pycache__", "node_modules", ".DS_Store", "Thumbs.db"
    )

    fun isValidRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/")) return false
        if (path.contains("\u0000")) return false
        if (path.contains("%2e", ignoreCase = true) || path.contains("%2f", ignoreCase = true)) return false
        val segments = path.split("/")
        return segments.none { it in DANGEROUS_SEGMENTS } &&
            segments.none { it.contains("\\") } &&
            !path.contains("..")
    }

    fun shouldIgnore(name: String, isDirectory: Boolean): Boolean {
        if (name in DANGEROUS_SEGMENTS) return true
        if (name.startsWith("~") || name.endsWith("~")) return true
        if (isDirectory && name.startsWith(".")) return true
        if (isDirectory && name == "build") return true
        return false
    }
}
