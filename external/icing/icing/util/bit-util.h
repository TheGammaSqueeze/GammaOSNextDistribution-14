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

#ifndef ICING_UTIL_BIT_UTIL_H_
#define ICING_UTIL_BIT_UTIL_H_

#include <cstdint>

namespace icing {
namespace lib {

namespace bit_util {

// Manipulating bit fields.
//
// value       value containing the bit field(s)
// lsb_offset  offset of bit field in value, starting from the least significant
//             bit. for example, the '1' in '0100' has a lsb_offset of 2
// len         len of bit field in value
//
// REQUIREMENTS
//
// - value is an unsigned integer <= 64 bits
// - lsb_offset + len <= sizeof(value) * 8
//
// There is no error checking so you will get garbage if you don't
// ensure the above.

// Shifting by more than the word length is undefined (on ARM it has the
// intended effect, but on Intel it shifts by % word length), so check the
// length).
inline uint64_t BitfieldMask(uint32_t len) {
  return ((len == 0) ? 0U : ((~uint64_t{0}) >> (64 - (len))));
}

inline void BitfieldClear(uint32_t lsb_offset, uint32_t len,
                          uint8_t* value_out) {
  *value_out &= ~(BitfieldMask(len) << lsb_offset);
}

inline void BitfieldClear(uint32_t lsb_offset, uint32_t len,
                          uint16_t* value_out) {
  *value_out &= ~(BitfieldMask(len) << lsb_offset);
}

inline void BitfieldClear(uint32_t lsb_offset, uint32_t len,
                          uint32_t* value_out) {
  *value_out &= ~(BitfieldMask(len) << lsb_offset);
}

inline void BitfieldClear(uint32_t lsb_offset, uint32_t len,
                          uint64_t* value_out) {
  *value_out &= ~(BitfieldMask(len) << lsb_offset);
}

inline uint64_t BitfieldGet(uint64_t value, uint32_t lsb_offset, uint32_t len) {
  return ((value) >> (lsb_offset)) & BitfieldMask(len);
}

inline void BitfieldSet(uint8_t new_value, uint32_t lsb_offset, uint32_t len,
                        uint8_t* value_out) {
  BitfieldClear(lsb_offset, len, value_out);

  // We conservatively mask new_value at len so value won't be corrupted if
  // new_value >= (1 << len).
  *value_out |= (new_value & BitfieldMask(len)) << (lsb_offset);
}

inline void BitfieldSet(uint16_t new_value, uint32_t lsb_offset, uint32_t len,
                        uint16_t* value_out) {
  BitfieldClear(lsb_offset, len, value_out);

  // We conservatively mask new_value at len so value won't be corrupted if
  // new_value >= (1 << len).
  *value_out |= (new_value & BitfieldMask(len)) << (lsb_offset);
}

inline void BitfieldSet(uint32_t new_value, uint32_t lsb_offset, uint32_t len,
                        uint32_t* value_out) {
  BitfieldClear(lsb_offset, len, value_out);

  // We conservatively mask new_value at len so value won't be corrupted if
  // new_value >= (1 << len).
  *value_out |= (new_value & BitfieldMask(len)) << (lsb_offset);
}

inline void BitfieldSet(uint64_t new_value, uint32_t lsb_offset, uint32_t len,
                        uint64_t* value_out) {
  BitfieldClear(lsb_offset, len, value_out);

  // We conservatively mask new_value at len so value won't be corrupted if
  // new_value >= (1 << len).
  *value_out |= (new_value & BitfieldMask(len)) << (lsb_offset);
}

}  // namespace bit_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_BIT_UTIL_H_
