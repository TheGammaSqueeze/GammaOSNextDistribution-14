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

#pragma once

#include <hardware/bt_le_audio.h>

#include <chrono>
#include <cstdint>
#include <memory>
#include <unordered_map>

#include "le_audio_types.h"
#include "types/raw_address.h"

namespace le_audio {

namespace metrics {
using ClockTimePoint =
    std::chrono::time_point<std::chrono::high_resolution_clock>;
}

enum ConnectionStatus : int32_t {
  UNKNOWN = 0,
  SUCCESS = 1,
  FAILED = 2,
};

/* android.bluetooth.leaudio.ContextType */
enum class LeAudioMetricsContextType : int32_t {
  INVALID = 0,
  UNSPECIFIED = 1,
  COMMUNICATION = 2,
  MEDIA = 3,
  INSTRUCTIONAL = 4,
  ATTENTION_SEEKING = 5,
  IMMEDIATE_ALERT = 6,
  MAN_MACHINE = 7,
  EMERGENCY_ALERT = 8,
  RINGTONE = 9,
  TV = 10,
  LIVE = 11,
  GAME = 12,
  RFU = 13,
};

class GroupMetrics {
 public:
  GroupMetrics() {}

  virtual ~GroupMetrics() {}

  virtual void AddStateChangedEvent(const RawAddress& address,
                                    bluetooth::le_audio::ConnectionState state,
                                    ConnectionStatus status) = 0;

  virtual void AddStreamStartedEvent(
      le_audio::types::LeAudioContextType context_type) = 0;

  virtual void AddStreamEndedEvent() = 0;

  virtual void SetGroupSize(int32_t group_size) = 0;

  virtual bool IsClosed() = 0;

  virtual void WriteStats() = 0;

  virtual void Flush() = 0;
};

class MetricsCollector {
 public:
  static MetricsCollector* Get();

  /**
   * Update the size of given group which will be used in the
   * LogMetricBluetoothLeAudioConnectionStateChanged()
   *
   * @param group_id ID of target group
   * @param group_size Size of target group
   */
  void OnGroupSizeUpdate(int32_t group_id, int32_t group_size);

  /**
   * When there is a change in Bluetooth LE Audio connection state
   *
   * @param group_id Group ID of the associated device.
   * @param address Address of the associated device.
   * @param state New LE Audio connetion state.
   * @param status status or reason of the state transition. Ignored at
   * CONNECTING states.
   */
  void OnConnectionStateChanged(int32_t group_id, const RawAddress& address,
                                bluetooth::le_audio::ConnectionState state,
                                ConnectionStatus status);

  /**
   * When there is a change in LE Audio stream started
   *
   * @param group_id Group ID of the associated stream.
   */
  void OnStreamStarted(int32_t group_id,
                       le_audio::types::LeAudioContextType context_type);

  /**
   * When there is a change in LE Audio stream started
   *
   * @param group_id Group ID of the associated stream.
   */
  void OnStreamEnded(int32_t group_id);

  /**
   * When there is a change in Bluetooth LE Audio broadcast state
   *
   * @param started if broadcast streaming is started.
   */
  void OnBroadcastStateChanged(bool started);

  /**
   * Flush all log to statsd
   *
   * @param group_id Group ID of the associated stream.
   */
  void Flush();

 protected:
  MetricsCollector() {}

 private:
  static MetricsCollector* instance;

  std::unordered_map<int32_t, std::unique_ptr<GroupMetrics>> opened_groups_;
  std::unordered_map<int32_t, int32_t> group_size_table_;

  metrics::ClockTimePoint broadcast_beginning_timepoint_;
};

}  // namespace le_audio
