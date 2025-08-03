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

#define LOG_TAG "security"

#include "security/security_module.h"

#include <memory>

#include "hci/acl_manager.h"
#include "hci/hci_layer.h"
#include "l2cap/classic/l2cap_classic_module.h"
#include "l2cap/le/l2cap_le_module.h"
#include "module.h"
#include "neighbor/name_db.h"
#include "os/handler.h"
#include "security/channel/security_manager_channel.h"
#include "security/facade_configuration_api.h"
#include "security/internal/security_manager_impl.h"
#include "security/l2cap_security_module_interface.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace security {

const ModuleFactory SecurityModule::Factory = ModuleFactory([]() { return new SecurityModule(); });

struct SecurityModule::impl {
  impl(
      os::Handler* security_handler,
      l2cap::le::L2capLeModule* l2cap_le_module,
      l2cap::classic::L2capClassicModule* l2cap_classic_module,
      hci::HciLayer* hci_layer,
      hci::AclManager* acl_manager,
      hci::Controller* controller,
      storage::StorageModule* storage_module,
      neighbor::NameDbModule* name_db_module)
      : security_handler_(security_handler),
        l2cap_classic_module_(l2cap_classic_module),
        l2cap_le_module_(l2cap_le_module),
        security_manager_channel_(new channel::SecurityManagerChannel(security_handler_, hci_layer)),
        hci_layer_(hci_layer),
        acl_manager_(acl_manager),
        controller_(controller),
        storage_module_(storage_module),
        l2cap_security_interface_(&security_manager_impl, security_handler),
        name_db_module_(name_db_module) {
    l2cap_classic_module->InjectSecurityEnforcementInterface(&l2cap_security_interface_);
    l2cap_le_module->InjectSecurityEnforcementInterface(&l2cap_security_interface_);
    security_manager_channel_->SetSecurityInterface(
        l2cap_classic_module->GetSecurityInterface(security_handler_, security_manager_channel_));
  }

  os::Handler* security_handler_;
  l2cap::classic::L2capClassicModule* l2cap_classic_module_;
  l2cap::le::L2capLeModule* l2cap_le_module_;
  channel::SecurityManagerChannel* security_manager_channel_;
  hci::HciLayer* hci_layer_;
  hci::AclManager* acl_manager_;
  hci::Controller* controller_;
  storage::StorageModule* storage_module_;
  L2capSecurityModuleInterface l2cap_security_interface_;
  neighbor::NameDbModule* name_db_module_;

  internal::SecurityManagerImpl security_manager_impl{security_handler_,
                                                      l2cap_le_module_,
                                                      security_manager_channel_,
                                                      hci_layer_,
                                                      acl_manager_,
                                                      controller_,
                                                      storage_module_,
                                                      name_db_module_};

  ~impl() {
    delete security_manager_channel_;
    l2cap_classic_module_->InjectSecurityEnforcementInterface(nullptr);
    l2cap_le_module_->InjectSecurityEnforcementInterface(nullptr);
  }
};

void SecurityModule::ListDependencies(ModuleList* list) const {
  list->add<l2cap::le::L2capLeModule>();
  list->add<l2cap::classic::L2capClassicModule>();
  list->add<hci::HciLayer>();
  list->add<hci::AclManager>();
  list->add<hci::Controller>();
  list->add<storage::StorageModule>();
  list->add<neighbor::NameDbModule>();
}

void SecurityModule::Start() {
  pimpl_ = std::make_unique<impl>(
      GetHandler(),
      GetDependency<l2cap::le::L2capLeModule>(),
      GetDependency<l2cap::classic::L2capClassicModule>(),
      GetDependency<hci::HciLayer>(),
      GetDependency<hci::AclManager>(),
      GetDependency<hci::Controller>(),
      GetDependency<storage::StorageModule>(),
      GetDependency<neighbor::NameDbModule>());

  GetDependency<hci::AclManager>()->SetSecurityModule(this);
}

void SecurityModule::Stop() {
  pimpl_.reset();
}

std::string SecurityModule::ToString() const {
  return "Security Module";
}

std::unique_ptr<SecurityManager> SecurityModule::GetSecurityManager() {
  return std::unique_ptr<SecurityManager>(
      new SecurityManager(pimpl_->security_handler_, &pimpl_->security_manager_impl));
}

std::unique_ptr<FacadeConfigurationApi> SecurityModule::GetFacadeConfigurationApi() {
  return std::unique_ptr<FacadeConfigurationApi>(
      new FacadeConfigurationApi(pimpl_->security_handler_, &pimpl_->security_manager_impl));
}

}  // namespace security
}  // namespace bluetooth
