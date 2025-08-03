/*
 * Copyright 2019 The Android Open Source Project
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

#include "l2cap/dynamic_channel.h"
#include "common/bind.h"
#include "l2cap/internal/dynamic_channel_impl.h"

namespace bluetooth {
namespace l2cap {

hci::AddressWithType DynamicChannel::GetDevice() const {
  return impl_->GetDevice();
}

void DynamicChannel::RegisterOnCloseCallback(DynamicChannel::OnCloseCallback on_close_callback) {
  l2cap_handler_->CallOn(
      impl_.get(), &l2cap::internal::DynamicChannelImpl::RegisterOnCloseCallback, std::move(on_close_callback));
}

void DynamicChannel::Close() {
  l2cap_handler_->CallOn(impl_.get(), &l2cap::internal::DynamicChannelImpl::Close);
}

common::BidiQueueEnd<packet::BasePacketBuilder, packet::PacketView<packet::kLittleEndian>>*
DynamicChannel::GetQueueUpEnd() const {
  return impl_->GetQueueUpEnd();
}

Cid DynamicChannel::HACK_GetRemoteCid() {
  return impl_->GetRemoteCid();
}

void DynamicChannel::HACK_SetChannelTxPriority(bool high_priority) {
  return impl_->SetChannelTxPriority(high_priority);
}

}  // namespace l2cap
}  // namespace bluetooth
