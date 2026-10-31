package com.koda.dev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntirouteQrParserTest {

    @Test
    fun `parses standard pinggy https url`() {
        val raw = "https://free-user-123.pinggy.io"
        val result = AntirouteQrParser.parse(raw)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("https://free-user-123.pinggy.io/v1", config.baseUrl)
        assertEquals("sk-antigravity-claude", config.apiKey)
        assertEquals("gemini-3.8-flash-high", config.model)
        assertEquals("Antiroute Tunnel", config.name)
    }

    @Test
    fun `parses pinggy url already containing v1`() {
        val raw = "https://free-user-123.pinggy.io/v1"
        val result = AntirouteQrParser.parse(raw)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("https://free-user-123.pinggy.io/v1", config.baseUrl)
    }

    @Test
    fun `parses local lan ip address with port`() {
        val raw = "http://192.168.1.55:8317"
        val result = AntirouteQrParser.parse(raw)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("http://192.168.1.55:8317/v1", config.baseUrl)
    }

    @Test
    fun `strips chat completions or setup path suffixes`() {
        val raw1 = "https://my-tunnel.pinggy.io/v1/chat/completions"
        val result1 = AntirouteQrParser.parse(raw1)
        assertTrue(result1.isSuccess)
        assertEquals("https://my-tunnel.pinggy.io/v1", result1.getOrThrow().baseUrl)

        val raw2 = "https://my-tunnel.pinggy.io/setup"
        val result2 = AntirouteQrParser.parse(raw2)
        assertTrue(result2.isSuccess)
        assertEquals("https://my-tunnel.pinggy.io/v1", result2.getOrThrow().baseUrl)
    }

    @Test
    fun `parses url with query parameters for custom key and model`() {
        val raw = "https://tunnel.pinggy.io?apiKey=sk-custom-secret&model=gemini-2.5-pro"
        val result = AntirouteQrParser.parse(raw)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("https://tunnel.pinggy.io/v1", config.baseUrl)
        assertEquals("sk-custom-secret", config.apiKey)
        assertEquals("gemini-2.5-pro", config.model)
    }

    @Test
    fun `parses json formatted qr code`() {
        val raw = """
            {
                "baseUrl": "https://antiroute-gateway.org/v1",
                "apiKey": "sk-json-key-456",
                "model": "claude-3-5-sonnet-20241022",
                "name": "My Work Antiroute"
            }
        """.trimIndent()

        val result = AntirouteQrParser.parse(raw)
        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("https://antiroute-gateway.org/v1", config.baseUrl)
        assertEquals("sk-json-key-456", config.apiKey)
        assertEquals("claude-3-5-sonnet-20241022", config.model)
        assertEquals("My Work Antiroute", config.name)
    }

    @Test
    fun `parses custom antiroute scheme`() {
        val raw = "antiroute://connect?url=https://tunnel.pinggy.io&key=sk-tunnel-key&model=gemini-2.5-flash"
        val result = AntirouteQrParser.parse(raw)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("https://tunnel.pinggy.io/v1", config.baseUrl)
        assertEquals("sk-tunnel-key", config.apiKey)
        assertEquals("gemini-2.5-flash", config.model)
    }

    @Test
    fun `parses key-value multiline text`() {
        val raw = """
            url=https://kv-tunnel.pinggy.io
            apiKey=sk-kv-key
            model=gemini-3.8-flash-high
        """.trimIndent()

        val result = AntirouteQrParser.parse(raw)
        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("https://kv-tunnel.pinggy.io/v1", config.baseUrl)
        assertEquals("sk-kv-key", config.apiKey)
    }

    @Test
    fun `rejects empty or invalid text`() {
        assertTrue(AntirouteQrParser.parse("").isFailure)
        assertTrue(AntirouteQrParser.parse("   ").isFailure)
        assertTrue(AntirouteQrParser.parse("not-a-valid-qr-code").isFailure)
    }
}
