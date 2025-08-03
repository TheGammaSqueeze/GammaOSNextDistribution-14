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

#ifndef CHRE_STRESS_TEST_MANAGER_H_
#define CHRE_STRESS_TEST_MANAGER_H_

#include "chre_stress_test.nanopb.h"

#include <cinttypes>

#include "chre/util/optional.h"
#include "chre/util/singleton.h"
#include "chre/util/time.h"
#include "chre_api/chre.h"

namespace chre {

namespace stress_test {

//! Lists types of BLE scan request.
enum BleScanRequestType {
  NO_FILTER = 0,
  SERVICE_DATA_16 = 1,
  STOP_SCAN = 2,
};

/**
 * A class to manage a CHRE stress test session.
 */
class Manager {
 public:
  /**
   * Handles an event from CHRE. Semantics are the same as nanoappHandleEvent.
   */
  void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                   const void *eventData);

 private:
  struct AsyncRequest {
    AsyncRequest(const void *cookie_) {
      cookie = cookie_;
    }

    uint64_t requestTimeNs = chreGetTime();
    const void *cookie;
  };

  struct SensorState {
    //! Corresponds to types defined in chre_api/sensor_types.h.
    const uint8_t type;

    //! The sampling interval for the next sensor request.
    uint64_t samplingInterval;

    //! The sensor handle obtained from chreSensorFindDefault().
    uint32_t handle;

    //! Indicate if the sensor is already configured.
    bool enabled;

    //! Information about this sensor.
    chreSensorInfo info;
  };

  /**
   * Handles a message from the host.
   *
   * @param senderInstanceId The sender instance ID of this message.
   * @param hostData The data from the host.
   */
  void handleMessageFromHost(uint32_t senderInstanceId,
                             const chreMessageFromHostData *hostData);
  /**
   * Processes data from CHRE.
   *
   * @param eventType The event type as defined by CHRE.
   * @param eventData A pointer to the data.
   */
  void handleDataFromChre(uint16_t eventType, const void *eventData);

  /**
   * @param handle A pointer to the timer handle.
   */
  void handleTimerEvent(const uint32_t *handle);

  /**
   * Validates a timestamp of an event where the timestamp is expected
   * to be monotonically increasing.
   *
   * @param timestamp The timestamp.
   * @param pastTimestamp The previous timestamp.
   */
  void checkTimestamp(uint64_t timestamp, uint64_t pastTimestamp);

  /**
   * Validates the difference between timestamps is below a certain interval
   *
   * @param timestamp The timestamp.
   * @param pastTimestamp The previous timestamp.
   * @param maxInterval The max interval allowed between the two timestamps.
   */
  void checkTimestampInterval(uint64_t timestamp, uint64_t pastTimestamp,
                              uint64_t maxInterval);

  /**
   * Handles a start command from the host.
   *
   * @param start true to start the test, stop otherwise.
   */
  void handleWifiStartCommand(bool start);
  void handleGnssLocationStartCommand(bool start);
  void handleGnssMeasurementStartCommand(bool start);
  void handleWwanStartCommand(bool start);
  void handleWifiScanMonitoringCommand(bool start);
  void handleSensorStartCommand(bool start);
  void handleAudioStartCommand(bool start);
  void handleBleStartCommand(bool start);

  /**
   * @param result The WiFi async result from CHRE.
   */
  void handleWifiAsyncResult(const chreAsyncResult *result);

  /**
   * @param result The WiFi scan event from CHRE.
   */
  void handleWifiScanEvent(const chreWifiScanEvent *event);

  /**
   * Sets up a WiFi scan request after some time.
   */
  void requestDelayedWifiScan();
  void handleDelayedWifiTimer();

  /**
   * Sends the failure to the host.
   *
   * @param errorMessage The error message string.
   */
  void sendFailure(const char *errorMessage);

  /**
   * Sets/cancels a timer and asserts success.
   *
   * @param delayNs The delay of the timer in nanoseconds.
   * @param oneShot true if the timer request is one-shot.
   * @param timerHandle A non-null pointer to where the timer handle is stored.
   */
  void setTimer(uint64_t delayNs, bool oneShot, uint32_t *timerHandle);
  void cancelTimer(uint32_t *timerHandle);

  /**
   * Makes the next GNSS request.
   */
  void makeGnssLocationRequest();
  void makeGnssMeasurementRequest();

  /**
   * @param result The GNSS async result from CHRE.
   */
  void handleGnssAsyncResult(const chreAsyncResult *result);

  /**
   * @param result The result to validate.
   * @param request The async request associated with this result.
   * @param asyncTimerHandle The async timer handle for this request.
   */
  void validateGnssAsyncResult(const chreAsyncResult *result,
                               Optional<AsyncRequest> &request,
                               uint32_t *asyncTimerHandle);

  /**
   * @param event The GNSS event from CHRE.
   */
  void handleGnssLocationEvent(const chreGnssLocationEvent *event);
  void handleGnssDataEvent(const chreGnssDataEvent *event);

  /**
   * Makes the next cell info request.
   */
  void makeWwanCellInfoRequest();

  /**
   * Send the capabilities to the host.
   */
  void sendCapabilitiesMessage();

  /**
   * @param event The cell info event from CHRE.
   */
  void handleCellInfoResult(const chreWwanCellInfoResult *event);

  /**
   * @param eventData The sensor data from CHRE.
   */
  void handleAccelSensorDataEvent(const chreSensorThreeAxisData *eventData);
  void handleGyroSensorDataEvent(const chreSensorThreeAxisData *eventData);
  void handleInstantMotionSensorDataEvent(
      const chreSensorOccurrenceData *eventData);
  void handleSensorSamplingChangeEvent(
      const chreSensorSamplingStatusEvent *eventData);

  /**
   * Makes the next sensor request.
   */
  void makeSensorRequests();

  /**
   * Send a disable request to all sensors.
   */
  void stopSensorRequests();

  /**
   * @param event The audio event from CHRE.
   */
  void handleAudioDataEvent(const chreAudioDataEvent *event);
  void handleAudioSamplingChangeEvent(const chreAudioSourceStatusEvent *event);

  /**
   * Makes the next audio request.
   */
  void makeAudioRequest();

  /**
   * @param event The BLE advertisement event from CHRE.
   */
  void handleBleAdvertismentEvent(const chreBleAdvertisementEvent *event);

  /**
   * @param event The BLE event from CHRE.
   */
  void handleBleAsyncResult(const chreAsyncResult *result);

  /**
   * Makes the next Ble request.
   */
  void makeBleScanRequest();

  /**
   * Enables a BLE scan.
   *
   * @return true if scan was enabled successfully.
   */
  bool enableBleScan();

  /**
   * Disables a BLE scan.
   *
   * @return true if scan was disabled successfully.
   */
  bool disableBleScan();

  /**
   * @param scanRequestType The current BLE scan request type.
   *
   * @return The pointer to a chreBleScanFilter that corresponds to the scan
   * request type.
   */
  chreBleScanFilter *getBleScanFilter(BleScanRequestType &scanRequestType);

  //! The host endpoint of the current test host.
  Optional<uint16_t> mHostEndpoint;

  //! The timer handle for performing requests.
  uint32_t mWifiScanTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mWifiScanAsyncTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mGnssLocationTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mGnssLocationAsyncTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mGnssMeasurementTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mGnssMeasurementAsyncTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mWwanTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mWifiScanMonitorAsyncTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mSensorTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mAudioTimerHandle = CHRE_TIMER_INVALID;
  uint32_t mBleScanTimerHandle = CHRE_TIMER_INVALID;

  //! true if the test has been started for the feature.
  bool mWifiTestStarted = false;
  bool mGnssLocationTestStarted = false;
  bool mGnssMeasurementTestStarted = false;
  bool mWwanTestStarted = false;
  bool mSensorTestStarted = false;
  bool mAudioTestStarted = false;
  bool mBleTestStarted = false;

  //! true if scan monitor is enabled for the nanoapp.
  bool mWifiScanMonitorEnabled = false;

  //! True if audio is enabled for the nanoapp.
  bool mAudioEnabled = false;

  //! The cookie to use for requests.
  const uint32_t kOnDemandWifiScanCookie = 0xface;
  const uint32_t kGnssLocationCookie = 0xbeef;
  const uint32_t kGnssMeasurementCookie = 0xbead;
  const uint32_t kWwanCellInfoCookie = 0x1337;
  const uint32_t kBleScanCookie = 0x1234;

  //! The pending requests.
  Optional<AsyncRequest> mWifiScanAsyncRequest;
  Optional<AsyncRequest> mGnssLocationAsyncRequest;
  Optional<AsyncRequest> mGnssMeasurementAsyncRequest;
  Optional<AsyncRequest> mWwanCellInfoAsyncRequest;
  Optional<AsyncRequest> mBleScanAsyncRequest;

  //! The previous timestamp of events.
  uint64_t mPrevGnssLocationEventTimestampMs = 0;
  uint64_t mPrevGnssMeasurementEventTimestampNs = 0;
  uint64_t mPrevWifiScanEventTimestampNs = 0;
  uint64_t mPrevWwanCellInfoEventTimestampNs = 0;
  uint64_t mPrevAccelEventTimestampNs = 0;
  uint64_t mPrevGyroEventTimestampNs = 0;
  uint64_t mPrevInstantMotionEventTimestampNs = 0;
  uint64_t mPrevAudioEventTimestampMs = 0;
  uint64_t mPrevBleAdTimestampMs = 0;

  //! Current number of sensors tested.
  static constexpr uint32_t kNumSensors = 3;

  //! List of sensors.
  SensorState mSensors[kNumSensors] = {
      {
          .type = CHRE_SENSOR_TYPE_ACCELEROMETER,
          .samplingInterval = CHRE_SENSOR_INTERVAL_DEFAULT,
          .handle = 0,
          .enabled = true,
          .info = {},
      },
      {
          .type = CHRE_SENSOR_TYPE_GYROSCOPE,
          .samplingInterval = CHRE_SENSOR_INTERVAL_DEFAULT,
          .handle = 0,
          .enabled = true,
          .info = {},
      },
      {
          .type = CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT,
          .samplingInterval = CHRE_SENSOR_INTERVAL_DEFAULT,
          .handle = 0,
          .enabled = true,
          .info = {},
      }};

  //! Controls BLE scan testing stage
  bool mShouldEnableBleScan = true;
  chreBleScanMode mBleScanMode = CHRE_BLE_SCAN_MODE_BACKGROUND;
};

// The stress test manager singleton.
typedef chre::Singleton<Manager> ManagerSingleton;

}  // namespace stress_test

}  // namespace chre

#endif  // CHRE_STRESS_TEST_MANAGER_H_
