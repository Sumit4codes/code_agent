package com.koda.dev.core.agent

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Encapsulates a single mid-run steering directive sent by the user while an agent task is active.
 */
data class SteeringDirective(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thread-safe queue for mid-run user guidance messages sent while the agent is executing.
 * The running agent loop drains this inbox between tool execution cycles and injects
 * the steering directives into the conversation context so the LLM course-corrects live.
 */
class SteeringInbox {
    private val queue = ConcurrentLinkedQueue<SteeringDirective>()

    /**
     * Enqueue a user steering message. Ignored if blank.
     */
    fun send(message: String, id: String = UUID.randomUUID().toString()): SteeringDirective? {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return null
        val directive = SteeringDirective(id = id, text = trimmed)
        queue.offer(directive)
        return directive
    }

    /**
     * Drain and return all pending steering directives in FIFO order.
     */
    fun drain(): List<SteeringDirective> {
        val items = mutableListOf<SteeringDirective>()
        while (true) {
            val item = queue.poll() ?: break
            items.add(item)
        }
        return items
    }

    /**
     * Clear all pending steering messages.
     */
    fun clear() {
        queue.clear()
    }

    /**
     * Check if there are any steering messages pending in the inbox.
     */
    val hasPending: Boolean get() = !queue.isEmpty()
}
