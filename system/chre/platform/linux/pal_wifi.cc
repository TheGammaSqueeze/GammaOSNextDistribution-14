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

#include "chre/platform/linux/pal_wifi.h"

#include <atomic>
#include <chrono>
#include <cinttypes>
#include <optional>

#include "chre/pal/wifi.h"
#include "chre/platform/linux/pal_nan.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/util/enum.h"
#include "chre/util/memory.h"
#include "chre/util/time.h"
#include "chre/util/unique_ptr.h"

/**
 * A simulated implementation of the WiFi PAL for the linux platform.
 */
namespace {

using ::chre::TaskManagerSingleton;

const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalWifiCallbacks *gCallbacks = nullptr;

//! Whether scan monitoring is active.
std::atomic_bool gScanMonitoringActive(false);

//! Whether PAL should respond to RRT ranging request.
std::atomic_bool gEnableRangingResponse(true);

//! Whether PAL should respond to configure scan monitor request.
std::atomic_bool gEnableScanMonitorResponse(true);

//! Whether PAL should respond to scan request.
std::atomic_bool gEnableScanResponse(true);

//! Thread sync variable for TaskIds.
std::mutex gRequestScanMutex;

//! Task IDs for the scanning tasks
std::optional<uint32_t> gScanMonitorTaskId;
std::optional<uint32_t> gRequestScanTaskId;
std::optional<uint32_t> gRequestRangingTaskId;

//! How long should each the PAL hold before response.
//! Use to mimic real world hardware process time.
std::chrono::nanoseconds gAsyncRequestDelayResponseTime[chre::asBaseType(
    PalWifiAsyncRequestTypes::NUM_WIFI_REQUEST_TYPE)];

void sendScanResponse() {
  {
    std::lock_guard<std::mutex> lock(gRequestScanMutex);
    if (!gRequestScanTaskId.has_value()) {
      LOGE("Sending scan response with no pending task");
      return;
    }
    gRequestScanTaskId.reset();
  }

  if (gEnableScanResponse) {
    auto event = chre::MakeUniqueZeroFill<struct chreWifiScanEvent>();
    auto result = chre::MakeUniqueZeroFill<struct chreWifiScanResult>();
    event->resultCount = 1;
    event->resultTotal = 1;
    event->referenceTime = gSystemApi->getCurrentTime();
    event->results = result.release();
    gCallbacks->scanEventCallback(event.release());
  }
}

void sendScanMonitorResponse(bool enable) {
  if (gEnableScanMonitorResponse) {
    gCallbacks->scanMonitorStatusChangeCallback(enable, CHRE_ERROR_NONE);
  }
}

void sendRangingResponse() {
  if (gEnableRangingResponse) {
    auto event = chre::MakeUniqueZeroFill<struct chreWifiRangingEvent>();
    auto result = chre::MakeUniqueZeroFill<struct chreWifiRangingResult>();
    event->resultCount = 1;
    event->results = result.release();
    gCallbacks->rangingEventCallback(CHRE_ERROR_NONE, event.release());
  }
}

void stopScanMonitorTask() {
  if (gScanMonitorTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gScanMonitorTaskId.value());
    gScanMonitorTaskId.reset();
  }
}

void stopRequestScanTask() {
  if (gRequestScanTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gRequestScanTaskId.value());
    gRequestScanTaskId.reset();
  }
}

void stopRequestRangingTask() {
  if (gRequestRangingTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gRequestRangingTaskId.value());
    gRequestRangingTaskId.reset();
  }
}

uint32_t chrePalWifiGetCapabilities() {
  return CHRE_WIFI_CAPABILITIES_SCAN_MONITORING |
         CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN | CHRE_WIFI_CAPABILITIES_NAN_SUB;
}

bool chrePalWifiConfigureScanMonitor(bool enable) {
  stopScanMonitorTask();

  gScanMonitorTaskId = TaskManagerSingleton::get()->addTask(
      [enable]() { sendScanMonitorResponse(enable); });
  gScanMonitoringActive = enable;
  return gScanMonitorTaskId.has_value();
}

bool chrePalWifiApiRequestScan(const struct chreWifiScanParams * /* params */) {
  std::lock_guard<std::mutex> lock(gRequestScanMutex);
  if (gRequestScanTaskId.has_value()) {
    LOGE("Requesting scan when existing scan request still in process");
    return false;
  }

  std::optional<uint32_t> requestScanTaskCallbackId =
      TaskManagerSingleton::get()->addTask([]() {
        if (gEnableScanResponse) {
          gCallbacks->scanResponseCallback(true, CHRE_ERROR_NONE);
        }
      });
  if (requestScanTaskCallbackId.has_value()) {
    gRequestScanTaskId = TaskManagerSingleton::get()->addTask(
        sendScanResponse,
        gAsyncRequestDelayResponseTime[chre::asBaseType(
            PalWifiAsyncRequestTypes::SCAN)],
        /* isOneShot= */ true);
    return gRequestScanTaskId.has_value();
  }
  return false;
}

bool chrePalWifiApiRequestRanging(
    const struct chreWifiRangingParams * /* params */) {
  stopRequestRangingTask();

  gRequestRangingTaskId =
      TaskManagerSingleton::get()->addTask(sendRangingResponse);
  return gRequestRangingTaskId.has_value();
}

void chrePalWifiApiReleaseScanEvent(struct chreWifiScanEvent *event) {
  chre::memoryFree(const_cast<uint32_t *>(event->scannedFreqList));
  chre::memoryFree(const_cast<struct chreWifiScanResult *>(event->results));
  chre::memoryFree(event);
}

void chrePalWifiApiReleaseRangingEvent(struct chreWifiRangingEvent *event) {
  chre::memoryFree(const_cast<struct chreWifiRangingResult *>(event->results));
  chre::memoryFree(event);
}

bool chrePalWifiApiNanSubscribe(
    const struct chreWifiNanSubscribeConfig *config) {
  uint32_t subscriptionId = 0;
  uint8_t errorCode =
      chre::PalNanEngineSingleton::get()->subscribe(config, &subscriptionId);

  gCallbacks->nanServiceIdentifierCallback(errorCode, subscriptionId);

  return true;
}

bool chrePalWifiApiNanSubscribeCancel(const uint32_t subscriptionId) {
  gCallbacks->nanSubscriptionCanceledCallback(CHRE_ERROR_NONE, subscriptionId);
  return chre::PalNanEngineSingleton::get()->subscribeCancel(subscriptionId);
}

void chrePalWifiApiNanReleaseDiscoveryEvent(
    struct chreWifiNanDiscoveryEvent *event) {
  chre::PalNanEngineSingleton::get()->destroyDiscoveryEvent(event);
}

bool chrePalWifiApiRequestNanRanging(
    const struct chreWifiNanRangingParams *params) {
  constexpr uint32_t kFakeRangeMeasurementMm = 1000;

  auto *event = chre::memoryAlloc<struct chreWifiRangingEvent>();
  CHRE_ASSERT_NOT_NULL(event);

  auto *result = chre::memoryAlloc<struct chreWifiRangingResult>();
  CHRE_ASSERT_NOT_NULL(result);

  std::memcpy(result->macAddress, params->macAddress, CHRE_WIFI_BSSID_LEN);
  result->status = CHRE_WIFI_RANGING_STATUS_SUCCESS;
  result->distance = kFakeRangeMeasurementMm;
  event->resultCount = 1;
  event->results = result;

  gCallbacks->rangingEventCallback(CHRE_ERROR_NONE, event);

  return true;
}

void chrePalWifiApiClose() {
  stopScanMonitorTask();
  stopRequestScanTask();
  stopRequestRangingTask();
}

bool chrePalWifiApiOpen(const struct chrePalSystemApi *systemApi,
                        const struct chrePalWifiCallbacks *callbacks) {
  chrePalWifiApiClose();

  bool success = false;
  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;

    chre::PalNanEngineSingleton::get()->setPlatformWifiCallbacks(callbacks);

    success = true;
  }

  return success;
}

}  // anonymous namespace

void chrePalWifiEnableResponse(PalWifiAsyncRequestTypes requestType,
                               bool enableResponse) {
  switch (requestType) {
    case PalWifiAsyncRequestTypes::RANGING:
      gEnableRangingResponse = enableResponse;
      break;

    case PalWifiAsyncRequestTypes::SCAN_MONITORING:
      gEnableScanMonitorResponse = enableResponse;
      break;

    case PalWifiAsyncRequestTypes::SCAN:
      gEnableScanResponse = enableResponse;
      break;

    default:
      LOGE("Cannot enable/disable request type: %" PRIu8,
           static_cast<uint8_t>(requestType));
  }
}

bool chrePalWifiIsScanMonitoringActive() {
  return gScanMonitoringActive;
}

void chrePalWifiDelayResponse(PalWifiAsyncRequestTypes requestType,
                              std::chrono::seconds seconds) {
  gAsyncRequestDelayResponseTime[chre::asBaseType(requestType)] =
      std::chrono::duration_cast<std::chrono::nanoseconds>(seconds);
}

const struct chrePalWifiApi *chrePalWifiGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalWifiApi kApi = {
      .moduleVersion = CHRE_PAL_WIFI_API_CURRENT_VERSION,
      .open = chrePalWifiApiOpen,
      .close = chrePalWifiApiClose,
      .getCapabilities = chrePalWifiGetCapabilities,
      .configureScanMonitor = chrePalWifiConfigureScanMonitor,
      .requestScan = chrePalWifiApiRequestScan,
      .releaseScanEvent = chrePalWifiApiReleaseScanEvent,
      .requestRanging = chrePalWifiApiRequestRanging,
      .releaseRangingEvent = chrePalWifiApiReleaseRangingEvent,
      .nanSubscribe = chrePalWifiApiNanSubscribe,
      .nanSubscribeCancel = chrePalWifiApiNanSubscribeCancel,
      .releaseNanDiscoveryEvent = chrePalWifiApiNanReleaseDiscoveryEvent,
      .requestNanRanging = chrePalWifiApiRequestNanRanging,
  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    chre::PalNanEngineSingleton::init();
    return &kApi;
  }
}
