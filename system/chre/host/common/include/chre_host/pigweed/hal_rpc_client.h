/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_PIGWEED_HAL_RPC_CLIENT_H_
#define CHRE_PIGWEED_HAL_RPC_CLIENT_H_

#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <optional>
#include <string_view>

#include <android-base/thread_annotations.h>

#include "chre/util/pigweed/rpc_common.h"
#include "chre_host/pigweed/hal_channel_output.h"

#include "chre/util/macros.h"
#include "chre/util/non_copyable.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/log.h"
#include "chre_host/pigweed/hal_channel_output.h"
#include "chre_host/socket_client.h"
#include "pw_rpc/client.h"

namespace android::chre {

/**
 * RPC client helper to use with native vendor processes.
 */
class HalRpcClient : public ::chre::NonCopyable {
 public:
  /**
   * Creates a RPC client helper.
   *
   * This method connects to the socket blocks until the initialization is
   * complete.
   *
   # @param appName Name of the app.
   * @param client A SocketClient that must not be already connected.
   * @param socketCallbacks The callbacks to call on SocketClient events.
   * @param hostEndpointId The host endpoint ID for the app.
   * @param serverNanoappId The ID of the nanoapp providing the service.
   * @return A pointer to a HalRpcClient. nullptr on error.
   */
  static std::unique_ptr<HalRpcClient> createClient(
      std::string_view appName, SocketClient &client,
      sp<SocketClient::ICallbacks> socketCallbacks, uint16_t hostEndpointId,
      uint64_t serverNanoappId);

  ~HalRpcClient() {
    close();
  }

  /**
   * Closes the RPC client and de-allocate resources.
   *
   * Note: This method is called from the destructor. However it can also be
   * called explicitly.
   */
  void close();

  /**
   * Returns a service client.
   *
   * NOTE: The template parameter must be set to the Pigweed client type,
   *       i.e. pw::rpc::pw_rpc::nanopb::<ServiceName>::Client

   * @return The service client. It has no value on errors.
   */
  template <typename T>
  std::optional<T> get();

  /**
   * Returns whether the server nanoapp supports the service.
   *
   * Also returns false when the nanoapp is not loaded.
   *
   * @return whether the service is published by the server.
   */
  bool hasService(uint64_t id, uint32_t version);

 private:
  /** Timeout for the requests to the daemon */
  static constexpr auto kRequestTimeout = std::chrono::milliseconds(2000);

  class Callbacks : public SocketClient::ICallbacks,
                    public IChreMessageHandlers {
   public:
    Callbacks(HalRpcClient *client,
              sp<SocketClient::ICallbacks> socketCallbacks)
        : mClient(client), mSocketCallbacks(socketCallbacks) {}

    /** Socket callbacks. */
    void onMessageReceived(const void *data, size_t length) override;
    void onConnected() override;
    void onConnectionAborted() override;
    void onDisconnected() override;

    /** Message handlers. */
    void handleNanoappMessage(
        const ::chre::fbs::NanoappMessageT &message) override;
    void handleHubInfoResponse(
        const ::chre::fbs::HubInfoResponseT &response) override;
    void handleNanoappListResponse(
        const ::chre::fbs::NanoappListResponseT &response) override;

   private:
    HalRpcClient *mClient;
    sp<SocketClient::ICallbacks> mSocketCallbacks;
  };

  HalRpcClient(std::string_view appName, SocketClient &client,
               uint16_t hostEndpointId, uint64_t serverNanoappId)
      : mServerNanoappId(serverNanoappId),
        mHostEndpointId(hostEndpointId),
        mAppName(appName),
        mSocketClient(client) {}

  /**
   * Initializes the RPC client helper.
   *
   * @param socketCallbacks The callbacks to call on SocketClient events.
   * @return Whether the initialization was successful.
   */
  bool init(sp<SocketClient::ICallbacks> socketCallbacks);

  /** @return the Pigweed RPC channel ID */
  uint32_t GetChannelId() {
    return ::chre::kChannelIdHostClient | mHostEndpointId;
  }

  /**
   * Notifies CHRE that the host endpoint has connected.
   *
   * Needed as the RPC Server helper will retrieve the host endpoint metadata
   * when servicing a request.
   */
  bool notifyEndpointConnected();

  /** Notifies CHRE that the host endpoint has disconnected. */
  bool notifyEndpointDisconnected();

  /** Query CHRE to retrieve the maximum message length. */
  bool retrieveMaxMessageLen();

  /** Query CHRE to retrieve the services published by the server nanoapp. */
  bool retrieveServices();

  const uint64_t mServerNanoappId;
  const uint16_t mHostEndpointId;
  const std::string mAppName;
  SocketClient &mSocketClient;
  std::unique_ptr<HalChannelOutput> mChannelOutput;
  pw::rpc::Client mRpcClient;
  bool mIsChannelOpened = false;

  /** Request Hub Info. */
  std::mutex mHubInfoMutex;
  size_t mMaxMessageLen GUARDED_BY(mHubInfoMutex);
  std::condition_variable mHubInfoCond;

  /** Request Nanoapps. */
  std::mutex mNanoappMutex;
  std::vector<::chre::fbs::NanoappRpcServiceT> mServices
      GUARDED_BY(mNanoappMutex);
  std::condition_variable mNanoappCond;
};

template <typename T>
std::optional<T> HalRpcClient::get() {
  if (mChannelOutput == nullptr) {
    LOGE("No channel output");
    return {};
  }

  if (!mIsChannelOpened) {
    mRpcClient.OpenChannel(GetChannelId(), *mChannelOutput);
    mIsChannelOpened = true;
  }

  return T(mRpcClient, GetChannelId());
}

}  // namespace android::chre

#endif  // CHRE_PIGWEED_HAL_RPC_CLIENT_H_