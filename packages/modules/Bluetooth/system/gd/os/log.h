/******************************************************************************
 *
 *  Copyright 2019 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <inttypes.h>

#include <cstdlib>

#ifndef LOG_TAG
#define LOG_TAG "bluetooth"
#endif

static_assert(LOG_TAG != nullptr, "LOG_TAG should never be NULL");

#include "os/log_tags.h"
#include "os/logging/log_adapter.h"

#if defined(FUZZ_TARGET)

#define LOG_VERBOSE_INT(...)
#define LOG_DEBUG_INT(...)
#define LOG_INFO_INT(...)
#define LOG_WARN_INT(...)

#define LOG_ERROR_INT(...) do {     \
  fprintf(stderr, __VA_ARGS__);     \
} while (false)

// for fuzz targets, we just
// need to abort in this statement
// to catch the bug
#define LOG_ALWAYS_FATAL_INT(...) do {  \
    fprintf(stderr, __VA_ARGS__);       \
    abort();                            \
  } while (false)

#else /* end of defined(FUZZ_TARGET) */

#if defined(__ANDROID__)

#include <log/log.h>
#include <log/log_event_list.h>

#define LOG_VERBOSE_INT(fmt, args...)                                                     \
  do {                                                                                    \
    if (!__android_log_is_loggable(ANDROID_LOG_VERBOSE, LOG_TAG, ANDROID_LOG_INFO) &&     \
        !__android_log_is_loggable(ANDROID_LOG_VERBOSE, "bluetooth", ANDROID_LOG_INFO)) { \
      ALOGV(fmt, ##args);                                                                 \
    }                                                                                     \
  } while (false)

#define LOG_DEBUG_INT(fmt, args...)                                                     \
  do {                                                                                  \
    if (!__android_log_is_loggable(ANDROID_LOG_DEBUG, LOG_TAG, ANDROID_LOG_INFO) &&     \
        !__android_log_is_loggable(ANDROID_LOG_DEBUG, "bluetooth", ANDROID_LOG_INFO)) { \
      ALOGD(fmt, ##args);                                                               \
    }                                                                                   \
  } while (false)

#define LOG_INFO_INT(fmt, args...) ALOGI(fmt, ##args)
#define LOG_WARN_INT(fmt, args...) ALOGW(fmt, ##args)
#define LOG_ERROR_INT(fmt, args...) ALOGE(fmt, ##args)
#define LOG_ALWAYS_FATAL_INT(fmt, args...) do { \
  ALOGE(fmt, ##args);                           \
  abort();                                      \
} while (false)

#elif defined (ANDROID_EMULATOR)  /* end of defined(__ANDROID__) */
// Log using android emulator logging mechanism
#include "android/utils/debug.h"

#define LOGWRAPPER(fmt, args...) VERBOSE_INFO(bluetooth, "bluetooth: " fmt, \
                                               ##args)

#define LOG_VEBOSE_INT(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_DEBUG_INT(...)  LOGWRAPPER(__VA_ARGS__)
#define LOG_INFO_INT(...)   LOGWRAPPER(__VA_ARGS__)
#define LOG_WARN_INT(...)   LOGWRAPPER(__VA_ARGS__)
#define LOG_ERROR_INT(...)  LOGWRAPPER(__VA_ARGS__)
#define LOG_ALWAYS_FATAL_INT(fmt, args...)                                          \
  do {                                                                              \
    fprintf(stderr, fmt "\n", ##args);                                              \
    abort();                                                                        \
  } while (false)
#elif defined(TARGET_FLOSS) /* end of defined (ANDROID_EMULATOR) */
#include "os/syslog.h"

// Prefix the log with tag, file, line and function
#define LOGWRAPPER(tag, fmt, args...) write_syslog(tag, LOG_TAG, "%s: " fmt, LOG_TAG, ##args)

#define LOG_VERBOSE_INT(...) LOGWRAPPER(LOG_TAG_VERBOSE, __VA_ARGS__)
#define LOG_DEBUG_INT(...) LOGWRAPPER(LOG_TAG_DEBUG, __VA_ARGS__)
#define LOG_INFO_INT(...) LOGWRAPPER(LOG_TAG_INFO, __VA_ARGS__)
#define LOG_WARN_INT(...) LOGWRAPPER(LOG_TAG_WARN, __VA_ARGS__)
#define LOG_ERROR_INT(...) LOGWRAPPER(LOG_TAG_ERROR, __VA_ARGS__)

#define LOG_ALWAYS_FATAL_INT(...)           \
  do {                                      \
    LOGWRAPPER(LOG_TAG_FATAL, __VA_ARGS__); \
    abort();                                \
  } while (false)

#else  /* end of defined (TARGET_FLOSS) */

/* syslog didn't work well here since we would be redefining LOG_DEBUG. */
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#include <chrono>
#include <cstdio>
#include <ctime>

#define LOGWRAPPER(fmt, args...)                                                                                    \
  do {                                                                                                              \
    auto _now = std::chrono::system_clock::now();                                                                   \
    auto _now_ms = std::chrono::time_point_cast<std::chrono::milliseconds>(_now);                                   \
    auto _now_t = std::chrono::system_clock::to_time_t(_now);                                                       \
    /* YYYY-MM-DD_HH:MM:SS.sss is 23 byte long, plus 1 for null terminator */                                       \
    char _buf[24];                                                                                                  \
    auto l = std::strftime(_buf, sizeof(_buf), "%Y-%m-%d %H:%M:%S", std::localtime(&_now_t));                       \
    snprintf(                                                                                                       \
        _buf + l, sizeof(_buf) - l, ".%03u", static_cast<unsigned int>(_now_ms.time_since_epoch().count() % 1000)); \
    /* pid max is 2^22 = 4194304 in 64-bit system, and 32768 by default, hence 7 digits are needed most */          \
    fprintf(                                                                                                        \
        stderr,                                                                                                     \
        "%s %7d %7ld %s:" fmt "\n",                                                                                 \
        _buf,                                                                                                       \
        static_cast<int>(getpid()),                                                                                 \
        syscall(SYS_gettid),                                                                                        \
        LOG_TAG,                                                                                                    \
        ##args);                                                                                                    \
  } while (false)

#define LOG_VERBOSE_INT(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_DEBUG_INT(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_INFO_INT(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_WARN_INT(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_ERROR_INT(...) LOGWRAPPER(__VA_ARGS__)

#ifndef LOG_ALWAYS_FATAL
#define LOG_ALWAYS_FATAL_INT(...) \
  do {                            \
    LOGWRAPPER(__VA_ARGS__);      \
    abort();                      \
  } while (false)
#endif

#endif /* defined(__ANDROID__) */

#endif /* defined(FUZZ_TARGET) */

#define _LOG_SRC_FMT_STR "%s:%d - %s: "
#define _PREPEND_SRC_LOC_IN_LOG(fmt, args...) \
  _LOG_SRC_FMT_STR fmt, __FILE__, __LINE__, __func__, ##args
// ---------------------------------------------------------
// All MACROs defined above are internal and should *not* be
// used directly (use LOG_XXX defined below instead).
// the output of LOG_XXX_INT does not contain the source
// location of the log emitting statement, so far they are only used by
// LogMsg, where the source locations is passed in.

#define LOG_VERBOSE(fmt, args...)                                             \
  LOG_VERBOSE_INT(_PREPEND_SRC_LOC_IN_LOG(fmt, ##args))

#define LOG_DEBUG(fmt, args...)                                               \
  LOG_DEBUG_INT(_PREPEND_SRC_LOC_IN_LOG(fmt, ##args))

#define LOG_INFO(fmt, args...)                                                \
  LOG_INFO_INT(_PREPEND_SRC_LOC_IN_LOG(fmt, ##args))

#define LOG_WARN(fmt, args...)                                                \
  LOG_WARN_INT(_PREPEND_SRC_LOC_IN_LOG(fmt, ##args))

#define LOG_ERROR(fmt, args...)                                               \
  LOG_ERROR_INT(_PREPEND_SRC_LOC_IN_LOG(fmt, ##args))

#ifndef LOG_ALWAYS_FATAL
#define LOG_ALWAYS_FATAL(fmt, args...)                                        \
  LOG_ALWAYS_FATAL_INT(_PREPEND_SRC_LOC_IN_LOG(fmt, ##args))
#endif

#define ASSERT(condition)                                    \
  do {                                                       \
    if (!(condition)) {                                      \
      LOG_ALWAYS_FATAL("assertion '" #condition "' failed"); \
    }                                                        \
  } while (false)

#define ASSERT_LOG(condition, fmt, args...)                                 \
  do {                                                                      \
    if (!(condition)) {                                                     \
      LOG_ALWAYS_FATAL("assertion '" #condition "' failed - " fmt, ##args); \
    }                                                                       \
  } while (false)
