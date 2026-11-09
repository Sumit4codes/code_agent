package com.koda.dev.core.ui.glass

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * State holding the captured backdrop layer and its layout coordinates in the window.
 * Passed to [Modifier.captureBackdrop] on the background surface (e.g. project list or chat stream)
 * and [Modifier.liquidGlassBackdrop] on foreground floating glass decks/capsules.
 */
@Stable
class BackdropState {
    var graphicsLayer: GraphicsLayer? = null
    var coordinates: LayoutCoordinates? = null
    var drawVersion by mutableLongStateOf(0L)
}

/**
 * Creates and remembers a [BackdropState] scoped to the current composition.
 */
@Composable
fun rememberBackdropState(): BackdropState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) {
        BackdropState().apply { this.graphicsLayer = graphicsLayer }
    }
}

/**
 * Captures the target composable content into [state.graphicsLayer] during its draw pass,
 * allowing overlay glass components to sample, blur, and refract the background in real-time.
 */
fun Modifier.captureBackdrop(state: BackdropState): Modifier = this
    .onGloballyPositioned { coords ->
        if (coords.isAttached) {
            state.coordinates = coords
        }
    }
    .drawWithContent {
        val layer = state.graphicsLayer
        if (layer != null && size.width > 0f && size.height > 0f) {
            layer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            state.drawVersion++
            drawLayer(layer)
        } else {
            drawContent()
        }
    }

/**
 * Liquid Glass AGSL Shader (API 33+)
 *
 * Implements real physical lens optics inspired by Apple Liquid Glass and QWEA0/Liquid-Glass-Android:
 * - Real-time Signed Distance Field (SDF) of the capsule with per-quadrant corner radii
 * - Numerical gradient normal vector (\nabla SDF)
 * - Spherical lens curvature refraction along normal gradient
 * - Chromatic dispersion along optical refraction gradient
 */
private const val GLASS_LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, float2(0.0))) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 m = max(cornerCoord, float2(0.0));
        float len = length(m);
        if (len > 0.0001) {
            return sign(coord) * (m / len);
        } else {
            return sign(coord) * float2(0.7071, 0.7071);
        }
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(clamp(1.0 - -sd / refractionHeight, 0.0, 1.0)) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 g = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float gLen = length(g);
    float2 grad = (gLen > 0.0001) ? (g / gLen) : float2(0.0, -1.0);
    
    float2 refractedCoord = coord + d * grad;
    
    // Chromatic dispersion along optical gradient (subtle RGB split at edges)
    float2 disp = d * grad * 0.035;
    half4 r = content.eval(refractedCoord + disp);
    half4 g = content.eval(refractedCoord);
    half4 b = content.eval(refractedCoord - disp);
    
    return half4(r.r, g.g, b.b, g.a);
}
"""

/**
 * Converts an [Outline] into a Compose [Path].
 */
private fun Path.addOutline(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

/**
 * Apple-inspired Liquid Glass modifier with hardware backdrop blur and AGSL optical lens refraction.
 *
 * Captures the slice of the [backdropState] directly beneath this component, blurs it using
 * hardware [android.graphics.RenderEffect.createBlurEffect] (API 31+), refracts the edges using AGSL (API 33+),
 * tints the surface with dark slate glass tint for contrast, and renders foreground contents with 100% crispness.
 */
@Composable
fun Modifier.liquidGlassBackdrop(
    backdropState: BackdropState?,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 24.dp,
    tintColor: Color = Color(0xC010141E), // 75% dark optical slate glass
    specularBorder: Brush = GlassDesign.liquidGlassBorder(),
    borderWidth: Dp = 1.dp
): Modifier {
    val localLayer = rememberGraphicsLayer()
    var localCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }

    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(GLASS_LENS_AGSL)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    return this
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                localCoordinates = coords
            }
        }
        .drawWithContent {
            // Observe backdrop draw version to trigger redraw when background scrolls or changes
            backdropState?.drawVersion

            val backdropLayer = backdropState?.graphicsLayer
            val backdropCoords = backdropState?.coordinates
            val currentCoords = localCoordinates

            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }

            if (backdropLayer != null && backdropCoords != null && currentCoords != null &&
                currentCoords.isAttached && backdropCoords.isAttached &&
                size.width > 0f && size.height > 0f
            ) {
                val offset = try {
                    backdropCoords.localPositionOf(currentCoords)
                } catch (_: Exception) {
                    currentCoords.positionInWindow() - backdropCoords.positionInWindow()
                }

                // Record the slice of the backdrop directly behind this glass component
                localLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                    withTransform({
                        translate(-offset.x, -offset.y)
                    }) {
                        drawLayer(backdropLayer)
                    }
                }

                // Apply hardware blur and AGSL lens refraction
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = android.graphics.RenderEffect.createBlurEffect(
                        blurRadiusPx,
                        blurRadiusPx,
                        android.graphics.Shader.TileMode.CLAMP
                    )

                    val finalEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
                        try {
                            runtimeShader.setFloatUniform("size", size.width, size.height)
                            if (outline is Outline.Rounded) {
                                val rr = outline.roundRect
                                runtimeShader.setFloatUniform(
                                    "cornerRadii",
                                    rr.topLeftCornerRadius.x,
                                    rr.topRightCornerRadius.x,
                                    rr.bottomRightCornerRadius.x,
                                    rr.bottomLeftCornerRadius.x
                                )
                            } else {
                                val r = min(size.width, size.height) * 0.25f
                                runtimeShader.setFloatUniform("cornerRadii", r, r, r, r)
                            }
                            runtimeShader.setFloatUniform("refractionHeight", with(density) { 18.dp.toPx() })
                            runtimeShader.setFloatUniform("refractionAmount", with(density) { 14.dp.toPx() })

                            val shaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(
                                runtimeShader,
                                "content"
                            )
                            android.graphics.RenderEffect.createChainEffect(shaderEffect, blur)
                        } catch (_: Throwable) {
                            blur
                        }
                    } else {
                        blur
                    }

                    localLayer.renderEffect = finalEffect.asComposeRenderEffect()
                }

                // Draw the blurred and optically refracted backdrop slice clipped to the shape
                clipPath(path) {
                    drawLayer(localLayer)
                }
            } else {
                // High-density optical gradient fallback when backdrop is unavailable
                drawPath(path, brush = GlassDesign.liquidGlassBackground())
            }

            // Draw dark slate optical tint to maintain rich contrast under text
            drawPath(path, color = tintColor)

            // Draw unblurred crisp foreground contents (BasicTextField, buttons, etc.)
            drawContent()

            // Draw top specular light-catch rim
            drawPath(
                path,
                brush = specularBorder,
                style = Stroke(width = borderWidth.toPx())
            )
        }
}

/**
 * Apple-inspired Liquid Glass design tokens and modifiers.
 */
object GlassDesign {

    /**
     * Optical frosted glass gradient fallback for floating cards and capsules.
     */
    fun liquidGlassBackground(
        topAlpha: Float = 0.96f,
        midAlpha: Float = 0.93f,
        bottomAlpha: Float = 0.88f
    ): Brush = Brush.verticalGradient(
        listOf(
            Color(0xFF242C3C).copy(alpha = topAlpha),
            Color(0xFF19202D).copy(alpha = midAlpha),
            Color(0xFF101520).copy(alpha = bottomAlpha)
        )
    )

    /**
     * Refined specular light-catch rim.
     * Crisp, bright overhead apex highlight with subtle cool dispersion tint.
     */
    fun liquidGlassBorder(
        topSpecularAlpha: Float = 0.65f,
        dispersionAlpha: Float = 0.28f,
        lateralAlpha: Float = 0.14f,
        bottomAlpha: Float = 0.08f
    ): Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = topSpecularAlpha),
            Color(0xFF79C0FF).copy(alpha = dispersionAlpha),
            Color.White.copy(alpha = lateralAlpha),
            Color.White.copy(alpha = bottomAlpha)
        )
    )

    /**
     * Soft internal refractive sheen for the upper curve of glass capsules,
     * giving the surface physical thickness and depth.
     */
    fun internalGlassSheen(): Brush = Brush.verticalGradient(
        listOf(
            Color(0x33FFFFFF), // 20% soft light catch
            Color(0x1479C0FF), // 8% optical dispersion
            Color.Transparent
        )
    )

    /**
     * Micro-glass translucent fill for chips, pills, and secondary controls.
     */
    fun microGlassBackground(
        topAlpha: Float = 0.16f,
        bottomAlpha: Float = 0.07f
    ): Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = topAlpha),
            Color.White.copy(alpha = bottomAlpha)
        )
    )

    /**
     * Micro-glass specular border for chips and pills.
     */
    fun microGlassBorder(
        topAlpha: Float = 0.32f,
        bottomAlpha: Float = 0.10f
    ): Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = topAlpha),
            Color.White.copy(alpha = bottomAlpha)
        )
    )
}

/**
 * Tactile spring press feedback that depresses the element on pointer-down
 * using a critically damped spring (Apple Design §1 & §4).
 */
@Composable
fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.95f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy, // 1.0 = critically damped
            stiffness = Spring.StiffnessMedium
        ),
        label = "spring_press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Convenience modifier combining spring-based press scaling with a click handler.
 */
@Composable
fun Modifier.springPressClickable(
    enabled: Boolean = true,
    targetScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .springPress(interactionSource = interactionSource, targetScale = targetScale, enabled = enabled)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
