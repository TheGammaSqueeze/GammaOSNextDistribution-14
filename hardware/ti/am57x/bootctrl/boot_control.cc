/*
 * Copyright (C) Texas Instruments Incorporated - http://www.ti.com/
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

#include <log/log.h>
#include <cutils/properties.h>
#include <zlib.h>
#include <hardware/boot_control.h>
#include <bootloader_message.h>

#include <string>

#define BOOT_SLOT_PROP "ro.boot.slot_suffix"

struct BootControlPrivate {
  // The base struct needs to be first in the list.
  boot_control_module_t base;

  // Whether this struct was initialized with data from the bootloader message
  // that doesn't change until next reboot.
  bool initialized;

  // The path to the misc_device as reported in the fstab.
  const char* misc_device;

  // The number of slots present on the device.
  unsigned int num_slots;

  // The slot where we are running from.
  unsigned int current_slot;
};

constexpr unsigned int kMaxNumSlots =
  sizeof(bootloader_control::slot_info) /
  sizeof(bootloader_control::slot_info[0]);

constexpr const char* kSlotSuffixes[kMaxNumSlots] = { "_a", "_b", "_c", "_d" };

// Return the little-endian representation of the CRC-32 of the first fields
// in |boot_ctrl| up to the crc32_le field.
static uint32_t GetBootloaderControlCRC(const bootloader_control* boot_ctrl) {
  return crc32(0, (const uint8_t*)boot_ctrl,
               offsetof(bootloader_control, crc32_le));
}

static bool LoadBootloaderControl(const char* misc_device,
                                  bootloader_control* boot_ctrl) {
  std::string str_err;
  if (read_bootloader_control_from(boot_ctrl, misc_device, &str_err))
    return true;

  ALOGE("%s", str_err.c_str());

  return false;
}

static bool SaveBootloaderControl(const char* misc_device,
                                  bootloader_control* boot_ctrl) {
  boot_ctrl->crc32_le = GetBootloaderControlCRC(boot_ctrl);

  std::string str_err;
  if (write_bootloader_control_to(boot_ctrl, misc_device, &str_err))
    return true;

  ALOGE("%s", str_err.c_str());

  return false;
}

// Return the index of the slot suffix passed or -1 if not a valid slot suffix.
static int SlotSuffixToIndex(const char* suffix) {
  for (unsigned int slot = 0; slot < kMaxNumSlots; ++slot) {
    if (!strcmp(kSlotSuffixes[slot], suffix)) return slot;
  }

  return -1;
}

static bool IsInitialized(const BootControlPrivate* module) {
  if (!module->initialized) {
    ALOGW("Module not initialized");
    return false;
  }

  return true;
}

void BootControlInit(boot_control_module_t* module) {
  struct BootControlPrivate* bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (bootctrl_module->initialized) return;

  if (!module) {
    ALOGE("Invalid argument passed to %s", __func__);
    return;
  }

  ALOGI("Init %s", module->common.name);

  // Initialize the current_slot from the read-only property. If the property
  // was not set (from either the command line or the device tree), we can later
  // initialize it from the bootloader_control struct.
  char suffix_prop[PROPERTY_VALUE_MAX] = {0};
  property_get(BOOT_SLOT_PROP, suffix_prop, "");
  bootctrl_module->current_slot = SlotSuffixToIndex(suffix_prop);

  std::string err;
  std::string device = get_bootloader_message_blk_device(&err);

  bootloader_control boot_ctrl;
  if (!LoadBootloaderControl(device.c_str(), &boot_ctrl))
    ALOGE("Error loading metadata");

  // Note that since there isn't a module unload function this memory is leaked.
  bootctrl_module->misc_device = strdup(device.c_str());
  uint32_t computed_crc32 = GetBootloaderControlCRC(&boot_ctrl);
  if (boot_ctrl.crc32_le != computed_crc32) {
    ALOGE("Invalid boot control found, expected CRC-32 0x%04X, "
          "but found 0x%04X. Should re-initializing A/B metadata.",
          computed_crc32, boot_ctrl.crc32_le);
    return;
  }

  std::string metadata_suffix = "_" + std::string(boot_ctrl.slot_suffix);
  if (SlotSuffixToIndex(metadata_suffix.c_str()) !=
      bootctrl_module->current_slot) {
    ALOGE("Kernel slot argument and A/B metadata do not match, "
          "%s=%s, slot metadata=%s", BOOT_SLOT_PROP, suffix_prop,
          boot_ctrl.slot_suffix);
    return;
  }

  bootctrl_module->initialized = true;
  bootctrl_module->num_slots = boot_ctrl.nb_slot;

  ALOGI("Current slot: %s(%d), number of slots: %d", boot_ctrl.slot_suffix,
        bootctrl_module->current_slot, bootctrl_module->num_slots);

  return;
}

unsigned int GetNumberSlots(boot_control_module_t* module) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module)) return -1;

  return bootctrl_module->num_slots;
}

unsigned int GetCurrentSlot(boot_control_module_t* module) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module)) return -1;

  return bootctrl_module->current_slot;
}

int IsSlotMarkedSuccessful(boot_control_module_t* module, unsigned int slot) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module)) return -1;

  if (slot >= kMaxNumSlots || slot >= bootctrl_module->num_slots) {
    // Invalid slot number.
    return -1;
  }

  bootloader_control bootctrl;
  if (!LoadBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  return (bootctrl.slot_info[slot].successful_boot &&
          bootctrl.slot_info[slot].tries_remaining);
}

int MarkBootSuccessful(boot_control_module_t* module) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module)) return -1;

  bootloader_control bootctrl;
  if (!LoadBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  bootctrl.slot_info[bootctrl_module->current_slot].successful_boot = 1;
  // tries_remaining == 0 means that the slot is not bootable anymore, make
  // sure we mark the current slot as bootable if it succeeds in the last
  // attempt.
  bootctrl.slot_info[bootctrl_module->current_slot].tries_remaining = 1;
  if (!SaveBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  ALOGI("Slot %d is marked as successfully booted",
        bootctrl_module->current_slot);

  return 0;
}

int SetActiveBootSlot(boot_control_module_t* module, unsigned int slot) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module))
    return -1;

  if (slot >= kMaxNumSlots || slot >= bootctrl_module->num_slots) {
    // Invalid slot number.
    return -1;
  }

  bootloader_control bootctrl;
  if (!LoadBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  // Set every other slot with a lower priority than the new "active" slot.
  const unsigned int kActivePriority = 15;
  const unsigned int kActiveTries = 6;
  for (unsigned int i = 0; i < bootctrl_module->num_slots; ++i) {
    if (i != slot) {
      if (bootctrl.slot_info[i].priority >= kActivePriority)
        bootctrl.slot_info[i].priority = kActivePriority - 1;
    }
  }

  // Note that setting a slot as active doesn't change the successful bit.
  // The successful bit will only be changed by setSlotAsUnbootable().
  bootctrl.slot_info[slot].priority = kActivePriority;
  bootctrl.slot_info[slot].tries_remaining = kActiveTries;

  // Setting the current slot as active is a way to revert the operation that
  // set *another* slot as active at the end of an updater. This is commonly
  // used to cancel the pending update. We should only reset the verity_corrpted
  // bit when attempting a new slot, otherwise the verity bit on the current
  // slot would be flip.
  if (slot != bootctrl_module->current_slot)
    bootctrl.slot_info[slot].verity_corrupted = 0;

  if (!SaveBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  ALOGI("Slot %d is set as active", slot);

  return 0;
}

int SetSlotAsUnbootable(boot_control_module_t* module, unsigned int slot) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module))
    return -1;

  if (slot >= kMaxNumSlots || slot >= bootctrl_module->num_slots) {
    // Invalid slot number.
    return -1;
  }

  bootloader_control bootctrl;
  if (!LoadBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  // The only way to mark a slot as unbootable, regardless of the priority is to
  // set the tries_remaining to 0.
  bootctrl.slot_info[slot].successful_boot = 0;
  bootctrl.slot_info[slot].tries_remaining = 0;
  if (!SaveBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  ALOGI("Slot %d is marked as unbootable", slot);

  return 0;
}

int IsSlotBootable(struct boot_control_module* module, unsigned int slot) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module)) return -1;

  if (slot >= kMaxNumSlots || slot >= bootctrl_module->num_slots) {
    // Invalid slot number.
    return -1;
  }

  bootloader_control bootctrl;
  if (!LoadBootloaderControl(bootctrl_module->misc_device, &bootctrl))
    return -1;

  return bootctrl.slot_info[slot].tries_remaining;
}

const char* GetSuffix(boot_control_module_t* module, unsigned int slot) {
  BootControlPrivate* const bootctrl_module =
    reinterpret_cast<BootControlPrivate*>(module);

  if (!IsInitialized(bootctrl_module)) return NULL;

  if (slot >= kMaxNumSlots || slot >= bootctrl_module->num_slots) return NULL;

  return kSlotSuffixes[slot];
}

static hw_module_methods_t boot_control_module_methods = {
  .open = NULL,
};

BootControlPrivate HAL_MODULE_INFO_SYM = {
  .base = {
    .common ={
      .tag = HARDWARE_MODULE_TAG,
      .module_api_version = 1,
      .hal_api_version = 0,
      .id = BOOT_CONTROL_HARDWARE_MODULE_ID,
      .name = "AM57xx Boot control HAL",
      .author = "Texas Instruments",
      .methods = &boot_control_module_methods
    },

    .init = BootControlInit,
    .getNumberSlots = GetNumberSlots,
    .getCurrentSlot = GetCurrentSlot,
    .markBootSuccessful = MarkBootSuccessful,
    .setActiveBootSlot = SetActiveBootSlot,
    .setSlotAsUnbootable = SetSlotAsUnbootable,
    .isSlotBootable = IsSlotBootable,
    .getSuffix = GetSuffix,
    .isSlotMarkedSuccessful = IsSlotMarkedSuccessful
  },

  .initialized = false,
  .misc_device = nullptr,
  .num_slots = 0,
  .current_slot = 0
};
