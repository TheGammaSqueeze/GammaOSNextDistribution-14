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

#ifndef CHRE_PLATFORM_LOG_H_
#define CHRE_PLATFORM_LOG_H_

/**
 * @file
 * Includes the appropriate platform-specific header file that supplies logging
 * capabilities. The platform header file must supply these symbols, either as
 * macros or free functions:
 *
 *   LOGE(format, ...)
 *   LOGW(format, ...)
 *   LOGI(format, ...)
 *   LOGD(format, ...)
 *
 * The platform header is recommend to also supply LOGV for verbose logs,
 * however it is not required.
 *
 * Where "format" is a printf-style format string, and E, W, I, D correspond to
 * the log levels Error, Warning, Informational, and Debug, respectively.
 */

#include "chre/target_platform/log.h"
#include "chre/util/log_common.h"

#ifndef LOGE
#error "LOGE must be defined by chre/target_platform/log.h"
#endif  // LOGE

#ifndef LOGW
#error "LOGW must be defined by chre/target_platform/log.h"
#endif  // LOGW

#ifndef LOGI
#error "LOGI must be defined by chre/target_platform/log.h"
#endif  // LOGI

#ifndef LOGD
#error "LOGD must be defined by chre/target_platform/log.h"
#endif  // LOGD

#ifndef LOGV
// Map LOGV to LOGD if the platform doesn't supply it - in that case LOGV won't
// be distinguished at runtime from LOGD, but we'll still retain the ability to
// compile out LOGV based on CHRE_MINIMUM_LOG_LEVEL
#define LOGV LOGD
#endif

/*
 * Supply a stub implementation of the LOGx macros when the build is
 * configured with a minimum logging level that is above the requested level.
 */

#ifndef CHRE_MINIMUM_LOG_LEVEL
#error "CHRE_MINIMUM_LOG_LEVEL must be defined"
#endif  // CHRE_MINIMUM_LOG_LEVEL

#if CHRE_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_ERROR
#undef LOGE
#define LOGE(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_WARN
#undef LOGW
#define LOGW(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_INFO
#undef LOGI
#define LOGI(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_DEBUG
#undef LOGD
#define LOGD(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL < CHRE_LOG_LEVEL_VERBOSE
#undef LOGV
#define LOGV(format, ...) CHRE_LOG_NULL(format, ##__VA_ARGS__)
#endif

#endif  // CHRE_PLATFORM_LOG_H_
