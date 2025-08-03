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

#include <gtest/gtest.h>

#include "stack/include/bt_dev_class.h"

class StackIncludeTest : public ::testing::Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(StackIncludeTest, dev_class_simple_zeros) {
  int mn = 0;
  int mj = 0;
  int sv = 0;
  DEV_CLASS dev_class{0xff};
  FIELDS_TO_COD(dev_class, mn, mj, sv);
  BTM_COD_MINOR_CLASS(mn, dev_class);
  BTM_COD_MAJOR_CLASS(mj, dev_class);
  BTM_COD_SERVICE_CLASS(sv, dev_class);
  ASSERT_EQ(0, mn);
  ASSERT_EQ(0, mj);
  ASSERT_EQ(0, sv);
}

TEST_F(StackIncludeTest, dev_class_simple_ones) {
  int mn = 0xff;
  int mj = 0xff;
  int sv = 0xffff;
  DEV_CLASS dev_class{0x00};
  FIELDS_TO_COD(dev_class, mn, mj, sv);
  BTM_COD_MINOR_CLASS(mn, dev_class);
  BTM_COD_MAJOR_CLASS(mj, dev_class);
  BTM_COD_SERVICE_CLASS(sv, dev_class);
  ASSERT_EQ(252, mn & BTM_COD_MINOR_CLASS_MASK);
  ASSERT_EQ(31, mj & BTM_COD_MAJOR_CLASS_MASK);
  ASSERT_EQ(65472, sv);
}

TEST_F(StackIncludeTest, dev_class_text) {
  int mn = 0xff;
  int mj = 0xff;
  int sv = 0xffff;
  DEV_CLASS dev_class;
  FIELDS_TO_COD(dev_class, mn, mj, sv);
  ASSERT_STREQ("31-252-65472", dev_class_text(dev_class).c_str());
}
