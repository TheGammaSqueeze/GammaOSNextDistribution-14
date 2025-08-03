/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "location/lbs/contexthub/nanoapps/nearby/fast_pair_filter.h"

#include <cinttypes>
#include <iterator>

#include "location/lbs/contexthub/nanoapps/nearby/bloom_filter.h"
#include "location/lbs/contexthub/nanoapps/nearby/fast_pair_account_data.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][FAST_PAIR_FILTER]"

namespace nearby {

constexpr uint16_t kFastPairUuid = 0xFE2C;
constexpr uint16_t kFastPairUuidFirstByte = 0xFE;
constexpr uint16_t kFastPairUuidSecondByte = 0x2C;
constexpr size_t kFpAccountKeyLength = 16;
constexpr size_t kFastPairModelIdLength = 3;
constexpr uint8_t kAccountKeyFirstByte[] = {
    0b00000100,  // Default.
    0b00000101,  // Recent.
    0b00000110   // In use.
};
// The key fed into Bloom Filter is the concatenation of account key, SALT, and
// RRD, and the max length is kFpAccountKeyLength + 3 x 2^4. SALT,Battery, or
// RRD length is less than 2^4 according to the spec.
constexpr size_t kMaxBloomFilterKeyLength = kFpAccountKeyLength + 48;

// Returns true if data_element is Fast Pair account.
bool IsAccountDataElement(const nearby_DataElement &data_element) {
  return (data_element.has_key &&
          data_element.key ==
              nearby_DataElement_ElementType_DE_FAST_PAIR_ACCOUNT_KEY &&
          data_element.has_value && data_element.has_value_length &&
          data_element.value_length == kFpAccountKeyLength);
}

// Returns true if filter include Fast Pair initial pair.
// Otherwise, returns false and saves account keys in account_keys.
bool CheckFastPairFilter(const nearby_BleFilter &filter,
                         chre::DynamicVector<const uint8_t *> *account_keys) {
  bool has_initial_pair = false;
  for (int i = 0; i < filter.data_element_count; i++) {
    if (IsAccountDataElement(filter.data_element[i])) {
      uint8_t account_value = 0;
      for (size_t j = 0; j < kFpAccountKeyLength; j++) {
        account_value |= filter.data_element[i].value[j];
      }
      if (account_value == 0) {
        // Account value for initial pair are all zeros.
        LOGD("Find Fast Pair initial pair filter.");
        has_initial_pair = true;
      } else {
        account_keys->push_back(
            static_cast<const uint8_t *>(filter.data_element[i].value));
      }
    }
  }
  return has_initial_pair;
}

// Fills a Fast Pair filtered result with service_data and account_key.
// Passes account_key as nullptr for initial pair.
// Returns false when filling failed due to memory overflow.
bool FillResult(const BleServiceData &service_data, const uint8_t *account_key,
                nearby_BleFilterResult *result) {
  if (result->data_element_count >= std::size(result->data_element)) {
    LOGE("Failed to fill Fast Pair result. Data Elements buffer full");
    return false;
  }
  // Sends the service data, which will be re-parsed by Fast Pair in GmsCore.
  if (!result->has_ble_service_data) {
    // the buffer size of 'result->ble_service_data' is defined in
    // ble_filter.options, which must be large enough to hold one byte service
    // length, two bytes UUID, and the service data.
    if (account_key == nullptr) {
      // Initial Pair service data only has model ID.
      static_assert(kFastPairModelIdLength + 3 <=
                    sizeof(result->ble_service_data));
    } else if (service_data.length + 3 > sizeof(result->ble_service_data)) {
      LOGE("Fast Pair BLE service data overflows the result buffer.");
      return false;
    }
    result->has_ble_service_data = true;
    // First byte is the length of service data plus two bytes UUID.
    result->ble_service_data[0] = service_data.length + 2;
    // Second and third byte includes the FP 3.2 UUID.
    result->ble_service_data[1] = kFastPairUuidFirstByte;
    result->ble_service_data[2] = kFastPairUuidSecondByte;
    // The rest bytes are service data.
    memcpy(result->ble_service_data + 3, service_data.data,
           service_data.length);
  }

  // Capacity has been checked above.
  size_t de_index = result->data_element_count;
  result->data_element[de_index].has_key = true;
  result->data_element[de_index].key =
      nearby_DataElement_ElementType_DE_FAST_PAIR_ACCOUNT_KEY;
  result->data_element[de_index].has_value_length = true;
  result->data_element[de_index].value_length = kFpAccountKeyLength;
  result->data_element[de_index].has_value = true;
  CHRE_ASSERT(sizeof(result->data_element[de_index].value) >=
              kFpAccountKeyLength);
  if (account_key != nullptr) {
    memcpy(result->data_element[de_index].value, account_key,
           kFpAccountKeyLength);
  }
  result->data_element_count++;

  result->has_result_type = true;
  result->result_type = nearby_BleFilterResult_ResultType_RESULT_FAST_PAIR;
  return true;
}

bool MatchInitialFastPair(const BleServiceData &ble_service_data,
                          nearby_BleFilterResult *result) {
  if (ble_service_data.uuid != kFastPairUuid) {
    LOGD("Not Fast Pair service data.");
    return false;
  }
  // Service data for initial pair only contains the three-byte model id.
  if (ble_service_data.length != kFastPairModelIdLength) {
    LOGD(
        "Not a initial pair whose BLE service data only includes a model "
        "id of three bytes.");
    return false;
  }
  return FillResult(ble_service_data, nullptr, result);
}

bool MatchSubsequentPair(const uint8_t *account_key,
                         const BleServiceData &service_data,
                         nearby_BleFilterResult *result) {
  LOGD("MatchSubsequentPair");
  if (service_data.uuid != kFastPairUuid) {
    LOGD("service data uuid %x is not Fast Pair uuid %x", service_data.uuid,
         kFastPairUuid);
    return false;
  }
  if (service_data.length == kFastPairModelIdLength) {
    LOGD(
        "Initial Pair advertisements, not proceed to subsequent pair "
        "filtering.");
    return false;
  }
  FastPairAccountData account_data = FastPairAccountData::Parse(
      ByteArray(const_cast<uint8_t *>(service_data.data), service_data.length));
  if (!account_data.is_valid) {
    return false;
  }
  LOGD_SENSITIVE_INFO("Fast Pair Bloom Filter:");
  for (size_t i = 0; i < account_data.filter.length; i++) {
    LOGD_SENSITIVE_INFO("%x", account_data.filter.data[i]);
  }
  if (account_data.filter.length > BloomFilter::kMaxBloomFilterByteSize) {
    LOGE("Subsequent Pair Bloom Filter size %zu exceeds: %zu",
         account_data.filter.length, BloomFilter::kMaxBloomFilterByteSize);
    return false;
  }
  BloomFilter bloom_filter =
      BloomFilter(account_data.filter.data, account_data.filter.length);
  // SALT, BATTERY, and RRD length must be less than 2^4 in FastPairAccountData
  // implementation based on the spec.
  CHRE_ASSERT((kFpAccountKeyLength + account_data.salt.length +
               account_data.battery.length + account_data.rrd.length) <=
              kMaxBloomFilterKeyLength);
  uint8_t key[kMaxBloomFilterKeyLength];
  size_t pos = 0;
  memcpy(&key[pos], account_key, kFpAccountKeyLength);
  pos += kFpAccountKeyLength;
  memcpy(&key[pos], account_data.salt.data, account_data.salt.length);
  pos += account_data.salt.length;
  LOGD_SENSITIVE_INFO("Fast Pair subsequent pair SALT");
  for (size_t i = 0; i < account_data.salt.length; i++) {
    LOGD_SENSITIVE_INFO("%x", account_data.salt.data[i]);
  }
  memcpy(&key[pos], account_data.battery.data, account_data.battery.length);
  pos += account_data.battery.length;
  LOGD_SENSITIVE_INFO("Fast Pair subsequent pair battery:");
  for (size_t i = 0; i < account_data.battery.length; i++) {
    LOGD_SENSITIVE_INFO("%x", account_data.battery.data[i]);
  }
  if (account_data.version == 1) {
    memcpy(&key[pos], account_data.rrd.data, account_data.rrd.length);
    pos += account_data.rrd.length;
    LOGD_SENSITIVE_INFO("Fast Pair subsequent pair RRD");
    for (size_t i = 0; i < account_data.rrd.length; i++) {
      LOGD_SENSITIVE_INFO("%x", account_data.rrd.data[i]);
    }
  }

  LOGD_SENSITIVE_INFO("Fast Pair subsequent pair combined key:");
  for (size_t i = 0; i < pos; i++) {
    LOGD_SENSITIVE_INFO("%x", key[i]);
  }
  bool matched = bloom_filter.MayContain(key, pos);
  if (!matched && account_data.rrd.length > 0) {
    // Flip the first byte to 4, 5, 6 when RRD is presented.
    for (uint8_t firstByte : kAccountKeyFirstByte) {
      key[0] = firstByte;
      matched = bloom_filter.MayContain(key, pos);
      if (matched) break;
    }
  }
  if (matched) {
    LOGD("Subsequent Pair match succeeds.");
    return FillResult(service_data, account_key, result);
  } else {
    return false;
  }
}

bool MatchFastPair(const nearby_BleFilter &filter,
                   const BleScanRecord &scan_record,
                   nearby_BleFilterResult *result) {
  LOGD("MatchFastPair");
  chre::DynamicVector<const uint8_t *> account_keys;
  if (CheckFastPairFilter(filter, &account_keys)) {
    LOGD("Fast Pair initial pair filter found.");
    for (const auto &ble_service_data : scan_record.service_data) {
      if (MatchInitialFastPair(ble_service_data, result)) {
        return true;
      }
    }
  } else {
    for (const auto account_key : account_keys) {
      for (const auto &ble_service_data : scan_record.service_data) {
        if (MatchSubsequentPair(account_key, ble_service_data, result)) {
          return true;
        }
      }
    }
  }
  return false;
}

}  // namespace nearby
