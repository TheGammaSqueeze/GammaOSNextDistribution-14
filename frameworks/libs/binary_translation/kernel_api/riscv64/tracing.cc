/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "berberis/kernel_api/tracing.h"

#include <cstdarg>

#include "berberis/base/tracing.h"

namespace berberis {

void __attribute__((__format__(printf, 1, 2))) KernelApiTrace(const char* format, ...) {
  if (Tracing::IsOn()) {
    va_list ap;
    va_start(ap, format);
    Tracing::TraceV(format, ap);
    va_end(ap);
  }
}

}  // namespace berberis
