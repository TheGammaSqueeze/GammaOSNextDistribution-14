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

#ifndef CHPP_WWAN_CLIENT_H_
#define CHPP_WWAN_CLIENT_H_

#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/clients.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Public Definitions
 ***********************************************/

/************************************************
 *  Public functions
 ***********************************************/

/**
 * Called by the App layer to register the WWAN (cellular) common client.
 *
 * @param appContext Maintains status for each app layer instance.
 */
void chppRegisterWwanClient(struct ChppAppState *appContext);

/**
 * Called by the App layer to deregister the WWAN (cellular) common client.
 *
 * @param appContext Maintains status for each app layer instance.
 */
void chppDeregisterWwanClient(struct ChppAppState *appContext);

/**
 * @return The ChppEndpointState pointer to the WWAN client.
 */
struct ChppEndpointState *getChppWwanClientState(void);

#ifndef CHPP_CLIENT_ENABLED_CHRE_WWAN
/**
 * Alternative to chrePalWwanGetApi() to avoid conflicting with CHPP service.
 */
const struct chrePalWwanApi *chppPalWwanGetApi(uint32_t requestedApiVersion);
#endif

#ifdef __cplusplus
}
#endif

#endif  // CHPP_WWAN_CLIENT_H_
