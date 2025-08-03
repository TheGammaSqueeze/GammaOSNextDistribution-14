/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <syslog.h>

#include <string>
#include <unordered_map>

#include "bluetooth/log.h"
#include "truncating_buffer.h"

namespace bluetooth::log_internal {

// Map of tags with custom levels.
std::unordered_map<std::string, Level>& GetTagMap() {
  static std::unordered_map<std::string, Level> tag_level_map;
  return tag_level_map;
}

// Default log level.
Level gDefaultLogLevel = Level::kInfo;

Level GetLogLevelForTag(char const* tag) {
  auto tag_map = GetTagMap();
  auto find = tag_map.find(tag);
  if (find != tag_map.end()) {
    return find->second;
  } else {
    return gDefaultLogLevel;
  }
}

Level GetDefaultLogLevel() { return gDefaultLogLevel; }

// Default value for $MaxMessageSize for rsyslog.
static constexpr size_t kBufferSize = 8192;

void vlog(Level level, char const* tag, char const* file_name, int line,
          char const* function_name, fmt::string_view fmt,
          fmt::format_args vargs) {
  // Filter out logs that don't meet level requirement.
  Level current_level = GetLogLevelForTag(tag);
  if (level < current_level) {
    return;
  }

  // Convert the level to syslog severity.
  int severity = LOG_DEBUG;
  switch (level) {
    case Level::kVerbose:
    case Level::kDebug:
    default:
      severity = LOG_DEBUG;
      break;
    case Level::kInfo:
      severity = LOG_INFO;
      break;
    case Level::kWarn:
      severity = LOG_WARNING;
      break;
    case Level::kError:
      severity = LOG_ERR;
      break;
    case Level::kFatal:
      severity = LOG_CRIT;
      break;
  }

  // Prepare bounded stack buffer.
  truncating_buffer<kBufferSize> buffer;

  // Format file, line.
  fmt::format_to(std::back_insert_iterator(buffer), "{} {}:{} {}: ", tag,
                 file_name, line, function_name);

  // Format message.
  fmt::vformat_to(std::back_insert_iterator(buffer), fmt, vargs);

  // Print to vsyslog.
  syslog(LOG_USER | severity, "%s", buffer.c_str());
}

}  // namespace bluetooth::log_internal

// These apis will be exposed in topshim to allow control of syslog log levels.
extern "C" {
void SetLogLevelForTag(char const* tag, uint8_t level) {
  if (level < bluetooth::log_internal::Level::kVerbose ||
      level > bluetooth::log_internal::Level::kFatal) {
    level = bluetooth::log_internal::GetDefaultLogLevel();
  }

  bluetooth::log_internal::GetTagMap().emplace(
      tag, static_cast<bluetooth::log_internal::Level>(level));
}

void SetDefaultLogLevel(uint8_t level) {
  if (level < bluetooth::log_internal::Level::kVerbose ||
      level > bluetooth::log_internal::Level::kFatal) {
    return;
  }

  bluetooth::log_internal::gDefaultLogLevel =
      static_cast<bluetooth::log_internal::Level>(level);
}
}
