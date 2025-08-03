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

#ifndef CHRE_PLATFORM_TINYSYS_LOG_H_
#define CHRE_PLATFORM_TINYSYS_LOG_H_

#include "chre_api/chre.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "mt_printf.h"

#ifdef __cplusplus
}  // extern "C"
#endif

#ifdef CHRE_USE_BUFFERED_LOGGING

/**
 * Log via the LogBufferManagerSingleton vaLog method.
 *
 * Defined in system/chre/platform/shared/log_buffer_manager.cc
 *
 * @param level The log level.
 * @param format The format string.
 * @param ... The arguments to print into the final log.
 */
void chrePlatformLogToBuffer(enum chreLogLevel level, const char *format, ...);

// Print logs to host logcat
#define CHRE_BUFFER_LOG(level, fmt, arg...)     \
  do {                                          \
    CHRE_LOG_PREAMBLE                           \
    chrePlatformLogToBuffer(level, fmt, ##arg); \
    CHRE_LOG_EPILOGUE                           \
  } while (0)

#define LOGE(fmt, arg...)                                 \
  do {                                                    \
    PRINTF_E("[CHRE]" fmt "\n", ##arg);                   \
    CHRE_BUFFER_LOG(CHRE_LOG_ERROR, "[CHRE]" fmt, ##arg); \
  } while (0)

#define LOGW(fmt, arg...)                                \
  do {                                                   \
    PRINTF_W("[CHRE]" fmt "\n", ##arg);                  \
    CHRE_BUFFER_LOG(CHRE_LOG_WARN, "[CHRE]" fmt, ##arg); \
  } while (0)

#define LOGI(fmt, arg...)                                \
  do {                                                   \
    PRINTF_I("[CHRE]" fmt "\n", ##arg);                  \
    CHRE_BUFFER_LOG(CHRE_LOG_INFO, "[CHRE]" fmt, ##arg); \
  } while (0)

#define LOGD(fmt, arg...)                                 \
  do {                                                    \
    PRINTF_D("[CHRE]" fmt "\n", ##arg);                   \
    CHRE_BUFFER_LOG(CHRE_LOG_DEBUG, "[CHRE]" fmt, ##arg); \
  } while (0)

#else

#define LOGE(fmt, arg...) PRINTF_E("[CHRE]" fmt "\n", ##arg)
#define LOGW(fmt, arg...) PRINTF_W("[CHRE]" fmt "\n", ##arg)
#define LOGI(fmt, arg...) PRINTF_I("[CHRE]" fmt "\n", ##arg)
#define LOGD(fmt, arg...) PRINTF_D("[CHRE]" fmt "\n", ##arg)

#endif

#endif  // CHRE_PLATFORM_TINYSYS_LOG_H_
