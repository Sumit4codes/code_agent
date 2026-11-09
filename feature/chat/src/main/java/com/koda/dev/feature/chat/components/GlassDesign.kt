package com.koda.dev.feature.chat.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.koda.dev.core.ui.glass.captureBackdrop as coreCaptureBackdrop
import com.koda.dev.core.ui.glass.liquidGlassBackdrop as coreLiquidGlassBackdrop
import com.koda.dev.core.ui.glass.springPress as coreSpringPress
import com.koda.dev.core.ui.glass.springPressClickable as coreSpringPressClickable

// Common liquid glass system re-exported from core:ui
typealias BackdropState = com.koda.dev.core.ui.glass.BackdropState
typealias GlassDesign = com.koda.dev.core.ui.glass.GlassDesign

@Composable
fun rememberBackdropState(): BackdropState =
    com.koda.dev.core.ui.glass.rememberBackdropState()

fun Modifier.captureBackdrop(state: BackdropState): Modifier =
    this.coreCaptureBackdrop(state)

@Composable
fun Modifier.liquidGlassBackdrop(
    backdropState: BackdropState?,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 24.dp,
    tintColor: Color = Color(0xC010141E),
    specularBorder: Brush = GlassDesign.liquidGlassBorder(),
    borderWidth: Dp = 1.dp
): Modifier = this.coreLiquidGlassBackdrop(
    backdropState = backdropState,
    shape = shape,
    blurRadius = blurRadius,
    tintColor = tintColor,
    specularBorder = specularBorder,
    borderWidth = borderWidth
)

@Composable
fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.95f,
    enabled: Boolean = true
): Modifier = this.coreSpringPress(
    interactionSource = interactionSource,
    targetScale = targetScale,
    enabled = enabled
)

@Composable
fun Modifier.springPressClickable(
    enabled: Boolean = true,
    targetScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = this.coreSpringPressClickable(
    enabled = enabled,
    targetScale = targetScale,
    onClick = onClick
)
