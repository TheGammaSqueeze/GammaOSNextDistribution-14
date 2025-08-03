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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_ADV_REPORT_CACHE_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_ADV_REPORT_CACHE_H_

#include "third_party/contexthub/chre/util/include/chre/util/time.h"
#ifdef NEARBY_PROFILE
#include <ash/profile.h>
#endif

#include <limits>
#include <utility>

#include "third_party/contexthub/chre/util/include/chre/util/dynamic_vector.h"

namespace nearby {

class AdvReportCache {
 public:
  // Constructs advertise report cache.
#ifdef NEARBY_PROFILE
  AdvReportCache() {
    ashProfileInit(
        &profile_data_, "[NEARBY_ADV_CACHE_PERF]", 1000 /* print_interval_ms */,
        false /* report_total_thread_cycles */, true /* printCsvFormat */);
  }
#else
  AdvReportCache() = default;
#endif

  // Deconstructs advertise report cache and releases all resources.
  ~AdvReportCache() {
    Clear();
  }

  // Move assignment operator
  AdvReportCache &operator=(AdvReportCache &&other) {
    if (&other == this) {
      return *this;
    }
    Clear();
    cache_reports_ = std::move(other.cache_reports_);
    cache_expire_nanosec_ = other.cache_expire_nanosec_;
    return *this;
  }

  // Releases all resources {cache element, heap memory} in cache.
  void Clear();

  // Adds advertise report to cache with deduplicating by
  // unique key which is {advertiser address and data}.
  // Among advertise report with the same key, latest one will be placed at the
  // same index of existing report in advertise reports cache.
  void Push(const chreBleAdvertisingReport &report);

  // Return advertise reports in cache after refreshing the cache elements.
  chre::DynamicVector<chreBleAdvertisingReport> &GetAdvReports() {
    Refresh();
    return cache_reports_;
  }

  // Computes moving average with previous average (previous) and a current data
  // point (current). Returns the computed average.
  int8_t ComputeMovingAverage(int8_t previous, int8_t current) const {
    return static_cast<int8_t>(current * kMovingAverageWeight +
                               previous * (1 - kMovingAverageWeight));
  }

  // Sets current cache timeout value.
  void SetCacheTimeout(uint64_t cache_expire_millisec) {
    cache_expire_nanosec_ =
        cache_expire_millisec * chre::kOneMillisecondInNanoseconds;
  }

  // Removes cached elements older than the cache timeout.
  void Refresh();

  // Removes cached elements older than the cache timeout if cache count is
  // larger than threshold.
  void RefreshIfNeeded();

 private:
  // Weight for a current data point in moving average.
  static constexpr float kMovingAverageWeight = 0.3f;

  // Default value for advertise report cache to expire.
  // Uses large enough value that it won't end soon.
  static constexpr uint64_t kMaxExpireTimeNanoSec =
      std::numeric_limits<uint64_t>::max();

  // Default value for threshold cache count to trigger refresh.
  // At the worst case, roughly 2KB ( = 255 byte * 8) could be allocated for the
  // cache elements exired.
  static constexpr size_t kRefreshCacheCountThreshold = 8;

  chre::DynamicVector<chreBleAdvertisingReport> cache_reports_;
  // Current cache timeout value.
  uint64_t cache_expire_nanosec_ = kMaxExpireTimeNanoSec;
#ifdef NEARBY_PROFILE
  ashProfileData profile_data_;
#endif
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_ADV_REPORT_CACHE_H_
