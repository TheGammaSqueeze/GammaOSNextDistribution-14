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

#include <vector>

#include "android-base/stringprintf.h"
#include "flags/FlagProvider.h"
#include "src/StatsLogProcessor.h"
#include "src/state/StateTracker.h"
#include "src/stats_log_util.h"
#include "src/storage/StorageManager.h"
#include "src/utils/RestrictedPolicyManager.h"
#include "stats_annotations.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

using base::StringPrintf;

#ifdef __ANDROID__

namespace {
const int64_t oneMonthLater = getWallClockNs() + 31 * 24 * 3600 * NS_PER_SEC;
const int64_t configId = 12345;
const string delegate_package_name = "com.test.restricted.metrics.package";
const int32_t delegate_uid = 1005;
const string config_package_name = "com.test.config.package";
const int32_t config_app_uid = 123;
const ConfigKey configKey(config_app_uid, configId);
const int64_t eightDaysAgo = getWallClockNs() - 8 * 24 * 3600 * NS_PER_SEC;
const int64_t oneDayAgo = getWallClockNs() - 1 * 24 * 3600 * NS_PER_SEC;
}  // anonymous namespace

// Setup for test fixture.
class RestrictedEventMetricE2eTest : public ::testing::Test {
protected:
    shared_ptr<MockStatsQueryCallback> mockStatsQueryCallback;
    vector<string> queryDataResult;
    vector<string> columnNamesResult;
    vector<int32_t> columnTypesResult;
    int32_t rowCountResult = 0;
    string error;
    sp<UidMap> uidMap;
    sp<StatsLogProcessor> processor;
    int32_t atomTag;
    int64_t restrictedMetricId;
    int64_t configAddedTimeNs;
    StatsdConfig config;

private:
    void SetUp() override {
        if (!isAtLeastU()) {
            GTEST_SKIP();
        }

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

        atomTag = 999;
        AtomMatcher restrictedAtomMatcher = CreateSimpleAtomMatcher("restricted_matcher", atomTag);
        *config.add_atom_matcher() = restrictedAtomMatcher;

        EventMetric restrictedEventMetric =
                createEventMetric("RestrictedMetricLogged", restrictedAtomMatcher.id(), nullopt);
        *config.add_event_metric() = restrictedEventMetric;
        restrictedMetricId = restrictedEventMetric.id();

        config.set_restricted_metrics_delegate_package_name(delegate_package_name.c_str());

        const int64_t baseTimeNs = 0;                     // 0:00
        configAddedTimeNs = baseTimeNs + 1 * NS_PER_SEC;  // 0:01

        uidMap = new UidMap();
        uidMap->updateApp(configAddedTimeNs, delegate_package_name,
                          /*uid=*/delegate_uid, /*versionCode=*/1,
                          /*versionString=*/"v2",
                          /*installer=*/"", /*certificateHash=*/{});
        uidMap->updateApp(configAddedTimeNs + 1, config_package_name,
                          /*uid=*/config_app_uid, /*versionCode=*/1,
                          /*versionString=*/"v2",
                          /*installer=*/"", /*certificateHash=*/{});

        processor = CreateStatsLogProcessor(baseTimeNs, configAddedTimeNs, config, configKey,
                                            /*puller=*/nullptr, /*atomTag=*/0, uidMap);
    }

    void TearDown() override {
        Mock::VerifyAndClear(mockStatsQueryCallback.get());
        queryDataResult.clear();
        columnNamesResult.clear();
        columnTypesResult.clear();
        rowCountResult = 0;
        error = "";
        dbutils::deleteDb(configKey);
        dbutils::deleteDb(ConfigKey(config_app_uid + 1, configId));
        FlagProvider::getInstance().resetOverrides();
    }
};

TEST_F(RestrictedEventMetricE2eTest, TestQueryThreeEvents) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 200));
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 300));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 3);
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,  // wallClockNs
                                             _,  // field_1
                                             to_string(atomTag), to_string(configAddedTimeNs + 200),
                                             _,  // wallClockNs
                                             _,  // field_1
                                             to_string(atomTag), to_string(configAddedTimeNs + 300),
                                             _,  // wallClockNs
                                             _   // field_1
                                             ));

    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));

    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestInvalidSchemaIncreasingFieldCount) {
    std::vector<std::unique_ptr<LogEvent>> events;

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomTag);
    AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY,
                                   ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC);
    AStatsEvent_overwriteTimestamp(statsEvent, configAddedTimeNs + 200);
    // This event has two extra fields
    AStatsEvent_writeString(statsEvent, "111");
    AStatsEvent_writeInt32(statsEvent, 11);
    AStatsEvent_writeFloat(statsEvent, 11.0);
    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));
    events.push_back(std::move(logEvent));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
        processor->WriteDataToDisk(DEVICE_SHUTDOWN, FAST,
                                   event->GetElapsedTimestampNs() + 20 * NS_PER_SEC,
                                   getWallClockNs());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 1);
    // Event 2 rejected.
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,  // wallClockNs
                                             _   // field_1
                                             ));

    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));

    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestInvalidSchemaDecreasingFieldCount) {
    std::vector<std::unique_ptr<LogEvent>> events;

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomTag);
    AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY,
                                   ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC);
    AStatsEvent_overwriteTimestamp(statsEvent, configAddedTimeNs + 100);
    // This event has one extra field.
    AStatsEvent_writeString(statsEvent, "111");
    AStatsEvent_writeInt32(statsEvent, 11);
    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());

    events.push_back(std::move(logEvent));
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 200));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
        processor->WriteDataToDisk(DEVICE_SHUTDOWN, FAST,
                                   event->GetElapsedTimestampNs() + 20 * NS_PER_SEC,
                                   getWallClockNs());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 1);
    // Event 2 Rejected
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,             // wallClockNs
                                             "111",         // field_1
                                             to_string(11)  // field_2
                                             ));

    EXPECT_THAT(columnNamesResult, ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs",
                                               "field_1", "field_2"));

    EXPECT_THAT(columnTypesResult, ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER,
                                               SQLITE_TEXT, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestInvalidSchemaDifferentFieldType) {
    std::vector<std::unique_ptr<LogEvent>> events;

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomTag);
    AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY,
                                   ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC);
    AStatsEvent_overwriteTimestamp(statsEvent, configAddedTimeNs + 200);
    // This event has a string instead of an int field
    AStatsEvent_writeString(statsEvent, "test_string");
    std::unique_ptr<LogEvent> logEvent = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, logEvent.get());

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));
    events.push_back(std::move(logEvent));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
        processor->WriteDataToDisk(DEVICE_SHUTDOWN, FAST,
                                   event->GetElapsedTimestampNs() + 20 * NS_PER_SEC,
                                   getWallClockNs());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 1);
    // Event 2 rejected.
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,  // wallClockNs
                                             _   // field_1
                                             ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestNewMetricSchemaAcrossReboot) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    processor->OnLogEvent(event1.get());

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult,
                ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime),
                            _,  // wallTimestampNs
                            _   // field_1
                            ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));

    // Create a new processor to simulate a reboot
    auto processor2 =
            CreateStatsLogProcessor(/*baseTimeNs=*/0, configAddedTimeNs, config, configKey,
                                    /*puller=*/nullptr, /*atomTag=*/0, uidMap);

    // Create a restricted event with one extra field.
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, atomTag);
    AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY,
                                   ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC);
    AStatsEvent_overwriteTimestamp(statsEvent, originalEventElapsedTime + 100);
    // This event has one extra field.
    AStatsEvent_writeString(statsEvent, "111");
    AStatsEvent_writeInt32(statsEvent, 11);
    std::unique_ptr<LogEvent> event2 = std::make_unique<LogEvent>(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, event2.get());
    processor2->OnLogEvent(event2.get());

    processor2->querySql(query.str(), /*minSqlClientVersion=*/0,
                         /*policyConfig=*/{}, mockStatsQueryCallback,
                         /*configKey=*/configId, /*configPackage=*/config_package_name,
                         /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult,
                ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime + 100),
                            _,               // wallTimestampNs
                            to_string(111),  // field_1
                            to_string(11)    // field_2
                            ));
    EXPECT_THAT(columnNamesResult, ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs",
                                               "field_1", "field_2"));
    EXPECT_THAT(columnTypesResult, ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER,
                                               SQLITE_TEXT, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestOneEventMultipleUids) {
    uidMap->updateApp(configAddedTimeNs, delegate_package_name,
                      /*uid=*/delegate_uid + 1, /*versionCode=*/1,
                      /*versionString=*/"v2",
                      /*installer=*/"", /*certificateHash=*/{});
    uidMap->updateApp(configAddedTimeNs + 1, config_package_name,
                      /*uid=*/config_app_uid + 1, /*versionCode=*/1,
                      /*versionString=*/"v2",
                      /*installer=*/"", /*certificateHash=*/{});

    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,  // wallClockNs
                                             _   // field_1
                                             ));
}

TEST_F(RestrictedEventMetricE2eTest, TestOneEventStaticUid) {
    ConfigKey key2(2000, configId);  // shell uid
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, key2, config);

    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/"AID_SHELL",
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,  // wallClockNs
                                             _   // field_1
                                             ));
    dbutils::deleteDb(key2);
}

TEST_F(RestrictedEventMetricE2eTest, TestTooManyConfigsAmbiguousQuery) {
    ConfigKey key2(config_app_uid + 1, configId);
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, key2, config);

    uidMap->updateApp(configAddedTimeNs, delegate_package_name,
                      /*uid=*/delegate_uid + 1, /*versionCode=*/1,
                      /*versionString=*/"v2",
                      /*installer=*/"", /*certificateHash=*/{});
    uidMap->updateApp(configAddedTimeNs + 1, config_package_name.c_str(),
                      /*uid=*/config_app_uid + 1, /*versionCode=*/1,
                      /*versionString=*/"v2",
                      /*installer=*/"", /*certificateHash=*/{});

    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(error, "Ambiguous ConfigKey");
    dbutils::deleteDb(key2);
}

TEST_F(RestrictedEventMetricE2eTest, TestUnknownConfigPackage) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/"unknown.config.package",
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(error, "No configs found matching the config key");
}

TEST_F(RestrictedEventMetricE2eTest, TestUnknownDelegatePackage) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid + 1);

    EXPECT_EQ(error, "No matching configs for restricted metrics delegate");
}

TEST_F(RestrictedEventMetricE2eTest, TestUnsupportedDatabaseVersion) {
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/INT_MAX,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_THAT(error, StartsWith("Unsupported sqlite version"));
}

TEST_F(RestrictedEventMetricE2eTest, TestInvalidQuery) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    std::stringstream query;
    query << "SELECT * FROM invalid_metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_THAT(error, StartsWith("failed to query db"));
}

TEST_F(RestrictedEventMetricE2eTest, TestEnforceTtlRemovesOldEvents) {
    int64_t currentWallTimeNs = getWallClockNs();
    // 8 days are used here because the TTL threshold is 7 days.
    int64_t eightDaysAgo = currentWallTimeNs - 8 * 24 * 3600 * NS_PER_SEC;
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(eightDaysAgo);

    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);
    processor->WriteDataToDisk(DEVICE_SHUTDOWN, FAST, originalEventElapsedTime + 20 * NS_PER_SEC,
                               getWallClockNs());
    processor->EnforceDataTtls(currentWallTimeNs, originalEventElapsedTime + 100);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    ASSERT_EQ(rows.size(), 0);
}

TEST_F(RestrictedEventMetricE2eTest, TestConfigRemovalDeletesData) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }
    // Query to make sure data is flushed
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    processor->OnConfigRemoved(configKey);

    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_FALSE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));

    EXPECT_THAT(err, StartsWith("unable to open database file"));
}

TEST_F(RestrictedEventMetricE2eTest, TestConfigRemovalDeletesDataWithoutFlush) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }
    processor->OnConfigRemoved(configKey);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_FALSE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));

    EXPECT_THAT(err, StartsWith("unable to open database file"));
}

TEST_F(RestrictedEventMetricE2eTest, TestConfigUpdateRestrictedDelegateCleared) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Update the existing config with no delegate
    config.clear_restricted_metrics_delegate_package_name();
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_FALSE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    EXPECT_EQ(rows.size(), 0);
    EXPECT_THAT(err, StartsWith("unable to open database file"));
    dbutils::deleteDb(configKey);
}

TEST_F(RestrictedEventMetricE2eTest, TestNonModularConfigUpdateRestrictedDelegate) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Update the existing config without modular update
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config, false);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_FALSE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    EXPECT_EQ(rows.size(), 0);
    EXPECT_THAT(err, StartsWith("no such table"));
    dbutils::deleteDb(configKey);
}

TEST_F(RestrictedEventMetricE2eTest, TestModularConfigUpdateNewRestrictedDelegate) {
    config.clear_restricted_metrics_delegate_package_name();
    // Update the existing config without a restricted delegate
    processor->OnConfigUpdated(configAddedTimeNs + 10, configKey, config);

    // Update the existing config with a new restricted delegate
    config.set_restricted_metrics_delegate_package_name("new.delegate.package");
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config);

    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 2 * NS_PER_SEC));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = configAddedTimeNs + 100 * NS_PER_SEC;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(configKey, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    ASSERT_EQ(reports.reports_size(), 0);

    //  Assert the config update was not modular and a RestrictedEventMetricProducer was created.
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    EXPECT_EQ(rows.size(), 1);
    EXPECT_THAT(rows[0],
                ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 2 * NS_PER_SEC),
                            _,  // wallClockNs
                            _   // field_1
                            ));
    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypes,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestModularConfigUpdateChangeRestrictedDelegate) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    // Update the existing config with a new restricted delegate
    int32_t newDelegateUid = delegate_uid + 1;
    config.set_restricted_metrics_delegate_package_name("new.delegate.package");
    uidMap->updateApp(configAddedTimeNs, "new.delegate.package",
                      /*uid=*/newDelegateUid, /*versionCode=*/1,
                      /*versionString=*/"v2",
                      /*installer=*/"", /*certificateHash=*/{});
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/newDelegateUid);

    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(configAddedTimeNs + 100),
                                             _,  // wallClockNs
                                             _   // field_1
                                             ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestInvalidConfigUpdateRestrictedDelegate) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    EventMetric metricWithoutMatcher = createEventMetric("metricWithoutMatcher", 999999, nullopt);
    *config.add_event_metric() = metricWithoutMatcher;
    // Update the existing config with an invalid config update
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_FALSE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    EXPECT_EQ(rows.size(), 0);
    EXPECT_THAT(err, StartsWith("unable to open database file"));
}

TEST_F(RestrictedEventMetricE2eTest, TestRestrictedConfigUpdateDoesNotUpdateUidMap) {
    auto& configKeyMap = processor->getUidMap()->mLastUpdatePerConfigKey;
    EXPECT_EQ(configKeyMap.find(configKey), configKeyMap.end());
}

TEST_F(RestrictedEventMetricE2eTest, TestRestrictedConfigUpdateAddsDelegateRemovesUidMapEntry) {
    auto& configKeyMap = processor->getUidMap()->mLastUpdatePerConfigKey;
    config.clear_restricted_metrics_delegate_package_name();
    // Update the existing config without a restricted delegate
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config);
    EXPECT_NE(configKeyMap.find(configKey), configKeyMap.end());
    // Update the existing config with a new restricted delegate
    config.set_restricted_metrics_delegate_package_name(delegate_package_name.c_str());
    processor->OnConfigUpdated(configAddedTimeNs + 1 * NS_PER_SEC, configKey, config);
    EXPECT_EQ(configKeyMap.find(configKey), configKeyMap.end());
}

TEST_F(RestrictedEventMetricE2eTest, TestLogEventsEnforceTtls) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    // 2 hours used here because the TTL check period is 1 hour.
    int64_t newEventElapsedTime = configAddedTimeNs + 2 * 3600 * NS_PER_SEC + 1;  // 2 hrs later
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(eightDaysAgo);
    std::unique_ptr<LogEvent> event2 =
            CreateRestrictedLogEvent(atomTag, originalEventElapsedTime + 100);
    event2->setLogdWallClockTimestampNs(oneDayAgo);
    std::unique_ptr<LogEvent> event3 = CreateRestrictedLogEvent(atomTag, newEventElapsedTime);
    event3->setLogdWallClockTimestampNs(currentWallTimeNs);

    processor->mLastTtlTime = originalEventElapsedTime;
    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);
    processor->OnLogEvent(event2.get(), newEventElapsedTime);
    processor->OnLogEvent(event3.get(), newEventElapsedTime + 100);
    processor->flushRestrictedDataLocked(newEventElapsedTime);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    ASSERT_EQ(rows.size(), 2);
    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypes,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
    EXPECT_THAT(rows[0], ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime + 100),
                                     to_string(oneDayAgo), _));
    EXPECT_THAT(rows[1], ElementsAre(to_string(atomTag), to_string(newEventElapsedTime),
                                     to_string(currentWallTimeNs), _));
}

TEST_F(RestrictedEventMetricE2eTest, TestLogEventsDoesNotEnforceTtls) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    // 30 min used here because the TTL check period is 1 hour.
    int64_t newEventElapsedTime = configAddedTimeNs + (3600 * NS_PER_SEC) / 2;  // 30 min later
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(eightDaysAgo);
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(atomTag, newEventElapsedTime);
    event2->setLogdWallClockTimestampNs(currentWallTimeNs);

    processor->mLastTtlTime = originalEventElapsedTime;
    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);
    processor->OnLogEvent(event2.get(), newEventElapsedTime);
    processor->flushRestrictedDataLocked(newEventElapsedTime);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    ASSERT_EQ(rows.size(), 2);
    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypes,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
    EXPECT_THAT(rows[0], ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime),
                                     to_string(eightDaysAgo), _));
    EXPECT_THAT(rows[1], ElementsAre(to_string(atomTag), to_string(newEventElapsedTime),
                                     to_string(currentWallTimeNs), _));
}

TEST_F(RestrictedEventMetricE2eTest, TestQueryEnforceTtls) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    // 30 min used here because the TTL check period is 1 hour.
    int64_t newEventElapsedTime = configAddedTimeNs + (3600 * NS_PER_SEC) / 2;  // 30 min later
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(eightDaysAgo);
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(atomTag, newEventElapsedTime);
    event2->setLogdWallClockTimestampNs(currentWallTimeNs);

    processor->mLastTtlTime = originalEventElapsedTime;
    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);
    processor->OnLogEvent(event2.get(), newEventElapsedTime);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);

    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult, ElementsAre(to_string(atomTag), to_string(newEventElapsedTime),
                                             to_string(currentWallTimeNs),
                                             _  // field_1
                                             ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestNotFlushed) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get(), event->GetElapsedTimestampNs());
    }
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_FALSE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    EXPECT_EQ(rows.size(), 0);
}

TEST_F(RestrictedEventMetricE2eTest, TestEnforceDbGuardrails) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime =
            configAddedTimeNs + (3600 * NS_PER_SEC) * 2;  // 2 hours after boot
    // 2 hours used here because the TTL check period is 1 hour.
    int64_t dbEnforcementTimeNs =
            configAddedTimeNs + (3600 * NS_PER_SEC) * 4;  // 4 hours after boot
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(currentWallTimeNs);
    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);

    EXPECT_TRUE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult,
                ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime),
                            to_string(currentWallTimeNs),
                            _  // field_1
                            ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));

    processor->enforceDbGuardrailsIfNecessaryLocked(oneMonthLater, dbEnforcementTimeNs);

    EXPECT_FALSE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));
}

TEST_F(RestrictedEventMetricE2eTest, TestEnforceDbGuardrailsDoesNotDeleteBeforeGuardrail) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime =
            configAddedTimeNs + (3600 * NS_PER_SEC) * 2;  // 2 hours after boot
    // 2 hours used here because the TTL check period is 1 hour.
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(currentWallTimeNs);
    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);

    EXPECT_TRUE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));
    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult,
                ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime),
                            to_string(currentWallTimeNs),
                            _  // field_1
                            ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));

    processor->enforceDbGuardrailsIfNecessaryLocked(oneMonthLater, originalEventElapsedTime);

    EXPECT_TRUE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));
}

TEST_F(RestrictedEventMetricE2eTest, TestFlushInWriteDataToDisk) {
    std::vector<std::unique_ptr<LogEvent>> events;
    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));
    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get(), event->GetElapsedTimestampNs());
    }

    // Call WriteDataToDisk after 20 second because cooldown period is 15 second.
    processor->WriteDataToDisk(DEVICE_SHUTDOWN, FAST, 20 * NS_PER_SEC, getWallClockNs());

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    EXPECT_EQ(rows.size(), 1);
}

TEST_F(RestrictedEventMetricE2eTest, TestFlushPeriodically) {
    std::vector<std::unique_ptr<LogEvent>> events;

    events.push_back(CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100));
    events.push_back(CreateRestrictedLogEvent(
            atomTag, configAddedTimeNs + StatsdStats::kMinFlushRestrictedPeriodNs + 1));

    // Send log events to StatsLogProcessor.
    for (auto& event : events) {
        processor->OnLogEvent(event.get(), event->GetElapsedTimestampNs());
    }

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    // Only first event is flushed when second event is logged.
    EXPECT_EQ(rows.size(), 1);
}

TEST_F(RestrictedEventMetricE2eTest, TestOnLogEventMalformedDbNameDeleted) {
    vector<string> emptyData;
    string fileName = StringPrintf("%s/malformedname.db", STATS_RESTRICTED_DATA_DIR);
    StorageManager::writeFile(fileName.c_str(), emptyData.data(), emptyData.size());
    EXPECT_TRUE(StorageManager::hasFile(fileName.c_str()));
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    // 2 hours used here because the TTL check period is 1 hour.
    int64_t newEventElapsedTime = configAddedTimeNs + 2 * 3600 * NS_PER_SEC + 1;  // 2 hrs later
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(atomTag, newEventElapsedTime);
    event2->setLogdWallClockTimestampNs(getWallClockNs());

    processor->mLastTtlTime = originalEventElapsedTime;
    // Send log events to StatsLogProcessor.
    processor->OnLogEvent(event2.get(), newEventElapsedTime);

    EXPECT_FALSE(StorageManager::hasFile(fileName.c_str()));
    StorageManager::deleteFile(fileName.c_str());
}

TEST_F(RestrictedEventMetricE2eTest, TestRestrictedMetricSavesTtlToDisk) {
    metadata::StatsMetadataList result;
    processor->WriteMetadataToProto(getWallClockNs(), configAddedTimeNs, &result);

    ASSERT_EQ(result.stats_metadata_size(), 1);
    metadata::StatsMetadata statsMetadata = result.stats_metadata(0);
    EXPECT_EQ(statsMetadata.config_key().config_id(), configId);
    EXPECT_EQ(statsMetadata.config_key().uid(), config_app_uid);

    ASSERT_EQ(statsMetadata.metric_metadata_size(), 1);
    metadata::MetricMetadata metricMetadata = statsMetadata.metric_metadata(0);
    EXPECT_EQ(metricMetadata.metric_id(), restrictedMetricId);
    EXPECT_EQ(metricMetadata.restricted_category(), CATEGORY_UNKNOWN);
    result.Clear();

    std::unique_ptr<LogEvent> event = CreateRestrictedLogEvent(atomTag, configAddedTimeNs + 100);
    processor->OnLogEvent(event.get());
    processor->WriteMetadataToProto(getWallClockNs(), configAddedTimeNs, &result);

    ASSERT_EQ(result.stats_metadata_size(), 1);
    statsMetadata = result.stats_metadata(0);
    EXPECT_EQ(statsMetadata.config_key().config_id(), configId);
    EXPECT_EQ(statsMetadata.config_key().uid(), config_app_uid);

    ASSERT_EQ(statsMetadata.metric_metadata_size(), 1);
    metricMetadata = statsMetadata.metric_metadata(0);
    EXPECT_EQ(metricMetadata.metric_id(), restrictedMetricId);
    EXPECT_EQ(metricMetadata.restricted_category(), CATEGORY_DIAGNOSTIC);
}

TEST_F(RestrictedEventMetricE2eTest, TestRestrictedMetricLoadsTtlFromDisk) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(atomTag, originalEventElapsedTime);
    event1->setLogdWallClockTimestampNs(eightDaysAgo);
    processor->OnLogEvent(event1.get(), originalEventElapsedTime);
    processor->flushRestrictedDataLocked(originalEventElapsedTime);
    int64_t wallClockNs = 1584991200 * NS_PER_SEC;  // random time
    int64_t metadataWriteTime = originalEventElapsedTime + 5000 * NS_PER_SEC;
    processor->SaveMetadataToDisk(wallClockNs, metadataWriteTime);

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    ASSERT_EQ(rows.size(), 1);
    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypes,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
    EXPECT_THAT(rows[0], ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime),
                                     to_string(eightDaysAgo), _));

    auto processor2 =
            CreateStatsLogProcessor(/*baseTimeNs=*/0, configAddedTimeNs, config, configKey,
                                    /*puller=*/nullptr, /*atomTag=*/0, uidMap);
    // 2 hours used here because the TTL check period is 1 hour.
    int64_t newEventElapsedTime = configAddedTimeNs + 2 * 3600 * NS_PER_SEC + 1;  // 2 hrs later
    processor2->LoadMetadataFromDisk(wallClockNs, newEventElapsedTime);

    // Log another event and check that the original TTL is maintained across reboot
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(atomTag, newEventElapsedTime);
    event2->setLogdWallClockTimestampNs(currentWallTimeNs);
    processor2->OnLogEvent(event2.get(), newEventElapsedTime);
    processor2->flushRestrictedDataLocked(newEventElapsedTime);

    columnTypes.clear();
    columnNames.clear();
    rows.clear();
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    ASSERT_EQ(rows.size(), 1);
    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypes,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
    EXPECT_THAT(rows[0], ElementsAre(to_string(atomTag), to_string(newEventElapsedTime),
                                     to_string(currentWallTimeNs), _));
}

TEST_F(RestrictedEventMetricE2eTest, TestNewRestrictionCategoryEventDeletesTable) {
    int64_t currentWallTimeNs = getWallClockNs();
    int64_t originalEventElapsedTime = configAddedTimeNs + 100;
    std::unique_ptr<LogEvent> event1 =
            CreateNonRestrictedLogEvent(atomTag, originalEventElapsedTime);
    processor->OnLogEvent(event1.get());

    std::stringstream query;
    query << "SELECT * FROM metric_" << dbutils::reformatMetricId(restrictedMetricId);
    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult,
                ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime),
                            _,  // wallTimestampNs
                            _   // field_1
                            ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));

    // Log a second event that will go into the cache
    std::unique_ptr<LogEvent> event2 =
            CreateNonRestrictedLogEvent(atomTag, originalEventElapsedTime + 100);
    processor->OnLogEvent(event2.get());

    // Log a third event with a different category
    std::unique_ptr<LogEvent> event3 =
            CreateRestrictedLogEvent(atomTag, originalEventElapsedTime + 200);
    processor->OnLogEvent(event3.get());

    processor->querySql(query.str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult,
                ElementsAre(to_string(atomTag), to_string(originalEventElapsedTime + 200),
                            _,  // wallTimestampNs
                            _   // field_1
                            ));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER, SQLITE_INTEGER));
}

TEST_F(RestrictedEventMetricE2eTest, TestDeviceInfoTableCreated) {
    std::string query = "SELECT * FROM device_info";
    processor->querySql(query.c_str(), /*minSqlClientVersion=*/0,
                        /*policyConfig=*/{}, mockStatsQueryCallback,
                        /*configKey=*/configId, /*configPackage=*/config_package_name,
                        /*callingUid=*/delegate_uid);
    EXPECT_EQ(rowCountResult, 1);
    EXPECT_THAT(queryDataResult, ElementsAre(_, _, _, _, _, _, _, _, _, _));
    EXPECT_THAT(columnNamesResult,
                ElementsAre("sdkVersion", "model", "product", "hardware", "device", "osBuild",
                            "fingerprint", "brand", "manufacturer", "board"));
    EXPECT_THAT(columnTypesResult,
                ElementsAre(SQLITE_INTEGER, SQLITE_TEXT, SQLITE_TEXT, SQLITE_TEXT, SQLITE_TEXT,
                            SQLITE_TEXT, SQLITE_TEXT, SQLITE_TEXT, SQLITE_TEXT, SQLITE_TEXT));
}
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
