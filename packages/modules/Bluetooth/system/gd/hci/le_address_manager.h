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

#include <map>
#include <variant>

#include "common/callback.h"
#include "hci/address_with_type.h"
#include "hci/octets.h"
#include "os/alarm.h"

namespace bluetooth {
namespace hci {

constexpr std::chrono::milliseconds kUnregisterSyncTimeoutInMs = std::chrono::milliseconds(10);

class LeAddressManagerCallback {
 public:
  virtual ~LeAddressManagerCallback() = default;
  virtual void OnPause() = 0;
  virtual void OnResume() = 0;
  virtual void NotifyOnIRKChange(){};
};

class LeAddressManager {
 public:
  LeAddressManager(
      common::Callback<void(std::unique_ptr<CommandBuilder>)> enqueue_command,
      os::Handler* handler,
      Address public_address,
      uint8_t accept_list_size,
      uint8_t resolving_list_size);
  virtual ~LeAddressManager();

  enum AddressPolicy {
    POLICY_NOT_SET,
    USE_PUBLIC_ADDRESS,
    USE_STATIC_ADDRESS,
    USE_NON_RESOLVABLE_ADDRESS,
    USE_RESOLVABLE_ADDRESS
  };

  // Aborts if called more than once
  void SetPrivacyPolicyForInitiatorAddress(
      AddressPolicy address_policy,
      AddressWithType fixed_address,
      Octet16 rotation_irk,
      bool supports_ble_privacy,
      std::chrono::milliseconds minimum_rotation_time,
      std::chrono::milliseconds maximum_rotation_time);
  // TODO(jpawlowski): remove once we have config file abstraction in cert tests
  void SetPrivacyPolicyForInitiatorAddressForTest(
      AddressPolicy address_policy,
      AddressWithType fixed_address,
      Octet16 rotation_irk,
      std::chrono::milliseconds minimum_rotation_time,
      std::chrono::milliseconds maximum_rotation_time);
  AddressPolicy GetAddressPolicy();
  bool RotatingAddress();
  virtual void AckPause(LeAddressManagerCallback* callback);
  virtual void AckResume(LeAddressManagerCallback* callback);
  virtual AddressPolicy Register(LeAddressManagerCallback* callback);
  virtual void Unregister(LeAddressManagerCallback* callback);
  virtual bool UnregisterSync(
      LeAddressManagerCallback* callback,
      std::chrono::milliseconds timeout = kUnregisterSyncTimeoutInMs);
  virtual AddressWithType GetInitiatorAddress();      // What was set in SetRandomAddress()
  virtual AddressWithType NewResolvableAddress();     // A new random address without rotating.
  virtual AddressWithType NewNonResolvableAddress();  // A new non-resolvable address

  uint8_t GetFilterAcceptListSize();
  uint8_t GetResolvingListSize();
  void AddDeviceToFilterAcceptList(FilterAcceptListAddressType accept_list_address_type, Address address);
  void AddDeviceToResolvingList(
      PeerAddressType peer_identity_address_type,
      Address peer_identity_address,
      const std::array<uint8_t, 16>& peer_irk,
      const std::array<uint8_t, 16>& local_irk);
  void RemoveDeviceFromFilterAcceptList(FilterAcceptListAddressType accept_list_address_type, Address address);
  void RemoveDeviceFromResolvingList(PeerAddressType peer_identity_address_type, Address peer_identity_address);
  void ClearFilterAcceptList();
  void ClearResolvingList();
  void OnCommandComplete(CommandCompleteView view);
  std::chrono::milliseconds GetNextPrivateAddressIntervalMs();

  // Unsynchronized check for testing purposes
  size_t NumberCachedCommands() const {
    return cached_commands_.size();
  }

 protected:
  AddressPolicy address_policy_ = AddressPolicy::POLICY_NOT_SET;
  std::chrono::milliseconds minimum_rotation_time_;
  std::chrono::milliseconds maximum_rotation_time_;

 private:
  enum ClientState {
    WAITING_FOR_PAUSE,
    PAUSED,
    WAITING_FOR_RESUME,
    RESUMED,
  };

  enum CommandType {
    ROTATE_RANDOM_ADDRESS,
    ADD_DEVICE_TO_ACCEPT_LIST,
    REMOVE_DEVICE_FROM_ACCEPT_LIST,
    CLEAR_ACCEPT_LIST,
    ADD_DEVICE_TO_RESOLVING_LIST,
    REMOVE_DEVICE_FROM_RESOLVING_LIST,
    CLEAR_RESOLVING_LIST,
    SET_ADDRESS_RESOLUTION_ENABLE,
    LE_SET_PRIVACY_MODE,
    UPDATE_IRK,
  };

  struct RotateRandomAddressCommand {};

  struct UpdateIRKCommand {
    Octet16 rotation_irk;
    std::chrono::milliseconds minimum_rotation_time;
    std::chrono::milliseconds maximum_rotation_time;
  };

  struct HCICommand {
    std::unique_ptr<CommandBuilder> command;
  };

  struct Command {
    CommandType command_type;  // Note that this field is only intended for logging, not control flow
    std::variant<RotateRandomAddressCommand, UpdateIRKCommand, HCICommand> contents;
  };

  void pause_registered_clients();
  void push_command(Command command);
  void ack_pause(LeAddressManagerCallback* callback);
  void resume_registered_clients();
  void ack_resume(LeAddressManagerCallback* callback);
  void register_client(LeAddressManagerCallback* callback);
  void unregister_client(LeAddressManagerCallback* callback);
  void prepare_to_rotate();
  void rotate_random_address();
  void schedule_rotate_random_address();
  void set_random_address();
  void prepare_to_update_irk(UpdateIRKCommand command);
  void update_irk(UpdateIRKCommand command);
  hci::Address generate_rpa();
  hci::Address generate_nrpa();
  void handle_next_command();
  void check_cached_commands();
  template <class View>
  void on_command_complete(CommandCompleteView view);

  common::Callback<void(std::unique_ptr<CommandBuilder>)> enqueue_command_;
  os::Handler* handler_;
  std::map<LeAddressManagerCallback*, ClientState> registered_clients_;

  AddressWithType le_address_;
  AddressWithType cached_address_;
  Address public_address_;
  std::unique_ptr<os::Alarm> address_rotation_alarm_;
  Octet16 rotation_irk_;
  uint8_t accept_list_size_;
  uint8_t resolving_list_size_;
  std::queue<Command> cached_commands_;
  bool supports_ble_privacy_{false};
};

}  // namespace hci
}  // namespace bluetooth
