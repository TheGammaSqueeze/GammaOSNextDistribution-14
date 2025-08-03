/*
 * Copyright 2022 The Android Open Source Project
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

#include <log/log.h>
#include <sys/syscall.h> /* Definition of SYS_* constants */
#include <unistd.h>

#include <chrono>
#include <string>

#include "build_timestamp.h"  // generated

long long GetTimestampMs();

// Internal to headless below

extern int console_fd;
extern std::chrono::system_clock::time_point _prev;

// Highlight the MAIN thread with text replacement
constexpr char _main[7 + 1] = "  MAIN ";

#define STR(obj) (obj).ToString().c_str()

#define ASSERT_LOG(condition, fmt, args...)                                 \
  do {                                                                      \
    if (!(condition)) {                                                     \
      LOG_ALWAYS_FATAL("assertion '" #condition "' failed - " fmt, ##args); \
    }                                                                       \
  } while (false)

#define LOG_CONSOLE(fmt, args...)                                              \
  do {                                                                         \
    ASSERT_LOG(console_fd != -1, "Console output fd has not been set");        \
    /* Also log to Android logging via INFO level */                           \
    ALOGI("%s:%d %s: " fmt, __FILE__, __LINE__, __func__, ##args);             \
    auto _now = std::chrono::system_clock::now();                              \
    auto _delta =                                                              \
        std::chrono::duration_cast<std::chrono::microseconds>(_now - _prev);   \
    _prev = _now;                                                              \
    auto _now_us =                                                             \
        std::chrono::time_point_cast<std::chrono::microseconds>(_now);         \
    auto _now_t = std::chrono::system_clock::to_time_t(_now);                  \
    /* YYYY-MM-DD_HH:MM:SS.ssssss is 26 byte long, plus 1 for null terminator  \
     */                                                                        \
    char _buf[26 + 1];                                                         \
    auto l = std::strftime(_buf, sizeof(_buf), "%Y-%m-%d %H:%M:%S",            \
                           std::localtime(&_now_t));                           \
    snprintf(_buf + l, sizeof(_buf) - l, ".%06u",                              \
             static_cast<unsigned int>(_now_us.time_since_epoch().count() %    \
                                       1000000));                              \
    /* pid max is 2^22 = 4194304 in 64-bit system, and 32768 by default, hence \
     * 7 digits are needed most */                                             \
    char _buf_thread[7 + 1];                                                   \
    snprintf(_buf_thread, sizeof(_buf_thread), "%7ld", syscall(SYS_gettid));   \
    dprintf(console_fd, "%s - [ %9.06f ] %7d %s %s : " fmt "\n", _buf,         \
            _delta.count() / 1000000.0, static_cast<int>(getpid()),            \
            (syscall(SYS_gettid) == static_cast<int>(getpid())) ? _main        \
                                                                : _buf_thread, \
            LOG_TAG, ##args);                                                  \
  } while (false)

constexpr char kCompiledDateFormat[] = "%b %d %Y";
constexpr char kBuildDateFormat[] = "%Y-%m-%d";

inline std::string build_id() {
  return std::string(bluetooth::test::headless::kBuildTime);
}
