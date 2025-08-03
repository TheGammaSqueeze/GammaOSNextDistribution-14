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

#ifndef MINIKIN_LINE_BREAKER_UTIL_H
#define MINIKIN_LINE_BREAKER_UTIL_H

#include <vector>

#include "HyphenatorMap.h"
#include "LayoutUtils.h"
#include "Locale.h"
#include "LocaleListCache.h"
#include "MinikinInternal.h"
#include "WordBreaker.h"
#include "minikin/Hyphenator.h"
#include "minikin/LineBreakStyle.h"
#include "minikin/MeasuredText.h"
#include "minikin/U16StringPiece.h"

namespace minikin {

constexpr uint32_t LBW_AUTO_HEURISTICS_LINE_COUNT = 5;

// ParaWidth is used to hold cumulative width from beginning of paragraph. Note that for very large
// paragraphs, accuracy could degrade using only 32-bit float. Note however that float is used
// extensively on the Java side for this. This is a typedef so that we can easily change it based
// on performance/accuracy tradeoff.
typedef float ParaWidth;

// Hyphenates a string potentially containing non-breaking spaces.
std::vector<HyphenationType> hyphenate(const U16StringPiece& string, const Hyphenator& hypenator);

// This function determines whether a character is a space that disappears at end of line.
// It is the Unicode set: [[:General_Category=Space_Separator:]-[:Line_Break=Glue:]], plus '\n'.
// Note: all such characters are in the BMP, so it's ok to use code units for this.
inline bool isLineEndSpace(uint16_t c) {
    return c == '\n' || c == ' '                           // SPACE
           || c == 0x1680                                  // OGHAM SPACE MARK
           || (0x2000 <= c && c <= 0x200A && c != 0x2007)  // EN QUAD, EM QUAD, EN SPACE, EM SPACE,
           // THREE-PER-EM SPACE, FOUR-PER-EM SPACE,
           // SIX-PER-EM SPACE, PUNCTUATION SPACE,
           // THIN SPACE, HAIR SPACE
           || c == 0x205F  // MEDIUM MATHEMATICAL SPACE
           || c == 0x3000;
}

inline Range trimTrailingLineEndSpaces(const U16StringPiece& textBuf, const Range& range) {
    for (uint32_t i = 0; i < range.getLength(); i++) {
        if (!isLineEndSpace(textBuf[range.getEnd() - i - 1])) {
            return Range(range.getStart(), range.getEnd() - i);
        }
    }
    return Range(range.getStart(), range.getStart());
}

inline Locale getEffectiveLocale(uint32_t localeListId) {
    const LocaleList& localeList = LocaleListCache::getById(localeListId);
    return localeList.empty() ? Locale() : localeList[0];
}

// Retrieves hyphenation break points from a word.
inline void populateHyphenationPoints(
        const U16StringPiece& textBuf,         // A text buffer.
        const Run& run,                        // A run of this region.
        const Hyphenator& hyphenator,          // A hyphenator to be used for hyphenation.
        const Range& contextRange,             // A context range for measuring hyphenated piece.
        const Range& hyphenationTargetRange,   // An actual range for the hyphenation target.
        const std::vector<float>& charWidths,  // Char width used for hyphen piece estimation.
        bool ignoreKerning,                    // True use full shaping for hyphenation piece.
        std::vector<HyphenBreak>* out,         // An output to be appended.
        LayoutPieces* pieces) {                // An output of layout pieces. Maybe null.
    if (!run.getRange().contains(contextRange) || !contextRange.contains(hyphenationTargetRange)) {
        return;
    }

    const std::vector<HyphenationType> hyphenResult =
            hyphenate(textBuf.substr(hyphenationTargetRange), hyphenator);
    for (uint32_t i = hyphenationTargetRange.getStart(); i < hyphenationTargetRange.getEnd(); ++i) {
        const HyphenationType hyph = hyphenResult[hyphenationTargetRange.toRangeOffset(i)];
        if (hyph == HyphenationType::DONT_BREAK) {
            continue;  // Not a hyphenation point.
        }

        if (!ignoreKerning) {
            auto hyphenPart = contextRange.split(i);
            U16StringPiece firstText = textBuf.substr(hyphenPart.first);
            U16StringPiece secondText = textBuf.substr(hyphenPart.second);
            const float first =
                    run.measureHyphenPiece(firstText, Range(0, firstText.size()),
                                           StartHyphenEdit::NO_EDIT /* start hyphen edit */,
                                           editForThisLine(hyph) /* end hyphen edit */, pieces);
            const float second =
                    run.measureHyphenPiece(secondText, Range(0, secondText.size()),
                                           editForNextLine(hyph) /* start hyphen edit */,
                                           EndHyphenEdit::NO_EDIT /* end hyphen edit */, pieces);

            out->emplace_back(i, hyph, first, second);
        } else {
            float first = 0;
            float second = 0;
            for (uint32_t j = contextRange.getStart(); j < i; ++j) {
                first += charWidths[j];
            }
            for (uint32_t j = i; j < contextRange.getEnd(); ++j) {
                second += charWidths[j];
            }

            EndHyphenEdit endEdit = editForThisLine(hyph);
            StartHyphenEdit startEdit = editForNextLine(hyph);

            if (endEdit != EndHyphenEdit::NO_EDIT) {
                auto [str, strSize] = getHyphenString(endEdit);
                first += run.measureText(U16StringPiece(str, strSize));
            }

            if (startEdit != StartHyphenEdit::NO_EDIT) {
                auto [str, strSize] = getHyphenString(startEdit);
                second += run.measureText(U16StringPiece(str, strSize));
            }

            out->emplace_back(i, hyph, first, second);
        }
    }
}

// Class for tracking the word breaker transition point.
class WordBreakerTransitionTracker {
public:
    // Update the word breaker transition information. This function return true if the word
    // breaker need to be updated.
    bool update(const Run& run) {
        const uint32_t newLocaleListId = run.getLocaleListId();
        const LineBreakStyle newLineBreakStyle = run.lineBreakStyle();
        const LineBreakWordStyle newLineBreakWordStyle = run.lineBreakWordStyle();
        const bool isUpdate = localeListId != newLocaleListId ||
                              lineBreakStyle != newLineBreakStyle ||
                              lineBreakWordStyle != newLineBreakWordStyle;

        localeListId = newLocaleListId;
        lineBreakStyle = newLineBreakStyle;
        lineBreakWordStyle = newLineBreakWordStyle;

        return isUpdate;
    }

    const LocaleList& getCurrentLocaleList() const {
        return LocaleListCache::getById(localeListId);
    }

    LineBreakStyle getCurrentLineBreakStyle() const { return lineBreakStyle; }

    LineBreakWordStyle getCurrentLineBreakWordStyle() const { return lineBreakWordStyle; }

private:
    uint32_t localeListId = LocaleListCache::kInvalidListId;
    LineBreakStyle lineBreakStyle = LineBreakStyle::None;
    LineBreakWordStyle lineBreakWordStyle = LineBreakWordStyle::None;
};

inline std::pair<LineBreakWordStyle, bool> resolveWordStyleAuto(LineBreakWordStyle lbWordStyle,
                                                                const LocaleList& localeList,
                                                                bool forceWordStyleAutoToPhrase) {
    if (lbWordStyle == LineBreakWordStyle::Auto) {
        if (forceWordStyleAutoToPhrase) {
            return std::make_pair(LineBreakWordStyle::Phrase, false);
        } else if (localeList.hasKorean()) {
            return std::make_pair(LineBreakWordStyle::Phrase, false);
        } else if (localeList.hasJapanese()) {
            return std::make_pair(LineBreakWordStyle::None, true);
        } else {
            return std::make_pair(LineBreakWordStyle::None, false);
        }
    } else {
        return std::make_pair(lbWordStyle, false);
    }
}

// Processes and retrieve informations from characters in the paragraph.
struct CharProcessor {
    // The number of spaces.
    uint32_t rawSpaceCount = 0;

    // The number of spaces minus trailing spaces.
    uint32_t effectiveSpaceCount = 0;

    // The sum of character width from the paragraph start.
    ParaWidth sumOfCharWidths = 0.0;

    // The sum of character width from the paragraph start minus trailing line end spaces.
    // This means that the line width from the paragraph start if we decided break now.
    ParaWidth effectiveWidth = 0.0;

    // The total amount of character widths at the previous word break point.
    ParaWidth sumOfCharWidthsAtPrevWordBreak = 0.0;

    // The next word break offset.
    uint32_t nextWordBreak = 0;

    // The previous word break offset.
    uint32_t prevWordBreak = 0;

    // The width of a space. May be 0 if there are no spaces.
    // Note: if there are multiple different widths for spaces (for example, because of mixing of
    // fonts), it's only guaranteed to pick one.
    float spaceWidth = 0.0f;

    // The current hyphenator.
    const Hyphenator* hyphenator = nullptr;

    bool retryWithPhraseWordBreak = false;

    // Retrieve the current word range.
    inline Range wordRange() const { return breaker.wordRange(); }

    // Retrieve the current context range.
    inline Range contextRange() const { return Range(prevWordBreak, nextWordBreak); }

    // Returns the width from the last word break point.
    inline ParaWidth widthFromLastWordBreak() const {
        return effectiveWidth - sumOfCharWidthsAtPrevWordBreak;
    }

    // Returns the break penalty for the current word break point.
    inline int wordBreakPenalty() const { return breaker.breakBadness(); }

    CharProcessor(const U16StringPiece& text) { breaker.setText(text.data(), text.size()); }

    // The user of CharProcessor must call updateLocaleIfNecessary with valid locale at least one
    // time before feeding characters.
    void updateLocaleIfNecessary(const Run& run, bool forceWordStyleAutoToPhrase) {
        if (wbTracker.update(run)) {
            const LocaleList& localeList = wbTracker.getCurrentLocaleList();
            const Locale locale = localeList.empty() ? Locale() : localeList[0];

            LineBreakWordStyle lbWordStyle = wbTracker.getCurrentLineBreakWordStyle();
            std::tie(lbWordStyle, retryWithPhraseWordBreak) =
                    resolveWordStyleAuto(lbWordStyle, localeList, forceWordStyleAutoToPhrase);
            nextWordBreak = breaker.followingWithLocale(locale, run.lineBreakStyle(), lbWordStyle,
                                                        run.getRange().getStart());
            hyphenator = HyphenatorMap::lookup(locale);
        }
    }

    // Process one character.
    void feedChar(uint32_t idx, uint16_t c, float w, bool canBreakHere) {
        if (idx == nextWordBreak) {
            if (canBreakHere) {
                prevWordBreak = nextWordBreak;
                sumOfCharWidthsAtPrevWordBreak = sumOfCharWidths;
            }
            nextWordBreak = breaker.next();
        }
        if (isWordSpace(c)) {
            rawSpaceCount += 1;
            spaceWidth = w;
        }
        sumOfCharWidths += w;
        if (isLineEndSpace(c)) {
            // If we break a line on a line-ending space, that space goes away. So postBreak
            // and postSpaceCount, which keep the width and number of spaces if we decide to
            // break at this point, don't need to get adjusted.
        } else {
            effectiveSpaceCount = rawSpaceCount;
            effectiveWidth = sumOfCharWidths;
        }
    }

private:
    WordBreakerTransitionTracker wbTracker;
    WordBreaker breaker;
};
}  // namespace minikin

#endif  // MINIKIN_LINE_BREAKER_UTIL_H
