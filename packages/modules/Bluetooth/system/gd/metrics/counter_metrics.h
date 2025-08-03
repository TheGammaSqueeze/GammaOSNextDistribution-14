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
#pragma once

#include <unordered_map>

#include "module.h"
#include "os/repeating_alarm.h"

namespace bluetooth {
namespace metrics {

class CounterMetrics : public bluetooth::Module {
 public:
  bool CacheCount(int32_t key, int64_t value);
  virtual bool Count(int32_t key, int64_t count);
  void Stop() override;
  static const ModuleFactory Factory;

 protected:
  void ListDependencies(ModuleList* list) const override;
  void Start() override;
  std::string ToString() const override {
    return std::string("BluetoothCounterMetrics");
  }
  void DrainBufferedCounters();
  virtual bool IsInitialized() {
    return initialized_;
  }

 private:
  std::unordered_map<int32_t, int64_t> counters_;
  mutable std::mutex mutex_;
  std::unique_ptr<os::RepeatingAlarm> alarm_;
  bool initialized_ {false};
};

}  // namespace metrics
}  // namespace bluetooth