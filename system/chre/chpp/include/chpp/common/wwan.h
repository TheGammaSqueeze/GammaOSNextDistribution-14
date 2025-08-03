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

#ifndef CHPP_WWAN_COMMON_H_
#define CHPP_WWAN_COMMON_H_

#include <stdint.h>

#include "chpp/app.h"
#include "chpp/macros.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

#define CHPP_PAL_WWAN_API_VERSION CHRE_PAL_WWAN_API_V1_4

/**
 * Data structures used by the Get Capabilities Response.
 */
CHPP_PACKED_START
struct ChppWwanGetCapabilitiesParameters {
  uint32_t capabilities;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

CHPP_PACKED_START
struct ChppWwanGetCapabilitiesResponse {
  struct ChppAppHeader header;
  struct ChppWwanGetCapabilitiesParameters params;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Commands used by the WWAN (cellular) Service.
 */
enum ChppWwanCommands {
  //! Initializes the service.
  CHPP_WWAN_OPEN = 0x0000,

  //! Deinitializes the service.
  CHPP_WWAN_CLOSE = 0x0001,

  //! Retrieves a set of flags indicating supported features.
  CHPP_WWAN_GET_CAPABILITIES = 0x0002,

  //! Query information about the current serving cell and its neighbors.
  CHPP_WWAN_GET_CELLINFO_ASYNC = 0x0003,
};
#define CHPP_WWAN_CLIENT_REQUEST_MAX CHPP_WWAN_GET_CELLINFO_ASYNC

#ifdef __cplusplus
}
#endif

#endif  // CHPP_WWAN_COMMON_H_
