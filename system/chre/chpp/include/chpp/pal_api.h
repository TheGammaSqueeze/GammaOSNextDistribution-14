/**
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

#ifndef CHPP_PAL_API_H_
#define CHPP_PAL_API_H_

#include "chpp/app.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Called by the App layer to initialize the PAL system APIs.
 *
 * @param context Maintains status for each app layer instance.
 */
void chppPalSystemApiInit(struct ChppAppState *context);

/**
 * Called by the App layer to deinitialize the PAL system APIs.
 *
 * @param context Maintains status for each app layer instance.
 */
void chppPalSystemApiDeinit(struct ChppAppState *context);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_LOG_H_
