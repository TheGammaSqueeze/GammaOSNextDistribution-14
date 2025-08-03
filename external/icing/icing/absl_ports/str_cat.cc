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

#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {
namespace absl_ports {

char* Append(char* out, std::string_view s) {
  if (!s.empty()) {
    memcpy(out, s.data(), s.length());
    out += s.length();
  }
  return out;
}

std::string StrCat(std::string_view a, std::string_view b) {
  std::string::size_type result_size = a.length() + b.length();
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

  char* out = &result[0];
  out = Append(out, a);
  out = Append(out, b);
  return result;
}

std::string StrCat(std::string_view a, std::string_view b, std::string_view c) {
  std::string::size_type result_size = a.length() + b.length() + c.length();
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

  char* out = &result[0];
  out = Append(out, a);
  out = Append(out, b);
  out = Append(out, c);
  return result;
}

std::string StrCat(std::string_view a, std::string_view b, std::string_view c,
                   std::string_view d) {
  std::string::size_type result_size =
      a.length() + b.length() + c.length() + d.length();
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

  char* out = &result[0];
  out = Append(out, a);
  out = Append(out, b);
  out = Append(out, c);
  out = Append(out, d);
  return result;
}

std::string StrCatPieces(std::vector<std::string_view> pieces) {
  std::string::size_type result_size = 0;
  for (std::string_view s : pieces) {
    result_size += s.length();
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

  char* out = &result[0];
  for (std::string_view s : pieces) {
    out = Append(out, s);
  }
  return result;
}

void StrAppend(std::string* dest, std::string_view a) {
  std::string::size_type old_size = dest->size();
  std::string::size_type new_size = old_size + a.length();
  dest->__resize_default_init(new_size);

  char* out = &(*dest)[old_size];
  out = Append(out, a);
}

void StrAppend(std::string* dest, std::string_view a, std::string_view b) {
  std::string::size_type old_size = dest->size();
  std::string::size_type new_size = old_size + a.length() + b.length();
  dest->__resize_default_init(new_size);

  char* out = &(*dest)[old_size];
  out = Append(out, a);
  out = Append(out, b);
}

void StrAppend(std::string* dest, std::string_view a, std::string_view b,
               std::string_view c) {
  std::string::size_type old_size = dest->size();
  std::string::size_type new_size =
      old_size + a.length() + b.length() + c.length();
  dest->__resize_default_init(new_size);

  char* out = &(*dest)[old_size];
  out = Append(out, a);
  out = Append(out, b);
  out = Append(out, c);
}

void StrAppend(std::string* dest, std::string_view a, std::string_view b,
               std::string_view c, std::string_view d) {
  std::string::size_type old_size = dest->size();
  std::string::size_type new_size =
      old_size + a.length() + b.length() + c.length() + d.length();
  dest->__resize_default_init(new_size);

  char* out = &(*dest)[old_size];
  out = Append(out, a);
  out = Append(out, b);
  out = Append(out, c);
  out = Append(out, d);
}

void StrAppendPieces(std::string* dest, std::vector<std::string_view> pieces) {
  std::string::size_type old_size = dest->size();
  std::string::size_type result_size = old_size;
  for (std::string_view s : pieces) {
    result_size += s.length();
  }
  // Resize dest with enough room to fit all operands.
  dest->__resize_default_init(result_size);

  char* out = &(*dest)[old_size];
  for (std::string_view s : pieces) {
    out = Append(out, s);
  }
}

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing
