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

#include "chre/pal/ble.h"

#include "chre.h"
#include "chre/platform/linux/pal_ble.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/util/memory.h"
#include "chre/util/unique_ptr.h"

#include <chrono>
#include <optional>
#include <vector>

/**
 * A simulated implementation of the BLE PAL for the linux platform.
 */
namespace {

using ::chre::TaskManagerSingleton;

const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalBleCallbacks *gCallbacks = nullptr;

bool gBleEnabled = false;
bool gDelayScanStart = false;

std::mutex gBatchMutex;
std::vector<struct chreBleAdvertisementEvent *> gBatchedAdEvents;
std::chrono::time_point<std::chrono::steady_clock> gLastAdDataTimestamp;
std::optional<uint32_t> gReportDelayMs;
std::chrono::nanoseconds gScanInterval = std::chrono::milliseconds(1400);

// Tasks for startScan, sendAdReportEvents, and stopScan.
std::optional<uint32_t> gBleAdReportEventTaskId;
std::optional<uint32_t> gBleFlushTaskId;

void updateScanInterval(chreBleScanMode mode) {
  gScanInterval = std::chrono::milliseconds(1400);
  switch (mode) {
    case CHRE_BLE_SCAN_MODE_BACKGROUND:
      gScanInterval = std::chrono::milliseconds(1400);
      break;
    case CHRE_BLE_SCAN_MODE_FOREGROUND:
      gScanInterval = std::chrono::milliseconds(700);
      break;
    case CHRE_BLE_SCAN_MODE_AGGRESSIVE:
      gScanInterval = std::chrono::milliseconds(100);
      break;
  }
}

void flush() {
  std::lock_guard<std::mutex> lock(gBatchMutex);
  for (struct chreBleAdvertisementEvent *batchedEvent : gBatchedAdEvents) {
    gCallbacks->advertisingEventCallback(batchedEvent);
  }
  gBatchedAdEvents.clear();
  gLastAdDataTimestamp = std::chrono::steady_clock::now();
}

void sendAdReportEvents() {
  auto event = chre::MakeUniqueZeroFill<struct chreBleAdvertisementEvent>();
  auto report = chre::MakeUniqueZeroFill<struct chreBleAdvertisingReport>();
  uint8_t *data =
      static_cast<uint8_t *>(chre::memoryAlloc(sizeof(uint8_t) * 2));
  data[0] = 0x01;
  data[1] = 0x16;
  report->timestamp = chreGetTime();
  report->data = data;
  report->dataLength = 2;
  event->reports = report.release();
  event->numReports = 1;

  std::lock_guard<std::mutex> lock(gBatchMutex);
  if (!gReportDelayMs.has_value() || gReportDelayMs.value() == 0) {
    gCallbacks->advertisingEventCallback(event.release());
  } else {
    gBatchedAdEvents.push_back(event.release());
  }
}

void stopAllTasks() {
  if (gBleAdReportEventTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gBleAdReportEventTaskId.value());
    gBleAdReportEventTaskId.reset();
  }

  if (gBleFlushTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gBleFlushTaskId.value());
    gBleFlushTaskId.reset();
  }
}

bool startScan() {
  stopAllTasks();

  std::lock_guard<std::mutex> lock(gBatchMutex);
  gLastAdDataTimestamp = std::chrono::steady_clock::now();

  gBleAdReportEventTaskId =
      TaskManagerSingleton::get()->addTask(sendAdReportEvents, gScanInterval);
  if (!gBleAdReportEventTaskId.has_value()) {
    return false;
  }

  if (gReportDelayMs.has_value()) {
    gBleFlushTaskId = TaskManagerSingleton::get()->addTask(
        flush, std::chrono::duration_cast<std::chrono::nanoseconds>(
                   std::chrono::milliseconds(gReportDelayMs.value())));
    if (!gBleFlushTaskId.has_value()) {
      stopAllTasks();
      return false;
    }
  }

  std::optional<uint32_t> callbackTaskId = TaskManagerSingleton::get()->addTask(
      []() { gCallbacks->scanStatusChangeCallback(true, CHRE_ERROR_NONE); });
  if (!callbackTaskId.has_value()) {
    stopAllTasks();
    return false;
  }

  gBleEnabled = true;
  return true;
}

uint32_t chrePalBleGetCapabilities() {
  return CHRE_BLE_CAPABILITIES_SCAN |
         CHRE_BLE_CAPABILITIES_SCAN_RESULT_BATCHING |
         CHRE_BLE_CAPABILITIES_SCAN_FILTER_BEST_EFFORT;
}

uint32_t chrePalBleGetFilterCapabilities() {
  return CHRE_BLE_FILTER_CAPABILITIES_RSSI |
         CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA;
}

bool chrePalBleStartScan(chreBleScanMode mode, uint32_t reportDelayMs,
                         const struct chreBleScanFilterV1_9 * /* filter */) {
  {
    std::lock_guard<std::mutex> lock(gBatchMutex);

    if (gReportDelayMs.has_value()) {
      gReportDelayMs = std::min(gReportDelayMs.value(), reportDelayMs);
    } else {
      gReportDelayMs = reportDelayMs;
    }
  }

  updateScanInterval(mode);
  flush();
  return gDelayScanStart || startScan();
}

bool chrePalBleStopScan() {
  stopAllTasks();
  flush();

  std::optional<uint32_t> callbackTaskId = TaskManagerSingleton::get()->addTask(
      []() { gCallbacks->scanStatusChangeCallback(false, CHRE_ERROR_NONE); });

  // If the callback is successfully scheduled, then BLE is disabled.
  gBleEnabled = !callbackTaskId.has_value();
  return callbackTaskId.has_value();
}

void chrePalBleReleaseAdvertisingEvent(
    struct chreBleAdvertisementEvent *event) {
  for (size_t i = 0; i < event->numReports; i++) {
    auto report = const_cast<chreBleAdvertisingReport *>(&(event->reports[i]));
    chre::memoryFree(const_cast<uint8_t *>(report->data));
  }
  chre::memoryFree(const_cast<chreBleAdvertisingReport *>(event->reports));
  chre::memoryFree(event);
}

bool chrePalBleReadRssi(uint16_t connectionHandle) {
  std::optional<uint32_t> readRssiTaskId =
      TaskManagerSingleton::get()->addTask([connectionHandle]() {
        gCallbacks->readRssiCallback(CHRE_ERROR_NONE, connectionHandle, -65);
      });

  return readRssiTaskId.has_value();
}

bool chrePalBleFlush() {
  std::optional<uint32_t> flushTaskId =
      TaskManagerSingleton::get()->addTask([]() {
        flush();
        gCallbacks->flushCallback(CHRE_ERROR_NONE);
      });

  return flushTaskId.has_value();
}

void chrePalBleApiClose() {
  stopAllTasks();

  {
    std::lock_guard<std::mutex> lock(gBatchMutex);

    for (struct chreBleAdvertisementEvent *batchedEvent : gBatchedAdEvents) {
      chrePalBleReleaseAdvertisingEvent(batchedEvent);
    }
  }
}

bool chrePalBleApiOpen(const struct chrePalSystemApi *systemApi,
                       const struct chrePalBleCallbacks *callbacks) {
  chrePalBleApiClose();

  bool success = false;
  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;
    success = true;
  }

  return success;
}

}  // anonymous namespace

bool chrePalIsBleEnabled() {
  return gBleEnabled;
}

void delayBleScanStart(bool delay) {
  gDelayScanStart = delay;
}

bool startBleScan() {
  return startScan();
}

const struct chrePalBleApi *chrePalBleGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalBleApi kApi = {
      .moduleVersion = CHRE_PAL_BLE_API_CURRENT_VERSION,
      .open = chrePalBleApiOpen,
      .close = chrePalBleApiClose,
      .getCapabilities = chrePalBleGetCapabilities,
      .getFilterCapabilities = chrePalBleGetFilterCapabilities,
      .startScan = chrePalBleStartScan,
      .stopScan = chrePalBleStopScan,
      .releaseAdvertisingEvent = chrePalBleReleaseAdvertisingEvent,
      .readRssi = chrePalBleReadRssi,
      .flush = chrePalBleFlush,
  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    return &kApi;
  }
}
