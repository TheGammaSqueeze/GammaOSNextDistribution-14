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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_CONTEXTHUB_H
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_CONTEXTHUB_H

#include <condition_variable>
#include <functional>
#include <mutex>
#include <optional>

#include <android/hardware/contexthub/1.0/IContexthub.h>
#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <log/log.h>

#include "IContextHubCallbackWrapper.h"
#include "debug_dump_helper.h"
#include "hal_chre_socket_connection.h"
#include "permissions_util.h"

namespace android {
namespace hardware {
namespace contexthub {
namespace common {
namespace implementation {

using ::android::sp;
using ::android::chre::FragmentedLoadTransaction;
using ::android::chre::getStringFromByteVector;
using ::android::chre::HostProtocolHost;
using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::contexthub::DebugDumpHelper;
using ::android::hardware::contexthub::common::implementation::
    chreToAndroidPermissions;
using ::android::hardware::contexthub::V1_0::AsyncEventType;
using ::android::hardware::contexthub::V1_0::ContextHub;
using ::android::hardware::contexthub::V1_0::IContexthubCallback;
using ::android::hardware::contexthub::V1_0::NanoAppBinary;
using ::android::hardware::contexthub::V1_0::Result;
using ::android::hardware::contexthub::V1_0::TransactionResult;
using ::android::hardware::contexthub::V1_2::ContextHubMsg;
using ::android::hardware::contexthub::V1_2::HubAppInfo;
using ::android::hardware::contexthub::V1_X::implementation::
    IContextHubCallbackWrapperBase;
using ::android::hardware::contexthub::V1_X::implementation::
    IContextHubCallbackWrapperV1_0;

constexpr uint32_t kDefaultHubId = 0;

inline constexpr uint8_t extractChreApiMajorVersion(uint32_t chreVersion) {
  return static_cast<uint8_t>(chreVersion >> 24);
}

inline constexpr uint8_t extractChreApiMinorVersion(uint32_t chreVersion) {
  return static_cast<uint8_t>(chreVersion >> 16);
}

inline constexpr uint16_t extractChrePatchVersion(uint32_t chreVersion) {
  return static_cast<uint16_t>(chreVersion);
}

inline hidl_vec<hidl_string> stringVectorToHidl(
    const std::vector<std::string> &list) {
  std::vector<hidl_string> outList;
  for (const std::string &item : list) {
    outList.push_back(item);
  }

  return hidl_vec(outList);
}

/**
 * @return file descriptor contained in the hidl_handle, or -1 if there is none
 */
inline int hidlHandleToFileDescriptor(const hidl_handle &hh) {
  const native_handle_t *handle = hh.getNativeHandle();
  return (handle != nullptr && handle->numFds >= 1) ? handle->data[0] : -1;
}

template <class IContexthubT>
class GenericContextHubBase : public IContexthubT,
                              public IChreSocketCallback,
                              public DebugDumpHelper {
 public:
  GenericContextHubBase() {
    mDeathRecipient = new DeathRecipient(this);
  }

  bool requestDebugDump() override {
    return mConnection.requestDebugDump();
  }

  Return<void> debug(const hidl_handle &fd,
                     const hidl_vec<hidl_string> & /* options */) override {
    debugDumpStart(hidlHandleToFileDescriptor(fd));
    return Void();
  }

  // Methods from ::android::hardware::contexthub::V1_0::IContexthub follow.
  Return<void> getHubs(V1_0::IContexthub::getHubs_cb _hidl_cb) override {
    std::vector<ContextHub> hubs;

    ::chre::fbs::HubInfoResponseT response;
    bool success = mConnection.getContextHubs(&response);
    if (success) {
      mHubInfo.name = getStringFromByteVector(response.name);
      mHubInfo.vendor = getStringFromByteVector(response.vendor);
      mHubInfo.toolchain = getStringFromByteVector(response.toolchain);
      mHubInfo.platformVersion = response.platform_version;
      mHubInfo.toolchainVersion = response.toolchain_version;
      mHubInfo.hubId = kDefaultHubId;

      mHubInfo.peakMips = response.peak_mips;
      mHubInfo.stoppedPowerDrawMw = response.stopped_power;
      mHubInfo.sleepPowerDrawMw = response.sleep_power;
      mHubInfo.peakPowerDrawMw = response.peak_power;

      mHubInfo.maxSupportedMsgLen = response.max_msg_len;
      mHubInfo.chrePlatformId = response.platform_id;

      uint32_t version = response.chre_platform_version;
      mHubInfo.chreApiMajorVersion = extractChreApiMajorVersion(version);
      mHubInfo.chreApiMinorVersion = extractChreApiMinorVersion(version);
      mHubInfo.chrePatchVersion = extractChrePatchVersion(version);

      hubs.push_back(mHubInfo);
    }

    _hidl_cb(hubs);
    return Void();
  }

  Return<Result> registerCallback(uint32_t hubId,
                                  const sp<IContexthubCallback> &cb) override {
    sp<IContextHubCallbackWrapperBase> wrappedCallback;
    if (cb != nullptr) {
      wrappedCallback = new IContextHubCallbackWrapperV1_0(cb);
    }
    return registerCallbackCommon(hubId, wrappedCallback);
  }

  // Common logic shared between pre-V1.2 and V1.2 HALs.
  Return<Result> registerCallbackCommon(
      uint32_t hubId, const sp<IContextHubCallbackWrapperBase> &cb) {
    Result result;
    ALOGV("%s", __func__);

    // TODO: currently we only support 1 hub behind this HAL implementation
    if (hubId == kDefaultHubId) {
      std::lock_guard<std::mutex> lock(mCallbacksLock);

      if (cb != nullptr) {
        if (mCallbacks != nullptr) {
          ALOGD("Modifying callback for hubId %" PRIu32, hubId);
          mCallbacks->unlinkToDeath(mDeathRecipient);
        }
        Return<bool> linkReturn = cb->linkToDeath(mDeathRecipient, hubId);
        if (!linkReturn.withDefault(false)) {
          ALOGW("Could not link death recipient to hubId %" PRIu32, hubId);
        }
      }

      mCallbacks = cb;
      result = Result::OK;
    } else {
      result = Result::BAD_PARAMS;
    }

    return result;
  }

  Return<Result> sendMessageToHub(uint32_t hubId,
                                  const V1_0::ContextHubMsg &msg) override {
    Result result;
    ALOGV("%s", __func__);

    if (hubId != kDefaultHubId) {
      result = Result::BAD_PARAMS;
    } else {
      result = toHidlResult(mConnection.sendMessageToHub(
          msg.appName, msg.msgType, msg.hostEndPoint, msg.msg.data(),
          msg.msg.size()));
    }

    return result;
  }

  Return<Result> loadNanoApp(uint32_t hubId, const NanoAppBinary &appBinary,
                             uint32_t transactionId) override {
    Result result;
    ALOGV("%s", __func__);

    if (hubId != kDefaultHubId) {
      result = Result::BAD_PARAMS;
    } else {
      uint32_t targetApiVersion = (appBinary.targetChreApiMajorVersion << 24) |
                                  (appBinary.targetChreApiMinorVersion << 16);
      FragmentedLoadTransaction transaction(
          transactionId, appBinary.appId, appBinary.appVersion, appBinary.flags,
          targetApiVersion, appBinary.customBinary);
      result = toHidlResult(mConnection.loadNanoapp(transaction));
    }

    ALOGD(
        "Attempted to send load nanoapp request for app of size %zu with ID "
        "0x%016" PRIx64 " as transaction ID %" PRIu32 ": result %" PRIu32,
        appBinary.customBinary.size(), appBinary.appId, transactionId, result);

    return result;
  }

  Return<Result> unloadNanoApp(uint32_t hubId, uint64_t appId,
                               uint32_t transactionId) override {
    Result result;
    ALOGV("%s", __func__);

    if (hubId != kDefaultHubId) {
      result = Result::BAD_PARAMS;
    } else {
      result = toHidlResult(mConnection.unloadNanoapp(appId, transactionId));
    }

    ALOGD("Attempted to send unload nanoapp request for app ID 0x%016" PRIx64
          " as transaction ID %" PRIu32 ": result %" PRIu32,
          appId, transactionId, result);

    return result;
  }

  Return<Result> enableNanoApp(uint32_t /* hubId */, uint64_t appId,
                               uint32_t /* transactionId */) override {
    // TODO
    ALOGW("Attempted to enable app ID 0x%016" PRIx64 ", but not supported",
          appId);
    return Result::TRANSACTION_FAILED;
  }

  Return<Result> disableNanoApp(uint32_t /* hubId */, uint64_t appId,
                                uint32_t /* transactionId */) override {
    // TODO
    ALOGW("Attempted to disable app ID 0x%016" PRIx64 ", but not supported",
          appId);
    return Result::TRANSACTION_FAILED;
  }

  Return<Result> queryApps(uint32_t hubId) override {
    Result result;
    ALOGV("%s", __func__);

    if (hubId != kDefaultHubId) {
      result = Result::BAD_PARAMS;
    } else {
      result = toHidlResult(mConnection.queryNanoapps());
    }

    return result;
  }

  void onNanoappMessage(const ::chre::fbs::NanoappMessageT &message) override {
    ContextHubMsg msg;
    msg.msg_1_0.appName = message.app_id;
    msg.msg_1_0.hostEndPoint = message.host_endpoint;
    msg.msg_1_0.msgType = message.message_type;
    msg.msg_1_0.msg = message.message;
    // Set of nanoapp permissions required to communicate with this nanoapp.
    msg.permissions =
        stringVectorToHidl(chreToAndroidPermissions(message.permissions));
    // Set of permissions required to consume this message and what will be
    // attributed when the host endpoint consumes this on the Android side.
    hidl_vec<hidl_string> msgContentPerms = stringVectorToHidl(
        chreToAndroidPermissions(message.message_permissions));

    invokeClientCallback(
        [&]() { return mCallbacks->handleClientMsg(msg, msgContentPerms); });
  }

  void onNanoappListResponse(
      const ::chre::fbs::NanoappListResponseT &response) override {
    std::vector<HubAppInfo> appInfoList;

    for (const std::unique_ptr<::chre::fbs::NanoappListEntryT> &nanoapp :
         response.nanoapps) {
      // TODO: determine if this is really required, and if so, have
      // HostProtocolHost strip out null entries as part of decode
      if (nanoapp == nullptr) {
        continue;
      }

      ALOGV("App 0x%016" PRIx64 " ver 0x%" PRIx32 " permissions 0x%" PRIx32
            " enabled %d system %d",
            nanoapp->app_id, nanoapp->version, nanoapp->permissions,
            nanoapp->enabled, nanoapp->is_system);
      if (!nanoapp->is_system) {
        HubAppInfo appInfo;

        appInfo.info_1_0.appId = nanoapp->app_id;
        appInfo.info_1_0.version = nanoapp->version;
        appInfo.info_1_0.enabled = nanoapp->enabled;
        appInfo.permissions =
            stringVectorToHidl(chreToAndroidPermissions(nanoapp->permissions));

        appInfoList.push_back(appInfo);
      }
    }

    invokeClientCallback(
        [&]() { return mCallbacks->handleAppsInfo(appInfoList); });
  }

  void onTransactionResult(uint32_t transactionId, bool success) override {
    TransactionResult result =
        success ? TransactionResult::SUCCESS : TransactionResult::FAILURE;
    invokeClientCallback(
        [&]() { return mCallbacks->handleTxnResult(transactionId, result); });
  }

  void onContextHubRestarted() override {
    invokeClientCallback([&]() {
      return mCallbacks->handleHubEvent(AsyncEventType::RESTARTED);
    });
  }

  void onDebugDumpData(const ::chre::fbs::DebugDumpDataT &data) override {
    auto str =
        std::string(reinterpret_cast<const char *>(data.debug_str.data()),
                    data.debug_str.size());
    debugDumpAppend(str);
  }

  void onDebugDumpComplete(
      const ::chre::fbs::DebugDumpResponseT & /* response */) override {
    debugDumpComplete();
  }

  // Write a string to the debug file.
  void writeToDebugFile(const char *str) override {
    if (checkDebugFd()) {
      size_t len = strlen(str);
      ssize_t written = write(getDebugFd(), str, len);
      if (written != (ssize_t)len) {
        ALOGW(
            "Couldn't write to debug header: returned %zd, expected %zu (errno "
            "%d)",
            written, len, errno);
      }
    } else {
      ALOGE("Attempted to write to an invalid FD");
    }
  }

 protected:
  sp<IContextHubCallbackWrapperBase> mCallbacks;
  std::mutex mCallbacksLock;

  class DeathRecipient : public hidl_death_recipient {
   public:
    explicit DeathRecipient(const sp<GenericContextHubBase> contexthub)
        : mGenericContextHub(contexthub) {}
    void serviceDied(
        uint64_t cookie,
        const wp<::android::hidl::base::V1_0::IBase> & /* who */) override {
      uint32_t hubId = static_cast<uint32_t>(cookie);
      mGenericContextHub->handleServiceDeath(hubId);
    }

   private:
    sp<GenericContextHubBase> mGenericContextHub;
  };

  HalChreSocketConnection mConnection{this};

  sp<DeathRecipient> mDeathRecipient;

  // Cached hub info used for getHubs(), and synchronization primitives to make
  // that function call synchronous if we need to query it
  ContextHub mHubInfo;
  bool mHubInfoValid = false;
  std::mutex mHubInfoMutex;
  std::condition_variable mHubInfoCond;

  // Unregisters callback when context hub service dies
  void handleServiceDeath(uint32_t hubId) {
    std::lock_guard<std::mutex> lock(mCallbacksLock);
    ALOGI("Context hub service died for hubId %" PRIu32, hubId);
    mCallbacks.clear();
  }

  void invokeClientCallback(std::function<Return<void>()> callback) {
    std::lock_guard<std::mutex> lock(mCallbacksLock);
    if (mCallbacks != nullptr && !callback().isOk()) {
      ALOGE("Failed to invoke client callback");
    }
  }

  Result toHidlResult(bool success) {
    return success ? Result::OK : Result::UNKNOWN_FAILURE;
  }
};

}  // namespace implementation
}  // namespace common
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_CONTEXTHUB_H
