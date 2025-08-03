// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/accel_filter_interpreter.h"

#include <algorithm>
#include <math.h>
#include <numeric>

#include "include/gestures.h"
#include "include/interpreter.h"
#include "include/logging.h"
#include "include/macros.h"
#include "include/tracer.h"

namespace gestures {

// Takes ownership of |next|:
AccelFilterInterpreter::AccelFilterInterpreter(PropRegistry* prop_reg,
                                               Interpreter* next,
                                               Tracer* tracer)
    : FilterInterpreter(nullptr, next, tracer, false),
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wsizeof-array-div"
      // Hack: cast tp_custom_point_/mouse_custom_point_/tp_custom_scroll_
      // to float arrays.
      tp_custom_point_prop_(prop_reg, "Pointer Accel Curve",
                            reinterpret_cast<double*>(&tp_custom_point_),
                            sizeof(tp_custom_point_) / sizeof(double)),
      tp_custom_scroll_prop_(prop_reg, "Scroll Accel Curve",
                             reinterpret_cast<double*>(&tp_custom_scroll_),
                             sizeof(tp_custom_scroll_) / sizeof(double)),
      mouse_custom_point_prop_(prop_reg, "Mouse Pointer Accel Curve",
                               reinterpret_cast<double*>(&mouse_custom_point_),
                               sizeof(mouse_custom_point_) / sizeof(double)),
#pragma GCC diagnostic pop
      use_custom_tp_point_curve_(
          prop_reg, "Use Custom Touchpad Pointer Accel Curve", false),
      use_custom_tp_scroll_curve_(
          prop_reg, "Use Custom Touchpad Scroll Accel Curve", false),
      use_custom_mouse_curve_(
          prop_reg, "Use Custom Mouse Pointer Accel Curve", false),
      pointer_sensitivity_(prop_reg, "Pointer Sensitivity", 3),
      scroll_sensitivity_(prop_reg, "Scroll Sensitivity", 3),
      point_x_out_scale_(prop_reg, "Point X Out Scale", 1.0),
      point_y_out_scale_(prop_reg, "Point Y Out Scale", 1.0),
      scroll_x_out_scale_(prop_reg, "Scroll X Out Scale", 2.5),
      scroll_y_out_scale_(prop_reg, "Scroll Y Out Scale", 2.5),
      use_mouse_point_curves_(prop_reg, "Mouse Accel Curves", false),
      use_mouse_scroll_curves_(prop_reg, "Mouse Scroll Curves", false),
      use_old_mouse_point_curves_(prop_reg, "Old Mouse Accel Curves", false),
      pointer_acceleration_(prop_reg, "Pointer Acceleration", true),
      min_reasonable_dt_(prop_reg, "Accel Min dt", 0.003),
      max_reasonable_dt_(prop_reg, "Accel Max dt", 0.050),
      smooth_accel_(prop_reg, "Smooth Accel", false) {
  InitName();
  // Set up default curves.

  // Our pointing curves are the following.
  // x = input speed of movement (mm/s, always >= 0), y = output speed (mm/s)
  // 1: y = x (No acceleration)
  // 2: y = 32x/60   (x < 32), x^2/60   (x < 150), linear with same slope after
  // 3: y = 32x/37.5 (x < 32), x^2/37.5 (x < 150), linear with same slope after
  // 4: y = 32x/30   (x < 32), x^2/30   (x < 150), linear with same slope after
  // 5: y = 32x/25   (x < 32), x^2/25   (x < 150), linear with same slope after

  const float point_divisors[] = { 0.0, // unused
                                   60.0, 37.5, 30.0, 25.0 };  // used


  // i starts as 1 b/c we skip the first slot, since the default is fine for it.
  for (size_t i = 1; i < kMaxAccelCurves; ++i) {
    const float divisor = point_divisors[i];
    const float linear_until_x = 32.0;
    const float init_slope = linear_until_x / divisor;
    point_curves_[i][0] = CurveSegment(linear_until_x, 0, init_slope, 0);
    const float x_border = 150;
    point_curves_[i][1] = CurveSegment(x_border, 1 / divisor, 0, 0);
    const float slope = x_border * 2 / divisor;
    const float y_at_border = x_border * x_border / divisor;
    const float icept = y_at_border - slope * x_border;
    point_curves_[i][2] = CurveSegment(INFINITY, 0, slope, icept);
  }

  // Setup unaccelerated touchpad curves. Each one is just a single linear
  // segment with the slope from |unaccel_tp_slopes|.
  const float unaccel_tp_slopes[] = { 1.0, 2.0, 3.0, 4.0, 5.0 };
  for (size_t i = 0; i < kMaxAccelCurves; ++i) {
    unaccel_point_curves_[i] = CurveSegment(
        INFINITY, 0, unaccel_tp_slopes[i], 0);
  }

  const float old_mouse_speed_straight_cutoff[] = { 5.0, 5.0, 5.0, 8.0, 8.0 };
  const float old_mouse_speed_accel[] = { 1.0, 1.4, 1.8, 2.0, 2.2 };

  for (size_t i = 0; i < kMaxAccelCurves; ++i) {
    const float kParabolaA = 1.3;
    const float kParabolaB = 0.2;
    const float cutoff_x = old_mouse_speed_straight_cutoff[i];
    const float cutoff_y =
        kParabolaA * cutoff_x * cutoff_x + kParabolaB * cutoff_x;
    const float line_m = 2.0 * kParabolaA * cutoff_x + kParabolaB;
    const float line_b = cutoff_y - cutoff_x * line_m;
    const float kOutMult = old_mouse_speed_accel[i];

    old_mouse_point_curves_[i][0] =
        CurveSegment(cutoff_x * 25.4, kParabolaA * kOutMult / 25.4,
                     kParabolaB * kOutMult, 0.0);
    old_mouse_point_curves_[i][1] = CurveSegment(INFINITY, 0.0, line_m * kOutMult,
                                             line_b * kOutMult * 25.4);
  }

  // These values were determined empirically through user studies:
  const float kMouseMultiplierA = 0.0311;
  const float kMouseMultiplierB = 3.26;
  const float kMouseCutoff = 195.0;
  const float kMultipliers[] = { 1.2, 1.4, 1.6, 1.8, 2.0 };
  for (size_t i = 0; i < kMaxAccelCurves; ++i) {
    float mouse_a = kMouseMultiplierA * kMultipliers[i] * kMultipliers[i];
    float mouse_b = kMouseMultiplierB * kMultipliers[i];
    float cutoff = kMouseCutoff / kMultipliers[i];
    float second_slope =
        (2.0 * kMouseMultiplierA * kMouseCutoff + kMouseMultiplierB) *
        kMultipliers[i];
    mouse_point_curves_[i][0] = CurveSegment(cutoff, mouse_a, mouse_b, 0.0);
    mouse_point_curves_[i][1] = CurveSegment(INFINITY, 0.0, second_slope, -1182);
  }

  // Setup unaccelerated mouse curves. Each one is just a single linear
  // segment with the slope from |unaccel_mouse_slopes|.
  const float unaccel_mouse_slopes[] = { 2.0, 4.0, 8.0, 16.0, 24.0 };
  for (size_t i = 0; i < kMaxAccelCurves; ++i) {
    unaccel_mouse_curves_[i] = CurveSegment(
        INFINITY, 0, unaccel_mouse_slopes[i], 0);
  }

  const float scroll_divisors[] = { 0.0, // unused
                                    150, 75.0, 70.0, 65.0 };  // used
  // Our scrolling curves are the following.
  // x = input speed of movement (mm/s, always >= 0), y = output speed (mm/s)
  // 1: y = x (No acceleration)
  // 2: y = 75x/150   (x < 75), x^2/150   (x < 600), linear (initial slope).
  // 3: y = 75x/75    (x < 75), x^2/75    (x < 600), linear (initial slope).
  // 4: y = 75x/70    (x < 75), x^2/70    (x < 600), linear (initial slope).
  // 5: y = 75x/65    (x < 75), x^2/65    (x < 600), linear (initial slope).
  // i starts as 1 b/c we skip the first slot, since the default is fine for it.
  for (size_t i = 1; i < kMaxAccelCurves; ++i) {
    const float divisor = scroll_divisors[i];
    const float linear_until_x = 75.0;
    const float init_slope = linear_until_x / divisor;
    scroll_curves_[i][0] = CurveSegment(linear_until_x, 0, init_slope, 0);
    const float x_border = 600;
    scroll_curves_[i][1] = CurveSegment(x_border, 1 / divisor, 0, 0);
    // For scrolling / flinging we level off the speed.
    const float slope = init_slope;
    const float y_at_border = x_border * x_border / divisor;
    const float icept = y_at_border - slope * x_border;
    scroll_curves_[i][2] = CurveSegment(INFINITY, 0, slope, icept);
  }
}

void AccelFilterInterpreter::ConsumeGesture(const Gesture& gs) {
  const char name[] = "AccelFilterInterpreter::ConsumeGesture";
  LogGestureConsume(name, gs);
  auto debug_data = ActivityLog::AccelGestureDebug{};

  // Use a copy of the gesture gs during the calculations and
  // adjustments so the original is left alone.
  Gesture gs_copy = gs;

  // Setup the parameters for acceleration calculations based on gesture type.
  float* dx;
  float* dy;
  float x_scale;
  float y_scale;
  float* scale_out_x;
  float* scale_out_y;
  float* scale_out_x_ordinal;
  float* scale_out_y_ordinal;
  size_t max_segs;
  CurveSegment* segs;

  if (!get_accel_parameters(gs_copy,
                            dx, dy,
                            x_scale, y_scale,
                            scale_out_x, scale_out_y,
                            scale_out_x_ordinal, scale_out_y_ordinal,
                            segs, max_segs)) {
    // It was determined no acceleration was required.
    debug_data.no_accel_for_gesture_type = true;
    LogDebugData(debug_data);
    LogGestureProduce(name, gs);
    ProduceGesture(gs);
    return;
  }
  debug_data.x_y_are_velocity = (dx == nullptr || dy == nullptr);
  debug_data.x_scale = x_scale;
  debug_data.y_scale = y_scale;
  debug_data.dt = get_dt(gs);
  debug_data.adjusted_dt = get_adjusted_dt(gs);

  float speed;
  if (!get_actual_speed(dx, dy,
                        gs.details.fling.vx, gs.details.fling.vy,
                        get_adjusted_dt(gs),
                        speed)) {
    // dt was too small, don't accelerate.
    debug_data.no_accel_for_small_dt = true;
    LogDebugData(debug_data);
    LogGestureProduce(name, gs);
    ProduceGesture(gs);
    return;
  }
  debug_data.speed = speed;
  smooth_speed(gs, speed);
  debug_data.smoothed_speed = speed;

  // Avoid scaling if the speed is too small.
  if (speed < 0.00001) {
    debug_data.no_accel_for_small_speed = true;
    if (gs.type != kGestureTypeFling)
      debug_data.dropped_gesture = true;
    LogDebugData(debug_data);
    if (gs.type == kGestureTypeFling) {
      LogGestureProduce(name, gs);
      ProduceGesture(gs); // Filter out zero length gestures.
    }
  } else {
    // Find the appropriate ratio and apply scaling.
    auto ratio = RatioFromAccelCurve(segs, max_segs, speed);
    debug_data.gain_x = ratio;
    debug_data.gain_y = ratio;
    if (ratio > 0.0) {
      *scale_out_x *= ratio * x_scale;
      *scale_out_y *= ratio * y_scale;

      if (gs.type == kGestureTypeFling ||
          gs.type == kGestureTypeScroll) {
        // We don't accelerate the ordinal values as we do for normal ones
        // because this is how the Chrome needs it.
        *scale_out_x_ordinal *= x_scale;
        *scale_out_y_ordinal *= y_scale;
      }
      LogDebugData(debug_data);
      LogGestureProduce(name, gs_copy);
      ProduceGesture(gs_copy);
    } else {
      debug_data.no_accel_for_bad_gain = true;
      debug_data.dropped_gesture = true;
      LogDebugData(debug_data);
    }
  }
}

float AccelFilterInterpreter::get_dt(const Gesture& gs) {
  return gs.end_time - gs.start_time;
}

float AccelFilterInterpreter::get_adjusted_dt(const Gesture& gs) {
  float dt = get_dt(gs);

  // If dt is not reasonable, use the last seen reasonable value
  // Otherwise, save it as the last seen reasonable value
  if (dt < min_reasonable_dt_.val_ || dt > max_reasonable_dt_.val_)
    dt = last_reasonable_dt_;
  else
    last_reasonable_dt_ = dt;

  return dt;
}

bool AccelFilterInterpreter::get_accel_parameters(
    Gesture& gs,
    float*& dx, float*& dy,
    float& x_scale, float& y_scale,
    float*& scale_out_x, float*& scale_out_y,
    float*& scale_out_x_ordinal, float*& scale_out_y_ordinal,
    CurveSegment*& segs, size_t& max_segs) {
  // CurveSegments to use.
  max_segs = kMaxCurveSegs;
  segs = nullptr;

  dx = nullptr;
  dy = nullptr;

  // The quantities to scale.
  scale_out_x = nullptr;
  scale_out_y = nullptr;

  // We scale ordinal values of scroll/fling gestures as well because we use
  // them in Chrome for history navigation (back/forward page gesture) and
  // we will easily run out of the touchpad space if we just use raw values
  // as they are. To estimate the length one needs to scroll on the touchpad
  // to trigger the history navigation:
  //
  // Pixel:
  // 1280 (screen width in DIPs) * 0.25 (overscroll threshold) /
  // (133 / 25.4) (conversion factor from DIP to mm) = 61.1 mm
  // Most other low-res devices:
  // 1366 * 0.25 / (133 / 25.4) = 65.2 mm
  //
  // With current scroll output scaling factor (2.5), we can reduce the length
  // required to about one inch on all devices.
  scale_out_x_ordinal = nullptr;
  scale_out_y_ordinal = nullptr;

  // Setup the parameters for acceleration calculations based on gesture
  // type.
  switch (gs.type) {
    case kGestureTypeMove:
    case kGestureTypeSwipe:
    case kGestureTypeFourFingerSwipe:
      // Setup the Gesture delta fields/scaling for the gesture type.
      if (gs.type == kGestureTypeMove) {
        scale_out_x = dx = &gs.details.move.dx;
        scale_out_y = dy = &gs.details.move.dy;
      } else if (gs.type == kGestureTypeSwipe) {
        scale_out_x = dx = &gs.details.swipe.dx;
        scale_out_y = dy = &gs.details.swipe.dy;
      } else {
        scale_out_x = dx = &gs.details.four_finger_swipe.dx;
        scale_out_y = dy = &gs.details.four_finger_swipe.dy;
      }

      // Setup CurveSegments for the device options set.
      if (use_mouse_point_curves_.val_ && use_custom_mouse_curve_.val_) {
        // Custom Mouse.
        segs = mouse_custom_point_;
        max_segs = kMaxCustomCurveSegs;
      } else if (!use_mouse_point_curves_.val_ &&
                 use_custom_tp_point_curve_.val_) {
        // Custom Touch.
        segs = tp_custom_point_;
        max_segs = kMaxCustomCurveSegs;
      } else if (use_mouse_point_curves_.val_) {
        // Standard Mouse.
        if (!pointer_acceleration_.val_) {
          segs = &unaccel_mouse_curves_[pointer_sensitivity_.val_ - 1];
          max_segs = kMaxUnaccelCurveSegs;
        } else if (use_old_mouse_point_curves_.val_) {
          segs = old_mouse_point_curves_[pointer_sensitivity_.val_ - 1];
        } else {
          segs = mouse_point_curves_[pointer_sensitivity_.val_ - 1];
        }
      } else {
        // Standard Touch.
        if (!pointer_acceleration_.val_) {
          segs = &unaccel_point_curves_[pointer_sensitivity_.val_ - 1];
          max_segs = kMaxUnaccelCurveSegs;
        } else {
          segs = point_curves_[pointer_sensitivity_.val_ - 1];
        }
      }

      x_scale = point_x_out_scale_.val_;
      y_scale = point_y_out_scale_.val_;
      break;

    case kGestureTypeFling:
    case kGestureTypeScroll:
      // We bypass mouse scroll events as they have a separate acceleration
      // algorithm implemented in mouse_interpreter.
      if (use_mouse_scroll_curves_.val_)
        return false;

      // Setup the Gesture velocity/delta fields/scaling for the gesture type.
      if (gs.type == kGestureTypeFling) {
        scale_out_x = &gs.details.fling.vx;
        scale_out_y = &gs.details.fling.vy;
        scale_out_x_ordinal = &gs.details.fling.ordinal_vx;
        scale_out_y_ordinal = &gs.details.fling.ordinal_vy;
      } else {
        scale_out_x = dx = &gs.details.scroll.dx;
        scale_out_y = dy = &gs.details.scroll.dy;
        scale_out_x_ordinal = &gs.details.scroll.ordinal_dx;
        scale_out_y_ordinal = &gs.details.scroll.ordinal_dy;
      }

      // Setup CurveSegments for the device options set.
      if (!use_custom_tp_scroll_curve_.val_) {
        segs = scroll_curves_[scroll_sensitivity_.val_ - 1];
      } else {
        segs = tp_custom_scroll_;
        max_segs = kMaxCustomCurveSegs;
      }

      x_scale = scroll_x_out_scale_.val_;
      y_scale = scroll_y_out_scale_.val_;
      break;

    default:  // Nothing to accelerate.
      return false;
  }
  return true;
}

bool AccelFilterInterpreter::get_actual_speed(
    float* dx, float* dy,
    float vx, float vy,
    float dt,
    float& speed) {
  // Calculate the hypotenuse to determine the actual speed.
  if (dx != nullptr && dy != nullptr) {
    if (dt < 0.00001)
      return false;  // Avoid division by 0.
    speed = sqrtf(*dx * *dx + *dy * *dy) / dt;
  } else {
    // FLING is the only gesture that uses vx/vy and assumes dt=1.
    speed = sqrtf(vx * vx + vy * vy);
  }
  return true;
}

void AccelFilterInterpreter::smooth_speed(const Gesture& gs, float& speed) {
  // Perform smoothing, if it is enabled.
  if (smooth_accel_.val_) {
    // Check if clock changed backwards.
    if (last_end_time_ > gs.start_time)
      last_end_time_ = -1.0;

    if (last_end_time_ == gs.start_time) {
      // Average the saved magnitudes.
      last_mags_.insert(last_mags_.begin(), speed);
      speed = std::reduce(last_mags_.begin(), last_mags_.end()) /
              last_mags_.size();
      // Limit the size of last_mags_ to the needed oldest mag entries.
      if (last_mags_.size() > kMaxLastMagsSize)
        last_mags_.pop_back();
    } else {
      // Time stamp jump, start smoothing anew.
      last_mags_.clear();
      last_mags_.push_back(speed);
    }
    last_end_time_ = gs.end_time;
  }
}

float AccelFilterInterpreter::RatioFromAccelCurve(
    const CurveSegment* segs,
    const size_t max_segs,
    const float speed) {
  if (speed <= 0.0)
    return 0.0;

  for (size_t i = 0; i < max_segs; ++i) {
    const CurveSegment& seg = segs[i];
    if (speed <= seg.x_) {
      return (seg.sqr_ * speed) + seg.mul_ + (seg.int_ / speed);
    }
  }
  return 0.0;
}

}  // namespace gestures
