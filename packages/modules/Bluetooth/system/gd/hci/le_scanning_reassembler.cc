/*
 * Copyright 2023 The Android Open Source Project
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
#include "hci/le_scanning_reassembler.h"

#include <memory>
#include <unordered_map>

#include "hci/acl_manager.h"
#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "hci/le_periodic_sync_manager.h"
#include "hci/le_scanning_interface.h"
#include "hci/vendor_specific_event_manager.h"
#include "module.h"
#include "os/handler.h"
#include "os/log.h"
#include "storage/storage_module.h"

namespace bluetooth::hci {

std::optional<LeScanningReassembler::CompleteAdvertisingData>
LeScanningReassembler::ProcessAdvertisingReport(
    uint16_t event_type,
    uint8_t address_type,
    Address address,
    uint8_t advertising_sid,
    const std::vector<uint8_t>& advertising_data) {
  bool is_scannable = event_type & (1 << kScannableBit);
  bool is_scan_response = event_type & (1 << kScanResponseBit);
  bool is_legacy = event_type & (1 << kLegacyBit);
  DataStatus data_status = DataStatus((event_type >> kDataStatusBits) & 0x3);

  if (address_type != (uint8_t)DirectAdvertisingAddressType::NO_ADDRESS_PROVIDED &&
      address == Address::kEmpty) {
    LOG_WARN("Ignoring non-anonymous advertising report with empty address");
    return {};
  }

  AdvertisingKey key(address, DirectAdvertisingAddressType(address_type), advertising_sid);

  // Ignore scan responses received without a matching advertising event.
  if (is_scan_response && (ignore_scan_responses_ || !ContainsFragment(key))) {
    LOG_INFO("Ignoring scan response received without advertising event");
    return {};
  }

  // Legacy advertising is always complete, we can drop
  // the previous data as safety measure if the report is not a scan
  // response.
  if (is_legacy && !is_scan_response) {
    LOG_DEBUG("Dropping repeated legacy advertising data");
    RemoveFragment(key);
  }

  // Concatenate the data with existing fragments.
  std::list<AdvertisingFragment>::iterator advertising_fragment =
      AppendFragment(key, event_type, advertising_data);

  // Trim the advertising data when the complete payload is received.
  if (data_status != DataStatus::CONTINUING) {
    advertising_fragment->data = TrimAdvertisingData(advertising_fragment->data);
  }

  // TODO(b/272120114) waiting for a scan response here is prone to failure as the
  // SCAN_REQ PDUs can be rejected by the advertiser according to the
  // advertising filter parameter.
  bool expect_scan_response = is_scannable && !is_scan_response && !ignore_scan_responses_;

  // Check if we should wait for additional fragments:
  // - For legacy advertising, when a scan response is expected.
  // - For extended advertising, when the current data is marked
  //   incomplete OR when a scan response is expected.
  if (data_status == DataStatus::CONTINUING || expect_scan_response) {
    return {};
  }

  // Otherwise the full advertising report has been reassembled,
  // removed the cache entry and return the complete advertising data.
  CompleteAdvertisingData result{
      .extended_event_type = advertising_fragment->extended_event_type,
      .data = std::move(advertising_fragment->data)};
  cache_.erase(advertising_fragment);
  return result;
}

std::optional<std::vector<uint8_t>> LeScanningReassembler::ProcessPeriodicAdvertisingReport(
    uint16_t sync_handle, DataStatus data_status, const std::vector<uint8_t>& advertising_data) {
  // Concatenate the data with existing fragments.
  std::list<PeriodicAdvertisingFragment>::iterator advertising_fragment =
      AppendPeriodicFragment(sync_handle, advertising_data);

  // Return and wait for additional fragments if the data is marked as
  // incomplete.
  if (data_status == DataStatus::CONTINUING) {
    return {};
  }

  // The complete payload has been received; trim the advertising data,
  // remove the cache entry and return the complete advertising data.
  std::vector<uint8_t> result = TrimAdvertisingData(advertising_fragment->data);
  periodic_cache_.erase(advertising_fragment);
  return result;
}

/// Trim the advertising data by removing empty or overflowing
/// GAP Data entries.
std::vector<uint8_t> LeScanningReassembler::TrimAdvertisingData(
    const std::vector<uint8_t>& advertising_data) {
  // Remove empty and overflowing entries from the advertising data.
  std::vector<uint8_t> significant_advertising_data;
  for (size_t offset = 0; offset < advertising_data.size();) {
    size_t remaining_size = advertising_data.size() - offset;
    uint8_t entry_size = advertising_data[offset];

    if (entry_size != 0 && entry_size < remaining_size) {
      significant_advertising_data.push_back(entry_size);
      significant_advertising_data.insert(
          significant_advertising_data.end(),
          advertising_data.begin() + offset + 1,
          advertising_data.begin() + offset + 1 + entry_size);
    }

    offset += entry_size + 1;
  }

  return significant_advertising_data;
}

LeScanningReassembler::AdvertisingKey::AdvertisingKey(
    Address address, DirectAdvertisingAddressType address_type, uint8_t sid)
    : address(), sid() {
  // The address type is NO_ADDRESS_PROVIDED for anonymous advertising.
  if (address_type != DirectAdvertisingAddressType::NO_ADDRESS_PROVIDED) {
    this->address = AddressWithType(address, AddressType(address_type));
  }
  // 0xff is reserved to indicate that the ADI field was not present
  // in the ADV_EXT_IND PDU.
  if (sid != 0xff) {
    this->sid = sid;
  }
}

bool LeScanningReassembler::AdvertisingKey::operator==(const AdvertisingKey& other) {
  return address == other.address && sid == other.sid;
}

/// Append to the current advertising data of the selected advertiser.
/// If the advertiser is unknown a new entry is added, optionally by
/// dropping the oldest advertiser.
std::list<LeScanningReassembler::AdvertisingFragment>::iterator
LeScanningReassembler::AppendFragment(
    const AdvertisingKey& key, uint16_t extended_event_type, const std::vector<uint8_t>& data) {
  auto it = FindFragment(key);
  if (it != cache_.end()) {
    // Legacy scan responses don't contain a 'connectable' bit, so this adds the
    // 'connectable' bit from the initial report.
    if ((extended_event_type & (1 << kLegacyBit)) &&
        (extended_event_type & (1 << kScanResponseBit))) {
      it->extended_event_type =
          extended_event_type | (it->extended_event_type & (1 << kConnectableBit));
    } else {
      it->extended_event_type = extended_event_type;
    }
    it->data.insert(it->data.end(), data.cbegin(), data.cend());
    return it;
  }

  if (cache_.size() > kMaximumCacheSize) {
    cache_.pop_back();
  }

  cache_.emplace_front(key, extended_event_type, data);
  return cache_.begin();
}

void LeScanningReassembler::RemoveFragment(const AdvertisingKey& key) {
  auto it = FindFragment(key);
  if (it != cache_.end()) {
    cache_.erase(it);
  }
}

bool LeScanningReassembler::ContainsFragment(const AdvertisingKey& key) {
  return FindFragment(key) != cache_.end();
}

std::list<LeScanningReassembler::AdvertisingFragment>::iterator LeScanningReassembler::FindFragment(
    const AdvertisingKey& key) {
  for (auto it = cache_.begin(); it != cache_.end(); it++) {
    if (it->key == key) {
      return it;
    }
  }
  return cache_.end();
}

/// Append to the current advertising data of the selected periodic advertiser.
/// If the advertiser is unknown a new entry is added, optionally by
/// dropping the oldest advertiser.
std::list<LeScanningReassembler::PeriodicAdvertisingFragment>::iterator
LeScanningReassembler::AppendPeriodicFragment(
    uint16_t sync_handle, const std::vector<uint8_t>& data) {
  auto it = FindPeriodicFragment(sync_handle);
  if (it != periodic_cache_.end()) {
    it->data.insert(it->data.end(), data.cbegin(), data.cend());
    return it;
  }

  if (periodic_cache_.size() > kMaximumPeriodicCacheSize) {
    periodic_cache_.pop_back();
  }

  periodic_cache_.emplace_front(sync_handle, data);
  return periodic_cache_.begin();
}

std::list<LeScanningReassembler::PeriodicAdvertisingFragment>::iterator
LeScanningReassembler::FindPeriodicFragment(uint16_t sync_handle) {
  for (auto it = periodic_cache_.begin(); it != periodic_cache_.end(); it++) {
    if (it->sync_handle == sync_handle) {
      return it;
    }
  }
  return periodic_cache_.end();
}

}  // namespace bluetooth::hci
