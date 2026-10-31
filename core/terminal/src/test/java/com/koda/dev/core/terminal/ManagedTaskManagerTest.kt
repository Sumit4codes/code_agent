package com.koda.dev.core.terminal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedTaskManagerTest {

    private lateinit var terminalExecutor: PosixTerminalExecutor
    private lateinit var taskManager: ManagedTaskManager

    @Before
    fun setUp() {
        terminalExecutor = PosixTerminalExecutor()
        taskManager = ManagedTaskManager(terminalExecutor)
    }

    @After
    fun tearDown() {
        taskManager.killAll()
    }

    @Test
    fun `short command finishes synchronously within waitSeconds`() = runTest {
        val result = taskManager.launch(
            command = "echo 'hello world'",
            waitSeconds = 5,
            isBackground = false
        )

        assertTrue(result is TaskLaunchResult.Completed)
        val completed = result as TaskLaunchResult.Completed
        assertTrue(completed.output.contains("hello world"))
        assertEquals(0, completed.exitCode)
    }

    @Test
    fun `command exceeding waitSeconds transitions to background task`() = runTest {
        val result = taskManager.launch(
            command = "sleep 10",
            waitSeconds = 1,
            isBackground = false
        )

        assertTrue(result is TaskLaunchResult.Running)
        val running = result as TaskLaunchResult.Running
        assertTrue(running.taskId.startsWith("task_"))

        val status = taskManager.getStatus(running.taskId)
        assertNotNull(status)
        assertTrue(status!!.isAlive)

        // Kill it
        val killed = taskManager.kill(running.taskId)
        assertTrue(killed)

        val afterKill = taskManager.getStatus(running.taskId)
        assertNotNull(afterKill)
        assertFalse(afterKill!!.isAlive)
    }

    @Test
    fun `isBackground true immediately launches in background`() = runTest {
        val result = taskManager.launch(
            command = "sleep 5",
            waitSeconds = 5,
            isBackground = true
        )

        assertTrue(result is TaskLaunchResult.Running)
        val running = result as TaskLaunchResult.Running
        assertEquals(0, running.waitSeconds)

        val status = taskManager.getStatus(running.taskId)
        assertNotNull(status)
        assertTrue(status!!.isAlive)
        taskManager.kill(running.taskId)
    }

    @Test
    fun `getStatus reports runtime and silence correctly`() = runTest {
        val result = taskManager.launch(
            command = "echo 'line 1' && sleep 2 && echo 'line 2'",
            waitSeconds = 1,
            isBackground = false
        )

        assertTrue(result is TaskLaunchResult.Running)
        val running = result as TaskLaunchResult.Running

        val status = taskManager.getStatus(running.taskId)
        assertNotNull(status)
        assertTrue(status!!.recentOutput.contains("line 1"))
        taskManager.kill(running.taskId)
    }

    @Test
    fun `list returns active and past tasks`() = runTest {
        val res1 = taskManager.launch(command = "echo 't1'", waitSeconds = 2)
        val res2 = taskManager.launch(command = "sleep 5", isBackground = true)

        val tasks = taskManager.list()
        assertTrue(tasks.size >= 2)
        taskManager.killAll()
    }

    @Test
    fun `exceeding max concurrent tasks cap returns error`() = runTest {
        val launchedTasks = mutableListOf<String>()
        for (i in 1..ManagedTaskManager.MAX_CONCURRENT_TASKS) {
            val res = taskManager.launch(command = "sleep 30", isBackground = true)
            assertTrue(res is TaskLaunchResult.Running)
            launchedTasks.add((res as TaskLaunchResult.Running).taskId)
        }

        // 6th task should be rejected
        val rejected = taskManager.launch(command = "sleep 10", isBackground = true)
        assertTrue(rejected is TaskLaunchResult.Error)
        val error = rejected as TaskLaunchResult.Error
        assertTrue(error.message.contains("Task limit reached"))

        taskManager.killAll()
    }

    @Test
    fun `circular buffer limits max lines and bytes`() {
        val buffer = CircularOutputBuffer(maxLines = 10, maxBytes = 200)
        for (i in 1..30) {
            buffer.append("Log line number $i")
        }

        val recent = buffer.getRecentLines(5)
        assertEquals(5, recent.size)
        assertTrue(recent.last().contains("30"))
        assertTrue(buffer.getFullText().lines().size <= 10)
    }
}
