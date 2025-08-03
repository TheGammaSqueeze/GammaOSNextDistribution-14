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

#ifdef __ANDROID__
#include <android-base/logging.h>
#include <log/log.h>
#endif

#include <ditto/logger.h>

#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include <iostream>
#include <sstream>
#include <vector>

namespace dittosuite {

Logger& Logger::GetInstance() {
  static Logger logger;
  return logger;
}

void Logger::SetLogLevel(const LogLevel log_level) {
  log_level_ = log_level;
}

void Logger::SetLogStream(const LogStream log_stream) {
  log_stream_ = log_stream;
}

LogLevel Logger::GetLogLevel() const {
  return log_level_;
}

LogStream Logger::GetLogStream() const {
  return log_stream_;
}

std::string LogLevelToString(const LogLevel log_level) {
  static const std::string prefixes[] = {"VERBOSE", "DEBUG", "INFO", "WARNING", "ERROR", "FATAL"};
  return prefixes[static_cast<int>(log_level)];
}

#ifdef __ANDROID__
android::base::LogSeverity LogLevelToAndroidLogLevel(const LogLevel log_level) {
  switch (log_level) {
    case LogLevel::kVerbose:
      return android::base::VERBOSE;
    case LogLevel::kDebug:
      return android::base::DEBUG;
    case LogLevel::kInfo:
      return android::base::INFO;
    case LogLevel::kWarning:
      return android::base::WARNING;
    case LogLevel::kError:
      return android::base::ERROR;
    case LogLevel::kFatal:
      return android::base::FATAL;
  }
}
#endif

void Logger::WriteLogMessage(const LogLevel log_level, const std::string& message,
                             const std::string& file_name, int line, bool print_errno) {
  std::stringstream ss;
  ss << file_name << ":" << line << ": " << LogLevelToString(log_level) << ": " << message;
  if (print_errno) {
    ss << ": " << strerror(errno);
  }
  switch (log_stream_) {
    case LogStream::kLogcat:
#ifdef __ANDROID__
      LOG(LogLevelToAndroidLogLevel(log_level)) << ss.str();
      break;
#else
      [[fallthrough]];
#endif
    case LogStream::kStdout:
      std::cout << ss.str() << std::endl;
      break;
  }
}

}  // namespace dittosuite
