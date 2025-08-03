/*
 * Copyright 2020 The Android Open Source Project
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

#include "common/contextual_callback.h"
#include "hci/address_with_type.h"
#include "l2cap/classic/security_policy.h"

namespace bluetooth {
namespace l2cap {
namespace classic {

/**
 * The interface for Security Module to implement.
 */
class SecurityEnforcementInterface {
 public:
  virtual ~SecurityEnforcementInterface() = default;

  using ResultCallback = common::ContextualOnceCallback<void(bool)>;

  /**
   * Invoked when L2CAP needs to open a channel with given security requirement. When the Security Module satisfies the
   * required security level, or cannot satisfy at all, invoke the result_callback.
   */
  virtual void Enforce(hci::AddressWithType remote, SecurityPolicy policy, ResultCallback result_callback) = 0;
};

/**
 * A default implementation which cannot satisfy any security level except
 * _SDP_ONLY_NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK.
 */
class SecurityEnforcementRejectAllImpl : public SecurityEnforcementInterface {
 public:
  void Enforce(
      hci::AddressWithType /* remote */,
      SecurityPolicy policy,
      ResultCallback result_callback) override {
    if (policy == SecurityPolicy::_SDP_ONLY_NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK) {
      result_callback.InvokeIfNotEmpty(true);
    } else {
      result_callback.InvokeIfNotEmpty(false);
    }
  }
};
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
