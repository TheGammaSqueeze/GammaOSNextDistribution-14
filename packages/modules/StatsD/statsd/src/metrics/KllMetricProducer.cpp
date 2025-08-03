/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "KllMetricProducer.h"

#include <limits.h>
#include <stdlib.h>

#include "guardrail/StatsdStats.h"
#include "metrics/parsing_utils/metrics_manager_util.h"
#include "stats_log_util.h"

using android::util::FIELD_COUNT_REPEATED;
using android::util::FIELD_TYPE_BYTES;
using android::util::FIELD_TYPE_INT32;
using android::util::FIELD_TYPE_MESSAGE;
using android::util::ProtoOutputStream;
using std::nullopt;
using std::optional;
using std::string;
using zetasketch::android::AggregatorStateProto;

namespace android {
namespace os {
namespace statsd {

// for StatsLogReport
const int FIELD_ID_KLL_METRICS = 16;
// for KllBucketInfo
const int FIELD_ID_SKETCH_INDEX = 1;
const int FIELD_ID_KLL_SKETCH = 2;
const int FIELD_ID_SKETCHES = 3;
const int FIELD_ID_BUCKET_NUM = 4;
const int FIELD_ID_START_BUCKET_ELAPSED_MILLIS = 5;
const int FIELD_ID_END_BUCKET_ELAPSED_MILLIS = 6;
const int FIELD_ID_CONDITION_TRUE_NS = 7;

KllMetricProducer::KllMetricProducer(const ConfigKey& key, const KllMetric& metric,
                                     const uint64_t protoHash, const PullOptions& pullOptions,
                                     const BucketOptions& bucketOptions,
                                     const WhatOptions& whatOptions,
                                     const ConditionOptions& conditionOptions,
                                     const StateOptions& stateOptions,
                                     const ActivationOptions& activationOptions,
                                     const GuardrailOptions& guardrailOptions)
    : ValueMetricProducer(metric.id(), key, protoHash, pullOptions, bucketOptions, whatOptions,
                          conditionOptions, stateOptions, activationOptions, guardrailOptions) {
}

KllMetricProducer::DumpProtoFields KllMetricProducer::getDumpProtoFields() const {
    return {FIELD_ID_KLL_METRICS,
            FIELD_ID_BUCKET_NUM,
            FIELD_ID_START_BUCKET_ELAPSED_MILLIS,
            FIELD_ID_END_BUCKET_ELAPSED_MILLIS,
            FIELD_ID_CONDITION_TRUE_NS,
            /*conditionCorrectionNsFieldId=*/nullopt};
}

void KllMetricProducer::writePastBucketAggregateToProto(
        const int aggIndex, const unique_ptr<KllQuantile>& kll, const int sampleSize,
        ProtoOutputStream* const protoOutput) const {
    uint64_t sketchesToken =
            protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_SKETCHES);
    protoOutput->write(FIELD_TYPE_INT32 | FIELD_ID_SKETCH_INDEX, aggIndex);

    // TODO(b/186737273): Serialize directly to ProtoOutputStream
    const AggregatorStateProto& aggProto = kll->SerializeToProto();
    const size_t numBytes = aggProto.ByteSizeLong();
    const unique_ptr<char[]> buffer(new char[numBytes]);
    aggProto.SerializeToArray(&buffer[0], numBytes);
    protoOutput->write(FIELD_TYPE_BYTES | FIELD_ID_KLL_SKETCH, &buffer[0], numBytes);

    VLOG("\t\t sketch %d: %zu bytes", aggIndex, numBytes);
    protoOutput->end(sketchesToken);
}

optional<int64_t> getInt64ValueFromEvent(const LogEvent& event, const Matcher& matcher) {
    for (const FieldValue& value : event.getValues()) {
        if (value.mField.matches(matcher)) {
            switch (value.mValue.type) {
                case INT:
                    return {value.mValue.int_value};
                case LONG:
                    return {value.mValue.long_value};
                default:
                    return nullopt;
            }
        }
    }
    return nullopt;
}

bool KllMetricProducer::aggregateFields(const int64_t eventTimeNs,
                                        const MetricDimensionKey& eventKey, const LogEvent& event,
                                        vector<Interval>& intervals, Empty& empty) {
    bool seenNewData = false;
    for (size_t i = 0; i < mFieldMatchers.size(); i++) {
        const Matcher& matcher = mFieldMatchers[i];
        Interval& interval = intervals[i];
        interval.aggIndex = i;
        const optional<int64_t> valueOpt = getInt64ValueFromEvent(event, matcher);
        if (!valueOpt) {
            VLOG("Failed to get value %zu from event %s", i, event.ToString().c_str());
            StatsdStats::getInstance().noteBadValueType(mMetricId);
            return seenNewData;
        }

        // interval.aggregate can be nullptr from cases:
        // 1. Initialization from default construction of Interval struct.
        // 2. Ownership of the unique_ptr<KllQuantile> at interval.aggregate being transferred to
        // PastBucket after flushing.
        if (!interval.aggregate) {
            interval.aggregate = KllQuantile::Create();
        }
        seenNewData = true;
        interval.aggregate->Add(valueOpt.value());
        interval.sampleSize += 1;
    }
    return seenNewData;
}

PastBucket<unique_ptr<KllQuantile>> KllMetricProducer::buildPartialBucket(
        int64_t bucketEndTimeNs, vector<Interval>& intervals) {
    PastBucket<unique_ptr<KllQuantile>> bucket;
    bucket.mBucketStartNs = mCurrentBucketStartTimeNs;
    bucket.mBucketEndNs = bucketEndTimeNs;
    for (Interval& interval : intervals) {
        if (interval.hasValue()) {
            bucket.aggIndex.push_back(interval.aggIndex);
            // Transfer ownership of unique_ptr<KllQuantile> from interval.aggregate to
            // bucket.aggregates vector. interval.aggregate is guaranteed to be nullptr after this.
            bucket.aggregates.push_back(std::move(interval.aggregate));
        }
    }
    return bucket;
}

size_t KllMetricProducer::byteSizeLocked() const {
    size_t totalSize = 0;
    for (const auto& [_, buckets] : mPastBuckets) {
        totalSize += buckets.size() * kBucketSize;
        for (const auto& bucket : buckets) {
            static const size_t kIntSize = sizeof(int);
            totalSize += bucket.aggIndex.size() * kIntSize;
            if (!bucket.aggregates.empty()) {
                static const size_t kInt64Size = sizeof(int64_t);
                // Assume sketch size is the same for all aggregations in a bucket.
                totalSize += bucket.aggregates.size() * kInt64Size *
                             bucket.aggregates[0]->num_stored_values();
            }
        }
    }
    return totalSize;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
