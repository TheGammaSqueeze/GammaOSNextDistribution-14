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

#include "generic_context_hub_aidl.h"

#include "chre_api/chre/event.h"
#include "chre_host/config_util.h"
#include "chre_host/file_stream.h"
#include "chre_host/fragmented_load_transaction.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/log.h"
#include "chre_host/napp_header.h"
#include "permissions_util.h"

#include <algorithm>
#include <chrono>
#include <limits>

namespace aidl::android::hardware::contexthub {

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

using ::android::chre::FragmentedLoadTransaction;
using ::android::chre::getPreloadedNanoappsFromConfigFile;
using ::android::chre::getStringFromByteVector;
using ::android::chre::NanoAppBinaryHeader;
using ::android::chre::readFileContents;
using ::android::hardware::contexthub::common::implementation::
    chreToAndroidPermissions;
using ::android::hardware::contexthub::common::implementation::
    kSupportedPermissions;
using ::ndk::ScopedAStatus;

namespace {
constexpr uint32_t kDefaultHubId = 0;
constexpr char kPreloadedNanoappsConfigPath[] =
    "/vendor/etc/chre/preloaded_nanoapps.json";
constexpr std::chrono::duration kTestModeTimeout = std::chrono::seconds(10);
constexpr uint16_t kMaxValidHostEndPointId = 0x7fff;

/*
 * The starting transaction ID for internal transactions. We choose
 * the limit + 1 here as any client will only pass non-negative values up to the
 * limit. The socket connection to CHRE accepts a uint32_t for the transaction
 * ID, so we can use the value below up to std::numeric_limits<uint32_t>::max()
 * for internal transaction IDs.
 */
constexpr int32_t kStartingInternalTransactionId = 0x80000000;

inline constexpr int8_t extractChreApiMajorVersion(uint32_t chreVersion) {
  return static_cast<int8_t>(chreVersion >> 24);
}

inline constexpr int8_t extractChreApiMinorVersion(uint32_t chreVersion) {
  return static_cast<int8_t>(chreVersion >> 16);
}

inline constexpr uint16_t extractChrePatchVersion(uint32_t chreVersion) {
  return static_cast<uint16_t>(chreVersion);
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

ScopedAStatus toServiceSpecificError(bool success) {
  return success ? ScopedAStatus::ok()
                 : ScopedAStatus::fromServiceSpecificError(
                       BnContextHub::EX_CONTEXT_HUB_UNSPECIFIED);
}

}  // anonymous namespace

ScopedAStatus ContextHub::getContextHubs(
    std::vector<ContextHubInfo> *out_contextHubInfos) {
  ::chre::fbs::HubInfoResponseT response;
  bool success = mConnection.getContextHubs(&response);
  if (success) {
    ContextHubInfo hub;
    hub.name = getStringFromByteVector(response.name);
    hub.vendor = getStringFromByteVector(response.vendor);
    hub.toolchain = getStringFromByteVector(response.toolchain);
    hub.id = kDefaultHubId;
    hub.peakMips = response.peak_mips;
    hub.maxSupportedMessageLengthBytes = response.max_msg_len;
    hub.chrePlatformId = response.platform_id;

    uint32_t version = response.chre_platform_version;
    hub.chreApiMajorVersion = extractChreApiMajorVersion(version);
    hub.chreApiMinorVersion = extractChreApiMinorVersion(version);
    hub.chrePatchVersion = extractChrePatchVersion(version);

    hub.supportedPermissions = kSupportedPermissions;

    hub.supportsReliableMessages = false;

    out_contextHubInfos->push_back(hub);
  }

  return ndk::ScopedAStatus::ok();
}

ScopedAStatus ContextHub::loadNanoapp(int32_t contextHubId,
                                      const NanoappBinary &appBinary,
                                      int32_t transactionId) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  std::lock_guard<std::mutex> lock(mTestModeMutex);
  bool success = loadNanoappInternal(appBinary, transactionId);
  return toServiceSpecificError(success);
}

ScopedAStatus ContextHub::unloadNanoapp(int32_t contextHubId, int64_t appId,
                                        int32_t transactionId) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  std::lock_guard<std::mutex> lock(mTestModeMutex);
  bool success = unloadNanoappInternal(appId, transactionId);
  return toServiceSpecificError(success);
}

ScopedAStatus ContextHub::disableNanoapp(int32_t /* contextHubId */,
                                         int64_t appId,
                                         int32_t /* transactionId */) {
  LOGW("Attempted to disable app ID 0x%016" PRIx64 ", but not supported",
       appId);
  return ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ScopedAStatus ContextHub::enableNanoapp(int32_t /* contextHubId */,
                                        int64_t appId,
                                        int32_t /* transactionId */) {
  LOGW("Attempted to enable app ID 0x%016" PRIx64 ", but not supported", appId);
  return ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ScopedAStatus ContextHub::onSettingChanged(Setting setting, bool enabled) {
  mSettingEnabled[setting] = enabled;
  fbs::Setting fbsSetting;
  bool isWifiOrBtSetting =
      (setting == Setting::WIFI_MAIN || setting == Setting::WIFI_SCANNING ||
       setting == Setting::BT_MAIN || setting == Setting::BT_SCANNING);

  if (!isWifiOrBtSetting && getFbsSetting(setting, &fbsSetting)) {
    mConnection.sendSettingChangedNotification(fbsSetting,
                                               toFbsSettingState(enabled));
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
    mConnection.sendSettingChangedNotification(
        fbs::Setting::WIFI_AVAILABLE, toFbsSettingState(isWifiAvailable));
    mIsWifiAvailable = isWifiAvailable;
  }

  // The BT switches determine whether we can BLE scan which is why things are
  // mapped like this into CHRE.
  bool isBtMainEnabled = isSettingEnabled(Setting::BT_MAIN);
  bool isBtScanEnabled = isSettingEnabled(Setting::BT_SCANNING);
  bool isBleAvailable = isBtMainEnabled || isBtScanEnabled;
  if (!mIsBleAvailable.has_value() || (isBleAvailable != mIsBleAvailable)) {
    mConnection.sendSettingChangedNotification(
        fbs::Setting::BLE_AVAILABLE, toFbsSettingState(isBleAvailable));
    mIsBleAvailable = isBleAvailable;
  }

  return ndk::ScopedAStatus::ok();
}

ScopedAStatus ContextHub::queryNanoapps(int32_t contextHubId) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  return toServiceSpecificError(mConnection.queryNanoapps());
}

::ndk::ScopedAStatus ContextHub::getPreloadedNanoappIds(
    int32_t contextHubId, std::vector<int64_t> *out_preloadedNanoappIds) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  if (out_preloadedNanoappIds == nullptr) {
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  std::unique_lock<std::mutex> lock(mPreloadedNanoappIdsMutex);
  if (mPreloadedNanoappIds.has_value()) {
    *out_preloadedNanoappIds = *mPreloadedNanoappIds;
    return ScopedAStatus::ok();
  }

  std::vector<chrePreloadedNanoappInfo> preloadedNanoapps;
  if (!getPreloadedNanoappIdsFromConfigFile(preloadedNanoapps, nullptr)) {
    return ScopedAStatus::fromExceptionCode(EX_SERVICE_SPECIFIC);
  }

  mPreloadedNanoappIds = std::vector<int64_t>();
  for (const auto &preloadedNanoapp : preloadedNanoapps) {
    mPreloadedNanoappIds->push_back(preloadedNanoapp.id);
    out_preloadedNanoappIds->push_back(preloadedNanoapp.id);
  }

  return ScopedAStatus::ok();
}

ScopedAStatus ContextHub::registerCallback(
    int32_t contextHubId, const std::shared_ptr<IContextHubCallback> &cb) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  std::lock_guard<std::mutex> lock(mCallbackMutex);
  if (mCallback != nullptr) {
    binder_status_t binder_status = AIBinder_unlinkToDeath(
        mCallback->asBinder().get(), mDeathRecipient.get(), this);
    if (binder_status != STATUS_OK) {
      LOGE("Failed to unlink to death");
    }
  }
  mCallback = cb;
  if (cb != nullptr) {
    binder_status_t binder_status =
        AIBinder_linkToDeath(cb->asBinder().get(), mDeathRecipient.get(), this);
    if (binder_status != STATUS_OK) {
      LOGE("Failed to link to death");
    }
  }
  return ScopedAStatus::ok();
}

ScopedAStatus ContextHub::sendMessageToHub(int32_t contextHubId,
                                           const ContextHubMessage &message) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  bool success = mConnection.sendMessageToHub(
      message.nanoappId, message.messageType, message.hostEndPoint,
      message.messageBody.data(), message.messageBody.size());
  mEventLogger.logMessageToNanoapp(message, success);

  return toServiceSpecificError(success);
}

ScopedAStatus ContextHub::setTestMode(bool enable) {
  return enable ? enableTestMode() : disableTestMode();
}

ScopedAStatus ContextHub::sendMessageDeliveryStatusToHub(
    int32_t /* contextHubId */,
    const MessageDeliveryStatus & /* messageDeliveryStatus */) {
  return ndk::ScopedAStatus::ok();
}

ScopedAStatus ContextHub::onHostEndpointConnected(
    const HostEndpointInfo &in_info) {
  std::lock_guard<std::mutex> lock(mConnectedHostEndpointsMutex);
  uint8_t type;
  switch (in_info.type) {
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
      LOGE("Unsupported host endpoint type %" PRIu32, type);
      return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  mConnectedHostEndpoints.insert(in_info.hostEndpointId);
  mConnection.onHostEndpointConnected(
      in_info.hostEndpointId, type, in_info.packageName.value_or(std::string()),
      in_info.attributionTag.value_or(std::string()));
  return ndk::ScopedAStatus::ok();
}

ScopedAStatus ContextHub::onHostEndpointDisconnected(
    char16_t in_hostEndpointId) {
  std::lock_guard<std::mutex> lock(mConnectedHostEndpointsMutex);
  if (mConnectedHostEndpoints.count(in_hostEndpointId) > 0) {
    mConnectedHostEndpoints.erase(in_hostEndpointId);

    mConnection.onHostEndpointDisconnected(in_hostEndpointId);
  } else {
    LOGE("Unknown host endpoint disconnected (ID: %" PRIu16 ")",
         in_hostEndpointId);
  }

  return ndk::ScopedAStatus::ok();
}

ScopedAStatus ContextHub::onNanSessionStateChanged(
    const NanSessionStateUpdate & /*in_update*/) {
  // TODO(271471342): Add support for NAN session management.
  return ndk::ScopedAStatus::ok();
}

void ContextHub::onNanoappMessage(const ::chre::fbs::NanoappMessageT &message) {
  std::lock_guard<std::mutex> lock(mCallbackMutex);
  if (mCallback != nullptr) {
    if (message.host_endpoint > kMaxValidHostEndPointId &&
        message.host_endpoint != CHRE_HOST_ENDPOINT_BROADCAST) {
      return;
    }

    mEventLogger.logMessageFromNanoapp(message);
    ContextHubMessage outMessage;
    outMessage.nanoappId = message.app_id;
    outMessage.hostEndPoint = message.host_endpoint;
    outMessage.messageType = message.message_type;
    outMessage.messageBody = message.message;
    outMessage.permissions = chreToAndroidPermissions(message.permissions);

    std::vector<std::string> messageContentPerms =
        chreToAndroidPermissions(message.message_permissions);
    mCallback->handleContextHubMessage(outMessage, messageContentPerms);
  }
}

void ContextHub::onNanoappListResponse(
    const ::chre::fbs::NanoappListResponseT &response) {
  std::vector<NanoappInfo> appInfoList;
  for (const std::unique_ptr<::chre::fbs::NanoappListEntryT> &nanoapp :
       response.nanoapps) {
    // TODO(b/245202050): determine if this is really required, and if so, have
    // HostProtocolHost strip out null entries as part of decode
    if (nanoapp == nullptr) {
      continue;
    }

    LOGV("App 0x%016" PRIx64 " ver 0x%" PRIx32 " permissions 0x%" PRIx32
         " enabled %d system %d",
         nanoapp->app_id, nanoapp->version, nanoapp->permissions,
         nanoapp->enabled, nanoapp->is_system);
    if (!nanoapp->is_system) {
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
  }

  {
    std::lock_guard<std::mutex> lock(mQueryNanoappsInternalMutex);
    if (!mQueryNanoappsInternalList) {
      mQueryNanoappsInternalList = appInfoList;
      mQueryNanoappsInternalCondVar.notify_all();
    }
  }

  std::lock_guard<std::mutex> lock(mCallbackMutex);
  if (mCallback != nullptr) {
    mCallback->handleNanoappInfo(appInfoList);
  }
}

void ContextHub::onTransactionResult(uint32_t transactionId, bool success) {
  std::unique_lock<std::mutex> lock(mSynchronousLoadUnloadMutex);
  if (mSynchronousLoadUnloadTransactionId &&
      transactionId == *mSynchronousLoadUnloadTransactionId) {
    mSynchronousLoadUnloadSuccess = success;
    mSynchronousLoadUnloadCondVar.notify_all();
  } else {
    std::lock_guard<std::mutex> lock(mCallbackMutex);
    if (mCallback != nullptr) {
      mCallback->handleTransactionResult(transactionId, success);
    }
  }
}

void ContextHub::onContextHubRestarted() {
  std::lock_guard<std::mutex> lock(mCallbackMutex);
  mIsWifiAvailable.reset();
  {
    std::lock_guard<std::mutex> endpointLock(mConnectedHostEndpointsMutex);
    mConnectedHostEndpoints.clear();
    mEventLogger.logContextHubRestart();
  }
  if (mCallback != nullptr) {
    mCallback->handleContextHubAsyncEvent(AsyncEventType::RESTARTED);
  }
}

void ContextHub::onDebugDumpData(const ::chre::fbs::DebugDumpDataT &data) {
  auto str = std::string(reinterpret_cast<const char *>(data.debug_str.data()),
                         data.debug_str.size());
  debugDumpAppend(str);
}

void ContextHub::onDebugDumpComplete(
    const ::chre::fbs::DebugDumpResponseT & /* response */) {
  debugDumpComplete();
}

void ContextHub::handleServiceDeath() {
  LOGI("Context Hub Service died ...");
  {
    std::lock_guard<std::mutex> lock(mCallbackMutex);
    mCallback.reset();
  }
  {
    std::lock_guard<std::mutex> lock(mConnectedHostEndpointsMutex);
    mConnectedHostEndpoints.clear();
  }
}

void ContextHub::onServiceDied(void *cookie) {
  auto *contexthub = static_cast<ContextHub *>(cookie);
  contexthub->handleServiceDeath();
}

binder_status_t ContextHub::dump(int fd, const char ** /* args */,
                                 uint32_t /* numArgs */) {
  debugDumpStart(fd);
  debugDumpFinish();
  return STATUS_OK;
}

void ContextHub::debugDumpFinish() {
  if (checkDebugFd()) {
    const std::string &dump = mEventLogger.dump();
    writeToDebugFile(dump.c_str());
    writeToDebugFile("\n-- End of CHRE/ASH debug info --\n");
    invalidateDebugFd();
  }
}

void ContextHub::writeToDebugFile(const char *str) {
  if (!::android::base::WriteStringToFd(std::string(str), getDebugFd())) {
    LOGW("Failed to write %zu bytes to debug dump fd", strlen(str));
  }
}

ScopedAStatus ContextHub::enableTestMode() {
  std::unique_lock<std::mutex> lock(mTestModeMutex);

  bool success = false;
  std::vector<int64_t> loadedNanoappIds;
  std::vector<int64_t> preloadedNanoappIds;
  std::vector<int64_t> nanoappIdsToUnload;
  if (mIsTestModeEnabled) {
    success = true;
  } else if (mConnection.isLoadTransactionPending()) {
    /**
     * There is already a pending load transaction. We cannot change the test
     * mode state if there is a pending load transaction. We do not consider
     * pending unload transactions as they can happen asynchronously and
     * multiple at a time.
     */
    LOGE("There exists a pending load transaction. Cannot enable test mode.");
  } else if (!queryNanoappsInternal(kDefaultHubId, &loadedNanoappIds)) {
    LOGE("Could not query nanoapps to enable test mode.");
  } else if (!getPreloadedNanoappIds(kDefaultHubId, &preloadedNanoappIds)
                  .isOk()) {
    LOGE("Unable to get preloaded nanoapp IDs from the config file.");
  } else {
    std::sort(loadedNanoappIds.begin(), loadedNanoappIds.end());
    std::sort(preloadedNanoappIds.begin(), preloadedNanoappIds.end());

    // Calculate the system nanoapp IDs. They are preloaded, but not loaded.
    mSystemNanoappIds.clear();
    std::set_difference(preloadedNanoappIds.begin(), preloadedNanoappIds.end(),
                        loadedNanoappIds.begin(), loadedNanoappIds.end(),
                        std::back_inserter(mSystemNanoappIds));

    /*
     * Unload all preloaded and loaded nanoapps (set intersection).
     * Both vectors need to be sorted for std::set_intersection to work.
     * We explicitly choose not to use std::set here to avoid the
     * copying cost as well as the tree balancing cost for the
     * red-black tree.
     */
    std::set_intersection(loadedNanoappIds.begin(), loadedNanoappIds.end(),
                          preloadedNanoappIds.begin(),
                          preloadedNanoappIds.end(),
                          std::back_inserter(nanoappIdsToUnload));
    if (!unloadNanoappsInternal(kDefaultHubId, nanoappIdsToUnload)) {
      LOGE("Unable to unload all loaded and preloaded nanoapps.");
    }
    success = true;
  }

  if (success) {
    mIsTestModeEnabled = true;
    LOGI("Successfully enabled test mode.");
    return ScopedAStatus::ok();
  } else {
    return ScopedAStatus::fromExceptionCode(EX_SERVICE_SPECIFIC);
  }
}

ScopedAStatus ContextHub::disableTestMode() {
  std::unique_lock<std::mutex> lock(mTestModeMutex);

  bool success = false;
  std::vector<chrePreloadedNanoappInfo> preloadedNanoapps;
  std::string preloadedNanoappDirectory;
  if (!mIsTestModeEnabled) {
    success = true;
  } else if (mConnection.isLoadTransactionPending()) {
    /**
     * There is already a pending load transaction. We cannot change the test
     * mode state if there is a pending load transaction. We do not consider
     * pending unload transactions as they can happen asynchronously and
     * multiple at a time.
     */
    LOGE("There exists a pending load transaction. Cannot disable test mode.");
  } else if (!getPreloadedNanoappIdsFromConfigFile(
                 preloadedNanoapps, &preloadedNanoappDirectory)) {
    LOGE("Unable to get preloaded nanoapp IDs from the config file.");
  } else {
    std::vector<NanoappBinary> nanoappsToLoad = selectPreloadedNanoappsToLoad(
        preloadedNanoapps, preloadedNanoappDirectory);

    if (!loadNanoappsInternal(kDefaultHubId, nanoappsToLoad)) {
      LOGE("Unable to load all preloaded, non-system nanoapps.");
    }
    // Any attempt to load non-test nanoapps should disable test mode, even if
    // not all nanoapps are successfully loaded.
    success = true;
  }

  if (success) {
    mIsTestModeEnabled = false;
    LOGI("Successfully disabled test mode.");
    return ScopedAStatus::ok();
  } else {
    return ScopedAStatus::fromExceptionCode(EX_SERVICE_SPECIFIC);
  }
}

bool ContextHub::queryNanoappsInternal(int32_t contextHubId,
                                       std::vector<int64_t> *nanoappIdList) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return false;
  }

  std::unique_lock<std::mutex> lock(mQueryNanoappsInternalMutex);
  mQueryNanoappsInternalList.reset();

  bool success =
      queryNanoapps(contextHubId).isOk() &&
      mQueryNanoappsInternalCondVar.wait_for(lock, kTestModeTimeout, [this]() {
        return mQueryNanoappsInternalList.has_value();
      });
  if (success && nanoappIdList != nullptr) {
    std::transform(
        mQueryNanoappsInternalList->begin(), mQueryNanoappsInternalList->end(),
        std::back_inserter(*nanoappIdList),
        [](const NanoappInfo &nanoapp) { return nanoapp.nanoappId; });
  }
  return success;
}

bool ContextHub::loadNanoappInternal(const NanoappBinary &appBinary,
                                     int32_t transactionId) {
  uint32_t targetApiVersion = (appBinary.targetChreApiMajorVersion << 24) |
                              (appBinary.targetChreApiMinorVersion << 16);
  FragmentedLoadTransaction transaction(
      transactionId, appBinary.nanoappId, appBinary.nanoappVersion,
      appBinary.flags, targetApiVersion, appBinary.customBinary);
  bool success = mConnection.loadNanoapp(transaction);
  mEventLogger.logNanoappLoad(appBinary, success);
  return success;
}

bool ContextHub::loadNanoappsInternal(
    int32_t contextHubId, const std::vector<NanoappBinary> &nanoappBinaryList) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return false;
  }

  std::unique_lock<std::mutex> lock(mSynchronousLoadUnloadMutex);
  mSynchronousLoadUnloadTransactionId = kStartingInternalTransactionId;

  for (const NanoappBinary &nanoappToLoad : nanoappBinaryList) {
    LOGI("Loading nanoapp with ID: 0x%016" PRIx64, nanoappToLoad.nanoappId);

    bool success = false;
    if (!loadNanoappInternal(nanoappToLoad,
                             *mSynchronousLoadUnloadTransactionId)) {
      LOGE("Failed to request loading nanoapp with ID 0x%" PRIx64,
           nanoappToLoad.nanoappId);
    } else {
      mSynchronousLoadUnloadSuccess.reset();
      mSynchronousLoadUnloadCondVar.wait_for(lock, kTestModeTimeout, [this]() {
        return mSynchronousLoadUnloadSuccess.has_value();
      });
      if (mSynchronousLoadUnloadSuccess.has_value() &&
          *mSynchronousLoadUnloadSuccess) {
        LOGI("Successfully loaded nanoapp with ID: 0x%016" PRIx64,
             nanoappToLoad.nanoappId);
        success = true;
      }
    }

    if (!success) {
      LOGE("Failed to load nanoapp with ID 0x%" PRIx64,
           nanoappToLoad.nanoappId);
    }
    ++(*mSynchronousLoadUnloadTransactionId);
  }

  return true;
}

bool ContextHub::unloadNanoappInternal(int64_t appId, int32_t transactionId) {
  bool success = mConnection.unloadNanoapp(appId, transactionId);
  mEventLogger.logNanoappUnload(appId, success);
  return success;
}

bool ContextHub::unloadNanoappsInternal(
    int32_t contextHubId, const std::vector<int64_t> &nanoappIdList) {
  if (contextHubId != kDefaultHubId) {
    LOGE("Invalid ID %" PRId32, contextHubId);
    return false;
  }

  std::unique_lock<std::mutex> lock(mSynchronousLoadUnloadMutex);
  mSynchronousLoadUnloadTransactionId = kStartingInternalTransactionId;

  for (int64_t nanoappIdToUnload : nanoappIdList) {
    LOGI("Unloading nanoapp with ID: 0x%016" PRIx64, nanoappIdToUnload);

    bool success = false;
    if (!unloadNanoappInternal(nanoappIdToUnload,
                               *mSynchronousLoadUnloadTransactionId)) {
      LOGE("Failed to request unloading nanoapp with ID 0x%" PRIx64,
           nanoappIdToUnload);
    } else {
      mSynchronousLoadUnloadSuccess.reset();
      mSynchronousLoadUnloadCondVar.wait_for(lock, kTestModeTimeout, [this]() {
        return mSynchronousLoadUnloadSuccess.has_value();
      });
      if (mSynchronousLoadUnloadSuccess.has_value() &&
          *mSynchronousLoadUnloadSuccess) {
        LOGI("Successfully unloaded nanoapp with ID: 0x%016" PRIx64,
             nanoappIdToUnload);

        success = true;
      }
    }

    if (!success) {
      LOGE("Failed to unload nanoapp with ID 0x%" PRIx64, nanoappIdToUnload);
    }
    ++(*mSynchronousLoadUnloadTransactionId);
  }

  return true;
}

bool ContextHub::getPreloadedNanoappIdsFromConfigFile(
    std::vector<chrePreloadedNanoappInfo> &out_preloadedNanoapps,
    std::string *out_directory) const {
  std::vector<std::string> nanoappNames;
  std::string directory;

  bool success = getPreloadedNanoappsFromConfigFile(
      kPreloadedNanoappsConfigPath, directory, nanoappNames);
  if (!success) {
    LOGE("Failed to parse preloaded nanoapps config file");
  }

  for (const std::string &nanoappName : nanoappNames) {
    std::string headerFile = directory + "/" + nanoappName + ".napp_header";
    std::vector<uint8_t> headerBuffer;
    if (!readFileContents(headerFile.c_str(), headerBuffer)) {
      LOGE("Cannot read header file: %s", headerFile.c_str());
      continue;
    }

    if (headerBuffer.size() != sizeof(NanoAppBinaryHeader)) {
      LOGE("Header size mismatch");
      continue;
    }

    const auto *appHeader =
        reinterpret_cast<const NanoAppBinaryHeader *>(headerBuffer.data());
    out_preloadedNanoapps.emplace_back(static_cast<int64_t>(appHeader->appId),
                                       nanoappName, *appHeader);
  }

  if (out_directory != nullptr) {
    *out_directory = directory;
  }

  return true;
}

std::vector<NanoappBinary> ContextHub::selectPreloadedNanoappsToLoad(
    std::vector<chrePreloadedNanoappInfo> &preloadedNanoapps,
    const std::string &preloadedNanoappDirectory) {
  std::vector<NanoappBinary> nanoappsToLoad;

  for (auto &preloadedNanoapp : preloadedNanoapps) {
    int64_t nanoappId = preloadedNanoapp.id;

    // A nanoapp is a system nanoapp if it is in the preloaded nanoapp list
    // but not in the loaded nanoapp list as CHRE hides system nanoapps
    // from the HAL.
    bool isSystemNanoapp =
        std::any_of(mSystemNanoappIds.begin(), mSystemNanoappIds.end(),
                    [nanoappId](int64_t systemNanoappId) {
                      return systemNanoappId == nanoappId;
                    });
    if (!isSystemNanoapp) {
      std::vector<uint8_t> nanoappBuffer;
      std::string nanoappFile =
          preloadedNanoappDirectory + "/" + preloadedNanoapp.name + ".so";
      if (!readFileContents(nanoappFile.c_str(), nanoappBuffer)) {
        LOGE("Cannot read header file: %s", nanoappFile.c_str());
      } else {
        NanoappBinary nanoapp;
        nanoapp.nanoappId = preloadedNanoapp.header.appId;
        nanoapp.nanoappVersion = preloadedNanoapp.header.appVersion;
        nanoapp.flags = preloadedNanoapp.header.flags;
        nanoapp.targetChreApiMajorVersion =
            preloadedNanoapp.header.targetChreApiMajorVersion;
        nanoapp.targetChreApiMinorVersion =
            preloadedNanoapp.header.targetChreApiMinorVersion;
        nanoapp.customBinary = nanoappBuffer;

        nanoappsToLoad.push_back(nanoapp);
      }
    }
  }
  return nanoappsToLoad;
}

}  // namespace aidl::android::hardware::contexthub
