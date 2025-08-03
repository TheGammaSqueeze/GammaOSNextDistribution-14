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

#define LOG_TAG "bt_gd_shim"

#include "main/shim/stack.h"

#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>

#include <string>

#include "common/init_flags.h"
#include "common/strings.h"
#include "device/include/controller.h"
#include "hal/hci_hal.h"
#include "hci/acl_manager.h"
#include "hci/acl_manager/acl_scheduler.h"
#include "hci/controller.h"
#include "hci/distance_measurement_manager.h"
#include "hci/hci_layer.h"
#include "hci/le_advertising_manager.h"
#include "hci/le_scanning_manager.h"
#include "hci/msft.h"
#include "hci/remote_name_request.h"
#include "hci/vendor_specific_event_manager.h"
#include "main/shim/acl_legacy_interface.h"
#include "main/shim/distance_measurement_manager.h"
#include "main/shim/hci_layer.h"
#include "main/shim/le_advertising_manager.h"
#include "main/shim/le_scanning_manager.h"
#include "metrics/counter_metrics.h"
#include "os/log.h"
#include "shim/dumpsys.h"
#include "storage/storage_module.h"
#include "sysprops/sysprops_module.h"

namespace bluetooth {
namespace shim {

using ::bluetooth::common::InitFlags;
using ::bluetooth::common::StringFormat;

Stack* Stack::GetInstance() {
  static Stack instance;
  return &instance;
}

void Stack::StartEverything() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT_LOG(!is_running_, "%s Gd stack already running", __func__);
  LOG_INFO("%s Starting Gd stack", __func__);
  ModuleList modules;

  modules.add<metrics::CounterMetrics>();
  modules.add<hal::HciHal>();
  modules.add<hci::HciLayer>();
  modules.add<storage::StorageModule>();
  modules.add<shim::Dumpsys>();
  modules.add<hci::VendorSpecificEventManager>();
  modules.add<sysprops::SyspropsModule>();

  modules.add<hci::Controller>();
  modules.add<hci::acl_manager::AclScheduler>();
  modules.add<hci::AclManager>();
  modules.add<hci::RemoteNameRequestModule>();
  modules.add<hci::LeAdvertisingManager>();
  modules.add<hci::MsftExtensionManager>();
  modules.add<hci::LeScanningManager>();
  modules.add<hci::DistanceMeasurementManager>();
  Start(&modules);
  is_running_ = true;
  // Make sure the leaf modules are started
  ASSERT(stack_manager_.GetInstance<storage::StorageModule>() != nullptr);
  ASSERT(stack_manager_.GetInstance<shim::Dumpsys>() != nullptr);
  if (stack_manager_.IsStarted<hci::Controller>()) {
    acl_ = new legacy::Acl(
        stack_handler_, legacy::GetAclInterface(),
        controller_get_interface()->get_ble_acceptlist_size(),
        controller_get_interface()->get_ble_resolving_list_max_size());
  } else {
    LOG_ERROR("Unable to create shim ACL layer as Controller has not started");
  }

  bluetooth::shim::hci_on_reset_complete();
  bluetooth::shim::init_advertising_manager();
  bluetooth::shim::init_scanning_manager();
  bluetooth::shim::init_distance_measurement_manager();
}

void Stack::StartModuleStack(const ModuleList* modules,
                             const os::Thread* thread) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT_LOG(!is_running_, "%s Gd stack already running", __func__);
  stack_thread_ = const_cast<os::Thread*>(thread);
  LOG_INFO("Starting Gd stack");

  stack_manager_.StartUp(const_cast<ModuleList*>(modules), stack_thread_);
  stack_handler_ = new os::Handler(stack_thread_);

  num_modules_ = modules->NumModules();
  is_running_ = true;
}

void Stack::Start(ModuleList* modules) {
  ASSERT_LOG(!is_running_, "%s Gd stack already running", __func__);
  LOG_INFO("%s Starting Gd stack", __func__);

  stack_thread_ =
      new os::Thread("gd_stack_thread", os::Thread::Priority::REAL_TIME);
  stack_manager_.StartUp(modules, stack_thread_);

  stack_handler_ = new os::Handler(stack_thread_);

  LOG_INFO("%s Successfully toggled Gd stack", __func__);
}

void Stack::Stop() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  bluetooth::shim::hci_on_shutting_down();

  // Make sure gd acl flag is enabled and we started it up
  if (acl_ != nullptr) {
    acl_->FinalShutdown();
    delete acl_;
    acl_ = nullptr;
  }

  ASSERT_LOG(is_running_, "%s Gd stack not running", __func__);
  is_running_ = false;

  delete btm_;
  btm_ = nullptr;

  stack_handler_->Clear();

  stack_manager_.ShutDown();

  delete stack_handler_;
  stack_handler_ = nullptr;

  stack_thread_->Stop();
  delete stack_thread_;
  stack_thread_ = nullptr;

  LOG_INFO("%s Successfully shut down Gd stack", __func__);
}

bool Stack::IsRunning() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  return is_running_;
}

StackManager* Stack::GetStackManager() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return &stack_manager_;
}

const StackManager* Stack::GetStackManager() const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return &stack_manager_;
}

legacy::Acl* Stack::GetAcl() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  ASSERT_LOG(acl_ != nullptr, "Acl shim layer has not been created");
  return acl_;
}

LinkPolicyInterface* Stack::LinkPolicy() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  ASSERT_LOG(acl_ != nullptr, "Acl shim layer has not been created");
  return acl_;
}

Btm* Stack::GetBtm() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return btm_;
}

os::Handler* Stack::GetHandler() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return stack_handler_;
}

bool Stack::IsDumpsysModuleStarted() const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  return GetStackManager()->IsStarted<Dumpsys>();
}

void Stack::LockForDumpsys(std::function<void()> dumpsys_callback) {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  dumpsys_callback();
}

}  // namespace shim
}  // namespace bluetooth
