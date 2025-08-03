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

#include "common/byte_array.h"

#include <gtest/gtest.h>

#include "os/log.h"

using bluetooth::common::ByteArray;

namespace {
const char* byte_string16 = "4c68384139f574d836bcf34e9dfb01bf\0";
const uint8_t byte_data16[16] = {
    0x4c, 0x68, 0x38, 0x41, 0x39, 0xf5, 0x74, 0xd8, 0x36, 0xbc, 0xf3, 0x4e, 0x9d, 0xfb, 0x01, 0xbf};
const char* byte_string21 = "4c68384139f574d836bcf34e9dfb01bf0011223344\0";
const uint8_t byte_data21[21] = {0x4c, 0x68, 0x38, 0x41, 0x39, 0xf5, 0x74, 0xd8, 0x36, 0xbc, 0xf3,
                                 0x4e, 0x9d, 0xfb, 0x01, 0xbf, 0x00, 0x11, 0x22, 0x33, 0x44};
const char* byte_string23 = "4c68384139f574d836bcf34e9dfb01bf00112233445566\0";
const uint8_t byte_data23[23] = {0x4c, 0x68, 0x38, 0x41, 0x39, 0xf5, 0x74, 0xd8, 0x36, 0xbc, 0xf3, 0x4e,
                                 0x9d, 0xfb, 0x01, 0xbf, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
const char* byte_string28 = "4c68384139f574d836bcf34e9dfb01bf00112233445566778899aabb\0";
const uint8_t byte_data28[28] = {0x4c, 0x68, 0x38, 0x41, 0x39, 0xf5, 0x74, 0xd8, 0x36, 0xbc, 0xf3, 0x4e, 0x9d, 0xfb,
                                 0x01, 0xbf, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb};

template <typename T, size_t N>
void simple_constructor_test(const T (&data)[N]) {
  ByteArray<N> byte_array(data);
  for (size_t i = 0; i < ByteArray<N>::kLength; i++) {
    ASSERT_EQ(data[i], byte_array.bytes[i]);
  }
}

template <typename T, size_t N>
void simple_const_constructor_test(const T (&data)[N]) {
  const ByteArray<N> byte_array(data);
  for (size_t i = 0; i < ByteArray<N>::kLength; i++) {
    ASSERT_EQ(data[i], byte_array.data()[i]);
  }
}

template <typename T, size_t N>
void simple_array_constructor_test(const T (&data)[N]) {
  std::array<uint8_t, N> array_of_bytes;
  std::copy(data, data + N, std::begin(array_of_bytes));

  ByteArray<N> byte_array(array_of_bytes);
  for (size_t i = 0; i < ByteArray<N>::kLength; i++) {
    ASSERT_EQ(data[i], byte_array.data()[i]);
  }
}

template <typename T, size_t N>
void simple_from_string_test(const char* byte_string, const T (&data)[N]) {
  auto byte_array = ByteArray<N>::FromString(byte_string);
  ASSERT_TRUE(byte_array);

  for (size_t i = 0; i < ByteArray<N>::kLength; i++) {
    ASSERT_EQ(data[i], byte_array->bytes[i]);
  }
}

template <typename T, size_t N>
void simple_to_string_test(const char* byte_string, const T (&data)[N]) {
  const ByteArray<N> byte_array(data);
  std::string str = byte_array.ToString();
  ASSERT_STREQ(str.c_str(), byte_string);
}

template <typename T, size_t N>
void simple_from_legacy_string_test(const char* byte_string, const T (&data)[N]) {
  auto byte_array = ByteArray<N>::FromLegacyConfigString(byte_string);
  ASSERT_TRUE(byte_array);

  for (size_t i = 0; i < ByteArray<N>::kLength; i++) {
    ASSERT_EQ(data[i], byte_array->bytes[i]);
  }
}

template <typename T, size_t N>
void simple_to_legacy_string_test(const char* byte_string, const T (&data)[N]) {
  const ByteArray<N> byte_array(data);
  std::string str = byte_array.ToLegacyConfigString();
  ASSERT_STREQ(str.c_str(), byte_string);
}

}  // namespace

TEST(ByteArrayTest, test_simple_constructor) {
  simple_constructor_test<const uint8_t, 16>(byte_data16);
  simple_constructor_test<const uint8_t, 21>(byte_data21);
  simple_constructor_test<const uint8_t, 23>(byte_data23);
  simple_constructor_test<const uint8_t, 28>(byte_data28);
}

TEST(ByteArrayTest, test_simple_const_constructor) {
  simple_const_constructor_test<const uint8_t, 16>(byte_data16);
  simple_const_constructor_test<const uint8_t, 21>(byte_data21);
  simple_const_constructor_test<const uint8_t, 23>(byte_data23);
  simple_const_constructor_test<const uint8_t, 28>(byte_data28);
}

TEST(ByteArrayTest, test_simple_array_constructor) {
  simple_array_constructor_test<const uint8_t, 16>(byte_data16);
  simple_array_constructor_test<const uint8_t, 21>(byte_data21);
  simple_array_constructor_test<const uint8_t, 23>(byte_data23);
  simple_array_constructor_test<const uint8_t, 28>(byte_data28);
}

TEST(ByteArrayTest, test_from_str) {
  simple_from_string_test<const uint8_t, 16>(byte_string16, byte_data16);
  simple_from_string_test<const uint8_t, 21>(byte_string21, byte_data21);
  simple_from_string_test<const uint8_t, 23>(byte_string23, byte_data23);
  simple_from_string_test<const uint8_t, 28>(byte_string28, byte_data28);
}

TEST(ByteArrayTest, test_from_legacy_str) {
  simple_from_legacy_string_test<const uint8_t, 16>(byte_string16, byte_data16);
  simple_from_legacy_string_test<const uint8_t, 21>(byte_string21, byte_data21);
  simple_from_legacy_string_test<const uint8_t, 23>(byte_string23, byte_data23);
  simple_from_legacy_string_test<const uint8_t, 28>(byte_string28, byte_data28);
}

TEST(ByteArrayTest, test_to_str) {
  simple_to_string_test<const uint8_t, 16>(byte_string16, byte_data16);
  simple_to_string_test<const uint8_t, 21>(byte_string21, byte_data21);
  simple_to_string_test<const uint8_t, 23>(byte_string23, byte_data23);
  simple_to_string_test<const uint8_t, 28>(byte_string28, byte_data28);
}

TEST(ByteArrayTest, test_to_legacy_str) {
  simple_to_legacy_string_test<const uint8_t, 16>(byte_string16, byte_data16);
  simple_to_legacy_string_test<const uint8_t, 21>(byte_string21, byte_data21);
  simple_to_legacy_string_test<const uint8_t, 23>(byte_string23, byte_data23);
  simple_to_legacy_string_test<const uint8_t, 28>(byte_string28, byte_data28);
}
