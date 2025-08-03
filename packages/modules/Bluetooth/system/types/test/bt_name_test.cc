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

#include "stack/include/bt_name.h"

#include <gtest/gtest.h>

TEST(BtNameTest, new_name) {
  BD_NAME bd_name1 = {};
  bd_name_from_char_pointer(bd_name1, "ATestName");

  BD_NAME bd_name2 = {};
  bd_name_from_char_pointer(bd_name2, "ATestName");

  BD_NAME bd_name3 = {};
  bd_name_from_char_pointer(bd_name3, "ADifferentTestName");

  ASSERT_TRUE(bd_name_is_equal(bd_name1, bd_name2));
  ASSERT_FALSE(bd_name_is_equal(bd_name1, bd_name3));
}

TEST(BtNameTest, new_name_nullptr) {
  BD_NAME bd_name1 = {0};
  bd_name_from_char_pointer(bd_name1, nullptr);
  ASSERT_TRUE(bd_name_is_empty(bd_name1));

  BD_NAME bd_name2 = {};
  bd_name_from_char_pointer(bd_name2, "ARealTestName");
  bd_name_from_char_pointer(bd_name2, nullptr);
  ASSERT_FALSE(bd_name_is_empty(bd_name2));
}

TEST(BtNameTest, clear) {
  BD_NAME bd_name = {};
  bd_name_from_char_pointer(bd_name, "ATestName");

  bd_name_clear(bd_name);
  ASSERT_TRUE(bd_name_is_empty(bd_name));
}

TEST(BtNameTest, copy_name) {
  BD_NAME bd_name1 = {};
  bd_name_from_char_pointer(bd_name1, "OldName");

  BD_NAME bd_name2 = {};
  bd_name_from_char_pointer(bd_name2, "ATestName");

  const size_t len = bd_name_copy(bd_name1, bd_name2);

  ASSERT_EQ(strlen(reinterpret_cast<const char*>(bd_name2)), len);
  ASSERT_TRUE(bd_name_is_equal(bd_name1, bd_name2));
}

TEST(BtNameTest, copy_name_max_chars) {
  BD_NAME bd_name1 = {};
  std::string s(kBdNameLength, 'a');
  s.replace(kBdNameLength - 1, 1, std::string("b"));
  bd_name_from_char_pointer(bd_name1, s.data());
  ASSERT_EQ('\0', bd_name1[kBdNameLength]);

  BD_NAME bd_name2 = {};

  const size_t len = bd_name_copy(bd_name2, bd_name1);
  ASSERT_EQ('\0', bd_name2[kBdNameLength]);
  ASSERT_EQ('b', bd_name2[kBdNameLength - 1]);

  ASSERT_EQ(strlen(reinterpret_cast<const char*>(bd_name1)), len);
  ASSERT_EQ(kBdNameLength, len);
  ASSERT_TRUE(bd_name_is_equal(bd_name1, bd_name2));
}

TEST(BtNameTest, too_many_characters) {
  std::string s(kBdNameLength + 1, 'a');
  BD_NAME bd_name1 = {};
  bd_name_from_char_pointer(bd_name1, s.data());
  ASSERT_EQ('\0', bd_name1[kBdNameLength]);

  ASSERT_EQ(kBdNameLength + 1, s.length());
  ASSERT_EQ(kBdNameLength, strlen(reinterpret_cast<const char*>(bd_name1)));
}
