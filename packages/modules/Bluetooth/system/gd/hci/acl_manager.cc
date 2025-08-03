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

#include "hci/acl_manager.h"

#include <atomic>
#include <future>
#include <mutex>

#include "common/bidi_queue.h"
#include "common/byte_array.h"
#include "dumpsys_data_generated.h"
#include "hci/acl_manager/acl_scheduler.h"
#include "hci/acl_manager/classic_impl.h"
#include "hci/acl_manager/le_acceptlist_callbacks.h"
#include "hci/acl_manager/le_acl_connection.h"
#include "hci/acl_manager/le_impl.h"
#include "hci/acl_manager/round_robin_scheduler.h"
#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/remote_name_request.h"
#include "hci_acl_manager_generated.h"
#include "security/security_module.h"
#include "storage/config_keys.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace hci {

constexpr uint16_t kQualcommDebugHandle = 0xedc;

using acl_manager::AclConnection;
using common::Bind;
using common::BindOnce;

using acl_manager::classic_impl;
using acl_manager::ClassicAclConnection;
using acl_manager::ConnectionCallbacks;

using acl_manager::le_impl;
using acl_manager::LeAcceptlistCallbacks;
using acl_manager::LeAclConnection;
using acl_manager::LeConnectionCallbacks;

using acl_manager::RoundRobinScheduler;

using acl_manager::AclScheduler;

struct AclManager::impl {
  impl(const AclManager& acl_manager) : acl_manager_(acl_manager) {}

  void Start() {
    hci_layer_ = acl_manager_.GetDependency<HciLayer>();
    handler_ = acl_manager_.GetHandler();
    controller_ = acl_manager_.GetDependency<Controller>();
    round_robin_scheduler_ = new RoundRobinScheduler(handler_, controller_, hci_layer_->GetAclQueueEnd());
    acl_scheduler_ = acl_manager_.GetDependency<AclScheduler>();

    remote_name_request_module_ = acl_manager_.GetDependency<RemoteNameRequestModule>();

    bool crash_on_unknown_handle = false;
    {
      const std::lock_guard<std::mutex> lock(dumpsys_mutex_);
      classic_impl_ = new classic_impl(
          hci_layer_,
          controller_,
          handler_,
          round_robin_scheduler_,
          crash_on_unknown_handle,
          acl_scheduler_,
          remote_name_request_module_);
      le_impl_ = new le_impl(hci_layer_, controller_, handler_, round_robin_scheduler_, crash_on_unknown_handle);
    }

    hci_queue_end_ = hci_layer_->GetAclQueueEnd();
    hci_queue_end_->RegisterDequeue(
        handler_, common::Bind(&impl::dequeue_and_route_acl_packet_to_connection, common::Unretained(this)));
  }

  void Stop() {
    hci_queue_end_->UnregisterDequeue();
    if (enqueue_registered_.exchange(false)) {
      hci_queue_end_->UnregisterEnqueue();
    }

    {
      const std::lock_guard<std::mutex> lock(dumpsys_mutex_);
      delete le_impl_;
      delete classic_impl_;
      le_impl_ = nullptr;
      classic_impl_ = nullptr;
    }

    unknown_acl_alarm_.reset();
    waiting_packets_.clear();

    delete round_robin_scheduler_;
    hci_queue_end_ = nullptr;
    handler_ = nullptr;
    hci_layer_ = nullptr;
    acl_scheduler_ = nullptr;
  }

  void retry_unknown_acl(bool timed_out) {
    std::vector<AclView> unsent_packets;
    for (const auto& itr : waiting_packets_) {
      auto handle = itr.GetHandle();
      if (!classic_impl_->send_packet_upward(
              handle,
              [itr](struct acl_manager::assembler* assembler) {
                assembler->on_incoming_packet(itr);
              }) &&
          !le_impl_->send_packet_upward(handle, [itr](struct acl_manager::assembler* assembler) {
            assembler->on_incoming_packet(itr);
          })) {
        if (!timed_out) {
          unsent_packets.push_back(itr);
        } else {
          LOG_ERROR(
              "Dropping packet of size %zu to unknown connection 0x%0hx",
              itr.size(),
              itr.GetHandle());
        }
      }
    }
    waiting_packets_ = std::move(unsent_packets);
  }

  static void on_unknown_acl_timer(struct AclManager::impl* impl) {
    LOG_INFO("Timer fired!");
    impl->retry_unknown_acl(/* timed_out = */ true);
    impl->unknown_acl_alarm_.reset();
  }

  // Invoked from some external Queue Reactable context 2
  void dequeue_and_route_acl_packet_to_connection() {
    // Retry any waiting packets first
    if (!waiting_packets_.empty()) {
      retry_unknown_acl(/* timed_out = */ false);
    }

    auto packet = hci_queue_end_->TryDequeue();
    ASSERT(packet != nullptr);
    if (!packet->IsValid()) {
      LOG_INFO("Dropping invalid packet of size %zu", packet->size());
      return;
    }
    uint16_t handle = packet->GetHandle();
    if (handle == kQualcommDebugHandle) return;
    if (classic_impl_->send_packet_upward(
            handle, [&packet](struct acl_manager::assembler* assembler) { assembler->on_incoming_packet(*packet); }))
      return;
    if (le_impl_->send_packet_upward(
            handle, [&packet](struct acl_manager::assembler* assembler) { assembler->on_incoming_packet(*packet); }))
      return;
    if (unknown_acl_alarm_ == nullptr) {
      unknown_acl_alarm_.reset(new os::Alarm(handler_));
    }
    waiting_packets_.push_back(*packet);
    LOG_INFO(
        "Saving packet of size %zu to unknown connection 0x%0hx",
        packet->size(),
        packet->GetHandle());
    unknown_acl_alarm_->Schedule(
        BindOnce(&on_unknown_acl_timer, common::Unretained(this)), kWaitBeforeDroppingUnknownAcl);
  }

  void Dump(
      std::promise<flatbuffers::Offset<AclManagerData>> promise, flatbuffers::FlatBufferBuilder* fb_builder) const;

  const AclManager& acl_manager_;

  classic_impl* classic_impl_ = nullptr;
  le_impl* le_impl_ = nullptr;
  AclScheduler* acl_scheduler_ = nullptr;
  RemoteNameRequestModule* remote_name_request_module_ = nullptr;
  os::Handler* handler_ = nullptr;
  Controller* controller_ = nullptr;
  HciLayer* hci_layer_ = nullptr;
  RoundRobinScheduler* round_robin_scheduler_ = nullptr;
  common::BidiQueueEnd<AclBuilder, AclView>* hci_queue_end_ = nullptr;
  std::atomic_bool enqueue_registered_ = false;
  uint16_t default_link_policy_settings_ = 0xffff;
  mutable std::mutex dumpsys_mutex_;
  std::unique_ptr<os::Alarm> unknown_acl_alarm_;
  std::vector<AclView> waiting_packets_;
  static constexpr std::chrono::seconds kWaitBeforeDroppingUnknownAcl{1};
};

AclManager::AclManager() : pimpl_(std::make_unique<impl>(*this)) {}

void AclManager::RegisterCallbacks(ConnectionCallbacks* callbacks, os::Handler* handler) {
  ASSERT(callbacks != nullptr && handler != nullptr);
  GetHandler()->Post(common::BindOnce(
      &classic_impl::handle_register_callbacks,
      common::Unretained(pimpl_->classic_impl_),
      common::Unretained(callbacks),
      common::Unretained(handler)));
}

void AclManager::UnregisterCallbacks(ConnectionCallbacks* callbacks, std::promise<void> promise) {
  ASSERT(callbacks != nullptr);
  CallOn(
      pimpl_->classic_impl_,
      &classic_impl::handle_unregister_callbacks,
      common::Unretained(callbacks),
      std::move(promise));
}

void AclManager::RegisterLeCallbacks(LeConnectionCallbacks* callbacks, os::Handler* handler) {
  ASSERT(callbacks != nullptr && handler != nullptr);
  CallOn(
      pimpl_->le_impl_,
      &le_impl::handle_register_le_callbacks,
      common::Unretained(callbacks),
      common::Unretained(handler));
}

void AclManager::RegisterLeAcceptlistCallbacks(LeAcceptlistCallbacks* callbacks) {
  ASSERT(callbacks != nullptr);
  CallOn(
      pimpl_->le_impl_,
      &le_impl::handle_register_le_acceptlist_callbacks,
      common::Unretained(callbacks));
}

void AclManager::UnregisterLeCallbacks(LeConnectionCallbacks* callbacks, std::promise<void> promise) {
  ASSERT(callbacks != nullptr);
  CallOn(pimpl_->le_impl_, &le_impl::handle_unregister_le_callbacks, common::Unretained(callbacks), std::move(promise));
}

void AclManager::UnregisterLeAcceptlistCallbacks(
    LeAcceptlistCallbacks* callbacks, std::promise<void> promise) {
  ASSERT(callbacks != nullptr);
  CallOn(
      pimpl_->le_impl_,
      &le_impl::handle_unregister_le_acceptlist_callbacks,
      common::Unretained(callbacks),
      std::move(promise));
}

void AclManager::CreateConnection(Address address) {
  CallOn(pimpl_->classic_impl_, &classic_impl::create_connection, address);
}

void AclManager::CreateLeConnection(AddressWithType address_with_type, bool is_direct) {
  if (!is_direct) {
    CallOn(pimpl_->le_impl_, &le_impl::add_device_to_background_connection_list, address_with_type);
  }
  CallOn(pimpl_->le_impl_, &le_impl::create_le_connection, address_with_type, true, is_direct);
}

void AclManager::IsOnBackgroundList(AddressWithType address_with_type, std::promise<bool> promise) {
  CallOn(pimpl_->le_impl_, &le_impl::is_on_background_connection_list, address_with_type, std::move(promise));
}

void AclManager::SetLeSuggestedDefaultDataParameters(uint16_t octets, uint16_t time) {
  CallOn(pimpl_->le_impl_, &le_impl::set_le_suggested_default_data_parameters, octets, time);
}

void AclManager::LeSetDefaultSubrate(
    uint16_t subrate_min, uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num, uint16_t sup_tout) {
  CallOn(pimpl_->le_impl_, &le_impl::LeSetDefaultSubrate, subrate_min, subrate_max, max_latency, cont_num, sup_tout);
}

void AclManager::SetPrivacyPolicyForInitiatorAddress(
    LeAddressManager::AddressPolicy address_policy,
    AddressWithType fixed_address,
    std::chrono::milliseconds minimum_rotation_time,
    std::chrono::milliseconds maximum_rotation_time) {
  Octet16 rotation_irk{};
  auto irk_prop = GetDependency<storage::StorageModule>()->GetProperty(
      BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IRK);
  if (irk_prop.has_value()) {
    auto irk = common::ByteArray<16>::FromString(irk_prop.value());
    if (irk.has_value()) {
      rotation_irk = irk->bytes;
    }
  }
  CallOn(
      pimpl_->le_impl_,
      &le_impl::set_privacy_policy_for_initiator_address,
      address_policy,
      fixed_address,
      rotation_irk,
      minimum_rotation_time,
      maximum_rotation_time);
}

// TODO(jpawlowski): remove once we have config file abstraction in cert tests
void AclManager::SetPrivacyPolicyForInitiatorAddressForTest(
    LeAddressManager::AddressPolicy address_policy,
    AddressWithType fixed_address,
    Octet16 rotation_irk,
    std::chrono::milliseconds minimum_rotation_time,
    std::chrono::milliseconds maximum_rotation_time) {
  CallOn(
      pimpl_->le_impl_,
      &le_impl::set_privacy_policy_for_initiator_address_for_test,
      address_policy,
      fixed_address,
      rotation_irk,
      minimum_rotation_time,
      maximum_rotation_time);
}

void AclManager::CancelConnect(Address address) {
  CallOn(pimpl_->classic_impl_, &classic_impl::cancel_connect, address);
}

void AclManager::CancelLeConnect(AddressWithType address_with_type) {
  CallOn(pimpl_->le_impl_, &le_impl::remove_device_from_background_connection_list, address_with_type);
  CallOn(pimpl_->le_impl_, &le_impl::cancel_connect, address_with_type);
}

void AclManager::RemoveFromBackgroundList(AddressWithType address_with_type) {
  CallOn(pimpl_->le_impl_, &le_impl::remove_device_from_background_connection_list, address_with_type);
}

void AclManager::ClearFilterAcceptList() {
  CallOn(pimpl_->le_impl_, &le_impl::clear_filter_accept_list);
}

void AclManager::AddDeviceToResolvingList(
    AddressWithType address_with_type,
    const std::array<uint8_t, 16>& peer_irk,
    const std::array<uint8_t, 16>& local_irk) {
  CallOn(pimpl_->le_impl_, &le_impl::add_device_to_resolving_list, address_with_type, peer_irk, local_irk);
}

void AclManager::RemoveDeviceFromResolvingList(AddressWithType address_with_type) {
  CallOn(pimpl_->le_impl_, &le_impl::remove_device_from_resolving_list, address_with_type);
}

void AclManager::ClearResolvingList() {
  CallOn(pimpl_->le_impl_, &le_impl::clear_resolving_list);
}

void AclManager::CentralLinkKey(KeyFlag key_flag) {
  CallOn(pimpl_->classic_impl_, &classic_impl::central_link_key, key_flag);
}

void AclManager::SwitchRole(Address address, Role role) {
  CallOn(pimpl_->classic_impl_, &classic_impl::switch_role, address, role);
}

uint16_t AclManager::ReadDefaultLinkPolicySettings() {
  ASSERT_LOG(pimpl_->default_link_policy_settings_ != 0xffff, "Settings were never written");
  return pimpl_->default_link_policy_settings_;
}

void AclManager::WriteDefaultLinkPolicySettings(uint16_t default_link_policy_settings) {
  pimpl_->default_link_policy_settings_ = default_link_policy_settings;
  CallOn(pimpl_->classic_impl_, &classic_impl::write_default_link_policy_settings, default_link_policy_settings);
}

void AclManager::OnAdvertisingSetTerminated(
    ErrorCode status,
    uint16_t conn_handle,
    uint8_t adv_set_id,
    hci::AddressWithType adv_address,
    bool is_discoverable) {
  if (status == ErrorCode::SUCCESS) {
    CallOn(
        pimpl_->le_impl_,
        &le_impl::OnAdvertisingSetTerminated,
        conn_handle,
        adv_set_id,
        adv_address,
        is_discoverable);
  }
}

void AclManager::SetSecurityModule(security::SecurityModule* security_module) {
  CallOn(pimpl_->classic_impl_, &classic_impl::set_security_module, security_module);
}

void AclManager::OnClassicSuspendInitiatedDisconnect(uint16_t handle, ErrorCode reason) {
  CallOn(pimpl_->classic_impl_, &classic_impl::on_classic_disconnect, handle, reason);
}

void AclManager::OnLeSuspendInitiatedDisconnect(uint16_t handle, ErrorCode reason) {
  CallOn(pimpl_->le_impl_, &le_impl::on_le_disconnect, handle, reason);
}

void AclManager::SetSystemSuspendState(bool suspended) {
  CallOn(pimpl_->le_impl_, &le_impl::set_system_suspend_state, suspended);
}

LeAddressManager* AclManager::GetLeAddressManager() {
  return pimpl_->le_impl_->le_address_manager_;
}

uint16_t AclManager::HACK_GetHandle(Address address) {
  return pimpl_->classic_impl_->HACK_get_handle(address);
}

uint16_t AclManager::HACK_GetLeHandle(Address address) {
  return pimpl_->le_impl_->HACK_get_handle(address);
}

void AclManager::HACK_SetAclTxPriority(uint8_t handle, bool high_priority) {
  CallOn(pimpl_->round_robin_scheduler_, &RoundRobinScheduler::SetLinkPriority, handle, high_priority);
}

void AclManager::ListDependencies(ModuleList* list) const {
  list->add<HciLayer>();
  list->add<Controller>();
  list->add<storage::StorageModule>();
  list->add<AclScheduler>();
  list->add<RemoteNameRequestModule>();
}

void AclManager::Start() {
  pimpl_->Start();
}

void AclManager::Stop() {
  pimpl_->Stop();
}

std::string AclManager::ToString() const {
  return "Acl Manager";
}

const ModuleFactory AclManager::Factory = ModuleFactory([]() { return new AclManager(); });

AclManager::~AclManager() = default;

void AclManager::impl::Dump(
    std::promise<flatbuffers::Offset<AclManagerData>> promise, flatbuffers::FlatBufferBuilder* fb_builder) const {
  const std::lock_guard<std::mutex> lock(dumpsys_mutex_);
  const auto accept_list = (le_impl_ != nullptr) ? le_impl_->accept_list : std::unordered_set<AddressWithType>();
  const auto le_connectability_state_text =
      (le_impl_ != nullptr) ? connectability_state_machine_text(le_impl_->connectability_state_) : "INDETERMINATE";
  const auto le_create_connection_timeout_alarms_count =
      (le_impl_ != nullptr) ? (int)le_impl_->create_connection_timeout_alarms_.size() : 0;

  auto title = fb_builder->CreateString("----- Acl Manager Dumpsys -----");
  auto le_connectability_state = fb_builder->CreateString(le_connectability_state_text);

  flatbuffers::Offset<flatbuffers::String> strings[accept_list.size()];

  size_t cnt = 0;
  for (const auto& it : accept_list) {
    strings[cnt++] = fb_builder->CreateString(it.ToString());
  }
  auto vecofstrings = fb_builder->CreateVector(strings, accept_list.size());

  AclManagerDataBuilder builder(*fb_builder);
  builder.add_title(title);
  builder.add_le_filter_accept_list_count(accept_list.size());
  builder.add_le_filter_accept_list(vecofstrings);
  builder.add_le_connectability_state(le_connectability_state);
  builder.add_le_create_connection_timeout_alarms_count(le_create_connection_timeout_alarms_count);

  flatbuffers::Offset<AclManagerData> dumpsys_data = builder.Finish();
  promise.set_value(dumpsys_data);
}

DumpsysDataFinisher AclManager::GetDumpsysData(flatbuffers::FlatBufferBuilder* fb_builder) const {
  ASSERT(fb_builder != nullptr);

  std::promise<flatbuffers::Offset<AclManagerData>> promise;
  auto future = promise.get_future();
  pimpl_->Dump(std::move(promise), fb_builder);

  auto dumpsys_data = future.get();

  return [dumpsys_data](DumpsysDataBuilder* dumpsys_builder) {
    dumpsys_builder->add_hci_acl_manager_dumpsys_data(dumpsys_data);
  };
}

}  // namespace hci
}  // namespace bluetooth
