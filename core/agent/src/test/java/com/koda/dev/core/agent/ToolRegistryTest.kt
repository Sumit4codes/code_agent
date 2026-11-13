package com.koda.dev.core.agent

import com.koda.dev.core.model.ToolNames
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun `rename is not exposed as an available tool until executor supports it`() {
        assertFalse(ToolNames.ALL.contains(ToolNames.RENAME_FILE))
        assertFalse(ToolRegistry.allSpecs().any { it.name == ToolNames.RENAME_FILE })
    }
}
