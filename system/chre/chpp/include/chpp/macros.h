/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef CHPP_MACROS_H_
#define CHPP_MACROS_H_

#include <assert.h>
#include <inttypes.h>
#include <stddef.h>
#include <stdlib.h>

#include "chpp/memory.h"

#ifdef __cplusplus
extern "C" {
#endif

#define MOST_SIGNIFICANT_NIBBLE 0xf0
#define LEAST_SIGNIFICANT_NIBBLE 0x0f

#ifndef UNUSED_VAR
#define UNUSED_VAR(var) ((void)(var))
#endif

#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif

#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(array) (sizeof(array) / sizeof((array)[0]))
#endif

#ifndef CHPP_ASSERT
#define CHPP_ASSERT(var) assert(var)
#endif

#ifndef CHPP_ASSERT_LOG
#define CHPP_ASSERT_LOG(var, fmt, ...) \
  do {                                 \
    if (!(var)) {                      \
      CHPP_LOGE(fmt, ##__VA_ARGS__);   \
      CHPP_ASSERT(false);              \
    }                                  \
  } while (0)
#endif

#ifndef CHPP_NOT_NULL
#define CHPP_NOT_NULL(var) CHPP_ASSERT((var) != NULL)
#endif

/**
 * Debug assertions that are not intended to be enabled for production software.
 */
#ifndef CHPP_DEBUG_ASSERT
#ifdef CHPP_DEBUG_ASSERT_ENABLED
#define CHPP_DEBUG_ASSERT(var) CHPP_ASSERT(var)
#else
#define CHPP_DEBUG_ASSERT(var) UNUSED_VAR(var)
#endif  // CHPP_DEBUG_ASSERT_ENABLED
#endif  // CHPP_DEBUG_ASSERT

#ifndef CHPP_DEBUG_NOT_NULL
#define CHPP_DEBUG_NOT_NULL(var) CHPP_DEBUG_ASSERT((var) != NULL)
#endif

#ifndef CHPP_DEBUG_ASSERT_LOG
#define CHPP_DEBUG_ASSERT_LOG(var, fmt, ...) \
  do {                                       \
    if (!(var)) {                            \
      CHPP_LOGE(fmt, ##__VA_ARGS__);         \
      CHPP_DEBUG_ASSERT(false);              \
    }                                        \
  } while (0)
#endif

#ifndef PRIu64  // Pre-C99 lacks PRIu64 support. Note that the correct
                // definition on pre-C99 systems would be compiler-dependent.
#define PRIu64 "llu"
#endif

#if (defined(__cpp_static_assert) && (__cpp_static_assert >= 200410)) || \
    (defined(__STDC_VERSION__) && (__STDC_VERSION__ >= 201112L))
#define CHPP_STATIC_ASSERT static_assert

#else  // Use fallback implementation as static_assert is not available.
#define CHPP_STATIC_ASSERT4(cond, var) typedef char var[(!!(cond)) * 2 - 1]
#define CHPP_STATIC_ASSERT3(cond, line) \
  CHPP_STATIC_ASSERT4(cond, static_assertion_at_line_##line)
#define CHPP_STATIC_ASSERT2(cond, line) CHPP_STATIC_ASSERT3(cond, line)
#define CHPP_STATIC_ASSERT(cond, msg) CHPP_STATIC_ASSERT2(cond, __LINE__)

#endif  // Use C11 or C++11 static_assert

// Time-related macros
#define CHPP_TIME_NONE 0
#define CHPP_TIME_MAX UINT64_MAX
#define CHPP_MSEC_PER_SEC UINT64_C(1000)
#define CHPP_USEC_PER_MSEC UINT64_C(1000)
#define CHPP_NSEC_PER_USEC UINT64_C(1000)
#define CHPP_USEC_PER_SEC (CHPP_USEC_PER_MSEC * CHPP_MSEC_PER_SEC)
#define CHPP_NSEC_PER_MSEC (CHPP_NSEC_PER_USEC * CHPP_USEC_PER_MSEC)
#define CHPP_NSEC_PER_SEC \
  (CHPP_NSEC_PER_USEC * CHPP_USEC_PER_MSEC * CHPP_MSEC_PER_SEC)
#define CHPP_NSEC_PER_MINUTE (CHPP_NSEC_PER_SEC * 60)
#define CHPP_NSEC_PER_HOUR (CHPP_NSEC_PER_MINUTE * 60)

#if defined(__GNUC__) || defined(__clang__)
#define check_types_match(t1, t2) ((__typeof__(t1) *)0 != (__typeof__(t2) *)0)
#else
#define check_types_match(t1, t2) 0
#endif

#define container_of(ptr, type, member)                     \
  ((type *)(void *)((char *)(ptr)-offsetof(type, member)) + \
   check_types_match(*(ptr), ((type *)0)->member))

#define sizeof_member(type, member) (sizeof(((type *)0)->member))

/**
 * Macros for defining (compiler dependent) packed structures
 */
#if defined(__GNUC__) || defined(__clang__)
// For GCC and clang
#define CHPP_PACKED_START
#define CHPP_PACKED_END
#define CHPP_PACKED_ATTR __attribute__((packed))

#elif defined(__ICCARM__) || defined(__CC_ARM)
// For IAR ARM and Keil MDK-ARM compilers
#define CHPP_PACKED_START _Pragma("pack(push, 1)")
#define CHPP_PACKED_END _Pragma("pack(pop)")
#define CHPP_PACKED_ATTR

#elif defined(_MSC_VER)
// For Microsoft Visual Studio
#define CHPP_PACKED_START __pragma(pack(push, 1))
#define CHPP_PACKED_END __pragma(pack(pop))
#define CHPP_PACKED_ATTR

#else
// Unknown compiler
#error Unrecognized compiler
#endif

#define CHPP_FREE_AND_NULLIFY(p) \
  do {                           \
    chppFree(p);                 \
    (p) = NULL;                  \
  } while (0)

//! Cast a const pointer to a non-const. This is necessary for removing const
//! with the -Wcast-qual compiler flag.
#define CHPP_CONST_CAST_POINTER(p) (void *)(intptr_t)(p)

#ifdef __cplusplus
}
#endif

#endif  // CHPP_MACROS_H_
