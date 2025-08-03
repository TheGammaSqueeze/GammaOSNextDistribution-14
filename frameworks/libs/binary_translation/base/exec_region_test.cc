/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <utility>

#include "berberis/base/exec_region.h"
#include "berberis/base/exec_region_anonymous.h"

namespace berberis {

namespace {

TEST(ExecRegion, Move) {
  ExecRegion exec = ExecRegionAnonymousFactory::Create(1);
  const uint8_t* begin = exec.begin();
  const uint8_t* end = exec.end();

  ExecRegion other_exec = std::move(exec);

  EXPECT_EQ(other_exec.begin(), begin);
  EXPECT_EQ(other_exec.end(), end);

  EXPECT_EQ(exec.begin(), nullptr);
  EXPECT_EQ(exec.end(), nullptr);

  other_exec.Free();
}

TEST(ExecRegion, SelfMove) {
  ExecRegion exec = ExecRegionAnonymousFactory::Create(1);
  const uint8_t* begin = exec.begin();
  const uint8_t* end = exec.end();

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wself-move"
  exec = std::move(exec);
#pragma clang diagnostic pop

  EXPECT_EQ(exec.begin(), begin);
  EXPECT_EQ(exec.end(), end);

  exec.Free();
}

}  // namespace

}  // namespace berberis
