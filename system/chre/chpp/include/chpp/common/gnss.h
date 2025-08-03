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

#ifndef CHPP_GNSS_COMMON_H_
#define CHPP_GNSS_COMMON_H_

#include <stdbool.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/macros.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

#define CHPP_PAL_GNSS_API_VERSION CHRE_PAL_GNSS_API_V1_4

/**
 * Data structures for Control Location Session request.
 */
CHPP_PACKED_START
struct ChppGnssControlLocationSessionParameters {
  bool enable;
  uint32_t minIntervalMs;
  uint32_t minTimeToNextFixMs;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

CHPP_PACKED_START
struct ChppGnssControlLocationSessionRequest {
  struct ChppAppHeader header;
  struct ChppGnssControlLocationSessionParameters params;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Data structures for Control Measurement Session request.
 */
CHPP_PACKED_START
struct ChppGnssControlMeasurementSessionParameters {
  bool enable;
  uint32_t minIntervalMs;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

CHPP_PACKED_START
struct ChppGnssControlMeasurementSessionRequest {
  struct ChppAppHeader header;
  struct ChppGnssControlMeasurementSessionParameters params;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Data structures for Configure Passive Listener request.
 */
CHPP_PACKED_START
struct ChppGnssConfigurePassiveLocationListenerParameters {
  bool enable;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

CHPP_PACKED_START
struct ChppGnssConfigurePassiveLocationListenerRequest {
  struct ChppAppHeader header;
  struct ChppGnssConfigurePassiveLocationListenerParameters params;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Data structures used by the Get Capabilities response.
 */
CHPP_PACKED_START
struct ChppGnssGetCapabilitiesParameters {
  uint32_t capabilities;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

CHPP_PACKED_START
struct ChppGnssGetCapabilitiesResponse {
  struct ChppAppHeader header;
  struct ChppGnssGetCapabilitiesParameters params;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Data structure used by the Control Location Session response.
 */
CHPP_PACKED_START struct ChppGnssControlLocationSessionResponse {
  struct ChppAppHeader header;
  bool enabled;
  uint8_t errorCode;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Data structure used by the Control Measurement Session response.
 */
CHPP_PACKED_START struct ChppGnssControlMeasurementSessionResponse {
  struct ChppAppHeader header;
  bool enabled;
  uint8_t errorCode;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/**
 * Commands used by the GNSS (GPS) Service.
 */
enum ChppGnssCommands {
  //! Initializes the service.
  CHPP_GNSS_OPEN = 0x0000,

  //! Deinitializes the service.
  CHPP_GNSS_CLOSE = 0x0001,

  //! Retrieves a set of flags indicating supported features.
  CHPP_GNSS_GET_CAPABILITIES = 0x0002,

  //! Start/stop/modify the GNSS location session.
  CHPP_GNSS_CONTROL_LOCATION_SESSION = 0x0003,

  //!  Start/stop/modify the raw GNSS measurement session.
  CHPP_GNSS_CONTROL_MEASUREMENT_SESSION = 0x0004,

  //! Configures opportunistic reporting of location fixes produced for other
  //! GNSS clients.
  CHPP_GNSS_CONFIGURE_PASSIVE_LOCATION_LISTENER = 0x0005,

  //! Notification to request that the core CHRE system re-send requests for any
  //! active sessions and its current passive location listener setting.
  CHPP_GNSS_REQUEST_STATE_RESYNC_NOTIFICATION = 0x0006,

  //! Notification with GNSS location results.
  CHPP_GNSS_LOCATION_RESULT_NOTIFICATION = 0x0007,

  //!  Notification with raw GNSS measurement results.
  CHPP_GNSS_MEASUREMENT_RESULT_NOTIFICATION = 0x0008,
};
#define CHPP_GNSS_CLIENT_REQUEST_MAX \
  CHPP_GNSS_CONFIGURE_PASSIVE_LOCATION_LISTENER

#ifdef __cplusplus
}
#endif

#endif  // CHPP_GNSS_COMMON_H_
