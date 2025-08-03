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

#include "icing/store/namespace-fingerprint-identifier.h"

#include <cstdint>
#include <limits>
#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/store/namespace-id.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;

TEST(NamespaceFingerprintIdentifierTest, EncodeToCString) {
  NamespaceFingerprintIdentifier identifier1(/*namespace_id=*/0,
                                             /*fingerprint=*/0);
  EXPECT_THAT(identifier1.EncodeToCString(), Eq("\x01\x01\x01\x01"));

  NamespaceFingerprintIdentifier identifier2(/*namespace_id=*/0,
                                             /*fingerprint=*/1);
  EXPECT_THAT(identifier2.EncodeToCString(), Eq("\x01\x01\x01\x02"));

  NamespaceFingerprintIdentifier identifier3(
      /*namespace_id=*/0, /*fingerprint=*/std::numeric_limits<uint64_t>::max());
  EXPECT_THAT(identifier3.EncodeToCString(),
              Eq("\x01\x01\x01\x80\x80\x80\x80\x80\x80\x80\x80\x80\x02"));

  NamespaceFingerprintIdentifier identifier4(/*namespace_id=*/1,
                                             /*fingerprint=*/0);
  EXPECT_THAT(identifier4.EncodeToCString(), Eq("\x02\x01\x01\x01"));

  NamespaceFingerprintIdentifier identifier5(/*namespace_id=*/1,
                                             /*fingerprint=*/1);
  EXPECT_THAT(identifier5.EncodeToCString(), Eq("\x02\x01\x01\x02"));

  NamespaceFingerprintIdentifier identifier6(
      /*namespace_id=*/1, /*fingerprint=*/std::numeric_limits<uint64_t>::max());
  EXPECT_THAT(identifier6.EncodeToCString(),
              Eq("\x02\x01\x01\x80\x80\x80\x80\x80\x80\x80\x80\x80\x02"));

  NamespaceFingerprintIdentifier identifier7(
      /*namespace_id=*/std::numeric_limits<NamespaceId>::max(),
      /*fingerprint=*/0);
  EXPECT_THAT(identifier7.EncodeToCString(), Eq("\x80\x80\x02\x01"));

  NamespaceFingerprintIdentifier identifier8(
      /*namespace_id=*/std::numeric_limits<NamespaceId>::max(),
      /*fingerprint=*/1);
  EXPECT_THAT(identifier8.EncodeToCString(), Eq("\x80\x80\x02\x02"));

  NamespaceFingerprintIdentifier identifier9(
      /*namespace_id=*/std::numeric_limits<NamespaceId>::max(),
      /*fingerprint=*/std::numeric_limits<uint64_t>::max());
  EXPECT_THAT(identifier9.EncodeToCString(),
              Eq("\x80\x80\x02\x80\x80\x80\x80\x80\x80\x80\x80\x80\x02"));
}

TEST(NamespaceFingerprintIdentifierTest,
     MultipleCStringConversionsAreReversible) {
  NamespaceFingerprintIdentifier identifier1(/*namespace_id=*/0,
                                             /*fingerprint=*/0);
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier1.EncodeToCString()),
              IsOkAndHolds(identifier1));

  NamespaceFingerprintIdentifier identifier2(/*namespace_id=*/0,
                                             /*fingerprint=*/1);
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier2.EncodeToCString()),
              IsOkAndHolds(identifier2));

  NamespaceFingerprintIdentifier identifier3(
      /*namespace_id=*/0, /*fingerprint=*/std::numeric_limits<uint64_t>::max());
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier3.EncodeToCString()),
              IsOkAndHolds(identifier3));

  NamespaceFingerprintIdentifier identifier4(/*namespace_id=*/1,
                                             /*fingerprint=*/0);
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier4.EncodeToCString()),
              IsOkAndHolds(identifier4));

  NamespaceFingerprintIdentifier identifier5(/*namespace_id=*/1,
                                             /*fingerprint=*/1);
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier5.EncodeToCString()),
              IsOkAndHolds(identifier5));

  NamespaceFingerprintIdentifier identifier6(
      /*namespace_id=*/1, /*fingerprint=*/std::numeric_limits<uint64_t>::max());
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier6.EncodeToCString()),
              IsOkAndHolds(identifier6));

  NamespaceFingerprintIdentifier identifier7(
      /*namespace_id=*/std::numeric_limits<NamespaceId>::max(),
      /*fingerprint=*/0);
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier7.EncodeToCString()),
              IsOkAndHolds(identifier7));

  NamespaceFingerprintIdentifier identifier8(
      /*namespace_id=*/std::numeric_limits<NamespaceId>::max(),
      /*fingerprint=*/1);
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier8.EncodeToCString()),
              IsOkAndHolds(identifier8));

  NamespaceFingerprintIdentifier identifier9(
      /*namespace_id=*/std::numeric_limits<NamespaceId>::max(),
      /*fingerprint=*/std::numeric_limits<uint64_t>::max());
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(
                  identifier9.EncodeToCString()),
              IsOkAndHolds(identifier9));
}

TEST(NamespaceFingerprintIdentifierTest,
     DecodeFromCStringInvalidLengthShouldReturnError) {
  std::string invalid_str = "\x01\x01\x01";
  EXPECT_THAT(NamespaceFingerprintIdentifier::DecodeFromCString(invalid_str),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
