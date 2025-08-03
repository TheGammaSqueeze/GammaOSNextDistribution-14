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

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <limits>
#include <utility>
#include <vector>

#include "icing/index/numeric/integer-index-data.h"

namespace icing {
namespace lib {

namespace integer_index_bucket_util {

namespace {

// Helper function to determine if data slice [start, end) forms a "full
// single-range bucket".
//
// Full single-range bucket: keys of all data are identical and # of them exceed
// num_data_threshold.
//
// REQUIRES: data slice [start, end) are sorted by key.
inline bool WouldBeFullSingleRangeBucket(
    const std::vector<IntegerIndexData>::iterator& start,
    const std::vector<IntegerIndexData>::iterator& end,
    int32_t num_data_threshold) {
  return std::distance(start, end) > num_data_threshold &&
         start->key() == (end - 1)->key();
}

// Helper function to determine if a bucket is full single-range.
//
// REQUIRES:
//   bucket.key_lower <= [bucket.start, bucket.end)->key() <= bucket.key_upper
inline bool IsFullSingleRangeBucket(const DataRangeAndBucketInfo& bucket,
                                    int32_t num_data_threshold) {
  return bucket.key_lower == bucket.key_upper &&
         WouldBeFullSingleRangeBucket(bucket.start, bucket.end,
                                      num_data_threshold);
}

// Helper function to append new bucket(s) with corresponding data slice for
// range [curr_key_lower, last_key] where last_key = (it_end - 1)->key().
//
// Also it handles an edge case:
// If data slice [it_start, it_end) forms a "full single-range bucket" (see
// WouldBeFullSingleRangeBucket for definition), then we have to put them into a
// single range bucket [last_key, last_key] instead of [curr_key_lower,
// last_key]. Also we have to deal with range [curr_key_lower, last_key - 1]:
// - If the previous bucket exists and it is not a "full single-range bucket",
//   then merge [curr_key_lower, last_key - 1] into the previous bucket, i.e.
//   change the previous bucket's key_upper to (last_key - 1). Then we will end
//   up having:
//   - [prev_bucket.key_lower, last_key - 1]
//   - [last_key, last_key]
// - Otherwise, we have to create [curr_key_lower, last_key - 1] with
//   empty data. Then we will end up having (Note: prev_bucket.key_upper ==
//   curr_key_lower - 1):
//   - [prev_bucket.key_lower, curr_key_lower - 1]
//   - [curr_key_lower, last_key - 1]
//   - [last_key, last_key]
// This will avoid split bucket being called too frequently.
// For example, original_key_lower = 0, original_key_upper = 50. If we have
// (num_data_threshold + 1) data with key = 20 and another data with key = 40:
// - Without this part, we will split them into [[0, 20], [21, 50]]. Then when
//   adding data with key = 10 next round, we will invoke split again and split
//   [0, 20] to [[0, 10], [11, 20]].
// - With this part, we will split them into [[0, 19], [20, 20], [21, 50]],
//   which will avoid splitting in the next round for key = 20.
//
// REQUIRES: it_start < it_end
void AppendNewBuckets(const std::vector<IntegerIndexData>::iterator& it_start,
                      const std::vector<IntegerIndexData>::iterator& it_end,
                      int64_t curr_key_lower, int32_t num_data_threshold,
                      std::vector<DataRangeAndBucketInfo>& results) {
  int64_t last_key = (it_end - 1)->key();
  if (curr_key_lower < last_key &&
      WouldBeFullSingleRangeBucket(it_start, it_end, num_data_threshold)) {
    if (!results.empty() &&
        !IsFullSingleRangeBucket(results.back(), num_data_threshold)) {
      // Previous bucket is not full single-range, so merge it to now hold the
      // range [prev_bucket.key_lower, last_key - 1].
      results.back().key_upper = last_key - 1;
    } else {
      // There is either no previous bucket or the previous bucket is full
      // single-range. So add an empty bucket for the range [curr_key_lower,
      // last_key - 1].
      results.push_back(DataRangeAndBucketInfo(it_start, it_start,
                                               curr_key_lower, last_key - 1));
    }
    curr_key_lower = last_key;
  }
  results.push_back(
      DataRangeAndBucketInfo(it_start, it_end, curr_key_lower, last_key));
}

}  // namespace

std::vector<DataRangeAndBucketInfo> Split(std::vector<IntegerIndexData>& data,
                                          int64_t original_key_lower,
                                          int64_t original_key_upper,
                                          int32_t num_data_threshold) {
  // Early return if there is no need to split.
  if (data.size() <= num_data_threshold) {
    return {DataRangeAndBucketInfo(data.begin(), data.end(), original_key_lower,
                                   original_key_upper)};
  }

  // Sort data by key.
  std::sort(
      data.begin(), data.end(),
      [](const IntegerIndexData& lhs, const IntegerIndexData& rhs) -> bool {
        return lhs.key() < rhs.key();
      });

  std::vector<DataRangeAndBucketInfo> results;
  int64_t curr_key_lower = original_key_lower;
  // Sliding window [it_start, it_end) to separate data into different buckets.
  auto it_start = data.begin();
  auto it_end = data.begin();
  while (it_end != data.end()) {
    // Attempt to extend it_end by 1, but we have to include all data with the
    // same key since they cannot be separated into different buckets. Also use
    // extend_it_end to avoid modifying it_end directly. For some edge cases,
    // the extension in a single round is extremely large (i.e. a lot of data
    // have the same key), and we want to separate them. For example:
    // - key = 0: 5 data
    // - key = 1: num_data_threshold - 1 data
    // In the second round, # of data in the sliding window will exceed the
    // threshold. We want to separate all data with key = 0 into a single bucket
    // instead of putting key = 0 and key = 1 together. Therefore, using
    // extend_it_end allow us to preserve it_end of the previous round and be
    // able to deal with this case.
    auto extend_it_end = it_end + 1;
    while (extend_it_end != data.end() &&
           it_end->key() == extend_it_end->key()) {
      ++extend_it_end;
    }

    if (std::distance(it_start, extend_it_end) > num_data_threshold &&
        it_start != it_end) {
      // Split data between [it_start, it_end) into range [curr_key_lower,
      // (it_end - 1)->key()].
      AppendNewBuckets(it_start, it_end, curr_key_lower, num_data_threshold,
                       results);

      // it_end at this moment won't be data.end(), so the last element of the
      // new bucket can't have key == INT64_MAX. Therefore, it is safe to set
      // curr_key_lower as ((it_end - 1)->key() + 1).
      curr_key_lower = (it_end - 1)->key() + 1;
      it_start = it_end;
    }
    it_end = extend_it_end;
  }

  // Handle the final range [curr_key_lower, original_key_upper].
  if (curr_key_lower <= original_key_upper) {
    if (it_start != it_end) {
      AppendNewBuckets(it_start, it_end, curr_key_lower, num_data_threshold,
                       results);

      // AppendNewBuckets only handles range [curr_key_lower, (it_end -
      // 1)->key()], so we have to handle range [(it_end - 1)->key() + 1,
      // original_key_upper] if needed.
      int64_t last_key = (it_end - 1)->key();
      if (last_key != std::numeric_limits<int64_t>::max() &&
          last_key + 1 <= original_key_upper) {
        if (!results.empty() &&
            !IsFullSingleRangeBucket(results.back(), num_data_threshold)) {
          results.back().key_upper = original_key_upper;
        } else {
          results.push_back(DataRangeAndBucketInfo(
              it_start, it_start, last_key + 1, original_key_upper));
        }
      }
    } else {
      results.push_back(DataRangeAndBucketInfo(it_start, it_end, curr_key_lower,
                                               original_key_upper));
    }
  }

  return results;
}

}  // namespace integer_index_bucket_util

}  // namespace lib
}  // namespace icing
