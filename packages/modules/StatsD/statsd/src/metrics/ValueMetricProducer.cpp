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

#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "ValueMetricProducer.h"

#include <kll.h>
#include <limits.h>
#include <stdlib.h>

#include "FieldValue.h"
#include "HashableDimensionKey.h"
#include "guardrail/StatsdStats.h"
#include "metrics/parsing_utils/metrics_manager_util.h"
#include "stats_log_util.h"
#include "stats_util.h"

using android::util::FIELD_COUNT_REPEATED;
using android::util::FIELD_TYPE_BOOL;
using android::util::FIELD_TYPE_INT32;
using android::util::FIELD_TYPE_INT64;
using android::util::FIELD_TYPE_MESSAGE;
using android::util::ProtoOutputStream;
using dist_proc::aggregation::KllQuantile;
using std::optional;
using std::shared_ptr;
using std::unique_ptr;
using std::unordered_map;
using std::vector;

namespace android {
namespace os {
namespace statsd {

// for StatsLogReport
const int FIELD_ID_ID = 1;
const int FIELD_ID_TIME_BASE = 9;
const int FIELD_ID_BUCKET_SIZE = 10;
const int FIELD_ID_DIMENSION_PATH_IN_WHAT = 11;
const int FIELD_ID_IS_ACTIVE = 14;
const int FIELD_ID_DIMENSION_GUARDRAIL_HIT = 17;
// for *MetricDataWrapper
const int FIELD_ID_DATA = 1;
const int FIELD_ID_SKIPPED = 2;
// for SkippedBuckets
const int FIELD_ID_SKIPPED_START_MILLIS = 3;
const int FIELD_ID_SKIPPED_END_MILLIS = 4;
const int FIELD_ID_SKIPPED_DROP_EVENT = 5;
// for DumpEvent Proto
const int FIELD_ID_BUCKET_DROP_REASON = 1;
const int FIELD_ID_DROP_TIME = 2;
// for *MetricData
const int FIELD_ID_DIMENSION_IN_WHAT = 1;
const int FIELD_ID_BUCKET_INFO = 3;
const int FIELD_ID_DIMENSION_LEAF_IN_WHAT = 4;
const int FIELD_ID_SLICE_BY_STATE = 6;

template <typename AggregatedValue, typename DimExtras>
ValueMetricProducer<AggregatedValue, DimExtras>::ValueMetricProducer(
        const int64_t metricId, const ConfigKey& key, const uint64_t protoHash,
        const PullOptions& pullOptions, const BucketOptions& bucketOptions,
        const WhatOptions& whatOptions, const ConditionOptions& conditionOptions,
        const StateOptions& stateOptions, const ActivationOptions& activationOptions,
        const GuardrailOptions& guardrailOptions)
    : MetricProducer(metricId, key, bucketOptions.timeBaseNs, conditionOptions.conditionIndex,
                     conditionOptions.initialConditionCache, conditionOptions.conditionWizard,
                     protoHash, activationOptions.eventActivationMap,
                     activationOptions.eventDeactivationMap, stateOptions.slicedStateAtoms,
                     stateOptions.stateGroupMap, bucketOptions.splitBucketForAppUpgrade),
      mWhatMatcherIndex(whatOptions.whatMatcherIndex),
      mEventMatcherWizard(whatOptions.matcherWizard),
      mPullerManager(pullOptions.pullerManager),
      mFieldMatchers(whatOptions.fieldMatchers),
      mPullAtomId(pullOptions.pullAtomId),
      mMinBucketSizeNs(bucketOptions.minBucketSizeNs),
      mDimensionSoftLimit(guardrailOptions.dimensionSoftLimit),
      mDimensionHardLimit(guardrailOptions.dimensionHardLimit),
      mCurrentBucketIsSkipped(false),
      mConditionCorrectionThresholdNs(bucketOptions.conditionCorrectionThresholdNs) {
    // TODO(b/185722221): inject directly via initializer list in MetricProducer.
    mBucketSizeNs = bucketOptions.bucketSizeNs;

    // TODO(b/185770171): inject dimensionsInWhat related fields via constructor.
    if (whatOptions.dimensionsInWhat.field() > 0) {
        translateFieldMatcher(whatOptions.dimensionsInWhat, &mDimensionsInWhat);
    }
    mContainANYPositionInDimensionsInWhat = whatOptions.containsAnyPositionInDimensionsInWhat;
    mShouldUseNestedDimensions = whatOptions.shouldUseNestedDimensions;

    if (conditionOptions.conditionLinks.size() > 0) {
        for (const auto& link : conditionOptions.conditionLinks) {
            Metric2Condition mc;
            mc.conditionId = link.condition();
            translateFieldMatcher(link.fields_in_what(), &mc.metricFields);
            translateFieldMatcher(link.fields_in_condition(), &mc.conditionFields);
            mMetric2ConditionLinks.push_back(mc);
        }

        // TODO(b/185770739): use !mMetric2ConditionLinks.empty() instead
        mConditionSliced = true;
    }

    for (const auto& stateLink : stateOptions.stateLinks) {
        Metric2State ms;
        ms.stateAtomId = stateLink.state_atom_id();
        translateFieldMatcher(stateLink.fields_in_what(), &ms.metricFields);
        translateFieldMatcher(stateLink.fields_in_state(), &ms.stateFields);
        mMetric2StateLinks.push_back(ms);
    }

    const int64_t numBucketsForward = calcBucketsForwardCount(bucketOptions.startTimeNs);
    mCurrentBucketNum = numBucketsForward;

    flushIfNeededLocked(bucketOptions.startTimeNs);

    if (isPulled()) {
        mPullerManager->RegisterReceiver(mPullAtomId, mConfigKey, this, getCurrentBucketEndTimeNs(),
                                         mBucketSizeNs);
    }

    // Only do this for partial buckets like first bucket. All other buckets should use
    // flushIfNeeded to adjust start and end to bucket boundaries.
    // Adjust start for partial bucket
    mCurrentBucketStartTimeNs = bucketOptions.startTimeNs;
    mConditionTimer.newBucketStart(mCurrentBucketStartTimeNs, mCurrentBucketStartTimeNs);

    // Now that activations are processed, start the condition timer if needed.
    mConditionTimer.onConditionChanged(mIsActive && mCondition == ConditionState::kTrue,
                                       mCurrentBucketStartTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
ValueMetricProducer<AggregatedValue, DimExtras>::~ValueMetricProducer() {
    VLOG("~ValueMetricProducer() called");
    if (isPulled()) {
        mPullerManager->UnRegisterReceiver(mPullAtomId, mConfigKey, this);
    }
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onStatsdInitCompleted(
        const int64_t eventTimeNs) {
    lock_guard<mutex> lock(mMutex);

    if (isPulled() && mCondition == ConditionState::kTrue && mIsActive) {
        pullAndMatchEventsLocked(eventTimeNs);
    }
    flushCurrentBucketLocked(eventTimeNs, eventTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::notifyAppUpgradeInternalLocked(
        const int64_t eventTimeNs) {
    if (isPulled() && mCondition == ConditionState::kTrue && mIsActive) {
        pullAndMatchEventsLocked(eventTimeNs);
    }
    flushCurrentBucketLocked(eventTimeNs, eventTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
optional<InvalidConfigReason>
ValueMetricProducer<AggregatedValue, DimExtras>::onConfigUpdatedLocked(
        const StatsdConfig& config, const int configIndex, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
        const unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
        const sp<EventMatcherWizard>& matcherWizard,
        const vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap, const sp<ConditionWizard>& wizard,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation) {
    optional<InvalidConfigReason> invalidConfigReason = MetricProducer::onConfigUpdatedLocked(
            config, configIndex, metricIndex, allAtomMatchingTrackers, oldAtomMatchingTrackerMap,
            newAtomMatchingTrackerMap, matcherWizard, allConditionTrackers, conditionTrackerMap,
            wizard, metricToActivationMap, trackerToMetricMap, conditionToMetricMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation);
    if (invalidConfigReason.has_value()) {
        return invalidConfigReason;
    }
    // Update appropriate indices: mWhatMatcherIndex, mConditionIndex and MetricsManager maps.
    const int64_t atomMatcherId = getWhatAtomMatcherIdForMetric(config, configIndex);
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            atomMatcherId, mMetricId, metricIndex, /*enforceOneAtom=*/false,
            allAtomMatchingTrackers, newAtomMatchingTrackerMap, trackerToMetricMap,
            mWhatMatcherIndex);
    if (invalidConfigReason.has_value()) {
        return invalidConfigReason;
    }
    const optional<int64_t>& conditionIdOpt = getConditionIdForMetric(config, configIndex);
    const ConditionLinks& conditionLinks = getConditionLinksForMetric(config, configIndex);
    if (conditionIdOpt.has_value()) {
        invalidConfigReason = handleMetricWithConditions(
                conditionIdOpt.value(), mMetricId, metricIndex, conditionTrackerMap, conditionLinks,
                allConditionTrackers, mConditionTrackerIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return invalidConfigReason;
        }
    }
    sp<EventMatcherWizard> tmpEventWizard = mEventMatcherWizard;
    mEventMatcherWizard = matcherWizard;
    return nullopt;
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onStateChanged(
        int64_t eventTimeNs, int32_t atomId, const HashableDimensionKey& primaryKey,
        const FieldValue& oldState, const FieldValue& newState) {
    // TODO(b/189353769): Acquire lock.
    VLOG("ValueMetricProducer %lld onStateChanged time %lld, State %d, key %s, %d -> %d",
         (long long)mMetricId, (long long)eventTimeNs, atomId, primaryKey.toString().c_str(),
         oldState.mValue.int_value, newState.mValue.int_value);

    FieldValue oldStateCopy = oldState;
    FieldValue newStateCopy = newState;
    mapStateValue(atomId, &oldStateCopy);
    mapStateValue(atomId, &newStateCopy);

    // If old and new states are in the same StateGroup, then we do not need to
    // pull for this state change.
    if (oldStateCopy == newStateCopy) {
        return;
    }

    // If condition is not true or metric is not active, we do not need to pull
    // for this state change.
    if (mCondition != ConditionState::kTrue || !mIsActive) {
        return;
    }

    if (isEventLateLocked(eventTimeNs)) {
        VLOG("Skip event due to late arrival: %lld vs %lld", (long long)eventTimeNs,
             (long long)mCurrentBucketStartTimeNs);
        invalidateCurrentBucket(eventTimeNs, BucketDropReason::EVENT_IN_WRONG_BUCKET);
        return;
    }

    if (isPulled()) {
        mStateChangePrimaryKey.first = atomId;
        mStateChangePrimaryKey.second = primaryKey;
        // TODO(b/185796114): pass mStateChangePrimaryKey as an argument to
        // pullAndMatchEventsLocked
        pullAndMatchEventsLocked(eventTimeNs);
        mStateChangePrimaryKey.first = 0;
        mStateChangePrimaryKey.second = DEFAULT_DIMENSION_KEY;
    }
    flushIfNeededLocked(eventTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onSlicedConditionMayChangeLocked(
        bool overallCondition, const int64_t eventTime) {
    VLOG("Metric %lld onSlicedConditionMayChange", (long long)mMetricId);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::dropDataLocked(const int64_t dropTimeNs) {
    StatsdStats::getInstance().noteBucketDropped(mMetricId);

    // The current partial bucket is not flushed and does not require a pull,
    // so the data is still valid.
    flushIfNeededLocked(dropTimeNs);
    clearPastBucketsLocked(dropTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::clearPastBucketsLocked(
        const int64_t dumpTimeNs) {
    mPastBuckets.clear();
    mSkippedBuckets.clear();
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onDumpReportLocked(
        const int64_t dumpTimeNs, const bool includeCurrentPartialBucket, const bool eraseData,
        const DumpLatency dumpLatency, set<string>* strSet, ProtoOutputStream* protoOutput) {
    VLOG("metric %lld dump report now...", (long long)mMetricId);

    // Pulled metrics need to pull before flushing, which is why they do not call flushIfNeeded.
    // TODO: b/249823426 see if we can pull and call flushIfneeded for pulled value metrics.
    if (!isPulled()) {
        flushIfNeededLocked(dumpTimeNs);
    }
    if (includeCurrentPartialBucket) {
        // For pull metrics, we need to do a pull at bucket boundaries. If we do not do that the
        // current bucket will have incomplete data and the next will have the wrong snapshot to do
        // a diff against. If the condition is false, we are fine since the base data is reset and
        // we are not tracking anything.
        if (isPulled() && mCondition == ConditionState::kTrue && mIsActive) {
            switch (dumpLatency) {
                case FAST:
                    invalidateCurrentBucket(dumpTimeNs, BucketDropReason::DUMP_REPORT_REQUESTED);
                    break;
                case NO_TIME_CONSTRAINTS:
                    pullAndMatchEventsLocked(dumpTimeNs);
                    break;
            }
        }
        flushCurrentBucketLocked(dumpTimeNs, dumpTimeNs);
    }

    protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_ID, (long long)mMetricId);
    protoOutput->write(FIELD_TYPE_BOOL | FIELD_ID_IS_ACTIVE, isActiveLocked());
    if (mPastBuckets.empty() && mSkippedBuckets.empty()) {
        return;
    }

    if (StatsdStats::getInstance().hasHitDimensionGuardrail(mMetricId)) {
        protoOutput->write(FIELD_TYPE_BOOL | FIELD_ID_DIMENSION_GUARDRAIL_HIT, true);
    }
    protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_TIME_BASE, (long long)mTimeBaseNs);
    protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_BUCKET_SIZE, (long long)mBucketSizeNs);
    // Fills the dimension path if not slicing by a primitive repeated field or position ALL.
    if (!mShouldUseNestedDimensions) {
        if (!mDimensionsInWhat.empty()) {
            uint64_t dimenPathToken =
                    protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_ID_DIMENSION_PATH_IN_WHAT);
            writeDimensionPathToProto(mDimensionsInWhat, protoOutput);
            protoOutput->end(dimenPathToken);
        }
    }

    const auto& [metricTypeFieldId, bucketNumFieldId, startBucketMsFieldId, endBucketMsFieldId,
                 conditionTrueNsFieldId,
                 conditionCorrectionNsFieldId] = getDumpProtoFields();

    uint64_t protoToken = protoOutput->start(FIELD_TYPE_MESSAGE | metricTypeFieldId);

    for (const auto& skippedBucket : mSkippedBuckets) {
        uint64_t wrapperToken =
                protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_SKIPPED);
        protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_SKIPPED_START_MILLIS,
                           (long long)(NanoToMillis(skippedBucket.bucketStartTimeNs)));
        protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_SKIPPED_END_MILLIS,
                           (long long)(NanoToMillis(skippedBucket.bucketEndTimeNs)));
        for (const auto& dropEvent : skippedBucket.dropEvents) {
            uint64_t dropEventToken = protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                                         FIELD_ID_SKIPPED_DROP_EVENT);
            protoOutput->write(FIELD_TYPE_INT32 | FIELD_ID_BUCKET_DROP_REASON, dropEvent.reason);
            protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_DROP_TIME,
                               (long long)(NanoToMillis(dropEvent.dropTimeNs)));
            protoOutput->end(dropEventToken);
        }
        protoOutput->end(wrapperToken);
    }

    for (const auto& [metricDimensionKey, buckets] : mPastBuckets) {
        VLOG("  dimension key %s", metricDimensionKey.toString().c_str());
        uint64_t wrapperToken =
                protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_DATA);

        // First fill dimension.
        if (mShouldUseNestedDimensions) {
            uint64_t dimensionToken =
                    protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_ID_DIMENSION_IN_WHAT);
            writeDimensionToProto(metricDimensionKey.getDimensionKeyInWhat(), strSet, protoOutput);
            protoOutput->end(dimensionToken);
        } else {
            writeDimensionLeafNodesToProto(metricDimensionKey.getDimensionKeyInWhat(),
                                           FIELD_ID_DIMENSION_LEAF_IN_WHAT, strSet, protoOutput);
        }

        // Then fill slice_by_state.
        for (auto state : metricDimensionKey.getStateValuesKey().getValues()) {
            uint64_t stateToken = protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                                     FIELD_ID_SLICE_BY_STATE);
            writeStateToProto(state, protoOutput);
            protoOutput->end(stateToken);
        }

        // Then fill bucket_info (*BucketInfo).
        for (const auto& bucket : buckets) {
            uint64_t bucketInfoToken = protoOutput->start(
                    FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_BUCKET_INFO);

            if (bucket.mBucketEndNs - bucket.mBucketStartNs != mBucketSizeNs) {
                protoOutput->write(FIELD_TYPE_INT64 | startBucketMsFieldId,
                                   (long long)NanoToMillis(bucket.mBucketStartNs));
                protoOutput->write(FIELD_TYPE_INT64 | endBucketMsFieldId,
                                   (long long)NanoToMillis(bucket.mBucketEndNs));
            } else {
                protoOutput->write(FIELD_TYPE_INT64 | bucketNumFieldId,
                                   (long long)(getBucketNumFromEndTimeNs(bucket.mBucketEndNs)));
            }
            // We only write the condition timer value if the metric has a
            // condition and/or is sliced by state.
            // If the metric is sliced by state, the condition timer value is
            // also sliced by state to reflect time spent in that state.
            if (mConditionTrackerIndex >= 0 || !mSlicedStateAtoms.empty()) {
                protoOutput->write(FIELD_TYPE_INT64 | conditionTrueNsFieldId,
                                   (long long)bucket.mConditionTrueNs);
            }

            if (conditionCorrectionNsFieldId) {
                // We write the condition correction value when below conditions are true:
                // - if metric is pulled
                // - if it is enabled by metric configuration via dedicated field,
                //   see condition_correction_threshold_nanos
                // - if the abs(value) >= condition_correction_threshold_nanos

                if (isPulled() && mConditionCorrectionThresholdNs &&
                    (abs(bucket.mConditionCorrectionNs) >= mConditionCorrectionThresholdNs)) {
                    protoOutput->write(FIELD_TYPE_INT64 | conditionCorrectionNsFieldId.value(),
                                       (long long)bucket.mConditionCorrectionNs);
                }
            }

            for (int i = 0; i < (int)bucket.aggIndex.size(); i++) {
                VLOG("\t bucket [%lld - %lld]", (long long)bucket.mBucketStartNs,
                     (long long)bucket.mBucketEndNs);
                int sampleSize = !bucket.sampleSizes.empty() ? bucket.sampleSizes[i] : 0;
                writePastBucketAggregateToProto(bucket.aggIndex[i], bucket.aggregates[i],
                                                sampleSize, protoOutput);
            }
            protoOutput->end(bucketInfoToken);
        }
        protoOutput->end(wrapperToken);
    }
    protoOutput->end(protoToken);

    VLOG("metric %lld done with dump report...", (long long)mMetricId);
    if (eraseData) {
        mPastBuckets.clear();
        mSkippedBuckets.clear();
    }
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::invalidateCurrentBucket(
        const int64_t dropTimeNs, const BucketDropReason reason) {
    if (!mCurrentBucketIsSkipped) {
        // Only report to StatsdStats once per invalid bucket.
        StatsdStats::getInstance().noteInvalidatedBucket(mMetricId);
    }

    skipCurrentBucket(dropTimeNs, reason);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::skipCurrentBucket(
        const int64_t dropTimeNs, const BucketDropReason reason) {
    if (!mIsActive) {
        // Don't keep track of skipped buckets if metric is not active.
        return;
    }

    if (!maxDropEventsReached()) {
        mCurrentSkippedBucket.dropEvents.push_back(buildDropEvent(dropTimeNs, reason));
    }
    mCurrentBucketIsSkipped = true;
}

// Handle active state change. Active state change is *mostly* treated like a condition change:
// - drop bucket if active state change event arrives too late
// - if condition is true, pull data on active state changes
// - ConditionTimer tracks changes based on AND of condition and active state.
template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onActiveStateChangedLocked(
        const int64_t eventTimeNs, const bool isActive) {
    const bool eventLate = isEventLateLocked(eventTimeNs);
    if (eventLate) {
        // Drop bucket because event arrived too late, ie. we are missing data for this bucket.
        StatsdStats::getInstance().noteLateLogEventSkipped(mMetricId);
        invalidateCurrentBucket(eventTimeNs, BucketDropReason::EVENT_IN_WRONG_BUCKET);
    }

    if (ConditionState::kTrue != mCondition) {
        // Call parent method before early return.
        MetricProducer::onActiveStateChangedLocked(eventTimeNs, isActive);
        return;
    }

    // Pull on active state changes.
    if (!eventLate) {
        if (isPulled()) {
            pullAndMatchEventsLocked(eventTimeNs);
        }

        onActiveStateChangedInternalLocked(eventTimeNs, isActive);
    }

    // Once any pulls are processed, call through to parent method which might flush the current
    // bucket.
    MetricProducer::onActiveStateChangedLocked(eventTimeNs, isActive);

    // Let condition timer know of new active state.
    mConditionTimer.onConditionChanged(isActive, eventTimeNs);

    updateCurrentSlicedBucketConditionTimers(isActive, eventTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onConditionChangedLocked(
        const bool condition, const int64_t eventTimeNs) {
    const ConditionState newCondition = condition ? ConditionState::kTrue : ConditionState::kFalse;
    const ConditionState oldCondition = mCondition;

    if (!mIsActive) {
        mCondition = newCondition;
        return;
    }

    // If the event arrived late, mark the bucket as invalid and skip the event.
    if (isEventLateLocked(eventTimeNs)) {
        VLOG("Skip event due to late arrival: %lld vs %lld", (long long)eventTimeNs,
             (long long)mCurrentBucketStartTimeNs);
        StatsdStats::getInstance().noteLateLogEventSkipped(mMetricId);
        StatsdStats::getInstance().noteConditionChangeInNextBucket(mMetricId);
        invalidateCurrentBucket(eventTimeNs, BucketDropReason::EVENT_IN_WRONG_BUCKET);
        mCondition = newCondition;
        mConditionTimer.onConditionChanged(newCondition, eventTimeNs);
        updateCurrentSlicedBucketConditionTimers(newCondition, eventTimeNs);
        return;
    }

    // If the previous condition was unknown, mark the bucket as invalid
    // because the bucket will contain partial data. For example, the condition
    // change might happen close to the end of the bucket and we might miss a
    // lot of data.
    // We still want to pull to set the base for diffed metrics.
    if (oldCondition == ConditionState::kUnknown) {
        invalidateCurrentBucket(eventTimeNs, BucketDropReason::CONDITION_UNKNOWN);
    }

    // Pull and match for the following condition change cases:
    // unknown/false -> true - condition changed
    // true -> false - condition changed
    // true -> true - old condition was true so we can flush the bucket at the
    // end if needed.
    //
    // We don’t need to pull for unknown -> false or false -> false.
    //
    // onConditionChangedLocked might happen on bucket boundaries if this is
    // called before #onDataPulled.
    if (isPulled() &&
        (newCondition == ConditionState::kTrue || oldCondition == ConditionState::kTrue)) {
        pullAndMatchEventsLocked(eventTimeNs);
    }

    onConditionChangedInternalLocked(oldCondition, newCondition, eventTimeNs);

    // Update condition state after pulling.
    mCondition = newCondition;

    flushIfNeededLocked(eventTimeNs);

    mConditionTimer.onConditionChanged(newCondition, eventTimeNs);
    updateCurrentSlicedBucketConditionTimers(newCondition, eventTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::updateCurrentSlicedBucketConditionTimers(
        bool newCondition, int64_t eventTimeNs) {
    if (mSlicedStateAtoms.empty()) {
        return;
    }

    // Utilize the current state key of each DimensionsInWhat key to determine
    // which condition timers to update.
    //
    // Assumes that the MetricDimensionKey exists in `mCurrentSlicedBucket`.
    for (const auto& [dimensionInWhatKey, dimensionInWhatInfo] : mDimInfos) {
        // If the new condition is true, turn ON the condition timer only if
        // the DimensionInWhat key was present in the data.
        mCurrentSlicedBucket[MetricDimensionKey(dimensionInWhatKey,
                                                dimensionInWhatInfo.currentState)]
                .conditionTimer.onConditionChanged(
                        newCondition && dimensionInWhatInfo.hasCurrentState, eventTimeNs);
    }
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::dumpStatesLocked(int out,
                                                                       bool verbose) const {
    if (mCurrentSlicedBucket.size() == 0) {
        return;
    }

    dprintf(out, "ValueMetricProducer %lld dimension size %lu\n", (long long)mMetricId,
            (unsigned long)mCurrentSlicedBucket.size());
    if (verbose) {
        for (const auto& [metricDimensionKey, currentBucket] : mCurrentSlicedBucket) {
            for (const Interval& interval : currentBucket.intervals) {
                dprintf(out, "\t(what)%s\t(states)%s  (aggregate)%s\n",
                        metricDimensionKey.getDimensionKeyInWhat().toString().c_str(),
                        metricDimensionKey.getStateValuesKey().toString().c_str(),
                        aggregatedValueToString(interval.aggregate).c_str());
            }
        }
    }
}

template <typename AggregatedValue, typename DimExtras>
bool ValueMetricProducer<AggregatedValue, DimExtras>::hasReachedGuardRailLimit() const {
    return mCurrentSlicedBucket.size() >= mDimensionHardLimit;
}

template <typename AggregatedValue, typename DimExtras>
bool ValueMetricProducer<AggregatedValue, DimExtras>::hitGuardRailLocked(
        const MetricDimensionKey& newKey) const {
    // ===========GuardRail==============
    // 1. Report the tuple count if the tuple count > soft limit
    if (mCurrentSlicedBucket.find(newKey) != mCurrentSlicedBucket.end()) {
        return false;
    }
    if (mCurrentSlicedBucket.size() > mDimensionSoftLimit - 1) {
        size_t newTupleCount = mCurrentSlicedBucket.size() + 1;
        StatsdStats::getInstance().noteMetricDimensionSize(mConfigKey, mMetricId, newTupleCount);
        // 2. Don't add more tuples, we are above the allowed threshold. Drop the data.
        if (hasReachedGuardRailLimit()) {
            if (!mHasHitGuardrail) {
                ALOGE("ValueMetricProducer %lld dropping data for dimension key %s",
                      (long long)mMetricId, newKey.toString().c_str());
                mHasHitGuardrail = true;
            }
            StatsdStats::getInstance().noteHardDimensionLimitReached(mMetricId);
            return true;
        }
    }

    return false;
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::onMatchedLogEventInternalLocked(
        const size_t matcherIndex, const MetricDimensionKey& eventKey,
        const ConditionKey& conditionKey, bool condition, const LogEvent& event,
        const map<int, HashableDimensionKey>& statePrimaryKeys) {
    // Skip this event if a state change occurred for a different primary key.
    auto it = statePrimaryKeys.find(mStateChangePrimaryKey.first);
    // Check that both the atom id and the primary key are equal.
    if (it != statePrimaryKeys.end() && it->second != mStateChangePrimaryKey.second) {
        VLOG("ValueMetric skip event with primary key %s because state change primary key "
             "is %s",
             it->second.toString().c_str(), mStateChangePrimaryKey.second.toString().c_str());
        return;
    }

    const int64_t eventTimeNs = event.GetElapsedTimestampNs();
    if (isEventLateLocked(eventTimeNs)) {
        VLOG("Skip event due to late arrival: %lld vs %lld", (long long)eventTimeNs,
             (long long)mCurrentBucketStartTimeNs);
        return;
    }

    const auto whatKey = eventKey.getDimensionKeyInWhat();
    mMatchedMetricDimensionKeys.insert(whatKey);

    if (!isPulled()) {
        // Only flushing for pushed because for pulled metrics, we need to do a pull first.
        flushIfNeededLocked(eventTimeNs);
    }

    if (canSkipLogEventLocked(eventKey, condition, eventTimeNs, statePrimaryKeys)) {
        return;
    }

    if (hitGuardRailLocked(eventKey)) {
        return;
    }

    const auto& returnVal = mDimInfos.emplace(whatKey, DimensionsInWhatInfo(getUnknownStateKey()));
    DimensionsInWhatInfo& dimensionsInWhatInfo = returnVal.first->second;
    const HashableDimensionKey& oldStateKey = dimensionsInWhatInfo.currentState;
    CurrentBucket& currentBucket = mCurrentSlicedBucket[MetricDimensionKey(whatKey, oldStateKey)];

    // Ensure we turn on the condition timer in the case where dimensions
    // were missing on a previous pull due to a state change.
    const auto stateKey = eventKey.getStateValuesKey();
    const bool stateChange = oldStateKey != stateKey || !dimensionsInWhatInfo.hasCurrentState;

    // We need to get the intervals stored with the previous state key so we can
    // close these value intervals.
    vector<Interval>& intervals = currentBucket.intervals;
    if (intervals.size() < mFieldMatchers.size()) {
        VLOG("Resizing number of intervals to %d", (int)mFieldMatchers.size());
        intervals.resize(mFieldMatchers.size());
    }

    dimensionsInWhatInfo.hasCurrentState = true;
    dimensionsInWhatInfo.currentState = stateKey;

    dimensionsInWhatInfo.seenNewData |= aggregateFields(eventTimeNs, eventKey, event, intervals,
                                                        dimensionsInWhatInfo.dimExtras);

    // State change.
    if (!mSlicedStateAtoms.empty() && stateChange) {
        // Turn OFF the condition timer for the previous state key.
        currentBucket.conditionTimer.onConditionChanged(false, eventTimeNs);

        // Turn ON the condition timer for the new state key.
        mCurrentSlicedBucket[MetricDimensionKey(whatKey, stateKey)]
                .conditionTimer.onConditionChanged(true, eventTimeNs);
    }
}

// For pulled metrics, we always need to make sure we do a pull before flushing the bucket
// if mCondition and mIsActive are true!
template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::flushIfNeededLocked(
        const int64_t eventTimeNs) {
    const int64_t currentBucketEndTimeNs = getCurrentBucketEndTimeNs();
    if (eventTimeNs < currentBucketEndTimeNs) {
        VLOG("eventTime is %lld, less than current bucket end time %lld", (long long)eventTimeNs,
             (long long)(currentBucketEndTimeNs));
        return;
    }
    int64_t numBucketsForward = calcBucketsForwardCount(eventTimeNs);
    int64_t nextBucketStartTimeNs =
            currentBucketEndTimeNs + (numBucketsForward - 1) * mBucketSizeNs;
    flushCurrentBucketLocked(eventTimeNs, nextBucketStartTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
int64_t ValueMetricProducer<AggregatedValue, DimExtras>::calcBucketsForwardCount(
        const int64_t eventTimeNs) const {
    int64_t currentBucketEndTimeNs = getCurrentBucketEndTimeNs();
    if (eventTimeNs < currentBucketEndTimeNs) {
        return 0;
    }
    return 1 + (eventTimeNs - currentBucketEndTimeNs) / mBucketSizeNs;
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::flushCurrentBucketLocked(
        const int64_t eventTimeNs, const int64_t nextBucketStartTimeNs) {
    if (mCondition == ConditionState::kUnknown) {
        StatsdStats::getInstance().noteBucketUnknownCondition(mMetricId);
        invalidateCurrentBucket(eventTimeNs, BucketDropReason::CONDITION_UNKNOWN);
    }

    VLOG("finalizing bucket for %ld, dumping %d slices", (long)mCurrentBucketStartTimeNs,
         (int)mCurrentSlicedBucket.size());

    closeCurrentBucket(eventTimeNs, nextBucketStartTimeNs);
    initNextSlicedBucket(nextBucketStartTimeNs);

    // Update the condition timer again, in case we skipped buckets.
    mConditionTimer.newBucketStart(eventTimeNs, nextBucketStartTimeNs);

    // NOTE: Update the condition timers in `mCurrentSlicedBucket` only when slicing
    // by state. Otherwise, the "global" condition timer will be used.
    if (!mSlicedStateAtoms.empty()) {
        for (auto& [metricDimensionKey, currentBucket] : mCurrentSlicedBucket) {
            currentBucket.conditionTimer.newBucketStart(eventTimeNs, nextBucketStartTimeNs);
        }
    }
    mCurrentBucketNum += calcBucketsForwardCount(eventTimeNs);
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::closeCurrentBucket(
        const int64_t eventTimeNs, const int64_t nextBucketStartTimeNs) {
    const int64_t fullBucketEndTimeNs = getCurrentBucketEndTimeNs();
    int64_t bucketEndTimeNs = fullBucketEndTimeNs;
    int64_t numBucketsForward = calcBucketsForwardCount(eventTimeNs);

    if (multipleBucketsSkipped(numBucketsForward)) {
        VLOG("Skipping forward %lld buckets", (long long)numBucketsForward);
        StatsdStats::getInstance().noteSkippedForwardBuckets(mMetricId);
        // Something went wrong. Maybe the device was sleeping for a long time. It is better
        // to mark the current bucket as invalid. The last pull might have been successful though.
        invalidateCurrentBucket(eventTimeNs, BucketDropReason::MULTIPLE_BUCKETS_SKIPPED);

        // End the bucket at the next bucket start time so the entire interval is skipped.
        bucketEndTimeNs = nextBucketStartTimeNs;
    } else if (eventTimeNs < fullBucketEndTimeNs) {
        bucketEndTimeNs = eventTimeNs;
    }

    // Close the current bucket
    const auto [globalConditionDurationNs, globalConditionCorrectionNs] =
            mConditionTimer.newBucketStart(eventTimeNs, bucketEndTimeNs);

    bool isBucketLargeEnough = bucketEndTimeNs - mCurrentBucketStartTimeNs >= mMinBucketSizeNs;
    if (!isBucketLargeEnough) {
        skipCurrentBucket(eventTimeNs, BucketDropReason::BUCKET_TOO_SMALL);
    }
    if (!mCurrentBucketIsSkipped) {
        bool bucketHasData = false;
        // The current bucket is large enough to keep.
        for (auto& [metricDimensionKey, currentBucket] : mCurrentSlicedBucket) {
            PastBucket<AggregatedValue> bucket =
                    buildPartialBucket(bucketEndTimeNs, currentBucket.intervals);
            if (bucket.aggIndex.empty()) {
                continue;
            }
            bucketHasData = true;
            if (!mSlicedStateAtoms.empty()) {
                const auto [conditionDurationNs, conditionCorrectionNs] =
                        currentBucket.conditionTimer.newBucketStart(eventTimeNs, bucketEndTimeNs);
                bucket.mConditionTrueNs = conditionDurationNs;
                bucket.mConditionCorrectionNs = conditionCorrectionNs;
            } else {
                bucket.mConditionTrueNs = globalConditionDurationNs;
                bucket.mConditionCorrectionNs = globalConditionCorrectionNs;
            }

            auto& bucketList = mPastBuckets[metricDimensionKey];
            bucketList.push_back(std::move(bucket));
        }
        if (!bucketHasData) {
            skipCurrentBucket(eventTimeNs, BucketDropReason::NO_DATA);
        }
    }

    if (mCurrentBucketIsSkipped) {
        mCurrentSkippedBucket.bucketStartTimeNs = mCurrentBucketStartTimeNs;
        mCurrentSkippedBucket.bucketEndTimeNs = bucketEndTimeNs;
        mSkippedBuckets.push_back(mCurrentSkippedBucket);
    }

    // This means that the current bucket was not flushed before a forced bucket split.
    // This can happen if an app update or a dump report with includeCurrentPartialBucket is
    // requested before we get a chance to flush the bucket due to receiving new data, either from
    // the statsd socket or the StatsPullerManager.
    if (bucketEndTimeNs < nextBucketStartTimeNs) {
        SkippedBucket bucketInGap;
        bucketInGap.bucketStartTimeNs = bucketEndTimeNs;
        bucketInGap.bucketEndTimeNs = nextBucketStartTimeNs;
        bucketInGap.dropEvents.emplace_back(buildDropEvent(eventTimeNs, BucketDropReason::NO_DATA));
        mSkippedBuckets.emplace_back(bucketInGap);
    }
}

template <typename AggregatedValue, typename DimExtras>
void ValueMetricProducer<AggregatedValue, DimExtras>::initNextSlicedBucket(
        int64_t nextBucketStartTimeNs) {
    StatsdStats::getInstance().noteBucketCount(mMetricId);
    if (mSlicedStateAtoms.empty()) {
        mCurrentSlicedBucket.clear();
    } else {
        for (auto it = mCurrentSlicedBucket.begin(); it != mCurrentSlicedBucket.end();) {
            bool obsolete = true;
            for (auto& interval : it->second.intervals) {
                interval.sampleSize = 0;
            }

            // When slicing by state, only delete the MetricDimensionKey when the
            // state key in the MetricDimensionKey is not the current state key.
            const HashableDimensionKey& dimensionInWhatKey = it->first.getDimensionKeyInWhat();
            const auto& currentDimInfoItr = mDimInfos.find(dimensionInWhatKey);

            if ((currentDimInfoItr != mDimInfos.end()) &&
                (it->first.getStateValuesKey() == currentDimInfoItr->second.currentState)) {
                obsolete = false;
            }
            if (obsolete) {
                it = mCurrentSlicedBucket.erase(it);
            } else {
                it++;
            }
        }
    }
    for (auto it = mDimInfos.begin(); it != mDimInfos.end();) {
        if (!it->second.seenNewData) {
            it = mDimInfos.erase(it);
        } else {
            it->second.seenNewData = false;
            it++;
        }
    }

    mCurrentBucketIsSkipped = false;
    mCurrentSkippedBucket.reset();

    mCurrentBucketStartTimeNs = nextBucketStartTimeNs;
    // Reset mHasHitGuardrail boolean since bucket was reset
    mHasHitGuardrail = false;
    VLOG("metric %lld: new bucket start time: %lld", (long long)mMetricId,
         (long long)mCurrentBucketStartTimeNs);
}

// Explicit template instantiations
template class ValueMetricProducer<Value, vector<optional<Value>>>;
template class ValueMetricProducer<unique_ptr<KllQuantile>, Empty>;

}  // namespace statsd
}  // namespace os
}  // namespace android
