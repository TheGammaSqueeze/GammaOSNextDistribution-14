// Copyright 2011 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/fling_stop_filter_interpreter.h"
#include "include/gestures.h"
#include "include/unittest_util.h"
#include "include/util.h"

namespace gestures {

class FlingStopFilterInterpreterTest : public ::testing::Test {};

namespace {

class FlingStopFilterInterpreterTestInterpreter : public Interpreter {
 public:
  FlingStopFilterInterpreterTestInterpreter()
      : Interpreter(nullptr, nullptr, false),
        sync_interpret_called_(false), handle_timer_called_(true),
        next_timeout_(NO_DEADLINE) {}

  virtual void SyncInterpret(HardwareState& hwstate, stime_t* timeout) {
    sync_interpret_called_ = true;
    *timeout = next_timeout_;
  }

  virtual void HandleTimer(stime_t now, stime_t* timeout) {
    handle_timer_called_ = true;
    *timeout = next_timeout_;
  }

  bool sync_interpret_called_;
  bool handle_timer_called_;
  stime_t next_timeout_;
};


struct SimpleTestInputs {
  stime_t now;
  short touch_cnt;  // -1 for timer callback

  bool expected_call_next;
  stime_t next_timeout;  // NO_DEADLINE for none (similarly for below)
  stime_t expected_local_deadline;
  stime_t expected_next_deadline;
  stime_t expected_timeout;
  bool expected_fling_stop_out;
};

}  // namespace {}

TEST(FlingStopFilterInterpreterTest, SimpleTest) {
  FlingStopFilterInterpreterTestInterpreter* base_interpreter =
      new FlingStopFilterInterpreterTestInterpreter;
  FlingStopFilterInterpreter interpreter(nullptr, base_interpreter, nullptr,
                                         GESTURES_DEVCLASS_TOUCHPAD);
  TestInterpreterWrapper wrapper(&interpreter);

  const stime_t kTO = interpreter.fling_stop_timeout_.val_ = 0.03;
  const stime_t kED= interpreter.fling_stop_extra_delay_.val_ = 0.055;
  const stime_t kND = NO_DEADLINE;

  SimpleTestInputs inputs[] = {
    // timeout case
    { 0.01,        1,  true, kND, 0.01 + kTO, kND,        kTO, false },
    { 0.02,        1,  true, kND, 0.01 + kTO, kND, kTO - 0.01, false },
    { 0.03,        0,  true, kND, 0.01 + kTO, kND, kTO - 0.02, false },
    { 0.01 + kTO, -1, false, kND,        kND, kND,        kND, true },

    // multiple fingers come down, timeout
    { 3.01,      1, true, kND, 3.01 + kTO,       kND,              kTO, false },
    { 3.02,      2, true, kND, 3.01 + kTO + kED, kND, kTO + kED - 0.01, false },
    { 3.03,      0, true, kND, 3.01 + kTO + kED, kND, kTO + kED - 0.02, false },
    { 3.01 + kTO + kED, -1, false, kND,     kND, kND,              kND, true },

    // Dual timeouts, local is shorter
    { 6.01,        1,  true,  kND, 6.01 + kTO,        kND,        kTO, false },
    { 6.02,        0,  true,  0.1, 6.01 + kTO, 6.02 + 0.1, kTO - 0.01, false },
    { 6.01 + kTO, -1, false,  kND,        kND, 6.02 + 0.1,       0.08, true },
    { 6.02 + 0.1, -1,  true,  kND,        kND,        kND,        kND, false },

    // Dual timeouts, local is longer
    { 9.01,        1,  true,  kND, 9.01 + kTO,        kND,       kTO, false },
    { 9.02,        0,  true,  .01, 9.01 + kTO, 9.02 + .01,       .01, false },
    { 9.02 + .01, -1,  true,  kND, 9.01 + kTO, kND, kTO - .01 - 0.01, false },
    { 9.01 + kTO, -1, false,  kND,        kND,        kND,       kND, true },

    // Dual timeouts, new timeout on handling timeout
    { 12.01,        1, true, kND, 12.01 + kTO,         kND,        kTO, false },
    { 12.02,        0, true, 0.1, 12.01 + kTO, 12.02 + 0.1, kTO - 0.01, false },
    { 12.01 + kTO, -1, false, kND,        kND, 12.02 + 0.1,       0.08, true },
    { 12.02 + 0.1, -1,  true, 0.1,        kND,       12.22,        0.1, false },
    { 12.22,       -1,  true, kND,        kND,         kND,        kND, false },

    // Overrun deadline
    { 15.01,        1,  true, kND, 15.01 + kTO, kND,        kTO, false },
    { 15.02,        1,  true, kND, 15.01 + kTO, kND, kTO - 0.01, false },
    { 15.03,        0,  true, kND, 15.01 + kTO, kND, kTO - 0.02, false },
    { 15.02 + kTO,  0,  true, kND,         kND, kND,        kND, true },
  };

  for (size_t i = 0; i < arraysize(inputs); i++) {
    SimpleTestInputs& input = inputs[i];

    base_interpreter->sync_interpret_called_ = false;
    base_interpreter->handle_timer_called_ = false;
    base_interpreter->next_timeout_ = input.next_timeout;

    stime_t timeout = kND;

    Gesture* ret = nullptr;
    if (input.touch_cnt >= 0) {
      FingerState fs[5];
      memset(fs, 0, sizeof(fs));
      unsigned short touch_cnt = static_cast<unsigned short>(input.touch_cnt);
      HardwareState hs = make_hwstate(input.now, 0, touch_cnt, touch_cnt, fs);

      ret = wrapper.SyncInterpret(hs, &timeout);

      EXPECT_EQ(input.expected_call_next,
                base_interpreter->sync_interpret_called_) << "i=" << i;
      EXPECT_FALSE(base_interpreter->handle_timer_called_) << "i=" << i;
    } else {
      ret = wrapper.HandleTimer(input.now, &timeout);

      EXPECT_EQ(input.expected_call_next,
                base_interpreter->handle_timer_called_) << "i=" << i;
      EXPECT_FALSE(base_interpreter->sync_interpret_called_) << "i=" << i;
    }
    EXPECT_FLOAT_EQ(input.expected_local_deadline,
                    interpreter.fling_stop_deadline_) << "i=" << i;
    EXPECT_FLOAT_EQ(input.expected_next_deadline,
                    interpreter.next_timer_deadline_) << "i=" << i;
    EXPECT_FLOAT_EQ(input.expected_timeout, timeout) << "i=" << i;
    EXPECT_EQ(input.expected_fling_stop_out,
              ret && ret->type == kGestureTypeFling &&
              ret->details.fling.fling_state == GESTURES_FLING_TAP_DOWN)
        << "i=" << i;
  }
}

TEST(FlingStopFilterInterpreterTest, FlingGestureTest) {
  FlingStopFilterInterpreterTestInterpreter* base_interpreter =
      new FlingStopFilterInterpreterTestInterpreter;
  FlingStopFilterInterpreter interpreter(nullptr, base_interpreter, nullptr,
                                         GESTURES_DEVCLASS_TOUCHPAD);

  Gesture fling(kGestureFling, 0.0, 1.0, 0.0, 0.0, GESTURES_FLING_TAP_DOWN);
  Gesture swipelift(kGestureSwipeLift, 1.0, 2.0);
  Gesture swipe4flift(kGestureFourFingerSwipeLift, 1.0, 2.0);
  Gesture move(kGestureMove, 1.0, 2.0, 3.0, 4.0);

  interpreter.fling_stop_already_sent_ = true;
  interpreter.ConsumeGesture(fling);
  interpreter.ConsumeGesture(fling);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeFling);
  interpreter.ConsumeGesture(swipelift);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeSwipeLift);
  interpreter.ConsumeGesture(swipe4flift);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeFourFingerSwipeLift);

  interpreter.fling_stop_already_sent_ = false;
  interpreter.ConsumeGesture(fling);
  interpreter.ConsumeGesture(fling);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeFling);
  interpreter.ConsumeGesture(swipelift);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeSwipeLift);
  interpreter.ConsumeGesture(swipe4flift);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeFourFingerSwipeLift);

  interpreter.ConsumeGesture(move);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeMove);
}

TEST(FlingStopFilterInterpreterTest, FlingStopMultimouseMoveTest) {
  FlingStopFilterInterpreterTestInterpreter* base_interpreter =
      new FlingStopFilterInterpreterTestInterpreter;
  FlingStopFilterInterpreter interpreter(nullptr, base_interpreter, nullptr,
                                         GESTURES_DEVCLASS_MULTITOUCH_MOUSE);

  Gesture move(kGestureMove, 1.0, 2.0, 3.0, 4.0);
  interpreter.ConsumeGesture(move);
  EXPECT_EQ(interpreter.prev_gesture_type_, kGestureTypeMove);
}

}  // namespace gestures
