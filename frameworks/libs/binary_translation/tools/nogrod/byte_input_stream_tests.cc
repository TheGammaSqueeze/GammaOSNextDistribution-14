/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <inttypes.h>
#include <limits.h>

#include <vector>

#include "byte_input_stream.h"

namespace nogrod {

TEST(byte_input_stream, smoke) {
  uint8_t bytes[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a,
                     0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0xc1, 0x00, 0xc1, 0x7f, 'b',
                     'a',  'r',  '\0', 0x2a, 0x2b, 0x2c, 0x1,  0x2,  0x3,  0xFF};

  ByteInputStream in(bytes, sizeof(bytes));

  ASSERT_TRUE(in.available());
  ASSERT_EQ(0x01, in.ReadUint8());
  ASSERT_TRUE(in.available());
  ASSERT_EQ(0x0302, in.ReadUint16());
  ASSERT_TRUE(in.available());
  ASSERT_EQ(0x07060504U, in.ReadUint32());
  // Reading 0 bytes should be a noop that returns empty vector
  auto empty_vector = in.ReadBytes(0);
  EXPECT_TRUE(empty_vector.empty());
  ASSERT_TRUE(in.available());
  ASSERT_EQ(0x0f0e0d0c0b0a0908U, in.ReadUint64());
  ASSERT_TRUE(in.available());
  ASSERT_EQ(65U, in.ReadLeb128());
  ASSERT_TRUE(in.available());
  ASSERT_EQ(-63, in.ReadSleb128());
  ASSERT_TRUE(in.available());
  ASSERT_EQ("bar", in.ReadString());
  ASSERT_TRUE(in.available());
  auto byte_vector = in.ReadBytes(3);
  ASSERT_EQ(3U, byte_vector.size());
  ASSERT_EQ(0x2a, byte_vector[0]);
  ASSERT_EQ(0x2b, byte_vector[1]);
  ASSERT_EQ(0x2c, byte_vector[2]);
  ASSERT_EQ(0x030201U, in.ReadUint24());
  ASSERT_EQ(0xFF, in.ReadUint8());
  ASSERT_TRUE(!in.available());
}

TEST(byte_input_stream, out_of_bounds) {
  uint8_t array_out_of_bounds[] = {0x80, 0x81, 0x82};
  ByteInputStream in(array_out_of_bounds, sizeof(array_out_of_bounds));

  EXPECT_DEATH((void)in.ReadString(), "");
  EXPECT_DEATH((void)in.ReadUint64(), "");
  EXPECT_DEATH((void)in.ReadUint32(), "");

  // This is ok because EXPECT_DEATH executes in forked process with cloned vm
  ASSERT_EQ(0x8180, in.ReadUint16());
  EXPECT_DEATH((void)in.ReadUint16(), "");
  EXPECT_DEATH((void)in.ReadBytes(3), "");
  EXPECT_DEATH((void)in.ReadBytes(2), "");
  ASSERT_EQ(0x82, in.ReadUint8());
  EXPECT_DEATH((void)in.ReadUint8(), "");
}

}  // namespace nogrod
