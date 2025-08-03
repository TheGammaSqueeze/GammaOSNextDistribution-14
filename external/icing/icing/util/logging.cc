// Copyright (C) 2022 Google LLC
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

#include "icing/util/logging.h"

#include <atomic>
#include <exception>
#include <string_view>

#include "icing/proto/debug.pb.h"
#include "icing/util/logging_raw.h"

namespace icing {
namespace lib {
namespace {
// Returns pointer to beginning of last /-separated token from file_name.
// file_name should be a pointer to a zero-terminated array of chars.
// E.g., "foo/bar.cc" -> "bar.cc", "foo/" -> "", "foo" -> "foo".
const char *JumpToBasename(const char *file_name) {
  if (file_name == nullptr) {
    return nullptr;
  }

  // Points to the beginning of the last encountered token.
  size_t last_token_start = std::string_view(file_name).find_last_of('/');
  if (last_token_start == std::string_view::npos) {
    return file_name;
  }
  return file_name + last_token_start + 1;
}

// Calculate the logging level value based on severity and verbosity.
constexpr uint32_t CalculateLoggingLevel(LogSeverity::Code severity,
                                         uint16_t verbosity) {
  uint32_t logging_level = static_cast<uint16_t>(severity);
  logging_level = (logging_level << 16) | verbosity;
  return logging_level;
}

#if defined(ICING_DEBUG_LOGGING)
#define DEFAULT_LOGGING_LEVEL CalculateLoggingLevel(LogSeverity::VERBOSE, 1)
#else
#define DEFAULT_LOGGING_LEVEL CalculateLoggingLevel(LogSeverity::INFO, 0)
#endif

// The current global logging level for Icing, which controls which logs are
// printed based on severity and verbosity.
//
// This needs to be global so that it can be easily accessed from ICING_LOG and
// ICING_VLOG macros spread throughout the entire code base.
//
// The first 16 bits represent the minimal log severity.
// The last 16 bits represent the current verbosity.
std::atomic<uint32_t> global_logging_level = DEFAULT_LOGGING_LEVEL;

}  // namespace

// Whether we should log according to the current logging level.
bool ShouldLog(LogSeverity::Code severity, int16_t verbosity) {
  if (verbosity < 0) {
    return false;
  }
  // Using the relaxed order for better performance because we only need to
  // guarantee the atomicity for this specific statement, without the need to
  // worry about reordering.
  uint32_t curr_logging_level =
      global_logging_level.load(std::memory_order_relaxed);
  // If severity is less than the the threshold set.
  if (static_cast<uint16_t>(severity) < (curr_logging_level >> 16)) {
    return false;
  }
  if (severity == LogSeverity::VERBOSE) {
    // return whether the verbosity is within the current verbose level set.
    return verbosity <= (curr_logging_level & 0xffff);
  }
  return true;
}

bool SetLoggingLevel(LogSeverity::Code severity, int16_t verbosity) {
  if (verbosity < 0) {
    return false;
  }
  if (severity > LogSeverity::VERBOSE && verbosity > 0) {
    return false;
  }
  // Using the relaxed order for better performance because we only need to
  // guarantee the atomicity for this specific statement, without the need to
  // worry about reordering.
  global_logging_level.store(CalculateLoggingLevel(severity, verbosity),
                             std::memory_order_relaxed);
  return true;
}

LogMessage::LogMessage(LogSeverity::Code severity, uint16_t verbosity,
                       const char *file_name, int line_number)
    : severity_(severity),
      verbosity_(verbosity),
      should_log_(ShouldLog(severity_, verbosity_)),
      stream_(should_log_) {
  if (should_log_) {
    stream_ << JumpToBasename(file_name) << ":" << line_number << ": ";
  }
}

LogMessage::~LogMessage() {
  if (should_log_) {
    LowLevelLogging(severity_, kIcingLoggingTag, stream_.message);
  }
  if (severity_ == LogSeverity::FATAL) {
    std::terminate();  // Will print a stacktrace (stdout or logcat).
  }
}
}  // namespace lib
}  // namespace icing
