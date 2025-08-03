/*
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#include "facade_configuration_api.h"

#include "common/bind.h"
#include "l2cap/classic/security_enforcement_interface.h"
#include "os/log.h"

namespace bluetooth {
namespace security {

void FacadeConfigurationApi::SetDisconnectCallback(internal::SecurityManagerImpl::FacadeDisconnectCallback callback) {
  security_handler_->CallOn(security_manager_impl_, &internal::SecurityManagerImpl::SetDisconnectCallback, callback);
}

void FacadeConfigurationApi::SetIoCapability(hci::IoCapability io_capability) {
  security_handler_->CallOn(security_manager_impl_, &internal::SecurityManagerImpl::SetIoCapability, io_capability);
}

void FacadeConfigurationApi::SetAuthenticationRequirements(hci::AuthenticationRequirements authentication_requirement) {
  security_handler_->CallOn(
      security_manager_impl_,
      &internal::SecurityManagerImpl::SetAuthenticationRequirements,
      authentication_requirement);
}

void FacadeConfigurationApi::SetLeIoCapability(security::IoCapability io_capability) {
  security_handler_->CallOn(security_manager_impl_, &internal::SecurityManagerImpl::SetLeIoCapability, io_capability);
}

void FacadeConfigurationApi::SetLeAuthRequirements(uint8_t auth_req) {
  security_handler_->CallOn(security_manager_impl_, &internal::SecurityManagerImpl::SetLeAuthRequirements, auth_req);
}

void FacadeConfigurationApi::SetLeMaximumEncryptionKeySize(uint8_t maximum_encryption_key_size) {
  security_handler_->CallOn(
      security_manager_impl_,
      &internal::SecurityManagerImpl::SetLeMaximumEncryptionKeySize,
      maximum_encryption_key_size);
}

void FacadeConfigurationApi::SetLeOobDataPresent(OobDataFlag oob_present) {
  security_handler_->CallOn(security_manager_impl_, &internal::SecurityManagerImpl::SetLeOobDataPresent, oob_present);
}

void FacadeConfigurationApi::GetLeOutOfBandData(
    std::array<uint8_t, 16>* confirmation_value, std::array<uint8_t, 16>* random_value) {
  security_manager_impl_->GetLeOutOfBandData(confirmation_value, random_value);
}

void FacadeConfigurationApi::SetOutOfBandData(
    hci::AddressWithType remote_address,
    std::array<uint8_t, 16> confirmation_value,
    std::array<uint8_t, 16> random_value) {
  security_handler_->CallOn(
      security_manager_impl_,
      &internal::SecurityManagerImpl::SetOutOfBandData,
      remote_address,
      confirmation_value,
      random_value);
}

void FacadeConfigurationApi::EnforceSecurityPolicy(
    hci::AddressWithType remote,
    l2cap::classic::SecurityPolicy policy,
    l2cap::classic::SecurityEnforcementInterface::ResultCallback callback) {
  security_handler_->CallOn(
      security_manager_impl_,
      &internal::SecurityManagerImpl::EnforceSecurityPolicy,
      remote,
      policy,
      std::move(callback));
}

}  // namespace security
}  // namespace bluetooth
