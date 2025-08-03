/*
 * Copyright 2021 The Android Open Source Project
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
#define LOG_TAG "BluetoothCounterMetrics"

#include "metrics/counter_metrics.h"

#include "common/bind.h"
#include "os/log.h"
#include "os/metrics.h"

namespace bluetooth {
namespace metrics {

const int COUNTER_METRICS_PERDIOD_MINUTES = 360; // Drain counters every 6 hours

const ModuleFactory CounterMetrics::Factory = ModuleFactory([]() { return new CounterMetrics(); });

void CounterMetrics::ListDependencies(ModuleList* /* list */) const {}

void CounterMetrics::Start() {
  alarm_ = std::make_unique<os::RepeatingAlarm>(GetHandler());
  alarm_->Schedule(
      common::Bind(&CounterMetrics::DrainBufferedCounters,
           bluetooth::common::Unretained(this)),
      std::chrono::minutes(COUNTER_METRICS_PERDIOD_MINUTES));
  LOG_INFO("Counter metrics initialized");
  initialized_ = true;
}

void CounterMetrics::Stop() {
  DrainBufferedCounters();
  initialized_ = false;
  alarm_->Cancel();
  alarm_.reset();
  LOG_INFO("Counter metrics canceled");
}

bool CounterMetrics::CacheCount(int32_t key, int64_t count) {
  if (!IsInitialized()) {
    LOG_WARN("Counter metrics isn't initialized");
    return false;
  }
  if (count <= 0) {
    LOG_WARN("count is not larger than 0. count: %s, key: %d", std::to_string(count).c_str(), key);
    return false;
  }
  int64_t total = 0;
  std::lock_guard<std::mutex> lock(mutex_);
  if (counters_.find(key) != counters_.end()) {
    total = counters_[key];
  }
  if (LLONG_MAX - total < count) {
      LOG_WARN("Counter metric overflows. count %s current total: %s key: %d",
               std::to_string(count).c_str(), std::to_string(total).c_str(), key);
      counters_[key] = LLONG_MAX;
      return false;
  }
  counters_[key] = total + count;
  return true;
}

bool CounterMetrics::Count(int32_t key, int64_t count) {
  if (!IsInitialized()) {
    LOG_WARN("Counter metrics isn't initialized");
    return false;
  }
  if (count <= 0) {
    LOG_WARN("count is not larger than 0. count: %s, key: %d", std::to_string(count).c_str(), key);
    return false;
  }
  os::LogMetricBluetoothCodePathCounterMetrics(key, count);
  return true;
}

void CounterMetrics::DrainBufferedCounters() {
  if (!IsInitialized()) {
    LOG_WARN("Counter metrics isn't initialized");
    return ;
  }
  std::lock_guard<std::mutex> lock(mutex_);
  LOG_INFO("Draining buffered counters");
  for (auto const& pair : counters_) {
    Count(pair.first, pair.second);
  }
  counters_.clear();
}

}  // namespace metrics
}  // namespace bluetooth
