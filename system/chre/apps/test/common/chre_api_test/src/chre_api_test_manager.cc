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

#include <algorithm>
#include <cstdint>

#include "chre_api_test_manager.h"

#include "chre.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"

namespace {
constexpr uint64_t kSyncFunctionTimeout = 2 * chre::kOneSecondInNanoseconds;

/**
 * The following constants are defined in chre_api_test.options.
 */
constexpr uint32_t kThreeAxisDataReadingsMaxCount = 10;
constexpr uint32_t kChreBleAdvertisementReportMaxCount = 10;
constexpr uint32_t kChreAudioDataEventMaxSampleBufferSize = 200;

/**
 * Closes the writer and invalidates the writer.
 *
 * @param T                   the RPC message type.
 * @param writer              the RPC ServerWriter.
 */
template <typename T>
void finishAndCloseWriter(
    Optional<ChreApiTestService::ServerWriter<T>> &writer) {
  CHRE_ASSERT(writer.has_value());

  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  writer->Finish();
  writer.reset();
}

/**
 * Writes a message to the writer, then closes the writer and invalidates the
 * writer.
 *
 * @param T                   the RPC message type.
 * @param writer              the RPC ServerWriter.
 * @param message             the message to write.
 */
template <typename T>
void sendFinishAndCloseWriter(
    Optional<ChreApiTestService::ServerWriter<T>> &writer, const T &message) {
  CHRE_ASSERT(writer.has_value());

  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  pw::Status status = writer->Write(message);
  CHRE_ASSERT(status.ok());
  finishAndCloseWriter(writer);
}

/**
 * Sends a failure message. If there is not a valid writer, this returns
 * without doing anything.
 *
 * @param T                   the RPC message type.
 * @param writer              the RPC ServerWriter.
 */
template <typename T>
void sendFailureAndFinishCloseWriter(
    Optional<ChreApiTestService::ServerWriter<T>> &writer) {
  CHRE_ASSERT(writer.has_value());

  T message;
  message.status = false;
  sendFinishAndCloseWriter(writer, message);
}
}  // namespace

// Start ChreApiTestService RPC generated functions

pw::Status ChreApiTestService::ChreBleGetCapabilities(
    const google_protobuf_Empty &request, chre_rpc_Capabilities &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreBleGetCapabilities(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreBleGetFilterCapabilities(
    const google_protobuf_Empty &request, chre_rpc_Capabilities &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreBleGetFilterCapabilities(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreSensorFindDefault(
    const chre_rpc_ChreSensorFindDefaultInput &request,
    chre_rpc_ChreSensorFindDefaultOutput &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreSensorFindDefault(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreGetSensorInfo(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreGetSensorInfoOutput &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreGetSensorInfo(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreGetSensorSamplingStatus(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreGetSensorSamplingStatusOutput &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreGetSensorSamplingStatus(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreSensorConfigure(
    const chre_rpc_ChreSensorConfigureInput &request,
    chre_rpc_Status &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreSensorConfigure(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreSensorConfigureModeOnly(
    const chre_rpc_ChreSensorConfigureModeOnlyInput &request,
    chre_rpc_Status &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreSensorConfigureModeOnly(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreAudioGetSource(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreAudioGetSourceOutput &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreAudioGetSource(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreAudioConfigureSource(
    const chre_rpc_ChreAudioConfigureSourceInput &request,
    chre_rpc_Status &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreAudioConfigureSource(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreAudioGetStatus(
    const chre_rpc_ChreHandleInput &request,
    chre_rpc_ChreAudioGetStatusOutput &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreAudioGetStatus(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreConfigureHostEndpointNotifications(
    const chre_rpc_ChreConfigureHostEndpointNotificationsInput &request,
    chre_rpc_Status &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreConfigureHostEndpointNotifications(request,
                                                                    response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

pw::Status ChreApiTestService::ChreGetHostEndpointInfo(
    const chre_rpc_ChreGetHostEndpointInfoInput &request,
    chre_rpc_ChreGetHostEndpointInfoOutput &response) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  return validateInputAndCallChreGetHostEndpointInfo(request, response)
             ? pw::OkStatus()
             : pw::Status::InvalidArgument();
}

// End ChreApiTestService RPC generated functions

// Start ChreApiTestService RPC sync functions

void ChreApiTestService::ChreBleStartScanSync(
    const chre_rpc_ChreBleStartScanAsyncInput &request,
    ServerWriter<chre_rpc_GeneralSyncMessage> &writer) {
  if (mWriter.has_value()) {
    ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
        CHRE_MESSAGE_PERMISSION_NONE);
    writer.Finish();
    LOGE("ChreBleStartScanSync: a sync message already exists");
    return;
  }

  mWriter = std::move(writer);
  CHRE_ASSERT(mSyncTimerHandle == CHRE_TIMER_INVALID);
  mRequestType = CHRE_BLE_REQUEST_TYPE_START_SCAN;

  chre_rpc_Status status;
  if (!validateInputAndCallChreBleStartScanAsync(request, status) ||
      !status.status || !startSyncTimer()) {
    sendFailureAndFinishCloseWriter(mWriter);
    mSyncTimerHandle = CHRE_TIMER_INVALID;
    LOGD("ChreBleStartScanSync: status: false (error)");
  }
}

void ChreApiTestService::ChreBleStopScanSync(
    const google_protobuf_Empty &request,
    ServerWriter<chre_rpc_GeneralSyncMessage> &writer) {
  if (mWriter.has_value()) {
    ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
        CHRE_MESSAGE_PERMISSION_NONE);
    writer.Finish();
    LOGE("ChreBleStopScanSync: a sync message already exists");
    return;
  }

  mWriter = std::move(writer);
  CHRE_ASSERT(mSyncTimerHandle == CHRE_TIMER_INVALID);
  mRequestType = CHRE_BLE_REQUEST_TYPE_STOP_SCAN;

  chre_rpc_Status status;
  if (!validateInputAndCallChreBleStopScanAsync(request, status) ||
      !status.status || !startSyncTimer()) {
    sendFailureAndFinishCloseWriter(mWriter);
    mSyncTimerHandle = CHRE_TIMER_INVALID;
    LOGD("ChreBleStopScanSync: status: false (error)");
  }
}

// End ChreApiTestService RPC sync functions

// Start ChreApiTestService event functions

void ChreApiTestService::GatherEvents(
    const chre_rpc_GatherEventsInput &request,
    ServerWriter<chre_rpc_GeneralEventsMessage> &writer) {
  if (mEventWriter.has_value()) {
    LOGE("GatherEvents: an event gathering call already exists");
    ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
        CHRE_MESSAGE_PERMISSION_NONE);
    writer.Finish();
    return;
  }

  if (request.eventTypes_count == 0) {
    LOGE("GatherEvents: request.eventTypes_count == 0");
    ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
        CHRE_MESSAGE_PERMISSION_NONE);
    writer.Finish();
    return;
  }

  for (uint32_t i = 0; i < request.eventTypes_count; ++i) {
    if (request.eventTypes[i] < std::numeric_limits<uint16_t>::min() ||
        request.eventTypes[i] > std::numeric_limits<uint16_t>::max()) {
      LOGE("GatherEvents: invalid request.eventTypes at index: %" PRIu32, i);
      ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
          CHRE_MESSAGE_PERMISSION_NONE);
      writer.Finish();
      return;
    }

    mEventTypes[i] = static_cast<uint16_t>(request.eventTypes[i]);
    LOGD("GatherEvents: Watching for events with type: %" PRIu16,
         mEventTypes[i]);
  }

  mEventWriter = std::move(writer);
  CHRE_ASSERT(mEventTimerHandle == CHRE_TIMER_INVALID);
  mEventTimerHandle = chreTimerSet(
      request.timeoutInNs, &mEventTimerHandle /* cookie */, true /* oneShot */);
  if (mEventTimerHandle == CHRE_TIMER_INVALID) {
    LOGE("GatherEvents: Cannot set the event timer");
    sendFailureAndFinishCloseWriter(mEventWriter);
    mEventTimerHandle = CHRE_TIMER_INVALID;
  } else {
    mEventTypeCount = request.eventTypes_count;
    mEventExpectedCount = request.eventCount;
    mEventSentCount = 0;
    LOGD("GatherEvents: mEventTypeCount: %" PRIu32
         " mEventExpectedCount: %" PRIu32,
         mEventTypeCount, mEventExpectedCount);
  }
}

// End ChreApiTestService event functions

void ChreApiTestService::handleBleAsyncResult(const chreAsyncResult *result) {
  if (result == nullptr || !mWriter.has_value()) {
    return;
  }

  if (result->requestType == mRequestType) {
    chreTimerCancel(mSyncTimerHandle);
    mSyncTimerHandle = CHRE_TIMER_INVALID;

    chre_rpc_GeneralSyncMessage generalSyncMessage;
    generalSyncMessage.status = result->success;
    sendFinishAndCloseWriter(mWriter, generalSyncMessage);
    LOGD("Active BLE sync function: status: %s",
         generalSyncMessage.status ? "true" : "false");
  }
}

bool ChreApiTestService::handleChreAudioDataEvent(
    const chreAudioDataEvent *data) {
  // send the metadata
  chre_rpc_GeneralEventsMessage metadataMessage;
  metadataMessage.data.chreAudioDataMetadata.version = data->version;
  metadataMessage.data.chreAudioDataMetadata.reserved =
      0;  // Must be set to 0 always
  metadataMessage.data.chreAudioDataMetadata.handle = data->handle;
  metadataMessage.data.chreAudioDataMetadata.timestamp = data->timestamp;
  metadataMessage.data.chreAudioDataMetadata.sampleRate = data->sampleRate;
  metadataMessage.data.chreAudioDataMetadata.sampleCount = data->sampleCount;
  metadataMessage.data.chreAudioDataMetadata.format = data->format;
  metadataMessage.status = true;
  metadataMessage.which_data =
      chre_rpc_GeneralEventsMessage_chreAudioDataMetadata_tag;
  sendPartialGeneralEventToHost(metadataMessage);

  // send the samples
  chre_rpc_GeneralEventsMessage samplesMessage;
  samplesMessage.status = false;

  uint32_t totalSamples = data->sampleCount;
  uint32_t maxSamplesPerMessage =
      data->format == CHRE_AUDIO_DATA_FORMAT_16_BIT_SIGNED_PCM
          ? kChreAudioDataEventMaxSampleBufferSize / 2
          : kChreAudioDataEventMaxSampleBufferSize;
  uint32_t samplesRemainingAfterSend = totalSamples > maxSamplesPerMessage
                                           ? totalSamples - maxSamplesPerMessage
                                           : 0;

  uint32_t numSamplesToSend = MIN(maxSamplesPerMessage, totalSamples);
  uint32_t sampleIdx = 0;
  for (int id = 0; numSamplesToSend > 0; id++) {
    samplesMessage.data.chreAudioDataSamples.id = id;

    // assign data
    if (data->format == CHRE_AUDIO_DATA_FORMAT_8_BIT_U_LAW) {
      samplesMessage.data.chreAudioDataSamples.samples.size = numSamplesToSend;
      std::memcpy(samplesMessage.data.chreAudioDataSamples.samples.bytes,
                  &data->samplesULaw8[sampleIdx], numSamplesToSend);

      samplesMessage.status = true;
      samplesMessage.which_data =
          chre_rpc_GeneralEventsMessage_chreAudioDataSamples_tag;
    } else if (data->format == CHRE_AUDIO_DATA_FORMAT_16_BIT_SIGNED_PCM) {
      // send double the bytes since each sample is 2B
      samplesMessage.data.chreAudioDataSamples.samples.size =
          numSamplesToSend * 2;
      std::memcpy(samplesMessage.data.chreAudioDataSamples.samples.bytes,
                  &data->samplesS16[sampleIdx], numSamplesToSend * 2);

      samplesMessage.status = true;
      samplesMessage.which_data =
          chre_rpc_GeneralEventsMessage_chreAudioDataSamples_tag;
    } else {
      LOGE("Chre audio data event: format %" PRIu8 " unknown", data->format);
      return false;
    }

    sendPartialGeneralEventToHost(samplesMessage);

    sampleIdx += numSamplesToSend;
    if (samplesRemainingAfterSend > maxSamplesPerMessage) {
      numSamplesToSend = maxSamplesPerMessage;
      samplesRemainingAfterSend =
          samplesRemainingAfterSend - maxSamplesPerMessage;
    } else {
      numSamplesToSend = samplesRemainingAfterSend;
      samplesRemainingAfterSend = 0;
    }
  }
  closePartialGeneralEventToHost();
  return true;
}

bool ChreApiTestService::sendGeneralEventToHost(
    const chre_rpc_GeneralEventsMessage &message) {
  sendPartialGeneralEventToHost(message);
  return closePartialGeneralEventToHost();
}

void ChreApiTestService::sendPartialGeneralEventToHost(
    const chre_rpc_GeneralEventsMessage &message) {
  ChreApiTestManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  pw::Status status = mEventWriter->Write(message);
  CHRE_ASSERT(status.ok());
}

bool ChreApiTestService::closePartialGeneralEventToHost() {
  ++mEventSentCount;

  if (mEventSentCount == mEventExpectedCount) {
    chreTimerCancel(mEventTimerHandle);
    mEventTimerHandle = CHRE_TIMER_INVALID;
    finishAndCloseWriter(mEventWriter);
    LOGD("GatherEvents: Finish");
    return false;
  }
  return true;
}

void ChreApiTestService::handleGatheringEvent(uint16_t eventType,
                                              const void *eventData) {
  if (!mEventWriter.has_value()) {
    return;
  }

  bool matchedEvent = false;
  for (uint32_t i = 0; i < mEventTypeCount; ++i) {
    if (mEventTypes[i] == eventType) {
      matchedEvent = true;
      break;
    }
  }
  if (!matchedEvent) {
    LOGD("GatherEvents: Received event with type: %" PRIu16
         " that did not match any gathered events",
         eventType);
    return;
  }

  LOGD("Gather events Received matching event with type: %" PRIu16, eventType);

  bool messageSent = false;
  chre_rpc_GeneralEventsMessage message;
  message.status = false;
  switch (eventType) {
    case CHRE_EVENT_SENSOR_ACCELEROMETER_DATA: {
      message.status = true;
      message.which_data =
          chre_rpc_GeneralEventsMessage_chreSensorThreeAxisData_tag;

      const auto *data =
          static_cast<const struct chreSensorThreeAxisData *>(eventData);
      message.data.chreSensorThreeAxisData.header.baseTimestamp =
          data->header.baseTimestamp;
      message.data.chreSensorThreeAxisData.header.sensorHandle =
          data->header.sensorHandle;
      message.data.chreSensorThreeAxisData.header.readingCount =
          data->header.readingCount;
      message.data.chreSensorThreeAxisData.header.accuracy =
          data->header.accuracy;
      message.data.chreSensorThreeAxisData.header.reserved =
          data->header.reserved;

      uint32_t numReadings =
          MIN(data->header.readingCount, kThreeAxisDataReadingsMaxCount);
      message.data.chreSensorThreeAxisData.readings_count = numReadings;
      for (uint32_t i = 0; i < numReadings; ++i) {
        message.data.chreSensorThreeAxisData.readings[i].timestampDelta =
            data->readings[i].timestampDelta;
        message.data.chreSensorThreeAxisData.readings[i].x =
            data->readings[i].x;
        message.data.chreSensorThreeAxisData.readings[i].y =
            data->readings[i].y;
        message.data.chreSensorThreeAxisData.readings[i].z =
            data->readings[i].z;
      }
      break;
    }
    case CHRE_EVENT_SENSOR_SAMPLING_CHANGE: {
      const auto *data =
          static_cast<const struct chreSensorSamplingStatusEvent *>(eventData);
      message.data.chreSensorSamplingStatusEvent.sensorHandle =
          data->sensorHandle;
      message.data.chreSensorSamplingStatusEvent.status.interval =
          data->status.interval;
      message.data.chreSensorSamplingStatusEvent.status.latency =
          data->status.latency;
      message.data.chreSensorSamplingStatusEvent.status.enabled =
          data->status.enabled;

      message.status = true;
      message.which_data =
          chre_rpc_GeneralEventsMessage_chreSensorSamplingStatusEvent_tag;
      break;
    }
    case CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION: {
      const auto *data =
          static_cast<const struct chreHostEndpointNotification *>(eventData);
      message.data.chreHostEndpointNotification.hostEndpointId =
          data->hostEndpointId;
      message.data.chreHostEndpointNotification.notificationType =
          data->notificationType;

      message.status = true;
      message.which_data =
          chre_rpc_GeneralEventsMessage_chreHostEndpointNotification_tag;
      break;
    }
    case CHRE_EVENT_BLE_ADVERTISEMENT: {
      const auto *data =
          static_cast<const struct chreBleAdvertisementEvent *>(eventData);
      message.data.chreBleAdvertisementEvent.reserved = data->reserved;

      uint32_t numReports =
          MIN(kChreBleAdvertisementReportMaxCount, data->numReports);
      message.data.chreBleAdvertisementEvent.reports_count = numReports;
      for (uint32_t i = 0; i < numReports; ++i) {
        message.data.chreBleAdvertisementEvent.reports[i].timestamp =
            data->reports[i].timestamp;
        message.data.chreBleAdvertisementEvent.reports[i]
            .eventTypeAndDataStatus = data->reports[i].eventTypeAndDataStatus;
        message.data.chreBleAdvertisementEvent.reports[i].addressType =
            data->reports[i].addressType;

        message.data.chreBleAdvertisementEvent.reports[i].address.size =
            CHRE_BLE_ADDRESS_LEN;
        std::memcpy(
            message.data.chreBleAdvertisementEvent.reports[i].address.bytes,
            data->reports[i].address, CHRE_BLE_ADDRESS_LEN);

        message.data.chreBleAdvertisementEvent.reports[i].primaryPhy =
            data->reports[i].primaryPhy;
        message.data.chreBleAdvertisementEvent.reports[i].secondaryPhy =
            data->reports[i].secondaryPhy;
        message.data.chreBleAdvertisementEvent.reports[i].advertisingSid =
            data->reports[i].advertisingSid;
        message.data.chreBleAdvertisementEvent.reports[i].txPower =
            data->reports[i].txPower;
        message.data.chreBleAdvertisementEvent.reports[i]
            .periodicAdvertisingInterval =
            data->reports[i].periodicAdvertisingInterval;
        message.data.chreBleAdvertisementEvent.reports[i].rssi =
            data->reports[i].rssi;
        message.data.chreBleAdvertisementEvent.reports[i].directAddressType =
            data->reports[i].directAddressType;

        message.data.chreBleAdvertisementEvent.reports[i].directAddress.size =
            CHRE_BLE_ADDRESS_LEN;
        std::memcpy(message.data.chreBleAdvertisementEvent.reports[i]
                        .directAddress.bytes,
                    data->reports[i].directAddress, CHRE_BLE_ADDRESS_LEN);

        message.data.chreBleAdvertisementEvent.reports[i].data.size =
            data->reports[i].dataLength;
        std::memcpy(
            message.data.chreBleAdvertisementEvent.reports[i].data.bytes,
            data->reports[i].data, data->reports[i].dataLength);

        message.data.chreBleAdvertisementEvent.reports[i].reserved =
            data->reports[i].reserved;
      }

      message.status = true;
      message.which_data =
          chre_rpc_GeneralEventsMessage_chreBleAdvertisementEvent_tag;
      break;
    }
    case CHRE_EVENT_AUDIO_SAMPLING_CHANGE: {
      const auto *data =
          static_cast<const struct chreAudioSourceStatusEvent *>(eventData);
      message.data.chreAudioSourceStatusEvent.handle = data->handle;
      message.data.chreAudioSourceStatusEvent.status.enabled =
          data->status.enabled;
      message.data.chreAudioSourceStatusEvent.status.suspended =
          data->status.suspended;

      message.status = true;
      message.which_data =
          chre_rpc_GeneralEventsMessage_chreAudioSourceStatusEvent_tag;
      break;
    }
    case CHRE_EVENT_AUDIO_DATA: {
      const auto *data =
          static_cast<const struct chreAudioDataEvent *>(eventData);
      messageSent = handleChreAudioDataEvent(data);
      break;
    }
    default: {
      LOGE("GatherEvents: event type: %" PRIu16 " not implemented", eventType);
    }
  }

  // If we already sent the message, we have nothing else to send.
  if (messageSent) {
    return;
  }

  if (!message.status) {
    LOGE("GatherEvents: unable to create message for event with type: %" PRIu16,
         eventType);
    return;
  }

  sendGeneralEventToHost(message);
}

void ChreApiTestService::handleTimerEvent(const void *cookie) {
  if (mWriter.has_value() && cookie == &mSyncTimerHandle) {
    sendFailureAndFinishCloseWriter(mWriter);
    mSyncTimerHandle = CHRE_TIMER_INVALID;
    LOGD("Active sync function: status: false (timeout)");
  } else if (mEventWriter.has_value() && cookie == &mEventTimerHandle) {
    finishAndCloseWriter(mEventWriter);
    mEventTimerHandle = CHRE_TIMER_INVALID;
    LOGD("Timeout for event collection");
  }
}

bool ChreApiTestService::startSyncTimer() {
  mSyncTimerHandle = chreTimerSet(
      kSyncFunctionTimeout, &mSyncTimerHandle /* cookie */, true /* oneShot */);
  return mSyncTimerHandle != CHRE_TIMER_INVALID;
}

// Start ChreApiTestManager functions

bool ChreApiTestManager::start() {
  chre::RpcServer::Service service = {.service = mChreApiTestService,
                                      .id = 0x61002d392de8430a,
                                      .version = 0x01000000};
  if (!mServer.registerServices(1, &service)) {
    LOGE("Error while registering the service");
    return false;
  }

  return true;
}

void ChreApiTestManager::end() {
  mServer.close();
}

void ChreApiTestManager::handleEvent(uint32_t senderInstanceId,
                                     uint16_t eventType,
                                     const void *eventData) {
  if (!mServer.handleEvent(senderInstanceId, eventType, eventData)) {
    LOGE("An RPC error occurred");
  }

  mChreApiTestService.handleGatheringEvent(eventType, eventData);

  switch (eventType) {
    case CHRE_EVENT_BLE_ASYNC_RESULT:
      mChreApiTestService.handleBleAsyncResult(
          static_cast<const chreAsyncResult *>(eventData));
      break;
    case CHRE_EVENT_TIMER:
      mChreApiTestService.handleTimerEvent(eventData);
      break;
    default: {
      // ignore
    }
  }
}

void ChreApiTestManager::setPermissionForNextMessage(uint32_t permission) {
  mServer.setPermissionForNextMessage(permission);
}

// End ChreApiTestManager functions
