// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <memory>

#include <gtest/gtest.h>
#include <json/value.h>

#include "include/interpreter.h"
#include "include/macros.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_FILTER_INTERPRETER_H__
#define GESTURES_FILTER_INTERPRETER_H__

namespace gestures {

// Interface for all filter interpreters.

class FilterInterpreter : public Interpreter, public GestureConsumer {
  FRIEND_TEST(FilterInterpreterTest, DeadlineSettingNoDeadlines);
  FRIEND_TEST(FilterInterpreterTest, DeadlineSettingLocalOnly);
  FRIEND_TEST(FilterInterpreterTest, DeadlineSettingNextOnly);
  FRIEND_TEST(FilterInterpreterTest, DeadlineSettingLocalBeforeNext);
  FRIEND_TEST(FilterInterpreterTest, DeadlineSettingNextBeforeLocal);
 public:
  FilterInterpreter(PropRegistry* prop_reg,
                    Interpreter* next,
                    Tracer* tracer,
                    bool force_log_creation)
      : Interpreter(prop_reg, tracer, force_log_creation) { next_.reset(next); }
  virtual ~FilterInterpreter() {}

  Json::Value EncodeCommonInfo();
  void Clear();

  virtual void Initialize(const HardwareProperties* hwprops,
                          Metrics* metrics, MetricsProperties* mprops,
                          GestureConsumer* consumer);

  virtual void ConsumeGesture(const Gesture& gesture);

 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate, stime_t* timeout);
  virtual void HandleTimerImpl(stime_t now, stime_t* timeout);

  // When we need to call HandlerTimer on next_, or NO_DEADLINE if there's no
  // outstanding timer for next_.
  stime_t next_timer_deadline_ = NO_DEADLINE;
  // Sets the next timer deadline, taking into account the deadline needed for
  // this interpreter and the one from the next in the chain.
  stime_t SetNextDeadlineAndReturnTimeoutVal(stime_t now,
                                             stime_t local_deadline,
                                             stime_t next_timeout);
  // Utility method for determining whether the timer callback is for this
  // interpreter or one further down the chain.
  bool ShouldCallNextTimer(stime_t local_deadline);

  std::unique_ptr<Interpreter> next_;

 private:
  DISALLOW_COPY_AND_ASSIGN(FilterInterpreter);
};
}  // namespace gestures

#endif  // GESTURES_FILTER_INTERPRETER_H__
