/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "encoder.h"

#include <gtest/gtest.h>

#include <cstdint>
#include <limits>
#include <string>

namespace dist_proc {
namespace aggregation {
namespace encoding {

namespace {

////////////////////////////////////////////////////////////////////////////////
// ------------------------ Tests for AppendToString ------------------------ //

struct EncodedTupleParam {
    int64_t input;
    const char* encoding;
    uint8_t encoding_length;
};

class EncodingCorrectnessTest : public ::testing::TestWithParam<EncodedTupleParam> {};

TEST_P(EncodingCorrectnessTest, CorrectEncoding) {
    EncodedTupleParam params = GetParam();
    std::string s;
    Encoder::AppendToString(params.input, &s);

    std::string_view expected(params.encoding, params.encoding_length);
    EXPECT_EQ(expected, s);
    EXPECT_EQ(params.encoding_length, s.length());
}

TEST_P(EncodingCorrectnessTest, CorrectAppendedEncoding) {
    EncodedTupleParam params = GetParam();
    std::string_view suffix(params.encoding, params.encoding_length);
    std::string with_prefix("thisisaprefix");

    Encoder::AppendToString(params.input, &with_prefix);
    EXPECT_EQ(with_prefix.length(), params.encoding_length + strlen("thisisaprefix"));
    EXPECT_EQ("thisisaprefix", with_prefix.substr(0, strlen("thisisaprefix")));
    EXPECT_EQ(suffix, with_prefix.substr(strlen("thisisaprefix")));
}

const EncodedTupleParam cases[] = {
        {0x0LL, "\0", 1},
        {0x1LL, "\x1\0", 1},
        {0xALL, "\xA\0", 1},
        {0x80LL, "\x80\x01", 2},
        {0x4000LL, "\x80\x80\x01", 3},
        {0x200000LL, "\x80\x80\x80\x01", 4},
        {0x10000000LL, "\x80\x80\x80\x80\x01", 5},
        {0xFFFFFFFFLL, "\xFF\xFF\xFF\xFF\x0F", 5},
        {0x03FFFFFFFFLL, "\xFF\xFF\xFF\xFF\x3F", 5},
        {0x800000000LL, "\x80\x80\x80\x80\x80\x01", 6},
        {0x40000000000LL, "\x80\x80\x80\x80\x80\x80\x01", 7},
        {0x2000000000000LL, "\x80\x80\x80\x80\x80\x80\x80\x01", 8},
        {0x100000000000000LL, "\x80\x80\x80\x80\x80\x80\x80\x80\x01", 9},
        {-0x01LL, "\xFF\xFF\xFF\xFF\xFF\xFF\xFF\xFF\xFF\x01", 10},
        {std::numeric_limits<int64_t>::min(), "\x80\x80\x80\x80\x80\x80\x80\x80\x80\x1", 10},
        {std::numeric_limits<int64_t>::max(), "\xFF\xFF\xFF\xFF\xFF\xFF\xFF\xFF\x7F", 9}};

INSTANTIATE_TEST_SUITE_P(EncodingCorrectnessTestCases, EncodingCorrectnessTest,
                         ::testing::ValuesIn(cases));

////////////////////////////////////////////////////////////////////////////////
// ------------------ Tests for SerializeToPackedStringAll ------------------ //

struct PackedEncodingTupleParam {
    std::vector<int64_t> values;
    const char* encoding;
    uint8_t encoding_length;
};

class PackedSerializationTest : public ::testing::TestWithParam<PackedEncodingTupleParam> {};

TEST_P(PackedSerializationTest, CorrectPackedEncoding) {
    PackedEncodingTupleParam params = GetParam();
    std::string packed;

    Encoder::SerializeToPackedStringAll(params.values.begin(), params.values.end(), &packed);
    std::string_view expected(params.encoding, params.encoding_length);
    EXPECT_EQ(packed, expected);
    EXPECT_EQ(packed.length(), params.encoding_length);
}

const PackedEncodingTupleParam packedCases[] = {
        {{}, "", 0},
        // Encoding one item should be identical to AppendToString.
        {{0x0LL}, "\0", 1},
        {{0x1LL}, "\x1\0", 1},
        {{0x80LL}, "\x80\x01", 2},
        {{0x200000LL}, "\x80\x80\x80\x01", 4},
        {{0x10000000LL}, "\x80\x80\x80\x80\x01", 5},
        {{0x03FFFFFFFFLL}, "\xFF\xFF\xFF\xFF\x3F", 5},
        // Combining items from above.
        {{0x1LL, 0x80LL}, "\x1\x80\x1", 3},
        {{0x200000LL, 0x03FFFFFFFFLL}, "\x80\x80\x80\x1\xFF\xFF\xFF\xFF?", 9},
        {{0x1LL, 0x80LL, 0x200000LL, 0x03FFFFFFFFLL},
         "\x1\x80\x1\x80\x80\x80\x1\xFF\xFF\xFF\xFF?",
         12},
        // Multiple items.
        {{1, 0xdeadbeef, 0x0aaabbbbccccddddL, 5},
         "\x1\xEF\xFD\xB6\xF5\r\xDD\xBB\xB3\xE6\xBC\xF7\xAE\xD5\n\x5",
         16},
        {{1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB}, "\x1\x2\x3\x4\x5\x6\a\b\t\n\v", 11}};

INSTANTIATE_TEST_SUITE_P(PackedSerializationTestCases, PackedSerializationTest,
                         ::testing::ValuesIn(packedCases));

TEST(EncoderTest, SerializeToPackedStringAllClearsPrepopulatedString) {
    std::string empty;
    std::string prepopulated = "some leftovers";
    std::vector<int64_t> v = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB};
    Encoder::SerializeToPackedStringAll(v.begin(), v.end(), &empty);
    Encoder::SerializeToPackedStringAll(v.begin(), v.end(), &prepopulated);
    EXPECT_EQ(prepopulated, "\x1\x2\x3\x4\x5\x6\a\b\t\n\v");

    EXPECT_EQ(empty, prepopulated);
}

}  // namespace

}  // namespace encoding
}  // namespace aggregation
}  // namespace dist_proc
