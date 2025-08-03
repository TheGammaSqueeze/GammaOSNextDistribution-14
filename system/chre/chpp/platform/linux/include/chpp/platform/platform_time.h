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

#ifndef CHPP_PLATFORM_TIME_H_
#define CHPP_PLATFORM_TIME_H_

#include <stdint.h>

#include "chpp/macros.h"

#ifdef __cplusplus
extern "C" {
#endif

#define CHPP_TIMESPEC_TO_NS(time) \
  ((uint64_t)time.tv_sec * CHPP_NSEC_PER_SEC + (uint64_t)time.tv_nsec)

static inline uint64_t chppGetCurrentTimeNs(void) {
  struct timespec now;
  clock_gettime(CLOCK_REALTIME, &now);
  return CHPP_TIMESPEC_TO_NS(now);
}

#ifdef __cplusplus
}
#endif

#endif  // CHPP_PLATFORM_TIME_H_
