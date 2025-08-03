/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHPP_CLIENTS_TEST_H_
#define CHPP_CLIENTS_TEST_H_

#include "chpp/app.h"
#include "chpp/transport.h"

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 *  Functions necessary for unit testing
 ***********************************************/

struct ChppAppHeader *chppTransportGetRequestTimeoutResponse(
    struct ChppTransportState *context, enum ChppEndpointType type);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_CLIENTS_TEST_H_
