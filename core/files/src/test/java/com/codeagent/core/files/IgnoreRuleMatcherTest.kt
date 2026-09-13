package com.codeagent.core.files

import org.junit.Assert.*
import org.junit.Test

class IgnoreRuleMatcherTest {

    @Test
    fun `empty matcher ignores nothing`() {
        val matcher = IgnoreRuleMatcher(emptyList())
        assertFalse(matcher.isIgnored("foo.txt", false))
        assertFalse(matcher.isIgnored("build/output", true))
    }

    @Test
    fun `star matches any file`() {
        val matcher = IgnoreRuleMatcher(listOf("*.class"))
        assertTrue(matcher.isIgnored("Foo.class", false))
        assertFalse(matcher.isIgnored("Foo.java", false))
    }

    @Test
    fun `directory only pattern matches dirs`() {
        val matcher = IgnoreRuleMatcher(listOf("build/"))
        assertTrue(matcher.isIgnored("build", true))
        assertFalse(matcher.isIgnored("build", false))
    }

    @Test
    fun `anchored pattern only matches from root`() {
        val matcher = IgnoreRuleMatcher(listOf("/Makefile"))
        assertTrue(matcher.isIgnored("Makefile", false))
        assertFalse(matcher.isIgnored("src/Makefile", false))
    }

    @Test
    fun `double star matches nested dirs`() {
        val matcher = IgnoreRuleMatcher(listOf("**/node_modules"))
        assertTrue(matcher.isIgnored("node_modules", true))
        assertTrue(matcher.isIgnored("foo/node_modules", true))
        assertTrue(matcher.isIgnored("foo/bar/node_modules", true))
    }

    @Test
    fun `fromGitignore parses standard gitignore content`() {
        val content = """
            # Comment
            *.class
            build/
            .idea/
            *.log
        """.trimIndent()
        val matcher = IgnoreRuleMatcher.fromGitignore(content)
        assertTrue(matcher.isIgnored("Foo.class", false))
        assertTrue(matcher.isIgnored("build", true))
        assertTrue(matcher.isIgnored(".idea", true))
        assertTrue(matcher.isIgnored("debug.log", false))
        assertFalse(matcher.isIgnored("Foo.java", false))
    }
}
