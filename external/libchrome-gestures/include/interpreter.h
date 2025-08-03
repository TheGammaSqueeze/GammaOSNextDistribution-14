// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include <gtest/gtest.h>

#include "include/activity_log.h"
#include "include/gestures.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_INTERPRETER_H__
#define GESTURES_INTERPRETER_H__

// This is a collection of supporting structs and an interface for
// Interpreters.

struct HardwareState;

namespace gestures {

class GestureConsumer {
 public:
  virtual ~GestureConsumer() {}
  virtual void ConsumeGesture(const Gesture& gesture) = 0;
};

class Metrics;
class MetricsProperties;

// Interface for all interpreters. Interpreters currently are synchronous.
// A synchronous interpreter will return  0 or 1 Gestures for each passed in
// HardwareState.
class Interpreter {
  FRIEND_TEST(InterpreterTest, SimpleTest);
  FRIEND_TEST(InterpreterTest, ResetLogTest);
  FRIEND_TEST(InterpreterTest, LoggingDisabledByDefault);
  FRIEND_TEST(LoggingFilterInterpreterTest, LogResetHandlerTest);
  FRIEND_TEST(InterpreterTest, EventDebugLoggingEnableTest);
  FRIEND_TEST(InterpreterTest, LogHardwareStateTest);
  FRIEND_TEST(InterpreterTest, LogGestureTest);
  FRIEND_TEST(InterpreterTest, LogHandleTimerTest);
  FRIEND_TEST(TimestampFilterInterpreterParmTest, TimestampDebugLoggingTest);
 public:
  Interpreter(PropRegistry* prop_reg, Tracer* tracer, bool force_log_creation);
  virtual ~Interpreter();

  // Called to interpret the current state.
  // The passed |hwstate| may be modified.
  // If *timeout is set to >0.0, a timer will be setup to call
  // HandleTimer after *timeout time passes. An interpreter can only
  // have up to 1 outstanding timer, so if a timeout is requested by
  // setting *timeout and one already exists, the old one will be cancelled
  // and reused for this timeout.
  virtual void SyncInterpret(HardwareState& hwstate, stime_t* timeout);

  // Called to handle a timeout.
  // If *timeout is set to >0.0, a timer will be setup to call
  // HandleTimer after *timeout time passes. An interpreter can only
  // have up to 1 outstanding timer, so if a timeout is requested by
  // setting *timeout and one already exists, the old one will be cancelled
  // and reused for this timeout.
  virtual void HandleTimer(stime_t now, stime_t* timeout);

  virtual void Initialize(const HardwareProperties* hwprops,
                          Metrics* metrics, MetricsProperties* mprops,
                          GestureConsumer* consumer);

  virtual Json::Value EncodeCommonInfo();
  std::string Encode();

  virtual void Clear() {
    if (log_.get())
      log_->Clear();
  }

  virtual void ProduceGesture(const Gesture& gesture);
  const char* name() const { return name_; }

 protected:
  std::unique_ptr<ActivityLog> log_;
  GestureConsumer* consumer_;
  const HardwareProperties* hwprops_;
  Metrics* metrics_;
  std::unique_ptr<Metrics> own_metrics_;
  bool requires_metrics_;
  bool initialized_;

  void InitName();
  void Trace(const char* message, const char* name);

  virtual void SyncInterpretImpl(HardwareState& hwstate,
                                 stime_t* timeout) {}
  virtual void HandleTimerImpl(stime_t now, stime_t* timeout) {}

  bool EventLoggingIsEnabled();
  void SetEventLoggingEnabled(bool enabled);

  bool EventDebugLoggingIsEnabled(ActivityLog::EventDebug event);
  uint32_t GetEventDebugLoggingEnabled();
  void SetEventDebugLoggingEnabled(uint32_t enabled);
  void EventDebugLoggingDisable(ActivityLog::EventDebug event);
  void EventDebugLoggingEnable(ActivityLog::EventDebug event);

  void LogGestureConsume(const std::string& name, const Gesture& gesture);
  void LogGestureProduce(const std::string& name, const Gesture& gesture);
  void LogHardwareStatePre(const std::string& name,
                           const HardwareState& hwstate);
  void LogHardwareStatePost(const std::string& name,
                            const HardwareState& hwstate);
  void LogHandleTimerPre(const std::string& name,
                         stime_t now, const stime_t* timeout);
  void LogHandleTimerPost(const std::string& name,
                          stime_t now, const stime_t* timeout);

 private:
  const char* name_;
  Tracer* tracer_;

  bool enable_event_logging_ = false;
  uint32_t enable_event_debug_logging_ = 0;

  void LogOutputs(const Gesture* result, stime_t* timeout, const char* action);
};
}  // namespace gestures

#endif  // GESTURES_INTERPRETER_H__
