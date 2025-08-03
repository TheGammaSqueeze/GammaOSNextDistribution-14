// Copyright (C) 2019 Google LLC
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

#ifndef ICING_UTIL_LOGGING_H_
#define ICING_UTIL_LOGGING_H_

#include <atomic>
#include <cstdint>
#include <string>

#include "icing/proto/debug.pb.h"

// This header provides base/logging.h style macros, ICING_LOG and ICING_VLOG,
// for logging in various platforms. The macros use __android_log_write on
// Android, and log to stdout/stderr on others. It also provides a function
// SetLoggingLevel to control the log severity level for ICING_LOG and verbosity
// for ICING_VLOG.
namespace icing {
namespace lib {

// Whether we should log according to the current logging level.
// The function will always return false when verbosity is negative.
bool ShouldLog(LogSeverity::Code severity, int16_t verbosity = 0);

// Set the minimal logging severity to be enabled, and the verbose level to see
// from the logs.
// Return false if severity is set higher than VERBOSE but verbosity is not 0.
// The function will always return false when verbosity is negative.
bool SetLoggingLevel(LogSeverity::Code severity, int16_t verbosity = 0);

// A tiny code footprint string stream for assembling log messages.
struct LoggingStringStream {
  explicit LoggingStringStream(bool should_log) : should_log_(should_log) {}
  LoggingStringStream& stream() { return *this; }

  std::string message;
  const bool should_log_;
};

template <typename T>
inline LoggingStringStream& operator<<(LoggingStringStream& stream,
                                       const T& entry) {
  if (stream.should_log_) {
    stream.message.append(std::to_string(entry));
  }
  return stream;
}

template <typename T>
inline LoggingStringStream& operator<<(LoggingStringStream& stream,
                                       T* const entry) {
  if (stream.should_log_) {
    stream.message.append(
        std::to_string(reinterpret_cast<const uint64_t>(entry)));
  }
  return stream;
}

inline LoggingStringStream& operator<<(LoggingStringStream& stream,
                                       const char* message) {
  if (stream.should_log_) {
    stream.message.append(message);
  }
  return stream;
}

inline LoggingStringStream& operator<<(LoggingStringStream& stream,
                                       const std::string& message) {
  if (stream.should_log_) {
    stream.message.append(message);
  }
  return stream;
}

inline LoggingStringStream& operator<<(LoggingStringStream& stream,
                                       std::string_view message) {
  if (stream.should_log_) {
    stream.message.append(message);
  }
  return stream;
}

template <typename T1, typename T2>
inline LoggingStringStream& operator<<(LoggingStringStream& stream,
                                       const std::pair<T1, T2>& entry) {
  if (stream.should_log_) {
    stream << "(" << entry.first << ", " << entry.second << ")";
  }
  return stream;
}

// The class that does all the work behind our ICING_LOG(severity) macros.  Each
// ICING_LOG(severity) << obj1 << obj2 << ...; logging statement creates a
// LogMessage temporary object containing a stringstream.  Each operator<< adds
// info to that stringstream and the LogMessage destructor performs the actual
// logging.  The reason this works is that in C++, "all temporary objects are
// destroyed as the last step in evaluating the full-expression that (lexically)
// contains the point where they were created."  For more info, see
// http://en.cppreference.com/w/cpp/language/lifetime.  Hence, the destructor is
// invoked after the last << from that logging statement.
class LogMessage {
 public:
  LogMessage(LogSeverity::Code severity, uint16_t verbosity,
             const char* file_name, int line_number) __attribute__((noinline));

  ~LogMessage() __attribute__((noinline));

  // Returns the stream associated with the logger object.
  LoggingStringStream& stream() { return stream_; }

 private:
  const LogSeverity::Code severity_;
  const uint16_t verbosity_;
  const bool should_log_;

  // Stream that "prints" all info into a string (not to a file).  We construct
  // here the entire logging message and next print it in one operation.
  LoggingStringStream stream_;
};

inline constexpr char kIcingLoggingTag[] = "AppSearchIcing";

// Define consts to make it easier to refer to log severities in code.
constexpr ::icing::lib::LogSeverity::Code VERBOSE =
    ::icing::lib::LogSeverity::VERBOSE;

constexpr ::icing::lib::LogSeverity::Code DBG = ::icing::lib::LogSeverity::DBG;

constexpr ::icing::lib::LogSeverity::Code INFO =
    ::icing::lib::LogSeverity::INFO;

constexpr ::icing::lib::LogSeverity::Code WARNING =
    ::icing::lib::LogSeverity::WARNING;

constexpr ::icing::lib::LogSeverity::Code ERROR =
    ::icing::lib::LogSeverity::ERROR;

constexpr ::icing::lib::LogSeverity::Code FATAL =
    ::icing::lib::LogSeverity::FATAL;

#define ICING_VLOG(verbose_level) \
  ::icing::lib::LogMessage(VERBOSE, verbose_level, __FILE__, __LINE__).stream()

#define ICING_LOG(severity)                                               \
  ::icing::lib::LogMessage(severity, /*verbosity=*/0, __FILE__, __LINE__) \
      .stream()

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_LOGGING_H_
