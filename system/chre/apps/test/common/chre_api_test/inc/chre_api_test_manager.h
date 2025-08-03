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

#ifndef CHRE_API_TEST_MANAGER_H_
#define CHRE_API_TEST_MANAGER_H_

#include <cinttypes>
#include <cstdint>

#include "chre/re.h"
#include "chre/util/pigweed/rpc_server.h"
#include "chre/util/singleton.h"
#include "chre_api/chre.h"
#include "chre_api_test.rpc.pb.h"

using ::chre::Optional;

/**
 * Contains signature-generated RPC functions for the ChreApiTest service.
 */
class ChreApiTestService final
    : public chre::rpc::pw_rpc::nanopb::ChreApiTestService::Service<
          ChreApiTestService> {
 public:
  /**
   * Returns the BLE capabilities.
   */
  pw::Status ChreBleGetCapabilities(const google_protobuf_Empty &request,
                                    chre_rpc_Capabilities &response);

  /**
   * Returns the BLE filter capabilities.
   */
  pw::Status ChreBleGetFilterCapabilities(const google_protobuf_Empty &request,
                                          chre_rpc_Capabilities &response);

  /**
   * Finds the default sensor and returns the handle in the output.
   */
  pw::Status ChreSensorFindDefault(
      const chre_rpc_ChreSensorFindDefaultInput &request,
      chre_rpc_ChreSensorFindDefaultOutput &response);

  /**
   * Gets the sensor information.
   */
  pw::Status ChreGetSensorInfo(const chre_rpc_ChreHandleInput &request,
                               chre_rpc_ChreGetSensorInfoOutput &response);

  /**
   * Gets the sensor sampling status for a given sensor.
   */
  pw::Status ChreGetSensorSamplingStatus(
      const chre_rpc_ChreHandleInput &request,
      chre_rpc_ChreGetSensorSamplingStatusOutput &response);

  /**
   * Configures a given sensor.
   */
  pw::Status ChreSensorConfigure(
      const chre_rpc_ChreSensorConfigureInput &request,
      chre_rpc_Status &response);

  /**
   * Configures the mode for a sensor.
   */
  pw::Status ChreSensorConfigureModeOnly(
      const chre_rpc_ChreSensorConfigureModeOnlyInput &request,
      chre_rpc_Status &response);

  /**
   * Gets the audio source information.
   */
  pw::Status ChreAudioGetSource(const chre_rpc_ChreHandleInput &request,
                                chre_rpc_ChreAudioGetSourceOutput &response);

  /**
   * Configures delivery of audio data to the current nanoapp.
   */
  pw::Status ChreAudioConfigureSource(
      const chre_rpc_ChreAudioConfigureSourceInput &request,
      chre_rpc_Status &response);

  /**
   * Gets the current chreAudioSourceStatus struct for a given audio handle.
   */
  pw::Status ChreAudioGetStatus(const chre_rpc_ChreHandleInput &request,
                                chre_rpc_ChreAudioGetStatusOutput &response);

  /**
   * Configures host endpoint notification.
   */
  pw::Status ChreConfigureHostEndpointNotifications(
      const chre_rpc_ChreConfigureHostEndpointNotificationsInput &request,
      chre_rpc_Status &response);

  /**
   * Gets the host endpoint info for a given host endpoint id.
   */
  pw::Status ChreGetHostEndpointInfo(
      const chre_rpc_ChreGetHostEndpointInfoInput &request,
      chre_rpc_ChreGetHostEndpointInfoOutput &response);

  /**
   * Starts a BLE scan synchronously. Waits for the CHRE_EVENT_BLE_ASYNC_RESULT
   * event.
   */
  void ChreBleStartScanSync(const chre_rpc_ChreBleStartScanAsyncInput &request,
                            ServerWriter<chre_rpc_GeneralSyncMessage> &writer);

  /**
   * Stops a BLE scan synchronously. Waits for the CHRE_EVENT_BLE_ASYNC_RESULT
   * event.
   */
  void ChreBleStopScanSync(const google_protobuf_Empty &request,
                           ServerWriter<chre_rpc_GeneralSyncMessage> &writer);

  /**
   * Gathers events that match the input filter before the timeout in ns or
   * the max event count.
   */
  void GatherEvents(const chre_rpc_GatherEventsInput &request,
                    ServerWriter<chre_rpc_GeneralEventsMessage> &writer);

  /**
   * Handles a BLE event from CHRE.
   *
   * @param result              the event result.
   */
  void handleBleAsyncResult(const chreAsyncResult *result);

  /**
   * Gathers the event if there is an existing event writer.
   *
   * @param eventType           the event type.
   * @param eventData           the event data.
   */
  void handleGatheringEvent(uint16_t eventType, const void *eventData);

  /**
   * Handles a timer event from CHRE.
   *
   * @param cookie              the cookie from the event.
   */
  void handleTimerEvent(const void *cookie);

  /**
   * Handles host endpoint notification event from CHRE.
   *
   * @param data                the data from event.
   */
  void handleHostEndpointNotificationEvent(
      const chreHostEndpointNotification *data);

 private:
  /**
   * Sets the synchronous timeout timer for the active sync message.
   *
   * @return                     if the operation was successful.
   */
  bool startSyncTimer();

  /**
   * The following functions validate the RPC input: request, calls the
   * underlying function, and sets the return value in response.
   *
   * @param request              the request.
   * @param response             the response.
   * @return                     true if the input was validated correctly;
   *                             false otherwise.
   */
  bool validateInputAndCallChreBleGetCapabilities(
      const google_protobuf_Empty &request, chre_rpc_Capabilities &response);

  bool validateInputAndCallChreBleGetFilterCapabilities(
      const google_protobuf_Empty &request, chre_rpc_Capabilities &response);

  bool validateInputAndCallChreBleStartScanAsync(
      const chre_rpc_ChreBleStartScanAsyncInput &request,
      chre_rpc_Status &response);

  bool validateInputAndCallChreBleStopScanAsync(
      const google_protobuf_Empty &request, chre_rpc_Status &response);

  bool validateInputAndCallChreSensorFindDefault(
      const chre_rpc_ChreSensorFindDefaultInput &request,
      chre_rpc_ChreSensorFindDefaultOutput &response);

  bool validateInputAndCallChreGetSensorInfo(
      const chre_rpc_ChreHandleInput &request,
      chre_rpc_ChreGetSensorInfoOutput &response);

  bool validateInputAndCallChreGetSensorSamplingStatus(
      const chre_rpc_ChreHandleInput &request,
      chre_rpc_ChreGetSensorSamplingStatusOutput &response);

  bool validateInputAndCallChreSensorConfigure(
      const chre_rpc_ChreSensorConfigureInput &request,
      chre_rpc_Status &response);

  bool validateInputAndCallChreSensorConfigureModeOnly(
      const chre_rpc_ChreSensorConfigureModeOnlyInput &request,
      chre_rpc_Status &response);

  bool validateInputAndCallChreAudioGetSource(
      const chre_rpc_ChreHandleInput &request,
      chre_rpc_ChreAudioGetSourceOutput &response);

  bool validateInputAndCallChreAudioConfigureSource(
      const chre_rpc_ChreAudioConfigureSourceInput &request,
      chre_rpc_Status &response);

  bool validateInputAndCallChreAudioGetStatus(
      const chre_rpc_ChreHandleInput &request,
      chre_rpc_ChreAudioGetStatusOutput &response);

  bool validateInputAndCallChreConfigureHostEndpointNotifications(
      const chre_rpc_ChreConfigureHostEndpointNotificationsInput &request,
      chre_rpc_Status &response);

  bool validateInputAndCallChreGetHostEndpointInfo(
      const chre_rpc_ChreGetHostEndpointInfoInput &request,
      chre_rpc_ChreGetHostEndpointInfoOutput &response);

  /**
   * Handle assigning the data to the GeneralEventsMessage proto received from
   * a CHRE_AUDIO_DATA_EVENT event.
   *
   * @param message     The message proto to fill in.
   * @param data        The data received in the event.
   */
  bool handleChreAudioDataEvent(const chreAudioDataEvent *data);

  /**
   * Handle sending a single message to host. Asserts success on event write.
   *
   * @param message     The message proto to send.
   * @return            False if we have written the number of expected events.
   */
  bool sendGeneralEventToHost(const chre_rpc_GeneralEventsMessage &message);

  /**
   * Handle sending part of a single message to host.
   * Used for sending events larger than CHRE_MESSAGE_TO_HOST_MAX_SIZE.
   * Asserts success on event write.
   *
   * @param message     The message proto to send.
   */
  void sendPartialGeneralEventToHost(
      const chre_rpc_GeneralEventsMessage &message);

  /**
   * Handles checking if we need to finish sending events.
   * Must be used after calls to sendPartialGeneralEventToHost.
   *
   * @param message     The message proto to send.
   * @return            False if we have written the number of expected events.
   */
  bool closePartialGeneralEventToHost();

  /**
   * Validates the BLE scan filters and creates a generic filter in the
   * outputScanFilters array. scanFilters and outputScanFilters must be of size
   * scanFilterCount or greater.
   *
   * @param scanFilters          the input scan filters.
   * @param outputScanFilters    the output scan filters.
   * @param scanFilterCount      the number of scan filters.
   * @return                     whether the validation was successful.
   */
  bool validateBleScanFilters(const chre_rpc_ChreBleGenericFilter *scanFilters,
                              chreBleGenericFilter *outputScanFilters,
                              uint32_t scanFilterCount);

  constexpr static uint32_t kMaxNumEventTypes =
      10;  // declared in chre_api_test.options

  /**
   * Variables to control synchronization for sync API calls.
   * Only one sync API call may be made at a time.
   */
  Optional<ServerWriter<chre_rpc_GeneralSyncMessage>> mWriter;
  uint32_t mSyncTimerHandle = CHRE_TIMER_INVALID;
  uint8_t mRequestType;

  /*
   * Variables to control synchronization for sync events calls.
   * Only one sync event call may be made at a time.
   */
  Optional<ServerWriter<chre_rpc_GeneralEventsMessage>> mEventWriter;
  uint32_t mEventTimerHandle = CHRE_TIMER_INVALID;
  uint16_t mEventTypes[kMaxNumEventTypes];
  uint32_t mEventTypeCount;
  uint32_t mEventExpectedCount;
  uint32_t mEventSentCount;
};

/**
 * Handles RPC requests for the CHRE API Test nanoapp.
 */
class ChreApiTestManager {
 public:
  /**
   * Allows the manager to do any init necessary as part of nanoappStart.
   */
  bool start();

  /**
   * Allows the manager to do any cleanup necessary as part of nanoappEnd.
   */
  void end();

  /**
   * Handle a CHRE event.
   *
   * @param senderInstanceId    the instand ID that sent the event.
   * @param eventType           the type of the event.
   * @param eventData           the data for the event.
   */
  void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                   const void *eventData);

  /**
   * Sets the permission for the next server message.
   *
   * @params permission Bitmasked CHRE_MESSAGE_PERMISSION_.
   */
  void setPermissionForNextMessage(uint32_t permission);

 private:
  // RPC server.
  chre::RpcServer mServer;

  // pw_rpc service used to process the RPCs.
  ChreApiTestService mChreApiTestService;
};

typedef chre::Singleton<ChreApiTestManager> ChreApiTestManagerSingleton;

#endif  // CHRE_API_TEST_MANAGER_H_
