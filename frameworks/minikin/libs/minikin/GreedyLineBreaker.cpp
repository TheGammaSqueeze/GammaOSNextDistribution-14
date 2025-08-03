/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "GreedyLineBreak"

#include "FeatureFlags.h"
#include "HyphenatorMap.h"
#include "LineBreakerUtil.h"
#include "Locale.h"
#include "LocaleListCache.h"
#include "WordBreaker.h"
#include "minikin/Characters.h"
#include "minikin/LineBreaker.h"
#include "minikin/MeasuredText.h"
#include "minikin/Range.h"
#include "minikin/U16StringPiece.h"

namespace minikin {

namespace {

constexpr uint32_t NOWHERE = 0xFFFFFFFF;

class GreedyLineBreaker {
public:
    // User of this class must keep measured, lineWidthLimit, tabStop alive until the instance is
    // destructed.
    GreedyLineBreaker(const U16StringPiece& textBuf, const MeasuredText& measured,
                      const LineWidth& lineWidthLimits, const TabStops& tabStops,
                      bool enableHyphenation, bool useBoundsForWidth)
            : mLineWidthLimit(lineWidthLimits.getAt(0)),
              mTextBuf(textBuf),
              mMeasuredText(measured),
              mLineWidthLimits(lineWidthLimits),
              mTabStops(tabStops),
              mEnableHyphenation(enableHyphenation),
              mUseBoundsForWidth(useBoundsForWidth) {}

    void process(bool forceWordStyleAutoToPhrase);

    LineBreakResult getResult() const;

    bool retryWithPhraseWordBreak = false;

private:
    struct BreakPoint {
        BreakPoint(uint32_t offset, float lineWidth, StartHyphenEdit startHyphen,
                   EndHyphenEdit endHyphen)
                : offset(offset),
                  lineWidth(lineWidth),
                  hyphenEdit(packHyphenEdit(startHyphen, endHyphen)) {}

        uint32_t offset;
        float lineWidth;
        HyphenEdit hyphenEdit;
    };

    inline uint32_t getPrevLineBreakOffset() {
        return mBreakPoints.empty() ? 0 : mBreakPoints.back().offset;
    }

    // Registers the break point and prepares for next line computation.
    void breakLineAt(uint32_t offset, float lineWidth, float remainingNextLineWidth,
                     float remainingNextSumOfCharWidths, EndHyphenEdit thisLineEndHyphen,
                     StartHyphenEdit nextLineStartHyphen);

    // Update current line width.
    void updateLineWidth(uint16_t c, float width);

    // Break line if current line exceeds the line limit.
    void processLineBreak(uint32_t offset, WordBreaker* breaker, bool doHyphenation);

    // Try to break with previous word boundary.
    // Returns false if unable to break by word boundary.
    bool tryLineBreakWithWordBreak();

    // Try to break with hyphenation.
    // Returns false if unable to hyphenate.
    //
    // This method keeps hyphenation until the line width after line break meets the line width
    // limit.
    bool tryLineBreakWithHyphenation(const Range& range, WordBreaker* breaker);

    // Do line break with each characters.
    //
    // This method only breaks at the first offset which has the longest width for the line width
    // limit. This method don't keep line breaking even if the rest of the word exceeds the line
    // width limit.
    // This method return true if there is no characters to be processed.
    bool doLineBreakWithGraphemeBounds(const Range& range);

    bool overhangExceedLineLimit(const Range& range);
    bool doLineBreakWithFallback(const Range& range);

    // Returns true if the current break point exceeds the width constraint.
    bool isWidthExceeded() const;

    // Info about the line currently processing.
    uint32_t mLineNum = 0;
    double mLineWidth = 0;
    double mSumOfCharWidths = 0;
    double mLineWidthLimit;
    StartHyphenEdit mStartHyphenEdit = StartHyphenEdit::NO_EDIT;

    // Previous word break point info.
    uint32_t mPrevWordBoundsOffset = NOWHERE;
    double mLineWidthAtPrevWordBoundary = 0;
    double mSumOfCharWidthsAtPrevWordBoundary = 0;
    bool mIsPrevWordBreakIsInEmailOrUrl = false;
    float mLineStartLetterSpacing = 0;  // initialized in the first loop of the run.
    float mCurrentLetterSpacing = 0;

    // The hyphenator currently used.
    const Hyphenator* mHyphenator = nullptr;

    // Input parameters.
    const U16StringPiece& mTextBuf;
    const MeasuredText& mMeasuredText;
    const LineWidth& mLineWidthLimits;
    const TabStops& mTabStops;
    bool mEnableHyphenation;
    bool mUseBoundsForWidth;

    // The result of line breaking.
    std::vector<BreakPoint> mBreakPoints;

    MINIKIN_PREVENT_COPY_ASSIGN_AND_MOVE(GreedyLineBreaker);
};

void GreedyLineBreaker::breakLineAt(uint32_t offset, float lineWidth, float remainingNextLineWidth,
                                    float remainingNextSumOfCharWidths,
                                    EndHyphenEdit thisLineEndHyphen,
                                    StartHyphenEdit nextLineStartHyphen) {
    float edgeLetterSpacing = (mLineStartLetterSpacing + mCurrentLetterSpacing) / 2.0f;
    // First, push the break to result.
    mBreakPoints.emplace_back(offset, lineWidth - edgeLetterSpacing, mStartHyphenEdit,
                              thisLineEndHyphen);

    // Update the current line info.
    mLineWidthLimit = mLineWidthLimits.getAt(++mLineNum);
    mLineWidth = remainingNextLineWidth;
    mSumOfCharWidths = remainingNextSumOfCharWidths;
    mStartHyphenEdit = nextLineStartHyphen;
    mPrevWordBoundsOffset = NOWHERE;
    mLineWidthAtPrevWordBoundary = 0;
    mSumOfCharWidthsAtPrevWordBoundary = 0;
    mIsPrevWordBreakIsInEmailOrUrl = false;
    mLineStartLetterSpacing = mCurrentLetterSpacing;
}

bool GreedyLineBreaker::tryLineBreakWithWordBreak() {
    if (mPrevWordBoundsOffset == NOWHERE) {
        return false;  // No word break point before..
    }

    breakLineAt(mPrevWordBoundsOffset,                            // break offset
                mLineWidthAtPrevWordBoundary,                     // line width
                mLineWidth - mSumOfCharWidthsAtPrevWordBoundary,  // remaining next line width
                // remaining next sum of char widths.
                mSumOfCharWidths - mSumOfCharWidthsAtPrevWordBoundary, EndHyphenEdit::NO_EDIT,
                StartHyphenEdit::NO_EDIT);  // No hyphen modification.
    return true;
}

bool GreedyLineBreaker::tryLineBreakWithHyphenation(const Range& range, WordBreaker* breaker) {
    if (!mEnableHyphenation || mHyphenator == nullptr) {
        return false;
    }

    Run* targetRun = nullptr;
    for (const auto& run : mMeasuredText.runs) {
        if (run->getRange().contains(range)) {
            targetRun = run.get();
        }
    }

    if (targetRun == nullptr) {
        return false;  // The target range may lay on multiple run. Unable to hyphenate.
    }

    const Range targetRange = breaker->wordRange();
    if (!range.contains(targetRange)) {
        return false;
    }

    if (!targetRun->canHyphenate()) {
        return false;
    }

    const std::vector<HyphenationType> hyphenResult =
            hyphenate(mTextBuf.substr(targetRange), *mHyphenator);
    Range contextRange = range;
    uint32_t prevOffset = NOWHERE;
    float prevWidth = 0;

    // Look up the hyphenation point from the begining.
    for (uint32_t i = targetRange.getStart(); i < targetRange.getEnd(); ++i) {
        const HyphenationType hyph = hyphenResult[targetRange.toRangeOffset(i)];
        if (hyph == HyphenationType::DONT_BREAK) {
            continue;  // Not a hyphenation point.
        }

        const float width =
                targetRun->measureHyphenPiece(mTextBuf, contextRange.split(i).first,
                                              mStartHyphenEdit, editForThisLine(hyph), nullptr);

        if (width <= mLineWidthLimit) {
            // There are still space, remember current offset and look up next hyphenation point.
            prevOffset = i;
            prevWidth = width;
            continue;
        }

        if (prevOffset == NOWHERE) {
            // Even with hyphenation, the piece is too long for line. Give up and break in
            // character bounds.
            doLineBreakWithGraphemeBounds(contextRange);
        } else {
            // Previous offset is the longest hyphenation piece. Break with it.
            const HyphenationType hyph = hyphenResult[targetRange.toRangeOffset(prevOffset)];
            const StartHyphenEdit nextLineStartHyphenEdit = editForNextLine(hyph);
            const float remainingCharWidths = targetRun->measureHyphenPiece(
                    mTextBuf, contextRange.split(prevOffset).second, nextLineStartHyphenEdit,
                    EndHyphenEdit::NO_EDIT, nullptr);
            breakLineAt(prevOffset, prevWidth,
                        remainingCharWidths - (mSumOfCharWidths - mLineWidth), remainingCharWidths,
                        editForThisLine(hyph), nextLineStartHyphenEdit);
        }

        if (mLineWidth <= mLineWidthLimit) {
            // The remaining hyphenation piece is less than line width. No more hyphenation is
            // needed. Go to next word.
            return true;
        }

        // Even after line break, the remaining hyphenation piece is still too long for the limit.
        // Keep hyphenating for the rest.
        i = getPrevLineBreakOffset();
        contextRange.setStart(i);  // Update the hyphenation start point.
        prevOffset = NOWHERE;
    }

    // Do the same line break at the end of text.
    // TODO: Remove code duplication. This is the same as in the for loop but extracting function
    //       may not clear.
    if (prevOffset == NOWHERE) {
        doLineBreakWithGraphemeBounds(contextRange);
    } else {
        const HyphenationType hyph = hyphenResult[targetRange.toRangeOffset(prevOffset)];
        const StartHyphenEdit nextLineStartHyphenEdit = editForNextLine(hyph);
        const float remainingCharWidths = targetRun->measureHyphenPiece(
                mTextBuf, contextRange.split(prevOffset).second, nextLineStartHyphenEdit,
                EndHyphenEdit::NO_EDIT, nullptr);

        breakLineAt(prevOffset, prevWidth, remainingCharWidths - (mSumOfCharWidths - mLineWidth),
                    remainingCharWidths, editForThisLine(hyph), nextLineStartHyphenEdit);
    }

    return true;
}

// TODO: Respect trailing line end spaces.
bool GreedyLineBreaker::doLineBreakWithGraphemeBounds(const Range& range) {
    float width = mMeasuredText.widths[range.getStart()];

    const float estimatedLetterSpacing = (mLineStartLetterSpacing + mCurrentLetterSpacing) * 0.5;
    // Starting from + 1 since at least one character needs to be assigned to a line.
    for (uint32_t i = range.getStart() + 1; i < range.getEnd(); ++i) {
        const float w = mMeasuredText.widths[i];
        if (w == 0) {
            continue;  // w == 0 means here is not a grapheme bounds. Don't break here.
        }
        if (width + w - estimatedLetterSpacing > mLineWidthLimit ||
            overhangExceedLineLimit(Range(range.getStart(), i + 1))) {
            // Okay, here is the longest position.
            breakLineAt(i, width, mLineWidth - width, mSumOfCharWidths - width,
                        EndHyphenEdit::NO_EDIT, StartHyphenEdit::NO_EDIT);

            // This method only breaks at the first longest offset, since we may want to hyphenate
            // the rest of the word.
            return false;
        } else {
            width += w;
        }
    }

    // Reaching here means even one character (or cluster) doesn't fit the line.
    // Give up and break at the end of this range.
    breakLineAt(range.getEnd(), mLineWidth, 0, 0, EndHyphenEdit::NO_EDIT, StartHyphenEdit::NO_EDIT);
    return true;
}

bool GreedyLineBreaker::doLineBreakWithFallback(const Range& range) {
    if (!features::phrase_strict_fallback()) {
        return false;
    }
    Run* targetRun = nullptr;
    for (const auto& run : mMeasuredText.runs) {
        if (run->getRange().contains(range)) {
            targetRun = run.get();
        }
    }

    if (targetRun == nullptr) {
        return false;  // The target range may lay on multiple run. Unable to fallback.
    }

    if (targetRun->lineBreakWordStyle() == LineBreakWordStyle::None) {
        return false;  // If the line break word style is already none, nothing can be falled back.
    }

    WordBreaker wb;
    wb.setText(mTextBuf.data(), mTextBuf.length());
    ssize_t next = wb.followingWithLocale(getEffectiveLocale(targetRun->getLocaleListId()),
                                          targetRun->lineBreakStyle(), LineBreakWordStyle::None,
                                          range.getStart());

    if (!range.contains(next)) {
        return false;  // No fallback break points.
    }

    int32_t prevBreak = -1;
    float wordWidth = 0;
    float preBreakWidth = 0;
    for (uint32_t i = range.getStart(); i < range.getEnd(); ++i) {
        const float w = mMeasuredText.widths[i];
        if (w == 0) {
            continue;  // w == 0 means here is not a grapheme bounds. Don't break here.
        }
        if (i == (uint32_t)next) {
            if (preBreakWidth + wordWidth > mLineWidthLimit) {
                if (prevBreak == -1) {
                    return false;  // No candidate before this point. Give up.
                }
                breakLineAt(prevBreak, preBreakWidth, mLineWidth - preBreakWidth,
                            mSumOfCharWidths - preBreakWidth, EndHyphenEdit::NO_EDIT,
                            StartHyphenEdit::NO_EDIT);
                return true;
            }
            prevBreak = i;
            next = wb.next();
            preBreakWidth += wordWidth;
            wordWidth = w;
        } else {
            wordWidth += w;
        }
    }

    if (preBreakWidth <= mLineWidthLimit) {
        breakLineAt(prevBreak, preBreakWidth, mLineWidth - preBreakWidth,
                    mSumOfCharWidths - preBreakWidth, EndHyphenEdit::NO_EDIT,
                    StartHyphenEdit::NO_EDIT);
        return true;
    }

    return false;
}

void GreedyLineBreaker::updateLineWidth(uint16_t c, float width) {
    if (c == CHAR_TAB) {
        mSumOfCharWidths = mTabStops.nextTab(mSumOfCharWidths);
        mLineWidth = mSumOfCharWidths;
    } else {
        mSumOfCharWidths += width;
        if (!isLineEndSpace(c)) {
            mLineWidth = mSumOfCharWidths;
        }
    }
}

bool GreedyLineBreaker::overhangExceedLineLimit(const Range& range) {
    if (!mUseBoundsForWidth) {
        return false;
    }
    if (!mMeasuredText.hasOverhang(range)) {
        return false;
    }

    uint32_t i;
    for (i = 0; i < range.getLength(); ++i) {
        uint16_t ch = mTextBuf[range.getEnd() - i - 1];
        if (!isLineEndSpace(ch)) {
            break;
        }
    }
    if (i == range.getLength()) {
        return false;
    }

    return mMeasuredText.getBounds(mTextBuf, Range(range.getStart(), range.getEnd() - i)).width() >
           mLineWidthLimit;
}

bool GreedyLineBreaker::isWidthExceeded() const {
    // The text layout adds letter spacing to the all characters, but the spaces at left and
    // right edge are removed. Here, we use the accumulated character widths as a text widths, but
    // it includes the letter spacing at the left and right edge. Thus, we need to remove a letter
    // spacing amount for one character. However, it is hard to get letter spacing of the left and
    // right edge and it makes greey line breaker O(n^2): n for line break and  n for perforimng
    // BiDi run resolution for getting left and right edge for every break opportunity. To avoid
    // this performance regression, use the letter spacing of the previous break point and letter
    // spacing of current break opportunity instead.
    const float estimatedLetterSpacing = (mLineStartLetterSpacing + mCurrentLetterSpacing) * 0.5;
    return (mLineWidth - estimatedLetterSpacing) > mLineWidthLimit;
}

void GreedyLineBreaker::processLineBreak(uint32_t offset, WordBreaker* breaker,
                                         bool doHyphenation) {
    while (isWidthExceeded() || overhangExceedLineLimit(Range(getPrevLineBreakOffset(), offset))) {
        if (tryLineBreakWithWordBreak()) {
            continue;  // The word in the new line may still be too long for the line limit.
        }

        if (doHyphenation &&
            tryLineBreakWithHyphenation(Range(getPrevLineBreakOffset(), offset), breaker)) {
            continue;  // TODO: we may be able to return here.
        }

        if (doLineBreakWithFallback(Range(getPrevLineBreakOffset(), offset))) {
            continue;
        }

        if (doLineBreakWithGraphemeBounds(Range(getPrevLineBreakOffset(), offset))) {
            return;
        }
    }

    // There is still spaces, remember current word break point as a candidate and wait next word.
    const bool isInEmailOrUrl = breaker->breakBadness() != 0;
    if (mPrevWordBoundsOffset == NOWHERE || mIsPrevWordBreakIsInEmailOrUrl | !isInEmailOrUrl) {
        mPrevWordBoundsOffset = offset;
        mLineWidthAtPrevWordBoundary = mLineWidth;
        mSumOfCharWidthsAtPrevWordBoundary = mSumOfCharWidths;
        mIsPrevWordBreakIsInEmailOrUrl = isInEmailOrUrl;
    }
}

void GreedyLineBreaker::process(bool forceWordStyleAutoToPhrase) {
    WordBreaker wordBreaker;
    wordBreaker.setText(mTextBuf.data(), mTextBuf.size());

    WordBreakerTransitionTracker wbTracker;
    uint32_t nextWordBoundaryOffset = 0;
    for (uint32_t runIndex = 0; runIndex < mMeasuredText.runs.size(); ++runIndex) {
        const std::unique_ptr<Run>& run = mMeasuredText.runs[runIndex];
        if (features::letter_spacing_justification()) {
            mCurrentLetterSpacing = run->getLetterSpacingInPx();
            if (runIndex == 0) {
                mLineStartLetterSpacing = mCurrentLetterSpacing;
            }
        } else {
            mCurrentLetterSpacing = 0;
            mLineStartLetterSpacing = 0;
        }
        const Range range = run->getRange();

        // Update locale if necessary.
        if (wbTracker.update(*run)) {
            const LocaleList& localeList = wbTracker.getCurrentLocaleList();
            const Locale locale = localeList.empty() ? Locale() : localeList[0];

            LineBreakWordStyle lbWordStyle = wbTracker.getCurrentLineBreakWordStyle();
            std::tie(lbWordStyle, retryWithPhraseWordBreak) =
                    resolveWordStyleAuto(lbWordStyle, localeList, forceWordStyleAutoToPhrase);

            nextWordBoundaryOffset = wordBreaker.followingWithLocale(locale, run->lineBreakStyle(),
                                                                     lbWordStyle, range.getStart());
            mHyphenator = HyphenatorMap::lookup(locale);
        }

        for (uint32_t i = range.getStart(); i < range.getEnd(); ++i) {
            updateLineWidth(mTextBuf[i], mMeasuredText.widths[i]);

            if ((i + 1) == nextWordBoundaryOffset) {
                // Only process line break at word boundary and the run can break into some pieces.
                if (run->canBreak() || nextWordBoundaryOffset == range.getEnd()) {
                    processLineBreak(i + 1, &wordBreaker, run->canBreak());
                }
                nextWordBoundaryOffset = wordBreaker.next();
            }
        }
    }

    if (getPrevLineBreakOffset() != mTextBuf.size() && mPrevWordBoundsOffset != NOWHERE) {
        // The remaining words in the last line.
        breakLineAt(mPrevWordBoundsOffset, mLineWidth, 0, 0, EndHyphenEdit::NO_EDIT,
                    StartHyphenEdit::NO_EDIT);
    }
}

LineBreakResult GreedyLineBreaker::getResult() const {
    constexpr int TAB_BIT = 1 << 29;  // Must be the same in StaticLayout.java

    LineBreakResult out;
    uint32_t prevBreakOffset = 0;
    for (const auto& breakPoint : mBreakPoints) {
        // TODO: compute these during line breaking if these takes longer time.
        bool hasTabChar = false;
        for (uint32_t i = prevBreakOffset; i < breakPoint.offset; ++i) {
            hasTabChar |= mTextBuf[i] == CHAR_TAB;
        }

        if (mUseBoundsForWidth) {
            Range range = Range(prevBreakOffset, breakPoint.offset);
            Range actualRange = trimTrailingLineEndSpaces(mTextBuf, range);
            if (actualRange.isEmpty()) {
                // No characters before the line-end-spaces.
                MinikinExtent extent = mMeasuredText.getExtent(mTextBuf, range);
                out.ascents.push_back(extent.ascent);
                out.descents.push_back(extent.descent);
                out.bounds.emplace_back(0, extent.ascent, breakPoint.lineWidth, extent.descent);
            } else {
                LineMetrics metrics = mMeasuredText.getLineMetrics(mTextBuf, actualRange);
                out.ascents.push_back(metrics.extent.ascent);
                out.descents.push_back(metrics.extent.descent);
                out.bounds.emplace_back(metrics.bounds);
            }
        } else {
            MinikinExtent extent =
                    mMeasuredText.getExtent(mTextBuf, Range(prevBreakOffset, breakPoint.offset));
            out.ascents.push_back(extent.ascent);
            out.descents.push_back(extent.descent);
            // We don't have bounding box if mUseBoundsForWidth is false. Use line ascent/descent
            // and linew width for the bounding box.
            out.bounds.emplace_back(0, extent.ascent, breakPoint.lineWidth, extent.descent);
        }
        out.breakPoints.push_back(breakPoint.offset);
        out.widths.push_back(breakPoint.lineWidth);
        out.flags.push_back((hasTabChar ? TAB_BIT : 0) | static_cast<int>(breakPoint.hyphenEdit));

        prevBreakOffset = breakPoint.offset;
    }
    return out;
}

}  // namespace

LineBreakResult breakLineGreedy(const U16StringPiece& textBuf, const MeasuredText& measured,
                                const LineWidth& lineWidthLimits, const TabStops& tabStops,
                                bool enableHyphenation, bool useBoundsForWidth) {
    if (textBuf.size() == 0) {
        return LineBreakResult();
    }
    GreedyLineBreaker lineBreaker(textBuf, measured, lineWidthLimits, tabStops, enableHyphenation,
                                  useBoundsForWidth);
    lineBreaker.process(false);
    LineBreakResult res = lineBreaker.getResult();

    if (!features::word_style_auto()) {
        return res;
    }

    // The line breaker says that retry with phrase based word break because of the auto option and
    // given locales.
    if (!lineBreaker.retryWithPhraseWordBreak) {
        return res;
    }

    // If the line break result is more than heuristics threshold, don't try pharse based word
    // break.
    if (res.breakPoints.size() >= LBW_AUTO_HEURISTICS_LINE_COUNT) {
        return res;
    }

    GreedyLineBreaker phLineBreaker(textBuf, measured, lineWidthLimits, tabStops, enableHyphenation,
                                    useBoundsForWidth);
    phLineBreaker.process(true);
    LineBreakResult res2 = phLineBreaker.getResult();

    if (res2.breakPoints.size() < LBW_AUTO_HEURISTICS_LINE_COUNT) {
        return res2;
    } else {
        return res;
    }
}

}  // namespace minikin
