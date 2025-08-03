// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <memory>
#include <set>
#include <gtest/gtest.h>  // for FRIEND_TEST

#include "include/filter_interpreter.h"
#include "include/finger_metrics.h"
#include "include/gestures.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_FLING_STOP_FILTER_INTERPRETER_H_
#define GESTURES_FLING_STOP_FILTER_INTERPRETER_H_

namespace gestures {

// This interpreter generates the fling-stop messages when new fingers
// arrive on the pad.

class FlingStopFilterInterpreter : public FilterInterpreter {
  FRIEND_TEST(FlingStopFilterInterpreterTest, SimpleTest);
  FRIEND_TEST(FlingStopFilterInterpreterTest, FlingGestureTest);
  FRIEND_TEST(FlingStopFilterInterpreterTest, FlingStopMultimouseMoveTest);
 public:
  // Takes ownership of |next|:
  FlingStopFilterInterpreter(PropRegistry* prop_reg,
                             Interpreter* next,
                             Tracer* tracer,
                             GestureInterpreterDeviceClass devclass);
  virtual ~FlingStopFilterInterpreter() {}

 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate, stime_t* timeout);

  virtual void HandleTimerImpl(stime_t now, stime_t* timeout);

  virtual void ConsumeGesture(const Gesture& gesture);

 private:
  // May override an outgoing gesture with a fling stop gesture.
  bool NeedsExtraTime(const HardwareState& hwstate) const;
  bool FlingStopNeeded(const Gesture& gesture) const;
  void UpdateFlingStopDeadline(const HardwareState& hwstate);

  // Has the deadline has already been extended once
  bool already_extended_;

  // Which tracking id's were on the pad at the last fling
  std::set<short> fingers_present_for_last_fling_;

  // tracking id's of the last hardware state
  std::set<short> fingers_of_last_hwstate_;

  // touch_cnt from previously input HardwareState.
  short prev_touch_cnt_;
  // timestamp from previous input HardwareState.
  stime_t prev_timestamp_;

  // Most recent gesture type consumed and produced.
  GestureType prev_gesture_type_;
  // Whether a fling stop has been sent since the last gesture.
  bool fling_stop_already_sent_;

  // When we should send fling-stop, or NO_DEADLINE if not set.
  stime_t fling_stop_deadline_;

  // Device class (e.g. touchpad, mouse).
  GestureInterpreterDeviceClass devclass_;

  // How long to wait when new fingers arrive (and possibly scroll), before
  // halting fling
  DoubleProperty fling_stop_timeout_;
  // How much extra time to add if it looks likely to be the start of a scroll
  DoubleProperty fling_stop_extra_delay_;
};

}  // namespace gestures

#endif  // GESTURES_FLING_STOP_FILTER_INTERPRETER_H_
