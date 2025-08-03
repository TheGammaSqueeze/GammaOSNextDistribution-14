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

#include "chre/core/event_loop_manager.h"
#include "chre/core/sensor_request.h"
#include "chre/core/sensor_type_helpers.h"
#include "chre/util/macros.h"
#include "chre/util/time.h"
#include "chre_api/chre/sensor.h"

using chre::EventLoopManager;
using chre::EventLoopManagerSingleton;
using chre::Nanoseconds;
using chre::SensorMode;
using chre::SensorRequest;

using chre::getSensorModeFromEnum;

DLL_EXPORT bool chreSensorFindDefault(uint8_t sensorType, uint32_t *handle) {
  return chreSensorFind(sensorType, CHRE_SENSOR_INDEX_DEFAULT, handle);
}

DLL_EXPORT bool chreSensorFind(uint8_t sensorType, uint8_t sensorIndex,
                               uint32_t *handle) {
#if CHRE_SENSORS_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return EventLoopManagerSingleton::get()
      ->getSensorRequestManager()
      .getSensorHandleForNanoapp(sensorType, sensorIndex, *nanoapp, handle);
#else  // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorType);
  UNUSED_VAR(sensorIndex);
  UNUSED_VAR(handle);
  return false;
#endif
}

DLL_EXPORT bool chreGetSensorInfo(uint32_t sensorHandle,
                                  struct chreSensorInfo *info) {
#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  CHRE_ASSERT(info);

  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);

  bool success = false;
  if (info != nullptr) {
    success = EventLoopManagerSingleton::get()
                  ->getSensorRequestManager()
                  .getSensorInfo(sensorHandle, *nanoapp, info);
  }
  return success;
#else   // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorHandle);
  UNUSED_VAR(info);
  return false;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED
}

DLL_EXPORT bool chreGetSensorSamplingStatus(
    uint32_t sensorHandle, struct chreSensorSamplingStatus *status) {
#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  CHRE_ASSERT(status);

  bool success = false;
  if (status != nullptr) {
    success = EventLoopManagerSingleton::get()
                  ->getSensorRequestManager()
                  .getSensorSamplingStatus(sensorHandle, status);
  }
  return success;
#else   // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorHandle);
  UNUSED_VAR(status);
  return false;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED
}

DLL_EXPORT bool chreSensorConfigure(uint32_t sensorHandle,
                                    enum chreSensorConfigureMode mode,
                                    uint64_t interval, uint64_t latency) {
#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  SensorMode sensorMode = getSensorModeFromEnum(mode);
  SensorRequest sensorRequest(nanoapp->getInstanceId(), sensorMode,
                              Nanoseconds(interval), Nanoseconds(latency));
  return EventLoopManagerSingleton::get()
      ->getSensorRequestManager()
      .setSensorRequest(nanoapp, sensorHandle, sensorRequest);
#else   // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorHandle);
  UNUSED_VAR(mode);
  UNUSED_VAR(interval);
  UNUSED_VAR(latency);
  return false;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED
}

DLL_EXPORT bool chreSensorConfigureBiasEvents(uint32_t sensorHandle,
                                              bool enable) {
#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return EventLoopManagerSingleton::get()
      ->getSensorRequestManager()
      .configureBiasEvents(nanoapp, sensorHandle, enable);
#else   // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorHandle);
  UNUSED_VAR(enable);
  return false;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED
}

DLL_EXPORT bool chreSensorGetThreeAxisBias(
    uint32_t sensorHandle, struct chreSensorThreeAxisData *bias) {
#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  return EventLoopManagerSingleton::get()
      ->getSensorRequestManager()
      .getThreeAxisBias(sensorHandle, bias);
#else   // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorHandle);
  UNUSED_VAR(bias);
  return false;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED
}

DLL_EXPORT bool chreSensorFlushAsync(uint32_t sensorHandle,
                                     const void *cookie) {
#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return EventLoopManagerSingleton::get()->getSensorRequestManager().flushAsync(
      nanoapp, sensorHandle, cookie);
#else   // CHRE_SENSORS_SUPPORT_ENABLED
  UNUSED_VAR(sensorHandle);
  UNUSED_VAR(cookie);
  return false;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED
}
