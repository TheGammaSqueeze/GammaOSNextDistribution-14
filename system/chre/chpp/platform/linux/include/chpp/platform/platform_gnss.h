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

#ifndef CHPP_PLATFORM_GNSS_SERVICE_H_
#define CHPP_PLATFORM_GNSS_SERVICE_H_

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Helper functions to force send a measurement or data event from the service.
 */
void gnssPalSendLocationEvent(void);
void gnssPalSendMeasurementEvent(void);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_PLATFORM_GNSS_SERVICE_H_
