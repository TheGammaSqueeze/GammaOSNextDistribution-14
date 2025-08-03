// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <math.h>

#include <memory>
#include <gtest/gtest.h>  // for FRIEND_TEST

#include "include/filter_interpreter.h"
#include "include/gestures.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_ACCEL_FILTER_INTERPRETER_H_
#define GESTURES_ACCEL_FILTER_INTERPRETER_H_

namespace gestures {

// This interpreter provides pointer and scroll acceleration based on
// an acceleration curve and the user's sensitivity setting.
//
// For additional documentation, see ../docs/accel_filter_interpreter.md.

class AccelFilterInterpreter : public FilterInterpreter {
  FRIEND_TEST(AccelFilterInterpreterTest, CurveSegmentInitializerTest);
  FRIEND_TEST(AccelFilterInterpreterTest, CustomAccelTest);
  FRIEND_TEST(AccelFilterInterpreterTest, SimpleTest);
  FRIEND_TEST(AccelFilterInterpreterTest, TimingTest);
  FRIEND_TEST(AccelFilterInterpreterTest, NotSmoothingTest);
  FRIEND_TEST(AccelFilterInterpreterTest, SmoothingTest);
  FRIEND_TEST(AccelFilterInterpreterTest, TinyMoveTest);
  FRIEND_TEST(AccelFilterInterpreterTest, BadGestureTest);
  FRIEND_TEST(AccelFilterInterpreterTest, BadDeltaTTest);
  FRIEND_TEST(AccelFilterInterpreterTest, BadSpeedFlingTest);
  FRIEND_TEST(AccelFilterInterpreterTest, BadSpeedMoveTest);
  FRIEND_TEST(AccelFilterInterpreterTest, UnacceleratedMouseTest);
  FRIEND_TEST(AccelFilterInterpreterTest, UnacceleratedTouchpadTest);
  FRIEND_TEST(AccelFilterInterpreterTest, TouchpadPointAccelCurveTest);
  FRIEND_TEST(AccelFilterInterpreterTest, TouchpadScrollAccelCurveTest);
  FRIEND_TEST(AccelFilterInterpreterTest, AccelDebugDataTest);
 public:
  // Takes ownership of |next|:
  AccelFilterInterpreter(PropRegistry* prop_reg, Interpreter* next,
                         Tracer* tracer);
  virtual ~AccelFilterInterpreter() {}

  virtual void ConsumeGesture(const Gesture& gs);

 private:
  struct CurveSegment {
    CurveSegment() : x_(INFINITY), sqr_(0.0), mul_(1.0), int_(0.0) {}
    CurveSegment(float x, float s, float m, float b)
        : x_(x), sqr_(s), mul_(m), int_(b) {}
    CurveSegment(const CurveSegment& that)
        : x_(that.x_), sqr_(that.sqr_), mul_(that.mul_), int_(that.int_) {}
    // Be careful adding new members: We currently cast arrays of CurveSegment
    // to arrays of float (to expose to the properties system)
    double x_;  // Max X value of segment. User's point will be less than this.
    double sqr_;  // x^2 multiplier
    double mul_;  // Slope of line (x multiplier)
    double int_;  // Intercept of line
  };

  //**************************************************************************
  // Worker Funnctions that are used internal to this class as well
  // as giving internal information for testing/research purposes.

  // Calculate the Delta Time for a given gesture.
  //    in:     gs, provided Gesture
  //    ret:    delta time
  float get_dt(const Gesture& gs);

  // Calculate the Delta Time and adjust the value if the calculation gives
  // an out of bounds value.
  //    in:     gs, provided Gesture
  //    ret:    Reasonable Delta Time
  float get_adjusted_dt(const Gesture& gs);

  // The calculations in the ConsumeGestures is generic but works on
  // different fields based on what the gesture is.  This worker will
  // gather the correct values for the ConsumeGestures process.
  //    in:     gs, provided Gesture
  //    out:    dx, address of delta X
  //    out:    dy, address of delta Y
  //    out:    x_scale, value of X scaling factor
  //    out:    y_scale, value of Y scaling factor
  //    out:    scale_out_x, address of X value to adjust
  //    out:    scale_out_y, address of Y value to adjust
  //    out:    scale_out_x_ordinal, address of X orginal value to adjust
  //    out:    scale_out_y_ordinal, address of Y orginal value to adjust
  //    out:    segs, address of CurveSegment array to use
  //    out:    max_segs, number of array entries of segs
  //    ret:    true, acceleration expected
  //            false, acceleration not expected
  bool get_accel_parameters(
      Gesture& gs,
      float*& dx, float*& dy,
      float& x_scale, float& y_scale,
      float*& scale_out_x, float*& scale_out_y,
      float*& scale_out_x_ordinal, float*& scale_out_y_ordinal,
      CurveSegment*& segs, size_t& max_segs);

  // Given a dx/dy/dt (non-fling motion) or, if dx and dy are nullptr,
  // vx/vy (fling velocity) calculate the speed.
  //    in:     dx, address of delta X
  //    in:     dy, address of delta Y
  //    in:     vx, value of X velocity (only valid if dx/dy are nullptr)
  //    in:     vy, value of Y velocity (only valid if dx/dy are nullptr)
  //    in:     dt, value of delta time (assumed 1 if dx/dy are nullptr)
  //    out:    speed, actual distance/delta time
  //    ret:    true, acceleration expected
  //            false, acceleration not expected
  bool get_actual_speed(
      float* dx, float* dy,
      float vx, float vy,
      float dt,
      float& speed);

  // Speed smoothing, this is currently disabled but can be enabled by
  // means of the smooth_accel_ Property.
  //    in:     gs, provided Gesture
  //    inout:  speed, actual speed on input and smoothed on output
  void smooth_speed(const Gesture& gs, float& speed);

  // Map a speed on a given CurveSegment array to a ratio multiplier.
  //    in:     segs, address of CurveSegment array being used
  //    in:     max_segs, number of array entries in segs
  //    in:     speed, actual distance/delta time value
  //    ret:    determined gain to apply
  float RatioFromAccelCurve(const CurveSegment* segs,
                            const size_t max_segs,
                            const float speed);

  template<typename T>
  void LogDebugData(const T& debug_data) {
    using EventDebug = ActivityLog::EventDebug;
    if (EventDebugLoggingIsEnabled(EventDebug::Accel))
      log_->LogDebugData(debug_data);
  }

  //**************************************************************************

  static const size_t kMaxCurveSegs = 3;
  static const size_t kMaxCustomCurveSegs = 20;
  static const size_t kMaxUnaccelCurveSegs = 1;

  static const size_t kMaxAccelCurves = 5;

  // curves for sensitivity 1..5
  CurveSegment point_curves_[kMaxAccelCurves][kMaxCurveSegs];
  CurveSegment old_mouse_point_curves_[kMaxAccelCurves][kMaxCurveSegs];
  CurveSegment mouse_point_curves_[kMaxAccelCurves][kMaxCurveSegs];
  CurveSegment scroll_curves_[kMaxAccelCurves][kMaxCurveSegs];

  // curves when acceleration is disabled.
  CurveSegment unaccel_point_curves_[kMaxAccelCurves];
  CurveSegment unaccel_mouse_curves_[kMaxAccelCurves];
  // TODO(zentaro): Add unaccelerated scroll curves.

  // Custom curves
  CurveSegment tp_custom_point_[kMaxCustomCurveSegs];
  CurveSegment tp_custom_scroll_[kMaxCustomCurveSegs];
  CurveSegment mouse_custom_point_[kMaxCustomCurveSegs];
  // Note: there is no mouse_custom_scroll_ b/c mouse wheel accel is
  // handled in the MouseInterpreter class.

  // See max* and min_reasonable_dt_ properties
  stime_t last_reasonable_dt_ = 0.05;

  // These are used to calculate acceleration, see smooth_accel_
  stime_t last_end_time_ = -1.0;

  std::vector<float> last_mags_;
  static const size_t kMaxLastMagsSize = 2;

  // These properties expose the custom curves (just above) to the
  // property system.
  DoubleArrayProperty tp_custom_point_prop_;
  DoubleArrayProperty tp_custom_scroll_prop_;
  DoubleArrayProperty mouse_custom_point_prop_;

  // These properties enable use of custom curves (just above).
  BoolProperty use_custom_tp_point_curve_;
  BoolProperty use_custom_tp_scroll_curve_;
  BoolProperty use_custom_mouse_curve_;

  IntProperty pointer_sensitivity_;  // [1..5]
  IntProperty scroll_sensitivity_;  // [1..5]

  DoubleProperty point_x_out_scale_;
  DoubleProperty point_y_out_scale_;
  DoubleProperty scroll_x_out_scale_;
  DoubleProperty scroll_y_out_scale_;

  // These properties are automatically set on mice-like devices:
  BoolProperty use_mouse_point_curves_;  // set on {touch,nontouch} mice
  BoolProperty use_mouse_scroll_curves_;  // set on nontouch mice
  // If use_mouse_point_curves_ is true, this is consulted to see which
  // curves to use:
  BoolProperty use_old_mouse_point_curves_;
  // Flag for disabling mouse/touchpad acceleration.
  BoolProperty pointer_acceleration_;

  // Sometimes on wireless hardware (e.g. Bluetooth), packets need to be
  // resent. This can lead to a time between packets that very large followed
  // by a very small one. Very small periods especially cause problems b/c they
  // make the velocity seem very fast, which leads to an exaggeration of
  // movement.
  // To compensate, we have bounds on what we expect a reasonable period to be.
  // Events that have too large or small a period get reassigned the last
  // reasonable period.
  DoubleProperty min_reasonable_dt_;
  DoubleProperty max_reasonable_dt_;

  // If we enable smooth accel, the past few magnitudes are used to compute the
  // multiplication factor.
  BoolProperty smooth_accel_;
};

}  // namespace gestures

#endif  // GESTURES_SCALING_FILTER_INTERPRETER_H_
