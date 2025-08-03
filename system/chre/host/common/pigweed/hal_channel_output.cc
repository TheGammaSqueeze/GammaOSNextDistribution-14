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

#include "chre_host/pigweed/hal_channel_output.h"

#include <cstdint>

#include "chre/event.h"
#include "chre/util/pigweed/rpc_common.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/log.h"
#include "chre_host/socket_client.h"
#include "pw_rpc/channel.h"
#include "pw_span/span.h"

namespace android::chre {

using ::flatbuffers::FlatBufferBuilder;

size_t HalChannelOutput::MaximumTransmissionUnit() {
  return mMaxMessageLen > kFlatBufferPadding
             ? mMaxMessageLen - kFlatBufferPadding
             : 0;
}

pw::Status HalChannelOutput::Send(pw::span<const std::byte> buffer) {
  if (buffer.size() > 0) {
    FlatBufferBuilder builder(buffer.size() + kFlatBufferPadding);

    HostProtocolHost::encodeNanoappMessage(
        builder, mServerNanoappId, CHRE_MESSAGE_TYPE_RPC, mHostEndpointId,
        buffer.data(), buffer.size());

    if (!mSocketClient.sendMessage(builder.GetBufferPointer(),
                                   builder.GetSize())) {
      LOGE("Failed to send message");
      return PW_STATUS_UNKNOWN;
    }
  }

  return PW_STATUS_OK;
}

}  // namespace android::chre