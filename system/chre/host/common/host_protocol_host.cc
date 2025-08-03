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

#include "chre_host/host_protocol_host.h"

#include <inttypes.h>
#include <string.h>

#include "chre_host/log.h"

using flatbuffers::FlatBufferBuilder;
using flatbuffers::Offset;

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

namespace android {
namespace chre {

// This is similar to getStringFromByteVector in host_protocol_chre.h. Ensure
// that method's implementation is kept in sync with this.
const char *getStringFromByteVector(const std::vector<int8_t> &vec) {
  constexpr int8_t kNullChar = static_cast<int8_t>('\0');
  const char *str = nullptr;

  // Check that the vector is present, non-empty, and null-terminated
  if (vec.size() > 0 && vec[vec.size() - 1] == kNullChar) {
    str = reinterpret_cast<const char *>(vec.data());
  }

  return str;
}

bool HostProtocolHost::decodeMessageFromChre(const void *message,
                                             size_t messageLen,
                                             IChreMessageHandlers &handlers) {
  bool success = verifyMessage(message, messageLen);
  if (success) {
    std::unique_ptr<fbs::MessageContainerT> container =
        fbs::UnPackMessageContainer(message);
    fbs::ChreMessageUnion &msg = container->message;

    switch (container->message.type) {
      case fbs::ChreMessage::NanoappMessage:
        handlers.handleNanoappMessage(*msg.AsNanoappMessage());
        break;

      case fbs::ChreMessage::HubInfoResponse:
        handlers.handleHubInfoResponse(*msg.AsHubInfoResponse());
        break;

      case fbs::ChreMessage::NanoappListResponse:
        handlers.handleNanoappListResponse(*msg.AsNanoappListResponse());
        break;

      case fbs::ChreMessage::LoadNanoappResponse:
        handlers.handleLoadNanoappResponse(*msg.AsLoadNanoappResponse());
        break;

      case fbs::ChreMessage::UnloadNanoappResponse:
        handlers.handleUnloadNanoappResponse(*msg.AsUnloadNanoappResponse());
        break;

      case fbs::ChreMessage::DebugDumpData:
        handlers.handleDebugDumpData(*msg.AsDebugDumpData());
        break;

      case fbs::ChreMessage::DebugDumpResponse:
        handlers.handleDebugDumpResponse(*msg.AsDebugDumpResponse());
        break;

      case fbs::ChreMessage::SelfTestResponse:
        handlers.handleSelfTestResponse(*msg.AsSelfTestResponse());
        break;

      default:
        LOGW("Got invalid/unexpected message type %" PRIu8,
             static_cast<uint8_t>(msg.type));
        success = false;
    }
  }

  return success;
}

void HostProtocolHost::encodeHubInfoRequest(FlatBufferBuilder &builder) {
  auto request = fbs::CreateHubInfoRequest(builder);
  finalize(builder, fbs::ChreMessage::HubInfoRequest, request.Union());
}

void HostProtocolHost::encodeDebugConfiguration(FlatBufferBuilder &builder) {
#ifdef CHRE_HEALTH_MONITOR_CHECK_CRASH
  auto request = fbs::CreateDebugConfiguration(builder, true);
#else
  auto request = fbs::CreateDebugConfiguration(builder, false);
#endif  // CHRE_HEALTH_MONITOR_CHECK_CRASH
  finalize(builder, fbs::ChreMessage::DebugConfiguration, request.Union());
}

void HostProtocolHost::encodeFragmentedLoadNanoappRequest(
    flatbuffers::FlatBufferBuilder &builder,
    const FragmentedLoadRequest &request, bool respondBeforeStart) {
  encodeLoadNanoappRequestForBinary(
      builder, request.transactionId, request.appId, request.appVersion,
      request.appFlags, request.targetApiVersion, request.binary,
      request.fragmentId, request.appTotalSizeBytes, respondBeforeStart);
}

void HostProtocolHost::encodeNanoappListRequest(FlatBufferBuilder &builder) {
  auto request = fbs::CreateNanoappListRequest(builder);
  finalize(builder, fbs::ChreMessage::NanoappListRequest, request.Union());
}

void HostProtocolHost::encodeUnloadNanoappRequest(
    FlatBufferBuilder &builder, uint32_t transactionId, uint64_t appId,
    bool allowSystemNanoappUnload) {
  auto request = fbs::CreateUnloadNanoappRequest(builder, transactionId, appId,
                                                 allowSystemNanoappUnload);
  finalize(builder, fbs::ChreMessage::UnloadNanoappRequest, request.Union());
}

void HostProtocolHost::encodeTimeSyncMessage(FlatBufferBuilder &builder,
                                             int64_t offset) {
  auto request = fbs::CreateTimeSyncMessage(builder, offset);
  finalize(builder, fbs::ChreMessage::TimeSyncMessage, request.Union());
}

void HostProtocolHost::encodeDebugDumpRequest(FlatBufferBuilder &builder) {
  auto request = fbs::CreateDebugDumpRequest(builder);
  finalize(builder, fbs::ChreMessage::DebugDumpRequest, request.Union());
}

bool HostProtocolHost::extractHostClientIdAndType(
    const void *message, size_t messageLen, uint16_t *hostClientId,
    ::chre::fbs::ChreMessage *messageType) {
  bool success = false;
  if (hostClientId != nullptr && messageType != nullptr) {
    success = verifyMessage(message, messageLen);

    if (success) {
      const fbs::MessageContainer *container =
          fbs::GetMessageContainer(message);
      // host_addr guaranteed to be non-null via verifyMessage (it's a required
      // field)
      *hostClientId = container->host_addr()->client_id();
      *messageType = container->message_type();
    }
  }

  return success;
}

bool HostProtocolHost::mutateHostClientId(void *message, size_t messageLen,
                                          uint16_t hostClientId) {
  bool success = verifyMessage(message, messageLen);

  if (!success) {
    LOGE("Message verification failed - can't mutate host ID");
  } else {
    fbs::MessageContainer *container = fbs::GetMutableMessageContainer(message);
    // host_addr guaranteed to be non-null via verifyMessage (it's a required
    // field)
    container->mutable_host_addr()->mutate_client_id(hostClientId);
    success = true;
  }

  return success;
}

void HostProtocolHost::encodeLoadNanoappRequestForBinary(
    FlatBufferBuilder &builder, uint32_t transactionId, uint64_t appId,
    uint32_t appVersion, uint32_t appFlags, uint32_t targetApiVersion,
    const std::vector<uint8_t> &nanoappBinary, uint32_t fragmentId,
    size_t appTotalSizeBytes, bool respondBeforeStart) {
  auto appBinary = builder.CreateVector(nanoappBinary);
  auto request = fbs::CreateLoadNanoappRequest(
      builder, transactionId, appId, appVersion, targetApiVersion, appBinary,
      fragmentId, appTotalSizeBytes, 0 /* app_binary_file_name */, appFlags,
      respondBeforeStart);
  finalize(builder, fbs::ChreMessage::LoadNanoappRequest, request.Union());
}

void HostProtocolHost::encodeLoadNanoappRequestForFile(
    flatbuffers::FlatBufferBuilder &builder, uint32_t transactionId,
    uint64_t appId, uint32_t appVersion, uint32_t targetApiVersion,
    const char *nanoappBinaryName) {
  const std::vector<uint8_t> emptyAppBinary;
  auto appBinary = builder.CreateVector(emptyAppBinary);
  auto appBinaryName = addStringAsByteVector(builder, nanoappBinaryName);
  auto request = fbs::CreateLoadNanoappRequest(
      builder, transactionId, appId, appVersion, targetApiVersion, appBinary,
      0 /* fragmentId */, 0 /* appTotalSizeBytes */, appBinaryName);
  finalize(builder, fbs::ChreMessage::LoadNanoappRequest, request.Union());
}

void HostProtocolHost::encodeSettingChangeNotification(
    flatbuffers::FlatBufferBuilder &builder, ::chre::fbs::Setting setting,
    ::chre::fbs::SettingState newState) {
  auto notification =
      fbs::CreateSettingChangeMessage(builder, setting, newState);
  finalize(builder, fbs::ChreMessage::SettingChangeMessage,
           notification.Union());
}

void HostProtocolHost::encodeSelfTestRequest(
    flatbuffers::FlatBufferBuilder &builder) {
  auto request = fbs::CreateSelfTestRequest(builder);
  finalize(builder, fbs::ChreMessage::SelfTestRequest, request.Union());
}

void HostProtocolHost::encodeHostEndpointConnected(
    flatbuffers::FlatBufferBuilder &builder, uint16_t hostEndpointId,
    uint8_t type, const std::string &packageName,
    const std::string &attributionTag) {
  std::vector<int8_t> packageNameVec(packageName.begin(), packageName.end());
  packageNameVec.push_back('\0');
  std::vector<int8_t> attributionTagVec(attributionTag.begin(),
                                        attributionTag.end());
  attributionTagVec.push_back('\0');

  auto message = fbs::CreateHostEndpointConnectedDirect(
      builder, hostEndpointId, type, &packageNameVec, &attributionTagVec);
  finalize(builder, fbs::ChreMessage::HostEndpointConnected, message.Union());
}

void HostProtocolHost::encodeHostEndpointDisconnected(
    flatbuffers::FlatBufferBuilder &builder, uint16_t hostEndpointId) {
  auto message = fbs::CreateHostEndpointDisconnected(builder, hostEndpointId);
  finalize(builder, fbs::ChreMessage::HostEndpointDisconnected,
           message.Union());
}

void HostProtocolHost::encodeNanconfigurationUpdate(
    flatbuffers::FlatBufferBuilder &builder, bool nanEnabled) {
  auto message = fbs::CreateNanConfigurationUpdate(builder, nanEnabled);
  finalize(builder, fbs::ChreMessage::NanConfigurationUpdate, message.Union());
}

void HostProtocolHost::encodePulseRequest(FlatBufferBuilder &builder) {
  auto message = fbs::CreatePulseRequest(builder);
  finalize(builder, fbs::ChreMessage::PulseRequest, message.Union());
}

}  // namespace chre
}  // namespace android
