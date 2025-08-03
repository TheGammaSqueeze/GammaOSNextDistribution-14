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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H
#define ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H

#include <aidl/android/hardware/contexthub/BnContextHub.h>
#include <log/log.h>
#include <atomic>
#include <future>
#include <map>
#include <mutex>
#include <optional>
#include <unordered_set>

#include "chre_host/napp_header.h"
#include "debug_dump_helper.h"
#include "event_logger.h"
#include "hal_chre_socket_connection.h"

namespace aidl::android::hardware::contexthub {

using ::android::chre::NanoAppBinaryHeader;

/**
 * Contains information about a preloaded nanoapp. Used when getting
 * preloaded nanoapp information from the config.
 */
struct chrePreloadedNanoappInfo {
  chrePreloadedNanoappInfo(int64_t _id, const std::string &_name,
                           const NanoAppBinaryHeader &_header)
      : id(_id), name(_name), header(_header) {}

  int64_t id;
  std::string name;
  NanoAppBinaryHeader header;
};

class ContextHub : public BnContextHub,
                   public ::android::hardware::contexthub::DebugDumpHelper,
                   public ::android::hardware::contexthub::common::
                       implementation::IChreSocketCallback {
 public:
  ContextHub()
      : mDeathRecipient(
            AIBinder_DeathRecipient_new(ContextHub::onServiceDied)) {}
  ::ndk::ScopedAStatus getContextHubs(
      std::vector<ContextHubInfo> *out_contextHubInfos) override;
  ::ndk::ScopedAStatus loadNanoapp(int32_t contextHubId,
                                   const NanoappBinary &appBinary,
                                   int32_t transactionId) override;
  ::ndk::ScopedAStatus unloadNanoapp(int32_t contextHubId, int64_t appId,
                                     int32_t transactionId) override;
  ::ndk::ScopedAStatus disableNanoapp(int32_t contextHubId, int64_t appId,
                                      int32_t transactionId) override;
  ::ndk::ScopedAStatus enableNanoapp(int32_t contextHubId, int64_t appId,
                                     int32_t transactionId) override;
  ::ndk::ScopedAStatus onSettingChanged(Setting setting, bool enabled) override;
  ::ndk::ScopedAStatus queryNanoapps(int32_t contextHubId) override;
  ::ndk::ScopedAStatus getPreloadedNanoappIds(
      int32_t contextHubId,
      std::vector<int64_t> *out_preloadedNanoappIds) override;
  ::ndk::ScopedAStatus registerCallback(
      int32_t contextHubId,
      const std::shared_ptr<IContextHubCallback> &cb) override;
  ::ndk::ScopedAStatus sendMessageToHub(
      int32_t contextHubId, const ContextHubMessage &message) override;
  ::ndk::ScopedAStatus setTestMode(bool enable) override;
  ::ndk::ScopedAStatus sendMessageDeliveryStatusToHub(
      int32_t contextHubId,
      const MessageDeliveryStatus &messageDeliveryStatus) override;
  ::ndk::ScopedAStatus onHostEndpointConnected(
      const HostEndpointInfo &in_info) override;
  ::ndk::ScopedAStatus onHostEndpointDisconnected(
      char16_t in_hostEndpointId) override;
  ::ndk::ScopedAStatus onNanSessionStateChanged(
      const NanSessionStateUpdate &in_update) override;

  void onNanoappMessage(const ::chre::fbs::NanoappMessageT &message) override;

  void onNanoappListResponse(
      const ::chre::fbs::NanoappListResponseT &response) override;

  void onTransactionResult(uint32_t transactionId, bool success) override;

  void onContextHubRestarted() override;

  void onDebugDumpData(const ::chre::fbs::DebugDumpDataT &data) override;

  void onDebugDumpComplete(
      const ::chre::fbs::DebugDumpResponseT &response) override;

  void handleServiceDeath();
  static void onServiceDied(void *cookie);

  binder_status_t dump(int fd, const char **args, uint32_t numArgs) override;

  bool requestDebugDump() override {
    return mConnection.requestDebugDump();
  }

  void debugDumpFinish() override;

  void writeToDebugFile(const char *str) override;

 private:
  /**
   * Enables test mode on the context hub. This unloads all nanoapps and puts
   * CHRE in a state that is consistent for testing.
   *
   * @return                            the status.
   */
  ::ndk::ScopedAStatus enableTestMode();

  /**
   * Disables test mode. Reverses the affects of enableTestMode() by loading all
   * preloaded nanoapps. This puts CHRE back in a normal state.
   *
   * @return                            the status.
   */
  ::ndk::ScopedAStatus disableTestMode();

  /**
   * Queries the list of loaded nanoapps in a synchronous manner.
   * The list is stored in the mQueryNanoappsInternalList variable.
   *
   * @param contextHubId                the ID of the context hub.
   * @param nanoappIdList               (out) optional out parameter that
   *                                    contains the nanoapp IDs.
   *
   * @return true                       the operation was successful.
   * @return false                      the operation was not successful.
   */
  bool queryNanoappsInternal(int32_t contextHubId,
                             std::vector<int64_t> *nanoappIdList);

  /**
   * Loads a nanoapp.
   *
   * @param appBinary                   the nanoapp binary to load.
   * @param transactionId               the transaction ID.
   *
   * @return true                       the operation was successful.
   * @return false                      the operation was not successful.
   */
  bool loadNanoappInternal(const NanoappBinary &appBinary,
                           int32_t transactionId);

  /**
   * Loads the nanoapps in a synchronous manner.
   *
   * @param contextHubId                the ID of the context hub.
   * @param nanoappBinaryList           the list of NanoappBinary's to load.
   * @return true                       the operation was successful.
   * @return false                      the operation was not successful.
   */
  bool loadNanoappsInternal(
      int32_t contextHubId,
      const std::vector<NanoappBinary> &nanoappBinaryList);

  /**
   * Unloads a nanoapp.
   *
   * @param appId                       the nanoapp ID to unload.
   * @param transactionId               the transaction ID.
   *
   * @return true                       the operation was successful.
   * @return false                      the operation was not successful.
   */
  bool unloadNanoappInternal(int64_t appId, int32_t transactionId);

  /**
   * Unloads the nanoapps in a synchronous manner.
   *
   * @param contextHubId                the ID of the context hub.
   * @param nanoappIdsToUnload          the list of nanoapp IDs to unload.
   * @return true                       the operation was successful.
   * @return false                      the operation was not successful.
   */
  bool unloadNanoappsInternal(int32_t contextHubId,
                              const std::vector<int64_t> &nanoappIdList);

  /**
   * Get the preloaded nanoapp IDs from the config file and headers. All IDs,
   * names and headers are in the same order (one nanoapp has the same index in
   * each).
   *
   * @param out_preloadedNanoapps       out parameter, the nanoapp information.
   * @param out_directory               out parameter, optional, the directory
   * that contains the nanoapps.
   * @return true                       the operation was successful.
   * @return false                      the operation was not successful.
   */
  bool getPreloadedNanoappIdsFromConfigFile(
      std::vector<chrePreloadedNanoappInfo> &out_preloadedNanoapps,
      std::string *out_directory) const;

  /**
   * Selects the nanoapps to load -> all preloaded and non-system nanoapps.
   *
   * @param preloadedNanoapps           the preloaded nanoapps.
   * @param preloadedNanoappDirectory   the preloaded nanoapp directory.
   * @return                            the nanoapps to load.
   */
  std::vector<NanoappBinary> selectPreloadedNanoappsToLoad(
      std::vector<chrePreloadedNanoappInfo> &preloadedNanoapps,
      const std::string &preloadedNanoappDirectory);

  bool isSettingEnabled(Setting setting) {
    return mSettingEnabled.count(setting) > 0 && mSettingEnabled[setting];
  }

  chre::fbs::SettingState toFbsSettingState(bool enabled) const {
    return enabled ? chre::fbs::SettingState::ENABLED
                   : chre::fbs::SettingState::DISABLED;
  }

  ::android::hardware::contexthub::common::implementation::
      HalChreSocketConnection mConnection{this};

  // A mutex to protect concurrent modifications to the callback pointer and
  // access (invocations).
  std::mutex mCallbackMutex;
  std::shared_ptr<IContextHubCallback> mCallback;

  ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

  std::map<Setting, bool> mSettingEnabled;
  std::optional<bool> mIsWifiAvailable;
  std::optional<bool> mIsBleAvailable;

  std::mutex mConnectedHostEndpointsMutex;
  std::unordered_set<char16_t> mConnectedHostEndpoints;

  // Logs events to be reported in debug dumps.
  EventLogger mEventLogger;

  // A mutex to synchronize access to the list of preloaded nanoapp IDs.
  std::mutex mPreloadedNanoappIdsMutex;
  std::optional<std::vector<int64_t>> mPreloadedNanoappIds;

  // A mutex and condition variable to synchronize queryNanoappsInternal.
  std::mutex mQueryNanoappsInternalMutex;
  std::condition_variable mQueryNanoappsInternalCondVar;
  std::optional<std::vector<NanoappInfo>> mQueryNanoappsInternalList;

  // State for synchronous loads and unloads. Primarily used for test mode.
  std::mutex mSynchronousLoadUnloadMutex;
  std::condition_variable mSynchronousLoadUnloadCondVar;
  std::optional<bool> mSynchronousLoadUnloadSuccess;
  std::optional<int32_t> mSynchronousLoadUnloadTransactionId;

  // A boolean and mutex to synchronize test mode state changes and
  // load/unloads.
  std::mutex mTestModeMutex;
  bool mIsTestModeEnabled = false;

  // List of system nanoapp Ids.
  std::vector<int64_t> mSystemNanoappIds;
};

}  // namespace aidl::android::hardware::contexthub

#endif  // ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H
