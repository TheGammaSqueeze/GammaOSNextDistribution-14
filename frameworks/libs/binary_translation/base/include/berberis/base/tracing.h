/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef BERBERIS_BASE_TRACING_H_
#define BERBERIS_BASE_TRACING_H_

#include <unistd.h>

#include <cstdarg>

#include "berberis/base/format_buffer.h"
#include "berberis/base/gettid.h"
#include "berberis/base/scoped_errno.h"

namespace berberis {

// TODO(eaeltsin): disable for user builds (NDEBUG doesn't work)!
constexpr bool kEnableTracing = true;

// Use class static members/functions to keep implementation private but still inline it.
class Tracing {
 public:
  // ATTENTION: portable code!

  static void Init() {
    if (kEnableTracing) InitImpl();
  }

  static bool IsOn() { return kEnableTracing && IsOnImpl(); }

  static void __attribute__((__format__(printf, 1, 2))) TraceF(const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    TraceV(format, ap);
    va_end(ap);
  }

  static void __attribute__((__format__(printf, 1, 0))) TraceV(const char* format, va_list ap) {
    FormatBufferVaListArgs args(ap);
    TraceA(format, &args);
  }

  template <typename Args>
  static void TraceA(const char* format, Args* args) {
    DynamicCStrBuffer buf;

    FormatBufferImplF(&buf, "%5u %5u ", GetpidSyscall(), GettidSyscall());
    FormatBufferImpl(&buf, format, args);
    buf.Put('\n');

    TraceImpl(buf.Data(), buf.Size());
  }

 private:
  // ATTENTION: platform-specific code!

  static void InitImpl();

  // ATTENTION: posix code!

  static bool IsOnImpl() { return fd_ != -1; }

  static void TraceImpl(const char* buf, size_t n) {
    ScopedErrno scoped_errno;
    // Tracing output should always be atomic, there should be no cases when
    // concurrent tracing threads create mixed messages.
    // A single 'write' call is intentional. If we are not lucky to output the
    // whole message or there is an error in one system call, so be it.
    UNUSED(write(fd_, buf, n));
  }

  static int fd_;
};

}  // namespace berberis

// Don't evaluate arguments if tracing is disabled.
#define TRACE(...)                              \
  do {                                          \
    if (::berberis::Tracing::IsOn()) {          \
      ::berberis::Tracing::TraceF(__VA_ARGS__); \
    }                                           \
  } while (0)

#endif  // BERBERIS_BASE_TRACING_H_
