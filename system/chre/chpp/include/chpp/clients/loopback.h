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

#ifndef CHPP_CLIENT_LOOPBACK_H_
#define CHPP_CLIENT_LOOPBACK_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Loopback Test Results.
 */
struct ChppLoopbackTestResult {
  enum ChppAppErrorCode error;  // Indicates success or error type
  size_t requestLen;   // Length of the loopback request datagram, including app
                       // header
  size_t responseLen;  // Length of the loopback response datagram, including
                       // app header
  size_t firstError;   // Location of the first incorrect byte in the response
                       // datagram
  size_t byteErrors;   // Number of incorrect bytes in the response datagram
  uint64_t rttNs;      // Round trip time
};

/**
 * Minimum header length for a loopback packet. Everything afterwards is
 * considered a payload and is looped back.
 */
#define CHPP_LOOPBACK_HEADER_LEN sizeof(struct ChppAppHeader)

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Initializes the client.
 *
 * @param appState Application layer state.
 */
void chppLoopbackClientInit(struct ChppAppState *appState);

/**
 * Deinitializes the client.
 *
 * @param appState Application layer state.
 */
void chppLoopbackClientDeinit(struct ChppAppState *appState);

/**
 * Dispatches an Rx Datagram from the transport layer that is determined to
 * be for the CHPP Loopback Client.
 *
 * @param appState Application layer state.
 * @param response Input (response) datagram. Cannot be null.
 * @param len Length of input data in bytes.
 */
bool chppDispatchLoopbackServiceResponse(struct ChppAppState *appState,
                                         const uint8_t *response, size_t len);

/**
 * Initiates a CHPP service loopback from the client side.
 * Note that only one loopback test may be run at any time on each client.
 *
 * @param appState Application layer state.
 */
struct ChppLoopbackTestResult chppRunLoopbackTest(struct ChppAppState *appState,
                                                  const uint8_t *buf,
                                                  size_t len);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_CLIENT_LOOPBACK_H_
