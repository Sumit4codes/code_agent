package com.codeagent.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditResolverTest {

    @Test
    fun `targeted edit with exact old_content replaces only target block`() {
        val original = """
            fun main() {
                var x = 0
                while (x < 10) {
                    x++
                }
                println(x)
            }
        """.trimIndent()

        val oldContent = """
                while (x < 10) {
                    x++
                }
        """.trimIndent()

        val newContent = """
                while (x < 10) {
                    // increment counter
                    x++
                }
        """.trimIndent()

        val result = EditResolver.resolveEdit(
            originalContent = original,
            newContent = newContent,
            oldContent = oldContent
        )

        assertTrue(result.success)
        assertTrue(result.proposedContent.contains("fun main() {"))
        assertTrue(result.proposedContent.contains("// increment counter"))
        assertTrue(result.proposedContent.contains("println(x)"))
    }

    @Test
    fun `targeted edit with nonexistent old_content fails gracefully`() {
        val original = "val a = 1\nval b = 2\n"
        val result = EditResolver.resolveEdit(
            originalContent = original,
            newContent = "val c = 3",
            oldContent = "nonexistent code"
        )
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("Could not find 'old_content'") == true)
    }

    @Test
    fun `snippet without old_content preserves surrounding code for while loop edit`() {
        // This reproduces the exact bug reported by the user!
        val original = """
            import java.io.*

            class Runner {
                fun run() {
                    var i = 0
                    while (i < 10) {
                        i++
                    }
                    println("Done")
                }
            }
        """.trimIndent()

        val snippet = """
                    while (i < 10) {
                        // added comment in while loop
                        i++
                    }
        """.trimIndent()

        val result = EditResolver.resolveEdit(
            originalContent = original,
            newContent = snippet,
            oldContent = null
        )

        assertTrue(result.success)
        // Verify surrounding code was NOT removed
        assertTrue("Header preserved", result.proposedContent.contains("import java.io.*"))
        assertTrue("Class preserved", result.proposedContent.contains("class Runner {"))
        assertTrue("Method preserved", result.proposedContent.contains("fun run() {"))
        assertTrue("Var preserved", result.proposedContent.contains("var i = 0"))
        assertTrue("Comment added", result.proposedContent.contains("// added comment in while loop"))
        assertTrue("Footer preserved", result.proposedContent.contains("println(\"Done\")"))
    }

    @Test
    fun `placeholder comments splice into original content`() {
        val original = """
            fun step1() = 1
            fun step2() = 2
            fun step3() = 3
        """.trimIndent()

        val newWithPlaceholders = """
            // ... existing code ...
            fun step2() = 200
            // ... existing code ...
        """.trimIndent()

        val result = EditResolver.resolveEdit(
            originalContent = original,
            newContent = newWithPlaceholders,
            oldContent = null
        )

        assertTrue(result.success)
        assertTrue(result.proposedContent.contains("fun step1() = 1"))
        assertTrue(result.proposedContent.contains("fun step2() = 200"))
        assertTrue(result.proposedContent.contains("fun step3() = 3"))
    }

    @Test
    fun `full file replacement succeeds when header and footer match`() {
        val original = """
            package test
            fun greet() = "hello"
        """.trimIndent()

        val replacement = """
            package test
            fun greet() = "world"
        """.trimIndent()

        val result = EditResolver.resolveEdit(
            originalContent = original,
            newContent = replacement,
            oldContent = null
        )

        assertTrue(result.success)
        assertEquals(replacement, result.proposedContent)
    }

    @Test
    fun `safety guard blocks catastrophic truncation when snippet cannot be anchored`() {
        val original = """
            line 1
            line 2
            line 3
            line 4
            line 5
            line 6
            line 7
            line 8
            line 9
            line 10
        """.trimIndent()

        val tinyUnanchoredSnippet = """
            completely new x
            completely new y
        """.trimIndent()

        val result = EditResolver.resolveEdit(
            originalContent = original,
            newContent = tinyUnanchoredSnippet,
            oldContent = null
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("Safety guard") == true)
    }
}
