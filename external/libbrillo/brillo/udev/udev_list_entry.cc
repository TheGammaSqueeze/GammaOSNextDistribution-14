// Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <brillo/udev/udev_list_entry.h>

#include <libudev.h>

#include <base/logging.h>

namespace brillo {

UdevListEntry::UdevListEntry() : list_entry_(nullptr) {}

UdevListEntry::UdevListEntry(udev_list_entry* list_entry)
    : list_entry_(list_entry) {
  CHECK(list_entry_);
}

std::unique_ptr<UdevListEntry> UdevListEntry::GetNext() const {
  udev_list_entry* list_entry = udev_list_entry_get_next(list_entry_);
  return list_entry ? std::make_unique<UdevListEntry>(list_entry) : nullptr;
}

std::unique_ptr<UdevListEntry> UdevListEntry::GetByName(
    const char* name) const {
  udev_list_entry* list_entry = udev_list_entry_get_by_name(list_entry_, name);
  return list_entry ? std::make_unique<UdevListEntry>(list_entry) : nullptr;
}

const char* UdevListEntry::GetName() const {
  return udev_list_entry_get_name(list_entry_);
}

const char* UdevListEntry::GetValue() const {
  return udev_list_entry_get_value(list_entry_);
}

}  // namespace brillo
