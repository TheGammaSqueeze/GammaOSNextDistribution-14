// Copyright (C) 2019 The Android Open Source Project
// Copyright (C) 2019 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#include "aemu/base/Tracing.h"

#if defined(__ANDROID__)

#include <cutils/trace.h>
#define TRACE_TAG ATRACE_TAG_GRAPHICS

#elif defined(__Fuchsia__) && !defined(FUCHSIA_NO_TRACE)

#include <lib/trace/event.h>
#define TRACE_TAG "gfx"

#else
#endif

namespace gfxstream {
namespace guest {

bool isTracingEnabled() {
#if defined(__ANDROID__)
    return atrace_is_tag_enabled(TRACE_TAG);
#else
    // TODO: Fuchsia + Linux
    return false;
#endif
}

void ScopedTraceGuest::beginTraceImpl(const char* name) {
#if defined(__ANDROID__)
    atrace_begin(TRACE_TAG, name);
#elif defined(__Fuchsia__) && !defined(FUCHSIA_NO_TRACE)
    TRACE_DURATION_BEGIN(TRACE_TAG, name);
#else
    // No-op
    (void)name;
#endif
}

void ScopedTraceGuest::endTraceImpl(const char* name) {
#if defined(__ANDROID__)
    (void)name;
    atrace_end(TRACE_TAG);
#elif defined(__Fuchsia__) && !defined(FUCHSIA_NO_TRACE)
    TRACE_DURATION_END(TRACE_TAG, name);
#else
    // No-op
    (void)name;
#endif
}

} // namespace base
} // namespace android
