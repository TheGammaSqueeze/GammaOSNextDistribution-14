/*
 * Copyright 2023 The Android Open Source Project
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

#define LOG_TAG "test"

#include "truncating_buffer.h"

#include <fmt/format.h>
#include <gtest/gtest.h>
#include <log/log.h>

using namespace bluetooth::log_internal;

TEST(TruncatingBufferTest, 1byte) {
  EXPECT_EQ(sizeof("ab"), 3);
  truncating_buffer<2> buffer_1;
  truncating_buffer<3> buffer_2;
  fmt::format_to(std::back_insert_iterator(buffer_1), "ab");
  fmt::format_to(std::back_insert_iterator(buffer_2), "ab");
  EXPECT_STREQ(buffer_1.c_str(), "a");
  EXPECT_STREQ(buffer_2.c_str(), "ab");
}

TEST(TruncatingBufferTest, 2bytes) {
  EXPECT_EQ(sizeof("αβ"), 5);
  truncating_buffer<3> buffer_1;
  truncating_buffer<4> buffer_2;
  truncating_buffer<5> buffer_3;
  fmt::format_to(std::back_insert_iterator(buffer_1), "αβ");
  fmt::format_to(std::back_insert_iterator(buffer_2), "αβ");
  fmt::format_to(std::back_insert_iterator(buffer_3), "αβ");
  EXPECT_STREQ(buffer_1.c_str(), "α");
  EXPECT_STREQ(buffer_2.c_str(), "α");
  EXPECT_STREQ(buffer_3.c_str(), "αβ");
}

TEST(TruncatingBufferTest, 3bytes) {
  EXPECT_EQ(sizeof("ພຮ"), 7);
  truncating_buffer<4> buffer_1;
  truncating_buffer<5> buffer_2;
  truncating_buffer<6> buffer_3;
  truncating_buffer<7> buffer_4;
  fmt::format_to(std::back_insert_iterator(buffer_1), "ພຮ");
  fmt::format_to(std::back_insert_iterator(buffer_2), "ພຮ");
  fmt::format_to(std::back_insert_iterator(buffer_3), "ພຮ");
  fmt::format_to(std::back_insert_iterator(buffer_4), "ພຮ");
  EXPECT_STREQ(buffer_1.c_str(), "ພ");
  EXPECT_STREQ(buffer_2.c_str(), "ພ");
  EXPECT_STREQ(buffer_3.c_str(), "ພ");
  EXPECT_STREQ(buffer_4.c_str(), "ພຮ");
}

TEST(TruncatingBufferTest, 4bytes) {
  EXPECT_EQ(sizeof("𐎡𐎪"), 9);
  truncating_buffer<5> buffer_1;
  truncating_buffer<6> buffer_2;
  truncating_buffer<7> buffer_3;
  truncating_buffer<8> buffer_4;
  truncating_buffer<9> buffer_5;
  fmt::format_to(std::back_insert_iterator(buffer_1), "𐎡𐎪");
  fmt::format_to(std::back_insert_iterator(buffer_2), "𐎡𐎪");
  fmt::format_to(std::back_insert_iterator(buffer_3), "𐎡𐎪");
  fmt::format_to(std::back_insert_iterator(buffer_4), "𐎡𐎪");
  fmt::format_to(std::back_insert_iterator(buffer_5), "𐎡𐎪");
  EXPECT_STREQ(buffer_1.c_str(), "𐎡");
  EXPECT_STREQ(buffer_2.c_str(), "𐎡");
  EXPECT_STREQ(buffer_3.c_str(), "𐎡");
  EXPECT_STREQ(buffer_4.c_str(), "𐎡");
  EXPECT_STREQ(buffer_5.c_str(), "𐎡𐎪");
}
