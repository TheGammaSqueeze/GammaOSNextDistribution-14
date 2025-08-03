// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/mouse_interpreter.h"

#include <math.h>

#include "include/logging.h"
#include "include/macros.h"
#include "include/tracer.h"

namespace gestures {

/*
 * The number of subdivisions that `REL_WHEEL_HI_RES` and `REL_HWHEEL_HI_RES`
 * have relative to `REL_WHEEL` and `REL_HWHEEL` respectively.
 */
const static int REL_WHEEL_HI_RES_UNITS_PER_NOTCH = 120;

// Default value for mouse scroll sensitivity.
const static int kMouseScrollSensitivityDefaultValue = 3;

MouseInterpreter::MouseInterpreter(PropRegistry* prop_reg, Tracer* tracer)
    : Interpreter(nullptr, tracer, false),
      wheel_emulation_accu_x_(0.0),
      wheel_emulation_accu_y_(0.0),
      wheel_emulation_active_(false),
      reverse_scrolling_(prop_reg, "Mouse Reverse Scrolling", false),
      scroll_acceleration_(prop_reg, "Mouse Scroll Acceleration", true),
      scroll_sensitivity_(prop_reg,"Mouse Scroll Sensitivity",
        kMouseScrollSensitivityDefaultValue),
      hi_res_scrolling_(prop_reg, "Mouse High Resolution Scrolling", true),
      scroll_accel_curve_prop_(prop_reg, "Mouse Scroll Accel Curve",
          scroll_accel_curve_, sizeof(scroll_accel_curve_) / sizeof(double)),
      scroll_max_allowed_input_speed_(prop_reg,
                                      "Mouse Scroll Max Input Speed",
                                      177.0),
      force_scroll_wheel_emulation_(prop_reg,
                                     "Force Scroll Wheel Emulation",
                                     false),
      scroll_wheel_emulation_speed_(prop_reg,
                                    "Scroll Wheel Emulation Speed",
                                    100.0),
      scroll_wheel_emulation_thresh_(prop_reg,
                                    "Scroll Wheel Emulation Threshold",
                                    1.0),
      output_mouse_wheel_gestures_(prop_reg,
                                   "Output Mouse Wheel Gestures", false) {
  InitName();
  memset(&prev_state_, 0, sizeof(prev_state_));
  memset(&last_wheel_, 0, sizeof(last_wheel_));
  memset(&last_hwheel_, 0, sizeof(last_hwheel_));
  // Scroll acceleration curve coefficients. See the definition for more
  // details on how to generate them.
  scroll_accel_curve_[0] = 1.0374e+01;
  scroll_accel_curve_[1] = 4.1773e-01;
  scroll_accel_curve_[2] = 2.5737e-02;
  scroll_accel_curve_[3] = 8.0428e-05;
  scroll_accel_curve_[4] = -9.1149e-07;
  scroll_max_allowed_input_speed_.SetDelegate(this);
}

void MouseInterpreter::SyncInterpretImpl(HardwareState& hwstate,
                                         stime_t* timeout) {
  const char name[] = "MouseInterpreter::SyncInterpretImpl";
  LogHardwareStatePre(name, hwstate);

  if(!EmulateScrollWheel(hwstate)) {
    // Interpret mouse events in the order of pointer moves, scroll wheels and
    // button clicks.
    InterpretMouseMotionEvent(prev_state_, hwstate);
    // Note that unlike touchpad scrolls, we interpret and send separate events
    // for horizontal/vertical mouse wheel scrolls. This is partly to match what
    // the xf86-input-evdev driver does and is partly because not all code in
    // Chrome honors MouseWheelEvent that has both X and Y offsets.
    InterpretScrollWheelEvent(hwstate, true);
    InterpretScrollWheelEvent(hwstate, false);
    InterpretMouseButtonEvent(prev_state_, hwstate);
  }
  // Pass max_finger_cnt = 0 to DeepCopy() since we don't care fingers and
  // did not allocate any space for fingers.
  prev_state_.DeepCopy(hwstate, 0);

  LogHardwareStatePost(name, hwstate);
}

double MouseInterpreter::ComputeScrollAccelFactor(double input_speed) {
  double result = 0.0;
  double term = 1.0;
  double allowed_speed = fabs(input_speed);
  if (allowed_speed > scroll_max_allowed_input_speed_.val_)
    allowed_speed = scroll_max_allowed_input_speed_.val_;

  // Compute the scroll acceleration factor.
  for (size_t i = 0; i < arraysize(scroll_accel_curve_); i++) {
    result += term * scroll_accel_curve_[i];
    term *= allowed_speed;
  }
  return result;
}

bool MouseInterpreter::EmulateScrollWheel(const HardwareState& hwstate) {
  const char name[] = "MouseInterpreter::EmulateScrollWheel";

  if (!force_scroll_wheel_emulation_.val_ && hwprops_->has_wheel)
    return false;
  bool down = hwstate.buttons_down & GESTURES_BUTTON_MIDDLE ||
              (hwstate.buttons_down & GESTURES_BUTTON_LEFT &&
               hwstate.buttons_down & GESTURES_BUTTON_RIGHT);
  bool prev_down = prev_state_.buttons_down & GESTURES_BUTTON_MIDDLE ||
                   (prev_state_.buttons_down & GESTURES_BUTTON_LEFT &&
                    prev_state_.buttons_down & GESTURES_BUTTON_RIGHT);
  bool raising = down && !prev_down;
  bool falling = !down && prev_down;

  // Reset scroll emulation detection on button down.
  if (raising) {
    wheel_emulation_accu_x_ = 0;
    wheel_emulation_accu_y_ = 0;
    wheel_emulation_active_ = false;
  }

  // Send button event if button has been released without scrolling.
  if (falling && !wheel_emulation_active_) {
    auto button_change = Gesture(kGestureButtonsChange,
                           prev_state_.timestamp,
                           hwstate.timestamp,
                           prev_state_.buttons_down,
                           prev_state_.buttons_down,
                           false); // is_tap
    LogGestureProduce(name, button_change);
    ProduceGesture(button_change);
  }

  if (down) {
    // Detect scroll emulation
    if (!wheel_emulation_active_) {
      wheel_emulation_accu_x_ += hwstate.rel_x;
      wheel_emulation_accu_y_ += hwstate.rel_y;
      double dist_sq = wheel_emulation_accu_x_ * wheel_emulation_accu_x_ +
                       wheel_emulation_accu_y_ * wheel_emulation_accu_y_;
      double thresh_sq = scroll_wheel_emulation_thresh_.val_ *
                         scroll_wheel_emulation_thresh_.val_;
      if (dist_sq > thresh_sq) {
        // Lock into scroll emulation until button is released.
        wheel_emulation_active_ = true;
      }
    }

    // Transform motion into scrolling.
    if (wheel_emulation_active_) {
      double scroll_x = hwstate.rel_x * scroll_wheel_emulation_speed_.val_;
      double scroll_y = hwstate.rel_y * scroll_wheel_emulation_speed_.val_;

      auto scroll = Gesture(kGestureScroll, hwstate.timestamp,
                            hwstate.timestamp, scroll_x, scroll_y);
      LogGestureProduce(name, scroll);
      ProduceGesture(scroll);
    }
    return true;
  }

  return false;
}

void MouseInterpreter::InterpretScrollWheelEvent(const HardwareState& hwstate,
                                                 bool is_vertical) {
  const char name[] = "MouseInterpreter::InterpretScrollWheelEvent";

  const float scroll_wheel_event_time_delta_min = 0.008;
  bool use_high_resolution =
      is_vertical && hwprops_->wheel_is_hi_res
      && hi_res_scrolling_.val_;
  // Vertical wheel or horizontal wheel.
  float current_wheel_value = hwstate.rel_hwheel;
  int ticks = hwstate.rel_hwheel * REL_WHEEL_HI_RES_UNITS_PER_NOTCH;
  WheelRecord* last_wheel_record = &last_hwheel_;
  if (is_vertical) {
    // Only vertical high-res scrolling is supported for now.
    if (use_high_resolution) {
      current_wheel_value = hwstate.rel_wheel_hi_res
          / REL_WHEEL_HI_RES_UNITS_PER_NOTCH;
      ticks = hwstate.rel_wheel_hi_res;
    } else {
      current_wheel_value = hwstate.rel_wheel;
      ticks = hwstate.rel_wheel * REL_WHEEL_HI_RES_UNITS_PER_NOTCH;
    }
    last_wheel_record = &last_wheel_;
  }

  // Check if the wheel is scrolled.
  if (current_wheel_value) {
    stime_t start_time, end_time = hwstate.timestamp;
    // Check if this scroll is in same direction as previous scroll event.
    if ((current_wheel_value < 0 && last_wheel_record->value < 0) ||
        (current_wheel_value > 0 && last_wheel_record->value > 0)) {
      start_time = last_wheel_record->timestamp;
    } else {
      start_time = end_time;
    }

    // If start_time == end_time, compute velocity using dt = 1 second.
    // (this happens when the user initially starts scrolling)
    stime_t dt = (end_time - start_time) ?: 1.0;
    if (dt < scroll_wheel_event_time_delta_min) {
      // the first packet received after BT wakeup may be delayed, causing the
      // time delta between that and the subsequent packet to be very small.
      // Prevent small time deltas from triggering large amounts of acceleration
      // by enforcing a minimum time delta.
      dt = scroll_wheel_event_time_delta_min;
    }

    // When scroll acceleration is off, the scroll factor does not relate to
    // scroll velocity. It's simply a constant multiplier to the wheel value.
    const double unaccel_scroll_factors[] = { 20.0, 36.0, 72.0, 112.0, 164.0 };

    float velocity = current_wheel_value / dt;
    float offset = current_wheel_value * (
      scroll_acceleration_.val_?
      ComputeScrollAccelFactor(velocity) :
      unaccel_scroll_factors[scroll_sensitivity_.val_ - 1]);
    last_wheel_record->timestamp = hwstate.timestamp;
    last_wheel_record->value = current_wheel_value;

    if (is_vertical) {
      // For historical reasons the vertical wheel (REL_WHEEL) is inverted
      if (!reverse_scrolling_.val_) {
        offset = -offset;
        ticks = -ticks;
      }
      auto scroll_wheel = CreateWheelGesture(start_time, end_time,
                                             0, offset, 0, ticks);
      LogGestureProduce(name, scroll_wheel);
      ProduceGesture(scroll_wheel);
    } else {
      auto scroll_wheel = CreateWheelGesture(start_time, end_time,
                                             offset, 0, ticks, 0);
      LogGestureProduce(name, scroll_wheel);
      ProduceGesture(scroll_wheel);
    }
  }
}

Gesture MouseInterpreter::CreateWheelGesture(
    stime_t start_time, stime_t end_time, float dx, float dy,
    int tick_120ths_dx, int tick_120ths_dy) {
  if (output_mouse_wheel_gestures_.val_) {
    return Gesture(kGestureMouseWheel, start_time, end_time, dx, dy,
                   tick_120ths_dx, tick_120ths_dy);
  } else {
    return Gesture(kGestureScroll, start_time, end_time, dx, dy);
  }
}

void MouseInterpreter::InterpretMouseButtonEvent(
    const HardwareState& prev_state, const HardwareState& hwstate) {
  const char name[] = "MouseInterpreter::InterpretMouseButtonEvent";

  const unsigned buttons[] = {
    GESTURES_BUTTON_LEFT,
    GESTURES_BUTTON_MIDDLE,
    GESTURES_BUTTON_RIGHT,
    GESTURES_BUTTON_BACK,
    GESTURES_BUTTON_FORWARD,
    GESTURES_BUTTON_SIDE,
    GESTURES_BUTTON_EXTRA,
  };
  unsigned down = 0, up = 0;

  for (unsigned i = 0; i < arraysize(buttons); i++) {
    if (!(prev_state.buttons_down & buttons[i]) &&
        (hwstate.buttons_down & buttons[i]))
      down |= buttons[i];
    if ((prev_state.buttons_down & buttons[i]) &&
        !(hwstate.buttons_down & buttons[i]))
      up |= buttons[i];
  }

  if (down || up) {
    auto button_change = Gesture(kGestureButtonsChange,
                                 prev_state.timestamp,
                                 hwstate.timestamp,
                                 down,
                                 up,
                                 false); // is_tap
    LogGestureProduce(name, button_change);
    ProduceGesture(button_change);
  }
}

void MouseInterpreter::InterpretMouseMotionEvent(
    const HardwareState& prev_state,
    const HardwareState& hwstate) {
  const char name[] = "MouseInterpreter::InterpretMouseMotionEvent";

  if (hwstate.rel_x || hwstate.rel_y) {
    auto move = Gesture(kGestureMove,
                        prev_state.timestamp,
                        hwstate.timestamp,
                        hwstate.rel_x,
                        hwstate.rel_y);
    LogGestureProduce(name, move);
    ProduceGesture(move);
  }
}

}  // namespace gestures
