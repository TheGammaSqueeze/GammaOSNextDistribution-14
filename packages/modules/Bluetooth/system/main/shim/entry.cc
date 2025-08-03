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

#include "main/shim/entry.h"

#include "hal/snoop_logger.h"
#include "hci/acl_manager.h"
#include "hci/controller.h"
#include "hci/controller_interface.h"
#include "hci/distance_measurement_manager.h"
#include "hci/hci_layer.h"
#include "hci/le_advertising_manager.h"
#include "hci/le_scanning_manager.h"
#include "hci/msft.h"
#include "hci/remote_name_request.h"
#include "hci/vendor_specific_event_manager.h"
#include "main/shim/stack.h"
#include "metrics/counter_metrics.h"
#include "neighbor/connectability.h"
#include "neighbor/discoverability.h"
#include "neighbor/inquiry.h"
#include "neighbor/page.h"
#include "os/handler.h"
#include "shim/dumpsys.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace shim {

os::Handler* GetGdShimHandler() { return Stack::GetInstance()->GetHandler(); }

hci::LeAdvertisingManager* GetAdvertising() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::LeAdvertisingManager>();
}

hci::ControllerInterface* GetController() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::Controller>();
}

neighbor::ConnectabilityModule* GetConnectability() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<neighbor::ConnectabilityModule>();
}

neighbor::DiscoverabilityModule* GetDiscoverability() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<neighbor::DiscoverabilityModule>();
}

Dumpsys* GetDumpsys() {
  return Stack::GetInstance()->GetStackManager()->GetInstance<Dumpsys>();
}

neighbor::InquiryModule* GetInquiry() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<neighbor::InquiryModule>();
}

hci::HciLayer* GetHciLayer() {
  return Stack::GetInstance()->GetStackManager()->GetInstance<hci::HciLayer>();
}

neighbor::PageModule* GetPage() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<neighbor::PageModule>();
}

hci::RemoteNameRequestModule* GetRemoteNameRequest() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::RemoteNameRequestModule>();
}

hci::LeScanningManager* GetScanning() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::LeScanningManager>();
}

hci::DistanceMeasurementManager* GetDistanceMeasurementManager() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::DistanceMeasurementManager>();
}

hal::SnoopLogger* GetSnoopLogger() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hal::SnoopLogger>();
}

storage::StorageModule* GetStorage() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<storage::StorageModule>();
}

hci::AclManager* GetAclManager() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::AclManager>();
}

hci::VendorSpecificEventManager* GetVendorSpecificEventManager() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::VendorSpecificEventManager>();
}

metrics::CounterMetrics* GetCounterMetrics() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<metrics::CounterMetrics>();
}

hci::MsftExtensionManager* GetMsftExtensionManager() {
  return Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<hci::MsftExtensionManager>();
}

}  // namespace shim
}  // namespace bluetooth
