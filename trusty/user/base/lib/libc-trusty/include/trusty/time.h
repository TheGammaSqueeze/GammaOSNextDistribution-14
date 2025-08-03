/*
 * Copyright (C) 2018 The Android Open Source Project
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

#pragma once

/* Augment time.h with trusty-specific functions. */
#include <time.h>

#include <stdint.h>

/* Don't use convenience macros here, it will polute the namespace. */
#ifdef __cplusplus
extern "C" {
#endif

/* Prefixed with trusty_ because the signatures do not match POSIX. */
int trusty_gettime(clockid_t clock_id, int64_t* time);
int trusty_nanosleep(clockid_t clock_id, uint32_t flags, uint64_t sleep_time);

/*
 * The trusty_nanosleep() can sleep longer than expected in certain scenarios
 * (like CPUIdle is enabled). trusty_nanodelay() is less likely to oversleep,
 * but at the cost of additional CPU usage. Use trusty_nanodelay() when working
 * with hardware with precise timing requirements. But avoid using
 * trusty_nanodelay() with large sleep_time values.
 */
int trusty_nanodelay(clockid_t clock_id, uint32_t flags, uint64_t sleep_time);

#ifdef __cplusplus
}
#endif
