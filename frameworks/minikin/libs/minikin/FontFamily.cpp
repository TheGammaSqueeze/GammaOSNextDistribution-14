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

#include "minikin/FontFamily.h"

#include <log/log.h>

#include <algorithm>
#include <unordered_set>
#include <vector>

#include "FontUtils.h"
#include "Locale.h"
#include "LocaleListCache.h"
#include "MinikinInternal.h"
#include "minikin/CmapCoverage.h"
#include "minikin/Constants.h"
#include "minikin/FamilyVariant.h"
#include "minikin/HbUtils.h"
#include "minikin/MinikinFont.h"

namespace minikin {

namespace {
const std::shared_ptr<FontFamily>& getAncestor(const std::shared_ptr<FontFamily>& family) {
    return !family->getParent() ? family : getAncestor(family->getParent());
}
}  // namespace

// static
std::shared_ptr<FontFamily> FontFamily::create(std::vector<std::shared_ptr<Font>>&& fonts) {
    return create(FamilyVariant::DEFAULT, std::move(fonts));
}

// static
std::shared_ptr<FontFamily> FontFamily::create(FamilyVariant variant,
                                               std::vector<std::shared_ptr<Font>>&& fonts) {
    return create(kEmptyLocaleListId, variant, std::move(fonts), false /* isCustomFallback */,
                  false /* isDefaultFallback */, VariationFamilyType::None);
}

// static
std::shared_ptr<FontFamily> FontFamily::create(uint32_t localeListId, FamilyVariant variant,
                                               std::vector<std::shared_ptr<Font>>&& fonts,
                                               bool isCustomFallback, bool isDefaultFallback,
                                               VariationFamilyType varFamilyType) {
    // TODO(b/174672300): Revert back to make_shared.
    return std::shared_ptr<FontFamily>(new FontFamily(localeListId, variant, std::move(fonts),
                                                      isCustomFallback, isDefaultFallback,
                                                      varFamilyType));
}

std::shared_ptr<FontFamily> FontFamily::create(const std::shared_ptr<FontFamily>& parent,
                                               const std::vector<FontVariation>& axes) {
    if (axes.empty() || parent->getSupportedAxesCount() == 0) {
        return nullptr;
    }

    bool hasSupportedAxis = false;
    for (const auto& axis : axes) {
        for (uint32_t i = 0; i < parent->getSupportedAxesCount(); ++i) {
            if (axis.axisTag == parent->getSupportedAxisAt(i)) {
                hasSupportedAxis = true;
                break;
            }
        }
    }
    if (!hasSupportedAxis) {
        // None of variation axes are suppored by this family.
        return nullptr;
    }

    return std::shared_ptr<FontFamily>(new FontFamily(parent, axes));
}

FontFamily::FontFamily(const std::shared_ptr<FontFamily>& parent,
                       const std::vector<FontVariation>& axesOverride)
        : mFonts(),
          mSupportedAxes(std::make_unique<AxisTag[]>(parent->getSupportedAxesCount())),
          mCoverage(),
          mCmapFmt14Coverage(nullptr),
          mParent(getAncestor(parent)),  // Use ancestor as parent.
          mVarOverride(axesOverride),
          mLocaleListId(parent->mLocaleListId),
          mFontsCount(parent->mFontsCount),
          mSupportedAxesCount(parent->mSupportedAxesCount),
          mCmapFmt14CoverageCount(parent->mCmapFmt14CoverageCount),
          mVariant(parent->mVariant),
          mIsColorEmoji(parent->mIsColorEmoji),
          mIsCustomFallback(parent->mIsCustomFallback),
          mIsDefaultFallback(parent->mIsDefaultFallback),
          mVarFamilyType(parent->mVarFamilyType) {
    // Filter only the axis supported font.
    std::vector<std::shared_ptr<Font>> overriddenFonts;
    for (uint16_t i = 0; i < mFontsCount; ++i) {
        const auto& font = parent->mFonts[i];
        bool isSupported = false;
        for (const auto& axis : axesOverride) {
            if (font->isAxisSupported(axis.axisTag)) {
                isSupported = true;
                break;
            }
        }
        if (isSupported) {
            overriddenFonts.emplace_back(std::make_shared<Font>(font, axesOverride));
        }
    }
    mFonts = std::make_unique<std::shared_ptr<Font>[]>(overriddenFonts.size());
    for (uint16_t i = 0; i < overriddenFonts.size(); ++i) {
        mFonts[i] = std::move(overriddenFonts[i]);
    }

    // Copy the supported axes
    const AxisTag* src = parent->mSupportedAxes.get();
    std::copy(src, src + mSupportedAxesCount, mSupportedAxes.get());
}

FontFamily::FontFamily(uint32_t localeListId, FamilyVariant variant,
                       std::vector<std::shared_ptr<Font>>&& fonts, bool isCustomFallback,
                       bool isDefaultFallback, VariationFamilyType varFamilyType)
        : mFonts(std::make_unique<std::shared_ptr<Font>[]>(fonts.size())),
          // computeCoverage may update supported axes and coverages later.
          mSupportedAxes(nullptr),
          mCoverage(),
          mCmapFmt14Coverage(nullptr),
          mParent(nullptr),
          mVarOverride(),
          mLocaleListId(localeListId),
          mFontsCount(static_cast<uint32_t>(fonts.size())),
          mSupportedAxesCount(0),
          mCmapFmt14CoverageCount(0),
          mVariant(variant),
          mIsColorEmoji(LocaleListCache::getById(localeListId).getEmojiStyle() ==
                        EmojiStyle::EMOJI),
          mIsCustomFallback(isCustomFallback),
          mIsDefaultFallback(isDefaultFallback),
          mVarFamilyType(varFamilyType) {
    MINIKIN_ASSERT(!fonts.empty(), "FontFamily must contain at least one font.");
    MINIKIN_ASSERT(fonts.size() <= std::numeric_limits<uint32_t>::max(),
                   "Number of fonts must be less than 2^32.");
    for (size_t i = 0; i < mFontsCount; i++) {
        mFonts[i] = std::move(fonts[i]);
    }
    computeCoverage();
}

FontFamily::FontFamily(BufferReader* reader, const std::shared_ptr<std::vector<Font>>& allFonts)
        : mSupportedAxes(nullptr), mCmapFmt14Coverage(nullptr), mParent(nullptr), mVarOverride() {
    mLocaleListId = LocaleListCache::readFrom(reader);
    mFontsCount = reader->read<uint32_t>();
    mFonts = std::make_unique<std::shared_ptr<Font>[]>(mFontsCount);
    for (size_t i = 0; i < mFontsCount; i++) {
        uint32_t fontIndex = reader->read<uint32_t>();
        // Use aliasing constructor to save memory.
        // See the comments on FontFamily::readVector for details.
        mFonts[i] = std::shared_ptr<Font>(allFonts, &(*allFonts)[fontIndex]);
    }
    // FamilyVariant is uint8_t
    static_assert(sizeof(FamilyVariant) == 1);
    mVariant = reader->read<FamilyVariant>();
    // AxisTag is uint32_t
    static_assert(sizeof(AxisTag) == 4);
    const auto& [axesPtr, axesCount] = reader->readArray<AxisTag>();
    mSupportedAxesCount = axesCount;
    if (axesCount > 0) {
        mSupportedAxes = std::unique_ptr<AxisTag[]>(new AxisTag[axesCount]);
        std::copy(axesPtr, axesPtr + axesCount, mSupportedAxes.get());
    }
    mIsColorEmoji = static_cast<bool>(reader->read<uint8_t>());
    mIsCustomFallback = static_cast<bool>(reader->read<uint8_t>());
    mIsDefaultFallback = static_cast<bool>(reader->read<uint8_t>());
    mVarFamilyType = reader->read<VariationFamilyType>();
    mCoverage = SparseBitSet(reader);
    // Read mCmapFmt14Coverage. As it can have null entries, it is stored in the buffer as a sparse
    // array (size, non-null entry count, array of (index, entry)).
    mCmapFmt14CoverageCount = reader->read<uint32_t>();
    if (mCmapFmt14CoverageCount > 0) {
        mCmapFmt14Coverage = std::make_unique<SparseBitSet[]>(mCmapFmt14CoverageCount);
        uint32_t cmapFmt14CoverageEntryCount = reader->read<uint32_t>();
        for (uint32_t i = 0; i < cmapFmt14CoverageEntryCount; i++) {
            uint32_t index = reader->read<uint32_t>();
            mCmapFmt14Coverage[index] = SparseBitSet(reader);
        }
    }
}

void FontFamily::writeTo(BufferWriter* writer, uint32_t* fontIndex) const {
    MINIKIN_ASSERT(mParent == nullptr, "Do not serialize variation overridden font families.");
    LocaleListCache::writeTo(writer, mLocaleListId);
    writer->write<uint32_t>(mFontsCount);
    for (size_t i = 0; i < mFontsCount; i++) {
        writer->write<uint32_t>(*fontIndex);
        (*fontIndex)++;
    }
    writer->write<FamilyVariant>(mVariant);
    writer->writeArray<AxisTag>(mSupportedAxes.get(), mSupportedAxesCount);
    writer->write<uint8_t>(mIsColorEmoji);
    writer->write<uint8_t>(mIsCustomFallback);
    writer->write<uint8_t>(mIsDefaultFallback);
    writer->write<VariationFamilyType>(mVarFamilyType);
    mCoverage.writeTo(writer);
    // Write mCmapFmt14Coverage as a sparse array (size, non-null entry count,
    // array of (index, entry))
    writer->write<uint32_t>(mCmapFmt14CoverageCount);
    // Skip writing the sparse entries if the size is zero
    if (mCmapFmt14CoverageCount > 0) {
        uint32_t cmapFmt14CoverageEntryCount = 0;
        for (size_t i = 0; i < mCmapFmt14CoverageCount; i++) {
            if (!mCmapFmt14Coverage[i].empty()) cmapFmt14CoverageEntryCount++;
        }
        writer->write<uint32_t>(cmapFmt14CoverageEntryCount);
        for (size_t i = 0; i < mCmapFmt14CoverageCount; i++) {
            if (!mCmapFmt14Coverage[i].empty()) {
                writer->write<uint32_t>(i);
                mCmapFmt14Coverage[i].writeTo(writer);
            }
        }
    }
}

// static
std::vector<std::shared_ptr<FontFamily>> FontFamily::readVector(BufferReader* reader) {
    // To save memory used for reference counting objects, we store
    // Font / FontFamily in shared_ptr<vector<Font / FontFamily>>, and use
    // shared_ptr's aliasing constructor to create shared_ptr<Font / FontFamily>
    // that share the reference counting objects with
    // the shared_ptr<vector<Font / FontFamily>>.
    // We can do this because we know that all Font and FontFamily
    // instances based on the same BufferReader share the same life span.
    uint32_t fontsCount = reader->read<uint32_t>();
    std::shared_ptr<std::vector<Font>> fonts = std::make_shared<std::vector<Font>>();
    fonts->reserve(fontsCount);
    for (uint32_t i = 0; i < fontsCount; i++) {
        fonts->emplace_back(reader);
    }
    uint32_t count = reader->read<uint32_t>();
    std::shared_ptr<std::vector<FontFamily>> families = std::make_shared<std::vector<FontFamily>>();
    families->reserve(count);
    std::vector<std::shared_ptr<FontFamily>> pointers;
    pointers.reserve(count);
    for (uint32_t i = 0; i < count; i++) {
        // TODO(b/174672300): Revert back to emplace_back.
        families->push_back(FontFamily(reader, fonts));
        // Use aliasing constructor.
        pointers.emplace_back(families, &families->back());
    }
    return pointers;
}

// static
void FontFamily::writeVector(BufferWriter* writer,
                             const std::vector<std::shared_ptr<FontFamily>>& families) {
    std::vector<std::shared_ptr<Font>> fonts;
    for (const auto& fontFamily : families) {
        for (uint32_t i = 0; i < fontFamily->getNumFonts(); i++) {
            fonts.emplace_back(fontFamily->getFontRef(i));
        }
    }
    writer->write<uint32_t>(fonts.size());
    for (const auto& font : fonts) {
        font->writeTo(writer);
    }
    uint32_t fontIndex = 0;
    writer->write<uint32_t>(families.size());
    for (const auto& fontFamily : families) {
        fontFamily->writeTo(writer, &fontIndex);
    }
}

// Compute a matching metric between two styles - 0 is an exact match
static int computeMatch(FontStyle style1, FontStyle style2) {
    if (style1 == style2) return 0;
    int score = abs(style1.weight() / 100 - style2.weight() / 100);
    if (style1.slant() != style2.slant()) {
        score += 2;
    }
    return score;
}

static FontFakery computeFakery(FontStyle wanted, FontStyle actual) {
    // If desired weight is semibold or darker, and 2 or more grades
    // higher than actual (for example, medium 500 -> bold 700), then
    // select fake bold.
    bool isFakeBold = wanted.weight() >= 600 && (wanted.weight() - actual.weight()) >= 200;
    bool isFakeItalic = wanted.slant() == FontStyle::Slant::ITALIC &&
                        actual.slant() == FontStyle::Slant::UPRIGHT;
    return FontFakery(isFakeBold, isFakeItalic);
}

FakedFont FontFamily::getClosestMatch(FontStyle style) const {
    if (mVarFamilyType != VariationFamilyType::None) {
        return getVariationFamilyAdjustment(style);
    }
    int bestIndex = 0;
    Font* bestFont = mFonts[bestIndex].get();
    int bestMatch = computeMatch(bestFont->style(), style);
    for (size_t i = 1; i < mFontsCount; i++) {
        Font* font = mFonts[i].get();
        int match = computeMatch(font->style(), style);
        if (i == 0 || match < bestMatch) {
            bestFont = font;
            bestIndex = i;
            bestMatch = match;
        }
    }
    return FakedFont{mFonts[bestIndex], computeFakery(style, bestFont->style())};
}

FakedFont FontFamily::getVariationFamilyAdjustment(FontStyle style) const {
    const bool italic = style.slant() == FontStyle::Slant::ITALIC;
    switch (mVarFamilyType) {
        case VariationFamilyType::SingleFont_wghtOnly:
            return FakedFont{mFonts[0], FontFakery(false, italic, style.weight(), -1)};
        case VariationFamilyType::SingleFont_wght_ital:
            return FakedFont{mFonts[0], FontFakery(false, false, style.weight(), italic ? 1 : 0)};
        case VariationFamilyType::TwoFont_wght:
            return FakedFont{mFonts[italic ? 1 : 0], FontFakery(false, false, style.weight(), -1)};
        case VariationFamilyType::None:
            return FakedFont{mFonts[0], FontFakery()};
    }
}

void FontFamily::computeCoverage() {
    const std::shared_ptr<Font>& font = getClosestMatch(FontStyle()).font;
    HbBlob cmapTable(font->baseFont(), MakeTag('c', 'm', 'a', 'p'));
    if (cmapTable.get() == nullptr) {
        ALOGE("Could not get cmap table size!\n");
        return;
    }

    std::vector<SparseBitSet> cmapFmt14Coverage;
    mCoverage = CmapCoverage::getCoverage(cmapTable.get(), cmapTable.size(), &cmapFmt14Coverage);
    static_assert(INVALID_VS_INDEX <= std::numeric_limits<uint16_t>::max());
    // cmapFmt14Coverage maps VS index to coverage.
    // cmapFmt14Coverage's size cannot exceed INVALID_VS_INDEX.
    MINIKIN_ASSERT(cmapFmt14Coverage.size() <= INVALID_VS_INDEX,
                   "cmapFmt14Coverage's size must not exceed INVALID_VS_INDEX.");
    mCmapFmt14CoverageCount = static_cast<uint16_t>(cmapFmt14Coverage.size());
    if (mCmapFmt14CoverageCount > 0) {
        mCmapFmt14Coverage = std::make_unique<SparseBitSet[]>(mCmapFmt14CoverageCount);
        for (size_t i = 0; i < mCmapFmt14CoverageCount; i++) {
            mCmapFmt14Coverage[i] = std::move(cmapFmt14Coverage[i]);
        }
    }

    std::unordered_set<AxisTag> supportedAxesSet;
    for (size_t i = 0; i < mFontsCount; ++i) {
        const uint16_t axesCount = mFonts[i]->getSupportedAxesCount();
        if (axesCount != 0) {
            const AxisTag* axesPtr = mFonts[i]->getSupportedAxes();
            supportedAxesSet.insert(axesPtr, axesPtr + axesCount);
        }
    }
    MINIKIN_ASSERT(supportedAxesSet.size() <= std::numeric_limits<uint32_t>::max(),
                   "Number of supported axes must be less than 2^16.");
    mSupportedAxesCount = static_cast<uint16_t>(supportedAxesSet.size());
    if (mSupportedAxesCount > 0) {
        mSupportedAxes = sortedArrayFromSet(supportedAxesSet);
    }
}

bool FontFamily::hasGlyph(uint32_t codepoint, uint32_t variationSelector) const {
    if (variationSelector == 0) {
        return getCoverage().get(codepoint);
    }

    if (mCmapFmt14CoverageCount == 0) {
        return false;
    }

    const uint16_t vsIndex = getVsIndex(variationSelector);

    if (vsIndex >= mCmapFmt14CoverageCount) {
        // Even if vsIndex is INVALID_VS_INDEX, we reach here since INVALID_VS_INDEX is defined to
        // be at the maximum end of the range.
        return false;
    }

    const SparseBitSet& bitset = getCmap14Coverage(vsIndex);
    if (bitset.empty()) {
        return false;
    }

    return bitset.get(codepoint);
}

std::shared_ptr<FontFamily> FontFamily::createFamilyWithVariation(
        const std::vector<FontVariation>& variations) const {
    if (variations.empty() || mSupportedAxesCount == 0) {
        return nullptr;
    }

    bool hasSupportedAxis = false;
    for (const FontVariation& variation : variations) {
        if (std::binary_search(mSupportedAxes.get(), mSupportedAxes.get() + mSupportedAxesCount,
                               variation.axisTag)) {
            hasSupportedAxis = true;
            break;
        }
    }
    if (!hasSupportedAxis) {
        // None of variation axes are suppored by this family.
        return nullptr;
    }

    std::vector<std::shared_ptr<Font>> fonts;
    for (size_t i = 0; i < mFontsCount; i++) {
        const std::shared_ptr<Font>& font = mFonts[i];
        bool supportedVariations = false;
        if (font->getSupportedAxesCount() != 0) {
            for (const FontVariation& variation : variations) {
                if (font->isAxisSupported(variation.axisTag)) {
                    supportedVariations = true;
                    break;
                }
            }
        }
        std::shared_ptr<MinikinFont> minikinFont;
        if (supportedVariations) {
            minikinFont = font->baseTypeface()->createFontWithVariation(variations);
        }
        if (minikinFont == nullptr) {
            fonts.push_back(font);
        } else {
            fonts.push_back(Font::Builder(minikinFont).setStyle(font->style()).build());
        }
    }

    return create(mLocaleListId, mVariant, std::move(fonts), mIsCustomFallback, mIsDefaultFallback,
                  mVarFamilyType);
}

}  // namespace minikin
