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

#ifndef CHRE_HOST_LOG_H_
#define CHRE_HOST_LOG_H_

#ifndef LOG_TAG
#define LOG_TAG "CHRE"
#endif

#include <log/log.h>

namespace android::chre {

/**
 * Logs a message to both logcat and stdout/stderr. Don't use this directly;
 * prefer one of LOGE, LOGW, etc.
 *
 * @param level  android log level, e.g., ANDROID_LOG_ERROR
 * @param stream output stream to print to, e.g., stdout
 * @param format printf-style format string
 * @param func   the function name included in the log, e.g., __func__
 * @param line   line number included in the log
 */
void outputHostLog(int priority, FILE *stream, const char *format,
                   const char *func, unsigned int line, ...);

}  // namespace android::chre

#define LOGE(format, ...)                                                     \
  ::android::chre::outputHostLog(ANDROID_LOG_ERROR, stderr, format, __func__, \
                                 __LINE__, ##__VA_ARGS__)
#define LOGW(format, ...)                                                    \
  ::android::chre::outputHostLog(ANDROID_LOG_WARN, stdout, format, __func__, \
                                 __LINE__, ##__VA_ARGS__)
#define LOGI(format, ...)                                                    \
  ::android::chre::outputHostLog(ANDROID_LOG_INFO, stdout, format, __func__, \
                                 __LINE__, ##__VA_ARGS__)
#define LOGD(format, ...)                                                     \
  ::android::chre::outputHostLog(ANDROID_LOG_DEBUG, stdout, format, __func__, \
                                 __LINE__, ##__VA_ARGS__)

#if LOG_NDEBUG
__attribute__((format(printf, 1, 2))) inline void chreLogNull(
    const char * /*fmt*/, ...) {}

#define LOGV(format, ...) chreLogNull(format, ##__VA_ARGS__)
#else
#define LOGV(format, ...)                                             \
  ::android::chre::outputHostLog(ANDROID_LOG_VERBOSE, stdout, format, \
                                 __func__, __LINE__, ##__VA_ARGS__)
#endif

/**
 * Helper to log a library error with a human-readable version of the provided
 * error code.
 *
 * @param message Error message string to log
 * @param error_code Standard error code number (EINVAL, etc)
 */
#define LOG_ERROR(message, error_code)                          \
  do {                                                          \
    char error_string[64];                                      \
    strerror_r(error_code, error_string, sizeof(error_string)); \
    LOGE("%s: %s (%d)\n", message, error_string, error_code);   \
  } while (0)

#endif  // CHRE_HOST_LOG_H_
