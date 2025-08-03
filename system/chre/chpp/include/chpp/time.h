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

/*
 * Implementation Notes
 * Each platform must supply chpp/platform/platform_time.h which provides the
 * platform-specific definitions (and implementation as necessary) for the
 * definitions in this file.
 */

#ifndef CHPP_TIME_H_
#define CHPP_TIME_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @return The current time in nanoseconds.
 */
static uint64_t chppGetCurrentTimeNs(void);

#ifdef __cplusplus
}
#endif

#include "chpp/platform/platform_time.h"

#endif  // CHPP_TIME_H_
