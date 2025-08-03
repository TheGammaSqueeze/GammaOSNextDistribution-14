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
#include "security/l2cap_security_module_interface.h"
#include "common/bind.h"

namespace bluetooth {
namespace security {

L2capSecurityModuleInterface::L2capSecurityModuleInterface(internal::SecurityManagerImpl* security_manager_impl,
                                                           os::Handler* security_handler)
    : security_manager_impl_(security_manager_impl), security_handler_(security_handler) {}

void L2capSecurityModuleInterface::Enforce(
    hci::AddressWithType remote, l2cap::classic::SecurityPolicy policy,
    l2cap::classic::SecurityEnforcementInterface::ResultCallback result_callback) {
  this->security_handler_->Post(common::BindOnce(
      &internal::SecurityManagerImpl::EnforceSecurityPolicy, common::Unretained(security_manager_impl_),
      std::forward<hci::AddressWithType>(remote), std::forward<l2cap::classic::SecurityPolicy>(policy),
      std::forward<l2cap::classic::SecurityEnforcementInterface::ResultCallback>(result_callback)));
}

void L2capSecurityModuleInterface::Enforce(hci::AddressWithType remote, l2cap::le::SecurityPolicy policy,
                                           l2cap::le::SecurityEnforcementInterface::ResultCallback result_callback) {
  this->security_handler_->Post(common::BindOnce(
      &internal::SecurityManagerImpl::EnforceLeSecurityPolicy, common::Unretained(security_manager_impl_),
      std::forward<hci::AddressWithType>(remote), std::forward<l2cap::le::SecurityPolicy>(policy),
      std::forward<l2cap::le::SecurityEnforcementInterface::ResultCallback>(result_callback)));
}

}  // namespace security
}  // namespace bluetooth
