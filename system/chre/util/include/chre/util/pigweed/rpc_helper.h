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

#ifndef CHRE_UTIL_PIGWEED_RPC_HELPER_H_
#define CHRE_UTIL_PIGWEED_RPC_HELPER_H_

#include <cstdint>

#include "chre/util/pigweed/rpc_common.h"
#include "chre_api/chre.h"

namespace chre {

/**
 * Returns whether the endpoint matches.
 *
 * Only the lower 16 bits are considered.
 *
 * @param expectedId Expected channel ID.
 * @param actualId Actual channel ID.
 * @return whether the nanoapp IDs match (lower 16b).
 */
bool rpcEndpointsMatch(uint32_t expectedId, uint32_t actualId);

/**
 * @param id Channel ID.
 * @return whether the channel ID is a host client.
 */
bool isRpcChannelIdHostClient(uint32_t id);

/**
 * @param id Channel ID.
 * @return whether the channel ID is a nanoapp client.
 */
bool isRpcChannelIdNanoappClient(uint32_t id);

/**
 * Validates that the host client sending the message matches the expected
 * channel ID.
 *
 * @param msg Message received from the host client.
 * @param channelId Channel ID extracted from the received packet.
 * @return Whether the IDs match.
 */
bool validateHostChannelId(const chreMessageFromHostData *msg,
                           uint32_t channelId);

/**
 * Validates that the nanoapp sending the message matches the expected
 * channel ID.
 *
 * @param senderInstanceId ID of the nanoapp sending the message.
 * @param channelId Channel ID extracted from the received packet.
 * @return Whether the IDs match.
 */
bool validateNanoappChannelId(uint32_t nappId, uint32_t channelId);

}  // namespace chre

#endif  // CHRE_UTIL_PIGWEED_RPC_HELPER_H_
