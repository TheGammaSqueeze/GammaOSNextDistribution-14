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

#include <unistd.h>

namespace {

bool CheckPageSize(int page_size) {
  return (page_size == 4 * 1024) || (page_size == 64 * 1024);
}

}  // namespace

// Tests if sysconf(_SC_CLK_TCK) returns a reasonable value.
TEST(SysconfTest, TestClkTck) {
  EXPECT_GE(sysconf(_SC_CLK_TCK), 0);
}

// Tests if sysconf(_SC_PAGESIZE) returns a reasonable value.
TEST(SysconfTest, TestPageSize) {
  const int page_size = sysconf(_SC_PAGESIZE);
  EXPECT_TRUE(CheckPageSize(page_size)) << page_size;
}

// Do the same with _SC_PAGE_SIZE, just in case.
TEST(SysconfTest, TestPage_Size) {
  const int page_size = sysconf(_SC_PAGE_SIZE);
  EXPECT_TRUE(CheckPageSize(page_size)) << page_size;
}

// Tests if sysconf(_SC_NPROCESSORS_*) returns a positive value.
TEST(SysconfTest, TestNProcessors) {
  EXPECT_GE(sysconf(_SC_NPROCESSORS_CONF), 0);
  EXPECT_GE(sysconf(_SC_NPROCESSORS_ONLN), 0);
  // The number of online processors should be smaller than or equal to the number of processors
  // physically available.
  EXPECT_LE(sysconf(_SC_NPROCESSORS_ONLN), sysconf(_SC_NPROCESSORS_CONF));
}
