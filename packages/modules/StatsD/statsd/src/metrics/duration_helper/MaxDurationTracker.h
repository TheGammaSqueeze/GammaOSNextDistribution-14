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

#ifndef MAX_DURATION_TRACKER_H
#define MAX_DURATION_TRACKER_H

#include "DurationTracker.h"

namespace android {
namespace os {
namespace statsd {

// Tracks a pool of atom durations, and output the max duration for each bucket.
// To get max duration, we need to keep track of each individual durations, and compare them when
// they stop or bucket expires.
class MaxDurationTracker : public DurationTracker {
public:
    MaxDurationTracker(const ConfigKey& key, const int64_t id, const MetricDimensionKey& eventKey,
                       const sp<ConditionWizard>& wizard, int conditionIndex, bool nesting,
                       int64_t currentBucketStartNs, int64_t currentBucketNum, int64_t startTimeNs,
                       int64_t bucketSizeNs, bool conditionSliced, bool fullLink,
                       const std::vector<sp<AnomalyTracker>>& anomalyTrackers);

    MaxDurationTracker(const MaxDurationTracker& tracker) = default;

    void noteStart(const HashableDimensionKey& key, bool condition, int64_t eventTime,
                   const ConditionKey& conditionKey, size_t dimensionHardLimit) override;
    void noteStop(const HashableDimensionKey& key, int64_t eventTime, const bool stopAll) override;
    void noteStopAll(const int64_t eventTime) override;

    bool flushIfNeeded(
            int64_t timestampNs, const optional<UploadThreshold>& uploadThreshold,
            std::unordered_map<MetricDimensionKey, std::vector<DurationBucket>>* output) override;
    bool flushCurrentBucket(
            int64_t eventTimeNs, const optional<UploadThreshold>& uploadThreshold,
            const int64_t globalConditionTrueNs,
            std::unordered_map<MetricDimensionKey, std::vector<DurationBucket>>*) override;

    void onSlicedConditionMayChange(const int64_t timestamp) override;
    void onConditionChanged(bool condition, int64_t timestamp) override;

    void onStateChanged(const int64_t timestamp, const int32_t atomId,
                        const FieldValue& newState) override;

    int64_t predictAnomalyTimestampNs(const AnomalyTracker& anomalyTracker,
                                      const int64_t currentTimestamp) const override;
    void dumpStates(int out, bool verbose) const override;

    int64_t getCurrentStateKeyDuration() const override;

    int64_t getCurrentStateKeyFullBucketDuration() const override;

    void updateCurrentStateKey(int32_t atomId, const FieldValue& newState);

    bool hasAccumulatedDuration() const override;

protected:
    // Returns true if at least one of the mInfos is started.
    bool hasStartedDuration() const override;

private:
    std::unordered_map<HashableDimensionKey, DurationInfo> mInfos;

    int64_t mDuration;  // current recorded duration result (for partial bucket)

    void noteConditionChanged(const HashableDimensionKey& key, bool conditionMet,
                              const int64_t timestamp);

    // return true if we should not allow newKey to be tracked because we are above the threshold
    bool hitGuardRail(const HashableDimensionKey& newKey, size_t dimensionHardLimit) const;

    FRIEND_TEST(MaxDurationTrackerTest, TestSimpleMaxDuration);
    FRIEND_TEST(MaxDurationTrackerTest, TestCrossBucketBoundary);
    FRIEND_TEST(MaxDurationTrackerTest, TestMaxDurationWithCondition);
    FRIEND_TEST(MaxDurationTrackerTest, TestStopAll);
    FRIEND_TEST(MaxDurationTrackerTest, TestAnomalyDetection);
    FRIEND_TEST(MaxDurationTrackerTest, TestAnomalyPredictedTimestamp);
    FRIEND_TEST(MaxDurationTrackerTest, TestUploadThreshold);
    FRIEND_TEST(MaxDurationTrackerTest, TestNoAccumulatingDuration);
};

}  // namespace statsd
}  // namespace os
}  // namespace android

#endif  // MAX_DURATION_TRACKER_H
