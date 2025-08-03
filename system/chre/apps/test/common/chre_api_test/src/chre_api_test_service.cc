/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "chre_api_test_manager.h"

#include "chre/util/nanoapp/ble.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/nanoapp/string.h"

using ::chre::copyString;
using ::chre::createBleGenericFilter;

namespace {

/**
 * The following constants are defined in chre_api_test.options.
 */
constexpr size_t kMaxNameStringBufferSize = 100;
constexpr size_t kMaxHostEndpointNameBufferSize = 51;
constexpr size_t kMaxHostEndpointTagBufferSize = 51;
}  // namespace

bool ChreApiTestService::validateInputAndCallChreBleGetCapabilities(
    const google_protobuf_Empty & /* request */,
    chre_rpc_Capabilities &response) {
  response.capabilities = chreBleGetCapabilities();
  LOGD("ChreBleGetCapabilities: capabilities: %" PRIu32, response.capabilities);
  return true;
}

bool ChreApiTestService::validateInputAndCallChreBleGetFilterCapabilities(
    const google_protobuf_Empty & /* request */,
    chre_rpc_Capabilities &response) {
  response.capabilities = chreBleGetFilterCapabilities();
  LOGD("ChreBleGetFilterCapabilities: capabilities: %" PRIu32,
       response.capabilities);
  return true;
}

bool ChreApiTestService::validateInputAndCallChreBleStartScanAsync(
    const chre_rpc_ChreBleStartScanAsyncInput &request,
    chre_rpc_Status &response) {
  if (request.mode < _chre_rpc_ChreBleScanMode_MIN ||
      request.mode > _chre_rpc_ChreBleScanMode_MAX ||
      request.mode == chre_rpc_ChreBleScanMode_INVALID) {
    LOGE("ChreBleStartScanAsync: invalid mode");
    return false;
  }

  if (!request.hasFilter) {
    auto mode = static_cast<chreBleScanMode>(request.mode);
    response.status =
        chreBleStartScanAsync(mode, request.reportDelayMs, nullptr);

    LOGD("ChreBleStartScanAsync: mode: %s, reportDelayMs: %" PRIu32
         ", filter: nullptr, status: %s",
         mode == CHRE_BLE_SCAN_MODE_BACKGROUND
             ? "background"
             : (mode == CHRE_BLE_SCAN_MODE_FOREGROUND ? "foreground"
                                                      : "aggressive"),
         request.reportDelayMs, response.status ? "true" : "false");
    return true;
  }

  if (request.filter.rssiThreshold < std::numeric_limits<int8_t>::min() ||
      request.filter.rssiThreshold > std::numeric_limits<int8_t>::max()) {
    LOGE("ChreBleStartScanAsync: invalid filter.rssiThreshold");
    return false;
  }

  if (request.filter.scanFilters_count == 0) {
    LOGE("ChreBleStartScanAsync: invalid filter.scanFilters_count");
    return false;
  }

  chreBleGenericFilter genericFilters[request.filter.scanFilters_count];
  if (!validateBleScanFilters(request.filter.scanFilters, genericFilters,
                              request.filter.scanFilters_count)) {
    return false;
  }

  struct chreBleScanFilter filter;
  filter.rssiThreshold = request.filter.rssiThreshold;
  filter.scanFilterCount = request.filter.scanFilters_count;
  filter.scanFilters = genericFilters;

  auto mode = static_cast<chreBleScanMode>(request.mode);
  response.status = chreBleStartScanAsync(mode, request.reportDelayMs, &filter);

  LOGD("ChreBleStartScanAsync: mode: %s, reportDelayMs: %" PRIu32
       ", scanFilterCount: %" PRIu16 ", status: %s",
       mode == CHRE_BLE_SCAN_MODE_BACKGROUND
           ? "background"
           : (mode == CHRE_BLE_SCAN_MODE_FOREGROUND ? "foreground"
                                                    : "aggressive"),
       request.reportDelayMs, request.filter.scanFilters_count,
       response.status ? "true" : "false");
  return true;
}

bool ChreApiTestService::validateInputAndCallChreBleStopScanAsync(
    const google_protobuf_Empty & /* request */, chre_rpc_Status &response) {
  response.status = chreBleStopScanAsync();
  LOGD("ChreBleStopScanAsync: status: %s", response.status ? "true" : "false");
  return true;
}

bool ChreApiTestService::validateInputAndCallChreSensorFindDefault(
    const chre_rpc_ChreSensorFindDefaultInput &request,
    chre_rpc_ChreSensorFindDefaultOutput &response) {
  if (request.sensorType > std::numeric_limits<uint8_t>::max()) {
    return false;
  }

  auto sensorType = static_cast<uint8_t>(request.sensorType);
  response.foundSensor =
      chreSensorFindDefault(sensorType, &response.sensorHandle);

  LOGD("ChreSensorFindDefault: foundSensor: %s, sensorHandle: %" PRIu32,
       response.foundSensor ? "true" : "false", response.sensorHandle);
  return true;
}

bool ChreApiTestService::validateInputAndCallChreGetSensorInfo(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreGetSensorInfoOutput &response) {
  struct chreSensorInfo sensorInfo;
  memset(&sensorInfo, 0, sizeof(sensorInfo));

  response.status = chreGetSensorInfo(request.handle, &sensorInfo);

  if (response.status) {
    copyString(response.sensorName, sensorInfo.sensorName,
               kMaxNameStringBufferSize);
    response.sensorType = sensorInfo.sensorType;
    response.isOnChange = sensorInfo.isOnChange;
    response.isOneShot = sensorInfo.isOneShot;
    response.reportsBiasEvents = sensorInfo.reportsBiasEvents;
    response.supportsPassiveMode = sensorInfo.supportsPassiveMode;
    response.unusedFlags = sensorInfo.unusedFlags;
    response.minInterval = sensorInfo.minInterval;
    response.sensorIndex = sensorInfo.sensorIndex;

    LOGD("ChreGetSensorInfo: status: true, sensorType: %" PRIu32
         ", isOnChange: %" PRIu32
         ", "
         "isOneShot: %" PRIu32 ", reportsBiasEvents: %" PRIu32
         ", supportsPassiveMode: %" PRIu32 ", unusedFlags: %" PRIu32
         ", minInterval: %" PRIu64 ", sensorIndex: %" PRIu32,
         response.sensorType, response.isOnChange, response.isOneShot,
         response.reportsBiasEvents, response.supportsPassiveMode,
         response.unusedFlags, response.minInterval, response.sensorIndex);
  } else {
    LOGD("ChreGetSensorInfo: status: false");
  }

  return true;
}

bool ChreApiTestService::validateInputAndCallChreGetSensorSamplingStatus(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreGetSensorSamplingStatusOutput &response) {
  struct chreSensorSamplingStatus samplingStatus;
  memset(&samplingStatus, 0, sizeof(samplingStatus));

  response.status =
      chreGetSensorSamplingStatus(request.handle, &samplingStatus);
  if (response.status) {
    response.interval = samplingStatus.interval;
    response.latency = samplingStatus.latency;
    response.enabled = samplingStatus.enabled;

    LOGD("ChreGetSensorSamplingStatus: status: true, interval: %" PRIu64
         ", latency: %" PRIu64 ", enabled: %s",
         response.interval, response.latency,
         response.enabled ? "true" : "false");
  } else {
    LOGD("ChreGetSensorSamplingStatus: status: false");
  }

  return true;
}

bool ChreApiTestService::validateInputAndCallChreSensorConfigure(
    const chre_rpc_ChreSensorConfigureInput &request,
    chre_rpc_Status &response) {
  auto mode = static_cast<chreSensorConfigureMode>(request.mode);
  response.status = chreSensorConfigure(request.sensorHandle, mode,
                                        request.interval, request.latency);

  LOGD("ChreSensorConfigure: status: %s", response.status ? "true" : "false");
  return true;
}

bool ChreApiTestService::validateInputAndCallChreSensorConfigureModeOnly(
    const chre_rpc_ChreSensorConfigureModeOnlyInput &request,
    chre_rpc_Status &response) {
  auto mode = static_cast<chreSensorConfigureMode>(request.mode);
  response.status = chreSensorConfigureModeOnly(request.sensorHandle, mode);

  LOGD("ChreSensorConfigureModeOnly: status: %s",
       response.status ? "true" : "false");
  return true;
}

bool ChreApiTestService::validateInputAndCallChreAudioGetSource(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreAudioGetSourceOutput &response) {
  struct chreAudioSource audioSource;
  memset(&audioSource, 0, sizeof(audioSource));
  response.status = chreAudioGetSource(request.handle, &audioSource);

  if (response.status) {
    copyString(response.name, audioSource.name, kMaxNameStringBufferSize);
    response.sampleRate = audioSource.sampleRate;
    response.minBufferDuration = audioSource.minBufferDuration;
    response.maxBufferDuration = audioSource.maxBufferDuration;
    response.format = audioSource.format;

    LOGD("ChreAudioGetSource: status: true, name: %s, sampleRate %" PRIu32
         ", minBufferDuration: %" PRIu64 ", maxBufferDuration %" PRIu64
         ", format: %" PRIu32,
         response.name, response.sampleRate, response.minBufferDuration,
         response.maxBufferDuration, response.format);
  } else {
    LOGD("ChreAudioGetSource: status: false");
  }

  return true;
}

bool ChreApiTestService::validateInputAndCallChreAudioConfigureSource(
    const chre_rpc_ChreAudioConfigureSourceInput &request,
    chre_rpc_Status &response) {
  response.status = chreAudioConfigureSource(request.handle, request.enable,
                                             request.bufferDuration,
                                             request.deliveryInterval);
  LOGD("ChreAudioConfigureSource: status: %s",
       response.status ? "true" : "false");

  return true;
}

bool ChreApiTestService::validateInputAndCallChreAudioGetStatus(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreAudioGetStatusOutput &response) {
  UNUSED_VAR(request);
  UNUSED_VAR(response);
  // TODO(b/174590023): Fill in when chreAudioGetStatus is implemented
  return false;
}

bool ChreApiTestService::
    validateInputAndCallChreConfigureHostEndpointNotifications(
        const chre_rpc_ChreConfigureHostEndpointNotificationsInput &request,
        chre_rpc_Status &response) {
  if (request.hostEndpointId > std::numeric_limits<uint16_t>::max()) {
    LOGE("Host Endpoint Id cannot exceed max of uint16_t");
    return false;
  }

  response.status = chreConfigureHostEndpointNotifications(
      request.hostEndpointId, request.enable);
  LOGD("ChreConfigureHostEndpointNotifications: status: %s",
       response.status ? "true" : "false");
  return true;
}

bool ChreApiTestService::validateInputAndCallChreGetHostEndpointInfo(
    const chre_rpc_ChreGetHostEndpointInfoInput &request,
    chre_rpc_ChreGetHostEndpointInfoOutput &response) {
  if (request.hostEndpointId > std::numeric_limits<uint16_t>::max()) {
    LOGE("Host Endpoint Id cannot exceed max of uint16_t");
    return false;
  }

  struct chreHostEndpointInfo hostEndpointInfo;
  memset(&hostEndpointInfo, 0, sizeof(hostEndpointInfo));
  response.status =
      chreGetHostEndpointInfo(request.hostEndpointId, &hostEndpointInfo);

  if (response.status) {
    response.hostEndpointId = hostEndpointInfo.hostEndpointId;
    response.hostEndpointType = hostEndpointInfo.hostEndpointType;
    response.isNameValid = hostEndpointInfo.isNameValid;
    response.isTagValid = hostEndpointInfo.isTagValid;
    if (hostEndpointInfo.isNameValid) {
      copyString(response.endpointName, hostEndpointInfo.endpointName,
                 kMaxHostEndpointNameBufferSize);
    } else {
      memset(response.endpointName, 0, kMaxHostEndpointNameBufferSize);
    }
    if (hostEndpointInfo.isTagValid) {
      copyString(response.endpointTag, hostEndpointInfo.endpointTag,
                 kMaxHostEndpointTagBufferSize);
    } else {
      memset(response.endpointTag, 0, kMaxHostEndpointTagBufferSize);
    }

    LOGD("ChreGetHostEndpointInfo: status: true, hostEndpointID: %" PRIu32
         ", hostEndpointType: %" PRIu32
         ", isNameValid: %s, isTagValid: %s, endpointName: %s, endpointTag: %s",
         response.hostEndpointId, response.hostEndpointType,
         response.isNameValid ? "true" : "false",
         response.isTagValid ? "true" : "false", response.endpointName,
         response.endpointTag);
  } else {
    LOGD("ChreGetHostEndpointInfo: status: false");
  }
  return true;
}

bool ChreApiTestService::validateBleScanFilters(
    const chre_rpc_ChreBleGenericFilter *scanFilters,
    chreBleGenericFilter *outputScanFilters, uint32_t scanFilterCount) {
  if (scanFilters == nullptr || outputScanFilters == nullptr) {
    return false;
  }

  for (uint32_t i = 0; i < scanFilterCount; ++i) {
    const chre_rpc_ChreBleGenericFilter &scanFilter = scanFilters[i];
    if (scanFilter.type > std::numeric_limits<uint8_t>::max() ||
        scanFilter.length > std::numeric_limits<uint8_t>::max()) {
      LOGE(
          "validateBleScanFilters: invalid request.filter.scanFilters member: "
          "type: %" PRIu32 " or length: %" PRIu32,
          scanFilter.type, scanFilter.length);
      return false;
    }

    if (scanFilter.data.size < scanFilter.length ||
        scanFilter.mask.size < scanFilter.length) {
      LOGE(
          "validateBleScanFilters: invalid request.filter.scanFilters member: "
          "data or mask size");
      return false;
    }

    outputScanFilters[i] =
        createBleGenericFilter(scanFilter.type, scanFilter.length,
                               scanFilter.data.bytes, scanFilter.mask.bytes);
  }

  return true;
}
