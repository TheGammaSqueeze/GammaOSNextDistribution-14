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

#include "minikin/FontFileParser.h"

#include <gtest/gtest.h>

#include "FontTestUtils.h"
#include "FreeTypeMinikinFontForTest.h"
#include "PathUtils.h"

namespace minikin {
namespace {

static size_t writeU16(uint16_t x, uint8_t* out, size_t offset) {
    out[offset] = x >> 8;
    out[offset + 1] = x;
    return offset + 2;
}

static size_t writeU32(uint32_t x, uint8_t* out, size_t offset) {
    out[offset] = x >> 24;
    out[offset + 1] = x >> 16;
    out[offset + 2] = x >> 8;
    out[offset + 3] = x;
    return offset + 4;
}

class TestableFontFileParser : public FontFileParser {
public:
    using FontFileParser::analyzeFontRevision;
    using FontFileParser::checkPSName;
};

// Returns valid head table contents.
static std::vector<uint8_t> buildHeadTable(uint32_t fontRevision) {
    std::vector<uint8_t> out(46);
    size_t head = writeU16(1, out.data(), 0);         // major version
    head = writeU16(0, out.data(), head);             // minor version
    head = writeU32(fontRevision, out.data(), head);  // fontRevision
    head = writeU32(0xB1B0AFBA, out.data(), head);    // checksum. (random value)
    head = writeU32(0x5F0F3CF5, out.data(), head);    // magicNumber
    head = writeU16(0, out.data(), head);             // flasgs
    head = writeU16(1024, out.data(), head);          // unitsPerEm
    head = writeU32(123457890, out.data(), head);     // created (random value)
    head = writeU32(123457890, out.data(), head);     // modified (random value)
    head = writeU16(0, out.data(), head);             // xMin
    head = writeU16(100, out.data(), head);           // yMin
    head = writeU16(1024, out.data(), head);          // xMax
    head = writeU16(2048, out.data(), head);          // yMax
    head = writeU16(0, out.data(), head);             // macStyle
    head = writeU16(10, out.data(), head);            // lowestRecPPEM
    head = writeU16(1, out.data(), head);             // fontDirectionHint
    head = writeU16(1, out.data(), head);             // indexToLocFormat
    head = writeU16(0, out.data(), head);             // glyphDataFormat;

    return out;
}

TEST(FontFileParserTest, analyzeFontRevision) {
    uint32_t rev = 0x12345678;
    std::vector<uint8_t> head = buildHeadTable(rev);

    uint32_t out = 0;
    EXPECT_TRUE(TestableFontFileParser::analyzeFontRevision(head.data(), head.size(), &out));
    EXPECT_EQ(rev, out);
}

TEST(FontFileParserTest, headInvalidLength) {
    uint32_t rev = 0x12345678;
    std::vector<uint8_t> head = buildHeadTable(rev);

    uint32_t out = 0;
    EXPECT_FALSE(TestableFontFileParser::analyzeFontRevision(head.data(), 6, &out));
}

TEST(FontFileParserTest, parseFontForRev) {
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(getTestFontPath("Ascii.ttf"));
    auto parser = FontFileParser(minikinFont->GetFontData(), minikinFont->GetFontSize(), 0);

    auto revision = parser.getFontRevision();
    EXPECT_TRUE(revision.has_value());
    EXPECT_EQ(0x00010000u, revision.value());
}

TEST(FontFileParser, checkPSName) {
    EXPECT_TRUE(TestableFontFileParser::checkPSName("Roboto-Regular"));
    EXPECT_TRUE(TestableFontFileParser::checkPSName("NotoColorEmoji"));

    // Space character is not allowed.
    EXPECT_FALSE(TestableFontFileParser::checkPSName("Roboto Regular"));
    EXPECT_FALSE(TestableFontFileParser::checkPSName("Noto Color Emoji"));

    // parens are not not allowed.
    EXPECT_FALSE(TestableFontFileParser::checkPSName("Roboto (Regular)"));
    EXPECT_FALSE(TestableFontFileParser::checkPSName("Noto <Color> {Emoji}"));

    // control characters are not allowed
    EXPECT_FALSE(TestableFontFileParser::checkPSName("Roboto-Regular\b"));
    EXPECT_FALSE(TestableFontFileParser::checkPSName("NotoColorEmoji\t"));

    // Up to 63 character is allowed.
    EXPECT_FALSE(TestableFontFileParser::checkPSName(std::string(64, 'a')));

    // Only printable ASCII is allowed.
    EXPECT_FALSE(TestableFontFileParser::checkPSName("ろぼとふぉんと"));
}

TEST(FontFileParserTest, parseFontForPSName) {
    auto minikinFont = std::make_shared<FreeTypeMinikinFontForTest>(getTestFontPath("Ascii.ttf"));
    auto parser = FontFileParser(minikinFont->GetFontData(), minikinFont->GetFontSize(), 0);

    auto psName = parser.getPostScriptName();
    EXPECT_TRUE(psName.has_value());
    EXPECT_EQ("SampleFont-Regular", psName.value());
}

}  // namespace
}  // namespace minikin
