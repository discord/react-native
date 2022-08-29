/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.views.text;

import android.graphics.Paint;
import android.text.style.LineHeightSpan;

/**
 * We use a custom {@link LineHeightSpan}, because `lineSpacingExtra` is broken. Details here:
 * https://github.com/facebook/react-native/issues/7546
 */
public class CustomLineHeightSpan implements LineHeightSpan, ReactSpan {
  private final int mHeight;

  public CustomLineHeightSpan(float height) {
    this.mHeight = (int) Math.ceil(height);
  }

  @Override
  public void chooseHeight(
    CharSequence text, int start, int end, int spanstartv, int v, Paint.FontMetricsInt fm) {

    if (OVERRIDE_LINE_HEIGHT) {
      overrideLineHeight(fm);
      return;
    }

    // This is more complicated that I wanted it to be. You can find a good explanation of what the
    // FontMetrics mean here: http://stackoverflow.com/questions/27631736.
    // The general solution is that if there's not enough height to show the full line height, we
    // will prioritize in this order: descent, ascent, bottom, top

    if (fm.descent > mHeight) {
      // Show as much descent as possible
      fm.bottom = fm.descent = Math.min(mHeight, fm.descent);
      fm.top = fm.ascent = 0;
    } else if (-fm.ascent + fm.descent > mHeight) {
      // Show all descent, and as much ascent as possible
      fm.bottom = fm.descent;
      fm.top = fm.ascent = -mHeight + fm.descent;
    } else if (-fm.ascent + fm.bottom > mHeight) {
      // Show all ascent, descent, as much bottom as possible
      fm.top = fm.ascent;
      fm.bottom = fm.ascent + mHeight;
    } else if (-fm.top + fm.bottom > mHeight) {
      // Show all ascent, descent, bottom, as much top as possible
      fm.top = fm.bottom - mHeight;
    } else {
      // Show proportionally additional ascent / top & descent / bottom
      final int additional = mHeight - (-fm.top + fm.bottom);

      // Round up for the negative values and down for the positive values  (arbitrary choice)
      // So that bottom - top equals additional even if it's an odd number.
      fm.top -= Math.ceil(additional / 2.0f);
      fm.bottom += Math.floor(additional / 2.0f);
      fm.ascent = fm.top;
      fm.descent = fm.bottom;
    }
  }

  private final static boolean OVERRIDE_LINE_HEIGHT = true;

  /**
   * Discord Story time!
   *
   * So since we decided to be _very_ flexible with channel names, users have decided that they were gonna name their channels
   * shit like ‧͙⁺˚･༓☾text☽༓･˚⁺‧͙ | l̶̟̦͚͎̦͑̎m̵̮̥̫͕͚̜̱̫̺̪͍̯̉̂̔͌́̚̕a̶͖̫͍͇̯̯̭͎͋̅́̿́̕͘͘͝͝ò̶̧̢͎̃̋͆̉͠ | and other fun non-standard channel names.
   *
   * This caused issues with line heights, because the RN implementation decided that it would try as best as possible to
   * fit the text within the lineHeight that was given to it by the react component, causing text to be shifted upward
   * and look terrible (see: https://canary.discord.com/channels/281683040739262465/912423796915462154/101286117376867126
   * for an example).
   *
   * We (Jerry + Charles) decided that to fix this issue, we would instead ignore lineHeights _only_ if the text
   * height was larger than the lineHeight provided to it.
   *
   * This is a much simpler implementation that what was previously here.
   *
   * _IF_ the lineHeight is larger than the text height, we default to centering the text as much as possible within
   * that line height.
   */
  private void overrideLineHeight(Paint.FontMetricsInt fm) {
    int realTextHeight = fm.bottom - fm.top;

    if (mHeight >= realTextHeight) {
      // Show proportionally additional ascent / top & descent / bottom
      final int additional = mHeight - (-fm.top + fm.bottom);

      // Round up for the negative values and down for the positive values  (arbitrary choice)
      // So that bottom - top equals additional even if it's an odd number.
      fm.top -= Math.ceil(additional / 2.0f);
      fm.bottom += Math.floor(additional / 2.0f);
      fm.ascent = fm.top;
      fm.descent = fm.bottom;
    }
  }
}
