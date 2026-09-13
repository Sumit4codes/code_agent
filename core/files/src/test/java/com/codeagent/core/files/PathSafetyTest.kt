package com.codeagent.core.files

import org.junit.Assert.*
import org.junit.Test

class PathSafetyTest {

    @Test
    fun `rejects path traversal`() {
        assertFalse(PathSafety.isValidRelativePath("../etc/passwd"))
        assertFalse(PathSafety.isValidRelativePath("foo/../../bar"))
        assertFalse(PathSafety.isValidRelativePath(".."))
    }

    @Test
    fun `rejects absolute paths`() {
        assertFalse(PathSafety.isValidRelativePath("/etc/passwd"))
        assertFalse(PathSafety.isValidRelativePath("/foo/bar"))
    }

    @Test
    fun `rejects empty and blank`() {
        assertFalse(PathSafety.isValidRelativePath(""))
        assertFalse(PathSafety.isValidRelativePath("   "))
    }

    @Test
    fun `accepts valid relative paths`() {
        assertTrue(PathSafety.isValidRelativePath("src/main.kt"))
        assertTrue(PathSafety.isValidRelativePath("foo/bar/baz.txt"))
        assertTrue(PathSafety.isValidRelativePath("file.kt"))
    }

    @Test
    fun `rejects dangerous segment names`() {
        assertFalse(PathSafety.isValidRelativePath(".git/config"))
        assertFalse(PathSafety.isValidRelativePath("build/output"))
    }

    @Test
    fun `shouldIgnore hides hidden and build dirs`() {
        assertTrue(PathSafety.shouldIgnore(".git", true))
        assertTrue(PathSafety.shouldIgnore("build", true))
        assertTrue(PathSafety.shouldIgnore(".idea", true))
    }

    @Test
    fun `shouldIgnore allows normal files`() {
        assertFalse(PathSafety.shouldIgnore("src", true))
        assertFalse(PathSafety.shouldIgnore("main.kt", false))
        assertFalse(PathSafety.shouldIgnore("README.md", false))
    }
}
