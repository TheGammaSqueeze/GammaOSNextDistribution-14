// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/unittest_util.h"

#include "include/gestures.h"

namespace gestures {

TestInterpreterWrapper::TestInterpreterWrapper(Interpreter* interpreter,
    const HardwareProperties* hwprops)
    : interpreter_(interpreter),
      hwprops_(hwprops) {
  Reset(interpreter);
}

TestInterpreterWrapper::TestInterpreterWrapper(Interpreter* interpreter)
    : interpreter_(interpreter),
      hwprops_(nullptr) {
  Reset(interpreter);
}

void TestInterpreterWrapper::Reset(Interpreter* interpreter) {
  Reset(interpreter, static_cast<MetricsProperties*>(nullptr));
}

void TestInterpreterWrapper::Reset(Interpreter* interpreter,
                                   MetricsProperties* mprops) {
  memset(&dummy_, 0, sizeof(HardwareProperties));
  if (!hwprops_)
    hwprops_ = &dummy_;

  if (!mprops) {
    if (mprops_.get()) {
      mprops_.reset(nullptr);
    }
    prop_reg_.reset(new PropRegistry());
    mprops_.reset(new MetricsProperties(prop_reg_.get()));
  } else {
    mprops_.reset(mprops);
  }

  interpreter_ = interpreter;
  if (interpreter_) {
    interpreter_->Initialize(hwprops_, nullptr, mprops_.get(), this);
  }
}

void TestInterpreterWrapper::Reset(Interpreter* interpreter,
                                   const HardwareProperties* hwprops) {
  hwprops_ = hwprops;
  Reset(interpreter);
}

Gesture* TestInterpreterWrapper::SyncInterpret(HardwareState& state,
                                               stime_t* timeout) {
  gesture_ = Gesture();
  interpreter_->SyncInterpret(state, timeout);
  if (gesture_.type == kGestureTypeNull)
    return nullptr;
  return &gesture_;
}

Gesture* TestInterpreterWrapper::HandleTimer(stime_t now, stime_t* timeout) {
  gesture_.type = kGestureTypeNull;
  interpreter_->HandleTimer(now, timeout);
  if (gesture_.type == kGestureTypeNull)
    return nullptr;
  return &gesture_;
}

void TestInterpreterWrapper::ConsumeGesture(const Gesture& gesture) {
  Assert(gesture_.type == kGestureTypeNull);
  gesture_ = gesture;
}


HardwareState make_hwstate(stime_t timestamp, int buttons_down,
                           unsigned short finger_cnt, unsigned short touch_cnt,
                           struct FingerState* fingers) {
  return {
    timestamp,
    buttons_down,
    finger_cnt,
    touch_cnt,
    fingers,
    0,    // rel_x
    0,    // rel_y
    0,    // rel_wheel
    0,    // rel_wheel_hi_res
    0,    // rel_hwheel
    0.0,  // msc_timestamp
  };
}

}  // namespace gestures
