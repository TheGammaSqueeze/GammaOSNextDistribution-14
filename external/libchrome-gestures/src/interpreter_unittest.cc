// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include <gtest/gtest.h>

#include "include/activity_replay.h"
#include "include/gestures.h"
#include "include/interpreter.h"
#include "include/prop_registry.h"
#include "include/unittest_util.h"
#include "include/util.h"

using std::string;

namespace gestures {

class InterpreterTest : public ::testing::Test {};

class InterpreterTestInterpreter : public Interpreter {
 public:
  explicit InterpreterTestInterpreter(PropRegistry* prop_reg)
      : Interpreter(prop_reg, nullptr, true),
        expected_hwstate_(nullptr),
        interpret_call_count_(0),
        handle_timer_call_count_(0),
        bool_prop_(prop_reg, "BoolProp", 0),
        double_prop_(prop_reg, "DoubleProp", 0),
        int_prop_(prop_reg, "IntProp", 0),
        string_prop_(prop_reg, "StringProp", "") {
    InitName();
    log_.reset(new ActivityLog(prop_reg));
  }

  Gesture return_value_;
  HardwareState* expected_hwstate_;
  int interpret_call_count_;
  int handle_timer_call_count_;
  BoolProperty bool_prop_;
  DoubleProperty double_prop_;
  IntProperty int_prop_;
  StringProperty string_prop_;
  char* expected_interpreter_name_;

 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate, stime_t* timeout) {
    interpret_call_count_++;
    EXPECT_STREQ(expected_interpreter_name_, name());
    EXPECT_NE(0, bool_prop_.val_);
    EXPECT_NE(0, double_prop_.val_);
    EXPECT_NE(0, int_prop_.val_);
    EXPECT_NE("", string_prop_.val_);
    EXPECT_TRUE(expected_hwstate_);
    EXPECT_DOUBLE_EQ(expected_hwstate_->timestamp, hwstate.timestamp);
    EXPECT_EQ(expected_hwstate_->buttons_down, hwstate.buttons_down);
    EXPECT_EQ(expected_hwstate_->finger_cnt, hwstate.finger_cnt);
    EXPECT_EQ(expected_hwstate_->touch_cnt, hwstate.touch_cnt);
    if (expected_hwstate_->finger_cnt == hwstate.finger_cnt) {
      for (size_t i = 0; i < expected_hwstate_->finger_cnt; i++)
        EXPECT_TRUE(expected_hwstate_->fingers[i] == hwstate.fingers[i]);
    }
    *timeout = 0.01;
    ProduceGesture(return_value_);
  }

  virtual void HandleTimerImpl(stime_t now, stime_t* timeout) {
    handle_timer_call_count_++;
    Interpreter::HandleTimerImpl(now, timeout);
    ProduceGesture(return_value_);
  }
};


TEST(InterpreterTest, SimpleTest) {
  PropRegistry prop_reg;
  InterpreterTestInterpreter* base_interpreter =
      new InterpreterTestInterpreter(&prop_reg);
  base_interpreter->SetEventLoggingEnabled(true);
  MetricsProperties mprops(&prop_reg);

  HardwareProperties hwprops = {
    .right = 100, .bottom = 100,
    .res_x = 10,
    .res_y = 10,
    .screen_x_dpi = 0,
    .screen_y_dpi = 0,
    .orientation_minimum = 1,
    .orientation_maximum = 2,
    .max_finger_cnt = 2, .max_touch_cnt = 5,
    .supports_t5r2 = 1, .support_semi_mt = 0, .is_button_pad = 0,
    .has_wheel = 0, .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };

  TestInterpreterWrapper wrapper(base_interpreter, &hwprops);

  base_interpreter->bool_prop_.val_ = 1;
  base_interpreter->double_prop_.val_ = 1;
  base_interpreter->int_prop_.val_ = 1;
  base_interpreter->string_prop_.val_ = "x";

  //if (prop_reg)
  //  prop_reg->set_activity_log(&(base_interpreter->log_));

  char interpreter_name[] = "InterpreterTestInterpreter";
  base_interpreter->expected_interpreter_name_ = interpreter_name;
  base_interpreter->return_value_ = Gesture(kGestureMove,
                                            0,  // start time
                                            1,  // end time
                                            -4,  // dx
                                            2.8);  // dy

  FingerState finger_state = {
    // TM, Tm, WM, Wm, Press, Orientation, X, Y, TrID
    0, 0, 0, 0, 10, 0, 50, 50, 1, 0
  };
  HardwareState hardware_state = make_hwstate(200000, 0, 1, 1, &finger_state);

  stime_t timeout = NO_DEADLINE;
  base_interpreter->expected_hwstate_ = &hardware_state;
  Gesture* result = wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_TRUE(base_interpreter->return_value_ == *result);
  ASSERT_GT(timeout, 0);
  stime_t now = hardware_state.timestamp + timeout;
  timeout = NO_DEADLINE;
  result = wrapper.HandleTimer(now, &timeout);
  EXPECT_TRUE(base_interpreter->return_value_ == *result);
  ASSERT_LT(timeout, 0);
  EXPECT_EQ(1, base_interpreter->interpret_call_count_);
  EXPECT_EQ(1, base_interpreter->handle_timer_call_count_);

  // Now, get the log
  string initial_log = base_interpreter->Encode();
  // Make a new interpreter and push the log through it
  PropRegistry prop_reg2;
  InterpreterTestInterpreter* base_interpreter2 =
      new InterpreterTestInterpreter(&prop_reg2);
  base_interpreter2->SetEventLoggingEnabled(true);
  base_interpreter2->return_value_ = base_interpreter->return_value_;
  base_interpreter2->expected_interpreter_name_ = interpreter_name;
  MetricsProperties mprops2(&prop_reg2);

  ActivityReplay replay(&prop_reg2);
  replay.Parse(initial_log);

  base_interpreter2->expected_hwstate_ = &hardware_state;

  replay.Replay(base_interpreter2, &mprops2);
  string final_log = base_interpreter2->Encode();
  EXPECT_EQ(initial_log, final_log);
  EXPECT_EQ(1, base_interpreter2->interpret_call_count_);
  EXPECT_EQ(1, base_interpreter2->handle_timer_call_count_);
}

class InterpreterResetLogTestInterpreter : public Interpreter {
 public:
  InterpreterResetLogTestInterpreter() : Interpreter(nullptr, nullptr, true) {
    log_.reset(new ActivityLog(nullptr));
  }
 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate,
                                     stime_t* timeout) {}

  virtual void HandleTimerImpl(stime_t now, stime_t* timeout) {}
};

TEST(InterpreterTest, ResetLogTest) {
  PropRegistry prop_reg;
  InterpreterResetLogTestInterpreter* base_interpreter =
      new InterpreterResetLogTestInterpreter();
  base_interpreter->SetEventLoggingEnabled(true);
  TestInterpreterWrapper wrapper(base_interpreter);

  FingerState finger_state = {
    // TM, Tm, WM, Wm, Press, Orientation, X, Y, TrID
    0, 0, 0, 0, 10, 0, 50, 50, 1, 0
  };
  HardwareState hardware_state = make_hwstate(200000, 0, 1, 1, &finger_state);
  stime_t timeout = NO_DEADLINE;
  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 1);

  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 2);

  // Assume the ResetLog property is set.
  base_interpreter->Clear();
  EXPECT_EQ(base_interpreter->log_->size(), 0);

  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 1);
}

TEST(InterpreterTest, LoggingDisabledByDefault) {
  PropRegistry prop_reg;
  InterpreterResetLogTestInterpreter* base_interpreter =
      new InterpreterResetLogTestInterpreter();
  TestInterpreterWrapper wrapper(base_interpreter);

  FingerState finger_state = {
    // TM, Tm, WM, Wm, Press, Orientation, X, Y, TrID
    0, 0, 0, 0, 10, 0, 50, 50, 1, 0
  };
  HardwareState hardware_state = make_hwstate(200000, 0, 1, 1, &finger_state);
  stime_t timeout = NO_DEADLINE;
  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 0);

  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 0);
}

TEST(InterpreterTest, EventDebugLoggingEnableTest) {
  InterpreterResetLogTestInterpreter* base_interpreter =
      new InterpreterResetLogTestInterpreter();

  base_interpreter->SetEventDebugLoggingEnabled(0);
  EXPECT_EQ(base_interpreter->GetEventDebugLoggingEnabled(), 0);

  using EventDebug = ActivityLog::EventDebug;
  base_interpreter->EventDebugLoggingEnable(EventDebug::HardwareState);
  EXPECT_EQ(base_interpreter->GetEventDebugLoggingEnabled(),
            1 << static_cast<int>(EventDebug::HardwareState));

  base_interpreter->EventDebugLoggingDisable(EventDebug::HardwareState);
  EXPECT_EQ(base_interpreter->GetEventDebugLoggingEnabled(), 0);
}

TEST(InterpreterTest, LogHardwareStateTest) {
  PropRegistry prop_reg;
  InterpreterResetLogTestInterpreter* base_interpreter =
      new InterpreterResetLogTestInterpreter();

  FingerState fs = { 0.0, 0.0, 0.0, 0.0, 9.0, 0.0, 3.0, 4.0, 22, 0 };
  HardwareState hs = make_hwstate(1.0, 0, 1, 1, &fs);

  base_interpreter->SetEventLoggingEnabled(false);
  base_interpreter->SetEventDebugLoggingEnabled(0);

  base_interpreter->LogHardwareStatePre(
      "InterpreterTest_LogHardwareStateTest", hs);
  EXPECT_EQ(base_interpreter->log_->size(), 0);

  base_interpreter->LogHardwareStatePost(
      "InterpreterTest_LogHardwareStateTest", hs);
  EXPECT_EQ(base_interpreter->log_->size(), 0);

  using EventDebug = ActivityLog::EventDebug;
  base_interpreter->SetEventLoggingEnabled(true);
  base_interpreter->EventDebugLoggingEnable(EventDebug::HardwareState);

  base_interpreter->LogHardwareStatePre(
      "InterpreterTest_LogHardwareStateTest", hs);
  EXPECT_EQ(base_interpreter->log_->size(), 1);

  base_interpreter->LogHardwareStatePost(
      "InterpreterTest_LogHardwareStateTest", hs);
  EXPECT_EQ(base_interpreter->log_->size(), 2);
}

TEST(InterpreterTest, LogGestureTest) {
  PropRegistry prop_reg;
  InterpreterResetLogTestInterpreter* base_interpreter =
      new InterpreterResetLogTestInterpreter();

  Gesture move(kGestureMove, 1.0, 2.0, 773, 4.0);

  base_interpreter->SetEventLoggingEnabled(false);
  base_interpreter->SetEventDebugLoggingEnabled(0);
  base_interpreter->LogGestureConsume("InterpreterTest_LogGestureTest", move);
  EXPECT_EQ(base_interpreter->log_->size(), 0);
  base_interpreter->LogGestureProduce("InterpreterTest_LogGestureTest", move);
  EXPECT_EQ(base_interpreter->log_->size(), 0);


  using EventDebug = ActivityLog::EventDebug;
  base_interpreter->SetEventLoggingEnabled(true);
  base_interpreter->EventDebugLoggingEnable(EventDebug::Gesture);
  base_interpreter->LogGestureConsume("InterpreterTest_LogGestureTest", move);
  EXPECT_EQ(base_interpreter->log_->size(), 1);
  base_interpreter->LogGestureProduce("InterpreterTest_LogGestureTest", move);
  EXPECT_EQ(base_interpreter->log_->size(), 2);
}

TEST(InterpreterTest, LogHandleTimerTest) {
  PropRegistry prop_reg;
  InterpreterResetLogTestInterpreter* base_interpreter =
      new InterpreterResetLogTestInterpreter();

  using EventDebug = ActivityLog::EventDebug;
  base_interpreter->SetEventLoggingEnabled(true);
  base_interpreter->EventDebugLoggingEnable(EventDebug::HandleTimer);

  stime_t timeout = 10;

  base_interpreter->LogHandleTimerPre("InterpreterTest_LogHandleTimerTest",
        0, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 1);

  base_interpreter->LogHandleTimerPost("InterpreterTest_LogHandleTimerTest",
        0, &timeout);
  EXPECT_EQ(base_interpreter->log_->size(), 2);
}

}  // namespace gestures
