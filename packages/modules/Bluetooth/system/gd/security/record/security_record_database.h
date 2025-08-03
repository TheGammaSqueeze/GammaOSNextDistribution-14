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

#pragma once

#include <set>

#include "hci/address_with_type.h"
#include "security/record/security_record.h"
#include "security/record/security_record_storage.h"

namespace bluetooth {
namespace security {
namespace record {

class SecurityRecordDatabase {
 public:
  SecurityRecordDatabase(record::SecurityRecordStorage security_record_storage)
      : security_record_storage_(security_record_storage) {}

  using iterator = std::set<std::shared_ptr<SecurityRecord>>::iterator;

  std::shared_ptr<SecurityRecord> FindOrCreate(hci::AddressWithType address) {
    auto it = Find(address);
    // Security record check
    if (it != records_.end()) return *it;

    // No security record, create one
    auto record_ptr = std::make_shared<SecurityRecord>(address);
    records_.insert(record_ptr);
    return record_ptr;
  }

  void Remove(const hci::AddressWithType& address) {
    auto it = Find(address);

    // No record exists
    if (it == records_.end()) return;

    records_.erase(it);
    security_record_storage_.RemoveDevice(address);
  }

  iterator Find(hci::AddressWithType address) {
    for (auto it = records_.begin(); it != records_.end(); ++it) {
      std::shared_ptr<SecurityRecord> record = *it;
      if (record->identity_address_.has_value() && record->identity_address_.value() == address) return it;
      if (record->GetPseudoAddress() == address) return it;
      if (record->remote_irk.has_value() && address.IsRpaThatMatchesIrk(record->remote_irk.value())) return it;
    }
    return records_.end();
  }

  void LoadRecordsFromStorage() {
    security_record_storage_.LoadSecurityRecords(&records_);
  }

  void SaveRecordsToStorage() {
    security_record_storage_.SaveSecurityRecords(&records_);
  }

  std::set<std::shared_ptr<SecurityRecord>> records_;
  record::SecurityRecordStorage security_record_storage_;
};

}  // namespace record
}  // namespace security
}  // namespace bluetooth
