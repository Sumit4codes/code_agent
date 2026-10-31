package com.koda.dev.core.terminal

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Summary information for an active or completed managed background task.
 */
data class TaskSummary(
    val taskId: String,
    val command: String,
    val isAlive: Boolean,
    val exitCode: Int?,
    val runtimeSeconds: Long,
    val silenceSeconds: Long,
    val isStalled: Boolean,
    val isDaemon: Boolean
)

/**
 * Detailed status for a managed background task, including recent output tail.
 */
data class TaskStatusInfo(
    val taskId: String,
    val command: String,
    val isAlive: Boolean,
    val exitCode: Int?,
    val runtimeSeconds: Long,
    val silenceSeconds: Long,
    val isStalled: Boolean,
    val recentOutput: String
)

/**
 * Result of launching a command through the task manager.
 */
sealed class TaskLaunchResult {
    data class Completed(
        val taskId: String,
        val output: String,
        val exitCode: Int
    ) : TaskLaunchResult()

    data class Running(
        val taskId: String,
        val command: String,
        val initialOutput: String,
        val waitSeconds: Int
    ) : TaskLaunchResult()

    data class Error(
        val message: String
    ) : TaskLaunchResult()
}

/**
 * Thread-safe circular ring buffer retaining the last N lines or maxBytes of output.
 */
class CircularOutputBuffer(
    private val maxLines: Int = 300,
    private val maxBytes: Int = 65536
) {
    private val lines = ArrayDeque<String>()
    private var totalBytes = 0

    @Synchronized
    fun append(line: String) {
        lines.addLast(line)
        totalBytes += line.length + 1
        while (lines.size > maxLines || totalBytes > maxBytes) {
            val removed = lines.pollFirst() ?: break
            totalBytes -= (removed.length + 1)
        }
    }

    @Synchronized
    fun getRecentLines(count: Int = 50): List<String> {
        val n = minOf(count, lines.size)
        return lines.toList().takeLast(n)
    }

    @Synchronized
    fun getFullText(): String {
        return lines.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        lines.clear()
        totalBytes = 0
    }
}

/**
 * Internal tracking data for a managed background task.
 */
data class ManagedTask(
    val taskId: String,
    val command: String,
    val workingDir: File,
    val process: Process,
    val startTimeMs: Long,
    val isDaemon: Boolean,
    val outputBuffer: CircularOutputBuffer,
    val stdinWriter: BufferedWriter?,
    val job: Job,
    val lastOutputTimeMs: AtomicLong = AtomicLong(System.currentTimeMillis()),
    @Volatile var isKilled: Boolean = false,
    @Volatile var exitCode: Int? = null
) {
    val isAlive: Boolean
        get() = process.isAlive && !isKilled

    val runtimeSeconds: Long
        get() = (System.currentTimeMillis() - startTimeMs) / 1000

    val silenceSeconds: Long
        get() = (System.currentTimeMillis() - lastOutputTimeMs.get()) / 1000

    val isStalled: Boolean
        get() = isAlive && silenceSeconds >= ManagedTaskManager.STALL_THRESHOLD_SECONDS
}

/**
 * Public interface for managing background and foreground task lifecycle.
 */
interface TaskManager {
    val activeTasksFlow: StateFlow<List<TaskSummary>>

    suspend fun launch(
        command: String,
        workingDir: File? = null,
        waitSeconds: Int = ManagedTaskManager.DEFAULT_WAIT_SECONDS,
        isBackground: Boolean = false,
        onOutputChunk: ((String) -> Unit)? = null
    ): TaskLaunchResult

    fun getStatus(taskId: String): TaskStatusInfo?
    fun kill(taskId: String): Boolean
    fun sendInput(taskId: String, input: String): Boolean
    fun list(): List<TaskSummary>
    fun killAll()
}

/**
 * Implementation of TaskManager with autonomous control, automatic backgrounding,
 * stalled process watchdog, and strict concurrency limits to safeguard mobile resources.
 */
@Singleton
class ManagedTaskManager @Inject constructor(
    private val terminalExecutor: PosixTerminalExecutor
) : TaskManager {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tasks = ConcurrentHashMap<String, ManagedTask>()
    private val taskCounter = AtomicInteger(1)

    private val _activeTasksFlow = MutableStateFlow<List<TaskSummary>>(emptyList())
    override val activeTasksFlow: StateFlow<List<TaskSummary>> = _activeTasksFlow.asStateFlow()
    private var tickerJob: Job? = null

    companion object {
        const val MAX_CONCURRENT_TASKS = 5
        const val STALL_THRESHOLD_SECONDS = 30L
        const val DEFAULT_WAIT_SECONDS = 20
    }

    @Synchronized
    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = coroutineScope.launch {
            while (isActive) {
                delay(1000L)
                if (tasks.values.any { it.isAlive }) {
                    updateActiveTasksFlow()
                } else {
                    updateActiveTasksFlow()
                    break
                }
            }
        }
    }

    private fun updateActiveTasksFlow() {
        val current = tasks.values
            .filter { it.isAlive }
            .map { task ->
                TaskSummary(
                    taskId = task.taskId,
                    command = task.command,
                    isAlive = task.isAlive,
                    exitCode = task.exitCode,
                    runtimeSeconds = task.runtimeSeconds,
                    silenceSeconds = task.silenceSeconds,
                    isStalled = task.isStalled,
                    isDaemon = task.isDaemon
                )
            }
        _activeTasksFlow.value = current
        if (current.isNotEmpty()) {
            ensureTicker()
        }
    }

    override suspend fun launch(
        command: String,
        workingDir: File?,
        waitSeconds: Int,
        isBackground: Boolean,
        onOutputChunk: ((String) -> Unit)?
    ): TaskLaunchResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext TaskLaunchResult.Completed("task_0", "", 0)
        }

        // 1. Guard against unbounded task creation on mobile devices (LMK protection)
        val activeCount = tasks.values.count { it.isAlive }
        if (activeCount >= MAX_CONCURRENT_TASKS) {
            return@withContext TaskLaunchResult.Error(
                "Task limit reached ($activeCount/$MAX_CONCURRENT_TASKS active background tasks). " +
                "Please kill or wait for an existing task to finish using manage_task(action='kill', task_id='...')."
            )
        }

        val taskId = "task_${taskCounter.getAndIncrement()}"
        val workDir = workingDir ?: terminalExecutor.currentWorkingDir ?: terminalExecutor.activeDirectory ?: File(".")

        val process: Process = try {
            val pb = terminalExecutor.buildProcessBuilder(trimmed, workDir)
            pb.start()
        } catch (e: Exception) {
            return@withContext TaskLaunchResult.Error("Failed to start process for '$trimmed': ${e.message}")
        }

        val stdinWriter = try {
            BufferedWriter(OutputStreamWriter(process.outputStream))
        } catch (_: Exception) { null }

        val outputBuffer = CircularOutputBuffer()
        val lastOutputTime = AtomicLong(System.currentTimeMillis())
        val startTime = System.currentTimeMillis()

        var currentTaskRef: ManagedTask? = null

        val readerJob = coroutineScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line = reader.readLine()
                while (line != null && isActive) {
                    lastOutputTime.set(System.currentTimeMillis())
                    outputBuffer.append(line)
                    onOutputChunk?.invoke(line)
                    line = reader.readLine()
                }

                val code = process.waitFor()
                currentTaskRef?.exitCode = code
            } catch (_: Exception) {
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                updateActiveTasksFlow()
            }
        }

        val task = ManagedTask(
            taskId = taskId,
            command = trimmed,
            workingDir = workDir,
            process = process,
            startTimeMs = startTime,
            isDaemon = isBackground,
            outputBuffer = outputBuffer,
            stdinWriter = stdinWriter,
            job = readerJob,
            lastOutputTimeMs = lastOutputTime
        )
        currentTaskRef = task
        tasks[taskId] = task
        updateActiveTasksFlow()

        // 2. If explicitly requested as background daemon, return immediately
        if (isBackground) {
            return@withContext TaskLaunchResult.Running(
                taskId = taskId,
                command = trimmed,
                initialOutput = "(Task launched in background)",
                waitSeconds = 0
            )
        }

        // 3. Grace period wait: wait up to waitSeconds for fast synchronous completion
        val waitMs = waitSeconds * 1000L
        val waitIntervalMs = 100L
        var elapsed = 0L

        while (elapsed < waitMs) {
            if (!task.isAlive) {
                // Completed during grace period; wait briefly for reader to finish reading stdout/stderr
                try {
                    withTimeoutOrNull(500) { readerJob.join() }
                } catch (_: Exception) {}
                val finalOutput = outputBuffer.getFullText().trimEnd()
                return@withContext TaskLaunchResult.Completed(
                    taskId = taskId,
                    output = if (finalOutput.isNotEmpty()) finalOutput else "(command finished with exit code ${task.exitCode ?: 0})",
                    exitCode = task.exitCode ?: 0
                )
            }
            delay(waitIntervalMs)
            elapsed += waitIntervalMs
        }

        // 4. Still running after grace period! Auto-background the task and return control to the agent
        val recentTail = outputBuffer.getRecentLines(30).joinToString("\n").trimEnd()
        TaskLaunchResult.Running(
            taskId = taskId,
            command = trimmed,
            initialOutput = recentTail,
            waitSeconds = waitSeconds
        )
    }

    override fun getStatus(taskId: String): TaskStatusInfo? {
        val task = tasks[taskId] ?: return null
        val recent = task.outputBuffer.getRecentLines(50).joinToString("\n")
        return TaskStatusInfo(
            taskId = task.taskId,
            command = task.command,
            isAlive = task.isAlive,
            exitCode = task.exitCode,
            runtimeSeconds = task.runtimeSeconds,
            silenceSeconds = task.silenceSeconds,
            isStalled = task.isStalled,
            recentOutput = recent
        )
    }

    override fun kill(taskId: String): Boolean {
        val task = tasks[taskId] ?: return false
        task.isKilled = true
        task.job.cancel()
        try {
            if (task.process.isAlive) {
                task.process.destroyForcibly()
            }
        } catch (_: Exception) {}
        updateActiveTasksFlow()
        return true
    }

    override fun sendInput(taskId: String, input: String): Boolean {
        val task = tasks[taskId] ?: return false
        if (!task.isAlive || task.stdinWriter == null) return false
        return try {
            task.stdinWriter.write(input)
            if (!input.endsWith("\n")) {
                task.stdinWriter.write("\n")
            }
            task.stdinWriter.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun list(): List<TaskSummary> {
        return tasks.values.map { task ->
            TaskSummary(
                taskId = task.taskId,
                command = task.command,
                isAlive = task.isAlive,
                exitCode = task.exitCode,
                runtimeSeconds = task.runtimeSeconds,
                silenceSeconds = task.silenceSeconds,
                isStalled = task.isStalled,
                isDaemon = task.isDaemon
            )
        }
    }

    override fun killAll() {
        for (task in tasks.values) {
            if (task.isAlive) {
                task.isKilled = true
                task.job.cancel()
                try {
                    task.process.destroyForcibly()
                } catch (_: Exception) {}
            }
        }
        updateActiveTasksFlow()
    }
}
