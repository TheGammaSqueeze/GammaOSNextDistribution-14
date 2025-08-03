/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "minikin/Measurement.h"

#include <gtest/gtest.h>

#include "UnicodeUtils.h"

namespace minikin {

float getAdvance(const float* advances, const char* src) {
    const size_t BUF_SIZE = 256;
    uint16_t buf[BUF_SIZE];
    size_t offset;
    size_t size;
    ParseUnicode(buf, BUF_SIZE, src, &size, &offset);
    return getRunAdvance(advances, buf, 0, size, offset);
}

void distributeAdvances(float* advances, const char* src, int count) {
    const size_t BUF_SIZE = 256;
    uint16_t buf[BUF_SIZE];
    size_t offset;
    size_t size;
    ParseUnicode(buf, BUF_SIZE, src, &size, &offset);
    distributeAdvances(advances, buf, offset, count);
}

// Latin fi
TEST(Measurement, getRunAdvance_fi) {
    const float unligated[] = {30.0, 20.0};
    EXPECT_EQ(0.0, getAdvance(unligated, "| 'f' 'i'"));
    EXPECT_EQ(30.0, getAdvance(unligated, "'f' | 'i'"));
    EXPECT_EQ(50.0, getAdvance(unligated, "'f' 'i' |"));

    const float ligated[] = {40.0, 0.0};
    EXPECT_EQ(0.0, getAdvance(ligated, "| 'f' 'i'"));
    EXPECT_EQ(20.0, getAdvance(ligated, "'f' | 'i'"));
    EXPECT_EQ(40.0, getAdvance(ligated, "'f' 'i' |"));
}

TEST(Measurement, getRunAdvance_control_characters) {
    const float unligated[] = {30.0, 20.0, 0.0, 0.0};
    EXPECT_EQ(0.0, getAdvance(unligated, "| 'f' 'i' U+2066 U+202C"));
    EXPECT_EQ(30.0, getAdvance(unligated, "'f' | 'i' U+2066 U+202C"));
    EXPECT_EQ(50.0, getAdvance(unligated, "'f' 'i' | U+2066 U+202C"));
    EXPECT_EQ(50.0, getAdvance(unligated, "'f' 'i' U+2066 | U+202C"));
    EXPECT_EQ(50.0, getAdvance(unligated, "'f' 'i' U+2066 U+202C |"));

    const float liagated[] = {40.0, 0.0, 0.0, 0.0};
    EXPECT_EQ(0.0, getAdvance(liagated, "| 'f' 'i' U+2066 U+202C"));
    EXPECT_EQ(20.0, getAdvance(liagated, "'f' | 'i' U+2066 U+202C"));
    EXPECT_EQ(40.0, getAdvance(liagated, "'f' 'i' | U+2066 U+202C"));
    EXPECT_EQ(40.0, getAdvance(liagated, "'f' 'i' U+2066 | U+202C"));
    EXPECT_EQ(40.0, getAdvance(liagated, "'f' 'i' U+2066 U+202C |"));
}

// Devanagari ka+virama+ka
TEST(Measurement, getRunAdvance_kka) {
    const float unligated[] = {30.0, 0.0, 30.0};
    EXPECT_EQ(0.0, getAdvance(unligated, "| U+0915 U+094D U+0915"));
    EXPECT_EQ(30.0, getAdvance(unligated, "U+0915 | U+094D U+0915"));
    EXPECT_EQ(30.0, getAdvance(unligated, "U+0915 U+094D | U+0915"));
    EXPECT_EQ(60.0, getAdvance(unligated, "U+0915 U+094D U+0915 |"));

    const float ligated[] = {30.0, 0.0, 0.0};
    EXPECT_EQ(0.0, getAdvance(ligated, "| U+0915 U+094D U+0915"));
    EXPECT_EQ(30.0, getAdvance(ligated, "U+0915 | U+094D U+0915"));
    EXPECT_EQ(30.0, getAdvance(ligated, "U+0915 U+094D | U+0915"));
    EXPECT_EQ(30.0, getAdvance(ligated, "U+0915 U+094D U+0915 |"));
}

TEST(Measurement, distributeAdvances_fi) {
    float ligated[] = {20.0, 0.0};
    distributeAdvances(ligated, "| 'f' 'i' ", 2);
    EXPECT_EQ(ligated[0], 10.0);
    EXPECT_EQ(ligated[1], 10.0);
}

TEST(Measurement, distributeAdvances_non_zero_start) {
    // Note that advance[i] corresponding to (i + start)-th character.
    float ligated[] = {20.0, 0.0};
    distributeAdvances(ligated, "'a' 'b' | 'f' 'i' ", 2);
    EXPECT_EQ(ligated[0], 10.0);
    EXPECT_EQ(ligated[1], 10.0);
}

TEST(Measurement, distributeAdvances_non_zero_start_with_control_characters) {
    // Note that advance[i] corresponding to (i + start)-th character.
    float ligated[] = {20.0, 0.0, 0.0, 0.0};
    distributeAdvances(ligated, "'a' U+2066 | 'f' 'i' U+2066 U+202C", 4);
    EXPECT_EQ(ligated[0], 10.0);
    EXPECT_EQ(ligated[1], 10.0);
    EXPECT_EQ(ligated[2], 0.0);
    EXPECT_EQ(ligated[3], 0.0);
}

TEST(Measurement, distributeAdvances_with_count) {
    // Note that advance[i] corresponding to (i + start)-th character.
    float ligated[] = {20.0, 0.0, 30.0, 0.0};
    distributeAdvances(ligated, "'a' 'b' | 'f' 'i' 'f' 'i' ", 2);
    EXPECT_EQ(ligated[0], 10.0);
    EXPECT_EQ(ligated[1], 10.0);
    // Count is 2, so it won't change the rest of the array.
    EXPECT_EQ(ligated[2], 30.0);
    EXPECT_EQ(ligated[3], 0.0);
}

TEST(Measurement, distributeAdvances_control_characters) {
    float ligated[] = {20.0, 0.0, 0.0, 0.0};
    distributeAdvances(ligated, "| 'f' 'i' U+2066 U+202C", 4);
    EXPECT_EQ(ligated[0], 10.0);
    EXPECT_EQ(ligated[1], 10.0);
    EXPECT_EQ(ligated[2], 0.0);
    EXPECT_EQ(ligated[3], 0.0);
}

TEST(Measurement, distributeAdvances_surrogate) {
    float advances[] = {20.0, 0.0, 0.0, 0.0};
    distributeAdvances(advances, "| U+D83D U+DE00 U+2066 U+202C", 4);
    EXPECT_EQ(advances[0], 20.0);
    EXPECT_EQ(advances[1], 0.0);
    EXPECT_EQ(advances[2], 0.0);
    EXPECT_EQ(advances[3], 0.0);
}

TEST(Measurement, distributeAdvances_surrogate_in_ligature) {
    // If a ligature contains surrogates, advances is assigned to the first
    // character in surrogate.
    float ligated[] = {40.0, 0.0, 0.0, 0.0};
    distributeAdvances(ligated, "| U+D83D U+DE00 U+D83D U+DE01", 4);
    EXPECT_EQ(ligated[0], 20.0);
    EXPECT_EQ(ligated[1], 0.0);
    EXPECT_EQ(ligated[2], 20.0);
    EXPECT_EQ(ligated[3], 0.0);
}

}  // namespace minikin
