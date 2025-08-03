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

#include "MetricsManager.h"

#include <private/android_filesystem_config.h>

#include "CountMetricProducer.h"
#include "condition/CombinationConditionTracker.h"
#include "condition/SimpleConditionTracker.h"
#include "flags/FlagProvider.h"
#include "guardrail/StatsdStats.h"
#include "matchers/CombinationAtomMatchingTracker.h"
#include "matchers/SimpleAtomMatchingTracker.h"
#include "parsing_utils/config_update_utils.h"
#include "parsing_utils/metrics_manager_util.h"
#include "state/StateManager.h"
#include "stats_log_util.h"
#include "stats_util.h"
#include "statslog_statsd.h"
#include "utils/DbUtils.h"

using android::util::FIELD_COUNT_REPEATED;
using android::util::FIELD_TYPE_INT32;
using android::util::FIELD_TYPE_INT64;
using android::util::FIELD_TYPE_MESSAGE;
using android::util::FIELD_TYPE_STRING;
using android::util::ProtoOutputStream;

using std::set;
using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace os {
namespace statsd {

const int FIELD_ID_METRICS = 1;
const int FIELD_ID_ANNOTATIONS = 7;
const int FIELD_ID_ANNOTATIONS_INT64 = 1;
const int FIELD_ID_ANNOTATIONS_INT32 = 2;

// for ActiveConfig
const int FIELD_ID_ACTIVE_CONFIG_ID = 1;
const int FIELD_ID_ACTIVE_CONFIG_UID = 2;
const int FIELD_ID_ACTIVE_CONFIG_METRIC = 3;

MetricsManager::MetricsManager(const ConfigKey& key, const StatsdConfig& config,
                               const int64_t timeBaseNs, const int64_t currentTimeNs,
                               const sp<UidMap>& uidMap,
                               const sp<StatsPullerManager>& pullerManager,
                               const sp<AlarmMonitor>& anomalyAlarmMonitor,
                               const sp<AlarmMonitor>& periodicAlarmMonitor)
    : mConfigKey(key),
      mUidMap(uidMap),
      mPackageCertificateHashSizeBytes(
              static_cast<uint8_t>(config.package_certificate_hash_size_bytes())),
      mTtlNs(config.has_ttl_in_seconds() ? config.ttl_in_seconds() * NS_PER_SEC : -1),
      mTtlEndNs(-1),
      mLastReportTimeNs(currentTimeNs),
      mLastReportWallClockNs(getWallClockNs()),
      mPullerManager(pullerManager),
      mWhitelistedAtomIds(config.whitelisted_atom_ids().begin(),
                          config.whitelisted_atom_ids().end()),
      mShouldPersistHistory(config.persist_locally()) {
    if (!isAtLeastU() && config.has_restricted_metrics_delegate_package_name()) {
        mInvalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_ENABLED);
        return;
    }
    if (config.has_restricted_metrics_delegate_package_name()) {
        mRestrictedMetricsDelegatePackageName = config.restricted_metrics_delegate_package_name();
    }
    // Init the ttl end timestamp.
    refreshTtl(timeBaseNs);
    mInvalidConfigReason = initStatsdConfig(
            key, config, uidMap, pullerManager, anomalyAlarmMonitor, periodicAlarmMonitor,
            timeBaseNs, currentTimeNs, mTagIdsToMatchersMap, mAllAtomMatchingTrackers,
            mAtomMatchingTrackerMap, mAllConditionTrackers, mConditionTrackerMap,
            mAllMetricProducers, mMetricProducerMap, mAllAnomalyTrackers, mAllPeriodicAlarmTrackers,
            mConditionToMetricMap, mTrackerToMetricMap, mTrackerToConditionMap,
            mActivationAtomTrackerToMetricMap, mDeactivationAtomTrackerToMetricMap,
            mAlertTrackerMap, mMetricIndexesWithActivation, mStateProtoHashes, mNoReportMetricIds);

    mHashStringsInReport = config.hash_strings_in_metric_report();
    mVersionStringsInReport = config.version_strings_in_metric_report();
    mInstallerInReport = config.installer_in_metric_report();

    createAllLogSourcesFromConfig(config);
    setMaxMetricsBytesFromConfig(config);
    setTriggerGetDataBytesFromConfig(config);
    mPullerManager->RegisterPullUidProvider(mConfigKey, this);

    // Store the sub-configs used.
    for (const auto& annotation : config.annotation()) {
        mAnnotations.emplace_back(annotation.field_int64(), annotation.field_int32());
    }
    verifyGuardrailsAndUpdateStatsdStats();
    initializeConfigActiveStatus();
}

MetricsManager::~MetricsManager() {
    for (auto it : mAllMetricProducers) {
        for (int atomId : it->getSlicedStateAtoms()) {
            StateManager::getInstance().unregisterListener(atomId, it);
        }
    }
    mPullerManager->UnregisterPullUidProvider(mConfigKey, this);

    VLOG("~MetricsManager()");
}

bool MetricsManager::updateConfig(const StatsdConfig& config, const int64_t timeBaseNs,
                                  const int64_t currentTimeNs,
                                  const sp<AlarmMonitor>& anomalyAlarmMonitor,
                                  const sp<AlarmMonitor>& periodicAlarmMonitor) {
    if (!isAtLeastU() && config.has_restricted_metrics_delegate_package_name()) {
        mInvalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_ENABLED);
        return false;
    }
    if (config.has_restricted_metrics_delegate_package_name()) {
        mRestrictedMetricsDelegatePackageName = config.restricted_metrics_delegate_package_name();
    } else {
        mRestrictedMetricsDelegatePackageName = nullopt;
    }
    vector<sp<AtomMatchingTracker>> newAtomMatchingTrackers;
    unordered_map<int64_t, int> newAtomMatchingTrackerMap;
    vector<sp<ConditionTracker>> newConditionTrackers;
    unordered_map<int64_t, int> newConditionTrackerMap;
    map<int64_t, uint64_t> newStateProtoHashes;
    vector<sp<MetricProducer>> newMetricProducers;
    unordered_map<int64_t, int> newMetricProducerMap;
    vector<sp<AnomalyTracker>> newAnomalyTrackers;
    unordered_map<int64_t, int> newAlertTrackerMap;
    vector<sp<AlarmTracker>> newPeriodicAlarmTrackers;
    mTagIdsToMatchersMap.clear();
    mConditionToMetricMap.clear();
    mTrackerToMetricMap.clear();
    mTrackerToConditionMap.clear();
    mActivationAtomTrackerToMetricMap.clear();
    mDeactivationAtomTrackerToMetricMap.clear();
    mMetricIndexesWithActivation.clear();
    mNoReportMetricIds.clear();
    mInvalidConfigReason = updateStatsdConfig(
            mConfigKey, config, mUidMap, mPullerManager, anomalyAlarmMonitor, periodicAlarmMonitor,
            timeBaseNs, currentTimeNs, mAllAtomMatchingTrackers, mAtomMatchingTrackerMap,
            mAllConditionTrackers, mConditionTrackerMap, mAllMetricProducers, mMetricProducerMap,
            mAllAnomalyTrackers, mAlertTrackerMap, mStateProtoHashes, mTagIdsToMatchersMap,
            newAtomMatchingTrackers, newAtomMatchingTrackerMap, newConditionTrackers,
            newConditionTrackerMap, newMetricProducers, newMetricProducerMap, newAnomalyTrackers,
            newAlertTrackerMap, newPeriodicAlarmTrackers, mConditionToMetricMap,
            mTrackerToMetricMap, mTrackerToConditionMap, mActivationAtomTrackerToMetricMap,
            mDeactivationAtomTrackerToMetricMap, mMetricIndexesWithActivation, newStateProtoHashes,
            mNoReportMetricIds);
    mAllAtomMatchingTrackers = newAtomMatchingTrackers;
    mAtomMatchingTrackerMap = newAtomMatchingTrackerMap;
    mAllConditionTrackers = newConditionTrackers;
    mConditionTrackerMap = newConditionTrackerMap;
    mAllMetricProducers = newMetricProducers;
    mMetricProducerMap = newMetricProducerMap;
    mStateProtoHashes = newStateProtoHashes;
    mAllAnomalyTrackers = newAnomalyTrackers;
    mAlertTrackerMap = newAlertTrackerMap;
    mAllPeriodicAlarmTrackers = newPeriodicAlarmTrackers;

    mTtlNs = config.has_ttl_in_seconds() ? config.ttl_in_seconds() * NS_PER_SEC : -1;
    refreshTtl(currentTimeNs);

    mHashStringsInReport = config.hash_strings_in_metric_report();
    mVersionStringsInReport = config.version_strings_in_metric_report();
    mInstallerInReport = config.installer_in_metric_report();
    mWhitelistedAtomIds.clear();
    mWhitelistedAtomIds.insert(config.whitelisted_atom_ids().begin(),
                               config.whitelisted_atom_ids().end());
    mShouldPersistHistory = config.persist_locally();
    mPackageCertificateHashSizeBytes = config.package_certificate_hash_size_bytes();

    // Store the sub-configs used.
    mAnnotations.clear();
    for (const auto& annotation : config.annotation()) {
        mAnnotations.emplace_back(annotation.field_int64(), annotation.field_int32());
    }

    mAllowedUid.clear();
    mAllowedPkg.clear();
    mDefaultPullUids.clear();
    mPullAtomUids.clear();
    mPullAtomPackages.clear();
    createAllLogSourcesFromConfig(config);
    setMaxMetricsBytesFromConfig(config);
    setTriggerGetDataBytesFromConfig(config);

    verifyGuardrailsAndUpdateStatsdStats();
    initializeConfigActiveStatus();
    return !mInvalidConfigReason.has_value();
}

void MetricsManager::createAllLogSourcesFromConfig(const StatsdConfig& config) {
    // Init allowed pushed atom uids.
    for (const auto& source : config.allowed_log_source()) {
        auto it = UidMap::sAidToUidMapping.find(source);
        if (it != UidMap::sAidToUidMapping.end()) {
            mAllowedUid.push_back(it->second);
        } else {
            mAllowedPkg.push_back(source);
        }
    }

    if (mAllowedUid.size() + mAllowedPkg.size() > StatsdStats::kMaxLogSourceCount) {
        ALOGE("Too many log sources. This is likely to be an error in the config.");
        mInvalidConfigReason = InvalidConfigReason(INVALID_CONFIG_REASON_TOO_MANY_LOG_SOURCES);
    } else {
        initAllowedLogSources();
    }

    // Init default allowed pull atom uids.
    int numPullPackages = 0;
    for (const string& pullSource : config.default_pull_packages()) {
        auto it = UidMap::sAidToUidMapping.find(pullSource);
        if (it != UidMap::sAidToUidMapping.end()) {
            numPullPackages++;
            mDefaultPullUids.insert(it->second);
        } else {
            ALOGE("Default pull atom packages must be in sAidToUidMapping");
            mInvalidConfigReason =
                    InvalidConfigReason(INVALID_CONFIG_REASON_DEFAULT_PULL_PACKAGES_NOT_IN_MAP);
        }
    }
    // Init per-atom pull atom packages.
    for (const PullAtomPackages& pullAtomPackages : config.pull_atom_packages()) {
        int32_t atomId = pullAtomPackages.atom_id();
        for (const string& pullPackage : pullAtomPackages.packages()) {
            numPullPackages++;
            auto it = UidMap::sAidToUidMapping.find(pullPackage);
            if (it != UidMap::sAidToUidMapping.end()) {
                mPullAtomUids[atomId].insert(it->second);
            } else {
                mPullAtomPackages[atomId].insert(pullPackage);
            }
        }
    }
    if (numPullPackages > StatsdStats::kMaxPullAtomPackages) {
        ALOGE("Too many sources in default_pull_packages and pull_atom_packages. This is likely to "
              "be an error in the config");
        mInvalidConfigReason =
                InvalidConfigReason(INVALID_CONFIG_REASON_TOO_MANY_SOURCES_IN_PULL_PACKAGES);
    } else {
        initPullAtomSources();
    }
}

void MetricsManager::setMaxMetricsBytesFromConfig(const StatsdConfig& config) {
    if (!config.has_max_metrics_memory_kb()) {
        mMaxMetricsBytes = StatsdStats::kDefaultMaxMetricsBytesPerConfig;
        return;
    }
    if (config.max_metrics_memory_kb() <= 0 ||
        static_cast<size_t>(config.max_metrics_memory_kb() * 1024) >
                StatsdStats::kHardMaxMetricsBytesPerConfig) {
        ALOGW("Memory limit must be between 0KB and 20MB. Setting to default value (2MB).");
        mMaxMetricsBytes = StatsdStats::kDefaultMaxMetricsBytesPerConfig;
    } else {
        mMaxMetricsBytes = config.max_metrics_memory_kb() * 1024;
    }
}

void MetricsManager::setTriggerGetDataBytesFromConfig(const StatsdConfig& config) {
    if (!config.has_soft_metrics_memory_kb()) {
        mTriggerGetDataBytes = StatsdStats::kDefaultBytesPerConfigTriggerGetData;
        return;
    }
    if (config.soft_metrics_memory_kb() <= 0 ||
        static_cast<size_t>(config.soft_metrics_memory_kb() * 1024) >
                StatsdStats::kHardMaxTriggerGetDataBytes) {
        ALOGW("Memory limit ust be between 0KB and 10MB. Setting to default value (192KB).");
        mTriggerGetDataBytes = StatsdStats::kDefaultBytesPerConfigTriggerGetData;
    } else {
        mTriggerGetDataBytes = config.soft_metrics_memory_kb() * 1024;
    }
}

void MetricsManager::verifyGuardrailsAndUpdateStatsdStats() {
    // Guardrail. Reject the config if it's too big.
    if (mAllMetricProducers.size() > StatsdStats::kMaxMetricCountPerConfig) {
        ALOGE("This config has too many metrics! Reject!");
        mInvalidConfigReason = InvalidConfigReason(INVALID_CONFIG_REASON_TOO_MANY_METRICS);
    }
    if (mAllConditionTrackers.size() > StatsdStats::kMaxConditionCountPerConfig) {
        ALOGE("This config has too many predicates! Reject!");
        mInvalidConfigReason = InvalidConfigReason(INVALID_CONFIG_REASON_TOO_MANY_CONDITIONS);
    }
    if (mAllAtomMatchingTrackers.size() > StatsdStats::kMaxMatcherCountPerConfig) {
        ALOGE("This config has too many matchers! Reject!");
        mInvalidConfigReason = InvalidConfigReason(INVALID_CONFIG_REASON_TOO_MANY_MATCHERS);
    }
    if (mAllAnomalyTrackers.size() > StatsdStats::kMaxAlertCountPerConfig) {
        ALOGE("This config has too many alerts! Reject!");
        mInvalidConfigReason = InvalidConfigReason(INVALID_CONFIG_REASON_TOO_MANY_ALERTS);
    }
    // no matter whether this config is valid, log it in the stats.
    StatsdStats::getInstance().noteConfigReceived(
            mConfigKey, mAllMetricProducers.size(), mAllConditionTrackers.size(),
            mAllAtomMatchingTrackers.size(), mAllAnomalyTrackers.size(), mAnnotations,
            mInvalidConfigReason);
}

void MetricsManager::initializeConfigActiveStatus() {
    mIsAlwaysActive = (mMetricIndexesWithActivation.size() != mAllMetricProducers.size()) ||
                      (mAllMetricProducers.size() == 0);
    mIsActive = mIsAlwaysActive;
    for (int metric : mMetricIndexesWithActivation) {
        mIsActive |= mAllMetricProducers[metric]->isActive();
    }
    VLOG("mIsActive is initialized to %d", mIsActive);
}

void MetricsManager::initAllowedLogSources() {
    std::lock_guard<std::mutex> lock(mAllowedLogSourcesMutex);
    mAllowedLogSources.clear();
    mAllowedLogSources.insert(mAllowedUid.begin(), mAllowedUid.end());

    for (const auto& pkg : mAllowedPkg) {
        auto uids = mUidMap->getAppUid(pkg);
        mAllowedLogSources.insert(uids.begin(), uids.end());
    }
    if (STATSD_DEBUG) {
        for (const auto& uid : mAllowedLogSources) {
            VLOG("Allowed uid %d", uid);
        }
    }
}

void MetricsManager::initPullAtomSources() {
    std::lock_guard<std::mutex> lock(mAllowedLogSourcesMutex);
    mCombinedPullAtomUids.clear();
    for (const auto& [atomId, uids] : mPullAtomUids) {
        mCombinedPullAtomUids[atomId].insert(uids.begin(), uids.end());
    }
    for (const auto& [atomId, packages] : mPullAtomPackages) {
        for (const string& pkg : packages) {
            set<int32_t> uids = mUidMap->getAppUid(pkg);
            mCombinedPullAtomUids[atomId].insert(uids.begin(), uids.end());
        }
    }
}

bool MetricsManager::isConfigValid() const {
    return !mInvalidConfigReason.has_value();
}

void MetricsManager::notifyAppUpgrade(const int64_t eventTimeNs, const string& apk, const int uid,
                                      const int64_t version) {
    // Inform all metric producers.
    for (const auto& it : mAllMetricProducers) {
        it->notifyAppUpgrade(eventTimeNs);
    }
    // check if we care this package
    if (std::find(mAllowedPkg.begin(), mAllowedPkg.end(), apk) != mAllowedPkg.end()) {
        // We will re-initialize the whole list because we don't want to keep the multi mapping of
        // UID<->pkg inside MetricsManager to reduce the memory usage.
        initAllowedLogSources();
    }

    for (const auto& it : mPullAtomPackages) {
        if (it.second.find(apk) != it.second.end()) {
            initPullAtomSources();
            return;
        }
    }
}

void MetricsManager::notifyAppRemoved(const int64_t eventTimeNs, const string& apk, const int uid) {
    // Inform all metric producers.
    for (const auto& it : mAllMetricProducers) {
        it->notifyAppRemoved(eventTimeNs);
    }
    // check if we care this package
    if (std::find(mAllowedPkg.begin(), mAllowedPkg.end(), apk) != mAllowedPkg.end()) {
        // We will re-initialize the whole list because we don't want to keep the multi mapping of
        // UID<->pkg inside MetricsManager to reduce the memory usage.
        initAllowedLogSources();
    }

    for (const auto& it : mPullAtomPackages) {
        if (it.second.find(apk) != it.second.end()) {
            initPullAtomSources();
            return;
        }
    }
}

void MetricsManager::onUidMapReceived(const int64_t eventTimeNs) {
    // Purposefully don't inform metric producers on a new snapshot
    // because we don't need to flush partial buckets.
    // This occurs if a new user is added/removed or statsd crashes.
    initPullAtomSources();

    if (mAllowedPkg.size() == 0) {
        return;
    }
    initAllowedLogSources();
}

void MetricsManager::onStatsdInitCompleted(const int64_t eventTimeNs) {
    // Inform all metric producers.
    for (const auto& it : mAllMetricProducers) {
        it->onStatsdInitCompleted(eventTimeNs);
    }
}

void MetricsManager::init() {
    for (const auto& producer : mAllMetricProducers) {
        producer->prepareFirstBucket();
    }
}

vector<int32_t> MetricsManager::getPullAtomUids(int32_t atomId) {
    std::lock_guard<std::mutex> lock(mAllowedLogSourcesMutex);
    vector<int32_t> uids;
    const auto& it = mCombinedPullAtomUids.find(atomId);
    if (it != mCombinedPullAtomUids.end()) {
        uids.insert(uids.end(), it->second.begin(), it->second.end());
    }
    uids.insert(uids.end(), mDefaultPullUids.begin(), mDefaultPullUids.end());
    return uids;
}

void MetricsManager::dumpStates(int out, bool verbose) {
    dprintf(out, "ConfigKey %s, allowed source:", mConfigKey.ToString().c_str());
    {
        std::lock_guard<std::mutex> lock(mAllowedLogSourcesMutex);
        for (const auto& source : mAllowedLogSources) {
            dprintf(out, "%d ", source);
        }
    }
    dprintf(out, "\n");
    for (const auto& producer : mAllMetricProducers) {
        producer->dumpStates(out, verbose);
    }
}

void MetricsManager::dropData(const int64_t dropTimeNs) {
    for (const auto& producer : mAllMetricProducers) {
        producer->dropData(dropTimeNs);
    }
}

void MetricsManager::onDumpReport(const int64_t dumpTimeStampNs, const int64_t wallClockNs,
                                  const bool include_current_partial_bucket, const bool erase_data,
                                  const DumpLatency dumpLatency, std::set<string>* str_set,
                                  ProtoOutputStream* protoOutput) {
    if (hasRestrictedMetricsDelegate()) {
        // TODO(b/268150038): report error to statsdstats
        VLOG("Unexpected call to onDumpReport in restricted metricsmanager.");
        return;
    }
    VLOG("=========================Metric Reports Start==========================");
    // one StatsLogReport per MetricProduer
    for (const auto& producer : mAllMetricProducers) {
        if (mNoReportMetricIds.find(producer->getMetricId()) == mNoReportMetricIds.end()) {
            uint64_t token = protoOutput->start(
                    FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | FIELD_ID_METRICS);
            if (mHashStringsInReport) {
                producer->onDumpReport(dumpTimeStampNs, include_current_partial_bucket, erase_data,
                                       dumpLatency, str_set, protoOutput);
            } else {
                producer->onDumpReport(dumpTimeStampNs, include_current_partial_bucket, erase_data,
                                       dumpLatency, nullptr, protoOutput);
            }
            protoOutput->end(token);
        } else {
            producer->clearPastBuckets(dumpTimeStampNs);
        }
    }
    for (const auto& annotation : mAnnotations) {
        uint64_t token = protoOutput->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                                            FIELD_ID_ANNOTATIONS);
        protoOutput->write(FIELD_TYPE_INT64 | FIELD_ID_ANNOTATIONS_INT64,
                           (long long)annotation.first);
        protoOutput->write(FIELD_TYPE_INT32 | FIELD_ID_ANNOTATIONS_INT32, annotation.second);
        protoOutput->end(token);
    }

    // Do not update the timestamps when data is not cleared to avoid timestamps from being
    // misaligned.
    if (erase_data) {
        mLastReportTimeNs = dumpTimeStampNs;
        mLastReportWallClockNs = wallClockNs;
    }
    VLOG("=========================Metric Reports End==========================");
}

bool MetricsManager::checkLogCredentials(const LogEvent& event) {
    if (mWhitelistedAtomIds.find(event.GetTagId()) != mWhitelistedAtomIds.end()) {
        return true;
    }

    if (event.GetUid() == AID_ROOT ||
        (event.GetUid() >= AID_SYSTEM && event.GetUid() < AID_SHELL)) {
        // enable atoms logged from pre-installed Android system services
        return true;
    }

    std::lock_guard<std::mutex> lock(mAllowedLogSourcesMutex);
    if (mAllowedLogSources.find(event.GetUid()) == mAllowedLogSources.end()) {
        VLOG("log source %d not on the whitelist", event.GetUid());
        return false;
    }
    return true;
}

// Consume the stats log if it's interesting to this metric.
void MetricsManager::onLogEvent(const LogEvent& event) {
    if (!isConfigValid()) {
        return;
    }

    if (!checkLogCredentials(event)) {
        return;
    }

    const int tagId = event.GetTagId();
    const int64_t eventTimeNs = event.GetElapsedTimestampNs();

    bool isActive = mIsAlwaysActive;

    // Set of metrics that are still active after flushing.
    unordered_set<int> activeMetricsIndices;

    // Update state of all metrics w/ activation conditions as of eventTimeNs.
    for (int metricIndex : mMetricIndexesWithActivation) {
        const sp<MetricProducer>& metric = mAllMetricProducers[metricIndex];
        metric->flushIfExpire(eventTimeNs);
        if (metric->isActive()) {
            // If this metric w/ activation condition is still active after
            // flushing, remember it.
            activeMetricsIndices.insert(metricIndex);
        }
    }

    mIsActive = isActive || !activeMetricsIndices.empty();

    const auto matchersIt = mTagIdsToMatchersMap.find(tagId);

    if (matchersIt == mTagIdsToMatchersMap.end()) {
        // Not interesting...
        return;
    }

    if (event.isParsedHeaderOnly()) {
        // This should not happen if metric config is defined for certain atom id
        const int64_t firstMatcherId =
                mAllAtomMatchingTrackers[*matchersIt->second.begin()]->getId();
        ALOGW("Atom %d is mistakenly skipped - there is a matcher %lld for it", tagId,
              (long long)firstMatcherId);
        return;
    }

    vector<MatchingState> matcherCache(mAllAtomMatchingTrackers.size(),
                                       MatchingState::kNotComputed);
    vector<shared_ptr<LogEvent>> matcherTransformations(matcherCache.size(), nullptr);

    for (const auto& matcherIndex : matchersIt->second) {
        mAllAtomMatchingTrackers[matcherIndex]->onLogEvent(event, matcherIndex,
                                                           mAllAtomMatchingTrackers, matcherCache,
                                                           matcherTransformations);
    }

    // Set of metrics that received an activation cancellation.
    unordered_set<int> metricIndicesWithCanceledActivations;

    // Determine which metric activations received a cancellation and cancel them.
    for (const auto& it : mDeactivationAtomTrackerToMetricMap) {
        if (matcherCache[it.first] == MatchingState::kMatched) {
            for (int metricIndex : it.second) {
                mAllMetricProducers[metricIndex]->cancelEventActivation(it.first);
                metricIndicesWithCanceledActivations.insert(metricIndex);
            }
        }
    }

    // Determine whether any metrics are no longer active after cancelling metric activations.
    for (const int metricIndex : metricIndicesWithCanceledActivations) {
        const sp<MetricProducer>& metric = mAllMetricProducers[metricIndex];
        metric->flushIfExpire(eventTimeNs);
        if (!metric->isActive()) {
            activeMetricsIndices.erase(metricIndex);
        }
    }

    isActive |= !activeMetricsIndices.empty();


    // Determine which metric activations should be turned on and turn them on
    for (const auto& it : mActivationAtomTrackerToMetricMap) {
        if (matcherCache[it.first] == MatchingState::kMatched) {
            for (int metricIndex : it.second) {
                mAllMetricProducers[metricIndex]->activate(it.first, eventTimeNs);
                isActive |= mAllMetricProducers[metricIndex]->isActive();
            }
        }
    }

    mIsActive = isActive;

    // A bitmap to see which ConditionTracker needs to be re-evaluated.
    vector<uint8_t> conditionToBeEvaluated(mAllConditionTrackers.size(), false);
    vector<shared_ptr<LogEvent>> conditionToTransformedLogEvents(mAllConditionTrackers.size(),
                                                                 nullptr);

    for (const auto& [matcherIndex, conditionList] : mTrackerToConditionMap) {
        if (matcherCache[matcherIndex] == MatchingState::kMatched) {
            for (const int conditionIndex : conditionList) {
                conditionToBeEvaluated[conditionIndex] = true;
                conditionToTransformedLogEvents[conditionIndex] =
                        matcherTransformations[matcherIndex];
            }
        }
    }

    vector<ConditionState> conditionCache(mAllConditionTrackers.size(),
                                          ConditionState::kNotEvaluated);
    // A bitmap to track if a condition has changed value.
    vector<uint8_t> changedCache(mAllConditionTrackers.size(), false);
    for (size_t i = 0; i < mAllConditionTrackers.size(); i++) {
        if (!conditionToBeEvaluated[i]) {
            continue;
        }
        sp<ConditionTracker>& condition = mAllConditionTrackers[i];
        const LogEvent& conditionEvent = conditionToTransformedLogEvents[i] == nullptr
                                                 ? event
                                                 : *conditionToTransformedLogEvents[i];
        condition->evaluateCondition(conditionEvent, matcherCache, mAllConditionTrackers,
                                     conditionCache, changedCache);
    }

    for (size_t i = 0; i < mAllConditionTrackers.size(); i++) {
        if (!changedCache[i]) {
            continue;
        }
        auto it = mConditionToMetricMap.find(i);
        if (it == mConditionToMetricMap.end()) {
            continue;
        }
        auto& metricList = it->second;
        for (auto metricIndex : metricList) {
            // Metric cares about non sliced condition, and it's changed.
            // Push the new condition to it directly.
            if (!mAllMetricProducers[metricIndex]->isConditionSliced()) {
                mAllMetricProducers[metricIndex]->onConditionChanged(conditionCache[i],
                                                                     eventTimeNs);
                // Metric cares about sliced conditions, and it may have changed. Send
                // notification, and the metric can query the sliced conditions that are
                // interesting to it.
            } else {
                mAllMetricProducers[metricIndex]->onSlicedConditionMayChange(conditionCache[i],
                                                                             eventTimeNs);
            }
        }
    }
    // For matched AtomMatchers, tell relevant metrics that a matched event has come.
    for (size_t i = 0; i < mAllAtomMatchingTrackers.size(); i++) {
        if (matcherCache[i] == MatchingState::kMatched) {
            StatsdStats::getInstance().noteMatcherMatched(mConfigKey,
                                                          mAllAtomMatchingTrackers[i]->getId());
            auto it = mTrackerToMetricMap.find(i);
            if (it == mTrackerToMetricMap.end()) {
                continue;
            }
            auto& metricList = it->second;
            const LogEvent& metricEvent =
                    matcherTransformations[i] == nullptr ? event : *matcherTransformations[i];
            for (const int metricIndex : metricList) {
                // pushed metrics are never scheduled pulls
                mAllMetricProducers[metricIndex]->onMatchedLogEvent(i, metricEvent);
            }
        }
    }
}

void MetricsManager::onAnomalyAlarmFired(
        const int64_t timestampNs,
        unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>>& alarmSet) {
    for (const auto& itr : mAllAnomalyTrackers) {
        itr->informAlarmsFired(timestampNs, alarmSet);
    }
}

void MetricsManager::onPeriodicAlarmFired(
        const int64_t timestampNs,
        unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>>& alarmSet) {
    for (const auto& itr : mAllPeriodicAlarmTrackers) {
        itr->informAlarmsFired(timestampNs, alarmSet);
    }
}

// Returns the total byte size of all metrics managed by a single config source.
size_t MetricsManager::byteSize() {
    size_t totalSize = 0;
    for (const auto& metricProducer : mAllMetricProducers) {
        totalSize += metricProducer->byteSize();
    }
    return totalSize;
}

void MetricsManager::loadActiveConfig(const ActiveConfig& config, int64_t currentTimeNs) {
    if (config.metric_size() == 0) {
        ALOGW("No active metric for config %s", mConfigKey.ToString().c_str());
        return;
    }

    for (int i = 0; i < config.metric_size(); i++) {
        const auto& activeMetric = config.metric(i);
        for (int metricIndex : mMetricIndexesWithActivation) {
            const auto& metric = mAllMetricProducers[metricIndex];
            if (metric->getMetricId() == activeMetric.id()) {
                VLOG("Setting active metric: %lld", (long long)metric->getMetricId());
                metric->loadActiveMetric(activeMetric, currentTimeNs);
                if (!mIsActive && metric->isActive()) {
                    StatsdStats::getInstance().noteActiveStatusChanged(mConfigKey,
                                                                       /*activate=*/ true);
                }
                mIsActive |= metric->isActive();
            }
        }
    }
}

void MetricsManager::writeActiveConfigToProtoOutputStream(
        int64_t currentTimeNs, const DumpReportReason reason, ProtoOutputStream* proto) {
    proto->write(FIELD_TYPE_INT64 | FIELD_ID_ACTIVE_CONFIG_ID, (long long)mConfigKey.GetId());
    proto->write(FIELD_TYPE_INT32 | FIELD_ID_ACTIVE_CONFIG_UID, mConfigKey.GetUid());
    for (int metricIndex : mMetricIndexesWithActivation) {
        const auto& metric = mAllMetricProducers[metricIndex];
        const uint64_t metricToken = proto->start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED |
                FIELD_ID_ACTIVE_CONFIG_METRIC);
        metric->writeActiveMetricToProtoOutputStream(currentTimeNs, reason, proto);
        proto->end(metricToken);
    }
}

bool MetricsManager::writeMetadataToProto(int64_t currentWallClockTimeNs,
                                          int64_t systemElapsedTimeNs,
                                          metadata::StatsMetadata* statsMetadata) {
    bool metadataWritten = false;
    metadata::ConfigKey* configKey = statsMetadata->mutable_config_key();
    configKey->set_config_id(mConfigKey.GetId());
    configKey->set_uid(mConfigKey.GetUid());
    for (const auto& anomalyTracker : mAllAnomalyTrackers) {
        metadata::AlertMetadata* alertMetadata = statsMetadata->add_alert_metadata();
        bool alertWritten = anomalyTracker->writeAlertMetadataToProto(currentWallClockTimeNs,
                systemElapsedTimeNs, alertMetadata);
        if (!alertWritten) {
            statsMetadata->mutable_alert_metadata()->RemoveLast();
        }
        metadataWritten |= alertWritten;
    }

    for (const auto& metricProducer : mAllMetricProducers) {
        metadata::MetricMetadata* metricMetadata = statsMetadata->add_metric_metadata();
        bool metricWritten = metricProducer->writeMetricMetadataToProto(metricMetadata);
        if (!metricWritten) {
            statsMetadata->mutable_metric_metadata()->RemoveLast();
        }
        metadataWritten |= metricWritten;
    }
    return metadataWritten;
}

void MetricsManager::loadMetadata(const metadata::StatsMetadata& metadata,
                                  int64_t currentWallClockTimeNs,
                                  int64_t systemElapsedTimeNs) {
    for (const metadata::AlertMetadata& alertMetadata : metadata.alert_metadata()) {
        int64_t alertId = alertMetadata.alert_id();
        const auto& it = mAlertTrackerMap.find(alertId);
        if (it == mAlertTrackerMap.end()) {
            ALOGE("No anomalyTracker found for alertId %lld", (long long) alertId);
            continue;
        }
        mAllAnomalyTrackers[it->second]->loadAlertMetadata(alertMetadata,
                                                           currentWallClockTimeNs,
                                                           systemElapsedTimeNs);
    }
    for (const metadata::MetricMetadata& metricMetadata : metadata.metric_metadata()) {
        int64_t metricId = metricMetadata.metric_id();
        const auto& it = mMetricProducerMap.find(metricId);
        if (it == mMetricProducerMap.end()) {
            ALOGE("No metricProducer found for metricId %lld", (long long)metricId);
        }
        mAllMetricProducers[it->second]->loadMetricMetadataFromProto(metricMetadata);
    }
}

void MetricsManager::enforceRestrictedDataTtls(const int64_t wallClockNs) {
    if (!hasRestrictedMetricsDelegate()) {
        return;
    }
    sqlite3* db = dbutils::getDb(mConfigKey);
    if (db == nullptr) {
        ALOGE("Failed to open sqlite db");
        dbutils::closeDb(db);
        return;
    }
    for (const auto& producer : mAllMetricProducers) {
        producer->enforceRestrictedDataTtl(db, wallClockNs);
    }
    dbutils::closeDb(db);
}

bool MetricsManager::validateRestrictedMetricsDelegate(const int32_t callingUid) {
    if (!hasRestrictedMetricsDelegate()) {
        return false;
    }

    set<int32_t> possibleUids = mUidMap->getAppUid(mRestrictedMetricsDelegatePackageName.value());

    return possibleUids.find(callingUid) != possibleUids.end();
}

void MetricsManager::flushRestrictedData() {
    if (!hasRestrictedMetricsDelegate()) {
        return;
    }
    int64_t flushStartNs = getElapsedRealtimeNs();
    for (const auto& producer : mAllMetricProducers) {
        producer->flushRestrictedData();
    }
    StatsdStats::getInstance().noteRestrictedConfigFlushLatency(
            mConfigKey, getElapsedRealtimeNs() - flushStartNs);
}

vector<int64_t> MetricsManager::getAllMetricIds() const {
    vector<int64_t> metricIds;
    metricIds.reserve(mMetricProducerMap.size());
    for (const auto& [metricId, _] : mMetricProducerMap) {
        metricIds.push_back(metricId);
    }
    return metricIds;
}

void MetricsManager::addAllAtomIds(LogEventFilter::AtomIdSet& allIds) const {
    for (const auto& [atomId, _] : mTagIdsToMatchersMap) {
        allIds.insert(atomId);
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
