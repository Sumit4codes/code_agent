package com.koda.dev.core.agent

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe queue for mid-run user guidance messages sent while the agent is executing.
 * The running agent loop drains this inbox between tool execution cycles and injects
 * the steering directives into the conversation context so the LLM course-corrects live.
 */
class SteeringInbox {
    private val queue = ConcurrentLinkedQueue<String>()

    /**
     * Enqueue a user steering message. Ignored if blank.
     */
    fun send(message: String) {
        val trimmed = message.trim()
        if (trimmed.isNotBlank()) {
            queue.offer(trimmed)
        }
    }

    /**
     * Drain and return all pending steering messages in FIFO order.
     */
    fun drain(): List<String> {
        val items = mutableListOf<String>()
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
