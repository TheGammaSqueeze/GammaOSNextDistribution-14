/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include "UnicodeUtils.h"
#include "minikin/FontCollection.h"
#include "minikin/Layout.h"
#include "minikin/LayoutPieces.h"
#include "minikin/Measurement.h"

namespace minikin {

namespace {
constexpr bool LTR = false;
constexpr bool RTL = true;
}  // namespace

class LayoutLetterSpacingTest : public testing::Test {
protected:
    LayoutLetterSpacingTest() {}
    virtual ~LayoutLetterSpacingTest() {}
    virtual void SetUp() override {}
    virtual void TearDown() override {}

    void LayoutTest(std::initializer_list<float> expect_advances_args,
                    std::initializer_list<float> expect_glyph_offsets_args, const std::string& utf8,
                    bool isRtl, RunFlag runFlag) {
        ASSERT_TRUE(mPaint) << "Setup error: must call setShapeParam for setting up the test";

        // Prepare parameters
        std::vector<float> expect_advances(expect_advances_args.begin(),
                                           expect_advances_args.end());
        std::vector<float> expect_glyph_offsets(expect_glyph_offsets_args.begin(),
                                                expect_glyph_offsets_args.end());
        std::vector<uint16_t> text = utf8ToUtf16(utf8);
        Range range(0, text.size());
        Bidi bidiFlag = isRtl ? Bidi::RTL : Bidi::LTR;

        // Compute widths and layout
        std::vector<float> advances(text.size());
        float width = Layout::measureText(text, range, bidiFlag, *mPaint, StartHyphenEdit::NO_EDIT,
                                          EndHyphenEdit::NO_EDIT, advances.data(), nullptr, nullptr,
                                          runFlag);
        // Calculate width without per character widths.
        float widthWithoutChars =
                Layout::measureText(text, range, bidiFlag, *mPaint, StartHyphenEdit::NO_EDIT,
                                    EndHyphenEdit::NO_EDIT, nullptr, nullptr, nullptr, runFlag);
        Layout layout(text, range, bidiFlag, *mPaint, StartHyphenEdit::NO_EDIT,
                      EndHyphenEdit::NO_EDIT, runFlag);

        SCOPED_TRACE(::testing::Message()
                     << "text=" << utf8 << ", flag=" << static_cast<int>(runFlag)
                     << ", rtl=" << isRtl << ", layout = " << layout);
        // First, Layout creation and measureText should have the same output
        EXPECT_EQ(width, widthWithoutChars);
        EXPECT_EQ(advances, layout.getAdvances());
        EXPECT_EQ(width, layout.getAdvance());

        // Verify the advances
        EXPECT_EQ(expect_advances, advances);
        float total_advance = 0;
        for (uint32_t i = 0; i < expect_advances.size(); ++i) {
            total_advance += advances[i];
        }
        EXPECT_EQ(total_advance, width);

        // Verify Glyph offset
        EXPECT_EQ(expect_glyph_offsets.size(), layout.nGlyphs());
        std::vector<float> actual_glyph_offsets;
        for (uint32_t i = 0; i < expect_glyph_offsets.size(); ++i) {
            actual_glyph_offsets.push_back(layout.getX(i));
        }
        EXPECT_EQ(expect_glyph_offsets, actual_glyph_offsets);
    }

    void setShapeParam(const std::string& font, float letterSpacing) {
        auto fc = buildFontCollection(font);
        mPaint = std::make_unique<MinikinPaint>(fc);

        mPaint->letterSpacing = letterSpacing;
        mPaint->size = 10.0f;
        mPaint->scaleX = 1.0f;
    }

private:
    std::unique_ptr<MinikinPaint> mPaint;
};

TEST_F(LayoutLetterSpacingTest, measuredTextTest_LetterSpacing) {
    // In this test case, use LayoutTestFont.ttf for testing. This font supports character I. The
    // glyph of 'I' in this font has 1em width.
    setShapeParam("LayoutTestFont.ttf", 1.0f);

    // Single letter
    LayoutTest({20}, {5}, "I", LTR, NONE);
    LayoutTest({15}, {0}, "I", LTR, LEFT_EDGE);
    LayoutTest({15}, {5}, "I", LTR, RIGHT_EDGE);
    LayoutTest({10}, {0}, "I", LTR, WHOLE_LINE);

    // Two letters
    LayoutTest({20, 20}, {5, 25}, "II", LTR, NONE);
    LayoutTest({15, 20}, {0, 20}, "II", LTR, LEFT_EDGE);
    LayoutTest({20, 15}, {5, 25}, "II", LTR, RIGHT_EDGE);
    LayoutTest({15, 15}, {0, 20}, "II", LTR, WHOLE_LINE);

    // Three letters
    LayoutTest({20, 20, 20}, {5, 25, 45}, "III", LTR, NONE);
    LayoutTest({15, 20, 20}, {0, 20, 40}, "III", LTR, LEFT_EDGE);
    LayoutTest({20, 20, 15}, {5, 25, 45}, "III", LTR, RIGHT_EDGE);
    LayoutTest({15, 20, 15}, {0, 20, 40}, "III", LTR, WHOLE_LINE);
}

TEST_F(LayoutLetterSpacingTest, measuredTextTest_LetterSpacing_ligature) {
    // In this test case, use Ligature.ttf for testing. This font supports both character 'f' and
    // character 'i', then this font has ligatured form of sequence "fi". All letters in this font
    // have 1em width.
    setShapeParam("Ligature.ttf", 1.0f);

    // Single letter
    LayoutTest({20, 0}, {5}, "fi", LTR, NONE);
    LayoutTest({15, 0}, {0}, "fi", LTR, LEFT_EDGE);
    LayoutTest({15, 0}, {5}, "fi", LTR, RIGHT_EDGE);
    LayoutTest({10, 0}, {0}, "fi", LTR, WHOLE_LINE);

    // Two letters
    LayoutTest({20, 0, 20, 0}, {5, 25}, "fifi", LTR, NONE);
    LayoutTest({15, 0, 20, 0}, {0, 20}, "fifi", LTR, LEFT_EDGE);
    LayoutTest({20, 0, 15, 0}, {5, 25}, "fifi", LTR, RIGHT_EDGE);
    LayoutTest({15, 0, 15, 0}, {0, 20}, "fifi", LTR, WHOLE_LINE);

    // Three letters
    LayoutTest({20, 0, 20, 0, 20, 0}, {5, 25, 45}, "fififi", LTR, NONE);
    LayoutTest({15, 0, 20, 0, 20, 0}, {0, 20, 40}, "fififi", LTR, LEFT_EDGE);
    LayoutTest({20, 0, 20, 0, 15, 0}, {5, 25, 45}, "fififi", LTR, RIGHT_EDGE);
    LayoutTest({15, 0, 20, 0, 15, 0}, {0, 20, 40}, "fififi", LTR, WHOLE_LINE);
}

TEST_F(LayoutLetterSpacingTest, measuredTextTest_LetterSpacing_Bidi) {
    const std::string LLRRLL = "aa\u05D0\u05D0aa";
    const std::string RRLLRR = "\u05D0\u05D0aa\u05D0\u05D0";

    // In this test case, use BiDi.ttf for testing. This font supports both character 'a' and
    // Hebrew Alef. Both letters have 1em advance.
    setShapeParam("BiDi.ttf", 1.0f);

    // LLRRLL with LTR context
    LayoutTest({20, 20, 20, 20, 20, 20}, {5, 25, 45, 65, 85, 105}, LLRRLL, LTR, NONE);
    LayoutTest({15, 20, 20, 20, 20, 20}, {0, 20, 40, 60, 80, 100}, LLRRLL, LTR, LEFT_EDGE);
    LayoutTest({20, 20, 20, 20, 20, 15}, {5, 25, 45, 65, 85, 105}, LLRRLL, LTR, RIGHT_EDGE);
    LayoutTest({15, 20, 20, 20, 20, 15}, {0, 20, 40, 60, 80, 100}, LLRRLL, LTR, WHOLE_LINE);

    // LLRRLL with RTL context
    LayoutTest({20, 20, 20, 20, 20, 20}, {5, 25, 45, 65, 85, 105}, LLRRLL, RTL, NONE);
    LayoutTest({20, 20, 20, 20, 15, 20}, {0, 20, 40, 60, 80, 100}, LLRRLL, RTL, LEFT_EDGE);
    LayoutTest({20, 15, 20, 20, 20, 20}, {5, 25, 45, 65, 85, 105}, LLRRLL, RTL, RIGHT_EDGE);
    LayoutTest({20, 15, 20, 20, 15, 20}, {0, 20, 40, 60, 80, 100}, LLRRLL, RTL, WHOLE_LINE);

    // RRLLRR with LTR context
    LayoutTest({20, 20, 20, 20, 20, 20}, {5, 25, 45, 65, 85, 105}, RRLLRR, LTR, NONE);
    LayoutTest({20, 15, 20, 20, 20, 20}, {0, 20, 40, 60, 80, 100}, RRLLRR, LTR, LEFT_EDGE);
    LayoutTest({20, 20, 20, 20, 15, 20}, {5, 25, 45, 65, 85, 105}, RRLLRR, LTR, RIGHT_EDGE);
    LayoutTest({20, 15, 20, 20, 15, 20}, {0, 20, 40, 60, 80, 100}, RRLLRR, LTR, WHOLE_LINE);

    // RRLLRR with RTL context
    LayoutTest({20, 20, 20, 20, 20, 20}, {5, 25, 45, 65, 85, 105}, RRLLRR, RTL, NONE);
    LayoutTest({20, 20, 20, 20, 20, 15}, {0, 20, 40, 60, 80, 100}, RRLLRR, RTL, LEFT_EDGE);
    LayoutTest({15, 20, 20, 20, 20, 20}, {5, 25, 45, 65, 85, 105}, RRLLRR, RTL, RIGHT_EDGE);
    LayoutTest({15, 20, 20, 20, 20, 15}, {0, 20, 40, 60, 80, 100}, RRLLRR, RTL, WHOLE_LINE);
}

TEST_F(LayoutLetterSpacingTest, measureTextTest_ControlCharacters) {
    // In this test case, use ControlCharacters.ttf for testing. This font supports ASCII plus
    // following letters.
    // U+FEFF: ZERO WIDTH NO-BREAK SPACE: Control character and no width.
    setShapeParam("ControlCharacters.ttf", 1.0f);

    // U+FEFF is a control character, so letter spacing should not be applied.
    LayoutTest({0}, {0}, "\uFEFF", LTR, NONE);
    LayoutTest({0}, {0}, "\uFEFF", LTR, LEFT_EDGE);
    LayoutTest({0}, {0}, "\uFEFF", LTR, RIGHT_EDGE);
    LayoutTest({0}, {0}, "\uFEFF", LTR, WHOLE_LINE);

    LayoutTest({20, 0}, {5, 20}, "a\uFEFF", LTR, NONE);
    LayoutTest({15, 0}, {0, 15}, "a\uFEFF", LTR, LEFT_EDGE);
    LayoutTest({15, 0}, {5, 15}, "a\uFEFF", LTR, RIGHT_EDGE);
    LayoutTest({10, 0}, {0, 10}, "a\uFEFF", LTR, WHOLE_LINE);

    LayoutTest({20, 0, 20}, {5, 20, 25}, "a\uFEFFa", LTR, NONE);
    LayoutTest({15, 0, 20}, {0, 15, 20}, "a\uFEFFa", LTR, LEFT_EDGE);
    LayoutTest({20, 0, 15}, {5, 20, 25}, "a\uFEFFa", LTR, RIGHT_EDGE);
    LayoutTest({15, 0, 15}, {0, 15, 20}, "a\uFEFFa", LTR, WHOLE_LINE);

    LayoutTest({0, 20}, {0, 5}, "\uFEFFa", LTR, NONE);
    LayoutTest({0, 15}, {0, 0}, "\uFEFFa", LTR, LEFT_EDGE);
    LayoutTest({0, 15}, {0, 5}, "\uFEFFa", LTR, RIGHT_EDGE);
    LayoutTest({0, 10}, {0, 0}, "\uFEFFa", LTR, WHOLE_LINE);

    LayoutTest({0, 20, 0}, {0, 5, 20}, "\uFEFFa\uFEFF", LTR, NONE);
    LayoutTest({0, 15, 0}, {0, 0, 15}, "\uFEFFa\uFEFF", LTR, LEFT_EDGE);
    LayoutTest({0, 15, 0}, {0, 5, 15}, "\uFEFFa\uFEFF", LTR, RIGHT_EDGE);
    LayoutTest({0, 10, 0}, {0, 0, 10}, "\uFEFFa\uFEFF", LTR, WHOLE_LINE);
}

TEST_F(LayoutLetterSpacingTest, measureTextTest_ControlCharacters_RTL) {
    // In this test case, use ControlCharacters.ttf for testing. This font supports ASCII plus
    // following letters.
    // U+FEFF: ZERO WIDTH NO-BREAK SPACE: Control character and no width.
    setShapeParam("ControlCharacters.ttf", 1.0f);

    // U+FEFF is a control character, so letter spacing should not be applied.
    LayoutTest({0}, {0}, "\uFEFF", RTL, NONE);
    LayoutTest({0}, {0}, "\uFEFF", RTL, LEFT_EDGE);
    LayoutTest({0}, {0}, "\uFEFF", RTL, RIGHT_EDGE);
    LayoutTest({0}, {0}, "\uFEFF", RTL, WHOLE_LINE);

    LayoutTest({20, 0}, {0, 5}, "\u05D0\uFEFF", RTL, NONE);
    LayoutTest({15, 0}, {0, 0}, "\u05D0\uFEFF", RTL, LEFT_EDGE);
    LayoutTest({15, 0}, {0, 5}, "\u05D0\uFEFF", RTL, RIGHT_EDGE);
    LayoutTest({10, 0}, {0, 0}, "\u05D0\uFEFF", RTL, WHOLE_LINE);

    LayoutTest({20, 0, 20}, {5, 20, 25}, "\u05D0\uFEFF\u05D0", RTL, NONE);
    LayoutTest({20, 0, 15}, {0, 15, 20}, "\u05D0\uFEFF\u05D0", RTL, LEFT_EDGE);
    LayoutTest({15, 0, 20}, {5, 20, 25}, "\u05D0\uFEFF\u05D0", RTL, RIGHT_EDGE);
    LayoutTest({15, 0, 15}, {0, 15, 20}, "\u05D0\uFEFF\u05D0", RTL, WHOLE_LINE);

    LayoutTest({0, 20}, {5, 20}, "\uFEFF\u05D0", RTL, NONE);
    LayoutTest({0, 15}, {0, 15}, "\uFEFF\u05D0", RTL, LEFT_EDGE);
    LayoutTest({0, 15}, {5, 15}, "\uFEFF\u05D0", RTL, RIGHT_EDGE);
    LayoutTest({0, 10}, {0, 10}, "\uFEFF\u05D0", RTL, WHOLE_LINE);

    LayoutTest({0, 20, 0}, {0, 5, 20}, "\uFEFF\u05D0\uFEFF", RTL, NONE);
    LayoutTest({0, 15, 0}, {0, 0, 15}, "\uFEFF\u05D0\uFEFF", RTL, LEFT_EDGE);
    LayoutTest({0, 15, 0}, {0, 5, 15}, "\uFEFF\u05D0\uFEFF", RTL, RIGHT_EDGE);
    LayoutTest({0, 10, 0}, {0, 0, 10}, "\uFEFF\u05D0\uFEFF", RTL, WHOLE_LINE);
}

TEST_F(LayoutLetterSpacingTest, measureTextTest_ControlCharacters_RTL_Arabic) {
    // In this test case, use ControlCharacters.ttf for testing. This font supports ASCII plus
    // following letters.
    // U+FEFF: ZERO WIDTH NO-BREAK SPACE: Control character and no width.
    setShapeParam("ControlCharacters.ttf", 1.0f);

    // U+FEFF is a control character, so letter spacing should not be applied.
    LayoutTest({10, 0}, {0, 0}, "\u0627\uFEFF", RTL, NONE);
    LayoutTest({10, 0}, {0, 0}, "\u0627\uFEFF", RTL, LEFT_EDGE);
    LayoutTest({10, 0}, {0, 0}, "\u0627\uFEFF", RTL, RIGHT_EDGE);
    LayoutTest({10, 0}, {0, 0}, "\u0627\uFEFF", RTL, WHOLE_LINE);

    LayoutTest({10, 0, 10}, {0, 10, 10}, "\u0627\uFEFF\u0627", RTL, NONE);
    LayoutTest({10, 0, 10}, {0, 10, 10}, "\u0627\uFEFF\u0627", RTL, LEFT_EDGE);
    LayoutTest({10, 0, 10}, {0, 10, 10}, "\u0627\uFEFF\u0627", RTL, RIGHT_EDGE);
    LayoutTest({10, 0, 10}, {0, 10, 10}, "\u0627\uFEFF\u0627", RTL, WHOLE_LINE);

    LayoutTest({0, 10}, {0, 10}, "\uFEFF\u0627", RTL, NONE);
    LayoutTest({0, 10}, {0, 10}, "\uFEFF\u0627", RTL, LEFT_EDGE);
    LayoutTest({0, 10}, {0, 10}, "\uFEFF\u0627", RTL, RIGHT_EDGE);
    LayoutTest({0, 10}, {0, 10}, "\uFEFF\u0627", RTL, WHOLE_LINE);

    LayoutTest({0, 10, 0}, {0, 0, 10}, "\uFEFF\u0627\uFEFF", RTL, NONE);
    LayoutTest({0, 10, 0}, {0, 0, 10}, "\uFEFF\u0627\uFEFF", RTL, LEFT_EDGE);
    LayoutTest({0, 10, 0}, {0, 0, 10}, "\uFEFF\u0627\uFEFF", RTL, RIGHT_EDGE);
    LayoutTest({0, 10, 0}, {0, 0, 10}, "\uFEFF\u0627\uFEFF", RTL, WHOLE_LINE);
}

}  // namespace minikin
