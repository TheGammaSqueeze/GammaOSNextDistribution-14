// Copyright (C) 2023 Google LLC
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

#include "icing/util/encode-util.h"

#include <cstdint>
#include <string>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace icing {
namespace lib {
namespace encode_util {

namespace {

using ::testing::Eq;
using ::testing::Gt;
using ::testing::SizeIs;

TEST(EncodeUtilTest, IntCStringZeroConversion) {
  uint64_t value = 0;
  std::string encoded_str = EncodeIntToCString(value);

  EXPECT_THAT(encoded_str, SizeIs(Gt(0)));
  EXPECT_THAT(DecodeIntFromCString(encoded_str), Eq(value));
}

TEST(EncodeUtilTest, IntCStringConversionIsReversible) {
  uint64_t value = 123456;
  std::string encoded_str = EncodeIntToCString(value);
  EXPECT_THAT(DecodeIntFromCString(encoded_str), Eq(value));
}

TEST(EncodeUtilTest, MultipleIntCStringConversionsAreReversible) {
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(25)), Eq(25));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(766)), Eq(766));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(2305)), Eq(2305));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(6922)), Eq(6922));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(62326)), Eq(62326));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(186985)), Eq(186985));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(560962)), Eq(560962));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(1682893)), Eq(1682893));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(15146065)), Eq(15146065));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(136314613)),
              Eq(136314613));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(1226831545)),
              Eq(1226831545));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(11041483933)),
              Eq(11041483933));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(2683080596566)),
              Eq(2683080596566));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(72443176107373)),
              Eq(72443176107373));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(1955965754899162)),
              Eq(1955965754899162));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(52811075382277465)),
              Eq(52811075382277465));
  EXPECT_THAT(DecodeIntFromCString(EncodeIntToCString(4277697105964474945)),
              Eq(4277697105964474945));
}

TEST(EncodeUtilTest, MultipleValidEncodedCStringIntConversionsAreReversible) {
  // Only valid encoded C string (no zero bytes, length is between 1 and 10) are
  // reversible.
  EXPECT_THAT(EncodeIntToCString(DecodeIntFromCString("foo")), Eq("foo"));
  EXPECT_THAT(EncodeIntToCString(DecodeIntFromCString("bar")), Eq("bar"));
  EXPECT_THAT(EncodeIntToCString(DecodeIntFromCString("baz")), Eq("baz"));
  EXPECT_THAT(EncodeIntToCString(DecodeIntFromCString("Icing")), Eq("Icing"));
  EXPECT_THAT(EncodeIntToCString(DecodeIntFromCString("Google")), Eq("Google"));
  EXPECT_THAT(EncodeIntToCString(DecodeIntFromCString("Youtube")),
              Eq("Youtube"));
}

}  // namespace

}  // namespace encode_util
}  // namespace lib
}  // namespace icing
