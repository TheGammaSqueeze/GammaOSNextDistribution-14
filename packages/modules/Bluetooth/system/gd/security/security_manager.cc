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
#include "security_manager.h"

#include "hci/octets.h"

namespace bluetooth {
namespace security {

// Definition of Pure Virtual Destructor
ISecurityManagerListener::~ISecurityManagerListener() {}

void SecurityManager::Init() {
  security_handler_->Post(
      common::BindOnce(&internal::SecurityManagerImpl::Init, common::Unretained(security_manager_impl_)));
}

void SecurityManager::CreateBond(hci::AddressWithType device) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::CreateBond,
                                           common::Unretained(security_manager_impl_),
                                           std::forward<hci::AddressWithType>(device)));
}

void SecurityManager::CreateBondOutOfBand(
    hci::AddressWithType device, pairing::OobData remote_p192_oob_data, pairing::OobData remote_p256_oob_data) {
  security_handler_->Post(common::BindOnce(
      &internal::SecurityManagerImpl::CreateBondOutOfBand,
      common::Unretained(security_manager_impl_),
      std::forward<hci::AddressWithType>(device),
      remote_p192_oob_data,
      remote_p256_oob_data));
}

void SecurityManager::GetOutOfBandData(channel::SecurityCommandStatusCallback callback) {
  security_handler_->Post(common::BindOnce(
      &internal::SecurityManagerImpl::GetOutOfBandData,
      common::Unretained(security_manager_impl_),
      std::forward<channel::SecurityCommandStatusCallback>(callback)));
}

void SecurityManager::CreateBondLe(hci::AddressWithType device) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::CreateBondLe,
                                           common::Unretained(security_manager_impl_),
                                           std::forward<hci::AddressWithType>(device)));
}

void SecurityManager::CancelBond(hci::AddressWithType device) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::CancelBond,
                                           common::Unretained(security_manager_impl_),
                                           std::forward<hci::AddressWithType>(device)));
}

void SecurityManager::RemoveBond(hci::AddressWithType device) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::RemoveBond,
                                           common::Unretained(security_manager_impl_),
                                           std::forward<hci::AddressWithType>(device)));
}

void SecurityManager::SetUserInterfaceHandler(UI* user_interface, os::Handler* handler) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::SetUserInterfaceHandler,
                                           common::Unretained(security_manager_impl_), user_interface, handler));
}

// TODO(jpawlowski): remove once we have config file abstraction in cert tests
void SecurityManager::SetLeInitiatorAddressPolicyForTest(
    hci::LeAddressManager::AddressPolicy address_policy,
    hci::AddressWithType fixed_address,
    hci::Octet16 rotation_irk,
    std::chrono::milliseconds minimum_rotation_time,
    std::chrono::milliseconds maximum_rotation_time) {
  security_handler_->Post(common::BindOnce(
      &internal::SecurityManagerImpl::SetLeInitiatorAddressPolicyForTest,
      common::Unretained(security_manager_impl_),
      address_policy,
      fixed_address,
      rotation_irk,
      minimum_rotation_time,
      maximum_rotation_time));
}

void SecurityManager::RegisterCallbackListener(ISecurityManagerListener* listener, os::Handler* handler) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::RegisterCallbackListener,
                                           common::Unretained(security_manager_impl_), listener, handler));
}

void SecurityManager::UnregisterCallbackListener(ISecurityManagerListener* listener) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::UnregisterCallbackListener,
                                           common::Unretained(security_manager_impl_), listener));
}

void SecurityManager::OnPairingPromptAccepted(const bluetooth::hci::AddressWithType& address, bool confirmed) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::OnPairingPromptAccepted,
                                           common::Unretained(security_manager_impl_), address, confirmed));
}

void SecurityManager::OnConfirmYesNo(const bluetooth::hci::AddressWithType& address, bool confirmed) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::OnConfirmYesNo,
                                           common::Unretained(security_manager_impl_), address, confirmed));
}

void SecurityManager::OnPasskeyEntry(const bluetooth::hci::AddressWithType& address, uint32_t passkey) {
  security_handler_->Post(common::BindOnce(&internal::SecurityManagerImpl::OnPasskeyEntry,
                                           common::Unretained(security_manager_impl_), address, passkey));
}

void SecurityManager::OnPinEntry(const bluetooth::hci::AddressWithType& address, std::vector<uint8_t> pin) {
  security_handler_->Post(common::BindOnce(
      &internal::SecurityManagerImpl::OnPinEntry, common::Unretained(security_manager_impl_), address, std::move(pin)));
}

}  // namespace security
}  // namespace bluetooth
