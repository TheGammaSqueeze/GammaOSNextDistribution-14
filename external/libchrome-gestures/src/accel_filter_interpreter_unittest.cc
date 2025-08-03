// Copyright 2011 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <deque>
#include <math.h>
#include <utility>
#include <vector>

#include <gtest/gtest.h>

#include "include/accel_filter_interpreter.h"
#include "include/gestures.h"
#include "include/macros.h"
#include "include/unittest_util.h"
#include "include/util.h"

using std::deque;
using std::make_pair;
using std::pair;
using std::vector;

namespace gestures {

using EventDebug = ActivityLog::EventDebug;

class AccelFilterInterpreterTest : public ::testing::Test {
 protected:
  HardwareState empty_hwstate_ = {};
};

class AccelFilterInterpreterTestInterpreter : public Interpreter {
 public:
  AccelFilterInterpreterTestInterpreter()
      : Interpreter(nullptr, nullptr, false) {}

  virtual void SyncInterpret(HardwareState& hwstate, stime_t* timeout) {
    if (return_values_.empty())
      return;
    return_value_ = return_values_.front();
    return_values_.pop_front();
    if (return_value_.type == kGestureTypeNull)
      return;
    ProduceGesture(return_value_);
  }

  virtual void HandleTimer(stime_t now, stime_t* timeout) {
    FAIL() << "This interpreter doesn't use timers";
  }

  Gesture return_value_;
  deque<Gesture> return_values_;
};

TEST_F(AccelFilterInterpreterTest, SimpleTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;

  float last_move_dx = 0.0;
  float last_move_dy = 0.0;
  float last_scroll_dx = 0.0;
  float last_scroll_dy = 0.0;
  float last_fling_vx = 0.0;
  float last_fling_vy = 0.0;

  for (int i = 1; i <= 5; ++i) {
    accel_interpreter.pointer_sensitivity_.val_ = i;
    accel_interpreter.scroll_sensitivity_.val_ = i;

    base_interpreter->return_values_.push_back(Gesture());  // Null type
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       1.001,  // end time
                                                       -4,  // dx
                                                       2.8));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                       2,  // start time
                                                       2.1,  // end time
                                                       4.1,  // dx
                                                       -10.3));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureFling,
                                                       3,  // start time
                                                       3.1,  // end time
                                                       100.1,  // vx
                                                       -10.3,  // vy
                                                       0));  // state

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_EQ(nullptr, out);
    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out);
    EXPECT_EQ(kGestureTypeMove, out->type);
    if (i == 1) {
      // Expect no acceleration
      EXPECT_FLOAT_EQ(-4.0, out->details.move.dx) << "i = " << i;
      EXPECT_FLOAT_EQ(2.8, out->details.move.dy);
    } else {
      // Expect increasing acceleration
      EXPECT_GT(fabsf(out->details.move.dx), fabsf(last_move_dx));
      EXPECT_GT(fabsf(out->details.move.dy), fabsf(last_move_dy));
    }
    last_move_dx = out->details.move.dx;
    last_move_dy = out->details.move.dy;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out);
    EXPECT_EQ(kGestureTypeScroll, out->type);
    if (i == 1) {
      // Expect no acceleration
      EXPECT_FLOAT_EQ(4.1, out->details.scroll.dx);
      EXPECT_FLOAT_EQ(-10.3, out->details.scroll.dy);
    } else if (i > 2) {
      // Expect increasing acceleration
      EXPECT_GT(fabsf(out->details.scroll.dx), fabsf(last_scroll_dx));
      EXPECT_GT(fabsf(out->details.scroll.dy), fabsf(last_scroll_dy));
    }
    last_scroll_dx = out->details.scroll.dx;
    last_scroll_dy = out->details.scroll.dy;
    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out);
    EXPECT_EQ(kGestureTypeFling, out->type);
    if (i == 1) {
      // Expect no acceleration
      EXPECT_FLOAT_EQ(100.1, out->details.fling.vx);
      EXPECT_FLOAT_EQ(-10.3, out->details.fling.vy);
    } else if (i > 2) {
      // Expect increasing acceleration
      EXPECT_GT(fabsf(out->details.fling.vx), fabsf(last_fling_vx));
      EXPECT_GT(fabsf(out->details.fling.vy), fabsf(last_fling_vy));
    }
    last_fling_vx = out->details.fling.vx;
    last_fling_vy = out->details.fling.vy;
  }
}

TEST_F(AccelFilterInterpreterTest, TinyMoveTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);
  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;

  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     1,  // start time
                                                     2,  // end time
                                                     4,  // dx
                                                     0));  // dy
  base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                     2,  // start time
                                                     3,  // end time
                                                     4,  // dx
                                                     0));  // dy
  base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                     2,  // start time
                                                     3,  // end time
                                                     4,  // dx
                                                     0));  // dy

  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_GT(fabsf(out->details.move.dx), 2);
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeScroll, out->type);
  EXPECT_GT(fabsf(out->details.scroll.dx), 2);
  float orig_x_scroll = out->details.scroll.dx;
  accel_interpreter.scroll_x_out_scale_.val_ = 2.0;
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeScroll, out->type);
  EXPECT_FLOAT_EQ(orig_x_scroll * accel_interpreter.scroll_x_out_scale_.val_,
                  out->details.scroll.dx);
}

TEST_F(AccelFilterInterpreterTest, BadGestureTest) {
  PropRegistry prop_reg;
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(&prop_reg, base_interpreter,
                                           nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.SetEventLoggingEnabled(true);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Accel);
  accel_interpreter.log_.reset(new ActivityLog(&prop_reg));

  // AccelFilterInterpreter should not add gain to a ButtonsChange gesture.
  base_interpreter->return_values_.push_back(Gesture(kGestureButtonsChange,
                                                     1,  // start time
                                                     2,  // end time
                                                     0, // down
                                                     0, // up
                                                     false)); // is_tap

  // Send the ButtonsChange into AccelFilterInterpreter. The filter
  // should send it right back out.
  EXPECT_EQ(accel_interpreter.log_->size(), 0);
  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeButtonsChange, out->type);

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = accel_interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(accel_interpreter.log_->size(), 5);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareState));
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyAccelGestureDebug));
  node = tree[ActivityLog::kKeyRoot][3];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureProduce));
  node = tree[ActivityLog::kKeyRoot][4];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGesture));
  accel_interpreter.log_->Clear();
}

TEST_F(AccelFilterInterpreterTest, BadDeltaTTest) {
  PropRegistry prop_reg;
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(&prop_reg, base_interpreter,
                                           nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.SetEventLoggingEnabled(true);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Accel);
  accel_interpreter.log_.reset(new ActivityLog(&prop_reg));

  // Change the bounds for reasonable minimum Dt.  This will allow the filter
  // to keep a very small Dt without adjusting it.
  accel_interpreter.min_reasonable_dt_.val_ = 0;

  // Send the filter a very small Dt and have the logic catch that it
  // is too small.  This will not allow a fictitious Dt to be used but
  // will just not apply gain to this specific gesture.
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     1,  // start time
                                                     1.000001, // end time
                                                     4,  // dx
                                                     0));  // dy

  // Send the Move into AccelFilterInterpreter. No gain should be applied
  // to Dx, even though this small of a Dt would normally have added a lot
  // of gain.
  EXPECT_EQ(accel_interpreter.log_->size(), 0);
  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_EQ(fabsf(out->details.move.dx), 4);

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = accel_interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(accel_interpreter.log_->size(), 5);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareState));
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyAccelGestureDebug));
  node = tree[ActivityLog::kKeyRoot][3];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureProduce));
  node = tree[ActivityLog::kKeyRoot][4];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGesture));
  accel_interpreter.log_->Clear();
}

TEST_F(AccelFilterInterpreterTest, BadSpeedFlingTest) {
  PropRegistry prop_reg;
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(&prop_reg, base_interpreter,
                                           nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.SetEventLoggingEnabled(true);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Accel);
  accel_interpreter.log_.reset(new ActivityLog(&prop_reg));

  // Change the bounds for reasonable maximum Dt.  This will allow the filter
  // to keep a large Dt without adjusting it.
  accel_interpreter.max_reasonable_dt_.val_ = 1000;

  // Send the filter a Fling with a large Dt and have the logic catch that it
  // is too big.  This will not allow a fictitious Dt to be used but will
  // just not apply gain to this specific gesture.
  base_interpreter->return_values_.push_back(Gesture(kGestureFling,
                                                     1,  // start time
                                                     2,  // end time
                                                     0.000001,  // vx
                                                     0,   // vy
                                                     0)); // state

  // Send the Fling into AccelFilterInterpreter. No gain should be applied
  // to Vx.
  EXPECT_EQ(accel_interpreter.log_->size(), 0);
  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeFling, out->type);
  EXPECT_NEAR(fabsf(out->details.fling.vx), 0.000001, 0.0000001);

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = accel_interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(accel_interpreter.log_->size(), 5);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareState));
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyAccelGestureDebug));
  node = tree[ActivityLog::kKeyRoot][3];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureProduce));
  node = tree[ActivityLog::kKeyRoot][4];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGesture));
  accel_interpreter.log_->Clear();
}

TEST_F(AccelFilterInterpreterTest, BadSpeedMoveTest) {
  PropRegistry prop_reg;
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(&prop_reg, base_interpreter,
                                           nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.SetEventLoggingEnabled(true);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Accel);
  accel_interpreter.log_.reset(new ActivityLog(&prop_reg));

  // Change the bounds for reasonable maximum Dt.  This will allow the filter
  // to keep a large Dt without adjusting it.
  accel_interpreter.max_reasonable_dt_.val_ = 1000;

  // Send the filter a Move with a large Dt and have the logic catch that it
  // is too big.  This will not allow a fictitious Dt to be used but will
  // just not apply gain to this specific gesture.
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     1,  // start time
                                                     1000, // end time
                                                     0.0001,  // dx
                                                     0));  // dy

  // Send the Move into AccelFilterInterpreter. The gesture should be dropped.
  EXPECT_EQ(accel_interpreter.log_->size(), 0);
  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_EQ(nullptr, out);

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = accel_interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(accel_interpreter.log_->size(), 3);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareState));
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyAccelGestureDebug));
  accel_interpreter.log_->Clear();
}

TEST_F(AccelFilterInterpreterTest, TimingTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);
  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;
  accel_interpreter.min_reasonable_dt_.val_ = 0.0;
  accel_interpreter.max_reasonable_dt_.val_ = INFINITY;

  accel_interpreter.pointer_sensitivity_.val_ = 3;  // standard sensitivity
  accel_interpreter.scroll_sensitivity_.val_ = 3;  // standard sensitivity

  float last_dx = 0.0;
  float last_dy = 0.0;

  base_interpreter->return_values_.push_back(Gesture());  // Null type
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     1,  // start time
                                                     1.001,  // end time
                                                     -4,  // dx
                                                     2.8));  // dy
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     2,  // start time
                                                     3,  // end time
                                                     -4,  // dx
                                                     2.8));  // dy
  base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                     3,  // start time
                                                     3.001,  // end time
                                                     4.1,  // dx
                                                     -10.3));  // dy
  base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                     4,  // start time
                                                     5,  // end time
                                                     4.1,  // dx
                                                     -10.3));  // dy

  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_EQ(nullptr, out);
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  // Expect less accel for same movement over more time
  last_dx = out->details.move.dx;
  last_dy = out->details.move.dy;
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_GT(fabsf(last_dx), fabsf(out->details.move.dx));
  EXPECT_GT(fabsf(last_dy), fabsf(out->details.move.dy));


  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeScroll, out->type);
  // Expect less accel for same movement over more time
  last_dx = out->details.scroll.dx;
  last_dy = out->details.scroll.dy;
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeScroll, out->type);
  EXPECT_GT(fabsf(last_dx), fabsf(out->details.scroll.dx));
  EXPECT_GT(fabsf(last_dy), fabsf(out->details.scroll.dy));
}

TEST_F(AccelFilterInterpreterTest, NotSmoothingTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);
  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;
  accel_interpreter.min_reasonable_dt_.val_ = 0.0;
  accel_interpreter.max_reasonable_dt_.val_ = INFINITY;

  accel_interpreter.pointer_sensitivity_.val_ = 3;  // standard sensitivity
  accel_interpreter.scroll_sensitivity_.val_ = 3;  // standard sensitivity

  accel_interpreter.smooth_accel_.val_ = false;

  float last_dx = 0.0;
  float last_dy = 0.0;

  base_interpreter->return_values_.push_back(Gesture());  // Null type
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/1,
                                                     /*end=*/1.001,
                                                     /*dx=*/-4,
                                                     /*dy=*/2.8));
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/2,
                                                     /*end=*/3,
                                                     /*dx=*/-4,
                                                     /*dy=*/2.8));
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/3,
                                                     /*end=*/3.001,
                                                     /*dx=*/4.1,
                                                     /*dy=*/-10.3));
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/4,
                                                     /*end=*/5,
                                                     /*dx=*/4.1,
                                                     /*dy=*/-10.3));

  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_EQ(nullptr, out);
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  // Expect less accel for same movement over more time
  last_dx = out->details.move.dx;
  last_dy = out->details.move.dy;
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_GT(fabsf(last_dx), fabsf(out->details.move.dx));
  EXPECT_GT(fabsf(last_dy), fabsf(out->details.move.dy));

  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  // Expect less accel for same movement over more time
  last_dx = out->details.move.dx;
  last_dy = out->details.move.dy;
  ASSERT_GT(fabsf(last_dx), 32.5780);
  ASSERT_LT(fabsf(last_dx), 32.5782);
  ASSERT_GT(fabsf(last_dy), 81.8424);
  ASSERT_LT(fabsf(last_dy), 81.8426);

  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_GT(fabsf(last_dx), fabsf(out->details.move.dx));
  EXPECT_GT(fabsf(last_dy), fabsf(out->details.move.dy));
}

TEST_F(AccelFilterInterpreterTest, SmoothingTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);
  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;
  accel_interpreter.min_reasonable_dt_.val_ = 0.0;
  accel_interpreter.max_reasonable_dt_.val_ = INFINITY;

  accel_interpreter.pointer_sensitivity_.val_ = 3;  // standard sensitivity
  accel_interpreter.scroll_sensitivity_.val_ = 3;  // standard sensitivity

  accel_interpreter.smooth_accel_.val_ = true;

  float last_dx = 0.0;
  float last_dy = 0.0;

  base_interpreter->return_values_.push_back(Gesture());  // Null type
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/1,
                                                     /*end=*/1.001,
                                                     /*dx=*/-4,
                                                     /*dy=*/2.8));
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/2,
                                                     /*end=*/3,
                                                     /*dx=*/-4,
                                                     /*dy=*/2.8));
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/3,
                                                     /*end=*/3.001,
                                                     /*dx=*/4.1,
                                                     /*dy=*/-10.3));
  base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                     /*start=*/4,
                                                     /*end=*/5,
                                                     /*dx=*/4.1,
                                                     /*dy=*/-10.3));

  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_EQ(nullptr, out);
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  // Expect less accel for same movement over more time
  last_dx = out->details.move.dx;
  last_dy = out->details.move.dy;
  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_GT(fabsf(last_dx), fabsf(out->details.move.dx));
  EXPECT_GT(fabsf(last_dy), fabsf(out->details.move.dy));

  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  // Expect less accel for same movement over more time
  last_dx = out->details.move.dx;
  last_dy = out->details.move.dy;
  ASSERT_GT(fabsf(last_dx), 32.3563);
  ASSERT_LT(fabsf(last_dx), 32.3565);
  ASSERT_GT(fabsf(last_dy), 81.2855);
  ASSERT_LT(fabsf(last_dy), 81.2857);

  out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeMove, out->type);
  EXPECT_GT(fabsf(last_dx), fabsf(out->details.move.dx));
  EXPECT_GT(fabsf(last_dy), fabsf(out->details.move.dy));
}

TEST_F(AccelFilterInterpreterTest, CurveSegmentInitializerTest) {
  AccelFilterInterpreter::CurveSegment temp1 =
      AccelFilterInterpreter::CurveSegment(INFINITY, 0.0, 2.0, -2.0);
  AccelFilterInterpreter::CurveSegment temp2 =
      AccelFilterInterpreter::CurveSegment(temp1);

  ASSERT_EQ(temp1.x_, temp2.x_);

  temp1 = AccelFilterInterpreter::CurveSegment(0.0, 0.0, 0.0, 0.0);
  ASSERT_NE(temp1.x_, temp2.x_);
}

TEST_F(AccelFilterInterpreterTest, CustomAccelTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);
  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;
  accel_interpreter.min_reasonable_dt_.val_ = 0.0;
  accel_interpreter.max_reasonable_dt_.val_ = INFINITY;

  // custom sensitivity
  accel_interpreter.use_custom_tp_point_curve_.val_ = 1;
  accel_interpreter.use_custom_tp_scroll_curve_.val_ = 1;
  accel_interpreter.tp_custom_point_[0] =
      AccelFilterInterpreter::CurveSegment(2.0, 0.0, 0.5, 0.0);
  accel_interpreter.tp_custom_point_[1] =
      AccelFilterInterpreter::CurveSegment(3.0, 0.0, 2.0, -3.0);
  accel_interpreter.tp_custom_point_[2] =
      AccelFilterInterpreter::CurveSegment(INFINITY, 0.0, 0.0, 3.0);
  accel_interpreter.tp_custom_scroll_[0] =
      AccelFilterInterpreter::CurveSegment(0.5, 0.0, 2.0, 0.0);
  accel_interpreter.tp_custom_scroll_[1] =
      AccelFilterInterpreter::CurveSegment(1.0, 0.0, 2.0, 0.0);
  accel_interpreter.tp_custom_scroll_[2] =
      AccelFilterInterpreter::CurveSegment(2.0, 0.0, 0.0, 2.0);
  accel_interpreter.tp_custom_scroll_[3] =
      AccelFilterInterpreter::CurveSegment(INFINITY, 0.0, 2.0, -2.0);

  float move_in[]  = { 1.0, 2.5, 3.5, 5.0 };
  float move_out[] = { 0.5, 2.0, 3.0, 3.0 };

  for (size_t i = 0; i < arraysize(move_in); ++i) {
    float dist = move_in[i];
    float expected = move_out[i];
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       2,  // end time
                                                       dist,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       2,  // end time
                                                       0,  // dx
                                                       dist));  // dy
    // half time, half distance = same speed
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       dist / 2.0,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       0,  // dx
                                                       dist / 2.0));  // dy

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeMove, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeMove, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeMove, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeMove, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.move.dy) << "i=" << i;
  }

  float swipe_in[]  = { 1.0, 2.5, 3.5, 5.0 };
  float swipe_out[] = { 0.5, 2.0, 3.0, 3.0 };

  for (size_t i = 0; i < arraysize(swipe_in); ++i) {
    float dist = swipe_in[i];
    float expected = swipe_out[i];
    base_interpreter->return_values_.push_back(Gesture(kGestureSwipe,
                                                       1,  // start time
                                                       2,  // end time
                                                       dist,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureSwipe,
                                                       1,  // start time
                                                       2,  // end time
                                                       0,  // dx
                                                       dist));  // dy
    // half time, half distance = same speed
    base_interpreter->return_values_.push_back(Gesture(kGestureSwipe,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       dist / 2.0,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureSwipe,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       0,  // dx
                                                       dist / 2.0));  // dy

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.move.dy) << "i=" << i;
  }

  float swipe4_in[]  = { 1.0, 2.5, 3.5, 5.0 };
  float swipe4_out[] = { 0.5, 2.0, 3.0, 3.0 };

  for (size_t i = 0; i < arraysize(swipe4_in); ++i) {
    float dist = swipe4_in[i];
    float expected = swipe4_out[i];
    base_interpreter->return_values_.push_back(Gesture(kGestureFourFingerSwipe,
                                                       1,  // start time
                                                       2,  // end time
                                                       dist,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureFourFingerSwipe,
                                                       1,  // start time
                                                       2,  // end time
                                                       0,  // dx
                                                       dist));  // dy
    // half time, half distance = same speed
    base_interpreter->return_values_.push_back(Gesture(kGestureFourFingerSwipe,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       dist / 2.0,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureFourFingerSwipe,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       0,  // dx
                                                       dist / 2.0));  // dy

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeFourFingerSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeFourFingerSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeFourFingerSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeFourFingerSwipe, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.move.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.move.dy) << "i=" << i;
  }

  float scroll_in[]  = { 0.25, 0.5, 0.75, 1.5, 2.5, 3.0, 3.5 };
  float scroll_out[] = { 0.5,  1.0, 1.5,  2.0, 3.0, 4.0, 5.0 };

  for (size_t i = 0; i < arraysize(scroll_in); ++i) {
    float dist = scroll_in[i];
    float expected = scroll_out[i];
    base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                       1,  // start time
                                                       2,  // end time
                                                       dist,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                       1,  // start time
                                                       2,  // end time
                                                       0,  // dx
                                                       dist));  // dy
    // half time, half distance = same speed
    base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       dist / 2.0,  // dx
                                                       0));  // dy
    base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                       1,  // start time
                                                       1.5,  // end time
                                                       0,  // dx
                                                       dist / 2.0));  // dy

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeScroll, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.scroll.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.scroll.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeScroll, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.scroll.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected, out->details.scroll.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeScroll, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.scroll.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.scroll.dy) << "i=" << i;

    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out) << "i=" << i;
    EXPECT_EQ(kGestureTypeScroll, out->type) << "i=" << i;
    EXPECT_FLOAT_EQ(0, out->details.scroll.dx) << "i=" << i;
    EXPECT_FLOAT_EQ(expected / 2.0, out->details.scroll.dy) << "i=" << i;
  }
}

TEST_F(AccelFilterInterpreterTest, UnacceleratedMouseTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.use_mouse_point_curves_.val_ = true;
  accel_interpreter.pointer_acceleration_.val_ = false;

  const float dx = 3;
  const float dy = 5;
  const float unaccel_slopes[] = { 2.0, 4.0, 8.0, 16.0, 24.0 };

  for (int i = 1; i <= 5; ++i) {
    accel_interpreter.pointer_sensitivity_.val_ = i;

    base_interpreter->return_values_.push_back(Gesture());  // Null type
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       1.001,  // end time
                                                       dx,  // dx
                                                       dy));  // dy

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_EQ(nullptr, out);
    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out);
    EXPECT_EQ(kGestureTypeMove, out->type);

    // Output should be scaled by a constant value.
    EXPECT_FLOAT_EQ(dx * unaccel_slopes[i - 1], out->details.move.dx);
    EXPECT_FLOAT_EQ(dy * unaccel_slopes[i - 1], out->details.move.dy);
  }
}

TEST_F(AccelFilterInterpreterTest, UnacceleratedTouchpadTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.use_mouse_point_curves_.val_ = false;
  accel_interpreter.pointer_acceleration_.val_ = false;

  const float dx = 3;
  const float dy = 5;
  const float unaccel_slopes[] = { 1.0, 2.0, 3.0, 4.0, 5.0 };

  for (int i = 1; i <= 5; ++i) {
    accel_interpreter.pointer_sensitivity_.val_ = i;

    base_interpreter->return_values_.push_back(Gesture());  // Null type
    base_interpreter->return_values_.push_back(Gesture(kGestureMove,
                                                       1,  // start time
                                                       1.001,  // end time
                                                       dx,  // dx
                                                       dy));  // dy

    Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_EQ(nullptr, out);
    out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
    ASSERT_NE(nullptr, out);
    EXPECT_EQ(kGestureTypeMove, out->type);

    // Output should be scaled by a constant value.
    EXPECT_FLOAT_EQ(dx * unaccel_slopes[i - 1], out->details.move.dx);
    EXPECT_FLOAT_EQ(dy * unaccel_slopes[i - 1], out->details.move.dy);
  }
}

TEST_F(AccelFilterInterpreterTest, TouchpadPointAccelCurveTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  size_t num_segs = AccelFilterInterpreter::kMaxCurveSegs;
  AccelFilterInterpreter::CurveSegment* segs;

  // x = input speed of movement (mm/s, always >= 0), y = output speed (mm/s)
  // Sensitivity: 1 No Acceleration
  segs = accel_interpreter.point_curves_[0];

  float ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, 0);
  ASSERT_EQ(ratio, 0.0);

  ASSERT_EQ(segs[0].x_, INFINITY);
  ASSERT_EQ(segs[0].sqr_, 0.0);
  ASSERT_EQ(segs[0].mul_, 1.0);
  ASSERT_EQ(segs[0].int_, 0.0);
  for (int x = 1; x < 1000; ++x) {
    ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
    float y = ratio * float(x);
    ASSERT_EQ(x, y);
  }

  // Sensitivity 2-5
  const float point_divisors[] = {0.0, // unused
                                  60.0, 37.5, 30.0, 25.0 };  // used

  for (int sensitivity = 2; sensitivity <= 5; ++sensitivity) {
    segs = accel_interpreter.point_curves_[sensitivity - 1];
    const float divisor = point_divisors[sensitivity - 1];

    ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, 0.0);
    ASSERT_EQ(ratio, 0.0);

    ratio = accel_interpreter.RatioFromAccelCurve(segs, 1, INFINITY);
    ASSERT_EQ(ratio, 0.0);

    //    y = 32x/divisor   (x < 32)
    const float linear_until_x = 32.0;
    ASSERT_EQ(segs[0].x_, linear_until_x);
    ASSERT_EQ(segs[0].sqr_, 0.0);
    ASSERT_EQ(segs[0].mul_, linear_until_x / divisor);
    ASSERT_EQ(segs[0].int_, 0.0);
    for (int i = 1; i < 32; ++i) {
      float x = float(i);
      ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
      float y = x * ratio;
      float expected = (linear_until_x * x) / divisor;
      ASSERT_LE(expected - 0.001, y);
      ASSERT_GE(expected + 0.001, y);
    }

    //    y = x^2/divisor   (x < 150)
    const float x_border = 150.0;
    ASSERT_EQ(segs[1].x_, x_border);
    ASSERT_EQ(segs[1].sqr_, 1 / divisor);
    ASSERT_EQ(segs[1].mul_, 0.0);
    ASSERT_EQ(segs[1].int_, 0.0);
    for (int i = 33; i < 150; ++i) {
      float x = float(i);
      ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
      float y = x * ratio;
      float expected = (x * x) / divisor;
      ASSERT_LE(expected - 0.001, y);
      ASSERT_GE(expected + 0.001, y);
    }

    // linear with same slope after
    const float slope = (x_border * 2) / divisor;
    const float y_at_border = (x_border * x_border) / divisor;
    const float intercept = y_at_border - (slope * x_border);
    ASSERT_EQ(segs[2].x_, INFINITY);
    ASSERT_EQ(segs[2].sqr_, 0.0);
    ASSERT_EQ(segs[2].mul_, slope);
    ASSERT_EQ(segs[2].int_, intercept);
    for (int i = 150; i < 1000; ++i) {
      float x = float(i);
          // return seg.mul_ + seg.int_ / speed;;
      ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
      float y = x * ratio;
      float expected = x * (slope + (intercept / x));
      ASSERT_LE(expected - 0.001, y);
      ASSERT_GE(expected + 0.001, y);
    }
  }
}

TEST_F(AccelFilterInterpreterTest, TouchpadScrollAccelCurveTest) {
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(nullptr, base_interpreter, nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  size_t num_segs = AccelFilterInterpreter::kMaxCurveSegs;
  AccelFilterInterpreter::CurveSegment* segs;

  // x = input speed of movement (mm/s, always >= 0), y = output speed (mm/s)
  // Sensitivity: 1 No Acceleration
  segs = accel_interpreter.scroll_curves_[0];

  float ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, 0);
  ASSERT_EQ(ratio, 0.0);

  ASSERT_EQ(segs[0].x_, INFINITY);
  ASSERT_EQ(segs[0].sqr_, 0.0);
  ASSERT_EQ(segs[0].mul_, 1.0);
  ASSERT_EQ(segs[0].int_, 0.0);
  for (int x = 1; x < 1000; ++x) {
    ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
    float y = ratio * float(x);
    ASSERT_EQ(x, y);
  }

  // Sensitivity 2-5
  const float scroll_divisors[] = {0.0, // unused
                                   150, 75.0, 70.0, 65.0 };  // used

  for (int sensitivity = 2; sensitivity <= 5; ++sensitivity) {
    segs = accel_interpreter.scroll_curves_[sensitivity - 1];
    const float divisor = scroll_divisors[sensitivity - 1];

    ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, 0.0);
    ASSERT_EQ(ratio, 0.0);

    ratio = accel_interpreter.RatioFromAccelCurve(segs, 1, INFINITY);
    ASSERT_EQ(ratio, 0.0);

    //    y = 75x/divisor   (x < 75)
    const float linear_until_x = 75.0;
    ASSERT_EQ(segs[0].x_, linear_until_x);
    ASSERT_EQ(segs[0].sqr_, 0.0);
    ASSERT_EQ(segs[0].mul_, linear_until_x / divisor);
    ASSERT_EQ(segs[0].int_, 0.0);
    for (int i = 1; i < 75; ++i) {
      float x = float(i);
      ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
      float y = x * ratio;
      float expected = (linear_until_x * x) / divisor;
      ASSERT_LE(expected - 0.001, y);
      ASSERT_GE(expected + 0.001, y);
    }

    //    y = x^2/divisor   (x < 600)
    const float x_border = 600.0;
    ASSERT_EQ(segs[1].x_, x_border);
    ASSERT_EQ(segs[1].sqr_, 1 / divisor);
    ASSERT_EQ(segs[1].mul_, 0.0);
    ASSERT_EQ(segs[1].int_, 0.0);
    for (int i = 75; i < 600; ++i) {
      float x = float(i);
      ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
      float y = x * ratio;
      float expected = (x * x) / divisor;
      ASSERT_LE(expected - 0.001, y);
      ASSERT_GE(expected + 0.001, y);
    }

    // linear with same slope after
    const float slope = linear_until_x / divisor;
    const float y_at_border = (x_border * x_border) / divisor;
    const float intercept = y_at_border - (slope * x_border);
    ASSERT_EQ(segs[2].x_, INFINITY);
    ASSERT_EQ(segs[2].sqr_, 0.0);
    ASSERT_EQ(segs[2].mul_, slope);
    ASSERT_EQ(segs[2].int_, intercept);
    for (int i = 600; i < 1000; ++i) {
      float x = float(i);
      ratio = accel_interpreter.RatioFromAccelCurve(segs, num_segs, x);
      float y = x * ratio;
      float expected = x * (slope + (intercept / x));
      ASSERT_LE(expected - 0.001, y);
      ASSERT_GE(expected + 0.001, y);
    }
  }
}

TEST_F(AccelFilterInterpreterTest, AccelDebugDataTest) {
  PropRegistry prop_reg;
  AccelFilterInterpreterTestInterpreter* base_interpreter =
      new AccelFilterInterpreterTestInterpreter;
  AccelFilterInterpreter accel_interpreter(&prop_reg, base_interpreter,
                                           nullptr);
  TestInterpreterWrapper interpreter(&accel_interpreter);

  accel_interpreter.SetEventLoggingEnabled(true);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Gesture);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::HardwareState);
  accel_interpreter.EventDebugLoggingEnable(EventDebug::Accel);
  accel_interpreter.log_.reset(new ActivityLog(&prop_reg));

  accel_interpreter.scroll_x_out_scale_.val_ =
      accel_interpreter.scroll_y_out_scale_.val_ = 1.0;

  base_interpreter->return_values_.push_back(Gesture(kGestureScroll,
                                                     2,  // start time
                                                     2.1,  // end time
                                                     4.1,  // dx
                                                     -10.3));  // dy

  Gesture* out = interpreter.SyncInterpret(empty_hwstate_, nullptr);
  ASSERT_NE(nullptr, out);
  EXPECT_EQ(kGestureTypeScroll, out->type);

  // Encode the log into Json
  Json::Value node;
  Json::Value tree = accel_interpreter.log_->EncodeCommonInfo();

  // Verify the Json information
  EXPECT_EQ(accel_interpreter.log_->size(), 5);
  node = tree[ActivityLog::kKeyRoot][0];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareState));
  node = tree[ActivityLog::kKeyRoot][1];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  node = tree[ActivityLog::kKeyRoot][2];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyAccelGestureDebug));
  node = tree[ActivityLog::kKeyRoot][3];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureProduce));
  node = tree[ActivityLog::kKeyRoot][4];
  EXPECT_EQ(node[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGesture));
  accel_interpreter.log_->Clear();
}

}  // namespace gestures
