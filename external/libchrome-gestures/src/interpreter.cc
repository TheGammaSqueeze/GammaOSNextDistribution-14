// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/interpreter.h"

#include <cxxabi.h>
#include <string>

#include <json/value.h>
#include <json/writer.h>

#include "include/activity_log.h"
#include "include/finger_metrics.h"
#include "include/gestures.h"
#include "include/logging.h"
#include "include/tracer.h"

using std::string;

namespace gestures {

using EventDebug = ActivityLog::EventDebug;

Interpreter::Interpreter(PropRegistry* prop_reg,
                         Tracer* tracer,
                         bool force_log_creation)
    : requires_metrics_(false),
      initialized_(false),
      name_(nullptr),
      tracer_(tracer) {
#ifdef DEEP_LOGS
  bool logging_enabled = true;
#else
  bool logging_enabled = force_log_creation;
#endif
  if (logging_enabled)
    log_.reset(new ActivityLog(prop_reg));
}

Interpreter::~Interpreter() {
  if (name_)
    free(const_cast<char*>(name_));
}

void Interpreter::Trace(const char* message, const char* name) {
  if (tracer_)
    tracer_->Trace(message, name);
}

void Interpreter::SyncInterpret(HardwareState& hwstate,
                                    stime_t* timeout) {
  AssertWithReturn(initialized_);
  if (EventLoggingIsEnabled()) {
    Trace("log: start: ", "LogHardwareState");
    log_->LogHardwareState(hwstate);
    Trace("log: end: ", "LogHardwareState");
  }
  if (own_metrics_)
    own_metrics_->Update(hwstate);

  Trace("SyncInterpret: start: ", name());
  SyncInterpretImpl(hwstate, timeout);
  Trace("SyncInterpret: end: ", name());
  LogOutputs(nullptr, timeout, "SyncLogOutputs");
}

void Interpreter::HandleTimer(stime_t now, stime_t* timeout) {
  AssertWithReturn(initialized_);
  if (EventLoggingIsEnabled()) {
    Trace("log: start: ", "LogTimerCallback");
    log_->LogTimerCallback(now);
    Trace("log: end: ", "LogTimerCallback");
  }
  Trace("HandleTimer: start: ", name());
  HandleTimerImpl(now, timeout);
  Trace("HandleTimer: end: ", name());
  LogOutputs(nullptr, timeout, "TimerLogOutputs");
}

void Interpreter::ProduceGesture(const Gesture& gesture) {
  AssertWithReturn(initialized_);
  LogOutputs(&gesture, nullptr, "ProduceGesture");
  consumer_->ConsumeGesture(gesture);
}

void Interpreter::Initialize(const HardwareProperties* hwprops,
                             Metrics* metrics,
                             MetricsProperties* mprops,
                             GestureConsumer* consumer) {
  if (log_.get() && hwprops) {
    Trace("log: start: ", "SetHardwareProperties");
    log_->SetHardwareProperties(*hwprops);
    Trace("log: end: ", "SetHardwareProperties");
  }

  metrics_ = metrics;
  if (requires_metrics_ && metrics == nullptr) {
    own_metrics_.reset(new Metrics(mprops));
    metrics_ = own_metrics_.get();
  }

  hwprops_ = hwprops;
  consumer_ = consumer;
  initialized_ = true;
}

Json::Value Interpreter::EncodeCommonInfo() {
  Json::Value root = log_.get() ?
      log_->EncodeCommonInfo() : Json::Value(Json::objectValue);
  root[ActivityLog::kKeyInterpreterName] = Json::Value(string(name()));
  return root;
}

std::string Interpreter::Encode() {
  Json::Value root = EncodeCommonInfo();
  if (log_.get())
    log_->AddEncodeInfo(&root);

  std::string out = root.toStyledString();
  return out;
}

void Interpreter::InitName() {
  if (!name_) {
    int status;
    char* full_name = abi::__cxa_demangle(typeid(*this).name(), 0, 0, &status);
    if (full_name == nullptr) {
      if (status == -1)
        Err("Memory allocation failed");
      else if (status == -2)
        Err("Mangled_name is not a valid name");
      else if (status == -3)
        Err("One of the arguments is invalid");
      return;
    }
    // the return value of abi::__cxa_demangle(...) is gestures::XXXInterpreter
    char* last_colon = strrchr(full_name, ':');
    char* class_name;
    if (last_colon)
      class_name = last_colon + 1;
    else
      class_name = full_name;
    name_ = strdup(class_name);
    free(full_name);
  }
}

bool Interpreter::EventLoggingIsEnabled() {
  return enable_event_logging_ && log_.get();
}

void Interpreter::SetEventLoggingEnabled(bool enabled) {
  // TODO(b/185844310): log an event when touch logging is enabled or disabled.
  enable_event_logging_ = enabled;
}

bool Interpreter::EventDebugLoggingIsEnabled(ActivityLog::EventDebug event) {
  return EventLoggingIsEnabled() &&
         (enable_event_debug_logging_ & (1 << static_cast<int>(event)));
}

uint32_t Interpreter::GetEventDebugLoggingEnabled() {
  return enable_event_debug_logging_;
}

void Interpreter::SetEventDebugLoggingEnabled(uint32_t enabled) {
  enable_event_debug_logging_ = enabled;
}

void Interpreter::EventDebugLoggingDisable(ActivityLog::EventDebug event) {
  enable_event_debug_logging_ &= ~(1 << static_cast<int>(event));
}

void Interpreter::EventDebugLoggingEnable(ActivityLog::EventDebug event) {
  enable_event_debug_logging_ |= (1 << static_cast<int>(event));
}

void Interpreter::LogOutputs(const Gesture* result,
                             stime_t* timeout,
                             const char* action) {
  if (!EventLoggingIsEnabled())
    return;
  Trace("log: start: ", action);
  if (result)
    log_->LogGesture(*result);
  if (timeout && *timeout >= 0.0)
    log_->LogCallbackRequest(*timeout);
  Trace("log: end: ", action);
}

void Interpreter::LogGestureConsume(
    const std::string& name, const Gesture& gesture) {
  if (EventDebugLoggingIsEnabled(EventDebug::Gesture))
    log_->LogGestureConsume(name, gesture);
}

void Interpreter::LogGestureProduce(
    const std::string& name, const Gesture& gesture) {
  if (EventDebugLoggingIsEnabled(EventDebug::Gesture))
    log_->LogGestureProduce(name, gesture);
}

void Interpreter::LogHardwareStatePre(
    const std::string& name, const HardwareState& hwstate) {
  if (EventDebugLoggingIsEnabled(EventDebug::HardwareState))
    log_->LogHardwareStatePre(name, hwstate);
}

void Interpreter::LogHardwareStatePost(
    const std::string& name, const HardwareState& hwstate) {
  if (EventDebugLoggingIsEnabled(EventDebug::HardwareState))
    log_->LogHardwareStatePost(name, hwstate);
}

void Interpreter::LogHandleTimerPre(
    const std::string& name, stime_t now, const stime_t* timeout) {
  if (EventDebugLoggingIsEnabled(EventDebug::HandleTimer))
    log_->LogHandleTimerPre(name, now, timeout);
}

void Interpreter::LogHandleTimerPost(
    const std::string& name, stime_t now, const stime_t* timeout) {
  if (EventDebugLoggingIsEnabled(EventDebug::HandleTimer))
    log_->LogHandleTimerPost(name, now, timeout);
}

}  // namespace gestures
