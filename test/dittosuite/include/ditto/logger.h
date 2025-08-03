// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <stdio.h>

#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace dittosuite {

enum class LogLevel { kVerbose, kDebug, kInfo, kWarning, kError, kFatal };

enum class LogStream { kStdout, kLogcat };

class Logger {
 public:
  Logger(Logger const&) = delete;
  void operator=(Logger const&) = delete;
  static Logger& GetInstance();
  void SetLogLevel(LogLevel log_level);
  void SetLogStream(LogStream log_stream);
  LogLevel GetLogLevel() const;
  LogStream GetLogStream() const;
  void WriteLogMessage(LogLevel log_level, const std::string& message, const std::string& file_name,
                       int line, bool print_errno);

 protected:
  Logger() : log_level_(LogLevel::kInfo) {}

 private:
  LogLevel log_level_;
  LogStream log_stream_;
};

LogLevel ArgToLogLevel(const std::string_view optarg);

}  // namespace dittosuite

#define DITTO_LOGGER dittosuite::Logger::GetInstance()

#define DITTO_LOG(VERBOSITY, X, print_errno)                                               \
  do {                                                                                     \
    if (DITTO_LOGGER.GetLogLevel() <= dittosuite::LogLevel::VERBOSITY) {                   \
      DITTO_LOGGER.WriteLogMessage(dittosuite::LogLevel::VERBOSITY, X, __FILE__, __LINE__, \
                                   print_errno);                                           \
    }                                                                                      \
  } while (false)

#define LOGF(X)                  \
  do {                           \
    DITTO_LOG(kFatal, X, false); \
    exit(EXIT_FAILURE);          \
  } while (false)
#define LOGE(X) DITTO_LOG(kError, X, false)
#define LOGW(X) DITTO_LOG(kWarning, X, false)
#define LOGI(X) DITTO_LOG(kInfo, X, false)
#define LOGD(X) DITTO_LOG(kDebug, X, false)
#define LOGV(X) DITTO_LOG(kVerbose, X, false)

#define PLOGF(X)                \
  do {                          \
    DITTO_LOG(kFatal, X, true); \
    exit(EXIT_FAILURE);         \
  } while (false)
#define PLOGE(X) DITTO_LOG(kError, X, true)
#define PLOGW(X) DITTO_LOG(kWarning, X, true)
#define PLOGI(X) DITTO_LOG(kInfo, X, true)
#define PLOGD(X) DITTO_LOG(kDebug, X, true)
#define PLOGV(X) DITTO_LOG(kVerbose, X, true)
