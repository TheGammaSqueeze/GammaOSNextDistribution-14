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

#include "l2cap/classic/security_enforcement_interface.h"
#include "l2cap/le/security_enforcement_interface.h"
#include "os/handler.h"
#include "security/internal/security_manager_impl.h"

namespace bluetooth {
namespace security {
class L2capSecurityModuleInterface : public l2cap::classic::SecurityEnforcementInterface,
                                     public l2cap::le::SecurityEnforcementInterface {
 public:
  L2capSecurityModuleInterface(internal::SecurityManagerImpl* security_manager_impl, os::Handler* security_handler);
  void Enforce(hci::AddressWithType remote, l2cap::classic::SecurityPolicy policy,
               l2cap::classic::SecurityEnforcementInterface::ResultCallback result_callback) override;
  void Enforce(hci::AddressWithType remote, l2cap::le::SecurityPolicy policy,
               l2cap::le::SecurityEnforcementInterface::ResultCallback result_callback) override;

 private:
  internal::SecurityManagerImpl* security_manager_impl_;
  os::Handler* security_handler_ = nullptr;
};

}  // namespace security
}  // namespace bluetooth
