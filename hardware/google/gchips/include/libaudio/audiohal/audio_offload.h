/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef __EXYNOS_AUDIOHAL_OFFLOAD_H__
#define __EXYNOS_AUDIOHAL_OFFLOAD_H__

#define OFFLOAD_EFFECT_LIBRARY_PATH ""

/**
 ** Compress Offload Message List
 **/
typedef enum {
    OFFLOAD_MSG_INVALID              = 0,

    OFFLOAD_MSG_WAIT_WRITE,
    OFFLOAD_MSG_WAIT_DRAIN,
    OFFLOAD_MSG_WAIT_PARTIAL_DRAIN,
    OFFLOAD_MSG_EXIT,

    OFFLOAD_MSG_MAX,
} offload_msg_type;

#endif  // __EXYNOS_AUDIOHAL_OFFLOAD_H__
