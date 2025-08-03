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

#pragma once

#include "bta/include/bta_api.h"
#include "bta/include/bta_hh_api.h"
#include "include/hardware/bluetooth.h"
#include "stack/include/btm_ble_api_types.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace core {

// These callbacks are not profile specific (e.g. connection complete, bond
// complete, etc) and are what go to the Java layer.
struct EventCallbacks {
  void (*invoke_adapter_state_changed_cb)(bt_state_t state);
  void (*invoke_adapter_properties_cb)(bt_status_t status, int num_properties,
                                       bt_property_t* properties);
  void (*invoke_remote_device_properties_cb)(bt_status_t status,
                                             RawAddress bd_addr,
                                             int num_properties,
                                             bt_property_t* properties);
  void (*invoke_device_found_cb)(int num_properties, bt_property_t* properties);
  void (*invoke_discovery_state_changed_cb)(bt_discovery_state_t state);
  void (*invoke_pin_request_cb)(RawAddress bd_addr, bt_bdname_t bd_name,
                                uint32_t cod, bool min_16_digit);
  void (*invoke_ssp_request_cb)(RawAddress bd_addr, bt_bdname_t bd_name,
                                uint32_t cod, bt_ssp_variant_t pairing_variant,
                                uint32_t pass_key);
  void (*invoke_oob_data_request_cb)(tBT_TRANSPORT t, bool valid, Octet16 c,
                                     Octet16 r, RawAddress raw_address,
                                     uint8_t address_type);
  void (*invoke_bond_state_changed_cb)(bt_status_t status, RawAddress bd_addr,
                                       bt_bond_state_t state, int fail_reason);
  void (*invoke_address_consolidate_cb)(RawAddress main_bd_addr,
                                        RawAddress secondary_bd_addr);
  void (*invoke_le_address_associate_cb)(RawAddress main_bd_addr,
                                         RawAddress secondary_bd_addr);
  void (*invoke_acl_state_changed_cb)(bt_status_t status, RawAddress bd_addr,
                                      bt_acl_state_t state,
                                      int transport_link_type,
                                      bt_hci_error_code_t hci_reason,
                                      bt_conn_direction_t direction,
                                      uint16_t acl_handle);
  void (*invoke_thread_evt_cb)(bt_cb_thread_evt event);
  void (*invoke_le_test_mode_cb)(bt_status_t status, uint16_t count);
  void (*invoke_energy_info_cb)(bt_activity_energy_info energy_info,
                                bt_uid_traffic_t* uid_data);
  void (*invoke_link_quality_report_cb)(uint64_t timestamp, int report_id,
                                        int rssi, int snr,
                                        int retransmission_count,
                                        int packets_not_receive_count,
                                        int negative_acknowledgement_count);
  void (*invoke_key_missing_cb)(RawAddress bd_addr);

  EventCallbacks& operator=(const EventCallbacks&) = delete;
};

// This interface lets us query for configuration properties of the stack that
// could change at runtime
struct ConfigInterface {
  virtual bool isA2DPOffloadEnabled() = 0;
  virtual bool isAndroidTVDevice() = 0;
  virtual bool isRestrictedMode() = 0;

  explicit ConfigInterface() = default;
  ConfigInterface(const ConfigInterface&) = delete;
  ConfigInterface& operator=(const ConfigInterface&) = delete;
  virtual ~ConfigInterface() = default;
};

// This interface lets us communicate with encoders used in profiles
struct CodecInterface {
  virtual void initialize() = 0;
  virtual void cleanup() = 0;

  virtual uint32_t encodePacket(int16_t* input, uint8_t* output) = 0;
  virtual bool decodePacket(const uint8_t* i_buf, int16_t* o_buf,
                            size_t out_len) = 0;

  explicit CodecInterface() = default;
  CodecInterface(const CodecInterface&) = delete;
  CodecInterface& operator=(const CodecInterface&) = delete;
  virtual ~CodecInterface() = default;
};

// Please DO NOT add any more methods to this interface.
// It is a legacy violation of the abstraction
// between core and profiles: the core stack should not have any
// profile-specific functionality or call directly into profile code, as this
// makes refactoring + testing much harder. Instead, create a *generic* callback
// that profiles can register themselves to.
struct HACK_ProfileInterface {
  // HID hacks
  bt_status_t (*btif_hh_connect)(const tAclLinkSpec* link_spec);
  bt_status_t (*btif_hh_virtual_unplug)(const tAclLinkSpec* link_spec);
  tBTA_HH_STATUS (*bta_hh_read_ssr_param)(const tAclLinkSpec& link_spec,
                                          uint16_t* p_max_ssr_lat,
                                          uint16_t* p_min_ssr_tout);

  // AVDTP hacks
  void (*btif_av_set_dynamic_audio_buffer_size)(
      uint8_t dynamic_audio_buffer_size);

  // ASHA hacks
  int (*GetHearingAidDeviceCount)();

  // LE Audio hacks
  bool (*IsLeAudioClientRunning)();

  // AVRCP hacks
  uint16_t (*AVRC_GetProfileVersion)();

  HACK_ProfileInterface& operator=(const HACK_ProfileInterface&) = delete;
};

// This class defines the overall interface expected by bluetooth::core.
struct CoreInterface {
  // generic interface
  EventCallbacks* events;
  ConfigInterface* config;

  // codecs
  CodecInterface* msbcCodec;
  CodecInterface* lc3Codec;

  // DO NOT add any more methods here
  HACK_ProfileInterface* profileSpecific_HACK;

  virtual void onBluetoothEnabled() = 0;
  virtual bt_status_t toggleProfile(tBTA_SERVICE_ID service_id,
                                    bool enable) = 0;
  virtual void removeDeviceFromProfiles(const RawAddress& bd_addr) = 0;
  virtual void onLinkDown(const RawAddress& bd_addr) = 0;

  CoreInterface(EventCallbacks* eventCallbacks,
                ConfigInterface* configInterface, CodecInterface* msbcCodec,
                CodecInterface* lc3Codec,
                HACK_ProfileInterface* profileSpecific_HACK)
      : events{eventCallbacks},
        config{configInterface},
        msbcCodec{msbcCodec},
        lc3Codec{lc3Codec},
        profileSpecific_HACK{profileSpecific_HACK} {};

  CoreInterface(const CoreInterface&) = delete;
  CoreInterface& operator=(const CoreInterface&) = delete;
  virtual ~CoreInterface() = default;
};

}  // namespace core
}  // namespace bluetooth
