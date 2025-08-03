// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <deque>
#include <math.h>
#include <vector>
#include <utility>

#include <gtest/gtest.h>

#include "include/gestures.h"
#include "include/t5r2_correcting_filter_interpreter.h"
#include "include/unittest_util.h"
#include "include/util.h"

using std::deque;
using std::make_pair;
using std::pair;
using std::vector;

namespace gestures {

class T5R2CorrectingFilterInterpreterTest : public ::testing::Test {};

class T5R2CorrectingFilterInterpreterTestInterpreter : public Interpreter {
 public:
  T5R2CorrectingFilterInterpreterTestInterpreter()
      : Interpreter(nullptr, nullptr, false) {}

  virtual void SyncInterpret(HardwareState& hwstate, stime_t* timeout) {
    if (expected_hardware_state_) {
      EXPECT_EQ(expected_hardware_state_->timestamp, hwstate.timestamp);
      EXPECT_EQ(expected_hardware_state_->buttons_down, hwstate.buttons_down);
      EXPECT_EQ(expected_hardware_state_->finger_cnt, hwstate.finger_cnt);
      EXPECT_EQ(expected_hardware_state_->touch_cnt, hwstate.touch_cnt);
      EXPECT_EQ(expected_hardware_state_->fingers, hwstate.fingers);
    }
    if (return_values_.empty())
      return;
    return_value_ = return_values_.front();
    return_values_.pop_front();
    if (return_value_.type == kGestureTypeNull)
      return;
    ProduceGesture(return_value_);
  }

  virtual void HandleTimer(stime_t now, stime_t* timeout) {
    EXPECT_TRUE(false);
  }

  Gesture return_value_;
  deque<Gesture> return_values_;
  HardwareState* expected_hardware_state_;
};

struct HardwareStateAndExpectations {
  HardwareState hs;
  bool modified;
};

// This test sends a bunch of HardwareStates into the T5R2 correcting
// interpreter and makes sure that, when expected, it alters the hardware
// state.

TEST(T5R2CorrectingFilterInterpreterTest, SimpleTest) {
  T5R2CorrectingFilterInterpreterTestInterpreter* base_interpreter = nullptr;
  std::unique_ptr<T5R2CorrectingFilterInterpreter> interpreter;

  HardwareProperties hwprops = {
    .right = 10, .bottom = 10,
    .res_x = 1,
    .res_y = 1,
    .screen_x_dpi = 0,
    .screen_y_dpi = 0,
    .orientation_minimum = -1,
    .orientation_maximum = 2,
    .max_finger_cnt = 2, .max_touch_cnt = 5,
    .supports_t5r2 = 0, .support_semi_mt = 0, .is_button_pad = 0,
    .has_wheel = 0, .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };
  TestInterpreterWrapper wrapper(interpreter.get(), &hwprops);

  FingerState fs[] = {
    { 0, 0, 0, 0, 1, 0, 150, 4000, 1, 0 },
    { 0, 0, 0, 0, 2, 0, 550, 2000, 2, 0 }
  };
  HardwareStateAndExpectations hse[] = {
    // normal case -- no change expected
    { make_hwstate(0.01, 0, 1, 1, &fs[0]), false },
    { make_hwstate(0.02, 0, 1, 3, &fs[0]), false },
    { make_hwstate(0.03, 0, 2, 3, &fs[0]), false },
    { make_hwstate(0.04, 0, 0, 0, nullptr), false },
    // problem -- change expected at end
    { make_hwstate(0.01, 0, 2, 3, &fs[0]), false },
    { make_hwstate(0.02, 0, 2, 3, &fs[0]), false },
    { make_hwstate(0.03, 0, 0, 1, nullptr), false },
    { make_hwstate(0.04, 0, 0, 1, nullptr), true },
    // problem -- change expected at end
    { make_hwstate(0.01, 0, 1, 1, &fs[0]), false },
    { make_hwstate(0.02, 0, 1, 3, &fs[0]), false },
    { make_hwstate(0.03, 0, 2, 3, &fs[0]), false },
    { make_hwstate(0.04, 0, 0, 2, nullptr), false },
    { make_hwstate(0.05, 0, 0, 2, nullptr), true }
  };

  for (size_t i = 0; i < arraysize(hse); i++) {
    // Reset the interpreter for each of the cases
    if (hse[i].hs.timestamp == 0.01) {
      base_interpreter =
          new T5R2CorrectingFilterInterpreterTestInterpreter;
      interpreter.reset(new T5R2CorrectingFilterInterpreter(
            nullptr, base_interpreter, nullptr));
      wrapper.Reset(interpreter.get());
    }
    HardwareState expected_hs = hse[i].hs;
    if (hse[i].modified)
      expected_hs.touch_cnt = 0;
    base_interpreter->expected_hardware_state_ = &expected_hs;
    stime_t timeout = NO_DEADLINE;
    EXPECT_EQ(nullptr, wrapper.SyncInterpret(hse[i].hs, &timeout));
    base_interpreter->expected_hardware_state_ = nullptr;
    EXPECT_LT(timeout, 0.0);
  }
}

}  // namespace gestures
