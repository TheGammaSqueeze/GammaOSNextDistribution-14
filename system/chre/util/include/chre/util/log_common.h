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

#ifndef CHRE_UTIL_LOG_COMMON_H_
#define CHRE_UTIL_LOG_COMMON_H_

#include <chre/toolchain.h>

#include "chre/util/toolchain.h"

/**
 * @file
 * Includes common logging macros and functions that are shared between nanoapps
 * and the CHRE implementation.
 */

/**
 * An inline stub function to direct log messages to when logging is disabled.
 * This avoids unused variable warnings and will result in no overhead if
 * passing the arguments doesn't have additional side effects.
 */
CHRE_PRINTF_ATTR(1, 2)
static inline void chreLogNull(const char *fmt, ...) {
  (void)fmt;
}

//! Macro version of chreLogNull that applies toolchain-specific preamble and
//! epilogue (e.g. to disable double promotion warnings)
#define CHRE_LOG_NULL(fmt, ...)      \
  do {                               \
    CHRE_LOG_PREAMBLE                \
    chreLogNull(fmt, ##__VA_ARGS__); \
    CHRE_LOG_EPILOGUE                \
  } while (0)

// Each log level below increases with value as verbosity increases, such that
// we can set a threshold for verbosity at compile time

//! Used with CHRE_MINIMUM_LOG_LEVEL to indicate that no logs should be included
#define CHRE_LOG_LEVEL_MUTE 0

#define CHRE_LOG_LEVEL_ERROR 1
#define CHRE_LOG_LEVEL_WARN 2
#define CHRE_LOG_LEVEL_INFO 3
#define CHRE_LOG_LEVEL_DEBUG 4
#define CHRE_LOG_LEVEL_VERBOSE 5

/**
 * Logs an out of memory error with file and line number.
 */
#define LOG_OOM() LOGE("OOM at %s:%d", CHRE_FILENAME, __LINE__)

#endif  // CHRE_UTIL_LOG_COMMON_H_
