/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "berberis/base/format_buffer.h"

#include <cstdarg>
#include <cstddef>  // size_t

namespace berberis {

size_t FormatBuffer(char* buf, size_t buf_size, const char* format, ...) {
  va_list ap;
  va_start(ap, format);
  size_t n = FormatBufferV(buf, buf_size, format, ap);
  va_end(ap);
  return n;
}

size_t FormatBufferV(char* buf, size_t buf_size, const char* format, va_list ap) {
  if (!buf) {
    return 0;
  }
  if (!buf_size) {
    return 0;
  }

  CStrBuffer out(buf, buf_size - 1);  // reserve space for '\0'!
  if (format) {
    FormatBufferVaListArgs args(ap);
    FormatBufferImpl<CStrBuffer, FormatBufferVaListArgs>(&out, format, &args);
  }
  size_t n = out.Size();
  buf[n] = '\0';
  return n;
}

}  // namespace berberis
