/*
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#pragma once

#include <set>

#include "os/handler.h"
#include "security/record/security_record.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace security {
namespace record {

#ifdef __ANDROID__
static const char* CONFIG_FILE_PATH = "/data/misc/bluedroid/bt_config.conf";
#else   // !__ANDROID__
static const char* CONFIG_FILE_PATH = "bt_config.conf";
#endif  // __ANDROID__

class SecurityRecordStorage {
 public:
  SecurityRecordStorage(storage::StorageModule* storage_module, os::Handler* handler);

  /**
   * Iterates through given vector and stores each record's metadata to disk.
   *
   * <p>Job gets posted to the Handler.
   *
   * @param records set of shared pointers to records.
   */
  void SaveSecurityRecords(std::set<std::shared_ptr<record::SecurityRecord>>* records);

  /**
   * Reads the record metadata from disk and converts each item into a SecurityRecord.
   *
   * <p>Job gets posted to the Handler.
   *
   * @param records set of shared pointers to records.
   */
  void LoadSecurityRecords(std::set<std::shared_ptr<record::SecurityRecord>>* records);

  /**
   * Removes a device from the storage
   *
   * @param address of device to remove
   */
  void RemoveDevice(hci::AddressWithType address);

 private:
  storage::StorageModule* storage_module_ __attribute__((unused));
  os::Handler* handler_ __attribute__((unused));
};

}  // namespace record
}  // namespace security
}  // namespace bluetooth
