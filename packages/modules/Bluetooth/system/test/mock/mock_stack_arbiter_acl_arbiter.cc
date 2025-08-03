/*
 * Copyright 2023 The Android Open Source Project
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

#include "stack/arbiter/acl_arbiter.h"

namespace bluetooth {
namespace shim {
namespace arbiter {

class MockAclArbiter : public AclArbiter {
 public:
  virtual void OnLeConnect(uint8_t /* tcb_idx */,
                           uint16_t /* advertiser_id */) override {}

  virtual void OnLeDisconnect(uint8_t /* tcb_idx */) override {}

  virtual InterceptAction InterceptAttPacket(
      uint8_t /* tcb_idx */, const BT_HDR* /* packet */) override {
    return InterceptAction::FORWARD;
  }

  virtual void OnOutgoingMtuReq(uint8_t /* tcb_idx */) override {}

  virtual void OnIncomingMtuResp(uint8_t /* tcb_idx */, size_t /* mtu */) {}

  virtual void OnIncomingMtuReq(uint8_t /* tcb_idx */, size_t /* mtu */) {}

  static MockAclArbiter& Get() {
    static auto singleton = MockAclArbiter();
    return singleton;
  }
};

AclArbiter& GetArbiter() {
  return static_cast<AclArbiter&>(MockAclArbiter::Get());
}

}  // namespace arbiter
}  // namespace shim
}  // namespace bluetooth
