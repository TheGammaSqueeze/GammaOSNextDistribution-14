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
#include <memory>
#include <unordered_map>

#include "common/interfaces/ILoggable.h"
#include "hci/acl_manager/classic_acl_connection.h"
#include "l2cap/classic/dynamic_channel_configuration_option.h"
#include "l2cap/classic/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/classic/internal/fixed_channel_impl.h"
#include "l2cap/classic/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/classic/security_enforcement_interface.h"
#include "l2cap/internal/data_pipeline_manager.h"
#include "l2cap/internal/dynamic_channel_allocator.h"
#include "l2cap/internal/dynamic_channel_impl.h"
#include "l2cap/internal/fixed_channel_allocator.h"
#include "l2cap/internal/ilink.h"
#include "l2cap/internal/parameter_provider.h"
#include "os/alarm.h"
#include "os/handler.h"
#include "signalling_manager.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

class LinkManager;
class DumpsysHelper;

class Link : public l2cap::internal::ILink,
             public hci::acl_manager::ConnectionManagementCallbacks,
             public bluetooth::common::IRedactableLoggable {
 public:
  Link(os::Handler* l2cap_handler, std::unique_ptr<hci::acl_manager::ClassicAclConnection> acl_connection,
       l2cap::internal::ParameterProvider* parameter_provider,
       DynamicChannelServiceManagerImpl* dynamic_service_manager, FixedChannelServiceManagerImpl* fixed_service_manager,
       LinkManager* link_manager);

  Link(const Link&) = delete;
  Link& operator=(const Link&) = delete;

  hci::AddressWithType GetDevice() const override {
    return {acl_connection_->GetAddress(), hci::AddressType::PUBLIC_DEVICE_ADDRESS};
  }

  struct PendingDynamicChannelConnection {
    os::Handler* handler_;
    DynamicChannelManager::OnConnectionOpenCallback on_open_callback_;
    DynamicChannelManager::OnConnectionFailureCallback on_fail_callback_;
    classic::DynamicChannelConfigurationOption configuration_;
  };

  struct PendingAuthenticateDynamicChannelConnection {
    Psm psm_;
    Cid cid_;
    PendingDynamicChannelConnection pending_dynamic_channel_connection_;
  };

  // ACL methods

  virtual void OnAclDisconnected(hci::ErrorCode status);

  virtual void Disconnect();

  virtual void Encrypt();

  virtual void Authenticate();

  virtual bool IsAuthenticated() const;

  virtual void ReadRemoteVersionInformation();

  virtual void ReadRemoteSupportedFeatures();

  virtual void ReadRemoteExtendedFeatures(uint8_t page_number);

  virtual void ReadClockOffset();

  // Increase the link usage refcount to ensure the link won't be disconnected when SecurityModule needs it
  virtual void AcquireSecurityHold();

  // Decrease the link usage refcount when SecurityModule no longer needs it
  virtual void ReleaseSecurityHold();

  // FixedChannel methods

  std::shared_ptr<FixedChannelImpl> AllocateFixedChannel(Cid cid);

  virtual bool IsFixedChannelAllocated(Cid cid);

  // DynamicChannel methods

  virtual Cid ReserveDynamicChannel();

  virtual void SendConnectionRequest(Psm psm, Cid local_cid);
  virtual void SendConnectionRequest(Psm psm, Cid local_cid,
                                     PendingDynamicChannelConnection pending_dynamic_channel_connection);
  void SetChannelTxPriority(Cid local_cid, bool high_priority) override;

  // When a Link is established, LinkManager notifies pending dynamic channels to connect
  virtual void SetPendingDynamicChannels(std::list<Psm> psm_list,
                                         std::list<Link::PendingDynamicChannelConnection> callback_list);

  // Invoked by signalling manager to indicate an outgoing connection request failed and link shall free resources
  virtual void OnOutgoingConnectionRequestFail(Cid local_cid, DynamicChannelManager::ConnectionResult result);

  virtual void SendInitialConfigRequestOrQueue(Cid local_cid);

  virtual void SendInformationRequest(InformationRequestInfoType type);

  virtual void SendDisconnectionRequest(Cid local_cid, Cid remote_cid) override;

  virtual std::shared_ptr<l2cap::internal::DynamicChannelImpl> AllocateDynamicChannel(Psm psm, Cid remote_cid);

  virtual std::shared_ptr<l2cap::internal::DynamicChannelImpl> AllocateReservedDynamicChannel(Cid reserved_cid, Psm psm,
                                                                                              Cid remote_cid);

  virtual classic::DynamicChannelConfigurationOption GetConfigurationForInitialConfiguration(Cid cid);

  virtual void FreeDynamicChannel(Cid cid);

  // Check how many channels are acquired or in use, if zero, start tear down timer, if non-zero, cancel tear down timer
  virtual void RefreshRefCount();

  virtual void NotifyChannelCreation(Cid cid, std::unique_ptr<DynamicChannel> channel);
  virtual void NotifyChannelFail(Cid cid, DynamicChannelManager::ConnectionResult result);

  // Information received from signaling channel
  virtual void SetRemoteConnectionlessMtu(Mtu mtu);
  virtual Mtu GetRemoteConnectionlessMtu() const;
  virtual bool GetRemoteSupportsErtm() const;
  virtual bool GetRemoteSupportsFcs() const;
  virtual void OnRemoteExtendedFeatureReceived(bool ertm_supported, bool fcs_supported);

  virtual std::string ToString() const {
    return GetDevice().ToString();
  }

  std::string ToStringForLogging() const override {
    return GetDevice().ToStringForLogging();
  }

  std::string ToRedactedStringForLogging() const override {
    return GetDevice().ToRedactedStringForLogging();
  }

  void SendLeCredit(Cid /* local_cid */, uint16_t /* credit */) override {}

  // ConnectionManagementCallbacks
  void OnConnectionPacketTypeChanged(uint16_t packet_type) override;
  void OnAuthenticationComplete(hci::ErrorCode hci_status) override;
  void OnEncryptionChange(hci::EncryptionEnabled enabled) override;
  void OnChangeConnectionLinkKeyComplete() override;
  void OnReadClockOffsetComplete(uint16_t clock_offset) override;
  void OnModeChange(hci::ErrorCode status, hci::Mode current_mode, uint16_t interval) override;
  void OnSniffSubrating(
      hci::ErrorCode hci_status,
      uint16_t maximum_transmit_latency,
      uint16_t maximum_receive_latency,
      uint16_t minimum_remote_timeout,
      uint16_t minimum_local_timeout) override;
  void OnQosSetupComplete(hci::ServiceType service_type, uint32_t token_rate, uint32_t peak_bandwidth, uint32_t latency,
                          uint32_t delay_variation) override;
  void OnFlowSpecificationComplete(hci::FlowDirection flow_direction, hci::ServiceType service_type,
                                   uint32_t token_rate, uint32_t token_bucket_size, uint32_t peak_bandwidth,
                                   uint32_t access_latency) override;
  void OnFlushOccurred() override;
  void OnRoleDiscoveryComplete(hci::Role current_role) override;
  void OnReadLinkPolicySettingsComplete(uint16_t link_policy_settings) override;
  void OnReadAutomaticFlushTimeoutComplete(uint16_t flush_timeout) override;
  void OnReadTransmitPowerLevelComplete(uint8_t transmit_power_level) override;
  void OnReadLinkSupervisionTimeoutComplete(uint16_t link_supervision_timeout) override;
  void OnReadFailedContactCounterComplete(uint16_t failed_contact_counter) override;
  void OnReadLinkQualityComplete(uint8_t link_quality) override;
  void OnReadAfhChannelMapComplete(hci::AfhMode afh_mode, std::array<uint8_t, 10> afh_channel_map) override;
  void OnReadRssiComplete(uint8_t rssi) override;
  void OnReadClockComplete(uint32_t clock, uint16_t accuracy) override;
  void OnCentralLinkKeyComplete(hci::KeyFlag key_flag) override;
  void OnRoleChange(hci::ErrorCode hci_status, hci::Role new_role) override;
  void OnDisconnection(hci::ErrorCode reason) override;
  void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version);
  void OnReadRemoteSupportedFeaturesComplete(uint64_t features);
  void OnReadRemoteExtendedFeaturesComplete(uint8_t page_number, uint8_t max_page_number, uint64_t features);

  struct EncryptionChangeListener {
    Cid cid;
    Psm psm;
  };
  void AddEncryptionChangeListener(EncryptionChangeListener);

  uint16_t GetAclHandle() const {
    return acl_handle_;
  }

  hci::Role GetRole() const {
    return role_;
  }

  void OnPendingPacketChange(Cid local_cid, bool has_packet) override;

 private:
  friend class DumpsysHelper;
  void connect_to_pending_dynamic_channels();
  void send_pending_configuration_requests();

  os::Handler* l2cap_handler_;
  l2cap::internal::FixedChannelAllocator<FixedChannelImpl, Link> fixed_channel_allocator_{this, l2cap_handler_};
  l2cap::internal::DynamicChannelAllocator dynamic_channel_allocator_{this, l2cap_handler_};
  std::unique_ptr<hci::acl_manager::ClassicAclConnection> acl_connection_;
  l2cap::internal::DataPipelineManager data_pipeline_manager_;
  l2cap::internal::ParameterProvider* parameter_provider_;
  DynamicChannelServiceManagerImpl* dynamic_service_manager_;
  FixedChannelServiceManagerImpl* fixed_service_manager_;
  LinkManager* link_manager_;
  std::unordered_map<Cid, PendingDynamicChannelConnection> local_cid_to_pending_dynamic_channel_connection_map_;
  os::Alarm link_idle_disconnect_alarm_{l2cap_handler_};
  ClassicSignallingManager signalling_manager_;
  uint16_t acl_handle_;
  Mtu remote_connectionless_mtu_ = kMinimumClassicMtu;
  hci::Role role_ = hci::Role::CENTRAL;
  bool remote_extended_feature_received_ = false;
  bool remote_supports_ertm_ = false;
  bool remote_supports_fcs_ = false;
  hci::EncryptionEnabled encryption_enabled_ = hci::EncryptionEnabled::OFF;
  std::list<Psm> pending_dynamic_psm_list_;
  std::list<Link::PendingDynamicChannelConnection> pending_dynamic_channel_callback_list_;
  std::list<uint16_t> pending_outgoing_configuration_request_list_;
  bool used_by_security_module_ = false;
  bool has_requested_authentication_ = false;
  std::list<EncryptionChangeListener> encryption_change_listener_;
  std::atomic_int remaining_packets_to_be_sent_ = 0;
};

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
