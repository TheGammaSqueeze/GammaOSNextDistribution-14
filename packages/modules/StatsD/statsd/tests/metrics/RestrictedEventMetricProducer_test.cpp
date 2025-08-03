#include "src/metrics/RestrictedEventMetricProducer.h"

#include <gtest/gtest.h>

#include "flags/FlagProvider.h"
#include "metrics_test_helper.h"
#include "stats_annotations.h"
#include "tests/statsd_test_util.h"
#include "utils/DbUtils.h"

using namespace testing;
using std::string;
using std::stringstream;
using std::vector;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {
const ConfigKey configKey(/*uid=*/0, /*id=*/12345);
const int64_t metricId1 = 123;
const int64_t metricId2 = 456;

bool metricTableExist(int64_t metricId) {
    stringstream query;
    query << "SELECT * FROM metric_" << metricId;
    vector<int32_t> columnTypes;
    vector<vector<string>> rows;
    vector<string> columnNames;
    string err;
    return dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err);
}
}  // anonymous namespace

class RestrictedEventMetricProducerTest : public Test {
protected:
    void SetUp() override {
        if (!isAtLeastU()) {
            GTEST_SKIP();
        }
    }
    void TearDown() override {
        if (!isAtLeastU()) {
            GTEST_SKIP();
        }
        dbutils::deleteDb(configKey);
        FlagProvider::getInstance().resetOverrides();
    }
};

TEST_F(RestrictedEventMetricProducerTest, TestOnMatchedLogEventMultipleEvents) {
    EventMetric metric;
    metric.set_id(metricId1);
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/-1,
                                           /*initialConditionCache=*/{}, new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(/*atomTag=*/123, /*timestampNs=*/1);
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(/*atomTag=*/123, /*timestampNs=*/3);

    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event1);
    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event2);
    producer.flushRestrictedData();

    stringstream query;
    query << "SELECT * FROM metric_" << metricId1;
    string err;
    vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    vector<vector<string>> rows;
    dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err);
    ASSERT_EQ(rows.size(), 2);
    EXPECT_EQ(columnTypes.size(),
              3 + event1->getValues().size());  // col 0:2 are reserved for metadata.
    EXPECT_EQ(/*tagId=*/rows[0][0], to_string(event1->GetTagId()));
    EXPECT_EQ(/*elapsedTimestampNs=*/rows[0][1], to_string(event1->GetElapsedTimestampNs()));
    EXPECT_EQ(/*elapsedTimestampNs=*/rows[1][1], to_string(event2->GetElapsedTimestampNs()));

    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
}

TEST_F(RestrictedEventMetricProducerTest, TestOnMatchedLogEventMultipleFields) {
    EventMetric metric;
    metric.set_id(metricId2);
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/-1,
                                           /*initialConditionCache=*/{}, new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, 1);
    AStatsEvent_addInt32Annotation(statsEvent, ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY,
                                   ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC);
    AStatsEvent_overwriteTimestamp(statsEvent, 1);

    AStatsEvent_writeString(statsEvent, "111");
    AStatsEvent_writeInt32(statsEvent, 11);
    AStatsEvent_writeFloat(statsEvent, 11.0);
    LogEvent logEvent(/*uid=*/0, /*pid=*/0);
    parseStatsEventToLogEvent(statsEvent, &logEvent);

    producer.onMatchedLogEvent(/*matcherIndex=1*/ 1, logEvent);
    producer.flushRestrictedData();

    stringstream query;
    query << "SELECT * FROM metric_" << metricId2;
    string err;
    vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    vector<vector<string>> rows;
    EXPECT_TRUE(dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err));
    ASSERT_EQ(rows.size(), 1);
    EXPECT_EQ(columnTypes.size(),
              3 + logEvent.getValues().size());  // col 0:2 are reserved for metadata.
    EXPECT_EQ(/*field1=*/rows[0][3], "111");
    EXPECT_EQ(/*field2=*/rows[0][4], "11");
    EXPECT_FLOAT_EQ(/*field3=*/std::stof(rows[0][5]), 11.0);

    EXPECT_THAT(columnNames, ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs",
                                         "field_1", "field_2", "field_3"));
}

TEST_F(RestrictedEventMetricProducerTest, TestOnMatchedLogEventWithCondition) {
    EventMetric metric;
    metric.set_id(metricId1);
    metric.set_condition(StringToId("SCREEN_ON"));
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/0,
                                           /*initialConditionCache=*/{ConditionState::kUnknown},
                                           new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(/*atomTag=*/123, /*timestampNs=*/1);
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(/*atomTag=*/123, /*timestampNs=*/3);

    producer.onConditionChanged(true, 0);
    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event1);
    producer.onConditionChanged(false, 1);
    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event2);
    producer.flushRestrictedData();

    std::stringstream query;
    query << "SELECT * FROM metric_" << metricId1;
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err);
    ASSERT_EQ(rows.size(), 1);
    EXPECT_EQ(columnTypes.size(), 3 + event1->getValues().size());
    EXPECT_EQ(/*elapsedTimestampNs=*/rows[0][1], to_string(event1->GetElapsedTimestampNs()));

    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
}

TEST_F(RestrictedEventMetricProducerTest, TestOnDumpReportNoOp) {
    EventMetric metric;
    metric.set_id(metricId1);
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/-1,
                                           /*initialConditionCache=*/{}, new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(/*timestampNs=*/1);
    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event1);
    ProtoOutputStream output;
    std::set<string> strSet;
    producer.onDumpReport(/*dumpTimeNs=*/10,
                          /*include_current_partial_bucket=*/true,
                          /*erase_data=*/true, FAST, &strSet, &output);

    ASSERT_EQ(output.size(), 0);
    ASSERT_EQ(strSet.size(), 0);
}

TEST_F(RestrictedEventMetricProducerTest, TestOnMetricRemove) {
    EventMetric metric;
    metric.set_id(metricId1);
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/-1,
                                           /*initialConditionCache=*/{}, new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);
    EXPECT_FALSE(metricTableExist(metricId1));

    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(/*timestampNs=*/1);
    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event1);
    producer.flushRestrictedData();
    EXPECT_TRUE(metricTableExist(metricId1));

    producer.onMetricRemove();
    EXPECT_FALSE(metricTableExist(metricId1));
}

TEST_F(RestrictedEventMetricProducerTest, TestRestrictedEventMetricTtlDeletesFirstEvent) {
    EventMetric metric;
    metric.set_id(metricId1);
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/-1,
                                           /*initialConditionCache=*/{}, new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);

    int64_t currentTimeNs = getWallClockNs();
    int64_t eightDaysAgo = currentTimeNs - 8 * 24 * 3600 * NS_PER_SEC;
    std::unique_ptr<LogEvent> event1 = CreateRestrictedLogEvent(/*atomTag=*/123, /*timestampNs=*/1);
    event1->setLogdWallClockTimestampNs(eightDaysAgo);
    std::unique_ptr<LogEvent> event2 = CreateRestrictedLogEvent(/*atomTag=*/123, /*timestampNs=*/3);
    event2->setLogdWallClockTimestampNs(currentTimeNs);

    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event1);
    producer.onMatchedLogEvent(/*matcherIndex=*/1, *event2);
    producer.flushRestrictedData();
    sqlite3* dbHandle = dbutils::getDb(configKey);
    producer.enforceRestrictedDataTtl(dbHandle, currentTimeNs + 100);
    dbutils::closeDb(dbHandle);

    std::stringstream query;
    query << "SELECT * FROM metric_" << metricId1;
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    dbutils::query(configKey, query.str(), rows, columnTypes, columnNames, err);
    ASSERT_EQ(rows.size(), 1);
    EXPECT_EQ(columnTypes.size(), 3 + event1->getValues().size());
    EXPECT_THAT(columnNames,
                ElementsAre("atomId", "elapsedTimestampNs", "wallTimestampNs", "field_1"));
    EXPECT_THAT(rows[0], ElementsAre(to_string(event2->GetTagId()),
                                     to_string(event2->GetElapsedTimestampNs()),
                                     to_string(currentTimeNs), _));
}

TEST_F(RestrictedEventMetricProducerTest, TestLoadMetricMetadataSetsCategory) {
    metadata::MetricMetadata metricMetadata;
    metricMetadata.set_metric_id(metricId1);
    metricMetadata.set_restricted_category(1);  // CATEGORY_DIAGNOSTIC
    EventMetric metric;
    metric.set_id(metricId1);
    RestrictedEventMetricProducer producer(configKey, metric,
                                           /*conditionIndex=*/-1,
                                           /*initialConditionCache=*/{}, new ConditionWizard(),
                                           /*protoHash=*/0x1234567890,
                                           /*startTimeNs=*/0);

    producer.loadMetricMetadataFromProto(metricMetadata);

    EXPECT_EQ(producer.getRestrictionCategory(), CATEGORY_DIAGNOSTIC);
}

}  // namespace statsd
}  // namespace os
}  // namespace android

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
