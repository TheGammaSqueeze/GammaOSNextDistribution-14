/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/platform/platform_wifi.h"

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/system/wifi_util.h"

namespace chre {

const chrePalWifiCallbacks PlatformWifiBase::sWifiCallbacks = {
    PlatformWifi::scanMonitorStatusChangeCallback,
    PlatformWifiBase::scanResponseCallback,
    PlatformWifiBase::scanEventCallback,
    PlatformWifiBase::rangingEventCallback,
    PlatformWifiBase::nanServiceIdentifierCallback,
    PlatformWifiBase::nanServiceDiscoveryCallback,
    PlatformWifiBase::nanServiceLostCallback,
    PlatformWifiBase::nanServiceTerminatedCallback,
    PlatformWifiBase::nanServiceSubscriptionCanceledCallback,
};

PlatformWifi::~PlatformWifi() {
  if (mWifiApi != nullptr) {
    LOGD("Platform WiFi closing");
    prePalApiCall(PalType::WIFI);
    mWifiApi->close();
    LOGD("Platform WiFi closed");
  }
}

void PlatformWifi::init() {
  prePalApiCall(PalType::WIFI);
  mWifiApi = chrePalWifiGetApi(CHRE_PAL_WIFI_API_CURRENT_VERSION);
  if (mWifiApi != nullptr) {
    if (!mWifiApi->open(&gChrePalSystemApi, &sWifiCallbacks)) {
      LOGE("WiFi PAL open returned false");

#ifdef CHRE_TELEMETRY_SUPPORT_ENABLED
      EventLoopManagerSingleton::get()->getTelemetryManager().onPalOpenFailure(
          TelemetryManager::PalType::WIFI);
#endif  // CHRE_TELEMETRY_SUPPORT_ENABLED

      mWifiApi = nullptr;
    } else {
      LOGD("Opened WiFi PAL version 0x%08" PRIx32, mWifiApi->moduleVersion);
    }
  } else {
    LOGW("Requested Wifi PAL (version 0x%08" PRIx32 ") not found",
         CHRE_PAL_WIFI_API_CURRENT_VERSION);
  }
}

uint32_t PlatformWifi::getCapabilities() {
  if (mWifiApi != nullptr) {
    prePalApiCall(PalType::WIFI);
    return mWifiApi->getCapabilities();
  } else {
    return CHRE_WIFI_CAPABILITIES_NONE;
  }
}

bool PlatformWifi::configureScanMonitor(bool enable) {
  if (mWifiApi != nullptr) {
    prePalApiCall(PalType::WIFI);
    return mWifiApi->configureScanMonitor(enable);
  } else {
    return false;
  }
}

bool PlatformWifi::requestRanging(const struct chreWifiRangingParams *params) {
  if (mWifiApi != nullptr &&
      mWifiApi->moduleVersion >= CHRE_PAL_WIFI_API_V1_2) {
    prePalApiCall(PalType::WIFI);
    return mWifiApi->requestRanging(params);
  } else {
    return false;
  }
}

bool PlatformWifi::requestNanRanging(
    const struct chreWifiNanRangingParams *params) {
  bool success = false;
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  if (mWifiApi != nullptr &&
      mWifiApi->moduleVersion >= CHRE_PAL_WIFI_API_V1_6) {
    prePalApiCall(PalType::WIFI);
    success = mWifiApi->requestNanRanging(params);
  }
#endif
  return success;
}

bool PlatformWifi::requestScan(const struct chreWifiScanParams *params) {
  if (mWifiApi != nullptr) {
    prePalApiCall(PalType::WIFI);

    if (mWifiApi->moduleVersion < CHRE_PAL_WIFI_API_V1_5) {
      const struct chreWifiScanParams paramsCompat =
          translateToLegacyWifiScanParams(params);
      return mWifiApi->requestScan(&paramsCompat);
    } else {
      return mWifiApi->requestScan(params);
    }
  } else {
    return false;
  }
}

void PlatformWifi::releaseRangingEvent(struct chreWifiRangingEvent *event) {
  prePalApiCall(PalType::WIFI);
  mWifiApi->releaseRangingEvent(event);
}

void PlatformWifi::releaseScanEvent(struct chreWifiScanEvent *event) {
  prePalApiCall(PalType::WIFI);
  mWifiApi->releaseScanEvent(event);
}

void PlatformWifi::releaseNanDiscoveryEvent(
    struct chreWifiNanDiscoveryEvent *event) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  prePalApiCall(PalType::WIFI);
  mWifiApi->releaseNanDiscoveryEvent(event);
#else
  UNUSED_VAR(event);
#endif
}

bool PlatformWifi::nanSubscribe(
    const struct chreWifiNanSubscribeConfig *config) {
  bool success = false;
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  if (mWifiApi != nullptr &&
      mWifiApi->moduleVersion >= CHRE_PAL_WIFI_API_V1_6) {
    prePalApiCall(PalType::WIFI);
    success = mWifiApi->nanSubscribe(config);
  }
#else
  UNUSED_VAR(config);
#endif
  return success;
}

bool PlatformWifi::nanSubscribeCancel(uint32_t subscriptionId) {
  bool success = false;
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  if (mWifiApi != nullptr &&
      mWifiApi->moduleVersion >= CHRE_PAL_WIFI_API_V1_6) {
    prePalApiCall(PalType::WIFI);
    success = mWifiApi->nanSubscribeCancel(subscriptionId);
  }
#else
  UNUSED_VAR(subscriptionId);
#endif
  return success;
}

void PlatformWifiBase::rangingEventCallback(
    uint8_t errorCode, struct chreWifiRangingEvent *event) {
  EventLoopManagerSingleton::get()->getWifiRequestManager().handleRangingEvent(
      errorCode, event);
}

void PlatformWifiBase::scanMonitorStatusChangeCallback(bool enabled,
                                                       uint8_t errorCode) {
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleScanMonitorStateChange(enabled, errorCode);
}

void PlatformWifiBase::scanResponseCallback(bool pending, uint8_t errorCode) {
  EventLoopManagerSingleton::get()->getWifiRequestManager().handleScanResponse(
      pending, errorCode);
}

void PlatformWifiBase::scanEventCallback(struct chreWifiScanEvent *event) {
  EventLoopManagerSingleton::get()->getWifiRequestManager().handleScanEvent(
      event);
}

void PlatformWifiBase::nanServiceIdentifierCallback(uint8_t errorCode,
                                                    uint32_t subscriptionId) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleNanServiceIdentifierEvent(errorCode, subscriptionId);
#else
  UNUSED_VAR(errorCode);
  UNUSED_VAR(subscriptionId);
#endif
}

void PlatformWifiBase::nanServiceDiscoveryCallback(
    struct chreWifiNanDiscoveryEvent *event) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleNanServiceDiscoveryEvent(event);
#else
  UNUSED_VAR(event);
#endif
}

void PlatformWifiBase::nanServiceLostCallback(uint32_t subscriptionId,
                                              uint32_t publisherId) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleNanServiceLostEvent(subscriptionId, publisherId);
#else
  UNUSED_VAR(subscriptionId);
  UNUSED_VAR(publisherId);
#endif
}

void PlatformWifiBase::nanServiceTerminatedCallback(uint32_t errorCode,
                                                    uint32_t subscriptionId) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleNanServiceTerminatedEvent(errorCode, subscriptionId);
#else
  UNUSED_VAR(errorCode);
  UNUSED_VAR(subscriptionId);
#endif
}

void PlatformWifiBase::nanServiceSubscriptionCanceledCallback(
    uint8_t errorCode, uint32_t subscriptionId) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .handleNanServiceSubscriptionCanceledEvent(errorCode, subscriptionId);
#else
  UNUSED_VAR(errorCode);
  UNUSED_VAR(subscriptionId);
#endif
}

}  // namespace chre
