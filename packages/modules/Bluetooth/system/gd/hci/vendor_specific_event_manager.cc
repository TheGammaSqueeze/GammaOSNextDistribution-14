/*
 * Copyright 2021 The Android Open Source Project
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
#include "hci/vendor_specific_event_manager.h"

#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {

const ModuleFactory VendorSpecificEventManager::Factory =
    ModuleFactory([]() { return new VendorSpecificEventManager(); });

struct VendorSpecificEventManager::impl {
  impl(Module* module) : module_(module){};

  ~impl() {}

  void start(os::Handler* handler, hci::HciLayer* hci_layer, hci::Controller* controller) {
    module_handler_ = handler;
    hci_layer_ = hci_layer;
    controller_ = controller;
    hci_layer_->RegisterEventHandler(
        EventCode::VENDOR_SPECIFIC, handler->BindOn(this, &VendorSpecificEventManager::impl::on_vendor_specific_event));
    vendor_capabilities_ = controller->GetVendorCapabilities();
  }

  void stop() {}

  void register_event(VseSubeventCode event, common::ContextualCallback<void(VendorSpecificEventView)> handler) {
    ASSERT_LOG(
        subevent_handlers_.count(event) == 0,
        "Can not register a second handler for %02hhx (%s)",
        event,
        VseSubeventCodeText(event).c_str());
    subevent_handlers_[event] = handler;
  }

  void unregister_event(VseSubeventCode event) {
    subevent_handlers_.erase(subevent_handlers_.find(event));
  }

  bool check_event_supported(VseSubeventCode event) {
    switch (event) {
      case (VseSubeventCode::BLE_THRESHOLD): {
        if (vendor_capabilities_.total_scan_results_storage_ > 0) {
          return true;
        }
      } break;
      case (VseSubeventCode::BLE_TRACKING): {
        if (vendor_capabilities_.total_num_of_advt_tracked_ > 0) {
          return true;
        }
      } break;
      case (VseSubeventCode::DEBUG_INFO): {
        return vendor_capabilities_.debug_logging_supported_;
      } break;
      case (VseSubeventCode::BQR_EVENT): {
        return vendor_capabilities_.bluetooth_quality_report_support_;
      } break;
      default:
        LOG_WARN("Unhandled event %s", VseSubeventCodeText(event).c_str());
    }
    return false;
  }

  void on_vendor_specific_event(EventView event_view) {
    auto vendor_specific_event_view = VendorSpecificEventView::Create(event_view);
    ASSERT(vendor_specific_event_view.IsValid());
    VseSubeventCode vse_subevent_code = vendor_specific_event_view.GetSubeventCode();
    if (subevent_handlers_.find(vse_subevent_code) == subevent_handlers_.end()) {
      LOG_WARN("Unhandled vendor specific event of type 0x%02hhx", vse_subevent_code);
      return;
    }
    subevent_handlers_[vse_subevent_code].Invoke(vendor_specific_event_view);
  }

  Module* module_;
  os::Handler* module_handler_;
  hci::HciLayer* hci_layer_;
  hci::Controller* controller_;
  Controller::VendorCapabilities vendor_capabilities_;
  std::map<VseSubeventCode, common::ContextualCallback<void(VendorSpecificEventView)>> subevent_handlers_;
};

VendorSpecificEventManager::VendorSpecificEventManager() {
  pimpl_ = std::make_unique<impl>(this);
}

void VendorSpecificEventManager::ListDependencies(ModuleList* list) const {
  list->add<hci::HciLayer>();
  list->add<hci::Controller>();
}

void VendorSpecificEventManager::Start() {
  pimpl_->start(GetHandler(), GetDependency<hci::HciLayer>(), GetDependency<hci::Controller>());
}

void VendorSpecificEventManager::Stop() {
  pimpl_->stop();
  pimpl_.reset();
}

std::string VendorSpecificEventManager::ToString() const {
  return "Vendor Specific Event Manager";
}

void VendorSpecificEventManager::RegisterEventHandler(
    VseSubeventCode event, common::ContextualCallback<void(VendorSpecificEventView)> handler) {
  CallOn(pimpl_.get(), &impl::register_event, event, handler);
}

void VendorSpecificEventManager::UnregisterEventHandler(VseSubeventCode event) {
  CallOn(pimpl_.get(), &impl::unregister_event, event);
}

}  // namespace hci
}  // namespace bluetooth
