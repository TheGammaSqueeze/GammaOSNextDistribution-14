/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "utils/grammar/parsing/chart.h"

#include "annotator/types.h"
#include "utils/base/arena.h"
#include "utils/grammar/parsing/parse-tree.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::IsEmpty;

class ChartTest : public testing::Test {
 protected:
  explicit ChartTest() : arena_(/*block_size=*/16 << 10) {}
  UnsafeArena arena_;
};

TEST_F(ChartTest, IsEmptyByDefault) {
  Chart<> chart;

  EXPECT_THAT(chart.derivations(), IsEmpty());
  EXPECT_TRUE(chart.MatchesEndingAt(0).Done());
}

TEST_F(ChartTest, IteratesThroughCell) {
  Chart<> chart;
  ParseTree* m0 = arena_.AllocAndInit<ParseTree>(/*lhs=*/0, CodepointSpan{0, 1},
                                                 /*match_offset=*/0,
                                                 ParseTree::Type::kDefault);
  chart.Add(m0);
  ParseTree* m1 = arena_.AllocAndInit<ParseTree>(/*lhs=*/1, CodepointSpan{0, 2},
                                                 /*match_offset=*/0,
                                                 ParseTree::Type::kDefault);
  chart.Add(m1);
  ParseTree* m2 = arena_.AllocAndInit<ParseTree>(/*lhs=*/2, CodepointSpan{0, 2},
                                                 /*match_offset=*/0,
                                                 ParseTree::Type::kDefault);
  chart.Add(m2);

  // Position 0 should be empty.
  EXPECT_TRUE(chart.MatchesEndingAt(0).Done());

  // Position 1 should contain m0.
  {
    Chart<>::Iterator it = chart.MatchesEndingAt(1);
    ASSERT_FALSE(it.Done());
    EXPECT_EQ(it.Item(), m0);
    it.Next();
    EXPECT_TRUE(it.Done());
  }

  // Position 2 should contain m1 and m2.
  {
    Chart<>::Iterator it = chart.MatchesEndingAt(2);
    ASSERT_FALSE(it.Done());
    EXPECT_EQ(it.Item(), m2);
    it.Next();
    ASSERT_FALSE(it.Done());
    EXPECT_EQ(it.Item(), m1);
    it.Next();
    EXPECT_TRUE(it.Done());
  }
}

TEST_F(ChartTest, ChecksExistingMatches) {
  Chart<> chart;
  ParseTree* m0 = arena_.AllocAndInit<ParseTree>(/*lhs=*/0, CodepointSpan{0, 1},
                                                 /*match_offset=*/0,
                                                 ParseTree::Type::kDefault);
  chart.Add(m0);
  ParseTree* m1 = arena_.AllocAndInit<ParseTree>(/*lhs=*/1, CodepointSpan{0, 2},
                                                 /*match_offset=*/0,
                                                 ParseTree::Type::kDefault);
  chart.Add(m1);
  ParseTree* m2 = arena_.AllocAndInit<ParseTree>(/*lhs=*/2, CodepointSpan{0, 2},
                                                 /*match_offset=*/0,
                                                 ParseTree::Type::kDefault);
  chart.Add(m2);

  EXPECT_TRUE(chart.HasMatch(0, CodepointSpan{0, 1}));
  EXPECT_FALSE(chart.HasMatch(0, CodepointSpan{0, 2}));
  EXPECT_TRUE(chart.HasMatch(1, CodepointSpan{0, 2}));
  EXPECT_TRUE(chart.HasMatch(2, CodepointSpan{0, 2}));
  EXPECT_FALSE(chart.HasMatch(0, CodepointSpan{0, 2}));
}

}  // namespace
}  // namespace libtextclassifier3::grammar
