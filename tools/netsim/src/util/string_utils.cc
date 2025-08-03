// Copyright 2022 The Android Open Source Project
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

#include "util/string_utils.h"

#include <iostream>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

namespace netsim {
namespace stringutils {
namespace {

const std::string WHITESPACE = " \n\r\t\f\v";
const char hex_digits[] = "0123456789ABCDEF";

}  // namespace

std::string_view LTrim(const std::string_view s) {
  auto start = s.find_first_not_of(WHITESPACE);
  return (start == std::string::npos) ? "" : s.substr(start);
}

std::string_view RTrim(const std::string_view s) {
  size_t end = s.find_last_not_of(WHITESPACE);
  return (end == std::string::npos) ? "" : s.substr(0, end + 1);
}

std::string_view Trim(const std::string_view s) { return RTrim(LTrim(s)); }

std::vector<std::string_view> Split(const std::string_view s,
                                    const std::string_view &delimiters) {
  std::vector<std::string_view> result;
  size_t start, end = 0;
  while ((start = s.find_first_not_of(delimiters, end)) != std::string::npos) {
    end = s.find(delimiters, start);
    result.emplace_back(s.substr(start, end - start));
  }
  return result;
}

std::string ToHexString(uint8_t x, uint8_t y) {
  return {'0',
          'x',
          hex_digits[x >> 4],
          hex_digits[x & 0x0f],
          hex_digits[y >> 4],
          hex_digits[y & 0x0f]};
}

std::string ToHexString(uint8_t x) {
  return {'0', 'x', hex_digits[x >> 4], hex_digits[x & 0x0f]};
}

std::string ToHexString(const uint8_t *buf, size_t len) {
  std::stringstream ss;
  for (int i = 0; i < len; i++) {
    ss << hex_digits[buf[i] >> 4] << hex_digits[buf[i] & 0x0f];
    if (i < len - 1) {
      ss << " ";
    }
  }
  return ss.str();
}

std::string ToHexString(const std::vector<uint8_t> &data, int max_length) {
  std::string result;
  auto length = max_length > data.size() ? data.size() : max_length;
  result.reserve(3 * length + 1);
  for (auto iter = data.begin(); iter < data.begin() + length; iter++) {
    result.push_back(hex_digits[*iter >> 4]);
    result.push_back(hex_digits[*iter & 0x0f]);
    result.push_back(' ');
  }
  if (result.length() > 0) {
    result.pop_back();
  }
  return result;
}

}  // namespace stringutils
}  // namespace netsim
