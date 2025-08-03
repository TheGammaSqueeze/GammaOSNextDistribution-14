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

#include "chre_host/pigweed/hal_rpc_client.h"

#include <cstdint>
#include <memory>

#include "chre/event.h"
#include "chre/util/pigweed/rpc_common.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/log.h"
#include "chre_host/pigweed/hal_channel_output.h"

namespace android::chre {

using ::chre::fbs::HubInfoResponseT;
using ::chre::fbs::NanoappListEntryT;
using ::chre::fbs::NanoappListResponseT;
using ::chre::fbs::NanoappMessageT;
using ::chre::fbs::NanoappRpcServiceT;
using ::flatbuffers::FlatBufferBuilder;

std::unique_ptr<HalRpcClient> HalRpcClient::createClient(
    std::string_view appName, SocketClient &client,
    sp<SocketClient::ICallbacks> socketCallbacks, uint16_t hostEndpointId,
    uint64_t serverNanoappId) {
  auto rpcClient = std::unique_ptr<HalRpcClient>(
      new HalRpcClient(appName, client, hostEndpointId, serverNanoappId));

  if (!rpcClient->init(socketCallbacks)) {
    return nullptr;
  }

  return rpcClient;
}

bool HalRpcClient::hasService(uint64_t id, uint32_t version) {
  std::lock_guard lock(mNanoappMutex);
  for (const NanoappRpcServiceT &service : mServices) {
    if (service.id == id && service.version == version) {
      return true;
    }
  }

  return false;
}

void HalRpcClient::close() {
  if (mIsChannelOpened) {
    mRpcClient.CloseChannel(GetChannelId());
    mIsChannelOpened = false;
  }
  if (mSocketClient.isConnected()) {
    notifyEndpointDisconnected();
    mSocketClient.disconnect();
  }
}

// Private methods

bool HalRpcClient::init(sp<SocketClient::ICallbacks> socketCallbacks) {
  if (mSocketClient.isConnected()) {
    LOGE("Already connected to socket");
    return false;
  }

  auto callbacks = sp<Callbacks>::make(this, socketCallbacks);

  if (!mSocketClient.connect("chre", callbacks)) {
    LOGE("Couldn't connect to socket");
    return false;
  }

  bool success = true;

  if (!notifyEndpointConnected()) {
    LOGE("Failed to notify connection");
    success = false;
  } else if (!retrieveMaxMessageLen()) {
    LOGE("Failed to retrieve the max message length");
    success = false;
  } else if (!retrieveServices()) {
    LOGE("Failed to retrieve the services");
    success = false;
  }

  if (!success) {
    mSocketClient.disconnect();
    return false;
  }

  {
    std::lock_guard lock(mHubInfoMutex);
    mChannelOutput = std::make_unique<HalChannelOutput>(
        mSocketClient, mHostEndpointId, mServerNanoappId, mMaxMessageLen);
  }

  return true;
}

bool HalRpcClient::notifyEndpointConnected() {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeHostEndpointConnected(
      builder, mHostEndpointId, CHRE_HOST_ENDPOINT_TYPE_NATIVE, mAppName,
      /* attributionTag= */ "");
  return mSocketClient.sendMessage(builder.GetBufferPointer(),
                                   builder.GetSize());
}

bool HalRpcClient::notifyEndpointDisconnected() {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeHostEndpointDisconnected(builder, mHostEndpointId);
  return mSocketClient.sendMessage(builder.GetBufferPointer(),
                                   builder.GetSize());
}

bool HalRpcClient::retrieveMaxMessageLen() {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeHubInfoRequest(builder);
  if (!mSocketClient.sendMessage(builder.GetBufferPointer(),
                                 builder.GetSize())) {
    return false;
  }

  std::unique_lock lock(mHubInfoMutex);
  std::cv_status status = mHubInfoCond.wait_for(lock, kRequestTimeout);

  return status != std::cv_status::timeout;
}

bool HalRpcClient::retrieveServices() {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeNanoappListRequest(builder);

  if (!mSocketClient.sendMessage(builder.GetBufferPointer(),
                                 builder.GetSize())) {
    return false;
  }

  std::unique_lock lock(mNanoappMutex);
  std::cv_status status = mNanoappCond.wait_for(lock, kRequestTimeout);

  return status != std::cv_status::timeout;
}

// Socket callbacks.

void HalRpcClient::Callbacks::onMessageReceived(const void *data,
                                                size_t length) {
  if (!android::chre::HostProtocolHost::decodeMessageFromChre(data, length,
                                                              *this)) {
    LOGE("Failed to decode message");
  }
  mSocketCallbacks->onMessageReceived(data, length);
}

void HalRpcClient::Callbacks::onConnected() {
  mSocketCallbacks->onConnected();
}

void HalRpcClient::Callbacks::onConnectionAborted() {
  mSocketCallbacks->onConnectionAborted();
}

void HalRpcClient::Callbacks::onDisconnected() {
  // Close connections on CHRE reset.
  mClient->close();
  mSocketCallbacks->onDisconnected();
}

// Message handlers.

void HalRpcClient::Callbacks::handleNanoappMessage(
    const NanoappMessageT &message) {
  if (message.message_type == CHRE_MESSAGE_TYPE_RPC) {
    pw::span packet(reinterpret_cast<const std::byte *>(message.message.data()),
                    message.message.size());

    if (message.app_id == mClient->mServerNanoappId) {
      pw::Status status = mClient->mRpcClient.ProcessPacket(packet);
      if (status != pw::OkStatus()) {
        LOGE("Failed to process the packet");
      }
    }
  }
}

void HalRpcClient::Callbacks::handleHubInfoResponse(
    const HubInfoResponseT &response) {
  {
    std::lock_guard lock(mClient->mHubInfoMutex);
    mClient->mMaxMessageLen = response.max_msg_len;
  }
  mClient->mHubInfoCond.notify_all();
}

void HalRpcClient::Callbacks::handleNanoappListResponse(
    const NanoappListResponseT &response) {
  for (const std::unique_ptr<NanoappListEntryT> &nanoapp : response.nanoapps) {
    if (nanoapp->app_id == mClient->mServerNanoappId) {
      std::lock_guard lock(mClient->mNanoappMutex);
      mClient->mServices.clear();
      mClient->mServices.reserve(nanoapp->rpc_services.size());
      for (const std::unique_ptr<NanoappRpcServiceT> &service :
           nanoapp->rpc_services) {
        mClient->mServices.push_back(*service);
      }
    }
  }

  mClient->mNanoappCond.notify_all();
}

}  // namespace android::chre