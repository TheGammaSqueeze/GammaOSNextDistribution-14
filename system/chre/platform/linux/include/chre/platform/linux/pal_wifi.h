/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_LINUX_PAL_WIFI_H_
#define CHRE_PLATFORM_LINUX_PAL_WIFI_H_

#include <stdint.h>

#include <chrono>

enum class PalWifiAsyncRequestTypes : uint8_t {
  SCAN,
  SCAN_MONITORING,
  RANGING,

  // Must be last
  NUM_WIFI_REQUEST_TYPE,
};

/**
 * @return whether scan monitoring is active.
 */
bool chrePalWifiIsScanMonitoringActive();

/**
 * Sets how long each async request should hold before replying the result
 * to CHRE.
 *
 * @param requestType select one request type to modify its behavior.
 * @param seconds delayed response time.
 */
void chrePalWifiDelayResponse(PalWifiAsyncRequestTypes requestType,
                              std::chrono::seconds seconds);

/**
 * Sets if PAL should send back async request result for each async request.
 *
 * This function is used to mimic the behavior of hardware failure in
 * simulation test.
 *
 * @param requestType select one request type to modify its behavior.
 * @param enableResponse true if allow pal to send back async result.
 */
void chrePalWifiEnableResponse(PalWifiAsyncRequestTypes requestType,
                               bool enableResponse);

#endif  // CHRE_PLATFORM_LINUX_PAL_WIFI_H_