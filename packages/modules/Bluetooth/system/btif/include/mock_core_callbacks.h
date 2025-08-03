/*
 * Copyright 2023 The Android Open Source Project
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

#include <gmock/gmock.h>

#include "btif/include/core_callbacks.h"
#include "include/hardware/bluetooth.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace core {
namespace testing {

// These callbacks are not profile specific (e.g. connection complete, bond
// complete, etc) and are what go to the Java layer.
EventCallbacks mock_event_callbacks = {
    .invoke_adapter_state_changed_cb = [](bt_state_t /* state */) {},
    .invoke_adapter_properties_cb = [](bt_status_t /* status */,
                                       int /* num_properties */,
                                       bt_property_t* /* properties */) {},
    .invoke_remote_device_properties_cb =
        [](bt_status_t /* status */, RawAddress /* bd_addr */,
           int /* num_properties */, bt_property_t* /* properties */) {},
    .invoke_device_found_cb = [](int /* num_properties */,
                                 bt_property_t* /* properties */) {},
    .invoke_discovery_state_changed_cb =
        [](bt_discovery_state_t /* state */) {},
    .invoke_pin_request_cb = [](RawAddress /* bd_addr */,
                                bt_bdname_t /* bd_name */, uint32_t /* cod */,
                                bool /* min_16_digit */) {},
    .invoke_ssp_request_cb = [](RawAddress /* bd_addr */,
                                bt_bdname_t /* bd_name */, uint32_t /* cod */,
                                bt_ssp_variant_t /* pairing_variant */,
                                uint32_t /* pass_key */) {},
    .invoke_oob_data_request_cb = [](tBT_TRANSPORT /* t */, bool /* valid */,
                                     Octet16 /* c */, Octet16 /* r */,
                                     RawAddress /* raw_address */,
                                     uint8_t /* address_type */) {},
    .invoke_bond_state_changed_cb =
        [](bt_status_t /* status */, RawAddress /* bd_addr */,
           bt_bond_state_t /* state */, int /* fail_reason */) {},
    .invoke_address_consolidate_cb = [](RawAddress /* main_bd_addr */,
                                        RawAddress /* secondary_bd_addr */) {},
    .invoke_le_address_associate_cb = [](RawAddress /* main_bd_addr */,
                                         RawAddress /* secondary_bd_addr */) {},
    .invoke_acl_state_changed_cb =
        [](bt_status_t /* status */, RawAddress /* bd_addr */,
           bt_acl_state_t /* state */, int /* transport_link_type */,
           bt_hci_error_code_t /* hci_reason */,
           bt_conn_direction_t /* direction */, uint16_t /* acl_handle */) {},
    .invoke_thread_evt_cb = [](bt_cb_thread_evt /* event */) {},
    .invoke_le_test_mode_cb = [](bt_status_t /* status */,
                                 uint16_t /* count */) {},
    .invoke_energy_info_cb = [](bt_activity_energy_info /* energy_info */,
                                bt_uid_traffic_t* /* uid_data */) {},
    .invoke_link_quality_report_cb =
        [](uint64_t /* timestamp */, int /* report_id */, int /* rssi */,
           int /* snr */, int /* retransmission_count */,
           int /* packets_not_receive_count */,
           int /* negative_acknowledgement_count */) {},
};

// This interface lets us query for configuration properties of the stack that
// could change at runtime
struct MockConfigInterface : public ConfigInterface {
  MOCK_METHOD((bool), isA2DPOffloadEnabled, (), ());
  MOCK_METHOD((bool), isAndroidTVDevice, (), ());
  MOCK_METHOD((bool), isRestrictedMode, (), ());
};
MockConfigInterface mock_config_interface;

// This interface lets us communicate with encoders used in profiles
struct MockCodecInterface : public CodecInterface {
  MOCK_METHOD((void), initialize, (), ());
  MOCK_METHOD((void), cleanup, (), ());
  MOCK_METHOD((uint32_t), encodePacket, (int16_t * input, uint8_t* output), ());
  MOCK_METHOD((bool), decodePacket,
              (const uint8_t* i_buf, int16_t* o_buf, size_t out_len), ());
};
MockCodecInterface mock_codec_msbcCodec;
MockCodecInterface mock_codec_lc3Codec;

HACK_ProfileInterface mock_HACK_profile_interface = {
    .btif_hh_connect = [](const tAclLinkSpec* /* link_spec */) -> bt_status_t {
      return BT_STATUS_SUCCESS;
    },
    .btif_hh_virtual_unplug = [](const tAclLinkSpec* /* link_spec */)
        -> bt_status_t { return BT_STATUS_SUCCESS; },
    .bta_hh_read_ssr_param =
        [](const tAclLinkSpec& /* link_spec */, uint16_t* /* p_max_ssr_lat */,
           uint16_t* /* p_min_ssr_tout */) -> tBTA_HH_STATUS {
      return BTA_HH_OK;
    },

    .btif_av_set_dynamic_audio_buffer_size =
        [](uint8_t /* dynamic_audio_buffer_size */) {},
    .GetHearingAidDeviceCount = []() -> int { return 0; },
    .IsLeAudioClientRunning = []() -> bool { return false; },
    .AVRC_GetProfileVersion = []() -> uint16_t { return 0; },
};

// This class defines the overall interface expected by bluetooth::core.
struct MockCoreInterface : public CoreInterface {
  MockCoreInterface()
      : CoreInterface(&mock_event_callbacks, &mock_config_interface,
                      &mock_codec_msbcCodec, &mock_codec_lc3Codec,
                      &mock_HACK_profile_interface) {}

  MOCK_METHOD((void), onBluetoothEnabled, (), ());
  MOCK_METHOD((bt_status_t), toggleProfile,
              (tBTA_SERVICE_ID service_id, bool enable), ());
  MOCK_METHOD((void), removeDeviceFromProfiles, (const RawAddress& bd_addr),
              ());
  MOCK_METHOD((void), onLinkDown, (const RawAddress& bd_addr), ());
};

}  // namespace testing
}  // namespace core
}  // namespace bluetooth
