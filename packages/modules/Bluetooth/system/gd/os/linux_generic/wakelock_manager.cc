/******************************************************************************
 *
 *  Copyright 2021 Google, Inc.
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
 ******************************************************************************/

#define LOG_TAG "BtGdWakelock"

#include "os/wakelock_manager.h"

#include <cerrno>
#include <mutex>

#include "os/internal/wakelock_native.h"
#include "os/log.h"

namespace bluetooth {
namespace os {

using internal::WakelockNative;
using StatusCode = WakelockNative::StatusCode;

uint64_t now_ms() {
  struct timespec ts = {};
  if (clock_gettime(CLOCK_BOOTTIME, &ts) == -1) {
    LOG_ERROR("unable to get current time: %s", strerror(errno));
    return 0;
  }
  return (ts.tv_sec * 1000LL) + (ts.tv_nsec / 1000000LL);
}

const std::string WakelockManager::kBtWakelockId = "bluetooth_gd_timer";

// Wakelock statistics for the "bluetooth_timer"
struct WakelockManager::Stats {
  bool is_acquired = false;
  size_t acquired_count = 0;
  size_t released_count = 0;
  size_t acquired_errors = 0;
  size_t released_errors = 0;
  uint64_t min_acquired_interval_ms = 0;
  uint64_t max_acquired_interval_ms = 0;
  uint64_t last_acquired_interval_ms = 0;
  uint64_t total_acquired_interval_ms = 0;
  uint64_t last_acquired_timestamp_ms = 0;
  uint64_t last_released_timestamp_ms = 0;
  uint64_t last_reset_timestamp_ms = now_ms();
  StatusCode last_acquired_error = StatusCode::SUCCESS;
  StatusCode last_released_error = StatusCode::SUCCESS;

  void Reset() {
    is_acquired = false;
    acquired_count = 0;
    released_count = 0;
    acquired_errors = 0;
    released_errors = 0;
    min_acquired_interval_ms = 0;
    max_acquired_interval_ms = 0;
    last_acquired_interval_ms = 0;
    total_acquired_interval_ms = 0;
    last_acquired_timestamp_ms = 0;
    last_released_timestamp_ms = 0;
    last_reset_timestamp_ms = now_ms();
    last_acquired_error = StatusCode::SUCCESS;
    last_released_error = StatusCode::SUCCESS;
  }

  // Update the Bluetooth acquire wakelock statistics.
  //
  // This function should be called every time when the wakelock is acquired.
  // |acquired_status| is the status code that was return when the wakelock was
  // acquired.
  void UpdateAcquiredStats(StatusCode acquired_status) {
    const uint64_t just_now_ms = now_ms();
    if (acquired_status != StatusCode::SUCCESS) {
      acquired_errors++;
      last_acquired_error = acquired_status;
    }

    if (is_acquired) {
      return;
    }

    is_acquired = true;
    acquired_count++;
    last_acquired_timestamp_ms = just_now_ms;
  }

  // Update the Bluetooth release wakelock statistics.
  //
  // This function should be called every time when the wakelock is released.
  // |released_status| is the status code that was return when the wakelock was
  // released.
  void UpdateReleasedStats(StatusCode released_status) {
    const uint64_t just_now_ms = now_ms();
    if (released_status != StatusCode::SUCCESS) {
      released_errors++;
      last_released_error = released_status;
    }

    if (!is_acquired) {
      return;
    }

    is_acquired = false;
    released_count++;
    last_released_timestamp_ms = just_now_ms;

    // Compute the acquired interval and update the statistics
    uint64_t delta_ms = just_now_ms - last_acquired_timestamp_ms;
    if (delta_ms < min_acquired_interval_ms || released_count == 1) {
      min_acquired_interval_ms = delta_ms;
    }
    if (delta_ms > max_acquired_interval_ms) {
      max_acquired_interval_ms = delta_ms;
    }
    last_acquired_interval_ms = delta_ms;
    total_acquired_interval_ms += delta_ms;
  }

  flatbuffers::Offset<WakelockManagerData> GetDumpsysData(
      flatbuffers::FlatBufferBuilder* fb_builder, bool is_native) const {
    const uint64_t just_now_ms = now_ms();
    // Compute the last acquired interval if the wakelock is still acquired
    uint64_t delta_ms = 0;
    uint64_t last_interval_ms = last_acquired_interval_ms;
    uint64_t min_interval_ms = min_acquired_interval_ms;
    uint64_t max_interval_ms = max_acquired_interval_ms;
    uint64_t avg_interval_ms = 0;

    if (is_acquired) {
      delta_ms = just_now_ms - last_acquired_timestamp_ms;
      if (delta_ms > max_interval_ms) {
        max_interval_ms = delta_ms;
      }
      if (delta_ms < min_interval_ms) {
        min_interval_ms = delta_ms;
      }
      last_interval_ms = delta_ms;
    }
    uint64_t total_interval_ms = total_acquired_interval_ms + delta_ms;

    if (acquired_count > 0) {
      avg_interval_ms = total_interval_ms / acquired_count;
    }

    WakelockManagerDataBuilder builder(*fb_builder);
    builder.add_title(fb_builder->CreateString("Bluetooth Wakelock Statistics"));
    builder.add_is_acquired(is_acquired);
    builder.add_is_native(is_native);
    builder.add_acquired_count(acquired_count);
    builder.add_released_count(released_count);
    builder.add_acquired_error_count(acquired_errors);
    builder.add_released_error_count(released_errors);
    builder.add_last_acquire_error_code(last_acquired_error);
    builder.add_last_release_error_code(last_released_error);
    builder.add_last_acquired_timestamp_millis(last_interval_ms);
    builder.add_last_released_timestamp_millis(last_released_timestamp_ms);
    builder.add_last_interval_millis(last_acquired_interval_ms);
    builder.add_max_interval_millis(max_interval_ms);
    builder.add_min_interval_millis(min_interval_ms);
    builder.add_avg_interval_millis(avg_interval_ms);
    builder.add_total_interval_millis(total_interval_ms);
    builder.add_total_time_since_reset_millis(just_now_ms - last_reset_timestamp_ms);
    return builder.Finish();
  }
};

void WakelockManager::SetOsCallouts(OsCallouts* callouts, Handler* handler) {
  std::lock_guard<std::recursive_mutex> lock_guard(mutex_);
  if (initialized_) {
    LOG_WARN("Setting OS callouts after initialization can lead to wakelock leak!");
  }
  os_callouts_ = callouts;
  os_callouts_handler_ = handler;
  is_native_ = (os_callouts_ == nullptr);
  if (is_native_) {
    ASSERT_LOG(os_callouts_handler_ != nullptr, "handler must not be null when callout is not null");
  }
  LOG_INFO("set to %s", is_native_ ? "native" : "non-native");
}

bool WakelockManager::Acquire() {
  std::lock_guard<std::recursive_mutex> lock_guard(mutex_);
  if (!initialized_) {
    if (is_native_) {
      WakelockNative::Get().Initialize();
    }
    initialized_ = true;
  }

  StatusCode status;
  if (is_native_) {
    status = WakelockNative::Get().Acquire(kBtWakelockId);
  } else {
    os_callouts_handler_->CallOn(os_callouts_, &OsCallouts::AcquireCallout, kBtWakelockId);
    status = StatusCode::SUCCESS;
  }

  pstats_->UpdateAcquiredStats(status);

  if (status != StatusCode::SUCCESS) {
    LOG_ERROR("unable to acquire wake lock, error code: %u", status);
  }

  return status == StatusCode ::SUCCESS;
}

bool WakelockManager::Release() {
  std::lock_guard<std::recursive_mutex> lock_guard(mutex_);
  if (!initialized_) {
    if (is_native_) {
      WakelockNative::Get().Initialize();
    }
    initialized_ = true;
  }

  StatusCode status;
  if (is_native_) {
    status = WakelockNative::Get().Release(kBtWakelockId);
  } else {
    os_callouts_handler_->CallOn(os_callouts_, &OsCallouts::ReleaseCallout, kBtWakelockId);
    status = StatusCode ::SUCCESS;
  }

  pstats_->UpdateReleasedStats(status);

  if (status != StatusCode::SUCCESS) {
    LOG_ERROR("unable to release wake lock, error code: %u", status);
  }

  return status == StatusCode ::SUCCESS;
}

void WakelockManager::CleanUp() {
  std::lock_guard<std::recursive_mutex> lock_guard(mutex_);
  if (!initialized_) {
    LOG_ERROR("Already uninitialized");
    return;
  }
  if (pstats_->is_acquired) {
    LOG_ERROR("Releasing wake lock as part of cleanup");
    Release();
  }
  if (is_native_) {
    WakelockNative::Get().CleanUp();
  }
  pstats_->Reset();
  initialized_ = false;
}

flatbuffers::Offset<WakelockManagerData> WakelockManager::GetDumpsysData(flatbuffers::FlatBufferBuilder* fb_builder) {
  std::lock_guard<std::recursive_mutex> lock_guard(mutex_);
  return pstats_->GetDumpsysData(fb_builder, is_native_);
}

WakelockManager::WakelockManager() : pstats_(std::make_unique<Stats>()) {}

WakelockManager::~WakelockManager() = default;

}  // namespace os
}  // namespace bluetooth