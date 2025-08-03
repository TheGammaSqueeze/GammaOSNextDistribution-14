/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_LIBARTTOOLS_INCLUDE_TOOLS_CMDLINE_BUILDER_H_
#define ART_LIBARTTOOLS_INCLUDE_TOOLS_CMDLINE_BUILDER_H_

#include <algorithm>
#include <iterator>
#include <string>
#include <string_view>
#include <vector>

#include "android-base/stringprintf.h"

namespace art {
namespace tools {

namespace internal {

constexpr bool ContainsOneFormatSpecifier(std::string_view format, char specifier) {
  int count = 0;
  size_t pos = 0;
  while ((pos = format.find('%', pos)) != std::string_view::npos) {
    if (pos == format.length() - 1) {
      // Invalid trailing '%'.
      return false;
    }
    if (format[pos + 1] == specifier) {
      count++;
    } else if (format[pos + 1] != '%') {
      // "%%" is okay. Otherwise, it's a wrong specifier.
      return false;
    }
    pos += 2;
  }
  return count == 1;
}

}  // namespace internal

// A util class that builds cmdline arguments.
class CmdlineBuilder {
 public:
  // Returns all arguments.
  const std::vector<std::string>& Get() const { return elements_; }

  // Adds an argument as-is.
  CmdlineBuilder& Add(std::string_view arg) {
    elements_.push_back(std::string(arg));
    return *this;
  }

  // Same as above but adds a runtime argument.
  CmdlineBuilder& AddRuntime(std::string_view arg) { return Add("--runtime-arg").Add(arg); }

  // Adds a string value formatted by the format string.
  //
  // Usage: Add("--flag=%s", "value")
  CmdlineBuilder& Add(const char* arg_format, const std::string& value)
      __attribute__((enable_if(internal::ContainsOneFormatSpecifier(arg_format, 's'),
                               "'arg' must be a string literal that contains '%s'"))) {
    return Add(android::base::StringPrintf(arg_format, value.c_str()));
  }

  // Same as above but adds a runtime argument.
  CmdlineBuilder& AddRuntime(const char* arg_format, const std::string& value)
      __attribute__((enable_if(internal::ContainsOneFormatSpecifier(arg_format, 's'),
                               "'arg' must be a string literal that contains '%s'"))) {
    return AddRuntime(android::base::StringPrintf(arg_format, value.c_str()));
  }

  // Adds an integer value formatted by the format string.
  //
  // Usage: Add("--flag=%d", 123)
  CmdlineBuilder& Add(const char* arg_format, int value)
      __attribute__((enable_if(internal::ContainsOneFormatSpecifier(arg_format, 'd'),
                               "'arg' must be a string literal that contains '%d'"))) {
    return Add(android::base::StringPrintf(arg_format, value));
  }

  // Same as above but adds a runtime argument.
  CmdlineBuilder& AddRuntime(const char* arg_format, int value)
      __attribute__((enable_if(internal::ContainsOneFormatSpecifier(arg_format, 'd'),
                               "'arg' must be a string literal that contains '%d'"))) {
    return AddRuntime(android::base::StringPrintf(arg_format, value));
  }

  // Adds a string value formatted by the format string if the value is non-empty. Does nothing
  // otherwise.
  //
  // Usage: AddIfNonEmpty("--flag=%s", "value")
  CmdlineBuilder& AddIfNonEmpty(const char* arg_format, const std::string& value)
      __attribute__((enable_if(internal::ContainsOneFormatSpecifier(arg_format, 's'),
                               "'arg' must be a string literal that contains '%s'"))) {
    if (!value.empty()) {
      Add(android::base::StringPrintf(arg_format, value.c_str()));
    }
    return *this;
  }

  // Same as above but adds a runtime argument.
  CmdlineBuilder& AddRuntimeIfNonEmpty(const char* arg_format, const std::string& value)
      __attribute__((enable_if(internal::ContainsOneFormatSpecifier(arg_format, 's'),
                               "'arg' must be a string literal that contains '%s'"))) {
    if (!value.empty()) {
      AddRuntime(android::base::StringPrintf(arg_format, value.c_str()));
    }
    return *this;
  }

  // Adds an argument as-is if the boolean value is true. Does nothing otherwise.
  CmdlineBuilder& AddIf(bool value, std::string_view arg) {
    if (value) {
      Add(arg);
    }
    return *this;
  }

  // Same as above but adds a runtime argument.
  CmdlineBuilder& AddRuntimeIf(bool value, std::string_view arg) {
    if (value) {
      AddRuntime(arg);
    }
    return *this;
  }

  // Concatenates this builder with another. Returns the concatenated result and nullifies the input
  // builder.
  CmdlineBuilder& Concat(CmdlineBuilder&& other) {
    elements_.reserve(elements_.size() + other.elements_.size());
    std::move(other.elements_.begin(), other.elements_.end(), std::back_inserter(elements_));
    other.elements_.clear();
    return *this;
  }

 private:
  std::vector<std::string> elements_;
};

}  // namespace tools
}  // namespace art

#endif  // ART_LIBARTTOOLS_INCLUDE_TOOLS_CMDLINE_BUILDER_H_
