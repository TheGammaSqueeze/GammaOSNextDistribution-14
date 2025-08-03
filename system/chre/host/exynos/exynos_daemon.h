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

#ifndef CHRE_EXYNOS_DAEMON_H_
#define CHRE_EXYNOS_DAEMON_H_

#include <atomic>
#include <thread>

#include "chre_host/fbs_daemon_base.h"
#include "chre_host/st_hal_lpma_handler.h"

namespace android {
namespace chre {

class ExynosDaemon : public FbsDaemonBase {
 public:
  ExynosDaemon();
  ~ExynosDaemon() {
    deinit();
  }

  //! EXYNOS's shared memory size for CHRE <-> AP is 4KB.
  static constexpr size_t kIpcMsgSizeMax = 4096;

  /**
   * Initializes the CHRE daemon.
   *
   * @return true on successful init
   */
  bool init();

  /**
   * Starts a socket server receive loop for inbound messages.
   */
  void run();

  void processIncomingMsgs();

  int64_t getTimeOffset(bool *success) override;

 protected:
  void loadPreloadedNanoapp(const std::string &directory,
                            const std::string &name,
                            uint32_t transactionId) override;
  void handleDaemonMessage(const uint8_t *message) override;
  bool doSendMessage(void *data, size_t length) override;

  void configureLpma(bool enabled) override {
    mLpmaHandler.enable(enabled);
  }

 private:
  static constexpr char kCommsDeviceFilename[] = "/dev/nanohub_comms";
  static constexpr int kInvalidFd = -1;

  int mCommsReadFd = kInvalidFd;
  int mCommsWriteFd = kInvalidFd;
  std::thread mIncomingMsgProcessThread;
  std::thread::native_handle_type mNativeThreadHandle;
  std::atomic<bool> mProcessThreadRunning = false;

  StHalLpmaHandler mLpmaHandler;

  //! Set to the expected transaction, fragment, app ID for loading a nanoapp.
  struct Transaction {
    uint32_t transactionId;
    uint32_t fragmentId;
    uint64_t nanoappId;
  };
  Transaction mPreloadedNanoappPendingTransaction;

  //! The mutex used to guard state between the nanoapp messaging thread
  //! and loading preloaded nanoapps.
  std::mutex mPreloadedNanoappsMutex;

  //! The condition variable used to wait for a nanoapp to finish loading.
  std::condition_variable mPreloadedNanoappsCond;

  //! Set to true when a preloaded nanoapp is pending load.
  bool mPreloadedNanoappPending;

  /**
   * Perform a graceful shutdown of the daemon
   */
  void deinit();

  /**
   * Stops the inbound message processing thread (forcibly).
   * Since the message read mechanism uses blocking system calls (poll, read),
   * and since there's no timeout on the system calls (to avoid waking the AP
   * up to handle timeouts), we forcibly terminate the thread on a daemon
   * deinit. POSIX semantics are used since the C++20 threading interface does
   * not provide an API to accomplish this.
   */
  void stopMsgProcessingThread();

  /**
   * Sends a preloaded nanoapp to CHRE.
   *
   * @param header The nanoapp header binary blob.
   * @param nanoapp The nanoapp binary blob.
   * @param transactionId The transaction ID to use when loading the app.
   * @return true if successful, false otherwise.
   */
  bool loadNanoapp(const std::vector<uint8_t> &header,
                   const std::vector<uint8_t> &nanoapp, uint32_t transactionId);

  /**
   * Loads a nanoapp using fragments.
   *
   * @param appId The ID of the nanoapp to load.
   * @param appVersion The version of the nanoapp to load.
   * @param appFlags The flags specified by the nanoapp to be loaded.
   * @param appTargetApiVersion The version of the CHRE API that the app
   * targets.
   * @param appBinary The application binary code.
   * @param appSize The size of the appBinary.
   * @param transactionId The transaction ID to use when loading.
   * @return true if successful, false otherwise.
   */
  bool sendFragmentedNanoappLoad(uint64_t appId, uint32_t appVersion,
                                 uint32_t appFlags,
                                 uint32_t appTargetApiVersion,
                                 const uint8_t *appBinary, size_t appSize,
                                 uint32_t transactionId);

  bool sendFragmentAndWaitOnResponse(uint32_t transactionId,
                                     flatbuffers::FlatBufferBuilder &builder,
                                     uint32_t fragmentId, uint64_t appId);

  /**
   * Empty signal handler to handle SIGINT
   */
  static void signalHandler(int /*signal*/) {}
};

}  // namespace chre
}  // namespace android

#endif  // CHRE_EXYNOS_DAEMON_H_
