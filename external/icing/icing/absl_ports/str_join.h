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

#ifndef ICING_ABSL_PORTS_STR_JOIN_H_
#define ICING_ABSL_PORTS_STR_JOIN_H_

#include <string>
#include <string_view>
#include <vector>

#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {
namespace absl_ports {

class DefaultFormatter {
 public:
  template <typename T>
  std::string operator()(const T& element) {
    return std::string(element);
  }
};

class NumberFormatter {
 public:
  template <typename T>
  std::string operator()(const T& number) {
    return std::to_string(number);
  }
};

// A port of absl::StrJoin.
//
// Joins a range of elements and returns the result as a std::string.
// `absl::StrJoin()` takes a range, a separator string to use between the
// elements joined.
//
// A Formatter may be supplied to convert the Iterator's elements to a
// std::string.
template <typename Iterator, typename Formatter>
std::string StrJoin(Iterator first, Iterator last, std::string_view sep,
                    Formatter&& formatter) {
  std::string::size_type result_size = 0;
  bool add_separator_before_element = false;
  for (Iterator current = first; current != last; ++current) {
    if (add_separator_before_element) {
      result_size += sep.length();
    }

    std::string formatted = formatter(*current);
    result_size += formatted.length();

    add_separator_before_element = true;
  }
  // Create result with enough room to fit all operands.
  std::string result;
  // __resize_default_init is provided by libc++ >= 8.0 and allows us to
  // allocate room for the content we're about to copy while avoiding the
  // unnecessary zero-initialization that the normal std::string::resize will
  // perform.
  //
  // The current absl implementation copies a null char to the character at
  // previous_size after the call to resize_default_init due to implementation
  // differences between libstdc++ and libc++. That behavior is NOT copied over
  // here because the following lines are just about to overwrite that character
  // anyways.
  result.__resize_default_init(result_size);

  add_separator_before_element = false;
  for (char* out = &result[0]; first != last; ++first) {
    if (add_separator_before_element) {
      out = Append(out, sep);
    }

    std::string formatted = formatter(*first);
    out = Append(out, formatted);

    add_separator_before_element = true;
  }

  return result;
}

template <typename Iterator>
std::string StrJoin(Iterator first, Iterator last, std::string_view sep) {
  return absl_ports::StrJoin(first, last, sep, DefaultFormatter());
}

template <typename Container, typename Formatter>
std::string StrJoin(const Container& container, std::string_view sep,
                    Formatter&& formatter) {
  return absl_ports::StrJoin(std::begin(container), std::end(container), sep,
                             formatter);
}

template <typename Container>
std::string StrJoin(const Container& container, std::string_view sep) {
  return absl_ports::StrJoin(container, sep, DefaultFormatter());
}

std::vector<std::string_view> StrSplit(std::string_view text,
                                       std::string_view sep);

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing

#endif  // ICING_ABSL_PORTS_STR_JOIN_H_