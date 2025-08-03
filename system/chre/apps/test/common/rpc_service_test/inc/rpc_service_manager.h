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

#ifndef CHRE_RPC_SERVICE_MANAGER_H_
#define CHRE_RPC_SERVICE_MANAGER_H_

#include <cinttypes>
#include <cstdint>

#include "chre/util/macros.h"
#include "chre/util/pigweed/chre_channel_output.h"
#include "chre/util/pigweed/rpc_server.h"
#include "chre/util/singleton.h"
#include "chre_api/chre.h"
#include "echo.rpc.pb.h"

namespace chre {
namespace rpc_service_test {

class EchoService final
    : public pw::rpc::pw_rpc::nanopb::EchoService::Service<EchoService> {
 public:
  // Echo RPC service definition. See auto-generated EchoService::Service for
  // more details.
  pw::Status Echo(const pw_rpc_EchoMessage &request,
                  pw_rpc_EchoMessage &response);
};

/**
 * Class to manage the CHRE rpc service nanoapp.
 */
class RpcServiceManager {
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
   * Cleanup on nanoapp end.
   */
  void end();

  /**
   * Sets the permission for the next server message.
   *
   * @params permission Bitmasked CHRE_MESSAGE_PERMISSION_.
   */
  void setPermissionForNextMessage(uint32_t permission);

 private:
  RpcServer mServer;
  // pw_rpc service used to process the echo RPC
  EchoService mEchoService;
};

typedef chre::Singleton<RpcServiceManager> RpcServiceManagerSingleton;

}  // namespace rpc_service_test
}  // namespace chre

#endif  // CHRE_RPC_SERVICE_MANAGER_H_
