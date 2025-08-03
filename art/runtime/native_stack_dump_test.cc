/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "native_stack_dump.h"

#include <gtest/gtest.h>

namespace art HIDDEN {

TEST(StripParametersTest, ValidInput) {
  EXPECT_EQ(StripParameters("foo(int)"), "foo");
  EXPECT_EQ(StripParameters("foo(int, std::string)"), "foo");
  EXPECT_EQ(StripParameters("foo(int) const"), "foo const");
  EXPECT_EQ(StripParameters("foo(int)::bar(int)"), "foo::bar");
  EXPECT_EQ(StripParameters("foo<int>(int)"), "foo<int>");
}

TEST(StripParametersTest, InvalidInput) {
  EXPECT_EQ(StripParameters("foo(int?"), "foo(int?");
  EXPECT_EQ(StripParameters("foo?int)"), "foo?int)");
  EXPECT_EQ(StripParameters("(foo(int)"), "(foo");
  EXPECT_EQ(StripParameters(")foo(int)"), ")foo");
  EXPECT_EQ(StripParameters("foo(((int)))"), "foo");
}

}  // namespace art
