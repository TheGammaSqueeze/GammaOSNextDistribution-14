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
#include "kll.h"

#include <gtest/gtest.h>

#include "kll-quantiles.pb.h"

namespace dist_proc {
namespace aggregation {

namespace {

using zetasketch::android::AggregatorStateProto;
using zetasketch::android::kll_quantiles_state;
using zetasketch::android::KllQuantilesStateProto;

////////////////////////////////////////////////////////////////////////////////
// --------------------- Tests for SerializeToProto ------------------------- //

TEST(KllQuantileSerializationTest, AggregatorProtoFieldsAreCorrectlyPopulated) {
    std::unique_ptr<KllQuantile> aggregator = KllQuantile::Create();
    for (int i = 0; i < 50; i++) {
        aggregator->Add(i);
    }
    EXPECT_EQ(aggregator->num_values(), 50);

    AggregatorStateProto aggregator_state = aggregator->SerializeToProto();
    ASSERT_TRUE(aggregator_state.has_num_values());
    EXPECT_EQ(aggregator_state.num_values(), 50);
    EXPECT_EQ(aggregator_state.type(), zetasketch::android::KLL_QUANTILES);
    EXPECT_EQ(aggregator_state.type(), 113);
    EXPECT_EQ(aggregator_state.value_type(), zetasketch::android::DefaultOpsType::INT64);
    EXPECT_EQ(aggregator_state.value_type(), 4);
}

TEST(KllQuantileSerializationTest, QuantilesProtoFieldsAreCorrectlyPopulated) {
    std::unique_ptr<KllQuantile> aggregator = KllQuantile::Create();
    for (int i = 1; i <= 10; i++) {
        aggregator->Add(i);
    }
    EXPECT_EQ(aggregator->num_values(), 10);

    AggregatorStateProto aggregator_state = aggregator->SerializeToProto();
    EXPECT_EQ(aggregator_state.num_values(), 10);

    EXPECT_EQ(aggregator_state.type(), zetasketch::android::KLL_QUANTILES);
    ASSERT_TRUE(aggregator_state.HasExtension(kll_quantiles_state));
    const KllQuantilesStateProto& quantiles_state =
            aggregator_state.GetExtension(kll_quantiles_state);

    ASSERT_TRUE(quantiles_state.has_k());
    EXPECT_GE(quantiles_state.k(), 300);
    EXPECT_LT(quantiles_state.k(), 5000);

    ASSERT_TRUE(quantiles_state.has_inv_eps());
    EXPECT_EQ(quantiles_state.inv_eps(), 1000);

    // Min, max
    ASSERT_TRUE(quantiles_state.has_min());
    EXPECT_EQ(quantiles_state.min(), "\x1");
    ASSERT_TRUE(quantiles_state.has_max());
    EXPECT_EQ(quantiles_state.max(), "\xA");

    // Compactors
    EXPECT_EQ(quantiles_state.compactors_size(), 1);
    const KllQuantilesStateProto::Compactor& compactor = quantiles_state.compactors(0);
    ASSERT_TRUE(compactor.has_packed_values());
    EXPECT_EQ(compactor.packed_values(), "\x1\x2\x3\x4\x5\x6\x7\x8\x9\xA");

    ASSERT_FALSE(quantiles_state.has_sampler());
}

TEST(KllQuantileSerializationTest, QuantilesProtoFieldsAreCorrectlyPopulatedNonDefaultInvDeltaEps) {
    KllQuantileOptions options;
    options.set_inv_delta(1000);
    options.set_inv_eps(40);
    std::unique_ptr<KllQuantile> aggregator = KllQuantile::Create(options);
    for (int i = 5; i < 200; i++) {
        aggregator->Add(i);
    }
    EXPECT_EQ(aggregator->num_values(), 195);

    AggregatorStateProto aggregator_state = aggregator->SerializeToProto();
    EXPECT_EQ(aggregator_state.num_values(), 195);

    ASSERT_TRUE(aggregator_state.HasExtension(kll_quantiles_state));
    const KllQuantilesStateProto& quantiles_state =
            aggregator_state.GetExtension(kll_quantiles_state);

    ASSERT_TRUE(quantiles_state.has_k());
    EXPECT_GE(quantiles_state.k(), 15);
    EXPECT_LT(quantiles_state.k(), 200);

    ASSERT_TRUE(quantiles_state.has_inv_eps());
    EXPECT_EQ(quantiles_state.inv_eps(), 40);

    // Min, max
    ASSERT_TRUE(quantiles_state.has_min());
    EXPECT_EQ(quantiles_state.min(), "\x5");
    ASSERT_TRUE(quantiles_state.has_max());
    EXPECT_EQ(quantiles_state.max(), "\xC7\x1");

    EXPECT_GT(quantiles_state.compactors_size(), 1);
}

TEST(KllQuantileSerializationTest, QuantilesProtoFieldsAreCorrectlyPopulatedNonDefaultK) {
    KllQuantileOptions options;
    options.set_k(6000);
    std::unique_ptr<KllQuantile> aggregator = KllQuantile::Create(options);
    for (int i = 10; i <= 1000; i++) {
        aggregator->Add(i);
    }
    EXPECT_EQ(aggregator->num_values(), 991);

    AggregatorStateProto aggregator_state = aggregator->SerializeToProto();
    EXPECT_EQ(aggregator_state.num_values(), 991);

    ASSERT_TRUE(aggregator_state.HasExtension(kll_quantiles_state));
    const KllQuantilesStateProto& quantiles_state =
            aggregator_state.GetExtension(kll_quantiles_state);

    ASSERT_TRUE(quantiles_state.has_k());
    EXPECT_EQ(quantiles_state.k(), 6000);

    // Min, max
    ASSERT_TRUE(quantiles_state.has_min());
    EXPECT_EQ(quantiles_state.min(), "\xA");
    ASSERT_TRUE(quantiles_state.has_max());
    EXPECT_EQ(quantiles_state.max(), "\xE8\a");

    EXPECT_EQ(quantiles_state.compactors_size(), 1);
}

TEST(KllQuantileSerializationTest, EmptyQuantilesProto) {
    std::unique_ptr<KllQuantile> aggregator = KllQuantile::Create();

    AggregatorStateProto aggregator_state = aggregator->SerializeToProto();
    EXPECT_EQ(aggregator->num_values(), 0);

    EXPECT_EQ(aggregator_state.type(), zetasketch::android::KLL_QUANTILES);
    ASSERT_TRUE(aggregator_state.HasExtension(kll_quantiles_state));
    aggregator_state.GetExtension(kll_quantiles_state);

    const KllQuantilesStateProto& quantiles_state =
            aggregator_state.GetExtension(kll_quantiles_state);

    ASSERT_TRUE(quantiles_state.has_k());
    EXPECT_GE(quantiles_state.k(), 1000);
    EXPECT_LT(quantiles_state.k(), 5000);

    ASSERT_TRUE(quantiles_state.has_inv_eps());
    EXPECT_EQ(quantiles_state.inv_eps(), 1000);

    ASSERT_FALSE(quantiles_state.has_min());
    ASSERT_FALSE(quantiles_state.has_max());

    EXPECT_EQ(quantiles_state.compactors_size(), 0);
    ASSERT_FALSE(quantiles_state.has_sampler());
}
}  // namespace

}  // namespace aggregation
}  // namespace dist_proc
