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

#include "location/lbs/contexthub/nanoapps/nearby/bloom_filter.h"

#include <cstring>

#include "location/lbs/contexthub/nanoapps/nearby/crypto/sha2.h"

namespace nearby {

inline static uint32_t BSWAP32(uint32_t value) {
#if defined(__clang__) || \
    (defined(__GNUC__) && \
     ((__GNUC__ == 4 && __GNUC_MINOR__ >= 8) || __GNUC__ >= 5))
  return __builtin_bswap32(value);
#else
  uint32_t Byte0 = value & 0x000000FF;
  uint32_t Byte1 = value & 0x0000FF00;
  uint32_t Byte2 = value & 0x00FF0000;
  uint32_t Byte3 = value & 0xFF000000;
  return (Byte0 << 24) | (Byte1 << 8) | (Byte2 >> 8) | (Byte3 >> 24);
#endif
}

BloomFilter::BloomFilter(const uint8_t filter[], size_t size)
    : filter_bit_size_(Init(filter, size) * 8) {}

size_t BloomFilter::Init(const uint8_t filter[], size_t size) {
  size_t minFilterSize =
      (kMaxBloomFilterByteSize > size) ? size : kMaxBloomFilterByteSize;
  memcpy(filter_, filter, minFilterSize);
  return minFilterSize;
}

bool BloomFilter::MayContain(const uint8_t key[], size_t size) {
  uint32_t hash[SHA2_HASH_WORDS];
  sha256(key, static_cast<uint32_t>(size), hash, sizeof(hash));

  for (size_t i = 0; i < SHA2_HASH_WORDS; i++) {
    hash[i] = BSWAP32(hash[i]);
    uint32_t bitPos = hash[i] % (filter_bit_size_);
    if (!(filter_[bitPos / 8] & (1 << (bitPos % 8)))) {
      return false;
    }
  }
  return true;
}

}  // namespace nearby
