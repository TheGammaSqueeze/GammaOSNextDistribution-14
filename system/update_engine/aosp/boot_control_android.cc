//
// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "update_engine/aosp/boot_control_android.h"

#include <memory>
#include <utility>
#include <vector>

#include <base/bind.h>
#include <base/logging.h>
#include <bootloader_message/bootloader_message.h>
#include <brillo/message_loops/message_loop.h>

#include "update_engine/aosp/dynamic_partition_control_android.h"

using std::string;

using Slot = chromeos_update_engine::BootControlInterface::Slot;

namespace {

}  // namespace

namespace chromeos_update_engine {

namespace boot_control {

// Factory defined in boot_control.h.
std::unique_ptr<BootControlInterface> CreateBootControl() {
  auto boot_control = std::make_unique<BootControlAndroid>();
  if (!boot_control->Init()) {
    return nullptr;
  }
  return std::move(boot_control);
}

}  // namespace boot_control

using android::hal::BootControlClient;
using android::hal::CommandResult;
using android::hal::BootControlVersion;

bool BootControlAndroid::Init() {
  module_ = BootControlClient::WaitForService();
  if (module_ == nullptr) {
    LOG(ERROR) << "Error getting bootctrl module.";
    return false;
  }

  LOG(INFO) << "Loaded boot control hal.";

  dynamic_control_ =
      std::make_unique<DynamicPartitionControlAndroid>(GetCurrentSlot());

  return true;
}

unsigned int BootControlAndroid::GetNumSlots() const {
  return module_->GetNumSlots();
}

BootControlInterface::Slot BootControlAndroid::GetCurrentSlot() const {
  return module_->GetCurrentSlot();
}

bool BootControlAndroid::GetPartitionDevice(const std::string& partition_name,
                                            BootControlInterface::Slot slot,
                                            bool not_in_payload,
                                            std::string* device,
                                            bool* is_dynamic) const {
  return dynamic_control_->GetPartitionDevice(partition_name,
                                              slot,
                                              GetCurrentSlot(),
                                              not_in_payload,
                                              device,
                                              is_dynamic);
}

bool BootControlAndroid::GetPartitionDevice(const string& partition_name,
                                            BootControlInterface::Slot slot,
                                            string* device) const {
  return GetPartitionDevice(
      partition_name, slot, false /* not_in_payload */, device, nullptr);
}

bool BootControlAndroid::IsSlotBootable(Slot slot) const {
  const auto ret = module_->IsSlotBootable(slot);
  if (!ret.has_value()) {
    LOG(ERROR) << "Unable to determine if slot " << SlotName(slot)
               << " is bootable";
    return false;
  }
  return ret.value();
}

bool BootControlAndroid::MarkSlotUnbootable(Slot slot) {
  const auto ret = module_->MarkSlotUnbootable(slot);
  if (!ret.IsOk()) {
    LOG(ERROR) << "Unable to call MarkSlotUnbootable for slot "
               << SlotName(slot) << ": " << ret.errMsg;
    return false;
  }
  return ret.success;
}

bool BootControlAndroid::SetActiveBootSlot(Slot slot) {
  const auto result = module_->SetActiveBootSlot(slot);
  if (!result.IsOk()) {
    LOG(ERROR) << "Unable to call SetActiveBootSlot for slot " << SlotName(slot)
               << ": " << result.errMsg;
    return false;
  }
  if (!result.success) {
    LOG(ERROR) << "Unable to set the active slot to slot " << SlotName(slot)
               << ": " << result.errMsg.c_str();
  }
  return result.success;
}

bool BootControlAndroid::MarkBootSuccessfulAsync(
    base::Callback<void(bool)> callback) {
  auto ret = module_->MarkBootSuccessful();
  if (!ret.IsOk()) {
    LOG(ERROR) << "Unable to MarkBootSuccessful: " << ret.errMsg;
    return false;
  }
  return brillo::MessageLoop::current()->PostTask(
             FROM_HERE, base::Bind(callback, ret.success)) !=
         brillo::MessageLoop::kTaskIdNull;
}

bool BootControlAndroid::IsSlotMarkedSuccessful(
    BootControlInterface::Slot slot) const {
  const auto ret = module_->IsSlotMarkedSuccessful(slot);
  CommandResult result;
  if (!ret.has_value()) {
    LOG(ERROR) << "Unable to determine if slot " << SlotName(slot)
               << " is marked successful";
    return false;
  }
  return ret.value();
}

Slot BootControlAndroid::GetActiveBootSlot() {
  if (module_->GetVersion() >= android::hal::BootControlVersion::BOOTCTL_V1_2) {
    return module_->GetActiveBootSlot();
  }
  LOG(WARNING) << "BootControl module version is lower than 1.2, "
               << __FUNCTION__ << " failed";
  return kInvalidSlot;
}

DynamicPartitionControlInterface*
BootControlAndroid::GetDynamicPartitionControl() {
  return dynamic_control_.get();
}

std::optional<PartitionDevice> BootControlAndroid::GetPartitionDevice(
    const std::string& partition_name,
    uint32_t slot,
    uint32_t current_slot,
    bool not_in_payload) const {
  return dynamic_control_->GetPartitionDevice(
      partition_name, slot, current_slot, not_in_payload);
}
}  // namespace chromeos_update_engine
