/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef CHPP_PLATFORM_LOG_H_
#define CHPP_PLATFORM_LOG_H_

#include <inttypes.h>
#include <pthread.h>
#include <stdio.h>

#include "chpp/platform/platform_time.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef __FILENAME__
#define __FILENAME__ __FILE__
#endif

#ifndef PRIuSIZE
#define PRIuSIZE "zu"
#endif

// TODO: Should use PRIu8 etc. from inttypes.h instead of %d, etc. (add -Wall
// and -Werror to cflags to catch these)
#define CHPP_LINUX_LOG(level, color, fmt, ...)                              \
  {                                                                         \
    char name[16];                                                          \
    uint64_t ms = chppGetCurrentTimeNs() / 1000000;                         \
    pthread_getname_np(pthread_self(), name, 16);                           \
    printf("\e[" color "m[%" PRIu64 "] %s %s:%d\t (%s) " fmt "\e[0m\n", ms, \
           level, __FILENAME__, __LINE__, name, ##__VA_ARGS__);             \
  }

#define CHPP_LOGE(fmt, ...) CHPP_LINUX_LOG("E", "91", fmt, ##__VA_ARGS__)
#define CHPP_LOGW(fmt, ...) CHPP_LINUX_LOG("W", "93", fmt, ##__VA_ARGS__)
#define CHPP_LOGI(fmt, ...) CHPP_LINUX_LOG("I", "96", fmt, ##__VA_ARGS__)
#define CHPP_LOGD(fmt, ...) CHPP_LINUX_LOG("D", "97", fmt, ##__VA_ARGS__)

#ifdef __cplusplus
}
#endif

#endif  // CHPP_PLATFORM_LOG_H_
