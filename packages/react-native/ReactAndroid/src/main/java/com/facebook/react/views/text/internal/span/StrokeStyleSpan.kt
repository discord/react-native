/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.views.text.internal.span

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.view.Gravity

/**
 * Renders an outer text stroke via two per-run passes driven from the text view's `onDraw`.
 *
 * The span is a `CharacterStyle` so it participates in `TextLine.handleRun`'s per-run
 * `updateDrawState(wp)` pipeline on every line. During [draw] we flip [isStrokePass] and re-run
 * the layout draw twice:
 *  1. Stroke pass: [updateDrawState] sets `STROKE`/`strokeWidth` and a `SRC_IN` color filter so
 *     every run (on every line) is drawn as an outline in [color].
 *  2. Fill pass: [updateDrawState] is a no-op, so normal per-run spans (`ReactForegroundColorSpan`
 *     etc.) render the text fill on top of the stroke.
 *
 * Using `updateDrawState` rather than mutating the shared `Paint` before `Layout.draw` is what
 * makes the stroke reliably apply to all lines: each run's working `TextPaint` is re-derived via
 * `wp.set(mPaint)` + `span.updateDrawState(wp)` at run time, so our stroke configuration can't be
 * lost between lines.
 */
public class StrokeStyleSpan(
    public val width: Float,
    public val color: Int
) : CharacterStyle(), ReactSpan {

  // Flipped by [draw] around each pass. Volatile isn't needed: the text view draws on the UI
  // thread and `Layout.draw` synchronously invokes `updateDrawState` on the same thread.
  private var isStrokePass: Boolean = false

  override fun updateDrawState(textPaint: TextPaint) {
    // Fill pass is a no-op so other spans (e.g. `ReactForegroundColorSpan`) render normally.
    if (!isStrokePass) return
    textPaint.style = Paint.Style.STROKE
    textPaint.strokeWidth = width
    textPaint.strokeJoin = Paint.Join.ROUND
    textPaint.strokeCap = Paint.Cap.ROUND
    // `SRC_IN` forces the stroke color to win over any per-run foreground color, matching iOS
    // `kCGTextStroke`.
    textPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
  }

  public fun hasStroke(): Boolean = width > 0 && color != 0

  public fun getLeftOffset(): Float = if (hasStroke()) width / 2f else 0f

  /**
   * Runs [drawCallback] twice (stroke then fill) with [isStrokePass] toggled around each call.
   * The [paint] parameter is unused - per-run paint state is applied via [updateDrawState] -
   * but the signature is kept so existing call sites don't need to change.
   *
   * Returns `true` when the stroke was drawn, `false` when [hasStroke] is false and the caller
   * should draw normally instead.
   */
  public fun draw(paint: Paint, drawCallback: Runnable): Boolean {
    if (!hasStroke()) {
      return false
    }

    isStrokePass = true
    try {
      drawCallback.run()
    } finally {
      isStrokePass = false
    }
    drawCallback.run()

    return true
  }

  public companion object {
    /**
     * Returns the first [StrokeStyleSpan] in [spanned], or `null` if none is present.
     *
     * Only the first stroke per text run is consulted by [draw] and by the measurement-side
     * halo reservation ([getStrokeWidth]) so the two stay consistent. Fragments that set
     * `textStrokeWidth` still get their own `StrokeStyleSpan` (one per fragment), but because
     * our per-run `updateDrawState` is range-scoped, only the characters covered by a span
     * actually render with stroke - matching iOS `RCTTextAttributes` behavior.
     */
    @JvmStatic
    public fun getStrokeSpan(spanned: Spanned?): StrokeStyleSpan? {
      if (spanned == null) return null
      val spans = spanned.getSpans(0, spanned.length, StrokeStyleSpan::class.java)
      return spans.firstOrNull()
    }

    /**
     * Stroke width to reserve around the text for halo padding, or 0 if no [StrokeStyleSpan] is
     * present. Drives [com.facebook.react.views.text.TextLayoutManager.applyStrokePadding] and
     * the draw-time top-halo shift so measurement and draw can't drift.
     */
    @JvmStatic
    public fun getStrokeWidth(spanned: Spanned?): Float = getStrokeSpan(spanned)?.width ?: 0f

    /**
     * Returns the Y shift to apply so the top stroke halo fits inside the reservation that
     * `TextLayoutManager.applyStrokePadding` added to the measured height. Clamped to the actual
     * vertical slack of the view so EXACTLY-sized parents and tight AT_MOST constraints don't
     * push glyphs past the view bounds. Used from the text view's `onDraw` (to translate the
     * canvas) and `onLayout` (to keep inline attachments aligned with the text).
     *
     * Returns 0 when [strokeSpan] is null or has no stroke so non-stroke text is unaffected.
     */
    @JvmStatic
    public fun strokeShift(
        strokeSpan: StrokeStyleSpan?,
        layout: Layout?,
        viewHeight: Int,
        paddingTop: Int,
        paddingBottom: Int,
        gravity: Int,
    ): Float {
      if (strokeSpan == null) return 0f
      if (layout == null) return 0f
      val strokeWidth = strokeSpan.width
      if (strokeWidth <= 0f) return 0f
      val slack = (viewHeight - paddingTop - paddingBottom - layout.height).toFloat()
      if (slack <= 0f) return 0f
      val baseOffset =
          when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> slack / 2f
            Gravity.BOTTOM -> slack
            else -> 0f
          }
      val target =
          if (slack >= strokeWidth) {
            baseOffset.coerceIn(strokeWidth / 2f, slack - strokeWidth / 2f)
          } else {
            slack / 2f
          }
      return target - baseOffset
    }
  }
}
