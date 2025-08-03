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
#include "bits_util.h"

#include <gtest/gtest.h>

#include <limits>

namespace dist_proc {
namespace aggregation {
namespace internal {

namespace {

class BitsUtilTest : public ::testing::Test {};

TEST_F(BitsUtilTest, Log2EdgeCases) {
    for (uint8_t i = 0; i < 64; i++) {
        uint64_t n = 1ULL << i;
        EXPECT_EQ(i, BitsUtil::Log2FloorNonZero64(n));
        if (n > 2) {
            EXPECT_EQ(i - 1, BitsUtil::Log2FloorNonZero64(n - 1));
            EXPECT_EQ(i, BitsUtil::Log2FloorNonZero64(n + 1));
        }
    }
}

struct BitsUtilTupleParam {
    uint64_t input;
    uint8_t result;
};

class BitsUtilTupleTest : public ::testing::TestWithParam<BitsUtilTupleParam> {};

TEST_P(BitsUtilTupleTest, CorrectResult) {
    BitsUtilTupleParam params = GetParam();
    EXPECT_EQ(BitsUtil::Log2FloorNonZero64(params.input), params.result);
}

const BitsUtilTupleParam cases[]{{0x1LL, 0},
                                 {0x2LL, 1},
                                 {0x4LL, 2},
                                 {0x5LL, 2},
                                 {0x7LL, 2},
                                 {0x9LL, 3},
                                 {0x10LL, 4},
                                 {0x20LL, 5},
                                 {0x40LL, 6},
                                 {0x80LL, 7},
                                 {0x100LL, 8},
                                 {0x200LL, 9},
                                 {0x402LL, 10},
                                 {0x800LL, 11},
                                 {0x10b0LL, 12},
                                 {0x200aLL, 13},
                                 {0x4000LL, 14},
                                 {0x8000LL, 15},
                                 {0x10002LL, 16},
                                 {0x8004000LL, 27},
                                 {0x10002000LL, 28},
                                 {0x40000020LL, 30},
                                 {0x100000002LL, 32},
                                 {0x100000002LL, 32},
                                 {0x800020800LL, 35},
                                 {0x1040000020LL, 36},
                                 {0x2000000000LL, 37},
                                 {0x4000000000LL, 38},
                                 {0x8000000000LL, 39},
                                 {0xb000000404LL, 39},
                                 {0x8000000000LL, 39},
                                 {0x10000000000LL, 40},
                                 {0x20008004000LL, 41},
                                 {0x80000010000LL, 43},
                                 {0x200000000004LL, 45},
                                 {0x400000000000LL, 46},
                                 {0x408000000000LL, 46},
                                 {0x2000000000422LL, 49},
                                 {0x108840000801c4LL, 52},
                                 {0x4000b800200604LL, 54},
                                 {0x44020118004900LL, 54},
                                 {0xc4020118004900LL, 55},
                                 {0x101964002152400LL, 56},
                                 {0x100004000000000LL, 56},
                                 {0x298281040000020LL, 57},
                                 {0x404014310000044LL, 58},
                                 {0x586000401200423LL, 58},
                                 {0x800002000000000LL, 59},
                                 {0x800620001000000LL, 59},
                                 {0x1000408000000000LL, 60},
                                 {0x27908a643028d1e5LL, 61},
                                 {0x2404014310000044LL, 61},
                                 {0x2008000000200021LL, 61},
                                 {0x6008000000200021LL, 62},
                                 {0x40c4020118004900LL, 62},
                                 {0x67908a643028d1e5LL, 62},
                                 {0xc52df86bf16be929LL, 63},
                                 {0xefffefefffffffffLL, 63},
                                 {0x80fa6201c594b6a9LL, 63},
                                 {0xefebdfff7fafffdfLL, 63}};

INSTANTIATE_TEST_SUITE_P(BitsUtilTestCases, BitsUtilTupleTest, ::testing::ValuesIn(cases));

}  // namespace

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
