// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <cstdio>
#include <string>

#include <gtest/gtest.h>

#include "include/activity_replay.h"
#include "include/file_util.h"
#include "include/gestures.h"
#include "include/interpreter.h"
#include "include/logging_filter_interpreter.h"
#include "include/prop_registry.h"
#include "include/unittest_util.h"
using std::string;

namespace gestures {

class LoggingFilterInterpreterTest : public ::testing::Test {};

class LoggingFilterInterpreterResetLogTestInterpreter : public Interpreter {
 public:
  LoggingFilterInterpreterResetLogTestInterpreter()
      : Interpreter(nullptr, nullptr, false) {}
 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate,
                                     stime_t* timeout) {}
  virtual void SetHardwarePropertiesImpl(const HardwareProperties& hw_props) {
  }
  virtual void HandleTimerImpl(stime_t now, stime_t* timeout) {}
};

TEST(LoggingFilterInterpreterTest, LogResetHandlerTest) {
  PropRegistry prop_reg;
  LoggingFilterInterpreterResetLogTestInterpreter* base_interpreter =
      new LoggingFilterInterpreterResetLogTestInterpreter();
  LoggingFilterInterpreter interpreter(&prop_reg, base_interpreter, nullptr);

  interpreter.event_logging_enable_.SetValue(Json::Value(true));
  interpreter.BoolWasWritten(&interpreter.event_logging_enable_);

  using EventDebug = ActivityLog::EventDebug;
  EXPECT_EQ(interpreter.enable_event_debug_logging_, 0);
  interpreter.event_debug_logging_enable_.SetValue(
    Json::Value((1 << static_cast<int>(EventDebug::Gesture)) |
                (1 << static_cast<int>(EventDebug::HardwareState))));
  interpreter.IntWasWritten(&interpreter.event_debug_logging_enable_);
  EXPECT_EQ(interpreter.enable_event_debug_logging_,
            (1 << static_cast<int>(EventDebug::Gesture)) |
            (1 << static_cast<int>(EventDebug::HardwareState)));

  HardwareProperties hwprops = {
    .right = 100, .bottom = 100,
    .res_x = 10,
    .res_y = 10,
    .screen_x_dpi = 0,
    .screen_y_dpi = 0,
    .orientation_minimum = -1,
    .orientation_maximum = 2,
    .max_finger_cnt = 2, .max_touch_cnt = 5,
    .supports_t5r2 = 1, .support_semi_mt = 0, .is_button_pad = 0,
    .has_wheel = 0, .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };

  TestInterpreterWrapper wrapper(&interpreter, &hwprops);
  FingerState finger_state = {
    // TM, Tm, WM, Wm, Press, Orientation, X, Y, TrID
    0, 0, 0, 0, 10, 0, 50, 50, 1, 0
  };
  HardwareState hardware_state = make_hwstate(200000, 0, 1, 1, &finger_state);
  stime_t timeout = NO_DEADLINE;
  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(interpreter.log_->size(), 1);

  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(interpreter.log_->size(), 2);

  // Assume the ResetLog property is set.
  interpreter.logging_reset_.HandleGesturesPropWritten();
  EXPECT_EQ(interpreter.log_->size(), 0);

  wrapper.SyncInterpret(hardware_state, &timeout);
  EXPECT_EQ(interpreter.log_->size(), 1);

  std::string str = interpreter.EncodeActivityLog();
  EXPECT_NE(0, str.size());

  // std::tmpnam is considered unsafe because another process could create the
  // temporary file after time std::tmpnam returns the name but before the code
  // actually opens it. Because this is just test code, we don't need to be
  // concerned about such security holes here.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
  const char* filename = std::tmpnam(nullptr);
#pragma GCC diagnostic pop
  ASSERT_NE(nullptr, filename) << "Couldn't generate a temporary file name";
  interpreter.log_location_.SetValue(Json::Value(filename));
  interpreter.IntWasWritten(&interpreter.logging_notify_);

  std::string read_str = "";
  bool couldRead = ReadFileToString(filename, &read_str);

  EXPECT_TRUE(couldRead);
  EXPECT_NE(0, read_str.size());
}
}  // namespace gestures
