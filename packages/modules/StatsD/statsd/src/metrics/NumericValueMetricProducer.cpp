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

#include "NumericValueMetricProducer.h"

#include <limits.h>
#include <stdlib.h>

#include "guardrail/StatsdStats.h"
#include "metrics/parsing_utils/metrics_manager_util.h"
#include "stats_log_util.h"

using android::util::FIELD_COUNT_REPEATED;
using android::util::FIELD_TYPE_BOOL;
using android::util::FIELD_TYPE_DOUBLE;
using android::util::FIELD_TYPE_INT32;
using android::util::FIELD_TYPE_INT64;
using android::util::FIELD_TYPE_MESSAGE;
using android::util::FIELD_TYPE_STRING;
using android::util::ProtoOutputStream;
using std::optional;
using std::shared_ptr;
using std::string;
using std::unordered_map;

namespace android {
namespace os {
namespace statsd {

// for StatsLogReport
const int FIELD_ID_VALUE_METRICS = 7;
// for ValueBucketInfo
const int FIELD_ID_VALUE_INDEX = 1;
const int FIELD_ID_VALUE_LONG = 2;
const int FIELD_ID_VALUE_DOUBLE = 3;
const int FIELD_ID_VALUE_SAMPLESIZE = 4;
const int FIELD_ID_VALUES = 9;
const int FIELD_ID_BUCKET_NUM = 4;
const int FIELD_ID_START_BUCKET_ELAPSED_MILLIS = 5;
const int FIELD_ID_END_BUCKET_ELAPSED_MILLIS = 6;
const int FIELD_ID_CONDITION_TRUE_NS = 10;
const int FIELD_ID_CONDITION_CORRECTION_NS = 11;

const Value ZERO_LONG((int64_t)0);
const Value ZERO_DOUBLE(0.0);

// ValueMetric has a minimum bucket size of 10min so that we don't pull too frequently
NumericValueMetricProducer::NumericValueMetricProducer(
        const ConfigKey& key, const ValueMetric& metric, const uint64_t protoHash,
        const PullOptions& pullOptions, const BucketOptions& bucketOptions,
        const WhatOptions& whatOptions, const ConditionOptions& conditionOptions,
        const StateOptions& stateOptions, const ActivationOptions& activationOptions,
        const GuardrailOptions& guardrailOptions)
    : ValueMetricProducer(metric.id(), key, protoHash, pullOptions, bucketOptions, whatOptions,
                          conditionOptions, stateOptions, activationOptions, guardrailOptions),
      mUseAbsoluteValueOnReset(metric.use_absolute_value_on_reset()),
      mAggregationType(metric.aggregation_type()),
      mIncludeSampleSize(metric.has_include_sample_size()
                                 ? metric.include_sample_size()
                                 : metric.aggregation_type() == ValueMetric_AggregationType_AVG),
      mUseDiff(metric.has_use_diff() ? metric.use_diff() : isPulled()),
      mValueDirection(metric.value_direction()),
      mSkipZeroDiffOutput(metric.skip_zero_diff_output()),
      mUseZeroDefaultBase(metric.use_zero_default_base()),
      mHasGlobalBase(false),
      mMaxPullDelayNs(metric.has_max_pull_delay_sec() ? metric.max_pull_delay_sec() * NS_PER_SEC
                                                      : StatsdStats::kPullMaxDelayNs) {
    // TODO(b/186677791): Use initializer list to initialize mUploadThreshold.
    if (metric.has_threshold()) {
        mUploadThreshold = metric.threshold();
    }
}

void NumericValueMetricProducer::invalidateCurrentBucket(const int64_t dropTimeNs,
                                                         const BucketDropReason reason) {
    ValueMetricProducer::invalidateCurrentBucket(dropTimeNs, reason);

    switch (reason) {
        case BucketDropReason::DUMP_REPORT_REQUESTED:
        case BucketDropReason::EVENT_IN_WRONG_BUCKET:
        case BucketDropReason::CONDITION_UNKNOWN:
        case BucketDropReason::PULL_FAILED:
        case BucketDropReason::PULL_DELAYED:
        case BucketDropReason::DIMENSION_GUARDRAIL_REACHED:
            resetBase();
            break;
        default:
            break;
    }
}

void NumericValueMetricProducer::resetBase() {
    for (auto& [_, dimInfo] : mDimInfos) {
        for (optional<Value>& base : dimInfo.dimExtras) {
            base.reset();
        }
    }
    mHasGlobalBase = false;
}

void NumericValueMetricProducer::writePastBucketAggregateToProto(
        const int aggIndex, const Value& value, const int sampleSize,
        ProtoOutputStream* const protoOutput) const {
    uint64_t valueToken =
            protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_VALUES);
    protoOutput->write(FIELD_TYPE_INT32 | FIELD_ID_VALUE_INDEX, aggIndex);
    if (mIncludeSampleSize) {
        protoOutput->write(FIELD_TYPE_INT32 | FIELD_ID_VALUE_SAMPLESIZE, sampleSize);
    }
    if (value.getType() == LONG) {
        protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_VALUE_LONG, (long long)value.long_value);
        VLOG("\t\t value %d: %lld", aggIndex, (long long)value.long_value);
    } else if (value.getType() == DOUBLE) {
        protoOutput->write(FIELD_TYPE_DOUBLE | FIELD_ID_VALUE_DOUBLE, value.double_value);
        VLOG("\t\t value %d: %.2f", aggIndex, value.double_value);
    } else {
        VLOG("Wrong value type for ValueMetric output: %d", value.getType());
    }
    protoOutput->end(valueToken);
}

void NumericValueMetricProducer::onActiveStateChangedInternalLocked(const int64_t eventTimeNs,
                                                                    const bool isActive) {
    // When active state changes from true to false for pulled metric, clear diff base but don't
    // reset other counters as we may accumulate more value in the bucket.
    if (mUseDiff && !isActive) {
        resetBase();
    }
}

// Only called when mIsActive and the event is NOT too late.
void NumericValueMetricProducer::onConditionChangedInternalLocked(const ConditionState oldCondition,
                                                                  const ConditionState newCondition,
                                                                  const int64_t eventTimeNs) {
    // For metrics that use diff, when condition changes from true to false,
    // clear diff base but don't reset other counts because we may accumulate
    // more value in the bucket.
    if (mUseDiff &&
        (oldCondition == ConditionState::kTrue && newCondition == ConditionState::kFalse)) {
        resetBase();
    }
}

void NumericValueMetricProducer::prepareFirstBucketLocked() {
    // Kicks off the puller immediately if condition is true and diff based.
    if (mIsActive && isPulled() && mCondition == ConditionState::kTrue && mUseDiff) {
        pullAndMatchEventsLocked(mCurrentBucketStartTimeNs);
    }
}

void NumericValueMetricProducer::pullAndMatchEventsLocked(const int64_t timestampNs) {
    vector<shared_ptr<LogEvent>> allData;
    if (!mPullerManager->Pull(mPullAtomId, mConfigKey, timestampNs, &allData)) {
        ALOGE("Stats puller failed for tag: %d at %lld", mPullAtomId, (long long)timestampNs);
        invalidateCurrentBucket(timestampNs, BucketDropReason::PULL_FAILED);
        return;
    }

    accumulateEvents(allData, timestampNs, timestampNs);
}

int64_t NumericValueMetricProducer::calcPreviousBucketEndTime(const int64_t currentTimeNs) {
    return mTimeBaseNs + ((currentTimeNs - mTimeBaseNs) / mBucketSizeNs) * mBucketSizeNs;
}

// By design, statsd pulls data at bucket boundaries using AlarmManager. These pulls are likely
// to be delayed. Other events like condition changes or app upgrade which are not based on
// AlarmManager might have arrived earlier and close the bucket.
void NumericValueMetricProducer::onDataPulled(const std::vector<std::shared_ptr<LogEvent>>& allData,
                                              PullResult pullResult, int64_t originalPullTimeNs) {
    lock_guard<mutex> lock(mMutex);
    if (mCondition == ConditionState::kTrue) {
        // If the pull failed, we won't be able to compute a diff.
        if (pullResult == PullResult::PULL_RESULT_FAIL) {
            invalidateCurrentBucket(originalPullTimeNs, BucketDropReason::PULL_FAILED);
        } else if (pullResult == PullResult::PULL_RESULT_SUCCESS) {
            bool isEventLate = originalPullTimeNs < getCurrentBucketEndTimeNs();
            if (isEventLate) {
                // If the event is late, we are in the middle of a bucket. Just
                // process the data without trying to snap the data to the nearest bucket.
                accumulateEvents(allData, originalPullTimeNs, originalPullTimeNs);
            } else {
                // For scheduled pulled data, the effective event time is snap to the nearest
                // bucket end. In the case of waking up from a deep sleep state, we will
                // attribute to the previous bucket end. If the sleep was long but not very
                // long, we will be in the immediate next bucket. Previous bucket may get a
                // larger number as we pull at a later time than real bucket end.
                //
                // If the sleep was very long, we skip more than one bucket before sleep. In
                // this case, if the diff base will be cleared and this new data will serve as
                // new diff base.
                int64_t bucketEndTimeNs = calcPreviousBucketEndTime(originalPullTimeNs) - 1;
                StatsdStats::getInstance().noteBucketBoundaryDelayNs(
                        mMetricId, originalPullTimeNs - bucketEndTimeNs);
                accumulateEvents(allData, originalPullTimeNs, bucketEndTimeNs);
            }
        }
    }

    // We can probably flush the bucket. Since we used bucketEndTimeNs when calling
    // #onMatchedLogEventInternalLocked, the current bucket will not have been flushed.
    flushIfNeededLocked(originalPullTimeNs);
}

void NumericValueMetricProducer::combineValueFields(pair<LogEvent, vector<int>>& eventValues,
                                                    const LogEvent& newEvent,
                                                    const vector<int>& newValueIndices) const {
    if (eventValues.second.size() != newValueIndices.size()) {
        ALOGE("NumericValueMetricProducer value indices sizes don't match");
        return;
    }
    vector<FieldValue>* const aggregateFieldValues = eventValues.first.getMutableValues();
    const vector<FieldValue>& newFieldValues = newEvent.getValues();
    for (size_t i = 0; i < eventValues.second.size(); ++i) {
        if (newValueIndices[i] != -1 && eventValues.second[i] != -1) {
            (*aggregateFieldValues)[eventValues.second[i]].mValue +=
                    newFieldValues[newValueIndices[i]].mValue;
        }
    }
}

// Process events retrieved from a pull.
void NumericValueMetricProducer::accumulateEvents(const vector<shared_ptr<LogEvent>>& allData,
                                                  int64_t originalPullTimeNs,
                                                  int64_t eventElapsedTimeNs) {
    if (isEventLateLocked(eventElapsedTimeNs)) {
        VLOG("Skip bucket end pull due to late arrival: %lld vs %lld",
             (long long)eventElapsedTimeNs, (long long)mCurrentBucketStartTimeNs);
        StatsdStats::getInstance().noteLateLogEventSkipped(mMetricId);
        invalidateCurrentBucket(eventElapsedTimeNs, BucketDropReason::EVENT_IN_WRONG_BUCKET);
        return;
    }

    const int64_t elapsedRealtimeNs = getElapsedRealtimeNs();
    const int64_t pullDelayNs = elapsedRealtimeNs - originalPullTimeNs;
    StatsdStats::getInstance().notePullDelay(mPullAtomId, pullDelayNs);
    if (pullDelayNs > mMaxPullDelayNs) {
        ALOGE("Pull finish too late for atom %d, longer than %lld", mPullAtomId,
              (long long)mMaxPullDelayNs);
        StatsdStats::getInstance().notePullExceedMaxDelay(mPullAtomId);
        // We are missing one pull from the bucket which means we will not have a complete view of
        // what's going on.
        invalidateCurrentBucket(eventElapsedTimeNs, BucketDropReason::PULL_DELAYED);
        return;
    }

    mMatchedMetricDimensionKeys.clear();
    if (mUseDiff) {
        // An extra aggregation step is needed to sum values with matching dimensions
        // before calculating the diff between sums of consecutive pulls.
        std::unordered_map<HashableDimensionKey, pair<LogEvent, vector<int>>> aggregateEvents;
        for (const auto& data : allData) {
            const auto [matchResult, transformedEvent] =
                    mEventMatcherWizard->matchLogEvent(*data, mWhatMatcherIndex);
            if (matchResult != MatchingState::kMatched) {
                continue;
            }

            // Get dimensions_in_what key and value indices.
            HashableDimensionKey dimensionsInWhat;
            vector<int> valueIndices(mFieldMatchers.size(), -1);
            const LogEvent& eventRef = transformedEvent == nullptr ? *data : *transformedEvent;
            if (!filterValues(mDimensionsInWhat, mFieldMatchers, eventRef.getValues(),
                              dimensionsInWhat, valueIndices)) {
                StatsdStats::getInstance().noteBadValueType(mMetricId);
            }

            // Store new event in map or combine values in existing event.
            auto it = aggregateEvents.find(dimensionsInWhat);
            if (it == aggregateEvents.end()) {
                aggregateEvents.emplace(std::piecewise_construct,
                                        std::forward_as_tuple(dimensionsInWhat),
                                        std::forward_as_tuple(eventRef, valueIndices));
            } else {
                combineValueFields(it->second, eventRef, valueIndices);
            }
        }

        for (auto& [dimKey, eventInfo] : aggregateEvents) {
            eventInfo.first.setElapsedTimestampNs(eventElapsedTimeNs);
            onMatchedLogEventLocked(mWhatMatcherIndex, eventInfo.first);
        }
    } else {
        for (const auto& data : allData) {
            const auto [matchResult, transformedEvent] =
                    mEventMatcherWizard->matchLogEvent(*data, mWhatMatcherIndex);
            if (matchResult == MatchingState::kMatched) {
                LogEvent localCopy = transformedEvent == nullptr ? *data : *transformedEvent;
                localCopy.setElapsedTimestampNs(eventElapsedTimeNs);
                onMatchedLogEventLocked(mWhatMatcherIndex, localCopy);
            }
        }
    }

    // If a key that is:
    // 1. Tracked in mCurrentSlicedBucket and
    // 2. A superset of the current mStateChangePrimaryKey
    // was not found in the new pulled data (i.e. not in mMatchedDimensionInWhatKeys)
    // then we clear the data from mDimInfos to reset the base and current state key.
    for (auto& [metricDimensionKey, currentValueBucket] : mCurrentSlicedBucket) {
        const auto& whatKey = metricDimensionKey.getDimensionKeyInWhat();
        bool presentInPulledData =
                mMatchedMetricDimensionKeys.find(whatKey) != mMatchedMetricDimensionKeys.end();
        if (!presentInPulledData &&
            containsLinkedStateValues(whatKey, mStateChangePrimaryKey.second, mMetric2StateLinks,
                                      mStateChangePrimaryKey.first)) {
            auto it = mDimInfos.find(whatKey);
            if (it != mDimInfos.end()) {
                mDimInfos.erase(it);
            }
            // Turn OFF condition timer for keys not present in pulled data.
            currentValueBucket.conditionTimer.onConditionChanged(false, eventElapsedTimeNs);
        }
    }
    mMatchedMetricDimensionKeys.clear();
    mHasGlobalBase = true;

    // If we reach the guardrail, we might have dropped some data which means the bucket is
    // incomplete.
    //
    // The base also needs to be reset. If we do not have the full data, we might
    // incorrectly compute the diff when mUseZeroDefaultBase is true since an existing key
    // might be missing from mCurrentSlicedBucket.
    if (hasReachedGuardRailLimit()) {
        invalidateCurrentBucket(eventElapsedTimeNs, BucketDropReason::DIMENSION_GUARDRAIL_REACHED);
        mCurrentSlicedBucket.clear();
    }
}

bool NumericValueMetricProducer::hitFullBucketGuardRailLocked(const MetricDimensionKey& newKey) {
    // ===========GuardRail==============
    // 1. Report the tuple count if the tuple count > soft limit
    if (mCurrentFullBucket.find(newKey) != mCurrentFullBucket.end()) {
        return false;
    }
    if (mCurrentFullBucket.size() > mDimensionSoftLimit - 1) {
        size_t newTupleCount = mCurrentFullBucket.size() + 1;
        // 2. Don't add more tuples, we are above the allowed threshold. Drop the data.
        if (newTupleCount > mDimensionHardLimit) {
            if (!mHasHitGuardrail) {
                ALOGE("ValueMetric %lld dropping data for full bucket dimension key %s",
                      (long long)mMetricId, newKey.toString().c_str());
                mHasHitGuardrail = true;
            }
            return true;
        }
    }

    return false;
}

bool getDoubleOrLong(const LogEvent& event, const Matcher& matcher, Value& ret) {
    for (const FieldValue& value : event.getValues()) {
        if (value.mField.matches(matcher)) {
            switch (value.mValue.type) {
                case INT:
                    ret.setLong(value.mValue.int_value);
                    break;
                case LONG:
                    ret.setLong(value.mValue.long_value);
                    break;
                case FLOAT:
                    ret.setDouble(value.mValue.float_value);
                    break;
                case DOUBLE:
                    ret.setDouble(value.mValue.double_value);
                    break;
                default:
                    return false;
                    break;
            }
            return true;
        }
    }
    return false;
}

bool NumericValueMetricProducer::aggregateFields(const int64_t eventTimeNs,
                                                 const MetricDimensionKey& eventKey,
                                                 const LogEvent& event, vector<Interval>& intervals,
                                                 ValueBases& bases) {
    if (bases.size() < mFieldMatchers.size()) {
        VLOG("Resizing number of bases to %zu", mFieldMatchers.size());
        bases.resize(mFieldMatchers.size());
    }

    // We only use anomaly detection under certain cases.
    // N.B.: The anomaly detection cases were modified in order to fix an issue with value metrics
    // containing multiple values. We tried to retain all previous behaviour, but we are unsure the
    // previous behaviour was correct. At the time of the fix, anomaly detection had no owner.
    // Whoever next works on it should look into the cases where it is triggered in this function.
    // Discussion here: http://ag/6124370.
    bool useAnomalyDetection = true;
    bool seenNewData = false;
    for (size_t i = 0; i < mFieldMatchers.size(); i++) {
        const Matcher& matcher = mFieldMatchers[i];
        Interval& interval = intervals[i];
        interval.aggIndex = i;
        optional<Value>& base = bases[i];
        Value value;
        if (!getDoubleOrLong(event, matcher, value)) {
            VLOG("Failed to get value %zu from event %s", i, event.ToString().c_str());
            StatsdStats::getInstance().noteBadValueType(mMetricId);
            return seenNewData;
        }
        seenNewData = true;
        if (mUseDiff) {
            if (!base.has_value()) {
                if (mHasGlobalBase && mUseZeroDefaultBase) {
                    // The bucket has global base. This key does not.
                    // Optionally use zero as base.
                    base = (value.type == LONG ? ZERO_LONG : ZERO_DOUBLE);
                } else {
                    // no base. just update base and return.
                    base = value;

                    // If we're missing a base, do not use anomaly detection on incomplete data
                    useAnomalyDetection = false;

                    // Continue (instead of return) here in order to set base value for other bases
                    continue;
                }
            }
            Value diff;
            switch (mValueDirection) {
                case ValueMetric::INCREASING:
                    if (value >= base.value()) {
                        diff = value - base.value();
                    } else if (mUseAbsoluteValueOnReset) {
                        diff = value;
                    } else {
                        VLOG("Unexpected decreasing value");
                        StatsdStats::getInstance().notePullDataError(mPullAtomId);
                        base = value;
                        // If we've got bad data, do not use anomaly detection
                        useAnomalyDetection = false;
                        continue;
                    }
                    break;
                case ValueMetric::DECREASING:
                    if (base.value() >= value) {
                        diff = base.value() - value;
                    } else if (mUseAbsoluteValueOnReset) {
                        diff = value;
                    } else {
                        VLOG("Unexpected increasing value");
                        StatsdStats::getInstance().notePullDataError(mPullAtomId);
                        base = value;
                        // If we've got bad data, do not use anomaly detection
                        useAnomalyDetection = false;
                        continue;
                    }
                    break;
                case ValueMetric::ANY:
                    diff = value - base.value();
                    break;
                default:
                    break;
            }
            base = value;
            value = diff;
        }

        if (interval.hasValue()) {
            switch (mAggregationType) {
                case ValueMetric::SUM:
                    // for AVG, we add up and take average when flushing the bucket
                case ValueMetric::AVG:
                    interval.aggregate += value;
                    break;
                case ValueMetric::MIN:
                    interval.aggregate = min(value, interval.aggregate);
                    break;
                case ValueMetric::MAX:
                    interval.aggregate = max(value, interval.aggregate);
                    break;
                default:
                    break;
            }
        } else {
            interval.aggregate = value;
        }
        interval.sampleSize += 1;
    }

    // Only trigger the tracker if all intervals are correct and we have not skipped the bucket due
    // to MULTIPLE_BUCKETS_SKIPPED.
    if (useAnomalyDetection && !multipleBucketsSkipped(calcBucketsForwardCount(eventTimeNs))) {
        // TODO: propgate proper values down stream when anomaly support doubles
        long wholeBucketVal = intervals[0].aggregate.long_value;
        auto prev = mCurrentFullBucket.find(eventKey);
        if (prev != mCurrentFullBucket.end()) {
            wholeBucketVal += prev->second;
        }
        for (auto& tracker : mAnomalyTrackers) {
            tracker->detectAndDeclareAnomaly(eventTimeNs, mCurrentBucketNum, mMetricId, eventKey,
                                             wholeBucketVal);
        }
    }
    return seenNewData;
}

PastBucket<Value> NumericValueMetricProducer::buildPartialBucket(int64_t bucketEndTimeNs,
                                                                 vector<Interval>& intervals) {
    PastBucket<Value> bucket;
    bucket.mBucketStartNs = mCurrentBucketStartTimeNs;
    bucket.mBucketEndNs = bucketEndTimeNs;

    // The first value field acts as a "gatekeeper" - if it does not pass the specified threshold,
    // then all interval values are discarded for this bucket.
    if (intervals.empty() || (intervals[0].hasValue() && !valuePassesThreshold(intervals[0]))) {
        return bucket;
    }

    for (const Interval& interval : intervals) {
        // skip the output if the diff is zero
        if (!interval.hasValue() ||
            (mSkipZeroDiffOutput && mUseDiff && interval.aggregate.isZero())) {
            continue;
        }

        bucket.aggIndex.push_back(interval.aggIndex);
        bucket.aggregates.push_back(getFinalValue(interval));
        if (mIncludeSampleSize) {
            bucket.sampleSizes.push_back(interval.sampleSize);
        }
    }
    return bucket;
}

// Also invalidates current bucket if multiple buckets have been skipped
void NumericValueMetricProducer::closeCurrentBucket(const int64_t eventTimeNs,
                                                    const int64_t nextBucketStartTimeNs) {
    ValueMetricProducer::closeCurrentBucket(eventTimeNs, nextBucketStartTimeNs);
    if (mAnomalyTrackers.size() > 0) {
        appendToFullBucket(eventTimeNs > getCurrentBucketEndTimeNs());
    }
}

void NumericValueMetricProducer::initNextSlicedBucket(int64_t nextBucketStartTimeNs) {
    ValueMetricProducer::initNextSlicedBucket(nextBucketStartTimeNs);

    // If we do not have a global base when the condition is true,
    // we will have incomplete bucket for the next bucket.
    if (mUseDiff && !mHasGlobalBase && mCondition) {
        // TODO(b/188878815): mCurrentBucketIsSkipped should probably be set to true here.
        mCurrentBucketIsSkipped = false;
    }
}

void NumericValueMetricProducer::appendToFullBucket(const bool isFullBucketReached) {
    if (mCurrentBucketIsSkipped) {
        if (isFullBucketReached) {
            // If the bucket is invalid, we ignore the full bucket since it contains invalid data.
            mCurrentFullBucket.clear();
        }
        // Current bucket is invalid, we do not add it to the full bucket.
        return;
    }

    if (isFullBucketReached) {  // If full bucket, send to anomaly tracker.
        // Accumulate partial buckets with current value and then send to anomaly tracker.
        if (mCurrentFullBucket.size() > 0) {
            for (const auto& [metricDimensionKey, currentBucket] : mCurrentSlicedBucket) {
                if (hitFullBucketGuardRailLocked(metricDimensionKey) ||
                    currentBucket.intervals.empty()) {
                    continue;
                }
                // TODO: fix this when anomaly can accept double values
                auto& interval = currentBucket.intervals[0];
                if (interval.hasValue()) {
                    mCurrentFullBucket[metricDimensionKey] += interval.aggregate.long_value;
                }
            }
            for (const auto& [metricDimensionKey, value] : mCurrentFullBucket) {
                for (auto& tracker : mAnomalyTrackers) {
                    if (tracker != nullptr) {
                        tracker->addPastBucket(metricDimensionKey, value, mCurrentBucketNum);
                    }
                }
            }
            mCurrentFullBucket.clear();
        } else {
            // Skip aggregating the partial buckets since there's no previous partial bucket.
            for (const auto& [metricDimensionKey, currentBucket] : mCurrentSlicedBucket) {
                for (auto& tracker : mAnomalyTrackers) {
                    if (tracker != nullptr && !currentBucket.intervals.empty()) {
                        // TODO: fix this when anomaly can accept double values
                        auto& interval = currentBucket.intervals[0];
                        if (interval.hasValue()) {
                            tracker->addPastBucket(metricDimensionKey,
                                                   interval.aggregate.long_value,
                                                   mCurrentBucketNum);
                        }
                    }
                }
            }
        }
    } else {
        // Accumulate partial bucket.
        for (const auto& [metricDimensionKey, currentBucket] : mCurrentSlicedBucket) {
            if (!currentBucket.intervals.empty()) {
                // TODO: fix this when anomaly can accept double values
                auto& interval = currentBucket.intervals[0];
                if (interval.hasValue()) {
                    mCurrentFullBucket[metricDimensionKey] += interval.aggregate.long_value;
                }
            }
        }
    }
}

size_t NumericValueMetricProducer::byteSizeLocked() const {
    size_t totalSize = 0;
    for (const auto& [_, buckets] : mPastBuckets) {
        totalSize += buckets.size() * kBucketSize;
        // TODO(b/189283526): Add bytes used to store PastBucket.aggIndex vector
    }
    return totalSize;
}

bool NumericValueMetricProducer::valuePassesThreshold(const Interval& interval) const {
    if (mUploadThreshold == nullopt) {
        return true;
    }

    Value finalValue = getFinalValue(interval);

    double doubleValue =
            finalValue.type == LONG ? (double)finalValue.long_value : finalValue.double_value;
    switch (mUploadThreshold->value_comparison_case()) {
        case UploadThreshold::kLtInt:
            return doubleValue < (double)mUploadThreshold->lt_int();
        case UploadThreshold::kGtInt:
            return doubleValue > (double)mUploadThreshold->gt_int();
        case UploadThreshold::kLteInt:
            return doubleValue <= (double)mUploadThreshold->lte_int();
        case UploadThreshold::kGteInt:
            return doubleValue >= (double)mUploadThreshold->gte_int();
        case UploadThreshold::kLtFloat:
            return doubleValue <= (double)mUploadThreshold->lt_float();
        case UploadThreshold::kGtFloat:
            return doubleValue >= (double)mUploadThreshold->gt_float();
        default:
            ALOGE("Value metric no upload threshold type used");
            return false;
    }
}

Value NumericValueMetricProducer::getFinalValue(const Interval& interval) const {
    if (mAggregationType != ValueMetric::AVG) {
        return interval.aggregate;
    } else {
        double sum = interval.aggregate.type == LONG ? (double)interval.aggregate.long_value
                                                     : interval.aggregate.double_value;
        return Value((double)sum / interval.sampleSize);
    }
}

NumericValueMetricProducer::DumpProtoFields NumericValueMetricProducer::getDumpProtoFields() const {
    return {FIELD_ID_VALUE_METRICS,
            FIELD_ID_BUCKET_NUM,
            FIELD_ID_START_BUCKET_ELAPSED_MILLIS,
            FIELD_ID_END_BUCKET_ELAPSED_MILLIS,
            FIELD_ID_CONDITION_TRUE_NS,
            FIELD_ID_CONDITION_CORRECTION_NS};
}

}  // namespace statsd
}  // namespace os
}  // namespace android
