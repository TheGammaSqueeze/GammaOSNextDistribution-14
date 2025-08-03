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

#include "rpc_service_manager.h"

#include "chre/util/macros.h"
#include "chre/util/nanoapp/log.h"

#define LOG_TAG "[RpcServiceTest]"

namespace chre {
namespace rpc_service_test {

pw::Status EchoService::Echo(const pw_rpc_EchoMessage &request,
                             pw_rpc_EchoMessage &response) {
  RpcServiceManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  memcpy(response.msg, request.msg,
         MIN(ARRAY_SIZE(response.msg), ARRAY_SIZE(request.msg)));
  return pw::OkStatus();
}

bool RpcServiceManager::start() {
  // Make sure nanoapps support publishing at least
  // CHRE_MINIMUM_RPC_SERVICE_LIMIT services.
  RpcServer::Service service{
      .service = mEchoService, .id = 0xca8f7150a3f05847, .version = 0x01020034};

  bool success = true;

  for (uint64_t i = 0; i < CHRE_MINIMUM_RPC_SERVICE_LIMIT - 1; i++) {
    struct chreNanoappRpcService chreService = {.id = i, .version = 1};
    success =
        success && chrePublishRpcServices(&chreService, 1 /*numServices*/);
  }

  return success && mServer.registerServices(1 /*numServices*/, &service);
}

void RpcServiceManager::handleEvent(uint32_t senderInstanceId,
                                    uint16_t eventType, const void *eventData) {
  if (!mServer.handleEvent(senderInstanceId, eventType, eventData)) {
    LOGE("An RPC error occurred");
  }
}

void RpcServiceManager::end() {
  mServer.close();
}

void RpcServiceManager::setPermissionForNextMessage(uint32_t permission) {
  mServer.setPermissionForNextMessage(permission);
}

}  // namespace rpc_service_test
}  // namespace chre
