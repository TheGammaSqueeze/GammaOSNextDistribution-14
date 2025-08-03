/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef SHARD_OFFSET_PROVIDER_H
#define SHARD_OFFSET_PROVIDER_H

#include <gtest/gtest_prod.h>
#include <stdlib.h>

namespace android {
namespace os {
namespace statsd {

/*
 * Class is not guarded by any mutex. It is currently thread safe.
 * Thread safety needs to be considered on all future changes to this class.
 */
class ShardOffsetProvider final {
public:
    ~ShardOffsetProvider(){};

    uint32_t getShardOffset() const {
        return mShardOffset;
    }

    static ShardOffsetProvider& getInstance();

private:
    ShardOffsetProvider(const uint32_t shardOffset);

    // Only used for testing.
    void setShardOffset(const uint32_t shardOffset) {
        mShardOffset = shardOffset;
    }

    uint32_t mShardOffset;

    FRIEND_TEST(CountMetricE2eTest, TestDimensionalSampling);
    FRIEND_TEST(DurationMetricE2eTest, TestDimensionalSampling);
    FRIEND_TEST(GaugeMetricE2ePushedTest, TestDimensionalSampling);
    FRIEND_TEST(GaugeMetricE2ePushedTest, TestPushedGaugeMetricSamplingWithDimensionalSampling);
    FRIEND_TEST(GaugeMetricProducerTest, TestPullDimensionalSampling);
    FRIEND_TEST(KllMetricE2eTest, TestDimensionalSampling);
    FRIEND_TEST(NumericValueMetricProducerTest, TestDimensionalSampling);
    FRIEND_TEST(StatsdStatsTest, TestShardOffsetProvider);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
#endif  // METRIC_PRODUCER_H