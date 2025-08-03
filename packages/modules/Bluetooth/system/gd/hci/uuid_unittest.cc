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

#include "hci/uuid.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace testing {

using bluetooth::hci::Uuid;

static const Uuid ONES = Uuid::From128BitBE(
    Uuid::UUID128Bit{{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11}});

static const Uuid SEQUENTIAL = Uuid::From128BitBE(
    Uuid::UUID128Bit{{0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef, 0xab, 0xcd, 0xef, 0x01, 0x23, 0x45, 0x67, 0x89}});

static const Uuid kBase = Uuid::From128BitBE(
    Uuid::UUID128Bit{{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb}});

TEST(UuidTest, IsEmpty) {
  ASSERT_TRUE(Uuid::kEmpty.IsEmpty());
  ASSERT_FALSE(kBase.IsEmpty());
}

TEST(UuidTest, GetShortestRepresentationSize) {
  ASSERT_TRUE(Uuid::kNumBytes16 == kBase.GetShortestRepresentationSize());
  ASSERT_TRUE(Uuid::kNumBytes32 == Uuid::From32Bit(0x01234567).GetShortestRepresentationSize());
  ASSERT_TRUE(Uuid::kNumBytes128 == Uuid::kEmpty.GetShortestRepresentationSize());
}

TEST(UuidTest, As16Bit) {
  // Even though this is is not 16bit UUID, we should be able to get proper bits
  ASSERT_EQ((uint16_t)0x1111, ONES.As16Bit());
  ASSERT_EQ((uint16_t)0x4567, SEQUENTIAL.As16Bit());
  ASSERT_EQ((uint16_t)0x0000, kBase.As16Bit());
}

TEST(UuidTest, As32Bit) {
  // Even though this is is not 32bit UUID, we should be able to get proper bits
  ASSERT_EQ((uint32_t)0x11111111, ONES.As32Bit());
  ASSERT_EQ((uint32_t)0x01234567, SEQUENTIAL.As32Bit());
  ASSERT_EQ((uint32_t)0x00000000, kBase.As32Bit());
  ASSERT_EQ((uint32_t)0x12345678, Uuid::From32Bit(0x12345678).As32Bit());
}

TEST(UuidTest, Is16Bit) {
  ASSERT_FALSE(ONES.Is16Bit());
  ASSERT_FALSE(SEQUENTIAL.Is16Bit());
  ASSERT_TRUE(kBase.Is16Bit());
  auto uuid = Uuid::FromString("1ae8");
  ASSERT_TRUE(uuid);
  ASSERT_TRUE(uuid->Is16Bit());
}

TEST(UuidTest, From16Bit) {
  ASSERT_EQ(Uuid::From16Bit(0x0000), kBase);

  const uint8_t u2[] = {0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  Uuid uuid = Uuid::From16Bit(0x0001);
  ASSERT_TRUE(memcmp(uuid.data(), u2, sizeof(u2)) == 0);

  const uint8_t u3[] = {0x00, 0x00, 0x55, 0x3e, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  uuid = Uuid::From16Bit(0x553e);
  ASSERT_TRUE(memcmp(uuid.data(), u3, sizeof(u3)) == 0);

  const uint8_t u4[] = {0x00, 0x00, 0xff, 0xff, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  uuid = Uuid::From16Bit(0xffff);
  ASSERT_TRUE(memcmp(uuid.data(), u4, sizeof(u4)) == 0);
}

TEST(UuidTest, From32Bit) {
  ASSERT_EQ(Uuid::From32Bit(0x00000000), kBase);

  const uint8_t u2[] = {0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  Uuid uuid = Uuid::From32Bit(0x00000001);
  ASSERT_TRUE(memcmp(uuid.data(), u2, sizeof(u2)) == 0);

  const uint8_t u3[] = {0x33, 0x44, 0x55, 0x3e, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  uuid = Uuid::From32Bit(0x3344553e);
  ASSERT_TRUE(memcmp(uuid.data(), u3, sizeof(u3)) == 0);

  const uint8_t u4[] = {0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  uuid = Uuid::From32Bit(0xffffffff);
  ASSERT_TRUE(memcmp(uuid.data(), u4, sizeof(u4)) == 0);
}

TEST(UuidTest, ToString) {
  const std::string UUID_BASE_STR = "00000000-0000-1000-8000-00805f9b34fb";
  const std::string UUID_EMP_STR = "00000000-0000-0000-0000-000000000000";
  const std::string UUID_ONES_STR = "11111111-1111-1111-1111-111111111111";
  const std::string UUID_SEQ_STR = "01234567-89ab-cdef-abcd-ef0123456789";

  ASSERT_EQ(UUID_BASE_STR, kBase.ToString());
  ASSERT_EQ(UUID_EMP_STR, Uuid::kEmpty.ToString());
  ASSERT_EQ(UUID_ONES_STR, ONES.ToString());
  ASSERT_EQ(UUID_SEQ_STR, SEQUENTIAL.ToString());

  Uuid uuid = Uuid::From32Bit(0x12345678);
  ASSERT_EQ("12345678-0000-1000-8000-00805f9b34fb", uuid.ToString());
}

TEST(UuidTest, test_string_to_uuid) {
  const uint8_t u1[] = {0xe3, 0x9c, 0x62, 0x85, 0x86, 0x7f, 0x4b, 0x1d, 0x9d, 0xb0, 0x35, 0xfb, 0xd9, 0xae, 0xbf, 0x22};
  auto uuid = Uuid::FromString("e39c6285-867f-4b1d-9db0-35fbd9aebf22");
  ASSERT_TRUE(uuid);
  ASSERT_TRUE(memcmp(uuid->data(), u1, sizeof(u1)) == 0);

  const uint8_t u2[] = {0x00, 0x00, 0x1a, 0xe8, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  uuid = Uuid::FromString("1Ae8");
  ASSERT_TRUE(uuid);
  ASSERT_TRUE(memcmp(uuid->data(), u2, sizeof(u2)) == 0);

  const uint8_t u3[] = {0x12, 0x34, 0x11, 0x28, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb};
  uuid = Uuid::FromString("12341128");
  ASSERT_TRUE(uuid);
  ASSERT_TRUE(memcmp(uuid->data(), u3, sizeof(u3)) == 0);
}

TEST(UuidTest, test_string_to_uuid_invalid) {
  ASSERT_FALSE(Uuid::FromString("This is not a UUID"));
  ASSERT_FALSE(Uuid::FromString("11212"));
  ASSERT_FALSE(Uuid::FromString("1121 "));
  ASSERT_FALSE(Uuid::FromString("AGFE"));
  ASSERT_FALSE(Uuid::FromString("ABFG"));
  ASSERT_FALSE(Uuid::FromString("e39c6285867f14b1d9db035fbd9aebf22"));
  ASSERT_FALSE(Uuid::FromString("12234567-89ab-cdef-abcd-ef01234567ZZ"));
}

}  // namespace testing
