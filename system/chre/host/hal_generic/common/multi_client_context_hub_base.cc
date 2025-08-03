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

#include "multi_client_context_hub_base.h"

#include <chre/platform/shared/host_protocol_common.h>
#include <chre_host/generated/host_messages_generated.h>
#include <chre_host/log.h>
#include "chre/common.h"
#include "chre/event.h"
#include "chre_host/config_util.h"
#include "chre_host/fragmented_load_transaction.h"
#include "chre_host/hal_error.h"
#include "chre_host/host_protocol_host.h"
#include "permissions_util.h"

#include <android_chre_flags.h>
#include <system/chre/core/chre_metrics.pb.h>

namespace android::hardware::contexthub::common::implementation {

using ::android::base::WriteStringToFd;
using ::android::chre::FragmentedLoadTransaction;
using ::android::chre::getStringFromByteVector;
using ::android::chre::flags::reliable_message_implementation;
using ::ndk::ScopedAStatus;
namespace fbs = ::chre::fbs;

namespace {
constexpr uint32_t kDefaultHubId = 0;

// timeout for calling getContextHubs(), which is synchronous
constexpr auto kHubInfoQueryTimeout = std::chrono::seconds(5);
// timeout for enable/disable test mode, which is synchronous
constexpr std::chrono::duration ktestModeTimeOut = std::chrono::seconds(5);

// The transaction id for synchronously load/unload a nanoapp in test mode.
constexpr int32_t kTestModeTransactionId{static_cast<int32_t>(0x80000000)};

bool isValidContextHubId(uint32_t hubId) {
  if (hubId != kDefaultHubId) {
    LOGE("Invalid context hub ID %" PRId32, hubId);
    return false;
  }
  return true;
}

bool getFbsSetting(const Setting &setting, fbs::Setting *fbsSetting) {
  bool foundSetting = true;
  switch (setting) {
    case Setting::LOCATION:
      *fbsSetting = fbs::Setting::LOCATION;
      break;
    case Setting::AIRPLANE_MODE:
      *fbsSetting = fbs::Setting::AIRPLANE_MODE;
      break;
    case Setting::MICROPHONE:
      *fbsSetting = fbs::Setting::MICROPHONE;
      break;
    default:
      foundSetting = false;
      LOGE("Setting update with invalid enum value %hhu", setting);
      break;
  }
  return foundSetting;
}

chre::fbs::SettingState toFbsSettingState(bool enabled) {
  return enabled ? chre::fbs::SettingState::ENABLED
                 : chre::fbs::SettingState::DISABLED;
}

// functions that extract different version numbers
inline constexpr int8_t extractChreApiMajorVersion(uint32_t chreVersion) {
  return static_cast<int8_t>(chreVersion >> 24);
}
inline constexpr int8_t extractChreApiMinorVersion(uint32_t chreVersion) {
  return static_cast<int8_t>(chreVersion >> 16);
}
inline constexpr uint16_t extractChrePatchVersion(uint32_t chreVersion) {
  return static_cast<uint16_t>(chreVersion);
}

// functions that help to generate ScopedAStatus from different values.
inline ScopedAStatus fromServiceError(HalError errorCode) {
  return ScopedAStatus::fromServiceSpecificError(
      static_cast<int32_t>(errorCode));
}
inline ScopedAStatus fromResult(bool result) {
  return result ? ScopedAStatus::ok()
                : fromServiceError(HalError::OPERATION_FAILED);
}

uint8_t toChreErrorCode(ErrorCode errorCode) {
  switch (errorCode) {
    case ErrorCode::OK:
      return CHRE_ERROR_NONE;
    case ErrorCode::TRANSIENT_ERROR:
      return CHRE_ERROR_TRANSIENT;
    case ErrorCode::PERMANENT_ERROR:
      return CHRE_ERROR;
    case ErrorCode::PERMISSION_DENIED:
      return CHRE_ERROR_PERMISSION_DENIED;
    case ErrorCode::DESTINATION_NOT_FOUND:
      return CHRE_ERROR_DESTINATION_NOT_FOUND;
  }

  return CHRE_ERROR;
}

ErrorCode toErrorCode(uint32_t chreErrorCode) {
  switch (chreErrorCode) {
    case CHRE_ERROR_NONE:
      return ErrorCode::OK;
    case CHRE_ERROR_TRANSIENT:
      return ErrorCode::TRANSIENT_ERROR;
    case CHRE_ERROR:
      return ErrorCode::PERMANENT_ERROR;
    case CHRE_ERROR_PERMISSION_DENIED:
      return ErrorCode::PERMISSION_DENIED;
    case CHRE_ERROR_DESTINATION_NOT_FOUND:
      return ErrorCode::DESTINATION_NOT_FOUND;
  }

  return ErrorCode::PERMANENT_ERROR;
}

}  // anonymous namespace

MultiClientContextHubBase::MultiClientContextHubBase() {
  mDeathRecipient = ndk::ScopedAIBinder_DeathRecipient(
      AIBinder_DeathRecipient_new(onClientDied));
  AIBinder_DeathRecipient_setOnUnlinked(
      mDeathRecipient.get(), /*onUnlinked= */ [](void *cookie) {
        LOGI("Callback is unlinked. Releasing the death recipient cookie.");
        delete static_cast<HalDeathRecipientCookie *>(cookie);
      });
  mDeadClientUnlinker =
      [&deathRecipient = mDeathRecipient](
          const std::shared_ptr<IContextHubCallback> &callback,
          void *deathRecipientCookie) {
        return AIBinder_unlinkToDeath(callback->asBinder().get(),
                                      deathRecipient.get(),
                                      deathRecipientCookie) == STATUS_OK;
      };
  mLogger.init();
}

ScopedAStatus MultiClientContextHubBase::getContextHubs(
    std::vector<ContextHubInfo> *contextHubInfos) {
  std::unique_lock<std::mutex> lock(mHubInfoMutex);
  if (mContextHubInfo == nullptr) {
    fbs::HubInfoResponseT response;
    flatbuffers::FlatBufferBuilder builder;
    HostProtocolHost::encodeHubInfoRequest(builder);
    if (mConnection->sendMessage(builder)) {
      mHubInfoCondition.wait_for(lock, kHubInfoQueryTimeout, [this]() {
        return mContextHubInfo != nullptr;
      });
    } else {
      LOGE("Failed to send a message to CHRE to get context hub info.");
    }
  }
  if (mContextHubInfo != nullptr) {
    contextHubInfos->push_back(*mContextHubInfo);
  } else {
    LOGE("Unable to get a valid context hub info for PID %d",
         AIBinder_getCallingPid());
  }
  return ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::loadNanoapp(
    int32_t contextHubId, const NanoappBinary &appBinary,
    int32_t transactionId) {
  if (!isValidContextHubId(contextHubId)) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  LOGI("Loading nanoapp 0x%" PRIx64, appBinary.nanoappId);
  uint32_t targetApiVersion = (appBinary.targetChreApiMajorVersion << 24) |
                              (appBinary.targetChreApiMinorVersion << 16);
  auto transaction = std::make_unique<FragmentedLoadTransaction>(
      transactionId, appBinary.nanoappId, appBinary.nanoappVersion,
      appBinary.flags, targetApiVersion, appBinary.customBinary,
      mConnection->getLoadFragmentSizeBytes());
  pid_t pid = AIBinder_getCallingPid();
  if (!mHalClientManager->registerPendingLoadTransaction(
          pid, std::move(transaction))) {
    return fromResult(false);
  }
  auto clientId = mHalClientManager->getClientId(pid);
  auto request = mHalClientManager->getNextFragmentedLoadRequest();

  if (request.has_value() &&
      sendFragmentedLoadRequest(clientId, request.value())) {
    mEventLogger.logNanoappLoad(appBinary, /* success= */ true);
    return ScopedAStatus::ok();
  }
  LOGE("Failed to send the first load request for nanoapp 0x%" PRIx64,
       appBinary.nanoappId);
  mHalClientManager->resetPendingLoadTransaction();
  // TODO(b/284481035): The result should be logged after the async response is
  //  received.
  mEventLogger.logNanoappLoad(appBinary, /* success= */ false);
  return fromResult(false);
}

bool MultiClientContextHubBase::sendFragmentedLoadRequest(
    HalClientId clientId, FragmentedLoadRequest &request) {
  flatbuffers::FlatBufferBuilder builder(128 + request.binary.size());
  HostProtocolHost::encodeFragmentedLoadNanoappRequest(
      builder, request, /* respondBeforeStart= */ false);
  HostProtocolHost::mutateHostClientId(builder.GetBufferPointer(),
                                       builder.GetSize(), clientId);
  return mConnection->sendMessage(builder);
}

ScopedAStatus MultiClientContextHubBase::unloadNanoapp(int32_t contextHubId,
                                                       int64_t appId,
                                                       int32_t transactionId) {
  if (!isValidContextHubId(contextHubId)) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  pid_t pid = AIBinder_getCallingPid();
  if (transactionId != kTestModeTransactionId &&
      !mHalClientManager->registerPendingUnloadTransaction(pid,
                                                           transactionId)) {
    return fromResult(false);
  }
  LOGI("Unloading nanoapp 0x%" PRIx64, appId);
  HalClientId clientId = mHalClientManager->getClientId(pid);
  flatbuffers::FlatBufferBuilder builder(64);
  HostProtocolHost::encodeUnloadNanoappRequest(
      builder, transactionId, appId, /* allowSystemNanoappUnload= */ false);
  HostProtocolHost::mutateHostClientId(builder.GetBufferPointer(),
                                       builder.GetSize(), clientId);

  bool result = mConnection->sendMessage(builder);
  if (!result) {
    mHalClientManager->resetPendingUnloadTransaction(clientId, transactionId);
  }
  // TODO(b/284481035): The result should be logged after the async response is
  //  received.
  mEventLogger.logNanoappUnload(appId, result);
  return fromResult(result);
}

ScopedAStatus MultiClientContextHubBase::disableNanoapp(
    int32_t /* contextHubId */, int64_t appId, int32_t /* transactionId */) {
  LOGW("Attempted to disable app ID 0x%016" PRIx64 ", but not supported",
       appId);
  return ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ScopedAStatus MultiClientContextHubBase::enableNanoapp(
    int32_t /* contextHubId */, int64_t appId, int32_t /* transactionId */) {
  LOGW("Attempted to enable app ID 0x%016" PRIx64 ", but not supported", appId);
  return ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ScopedAStatus MultiClientContextHubBase::onSettingChanged(Setting setting,
                                                          bool enabled) {
  mSettingEnabled[setting] = enabled;
  fbs::Setting fbsSetting;
  bool isWifiOrBtSetting =
      (setting == Setting::WIFI_MAIN || setting == Setting::WIFI_SCANNING ||
       setting == Setting::BT_MAIN || setting == Setting::BT_SCANNING);
  if (!isWifiOrBtSetting && getFbsSetting(setting, &fbsSetting)) {
    flatbuffers::FlatBufferBuilder builder(64);
    HostProtocolHost::encodeSettingChangeNotification(
        builder, fbsSetting, toFbsSettingState(enabled));
    mConnection->sendMessage(builder);
  }

  bool isWifiMainEnabled = isSettingEnabled(Setting::WIFI_MAIN);
  bool isWifiScanEnabled = isSettingEnabled(Setting::WIFI_SCANNING);
  bool isAirplaneModeEnabled = isSettingEnabled(Setting::AIRPLANE_MODE);

  // Because the airplane mode impact on WiFi is not standardized in Android,
  // we write a specific handling in the Context Hub HAL to inform CHRE.
  // The following definition is a default one, and can be adjusted
  // appropriately if necessary.
  bool isWifiAvailable = isAirplaneModeEnabled
                             ? (isWifiMainEnabled)
                             : (isWifiMainEnabled || isWifiScanEnabled);
  if (!mIsWifiAvailable.has_value() || (isWifiAvailable != mIsWifiAvailable)) {
    flatbuffers::FlatBufferBuilder builder(64);
    HostProtocolHost::encodeSettingChangeNotification(
        builder, fbs::Setting::WIFI_AVAILABLE,
        toFbsSettingState(isWifiAvailable));
    mConnection->sendMessage(builder);
    mIsWifiAvailable = isWifiAvailable;
  }

  // The BT switches determine whether we can BLE scan which is why things are
  // mapped like this into CHRE.
  bool isBtMainEnabled = isSettingEnabled(Setting::BT_MAIN);
  bool isBtScanEnabled = isSettingEnabled(Setting::BT_SCANNING);
  bool isBleAvailable = isBtMainEnabled || isBtScanEnabled;
  if (!mIsBleAvailable.has_value() || (isBleAvailable != mIsBleAvailable)) {
    flatbuffers::FlatBufferBuilder builder(64);
    HostProtocolHost::encodeSettingChangeNotification(
        builder, fbs::Setting::BLE_AVAILABLE,
        toFbsSettingState(isBleAvailable));
    mConnection->sendMessage(builder);
    mIsBleAvailable = isBleAvailable;
  }

  return ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::queryNanoapps(int32_t contextHubId) {
  if (!isValidContextHubId(contextHubId)) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  flatbuffers::FlatBufferBuilder builder(64);
  HostProtocolHost::encodeNanoappListRequest(builder);
  HostProtocolHost::mutateHostClientId(
      builder.GetBufferPointer(), builder.GetSize(),
      mHalClientManager->getClientId(AIBinder_getCallingPid()));
  return fromResult(mConnection->sendMessage(builder));
}

ScopedAStatus MultiClientContextHubBase::getPreloadedNanoappIds(
    int32_t contextHubId, std::vector<int64_t> *out_preloadedNanoappIds) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  if (out_preloadedNanoappIds == nullptr) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  std::unique_lock<std::mutex> lock(mPreloadedNanoappIdsMutex);
  if (!mPreloadedNanoappIds.has_value()) {
    mPreloadedNanoappIds = std::vector<uint64_t>{};
    mPreloadedNanoappLoader->getPreloadedNanoappIds(*mPreloadedNanoappIds);
  }
  for (const auto &nanoappId : mPreloadedNanoappIds.value()) {
    out_preloadedNanoappIds->emplace_back(static_cast<uint64_t>(nanoappId));
  }
  return ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::registerCallback(
    int32_t contextHubId,
    const std::shared_ptr<IContextHubCallback> &callback) {
  if (!isValidContextHubId(contextHubId)) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  if (callback == nullptr) {
    LOGE("Callback of context hub HAL must not be null");
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  pid_t pid = AIBinder_getCallingPid();
  auto *cookie = new HalDeathRecipientCookie(this, pid);
  if (AIBinder_linkToDeath(callback->asBinder().get(), mDeathRecipient.get(),
                           cookie) != STATUS_OK) {
    LOGE("Failed to link a client binder (pid=%d) to the death recipient", pid);
    delete cookie;
    return fromResult(false);
  }
  // If AIBinder_linkToDeath is successful the cookie will be released by the
  // callback of binder unlinking (callback overridden).
  if (!mHalClientManager->registerCallback(pid, callback, cookie)) {
    LOGE("Unable to register a client (pid=%d) callback", pid);
    return fromResult(false);
  }
  return ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::sendMessageToHub(
    int32_t contextHubId, const ContextHubMessage &message) {
  if (!isValidContextHubId(contextHubId)) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  HostEndpointId hostEndpointId = message.hostEndPoint;
  if (!mHalClientManager->mutateEndpointIdFromHostIfNeeded(
          AIBinder_getCallingPid(), hostEndpointId)) {
    return fromResult(false);
  }

  if (reliable_message_implementation() && message.isReliable) {
    mReliableMessageMap.insert({message.messageSequenceNumber, hostEndpointId});
  }

  flatbuffers::FlatBufferBuilder builder(1024);
  if (reliable_message_implementation()) {
    HostProtocolHost::encodeNanoappMessage(
        builder, message.nanoappId, message.messageType, hostEndpointId,
        message.messageBody.data(), message.messageBody.size(),
        /* permissions= */ 0,
        /* messagePermissions= */ 0,
        /* wokeHost= */ false, message.isReliable,
        message.messageSequenceNumber);
  } else {
    HostProtocolHost::encodeNanoappMessage(
        builder, message.nanoappId, message.messageType, hostEndpointId,
        message.messageBody.data(), message.messageBody.size());
  }

  bool success = mConnection->sendMessage(builder);
  mEventLogger.logMessageToNanoapp(message, success);
  return fromResult(success);
}

ScopedAStatus MultiClientContextHubBase::onHostEndpointConnected(
    const HostEndpointInfo &info) {
  uint8_t type;
  switch (info.type) {
    case HostEndpointInfo::Type::APP:
      type = CHRE_HOST_ENDPOINT_TYPE_APP;
      break;
    case HostEndpointInfo::Type::NATIVE:
      type = CHRE_HOST_ENDPOINT_TYPE_NATIVE;
      break;
    case HostEndpointInfo::Type::FRAMEWORK:
      type = CHRE_HOST_ENDPOINT_TYPE_FRAMEWORK;
      break;
    default:
      LOGE("Unsupported host endpoint type %" PRIu32, info.type);
      return fromServiceError(HalError::INVALID_ARGUMENT);
  }

  uint16_t endpointId = info.hostEndpointId;
  pid_t pid = AIBinder_getCallingPid();
  if (!mHalClientManager->registerEndpointId(pid, info.hostEndpointId) ||
      !mHalClientManager->mutateEndpointIdFromHostIfNeeded(pid, endpointId)) {
    return fromServiceError(HalError::INVALID_ARGUMENT);
  }
  flatbuffers::FlatBufferBuilder builder(64);
  HostProtocolHost::encodeHostEndpointConnected(
      builder, endpointId, type, info.packageName.value_or(std::string()),
      info.attributionTag.value_or(std::string()));
  return fromResult(mConnection->sendMessage(builder));
}

ScopedAStatus MultiClientContextHubBase::onHostEndpointDisconnected(
    char16_t in_hostEndpointId) {
  HostEndpointId hostEndpointId = in_hostEndpointId;
  pid_t pid = AIBinder_getCallingPid();
  bool isSuccessful = false;
  if (mHalClientManager->removeEndpointId(pid, hostEndpointId) &&
      mHalClientManager->mutateEndpointIdFromHostIfNeeded(pid,
                                                          hostEndpointId)) {
    flatbuffers::FlatBufferBuilder builder(64);
    HostProtocolHost::encodeHostEndpointDisconnected(builder, hostEndpointId);
    isSuccessful = mConnection->sendMessage(builder);
  }
  if (!isSuccessful) {
    LOGW("Unable to remove host endpoint id %" PRIu16, in_hostEndpointId);
  }
  return ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::onNanSessionStateChanged(
    const NanSessionStateUpdate & /*in_update*/) {
  // TODO(271471342): Add support for NAN session management.
  return ndk::ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::setTestMode(bool enable) {
  if (enable) {
    return fromResult(enableTestMode());
  }
  disableTestMode();
  return ScopedAStatus::ok();
}

ScopedAStatus MultiClientContextHubBase::sendMessageDeliveryStatusToHub(
    int32_t contextHubId, const MessageDeliveryStatus &messageDeliveryStatus) {
  if (!reliable_message_implementation()) {
    return ScopedAStatus::ok();
  }

  if (!isValidContextHubId(contextHubId)) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  flatbuffers::FlatBufferBuilder builder(64);
  HostProtocolHost::encodeMessageDeliveryStatus(
      builder, messageDeliveryStatus.messageSequenceNumber,
      toChreErrorCode(messageDeliveryStatus.errorCode));

  bool success = mConnection->sendMessage(builder);
  if (!success) {
    LOGE("Failed to send a message delivery status to CHRE");
  }
  return fromResult(success);
}

bool MultiClientContextHubBase::enableTestMode() {
  std::unique_lock<std::mutex> lock(mTestModeMutex);
  if (mIsTestModeEnabled) {
    return true;
  }

  // Pulling out a list of loaded nanoapps.
  mTestModeNanoapps.reset();
  if (!queryNanoapps(kDefaultHubId).isOk()) {
    LOGE("Failed to get a list of loaded nanoapps to enable test mode");
    mTestModeNanoapps.emplace();
    return false;
  }
  if (!mEnableTestModeCv.wait_for(lock, ktestModeTimeOut, [&]() {
        return mTestModeNanoapps.has_value() &&
               mTestModeSystemNanoapps.has_value();
      })) {
    LOGE("Failed to get a list of loaded nanoapps within %" PRIu64
         " seconds to enable test mode",
         ktestModeTimeOut.count());
    mTestModeNanoapps.emplace();
    return false;
  }

  // Unload each nanoapp.
  // mTestModeNanoapps tracks nanoapps that are actually unloaded. Removing an
  // element from std::vector is O(n) but such a removal should rarely happen.
  LOGI("Trying to unload %" PRIu64 " nanoapps to enable test mode",
       mTestModeNanoapps->size());
  for (auto iter = mTestModeNanoapps->begin();
       iter != mTestModeNanoapps->end();) {
    uint64_t appId = *iter;
    if (!unloadNanoapp(kDefaultHubId, appId, kTestModeTransactionId).isOk()) {
      LOGW("Failed to request to unload nanoapp 0x%" PRIx64
           " to enable test mode",
           appId);
      iter = mTestModeNanoapps->erase(iter);
      continue;
    }
    mTestModeSyncUnloadResult.reset();
    mEnableTestModeCv.wait_for(lock, ktestModeTimeOut, [&]() {
      return mTestModeSyncUnloadResult.has_value();
    });
    if (!*mTestModeSyncUnloadResult) {
      LOGW("Failed to unload nanoapp 0x%" PRIx64 " to enable test mode", appId);
      iter = mTestModeNanoapps->erase(iter);
      continue;
    }
    iter++;
  }
  LOGI("%" PRIu64 " nanoapps are unloaded to enable test mode",
       mTestModeNanoapps->size());

  mIsTestModeEnabled = true;
  mTestModeNanoapps.emplace();
  return true;
}

void MultiClientContextHubBase::disableTestMode() {
  std::unique_lock<std::mutex> lock(mTestModeMutex);
  if (!mIsTestModeEnabled) {
    return;
  }
  int numOfNanoappsLoaded =
      mPreloadedNanoappLoader->loadPreloadedNanoapps(mTestModeSystemNanoapps);
  LOGI("%d nanoapps are reloaded to recover from test mode",
       numOfNanoappsLoaded);
  mIsTestModeEnabled = false;
}

void MultiClientContextHubBase::handleMessageFromChre(
    const unsigned char *messageBuffer, size_t messageLen) {
  if (!::chre::HostProtocolCommon::verifyMessage(messageBuffer, messageLen)) {
    LOGE("Invalid message received from CHRE.");
    return;
  }
  std::unique_ptr<fbs::MessageContainerT> container =
      fbs::UnPackMessageContainer(messageBuffer);
  fbs::ChreMessageUnion &message = container->message;
  HalClientId clientId = container->host_addr->client_id();

  switch (container->message.type) {
    case fbs::ChreMessage::HubInfoResponse: {
      handleHubInfoResponse(*message.AsHubInfoResponse());
      break;
    }
    case fbs::ChreMessage::NanoappListResponse: {
      onNanoappListResponse(*message.AsNanoappListResponse(), clientId);
      break;
    }
    case fbs::ChreMessage::LoadNanoappResponse: {
      onNanoappLoadResponse(*message.AsLoadNanoappResponse(), clientId);
      break;
    }
    case fbs::ChreMessage::TimeSyncRequest: {
      if (mConnection->isTimeSyncNeeded()) {
        TimeSyncer::sendTimeSync(mConnection.get());
      } else {
        LOGW("Received an unexpected time sync request from CHRE.");
      }
      break;
    }
    case fbs::ChreMessage::UnloadNanoappResponse: {
      onNanoappUnloadResponse(*message.AsUnloadNanoappResponse(), clientId);
      break;
    }
    case fbs::ChreMessage::NanoappMessage: {
      onNanoappMessage(*message.AsNanoappMessage());
      break;
    }
    case fbs::ChreMessage::MessageDeliveryStatus: {
      onMessageDeliveryStatus(*message.AsMessageDeliveryStatus());
      break;
    }
    case fbs::ChreMessage::DebugDumpData: {
      onDebugDumpData(*message.AsDebugDumpData());
      break;
    }
    case fbs::ChreMessage::DebugDumpResponse: {
      onDebugDumpComplete(*message.AsDebugDumpResponse());
      break;
    }
    case fbs::ChreMessage::LogMessageV2: {
      const chre::fbs::LogMessageV2T *logMessage = message.AsLogMessageV2();
      const std::vector<int8_t> &buffer = logMessage->buffer;
      auto logData = reinterpret_cast<const uint8_t *>(buffer.data());
      uint32_t numLogsDropped = logMessage->num_logs_dropped;
      mLogger.logV2(logData, buffer.size(), numLogsDropped);
      break;
    }
    case fbs::ChreMessage::MetricLog: {
      onMetricLog(*message.AsMetricLog());
      break;
    }
    case fbs::ChreMessage::NanoappTokenDatabaseInfo: {
      // TODO(b/242760291): Map nanoapp log detokenizers to instance IDs in the
      //  log message parser.
      break;
    }
    default:
      LOGW("Got unexpected message type %" PRIu8,
           static_cast<uint8_t>(message.type));
  }
}

void MultiClientContextHubBase::handleHubInfoResponse(
    const fbs::HubInfoResponseT &response) {
  std::unique_lock<std::mutex> lock(mHubInfoMutex);
  mContextHubInfo = std::make_unique<ContextHubInfo>();
  mContextHubInfo->name = getStringFromByteVector(response.name);
  mContextHubInfo->vendor = getStringFromByteVector(response.vendor);
  mContextHubInfo->toolchain = getStringFromByteVector(response.toolchain);
  mContextHubInfo->id = kDefaultHubId;
  mContextHubInfo->peakMips = response.peak_mips;
  mContextHubInfo->maxSupportedMessageLengthBytes = response.max_msg_len;
  mContextHubInfo->chrePlatformId = response.platform_id;
  uint32_t version = response.chre_platform_version;
  mContextHubInfo->chreApiMajorVersion = extractChreApiMajorVersion(version);
  mContextHubInfo->chreApiMinorVersion = extractChreApiMinorVersion(version);
  mContextHubInfo->chrePatchVersion = extractChrePatchVersion(version);
  mContextHubInfo->supportedPermissions = kSupportedPermissions;

  mContextHubInfo->supportsReliableMessages =
      reliable_message_implementation() && response.supports_reliable_messages;

  mHubInfoCondition.notify_all();
}

void MultiClientContextHubBase::onDebugDumpData(
    const ::chre::fbs::DebugDumpDataT &data) {
  auto str = std::string(reinterpret_cast<const char *>(data.debug_str.data()),
                         data.debug_str.size());
  debugDumpAppend(str);
}

void MultiClientContextHubBase::onDebugDumpComplete(
    const ::chre::fbs::DebugDumpResponseT &response) {
  if (!response.success) {
    LOGE("Dumping debug information fails");
  }
  if (checkDebugFd()) {
    const std::string &dump = mEventLogger.dump();
    writeToDebugFile(dump.c_str());
    writeToDebugFile("\n-- End of CHRE/ASH debug info --\n");
  }
  debugDumpComplete();
}

void MultiClientContextHubBase::onNanoappListResponse(
    const fbs::NanoappListResponseT &response, HalClientId clientId) {
  {
    std::unique_lock<std::mutex> lock(mTestModeMutex);
    if (!mTestModeNanoapps.has_value()) {
      mTestModeNanoapps.emplace();
      mTestModeSystemNanoapps.emplace();
      for (const auto &nanoapp : response.nanoapps) {
        if (nanoapp->is_system) {
          mTestModeSystemNanoapps->push_back(nanoapp->app_id);
        } else {
          mTestModeNanoapps->push_back(nanoapp->app_id);
        }
      }
      mEnableTestModeCv.notify_all();
    }
  }

  std::shared_ptr<IContextHubCallback> callback =
      mHalClientManager->getCallback(clientId);
  if (callback == nullptr) {
    return;
  }

  std::vector<NanoappInfo> appInfoList;
  for (const auto &nanoapp : response.nanoapps) {
    if (nanoapp->is_system) {
      continue;
    }
    NanoappInfo appInfo;
    appInfo.nanoappId = nanoapp->app_id;
    appInfo.nanoappVersion = nanoapp->version;
    appInfo.enabled = nanoapp->enabled;
    appInfo.permissions = chreToAndroidPermissions(nanoapp->permissions);

    std::vector<NanoappRpcService> rpcServices;
    for (const auto &service : nanoapp->rpc_services) {
      NanoappRpcService aidlService;
      aidlService.id = service->id;
      aidlService.version = service->version;
      rpcServices.emplace_back(aidlService);
    }
    appInfo.rpcServices = rpcServices;
    appInfoList.push_back(appInfo);
  }

  callback->handleNanoappInfo(appInfoList);
}

void MultiClientContextHubBase::onNanoappLoadResponse(
    const fbs::LoadNanoappResponseT &response, HalClientId clientId) {
  LOGV("Received nanoapp load response for client %" PRIu16
       " transaction %" PRIu32 " fragment %" PRIu32,
       clientId, response.transaction_id, response.fragment_id);
  if (mPreloadedNanoappLoader->isPreloadOngoing()) {
    mPreloadedNanoappLoader->onLoadNanoappResponse(response, clientId);
    return;
  }
  if (!mHalClientManager->isPendingLoadTransactionExpected(
          clientId, response.transaction_id, response.fragment_id)) {
    LOGW("Received a response for client %" PRIu16 " transaction %" PRIu32
         " fragment %" PRIu32
         " that doesn't match the existing transaction. Skipped.",
         clientId, response.transaction_id, response.fragment_id);
    return;
  }
  if (response.success) {
    auto nextFragmentedRequest =
        mHalClientManager->getNextFragmentedLoadRequest();
    if (nextFragmentedRequest.has_value()) {
      // nextFragmentedRequest will only have a value if the pending transaction
      // matches the response and there are more fragments to send. Hold off on
      // calling the callback in this case.
      LOGV("Sending next FragmentedLoadRequest for client %" PRIu16
           ": (transaction: %" PRIu32 ", fragment %zu)",
           clientId, nextFragmentedRequest->transactionId,
           nextFragmentedRequest->fragmentId);
      sendFragmentedLoadRequest(clientId, nextFragmentedRequest.value());
      return;
    }
  } else {
    LOGE("Loading nanoapp fragment for client %" PRIu16 " transaction %" PRIu32
         " fragment %" PRIu32 " failed",
         clientId, response.transaction_id, response.fragment_id);
    mHalClientManager->resetPendingLoadTransaction();
  }
  // At this moment the current pending transaction should either have no more
  // fragment to send or the response indicates its last nanoapp fragment fails
  // to get loaded.
  if (auto callback = mHalClientManager->getCallback(clientId);
      callback != nullptr) {
    callback->handleTransactionResult(response.transaction_id,
                                      /* in_success= */ response.success);
  }
}

void MultiClientContextHubBase::onNanoappUnloadResponse(
    const fbs::UnloadNanoappResponseT &response, HalClientId clientId) {
  if (response.transaction_id == kTestModeTransactionId) {
    std::unique_lock<std::mutex> lock(mTestModeMutex);
    mTestModeSyncUnloadResult.emplace(response.success);
    mEnableTestModeCv.notify_all();
    return;
  }
  if (mHalClientManager->resetPendingUnloadTransaction(
          clientId, response.transaction_id)) {
    if (auto callback = mHalClientManager->getCallback(clientId);
        callback != nullptr) {
      callback->handleTransactionResult(response.transaction_id,
                                        /* in_success= */ response.success);
    }
  }
}

void MultiClientContextHubBase::onNanoappMessage(
    const ::chre::fbs::NanoappMessageT &message) {
  mEventLogger.logMessageFromNanoapp(message);
  ContextHubMessage outMessage;
  outMessage.nanoappId = message.app_id;
  outMessage.hostEndPoint = message.host_endpoint;
  outMessage.messageType = message.message_type;
  outMessage.messageBody = message.message;
  outMessage.permissions = chreToAndroidPermissions(message.permissions);

  if (reliable_message_implementation()) {
    outMessage.isReliable = message.is_reliable;
    outMessage.messageSequenceNumber = message.message_sequence_number;
  } else {
    outMessage.isReliable = false;
    outMessage.messageSequenceNumber = 0;
  }

  auto messageContentPerms =
      chreToAndroidPermissions(message.message_permissions);
  // broadcast message is sent to every connected endpoint
  if (message.host_endpoint == CHRE_HOST_ENDPOINT_BROADCAST) {
    mHalClientManager->sendMessageForAllCallbacks(outMessage,
                                                  messageContentPerms);
  } else if (auto callback = mHalClientManager->getCallbackForEndpoint(
                 message.host_endpoint);
             callback != nullptr) {
    outMessage.hostEndPoint =
        HalClientManager::convertToOriginalEndpointId(message.host_endpoint);
    callback->handleContextHubMessage(outMessage, messageContentPerms);
  }

  if (isMetricEnabled() && message.woke_host) {
    mMetricsReporter.logApWakeupOccurred(message.app_id);
  }
}

void MultiClientContextHubBase::onMessageDeliveryStatus(
    const ::chre::fbs::MessageDeliveryStatusT &status) {
  if (!reliable_message_implementation()) {
    return;
  }

  auto hostEndpointIdIter =
      mReliableMessageMap.find(status.message_sequence_number);
  if (hostEndpointIdIter == mReliableMessageMap.end()) {
    LOGE(
        "Unable to get the host endpoint ID for message sequence "
        "number: %" PRIu32,
        status.message_sequence_number);
    return;
  }

  HostEndpointId hostEndpointId = hostEndpointIdIter->second;
  mReliableMessageMap.erase(hostEndpointIdIter);
  std::shared_ptr<IContextHubCallback> callback =
      mHalClientManager->getCallbackForEndpoint(hostEndpointId);
  if (callback == nullptr) {
    LOGE("Could not get callback for host endpoint: %" PRIu16, hostEndpointId);
    return;
  }
  hostEndpointId =
      HalClientManager::convertToOriginalEndpointId(hostEndpointId);

  MessageDeliveryStatus outStatus;
  outStatus.messageSequenceNumber = status.message_sequence_number;
  outStatus.errorCode = toErrorCode(status.error_code);
  callback->handleMessageDeliveryStatus(hostEndpointId, outStatus);
}

void MultiClientContextHubBase::onClientDied(void *cookie) {
  auto *info = static_cast<HalDeathRecipientCookie *>(cookie);
  info->hal->handleClientDeath(info->clientPid);
}

void MultiClientContextHubBase::handleClientDeath(pid_t clientPid) {
  LOGI("Process %d is dead. Cleaning up.", clientPid);
  if (auto endpoints = mHalClientManager->getAllConnectedEndpoints(clientPid)) {
    for (auto endpointId : *endpoints) {
      LOGI("Sending message to remove endpoint 0x%" PRIx16, endpointId);
      if (!mHalClientManager->mutateEndpointIdFromHostIfNeeded(clientPid,
                                                               endpointId)) {
        continue;
      }
      flatbuffers::FlatBufferBuilder builder(64);
      HostProtocolHost::encodeHostEndpointDisconnected(builder, endpointId);
      mConnection->sendMessage(builder);
    }
  }
  mHalClientManager->handleClientDeath(clientPid);
}

void MultiClientContextHubBase::onChreRestarted() {
  mIsWifiAvailable.reset();
  mEventLogger.logContextHubRestart();
  mHalClientManager->handleChreRestart();
}

binder_status_t MultiClientContextHubBase::dump(int fd,
                                                const char ** /* args */,
                                                uint32_t /* numArgs */) {
  // Dump of CHRE debug data. It waits for the dump to finish before returning.
  debugDumpStart(fd);

  // Dump debug info of HalClientManager.
  std::string dumpOfHalClientManager = mHalClientManager->debugDump();
  if (!WriteStringToFd(dumpOfHalClientManager, fd)) {
    LOGW("Failed to write debug dump of HalClientManager. Size: %zu.",
         dumpOfHalClientManager.size());
  }

  // Dump the status of test mode
  std::ostringstream testModeDump;
  testModeDump << "\n-- HAL Test Mode Status --\n\n";
  {
    std::lock_guard<std::mutex> lockGuard(mTestModeMutex);
    testModeDump << (mIsTestModeEnabled ? "Enabled" : "Disabled") << "\n";
    if (!mTestModeNanoapps.has_value()) {
      testModeDump << "\nError: Nanoapp list is left unset\n";
    }
  }
  testModeDump << "\n-- End of HAL Test Mode Status --\n";
  if (!WriteStringToFd(testModeDump.str(), fd)) {
    LOGW("Failed to write test mode dump");
  }

  return STATUS_OK;
}

bool MultiClientContextHubBase::requestDebugDump() {
  flatbuffers::FlatBufferBuilder builder;
  HostProtocolHost::encodeDebugDumpRequest(builder);
  return mConnection->sendMessage(builder);
}

void MultiClientContextHubBase::writeToDebugFile(const char *str) {
  if (!WriteStringToFd(std::string(str), getDebugFd())) {
    LOGW("Failed to write %zu bytes to debug dump fd", strlen(str));
  }
}

void MultiClientContextHubBase::onMetricLog(
    const ::chre::fbs::MetricLogT &metricMessage) {
  if (!isMetricEnabled()) {
    return;
  }

  using ::android::chre::Atoms::ChrePalOpenFailed;

  const std::vector<int8_t> &encodedMetric = metricMessage.encoded_metric;
  auto metricSize = static_cast<int>(encodedMetric.size());

  switch (metricMessage.id) {
    case Atoms::CHRE_PAL_OPEN_FAILED: {
      metrics::ChrePalOpenFailed metric;
      if (!metric.ParseFromArray(encodedMetric.data(), metricSize)) {
        break;
      }
      auto pal = static_cast<ChrePalOpenFailed::ChrePalType>(metric.pal());
      auto type = static_cast<ChrePalOpenFailed::Type>(metric.type());
      if (!mMetricsReporter.logPalOpenFailed(pal, type)) {
        LOGE("Could not log the PAL open failed metric");
      }
      return;
    }
    case Atoms::CHRE_EVENT_QUEUE_SNAPSHOT_REPORTED: {
      metrics::ChreEventQueueSnapshotReported metric;
      if (!metric.ParseFromArray(encodedMetric.data(), metricSize)) {
        break;
      }
      if (!mMetricsReporter.logEventQueueSnapshotReported(
              metric.snapshot_chre_get_time_ms(), metric.max_event_queue_size(),
              metric.mean_event_queue_size(), metric.num_dropped_events())) {
        LOGE("Could not log the event queue snapshot metric");
      }
      return;
    }
    default: {
      LOGW("Unknown metric ID %" PRIu32, metricMessage.id);
      return;
    }
  }
  // Reached here only if an error has occurred for a known metric id.
  LOGE("Failed to parse metric data with id %" PRIu32, metricMessage.id);
}
}  // namespace android::hardware::contexthub::common::implementation
