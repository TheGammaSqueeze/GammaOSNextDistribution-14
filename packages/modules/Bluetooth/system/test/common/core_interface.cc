/*
 * Copyright 2022 The Android Open Source Project
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

#include "core_interface.h"

#include "btif/include/btif_common.h"
#include "btif/include/core_callbacks.h"
#include "btif/include/stack_manager_t.h"

namespace {

static bluetooth::core::EventCallbacks eventCallbacks = {
    .invoke_adapter_state_changed_cb = invoke_adapter_state_changed_cb,
    .invoke_adapter_properties_cb = invoke_adapter_properties_cb,
    .invoke_remote_device_properties_cb = invoke_remote_device_properties_cb,
    .invoke_device_found_cb = invoke_device_found_cb,
    .invoke_discovery_state_changed_cb = invoke_discovery_state_changed_cb,
    .invoke_pin_request_cb = invoke_pin_request_cb,
    .invoke_ssp_request_cb = invoke_ssp_request_cb,
    .invoke_oob_data_request_cb = invoke_oob_data_request_cb,
    .invoke_bond_state_changed_cb = invoke_bond_state_changed_cb,
    .invoke_address_consolidate_cb = invoke_address_consolidate_cb,
    .invoke_le_address_associate_cb = invoke_le_address_associate_cb,
    .invoke_acl_state_changed_cb = invoke_acl_state_changed_cb,
    .invoke_thread_evt_cb = invoke_thread_evt_cb,
    .invoke_le_test_mode_cb = invoke_le_test_mode_cb,
    .invoke_energy_info_cb = invoke_energy_info_cb,
    .invoke_link_quality_report_cb = invoke_link_quality_report_cb,
    .invoke_key_missing_cb = invoke_key_missing_cb};

// This interface lets us query for configuration properties of the stack that
// could change at runtime
struct MockConfigInterface : public bluetooth::core::ConfigInterface {
  virtual bool isA2DPOffloadEnabled() { return false; }
  virtual bool isAndroidTVDevice() { return false; }
  virtual bool isRestrictedMode() { return false; }
};

static auto mockConfigInterface = MockConfigInterface{};

// This interface lets us communicate with encoders used in profiles
struct MockMsbcCodecInterface : public bluetooth::core::CodecInterface {
  virtual void initialize(){};
  virtual void cleanup() {}

  virtual uint32_t encodePacket(int16_t* /* input */, uint8_t* /* output */) {
    return 0;
  };
  virtual bool decodePacket(const uint8_t* /* i_buf */, int16_t* /* o_buf */,
                            size_t /* out_len */) {
    return false;
  };
};

struct MockLc3CodecInterface : public bluetooth::core::CodecInterface {
  virtual void initialize(){};
  virtual void cleanup() {}

  virtual uint32_t encodePacket(int16_t* /* input */, uint8_t* /* output */) {
    return 0;
  };
  virtual bool decodePacket(const uint8_t* /* i_buf */, int16_t* /* o_buf */,
                            size_t /* out_len */) {
    return false;
  };
};

static auto mockMsbcCodecInterface = MockMsbcCodecInterface{};
static auto mockLc3CodecInterface = MockLc3CodecInterface{};

struct bluetooth::core::HACK_ProfileInterface HACK_profileInterface = {
    // HID
    .btif_hh_connect = nullptr,
    .btif_hh_virtual_unplug = nullptr,
    .bta_hh_read_ssr_param = nullptr,

    // AVDTP
    .btif_av_set_dynamic_audio_buffer_size = nullptr,

    // ASHA
    .GetHearingAidDeviceCount = nullptr,

    // LE Audio
    .IsLeAudioClientRunning = nullptr,

    // AVRCP
    .AVRC_GetProfileVersion = nullptr,
};

}  // namespace

void InitializeCoreInterface() {
  static auto mockCoreInterface = MockCoreInterface{};
  stack_manager_get_interface()->init_stack(&mockCoreInterface);
}

void CleanCoreInterface() {
  stack_manager_get_interface()->clean_up_stack([] {});
}

MockCoreInterface::MockCoreInterface()
    : bluetooth::core::CoreInterface{
          &eventCallbacks, &mockConfigInterface, &mockMsbcCodecInterface,
          &mockLc3CodecInterface, &HACK_profileInterface} {};

void MockCoreInterface::onBluetoothEnabled(){};

bt_status_t MockCoreInterface::toggleProfile(tBTA_SERVICE_ID /* service_id */,
                                             bool /* enable */) {
  return BT_STATUS_SUCCESS;
};

void MockCoreInterface::removeDeviceFromProfiles(
    const RawAddress& /* bd_addr */){};

void MockCoreInterface::onLinkDown(const RawAddress& /* bd_addr */){};
