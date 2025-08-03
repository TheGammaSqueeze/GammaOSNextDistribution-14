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

#include "gtest/gtest.h"

struct CppStaticInit {
  CppStaticInit() : init_(true) {}

  bool init_;
};

CppStaticInit gCppStaticInit;

TEST(Cpp, StaticInitializers) {
  EXPECT_TRUE(gCppStaticInit.init_);
}

TEST(Cpp, Exception) {
  bool res = false;
  try {
    throw true;
  } catch (bool b) {
    res = b;
  }
  ASSERT_TRUE(res);
}
