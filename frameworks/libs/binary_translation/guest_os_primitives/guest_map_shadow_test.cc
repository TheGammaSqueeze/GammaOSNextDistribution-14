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

#include <memory>

#include "berberis/base/large_mmap.h"
#include "berberis/guest_os_primitives/guest_map_shadow.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

class GuestMapShadowTest : public ::testing::Test {
 protected:
  void SetUp() override {
    ::testing::Test::SetUp();
    InitLargeMmap();
  }
};

constexpr GuestAddr kGuestAddr = GuestAddr{0x7f018000};
constexpr size_t kGuestRegionSize = GuestAddr{0x00020000};

TEST_F(GuestMapShadowTest, smoke) {
  auto shadow = std::make_unique<GuestMapShadow>();

  ASSERT_EQ(kBitUnset, shadow->GetExecutable(kGuestAddr, kGuestRegionSize));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, kGuestRegionSize));

  shadow->SetExecutable(kGuestAddr, kGuestRegionSize / 2);

  ASSERT_EQ(kBitMixed, shadow->GetExecutable(kGuestAddr, kGuestRegionSize));
  ASSERT_EQ(kBitSet, shadow->GetExecutable(kGuestAddr, kGuestRegionSize / 2));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kGuestRegionSize / 2));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, kGuestRegionSize));

  shadow->SetExecutable(kGuestAddr, kGuestRegionSize);
  ASSERT_EQ(kBitSet, shadow->GetExecutable(kGuestAddr, kGuestRegionSize));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kGuestRegionSize / 2));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kGuestRegionSize));

  shadow->ClearExecutable(kGuestAddr, kGuestRegionSize * 2);
  ASSERT_EQ(kBitUnset, shadow->GetExecutable(kGuestAddr, kGuestRegionSize));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, kGuestRegionSize / 2));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, kGuestRegionSize));
}

TEST_F(GuestMapShadowTest, remap) {
  constexpr GuestAddr kRemapAddr = 0x00107000;
  constexpr size_t kRemapRegionSize1 = kGuestRegionSize / 2;
  constexpr size_t kRemapRegionSize2 = kGuestRegionSize * 2;

  auto shadow = std::make_unique<GuestMapShadow>();

  shadow->SetExecutable(kGuestAddr, kGuestRegionSize);
  ASSERT_EQ(kBitSet, shadow->GetExecutable(kGuestAddr, kGuestRegionSize));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kGuestRegionSize / 2));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kGuestRegionSize));

  shadow->RemapExecutable(kGuestAddr, kGuestRegionSize, kRemapAddr, kRemapRegionSize1);
  ASSERT_EQ(kBitUnset, shadow->GetExecutable(kGuestAddr, kGuestRegionSize));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, kGuestRegionSize / 2));
  ASSERT_TRUE(!shadow->IsExecutable(kGuestAddr, kGuestRegionSize));

  ASSERT_EQ(kBitSet, shadow->GetExecutable(kRemapAddr, kRemapRegionSize1));
  ASSERT_TRUE(shadow->IsExecutable(kRemapAddr, 1));
  ASSERT_TRUE(shadow->IsExecutable(kRemapAddr, kRemapRegionSize1));

  shadow->RemapExecutable(kRemapAddr, kRemapRegionSize1, kGuestAddr, kRemapRegionSize2);
  ASSERT_EQ(kBitUnset, shadow->GetExecutable(kRemapAddr, kRemapRegionSize1));
  ASSERT_TRUE(!shadow->IsExecutable(kRemapAddr, 1));
  ASSERT_TRUE(!shadow->IsExecutable(kRemapAddr, kRemapRegionSize1));

  ASSERT_EQ(kBitSet, shadow->GetExecutable(kGuestAddr, kRemapRegionSize2));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, 1));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kRemapRegionSize2 / 2));
  ASSERT_TRUE(shadow->IsExecutable(kGuestAddr, kRemapRegionSize2));
}

TEST_F(GuestMapShadowTest, ProtectedMappings) {
  const char* kStart = ToHostAddr<char>(0x00107000);
  const char* kEnd = kStart + kGuestRegionSize;
  const size_t kHalf = kGuestRegionSize / 2;

  auto shadow = std::make_unique<GuestMapShadow>();

  shadow->AddProtectedMapping(kStart, kEnd);

  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kStart, kEnd));

  // Intersecting mappings are also protected.
  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kStart - kHalf, kEnd - kHalf));
  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kStart + kHalf, kEnd + kHalf));

  // Adjacent mappings are not protected.
  EXPECT_FALSE(shadow->IntersectsWithProtectedMapping(kStart - kGuestRegionSize, kStart));
  EXPECT_FALSE(shadow->IntersectsWithProtectedMapping(kEnd, kEnd + kGuestRegionSize));

  // Add and test another mapping.

  const char* kAnotherStart = kStart + kGuestRegionSize;
  const char* kAnotherEnd = kAnotherStart + kGuestRegionSize;
  shadow->AddProtectedMapping(kAnotherStart, kAnotherEnd);

  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kAnotherStart, kAnotherEnd));

  // Intersecting mappings, including those that span across
  // multiple protected mappings, are also protected.
  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kAnotherStart - kHalf, kAnotherEnd - kHalf));
  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kAnotherStart + kHalf, kAnotherEnd + kHalf));
  EXPECT_TRUE(shadow->IntersectsWithProtectedMapping(kStart - kHalf, kAnotherEnd + kHalf));

  // Adjacent mappings, including between the protected mappings, are not protected.
  EXPECT_FALSE(shadow->IntersectsWithProtectedMapping(kEnd, kAnotherStart));
  EXPECT_FALSE(shadow->IntersectsWithProtectedMapping(kAnotherEnd, kAnotherEnd + kGuestRegionSize));
}

}  // namespace

}  // namespace berberis
