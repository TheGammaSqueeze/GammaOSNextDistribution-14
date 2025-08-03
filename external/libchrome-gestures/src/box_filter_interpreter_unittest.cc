// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <deque>
#include <math.h>
#include <vector>
#include <utility>

#include <gtest/gtest.h>

#include "include/box_filter_interpreter.h"
#include "include/gestures.h"
#include "include/macros.h"
#include "include/unittest_util.h"

using std::deque;
using std::make_pair;
using std::pair;
using std::vector;

static const HardwareProperties hwprops = {
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

namespace gestures {

class BoxFilterInterpreterTest : public ::testing::Test {};

class BoxFilterInterpreterTestInterpreter : public Interpreter {
 public:
  BoxFilterInterpreterTestInterpreter()
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

struct InputAndExpectedOutput {
  float in;
  float out;
};

TEST(BoxFilterInterpreterTest, SimpleTest) {
  BoxFilterInterpreterTestInterpreter* base_interpreter =
      new BoxFilterInterpreterTestInterpreter;
  BoxFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  interpreter.box_width_.val_ = 1.0;
  interpreter.box_height_.val_ = 1.0;
  TestInterpreterWrapper wrapper(&interpreter, &hwprops);

  EXPECT_FALSE(base_interpreter->handle_timer_called_);
  wrapper.HandleTimer(0.0, nullptr);
  EXPECT_TRUE(base_interpreter->handle_timer_called_);

  FingerState fs = { 0, 0, 0, 0, 1, 0, 3.0, 0.0, 1, 0 };
  HardwareState hs = make_hwstate(0.0, 0, 1, 1, &fs);

  InputAndExpectedOutput data[] = {
    { 3.0, 3.0 },
    { 4.0, 3.5 },
    { 3.0, 3.5 },
    { 4.0, 3.5 },
    { 5.0, 4.5 },
    { 6.0, 5.5 },
    { 5.0, 5.5 },
    { 4.0, 4.5 },
  };

  stime_t now = 0.0;
  const stime_t kTimeDelta = 0.01;
  for (size_t i = 0; i < arraysize(data); i++) {
    now += kTimeDelta;
    hs.timestamp = now;
    fs.position_y = data[i].in;
    wrapper.SyncInterpret(hs, nullptr);
    EXPECT_FLOAT_EQ(data[i].out, fs.position_y) << "i=" << i;
  }
}

TEST(BoxFilterInterpreterTest, ZeroSizeBoxTest) {
  BoxFilterInterpreterTestInterpreter* base_interpreter =
      new BoxFilterInterpreterTestInterpreter;
  BoxFilterInterpreter interpreter(nullptr, base_interpreter, nullptr);
  interpreter.box_width_.val_ = 0.0;
  interpreter.box_height_.val_ = 0.0;
  TestInterpreterWrapper wrapper(&interpreter, &hwprops);

  EXPECT_FALSE(base_interpreter->handle_timer_called_);
  wrapper.HandleTimer(0.0, nullptr);
  EXPECT_TRUE(base_interpreter->handle_timer_called_);

  FingerState fs = { 0, 0, 0, 0, 1, 0, 3.0, 0.0, 1, 0 };
  HardwareState hs = make_hwstate(0.0, 0, 1, 1, &fs);

  InputAndExpectedOutput data[] = {
    { 3.0, 3.0 },
    { 4.0, 4.0 },
    { 3.0, 3.0 },
    { 4.0, 4.0 },
    { 5.0, 5.0 },
    { 6.0, 6.0 },
    { 5.0, 5.0 },
    { 4.0, 4.0 },
  };

  stime_t now = 0.0;
  const stime_t kTimeDelta = 0.01;
  for (size_t i = 0; i < arraysize(data); i++) {
    now += kTimeDelta;
    hs.timestamp = now;
    fs.position_y = data[i].in;
    wrapper.SyncInterpret(hs, nullptr);
    EXPECT_FLOAT_EQ(data[i].out, fs.position_y) << "i=" << i;
  }
}

}  // namespace gestures
