// Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef LIBBRILLO_BRILLO_UDEV_UDEV_LIST_ENTRY_H_
#define LIBBRILLO_BRILLO_UDEV_UDEV_LIST_ENTRY_H_

#include <memory>

#include <base/macros.h>
#include <brillo/brillo_export.h>

struct udev_list_entry;

namespace brillo {

// A udev list entry, which wraps a udev_list_entry C struct from libudev and
// related library functions into a C++ object.
class BRILLO_EXPORT UdevListEntry {
 public:
  // Constructs a UdevListEntry object by taking a raw pointer to a
  // udev_list_entry struct as |list_entry|. The ownership of |list_entry| is
  // not transferred, and thus it should outlive this object.
  explicit UdevListEntry(udev_list_entry* list_entry);

  virtual ~UdevListEntry() = default;

  // Wraps udev_list_entry_get_next().
  virtual std::unique_ptr<UdevListEntry> GetNext() const;

  // Wraps udev_list_entry_get_by_name().
  virtual std::unique_ptr<UdevListEntry> GetByName(const char* name) const;

  // Wraps udev_list_entry_get_name().
  virtual const char* GetName() const;

  // Wraps udev_list_entry_get_value().
  virtual const char* GetValue() const;

 private:
  // Allows MockUdevListEntry to invoke the private default constructor below.
  friend class MockUdevListEntry;

  // Constructs a UdevListEntry object without referencing a udev_list_entry
  // struct, which is only allowed to be called by MockUdevListEntry.
  UdevListEntry();

  udev_list_entry* const list_entry_;

  DISALLOW_COPY_AND_ASSIGN(UdevListEntry);
};

}  // namespace brillo

#endif  // LIBBRILLO_BRILLO_UDEV_UDEV_LIST_ENTRY_H_
