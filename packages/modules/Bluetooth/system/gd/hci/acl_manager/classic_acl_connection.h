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

#include <memory>

#include "hci/acl_connection_interface.h"
#include "hci/acl_manager/acl_connection.h"
#include "hci/acl_manager/connection_management_callbacks.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class ClassicAclConnection : public AclConnection {
 public:
  ClassicAclConnection();
  ClassicAclConnection(std::shared_ptr<Queue> queue, AclConnectionInterface* acl_connection_interface, uint16_t handle,
                       Address address);
  ClassicAclConnection(const ClassicAclConnection&) = delete;
  ClassicAclConnection& operator=(const ClassicAclConnection&) = delete;

  ~ClassicAclConnection();

  virtual Address GetAddress() const {
    return address_;
  }

  virtual void RegisterCallbacks(ConnectionManagementCallbacks* callbacks, os::Handler* handler);
  virtual bool Disconnect(DisconnectReason reason);
  virtual bool ChangeConnectionPacketType(uint16_t packet_type);
  virtual bool AuthenticationRequested();
  virtual bool SetConnectionEncryption(Enable enable);
  virtual bool ChangeConnectionLinkKey();
  virtual bool ReadClockOffset();
  virtual bool HoldMode(uint16_t max_interval, uint16_t min_interval);
  virtual bool SniffMode(uint16_t max_interval, uint16_t min_interval, uint16_t attempt, uint16_t timeout);
  virtual bool ExitSniffMode();
  virtual bool QosSetup(ServiceType service_type, uint32_t token_rate, uint32_t peak_bandwidth, uint32_t latency,
                        uint32_t delay_variation);
  virtual bool RoleDiscovery();
  virtual bool ReadLinkPolicySettings();
  virtual bool WriteLinkPolicySettings(uint16_t link_policy_settings);
  virtual bool FlowSpecification(FlowDirection flow_direction, ServiceType service_type, uint32_t token_rate,
                                 uint32_t token_bucket_size, uint32_t peak_bandwidth, uint32_t access_latency);
  virtual bool SniffSubrating(uint16_t maximum_latency, uint16_t minimum_remote_timeout,
                              uint16_t minimum_local_timeout);
  virtual bool Flush();
  virtual bool ReadAutomaticFlushTimeout();
  virtual bool WriteAutomaticFlushTimeout(uint16_t flush_timeout);
  virtual bool ReadTransmitPowerLevel(TransmitPowerLevelType type);
  virtual bool ReadLinkSupervisionTimeout();
  virtual bool WriteLinkSupervisionTimeout(uint16_t link_supervision_timeout);
  virtual bool ReadFailedContactCounter();
  virtual bool ResetFailedContactCounter();
  virtual bool ReadLinkQuality();
  virtual bool ReadAfhChannelMap();
  virtual bool ReadRssi();
  virtual bool ReadClock(WhichClock which_clock);
  virtual bool ReadRemoteVersionInformation() override;
  virtual bool ReadRemoteSupportedFeatures();
  virtual bool ReadRemoteExtendedFeatures(uint8_t page_number);

  // Called once before passing the connection to the client
  virtual ConnectionManagementCallbacks* GetEventCallbacks(std::function<void(uint16_t)> invalidate_callbacks);

 private:
  AclConnectionInterface* acl_connection_interface_;

 protected:
  Address address_;

 private:
  struct impl;
  struct impl* pimpl_ = nullptr;
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
