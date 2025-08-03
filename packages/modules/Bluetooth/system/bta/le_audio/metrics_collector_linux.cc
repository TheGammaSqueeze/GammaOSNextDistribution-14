/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "metrics_collector.h"

namespace le_audio {

/* Metrics Colloctor */
MetricsCollector* MetricsCollector::instance = nullptr;

MetricsCollector* MetricsCollector::Get() {
  if (MetricsCollector::instance == nullptr) {
    MetricsCollector::instance = new MetricsCollector();
  }
  return MetricsCollector::instance;
}

void MetricsCollector::OnGroupSizeUpdate(int32_t group_id, int32_t group_size) {
}

void MetricsCollector::OnConnectionStateChanged(
    int32_t group_id, const RawAddress& address,
    bluetooth::le_audio::ConnectionState state, ConnectionStatus status) {}

void MetricsCollector::OnStreamStarted(
    int32_t group_id, le_audio::types::LeAudioContextType context_type) {}

void MetricsCollector::OnStreamEnded(int32_t group_id) {}

void MetricsCollector::OnBroadcastStateChanged(bool started) {}

void MetricsCollector::Flush() {}

}  // namespace le_audio
