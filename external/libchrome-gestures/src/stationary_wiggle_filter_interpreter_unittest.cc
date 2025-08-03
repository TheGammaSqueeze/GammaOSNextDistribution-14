// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/stationary_wiggle_filter_interpreter.h"
#include "include/unittest_util.h"

namespace gestures {

class StationaryWiggleFilterInterpreterTest : public ::testing::Test {};

class StationaryWiggleFilterInterpreterTestInterpreter : public Interpreter {
 public:
  StationaryWiggleFilterInterpreterTestInterpreter()
      : Interpreter(nullptr, nullptr, false),
        handle_timer_called_(false) {}

  virtual void SyncInterpret(HardwareState& hwstate, stime_t* timeout) {
    EXPECT_EQ(1, hwstate.finger_cnt);
    prev_ = hwstate.fingers[0];
  }

  virtual void HandleTimer(stime_t now, stime_t* timeout) {
    handle_timer_called_ = true;
  }

  FingerState prev_;
  bool handle_timer_called_;
};


TEST(StationaryWiggleFilterInterpreterTest, SimpleTest) {
  FingerEnergy fe = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
  FingerEnergy fe_eq = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
  FingerEnergy fe_ne0 = {9.0, 1.0, 1.0, 1.0, 1.0, 1.0};
  FingerEnergy fe_ne1 = {1.0, 9.0, 1.0, 1.0, 1.0, 1.0};
  FingerEnergy fe_ne2 = {1.0, 1.0, 9.0, 1.0, 1.0, 1.0};
  FingerEnergy fe_ne3 = {1.0, 1.0, 1.0, 9.0, 1.0, 1.0};
  FingerEnergy fe_ne4 = {1.0, 1.0, 1.0, 1.0, 9.0, 1.0};
  FingerEnergy fe_ne5 = {1.0, 1.0, 1.0, 1.0, 1.0, 9.0};

  EXPECT_EQ(fe, fe_eq);
  EXPECT_NE(fe, fe_ne0);
  EXPECT_NE(fe, fe_ne1);
  EXPECT_NE(fe, fe_ne2);
  EXPECT_NE(fe, fe_ne3);
  EXPECT_NE(fe, fe_ne4);
  EXPECT_NE(fe, fe_ne5);


  StationaryWiggleFilterInterpreterTestInterpreter* base_interpreter =
      new StationaryWiggleFilterInterpreterTestInterpreter;
  StationaryWiggleFilterInterpreter interpreter(
      nullptr, base_interpreter, nullptr);

  EXPECT_FALSE(interpreter.enabled_.val_);
  interpreter.enabled_.val_ = true;

  HardwareProperties hwprops = {
    .right = 100, .bottom = 100,
    .res_x = 1, .res_y = 1,
    .screen_x_dpi = 0, .screen_y_dpi = 0,
    .orientation_minimum = -1,
    .orientation_maximum = 2,
    .max_finger_cnt = 5, .max_touch_cnt = 5,
    .supports_t5r2 = 0, .support_semi_mt = 0, .is_button_pad = 1,
    .has_wheel = 0, .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };
  TestInterpreterWrapper wrapper(&interpreter, &hwprops);

  EXPECT_FALSE(base_interpreter->handle_timer_called_);
  wrapper.HandleTimer(0.0, nullptr);
  EXPECT_TRUE(base_interpreter->handle_timer_called_);

  FingerState finger_states[] = {
    // TM, Tm, WM, Wm, Press, Orientation, X, Y, TrID
    // Consistent movement for 16 frames
    {0, 0, 0, 0, 20, 0, 40, 20, 1, 0}, // 0
    {0, 0, 0, 0, 20, 0, 40, 25, 1, 0}, // 1
    {0, 0, 0, 0, 20, 0, 40, 30, 1, 0}, // 2
    {0, 0, 0, 0, 20, 0, 40, 35, 1, 0}, // 3
    {0, 0, 0, 0, 20, 0, 40, 40, 1, 0}, // 4
    {0, 0, 0, 0, 20, 0, 40, 45, 1, 0}, // 5
    {0, 0, 0, 0, 20, 0, 40, 50, 1, 0}, // 6
    {0, 0, 0, 0, 20, 0, 40, 55, 1, 0}, // 7
    {0, 0, 0, 0, 20, 0, 40, 60, 1, 0}, // 8
    {0, 0, 0, 0, 20, 0, 40, 65, 1, 0}, // 9
    {0, 0, 0, 0, 20, 0, 40, 70, 1, 0}, // 10
    {0, 0, 0, 0, 20, 0, 40, 75, 1, 0}, // 11
    {0, 0, 0, 0, 20, 0, 40, 80, 1, 0}, // 12
    {0, 0, 0, 0, 20, 0, 40, 85, 1, 0}, // 13
    {0, 0, 0, 0, 20, 0, 40, 90, 1, 0}, // 14
    {0, 0, 0, 0, 20, 0, 40, 95, 1, 0}, // 15
  };

  HardwareState hardware_states[] = {
    // time, buttons, finger count, touch count, finger states pointer
    make_hwstate(1.00, 0, 1, 1, &finger_states[0]),
    make_hwstate(1.01, 0, 1, 1, &finger_states[1]),
    make_hwstate(1.02, 0, 1, 1, &finger_states[2]),
    make_hwstate(1.03, 0, 1, 1, &finger_states[3]),
    make_hwstate(1.04, 0, 1, 1, &finger_states[4]),
    make_hwstate(1.05, 0, 1, 1, &finger_states[5]),
    make_hwstate(1.06, 0, 1, 1, &finger_states[6]),
    make_hwstate(1.07, 0, 1, 1, &finger_states[7]),
    make_hwstate(1.08, 0, 1, 1, &finger_states[8]),
    make_hwstate(1.09, 0, 1, 1, &finger_states[9]),
    make_hwstate(1.10, 0, 1, 1, &finger_states[10]),
    make_hwstate(1.11, 0, 1, 1, &finger_states[11]),
    make_hwstate(1.12, 0, 1, 1, &finger_states[12]),
    make_hwstate(1.13, 0, 1, 1, &finger_states[13]),
    make_hwstate(1.14, 0, 1, 1, &finger_states[14]),
    make_hwstate(1.15, 0, 1, 1, &finger_states[15]),
  };

  unsigned expected[] = {
    0, 0, 0, 0,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
    GESTURES_FINGER_INSTANTANEOUS_MOVING,
  };

  for (size_t i = 0; i < arraysize(hardware_states); i++) {
    HardwareState& hwstate = hardware_states[i];
    wrapper.SyncInterpret(hwstate, nullptr);
    for (int j = 0; j < hwstate.finger_cnt; ++j) {
      FingerState *fs = &hwstate.fingers[j];
      EXPECT_EQ(fs->flags & (GESTURES_FINGER_WARP_X |
                             GESTURES_FINGER_WARP_Y |
                             GESTURES_FINGER_INSTANTANEOUS_MOVING),
                expected[i]);
    }
  }

  EXPECT_TRUE(interpreter.enabled_.val_);
  EXPECT_EQ(interpreter.threshold_.val_, 0.012);
  EXPECT_EQ(interpreter.hysteresis_.val_, 0.006);
}

}  // namespace gestures
