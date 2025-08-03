/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "aidl/PhysicalQuantityTypes.h"

using std::chrono_literals::operator""ms;
using std::chrono_literals::operator""s;
using std::chrono_literals::operator""min;
using testing::Eq;
using testing::Gt;
using testing::Lt;

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

// compile time tests
static_assert(Cycles(10) * 2 == Cycles(20));
static_assert(100_hz + 200_hz == Frequency(300));
static_assert(Cycles(100) / 1s == Frequency(100));

TEST(PhysicalQuantityTypeTest, type_check_basic_cycles) {
    Cycles a(0);
    Cycles b(-1);
    Cycles c(8);
    Cycles d(11);
    Cycles e(8);
    EXPECT_THAT(a, Eq(a));
    EXPECT_THAT(e, Eq(c));
    EXPECT_THAT(c, Eq(e));
    EXPECT_THAT(b, Lt(a));
    EXPECT_THAT(a, Gt(b));
    EXPECT_THAT(a + b, Eq(b));
    EXPECT_THAT(b + c, Eq(Cycles(7)));
    EXPECT_THAT(c - b, Eq(Cycles(9)));
    EXPECT_THAT(c * 8, Eq(Cycles(64)));
    EXPECT_THAT(3 * c, Eq(Cycles(24)));
    EXPECT_THAT(c / 2, Eq(Cycles(4)));
}

TEST(PhysicalQuantityTypeTest, type_check_basic_frequency) {
    Frequency a(1000);
    Frequency b(1111);
    EXPECT_THAT(a, Eq(a));
    EXPECT_THAT(a + Frequency(111), Eq(b));
    EXPECT_THAT(b, Gt(a));
    EXPECT_THAT(a, Lt(b));
}

TEST(PhysicalQuantityTypeTest, freq_cycles_time_conversions) {
    EXPECT_THAT(Cycles(1000) / 2s, Eq(500_hz));
    EXPECT_THAT(Cycles(1000) / 500ms, Eq(2000_hz));

    EXPECT_THAT(1000_hz * 12ms, Eq(Cycles(12)));
    EXPECT_THAT(6min * 500_hz, Eq(Cycles(180000)));
    EXPECT_THAT(1000_hz * 2min, Eq(Cycles(120000)));
}

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
