// Copyright 2021 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>  // for FRIEND_TEST
#include <set>

#include "include/filter_interpreter.h"
#include "include/gestures.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_INCLUDE_HAPTIC_BUTTON_GENERATOR_FILTER_INTERPRETER_H_
#define GESTURES_INCLUDE_HAPTIC_BUTTON_GENERATOR_FILTER_INTERPRETER_H_

// For haptic touchpads, the gesture library is responsible for determining the
// state of the "physical" button. This class sets the button state based on the
// user's force threshold preferences.

namespace gestures {

class HapticButtonGeneratorFilterInterpreter : public FilterInterpreter {
  FRIEND_TEST(HapticButtonGeneratorFilterInterpreterTest, SimpleTest);
  FRIEND_TEST(HapticButtonGeneratorFilterInterpreterTest, NotHapticTest);
  FRIEND_TEST(HapticButtonGeneratorFilterInterpreterTest,
              NotHapticConsumeGestureTest);
  FRIEND_TEST(HapticButtonGeneratorFilterInterpreterTest,
              GesturePreventsButtonDownTest);
  FRIEND_TEST(HapticButtonGeneratorFilterInterpreterTest, DynamicThresholdTest);
  FRIEND_TEST(HapticButtonGeneratorFilterInterpreterTest, PalmTest);
 public:
  // Takes ownership of |next|:
  explicit HapticButtonGeneratorFilterInterpreter(PropRegistry* prop_reg,
                                                  Interpreter* next,
                                                  Tracer* tracer);
  virtual ~HapticButtonGeneratorFilterInterpreter() {}

  virtual void Initialize(const HardwareProperties* hwprops,
                          Metrics* metrics, MetricsProperties* mprops,
                          GestureConsumer* consumer) override;
 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate,
                                 stime_t* timeout) override;

 private:
  void ConsumeGesture(const Gesture& gesture) override;
  void HandleHardwareState(HardwareState& hwstate);
  virtual void HandleTimerImpl(stime_t now, stime_t *timeout) override;
  void UpdatePalmState(const HardwareState& hwstate);

  static const size_t kMaxSensitivitySettings = 5;

  // All threshold values are in grams.
  const double down_thresholds_[kMaxSensitivitySettings] =
      {90.0, 110.0, 130.0, 145.0, 160.0};
  const double up_thresholds_[kMaxSensitivitySettings] =
      {80.0, 95.0, 105.0, 120.0, 135.0};

  std::set<short> palms_;

  // Scaling factor for release force [0.0-1.0]
  double release_suppress_factor_;

  // We prevent button down events during an active non-click multi-finger
  // gesture
  bool active_gesture_;
  double active_gesture_timeout_;
  double active_gesture_deadline_;

  // Is the button currently down?
  bool button_down_;

  bool is_haptic_pad_;

  // When the user presses very hard, we dynamically adjust force thresholds to
  // allow easier double-clicking
  double dynamic_down_threshold_;
  double dynamic_up_threshold_;

  IntProperty sensitivity_;  // [1..5]

  BoolProperty use_custom_thresholds_;
  DoubleProperty custom_down_threshold_;
  DoubleProperty custom_up_threshold_;

  BoolProperty enabled_;

  DoubleProperty force_scale_;
  DoubleProperty force_translate_;

  DoubleProperty complete_release_suppress_speed_;

  BoolProperty use_dynamic_thresholds_;
  DoubleProperty dynamic_down_ratio_;
  DoubleProperty dynamic_up_ratio_;
  DoubleProperty max_dynamic_up_force_;
};

}  // namespace gestures


#endif  // GESTURES_INCLUDE_HAPTIC_BUTTON_GENERATOR_FILTER_INTERPRETER_H_
