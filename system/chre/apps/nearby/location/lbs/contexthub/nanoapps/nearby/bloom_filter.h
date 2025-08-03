/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLOOM_FILTER_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLOOM_FILTER_H_

#include <chre.h>
#include <inttypes.h>

#include <cstddef>

namespace nearby {

// Bloom filter to test if an account key is included.
class BloomFilter {
 public:
  // Bloom Filter size is capped by the max value of Length-Type header, i.e.
  // 2^4 = 16.
  static constexpr size_t kMaxBloomFilterByteSize = 16;

  // Constructs a Bloom Filter from a byte array.
  BloomFilter(const uint8_t filter[], size_t size);

  // Returns true if the key is set in the Bloom filter.
  bool MayContain(const uint8_t key[], size_t size);

 private:
  uint8_t filter_[kMaxBloomFilterByteSize] = {0};
  size_t filter_bit_size_;

  // Initializes filter uint8_t array
  // Returns byte size of filter as min(kMaxBloomFilterByteSize, size)
  size_t Init(const uint8_t filter[], size_t size);
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_BLOOM_FILTER_H_
