package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.Message
import com.koda.dev.core.model.MessageRole
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.ui.components.MarkdownText
import com.koda.dev.core.ui.glass.springPressClickable
import kotlinx.coroutines.delay

@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    pendingChanges: List<PendingChange> = emptyList(),
    onApproveChange: ((PendingChange) -> Unit)? = null,
    onRejectChange: ((PendingChange) -> Unit)? = null,
    onOpenFullReview: ((PendingChange?) -> Unit)? = null
) {
    when (message.role) {
        MessageRole.USER -> UserMessageBubble(message = message, modifier = modifier)
        MessageRole.ASSISTANT -> AssistantMessageBubble(
            message = message,
            modifier = modifier,
            isLive = isLive,
            pendingChanges = pendingChanges,
            onApproveChange = onApproveChange,
            onRejectChange = onRejectChange,
            onOpenFullReview = onOpenFullReview
        )
        MessageRole.TOOL -> {
            // Tool results are seamlessly integrated into preceding Assistant AgentTimelineView
        }
        MessageRole.SYSTEM -> {
            // Minimal system notification
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF21262D)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modern user message card matching the reference design:
 * Clean dark graphite container (#21262D), subtle border, high readability, right-aligned.
 */
@Composable
fun UserMessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val isSteering = remember(message.content) {
        message.content.contains("<user_steering_guidance>")
    }
    val displayContent = remember(message.content, isSteering) {
        if (isSteering) {
            message.content
                .replace("<user_steering_guidance>", "")
                .replace("</user_steering_guidance>", "")
                .trim()
        } else {
            message.content
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 6.dp
        )
        val bubbleBackground = if (isSteering) {
            Brush.linearGradient(
                listOf(
                    Color(0xFF1F6FEB).copy(alpha = 0.40f),
                    Color(0xFF0F172A).copy(alpha = 0.85f)
                )
            )
        } else {
            GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.08f)
        }
        val bubbleBorder = if (isSteering) {
            GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.75f, dispersionAlpha = 0.35f)
        } else {
            GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f)
        }

        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = bubbleShape,
                    ambientColor = Color(0x30000000),
                    spotColor = Color(0x600F172A)
                )
                .clip(bubbleShape)
                .background(bubbleBackground)
                .border(1.dp, bubbleBorder, bubbleShape)
        ) {
            // Internal top highlight sheen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (isSteering) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFF58A6FF)
                        )
                        Text(
                            text = "Mid-Run Steering",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF58A6FF)
                        )
                    }
                }

                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = Color(0xFFF0F6FC)
                )
            }
        }
    }
}

/**
 * Modern assistant message stream representation matching the reference design:
 * Flows directly in the stream without bounding boxes or robot avatars.
 * - Collapsible "Worked for Xm ⌄" timeline for background steps
 * - "N file(s) changed +X -Y [Review]" diff pill
 * - Direct markdown text
 * - Clean footer with relative time and action buttons
 */
@Composable
fun AssistantMessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    pendingChanges: List<PendingChange> = emptyList(),
    onApproveChange: ((PendingChange) -> Unit)? = null,
    onRejectChange: ((PendingChange) -> Unit)? = null,
    onOpenFullReview: ((PendingChange?) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var userRating by remember { mutableStateOf<Boolean?>(null) } // true: liked, false: disliked, null: none

    val relativeTime = remember(message.timestamp) {
        formatRelativeTime(message.timestamp)
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Collapsible Activity Timeline ("Worked for 1m ⌄")
        if (message.toolCalls.isNotEmpty()) {
            AgentTimelineView(
                toolCalls = message.toolCalls,
                isLive = isLive,
                messageId = message.id
            )
        }

        // 2. Diff Review Pill ("1 file changed +103 -22  ›   [ Review ]")
        if (pendingChanges.isNotEmpty()) {
            FileChangesPill(
                pendingChanges = pendingChanges,
                onOpenFullReview = { onOpenFullReview?.invoke(null) }
            )
        }

        // 3. Assistant Output Markdown Text
        if (message.content.isNotBlank()) {
            MarkdownText(content = message.content)
        }

        // 4. Clean Turn Footer: Relative timestamp + Copy + Feedback actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (relativeTime.isNotBlank()) {
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = Color(0xFF8B949E)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Copy action button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = if (copied) 0.22f else 0.12f, bottomAlpha = 0.04f))
                    .border(0.5.dp, GlassDesign.microGlassBorder(topAlpha = if (copied) 0.50f else 0.25f, bottomAlpha = 0.08f), CircleShape)
                    .springPressClickable {
                        clipboardManager.setText(AnnotatedString(message.content))
                        copied = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    modifier = Modifier.size(14.dp),
                    tint = if (copied) Color(0xFF3FB950) else Color(0xFF8B949E)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Thumbs Up button
            val isLiked = userRating == true
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = if (isLiked) 0.22f else 0.12f, bottomAlpha = 0.04f))
                    .border(0.5.dp, GlassDesign.microGlassBorder(topAlpha = if (isLiked) 0.50f else 0.25f, bottomAlpha = 0.08f), CircleShape)
                    .springPressClickable {
                        userRating = if (isLiked) null else true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Helpful",
                    modifier = Modifier.size(14.dp),
                    tint = if (isLiked) Color(0xFF58A6FF) else Color(0xFF8B949E)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Thumbs Down button
            val isDisliked = userRating == false
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = if (isDisliked) 0.22f else 0.12f, bottomAlpha = 0.04f))
                    .border(0.5.dp, GlassDesign.microGlassBorder(topAlpha = if (isDisliked) 0.50f else 0.25f, bottomAlpha = 0.08f), CircleShape)
                    .springPressClickable {
                        userRating = if (isDisliked) null else false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Not helpful",
                    modifier = Modifier.size(14.dp),
                    tint = if (isDisliked) Color(0xFFF85149) else Color(0xFF8B949E)
                )
            }
        }
    }
}
