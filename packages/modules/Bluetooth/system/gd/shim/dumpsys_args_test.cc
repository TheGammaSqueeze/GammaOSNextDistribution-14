/*
 * Copyright 2020 The Android Open Source Project
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

#include "shim/dumpsys_args.h"
#include "shim/dumpsys.h"

#include <gtest/gtest.h>

using namespace bluetooth;

namespace testing {

TEST(DumpsysArgsTest, no_args) {
  shim::ParsedDumpsysArgs parsed_dumpsys_args(nullptr);
  ASSERT_FALSE(parsed_dumpsys_args.IsDeveloper());
}

TEST(DumpsysArgsTest, parsed_args_without_dev) {
  const char* args[]{
      nullptr,
  };
  shim::ParsedDumpsysArgs parsed_dumpsys_args(args);
  ASSERT_FALSE(parsed_dumpsys_args.IsDeveloper());
}

TEST(DumpsysArgsTest, parsed_args_with_dev) {
  const char* args[]{
      bluetooth::shim::kArgumentDeveloper,
      nullptr,
  };
  shim::ParsedDumpsysArgs parsed_dumpsys_args(args);
  ASSERT_TRUE(parsed_dumpsys_args.IsDeveloper());
}

}  // namespace testing
