/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define LOG_TAG "Minikin"

#include "minikin/Layout.h"

#include <hb-icu.h>
#include <hb-ot.h>
#include <log/log.h>
#include <unicode/ubidi.h>
#include <unicode/utf16.h>
#include <utils/LruCache.h>

#include <cmath>
#include <iostream>
#include <mutex>
#include <string>
#include <vector>

#include "BidiUtils.h"
#include "LayoutSplitter.h"
#include "LayoutUtils.h"
#include "LetterSpacingUtils.h"
#include "LocaleListCache.h"
#include "MinikinInternal.h"
#include "minikin/Emoji.h"
#include "minikin/HbUtils.h"
#include "minikin/LayoutCache.h"
#include "minikin/LayoutPieces.h"
#include "minikin/Macros.h"

namespace minikin {

// TODO: Instead of two-pass letter space adjustment, apply letter space after populating from the
// layout cache
void adjustGlyphLetterSpacingEdge(const U16StringPiece& textBuf, const MinikinPaint& paint,
                                  RunFlag runFlag, std::vector<LayoutGlyph>* glyphs) {
    const float letterSpacing = paint.letterSpacing * paint.size * paint.scaleX;
    const float letterSpacingHalf = letterSpacing * 0.5f;
    const uint32_t glyphCount = glyphs->size();

    if (runFlag & RunFlag::LEFT_EDGE) {
        uint32_t i;
        for (i = 0; i < glyphCount; ++i) {
            const uint32_t cluster = glyphs->at(i).cluster;
            const uint32_t cp = textBuf.codePointAt(cluster);
            if (!u_iscntrl(cp)) {
                break;
            }
        }

        for (; i < glyphCount; ++i) {
            const uint32_t cluster = glyphs->at(i).cluster;
            const uint32_t cp = textBuf.codePointAt(cluster);
            if (!isLetterSpacingCapableCodePoint(cp)) {
                break;
            }
            glyphs->at(i).x -= letterSpacingHalf;
        }
    }

    if (runFlag & RunFlag::RIGHT_EDGE) {
        uint32_t i;
        for (i = 0; i < glyphCount; ++i) {
            const uint32_t cluster = glyphs->at(glyphCount - 1 - i).cluster;
            const uint32_t cp = textBuf.codePointAt(cluster);
            if (!u_iscntrl(cp)) {
                if (!isLetterSpacingCapableCodePoint(cp)) {
                    i = glyphCount;
                }
                break;
            }
        }

        if (i < glyphCount) {
            for (uint32_t j = glyphCount - i; j < glyphCount; ++j) {
                glyphs->at(j).x -= letterSpacingHalf;
            }
        }
    }
}

// TODO: Instead of two-pass letter space adjustment, apply letter space after populating from the
// layout cache
float adjustAdvanceLetterSpacingEdge(const U16StringPiece& textBuf, const Range& range,
                                     const BidiText& bidiText, const MinikinPaint& paint,
                                     RunFlag runFlag, float advance, float* advances) {
    const float letterSpacing = paint.letterSpacing * paint.size * paint.scaleX;
    const float letterSpacingHalf = letterSpacing * 0.5f;
    if (letterSpacing == 0 || textBuf.length() == 0) {
        return advance;
    }
    const uint32_t advOffset = range.getStart();

    if (runFlag & RunFlag::LEFT_EDGE) {
        const BidiText::RunInfo lRun = bidiText.getRunInfoAt(0);
        if (lRun.isRtl) {
            uint32_t lastNonCtrlCharIndex = static_cast<uint32_t>(-1);
            for (uint32_t i : lRun.range) {
                if (!u_iscntrl(textBuf.codePointAt(i)) && advances[i - advOffset] != 0) {
                    lastNonCtrlCharIndex = i;
                }
            }
            if (lastNonCtrlCharIndex != static_cast<uint32_t>(-1)) {
                uint32_t cp = textBuf.codePointAt(lastNonCtrlCharIndex);
                if (isLetterSpacingCapableCodePoint(cp)) {
                    advances[lastNonCtrlCharIndex - advOffset] -= letterSpacingHalf;
                    advance -= letterSpacingHalf;
                }
            }
        } else {
            for (uint32_t i : lRun.range) {
                uint32_t cp = textBuf.codePointAt(i);
                if (!u_iscntrl(cp) && advances[i - advOffset] != 0) {
                    if (isLetterSpacingCapableCodePoint(cp)) {
                        advances[i - advOffset] -= letterSpacingHalf;
                        advance -= letterSpacingHalf;
                    }
                    break;
                }
            }
        }
    }

    if (runFlag & RunFlag::RIGHT_EDGE) {
        const BidiText::RunInfo rRun = bidiText.getRunInfoAt(bidiText.getRunCount() - 1);
        if (rRun.isRtl) {
            for (uint32_t i : rRun.range) {
                uint32_t cp = textBuf.codePointAt(i);
                if (!u_iscntrl(cp) && advances[i - advOffset] != 0) {
                    if (isLetterSpacingCapableCodePoint(cp)) {
                        advances[i - advOffset] -= letterSpacingHalf;
                        advance -= letterSpacingHalf;
                    }
                    break;
                }
            }
        } else {
            uint32_t lastNonCtrlCharIndex = static_cast<uint32_t>(-1);
            for (uint32_t i : rRun.range) {
                if (!u_iscntrl(textBuf.codePointAt(i)) && advances[i - advOffset] != 0) {
                    lastNonCtrlCharIndex = i;
                }
            }
            if (lastNonCtrlCharIndex != static_cast<uint32_t>(-1)) {
                uint32_t cp = textBuf.codePointAt(lastNonCtrlCharIndex);
                if (isLetterSpacingCapableCodePoint(cp)) {
                    advances[lastNonCtrlCharIndex - advOffset] -= letterSpacingHalf;
                    advance -= letterSpacingHalf;
                }
            }
        }
    }
    return advance;
}

// TODO: Instead of two-pass letter space adjustment, apply letter space after populating from the
// layout cache
void adjustBoundsLetterSpacingEdge(const MinikinPaint& paint, RunFlag runFlag,
                                   MinikinRect* bounds) {
    if (!bounds) {
        return;
    }
    const float letterSpacing = paint.letterSpacing * paint.size * paint.scaleX;
    const float letterSpacingHalf = letterSpacing * 0.5f;
    if (letterSpacing == 0) {
        return;
    }
    if (runFlag & RunFlag::LEFT_EDGE) {
        bounds->mLeft -= letterSpacingHalf;
        bounds->mRight -= letterSpacingHalf;
    }

    if (runFlag & RunFlag::RIGHT_EDGE) {
        bounds->mRight -= letterSpacingHalf;
    }
}

void Layout::doLayout(const U16StringPiece& textBuf, const Range& range, Bidi bidiFlags,
                      const MinikinPaint& paint, StartHyphenEdit startHyphen,
                      EndHyphenEdit endHyphen, RunFlag runFlag) {
    const uint32_t count = range.getLength();
    mAdvances.resize(count, 0);
    mGlyphs.reserve(count);
    const BidiText bidiText(textBuf, range, bidiFlags);
    for (const BidiText::RunInfo& runInfo : bidiText) {
        doLayoutRunCached(textBuf, runInfo.range, runInfo.isRtl, paint, range.getStart(),
                          startHyphen, endHyphen, this, nullptr, nullptr, nullptr);
    }
    U16StringPiece substr = textBuf.substr(range);
    adjustGlyphLetterSpacingEdge(substr, paint, runFlag, &mGlyphs);
    mAdvance = adjustAdvanceLetterSpacingEdge(textBuf, range, bidiText, paint, runFlag, mAdvance,
                                              mAdvances.data());
}

float Layout::measureText(const U16StringPiece& textBuf, const Range& range, Bidi bidiFlags,
                          const MinikinPaint& paint, StartHyphenEdit startHyphen,
                          EndHyphenEdit endHyphen, float* advances, MinikinRect* bounds,
                          uint32_t* clusterCount, RunFlag runFlag) {
    float advance = 0;
    if (clusterCount) {
        *clusterCount = 0;
    }

    // To distinguish the control characters and combining character, need per character advances.
    std::vector<float> tmpAdvances;
    if (advances == nullptr) {
        tmpAdvances.resize(range.getLength());
        advances = tmpAdvances.data();
    }

    MinikinRect tmpBounds;
    const BidiText bidiText(textBuf, range, bidiFlags);
    for (const BidiText::RunInfo& runInfo : bidiText) {
        const size_t offset = range.toRangeOffset(runInfo.range.getStart());
        float* advancesForRun = advances ? advances + offset : nullptr;
        tmpBounds.setEmpty();
        float run_advance = doLayoutRunCached(textBuf, runInfo.range, runInfo.isRtl, paint, 0,
                                              startHyphen, endHyphen, nullptr, advancesForRun,
                                              bounds ? &tmpBounds : nullptr, clusterCount);
        if (bounds) {
            bounds->join(tmpBounds, advance, 0);
        }
        advance += run_advance;
    }
    adjustBoundsLetterSpacingEdge(paint, runFlag, bounds);
    return adjustAdvanceLetterSpacingEdge(textBuf, range, bidiText, paint, runFlag, advance,
                                          advances);
}

float Layout::doLayoutRunCached(const U16StringPiece& textBuf, const Range& range, bool isRtl,
                                const MinikinPaint& paint, size_t dstStart,
                                StartHyphenEdit startHyphen, EndHyphenEdit endHyphen,
                                Layout* layout, float* advances, MinikinRect* bounds,
                                uint32_t* clusterCount) {
    if (!range.isValid()) {
        return 0.0f;  // ICU failed to retrieve the bidi run?
    }
    float advance = 0;
    MinikinRect tmpBounds;
    for (const auto[context, piece] : LayoutSplitter(textBuf, range, isRtl)) {
        // Hyphenation only applies to the start/end of run.
        const StartHyphenEdit pieceStartHyphen =
                (piece.getStart() == range.getStart()) ? startHyphen : StartHyphenEdit::NO_EDIT;
        const EndHyphenEdit pieceEndHyphen =
                (piece.getEnd() == range.getEnd()) ? endHyphen : EndHyphenEdit::NO_EDIT;
        float* advancesForRun =
                advances ? advances + (piece.getStart() - range.getStart()) : nullptr;
        tmpBounds.setEmpty();
        float word_advance = doLayoutWord(
                textBuf.data() + context.getStart(), piece.getStart() - context.getStart(),
                piece.getLength(), context.getLength(), isRtl, paint, piece.getStart() - dstStart,
                pieceStartHyphen, pieceEndHyphen, layout, advancesForRun,
                bounds ? &tmpBounds : nullptr, clusterCount);
        if (bounds) {
            bounds->join(tmpBounds, advance, 0);
        }
        advance += word_advance;
    }
    return advance;
}

class LayoutAppendFunctor {
public:
    LayoutAppendFunctor(Layout* layout, float* advances, uint32_t outOffset, float wordSpacing,
                        MinikinRect* bounds)
            : mLayout(layout),
              mAdvances(advances),
              mOutOffset(outOffset),
              mWordSpacing(wordSpacing),
              mBounds(bounds) {}

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& /* paint */,
                    const MinikinRect& bounds) {
        if (mLayout) {
            mLayout->appendLayout(layoutPiece, mOutOffset, mWordSpacing);
        }
        if (mAdvances) {
            const std::vector<float>& advances = layoutPiece.advances();
            std::copy(advances.begin(), advances.end(), mAdvances);
        }
        if (mBounds) {
            mBounds->join(bounds, mTotalAdvance, 0);
        }
        mTotalAdvance += layoutPiece.advance();
        mClusterCount += layoutPiece.clusterCount();
    }

    float getTotalAdvance() { return mTotalAdvance; }
    uint32_t getClusterCount() const { return mClusterCount; }

private:
    Layout* mLayout;
    float* mAdvances;
    float mTotalAdvance{0};
    const uint32_t mOutOffset;
    const float mWordSpacing;
    uint32_t mClusterCount{0};
    MinikinRect* mBounds;
};

float Layout::doLayoutWord(const uint16_t* buf, size_t start, size_t count, size_t bufSize,
                           bool isRtl, const MinikinPaint& paint, size_t bufStart,
                           StartHyphenEdit startHyphen, EndHyphenEdit endHyphen, Layout* layout,
                           float* advances, MinikinRect* bounds, uint32_t* clusterCount) {
    float wordSpacing = count == 1 && isWordSpace(buf[start]) ? paint.wordSpacing : 0;
    float totalAdvance = 0;
    const bool boundsCalculation = bounds != nullptr;

    const U16StringPiece textBuf(buf, bufSize);
    const Range range(start, start + count);
    LayoutAppendFunctor f(layout, advances, bufStart, wordSpacing, bounds);
    LayoutCache::getInstance().getOrCreate(textBuf, range, paint, isRtl, startHyphen, endHyphen,
                                           boundsCalculation, f);
    totalAdvance = f.getTotalAdvance();
    if (clusterCount) {
        *clusterCount += f.getClusterCount();
    }

    if (wordSpacing != 0) {
        totalAdvance += wordSpacing;
        if (advances) {
            advances[0] += wordSpacing;
        }
    }
    return totalAdvance;
}

void Layout::appendLayout(const LayoutPiece& src, size_t start, float extraAdvance) {
    for (size_t i = 0; i < src.glyphCount(); i++) {
        mGlyphs.emplace_back(src.fontAt(i), src.glyphIdAt(i), src.clusterAt(i) + start,
                             mAdvance + src.pointAt(i).x, src.pointAt(i).y);
    }
    const std::vector<float>& advances = src.advances();
    for (size_t i = 0; i < advances.size(); i++) {
        mAdvances[i + start] = advances[i];
        if (i == 0) {
            mAdvances[start] += extraAdvance;
        }
    }
    mAdvance += src.advance() + extraAdvance;
}

void Layout::purgeCaches() {
    LayoutCache::getInstance().clear();
}

}  // namespace minikin
