/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <assert.h>

#include <bit>
#include <cstdint>
#include <limits>

class BitsUtil {
public:
    // ⌊log2(n)⌋, resp. numbers of bits needed to represent n in binary encoding
    // minus one. Input cannot be zero.
    // Same as util/bits/Bits::Log2FloorNonZero64(..).
    static uint8_t Log2FloorNonZero64(uint64_t n);

private:
    static uint8_t CountLeadingZeros64(uint64_t x);
};

inline uint8_t BitsUtil::Log2FloorNonZero64(uint64_t n) {
    assert(n != 0);
    return std::numeric_limits<uint64_t>::digits - CountLeadingZeros64(n) - 1;
}

inline uint8_t BitsUtil::CountLeadingZeros64(uint64_t x) {
    uint8_t zeroes = 60;
    if (x >> 32) {
        zeroes -= 32;
        x >>= 32;
    }
    if (x >> 16) {
        zeroes -= 16;
        x >>= 16;
    }
    if (x >> 8) {
        zeroes -= 8;
        x >>= 8;
    }
    if (x >> 4) {
        zeroes -= 4;
        x >>= 4;
    }
    return "\4\3\2\2\1\1\1\1\0\0\0\0\0\0\0"[x] + zeroes;
}
