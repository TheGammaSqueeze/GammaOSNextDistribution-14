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
// Authors: corbin.souffrant@leviathansecurity.com
//          dylan.katz@leviathansecurity.com

#pragma once

#include "fuzz_dynamic_channel_manager_impl.h"
#include "hci/address.h"
#include "l2cap/classic/dynamic_channel_configuration_option.h"
#include "l2cap/classic/dynamic_channel_manager.h"
#include "l2cap/classic/security_policy.h"
#include "l2cap/psm.h"

namespace bluetooth {
namespace shim {
namespace {
class FuzzDynamicChannelManager : public l2cap::classic::DynamicChannelManager {
 public:
  void ConnectChannel(
      hci::Address device,
      l2cap::classic::DynamicChannelConfigurationOption configuration_option,
      l2cap::Psm psm,
      l2cap::classic::DynamicChannelManager::OnConnectionOpenCallback on_open_callback,
      l2cap::classic::DynamicChannelManager::OnConnectionFailureCallback on_fail_callback) override {
    impl_.ConnectChannel(device, configuration_option, psm, std::move(on_open_callback), std::move(on_fail_callback));
  }

  void RegisterService(
      l2cap::Psm psm,
      l2cap::classic::DynamicChannelConfigurationOption configuration_option,
      const l2cap::classic::SecurityPolicy& security_policy,
      l2cap::classic::DynamicChannelManager::OnRegistrationCompleteCallback on_registration_complete,
      l2cap::classic::DynamicChannelManager::OnConnectionOpenCallback on_open_callback) override {
    impl_.RegisterService(
        psm, configuration_option, security_policy, std::move(on_registration_complete), std::move(on_open_callback));
  }
  FuzzDynamicChannelManager(FuzzDynamicChannelManagerImpl& impl) : impl_(impl) {}
  FuzzDynamicChannelManagerImpl& impl_;
};
}  // namespace
}  // namespace shim
}  // namespace bluetooth
