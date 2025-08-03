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

#ifndef CHRE_PLATFORM_LINUX_LOG_H_
#define CHRE_PLATFORM_LINUX_LOG_H_

#include "chre_api/chre/re.h"

#ifndef __FILENAME__
#define __FILENAME__ __FILE__
#endif

#ifdef GTEST
// When using GoogleTest, just output to stdout since tests are single-threaded.
// GoogleTest complains about multiple threads if the PlatformLogSingleton is
// used.
#include <stdio.h>

#define CHRE_LINUX_LOG(logLevel, levelStr, color, fmt, ...)               \
  printf("\e[" color "m%s %s:%d\t" fmt "\e[0m\n", levelStr, __FILENAME__, \
         __LINE__, ##__VA_ARGS__)
#else
#include "chre/platform/linux/platform_log.h"

#define CHRE_LINUX_LOG(logLevel, levelStr, color, fmt, ...)        \
  if (::chre::PlatformLogSingleton::isInitialized()) {             \
    ::chre::PlatformLogSingleton::get()->log(                      \
        logLevel, "\e[" color "m%s %s:%d\t" fmt "\e[0m", levelStr, \
        __FILENAME__, __LINE__, ##__VA_ARGS__);                    \
  }
#endif

#define LOGE(fmt, ...) \
  CHRE_LINUX_LOG(CHRE_LOG_ERROR, "E", "91", fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) \
  CHRE_LINUX_LOG(CHRE_LOG_WARN, "W", "93", fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) \
  CHRE_LINUX_LOG(CHRE_LOG_INFO, "I", "96", fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) \
  CHRE_LINUX_LOG(CHRE_LOG_DEBUG, "D", "97", fmt, ##__VA_ARGS__)

#endif  // CHRE_PLATFORM_LINUX_LOG_H_
