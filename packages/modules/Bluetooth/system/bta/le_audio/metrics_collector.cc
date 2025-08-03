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

#include <base/logging.h>

#include <memory>
#include <vector>

#include "common/metrics.h"

namespace le_audio {

using bluetooth::le_audio::ConnectionState;
using le_audio::types::LeAudioContextType;

const static metrics::ClockTimePoint kInvalidTimePoint{};

MetricsCollector* MetricsCollector::instance = nullptr;

inline int64_t get_timedelta_nanos(const metrics::ClockTimePoint& t1,
                                   const metrics::ClockTimePoint& t2) {
  if (t1 == kInvalidTimePoint || t2 == kInvalidTimePoint) {
    return -1;
  }
  return std::abs(
      std::chrono::duration_cast<std::chrono::nanoseconds>(t1 - t2).count());
}

const static std::unordered_map<LeAudioContextType, LeAudioMetricsContextType>
    kContextTypeTable = {
        {LeAudioContextType::UNINITIALIZED, LeAudioMetricsContextType::INVALID},
        {LeAudioContextType::UNSPECIFIED,
         LeAudioMetricsContextType::UNSPECIFIED},
        {LeAudioContextType::CONVERSATIONAL,
         LeAudioMetricsContextType::COMMUNICATION},
        {LeAudioContextType::MEDIA, LeAudioMetricsContextType::MEDIA},
        {LeAudioContextType::GAME, LeAudioMetricsContextType::GAME},
        {LeAudioContextType::INSTRUCTIONAL,
         LeAudioMetricsContextType::INSTRUCTIONAL},
        {LeAudioContextType::VOICEASSISTANTS,
         LeAudioMetricsContextType::MAN_MACHINE},
        {LeAudioContextType::LIVE, LeAudioMetricsContextType::LIVE},
        {LeAudioContextType::SOUNDEFFECTS,
         LeAudioMetricsContextType::ATTENTION_SEEKING},
        {LeAudioContextType::NOTIFICATIONS,
         LeAudioMetricsContextType::ATTENTION_SEEKING},
        {LeAudioContextType::RINGTONE, LeAudioMetricsContextType::RINGTONE},
        {LeAudioContextType::ALERTS,
         LeAudioMetricsContextType::IMMEDIATE_ALERT},
        {LeAudioContextType::EMERGENCYALARM,
         LeAudioMetricsContextType::EMERGENCY_ALERT},
        {LeAudioContextType::RFU, LeAudioMetricsContextType::RFU},
};

inline int32_t to_atom_context_type(const LeAudioContextType stack_type) {
  auto it = kContextTypeTable.find(stack_type);
  if (it != kContextTypeTable.end()) {
    return static_cast<int32_t>(it->second);
  }
  return static_cast<int32_t>(LeAudioMetricsContextType::INVALID);
}

class DeviceMetrics {
 public:
  RawAddress address_;
  metrics::ClockTimePoint connecting_timepoint_ = kInvalidTimePoint;
  metrics::ClockTimePoint connected_timepoint_ = kInvalidTimePoint;
  metrics::ClockTimePoint disconnected_timepoint_ = kInvalidTimePoint;
  int32_t connection_status_ = 0;
  int32_t disconnection_status_ = 0;

  DeviceMetrics(const RawAddress& address) : address_(address) {}

  void AddStateChangedEvent(ConnectionState state, ConnectionStatus status) {
    switch (state) {
      case ConnectionState::CONNECTING:
        connecting_timepoint_ = std::chrono::high_resolution_clock::now();
        break;
      case ConnectionState::CONNECTED:
        connected_timepoint_ = std::chrono::high_resolution_clock::now();
        connection_status_ = static_cast<int32_t>(status);
        break;
      case ConnectionState::DISCONNECTED:
        disconnected_timepoint_ = std::chrono::high_resolution_clock::now();
        disconnection_status_ = static_cast<int32_t>(status);
        break;
      case ConnectionState::DISCONNECTING:
        // Ignore
        break;
    }
  }
};

class GroupMetricsImpl : public GroupMetrics {
 private:
  static constexpr int32_t kInvalidGroupId = -1;
  int32_t group_id_;
  int32_t group_size_;
  std::vector<std::unique_ptr<DeviceMetrics>> device_metrics_;
  std::unordered_map<RawAddress, DeviceMetrics*> opened_devices_;
  metrics::ClockTimePoint beginning_timepoint_;
  std::vector<int64_t> streaming_offset_nanos_;
  std::vector<int64_t> streaming_duration_nanos_;
  std::vector<int32_t> streaming_context_type_;

 public:
  GroupMetricsImpl() : group_id_(kInvalidGroupId), group_size_(0) {
    beginning_timepoint_ = std::chrono::high_resolution_clock::now();
  }
  GroupMetricsImpl(int32_t group_id, int32_t group_size)
      : group_id_(group_id), group_size_(group_size) {
    beginning_timepoint_ = std::chrono::high_resolution_clock::now();
  }

  void AddStateChangedEvent(const RawAddress& address,
                            le_audio::ConnectionState state,
                            ConnectionStatus status) override {
    auto it = opened_devices_.find(address);
    if (it == opened_devices_.end()) {
      device_metrics_.push_back(std::make_unique<DeviceMetrics>(address));
      it = opened_devices_.insert(std::begin(opened_devices_),
                                  {address, device_metrics_.back().get()});
    }
    it->second->AddStateChangedEvent(state, status);
    if (state == le_audio::ConnectionState::DISCONNECTED ||
        (state == le_audio::ConnectionState::CONNECTED &&
         status != ConnectionStatus::SUCCESS)) {
      opened_devices_.erase(it);
    }
  }

  void AddStreamStartedEvent(
      le_audio::types::LeAudioContextType context_type) override {
    int32_t atom_context_type = to_atom_context_type(context_type);
    // Make sure events aligned
    if (streaming_offset_nanos_.size() - streaming_duration_nanos_.size() !=
        0) {
      // Allow type switching
      if (!streaming_context_type_.empty() &&
          streaming_context_type_.back() != atom_context_type) {
        AddStreamEndedEvent();
      } else {
        return;
      }
    }
    streaming_offset_nanos_.push_back(get_timedelta_nanos(
        std::chrono::high_resolution_clock::now(), beginning_timepoint_));
    streaming_context_type_.push_back(atom_context_type);
  }

  void AddStreamEndedEvent() override {
    // Make sure events aligned
    if (streaming_offset_nanos_.size() - streaming_duration_nanos_.size() !=
        1) {
      return;
    }
    streaming_duration_nanos_.push_back(
        get_timedelta_nanos(std::chrono::high_resolution_clock::now(),
                            beginning_timepoint_) -
        streaming_offset_nanos_.back());
  }

  void SetGroupSize(int32_t group_size) override { group_size_ = group_size; }

  bool IsClosed() override { return opened_devices_.empty(); }

  void WriteStats() override {
    int64_t connection_duration_nanos = get_timedelta_nanos(
        beginning_timepoint_, std::chrono::high_resolution_clock::now());

    int len = device_metrics_.size();
    std::vector<int64_t> device_connecting_offset_nanos(len);
    std::vector<int64_t> device_connected_offset_nanos(len);
    std::vector<int64_t> device_connection_duration_nanos(len);
    std::vector<int32_t> device_connection_statuses(len);
    std::vector<int32_t> device_disconnection_statuses(len);
    std::vector<RawAddress> device_address(len);

    while (streaming_duration_nanos_.size() < streaming_offset_nanos_.size()) {
      AddStreamEndedEvent();
    }

    for (int i = 0; i < len; i++) {
      auto device_metric = device_metrics_[i].get();
      device_connecting_offset_nanos[i] = get_timedelta_nanos(
          device_metric->connecting_timepoint_, beginning_timepoint_);
      device_connected_offset_nanos[i] = get_timedelta_nanos(
          device_metric->connected_timepoint_, beginning_timepoint_);
      device_connection_duration_nanos[i] =
          get_timedelta_nanos(device_metric->disconnected_timepoint_,
                              device_metric->connected_timepoint_);
      device_connection_statuses[i] = device_metric->connection_status_;
      device_disconnection_statuses[i] = device_metric->disconnection_status_;
      device_address[i] = device_metric->address_;
    }

    bluetooth::common::LogLeAudioConnectionSessionReported(
        group_size_, group_id_, connection_duration_nanos,
        device_connecting_offset_nanos, device_connected_offset_nanos,
        device_connection_duration_nanos, device_connection_statuses,
        device_disconnection_statuses, device_address, streaming_offset_nanos_,
        streaming_duration_nanos_, streaming_context_type_);
  }

  void Flush() {
    for (auto& p : opened_devices_) {
      p.second->AddStateChangedEvent(
          bluetooth::le_audio::ConnectionState::DISCONNECTED,
          ConnectionStatus::SUCCESS);
    }
    WriteStats();
  }
};

/* Metrics Colloctor */

MetricsCollector* MetricsCollector::Get() {
  if (MetricsCollector::instance == nullptr) {
    MetricsCollector::instance = new MetricsCollector();
  }
  return MetricsCollector::instance;
}

void MetricsCollector::OnGroupSizeUpdate(int32_t group_id, int32_t group_size) {
  group_size_table_[group_id] = group_size;
  auto it = opened_groups_.find(group_id);
  if (it != opened_groups_.end()) {
    it->second->SetGroupSize(group_size);
  }
}

void MetricsCollector::OnConnectionStateChanged(
    int32_t group_id, const RawAddress& address,
    bluetooth::le_audio::ConnectionState state, ConnectionStatus status) {
  if (address.IsEmpty() || group_id <= 0) {
    return;
  }
  auto it = opened_groups_.find(group_id);
  if (it == opened_groups_.end()) {
    it = opened_groups_.insert(
        std::begin(opened_groups_),
        {group_id, std::make_unique<GroupMetricsImpl>(
                       group_id, group_size_table_[group_id])});
  }
  it->second->AddStateChangedEvent(address, state, status);

  if (it->second->IsClosed()) {
    it->second->WriteStats();
    opened_groups_.erase(it);
  }
}

void MetricsCollector::OnStreamStarted(
    int32_t group_id, le_audio::types::LeAudioContextType context_type) {
  if (group_id <= 0) return;
  auto it = opened_groups_.find(group_id);
  if (it != opened_groups_.end()) {
    it->second->AddStreamStartedEvent(context_type);
  }
}

void MetricsCollector::OnStreamEnded(int32_t group_id) {
  if (group_id <= 0) return;
  auto it = opened_groups_.find(group_id);
  if (it != opened_groups_.end()) {
    it->second->AddStreamEndedEvent();
  }
}

void MetricsCollector::OnBroadcastStateChanged(bool started) {
  if (started) {
    broadcast_beginning_timepoint_ = std::chrono::high_resolution_clock::now();
  } else {
    auto broadcast_ending_timepoint_ =
        std::chrono::high_resolution_clock::now();
    bluetooth::common::LogLeAudioBroadcastSessionReported(get_timedelta_nanos(
        broadcast_beginning_timepoint_, broadcast_ending_timepoint_));
    broadcast_beginning_timepoint_ = kInvalidTimePoint;
  }
}

void MetricsCollector::Flush() {
  LOG(INFO) << __func__;
  for (auto& p : opened_groups_) {
    p.second->Flush();
  }
  opened_groups_.clear();
}

}  // namespace le_audio
