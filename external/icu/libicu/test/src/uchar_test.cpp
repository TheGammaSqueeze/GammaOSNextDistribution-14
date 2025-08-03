/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <unicode/uchar.h>

TEST(Icu4cUCharTest, test_u_hasBinaryProperty) {
  ASSERT_TRUE(u_hasBinaryProperty(U' '  /* ascii space */, UCHAR_WHITE_SPACE));
  ASSERT_TRUE(u_hasBinaryProperty(8200 /* Punctuation space U+2008 */, UCHAR_WHITE_SPACE));
  ASSERT_TRUE(u_hasBinaryProperty(U'❤' /* Emoji heart U+2764 */, UCHAR_EMOJI));
  ASSERT_FALSE(u_hasBinaryProperty(U'❤' /* Emoji heart U+2764 */, UCHAR_WHITE_SPACE));
}

TEST(Icu4cUCharTest, test_u_toupper) {
  ASSERT_EQ(U'A', u_toupper(U'a'));
  ASSERT_EQ(U'A', u_toupper(U'A'));
  ASSERT_EQ(U'1', u_toupper(U'1'));
  ASSERT_EQ(U'Ë', u_toupper(U'ë'));
}
