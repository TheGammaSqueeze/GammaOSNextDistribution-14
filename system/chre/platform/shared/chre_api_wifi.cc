/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "chre_api/chre/wifi.h"

#include "chre/core/event_loop_manager.h"
#include "chre/util/macros.h"
#include "chre/util/system/napp_permissions.h"

using chre::EventLoopManager;
using chre::EventLoopManagerSingleton;
using chre::NanoappPermissions;

DLL_EXPORT uint32_t chreWifiGetCapabilities() {
#ifdef CHRE_WIFI_SUPPORT_ENABLED
  return chre::EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .getCapabilities();
#else
  return CHRE_WIFI_CAPABILITIES_NONE;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiConfigureScanMonitorAsync(bool enable,
                                                  const void *cookie) {
#ifdef CHRE_WIFI_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_WIFI) &&
         EventLoopManagerSingleton::get()
             ->getWifiRequestManager()
             .configureScanMonitor(nanoapp, enable, cookie);
#else
  return false;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiRequestScanAsync(
    const struct chreWifiScanParams *params, const void *cookie) {
#ifdef CHRE_WIFI_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_WIFI) &&
         EventLoopManagerSingleton::get()->getWifiRequestManager().requestScan(
             nanoapp, params, cookie);
#else
  return false;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiRequestRangingAsync(
    const struct chreWifiRangingParams *params, const void *cookie) {
#ifdef CHRE_WIFI_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_WIFI) &&
         EventLoopManagerSingleton::get()
             ->getWifiRequestManager()
             .requestRanging(chre::WifiRequestManager::RangingType::WIFI_AP,
                             nanoapp, params, cookie);
#else
  return false;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiNanSubscribe(struct chreWifiNanSubscribeConfig *config,
                                     const void *cookie) {
#if defined(CHRE_WIFI_SUPPORT_ENABLED) && defined(CHRE_WIFI_NAN_SUPPORT_ENABLED)
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_WIFI) &&
         EventLoopManagerSingleton::get()->getWifiRequestManager().nanSubscribe(
             nanoapp, config, cookie);
#else
  return false;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiNanSubscribeCancel(uint32_t subscriptionId) {
#if defined(CHRE_WIFI_SUPPORT_ENABLED) && defined(CHRE_WIFI_NAN_SUPPORT_ENABLED)
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_WIFI) &&
         EventLoopManagerSingleton::get()
             ->getWifiRequestManager()
             .nanSubscribeCancel(nanoapp, subscriptionId);
#else
  return false;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiNanRequestRangingAsync(
    const struct chreWifiNanRangingParams *params, const void *cookie) {
#if defined(CHRE_WIFI_SUPPORT_ENABLED) && defined(CHRE_WIFI_NAN_SUPPORT_ENABLED)
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_WIFI) &&
         EventLoopManagerSingleton::get()
             ->getWifiRequestManager()
             .requestRanging(chre::WifiRequestManager::RangingType::WIFI_AWARE,
                             nanoapp, params, cookie);
#else
  return false;
#endif  // CHRE_WIFI_SUPPORT_ENABLED
}

DLL_EXPORT bool chreWifiNanGetCapabilities(
    struct chreWifiNanCapabilities *capabilities) {
  // Not implemented yet.
  UNUSED_VAR(capabilities);
  return false;
}
