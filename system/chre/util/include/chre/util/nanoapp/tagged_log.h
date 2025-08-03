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

#ifndef CHRE_UTIL_NANOAPP_TAGGED_LOG_H_
#define CHRE_UTIL_NANOAPP_TAGGED_LOG_H_

/**
 * @file
 * Logging macros for nanoapps. These macros allow injecting a LOG_TAG
 * regardless of the build type (dynamic or static).
 *
 * The typical format for the LOG_TAG macro is: "[AppName]"
 */

#include "chre/util/nanoapp/log.h"

#ifdef CHRE_IS_NANOAPP_BUILD
// Dynamic nanoapps
#define TLOGV LOGV
#define TLOGD LOGD
#define TLOGI LOGI
#define TLOGW LOGW
#define TLOGE LOGE

#else  // CHRE_IS_NANOAPP_BUILD
// Static nanoapps
#define TLOGV(format, ...) LOGV("%s " format, LOG_TAG, ##__VA_ARGS__)
#define TLOGD(format, ...) LOGD("%s " format, LOG_TAG, ##__VA_ARGS__)
#define TLOGI(format, ...) LOGI("%s " format, LOG_TAG, ##__VA_ARGS__)
#define TLOGW(format, ...) LOGW("%s " format, LOG_TAG, ##__VA_ARGS__)
#define TLOGE(format, ...) LOGE("%s " format, LOG_TAG, ##__VA_ARGS__)

#endif  // CHRE_IS_NANOAPP_BUILD

#endif  // CHRE_UTIL_NANOAPP_TAGGED_LOG_H_
