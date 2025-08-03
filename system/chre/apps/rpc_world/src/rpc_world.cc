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

#include "rpc_world_manager.h"

#ifdef CHRE_NANOAPP_INTERNAL
namespace chre {
namespace {
#endif  // CHRE_NANOAPP_INTERNAL

void nanoappHandleEvent(uint32_t senderInstanceId, uint16_t eventType,
                        const void *eventData) {
  RpcWorldManagerSingleton::get()->handleEvent(senderInstanceId, eventType,
                                               eventData);
}

bool nanoappStart(void) {
  RpcWorldManagerSingleton::init();
  return RpcWorldManagerSingleton::get()->start();
}

void nanoappEnd(void) {
  RpcWorldManagerSingleton::get()->end();
  RpcWorldManagerSingleton::deinit();
}

#ifdef CHRE_NANOAPP_INTERNAL
}  // namespace
}  // namespace chre

#include "chre/platform/static_nanoapp_init.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_permissions.h"

CHRE_STATIC_NANOAPP_INIT(RpcWorld, chre::kRpcWorldAppId, 0,
                         chre::NanoappPermissions::CHRE_PERMS_NONE);
#endif  // CHRE_NANOAPP_INTERNAL