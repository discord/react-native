package com.facebook.react.views.text.internal.span

import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import com.facebook.react.uimanager.PixelUtil.dpToPx

/**
 * Span that applies a linear gradient to text.
 *
 * @param start The x-offset for the gradient start position
 * @param colors Array of gradient colors
 * @param angle Gradient angle in degrees (0 = horizontal, 90 = vertical)
 * @param gradientLength Length of the gradient along its axis, in DP. This is the distance over which
 *   the color stops transition (text width for a horizontal gradient, text/line height for a vertical
 *   one). Default is 100px.
 * @param gradientMode "mirror" (default) or "clamp" - controls tiling behavior
 */
public class LinearGradientSpan(
    private val start: Float,
    private val colors: IntArray,
    private val angle: Float = 0f,
    private val gradientLength: Float = Float.NaN,
    private val gradientMode: String? = "mirror",
) : CharacterStyle(), ReactSpan,
    UpdateAppearance {
    public override fun updateDrawState(tp: TextPaint) {
        val tileMode = if (gradientMode == "clamp") Shader.TileMode.CLAMP else Shader.TileMode.MIRROR

        // without setting the paint color, the gradient appears "faded" if no foreground color span is also applied
        // https://stackoverflow.com/a/52289927
        tp.setColor(colors[0])

        val radians = Math.toRadians(angle.toDouble())
        val axisLength = if (gradientLength.isNaN()) 100f else gradientLength.dpToPx()
        val half = axisLength / 2f
        val cos = Math.cos(radians).toFloat()
        val sin = Math.sin(radians).toFloat()

        // Anchor the gradient so it spans [0, axisLength] along its axis. Keep the start offset on the
        // (horizontal) tiling axis for mirror mode; clamp mode ignores it.
        val centerX = if (tileMode == Shader.TileMode.MIRROR) start + half else half
        val centerY = half

        val startX = centerX - half * cos
        val startY = centerY - half * sin
        val endX = centerX + half * cos
        val endY = centerY + half * sin

        var adjustedColors = colors
        if (tileMode == Shader.TileMode.MIRROR) {
          // Mirror mode duplicates the first color at the end.
          adjustedColors = IntArray(colors.size + 1)
          System.arraycopy(colors, 0, adjustedColors, 0, colors.size)
          adjustedColors[colors.size] = colors[0]
        }

        val textShader: Shader =
            LinearGradient(
                startX,
                startY,
                endX,
                endY,
                adjustedColors,
                null,
                tileMode,
            )
        tp.setShader(textShader)
    }
}
