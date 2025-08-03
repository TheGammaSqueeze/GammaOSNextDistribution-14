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

#include <functional>
#include <future>
#include <memory>

#include "hci/acl_manager/connection_callbacks.h"
#include "hci/acl_manager/le_acceptlist_callbacks.h"
#include "hci/acl_manager/le_connection_callbacks.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/distance_measurement_manager.h"
#include "hci/hci_packets.h"
#include "hci/le_address_manager.h"
#include "hci/le_scanning_manager.h"
#include "module.h"
#include "os/handler.h"

namespace bluetooth {
namespace security {
class SecurityModule;
}  // namespace security
namespace shim {
namespace legacy {
class Acl;
}  // namespace legacy

class Btm;
bool L2CA_SetAclPriority(uint16_t, bool);
}  // namespace shim

namespace hci {

class AclManager : public Module {
  friend class bluetooth::shim::Btm;
  friend class bluetooth::shim::legacy::Acl;
  friend bool bluetooth::shim::L2CA_SetAclPriority(uint16_t, bool);
  friend class bluetooth::hci::LeScanningManager;
  friend class bluetooth::hci::DistanceMeasurementManager;

 public:
  AclManager();
  AclManager(const AclManager&) = delete;
  AclManager& operator=(const AclManager&) = delete;

  // NOTE: It is necessary to forward declare a default destructor that
  // overrides the base class one, because "struct impl" is forwarded declared
  // in .cc and compiler needs a concrete definition of "struct impl" when
  // compiling AclManager's destructor. Hence we need to forward declare the
  // destructor for AclManager to delay compiling AclManager's destructor until
  // it starts linking the .cc file.
  ~AclManager();

  // Should register only once when user module starts.
  // Generates OnConnectSuccess when an incoming connection is established.
  virtual void RegisterCallbacks(acl_manager::ConnectionCallbacks* callbacks, os::Handler* handler);
  virtual void UnregisterCallbacks(
      acl_manager::ConnectionCallbacks* callbacks, std::promise<void> promise);

  // Should register only once when user module starts.
  virtual void RegisterLeCallbacks(
      acl_manager::LeConnectionCallbacks* callbacks, os::Handler* handler);
  virtual void UnregisterLeCallbacks(
      acl_manager::LeConnectionCallbacks* callbacks, std::promise<void> promise);
  void RegisterLeAcceptlistCallbacks(acl_manager::LeAcceptlistCallbacks* callbacks);
  void UnregisterLeAcceptlistCallbacks(
      acl_manager::LeAcceptlistCallbacks* callbacks, std::promise<void> promise);

  // Generates OnConnectSuccess if connected, or OnConnectFail otherwise
  virtual void CreateConnection(Address address);

  // Generates OnLeConnectSuccess if connected, or OnLeConnectFail otherwise
  virtual void CreateLeConnection(AddressWithType address_with_type, bool is_direct);

  // Ask the controller for specific data parameters
  virtual void SetLeSuggestedDefaultDataParameters(uint16_t octets, uint16_t time);

  virtual void LeSetDefaultSubrate(
      uint16_t subrate_min,
      uint16_t subrate_max,
      uint16_t max_latency,
      uint16_t cont_num,
      uint16_t sup_tout);

  virtual void SetPrivacyPolicyForInitiatorAddress(
      LeAddressManager::AddressPolicy address_policy,
      AddressWithType fixed_address,
      std::chrono::milliseconds minimum_rotation_time,
      std::chrono::milliseconds maximum_rotation_time);

  // TODO(jpawlowski): remove once we have config file abstraction in cert tests
  virtual void SetPrivacyPolicyForInitiatorAddressForTest(
      LeAddressManager::AddressPolicy address_policy,
      AddressWithType fixed_address,
      Octet16 rotation_irk,
      std::chrono::milliseconds minimum_rotation_time,
      std::chrono::milliseconds maximum_rotation_time);

  // Generates OnConnectFail with error code "terminated by local host 0x16" if
  // cancelled, or OnConnectSuccess if not successfully cancelled and already
  // connected
  virtual void CancelConnect(Address address);
  virtual void RemoveFromBackgroundList(AddressWithType address_with_type);
  virtual void IsOnBackgroundList(AddressWithType address_with_type, std::promise<bool> promise);

  virtual void CancelLeConnect(AddressWithType address_with_type);

  virtual void ClearFilterAcceptList();

  virtual void AddDeviceToResolvingList(
      AddressWithType address_with_type,
      const std::array<uint8_t, 16>& peer_irk,
      const std::array<uint8_t, 16>& local_irk);
  virtual void RemoveDeviceFromResolvingList(AddressWithType address_with_type);
  virtual void ClearResolvingList();

  virtual void CentralLinkKey(KeyFlag key_flag);
  virtual void SwitchRole(Address address, Role role);
  virtual uint16_t ReadDefaultLinkPolicySettings();
  virtual void WriteDefaultLinkPolicySettings(uint16_t default_link_policy_settings);

  // Callback from Advertising Manager to notify the advitiser (local) address
  virtual void OnAdvertisingSetTerminated(
      ErrorCode status,
      uint16_t conn_handle,
      uint8_t adv_set_id,
      hci::AddressWithType adv_address,
      bool is_discoverable);

  // In order to avoid circular dependency use setter rather than module
  // dependency.
  virtual void SetSecurityModule(security::SecurityModule* security_module);

  virtual LeAddressManager* GetLeAddressManager();

  // Virtual ACL disconnect emitted during suspend.
  virtual void OnClassicSuspendInitiatedDisconnect(uint16_t handle, ErrorCode reason);
  virtual void OnLeSuspendInitiatedDisconnect(uint16_t handle, ErrorCode reason);
  virtual void SetSystemSuspendState(bool suspended);

  static const ModuleFactory Factory;

 protected:
  void ListDependencies(ModuleList* list) const override;

  void Start() override;

  void Stop() override;

  std::string ToString() const override;

  DumpsysDataFinisher GetDumpsysData(
      flatbuffers::FlatBufferBuilder* builder) const override;  // Module

 private:
  virtual uint16_t HACK_GetHandle(const Address address);
  virtual uint16_t HACK_GetLeHandle(const Address address);

  virtual void HACK_SetAclTxPriority(uint8_t handle, bool high_priority);

  struct impl;
  std::unique_ptr<impl> pimpl_;
};

}  // namespace hci
}  // namespace bluetooth
