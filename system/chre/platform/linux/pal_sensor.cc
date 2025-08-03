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

#include "chre/pal/sensor.h"

#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/platform/memory.h"
#include "chre/util/macros.h"
#include "chre/util/memory.h"
#include "chre/util/unique_ptr.h"

#include <chrono>
#include <cinttypes>
#include <cstdint>

/**
 * A simulated implementation of the Sensor PAL for the linux platform.
 */
namespace {

using ::chre::TaskManagerSingleton;

const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalSensorCallbacks *gCallbacks = nullptr;

struct chreSensorInfo gSensors[] = {
    // Sensor 0 - Accelerometer.
    {
        .sensorName = "Test Accelerometer",
        .sensorType = CHRE_SENSOR_TYPE_UNCALIBRATED_ACCELEROMETER,
        .isOnChange = 0,
        .isOneShot = 0,
        .reportsBiasEvents = 0,
        .supportsPassiveMode = 0,
        .minInterval = 0,
        .sensorIndex = CHRE_SENSOR_INDEX_DEFAULT,
    },
};

//! Task to deliver asynchronous sensor data after a CHRE request.
std::optional<uint32_t> gSensor0TaskId;
bool gIsSensor0Enabled = false;

void stopSensor0Task() {
  if (gSensor0TaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(gSensor0TaskId.value());
    gSensor0TaskId.reset();
  }
}

void chrePalSensorApiClose() {
  stopSensor0Task();
}

bool chrePalSensorApiOpen(const struct chrePalSystemApi *systemApi,
                          const struct chrePalSensorCallbacks *callbacks) {
  chrePalSensorApiClose();

  bool success = false;
  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;
    success = true;
  }

  return success;
}

bool chrePalSensorApiGetSensors(const struct chreSensorInfo **sensors,
                                uint32_t *arraySize) {
  if (sensors != nullptr) {
    *sensors = gSensors;
  }
  if (arraySize != nullptr) {
    *arraySize = ARRAY_SIZE(gSensors);
  }
  return true;
}

void sendSensor0StatusUpdate(uint64_t intervalNs, bool enabled) {
  auto status = chre::MakeUniqueZeroFill<struct chreSensorSamplingStatus>();
  status->interval = intervalNs;
  status->latency = 0;
  status->enabled = enabled;
  gCallbacks->samplingStatusUpdateCallback(0, status.release());
}

void sendSensor0Events() {
  auto data = chre::MakeUniqueZeroFill<struct chreSensorThreeAxisData>();

  data->header.baseTimestamp = gSystemApi->getCurrentTime();
  data->header.sensorHandle = 0;
  data->header.readingCount = 1;
  data->header.accuracy = CHRE_SENSOR_ACCURACY_UNRELIABLE;
  data->header.reserved = 0;

  gCallbacks->dataEventCallback(0, data.release());
}

bool chrePalSensorApiConfigureSensor(uint32_t sensorInfoIndex,
                                     enum chreSensorConfigureMode mode,
                                     uint64_t intervalNs, uint64_t latencyNs) {
  UNUSED_VAR(latencyNs);
  if (sensorInfoIndex > ARRAY_SIZE(gSensors) - 1) {
    return false;
  }

  if (sensorInfoIndex != 0) {
    // Only sensor 0 is supported for now.
    return false;
  }

  if (mode == CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS) {
    stopSensor0Task();
    gIsSensor0Enabled = true;
    sendSensor0StatusUpdate(intervalNs, true /*enabled*/);
    gSensor0TaskId = TaskManagerSingleton::get()->addTask(
        sendSensor0Events, std::chrono::nanoseconds(intervalNs));
    return gSensor0TaskId.has_value();
  }

  if (mode == CHRE_SENSOR_CONFIGURE_MODE_DONE) {
    stopSensor0Task();
    gIsSensor0Enabled = false;
    sendSensor0StatusUpdate(intervalNs, false /*enabled*/);
    return true;
  }

  return false;
}

bool chrePalSensorApiFlush(uint32_t sensorInfoIndex, uint32_t *flushRequestId) {
  UNUSED_VAR(sensorInfoIndex);
  UNUSED_VAR(flushRequestId);
  return false;
}

bool chrePalSensorApiConfigureBiasEvents(uint32_t sensorInfoIndex, bool enable,
                                         uint64_t latencyNs) {
  UNUSED_VAR(sensorInfoIndex);
  UNUSED_VAR(enable);
  UNUSED_VAR(latencyNs);
  return false;
}

bool chrePalSensorApiGetThreeAxisBias(uint32_t sensorInfoIndex,
                                      struct chreSensorThreeAxisData *bias) {
  UNUSED_VAR(sensorInfoIndex);
  UNUSED_VAR(bias);
  return false;
}

void chrePalSensorApiReleaseSensorDataEvent(void *data) {
  chre::memoryFree(data);
}

void chrePalSensorApiReleaseSamplingStatusEvent(
    struct chreSensorSamplingStatus *status) {
  chre::memoryFree(status);
}

void chrePalSensorApiReleaseBiasEvent(void *bias) {
  chre::memoryFree(bias);
}

}  // namespace

bool chrePalSensorIsSensor0Enabled() {
  return gIsSensor0Enabled;
}

const chrePalSensorApi *chrePalSensorGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalSensorApi kApi = {
      .moduleVersion = CHRE_PAL_SENSOR_API_CURRENT_VERSION,
      .open = chrePalSensorApiOpen,
      .close = chrePalSensorApiClose,
      .getSensors = chrePalSensorApiGetSensors,
      .configureSensor = chrePalSensorApiConfigureSensor,
      .flush = chrePalSensorApiFlush,
      .configureBiasEvents = chrePalSensorApiConfigureBiasEvents,
      .getThreeAxisBias = chrePalSensorApiGetThreeAxisBias,
      .releaseSensorDataEvent = chrePalSensorApiReleaseSensorDataEvent,
      .releaseSamplingStatusEvent = chrePalSensorApiReleaseSamplingStatusEvent,
      .releaseBiasEvent = chrePalSensorApiReleaseBiasEvent,

  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    return &kApi;
  }
}