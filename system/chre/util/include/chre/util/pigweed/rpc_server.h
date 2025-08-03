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

#ifndef CHRE_UTIL_PIGWEED_RPC_SERVER_H_
#define CHRE_UTIL_PIGWEED_RPC_SERVER_H_

#include "chre/util/dynamic_vector.h"
#include "chre/util/macros.h"
#include "chre/util/non_copyable.h"
#include "chre/util/pigweed/chre_channel_output.h"
#include "chre/util/pigweed/permission.h"
#include "pw_rpc/server.h"
#include "pw_rpc/service.h"
#include "pw_span/span.h"

namespace chre {

/**
 * RPC Server wrapping a Pigweed RPC server.
 *
 * This helper class handles Pigweed RPC calls on the server side.
 *
 * The services must be registered from the `nanoappStart` function using
 * the `registerService` method.
 *
 * The `handleEvent` method must be called at the beginning of the
 * `nanoappHandleEvent` function to respond to RPC calls from the clients.
 */
class RpcServer : public NonCopyable {
 public:
  struct Service {
    /** A Pigweed service. */
    pw::rpc::Service &service;
    /**
     * The ID of the service, it must be generated according to RFC 4122, UUID
     * version 4 (random). This ID must be unique within a given nanoapp.
     */
    uint64_t id;

    /**
     * The version of the service. It should be in sync with the version on the
     * client side.
     */
    uint32_t version;
  };

  RpcServer() : mHostOutput(mPermission), mNanoappOutput(mPermission) {}

  /**
   * Registers services to the server and to CHRE.
   *
   * This method must be called from `nanoappStart`.
   *
   * Although nanoapps are recommended to only call this API once with all
   * services it intends to publish, if it is called multiple times, each
   * call will append to the list of published services.
   *
   * @param numServices The number of services to register.
   * @param services Service definitions.
   * @return whether the registration was successful.
   */
  bool registerServices(size_t numServices, Service *services);

  /**
   * Sets the permission for the next message to a client.
   *
   * While the permission are only actually used for messages to host clients,
   * the value passed here only applies to the next message whether the client
   * is a host client or a nanoapp.
   *
   * This method must be called:
   * - from the body of a service implementation method in the case of unary
   *   RPCs,
   * - right before invoking ServerReader.Finish for client streaming and
   *   bidirectional streaming RPCs,
   * - right before invoking ServerWriter.Write and ServerWriter.Finish for
   *   server streaming and bidirectional streaming RPCs.
   *
   * @params permission Bitmasked CHRE_MESSAGE_PERMISSION_.
   *
   * @see chreSendMessageWithPermissions
   */
  void setPermissionForNextMessage(uint32_t permission);

  /**
   * Handles events related to RPC services.
   *
   * Handles the following events:
   * - CHRE_EVENT_MESSAGE_FROM_HOST: respond to host RPC requests,
   * - CHRE_EVENT_RPC_REQUEST: respond to nanoapp RPC requests,
   * - CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION: close the channel when the host
   *   terminates,
   * - CHRE_EVENT_NANOAPP_STOPPED: close the channel when a nanoapp
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
   * Close all connections to the server.
   *
   * Must be called from the nanoapp end.
   */
  void close();

 private:
  /**
   * Handles messages from host clients.
   *
   * This method must be called when nanoapps receive a
   * CHRE_EVENT_MESSAGE_FROM_HOST event.
   *
   * @param eventData  The associated data, if any.
   * @return whether the RPC was handled successfully.
   */
  bool handleMessageFromHost(const void *eventData);

  /**
   * Handles messages from nanoapp clients.
   *
   * This method must be called when nanoapps receive a CHRE_EVENT_RPC_REQUEST
   * event.
   *
   * @param eventData  The associated data, if any.
   * @return whether the RPC was handled successfully.
   */
  bool handleMessageFromNanoapp(uint32_t senderInstanceId,
                                const void *eventData);

  /**
   * Closes the Pigweed channel when a host client disconnects.
   *
   * @param notification The notification from the host client
   */
  void handleHostClientNotification(const void *eventData);

  /**
   * Closes the Pigweed channel when a nanoapp client disconnects.
   *
   * @param notification The eventData associated to a
   *    CHRE_EVENT_NANOAPP_STOPPED event.
   */
  void handleNanoappStopped(const void *eventData);

  /**
   * Closes a Pigweed channel.
   *
   * @param id The channel ID.
   * @return either an ok or not found status if the channel was not opened.
   */
  pw::Status closeChannel(uint32_t id);

  // Permission for the next message sent by mServer.
  RpcPermission mPermission;

  pw::rpc::Server mServer;

  ChreServerHostChannelOutput mHostOutput;
  ChreServerNanoappChannelOutput mNanoappOutput;

  // Host endpoints for the connected clients.
  DynamicVector<uint16_t> mConnectedHosts;
};

}  // namespace chre

#endif  // CHRE_UTIL_PIGWEED_RPC_SERVER_H_