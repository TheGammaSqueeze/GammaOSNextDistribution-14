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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <cstdint>
#include <vector>

#include "types/raw_address.h"

using testing::_;
using testing::AnyNumber;
using testing::AtLeast;
using testing::AtMost;
using testing::DoAll;
using testing::Invoke;
using testing::Mock;
using testing::MockFunction;
using testing::NotNull;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::Test;
using testing::WithArg;

int log_count = 0;
int32_t last_group_size;
int32_t last_group_metric_id;
int64_t last_connection_duration_nanos;
int64_t last_broadcast_duration_nanos;
std::vector<int64_t> last_device_connecting_offset_nanos;
std::vector<int64_t> last_device_connected_offset_nanos;
std::vector<int64_t> last_device_connection_duration_nanos;
std::vector<int32_t> last_device_connection_status;
std::vector<int32_t> last_device_disconnection_status;
std::vector<RawAddress> last_device_address;
std::vector<int64_t> last_streaming_offset_nanos;
std::vector<int64_t> last_streaming_duration_nanos;
std::vector<int32_t> last_streaming_context_type;

namespace bluetooth {
namespace common {

void LogLeAudioConnectionSessionReported(
    int32_t group_size, int32_t group_metric_id,
    int64_t connection_duration_nanos,
    std::vector<int64_t>& device_connecting_offset_nanos,
    std::vector<int64_t>& device_connected_offset_nanos,
    std::vector<int64_t>& device_connection_duration_nanos,
    std::vector<int32_t>& device_connection_status,
    std::vector<int32_t>& device_disconnection_status,
    std::vector<RawAddress>& device_address,
    std::vector<int64_t>& streaming_offset_nanos,
    std::vector<int64_t>& streaming_duration_nanos,
    std::vector<int32_t>& streaming_context_type) {
  log_count++;
  last_group_size = group_size;
  last_group_metric_id = group_metric_id;
  last_connection_duration_nanos = connection_duration_nanos;
  last_device_connecting_offset_nanos = device_connecting_offset_nanos;
  last_device_connected_offset_nanos = device_connected_offset_nanos;
  last_device_connection_duration_nanos = device_connection_duration_nanos;
  last_device_connection_status = device_connection_status;
  last_device_disconnection_status = device_disconnection_status;
  last_device_address = device_address;
  last_streaming_offset_nanos = streaming_offset_nanos;
  last_streaming_duration_nanos = streaming_duration_nanos;
  last_streaming_context_type = streaming_context_type;
}

void LogLeAudioBroadcastSessionReported(int64_t duration_nanos) {
  last_broadcast_duration_nanos = duration_nanos;
}

}  // namespace common
}  // namespace bluetooth

namespace le_audio {

const int32_t group_id1 = 1;
const RawAddress device1 = RawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});

const int32_t group_id2 = 2;
const RawAddress device2 = RawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x67});
const RawAddress device3 = RawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x68});

class MockMetricsCollector : public MetricsCollector {
 public:
  MockMetricsCollector() {}
};

class MetricsCollectorTest : public Test {
 protected:
  std::unique_ptr<MetricsCollector> collector;

  void SetUp() override {
    collector = std::make_unique<MockMetricsCollector>();

    log_count = 0;
    last_group_size = 0;
    last_group_metric_id = 0;
    last_broadcast_duration_nanos = 0;
    last_connection_duration_nanos = 0;
    last_device_connecting_offset_nanos = {};
    last_device_connected_offset_nanos = {};
    last_device_connection_duration_nanos = {};
    last_device_connection_status = {};
    last_device_disconnection_status = {};
    last_device_address = {};
    last_streaming_offset_nanos = {};
    last_streaming_duration_nanos = {};
    last_streaming_context_type = {};
  }

  void TearDown() override { collector = nullptr; }
};

TEST_F(MetricsCollectorTest, Initialize) { ASSERT_EQ(log_count, 0); }

TEST_F(MetricsCollectorTest, ConnectionFailed) {
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::FAILED);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.back(), ConnectionStatus::FAILED);
}

TEST_F(MetricsCollectorTest, ConnectingConnectedDisconnected) {
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.size(), 1UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 1UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.back(), ConnectionStatus::SUCCESS);
  ASSERT_EQ(last_device_disconnection_status.back(), ConnectionStatus::SUCCESS);
}

TEST_F(MetricsCollectorTest, SingleDeviceTwoConnections) {
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.size(), 1UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 1UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.back(), ConnectionStatus::SUCCESS);
  ASSERT_EQ(last_device_disconnection_status.back(), ConnectionStatus::SUCCESS);

  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 2);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.size(), 1UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 1UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.back(), ConnectionStatus::SUCCESS);
  ASSERT_EQ(last_device_disconnection_status.back(), ConnectionStatus::SUCCESS);
}

TEST_F(MetricsCollectorTest, StereoGroupBasicTest) {
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id2);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_device_connection_status.size(), 2UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 2UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 2UL);
}

TEST_F(MetricsCollectorTest, StereoGroupMultiReconnections) {
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id2);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 3UL);
  ASSERT_EQ(last_device_connection_status.size(), 3UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 3UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 3UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 3UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 3UL);
}

TEST_F(MetricsCollectorTest, MixGroups) {
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device3, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id2, device2, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id2);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_device_connection_status.size(), 2UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 2UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 2UL);

  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 2);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_status.size(), 1UL);
  ASSERT_EQ(last_device_disconnection_status.size(), 1UL);
  ASSERT_EQ(last_device_connecting_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connected_offset_nanos.size(), 1UL);
  ASSERT_EQ(last_device_connection_duration_nanos.size(), 1UL);
}

TEST_F(MetricsCollectorTest, GroupSizeUpdated) {
  collector->OnGroupSizeUpdate(group_id2, 1);
  collector->OnGroupSizeUpdate(group_id1, 2);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_group_size, 2);
}

TEST_F(MetricsCollectorTest, StreamingSessions) {
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTING,
      ConnectionStatus::UNKNOWN);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::CONNECTED,
      ConnectionStatus::SUCCESS);
  collector->OnStreamStarted(group_id1,
                             le_audio::types::LeAudioContextType::MEDIA);
  collector->OnStreamEnded(group_id1);
  collector->OnStreamStarted(
      group_id1, le_audio::types::LeAudioContextType::CONVERSATIONAL);
  collector->OnStreamEnded(group_id1);
  collector->OnConnectionStateChanged(
      group_id1, device1, bluetooth::le_audio::ConnectionState::DISCONNECTED,
      ConnectionStatus::SUCCESS);

  ASSERT_EQ(log_count, 1);
  ASSERT_EQ(last_group_metric_id, group_id1);
  ASSERT_EQ(last_streaming_offset_nanos.size(), 2UL);
  ASSERT_EQ(last_streaming_duration_nanos.size(), 2UL);
  ASSERT_EQ(last_streaming_context_type.size(), 2UL);

  ASSERT_GT(last_streaming_offset_nanos[0], 0L);
  ASSERT_GT(last_streaming_offset_nanos[1], 0L);
  ASSERT_GT(last_streaming_duration_nanos[0], 0L);
  ASSERT_GT(last_streaming_duration_nanos[1], 0L);
  ASSERT_EQ(last_streaming_context_type[0],
            static_cast<int32_t>(LeAudioMetricsContextType::MEDIA));
  ASSERT_EQ(last_streaming_context_type[1],
            static_cast<int32_t>(LeAudioMetricsContextType::COMMUNICATION));
}

TEST_F(MetricsCollectorTest, BroadastSessions) {
  last_broadcast_duration_nanos = 0;
  collector->OnBroadcastStateChanged(true);
  collector->OnBroadcastStateChanged(false);
  ASSERT_GT(last_broadcast_duration_nanos, 0);
  last_broadcast_duration_nanos = 0;
  collector->OnBroadcastStateChanged(true);
  collector->OnBroadcastStateChanged(false);
  ASSERT_GT(last_broadcast_duration_nanos, 0);
}

}  // namespace le_audio