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

#ifndef CHRE_RPC_WORLD_MANAGER_H_
#define CHRE_RPC_WORLD_MANAGER_H_

#include <cinttypes>
#include <cstdint>

#include "chre/re.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/pigweed/rpc_client.h"
#include "chre/util/pigweed/rpc_server.h"
#include "chre/util/singleton.h"
#include "chre_api/chre.h"
#include "rpc_world.rpc.pb.h"

class RpcWorldService final
    : public chre::rpc::pw_rpc::nanopb::RpcWorldService::Service<
          RpcWorldService> {
 public:
  // Increment RPC unary service definition.
  // See generated IncrementService::Service for more details.
  pw::Status Increment(const chre_rpc_NumberMessage &request,
                       chre_rpc_NumberMessage &response);

  // Timer RPC server streaming service definition.
  // See generated TimerService::Service for more details.
  void Timer(const chre_rpc_TimerRequest &request,
             ServerWriter<chre_rpc_TimerResponse> &writer);

  // Add RPC client streaming service definition.
  // See generated AddService::Service for more details.
  void Add(
      ServerReader<chre_rpc_NumberMessage, chre_rpc_NumberMessage> &reader);
};

/**
 * Acts both as a RPC server and a RPC client.
 * The client calls the RPCWorld service provided by the server.
 */
class RpcWorldManager {
 public:
  /**
   * Allows the manager to do any init necessary as part of nanoappStart.
   */
  bool start();

  /**
   * Handle a CHRE event.
   *
   * @param senderInstanceId The instand ID that sent the event.
   * @param eventType The type of the event.
   * @param eventData The data for the event.
   */
  void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                   const void *eventData);

  /**
   * Allows the manager to do any cleanup necessary as part of nanoappEnd.
   */
  void end();

  /**
   * Starts the tick timer.
   *
   * @param numTicks Number of ticks to stream.
   * @param writer Used to stream the responses.
   */
  void timerStart(
      uint32_t numTicks,
      RpcWorldService::ServerWriter<chre_rpc_TimerResponse> &writer);

  /**
   * Starts a client streamed add.
   *
   * @param reader Used to read the streamed requests.
   */
  void addStart(RpcWorldService::ServerReader<chre_rpc_NumberMessage,
                                              chre_rpc_NumberMessage> &reader);

  /**
   * Sets the permission for the next server message.
   *
   * @params permission Bitmasked CHRE_MESSAGE_PERMISSION_.
   */
  void setPermissionForNextMessage(uint32_t permission);

  uint32_t mSum = 0;

 private:
  chre::RpcServer mServer;
  chre::RpcClient mClient{chre::kRpcWorldAppId};
  // pw_rpc service used to process the RPCs.
  RpcWorldService mRpcWorldService;
  RpcWorldService::ServerWriter<chre_rpc_TimerResponse> mTimerWriter;
  RpcWorldService::ServerReader<chre_rpc_NumberMessage, chre_rpc_NumberMessage>
      mAddReader;
  uint32_t mTimerId = CHRE_TIMER_INVALID;
  uint32_t mTimerCurrentTick;
  uint32_t mTimerTotalTicks;
  pw::rpc::NanopbUnaryReceiver<chre_rpc_NumberMessage> mIncrementCall;
  pw::rpc::NanopbClientReader<chre_rpc_TimerResponse> mTimerCall;
  pw::rpc::NanopbClientWriter<chre_rpc_NumberMessage, chre_rpc_NumberMessage>
      mAddCall;
};

typedef chre::Singleton<RpcWorldManager> RpcWorldManagerSingleton;

#endif  // CHRE_RPC_WORLD_MANAGER_H_
