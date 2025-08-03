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

// Copyright 2012 Google Inc. All Rights Reserved.
// Author: ulas@google.com (Ulas Kirazci)
//
// Utilities for fiddling bits.

#ifndef ICING_LEGACY_INDEX_ICING_BIT_UTIL_H_
#define ICING_LEGACY_INDEX_ICING_BIT_UTIL_H_

#include <cstdint>
#include <cstdio>
#include <limits>
#include <vector>

namespace icing {
namespace lib {

// Manipulating bit fields.
//
// x       value containing the bit field(s)
// offset  offset of bit field in x
// len     len of bit field in x
//
// REQUIREMENTS
//
// - x an unsigned integer <= 64 bits
// - offset + len <= sizeof(x) * 8
//
// There is no error checking so you will get garbage if you don't
// ensure the above.
//
// To set a value, use BITFIELD_CLEAR then BITFIELD_OR.

// Shifting by more than the word length is undefined (on ARM it has the
// intended effect, but on Intel it shifts by % word length), so check the
// length).
#define BITFIELD_MASK(len) ((len == 0) ? 0U : ((~uint64_t{0}) >> (64 - (len))))
#define BITFIELD_GET(x, offset, len) (((x) >> (offset)) & BITFIELD_MASK(len))
// The following modify x.
#define BITFIELD_CLEAR(x, offset, len) (x) &= ~(BITFIELD_MASK(len) << (offset))
// We conservatively mask val at len so x won't be corrupted if val >=
// 1 << len.
#define BITFIELD_OR(x, offset, len, val) \
  (x) |= (uint64_t{val} & BITFIELD_MASK(len)) << (offset)

// Number of bits needed to store the range [0, n).
inline uint8_t BitsToStore(uint32_t n) {
  if (n <= 1) {
    return 0;
  } else {
    return 32 - __builtin_clz(n - 1);
  }
}

#define ALIGN_UP(n, alignment) \
  ((((n) + (alignment)-1) / (alignment)) * (alignment))

// Align up to a multiple.
inline uint64_t AlignUp(uint64_t n, uint64_t alignment) {
  return ALIGN_UP(n, alignment);
}

inline bool SumOverflowsUint32(std::vector<uint64_t> values) {
  uint64_t sum = 0L;
  for (uint64_t value : values) {
    sum += value;
  }
  return sum > std::numeric_limits<uint32_t>::max();
}

// VarInt (See
// https://developers.google.com/protocol-buffers/docs/encoding)
#define VAR_INT_MAX_ENCODED_LEN(n_size) (ALIGN_UP(8 * (n_size), 7) / 7)

class VarInt {
 public:
  // 7 bits per byte.
  static size_t MaxEncodedLen(size_t n_size) {
    return VAR_INT_MAX_ENCODED_LEN(n_size);
  }
  static const int kMaxEncodedLen64 = VAR_INT_MAX_ENCODED_LEN(8);

  // Encode n into buf. Return encoded len. buf must be at least
  // kMaxEncodedLen64 long.
  static size_t Encode(uint64_t n, uint8_t *buf) {
    uint8_t *start = buf;
    do {
      *buf = 0x80 | (n & 0x7f);
      n >>= 7;
      buf++;
    } while (n);
    // buf is one past last byte. Last byte must have MSB cleared.
    *(buf - 1) &= 0x7f;
    return buf - start;
  }

  // Decode buf into unsigned integral type pn. Return length
  // decoded. buf must terminate with a byte with MSB cleared. No
  // error checking is done but if buf is null-terminated, Decode
  // won't crash. If decoded doesn't fit into *pn higher order bits
  // will be dropped.
  template <class T>
  static size_t Decode(const uint8_t *buf, T *pn) {
    const uint8_t *start = buf;
    *pn = 0;
    int offset = 0;
    while ((*buf & 0x80)) {
      *pn |= static_cast<T>(*buf & 0x7f) << offset;
      offset += 7;
      buf++;
    }
    // Last byte.
    *pn |= static_cast<T>(*buf) << offset;
    // Buf is pointing to last byte, not one off the end.
    return buf - start + 1;
  }
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_BIT_UTIL_H_
