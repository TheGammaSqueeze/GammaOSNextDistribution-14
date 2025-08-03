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

#include "metrics_manager_util.h"

#include <inttypes.h>

#include "FieldValue.h"
#include "condition/CombinationConditionTracker.h"
#include "condition/SimpleConditionTracker.h"
#include "external/StatsPullerManager.h"
#include "guardrail/StatsdStats.h"
#include "hash.h"
#include "matchers/CombinationAtomMatchingTracker.h"
#include "matchers/EventMatcherWizard.h"
#include "matchers/SimpleAtomMatchingTracker.h"
#include "metrics/CountMetricProducer.h"
#include "metrics/DurationMetricProducer.h"
#include "metrics/EventMetricProducer.h"
#include "metrics/GaugeMetricProducer.h"
#include "metrics/KllMetricProducer.h"
#include "metrics/MetricProducer.h"
#include "metrics/NumericValueMetricProducer.h"
#include "metrics/RestrictedEventMetricProducer.h"
#include "state/StateManager.h"
#include "stats_util.h"

using google::protobuf::MessageLite;
using std::set;
using std::unordered_map;
using std::vector;

namespace android {
namespace os {
namespace statsd {

namespace {

bool hasLeafNode(const FieldMatcher& matcher) {
    if (!matcher.has_field()) {
        return false;
    }
    for (int i = 0; i < matcher.child_size(); ++i) {
        if (hasLeafNode(matcher.child(i))) {
            return true;
        }
    }
    return true;
}

// DFS for ensuring there is no
// 1. value matching in the FieldValueMatcher tree with Position::ALL.
// 2. string replacement in the FieldValueMatcher tree without a value matcher with Position::ANY.
// Using vector to keep track of visited FieldValueMatchers since we expect number of
// FieldValueMatchers to be low.
optional<InvalidConfigReasonEnum> validateFvmPositionAllAndAny(
        const FieldValueMatcher& fvm, bool inPositionAll, bool inPositionAny,
        vector<FieldValueMatcher const*>& visited) {
    visited.push_back(&fvm);
    inPositionAll = inPositionAll || fvm.position() == Position::ALL;
    inPositionAny = inPositionAny || fvm.position() == Position::ANY;
    if (fvm.value_matcher_case() == FieldValueMatcher::kMatchesTuple) {
        for (const FieldValueMatcher& childFvm : fvm.matches_tuple().field_value_matcher()) {
            if (std::find(visited.cbegin(), visited.cend(), &childFvm) != visited.cend()) {
                continue;
            }
            const optional<InvalidConfigReasonEnum> reasonEnum =
                    validateFvmPositionAllAndAny(childFvm, inPositionAll, inPositionAny, visited);
            if (reasonEnum != nullopt) {
                return reasonEnum;
            }
        }
        return nullopt;
    }
    if (inPositionAll && fvm.value_matcher_case() != FieldValueMatcher::VALUE_MATCHER_NOT_SET) {
        // value_matcher is set to something other than matches_tuple with Position::ALL
        return INVALID_CONFIG_REASON_MATCHER_VALUE_MATCHER_WITH_POSITION_ALL;
    }
    if (inPositionAny && fvm.value_matcher_case() == FieldValueMatcher::VALUE_MATCHER_NOT_SET &&
        fvm.has_replace_string()) {
        // value_matcher is not set and there is a string replacement with Position::ANY
        return INVALID_CONFIG_REASON_MATCHER_STRING_REPLACE_WITH_NO_VALUE_MATCHER_WITH_POSITION_ANY;
    }
    return nullopt;
}

optional<InvalidConfigReason> validateSimpleAtomMatcher(int64_t matcherId,
                                                        const SimpleAtomMatcher& simpleMatcher) {
    for (const FieldValueMatcher& fvm : simpleMatcher.field_value_matcher()) {
        if (fvm.value_matcher_case() == FieldValueMatcher::VALUE_MATCHER_NOT_SET &&
            !fvm.has_replace_string()) {
            return createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_MATCHER_NO_VALUE_MATCHER_NOR_STRING_REPLACER, matcherId);
        } else if (fvm.has_replace_string() &&
                   !(fvm.value_matcher_case() == FieldValueMatcher::VALUE_MATCHER_NOT_SET ||
                     fvm.value_matcher_case() == FieldValueMatcher::kEqString ||
                     fvm.value_matcher_case() == FieldValueMatcher::kEqAnyString ||
                     fvm.value_matcher_case() == FieldValueMatcher::kNeqAnyString ||
                     fvm.value_matcher_case() == FieldValueMatcher::kEqWildcardString ||
                     fvm.value_matcher_case() == FieldValueMatcher::kEqAnyWildcardString ||
                     fvm.value_matcher_case() == FieldValueMatcher::kNeqAnyWildcardString)) {
            return createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_MATCHER_INVALID_VALUE_MATCHER_WITH_STRING_REPLACE,
                    matcherId);
        }
        vector<FieldValueMatcher const*> visited;
        const optional<InvalidConfigReasonEnum> reasonEnum = validateFvmPositionAllAndAny(
                fvm, false /* inPositionAll */, false /* inPositionAny */, visited);
        if (reasonEnum != nullopt) {
            return createInvalidConfigReasonWithMatcher(*reasonEnum, matcherId);
        }
    }
    return nullopt;
}

}  // namespace

sp<AtomMatchingTracker> createAtomMatchingTracker(
        const AtomMatcher& logMatcher, const sp<UidMap>& uidMap,
        optional<InvalidConfigReason>& invalidConfigReason) {
    string serializedMatcher;
    if (!logMatcher.SerializeToString(&serializedMatcher)) {
        ALOGE("Unable to serialize matcher %lld", (long long)logMatcher.id());
        invalidConfigReason = createInvalidConfigReasonWithMatcher(
                INVALID_CONFIG_REASON_MATCHER_SERIALIZATION_FAILED, logMatcher.id());
        return nullptr;
    }
    uint64_t protoHash = Hash64(serializedMatcher);
    switch (logMatcher.contents_case()) {
        case AtomMatcher::ContentsCase::kSimpleAtomMatcher: {
            invalidConfigReason =
                    validateSimpleAtomMatcher(logMatcher.id(), logMatcher.simple_atom_matcher());
            if (invalidConfigReason != nullopt) {
                ALOGE("Matcher \"%lld\" malformed", (long long)logMatcher.id());
                return nullptr;
            }
            sp<AtomMatchingTracker> simpleAtomMatcher = new SimpleAtomMatchingTracker(
                    logMatcher.id(), protoHash, logMatcher.simple_atom_matcher(), uidMap);
            return simpleAtomMatcher;
        }
        case AtomMatcher::ContentsCase::kCombination:
            return new CombinationAtomMatchingTracker(logMatcher.id(), protoHash);
        default:
            ALOGE("Matcher \"%lld\" malformed", (long long)logMatcher.id());
            invalidConfigReason = createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_MATCHER_MALFORMED_CONTENTS_CASE, logMatcher.id());
            return nullptr;
    }
}

sp<ConditionTracker> createConditionTracker(
        const ConfigKey& key, const Predicate& predicate, const int index,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        optional<InvalidConfigReason>& invalidConfigReason) {
    string serializedPredicate;
    if (!predicate.SerializeToString(&serializedPredicate)) {
        ALOGE("Unable to serialize predicate %lld", (long long)predicate.id());
        invalidConfigReason = createInvalidConfigReasonWithPredicate(
                INVALID_CONFIG_REASON_CONDITION_SERIALIZATION_FAILED, predicate.id());
        return nullptr;
    }
    uint64_t protoHash = Hash64(serializedPredicate);
    switch (predicate.contents_case()) {
        case Predicate::ContentsCase::kSimplePredicate: {
            return new SimpleConditionTracker(key, predicate.id(), protoHash, index,
                                              predicate.simple_predicate(), atomMatchingTrackerMap);
        }
        case Predicate::ContentsCase::kCombination: {
            return new CombinationConditionTracker(predicate.id(), index, protoHash);
        }
        default:
            ALOGE("Predicate \"%lld\" malformed", (long long)predicate.id());
            invalidConfigReason = createInvalidConfigReasonWithPredicate(
                    INVALID_CONFIG_REASON_CONDITION_MALFORMED_CONTENTS_CASE, predicate.id());
            return nullptr;
    }
}

optional<InvalidConfigReason> getMetricProtoHash(
        const StatsdConfig& config, const MessageLite& metric, const int64_t id,
        const unordered_map<int64_t, int>& metricToActivationMap, uint64_t& metricHash) {
    string serializedMetric;
    if (!metric.SerializeToString(&serializedMetric)) {
        ALOGE("Unable to serialize metric %lld", (long long)id);
        return InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SERIALIZATION_FAILED, id);
    }
    metricHash = Hash64(serializedMetric);

    // Combine with activation hash, if applicable
    const auto& metricActivationIt = metricToActivationMap.find(id);
    if (metricActivationIt != metricToActivationMap.end()) {
        string serializedActivation;
        const MetricActivation& activation = config.metric_activation(metricActivationIt->second);
        if (!activation.SerializeToString(&serializedActivation)) {
            ALOGE("Unable to serialize metric activation for metric %lld", (long long)id);
            return InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_ACTIVATION_SERIALIZATION_FAILED,
                                       id);
        }
        metricHash = Hash64(to_string(metricHash).append(to_string(Hash64(serializedActivation))));
    }
    return nullopt;
}

optional<InvalidConfigReason> handleMetricWithAtomMatchingTrackers(
        const int64_t matcherId, const int64_t metricId, const int metricIndex,
        const bool enforceOneAtom, const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        unordered_map<int, vector<int>>& trackerToMetricMap, int& logTrackerIndex) {
    auto logTrackerIt = atomMatchingTrackerMap.find(matcherId);
    if (logTrackerIt == atomMatchingTrackerMap.end()) {
        ALOGW("cannot find the AtomMatcher \"%lld\" in config", (long long)matcherId);
        return createInvalidConfigReasonWithMatcher(INVALID_CONFIG_REASON_METRIC_MATCHER_NOT_FOUND,
                                                    metricId, matcherId);
    }
    if (enforceOneAtom && allAtomMatchingTrackers[logTrackerIt->second]->getAtomIds().size() > 1) {
        ALOGE("AtomMatcher \"%lld\" has more than one tag ids. When a metric has dimension, "
              "the \"what\" can only be about one atom type. trigger_event matchers can also only "
              "be about one atom type.",
              (long long)matcherId);
        return createInvalidConfigReasonWithMatcher(
                INVALID_CONFIG_REASON_METRIC_MATCHER_MORE_THAN_ONE_ATOM, metricId, matcherId);
    }
    logTrackerIndex = logTrackerIt->second;
    auto& metric_list = trackerToMetricMap[logTrackerIndex];
    metric_list.push_back(metricIndex);
    return nullopt;
}

optional<InvalidConfigReason> handleMetricWithConditions(
        const int64_t condition, const int64_t metricId, const int metricIndex,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const ::google::protobuf::RepeatedPtrField<MetricConditionLink>& links,
        const vector<sp<ConditionTracker>>& allConditionTrackers, int& conditionIndex,
        unordered_map<int, vector<int>>& conditionToMetricMap) {
    auto condition_it = conditionTrackerMap.find(condition);
    if (condition_it == conditionTrackerMap.end()) {
        ALOGW("cannot find Predicate \"%lld\" in the config", (long long)condition);
        return createInvalidConfigReasonWithPredicate(
                INVALID_CONFIG_REASON_METRIC_CONDITION_NOT_FOUND, metricId, condition);
    }
    for (const auto& link : links) {
        auto it = conditionTrackerMap.find(link.condition());
        if (it == conditionTrackerMap.end()) {
            ALOGW("cannot find Predicate \"%lld\" in the config", (long long)link.condition());
            return createInvalidConfigReasonWithPredicate(
                    INVALID_CONFIG_REASON_METRIC_CONDITION_LINK_NOT_FOUND, metricId,
                    link.condition());
        }
    }
    conditionIndex = condition_it->second;

    // will create new vector if not exist before.
    auto& metricList = conditionToMetricMap[condition_it->second];
    metricList.push_back(metricIndex);
    return nullopt;
}

// Initializes state data structures for a metric.
// input:
// [config]: the input config
// [stateIds]: the slice_by_state ids for this metric
// [stateAtomIdMap]: this map contains the mapping from all state ids to atom ids
// [allStateGroupMaps]: this map contains the mapping from state ids and state
//                      values to state group ids for all states
// output:
// [slicedStateAtoms]: a vector of atom ids of all the slice_by_states
// [stateGroupMap]: this map should contain the mapping from states ids and state
//                      values to state group ids for all states that this metric
//                      is interested in
optional<InvalidConfigReason> handleMetricWithStates(
        const StatsdConfig& config, const int64_t metricId,
        const ::google::protobuf::RepeatedField<int64_t>& stateIds,
        const unordered_map<int64_t, int>& stateAtomIdMap,
        const unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        vector<int>& slicedStateAtoms,
        unordered_map<int, unordered_map<int, int64_t>>& stateGroupMap) {
    for (const auto& stateId : stateIds) {
        auto it = stateAtomIdMap.find(stateId);
        if (it == stateAtomIdMap.end()) {
            ALOGW("cannot find State %" PRId64 " in the config", stateId);
            return createInvalidConfigReasonWithState(INVALID_CONFIG_REASON_METRIC_STATE_NOT_FOUND,
                                                      metricId, stateId);
        }
        int atomId = it->second;
        slicedStateAtoms.push_back(atomId);

        auto stateIt = allStateGroupMaps.find(stateId);
        if (stateIt != allStateGroupMaps.end()) {
            stateGroupMap[atomId] = stateIt->second;
        }
    }
    return nullopt;
}

optional<InvalidConfigReason> handleMetricWithStateLink(const int64_t metricId,
                                                        const FieldMatcher& stateMatcher,
                                                        const vector<Matcher>& dimensionsInWhat) {
    vector<Matcher> stateMatchers;
    translateFieldMatcher(stateMatcher, &stateMatchers);
    if (!subsetDimensions(stateMatchers, dimensionsInWhat)) {
        return InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_STATELINKS_NOT_SUBSET_DIM_IN_WHAT,
                                   metricId);
    }
    return nullopt;
}

optional<InvalidConfigReason> handleMetricWithDimensionalSampling(
        const int64_t metricId, const DimensionalSamplingInfo& dimSamplingInfo,
        const vector<Matcher>& dimensionsInWhat, SamplingInfo& samplingInfo) {
    if (!dimSamplingInfo.has_sampled_what_field()) {
        ALOGE("metric DimensionalSamplingInfo missing sampledWhatField");
        return InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_DIMENSIONAL_SAMPLING_INFO_MISSING_SAMPLED_FIELD,
                metricId);
    }

    if (dimSamplingInfo.shard_count() <= 1) {
        ALOGE("metric shardCount must be > 1");
        return InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_DIMENSIONAL_SAMPLING_INFO_INCORRECT_SHARD_COUNT,
                metricId);
    }
    samplingInfo.shardCount = dimSamplingInfo.shard_count();

    if (HasPositionALL(dimSamplingInfo.sampled_what_field()) ||
        HasPositionANY(dimSamplingInfo.sampled_what_field())) {
        ALOGE("metric has repeated field with position ALL or ANY as the sampled dimension");
        return InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELD_INCORRECT_SIZE,
                                   metricId);
    }

    translateFieldMatcher(dimSamplingInfo.sampled_what_field(), &samplingInfo.sampledWhatFields);
    if (samplingInfo.sampledWhatFields.size() != 1) {
        ALOGE("metric has incorrect number of sampled dimension fields");
        return InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELD_INCORRECT_SIZE,
                                   metricId);
    }
    if (!subsetDimensions(samplingInfo.sampledWhatFields, dimensionsInWhat)) {
        return InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELDS_NOT_SUBSET_DIM_IN_WHAT, metricId);
    }
    return nullopt;
}

// Validates a metricActivation and populates state.
// EventActivationMap and EventDeactivationMap are supplied to a MetricProducer
//      to provide the producer with state about its activators and deactivators.
// Returns false if there are errors.
optional<InvalidConfigReason> handleMetricActivation(
        const StatsdConfig& config, const int64_t metricId, const int metricIndex,
        const unordered_map<int64_t, int>& metricToActivationMap,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation,
        unordered_map<int, shared_ptr<Activation>>& eventActivationMap,
        unordered_map<int, vector<shared_ptr<Activation>>>& eventDeactivationMap) {
    // Check if metric has an associated activation
    auto itr = metricToActivationMap.find(metricId);
    if (itr == metricToActivationMap.end()) {
        return nullopt;
    }

    int activationIndex = itr->second;
    const MetricActivation& metricActivation = config.metric_activation(activationIndex);

    for (int i = 0; i < metricActivation.event_activation_size(); i++) {
        const EventActivation& activation = metricActivation.event_activation(i);

        auto itr = atomMatchingTrackerMap.find(activation.atom_matcher_id());
        if (itr == atomMatchingTrackerMap.end()) {
            ALOGE("Atom matcher not found for event activation.");
            return createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_METRIC_ACTIVATION_MATCHER_NOT_FOUND, metricId,
                    activation.atom_matcher_id());
        }

        ActivationType activationType = (activation.has_activation_type())
                                                ? activation.activation_type()
                                                : metricActivation.activation_type();
        std::shared_ptr<Activation> activationWrapper =
                std::make_shared<Activation>(activationType, activation.ttl_seconds() * NS_PER_SEC);

        int atomMatcherIndex = itr->second;
        activationAtomTrackerToMetricMap[atomMatcherIndex].push_back(metricIndex);
        eventActivationMap.emplace(atomMatcherIndex, activationWrapper);

        if (activation.has_deactivation_atom_matcher_id()) {
            itr = atomMatchingTrackerMap.find(activation.deactivation_atom_matcher_id());
            if (itr == atomMatchingTrackerMap.end()) {
                ALOGE("Atom matcher not found for event deactivation.");
                return createInvalidConfigReasonWithMatcher(
                        INVALID_CONFIG_REASON_METRIC_DEACTIVATION_MATCHER_NOT_FOUND, metricId,
                        activation.deactivation_atom_matcher_id());
            }
            int deactivationAtomMatcherIndex = itr->second;
            deactivationAtomTrackerToMetricMap[deactivationAtomMatcherIndex].push_back(metricIndex);
            eventDeactivationMap[deactivationAtomMatcherIndex].push_back(activationWrapper);
        }
    }

    metricsWithActivation.push_back(metricIndex);
    return nullopt;
}

// Validates a metricActivation and populates state.
// Fills the new event activation/deactivation maps, preserving the existing activations
// Returns false if there are errors.
optional<InvalidConfigReason> handleMetricActivationOnConfigUpdate(
        const StatsdConfig& config, const int64_t metricId, const int metricIndex,
        const unordered_map<int64_t, int>& metricToActivationMap,
        const unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
        const unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
        const unordered_map<int, shared_ptr<Activation>>& oldEventActivationMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation,
        unordered_map<int, shared_ptr<Activation>>& newEventActivationMap,
        unordered_map<int, vector<shared_ptr<Activation>>>& newEventDeactivationMap) {
    // Check if metric has an associated activation.
    const auto& itr = metricToActivationMap.find(metricId);
    if (itr == metricToActivationMap.end()) {
        return nullopt;
    }

    int activationIndex = itr->second;
    const MetricActivation& metricActivation = config.metric_activation(activationIndex);

    for (int i = 0; i < metricActivation.event_activation_size(); i++) {
        const int64_t activationMatcherId = metricActivation.event_activation(i).atom_matcher_id();

        const auto& newActivationIt = newAtomMatchingTrackerMap.find(activationMatcherId);
        if (newActivationIt == newAtomMatchingTrackerMap.end()) {
            ALOGE("Atom matcher not found in new config for event activation.");
            return createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_METRIC_ACTIVATION_MATCHER_NOT_FOUND_NEW, metricId,
                    activationMatcherId);
        }
        int newActivationMatcherIndex = newActivationIt->second;

        // Find the old activation struct and copy it over.
        const auto& oldActivationIt = oldAtomMatchingTrackerMap.find(activationMatcherId);
        if (oldActivationIt == oldAtomMatchingTrackerMap.end()) {
            ALOGE("Atom matcher not found in existing config for event activation.");
            return createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_METRIC_ACTIVATION_MATCHER_NOT_FOUND_EXISTING, metricId,
                    activationMatcherId);
        }
        int oldActivationMatcherIndex = oldActivationIt->second;
        const auto& oldEventActivationIt = oldEventActivationMap.find(oldActivationMatcherIndex);
        if (oldEventActivationIt == oldEventActivationMap.end()) {
            ALOGE("Could not find existing event activation to update");
            return createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_METRIC_ACTIVATION_NOT_FOUND_EXISTING, metricId,
                    activationMatcherId);
        }
        newEventActivationMap.emplace(newActivationMatcherIndex, oldEventActivationIt->second);
        activationAtomTrackerToMetricMap[newActivationMatcherIndex].push_back(metricIndex);

        if (metricActivation.event_activation(i).has_deactivation_atom_matcher_id()) {
            const int64_t deactivationMatcherId =
                    metricActivation.event_activation(i).deactivation_atom_matcher_id();
            const auto& newDeactivationIt = newAtomMatchingTrackerMap.find(deactivationMatcherId);
            if (newDeactivationIt == newAtomMatchingTrackerMap.end()) {
                ALOGE("Deactivation atom matcher not found in new config for event activation.");
                return createInvalidConfigReasonWithMatcher(
                        INVALID_CONFIG_REASON_METRIC_DEACTIVATION_MATCHER_NOT_FOUND_NEW, metricId,
                        deactivationMatcherId);
            }
            int newDeactivationMatcherIndex = newDeactivationIt->second;
            newEventDeactivationMap[newDeactivationMatcherIndex].push_back(
                    oldEventActivationIt->second);
            deactivationAtomTrackerToMetricMap[newDeactivationMatcherIndex].push_back(metricIndex);
        }
    }

    metricsWithActivation.push_back(metricIndex);
    return nullopt;
}

optional<sp<MetricProducer>> createCountMetricProducerAndUpdateMetadata(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseNs,
        const int64_t currentTimeNs, const CountMetric& metric, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const unordered_map<int64_t, int>& stateAtomIdMap,
        const unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation, optional<InvalidConfigReason>& invalidConfigReason) {
    if (!metric.has_id() || !metric.has_what()) {
        ALOGE("cannot find metric id or \"what\" in CountMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metric.id());
        return nullopt;
    }
    int trackerIndex;
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            metric.what(), metric.id(), metricIndex, metric.has_dimensions_in_what(),
            allAtomMatchingTrackers, atomMatchingTrackerMap, trackerToMetricMap, trackerIndex);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    int conditionIndex = -1;
    if (metric.has_condition()) {
        invalidConfigReason = handleMetricWithConditions(
                metric.condition(), metric.id(), metricIndex, conditionTrackerMap, metric.links(),
                allConditionTrackers, conditionIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else {
        if (metric.links_size() > 0) {
            ALOGW("metrics has a MetricConditionLink but doesn't have a condition");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION, metric.id());
            return nullopt;
        }
    }

    std::vector<int> slicedStateAtoms;
    unordered_map<int, unordered_map<int, int64_t>> stateGroupMap;
    if (metric.slice_by_state_size() > 0) {
        invalidConfigReason =
                handleMetricWithStates(config, metric.id(), metric.slice_by_state(), stateAtomIdMap,
                                       allStateGroupMaps, slicedStateAtoms, stateGroupMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else {
        if (metric.state_link_size() > 0) {
            ALOGW("CountMetric has a MetricStateLink but doesn't have a slice_by_state");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_METRIC_STATELINK_NO_STATE, metric.id());
            return nullopt;
        }
    }

    // Check that all metric state links are a subset of dimensions_in_what fields.
    std::vector<Matcher> dimensionsInWhat;
    translateFieldMatcher(metric.dimensions_in_what(), &dimensionsInWhat);
    for (const auto& stateLink : metric.state_link()) {
        invalidConfigReason = handleMetricWithStateLink(metric.id(), stateLink.fields_in_what(),
                                                        dimensionsInWhat);
        if (invalidConfigReason.has_value()) {
            ALOGW("CountMetric's MetricStateLinks must be a subset of dimensions in what");
            return nullopt;
        }
    }

    unordered_map<int, shared_ptr<Activation>> eventActivationMap;
    unordered_map<int, vector<shared_ptr<Activation>>> eventDeactivationMap;
    invalidConfigReason = handleMetricActivation(
            config, metric.id(), metricIndex, metricToActivationMap, atomMatchingTrackerMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation, eventActivationMap, eventDeactivationMap);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    uint64_t metricHash;
    invalidConfigReason =
            getMetricProtoHash(config, metric, metric.id(), metricToActivationMap, metricHash);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    if (metric.has_threshold() &&
        (metric.threshold().value_comparison_case() == UploadThreshold::kLtFloat ||
         metric.threshold().value_comparison_case() == UploadThreshold::kGtFloat)) {
        ALOGW("Count metric incorrect upload threshold type or no type used");
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_BAD_THRESHOLD, metric.id());
        return nullopt;
    }

    sp<MetricProducer> metricProducer =
            new CountMetricProducer(key, metric, conditionIndex, initialConditionCache, wizard,
                                    metricHash, timeBaseNs, currentTimeNs, eventActivationMap,
                                    eventDeactivationMap, slicedStateAtoms, stateGroupMap);

    SamplingInfo samplingInfo;
    if (metric.has_dimensional_sampling_info()) {
        invalidConfigReason = handleMetricWithDimensionalSampling(
                metric.id(), metric.dimensional_sampling_info(), dimensionsInWhat, samplingInfo);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
        metricProducer->setSamplingInfo(samplingInfo);
    }

    return metricProducer;
}

optional<sp<MetricProducer>> createDurationMetricProducerAndUpdateMetadata(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseNs,
        const int64_t currentTimeNs, const DurationMetric& metric, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const unordered_map<int64_t, int>& stateAtomIdMap,
        const unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation, optional<InvalidConfigReason>& invalidConfigReason) {
    if (!metric.has_id() || !metric.has_what()) {
        ALOGE("cannot find metric id or \"what\" in DurationMetric \"%lld\"",
              (long long)metric.id());
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metric.id());
        return nullopt;
    }
    const auto& what_it = conditionTrackerMap.find(metric.what());
    if (what_it == conditionTrackerMap.end()) {
        ALOGE("DurationMetric's \"what\" is not present in the condition trackers");
        invalidConfigReason = createInvalidConfigReasonWithPredicate(
                INVALID_CONFIG_REASON_DURATION_METRIC_WHAT_NOT_FOUND, metric.id(), metric.what());
        return nullopt;
    }

    const int whatIndex = what_it->second;
    const Predicate& durationWhat = config.predicate(whatIndex);
    if (durationWhat.contents_case() != Predicate::ContentsCase::kSimplePredicate) {
        ALOGE("DurationMetric's \"what\" must be a simple condition");
        invalidConfigReason = createInvalidConfigReasonWithPredicate(
                INVALID_CONFIG_REASON_DURATION_METRIC_WHAT_NOT_SIMPLE, metric.id(), metric.what());
        return nullopt;
    }

    const SimplePredicate& simplePredicate = durationWhat.simple_predicate();
    bool nesting = simplePredicate.count_nesting();

    int startIndex = -1, stopIndex = -1, stopAllIndex = -1;
    if (!simplePredicate.has_start()) {
        ALOGE("Duration metrics must specify a valid start event matcher");
        invalidConfigReason = createInvalidConfigReasonWithPredicate(
                INVALID_CONFIG_REASON_DURATION_METRIC_MISSING_START, metric.id(), metric.what());
        return nullopt;
    }
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            simplePredicate.start(), metric.id(), metricIndex, metric.has_dimensions_in_what(),
            allAtomMatchingTrackers, atomMatchingTrackerMap, trackerToMetricMap, startIndex);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    if (simplePredicate.has_stop()) {
        invalidConfigReason = handleMetricWithAtomMatchingTrackers(
                simplePredicate.stop(), metric.id(), metricIndex, metric.has_dimensions_in_what(),
                allAtomMatchingTrackers, atomMatchingTrackerMap, trackerToMetricMap, stopIndex);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    }

    if (simplePredicate.has_stop_all()) {
        invalidConfigReason = handleMetricWithAtomMatchingTrackers(
                simplePredicate.stop_all(), metric.id(), metricIndex,
                metric.has_dimensions_in_what(), allAtomMatchingTrackers, atomMatchingTrackerMap,
                trackerToMetricMap, stopAllIndex);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    }

    const FieldMatcher& internalDimensions = simplePredicate.dimensions();

    int conditionIndex = -1;
    if (metric.has_condition()) {
        invalidConfigReason = handleMetricWithConditions(
                metric.condition(), metric.id(), metricIndex, conditionTrackerMap, metric.links(),
                allConditionTrackers, conditionIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else if (metric.links_size() > 0) {
        ALOGW("metrics has a MetricConditionLink but doesn't have a condition");
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION, metric.id());
        return nullopt;
    }

    std::vector<int> slicedStateAtoms;
    unordered_map<int, unordered_map<int, int64_t>> stateGroupMap;
    if (metric.slice_by_state_size() > 0) {
        if (metric.aggregation_type() == DurationMetric::MAX_SPARSE) {
            ALOGE("DurationMetric with aggregation type MAX_SPARSE cannot be sliced by state");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_DURATION_METRIC_MAX_SPARSE_HAS_SLICE_BY_STATE,
                    metric.id());
            return nullopt;
        }
        invalidConfigReason =
                handleMetricWithStates(config, metric.id(), metric.slice_by_state(), stateAtomIdMap,
                                       allStateGroupMaps, slicedStateAtoms, stateGroupMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else if (metric.state_link_size() > 0) {
        ALOGW("DurationMetric has a MetricStateLink but doesn't have a sliced state");
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_STATELINK_NO_STATE, metric.id());
        return nullopt;
    }

    // Check that all metric state links are a subset of dimensions_in_what fields.
    std::vector<Matcher> dimensionsInWhat;
    translateFieldMatcher(metric.dimensions_in_what(), &dimensionsInWhat);
    for (const auto& stateLink : metric.state_link()) {
        invalidConfigReason = handleMetricWithStateLink(metric.id(), stateLink.fields_in_what(),
                                                        dimensionsInWhat);
        if (invalidConfigReason.has_value()) {
            ALOGW("DurationMetric's MetricStateLinks must be a subset of dimensions in what");
            return nullopt;
        }
    }

    unordered_map<int, shared_ptr<Activation>> eventActivationMap;
    unordered_map<int, vector<shared_ptr<Activation>>> eventDeactivationMap;
    invalidConfigReason = handleMetricActivation(
            config, metric.id(), metricIndex, metricToActivationMap, atomMatchingTrackerMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation, eventActivationMap, eventDeactivationMap);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    uint64_t metricHash;
    invalidConfigReason =
            getMetricProtoHash(config, metric, metric.id(), metricToActivationMap, metricHash);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    if (metric.has_threshold()) {
        switch (metric.threshold().value_comparison_case()) {
            case UploadThreshold::kLtInt:
            case UploadThreshold::kGtInt:
            case UploadThreshold::kLteInt:
            case UploadThreshold::kGteInt:
                break;
            default:
                ALOGE("Duration metric incorrect upload threshold type or no type used");
                invalidConfigReason = InvalidConfigReason(
                        INVALID_CONFIG_REASON_METRIC_BAD_THRESHOLD, metric.id());
                return nullopt;
        }
    }

    sp<MetricProducer> metricProducer = new DurationMetricProducer(
            key, metric, conditionIndex, initialConditionCache, whatIndex, startIndex, stopIndex,
            stopAllIndex, nesting, wizard, metricHash, internalDimensions, timeBaseNs,
            currentTimeNs, eventActivationMap, eventDeactivationMap, slicedStateAtoms,
            stateGroupMap);
    if (!metricProducer->isValid()) {
        // TODO: Remove once invalidConfigReason is added to the DurationMetricProducer constructor
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_DURATION_METRIC_PRODUCER_INVALID, metric.id());
        return nullopt;
    }

    SamplingInfo samplingInfo;
    if (metric.has_dimensional_sampling_info()) {
        invalidConfigReason = handleMetricWithDimensionalSampling(
                metric.id(), metric.dimensional_sampling_info(), dimensionsInWhat, samplingInfo);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
        metricProducer->setSamplingInfo(samplingInfo);
    }

    return metricProducer;
}

optional<sp<MetricProducer>> createEventMetricProducerAndUpdateMetadata(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseNs,
        const EventMetric& metric, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation, optional<InvalidConfigReason>& invalidConfigReason) {
    if (!metric.has_id() || !metric.has_what()) {
        ALOGE("cannot find the metric name or what in config");
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metric.id());
        return nullopt;
    }
    int trackerIndex;
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            metric.what(), metric.id(), metricIndex, false, allAtomMatchingTrackers,
            atomMatchingTrackerMap, trackerToMetricMap, trackerIndex);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    int conditionIndex = -1;
    if (metric.has_condition()) {
        invalidConfigReason = handleMetricWithConditions(
                metric.condition(), metric.id(), metricIndex, conditionTrackerMap, metric.links(),
                allConditionTrackers, conditionIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else {
        if (metric.links_size() > 0) {
            ALOGW("metrics has a MetricConditionLink but doesn't have a condition");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION, metric.id());
            return nullopt;
        }
    }

    if (metric.sampling_percentage() < 1 || metric.sampling_percentage() > 100) {
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_INCORRECT_SAMPLING_PERCENTAGE, metric.id());
        return nullopt;
    }

    unordered_map<int, shared_ptr<Activation>> eventActivationMap;
    unordered_map<int, vector<shared_ptr<Activation>>> eventDeactivationMap;
    invalidConfigReason = handleMetricActivation(
            config, metric.id(), metricIndex, metricToActivationMap, atomMatchingTrackerMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation, eventActivationMap, eventDeactivationMap);
    if (invalidConfigReason.has_value()) return nullptr;

    uint64_t metricHash;
    invalidConfigReason =
            getMetricProtoHash(config, metric, metric.id(), metricToActivationMap, metricHash);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    if (config.has_restricted_metrics_delegate_package_name()) {
        return {new RestrictedEventMetricProducer(
                key, metric, conditionIndex, initialConditionCache, wizard, metricHash, timeBaseNs,
                eventActivationMap, eventDeactivationMap)};
    }
    return {new EventMetricProducer(key, metric, conditionIndex, initialConditionCache, wizard,
                                    metricHash, timeBaseNs, eventActivationMap,
                                    eventDeactivationMap)};
}

optional<sp<MetricProducer>> createNumericValueMetricProducerAndUpdateMetadata(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseNs,
        const int64_t currentTimeNs, const sp<StatsPullerManager>& pullerManager,
        const ValueMetric& metric, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const sp<EventMatcherWizard>& matcherWizard,
        const unordered_map<int64_t, int>& stateAtomIdMap,
        const unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation, optional<InvalidConfigReason>& invalidConfigReason) {
    if (!metric.has_id() || !metric.has_what()) {
        ALOGE("cannot find metric id or \"what\" in ValueMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metric.id());
        return nullopt;
    }
    if (!metric.has_value_field()) {
        ALOGE("cannot find \"value_field\" in ValueMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_VALUE_METRIC_MISSING_VALUE_FIELD, metric.id());
        return nullopt;
    }
    if (HasPositionALL(metric.value_field())) {
        ALOGE("value field with position ALL is not supported. ValueMetric \"%lld\"",
              (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_VALUE_METRIC_VALUE_FIELD_HAS_POSITION_ALL, metric.id());
        return nullopt;
    }
    std::vector<Matcher> fieldMatchers;
    translateFieldMatcher(metric.value_field(), &fieldMatchers);
    if (fieldMatchers.size() < 1) {
        ALOGE("incorrect \"value_field\" in ValueMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_VALUE_METRIC_HAS_INCORRECT_VALUE_FIELD, metric.id());
        return nullopt;
    }

    int trackerIndex;
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            metric.what(), metric.id(), metricIndex,
            /*enforceOneAtom=*/true, allAtomMatchingTrackers, atomMatchingTrackerMap,
            trackerToMetricMap, trackerIndex);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    const sp<AtomMatchingTracker>& atomMatcher = allAtomMatchingTrackers.at(trackerIndex);
    int atomTagId = *(atomMatcher->getAtomIds().begin());
    int pullTagId = pullerManager->PullerForMatcherExists(atomTagId) ? atomTagId : -1;

    int conditionIndex = -1;
    if (metric.has_condition()) {
        invalidConfigReason = handleMetricWithConditions(
                metric.condition(), metric.id(), metricIndex, conditionTrackerMap, metric.links(),
                allConditionTrackers, conditionIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else if (metric.links_size() > 0) {
        ALOGE("metrics has a MetricConditionLink but doesn't have a condition");
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION, metric.id());
        return nullopt;
    }

    std::vector<int> slicedStateAtoms;
    unordered_map<int, unordered_map<int, int64_t>> stateGroupMap;
    if (metric.slice_by_state_size() > 0) {
        invalidConfigReason =
                handleMetricWithStates(config, metric.id(), metric.slice_by_state(), stateAtomIdMap,
                                       allStateGroupMaps, slicedStateAtoms, stateGroupMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else if (metric.state_link_size() > 0) {
        ALOGE("ValueMetric has a MetricStateLink but doesn't have a sliced state");
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_STATELINK_NO_STATE, metric.id());
        return nullopt;
    }

    // Check that all metric state links are a subset of dimensions_in_what fields.
    std::vector<Matcher> dimensionsInWhat;
    translateFieldMatcher(metric.dimensions_in_what(), &dimensionsInWhat);
    for (const auto& stateLink : metric.state_link()) {
        invalidConfigReason = handleMetricWithStateLink(metric.id(), stateLink.fields_in_what(),
                                                        dimensionsInWhat);
        if (invalidConfigReason.has_value()) {
            ALOGW("ValueMetric's MetricStateLinks must be a subset of the dimensions in what");
            return nullopt;
        }
    }

    unordered_map<int, shared_ptr<Activation>> eventActivationMap;
    unordered_map<int, vector<shared_ptr<Activation>>> eventDeactivationMap;
    invalidConfigReason = handleMetricActivation(
            config, metric.id(), metricIndex, metricToActivationMap, atomMatchingTrackerMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation, eventActivationMap, eventDeactivationMap);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    uint64_t metricHash;
    invalidConfigReason =
            getMetricProtoHash(config, metric, metric.id(), metricToActivationMap, metricHash);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    const TimeUnit bucketSizeTimeUnit =
            metric.bucket() == TIME_UNIT_UNSPECIFIED ? ONE_HOUR : metric.bucket();
    const int64_t bucketSizeNs =
            MillisToNano(TimeUnitToBucketSizeInMillisGuardrailed(key.GetUid(), bucketSizeTimeUnit));

    const bool containsAnyPositionInDimensionsInWhat = HasPositionANY(metric.dimensions_in_what());
    const bool shouldUseNestedDimensions = ShouldUseNestedDimensions(metric.dimensions_in_what());

    const auto [dimensionSoftLimit, dimensionHardLimit] =
            StatsdStats::getAtomDimensionKeySizeLimits(
                    pullTagId,
                    StatsdStats::clampDimensionKeySizeLimit(metric.max_dimensions_per_bucket()));

    // get the condition_correction_threshold_nanos value
    const optional<int64_t> conditionCorrectionThresholdNs =
            metric.has_condition_correction_threshold_nanos()
                    ? optional<int64_t>(metric.condition_correction_threshold_nanos())
                    : nullopt;

    sp<MetricProducer> metricProducer = new NumericValueMetricProducer(
            key, metric, metricHash, {pullTagId, pullerManager},
            {timeBaseNs, currentTimeNs, bucketSizeNs, metric.min_bucket_size_nanos(),
             conditionCorrectionThresholdNs, getAppUpgradeBucketSplit(metric)},
            {containsAnyPositionInDimensionsInWhat, shouldUseNestedDimensions, trackerIndex,
             matcherWizard, metric.dimensions_in_what(), fieldMatchers},
            {conditionIndex, metric.links(), initialConditionCache, wizard},
            {metric.state_link(), slicedStateAtoms, stateGroupMap},
            {eventActivationMap, eventDeactivationMap}, {dimensionSoftLimit, dimensionHardLimit});

    SamplingInfo samplingInfo;
    if (metric.has_dimensional_sampling_info()) {
        invalidConfigReason = handleMetricWithDimensionalSampling(
                metric.id(), metric.dimensional_sampling_info(), dimensionsInWhat, samplingInfo);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
        metricProducer->setSamplingInfo(samplingInfo);
    }

    return metricProducer;
}

optional<sp<MetricProducer>> createKllMetricProducerAndUpdateMetadata(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseNs,
        const int64_t currentTimeNs, const sp<StatsPullerManager>& pullerManager,
        const KllMetric& metric, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const sp<EventMatcherWizard>& matcherWizard,
        const unordered_map<int64_t, int>& stateAtomIdMap,
        const unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation, optional<InvalidConfigReason>& invalidConfigReason) {
    if (!metric.has_id() || !metric.has_what()) {
        ALOGE("cannot find metric id or \"what\" in KllMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metric.id());
        return nullopt;
    }
    if (!metric.has_kll_field()) {
        ALOGE("cannot find \"kll_field\" in KllMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_KLL_METRIC_MISSING_KLL_FIELD, metric.id());
        return nullopt;
    }
    if (HasPositionALL(metric.kll_field())) {
        ALOGE("kll field with position ALL is not supported. KllMetric \"%lld\"",
              (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_KLL_METRIC_KLL_FIELD_HAS_POSITION_ALL, metric.id());
        return nullopt;
    }
    std::vector<Matcher> fieldMatchers;
    translateFieldMatcher(metric.kll_field(), &fieldMatchers);
    if (fieldMatchers.empty()) {
        ALOGE("incorrect \"kll_field\" in KllMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_KLL_METRIC_HAS_INCORRECT_KLL_FIELD, metric.id());
        return nullopt;
    }

    int trackerIndex;
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            metric.what(), metric.id(), metricIndex,
            /*enforceOneAtom=*/true, allAtomMatchingTrackers, atomMatchingTrackerMap,
            trackerToMetricMap, trackerIndex);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    int conditionIndex = -1;
    if (metric.has_condition()) {
        invalidConfigReason = handleMetricWithConditions(
                metric.condition(), metric.id(), metricIndex, conditionTrackerMap, metric.links(),
                allConditionTrackers, conditionIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else if (metric.links_size() > 0) {
        ALOGE("metrics has a MetricConditionLink but doesn't have a condition");
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION, metric.id());
        return nullopt;
    }

    std::vector<int> slicedStateAtoms;
    unordered_map<int, unordered_map<int, int64_t>> stateGroupMap;
    if (metric.slice_by_state_size() > 0) {
        invalidConfigReason =
                handleMetricWithStates(config, metric.id(), metric.slice_by_state(), stateAtomIdMap,
                                       allStateGroupMaps, slicedStateAtoms, stateGroupMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else if (metric.state_link_size() > 0) {
        ALOGE("KllMetric has a MetricStateLink but doesn't have a sliced state");
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_STATELINK_NO_STATE, metric.id());
        return nullopt;
    }

    // Check that all metric state links are a subset of dimensions_in_what fields.
    std::vector<Matcher> dimensionsInWhat;
    translateFieldMatcher(metric.dimensions_in_what(), &dimensionsInWhat);
    for (const auto& stateLink : metric.state_link()) {
        invalidConfigReason = handleMetricWithStateLink(metric.id(), stateLink.fields_in_what(),
                                                        dimensionsInWhat);
        if (invalidConfigReason.has_value()) {
            ALOGW("KllMetric's MetricStateLinks must be a subset of the dimensions in what");
            return nullopt;
        }
    }

    unordered_map<int, shared_ptr<Activation>> eventActivationMap;
    unordered_map<int, vector<shared_ptr<Activation>>> eventDeactivationMap;
    invalidConfigReason = handleMetricActivation(
            config, metric.id(), metricIndex, metricToActivationMap, atomMatchingTrackerMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation, eventActivationMap, eventDeactivationMap);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    uint64_t metricHash;
    invalidConfigReason =
            getMetricProtoHash(config, metric, metric.id(), metricToActivationMap, metricHash);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    const TimeUnit bucketSizeTimeUnit =
            metric.bucket() == TIME_UNIT_UNSPECIFIED ? ONE_HOUR : metric.bucket();
    const int64_t bucketSizeNs =
            MillisToNano(TimeUnitToBucketSizeInMillisGuardrailed(key.GetUid(), bucketSizeTimeUnit));

    const bool containsAnyPositionInDimensionsInWhat = HasPositionANY(metric.dimensions_in_what());
    const bool shouldUseNestedDimensions = ShouldUseNestedDimensions(metric.dimensions_in_what());

    const sp<AtomMatchingTracker>& atomMatcher = allAtomMatchingTrackers.at(trackerIndex);
    const int atomTagId = *(atomMatcher->getAtomIds().begin());
    const auto [dimensionSoftLimit, dimensionHardLimit] =
            StatsdStats::getAtomDimensionKeySizeLimits(
                    atomTagId,
                    StatsdStats::clampDimensionKeySizeLimit(metric.max_dimensions_per_bucket()));

    sp<MetricProducer> metricProducer = new KllMetricProducer(
            key, metric, metricHash, {/*pullTagId=*/-1, pullerManager},
            {timeBaseNs, currentTimeNs, bucketSizeNs, metric.min_bucket_size_nanos(),
             /*conditionCorrectionThresholdNs=*/nullopt, getAppUpgradeBucketSplit(metric)},
            {containsAnyPositionInDimensionsInWhat, shouldUseNestedDimensions, trackerIndex,
             matcherWizard, metric.dimensions_in_what(), fieldMatchers},
            {conditionIndex, metric.links(), initialConditionCache, wizard},
            {metric.state_link(), slicedStateAtoms, stateGroupMap},
            {eventActivationMap, eventDeactivationMap}, {dimensionSoftLimit, dimensionHardLimit});

    SamplingInfo samplingInfo;
    if (metric.has_dimensional_sampling_info()) {
        invalidConfigReason = handleMetricWithDimensionalSampling(
                metric.id(), metric.dimensional_sampling_info(), dimensionsInWhat, samplingInfo);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
        metricProducer->setSamplingInfo(samplingInfo);
    }

    return metricProducer;
}

optional<sp<MetricProducer>> createGaugeMetricProducerAndUpdateMetadata(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseNs,
        const int64_t currentTimeNs, const sp<StatsPullerManager>& pullerManager,
        const GaugeMetric& metric, const int metricIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
        const sp<EventMatcherWizard>& matcherWizard,
        const unordered_map<int64_t, int>& metricToActivationMap,
        unordered_map<int, vector<int>>& trackerToMetricMap,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation, optional<InvalidConfigReason>& invalidConfigReason) {
    if (!metric.has_id() || !metric.has_what()) {
        ALOGE("cannot find metric id or \"what\" in GaugeMetric \"%lld\"", (long long)metric.id());
        invalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metric.id());
        return nullopt;
    }

    if ((!metric.gauge_fields_filter().has_include_all() ||
         (metric.gauge_fields_filter().include_all() == false)) &&
        !hasLeafNode(metric.gauge_fields_filter().fields())) {
        ALOGW("Incorrect field filter setting in GaugeMetric %lld", (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_GAUGE_METRIC_INCORRECT_FIELD_FILTER, metric.id());
        return nullopt;
    }
    if ((metric.gauge_fields_filter().has_include_all() &&
         metric.gauge_fields_filter().include_all() == true) &&
        hasLeafNode(metric.gauge_fields_filter().fields())) {
        ALOGW("Incorrect field filter setting in GaugeMetric %lld", (long long)metric.id());
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_GAUGE_METRIC_INCORRECT_FIELD_FILTER, metric.id());
        return nullopt;
    }

    int trackerIndex;
    invalidConfigReason = handleMetricWithAtomMatchingTrackers(
            metric.what(), metric.id(), metricIndex, true, allAtomMatchingTrackers,
            atomMatchingTrackerMap, trackerToMetricMap, trackerIndex);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    const sp<AtomMatchingTracker>& atomMatcher = allAtomMatchingTrackers.at(trackerIndex);
    int atomTagId = *(atomMatcher->getAtomIds().begin());
    int pullTagId = pullerManager->PullerForMatcherExists(atomTagId) ? atomTagId : -1;

    int triggerTrackerIndex;
    int triggerAtomId = -1;
    if (metric.has_trigger_event()) {
        if (pullTagId == -1) {
            ALOGW("Pull atom not specified for trigger");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_GAUGE_METRIC_TRIGGER_NO_PULL_ATOM, metric.id());
            return nullopt;
        }
        // trigger_event should be used with FIRST_N_SAMPLES
        if (metric.sampling_type() != GaugeMetric::FIRST_N_SAMPLES) {
            ALOGW("Gauge Metric with trigger event must have sampling type FIRST_N_SAMPLES");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_GAUGE_METRIC_TRIGGER_NO_FIRST_N_SAMPLES, metric.id());
            return nullopt;
        }
        invalidConfigReason = handleMetricWithAtomMatchingTrackers(
                metric.trigger_event(), metric.id(), metricIndex,
                /*enforceOneAtom=*/true, allAtomMatchingTrackers, atomMatchingTrackerMap,
                trackerToMetricMap, triggerTrackerIndex);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
        const sp<AtomMatchingTracker>& triggerAtomMatcher =
                allAtomMatchingTrackers.at(triggerTrackerIndex);
        triggerAtomId = *(triggerAtomMatcher->getAtomIds().begin());
    }

    int conditionIndex = -1;
    if (metric.has_condition()) {
        invalidConfigReason = handleMetricWithConditions(
                metric.condition(), metric.id(), metricIndex, conditionTrackerMap, metric.links(),
                allConditionTrackers, conditionIndex, conditionToMetricMap);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
    } else {
        if (metric.links_size() > 0) {
            ALOGW("metrics has a MetricConditionLink but doesn't have a condition");
            invalidConfigReason = InvalidConfigReason(
                    INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION, metric.id());
            return nullopt;
        }
    }

    if (pullTagId != -1 && metric.sampling_percentage() != 100) {
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_GAUGE_METRIC_PULLED_WITH_SAMPLING, metric.id());
        return nullopt;
    }

    if (metric.sampling_percentage() < 1 || metric.sampling_percentage() > 100) {
        invalidConfigReason = InvalidConfigReason(
                INVALID_CONFIG_REASON_METRIC_INCORRECT_SAMPLING_PERCENTAGE, metric.id());
        return nullopt;
    }

    unordered_map<int, shared_ptr<Activation>> eventActivationMap;
    unordered_map<int, vector<shared_ptr<Activation>>> eventDeactivationMap;
    invalidConfigReason = handleMetricActivation(
            config, metric.id(), metricIndex, metricToActivationMap, atomMatchingTrackerMap,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation, eventActivationMap, eventDeactivationMap);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    uint64_t metricHash;
    invalidConfigReason =
            getMetricProtoHash(config, metric, metric.id(), metricToActivationMap, metricHash);
    if (invalidConfigReason.has_value()) {
        return nullopt;
    }

    const auto [dimensionSoftLimit, dimensionHardLimit] =
            StatsdStats::getAtomDimensionKeySizeLimits(
                    pullTagId,
                    StatsdStats::clampDimensionKeySizeLimit(metric.max_dimensions_per_bucket()));

    sp<MetricProducer> metricProducer = new GaugeMetricProducer(
            key, metric, conditionIndex, initialConditionCache, wizard, metricHash, trackerIndex,
            matcherWizard, pullTagId, triggerAtomId, atomTagId, timeBaseNs, currentTimeNs,
            pullerManager, eventActivationMap, eventDeactivationMap, dimensionSoftLimit,
            dimensionHardLimit);

    SamplingInfo samplingInfo;
    std::vector<Matcher> dimensionsInWhat;
    translateFieldMatcher(metric.dimensions_in_what(), &dimensionsInWhat);
    if (metric.has_dimensional_sampling_info()) {
        invalidConfigReason = handleMetricWithDimensionalSampling(
                metric.id(), metric.dimensional_sampling_info(), dimensionsInWhat, samplingInfo);
        if (invalidConfigReason.has_value()) {
            return nullopt;
        }
        metricProducer->setSamplingInfo(samplingInfo);
    }

    return metricProducer;
}

optional<sp<AnomalyTracker>> createAnomalyTracker(
        const Alert& alert, const sp<AlarmMonitor>& anomalyAlarmMonitor,
        const UpdateStatus& updateStatus, const int64_t currentTimeNs,
        const unordered_map<int64_t, int>& metricProducerMap,
        vector<sp<MetricProducer>>& allMetricProducers,
        optional<InvalidConfigReason>& invalidConfigReason) {
    const auto& itr = metricProducerMap.find(alert.metric_id());
    if (itr == metricProducerMap.end()) {
        ALOGW("alert \"%lld\" has unknown metric id: \"%lld\"", (long long)alert.id(),
              (long long)alert.metric_id());
        invalidConfigReason = createInvalidConfigReasonWithAlert(
                INVALID_CONFIG_REASON_ALERT_METRIC_NOT_FOUND, alert.metric_id(), alert.id());
        return nullopt;
    }
    if (!alert.has_trigger_if_sum_gt()) {
        ALOGW("invalid alert: missing threshold");
        invalidConfigReason = createInvalidConfigReasonWithAlert(
                INVALID_CONFIG_REASON_ALERT_THRESHOLD_MISSING, alert.id());
        return nullopt;
    }
    if (alert.trigger_if_sum_gt() < 0 || alert.num_buckets() <= 0) {
        ALOGW("invalid alert: threshold=%f num_buckets= %d", alert.trigger_if_sum_gt(),
              alert.num_buckets());
        invalidConfigReason = createInvalidConfigReasonWithAlert(
                INVALID_CONFIG_REASON_ALERT_INVALID_TRIGGER_OR_NUM_BUCKETS, alert.id());
        return nullopt;
    }
    const int metricIndex = itr->second;
    sp<MetricProducer> metric = allMetricProducers[metricIndex];
    sp<AnomalyTracker> anomalyTracker =
            metric->addAnomalyTracker(alert, anomalyAlarmMonitor, updateStatus, currentTimeNs);
    if (anomalyTracker == nullptr) {
        // The ALOGW for this invalid alert was already displayed in addAnomalyTracker().
        invalidConfigReason = createInvalidConfigReasonWithAlert(
                INVALID_CONFIG_REASON_ALERT_CANNOT_ADD_ANOMALY, alert.metric_id(), alert.id());
        return nullopt;
    }
    return {anomalyTracker};
}

optional<InvalidConfigReason> initAtomMatchingTrackers(
        const StatsdConfig& config, const sp<UidMap>& uidMap,
        unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        unordered_map<int, vector<int>>& allTagIdsToMatchersMap) {
    vector<AtomMatcher> matcherConfigs;
    const int atomMatcherCount = config.atom_matcher_size();
    matcherConfigs.reserve(atomMatcherCount);
    allAtomMatchingTrackers.reserve(atomMatcherCount);
    optional<InvalidConfigReason> invalidConfigReason;

    for (int i = 0; i < atomMatcherCount; i++) {
        const AtomMatcher& logMatcher = config.atom_matcher(i);
        sp<AtomMatchingTracker> tracker =
                createAtomMatchingTracker(logMatcher, uidMap, invalidConfigReason);
        if (tracker == nullptr) {
            return invalidConfigReason;
        }
        allAtomMatchingTrackers.push_back(tracker);
        if (atomMatchingTrackerMap.find(logMatcher.id()) != atomMatchingTrackerMap.end()) {
            ALOGE("Duplicate AtomMatcher found!");
            return createInvalidConfigReasonWithMatcher(INVALID_CONFIG_REASON_MATCHER_DUPLICATE,
                                                        logMatcher.id());
        }
        atomMatchingTrackerMap[logMatcher.id()] = i;
        matcherConfigs.push_back(logMatcher);
    }

    vector<uint8_t> stackTracker2(allAtomMatchingTrackers.size(), false);
    for (size_t matcherIndex = 0; matcherIndex < allAtomMatchingTrackers.size(); matcherIndex++) {
        auto& matcher = allAtomMatchingTrackers[matcherIndex];
        const auto [invalidConfigReason, _] =
                matcher->init(matcherIndex, matcherConfigs, allAtomMatchingTrackers,
                              atomMatchingTrackerMap, stackTracker2);
        if (invalidConfigReason.has_value()) {
            return invalidConfigReason;
        }

        // Collect all the tag ids that are interesting. TagIds exist in leaf nodes only.
        const set<int>& tagIds = matcher->getAtomIds();
        for (int atomId : tagIds) {
            auto& matchers = allTagIdsToMatchersMap[atomId];
            // Performance note:
            // For small amount of elements linear search in vector will be
            // faster then look up in a set:
            // - we do not expect matchers vector per atom id will have significant size (< 10)
            // - iteration via vector is the fastest way compared to other containers (set, etc.)
            //   in the hot path MetricsManager::onLogEvent()
            // - vector<T> will have the smallest memory footprint compared to any other
            //   std containers implementation
            if (find(matchers.begin(), matchers.end(), matcherIndex) == matchers.end()) {
                matchers.push_back(matcherIndex);
            }
        }
    }

    return nullopt;
}

optional<InvalidConfigReason> initConditions(
        const ConfigKey& key, const StatsdConfig& config,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        unordered_map<int64_t, int>& conditionTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        unordered_map<int, std::vector<int>>& trackerToConditionMap,
        vector<ConditionState>& initialConditionCache) {
    vector<Predicate> conditionConfigs;
    const int conditionTrackerCount = config.predicate_size();
    conditionConfigs.reserve(conditionTrackerCount);
    allConditionTrackers.reserve(conditionTrackerCount);
    initialConditionCache.assign(conditionTrackerCount, ConditionState::kNotEvaluated);
    optional<InvalidConfigReason> invalidConfigReason;

    for (int i = 0; i < conditionTrackerCount; i++) {
        const Predicate& condition = config.predicate(i);
        sp<ConditionTracker> tracker = createConditionTracker(
                key, condition, i, atomMatchingTrackerMap, invalidConfigReason);
        if (tracker == nullptr) {
            return invalidConfigReason;
        }
        allConditionTrackers.push_back(tracker);
        if (conditionTrackerMap.find(condition.id()) != conditionTrackerMap.end()) {
            ALOGE("Duplicate Predicate found!");
            return createInvalidConfigReasonWithPredicate(INVALID_CONFIG_REASON_CONDITION_DUPLICATE,
                                                          condition.id());
        }
        conditionTrackerMap[condition.id()] = i;
        conditionConfigs.push_back(condition);
    }

    vector<uint8_t> stackTracker(allConditionTrackers.size(), false);
    for (size_t i = 0; i < allConditionTrackers.size(); i++) {
        auto& conditionTracker = allConditionTrackers[i];
        invalidConfigReason =
                conditionTracker->init(conditionConfigs, allConditionTrackers, conditionTrackerMap,
                                       stackTracker, initialConditionCache);
        if (invalidConfigReason.has_value()) {
            return invalidConfigReason;
        }
        for (const int trackerIndex : conditionTracker->getAtomMatchingTrackerIndex()) {
            auto& conditionList = trackerToConditionMap[trackerIndex];
            conditionList.push_back(i);
        }
    }
    return nullopt;
}

optional<InvalidConfigReason> initStates(
        const StatsdConfig& config, unordered_map<int64_t, int>& stateAtomIdMap,
        unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        map<int64_t, uint64_t>& stateProtoHashes) {
    for (int i = 0; i < config.state_size(); i++) {
        const State& state = config.state(i);
        const int64_t stateId = state.id();
        stateAtomIdMap[stateId] = state.atom_id();

        string serializedState;
        if (!state.SerializeToString(&serializedState)) {
            ALOGE("Unable to serialize state %lld", (long long)stateId);
            return createInvalidConfigReasonWithState(
                    INVALID_CONFIG_REASON_STATE_SERIALIZATION_FAILED, state.id(), state.atom_id());
        }
        stateProtoHashes[stateId] = Hash64(serializedState);

        const StateMap& stateMap = state.map();
        for (const auto& group : stateMap.group()) {
            for (const auto& value : group.value()) {
                allStateGroupMaps[stateId][value] = group.group_id();
            }
        }
    }

    return nullopt;
}

optional<InvalidConfigReason> initMetrics(
        const ConfigKey& key, const StatsdConfig& config, const int64_t timeBaseTimeNs,
        const int64_t currentTimeNs, const sp<StatsPullerManager>& pullerManager,
        const unordered_map<int64_t, int>& atomMatchingTrackerMap,
        const unordered_map<int64_t, int>& conditionTrackerMap,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& stateAtomIdMap,
        const unordered_map<int64_t, unordered_map<int, int64_t>>& allStateGroupMaps,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        const vector<ConditionState>& initialConditionCache,
        vector<sp<MetricProducer>>& allMetricProducers,
        unordered_map<int, vector<int>>& conditionToMetricMap,
        unordered_map<int, vector<int>>& trackerToMetricMap, unordered_map<int64_t, int>& metricMap,
        std::set<int64_t>& noReportMetricIds,
        unordered_map<int, vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, vector<int>>& deactivationAtomTrackerToMetricMap,
        vector<int>& metricsWithActivation) {
    sp<ConditionWizard> wizard = new ConditionWizard(allConditionTrackers);
    sp<EventMatcherWizard> matcherWizard = new EventMatcherWizard(allAtomMatchingTrackers);
    const int allMetricsCount = config.count_metric_size() + config.duration_metric_size() +
                                config.event_metric_size() + config.gauge_metric_size() +
                                config.value_metric_size() + config.kll_metric_size();
    allMetricProducers.reserve(allMetricsCount);
    optional<InvalidConfigReason> invalidConfigReason;

    if (config.has_restricted_metrics_delegate_package_name() &&
        allMetricsCount != config.event_metric_size()) {
        ALOGE("Restricted metrics only support event metric");
        return InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_SUPPORTED);
    }

    // Construct map from metric id to metric activation index. The map will be used to determine
    // the metric activation corresponding to a metric.
    unordered_map<int64_t, int> metricToActivationMap;
    for (int i = 0; i < config.metric_activation_size(); i++) {
        const MetricActivation& metricActivation = config.metric_activation(i);
        int64_t metricId = metricActivation.metric_id();
        if (metricToActivationMap.find(metricId) != metricToActivationMap.end()) {
            ALOGE("Metric %lld has multiple MetricActivations", (long long)metricId);
            return InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_HAS_MULTIPLE_ACTIVATIONS,
                                       metricId);
        }
        metricToActivationMap.insert({metricId, i});
    }

    // Build MetricProducers for each metric defined in config.
    // build CountMetricProducer
    for (int i = 0; i < config.count_metric_size(); i++) {
        int metricIndex = allMetricProducers.size();
        const CountMetric& metric = config.count_metric(i);
        metricMap.insert({metric.id(), metricIndex});
        optional<sp<MetricProducer>> producer = createCountMetricProducerAndUpdateMetadata(
                key, config, timeBaseTimeNs, currentTimeNs, metric, metricIndex,
                allAtomMatchingTrackers, atomMatchingTrackerMap, allConditionTrackers,
                conditionTrackerMap, initialConditionCache, wizard, stateAtomIdMap,
                allStateGroupMaps, metricToActivationMap, trackerToMetricMap, conditionToMetricMap,
                activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
                metricsWithActivation, invalidConfigReason);
        if (!producer) {
            return invalidConfigReason;
        }
        allMetricProducers.push_back(producer.value());
    }

    // build DurationMetricProducer
    for (int i = 0; i < config.duration_metric_size(); i++) {
        int metricIndex = allMetricProducers.size();
        const DurationMetric& metric = config.duration_metric(i);
        metricMap.insert({metric.id(), metricIndex});

        optional<sp<MetricProducer>> producer = createDurationMetricProducerAndUpdateMetadata(
                key, config, timeBaseTimeNs, currentTimeNs, metric, metricIndex,
                allAtomMatchingTrackers, atomMatchingTrackerMap, allConditionTrackers,
                conditionTrackerMap, initialConditionCache, wizard, stateAtomIdMap,
                allStateGroupMaps, metricToActivationMap, trackerToMetricMap, conditionToMetricMap,
                activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
                metricsWithActivation, invalidConfigReason);
        if (!producer) {
            return invalidConfigReason;
        }
        allMetricProducers.push_back(producer.value());
    }

    // build EventMetricProducer
    for (int i = 0; i < config.event_metric_size(); i++) {
        int metricIndex = allMetricProducers.size();
        const EventMetric& metric = config.event_metric(i);
        metricMap.insert({metric.id(), metricIndex});
        optional<sp<MetricProducer>> producer = createEventMetricProducerAndUpdateMetadata(
                key, config, timeBaseTimeNs, metric, metricIndex, allAtomMatchingTrackers,
                atomMatchingTrackerMap, allConditionTrackers, conditionTrackerMap,
                initialConditionCache, wizard, metricToActivationMap, trackerToMetricMap,
                conditionToMetricMap, activationAtomTrackerToMetricMap,
                deactivationAtomTrackerToMetricMap, metricsWithActivation, invalidConfigReason);
        if (!producer) {
            return invalidConfigReason;
        }
        allMetricProducers.push_back(producer.value());
    }

    // build NumericValueMetricProducer
    for (int i = 0; i < config.value_metric_size(); i++) {
        int metricIndex = allMetricProducers.size();
        const ValueMetric& metric = config.value_metric(i);
        metricMap.insert({metric.id(), metricIndex});
        optional<sp<MetricProducer>> producer = createNumericValueMetricProducerAndUpdateMetadata(
                key, config, timeBaseTimeNs, currentTimeNs, pullerManager, metric, metricIndex,
                allAtomMatchingTrackers, atomMatchingTrackerMap, allConditionTrackers,
                conditionTrackerMap, initialConditionCache, wizard, matcherWizard, stateAtomIdMap,
                allStateGroupMaps, metricToActivationMap, trackerToMetricMap, conditionToMetricMap,
                activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
                metricsWithActivation, invalidConfigReason);
        if (!producer) {
            return invalidConfigReason;
        }
        allMetricProducers.push_back(producer.value());
    }

    // build KllMetricProducer
    for (int i = 0; i < config.kll_metric_size(); i++) {
        int metricIndex = allMetricProducers.size();
        const KllMetric& metric = config.kll_metric(i);
        metricMap.insert({metric.id(), metricIndex});
        optional<sp<MetricProducer>> producer = createKllMetricProducerAndUpdateMetadata(
                key, config, timeBaseTimeNs, currentTimeNs, pullerManager, metric, metricIndex,
                allAtomMatchingTrackers, atomMatchingTrackerMap, allConditionTrackers,
                conditionTrackerMap, initialConditionCache, wizard, matcherWizard, stateAtomIdMap,
                allStateGroupMaps, metricToActivationMap, trackerToMetricMap, conditionToMetricMap,
                activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
                metricsWithActivation, invalidConfigReason);
        if (!producer) {
            return invalidConfigReason;
        }
        allMetricProducers.push_back(producer.value());
    }

    // Gauge metrics.
    for (int i = 0; i < config.gauge_metric_size(); i++) {
        int metricIndex = allMetricProducers.size();
        const GaugeMetric& metric = config.gauge_metric(i);
        metricMap.insert({metric.id(), metricIndex});
        optional<sp<MetricProducer>> producer = createGaugeMetricProducerAndUpdateMetadata(
                key, config, timeBaseTimeNs, currentTimeNs, pullerManager, metric, metricIndex,
                allAtomMatchingTrackers, atomMatchingTrackerMap, allConditionTrackers,
                conditionTrackerMap, initialConditionCache, wizard, matcherWizard,
                metricToActivationMap, trackerToMetricMap, conditionToMetricMap,
                activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
                metricsWithActivation, invalidConfigReason);
        if (!producer) {
            return invalidConfigReason;
        }
        allMetricProducers.push_back(producer.value());
    }
    for (int i = 0; i < config.no_report_metric_size(); ++i) {
        const auto no_report_metric = config.no_report_metric(i);
        if (metricMap.find(no_report_metric) == metricMap.end()) {
            ALOGW("no_report_metric %" PRId64 " not exist", no_report_metric);
            return InvalidConfigReason(INVALID_CONFIG_REASON_NO_REPORT_METRIC_NOT_FOUND,
                                       no_report_metric);
        }
        noReportMetricIds.insert(no_report_metric);
    }

    const set<int> whitelistedAtomIds(config.whitelisted_atom_ids().begin(),
                                      config.whitelisted_atom_ids().end());
    for (const auto& it : allMetricProducers) {
        // Register metrics to StateTrackers
        for (int atomId : it->getSlicedStateAtoms()) {
            // Register listener for non-whitelisted atoms only. Using whitelisted atom as a sliced
            // state atom is not allowed.
            if (whitelistedAtomIds.find(atomId) == whitelistedAtomIds.end()) {
                StateManager::getInstance().registerListener(atomId, it);
            } else {
                return InvalidConfigReason(
                        INVALID_CONFIG_REASON_METRIC_SLICED_STATE_ATOM_ALLOWED_FROM_ANY_UID,
                        it->getMetricId());
            }
        }
    }
    return nullopt;
}

optional<InvalidConfigReason> initAlerts(const StatsdConfig& config, const int64_t currentTimeNs,
                                         const unordered_map<int64_t, int>& metricProducerMap,
                                         unordered_map<int64_t, int>& alertTrackerMap,
                                         const sp<AlarmMonitor>& anomalyAlarmMonitor,
                                         vector<sp<MetricProducer>>& allMetricProducers,
                                         vector<sp<AnomalyTracker>>& allAnomalyTrackers) {
    optional<InvalidConfigReason> invalidConfigReason;
    for (int i = 0; i < config.alert_size(); i++) {
        const Alert& alert = config.alert(i);
        alertTrackerMap.insert(std::make_pair(alert.id(), allAnomalyTrackers.size()));
        optional<sp<AnomalyTracker>> anomalyTracker = createAnomalyTracker(
                alert, anomalyAlarmMonitor, UpdateStatus::UPDATE_NEW, currentTimeNs,
                metricProducerMap, allMetricProducers, invalidConfigReason);
        if (!anomalyTracker) {
            return invalidConfigReason;
        }
        allAnomalyTrackers.push_back(anomalyTracker.value());
    }
    return initSubscribersForSubscriptionType(config, Subscription::ALERT, alertTrackerMap,
                                              allAnomalyTrackers);
}

optional<InvalidConfigReason> initAlarms(const StatsdConfig& config, const ConfigKey& key,
                                         const sp<AlarmMonitor>& periodicAlarmMonitor,
                                         const int64_t timeBaseNs, const int64_t currentTimeNs,
                                         vector<sp<AlarmTracker>>& allAlarmTrackers) {
    unordered_map<int64_t, int> alarmTrackerMap;
    int64_t startMillis = timeBaseNs / 1000 / 1000;
    int64_t currentTimeMillis = currentTimeNs / 1000 / 1000;
    for (int i = 0; i < config.alarm_size(); i++) {
        const Alarm& alarm = config.alarm(i);
        if (alarm.offset_millis() <= 0) {
            ALOGW("Alarm offset_millis should be larger than 0.");
            return createInvalidConfigReasonWithAlarm(
                    INVALID_CONFIG_REASON_ALARM_OFFSET_LESS_THAN_OR_EQUAL_ZERO, alarm.id());
        }
        if (alarm.period_millis() <= 0) {
            ALOGW("Alarm period_millis should be larger than 0.");
            return createInvalidConfigReasonWithAlarm(
                    INVALID_CONFIG_REASON_ALARM_PERIOD_LESS_THAN_OR_EQUAL_ZERO, alarm.id());
        }
        alarmTrackerMap.insert(std::make_pair(alarm.id(), allAlarmTrackers.size()));
        allAlarmTrackers.push_back(
                new AlarmTracker(startMillis, currentTimeMillis, alarm, key, periodicAlarmMonitor));
    }
    return initSubscribersForSubscriptionType(config, Subscription::ALARM, alarmTrackerMap,
                                              allAlarmTrackers);
}

optional<InvalidConfigReason> initStatsdConfig(
        const ConfigKey& key, const StatsdConfig& config, const sp<UidMap>& uidMap,
        const sp<StatsPullerManager>& pullerManager, const sp<AlarmMonitor>& anomalyAlarmMonitor,
        const sp<AlarmMonitor>& periodicAlarmMonitor, const int64_t timeBaseNs,
        const int64_t currentTimeNs,
        std::unordered_map<int, std::vector<int>>& allTagIdsToMatchersMap,
        vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        unordered_map<int64_t, int>& atomMatchingTrackerMap,
        vector<sp<ConditionTracker>>& allConditionTrackers,
        unordered_map<int64_t, int>& conditionTrackerMap,
        vector<sp<MetricProducer>>& allMetricProducers,
        unordered_map<int64_t, int>& metricProducerMap,
        vector<sp<AnomalyTracker>>& allAnomalyTrackers,
        vector<sp<AlarmTracker>>& allPeriodicAlarmTrackers,
        unordered_map<int, std::vector<int>>& conditionToMetricMap,
        unordered_map<int, std::vector<int>>& trackerToMetricMap,
        unordered_map<int, std::vector<int>>& trackerToConditionMap,
        unordered_map<int, std::vector<int>>& activationAtomTrackerToMetricMap,
        unordered_map<int, std::vector<int>>& deactivationAtomTrackerToMetricMap,
        unordered_map<int64_t, int>& alertTrackerMap, vector<int>& metricsWithActivation,
        map<int64_t, uint64_t>& stateProtoHashes, set<int64_t>& noReportMetricIds) {
    vector<ConditionState> initialConditionCache;
    unordered_map<int64_t, int> stateAtomIdMap;
    unordered_map<int64_t, unordered_map<int, int64_t>> allStateGroupMaps;

    if (config.package_certificate_hash_size_bytes() > UINT8_MAX) {
        ALOGE("Invalid value for package_certificate_hash_size_bytes: %d",
              config.package_certificate_hash_size_bytes());
        return InvalidConfigReason(INVALID_CONFIG_REASON_PACKAGE_CERT_HASH_SIZE_TOO_LARGE);
    }

    optional<InvalidConfigReason> invalidConfigReason =
            initAtomMatchingTrackers(config, uidMap, atomMatchingTrackerMap,
                                     allAtomMatchingTrackers, allTagIdsToMatchersMap);
    if (invalidConfigReason.has_value()) {
        ALOGE("initAtomMatchingTrackers failed");
        return invalidConfigReason;
    }
    VLOG("initAtomMatchingTrackers succeed...");

    invalidConfigReason =
            initConditions(key, config, atomMatchingTrackerMap, conditionTrackerMap,
                           allConditionTrackers, trackerToConditionMap, initialConditionCache);
    if (invalidConfigReason.has_value()) {
        ALOGE("initConditionTrackers failed");
        return invalidConfigReason;
    }

    invalidConfigReason = initStates(config, stateAtomIdMap, allStateGroupMaps, stateProtoHashes);
    if (invalidConfigReason.has_value()) {
        ALOGE("initStates failed");
        return invalidConfigReason;
    }

    invalidConfigReason = initMetrics(
            key, config, timeBaseNs, currentTimeNs, pullerManager, atomMatchingTrackerMap,
            conditionTrackerMap, allAtomMatchingTrackers, stateAtomIdMap, allStateGroupMaps,
            allConditionTrackers, initialConditionCache, allMetricProducers, conditionToMetricMap,
            trackerToMetricMap, metricProducerMap, noReportMetricIds,
            activationAtomTrackerToMetricMap, deactivationAtomTrackerToMetricMap,
            metricsWithActivation);
    if (invalidConfigReason.has_value()) {
        ALOGE("initMetricProducers failed");
        return invalidConfigReason;
    }

    invalidConfigReason = initAlerts(config, currentTimeNs, metricProducerMap, alertTrackerMap,
                                     anomalyAlarmMonitor, allMetricProducers, allAnomalyTrackers);
    if (invalidConfigReason.has_value()) {
        ALOGE("initAlerts failed");
        return invalidConfigReason;
    }

    invalidConfigReason = initAlarms(config, key, periodicAlarmMonitor, timeBaseNs, currentTimeNs,
                                     allPeriodicAlarmTrackers);
    if (invalidConfigReason.has_value()) {
        ALOGE("initAlarms failed");
        return invalidConfigReason;
    }

    return nullopt;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
