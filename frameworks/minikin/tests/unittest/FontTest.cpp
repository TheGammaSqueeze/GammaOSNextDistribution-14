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

#include <gtest/gtest.h>
#include <minikin/Constants.h>

#include "BufferUtils.h"
#include "FontTestUtils.h"
#include "FreeTypeMinikinFontForTest.h"
#include "minikin/Font.h"

namespace minikin {

namespace {

size_t getHeapSize() {
    struct mallinfo info = mallinfo();
    return info.uordblks;
}

}  // namespace

TEST(FontTest, BufferTest) {
    FreeTypeMinikinFontForTestFactory::init();
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(getTestFontPath("Ascii.ttf"));
    std::shared_ptr<Font> original = Font::Builder(minikinFont).build();
    std::vector<uint8_t> buffer = writeToBuffer<Font>(*original);

    BufferReader reader(buffer.data());
    Font font(&reader);
    EXPECT_EQ(minikinFont->GetFontPath(), font.baseTypeface()->GetFontPath());
    EXPECT_EQ(original->style(), font.style());
    EXPECT_EQ(original->getLocaleListId(), font.getLocaleListId());
    // baseFont() should return the same non-null instance when called twice.
    const auto& baseFont = font.baseFont();
    EXPECT_NE(nullptr, baseFont);
    EXPECT_EQ(baseFont, font.baseFont());
    // baseTypeface() should return the same non-null instance when called twice.
    const auto& typeface = font.baseTypeface();
    EXPECT_NE(nullptr, typeface);
    EXPECT_EQ(typeface, font.baseTypeface());
    std::vector<uint8_t> newBuffer = writeToBuffer<Font>(font);
    EXPECT_EQ(buffer, newBuffer);
}

TEST(FontTest, MoveConstructorTest) {
    FreeTypeMinikinFontForTestFactory::init();
    // Note: by definition, only BufferReader-based Font can be moved.
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(getTestFontPath("Ascii.ttf"));
    std::shared_ptr<Font> original = Font::Builder(minikinFont).build();
    std::vector<uint8_t> buffer = writeToBuffer<Font>(*original);

    size_t baseHeapSize = getHeapSize();
    {
        BufferReader reader(buffer.data());
        Font moveFrom(&reader);
        Font moveTo(std::move(moveFrom));
        EXPECT_EQ(nullptr, moveFrom.mExternalRefsHolder.load());
        EXPECT_EQ(nullptr, moveTo.mExternalRefsHolder.load());
    }
    EXPECT_EQ(baseHeapSize, getHeapSize());
    {
        BufferReader reader(buffer.data());
        Font moveFrom(&reader);
        std::shared_ptr<MinikinFont> typeface = moveFrom.baseTypeface();
        Font moveTo(std::move(moveFrom));
        EXPECT_EQ(nullptr, moveFrom.mExternalRefsHolder.load());
        EXPECT_EQ(typeface, moveTo.baseTypeface());
    }
    EXPECT_EQ(baseHeapSize, getHeapSize());
}

TEST(FontTest, MoveAssignmentTest) {
    FreeTypeMinikinFontForTestFactory::init();
    // Note: by definition, only BufferReader-based Font can be moved.
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(getTestFontPath("Ascii.ttf"));
    std::shared_ptr<Font> original = Font::Builder(minikinFont).build();
    std::vector<uint8_t> buffer = writeToBuffer<Font>(*original);

    size_t baseHeapSize = getHeapSize();
    {
        // mExternalRefsHolder: null -> null
        BufferReader reader(buffer.data());
        Font moveFrom(&reader);
        BufferReader reader2(buffer.data());
        Font moveTo(&reader2);
        moveTo = std::move(moveFrom);
        EXPECT_EQ(nullptr, moveFrom.mExternalRefsHolder.load());
        EXPECT_EQ(nullptr, moveTo.mExternalRefsHolder.load());
    }
    EXPECT_EQ(baseHeapSize, getHeapSize());
    {
        // mExternalRefsHolder: non-null -> null
        BufferReader reader(buffer.data());
        Font moveFrom(&reader);
        std::shared_ptr<MinikinFont> typeface = moveFrom.baseTypeface();
        BufferReader reader2(buffer.data());
        Font moveTo(&reader2);
        moveTo = std::move(moveFrom);
        EXPECT_EQ(nullptr, moveFrom.mExternalRefsHolder.load());
        EXPECT_EQ(typeface, moveTo.baseTypeface());
    }
    EXPECT_EQ(baseHeapSize, getHeapSize());
    {
        // mExternalRefsHolder: null -> non-null
        BufferReader reader(buffer.data());
        Font moveFrom(&reader);
        BufferReader reader2(buffer.data());
        Font moveTo(&reader2);
        moveTo.baseTypeface();
        moveTo = std::move(moveFrom);
        EXPECT_EQ(nullptr, moveFrom.mExternalRefsHolder.load());
        EXPECT_EQ(nullptr, moveTo.mExternalRefsHolder.load());
    }
    EXPECT_EQ(baseHeapSize, getHeapSize());
    {
        // mExternalRefsHolder: non-null -> non-null
        BufferReader reader(buffer.data());
        Font moveFrom(&reader);
        std::shared_ptr<MinikinFont> typeface = moveFrom.baseTypeface();
        BufferReader reader2(buffer.data());
        Font moveTo(&reader2);
        moveTo.baseTypeface();
        moveTo = std::move(moveFrom);
        EXPECT_EQ(nullptr, moveFrom.mExternalRefsHolder.load());
        EXPECT_EQ(typeface, moveTo.baseTypeface());
    }
    EXPECT_EQ(baseHeapSize, getHeapSize());
}

TEST(FontTest, getAdjustedFontTest) {
    FreeTypeMinikinFontForTestFactory::init();
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(
            getTestFontPath("WeightEqualsEmVariableFont.ttf"));
    std::shared_ptr<Font> font = Font::Builder(minikinFont).build();

    {
        auto hbFont = font->getAdjustedFont(-1, -1);
        EXPECT_EQ(hbFont.get(), font->baseFont().get());
    }
    {
        // Set correct wight axis value.
        auto hbFont = font->getAdjustedFont(400, -1);
        EXPECT_EQ(hb_font_get_parent(hbFont.get()), font->baseFont().get());
        EXPECT_NE(hbFont.get(), font->baseFont().get());
        unsigned int length;
        const float* coords = hb_font_get_var_coords_design(hbFont.get(), &length);
        ASSERT_EQ(2u, length);  // The test font has 'wght', 'ital' axes in this order
        EXPECT_EQ(400, coords[0]);
        EXPECT_EQ(0, coords[1]);
    }
    {
        // Override existing wght axis.
        std::shared_ptr<Font> newFont = Font::Builder(font->getAdjustedTypeface(700, -1)).build();
        auto hbFont = newFont->getAdjustedFont(500, -1);
        EXPECT_EQ(hb_font_get_parent(hbFont.get()), newFont->baseFont().get());
        EXPECT_NE(hbFont.get(), newFont->baseFont().get());
        unsigned int length;
        const float* coords = hb_font_get_var_coords_design(hbFont.get(), &length);
        ASSERT_EQ(2u, length);  // The test font has 'wght', 'ital' axes in this order
        EXPECT_EQ(500, coords[0]);
        EXPECT_EQ(0, coords[1]);
    }
    {
        // Set correct wight axis value.
        auto hbFont = font->getAdjustedFont(-1, 1);
        EXPECT_EQ(hb_font_get_parent(hbFont.get()), font->baseFont().get());
        EXPECT_NE(hbFont.get(), font->baseFont().get());
        unsigned int length;
        const float* coords = hb_font_get_var_coords_design(hbFont.get(), &length);
        ASSERT_EQ(2u, length);      // The test font has 'wght', 'ital' axes in this order
        EXPECT_EQ(400, coords[0]);  // 400 is a default value of `wght` axis
        EXPECT_EQ(1, coords[1]);
    }
    {
        // Override existing wght axis.
        std::shared_ptr<Font> newFont = Font::Builder(font->getAdjustedTypeface(-1, 0)).build();
        auto hbFont = newFont->getAdjustedFont(-1, 1);
        EXPECT_EQ(hb_font_get_parent(hbFont.get()), newFont->baseFont().get());
        EXPECT_NE(hbFont.get(), newFont->baseFont().get());
        unsigned int length;
        const float* coords = hb_font_get_var_coords_design(hbFont.get(), &length);
        ASSERT_EQ(2u, length);      // The test font has 'wght', 'ital' axes in this order
        EXPECT_EQ(400, coords[0]);  // 400 is a default value of `wght` axis
        EXPECT_EQ(1, coords[1]);
    }
    {
        // Set correct wight axis value.
        auto hbFont = font->getAdjustedFont(500, 1);
        EXPECT_EQ(hb_font_get_parent(hbFont.get()), font->baseFont().get());
        EXPECT_NE(hbFont.get(), font->baseFont().get());
        unsigned int length;
        const float* coords = hb_font_get_var_coords_design(hbFont.get(), &length);
        ASSERT_EQ(2u, length);  // The test font has 'wght', 'ital' axes in this order
        EXPECT_EQ(500, coords[0]);
        EXPECT_EQ(1, coords[1]);
    }
    {
        // Override existing wght axis.
        std::shared_ptr<Font> newFont = Font::Builder(font->getAdjustedTypeface(500, 1)).build();
        auto hbFont = newFont->getAdjustedFont(700, 0);
        EXPECT_EQ(hb_font_get_parent(hbFont.get()), newFont->baseFont().get());
        EXPECT_NE(hbFont.get(), newFont->baseFont().get());
        unsigned int length;
        const float* coords = hb_font_get_var_coords_design(hbFont.get(), &length);
        ASSERT_EQ(2u, length);  // The test font has 'wght', 'ital' axes in this order
        EXPECT_EQ(700, coords[0]);
        EXPECT_EQ(0, coords[1]);
    }
}

TEST(FontTest, getAdjustedTypefaceTest) {
    FreeTypeMinikinFontForTestFactory::init();
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(
            getTestFontPath("WeightEqualsEmVariableFont.ttf"));
    std::shared_ptr<Font> font = Font::Builder(minikinFont).build();

    {
        auto minikinFontBase = font->getAdjustedTypeface(-1, -1);
        EXPECT_EQ(minikinFontBase.get(), font->baseTypeface().get());
    }
    {
        // Set correct wght axis value.
        auto minikinFontBase = font->getAdjustedTypeface(400, -1);
        EXPECT_NE(minikinFontBase.get(), font->baseTypeface().get());
        auto axes = minikinFontBase->GetAxes();
        ASSERT_EQ(1u, axes.size());
        EXPECT_EQ(TAG_wght, axes[0].axisTag);
        EXPECT_EQ(400, axes[0].value);
    }
    {
        // Override existing wght axis.
        std::shared_ptr<Font> newFont = Font::Builder(font->getAdjustedTypeface(700, -1)).build();
        auto minikinFontBase = newFont->getAdjustedTypeface(500, -1);
        EXPECT_NE(minikinFontBase.get(), font->baseTypeface().get());
        auto axes = minikinFontBase->GetAxes();
        ASSERT_EQ(1u, axes.size());
        EXPECT_EQ(TAG_wght, axes[0].axisTag);
        EXPECT_EQ(500, axes[0].value);
    }
    {
        // Set correct wght axis value.
        auto minikinFontBase = font->getAdjustedTypeface(-1, 1);
        EXPECT_NE(minikinFontBase.get(), font->baseTypeface().get());
        auto axes = minikinFontBase->GetAxes();
        ASSERT_EQ(1u, axes.size());
        EXPECT_EQ(TAG_ital, axes[0].axisTag);
        EXPECT_EQ(1, axes[0].value);
    }
    {
        // Override existing wght axis.
        std::shared_ptr<Font> newFont = Font::Builder(font->getAdjustedTypeface(-1, 1)).build();
        auto minikinFontBase = newFont->getAdjustedTypeface(-1, 0);
        EXPECT_NE(minikinFontBase.get(), font->baseTypeface().get());
        auto axes = minikinFontBase->GetAxes();
        ASSERT_EQ(1u, axes.size());
        EXPECT_EQ(TAG_ital, axes[0].axisTag);
        EXPECT_EQ(0, axes[0].value);
    }
    {
        // Set correct ital axis value.
        auto minikinFontBase = font->getAdjustedTypeface(400, 1);
        EXPECT_NE(minikinFontBase.get(), font->baseTypeface().get());
        auto axes = minikinFontBase->GetAxes();
        ASSERT_EQ(2u, axes.size());
        EXPECT_EQ(TAG_wght, axes[0].axisTag);
        EXPECT_EQ(TAG_ital, axes[1].axisTag);
        EXPECT_EQ(400, axes[0].value);
        EXPECT_EQ(1, axes[1].value);
    }
    {
        // Override existing ital axis.
        std::shared_ptr<Font> newFont = Font::Builder(font->getAdjustedTypeface(500, 0)).build();
        auto minikinFontBase = newFont->getAdjustedTypeface(700, 1);
        EXPECT_NE(minikinFontBase.get(), font->baseTypeface().get());
        auto axes = minikinFontBase->GetAxes();
        ASSERT_EQ(2u, axes.size());
        EXPECT_EQ(TAG_wght, axes[0].axisTag);
        EXPECT_EQ(TAG_ital, axes[1].axisTag);
        EXPECT_EQ(700, axes[0].value);
        EXPECT_EQ(1, axes[1].value);
    }
}

TEST(FontTest, ChildLazyCreationTest) {
    FreeTypeMinikinFontForTestFactory::init();
    // Note: by definition, only BufferReader-based Font can be moved.
    auto minikinFont =
            std::make_shared<FreeTypeMinikinFontForTest>(getTestFontPath("MultiAxis.ttf"));
    std::shared_ptr<Font> original = Font::Builder(minikinFont).build();

    // The original font doesn't axes settings.
    EXPECT_TRUE(original->baseTypeface()->GetAxes().empty());

    std::shared_ptr<Font> overridden = std::make_shared<Font>(
            original, std::vector<FontVariation>{FontVariation(MakeTag('w', 'g', 'h', 't'), 0)});

    EXPECT_EQ(1u, overridden->baseTypeface()->GetAxes().size());
    EXPECT_EQ(MakeTag('w', 'g', 'h', 't'), overridden->baseTypeface()->GetAxes()[0].axisTag);
}

}  // namespace minikin
