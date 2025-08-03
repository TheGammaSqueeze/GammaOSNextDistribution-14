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

#include "gtest/gtest.h"

#include "berberis/base/format_buffer.h"

#include <string>

namespace berberis {

namespace {

TEST(FormatBufferTest, NullBuffer) {
  EXPECT_EQ(0U, FormatBuffer(nullptr, 0, "test"));
  EXPECT_EQ(0U, FormatBuffer(nullptr, 128, "test"));
}

TEST(FormatBufferTest, ZeroBufferSize) {
  char buf[128] = "hello";
  EXPECT_EQ(0U, FormatBuffer(buf, 0, "test"));
  // Should write nothing.
  EXPECT_STREQ("hello", buf);
}

TEST(FormatBufferTest, NullFormat) {
  char buf[128] = "hello";
  EXPECT_EQ(0U, FormatBuffer(buf, sizeof(buf), nullptr));
  // Should write '\0'.
  // TODO(eaeltsin): maybe "(null)"?
  EXPECT_STREQ("", buf);
}

TEST(FormatBufferTest, EmptyFormat) {
  char buf[128] = "hello";
  EXPECT_EQ(0U, FormatBuffer(buf, sizeof(buf), ""));
  // Should write '\0'.
  EXPECT_STREQ("", buf);
}

TEST(FormatBufferTest, FixedFormat) {
  char buf[128];
  EXPECT_EQ(4U, FormatBuffer(buf, sizeof(buf), "test"));
  EXPECT_STREQ("test", buf);
}

TEST(FormatBufferTest, FixedFormatSmallBuffer) {
  char buf[4];
  EXPECT_EQ(3U, FormatBuffer(buf, sizeof(buf), "test"));
  EXPECT_STREQ("tes", buf);
}

TEST(FormatBufferTest, SpecMissing) {
  char buf[128];
  // Should print nothing for missing specifier.
  EXPECT_EQ(5U, FormatBuffer(buf, sizeof(buf), "test %"));
  EXPECT_STREQ("test ", buf);
}

TEST(FormatBufferTest, SpecUnknown) {
  char buf[128];
  // ATTENTION: assume '?' is not a valid specifier!
  // Should print nothing for unknown specifier.
  EXPECT_EQ(5U, FormatBuffer(buf, sizeof(buf), "test %?"));
  EXPECT_STREQ("test ", buf);
}

TEST(FormatBufferTest, SpecPercent) {
  char buf[128];
  EXPECT_EQ(6U, FormatBuffer(buf, sizeof(buf), "%% test"));
  EXPECT_STREQ("% test", buf);
}

TEST(FormatBufferTest, SpecString) {
  char buf[128];
  EXPECT_EQ(11U, FormatBuffer(buf, sizeof(buf), "%s test", "string"));
  EXPECT_STREQ("string test", buf);
}

TEST(FormatBufferTest, SpecStringNull) {
  char buf[128];
  EXPECT_EQ(11U, FormatBuffer(buf, sizeof(buf), "%s test", nullptr));
  EXPECT_STREQ("(null) test", buf);
}

TEST(FormatBufferTest, SpecStringSmallBuffer) {
  char buf[10];
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "%s test", "string"));
  EXPECT_STREQ("string te", buf);
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "test %s", "string"));
  EXPECT_STREQ("test stri", buf);
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "test %s", nullptr));
  EXPECT_STREQ("test (nul", buf);
}

TEST(FormatBufferTest, SpecDec) {
  char buf[128];
  EXPECT_EQ(6U, FormatBuffer(buf, sizeof(buf), "%d test", 0));
  EXPECT_STREQ("0 test", buf);
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%d test", 123));
  EXPECT_STREQ("123 test", buf);
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "%d test", -123));
  EXPECT_STREQ("-123 test", buf);
}

TEST(FormatBufferTest, SpecDecSmallBuffer) {
  char buf[4];
  EXPECT_EQ(3U, FormatBuffer(buf, sizeof(buf), "%d test", 123456));
  EXPECT_STREQ("123", buf);
  EXPECT_EQ(3U, FormatBuffer(buf, sizeof(buf), "%d test", -123456));
  EXPECT_STREQ("-12", buf);
}

TEST(FormatBufferTest, SpecHex) {
  char buf[128];
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%x test", 0xabc));
  EXPECT_STREQ("abc test", buf);
  // Max hex digits count for unsigned (2 hex digits per byte).
  const size_t n = sizeof(unsigned) * 2;
  EXPECT_EQ(n, FormatBuffer(buf, sizeof(buf), "%x", static_cast<unsigned>(-1)));
  for (size_t i = 0; i < n; ++i) {
    // Abort on first mismatch.
    ASSERT_EQ('f', buf[i]);
  }
}

TEST(FormatBufferTest, SpecPtr) {
  char buf[128];
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%p test", nullptr));
  EXPECT_STREQ("0x0 test", buf);
}

TEST(FormatBufferTest, SpecMany) {
  char buf[128];
  EXPECT_EQ(12U, FormatBuffer(buf, sizeof(buf), "%p %d %s test", nullptr, 1, "2"));
  EXPECT_STREQ("0x0 1 2 test", buf);
}

TEST(FormatBufferTest, SpecLongLongUnknown) {
  char buf[128];
  // ATTENTION: assume '?' is not a valid specifier!
  // Should print nothing for unknown specifier after length modifier.
  EXPECT_EQ(5U, FormatBuffer(buf, sizeof(buf), "test %ll?"));
  EXPECT_STREQ("test ", buf);
}

TEST(FormatBufferTest, SpecLongLongHex) {
  char buf[128];
  // Max hex digits count for unsigned long long (2 hex digits per byte).
  const size_t n = sizeof(unsigned long long) * 2;
  EXPECT_EQ(n, FormatBuffer(buf, sizeof(buf), "%llx", static_cast<unsigned long long>(-1)));
  for (size_t i = 0; i < n; ++i) {
    // Abort on first mismatch.
    ASSERT_EQ('f', buf[i]);
  }
}

TEST(FormatBufferTest, SpecWidthString) {
  char buf[128];
  EXPECT_EQ(11U, FormatBuffer(buf, sizeof(buf), "%4s test", "string"));
  EXPECT_STREQ("string test", buf);
  EXPECT_EQ(13U, FormatBuffer(buf, sizeof(buf), "%8s test", "string"));
  EXPECT_STREQ("  string test", buf);
  EXPECT_EQ(21U, FormatBuffer(buf, sizeof(buf), "%16s test", "string"));
  EXPECT_STREQ("          string test", buf);
}

TEST(FormatBufferTest, SpecWidthDec) {
  char buf[128];
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%3d test", 0));
  EXPECT_STREQ("  0 test", buf);
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%2d test", 123));
  EXPECT_STREQ("123 test", buf);
  EXPECT_EQ(10U, FormatBuffer(buf, sizeof(buf), "%5d test", 123));
  EXPECT_STREQ("  123 test", buf);
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "%2d test", -123));
  EXPECT_STREQ("-123 test", buf);
  EXPECT_EQ(10U, FormatBuffer(buf, sizeof(buf), "%5d test", -123));
  EXPECT_STREQ(" -123 test", buf);
}

TEST(FormatBufferTest, SpecWidthPtr) {
  char buf[128];
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%2p test", nullptr));
  EXPECT_STREQ("0x0 test", buf);
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "%4p test", nullptr));
  EXPECT_STREQ(" 0x0 test", buf);
}

TEST(FormatBufferTest, SpecChar) {
  char buf[128];
  EXPECT_EQ(6U, FormatBuffer(buf, sizeof(buf), "%c test", 'a'));
  EXPECT_STREQ("a test", buf);
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%3c test", 'a'));
  EXPECT_STREQ("  a test", buf);
  EXPECT_EQ(10U, FormatBuffer(buf, sizeof(buf), "%c%d test", 'a', -123));
  EXPECT_STREQ("a-123 test", buf);
}

TEST(FormatBufferTest, SpecVariableWidth) {
  char buf[128];
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%*d test", 3, 0));
  EXPECT_STREQ("  0 test", buf);
  EXPECT_EQ(8U, FormatBuffer(buf, sizeof(buf), "%*d test", 3, 123));
  EXPECT_STREQ("123 test", buf);
  EXPECT_EQ(10U, FormatBuffer(buf, sizeof(buf), "%*d test", 5, 123));
  EXPECT_STREQ("  123 test", buf);
  EXPECT_EQ(9U, FormatBuffer(buf, sizeof(buf), "%*d test", 2, -123));
  EXPECT_STREQ("-123 test", buf);
  EXPECT_EQ(10U, FormatBuffer(buf, sizeof(buf), "%*d test", 5, -123));
  EXPECT_STREQ(" -123 test", buf);
  EXPECT_EQ(11U, FormatBuffer(buf, sizeof(buf), "%*s test", 4, "string"));
  EXPECT_STREQ("string test", buf);
  EXPECT_EQ(13U, FormatBuffer(buf, sizeof(buf), "%*s test", 8, "string"));
  EXPECT_STREQ("  string test", buf);
  EXPECT_EQ(21U, FormatBuffer(buf, sizeof(buf), "%*s test", 16, "string"));
  EXPECT_STREQ("          string test", buf);
}

TEST(FormatBufferTest, PadNumberWithZeroes) {
  char buf[128];
  EXPECT_EQ(1U, FormatBuffer(buf, sizeof(buf), "%0d", 1));
  EXPECT_STREQ("1", buf);
  EXPECT_EQ(4U, FormatBuffer(buf, sizeof(buf), "%04d", 1));
  EXPECT_STREQ("0001", buf);
  EXPECT_EQ(4U, FormatBuffer(buf, sizeof(buf), "%04d", -1));
  EXPECT_STREQ("-001", buf);
  EXPECT_EQ(4U, FormatBuffer(buf, sizeof(buf), "%0*d", 4, -1));
  EXPECT_STREQ("-001", buf);
  EXPECT_EQ(4U, FormatBuffer(buf, sizeof(buf), "%04s", "hi"));
  EXPECT_STREQ("  hi", buf);
}

TEST(FormatBufferTest, SpecSizeT) {
  char buf[128];
  EXPECT_EQ(3U, FormatBuffer(buf, sizeof(buf), "%zu", sizeof(buf)));
  EXPECT_STREQ("128", buf);
  EXPECT_EQ(2U, FormatBuffer(buf, sizeof(buf), "%zx", sizeof(buf)));
  EXPECT_STREQ("80", buf);
}

TEST(FormatBufferTest, DynamicCStrBuffer) {
  DynamicCStrBuffer buf;
  EXPECT_TRUE(buf.Put('c'));
  EXPECT_TRUE(std::string("c") == std::string(buf.Data(), buf.Size()));
  EXPECT_EQ(1u, buf.Size());
  EXPECT_FALSE(buf.IsDynamicForTesting());

  for (int i = 0; i < 1023; i++) {
    EXPECT_TRUE(buf.Put('c'));
  }
  EXPECT_TRUE(std::string(1024, 'c') == std::string(buf.Data(), buf.Size()));
  EXPECT_EQ(1024u, buf.Size());
  EXPECT_TRUE(buf.IsDynamicForTesting());
}

}  // namespace

}  // namespace berberis
