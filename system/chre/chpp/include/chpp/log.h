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

#ifndef CHPP_LOG_H_
#define CHPP_LOG_H_

// Defines CHRE macros that are necessary to compile CHPP (for compilers other
// than GCC / clang)
#ifndef IS_CHPP_BUILD
#define IS_CHPP_BUILD
#endif

#include "chpp/platform/platform_log.h"
#include "chre/util/log_common.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Log errors if the platform does not supply logging macros.
 */

#ifndef CHPP_LOGE
#error "CHPP_LOGE must be defined"
#endif  // CHPP_LOGE

#ifndef CHPP_LOGW
#error "CHPP_LOGW must be defined"
#endif  // CHPP_LOGW

#ifndef CHPP_LOGI
#error "CHPP_LOGI must be defined"
#endif  // CHPP_LOGI

#ifndef CHPP_LOGD
#error "CHPP_LOGD must be defined"
#endif  // CHPP_LOGD

#ifndef CHPP_MINIMUM_LOG_LEVEL
#define CHPP_MINIMUM_LOG_LEVEL CHRE_LOG_LEVEL_DEBUG
#endif  // CHPP_MINIMUM_LOG_LEVEL

#if CHPP_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_ERROR
#undef CHPP_LOGE
#define CHPP_LOGE(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHPP_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_WARN
#undef CHPP_LOGW
#define CHPP_LOGW(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHPP_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_INFO
#undef CHPP_LOGI
#define CHPP_LOGI(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHPP_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_DEBUG
#undef CHPP_LOGD
#define CHPP_LOGD(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

/**
 * Logs an out of memory error with file and line number.
 */
#define CHPP_LOG_OOM() CHPP_LOGE("OOM at %s:%d", __FILE__, __LINE__)

#ifdef __cplusplus
}
#endif

#endif  // CHPP_LOG_H_
