// Copyright (C) 2020 The Android Open Source Project
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

#include "src/metrics/parsing_utils/metrics_manager_util.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <private/android_filesystem_config.h>
#include <stdio.h>

#include <set>
#include <unordered_map>
#include <vector>

#include "src/condition/ConditionTracker.h"
#include "src/matchers/AtomMatchingTracker.h"
#include "src/metrics/CountMetricProducer.h"
#include "src/metrics/DurationMetricProducer.h"
#include "src/metrics/GaugeMetricProducer.h"
#include "src/metrics/KllMetricProducer.h"
#include "src/metrics/MetricProducer.h"
#include "src/metrics/NumericValueMetricProducer.h"
#include "src/state/StateManager.h"
#include "src/statsd_config.pb.h"
#include "tests/metrics/metrics_test_helper.h"
#include "tests/statsd_test_util.h"

using namespace testing;
using android::sp;
using android::os::statsd::Predicate;
using std::map;
using std::set;
using std::unordered_map;
using std::vector;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {
const int kConfigId = 12345;
const ConfigKey kConfigKey(0, kConfigId);
const long timeBaseSec = 1000;
const long kAlertId = 3;

sp<UidMap> uidMap = new UidMap();
sp<StatsPullerManager> pullerManager = new StatsPullerManager();
sp<AlarmMonitor> anomalyAlarmMonitor;
sp<AlarmMonitor> periodicAlarmMonitor;
unordered_map<int, vector<int>> allTagIdsToMatchersMap;
vector<sp<AtomMatchingTracker>> allAtomMatchingTrackers;
unordered_map<int64_t, int> atomMatchingTrackerMap;
vector<sp<ConditionTracker>> allConditionTrackers;
unordered_map<int64_t, int> conditionTrackerMap;
vector<sp<MetricProducer>> allMetricProducers;
unordered_map<int64_t, int> metricProducerMap;
vector<sp<AnomalyTracker>> allAnomalyTrackers;
unordered_map<int64_t, int> alertTrackerMap;
vector<sp<AlarmTracker>> allAlarmTrackers;
unordered_map<int, vector<int>> conditionToMetricMap;
unordered_map<int, vector<int>> trackerToMetricMap;
unordered_map<int, vector<int>> trackerToConditionMap;
unordered_map<int, vector<int>> activationAtomTrackerToMetricMap;
unordered_map<int, vector<int>> deactivationAtomTrackerToMetricMap;
vector<int> metricsWithActivation;
map<int64_t, uint64_t> stateProtoHashes;
set<int64_t> noReportMetricIds;

optional<InvalidConfigReason> initConfig(const StatsdConfig& config) {
    // initStatsdConfig returns nullopt if config is valid
    return initStatsdConfig(
            kConfigKey, config, uidMap, pullerManager, anomalyAlarmMonitor, periodicAlarmMonitor,
            timeBaseSec, timeBaseSec, allTagIdsToMatchersMap, allAtomMatchingTrackers,
            atomMatchingTrackerMap, allConditionTrackers, conditionTrackerMap, allMetricProducers,
            metricProducerMap, allAnomalyTrackers, allAlarmTrackers, conditionToMetricMap,
            trackerToMetricMap, trackerToConditionMap, activationAtomTrackerToMetricMap,
            deactivationAtomTrackerToMetricMap, alertTrackerMap, metricsWithActivation,
            stateProtoHashes, noReportMetricIds);
}

StatsdConfig buildCircleMatchers() {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_IS_ON"));

    SimpleAtomMatcher* simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(SCREEN_STATE_ATOM_ID);
    simpleAtomMatcher->add_field_value_matcher()->set_field(
            1 /*SCREEN_STATE_CHANGE__DISPLAY_STATE*/);
    simpleAtomMatcher->mutable_field_value_matcher(0)->set_eq_int(
            2 /*SCREEN_STATE_CHANGE__DISPLAY_STATE__STATE_ON*/);

    eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_ON_OR_OFF"));

    AtomMatcher_Combination* combination = eventMatcher->mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_matcher(StringToId("SCREEN_IS_ON"));
    // Circle dependency
    combination->add_matcher(StringToId("SCREEN_ON_OR_OFF"));

    return config;
}

StatsdConfig buildAlertWithUnknownMetric() {
    StatsdConfig config;
    config.set_id(12345);

    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    CountMetric* metric = config.add_count_metric();
    metric->set_id(3);
    metric->set_what(StringToId("ScreenTurnedOn"));
    metric->set_bucket(ONE_MINUTE);
    metric->mutable_dimensions_in_what()->set_field(SCREEN_STATE_ATOM_ID);
    metric->mutable_dimensions_in_what()->add_child()->set_field(1);

    auto alert = config.add_alert();
    alert->set_id(3);
    alert->set_metric_id(2);
    alert->set_num_buckets(10);
    alert->set_refractory_period_secs(100);
    alert->set_trigger_if_sum_gt(100);
    return config;
}

StatsdConfig buildMissingMatchers() {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_IS_ON"));

    SimpleAtomMatcher* simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(SCREEN_STATE_ATOM_ID);
    simpleAtomMatcher->add_field_value_matcher()->set_field(
            1 /*SCREEN_STATE_CHANGE__DISPLAY_STATE*/);
    simpleAtomMatcher->mutable_field_value_matcher(0)->set_eq_int(
            2 /*SCREEN_STATE_CHANGE__DISPLAY_STATE__STATE_ON*/);

    eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_ON_OR_OFF"));

    AtomMatcher_Combination* combination = eventMatcher->mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_matcher(StringToId("SCREEN_IS_ON"));
    // undefined matcher
    combination->add_matcher(StringToId("ABC"));

    return config;
}

StatsdConfig buildMissingPredicate() {
    StatsdConfig config;
    config.set_id(12345);

    CountMetric* metric = config.add_count_metric();
    metric->set_id(3);
    metric->set_what(StringToId("SCREEN_EVENT"));
    metric->set_bucket(ONE_MINUTE);
    metric->set_condition(StringToId("SOME_CONDITION"));

    AtomMatcher* eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_EVENT"));

    SimpleAtomMatcher* simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(2);

    return config;
}

StatsdConfig buildDimensionMetricsWithMultiTags() {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("BATTERY_VERY_LOW"));
    SimpleAtomMatcher* simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(2);

    eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("BATTERY_VERY_VERY_LOW"));
    simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(3);

    eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("BATTERY_LOW"));

    AtomMatcher_Combination* combination = eventMatcher->mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_matcher(StringToId("BATTERY_VERY_LOW"));
    combination->add_matcher(StringToId("BATTERY_VERY_VERY_LOW"));

    // Count process state changes, slice by uid, while SCREEN_IS_OFF
    CountMetric* metric = config.add_count_metric();
    metric->set_id(3);
    metric->set_what(StringToId("BATTERY_LOW"));
    metric->set_bucket(ONE_MINUTE);
    // This case is interesting. We want to dimension across two atoms.
    metric->mutable_dimensions_in_what()->add_child()->set_field(1);

    auto alert = config.add_alert();
    alert->set_id(kAlertId);
    alert->set_metric_id(3);
    alert->set_num_buckets(10);
    alert->set_refractory_period_secs(100);
    alert->set_trigger_if_sum_gt(100);
    return config;
}

StatsdConfig buildCirclePredicates() {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_IS_ON"));

    SimpleAtomMatcher* simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(SCREEN_STATE_ATOM_ID);
    simpleAtomMatcher->add_field_value_matcher()->set_field(
            1 /*SCREEN_STATE_CHANGE__DISPLAY_STATE*/);
    simpleAtomMatcher->mutable_field_value_matcher(0)->set_eq_int(
            2 /*SCREEN_STATE_CHANGE__DISPLAY_STATE__STATE_ON*/);

    eventMatcher = config.add_atom_matcher();
    eventMatcher->set_id(StringToId("SCREEN_IS_OFF"));

    simpleAtomMatcher = eventMatcher->mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(SCREEN_STATE_ATOM_ID);
    simpleAtomMatcher->add_field_value_matcher()->set_field(
            1 /*SCREEN_STATE_CHANGE__DISPLAY_STATE*/);
    simpleAtomMatcher->mutable_field_value_matcher(0)->set_eq_int(
            1 /*SCREEN_STATE_CHANGE__DISPLAY_STATE__STATE_OFF*/);

    auto condition = config.add_predicate();
    condition->set_id(StringToId("SCREEN_IS_ON"));
    SimplePredicate* simplePredicate = condition->mutable_simple_predicate();
    simplePredicate->set_start(StringToId("SCREEN_IS_ON"));
    simplePredicate->set_stop(StringToId("SCREEN_IS_OFF"));

    condition = config.add_predicate();
    condition->set_id(StringToId("SCREEN_IS_EITHER_ON_OFF"));

    Predicate_Combination* combination = condition->mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_predicate(StringToId("SCREEN_IS_ON"));
    combination->add_predicate(StringToId("SCREEN_IS_EITHER_ON_OFF"));

    return config;
}

StatsdConfig buildConfigWithDifferentPredicates() {
    StatsdConfig config;
    config.set_id(12345);

    auto pulledAtomMatcher =
            CreateSimpleAtomMatcher("SUBSYSTEM_SLEEP", util::SUBSYSTEM_SLEEP_STATE);
    *config.add_atom_matcher() = pulledAtomMatcher;
    auto screenOnAtomMatcher = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = screenOnAtomMatcher;
    auto screenOffAtomMatcher = CreateScreenTurnedOffAtomMatcher();
    *config.add_atom_matcher() = screenOffAtomMatcher;
    auto batteryNoneAtomMatcher = CreateBatteryStateNoneMatcher();
    *config.add_atom_matcher() = batteryNoneAtomMatcher;
    auto batteryUsbAtomMatcher = CreateBatteryStateUsbMatcher();
    *config.add_atom_matcher() = batteryUsbAtomMatcher;

    // Simple condition with InitialValue set to default (unknown).
    auto screenOnUnknownPredicate = CreateScreenIsOnPredicate();
    *config.add_predicate() = screenOnUnknownPredicate;

    // Simple condition with InitialValue set to false.
    auto screenOnFalsePredicate = config.add_predicate();
    screenOnFalsePredicate->set_id(StringToId("ScreenIsOnInitialFalse"));
    SimplePredicate* simpleScreenOnFalsePredicate =
            screenOnFalsePredicate->mutable_simple_predicate();
    simpleScreenOnFalsePredicate->set_start(screenOnAtomMatcher.id());
    simpleScreenOnFalsePredicate->set_stop(screenOffAtomMatcher.id());
    simpleScreenOnFalsePredicate->set_initial_value(SimplePredicate_InitialValue_FALSE);

    // Simple condition with InitialValue set to false.
    auto onBatteryFalsePredicate = config.add_predicate();
    onBatteryFalsePredicate->set_id(StringToId("OnBatteryInitialFalse"));
    SimplePredicate* simpleOnBatteryFalsePredicate =
            onBatteryFalsePredicate->mutable_simple_predicate();
    simpleOnBatteryFalsePredicate->set_start(batteryNoneAtomMatcher.id());
    simpleOnBatteryFalsePredicate->set_stop(batteryUsbAtomMatcher.id());
    simpleOnBatteryFalsePredicate->set_initial_value(SimplePredicate_InitialValue_FALSE);

    // Combination condition with both simple condition InitialValues set to false.
    auto screenOnFalseOnBatteryFalsePredicate = config.add_predicate();
    screenOnFalseOnBatteryFalsePredicate->set_id(StringToId("ScreenOnFalseOnBatteryFalse"));
    screenOnFalseOnBatteryFalsePredicate->mutable_combination()->set_operation(
            LogicalOperation::AND);
    addPredicateToPredicateCombination(*screenOnFalsePredicate,
                                       screenOnFalseOnBatteryFalsePredicate);
    addPredicateToPredicateCombination(*onBatteryFalsePredicate,
                                       screenOnFalseOnBatteryFalsePredicate);

    // Combination condition with one simple condition InitialValue set to unknown and one set to
    // false.
    auto screenOnUnknownOnBatteryFalsePredicate = config.add_predicate();
    screenOnUnknownOnBatteryFalsePredicate->set_id(StringToId("ScreenOnUnknowneOnBatteryFalse"));
    screenOnUnknownOnBatteryFalsePredicate->mutable_combination()->set_operation(
            LogicalOperation::AND);
    addPredicateToPredicateCombination(screenOnUnknownPredicate,
                                       screenOnUnknownOnBatteryFalsePredicate);
    addPredicateToPredicateCombination(*onBatteryFalsePredicate,
                                       screenOnUnknownOnBatteryFalsePredicate);

    // Simple condition metric with initial value false.
    ValueMetric* metric1 = config.add_value_metric();
    metric1->set_id(StringToId("ValueSubsystemSleepWhileScreenOnInitialFalse"));
    metric1->set_what(pulledAtomMatcher.id());
    *metric1->mutable_value_field() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {4 /* time sleeping field */});
    metric1->set_bucket(FIVE_MINUTES);
    metric1->set_condition(screenOnFalsePredicate->id());

    // Simple condition metric with initial value unknown.
    ValueMetric* metric2 = config.add_value_metric();
    metric2->set_id(StringToId("ValueSubsystemSleepWhileScreenOnInitialUnknown"));
    metric2->set_what(pulledAtomMatcher.id());
    *metric2->mutable_value_field() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {4 /* time sleeping field */});
    metric2->set_bucket(FIVE_MINUTES);
    metric2->set_condition(screenOnUnknownPredicate.id());

    // Combination condition metric with initial values false and false.
    ValueMetric* metric3 = config.add_value_metric();
    metric3->set_id(StringToId("ValueSubsystemSleepWhileScreenOnFalseDeviceUnpluggedFalse"));
    metric3->set_what(pulledAtomMatcher.id());
    *metric3->mutable_value_field() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {4 /* time sleeping field */});
    metric3->set_bucket(FIVE_MINUTES);
    metric3->set_condition(screenOnFalseOnBatteryFalsePredicate->id());

    // Combination condition metric with initial values unknown and false.
    ValueMetric* metric4 = config.add_value_metric();
    metric4->set_id(StringToId("ValueSubsystemSleepWhileScreenOnUnknownDeviceUnpluggedFalse"));
    metric4->set_what(pulledAtomMatcher.id());
    *metric4->mutable_value_field() =
            CreateDimensions(util::SUBSYSTEM_SLEEP_STATE, {4 /* time sleeping field */});
    metric4->set_bucket(FIVE_MINUTES);
    metric4->set_condition(screenOnUnknownOnBatteryFalsePredicate->id());

    return config;
}

class MetricsManagerUtilTest : public ::testing::Test {
public:
    void SetUp() override {
        allTagIdsToMatchersMap.clear();
        allAtomMatchingTrackers.clear();
        atomMatchingTrackerMap.clear();
        allConditionTrackers.clear();
        conditionTrackerMap.clear();
        allMetricProducers.clear();
        metricProducerMap.clear();
        allAnomalyTrackers.clear();
        allAlarmTrackers.clear();
        conditionToMetricMap.clear();
        trackerToMetricMap.clear();
        trackerToConditionMap.clear();
        activationAtomTrackerToMetricMap.clear();
        deactivationAtomTrackerToMetricMap.clear();
        alertTrackerMap.clear();
        metricsWithActivation.clear();
        stateProtoHashes.clear();
        noReportMetricIds.clear();
        StateManager::getInstance().clear();
    }
};

struct DimLimitTestCase {
    int configLimit;
    int actualLimit;

    friend void PrintTo(const DimLimitTestCase& testCase, ostream* os) {
        *os << testCase.configLimit;
    }
};

class MetricsManagerUtilDimLimitTest : public MetricsManagerUtilTest,
                                       public WithParamInterface<DimLimitTestCase> {};

const vector<DimLimitTestCase> dimLimitTestCases = {{900, 900}, {799, 800}, {3001, 3000}, {0, 800}};

INSTANTIATE_TEST_SUITE_P(DimLimit, MetricsManagerUtilDimLimitTest, ValuesIn(dimLimitTestCases),
                         PrintToStringParamName());

}  // anonymous namespace

TEST_F(MetricsManagerUtilTest, TestInitialConditions) {
    // initConfig returns nullopt if config is valid
    EXPECT_EQ(initConfig(buildConfigWithDifferentPredicates()), nullopt);
    ASSERT_EQ(4u, allMetricProducers.size());
    ASSERT_EQ(5u, allConditionTrackers.size());

    ConditionKey queryKey;
    vector<ConditionState> conditionCache(5, ConditionState::kNotEvaluated);

    allConditionTrackers[3]->isConditionMet(queryKey, allConditionTrackers, false, conditionCache);
    allConditionTrackers[4]->isConditionMet(queryKey, allConditionTrackers, false, conditionCache);
    EXPECT_EQ(ConditionState::kUnknown, conditionCache[0]);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[1]);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[2]);
    EXPECT_EQ(ConditionState::kFalse, conditionCache[3]);
    EXPECT_EQ(ConditionState::kUnknown, conditionCache[4]);

    EXPECT_EQ(ConditionState::kFalse, allMetricProducers[0]->mCondition);
    EXPECT_EQ(ConditionState::kUnknown, allMetricProducers[1]->mCondition);
    EXPECT_EQ(ConditionState::kFalse, allMetricProducers[2]->mCondition);
    EXPECT_EQ(ConditionState::kUnknown, allMetricProducers[3]->mCondition);

    EXPECT_EQ(allTagIdsToMatchersMap.size(), 3);
    EXPECT_EQ(allTagIdsToMatchersMap[SCREEN_STATE_ATOM_ID].size(), 2);
    EXPECT_EQ(allTagIdsToMatchersMap[util::PLUGGED_STATE_CHANGED].size(), 2);
    EXPECT_EQ(allTagIdsToMatchersMap[util::SUBSYSTEM_SLEEP_STATE].size(), 1);
}

TEST_F(MetricsManagerUtilTest, TestGoodConfig) {
    StatsdConfig config = buildGoodConfig(kConfigId, kAlertId);
    // initConfig returns nullopt if config is valid
    EXPECT_EQ(initConfig(config), nullopt);
    ASSERT_EQ(5u, allMetricProducers.size());
    EXPECT_THAT(metricProducerMap, UnorderedElementsAre(Key(config.count_metric(0).id()),
                                                        Key(config.duration_metric(0).id()),
                                                        Key(config.value_metric(0).id()),
                                                        Key(config.kll_metric(0).id()),
                                                        Key(config.gauge_metric(0).id())));
    ASSERT_EQ(1u, allAnomalyTrackers.size());
    ASSERT_EQ(1u, noReportMetricIds.size());
    ASSERT_EQ(1u, alertTrackerMap.size());
    EXPECT_NE(alertTrackerMap.find(kAlertId), alertTrackerMap.end());
    EXPECT_EQ(alertTrackerMap.find(kAlertId)->second, 0);
}

TEST_F(MetricsManagerUtilTest, TestDimensionMetricsWithMultiTags) {
    EXPECT_EQ(initConfig(buildDimensionMetricsWithMultiTags()),
              createInvalidConfigReasonWithMatcher(
                      INVALID_CONFIG_REASON_METRIC_MATCHER_MORE_THAN_ONE_ATOM, /*metric id=*/3,
                      StringToId("BATTERY_LOW")));
}

TEST_F(MetricsManagerUtilTest, TestCircleLogMatcherDependency) {
    optional<InvalidConfigReason> expectedInvalidConfigReason =
            createInvalidConfigReasonWithMatcher(INVALID_CONFIG_REASON_MATCHER_CYCLE,
                                                 StringToId("SCREEN_ON_OR_OFF"));
    expectedInvalidConfigReason->matcherIds.push_back(StringToId("SCREEN_ON_OR_OFF"));

    EXPECT_EQ(initConfig(buildCircleMatchers()), expectedInvalidConfigReason);
}

TEST_F(MetricsManagerUtilTest, TestMissingMatchers) {
    optional<InvalidConfigReason> expectedInvalidConfigReason =
            createInvalidConfigReasonWithMatcher(INVALID_CONFIG_REASON_MATCHER_CHILD_NOT_FOUND,
                                                 StringToId("SCREEN_ON_OR_OFF"));
    expectedInvalidConfigReason->matcherIds.push_back(StringToId("ABC"));

    EXPECT_EQ(initConfig(buildMissingMatchers()), expectedInvalidConfigReason);
}

TEST_F(MetricsManagerUtilTest, TestMissingPredicate) {
    EXPECT_EQ(
            initConfig(buildMissingPredicate()),
            createInvalidConfigReasonWithPredicate(INVALID_CONFIG_REASON_METRIC_CONDITION_NOT_FOUND,
                                                   /*metric id=*/3, StringToId("SOME_CONDITION")));
}

TEST_F(MetricsManagerUtilTest, TestCirclePredicateDependency) {
    optional<InvalidConfigReason> expectedInvalidConfigReason =
            createInvalidConfigReasonWithPredicate(INVALID_CONFIG_REASON_CONDITION_CYCLE,
                                                   StringToId("SCREEN_IS_EITHER_ON_OFF"));
    expectedInvalidConfigReason->conditionIds.push_back(StringToId("SCREEN_IS_EITHER_ON_OFF"));

    EXPECT_EQ(initConfig(buildCirclePredicates()), expectedInvalidConfigReason);
}

TEST_F(MetricsManagerUtilTest, TestAlertWithUnknownMetric) {
    EXPECT_EQ(initConfig(buildAlertWithUnknownMetric()),
              createInvalidConfigReasonWithAlert(INVALID_CONFIG_REASON_ALERT_METRIC_NOT_FOUND,
                                                 /*metric id=*/2, /*matcher id=*/3));
}

TEST_F(MetricsManagerUtilTest, TestMetricWithMultipleActivations) {
    StatsdConfig config;
    int64_t metricId = 1;
    auto metric_activation1 = config.add_metric_activation();
    metric_activation1->set_metric_id(metricId);
    metric_activation1->set_activation_type(ACTIVATE_IMMEDIATELY);
    auto metric_activation2 = config.add_metric_activation();
    metric_activation2->set_metric_id(metricId);
    metric_activation2->set_activation_type(ACTIVATE_IMMEDIATELY);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_HAS_MULTIPLE_ACTIVATIONS, metricId));
}

TEST_F(MetricsManagerUtilTest, TestCountMetricMissingIdOrWhat) {
    StatsdConfig config;
    int64_t metricId = 1;
    CountMetric* metric = config.add_count_metric();
    metric->set_id(metricId);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metricId));
}

TEST_F(MetricsManagerUtilTest, TestCountMetricConditionlinkNoCondition) {
    StatsdConfig config;
    CountMetric* metric = config.add_count_metric();
    *metric = createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    auto link = metric->add_links();
    link->set_condition(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION,
                                  StringToId("Count")));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricMissingIdOrWhat) {
    StatsdConfig config;
    int64_t metricId = 1;
    DurationMetric* metric = config.add_duration_metric();
    metric->set_id(metricId);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metricId));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricConditionlinkNoCondition) {
    StatsdConfig config;
    DurationMetric* metric = config.add_duration_metric();
    *metric = createDurationMetric(/*name=*/"Duration", /*what=*/StringToId("ScreenIsOn"),
                                   /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();
    *config.add_predicate() = CreateScreenIsOnPredicate();

    auto link = metric->add_links();
    link->set_condition(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION,
                                  StringToId("Duration")));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricMissingIdOrWhat) {
    StatsdConfig config;
    int64_t metricId = 1;
    GaugeMetric* metric = config.add_gauge_metric();
    metric->set_id(metricId);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metricId));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricConditionlinkNoCondition) {
    StatsdConfig config;
    GaugeMetric* metric = config.add_gauge_metric();
    *metric = createGaugeMetric(/*name=*/"Gauge", /*what=*/StringToId("ScreenTurnedOn"),
                                /*samplingType=*/GaugeMetric_SamplingType_FIRST_N_SAMPLES,
                                /*condition=*/nullopt, /*triggerEvent=*/nullopt);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    auto link = metric->add_links();
    link->set_condition(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION,
                                  StringToId("Gauge")));
}

TEST_F(MetricsManagerUtilTest, TestEventMetricMissingIdOrWhat) {
    StatsdConfig config;
    int64_t metricId = 1;
    EventMetric* metric = config.add_event_metric();
    metric->set_id(metricId);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metricId));
}

TEST_F(MetricsManagerUtilTest, TestEventMetricConditionlinkNoCondition) {
    StatsdConfig config;
    EventMetric* metric = config.add_event_metric();
    *metric = createEventMetric(/*name=*/"Event", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    auto link = metric->add_links();
    link->set_condition(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION,
                                  StringToId("Event")));
}

TEST_F(MetricsManagerUtilTest, TestEventMetricInvalidSamplingPercentage) {
    StatsdConfig config;
    EventMetric* metric = config.add_event_metric();
    *metric = createEventMetric(/*name=*/"Event", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt);
    metric->set_sampling_percentage(101);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_INCORRECT_SAMPLING_PERCENTAGE,
                                  StringToId("Event")));
}

TEST_F(MetricsManagerUtilTest, TestEventMetricInvalidSamplingPercentageZero) {
    StatsdConfig config;
    EventMetric* metric = config.add_event_metric();
    *metric = createEventMetric(/*name=*/"Event", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt);
    metric->set_sampling_percentage(0);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_INCORRECT_SAMPLING_PERCENTAGE,
                                  StringToId("Event")));
}

TEST_F(MetricsManagerUtilTest, TestEventMetricValidSamplingPercentage) {
    StatsdConfig config;
    EventMetric* metric = config.add_event_metric();
    *metric = createEventMetric(/*name=*/"Event", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt);
    metric->set_sampling_percentage(50);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config), nullopt);
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricInvalidSamplingPercentage) {
    StatsdConfig config;
    GaugeMetric* metric = config.add_gauge_metric();
    *metric = createGaugeMetric(/*name=*/"Gauge", /*what=*/StringToId("ScreenTurnedOn"),
                                GaugeMetric::FIRST_N_SAMPLES,
                                /*condition=*/nullopt, /*triggerEvent=*/nullopt);
    metric->set_sampling_percentage(101);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_INCORRECT_SAMPLING_PERCENTAGE,
                                  StringToId("Gauge")));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricInvalidSamplingPercentageZero) {
    StatsdConfig config;
    GaugeMetric* metric = config.add_gauge_metric();
    *metric = createGaugeMetric(/*name=*/"Gauge", /*what=*/StringToId("ScreenTurnedOn"),
                                GaugeMetric::FIRST_N_SAMPLES,
                                /*condition=*/nullopt, /*triggerEvent=*/nullopt);
    metric->set_sampling_percentage(0);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_INCORRECT_SAMPLING_PERCENTAGE,
                                  StringToId("Gauge")));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricValidSamplingPercentage) {
    StatsdConfig config;
    GaugeMetric* metric = config.add_gauge_metric();
    *metric = createGaugeMetric(/*name=*/"Gauge", /*what=*/StringToId("ScreenTurnedOn"),
                                GaugeMetric::FIRST_N_SAMPLES,
                                /*condition=*/nullopt, /*triggerEvent=*/nullopt);
    metric->set_sampling_percentage(50);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config), nullopt);
}

TEST_F(MetricsManagerUtilTest, TestPulledGaugeMetricWithSamplingPercentage) {
    StatsdConfig config;
    GaugeMetric* metric = config.add_gauge_metric();
    *metric = createGaugeMetric(/*name=*/"Gauge", /*what=*/StringToId("SubsystemSleep"),
                                GaugeMetric::FIRST_N_SAMPLES,
                                /*condition=*/nullopt, /*triggerEvent=*/nullopt);
    metric->set_sampling_percentage(50);
    *config.add_atom_matcher() =
            CreateSimpleAtomMatcher("SubsystemSleep", util::SUBSYSTEM_SLEEP_STATE);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_GAUGE_METRIC_PULLED_WITH_SAMPLING,
                                  StringToId("Gauge")));
}

TEST_F(MetricsManagerUtilTest, TestNumericValueMetricMissingIdOrWhat) {
    StatsdConfig config;
    int64_t metricId = 1;
    ValueMetric* metric = config.add_value_metric();
    metric->set_id(metricId);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metricId));
}

TEST_F(MetricsManagerUtilTest, TestNumericValueMetricConditionlinkNoCondition) {
    StatsdConfig config;
    ValueMetric* metric = config.add_value_metric();
    *metric = createValueMetric(/*name=*/"NumericValue", /*what=*/CreateScreenTurnedOnAtomMatcher(),
                                /*valueField=*/2, /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    auto link = metric->add_links();
    link->set_condition(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION,
                                  StringToId("NumericValue")));
}

TEST_F(MetricsManagerUtilTest, TestKllMetricMissingIdOrWhat) {
    StatsdConfig config;
    int64_t metricId = 1;
    KllMetric* metric = config.add_kll_metric();
    metric->set_id(metricId);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_MISSING_ID_OR_WHAT, metricId));
}

TEST_F(MetricsManagerUtilTest, TestKllMetricConditionlinkNoCondition) {
    StatsdConfig config;
    KllMetric* metric = config.add_kll_metric();
    *metric = createKllMetric(/*name=*/"Kll", /*what=*/CreateScreenTurnedOnAtomMatcher(),
                              /*valueField=*/2, /*condition=*/nullopt);
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    auto link = metric->add_links();
    link->set_condition(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_CONDITIONLINK_NO_CONDITION,
                                  StringToId("Kll")));
}

TEST_F(MetricsManagerUtilTest, TestMetricMatcherNotFound) {
    StatsdConfig config;
    *config.add_count_metric() =
            createCountMetric(/*name=*/"Count", /*what=*/StringToId("SOME MATCHER"),
                              /*condition=*/nullopt, /*states=*/{});

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithMatcher(
                                          INVALID_CONFIG_REASON_METRIC_MATCHER_NOT_FOUND,
                                          StringToId("Count"), StringToId("SOME MATCHER")));
}

TEST_F(MetricsManagerUtilTest, TestMetricConditionLinkNotFound) {
    StatsdConfig config;
    CountMetric* metric = config.add_count_metric();
    *metric = createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/StringToId("ScreenIsOn"), /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_predicate() = CreateScreenIsOnPredicate();

    auto link = metric->add_links();
    link->set_condition(StringToId("SOME CONDITION"));

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithPredicate(
                                          INVALID_CONFIG_REASON_METRIC_CONDITION_LINK_NOT_FOUND,
                                          StringToId("Count"), StringToId("SOME CONDITION")));
}

TEST_F(MetricsManagerUtilTest, TestMetricStateNotFound) {
    StatsdConfig config;
    *config.add_count_metric() =
            createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                              /*condition=*/nullopt, /*states=*/{StringToId("SOME STATE")});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithState(INVALID_CONFIG_REASON_METRIC_STATE_NOT_FOUND,
                                                 StringToId("Count"), StringToId("SOME STATE")));
}

TEST_F(MetricsManagerUtilTest, TestMetricStatelinkNoState) {
    StatsdConfig config;
    CountMetric* metric = config.add_count_metric();
    *metric = createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    auto link = metric->add_state_link();
    link->set_state_atom_id(2);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_STATELINK_NO_STATE,
                                  StringToId("Count")));
}

TEST_F(MetricsManagerUtilTest, TestMetricBadThreshold) {
    StatsdConfig config;
    CountMetric* metric = config.add_count_metric();
    *metric = createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    metric->mutable_threshold()->set_lt_float(1.0);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_BAD_THRESHOLD, StringToId("Count")));
}

TEST_F(MetricsManagerUtilTest, TestMetricActivationMatcherNotFound) {
    StatsdConfig config;
    *config.add_count_metric() =
            createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                              /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    auto metric_activation = config.add_metric_activation();
    metric_activation->set_metric_id(StringToId("Count"));
    metric_activation->set_activation_type(ACTIVATE_IMMEDIATELY);
    auto event_activation = metric_activation->add_event_activation();

    event_activation->set_atom_matcher_id(StringToId("SOME_MATCHER"));

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithMatcher(
                                          INVALID_CONFIG_REASON_METRIC_ACTIVATION_MATCHER_NOT_FOUND,
                                          StringToId("Count"), StringToId("SOME_MATCHER")));
}

TEST_F(MetricsManagerUtilTest, TestMetricDeactivationMatcherNotFound) {
    StatsdConfig config;
    *config.add_count_metric() =
            createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                              /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    auto metric_activation = config.add_metric_activation();
    metric_activation->set_metric_id(StringToId("Count"));
    metric_activation->set_activation_type(ACTIVATE_IMMEDIATELY);
    auto event_activation = metric_activation->add_event_activation();
    event_activation->set_atom_matcher_id(StringToId("ScreenTurnedOn"));

    event_activation->set_deactivation_atom_matcher_id(StringToId("SOME_MATCHER"));

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithMatcher(
                      INVALID_CONFIG_REASON_METRIC_DEACTIVATION_MATCHER_NOT_FOUND,
                      StringToId("Count"), StringToId("SOME_MATCHER")));
}

TEST_F(MetricsManagerUtilTest, TestMetricSlicedStateAtomAllowedFromAnyUid) {
    StatsdConfig config;
    CountMetric* metric = config.add_count_metric();
    *metric = createCountMetric(/*name=*/"Count", /*what=*/StringToId("ScreenTurnedOn"),
                                /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    *config.add_state() = CreateScreenState();
    metric->add_slice_by_state(StringToId("ScreenState"));
    config.add_whitelisted_atom_ids(util::SCREEN_STATE_CHANGED);

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SLICED_STATE_ATOM_ALLOWED_FROM_ANY_UID,
                                StringToId("Count")));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricWhatNotSimple) {
    StatsdConfig config;
    *config.add_duration_metric() =
            createDurationMetric(/*name=*/"Duration", /*what=*/StringToId("ScreenIsEitherOnOff"),
                                 /*condition=*/nullopt, /*states=*/{});
    *config.add_predicate() = CreateScreenIsOnPredicate();
    *config.add_predicate() = CreateScreenIsOffPredicate();

    auto condition = config.add_predicate();
    condition->set_id(StringToId("ScreenIsEitherOnOff"));
    Predicate_Combination* combination = condition->mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_predicate(StringToId("ScreenIsOn"));
    combination->add_predicate(StringToId("ScreenIsOff"));

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithPredicate(
                      INVALID_CONFIG_REASON_DURATION_METRIC_WHAT_NOT_SIMPLE, StringToId("Duration"),
                      StringToId("ScreenIsEitherOnOff")));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricWhatNotFound) {
    StatsdConfig config;
    int64_t metricId = 1;
    DurationMetric* metric = config.add_duration_metric();
    metric->set_id(metricId);

    metric->set_what(StringToId("SOME WHAT"));

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithPredicate(
                                          INVALID_CONFIG_REASON_DURATION_METRIC_WHAT_NOT_FOUND,
                                          metricId, StringToId("SOME WHAT")));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricMissingStart) {
    StatsdConfig config;
    *config.add_duration_metric() =
            createDurationMetric(/*name=*/"Duration", /*what=*/StringToId("SCREEN_IS_ON"),
                                 /*condition=*/nullopt, /*states=*/{});
    auto condition = config.add_predicate();
    condition->set_id(StringToId("SCREEN_IS_ON"));

    SimplePredicate* simplePredicate = condition->mutable_simple_predicate();
    simplePredicate->set_stop(StringToId("SCREEN_IS_OFF"));

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithPredicate(
                                          INVALID_CONFIG_REASON_DURATION_METRIC_MISSING_START,
                                          StringToId("Duration"), StringToId("SCREEN_IS_ON")));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricMaxSparseHasSpliceByState) {
    StatsdConfig config;
    DurationMetric* metric = config.add_duration_metric();
    *metric = createDurationMetric(/*name=*/"Duration", /*what=*/StringToId("ScreenIsOn"),
                                   /*condition=*/nullopt, /*states=*/{});
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();
    *config.add_predicate() = CreateScreenIsOnPredicate();
    *config.add_state() = CreateScreenState();

    metric->add_slice_by_state(StringToId("ScreenState"));
    metric->set_aggregation_type(DurationMetric::MAX_SPARSE);

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_DURATION_METRIC_MAX_SPARSE_HAS_SLICE_BY_STATE,
                                StringToId("Duration")));
}

TEST_F(MetricsManagerUtilTest, TestValueMetricMissingValueField) {
    StatsdConfig config;
    int64_t metricId = 1;
    ValueMetric* metric = config.add_value_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_VALUE_METRIC_MISSING_VALUE_FIELD, metricId));
}

TEST_F(MetricsManagerUtilTest, TestValueMetricValueFieldHasPositionAll) {
    StatsdConfig config;
    int64_t metricId = 1;
    ValueMetric* metric = config.add_value_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    metric->mutable_value_field()->set_position(ALL);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_VALUE_METRIC_VALUE_FIELD_HAS_POSITION_ALL,
                                  metricId));
}

TEST_F(MetricsManagerUtilTest, TestValueMetricHasIncorrectValueField) {
    StatsdConfig config;
    int64_t metricId = 1;
    ValueMetric* metric = config.add_value_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    metric->mutable_value_field()->set_position(ANY);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_VALUE_METRIC_HAS_INCORRECT_VALUE_FIELD,
                                  metricId));
}

TEST_F(MetricsManagerUtilTest, TestKllMetricMissingKllField) {
    StatsdConfig config;
    int64_t metricId = 1;
    KllMetric* metric = config.add_kll_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_KLL_METRIC_MISSING_KLL_FIELD, metricId));
}

TEST_F(MetricsManagerUtilTest, TestKllMetricKllFieldHasPositionAll) {
    StatsdConfig config;
    int64_t metricId = 1;
    KllMetric* metric = config.add_kll_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    metric->mutable_kll_field()->set_position(ALL);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_KLL_METRIC_KLL_FIELD_HAS_POSITION_ALL,
                                  metricId));
}

TEST_F(MetricsManagerUtilTest, TestKllMetricHasIncorrectKllField) {
    StatsdConfig config;
    int64_t metricId = 1;
    KllMetric* metric = config.add_kll_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    metric->mutable_kll_field()->set_position(ANY);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_KLL_METRIC_HAS_INCORRECT_KLL_FIELD,
                                  metricId));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricIncorrectFieldFilter) {
    StatsdConfig config;
    int64_t metricId = 1;
    GaugeMetric* metric = config.add_gauge_metric();
    metric->set_id(metricId);
    metric->set_what(1);

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_GAUGE_METRIC_INCORRECT_FIELD_FILTER,
                                  metricId));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricTriggerNoPullAtom) {
    StatsdConfig config;
    int64_t metricId = 1;
    GaugeMetric* metric = config.add_gauge_metric();
    metric->set_id(metricId);
    metric->set_what(StringToId("ScreenTurnedOn"));
    metric->mutable_gauge_fields_filter()->set_include_all(true);
    metric->set_trigger_event(1);

    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_GAUGE_METRIC_TRIGGER_NO_PULL_ATOM, metricId));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricTriggerNoFirstNSamples) {
    StatsdConfig config;
    int64_t metricId = 1;
    GaugeMetric* metric = config.add_gauge_metric();
    metric->set_id(metricId);
    metric->set_what(StringToId("Matcher"));
    *config.add_atom_matcher() =
            CreateSimpleAtomMatcher(/*name=*/"Matcher", /*atomId=*/util::SUBSYSTEM_SLEEP_STATE);

    metric->mutable_gauge_fields_filter()->set_include_all(true);
    metric->set_trigger_event(StringToId("Matcher"));

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_GAUGE_METRIC_TRIGGER_NO_FIRST_N_SAMPLES,
                                  metricId));
}

TEST_F(MetricsManagerUtilTest, TestMatcherDuplicate) {
    StatsdConfig config;

    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithMatcher(INVALID_CONFIG_REASON_MATCHER_DUPLICATE,
                                                   StringToId("ScreenTurnedOn")));
}

TEST_F(MetricsManagerUtilTest, TestMatcherNoOperation) {
    StatsdConfig config;
    int64_t matcherId = 1;

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(matcherId);
    matcher->mutable_combination()->add_matcher(StringToId("ScreenTurnedOn"));

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithMatcher(
                                          INVALID_CONFIG_REASON_MATCHER_NO_OPERATION, matcherId));
}

TEST_F(MetricsManagerUtilTest, TestMatcherNotOperationIsNotUnary) {
    StatsdConfig config;
    int64_t matcherId = 1;
    *config.add_atom_matcher() = CreateScreenTurnedOnAtomMatcher();
    *config.add_atom_matcher() = CreateScreenTurnedOffAtomMatcher();

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(matcherId);
    matcher->mutable_combination()->set_operation(LogicalOperation::NOT);
    matcher->mutable_combination()->add_matcher(StringToId("ScreenTurnedOn"));
    matcher->mutable_combination()->add_matcher(StringToId("ScreenTurnedOff"));

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithMatcher(
                      INVALID_CONFIG_REASON_MATCHER_NOT_OPERATION_IS_NOT_UNARY, matcherId));
}

TEST_F(MetricsManagerUtilTest, TestConditionChildNotFound) {
    StatsdConfig config;
    int64_t conditionId = 1;
    int64_t childConditionId = 2;

    Predicate* condition = config.add_predicate();
    condition->set_id(conditionId);
    condition->mutable_combination()->set_operation(LogicalOperation::NOT);
    condition->mutable_combination()->add_predicate(childConditionId);

    optional<InvalidConfigReason> expectedInvalidConfigReason =
            createInvalidConfigReasonWithPredicate(INVALID_CONFIG_REASON_CONDITION_CHILD_NOT_FOUND,
                                                   conditionId);
    expectedInvalidConfigReason->conditionIds.push_back(childConditionId);
    EXPECT_EQ(initConfig(config), expectedInvalidConfigReason);
}

TEST_F(MetricsManagerUtilTest, TestConditionDuplicate) {
    StatsdConfig config;
    *config.add_predicate() = CreateScreenIsOnPredicate();
    *config.add_predicate() = CreateScreenIsOnPredicate();

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithPredicate(INVALID_CONFIG_REASON_CONDITION_DUPLICATE,
                                                     StringToId("ScreenIsOn")));
}

TEST_F(MetricsManagerUtilTest, TestConditionNoOperation) {
    StatsdConfig config;
    int64_t conditionId = 1;
    *config.add_predicate() = CreateScreenIsOnPredicate();

    Predicate* condition = config.add_predicate();
    condition->set_id(conditionId);
    condition->mutable_combination()->add_predicate(StringToId("ScreenIsOn"));

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithPredicate(INVALID_CONFIG_REASON_CONDITION_NO_OPERATION,
                                                     conditionId));
}

TEST_F(MetricsManagerUtilTest, TestConditionNotOperationIsNotUnary) {
    StatsdConfig config;
    int64_t conditionId = 1;
    *config.add_predicate() = CreateScreenIsOnPredicate();
    *config.add_predicate() = CreateScreenIsOffPredicate();

    Predicate* condition = config.add_predicate();
    condition->set_id(conditionId);
    condition->mutable_combination()->set_operation(LogicalOperation::NOT);
    condition->mutable_combination()->add_predicate(StringToId("ScreenIsOn"));
    condition->mutable_combination()->add_predicate(StringToId("ScreenIsOff"));

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithPredicate(
                      INVALID_CONFIG_REASON_CONDITION_NOT_OPERATION_IS_NOT_UNARY, conditionId));
}

TEST_F(MetricsManagerUtilTest, TestSubscriptionRuleNotFoundAlert) {
    StatsdConfig config;
    int64_t alertId = 1;
    *config.add_subscription() = createSubscription("Subscription", Subscription::ALERT, alertId);

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithSubscriptionAndAlert(
                                          INVALID_CONFIG_REASON_SUBSCRIPTION_RULE_NOT_FOUND,
                                          StringToId("Subscription"), alertId));
}

TEST_F(MetricsManagerUtilTest, TestSubscriptionRuleNotFoundAlarm) {
    StatsdConfig config;
    int64_t alarmId = 1;
    *config.add_subscription() = createSubscription("Subscription", Subscription::ALARM, alarmId);

    EXPECT_EQ(initConfig(config), createInvalidConfigReasonWithSubscriptionAndAlarm(
                                          INVALID_CONFIG_REASON_SUBSCRIPTION_RULE_NOT_FOUND,
                                          StringToId("Subscription"), alarmId));
}

TEST_F(MetricsManagerUtilTest, TestSubscriptionSubscriberInfoMissing) {
    StatsdConfig config;
    Subscription subscription =
            createSubscription("Subscription", Subscription::ALERT, /*alert id=*/1);
    subscription.clear_subscriber_information();
    *config.add_subscription() = subscription;

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithSubscription(
                      INVALID_CONFIG_REASON_SUBSCRIPTION_SUBSCRIBER_INFO_MISSING,
                      StringToId("Subscription")));
}

TEST_F(MetricsManagerUtilTest, TestAlarmPeriodLessThanOrEqualZero) {
    StatsdConfig config;
    *config.add_alarm() = createAlarm("Alarm", /*offset=*/1, /*period=*/-1);

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithAlarm(
                      INVALID_CONFIG_REASON_ALARM_PERIOD_LESS_THAN_OR_EQUAL_ZERO,
                      StringToId("Alarm")));
}

TEST_F(MetricsManagerUtilTest, TestAlarmOffsetLessThanOrEqualZero) {
    StatsdConfig config;
    *config.add_alarm() = createAlarm("Alarm", /*offset=*/-1, /*period=*/1);

    EXPECT_EQ(initConfig(config),
              createInvalidConfigReasonWithAlarm(
                      INVALID_CONFIG_REASON_ALARM_OFFSET_LESS_THAN_OR_EQUAL_ZERO,
                      StringToId("Alarm")));
}

TEST_F(MetricsManagerUtilTest, TestCreateAtomMatchingTrackerInvalidMatcher) {
    sp<UidMap> uidMap = new UidMap();
    AtomMatcher matcher;
    // Matcher has no contents_case (simple/combination), so it is invalid.
    matcher.set_id(21);
    optional<InvalidConfigReason> invalidConfigReason;
    EXPECT_EQ(createAtomMatchingTracker(matcher, uidMap, invalidConfigReason), nullptr);
    EXPECT_EQ(invalidConfigReason,
              createInvalidConfigReasonWithMatcher(
                      INVALID_CONFIG_REASON_MATCHER_MALFORMED_CONTENTS_CASE, matcher.id()));
}

TEST_F(MetricsManagerUtilTest, TestCreateAtomMatchingTrackerSimple) {
    int64_t id = 123;
    sp<UidMap> uidMap = new UidMap();
    AtomMatcher matcher;
    matcher.set_id(id);
    SimpleAtomMatcher* simpleAtomMatcher = matcher.mutable_simple_atom_matcher();
    simpleAtomMatcher->set_atom_id(SCREEN_STATE_ATOM_ID);
    simpleAtomMatcher->add_field_value_matcher()->set_field(
            1 /*SCREEN_STATE_CHANGE__DISPLAY_STATE*/);
    simpleAtomMatcher->mutable_field_value_matcher(0)->set_eq_int(
            android::view::DisplayStateEnum::DISPLAY_STATE_ON);

    optional<InvalidConfigReason> invalidConfigReason;
    sp<AtomMatchingTracker> tracker =
            createAtomMatchingTracker(matcher, uidMap, invalidConfigReason);
    EXPECT_NE(tracker, nullptr);
    EXPECT_EQ(invalidConfigReason, nullopt);

    EXPECT_TRUE(tracker->mInitialized);
    EXPECT_EQ(tracker->getId(), id);
    const set<int>& atomIds = tracker->getAtomIds();
    ASSERT_EQ(atomIds.size(), 1);
    EXPECT_EQ(atomIds.count(SCREEN_STATE_ATOM_ID), 1);
}

TEST_F(MetricsManagerUtilTest, TestCreateAtomMatchingTrackerCombination) {
    int64_t id = 123;
    sp<UidMap> uidMap = new UidMap();
    AtomMatcher matcher;
    matcher.set_id(id);
    AtomMatcher_Combination* combination = matcher.mutable_combination();
    combination->set_operation(LogicalOperation::OR);
    combination->add_matcher(123);
    combination->add_matcher(223);

    optional<InvalidConfigReason> invalidConfigReason;
    sp<AtomMatchingTracker> tracker =
            createAtomMatchingTracker(matcher, uidMap, invalidConfigReason);
    EXPECT_NE(tracker, nullptr);
    EXPECT_EQ(invalidConfigReason, nullopt);

    // Combination matchers need to be initialized first.
    EXPECT_FALSE(tracker->mInitialized);
    EXPECT_EQ(tracker->getId(), id);
    const set<int>& atomIds = tracker->getAtomIds();
    ASSERT_EQ(atomIds.size(), 0);
}

TEST_F(MetricsManagerUtilTest, TestCreateConditionTrackerInvalid) {
    const ConfigKey key(123, 456);
    // Predicate has no contents_case (simple/combination), so it is invalid.
    Predicate predicate;
    predicate.set_id(21);
    unordered_map<int64_t, int> atomTrackerMap;
    optional<InvalidConfigReason> invalidConfigReason;
    EXPECT_EQ(createConditionTracker(key, predicate, 0, atomTrackerMap, invalidConfigReason),
              nullptr);
    EXPECT_EQ(invalidConfigReason,
              createInvalidConfigReasonWithPredicate(
                      INVALID_CONFIG_REASON_CONDITION_MALFORMED_CONTENTS_CASE, predicate.id()));
}

TEST_F(MetricsManagerUtilTest, TestCreateConditionTrackerSimple) {
    int index = 1;
    int64_t id = 987;
    const ConfigKey key(123, 456);

    int startMatcherIndex = 2, stopMatcherIndex = 0, stopAllMatcherIndex = 1;
    int64_t startMatcherId = 246, stopMatcherId = 153, stopAllMatcherId = 975;

    Predicate predicate;
    predicate.set_id(id);
    SimplePredicate* simplePredicate = predicate.mutable_simple_predicate();
    simplePredicate->set_start(startMatcherId);
    simplePredicate->set_stop(stopMatcherId);
    simplePredicate->set_stop_all(stopAllMatcherId);

    unordered_map<int64_t, int> atomTrackerMap;
    atomTrackerMap[startMatcherId] = startMatcherIndex;
    atomTrackerMap[stopMatcherId] = stopMatcherIndex;
    atomTrackerMap[stopAllMatcherId] = stopAllMatcherIndex;

    optional<InvalidConfigReason> invalidConfigReason;
    sp<ConditionTracker> tracker =
            createConditionTracker(key, predicate, index, atomTrackerMap, invalidConfigReason);
    EXPECT_EQ(invalidConfigReason, nullopt);
    EXPECT_EQ(tracker->getConditionId(), id);
    EXPECT_EQ(tracker->isSliced(), false);
    EXPECT_TRUE(tracker->IsSimpleCondition());
    const set<int>& interestedMatchers = tracker->getAtomMatchingTrackerIndex();
    ASSERT_EQ(interestedMatchers.size(), 3);
    ASSERT_EQ(interestedMatchers.count(startMatcherIndex), 1);
    ASSERT_EQ(interestedMatchers.count(stopMatcherIndex), 1);
    ASSERT_EQ(interestedMatchers.count(stopAllMatcherIndex), 1);
}

TEST_F(MetricsManagerUtilTest, TestCreateConditionTrackerCombination) {
    int index = 1;
    int64_t id = 987;
    const ConfigKey key(123, 456);

    Predicate predicate;
    predicate.set_id(id);
    Predicate_Combination* combinationPredicate = predicate.mutable_combination();
    combinationPredicate->set_operation(LogicalOperation::AND);
    combinationPredicate->add_predicate(888);
    combinationPredicate->add_predicate(777);

    // Combination conditions must be initialized to set most state.
    unordered_map<int64_t, int> atomTrackerMap;
    optional<InvalidConfigReason> invalidConfigReason;
    sp<ConditionTracker> tracker =
            createConditionTracker(key, predicate, index, atomTrackerMap, invalidConfigReason);
    EXPECT_EQ(invalidConfigReason, nullopt);
    EXPECT_EQ(tracker->getConditionId(), id);
    EXPECT_FALSE(tracker->IsSimpleCondition());
}

TEST_F(MetricsManagerUtilTest, TestCreateAnomalyTrackerInvalidMetric) {
    Alert alert;
    alert.set_id(123);
    alert.set_metric_id(1);
    alert.set_trigger_if_sum_gt(1);
    alert.set_num_buckets(1);

    sp<AlarmMonitor> anomalyAlarmMonitor;
    vector<sp<MetricProducer>> metricProducers;
    optional<InvalidConfigReason> invalidConfigReason;
    // Pass in empty metric producers, causing an error.
    EXPECT_EQ(createAnomalyTracker(alert, anomalyAlarmMonitor, UPDATE_NEW, /*updateTime=*/123, {},
                                   metricProducers, invalidConfigReason),
              nullopt);
    EXPECT_EQ(invalidConfigReason,
              createInvalidConfigReasonWithAlert(INVALID_CONFIG_REASON_ALERT_METRIC_NOT_FOUND,
                                                 alert.metric_id(), alert.id()));
}

TEST_F(MetricsManagerUtilTest, TestCreateAnomalyTrackerNoThreshold) {
    int64_t metricId = 1;
    Alert alert;
    alert.set_id(123);
    alert.set_metric_id(metricId);
    alert.set_num_buckets(1);

    CountMetric metric;
    metric.set_id(metricId);
    metric.set_bucket(ONE_MINUTE);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    vector<sp<MetricProducer>> metricProducers({new CountMetricProducer(
            kConfigKey, metric, 0, {ConditionState::kUnknown}, wizard, 0x0123456789, 0, 0)});
    sp<AlarmMonitor> anomalyAlarmMonitor;
    optional<InvalidConfigReason> invalidConfigReason;
    EXPECT_EQ(createAnomalyTracker(alert, anomalyAlarmMonitor, UPDATE_NEW, /*updateTime=*/123,
                                   {{1, 0}}, metricProducers, invalidConfigReason),
              nullopt);
    EXPECT_EQ(invalidConfigReason,
              createInvalidConfigReasonWithAlert(INVALID_CONFIG_REASON_ALERT_THRESHOLD_MISSING,
                                                 alert.id()));
}

TEST_F(MetricsManagerUtilTest, TestCreateAnomalyTrackerMissingBuckets) {
    int64_t metricId = 1;
    Alert alert;
    alert.set_id(123);
    alert.set_metric_id(metricId);
    alert.set_trigger_if_sum_gt(1);

    CountMetric metric;
    metric.set_id(metricId);
    metric.set_bucket(ONE_MINUTE);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    vector<sp<MetricProducer>> metricProducers({new CountMetricProducer(
            kConfigKey, metric, 0, {ConditionState::kUnknown}, wizard, 0x0123456789, 0, 0)});
    sp<AlarmMonitor> anomalyAlarmMonitor;
    optional<InvalidConfigReason> invalidConfigReason;
    EXPECT_EQ(createAnomalyTracker(alert, anomalyAlarmMonitor, UPDATE_NEW, /*updateTime=*/123,
                                   {{1, 0}}, metricProducers, invalidConfigReason),
              nullopt);
    EXPECT_EQ(invalidConfigReason,
              createInvalidConfigReasonWithAlert(
                      INVALID_CONFIG_REASON_ALERT_INVALID_TRIGGER_OR_NUM_BUCKETS, alert.id()));
}

TEST_F(MetricsManagerUtilTest, TestCreateAnomalyTrackerGood) {
    int64_t metricId = 1;
    Alert alert;
    alert.set_id(123);
    alert.set_metric_id(metricId);
    alert.set_trigger_if_sum_gt(1);
    alert.set_num_buckets(1);

    CountMetric metric;
    metric.set_id(metricId);
    metric.set_bucket(ONE_MINUTE);
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    vector<sp<MetricProducer>> metricProducers({new CountMetricProducer(
            kConfigKey, metric, 0, {ConditionState::kUnknown}, wizard, 0x0123456789, 0, 0)});
    sp<AlarmMonitor> anomalyAlarmMonitor;
    optional<InvalidConfigReason> invalidConfigReason;
    EXPECT_NE(createAnomalyTracker(alert, anomalyAlarmMonitor, UPDATE_NEW, /*updateTime=*/123,
                                   {{1, 0}}, metricProducers, invalidConfigReason),
              nullopt);
    EXPECT_EQ(invalidConfigReason, nullopt);
}

TEST_F(MetricsManagerUtilTest, TestCreateAnomalyTrackerDurationTooLong) {
    int64_t metricId = 1;
    Alert alert;
    alert.set_id(123);
    alert.set_metric_id(metricId);
    // Impossible for alert to fire since the time is bigger than bucketSize * numBuckets
    alert.set_trigger_if_sum_gt(MillisToNano(TimeUnitToBucketSizeInMillis(ONE_MINUTE)) + 1);
    alert.set_num_buckets(1);

    DurationMetric metric;
    metric.set_id(metricId);
    metric.set_bucket(ONE_MINUTE);
    metric.set_aggregation_type(DurationMetric_AggregationType_SUM);
    FieldMatcher dimensions;
    sp<MockConditionWizard> wizard = new NaggyMock<MockConditionWizard>();
    vector<sp<MetricProducer>> metricProducers({new DurationMetricProducer(
            kConfigKey, metric, -1 /*no condition*/, {}, -1 /* what index not needed*/,
            1 /* start index */, 2 /* stop index */, 3 /* stop_all index */, false /*nesting*/,
            wizard, 0x0123456789, dimensions, 0, 0)});
    sp<AlarmMonitor> anomalyAlarmMonitor;
    optional<InvalidConfigReason> invalidConfigReason;
    EXPECT_EQ(createAnomalyTracker(alert, anomalyAlarmMonitor, UPDATE_NEW, /*updateTime=*/123,
                                   {{1, 0}}, metricProducers, invalidConfigReason),
              nullopt);
    EXPECT_EQ(invalidConfigReason,
              createInvalidConfigReasonWithAlert(INVALID_CONFIG_REASON_ALERT_CANNOT_ADD_ANOMALY,
                                                 alert.metric_id(), alert.id()));
}

TEST_F(MetricsManagerUtilTest, TestCreateDurationProducerDimensionsInWhatInvalid) {
    StatsdConfig config;
    *config.add_atom_matcher() = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateReleaseWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateMoveToBackgroundAtomMatcher();
    *config.add_atom_matcher() = CreateMoveToForegroundAtomMatcher();

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    // The predicate is dimensioning by first attribution node by uid.
    FieldMatcher dimensions =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() = dimensions;
    *config.add_predicate() = holdingWakelockPredicate;

    DurationMetric* durationMetric = config.add_duration_metric();
    durationMetric->set_id(StringToId("WakelockDuration"));
    durationMetric->set_what(holdingWakelockPredicate.id());
    durationMetric->set_aggregation_type(DurationMetric::SUM);
    // The metric is dimensioning by first attribution node by uid AND tag.
    // Invalid since the predicate only dimensions by uid.
    *durationMetric->mutable_dimensions_in_what() = CreateAttributionUidAndOtherDimensions(
            util::WAKELOCK_STATE_CHANGED, {Position::FIRST}, {3 /* tag */});
    durationMetric->set_bucket(FIVE_MINUTES);

    ConfigKey key(123, 987);
    uint64_t timeNs = 456;
    sp<StatsPullerManager> pullerManager = new StatsPullerManager();
    sp<AlarmMonitor> anomalyAlarmMonitor;
    sp<AlarmMonitor> periodicAlarmMonitor;
    sp<UidMap> uidMap;
    sp<MetricsManager> metricsManager =
            new MetricsManager(key, config, timeNs, timeNs, uidMap, pullerManager,
                               anomalyAlarmMonitor, periodicAlarmMonitor);
    EXPECT_FALSE(metricsManager->isConfigValid());
}

TEST_F(MetricsManagerUtilTest, TestSampledMetrics) {
    StatsdConfig config;

    AtomMatcher appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);
    *config.add_atom_matcher() = appCrashMatcher;

    *config.add_atom_matcher() = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = CreateReleaseWakelockAtomMatcher();

    AtomMatcher bleScanResultReceivedMatcher = CreateSimpleAtomMatcher(
            "BleScanResultReceivedAtomMatcher", util::BLE_SCAN_RESULT_RECEIVED);
    *config.add_atom_matcher() = bleScanResultReceivedMatcher;

    Predicate holdingWakelockPredicate = CreateHoldingWakelockPredicate();
    *holdingWakelockPredicate.mutable_simple_predicate()->mutable_dimensions() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_predicate() = holdingWakelockPredicate;

    CountMetric sampledCountMetric =
            createCountMetric("CountSampledAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *sampledCountMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *sampledCountMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    sampledCountMetric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = sampledCountMetric;

    CountMetric unsampledCountMetric =
            createCountMetric("CountAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *unsampledCountMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *config.add_count_metric() = unsampledCountMetric;

    DurationMetric sampledDurationMetric = createDurationMetric(
            "DurationSampledWakelockPerUid", holdingWakelockPredicate.id(), nullopt, {});
    *sampledDurationMetric.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *sampledDurationMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    sampledDurationMetric.mutable_dimensional_sampling_info()->set_shard_count(4);
    *config.add_duration_metric() = sampledDurationMetric;

    DurationMetric unsampledDurationMetric = createDurationMetric(
            "DurationWakelockPerUid", holdingWakelockPredicate.id(), nullopt, {});
    unsampledDurationMetric.set_aggregation_type(DurationMetric::SUM);
    *unsampledDurationMetric.mutable_dimensions_in_what() =
            CreateAttributionUidDimensions(util::WAKELOCK_STATE_CHANGED, {Position::FIRST});
    *config.add_duration_metric() = unsampledDurationMetric;

    ValueMetric sampledValueMetric =
            createValueMetric("ValueSampledBleScanResultsPerUid", bleScanResultReceivedMatcher,
                              /*num_results=*/2, nullopt, {});
    *sampledValueMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::BLE_SCAN_RESULT_RECEIVED, {1 /* uid */});
    *sampledValueMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::BLE_SCAN_RESULT_RECEIVED, {1 /*uid*/});
    sampledValueMetric.mutable_dimensional_sampling_info()->set_shard_count(6);
    *config.add_value_metric() = sampledValueMetric;

    ValueMetric unsampledValueMetric =
            createValueMetric("ValueBleScanResultsPerUid", bleScanResultReceivedMatcher,
                              /*num_results=*/2, nullopt, {});
    *unsampledValueMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::BLE_SCAN_RESULT_RECEIVED, {1 /* uid */});
    *config.add_value_metric() = unsampledValueMetric;

    KllMetric sampledKllMetric =
            createKllMetric("KllSampledBleScanResultsPerUid", bleScanResultReceivedMatcher,
                            /*num_results=*/2, nullopt);
    *sampledKllMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::BLE_SCAN_RESULT_RECEIVED, {1 /* uid */});
    *sampledKllMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::BLE_SCAN_RESULT_RECEIVED, {1 /*uid*/});
    sampledKllMetric.mutable_dimensional_sampling_info()->set_shard_count(8);
    *config.add_kll_metric() = sampledKllMetric;

    KllMetric unsampledKllMetric = createKllMetric(
            "KllBleScanResultsPerUid", bleScanResultReceivedMatcher, /*num_results=*/2, nullopt);
    *unsampledKllMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::BLE_SCAN_RESULT_RECEIVED, {1 /* uid */});
    *config.add_kll_metric() = unsampledKllMetric;

    GaugeMetric sampledGaugeMetric =
            createGaugeMetric("GaugeSampledAppCrashesPerUid", appCrashMatcher.id(),
                              GaugeMetric::FIRST_N_SAMPLES, nullopt, nullopt);
    *sampledGaugeMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /* uid */});
    *sampledGaugeMetric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    sampledGaugeMetric.mutable_dimensional_sampling_info()->set_shard_count(10);
    *config.add_gauge_metric() = sampledGaugeMetric;

    GaugeMetric unsampledGaugeMetric =
            createGaugeMetric("GaugeAppCrashesPerUid", appCrashMatcher.id(),
                              GaugeMetric::FIRST_N_SAMPLES, nullopt, nullopt);
    *unsampledGaugeMetric.mutable_dimensions_in_what() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /* uid */});
    *config.add_gauge_metric() = unsampledGaugeMetric;

    ConfigKey key(123, 987);
    uint64_t timeNs = 456;
    sp<StatsPullerManager> pullerManager = new StatsPullerManager();
    sp<AlarmMonitor> anomalyAlarmMonitor;
    sp<AlarmMonitor> periodicAlarmMonitor;
    sp<UidMap> uidMap;
    sp<MetricsManager> metricsManager =
            new MetricsManager(key, config, timeNs, timeNs, uidMap, pullerManager,
                               anomalyAlarmMonitor, periodicAlarmMonitor);
    ASSERT_TRUE(metricsManager->isConfigValid());
    ASSERT_EQ(10, metricsManager->mAllMetricProducers.size());

    sp<MetricProducer> sampledCountMetricProducer = metricsManager->mAllMetricProducers[0];
    sp<MetricProducer> unsampledCountMetricProducer = metricsManager->mAllMetricProducers[1];
    sp<MetricProducer> sampledDurationMetricProducer = metricsManager->mAllMetricProducers[2];
    sp<MetricProducer> unsampledDurationMetricProducer = metricsManager->mAllMetricProducers[3];
    sp<MetricProducer> sampledValueMetricProducer = metricsManager->mAllMetricProducers[4];
    sp<MetricProducer> unsampledValueMetricProducer = metricsManager->mAllMetricProducers[5];
    sp<MetricProducer> sampledKllMetricProducer = metricsManager->mAllMetricProducers[6];
    sp<MetricProducer> unsampledKllMetricProducer = metricsManager->mAllMetricProducers[7];
    sp<MetricProducer> sampledGaugeMetricProducer = metricsManager->mAllMetricProducers[8];
    sp<MetricProducer> unsampledGaugeMetricProducer = metricsManager->mAllMetricProducers[9];

    // Check shard count is set correctly for sampled metrics or set to default.
    EXPECT_EQ(2, sampledCountMetricProducer->mShardCount);
    EXPECT_EQ(0, unsampledCountMetricProducer->mShardCount);
    EXPECT_EQ(4, sampledDurationMetricProducer->mShardCount);
    EXPECT_EQ(0, unsampledDurationMetricProducer->mShardCount);
    EXPECT_EQ(6, sampledValueMetricProducer->mShardCount);
    EXPECT_EQ(0, unsampledValueMetricProducer->mShardCount);
    EXPECT_EQ(8, sampledKllMetricProducer->mShardCount);
    EXPECT_EQ(0, unsampledKllMetricProducer->mShardCount);
    EXPECT_EQ(10, sampledGaugeMetricProducer->mShardCount);
    EXPECT_EQ(0, unsampledGaugeMetricProducer->mShardCount);

    // Check sampled what fields is set correctly or empty.
    EXPECT_EQ(1, sampledCountMetricProducer->mSampledWhatFields.size());
    EXPECT_EQ(true, unsampledCountMetricProducer->mSampledWhatFields.empty());
    EXPECT_EQ(1, sampledDurationMetricProducer->mSampledWhatFields.size());
    EXPECT_EQ(true, unsampledDurationMetricProducer->mSampledWhatFields.empty());
    EXPECT_EQ(1, sampledValueMetricProducer->mSampledWhatFields.size());
    EXPECT_EQ(true, unsampledValueMetricProducer->mSampledWhatFields.empty());
    EXPECT_EQ(1, sampledKllMetricProducer->mSampledWhatFields.size());
    EXPECT_EQ(true, unsampledKllMetricProducer->mSampledWhatFields.empty());
    EXPECT_EQ(1, sampledGaugeMetricProducer->mSampledWhatFields.size());
    EXPECT_EQ(true, unsampledGaugeMetricProducer->mSampledWhatFields.empty());
}

TEST_F(MetricsManagerUtilTest, TestMetricHasShardCountButNoSampledField) {
    AtomMatcher appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);

    StatsdConfig config;
    *config.add_atom_matcher() = appCrashMatcher;

    CountMetric metric =
            createCountMetric("CountSampledAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(
                      INVALID_CONFIG_REASON_METRIC_DIMENSIONAL_SAMPLING_INFO_MISSING_SAMPLED_FIELD,
                      metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricHasSampledFieldIncorrectShardCount) {
    AtomMatcher appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);

    StatsdConfig config;
    *config.add_atom_matcher() = appCrashMatcher;

    CountMetric metric =
            createCountMetric("CountSampledAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *config.add_count_metric() = metric;

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(
                      INVALID_CONFIG_REASON_METRIC_DIMENSIONAL_SAMPLING_INFO_INCORRECT_SHARD_COUNT,
                      metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricHasMultipleSampledFields) {
    AtomMatcher appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);

    StatsdConfig config;
    *config.add_atom_matcher() = appCrashMatcher;

    CountMetric metric =
            createCountMetric("CountSampledAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/, 2 /*event_type*/});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELD_INCORRECT_SIZE,
                                  metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_PositionALL) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::ALL});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::ALL});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELD_INCORRECT_SIZE,
                                  metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_PositionANY) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::ANY});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::ALL});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELD_INCORRECT_SIZE,
                                  metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricSampledField_DifferentFieldsNotSubsetDimension) {
    AtomMatcher appCrashMatcher =
            CreateSimpleAtomMatcher("APP_CRASH_OCCURRED", util::APP_CRASH_OCCURRED);

    StatsdConfig config;
    *config.add_atom_matcher() = appCrashMatcher;

    CountMetric metric =
            createCountMetric("CountSampledAppCrashesPerUid", appCrashMatcher.id(), nullopt, {});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateDimensions(util::APP_CRASH_OCCURRED, {1 /*uid*/});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELDS_NOT_SUBSET_DIM_IN_WHAT,
                                metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_LastNotSubsetDimensionsFirst) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::FIRST});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::LAST});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELDS_NOT_SUBSET_DIM_IN_WHAT,
                                metric.id()));
}

TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_FirstNotSubsetDimensionsLast) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::LAST});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::FIRST});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;

    EXPECT_EQ(
            initConfig(config),
            InvalidConfigReason(INVALID_CONFIG_REASON_METRIC_SAMPLED_FIELDS_NOT_SUBSET_DIM_IN_WHAT,
                                metric.id()));
}

// dimensions_in_what position ALL, sampled_what_field position FIRST
TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_FirstSubsetDimensionsAll) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::ALL});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::FIRST});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;
    EXPECT_EQ(initConfig(config), nullopt);
}

// dimensions_in_what position ALL, sampled_what_field position LAST
TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_LastSubsetDimensionsAll) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::ALL});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::LAST});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;
    EXPECT_EQ(initConfig(config), nullopt);
}

// dimensions_in_what position FIRST, sampled_what_field position FIRST
TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_FirstSubsetDimensionsFirst) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::FIRST});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::FIRST});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;
    EXPECT_EQ(initConfig(config), nullopt);
}

// dimensions_in_what position LAST, sampled_what_field position LAST
TEST_F(MetricsManagerUtilTest, TestMetricHasRepeatedSampledField_LastSubsetDimensionsLast) {
    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TEST_ATOM_REPORTED", util::TEST_ATOM_REPORTED);

    StatsdConfig config;
    *config.add_atom_matcher() = testAtomReportedMatcher;

    CountMetric metric = createCountMetric("CountSampledTestAtomReportedPerRepeatedIntField",
                                           testAtomReportedMatcher.id(), nullopt, {});
    *metric.mutable_dimensions_in_what() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::LAST});
    *metric.mutable_dimensional_sampling_info()->mutable_sampled_what_field() =
            CreateRepeatedDimensions(util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/},
                                     {Position::LAST});
    metric.mutable_dimensional_sampling_info()->set_shard_count(2);
    *config.add_count_metric() = metric;
    EXPECT_EQ(initConfig(config), nullopt);
}

TEST_F(MetricsManagerUtilTest, TestCountMetricHasRestrictedDelegate) {
    StatsdConfig config;
    CountMetric* metric = config.add_count_metric();
    config.set_restricted_metrics_delegate_package_name("com.android.app.test");

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_SUPPORTED));
}

TEST_F(MetricsManagerUtilTest, TestDurationMetricHasRestrictedDelegate) {
    StatsdConfig config;
    DurationMetric* metric = config.add_duration_metric();
    config.set_restricted_metrics_delegate_package_name("com.android.app.test");

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_SUPPORTED));
}

TEST_F(MetricsManagerUtilTest, TestGaugeMetricHasRestrictedDelegate) {
    StatsdConfig config;
    GaugeMetric* metric = config.add_gauge_metric();
    config.set_restricted_metrics_delegate_package_name("com.android.app.test");

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_SUPPORTED));
}

TEST_F(MetricsManagerUtilTest, TestNumericValueMetricHasRestrictedDelegate) {
    StatsdConfig config;
    ValueMetric* metric = config.add_value_metric();
    config.set_restricted_metrics_delegate_package_name("com.android.app.test");

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_SUPPORTED));
}

TEST_F(MetricsManagerUtilTest, TestKllMetricHasRestrictedDelegate) {
    StatsdConfig config;
    KllMetric* metric = config.add_kll_metric();
    config.set_restricted_metrics_delegate_package_name("com.android.app.test");

    EXPECT_EQ(initConfig(config),
              InvalidConfigReason(INVALID_CONFIG_REASON_RESTRICTED_METRIC_NOT_SUPPORTED));
}

TEST_P(MetricsManagerUtilDimLimitTest, TestDimLimit) {
    StatsdConfig config = buildGoodConfig(kConfigId, kAlertId);
    const auto& [configLimit, actualLimit] = GetParam();
    if (configLimit > 0) {
        config.mutable_count_metric(0)->set_max_dimensions_per_bucket(configLimit);
        config.mutable_duration_metric(0)->set_max_dimensions_per_bucket(configLimit);
        config.mutable_gauge_metric(0)->set_max_dimensions_per_bucket(configLimit);
        config.mutable_value_metric(0)->set_max_dimensions_per_bucket(configLimit);
        config.mutable_kll_metric(0)->set_max_dimensions_per_bucket(configLimit);
    }

    // initConfig returns nullopt if config is valid
    EXPECT_EQ(initConfig(config), nullopt);
    ASSERT_EQ(5u, allMetricProducers.size());

    sp<MetricProducer> producer =
            allMetricProducers[metricProducerMap.at(config.count_metric(0).id())];
    CountMetricProducer* countProducer = static_cast<CountMetricProducer*>(producer.get());
    EXPECT_EQ(countProducer->mDimensionHardLimit, actualLimit);

    producer = allMetricProducers[metricProducerMap.at(config.duration_metric(0).id())];
    DurationMetricProducer* durationProducer = static_cast<DurationMetricProducer*>(producer.get());
    EXPECT_EQ(durationProducer->mDimensionHardLimit, actualLimit);

    producer = allMetricProducers[metricProducerMap.at(config.gauge_metric(0).id())];
    GaugeMetricProducer* gaugeProducer = static_cast<GaugeMetricProducer*>(producer.get());
    EXPECT_EQ(gaugeProducer->mDimensionHardLimit, actualLimit);

    producer = allMetricProducers[metricProducerMap.at(config.value_metric(0).id())];
    NumericValueMetricProducer* numericValueProducer =
            static_cast<NumericValueMetricProducer*>(producer.get());
    EXPECT_EQ(numericValueProducer->mDimensionHardLimit, actualLimit);

    producer = allMetricProducers[metricProducerMap.at(config.kll_metric(0).id())];
    KllMetricProducer* kllProducer = static_cast<KllMetricProducer*>(producer.get());
    EXPECT_EQ(kllProducer->mDimensionHardLimit, actualLimit);
}

TEST_F(MetricsManagerUtilTest, TestMissingValueMatcherAndStringReplacer) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(SCREEN_STATE_ATOM_ID);
    matcher->mutable_simple_atom_matcher()->add_field_value_matcher();

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_NO_VALUE_MATCHER_NOR_STRING_REPLACER);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestMatcherWithValueMatcherOnly) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(SCREEN_STATE_ATOM_ID);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(2 /*int_field*/);
    fvm->set_eq_int(1);

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_EQ(actualInvalidConfigReason, nullopt);
}

TEST_F(MetricsManagerUtilTest, TestMatcherWithStringReplacerOnly) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(SCREEN_STATE_ATOM_ID);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(5 /*string_field*/);
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("#");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_EQ(actualInvalidConfigReason, nullopt);
}

TEST_F(MetricsManagerUtilTest, TestValueMatcherWithPositionAll) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(9 /*repeated_int_field*/);
    fvm->set_position(Position::ALL);
    fvm->set_eq_int(1);

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_VALUE_MATCHER_WITH_POSITION_ALL);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestValueMatcherAndStringReplaceWithPositionAll) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(12 /*repeated_string_field*/);
    fvm->set_position(Position::ALL);
    fvm->set_eq_string("foo");
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_VALUE_MATCHER_WITH_POSITION_ALL);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestValueMatcherWithPositionAllNested) {
    StatsdConfig config;
    config.set_id(12345);

    // Match on attribution_node[ALL].uid = 1
    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(1 /*attribution_node*/);
    fvm->set_position(Position::ALL);
    fvm->mutable_matches_tuple()->add_field_value_matcher()->set_field(1 /* uid */);
    fvm->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_int(1);

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_VALUE_MATCHER_WITH_POSITION_ALL);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestValueMatcherAndStringReplaceWithPositionAllNested) {
    StatsdConfig config;
    config.set_id(12345);

    // Match on attribution_node[ALL].uid = 1
    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(1 /*attribution_node*/);
    fvm->set_position(Position::ALL);
    fvm->mutable_matches_tuple()->add_field_value_matcher()->set_field(2 /* tag */);
    fvm->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string("foo");
    fvm->mutable_matches_tuple()
            ->mutable_field_value_matcher(0)
            ->mutable_replace_string()
            ->set_regex(R"([0-9]+$)");
    fvm->mutable_matches_tuple()
            ->mutable_field_value_matcher(0)
            ->mutable_replace_string()
            ->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_VALUE_MATCHER_WITH_POSITION_ALL);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestStringReplaceWithNoValueMatcherWithPositionAny) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(12 /*repeated_string_field*/);
    fvm->set_position(Position::ANY);
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_STRING_REPLACE_WITH_NO_VALUE_MATCHER_WITH_POSITION_ANY);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestStringReplaceWithNoValueMatcherWithPositionAnyNested) {
    StatsdConfig config;
    config.set_id(12345);

    // Match on attribution_node[ALL].uid = 1
    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(1 /*attribution_node*/);
    fvm->set_position(Position::ANY);
    fvm->mutable_matches_tuple()->add_field_value_matcher()->set_field(2 /* tag */);
    fvm->mutable_matches_tuple()
            ->mutable_field_value_matcher(0)
            ->mutable_replace_string()
            ->set_regex(R"([0-9]+$)");
    fvm->mutable_matches_tuple()
            ->mutable_field_value_matcher(0)
            ->mutable_replace_string()
            ->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_STRING_REPLACE_WITH_NO_VALUE_MATCHER_WITH_POSITION_ANY);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestStringReplaceWithValueMatcherWithPositionAny) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(12 /*repeated_string_field*/);
    fvm->set_position(Position::ANY);
    fvm->set_eq_string("bar");
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_EQ(actualInvalidConfigReason, nullopt);
}

TEST_F(MetricsManagerUtilTest, TestStringReplaceWithValueMatcherWithPositionAnyNested) {
    StatsdConfig config;
    config.set_id(12345);

    // Match on attribution_node[ALL].uid = 1
    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(1 /*attribution_node*/);
    fvm->set_position(Position::ANY);
    fvm->mutable_matches_tuple()->add_field_value_matcher()->set_field(2 /* tag */);
    fvm->mutable_matches_tuple()->mutable_field_value_matcher(0)->set_eq_string("bar");
    fvm->mutable_matches_tuple()
            ->mutable_field_value_matcher(0)
            ->mutable_replace_string()
            ->set_regex(R"([0-9]+$)");
    fvm->mutable_matches_tuple()
            ->mutable_field_value_matcher(0)
            ->mutable_replace_string()
            ->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_EQ(actualInvalidConfigReason, nullopt);
}

TEST_F(MetricsManagerUtilTest, TestStringReplaceWithPositionAllNested) {
    StatsdConfig config;
    config.set_id(12345);

    // Replace attribution_node[ALL].tag using "[0-9]+$" -> "".
    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(1 /*attribution_node*/);
    fvm->set_position(Position::ALL);
    fvm = fvm->mutable_matches_tuple()->add_field_value_matcher();
    fvm->set_field(2 /* tag */);
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_EQ(actualInvalidConfigReason, nullopt);
}

TEST_F(MetricsManagerUtilTest, TestMatcherWithStringReplaceAndNonStringValueMatcher) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(2 /*int_field*/);
    fvm->set_eq_int(1);
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("#");

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_INVALID_VALUE_MATCHER_WITH_STRING_REPLACE);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(111));
}

TEST_F(MetricsManagerUtilTest, TestCombinationMatcherWithStringReplace) {
    StatsdConfig config;
    config.set_id(12345);

    AtomMatcher* matcher = config.add_atom_matcher();
    matcher->set_id(111);
    matcher->mutable_simple_atom_matcher()->set_atom_id(util::TEST_ATOM_REPORTED);
    FieldValueMatcher* fvm = matcher->mutable_simple_atom_matcher()->add_field_value_matcher();
    fvm->set_field(5 /*string_field*/);
    fvm->mutable_replace_string()->set_regex(R"([0-9]+$)");
    fvm->mutable_replace_string()->set_replacement("#");

    matcher = config.add_atom_matcher();
    matcher->set_id(222);
    matcher->mutable_combination()->set_operation(LogicalOperation::NOT);
    matcher->mutable_combination()->add_matcher(111);

    optional<InvalidConfigReason> actualInvalidConfigReason = initConfig(config);

    ASSERT_NE(actualInvalidConfigReason, nullopt);
    EXPECT_EQ(actualInvalidConfigReason->reason,
              INVALID_CONFIG_REASON_MATCHER_COMBINATION_WITH_STRING_REPLACE);
    EXPECT_THAT(actualInvalidConfigReason->matcherIds, ElementsAre(222));
}

}  // namespace statsd
}  // namespace os
}  // namespace android

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
