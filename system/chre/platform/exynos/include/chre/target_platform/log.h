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
#ifndef CHRE_PLATFORM_EXYNOS_LOG_H_
#define CHRE_PLATFORM_EXYNOS_LOG_H_

#include <stdio.h>

#include "chre_api/chre.h"

// TODO(b/230134803): Note that 'printf' currently redirects to dmesg: modify
// the below macros when we have a platform implementation available that
// redirects to logcat.
#define CHRE_EXYNOS_LOG(logLevel, fmt, ...) printf("[CHRE] " fmt, ##__VA_ARGS__)

#define LOGE(fmt, ...) CHRE_EXYNOS_LOG(CHRE_LOG_ERROR, fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) CHRE_EXYNOS_LOG(CHRE_LOG_WARN, fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) CHRE_EXYNOS_LOG(CHRE_LOG_INFO, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) CHRE_EXYNOS_LOG(CHRE_LOG_DEBUG, fmt, ##__VA_ARGS__)
#define LOGV(fmt, ...) CHRE_EXYNOS_LOG(CHRE_LOG_VERBOSE, fmt, ##__VA_ARGS__)

#endif  // CHRE_PLATFORM_EXYNOS_LOG_H_
