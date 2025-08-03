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

#include "berberis/calling_conventions/calling_conventions_arm.h"

namespace berberis::arm {

namespace {

TEST(CallingConventions_arm, Smoke) {
  CallingConventions conv;
  ArgLocation loc;

  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationInt);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextIntArgLoc(8, 8);
  EXPECT_EQ(loc.kind, kArgLocationInt);
  EXPECT_EQ(loc.offset, 2u);

  loc = conv.GetNextIntArgLoc(8, 8);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetIntResLoc(1);
  EXPECT_EQ(loc.kind, kArgLocationInt);
  EXPECT_EQ(loc.offset, 0u);
}

TEST(CallingConventions_arm, RegAndStack) {
  CallingConventions conv;
  ArgLocation loc;

  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationInt);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextIntArgLoc(16, 8);
  EXPECT_EQ(loc.kind, kArgLocationIntAndStack);
  EXPECT_EQ(loc.offset, 2u);

  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 8u);
}

TEST(CallingConventions_arm, UnalignedStack) {
  CallingConventions conv(CallingConventions::kStackOnly, 4);
  ArgLocation loc;

  loc = conv.GetNextIntArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  // Note: we request alignment 16 here, but arm only supports alignment of 8.
  EXPECT_EQ(loc.offset, 8u);
}

TEST(CallingConventions_arm, Simd) {
  CallingConventions conv;
  ArgLocation loc;

  loc = conv.GetNextFpArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 2u);

  loc = conv.GetNextFpArgLoc(8, 8);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 4u);

  loc = conv.GetNextFpArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 1u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 8u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 12u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextFpArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 16u);
}

TEST(CallingConventions_arm, SimdRegAndStack) {
  CallingConventions conv;
  ArgLocation loc;

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 4u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 8u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationSimd);
  EXPECT_EQ(loc.offset, 12u);

  loc = conv.GetNextFpArgLoc(16, 16);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationInt);
  EXPECT_EQ(loc.offset, 0u);

  loc = conv.GetNextIntArgLoc(16, 8);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 16u);

  loc = conv.GetNextIntArgLoc(4, 4);
  EXPECT_EQ(loc.kind, kArgLocationStack);
  EXPECT_EQ(loc.offset, 32u);
}

}  // namespace

}  // namespace berberis::arm
