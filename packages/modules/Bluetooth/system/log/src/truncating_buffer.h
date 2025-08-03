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

#pragma once

#include <cstddef>

namespace bluetooth::log_internal {

/// Truncating write buffer.
///
/// This buffer can be used with `std::back_insert_iterator` to create
/// an output iterator. All write actions beyond the maximum length of
/// the buffer are silently ignored.
template <int buffer_size>
struct truncating_buffer {
  using value_type = char;

  void push_back(char c) {
    if (len < buffer_size - 1) {
      buffer[len++] = c;
    }
  }

  char const* c_str() {
    if (len == buffer_size - 1) {
      // Inspect the last 4 bytes of the buffer to check if
      // the last character was truncated. Remove the character
      // entirely if that's the case.
      for (size_t n = 0; n < 4; n++) {
        char c = buffer[len - n - 1];
        if ((c & 0b11000000) == 0b10000000) {
          continue;
        }
        size_t char_len = (c & 0b10000000) == 0b00000000   ? 1
                          : (c & 0b11100000) == 0b11000000 ? 2
                          : (c & 0b11110000) == 0b11100000 ? 3
                          : (c & 0b11111000) == 0b11110000 ? 4
                                                           : 0;
        if ((n + 1) < char_len) {
          len -= n + 1;
        }
        break;
      }
    }

    buffer[len] = '\0';
    return buffer;
  }

 private:
  char buffer[buffer_size];
  size_t len{0};
};

}  // namespace bluetooth::log_internal
