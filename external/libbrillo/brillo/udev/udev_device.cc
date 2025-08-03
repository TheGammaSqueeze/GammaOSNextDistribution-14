// Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <brillo/udev/udev_device.h>

#include <libudev.h>

#include <base/logging.h>

namespace brillo {

UdevDevice::UdevDevice() : device_(nullptr) {}

UdevDevice::UdevDevice(udev_device* device) : device_(device) {
  CHECK(device_);

  udev_device_ref(device_);
}

UdevDevice::~UdevDevice() {
  if (device_) {
    udev_device_unref(device_);
    device_ = nullptr;
  }
}

std::unique_ptr<UdevDevice> UdevDevice::GetParent() const {
  // udev_device_get_parent does not increase the reference count of the
  // returned udev_device struct.
  udev_device* parent_device = udev_device_get_parent(device_);
  return parent_device ? std::make_unique<UdevDevice>(parent_device) : nullptr;
}

std::unique_ptr<UdevDevice> UdevDevice::GetParentWithSubsystemDeviceType(
    const char* subsystem, const char* device_type) const {
  // udev_device_get_parent_with_subsystem_devtype does not increase the
  // reference count of the returned udev_device struct.
  udev_device* parent_device = udev_device_get_parent_with_subsystem_devtype(
      device_, subsystem, device_type);
  return parent_device ? std::make_unique<UdevDevice>(parent_device) : nullptr;
}

bool UdevDevice::IsInitialized() const {
  return udev_device_get_is_initialized(device_);
}

uint64_t UdevDevice::GetMicrosecondsSinceInitialized() const {
  return udev_device_get_usec_since_initialized(device_);
}

uint64_t UdevDevice::GetSequenceNumber() const {
  return udev_device_get_seqnum(device_);
}

const char* UdevDevice::GetDevicePath() const {
  return udev_device_get_devpath(device_);
}

const char* UdevDevice::GetDeviceNode() const {
  return udev_device_get_devnode(device_);
}

dev_t UdevDevice::GetDeviceNumber() const {
  return udev_device_get_devnum(device_);
}

const char* UdevDevice::GetDeviceType() const {
  return udev_device_get_devtype(device_);
}

const char* UdevDevice::GetDriver() const {
  return udev_device_get_driver(device_);
}

const char* UdevDevice::GetSubsystem() const {
  return udev_device_get_subsystem(device_);
}

const char* UdevDevice::GetSysPath() const {
  return udev_device_get_syspath(device_);
}

const char* UdevDevice::GetSysName() const {
  return udev_device_get_sysname(device_);
}

const char* UdevDevice::GetSysNumber() const {
  return udev_device_get_sysnum(device_);
}

const char* UdevDevice::GetAction() const {
  return udev_device_get_action(device_);
}

std::unique_ptr<UdevListEntry> UdevDevice::GetDeviceLinksListEntry() const {
  udev_list_entry* list_entry = udev_device_get_devlinks_list_entry(device_);
  return list_entry ? std::make_unique<UdevListEntry>(list_entry) : nullptr;
}

std::unique_ptr<UdevListEntry> UdevDevice::GetPropertiesListEntry() const {
  udev_list_entry* list_entry = udev_device_get_properties_list_entry(device_);
  return list_entry ? std::make_unique<UdevListEntry>(list_entry) : nullptr;
}

const char* UdevDevice::GetPropertyValue(const char* key) const {
  return udev_device_get_property_value(device_, key);
}

std::unique_ptr<UdevListEntry> UdevDevice::GetTagsListEntry() const {
  udev_list_entry* list_entry = udev_device_get_tags_list_entry(device_);
  return list_entry ? std::make_unique<UdevListEntry>(list_entry) : nullptr;
}

std::unique_ptr<UdevListEntry> UdevDevice::GetSysAttributeListEntry() const {
  udev_list_entry* list_entry = udev_device_get_sysattr_list_entry(device_);
  return list_entry ? std::make_unique<UdevListEntry>(list_entry) : nullptr;
}

const char* UdevDevice::GetSysAttributeValue(const char* attribute) const {
  return udev_device_get_sysattr_value(device_, attribute);
}

std::unique_ptr<UdevDevice> UdevDevice::Clone() {
  return std::make_unique<UdevDevice>(device_);
}

}  // namespace brillo
