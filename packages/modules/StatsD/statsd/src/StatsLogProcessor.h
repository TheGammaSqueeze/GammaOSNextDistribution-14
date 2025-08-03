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

#pragma once

#include <aidl/android/os/BnStatsd.h>
#include <gtest/gtest_prod.h>
#include <stdio.h>

#include <unordered_map>

#include "config/ConfigListener.h"
#include "external/StatsPullerManager.h"
#include "logd/LogEvent.h"
#include "metrics/MetricsManager.h"
#include "packages/UidMap.h"
#include "socket/LogEventFilter.h"
#include "src/statsd_config.pb.h"
#include "src/statsd_metadata.pb.h"

namespace android {
namespace os {
namespace statsd {

class StatsLogProcessor : public ConfigListener, public virtual PackageInfoListener {
public:
    StatsLogProcessor(
            const sp<UidMap>& uidMap, const sp<StatsPullerManager>& pullerManager,
            const sp<AlarmMonitor>& anomalyAlarmMonitor,
            const sp<AlarmMonitor>& subscriberTriggerAlarmMonitor, int64_t timeBaseNs,
            const std::function<bool(const ConfigKey&)>& sendBroadcast,
            const std::function<bool(const int&, const vector<int64_t>&)>& sendActivationBroadcast,
            const std::function<void(const ConfigKey&, const string&, const vector<int64_t>&)>&
                    sendRestrictedMetricsBroadcast,
            const std::shared_ptr<LogEventFilter>& logEventFilter);

    virtual ~StatsLogProcessor();

    void OnLogEvent(LogEvent* event);

    void OnConfigUpdated(const int64_t timestampNs, int64_t wallClockNs, const ConfigKey& key,
                         const StatsdConfig& config, bool modularUpdate = true);
    // For testing only.
    void OnConfigUpdated(const int64_t timestampNs, const ConfigKey& key,
                         const StatsdConfig& config, bool modularUpdate = true);
    void OnConfigRemoved(const ConfigKey& key);

    size_t GetMetricsSize(const ConfigKey& key) const;

    void GetActiveConfigs(const int uid, vector<int64_t>& outActiveConfigs);

    void onDumpReport(const ConfigKey& key, int64_t dumpTimeNs, int64_t wallClockNs,
                      const bool include_current_partial_bucket, const bool erase_data,
                      const DumpReportReason dumpReportReason, const DumpLatency dumpLatency,
                      vector<uint8_t>* outData);
    void onDumpReport(const ConfigKey& key, int64_t dumpTimeNs, int64_t wallClockNs,
                      const bool include_current_partial_bucket, const bool erase_data,
                      const DumpReportReason dumpReportReason, const DumpLatency dumpLatency,
                      ProtoOutputStream* proto);
    // For testing only.
    void onDumpReport(const ConfigKey& key, int64_t dumpTimeNs,
                      const bool include_current_partial_bucket, const bool erase_data,
                      const DumpReportReason dumpReportReason, const DumpLatency dumpLatency,
                      vector<uint8_t>* outData);

    /* Tells MetricsManager that the alarms in alarmSet have fired. Modifies periodic alarmSet. */
    void onPeriodicAlarmFired(
            int64_t timestampNs,
            unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>>& alarmSet);

    /* Flushes data to disk. Data on memory will be gone after written to disk. */
    void WriteDataToDisk(const DumpReportReason dumpReportReason, const DumpLatency dumpLatency,
                         const int64_t elapsedRealtimeNs, int64_t wallClockNs);

    /* Persist configs containing metrics with active activations to disk. */
    void SaveActiveConfigsToDisk(int64_t currentTimeNs);

    /* Writes the current active status/ttl for all configs and metrics to ProtoOutputStream. */
    void WriteActiveConfigsToProtoOutputStream(
            int64_t currentTimeNs, const DumpReportReason reason, ProtoOutputStream* proto);

    /* Load configs containing metrics with active activations from disk. */
    void LoadActiveConfigsFromDisk();

    /* Persist metadata for configs and metrics to disk. */
    void SaveMetadataToDisk(int64_t currentWallClockTimeNs, int64_t systemElapsedTimeNs);

    /* Writes the statsd metadata for all configs and metrics to StatsMetadataList. */
    void WriteMetadataToProto(int64_t currentWallClockTimeNs,
                              int64_t systemElapsedTimeNs,
                              metadata::StatsMetadataList* metadataList);

    /* Load stats metadata for configs and metrics from disk. */
    void LoadMetadataFromDisk(int64_t currentWallClockTimeNs,
                              int64_t systemElapsedTimeNs);

    /* Sets the metadata for all configs and metrics */
    void SetMetadataState(const metadata::StatsMetadataList& statsMetadataList,
                          int64_t currentWallClockTimeNs,
                          int64_t systemElapsedTimeNs);

    /* Enforces ttls for restricted metrics */
    void EnforceDataTtls(const int64_t wallClockNs, int64_t elapsedRealtimeNs);

    /* Sets the active status/ttl for all configs and metrics to the status in ActiveConfigList. */
    void SetConfigsActiveState(const ActiveConfigList& activeConfigList, int64_t currentTimeNs);

    /* Notify all MetricsManagers of app upgrades */
    void notifyAppUpgrade(int64_t eventTimeNs, const string& apk, int uid,
                          int64_t version) override;

    /* Notify all MetricsManagers of app removals */
    void notifyAppRemoved(int64_t eventTimeNs, const string& apk, int uid) override;

    /* Notify all MetricsManagers of uid map snapshots received */
    void onUidMapReceived(int64_t eventTimeNs) override;

    /* Notify all metrics managers of boot completed
     * This will force a bucket split when the boot is finished.
     */
    void onStatsdInitCompleted(int64_t elapsedTimeNs);

    // Reset all configs.
    void resetConfigs();

    inline sp<UidMap> getUidMap() {
        return mUidMap;
    }

    void dumpStates(int outFd, bool verbose) const;

    void informPullAlarmFired(const int64_t timestampNs);

    int64_t getLastReportTimeNs(const ConfigKey& key);

    inline void setPrintLogs(bool enabled) {
        std::lock_guard<std::mutex> lock(mMetricsMutex);
        mPrintAllLogs = enabled;
        // Turning on print logs turns off pushed event filtering to enforce
        // complete log event buffer parsing
        mLogEventFilter->setFilteringEnabled(!enabled);
    }

    // Add a specific config key to the possible configs to dump ASAP.
    void noteOnDiskData(const ConfigKey& key);

    void setAnomalyAlarm(const int64_t timeMillis);

    void cancelAnomalyAlarm();

    void querySql(const string& sqlQuery, const int32_t minSqlClientVersion,
                  const optional<vector<uint8_t>>& policyConfig,
                  const shared_ptr<aidl::android::os::IStatsQueryCallback>& callback,
                  const int64_t configId, const string& configPackage, const int32_t callingUid);

    void fillRestrictedMetrics(const int64_t configId, const string& configPackage,
                               const int32_t delegateUid, vector<int64_t>* output);

    /* Returns pre-defined list of atoms to parse by LogEventFilter */
    static LogEventFilter::AtomIdSet getDefaultAtomIdSet();

private:
    // For testing only.
    inline sp<AlarmMonitor> getAnomalyAlarmMonitor() const {
        return mAnomalyAlarmMonitor;
    }

    inline sp<AlarmMonitor> getPeriodicAlarmMonitor() const {
        return mPeriodicAlarmMonitor;
    }

    mutable mutex mMetricsMutex;

    // Guards mNextAnomalyAlarmTime. A separate mutex is needed because alarms are set/cancelled
    // in the onLogEvent code path, which is locked by mMetricsMutex.
    // DO NOT acquire mMetricsMutex while holding mAnomalyAlarmMutex. This can lead to a deadlock.
    mutable mutex mAnomalyAlarmMutex;

    std::unordered_map<ConfigKey, sp<MetricsManager>> mMetricsManagers;

    std::unordered_map<ConfigKey, int64_t> mLastBroadcastTimes;

    // Last time we sent a broadcast to this uid that the active configs had changed.
    std::unordered_map<int, int64_t> mLastActivationBroadcastTimes;

    // Tracks when we last checked the bytes consumed for each config key.
    std::unordered_map<ConfigKey, int64_t> mLastByteSizeTimes;

    // Tracks the number of times a config with a specified config key has been dumped.
    std::unordered_map<ConfigKey, int32_t> mDumpReportNumbers;

    // Tracks when we last checked the ttl for restricted metrics.
    int64_t mLastTtlTime;

    // Tracks when we last flushed restricted metrics.
    int64_t mLastFlushRestrictedTime;

    // Tracks when we last checked db guardrails.
    int64_t mLastDbGuardrailEnforcementTime;

    // Tracks which config keys has metric reports on disk
    std::set<ConfigKey> mOnDiskDataConfigs;

    sp<UidMap> mUidMap;  // Reference to the UidMap to lookup app name and version for each uid.

    sp<StatsPullerManager> mPullerManager;  // Reference to StatsPullerManager

    sp<AlarmMonitor> mAnomalyAlarmMonitor;

    sp<AlarmMonitor> mPeriodicAlarmMonitor;

    std::shared_ptr<LogEventFilter> mLogEventFilter;

    void OnLogEvent(LogEvent* event, int64_t elapsedRealtimeNs);

    void resetIfConfigTtlExpiredLocked(const int64_t eventTimeNs);

    void OnConfigUpdatedLocked(const int64_t currentTimestampNs, const ConfigKey& key,
                               const StatsdConfig& config, bool modularUpdate);

    void GetActiveConfigsLocked(const int uid, vector<int64_t>& outActiveConfigs);

    void WriteActiveConfigsToProtoOutputStreamLocked(
            int64_t currentTimeNs, const DumpReportReason reason, ProtoOutputStream* proto);

    void SetConfigsActiveStateLocked(const ActiveConfigList& activeConfigList,
                                     int64_t currentTimeNs);

    void SetMetadataStateLocked(const metadata::StatsMetadataList& statsMetadataList,
                                int64_t currentWallClockTimeNs,
                                int64_t systemElapsedTimeNs);

    void WriteMetadataToProtoLocked(int64_t currentWallClockTimeNs,
                                    int64_t systemElapsedTimeNs,
                                    metadata::StatsMetadataList* metadataList);

    void WriteDataToDiskLocked(const DumpReportReason dumpReportReason,
                               const DumpLatency dumpLatency, int64_t elapsedRealtimeNs,
                               const int64_t wallClockNs);

    void WriteDataToDiskLocked(const ConfigKey& key, int64_t timestampNs, const int64_t wallClockNs,
                               const DumpReportReason dumpReportReason,
                               const DumpLatency dumpLatency);

    void onConfigMetricsReportLocked(
            const ConfigKey& key, int64_t dumpTimeStampNs, int64_t wallClockNs,
            const bool include_current_partial_bucket, const bool erase_data,
            const DumpReportReason dumpReportReason, const DumpLatency dumpLatency,
            /*if dataSavedToDisk is true, it indicates the caller will write the data to disk
             (e.g., before reboot). So no need to further persist local history.*/
            const bool dataSavedToDisk, vector<uint8_t>* proto);

    /* Check if it is time enforce data ttls for restricted metrics, and if it is, enforce ttls
     * on all restricted metrics. */
    void enforceDataTtlsIfNecessaryLocked(const int64_t wallClockNs,
                                          const int64_t elapsedRealtimeNs);

    // Enforces ttls on all restricted metrics.
    void enforceDataTtlsLocked(const int64_t wallClockNs, int64_t elapsedRealtimeNs);

    // Enforces that dbs are within guardrail parameters.
    void enforceDbGuardrailsIfNecessaryLocked(const int64_t wallClockNs,
                                              const int64_t elapsedRealtimeNs);

    /* Check if we should send a broadcast if approaching memory limits and if we're over, we
     * actually delete the data. */
    void flushIfNecessaryLocked(const ConfigKey& key, MetricsManager& metricsManager);

    set<ConfigKey> getRestrictedConfigKeysToQueryLocked(int32_t callingUid, const int64_t configId,
                                                        const set<int32_t>& configPackageUids,
                                                        string& err,
                                                        InvalidQueryReason& invalidQueryReason);

    // Maps the isolated uid in the log event to host uid if the log event contains uid fields.
    void mapIsolatedUidToHostUidIfNecessaryLocked(LogEvent* event) const;

    // Handler over the isolated uid change event.
    void onIsolatedUidChangedEventLocked(const LogEvent& event);

    // Handler over the binary push state changed event.
    void onBinaryPushStateChangedEventLocked(LogEvent* event);

    // Handler over the watchdog rollback occurred event.
    void onWatchdogRollbackOccurredLocked(LogEvent* event);

    // Updates train info on disk based on binary push state changed info and
    // write disk info into parameters.
    void getAndUpdateTrainInfoOnDisk(bool is_rollback, InstallTrainInfo* trainInfoIn);

    // Gets experiment ids on disk for associated train and updates them
    // depending on rollback type. Then writes them back to disk and returns
    // them.
    std::vector<int64_t> processWatchdogRollbackOccurred(int32_t rollbackTypeIn,
                                                         const string& packageName);

    // Reset all configs.
    void resetConfigsLocked(const int64_t timestampNs);
    // Reset the specified configs.
    void resetConfigsLocked(const int64_t timestampNs, const std::vector<ConfigKey>& configs);

    // An anomaly alarm should have fired.
    // Check with anomaly alarm manager to find the alarms and process the result.
    void informAnomalyAlarmFiredLocked(const int64_t elapsedTimeMillis);

    /* Tells MetricsManager that the alarms in alarmSet have fired. Modifies anomaly alarmSet. */
    void processFiredAnomalyAlarmsLocked(
            int64_t timestampNs,
            unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>>& alarmSet);

    void flushRestrictedDataLocked(const int64_t elapsedRealtimeNs);

    void flushRestrictedDataIfNecessaryLocked(const int64_t elapsedRealtimeNs);

    /* Tells LogEventFilter about atom ids to parse */
    void updateLogEventFilterLocked() const;

    void writeDataCorruptedReasons(ProtoOutputStream& proto);

    bool validateAppBreadcrumbEvent(const LogEvent& event) const;

    // Function used to send a broadcast so that receiver for the config key can call getData
    // to retrieve the stored data.
    std::function<bool(const ConfigKey& key)> mSendBroadcast;

    // Function used to send a broadcast so that receiver can be notified of which configs
    // are currently active.
    std::function<bool(const int& uid, const vector<int64_t>& configIds)> mSendActivationBroadcast;

    // Function used to send a broadcast if necessary so the receiver can be notified of the
    // restricted metrics for the given config.
    std::function<void(const ConfigKey& key, const string& delegatePackage,
                       const vector<int64_t>& restrictedMetricIds)>
            mSendRestrictedMetricsBroadcast;

    const int64_t mTimeBaseNs;

    // Largest timestamp of the events that we have processed.
    int64_t mLargestTimestampSeen = 0;

    int64_t mLastTimestampSeen = 0;

    int64_t mLastPullerCacheClearTimeSec = 0;

    // Last time we wrote data to disk.
    int64_t mLastWriteTimeNs = 0;

    // Last time we wrote active metrics to disk.
    int64_t mLastActiveMetricsWriteNs = 0;

    //Last time we wrote metadata to disk.
    int64_t mLastMetadataWriteNs = 0;

    // The time for the next anomaly alarm for alerts.
    int64_t mNextAnomalyAlarmTime = 0;

    bool mPrintAllLogs = false;

    friend class StatsLogProcessorTestRestricted;
    FRIEND_TEST(StatsLogProcessorTest, TestOutOfOrderLogs);
    FRIEND_TEST(StatsLogProcessorTest, TestRateLimitByteSize);
    FRIEND_TEST(StatsLogProcessorTest, TestRateLimitBroadcast);
    FRIEND_TEST(StatsLogProcessorTest, TestDropWhenByteSizeTooLarge);
    FRIEND_TEST(StatsLogProcessorTest, InvalidConfigRemoved);
    FRIEND_TEST(StatsLogProcessorTest, TestActiveConfigMetricDiskWriteRead);
    FRIEND_TEST(StatsLogProcessorTest, TestActivationOnBoot);
    FRIEND_TEST(StatsLogProcessorTest, TestActivationOnBootMultipleActivations);
    FRIEND_TEST(StatsLogProcessorTest,
            TestActivationOnBootMultipleActivationsDifferentActivationTypes);
    FRIEND_TEST(StatsLogProcessorTest, TestActivationsPersistAcrossSystemServerRestart);
    FRIEND_TEST(StatsLogProcessorTest, LogEventFilterOnSetPrintLogs);
    FRIEND_TEST(StatsLogProcessorTest, TestUidMapHasSnapshot);
    FRIEND_TEST(StatsLogProcessorTest, TestEmptyConfigHasNoUidMap);
    FRIEND_TEST(StatsLogProcessorTest, TestReportIncludesSubConfig);
    FRIEND_TEST(StatsLogProcessorTest, TestPullUidProviderSetOnConfigUpdate);
    FRIEND_TEST(StatsLogProcessorTestRestricted, TestInconsistentRestrictedMetricsConfigUpdate);
    FRIEND_TEST(StatsLogProcessorTestRestricted, TestRestrictedLogEventPassed);
    FRIEND_TEST(StatsLogProcessorTestRestricted, TestRestrictedLogEventNotPassed);
    FRIEND_TEST(StatsLogProcessorTestRestricted, RestrictedMetricsManagerOnDumpReportNotCalled);
    FRIEND_TEST(StatsLogProcessorTestRestricted, NonRestrictedMetricsManagerOnDumpReportCalled);
    FRIEND_TEST(StatsLogProcessorTestRestricted, RestrictedMetricOnDumpReportEmpty);
    FRIEND_TEST(StatsLogProcessorTestRestricted, NonRestrictedMetricOnDumpReportNotEmpty);
    FRIEND_TEST(StatsLogProcessorTestRestricted, RestrictedMetricNotWriteToDisk);
    FRIEND_TEST(StatsLogProcessorTestRestricted, NonRestrictedMetricWriteToDisk);
    FRIEND_TEST(StatsLogProcessorTestRestricted, RestrictedMetricFlushIfReachMemoryLimit);
    FRIEND_TEST(StatsLogProcessorTestRestricted, RestrictedMetricNotFlushIfNotReachMemoryLimit);
    FRIEND_TEST(WakelockDurationE2eTest, TestAggregatedPredicateDimensionsForSumDuration1);
    FRIEND_TEST(WakelockDurationE2eTest, TestAggregatedPredicateDimensionsForSumDuration2);
    FRIEND_TEST(WakelockDurationE2eTest, TestAggregatedPredicateDimensionsForSumDuration3);
    FRIEND_TEST(WakelockDurationE2eTest, TestAggregatedPredicateDimensionsForMaxDuration1);
    FRIEND_TEST(WakelockDurationE2eTest, TestAggregatedPredicateDimensionsForMaxDuration2);
    FRIEND_TEST(WakelockDurationE2eTest, TestAggregatedPredicateDimensionsForMaxDuration3);
    FRIEND_TEST(MetricConditionLinkE2eTest, TestMultiplePredicatesAndLinks1);
    FRIEND_TEST(MetricConditionLinkE2eTest, TestMultiplePredicatesAndLinks2);
    FRIEND_TEST(AttributionE2eTest, TestAttributionMatchAndSliceByFirstUid);
    FRIEND_TEST(AttributionE2eTest, TestAttributionMatchAndSliceByChain);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestFirstNSamplesPulledNoTrigger);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestFirstNSamplesPulledNoTriggerWithActivation);
    FRIEND_TEST(GaugeMetricE2ePushedTest, TestMultipleFieldsForPushedEvent);
    FRIEND_TEST(GaugeMetricE2ePushedTest, TestRepeatedFieldsForPushedEvent);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEvents);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEvent_LateAlarm);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEventsWithActivation);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEventsNoCondition);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestConditionChangeToTrueSamplePulledEvents);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestEnforceTtlRemovesOldEvents);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestFlagDisabled);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestLogEventsEnforceTtls);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestQueryEnforceTtls);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestLogEventsDoesNotEnforceTtls);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestNotFlushed);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestFlushInWriteDataToDisk);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestFlushPeriodically);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestTTlsEnforceDbGuardrails);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestOnLogEventMalformedDbNameDeleted);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestEnforceDbGuardrails);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestEnforceDbGuardrailsDoesNotDeleteBeforeGuardrail);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestRestrictedMetricLoadsTtlFromDisk);

    FRIEND_TEST(AnomalyCountDetectionE2eTest, TestSlicedCountMetric_single_bucket);
    FRIEND_TEST(AnomalyCountDetectionE2eTest, TestSlicedCountMetric_multiple_buckets);
    FRIEND_TEST(AnomalyCountDetectionE2eTest,
                TestCountMetric_save_refractory_to_disk_no_data_written);
    FRIEND_TEST(AnomalyCountDetectionE2eTest, TestCountMetric_save_refractory_to_disk);
    FRIEND_TEST(AnomalyCountDetectionE2eTest, TestCountMetric_load_refractory_from_disk);
    FRIEND_TEST(AnomalyDurationDetectionE2eTest, TestDurationMetric_SUM_single_bucket);
    FRIEND_TEST(AnomalyDurationDetectionE2eTest, TestDurationMetric_SUM_partial_bucket);
    FRIEND_TEST(AnomalyDurationDetectionE2eTest, TestDurationMetric_SUM_multiple_buckets);
    FRIEND_TEST(AnomalyDurationDetectionE2eTest, TestDurationMetric_SUM_long_refractory_period);

    FRIEND_TEST(AlarmE2eTest, TestMultipleAlarms);
    FRIEND_TEST(ConfigTtlE2eTest, TestCountMetric);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetric);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithOneDeactivation);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithTwoDeactivations);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithSameDeactivation);
    FRIEND_TEST(MetricActivationE2eTest, TestCountMetricWithTwoMetricsTwoDeactivations);

    FRIEND_TEST(ConfigUpdateE2eTest, TestAlarms);
    FRIEND_TEST(ConfigUpdateE2eTest, TestGaugeMetric);
    FRIEND_TEST(ConfigUpdateE2eTest, TestValueMetric);
    FRIEND_TEST(ConfigUpdateE2eTest, TestAnomalyDurationMetric);
    FRIEND_TEST(ConfigUpdateE2eAbTest, TestHashStrings);
    FRIEND_TEST(ConfigUpdateE2eAbTest, TestUidMapVersionStringInstaller);
    FRIEND_TEST(ConfigUpdateE2eAbTest, TestConfigTtl);

    FRIEND_TEST(CountMetricE2eTest, TestInitialConditionChanges);
    FRIEND_TEST(CountMetricE2eTest, TestSlicedState);
    FRIEND_TEST(CountMetricE2eTest, TestSlicedStateWithMap);
    FRIEND_TEST(CountMetricE2eTest, TestMultipleSlicedStates);
    FRIEND_TEST(CountMetricE2eTest, TestSlicedStateWithPrimaryFields);
    FRIEND_TEST(CountMetricE2eTest, TestRepeatedFieldsAndEmptyArrays);

    FRIEND_TEST(DurationMetricE2eTest, TestOneBucket);
    FRIEND_TEST(DurationMetricE2eTest, TestTwoBuckets);
    FRIEND_TEST(DurationMetricE2eTest, TestWithActivation);
    FRIEND_TEST(DurationMetricE2eTest, TestWithCondition);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedCondition);
    FRIEND_TEST(DurationMetricE2eTest, TestWithActivationAndSlicedCondition);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedState);
    FRIEND_TEST(DurationMetricE2eTest, TestWithConditionAndSlicedState);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedStateMapped);
    FRIEND_TEST(DurationMetricE2eTest, TestSlicedStatePrimaryFieldsNotSubsetDimInWhat);
    FRIEND_TEST(DurationMetricE2eTest, TestWithSlicedStatePrimaryFieldsSubset);
    FRIEND_TEST(DurationMetricE2eTest, TestUploadThreshold);
    FRIEND_TEST(DurationMetricE2eTest, TestConditionOnRepeatedEnumField);

    FRIEND_TEST(ValueMetricE2eTest, TestInitialConditionChanges);
    FRIEND_TEST(ValueMetricE2eTest, TestPulledEvents);
    FRIEND_TEST(ValueMetricE2eTest, TestPulledEvents_LateAlarm);
    FRIEND_TEST(ValueMetricE2eTest, TestPulledEvents_WithActivation);
    FRIEND_TEST(ValueMetricE2eTest, TestInitWithSlicedState);
    FRIEND_TEST(ValueMetricE2eTest, TestInitWithSlicedState_WithDimensions);
    FRIEND_TEST(ValueMetricE2eTest, TestInitWithSlicedState_WithIncorrectDimensions);
    FRIEND_TEST(ValueMetricE2eTest, TestInitWithValueFieldPositionALL);

    FRIEND_TEST(KllMetricE2eTest, TestInitWithKllFieldPositionALL);

    FRIEND_TEST(StatsServiceStatsdInitTest, StatsServiceStatsdInitTest);

    FRIEND_TEST(StringReplaceE2eTest, TestPulledDimension);
    FRIEND_TEST(StringReplaceE2eTest, TestPulledWhat);
    FRIEND_TEST(StringReplaceE2eTest, TestMultipleMatchersForAtom);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
