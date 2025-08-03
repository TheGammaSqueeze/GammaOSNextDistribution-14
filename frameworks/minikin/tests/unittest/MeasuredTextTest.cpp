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

#include "minikin/MeasuredText.h"

#include <gtest/gtest.h>

#include "minikin/LineBreaker.h"
#include "minikin/Measurement.h"

#include "FontTestUtils.h"
#include "UnicodeUtils.h"

namespace minikin {

constexpr float CHAR_WIDTH = 10.0;  // Mock implementation always returns 10.0 for advance.

TEST(MeasuredTextTest, RunTests) {
    constexpr uint32_t CHAR_COUNT = 6;
    constexpr float REPLACEMENT_WIDTH = 20.0f;
    auto font = buildFontCollection("Ascii.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;

    MinikinPaint paint1(font);
    paint1.size = 10.0f;  // make 1em = 10px
    builder.addStyleRun(0, 2, std::move(paint1), lbStyle, lbWordStyle, true /* can hyphenate */,
                        false /* is RTL */);
    builder.addReplacementRun(2, 4, REPLACEMENT_WIDTH, 0 /* locale list id */);
    MinikinPaint paint2(font);
    paint2.size = 10.0f;  // make 1em = 10px
    builder.addStyleRun(4, 6, std::move(paint2), lbStyle, lbWordStyle, true /* can hyphenate */,
                        false /* is RTL */);

    std::vector<uint16_t> text(CHAR_COUNT, 'a');

    std::unique_ptr<MeasuredText> measuredText = builder.build(
            text, true /* compute hyphenation */, false /* compute full layout */,
            false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);

    ASSERT_TRUE(measuredText);

    // ReplacementRun assigns all width to the first character and leave zeros others.
    std::vector<float> expectedWidths = {CHAR_WIDTH, CHAR_WIDTH, REPLACEMENT_WIDTH,
                                         0,          CHAR_WIDTH, CHAR_WIDTH};

    EXPECT_EQ(expectedWidths, measuredText->widths);
}

TEST(MeasuredTextTest, getBoundsTest) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    EXPECT_EQ(MinikinRect(0.0f, 0.0f, 0.0f, 0.0f), mt->getBounds(text, Range(0, 0)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), mt->getBounds(text, Range(0, 1)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 20.0f, 0.0f), mt->getBounds(text, Range(0, 2)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), mt->getBounds(text, Range(1, 2)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 130.0f, 0.0f), mt->getBounds(text, Range(0, text.size())));
}

TEST(MeasuredTextTest, getBoundsTest_LTR) {
    auto text = utf8ToUtf16("\u0028");  // U+0028 has 1em in LTR, 3em in RTL.
    auto font = buildFontCollection("Bbox.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), mt->getBounds(text, Range(0, 1)));
}

TEST(MeasuredTextTest, getBoundsTest_RTL) {
    auto text = utf8ToUtf16("\u0028");  // U+0028 has 1em in LTR, 3em in RTL.
    auto font = buildFontCollection("Bbox.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, true /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 30.0f, 0.0f), mt->getBounds(text, Range(0, 2)));
}

TEST(MeasuredTextTest, getBoundsTest_multiStyle) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    uint32_t helloLength = 7;  // length of "Hello, "
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, helloLength, std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    MinikinPaint paint2(font);
    paint2.size = 20.0f;
    builder.addStyleRun(helloLength, text.size(), std::move(paint2), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    EXPECT_EQ(MinikinRect(0.0f, 0.0f, 0.0f, 0.0f), mt->getBounds(text, Range(0, 0)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), mt->getBounds(text, Range(0, 1)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 20.0f, 0.0f), mt->getBounds(text, Range(0, 2)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), mt->getBounds(text, Range(1, 2)));
    EXPECT_EQ(MinikinRect(0.0f, 0.0f, 0.0f, 0.0f), mt->getBounds(text, Range(7, 7)));
    EXPECT_EQ(MinikinRect(0.0f, 20.0f, 20.0f, 0.0f), mt->getBounds(text, Range(7, 8)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 30.0f, 0.0f), mt->getBounds(text, Range(6, 8)));
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 190.0f, 0.0f), mt->getBounds(text, Range(0, text.size())));
}

TEST(MeasuredTextTest, getExtentTest) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kernign */,
                            nullptr /* no hint */);

    EXPECT_EQ(MinikinExtent(0.0f, 0.0f), mt->getExtent(text, Range(0, 0)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(0, 1)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(0, 2)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(1, 2)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(0, text.size())));
}

TEST(MeasuredTextTest, getExtentTest_multiStyle) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    uint32_t helloLength = 7;  // length of "Hello, "
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, helloLength, std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    MinikinPaint paint2(font);
    paint2.size = 20.0f;
    builder.addStyleRun(helloLength, text.size(), std::move(paint2), 0 /* no line break */,
                        0 /* no line break word style */, true /* can hyphenate */,
                        false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    EXPECT_EQ(MinikinExtent(0.0f, 0.0f), mt->getExtent(text, Range(0, 0)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(0, 1)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(0, 2)));
    EXPECT_EQ(MinikinExtent(-80.0f, 20.0f), mt->getExtent(text, Range(1, 2)));
    EXPECT_EQ(MinikinExtent(0.0f, 0.0f), mt->getExtent(text, Range(7, 7)));
    EXPECT_EQ(MinikinExtent(-160.0f, 40.0f), mt->getExtent(text, Range(7, 8)));
    EXPECT_EQ(MinikinExtent(-160.0f, 40.0f), mt->getExtent(text, Range(6, 8)));
    EXPECT_EQ(MinikinExtent(-160.0f, 40.0f), mt->getExtent(text, Range(0, text.size())));
}

TEST(MeasuredTextTest, buildLayoutTest) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    Range fullContext(0, text.size());
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    MinikinRect rect;
    MinikinPaint samePaint(font);
    samePaint.size = 10.0f;

    Layout layout = mt->buildLayout(text, Range(0, 0), fullContext, samePaint,
                                    StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    EXPECT_EQ(0u, layout.nGlyphs());

    layout = mt->buildLayout(text, Range(0, 1), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(10.0f, layout.getAdvance());
    EXPECT_EQ(10.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(0, 1), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, 2), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(2u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0) && layout.getFont(0) == layout.getFont(1));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(10.0f, layout.getX(1));
    EXPECT_EQ(0.0f, layout.getY(1));
    EXPECT_EQ(20.0f, layout.getAdvance());
    EXPECT_EQ(10.0f, layout.getCharAdvance(0));
    EXPECT_EQ(10.0f, layout.getCharAdvance(1));
    EXPECT_EQ(2u, layout.getAdvances().size());
    getBounds(text, Range(0, 2), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 20.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(1, 2), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(10.0f, layout.getAdvance());
    EXPECT_EQ(10.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(1, 2), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, text.size()), fullContext, samePaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(text.size(), layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    for (uint32_t i = 0; i < text.size(); ++i) {
        EXPECT_EQ(layout.getFont(0), layout.getFont(i)) << i;
        EXPECT_EQ(10.0f * i, layout.getX(i)) << i;
        EXPECT_EQ(0.0f, layout.getY(i)) << i;
        EXPECT_EQ(10.0f, layout.getCharAdvance(i)) << i;
    }
    EXPECT_EQ(130.0f, layout.getAdvance());
    EXPECT_EQ(text.size(), layout.getAdvances().size());
    getBounds(text, Range(0, text.size()), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 130.0f, 0.0f), rect);
}

TEST(MeasuredTextTest, buildLayoutTest_multiStyle) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    uint32_t helloLength = 7;  // length of "Hello, "
    Range fullContext(0, text.size());
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, helloLength, std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    MinikinPaint paint2(font);
    paint2.size = 20.0f;
    builder.addStyleRun(helloLength, text.size(), std::move(paint2), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    MinikinRect rect;
    MinikinPaint samePaint(font);
    samePaint.size = 10.0f;

    Layout layout = mt->buildLayout(text, Range(0, 0), fullContext, samePaint,
                                    StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    EXPECT_EQ(0u, layout.nGlyphs());

    layout = mt->buildLayout(text, Range(0, 1), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(10.0f, layout.getAdvance());
    EXPECT_EQ(10.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(0, 1), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, 2), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(2u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0) && layout.getFont(0) == layout.getFont(1));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(10.0f, layout.getX(1));
    EXPECT_EQ(0.0f, layout.getY(1));
    EXPECT_EQ(20.0f, layout.getAdvance());
    EXPECT_EQ(10.0f, layout.getCharAdvance(0));
    EXPECT_EQ(10.0f, layout.getCharAdvance(1));
    EXPECT_EQ(2u, layout.getAdvances().size());
    getBounds(text, Range(0, 2), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 20.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(1, 2), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(10.0f, layout.getAdvance());
    EXPECT_EQ(10.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(1, 2), Bidi::LTR, samePaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 10.0f, 10.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(7, 7), fullContext, samePaint, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    EXPECT_EQ(0u, layout.nGlyphs());

    MinikinPaint samePaint2(font);
    samePaint2.size = 20.0f;
    layout = mt->buildLayout(text, Range(7, 8), fullContext, samePaint2, StartHyphenEdit::NO_EDIT,
                             EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(20.0f, layout.getAdvance());
    EXPECT_EQ(20.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(7, 8), Bidi::LTR, samePaint2, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 20.0f, 20.0f, 0.0f), rect);
}

TEST(MeasuredTextTest, buildLayoutTest_differentPaint) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    Range fullContext(0, text.size());
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    MinikinRect rect;
    MinikinPaint differentPaint(font);
    differentPaint.size = 20.0f;

    Layout layout = mt->buildLayout(text, Range(0, 0), fullContext, differentPaint,
                                    StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    EXPECT_EQ(0u, layout.nGlyphs());

    layout = mt->buildLayout(text, Range(0, 1), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(20.0f, layout.getAdvance());
    EXPECT_EQ(20.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(0, 1), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 20.0f, 20.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, 2), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(2u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0) && layout.getFont(0) == layout.getFont(1));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(20.0f, layout.getX(1));
    EXPECT_EQ(0.0f, layout.getY(1));
    EXPECT_EQ(40.0f, layout.getAdvance());
    EXPECT_EQ(20.0f, layout.getCharAdvance(0));
    EXPECT_EQ(20.0f, layout.getCharAdvance(1));
    EXPECT_EQ(2u, layout.getAdvances().size());
    getBounds(text, Range(0, 2), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 20.0f, 40.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(1, 2), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(20.0f, layout.getAdvance());
    EXPECT_EQ(20.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(1, 2), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 20.0f, 20.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, text.size()), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(text.size(), layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    for (uint32_t i = 0; i < text.size(); ++i) {
        EXPECT_EQ(layout.getFont(0), layout.getFont(i)) << i;
        EXPECT_EQ(20.0f * i, layout.getX(i)) << i;
        EXPECT_EQ(0.0f, layout.getY(i)) << i;
        EXPECT_EQ(20.0f, layout.getCharAdvance(i)) << i;
    }
    EXPECT_EQ(260.0f, layout.getAdvance());
    EXPECT_EQ(text.size(), layout.getAdvances().size());
    getBounds(text, Range(0, text.size()), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 20.0f, 260.0f, 0.0f), rect);
}

TEST(MeasuredTextTest, buildLayoutTest_multiStyle_differentPaint) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    uint32_t helloLength = 7;  // length of "Hello, "
    Range fullContext(0, text.size());
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, helloLength, std::move(paint), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    MinikinPaint paint2(font);
    paint2.size = 20.0f;
    builder.addStyleRun(helloLength, text.size(), std::move(paint2), lbStyle, lbWordStyle,
                        true /* can hyphenate */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            false /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);

    MinikinRect rect;
    MinikinPaint differentPaint(font);
    differentPaint.size = 30.0f;

    Layout layout = mt->buildLayout(text, Range(0, 0), fullContext, differentPaint,
                                    StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    EXPECT_EQ(0u, layout.nGlyphs());

    layout = mt->buildLayout(text, Range(0, 1), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(30.0f, layout.getAdvance());
    EXPECT_EQ(30.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(0, 1), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 30.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, 2), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(2u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0) && layout.getFont(0) == layout.getFont(1));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(30.0f, layout.getX(1));
    EXPECT_EQ(0.0f, layout.getY(1));
    EXPECT_EQ(60.0f, layout.getAdvance());
    EXPECT_EQ(30.0f, layout.getCharAdvance(0));
    EXPECT_EQ(30.0f, layout.getCharAdvance(1));
    EXPECT_EQ(2u, layout.getAdvances().size());
    getBounds(text, Range(0, 2), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 60.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(1, 2), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(30.0f, layout.getAdvance());
    EXPECT_EQ(30.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(1, 2), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 30.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(7, 7), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    EXPECT_EQ(0u, layout.nGlyphs());

    layout = mt->buildLayout(text, Range(7, 8), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(1u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(30.0f, layout.getAdvance());
    EXPECT_EQ(30.0f, layout.getCharAdvance(0));
    EXPECT_EQ(1u, layout.getAdvances().size());
    getBounds(text, Range(7, 8), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 30.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(6, 8), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(2u, layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0) && layout.getFont(0) == layout.getFont(1));
    EXPECT_EQ(0.0f, layout.getX(0));
    EXPECT_EQ(0.0f, layout.getY(0));
    EXPECT_EQ(30.0f, layout.getX(1));
    EXPECT_EQ(0.0f, layout.getY(1));
    EXPECT_EQ(60.0f, layout.getAdvance());
    EXPECT_EQ(30.0f, layout.getCharAdvance(0));
    EXPECT_EQ(30.0f, layout.getCharAdvance(1));
    EXPECT_EQ(2u, layout.getAdvances().size());
    getBounds(text, Range(6, 8), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 60.0f, 0.0f), rect);

    layout = mt->buildLayout(text, Range(0, text.size()), fullContext, differentPaint,
                             StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT);
    ASSERT_EQ(text.size(), layout.nGlyphs());
    EXPECT_TRUE(layout.getFont(0));
    for (uint32_t i = 0; i < text.size(); ++i) {
        EXPECT_EQ(layout.getFont(0), layout.getFont(i)) << i;
        EXPECT_EQ(30.0f * i, layout.getX(i)) << i;
        EXPECT_EQ(0.0f, layout.getY(i)) << i;
        EXPECT_EQ(30.0f, layout.getCharAdvance(i)) << i;
    }
    EXPECT_EQ(390.0f, layout.getAdvance());
    EXPECT_EQ(text.size(), layout.getAdvances().size());
    getBounds(text, Range(0, text.size()), Bidi::LTR, differentPaint, StartHyphenEdit::NO_EDIT,
              EndHyphenEdit::NO_EDIT, &rect);
    EXPECT_EQ(MinikinRect(0.0f, 30.0f, 390.0f, 0.0f), rect);
}

TEST(MeasuredTextTest, testLineBreakStyle_from_builder) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    int lbStyle = (int)LineBreakStyle::Loose;           // loose
    int lbWordStyle = (int)LineBreakWordStyle::Phrase;  // phrase

    MeasuredTextBuilder looseStyleBuilder;
    MinikinPaint paint(font);
    looseStyleBuilder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                                  true /* can hyphenate */, false);
    auto mt = looseStyleBuilder.build(text, true /* hyphenation */, true /* full layout */,
                                      false /* computeBounds */, false /* ignore kerning */,
                                      nullptr /* no hint */);

    EXPECT_EQ((size_t)1, mt->runs.size());
    EXPECT_EQ(LineBreakStyle::Loose, mt->runs[0]->lineBreakStyle());
    EXPECT_EQ(LineBreakWordStyle::Phrase, mt->runs[0]->lineBreakWordStyle());

    lbStyle = (int)LineBreakStyle::Normal;  // normal
    MeasuredTextBuilder normalStyleBuilder;
    MinikinPaint normalStylePaint(font);
    normalStyleBuilder.addStyleRun(0, text.size(), std::move(normalStylePaint), lbStyle,
                                   lbWordStyle, true /* can hyphenate */, false);
    mt = normalStyleBuilder.build(text, true /* hyphenation */, true /* full layout */,
                                  false /* computeBounds */, false /* ignore kerning */,
                                  nullptr /* no hint */);

    EXPECT_EQ((size_t)1, mt->runs.size());
    EXPECT_EQ(LineBreakStyle::Normal, mt->runs[0]->lineBreakStyle());
    EXPECT_EQ(LineBreakWordStyle::Phrase, mt->runs[0]->lineBreakWordStyle());

    lbStyle = (int)LineBreakStyle::Strict;        // strict
    lbWordStyle = (int)LineBreakWordStyle::None;  // no word style
    MeasuredTextBuilder strictStyleBuilder;
    MinikinPaint strictStylePaint(font);
    strictStyleBuilder.addStyleRun(0, text.size(), std::move(strictStylePaint), lbStyle,
                                   lbWordStyle, true /* can hyphenate */, false);
    mt = strictStyleBuilder.build(text, true /* hyphenation */, true /* full layout */,
                                  false /* computeBounds */, false /* ignore kerning */,
                                  nullptr /* no hint */);

    EXPECT_EQ((size_t)1, mt->runs.size());
    EXPECT_EQ(LineBreakStyle::Strict, mt->runs[0]->lineBreakStyle());
    EXPECT_EQ(LineBreakWordStyle::None, mt->runs[0]->lineBreakWordStyle());
}

TEST(MeasuredTextTest, testLineBreakStyle_from_run) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    int lbStyle = (int)LineBreakStyle::Strict;
    int lbWordStyle = (int)LineBreakWordStyle::Phrase;
    Range range(0, text.size());
    MinikinPaint paint(font);

    StyleRun styleRun(range, std::move(paint), lbStyle, lbWordStyle, true /* can hyphenate */,
                      false /* isRtl */);
    EXPECT_EQ(LineBreakStyle::Strict, styleRun.lineBreakStyle());
    EXPECT_EQ(LineBreakWordStyle::Phrase, styleRun.lineBreakWordStyle());

    ReplacementRun replacementRun(range, 10.0f /* width */, 0 /* locale list id */);
    EXPECT_EQ(LineBreakStyle::None, replacementRun.lineBreakStyle());
    EXPECT_EQ(LineBreakWordStyle::None, replacementRun.lineBreakWordStyle());
}

TEST(MeasuredTextTest, hasOverhang_false) {
    auto text = utf8ToUtf16("Hello, World!");
    auto font = buildFontCollection("Ascii.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* hyphenation */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            true /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);
    EXPECT_FALSE(mt->hasOverhang(Range(0, text.size())));
}

TEST(MeasuredTextTest, hasOverhang_true) {
    auto text = utf8ToUtf16("b");
    auto font = buildFontCollection("OvershootTest.ttf");
    int lbStyle = (int)LineBreakStyle::None;
    int lbWordStyle = (int)LineBreakWordStyle::None;

    MeasuredTextBuilder builder;
    MinikinPaint paint(font);
    paint.size = 10.0f;
    builder.addStyleRun(0, text.size(), std::move(paint), lbStyle, lbWordStyle,
                        true /* hyphenation */, false /* is RTL */);
    auto mt = builder.build(text, true /* hyphenation */, true /* full layout */,
                            true /* computeBounds */, false /* ignore kerning */,
                            nullptr /* no hint */);
    EXPECT_TRUE(mt->hasOverhang(Range(0, text.size())));
}

}  // namespace minikin
