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

#include <cstdint>

#include "bits_util.h"

class Varint {
public:
    // Maximum lengths of varint encoding of uint64
    static constexpr int kMax64 = 10;

    // REQUIRES   "ptr" points to a buffer of length sufficient to hold "v".
    // EFFECTS    Encodes "v" into "ptr" and returns a pointer to the
    //            byte just past the last encoded byte.
    static char* Encode32(char* ptr, uint32_t v);
    static char* Encode64(char* ptr, uint64_t v);

    // EFFECTS    Returns the encoding length of the specified value.
    static int Length64(uint64_t v);

private:
    // A fully inlined version of Encode32: useful in the most time critical
    // routines, but its code size is large
    static char* Encode32Inline(char* ptr, uint32_t v);
};

inline int Varint::Length64(uint64_t v) {
    // This computes value == 0 ? 1 : floor(log2(v)) / 7 + 1 using an explicit
    // multiplication to implement the division of a number in the 1..63 range.
    // Explicit OR 0x1 to handle v == 0.
    uint32_t log2value = BitsUtil::Log2FloorNonZero64(v | 0x1);
    return static_cast<int>((log2value * 9 + 73) / 64);
}

inline char* Varint::Encode32Inline(char* sptr, uint32_t v) {
    // Operate on characters as unsigneds
    uint8_t* ptr = reinterpret_cast<uint8_t*>(sptr);
    static const uint32_t B = 128;
    if (v < (1 << 7)) {
        *(ptr++) = static_cast<uint8_t>(v);
    } else if (v < (1 << 14)) {
        *(ptr++) = static_cast<uint8_t>(v | B);
        *(ptr++) = static_cast<uint8_t>(v >> 7);
    } else if (v < (1 << 21)) {
        *(ptr++) = static_cast<uint8_t>(v | B);
        *(ptr++) = static_cast<uint8_t>((v >> 7) | B);
        *(ptr++) = static_cast<uint8_t>(v >> 14);
    } else if (v < (1 << 28)) {
        *(ptr++) = static_cast<uint8_t>(v | B);
        *(ptr++) = static_cast<uint8_t>((v >> 7) | B);
        *(ptr++) = static_cast<uint8_t>((v >> 14) | B);
        *(ptr++) = static_cast<uint8_t>(v >> 21);
    } else {
        *(ptr++) = static_cast<uint8_t>(v | B);
        *(ptr++) = static_cast<uint8_t>((v >> 7) | B);
        *(ptr++) = static_cast<uint8_t>((v >> 14) | B);
        *(ptr++) = static_cast<uint8_t>((v >> 21) | B);
        *(ptr++) = static_cast<uint8_t>(v >> 28);
    }
    return reinterpret_cast<char*>(ptr);
}
