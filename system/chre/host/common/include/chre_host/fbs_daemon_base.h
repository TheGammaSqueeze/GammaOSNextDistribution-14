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

#ifndef CHRE_FBS_DAEMON_BASE_H_
#define CHRE_FBS_DAEMON_BASE_H_

/**
 * @file fbs_daemon_base.h
 * This header defines a base class for all CHRE daemon variants that use
 * flatbuffers as the codec scheme for communicating with CHRE.
 */

#include "chre_host/daemon_base.h"
#include "chre_host/host_protocol_host.h"

namespace android {
namespace chre {

class FbsDaemonBase : public ChreDaemonBase {
 public:
  virtual ~FbsDaemonBase() {}

  /**
   * Send a message to CHRE
   *
   * @param clientId The client ID that this message originates from.
   * @param data The data to pass down.
   * @param length The size of the data to send.
   * @return true if successful, false otherwise.
   */
  bool sendMessageToChre(uint16_t clientId, void *data,
                         size_t dataLen) override;

  /**
   * Enables or disables LPMA (low power microphone access).
   */
  virtual void configureLpma(bool enabled) = 0;

 protected:
  /**
   * Loads a nanoapp by sending the nanoapp filename to the CHRE framework. This
   * method will return after sending the request so no guarantee is made that
   * the nanoapp is loaded until after the response is received.
   *
   * @param appId The ID of the nanoapp to load.
   * @param appVersion The version of the nanoapp to load.
   * @param appTargetApiVersion The version of the CHRE API that the app
   * targets.
   * @param appBinaryName The name of the binary as stored in the filesystem.
   * This will be used to load the nanoapp into CHRE.
   * @param transactionId The transaction ID to use when loading.
   * @return true if a request was successfully sent, false otherwise.
   */
  bool sendNanoappLoad(uint64_t appId, uint32_t appVersion,
                       uint32_t appTargetApiVersion,
                       const std::string &appBinaryName,
                       uint32_t transactionId) override;

  /**
   * Send a time sync message to CHRE
   *
   * @param logOnError If true, logs an error message on failure.
   *
   * @return true if the time sync message was successfully sent to CHRE.
   */
  bool sendTimeSync(bool logOnError) override;

  /**
   * Interface to a callback that is called when the Daemon receives a message.
   *
   * @param message A buffer containing the message
   * @param messageLen size of the message buffer in bytes
   */
  void onMessageReceived(const unsigned char *message,
                         size_t messageLen) override;

  /**
   * Handles a message that is directed towards the daemon.
   *
   * @param message The message sent to the daemon.
   */
  void handleDaemonMessage(const uint8_t *message) override;

  /**
   * Platform-specific method to actually do the message sending requested by
   * sendMessageToChre.
   *
   * @return true if message was sent successfully, false otherwise.
   */
  virtual bool doSendMessage(void *data, size_t dataLen) = 0;

 private:
  //! Contains a set of transaction IDs and app IDs used to load the preloaded
  //! nanoapps. The IDs are stored in the order they are sent.
  std::queue<Transaction> mPreloadedNanoappPendingTransactions;
};

}  // namespace chre
}  // namespace android

#endif  // CHRE_FBS_DAEMON_BASE_H_
