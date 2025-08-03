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

#define LOG_TAG "iso"

#include <memory>
#include "module.h"
#include "os/handler.h"
#include "os/log.h"

#include "hci/acl_manager.h"
#include "hci/hci_layer.h"
#include "iso/iso_module.h"

namespace bluetooth {
namespace iso {

const ModuleFactory IsoModule::Factory = ModuleFactory([]() { return new IsoModule(); });

struct IsoModule::impl {
  impl(os::Handler* iso_handler, hci::HciLayer* hci_layer, hci::Controller* controller)
      : iso_handler_(iso_handler), hci_layer_(hci_layer), controller_(controller) {}

  os::Handler* iso_handler_;
  hci::HciLayer* hci_layer_;
  hci::Controller* controller_;

  internal::IsoManagerImpl iso_manager_impl{iso_handler_, hci_layer_, controller_};
};

void IsoModule::ListDependencies(ModuleList* list) const {
  list->add<hci::HciLayer>();
  list->add<hci::Controller>();
}

void IsoModule::Start() {
  pimpl_ = std::make_unique<impl>(GetHandler(), GetDependency<hci::HciLayer>(), GetDependency<hci::Controller>());
}

void IsoModule::Stop() {
  pimpl_.reset();
}

std::string IsoModule::ToString() const {
  return "Iso Module";
}

std::unique_ptr<IsoManager> IsoModule::GetIsoManager() {
  return std::unique_ptr<IsoManager>(new IsoManager(pimpl_->iso_handler_, &pimpl_->iso_manager_impl));
}

}  // namespace iso
}  // namespace bluetooth
