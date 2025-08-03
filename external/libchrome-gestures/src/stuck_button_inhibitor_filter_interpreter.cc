// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/stuck_button_inhibitor_filter_interpreter.h"

#include "include/logging.h"
#include "include/tracer.h"

namespace gestures {

StuckButtonInhibitorFilterInterpreter::StuckButtonInhibitorFilterInterpreter(
    Interpreter* next, Tracer* tracer)
    : FilterInterpreter(nullptr, next, tracer, false),
      incoming_button_must_be_up_(true),
      sent_buttons_down_(0),
      next_expects_timer_(false) {
  InitName();
}

void StuckButtonInhibitorFilterInterpreter::SyncInterpretImpl(
    HardwareState& hwstate, stime_t* timeout) {
  const char name[] =
      "StuckButtonInhibitorFilterInterpreter::SyncInterpretImpl";
  LogHardwareStatePre(name, hwstate);

  HandleHardwareState(hwstate);

  stime_t next_timeout = NO_DEADLINE;
  LogHardwareStatePost(name, hwstate);
  next_->SyncInterpret(hwstate, &next_timeout);
  HandleTimeouts(next_timeout, timeout);
}

void StuckButtonInhibitorFilterInterpreter::HandleTimerImpl(
    stime_t now, stime_t* timeout) {
  const char name[] = "StuckButtonInhibitorFilterInterpreter::HandleTimerImpl";
  LogHandleTimerPre(name, now, timeout);

  stime_t next_timeout = NO_DEADLINE;
  if (next_expects_timer_) {
    next_->HandleTimer(now, &next_timeout);
  } else {
    if (!sent_buttons_down_) {
      Err("Bug: got callback, but no gesture to send.");
      return;
    } else {
      Err("Mouse button seems stuck down. Sending button-up.");
      auto button_change = Gesture(kGestureButtonsChange,
                                   now, now, 0, sent_buttons_down_,
                                   false); // is_tap
      LogGestureProduce(name, button_change);
      ProduceGesture(button_change);
      sent_buttons_down_ = 0;
    }
  }
  HandleTimeouts(next_timeout, timeout);
  LogHandleTimerPost(name, now, timeout);
}

void StuckButtonInhibitorFilterInterpreter::HandleHardwareState(
    const HardwareState& hwstate) {
  incoming_button_must_be_up_ =
      hwstate.touch_cnt == 0 && hwstate.buttons_down == 0;
}

void StuckButtonInhibitorFilterInterpreter::ConsumeGesture(
    const Gesture& gesture) {
  const char name[] = "StuckButtonInhibitorFilterInterpreter::ConsumeGesture";
  LogGestureConsume(name, gesture);

  if (gesture.type == kGestureTypeButtonsChange) {
    Gesture result = gesture;
    // process buttons going down
    if (sent_buttons_down_ & result.details.buttons.down) {
      Err("Odd. result is sending buttons down that are already down: "
          "Existing down: %d. New down: %d. fixing.",
          sent_buttons_down_, result.details.buttons.down);
      result.details.buttons.down &= ~sent_buttons_down_;
    }
    sent_buttons_down_ |= result.details.buttons.down;
    if ((~sent_buttons_down_) & result.details.buttons.up) {
      Err("Odd. result is sending buttons up for buttons we didn't send down: "
          "Existing down: %d. New up: %d.",
          sent_buttons_down_, result.details.buttons.up);
      result.details.buttons.up &= sent_buttons_down_;
    }
    sent_buttons_down_ &= ~result.details.buttons.up;
    if (!result.details.buttons.up && !result.details.buttons.down)
      return; // skip gesture
    LogGestureProduce(name, result);
    ProduceGesture(result);
  } else {
    LogGestureProduce(name, gesture);
    ProduceGesture(gesture);
  }
}

void StuckButtonInhibitorFilterInterpreter::HandleTimeouts(
    stime_t next_timeout, stime_t* timeout) {
  if (next_timeout >= 0.0) {
    // next_ is doing stuff, so don't interfere
    *timeout = next_timeout;
    next_expects_timer_ = true;
  } else {
    next_expects_timer_ = false;
    if (incoming_button_must_be_up_ && sent_buttons_down_) {
      // We should lift the buttons before too long.
      const stime_t kTimeoutLength = 1.0;
      *timeout = kTimeoutLength;
    }
  }
}

}  // namespace gestures
