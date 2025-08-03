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

#ifndef CHRE_UTIL_PIGWEED_RPC_CLIENT_H_
#define CHRE_UTIL_PIGWEED_RPC_CLIENT_H_

#include <cstdint>

#include "chre/util/non_copyable.h"
#include "chre/util/optional.h"
#include "chre/util/pigweed/chre_channel_output.h"
#include "chre/util/unique_ptr.h"
#include "chre_api/chre.h"
#include "pw_rpc/client.h"
#include "pw_span/span.h"
#include "rpc_helper.h"

namespace chre {

/**
 * RPC Client wrapping a Pigweed RPC client.
 *
 * This helper class handles Pigweed RPC calls on the client side.
 *
 * The `handleEvent` method must be called at the beginning of the
 * `nanoappHandleEvent` function to handle RPC responses from the server.
 */
class RpcClient : public NonCopyable {
 public:
  /**
   * @param serverNanoappId Nanoapp ID of the server.
   */
  explicit RpcClient(uint64_t serverNanoappId)
      : mServerNanoappId((serverNanoappId)) {}

  /**
   * Handles events related to RPC services.
   *
   * Handles the following events:
   * - CHRE_EVENT_RPC_RESPONSE: handle the server responses,
   * - CHRE_EVENT_NANOAPP_STOPPED: close the channel when the server nanoapp
   *   terminates.
   *
   * @param senderInstanceId The Instance ID for the source of this event.
   * @param eventType The event type.
   * @param eventData The associated data, if any, for this specific type of
   *                  event.
   * @return whether any event was handled successfully.
   */
  bool handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                   const void *eventData);

  /**
   * Returns a service client.
   *
   * NOTE: The template parameter must be set to the Pigweed client type,
   *       i.e. pw::rpc::pw_rpc::nanopb::<ServiceName>::Client

   * @return The service client. It has no value on errors.
   */
  template <typename T>
  Optional<T> get();

  /**
   * Returns whether the server nanoapp supports the service.
   *
   * Also returns false when the nanoapp is not loaded.
   *
   * @return whether the service is published by the server.
   */
  bool hasService(uint64_t id, uint32_t version);

  /**
   * Must be called from nanoapp end.
   */
  void close();

 private:
  /**
   * Handles responses from the server.
   *
   * This method must be called when nanoapps receive a CHRE_EVENT_RPC_RESPONSE
   * event.
   *
   * @param senderInstanceId The Instance ID for the source of this event.
   * @param eventData  The associated data, if any.
   * @return whether the RPC was handled successfully.
   */
  bool handleMessageFromServer(uint32_t senderInstanceId,
                               const void *eventData);

  /**
   * Closes the Pigweed channel when the server terminates.
   *
   * @param notification The eventData associated to a
   *    CHRE_EVENT_NANOAPP_STOPPED event.
   */
  void handleNanoappStopped(const void *eventData);

  ChreClientNanoappChannelOutput mChannelOutput;
  pw::rpc::Client mRpcClient;
  uint64_t mServerNanoappId;
  uint32_t mChannelId = 0;
};

template <typename T>
Optional<T> RpcClient::get() {
  if (mChannelId == 0) {
    struct chreNanoappInfo info;

    if (!chreGetNanoappInfoByAppId(mServerNanoappId, &info) ||
        info.instanceId > kRpcNanoappMaxId) {
      return Optional<T>();
    }

    mChannelId = chreGetInstanceId();
    mChannelOutput.setServer(info.instanceId);
    mRpcClient.OpenChannel(mChannelId, mChannelOutput);
  }

  chreConfigureNanoappInfoEvents(true);
  return T(mRpcClient, mChannelId);
}

}  // namespace chre

#endif  // CHRE_UTIL_PIGWEED_RPC_SERVER_H_