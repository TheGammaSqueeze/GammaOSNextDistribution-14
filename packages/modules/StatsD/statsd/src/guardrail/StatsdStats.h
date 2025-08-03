/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <gtest/gtest_prod.h>
#include <log/log_time.h>
#include <src/guardrail/stats_log_enums.pb.h>

#include <list>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "config/ConfigKey.h"
#include "logd/logevent_util.h"

namespace android {
namespace os {
namespace statsd {

struct InvalidConfigReason {
    InvalidConfigReasonEnum reason;
    std::optional<int64_t> metricId;
    std::optional<int64_t> stateId;
    std::optional<int64_t> alertId;
    std::optional<int64_t> alarmId;
    std::optional<int64_t> subscriptionId;
    std::vector<int64_t> matcherIds;
    std::vector<int64_t> conditionIds;
    InvalidConfigReason(){};
    InvalidConfigReason(InvalidConfigReasonEnum reason) : reason(reason){};
    InvalidConfigReason(InvalidConfigReasonEnum reason, int64_t metricId)
        : reason(reason), metricId(metricId){};
    bool operator==(const InvalidConfigReason& other) const {
        return (this->reason == other.reason) && (this->metricId == other.metricId) &&
               (this->stateId == other.stateId) && (this->alertId == other.alertId) &&
               (this->alarmId == other.alarmId) && (this->subscriptionId == other.subscriptionId) &&
               (this->matcherIds == other.matcherIds) && (this->conditionIds == other.conditionIds);
    }
};

typedef struct {
    int64_t insertError = 0;
    int64_t tableCreationError = 0;
    int64_t tableDeletionError = 0;
    std::list<int64_t> flushLatencyNs;
    int64_t categoryChangedCount = 0;
} RestrictedMetricStats;

struct DumpReportStats {
    DumpReportStats(int32_t dumpReportSec, int32_t dumpReportSize, int32_t reportNumber)
        : mDumpReportTimeSec(dumpReportSec),
          mDumpReportSizeBytes(dumpReportSize),
          mDumpReportNumber(reportNumber) {
    }
    int32_t mDumpReportTimeSec = 0;
    int32_t mDumpReportSizeBytes = 0;
    int32_t mDumpReportNumber = 0;
};

struct ConfigStats {
    int32_t uid;
    int64_t id;
    int32_t creation_time_sec;
    int32_t deletion_time_sec = 0;
    int32_t reset_time_sec = 0;
    int32_t metric_count;
    int32_t condition_count;
    int32_t matcher_count;
    int32_t alert_count;
    bool is_valid;
    bool device_info_table_creation_failed = false;
    int32_t db_corrupted_count = 0;
    int32_t db_deletion_stat_failed = 0;
    int32_t db_deletion_size_exceeded_limit = 0;
    int32_t db_deletion_config_invalid = 0;
    int32_t db_deletion_too_old = 0;
    int32_t db_deletion_config_removed = 0;
    int32_t db_deletion_config_updated = 0;

    // Stores reasons for why config is valid or not
    std::optional<InvalidConfigReason> reason;

    std::list<int32_t> broadcast_sent_time_sec;

    // Times at which this config is activated.
    std::list<int32_t> activation_time_sec;

    // Times at which this config is deactivated.
    std::list<int32_t> deactivation_time_sec;

    std::list<int32_t> data_drop_time_sec;
    // Number of bytes dropped at corresponding time.
    std::list<int64_t> data_drop_bytes;

    std::list<DumpReportStats> dump_report_stats;

    // Stores how many times a matcher have been matched. The map size is capped by kMaxConfigCount.
    std::map<const int64_t, int> matcher_stats;

    // Stores the number of output tuple of condition trackers when it's bigger than
    // kDimensionKeySizeSoftLimit. When you see the number is kDimensionKeySizeHardLimit +1,
    // it means some data has been dropped. The map size is capped by kMaxConfigCount.
    std::map<const int64_t, int> condition_stats;

    // Stores the number of output tuple of metric producers when it's bigger than
    // kDimensionKeySizeSoftLimit. When you see the number is kDimensionKeySizeHardLimit +1,
    // it means some data has been dropped. The map size is capped by kMaxConfigCount.
    std::map<const int64_t, int> metric_stats;

    // Stores the max number of output tuple of dimensions in condition across dimensions in what
    // when it's bigger than kDimensionKeySizeSoftLimit. When you see the number is
    // kDimensionKeySizeHardLimit +1, it means some data has been dropped. The map size is capped by
    // kMaxConfigCount.
    std::map<const int64_t, int> metric_dimension_in_condition_stats;

    // Stores the number of times an anomaly detection alert has been declared.
    // The map size is capped by kMaxConfigCount.
    std::map<const int64_t, int> alert_stats;

    // Stores the config ID for each sub-config used.
    std::list<std::pair<const int64_t, const int32_t>> annotations;

    // Maps metric ID of restricted metric to its stats.
    std::map<int64_t, RestrictedMetricStats> restricted_metric_stats;

    std::list<int64_t> total_flush_latency_ns;

    // Stores the last 20 timestamps for computing sqlite db size.
    std::list<int64_t> total_db_size_timestamps;

    // Stores the last 20 sizes of the sqlite db.
    std::list<int64_t> total_db_sizes;
};

struct UidMapStats {
    int32_t changes = 0;
    int32_t bytes_used = 0;
    int32_t dropped_changes = 0;
    int32_t deleted_apps = 0;
};

struct SubscriptionStats {
    int32_t pushed_atom_count = 0;
    int32_t pulled_atom_count = 0;
    int32_t start_time_sec = 0;
    int32_t end_time_sec = 0;
    int32_t flush_count = 0;
};

// Keeps track of stats of statsd.
// Single instance shared across the process. All public methods are thread safe.
class StatsdStats {
public:
    static StatsdStats& getInstance();
    ~StatsdStats(){};

    const static int kDimensionKeySizeSoftLimit = 500;
    static constexpr int kDimensionKeySizeHardLimit = 800;
    static constexpr int kDimensionKeySizeHardLimitMin = 800;
    static constexpr int kDimensionKeySizeHardLimitMax = 3000;

    // Per atom dimension key size limit
    static const std::map<int, std::pair<size_t, size_t>> kAtomDimensionKeySizeLimitMap;

    const static int kMaxConfigCountPerUid = 20;
    const static int kMaxAlertCountPerConfig = 200;
    const static int kMaxConditionCountPerConfig = 500;
    const static int kMaxMetricCountPerConfig = 2000;
    const static int kMaxMatcherCountPerConfig = 2500;

    // The max number of old config stats we keep.
    const static int kMaxIceBoxSize = 20;

    const static int kMaxLoggerErrors = 20;

    const static int kMaxSystemServerRestarts = 20;

    const static int kMaxTimestampCount = 20;

    const static int kMaxLogSourceCount = 150;

    const static int kMaxPullAtomPackages = 100;

    const static int kMaxRestrictedMetricQueryCount = 20;

    const static int kMaxRestrictedMetricFlushLatencyCount = 20;

    const static int kMaxRestrictedConfigFlushLatencyCount = 20;

    const static int kMaxRestrictedConfigDbSizeCount = 20;

    // Max memory allowed for storing metrics per configuration. If this limit is exceeded, statsd
    // drops the metrics data in memory.
    static const size_t kDefaultMaxMetricsBytesPerConfig = 2 * 1024 * 1024;

    // Hard limit for custom memory allowed for storing metrics per configuration.
    static const size_t kHardMaxMetricsBytesPerConfig = 20 * 1024 * 1024;

    // Max memory allowed for storing metrics per configuration before triggering a intent to fetch
    // data.
    static const size_t kHardMaxTriggerGetDataBytes = 10 * 1024 * 1024;

    // Soft memory limit per configuration. Once this limit is exceeded, we begin notifying the
    // data subscriber that it's time to call getData.
    static const size_t kDefaultBytesPerConfigTriggerGetData = 192 * 1024;

    // Soft memory limit per restricted configuration. Once this limit is exceeded,
    // we begin flush in-memory restricted metrics to database.
    static const size_t kBytesPerRestrictedConfigTriggerFlush = 25 * 1024;

    // Cap the UID map's memory usage to this. This should be fairly high since the UID information
    // is critical for understanding the metrics.
    const static size_t kMaxBytesUsedUidMap = 50 * 1024;

    // The number of deleted apps that are stored in the uid map.
    const static int kMaxDeletedAppsInUidMap = 100;

    /* Minimum period between two broadcasts in nanoseconds. */
    static const int64_t kMinBroadcastPeriodNs = 60 * NS_PER_SEC;

    /* Min period between two checks of byte size per config key in nanoseconds. */
    static const int64_t kMinByteSizeCheckPeriodNs = 60 * NS_PER_SEC;

    /* Min period between two checks of restricted metrics TTLs. */
    static const int64_t kMinTtlCheckPeriodNs = 60 * 60 * NS_PER_SEC;

    /* Min period between two flush operations of restricted metrics. */
    static const int64_t kMinFlushRestrictedPeriodNs = 60 * 60 * NS_PER_SEC;

    /* Min period between two db guardrail check operations of restricted metrics. */
    static const int64_t kMinDbGuardrailEnforcementPeriodNs = 60 * 60 * NS_PER_SEC;

    /* Minimum period between two activation broadcasts in nanoseconds. */
    static const int64_t kMinActivationBroadcastPeriodNs = 10 * NS_PER_SEC;

    // Maximum age (30 days) that files on disk can exist in seconds.
    static const int kMaxAgeSecond = 60 * 60 * 24 * 30;

    // Maximum age (2 days) that local history files on disk can exist in seconds.
    static const int kMaxLocalHistoryAgeSecond = 60 * 60 * 24 * 2;

    // Maximum number of files (1000) that can be in stats directory on disk.
    static const int kMaxFileNumber = 1000;

    // Maximum size of all files that can be written to stats directory on disk.
    static const int kMaxFileSize = 50 * 1024 * 1024;

    // How long to try to clear puller cache from last time
    static const long kPullerCacheClearIntervalSec = 1;

    // Max time to do a pull.
    static const int64_t kPullMaxDelayNs = 30 * NS_PER_SEC;

    // Maximum number of pushed atoms statsd stats will track above kMaxPushedAtomId.
    static const int kMaxNonPlatformPushedAtoms = 600;

    // Maximum number of pushed atoms error statsd stats will track.
    static const int kMaxPushedAtomErrorStatsSize = 100;

    // Maximum number of socket loss stats to track.
    static const int kMaxSocketLossStatsSize = 50;

    // Maximum atom id value that we consider a platform pushed atom.
    // This should be updated once highest pushed atom id in atoms.proto approaches this value.
    static const int kMaxPushedAtomId = 900;

    // Atom id that is the start of the pulled atoms.
    static const int kPullAtomStartTag = 10000;

    // Atom id that is the start of vendor atoms.
    static const int kVendorAtomStartTag = 100000;

    // Vendor pulled atom start id.
    static const int32_t kVendorPulledAtomStartTag = 150000;

    // Beginning of range for timestamp truncation.
    static const int32_t kTimestampTruncationStartTag = 300000;

    // End of range for timestamp truncation.
    static const int32_t kTimestampTruncationEndTag = 304999;

    // Max accepted atom id.
    static const int32_t kMaxAtomTag = 200000;

    static const int64_t kInt64Max = 0x7fffffffffffffffLL;

    static const int32_t kMaxLoggedBucketDropEvents = 10;

    /**
     * Report a new config has been received and report the static stats about the config.
     *
     * The static stats include: the count of metrics, conditions, matchers, and alerts.
     * If the config is not valid, this config stats will be put into icebox immediately.
     */
    void noteConfigReceived(const ConfigKey& key, int metricsCount, int conditionsCount,
                            int matchersCount, int alertCount,
                            const std::list<std::pair<const int64_t, const int32_t>>& annotations,
                            const std::optional<InvalidConfigReason>& reason);
    /**
     * Report a config has been removed.
     */
    void noteConfigRemoved(const ConfigKey& key);
    /**
     * Report a config has been reset when ttl expires.
     */
    void noteConfigReset(const ConfigKey& key);

    /**
     * Report a broadcast has been sent to a config owner to collect the data.
     */
    void noteBroadcastSent(const ConfigKey& key);

    /**
     * Report that a config has become activated or deactivated.
     * This can be different from whether or not a broadcast is sent if the
     * guardrail prevented the broadcast from being sent.
     */
    void noteActiveStatusChanged(const ConfigKey& key, bool activate);

    /**
     * Report a config's metrics data has been dropped.
     */
    void noteDataDropped(const ConfigKey& key, const size_t totalBytes);

    /**
     * Report metrics data report has been sent.
     *
     * The report may be requested via StatsManager API, or through adb cmd.
     */
    void noteMetricsReportSent(const ConfigKey& key, const size_t numBytes,
                               const int32_t reportNumber);

    /**
     * Report failure in creating the device info metadata table for restricted configs.
     */
    void noteDeviceInfoTableCreationFailed(const ConfigKey& key);

    /**
     * Report db corruption for restricted configs.
     */
    void noteDbCorrupted(const ConfigKey& key);

    /**
     * Report db exceeded the size limit for restricted configs.
     */
    void noteDbSizeExceeded(const ConfigKey& key);

    /**
     * Report db size check with stat for restricted configs failed.
     */
    void noteDbStatFailed(const ConfigKey& key);

    /**
     * Report restricted config is invalid.
     */
    void noteDbConfigInvalid(const ConfigKey& key);

    /**
     * Report db is too old for restricted configs.
     */
    void noteDbTooOld(const ConfigKey& key);

    /**
     * Report db was deleted due to config removal.
     */
    void noteDbDeletionConfigRemoved(const ConfigKey& key);

    /**
     * Report db was deleted due to config update.
     */
    void noteDbDeletionConfigUpdated(const ConfigKey& key);

    /**
     * Report the size of output tuple of a condition.
     *
     * Note: only report when the condition has an output dimension, and the tuple
     * count > kDimensionKeySizeSoftLimit.
     *
     * [key]: The config key that this condition belongs to.
     * [id]: The id of the condition.
     * [size]: The output tuple size.
     */
    void noteConditionDimensionSize(const ConfigKey& key, int64_t id, int size);

    /**
     * Report the size of output tuple of a metric.
     *
     * Note: only report when the metric has an output dimension, and the tuple
     * count > kDimensionKeySizeSoftLimit.
     *
     * [key]: The config key that this metric belongs to.
     * [id]: The id of the metric.
     * [size]: The output tuple size.
     */
    void noteMetricDimensionSize(const ConfigKey& key, int64_t id, int size);

    /**
     * Report the max size of output tuple of dimension in condition across dimensions in what.
     *
     * Note: only report when the metric has an output dimension in condition, and the max tuple
     * count > kDimensionKeySizeSoftLimit.
     *
     * [key]: The config key that this metric belongs to.
     * [id]: The id of the metric.
     * [size]: The output tuple size.
     */
    void noteMetricDimensionInConditionSize(const ConfigKey& key, int64_t id, int size);

    /**
     * Report a matcher has been matched.
     *
     * [key]: The config key that this matcher belongs to.
     * [id]: The id of the matcher.
     */
    void noteMatcherMatched(const ConfigKey& key, int64_t id);

    /**
     * Report that an anomaly detection alert has been declared.
     *
     * [key]: The config key that this alert belongs to.
     * [id]: The id of the alert.
     */
    void noteAnomalyDeclared(const ConfigKey& key, int64_t id);

    /**
     * Report an atom event has been logged.
     */
    void noteAtomLogged(int atomId, int32_t timeSec, bool isSkipped);

    /**
     * Report that statsd modified the anomaly alarm registered with StatsCompanionService.
     */
    void noteRegisteredAnomalyAlarmChanged();

    /**
     * Report that statsd modified the periodic alarm registered with StatsCompanionService.
     */
    void noteRegisteredPeriodicAlarmChanged();

    /**
     * Records the number of delta entries that are being dropped from the uid map.
     */
    void noteUidMapDropped(int deltas);

    /**
     * Records that an app was deleted (from statsd's map).
     */
    void noteUidMapAppDeletionDropped();

    /**
     * Updates the number of changes currently stored in the uid map.
     */
    void setUidMapChanges(int changes);
    void setCurrentUidMapMemory(int bytes);

    /*
     * Updates minimum interval between pulls for an pulled atom.
     */
    void updateMinPullIntervalSec(int pullAtomId, long intervalSec);

    /*
     * Notes an atom is pulled.
     */
    void notePull(int pullAtomId);

    /*
     * Notes an atom is served from puller cache.
     */
    void notePullFromCache(int pullAtomId);

    /*
     * Notify data error for pulled atom.
     */
    void notePullDataError(int pullAtomId);

    /*
     * Records time for actual pulling, not including those served from cache and not including
     * statsd processing delays.
     */
    void notePullTime(int pullAtomId, int64_t pullTimeNs);

    /*
     * Records pull delay for a pulled atom, including those served from cache and including statsd
     * processing delays.
     */
    void notePullDelay(int pullAtomId, int64_t pullDelayNs);

    /*
     * Records pull exceeds timeout for the puller.
     */
    void notePullTimeout(int pullAtomId, int64_t pullUptimeMillis, int64_t pullElapsedMillis);

    /*
     * Records pull exceeds max delay for a metric.
     */
    void notePullExceedMaxDelay(int pullAtomId);

    /*
     * Records when system server restarts.
     */
    void noteSystemServerRestart(int32_t timeSec);

    /**
     * Records statsd skipped an event.
     */
    void noteLogLost(int32_t wallClockTimeSec, int32_t count, int32_t lastError,
                     int32_t lastAtomTag, int32_t uid, int32_t pid);

    /**
     * Records that the pull of an atom has failed. Eg, if the client indicated the pull failed, if
     * the pull timed out, or if the outgoing binder call failed.
     * This count will only increment if the puller was actually invoked.
     *
     * It does not include a pull not occurring due to not finding the appropriate
     * puller. These cases are covered in other counts.
     */
    void notePullFailed(int atomId);

    /**
     * Records that the pull of an atom has failed due to not having a uid provider.
     */
    void notePullUidProviderNotFound(int atomId);

    /**
     * Records that the pull of an atom has failed due not finding a puller registered by a
     * trusted uid.
     */
    void notePullerNotFound(int atomId);

    /**
     * Records that the pull has failed due to the outgoing binder call failing.
     */
    void notePullBinderCallFailed(int atomId);

    /**
     * A pull with no data occurred
     */
    void noteEmptyData(int atomId);

    /**
     * Records that a puller callback for the given atomId was registered or unregistered.
     *
     * @param registered True if the callback was registered, false if was unregistered.
     */
    void notePullerCallbackRegistrationChanged(int atomId, bool registered);

    /**
     * Hard limit was reached in the cardinality of an atom
     */
    void noteHardDimensionLimitReached(int64_t metricId);

    /**
     * A log event was too late, arrived in the wrong bucket and was skipped
     */
    void noteLateLogEventSkipped(int64_t metricId);

    /**
     * Buckets were skipped as time elapsed without any data for them
     */
    void noteSkippedForwardBuckets(int64_t metricId);

    /**
     * An unsupported value type was received
     */
    void noteBadValueType(int64_t metricId);

    /**
     * Buckets were dropped due to reclaim memory.
     */
    void noteBucketDropped(int64_t metricId);

    /**
     * A condition change was too late, arrived in the wrong bucket and was skipped
     */
    void noteConditionChangeInNextBucket(int64_t metricId);

    /**
     * A bucket has been tagged as invalid.
     */
    void noteInvalidatedBucket(int64_t metricId);

    /**
     * Tracks the total number of buckets (include skipped/invalid buckets).
     */
    void noteBucketCount(int64_t metricId);

    /**
     * For pulls at bucket boundaries, it represents the misalignment between the real timestamp and
     * the end of the bucket.
     */
    void noteBucketBoundaryDelayNs(int64_t metricId, int64_t timeDelayNs);

    /**
     * Number of buckets with unknown condition.
     */
    void noteBucketUnknownCondition(int64_t metricId);

    /* Reports one event id has been dropped due to queue overflow, and the oldest event timestamp
     * in the queue */
    void noteEventQueueOverflow(int64_t oldestEventTimestampNs, int32_t atomId, bool isSkipped);

    /* Notes queue max size seen so far and associated timestamp */
    void noteEventQueueSize(int32_t size, int64_t eventTimestampNs);

    /**
     * Reports that the activation broadcast guardrail was hit for this uid. Namely, the broadcast
     * should have been sent, but instead was skipped due to hitting the guardrail.
     */
    void noteActivationBroadcastGuardrailHit(const int uid);

    /**
     * Reports that an atom is erroneous or cannot be parsed successfully by
     * statsd. An atom tag of 0 indicates that the client did not supply the
     * atom id within the encoding.
     *
     * For pushed atoms only, this call should be preceded by a call to
     * noteAtomLogged.
     */
    void noteAtomError(int atomTag, bool pull = false);

    /** Report query of restricted metric succeed **/
    void noteQueryRestrictedMetricSucceed(const int64_t configId, const string& configPackage,
                                          const std::optional<int32_t> configUid,
                                          const int32_t callingUid, int64_t queryLatencyNs);

    /** Report query of restricted metric failed **/
    void noteQueryRestrictedMetricFailed(const int64_t configId, const string& configPackage,
                                         const std::optional<int32_t> configUid,
                                         const int32_t callingUid, const InvalidQueryReason reason);

    /** Report query of restricted metric failed along with an error string **/
    void noteQueryRestrictedMetricFailed(const int64_t configId, const string& configPackage,
                                         const std::optional<int32_t> configUid,
                                         const int32_t callingUid, const InvalidQueryReason reason,
                                         const string& error);

    // Reports that a restricted metric fails to be inserted to database.
    void noteRestrictedMetricInsertError(const ConfigKey& configKey, int64_t metricId);

    // Reports that a restricted metric fails to create table in database.
    void noteRestrictedMetricTableCreationError(const ConfigKey& configKey, int64_t metricId);

    // Reports that a restricted metric fails to delete table in database.
    void noteRestrictedMetricTableDeletionError(const ConfigKey& configKey, int64_t metricId);

    // Reports the time it takes for a restricted metric to flush the data to the database.
    void noteRestrictedMetricFlushLatency(const ConfigKey& configKey, int64_t metricId,
                                          const int64_t flushLatencyNs);

    // Reports that a restricted metric had a category change.
    void noteRestrictedMetricCategoryChanged(const ConfigKey& configKey, int64_t metricId);

    // Reports the time is takes to flush a restricted config to the database.
    void noteRestrictedConfigFlushLatency(const ConfigKey& configKey,
                                          const int64_t totalFlushLatencyNs);

    // Reports the size of the internal sqlite db.
    void noteRestrictedConfigDbSize(const ConfigKey& configKey, int64_t elapsedTimeNs,
                                    const int64_t dbSize);

    /**
     * Records libstatssocket was not able to write into socket.
     */
    void noteAtomSocketLoss(const SocketLossInfo& lossInfo);

    /**
     * Report a new subscription has started and report the static stats about the subscription
     * config.
     *
     * The static stats include: the count of pushed atoms and pulled atoms.
     */
    void noteSubscriptionStarted(int subId, int32_t pushedAtomCount, int32_t pulledAtomCount);

    /**
     * Report an existing subscription has ended.
     */
    void noteSubscriptionEnded(int subId);

    /**
     * Report an existing subscription was flushed.
     */
    void noteSubscriptionFlushed(int subId);

    /**
     * Report an atom was pulled for a subscription.
     */
    void noteSubscriptionAtomPulled(int atomId);

    /**
     * Report subscriber pull thread wakeup.
     */
    void noteSubscriptionPullThreadWakeup();

    /**
     * Reset the historical stats. Including all stats in icebox, and the tracked stats about
     * metrics, matchers, and atoms. The active configs will be kept and StatsdStats will continue
     * to collect stats after reset() has been called.
     */
    void reset();

    /**
     * Output the stats in protobuf binary format to [buffer].
     *
     * [reset]: whether to clear the historical stats after the call.
     */
    void dumpStats(std::vector<uint8_t>* buffer, bool reset);

    /**
     * Output statsd stats in human readable format to [out] file descriptor.
     */
    void dumpStats(int outFd) const;

    /**
     * Returns true if dimension guardrail has been hit since boot for given metric.
     */
    bool hasHitDimensionGuardrail(int64_t metricId) const;

    /**
     * Return soft and hard atom key dimension size limits as an std::pair.
     */
    static std::pair<size_t, size_t> getAtomDimensionKeySizeLimits(int atomId,
                                                                   size_t defaultHardLimit);

    inline static int clampDimensionKeySizeLimit(int dimLimit) {
        return std::clamp(dimLimit, kDimensionKeySizeHardLimitMin, kDimensionKeySizeHardLimitMax);
    }

    /**
     * Return the unique identifier for the statsd stats report. This id is
     * reset on boot.
     */
    inline int32_t getStatsdStatsId() const {
        return mStatsdStatsId;
    }

    /**
     * Returns true if there is recorded event queue overflow
     */
    bool hasEventQueueOverflow() const;

    /**
     * Returns true if there is recorded socket loss
     */
    bool hasSocketLoss() const;

    typedef struct PullTimeoutMetadata {
        int64_t pullTimeoutUptimeMillis;
        int64_t pullTimeoutElapsedMillis;
        PullTimeoutMetadata(int64_t uptimeMillis, int64_t elapsedMillis)
            : pullTimeoutUptimeMillis(uptimeMillis),
              pullTimeoutElapsedMillis(elapsedMillis) { /* do nothing */
        }
    } PullTimeoutMetadata;

    typedef struct {
        long totalPull = 0;
        long totalPullFromCache = 0;
        long minPullIntervalSec = LONG_MAX;
        int64_t avgPullTimeNs = 0;
        int64_t maxPullTimeNs = 0;
        long numPullTime = 0;
        int64_t avgPullDelayNs = 0;
        int64_t maxPullDelayNs = 0;
        long numPullDelay = 0;
        long dataError = 0;
        long pullTimeout = 0;
        long pullExceedMaxDelay = 0;
        long pullFailed = 0;
        long pullUidProviderNotFound = 0;
        long pullerNotFound = 0;
        long emptyData = 0;
        long registeredCount = 0;
        long unregisteredCount = 0;
        int32_t atomErrorCount = 0;
        long binderCallFailCount = 0;
        std::list<PullTimeoutMetadata> pullTimeoutMetadata;
        int32_t subscriptionPullCount = 0;
    } PulledAtomStats;

    typedef struct {
        long hardDimensionLimitReached = 0;
        long lateLogEventSkipped = 0;
        long skippedForwardBuckets = 0;
        long badValueType = 0;
        long conditionChangeInNextBucket = 0;
        long invalidatedBucket = 0;
        long bucketDropped = 0;
        int64_t minBucketBoundaryDelayNs = 0;
        int64_t maxBucketBoundaryDelayNs = 0;
        long bucketUnknownCondition = 0;
        long bucketCount = 0;
    } AtomMetricStats;

private:
    StatsdStats();

    mutable std::mutex mLock;

    int32_t mStartTimeSec;

    // Random id set using rand() during the initialization. Used to uniquely
    // identify a session. This is more reliable than mStartTimeSec due to the
    // unreliable nature of wall clock times.
    const int32_t mStatsdStatsId;

    // Track the number of dropped entries used by the uid map.
    UidMapStats mUidMapStats;

    // The stats about the configs that are still in use.
    // The map size is capped by kMaxConfigCount.
    std::map<const ConfigKey, std::shared_ptr<ConfigStats>> mConfigStats;

    // Stores the stats for the configs that are no longer in use.
    // The size of the vector is capped by kMaxIceBoxSize.
    std::list<std::shared_ptr<ConfigStats>> mIceBox;

    // Stores the number of times a pushed atom is logged and skipped (if skipped).
    // The size of the vector is the largest pushed atom id in atoms.proto + 1. Atoms
    // out of that range will be put in mNonPlatformPushedAtomStats.
    // This is a vector, not a map because it will be accessed A LOT -- for each stats log.
    struct PushedAtomStats {
        int logCount = 0;
        int skipCount = 0;
    };

    std::vector<PushedAtomStats> mPushedAtomStats;

    // Stores the number of times a pushed atom is logged and skipped for atom ids above
    // kMaxPushedAtomId. The max size of the map is kMaxNonPlatformPushedAtoms.
    std::unordered_map<int, PushedAtomStats> mNonPlatformPushedAtomStats;

    // Stores the number of times a pushed atom is dropped due to queue overflow event.
    // We do not expect it will happen too often so the map is preferable vs pre-allocated vector
    // The max size of the map is kMaxPushedAtomId + kMaxNonPlatformPushedAtoms.
    std::unordered_map<int, int> mPushedAtomDropsStats;

    // Maps PullAtomId to its stats. The size is capped by the puller atom counts.
    std::map<int, PulledAtomStats> mPulledAtomStats;

    // Stores the number of times a pushed atom was logged erroneously. The
    // corresponding counts for pulled atoms are stored in PulledAtomStats.
    // The max size of this map is kMaxPushedAtomErrorStatsSize.
    std::map<int, int> mPushedAtomErrorStats;

    // Stores the number of times a pushed atom was lost due to socket error.
    // Represents counter per uid per tag per error with indication when the loss event was observed
    // first & last time.
    struct SocketLossStats {
        SocketLossStats(int32_t uid, int64_t firstLossTsNanos, int64_t lastLossTsNanos)
            : mUid(uid), mFirstLossTsNanos(firstLossTsNanos), mLastLossTsNanos(lastLossTsNanos) {
        }

        int32_t mUid;
        int64_t mFirstLossTsNanos;
        int64_t mLastLossTsNanos;
        // atom loss count per error, atom id
        struct AtomLossInfo {
            AtomLossInfo(int32_t atomId, int32_t error, int32_t count)
                : mAtomId(atomId), mError(error), mCount(count) {
            }
            int mAtomId;
            int mError;
            int mCount;
        };
        std::vector<AtomLossInfo> mLossCountPerErrorAtomId;
    };
    // The max size of this list is kMaxSocketLossStatsSize.
    std::list<SocketLossStats> mSocketLossStats;

    // Stores the number of times a pushed atom loss info was dropped from the stats
    // on libstatssocket side due to guardrail hit.
    // Represents counter per uid.
    // The max size of this map is kMaxSocketLossStatsSize.
    std::map<int32_t, int32_t> mSocketLossStatsOverflowCounters;

    // Maps metric ID to its stats. The size is capped by the number of metrics.
    std::map<int64_t, AtomMetricStats> mAtomMetricStats;

    // Maps uids to times when the activation changed broadcast not sent due to hitting the
    // guardrail. The size is capped by the number of configs, and up to 20 times per uid.
    std::map<int, std::list<int32_t>> mActivationBroadcastGuardrailStats;

    struct LogLossStats {
        LogLossStats(int32_t sec, int32_t count, int32_t error, int32_t tag, int32_t uid,
                     int32_t pid)
            : mWallClockSec(sec),
              mCount(count),
              mLastError(error),
              mLastTag(tag),
              mUid(uid),
              mPid(pid) {
        }
        int32_t mWallClockSec;
        int32_t mCount;
        // error code defined in linux/errno.h
        int32_t mLastError;
        int32_t mLastTag;
        int32_t mUid;
        int32_t mPid;
    };

    // Max of {(now - oldestEventTimestamp) when overflow happens}.
    // This number is helpful to understand how SLOW statsd can be.
    int64_t mMaxQueueHistoryNs = 0;

    // Min of {(now - oldestEventTimestamp) when overflow happens}.
    // This number is helpful to understand how FAST the events floods to statsd.
    int64_t mMinQueueHistoryNs = kInt64Max;

    // Total number of events that are lost due to queue overflow.
    int32_t mOverflowCount = 0;

    // Max number of events stored into the queue seen so far.
    int32_t mEventQueueMaxSizeObserved = 0;

    // Event timestamp for associated max size hit.
    int64_t mEventQueueMaxSizeObservedElapsedNanos = 0;

    // Timestamps when we detect log loss, and the number of logs lost.
    std::list<LogLossStats> mLogLossStats;

    std::list<int32_t> mSystemServerRestartSec;

    struct RestrictedMetricQueryStats {
        RestrictedMetricQueryStats(int32_t callingUid, int64_t configId,
                                   const string& configPackage, std::optional<int32_t> configUid,
                                   int64_t queryTimeNs,
                                   std::optional<InvalidQueryReason> invalidQueryReason,
                                   const string& error, std::optional<int64_t> queryLatencyNs)
            : mCallingUid(callingUid),
              mConfigId(configId),
              mConfigPackage(configPackage),
              mConfigUid(configUid),
              mQueryWallTimeNs(queryTimeNs),
              mInvalidQueryReason(invalidQueryReason),
              mError(error),
              mQueryLatencyNs(queryLatencyNs) {
            mHasError = invalidQueryReason.has_value();
        }
        int32_t mCallingUid;
        int64_t mConfigId;
        string mConfigPackage;
        std::optional<int32_t> mConfigUid;
        int64_t mQueryWallTimeNs;
        std::optional<InvalidQueryReason> mInvalidQueryReason;
        bool mHasError;
        string mError;
        std::optional<int64_t> mQueryLatencyNs;
    };
    std::list<RestrictedMetricQueryStats> mRestrictedMetricQueryStats;

    void noteQueryRestrictedMetricFailedLocked(const int64_t configId, const string& configPackage,
                                               const std::optional<int32_t> configUid,
                                               const int32_t callingUid,
                                               const InvalidQueryReason reason,
                                               const string& error);

    int32_t mSubscriptionPullThreadWakeupCount = 0;

    // Maps Subscription ID to the corresponding SubscriptionStats struct object.
    // Size of this map is capped by ShellSubscriber::kMaxSubscriptions.
    std::map<int32_t, SubscriptionStats> mSubscriptionStats;

    // Stores the number of times statsd modified the anomaly alarm registered with
    // StatsCompanionService.
    int mAnomalyAlarmRegisteredStats = 0;

    // Stores the number of times statsd registers the periodic alarm changes
    int mPeriodicAlarmRegisteredStats = 0;

    void noteConfigResetInternalLocked(const ConfigKey& key);

    void noteConfigRemovedInternalLocked(const ConfigKey& key);

    void resetInternalLocked();

    void noteAtomLoggedLocked(int atomId, bool isSkipped);

    void noteAtomDroppedLocked(int atomId);

    void noteDataDropped(const ConfigKey& key, const size_t totalBytes, int32_t timeSec);

    void noteMetricsReportSent(const ConfigKey& key, const size_t numBytes, int32_t timeSec,
                               const int32_t reportNumber);

    void noteBroadcastSent(const ConfigKey& key, int32_t timeSec);

    void noteActiveStatusChanged(const ConfigKey& key, bool activate, int32_t timeSec);

    void noteActivationBroadcastGuardrailHit(const int uid, int32_t timeSec);

    void addToIceBoxLocked(std::shared_ptr<ConfigStats>& stats);

    int getPushedAtomErrorsLocked(int atomId) const;

    int getPushedAtomDropsLocked(int atomId) const;

    bool hasRestrictedConfigErrors(const std::shared_ptr<ConfigStats>& configStats) const;

    /**
     * Get a reference to AtomMetricStats for a metric. If none exists, create it. The reference
     * will live as long as `this`.
     */
    StatsdStats::AtomMetricStats& getAtomMetricStats(int64_t metricId);

    FRIEND_TEST(LogEventQueue_test, TestQueueMaxSize);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessage);
    FRIEND_TEST(StatsLogProcessorTest, InvalidConfigRemoved);
    FRIEND_TEST(StatsdStatsTest, TestActivationBroadcastGuardrailHit);
    FRIEND_TEST(StatsdStatsTest, TestAnomalyMonitor);
    FRIEND_TEST(StatsdStatsTest, TestAtomDroppedStats);
    FRIEND_TEST(StatsdStatsTest, TestAtomErrorStats);
    FRIEND_TEST(StatsdStatsTest, TestAtomLog);
    FRIEND_TEST(StatsdStatsTest, TestAtomLoggedAndDroppedAndSkippedStats);
    FRIEND_TEST(StatsdStatsTest, TestAtomLoggedAndDroppedStats);
    FRIEND_TEST(StatsdStatsTest, TestAtomMetricsStats);
    FRIEND_TEST(StatsdStatsTest, TestAtomSkippedStats);
    FRIEND_TEST(StatsdStatsTest, TestConfigRemove);
    FRIEND_TEST(StatsdStatsTest, TestHasHitDimensionGuardrail);
    FRIEND_TEST(StatsdStatsTest, TestInvalidConfigAdd);
    FRIEND_TEST(StatsdStatsTest, TestInvalidConfigMissingMetricId);
    FRIEND_TEST(StatsdStatsTest, TestInvalidConfigOnlyMetricId);
    FRIEND_TEST(StatsdStatsTest, TestNonPlatformAtomLog);
    FRIEND_TEST(StatsdStatsTest, TestPullAtomStats);
    FRIEND_TEST(StatsdStatsTest, TestQueueStats);
    FRIEND_TEST(StatsdStatsTest, TestRestrictedMetricsQueryStats);
    FRIEND_TEST(StatsdStatsTest, TestRestrictedMetricsStats);
    FRIEND_TEST(StatsdStatsTest, TestShardOffsetProvider);
    FRIEND_TEST(StatsdStatsTest, TestSocketLossStats);
    FRIEND_TEST(StatsdStatsTest, TestSocketLossStatsOverflowCounter);
    FRIEND_TEST(StatsdStatsTest, TestSubStats);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionAtomPulled);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionEnded);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionFlushed);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionPullThreadWakeup);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionStarted);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionStartedMaxActiveSubscriptions);
    FRIEND_TEST(StatsdStatsTest, TestSubscriptionStartedRemoveFinishedSubscription);
    FRIEND_TEST(StatsdStatsTest, TestSystemServerCrash);
    FRIEND_TEST(StatsdStatsTest, TestTimestampThreshold);
    FRIEND_TEST(StatsdStatsTest, TestValidConfigAdd);
};

InvalidConfigReason createInvalidConfigReasonWithMatcher(const InvalidConfigReasonEnum reason,
                                                         const int64_t matcherId);

InvalidConfigReason createInvalidConfigReasonWithMatcher(const InvalidConfigReasonEnum reason,
                                                         const int64_t metricId,
                                                         const int64_t matcherId);

InvalidConfigReason createInvalidConfigReasonWithPredicate(const InvalidConfigReasonEnum reason,
                                                           const int64_t conditionId);

InvalidConfigReason createInvalidConfigReasonWithPredicate(const InvalidConfigReasonEnum reason,
                                                           const int64_t metricId,
                                                           const int64_t conditionId);

InvalidConfigReason createInvalidConfigReasonWithState(const InvalidConfigReasonEnum reason,
                                                       const int64_t metricId,
                                                       const int64_t stateId);

InvalidConfigReason createInvalidConfigReasonWithAlert(const InvalidConfigReasonEnum reason,
                                                       const int64_t alertId);

InvalidConfigReason createInvalidConfigReasonWithAlert(const InvalidConfigReasonEnum reason,
                                                       const int64_t metricId,
                                                       const int64_t alertId);

InvalidConfigReason createInvalidConfigReasonWithAlarm(const InvalidConfigReasonEnum reason,
                                                       const int64_t alarmId);

InvalidConfigReason createInvalidConfigReasonWithSubscription(const InvalidConfigReasonEnum reason,
                                                              const int64_t subscriptionId);

InvalidConfigReason createInvalidConfigReasonWithSubscriptionAndAlarm(
        const InvalidConfigReasonEnum reason, int64_t subscriptionId, int64_t alarmId);

InvalidConfigReason createInvalidConfigReasonWithSubscriptionAndAlert(
        const InvalidConfigReasonEnum reason, int64_t subscriptionId, int64_t alertId);

}  // namespace statsd
}  // namespace os
}  // namespace android
