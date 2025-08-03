// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/finger_merge_filter_interpreter.h"
#include "include/unittest_util.h"

namespace gestures {

class FingerMergeFilterInterpreterTest : public ::testing::Test {};

class FingerMergeFilterInterpreterTestInterpreter : public Interpreter {
 public:
  FingerMergeFilterInterpreterTestInterpreter()
      : Interpreter(nullptr, nullptr, false),
        handle_timer_called_(false) {}

  virtual void SyncInterpret(HardwareState& hwstate, stime_t* timeout) {
    EXPECT_EQ(2, hwstate.finger_cnt);
    prev_ = hwstate.fingers[0];
  }

  virtual void HandleTimer(stime_t now, stime_t* timeout) {
    handle_timer_called_ = true;
  }

  FingerState prev_;
  bool handle_timer_called_;
};


TEST(FingerMergeFilterInterpreterTest, SimpleTest) {
  FingerMergeFilterInterpreter::Start loc = {1.0, 1.0, 1.0};
  FingerMergeFilterInterpreter::Start loc_eq = {1.0, 1.0, 1.0};
  FingerMergeFilterInterpreter::Start loc_ne0 = {9.0, 1.0, 1.0};
  FingerMergeFilterInterpreter::Start loc_ne1 = {1.0, 9.0, 1.0};
  FingerMergeFilterInterpreter::Start loc_ne2 = {1.0, 1.0, 9.0};

  EXPECT_EQ(loc, loc_eq);
  EXPECT_NE(loc, loc_ne0);
  EXPECT_NE(loc, loc_ne1);
  EXPECT_NE(loc, loc_ne2);


  FingerMergeFilterInterpreterTestInterpreter* base_interpreter =
      new FingerMergeFilterInterpreterTestInterpreter;
  FingerMergeFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);

  EXPECT_FALSE(interpreter.finger_merge_filter_enable_.val_);
  interpreter.finger_merge_filter_enable_.val_ = true;

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
    // Consistent movement for 6 frames
    {0, 0, 0, 0, 20, 0, 40, 20, 1, 0}, // 0
    {0, 0, 0, 0, 20, 0, 42, 22, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 25, 1, 0}, // 2
    {0, 0, 0, 0, 20, 0, 42, 27, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 30, 1, 0}, // 4
    {0, 0, 0, 0, 20, 0, 42, 32, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 35, 1, 0}, // 6
    {0, 0, 0, 0, 20, 0, 42, 37, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 40, 1, 0}, // 8
    {0, 0, 0, 0, 20, 0, 42, 42, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 45, 1, 0}, // 10
    {0, 0, 0, 0, 20, 0, 42, 47, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 50, 1, 0}, // 12
    {0, 0, 0, 0, 20, 0, 42, 52, 2, 0},

    {0, 0, 0, 0, 20, 0, 40, 55, 1, 0}, // 14
    {0, 0, 0, 0, 20, 0, 42, 57, 2, 0},
  };

  HardwareState hardware_states[] = {
    // time, buttons, finger count, touch count, finger states pointer
    make_hwstate(1.00, 0, 2, 2, &finger_states[0]),
    make_hwstate(1.01, 0, 2, 2, &finger_states[2]),
    make_hwstate(1.02, 0, 2, 2, &finger_states[4]),
    make_hwstate(1.03, 0, 2, 2, &finger_states[6]),
    make_hwstate(1.04, 0, 2, 2, &finger_states[8]),
    make_hwstate(1.05, 0, 2, 2, &finger_states[10]),
    make_hwstate(1.06, 0, 2, 2, &finger_states[12]),
    make_hwstate(1.07, 0, 2, 2, &finger_states[14]),
  };

  for (size_t i = 0; i < arraysize(hardware_states); i++) {
    HardwareState& hwstate = hardware_states[i];
    wrapper.SyncInterpret(hwstate, nullptr);
    for (short j = 0; j < hwstate.finger_cnt; j++) {
      FingerState *fs = hwstate.fingers;
      EXPECT_TRUE(fs[j].flags & GESTURES_FINGER_MERGE);
    }
  }

  EXPECT_TRUE(interpreter.finger_merge_filter_enable_.val_);
  EXPECT_EQ(interpreter.merge_distance_threshold_.val_, 140.0);
  EXPECT_EQ(interpreter.max_pressure_threshold_.val_, 83.0);
  EXPECT_EQ(interpreter.min_pressure_threshold_.val_, 51.0);
  EXPECT_EQ(interpreter.min_major_threshold_.val_, 280.0);
  EXPECT_EQ(interpreter.merged_major_pressure_ratio_.val_, 5.0);
  EXPECT_EQ(interpreter.merged_major_threshold_.val_, 380.0);
  EXPECT_EQ(interpreter.x_jump_min_displacement_.val_, 6.0);
  EXPECT_EQ(interpreter.x_jump_max_displacement_.val_, 9.0);
  EXPECT_EQ(interpreter.suspicious_angle_min_displacement_.val_, 7.0);
  EXPECT_EQ(interpreter.max_x_move_.val_, 180.0);
  EXPECT_EQ(interpreter.max_y_move_.val_, 60.0);
  EXPECT_EQ(interpreter.max_age_.val_, 0.35);
}

}  // namespace gestures
