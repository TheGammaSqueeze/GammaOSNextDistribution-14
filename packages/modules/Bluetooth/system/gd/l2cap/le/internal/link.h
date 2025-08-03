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

#include <atomic>
#include <chrono>
#include <memory>

#include "common/interfaces/ILoggable.h"
#include "hci/acl_manager/le_acl_connection.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/internal/dynamic_channel_allocator.h"
#include "l2cap/internal/fixed_channel_allocator.h"
#include "l2cap/internal/ilink.h"
#include "l2cap/internal/parameter_provider.h"
#include "l2cap/le/dynamic_channel_manager.h"
#include "l2cap/le/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/le/internal/fixed_channel_impl.h"
#include "l2cap/le/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/le/internal/signalling_manager.h"
#include "l2cap/le/link_options.h"
#include "l2cap/le/security_enforcement_interface.h"
#include "os/alarm.h"

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {

class LinkManager;

class Link : public l2cap::internal::ILink,
             public hci::acl_manager::LeConnectionManagementCallbacks,
             public bluetooth::common::IRedactableLoggable {
 public:
  Link(os::Handler* l2cap_handler, std::unique_ptr<hci::acl_manager::LeAclConnection> acl_connection,
       l2cap::internal::ParameterProvider* parameter_provider,
       DynamicChannelServiceManagerImpl* dynamic_service_manager, FixedChannelServiceManagerImpl* fixed_service_manager,
       LinkManager* link_manager);

  Link(const Link&) = delete;
  Link& operator=(const Link&) = delete;

  ~Link() = default;

  inline hci::AddressWithType GetDevice() const override {
    return acl_connection_->GetRemoteAddress();
  }

  struct PendingDynamicChannelConnection {
    os::Handler* handler_;
    DynamicChannelManager::OnConnectionOpenCallback on_open_callback_;
    DynamicChannelManager::OnConnectionFailureCallback on_fail_callback_;
    le::DynamicChannelConfigurationOption configuration_;
  };

  inline virtual hci::Role GetRole() {
    return acl_connection_->GetRole();
  }

  inline virtual hci::acl_manager::LeAclConnection* GetAclConnection() {
    return acl_connection_.get();
  }

  // ACL methods

  virtual void OnAclDisconnected(hci::ErrorCode reason);

  void OnDisconnection(hci::ErrorCode reason) override;

  void OnConnectionUpdate(
      hci::ErrorCode hci_status,
      uint16_t connection_interval,
      uint16_t connection_latency,
      uint16_t supervision_timeout) override;

  void OnDataLengthChange(uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time) override;

  void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version) override;

  void OnLeReadRemoteFeaturesComplete(hci::ErrorCode hci_status, uint64_t features) override;

  void OnPhyUpdate(hci::ErrorCode hci_status, uint8_t tx_phy, uint8_t rx_phy) override;

  void OnLeSubrateChange(
      hci::ErrorCode hci_status,
      uint16_t subrate_factor,
      uint16_t peripheral_latency,
      uint16_t continuation_number,
      uint16_t supervision_timeout) override;

  virtual void Disconnect();

  // Handles connection parameter update request from remote
  virtual void UpdateConnectionParameterFromRemote(SignalId signal_id, uint16_t conn_interval_min,
                                                   uint16_t conn_interval_max, uint16_t conn_latency,
                                                   uint16_t supervision_timeout);
  virtual bool CheckConnectionParameters(
      uint16_t conn_interval_min, uint16_t conn_interval_max, uint16_t conn_latency, uint16_t supervision_timeout);

  virtual void SendConnectionParameterUpdate(uint16_t conn_interval_min, uint16_t conn_interval_max,
                                             uint16_t conn_latency, uint16_t supervision_timeout,
                                             uint16_t min_ce_length, uint16_t max_ce_length);

  // FixedChannel methods

  virtual std::shared_ptr<FixedChannelImpl> AllocateFixedChannel(Cid cid, SecurityPolicy security_policy);

  virtual bool IsFixedChannelAllocated(Cid cid);

  // DynamicChannel methods

  virtual Cid ReserveDynamicChannel();

  virtual void SendConnectionRequest(Psm psm, PendingDynamicChannelConnection pending_dynamic_channel_connection);

  void SendDisconnectionRequest(Cid local_cid, Cid remote_cid) override;

  // Invoked by signalling manager to indicate an outgoing connection request failed and link shall free resources
  virtual void OnOutgoingConnectionRequestFail(Cid local_cid, LeCreditBasedConnectionResponseResult result);

  virtual std::shared_ptr<l2cap::internal::DynamicChannelImpl> AllocateDynamicChannel(Psm psm, Cid remote_cid);

  virtual std::shared_ptr<l2cap::internal::DynamicChannelImpl> AllocateReservedDynamicChannel(Cid reserved_cid, Psm psm,
                                                                                              Cid remote_cid);

  virtual void FreeDynamicChannel(Cid cid);

  // Check how many channels are acquired or in use, if zero, start tear down timer, if non-zero, cancel tear down timer
  virtual void RefreshRefCount();

  void NotifyChannelCreation(Cid cid, std::unique_ptr<DynamicChannel> user_channel);
  void NotifyChannelFail(Cid cid, DynamicChannelManager::ConnectionResult result);

  virtual std::string ToString() {
    return GetDevice().ToString();
  }

  std::string ToStringForLogging() const override {
    return GetDevice().ToStringForLogging();
  }

  std::string ToRedactedStringForLogging() const override {
    return GetDevice().ToRedactedStringForLogging();
  }

  virtual uint16_t GetMps() const;

  virtual uint16_t GetInitialCredit() const;

  void SendLeCredit(Cid local_cid, uint16_t credit) override;

  LinkOptions* GetLinkOptions() {
    return &link_options_;
  }

  void ReadRemoteVersionInformation();

  void OnPendingPacketChange(Cid local_cid, bool has_packet) override;

 private:
  os::Handler* l2cap_handler_;
  l2cap::internal::FixedChannelAllocator<FixedChannelImpl, Link> fixed_channel_allocator_{this, l2cap_handler_};
  l2cap::internal::DynamicChannelAllocator dynamic_channel_allocator_{this, l2cap_handler_};
  std::unique_ptr<hci::acl_manager::LeAclConnection> acl_connection_;
  l2cap::internal::DataPipelineManager data_pipeline_manager_;
  l2cap::internal::ParameterProvider* parameter_provider_;
  DynamicChannelServiceManagerImpl* dynamic_service_manager_;
  LeSignallingManager signalling_manager_;
  std::unordered_map<Cid, PendingDynamicChannelConnection> local_cid_to_pending_dynamic_channel_connection_map_;
  os::Alarm link_idle_disconnect_alarm_{l2cap_handler_};
  LinkOptions link_options_{acl_connection_.get(), this, l2cap_handler_};
  LinkManager* link_manager_;
  SignalId update_request_signal_id_ = kInvalidSignalId;
  uint16_t update_request_interval_min_;
  uint16_t update_request_interval_max_;
  uint16_t update_request_latency_;
  uint16_t update_request_supervision_timeout_;
  std::atomic_int remaining_packets_to_be_sent_ = 0;

  // Received connection update complete from ACL manager. SignalId is bound to a valid number when we need to send a
  // response to remote. If SignalId is bound to an invalid number, we don't send a response to remote, because the
  // connection update request is not from remote LL peripheral.
  void on_connection_update_complete(SignalId signal_id, hci::ErrorCode error_code);
};

}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
