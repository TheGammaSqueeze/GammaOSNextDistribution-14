/*
 * Copyright (C) 2020 The Android Open Source Project
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

// IWYU pragma: private, include "chre_api/chre.h"
// IWYU pragma: friend chre/.*\.h

#ifndef CHRE_TOOLCHAIN_H_
#define CHRE_TOOLCHAIN_H_

/**
 * @file
 * Compiler/build toolchain-specific macros used by the CHRE API
 */

#if defined(__GNUC__) || defined(__clang__)
// For GCC and clang

#define CHRE_DEPRECATED(message) \
  __attribute__((deprecated(message)))

// Indicates that the function does not return (i.e. abort).
#define CHRE_NO_RETURN __attribute__((noreturn))

// Enable printf-style compiler warnings for mismatched format string and args
#define CHRE_PRINTF_ATTR(formatPos, argStart) \
  __attribute__((format(printf, formatPos, argStart)))

#define CHRE_BUILD_ERROR(message) CHRE_DO_PRAGMA(GCC error message)
#define CHRE_DO_PRAGMA(message) _Pragma(#message)

#elif defined(__ICCARM__) || defined(__CC_ARM)
// For IAR ARM and Keil MDK-ARM compilers

#define CHRE_PRINTF_ATTR(formatPos, argStart)

#elif defined(_MSC_VER)
// For Microsoft Visual Studio

#define CHRE_PRINTF_ATTR(formatPos, argStart)

#else  // if !defined(__GNUC__) && !defined(__clang__)

#error Need to add support for new compiler

#endif

// For platforms that don't support error pragmas, utilize the best method of
// showing an error depending on the platform support.
#ifndef CHRE_BUILD_ERROR
#ifdef __cplusplus  // C++17 or greater assumed
#define CHRE_BUILD_ERROR(message) static_assert(0, message)
#elif defined(__STDC_VERSION__) && __STDC_VERSION__ >= 201112L
#define CHRE_BUILD_ERROR(message) _Static_assert(0, message)
#else
#define CHRE_BUILD_ERROR(message) char buildError[-1] = message
#endif
#endif

#endif  // CHRE_TOOLCHAIN_H_
