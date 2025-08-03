// Copyright 2013 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef GESTURES_UNITTEST_UTIL_H_
#define GESTURES_UNITTEST_UTIL_H_

#include "include/finger_metrics.h"
#include "include/gestures.h"
#include "include/interpreter.h"

namespace gestures {

// A wrapper for interpreters in unit tests. Mimicks the old API and
// initializes the interpreter correctly.
class TestInterpreterWrapper : public GestureConsumer {
 public:
  explicit TestInterpreterWrapper(Interpreter* interpreter);
  TestInterpreterWrapper(Interpreter* interpreter,
                         const HardwareProperties* hwprops);

  void Reset(Interpreter* interpreter);
  // Takes ownership of mprops
  void Reset(Interpreter* interpreter, MetricsProperties* mprops);
  void Reset(Interpreter* interpreter, const HardwareProperties* hwprops);
  Gesture* SyncInterpret(HardwareState& state, stime_t* timeout);
  Gesture* HandleTimer(stime_t now, stime_t* timeout);
  virtual void ConsumeGesture(const Gesture& gs);

 private:
  Interpreter* interpreter_;
  const HardwareProperties* hwprops_;
  HardwareProperties dummy_;
  Gesture gesture_;
  std::unique_ptr<PropRegistry> prop_reg_;
  std::unique_ptr<MetricsProperties> mprops_;
};


// A utility method for making a HardwareState struct with just the fields
// necessary for touchpads. The remaining fields are set to sensible defaults.
HardwareState make_hwstate(stime_t timestamp, int buttons_down,
                           unsigned short finger_cnt, unsigned short touch_cnt,
                           struct FingerState* fingers);

}  // namespace gestures

#endif  // GESTURES_UNITTEST_UTIL_H_
