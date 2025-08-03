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

#include "common/strings.h"

#include <algorithm>
#include <charconv>
#include <cstdlib>
#include <functional>
#include <iomanip>
#include <iterator>
#include <sstream>
#include <system_error>

namespace {

struct IsSpace {
  bool operator()(std::string::value_type v) {
    return isspace(static_cast<int>(v));
  }
};

struct IsHexDigit {
  bool operator()(std::string::value_type v) {
    return isxdigit(static_cast<int>(v));
  }
};

}  // namespace

namespace bluetooth {
namespace common {

std::string ToHexString(const std::vector<uint8_t>& value) {
  return ToHexString(value.begin(), value.end());
}

bool IsValidHexString(const std::string& str) {
  return std::find_if_not(str.begin(), str.end(), IsHexDigit{}) == str.end();
}

std::optional<std::vector<uint8_t>> FromHexString(const std::string& str) {
  if (str.size() % 2 != 0) {
    LOG_INFO("str size is not divisible by 2, size is %zu", str.size());
    return std::nullopt;
  }
  if (std::find_if_not(str.begin(), str.end(), IsHexDigit{}) != str.end()) {
    LOG_INFO("value contains none hex digit");
    return std::nullopt;
  }
  std::vector<uint8_t> value;
  value.reserve(str.size() / 2);
  for (size_t i = 0; i < str.size(); i += 2) {
    uint8_t v = 0;
    auto ret = std::from_chars(str.c_str() + i, str.c_str() + i + 2, v, 16);
    if (std::make_error_code(ret.ec)) {
      LOG_INFO("failed to parse hex char at index %zu", i);
      return std::nullopt;
    }
    value.push_back(v);
  }
  return value;
}

std::string StringTrim(std::string str) {
  str.erase(str.begin(), std::find_if_not(str.begin(), str.end(), IsSpace{}));
  str.erase(std::find_if_not(str.rbegin(), str.rend(), IsSpace{}).base(), str.end());
  return str;
}

std::vector<std::string> StringSplit(const std::string& str, const std::string& delim, size_t max_token) {
  ASSERT_LOG(!delim.empty(), "delim cannot be empty");
  std::vector<std::string> tokens;
  // Use std::string::find and std::string::substr to avoid copying str into a stringstream
  std::string::size_type starting_index = 0;
  auto index_of_delim = str.find(delim);
  while ((max_token == 0 || tokens.size() < (max_token - 1)) && index_of_delim != std::string::npos) {
    tokens.push_back(str.substr(starting_index, index_of_delim - starting_index));
    starting_index = index_of_delim + delim.size();
    index_of_delim = str.find(delim, starting_index);
  }
  // Append last item to the vector if there are anything left
  if (starting_index < (str.size() + 1)) {
    tokens.push_back(str.substr(starting_index));
  }
  return tokens;
}

std::string StringJoin(const std::vector<std::string>& strings, const std::string& delim) {
  std::stringstream ss;
  for (auto it = strings.begin(); it != strings.end(); it++) {
    ss << *it;
    if (std::next(it) != strings.end()) {
      ss << delim;
    }
  }
  return ss.str();
}

std::optional<int64_t> Int64FromString(const std::string& str) {
  char* ptr = nullptr;
  errno = 0;
  int64_t value = std::strtoll(str.c_str(), &ptr, 10);
  if (errno != 0) {
    LOG_INFO("cannot parse string '%s' with error '%s'", str.c_str(), strerror(errno));
    return std::nullopt;
  }
  if (ptr == str.c_str()) {
    LOG_INFO("string '%s' is empty or has wrong format", str.c_str());
    return std::nullopt;
  }
  if (ptr != (str.c_str() + str.size())) {
    LOG_INFO("cannot parse whole string '%s'", str.c_str());
    return std::nullopt;
  }
  return value;
}

std::string ToString(int64_t value) {
  return std::to_string(value);
}

std::optional<uint64_t> Uint64FromString(const std::string& str) {
  if (str.find('-') != std::string::npos) {
    LOG_INFO("string '%s' contains minus sign, this function is for unsigned", str.c_str());
    return std::nullopt;
  }
  char* ptr = nullptr;
  errno = 0;
  uint64_t value = std::strtoull(str.c_str(), &ptr, 10);
  if (errno != 0) {
    LOG_INFO("cannot parse string '%s' with error '%s'", str.c_str(), strerror(errno));
    return std::nullopt;
  }
  if (ptr == str.c_str()) {
    LOG_INFO("string '%s' is empty or has wrong format", str.c_str());
    return std::nullopt;
  }
  if (ptr != (str.c_str() + str.size())) {
    LOG_INFO("cannot parse whole string '%s'", str.c_str());
    return std::nullopt;
  }
  return value;
}

std::string ToString(uint64_t value) {
  return std::to_string(value);
}

std::optional<bool> BoolFromString(const std::string& str) {
  if (str == "true") {
    return true;
  } else if (str == "false") {
    return false;
  } else {
    LOG_INFO("string '%s' is neither true nor false", str.c_str());
    return std::nullopt;
  }
}

std::string ToString(bool value) {
  return value ? "true" : "false";
}

}  // namespace common
}  // namespace bluetooth
