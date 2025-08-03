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

#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/hci/enums.pb.h>

#include "main/shim/metric_id_api.h"
#include "types/raw_address.h"

void log_a2dp_audio_underrun_event(const RawAddress& address,
                                   uint64_t encoding_interval_millis,
                                   int num_missing_pcm_bytes);

void log_a2dp_audio_overrun_event(const RawAddress& address,
                                  uint64_t encoding_interval_millis,
                                  int num_dropped_buffers,
                                  int num_dropped_encoded_frames,
                                  int num_dropped_encoded_bytes);

void log_a2dp_playback_event(const RawAddress& address, int playback_state,
                             int audio_coding_mode);

void log_a2dp_session_metrics_event(
    const RawAddress& address, int64_t audio_duration_ms,
    int media_timer_min_ms, int media_timer_max_ms, int media_timer_avg_ms,
    int total_scheduling_count, int buffer_overruns_max_count,
    int buffer_overruns_total, float buffer_underruns_average,
    int buffer_underruns_count, int64_t codec_index, bool is_a2dp_offload);

void log_read_rssi_result(const RawAddress& address, uint16_t handle,
                          uint32_t cmd_status, int8_t rssi);

void log_read_failed_contact_counter_result(const RawAddress& address,
                                            uint16_t handle,
                                            uint32_t cmd_status,
                                            int32_t failed_contact_counter);

void log_read_tx_power_level_result(const RawAddress& address, uint16_t handle,
                                    uint32_t cmd_status,
                                    int32_t transmit_power_level);

void log_counter_metrics_btif(android::bluetooth::CodePathCounterKeyEnum key,
                              int64_t value);

void log_socket_connection_state(
    const RawAddress& address, int port, int type,
    android::bluetooth::SocketConnectionstateEnum connection_state,
    int64_t tx_bytes, int64_t rx_bytes, int uid, int server_port,
    android::bluetooth::SocketRoleEnum socket_role);

bool init_metric_id_allocator(
    const std::unordered_map<RawAddress, int>& paired_device_map,
    bluetooth::shim::CallbackLegacy save_id_callback,
    bluetooth::shim::CallbackLegacy forget_device_callback);

bool close_metric_id_allocator();

int allocate_metric_id_from_metric_id_allocator(const RawAddress&);

int save_metric_id_from_metric_id_allocator(const RawAddress&);

void forget_device_from_metric_id_allocator(const RawAddress&);

bool is_valid_id_from_metric_id_allocator(const int id);