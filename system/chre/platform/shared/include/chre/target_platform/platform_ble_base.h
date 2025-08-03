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

#ifndef CHRE_PLATFORM_SHARED_PLATFORM_BLE_BASE_H_
#define CHRE_PLATFORM_SHARED_PLATFORM_BLE_BASE_H_

#include "chre/pal/ble.h"
#include "chre/platform/shared/platform_pal.h"

namespace chre {

/**
 * Provides an instance of the PlatformBleBase class that uses the CHRE PAL to
 * access the ble subsystem.
 */
class PlatformBleBase : public PlatformPal {
 protected:
  //! The instance of callbacks that are provided to the CHRE PAL.
  static const chrePalBleCallbacks sBleCallbacks;

  //! The instance of the CHRE PAL API. This will be set to nullptr if the
  //! platform does not supply an implementation.
  const chrePalBleApi *mBleApi;

  static void requestStateResync();
  static void scanStatusChangeCallback(bool enabled, uint8_t errorCode);
  static void advertisingEventCallback(struct chreBleAdvertisementEvent *event);
  static void readRssiCallback(uint8_t errorCode, uint16_t connectionHandle,
                               int8_t rssi);
  static void flushCallback(uint8_t errorCode);
  static void handleBtSnoopLog(bool isTxToBtController, const uint8_t *buffer,
                               size_t size);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_SHARED_PLATFORM_BLE_BASE_H_
