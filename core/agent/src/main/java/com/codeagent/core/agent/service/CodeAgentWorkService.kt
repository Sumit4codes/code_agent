package com.codeagent.core.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CodeAgentWorkService : Service() {

    @Inject
    lateinit var agentWorkManager: AgentWorkManager

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: "Project"
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: ""
                val isGoalMode = intent.getBooleanExtra(EXTRA_IS_GOAL_MODE, false)

                acquireWakeLock()

                val initialNotification = buildProgressNotification(
                    projectName = projectName,
                    prompt = prompt,
                    isGoalMode = isGoalMode,
                    stepCount = 0,
                    currentAction = if (isGoalMode) "Starting autonomous goal..." else "Initializing..."
                )

                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }

                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID_PROGRESS,
                        initialNotification,
                        serviceType
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to startForeground", e)
                    stopSelf()
                    return START_NOT_STICKY
                }

                startObservingState()
            }
            ACTION_CANCEL -> {
                Log.d(TAG, "Received ACTION_CANCEL")
                agentWorkManager.cancelCurrentTask()
                releaseWakeLock()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_COMPLETE -> {
                val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: "Project"
                val changesCount = intent.getIntExtra(EXTRA_CHANGES_COUNT, 0)
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                releaseWakeLock()
                showCompletionNotification(projectName, changesCount, durationMs)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_FAIL -> {
                val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: "Project"
                val error = intent.getStringExtra(EXTRA_ERROR) ?: "Unknown error"
                releaseWakeLock()
                showFailNotification(projectName, error)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startObservingState() {
        observationJob?.cancel()
        observationJob = serviceScope.launch {
            agentWorkManager.activeTaskState.collectLatest { taskState ->
                if (taskState == null) return@collectLatest

                if (taskState.isRunning) {
                    val updatedNotification = buildProgressNotification(
                        projectName = taskState.projectName,
                        prompt = taskState.userPrompt,
                        isGoalMode = taskState.isGoalMode,
                        stepCount = taskState.stepCount,
                        currentAction = taskState.currentAction
                    )
                    try {
                        NotificationManagerCompat.from(this@CodeAgentWorkService)
                            .notify(NOTIFICATION_ID_PROGRESS, updatedNotification)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Missing notification permission", e)
                    }
                } else {
                    releaseWakeLock()
                    if (taskState.isCancelled) {
                        ServiceCompat.stopForeground(this@CodeAgentWorkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (taskState.completedSuccessfully == true) {
                        showCompletionNotification(
                            projectName = taskState.projectName,
                            changesCount = taskState.pendingChanges.size,
                            durationMs = taskState.durationMs
                        )
                        ServiceCompat.stopForeground(this@CodeAgentWorkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (taskState.completedSuccessfully == false) {
                        showFailNotification(
                            projectName = taskState.projectName,
                            errorMessage = taskState.error ?: "Task failed"
                        )
                        ServiceCompat.stopForeground(this@CodeAgentWorkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CodeAgent:WorkServiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(30 * 60 * 1000L) // 30 min safety timeout
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return

            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "Agent Task Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live progress while autonomous agent executes tasks"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                "Agent Task Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when an agent task finishes or fails"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    private fun createOpenAppPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, CodeAgentWorkService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildProgressNotification(
        projectName: String,
        prompt: String,
        isGoalMode: Boolean,
        stepCount: Int = 0,
        currentAction: String = "Initializing..."
    ): Notification {
        val iconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_sync
        val title = if (isGoalMode) "[Goal Mode] $projectName" else "Agent: $projectName"
        val subText = if (stepCount > 0) "Step $stepCount" else if (isGoalMode) "Autonomous Goal" else null

        return NotificationCompat.Builder(this, CHANNEL_ID_PROGRESS)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(currentAction)
            .setSubText(subText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$currentAction\nObjective: ${prompt.take(120)}")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(createOpenAppPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel Task",
                createCancelPendingIntent()
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showCompletionNotification(
        projectName: String,
        changesCount: Int,
        durationMs: Long
    ) {
        val iconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_sync
        val durationText = formatDuration(durationMs)
        val text = if (changesCount > 0) {
            "$changesCount changes proposed in $durationText. Tap to review."
        } else {
            "Finished in $durationText with no pending changes."
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setSmallIcon(iconRes)
            .setContentTitle("Agent Task Complete - $projectName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    private fun showFailNotification(
        projectName: String,
        errorMessage: String
    ) {
        val iconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_sync

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setSmallIcon(iconRes)
            .setContentTitle("Agent Task Failed - $projectName")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error: $errorMessage"))
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CodeAgentWorkService"
        const val CHANNEL_ID_PROGRESS = "channel_agent_progress"
        const val CHANNEL_ID_COMPLETE = "channel_agent_complete"

        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002

        const val ACTION_START = "com.codeagent.core.agent.service.START"
        const val ACTION_CANCEL = "com.codeagent.core.agent.service.CANCEL"
        const val ACTION_COMPLETE = "com.codeagent.core.agent.service.COMPLETE"
        const val ACTION_FAIL = "com.codeagent.core.agent.service.FAIL"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_IS_GOAL_MODE = "extra_is_goal_mode"
        const val EXTRA_CHANGES_COUNT = "extra_changes_count"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_ERROR = "extra_error"

        fun startWork(
            context: Context,
            taskId: String,
            projectName: String,
            prompt: String,
            isGoalMode: Boolean
        ) {
            try {
                val intent = Intent(context, CodeAgentWorkService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_PROJECT_NAME, projectName)
                    putExtra(EXTRA_PROMPT, prompt)
                    putExtra(EXTRA_IS_GOAL_MODE, isGoalMode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not start CodeAgentWorkService", e)
            }
        }

        fun cancelWork(context: Context) {
            val intent = Intent(context, CodeAgentWorkService::class.java).apply {
                action = ACTION_CANCEL
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not send cancel action", e)
            }
        }

        fun completeWork(
            context: Context,
            projectName: String,
            changesCount: Int,
            durationMs: Long
        ) {
            val intent = Intent(context, CodeAgentWorkService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_CHANGES_COUNT, changesCount)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not send complete action", e)
            }
        }

        fun failWork(
            context: Context,
            projectName: String,
            errorMessage: String
        ) {
            val intent = Intent(context, CodeAgentWorkService::class.java).apply {
                action = ACTION_FAIL
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_ERROR, errorMessage)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not send fail action", e)
            }
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSecs = durationMs / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        }
    }
}
