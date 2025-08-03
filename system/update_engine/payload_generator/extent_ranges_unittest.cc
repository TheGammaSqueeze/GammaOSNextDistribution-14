//
// Copyright (C) 2010 The Android Open Source Project
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
//

#include "update_engine/payload_generator/extent_ranges.h"

#include <vector>

#include <base/stl_util.h>
#include <gtest/gtest.h>

#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/payload_consumer/payload_constants.h"

using std::vector;
using chromeos_update_engine::operator==;

namespace chromeos_update_engine {

class ExtentRangesTest : public ::testing::Test {};

namespace {
void ExpectRangeEq(const ExtentRanges& ranges,
                   const uint64_t* expected,
                   size_t sz,
                   int line) {
  uint64_t blocks = 0;
  for (size_t i = 1; i < sz; i += 2) {
    blocks += expected[i];
  }
  ASSERT_EQ(blocks, ranges.blocks()) << "line: " << line;

  const ExtentRanges::ExtentSet& result = ranges.extent_set();
  ExtentRanges::ExtentSet::const_iterator it = result.begin();
  for (size_t i = 0; i < sz; i += 2) {
    ASSERT_FALSE(it == result.end()) << "line: " << line;
    ASSERT_EQ(expected[i], it->start_block()) << "line: " << line;
    ASSERT_EQ(expected[i + 1], it->num_blocks()) << "line: " << line;
    ++it;
  }
}

#define ASSERT_RANGE_EQ(ranges, var) \
  ASSERT_NO_FATAL_FAILURE(ExpectRangeEq(ranges, var, base::size(var), __LINE__))

void ExpectRangesOverlapOrTouch(uint64_t a_start,
                                uint64_t a_num,
                                uint64_t b_start,
                                uint64_t b_num) {
  ASSERT_TRUE(ExtentRanges::ExtentsOverlapOrTouch(
      ExtentForRange(a_start, a_num), ExtentForRange(b_start, b_num)));
  ASSERT_TRUE(ExtentRanges::ExtentsOverlapOrTouch(
      ExtentForRange(b_start, b_num), ExtentForRange(a_start, a_num)));
}

void ExpectFalseRangesOverlapOrTouch(uint64_t a_start,
                                     uint64_t a_num,
                                     uint64_t b_start,
                                     uint64_t b_num) {
  ASSERT_FALSE(ExtentRanges::ExtentsOverlapOrTouch(
      ExtentForRange(a_start, a_num), ExtentForRange(b_start, b_num)));
  ASSERT_FALSE(ExtentRanges::ExtentsOverlapOrTouch(
      ExtentForRange(b_start, b_num), ExtentForRange(a_start, a_num)));
  ASSERT_FALSE(ExtentRanges::ExtentsOverlap(ExtentForRange(a_start, a_num),
                                            ExtentForRange(b_start, b_num)));
  ASSERT_FALSE(ExtentRanges::ExtentsOverlap(ExtentForRange(b_start, b_num),
                                            ExtentForRange(a_start, a_num)));
}

void ExpectRangesOverlap(uint64_t a_start,
                         uint64_t a_num,
                         uint64_t b_start,
                         uint64_t b_num) {
  ASSERT_TRUE(ExtentRanges::ExtentsOverlap(ExtentForRange(a_start, a_num),
                                           ExtentForRange(b_start, b_num)));
  ASSERT_TRUE(ExtentRanges::ExtentsOverlap(ExtentForRange(b_start, b_num),
                                           ExtentForRange(a_start, a_num)));
  ASSERT_TRUE(ExtentRanges::ExtentsOverlapOrTouch(
      ExtentForRange(a_start, a_num), ExtentForRange(b_start, b_num)));
  ASSERT_TRUE(ExtentRanges::ExtentsOverlapOrTouch(
      ExtentForRange(b_start, b_num), ExtentForRange(a_start, a_num)));
}

void ExpectFalseRangesOverlap(uint64_t a_start,
                              uint64_t a_num,
                              uint64_t b_start,
                              uint64_t b_num) {
  ASSERT_FALSE(ExtentRanges::ExtentsOverlap(ExtentForRange(a_start, a_num),
                                            ExtentForRange(b_start, b_num)));
  ASSERT_FALSE(ExtentRanges::ExtentsOverlap(ExtentForRange(b_start, b_num),
                                            ExtentForRange(a_start, a_num)));
}

}  // namespace

TEST(ExtentRangesTest, ExtentsOverlapTest) {
  ASSERT_NO_FATAL_FAILURE(ExpectRangesOverlapOrTouch(10, 20, 30, 10));
  ASSERT_NO_FATAL_FAILURE(ExpectRangesOverlap(10, 20, 25, 10));
  ASSERT_NO_FATAL_FAILURE(ExpectFalseRangesOverlapOrTouch(10, 20, 35, 10));
  ASSERT_NO_FATAL_FAILURE(ExpectFalseRangesOverlap(10, 20, 30, 10));
  ASSERT_NO_FATAL_FAILURE(ExpectRangesOverlap(12, 4, 12, 3));

  ASSERT_NO_FATAL_FAILURE(
      ExpectRangesOverlapOrTouch(kSparseHole, 2, kSparseHole, 3));
  ASSERT_NO_FATAL_FAILURE(ExpectRangesOverlap(kSparseHole, 2, kSparseHole, 3));
  ASSERT_NO_FATAL_FAILURE(
      ExpectFalseRangesOverlapOrTouch(kSparseHole, 2, 10, 3));
  ASSERT_NO_FATAL_FAILURE(
      ExpectFalseRangesOverlapOrTouch(10, 2, kSparseHole, 3));
  ASSERT_NO_FATAL_FAILURE(ExpectFalseRangesOverlap(kSparseHole, 2, 10, 3));
  ASSERT_NO_FATAL_FAILURE(ExpectFalseRangesOverlap(10, 2, kSparseHole, 3));
}

TEST(ExtentRangesTest, SimpleTest) {
  ExtentRanges ranges;
  {
    static constexpr uint64_t expected[] = {};
    // Can't use arraysize() on 0-length arrays:
    ASSERT_NO_FATAL_FAILURE(ExpectRangeEq(ranges, expected, 0, __LINE__));
  }
  ranges.SubtractBlock(2);
  {
    static constexpr uint64_t expected[] = {};
    // Can't use arraysize() on 0-length arrays:
    ASSERT_NO_FATAL_FAILURE(ExpectRangeEq(ranges, expected, 0, __LINE__));
  }

  ranges.AddBlock(0);
  ranges.Dump();
  ranges.AddBlock(1);
  ranges.AddBlock(3);

  {
    static constexpr uint64_t expected[] = {0, 2, 3, 1};
    ASSERT_RANGE_EQ(ranges, expected);
  }
  ranges.AddBlock(2);
  {
    static constexpr uint64_t expected[] = {0, 4};
    ASSERT_RANGE_EQ(ranges, expected);
    ranges.AddBlock(kSparseHole);
    ASSERT_RANGE_EQ(ranges, expected);
    ranges.SubtractBlock(kSparseHole);
    ASSERT_RANGE_EQ(ranges, expected);
  }
  ranges.SubtractBlock(2);
  {
    static constexpr uint64_t expected[] = {0, 2, 3, 1};
    ASSERT_RANGE_EQ(ranges, expected);
  }

  for (uint64_t i = 100; i < 1000; i += 100) {
    ranges.AddExtent(ExtentForRange(i, 50));
  }
  {
    static constexpr uint64_t expected[] = {0,   2,  3,   1,  100, 50, 200, 50,
                                            300, 50, 400, 50, 500, 50, 600, 50,
                                            700, 50, 800, 50, 900, 50};
    ASSERT_RANGE_EQ(ranges, expected);
  }

  ranges.SubtractExtent(ExtentForRange(210, 410 - 210));
  {
    static constexpr uint64_t expected[] = {0,   2,   3,   1,   100, 50,  200,
                                            10,  410, 40,  500, 50,  600, 50,
                                            700, 50,  800, 50,  900, 50};
    ASSERT_RANGE_EQ(ranges, expected);
  }
  ranges.AddExtent(ExtentForRange(100000, 0));
  {
    static constexpr uint64_t expected[] = {0,   2,   3,   1,   100, 50,  200,
                                            10,  410, 40,  500, 50,  600, 50,
                                            700, 50,  800, 50,  900, 50};
    ASSERT_RANGE_EQ(ranges, expected);
  }
  ranges.SubtractExtent(ExtentForRange(3, 0));
  {
    static constexpr uint64_t expected[] = {0,   2,   3,   1,   100, 50,  200,
                                            10,  410, 40,  500, 50,  600, 50,
                                            700, 50,  800, 50,  900, 50};
    ASSERT_RANGE_EQ(ranges, expected);
  }
}

TEST(ExtentRangesTest, MultipleRanges) {
  ExtentRanges ranges_a, ranges_b;
  ranges_a.AddBlock(0);
  ranges_b.AddBlock(4);
  ranges_b.AddBlock(3);
  {
    constexpr uint64_t expected[] = {3, 2};
    ASSERT_RANGE_EQ(ranges_b, expected);
  }
  ranges_a.AddRanges(ranges_b);
  {
    constexpr uint64_t expected[] = {0, 1, 3, 2};
    ASSERT_RANGE_EQ(ranges_a, expected);
  }
  ranges_a.SubtractRanges(ranges_b);
  {
    constexpr uint64_t expected[] = {0, 1};
    ASSERT_RANGE_EQ(ranges_a, expected);
  }
  {
    constexpr uint64_t expected[] = {3, 2};
    ASSERT_RANGE_EQ(ranges_b, expected);
  }
}

TEST(ExtentRangesTest, GetExtentsForBlockCountTest) {
  ExtentRanges ranges;
  ranges.AddExtents(vector<Extent>(1, ExtentForRange(10, 30)));
  {
    vector<Extent> zero_extents = ranges.GetExtentsForBlockCount(0);
    ASSERT_TRUE(zero_extents.empty());
  }
  ::google::protobuf::RepeatedPtrField<Extent> rep_field;
  *rep_field.Add() = ExtentForRange(30, 40);
  ranges.AddRepeatedExtents(rep_field);
  ranges.SubtractExtents(vector<Extent>(1, ExtentForRange(20, 10)));
  *rep_field.Mutable(0) = ExtentForRange(50, 10);
  ranges.SubtractRepeatedExtents(rep_field);
  ASSERT_EQ(40U, ranges.blocks());

  for (int i = 0; i < 2; i++) {
    vector<Extent> expected(2);
    expected[0] = ExtentForRange(10, 10);
    expected[1] = ExtentForRange(30, i == 0 ? 10 : 20);
    vector<Extent> actual =
        ranges.GetExtentsForBlockCount(10 + expected[1].num_blocks());
    ASSERT_EQ(expected.size(), actual.size());
    for (vector<Extent>::size_type j = 0, e = expected.size(); j != e; ++j) {
      ASSERT_EQ(expected[j].start_block(), actual[j].start_block())
          << "j = " << j;
      ASSERT_EQ(expected[j].num_blocks(), actual[j].num_blocks())
          << "j = " << j;
    }
  }
}

TEST(ExtentRangesTest, ContainsBlockTest) {
  ExtentRanges ranges;
  ASSERT_FALSE(ranges.ContainsBlock(123));

  ranges.AddExtent(ExtentForRange(10, 10));
  ranges.AddExtent(ExtentForRange(100, 1));

  ASSERT_FALSE(ranges.ContainsBlock(9));
  ASSERT_TRUE(ranges.ContainsBlock(10));
  ASSERT_TRUE(ranges.ContainsBlock(15));
  ASSERT_TRUE(ranges.ContainsBlock(19));
  ASSERT_FALSE(ranges.ContainsBlock(20));

  // Test for an extent with just the block we are requesting.
  ASSERT_FALSE(ranges.ContainsBlock(99));
  ASSERT_TRUE(ranges.ContainsBlock(100));
  ASSERT_FALSE(ranges.ContainsBlock(101));
}

TEST(ExtentRangesTest, FilterExtentRangesEmptyRanges) {
  ExtentRanges ranges;
  ASSERT_EQ(vector<Extent>(), FilterExtentRanges(vector<Extent>(), ranges));
  ASSERT_EQ(vector<Extent>{ExtentForRange(50, 10)},
            FilterExtentRanges(vector<Extent>{ExtentForRange(50, 10)}, ranges));
  // Check that the empty Extents are ignored.
  ASSERT_EQ((vector<Extent>{ExtentForRange(10, 10), ExtentForRange(20, 10)}),
            FilterExtentRanges(vector<Extent>{ExtentForRange(10, 10),
                                              ExtentForRange(3, 0),
                                              ExtentForRange(20, 10)},
                               ranges));
}

TEST(ExtentRangesTest, FilterExtentRangesMultipleRanges) {
  // Two overlapping extents, with three ranges to remove.
  vector<Extent> extents{ExtentForRange(10, 100), ExtentForRange(30, 100)};
  ExtentRanges ranges;
  // This overlaps the beginning of the second extent.
  ranges.AddExtent(ExtentForRange(28, 3));
  ranges.AddExtent(ExtentForRange(50, 10));
  ranges.AddExtent(ExtentForRange(70, 10));
  // This overlaps the end of the second extent.
  ranges.AddExtent(ExtentForRange(108, 6));
  ASSERT_EQ((vector<Extent>{// For the first extent:
                            ExtentForRange(10, 18),
                            ExtentForRange(31, 19),
                            ExtentForRange(60, 10),
                            ExtentForRange(80, 28),
                            // For the second extent:
                            ExtentForRange(31, 19),
                            ExtentForRange(60, 10),
                            ExtentForRange(80, 28),
                            ExtentForRange(114, 16)}),
            FilterExtentRanges(extents, ranges));
}

TEST(ExtentRangesTest, FilterExtentRangesOvelapping) {
  ExtentRanges ranges;
  ranges.AddExtent(ExtentForRange(10, 3));
  ranges.AddExtent(ExtentForRange(20, 5));
  // Requested extent overlaps with one of the ranges.
  ASSERT_EQ(vector<Extent>(),
            FilterExtentRanges(
                vector<Extent>{ExtentForRange(10, 1), ExtentForRange(22, 1)},
                ranges));
}

TEST(ExtentRangesTest, GetOverlapExtent) {
  const auto ret1 =
      GetOverlapExtent(ExtentForRange(5, 5), ExtentForRange(10, 10));
  ASSERT_EQ(ret1.num_blocks(), 0UL) << ret1;
  const auto ret2 =
      GetOverlapExtent(ExtentForRange(5, 5), ExtentForRange(9, 10));
  ASSERT_EQ(ret2, ExtentForRange(9, 1));

  const auto ret3 =
      GetOverlapExtent(ExtentForRange(7, 5), ExtentForRange(3, 10));
  ASSERT_EQ(ret3, ExtentForRange(7, 5));
  const auto ret4 =
      GetOverlapExtent(ExtentForRange(7, 5), ExtentForRange(3, 3));
  ASSERT_EQ(ret4.num_blocks(), 0UL);
}

TEST(ExtentRangesTest, ContainsBlockSameStart) {
  ExtentRanges ranges{false};
  ranges.AddExtent(ExtentForRange(5, 4));
  ranges.AddExtent(ExtentForRange(10, 5));
  ranges.AddExtent(ExtentForRange(15, 5));
  ranges.AddExtent(ExtentForRange(20, 5));
  ranges.AddExtent(ExtentForRange(25, 5));

  ASSERT_TRUE(ranges.ContainsBlock(10));
  ASSERT_TRUE(ranges.ContainsBlock(15));
  ASSERT_TRUE(ranges.ContainsBlock(20));
  ASSERT_TRUE(ranges.ContainsBlock(25));
  ASSERT_TRUE(ranges.ContainsBlock(29));
  ASSERT_FALSE(ranges.ContainsBlock(30));
  ASSERT_FALSE(ranges.ContainsBlock(9));
}

TEST(ExtentRangesTest, OverlapsWithExtentSameStart) {
  ExtentRanges ranges{false};
  ranges.AddExtent(ExtentForRange(5, 4));
  ranges.AddExtent(ExtentForRange(10, 5));
  ranges.AddExtent(ExtentForRange(15, 5));
  ranges.AddExtent(ExtentForRange(20, 5));
  ranges.AddExtent(ExtentForRange(25, 5));

  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(9, 2)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(12, 5)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(14, 5)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(10, 9)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(11, 20)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(0, 5)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(30, 20)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(9, 1)));

  ranges.SubtractExtent(ExtentForRange(12, 5));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(12, 5)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(13, 3)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(15, 2)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(14, 5)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(17, 1)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(8, 5)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(8, 4)));
}

TEST(ExtentRangesTest, OverlapsWithExtent) {
  ExtentRanges ranges;
  ranges.AddExtent(ExtentForRange(5, 5));
  ranges.AddExtent(ExtentForRange(15, 5));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(3, 10)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(17, 10)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(0, 10)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(10, 5)));
  ASSERT_FALSE(ranges.OverlapsWithExtent(ExtentForRange(20, 5)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(7, 1)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(0, 100)));
  ASSERT_TRUE(ranges.OverlapsWithExtent(ExtentForRange(19, 1)));
}

TEST(ExtentRangesTest, AddExtentMergeStressTest) {
  ExtentRanges ranges(true);
  for (size_t i = 0; i < 1000000; i++) {
    ranges.AddExtent(ExtentForRange(i, 1));
  }
  ASSERT_EQ(ranges.extent_set().size(), 1UL) << ranges.extent_set();
}

TEST(ExtentRangesTest, AddExtentNoMergeStressTest) {
  ExtentRanges ranges(true);
  for (size_t i = 0; i < 200000; i++) {
    ranges.AddExtent(ExtentForRange(i * 2, 1));
  }
  ASSERT_EQ(ranges.extent_set().size(), 200000UL) << ranges.extent_set();
}

TEST(ExtentRangesTest, AddExtentTouching) {
  ExtentRanges ranges(true);
  ranges.AddExtent(ExtentForRange(5, 5));
  ranges.AddExtent(ExtentForRange(25, 7));
  ASSERT_EQ(ranges.extent_set().size(), 2UL) << ranges.extent_set();
  ranges.AddExtent(ExtentForRange(0, 5));
  ASSERT_EQ(ranges.extent_set().size(), 2UL) << ranges.extent_set();
  ranges.AddExtent(ExtentForRange(10, 15));
  ASSERT_EQ(ranges.extent_set().size(), 1UL) << ranges.extent_set();
  ranges.AddExtent(ExtentForRange(32, 8));
  ASSERT_EQ(ranges.extent_set().size(), 1UL) << ranges.extent_set();
  ranges.AddExtent(ExtentForRange(45, 5));
  ASSERT_EQ(ranges.extent_set().size(), 2UL) << ranges.extent_set();
}

}  // namespace chromeos_update_engine
