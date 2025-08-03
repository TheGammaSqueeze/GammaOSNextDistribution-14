// Copyright 2023 The Android Open Source Project
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

#include "logging.h"

#include <chrono>
#include <cinttypes>
#include <cstdarg>
#include <cstring>
#include <sstream>
#include <thread>

#ifdef _WIN32
#include <windows.h>
#endif

#ifdef __linux__
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>
#endif

namespace {

constexpr int kMaxThreadIdLength = 7;  // 7 digits for the thread id is what Google uses everywhere.

gfxstream_logger_t sLogger = nullptr;
gfxstream_logger_t sFineLogger = nullptr;

// Returns the current thread id as a string of at most kMaxThreadIdLength characters.
// We try to avoid using std::this_thread::get_id() because on Linux at least it returns a long
// number (e.g. 139853607339840) which isn't the same as the thread id from the OS itself.
// The current logic is inspired by:
// https://github.com/abseil/abseil-cpp/blob/52d41a9ec23e39db7e2cbce5c9449506cf2d3a5c/absl/base/internal/sysinfo.cc#L367-L381
std::string getThreadID() {
    std::ostringstream ss;
#if defined(_WIN32)
    ss << GetCurrentThreadId();
#elif defined(__linux__)
    ss << syscall(__NR_gettid);
#else
    ss << std::this_thread::get_id();
#endif
    std::string result = ss.str();
    // Truncate on the left if necessary
    return result.length() > kMaxThreadIdLength
               ? result.substr(result.length() - kMaxThreadIdLength)
               : result;
}

// Caches the thread id in thread local storage to increase performance
// Inspired by: https://github.com/abseil/abseil-cpp/blob/52d41a9ec23e39db7e2cbce5c9449506cf2d3a5c/absl/base/internal/sysinfo.cc#L494-L504
const char* getCachedThreadID() {
    static thread_local std::string thread_id = getThreadID();
    return thread_id.c_str();
}

// Borrowed from https://cs.android.com/android/platform/superproject/+/master:system/libbase/logging.cpp;l=84-98;drc=18c2bd4f3607cb300bb96e543df91dfdda6a9655
// Note: we use this over std::filesystem::path to keep it as fast as possible.
const char* GetFileBasename(const char* file) {
#if defined(_WIN32)
    const char* last_backslash = strrchr(file, '\\');
    if (last_backslash != nullptr) {
        return last_backslash + 1;
    }
#endif
    const char* last_slash = strrchr(file, '/');
    if (last_slash != nullptr) {
        return last_slash + 1;
    }
    return file;
}

}  // namespace

void set_gfxstream_logger(gfxstream_logger_t f) { sLogger = f; }

void set_gfxstream_fine_logger(gfxstream_logger_t f) { sFineLogger = f; }

void OutputLog(FILE* stream, char severity, const char* file, unsigned int line,
               int64_t timestamp_us, const char* format, ...) {
    gfxstream_logger_t logger =
        severity == 'I' || severity == 'W' || severity == 'E' || severity == 'F' ? sLogger
                                                                                 : sFineLogger;

    if (timestamp_us == 0) {
        timestamp_us = std::chrono::duration_cast<std::chrono::microseconds>(
                           std::chrono::system_clock::now().time_since_epoch())
                           .count();
    }
    std::time_t timestamp_s = timestamp_us / 1000000;

    // Break down the timestamp into the individual time parts
    std::tm ts_parts = {};
#if defined(_WIN32)
    localtime_s(&ts_parts, &timestamp_s);
#else
    localtime_r(&timestamp_s, &ts_parts);
#endif

    // Get the microseconds part of the timestamp since it's not available in the tm struct
    int64_t microseconds = timestamp_us % 1000000;

    // Output the standard Google logging prefix
    // See also: https://github.com/google/glog/blob/9dc1107f88d3a1613d61b80040d83c1c1acbac3d/src/logging.cc#L1612-L1615
    if (logger) {
        logger("%c%02d%02d %02d:%02d:%02d.%06" PRId64 " %7s %s:%d] ", severity, ts_parts.tm_mon + 1,
               ts_parts.tm_mday, ts_parts.tm_hour, ts_parts.tm_min, ts_parts.tm_sec, microseconds,
               getCachedThreadID(), GetFileBasename(file), line);
    } else {
        fprintf(stream, "%c%02d%02d %02d:%02d:%02d.%06" PRId64 " %7s %s:%d] ", severity,
                ts_parts.tm_mon + 1, ts_parts.tm_mday, ts_parts.tm_hour, ts_parts.tm_min,
                ts_parts.tm_sec, microseconds, getCachedThreadID(), GetFileBasename(file), line);
    }

    // Output the actual log message and newline
    va_list args;
    va_start(args, format);
    char temp[2048];
    int ret = vsnprintf(temp, sizeof(temp), format, args);
    temp[sizeof(temp) - 1] = 0;

    if (logger) {
        logger("%s\n", temp);
    } else {
        fprintf(stream, "%s\n", temp);
    }
    va_end(args);

}
