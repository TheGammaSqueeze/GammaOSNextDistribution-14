#ifndef RESTRICTED_EVENT_METRIC_PRODUCER_H
#define RESTRICTED_EVENT_METRIC_PRODUCER_H

#include <gtest/gtest_prod.h>

#include "EventMetricProducer.h"
#include "utils/RestrictedPolicyManager.h"

namespace android {
namespace os {
namespace statsd {

class RestrictedEventMetricProducer : public EventMetricProducer {
public:
    RestrictedEventMetricProducer(
            const ConfigKey& key, const EventMetric& eventMetric, const int conditionIndex,
            const vector<ConditionState>& initialConditionCache, const sp<ConditionWizard>& wizard,
            const uint64_t protoHash, int64_t startTimeNs,
            const std::unordered_map<int, std::shared_ptr<Activation>>& eventActivationMap = {},
            const std::unordered_map<int, std::vector<std::shared_ptr<Activation>>>&
                    eventDeactivationMap = {},
            const vector<int>& slicedStateAtoms = {},
            const unordered_map<int, unordered_map<int, int64_t>>& stateGroupMap = {});

    void onMetricRemove() override;

    void enforceRestrictedDataTtl(sqlite3* db, int64_t wallClockNs);

    void flushRestrictedData() override;

    bool writeMetricMetadataToProto(metadata::MetricMetadata* metricMetadata) override;

    void loadMetricMetadataFromProto(const metadata::MetricMetadata& metricMetadata) override;

    inline StatsdRestrictionCategory getRestrictionCategory() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mRestrictedDataCategory;
    }

private:
    void onMatchedLogEventInternalLocked(
            const size_t matcherIndex, const MetricDimensionKey& eventKey,
            const ConditionKey& conditionKey, bool condition, const LogEvent& event,
            const std::map<int, HashableDimensionKey>& statePrimaryKeys) override;

    void onDumpReportLocked(const int64_t dumpTimeNs, const bool include_current_partial_bucket,
                            const bool erase_data, const DumpLatency dumpLatency,
                            std::set<string>* str_set,
                            android::util::ProtoOutputStream* protoOutput) override;

    void clearPastBucketsLocked(const int64_t dumpTimeNs) override;

    void dropDataLocked(const int64_t dropTimeNs) override;

    void deleteMetricTable();

    bool mIsMetricTableCreated = false;

    StatsdRestrictionCategory mRestrictedDataCategory;

    vector<LogEvent> mLogEvents;
};

}  // namespace statsd
}  // namespace os
}  // namespace android
#endif  // RESTRICTED_EVENT_METRIC_PRODUCER_H
