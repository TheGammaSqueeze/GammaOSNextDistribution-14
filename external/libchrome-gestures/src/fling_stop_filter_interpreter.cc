// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/fling_stop_filter_interpreter.h"

#include "include/util.h"

namespace gestures {

FlingStopFilterInterpreter::FlingStopFilterInterpreter(
    PropRegistry* prop_reg, Interpreter* next, Tracer* tracer,
    GestureInterpreterDeviceClass devclass)
    : FilterInterpreter(nullptr, next, tracer, false),
      already_extended_(false),
      prev_touch_cnt_(0),
      prev_gesture_type_(kGestureTypeNull),
      fling_stop_already_sent_(false),
      fling_stop_deadline_(NO_DEADLINE),
      devclass_(devclass),
      fling_stop_timeout_(prop_reg, "Fling Stop Timeout", 0.03),
      fling_stop_extra_delay_(prop_reg, "Fling Stop Extra Delay", 0.055) {
  InitName();
}

void FlingStopFilterInterpreter::SyncInterpretImpl(HardwareState& hwstate,
                                                   stime_t* timeout) {
  const char name[] = "FlingStopFilterInterpreter::SyncInterpretImpl";
  LogHardwareStatePre(name, hwstate);

  fingers_of_last_hwstate_.clear();
  for (int i = 0; i < hwstate.finger_cnt; i++)
    fingers_of_last_hwstate_.insert(hwstate.fingers[i].tracking_id);

  UpdateFlingStopDeadline(hwstate);
  if (fling_stop_deadline_ != NO_DEADLINE) {
    if (!already_extended_ && NeedsExtraTime(hwstate)) {
      fling_stop_deadline_ += fling_stop_extra_delay_.val_;
      already_extended_ = true;
    }
    if (hwstate.timestamp > fling_stop_deadline_) {
      // sub in a fling before processing other interpreters
      auto fling_tap_down = Gesture(kGestureFling, prev_timestamp_,
                                    hwstate.timestamp, 0.0, 0.0,
                                    GESTURES_FLING_TAP_DOWN);
      LogGestureProduce(name, fling_tap_down);
      ProduceGesture(fling_tap_down);

      fling_stop_already_sent_ = true;
      fling_stop_deadline_ = NO_DEADLINE;
    }
  }

  stime_t next_timeout = NO_DEADLINE;
  LogHardwareStatePost(name, hwstate);
  next_->SyncInterpret(hwstate, &next_timeout);

  *timeout = SetNextDeadlineAndReturnTimeoutVal(hwstate.timestamp,
                                                fling_stop_deadline_,
                                                next_timeout);
}

bool FlingStopFilterInterpreter::NeedsExtraTime(
    const HardwareState& hwstate) const {
  int num_new_fingers = 0;
  for (int i = 0; i < hwstate.finger_cnt; i++) {
    const short id = hwstate.fingers[i].tracking_id;
    if (!SetContainsValue(fingers_present_for_last_fling_, id)) {
      num_new_fingers++;
    }
  }
  return (num_new_fingers >= 2);
}

bool FlingStopFilterInterpreter::FlingStopNeeded(const Gesture& gesture) const {
  if (fling_stop_already_sent_ || gesture.type == prev_gesture_type_)
    return false;

  if (devclass_ == GESTURES_DEVCLASS_MULTITOUCH_MOUSE &&
      gesture.type == kGestureTypeMove)
    return false;

  return (gesture.type != kGestureTypeFling &&
          gesture.type != kGestureTypeSwipeLift &&
          gesture.type != kGestureTypeFourFingerSwipeLift);
}

void FlingStopFilterInterpreter::ConsumeGesture(const Gesture& gesture) {
  const char name[] = "FlingStopFilterInterpreter::ConsumeGesture";
  LogGestureConsume(name, gesture);

  if (gesture.type == kGestureTypeFling) {
    fingers_present_for_last_fling_ = fingers_of_last_hwstate_;
    already_extended_ = false;
  }

  if (FlingStopNeeded(gesture)) {
    // sub in a fling before a new gesture
    auto fling_tap_down = Gesture(kGestureFling, gesture.start_time,
                                  gesture.start_time, 0.0, 0.0,
                                  GESTURES_FLING_TAP_DOWN);
    LogGestureProduce(name, fling_tap_down);
    ProduceGesture(fling_tap_down);
  }
  LogGestureProduce(name, gesture);
  ProduceGesture(gesture);

  fling_stop_deadline_ = NO_DEADLINE;
  prev_gesture_type_ = gesture.type;
  fling_stop_already_sent_ = false;
}

void FlingStopFilterInterpreter::UpdateFlingStopDeadline(
    const HardwareState& hwstate) {
  if (fling_stop_timeout_.val_ <= 0.0)
    return;

  stime_t now = hwstate.timestamp;
  bool finger_added = hwstate.touch_cnt > prev_touch_cnt_;

  if (finger_added && fling_stop_deadline_ == NO_DEADLINE) {
    // first finger added in a while. Note it.
    fling_stop_deadline_ = now + fling_stop_timeout_.val_;
    return;
  }

  prev_timestamp_ = now;
  prev_touch_cnt_ = hwstate.touch_cnt;
}

void FlingStopFilterInterpreter::HandleTimerImpl(stime_t now,
                                                 stime_t* timeout) {
  const char name[] = "FlingStopFilterInterpreter::HandleTimerImpl";
  LogHandleTimerPre(name, now, timeout);

  stime_t next_timeout;
  if (ShouldCallNextTimer(fling_stop_deadline_)) {
    if (next_timer_deadline_ > now) {
      Err("Spurious callback. now: %f, fs deadline: %f, next deadline: %f",
          now, fling_stop_deadline_, next_timer_deadline_);
      return;
    }
    next_timeout = NO_DEADLINE;
    next_->HandleTimer(now, &next_timeout);
  } else {
    if (fling_stop_deadline_ > now) {
      Err("Spurious callback. now: %f, fs deadline: %f, next deadline: %f",
          now, fling_stop_deadline_, next_timer_deadline_);
      return;
    }
    fling_stop_deadline_ = NO_DEADLINE;
    auto fling_tap_down = Gesture(kGestureFling, prev_timestamp_,
                                  now, 0.0, 0.0,
                                  GESTURES_FLING_TAP_DOWN);
    LogGestureProduce(name, fling_tap_down);
    ProduceGesture(fling_tap_down);

    fling_stop_already_sent_ = true;
    next_timeout = next_timer_deadline_ == NO_DEADLINE ||
                   next_timer_deadline_ <= now
                      ? NO_DEADLINE
                      : next_timer_deadline_ - now;
  }
  *timeout = SetNextDeadlineAndReturnTimeoutVal(now, fling_stop_deadline_,
                                                next_timeout);
  LogHandleTimerPost(name, now, timeout);
}

}  // namespace gestures
