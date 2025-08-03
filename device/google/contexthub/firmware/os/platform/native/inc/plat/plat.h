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

#include <stdint.h>

#ifndef _LINUX_PLAT_H_
#define _LINUX_PLAT_H_

#ifdef __cplusplus
extern "C" {
#endif

const struct AppHdr* platGetInternalAppList(uint32_t *numAppsP);

// External apps not supported
static inline uint8_t* platGetSharedAreaInfo(uint32_t *areaSzP)
{
    // Note: current seos.c code assumes that it will be able to read at least
    // 4 bytes. This will be addressed, but using this workaround for now.
    static uint8_t appHdr[4] = {0xff, 0xff, 0xff, 0xff};
    *areaSzP = 0;
    return appHdr;
}

static inline void platWake(void) {}

#ifdef __cplusplus
}
#endif

#endif
