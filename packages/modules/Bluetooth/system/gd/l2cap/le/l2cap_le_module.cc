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

#include <memory>

#include "hci/acl_manager.h"
#include "l2cap/internal/parameter_provider.h"
#include "l2cap/le/internal/dynamic_channel_service_manager_impl.h"
#include "l2cap/le/internal/fixed_channel_service_manager_impl.h"
#include "l2cap/le/internal/link_manager.h"
#include "l2cap/le/security_enforcement_interface.h"
#include "module.h"
#include "os/handler.h"

#include "l2cap/le/l2cap_le_module.h"

namespace bluetooth {
namespace l2cap {
namespace le {

const ModuleFactory L2capLeModule::Factory = ModuleFactory([]() { return new L2capLeModule(); });

/**
 * A default implementation which cannot satisfy any security level except
 * NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK.
 */
class SecurityEnforcementRejectAllImpl : public SecurityEnforcementInterface {
 public:
  void Enforce(
      hci::AddressWithType /* remote */,
      SecurityPolicy policy,
      ResultCallback result_callback) override {
    if (policy == SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK) {
      result_callback.InvokeIfNotEmpty(true);
    } else {
      result_callback.InvokeIfNotEmpty(false);
    }
  }
};
static SecurityEnforcementRejectAllImpl default_security_module_impl_;

struct L2capLeModule::impl {
  impl(os::Handler* l2cap_handler, hci::AclManager* acl_manager)
      : l2cap_handler_(l2cap_handler), acl_manager_(acl_manager) {
    dynamic_channel_service_manager_impl_.SetSecurityEnforcementInterface(&default_security_module_impl_);
  }
  os::Handler* l2cap_handler_;
  hci::AclManager* acl_manager_;
  l2cap::internal::ParameterProvider parameter_provider_;
  internal::FixedChannelServiceManagerImpl fixed_channel_service_manager_impl_{l2cap_handler_};
  internal::DynamicChannelServiceManagerImpl dynamic_channel_service_manager_impl_{l2cap_handler_};
  internal::LinkManager link_manager_{l2cap_handler_,
                                      acl_manager_,
                                      &fixed_channel_service_manager_impl_,
                                      &dynamic_channel_service_manager_impl_,
                                      &parameter_provider_};
};

L2capLeModule::L2capLeModule() {}
L2capLeModule::~L2capLeModule() {}

void L2capLeModule::ListDependencies(ModuleList* list) const {
  list->add<hci::AclManager>();
}

void L2capLeModule::Start() {
  pimpl_ = std::make_unique<impl>(GetHandler(), GetDependency<hci::AclManager>());
}

void L2capLeModule::Stop() {
  pimpl_.reset();
}

std::string L2capLeModule::ToString() const {
  return "L2cap Le Module";
}

std::unique_ptr<FixedChannelManager> L2capLeModule::GetFixedChannelManager() {
  return std::unique_ptr<FixedChannelManager>(new FixedChannelManager(&pimpl_->fixed_channel_service_manager_impl_,
                                                                      &pimpl_->link_manager_, pimpl_->l2cap_handler_));
}

std::unique_ptr<DynamicChannelManager> L2capLeModule::GetDynamicChannelManager() {
  return std::unique_ptr<DynamicChannelManager>(new DynamicChannelManager(
      &pimpl_->dynamic_channel_service_manager_impl_, &pimpl_->link_manager_, pimpl_->l2cap_handler_));
}

void L2capLeModule::InjectSecurityEnforcementInterface(SecurityEnforcementInterface* security_enforcement_interface) {
  if (security_enforcement_interface != nullptr) {
    pimpl_->dynamic_channel_service_manager_impl_.SetSecurityEnforcementInterface(security_enforcement_interface);
  } else {
    pimpl_->dynamic_channel_service_manager_impl_.SetSecurityEnforcementInterface(&default_security_module_impl_);
  }
}

void L2capLeModule::SetLinkPropertyListener(os::Handler* handler, LinkPropertyListener* listener) {
  pimpl_->link_manager_.RegisterLinkPropertyListener(handler, listener);
}

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
