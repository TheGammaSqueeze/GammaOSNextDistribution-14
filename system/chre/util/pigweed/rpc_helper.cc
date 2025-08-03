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

#include "chre/util/pigweed/rpc_helper.h"

#include <cinttypes>

#include "chre/util/nanoapp/log.h"

#ifndef LOG_TAG
#define LOG_TAG "[RpcHelper]"
#endif  // LOG_TAG

namespace chre {

bool rpcEndpointsMatch(uint32_t expectedId, uint32_t actualId) {
  if ((expectedId & kRpcClientIdMask) != (actualId & kRpcClientIdMask)) {
    LOGE("Invalid endpoint 0x%04" PRIx32 " expected 0x%04" PRIx32, actualId,
         expectedId);
    return false;
  }

  return true;
}

bool isRpcChannelIdHost(uint32_t id) {
  return id >> 16 == 1;
}

bool isRpcChannelIdNanoapp(uint32_t id) {
  return id >> 16 == 0;
}

bool validateHostChannelId(const chreMessageFromHostData *msg,
                           uint32_t channelId) {
  struct chreHostEndpointInfo info;

  if (!isRpcChannelIdHost(channelId) ||
      !chreGetHostEndpointInfo(msg->hostEndpoint, &info)) {
    LOGE("Invalid channelId for a host client 0x%08" PRIx32, channelId);
    return false;
  }

  return rpcEndpointsMatch(channelId, static_cast<uint32_t>(msg->hostEndpoint));
}

bool validateNanoappChannelId(uint32_t nappId, uint32_t channelId) {
  if (nappId > kRpcNanoappMaxId) {
    LOGE("Invalid nanoapp Id 0x%08" PRIx32, nappId);
    return false;
  }

  if (!isRpcChannelIdNanoapp(channelId)) {
    LOGE("Invalid channelId for a nanoapp 0x%08" PRIx32, channelId);
    return false;
  }

  return rpcEndpointsMatch(channelId, nappId);
}

}  // namespace chre