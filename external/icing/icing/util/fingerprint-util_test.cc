// Copyright (C) 2022 Google LLC
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

#include "icing/util/fingerprint-util.h"

#include <cstdint>
#include <limits>

#include "icing/text_classifier/lib3/utils/hash/farmhash.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace icing {
namespace lib {
namespace fingerprint_util {

namespace {

using ::testing::Eq;

TEST(FingerprintUtilTest, ConversionIsReversible) {
  std::string str = "foo-bar-baz";
  uint64_t fprint = tc3farmhash::Fingerprint64(str);
  std::string fprint_string = GetFingerprintString(fprint);
  EXPECT_THAT(GetFingerprint(fprint_string), Eq(fprint));
}

TEST(FingerprintUtilTest, ZeroConversionIsReversible) {
  uint64_t fprint = 0;
  std::string fprint_string = GetFingerprintString(fprint);
  EXPECT_THAT(GetFingerprint(fprint_string), Eq(fprint));
}

TEST(FingerprintUtilTest, MultipleConversionsAreReversible) {
  EXPECT_THAT(GetFingerprint(GetFingerprintString(25)), Eq(25));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(766)), Eq(766));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(2305)), Eq(2305));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(6922)), Eq(6922));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(62326)), Eq(62326));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(186985)), Eq(186985));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(560962)), Eq(560962));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(1682893)), Eq(1682893));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(15146065)), Eq(15146065));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(136314613)), Eq(136314613));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(1226831545)), Eq(1226831545));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(11041483933)),
              Eq(11041483933));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(2683080596566)),
              Eq(2683080596566));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(72443176107373)),
              Eq(72443176107373));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(1955965754899162)),
              Eq(1955965754899162));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(52811075382277465)),
              Eq(52811075382277465));
  EXPECT_THAT(GetFingerprint(GetFingerprintString(4277697105964474945)),
              Eq(4277697105964474945));
}

}  // namespace

}  // namespace fingerprint_util
}  // namespace lib
}  // namespace icing
