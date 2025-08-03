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

#include "berberis/calling_conventions/calling_conventions_x86_64.h"

namespace berberis::x86_64 {

namespace {

TEST(CallingConventions_x86_64, Smoke) {
  CallingConventions conv;
  ArgLocation loc;

  loc = conv.GetNextIntArgLoc(1, 1);
  EXPECT_EQ(kArgLocationInt, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  loc = conv.GetNextIntArgLoc(16, 16);
  EXPECT_EQ(kArgLocationInt, loc.kind);
  EXPECT_EQ(1u, loc.offset);

  loc = conv.GetNextIntArgLoc(8, 8);
  EXPECT_EQ(kArgLocationInt, loc.kind);
  EXPECT_EQ(3u, loc.offset);

  loc = conv.GetNextIntArgLoc(16, 16);
  EXPECT_EQ(kArgLocationInt, loc.kind);
  EXPECT_EQ(4u, loc.offset);

  loc = conv.GetNextIntArgLoc(1, 1);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  loc = conv.GetNextIntArgLoc(1, 1);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(8u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(1u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(2u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(3u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(4u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(5u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(6u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(7u, loc.offset);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(16u, loc.offset);

  loc = conv.GetIntResLoc(1);
  EXPECT_EQ(kArgLocationIntOut, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  loc = conv.GetFpResLoc(8);
  EXPECT_EQ(kArgLocationSimd, loc.kind);
  EXPECT_EQ(0u, loc.offset);
}

TEST(CallingConventions_x86_64, LastIntRegUsed) {
  CallingConventions conv;
  ArgLocation loc;

  // Use 5 of 6 int regs.
  conv.GetNextIntArgLoc(4, 4);
  conv.GetNextIntArgLoc(4, 4);
  conv.GetNextIntArgLoc(4, 4);
  conv.GetNextIntArgLoc(4, 4);
  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(kArgLocationInt, loc.kind);
  EXPECT_EQ(4u, loc.offset);

  // Add param that doesn't fit in the last reg.
  loc = conv.GetNextIntArgLoc(16, 16);
  EXPECT_EQ(kArgLocationStack, loc.kind);
  EXPECT_EQ(0u, loc.offset);

  // Add param that fits in the last reg.
  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(kArgLocationInt, loc.kind);
  EXPECT_EQ(5u, loc.offset);
}

}  // namespace

}  // namespace berberis::x86_64
