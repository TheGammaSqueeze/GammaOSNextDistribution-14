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

#include "l2cap/le/dynamic_channel.h"
#include "l2cap/le/internal/link.h"

namespace bluetooth {
namespace l2cap {
namespace le {
LinkOptions* DynamicChannel::GetLinkOptions() {
  return link_->GetLinkOptions();
}

Mtu DynamicChannel::GetMtu() const {
  return mtu_;
}

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
