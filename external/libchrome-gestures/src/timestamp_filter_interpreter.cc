// Copyright 2017 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/timestamp_filter_interpreter.h"

#include <math.h>

#include "include/logging.h"
#include "include/tracer.h"

namespace gestures {

TimestampFilterInterpreter::TimestampFilterInterpreter(
    PropRegistry* prop_reg, Interpreter* next, Tracer* tracer)
    : FilterInterpreter(nullptr, next, tracer, false),
      prev_msc_timestamp_(-1.0),
      msc_timestamp_offset_(-1.0),
      fake_timestamp_(-1.0),
      fake_timestamp_max_divergence_(0.1),
      skew_(0.0),
      max_skew_(0.0),
      fake_timestamp_delta_(prop_reg, "Fake Timestamp Delta", 0.0) {
  InitName();
}

void TimestampFilterInterpreter::SyncInterpretImpl(
    HardwareState& hwstate, stime_t* timeout) {
  const char name[] = "TimestampFilterInterpreter::SyncInterpretImpl";
  LogHardwareStatePre(name, hwstate);
  auto debug_data = ActivityLog::TimestampHardwareStateDebug{};

  if (fake_timestamp_delta_.val_ == 0.0)
    ChangeTimestampDefault(hwstate, debug_data);
  else
    ChangeTimestampUsingFake(hwstate, debug_data);

  LogDebugData(debug_data);
  LogHardwareStatePost(name, hwstate);
  next_->SyncInterpret(hwstate, timeout);
}

void TimestampFilterInterpreter::ChangeTimestampDefault(
    HardwareState& hwstate,
    ActivityLog::TimestampHardwareStateDebug& debug_data) {
  // Check if this is the first event or there has been a jump backwards.
  debug_data.prev_msc_timestamp_in = prev_msc_timestamp_;
  if (prev_msc_timestamp_ < 0.0 ||
      hwstate.msc_timestamp == 0.0 ||
      hwstate.msc_timestamp < prev_msc_timestamp_) {
    msc_timestamp_offset_ = hwstate.timestamp - hwstate.msc_timestamp;
    max_skew_ = 0.0;
    debug_data.was_first_or_backward = true;
  }
  prev_msc_timestamp_ = hwstate.msc_timestamp;
  debug_data.prev_msc_timestamp_out = prev_msc_timestamp_;

  stime_t new_timestamp = hwstate.msc_timestamp + msc_timestamp_offset_;
  skew_ = new_timestamp - hwstate.timestamp;
  max_skew_ = std::max(max_skew_, skew_);
  hwstate.timestamp = new_timestamp;

  hwstate.msc_timestamp = 0.0;
  debug_data.skew = skew_;
  debug_data.max_skew = max_skew_;
}

void TimestampFilterInterpreter::ChangeTimestampUsingFake(
    HardwareState& hwstate,
    ActivityLog::TimestampHardwareStateDebug& debug_data) {
  debug_data.is_using_fake = true;
  debug_data.fake_timestamp_in = fake_timestamp_;
  debug_data.fake_timestamp_delta = fake_timestamp_delta_.val_;
  fake_timestamp_ += fake_timestamp_delta_.val_;
  if (fabs(fake_timestamp_ - hwstate.timestamp) >
      fake_timestamp_max_divergence_) {
    fake_timestamp_ = hwstate.timestamp;
    max_skew_ = 0.0;
    debug_data.was_divergence_reset = true;
  }
  debug_data.fake_timestamp_out = fake_timestamp_;

  skew_ = fake_timestamp_ - hwstate.timestamp;
  max_skew_ = std::max(max_skew_, skew_);
  hwstate.timestamp = fake_timestamp_;
  debug_data.skew = skew_;
  debug_data.max_skew = max_skew_;
}

void TimestampFilterInterpreter::HandleTimerImpl(stime_t now,
                                                 stime_t* timeout) {
  const char name[] = "TimestampFilterInterpreter::HandleTimerImpl";
  LogHandleTimerPre(name, now, timeout);

  // Adjust the timestamp by the largest skew_ since reset. This ensures that
  // the callback isn't ignored because it looks like it's coming too early.
  now += max_skew_;
  next_->HandleTimer(now, timeout);

  LogHandleTimerPost(name, now, timeout);
}

void TimestampFilterInterpreter::ConsumeGesture(const Gesture& gs) {
  const char name[] = "TimestampFilterInterpreter::ConsumeGesture";
  LogGestureConsume(name, gs);
  auto debug_data = ActivityLog::TimestampGestureDebug{ skew_ };

  // Adjust gesture timestamp by latest skew to match browser clock
  Gesture copy = gs;
  copy.start_time -= skew_;
  copy.end_time -= skew_;

  LogDebugData(debug_data);
  LogGestureProduce(name, copy);
  ProduceGesture(copy);
}

}  // namespace gestures
