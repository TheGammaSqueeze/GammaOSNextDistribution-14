/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "OptimalLineBreaker.h"

#include <algorithm>
#include <limits>

#include "FeatureFlags.h"
#include "HyphenatorMap.h"
#include "LayoutUtils.h"
#include "LineBreakerUtil.h"
#include "Locale.h"
#include "LocaleListCache.h"
#include "MinikinInternal.h"
#include "WordBreaker.h"
#include "minikin/Characters.h"
#include "minikin/Layout.h"
#include "minikin/Range.h"
#include "minikin/U16StringPiece.h"

namespace minikin {

namespace {

// Large scores in a hierarchy; we prefer desperate breaks to an overfull line. All these
// constants are larger than any reasonable actual width score.
constexpr float SCORE_INFTY = std::numeric_limits<float>::max();
constexpr float SCORE_OVERFULL = 1e12f;
constexpr float SCORE_DESPERATE = 1e10f;
constexpr float SCORE_FALLBACK = 1e6f;

// Multiplier for hyphen penalty on last line.
constexpr float LAST_LINE_PENALTY_MULTIPLIER = 4.0f;
// Penalty assigned to each line break (to try to minimize number of lines)
// TODO: when we implement full justification (so spaces can shrink and stretch), this is
// probably not the most appropriate method.
constexpr float LINE_PENALTY_MULTIPLIER = 2.0f;

// Penalty assigned to shrinking the whitepsace.
constexpr float SHRINK_PENALTY_MULTIPLIER = 4.0f;

// Maximum amount that spaces can shrink, in justified text.
constexpr float SHRINKABILITY = 1.0 / 3.0;

// A single candidate break
struct Candidate {
    uint32_t offset;  // offset to text buffer, in code units

    ParaWidth preBreak;       // width of text until this point, if we decide to not break here:
                              // preBreak is used as an optimized way to calculate the width
                              // between two candidates. The line width between two line break
                              // candidates i and j is calculated as postBreak(j) - preBreak(i).
    ParaWidth postBreak;      // width of text until this point, if we decide to break here
    float penalty;            // penalty of this break (for example, hyphen penalty)
    uint32_t preSpaceCount;   // preceding space count before breaking
    uint32_t postSpaceCount;  // preceding space count after breaking
    HyphenationType hyphenType;
    bool isRtl;  // The direction of the bidi run containing or ending in this candidate

    Candidate(uint32_t offset, ParaWidth preBreak, ParaWidth postBreak, float penalty,
              uint32_t preSpaceCount, uint32_t postSpaceCount, HyphenationType hyphenType,
              bool isRtl)
            : offset(offset),
              preBreak(preBreak),
              postBreak(postBreak),
              penalty(penalty),
              preSpaceCount(preSpaceCount),
              postSpaceCount(postSpaceCount),
              hyphenType(hyphenType),
              isRtl(isRtl) {}
};

// A context of line break optimization.
struct OptimizeContext {
    // The break candidates.
    std::vector<Candidate> candidates;

    // The penalty for the number of lines.
    float linePenalty = 0.0f;

    // The width of a space. May be 0 if there are no spaces.
    // Note: if there are multiple different widths for spaces (for example, because of mixing of
    // fonts), it's only guaranteed to pick one.
    float spaceWidth = 0.0f;

    bool retryWithPhraseWordBreak = false;

    // Append desperate break point to the candidates.
    inline void pushDesperate(uint32_t offset, ParaWidth sumOfCharWidths, float score,
                              uint32_t spaceCount, bool isRtl, float letterSpacing) {
        pushBreakCandidate(offset, sumOfCharWidths, sumOfCharWidths, score, spaceCount, spaceCount,
                           HyphenationType::BREAK_AND_DONT_INSERT_HYPHEN, isRtl, letterSpacing);
    }

    // Append hyphenation break point to the candidates.
    inline void pushHyphenation(uint32_t offset, ParaWidth preBreak, ParaWidth postBreak,
                                float penalty, uint32_t spaceCount, HyphenationType type,
                                bool isRtl, float letterSpacing) {
        pushBreakCandidate(offset, preBreak, postBreak, penalty, spaceCount, spaceCount, type,
                           isRtl, letterSpacing);
    }

    // Append word break point to the candidates.
    inline void pushWordBreak(uint32_t offset, ParaWidth preBreak, ParaWidth postBreak,
                              float penalty, uint32_t preSpaceCount, uint32_t postSpaceCount,
                              bool isRtl, float letterSpacing) {
        pushBreakCandidate(offset, preBreak, postBreak, penalty, preSpaceCount, postSpaceCount,
                           HyphenationType::DONT_BREAK, isRtl, letterSpacing);
    }

    OptimizeContext(float firstLetterSpacing) {
        pushWordBreak(0, 0, 0, 0, 0, 0, false, firstLetterSpacing);
    }

private:
    void pushBreakCandidate(uint32_t offset, ParaWidth preBreak, ParaWidth postBreak, float penalty,
                            uint32_t preSpaceCount, uint32_t postSpaceCount, HyphenationType type,
                            bool isRtl, float letterSpacing) {
        // Adjust the letter spacing amount. To remove the letter spacing of left and right edge,
        // adjust the preBreak and postBreak values. Adding half to preBreak and removing half from
        // postBreak, the letter space amount is subtracted from the line.
        //
        // This calculation assumes the letter spacing of starting edge is the same to the line
        // start offset and letter spacing of ending edge is the same to the line end offset.
        // Ideally, we should do the BiDi reordering for identifying the run of the left edge and
        // right edge but it makes the candidate population to O(n^2). To avoid performance
        // regression, use the letter spacing of the line start offset and letter spacing of the
        // line end offset.
        const float letterSpacingHalf = letterSpacing * 0.5;
        candidates.emplace_back(offset, preBreak + letterSpacingHalf, postBreak - letterSpacingHalf,
                                penalty, preSpaceCount, postSpaceCount, type, isRtl);
    }
};

// Compute the penalty for the run and returns penalty for hyphenation and number of lines.
std::pair<float, float> computePenalties(const Run& run, const LineWidth& lineWidth,
                                         HyphenationFrequency frequency, bool justified) {
    float linePenalty = 0.0;
    const MinikinPaint* paint = run.getPaint();
    // a heuristic that seems to perform well
    float hyphenPenalty = 0.5 * paint->size * paint->scaleX * lineWidth.getAt(0);
    if (frequency == HyphenationFrequency::Normal) {
        hyphenPenalty *= 4.0;  // TODO: Replace with a better value after some testing
    }

    if (justified) {
        // Make hyphenation more aggressive for fully justified text (so that "normal" in
        // justified mode is the same as "full" in ragged-right).
        hyphenPenalty *= 0.25;
    } else {
        // Line penalty is zero for justified text.
        linePenalty = hyphenPenalty * LINE_PENALTY_MULTIPLIER;
    }

    return std::make_pair(hyphenPenalty, linePenalty);
}

// Represents a desperate break point.
struct DesperateBreak {
    // The break offset.
    uint32_t offset;

    // The sum of the character width from the beginning of the word.
    ParaWidth sumOfChars;

    float score;

    DesperateBreak(uint32_t offset, ParaWidth sumOfChars, float score)
            : offset(offset), sumOfChars(sumOfChars), score(score){};
};

// Retrieves desperate break points from a word.
std::vector<DesperateBreak> populateDesperatePoints(const U16StringPiece& textBuf,
                                                    const MeasuredText& measured,
                                                    const Range& range, const Run& run) {
    std::vector<DesperateBreak> out;

    if (!features::phrase_strict_fallback() ||
        run.lineBreakWordStyle() == LineBreakWordStyle::None) {
        ParaWidth width = measured.widths[range.getStart()];
        for (uint32_t i = range.getStart() + 1; i < range.getEnd(); ++i) {
            const float w = measured.widths[i];
            if (w == 0) {
                continue;  // w == 0 means here is not a grapheme bounds. Don't break here.
            }
            out.emplace_back(i, width, SCORE_DESPERATE);
            width += w;
        }
    } else {
        WordBreaker wb;
        wb.setText(textBuf.data(), textBuf.length());
        ssize_t next = wb.followingWithLocale(getEffectiveLocale(run.getLocaleListId()),
                                              run.lineBreakStyle(), LineBreakWordStyle::None,
                                              range.getStart());

        const bool calculateFallback = range.contains(next);
        ParaWidth width = measured.widths[range.getStart()];
        for (uint32_t i = range.getStart() + 1; i < range.getEnd(); ++i) {
            const float w = measured.widths[i];
            if (w == 0) {
                continue;  // w == 0 means here is not a grapheme bounds. Don't break here.
            }
            if (calculateFallback && i == (uint32_t)next) {
                out.emplace_back(i, width, SCORE_FALLBACK);
                next = wb.next();
                if (!range.contains(next)) {
                    break;
                }
            } else {
                out.emplace_back(i, width, SCORE_DESPERATE);
            }
            width += w;
        }
    }

    return out;
}

// Append hyphenation break points and desperate break points.
// If an offset is a both candidate for hyphenation and desperate break points, place desperate
// break candidate first and hyphenation break points second since the result width of the desperate
// break is shorter than hyphenation break.
// This is important since DP in computeBreaksOptimal assumes that the result line width is
// increased by break offset.
void appendWithMerging(std::vector<HyphenBreak>::const_iterator hyIter,
                       std::vector<HyphenBreak>::const_iterator endHyIter,
                       const std::vector<DesperateBreak>& desperates, const CharProcessor& proc,
                       float hyphenPenalty, bool isRtl, float letterSpacing, OptimizeContext* out) {
    auto d = desperates.begin();
    while (hyIter != endHyIter || d != desperates.end()) {
        // If both hyphen breaks and desperate breaks point to the same offset, push desperate
        // breaks first.
        if (d != desperates.end() && (hyIter == endHyIter || d->offset <= hyIter->offset)) {
            out->pushDesperate(d->offset, proc.sumOfCharWidthsAtPrevWordBreak + d->sumOfChars,
                               d->score, proc.effectiveSpaceCount, isRtl, letterSpacing);
            d++;
        } else {
            out->pushHyphenation(hyIter->offset, proc.sumOfCharWidths - hyIter->second,
                                 proc.sumOfCharWidthsAtPrevWordBreak + hyIter->first, hyphenPenalty,
                                 proc.effectiveSpaceCount, hyIter->type, isRtl, letterSpacing);
            hyIter++;
        }
    }
}

// Enumerate all line break candidates.
OptimizeContext populateCandidates(const U16StringPiece& textBuf, const MeasuredText& measured,
                                   const LineWidth& lineWidth, HyphenationFrequency frequency,
                                   bool isJustified, bool forceWordStyleAutoToPhrase) {
    const ParaWidth minLineWidth = lineWidth.getMin();
    CharProcessor proc(textBuf);

    float initialLetterSpacing;
    if (features::letter_spacing_justification()) {
        if (measured.runs.empty()) {
            initialLetterSpacing = 0;
        } else {
            initialLetterSpacing = measured.runs[0]->getLetterSpacingInPx();
        }
    } else {
        initialLetterSpacing = 0;
    }
    OptimizeContext result(initialLetterSpacing);

    const bool doHyphenation = frequency != HyphenationFrequency::None;
    auto hyIter = std::begin(measured.hyphenBreaks);

    for (const auto& run : measured.runs) {
        const bool isRtl = run->isRtl();
        const Range& range = run->getRange();
        const float letterSpacing =
                features::letter_spacing_justification() ? run->getLetterSpacingInPx() : 0;

        // Compute penalty parameters.
        float hyphenPenalty = 0.0f;
        if (run->canBreak()) {
            auto penalties = computePenalties(*run, lineWidth, frequency, isJustified);
            hyphenPenalty = penalties.first;
            result.linePenalty = std::max(penalties.second, result.linePenalty);
        }

        proc.updateLocaleIfNecessary(*run, forceWordStyleAutoToPhrase);

        for (uint32_t i = range.getStart(); i < range.getEnd(); ++i) {
            MINIKIN_ASSERT(textBuf[i] != CHAR_TAB, "TAB is not supported in optimal line breaker");
            // Even if the run is not a candidate of line break, treat the end of run as the line
            // break candidate.
            const bool canBreak = run->canBreak() || (i + 1) == range.getEnd();
            proc.feedChar(i, textBuf[i], measured.widths[i], canBreak);

            const uint32_t nextCharOffset = i + 1;
            if (nextCharOffset != proc.nextWordBreak) {
                continue;  // Wait until word break point.
            }

            // Add hyphenation and desperate break points.
            std::vector<HyphenBreak> hyphenedBreaks;
            std::vector<DesperateBreak> desperateBreaks;
            const Range contextRange = proc.contextRange();

            auto beginHyIter = hyIter;
            while (hyIter != std::end(measured.hyphenBreaks) &&
                   hyIter->offset < contextRange.getEnd()) {
                hyIter++;
            }
            if (proc.widthFromLastWordBreak() > minLineWidth) {
                desperateBreaks = populateDesperatePoints(textBuf, measured, contextRange, *run);
            }
            const bool doHyphenationRun = doHyphenation && run->canHyphenate();

            appendWithMerging(beginHyIter, doHyphenationRun ? hyIter : beginHyIter, desperateBreaks,
                              proc, hyphenPenalty, isRtl, letterSpacing, &result);

            // We skip breaks for zero-width characters inside replacement spans.
            if (run->getPaint() != nullptr || nextCharOffset == range.getEnd() ||
                measured.widths[nextCharOffset] > 0) {
                const float penalty = hyphenPenalty * proc.wordBreakPenalty();
                result.pushWordBreak(nextCharOffset, proc.sumOfCharWidths, proc.effectiveWidth,
                                     penalty, proc.rawSpaceCount, proc.effectiveSpaceCount, isRtl,
                                     letterSpacing);
            }
        }
    }
    result.spaceWidth = proc.spaceWidth;
    result.retryWithPhraseWordBreak = proc.retryWithPhraseWordBreak;
    return result;
}

class LineBreakOptimizer {
public:
    LineBreakOptimizer() {}

    LineBreakResult computeBreaks(const OptimizeContext& context, const U16StringPiece& textBuf,
                                  const MeasuredText& measuredText, const LineWidth& lineWidth,
                                  BreakStrategy strategy, bool justified, bool useBoundsForWidth);

private:
    // Data used to compute optimal line breaks
    struct OptimalBreaksData {
        float score;          // best score found for this break
        uint32_t prev;        // index to previous break
        uint32_t lineNumber;  // the computed line number of the candidate
    };
    LineBreakResult finishBreaksOptimal(const U16StringPiece& textBuf, const MeasuredText& measured,
                                        const std::vector<OptimalBreaksData>& breaksData,
                                        const std::vector<Candidate>& candidates,
                                        bool useBoundsForWidth);
};

// Follow "prev" links in candidates array, and copy to result arrays.
LineBreakResult LineBreakOptimizer::finishBreaksOptimal(
        const U16StringPiece& textBuf, const MeasuredText& measured,
        const std::vector<OptimalBreaksData>& breaksData, const std::vector<Candidate>& candidates,
        bool useBoundsForWidth) {
    LineBreakResult result;
    const uint32_t nCand = candidates.size();
    uint32_t prevIndex;
    for (uint32_t i = nCand - 1; i > 0; i = prevIndex) {
        prevIndex = breaksData[i].prev;
        const Candidate& cand = candidates[i];
        const Candidate& prev = candidates[prevIndex];

        result.breakPoints.push_back(cand.offset);
        result.widths.push_back(cand.postBreak - prev.preBreak);
        if (useBoundsForWidth) {
            Range range = Range(prev.offset, cand.offset);
            Range actualRange = trimTrailingLineEndSpaces(textBuf, range);
            if (actualRange.isEmpty()) {
                MinikinExtent extent = measured.getExtent(textBuf, range);
                result.ascents.push_back(extent.ascent);
                result.descents.push_back(extent.descent);
                result.bounds.emplace_back(0, extent.ascent, cand.postBreak - prev.preBreak,
                                           extent.descent);
            } else {
                LineMetrics metrics = measured.getLineMetrics(textBuf, actualRange);
                result.ascents.push_back(metrics.extent.ascent);
                result.descents.push_back(metrics.extent.descent);
                result.bounds.emplace_back(metrics.bounds);
            }
        } else {
            MinikinExtent extent = measured.getExtent(textBuf, Range(prev.offset, cand.offset));
            result.ascents.push_back(extent.ascent);
            result.descents.push_back(extent.descent);
            result.bounds.emplace_back(0, extent.ascent, cand.postBreak - prev.preBreak,
                                       extent.descent);
        }

        const HyphenEdit edit =
                packHyphenEdit(editForNextLine(prev.hyphenType), editForThisLine(cand.hyphenType));
        result.flags.push_back(static_cast<int>(edit));
    }
    result.reverse();
    return result;
}

LineBreakResult LineBreakOptimizer::computeBreaks(const OptimizeContext& context,
                                                  const U16StringPiece& textBuf,
                                                  const MeasuredText& measured,
                                                  const LineWidth& lineWidth,
                                                  BreakStrategy strategy, bool justified,
                                                  bool useBoundsForWidth) {
    const std::vector<Candidate>& candidates = context.candidates;
    uint32_t active = 0;
    const uint32_t nCand = candidates.size();
    const float maxShrink = justified ? SHRINKABILITY * context.spaceWidth : 0.0f;

    std::vector<OptimalBreaksData> breaksData;
    breaksData.reserve(nCand);
    breaksData.push_back({0.0, 0, 0});  // The first candidate is always at the first line.

    // "i" iterates through candidates for the end of the line.
    for (uint32_t i = 1; i < nCand; i++) {
        const bool atEnd = i == nCand - 1;
        float best = SCORE_INFTY;
        uint32_t bestPrev = 0;

        uint32_t lineNumberLast = breaksData[active].lineNumber;
        float width = lineWidth.getAt(lineNumberLast);

        ParaWidth leftEdge = candidates[i].postBreak - width;
        float bestHope = 0;

        // "j" iterates through candidates for the beginning of the line.
        for (uint32_t j = active; j < i; j++) {
            const uint32_t lineNumber = breaksData[j].lineNumber;
            if (lineNumber != lineNumberLast) {
                const float widthNew = lineWidth.getAt(lineNumber);
                if (widthNew != width) {
                    leftEdge = candidates[i].postBreak - width;
                    bestHope = 0;
                    width = widthNew;
                }
                lineNumberLast = lineNumber;
            }
            const float jScore = breaksData[j].score;
            if (jScore + bestHope >= best) continue;
            float delta = candidates[j].preBreak - leftEdge;

            if (useBoundsForWidth) {
                // FIXME: Support bounds based line break for hyphenated break point.
                if (candidates[i].hyphenType == HyphenationType::DONT_BREAK &&
                    candidates[j].hyphenType == HyphenationType::DONT_BREAK) {
                    if (delta >= 0) {
                        Range range = Range(candidates[j].offset, candidates[i].offset);
                        Range actualRange = trimTrailingLineEndSpaces(textBuf, range);
                        if (!actualRange.isEmpty() && measured.hasOverhang(range)) {
                            float boundsDelta =
                                    width - measured.getBounds(textBuf, actualRange).width();
                            if (boundsDelta < 0) {
                                delta = boundsDelta;
                            }
                        }
                    }
                }
            }

            // compute width score for line

            // Note: the "bestHope" optimization makes the assumption that, when delta is
            // non-negative, widthScore will increase monotonically as successive candidate
            // breaks are considered.
            float widthScore = 0.0f;
            float additionalPenalty = 0.0f;
            if ((atEnd || !justified) && delta < 0) {
                widthScore = SCORE_OVERFULL;
            } else if (atEnd && strategy != BreakStrategy::Balanced) {
                // increase penalty for hyphen on last line
                additionalPenalty = LAST_LINE_PENALTY_MULTIPLIER * candidates[j].penalty;
            } else {
                widthScore = delta * delta;
                if (delta < 0) {
                    if (-delta <
                        maxShrink * (candidates[i].postSpaceCount - candidates[j].preSpaceCount)) {
                        widthScore *= SHRINK_PENALTY_MULTIPLIER;
                    } else {
                        widthScore = SCORE_OVERFULL;
                    }
                }
            }

            if (delta < 0) {
                active = j + 1;
            } else {
                bestHope = widthScore;
            }

            const float score = jScore + widthScore + additionalPenalty;
            if (score <= best) {
                best = score;
                bestPrev = j;
            }
        }
        breaksData.push_back({best + candidates[i].penalty + context.linePenalty,  // score
                              bestPrev,                                            // prev
                              breaksData[bestPrev].lineNumber + 1});               // lineNumber
    }
    return finishBreaksOptimal(textBuf, measured, breaksData, candidates, useBoundsForWidth);
}

}  // namespace

LineBreakResult breakLineOptimal(const U16StringPiece& textBuf, const MeasuredText& measured,
                                 const LineWidth& lineWidth, BreakStrategy strategy,
                                 HyphenationFrequency frequency, bool justified,
                                 bool useBoundsForWidth) {
    if (textBuf.size() == 0) {
        return LineBreakResult();
    }

    const OptimizeContext context =
            populateCandidates(textBuf, measured, lineWidth, frequency, justified,
                               false /* forceWordStyleAutoToPhrase */);
    LineBreakOptimizer optimizer;
    LineBreakResult res = optimizer.computeBreaks(context, textBuf, measured, lineWidth, strategy,
                                                  justified, useBoundsForWidth);

    if (!features::word_style_auto()) {
        return res;
    }

    // The line breaker says that retry with phrase based word break because of the auto option and
    // given locales.
    if (!context.retryWithPhraseWordBreak) {
        return res;
    }

    // If the line break result is more than heuristics threshold, don't try pharse based word
    // break.
    if (res.breakPoints.size() >= LBW_AUTO_HEURISTICS_LINE_COUNT) {
        return res;
    }

    const OptimizeContext phContext =
            populateCandidates(textBuf, measured, lineWidth, frequency, justified,
                               true /* forceWordStyleAutoToPhrase */);
    LineBreakResult res2 = optimizer.computeBreaks(phContext, textBuf, measured, lineWidth,
                                                   strategy, justified, useBoundsForWidth);
    if (res2.breakPoints.size() < LBW_AUTO_HEURISTICS_LINE_COUNT) {
        return res2;
    } else {
        return res;
    }
}

}  // namespace minikin
