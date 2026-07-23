/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Core Text (used for the two-pass stroke rendering of text with a stroke effect) performs its own
 * word wrapping and ignores the NSTextContainer's `maximumNumberOfLines` and truncating line break
 * mode. If we hand it the full attributed string, an overflowing last word gets wrapped to a line
 * that does not fit the (clamped) frame and disappears entirely - with no ellipsis. The non-stroke
 * path avoids this because it draws the glyphs NSLayoutManager already laid out and truncated. This
 * helper reconstructs that same visible, truncated text (visible characters + an ellipsis) so the
 * stroke passes render identically to the non-stroke path.
 *
 * NOTE: This helper is written for TAIL truncation (`NSLineBreakByTruncatingTail`), which is the
 * only mode used by stroke effects. `truncatedGlyphRangeInLineFragmentForGlyphAtIndex:` only
 * reports a range for truncating line break modes, so for non-truncating modes (e.g. clipping) the
 * full string is returned unchanged. Head/middle truncation would report a range but place the
 * ellipsis at the start/middle; the reconstruction here always appends it at the end, so those
 * modes would need mode-specific handling before they could be used with a stroke effect.
 */
NSAttributedString *RCTTruncatedAttributedStringForStroke(
    NSTextStorage *textStorage,
    NSLayoutManager *layoutManager,
    NSTextContainer *textContainer);

NS_ASSUME_NONNULL_END
