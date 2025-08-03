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

#include "icing/index/numeric/integer-index-bucket-util.h"

#include <limits>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {
namespace integer_index_bucket_util {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::Ne;
using ::testing::SizeIs;

static constexpr DocumentId kDefaultDocumentId = 123;
static constexpr SectionId kDefaultSectionId = 31;

TEST(IntegerIndexBucketUtilTest, Split_numDataNotDivisibleByThreshold) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)};
  int64_t key_lower = -10;
  int64_t key_upper = 10;
  int32_t num_data_threshold = 3;
  ASSERT_THAT(data.size() % num_data_threshold, Ne(0));

  // Keys = [-10, -3, -2, 0, 1, 2, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, key_lower, key_upper, num_data_threshold);
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -10, key upper = -2, keys = [-10, -3, -2].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)));
  // Bucket 1: key lower = -1, key upper = 2, keys = [0, 1, 2].
  EXPECT_THAT(results[1].key_lower, Eq(-1));
  EXPECT_THAT(results[1].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 2: key lower = 3, key upper = 10, keys = [10].
  EXPECT_THAT(results[2].key_lower, Eq(3));
  EXPECT_THAT(results[2].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest, Split_numDataDivisibleByThreshold) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)};
  int64_t key_lower = -10;
  int64_t key_upper = 10;
  int32_t num_data_threshold = 3;
  ASSERT_THAT(data.size() % num_data_threshold, Eq(0));

  // Keys = [-10, -3, -2, 0, 2, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, key_lower, key_upper, num_data_threshold);
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = -10, key upper = -2, keys = [-10, -3, -2].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)));
  // Bucket 1: key lower = -1, key upper = 2, keys = [0, 2, 10].
  EXPECT_THAT(results[1].key_lower, Eq(-1));
  EXPECT_THAT(results[1].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest, Split_shouldIncludeOriginalKeyRange) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)};
  int64_t key_lower = -1000;
  int64_t key_upper = 1000;
  int32_t num_data_threshold = 3;

  // Keys = [-10, -3, -2, 0, 1, 2, 10].
  // Split should include the original key_lower and key_upper even if there is
  // no key at boundary.
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, key_lower, key_upper, num_data_threshold);
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -1000, key upper = -2, keys = [-10, -3, -2].
  EXPECT_THAT(results[0].key_lower, Eq(-1000));
  EXPECT_THAT(results[0].key_upper, Eq(-2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)));
  // Bucket 1: key lower = -1, key upper = 2, keys = [0, 1, 2].
  EXPECT_THAT(results[1].key_lower, Eq(-1));
  EXPECT_THAT(results[1].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 2: key lower = 3, key upper = 1000, keys = [10].
  EXPECT_THAT(results[2].key_lower, Eq(3));
  EXPECT_THAT(results[2].key_upper, Eq(1000));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest, Split_singleBucketWithoutSplitting) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)};
  int64_t key_lower = -1000;
  int64_t key_upper = 1000;
  int32_t num_data_threshold = 100;

  // Keys = [-10, -3, -2, 0, 1, 2, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, key_lower, key_upper, num_data_threshold);
  ASSERT_THAT(results, SizeIs(1));
  // Bucket 0: key lower = -1000, key upper = 1000, keys = [-10, -3, -2, 0, 1,
  // 2, 10]. Since # of data <= threshold, data vector won't be sorted and thus
  // [start, end) will have data with the original order.
  EXPECT_THAT(results[0].key_lower, Eq(-1000));
  EXPECT_THAT(results[0].key_upper, Eq(1000));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)));
}

TEST(IntegerIndexBucketUtilTest, Split_emptyData) {
  std::vector<IntegerIndexData> empty_data;
  std::vector<DataRangeAndBucketInfo> results =
      Split(empty_data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(1));
  // Bucket 0: key lower = -10, key upper = 10, keys = [].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(10));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[0].start, results[0].end),
              IsEmpty());
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_firstBucket_keyEqualsKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -10, -10, -10, -10, 0, 3, 5, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = -10 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = 0, 3, ....
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -10, key upper = -10, keys = [-10, -10, -10, -10,
  // -10].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10)));
  // Bucket 1: key lower = -9, key upper = 5, keys = [0, 3, 5].
  EXPECT_THAT(results[1].key_lower, Eq(-9));
  EXPECT_THAT(results[1].key_upper, Eq(5));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5)));
  // Bucket 2: key lower = 6, key upper = 10, keys = [10].
  EXPECT_THAT(results[2].key_lower, Eq(6));
  EXPECT_THAT(results[2].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_firstBucket_keyGreaterThanKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-7, -7, -7, -7, -7, 0, 3, 5, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = -7 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = 0, 3, ....
  // - They should be in a single range bucket [-7, -7], and another bucket
  //   [-10, -8] with empty data should be created before it.
  ASSERT_THAT(results, SizeIs(4));
  // Bucket 0: key lower = -10, key upper = -8, keys = [].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-8));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[0].start, results[0].end),
              IsEmpty());
  // Bucket 1: key lower = -7, key upper = -7, keys = [-7, -7, -7, -7, -7].
  EXPECT_THAT(results[1].key_lower, Eq(-7));
  EXPECT_THAT(results[1].key_upper, Eq(-7));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -7)));
  // Bucket 2: key lower = -6, key upper = 5, keys = [0, 3, 5].
  EXPECT_THAT(results[2].key_lower, Eq(-6));
  EXPECT_THAT(results[2].key_upper, Eq(5));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5)));
  // Bucket 3: key lower = 6, key upper = 10, keys = [10].
  EXPECT_THAT(results[3].key_lower, Eq(6));
  EXPECT_THAT(results[3].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[3].start, results[3].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_midBucket_keyEqualsKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -5, -4, -4, -4, -4, -4, 5, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = -4 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = -10, -5, 5, 10.
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -10, key upper = -5, keys = [-10, -5].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-5));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -5)));
  // Bucket 1: key lower = -4, key upper = -4, keys = [-4, -4, -4, -4, -4].
  EXPECT_THAT(results[1].key_lower, Eq(-4));
  EXPECT_THAT(results[1].key_upper, Eq(-4));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -4)));
  // Bucket 2: key lower = -3, key upper = 10, keys = [5, 10].
  EXPECT_THAT(results[2].key_lower, Eq(-3));
  EXPECT_THAT(results[2].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_midBucket_keyGreaterThanKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -5, -1, -1, -1, -1, -1, 5, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = -1 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = -10, -5, 5, 10.
  // - They should be in a single range bucket [-1, -1], and range [-4, -2]
  //   should be merged into the previous bucket.
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -10, key upper = -2, keys = [-10, -5].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -5)));
  // Bucket 1: key lower = -1, key upper = -1, keys = [-1, -1, -1, -1, -1].
  EXPECT_THAT(results[1].key_lower, Eq(-1));
  EXPECT_THAT(results[1].key_upper, Eq(-1));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1)));
  // Bucket 2: key lower = 0, key upper = 10, keys = [5, 10].
  EXPECT_THAT(results[2].key_lower, Eq(0));
  EXPECT_THAT(results[2].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_lastBucket_keyEqualsKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3)};

  // Keys = [-10, -3, 0, 2, 3, 3, 3, 3, 3].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = 3 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = -10, -3, 0, 2.
  // - They should be in a single range bucket [3, 3], and another bucket
  //   [4, 10] with empty data should be created after it.
  ASSERT_THAT(results, SizeIs(4));
  // Bucket 0: key lower = -10, key upper = 0, keys = [-10, -3, 0].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(0));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0)));
  // Bucket 1: key lower = 1, key upper = 2, keys = [2].
  EXPECT_THAT(results[1].key_lower, Eq(1));
  EXPECT_THAT(results[1].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 2: key lower = 3, key upper = 10, keys = [3, 3, 3, 3, 3].
  EXPECT_THAT(results[2].key_lower, Eq(3));
  EXPECT_THAT(results[2].key_upper, Eq(3));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 3)));
  // Bucket 3: key lower = 4, key upper = 10, keys = [].
  EXPECT_THAT(results[3].key_lower, Eq(4));
  EXPECT_THAT(results[3].key_upper, Eq(10));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[3].start, results[3].end),
              IsEmpty());
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_lastBucket_keyWithinKeyLowerAndUpper) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6)};

  // Keys = [-10, -3, 0, 2, 6, 6, 6, 6, 6].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = 6 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = -10, -3, 0, 2.
  // - They should be in a single range bucket [6, 6]. Range [3, 5] should be
  //   merged into the previous bucket. and another bucket [7, 10] with empty
  //   data should be created after it.
  ASSERT_THAT(results, SizeIs(4));
  // Bucket 0: key lower = -10, key upper = 0, keys = [-10, -3, 0].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(0));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0)));
  // Bucket 1: key lower = 1, key upper = 5, keys = [2].
  EXPECT_THAT(results[1].key_lower, Eq(1));
  EXPECT_THAT(results[1].key_upper, Eq(5));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 2: key lower = 6, key upper = 6, keys = [6, 6, 6, 6, 6].
  EXPECT_THAT(results[2].key_lower, Eq(6));
  EXPECT_THAT(results[2].key_upper, Eq(6));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 6)));
  // Bucket 3: key lower = 7, key upper = 10, keys = [].
  EXPECT_THAT(results[3].key_lower, Eq(7));
  EXPECT_THAT(results[3].key_upper, Eq(10));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[3].start, results[3].end),
              IsEmpty());
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_lastBucket_keyEqualsKeyUpper) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -3, 0, 2, 10, 10, 10, 10, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = 10 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be separated from key = -10, -3, 0, 2.
  // - They should be in a single range bucket [10, 10], and range [3, 9] should
  //   be merged into the previous bucket.
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -10, key upper = 0, keys = [-10, -3, 0].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(0));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0)));
  // Bucket 1: key lower = 1, key upper = 9, keys = [2].
  EXPECT_THAT(results[1].key_lower, Eq(1));
  EXPECT_THAT(results[1].key_upper, Eq(9));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 2: key lower = 10, key upper = 10, keys = [10, 10, 10, 10, 10].
  EXPECT_THAT(results[2].key_lower, Eq(10));
  EXPECT_THAT(results[2].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_shouldNotMergeIntoPreviousBucket) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -2, -2, -2, -2, -2, 5, 5, 5, 5, 5, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Data with key = -2 and 5 should be put into a single bucket respectively.
  // - When dealing with key = 5, range [-1, 4] should not be merged into the
  //   previous bucket [-2, -2] because [-2, -2] also contains single key data
  //   exceeding the threshold. Instead, we should create bucket [-1, 4] with
  //   empty data.
  ASSERT_THAT(results, SizeIs(5));
  // Bucket 0: key lower = -10, key upper = -3, keys = [-10].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-3));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[0].start, results[0].end),
              ElementsAre(IntegerIndexData(kDefaultSectionId,
                                           kDefaultDocumentId, -10)));
  // Bucket 1: key lower = -2, key upper = -2, keys = [-2, -2, -2, -2, -2].
  EXPECT_THAT(results[1].key_lower, Eq(-2));
  EXPECT_THAT(results[1].key_upper, Eq(-2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)));
  // Bucket 2: key lower = -1, key upper = 4, keys = [].
  EXPECT_THAT(results[2].key_lower, Eq(-1));
  EXPECT_THAT(results[2].key_upper, Eq(4));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[2].start, results[2].end),
              IsEmpty());
  // Bucket 3: key lower = 5, key upper = 5, keys = [5, 5, 5, 5, 5].
  EXPECT_THAT(results[3].key_lower, Eq(5));
  EXPECT_THAT(results[3].key_upper, Eq(5));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[3].start, results[3].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5)));
  // Bucket 4: key lower = 6, key upper = 10, keys = [10].
  EXPECT_THAT(results[4].key_lower, Eq(6));
  EXPECT_THAT(results[4].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[4].start, results[4].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_shouldMergeIntoPreviousBucket) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -8),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -8, -3, -2, -2, -2, 5, 5, 5, 5, 5, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Data with key = 5 should be put into a single bucket.
  // - When dealing with key = 5, range [-1, 4] should be merged into the
  //   previous bucket [-2, -2] because # of data in [-2, -2] doesn't exceed the
  //   threshold.
  ASSERT_THAT(results, SizeIs(4));
  // Bucket 0: key lower = -10, key upper = -3, keys = [-10, -8, -3].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-3));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -8),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -3)));
  // Bucket 1: key lower = -2, key upper = 4, keys = [-2, -2, -2].
  EXPECT_THAT(results[1].key_lower, Eq(-2));
  EXPECT_THAT(results[1].key_upper, Eq(4));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -2)));
  // Bucket 2: key lower = 5, key upper = 5, keys = [5, 5, 5, 5, 5].
  EXPECT_THAT(results[2].key_lower, Eq(5));
  EXPECT_THAT(results[2].key_upper, Eq(5));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 5)));
  // Bucket 3: key lower = 6, key upper = 10, keys = [10].
  EXPECT_THAT(results[3].key_lower, Eq(6));
  EXPECT_THAT(results[3].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[3].start, results[3].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_singleBucket_keyEqualsKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10)};

  // Keys = [-10, -10, -10, -10, -10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = -10 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be in a single range bucket [-10, -10], and another bucket
  //   [-9, 10] with empty data should be created after it.
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = -10, key upper = -10, keys = [-10, -10, -10, -10,
  // -10].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10)));
  // Bucket 1: key lower = -9, key upper = 10, keys = [].
  EXPECT_THAT(results[1].key_lower, Eq(-9));
  EXPECT_THAT(results[1].key_upper, Eq(10));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[1].start, results[1].end),
              IsEmpty());
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_singleBucket_keyWithinKeyLowerAndUpper) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0)};

  // Keys = [0, 0, 0, 0, 0].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = 0 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be in a single range bucket [0, 0]. Another bucket [-10, -1]
  //   with empty data should be created before it, and another bucket [1, 10]
  //   with empty data should be created after it.
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = -10, key upper = -1, keys = [].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-1));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[0].start, results[0].end),
              IsEmpty());
  // Bucket 1: key lower = 0, key upper = 0, keys = [0, 0, 0, 0, 0].
  EXPECT_THAT(results[1].key_lower, Eq(0));
  EXPECT_THAT(results[1].key_upper, Eq(0));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 0)));
  // Bucket 2: key lower = 1, key upper = 10, keys = [].
  EXPECT_THAT(results[2].key_lower, Eq(1));
  EXPECT_THAT(results[2].key_upper, Eq(10));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[2].start, results[2].end),
              IsEmpty());
}

TEST(IntegerIndexBucketUtilTest,
     Split_sameKeysExceedingThreshold_singleBucket_keyEqualsKeyUpper) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [10, 10, 10, 10, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // - Even though # of data with key = 10 exceeds the threshold, they should
  //   still be in the same bucket.
  // - They should be in a single range bucket [10, 10], and another bucket
  //   [-10, 9] with empty data should be created before it.
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = -10, key upper = 9, keys = [].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(9));
  EXPECT_THAT(std::vector<IntegerIndexData>(results[0].start, results[0].end),
              IsEmpty());
  // Bucket 1: key lower = -10, key upper = 10, keys = [10, 10, 10, 10, 10].
  EXPECT_THAT(results[1].key_lower, Eq(10));
  EXPECT_THAT(results[1].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_adjacentKeysTotalNumDataExceedThreshold) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [-10, -10, -1, -1, 2, 2, 10, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/-10, /*original_key_upper=*/10,
            /*num_data_threshold=*/3);
  // Even though # of data with the same key is within the threshold, since
  // total # of data of adjacent keys exceed the threshold, they should be
  // separated into different buckets.
  ASSERT_THAT(results, SizeIs(4));
  // Bucket 0: key lower = -10, key upper = -10, keys = [-10, -10].
  EXPECT_THAT(results[0].key_lower, Eq(-10));
  EXPECT_THAT(results[0].key_upper, Eq(-10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
          IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10)));
  // Bucket 1: key lower = -9, key upper = -1, keys = [-1, -1].
  EXPECT_THAT(results[1].key_lower, Eq(-9));
  EXPECT_THAT(results[1].key_upper, Eq(-1));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1)));
  // Bucket 2: key lower = 0, key upper = 2, keys = [2, 2].
  EXPECT_THAT(results[2].key_lower, Eq(0));
  EXPECT_THAT(results[2].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 3: key lower = 3, key upper = 10, keys = [10, 10].
  EXPECT_THAT(results[3].key_lower, Eq(3));
  EXPECT_THAT(results[3].key_upper, Eq(10));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[3].start, results[3].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_keyLowerEqualsIntMin_smallestKeyGreaterThanKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min() + 1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [INT64_MIN + 1, -10, -1, 2, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/std::numeric_limits<int64_t>::min(),
            /*original_key_upper=*/std::numeric_limits<int64_t>::max(),
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = INT64_MIN, key upper = -1, keys = [INT64_MIN + 1,
  // -10, -1].
  EXPECT_THAT(results[0].key_lower, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(results[0].key_upper, Eq(-1));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min() + 1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1)));
  // Bucket 1: key lower = 0, key upper = INT64_MAX, keys = [2, 10].
  EXPECT_THAT(results[1].key_lower, Eq(0));
  EXPECT_THAT(results[1].key_upper, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_keyLowerEqualsIntMin_smallestKeyEqualsKeyLower) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [INT64_MIN, -10, -1, 2, 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/std::numeric_limits<int64_t>::min(),
            /*original_key_upper=*/std::numeric_limits<int64_t>::max(),
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = INT64_MIN, key upper = -1, keys = [INT64_MIN, -10,
  // -1].
  EXPECT_THAT(results[0].key_lower, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(results[0].key_upper, Eq(-1));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1)));
  // Bucket 1: key lower = 0, key upper = INT64_MAX, keys = [2, 10].
  EXPECT_THAT(results[1].key_lower, Eq(0));
  EXPECT_THAT(results[1].key_upper, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_keyLowerEqualsIntMin_keyIntMinExceedingThreshold) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::min()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)};

  // Keys = [INT64_MIN, INT64_MIN, INT64_MIN, INT64_MIN, INT64_MIN, -10, -1, 2,
  // 10].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/std::numeric_limits<int64_t>::min(),
            /*original_key_upper=*/std::numeric_limits<int64_t>::max(),
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = INT64_MIN, key upper = INT64_MIN, keys = [INT64_MIN,
  // INT64_MIN, INT64_MIN, INT64_MIN, INT64_MIN].
  EXPECT_THAT(results[0].key_lower, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(results[0].key_upper, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::min())));
  // Bucket 1: key lower = INT64_MIN + 1, key upper = 2, keys = [-10, -1, 2].
  EXPECT_THAT(results[1].key_lower,
              Eq(std::numeric_limits<int64_t>::min() + 1));
  EXPECT_THAT(results[1].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 2: key lower = 3, key upper = INT64_MAX, keys = [10].
  EXPECT_THAT(results[2].key_lower, Eq(3));
  EXPECT_THAT(results[2].key_upper, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_keyUpperEqualsIntMax_largestKeySmallerThanKeyUpper) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max() - 1),
  };

  // Keys = [-10, -1, 2, 10, INT64_MAX - 1].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/std::numeric_limits<int64_t>::min(),
            /*original_key_upper=*/std::numeric_limits<int64_t>::max(),
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = INT64_MIN, key upper = 2, keys = [-10, -1, 2].
  EXPECT_THAT(results[0].key_lower, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(results[0].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 1: key lower = 3, key upper = INT64_MAX, keys = [10, INT64_MAX - 1].
  EXPECT_THAT(results[1].key_lower, Eq(3));
  EXPECT_THAT(results[1].key_upper, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max() - 1)));
}

TEST(IntegerIndexBucketUtilTest,
     Split_keyUpperEqualsIntMax_largestKeyEqualsKeyUpper) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max()),
  };

  // Keys = [-10, -1, 2, 10, INT64_MAX].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/std::numeric_limits<int64_t>::min(),
            /*original_key_upper=*/std::numeric_limits<int64_t>::max(),
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(2));
  // Bucket 0: key lower = INT64_MIN, key upper = 2, keys = [-10, -1, 2].
  EXPECT_THAT(results[0].key_lower, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(results[0].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 1: key lower = 3, key upper = INT64_MAX, keys = [10, INT64_MAX].
  EXPECT_THAT(results[1].key_lower, Eq(3));
  EXPECT_THAT(results[1].key_upper, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max())));
}

TEST(IntegerIndexBucketUtilTest,
     Split_keyUpperEqualsIntMax_keyIntMaxExceedingThreshold) {
  std::vector<IntegerIndexData> data = {
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max()),
      IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                       std::numeric_limits<int64_t>::max())};

  // Keys = [-10, -1, 2, 10, INT64_MAX, INT64_MAX, INT64_MAX, INT64_MAX,
  // INT64_MAX].
  std::vector<DataRangeAndBucketInfo> results =
      Split(data, /*original_key_lower=*/std::numeric_limits<int64_t>::min(),
            /*original_key_upper=*/std::numeric_limits<int64_t>::max(),
            /*num_data_threshold=*/3);
  ASSERT_THAT(results, SizeIs(3));
  // Bucket 0: key lower = INT64_MIN, key upper = 2, keys = [-10, -1, 2].
  EXPECT_THAT(results[0].key_lower, Eq(std::numeric_limits<int64_t>::min()));
  EXPECT_THAT(results[0].key_upper, Eq(2));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[0].start, results[0].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -10),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, -1),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 2)));
  // Bucket 1: key lower = 3, key upper = INT_MAX - 1, keys = [10].
  EXPECT_THAT(results[1].key_lower, Eq(3));
  EXPECT_THAT(results[1].key_upper,
              Eq(std::numeric_limits<int64_t>::max() - 1));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[1].start, results[1].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId, 10)));
  // Bucket 2: key lower = INT64_MAX, key upper = INT64_MAX, keys = [INT64_MAX,
  // INT64_MAX, INT64_MAX, INT64_MAX, INT64_MAX].
  EXPECT_THAT(results[2].key_lower, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(results[2].key_upper, Eq(std::numeric_limits<int64_t>::max()));
  EXPECT_THAT(
      std::vector<IntegerIndexData>(results[2].start, results[2].end),
      ElementsAre(IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max()),
                  IntegerIndexData(kDefaultSectionId, kDefaultDocumentId,
                                   std::numeric_limits<int64_t>::max())));
}

}  // namespace

}  // namespace integer_index_bucket_util
}  // namespace lib
}  // namespace icing
