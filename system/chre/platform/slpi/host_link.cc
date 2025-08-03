/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef FARF_MEDIUM
#define FARF_MEDIUM 1
#endif

#include "HAP_farf.h"
#include "timer.h"

#include "chre/core/event_loop_manager.h"
#include "chre/core/host_comms_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/platform/memory.h"
#include "chre/platform/shared/host_protocol_chre.h"
#include "chre/platform/shared/nanoapp_load_manager.h"
#ifdef CHRE_USE_BUFFERED_LOGGING
#include "chre/platform/shared/log_buffer_manager.h"
#endif
#include "chre/platform/slpi/fastrpc.h"
#include "chre/platform/slpi/power_control_util.h"
#include "chre/platform/system_time.h"
#include "chre/platform/system_timer.h"
#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/flatbuffers/helpers.h"
#include "chre/util/macros.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/unique_ptr.h"
#include "chre_api/chre/version.h"

#include <inttypes.h>
#include <limits.h>

namespace chre {

namespace {

constexpr size_t kOutboundQueueSize = 32;

//! The last time a time sync request message has been sent.
//! TODO: Make this a member of HostLinkBase
Nanoseconds gLastTimeSyncRequestNanos(0);

struct NanoappListData {
  ChreFlatBufferBuilder *builder;
  DynamicVector<NanoappListEntryOffset> nanoappEntries;
  uint16_t hostClientId;
};

enum class PendingMessageType {
  Shutdown,
  NanoappMessageToHost,
  HubInfoResponse,
  NanoappListResponse,
  LoadNanoappResponse,
  UnloadNanoappResponse,
  DebugDumpData,
  DebugDumpResponse,
  TimeSyncRequest,
  LowPowerMicAccessRequest,
  LowPowerMicAccessRelease,
  EncodedLogMessage,
  SelfTestResponse,
  MetricLog,
  NanConfigurationRequest,
};

struct PendingMessage {
  PendingMessage(PendingMessageType msgType, uint16_t hostClientId) {
    type = msgType;
    data.hostClientId = hostClientId;
  }

  PendingMessage(PendingMessageType msgType,
                 const MessageToHost *msgToHost = nullptr) {
    type = msgType;
    data.msgToHost = msgToHost;
  }

  PendingMessage(PendingMessageType msgType, ChreFlatBufferBuilder *builder) {
    type = msgType;
    data.builder = builder;
  }

  PendingMessageType type;
  union {
    const MessageToHost *msgToHost;
    uint16_t hostClientId;
    ChreFlatBufferBuilder *builder;
  } data;
};

struct UnloadNanoappCallbackData {
  uint64_t appId;
  uint32_t transactionId;
  uint16_t hostClientId;
  bool allowSystemNanoappUnload;
};

/**
 * @see buildAndEnqueueMessage()
 */
typedef void(MessageBuilderFunction)(ChreFlatBufferBuilder &builder,
                                     void *cookie);

FixedSizeBlockingQueue<PendingMessage, kOutboundQueueSize> gOutboundQueue;

int copyToHostBuffer(const ChreFlatBufferBuilder &builder,
                     unsigned char *buffer, size_t bufferSize,
                     unsigned int *messageLen) {
  uint8_t *data = builder.GetBufferPointer();
  size_t size = builder.GetSize();
  int result;

  if (size > bufferSize) {
    LOGE("Encoded structure size %zu too big for host buffer %zu; dropping",
         size, bufferSize);
    result = CHRE_FASTRPC_ERROR;
  } else {
    memcpy(buffer, data, size);
    *messageLen = size;
    result = CHRE_FASTRPC_SUCCESS;
  }

  return result;
}

/**
 * Wrapper function to enqueue a message on the outbound message queue. All
 * outgoing message to the host must be called through this function.
 *
 * @param message The message to send to host.
 *
 * @return true if the message was successfully added to the queue.
 */
bool enqueueMessage(PendingMessage message) {
  // Vote for big image temporarily when waking up the main thread waiting for
  // the message
  bool voteSuccess = slpiForceBigImage();
  bool success = gOutboundQueue.push(message);

  // Remove the vote only if we successfully made a big image transition
  if (voteSuccess) {
    slpiRemoveBigImageVote();
  }

  return success;
}

/**
 * Helper function that takes care of the boilerplate for allocating a
 * ChreFlatBufferBuilder on the heap and adding it to the outbound message
 * queue.
 *
 * @param msgType Identifies the message while in the outboud queue
 * @param initialBufferSize Number of bytes to reserve when first allocating the
 *        ChreFlatBufferBuilder
 * @param buildMsgFunc Synchronous callback used to encode the FlatBuffer
 *        message. Will not be invoked if allocation fails.
 * @param cookie Opaque pointer that will be passed through to buildMsgFunc
 *
 * @return true if the message was successfully added to the queue
 */
bool buildAndEnqueueMessage(PendingMessageType msgType,
                            size_t initialBufferSize,
                            MessageBuilderFunction *msgBuilder, void *cookie) {
  bool pushed = false;

  auto builder = MakeUnique<ChreFlatBufferBuilder>(initialBufferSize);
  if (builder.isNull()) {
    LOGE("Couldn't allocate memory for message type %d",
         static_cast<int>(msgType));
  } else {
    msgBuilder(*builder, cookie);

    // TODO: if this fails, ideally we should block for some timeout until
    // there's space in the queue
    if (!enqueueMessage(PendingMessage(msgType, builder.get()))) {
      LOGE("Couldn't push message type %d to outbound queue",
           static_cast<int>(msgType));
    } else {
      builder.release();
      pushed = true;
    }
  }

  return pushed;
}

/**
 * FlatBuffer message builder callback used with handleNanoappListRequest()
 */
void buildNanoappListResponse(ChreFlatBufferBuilder &builder, void *cookie) {
  auto nanoappAdderCallback = [](const Nanoapp *nanoapp, void *data) {
    auto *cbData = static_cast<NanoappListData *>(data);
    HostProtocolChre::addNanoappListEntry(
        *(cbData->builder), cbData->nanoappEntries, nanoapp->getAppId(),
        nanoapp->getAppVersion(), true /*enabled*/, nanoapp->isSystemNanoapp(),
        nanoapp->getAppPermissions(), nanoapp->getRpcServices());
  };

  // Add a NanoappListEntry to the FlatBuffer for each nanoapp
  auto *cbData = static_cast<NanoappListData *>(cookie);
  cbData->builder = &builder;
  EventLoop &eventLoop = EventLoopManagerSingleton::get()->getEventLoop();
  eventLoop.forEachNanoapp(nanoappAdderCallback, cbData);
  HostProtocolChre::finishNanoappListResponse(builder, cbData->nanoappEntries,
                                              cbData->hostClientId);
}

void handleUnloadNanoappCallback(SystemCallbackType /*type*/,
                                 UniquePtr<UnloadNanoappCallbackData> &&data) {
  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    auto *cbData = static_cast<UnloadNanoappCallbackData *>(cookie);

    bool success = false;
    uint16_t instanceId;
    EventLoop &eventLoop = EventLoopManagerSingleton::get()->getEventLoop();
    if (!eventLoop.findNanoappInstanceIdByAppId(cbData->appId, &instanceId)) {
      LOGE("Couldn't unload app ID 0x%016" PRIx64 ": not found", cbData->appId);
    } else {
      success =
          eventLoop.unloadNanoapp(instanceId, cbData->allowSystemNanoappUnload);
    }

    HostProtocolChre::encodeUnloadNanoappResponse(
        builder, cbData->hostClientId, cbData->transactionId, success);
  };

  constexpr size_t kInitialBufferSize = 52;
  buildAndEnqueueMessage(PendingMessageType::UnloadNanoappResponse,
                         kInitialBufferSize, msgBuilder, data.get());
}

int generateMessageToHost(const MessageToHost *msgToHost, unsigned char *buffer,
                          size_t bufferSize, unsigned int *messageLen) {
  // TODO: ideally we'd construct our flatbuffer directly in the
  // host-supplied buffer
  constexpr size_t kFixedSizePortion = 88;
  ChreFlatBufferBuilder builder(msgToHost->message.size() + kFixedSizePortion);
  HostProtocolChre::encodeNanoappMessage(
      builder, msgToHost->appId, msgToHost->toHostData.messageType,
      msgToHost->toHostData.hostEndpoint, msgToHost->message.data(),
      msgToHost->message.size(), msgToHost->toHostData.appPermissions,
      msgToHost->toHostData.messagePermissions, msgToHost->toHostData.wokeHost);

  int result = copyToHostBuffer(builder, buffer, bufferSize, messageLen);

  auto &hostCommsManager =
      EventLoopManagerSingleton::get()->getHostCommsManager();
  hostCommsManager.onMessageToHostComplete(msgToHost);

  return result;
}

int generateHubInfoResponse(uint16_t hostClientId, unsigned char *buffer,
                            size_t bufferSize, unsigned int *messageLen) {
  constexpr size_t kInitialBufferSize = 192;

  constexpr char kHubName[] = "CHRE on SLPI";
  constexpr char kVendor[] = "Google";
  constexpr char kToolchain[] =
      "Hexagon Tools 8.x (clang " STRINGIFY(__clang_major__) "." STRINGIFY(
          __clang_minor__) "." STRINGIFY(__clang_patchlevel__) ")";
  constexpr uint32_t kLegacyPlatformVersion = 0;
  constexpr uint32_t kLegacyToolchainVersion =
      ((__clang_major__ & 0xFF) << 24) | ((__clang_minor__ & 0xFF) << 16) |
      (__clang_patchlevel__ & 0xFFFF);
  constexpr float kPeakMips = 350;
  constexpr float kStoppedPower = 0;
  constexpr float kSleepPower = 1;
  constexpr float kPeakPower = 15;

  // Note that this may execute prior to EventLoopManager::lateInit() completing
  ChreFlatBufferBuilder builder(kInitialBufferSize);
  HostProtocolChre::encodeHubInfoResponse(
      builder, kHubName, kVendor, kToolchain, kLegacyPlatformVersion,
      kLegacyToolchainVersion, kPeakMips, kStoppedPower, kSleepPower,
      kPeakPower, CHRE_MESSAGE_TO_HOST_MAX_SIZE, chreGetPlatformId(),
      chreGetVersion(), hostClientId);

  return copyToHostBuffer(builder, buffer, bufferSize, messageLen);
}

int generateMessageFromBuilder(ChreFlatBufferBuilder *builder,
                               unsigned char *buffer, size_t bufferSize,
                               unsigned int *messageLen,
                               bool isEncodedLogMessage) {
  CHRE_ASSERT(builder != nullptr);
  int result = copyToHostBuffer(*builder, buffer, bufferSize, messageLen);

#ifdef CHRE_USE_BUFFERED_LOGGING
  if (isEncodedLogMessage && LogBufferManagerSingleton::isInitialized()) {
    LogBufferManagerSingleton::get()->onLogsSentToHost();
  }
#else
  UNUSED_VAR(isEncodedLogMessage);
#endif

  builder->~ChreFlatBufferBuilder();
  memoryFree(builder);
  return result;
}

void sendDebugDumpData(uint16_t hostClientId, const char *debugStr,
                       size_t debugStrSize) {
  struct DebugDumpMessageData {
    uint16_t hostClientId;
    const char *debugStr;
    size_t debugStrSize;
  };

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const DebugDumpMessageData *>(cookie);
    HostProtocolChre::encodeDebugDumpData(builder, data->hostClientId,
                                          data->debugStr, data->debugStrSize);
  };

  constexpr size_t kFixedSizePortion = 52;
  DebugDumpMessageData data;
  data.hostClientId = hostClientId;
  data.debugStr = debugStr;
  data.debugStrSize = debugStrSize;
  buildAndEnqueueMessage(PendingMessageType::DebugDumpData,
                         kFixedSizePortion + debugStrSize, msgBuilder, &data);
}

void sendDebugDumpResponse(uint16_t hostClientId, bool success,
                           uint32_t dataCount) {
  struct DebugDumpResponseData {
    uint16_t hostClientId;
    bool success;
    uint32_t dataCount;
  };

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const DebugDumpResponseData *>(cookie);
    HostProtocolChre::encodeDebugDumpResponse(builder, data->hostClientId,
                                              data->success, data->dataCount);
  };

  constexpr size_t kInitialSize = 52;
  DebugDumpResponseData data;
  data.hostClientId = hostClientId;
  data.success = success;
  data.dataCount = dataCount;
  buildAndEnqueueMessage(PendingMessageType::DebugDumpResponse, kInitialSize,
                         msgBuilder, &data);
}

void sendSelfTestResponse(uint16_t hostClientId, bool success) {
  struct SelfTestResponseData {
    uint16_t hostClientId;
    bool success;
  };

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const SelfTestResponseData *>(cookie);
    HostProtocolChre::encodeSelfTestResponse(builder, data->hostClientId,
                                             data->success);
  };

  constexpr size_t kInitialSize = 52;
  SelfTestResponseData data;
  data.hostClientId = hostClientId;
  data.success = success;
  buildAndEnqueueMessage(PendingMessageType::SelfTestResponse, kInitialSize,
                         msgBuilder, &data);
}

/**
 * Sends a request to the host for a time sync message.
 */
void sendTimeSyncRequest() {
  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    HostProtocolChre::encodeTimeSyncRequest(builder);
  };

  constexpr size_t kInitialSize = 52;
  buildAndEnqueueMessage(PendingMessageType::TimeSyncRequest, kInitialSize,
                         msgBuilder, nullptr);

  gLastTimeSyncRequestNanos = SystemTime::getMonotonicTime();
}

void setTimeSyncRequestTimer(Nanoseconds delay) {
  static SystemTimer sTimeSyncRequestTimer;
  static bool sTimeSyncRequestTimerInitialized = false;

  // Check for timer init since this method might be called before CHRE
  // init is called.
  if (!sTimeSyncRequestTimerInitialized) {
    if (!sTimeSyncRequestTimer.init()) {
      FATAL_ERROR("Failed to initialize time sync request timer.");
    } else {
      sTimeSyncRequestTimerInitialized = true;
    }
  }
  if (sTimeSyncRequestTimer.isActive()) {
    sTimeSyncRequestTimer.cancel();
  }
  auto callback = [](void * /* data */) { sendTimeSyncRequest(); };
  if (!sTimeSyncRequestTimer.set(callback, nullptr /* data */, delay)) {
    LOGE("Failed to set time sync request timer.");
  }
}

/**
 * Helper function that prepares a nanoapp that can be loaded into the system
 * from a file stored on disk.
 *
 * @param hostClientId the ID of client that originated this transaction
 * @param transactionId the ID of the transaction
 * @param appId the ID of the app to load
 * @param appVersion the version of the app to load
 * @param targetApiVersion the API version this nanoapp is targeted for
 * @param appFilename Null-terminated ASCII string containing the file name that
 *     contains the app binary to be loaded.
 *
 * @return A valid pointer to a nanoapp that can be loaded into the system. A
 *     nullptr if the preparation process fails.
 */
UniquePtr<Nanoapp> handleLoadNanoappFile(uint16_t hostClientId,
                                         uint32_t transactionId, uint64_t appId,
                                         uint32_t appVersion,
                                         uint32_t targetApiVersion,
                                         const char *appFilename) {
  LOGD("Load nanoapp request for app ID 0x%016" PRIx64 " ver 0x%" PRIx32
       " target API 0x%08" PRIx32 " (txnId %" PRIu32 " client %" PRIu16 ")",
       appId, appVersion, targetApiVersion, transactionId, hostClientId);

  auto nanoapp = MakeUnique<Nanoapp>();

  if (nanoapp.isNull()) {
    LOG_OOM();
  } else if (!nanoapp->setAppInfo(appId, appVersion, appFilename,
                                  targetApiVersion) ||
             !nanoapp->isLoaded()) {
    nanoapp.reset(nullptr);
  }

  return nanoapp;
}

/**
 * FastRPC method invoked by the host to block on messages
 *
 * @param buffer Output buffer to populate with message data
 * @param bufferLen Size of the buffer, in bytes
 * @param messageLen Output parameter to populate with the size of the message
 *        in bytes upon success
 *
 * @return 0 on success, nonzero on failure
 */
extern "C" int chre_slpi_get_message_to_host(unsigned char *buffer,
                                             int bufferLen,
                                             unsigned int *messageLen) {
  CHRE_ASSERT(buffer != nullptr);
  CHRE_ASSERT(bufferLen > 0);
  CHRE_ASSERT(messageLen != nullptr);
  int result = CHRE_FASTRPC_ERROR;

  if (bufferLen <= 0 || buffer == nullptr || messageLen == nullptr) {
    // Note that we can't use regular logs here as they can result in sending
    // a message, leading to an infinite loop if the error is persistent
    FARF(FATAL, "Invalid buffer size %d or bad pointers (buf %d len %d)",
         bufferLen, (buffer == nullptr), (messageLen == nullptr));
  } else {
    size_t bufferSize = static_cast<size_t>(bufferLen);
    PendingMessage pendingMsg = gOutboundQueue.pop();

    switch (pendingMsg.type) {
      case PendingMessageType::Shutdown:
        result = CHRE_FASTRPC_ERROR_SHUTTING_DOWN;
        break;

      case PendingMessageType::NanoappMessageToHost:
        result = generateMessageToHost(pendingMsg.data.msgToHost, buffer,
                                       bufferSize, messageLen);
        break;

      case PendingMessageType::HubInfoResponse:
        result = generateHubInfoResponse(pendingMsg.data.hostClientId, buffer,
                                         bufferSize, messageLen);
        break;

      case PendingMessageType::NanoappListResponse:
      case PendingMessageType::LoadNanoappResponse:
      case PendingMessageType::UnloadNanoappResponse:
      case PendingMessageType::DebugDumpData:
      case PendingMessageType::DebugDumpResponse:
      case PendingMessageType::TimeSyncRequest:
      case PendingMessageType::LowPowerMicAccessRequest:
      case PendingMessageType::LowPowerMicAccessRelease:
      case PendingMessageType::EncodedLogMessage:
      case PendingMessageType::SelfTestResponse:
      case PendingMessageType::MetricLog:
      case PendingMessageType::NanConfigurationRequest:
        result = generateMessageFromBuilder(
            pendingMsg.data.builder, buffer, bufferSize, messageLen,
            pendingMsg.type == PendingMessageType::EncodedLogMessage);
        break;

      default:
        CHRE_ASSERT_LOG(false, "Unexpected pending message type");
    }
  }

  // Opportunistically send a time sync message (1 hour period threshold)
  constexpr Seconds kOpportunisticTimeSyncPeriod = Seconds(60 * 60 * 1);
  if (SystemTime::getMonotonicTime() >
      gLastTimeSyncRequestNanos + kOpportunisticTimeSyncPeriod) {
    sendTimeSyncRequest();
  }

  return result;
}

/**
 * FastRPC method invoked by the host to send a message to the system
 *
 * @param buffer
 * @param size
 *
 * @return 0 on success, nonzero on failure
 */
extern "C" int chre_slpi_deliver_message_from_host(const unsigned char *message,
                                                   int messageLen) {
  CHRE_ASSERT(message != nullptr);
  CHRE_ASSERT(messageLen > 0);
  int result = CHRE_FASTRPC_ERROR;

  if (message == nullptr || messageLen <= 0) {
    LOGE("Got null or invalid size (%d) message from host", messageLen);
  } else if (!HostProtocolChre::decodeMessageFromHost(
                 message, static_cast<size_t>(messageLen))) {
    LOGE("Failed to decode/handle message");
  } else {
    result = CHRE_FASTRPC_SUCCESS;
  }

  return result;
}

}  // anonymous namespace

void sendDebugDumpResultToHost(uint16_t hostClientId, const char *debugStr,
                               size_t debugStrSize, bool complete,
                               uint32_t dataCount) {
  if (debugStrSize > 0) {
    sendDebugDumpData(hostClientId, debugStr, debugStrSize);
  }

  if (complete) {
    sendDebugDumpResponse(hostClientId, true /*success*/, dataCount);
  }
}

void HostLink::flushMessagesSentByNanoapp(uint64_t /*appId*/) {
  // TODO: this is not completely safe since it's timer-based, but should work
  // well enough for the initial implementation. To be fully safe, we'd need
  // some synchronization with the thread that runs
  // chre_slpi_get_message_to_host(), e.g. a mutex that is held by that thread
  // prior to calling pop() and only released after onMessageToHostComplete
  // would've been called. If we acquire that mutex here, and hold it while
  // purging any messages sent by the nanoapp in the queue, we can be certain
  // that onMessageToHostComplete will not be called after this function returns
  // for messages sent by that nanoapp
  flushOutboundQueue();

  // One extra sleep to try to ensure that any messages popped just before
  // checking empty() are fully processed before we return
  constexpr time_timetick_type kFinalDelayUsec = 10000;
  timer_sleep(kFinalDelayUsec, T_USEC, true /* non_deferrable */);
}

bool HostLink::sendMessage(const MessageToHost *message) {
  return enqueueMessage(
      PendingMessage(PendingMessageType::NanoappMessageToHost, message));
}

bool HostLink::sendMetricLog(uint32_t metricId, const uint8_t *encodedMetric,
                             size_t encodedMetricLen) {
  struct MetricLogData {
    uint32_t metricId;
    const uint8_t *encodedMetric;
    size_t encodedMetricLen;
  };

  MetricLogData data;
  data.metricId = metricId;
  data.encodedMetric = encodedMetric;
  data.encodedMetricLen = encodedMetricLen;

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const MetricLogData *>(cookie);
    HostProtocolChre::encodeMetricLog(
        builder, data->metricId, data->encodedMetric, data->encodedMetricLen);
  };

  constexpr size_t kInitialSize = 52;
  buildAndEnqueueMessage(PendingMessageType::MetricLog, kInitialSize,
                         msgBuilder, &data);
  return true;
}

bool HostLinkBase::flushOutboundQueue() {
  int waitCount = 5;

  FARF(MEDIUM, "Draining message queue");
  while (!gOutboundQueue.empty() && waitCount-- > 0) {
    timer_sleep(kPollingIntervalUsec, T_USEC, true /* non_deferrable */);
  }

  return (waitCount >= 0);
}

void HostLinkBase::sendLogMessage(const uint8_t *logMessage,
                                  size_t logMessageSize) {
  struct LogMessageData {
    const uint8_t *logMsg;
    size_t logMsgSize;
  };

  LogMessageData logMessageData;

  logMessageData.logMsg = logMessage;
  logMessageData.logMsgSize = logMessageSize;

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const LogMessageData *>(cookie);
    HostProtocolChre::encodeLogMessages(builder, data->logMsg,
                                        data->logMsgSize);
  };

  constexpr size_t kInitialSize = 128;
  buildAndEnqueueMessage(PendingMessageType::EncodedLogMessage, kInitialSize,
                         msgBuilder, &logMessageData);
}

void HostLinkBase::sendLogMessageV2(const uint8_t *logMessage,
                                    size_t logMessageSize,
                                    uint32_t numLogsDropped) {
  struct LogMessageData {
    const uint8_t *logMsg;
    size_t logMsgSize;
    uint32_t numLogsDropped;
  };

  LogMessageData logMessageData{logMessage, logMessageSize, numLogsDropped};

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const LogMessageData *>(cookie);
    HostProtocolChre::encodeLogMessagesV2(
        builder, data->logMsg, data->logMsgSize, data->numLogsDropped);
  };

  constexpr size_t kInitialSize = 128;
  buildAndEnqueueMessage(PendingMessageType::EncodedLogMessage, kInitialSize,
                         msgBuilder, &logMessageData);
}

void HostLinkBase::sendNanConfiguration(bool enable) {
  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    const auto *data = static_cast<const bool *>(cookie);
    HostProtocolChre::encodeNanConfigurationRequest(builder, *data);
  };

  constexpr size_t kInitialSize = 48;
  buildAndEnqueueMessage(PendingMessageType::NanConfigurationRequest,
                         kInitialSize, msgBuilder, &enable);
}

void HostLinkBase::shutdown() {
  // Push a null message so the blocking call in chre_slpi_get_message_to_host()
  // returns and the host can exit cleanly. If the queue is full, try again to
  // avoid getting stuck (no other new messages should be entering the queue at
  // this time). Don't wait too long as the host-side binary may have died in
  // a state where it's not blocked in chre_slpi_get_message_to_host().
  int retryCount = 5;
  FARF(MEDIUM, "Shutting down host link");
  while (!enqueueMessage(PendingMessage(PendingMessageType::Shutdown)) &&
         --retryCount > 0) {
    timer_sleep(kPollingIntervalUsec, T_USEC, true /* non_deferrable */);
  }

  if (retryCount <= 0) {
    // Don't use LOGE, as it may involve trying to send a message
    FARF(ERROR,
         "No room in outbound queue for shutdown message and host not "
         "draining queue!");
  } else {
    // We were able to push the shutdown message. Wait for the queue to
    // completely flush before returning.
    if (!flushOutboundQueue()) {
      FARF(ERROR, "Host took too long to drain outbound queue; exiting anyway");
    } else {
      FARF(MEDIUM, "Finished draining queue");
    }
  }
}

void sendAudioRequest() {
  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    HostProtocolChre::encodeLowPowerMicAccessRequest(builder);
  };

  constexpr size_t kInitialSize = 32;
  buildAndEnqueueMessage(PendingMessageType::LowPowerMicAccessRequest,
                         kInitialSize, msgBuilder, nullptr);
}

void sendAudioRelease() {
  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    HostProtocolChre::encodeLowPowerMicAccessRelease(builder);
  };

  constexpr size_t kInitialSize = 32;
  buildAndEnqueueMessage(PendingMessageType::LowPowerMicAccessRelease,
                         kInitialSize, msgBuilder, nullptr);
}

void HostMessageHandlers::sendFragmentResponse(uint16_t hostClientId,
                                               uint32_t transactionId,
                                               uint32_t fragmentId,
                                               bool success) {
  struct FragmentedLoadInfoResponse {
    uint16_t hostClientId;
    uint32_t transactionId;
    uint32_t fragmentId;
    bool success;
  };

  auto msgBuilder = [](ChreFlatBufferBuilder &builder, void *cookie) {
    auto *cbData = static_cast<FragmentedLoadInfoResponse *>(cookie);
    HostProtocolChre::encodeLoadNanoappResponse(
        builder, cbData->hostClientId, cbData->transactionId, cbData->success,
        cbData->fragmentId);
  };

  FragmentedLoadInfoResponse response = {
      .hostClientId = hostClientId,
      .transactionId = transactionId,
      .fragmentId = fragmentId,
      .success = success,
  };
  constexpr size_t kInitialBufferSize = 48;
  buildAndEnqueueMessage(PendingMessageType::LoadNanoappResponse,
                         kInitialBufferSize, msgBuilder, &response);
}

void HostMessageHandlers::handleNanoappMessage(uint64_t appId,
                                               uint32_t messageType,
                                               uint16_t hostEndpoint,
                                               const void *messageData,
                                               size_t messageDataLen) {
  LOGD("Parsed nanoapp message from host: app ID 0x%016" PRIx64
       ", endpoint "
       "0x%" PRIx16 ", msgType %" PRIu32 ", payload size %zu",
       appId, hostEndpoint, messageType, messageDataLen);

  HostCommsManager &manager =
      EventLoopManagerSingleton::get()->getHostCommsManager();
  manager.sendMessageToNanoappFromHost(appId, messageType, hostEndpoint,
                                       messageData, messageDataLen);
}

void HostMessageHandlers::handleHubInfoRequest(uint16_t hostClientId) {
  // We generate the response in the context of chre_slpi_get_message_to_host
  LOGD("Hub info request from client ID %" PRIu16, hostClientId);
  enqueueMessage(
      PendingMessage(PendingMessageType::HubInfoResponse, hostClientId));
}

void HostMessageHandlers::handleNanoappListRequest(uint16_t hostClientId) {
  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    uint16_t cbHostClientId = NestedDataPtr<uint16_t>(data);

    NanoappListData cbData = {};
    cbData.hostClientId = cbHostClientId;

    size_t expectedNanoappCount =
        EventLoopManagerSingleton::get()->getEventLoop().getNanoappCount();
    if (!cbData.nanoappEntries.reserve(expectedNanoappCount)) {
      LOG_OOM();
    } else {
      constexpr size_t kFixedOverhead = 48;
      constexpr size_t kPerNanoappSize = 32;
      size_t initialBufferSize =
          (kFixedOverhead + expectedNanoappCount * kPerNanoappSize);

      buildAndEnqueueMessage(PendingMessageType::NanoappListResponse,
                             initialBufferSize, buildNanoappListResponse,
                             &cbData);
    }
  };

  LOGD("Nanoapp list request from client ID %" PRIu16, hostClientId);
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::NanoappListResponse,
      NestedDataPtr<uint16_t>(hostClientId), callback);
}

void HostMessageHandlers::handleLoadNanoappRequest(
    uint16_t hostClientId, uint32_t transactionId, uint64_t appId,
    uint32_t appVersion, uint32_t appFlags, uint32_t targetApiVersion,
    const void *buffer, size_t bufferLen, const char *appFileName,
    uint32_t fragmentId, size_t appBinaryLen, bool respondBeforeStart) {
  if (appFileName == nullptr) {
    loadNanoappData(hostClientId, transactionId, appId, appVersion, appFlags,
                    targetApiVersion, buffer, bufferLen, fragmentId,
                    appBinaryLen, respondBeforeStart);
    return;
  }

  UniquePtr<Nanoapp> pendingNanoapp =
      handleLoadNanoappFile(hostClientId, transactionId, appId, appVersion,
                            targetApiVersion, appFileName);

  if (!pendingNanoapp.isNull()) {
    auto cbData = MakeUnique<LoadNanoappCallbackData>();
    if (cbData.isNull()) {
      LOG_OOM();
    } else {
      cbData->transactionId = transactionId;
      cbData->hostClientId = hostClientId;
      cbData->appId = appId;
      cbData->fragmentId = fragmentId;
      cbData->nanoapp = std::move(pendingNanoapp);

      // Note that if this fails, we'll generate the error response in
      // the normal deferred callback
      EventLoopManagerSingleton::get()->deferCallback(
          SystemCallbackType::FinishLoadingNanoapp, std::move(cbData),
          finishLoadingNanoappCallback);
    }
  }
}

void HostMessageHandlers::handleUnloadNanoappRequest(
    uint16_t hostClientId, uint32_t transactionId, uint64_t appId,
    bool allowSystemNanoappUnload) {
  LOGD("Unload nanoapp request (txnID %" PRIu32 ") for appId 0x%016" PRIx64
       " system %d",
       transactionId, appId, allowSystemNanoappUnload);
  auto cbData = MakeUnique<UnloadNanoappCallbackData>();
  if (cbData == nullptr) {
    LOG_OOM();
  } else {
    cbData->appId = appId;
    cbData->transactionId = transactionId;
    cbData->hostClientId = hostClientId;
    cbData->allowSystemNanoappUnload = allowSystemNanoappUnload;

    EventLoopManagerSingleton::get()->deferCallback(
        SystemCallbackType::HandleUnloadNanoapp, std::move(cbData),
        handleUnloadNanoappCallback);
  }
}

void HostMessageHandlers::handleTimeSyncMessage(int64_t offset) {
  SystemTime::setEstimatedHostTimeOffset(offset);

  // Schedule a time sync request since offset may drift
  constexpr Seconds kClockDriftTimeSyncPeriod =
      Seconds(60 * 60 * 6);  // 6 hours
  setTimeSyncRequestTimer(kClockDriftTimeSyncPeriod);
}

void HostMessageHandlers::handleDebugDumpRequest(uint16_t hostClientId) {
  if (!chre::EventLoopManagerSingleton::get()
           ->getDebugDumpManager()
           .onDebugDumpRequested(hostClientId)) {
    LOGE("Couldn't trigger debug dump process");
    sendDebugDumpResponse(hostClientId, false /*success*/, 0 /*dataCount*/);
  }
}

void HostMessageHandlers::handleSettingChangeMessage(fbs::Setting setting,
                                                     fbs::SettingState state) {
  Setting chreSetting;
  bool chreSettingEnabled;
  if (HostProtocolChre::getSettingFromFbs(setting, &chreSetting) &&
      HostProtocolChre::getSettingEnabledFromFbs(state, &chreSettingEnabled)) {
    EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
        chreSetting, chreSettingEnabled);
  }
}

void HostMessageHandlers::handleSelfTestRequest(uint16_t hostClientId) {
  // TODO(b/182201569): Run test
  bool success = true;
  sendSelfTestResponse(hostClientId, success);
}

void HostMessageHandlers::handlePulseRequest() {}

void HostMessageHandlers::handleNanConfigurationUpdate(bool enabled) {
#ifdef CHRE_WIFI_NAN_SUPPORT_ENABLED
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .updateNanAvailability(enabled);
#else
  UNUSED_VAR(enabled);
#endif  // CHRE_WIFI_NAN_SUPPORT_ENABLED
}

}  // namespace chre
