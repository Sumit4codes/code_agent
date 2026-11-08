package com.koda.dev.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogResolverTest {

    @Test
    fun userOverrideTakesHighestPriority() {
        val limit = ModelCatalogResolver.resolveContextLimit(
            modelId = "gemini-2.0-flash",
            providerReportedWindow = 1_000_000,
            userOverride = 32_000
        )
        assertEquals(32_000, limit)
    }

    @Test
    fun providerReportedWindowTakesPrecedenceOverStaticCatalog() {
        val limit = ModelCatalogResolver.resolveContextLimit(
            modelId = "gpt-4o",
            providerReportedWindow = 65_536
        )
        assertEquals(65_536, limit)
    }

    @Test
    fun staticCatalogResolvesStandardModels() {
        assertEquals(2_000_000, ModelCatalogResolver.resolveContextLimit("gemini-2.0-pro"))
        assertEquals(1_000_000, ModelCatalogResolver.resolveContextLimit("gemini-2.0-flash"))
        assertEquals(200_000, ModelCatalogResolver.resolveContextLimit("claude-3-7-sonnet"))
        assertEquals(200_000, ModelCatalogResolver.resolveContextLimit("claude-3-5-sonnet"))
        assertEquals(128_000, ModelCatalogResolver.resolveContextLimit("gpt-4o"))
        assertEquals(200_000, ModelCatalogResolver.resolveContextLimit("o1"))
        assertEquals(128_000, ModelCatalogResolver.resolveContextLimit("nvidia/nemotron-3.5-super-vl-120b-a12b"))
        assertEquals(128_000, ModelCatalogResolver.resolveContextLimit("deepseek-r1"))
        assertEquals(256_000, ModelCatalogResolver.resolveContextLimit("kilo-auto/free"))
        assertEquals(1_000_000, ModelCatalogResolver.resolveContextLimit("nvidia/nemotron-3.5-super-vl:free"))
        assertEquals(256_000, ModelCatalogResolver.resolveContextLimit("cohere/north-mini-code:free"))
        assertEquals(200_000, ModelCatalogResolver.resolveContextLimit("openrouter/free"))
    }

    @Test
    fun familyPatternMatchingHandlesVariants() {
        assertEquals(1_000_000, ModelCatalogResolver.resolveContextLimit("custom/my-gemini-model-v2"))
        assertEquals(200_000, ModelCatalogResolver.resolveContextLimit("anthropic/claude-something-new"))
        assertEquals(128_000, ModelCatalogResolver.resolveContextLimit("my-llama-3.3-fine-tune"))
        assertEquals(32_768, ModelCatalogResolver.resolveContextLimit("local/phi-4-mini"))
        assertEquals(128_000, ModelCatalogResolver.resolveContextLimit("unknown-xyz-vendor-model"))
    }
}
