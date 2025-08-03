// Copyright 2017 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/gestures.h"
#include "include/string_util.h"
#include "include/timestamp_filter_interpreter.h"
#include "include/unittest_util.h"
#include "include/util.h"

namespace gestures {

using EventDebug = ActivityLog::EventDebug;

class TimestampFilterInterpreterTest : public ::testing::Test {};
class TimestampFilterInterpreterParmTest :
          public TimestampFilterInterpreterTest,
          public testing::WithParamInterface<stime_t> {};

class TimestampFilterInterpreterTestInterpreter : public Interpreter {
 public:
  TimestampFilterInterpreterTestInterpreter()
      : Interpreter(nullptr, nullptr, false) {}
  virtual void HandleTimer(stime_t now, stime_t* timeout) {
    // For tests, use timeout to return the adjusted timestamp.
    *timeout = now;
  }

};

static HardwareState make_hwstate_times(stime_t timestamp,
                                        stime_t msc_timestamp) {
  return { timestamp, 0, 1, 1, nullptr, 0, 0, 0, 0, 0, msc_timestamp };
}

TEST(TimestampFilterInterpreterTest, SimpleTest) {
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  HardwareState hs[] = {
    make_hwstate_times(1.000, 0.000),
    make_hwstate_times(1.010, 0.012),
    make_hwstate_times(1.020, 0.018),
    make_hwstate_times(1.030, 0.031),
  };

  stime_t expected_timestamps[] = { 1.000, 1.012, 1.018, 1.031 };

  for (size_t i = 0; i < arraysize(hs); i++) {
    wrapper.SyncInterpret(hs[i], nullptr);
    EXPECT_EQ(hs[i].timestamp, expected_timestamps[i]);
  }

  stime_t adjusted_timestamp = 0.0;
  wrapper.HandleTimer(2.0, &adjusted_timestamp);

  // Should be adjusted by the maximum skew between timestamps.
  EXPECT_FLOAT_EQ(adjusted_timestamp, 2.002);
}

TEST(TimestampFilterInterpreterTest, NoMscTimestampTest) {
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  HardwareState hs[] = {
    make_hwstate_times(1.000, 0.000),
    make_hwstate_times(1.010, 0.000),
    make_hwstate_times(1.020, 0.000),
    make_hwstate_times(1.030, 0.000),
  };

  for (size_t i = 0; i < arraysize(hs); i++) {
    stime_t expected_timestamp = hs[i].timestamp;
    wrapper.SyncInterpret(hs[i], nullptr);
    EXPECT_EQ(hs[i].timestamp, expected_timestamp);
  }

  stime_t adjusted_timestamp = 0.0;
  wrapper.HandleTimer(2.0, &adjusted_timestamp);

  EXPECT_FLOAT_EQ(adjusted_timestamp, 2.0);
}

TEST(TimestampFilterInterpreterTest, MscTimestampResetTest) {
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  HardwareState hs[] = {
    make_hwstate_times(1.000, 0.000),
    make_hwstate_times(1.010, 0.012),
    make_hwstate_times(1.020, 0.018),
    make_hwstate_times(1.030, 0.035),
    make_hwstate_times(3.000, 0.000),  // msc_timestamp reset to 0
    make_hwstate_times(3.010, 0.008),
    make_hwstate_times(3.020, 0.020),
    make_hwstate_times(3.030, 0.031),
  };

  stime_t expected_timestamps[] = {
    1.000, 1.012, 1.018, 1.035,
    3.000, 3.008, 3.020, 3.031
  };

  for (size_t i = 0; i < arraysize(hs); i++) {
    wrapper.SyncInterpret(hs[i], nullptr);
    EXPECT_EQ(hs[i].timestamp, expected_timestamps[i]);
  }

  stime_t adjusted_timestamp = 0.0;
  wrapper.HandleTimer(4.0, &adjusted_timestamp);

  // Should be adjusted by the maximum skew between timestamps, but only since
  // the last reset.
  EXPECT_FLOAT_EQ(adjusted_timestamp, 4.001);
}

TEST(TimestampFilterInterpreterTest, FakeTimestampTest) {
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  interpreter.fake_timestamp_delta_.val_ = 0.010;

  HardwareState hs[] = {
    make_hwstate_times(1.000, 0.002),
    make_hwstate_times(1.002, 6.553),
    make_hwstate_times(1.008, 0.001),
    make_hwstate_times(1.031, 0.001),
  };

  stime_t expected_timestamps[] = { 1.000, 1.010, 1.020, 1.030 };

  for (size_t i = 0; i < arraysize(hs); i++) {
    wrapper.SyncInterpret(hs[i], nullptr);
    EXPECT_TRUE(DoubleEq(hs[i].timestamp, expected_timestamps[i]));
  }

  stime_t adjusted_timestamp = 0.0;
  wrapper.HandleTimer(2.0, &adjusted_timestamp);

  // Should be adjusted by the maximum skew between timestamps.
  EXPECT_FLOAT_EQ(adjusted_timestamp, 2.012);
}

TEST(TimestampFilterInterpreterTest, FakeTimestampJumpForwardTest) {
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  interpreter.fake_timestamp_delta_.val_ = 0.010;

  HardwareState hs[] = {
    make_hwstate_times(1.000, 0.002),
    make_hwstate_times(1.002, 6.553),
    make_hwstate_times(1.008, 0.001),
    make_hwstate_times(1.031, 0.001),
    make_hwstate_times(2.000, 6.552),
    make_hwstate_times(2.002, 6.553),
    make_hwstate_times(2.011, 0.002),
    make_hwstate_times(2.031, 0.001),
  };

  stime_t expected_timestamps[] = {
    1.000, 1.010, 1.020, 1.030,
    2.000, 2.010, 2.020, 2.030
  };

  for (size_t i = 0; i < arraysize(hs); i++) {
    wrapper.SyncInterpret(hs[i], nullptr);
    EXPECT_TRUE(DoubleEq(hs[i].timestamp, expected_timestamps[i]));
  }

  stime_t adjusted_timestamp = 0.0;
  wrapper.HandleTimer(3.0, &adjusted_timestamp);

  // Should be adjusted by the maximum skew between timestamps, but only since
  // the last reset.
  EXPECT_FLOAT_EQ(adjusted_timestamp, 3.009);
}

TEST(TimestampFilterInterpreterTest, FakeTimestampFallBackwardTest) {
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  interpreter.fake_timestamp_delta_.val_ = 0.010;
  interpreter.fake_timestamp_max_divergence_ = 0.030;

  HardwareState hs[] = {
    make_hwstate_times(1.000, 0.002),
    make_hwstate_times(1.001, 6.553),
    make_hwstate_times(1.002, 0.001),
    make_hwstate_times(1.003, 0.001),
    make_hwstate_times(1.004, 6.552),
    make_hwstate_times(1.005, 6.553),
    make_hwstate_times(1.006, 0.002),
    make_hwstate_times(1.009, 6.552),
  };

  stime_t expected_timestamps[] = {
    1.000, 1.010, 1.020, 1.030,
    1.004, 1.014, 1.024, 1.034,
  };

  for (size_t i = 0; i < arraysize(hs); i++) {
    wrapper.SyncInterpret(hs[i], nullptr);
    EXPECT_TRUE(DoubleEq(hs[i].timestamp, expected_timestamps[i]));
  }

  stime_t adjusted_timestamp = 0.0;
  wrapper.HandleTimer(2.0, &adjusted_timestamp);

  // Should be adjusted by the maximum skew between timestamps, but only since
  // the last reset.
  EXPECT_FLOAT_EQ(adjusted_timestamp, 2.025);
}

TEST(TimestampFilterInterpreterTest, GestureDebugTest) {
  PropRegistry prop_reg;
  TimestampFilterInterpreter interpreter(&prop_reg, nullptr, nullptr);

  using EventDebug = ActivityLog::EventDebug;
  interpreter.SetEventLoggingEnabled(true);
  interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  interpreter.EventDebugLoggingEnable(EventDebug::HardwareState);
  interpreter.EventDebugLoggingEnable(EventDebug::HandleTimer);
  interpreter.EventDebugLoggingEnable(EventDebug::Timestamp);
  interpreter.log_.reset(new ActivityLog(&prop_reg));

  EXPECT_EQ(interpreter.log_->size(), 0);
  interpreter.ConsumeGesture(Gesture(kGestureButtonsChange,
                                     1,  // start time
                                     2,  // end time
                                     0, // down
                                     0, // up
                                     false)); // is_tap

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(interpreter.log_->size(), 3);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyTimestampGestureDebug));
  EXPECT_EQ(node[ActivityLog::kKeyTimestampDebugSkew],
            Json::Value(interpreter.skew_));
  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureProduce));
  interpreter.log_->Clear();
}

TEST_P(TimestampFilterInterpreterParmTest, TimestampDebugLoggingTest) {
  PropRegistry prop_reg;
  TimestampFilterInterpreterTestInterpreter* base_interpreter =
      new TimestampFilterInterpreterTestInterpreter;
  TimestampFilterInterpreter interpreter(&prop_reg, base_interpreter, nullptr);
  TestInterpreterWrapper wrapper(&interpreter);

  interpreter.SetEventLoggingEnabled(true);
  interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  interpreter.EventDebugLoggingEnable(EventDebug::HardwareState);
  interpreter.EventDebugLoggingEnable(EventDebug::HandleTimer);
  interpreter.EventDebugLoggingEnable(EventDebug::Timestamp);
  interpreter.log_.reset(new ActivityLog(&prop_reg));
  interpreter.fake_timestamp_delta_.val_ = GetParam();

  HardwareState hs = make_hwstate_times(1.000, 0.000);
  wrapper.SyncInterpret(hs, nullptr);

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(interpreter.log_->size(), 4);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType].asString(),
            ActivityLog::kKeyHardwareState);
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType].asString(),
            ActivityLog::kKeyHardwareStatePre);

  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType].asString(),
            ActivityLog::kKeyTimestampHardwareStateDebug);
  if (interpreter.fake_timestamp_delta_.val_ == 0.0) {
    EXPECT_FALSE(node[ActivityLog::kKeyTimestampDebugIsUsingFake].asBool());
  } else {
    EXPECT_TRUE(node[ActivityLog::kKeyTimestampDebugIsUsingFake].asBool());
  }

  node = tree[ActivityLog::kKeyRoot][3];
  EXPECT_EQ(node[ActivityLog::kKeyType].asString(),
            ActivityLog::kKeyHardwareStatePost);
  interpreter.log_->Clear();
}

INSTANTIATE_TEST_SUITE_P(TimestampFilterInterpreter,
                         TimestampFilterInterpreterParmTest,
                         testing::Values(0.000, 0.010));

}  // namespace gestures
