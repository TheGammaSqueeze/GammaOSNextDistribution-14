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

#include <gtest/gtest.h>

#include "FontTestUtils.h"
#include "FreeTypeMinikinFontForTest.h"
#include "MinikinInternal.h"
#include "minikin/Constants.h"
#include "minikin/FontCollection.h"

namespace minikin {

// The test font has following glyphs.
// U+82A6
// U+82A6 U+FE00 (VS1)
// U+82A6 U+E0100 (VS17)
// U+82A6 U+E0101 (VS18)
// U+82A6 U+E0102 (VS19)
// U+845B
// U+845B U+FE01 (VS2)
// U+845B U+E0101 (VS18)
// U+845B U+E0102 (VS19)
// U+845B U+E0103 (VS20)
// U+537F
// U+717D U+FE02 (VS3)
// U+717D U+E0102 (VS19)
// U+717D U+E0103 (VS20)
const char kVsTestFont[] = "VariationSelectorTest-Regular.ttf";

void expectVSGlyphs(const FontCollection* fc, uint32_t codepoint, const std::set<uint32_t>& vsSet) {
    for (uint32_t vs = 0xFE00; vs <= 0xE01EF; ++vs) {
        // Move to variation selectors supplements after variation selectors.
        if (vs == 0xFF00) {
            vs = 0xE0100;
        }
        if (vsSet.find(vs) == vsSet.end()) {
            EXPECT_FALSE(fc->hasVariationSelector(codepoint, vs))
                    << "Glyph for U+" << std::hex << codepoint << " U+" << vs;
        } else {
            EXPECT_TRUE(fc->hasVariationSelector(codepoint, vs))
                    << "Glyph for U+" << std::hex << codepoint << " U+" << vs;
        }
    }
}

void expectVSGlyphsForVsTestFont(const FontCollection* fc) {
    EXPECT_FALSE(fc->hasVariationSelector(0x82A6, 0));
    expectVSGlyphs(fc, 0x82A6, std::set<uint32_t>({0xFE00, 0xFE0E, 0xE0100, 0xE0101, 0xE0102}));

    EXPECT_FALSE(fc->hasVariationSelector(0x845B, 0));
    expectVSGlyphs(fc, 0x845B, std::set<uint32_t>({0xFE01, 0xFE0E, 0xE0101, 0xE0102, 0xE0103}));

    EXPECT_FALSE(fc->hasVariationSelector(0x537F, 0));
    expectVSGlyphs(fc, 0x537F, std::set<uint32_t>({0xFE0E}));

    EXPECT_FALSE(fc->hasVariationSelector(0x717D, 0));
    expectVSGlyphs(fc, 0x717D, std::set<uint32_t>({0xFE02, 0xE0102, 0xE0103}));
}

TEST(FontCollectionTest, hasVariationSelectorTest) {
    auto fc = buildFontCollection(kVsTestFont);
    expectVSGlyphsForVsTestFont(fc.get());
}

const char kEmojiXmlFile[] = "emoji.xml";

TEST(FontCollectionTest, hasVariationSelectorTest_emoji) {
    auto collection = buildFontCollectionFromXml(kEmojiXmlFile);

    // Both text/color font have cmap format 14 subtable entry for VS15/VS16 respectively.
    EXPECT_TRUE(collection->hasVariationSelector(0x2623, 0xFE0E));
    EXPECT_TRUE(collection->hasVariationSelector(0x2623, 0xFE0F));

    // The text font has cmap format 14 subtable entry for VS15 but the color font doesn't have for
    // VS16
    EXPECT_TRUE(collection->hasVariationSelector(0x2626, 0xFE0E));
    EXPECT_FALSE(collection->hasVariationSelector(0x2626, 0xFE0F));

    // The color font has cmap format 14 subtable entry for VS16 but the text font doesn't have for
    // VS15.
    EXPECT_TRUE(collection->hasVariationSelector(0x262A, 0xFE0E));
    EXPECT_TRUE(collection->hasVariationSelector(0x262A, 0xFE0F));

    // Neither text/color font have cmap format 14 subtable entry for VS15/VS16.
    EXPECT_TRUE(collection->hasVariationSelector(0x262E, 0xFE0E));
    EXPECT_FALSE(collection->hasVariationSelector(0x262E, 0xFE0F));

    // Text font doesn't support U+1F3FD. Only the color emoji fonts has. So VS15 is not supported.
    EXPECT_FALSE(collection->hasVariationSelector(0x1F3FD, 0xFE0E));

    // Text font doesn't have U+262F U+FE0E or even its base code point U+262F.
    EXPECT_FALSE(collection->hasVariationSelector(0x262F, 0xFE0E));

    // None of the fonts support U+2229.
    EXPECT_FALSE(collection->hasVariationSelector(0x2229, 0xFE0E));
    EXPECT_FALSE(collection->hasVariationSelector(0x2229, 0xFE0F));
}

TEST(FontCollectionTest, newEmojiTest) {
    auto collection = buildFontCollectionFromXml(kEmojiXmlFile);

    // U+2695, U+2640, U+2642 are not in emoji catrgory in Unicode 9 but they are now in emoji
    // category. Should return true even if U+FE0E was appended.
    // These three emojis are only avalilable in TextEmoji.ttf but U+2695 is excluded here since it
    // is used in other tests.
    EXPECT_TRUE(collection->hasVariationSelector(0x2640, 0xFE0E));
    EXPECT_FALSE(collection->hasVariationSelector(0x2640, 0xFE0F));
    EXPECT_TRUE(collection->hasVariationSelector(0x2642, 0xFE0E));
    EXPECT_FALSE(collection->hasVariationSelector(0x2642, 0xFE0F));
}

TEST(FontCollectionTest, createWithVariations) {
    // This font has 'wdth' and 'wght' axes.
    const char kMultiAxisFont[] = "MultiAxis.ttf";
    const char kNoAxisFont[] = "Regular.ttf";

    std::shared_ptr<FontCollection> multiAxisFc = buildFontCollection(kMultiAxisFont);
    std::shared_ptr<FontCollection> noAxisFc = buildFontCollection(kNoAxisFont);

    {
        // Do not ceate new instance if none of variations are specified.
        EXPECT_EQ(nullptr,
                  multiAxisFc->createCollectionWithVariation(std::vector<FontVariation>()));
        EXPECT_EQ(nullptr, noAxisFc->createCollectionWithVariation(std::vector<FontVariation>()));
    }
    {
        // New instance should be used for supported variation.
        std::vector<FontVariation> variations = {{MakeTag('w', 'd', 't', 'h'), 1.0f}};
        std::shared_ptr<FontCollection> newFc(
                multiAxisFc->createCollectionWithVariation(variations));
        EXPECT_NE(nullptr, newFc.get());
        EXPECT_NE(multiAxisFc.get(), newFc.get());

        EXPECT_EQ(nullptr, noAxisFc->createCollectionWithVariation(variations));
    }
    {
        // New instance should be used for supported variation (multiple variations case).
        std::vector<FontVariation> variations = {{MakeTag('w', 'd', 't', 'h'), 1.0f},
                                                 {MakeTag('w', 'g', 'h', 't'), 1.0f}};
        std::shared_ptr<FontCollection> newFc(
                multiAxisFc->createCollectionWithVariation(variations));
        EXPECT_NE(nullptr, newFc.get());
        EXPECT_NE(multiAxisFc.get(), newFc.get());

        EXPECT_EQ(nullptr, noAxisFc->createCollectionWithVariation(variations));
    }
    {
        // Do not ceate new instance if none of variations are supported.
        std::vector<FontVariation> variations = {{MakeTag('Z', 'Z', 'Z', 'Z'), 1.0f}};
        EXPECT_EQ(nullptr, multiAxisFc->createCollectionWithVariation(variations));
        EXPECT_EQ(nullptr, noAxisFc->createCollectionWithVariation(variations));
    }
    {
        // At least one axis is supported, should create new instance.
        std::vector<FontVariation> variations = {{MakeTag('w', 'd', 't', 'h'), 1.0f},
                                                 {MakeTag('Z', 'Z', 'Z', 'Z'), 1.0f}};
        std::shared_ptr<FontCollection> newFc(
                multiAxisFc->createCollectionWithVariation(variations));
        EXPECT_NE(nullptr, newFc.get());
        EXPECT_NE(multiAxisFc.get(), newFc.get());

        EXPECT_EQ(nullptr, noAxisFc->createCollectionWithVariation(variations));
    }
}

TEST(FontCollectionTest, createCollectionWithFamilies) {
    auto fallback = buildFontCollectionFromXml(kEmojiXmlFile);
    std::shared_ptr<FontFamily> family = buildFontFamily(kVsTestFont);
    std::shared_ptr<FontCollection> created = fallback->createCollectionWithFamilies({family});
    ASSERT_EQ(fallback->getFamilyCount() + 1, created->getFamilyCount());
    EXPECT_EQ(family, created->getFamilyAt(0));
    for (size_t i = 0; i < fallback->getFamilyCount(); i++) {
        EXPECT_EQ(fallback->getFamilyAt(i), created->getFamilyAt(i + 1));
    }
}

std::vector<uint8_t> writeToBuffer(
        const std::vector<std::shared_ptr<FontCollection>>& collections) {
    BufferWriter fakeWriter(nullptr);
    FontCollection::writeVector(&fakeWriter, collections);
    std::vector<uint8_t> buffer(fakeWriter.size());
    BufferWriter writer(buffer.data());
    FontCollection::writeVector(&writer, collections);
    return buffer;
}

TEST(FontCollectionTest, bufferTest) {
    FreeTypeMinikinFontForTestFactory::init();
    {
        std::vector<std::shared_ptr<FontCollection>> original({buildFontCollection(kVsTestFont)});
        std::vector<uint8_t> buffer = writeToBuffer(original);
        BufferReader reader(buffer.data());
        auto copied = FontCollection::readVector(&reader);
        EXPECT_EQ(1u, copied.size());
        expectVSGlyphsForVsTestFont(copied[0].get());
        ASSERT_EQ(original[0]->getSupportedAxesCount(), copied[0]->getSupportedAxesCount());
        for (size_t i = 0; i < original[0]->getSupportedAxesCount(); i++) {
            EXPECT_EQ(original[0]->getSupportedAxisAt(i), copied[0]->getSupportedAxisAt(i));
        }
        // Id will be different.
        EXPECT_NE(original[0]->getId(), copied[0]->getId());
        std::vector<uint8_t> newBuffer = writeToBuffer(copied);
        EXPECT_EQ(buffer, newBuffer);
    }
    {
        // Test that FontFamily instances are shared.
        std::vector<std::shared_ptr<FontFamily>> families = {buildFontFamily(kVsTestFont)};
        auto fc1 = FontCollection::create(families);
        auto fc2 = FontCollection::create(families);
        std::vector<std::shared_ptr<FontCollection>> original({fc1, fc2});
        std::vector<uint8_t> buffer = writeToBuffer(original);
        BufferReader reader(buffer.data());
        auto copied = FontCollection::readVector(&reader);
        EXPECT_EQ(2u, copied.size());
        EXPECT_EQ(copied[0]->getFamilyAt(0), copied[1]->getFamilyAt(0));
        std::vector<uint8_t> newBuffer = writeToBuffer(copied);
        EXPECT_EQ(buffer, newBuffer);
    }
    {
        // Test axes.
        // This font has 'wdth' and 'wght' axes.
        const char kMultiAxisFont[] = "MultiAxis.ttf";
        std::vector<std::shared_ptr<FontCollection>> original(
                {buildFontCollection(kMultiAxisFont)});
        std::vector<uint8_t> buffer = writeToBuffer(original);
        BufferReader reader(buffer.data());
        auto copied = FontCollection::readVector(&reader);
        EXPECT_EQ(1u, copied.size());
        ASSERT_EQ(2u, copied[0]->getSupportedAxesCount());
        // mSupportedAxes must be sorted.
        EXPECT_EQ(MakeTag('w', 'd', 't', 'h'), copied[0]->getSupportedAxisAt(0));
        EXPECT_EQ(MakeTag('w', 'g', 'h', 't'), copied[0]->getSupportedAxisAt(1));
        std::vector<uint8_t> newBuffer = writeToBuffer(copied);
        EXPECT_EQ(buffer, newBuffer);
    }
}

TEST(FontCollectionTest, FamilyMatchResultBuilderTest) {
    using Builder = FontCollection::FamilyMatchResult::Builder;
    EXPECT_TRUE(Builder().empty());
    EXPECT_EQ(0u, Builder().size());
    EXPECT_EQ(1u, Builder().add(5).size());
    EXPECT_EQ(2u, Builder().add(5).add(4).size());

    // Reset
    EXPECT_TRUE(Builder().add(5).reset().empty());
    EXPECT_EQ(0u, Builder().add(5).reset().size());
}

TEST(FontCollectionTest, FamilyMatchResultTest) {
    using Builder = FontCollection::FamilyMatchResult::Builder;

    auto r = Builder().build();
    EXPECT_EQ(0u, r.size());
    EXPECT_TRUE(r.empty());

    r = Builder().add(1).build();
    EXPECT_EQ(1u, r.size());
    EXPECT_FALSE(r.empty());
    EXPECT_EQ(1u, r[0]);

    r = Builder().add(1).add(2).build();
    EXPECT_EQ(2u, r.size());
    EXPECT_FALSE(r.empty());
    EXPECT_EQ(1u, r[0]);
    EXPECT_EQ(2u, r[1]);
}

TEST(FontCollectionTest, FamilyMatchResultTest_BuilderHoldeFirst7) {
    auto b = FontCollection::FamilyMatchResult::Builder();
    for (uint8_t i = 0; i < 128; ++i) {
        b.add(i);
    }
    auto r = b.build();
    EXPECT_EQ(7u, r.size());
    EXPECT_FALSE(r.empty());
    EXPECT_EQ(0u, r[0]);
    EXPECT_EQ(1u, r[1]);
    EXPECT_EQ(2u, r[2]);
    EXPECT_EQ(3u, r[3]);
    EXPECT_EQ(4u, r[4]);
    EXPECT_EQ(5u, r[5]);
    EXPECT_EQ(6u, r[6]);
}

TEST(FontCollectionTest, FamilyMatchResultTest_iterator) {
    auto b = FontCollection::FamilyMatchResult::Builder();
    for (uint8_t i = 0; i < 7; ++i) {
        b.add(i);
    }
    auto r = b.build();
    EXPECT_EQ(7u, r.size());
    EXPECT_FALSE(r.empty());
    int i = 0;
    for (auto v : r) {
        EXPECT_EQ(i, v);
        i++;
    }
}

TEST(FontCollectionTest, FamilyMatchResultTest_intersect) {
    using Builder = FontCollection::FamilyMatchResult::Builder;

    EXPECT_EQ(Builder().add(1).add(2).add(3).build(),
              FontCollection::FamilyMatchResult::intersect(Builder().add(1).add(2).add(3).build(),
                                                           Builder().add(1).add(2).add(3).build()));

    EXPECT_EQ(Builder().build(),
              FontCollection::FamilyMatchResult::intersect(Builder().add(1).add(2).add(3).build(),
                                                           Builder().build()));

    EXPECT_EQ(Builder().build(),
              FontCollection::FamilyMatchResult::intersect(Builder().add(2).add(4).add(6).build(),
                                                           Builder().add(1).add(3).add(5).build()));

    EXPECT_EQ(Builder().add(1).add(3).build(),
              FontCollection::FamilyMatchResult::intersect(Builder().add(1).add(2).add(3).build(),
                                                           Builder().add(1).add(3).add(5).build()));
}

}  // namespace minikin
