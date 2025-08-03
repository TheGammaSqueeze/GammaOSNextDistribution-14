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

#include "chre/platform/linux/pal_gnss.h"
#include "chre/pal/gnss.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/platform/log.h"

#include "chre/util/memory.h"
#include "chre/util/unique_ptr.h"

#include <chrono>
#include <cinttypes>
#include <mutex>
#include <optional>

/**
 * A simulated implementation of the GNSS PAL for the linux platform.
 */
namespace {

using ::chre::TaskManagerSingleton;

const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalGnssCallbacks *gCallbacks = nullptr;

// Task to deliver asynchronous location data after a CHRE request.
std::mutex gLocationEventsMutex;
std::optional<uint32_t> gLocationEventsTaskId;
uint32_t gLocationEventsMinIntervalMs = 0;
bool gDelaySendingLocationEvents = false;
bool gIsLocationEnabled = false;

// Task to use when delivering a location status update.
std::optional<uint32_t> gLocationStatusTaskId;

// Task to deliver asynchronous measurement data after a CHRE request.
std::optional<uint32_t> gMeasurementEventsTaskId;
bool gIsMeasurementEnabled = false;

// Task to use when delivering a measurement status update.
std::optional<uint32_t> gMeasurementStatusTaskId;

// Passive listener flag.
bool gIsPassiveListenerEnabled = false;

void sendLocationEvents() {
  if (!gIsLocationEnabled) {
    return;
  }

  auto event = chre::MakeUniqueZeroFill<struct chreGnssLocationEvent>();
  event->timestamp = gSystemApi->getCurrentTime();
  gCallbacks->locationEventCallback(event.release());
}

void startSendingLocationEvents(uint32_t minIntervalMs) {
  std::lock_guard<std::mutex> lock(gLocationEventsMutex);
  if (gLocationEventsTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gLocationEventsTaskId.value());
    gLocationEventsTaskId.reset();
  }

  TaskManagerSingleton::get()->addTask(
      []() { gCallbacks->locationStatusChangeCallback(true, CHRE_ERROR_NONE); },
      std::chrono::nanoseconds(0), true /* isOneShot */);

  gLocationEventsTaskId = TaskManagerSingleton::get()->addTask(
      sendLocationEvents, std::chrono::milliseconds(minIntervalMs));
}

void sendMeasurementEvents() {
  if (!gIsMeasurementEnabled) {
    return;
  }

  auto event = chre::MakeUniqueZeroFill<struct chreGnssDataEvent>();
  auto measurement = chre::MakeUniqueZeroFill<struct chreGnssMeasurement>();
  measurement->c_n0_dbhz = 63.0f;
  event->measurement_count = 1;
  event->clock.time_ns = static_cast<int64_t>(gSystemApi->getCurrentTime());
  event->measurements = measurement.release();
  gCallbacks->measurementEventCallback(event.release());
}

void stopLocation() {
  gCallbacks->locationStatusChangeCallback(false, CHRE_ERROR_NONE);
}

void stopMeasurement() {
  gCallbacks->measurementStatusChangeCallback(false, CHRE_ERROR_NONE);
}

void stopLocationTasks() {
  {
    std::lock_guard<std::mutex> lock(gLocationEventsMutex);

    if (gLocationEventsTaskId.has_value()) {
      TaskManagerSingleton::get()->cancelTask(gLocationEventsTaskId.value());
      gLocationEventsTaskId.reset();
    }
  }

  if (gLocationStatusTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gLocationStatusTaskId.value());
    gLocationStatusTaskId.reset();
  }
}

void stopMeasurementTasks() {
  if (gMeasurementEventsTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gMeasurementEventsTaskId.value());
    gMeasurementEventsTaskId.reset();
  }

  if (gMeasurementStatusTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gMeasurementStatusTaskId.value());
    gMeasurementStatusTaskId.reset();
  }
}

uint32_t chrePalGnssGetCapabilities() {
  return CHRE_GNSS_CAPABILITIES_LOCATION | CHRE_GNSS_CAPABILITIES_MEASUREMENTS |
         CHRE_GNSS_CAPABILITIES_GNSS_ENGINE_BASED_PASSIVE_LISTENER;
}

bool chrePalControlLocationSession(bool enable, uint32_t minIntervalMs,
                                   uint32_t /* minTimeToNextFixMs */) {
  stopLocationTasks();

  gLocationEventsMinIntervalMs = minIntervalMs;
  if (enable && !gDelaySendingLocationEvents) {
    startSendingLocationEvents(minIntervalMs);
    if (!gLocationEventsTaskId.has_value()) {
      return false;
    }
  } else if (!enable) {
    gLocationStatusTaskId = TaskManagerSingleton::get()->addTask(stopLocation);
    if (!gLocationStatusTaskId.has_value()) {
      return false;
    }
  }

  gIsLocationEnabled = enable;
  return true;
}

void chrePalGnssReleaseLocationEvent(struct chreGnssLocationEvent *event) {
  chre::memoryFree(event);
}

bool chrePalControlMeasurementSession(bool enable, uint32_t minIntervalMs) {
  stopMeasurementTasks();

  if (enable) {
    std::optional<uint32_t> measurementEventsChangeCallbackTaskId =
        TaskManagerSingleton::get()->addTask(
            []() {
              gCallbacks->measurementStatusChangeCallback(true,
                                                          CHRE_ERROR_NONE);
            },
            std::chrono::nanoseconds(0), true /* isOneShot */);
    if (!measurementEventsChangeCallbackTaskId.has_value()) {
      return false;
    }

    gMeasurementEventsTaskId = TaskManagerSingleton::get()->addTask(
        sendMeasurementEvents, std::chrono::milliseconds(minIntervalMs));
    if (!gMeasurementEventsTaskId.has_value()) {
      return false;
    }
  } else {
    gMeasurementStatusTaskId =
        TaskManagerSingleton::get()->addTask(stopMeasurement);
    if (!gMeasurementStatusTaskId.has_value()) {
      return false;
    }
  }

  gIsMeasurementEnabled = enable;
  return true;
}

void chrePalGnssReleaseMeasurementDataEvent(struct chreGnssDataEvent *event) {
  chre::memoryFree(
      const_cast<struct chreGnssMeasurement *>(event->measurements));
  chre::memoryFree(event);
}

void chrePalGnssApiClose() {
  stopLocationTasks();
  stopMeasurementTasks();
}

bool chrePalGnssApiOpen(const struct chrePalSystemApi *systemApi,
                        const struct chrePalGnssCallbacks *callbacks) {
  chrePalGnssApiClose();

  bool success = false;
  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;
    success = true;
  }

  return success;
}

bool chrePalGnssconfigurePassiveLocationListener(bool enable) {
  gIsPassiveListenerEnabled = enable;
  return true;
}

}  // anonymous namespace

bool chrePalGnssIsLocationEnabled() {
  return gIsLocationEnabled;
}

bool chrePalGnssIsMeasurementEnabled() {
  return gIsMeasurementEnabled;
}

bool chrePalGnssIsPassiveLocationListenerEnabled() {
  return gIsPassiveListenerEnabled;
}

void chrePalGnssDelaySendingLocationEvents(bool enabled) {
  gDelaySendingLocationEvents = enabled;
}

void chrePalGnssStartSendingLocationEvents() {
  CHRE_ASSERT(gDelaySendingLocationEvents);
  startSendingLocationEvents(gLocationEventsMinIntervalMs);
}

const struct chrePalGnssApi *chrePalGnssGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalGnssApi kApi = {
      .moduleVersion = CHRE_PAL_GNSS_API_CURRENT_VERSION,
      .open = chrePalGnssApiOpen,
      .close = chrePalGnssApiClose,
      .getCapabilities = chrePalGnssGetCapabilities,
      .controlLocationSession = chrePalControlLocationSession,
      .releaseLocationEvent = chrePalGnssReleaseLocationEvent,
      .controlMeasurementSession = chrePalControlMeasurementSession,
      .releaseMeasurementDataEvent = chrePalGnssReleaseMeasurementDataEvent,
      .configurePassiveLocationListener =
          chrePalGnssconfigurePassiveLocationListener,
  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    return &kApi;
  }
}
