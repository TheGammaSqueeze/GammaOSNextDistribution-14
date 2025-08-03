/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <com_android_text_flags.h>
#include <flag_macros.h>
#include <gtest/gtest.h>

#include "FontTestUtils.h"
#include "minikin/FontFeature.h"
#include "minikin/MinikinPaint.h"

namespace minikin {

namespace {

constexpr hb_tag_t chws_tag = HB_TAG('c', 'h', 'w', 's');
constexpr hb_tag_t clig_tag = HB_TAG('c', 'l', 'i', 'g');
constexpr hb_tag_t halt_tag = HB_TAG('h', 'a', 'l', 't');
constexpr hb_tag_t liga_tag = HB_TAG('l', 'i', 'g', 'a');
constexpr hb_tag_t palt_tag = HB_TAG('p', 'a', 'l', 't');
constexpr hb_tag_t ruby_tag = HB_TAG('r', 'u', 'b', 'y');

bool compareFeatureTag(hb_feature_t l, hb_feature_t r) {
    return l.tag < r.tag;
}

}  // namespace

class DefaultFontFeatureTest : public testing::Test {
protected:
    std::shared_ptr<FontCollection> font;

    virtual void SetUp() override { font = buildFontCollection("Ascii.ttf"); }
};

TEST_F(DefaultFontFeatureTest, default) {
    auto f = cleanAndAddDefaultFontFeatures(MinikinPaint(font));
    EXPECT_EQ(1u, f.size());
    EXPECT_EQ(chws_tag, f[0].tag);
    EXPECT_TRUE(f[0].value);
}

TEST_F(DefaultFontFeatureTest, disable) {
    auto paint = MinikinPaint(font);
    paint.fontFeatureSettings = FontFeature::parse("\"chws\" off");

    auto f = cleanAndAddDefaultFontFeatures(paint);
    std::sort(f.begin(), f.end(), compareFeatureTag);

    EXPECT_EQ(1u, f.size());
    EXPECT_EQ(chws_tag, f[0].tag);
    EXPECT_FALSE(f[0].value);
}

TEST_F(DefaultFontFeatureTest, preserve) {
    auto paint = MinikinPaint(font);
    paint.fontFeatureSettings = FontFeature::parse("\"ruby\" on");

    auto f = cleanAndAddDefaultFontFeatures(paint);
    std::sort(f.begin(), f.end(), compareFeatureTag);

    EXPECT_EQ(2u, f.size());
    EXPECT_EQ(chws_tag, f[0].tag);
    EXPECT_TRUE(f[0].value);
    EXPECT_EQ(ruby_tag, f[1].tag);
    EXPECT_TRUE(f[1].value);
}

TEST_F(DefaultFontFeatureTest, large_letter_spacing) {
    auto paint = MinikinPaint(font);
    paint.letterSpacing = 1.0;  // em

    auto f = cleanAndAddDefaultFontFeatures(paint);
    std::sort(f.begin(), f.end(), compareFeatureTag);

    EXPECT_EQ(3u, f.size());
    EXPECT_EQ(chws_tag, f[0].tag);
    EXPECT_TRUE(f[0].value);
    EXPECT_EQ(clig_tag, f[1].tag);
    EXPECT_FALSE(f[1].value);
    EXPECT_EQ(liga_tag, f[2].tag);
    EXPECT_FALSE(f[2].value);
}

TEST_F(DefaultFontFeatureTest, halt_disable_chws) {
    auto paint = MinikinPaint(font);
    paint.fontFeatureSettings = FontFeature::parse("\"halt\" on");

    auto f = cleanAndAddDefaultFontFeatures(paint);
    EXPECT_EQ(1u, f.size());
    EXPECT_EQ(halt_tag, f[0].tag);
    EXPECT_TRUE(f[0].value);
}

TEST_F(DefaultFontFeatureTest, palt_disable_chws) {
    auto paint = MinikinPaint(font);
    paint.fontFeatureSettings = FontFeature::parse("\"palt\" on");

    auto f = cleanAndAddDefaultFontFeatures(paint);
    EXPECT_EQ(1u, f.size());
    EXPECT_EQ(palt_tag, f[0].tag);
    EXPECT_TRUE(f[0].value);
}

TEST_F(DefaultFontFeatureTest, halt_disable_chws_large_letter_spacing) {
    auto paint = MinikinPaint(font);
    paint.letterSpacing = 1.0;  // em
    paint.fontFeatureSettings = FontFeature::parse("\"halt\" on");

    auto f = cleanAndAddDefaultFontFeatures(paint);
    std::sort(f.begin(), f.end(), compareFeatureTag);

    EXPECT_EQ(3u, f.size());
    EXPECT_EQ(clig_tag, f[0].tag);
    EXPECT_FALSE(f[0].value);
    EXPECT_EQ(halt_tag, f[1].tag);
    EXPECT_TRUE(f[1].value);
    EXPECT_EQ(liga_tag, f[2].tag);
    EXPECT_FALSE(f[2].value);
}

TEST_F(DefaultFontFeatureTest, palt_disable_chws_large_letter_spacing) {
    auto paint = MinikinPaint(font);
    paint.letterSpacing = 1.0;  // em
    paint.fontFeatureSettings = FontFeature::parse("\"palt\" on");

    auto f = cleanAndAddDefaultFontFeatures(paint);
    std::sort(f.begin(), f.end(), compareFeatureTag);

    EXPECT_EQ(3u, f.size());
    EXPECT_EQ(clig_tag, f[0].tag);
    EXPECT_FALSE(f[0].value);
    EXPECT_EQ(liga_tag, f[1].tag);
    EXPECT_FALSE(f[1].value);
    EXPECT_EQ(palt_tag, f[2].tag);
    EXPECT_TRUE(f[2].value);
}

class FontFeatureTest : public testing::Test {
protected:
    std::shared_ptr<FontCollection> font;

    virtual void SetUp() override { font = buildFontCollection("Ascii.ttf"); }
};

TEST_F_WITH_FLAGS(FontFeatureTest, do_not_skip_cache_if_flagEnabled,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(com::android::text::flags,
                                                      letter_spacing_justification))) {
    auto paint = MinikinPaint(font);
    paint.fontFeatureSettings = FontFeature::parse("\"palt\" on");
    EXPECT_FALSE(paint.skipCache());
}

TEST_F_WITH_FLAGS(FontFeatureTest, do_not_skip_cache_if_flagDisabled,
                  REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(com::android::text::flags,
                                                       letter_spacing_justification))) {
    auto paint = MinikinPaint(font);
    paint.fontFeatureSettings = FontFeature::parse("\"palt\" on");
    EXPECT_TRUE(paint.skipCache());
}

}  // namespace minikin
