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

#include "chre/platform/shared/host_protocol_chre.h"

#include <inttypes.h>
#include <string.h>

#include "chre/core/event_loop_manager.h"
#include "chre/core/host_endpoint_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/generated/host_messages_generated.h"
#include "chre/util/macros.h"

using flatbuffers::Offset;
using flatbuffers::Vector;

namespace chre {

// This is similar to getStringFromByteVector in host_protocol_host.h. Ensure
// that method's implementation is kept in sync with this.
const char *getStringFromByteVector(const flatbuffers::Vector<int8_t> *vec) {
  constexpr int8_t kNullChar = static_cast<int8_t>('\0');
  const char *str = nullptr;

  // Check that the vector is present, non-empty, and null-terminated
  if (vec != nullptr && vec->size() > 0 &&
      (*vec)[vec->size() - 1] == kNullChar) {
    str = reinterpret_cast<const char *>(vec->Data());
  }

  return str;
}

bool HostProtocolChre::decodeMessageFromHost(const void *message,
                                             size_t messageLen) {
  bool success = verifyMessage(message, messageLen);
  if (!success) {
    LOGE("Dropping invalid/corrupted message from host (length %zu)",
         messageLen);
  } else {
    const fbs::MessageContainer *container = fbs::GetMessageContainer(message);
    uint16_t hostClientId = container->host_addr()->client_id();

    switch (container->message_type()) {
      case fbs::ChreMessage::NanoappMessage: {
        const auto *nanoappMsg =
            static_cast<const fbs::NanoappMessage *>(container->message());
        // Required field; verifier ensures that this is not null (though it
        // may be empty)
        const flatbuffers::Vector<uint8_t> *msgData = nanoappMsg->message();
        HostMessageHandlers::handleNanoappMessage(
            nanoappMsg->app_id(), nanoappMsg->message_type(),
            nanoappMsg->host_endpoint(), msgData->data(), msgData->size());
        break;
      }

      case fbs::ChreMessage::HubInfoRequest:
        HostMessageHandlers::handleHubInfoRequest(hostClientId);
        break;

      case fbs::ChreMessage::NanoappListRequest:
        HostMessageHandlers::handleNanoappListRequest(hostClientId);
        break;

      case fbs::ChreMessage::LoadNanoappRequest: {
        const auto *request =
            static_cast<const fbs::LoadNanoappRequest *>(container->message());
        const flatbuffers::Vector<uint8_t> *appBinary = request->app_binary();
        const char *appBinaryFilename =
            getStringFromByteVector(request->app_binary_file_name());
        HostMessageHandlers::handleLoadNanoappRequest(
            hostClientId, request->transaction_id(), request->app_id(),
            request->app_version(), request->app_flags(),
            request->target_api_version(), appBinary->data(), appBinary->size(),
            appBinaryFilename, request->fragment_id(),
            request->total_app_size(), request->respond_before_start());
        break;
      }

      case fbs::ChreMessage::UnloadNanoappRequest: {
        const auto *request = static_cast<const fbs::UnloadNanoappRequest *>(
            container->message());
        HostMessageHandlers::handleUnloadNanoappRequest(
            hostClientId, request->transaction_id(), request->app_id(),
            request->allow_system_nanoapp_unload());
        break;
      }

      case fbs::ChreMessage::TimeSyncMessage: {
        const auto *request =
            static_cast<const fbs::TimeSyncMessage *>(container->message());
        HostMessageHandlers::handleTimeSyncMessage(request->offset());
        break;
      }

      case fbs::ChreMessage::DebugDumpRequest:
        HostMessageHandlers::handleDebugDumpRequest(hostClientId);
        break;

      case fbs::ChreMessage::SettingChangeMessage: {
        const auto *settingMessage =
            static_cast<const fbs::SettingChangeMessage *>(
                container->message());
        HostMessageHandlers::handleSettingChangeMessage(
            settingMessage->setting(), settingMessage->state());
        break;
      }

      case fbs::ChreMessage::SelfTestRequest: {
        HostMessageHandlers::handleSelfTestRequest(hostClientId);
        break;
      }

      case fbs::ChreMessage::HostEndpointConnected: {
        const auto *connectedMessage =
            static_cast<const fbs::HostEndpointConnected *>(
                container->message());
        struct chreHostEndpointInfo info;
        info.hostEndpointId = connectedMessage->host_endpoint();
        info.hostEndpointType = connectedMessage->type();
        if (strlen(reinterpret_cast<const char *>(
                connectedMessage->package_name()->data())) > 0) {
          info.isNameValid = true;
          memcpy(&info.packageName[0], connectedMessage->package_name()->data(),
                 MIN(connectedMessage->package_name()->size(),
                     CHRE_MAX_ENDPOINT_NAME_LEN));
          info.packageName[CHRE_MAX_ENDPOINT_NAME_LEN - 1] = '\0';
        } else {
          info.isNameValid = false;
        }
        if (strlen(reinterpret_cast<const char *>(
                connectedMessage->attribution_tag()->data())) > 0) {
          info.isTagValid = true;
          memcpy(&info.attributionTag[0],
                 connectedMessage->attribution_tag()->data(),
                 MIN(connectedMessage->attribution_tag()->size(),
                     CHRE_MAX_ENDPOINT_TAG_LEN));
          info.attributionTag[CHRE_MAX_ENDPOINT_NAME_LEN - 1] = '\0';
        } else {
          info.isTagValid = false;
        }

        EventLoopManagerSingleton::get()
            ->getHostEndpointManager()
            .postHostEndpointConnected(info);
        break;
      }

      case fbs::ChreMessage::HostEndpointDisconnected: {
        const auto *disconnectedMessage =
            static_cast<const fbs::HostEndpointDisconnected *>(
                container->message());
        EventLoopManagerSingleton::get()
            ->getHostEndpointManager()
            .postHostEndpointDisconnected(disconnectedMessage->host_endpoint());
        break;
      }

      case fbs::ChreMessage::NanConfigurationUpdate: {
        const auto *nanConfigUpdateMessage =
            static_cast<const fbs::NanConfigurationUpdate *>(
                container->message());
        HostMessageHandlers::handleNanConfigurationUpdate(
            nanConfigUpdateMessage->enabled());
        break;
      }

      case fbs::ChreMessage::DebugConfiguration: {
        const auto *debugConfiguration =
            static_cast<const fbs::DebugConfiguration *>(container->message());
        HostMessageHandlers::handleDebugConfiguration(debugConfiguration);
        break;
      }

      case fbs::ChreMessage::PulseRequest: {
        HostMessageHandlers::handlePulseRequest();
        break;
      }

      default:
        LOGW("Got invalid/unexpected message type %" PRIu8,
             static_cast<uint8_t>(container->message_type()));
        success = false;
    }
  }

  return success;
}

void HostProtocolChre::encodeHubInfoResponse(
    ChreFlatBufferBuilder &builder, const char *name, const char *vendor,
    const char *toolchain, uint32_t legacyPlatformVersion,
    uint32_t legacyToolchainVersion, float peakMips, float stoppedPower,
    float sleepPower, float peakPower, uint32_t maxMessageLen,
    uint64_t platformId, uint32_t version, uint16_t hostClientId) {
  auto nameOffset = addStringAsByteVector(builder, name);
  auto vendorOffset = addStringAsByteVector(builder, vendor);
  auto toolchainOffset = addStringAsByteVector(builder, toolchain);

  auto response = fbs::CreateHubInfoResponse(
      builder, nameOffset, vendorOffset, toolchainOffset, legacyPlatformVersion,
      legacyToolchainVersion, peakMips, stoppedPower, sleepPower, peakPower,
      maxMessageLen, platformId, version);
  finalize(builder, fbs::ChreMessage::HubInfoResponse, response.Union(),
           hostClientId);
}

void HostProtocolChre::addNanoappListEntry(
    ChreFlatBufferBuilder &builder,
    DynamicVector<Offset<fbs::NanoappListEntry>> &offsetVector, uint64_t appId,
    uint32_t appVersion, bool enabled, bool isSystemNanoapp,
    uint32_t appPermissions,
    const DynamicVector<struct chreNanoappRpcService> &rpcServices) {
  DynamicVector<Offset<fbs::NanoappRpcService>> rpcServiceList;
  for (const auto &service : rpcServices) {
    Offset<fbs::NanoappRpcService> offsetService =
        fbs::CreateNanoappRpcService(builder, service.id, service.version);
    if (!rpcServiceList.push_back(offsetService)) {
      LOGE("Couldn't push RPC service to list");
    }
  }

  auto vectorOffset =
      builder.CreateVector<Offset<fbs::NanoappRpcService>>(rpcServiceList);
  auto offset = fbs::CreateNanoappListEntry(builder, appId, appVersion, enabled,
                                            isSystemNanoapp, appPermissions,
                                            vectorOffset);

  if (!offsetVector.push_back(offset)) {
    LOGE("Couldn't push nanoapp list entry offset!");
  }
}

void HostProtocolChre::finishNanoappListResponse(
    ChreFlatBufferBuilder &builder,
    DynamicVector<Offset<fbs::NanoappListEntry>> &offsetVector,
    uint16_t hostClientId) {
  auto vectorOffset =
      builder.CreateVector<Offset<fbs::NanoappListEntry>>(offsetVector);
  auto response = fbs::CreateNanoappListResponse(builder, vectorOffset);
  finalize(builder, fbs::ChreMessage::NanoappListResponse, response.Union(),
           hostClientId);
}

void HostProtocolChre::encodePulseResponse(ChreFlatBufferBuilder &builder) {
  auto response = fbs::CreatePulseResponse(builder);
  finalize(builder, fbs::ChreMessage::PulseResponse, response.Union());
}

void HostProtocolChre::encodeLoadNanoappResponse(ChreFlatBufferBuilder &builder,
                                                 uint16_t hostClientId,
                                                 uint32_t transactionId,
                                                 bool success,
                                                 uint32_t fragmentId) {
  auto response = fbs::CreateLoadNanoappResponse(builder, transactionId,
                                                 success, fragmentId);
  finalize(builder, fbs::ChreMessage::LoadNanoappResponse, response.Union(),
           hostClientId);
}

void HostProtocolChre::encodeNanoappTokenDatabaseInfo(
    ChreFlatBufferBuilder &builder, uint16_t instanceId, uint64_t appId,
    uint32_t tokenDatabaseOffset, size_t tokenDatabaseSize) {
  auto response = fbs::CreateNanoappTokenDatabaseInfo(
      builder, instanceId, appId, tokenDatabaseOffset, tokenDatabaseSize);
  finalize(builder, fbs::ChreMessage::NanoappTokenDatabaseInfo,
           response.Union());
}

void HostProtocolChre::encodeUnloadNanoappResponse(
    ChreFlatBufferBuilder &builder, uint16_t hostClientId,
    uint32_t transactionId, bool success) {
  auto response =
      fbs::CreateUnloadNanoappResponse(builder, transactionId, success);
  finalize(builder, fbs::ChreMessage::UnloadNanoappResponse, response.Union(),
           hostClientId);
}

void HostProtocolChre::encodeLogMessages(ChreFlatBufferBuilder &builder,
                                         const uint8_t *logBuffer,
                                         size_t bufferSize) {
  auto logBufferOffset = builder.CreateVector(
      reinterpret_cast<const int8_t *>(logBuffer), bufferSize);
  auto message = fbs::CreateLogMessage(builder, logBufferOffset);
  finalize(builder, fbs::ChreMessage::LogMessage, message.Union());
}

void HostProtocolChre::encodeLogMessagesV2(ChreFlatBufferBuilder &builder,
                                           const uint8_t *logBuffer,
                                           size_t bufferSize,
                                           uint32_t numLogsDropped) {
  auto logBufferOffset = builder.CreateVector(
      reinterpret_cast<const int8_t *>(logBuffer), bufferSize);
  auto message =
      fbs::CreateLogMessageV2(builder, logBufferOffset, numLogsDropped);
  finalize(builder, fbs::ChreMessage::LogMessageV2, message.Union());
}

void HostProtocolChre::encodeDebugDumpData(ChreFlatBufferBuilder &builder,
                                           uint16_t hostClientId,
                                           const char *debugStr,
                                           size_t debugStrSize) {
  auto debugStrOffset = builder.CreateVector(
      reinterpret_cast<const int8_t *>(debugStr), debugStrSize);
  auto message = fbs::CreateDebugDumpData(builder, debugStrOffset);
  finalize(builder, fbs::ChreMessage::DebugDumpData, message.Union(),
           hostClientId);
}

void HostProtocolChre::encodeDebugDumpResponse(ChreFlatBufferBuilder &builder,
                                               uint16_t hostClientId,
                                               bool success,
                                               uint32_t dataCount) {
  auto response = fbs::CreateDebugDumpResponse(builder, success, dataCount);
  finalize(builder, fbs::ChreMessage::DebugDumpResponse, response.Union(),
           hostClientId);
}

void HostProtocolChre::encodeTimeSyncRequest(ChreFlatBufferBuilder &builder) {
  auto request = fbs::CreateTimeSyncRequest(builder);
  finalize(builder, fbs::ChreMessage::TimeSyncRequest, request.Union());
}

void HostProtocolChre::encodeLowPowerMicAccessRequest(
    ChreFlatBufferBuilder &builder) {
  auto request = fbs::CreateLowPowerMicAccessRequest(builder);
  finalize(builder, fbs::ChreMessage::LowPowerMicAccessRequest,
           request.Union());
}

void HostProtocolChre::encodeLowPowerMicAccessRelease(
    ChreFlatBufferBuilder &builder) {
  auto request = fbs::CreateLowPowerMicAccessRelease(builder);
  finalize(builder, fbs::ChreMessage::LowPowerMicAccessRelease,
           request.Union());
}

void HostProtocolChre::encodeSelfTestResponse(ChreFlatBufferBuilder &builder,
                                              uint16_t hostClientId,
                                              bool success) {
  auto response = fbs::CreateSelfTestResponse(builder, success);
  finalize(builder, fbs::ChreMessage::SelfTestResponse, response.Union(),
           hostClientId);
}

void HostProtocolChre::encodeMetricLog(ChreFlatBufferBuilder &builder,
                                       uint32_t metricId,
                                       const uint8_t *encodedMsg,
                                       size_t metricSize) {
  auto encodedMessage = builder.CreateVector(
      reinterpret_cast<const int8_t *>(encodedMsg), metricSize);
  auto message = fbs::CreateMetricLog(builder, metricId, encodedMessage);
  finalize(builder, fbs::ChreMessage::MetricLog, message.Union());
}

void HostProtocolChre::encodeNanConfigurationRequest(
    ChreFlatBufferBuilder &builder, bool enable) {
  auto request = fbs::CreateNanConfigurationRequest(builder, enable);
  finalize(builder, fbs::ChreMessage::NanConfigurationRequest, request.Union());
}

bool HostProtocolChre::getSettingFromFbs(fbs::Setting setting,
                                         Setting *chreSetting) {
  bool success = true;
  switch (setting) {
    case fbs::Setting::LOCATION:
      *chreSetting = Setting::LOCATION;
      break;
    case fbs::Setting::WIFI_AVAILABLE:
      *chreSetting = Setting::WIFI_AVAILABLE;
      break;
    case fbs::Setting::AIRPLANE_MODE:
      *chreSetting = Setting::AIRPLANE_MODE;
      break;
    case fbs::Setting::MICROPHONE:
      *chreSetting = Setting::MICROPHONE;
      break;
    case fbs::Setting::BLE_AVAILABLE:
      *chreSetting = Setting::BLE_AVAILABLE;
      break;
    default:
      LOGE("Unknown setting %" PRIu8, static_cast<uint8_t>(setting));
      success = false;
  }

  return success;
}

bool HostProtocolChre::getSettingEnabledFromFbs(fbs::SettingState state,
                                                bool *chreSettingEnabled) {
  bool success = true;
  switch (state) {
    case fbs::SettingState::DISABLED:
      *chreSettingEnabled = false;
      break;
    case fbs::SettingState::ENABLED:
      *chreSettingEnabled = true;
      break;
    default:
      LOGE("Unknown state %" PRIu8, static_cast<uint8_t>(state));
      success = false;
  }

  return success;
}

}  // namespace chre
