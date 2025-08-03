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

#include "chre_api/chre/ble.h"

#include "chre/core/event_loop_manager.h"
#include "chre/util/macros.h"
#include "chre/util/system/napp_permissions.h"

using chre::EventLoopManager;
using chre::EventLoopManagerSingleton;
using chre::NanoappPermissions;

DLL_EXPORT uint32_t chreBleGetCapabilities() {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  return EventLoopManagerSingleton::get()
      ->getBleRequestManager()
      .getCapabilities();
#else
  return CHRE_BLE_CAPABILITIES_NONE;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT uint32_t chreBleGetFilterCapabilities() {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  return EventLoopManagerSingleton::get()
      ->getBleRequestManager()
      .getFilterCapabilities();
#else
  return CHRE_BLE_FILTER_CAPABILITIES_NONE;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleFlushAsync(const void *cookie) {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()->getBleRequestManager().flushAsync(
             nanoapp, cookie);
#else
  UNUSED_VAR(cookie);
  return false;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleStartScanAsyncV1_9(
    chreBleScanMode mode, uint32_t reportDelayMs,
    const struct chreBleScanFilterV1_9 *filter, const void *cookie) {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()
             ->getBleRequestManager()
             .startScanAsync(nanoapp, mode, reportDelayMs, filter, cookie);
#else
  UNUSED_VAR(mode);
  UNUSED_VAR(reportDelayMs);
  UNUSED_VAR(filter);
  UNUSED_VAR(cookie);
  return false;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleStartScanAsync(chreBleScanMode mode,
                                      uint32_t reportDelayMs,
                                      const struct chreBleScanFilter *filter) {
  if (filter == nullptr) {
    return chreBleStartScanAsyncV1_9(mode, reportDelayMs, nullptr /* filter */,
                                     nullptr /* cookie */);
  }
  chreBleScanFilterV1_9 filterV1_9 = {
      filter->rssiThreshold, filter->scanFilterCount, filter->scanFilters,
      0 /* broadcasterAddressFilterCount */,
      nullptr /* broadcasterAddressFilters */};
  return chreBleStartScanAsyncV1_9(mode, reportDelayMs, &filterV1_9,
                                   nullptr /* cookie */);
}

DLL_EXPORT bool chreBleStopScanAsyncV1_9(const void *cookie) {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()->getBleRequestManager().stopScanAsync(
             nanoapp, cookie);
#else
  UNUSED_VAR(cookie);
  return false;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleStopScanAsync() {
  return chreBleStopScanAsyncV1_9(nullptr /* cookie */);
}

DLL_EXPORT bool chreBleReadRssiAsync(uint16_t connectionHandle,
                                     const void *cookie) {
#ifdef CHRE_BLE_READ_RSSI_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()->getBleRequestManager().readRssiAsync(
             nanoapp, connectionHandle, cookie);
#else
  UNUSED_VAR(connectionHandle);
  UNUSED_VAR(cookie);
  return false;
#endif  // CHRE_BLE_READ_RSSI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleGetScanStatus(struct chreBleScanStatus *status) {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()->getBleRequestManager().getScanStatus(
             status);
#else
  UNUSED_VAR(status);
  return false;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}