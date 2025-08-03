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
#include "security_manager_impl.h"

#include <android_bluetooth_sysprop.h>

#include "common/bind.h"
#include "hci/address_with_type.h"
#include "hci/octets.h"
#include "os/log.h"
#include "os/rand.h"
#include "security/initial_informations.h"
#include "security/internal/security_manager_impl.h"
#include "security/pairing_handler_le.h"
#include "security/security_manager_listener.h"
#include "security/ui.h"
#include "storage/config_keys.h"

namespace bluetooth {
namespace security {
namespace internal {

void SecurityManagerImpl::DispatchPairingHandler(
    std::shared_ptr<record::SecurityRecord> record,
    bool locally_initiated,
    hci::IoCapability io_capability,
    hci::AuthenticationRequirements auth_requirements,
    pairing::OobData remote_p192_oob_data,
    pairing::OobData remote_p256_oob_data) {
  common::OnceCallback<void(hci::Address, PairingResultOrFailure)> callback =
      common::BindOnce(&SecurityManagerImpl::OnPairingHandlerComplete, common::Unretained(this));
  auto entry = pairing_handler_map_.find(record->GetPseudoAddress()->GetAddress());
  if (entry != pairing_handler_map_.end()) {
    LOG_WARN("Device already has a pairing handler, and is in the middle of pairing!");
    return;
  }
  std::shared_ptr<pairing::PairingHandler> pairing_handler = nullptr;
  switch (record->GetPseudoAddress()->GetAddressType()) {
    case hci::AddressType::PUBLIC_DEVICE_ADDRESS: {
      pairing_handler = std::make_shared<security::pairing::ClassicPairingHandler>(
          security_manager_channel_,
          record,
          security_handler_,
          std::move(callback),
          user_interface_,
          user_interface_handler_,
          record->GetPseudoAddress()->ToString(),
          name_db_module_);
      break;
    }
    default:
      ASSERT_LOG(false, "Pairing type %hhu not implemented!", record->GetPseudoAddress()->GetAddressType());
  }
  auto new_entry = std::pair<hci::Address, std::shared_ptr<pairing::PairingHandler>>(
      record->GetPseudoAddress()->GetAddress(), pairing_handler);
  pairing_handler_map_.insert(std::move(new_entry));
  pairing_handler->Initiate(
      locally_initiated, io_capability, auth_requirements, remote_p192_oob_data, remote_p256_oob_data);
}

void SecurityManagerImpl::Init() {
  security_manager_channel_->SetChannelListener(this);
  security_manager_channel_->SendCommand(hci::WriteSimplePairingModeBuilder::Create(hci::Enable::ENABLED));
  security_manager_channel_->SendCommand(hci::WriteSecureConnectionsHostSupportBuilder::Create(hci::Enable::ENABLED));

  ASSERT_LOG(storage_module_ != nullptr, "Storage module must not be null!");
  security_database_.LoadRecordsFromStorage();

  auto irk_prop =
      storage_module_->GetBin(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IRK);
  if (!irk_prop.has_value()) {
    auto rand16 = bluetooth::os::GenerateRandom<16>();
    std::vector<uint8_t> new_irk{rand16.begin(), rand16.end()};
    storage_module_->SetBin(
        BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IRK, new_irk);
    irk_prop =
        storage_module_->GetBin(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IRK);
  }

  Address controllerAddress = controller_->GetMacAddress();
  auto address_prop =
      storage_module_->GetProperty(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_ADDRESS);
  if (!address_prop || address_prop.value() != controllerAddress.ToString()) {
    storage_module_->SetProperty(
        BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_ADDRESS, controllerAddress.ToString());
  }

  local_identity_address_ =
      hci::AddressWithType(controllerAddress, hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  irk_prop =
      storage_module_->GetBin(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IRK);
  ASSERT_LOG(irk_prop.has_value(), "Irk not found in storage");
  ASSERT_LOG(irk_prop->size() == 16, "Irk corrupted in storage");
  std::copy(irk_prop->begin(), irk_prop->end(), local_identity_resolving_key_.data());

  hci::LeAddressManager::AddressPolicy address_policy = hci::LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS;
  hci::AddressWithType address_with_type(hci::Address{}, hci::AddressType::RANDOM_DEVICE_ADDRESS);

  /* Default to 7 minutes minimum, 15 minutes maximum for random address refreshing;
   * device can override. */
  auto minimum_rotation_time = std::chrono::minutes(
      GET_SYSPROP(Ble, random_address_rotation_interval_min, 7));
  auto maximum_rotation_time = std::chrono::minutes(
      GET_SYSPROP(Ble, random_address_rotation_interval_max, 15));

  acl_manager_->SetPrivacyPolicyForInitiatorAddress(
      address_policy, address_with_type, minimum_rotation_time, maximum_rotation_time);
}

void SecurityManagerImpl::CreateBond(hci::AddressWithType device) {
  this->CreateBondOutOfBand(device, pairing::OobData(), pairing::OobData());
}

void SecurityManagerImpl::CreateBondOutOfBand(
    hci::AddressWithType device, pairing::OobData remote_p192_oob_data, pairing::OobData remote_p256_oob_data) {
  auto record = security_database_.FindOrCreate(device);
  if (record->IsPaired()) {
    // Bonded means we saved it, but the caller doesn't care
    // Bonded will always mean paired
    NotifyDeviceBonded(device);
  } else {
    if (!record->IsPairing()) {
      // Dispatch pairing handler, if we are calling create we are the initiator
      LOG_WARN("Dispatch #1");
      DispatchPairingHandler(
          record,
          true,
          this->local_io_capability_,
          this->local_authentication_requirements_,
          remote_p192_oob_data,
          remote_p256_oob_data);
    }
  }
}

void SecurityManagerImpl::CreateBondLe(hci::AddressWithType address) {
  auto record = security_database_.FindOrCreate(address);
  if (record->IsPaired()) {
    NotifyDeviceBondFailed(address, PairingFailure("Already bonded"));
    return;
  }

  pending_le_pairing_.address_ = address;

  LeFixedChannelEntry* stored_chan = FindStoredLeChannel(address);
  if (stored_chan) {
    // We are already connected
    ConnectionIsReadyStartPairing(stored_chan);
    return;
  }

  l2cap_manager_le_->ConnectServices(
      address, common::BindOnce(&SecurityManagerImpl::OnConnectionFailureLe, common::Unretained(this)),
      security_handler_);
}

void SecurityManagerImpl::CancelBond(hci::AddressWithType device) {
  auto entry = pairing_handler_map_.find(device.GetAddress());
  if (entry != pairing_handler_map_.end()) {
    auto cancel_me = entry->second;
    pairing_handler_map_.erase(entry);
    cancel_me->Cancel();
  }

  auto record = security_database_.FindOrCreate(device);
  record->CancelPairing();

  WipeLePairingHandler();
}

void SecurityManagerImpl::RemoveBond(hci::AddressWithType device) {
  CancelBond(device);
  security_manager_channel_->Disconnect(device.GetAddress());
  security_database_.Remove(device);
  security_manager_channel_->SendCommand(hci::DeleteStoredLinkKeyBuilder::Create(
      device.GetAddress(), hci::DeleteStoredLinkKeyDeleteAllFlag::SPECIFIED_BD_ADDR));
  NotifyDeviceUnbonded(device);
}

void SecurityManagerImpl::SetUserInterfaceHandler(UI* user_interface, os::Handler* handler) {
  if (user_interface_ != nullptr || user_interface_handler_ != nullptr) {
    LOG_ALWAYS_FATAL("Listener has already been registered!");
  }
  user_interface_ = user_interface;
  user_interface_handler_ = handler;
}

// TODO(jpawlowski): remove once we have config file abstraction in cert tests
void SecurityManagerImpl::SetLeInitiatorAddressPolicyForTest(
    hci::LeAddressManager::AddressPolicy address_policy,
    hci::AddressWithType fixed_address,
    hci::Octet16 rotation_irk,
    std::chrono::milliseconds minimum_rotation_time,
    std::chrono::milliseconds maximum_rotation_time) {
  acl_manager_->SetPrivacyPolicyForInitiatorAddressForTest(
      address_policy, fixed_address, rotation_irk, minimum_rotation_time, maximum_rotation_time);
}

void SecurityManagerImpl::RegisterCallbackListener(ISecurityManagerListener* listener, os::Handler* handler) {
  for (auto it = listeners_.begin(); it != listeners_.end(); ++it) {
    if (it->first == listener) {
      LOG_ALWAYS_FATAL("Listener has already been registered!");
    }
  }

  listeners_.push_back({listener, handler});
}

void SecurityManagerImpl::UnregisterCallbackListener(ISecurityManagerListener* listener) {
  for (auto it = listeners_.begin(); it != listeners_.end(); ++it) {
    if (it->first == listener) {
      listeners_.erase(it);
      return;
    }
  }

  LOG_ALWAYS_FATAL("Listener has not been registered!");
}

void SecurityManagerImpl::NotifyDeviceBonded(hci::AddressWithType device) {
  for (auto& iter : listeners_) {
    iter.second->Post(common::Bind(&ISecurityManagerListener::OnDeviceBonded, common::Unretained(iter.first), device));
  }
}

void SecurityManagerImpl::NotifyDeviceBondFailed(hci::AddressWithType device, PairingFailure status) {
  for (auto& iter : listeners_) {
    iter.second->Post(
        common::Bind(&ISecurityManagerListener::OnDeviceBondFailed, common::Unretained(iter.first), device, status));
  }
}

void SecurityManagerImpl::NotifyDeviceUnbonded(hci::AddressWithType device) {
  for (auto& iter : listeners_) {
    iter.second->Post(
        common::Bind(&ISecurityManagerListener::OnDeviceUnbonded, common::Unretained(iter.first), device));
  }
  acl_manager_->CancelLeConnect(device);
}

void SecurityManagerImpl::NotifyEncryptionStateChanged(hci::EncryptionChangeView encryption_change_view) {
  for (auto& iter : listeners_) {
    iter.second->Post(common::Bind(&ISecurityManagerListener::OnEncryptionStateChanged, common::Unretained(iter.first),
                                   encryption_change_view));
  }
}

template <class T>
void SecurityManagerImpl::HandleEvent(T packet) {
  ASSERT(packet.IsValid());
  auto entry = pairing_handler_map_.find(packet.GetBdAddr());

  if (entry == pairing_handler_map_.end()) {
    auto bd_addr = packet.GetBdAddr();
    auto event_code = packet.GetEventCode();

    if (event_code != hci::EventCode::LINK_KEY_REQUEST && event_code != hci::EventCode::PIN_CODE_REQUEST &&
        event_code != hci::EventCode::IO_CAPABILITY_RESPONSE) {
      LOG_ERROR(
          "No classic pairing handler for device '%s' ready for command %s ",
          ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
          hci::EventCodeText(event_code).c_str());
      return;
    }

    auto device = storage_module_->GetDeviceByClassicMacAddress(bd_addr);

    auto record =
        security_database_.FindOrCreate(hci::AddressWithType{bd_addr, hci::AddressType::PUBLIC_DEVICE_ADDRESS});
    LOG_WARN("Dispatch #2");
    DispatchPairingHandler(
        record,
        false,
        this->local_io_capability_,
        this->local_authentication_requirements_,
        pairing::OobData(),
        pairing::OobData());
    entry = pairing_handler_map_.find(bd_addr);
  }
  entry->second->OnReceive(packet);
}

void SecurityManagerImpl::OnHciEventReceived(hci::EventView packet) {
  auto event = hci::EventView::Create(packet);
  ASSERT_LOG(event.IsValid(), "Received invalid packet");
  const hci::EventCode code = event.GetEventCode();
  switch (code) {
    case hci::EventCode::PIN_CODE_REQUEST:
      HandleEvent<hci::PinCodeRequestView>(hci::PinCodeRequestView::Create(event));
      break;
    case hci::EventCode::LINK_KEY_REQUEST:
      HandleEvent(hci::LinkKeyRequestView::Create(event));
      break;
    case hci::EventCode::LINK_KEY_NOTIFICATION:
      HandleEvent(hci::LinkKeyNotificationView::Create(event));
      break;
    case hci::EventCode::IO_CAPABILITY_REQUEST:
      HandleEvent(hci::IoCapabilityRequestView::Create(event));
      break;
    case hci::EventCode::IO_CAPABILITY_RESPONSE:
      HandleEvent(hci::IoCapabilityResponseView::Create(event));
      break;
    case hci::EventCode::SIMPLE_PAIRING_COMPLETE:
      HandleEvent(hci::SimplePairingCompleteView::Create(event));
      break;
    case hci::EventCode::REMOTE_OOB_DATA_REQUEST:
      HandleEvent(hci::RemoteOobDataRequestView::Create(event));
      break;
    case hci::EventCode::USER_PASSKEY_NOTIFICATION:
      HandleEvent(hci::UserPasskeyNotificationView::Create(event));
      break;
    case hci::EventCode::KEYPRESS_NOTIFICATION:
      HandleEvent(hci::KeypressNotificationView::Create(event));
      break;
    case hci::EventCode::USER_CONFIRMATION_REQUEST:
      HandleEvent(hci::UserConfirmationRequestView::Create(event));
      break;
    case hci::EventCode::USER_PASSKEY_REQUEST:
      HandleEvent(hci::UserPasskeyRequestView::Create(event));
      break;

    case hci::EventCode::ENCRYPTION_CHANGE: {
      EncryptionChangeView encryption_change_view = EncryptionChangeView::Create(event);
      if (!encryption_change_view.IsValid()) {
        LOG_ERROR("Invalid EncryptionChange packet received");
        return;
      }
      if (encryption_change_view.GetConnectionHandle() == pending_le_pairing_.connection_handle_) {
        pending_le_pairing_.handler_->OnHciEvent(event);
        return;
      }
      NotifyEncryptionStateChanged(encryption_change_view);
      break;
    }

    default:
      ASSERT_LOG(false, "Cannot handle received packet: %s", hci::EventCodeText(code).c_str());
      break;
  }
}

void SecurityManagerImpl::OnConnectionClosed(hci::Address address) {
  auto entry = pairing_handler_map_.find(address);
  if (entry != pairing_handler_map_.end()) {
    LOG_INFO("Cancelling pairing handler for '%s'", ADDRESS_TO_LOGGABLE_CSTR(address));
    entry->second->Cancel();
  }
  auto record = security_database_.FindOrCreate(hci::AddressWithType(address, hci::AddressType::PUBLIC_DEVICE_ADDRESS));
  if (record->IsTemporary()) {
    security_database_.Remove(hci::AddressWithType(address, hci::AddressType::PUBLIC_DEVICE_ADDRESS));
  }
  if (this->facade_disconnect_callback_) {
    this->security_handler_->Call(
        *this->facade_disconnect_callback_, hci::AddressWithType(address, hci::AddressType::PUBLIC_DEVICE_ADDRESS));
  }
}

void SecurityManagerImpl::OnHciLeEvent(hci::LeMetaEventView event) {
  hci::SubeventCode code = event.GetSubeventCode();

  if (code == hci::SubeventCode::LONG_TERM_KEY_REQUEST) {
    hci::LeLongTermKeyRequestView le_long_term_key_request_view = hci::LeLongTermKeyRequestView::Create(event);
    if (!le_long_term_key_request_view.IsValid()) {
      LOG_ERROR("Invalid LeLongTermKeyRequestView packet received");
      return;
    }

    if (le_long_term_key_request_view.GetConnectionHandle() == pending_le_pairing_.connection_handle_) {
      pending_le_pairing_.handler_->OnHciLeEvent(event);
      return;
    }

    LOG_INFO("Unhandled HCI LE security event, code %s", hci::SubeventCodeText(code).c_str());
    return;
  }

  // hci::SubeventCode::READ_LOCAL_P256_PUBLIC_KEY_COMPLETE,
  // hci::SubeventCode::GENERATE_DHKEY_COMPLETE,
  LOG_ERROR("Unhandled HCI LE security event, code %s", hci::SubeventCodeText(code).c_str());
}

void SecurityManagerImpl::OnPairingPromptAccepted(const bluetooth::hci::AddressWithType& address, bool confirmed) {
  auto entry = pairing_handler_map_.find(address.GetAddress());
  if (entry != pairing_handler_map_.end()) {
    entry->second->OnPairingPromptAccepted(address, confirmed);
  } else {
    if (pending_le_pairing_.address_ == address) {
      pending_le_pairing_.handler_->OnUiAction(PairingEvent::UI_ACTION_TYPE::PAIRING_ACCEPTED, confirmed);
    }
  }
}

void SecurityManagerImpl::OnConfirmYesNo(const bluetooth::hci::AddressWithType& address, bool confirmed) {
  auto entry = pairing_handler_map_.find(address.GetAddress());
  if (entry != pairing_handler_map_.end()) {
    entry->second->OnConfirmYesNo(address, confirmed);
  } else {
    if (pending_le_pairing_.address_ == address) {
      pending_le_pairing_.handler_->OnUiAction(PairingEvent::UI_ACTION_TYPE::CONFIRM_YESNO, confirmed);
    }
  }
}

void SecurityManagerImpl::OnPasskeyEntry(const bluetooth::hci::AddressWithType& address, uint32_t passkey) {
  auto entry = pairing_handler_map_.find(address.GetAddress());
  if (entry != pairing_handler_map_.end()) {
    entry->second->OnPasskeyEntry(address, passkey);
  } else {
    if (pending_le_pairing_.address_ == address) {
      pending_le_pairing_.handler_->OnUiAction(PairingEvent::UI_ACTION_TYPE::PASSKEY, passkey);
    }
  }
}

void SecurityManagerImpl::OnPinEntry(const bluetooth::hci::AddressWithType& address, std::vector<uint8_t> pin) {
  auto entry = pairing_handler_map_.find(address.GetAddress());
  if (entry != pairing_handler_map_.end()) {
    LOG_INFO("PIN for %s", ADDRESS_TO_LOGGABLE_CSTR(address));
    entry->second->OnPinEntry(address, pin);
  } else {
    LOG_WARN("No handler found for PIN for %s", ADDRESS_TO_LOGGABLE_CSTR(address));
    // TODO(jpawlowski): Implement LE version
  }
}

void SecurityManagerImpl::OnPairingHandlerComplete(hci::Address address, PairingResultOrFailure status) {
  auto entry = pairing_handler_map_.find(address);
  if (entry != pairing_handler_map_.end()) {
    pairing_handler_map_.erase(entry);
    security_manager_channel_->Release(address);
  }
  auto remote = hci::AddressWithType(address, hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  if (!std::holds_alternative<PairingFailure>(status)) {
    NotifyDeviceBonded(remote);
  } else {
    NotifyDeviceBondFailed(remote, std::get<PairingFailure>(status));
  }
  auto record = this->security_database_.FindOrCreate(remote);
  record->CancelPairing();
  security_database_.SaveRecordsToStorage();
  // Only call update link if we need to
  auto policy_callback_entry = enforce_security_policy_callback_map_.find(remote);
  if (policy_callback_entry != enforce_security_policy_callback_map_.end()) {
    UpdateLinkSecurityCondition(remote);
  }
}

void SecurityManagerImpl::OnL2capRegistrationCompleteLe(
    l2cap::le::FixedChannelManager::RegistrationResult result,
    [[maybe_unused]] std::unique_ptr<l2cap::le::FixedChannelService> le_smp_service) {
  ASSERT_LOG(
      result == bluetooth::l2cap::le::FixedChannelManager::RegistrationResult::SUCCESS,
      "Failed to register to LE SMP Fixed Channel Service");
}

LeFixedChannelEntry* SecurityManagerImpl::FindStoredLeChannel(const hci::AddressWithType& device) {
  for (LeFixedChannelEntry& storage : all_channels_) {
    if (storage.channel_->GetDevice() == device) {
      return &storage;
    }
  }
  return nullptr;
}

bool SecurityManagerImpl::EraseStoredLeChannel(const hci::AddressWithType& device) {
  for (auto it = all_channels_.begin(); it != all_channels_.end(); it++) {
    if (it->channel_->GetDevice() == device) {
      all_channels_.erase(it);
      return true;
    }
  }
  return false;
}

void SecurityManagerImpl::OnSmpCommandLe(hci::AddressWithType device) {
  LeFixedChannelEntry* stored_chan = FindStoredLeChannel(device);
  if (!stored_chan) {
    LOG_ALWAYS_FATAL("Received SMP command for unknown channel");
    return;
  }

  std::unique_ptr<l2cap::le::FixedChannel>& channel = stored_chan->channel_;

  auto packet = channel->GetQueueUpEnd()->TryDequeue();
  if (!packet) {
    LOG_ERROR("Received dequeue, but no data ready...");
    return;
  }

  // Pending pairing - pass the data to the handler
  auto temp_cmd_view = CommandView::Create(*packet);
  if (pending_le_pairing_.address_ == device) {
    pending_le_pairing_.handler_->OnCommandView(temp_cmd_view);
    return;
  }

  // no pending pairing attempt
  if (!temp_cmd_view.IsValid()) {
    LOG_ERROR("Invalid Command packet");
    return;
  }

  if (temp_cmd_view.GetCode() == Code::SECURITY_REQUEST) {
    // TODO: either start encryption or pairing
    LOG_WARN("Unhandled security request!!!");
    return;
  }

  auto my_role = channel->GetLinkOptions()->GetRole();
  if (temp_cmd_view.GetCode() == Code::PAIRING_REQUEST && my_role == hci::Role::PERIPHERAL) {
    // TODO: if (pending_le_pairing_) { do not start another }

    LOG_INFO("start of security request handling!");

    stored_chan->channel_->Acquire();

    PairingRequestView pairing_request = PairingRequestView::Create(temp_cmd_view);
    auto& enqueue_buffer = stored_chan->enqueue_buffer_;

    std::optional<InitialInformations::out_of_band_data> remote_oob_data = std::nullopt;
    if (remote_oob_data_address_.has_value() && remote_oob_data_address_.value() == channel->GetDevice())
      remote_oob_data = InitialInformations::out_of_band_data{.le_sc_c = remote_oob_data_le_sc_c_.value(),
                                                              .le_sc_r = remote_oob_data_le_sc_r_.value()};

    // TODO: this doesn't have to be a unique ptr, if there is a way to properly std::move it into place where it's
    // stored
    pending_le_pairing_.connection_handle_ = channel->GetLinkOptions()->GetHandle();
    InitialInformations initial_informations{
        .my_role = my_role,
        .my_connection_address = channel->GetLinkOptions()->GetLocalAddress(),
        .my_identity_address = local_identity_address_,
        .my_identity_resolving_key = local_identity_resolving_key_,
        /*TODO: properly obtain capabilities from device-specific storage*/
        .myPairingCapabilities = {.io_capability = local_le_io_capability_,
                                  .oob_data_flag = local_le_oob_data_present_,
                                  .auth_req = local_le_auth_req_,
                                  .maximum_encryption_key_size = local_maximum_encryption_key_size_,
                                  .initiator_key_distribution = 0x07,
                                  .responder_key_distribution = 0x07},
        .remotely_initiated = true,
        .connection_handle = channel->GetLinkOptions()->GetHandle(),
        .remote_connection_address = channel->GetDevice(),
        .remote_name = "TODO: grab proper device name in sec mgr",
        /* contains pairing request, if the pairing was remotely initiated */
        .pairing_request = pairing_request,
        .remote_oob_data = remote_oob_data,
        .my_oob_data = local_le_oob_data_,
        /* Used by Pairing Handler to present user with requests*/
        .user_interface = user_interface_,
        .user_interface_handler = user_interface_handler_,

        /* HCI interface to use */
        .le_security_interface = hci_security_interface_le_,
        .proper_l2cap_interface = enqueue_buffer.get(),
        .l2cap_handler = security_handler_,
        /* Callback to execute once the Pairing process is finished */
        // TODO: make it an common::OnceCallback ?
        .OnPairingFinished = std::bind(&SecurityManagerImpl::OnPairingFinished, this, std::placeholders::_1),
    };
    pending_le_pairing_.address_ = device;
    pending_le_pairing_.handler_ = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, initial_informations);
  }
}

void SecurityManagerImpl::OnConnectionOpenLe(std::unique_ptr<l2cap::le::FixedChannel> channel_param) {
  auto enqueue_buffer_temp =
      std::make_unique<os::EnqueueBuffer<packet::BasePacketBuilder>>(channel_param->GetQueueUpEnd());

  all_channels_.push_back({std::move(channel_param), std::move(enqueue_buffer_temp)});
  auto& stored_channel = all_channels_.back();
  auto& channel = stored_channel.channel_;

  channel->RegisterOnCloseCallback(
      security_handler_,
      common::BindOnce(&SecurityManagerImpl::OnConnectionClosedLe, common::Unretained(this), channel->GetDevice()));
  channel->GetQueueUpEnd()->RegisterDequeue(
      security_handler_,
      common::Bind(&SecurityManagerImpl::OnSmpCommandLe, common::Unretained(this), channel->GetDevice()));

  if (pending_le_pairing_.address_ != channel->GetDevice()) {
    return;
  }

  ConnectionIsReadyStartPairing(&stored_channel);
}

void SecurityManagerImpl::ConnectionIsReadyStartPairing(LeFixedChannelEntry* stored_channel) {
  auto& channel = stored_channel->channel_;
  auto& enqueue_buffer = stored_channel->enqueue_buffer_;

  stored_channel->channel_->Acquire();

  std::optional<InitialInformations::out_of_band_data> remote_oob_data = std::nullopt;
  if (remote_oob_data_address_.has_value() && remote_oob_data_address_.value() == channel->GetDevice())
    remote_oob_data = InitialInformations::out_of_band_data{.le_sc_c = remote_oob_data_le_sc_c_.value(),
                                                            .le_sc_r = remote_oob_data_le_sc_r_.value()};

  // TODO: this doesn't have to be a unique ptr, if there is a way to properly std::move it into place where it's stored
  pending_le_pairing_.connection_handle_ = channel->GetLinkOptions()->GetHandle();
  InitialInformations initial_informations{
      .my_role = channel->GetLinkOptions()->GetRole(),
      .my_connection_address = channel->GetLinkOptions()->GetLocalAddress(),
      .my_identity_address = local_identity_address_,
      .my_identity_resolving_key = local_identity_resolving_key_,
      /*TODO: properly obtain capabilities from device-specific storage*/
      .myPairingCapabilities = {.io_capability = local_le_io_capability_,
                                .oob_data_flag = local_le_oob_data_present_,
                                .auth_req = local_le_auth_req_,
                                .maximum_encryption_key_size = local_maximum_encryption_key_size_,
                                .initiator_key_distribution = 0x07,
                                .responder_key_distribution = 0x07},
      .remotely_initiated = false,
      .connection_handle = channel->GetLinkOptions()->GetHandle(),
      .remote_connection_address = channel->GetDevice(),
      .remote_name = "TODO: grab proper device name in sec mgr",
      /* contains pairing request, if the pairing was remotely initiated */
      .pairing_request = std::nullopt,  // TODO: handle remotely initiated pairing in SecurityManager properly
      .remote_oob_data = remote_oob_data,
      .my_oob_data = local_le_oob_data_,
      /* Used by Pairing Handler to present user with requests*/
      .user_interface = user_interface_,
      .user_interface_handler = user_interface_handler_,

      /* HCI interface to use */
      .le_security_interface = hci_security_interface_le_,
      .proper_l2cap_interface = enqueue_buffer.get(),
      .l2cap_handler = security_handler_,
      /* Callback to execute once the Pairing process is finished */
      // TODO: make it an common::OnceCallback ?
      .OnPairingFinished = std::bind(&SecurityManagerImpl::OnPairingFinished, this, std::placeholders::_1),
  };
  pending_le_pairing_.handler_ = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, initial_informations);
}

void SecurityManagerImpl::OnConnectionClosedLe(
    hci::AddressWithType address, hci::ErrorCode /* error_code */) {
  if (pending_le_pairing_.address_ != address) {
    LeFixedChannelEntry* stored_chan = FindStoredLeChannel(address);
    if (!stored_chan) {
      LOG_ALWAYS_FATAL("Received connection closed for unknown channel");
      return;
    }
    stored_chan->channel_->GetQueueUpEnd()->UnregisterDequeue();
    stored_chan->enqueue_buffer_.reset();
    EraseStoredLeChannel(address);
    return;
  }
  pending_le_pairing_.handler_->SendExitSignal();
  NotifyDeviceBondFailed(address, PairingFailure("Connection closed"));
}

void SecurityManagerImpl::OnConnectionFailureLe(bluetooth::l2cap::le::FixedChannelManager::ConnectionResult result) {
  if (result.connection_result_code ==
      bluetooth::l2cap::le::FixedChannelManager::ConnectionResultCode::FAIL_ALL_SERVICES_HAVE_CHANNEL) {
    // TODO: already connected
  }

  // This callback is invoked only for devices we attempted to connect to.
  NotifyDeviceBondFailed(pending_le_pairing_.address_, PairingFailure("Connection establishment failed"));
}

SecurityManagerImpl::SecurityManagerImpl(
    os::Handler* security_handler,
    l2cap::le::L2capLeModule* l2cap_le_module,
    channel::SecurityManagerChannel* security_manager_channel,
    hci::HciLayer* hci_layer,
    hci::AclManager* acl_manager,
    hci::Controller* controller,
    storage::StorageModule* storage_module,
    neighbor::NameDbModule* name_db_module)
    : security_handler_(security_handler),
      l2cap_le_module_(l2cap_le_module),
      l2cap_manager_le_(l2cap_le_module_->GetFixedChannelManager()),
      hci_security_interface_le_(
          hci_layer->GetLeSecurityInterface(security_handler_->BindOn(this, &SecurityManagerImpl::OnHciLeEvent))),
      security_manager_channel_(security_manager_channel),
      acl_manager_(acl_manager),
      controller_(controller),
      storage_module_(storage_module),
      security_record_storage_(storage_module, security_handler),
      security_database_(security_record_storage_),
      name_db_module_(name_db_module) {
  Init();

  l2cap_manager_le_->RegisterService(
      bluetooth::l2cap::kSmpCid,
      common::BindOnce(&SecurityManagerImpl::OnL2capRegistrationCompleteLe, common::Unretained(this)),
      common::Bind(&SecurityManagerImpl::OnConnectionOpenLe, common::Unretained(this)), security_handler_);
}

void SecurityManagerImpl::OnPairingFinished(security::PairingResultOrFailure pairing_result) {
  LOG_INFO(" ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■ Received pairing result");

  LeFixedChannelEntry* stored_chan = FindStoredLeChannel(pending_le_pairing_.address_);
  if (stored_chan) {
    stored_chan->channel_->Release();
  }

  if (std::holds_alternative<PairingFailure>(pairing_result)) {
    PairingFailure failure = std::get<PairingFailure>(pairing_result);
    LOG_INFO(" ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■ failure message: %s",
             failure.message.c_str());
    if (stored_chan) {
      NotifyDeviceBondFailed(stored_chan->channel_->GetDevice(), failure);
    }
    return;
  }

  auto result = std::get<PairingResult>(pairing_result);
  LOG_INFO("Pairing with %s was successful", ADDRESS_TO_LOGGABLE_CSTR(result.connection_address));

  // TODO: ensure that the security level is not weaker than what we already have.
  auto record = this->security_database_.FindOrCreate(result.connection_address);
  record->identity_address_ = result.distributed_keys.remote_identity_address;
  record->remote_ltk = result.distributed_keys.remote_ltk;
  record->key_size = result.key_size;
  record->security_level = result.security_level;
  record->remote_ediv = result.distributed_keys.remote_ediv;
  record->remote_rand = result.distributed_keys.remote_rand;
  record->remote_irk = result.distributed_keys.remote_irk;
  record->remote_signature_key = result.distributed_keys.remote_signature_key;
  if (result.distributed_keys.remote_link_key)
    record->SetLinkKey(*result.distributed_keys.remote_link_key, hci::KeyType::AUTHENTICATED_P256);
  security_database_.SaveRecordsToStorage();

  NotifyDeviceBonded(result.connection_address);
  // We also notify bond complete using identity address. That's what old stack used to do.
  if (result.distributed_keys.remote_identity_address)
    NotifyDeviceBonded(*result.distributed_keys.remote_identity_address);

  security_handler_->CallOn(this, &SecurityManagerImpl::WipeLePairingHandler);
}

void SecurityManagerImpl::WipeLePairingHandler() {
  pending_le_pairing_.handler_.reset();
  pending_le_pairing_.connection_handle_ = kInvalidConnectionHandle;
  pending_le_pairing_.address_ = hci::AddressWithType();
}

// Facade Configuration API functions
void SecurityManagerImpl::SetDisconnectCallback(FacadeDisconnectCallback callback) {
  this->facade_disconnect_callback_ = std::make_optional<FacadeDisconnectCallback>(callback);
}

void SecurityManagerImpl::SetIoCapability(hci::IoCapability io_capability) {
  this->local_io_capability_ = io_capability;
}

void SecurityManagerImpl::SetLeIoCapability(security::IoCapability io_capability) {
  this->local_le_io_capability_ = io_capability;
}

void SecurityManagerImpl::SetLeAuthRequirements(uint8_t auth_req) {
  this->local_le_auth_req_ = auth_req;
}

void SecurityManagerImpl::SetLeMaximumEncryptionKeySize(uint8_t maximum_encryption_key_size) {
  this->local_maximum_encryption_key_size_ = maximum_encryption_key_size;
}

void SecurityManagerImpl::SetLeOobDataPresent(OobDataFlag data_present) {
  this->local_le_oob_data_present_ = data_present;
}

void SecurityManagerImpl::GetOutOfBandData(channel::SecurityCommandStatusCallback callback) {
  this->security_manager_channel_->SendCommand(
      hci::ReadLocalOobDataBuilder::Create(), std::forward<channel::SecurityCommandStatusCallback>(callback));
}

void SecurityManagerImpl::GetLeOutOfBandData(
    std::array<uint8_t, 16>* confirmation_value, std::array<uint8_t, 16>* random_value) {
  local_le_oob_data_ = std::make_optional<MyOobData>(PairingHandlerLe::GenerateOobData());
  *confirmation_value = local_le_oob_data_.value().c;
  *random_value = local_le_oob_data_.value().r;
}

void SecurityManagerImpl::SetOutOfBandData(
    hci::AddressWithType remote_address,
    std::array<uint8_t, 16> confirmation_value,
    std::array<uint8_t, 16> random_value) {
  remote_oob_data_address_ = remote_address;
  remote_oob_data_le_sc_c_ = confirmation_value;
  remote_oob_data_le_sc_r_ = random_value;
}

void SecurityManagerImpl::SetAuthenticationRequirements(hci::AuthenticationRequirements authentication_requirements) {
  this->local_authentication_requirements_ = authentication_requirements;
}

void SecurityManagerImpl::InternalEnforceSecurityPolicy(
    hci::AddressWithType remote,
    l2cap::classic::SecurityPolicy policy,
    l2cap::classic::SecurityEnforcementInterface::ResultCallback result_callback) {
  if (IsSecurityRequirementSatisfied(remote, policy)) {
    // Notify client immediately if already satisfied
    std::move(result_callback).Invoke(true);
    return;
  }

  // At this point we don't meet the security requirements; must pair
  auto record = this->security_database_.FindOrCreate(remote);
  hci::AuthenticationRequirements authentication_requirements = kDefaultAuthenticationRequirements;
  enforce_security_policy_callback_map_[remote] = {policy, std::move(result_callback)};

  switch (policy) {
    case l2cap::classic::SecurityPolicy::BEST:
    case l2cap::classic::SecurityPolicy::AUTHENTICATED_ENCRYPTED_TRANSPORT:
      // Force MITM requirement locally
      authentication_requirements = hci::AuthenticationRequirements::GENERAL_BONDING_MITM_PROTECTION;
      break;
    case l2cap::classic::SecurityPolicy::ENCRYPTED_TRANSPORT:
      authentication_requirements = hci::AuthenticationRequirements::GENERAL_BONDING;
      break;
    default:
      // I could hear the voice of Myles, "This should be an ASSERT!"
      ASSERT_LOG(false, "Unreachable code path");
      return;
  }

  LOG_WARN("Dispatch #3");
  DispatchPairingHandler(
      record,
      true,
      this->local_io_capability_,
      std::as_const(authentication_requirements),
      pairing::OobData(),
      pairing::OobData());
}

void SecurityManagerImpl::UpdateLinkSecurityCondition(hci::AddressWithType remote) {
  auto entry = enforce_security_policy_callback_map_.find(remote);
  if (entry == enforce_security_policy_callback_map_.end()) {
    LOG_ERROR("No L2CAP security policy callback pending for %s", ADDRESS_TO_LOGGABLE_CSTR(remote));
    return;
  }
  std::move(entry->second.callback_).Invoke(IsSecurityRequirementSatisfied(remote, entry->second.policy_));
  enforce_security_policy_callback_map_.erase(entry);
}

bool SecurityManagerImpl::IsSecurityRequirementSatisfied(
    hci::AddressWithType remote, l2cap::classic::SecurityPolicy policy) {
  auto record = security_database_.FindOrCreate(remote);
  switch (policy) {
    case l2cap::classic::SecurityPolicy::BEST:
    case l2cap::classic::SecurityPolicy::AUTHENTICATED_ENCRYPTED_TRANSPORT:
      return (record->IsPaired() && record->IsAuthenticated());
    case l2cap::classic::SecurityPolicy::ENCRYPTED_TRANSPORT:
      return record->IsPaired();
    default:
      return true;
  }
}

void SecurityManagerImpl::EnforceSecurityPolicy(
    hci::AddressWithType remote,
    l2cap::classic::SecurityPolicy policy,
    l2cap::classic::SecurityEnforcementInterface::ResultCallback result_callback) {
  LOG_INFO("Attempting to enforce security policy");
  auto record = security_database_.FindOrCreate(remote);
  if (!record->IsPairing()) {
    this->InternalEnforceSecurityPolicy(remote, policy, std::move(result_callback));
  }
}

void SecurityManagerImpl::EnforceLeSecurityPolicy(
    hci::AddressWithType /* remote */,
    l2cap::le::SecurityPolicy policy,
    l2cap::le::SecurityEnforcementInterface::ResultCallback result_callback) {
  bool result = false;
  // TODO(jpawlowski): Implement for LE
  switch (policy) {
    case l2cap::le::SecurityPolicy::BEST:
      break;
    case l2cap::le::SecurityPolicy::AUTHENTICATED_ENCRYPTED_TRANSPORT:
      break;
    case l2cap::le::SecurityPolicy::ENCRYPTED_TRANSPORT:
      break;
    case l2cap::le::SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK:
      result = true;
      break;
    case l2cap::le::SecurityPolicy::_NOT_FOR_YOU__AUTHENTICATED_PAIRING_WITH_128_BIT_KEY:
      break;
    case l2cap::le::SecurityPolicy::_NOT_FOR_YOU__AUTHORIZATION:
      break;
  }
  result_callback.Invoke(result);
}
}  // namespace internal
}  // namespace security
}  // namespace bluetooth
