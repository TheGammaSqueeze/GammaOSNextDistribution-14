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

#include "icing/index/term-id-codec.h"

#include <cstdint>
#include <limits>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;

TEST(TermIdCodecTest, CreateExceedsInt32MaxInvalid) {
  EXPECT_THAT(TermIdCodec::Create(
                  /*max_main_tvi=*/std::numeric_limits<uint32_t>::max() - 10,
                  /*max_lite_tvi=*/std::numeric_limits<uint32_t>::max() - 10),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(TermIdCodecTest, CreateWithPositiveTvisOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIdCodec> codec,
      TermIdCodec::Create(/*max_main_tvi=*/10, /*max_lite_tvi=*/10));
}

TEST(TermIdCodecTest, CreateWithZeroTvisOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIdCodec> codec,
      TermIdCodec::Create(/*max_main_tvi=*/0, /*max_lite_tvi=*/0));
}

TEST(TermIdCodecTest, Encode) {
  // Create a codec where main TVIs are: [0,3), lite: [3,10)
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIdCodec> codec,
      TermIdCodec::Create(/*max_main_tvi=*/3, /*max_lite_tvi=*/7));

  EXPECT_THAT(codec->EncodeTvi(0, TviType::MAIN), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(codec->EncodeTvi(2, TviType::MAIN), IsOkAndHolds(Eq(2)));
  EXPECT_THAT(codec->EncodeTvi(3, TviType::MAIN),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  EXPECT_THAT(codec->EncodeTvi(0, TviType::LITE), IsOkAndHolds(Eq(3)));
  EXPECT_THAT(codec->EncodeTvi(6, TviType::LITE), IsOkAndHolds(Eq(9)));
  EXPECT_THAT(codec->EncodeTvi(7, TviType::LITE),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(TermIdCodecTest, DecodeTermInfo) {
  // Create a codec where main TVIs are: [0,3), lite: [3,10)
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIdCodec> codec,
      TermIdCodec::Create(/*max_main_tvi=*/3, /*max_lite_tvi=*/7));

  ICING_ASSERT_OK_AND_ASSIGN(TermIdCodec::DecodedTermInfo decoded_term,
                             codec->DecodeTermInfo(0));
  EXPECT_THAT(decoded_term.tvi_type, Eq(TviType::MAIN));
  EXPECT_THAT(decoded_term.tvi, Eq(0));

  ICING_ASSERT_OK_AND_ASSIGN(decoded_term, codec->DecodeTermInfo(3));
  EXPECT_THAT(decoded_term.tvi_type, Eq(TviType::LITE));
  EXPECT_THAT(decoded_term.tvi, Eq(0));

  EXPECT_THAT(codec->DecodeTermInfo(10),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(TermIdCodecTest, TviType) {
  // Create a codec where main TVIs are: [0,3), lite: [3,10)
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIdCodec> codec,
      TermIdCodec::Create(/*max_main_tvi=*/3, /*max_lite_tvi=*/7));

  EXPECT_THAT(codec->DecodeTviType(0), IsOkAndHolds(Eq(TviType::MAIN)));
  EXPECT_THAT(codec->DecodeTviType(2), IsOkAndHolds(Eq(TviType::MAIN)));
  EXPECT_THAT(codec->DecodeTviType(3), IsOkAndHolds(Eq(TviType::LITE)));
  EXPECT_THAT(codec->DecodeTviType(9), IsOkAndHolds(Eq(TviType::LITE)));
  EXPECT_THAT(codec->DecodeTviType(10),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(TermIdCodecTest, Max) {
  // Create a codec where main TVIs are: [0,3), lite: [3,10)
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIdCodec> codec,
      TermIdCodec::Create(/*max_main_tvi=*/3, /*max_lite_tvi=*/7));

  EXPECT_THAT(codec->max_main_tvi(), Eq(3));
  EXPECT_THAT(codec->max_lite_tvi(), Eq(7));
  EXPECT_THAT(codec->max_term_id(), Eq(10));
}

}  // namespace

}  // namespace lib
}  // namespace icing
