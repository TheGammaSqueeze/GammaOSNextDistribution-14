/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_SLPI_LOG_H_
#define CHRE_PLATFORM_SLPI_LOG_H_

#include "chre/util/toolchain.h"

#ifndef __FILENAME__
#define __FILENAME__ CHRE_FILENAME
#endif

#if defined(CHRE_USE_BUFFERED_LOGGING)
#include "chre_api/chre/re.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Log via the PlatformLogBufferSingleton vaLog method.
 *
 * Defined in platform/shared/log_buffer_manager.cc
 *
 * @param chreLogLevel The log level.
 * @param format The format string.
 * @param ... The arguments to print into the final log.
 */
void chrePlatformLogToBuffer(enum chreLogLevel chreLogLevel, const char *format,
                             ...);

#ifdef __cplusplus
}
#endif

#define CHRE_BUFFER_LOG(level, fmt, ...)                \
  do {                                                  \
    CHRE_LOG_PREAMBLE                                   \
    chrePlatformLogToBuffer(level, fmt, ##__VA_ARGS__); \
    CHRE_LOG_EPILOGUE                                   \
  } while (0)
#define LOGE(fmt, ...) CHRE_BUFFER_LOG(CHRE_LOG_ERROR, fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) CHRE_BUFFER_LOG(CHRE_LOG_WARN, fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) CHRE_BUFFER_LOG(CHRE_LOG_INFO, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) CHRE_BUFFER_LOG(CHRE_LOG_DEBUG, fmt, ##__VA_ARGS__)
#define LOGV(fmt, ...) CHRE_BUFFER_LOG(CHRE_LOG_VERBOSE, fmt, ##__VA_ARGS__)

#elif defined(CHRE_TOKENIZED_LOGGING_ENABLED)
#include "pw_tokenizer/tokenize.h"
#define CHRE_SEND_TOKENIZED_LOG(level, fmt, ...)                   \
  do {                                                             \
    CHRE_LOG_PREAMBLE                                              \
    PW_TOKENIZE_TO_GLOBAL_HANDLER_WITH_PAYLOAD((void *)level, fmt, \
                                               ##__VA_ARGS__);     \
    CHRE_LOG_EPILOGUE                                              \
  } while (0)
#define LOGE(fmt, ...) \
  CHRE_SEND_TOKENIZED_LOG(CHRE_LOG_LEVEL_ERROR, fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) \
  CHRE_SEND_TOKENIZED_LOG(CHRE_LOG_LEVEL_WARN, fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) \
  CHRE_SEND_TOKENIZED_LOG(CHRE_LOG_LEVEL_INFO, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) \
  CHRE_SEND_TOKENIZED_LOG(CHRE_LOG_LEVEL_DEBUG, fmt, ##__VA_ARGS__)
#define LOGV(fmt, ...) \
  CHRE_SEND_TOKENIZED_LOG(CHRE_LOG_LEVEL_VERBOSE, fmt, ##__VA_ARGS__)

#elif defined(CHRE_USE_FARF_LOGGING)
// Define FARF_X macros depending on the CHRE log level to ensure that FARF
// doesn't strip out logs that CHRE intends to log.
#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_ERROR
#ifndef FARF_ERROR
#define FARF_ERROR 1
#endif
#endif

#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_WARN
#ifndef FARF_HIGH
#define FARF_HIGH 1
#endif
#endif

#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_INFO
#ifndef FARF_MEDIUM
#define FARF_MEDIUM 1
#endif
#endif

#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_DEBUG
#ifndef FARF_LOW
#define FARF_LOW 1
#endif
#endif

#ifdef CHRE_USE_FARF_LOGGING
#include "HAP_farf.h"
#endif

#define CHRE_SLPI_LOG(level, fmt, ...) \
  do {                                 \
    CHRE_LOG_PREAMBLE                  \
    FARF(level, fmt, ##__VA_ARGS__);   \
    CHRE_LOG_EPILOGUE                  \
  } while (0)

#define LOGE(fmt, ...) CHRE_SLPI_LOG(ERROR, fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) CHRE_SLPI_LOG(HIGH, fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) CHRE_SLPI_LOG(MEDIUM, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) CHRE_SLPI_LOG(LOW, fmt, ##__VA_ARGS__)
#define LOGV(fmt, ...) CHRE_SLPI_LOG(LOW, fmt, ##__VA_ARGS__)

#else

#include "ash/debug.h"
#define CHRE_SLPI_LOG(level, fmt, ...)                  \
  do {                                                  \
    CHRE_LOG_PREAMBLE                                   \
    ashLog(ASH_SOURCE_CHRE, level, fmt, ##__VA_ARGS__); \
    CHRE_LOG_EPILOGUE                                   \
  } while (0)

#define LOGE(fmt, ...) CHRE_SLPI_LOG(ASH_LOG_ERROR, fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) CHRE_SLPI_LOG(ASH_LOG_WARN, fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) CHRE_SLPI_LOG(ASH_LOG_INFO, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) CHRE_SLPI_LOG(ASH_LOG_DEBUG, fmt, ##__VA_ARGS__)
#define LOGV(fmt, ...) CHRE_SLPI_LOG(ASH_LOG_VERBOSE, fmt, ##__VA_ARGS__)
#endif  // CHRE_USE_FARF_LOGGING

#endif  // CHRE_PLATFORM_SLPI_LOG_H_
