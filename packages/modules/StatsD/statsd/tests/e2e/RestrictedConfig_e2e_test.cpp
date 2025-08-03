// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gtest/gtest.h>

#include "flags/FlagProvider.h"
#include "storage/StorageManager.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

namespace {
const int32_t atomTag = 666;
const string delegatePackageName = "com.test.restricted.metrics.package";
const int32_t delegateUid = 10200;
const string configPackageName = "com.test.config.package";
int64_t metricId;
int64_t anotherMetricId;

StatsdConfig CreateConfigWithOneMetric() {
    StatsdConfig config;
    AtomMatcher atomMatcher = CreateSimpleAtomMatcher("testmatcher", atomTag);
    *config.add_atom_matcher() = atomMatcher;

    EventMetric eventMetric = createEventMetric("EventMetric", atomMatcher.id(), nullopt);
    metricId = eventMetric.id();
    *config.add_event_metric() = eventMetric;
    return config;
}
StatsdConfig CreateConfigWithTwoMetrics() {
    StatsdConfig config;
    AtomMatcher atomMatcher = CreateSimpleAtomMatcher("testmatcher", atomTag);
    *config.add_atom_matcher() = atomMatcher;

    EventMetric eventMetric = createEventMetric("EventMetric", atomMatcher.id(), nullopt);
    metricId = eventMetric.id();
    *config.add_event_metric() = eventMetric;
    EventMetric anotherEventMetric =
            createEventMetric("AnotherEventMetric", atomMatcher.id(), nullopt);
    anotherMetricId = anotherEventMetric.id();
    *config.add_event_metric() = anotherEventMetric;
    return config;
}

std::vector<std::unique_ptr<LogEvent>> CreateLogEvents(int64_t configAddedTimeNs) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateNonRestrictedLogEvent(atomTag, configAddedTimeNs + 10 * NS_PER_SEC));
    events.push_back(CreateNonRestrictedLogEvent(atomTag, configAddedTimeNs + 20 * NS_PER_SEC));
    events.push_back(CreateNonRestrictedLogEvent(atomTag, configAddedTimeNs + 30 * NS_PER_SEC));
    return events;
}

}  // Anonymous namespace

class RestrictedConfigE2ETest : public StatsServiceConfigTest {
protected:
    shared_ptr<MockStatsQueryCallback> mockStatsQueryCallback;
    const ConfigKey configKey = ConfigKey(kCallingUid, kConfigKey);
    vector<string> queryDataResult;
    vector<string> columnNamesResult;
    vector<int32_t> columnTypesResult;
    int32_t rowCountResult = 0;
    string error;

    void SetUp() override {
        if (!isAtLeastU()) {
            GTEST_SKIP();
        }
        StatsServiceConfigTest::SetUp();

        mockStatsQueryCallback = SharedRefBase::make<StrictMock<MockStatsQueryCallback>>();
        EXPECT_CALL(*mockStatsQueryCallback, sendResults(_, _, _, _))
                .Times(AnyNumber())
                .WillRepeatedly(Invoke(
                        [this](const vector<string>& queryData, const vector<string>& columnNames,
                               const vector<int32_t>& columnTypes, int32_t rowCount) {
                            queryDataResult = queryData;
                            columnNamesResult = columnNames;
                            columnTypesResult = columnTypes;
                            rowCountResult = rowCount;
                            error = "";
                            return Status::ok();
                        }));
        EXPECT_CALL(*mockStatsQueryCallback, sendFailure(_))
                .Times(AnyNumber())
                .WillRepeatedly(Invoke([this](const string& err) {
                    error = err;
                    queryDataResult.clear();
                    columnNamesResult.clear();
                    columnTypesResult.clear();
                    rowCountResult = 0;
                    return Status::ok();
                }));

        int64_t startTimeNs = getElapsedRealtimeNs();
        UidData uidData;
        *uidData.add_app_info() = createApplicationInfo(delegateUid, 1, "v2", delegatePackageName);
        *uidData.add_app_info() = createApplicationInfo(kCallingUid, 1, "v2", configPackageName);
        service->mUidMap->updateMap(startTimeNs, uidData);
    }
    void TearDown() override {
        if (!isAtLeastU()) {
            GTEST_SKIP();
        }
        Mock::VerifyAndClear(mockStatsQueryCallback.get());
        queryDataResult.clear();
        columnNamesResult.clear();
        columnTypesResult.clear();
        rowCountResult = 0;
        error = "";
        StatsServiceConfigTest::TearDown();
        FlagProvider::getInstance().resetOverrides();
        dbutils::deleteDb(configKey);
    }

    void verifyRestrictedData(int32_t expectedNumOfMetrics, int64_t metricIdToVerify = metricId,
                              bool shouldExist = true) {
        std::stringstream query;
        query << "SELECT * FROM metric_" << dbutils::reformatMetricId(metricIdToVerify);
        string err;
        std::vector<int32_t> columnTypes;
        std::vector<string> columnNames;
        std::vector<std::vector<std::string>> rows;
        if (shouldExist) {
            EXPECT_TRUE(
                    dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
            EXPECT_EQ(rows.size(), expectedNumOfMetrics);
        } else {
            // Expect that table is deleted.
            EXPECT_FALSE(
                    dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
        }
    }
};

TEST_F(RestrictedConfigE2ETest, RestrictedConfigNoReport) {
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name("delegate");
    sendConfig(config);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();

    for (auto& event : CreateLogEvents(configAddedTimeNs)) {
        service->OnLogEvent(event.get());
    }

    vector<uint8_t> output;
    ConfigKey configKey(kCallingUid, kConfigKey);
    service->getData(kConfigKey, kCallingUid, &output);

    EXPECT_TRUE(output.empty());
}

TEST_F(RestrictedConfigE2ETest, NonRestrictedConfigGetReport) {
    StatsdConfig config = CreateConfigWithOneMetric();
    sendConfig(config);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();

    for (auto& event : CreateLogEvents(configAddedTimeNs)) {
        service->OnLogEvent(event.get());
    }

    ConfigMetricsReport report = getReports(service->mProcessor, /*timestamp=*/10);
    EXPECT_EQ(report.metrics_size(), 1);
}

TEST_F(RestrictedConfigE2ETest, RestrictedShutdownFlushToRestrictedDB) {
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name("delegate");
    sendConfig(config);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();
    std::vector<std::unique_ptr<LogEvent>> logEvents = CreateLogEvents(configAddedTimeNs);
    for (const auto& e : logEvents) {
        service->OnLogEvent(e.get());
    }

    service->informDeviceShutdown();

    // Should not be written to non-restricted storage.
    EXPECT_FALSE(StorageManager::hasConfigMetricsReport(ConfigKey(kCallingUid, kConfigKey)));
    verifyRestrictedData(logEvents.size());
}

TEST_F(RestrictedConfigE2ETest, NonRestrictedOnShutdownWriteDataToDisk) {
    StatsdConfig config = CreateConfigWithOneMetric();
    sendConfig(config);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();
    for (auto& event : CreateLogEvents(configAddedTimeNs)) {
        service->OnLogEvent(event.get());
    }

    service->informDeviceShutdown();

    EXPECT_TRUE(StorageManager::hasConfigMetricsReport(ConfigKey(kCallingUid, kConfigKey)));
}

TEST_F(RestrictedConfigE2ETest, RestrictedConfigOnTerminateFlushToRestrictedDB) {
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name("delegate");
    sendConfig(config);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();
    std::vector<std::unique_ptr<LogEvent>> logEvents = CreateLogEvents(configAddedTimeNs);
    for (auto& event : logEvents) {
        service->OnLogEvent(event.get());
    }

    service->Terminate();

    EXPECT_FALSE(StorageManager::hasConfigMetricsReport(ConfigKey(kCallingUid, kConfigKey)));
    verifyRestrictedData(logEvents.size());
}

TEST_F(RestrictedConfigE2ETest, NonRestrictedConfigOnTerminateWriteDataToDisk) {
    StatsdConfig config = CreateConfigWithOneMetric();
    sendConfig(config);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();
    for (auto& event : CreateLogEvents(configAddedTimeNs)) {
        service->OnLogEvent(event.get());
    }

    service->Terminate();

    EXPECT_TRUE(StorageManager::hasConfigMetricsReport(ConfigKey(kCallingUid, kConfigKey)));
}

TEST_F(RestrictedConfigE2ETest, RestrictedConfigOnUpdateWithMetricRemoval) {
    StatsdConfig complexConfig = CreateConfigWithTwoMetrics();
    complexConfig.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(complexConfig);
    int64_t configAddedTimeNs = getElapsedRealtimeNs();
    std::vector<std::unique_ptr<LogEvent>> logEvents = CreateLogEvents(configAddedTimeNs);
    for (auto& event : logEvents) {
        service->OnLogEvent(event.get());
    }

    // Use query API to make sure data is flushed.
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(metricId);
    service->querySql(query.str(), /*minSqlClientVersion=*/0,
                      /*policyConfig=*/{}, mockStatsQueryCallback,
                      /*configKey=*/kConfigKey, /*configPackage=*/configPackageName,
                      /*callingUid=*/delegateUid);
    EXPECT_EQ(error, "");
    EXPECT_EQ(rowCountResult, logEvents.size());
    verifyRestrictedData(logEvents.size(), anotherMetricId, true);

    // Update config to have only one metric
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);

    // Make sure metric data is deleted.
    verifyRestrictedData(logEvents.size(), metricId, true);
    verifyRestrictedData(logEvents.size(), anotherMetricId, false);
}

TEST_F(RestrictedConfigE2ETest, TestSendRestrictedMetricsChangedBroadcast) {
    vector<int64_t> receivedMetricIds;
    int receiveCount = 0;
    shared_ptr<MockPendingIntentRef> pir = SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*pir, sendRestrictedMetricsChangedBroadcast(_))
            .Times(7)
            .WillRepeatedly(Invoke([&receivedMetricIds, &receiveCount](const vector<int64_t>& ids) {
                receiveCount++;
                receivedMetricIds = ids;
                return Status::ok();
            }));

    // Set the operation. No configs present so empty list is returned.
    vector<int64_t> returnedMetricIds;
    service->setRestrictedMetricsChangedOperation(kConfigKey, configPackageName, pir, delegateUid,
                                                  &returnedMetricIds);
    EXPECT_EQ(receiveCount, 0);
    EXPECT_THAT(returnedMetricIds, IsEmpty());

    // Add restricted config. Should receive one metric
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 1);
    EXPECT_THAT(receivedMetricIds, UnorderedElementsAre(metricId));

    // Config update, should receive two metrics.
    config = CreateConfigWithTwoMetrics();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 2);
    EXPECT_THAT(receivedMetricIds, UnorderedElementsAre(metricId, anotherMetricId));

    // Make config unrestricted. Should receive empty list.
    config.clear_restricted_metrics_delegate_package_name();
    sendConfig(config);
    EXPECT_EQ(receiveCount, 3);
    EXPECT_THAT(receivedMetricIds, IsEmpty());

    // Update the unrestricted config. Nothing should be sent.
    config = CreateConfigWithOneMetric();
    sendConfig(config);

    // Update config and make it restricted. Should receive one metric.
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 4);
    EXPECT_THAT(receivedMetricIds, UnorderedElementsAre(metricId));

    // Send an invalid config. Should receive empty list.
    auto invalidCountMetric = config.add_count_metric();
    invalidCountMetric->set_what(0);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 5);
    EXPECT_THAT(receivedMetricIds, IsEmpty());

    service->removeRestrictedMetricsChangedOperation(kConfigKey, configPackageName, delegateUid);

    // Nothing should be sent since the operation is removed.
    config = CreateConfigWithTwoMetrics();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);

    // Set the operation. Two metrics should be returned.
    returnedMetricIds.clear();
    service->setRestrictedMetricsChangedOperation(kConfigKey, configPackageName, pir, delegateUid,
                                                  &returnedMetricIds);
    EXPECT_THAT(returnedMetricIds, UnorderedElementsAre(metricId, anotherMetricId));
    EXPECT_EQ(receiveCount, 5);

    // Config update, should receive two metrics.
    config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 6);
    EXPECT_THAT(receivedMetricIds, UnorderedElementsAre(metricId));

    // Remove the config and verify an empty list is received
    service->removeConfiguration(kConfigKey, kCallingUid);
    EXPECT_EQ(receiveCount, 7);
    EXPECT_THAT(receivedMetricIds, IsEmpty());

    // Cleanup.
    service->removeRestrictedMetricsChangedOperation(kConfigKey, configPackageName, delegateUid);
}

TEST_F(RestrictedConfigE2ETest, TestSendRestrictedMetricsChangedBroadcastMultipleListeners) {
    const string configPackageName2 = "com.test.config.package2";
    const int32_t delegateUid2 = delegateUid + 1, delegateUid3 = delegateUid + 2;
    service->informOnePackage(configPackageName2, kCallingUid, 0, "", "", {});
    service->informOnePackage(delegatePackageName, delegateUid2, 0, "", "", {});
    service->informOnePackage("not.a.good.package", delegateUid3, 0, "", "", {});

    vector<int64_t> receivedMetricIds;
    int receiveCount = 0;
    shared_ptr<MockPendingIntentRef> pir = SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*pir, sendRestrictedMetricsChangedBroadcast(_))
            .Times(2)
            .WillRepeatedly(Invoke([&receivedMetricIds, &receiveCount](const vector<int64_t>& ids) {
                receiveCount++;
                receivedMetricIds = ids;
                return Status::ok();
            }));

    int receiveCount2 = 0;
    vector<int64_t> receivedMetricIds2;
    shared_ptr<MockPendingIntentRef> pir2 = SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*pir2, sendRestrictedMetricsChangedBroadcast(_))
            .Times(2)
            .WillRepeatedly(
                    Invoke([&receivedMetricIds2, &receiveCount2](const vector<int64_t>& ids) {
                        receiveCount2++;
                        receivedMetricIds2 = ids;
                        return Status::ok();
                    }));

    // This one should never be called.
    shared_ptr<MockPendingIntentRef> pir3 = SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    EXPECT_CALL(*pir3, sendRestrictedMetricsChangedBroadcast(_)).Times(0);

    // Set the operations. No configs present so empty list is returned.
    vector<int64_t> returnedMetricIds;
    service->setRestrictedMetricsChangedOperation(kConfigKey, configPackageName, pir, delegateUid,
                                                  &returnedMetricIds);
    EXPECT_EQ(receiveCount, 0);
    EXPECT_THAT(returnedMetricIds, IsEmpty());

    vector<int64_t> returnedMetricIds2;
    service->setRestrictedMetricsChangedOperation(kConfigKey, configPackageName2, pir2,
                                                  delegateUid2, &returnedMetricIds2);
    EXPECT_EQ(receiveCount2, 0);
    EXPECT_THAT(returnedMetricIds2, IsEmpty());

    // Represents a package listening for changes but doesn't match the restricted package in the
    // config.
    vector<int64_t> returnedMetricIds3;
    service->setRestrictedMetricsChangedOperation(kConfigKey, configPackageName, pir3, delegateUid3,
                                                  &returnedMetricIds3);
    EXPECT_THAT(returnedMetricIds3, IsEmpty());

    // Add restricted config. Should receive one metric on pir1 and 2.
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 1);
    EXPECT_THAT(receivedMetricIds, UnorderedElementsAre(metricId));
    EXPECT_EQ(receiveCount2, 1);
    EXPECT_THAT(receivedMetricIds2, UnorderedElementsAre(metricId));

    // Config update, should receive two metrics on pir1 and 2.
    config = CreateConfigWithTwoMetrics();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);
    EXPECT_EQ(receiveCount, 2);
    EXPECT_THAT(receivedMetricIds, UnorderedElementsAre(metricId, anotherMetricId));
    EXPECT_EQ(receiveCount2, 2);
    EXPECT_THAT(receivedMetricIds2, UnorderedElementsAre(metricId, anotherMetricId));

    // Cleanup.
    service->removeRestrictedMetricsChangedOperation(kConfigKey, configPackageName, delegateUid);
    service->removeRestrictedMetricsChangedOperation(kConfigKey, configPackageName2, delegateUid2);
    service->removeRestrictedMetricsChangedOperation(kConfigKey, configPackageName, delegateUid3);
}

TEST_F(RestrictedConfigE2ETest, TestSendRestrictedMetricsChangedBroadcastMultipleMatchedConfigs) {
    const int32_t callingUid2 = kCallingUid + 1;
    service->informOnePackage(configPackageName, callingUid2, 0, "", "", {});

    // Add restricted config.
    StatsdConfig config = CreateConfigWithOneMetric();
    config.set_restricted_metrics_delegate_package_name(delegatePackageName);
    sendConfig(config);

    // Add a second config.
    const int64_t metricId2 = 42;
    config.mutable_event_metric(0)->set_id(42);
    string str;
    config.SerializeToString(&str);
    std::vector<uint8_t> configAsVec(str.begin(), str.end());
    service->addConfiguration(kConfigKey, configAsVec, callingUid2);

    // Set the operation. Matches multiple configs so a union of metrics are returned.
    shared_ptr<MockPendingIntentRef> pir = SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    vector<int64_t> returnedMetricIds;
    service->setRestrictedMetricsChangedOperation(kConfigKey, configPackageName, pir, delegateUid,
                                                  &returnedMetricIds);
    EXPECT_THAT(returnedMetricIds, UnorderedElementsAre(metricId, metricId2));

    // Cleanup.
    service->removeRestrictedMetricsChangedOperation(kConfigKey, configPackageName, delegateUid);

    ConfigKey cfgKey(callingUid2, kConfigKey);
    service->removeConfiguration(kConfigKey, callingUid2);
    service->mProcessor->onDumpReport(cfgKey, getElapsedRealtimeNs(),
                                      false /* include_current_bucket*/, true /* erase_data */,
                                      ADB_DUMP, NO_TIME_CONSTRAINTS, nullptr);
}
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
