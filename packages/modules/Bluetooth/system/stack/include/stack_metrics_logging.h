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

#include <cstdint>

#include "types/raw_address.h"

void log_classic_pairing_event(const RawAddress& address, uint16_t handle,
                               uint32_t hci_cmd, uint16_t hci_event,
                               uint16_t cmd_status, uint16_t reason_code,
                               int64_t event_value);

void log_link_layer_connection_event(
    const RawAddress* address, uint32_t connection_handle,
    android::bluetooth::DirectionEnum direction, uint16_t link_type,
    uint32_t hci_cmd, uint16_t hci_event, uint16_t hci_ble_event,
    uint16_t cmd_status, uint16_t reason_code);

void log_smp_pairing_event(const RawAddress& address, uint16_t smp_cmd,
                           android::bluetooth::DirectionEnum direction,
                           uint16_t smp_fail_reason);

void log_sdp_attribute(const RawAddress& address, uint16_t protocol_uuid,
                       uint16_t attribute_id, size_t attribute_size,
                       const char* attribute_value);

void log_manufacturer_info(const RawAddress& address,
                           android::bluetooth::AddressTypeEnum address_type,
                           android::bluetooth::DeviceInfoSrcEnum source_type,
                           const std::string& source_name,
                           const std::string& manufacturer,
                           const std::string& model,
                           const std::string& hardware_version,
                           const std::string& software_version);

void log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum key,
                         int64_t value);

void log_hfp_audio_packet_loss_stats(const RawAddress& address,
                                     int num_decoded_frames,
                                     double packet_loss_ratio,
                                     uint16_t codec_type);

void log_mmc_transcode_rtt_stats(int maximum_rtt, double mean_rtt,
                                 int num_requests, int codec_type);
