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

#define LOG_TAG "Minikin"
#include "minikin/MeasuredText.h"

#include "BidiUtils.h"
#include "LayoutSplitter.h"
#include "LayoutUtils.h"
#include "LineBreakerUtil.h"
#include "minikin/Characters.h"
#include "minikin/Layout.h"

namespace minikin {

// Helper class for composing character advances.
class AdvancesCompositor {
public:
    AdvancesCompositor(std::vector<float>* outAdvances, std::vector<uint8_t>* flags,
                       LayoutPieces* outPieces)
            : mOutAdvances(outAdvances), mFlags(flags), mOutPieces(outPieces) {}

    void setNextRange(const Range& range, bool dir) {
        mRange = range;
        mDir = dir;
    }

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& paint,
                    const MinikinRect& bounds) {
        const std::vector<float>& advances = layoutPiece.advances();
        std::copy(advances.begin(), advances.end(), mOutAdvances->begin() + mRange.getStart());

        if (bounds.mLeft < 0 || bounds.mRight > layoutPiece.advance()) {
            for (uint32_t i : mRange) {
                (*mFlags)[i] |= MeasuredText::MAY_OVERHANG_BIT;
            }
        }

        if (mOutPieces != nullptr) {
            mOutPieces->insert(mRange, 0 /* no edit */, layoutPiece, mDir, paint, bounds);
        }
    }

private:
    Range mRange;
    bool mDir;
    std::vector<float>* mOutAdvances;
    std::vector<uint8_t>* mFlags;
    LayoutPieces* mOutPieces;
};

void StyleRun::getMetrics(const U16StringPiece& textBuf, std::vector<float>* advances,
                          std::vector<uint8_t>* flags, LayoutPieces* precomputed,
                          bool boundsCalculation, LayoutPieces* outPieces) const {
    AdvancesCompositor compositor(advances, flags, outPieces);
    const Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    const uint32_t paintId =
            (precomputed == nullptr) ? LayoutPieces::kNoPaintId : precomputed->findPaintId(mPaint);
    for (const BidiText::RunInfo info : BidiText(textBuf, mRange, bidiFlag)) {
        for (const auto[context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            compositor.setNextRange(piece, info.isRtl);
            if (paintId == LayoutPieces::kNoPaintId) {
                LayoutCache::getInstance().getOrCreate(
                        textBuf.substr(context), piece - context.getStart(), mPaint, info.isRtl,
                        StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, boundsCalculation,
                        compositor);
            } else {
                precomputed->getOrCreate(textBuf, piece, context, mPaint, info.isRtl,
                                         StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, paintId,
                                         boundsCalculation, compositor);
            }
        }
    }
}

// Helper class for composing total advances.
class TotalAdvancesCompositor {
public:
    TotalAdvancesCompositor() : mOut(0) {}

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint&, const MinikinRect&) {
        for (float w : layoutPiece.advances()) {
            mOut += w;
        }
    }

    float getTotalAdvance() { return mOut; }

private:
    float mOut;
};

float StyleRun::measureText(const U16StringPiece& textBuf) const {
    TotalAdvancesCompositor compositor;
    const Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    LayoutCache& layoutCache = LayoutCache::getInstance();
    for (const BidiText::RunInfo info : BidiText(textBuf, Range(0, textBuf.length()), bidiFlag)) {
        for (const auto [context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            layoutCache.getOrCreate(textBuf.substr(context), piece - context.getStart(), mPaint,
                                    info.isRtl, StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT,
                                    false /* bounds calculation */, compositor);
        }
    }
    return compositor.getTotalAdvance();
}

// Helper class for composing total amount of advance
class TotalAdvanceCompositor {
public:
    TotalAdvanceCompositor(LayoutPieces* outPieces) : mTotalAdvance(0), mOutPieces(outPieces) {}

    void setNextContext(const Range& range, HyphenEdit edit, bool dir) {
        mRange = range;
        mEdit = edit;
        mDir = dir;
    }

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& paint,
                    const MinikinRect& bounds) {
        mTotalAdvance += layoutPiece.advance();
        if (mOutPieces != nullptr) {
            mOutPieces->insert(mRange, mEdit, layoutPiece, mDir, paint, bounds);
        }
    }

    float advance() const { return mTotalAdvance; }

private:
    float mTotalAdvance;
    Range mRange;
    HyphenEdit mEdit;
    bool mDir;
    LayoutPieces* mOutPieces;
};

float StyleRun::measureHyphenPiece(const U16StringPiece& textBuf, const Range& range,
                                   StartHyphenEdit startHyphen, EndHyphenEdit endHyphen,
                                   LayoutPieces* pieces) const {
    TotalAdvanceCompositor compositor(pieces);
    const Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    for (const BidiText::RunInfo info : BidiText(textBuf, range, bidiFlag)) {
        for (const auto[context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            const StartHyphenEdit startEdit =
                    piece.getStart() == range.getStart() ? startHyphen : StartHyphenEdit::NO_EDIT;
            const EndHyphenEdit endEdit =
                    piece.getEnd() == range.getEnd() ? endHyphen : EndHyphenEdit::NO_EDIT;

            compositor.setNextContext(piece, packHyphenEdit(startEdit, endEdit), info.isRtl);
            LayoutCache::getInstance().getOrCreate(
                    textBuf.substr(context), piece - context.getStart(), mPaint, info.isRtl,
                    startEdit, endEdit, false /* bounds calculation */, compositor);
        }
    }
    return compositor.advance();
}

void MeasuredText::measure(const U16StringPiece& textBuf, bool computeHyphenation,
                           bool computeLayout, bool computeBounds, bool ignoreHyphenKerning,
                           MeasuredText* hint) {
    if (textBuf.size() == 0) {
        return;
    }

    LayoutPieces* piecesOut = computeLayout ? &layoutPieces : nullptr;
    CharProcessor proc(textBuf);
    for (const auto& run : runs) {
        const Range& range = run->getRange();
        run->getMetrics(textBuf, &widths, &flags, hint ? &hint->layoutPieces : nullptr,
                        computeBounds, piecesOut);

        if (!computeHyphenation || !run->canBreak()) {
            continue;
        }

        proc.updateLocaleIfNecessary(*run, false /* forceWordStyleAutoToPhrase */);
        for (uint32_t i = range.getStart(); i < range.getEnd(); ++i) {
            // Even if the run is not a candidate of line break, treat the end of run as the line
            // break candidate.
            const bool canBreak = run->canBreak() || (i + 1) == range.getEnd();
            proc.feedChar(i, textBuf[i], widths[i], canBreak);

            const uint32_t nextCharOffset = i + 1;
            if (nextCharOffset != proc.nextWordBreak) {
                continue;  // Wait until word break point.
            }

            populateHyphenationPoints(textBuf, *run, *proc.hyphenator, proc.contextRange(),
                                      proc.wordRange(), widths, ignoreHyphenKerning, &hyphenBreaks,
                                      piecesOut);
        }
    }
}

// Helper class for composing Layout object.
class LayoutCompositor {
public:
    LayoutCompositor(Layout* outLayout, float extraAdvance)
            : mOutLayout(outLayout), mExtraAdvance(extraAdvance) {}

    void setOutOffset(uint32_t outOffset) { mOutOffset = outOffset; }

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& /* paint */,
                    const MinikinRect&) {
        mOutLayout->appendLayout(layoutPiece, mOutOffset, mExtraAdvance);
    }

    uint32_t mOutOffset;
    Layout* mOutLayout;
    float mExtraAdvance;
};

void StyleRun::appendLayout(const U16StringPiece& textBuf, const Range& range,
                            const Range& /* context */, const LayoutPieces& pieces,
                            const MinikinPaint& paint, uint32_t outOrigin,
                            StartHyphenEdit startHyphen, EndHyphenEdit endHyphen,
                            Layout* outLayout) const {
    bool boundsCalculation = false;
    float wordSpacing = range.getLength() == 1 && isWordSpace(textBuf[range.getStart()])
                                ? mPaint.wordSpacing
                                : 0;
    bool canUsePrecomputedResult = mPaint == paint;

    LayoutCompositor compositor(outLayout, wordSpacing);
    const Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    const uint32_t paintId = pieces.findPaintId(mPaint);
    for (const BidiText::RunInfo info : BidiText(textBuf, range, bidiFlag)) {
        for (const auto[context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            compositor.setOutOffset(piece.getStart() - outOrigin);
            const StartHyphenEdit startEdit =
                    range.getStart() == piece.getStart() ? startHyphen : StartHyphenEdit::NO_EDIT;
            const EndHyphenEdit endEdit =
                    range.getEnd() == piece.getEnd() ? endHyphen : EndHyphenEdit::NO_EDIT;

            if (canUsePrecomputedResult) {
                pieces.getOrCreate(textBuf, piece, context, mPaint, info.isRtl, startEdit, endEdit,
                                   paintId, boundsCalculation, compositor);
            } else {
                LayoutCache::getInstance().getOrCreate(
                        textBuf.substr(context), piece - context.getStart(), paint, info.isRtl,
                        startEdit, endEdit, boundsCalculation, compositor);
            }
        }
    }
}

// Helper class for composing bounding box.
class BoundsCompositor {
public:
    BoundsCompositor() : mAdvance(0) {}

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& /* paint */,
                    const MinikinRect& bounds) {
        mBounds.join(bounds, mAdvance, 0);
        mAdvance += layoutPiece.advance();
    }

    const MinikinRect& bounds() const { return mBounds; }
    float advance() const { return mAdvance; }

private:
    float mAdvance;
    MinikinRect mBounds;
};

std::pair<float, MinikinRect> StyleRun::getBounds(const U16StringPiece& textBuf, const Range& range,
                                                  const LayoutPieces& pieces) const {
    BoundsCompositor compositor;
    const Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    const uint32_t paintId = pieces.findPaintId(mPaint);
    for (const BidiText::RunInfo info : BidiText(textBuf, range, bidiFlag)) {
        for (const auto[context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            pieces.getOrCreate(textBuf, piece, context, mPaint, info.isRtl,
                               StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, paintId,
                               true /* bounds calculation */, compositor);
        }
    }
    return std::make_pair(compositor.advance(), compositor.bounds());
}

// Helper class for composing total extent.
class ExtentCompositor {
public:
    ExtentCompositor() {}

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& /* paint */,
                    const MinikinRect&) {
        mExtent.extendBy(layoutPiece.extent());
    }

    const MinikinExtent& extent() const { return mExtent; }

private:
    MinikinExtent mExtent;
};

MinikinExtent StyleRun::getExtent(const U16StringPiece& textBuf, const Range& range,
                                  const LayoutPieces& pieces) const {
    ExtentCompositor compositor;
    Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    const uint32_t paintId = pieces.findPaintId(mPaint);
    for (const BidiText::RunInfo info : BidiText(textBuf, range, bidiFlag)) {
        for (const auto[context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            pieces.getOrCreate(textBuf, piece, context, mPaint, info.isRtl,
                               StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, paintId,
                               false /* bounds calculation */, compositor);
        }
    }
    return compositor.extent();
}

class LineMetricsCompositor {
public:
    LineMetricsCompositor() {}

    void operator()(const LayoutPiece& layoutPiece, const MinikinPaint& /* paint */,
                    const MinikinRect& bounds) {
        mMetrics.append(layoutPiece.extent(), bounds, layoutPiece.advance());
    }

    const LineMetrics& metrics() const { return mMetrics; }

private:
    LineMetrics mMetrics;
};

LineMetrics StyleRun::getLineMetrics(const U16StringPiece& textBuf, const Range& range,
                                     const LayoutPieces& pieces) const {
    LineMetricsCompositor compositor;
    Bidi bidiFlag = mIsRtl ? Bidi::FORCE_RTL : Bidi::FORCE_LTR;
    const uint32_t paintId = pieces.findPaintId(mPaint);
    for (const BidiText::RunInfo info : BidiText(textBuf, range, bidiFlag)) {
        for (const auto [context, piece] : LayoutSplitter(textBuf, info.range, info.isRtl)) {
            pieces.getOrCreate(textBuf, piece, context, mPaint, info.isRtl,
                               StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, paintId,
                               true /* bounds calculation */, compositor);
        }
    }
    return compositor.metrics();
}

Layout MeasuredText::buildLayout(const U16StringPiece& textBuf, const Range& range,
                                 const Range& contextRange, const MinikinPaint& paint,
                                 StartHyphenEdit startHyphen, EndHyphenEdit endHyphen) {
    Layout outLayout(range.getLength());
    for (const auto& run : runs) {
        const Range& runRange = run->getRange();
        if (!Range::intersects(range, runRange)) {
            continue;
        }
        const Range targetRange = Range::intersection(runRange, range);
        StartHyphenEdit startEdit =
                targetRange.getStart() == range.getStart() ? startHyphen : StartHyphenEdit::NO_EDIT;
        EndHyphenEdit endEdit =
                targetRange.getEnd() == range.getEnd() ? endHyphen : EndHyphenEdit::NO_EDIT;
        run->appendLayout(textBuf, targetRange, contextRange, layoutPieces, paint, range.getStart(),
                          startEdit, endEdit, &outLayout);
    }
    return outLayout;
}

MinikinRect MeasuredText::getBounds(const U16StringPiece& textBuf, const Range& range) const {
    MinikinRect rect;
    float totalAdvance = 0.0f;

    for (const auto& run : runs) {
        const Range& runRange = run->getRange();
        if (!Range::intersects(range, runRange)) {
            continue;
        }
        auto[advance, bounds] =
                run->getBounds(textBuf, Range::intersection(runRange, range), layoutPieces);
        rect.join(bounds, totalAdvance, 0);
        totalAdvance += advance;
    }
    return rect;
}

MinikinExtent MeasuredText::getExtent(const U16StringPiece& textBuf, const Range& range) const {
    MinikinExtent extent;
    for (const auto& run : runs) {
        const Range& runRange = run->getRange();
        if (!Range::intersects(range, runRange)) {
            continue;
        }
        extent.extendBy(
                run->getExtent(textBuf, Range::intersection(runRange, range), layoutPieces));
    }
    return extent;
}

LineMetrics MeasuredText::getLineMetrics(const U16StringPiece& textBuf, const Range& range) const {
    LineMetrics metrics;
    for (const auto& run : runs) {
        const Range& runRange = run->getRange();
        if (!Range::intersects(range, runRange)) {
            continue;
        }
        metrics.append(
                run->getLineMetrics(textBuf, Range::intersection(runRange, range), layoutPieces));
    }
    return metrics;
}

}  // namespace minikin
