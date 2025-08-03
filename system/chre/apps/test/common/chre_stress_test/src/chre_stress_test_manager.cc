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

#include "chre_stress_test_manager.h"

#include <pb_decode.h>
#include <pb_encode.h>

#include "chre/util/macros.h"
#include "chre/util/memory.h"
#include "chre/util/nanoapp/audio.h"
#include "chre/util/nanoapp/ble.h"
#include "chre/util/nanoapp/callbacks.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/unique_ptr.h"
#include "chre_stress_test.nanopb.h"
#include "send_message.h"

#define LOG_TAG "[ChreStressTest]"

using chre::kOneMicrosecondInNanoseconds;
using chre::kOneMillisecondInNanoseconds;
using chre::ble_constants::kNumScanFilters;

namespace chre {

namespace stress_test {

namespace {

//! Additional duration to handle request timeout over the specified
//! CHRE API timeout (to account for processing delay).
#define TIMEOUT_BUFFER_DELAY_NS (1 * CHRE_NSEC_PER_SEC)

constexpr chre::Nanoseconds kWifiScanInterval = chre::Seconds(5);
constexpr chre::Nanoseconds kSensorRequestInterval = chre::Seconds(5);
constexpr chre::Nanoseconds kAudioRequestInterval = chre::Seconds(5);
constexpr chre::Nanoseconds kBleRequestInterval = chre::Milliseconds(50);
constexpr uint64_t kSensorSamplingDelayNs = 0;
constexpr uint8_t kAccelSensorIndex = 0;
constexpr uint8_t kGyroSensorIndex = 1;
constexpr uint8_t kInstantMotionSensorIndex = 1;

//! Report delay for BLE scans.
constexpr uint32_t kBleBatchDurationMs = 0;

bool isRequestTypeForLocation(uint8_t requestType) {
  return (requestType == CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_START) ||
         (requestType == CHRE_GNSS_REQUEST_TYPE_LOCATION_SESSION_STOP);
}

bool isRequestTypeForMeasurement(uint8_t requestType) {
  return (requestType == CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_START) ||
         (requestType == CHRE_GNSS_REQUEST_TYPE_MEASUREMENT_SESSION_STOP);
}

}  // anonymous namespace

void Manager::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                          const void *eventData) {
  if (eventType == CHRE_EVENT_MESSAGE_FROM_HOST) {
    handleMessageFromHost(
        senderInstanceId,
        static_cast<const chreMessageFromHostData *>(eventData));
  } else if (senderInstanceId == CHRE_INSTANCE_ID) {
    handleDataFromChre(eventType, eventData);
  } else {
    LOGW("Got unknown event type from senderInstanceId %" PRIu32
         " and with eventType %" PRIu16,
         senderInstanceId, eventType);
  }
}

void Manager::handleMessageFromHost(uint32_t senderInstanceId,
                                    const chreMessageFromHostData *hostData) {
  bool success = false;
  uint32_t messageType = hostData->messageType;
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else if (messageType == chre_stress_test_MessageType_TEST_HOST_RESTARTED) {
    // Do nothing and only update the host endpoint
    mHostEndpoint = hostData->hostEndpoint;
    success = true;
  } else if (messageType == chre_stress_test_MessageType_GET_CAPABILITIES) {
    sendCapabilitiesMessage();
    success = true;
  } else if (messageType != chre_stress_test_MessageType_TEST_COMMAND) {
    LOGE("Invalid message type %" PRIu32, messageType);
  } else if (mHostEndpoint.has_value() &&
             hostData->hostEndpoint != mHostEndpoint.value()) {
    LOGE("Invalid host endpoint %" PRIu16 " expected %" PRIu16,
         hostData->hostEndpoint, mHostEndpoint.value());
  } else {
    pb_istream_t istream = pb_istream_from_buffer(
        static_cast<const pb_byte_t *>(hostData->message),
        hostData->messageSize);
    chre_stress_test_TestCommand testCommand =
        chre_stress_test_TestCommand_init_default;

    if (!pb_decode(&istream, chre_stress_test_TestCommand_fields,
                   &testCommand)) {
      LOGE("Failed to decode start command error %s", PB_GET_ERROR(&istream));
    } else {
      LOGI("Got message from host: feature %d start %d", testCommand.feature,
           testCommand.start);

      success = true;
      switch (testCommand.feature) {
        case chre_stress_test_TestCommand_Feature_WIFI_ON_DEMAND_SCAN: {
          handleWifiStartCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_GNSS_LOCATION: {
          handleGnssLocationStartCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_GNSS_MEASUREMENT: {
          handleGnssMeasurementStartCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_WWAN: {
          handleWwanStartCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_WIFI_SCAN_MONITOR: {
          handleWifiScanMonitoringCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_SENSORS: {
          handleSensorStartCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_AUDIO: {
          handleAudioStartCommand(testCommand.start);
          break;
        }
        case chre_stress_test_TestCommand_Feature_BLE: {
          handleBleStartCommand(testCommand.start);
          break;
        }
        default: {
          LOGE("Unknown feature %d", testCommand.feature);
          success = false;
          break;
        }
      }
    }

    mHostEndpoint = hostData->hostEndpoint;
  }

  if (!success) {
    test_shared::sendTestResultWithMsgToHost(
        hostData->hostEndpoint,
        chre_stress_test_MessageType_TEST_RESULT /* messageType */, success,
        nullptr /* errMessage */);
  }
}

void Manager::handleDataFromChre(uint16_t eventType, const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_TIMER:
      handleTimerEvent(static_cast<const uint32_t *>(eventData));
      break;

    case CHRE_EVENT_WIFI_ASYNC_RESULT:
      handleWifiAsyncResult(static_cast<const chreAsyncResult *>(eventData));
      break;

    case CHRE_EVENT_WIFI_SCAN_RESULT:
      handleWifiScanEvent(static_cast<const chreWifiScanEvent *>(eventData));
      break;

    case CHRE_EVENT_GNSS_ASYNC_RESULT:
      handleGnssAsyncResult(static_cast<const chreAsyncResult *>(eventData));
      break;

    case CHRE_EVENT_GNSS_LOCATION:
      handleGnssLocationEvent(
          static_cast<const chreGnssLocationEvent *>(eventData));
      break;

    case CHRE_EVENT_GNSS_DATA:
      handleGnssDataEvent(static_cast<const chreGnssDataEvent *>(eventData));
      break;

    case CHRE_EVENT_WWAN_CELL_INFO_RESULT:
      handleCellInfoResult(
          static_cast<const chreWwanCellInfoResult *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_ACCELEROMETER_DATA:
      handleAccelSensorDataEvent(
          static_cast<const chreSensorThreeAxisData *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_GYROSCOPE_DATA:
      handleGyroSensorDataEvent(
          static_cast<const chreSensorThreeAxisData *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_INSTANT_MOTION_DETECT_DATA:
      handleInstantMotionSensorDataEvent(
          static_cast<const chreSensorOccurrenceData *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_SAMPLING_CHANGE:
      handleSensorSamplingChangeEvent(
          static_cast<const chreSensorSamplingStatusEvent *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_GYROSCOPE_BIAS_INFO:
      LOGI("Received gyro bias info");
      break;

    case CHRE_EVENT_AUDIO_DATA:
      handleAudioDataEvent(static_cast<const chreAudioDataEvent *>(eventData));
      break;

    case CHRE_EVENT_AUDIO_SAMPLING_CHANGE:
      handleAudioSamplingChangeEvent(
          static_cast<const chreAudioSourceStatusEvent *>(eventData));
      break;

    case CHRE_EVENT_BLE_ADVERTISEMENT:
      handleBleAdvertismentEvent(
          static_cast<const chreBleAdvertisementEvent *>(eventData));
      break;

    case CHRE_EVENT_BLE_ASYNC_RESULT:
      handleBleAsyncResult(static_cast<const chreAsyncResult *>(eventData));
      break;

    default:
      LOGW("Unknown event type %" PRIu16, eventType);
      break;
  }
}

void Manager::handleTimerEvent(const uint32_t *handle) {
  if (*handle == mWifiScanTimerHandle) {
    handleDelayedWifiTimer();
  } else if (*handle == mWifiScanAsyncTimerHandle) {
    sendFailure("WiFi scan request timed out");
  } else if (*handle == mGnssLocationTimerHandle) {
    makeGnssLocationRequest();
  } else if (*handle == mGnssMeasurementTimerHandle) {
    makeGnssMeasurementRequest();
  } else if (*handle == mSensorTimerHandle) {
    makeSensorRequests();
  } else if (*handle == mGnssLocationAsyncTimerHandle &&
             mGnssLocationAsyncRequest.has_value()) {
    sendFailure("GNSS location async result timed out");
  } else if (*handle == mGnssMeasurementAsyncTimerHandle &&
             mGnssMeasurementAsyncRequest.has_value()) {
    sendFailure("GNSS measurement async result timed out");
  } else if (*handle == mWwanTimerHandle) {
    makeWwanCellInfoRequest();
  } else if (*handle == mBleScanTimerHandle) {
    makeBleScanRequest();
  } else if (*handle == mWifiScanMonitorAsyncTimerHandle) {
    sendFailure("WiFi scan monitor request timed out");
  } else if (*handle == mAudioTimerHandle) {
    makeAudioRequest();
  } else {
    sendFailure("Unknown timer handle");
  }
}

void Manager::handleDelayedWifiTimer() {
  // NOTE: We set the maxScanAgeMs to something smaller than the WiFi
  // scan periodicity to ensure new scans are generated.
  static const struct chreWifiScanParams params = {
      /*.scanType=*/CHRE_WIFI_SCAN_TYPE_NO_PREFERENCE,
      /*.maxScanAgeMs=*/2000,  // 2 seconds
      /*.frequencyListLen=*/0,
      /*.frequencyList=*/NULL,
      /*.ssidListLen=*/0,
      /*.ssidList=*/NULL,
      /*.radioChainPref=*/CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT,
      /*.channelSet=*/CHRE_WIFI_CHANNEL_SET_NON_DFS};

  bool success = chreWifiRequestScanAsync(&params, &kOnDemandWifiScanCookie);
  LOGI("Requested on demand wifi success ? %d", success);
  if (!success) {
    sendFailure("Failed to make WiFi scan request");
  } else {
    mWifiScanAsyncRequest = AsyncRequest(&kOnDemandWifiScanCookie);
    setTimer(CHRE_WIFI_SCAN_RESULT_TIMEOUT_NS + TIMEOUT_BUFFER_DELAY_NS,
             true /* oneShot */, &mWifiScanAsyncTimerHandle);
  }
}

void Manager::handleWifiAsyncResult(const chreAsyncResult *result) {
  if (result->requestType == CHRE_WIFI_REQUEST_TYPE_REQUEST_SCAN) {
    if (result->success) {
      LOGI("On-demand scan success");
    } else {
      LOGW("On-demand scan failed: code %" PRIu8, result->errorCode);
    }

    if (!mWifiScanAsyncRequest.has_value()) {
      sendFailure("Received WiFi async result with no pending request");
    } else if (result->cookie != mWifiScanAsyncRequest->cookie) {
      sendFailure("On-demand scan cookie mismatch");
    }

    cancelTimer(&mWifiScanAsyncTimerHandle);
    mWifiScanAsyncRequest.reset();
    requestDelayedWifiScan();
  } else if (result->requestType ==
             CHRE_WIFI_REQUEST_TYPE_CONFIGURE_SCAN_MONITOR) {
    if (!result->success) {
      LOGE("Scan monitor async failure: code %" PRIu8, result->errorCode);
      sendFailure("Scan monitor async failed");
    }

    cancelTimer(&mWifiScanMonitorAsyncTimerHandle);
    mWifiScanMonitorEnabled = (result->cookie != nullptr);
  } else {
    sendFailure("Unknown WiFi async result type");
  }
}

void Manager::handleGnssAsyncResult(const chreAsyncResult *result) {
  if (isRequestTypeForLocation(result->requestType)) {
    validateGnssAsyncResult(result, mGnssLocationAsyncRequest,
                            &mGnssLocationAsyncTimerHandle);
  } else if (isRequestTypeForMeasurement(result->requestType)) {
    validateGnssAsyncResult(result, mGnssMeasurementAsyncRequest,
                            &mGnssMeasurementAsyncTimerHandle);
  } else {
    sendFailure("Unknown GNSS async result type");
  }
}

void Manager::handleAudioDataEvent(const chreAudioDataEvent *event) {
  uint64_t timestamp = event->timestamp;

  checkTimestamp(timestamp, mPrevAudioEventTimestampMs);
  mPrevAudioEventTimestampMs = timestamp;
}

void Manager::handleAudioSamplingChangeEvent(
    const chreAudioSourceStatusEvent *event) {
  LOGI("Received audio sampling change event - suspended: %d",
       event->status.suspended);
}

void Manager::validateGnssAsyncResult(const chreAsyncResult *result,
                                      Optional<AsyncRequest> &request,
                                      uint32_t *asyncTimerHandle) {
  if (!request.has_value()) {
    sendFailure("Received GNSS async result with no pending request");
  } else if (!result->success) {
    sendFailure("Async GNSS failure");
  } else if (result->cookie != request->cookie) {
    sendFailure("GNSS async cookie mismatch");
  }

  cancelTimer(asyncTimerHandle);
  request.reset();
}

void Manager::handleBleAdvertismentEvent(
    const chreBleAdvertisementEvent *event) {
  for (uint8_t i = 0; i < event->numReports; i++) {
    uint64_t timestamp =
        event->reports[i].timestamp / chre::kOneMillisecondInNanoseconds;

    checkTimestamp(timestamp, mPrevBleAdTimestampMs);
    mPrevBleAdTimestampMs = timestamp;
  }
}

void Manager::handleBleAsyncResult(const chreAsyncResult *result) {
  const char *requestType =
      result->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN ? "start"
                                                              : "stop";
  if (!mBleScanAsyncRequest.has_value()) {
    sendFailure("Received BLE async result with no pending request");
  } else if (!result->success) {
    LOGE("BLE %s scan failure: %" PRIu8, requestType, result->errorCode);
    sendFailure("Async BLE failure");
  } else if (result->cookie != mBleScanAsyncRequest->cookie) {
    sendFailure("BLE async cookie mismatch");
  } else {
    LOGI("BLE %s scan success", requestType);
  }
  mBleScanAsyncRequest.reset();
}

void Manager::checkTimestamp(uint64_t timestamp, uint64_t pastTimestamp) {
  if (timestamp < pastTimestamp) {
    sendFailure("Timestamp was too old");
  } else if (timestamp == pastTimestamp) {
    sendFailure("Timestamp was duplicate");
  }
}

void Manager::checkTimestampInterval(uint64_t timestamp, uint64_t pastTimestamp,
                                     uint64_t maxInterval) {
  checkTimestamp(timestamp, pastTimestamp);
  if (timestamp - pastTimestamp > maxInterval) {
    LOGE("Timestamp is later than expected");
    LOGI("Current timestamp %" PRIu64, timestamp);
    LOGI("past timestamp %" PRIu64, pastTimestamp);
    LOGI("Timestamp difference %" PRIu64, timestamp - pastTimestamp);
  }
}

void Manager::handleGnssLocationEvent(const chreGnssLocationEvent *event) {
  LOGI("Received GNSS location event at %" PRIu64 " ms", event->timestamp);

  checkTimestamp(event->timestamp, mPrevGnssLocationEventTimestampMs);
  mPrevGnssLocationEventTimestampMs = event->timestamp;
}

void Manager::handleGnssDataEvent(const chreGnssDataEvent *event) {
  static uint32_t sPrevDiscontCount = 0;
  LOGI("Received GNSS measurement event at %" PRIu64 " ns count %" PRIu32
       " flags 0x%" PRIx16,
       event->clock.time_ns, event->clock.hw_clock_discontinuity_count,
       event->clock.flags);

  if (sPrevDiscontCount == event->clock.hw_clock_discontinuity_count) {
    checkTimestamp(event->clock.time_ns, mPrevGnssMeasurementEventTimestampNs);
  }

  sPrevDiscontCount = event->clock.hw_clock_discontinuity_count;
  mPrevGnssMeasurementEventTimestampNs = event->clock.time_ns;
}

void Manager::handleWifiScanEvent(const chreWifiScanEvent *event) {
  LOGI("Received Wifi scan event of type %" PRIu8 " with %" PRIu8
       " results at %" PRIu64 " ns",
       event->scanType, event->resultCount, event->referenceTime);

  if (event->eventIndex == 0) {
    checkTimestamp(event->referenceTime, mPrevWifiScanEventTimestampNs);
    mPrevWifiScanEventTimestampNs = event->referenceTime;
  }

  if (mWifiScanMonitorEnabled) {
    chreSendMessageToHostEndpoint(
        nullptr, 0,
        chre_stress_test_MessageType_TEST_WIFI_SCAN_MONITOR_TRIGGERED,
        mHostEndpoint.value(), nullptr /* freeCallback */);
  }
}

void Manager::handleAccelSensorDataEvent(
    const chreSensorThreeAxisData *eventData) {
  const auto &header = eventData->header;
  uint64_t timestamp = header.baseTimestamp;

  // Note: The stress test sends streaming data request for accel, so only
  // non-batched data are checked for timestamp. The allowed interval between
  // data events is selected 1 ms higher than the sensor sampling interval to
  // account for processing delays.
  if (header.readingCount == 1) {
    if (mPrevAccelEventTimestampNs != 0) {
      checkTimestampInterval(timestamp, mPrevAccelEventTimestampNs,
                             mSensors[kAccelSensorIndex].samplingInterval +
                                 kOneMillisecondInNanoseconds);
    }
    mPrevAccelEventTimestampNs = timestamp;
  }
}

void Manager::handleGyroSensorDataEvent(
    const chreSensorThreeAxisData *eventData) {
  const auto &header = eventData->header;
  uint64_t timestamp = header.baseTimestamp;

  // Note: The stress test sends streaming data request for gyro, so only
  // non-batched data are checked for timestamp. The interval is selected 1ms
  // higher than the sensor sampling interval to account for processing delays.
  if (header.readingCount == 1) {
    if (mPrevGyroEventTimestampNs != 0) {
      checkTimestampInterval(timestamp, mPrevGyroEventTimestampNs,
                             mSensors[kGyroSensorIndex].samplingInterval +
                                 kOneMillisecondInNanoseconds);
    }
    mPrevGyroEventTimestampNs = timestamp;
  }
}

void Manager::handleInstantMotionSensorDataEvent(
    const chreSensorOccurrenceData *eventData) {
  const auto &header = eventData->header;
  uint64_t timestamp = header.baseTimestamp;

  mSensors[kInstantMotionSensorIndex].enabled = false;
  checkTimestamp(timestamp, mPrevInstantMotionEventTimestampNs);
  mPrevInstantMotionEventTimestampNs = timestamp;
}

void Manager::handleSensorSamplingChangeEvent(
    const chreSensorSamplingStatusEvent *eventData) {
  LOGI("Sampling Change: handle %" PRIu32 ", status: interval %" PRIu64
       " latency %" PRIu64 " enabled %d",
       eventData->sensorHandle, eventData->status.interval,
       eventData->status.latency, eventData->status.enabled);
  if (eventData->sensorHandle == mSensors[kAccelSensorIndex].handle &&
      eventData->status.interval !=
          mSensors[kAccelSensorIndex].samplingInterval) {
    mSensors[kAccelSensorIndex].samplingInterval = eventData->status.interval;
  } else if (eventData->sensorHandle == mSensors[kGyroSensorIndex].handle &&
             eventData->status.interval !=
                 mSensors[kGyroSensorIndex].samplingInterval) {
    mSensors[kGyroSensorIndex].samplingInterval = eventData->status.interval;
  }
}

void Manager::handleCellInfoResult(const chreWwanCellInfoResult *event) {
  LOGI("Received %" PRIu8 " cell info results", event->cellInfoCount);

  mWwanCellInfoAsyncRequest.reset();
  if (event->errorCode != CHRE_ERROR_NONE) {
    LOGE("Cell info request failed with error code %" PRIu8, event->errorCode);
    sendFailure("Cell info request failed");
  } else if (event->cellInfoCount > 0) {
    uint64_t maxTimestamp = 0;
    for (uint8_t i = 0; i < event->cellInfoCount; i++) {
      maxTimestamp = MAX(maxTimestamp, event->cells[i].timeStamp);
      checkTimestamp(event->cells[i].timeStamp,
                     mPrevWwanCellInfoEventTimestampNs);
    }

    mPrevWwanCellInfoEventTimestampNs = maxTimestamp;
  }
}

void Manager::handleWifiStartCommand(bool start) {
  mWifiTestStarted = start;
  if (start) {
    requestDelayedWifiScan();
  } else {
    cancelTimer(&mWifiScanTimerHandle);
  }
}

void Manager::handleGnssLocationStartCommand(bool start) {
  constexpr uint64_t kTimerDelayNs = Seconds(60).toRawNanoseconds();

  if (chreGnssGetCapabilities() & CHRE_GNSS_CAPABILITIES_LOCATION) {
    mGnssLocationTestStarted = start;
    makeGnssLocationRequest();

    if (start) {
      setTimer(kTimerDelayNs, false /* oneShot */, &mGnssLocationTimerHandle);
    } else {
      cancelTimer(&mGnssLocationTimerHandle);
    }
  } else {
    LOGW("Platform has no location capability");
  }
}

void Manager::handleGnssMeasurementStartCommand(bool start) {
  constexpr uint64_t kTimerDelayNs = Seconds(60).toRawNanoseconds();

  if (chreGnssGetCapabilities() & CHRE_GNSS_CAPABILITIES_MEASUREMENTS) {
    mGnssMeasurementTestStarted = start;
    makeGnssMeasurementRequest();

    if (start) {
      setTimer(kTimerDelayNs, false /* oneShot */,
               &mGnssMeasurementTimerHandle);
    } else {
      cancelTimer(&mGnssMeasurementTimerHandle);
    }
  } else {
    LOGW("Platform has no GNSS measurement capability");
  }
}

void Manager::handleWwanStartCommand(bool start) {
  constexpr uint64_t kTimerDelayNs =
      CHRE_ASYNC_RESULT_TIMEOUT_NS + TIMEOUT_BUFFER_DELAY_NS;

  if (chreWwanGetCapabilities() & CHRE_WWAN_GET_CELL_INFO) {
    mWwanTestStarted = start;
    makeWwanCellInfoRequest();

    if (start) {
      setTimer(kTimerDelayNs, false /* oneShot */, &mWwanTimerHandle);
    } else {
      cancelTimer(&mWwanTimerHandle);
    }
  } else {
    LOGW("Platform has no WWAN cell info capability");
  }
}

void Manager::handleWifiScanMonitoringCommand(bool start) {
  if (chreWifiGetCapabilities() & CHRE_WIFI_CAPABILITIES_SCAN_MONITORING) {
    const uint32_t kWifiScanMonitorEnabledCookie = 0x1234;
    bool success = chreWifiConfigureScanMonitorAsync(
        start, start ? &kWifiScanMonitorEnabledCookie : nullptr);
    LOGI("Scan monitor enable %d request success ? %d", start, success);

    if (!success) {
      sendFailure("Scan monitor request failed");
    } else {
      setTimer(CHRE_ASYNC_RESULT_TIMEOUT_NS + TIMEOUT_BUFFER_DELAY_NS,
               true /* oneShot */, &mWifiScanMonitorAsyncTimerHandle);
    }
  } else {
    LOGW("Platform has no WiFi scan monitoring capability");
  }
}

void Manager::handleSensorStartCommand(bool start) {
  mSensorTestStarted = start;
  bool sensorsFound = true;

  for (size_t i = 0; i < ARRAY_SIZE(mSensors); i++) {
    SensorState &sensor = mSensors[i];
    bool isInitialized = chreSensorFindDefault(sensor.type, &sensor.handle);
    if (!isInitialized) {
      sensorsFound = false;
    } else {
      chreSensorInfo &info = sensor.info;
      bool infoStatus = chreGetSensorInfo(sensor.handle, &info);
      if (infoStatus) {
        sensor.samplingInterval = info.minInterval;
        LOGI("SensorInfo: %s, Type=%" PRIu8
             " OnChange=%d OneShot=%d Passive=%d "
             "minInterval=%" PRIu64 "nsec",
             info.sensorName, info.sensorType, info.isOnChange, info.isOneShot,
             info.supportsPassiveMode, info.minInterval);
      } else {
        LOGE("chreGetSensorInfo failed");
      }
    }
    LOGI("Sensor %zu initialized: %s with handle %" PRIu32, i,
         isInitialized ? "true" : "false", sensor.handle);
  }

  if (sensorsFound) {
    if (start) {
      makeSensorRequests();
    } else {
      stopSensorRequests();
      cancelTimer(&mSensorTimerHandle);
    }
  } else {
    LOGW("Platform has no sensor capability");
  }
}

void Manager::handleAudioStartCommand(bool start) {
  mAudioTestStarted = start;
  mAudioEnabled = true;

  if (mAudioTestStarted) {
    makeAudioRequest();
  } else {
    cancelTimer(&mAudioTimerHandle);
  }
}

void Manager::handleBleStartCommand(bool start) {
  if (chreBleGetCapabilities() & CHRE_BLE_CAPABILITIES_SCAN) {
    mBleTestStarted = start;

    if (start) {
      makeBleScanRequest();
    } else {
      cancelTimer(&mBleScanTimerHandle);
    }
  } else {
    sendFailure("Platform has no BLE capability");
  }
}

void Manager::setTimer(uint64_t delayNs, bool oneShot, uint32_t *timerHandle) {
  *timerHandle = chreTimerSet(delayNs, timerHandle, oneShot);
  if (*timerHandle == CHRE_TIMER_INVALID) {
    sendFailure("Failed to set timer");
  }
}

void Manager::cancelTimer(uint32_t *timerHandle) {
  if (*timerHandle != CHRE_TIMER_INVALID) {
    if (!chreTimerCancel(*timerHandle)) {
      // We don't treat this as a test failure, because the CHRE API does not
      // guarantee this method succeeds (e.g. if the timer is one-shot and just
      // fired).
      LOGW("Failed to cancel timer");
    }
    *timerHandle = CHRE_TIMER_INVALID;
  }
}

void Manager::makeSensorRequests() {
  bool anySensorConfigured = false;
  for (size_t i = 0; i < ARRAY_SIZE(mSensors); i++) {
    SensorState &sensor = mSensors[i];
    bool status = false;
    if (!sensor.enabled) {
      if (sensor.info.isOneShot) {
        status = chreSensorConfigure(
            sensor.handle, CHRE_SENSOR_CONFIGURE_MODE_ONE_SHOT,
            CHRE_SENSOR_INTERVAL_DEFAULT, kSensorSamplingDelayNs);
      } else {
        status = chreSensorConfigure(
            sensor.handle, CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS,
            sensor.samplingInterval, kSensorSamplingDelayNs);
      }
    } else {
      status = chreSensorConfigureModeOnly(sensor.handle,
                                           CHRE_SENSOR_CONFIGURE_MODE_DONE);
      if (i == kAccelSensorIndex) {
        mPrevAccelEventTimestampNs = 0;
      } else if (i == kGyroSensorIndex) {
        mPrevGyroEventTimestampNs = 0;
      }
    }
    if (status) {
      sensor.enabled = !sensor.enabled;
    }
    LOGI("Configure [enable %d, status %d]: %s", sensor.enabled, status,
         sensor.info.sensorName);
    anySensorConfigured = anySensorConfigured || status;
  }
  if (anySensorConfigured) {
    setTimer(kSensorRequestInterval.toRawNanoseconds(), true /* oneShot */,
             &mSensorTimerHandle);
  } else {
    LOGW("Failed to make sensor request");
  }
}

void Manager::stopSensorRequests() {
  for (size_t i = 0; i < ARRAY_SIZE(mSensors); i++) {
    SensorState &sensor = mSensors[i];
    if (sensor.enabled) {
      if (!chreSensorConfigureModeOnly(sensor.handle,
                                       CHRE_SENSOR_CONFIGURE_MODE_DONE)) {
        LOGE("Failed to disable sensor: %s", sensor.info.sensorName);
      }
    }
  }
}

bool Manager::enableBleScan() {
  chreBleScanMode mode = mBleScanMode;
  switch (mBleScanMode) {
    case CHRE_BLE_SCAN_MODE_BACKGROUND:
      mBleScanMode = CHRE_BLE_SCAN_MODE_FOREGROUND;
      break;
    case CHRE_BLE_SCAN_MODE_FOREGROUND:
      mBleScanMode = CHRE_BLE_SCAN_MODE_AGGRESSIVE;
      break;
    case CHRE_BLE_SCAN_MODE_AGGRESSIVE:
      mBleScanMode = CHRE_BLE_SCAN_MODE_BACKGROUND;
      mShouldEnableBleScan = false;
      break;
    default:
      sendFailure("Invalid scan mode");
      break;
  }
  struct chreBleScanFilterV1_9 filter;
  chreBleGenericFilter uuidFilters[kNumScanFilters];
  createBleScanFilterForKnownBeaconsV1_9(filter, uuidFilters, kNumScanFilters);
  return chreBleStartScanAsyncV1_9(mode, kBleBatchDurationMs, &filter,
                                   &kBleScanCookie);
}

bool Manager::disableBleScan() {
  mShouldEnableBleScan = true;
  return chreBleStopScanAsyncV1_9(&kBleScanCookie);
}

void Manager::makeBleScanRequest() {
  bool success = false;
  if (mShouldEnableBleScan) {
    success = enableBleScan();
  } else {
    success = disableBleScan();
  }

  if (!success) {
    LOGE("Failed to send BLE scan request");
  } else {
    mBleScanAsyncRequest = AsyncRequest(&kBleScanCookie);
    setTimer(kBleRequestInterval.toRawNanoseconds(), true /* oneShot */,
             &mBleScanTimerHandle);
  }
}

void Manager::makeGnssLocationRequest() {
  // The list of location intervals to iterate; wraps around.
  static const uint32_t kMinIntervalMsList[] = {1000, 0};
  static size_t sIntervalIndex = 0;

  uint32_t minIntervalMs = 0;
  if (mGnssLocationTestStarted) {
    minIntervalMs = kMinIntervalMsList[sIntervalIndex];
    sIntervalIndex = (sIntervalIndex + 1) % ARRAY_SIZE(kMinIntervalMsList);
  } else {
    sIntervalIndex = 0;
  }

  bool success = false;
  if (minIntervalMs > 0) {
    success = chreGnssLocationSessionStartAsync(
        minIntervalMs, 0 /* minTimeToNextFixMs */, &kGnssLocationCookie);
  } else {
    success = chreGnssLocationSessionStopAsync(&kGnssLocationCookie);
  }

  LOGI("Configure GNSS location interval %" PRIu32 " ms success ? %d",
       minIntervalMs, success);

  if (!success) {
    sendFailure("Failed to make location request");
  } else {
    mGnssLocationAsyncRequest = AsyncRequest(&kGnssLocationCookie);
    setTimer(CHRE_GNSS_ASYNC_RESULT_TIMEOUT_NS + TIMEOUT_BUFFER_DELAY_NS,
             true /* oneShot */, &mGnssLocationAsyncTimerHandle);
  }
}

void Manager::makeGnssMeasurementRequest() {
  // The list of measurement intervals to iterate; wraps around.
  static const uint32_t kMinIntervalMsList[] = {1000, 0};
  static size_t sIntervalIndex = 0;

  uint32_t minIntervalMs = 0;
  if (mGnssMeasurementTestStarted) {
    minIntervalMs = kMinIntervalMsList[sIntervalIndex];
    sIntervalIndex = (sIntervalIndex + 1) % ARRAY_SIZE(kMinIntervalMsList);
  } else {
    sIntervalIndex = 0;
  }

  bool success = false;
  if (minIntervalMs > 0) {
    success = chreGnssMeasurementSessionStartAsync(minIntervalMs,
                                                   &kGnssMeasurementCookie);
  } else {
    success = chreGnssMeasurementSessionStopAsync(&kGnssMeasurementCookie);
    // Reset the previous timestamp, since the GNSS internal clock may reset.
    mPrevGnssMeasurementEventTimestampNs = 0;
  }

  LOGI("Configure GNSS measurement interval %" PRIu32 " ms success ? %d",
       minIntervalMs, success);

  if (!success) {
    sendFailure("Failed to make measurement request");
  } else {
    mGnssMeasurementAsyncRequest = AsyncRequest(&kGnssMeasurementCookie);
    setTimer(CHRE_GNSS_ASYNC_RESULT_TIMEOUT_NS + TIMEOUT_BUFFER_DELAY_NS,
             true /* oneShot */, &mGnssMeasurementAsyncTimerHandle);
  }
}

void Manager::requestDelayedWifiScan() {
  if (mWifiTestStarted) {
    if (chreWifiGetCapabilities() & CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN) {
      setTimer(kWifiScanInterval.toRawNanoseconds(), true /* oneShot */,
               &mWifiScanTimerHandle);
    } else {
      LOGW("Platform has no on-demand scan capability");
    }
  }
}

void Manager::makeWwanCellInfoRequest() {
  if (mWwanTestStarted) {
    if (mWwanCellInfoAsyncRequest.has_value()) {
      if (chreGetTime() > mWwanCellInfoAsyncRequest->requestTimeNs +
                              CHRE_ASYNC_RESULT_TIMEOUT_NS) {
        sendFailure("Prev cell info request did not complete in time");
      }
    } else {
      bool success = chreWwanGetCellInfoAsync(&kWwanCellInfoCookie);

      LOGI("Cell info request success ? %d", success);

      if (!success) {
        sendFailure("Failed to make cell info request");
      } else {
        mWwanCellInfoAsyncRequest = AsyncRequest(&kWwanCellInfoCookie);
      }
    }
  }
}

void Manager::makeAudioRequest() {
  bool success = false;
  struct chreAudioSource source;
  if (mAudioEnabled) {
    for (uint32_t i = 0; chreAudioGetSource(i, &source); i++) {
      if (chreAudioConfigureSource(i, true, source.minBufferDuration,
                                   source.minBufferDuration)) {
        LOGI("Successfully enabled audio for source %" PRIu32, i);
        success = true;
      } else {
        LOGE("Failed to enable audio");
      }
    }
  } else {
    for (uint32_t i = 0; chreAudioGetSource(i, &source); i++) {
      if (chreAudioConfigureSource(i, false, 0, 0)) {
        LOGI("Successfully disabled audio for source %" PRIu32, i);
        success = true;
      } else {
        LOGE("Failed to disable audio");
      }
    }
  }

  if (success) {
    mAudioEnabled = !mAudioEnabled;
    setTimer(kAudioRequestInterval.toRawNanoseconds(), true /* oneShot */,
             &mAudioTimerHandle);
  } else {
    sendFailure("Failed to make audio request");
  }
}

void Manager::sendFailure(const char *errorMessage) {
  test_shared::sendTestResultWithMsgToHost(
      mHostEndpoint.value(),
      chre_stress_test_MessageType_TEST_RESULT /* messageType */,
      false /* success */, errorMessage, false /* abortOnFailure */);
}

void Manager::sendCapabilitiesMessage() {
  if (!mHostEndpoint.has_value()) {
    LOGE("mHostEndpoint is not initialized");
    return;
  }

  chre_stress_test_Capabilities capabilities =
      chre_stress_test_Capabilities_init_default;
  capabilities.wifi = chreWifiGetCapabilities();

  size_t size;
  if (!pb_get_encoded_size(&size, chre_stress_test_Capabilities_fields,
                           &capabilities)) {
    LOGE("Failed to get message size");
    return;
  }

  pb_byte_t *bytes = static_cast<pb_byte_t *>(chreHeapAlloc(size));
  if (size > 0 && bytes == nullptr) {
    LOG_OOM();
  } else {
    pb_ostream_t stream = pb_ostream_from_buffer(bytes, size);
    if (!pb_encode(&stream, chre_stress_test_Capabilities_fields,
                   &capabilities)) {
      LOGE("Failed to encode capabilities error %s", PB_GET_ERROR(&stream));
      chreHeapFree(bytes);
    } else {
      chreSendMessageToHostEndpoint(
          bytes, size, chre_stress_test_MessageType_CAPABILITIES,
          mHostEndpoint.value(), heapFreeMessageCallback);
    }
  }
}

}  // namespace stress_test

}  // namespace chre
