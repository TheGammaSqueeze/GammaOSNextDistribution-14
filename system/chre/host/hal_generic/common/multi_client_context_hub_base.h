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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_MULTICLIENTS_HAL_BASE_H_
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_MULTICLIENTS_HAL_BASE_H_

#include <aidl/android/hardware/contexthub/BnContextHub.h>
#include <chre_host/generated/host_messages_generated.h>
#include <chre_host/log_message_parser.h>
#include <chre_host/metrics_reporter.h>

#include "chre_connection_callback.h"
#include "chre_host/napp_header.h"
#include "chre_host/preloaded_nanoapp_loader.h"
#include "chre_host/time_syncer.h"
#include "debug_dump_helper.h"
#include "event_logger.h"
#include "hal_client_id.h"
#include "hal_client_manager.h"

namespace android::hardware::contexthub::common::implementation {

using namespace aidl::android::hardware::contexthub;
using namespace android::chre;
using ::ndk::ScopedAStatus;

/**
 * The base class of multiclient HAL.
 *
 * A subclass should initiate mConnection, mHalClientManager and
 * mPreloadedNanoappLoader in its constructor.
 */
class MultiClientContextHubBase
    : public BnContextHub,
      public ::android::hardware::contexthub::common::implementation::
          ChreConnectionCallback,
      public ::android::hardware::contexthub::DebugDumpHelper {
 public:
  /** The entry point of death recipient for a disconnected client. */
  static void onClientDied(void *cookie);

  MultiClientContextHubBase();

  // functions implementing IContextHub
  ScopedAStatus getContextHubs(
      std::vector<ContextHubInfo> *contextHubInfos) override;
  ScopedAStatus loadNanoapp(int32_t contextHubId,
                            const NanoappBinary &appBinary,
                            int32_t transactionId) override;
  ScopedAStatus unloadNanoapp(int32_t contextHubId, int64_t appId,
                              int32_t transactionId) override;
  ScopedAStatus disableNanoapp(int32_t contextHubId, int64_t appId,
                               int32_t transactionId) override;
  ScopedAStatus enableNanoapp(int32_t contextHubId, int64_t appId,
                              int32_t transactionId) override;
  ScopedAStatus onSettingChanged(Setting setting, bool enabled) override;
  ScopedAStatus queryNanoapps(int32_t contextHubId) override;
  ScopedAStatus registerCallback(
      int32_t contextHubId,
      const std::shared_ptr<IContextHubCallback> &callback) override;
  ScopedAStatus sendMessageToHub(int32_t contextHubId,
                                 const ContextHubMessage &message) override;
  ScopedAStatus onHostEndpointConnected(const HostEndpointInfo &info) override;
  ScopedAStatus onHostEndpointDisconnected(char16_t in_hostEndpointId) override;
  ScopedAStatus getPreloadedNanoappIds(int32_t contextHubId,
                                       std::vector<int64_t> *result) override;
  ScopedAStatus onNanSessionStateChanged(
      const NanSessionStateUpdate &in_update) override;
  ScopedAStatus setTestMode(bool enable) override;
  ScopedAStatus sendMessageDeliveryStatusToHub(
      int32_t contextHubId,
      const MessageDeliveryStatus &messageDeliveryStatus) override;

  // The callback function implementing ChreConnectionCallback
  void handleMessageFromChre(const unsigned char *messageBuffer,
                             size_t messageLen) override;
  void onChreRestarted() override;

  // The functions for dumping debug information
  binder_status_t dump(int fd, const char **args, uint32_t numArgs) override;
  bool requestDebugDump() override;
  void writeToDebugFile(const char *str) override;

 protected:
  // The data needed by the death client to clear states of a client.
  struct HalDeathRecipientCookie {
    MultiClientContextHubBase *hal;
    pid_t clientPid;
    HalDeathRecipientCookie(MultiClientContextHubBase *hal, pid_t pid) {
      this->hal = hal;
      this->clientPid = pid;
    }
  };

  void tryTimeSync(size_t numOfRetries, useconds_t retryDelayUs) {
    if (mConnection->isTimeSyncNeeded()) {
      TimeSyncer::sendTimeSyncWithRetry(mConnection.get(), numOfRetries,
                                        retryDelayUs);
    }
  }

  bool sendFragmentedLoadRequest(HalClientId clientId,
                                 FragmentedLoadRequest &fragmentedLoadRequest);

  // Functions handling various types of messages
  void handleHubInfoResponse(const ::chre::fbs::HubInfoResponseT &message);
  void onNanoappListResponse(const ::chre::fbs::NanoappListResponseT &response,
                             HalClientId clientid);
  void onNanoappLoadResponse(const ::chre::fbs::LoadNanoappResponseT &response,
                             HalClientId clientId);
  void onNanoappUnloadResponse(
      const ::chre::fbs::UnloadNanoappResponseT &response,
      HalClientId clientId);
  void onNanoappMessage(const ::chre::fbs::NanoappMessageT &message);
  void onMessageDeliveryStatus(
      const ::chre::fbs::MessageDeliveryStatusT &status);
  void onDebugDumpData(const ::chre::fbs::DebugDumpDataT &data);
  void onDebugDumpComplete(
      const ::chre::fbs::DebugDumpResponseT & /* response */);
  void onMetricLog(const ::chre::fbs::MetricLogT &metricMessage);
  void handleClientDeath(pid_t pid);

  /**
   * Returns true to allow metrics to be reported to stats service.
   *
   * <p>Subclasses can override to turn it off.
   */
  virtual bool isMetricEnabled() {
    return true;
  }

  /**
   * Enables test mode by unloading all the nanoapps except the system nanoapps.
   *
   * @return true as long as we have a list of nanoapps to unload.
   */
  bool enableTestMode();

  /**
   * Disables test mode by reloading all the <b>preloaded</b> nanoapps except
   * system nanoapps.
   *
   * <p>Note that dynamically loaded nanoapps that are unloaded during
   * enableTestMode() are not reloaded back because HAL doesn't track the
   * location of their binaries.
   */
  void disableTestMode();

  inline bool isSettingEnabled(Setting setting) {
    return mSettingEnabled.find(setting) != mSettingEnabled.end() &&
           mSettingEnabled[setting];
  }

  HalClientManager::DeadClientUnlinker mDeadClientUnlinker;

  // HAL is the unique owner of the communication channel to CHRE.
  std::unique_ptr<ChreConnection> mConnection{};

  // HalClientManager maintains states of hal clients. Each HAL should only have
  // one instance of a HalClientManager.
  std::unique_ptr<HalClientManager> mHalClientManager{};

  std::unique_ptr<PreloadedNanoappLoader> mPreloadedNanoappLoader{};

  std::unique_ptr<ContextHubInfo> mContextHubInfo;

  // Mutex and CV are used to get context hub info synchronously.
  std::mutex mHubInfoMutex;
  std::condition_variable mHubInfoCondition;

  // Death recipient handling clients' disconnections
  ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

  // States of settings
  std::unordered_map<Setting, bool> mSettingEnabled;
  std::optional<bool> mIsWifiAvailable;
  std::optional<bool> mIsBleAvailable;

  // A mutex to synchronize access to the list of preloaded nanoapp IDs.
  std::mutex mPreloadedNanoappIdsMutex;
  std::optional<std::vector<uint64_t>> mPreloadedNanoappIds{};

  // test mode settings
  std::mutex mTestModeMutex;
  std::condition_variable mEnableTestModeCv;
  bool mIsTestModeEnabled = false;
  std::optional<bool> mTestModeSyncUnloadResult = std::nullopt;
  // mTestModeNanoapps records the nanoapps that will be unloaded in
  // enableTestMode(). it is initialized to an empty vector to prevent it from
  // unintended population in onNanoappListResponse().
  std::optional<std::vector<uint64_t>> mTestModeNanoapps{{}};
  // mTestModeSystemNanoapps records system nanoapps that won't be reloaded in
  // disableTestMode().
  std::optional<std::vector<uint64_t>> mTestModeSystemNanoapps;

  EventLogger mEventLogger;

  // The parser of buffered logs from CHRE
  LogMessageParser mLogger;

  MetricsReporter mMetricsReporter;

  // Used to map message sequence number to host endpoint ID
  std::unordered_map<int32_t, HostEndpointId> mReliableMessageMap;
};
}  // namespace android::hardware::contexthub::common::implementation
#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_MULTICLIENTS_HAL_BASE_H_
