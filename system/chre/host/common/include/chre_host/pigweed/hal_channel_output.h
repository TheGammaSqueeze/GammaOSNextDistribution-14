/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_PIGWEED_HAL_CHANNEL_OUTPUT_H_
#define CHRE_PIGWEED_HAL_CHANNEL_OUTPUT_H_

#include <cstdint>

#include "chre/util/non_copyable.h"
#include "chre_host/socket_client.h"
#include "pw_rpc/channel.h"
#include "pw_span/span.h"

namespace android::chre {

/**
 * RPC Channel Output for native vendor processes.
 */
class HalChannelOutput : public ::chre::NonCopyable,
                         public pw::rpc::ChannelOutput {
 public:
  HalChannelOutput(android::chre::SocketClient &client, uint32_t hostEndpointId,
                   uint64_t serverNanoappId, size_t maxMessageLen)
      : pw::rpc::ChannelOutput("CHRE"),
        mServerNanoappId(serverNanoappId),
        mHostEndpointId(hostEndpointId),
        mMaxMessageLen(maxMessageLen),
        mSocketClient(client) {}

  size_t MaximumTransmissionUnit() override;

  pw::Status Send(pw::span<const std::byte> buffer) override;

 private:
  // Padding used to encode nanoapp messages.
  static constexpr size_t kFlatBufferPadding = 88;

  const uint64_t mServerNanoappId;
  const uint32_t mHostEndpointId;
  const size_t mMaxMessageLen;
  SocketClient &mSocketClient;
};

}  // namespace android::chre

#endif  // CHRE_PIGWEED_HAL_CHANNEL_OUTPUT_H_