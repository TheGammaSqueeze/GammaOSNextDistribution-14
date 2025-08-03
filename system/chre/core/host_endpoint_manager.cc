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

#include "chre/core/host_endpoint_manager.h"

#include "chre/core/event_loop_manager.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/system/event_callbacks.h"

namespace chre {
bool HostEndpointManager::isHostEndpointConnected(uint16_t hostEndpointId,
                                                  size_t *index) {
  for (size_t i = 0; i < mHostEndpoints.size(); i++) {
    if (mHostEndpoints[i].hostEndpointId == hostEndpointId) {
      *index = i;
      return true;
    }
  }

  return false;
}

void HostEndpointManager::hostNotificationCallback(uint16_t type, void *data,
                                                   void *extraData) {
  uint16_t hostEndpointId = NestedDataPtr<uint16_t>(data);

  SystemCallbackType callbackType = static_cast<SystemCallbackType>(type);
  if (callbackType == SystemCallbackType::HostEndpointDisconnected) {
    size_t index;
    if (isHostEndpointConnected(hostEndpointId, &index)) {
      mHostEndpoints.erase(index);

      uint16_t eventType = CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION;
      auto *eventData = memoryAlloc<struct chreHostEndpointNotification>();

      if (eventData == nullptr) {
        LOG_OOM();
      } else {
        eventData->hostEndpointId = hostEndpointId;
        eventData->notificationType =
            HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT;
        eventData->reserved = 0;

        EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
            eventType, eventData, freeEventDataCallback, kBroadcastInstanceId);
      }
    } else {
      LOGW("Got disconnected event for nonexistent host endpoint ID %" PRIu16,
           hostEndpointId);
    }
  } else {
    auto *info = static_cast<struct chreHostEndpointInfo *>(extraData);

    size_t index;
    if (!isHostEndpointConnected(hostEndpointId, &index)) {
      mHostEndpoints.push_back(*info);
    } else {
      LOGW("Got connected event for already existing host endpoint ID %" PRIu16,
           hostEndpointId);
    }
  }

  memoryFree(extraData);
}

auto HostEndpointManager::getHostNotificationCallback() {
  return [](uint16_t type, void *data, void *extraData) {
    EventLoopManagerSingleton::get()
        ->getHostEndpointManager()
        .hostNotificationCallback(type, data, extraData);
  };
}

bool HostEndpointManager::getHostEndpointInfo(
    uint16_t hostEndpointId, struct chreHostEndpointInfo *info) {
  size_t index;
  if (isHostEndpointConnected(hostEndpointId, &index)) {
    *info = mHostEndpoints[index];
    return true;
  } else {
    return false;
  }
}

void HostEndpointManager::postHostEndpointConnected(
    const struct chreHostEndpointInfo &info) {
  auto *infoData = memoryAlloc<struct chreHostEndpointInfo>();
  if (infoData == nullptr) {
    LOG_OOM();
  } else {
    memcpy(infoData, &info, sizeof(struct chreHostEndpointInfo));

    auto callback = getHostNotificationCallback();

    EventLoopManagerSingleton::get()->deferCallback(
        SystemCallbackType::HostEndpointConnected,
        NestedDataPtr<uint16_t>(info.hostEndpointId), callback,
        infoData /* extraData */);
  }
}

void HostEndpointManager::postHostEndpointDisconnected(
    uint16_t hostEndpointId) {
  auto callback = getHostNotificationCallback();
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::HostEndpointDisconnected,
      NestedDataPtr<uint16_t>(hostEndpointId), callback, nullptr);
}

}  // namespace chre
