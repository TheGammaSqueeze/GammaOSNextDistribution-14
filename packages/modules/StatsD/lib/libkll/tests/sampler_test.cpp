/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "sampler.h"

#include <cmath>
#include <cstdint>
#include <type_traits>
#include <vector>

#include "compactor_stack.h"
#include "gmock/gmock.h"

namespace dist_proc {
namespace aggregation {
namespace internal {

namespace {

using ::testing::_;
using ::testing::AnyOf;
using ::testing::Contains;
using ::testing::Eq;
using ::testing::Optional;
using ::testing::Pair;

class KllQuantileSamplerTest : public ::testing::Test {
protected:
    KllQuantileSamplerTest() : random_() {
    }
    MTRandomGenerator random_;
};

TEST_F(KllQuantileSamplerTest, Add100Items) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));

    sampler.Add(4);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(4), Eq(1))));
    sampler.Add(10);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));

    for (int i = 0; i < 100; i++) {
        sampler.Reset();
        sampler.DoubleCapacity();
        sampler.Add(4);
        EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(4), Eq(1))));
        sampler.Add(10);
        EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(AnyOf(Eq(4), Eq(10)), Eq(2))));
        sampler.Add(14);
        EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(_, Eq(3))));
        sampler.Add(24);
        EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
    }
}

TEST_F(KllQuantileSamplerTest, ZeroItems) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
}

TEST_F(KllQuantileSamplerTest, OneItem) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.Add(4);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(4), Eq(1))));
}

TEST_F(KllQuantileSamplerTest, TwoInts) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    EXPECT_EQ(sampler.capacity(), 2);
    sampler.Add(1);
    sampler.Add(2);

    const std::vector<int64_t>& compactor =
            compactor_stack.compactors()[sampler.num_replaced_levels()];

    EXPECT_THAT(compactor, AnyOf(Contains(Eq(1)), Contains(Eq(2))));
    EXPECT_EQ(compactor_stack.num_stored_items(), 1);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
}

TEST_F(KllQuantileSamplerTest, TwoIntsCapacityFour) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.DoubleCapacity();
    EXPECT_EQ(sampler.capacity(), 4);
    EXPECT_EQ(sampler.num_replaced_levels(), 2);

    sampler.Add(1);
    sampler.Add(2);

    EXPECT_EQ(compactor_stack.num_stored_items(), 0);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(AnyOf(Eq(1), Eq(2)), Eq(2))));
}

TEST_F(KllQuantileSamplerTest, FourInts) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    EXPECT_EQ(sampler.capacity(), 2);
    sampler.Add(1);
    sampler.Add(2);
    sampler.Add(3);
    sampler.Add(4);

    const std::vector<int64_t>& compactor =
            compactor_stack.compactors()[sampler.num_replaced_levels()];
    EXPECT_THAT(compactor, AnyOf(Contains(Eq(1)), Contains(Eq(2))));
    EXPECT_THAT(compactor, AnyOf(Contains(Eq(3)), Contains(Eq(4))));

    EXPECT_EQ(compactor_stack.num_stored_items(), 2);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
}

TEST_F(KllQuantileSamplerTest, ThreeInts) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    EXPECT_EQ(sampler.capacity(), 2);

    sampler.Add(1);
    sampler.Add(2);
    sampler.Add(3);

    const std::vector<int64_t>& compactor =
            compactor_stack.compactors()[sampler.num_replaced_levels()];
    EXPECT_THAT(compactor, AnyOf(Contains(Eq(1)), Contains(Eq(2))));
    EXPECT_EQ(compactor_stack.num_stored_items(), 1);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(3), Eq(1))));
}

TEST_F(KllQuantileSamplerTest, AddWithWeightZero) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.AddWithWeight(5, 0);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
}

TEST_F(KllQuantileSamplerTest, AddWithWeightOne) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.AddWithWeight(5, 1);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(5), Eq(1))));
}

TEST_F(KllQuantileSamplerTest, AddWithWeightTwo) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.AddWithWeight(1, 2);

    const std::vector<int64_t>& compactor =
            compactor_stack.compactors()[sampler.num_replaced_levels()];
    EXPECT_THAT(compactor, Contains(Eq(1)));
    EXPECT_EQ(compactor_stack.num_stored_items(), 1);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
}

TEST_F(KllQuantileSamplerTest, AddWithWeightThree) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.AddWithWeight(3, 3);

    const std::vector<int64_t>& compactor =
            compactor_stack.compactors()[sampler.num_replaced_levels()];
    EXPECT_THAT(compactor, Contains(Eq(3)));
    EXPECT_EQ(compactor_stack.num_stored_items(), 1);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(3), Eq(1))));
}

TEST_F(KllQuantileSamplerTest, WeightThreeNonEmptySampler) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.Add(1);
    sampler.DoubleCapacity();
    sampler.DoubleCapacity();
    sampler.AddWithWeight(2, 3);

    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(AnyOf(Eq(1), Eq(2)), Eq(4))));
}

TEST_F(KllQuantileSamplerTest, WeightFiveNonEmptySampler) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.Add(1);
    sampler.DoubleCapacity();
    sampler.Add(2);
    sampler.AddWithWeight(3, 5);

    const std::vector<int64_t>& compactor =
            compactor_stack.compactors()[sampler.num_replaced_levels()];
    EXPECT_THAT(compactor, AnyOf(Contains(Eq(1)), Contains(Eq(2)), Contains(Eq(3))));
    EXPECT_EQ(compactor_stack.num_stored_items(), 1);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(3), Eq(3))));
}

TEST_F(KllQuantileSamplerTest, DoubleCapacityBetweenAdds) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.Add(1);
    sampler.DoubleCapacity();
    EXPECT_EQ(sampler.capacity(), 4);
    EXPECT_EQ(sampler.num_replaced_levels(), 2);
    sampler.Add(2);
    sampler.Add(3);
    EXPECT_THAT(sampler.sampled_item_and_weight(),
                Optional(Pair(AnyOf(Eq((1)), Eq((2)), Eq((3))), Eq(3))));
    sampler.DoubleCapacity();
    EXPECT_EQ(sampler.capacity(), 8);
    EXPECT_EQ(sampler.num_replaced_levels(), 3);
    sampler.Add(4);
    sampler.Add(5);
    EXPECT_THAT(sampler.sampled_item_and_weight(),
                Optional(Pair(AnyOf(Eq((1)), Eq((2)), Eq((3)), Eq((4)), Eq((5))), Eq(5))));
}

TEST_F(KllQuantileSamplerTest, ResetZeroItems) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.Reset();
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
}

TEST_F(KllQuantileSamplerTest, ResetBetweenAddingOneItem) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.Add(1);
    sampler.Reset();
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
    sampler.Add(2);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(Eq(2), Eq(1))));
}

TEST_F(KllQuantileSamplerTest, ResetBetweenAddingTenItems) {
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    sampler.DoubleCapacity();
    EXPECT_EQ(sampler.capacity(), 4);
    EXPECT_EQ(sampler.num_replaced_levels(), 2);

    for (int i = 0; i < 10; i++) {
        sampler.Add(i);
    }
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(_, Eq(2))));

    sampler.Reset();
    EXPECT_THAT(sampler.sampled_item_and_weight(), Eq(std::nullopt));
    EXPECT_EQ(sampler.capacity(), 2);
    EXPECT_EQ(sampler.num_replaced_levels(), 1);
    sampler.DoubleCapacity();
    EXPECT_EQ(sampler.capacity(), 4);
    EXPECT_EQ(sampler.num_replaced_levels(), 2);
    for (int i = 0; i < 10; i++) {
        sampler.Add(i);
    }
    EXPECT_EQ(compactor_stack.num_stored_items(), 4);
    EXPECT_THAT(sampler.sampled_item_and_weight(), Optional(Pair(AnyOf(Eq(8), Eq(9)), Eq(2))));
}

class SamplerItemSeenTest : public ::testing::TestWithParam<int> {};

// Whenever making a larger change to the sampler remove the fixed seed for
// this test. You should see around 2% failure rate (among all num_item sizes)
// in this test when running it with a sufficiently large --num_test_runs.
//
// The test checks whether we generate all items from a set of [0, n)
// items in 2 * n * log(n) runs. The expected value for number of repetitions
// required is n * H_n (see coupon collector problem
// https://en.wikipedia.org/wiki/Coupon_collector%27s_problem).
//
// For a given n the probability that we see all items is:
// Stirling_2nd(runs, n) * n! / n^runs (n^runs is the total number
// of possible outcomes, Stirling 2nd is the number of possibilities to
// partition run elements into n non-empty buckets regardless of order and n!
// is needed to introduce the ordering).)
TEST_P(SamplerItemSeenTest, AllItemsSeen) {
    const int num_items = GetParam();
    auto random = MTRandomGenerator(10);
    bool seen_items[1000] = {};
    int num_repetitions = 2 * num_items * std::ceil(std::log(1 + num_items));
    for (int i = 0; i < num_repetitions; i++) {
        CompactorStack compactor_stack(1000, 100000, &random);
        KllSampler sampler(&compactor_stack);
        while (sampler.capacity() <= num_items) {
            sampler.DoubleCapacity();
        }
        for (int j = 0; j < num_items; j++) {
            sampler.Add(j);
        }
        seen_items[sampler.sampled_item_and_weight().value().first] = true;
        EXPECT_EQ(sampler.sampled_item_and_weight().value().second, num_items);
    }

    for (int i = 0; i < num_items; i++) {
        EXPECT_TRUE(seen_items[i]);
    }
}

INSTANTIATE_TEST_SUITE_P(SamplerEveryItemSeenTestCases, SamplerItemSeenTest,
                         testing::Range(1, 1000, 10));
}  // namespace

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
