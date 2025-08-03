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

#include <gtest/gtest.h>

#include <cstdint>

// A straightforward implementation of Length64 for testing
inline int Varint_Length64Old(uint64_t v) {
    // Each byte of output stores 7 bits of "v" until "v" becomes zero
    int nbytes = 0;
    do {
        nbytes++;
        v >>= 7;
    } while (v != 0);
    return nbytes;
}

TEST(VarintTest, Length64) {
    ASSERT_EQ(Varint::Length64(0), 1);
    ASSERT_EQ(Varint::Length64(1), 1);
    ASSERT_EQ(Varint::Length64(127), 1);
    ASSERT_EQ(Varint::Length64(128), 2);
    ASSERT_EQ(Varint::Length64(16383), 2);
    ASSERT_EQ(Varint::Length64(16384), 3);
    // Check around each power of two
    for (int i = 0; i < 63; i++) {
        uint64_t v = (1ull << i);
        ASSERT_EQ(Varint::Length64(v - 1), Varint_Length64Old(v - 1));
        ASSERT_EQ(Varint::Length64(v), Varint_Length64Old(v));
        ASSERT_EQ(Varint::Length64(v + 1), Varint_Length64Old(v + 1));
    }
    ASSERT_EQ(Varint::Length64(((uint64_t)1 << 21) - 1), 3);
    ASSERT_EQ(Varint::Length64((uint64_t)1 << 21), 4);
    ASSERT_EQ(Varint::Length64((uint64_t)1 << 63), Varint::kMax64);
    ASSERT_EQ(Varint::Length64(~0ull), Varint::kMax64);
}

TEST(VarintTest, Encode32) {
    // Encode32 the 28-bit number 1110 0100 1001 1001 1000 0110 0111
    // aka hex E 49 98 67
    // which can be split in groups of 7 bytes as 1110010 0100110 0110000 1100111
    // which should encode to (reverse the bytes, set the MSB to 1):
    // 11100111 10110000 10100110 01110010 aka hex E7 B0 A6 72
    char s[20];
    uint32_t n = 0xe499867;
    unsigned char n_encrypt[5] = {0xe7, 0xb0, 0xa6, 0x72, '\0'};
    char* end_s = Varint::Encode32(s, n);
    // now end_s - s represents the encryption length
    ASSERT_EQ(end_s - s, Varint::Length64(n));
    *end_s = '\0';  // terminate the string
    ASSERT_EQ(std::string(s), std::string(reinterpret_cast<char*>(n_encrypt)));
}

TEST(VarintTest, Encode64) {
    // Encode64 the 60-bit number
    // 1110 0100 1001 1001 1000 0110 0111 1001 0100 0111 0000 1101 1001 1000 1101
    // aka hex E 49 98 67 94 70 D9 8D
    // which can be split in groups of 7 bytes as
    // 1110 0100100 1100110 0001100 1111001 0100011 1000011 0110011 0001101
    // which should encode to (reverse the bytes, set the MSB to 1):
    // 10001101 10110011 11000011 10100011 11111001 10001100 11100110 10100100
    // 00001110 aka hex 8D B3 C3 A3 F9 8C E6 A4 0E
    char s[10];
    uint64_t n = 0xe4998679470d98dull;
    unsigned char n_encrypt[10] = {0x8d, 0xb3, 0xc3, 0xa3, 0xf9, 0x8c, 0xe6, 0xa4, 0x0e, '\0'};
    char* end_s = Varint::Encode64(s, n);
    // now end_s - s represents the encryption length
    ASSERT_EQ(end_s - s, Varint::Length64(n));
    *end_s = '\0';  // terminate the string
    ASSERT_EQ(std::string(s), std::string(reinterpret_cast<char*>(n_encrypt)));
}
