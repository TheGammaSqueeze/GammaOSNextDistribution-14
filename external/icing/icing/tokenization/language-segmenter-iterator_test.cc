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

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/portable/platform.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {
namespace {

using ::testing::Eq;

// We have a separate test just for the language segmenter iterators because we
// don't need to stress test the implementation's definition of a term. These
// test that it advances and traverses through simple terms consistently between
// all the implementations.
class LanguageSegmenterIteratorTest : public testing::Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }
  }

  std::unique_ptr<const JniCache> jni_cache_ = GetTestJniCache();
};

TEST_F(LanguageSegmenterIteratorTest, AdvanceAndGetTerm) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator,
                             language_segmenter->Segment("foo bar"));

  EXPECT_TRUE(iterator->Advance());
  EXPECT_THAT(iterator->GetTerm(), Eq("foo"));

  EXPECT_TRUE(iterator->Advance());
  EXPECT_THAT(iterator->GetTerm(), Eq(" "));

  EXPECT_TRUE(iterator->Advance());
  EXPECT_THAT(iterator->GetTerm(), Eq("bar"));

  EXPECT_FALSE(iterator->Advance());
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermStartingAfterUtf32WithOffsetInText) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator,
                             language_segmenter->Segment("foo bar"));

  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/0),
              IsOkAndHolds(3));  // The term " "
  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/3),
              IsOkAndHolds(4));  // The term "bar"
  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/4),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermStartingAfterUtf32WithNegativeOffsetNotOk) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator,
                             language_segmenter->Segment("foo bar"));

  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/-1), IsOk());

  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/-100), IsOk());

  EXPECT_THAT(iterator->ResetToStartUtf32(), IsOkAndHolds(0));
  EXPECT_THAT(iterator->GetTerm(), Eq("foo"));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermStartingAfterUtf32WithTextLengthOffsetInvalidArgument) {
  std::string text = "foo bar";
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator, language_segmenter->Segment(text));

  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/text.length()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermStartingAfterUtf32WithOffsetPastTextLengthInvalidArgument) {
  std::string text = "foo bar";
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator, language_segmenter->Segment(text));

  EXPECT_THAT(iterator->ResetToTermStartingAfterUtf32(/*offset=*/100),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermEndingBeforeUtf32WithOffsetInText) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator,
                             language_segmenter->Segment("foo bar"));

  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/6),
              IsOkAndHolds(3));  // The term " "
  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/3),
              IsOkAndHolds(0));  // The term "foo"
  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/2),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermEndingBeforeUtf32WithZeroNotFound) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator,
                             language_segmenter->Segment("foo bar"));

  // Zero is a valid argument, but there aren't any terms that end before it.
  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermEndingBeforeUtf32WithNegativeOffsetInvalidArgument) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator,
                             language_segmenter->Segment("foo bar"));

  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/-100),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(LanguageSegmenterIteratorTest,
       ResetToTermEndingBeforeUtf32WithOffsetPastTextEndInvalidArgument) {
  std::string text = "foo bar";
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(auto iterator, language_segmenter->Segment(text));

  EXPECT_THAT(iterator->ResetToTermEndingBeforeUtf32(/*offset=*/text.length()),
              IsOk());

  EXPECT_THAT(
      iterator->ResetToTermEndingBeforeUtf32(/*offset=*/text.length() + 1),
      IsOk());
}

}  // namespace
}  // namespace lib
}  // namespace icing
