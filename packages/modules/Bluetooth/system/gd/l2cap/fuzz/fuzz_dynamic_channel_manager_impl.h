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

#include <future>
#include <memory>

#include "hci/address.h"
#include "l2cap/classic/dynamic_channel_configuration_option.h"
#include "l2cap/classic/dynamic_channel_manager.h"
#include "l2cap/classic/security_policy.h"
#include "l2cap/psm.h"

namespace bluetooth {
namespace shim {
namespace {
class FuzzDynamicChannelManagerImpl {
 public:
  void ConnectChannel(
      hci::Address device,
      l2cap::classic::DynamicChannelConfigurationOption configuration_option,
      l2cap::Psm,
      l2cap::classic::DynamicChannelManager::OnConnectionOpenCallback on_open_callback,
      l2cap::classic::DynamicChannelManager::OnConnectionFailureCallback on_fail_callback) {
    connections_++;
    on_open_callback_ = std::move(on_open_callback);
    on_fail_callback_ = std::move(on_fail_callback);

    connected_promise_.set_value();
  }
  int connections_{0};

  void RegisterService(
      l2cap::Psm,
      l2cap::classic::DynamicChannelConfigurationOption,
      const l2cap::classic::SecurityPolicy&,
      l2cap::classic::DynamicChannelManager::OnRegistrationCompleteCallback on_registration_complete,
      l2cap::classic::DynamicChannelManager::OnConnectionOpenCallback on_open_callback) {
    services_++;
    on_registration_complete_ = std::move(on_registration_complete);
    on_open_callback_ = std::move(on_open_callback);

    register_promise_.set_value();
  }
  int services_{0};

  void SetConnectionFuture() {
    connected_promise_ = std::promise<void>();
  }

  void WaitConnectionFuture() {
    connected_future_ = connected_promise_.get_future();
    connected_future_.wait();
  }

  void SetRegistrationFuture() {
    register_promise_ = std::promise<void>();
  }

  void WaitRegistrationFuture() {
    register_future_ = register_promise_.get_future();
    register_future_.wait();
  }

  void SetConnectionOnFail(l2cap::classic::DynamicChannelManager::ConnectionResult result, std::promise<void> promise) {
    std::move(on_fail_callback_).Invoke(result);
    promise.set_value();
  }

  void SetConnectionOnOpen(std::unique_ptr<l2cap::DynamicChannel> channel, std::promise<void> promise) {
    std::move(on_open_callback_).Invoke(std::move(channel));
    promise.set_value();
  }

  l2cap::classic::DynamicChannelManager::OnRegistrationCompleteCallback on_registration_complete_{};
  l2cap::classic::DynamicChannelManager::OnConnectionOpenCallback on_open_callback_{};
  l2cap::classic::DynamicChannelManager::OnConnectionFailureCallback on_fail_callback_{};

  FuzzDynamicChannelManagerImpl() = default;
  ~FuzzDynamicChannelManagerImpl() = default;

 private:
  std::promise<void> connected_promise_;
  std::future<void> connected_future_;

  std::promise<void> register_promise_;
  std::future<void> register_future_;
};
}  // namespace
}  // namespace shim
}  // namespace bluetooth
