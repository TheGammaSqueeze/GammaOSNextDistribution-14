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

#ifndef CHRE_PLATFORM_ZEPHYR_LOG_H_
#define CHRE_PLATFORM_ZEPHYR_LOG_H_

#include <zephyr/logging/log.h>
LOG_MODULE_DECLARE(chre, CONFIG_CHRE_LOG_LEVEL);

/** Map CHRE's LOGE to Zephyr's LOG_ERR */
#define LOGE(...) LOG_ERR(__VA_ARGS__)
/** Map CHRE's LOGW to Zephyr's LOG_WRN */
#define LOGW(...) LOG_WRN(__VA_ARGS__)
/** Map CHRE's LOGI to Zephyr's LOG_INF */
#define LOGI(...) LOG_INF(__VA_ARGS__)
/** Map CHRE's LOGD to Zephyr's LOG_DBG */
#define LOGD(...) LOG_DBG(__VA_ARGS__)

#endif  // CHRE_PLATFORM_ZEPHYR_LOG_H_
