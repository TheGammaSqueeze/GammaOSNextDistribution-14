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

#include <unordered_map>

#include "l2cap/cid.h"
#include "l2cap/classic/internal/link.h"
#include "l2cap/classic/security_policy.h"
#include "l2cap/internal/dynamic_channel_impl.h"
#include "l2cap/internal/sender.h"
#include "l2cap/psm.h"
#include "os/handler.h"
#include "os/log.h"

namespace bluetooth {
namespace l2cap {
namespace internal {

DynamicChannelImpl::DynamicChannelImpl(Psm psm, Cid cid, Cid remote_cid, l2cap::internal::ILink* link,
                                       os::Handler* l2cap_handler)
    : psm_(psm), cid_(cid), remote_cid_(remote_cid), link_(link), l2cap_handler_(l2cap_handler),
      device_(link->GetDevice()) {
  ASSERT(cid_ > 0);
  ASSERT(remote_cid_ > 0);
  ASSERT(link_ != nullptr);
  ASSERT(l2cap_handler_ != nullptr);
}

hci::AddressWithType DynamicChannelImpl::GetDevice() const {
  return device_;
}

void DynamicChannelImpl::RegisterOnCloseCallback(DynamicChannel::OnCloseCallback on_close_callback) {
  ASSERT_LOG(on_close_callback_.IsEmpty(), "OnCloseCallback can only be registered once");
  // If channel is already closed, call the callback immediately without saving it
  if (closed_) {
    on_close_callback.Invoke(close_reason_);
    return;
  }
  on_close_callback_ = std::move(on_close_callback);
}

void DynamicChannelImpl::Close() {
  if (link_ == nullptr) {
    LOG_ERROR("Channel is already closed");
    return;
  }
  link_->SendDisconnectionRequest(cid_, remote_cid_);
}

void DynamicChannelImpl::OnClosed(hci::ErrorCode status) {
  ASSERT_LOG(
      !closed_,
      "Device %s Cid 0x%x closed twice, old status 0x%x, new status 0x%x",
      ADDRESS_TO_LOGGABLE_CSTR(device_),
      cid_,
      static_cast<int>(close_reason_),
      static_cast<int>(status));
  closed_ = true;
  close_reason_ = status;
  link_ = nullptr;
  l2cap_handler_ = nullptr;
  on_close_callback_.InvokeIfNotEmpty(close_reason_);
  on_close_callback_ = {};
}

}  // namespace internal
}  // namespace l2cap
}  // namespace bluetooth
