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

#include <com_android_text_flags.h>
#include <flag_macros.h>
#include <gtest/gtest.h>

#include <memory>

#include "FileUtils.h"
#include "FontTestUtils.h"
#include "HyphenatorMap.h"
#include "LineBreakerTestHelper.h"
#include "LocaleListCache.h"
#include "MinikinInternal.h"
#include "OptimalLineBreaker.h"
#include "UnicodeUtils.h"
#include "WordBreaker.h"
#include "minikin/Hyphenator.h"

namespace minikin {
namespace {

using line_breaker_test_helper::ConstantRun;
using line_breaker_test_helper::LineBreakExpectation;
using line_breaker_test_helper::RectangleLineWidth;
using line_breaker_test_helper::sameLineBreak;
using line_breaker_test_helper::toString;

// The ascent/descent of Ascii.ttf with text size = 10.
constexpr float ASCENT = -80.0f;
constexpr float DESCENT = 20.0f;

// The ascent/descent of CustomExtent.ttf with text size = 10.
constexpr float CUSTOM_ASCENT = -160.0f;
constexpr float CUSTOM_DESCENT = 40.0f;

// A test string for Japanese. The meaning is that "Today is a sunny day."
// The expected line break of phrase and non-phrase cases are:
//     Phrase: | \u672C\u65E5\u306F | \u6674\u5929\u306A\u308A\u3002 |
// Non-Phrase: | \u672C | \u65E5 | \u306F | \u6674 | \u5929 | \u306A | \u308A\u3002 |
const char* JP_TEXT = "\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002";

class OptimalLineBreakerTest : public testing::Test {
public:
    OptimalLineBreakerTest() {}

    virtual ~OptimalLineBreakerTest() {}

    virtual void SetUp() override {
        mHyphenationPattern = readWholeFile("/system/usr/hyphen-data/hyph-en-us.hyb");
        Hyphenator* hyphenator = Hyphenator::loadBinary(
                mHyphenationPattern.data(), 2 /* min prefix */, 2 /* min suffix */, "en-US");
        HyphenatorMap::add("en-US", hyphenator);
        HyphenatorMap::add("pl", Hyphenator::loadBinary(nullptr, 0, 0, "pl"));
    }

    virtual void TearDown() override { HyphenatorMap::clear(); }

protected:
    LineBreakResult doLineBreak(const U16StringPiece& textBuffer, BreakStrategy strategy,
                                HyphenationFrequency frequency, const std::string& lang,
                                float lineWidth, bool ignoreKerning) {
        MeasuredTextBuilder builder;
        auto family1 = buildFontFamily("Ascii.ttf");
        auto family2 = buildFontFamily("CustomExtent.ttf");
        std::vector<std::shared_ptr<FontFamily>> families = {family1, family2};
        auto fc = FontCollection::create(families);
        MinikinPaint paint(fc);
        paint.size = 10.0f;  // Make 1em=10px
        paint.localeListId = LocaleListCache::getId(lang);
        builder.addStyleRun(0, textBuffer.size(), std::move(paint), 0, 0, true, false);
        bool computeHyphen = frequency != HyphenationFrequency::None;
        std::unique_ptr<MeasuredText> measuredText =
                builder.build(textBuffer, computeHyphen, false /* compute full layout */,
                              false /* computeBounds */, ignoreKerning, nullptr /* no hint */);
        return doLineBreak(textBuffer, *measuredText, strategy, frequency, lineWidth);
    }

    LineBreakResult doLineBreakForJapanese(const U16StringPiece& textBuffer,
                                           LineBreakWordStyle lbwStyle, const std::string& lang,
                                           float lineWidth) {
        MeasuredTextBuilder builder;
        auto family1 = buildFontFamily("Japanese.ttf");
        std::vector<std::shared_ptr<FontFamily>> families = {family1};
        auto fc = FontCollection::create(families);
        MinikinPaint paint(fc);
        paint.size = 10.0f;  // Make 1em=10px
        paint.localeListId = LocaleListCache::getId(lang);
        builder.addStyleRun(0, textBuffer.size(), std::move(paint), 0, (int)lbwStyle, true, false);
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuffer, false /* computeHyphen */, false /* compute full layout */,
                false /* computeBounds */, false /* ignoreKerning */, nullptr /* no hint */);
        return doLineBreak(textBuffer, *measuredText, BreakStrategy::HighQuality,
                           HyphenationFrequency::None, lineWidth);
    }
    LineBreakResult doLineBreakWithNoHyphenSpan(const U16StringPiece& textBuffer,
                                                const Range& noHyphenRange, float lineWidth) {
        MeasuredTextBuilder builder;
        auto family1 = buildFontFamily("Ascii.ttf");
        auto family2 = buildFontFamily("CustomExtent.ttf");
        std::vector<std::shared_ptr<FontFamily>> families = {family1, family2};
        auto fc = FontCollection::create(families);
        if (noHyphenRange.getStart() != 0) {
            MinikinPaint paint(fc);
            paint.size = 10.0f;  // Make 1em=10px
            paint.localeListId = LocaleListCache::getId("en-US");
            builder.addStyleRun(0, noHyphenRange.getStart(), std::move(paint), 0, 0,
                                true /* hyphenation */, false);
        }
        MinikinPaint paint(fc);
        paint.size = 10.0f;  // Make 1em=10px
        paint.localeListId = LocaleListCache::getId("en-US");
        builder.addStyleRun(noHyphenRange.getStart(), noHyphenRange.getEnd(), std::move(paint), 0,
                            0, false /* no hyphenation */, false);
        if (noHyphenRange.getEnd() != textBuffer.size()) {
            MinikinPaint paint(fc);
            paint.size = 10.0f;  // Make 1em=10px
            paint.localeListId = LocaleListCache::getId("en-US");
            builder.addStyleRun(noHyphenRange.getEnd(), textBuffer.size(), std::move(paint), 0, 0,
                                true /* hyphenation */, false);
        }
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuffer, true /* computeHyphen */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        return doLineBreak(textBuffer, *measuredText, BreakStrategy::HighQuality,
                           HyphenationFrequency::Normal, lineWidth);
    }

    LineBreakResult doLineBreakForBounds(const U16StringPiece& textBuffer, BreakStrategy strategy,
                                         HyphenationFrequency frequency, float lineWidth) {
        MeasuredTextBuilder builder;
        auto family1 = buildFontFamily("OvershootTest.ttf");
        auto family2 = buildFontFamily("Ascii.ttf");
        std::vector<std::shared_ptr<FontFamily>> families = {family1, family2};
        auto fc = FontCollection::create(families);
        MinikinPaint paint(fc);
        paint.size = 10.0f;  // Make 1em=10px
        paint.localeListId = LocaleListCache::getId("en-US");
        builder.addStyleRun(0, textBuffer.size(), std::move(paint), 0, 0, true, false);
        bool computeHyphen = frequency != HyphenationFrequency::None;
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuffer, computeHyphen, false /* compute full layout */,
                true /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(lineWidth);
        return breakLineOptimal(textBuffer, *measuredText, rectangleLineWidth, strategy, frequency,
                                false /* justified */, true /* useBoundsForWidth */);
    }

    LineBreakResult doLineBreak(const U16StringPiece& textBuffer, const MeasuredText& measuredText,
                                BreakStrategy strategy, HyphenationFrequency frequency,
                                float lineWidth) {
        RectangleLineWidth rectangleLineWidth(lineWidth);
        return breakLineOptimal(textBuffer, measuredText, rectangleLineWidth, strategy, frequency,
                                false /* justified */, false /* useBoundsForWidth */);
    }

    void expectBreak(const std::vector<LineBreakExpectation>& expect,
                     const U16StringPiece& textBuffer, BreakStrategy strategy,
                     HyphenationFrequency frequency, const std::string& lang, float lineWidth) {
        {
            char msg[256] = {};
            snprintf(msg, 256, "width = %f, lang = %s, strategy = %u, frequency = %u, fullyHyphen",
                     lineWidth, lang.c_str(), (uint32_t)strategy, (uint32_t)frequency);
            SCOPED_TRACE(msg);
            auto actual = doLineBreak(textBuffer, strategy, frequency, lang, lineWidth,
                                      false /* ignoreKerning */);
            EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                       << " vs " << std::endl
                                                       << toString(textBuffer, actual);
        }
        {
            char msg[256] = {};
            snprintf(msg, 256, "width = %f, lang = %s, strategy = %u, frequency = %u, fullyHyphen",
                     lineWidth, lang.c_str(), (uint32_t)strategy, (uint32_t)frequency);
            SCOPED_TRACE(msg);
            auto actual = doLineBreak(textBuffer, strategy, frequency, lang, lineWidth,
                                      true /* ignoreKerning */);
            EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                       << " vs " << std::endl
                                                       << toString(textBuffer, actual);
        }
    }

    LineBreakResult doLineBreakWithLetterSpacing(const U16StringPiece& textBuffer,
                                                 BreakStrategy strategy,
                                                 HyphenationFrequency frequency,
                                                 float letterSpacing, float lineWidth) {
        MeasuredTextBuilder builder;
        auto family1 = buildFontFamily("Ascii.ttf");
        std::vector<std::shared_ptr<FontFamily>> families = {family1};
        auto fc = FontCollection::create(families);
        MinikinPaint paint(fc);
        paint.size = 10.0f;  // Make 1em=10px
        paint.scaleX = 1.0f;
        paint.letterSpacing = letterSpacing;
        paint.localeListId = LocaleListCache::getId("en-US");
        builder.addStyleRun(0, textBuffer.size(), std::move(paint), 0, 0, true, false);
        bool computeHyphen = frequency != HyphenationFrequency::None;
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuffer, computeHyphen, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        return doLineBreak(textBuffer, *measuredText, strategy, frequency, lineWidth);
    }

private:
    std::vector<uint8_t> mHyphenationPattern;
};

TEST_F(OptimalLineBreakerTest, testBreakWithoutHyphenation) {
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr BreakStrategy BALANCED = BreakStrategy::Balanced;
    constexpr HyphenationFrequency NO_HYPHENATION = HyphenationFrequency::None;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;
    const std::vector<uint16_t> textBuf = utf8ToUtf16("This is an example text.");

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit END_HYPHEN = EndHyphenEdit::INSERT_HYPHEN;
    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        constexpr float LINE_WIDTH = 1000;
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 240, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 240;
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 240, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 230;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is an example " , 180, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."               ,  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an ex-" , 140, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample text."    , 110, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    return;
    {
        constexpr float LINE_WIDTH = 170;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an exam-" , 160, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple text."        ,  90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an ex-", 140, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample text."   , 110, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 160;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an exam-" , 160, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple text."        ,  90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an ex-", 140, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample text."   , 110, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 150;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an ex-", 140, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample text."   , 110, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is an ex-", 140, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample text."   , 110, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 130;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is an "   , 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example text." , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 120;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is an ", 100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example "   ,  70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."      ,  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is " ,  70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an exam-" ,  80, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple text.",  90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 90;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This "   , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an "  , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."   , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is " , 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an exam-" , 80, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple text.", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This "   , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an "  , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."   , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is " , 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an exam-" , 80, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple text.", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 80;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This "   , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an "  , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."   , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This is ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an ex-"  , 60, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample "  , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."   , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 70;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This "   , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an "  , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "example ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."   , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        // clang-format off
        expect = {
                { "This is ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an ex-"  , 60, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ample "  , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text."   , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 60;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an ", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "exa"   , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "mple " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text." , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        // clang-format off
        expect = {
                { "This " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an ", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "exam-" , 50, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple "  , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text." , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 50;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an ", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "exa"   , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "mple " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text." , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        // clang-format off
        expect = {
                { "This " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is an ", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "exam-" , 50, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple "  , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text." , 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 40;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is "   , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an "   , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "exa"   , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "mple " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text"  , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "."     , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is "   , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an "   , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "exa"   , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "mple " , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "t"     , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ext."  , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "This ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is "  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an "  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ex-"  , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "am-"  , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple " , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "text" , 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "."    , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        // clang-format off
        expect = {
                { "This ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is "  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an "  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ex-"  , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "am-"  , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple " , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "te"   , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xt."  , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 30;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                // TODO: Is this desperate break working correctly?
                { "T"   , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "his ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "e"   , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xam" , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ple ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "tex" , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "t."  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                // TODO: Is this desperate break working correctly?
                { "T"   , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "his ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "e"   , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xam" , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ple ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "te"  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xt." , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                // TODO: Is this desperate break working correctly?
                { "T"   , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "his ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ex-" , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "am-" , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT },
                { "ple ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "tex" , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "t."  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                // TODO: Is this desperate break working correctly?
                {"T"   , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"his ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"an " , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"ex-" , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT},
                {"am-" , 30, NO_START_HYPHEN, END_HYPHEN, ASCENT, DESCENT},
                {"ple ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                // TODO: Is this desperate break working correctly?
                {"te"  , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"xt." , 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 20;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "Th" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "e"  , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xa" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "mp" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "le ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "te" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xt" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "."  , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                { "Th" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "is ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "an ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "e"  , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "xa" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "mp" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "le ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                // TODO: Is this desperate break working correctly?
                { "t"  , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "ex" , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "t." , 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "T" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "h" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "i" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "s ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "i" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "s ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "a" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "n ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "e" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "x" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "a" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "m" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "p" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "l" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "e ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "t" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "e" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "x" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "t" , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "." , 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
}

TEST_F(OptimalLineBreakerTest, testHyphenationStartLineChange) {
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;
    // "hyphenation" is hyphnated to "hy-phen-a-tion".
    const std::vector<uint16_t> textBuf = utf8ToUtf16("czerwono-niebieska");

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    constexpr StartHyphenEdit START_HYPHEN = StartHyphenEdit::INSERT_HYPHEN;

    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        constexpr float LINE_WIDTH = 1000;
        std::vector<LineBreakExpectation> expect = {
                {"czerwono-niebieska", 180, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };

        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "pl", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 180;
        std::vector<LineBreakExpectation> expect = {
                {"czerwono-niebieska", 180, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };

        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "pl", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 130;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"czerwono-" ,  90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"-niebieska", 100,    START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "pl", LINE_WIDTH);
    }
}

TEST_F(OptimalLineBreakerTest, testZeroWidthLine) {
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;
    constexpr float LINE_WIDTH = 0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    {
        const auto textBuf = utf8ToUtf16("");
        std::vector<LineBreakExpectation> expect = {};
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        const auto textBuf = utf8ToUtf16("A");
        std::vector<LineBreakExpectation> expect = {
                {"A", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        const auto textBuf = utf8ToUtf16("AB");
        std::vector<LineBreakExpectation> expect = {
                {"A", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"B", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
}

TEST_F(OptimalLineBreakerTest, testZeroWidthCharacter) {
    constexpr float CHAR_WIDTH = 0.0;
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    {
        constexpr float LINE_WIDTH = 1.0;
        const auto textBuf = utf8ToUtf16("This is an example text.");
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 0, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, textBuf.size()), "en-US", CHAR_WIDTH, ASCENT,
                                          DESCENT);
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, true /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);

        const auto actual =
                doLineBreak(textBuf, *measuredText, HIGH_QUALITY, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 0.0;
        const auto textBuf = utf8ToUtf16("This is an example text.");
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 0, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, textBuf.size()), "en-US", CHAR_WIDTH, ASCENT,
                                          DESCENT);
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, true /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);

        const auto actual =
                doLineBreak(textBuf, *measuredText, HIGH_QUALITY, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testLocaleSwitchTest) {
    constexpr float CHAR_WIDTH = 10.0;
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    constexpr float LINE_WIDTH = 240;
    const auto textBuf = utf8ToUtf16("This is an example text.");
    {
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 240, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };

        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 18), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addCustomRun<ConstantRun>(Range(18, textBuf.size()), "en-US", CHAR_WIDTH, ASCENT,
                                          DESCENT);
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, true /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);

        const auto actual =
                doLineBreak(textBuf, *measuredText, HIGH_QUALITY, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 240, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };

        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 18), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addCustomRun<ConstantRun>(Range(18, textBuf.size()), "fr-FR", CHAR_WIDTH, ASCENT,
                                          DESCENT);
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, true /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        const auto actual =
                doLineBreak(textBuf, *measuredText, HIGH_QUALITY, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testEmailOrUrl) {
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr BreakStrategy BALANCED = BreakStrategy::Balanced;
    constexpr HyphenationFrequency NO_HYPHENATION = HyphenationFrequency::None;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    {
        constexpr float LINE_WIDTH = 240;
        const auto textBuf = utf8ToUtf16("This is an url: http://a.b");
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                // TODO: Fix this. Prefer not to break inside URL.
                {"This is an url: http://a", 240, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".b",                        20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);

        // clang-format off
        expect = {
                {"This is an url: ", 150, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"http://a.b",       100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 240;
        const auto textBuf = utf8ToUtf16("This is an email: a@example.com");
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an email: ", 170, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a@example.com"     , 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, HIGH_QUALITY, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NORMAL_HYPHENATION, "en-US", LINE_WIDTH);
    }
}

TEST_F(OptimalLineBreakerTest, testLocaleSwitch_InEmailOrUrl) {
    constexpr float CHAR_WIDTH = 10.0;
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr BreakStrategy BALANCED = BreakStrategy::Balanced;
    constexpr HyphenationFrequency NO_HYPHENATION = HyphenationFrequency::None;
    constexpr HyphenationFrequency NORMAL_HYPHENATION = HyphenationFrequency::Normal;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    constexpr float LINE_WIDTH = 240;
    {
        const auto textBuf = utf8ToUtf16("This is an url: http://a.b");
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 18), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addCustomRun<ConstantRun>(Range(18, textBuf.size()), "fr-FR", CHAR_WIDTH, ASCENT,
                                          DESCENT);
        std::unique_ptr<MeasuredText> measured = builder.build(
                textBuf, true /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);

        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                // TODO: Fix this. Prefer not to break inside URL.
                {"This is an url: http://a", 240, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".b",                        20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        auto actual = doLineBreak(textBuf, *measured, HIGH_QUALITY, NO_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        actual = doLineBreak(textBuf, *measured, HIGH_QUALITY, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);

        // clang-format off
        expect = {
                {"This is an url: ", 150, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"http://a.b",       100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        actual = doLineBreak(textBuf, *measured, BALANCED, NO_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        actual = doLineBreak(textBuf, *measured, BALANCED, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const auto textBuf = utf8ToUtf16("This is an email: a@example.com");
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 18), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addCustomRun<ConstantRun>(Range(18, textBuf.size()), "fr-FR", CHAR_WIDTH, ASCENT,
                                          DESCENT);
        std::unique_ptr<MeasuredText> measured = builder.build(
                textBuf, true /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);

        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an email: ", 170, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a@example.com",      130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        auto actual = doLineBreak(textBuf, *measured, HIGH_QUALITY, NO_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        actual = doLineBreak(textBuf, *measured, HIGH_QUALITY, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        actual = doLineBreak(textBuf, *measured, BALANCED, NO_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        actual = doLineBreak(textBuf, *measured, BALANCED, NORMAL_HYPHENATION, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, ExtentTest) {
    constexpr HyphenationFrequency NO_HYPHEN = HyphenationFrequency::None;
    const std::vector<uint16_t> textBuf = utf8ToUtf16("The \u3042\u3044\u3046 is Japanese.");

    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    {
        constexpr float LINE_WIDTH = 1000;
        std::vector<LineBreakExpectation> expect = {
                {"The \u3042\u3044\u3046 is Japanese.", 200, NO_START_HYPHEN, NO_END_HYPHEN,
                 CUSTOM_ASCENT, CUSTOM_DESCENT},
        };

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 200;
        std::vector<LineBreakExpectation> expect = {
                {"The \u3042\u3044\u3046 is Japanese.", 200, NO_START_HYPHEN, NO_END_HYPHEN,
                 CUSTOM_ASCENT, CUSTOM_DESCENT},
        };

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 190;
        std::vector<LineBreakExpectation> expect = {
                {"The \u3042\u3044\u3046 is ", 100, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT,
                 CUSTOM_DESCENT},
                {"Japanese.", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 90;
        std::vector<LineBreakExpectation> expect = {
                {"The \u3042", 50, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"\u3044\u3046 is ", 50, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT,
                 CUSTOM_DESCENT},
                {"Japanese.", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 50;
        std::vector<LineBreakExpectation> expect = {
                {"The \u3042", 50, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"\u3044\u3046 is ", 50, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT,
                 CUSTOM_DESCENT},
                {"Japan", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"ese.", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 40;
        std::vector<LineBreakExpectation> expect = {
                {"The ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u3042\u3044", 20, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"\u3046 is ", 40, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"Japa", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"nese", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 20;
        std::vector<LineBreakExpectation> expect = {
                {"T", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"he ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u3042", 10, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"\u3044\u3046 ", 20, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT,
                 CUSTOM_DESCENT},
                {"is ", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"Ja", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"pa", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"ne", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"se", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
    {
        constexpr float LINE_WIDTH = 10;
        std::vector<LineBreakExpectation> expect = {
                {"T", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"h", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u3042", 10, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"\u3044", 10, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"\u3046 ", 10, NO_START_HYPHEN, NO_END_HYPHEN, CUSTOM_ASCENT, CUSTOM_DESCENT},
                {"i", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"J", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"p", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"n", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHEN, "en-US", LINE_WIDTH);
    }
}

TEST_F(OptimalLineBreakerTest, testReplacementSpanNotBreakTest_SingleChar) {
    constexpr float CHAR_WIDTH = 10.0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    const auto textBuf = utf8ToUtf16("This is an example \u2639 text.");

    // In this test case, assign a replacement run for "U+2639" with 5 times of CHAR_WIDTH.
    auto doLineBreak = [=](float width) {
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 19), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(19, 21, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addCustomRun<ConstantRun>(Range(21, textBuf.size()), "en-US", CHAR_WIDTH, ASCENT,
                                          DESCENT);

        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, false /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(width);
        TabStops tabStops(nullptr, 0, 0);
        return breakLineOptimal(textBuf, *measuredText, rectangleLineWidth,
                                BreakStrategy::HighQuality, HyphenationFrequency::None,
                                false /* justified */, false /* useBoundsForWidth */);
    };

    {
        constexpr float LINE_WIDTH = 100;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an ",   100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ",       70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u2639 text.",  100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 90;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",   40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is an ",  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ",70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u2639 ", 50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"text.",   50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"T",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"h",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"i",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"i",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"n ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"m",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"p",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"l",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u2639 ",50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"t",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testReplacementSpanNotBreakTest_MultipleChars) {
    constexpr float CHAR_WIDTH = 10.0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    const auto textBuf = utf8ToUtf16("This is an example text.");

    // In this test case, assign a replacement run for "is an " with 5 times of CHAR_WIDTH.
    auto doLineBreak = [=](float width) {
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 5), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(5, 11, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addCustomRun<ConstantRun>(Range(11, textBuf.size()), "en-US", CHAR_WIDTH, ASCENT,
                                          DESCENT);

        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, false /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(width);
        TabStops tabStops(nullptr, 0, 0);
        return breakLineOptimal(textBuf, *measuredText, rectangleLineWidth,
                                BreakStrategy::HighQuality, HyphenationFrequency::None,
                                false /* justified */, false /* useBoundsForWidth */);
    };

    {
        constexpr float LINE_WIDTH = 100;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an ",   100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ",       70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",          50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 90;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",   40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is an ",  50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"example ",70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",   50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"T",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"h",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"i",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is an ", 50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"e",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"m",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"p",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"l",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testReplacementSpanNotBreakTest_continuedReplacementSpan) {
    constexpr float CHAR_WIDTH = 10.0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    const auto textBuf = utf8ToUtf16("This is an example text.");

    // In this test case, assign a replacement run for "is an " with 5 times of CHAR_WIDTH.
    auto doLineBreak = [=](float width) {
        MeasuredTextBuilder builder;
        builder.addReplacementRun(0, 5, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addReplacementRun(5, 8, 3 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addReplacementRun(8, 11, 3 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addReplacementRun(11, 19, 8 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addReplacementRun(19, 24, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, false /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(width);
        TabStops tabStops(nullptr, 0, 0);
        return breakLineOptimal(textBuf, *measuredText, rectangleLineWidth,
                                BreakStrategy::HighQuality, HyphenationFrequency::None,
                                false /* justified */, false /* useBoundsForWidth */);
    };

    {
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",    50, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"is an ",   60, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"example ", 80, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"text.",    50, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 40;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",    50, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"is ",      30, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"an ",      30, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"example ", 80, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"text.",    50, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",    50, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"is ",      30, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"an ",      30, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"example ", 80, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
                {"text.",    50, NO_START_HYPHEN, NO_END_HYPHEN, 0, 0},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testReplacementSpanNotBreakTest_CJK) {
    constexpr float CHAR_WIDTH = 10.0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    // Example string: "Today is a sunny day." in Japanese.
    const auto textBuf = utf8ToUtf16("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A");

    // In this test case, assign a replacement run for "\u6674\u5929" with 5 times of CHAR_WIDTH.
    auto doLineBreak = [=](float width) {
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 3), "ja-JP", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(3, 5, 5 * CHAR_WIDTH, LocaleListCache::getId("ja-JP"));
        builder.addCustomRun<ConstantRun>(Range(5, textBuf.size()), "ja-JP", CHAR_WIDTH, ASCENT,
                                          DESCENT);

        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, false /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore krening */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(width);
        TabStops tabStops(nullptr, 0, 0);
        return breakLineOptimal(textBuf, *measuredText, rectangleLineWidth,
                                BreakStrategy::HighQuality, HyphenationFrequency::None,
                                false /* justified */, false /* useBoundsForWidth */);
    };

    {
        constexpr float LINE_WIDTH = 100;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F\u6674\u5929\u306A\u308A",
                  100, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 90;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F\u6674\u5929\u306A",
                  90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u308A",
                  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 80;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F\u6674\u5929",
                  80, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u306A\u308A",
                  20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 70;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u6674\u5929\u306A\u308A", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 60;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u6674\u5929\u306A", 60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u308A",             10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 50;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u6674\u5929",       50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"\u306A\u308A",       20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 40;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C\u65E5\u306F", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u6674\u5929",       50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"\u306A\u308A",       20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // "\u6674\u5929" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"\u672C",       10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u65E5",       10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u306F",       10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u6674\u5929", 50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"\u306A",       10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u308A",       10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

// http://b/119657685
// Following test case is for verifying that the ReplacementSpan should not be broken into multiple
// pieces. The actual break point is not a part of expectation. For example, it would be good to
// break the starting offset of the ReplacementSpan for some case.
TEST_F(OptimalLineBreakerTest, testReplacementSpan_GraphemeLineBreakWithMultipleRepalcementSpans) {
    constexpr float CHAR_WIDTH = 10.0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    const auto textBuf = utf8ToUtf16("ab de\u00A0\u00A0fg ij\u00A0\u00A0kl no\u00A0\u00A0pq st");

    auto doLineBreak = [=](float width) {
        MeasuredTextBuilder builder;
        builder.addReplacementRun(0, 5, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addCustomRun<ConstantRun>(Range(5, 7), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(7, 12, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addCustomRun<ConstantRun>(Range(12, 14), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(14, 19, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addCustomRun<ConstantRun>(Range(19, 21), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(21, 26, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));

        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, false /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(width);
        TabStops tabStops(nullptr, 0, 0);
        return breakLineOptimal(textBuf, *measuredText, rectangleLineWidth,
                                BreakStrategy::HighQuality, HyphenationFrequency::None,
                                false /* justified */, false /* useBoundsForWidth */);
    };

    {
        constexpr float LINE_WIDTH = 1000;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de\u00A0\u00A0fg ij\u00A0\u00A0kl no\u00A0\u00A0pq st",
                  260, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 250;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de\u00A0\u00A0fg ij\u00A0\u00A0kl no\u00A0\u00A0",
                  210, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"pq st",
                   50, NO_START_HYPHEN, NO_END_HYPHEN,       0,      0},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 180;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de\u00A0\u00A0fg ij\u00A0\u00A0",
                  140, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"kl no\u00A0\u00A0pq st",
                  120, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 130;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de\u00A0\u00A0fg ij\u00A0",
                  130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u00A0kl no\u00A0\u00A0pq st",
                  130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 110;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de\u00A0",             60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u00A0fg ij\u00A0\u00A0", 80, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"kl no\u00A0\u00A0",       70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"pq st",                   50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 60;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de\u00A0",  60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u00A0fg ij",  60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u00A0\u00A0", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"kl no\u00A0",  60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u00A0pq st",  60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 50;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"ab de",        50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"\u00A0\u00A0", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"fg ij",        50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"\u00A0\u00A0", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"kl no",        50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {"\u00A0\u00A0", 20, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"pq st",        50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testReplacementSpanNotBreakTest_with_punctuation) {
    constexpr float CHAR_WIDTH = 10.0;

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;

    const auto textBuf = utf8ToUtf16("This (is an) example text.");

    // In this test case, assign a replacement run for "U+2639" with 5 times of CHAR_WIDTH.
    auto doLineBreak = [=](float width) {
        MeasuredTextBuilder builder;
        builder.addCustomRun<ConstantRun>(Range(0, 6), "en-US", CHAR_WIDTH, ASCENT, DESCENT);
        builder.addReplacementRun(6, 11, 5 * CHAR_WIDTH, LocaleListCache::getId("en-US"));
        builder.addCustomRun<ConstantRun>(Range(11, textBuf.size()), "en-US", CHAR_WIDTH, ASCENT,
                                          DESCENT);

        std::unique_ptr<MeasuredText> measuredText = builder.build(
                textBuf, false /* compute hyphenation */, false /* compute full layout */,
                false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
        RectangleLineWidth rectangleLineWidth(width);
        TabStops tabStops(nullptr, 0, 0);
        return breakLineOptimal(textBuf, *measuredText, rectangleLineWidth,
                                BreakStrategy::HighQuality, HyphenationFrequency::Normal,
                                false /* justified */, false /* useBoundsForWidth */);
    };

    {
        constexpr float LINE_WIDTH = 1000;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This (is an) example text.",
                  260, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 250;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This (is an) example ", 200, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",                  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 190;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This (is an) ", 120, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example text.", 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 120;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This (is an) ", 120, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ",       70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",          50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 110;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",    40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"(is an) ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",    50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 60;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",  40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"(is an", 60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {") ex",   40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"ample ", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 50;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",  40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"(",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is an",  50, NO_START_HYPHEN, NO_END_HYPHEN,      0,       0},
                {") ex",   40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"ample ", 50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 40;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",  40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                // TODO(nona): This might be wrongly broken. "(is an" should be broken into "(" and
                // "is an" as the desperate break.
                {"(is an", 60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {") ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"exa",    30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"mple ",  40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text",   40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // "is an" is a single replacement span. Do not break.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"T",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"h",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"i",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                // TODO(nona): This might be wrongly broken. "(is an" should be broken into "(" and
                // "is an" as the desperate break.
                {"(is an", 60, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {") ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"m",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"p",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"l",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e ",     10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".",      10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on
        const auto actual = doLineBreak(LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F(OptimalLineBreakerTest, testControllCharAfterSpace) {
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr BreakStrategy BALANCED = BreakStrategy::Balanced;
    constexpr HyphenationFrequency NO_HYPHENATION = HyphenationFrequency::None;
    const std::vector<uint16_t> textBuf = utf8ToUtf16("example \u2066example");

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    {
        constexpr float LINE_WIDTH = 90;
        // Note that HarfBuzz assigns 0px for control characters regardless of glyph existence in
        // the font.
        std::vector<LineBreakExpectation> expect = {
                {"example ", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"\u2066example", 70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };

        expectBreak(expect, textBuf, HIGH_QUALITY, NO_HYPHENATION, "en-US", LINE_WIDTH);
        expectBreak(expect, textBuf, BALANCED, NO_HYPHENATION, "en-US", LINE_WIDTH);
    }
}

TEST_F(OptimalLineBreakerTest, roundingError) {
    MeasuredTextBuilder builder;
    auto family1 = buildFontFamily("Ascii.ttf");
    std::vector<std::shared_ptr<FontFamily>> families = {family1};
    auto fc = FontCollection::create(families);
    MinikinPaint paint(fc);
    paint.size = 56.0f;  // Make 1em=56px
    paint.scaleX = 1;
    paint.letterSpacing = -0.093f;
    paint.localeListId = LocaleListCache::getId("en-US");
    const std::vector<uint16_t> textBuffer = utf8ToUtf16("8888888888888888888");

    float measured =
            Layout::measureText(textBuffer, Range(0, textBuffer.size()), Bidi::LTR, paint,
                                StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, nullptr,
                                nullptr /* bounds */, nullptr /* cluster count */, RunFlag::NONE);

    builder.addStyleRun(0, textBuffer.size(), std::move(paint), 0, 0, true, false);
    std::unique_ptr<MeasuredText> measuredText = builder.build(
            textBuffer, false /* compute hyphenation */, false /* compute full layout */,
            false /* computeBounds */, false /* ignore kerning */, nullptr /* no hint */);
    RectangleLineWidth rectangleLineWidth(measured);
    TabStops tabStops(nullptr, 0, 10);
    LineBreakResult r = doLineBreak(textBuffer, *measuredText, BreakStrategy::Balanced,
                                    HyphenationFrequency::None, measured);

    EXPECT_EQ(1u, r.breakPoints.size());
}

TEST_F(OptimalLineBreakerTest, testBreakWithoutBounds_trailing) {
    // The OvershootTest.ttf has following coverage, extent, width and bbox.
    // U+0061(a): 1em, (   0, 0) - (1,   1)
    // U+0062(b): 1em, (   0, 0) - (1.5, 1)
    // U+0063(c): 1em, (   0, 0) - (2,   1)
    // U+0064(d): 1em, (   0, 0) - (2.5, 1)
    // U+0065(e): 1em, (-0.5, 0) - (1,   1)
    // U+0066(f): 1em, (-1.0, 0) - (1,   1)
    // U+0067(g): 1em, (-1.5, 0) - (1,   1)
    const std::vector<uint16_t> textBuf = utf8ToUtf16("dddd dddd dddd dddd");
    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        constexpr float LINE_WIDTH = 1000;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"dddd dddd dddd dddd", 190, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual = doLineBreakForBounds(textBuf, BreakStrategy::HighQuality,
                                                 HyphenationFrequency::None, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        EXPECT_EQ(MinikinRect(0, 10, 205, 0), actual.bounds[0]);
    }
    {
        constexpr float LINE_WIDTH = 110;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"dddd dddd ", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"dddd dddd", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual = doLineBreakForBounds(textBuf, BreakStrategy::HighQuality,
                                                 HyphenationFrequency::None, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        EXPECT_EQ(MinikinRect(0, 10, 105, 0), actual.bounds[0]);
        EXPECT_EQ(MinikinRect(0, 10, 105, 0), actual.bounds[1]);
    }
    {
        constexpr float LINE_WIDTH = 100;
        // Even if the total advance of "dddd dddd" is 90, the width of bounding box of "dddd dddd"
        // is
        // Rect(0em, 1em, 10.5em, 0em). So "dddd dddd" is broken into two lines.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"dddd ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"dddd ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"dddd ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"dddd", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual = doLineBreakForBounds(textBuf, BreakStrategy::HighQuality,
                                                 HyphenationFrequency::None, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        EXPECT_EQ(MinikinRect(0, 10, 55, 0), actual.bounds[0]);
        EXPECT_EQ(MinikinRect(0, 10, 55, 0), actual.bounds[1]);
        EXPECT_EQ(MinikinRect(0, 10, 55, 0), actual.bounds[2]);
        EXPECT_EQ(MinikinRect(0, 10, 55, 0), actual.bounds[3]);
    }
}

TEST_F(OptimalLineBreakerTest, testBreakWithoutBounds_preceding) {
    // The OvershootTest.ttf has following coverage, extent, width and bbox.
    // U+0061(a): 1em, (   0, 0) - (1,   1)
    // U+0062(b): 1em, (   0, 0) - (1.5, 1)
    // U+0063(c): 1em, (   0, 0) - (2,   1)
    // U+0064(d): 1em, (   0, 0) - (2.5, 1)
    // U+0065(e): 1em, (-0.5, 0) - (1,   1)
    // U+0066(f): 1em, (-1.0, 0) - (1,   1)
    // U+0067(g): 1em, (-1.5, 0) - (1,   1)
    const std::vector<uint16_t> textBuf = utf8ToUtf16("gggg gggg gggg gggg");
    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        constexpr float LINE_WIDTH = 1000;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"gggg gggg gggg gggg", 190, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual = doLineBreakForBounds(textBuf, BreakStrategy::HighQuality,
                                                 HyphenationFrequency::None, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        EXPECT_EQ(MinikinRect(-15, 10, 190, 0), actual.bounds[0]);
    }
    {
        constexpr float LINE_WIDTH = 110;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"gggg gggg ", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"gggg gggg", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual = doLineBreakForBounds(textBuf, BreakStrategy::HighQuality,
                                                 HyphenationFrequency::None, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        EXPECT_EQ(MinikinRect(-15, 10, 90, 0), actual.bounds[0]);
        EXPECT_EQ(MinikinRect(-15, 10, 90, 0), actual.bounds[1]);
    }
    {
        constexpr float LINE_WIDTH = 100;
        // Even if the total advance of "gggg gggg" is 90, the width of bounding box of "gggg gggg"
        // is
        // Rect(0em, 1em, 10.5em, 0em). So "gggg gggg" is broken into two lines.
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"gggg ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"gggg ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"gggg ", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"gggg", 40, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual = doLineBreakForBounds(textBuf, BreakStrategy::HighQuality,
                                                 HyphenationFrequency::None, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
        EXPECT_EQ(MinikinRect(-15, 10, 40, 0), actual.bounds[0]);
        EXPECT_EQ(MinikinRect(-15, 10, 40, 0), actual.bounds[1]);
        EXPECT_EQ(MinikinRect(-15, 10, 40, 0), actual.bounds[2]);
        EXPECT_EQ(MinikinRect(-15, 10, 40, 0), actual.bounds[3]);
    }
}

TEST_F(OptimalLineBreakerTest, testBreakWithHyphenation_NoHyphenSpan) {
    const std::vector<uint16_t> textBuffer = utf8ToUtf16("This is Android. Here is hyphenation.");
    const Range noHyphenRange(25, 37);  // the range of the word "hyphenation".

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit END_HYPHEN = EndHyphenEdit::INSERT_HYPHEN;
    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        constexpr float LINE_WIDTH = 170;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is Android. " , 160, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "Here is hyphena-"  , 160, NO_START_HYPHEN,    END_HYPHEN, ASCENT, DESCENT },
                { "tion."             ,  50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        auto actual =
                doLineBreak(textBuffer, BreakStrategy::HighQuality, HyphenationFrequency::Normal,
                            "en-US", LINE_WIDTH, false /* ignore kerning */);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuffer, actual);
    }
    {
        constexpr float LINE_WIDTH = 170;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                { "This is An-"    , 110, NO_START_HYPHEN,    END_HYPHEN, ASCENT, DESCENT },
                { "droid. Here is ", 140, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
                { "hyphenation."   , 120, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT },
        };
        // clang-format on

        auto actual = doLineBreakWithNoHyphenSpan(textBuffer, noHyphenRange, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuffer, actual);
    }
}

TEST_F_WITH_FLAGS(OptimalLineBreakerTest, testPhraseBreakNone,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(com::android::text::flags,
                                                      word_style_auto))) {
    // For short hand of writing expectation for lines.
    auto line = [](std::string t, float w) -> LineBreakExpectation {
        return {t, w, StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, ASCENT, DESCENT};
    };

    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 1));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002" , 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::None, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 2));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5" , 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002" , 60),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::None, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 3));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5", 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674", 100),
                line("\u5929\u306A\u308A\u3002", 40),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::None, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 4));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5", 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674", 100),
                line("\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A", 100),
                line("\u308A\u3002"  , 20),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::None, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 5));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5", 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674", 100),
                line("\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A", 100),
                line("\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 100),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::None, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 6));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5", 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674", 100),
                line("\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A", 100),
                line("\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 100),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::None, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F_WITH_FLAGS(OptimalLineBreakerTest, testPhraseBreakPhrase,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(com::android::text::flags,
                                                      word_style_auto))) {
    // For short hand of writing expectation for lines.
    auto line = [](std::string t, float w) -> LineBreakExpectation {
        return {t, w, StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, ASCENT, DESCENT};
    };

    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 1));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Phrase, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 2));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Phrase, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 3));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Phrase, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 4));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Phrase, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 5));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Phrase, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 6));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Phrase, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F_WITH_FLAGS(OptimalLineBreakerTest, testPhraseBreakAuto,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(com::android::text::flags,
                                                      word_style_auto))) {
    // For short hand of writing expectation for lines.
    auto line = [](std::string t, float w) -> LineBreakExpectation {
        return {t, w, StartHyphenEdit::NO_EDIT, EndHyphenEdit::NO_EDIT, ASCENT, DESCENT};
    };

    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 1));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Auto, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 2));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Auto, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 3));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Auto, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 4));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Auto, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    // When the line becomes more or equal to 5, the phrase based line break is disabled.
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 5));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5", 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674", 100),
                line("\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A", 100),
                line("\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 100),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Auto, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        const std::vector<uint16_t> textBuf = utf8ToUtf16(repeat(JP_TEXT, 6));
        constexpr float LINE_WIDTH = 100;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5", 100),
                line("\u306F\u6674\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674", 100),
                line("\u5929\u306A\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A", 100),
                line("\u308A\u3002\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002", 100),
                line("\u672C\u65E5\u306F\u6674\u5929\u306A\u308A\u3002"  , 80),
        };
        // clang-format on

        const auto actual =
                doLineBreakForJapanese(textBuf, LineBreakWordStyle::Auto, "ja-JP", LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

TEST_F_WITH_FLAGS(OptimalLineBreakerTest, testBreakLetterSpacing,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(com::android::text::flags,
                                                      letter_spacing_justification))) {
    constexpr BreakStrategy HIGH_QUALITY = BreakStrategy::HighQuality;
    constexpr HyphenationFrequency NO_HYPHEN = HyphenationFrequency::None;
    const std::vector<uint16_t> textBuf = utf8ToUtf16("This is an example text.");

    constexpr StartHyphenEdit NO_START_HYPHEN = StartHyphenEdit::NO_EDIT;
    constexpr EndHyphenEdit NO_END_HYPHEN = EndHyphenEdit::NO_EDIT;
    // Note that disable clang-format everywhere since aligned expectation is more readable.
    {
        constexpr float LINE_WIDTH = 1000;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 470, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 470;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an example text.", 470, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 460;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an example ", 350, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",                90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 240;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This is an ", 190, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ",    130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",        90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 130;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",     70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is an ",    90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"example ", 130, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",     90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 120;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"This ",  70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is an ", 90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"exa",    50, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"mple ",  70, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"text.",  90, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 30;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"Th",  30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"is ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"an ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",   10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"xa",  30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"mp",  30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"le ", 30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"te",  30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"xt",  30, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".",   10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
    {
        constexpr float LINE_WIDTH = 10;
        // clang-format off
        std::vector<LineBreakExpectation> expect = {
                {"T",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"h",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"i",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"i",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"s ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"n ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"a",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"m",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"p",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"l",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e ", 10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"e",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"x",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {"t",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
                {".",  10, NO_START_HYPHEN, NO_END_HYPHEN, ASCENT, DESCENT},
        };
        // clang-format on

        const auto actual =
                doLineBreakWithLetterSpacing(textBuf, HIGH_QUALITY, NO_HYPHEN, 1.0f, LINE_WIDTH);
        EXPECT_TRUE(sameLineBreak(expect, actual)) << toString(expect) << std::endl
                                                   << " vs " << std::endl
                                                   << toString(textBuf, actual);
    }
}

}  // namespace
}  // namespace minikin
