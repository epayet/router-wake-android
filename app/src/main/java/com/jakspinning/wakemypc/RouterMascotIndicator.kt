package com.jakspinning.wakemypc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Same 108x108 path data as the router mascot in ic_launcher_foreground.xml —
// the in-app status indicator IS the app icon, not a separate graphic that
// has to be kept in sync by hand.
private const val VIEWPORT_SIZE = 108f

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

private val bodyPath = path(
    "M32,44 L76,44 A6,6 0 0 1 82,50 L82,72 A6,6 0 0 1 76,78 L32,78 A6,6 0 0 1 26,72 L26,50 A6,6 0 0 1 32,44 Z",
)
private val antennaLeftPath = path("M38,44 L30,24")
private val antennaRightPath = path("M70,44 L78,24")
private val antennaTipLeftPath = path("M27,24 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0")
private val antennaTipRightPath = path("M75,24 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0")
private val signalArcLeftPath = path("M18,30 a12,12 0 0,1 8,-16")
private val signalArcRightPath = path("M90,30 a12,12 0 0,0 -8,-16")
private val footLeftPath = path("M34,78 L28,84")
private val footRightPath = path("M74,78 L80,84")
private val eyeLeftPath = path("M40,57 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0")
private val eyeRightPath = path("M62,57 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0")
private val mouthPath = path("M51,66 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0")
private val ledPaths = listOf(
    path("M36,73 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"),
    path("M52,73 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"),
    path("M68,73 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"),
)

/**
 * The launcher icon's router mascot, redrawn as the "is my PC on" indicator.
 * Unlike the (always-yellow) launcher icon, this one's whole plate changes
 * color with [backgroundColor] — yellow when on, blue when off/unknown, red
 * on error — so the state is obvious at a glance; [lineColor] keeps the
 * mascot's linework readable against whichever plate color is showing.
 *
 * Tapping it gives it a little startled shake — purely for delight, [onClick]
 * fires alongside it for anything the caller wants to do (e.g. a quip).
 */
@Composable
fun RouterMascotIndicator(
    backgroundColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .size(80.dp)
            .rotate(rotation.value)
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onClick()
                scope.launch {
                    rotation.animateTo(-14f, tween(60))
                    rotation.animateTo(12f, tween(90))
                    rotation.animateTo(-8f, tween(90))
                    rotation.animateTo(0f, tween(80))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val scaleFactor = size.minDimension / VIEWPORT_SIZE
            scale(scaleFactor, pivot = Offset.Zero) {
                drawPath(bodyPath, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
                drawPath(antennaLeftPath, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                drawPath(antennaRightPath, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                drawPath(antennaTipLeftPath, color = lineColor)
                drawPath(antennaTipRightPath, color = lineColor)
                drawPath(signalArcLeftPath, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                drawPath(signalArcRightPath, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                drawPath(footLeftPath, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                drawPath(footRightPath, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                drawPath(eyeLeftPath, color = lineColor)
                drawPath(eyeRightPath, color = lineColor)
                drawPath(mouthPath, color = lineColor, style = Stroke(width = 3f))
                ledPaths.forEach { drawPath(it, color = lineColor) }
            }
        }
    }
}
