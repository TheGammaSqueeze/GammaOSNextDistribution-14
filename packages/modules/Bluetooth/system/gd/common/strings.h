/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <limits.h>
#include <string.h>

#include <charconv>
#include <chrono>
#include <iomanip>
#include <iterator>
#include <limits>
#include <optional>
#include <sstream>
#include <string>
#include <type_traits>
#include <vector>

#include "common/type_helper.h"
#include "os/log.h"

namespace bluetooth {
namespace common {

template <typename T>
inline std::string ToString(const T& value) {
  std::stringstream tmp;
  tmp << value;
  return tmp.str();
}

// Convert number into a hex string prefixed with 0x
template <typename T>
std::string ToHexString(T x) {
  if (x < 0) {
    if (x == INT_MIN) return "INT_MIN";
    return "-" + ToHexString(-x);
  }
  std::stringstream tmp;
  tmp << "0x" << std::internal << std::hex << std::setfill('0') << std::setw(sizeof(T) * 2) << (unsigned long)x;
  return tmp.str();
}

template <>
inline std::string ToHexString<>(signed long x) {
  if (x < 0) {
    if (x == LONG_MIN) return "LONG_MIN";
    return "-" + ToHexString<signed long>(-x);
  }
  std::stringstream tmp;
  tmp << "0x" << std::internal << std::hex << std::setfill('0') << std::setw(sizeof(signed long) * 2)
      << (unsigned long)x;
  return tmp.str();
}

template <>
inline std::string ToHexString<>(unsigned int x) {
  std::stringstream tmp;
  tmp << "0x" << std::internal << std::hex << std::setfill('0') << std::setw(sizeof(unsigned int) * 2)
      << (unsigned long)x;
  return tmp.str();
}

// Convert value into a hex decimal formatted string in lower case, prefixed with 0s
template <class InputIt>
std::string ToHexString(InputIt first, InputIt last) {
  static_assert(
      std::is_same_v<typename std::iterator_traits<InputIt>::value_type, uint8_t>, "Must use uint8_t iterator");
  std::stringstream ss;
  for (InputIt it = first; it != last; ++it) {
    // +(byte) to prevent an uint8_t to be interpreted as a char
    ss << std::hex << std::setw(2) << std::setfill('0') << +(*it);
  }
  return ss.str();
}
// Convenience method for normal cases and initializer list, e.g. ToHexString({0x12, 0x34, 0x56, 0xab})
std::string ToHexString(const std::vector<uint8_t>& value);

// Return true if |str| is a valid hex demical strings contains only hex decimal chars [0-9a-fA-F]
bool IsValidHexString(const std::string& str);

// Parse |str| into a vector of uint8_t, |str| must contains only hex decimal
std::optional<std::vector<uint8_t>> FromHexString(const std::string& str);

// Remove whitespace from both ends of the |str|, returning a copy
std::string StringTrim(std::string str);

// Split |str| into at most |max_token| tokens delimited by |delim|, unlimited tokens when |max_token| is 0
std::vector<std::string> StringSplit(const std::string& str, const std::string& delim, size_t max_token = 0);

// Join |strings| into a single string using |delim|
std::string StringJoin(const std::vector<std::string>& strings, const std::string& delim);

// Various number comparison functions, only base 10 is supported
std::optional<int64_t> Int64FromString(const std::string& str);
std::string ToString(int64_t value);
std::optional<uint64_t> Uint64FromString(const std::string& str);
std::string ToString(uint64_t value);
std::optional<bool> BoolFromString(const std::string& str);
std::string ToString(bool value);

// Migrate this method to std::format when C++20 becomes available
// printf like formatting to std::string
// format must contains format information, to print a string use StringFormat("%s", str)
template <typename... Args>
std::string StringFormat(const std::string& format, Args... args) {
  auto size = std::snprintf(nullptr, 0, format.c_str(), args...);
  ASSERT_LOG(size >= 0, "return value %d, error %d, text '%s'", size, errno, strerror(errno));
  // Add 1 for terminating null byte
  std::vector<char> buffer(size + 1);
  auto actual_size = std::snprintf(buffer.data(), buffer.size(), format.c_str(), args...);
  ASSERT_LOG(
      size == actual_size,
      "asked size %d, actual size %d, error %d, text '%s'",
      size,
      actual_size,
      errno,
      strerror(errno));
  // Exclude the terminating null byte
  return std::string(buffer.data(), size);
}

inline std::string StringFormatTime(const std::string& format, const struct std::tm& tm) {
  std::ostringstream os;
  os << std::put_time(&tm, format.c_str());
  return os.str();
}

inline std::string StringFormatTimeWithMilliseconds(
    const std::string& format,
    std::chrono::time_point<std::chrono::system_clock> time_point,
    struct tm* (*calendar_to_tm)(const time_t* timep) = localtime) {
  std::time_t epoch_time = std::chrono::system_clock::to_time_t(time_point);
  auto millis = time_point.time_since_epoch() / std::chrono::milliseconds(1) % 1000;
  std::tm tm = *calendar_to_tm(&epoch_time);
  std::ostringstream os;
  os << std::put_time(&tm, format.c_str()) << StringFormat(".%03u", millis);
  return os.str();
}

}  // namespace common
}  // namespace bluetooth
