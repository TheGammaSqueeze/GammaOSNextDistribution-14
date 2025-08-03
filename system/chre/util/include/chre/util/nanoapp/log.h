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

#ifndef CHRE_UTIL_NANOAPP_LOG_H_
#define CHRE_UTIL_NANOAPP_LOG_H_

/**
 * @file
 * Logging macros for nanoapps. These macros allow injecting a LOG_TAG and
 * compiling nanoapps with a minimum logging level (that is different than CHRE
 * itself).
 *
 * The typical format for the LOG_TAG macro is: "[AppName]"
 */
#ifdef CHRE_IS_NANOAPP_BUILD

#include "chre/util/log_common.h"
#include "chre_api/chre.h"

#ifndef NANOAPP_MINIMUM_LOG_LEVEL
#error "NANOAPP_MINIMUM_LOG_LEVEL must be defined"
#endif  // NANOAPP_MINIMUM_LOG_LEVEL

/*
 * Supply a stub implementation of the LOGx macros when the build is
 * configured with a minimum logging level that is above the requested level.
 * Use CHRE with pigweed tokenized logging if enabled, otherwise just map into
 * the chreLog function with the appropriate level.
 */

#ifdef CHRE_NANOAPP_TOKENIZED_LOGGING_ENABLED
#include "chre/platform/shared/nanoapp/tokenized_log.h"
#include "pw_tokenizer/encode_args.h"
#include "pw_tokenizer/tokenize.h"

#define CHRE_LOG_TAG(level, tag, fmt, ...)                             \
  do {                                                                 \
    CHRE_LOG_PREAMBLE                                                  \
    PW_TOKENIZE_FORMAT_STRING(PW_TOKENIZER_DEFAULT_DOMAIN, UINT32_MAX, \
                              tag " " fmt, __VA_ARGS__);               \
    platform_chrePwTokenizedLog(level, _pw_tokenizer_token,            \
                                PW_TOKENIZER_ARG_TYPES(__VA_ARGS__)    \
                                    PW_COMMA_ARGS(__VA_ARGS__));       \
    CHRE_LOG_EPILOGUE                                                  \
  } while (0)

#else

#define CHRE_LOG_TAG(level, tag, fmt, ...)         \
  do {                                             \
    CHRE_LOG_PREAMBLE                              \
    chreLog(level, "%s " fmt, tag, ##__VA_ARGS__); \
    CHRE_LOG_EPILOGUE                              \
  } while (0)

#endif  // CHRE_NANOAPP_TOKENIZED_LOGGING_ENABLED

#if NANOAPP_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_ERROR
#define LOGE_TAG(tag, fmt, ...) \
  CHRE_LOG_TAG(CHRE_LOG_ERROR, tag, fmt, ##__VA_ARGS__)
#else
#define LOGE_TAG(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#endif
#define LOGE(fmt, ...) LOGE_TAG(LOG_TAG, fmt, ##__VA_ARGS__)

#if NANOAPP_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_WARN
#define LOGW_TAG(tag, fmt, ...) \
  CHRE_LOG_TAG(CHRE_LOG_WARN, tag, fmt, ##__VA_ARGS__)
#else
#define LOGW_TAG(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#endif
#define LOGW(fmt, ...) LOGW_TAG(LOG_TAG, fmt, ##__VA_ARGS__)

#if NANOAPP_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_INFO
#define LOGI_TAG(tag, fmt, ...) \
  CHRE_LOG_TAG(CHRE_LOG_INFO, tag, fmt, ##__VA_ARGS__)
#else
#define LOGI_TAG(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#endif
#define LOGI(fmt, ...) LOGI_TAG(LOG_TAG, fmt, ##__VA_ARGS__)

#if NANOAPP_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_DEBUG
#define LOGD_TAG(tag, fmt, ...) \
  CHRE_LOG_TAG(CHRE_LOG_DEBUG, tag, fmt, ##__VA_ARGS__)
#else
#define LOGD_TAG(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#endif
#define LOGD(fmt, ...) LOGD_TAG(LOG_TAG, fmt, ##__VA_ARGS__)

// Map LOGV to LOGD as CHRE doesn't support it yet.
#if NANOAPP_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_VERBOSE
#define LOGV_TAG(tag, fmt, ...) \
  CHRE_LOG_TAG(CHRE_LOG_DEBUG, tag, fmt, ##__VA_ARGS__)
#else
#define LOGV_TAG(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#endif
#define LOGV(fmt, ...) LOGV_TAG(LOG_TAG, fmt, ##__VA_ARGS__)

#else

// For static nanoapps, reroute to the internal framework logging macro so that
// things are consistent across all the source code statically linked into the
// binary that contains the framework.
// This loses out on LOG_TAG prepending, and follows CHRE_MINIMUM_LOG_LEVEL
// rather than NANOAPP_MINIMUM_LOG_LEVEL, but means that anything using the
// container support library will have a consistent definition regardless of
// whether it's used in framework code or static nanoapp code.
#include "chre/platform/log.h"

#endif  // CHRE_IS_NANOAPP_BUILD

// Use this macro when including privacy-sensitive information like the user's
// location.
#ifdef LOG_INCLUDE_SENSITIVE_INFO
#define LOGE_SENSITIVE_INFO LOGE
#define LOGE_TAG_SENSITIVE_INFO LOGE_TAG
#define LOGW_SENSITIVE_INFO LOGW
#define LOGW_TAG_SENSITIVE_INFO LOGW_TAG
#define LOGI_SENSITIVE_INFO LOGI
#define LOGI_TAG_SENSITIVE_INFO LOGI_TAG
#define LOGD_SENSITIVE_INFO LOGD
#define LOGD_TAG_SENSITIVE_INFO LOGD_TAG
#define LOGV_SENSITIVE_INFO LOGV
#define LOGV_TAG_SENSITIVE_INFO LOGV_TAG
#else
#define LOGE_SENSITIVE_INFO(fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGE_TAG_SENSITIVE_INFO(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGW_SENSITIVE_INFO(fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGW_TAG_SENSITIVE_INFO(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGI_SENSITIVE_INFO(fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGI_TAG_SENSITIVE_INFO(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGD_SENSITIVE_INFO(fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGD_TAG_SENSITIVE_INFO(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGV_SENSITIVE_INFO(fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#define LOGV_TAG_SENSITIVE_INFO(tag, fmt, ...) CHRE_LOG_NULL(fmt, ##__VA_ARGS__)
#endif

// Convenience macro that helps with suppressing double promotion warnings when
// passing a float to chreDebugDumpLog().
#define CHRE_DEBUG_DUMP_LOG(fmt, ...)     \
  do {                                    \
    CHRE_LOG_PREAMBLE                     \
    chreDebugDumpLog(fmt, ##__VA_ARGS__); \
    CHRE_LOG_EPILOGUE                     \
  } while (0)

#endif  // CHRE_UTIL_NANOAPP_LOG_H_
