// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/filter_interpreter.h"
#include "include/gestures.h"
#include "include/tracer.h"

#ifndef GESTURES_INTEGRAL_GESTURE_FILTER_INTERPRETER_H_
#define GESTURES_INTEGRAL_GESTURE_FILTER_INTERPRETER_H_

namespace gestures {

// This interpreter passes HardwareState unmodified to next_. All gestures
// that pass through, though, are changed to have integral values. Any
// remainder is stored and added to the next gestures. This means that if
// a user is very slowly rolling their finger, many gestures w/ values < 1
// can be accumulated and together create a move of a single pixel.

class IntegralGestureFilterInterpreter : public FilterInterpreter {
  FRIEND_TEST(IntegralGestureFilterInterpreterTestInterpreter, ConsumeGesture);
 public:
  // Takes ownership of |next|:
  explicit IntegralGestureFilterInterpreter(Interpreter* next, Tracer* tracer);
  virtual ~IntegralGestureFilterInterpreter() {}

 private:
  virtual void SyncInterpretImpl(HardwareState& hwstate, stime_t* timeout);
  virtual void ConsumeGesture(const Gesture& gesture);

 private:
  Gesture* HandleGesture(Gesture* gs);

  float hscroll_remainder_, vscroll_remainder_;
  float hscroll_ordinal_remainder_, vscroll_ordinal_remainder_;
  bool can_clear_remainders_;

  stime_t remainder_reset_deadline_;
  virtual void HandleTimerImpl(stime_t now, stime_t *timeout);
};

}  // namespace gestures

#endif  // GESTURES_INTEGRAL_GESTURE_FILTER_INTERPRETER_H_
