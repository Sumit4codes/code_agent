package com.koda.dev.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringInboxTest {

    @Test
    fun `empty inbox returns empty list and hasPending false`() {
        val inbox = SteeringInbox()
        assertFalse(inbox.hasPending)
        assertTrue(inbox.drain().isEmpty())
    }

    @Test
    fun `send enqueues message and drain returns items in FIFO order`() {
        val inbox = SteeringInbox()
        inbox.send("First guidance")
        inbox.send("Second guidance")

        assertTrue(inbox.hasPending)
        val drained = inbox.drain()
        assertEquals(listOf("First guidance", "Second guidance"), drained)
        assertFalse(inbox.hasPending)
        assertTrue(inbox.drain().isEmpty())
    }

    @Test
    fun `blank messages are ignored`() {
        val inbox = SteeringInbox()
        inbox.send("")
        inbox.send("   ")
        inbox.send("\n\t")

        assertFalse(inbox.hasPending)
        assertTrue(inbox.drain().isEmpty())
    }

    @Test
    fun `clear empties pending queue`() {
        val inbox = SteeringInbox()
        inbox.send("Guidance to discard")
        assertTrue(inbox.hasPending)

        inbox.clear()
        assertFalse(inbox.hasPending)
        assertTrue(inbox.drain().isEmpty())
    }
}
