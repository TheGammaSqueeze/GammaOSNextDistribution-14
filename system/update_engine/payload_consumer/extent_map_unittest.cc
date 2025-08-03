//
// Copyright (C) 2021 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <optional>
#include <vector>

#include "update_engine/payload_consumer/extent_map.h"
#include "update_engine/payload_generator/extent_ranges.h"

namespace chromeos_update_engine {

class ExtentMapTest : public ::testing::Test {
 public:
  ExtentMap<int> map_;
};

TEST_F(ExtentMapTest, QueryExactExtent) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 1));
  auto ret = map_.Get(ExtentForRange(0, 5));
  ASSERT_NE(ret, std::nullopt);
  ASSERT_EQ(*ret, 7);
}

TEST_F(ExtentMapTest, QuerySubset) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 1));
  auto ret = map_.Get(ExtentForRange(1, 2));
  ASSERT_EQ(ret, 7);
}

TEST_F(ExtentMapTest, QueryNoMerge) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(5, 5), 1));
  auto ret = map_.Get(ExtentForRange(1, 2));
  ASSERT_EQ(ret, 7);
  ret = map_.Get(ExtentForRange(0, 10));
  ASSERT_EQ(ret, std::nullopt);
  ret = map_.Get(ExtentForRange(4, 3));
  ASSERT_EQ(ret, std::nullopt);
}

TEST_F(ExtentMapTest, QueryTouching) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 1));
  auto ret = map_.Get(ExtentForRange(3, 2));
  ASSERT_EQ(ret, 7);
  ret = map_.Get(ExtentForRange(4, 1));
  ASSERT_EQ(ret, 7);
  ret = map_.Get(ExtentForRange(5, 5));
  ASSERT_EQ(ret, std::nullopt);
  ret = map_.Get(ExtentForRange(5, 6));
  ASSERT_EQ(ret, std::nullopt);
}

TEST_F(ExtentMapTest, GetIntersectingExtents) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 7));
  auto ret = std::vector<Extent>{};
  ret = map_.GetIntersectingExtents(ExtentForRange(2, 10));
  ASSERT_EQ(ret.size(), 2U);
  ASSERT_EQ(ret[0].start_block(), 2U);
  ASSERT_EQ(ret[0].num_blocks(), 3U);

  ASSERT_EQ(ret[1].start_block(), 10U);
  ASSERT_EQ(ret[1].num_blocks(), 2U);

  ret = map_.GetIntersectingExtents(ExtentForRange(2, 17));
  ASSERT_EQ(ret.size(), 2U);
  ASSERT_EQ(ret[0].start_block(), 2U);
  ASSERT_EQ(ret[0].num_blocks(), 3U);

  ASSERT_EQ(ret[1].start_block(), 10U);
  ASSERT_EQ(ret[1].num_blocks(), 5U);

  ret = map_.GetIntersectingExtents(ExtentForRange(2, 2));
  ASSERT_EQ(ret, std::vector<Extent>{ExtentForRange(2, 2)});

  ret = map_.GetIntersectingExtents(ExtentForRange(10, 5));
  ASSERT_EQ(ret, std::vector<Extent>{ExtentForRange(10, 5)});

  ASSERT_TRUE(map_.AddExtent(ExtentForRange(20, 5), 7));
  ret = map_.GetIntersectingExtents(ExtentForRange(0, 30));
  ASSERT_EQ(ret.size(), 3U);
  ASSERT_EQ(ret[0].start_block(), 0U);
  ASSERT_EQ(ret[0].num_blocks(), 5U);

  ASSERT_EQ(ret[1].start_block(), 10U);
  ASSERT_EQ(ret[1].num_blocks(), 5U);

  ASSERT_EQ(ret[2].start_block(), 20U);
  ASSERT_EQ(ret[2].num_blocks(), 5U);
}

TEST_F(ExtentMapTest, GetNonIntersectingExtents) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(20, 5), 7));

  auto ret = std::vector<Extent>{};
  ret = map_.GetNonIntersectingExtents(ExtentForRange(2, 13));

  ASSERT_EQ(ret.size(), 1U);
  ASSERT_EQ(ret[0].start_block(), 5U);
  ASSERT_EQ(ret[0].num_blocks(), 5U);

  ret = map_.GetNonIntersectingExtents(ExtentForRange(7, 20));
  ASSERT_EQ(ret.size(), 3U);
  ASSERT_EQ(ret[0].start_block(), 7U);
  ASSERT_EQ(ret[0].num_blocks(), 3U);

  ASSERT_EQ(ret[1].start_block(), 15U);
  ASSERT_EQ(ret[1].num_blocks(), 5U);

  ASSERT_EQ(ret[2].start_block(), 25U);
  ASSERT_EQ(ret[2].num_blocks(), 2U);
}

TEST_F(ExtentMapTest, GetSameStartBlock) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(0, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 12));

  const auto ret = map_.Get(ExtentForRange(0, 10));
  // ASSERT_FALSE(ret.has_value()) << ret.value() won't work, because when |ret|
  // doesn't have value, the part after '<<' after still evaluated, resulting in
  // undefined behavior.
  if (ret.has_value()) {
    FAIL() << ret.value();
  }
}

TEST_F(ExtentMapTest, GetTouchingExtents) {
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(5, 5), 7));
  ASSERT_TRUE(map_.AddExtent(ExtentForRange(10, 5), 12));
  const auto ret = map_.Get(ExtentForRange(5, 10));
  if (ret.has_value()) {
    ASSERT_FALSE(ret.has_value()) << ret.value();
  }
  const auto extents = map_.GetIntersectingExtents(ExtentForRange(0, 20));
  ASSERT_GT(extents.size(), 0UL);
  ASSERT_EQ(extents.size(), 2UL)
      << "Expecting unmerged extents [5-9] and [10-14], actual: " << extents;
  ASSERT_EQ(extents[0], ExtentForRange(5, 5));
  ASSERT_EQ(extents[1], ExtentForRange(10, 5));
}

}  // namespace chromeos_update_engine
