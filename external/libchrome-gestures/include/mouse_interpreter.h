// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>  // For FRIEND_TEST

#include "include/gestures.h"
#include "include/interpreter.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_MOUSE_INTERPRETER_H_
#define GESTURES_MOUSE_INTERPRETER_H_

namespace gestures {

class MouseInterpreter : public Interpreter, public PropertyDelegate {
  FRIEND_TEST(MouseInterpreterTest, SimpleTest);
  FRIEND_TEST(MouseInterpreterTest, HighResolutionVerticalScrollTest);
  FRIEND_TEST(MouseInterpreterTest, ScrollAccelerationOnAndOffTest);
  FRIEND_TEST(MouseInterpreterTest, JankyScrollTest);
  FRIEND_TEST(MouseInterpreterTest, WheelTickReportingHighResTest);
  FRIEND_TEST(MouseInterpreterTest, WheelTickReportingLowResTest);
  FRIEND_TEST(MouseInterpreterTest, EmulateScrollWheelTest);
 public:
  MouseInterpreter(PropRegistry* prop_reg, Tracer* tracer);
  virtual ~MouseInterpreter() {};

 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate, stime_t* timeout);
  // These functions interpret mouse events, which include button clicking and
  // mouse movement. This function needs two consecutive HardwareState. If no
  // mouse events are presented, result object is not modified. Scroll wheel
  // events are not interpreted as they are handled differently for normal
  // mice and multi-touch mice (ignored for multi-touch mice and accelerated
  // for normal mice).
  void InterpretMouseButtonEvent(const HardwareState& prev_state,
                                 const HardwareState& hwstate);

  void InterpretMouseMotionEvent(const HardwareState& prev_state,
                                 const HardwareState& hwstate);
  // Check for scroll wheel events and produce scroll gestures.
  void InterpretScrollWheelEvent(const HardwareState& hwstate,
                                 bool is_vertical);
  bool EmulateScrollWheel(const HardwareState& hwstate);
 private:
  struct WheelRecord {
    WheelRecord(float v, stime_t t): value(v), timestamp(t) {}
    WheelRecord(): value(0), timestamp(0) {}
    float value;
    stime_t timestamp;
  };

  // Accelerate mouse scroll offsets so that it is larger when the user scroll
  // the mouse wheel faster.
  double ComputeScrollAccelFactor(double input_speed);

  Gesture CreateWheelGesture(stime_t start, stime_t end, float dx, float dy,
                             int tick_120ths_dx, int tick_120ths_dy);

  HardwareState prev_state_;

  // Records last scroll wheel event.
  WheelRecord last_wheel_, last_hwheel_;

  // Accumulators to measure scroll distance while doing scroll wheel emulation
  double wheel_emulation_accu_x_;
  double wheel_emulation_accu_y_;

  // True while wheel emulation is locked in.
  bool wheel_emulation_active_;

  // f_approximated = a0 + a1*v + a2*v^2 + a3*v^3 + a4*v^4
  double scroll_accel_curve_[5];

  // Reverse wheel scrolling.
  BoolProperty reverse_scrolling_;

  // Mouse scroll acceleration.
  BoolProperty scroll_acceleration_;

  // Mouse scroll sensitivity 1..5.
  IntProperty scroll_sensitivity_;

  // Enable high-resolution scrolling.
  BoolProperty hi_res_scrolling_;

  // We use normal CDF to simulate scroll wheel acceleration curve. Use the
  // following method to generate the coefficients of a degree-4 polynomial
  // regression for a specific normal cdf in Python.
  //
  // Note: x for wheel value, v for velocity, y for scroll pixels (offset),
  // and v = x / dt.
  //
  // The offset is computed as x * f(v) where f() outputs the acceleration
  // factor for the given input speed. The formula allows us to produce similar
  // offsets regardless of the mouse scrolling resolution. Since we want y to
  // follow the normal CDF, we need to attenuate the case where x >= 1. This can
  // happen when the user scrolls really fast, e.g., more than 1 unit within 8ms
  // for a common, low-resolution mouse.
  //
  // In reality, v ranges from 1 to 120+ for an Apple Mighty Mouse, use range
  // greater than that to minimize approximation error at the end points.
  // In our case, the range is [-50, 200].
  //
  // Python (3) code:
  // import numpy as np
  // from scipy.stats import norm
  // v = np.arange(-50, 201)
  // f = (580 * norm.cdf(v, 100, 40) + 20) / np.maximum(v / 125.0, 1)
  // coeff = np.flip(np.polyfit(v, f, 4), 0)
  // Adjust the scroll acceleration curve
  DoubleArrayProperty scroll_accel_curve_prop_;

  // when x is 177, the polynomial curve gives 450, the max pixels to scroll.
  DoubleProperty scroll_max_allowed_input_speed_;

  // Force scroll wheel emulation for any devices
  BoolProperty force_scroll_wheel_emulation_;

  // Multiplication factor to translate cursor motion into scrolling
  DoubleProperty scroll_wheel_emulation_speed_;

  // Movement distance after which to start scroll wheel emulation [in mm]
  DoubleProperty scroll_wheel_emulation_thresh_;

  // Whether to output GestureMouseWheel or GestureScroll structs from scrolls.
  // TODO(chromium:1077644): remove once Chrome is migrated to the new structs.
  BoolProperty output_mouse_wheel_gestures_;
};

}  // namespace gestures

#endif  // GESTURES_MOUSE_INTERPRETER_H_
