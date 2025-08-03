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

#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/translation_cache.h"

#include "translator_riscv64.h"

namespace berberis {

namespace {

TEST(TranslatorRiscv64, LiteTranslateSupportedRegion) {
  static const uint32_t code[] = {
      0x002081b3,  // add x3, x1, x2
      0x008000ef,  // jal x1, 8
  };

  auto [success, host_code_piece, guest_size, kind] =
      TryLiteTranslateAndInstallRegion(ToGuestAddr(code));

  EXPECT_TRUE(success);
  EXPECT_NE(host_code_piece.code, nullptr);
  EXPECT_GT(host_code_piece.size, 0U);
  EXPECT_EQ(guest_size, 8U);
  EXPECT_EQ(kind, GuestCodeEntry::Kind::kLightTranslated);
}

TEST(TranslatorRiscv64, LiteTranslateUnsupportedRegion) {
  static const uint32_t code[] = {
      0x00000073,  // ecall #0x0
  };

  auto [success, host_code_piece, guest_size, kind] =
      TryLiteTranslateAndInstallRegion(ToGuestAddr(code));

  EXPECT_FALSE(success);
}

TEST(TranslatorRiscv64, LiteTranslatePartiallySupportedRegion) {
  static const uint32_t code[] = {
      0x002081b3,  // add x3, x1, x2
      0x00000073,  // ecall #0x0
  };

  auto [success, host_code_piece, guest_size, kind] =
      TryLiteTranslateAndInstallRegion(ToGuestAddr(code));

  EXPECT_TRUE(success);
  EXPECT_NE(host_code_piece.code, nullptr);
  EXPECT_GT(host_code_piece.size, 0U);
  EXPECT_EQ(guest_size, 4U);
  EXPECT_EQ(kind, GuestCodeEntry::Kind::kLightTranslated);
}

TEST(TranslatorRiscv64, HeavyOptimizeSupportedRegion) {
  static const uint32_t code[] = {
      0x008000ef,  // jal x1, 8
  };

  auto [success, host_code_piece, guest_size, kind] = HeavyOptimizeRegion(ToGuestAddr(code));

  EXPECT_TRUE(success);
  EXPECT_NE(host_code_piece.code, kEntryInterpret);
  EXPECT_GT(host_code_piece.size, 0U);
  EXPECT_EQ(guest_size, 4U);
  EXPECT_EQ(kind, GuestCodeEntry::Kind::kHeavyOptimized);
}

TEST(TranslatorRiscv64, HeavyOptimizeUnsupportedRegion) {
  static const uint32_t code[] = {
      0x0000100f,  // fence.i
  };

  auto [success, host_code_piece, guest_size, kind] = HeavyOptimizeRegion(ToGuestAddr(code));

  EXPECT_FALSE(success);
  EXPECT_NE(host_code_piece.code, kEntryInterpret);
  EXPECT_EQ(host_code_piece.size, 0U);
  EXPECT_EQ(guest_size, 0U);
  EXPECT_EQ(kind, GuestCodeEntry::Kind::kInterpreted);
}

}  // namespace

}  // namespace berberis
