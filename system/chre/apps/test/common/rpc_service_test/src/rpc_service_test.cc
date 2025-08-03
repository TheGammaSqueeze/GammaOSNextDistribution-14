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

#include "rpc_service_manager.h"

namespace chre {
namespace rpc_service_test {

extern "C" void nanoappHandleEvent(uint32_t senderInstanceId,
                                   uint16_t eventType, const void *eventData) {
  RpcServiceManagerSingleton::get()->handleEvent(senderInstanceId, eventType,
                                                 eventData);
}

extern "C" bool nanoappStart(void) {
  RpcServiceManagerSingleton::init();
  return RpcServiceManagerSingleton::get()->start();
}

extern "C" void nanoappEnd(void) {
  RpcServiceManagerSingleton::get()->end();
  RpcServiceManagerSingleton::deinit();
}

}  // namespace rpc_service_test
}  // namespace chre
