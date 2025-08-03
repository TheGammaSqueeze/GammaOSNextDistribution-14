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

#ifndef CHPP_CLIENT_TIMESYNC_H_
#define CHPP_CLIENT_TIMESYNC_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/common/timesync.h"
#include "chpp/macros.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Maximum timesync step change at each measurement
 */
#ifndef CHPP_CLIENT_TIMESYNC_MAX_CHANGE_NS
#define CHPP_CLIENT_TIMESYNC_MAX_CHANGE_NS (100 * CHPP_NSEC_PER_MSEC)
#endif

/**
 * Default maximum age of a time offset measurement
 */
#ifndef CHPP_TIMESYNC_DEFAULT_MAX_AGE_NS
#define CHPP_TIMESYNC_DEFAULT_MAX_AGE_NS (24 * CHPP_NSEC_PER_HOUR)
#endif

/**
 * Timesync Results.
 */
struct ChppTimesyncResult {
  enum ChppAppErrorCode error;  // Indicates success or error type
  int64_t offsetNs;             // Time offset of service (service - client)
  uint64_t rttNs;               // RTT
  uint64_t measurementTimeNs;   // Time of last measurement
};

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Initializes the client.
 *
 * @param appState Application layer state.
 */
void chppTimesyncClientInit(struct ChppAppState *appState);

/**
 * Deinitializes the client.
 *
 * @param appState Application layer state.
 */
void chppTimesyncClientDeinit(struct ChppAppState *appState);

/**
 * Resets the client.
 *
 * @param appState Application layer state.
 */
void chppTimesyncClientReset(struct ChppAppState *appState);

/**
 * Dispatches an Rx Datagram from the transport layer that is determined to
 * be for the CHPP Timesync Client.
 *
 * @param appState Application layer state.
 * @param buf Input (response) datagram. Cannot be null.
 * @param len Length of input data in bytes.
 *
 * @return Indicates success or failure.
 */
bool chppDispatchTimesyncServiceResponse(struct ChppAppState *appState,
                                         const uint8_t *buf, size_t len);

/**
 * Initiates a CHPP timesync to measure time offset of the service.
 *
 * @param appState Application layer state.
 *
 * @return Indicates success or failure.
 */
bool chppTimesyncMeasureOffset(struct ChppAppState *appState);

/**
 * Provides the time offset of the service. If the latest measurement is within
 * maxTimesyncAgeNs, this function reuses the last measurement. Otherwise, it
 * will initiate a new measurement.
 *
 * @param appState Application layer state.
 * @param maxTimesyncAgeNs Maximum acceptable age of measuement.
 *
 * @return Time offset of service vs client (service - client)
 */
int64_t chppTimesyncGetOffset(struct ChppAppState *appState,
                              uint64_t maxTimesyncAgeNs);

/**
 * Provides the raw results of the latest timesync measurement.
 *
 * @param appState Application layer state.
 *
 * @return Latest result.
 */
const struct ChppTimesyncResult *chppTimesyncGetResult(
    struct ChppAppState *appState);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_CLIENT_TIMESYNC_H_
