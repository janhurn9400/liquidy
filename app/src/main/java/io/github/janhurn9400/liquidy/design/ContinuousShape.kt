package io.github.janhurn9400.liquidy.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Apple's "continuous corner" rectangle (G2-continuous corner), RESEARCH §2.7.
 * Bézier constants after Liam Rosenfeld / UIBezierPath reverse engineering;
 * the corner starts 1.52866 × r from the edge.
 */
class ContinuousRoundedShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(continuousRoundedRectPath(size.width, size.height, with(density) { cornerRadius.toPx() }))
}

private const val EXTENT = 1.52866483f

// Top-left corner, from the left edge (0, EXTENT) to the top edge (EXTENT, 0), in units of r.
// Per segment: cp1x, cp1y, cp2x, cp2y, endx, endy
private val FORWARD = floatArrayOf(
    0f, 1.08849323f, 0f, 0.86840701f, 0.07491100f, 0.63149399f,
    0.16905956f, 0.37282401f, 0.37282401f, 0.16905956f, 0.63149399f, 0.07491100f,
    0.86840701f, 0f, 1.08849323f, 0f, EXTENT, 0f,
)
// The same corner backwards (from the top to the left edge)
private val REVERSED = floatArrayOf(
    1.08849323f, 0f, 0.86840701f, 0f, 0.63149399f, 0.07491100f,
    0.37282401f, 0.16905956f, 0.16905956f, 0.37282401f, 0.07491100f, 0.63149399f,
    0f, 0.86840701f, 0f, 1.08849323f, 0f, EXTENT,
)

fun continuousRoundedRectPath(w: Float, h: Float, radius: Float): Path {
    val r = min(radius, min(w, h) / 2f / EXTENT).coerceAtLeast(0f)
    val path = Path()
    if (r <= 0f) {
        path.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        return path
    }
    fun corner(ox: Float, oy: Float, sx: Float, sy: Float, pts: FloatArray) {
        var i = 0
        while (i < pts.size) {
            path.cubicTo(
                ox + sx * pts[i] * r, oy + sy * pts[i + 1] * r,
                ox + sx * pts[i + 2] * r, oy + sy * pts[i + 3] * r,
                ox + sx * pts[i + 4] * r, oy + sy * pts[i + 5] * r,
            )
            i += 6
        }
    }
    val e = EXTENT * r
    path.moveTo(e, 0f)
    path.lineTo(w - e, 0f)
    corner(w, 0f, -1f, 1f, REVERSED)   // top right
    path.lineTo(w, h - e)
    corner(w, h, -1f, -1f, FORWARD)    // bottom right
    path.lineTo(e, h)
    corner(0f, h, 1f, -1f, REVERSED)   // bottom left
    path.lineTo(0f, e)
    corner(0f, 0f, 1f, 1f, FORWARD)    // top left
    path.close()
    return path
}
