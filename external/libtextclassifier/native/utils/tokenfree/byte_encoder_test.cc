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

#include "utils/tokenfree/byte_encoder.h"

#include <memory>
#include <vector>

#include "utils/base/integral_types.h"
#include "utils/container/sorted-strings-table.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

using testing::ElementsAre;

TEST(ByteEncoderTest, SimpleTokenization) {
  const ByteEncoder encoder;
  {
    std::vector<int64_t> encoded_text;
    EXPECT_TRUE(encoder.Encode("hellothere", &encoded_text));
    EXPECT_THAT(encoded_text,
                ElementsAre(104, 101, 108, 108, 111, 116, 104, 101, 114, 101));
  }
}

TEST(ByteEncoderTest, SimpleTokenization2) {
  const ByteEncoder encoder;
  {
    std::vector<int64_t> encoded_text;
    EXPECT_TRUE(encoder.Encode("Hello", &encoded_text));
    EXPECT_THAT(encoded_text, ElementsAre(72, 101, 108, 108, 111));
  }
}
}  // namespace
}  // namespace libtextclassifier3
