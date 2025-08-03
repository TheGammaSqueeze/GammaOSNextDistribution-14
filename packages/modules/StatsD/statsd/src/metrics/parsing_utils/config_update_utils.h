/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once

#include <vector>

#include "anomaly/AlarmMonitor.h"
#include "anomaly/AlarmTracker.h"
#include "condition/ConditionTracker.h"
#include "external/StatsPullerManager.h"
#include "matchers/AtomMatchingTracker.h"
#include "metrics/MetricProducer.h"

namespace android {
namespace os {
namespace statsd {

// Helper functions for MetricsManager to update itself from a new StatsdConfig.
// *Note*: only updateStatsdConfig() should be called from outside this file.
// All other functions are intermediate steps, created to make unit testing easier.

// Recursive function to determine if a matcher needs to be updated.
// input:
// [config]: the input StatsdConfig
// [matcherIdx]: the index of the current matcher to be updated
// [oldAtomMatchingTrackerMap]: matcher id to index mapping in the existing MetricsManager
// [oldAtomMatchingTrackers]: stores the existing AtomMatchingTrackers
// [newAtomMatchingTrackerMap]: matcher id to index mapping in the input StatsdConfig
// output:
// [matchersToUpdate]: vector of the update status of each matcher. The matcherIdx index will
//                     be updated from UPDATE_UNKNOWN after this call.
// [cycleTracker]: intermediate param used during recursion.
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> determineMatcherUpdateStatus(
        const StatsdConfig& config, int matcherIdx,
        const std::unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
        const std::vector<sp<AtomMatchingTracker>>& oldAtomMatchingTrackers,
        const std::unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
        std::vector<UpdateStatus>& matchersToUpdate, std::vector<uint8_t>& cycleTracker);

// Updates the AtomMatchingTrackers.
// input:
// [config]: the input StatsdConfig
// [oldAtomMatchingTrackerMap]: existing matcher id to index mapping
// [oldAtomMatchingTrackers]: stores the existing AtomMatchingTrackers
// output:
// [allTagIdsToMatchersMap]: maps of tag ids to atom matchers
// [newAtomMatchingTrackerMap]: new matcher id to index mapping
// [newAtomMatchingTrackers]: stores the new AtomMatchingTrackers
// [replacedMatchers]: set of matcher ids that changed and have been replaced
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> updateAtomMatchingTrackers(
        const StatsdConfig& config, const sp<UidMap>& uidMap,
        const std::unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
        const std::vector<sp<AtomMatchingTracker>>& oldAtomMatchingTrackers,
        std::unordered_map<int, std::vector<int>>& allTagIdsToMatchersMap,
        std::unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
        std::vector<sp<AtomMatchingTracker>>& newAtomMatchingTrackers,
        std::set<int64_t>& replacedMatchers);

// Recursive function to determine if a condition needs to be updated.
// input:
// [config]: the input StatsdConfig
// [conditionIdx]: the index of the current condition to be updated
// [oldConditionTrackerMap]: condition id to index mapping in the existing MetricsManager
// [oldConditionTrackers]: stores the existing ConditionTrackers
// [newConditionTrackerMap]: condition id to index mapping in the input StatsdConfig
// [replacedMatchers]: set of replaced matcher ids. conditions using these matchers must be replaced
// output:
// [conditionsToUpdate]: vector of the update status of each condition. The conditionIdx index will
//                       be updated from UPDATE_UNKNOWN after this call.
// [cycleTracker]: intermediate param used during recursion.
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> determineConditionUpdateStatus(
        const StatsdConfig& config, int conditionIdx,
        const std::unordered_map<int64_t, int>& oldConditionTrackerMap,
        const std::vector<sp<ConditionTracker>>& oldConditionTrackers,
        const std::unordered_map<int64_t, int>& newConditionTrackerMap,
        const std::set<int64_t>& replacedMatchers, std::vector<UpdateStatus>& conditionsToUpdate,
        std::vector<uint8_t>& cycleTracker);

// Updates ConditionTrackers
// input:
// [config]: the input config
// [atomMatchingTrackerMap]: AtomMatchingTracker name to index mapping from previous step.
// [replacedMatchers]: ids of replaced matchers. conditions depending on these must also be replaced
// [oldConditionTrackerMap]: existing matcher id to index mapping
// [oldConditionTrackers]: stores the existing ConditionTrackers
// output:
// [newConditionTrackerMap]: new condition id to index mapping
// [newConditionTrackers]: stores the sp to all the ConditionTrackers
// [trackerToConditionMap]: contains the mapping from the index of an atom matcher
//                          to indices of condition trackers that use the matcher
// [conditionCache]: stores the current conditions for each ConditionTracker
// [replacedConditions]: set of condition ids that have changed and have been replaced
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> updateConditions(
        const ConfigKey& key, const StatsdConfig& config,
        const std::unordered_map<int64_t, int>& atomMatchingTrackerMap,
        const std::set<int64_t>& replacedMatchers,
        const std::unordered_map<int64_t, int>& oldConditionTrackerMap,
        const std::vector<sp<ConditionTracker>>& oldConditionTrackers,
        std::unordered_map<int64_t, int>& newConditionTrackerMap,
        std::vector<sp<ConditionTracker>>& newConditionTrackers,
        std::unordered_map<int, std::vector<int>>& trackerToConditionMap,
        std::vector<ConditionState>& conditionCache, std::set<int64_t>& replacedConditions);

optional<InvalidConfigReason> updateStates(
        const StatsdConfig& config, const std::map<int64_t, uint64_t>& oldStateProtoHashes,
        std::unordered_map<int64_t, int>& stateAtomIdMap,
        std::unordered_map<int64_t, std::unordered_map<int, int64_t>>& allStateGroupMaps,
        std::map<int64_t, uint64_t>& newStateProtoHashes, std::set<int64_t>& replacedStates);

// Function to determine the update status (preserve/replace/new) of all metrics in the config.
// [config]: the input StatsdConfig
// [oldMetricProducerMap]: metric id to index mapping in the existing MetricsManager
// [oldMetricProducers]: stores the existing MetricProducers
// [metricToActivationMap]:  map from metric id to metric activation index
// [replacedMatchers]: set of replaced matcher ids. metrics using these matchers must be replaced
// [replacedConditions]: set of replaced conditions. metrics using these conditions must be replaced
// [replacedStates]: set of replaced state ids. metrics using these states must be replaced
// output:
// [metricsToUpdate]: update status of each metric. Will be changed from UPDATE_UNKNOWN
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> determineAllMetricUpdateStatuses(
        const StatsdConfig& config, const unordered_map<int64_t, int>& oldMetricProducerMap,
        const vector<sp<MetricProducer>>& oldMetricProducers,
        const unordered_map<int64_t, int>& metricToActivationMap,
        const set<int64_t>& replacedMatchers, const set<int64_t>& replacedConditions,
        const set<int64_t>& replacedStates, vector<UpdateStatus>& metricsToUpdate);

// Update MetricProducers.
// input:
// [key]: the config key that this config belongs to
// [config]: the input config
// [timeBaseNs]: start time base for all metrics
// [currentTimeNs]: time of the config update
// [atomMatchingTrackerMap]: AtomMatchingTracker name to index mapping from previous step.
// [replacedMatchers]: ids of replaced matchers. Metrics depending on these must also be replaced
// [allAtomMatchingTrackers]: stores the sp of the atom matchers.
// [conditionTrackerMap]: condition name to index mapping
// [replacedConditions]: set of condition ids that have changed and have been replaced
// [stateAtomIdMap]: contains the mapping from state ids to atom ids
// [allStateGroupMaps]: contains the mapping from atom ids and state values to
//                      state group ids for all states
// output:
// [allMetricProducers]: contains the list of sp to the MetricProducers created.
// [conditionToMetricMap]: contains the mapping from condition tracker index to
//                          the list of MetricProducer index
// [trackerToMetricMap]: contains the mapping from log tracker to MetricProducer index.
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> updateMetrics(
        const ConfigKey& key, const StatsdConfig& config, int64_t timeBaseNs,
        const int64_t currentTimeNs, const sp<StatsPullerManager>& pullerManager,
        const std::unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
        const std::unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
        const std::set<int64_t>& replacedMatchers,
        const std::vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const std::unordered_map<int64_t, int>& conditionTrackerMap,
        const std::set<int64_t>& replacedConditions,
        std::vector<sp<ConditionTracker>>& allConditionTrackers,
        const std::vector<ConditionState>& initialConditionCache,
        const std::unordered_map<int64_t, int>& stateAtomIdMap,
        const std::unordered_map<int64_t, std::unordered_map<int, int64_t>>& allStateGroupMaps,
        const std::set<int64_t>& replacedStates,
        const std::unordered_map<int64_t, int>& oldMetricProducerMap,
        const std::vector<sp<MetricProducer>>& oldMetricProducers,
        std::unordered_map<int64_t, int>& newMetricProducerMap,
        std::vector<sp<MetricProducer>>& newMetricProducers,
        std::unordered_map<int, std::vector<int>>& conditionToMetricMap,
        std::unordered_map<int, std::vector<int>>& trackerToMetricMap,
        std::set<int64_t>& noReportMetricIds,
        std::unordered_map<int, std::vector<int>>& activationAtomTrackerToMetricMap,
        std::unordered_map<int, std::vector<int>>& deactivationAtomTrackerToMetricMap,
        std::vector<int>& metricsWithActivation, std::set<int64_t>& replacedMetrics);

// Function to determine the update status (preserve/replace/new) of an alert.
// [alert]: the input Alert
// [oldAlertTrackerMap]: alert id to index mapping in the existing MetricsManager
// [oldAnomalyTrackers]: stores the existing AnomalyTrackers
// [replacedMetrics]: set of replaced metric ids. alerts using these metrics must be replaced
// output:
// [updateStatus]: update status of the alert. Will be changed from UPDATE_UNKNOWN
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> determineAlertUpdateStatus(
        const Alert& alert, const std::unordered_map<int64_t, int>& oldAlertTrackerMap,
        const std::vector<sp<AnomalyTracker>>& oldAnomalyTrackers,
        const std::set<int64_t>& replacedMetrics, UpdateStatus& updateStatus);

// Update MetricProducers.
// input:
// [config]: the input config
// [currentTimeNs]: time of the config update
// [metricProducerMap]: metric id to index mapping in the new config
// [replacedMetrics]: set of metric ids that have changed and were replaced
// [oldAlertTrackerMap]: alert id to index mapping in the existing MetricsManager.
// [oldAnomalyTrackers]: stores the existing AnomalyTrackers
// [anomalyAlarmMonitor]: AlarmMonitor used for duration metric anomaly detection
// [allMetricProducers]: stores the sp of the metric producers, AnomalyTrackers need to be added.
// [stateAtomIdMap]: contains the mapping from state ids to atom ids
// [allStateGroupMaps]: contains the mapping from atom ids and state values to
//                      state group ids for all states
// output:
// [newAlertTrackerMap]: mapping of alert id to index in the new config
// [newAnomalyTrackers]: contains the list of sp to the AnomalyTrackers created.
// Returns nullopt if successful and InvalidConfigReason if not.
optional<InvalidConfigReason> updateAlerts(
        const StatsdConfig& config, int64_t currentTimeNs,
        const std::unordered_map<int64_t, int>& metricProducerMap,
        const std::set<int64_t>& replacedMetrics,
        const std::unordered_map<int64_t, int>& oldAlertTrackerMap,
        const std::vector<sp<AnomalyTracker>>& oldAnomalyTrackers,
        const sp<AlarmMonitor>& anomalyAlarmMonitor,
        std::vector<sp<MetricProducer>>& allMetricProducers,
        std::unordered_map<int64_t, int>& newAlertTrackerMap,
        std::vector<sp<AnomalyTracker>>& newAnomalyTrackers);

// Updates the existing MetricsManager from a new StatsdConfig.
// Parameters are the members of MetricsManager. See MetricsManager for declaration.
optional<InvalidConfigReason> updateStatsdConfig(
        const ConfigKey& key, const StatsdConfig& config, const sp<UidMap>& uidMap,
        const sp<StatsPullerManager>& pullerManager, const sp<AlarmMonitor>& anomalyAlarmMonitor,
        const sp<AlarmMonitor>& periodicAlarmMonitor, int64_t timeBaseNs,
        const int64_t currentTimeNs,
        const std::vector<sp<AtomMatchingTracker>>& oldAtomMatchingTrackers,
        const std::unordered_map<int64_t, int>& oldAtomMatchingTrackerMap,
        const std::vector<sp<ConditionTracker>>& oldConditionTrackers,
        const std::unordered_map<int64_t, int>& oldConditionTrackerMap,
        const std::vector<sp<MetricProducer>>& oldMetricProducers,
        const std::unordered_map<int64_t, int>& oldMetricProducerMap,
        const std::vector<sp<AnomalyTracker>>& oldAnomalyTrackers,
        const std::unordered_map<int64_t, int>& oldAlertTrackerMap,
        const std::map<int64_t, uint64_t>& oldStateProtoHashes,
        std::unordered_map<int, std::vector<int>>& allTagIdsToMatchersMap,
        std::vector<sp<AtomMatchingTracker>>& newAtomMatchingTrackers,
        std::unordered_map<int64_t, int>& newAtomMatchingTrackerMap,
        std::vector<sp<ConditionTracker>>& newConditionTrackers,
        std::unordered_map<int64_t, int>& newConditionTrackerMap,
        std::vector<sp<MetricProducer>>& newMetricProducers,
        std::unordered_map<int64_t, int>& newMetricProducerMap,
        std::vector<sp<AnomalyTracker>>& newAlertTrackers,
        std::unordered_map<int64_t, int>& newAlertTrackerMap,
        std::vector<sp<AlarmTracker>>& newPeriodicAlarmTrackers,
        std::unordered_map<int, std::vector<int>>& conditionToMetricMap,
        std::unordered_map<int, std::vector<int>>& trackerToMetricMap,
        std::unordered_map<int, std::vector<int>>& trackerToConditionMap,
        std::unordered_map<int, std::vector<int>>& activationTrackerToMetricMap,
        std::unordered_map<int, std::vector<int>>& deactivationTrackerToMetricMap,
        std::vector<int>& metricsWithActivation, std::map<int64_t, uint64_t>& newStateProtoHashes,
        std::set<int64_t>& noReportMetricIds);

}  // namespace statsd
}  // namespace os
}  // namespace android
