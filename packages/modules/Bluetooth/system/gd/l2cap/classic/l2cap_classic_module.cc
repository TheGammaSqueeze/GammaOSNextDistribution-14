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
#define LOG_TAG "l2cap2"

#include "l2cap/classic/l2cap_classic_module.h"

#include <future>
#include <memory>

#include "dumpsys_data_generated.h"
#include "hci/acl_manager.h"
#include "hci/address.h"
#include "l2cap/classic/internal/dumpsys_helper.h"
#include "l2cap/classic/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/classic/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/classic/internal/link_manager.h"
#include "l2cap/internal/parameter_provider.h"
#include "l2cap_classic_module_generated.h"
#include "module.h"
#include "os/handler.h"
#include "os/log.h"

namespace bluetooth {
namespace l2cap {
namespace classic {

const ModuleFactory L2capClassicModule::Factory = ModuleFactory([]() { return new L2capClassicModule(); });

static SecurityEnforcementRejectAllImpl default_security_module_impl_;

struct L2capClassicModule::impl {
  impl(os::Handler* l2cap_handler, hci::AclManager* acl_manager)
      : l2cap_handler_(l2cap_handler), acl_manager_(acl_manager) {
    dynamic_channel_service_manager_impl_.SetSecurityEnforcementInterface(&default_security_module_impl_);
    dumpsys_helper_ = std::make_unique<internal::DumpsysHelper>(link_manager_);
  }
  os::Handler* l2cap_handler_;
  hci::AclManager* acl_manager_;
  l2cap::internal::ParameterProvider parameter_provider_;
  internal::FixedChannelServiceManagerImpl fixed_channel_service_manager_impl_{l2cap_handler_};
  internal::DynamicChannelServiceManagerImpl dynamic_channel_service_manager_impl_{l2cap_handler_};
  internal::LinkManager link_manager_{l2cap_handler_, acl_manager_, &fixed_channel_service_manager_impl_,
                                      &dynamic_channel_service_manager_impl_, &parameter_provider_};
  std::unique_ptr<internal::DumpsysHelper> dumpsys_helper_;

  struct SecurityInterfaceImpl : public SecurityInterface {
    SecurityInterfaceImpl(impl* module_impl) : module_impl_(module_impl) {}

    void RegisterLinkSecurityInterfaceListener(os::Handler* handler, LinkSecurityInterfaceListener* listener) {
      ASSERT(!registered_);
      module_impl_->link_manager_.RegisterLinkSecurityInterfaceListener(handler, listener);
      registered_ = true;
    }

    void InitiateConnectionForSecurity(hci::Address remote) override {
      ASSERT(registered_);
      module_impl_->link_manager_.InitiateConnectionForSecurity(remote);
    }

    void Unregister() override {
      ASSERT(registered_);
      module_impl_->link_manager_.RegisterLinkSecurityInterfaceListener(nullptr, nullptr);
      registered_ = false;
    }
    impl* module_impl_;
    bool registered_ = false;
  } security_interface_impl_{this};

  void Dump(
      std::promise<flatbuffers::Offset<L2capClassicModuleData>> promise,
      flatbuffers::FlatBufferBuilder* fb_builder) const;
};

L2capClassicModule::L2capClassicModule() {}

L2capClassicModule::~L2capClassicModule() {}

void L2capClassicModule::ListDependencies(ModuleList* list) const {
  list->add<hci::AclManager>();
}

void L2capClassicModule::Start() {
  pimpl_ = std::make_unique<impl>(GetHandler(), GetDependency<hci::AclManager>());
}

void L2capClassicModule::Stop() {
  pimpl_.reset();
}

std::string L2capClassicModule::ToString() const {
  return "L2cap Classic Module";
}

std::unique_ptr<FixedChannelManager> L2capClassicModule::GetFixedChannelManager() {
  return std::unique_ptr<FixedChannelManager>(new FixedChannelManager(&pimpl_->fixed_channel_service_manager_impl_,
                                                                      &pimpl_->link_manager_, pimpl_->l2cap_handler_));
}

std::unique_ptr<DynamicChannelManager> L2capClassicModule::GetDynamicChannelManager() {
  return std::unique_ptr<DynamicChannelManager>(new DynamicChannelManager(
      &pimpl_->dynamic_channel_service_manager_impl_, &pimpl_->link_manager_, pimpl_->l2cap_handler_));
}

void L2capClassicModule::InjectSecurityEnforcementInterface(
    SecurityEnforcementInterface* security_enforcement_interface) {
  if (security_enforcement_interface != nullptr) {
    pimpl_->dynamic_channel_service_manager_impl_.SetSecurityEnforcementInterface(security_enforcement_interface);
  } else {
    pimpl_->dynamic_channel_service_manager_impl_.SetSecurityEnforcementInterface(&default_security_module_impl_);
  }
}

SecurityInterface* L2capClassicModule::GetSecurityInterface(
    os::Handler* handler, LinkSecurityInterfaceListener* listener) {
  pimpl_->security_interface_impl_.RegisterLinkSecurityInterfaceListener(handler, listener);
  return &pimpl_->security_interface_impl_;
}

void L2capClassicModule::SetLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener) {
  pimpl_->link_manager_.RegisterLinkPropertyListener(handler, listener);
}

void L2capClassicModule::impl::Dump(
    std::promise<flatbuffers::Offset<L2capClassicModuleData>> promise,
    flatbuffers::FlatBufferBuilder* fb_builder) const {
  auto title = fb_builder->CreateString("----- L2cap Classic Dumpsys -----");

  std::vector<flatbuffers::Offset<bluetooth::l2cap::classic::LinkData>> link_offsets =
      dumpsys_helper_->DumpActiveLinks(fb_builder);

  auto active_links = fb_builder->CreateVector(link_offsets);

  L2capClassicModuleDataBuilder builder(*fb_builder);
  builder.add_title(title);
  builder.add_active_links(active_links);
  flatbuffers::Offset<L2capClassicModuleData> dumpsys_data = builder.Finish();

  promise.set_value(dumpsys_data);
}

DumpsysDataFinisher L2capClassicModule::GetDumpsysData(flatbuffers::FlatBufferBuilder* fb_builder) const {
  ASSERT(fb_builder != nullptr);

  std::promise<flatbuffers::Offset<L2capClassicModuleData>> promise;
  auto future = promise.get_future();
  pimpl_->Dump(std::move(promise), fb_builder);

  auto dumpsys_data = future.get();

  return [dumpsys_data](DumpsysDataBuilder* dumpsys_builder) {
    dumpsys_builder->add_l2cap_classic_dumpsys_data(dumpsys_data);
  };
}

}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
