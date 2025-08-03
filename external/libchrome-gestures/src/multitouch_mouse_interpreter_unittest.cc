// Copyright 2013 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>

#include "include/gestures.h"
#include "include/multitouch_mouse_interpreter.h"
#include "include/unittest_util.h"
#include "include/util.h"

namespace gestures {

class MultitouchMouseInterpreterTest : public ::testing::Test {};

TEST(MultitouchMouseInterpreterTest, SimpleTest) {
  MultitouchMouseInterpreter mi(nullptr, nullptr);
  Gesture* gs;

  HardwareProperties hwprops = {
    .left = 133, .top = 728, .right = 10279, .bottom = 5822,
    .res_x = (10279.0 - 133.0) / 100.0,
    .res_y = (5822.0 - 728.0) / 60,
    .screen_x_dpi = 0,
    .screen_y_dpi = 0,
    .orientation_minimum = -1,
    .orientation_maximum = 2,
    .max_finger_cnt = 2, .max_touch_cnt = 5,
    .supports_t5r2 = 0, .support_semi_mt = 0, .is_button_pad = 0,
    .has_wheel = 0, .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };
  TestInterpreterWrapper wrapper(&mi, &hwprops);

  FingerState fs_0[] = {
    { 1, 1, 0, 0, 0, 0, 0, 0, 1, 0 },
    { 1, 1, 0, 0, 0, 0, 0, 0, 2, 0 },
  };
  FingerState fs_1[] = {
    { 1, 1, 0, 0, 0, 0, 3, 4, 1, 0 },
    { 1, 1, 0, 0, 0, 0, 6, 8, 2, 0 },
  };
  HardwareState hwstates[] = {
    { 200000, 0, 2, 2, fs_0, 0, 0, 0, 0, 0, 0.0 },
    { 210000, 0, 2, 2, fs_0, 9, -7, 0, 0, 0, 0.0 },
    { 220000, 1, 2, 2, fs_0, 0, 0, 0, 0, 0, 0.0 },
    { 230000, 0, 2, 2, fs_0, 0, 0, 0, 0, 0, 0.0 },
    { 240000, 0, 2, 2, fs_1, 0, 0, 0, 0, 0, 0.0 },
  };

  // Make snap impossible
  mi.scroll_manager_.horizontal_scroll_snap_slope_.val_ = 0;
  mi.scroll_manager_.vertical_scroll_snap_slope_.val_ = 100;

  gs = wrapper.SyncInterpret(hwstates[0], nullptr);
  EXPECT_EQ(nullptr, gs);

  gs = wrapper.SyncInterpret(hwstates[1], nullptr);
  ASSERT_NE(nullptr, gs);
  EXPECT_EQ(kGestureTypeMove, gs->type);
  EXPECT_EQ(9, gs->details.move.dx);
  EXPECT_EQ(-7, gs->details.move.dy);
  EXPECT_EQ(200000, gs->start_time);
  EXPECT_EQ(210000, gs->end_time);

  gs = wrapper.SyncInterpret(hwstates[2], nullptr);
  ASSERT_NE(nullptr, gs);
  EXPECT_EQ(kGestureTypeButtonsChange, gs->type);
  EXPECT_EQ(1, gs->details.buttons.down);
  EXPECT_EQ(0, gs->details.buttons.up);
  EXPECT_GE(210000, gs->start_time);
  EXPECT_EQ(220000, gs->end_time);

  gs = wrapper.SyncInterpret(hwstates[3], nullptr);
  ASSERT_NE(nullptr, gs);
  EXPECT_EQ(kGestureTypeButtonsChange, gs->type);
  EXPECT_EQ(0, gs->details.buttons.down);
  EXPECT_EQ(1, gs->details.buttons.up);
  EXPECT_EQ(220000, gs->start_time);
  EXPECT_EQ(230000, gs->end_time);

  gs = wrapper.SyncInterpret(hwstates[4], nullptr);
  ASSERT_NE(nullptr, gs);
  EXPECT_EQ(kGestureTypeScroll, gs->type);
  EXPECT_EQ(6, gs->details.scroll.dx);
  EXPECT_EQ(8, gs->details.scroll.dy);
  EXPECT_EQ(230000, gs->start_time);
  EXPECT_EQ(240000, gs->end_time);
}

}  // namespace gestures
