/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/platform/host_link.h"
#include "chre/core/event_loop_manager.h"

namespace chre {

void HostLink::flushMessagesSentByNanoapp(uint64_t /* appId */) {
  // (empty)
}

bool HostLink::sendMessage(const MessageToHost *message) {
  // Just drop the message since we don't have a real host to send to
  EventLoopManagerSingleton::get()
      ->getHostCommsManager()
      .onMessageToHostComplete(message);
  return true;
}

void HostLinkBase::sendNanConfiguration(bool enable) {
#if defined(CHRE_WIFI_SUPPORT_ENABLED) && defined(CHRE_WIFI_NAN_SUPPORT_ENABLED)
  EventLoopManagerSingleton::get()
      ->getWifiRequestManager()
      .updateNanAvailability(enable);
#else
  UNUSED_VAR(enable);
#endif
}

}  // namespace chre
