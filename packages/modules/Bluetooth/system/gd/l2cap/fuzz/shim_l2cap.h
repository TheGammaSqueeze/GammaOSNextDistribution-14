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

#include <fuzzer/FuzzedDataProvider.h>

#include <future>
#include <memory>

#include "fuzz_l2cap_classic_module.h"
#include "hci/fuzz/fuzz_hci_layer.h"
#include "l2cap/classic/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/classic/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/classic/internal/link_manager.h"
#include "l2cap/classic/l2cap_classic_module.h"
#include "l2cap/internal/parameter_provider.h"
#include "os/handler.h"

namespace bluetooth {

namespace shim {
namespace {
class ShimL2capFuzz {
 public:
  uint16_t CreateConnection(uint16_t psm, hci::Address device_address) {
    std::promise<uint16_t> promise;
    auto future = promise.get_future();

    fuzz_l2cap_classic_module_->GetDynamicChannelManager()->ConnectChannel(
        device_address,
        {},
        psm,
        handler_->BindOn(this, &ShimL2capFuzz::OnConnectionComplete),
        handler_->BindOnceOn(this, &ShimL2capFuzz::OnConnectionFail));

    return future.get();
  }

  void OnConnectionComplete(std::unique_ptr<l2cap::classic::DynamicChannel> channel) {}

  void OnConnectionFail(l2cap::classic::DynamicChannelManager::ConnectionResult result) {}

  ShimL2capFuzz(FuzzedDataProvider* fdp) {
    hci::fuzz::FuzzHciLayer* fuzzHci = fake_registry_.Inject<hci::fuzz::FuzzHciLayer>(&hci::HciLayer::Factory);
    fuzz_l2cap_classic_module_ = new FuzzL2capClassicModule();
    fake_registry_.InjectTestModule(&l2cap::classic::L2capClassicModule::Factory, fuzz_l2cap_classic_module_);
    fake_registry_.Start<l2cap::classic::L2capClassicModule>();

    // The autoreply is needed to prevent it from hanging.
    fuzzHci->TurnOnAutoReply(fdp);
    acl_manager_ = fake_registry_.Start<hci::AclManager>();
    fuzzHci->TurnOffAutoReply();

    // Create the LinkManager
    handler_ = std::unique_ptr<os::Handler>(new os::Handler(&thread_));
    dynamic_channel_impl = std::unique_ptr<l2cap::classic::internal::DynamicChannelServiceManagerImpl>(
        new l2cap::classic::internal::DynamicChannelServiceManagerImpl(handler_.get()));
    fixed_channel_impl = std::unique_ptr<l2cap::classic::internal::FixedChannelServiceManagerImpl>(
        new l2cap::classic::internal::FixedChannelServiceManagerImpl(handler_.get()));
    parameter_provider = std::unique_ptr<l2cap::internal::ParameterProvider>(new l2cap::internal::ParameterProvider());
    link_manager = std::unique_ptr<l2cap::classic::internal::LinkManager>(new l2cap::classic::internal::LinkManager(
        handler_.get(), acl_manager_, fixed_channel_impl.get(), dynamic_channel_impl.get(), parameter_provider.get()));
  }

  ~ShimL2capFuzz() {
    handler_->Clear();
  }

  void stopRegistry() {
    fake_registry_.WaitForIdleAndStopAll();
  }

  std::promise<void> connection_complete_promise_;

  FuzzL2capClassicModule* fuzz_l2cap_classic_module_{nullptr};
  hci::AclManager* acl_manager_{nullptr};

  std::unique_ptr<os::Handler> handler_;
  std::unique_ptr<l2cap::classic::internal::FixedChannelServiceManagerImpl> fixed_channel_impl;
  std::unique_ptr<l2cap::classic::internal::DynamicChannelServiceManagerImpl> dynamic_channel_impl;
  std::unique_ptr<l2cap::classic::internal::LinkManager> link_manager;
  std::unique_ptr<l2cap::internal::ParameterProvider> parameter_provider;

 private:
  FuzzTestModuleRegistry fake_registry_;
  os::Thread& thread_ = fake_registry_.GetTestThread();
};
}  // namespace
}  // namespace shim
}  // namespace bluetooth
