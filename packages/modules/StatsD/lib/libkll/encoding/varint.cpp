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
#include "varint.h"

#include <cstdint>

char* Varint::Encode32(char* sptr, uint32_t v) {
    return Encode32Inline(sptr, v);
}

char* Varint::Encode64(char* sptr, uint64_t v) {
    if (v < (1u << 28)) {
        return Varint::Encode32(sptr, v);
    } else {
        // Operate on characters as unsigneds
        unsigned char* ptr = reinterpret_cast<unsigned char*>(sptr);
        // Rather than computing four subresults and or'ing each with 0x80,
        // we can do two ors now.  (Doing one now wouldn't work.)
        const uint32_t x32 = v | (1 << 7) | (1 << 21);
        const uint32_t y32 = v | (1 << 14) | (1 << 28);
        *(ptr++) = x32;
        *(ptr++) = y32 >> 7;
        *(ptr++) = x32 >> 14;
        *(ptr++) = y32 >> 21;
        if (v < (1ull << 35)) {
            *(ptr++) = v >> 28;
            return reinterpret_cast<char*>(ptr);
        } else {
            *(ptr++) = (v >> 28) | (1 << 7);
            return Varint::Encode32(reinterpret_cast<char*>(ptr), v >> 35);
        }
    }
}
