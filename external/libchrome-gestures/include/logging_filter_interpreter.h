// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <gtest/gtest.h>
#include <string>

#include "include/activity_log.h"
#include "include/gestures.h"
#include "include/filter_interpreter.h"
#include "include/prop_registry.h"
#include "include/tracer.h"

#ifndef GESTURES_LOGGING_FILTER_INTERPRETER_H_
#define GESTURES_LOGGING_FILTER_INTERPRETER_H_

namespace gestures {
// This interpreter keeps an ActivityLog of everything that happens, and can
// log it when requested.

class LoggingFilterInterpreter : public FilterInterpreter,
                                 public PropertyDelegate {
  FRIEND_TEST(ActivityReplayTest, DISABLED_SimpleTest);
  FRIEND_TEST(LoggingFilterInterpreterTest, LogResetHandlerTest);
 public:
  // Takes ownership of |next|:
  LoggingFilterInterpreter(PropRegistry* prop_reg, Interpreter* next,
                           Tracer* tracer);
  virtual ~LoggingFilterInterpreter() {}

  virtual void BoolWasWritten(BoolProperty* prop);
  virtual void IntWasWritten(IntProperty* prop);

  std::string EncodeActivityLog();

 private:
  void Dump(const char* filename);

  IntProperty event_debug_logging_enable_;
  BoolProperty event_logging_enable_;
  IntProperty logging_notify_;
  // Reset the log by setting the property value.
  IntProperty logging_reset_;
  StringProperty log_location_;

  // This property is unused by this library, but we need a place to stick it.
  // If true, this device is an integrated touchpad, as opposed to an external
  // device.
  BoolProperty integrated_touchpad_;
};
}  // namespace gestures

#endif  // GESTURES_LOGGING_FILTER_INTERPRETER_H_
