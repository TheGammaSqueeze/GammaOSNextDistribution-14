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

#include "berberis/calling_conventions/calling_conventions_x86_32.h"

namespace berberis::x86_32 {

namespace {

TEST(CallingConventions_x86_32, Smoke) {
  CallingConventions conv;
  ArgLocation loc;

  loc = conv.GetNextArgLoc(1, 1);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  loc = conv.GetNextArgLoc(2, 2);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(4u, loc.offset);

  loc = conv.GetNextArgLoc(8, 8);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(8u, loc.offset);

  loc = conv.GetNextArgLoc(4, 4);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(16u, loc.offset);

  loc = conv.GetNextArgLoc(1, 1);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(20u, loc.offset);

  loc = conv.GetNextArgLoc(8, 8);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(24u, loc.offset);

  loc = conv.GetNextArgLoc(4, 4);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(32u, loc.offset);

  loc = conv.GetNextArgLoc(8, 8);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(40u, loc.offset);

  loc = conv.GetNextArgLoc(4, 4);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(48u, loc.offset);

  loc = conv.GetIntResLoc(1);
  EXPECT_EQ(kArgLocationIntOut, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  loc = conv.GetFpResLoc(8);
  EXPECT_EQ(kArgLocationFp, loc.kind);
  EXPECT_EQ(0u, loc.offset);
}

}  // namespace

}  // namespace berberis::x86_32
