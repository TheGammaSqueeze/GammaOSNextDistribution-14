/*
 * Copyright 2024 The Android Open Source Project
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

#include "hfp_client_interface.h"

namespace bluetooth {
namespace audio {
namespace hfp {

HfpClientInterface::Decode* HfpClientInterface::GetDecode(
    bluetooth::common::MessageLoopThread* message_loop) {
  return nullptr;
}

bool HfpClientInterface::ReleaseDecode(HfpClientInterface::Decode* decode) {
  return false;
}

HfpClientInterface::Encode* HfpClientInterface::GetEncode(
    bluetooth::common::MessageLoopThread* message_loop) {
  return nullptr;
}

bool HfpClientInterface::ReleaseEncode(HfpClientInterface::Encode* encode) {
  return false;
}

HfpClientInterface::Offload* HfpClientInterface::GetOffload(
    bluetooth::common::MessageLoopThread* message_loop) {
  return nullptr;
}

bool HfpClientInterface::ReleaseOffload(HfpClientInterface::Offload* offload) {
  return false;
}

HfpClientInterface* HfpClientInterface::Get() { return nullptr; }

std::unordered_map<int, ::hfp::sco_config>
HfpClientInterface::Offload::GetHfpScoConfig() {
  return std::unordered_map<int, ::hfp::sco_config>();
}

}  // namespace hfp
}  // namespace audio
}  // namespace bluetooth
