// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/string_util.h"

#include <string>
#include <vector>

namespace gestures {

class StringUtilTest : public ::testing::Test {};

// This test adds code coverage to string_util.

TEST(StringUtilTest, SimpleTest) {
  const char *pstr =
    "0123456789012345678901234567890123456789012345678901234567890123456789";
  std::string str = StringPrintf(
    " %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s ",
    pstr, pstr, pstr, pstr, pstr,
    pstr, pstr, pstr, pstr, pstr,
    pstr, pstr, pstr, pstr, pstr
  );
  int expected_length = (70*15)+15+1;
  EXPECT_EQ(str.size(), expected_length);

  TrimPositions trimmed_from = TrimWhitespaceASCII(str, TRIM_ALL, &str);
  EXPECT_EQ(trimmed_from, TRIM_ALL);
  EXPECT_EQ(str.size(), expected_length-2);

  std::vector<std::string> split;
  SplitString(str, ' ', &split);
  EXPECT_EQ(split.size(), 15);

  bool matches = StartsWithASCII(split[0], pstr, true);
  EXPECT_TRUE(matches);
}

}  // namespace gestures
