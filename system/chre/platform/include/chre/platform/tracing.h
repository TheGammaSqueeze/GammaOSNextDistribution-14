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

#ifndef CHRE_PLATFORM_TRACING_H_
#define CHRE_PLATFORM_TRACING_H_

#include <cstdint>

// Needs to be a number because it's used in STRINGIFY and as a number.
#define CHRE_TRACE_STR_BUFFER_SIZE 11
// Strings are placed into a buffer in the form:
// {<1-byte str len>, str chars...}.
// So the max string size is always one less than the total string buffer size.
#define CHRE_TRACE_MAX_STRING_SIZE CHRE_TRACE_STR_BUFFER_SIZE - 1

// TODO(b/301497381): See if netstruct lib would be more useful here
// Field values defined by python struct docs:
// https://docs.python.org/3/library/struct.html.
#define TRACE_BOOL "?"
#define TRACE_U8 "B"
#define TRACE_U16 "H"
#define TRACE_U32 "L"
#define TRACE_U64 "Q"
#define TRACE_I8 "b"
#define TRACE_I16 "h"
#define TRACE_I32 "l"
#define TRACE_I64 "q"
#define TRACE_C "c"
#define TRACE_S STRINGIFY(CHRE_TRACE_STR_BUFFER_SIZE) "p"

// Check to make sure pointer size macro is defined.
#ifndef __SIZEOF_POINTER__
#error "__SIZEOF_POINTER__ macro not defined - unsupported toolchain being used"
#else
static_assert(sizeof(void *) == __SIZEOF_POINTER__,
              "Size of pointer does not match __SIZEOF_POINTER__ macro");
#endif

// Check the predefined pointer size to use the most accurate size
#if __SIZEOF_POINTER__ == 8
#define TRACE_PTR TRACE_U64
#elif __SIZEOF_POINTER__ == 4
#define TRACE_PTR TRACE_U32
#else
#error "Pointer size is of unsupported size"
#endif  // __SIZEOF_POINTER__ == 8 || __SIZEOF_POINTER__ == 4

#ifdef CHRE_TRACING_ENABLED

#include "chre/target_platform/tracing.h"

/**
 * All tracing macros to be used in CHRE
 */
#ifndef CHRE_TRACE_INSTANT
#error "CHRE_TRACE_INSTANT must be defined by chre/target_platform/tracing.h"
#endif

#ifndef CHRE_TRACE_START
#error "CHRE_TRACE_START must be defined by chre/target_platform/tracing.h"
#endif

#ifndef CHRE_TRACE_END
#error "CHRE_TRACE_END must be defined by chre/target_platform/tracing.h"
#endif

#ifndef CHRE_TRACE_INSTANT_DATA
#error \
    "CHRE_TRACE_INSTANT_DATA must be defined by chre/target_platform/tracing.h"
#endif

#ifndef CHRE_TRACE_START_DATA
#error "CHRE_TRACE_START_DATA must be defined by chre/target_platform/tracing.h"
#endif

#ifndef CHRE_TRACE_END_DATA
#error "CHRE_TRACE_END_DATA must be defined by chre/target_platform/tracing.h"
#endif

#else  // CHRE_TRACING_ENABLED

#include "chre/util/macros.h"

inline void chreTraceUnusedParams(...) {}

#define CHRE_TRACE_INSTANT(...) chreTraceUnusedParams(__VA_ARGS__)
#define CHRE_TRACE_START(...) chreTraceUnusedParams(__VA_ARGS__)
#define CHRE_TRACE_END(...) chreTraceUnusedParams(__VA_ARGS__)
#define CHRE_TRACE_INSTANT_DATA(...) chreTraceUnusedParams(__VA_ARGS__)
#define CHRE_TRACE_START_DATA(...) chreTraceUnusedParams(__VA_ARGS__)
#define CHRE_TRACE_END_DATA(...) chreTraceUnusedParams(__VA_ARGS__)

#endif  // CHRE_TRACING_ENABLED

#endif  // CHRE_PLATFORM_TRACING_H_
