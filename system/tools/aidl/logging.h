/*
 * Copyright (C) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <iostream>
#include <memory>
#include <string>

#include "location.h"

class AidlNode;

// Generic point for printing any error in the AIDL compiler.
class AidlErrorLog {
 public:
  enum Severity { NO_OP, WARNING, ERROR, FATAL };

  AidlErrorLog(Severity severity, const AidlLocation& location, const std::string& suffix = "");
  AidlErrorLog(Severity severity, const std::string& filename);
  AidlErrorLog(Severity severity, const AidlNode& node);
  AidlErrorLog(Severity severity, const AidlNode* node) : AidlErrorLog(severity, *node) {}

  template <typename T>
  AidlErrorLog(Severity severity, const std::unique_ptr<T>& node) : AidlErrorLog(severity, *node) {}
  ~AidlErrorLog();

  // AidlErrorLog is a single use object. No need to copy
  AidlErrorLog(const AidlErrorLog&) = delete;
  AidlErrorLog& operator=(const AidlErrorLog&) = delete;

  // btw, making it movable so that functions can return it.
  AidlErrorLog(AidlErrorLog&&) = default;
  AidlErrorLog& operator=(AidlErrorLog&&) = delete;

  template <typename T>
  AidlErrorLog& operator<<(T&& arg) {
    if (severity_ != NO_OP) {
      (*os_) << std::forward<T>(arg);
    }
    return *this;
  }

  static void clearError() { sHadError = false; }
  static bool hadError() { return sHadError; }

 private:
  std::ostream* os_;
  Severity severity_;
  const AidlLocation location_;
  const std::string suffix_;
  static bool sHadError;
};

// A class used to make it obvious to clang that code is going to abort. This
// informs static analyses of the aborting behavior of `AIDL_FATAL`, and
// helps generate slightly faster/smaller code.
class AidlAbortOnDestruction {
 public:
  __attribute__((noreturn)) ~AidlAbortOnDestruction() { abort(); }
};

#define AIDL_ERROR(CONTEXT) ::AidlErrorLog(AidlErrorLog::ERROR, (CONTEXT))
#define AIDL_FATAL(CONTEXT) \
  (::AidlAbortOnDestruction(), ::AidlErrorLog(AidlErrorLog::FATAL, (CONTEXT)))
#define AIDL_FATAL_IF(CONDITION, CONTEXT) \
  if (CONDITION) AIDL_FATAL(CONTEXT) << "Bad internal state: " << #CONDITION << ": "
