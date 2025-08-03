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
#include "compactor_stack.h"

#include <cmath>
#include <cstdint>
#include <type_traits>
#include <vector>

#include "gmock/gmock.h"
#include "random_generator.h"

namespace dist_proc {
namespace aggregation {
namespace internal {

namespace {

using ::testing::AnyOf;
using ::testing::Contains;

struct CompactorsTestParams {
    int num_items;
    int capacity;
};

std::vector<CompactorsTestParams> GenCompactorsTestParams() {
    std::vector<CompactorsTestParams> testcases;
    for (int num_items = 1; num_items < 1000; num_items += std::ceil(std::log(1 + num_items))) {
        for (int capacity = 2; capacity < 1030; capacity *= 2) {
            testcases.push_back({num_items, capacity});
        }
    }
    return testcases;
}

class AddsToCompactorsTest : public ::testing::TestWithParam<CompactorsTestParams> {
protected:
    AddsToCompactorsTest() : random_() {
    }
    MTRandomGenerator random_;
};

TEST_P(AddsToCompactorsTest, AddsToCompactorsTestWeightOne) {
    // This test should not depend on the seed, since we only test
    // num_stored_items, capacity and sampler weight.
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<uint64_t> dis(0, std::numeric_limits<uint64_t>::max());
    const CompactorsTestParams params = GetParam();
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    while (sampler.capacity() < params.capacity) {
        sampler.DoubleCapacity();
    }
    for (int i = 0; i < params.num_items; i++) {
        sampler.Add(dis(gen));
    }
    EXPECT_EQ(compactor_stack.num_stored_items(), params.num_items / sampler.capacity());
    if (sampler.sampled_item_and_weight().has_value()) {
        EXPECT_EQ(sampler.sampled_item_and_weight().value().second,
                  params.num_items % sampler.capacity());
    } else {
        EXPECT_EQ(0, params.num_items % sampler.capacity());
    }
}

TEST_P(AddsToCompactorsTest, AddWithWeightToCompactorsTest) {
    // This test should not depend on the seed, since we only test
    // num_stored_items, capacity and sampler weight.
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<uint64_t> dis(0, std::numeric_limits<uint64_t>::max());
    const CompactorsTestParams params = GetParam();
    CompactorStack compactor_stack(1000, 100000, &random_);
    KllSampler sampler(&compactor_stack);
    while (sampler.capacity() < params.capacity) {
        sampler.DoubleCapacity();
    }
    int remaining_num_items = params.num_items;
    while (remaining_num_items > 0) {
        // Add +1 to weight to avoid adding values with 0 weight.
        int weight = random_.UnbiasedUniform(remaining_num_items) + 1;
        sampler.AddWithWeight(dis(gen), weight);
        remaining_num_items -= weight;
    }
    EXPECT_EQ(compactor_stack.num_stored_items(), params.num_items / sampler.capacity());
    if (sampler.sampled_item_and_weight().has_value()) {
        EXPECT_EQ(sampler.sampled_item_and_weight().value().second,
                  params.num_items % sampler.capacity());
    } else {
        EXPECT_EQ(0, params.num_items % sampler.capacity());
    }
}

INSTANTIATE_TEST_SUITE_P(AddsToCompactorsTestCases, AddsToCompactorsTest,
                         ::testing::ValuesIn(GenCompactorsTestParams()));
class KllQuantileUseSamplerTest : public ::testing::Test {
protected:
    KllQuantileUseSamplerTest() {
    }
    ~KllQuantileUseSamplerTest() override {
    }
    MTRandomGenerator random_;
};

TEST_F(KllQuantileUseSamplerTest, ZeroCapacityAfterReplacedWithSampler) {
    CompactorStack compactor_stack(10, 10, &random_);
    for (int i = 0; i < 200000; i++) {
        compactor_stack.Add(random_.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    EXPECT_TRUE(compactor_stack.IsSamplerOn());
    const auto& compactors = compactor_stack.compactors();
    for (int i = 0; i < compactor_stack.lowest_active_level(); i++) {
        EXPECT_EQ(compactors[i].capacity(), 0u);
    }
}

TEST_F(KllQuantileUseSamplerTest, NumStoredItemsWithSampledItem) {
    CompactorStack compactor_stack(10, 10, &random_);
    for (int i = 0; i < 2000; i++) {
        compactor_stack.Add(random_.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    EXPECT_TRUE(compactor_stack.IsSamplerOn());

    int num_items_in_compactors = 0;
    for (const auto& compactor : compactor_stack.compactors()) {
        if (compactor.capacity() > 0) {
            num_items_in_compactors += compactor.size();
        }
    }
    if (compactor_stack.sampled_item_and_weight().has_value()) {
        EXPECT_EQ(compactor_stack.num_stored_items(), num_items_in_compactors + 1);
    } else {
        EXPECT_EQ(compactor_stack.num_stored_items(), num_items_in_compactors);
        compactor_stack.Add(random_.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
        EXPECT_EQ(compactor_stack.num_stored_items(), num_items_in_compactors + 1);
    }
}

TEST_F(KllQuantileUseSamplerTest, ResetWithSampler) {
    // Set a fixed seed for this test, as it is not given that there are 40 items
    // in the compactor stack after 2000 insertions.
    MTRandomGenerator random(10);
    CompactorStack compactor_stack(10, 10, &random);
    for (int i = 0; i < 2000; i++) {
        compactor_stack.Add(random_.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    EXPECT_TRUE(compactor_stack.IsSamplerOn());
    EXPECT_GE(compactor_stack.num_stored_items(), 40);
    EXPECT_LE(compactor_stack.num_stored_items(), 2000);
    EXPECT_GE(compactor_stack.compactors().size(), 2u);

    compactor_stack.Reset();
    EXPECT_FALSE(compactor_stack.IsSamplerOn());
    EXPECT_FALSE(compactor_stack.sampled_item_and_weight().has_value());
    EXPECT_EQ(compactor_stack.num_stored_items(), 0);
    EXPECT_EQ(compactor_stack.compactors().size(), 1u);

    for (int i = 0; i < 2000; i++) {
        compactor_stack.Add(random_.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    EXPECT_TRUE(compactor_stack.IsSamplerOn());
    EXPECT_GE(compactor_stack.num_stored_items(), 40);
    EXPECT_LE(compactor_stack.num_stored_items(), 2000);
    EXPECT_GE(compactor_stack.compactors().size(), 2u);
}

struct AddWithSamplerParam {
    int64_t inv_eps;
    int64_t inv_delta;
    int64_t num_items;
};

class AddWithSamplerTest : public ::testing::TestWithParam<AddWithSamplerParam> {};

TEST_P(AddWithSamplerTest, AddWithSampler) {
    const AddWithSamplerParam params = GetParam();
    std::random_device rd;
    MTRandomGenerator random(rd());
    CompactorStack compactor_stack(params.inv_eps, params.inv_delta, &random);

    int upbound = (random.UnbiasedUniform(3) + 1) * params.num_items + 1;
    for (int i = 0; i < upbound; i++) {
        compactor_stack.Add(random.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    EXPECT_TRUE(compactor_stack.IsSamplerOn());

    int lowest_active_level = compactor_stack.lowest_active_level();
    int size_before_add = compactor_stack.compactors()[lowest_active_level].size();
    uint64_t previous_sampled_item = -1;
    if (compactor_stack.sampled_item_and_weight().has_value()) {
        previous_sampled_item = compactor_stack.sampled_item_and_weight()->first;
    }
    // Add additional value sufficiently many times to complete full sampler
    // cycle.
    for (int i = 0; i < compactor_stack.sampler_capacity(); i++) {
        compactor_stack.Add(10);
    }

    int size_after_add = compactor_stack.compactors()[lowest_active_level].size();
    if (size_after_add > 0) {
        EXPECT_EQ(size_after_add, size_before_add + 1);
        EXPECT_THAT(compactor_stack.compactors()[lowest_active_level],
                    AnyOf(Contains(10), Contains(previous_sampled_item)));
    }
}

TEST_P(AddWithSamplerTest, AddWithWeightWithSampler) {
    // Set a fixed seed, since the tests depends on which level is propagated
    // in the compactor stack.
    uint64_t seed = 3;
    const AddWithSamplerParam params = GetParam();
    MTRandomGenerator random = MTRandomGenerator(seed);
    CompactorStack compactor_stack(params.inv_eps, params.inv_delta, &random);

    for (int i = 0; i < params.num_items; i++) {
        compactor_stack.Add(random.UnbiasedUniform(std::numeric_limits<uint32_t>::max()));
    }
    ASSERT_TRUE(compactor_stack.IsSamplerOn());
    if (!compactor_stack.sampled_item_and_weight().has_value()) {
        compactor_stack.Add(random.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    int lowest_active_level = compactor_stack.lowest_active_level();
    int size_before_add_lowest_level = compactor_stack.compactors()[lowest_active_level].size();
    int size_before_add_level_plus2 = compactor_stack.compactors()[lowest_active_level + 2].size();
    int size_before_add_level_plus3 = compactor_stack.compactors()[lowest_active_level + 3].size();
    uint64_t previous_sampled_item = -1;
    if (compactor_stack.sampled_item_and_weight().has_value()) {
        previous_sampled_item = compactor_stack.sampled_item_and_weight()->first;
    }

    // Expected additions: one item from the sampler to the lowest active level;
    // one item to the lowest active level + 2.
    int weight = 5 * (1 << lowest_active_level) - 1;
    compactor_stack.AddWithWeight(10.0, weight);

    int size_after_add_lowest_level = compactor_stack.compactors()[lowest_active_level].size();
    // > 0 to make sure we are not checking compact case
    if (size_after_add_lowest_level > 0) {
        EXPECT_EQ(size_after_add_lowest_level, size_before_add_lowest_level + 1);
        EXPECT_THAT(compactor_stack.compactors()[lowest_active_level],
                    AnyOf(Contains(10.0), Contains(previous_sampled_item)));
    }

    // Level+2 is expected to change.
    // Exact behavior also subject to seed - a lower level might be compacted
    // into this one, or this one might be compacted up.
    // Test only checks the uncompacted case. (Different from Java version)
    int size_after_add_level_plus2 = compactor_stack.compactors()[lowest_active_level + 2].size();
    if (size_after_add_level_plus2 >
        // make sure we are not checking comact case
        size_before_add_level_plus2) {
        EXPECT_EQ(size_after_add_level_plus2, size_before_add_level_plus2 + 1);
        EXPECT_THAT(compactor_stack.compactors()[lowest_active_level + 2], Contains(10.0));
        int size_after_add_level_plus3 =
                compactor_stack.compactors()[lowest_active_level + 3].size();
        EXPECT_EQ(size_after_add_level_plus3, size_before_add_level_plus3);
    }
}

TEST_P(AddWithSamplerTest, CompactorsStillBigXorSamplerIsOn) {
    std::random_device rd;
    uint64_t seed = rd();
    const AddWithSamplerParam params = GetParam();
    MTRandomGenerator random = MTRandomGenerator(seed);
    CompactorStack compactor_stack(params.inv_eps, params.inv_delta, &random);
    for (int i = 0; i < params.num_items; i++) {
        compactor_stack.Add(random.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
        int lowest_compactor_capacity = compactor_stack.TargetCapacityAtLevel(0);
        EXPECT_TRUE((lowest_compactor_capacity > 2) ^ (compactor_stack.IsSamplerOn()));
    }
}

TEST_P(AddWithSamplerTest, SampledItemIsPresent) {
    std::random_device rd;
    uint64_t seed = rd();
    const AddWithSamplerParam params = GetParam();
    MTRandomGenerator random = MTRandomGenerator(seed);
    CompactorStack compactor_stack(params.inv_eps, params.inv_delta, &random);

    for (int i = 0; i < params.num_items; i++) {
        compactor_stack.Add(random.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    }
    int sampled_item_weight = (compactor_stack.sampled_item_and_weight().has_value())
                                      ? compactor_stack.sampled_item_and_weight().value().second
                                      : 0;
    compactor_stack.Add(random.UnbiasedUniform(std::numeric_limits<uint64_t>::max()));
    int sampled_item_weight_after_add =
            (compactor_stack.sampled_item_and_weight().has_value())
                    ? compactor_stack.sampled_item_and_weight().value().second
                    : 0;
    sampled_item_weight = std::max(sampled_item_weight, sampled_item_weight_after_add);
    EXPECT_GT(sampled_item_weight, 0);
    EXPECT_LE(sampled_item_weight, (1 << compactor_stack.lowest_active_level()));
}

INSTANTIATE_TEST_SUITE_P(AddWithSamplerTestCases, AddWithSamplerTest,
                         ::testing::ValuesIn(std::vector<AddWithSamplerParam>{
                                 {10, 10, 2400},
                                 {10, 100, 4600},
                                 {10, 1000, 5000},
                                 {50, 10000, 5000000},
                                 {100, 100, 1250000},
                                 {100, 1000, 2000000}}));

}  // namespace

}  // namespace internal
}  // namespace aggregation
}  // namespace dist_proc
