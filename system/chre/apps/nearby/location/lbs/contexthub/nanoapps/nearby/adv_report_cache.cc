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

#include "location/lbs/contexthub/nanoapps/nearby/adv_report_cache.h"

#include <utility>

#include "chre_api/chre.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][ADV_CACHE]"

namespace nearby {

void AdvReportCache::Clear() {
  // Release all resources.
  for (const auto &report : cache_reports_) {
    if (report.dataLength == 0) {
      continue;
    }
    chreHeapFree(const_cast<uint8_t *>(report.data));
  }
  cache_reports_.clear();
}

void AdvReportCache::Refresh() {
  if (cache_expire_nanosec_ == kMaxExpireTimeNanoSec) {
    return;
  }

  uint64_t current_time = chreGetTime();
  size_t index = 0;
  while (index < cache_reports_.size()) {
    if (current_time - cache_reports_[index].timestamp >
        cache_expire_nanosec_) {
      // TODO(b/285043291): Refactor cache element by wrapper struct/class
      // which deallocates data in its destructor.
      chreHeapFree(const_cast<uint8_t *>(cache_reports_[index].data));
      // The index does not need to increase because the current element is
      // replaced by the end of the element and the list is resized.
      cache_reports_.swap(index, cache_reports_.size() - 1);
      cache_reports_.resize(cache_reports_.size() - 1);
    } else {
      ++index;
    }
  }
}

void AdvReportCache::RefreshIfNeeded() {
  if (cache_reports_.size() > kRefreshCacheCountThreshold) {
    Refresh();
  }
}

void AdvReportCache::Push(const chreBleAdvertisingReport &event_report) {
#ifdef NEARBY_PROFILE
  ashProfileBegin(&profile_data_);
#endif
  bool is_duplicate = false;
  for (auto &cache_report : cache_reports_) {
    if (cache_report.addressType == event_report.addressType &&
        memcmp(cache_report.address, event_report.address,
               CHRE_BLE_ADDRESS_LEN) == 0 &&
        cache_report.dataLength == event_report.dataLength &&
        memcmp(cache_report.data, event_report.data, cache_report.dataLength) ==
            0) {
      // Updates RSSI by max value in the duplicated report.
      if (cache_report.rssi == CHRE_BLE_RSSI_NONE ||
          (event_report.rssi != CHRE_BLE_RSSI_NONE &&
           event_report.rssi > cache_report.rssi)) {
        cache_report.rssi = event_report.rssi;
      }
      // Updates timestamp to latest in the duplicated report.
      if (event_report.timestamp > cache_report.timestamp) {
        cache_report.timestamp = event_report.timestamp;
      }
      is_duplicate = true;
      break;
    }
  }
  if (!is_duplicate) {
    LOGD("Adds to advertising reports cache");
    // Copies advertise report by value.
    chreBleAdvertisingReport new_report = event_report;
    // Allocates advertise data and copy it.
    uint16_t dataLength = event_report.dataLength;
    uint8_t *data = nullptr;
    if (dataLength > 0) {
      data =
          static_cast<uint8_t *>(chreHeapAlloc(sizeof(uint8_t) * dataLength));
      if (data == nullptr) {
        LOGE("Memory allocation failed!");
        // Clean up expired cache elements for which heap memory is allocated.
        Refresh();
        return;
      }
      memcpy(data, event_report.data, dataLength);
      new_report.data = data;
    }
    if (!cache_reports_.push_back(std::move(new_report))) {
      LOGE("Pushes advertise report failed!");
      if (data != nullptr) {
        chreHeapFree(const_cast<uint8_t *>(data));
      }
    }
  } else {
    LOGD("Duplicated report in advertising reports cache");
  }
#ifdef NEARBY_PROFILE
  ashProfileEnd(&profile_data_, nullptr /* output */);
#endif
}

}  // namespace nearby
