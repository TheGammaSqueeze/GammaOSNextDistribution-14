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

#include "hci/acl_manager_mock.h"
#include "hci/controller_interface_mock.h"
#include "hci/distance_measurement_manager_mock.h"
#include "hci/hci_layer.h"
#include "hci/hci_layer_mock.h"
#include "hci/le_advertising_manager_mock.h"
#include "hci/le_scanning_manager_mock.h"
#include "main/shim/entry.h"
#include "neighbor/connectability.h"
#include "neighbor/discoverability.h"
#include "neighbor/inquiry.h"
#include "neighbor/page.h"
#include "os/handler.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace hci {
namespace testing {

MockAclManager* mock_acl_manager_{nullptr};
MockControllerInterface* mock_controller_{nullptr};
shim::Dumpsys* shim_dumpsys_ = {};
MockHciLayer* mock_hci_layer_{nullptr};
os::Handler* mock_gd_shim_handler_{nullptr};
MockLeAdvertisingManager* mock_le_advertising_manager_{nullptr};
MockLeScanningManager* mock_le_scanning_manager_{nullptr};
MockDistanceMeasurementManager* mock_distance_measurement_manager_{nullptr};

}  // namespace testing
}  // namespace hci

class Dumpsys;

namespace shim {

Dumpsys* GetDumpsys() { return hci::testing::shim_dumpsys_; }
hci::AclManager* GetAclManager() { return hci::testing::mock_acl_manager_; }
hci::ControllerInterface* GetController() {
  return hci::testing::mock_controller_;
}
hci::HciLayer* GetHciLayer() { return hci::testing::mock_hci_layer_; }
hci::LeAdvertisingManager* GetAdvertising() {
  return hci::testing::mock_le_advertising_manager_;
}
hci::LeScanningManager* GetScanning() {
  return hci::testing::mock_le_scanning_manager_;
}
hci::DistanceMeasurementManager* GetDistanceMeasurementManager() {
  return hci::testing::mock_distance_measurement_manager_;
}
hci::VendorSpecificEventManager* GetVendorSpecificEventManager() {
  return nullptr;
}
neighbor::ConnectabilityModule* GetConnectability() { return nullptr; }
neighbor::DiscoverabilityModule* GetDiscoverability() { return nullptr; }
neighbor::InquiryModule* GetInquiry() { return nullptr; }
neighbor::PageModule* GetPage() { return nullptr; }
os::Handler* GetGdShimHandler() { return hci::testing::mock_gd_shim_handler_; }
hal::SnoopLogger* GetSnoopLogger() { return nullptr; }
storage::StorageModule* GetStorage() { return nullptr; }
metrics::CounterMetrics* GetCounterMetrics() { return nullptr; }
hci::MsftExtensionManager* GetMsftExtensionManager() { return nullptr; }
hci::RemoteNameRequestModule* GetRemoteNameRequest() { return nullptr; }

}  // namespace shim
}  // namespace bluetooth
