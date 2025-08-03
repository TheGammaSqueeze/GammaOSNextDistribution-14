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

#include "ScriptUtils.h"
#include "UnicodeUtils.h"

namespace minikin {
namespace {

struct Result {
    Result(int start, int end, hb_script_t script) : start(start), end(end), script(script) {}
    int start;
    int end;
    hb_script_t script;
};

bool operator==(const Result& l, const Result& r) {
    return l.start == r.start && l.end == r.end && l.script == r.script;
}

std::ostream& operator<<(std::ostream& os, const Result& r) {
    char buf[5] = {};
    buf[0] = static_cast<char>((r.script >> 24) & 0xFF);
    buf[1] = static_cast<char>((r.script >> 16) & 0xFF);
    buf[2] = static_cast<char>((r.script >> 8) & 0xFF);
    buf[3] = static_cast<char>((r.script) & 0xFF);
    return os << "(" << r.start << "," << r.end << "): " << buf;
}

std::vector<Result> splitByScript(const std::vector<uint16_t>& text, uint32_t start, uint32_t end) {
    std::vector<Result> result;
    for (const auto [range, script] : ScriptText(text, start, end)) {
        result.emplace_back(range.getStart(), range.getEnd(), script);
    }
    return result;
}

std::vector<Result> splitByScript(const std::string& text, uint32_t start, uint32_t end) {
    std::vector<uint16_t> utf16 = utf8ToUtf16(text);
    return splitByScript(utf16, start, end);
}

TEST(ScriptUtilsTest, Latin) {
    auto result = splitByScript("abcde", 0, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 5, HB_SCRIPT_LATIN), result[0]);

    result = splitByScript("abcde", 0, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 3, HB_SCRIPT_LATIN), result[0]);

    result = splitByScript("abcde", 2, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(2, 5, HB_SCRIPT_LATIN), result[0]);

    result = splitByScript("abcde", 2, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(2, 3, HB_SCRIPT_LATIN), result[0]);
}

TEST(ScriptUtilsTest, Arabic) {
    auto result = splitByScript("\u0645\u0631\u062D\u0628\u064B\u0627", 0, 6);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 6, HB_SCRIPT_ARABIC), result[0]);

    result = splitByScript("\u0645\u0631\u062D\u0628\u064B\u0627", 0, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 3, HB_SCRIPT_ARABIC), result[0]);

    result = splitByScript("\u0645\u0631\u062D\u0628\u064B\u0627", 2, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(2, 5, HB_SCRIPT_ARABIC), result[0]);

    result = splitByScript("\u0645\u0631\u062D\u0628\u064B\u0627", 2, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(2, 3, HB_SCRIPT_ARABIC), result[0]);
}

TEST(ScriptUtilsTest, Common) {
    auto result = splitByScript("     ", 0, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 5, HB_SCRIPT_COMMON), result[0]);

    result = splitByScript("     ", 0, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 3, HB_SCRIPT_COMMON), result[0]);

    result = splitByScript("     ", 2, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(2, 5, HB_SCRIPT_COMMON), result[0]);

    result = splitByScript("     ", 2, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(2, 3, HB_SCRIPT_COMMON), result[0]);
}

TEST(ScriptUtilsTest, InheritOrCommon) {
    // Parens are inherit which is inherit from the previous script. If there is no character
    // before, use the next non-inherit type of script.
    auto result = splitByScript("(abc)", 0, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 5, HB_SCRIPT_LATIN), result[0]);

    result = splitByScript("[(b)]", 0, 5);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 5, HB_SCRIPT_LATIN), result[0]);

    result = splitByScript("[(b)]", 0, 2);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 2, HB_SCRIPT_COMMON), result[0]);
}

TEST(ScriptUtilsTest, MultiScript_InheritOrCommon) {
    auto result = splitByScript("a(\u0645)e", 0, 5);
    EXPECT_EQ(Result(0, 2, HB_SCRIPT_LATIN), result[0]);
    EXPECT_EQ(Result(2, 4, HB_SCRIPT_ARABIC), result[1]);
    EXPECT_EQ(Result(4, 5, HB_SCRIPT_LATIN), result[2]);
}

TEST(ScriptUtilsTest, MultiScript_NoInheritOrCommon) {
    auto result = splitByScript("a\u0645b\u0631c", 0, 5);
    EXPECT_EQ(Result(0, 1, HB_SCRIPT_LATIN), result[0]);
    EXPECT_EQ(Result(1, 2, HB_SCRIPT_ARABIC), result[1]);
    EXPECT_EQ(Result(2, 3, HB_SCRIPT_LATIN), result[2]);
    EXPECT_EQ(Result(3, 4, HB_SCRIPT_ARABIC), result[3]);
    EXPECT_EQ(Result(4, 5, HB_SCRIPT_LATIN), result[4]);
}

TEST(ScriptUtilsTest, SurrogatePair) {
    auto result = splitByScript(std::vector<uint16_t>({0xD83C, 0xDFF3, 0xD83C, 0xDFF3}), 0, 4);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 4, HB_SCRIPT_COMMON), result[0]);

    result = splitByScript(std::vector<uint16_t>({0xD83C, 0xDFF3, 0xD83C, 0xDFF3}), 0, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(0, 3, HB_SCRIPT_COMMON), result[0]);

    result = splitByScript(std::vector<uint16_t>({0xD83C, 0xDFF3, 0xD83C, 0xDFF3}), 1, 4);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(1, 4, HB_SCRIPT_COMMON), result[0]);

    result = splitByScript(std::vector<uint16_t>({0xD83C, 0xDFF3, 0xD83C, 0xDFF3}), 1, 3);
    ASSERT_EQ(1u, result.size());
    EXPECT_EQ(Result(1, 3, HB_SCRIPT_COMMON), result[0]);
}
}  // namespace
}  // namespace minikin
