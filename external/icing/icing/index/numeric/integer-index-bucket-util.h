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

#ifndef ICING_INDEX_NUMERIC_INTEGER_INDEX_BUCKET_UTIL_H_
#define ICING_INDEX_NUMERIC_INTEGER_INDEX_BUCKET_UTIL_H_

#include <cstdint>
#include <utility>
#include <vector>

#include "icing/index/numeric/integer-index-data.h"

namespace icing {
namespace lib {

namespace integer_index_bucket_util {

// A wrapper struct that contains information of a bucket.
// - The bucket contains data within the iterator [start, end).
// - Bucket range is [key_lower, key_upper], and all data within [start, end)
//   should have keys in the bucket range.
//
// Note: the caller should make sure the lifecycle of data vector is longer than
// instances of this wrapper struct.
struct DataRangeAndBucketInfo {
  std::vector<IntegerIndexData>::iterator start;
  std::vector<IntegerIndexData>::iterator end;
  int64_t key_lower;
  int64_t key_upper;

  explicit DataRangeAndBucketInfo(
      std::vector<IntegerIndexData>::iterator start_in,
      std::vector<IntegerIndexData>::iterator end_in, int64_t key_lower_in,
      int64_t key_upper_in)
      : start(std::move(start_in)),
        end(std::move(end_in)),
        key_lower(key_lower_in),
        key_upper(key_upper_in) {}
};

// Helper function to split data (that are originally in a bucket with range
// [original_key_lower, original_key_upper]) into different buckets according to
// num_data_threshold.
// - The input vector `data` will be sorted by key in ascending order (unless
//   there's no need to split in which case data is returned unmodified)
// - Data with the same key will be in the same bucket even if # of them exceed
//   num_data_threshold.
// - Range of all buckets will be disjoint, and the range union will be
//   [original_key_lower, original_key_upper].
// - Data slice (i.e. [start, end)) can be empty.
//
// REQUIRES:
// - original_key_lower < original_key_upper
// - num_data_threshold > 0
// - Keys of all data are in range [original_key_lower, original_key_upper]
//
// Returns: a vector of DataRangeAndBucketInfo that contain all bucket info
//   after splitting. Also the returned vector should contain at least one
//   bucket, otherwise it is considered an error.
std::vector<DataRangeAndBucketInfo> Split(std::vector<IntegerIndexData>& data,
                                          int64_t original_key_lower,
                                          int64_t original_key_upper,
                                          int32_t num_data_threshold);

}  // namespace integer_index_bucket_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_INTEGER_INDEX_BUCKET_UTIL_H_
