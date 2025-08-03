/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef MINIKIN_FONT_H
#define MINIKIN_FONT_H

#include <gtest/gtest_prod.h>

#include <atomic>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <unordered_set>

#include "minikin/Buffer.h"
#include "minikin/FontStyle.h"
#include "minikin/FontVariation.h"
#include "minikin/HbUtils.h"
#include "minikin/LocaleList.h"
#include "minikin/Macros.h"
#include "minikin/MinikinFont.h"

namespace minikin {

class Font;

// attributes representing transforms (fake bold, fake italic) to match styles
class FontFakery {
public:
    FontFakery() : FontFakery(false, false, -1, -1) {}
    FontFakery(bool fakeBold, bool fakeItalic) : FontFakery(fakeBold, fakeItalic, -1, -1) {}
    FontFakery(bool fakeBold, bool fakeItalic, int16_t wghtAdjustment, int8_t italAdjustment)
            : mBits(pack(fakeBold, fakeItalic, wghtAdjustment, italAdjustment)) {}

    // TODO: want to support graded fake bolding
    bool isFakeBold() { return (mBits & MASK_FAKE_BOLD) != 0; }
    bool isFakeItalic() { return (mBits & MASK_FAKE_ITALIC) != 0; }
    bool hasAdjustment() const { return hasWghtAdjustment() || hasItalAdjustment(); }
    bool hasWghtAdjustment() const { return (mBits & MASK_HAS_WGHT_ADJUSTMENT) != 0; }
    bool hasItalAdjustment() const { return (mBits & MASK_HAS_ITAL_ADJUSTMENT) != 0; }
    int16_t wghtAdjustment() const {
        if (hasWghtAdjustment()) {
            return (mBits & MASK_WGHT_ADJUSTMENT) >> WGHT_ADJUSTMENT_SHIFT;
        } else {
            return -1;
        }
    }

    int8_t italAdjustment() const {
        if (hasItalAdjustment()) {
            return (mBits & MASK_ITAL_ADJUSTMENT) != 0 ? 1 : 0;
        } else {
            return -1;
        }
    }

    uint16_t bits() const { return mBits; }

    inline bool operator==(const FontFakery& o) const { return mBits == o.mBits; }
    inline bool operator!=(const FontFakery& o) const { return !(*this == o); }

private:
    static constexpr uint16_t MASK_FAKE_BOLD = 1u;
    static constexpr uint16_t MASK_FAKE_ITALIC = 1u << 1;
    static constexpr uint16_t MASK_HAS_WGHT_ADJUSTMENT = 1u << 2;
    static constexpr uint16_t MASK_HAS_ITAL_ADJUSTMENT = 1u << 3;
    static constexpr uint16_t MASK_ITAL_ADJUSTMENT = 1u << 4;
    static constexpr uint16_t MASK_WGHT_ADJUSTMENT = 0b1111111111u << 5;
    static constexpr uint16_t WGHT_ADJUSTMENT_SHIFT = 5;

    uint16_t pack(bool isFakeBold, bool isFakeItalic, int16_t wghtAdjustment,
                  int8_t italAdjustment) {
        uint16_t bits = 0u;
        bits |= isFakeBold ? MASK_FAKE_BOLD : 0;
        bits |= isFakeItalic ? MASK_FAKE_ITALIC : 0;
        if (wghtAdjustment != -1) {
            bits |= MASK_HAS_WGHT_ADJUSTMENT;
            bits |= (static_cast<uint16_t>(wghtAdjustment) << WGHT_ADJUSTMENT_SHIFT) &
                    MASK_WGHT_ADJUSTMENT;
        }
        if (italAdjustment != -1) {
            bits |= MASK_HAS_ITAL_ADJUSTMENT;
            bits |= (italAdjustment == 1) ? MASK_ITAL_ADJUSTMENT : 0;
        }
        return bits;
    }

    const uint16_t mBits;
};

// Represents a single font file.
class Font {
public:
    class Builder {
    public:
        Builder(const std::shared_ptr<MinikinFont>& typeface) : mTypeface(typeface) {}

        // Override the font style. If not called, info from OS/2 table is used.
        Builder& setStyle(FontStyle style) {
            mWeight = style.weight();
            mSlant = style.slant();
            mIsWeightSet = mIsSlantSet = true;
            return *this;
        }

        // Override the font weight. If not called, info from OS/2 table is used.
        Builder& setWeight(uint16_t weight) {
            mWeight = weight;
            mIsWeightSet = true;
            return *this;
        }

        // Override the font slant. If not called, info from OS/2 table is used.
        Builder& setSlant(FontStyle::Slant slant) {
            mSlant = slant;
            mIsSlantSet = true;
            return *this;
        }

        Builder& setLocaleListId(uint32_t id) {
            mLocaleListId = id;
            return *this;
        }

        std::shared_ptr<Font> build();

    private:
        std::shared_ptr<MinikinFont> mTypeface;
        uint16_t mWeight = static_cast<uint16_t>(FontStyle::Weight::NORMAL);
        FontStyle::Slant mSlant = FontStyle::Slant::UPRIGHT;
        uint32_t mLocaleListId = kEmptyLocaleListId;
        bool mIsWeightSet = false;
        bool mIsSlantSet = false;
    };

    explicit Font(BufferReader* reader);
    void writeTo(BufferWriter* writer) const;

    // Create font instance with axes override.
    Font(const std::shared_ptr<Font>& parent, const std::vector<FontVariation>& axes);

    Font(Font&& o) noexcept;
    Font& operator=(Font&& o) noexcept;
    ~Font();
    // This locale list is just for API compatibility. This is not used in font selection or family
    // fallback.
    uint32_t getLocaleListId() const { return mLocaleListId; }
    inline FontStyle style() const { return mStyle; }

    const HbFontUniquePtr& baseFont() const;
    const std::shared_ptr<MinikinFont>& baseTypeface() const;

    // Returns an adjusted hb_font_t instance and MinikinFont instance.
    // Passing -1 each means do not override the current variation settings.
    HbFontUniquePtr getAdjustedFont(int wght, int ital) const;
    const std::shared_ptr<MinikinFont>& getAdjustedTypeface(int wght, int ital) const;

    BufferReader typefaceMetadataReader() const { return mTypefaceMetadataReader; }

    uint16_t getSupportedAxesCount() const { return mSupportedAxesCount; }
    const AxisTag* getSupportedAxes() const { return mSupportedAxes.get(); }
    bool isAxisSupported(uint32_t tag) const;

private:
    // ExternalRefs holds references to objects provided by external libraries.
    // Because creating these external objects is costly,
    // ExternalRefs is lazily created if Font was created by readFrom().
    class ExternalRefs {
    public:
        ExternalRefs(std::shared_ptr<MinikinFont>&& typeface, HbFontUniquePtr&& baseFont)
                : mTypeface(std::move(typeface)), mBaseFont(std::move(baseFont)) {}

        std::shared_ptr<MinikinFont> mTypeface;
        HbFontUniquePtr mBaseFont;

        const std::shared_ptr<MinikinFont>& getAdjustedTypeface(int wght, int ital) const;
        HbFontUniquePtr getAdjustedFont(int wght, int ital) const;
        mutable std::mutex mMutex;
        mutable std::map<uint16_t, std::shared_ptr<MinikinFont>> mVarTypefaceCache
                GUARDED_BY(mMutex);
        mutable std::map<uint16_t, HbFontUniquePtr> mVarFontCache GUARDED_BY(mMutex);
    };

    // Use Builder instead.
    Font(std::shared_ptr<MinikinFont>&& typeface, FontStyle style, HbFontUniquePtr&& baseFont,
         uint32_t localeListId)
            : mExternalRefsHolder(new ExternalRefs(std::move(typeface), std::move(baseFont))),
              mExternalRefsBuilder(nullptr),
              mStyle(style),
              mLocaleListId(localeListId),
              mTypefaceMetadataReader(nullptr) {
        calculateSupportedAxes();
    }

    void resetExternalRefs(ExternalRefs* refs);

    const ExternalRefs* getExternalRefs() const;
    std::vector<FontVariation> getAdjustedVariations(int wght, int ital) const;

    static HbFontUniquePtr prepareFont(const std::shared_ptr<MinikinFont>& typeface);
    static FontStyle analyzeStyle(const HbFontUniquePtr& font);

    // Lazy-initialized if created by readFrom().
    mutable std::atomic<ExternalRefs*> mExternalRefsHolder;
    std::function<ExternalRefs*()> mExternalRefsBuilder;
    FontStyle mStyle;
    uint32_t mLocaleListId;
    std::unique_ptr<AxisTag[]> mSupportedAxes;
    uint16_t mSupportedAxesCount;

    void calculateSupportedAxes();

    // Non-null if created by readFrom().
    BufferReader mTypefaceMetadataReader;

    // Stop copying.
    Font(const Font& o) = delete;
    Font& operator=(const Font& o) = delete;

    FRIEND_TEST(FontTest, MoveConstructorTest);
    FRIEND_TEST(FontTest, MoveAssignmentTest);
};

struct FakedFont {
    inline bool operator==(const FakedFont& o) const {
        return font == o.font && fakery == o.fakery;
    }
    inline bool operator!=(const FakedFont& o) const { return !(*this == o); }

    HbFontUniquePtr hbFont() const {
        return font->getAdjustedFont(fakery.wghtAdjustment(), fakery.italAdjustment());
    }

    const std::shared_ptr<MinikinFont>& typeface() const {
        return font->getAdjustedTypeface(fakery.wghtAdjustment(), fakery.italAdjustment());
    }

    // ownership is the enclosing FontCollection
    // FakedFont will be stored in the LayoutCache. It is not a good idea too keep font instance
    // even if the enclosing FontCollection, i.e. Typeface is GC-ed. The layout cache is only
    // purged when it is overflown, thus intentionally keep only reference.
    const std::shared_ptr<Font>& font;
    FontFakery fakery;
};

}  // namespace minikin

#endif  // MINIKIN_FONT_H
