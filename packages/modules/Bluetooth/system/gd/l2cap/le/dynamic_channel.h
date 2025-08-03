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

#pragma once

#include "l2cap/dynamic_channel.h"
#include "l2cap/le/link_options.h"
#include "l2cap/mtu.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {
class Link;
}

class DynamicChannel : public l2cap::DynamicChannel {
 public:
  DynamicChannel(
      std::shared_ptr<l2cap::internal::DynamicChannelImpl> impl,
      os::Handler* l2cap_handler,
      internal::Link* link,
      Mtu mtu)
      : l2cap::DynamicChannel(impl, l2cap_handler), link_(link), mtu_(mtu) {}

  /**
   * Get the Proxy for L2CAP Link Options.
   * Only few special L2CAP users need to use it, including
   * Hearing Aid Profile and Java API.
   */
  LinkOptions* GetLinkOptions();

  Mtu GetMtu() const;

 private:
  internal::Link* link_;
  Mtu mtu_;
};

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
