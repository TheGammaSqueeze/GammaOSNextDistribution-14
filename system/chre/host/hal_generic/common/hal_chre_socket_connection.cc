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

// TODO(b/298459533): remove_ap_wakeup_metric_report_limit ramp up -> remove old
// code

#define LOG_TAG "ContextHubHal"
#define LOG_NDEBUG 1

#include "hal_chre_socket_connection.h"

#include <log/log.h>

#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
// TODO(b/298459533): Remove these when the flag_log_nanoapp_load_metrics flag
// is cleaned up
#include <aidl/android/frameworks/stats/IStats.h>
#include <android/binder_manager.h>
#include <android_chre_flags.h>
// TODO(b/298459533): Remove end

#include <chre_atoms_log.h>
#include <utils/SystemClock.h>
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED

namespace android {
namespace hardware {
namespace contexthub {
namespace common {
namespace implementation {

using ::android::chre::FragmentedLoadRequest;
using ::android::chre::FragmentedLoadTransaction;
using ::android::chre::HostProtocolHost;
using ::flatbuffers::FlatBufferBuilder;

#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
// TODO(b/298459533): Remove these when the flag_log_nanoapp_load_metrics flag
// is cleaned up
using ::aidl::android::frameworks::stats::IStats;
using ::aidl::android::frameworks::stats::VendorAtom;
using ::aidl::android::frameworks::stats::VendorAtomValue;
using ::android::chre::Atoms::CHRE_AP_WAKE_UP_OCCURRED;
using ::android::chre::Atoms::CHRE_HAL_NANOAPP_LOAD_FAILED;
using ::android::chre::flags::flag_log_nanoapp_load_metrics;
using ::android::chre::flags::remove_ap_wakeup_metric_report_limit;
// TODO(b/298459533): Remove end

using ::android::chre::MetricsReporter;
using ::android::chre::Atoms::ChreHalNanoappLoadFailed;
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED

HalChreSocketConnection::HalChreSocketConnection(
    IChreSocketCallback *callback) {
  constexpr char kChreSocketName[] = "chre";

  mSocketCallbacks = sp<SocketCallbacks>::make(*this, callback);
  if (!mClient.connectInBackground(kChreSocketName, mSocketCallbacks)) {
    ALOGE("Couldn't start socket client");
  }
}

bool HalChreSocketConnection::getContextHubs(
    ::chre::fbs::HubInfoResponseT *response) {
  constexpr auto kHubInfoQueryTimeout = std::chrono::seconds(5);
  ALOGV("%s", __func__);

  // If we're not connected yet, give it some time
  // TODO refactor from polling into conditional wait
  int maxSleepIterations = 250;
  while (!mHubInfoValid && !mClient.isConnected() && --maxSleepIterations > 0) {
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
  }

  if (!mClient.isConnected()) {
    ALOGE("Couldn't connect to hub daemon");
  } else if (!mHubInfoValid) {
    // We haven't cached the hub details yet, so send a request and block
    // waiting on a response
    std::unique_lock<std::mutex> lock(mHubInfoMutex);
    FlatBufferBuilder builder;
    HostProtocolHost::encodeHubInfoRequest(builder);

    ALOGD("Sending hub info request");
    if (!mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
      ALOGE("Couldn't send hub info request");
    } else {
      mHubInfoCond.wait_for(lock, kHubInfoQueryTimeout,
                            [this]() { return mHubInfoValid; });
    }
  }

  if (mHubInfoValid) {
    *response = mHubInfoResponse;
  } else {
    ALOGE("Unable to get hub info from CHRE");
  }

  return mHubInfoValid;
}

bool HalChreSocketConnection::sendDebugConfiguration() {
  FlatBufferBuilder builder;
  HostProtocolHost::encodeDebugConfiguration(builder);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::sendMessageToHub(uint64_t nanoappId,
                                               uint32_t messageType,
                                               uint16_t hostEndpointId,
                                               const unsigned char *payload,
                                               size_t payloadLength) {
  FlatBufferBuilder builder(1024);
  HostProtocolHost::encodeNanoappMessage(
      builder, nanoappId, messageType, hostEndpointId, payload, payloadLength);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::loadNanoapp(
    FragmentedLoadTransaction &transaction) {
  bool success = false;
  std::lock_guard<std::mutex> lock(mPendingLoadTransactionMutex);

  if (mPendingLoadTransaction.has_value()) {
    ALOGE("Pending load transaction exists. Overriding pending request");
  }

  mPendingLoadTransaction = transaction;
  success = sendFragmentedLoadNanoAppRequest(mPendingLoadTransaction.value());
  if (!success) {
    mPendingLoadTransaction.reset();
  }

  return success;
}

bool HalChreSocketConnection::unloadNanoapp(uint64_t appId,
                                            uint32_t transactionId) {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeUnloadNanoappRequest(
      builder, transactionId, appId, false /* allowSystemNanoappUnload */);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::queryNanoapps() {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeNanoappListRequest(builder);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::requestDebugDump() {
  FlatBufferBuilder builder;
  HostProtocolHost::encodeDebugDumpRequest(builder);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::sendSettingChangedNotification(
    ::chre::fbs::Setting fbsSetting, ::chre::fbs::SettingState fbsState) {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeSettingChangeNotification(builder, fbsSetting,
                                                    fbsState);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::onHostEndpointConnected(
    uint16_t hostEndpointId, uint8_t type, const std::string &package_name,
    const std::string &attribution_tag) {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeHostEndpointConnected(builder, hostEndpointId, type,
                                                package_name, attribution_tag);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::onHostEndpointDisconnected(
    uint16_t hostEndpointId) {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeHostEndpointDisconnected(builder, hostEndpointId);
  return mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool HalChreSocketConnection::isLoadTransactionPending() {
  std::lock_guard<std::mutex> lock(mPendingLoadTransactionMutex);
  return mPendingLoadTransaction.has_value();
}

HalChreSocketConnection::SocketCallbacks::SocketCallbacks(
    HalChreSocketConnection &parent, IChreSocketCallback *callback)
    : mParent(parent), mCallback(callback) {
#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
  if (!remove_ap_wakeup_metric_report_limit()) {
    mLastClearedTimestamp = elapsedRealtime();
  }
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED
}

void HalChreSocketConnection::SocketCallbacks::onMessageReceived(
    const void *data, size_t length) {
  if (!HostProtocolHost::decodeMessageFromChre(data, length, *this)) {
    ALOGE("Failed to decode message");
  }
}

void HalChreSocketConnection::SocketCallbacks::onConnected() {
  ALOGI("Reconnected to CHRE daemon");
  if (mHaveConnected) {
    ALOGI("Reconnected to CHRE daemon");
    mCallback->onContextHubRestarted();
  }
  mParent.sendDebugConfiguration();
  mHaveConnected = true;
}

void HalChreSocketConnection::SocketCallbacks::onDisconnected() {
  ALOGW("Lost connection to CHRE daemon");
}

void HalChreSocketConnection::SocketCallbacks::handleNanoappMessage(
    const ::chre::fbs::NanoappMessageT &message) {
  ALOGD("Got message from nanoapp: ID 0x%" PRIx64, message.app_id);
  mCallback->onNanoappMessage(message);

#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
  if (message.woke_host) {
    // check and update the 24hour timer
    std::lock_guard<std::mutex> lock(mNanoappWokeApCountMutex);
    long nanoappId = message.app_id;

    if (!remove_ap_wakeup_metric_report_limit()) {
      long timeElapsed = elapsedRealtime() - mLastClearedTimestamp;
      if (timeElapsed > kOneDayinMillis) {
        mNanoappWokeUpCount = 0;
        mLastClearedTimestamp = elapsedRealtime();
      }

      mNanoappWokeUpCount++;
    }

    if (remove_ap_wakeup_metric_report_limit() ||
        mNanoappWokeUpCount < kMaxDailyReportedApWakeUp) {
      if (flag_log_nanoapp_load_metrics()) {
        if (!mParent.mMetricsReporter.logApWakeupOccurred(nanoappId)) {
          ALOGE("Could not log AP Wakeup metric");
        }
      } else {
        // create and report the vendor atom
        std::vector<VendorAtomValue> values(1);
        values[0].set<VendorAtomValue::longValue>(nanoappId);

        const VendorAtom atom{
            .atomId = CHRE_AP_WAKE_UP_OCCURRED,
            .values{std::move(values)},
        };

        mParent.reportMetric(atom);
      }
    }
  }
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED
}

void HalChreSocketConnection::SocketCallbacks::handleHubInfoResponse(
    const ::chre::fbs::HubInfoResponseT &response) {
  ALOGD("Got hub info response");

  std::lock_guard<std::mutex> lock(mParent.mHubInfoMutex);
  if (mParent.mHubInfoValid) {
    ALOGI("Ignoring duplicate/unsolicited hub info response");
  } else {
    mParent.mHubInfoResponse = response;
    mParent.mHubInfoValid = true;
    mParent.mHubInfoCond.notify_all();
  }
}

void HalChreSocketConnection::SocketCallbacks::handleNanoappListResponse(
    const ::chre::fbs::NanoappListResponseT &response) {
  ALOGD("Got nanoapp list response with %zu apps", response.nanoapps.size());
  mCallback->onNanoappListResponse(response);
}

void HalChreSocketConnection::SocketCallbacks::handleLoadNanoappResponse(
    const ::chre::fbs::LoadNanoappResponseT &response) {
  ALOGD("Got load nanoapp response for transaction %" PRIu32
        " fragment %" PRIu32 " with result %d",
        response.transaction_id, response.fragment_id, response.success);
  std::unique_lock<std::mutex> lock(mParent.mPendingLoadTransactionMutex);

  // TODO: Handle timeout in receiving load response
  if (!mParent.mPendingLoadTransaction.has_value()) {
    ALOGE(
        "Dropping unexpected load response (no pending transaction "
        "exists)");
  } else {
    FragmentedLoadTransaction &transaction =
        mParent.mPendingLoadTransaction.value();

    if (!mParent.isExpectedLoadResponseLocked(response)) {
      ALOGE("Dropping unexpected load response, expected transaction %" PRIu32
            " fragment %" PRIu32 ", received transaction %" PRIu32
            " fragment %" PRIu32,
            transaction.getTransactionId(), mParent.mCurrentFragmentId,
            response.transaction_id, response.fragment_id);
    } else {
      bool success = false;
      bool continueLoadRequest = false;
      if (response.success && !transaction.isComplete()) {
        if (mParent.sendFragmentedLoadNanoAppRequest(transaction)) {
          continueLoadRequest = true;
          success = true;
        }
      } else {
        success = response.success;

#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
        if (!success) {
          if (flag_log_nanoapp_load_metrics()) {
            if (!mParent.mMetricsReporter.logNanoappLoadFailed(
                    transaction.getNanoappId(),
                    ChreHalNanoappLoadFailed::TYPE_DYNAMIC,
                    ChreHalNanoappLoadFailed::REASON_ERROR_GENERIC)) {
              ALOGE("Could not log the nanoapp load failed metric");
            }
          }
        }
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED
      }

      if (!continueLoadRequest) {
        mParent.mPendingLoadTransaction.reset();
        lock.unlock();
        mCallback->onTransactionResult(response.transaction_id, success);
      }
    }
  }
}

void HalChreSocketConnection::SocketCallbacks::handleUnloadNanoappResponse(
    const ::chre::fbs::UnloadNanoappResponseT &response) {
  ALOGV("Got unload nanoapp response for transaction %" PRIu32
        " with result %d",
        response.transaction_id, response.success);
  mCallback->onTransactionResult(response.transaction_id, response.success);
}

void HalChreSocketConnection::SocketCallbacks::handleDebugDumpData(
    const ::chre::fbs::DebugDumpDataT &data) {
  ALOGV("Got debug dump data, size %zu", data.debug_str.size());
  mCallback->onDebugDumpData(data);
}

void HalChreSocketConnection::SocketCallbacks::handleDebugDumpResponse(
    const ::chre::fbs::DebugDumpResponseT &response) {
  ALOGV("Got debug dump response, success %d, data count %" PRIu32,
        response.success, response.data_count);
  mCallback->onDebugDumpComplete(response);
}

bool HalChreSocketConnection::isExpectedLoadResponseLocked(
    const ::chre::fbs::LoadNanoappResponseT &response) {
  return mPendingLoadTransaction.has_value() &&
         (mPendingLoadTransaction->getTransactionId() ==
          response.transaction_id) &&
         (response.fragment_id == 0 ||
          mCurrentFragmentId == response.fragment_id);
}

bool HalChreSocketConnection::sendFragmentedLoadNanoAppRequest(
    FragmentedLoadTransaction &transaction) {
  bool success = false;
  const FragmentedLoadRequest &request = transaction.getNextRequest();

  FlatBufferBuilder builder(128 + request.binary.size());
  HostProtocolHost::encodeFragmentedLoadNanoappRequest(builder, request);

  if (!mClient.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
    ALOGE("Failed to send load request message (fragment ID = %zu)",
          request.fragmentId);

#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
    if (flag_log_nanoapp_load_metrics()) {
      if (!mMetricsReporter.logNanoappLoadFailed(
              request.appId, ChreHalNanoappLoadFailed::TYPE_DYNAMIC,
              ChreHalNanoappLoadFailed::REASON_CONNECTION_ERROR)) {
        ALOGE("Could not log the nanoapp load failed metric");
      }
    } else {
      // create and report the vendor atom
      std::vector<VendorAtomValue> values(3);
      values[0].set<VendorAtomValue::longValue>(request.appId);
      values[1].set<VendorAtomValue::intValue>(
          ChreHalNanoappLoadFailed::TYPE_DYNAMIC);
      values[2].set<VendorAtomValue::intValue>(
          ChreHalNanoappLoadFailed::REASON_ERROR_GENERIC);

      const VendorAtom atom{
          .atomId = CHRE_HAL_NANOAPP_LOAD_FAILED,
          .values{std::move(values)},
      };
      reportMetric(atom);
    }
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED

  } else {
    mCurrentFragmentId = request.fragmentId;
    success = true;
  }

  return success;
}

#ifdef CHRE_HAL_SOCKET_METRICS_ENABLED
// TODO(b/298459533): Remove this the flag_log_nanoapp_load_metrics flag is
// cleaned up
void HalChreSocketConnection::reportMetric(const VendorAtom atom) {
  const std::string statsServiceName =
      std::string(IStats::descriptor).append("/default");
  if (!AServiceManager_isDeclared(statsServiceName.c_str())) {
    ALOGE("Stats service is not declared.");
    return;
  }

  std::shared_ptr<IStats> stats_client = IStats::fromBinder(ndk::SpAIBinder(
      AServiceManager_waitForService(statsServiceName.c_str())));
  if (stats_client == nullptr) {
    ALOGE("Failed to get IStats service");
    return;
  }

  const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(atom);
  if (!ret.isOk()) {
    ALOGE("Failed to report vendor atom");
  }
}
// TODO(b/298459533): Remove end
#endif  // CHRE_HAL_SOCKET_METRICS_ENABLED

}  // namespace implementation
}  // namespace common
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
