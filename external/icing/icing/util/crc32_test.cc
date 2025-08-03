// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/util/crc32.h"

#include <cstdint>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/portable/zlib.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {
using ::testing::Eq;

void UpdateAtRandomOffset(std::string* buf, uint32_t* update_xor, int* offset) {
  // The max value of rand() is 2^31 - 1 (2147483647) but the max value of
  // uint32_t is 2^32 (4294967296), so we need rand() * rand() to cover all the
  // possibilities.
  *offset = (static_cast<uint32_t>(rand()) *
             static_cast<uint32_t>(rand())) %  // NOLINT
            (buf->size() - sizeof(uint32_t));
  *update_xor =
      static_cast<uint32_t>(rand()) * static_cast<uint32_t>(rand());  // NOLINT
  const unsigned char* update_xor_buf =
      reinterpret_cast<const unsigned char*>(update_xor);

  // XOR update_xor at offset.
  for (size_t j = 0; j < sizeof(*update_xor); j++) {
    (*buf)[*offset + j] ^= update_xor_buf[j];
  }
}

TEST(Crc32Test, Get) {
  Crc32 crc32_test{10};
  Crc32 crc32_test_empty{};
  EXPECT_THAT(crc32_test.Get(), Eq(10));
  EXPECT_THAT(crc32_test_empty.Get(), Eq(0));
}

TEST(Crc32Test, Append) {
  // Test the complement logic inside Append()
  const uLong kCrcInitZero = crc32(0L, nullptr, 0);
  uint32_t foo_crc =
      crc32(kCrcInitZero, reinterpret_cast<const Bytef*>("foo"), 3);
  uint32_t foobar_crc =
      crc32(kCrcInitZero, reinterpret_cast<const Bytef*>("foobar"), 6);

  Crc32 crc32_test(~foo_crc);
  ASSERT_THAT(~crc32_test.Append("bar"), Eq(foobar_crc));

  // Test Append() that appending things separately should be the same as
  // appending in one shot
  Crc32 crc32_foobar{};
  crc32_foobar.Append("foobar");
  Crc32 crc32_foo_and_bar{};
  crc32_foo_and_bar.Append("foo");
  crc32_foo_and_bar.Append("bar");

  EXPECT_THAT(crc32_foo_and_bar.Get(), Eq(crc32_foobar.Get()));
}

TEST(Crc32Test, UpdateAtPosition) {
  std::string buf;
  buf.resize(1000);
  for (size_t i = 0; i < buf.size(); i++) {
    buf[i] = i * 2;
  }
  Crc32 crc32_test{};
  crc32_test.Append(buf);

  for (int i = 0; i < 100; i++) {
    uint32_t update_xor;
    int offset;
    UpdateAtRandomOffset(&buf, &update_xor, &offset);

    // Compute crc from scratch and compare against update.
    uint32_t new_crc =
        ~crc32(~0, reinterpret_cast<const Bytef*>(buf.data()), buf.size());
    const std::string_view xored_str(reinterpret_cast<const char*>(&update_xor),
                                     sizeof(update_xor));
    EXPECT_THAT(crc32_test.UpdateWithXor(xored_str, buf.size(), offset),
                IsOkAndHolds(new_crc));
  }

  // Wrong string length
  EXPECT_THAT(crc32_test.UpdateWithXor("12345", buf.size(), buf.size() - 1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
