/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import <React/RCTTextStroke.h>

NSAttributedString *RCTTruncatedAttributedStringForStroke(
    NSTextStorage *textStorage,
    NSLayoutManager *layoutManager,
    NSTextContainer *textContainer)
{
  // No line limit means nothing is truncated and the frame was measured to fit every wrapped line,
  // so Core Text can safely lay out the full string.
  if (textContainer.maximumNumberOfLines == 0) {
    return textStorage;
  }

  [layoutManager ensureLayoutForTextContainer:textContainer];
  NSRange fullGlyphRange = [layoutManager glyphRangeForTextContainer:textContainer];
  if (fullGlyphRange.length == 0) {
    return textStorage;
  }

  NSMutableAttributedString *truncated = [NSMutableAttributedString new];
  [layoutManager
      enumerateLineFragmentsForGlyphRange:fullGlyphRange
                               usingBlock:^(
                                   CGRect rect,
                                   CGRect usedRect,
                                   NSTextContainer *_Nonnull _,
                                   NSRange lineGlyphRange,
                                   BOOL *_Nonnull stop) {
                                 NSRange lineCharRange =
                                     [layoutManager characterRangeForGlyphRange:lineGlyphRange
                                                               actualGlyphRange:NULL];
                                 NSRange truncatedGlyphRange = [layoutManager
                                     truncatedGlyphRangeInLineFragmentForGlyphAtIndex:lineGlyphRange.location];

                                 if (truncatedGlyphRange.location != NSNotFound) {
                                   // This line is truncated. Keep the characters before the
                                   // truncation point and append an ellipsis carrying the attributes
                                   // of the last visible character (matching what TextKit renders).
                                   NSRange truncatedCharRange =
                                       [layoutManager characterRangeForGlyphRange:truncatedGlyphRange
                                                                 actualGlyphRange:NULL];
                                   NSInteger visibleLength = truncatedCharRange.location - lineCharRange.location;
                                   if (visibleLength > 0) {
                                     NSRange visibleRange = NSMakeRange(lineCharRange.location, visibleLength);
                                     [truncated
                                         appendAttributedString:[textStorage attributedSubstringFromRange:visibleRange]];
                                     NSDictionary<NSAttributedStringKey, id> *ellipsisAttributes =
                                         [textStorage attributesAtIndex:NSMaxRange(visibleRange) - 1 effectiveRange:NULL];
                                     [truncated appendAttributedString:[[NSAttributedString alloc]
                                                                           initWithString:@"\u2026"
                                                                               attributes:ellipsisAttributes]];
                                   }
                                   *stop = YES;
                                   return;
                                 }

                                 // Fully visible line. Append it verbatim; if it was soft-wrapped
                                 // (does not already end in a newline) insert one so Core Text
                                 // reproduces the same line break.
                                 NSAttributedString *lineString =
                                     [textStorage attributedSubstringFromRange:lineCharRange];
                                 [truncated appendAttributedString:lineString];
                                 NSString *lineText = lineString.string;
                                 if (lineText.length > 0 && [lineText characterAtIndex:lineText.length - 1] != '\n' &&
                                     NSMaxRange(lineCharRange) < textStorage.length) {
                                   NSDictionary<NSAttributedStringKey, id> *newlineAttributes =
                                       [textStorage attributesAtIndex:NSMaxRange(lineCharRange) - 1 effectiveRange:NULL];
                                   [truncated appendAttributedString:[[NSAttributedString alloc]
                                                                         initWithString:@"\n"
                                                                             attributes:newlineAttributes]];
                                 }
                               }];

  return truncated.length > 0 ? truncated : textStorage;
}
