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

#include "hci/acl_manager/le_acl_connection.h"

#include "hci/acl_manager/le_connection_management_callbacks.h"
#include "os/metrics.h"

using bluetooth::hci::Address;

namespace bluetooth {
namespace hci {
namespace acl_manager {

class LeAclConnectionTracker : public LeConnectionManagementCallbacks {
 public:
  LeAclConnectionTracker(LeAclConnectionInterface* le_acl_connection_interface, uint16_t connection_handle)
      : le_acl_connection_interface_(le_acl_connection_interface), connection_handle_(connection_handle) {}
  ~LeAclConnectionTracker() {
    ASSERT(queued_callbacks_.empty());
  }
  void RegisterCallbacks(LeConnectionManagementCallbacks* callbacks, os::Handler* handler) {
    client_handler_ = handler;
    client_callbacks_ = callbacks;
    while (!queued_callbacks_.empty()) {
      auto iter = queued_callbacks_.begin();
      handler->Post(std::move(*iter));
      queued_callbacks_.erase(iter);
    }
  }

#define SAVE_OR_CALL(f, ...)                                                                                        \
  if (client_handler_ == nullptr) {                                                                                 \
    queued_callbacks_.emplace_back(                                                                                 \
        common::BindOnce(&LeConnectionManagementCallbacks::f, common::Unretained(this), __VA_ARGS__));              \
  } else {                                                                                                          \
    client_handler_->Post(                                                                                          \
        common::BindOnce(&LeConnectionManagementCallbacks::f, common::Unretained(client_callbacks_), __VA_ARGS__)); \
  }

  void OnConnectionUpdate(
      hci::ErrorCode hci_status, uint16_t conn_interval, uint16_t conn_latency, uint16_t supervision_timeout) override {
    SAVE_OR_CALL(OnConnectionUpdate, hci_status, conn_interval, conn_latency, supervision_timeout)
  }

  void OnDataLengthChange(uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time) override {
    SAVE_OR_CALL(OnDataLengthChange, tx_octets, tx_time, rx_octets, rx_time)
  }

  void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version) {
    bluetooth::os::LogMetricRemoteVersionInfo(
        connection_handle_, static_cast<uint8_t>(hci_status), lmp_version, manufacturer_name, sub_version);
    SAVE_OR_CALL(OnReadRemoteVersionInformationComplete, hci_status, lmp_version, manufacturer_name, sub_version);
  }

  void OnLeReadRemoteFeaturesComplete(hci::ErrorCode hci_status, uint64_t features) override {
    SAVE_OR_CALL(OnLeReadRemoteFeaturesComplete, hci_status, features);
  }

  void OnPhyUpdate(hci::ErrorCode hci_status, uint8_t tx_phy, uint8_t rx_phy) override {
    SAVE_OR_CALL(OnPhyUpdate, hci_status, tx_phy, rx_phy);
  }
  void OnLeSubrateChange(
      hci::ErrorCode hci_status,
      uint16_t subrate_factor,
      uint16_t peripheral_latency,
      uint16_t continuation_number,
      uint16_t supervision_timeout) override {
    SAVE_OR_CALL(
        OnLeSubrateChange, hci_status, subrate_factor, peripheral_latency, continuation_number, supervision_timeout);
  }

  void OnDisconnection(ErrorCode reason) override {
    SAVE_OR_CALL(OnDisconnection, reason);
  }
#undef SAVE_OR_CALL

  LeAclConnectionInterface* le_acl_connection_interface_;
  os::Handler* client_handler_ = nullptr;
  LeConnectionManagementCallbacks* client_callbacks_ = nullptr;
  std::list<common::OnceClosure> queued_callbacks_;
  const uint16_t connection_handle_;
};

struct LeAclConnection::impl {
  impl(LeAclConnectionInterface* le_acl_connection_interface, std::shared_ptr<Queue> queue, uint16_t connection_handle)
      : queue_(std::move(queue)), tracker(le_acl_connection_interface, connection_handle) {}
  LeConnectionManagementCallbacks* GetEventCallbacks(std::function<void(uint16_t)> invalidate_callbacks) {
    ASSERT_LOG(!invalidate_callbacks_, "Already returned event callbacks for this connection");
    invalidate_callbacks_ = std::move(invalidate_callbacks);
    return &tracker;
  }
  void PutEventCallbacks() {
    if (invalidate_callbacks_) invalidate_callbacks_(tracker.connection_handle_);
  }
  std::shared_ptr<Queue> queue_;
  LeAclConnectionTracker tracker;
  std::function<void(uint16_t)> invalidate_callbacks_;
};

LeAclConnection::LeAclConnection()
    : AclConnection(),
      remote_address_(Address::kEmpty, AddressType::PUBLIC_DEVICE_ADDRESS),
      role_specific_data_(DataAsUninitializedPeripheral{}) {}

LeAclConnection::LeAclConnection(
    std::shared_ptr<Queue> queue,
    LeAclConnectionInterface* le_acl_connection_interface,
    uint16_t handle,
    RoleSpecificData role_specific_data,
    AddressWithType remote_address)
    : AclConnection(queue->GetUpEnd(), handle),
      remote_address_(remote_address),
      role_specific_data_(role_specific_data) {
  pimpl_ = new LeAclConnection::impl(le_acl_connection_interface, std::move(queue), handle);
}

LeAclConnection::~LeAclConnection() {
  if (pimpl_) pimpl_->PutEventCallbacks();
  delete pimpl_;
}

AddressWithType LeAclConnection::GetLocalAddress() const {
  return std::visit(
      [](auto&& data) {
        using T = std::decay_t<decltype(data)>;
        if constexpr (std::is_same_v<T, DataAsUninitializedPeripheral>) {
          // This case should never happen outside of acl_manager.cc, since once the connection is
          // passed into the OnConnectSuccess callback, it should be fully initialized.
          LOG_ALWAYS_FATAL("Attempted to read the local address of an uninitialized connection");
          return AddressWithType{};
        } else {
          return data.local_address;
        }
      },
      role_specific_data_);
}

Role LeAclConnection::GetRole() const {
  return std::visit(
      [](auto&& data) {
        using T = std::decay_t<decltype(data)>;
        if constexpr (std::is_same_v<T, DataAsCentral>) {
          return Role::CENTRAL;
        } else if constexpr (
            std::is_same_v<T, DataAsPeripheral> ||
            std::is_same_v<T, DataAsUninitializedPeripheral>) {
          return Role::PERIPHERAL;
        } else {
          static_assert(!sizeof(T*), "missing case");
        }
      },
      role_specific_data_);
}

const RoleSpecificData& LeAclConnection::GetRoleSpecificData() const {
  return role_specific_data_;
}

void LeAclConnection::RegisterCallbacks(LeConnectionManagementCallbacks* callbacks, os::Handler* handler) {
  return pimpl_->tracker.RegisterCallbacks(callbacks, handler);
}

void LeAclConnection::Disconnect(DisconnectReason reason) {
  pimpl_->tracker.le_acl_connection_interface_->EnqueueCommand(
      DisconnectBuilder::Create(handle_, reason),
      pimpl_->tracker.client_handler_->BindOnce([](CommandStatusView status) {
        ASSERT(status.IsValid());
        ASSERT(status.GetCommandOpCode() == OpCode::DISCONNECT);
        auto disconnect_status = DisconnectStatusView::Create(status);
        ASSERT(disconnect_status.IsValid());
        auto error_code = disconnect_status.GetStatus();
        if (error_code != ErrorCode::SUCCESS) {
          LOG_INFO("Disconnect status %s", ErrorCodeText(error_code).c_str());
        }
      }));
}

void LeAclConnection::OnLeSubrateRequestStatus(CommandStatusView status) {
  auto subrate_request_status = LeSubrateRequestStatusView::Create(status);
  ASSERT(subrate_request_status.IsValid());
  auto hci_status = subrate_request_status.GetStatus();
  if (hci_status != ErrorCode::SUCCESS) {
    LOG_INFO("LeSubrateRequest status %s", ErrorCodeText(hci_status).c_str());
    pimpl_->tracker.OnLeSubrateChange(hci_status, 0, 0, 0, 0);
  }
}

void LeAclConnection::LeSubrateRequest(
    uint16_t subrate_min, uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num, uint16_t sup_tout) {
  pimpl_->tracker.le_acl_connection_interface_->EnqueueCommand(
      LeSubrateRequestBuilder::Create(handle_, subrate_min, subrate_max, max_latency, cont_num, sup_tout),
      pimpl_->tracker.client_handler_->BindOnceOn(this, &LeAclConnection::OnLeSubrateRequestStatus));
}

LeConnectionManagementCallbacks* LeAclConnection::GetEventCallbacks(
    std::function<void(uint16_t)> invalidate_callbacks) {
  return pimpl_->GetEventCallbacks(std::move(invalidate_callbacks));
}

bool LeAclConnection::LeConnectionUpdate(
    uint16_t conn_interval_min,
    uint16_t conn_interval_max,
    uint16_t conn_latency,
    uint16_t supervision_timeout,
    uint16_t min_ce_length,
    uint16_t max_ce_length) {
  if (!check_connection_parameters(conn_interval_min, conn_interval_max, conn_latency, supervision_timeout)) {
    LOG_ERROR("Invalid parameter");
    return false;
  }
  pimpl_->tracker.le_acl_connection_interface_->EnqueueCommand(
      LeConnectionUpdateBuilder::Create(
          handle_,
          conn_interval_min,
          conn_interval_max,
          conn_latency,
          supervision_timeout,
          min_ce_length,
          max_ce_length),
      pimpl_->tracker.client_handler_->BindOnce([](CommandStatusView status) {
        ASSERT(status.IsValid());
        ASSERT(status.GetCommandOpCode() == OpCode::LE_CONNECTION_UPDATE);
      }));
  return true;
}

bool LeAclConnection::ReadRemoteVersionInformation() {
  pimpl_->tracker.le_acl_connection_interface_->EnqueueCommand(
      ReadRemoteVersionInformationBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce([](CommandStatusView status) {
        ASSERT(status.IsValid());
        ASSERT(status.GetCommandOpCode() == OpCode::READ_REMOTE_VERSION_INFORMATION);
      }));
  return true;
}

bool LeAclConnection::LeReadRemoteFeatures() {
  pimpl_->tracker.le_acl_connection_interface_->EnqueueCommand(
      LeReadRemoteFeaturesBuilder::Create(handle_),
      pimpl_->tracker.client_handler_->BindOnce([](CommandStatusView status) {
        ASSERT(status.IsValid());
        ASSERT(status.GetCommandOpCode() == OpCode::LE_READ_REMOTE_FEATURES);
      }));
  return true;
}

bool LeAclConnection::check_connection_parameters(
    uint16_t conn_interval_min, uint16_t conn_interval_max, uint16_t conn_latency, uint16_t supervision_timeout) {
  if (conn_interval_min < 0x0006 || conn_interval_min > 0x0C80 || conn_interval_max < 0x0006 ||
      conn_interval_max > 0x0C80 || conn_latency > 0x01F3 || supervision_timeout < 0x000A ||
      supervision_timeout > 0x0C80) {
    LOG_ERROR("Invalid parameter");
    return false;
  }
  // The Maximum interval in milliseconds will be conn_interval_max * 1.25 ms
  // The Timeout in milliseconds will be expected_supervision_timeout * 10 ms
  // The Timeout in milliseconds shall be larger than (1 + Latency) * Interval_Max * 2, where Interval_Max is given in
  // milliseconds.
  uint32_t supervision_timeout_min = (uint32_t)(1 + conn_latency) * conn_interval_max * 2 + 1;
  if (supervision_timeout * 8 < supervision_timeout_min || conn_interval_max < conn_interval_min) {
    LOG_ERROR("Invalid parameter");
    return false;
  }

  return true;
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
