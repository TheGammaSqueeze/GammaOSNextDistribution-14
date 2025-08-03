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

#ifndef CHPP_COMMON_TIMESYNC_H_
#define CHPP_COMMON_TIMESYNC_H_

#include "chpp/app.h"
#include "chpp/macros.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

/**
 * Data structure used by the Discovery Response.
 */
CHPP_PACKED_START
struct ChppTimesyncResponse {
  struct ChppAppHeader header;
  uint64_t timeNs;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Commands used by the Discovery Service
 */
enum ChppTimesyncCommands {
  // Get time. This should be the same clock used to timestamp any data /
  // samples provided to CHPP.
  CHPP_TIMESYNC_COMMAND_GETTIME = 0x0001,
};

#ifdef __cplusplus
}
#endif

#endif  // CHPP_COMMON_TIMESYNC_H_
