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

/*
 * Generated mock file from original source file
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.2
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_metrics_logging.h"

#include <string>

// Original included files, if any
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/hci/enums.pb.h>

#include "test/common/mock_functions.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_metrics_logging {

// Function state capture and return values, if needed
struct log_classic_pairing_event log_classic_pairing_event;
struct log_link_layer_connection_event log_link_layer_connection_event;
struct log_smp_pairing_event log_smp_pairing_event;
struct log_sdp_attribute log_sdp_attribute;
struct log_manufacturer_info log_manufacturer_info;
struct log_counter_metrics log_counter_metrics;
struct log_hfp_audio_packet_loss_stats log_hfp_audio_packet_loss_stats;
struct log_mmc_transcode_rtt_stats log_mmc_transcode_rtt_stats;

}  // namespace stack_metrics_logging
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void log_classic_pairing_event(const RawAddress& address, uint16_t handle,
                               uint32_t hci_cmd, uint16_t hci_event,
                               uint16_t cmd_status, uint16_t reason_code,
                               int64_t event_value) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_classic_pairing_event(
      address, handle, hci_cmd, hci_event, cmd_status, reason_code,
      event_value);
}
void log_link_layer_connection_event(
    const RawAddress* address, uint32_t connection_handle,
    android::bluetooth::DirectionEnum direction, uint16_t link_type,
    uint32_t hci_cmd, uint16_t hci_event, uint16_t hci_ble_event,
    uint16_t cmd_status, uint16_t reason_code) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_link_layer_connection_event(
      address, connection_handle, direction, link_type, hci_cmd, hci_event,
      hci_ble_event, cmd_status, reason_code);
}
void log_smp_pairing_event(const RawAddress& address, uint16_t smp_cmd,
                           android::bluetooth::DirectionEnum direction,
                           uint16_t smp_fail_reason) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_smp_pairing_event(
      address, smp_cmd, direction, smp_fail_reason);
}
void log_sdp_attribute(const RawAddress& address, uint16_t protocol_uuid,
                       uint16_t attribute_id, size_t attribute_size,
                       const char* attribute_value) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_sdp_attribute(
      address, protocol_uuid, attribute_id, attribute_size, attribute_value);
}
void log_manufacturer_info(const RawAddress& address,
                           android::bluetooth::DeviceInfoSrcEnum source_type,
                           const std::string& source_name,
                           const std::string& manufacturer,
                           const std::string& model,
                           const std::string& hardware_version,
                           const std::string& software_version) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_manufacturer_info(
      address, source_type, source_name, manufacturer, model, hardware_version,
      software_version);
}
void log_manufacturer_info(const RawAddress& address,
                           android::bluetooth::AddressTypeEnum address_type,
                           android::bluetooth::DeviceInfoSrcEnum source_type,
                           const std::string& source_name,
                           const std::string& manufacturer,
                           const std::string& model,
                           const std::string& hardware_version,
                           const std::string& software_version) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_manufacturer_info(
      address, address_type, source_type, source_name, manufacturer, model,
      hardware_version, software_version);
}

void log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum key,
                         int64_t value) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_counter_metrics(key, value);
}

void log_hfp_audio_packet_loss_stats(const RawAddress& address,
                                     int num_decoded_frames,
                                     double packet_loss_ratio,
                                     uint16_t codec_type) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_hfp_audio_packet_loss_stats(
      address, num_decoded_frames, packet_loss_ratio, codec_type);
}

void log_mmc_transcode_rtt_stats(int maximum_rtt, double mean_rtt,
                                 int num_requests, int codec_type) {
  inc_func_call_count(__func__);
  test::mock::stack_metrics_logging::log_mmc_transcode_rtt_stats(
      maximum_rtt, mean_rtt, num_requests, codec_type);
}
// END mockcify generation
