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

#include "chre/util/pigweed/rpc_client.h"

#include <cinttypes>
#include <cstdint>

#include "chre/util/macros.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/pigweed/rpc_helper.h"
#include "chre_api/chre.h"

#ifndef LOG_TAG
#define LOG_TAG "[RpcClient]"
#endif  // LOG_TAG

namespace chre {

bool RpcClient::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                            const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_RPC_RESPONSE:
      return handleMessageFromServer(senderInstanceId, eventData);
    case CHRE_EVENT_NANOAPP_STOPPED:
      handleNanoappStopped(eventData);
      return true;
  }

  return true;
}

bool RpcClient::hasService(uint64_t id, uint32_t version) {
  struct chreNanoappInfo info;
  if (!chreGetNanoappInfoByAppId(mServerNanoappId, &info)) {
    return false;
  }

  for (uint32_t i = 0; i < info.rpcServiceCount; i++) {
    if (info.rpcServices[i].id == id) {
      return info.rpcServices[i].version == version;
    }
  }

  return false;
}

void RpcClient::close() {
  chreConfigureNanoappInfoEvents(false);
}

bool RpcClient::handleMessageFromServer(uint32_t senderInstanceId,
                                        const void *eventData) {
  auto data = static_cast<const chre::ChrePigweedNanoappMessage *>(eventData);
  pw::span packet(reinterpret_cast<const std::byte *>(data->msg),
                  data->msgSize);
  struct chreNanoappInfo info;

  if (!chreGetNanoappInfoByAppId(mServerNanoappId, &info) ||
      info.instanceId > kRpcNanoappMaxId) {
    return false;
  }

  if (!validateNanoappChannelId(senderInstanceId, info.instanceId)) {
    return false;
  }

  pw::Status status = mRpcClient.ProcessPacket(packet);

  if (status != pw::OkStatus()) {
    LOGE("Failed to process the packet");
    return false;
  }

  return true;
}

void RpcClient::handleNanoappStopped(const void *eventData) {
  auto info = static_cast<const struct chreNanoappInfo *>(eventData);

  if (info->instanceId > kRpcNanoappMaxId) {
    LOGE("Invalid nanoapp Id 0x%08" PRIx32, info->instanceId);
    return;
  }

  if (info->instanceId == mChannelId) {
    mRpcClient.CloseChannel(mChannelId);
    mChannelId = 0;
  }
}

}  // namespace chre