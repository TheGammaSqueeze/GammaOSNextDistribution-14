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

#ifndef CHPP_SERVICE_DISCOVERY_H_
#define CHPP_SERVICE_DISCOVERY_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public functions
 ***********************************************/

/*
 * Dispatches a client request that is determined to be for the CHPP Discovery
 * Service.
 *
 * @param context Maintains status for each app layer instance.
 * @param buf Input (request) datagram. Cannot be null.
 * @param len Length of input data in bytes.
 */
bool chppDispatchDiscoveryClientRequest(struct ChppAppState *context,
                                        const uint8_t *buf, size_t len);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_SERVICE_DISCOVERY_H_
