/******************************************************************************
 *
 *  Copyright (C) 2016 The Linux Foundation
 *  Copyright 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/*******************************************************************************
 *
 *  Filename:      bluetooth.c
 *
 *  Description:   Bluetooth HAL implementation
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif"

#include <android_bluetooth_flags.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bluetooth_headset_interface.h>
#include <hardware/bt_av.h>
#include <hardware/bt_csis.h>
#include <hardware/bt_gatt.h>
#include <hardware/bt_has.h>
#include <hardware/bt_hd.h>
#include <hardware/bt_hearing_aid.h>
#include <hardware/bt_hf_client.h>
#include <hardware/bt_hh.h>
#include <hardware/bt_le_audio.h>
#include <hardware/bt_pan.h>
#include <hardware/bt_rc.h>
#include <hardware/bt_sdp.h>
#include <hardware/bt_sock.h>
#include <hardware/bt_vc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "audio_hal_interface/a2dp_encoding.h"
#include "bta/hh/bta_hh_int.h"  // for HID HACK profile methods
#include "bta/include/bta_api.h"
#include "bta/include/bta_ar_api.h"
#include "bta/include/bta_csis_api.h"
#include "bta/include/bta_has_api.h"
#include "bta/include/bta_hearing_aid_api.h"
#include "bta/include/bta_hf_client_api.h"
#include "bta/include/bta_le_audio_api.h"
#include "bta/include/bta_le_audio_broadcaster_api.h"
#include "bta/include/bta_vc_api.h"
#include "btif/avrcp/avrcp_service.h"
#include "btif/include/btif_sock.h"
#include "btif/include/core_callbacks.h"
#include "btif/include/stack_manager_t.h"
#include "btif_a2dp.h"
#include "btif_api.h"
#include "btif_av.h"
#include "btif_bqr.h"
#include "btif_config.h"
#include "btif_debug_conn.h"
#include "btif_dm.h"
#include "btif_hd.h"
#include "btif_hf.h"
#include "btif_hh.h"
#include "btif_keystore.h"
#include "btif_metrics_logging.h"
#include "btif_pan.h"
#include "btif_profile_storage.h"
#include "btif_rc.h"
#include "btif_sock.h"
#include "btif_storage.h"
#include "common/address_obfuscator.h"
#include "common/init_flags.h"
#include "common/metrics.h"
#include "common/os_utils.h"
#include "device/include/device_iot_config.h"
#include "device/include/esco_parameters.h"
#include "device/include/interop.h"
#include "device/include/interop_config.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "main/shim/dumpsys.h"
#include "os/log.h"
#include "os/parameter_provider.h"
#include "osi/include/alarm.h"
#include "osi/include/allocator.h"
#include "osi/include/stack_power_telemetry.h"
#include "osi/include/wakelock.h"
#include "stack/btm/btm_sco_hfp_hal.h"
#include "stack/gatt/connection_manager.h"
#include "stack/include/a2dp_api.h"
#include "stack/include/avdt_api.h"
#include "stack/include/btm_api.h"
#include "stack/include/hfp_lc3_decoder.h"
#include "stack/include/hfp_lc3_encoder.h"
#include "stack/include/hfp_msbc_decoder.h"
#include "stack/include/hfp_msbc_encoder.h"
#include "stack/include/hidh_api.h"
#include "stack/include/main_thread.h"
#include "stack/include/pan_api.h"
#include "storage/config_keys.h"
#include "types/raw_address.h"

using bluetooth::csis::CsisClientInterface;
using bluetooth::has::HasClientInterface;
using bluetooth::hearing_aid::HearingAidInterface;
using bluetooth::le_audio::LeAudioBroadcasterInterface;
using bluetooth::le_audio::LeAudioClientInterface;
using bluetooth::vc::VolumeControlInterface;
using namespace bluetooth;

/*******************************************************************************
 *  Static variables
 ******************************************************************************/

static bt_callbacks_t* bt_hal_cbacks = NULL;
bool restricted_mode = false;
bool common_criteria_mode = false;
const int CONFIG_COMPARE_ALL_PASS = 0b11;
int common_criteria_config_compare_result = CONFIG_COMPARE_ALL_PASS;
bool is_local_device_atv = false;

/*******************************************************************************
 *  Externs
 ******************************************************************************/

/* list all extended interfaces here */

/* handsfree profile - client */
extern const bthf_client_interface_t* btif_hf_client_get_interface();
/* advanced audio profile */
extern const btav_source_interface_t* btif_av_get_src_interface();
extern const btav_sink_interface_t* btif_av_get_sink_interface();
/*rfc l2cap*/
extern const btsock_interface_t* btif_sock_get_interface();
/* hid host profile */
extern const bthh_interface_t* btif_hh_get_interface();
/* hid device profile */
extern const bthd_interface_t* btif_hd_get_interface();
/*pan*/
extern const btpan_interface_t* btif_pan_get_interface();
/* gatt */
extern const btgatt_interface_t* btif_gatt_get_interface();
/* avrc target */
extern const btrc_interface_t* btif_rc_get_interface();
/* avrc controller */
extern const btrc_ctrl_interface_t* btif_rc_ctrl_get_interface();
/*SDP search client*/
extern const btsdp_interface_t* btif_sdp_get_interface();
/*Hearing Aid client*/
extern HearingAidInterface* btif_hearing_aid_get_interface();
/* Hearing Access client */
extern HasClientInterface* btif_has_client_get_interface();
/* LeAudio testi client */
extern LeAudioClientInterface* btif_le_audio_get_interface();
/* LeAudio Broadcaster */
extern LeAudioBroadcasterInterface* btif_le_audio_broadcaster_get_interface();
/* Coordinated Set Service Client */
extern CsisClientInterface* btif_csis_client_get_interface();
/* Volume Control client */
extern VolumeControlInterface* btif_volume_control_get_interface();

bt_status_t btif_av_sink_execute_service(bool b_enable);
bt_status_t btif_hh_execute_service(bool b_enable);
bt_status_t btif_hf_client_execute_service(bool b_enable);
bt_status_t btif_sdp_execute_service(bool b_enable);
bt_status_t btif_hh_connect(const tAclLinkSpec* link_spec);
bt_status_t btif_hd_execute_service(bool b_enable);

extern void gatt_tcb_dump(int fd);

/*******************************************************************************
 *  Callbacks from bluetooth::core (see go/invisalign-bt)
 ******************************************************************************/

struct ConfigInterfaceImpl : bluetooth::core::ConfigInterface {
  ConfigInterfaceImpl() : bluetooth::core::ConfigInterface(){};

  bool isRestrictedMode() override { return is_restricted_mode(); }

  bool isA2DPOffloadEnabled() override {
    char value_sup[PROPERTY_VALUE_MAX] = {'\0'};
    char value_dis[PROPERTY_VALUE_MAX] = {'\0'};

    osi_property_get("ro.bluetooth.a2dp_offload.supported", value_sup, "false");
    osi_property_get("persist.bluetooth.a2dp_offload.disabled", value_dis,
                     "false");
    auto a2dp_offload_enabled =
        (strcmp(value_sup, "true") == 0) && (strcmp(value_dis, "false") == 0);
    log::verbose("a2dp_offload.enable = {}", a2dp_offload_enabled);

    return a2dp_offload_enabled;
  }

  bool isAndroidTVDevice() override { return is_atv_device(); }
};

// TODO(aryarahul): remove unnecessary indirection through hfp_msbc_*.cc
struct MSBCCodec : bluetooth::core::CodecInterface {
  MSBCCodec() : bluetooth::core::CodecInterface(){};

  void initialize() override {
    hfp_msbc_decoder_init();
    hfp_msbc_encoder_init();
  }

  void cleanup() override {
    hfp_msbc_decoder_cleanup();
    hfp_msbc_encoder_cleanup();
  }

  uint32_t encodePacket(int16_t* input, uint8_t* output) {
    return hfp_msbc_encode_frames(input, output);
  }

  bool decodePacket(const uint8_t* i_buf, int16_t* o_buf, size_t out_len) {
    return hfp_msbc_decoder_decode_packet(i_buf, o_buf, out_len);
  }
};

struct LC3Codec : bluetooth::core::CodecInterface {
  LC3Codec() : bluetooth::core::CodecInterface(){};

  void initialize() override {
    hfp_lc3_decoder_init();
    hfp_lc3_encoder_init();
  }

  void cleanup() override {
    hfp_lc3_encoder_cleanup();
    hfp_lc3_decoder_cleanup();
  }

  uint32_t encodePacket(int16_t* input, uint8_t* output) {
    return hfp_lc3_encode_frames(input, output);
  }

  bool decodePacket(const uint8_t* i_buf, int16_t* o_buf, size_t out_len) {
    return hfp_lc3_decoder_decode_packet(i_buf, o_buf, out_len);
  }
};

struct CoreInterfaceImpl : bluetooth::core::CoreInterface {
  using bluetooth::core::CoreInterface::CoreInterface;

  void onBluetoothEnabled() override {
    /* init pan */
    btif_pan_init();
  }

  bt_status_t toggleProfile(tBTA_SERVICE_ID service_id, bool enable) override {
    /* Check the service_ID and invoke the profile's BT state changed API */
    switch (service_id) {
      case BTA_HFP_SERVICE_ID:
      case BTA_HSP_SERVICE_ID: {
        bluetooth::headset::ExecuteService(enable);
      } break;
      case BTA_A2DP_SOURCE_SERVICE_ID: {
        btif_av_source_execute_service(enable);
      } break;
      case BTA_A2DP_SINK_SERVICE_ID: {
        btif_av_sink_execute_service(enable);
      } break;
      case BTA_HID_SERVICE_ID: {
        btif_hh_execute_service(enable);
      } break;
      case BTA_HFP_HS_SERVICE_ID: {
        btif_hf_client_execute_service(enable);
      } break;
      case BTA_HIDD_SERVICE_ID: {
        btif_hd_execute_service(enable);
      } break;
      case BTA_PBAP_SERVICE_ID:
      case BTA_PCE_SERVICE_ID:
      case BTA_MAP_SERVICE_ID:
      case BTA_MN_SERVICE_ID: {
        /**
         * Do nothing; these services were started elsewhere. However, we need
         * to flow through this codepath in order to properly report back the
         * local UUIDs back to adapter properties in Java. To achieve this, we
         * need to catch these service IDs in order for {@link
         * btif_in_execute_service_request} to return {@code BT_STATUS_SUCCESS},
         * so that in {@link btif_dm_enable_service} the check passes and the
         * UUIDs are allowed to be passed up into the Java layer.
         */
      } break;
      default:
        log::error("Unknown service {} being {}", service_id,
                   (enable) ? "enabled" : "disabled");
        return BT_STATUS_FAIL;
    }
    return BT_STATUS_SUCCESS;
  }

  void removeDeviceFromProfiles(const RawAddress& bd_addr) override {
/*special handling for HID devices */
#if (defined(BTA_HH_INCLUDED) && (BTA_HH_INCLUDED == TRUE))
    tAclLinkSpec link_spec;
    link_spec.addrt.bda = bd_addr;
    link_spec.addrt.type = BLE_ADDR_PUBLIC;
    link_spec.transport = BT_TRANSPORT_AUTO;

    btif_hh_remove_device(link_spec);
#endif
#if (defined(BTA_HD_INCLUDED) && (BTA_HD_INCLUDED == TRUE))
    btif_hd_remove_device(bd_addr);
#endif
    btif_hearing_aid_get_interface()->RemoveDevice(bd_addr);

    if (bluetooth::csis::CsisClient::IsCsisClientRunning())
      btif_csis_client_get_interface()->RemoveDevice(bd_addr);

    if (LeAudioClient::IsLeAudioClientRunning())
      btif_le_audio_get_interface()->RemoveDevice(bd_addr);

    if (VolumeControl::IsVolumeControlRunning()) {
      btif_volume_control_get_interface()->RemoveDevice(bd_addr);
    }
  }

  void onLinkDown(const RawAddress& bd_addr) override {
    btif_av_acl_disconnected(bd_addr);
  }
};

static bluetooth::core::CoreInterface* CreateInterfaceToProfiles() {
  static bluetooth::core::EventCallbacks eventCallbacks{
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
      .invoke_key_missing_cb = invoke_key_missing_cb,
  };
  static bluetooth::core::HACK_ProfileInterface profileInterface{
      // HID
      .btif_hh_connect = btif_hh_connect,
      .btif_hh_virtual_unplug = btif_hh_virtual_unplug,
      .bta_hh_read_ssr_param = bta_hh_read_ssr_param,

      // AVDTP
      .btif_av_set_dynamic_audio_buffer_size =
          btif_av_set_dynamic_audio_buffer_size,

      // ASHA
      .GetHearingAidDeviceCount = HearingAid::GetDeviceCount,

      // LE Audio
      .IsLeAudioClientRunning = LeAudioClient::IsLeAudioClientRunning,

      // AVRCP
      .AVRC_GetProfileVersion = AVRC_GetProfileVersion,
  };

  static auto configInterface = ConfigInterfaceImpl();
  static auto msbcCodecInterface = MSBCCodec();
  static auto lc3CodecInterface = LC3Codec();

  static auto interfaceForCore =
      CoreInterfaceImpl(&eventCallbacks, &configInterface, &msbcCodecInterface,
                        &lc3CodecInterface, &profileInterface);
  return &interfaceForCore;
}

/*******************************************************************************
 *  Functions
 ******************************************************************************/

static bool interface_ready(void) { return bt_hal_cbacks != NULL; }
void set_hal_cbacks(bt_callbacks_t* callbacks) { bt_hal_cbacks = callbacks; }

static bool is_profile(const char* p1, const char* p2) {
  CHECK(p1);
  CHECK(p2);
  return strlen(p1) == strlen(p2) && strncmp(p1, p2, strlen(p2)) == 0;
}

/*****************************************************************************
 *
 *   BLUETOOTH HAL INTERFACE FUNCTIONS
 *
 ****************************************************************************/

static int init(bt_callbacks_t* callbacks, bool start_restricted,
                bool is_common_criteria_mode, int config_compare_result,
                const char** init_flags, bool is_atv,
                const char* user_data_directory) {
  (void)user_data_directory;
  log::info(
      "start restricted = {} ; common criteria mode = {}, config compare "
      "result = {}",
      start_restricted, is_common_criteria_mode, config_compare_result);

  bluetooth::common::InitFlags::Load(init_flags);

  if (interface_ready()) return BT_STATUS_DONE;

  set_hal_cbacks(callbacks);

  restricted_mode = start_restricted;

  bluetooth::os::ParameterProvider::SetBtKeystoreInterface(
      bluetooth::bluetooth_keystore::getBluetoothKeystoreInterface());
  bluetooth::os::ParameterProvider::SetCommonCriteriaMode(
      is_common_criteria_mode);
  if (is_bluetooth_uid() && is_common_criteria_mode) {
    bluetooth::os::ParameterProvider::SetCommonCriteriaConfigCompareResult(
        config_compare_result);
  } else {
    bluetooth::os::ParameterProvider::SetCommonCriteriaConfigCompareResult(
        CONFIG_COMPARE_ALL_PASS);
  }

  is_local_device_atv = is_atv;

  stack_manager_get_interface()->init_stack(CreateInterfaceToProfiles());
  return BT_STATUS_SUCCESS;
}

static void start_profiles() {
#if (BNEP_INCLUDED == TRUE)
  BNEP_Init();
#if (PAN_INCLUDED == TRUE)
  PAN_Init();
#endif /* PAN */
#endif /* BNEP Included */
  A2DP_Init();
  AVRC_Init();
#if (HID_HOST_INCLUDED == TRUE)
  HID_HostInit();
#endif
  bta_ar_init();
}

static void stop_profiles() {
  btif_sock_cleanup();
  btif_pan_cleanup();
}

static int enable() {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  stack_manager_get_interface()->start_up_stack_async(
      CreateInterfaceToProfiles(), &start_profiles, &stop_profiles);
  return BT_STATUS_SUCCESS;
}

static int disable(void) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  stack_manager_get_interface()->shut_down_stack_async(&stop_profiles);
  return BT_STATUS_SUCCESS;
}

static void cleanup(void) {
  stack_manager_get_interface()->clean_up_stack(&stop_profiles);
}

bool is_restricted_mode() { return restricted_mode; }

static bool get_wbs_supported() {
  return hfp_hal_interface::get_wbs_supported();
}

static bool get_swb_supported() {
  return hfp_hal_interface::get_swb_supported();
}

static bool is_coding_format_supported(esco_coding_format_t coding_format) {
  return hfp_hal_interface::is_coding_format_supported(coding_format);
}

bool is_common_criteria_mode() {
  return is_bluetooth_uid() && common_criteria_mode;
}
// if common criteria mode disable, will always return
// CONFIG_COMPARE_ALL_PASS(0b11) indicate don't check config checksum.
int get_common_criteria_config_compare_result() {
  return is_common_criteria_mode() ? common_criteria_config_compare_result
                                   : CONFIG_COMPARE_ALL_PASS;
}

bool is_atv_device() { return is_local_device_atv; }

static int get_adapter_properties(void) {
  if (!btif_is_enabled()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_get_adapter_properties));
  return BT_STATUS_SUCCESS;
}

static int get_adapter_property(bt_property_type_t type) {
  /* Allow get_adapter_property only for BDADDR and BDNAME if BT is disabled */
  if (!btif_is_enabled() && (type != BT_PROPERTY_BDADDR) &&
      (type != BT_PROPERTY_BDNAME))
    return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_get_adapter_property, type));
  return BT_STATUS_SUCCESS;
}

static int set_adapter_property(const bt_property_t* property) {
  if (!btif_is_enabled()) return BT_STATUS_NOT_READY;

  switch (property->type) {
    case BT_PROPERTY_BDNAME:
    case BT_PROPERTY_ADAPTER_SCAN_MODE:
    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT:
    case BT_PROPERTY_CLASS_OF_DEVICE:
    case BT_PROPERTY_LOCAL_IO_CAPS:
      break;
    default:
      return BT_STATUS_FAIL;
  }

  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](bt_property_t* property) {
                                     btif_set_adapter_property(property);
                                     osi_free(property);
                                   },
                                   property_deep_copy(property)));
  return BT_STATUS_SUCCESS;
}

int get_remote_device_properties(RawAddress* remote_addr) {
  if (!btif_is_enabled()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_get_remote_device_properties,
                                              *remote_addr));
  return BT_STATUS_SUCCESS;
}

int get_remote_device_property(RawAddress* remote_addr,
                               bt_property_type_t type) {
  if (!btif_is_enabled()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_get_remote_device_property,
                                              *remote_addr, type));
  return BT_STATUS_SUCCESS;
}

int set_remote_device_property(RawAddress* remote_addr,
                               const bt_property_t* property) {
  if (!btif_is_enabled()) return BT_STATUS_NOT_READY;

  do_in_main_thread(
      FROM_HERE, base::BindOnce(
                     [](RawAddress remote_addr, bt_property_t* property) {
                       btif_set_remote_device_property(&remote_addr, property);
                       osi_free(property);
                     },
                     *remote_addr, property_deep_copy(property)));
  return BT_STATUS_SUCCESS;
}

int get_remote_services(RawAddress* remote_addr, int transport) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_get_remote_services,
                                              *remote_addr, transport));
  return BT_STATUS_SUCCESS;
}

static int start_discovery(void) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_start_discovery));
  return BT_STATUS_SUCCESS;
}

static int cancel_discovery(void) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_cancel_discovery));
  return BT_STATUS_SUCCESS;
}

static int create_bond(const RawAddress* bd_addr, int transport) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (btif_dm_pairing_is_busy()) return BT_STATUS_BUSY;

  do_in_main_thread(FROM_HERE,
                    base::BindOnce(btif_dm_create_bond, *bd_addr, transport));
  return BT_STATUS_SUCCESS;
}

static int create_bond_le(const RawAddress* bd_addr, uint8_t addr_type) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (btif_dm_pairing_is_busy()) return BT_STATUS_BUSY;

  do_in_main_thread(
      FROM_HERE, base::BindOnce(btif_dm_create_bond_le, *bd_addr, addr_type));
  return BT_STATUS_SUCCESS;
}

static int create_bond_out_of_band(const RawAddress* bd_addr, int transport,
                                   const bt_oob_data_t* p192_data,
                                   const bt_oob_data_t* p256_data) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (btif_dm_pairing_is_busy()) return BT_STATUS_BUSY;

  do_in_main_thread(FROM_HERE,
                    base::BindOnce(btif_dm_create_bond_out_of_band, *bd_addr,
                                   transport, *p192_data, *p256_data));
  return BT_STATUS_SUCCESS;
}

static int generate_local_oob_data(tBT_TRANSPORT transport) {
  log::info("");
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  return do_in_main_thread(
      FROM_HERE, base::BindOnce(btif_dm_generate_local_oob_data, transport));
}

static int cancel_bond(const RawAddress* bd_addr) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_cancel_bond, *bd_addr));
  return BT_STATUS_SUCCESS;
}

static int remove_bond(const RawAddress* bd_addr) {
  if (is_restricted_mode() && !btif_storage_is_restricted_device(bd_addr)) {
    log::info("{} cannot be removed in restricted mode",
              ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_SUCCESS;
  }

  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_remove_bond, *bd_addr));
  return BT_STATUS_SUCCESS;
}

static bool pairing_is_busy() {
  if (btif_dm_pairing_is_busy()) return true;

  return false;
}

static int get_connection_state(const RawAddress* bd_addr) {
  if (!interface_ready()) return 0;

  if (bd_addr == nullptr) return 0;

  if (IS_FLAG_ENABLED(api_get_connection_state_sync_on_main)) {
    return btif_dm_get_connection_state_sync(*bd_addr);
  } else {
    return btif_dm_get_connection_state(*bd_addr);
  }
}

static int pin_reply(const RawAddress* bd_addr, uint8_t accept, uint8_t pin_len,
                     bt_pin_code_t* pin_code) {
  bt_pin_code_t tmp_pin_code;
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (pin_code == nullptr || pin_len > PIN_CODE_LEN) return BT_STATUS_FAIL;

  memcpy(&tmp_pin_code, pin_code, pin_len);

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_pin_reply, *bd_addr,
                                              accept, pin_len, tmp_pin_code));
  return BT_STATUS_SUCCESS;
}

static int ssp_reply(const RawAddress* bd_addr, bt_ssp_variant_t variant,
                     uint8_t accept, uint32_t /* passkey */) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (variant == BT_SSP_VARIANT_PASSKEY_ENTRY) return BT_STATUS_FAIL;

  do_in_main_thread(
      FROM_HERE, base::BindOnce(btif_dm_ssp_reply, *bd_addr, variant, accept));
  return BT_STATUS_SUCCESS;
}

static int read_energy_info() {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_read_energy_info));
  return BT_STATUS_SUCCESS;
}

static int clear_event_filter() {
  log::verbose("");
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_clear_event_filter));
  return BT_STATUS_SUCCESS;
}

static int clear_event_mask() {
  log::verbose("");
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_clear_event_mask));
  return BT_STATUS_SUCCESS;
}

static int clear_filter_accept_list() {
  log::verbose("");
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE,
                    base::BindOnce(btif_dm_clear_filter_accept_list));
  return BT_STATUS_SUCCESS;
}

static int disconnect_all_acls() {
  log::verbose("");
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_disconnect_all_acls));
  return BT_STATUS_SUCCESS;
}

static void le_rand_btif_cb(uint64_t random_number) {
  log::verbose("");
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(
          [](uint64_t random) { HAL_CBACK(bt_hal_cbacks, le_rand_cb, random); },
          random_number));
}

static int le_rand() {
  log::verbose("");
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(btif_dm_le_rand, base::BindOnce(&le_rand_btif_cb)));
  return BT_STATUS_SUCCESS;
}

static int set_event_filter_inquiry_result_all_devices() {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(btif_dm_set_event_filter_inquiry_result_all_devices));
  return BT_STATUS_SUCCESS;
}

static int set_default_event_mask_except(uint64_t mask, uint64_t le_mask) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(btif_dm_set_default_event_mask_except, mask, le_mask));
  return BT_STATUS_SUCCESS;
}

static int restore_filter_accept_list() {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  // TODO(b/260922031) - When restoring the filter accept list after a system
  // suspend, we need to re-arm the LE connections that had `is_direct=False`.
  // This should be the list of bonded devices and potentially any GATT
  // connections that have `is_direct=False`. Currently, we only restore LE hid
  // devices.
  auto le_hid_addrs = btif_storage_get_le_hid_devices();
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(btif_dm_restore_filter_accept_list,
                                   std::move(le_hid_addrs)));
  return BT_STATUS_SUCCESS;
}

static int allow_wake_by_hid() {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  auto le_hid_addrs = btif_storage_get_le_hid_devices();
  auto classic_hid_addrs = btif_storage_get_wake_capable_classic_hid_devices();
  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dm_allow_wake_by_hid,
                                              std::move(classic_hid_addrs),
                                              std::move(le_hid_addrs)));
  return BT_STATUS_SUCCESS;
}

static int set_event_filter_connection_setup_all_devices() {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(btif_dm_set_event_filter_connection_setup_all_devices));
  return BT_STATUS_SUCCESS;
}

static void dump(int fd, const char** arguments) {
  btif_debug_conn_dump(fd);
  btif_debug_bond_event_dump(fd);
  btif_debug_linkkey_type_dump(fd);
  btif_debug_rc_dump(fd);
  btif_debug_a2dp_dump(fd);
  btif_debug_av_dump(fd);
  bta_debug_av_dump(fd);
  stack_debug_avdtp_api_dump(fd);
  btif_sock_dump(fd);
  bluetooth::avrcp::AvrcpService::DebugDump(fd);
  btif_debug_config_dump(fd);
  gatt_tcb_dump(fd);
  device_debug_iot_config_dump(fd);
  BTA_HfClientDumpStatistics(fd);
  wakelock_debug_dump(fd);
  alarm_debug_dump(fd);
  bluetooth::csis::CsisClient::DebugDump(fd);
  ::le_audio::has::HasClient::DebugDump(fd);
  HearingAid::DebugDump(fd);
  LeAudioClient::DebugDump(fd);
  LeAudioBroadcaster::DebugDump(fd);
  VolumeControl::DebugDump(fd);
  connection_manager::dump(fd);
  bluetooth::bqr::DebugDump(fd);
  PAN_Dumpsys(fd);
  DumpsysHid(fd);
  DumpsysBtaDm(fd);
  bluetooth::shim::Dump(fd, arguments);
  power_telemetry::GetInstance().Dumpsys(fd);
}

static void dumpMetrics(std::string* output) {
  bluetooth::common::BluetoothMetricsLogger::GetInstance()->WriteString(output);
}

static int get_remote_pbap_pce_version(const RawAddress* bd_addr) {
  // Read and restore the PCE version from local storage
  uint16_t pce_version = 0;
  size_t version_value_size = sizeof(pce_version);
  if (!btif_config_get_bin(bd_addr->ToString(),
                           BTIF_STORAGE_KEY_PBAP_PCE_VERSION,
                           (uint8_t*)&pce_version, &version_value_size)) {
    log::warn("Failed to read cached peer PCE version for {}",
              ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
  }
  return pce_version;
}

static bool pbap_pse_dynamic_version_upgrade_is_enabled() {
  if (bluetooth::common::init_flags::
          pbap_pse_dynamic_version_upgrade_is_enabled()) {
    return true;
  }
  log::warn("PBAP PSE dynamic version upgrade is not enabled");
  return false;
}

static const void* get_profile_interface(const char* profile_id) {
  log::info("id = {}", profile_id);

  /* sanity check */
  if (!interface_ready()) return NULL;

  /* check for supported profile interfaces */
  if (is_profile(profile_id, BT_PROFILE_HANDSFREE_ID))
    return bluetooth::headset::GetInterface();

  if (is_profile(profile_id, BT_PROFILE_HANDSFREE_CLIENT_ID))
    return btif_hf_client_get_interface();

  if (is_profile(profile_id, BT_PROFILE_SOCKETS_ID))
    return btif_sock_get_interface();

  if (is_profile(profile_id, BT_PROFILE_PAN_ID))
    return btif_pan_get_interface();

  if (is_profile(profile_id, BT_PROFILE_ADVANCED_AUDIO_ID))
    return btif_av_get_src_interface();

  if (is_profile(profile_id, BT_PROFILE_ADVANCED_AUDIO_SINK_ID))
    return btif_av_get_sink_interface();

  if (is_profile(profile_id, BT_PROFILE_HIDHOST_ID))
    return btif_hh_get_interface();

  if (is_profile(profile_id, BT_PROFILE_HIDDEV_ID))
    return btif_hd_get_interface();

  if (is_profile(profile_id, BT_PROFILE_SDP_CLIENT_ID))
    return btif_sdp_get_interface();

  if (is_profile(profile_id, BT_PROFILE_GATT_ID))
    return btif_gatt_get_interface();

  if (is_profile(profile_id, BT_PROFILE_AV_RC_ID))
    return btif_rc_get_interface();

  if (is_profile(profile_id, BT_PROFILE_AV_RC_CTRL_ID))
    return btif_rc_ctrl_get_interface();

  if (is_profile(profile_id, BT_PROFILE_HEARING_AID_ID))
    return btif_hearing_aid_get_interface();

  if (is_profile(profile_id, BT_PROFILE_HAP_CLIENT_ID))
    return btif_has_client_get_interface();

  if (is_profile(profile_id, BT_KEYSTORE_ID))
    return bluetooth::bluetooth_keystore::getBluetoothKeystoreInterface();

  if (is_profile(profile_id, BT_PROFILE_LE_AUDIO_ID))
    return btif_le_audio_get_interface();

  if (is_profile(profile_id, BT_PROFILE_LE_AUDIO_BROADCASTER_ID))
    return btif_le_audio_broadcaster_get_interface();

  if (is_profile(profile_id, BT_PROFILE_VC_ID))
    return btif_volume_control_get_interface();

  if (is_profile(profile_id, BT_PROFILE_CSIS_CLIENT_ID))
    return btif_csis_client_get_interface();

  bool isBqrEnabled =
      bluetooth::common::InitFlags::IsBluetoothQualityReportCallbackEnabled();
  if (isBqrEnabled) {
    if (is_profile(profile_id, BT_BQR_ID))
      return bluetooth::bqr::getBluetoothQualityReportInterface();
  }

  return NULL;
}

int dut_mode_configure(uint8_t enable) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (!stack_manager_get_interface()->get_stack_is_running())
    return BT_STATUS_NOT_READY;

  do_in_main_thread(FROM_HERE, base::BindOnce(btif_dut_mode_configure, enable));
  return BT_STATUS_SUCCESS;
}

int dut_mode_send(uint16_t opcode, uint8_t* buf, uint8_t len) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;
  if (!btif_is_dut_mode()) return BT_STATUS_FAIL;

  uint8_t* copy = (uint8_t*)osi_calloc(len);
  memcpy(copy, buf, len);

  do_in_main_thread(FROM_HERE,
                    base::BindOnce(
                        [](uint16_t opcode, uint8_t* buf, uint8_t len) {
                          btif_dut_mode_send(opcode, buf, len);
                          osi_free(buf);
                        },
                        opcode, copy, len));
  return BT_STATUS_SUCCESS;
}

int le_test_mode(uint16_t opcode, uint8_t* buf, uint8_t len) {
  if (!interface_ready()) return BT_STATUS_NOT_READY;

  switch (opcode) {
    case HCI_BLE_TRANSMITTER_TEST:
      if (len != 3) return BT_STATUS_PARM_INVALID;
      do_in_main_thread(FROM_HERE, base::BindOnce(btif_ble_transmitter_test,
                                                  buf[0], buf[1], buf[2]));
      break;
    case HCI_BLE_RECEIVER_TEST:
      if (len != 1) return BT_STATUS_PARM_INVALID;
      do_in_main_thread(FROM_HERE,
                        base::BindOnce(btif_ble_receiver_test, buf[0]));
      break;
    case HCI_BLE_TEST_END:
      do_in_main_thread(FROM_HERE, base::BindOnce(btif_ble_test_end));
      break;
    default:
      return BT_STATUS_UNSUPPORTED;
  }
  return BT_STATUS_SUCCESS;
}

static bt_os_callouts_t* wakelock_os_callouts_saved = nullptr;

static int acquire_wake_lock_cb(const char* lock_name) {
  return do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(
          base::IgnoreResult(wakelock_os_callouts_saved->acquire_wake_lock),
          lock_name));
}

static int release_wake_lock_cb(const char* lock_name) {
  return do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(
          base::IgnoreResult(wakelock_os_callouts_saved->release_wake_lock),
          lock_name));
}

static bt_os_callouts_t wakelock_os_callouts_jni = {
    sizeof(wakelock_os_callouts_jni),
    acquire_wake_lock_cb,
    release_wake_lock_cb,
};

static int set_os_callouts(bt_os_callouts_t* callouts) {
  wakelock_os_callouts_saved = callouts;
  wakelock_set_os_callouts(&wakelock_os_callouts_jni);
  return BT_STATUS_SUCCESS;
}

static int config_clear(void) {
  log::info("");
  int ret = BT_STATUS_SUCCESS;
  if (!btif_config_clear()) {
    log::error("Failed to clear btif config");
    ret = BT_STATUS_FAIL;
  }

  if (!device_iot_config_clear()) {
    log::error("Failed to clear device iot config");
    ret = BT_STATUS_FAIL;
  }

  return ret;
}

static bluetooth::avrcp::ServiceInterface* get_avrcp_service(void) {
  return bluetooth::avrcp::AvrcpService::GetServiceInterface();
}

static std::string obfuscate_address(const RawAddress& address) {
  return bluetooth::common::AddressObfuscator::GetInstance()->Obfuscate(
      address);
}

static int get_metric_id(const RawAddress& address) {
  return allocate_metric_id_from_metric_id_allocator(address);
}

static int set_dynamic_audio_buffer_size(int codec, int size) {
  return btif_set_dynamic_audio_buffer_size(codec, size);
}

static bool allow_low_latency_audio(bool allowed,
                                    const RawAddress& /* address */) {
  log::info("{}", allowed);
  bluetooth::audio::a2dp::set_audio_low_latency_mode_allowed(allowed);
  return true;
}

static void metadata_changed(const RawAddress& remote_bd_addr, int key,
                             std::vector<uint8_t> value) {
  if (!interface_ready()) {
    log::error("Interface not ready!");
    return;
  }

  do_in_main_thread(
      FROM_HERE, base::BindOnce(btif_dm_metadata_changed, remote_bd_addr, key,
                                std::move(value)));
}

static bool interop_match_addr(const char* feature_name,
                               const RawAddress* addr) {
  if (feature_name == NULL || addr == NULL) {
    return false;
  }

  int feature = interop_feature_name_to_feature_id(feature_name);
  if (feature == -1) {
    log::error("feature doesn't exist: {}", feature_name);
    return false;
  }

  return interop_match_addr((interop_feature_t)feature, addr);
}

static bool interop_match_name(const char* feature_name, const char* name) {
  if (feature_name == NULL || name == NULL) {
    return false;
  }

  int feature = interop_feature_name_to_feature_id(feature_name);
  if (feature == -1) {
    log::error("feature doesn't exist: {}", feature_name);
    return false;
  }

  return interop_match_name((interop_feature_t)feature, name);
}

static bool interop_match_addr_or_name(const char* feature_name,
                                       const RawAddress* addr) {
  if (feature_name == NULL || addr == NULL) {
    return false;
  }

  int feature = interop_feature_name_to_feature_id(feature_name);
  if (feature == -1) {
    log::error("feature doesn't exist: {}", feature_name);
    return false;
  }

  return interop_match_addr_or_name((interop_feature_t)feature, addr,
                                    &btif_storage_get_remote_device_property);
}

static void interop_database_add_remove_addr(bool do_add,
                                             const char* feature_name,
                                             const RawAddress* addr,
                                             int length) {
  if (feature_name == NULL || addr == NULL) {
    return;
  }

  int feature = interop_feature_name_to_feature_id(feature_name);
  if (feature == -1) {
    log::error("feature doesn't exist: {}", feature_name);
    return;
  }

  if (do_add) {
    interop_database_add_addr((interop_feature_t)feature, addr, (size_t)length);
  } else {
    interop_database_remove_addr((interop_feature_t)feature, addr);
  }
}

static void interop_database_add_remove_name(bool do_add,
                                             const char* feature_name,
                                             const char* name) {
  if (feature_name == NULL || name == NULL) {
    return;
  }

  int feature = interop_feature_name_to_feature_id(feature_name);
  if (feature == -1) {
    log::error("feature doesn't exist: {}", feature_name);
    return;
  }

  if (do_add) {
    interop_database_add_name((interop_feature_t)feature, name);
  } else {
    interop_database_remove_name((interop_feature_t)feature, name);
  }
}

EXPORT_SYMBOL bt_interface_t bluetoothInterface = {
    sizeof(bluetoothInterface),
    .init = init,
    .enable = enable,
    .disable = disable,
    .cleanup = cleanup,
    .get_adapter_properties = get_adapter_properties,
    .get_adapter_property = get_adapter_property,
    .set_adapter_property = set_adapter_property,
    .get_remote_device_properties = get_remote_device_properties,
    .get_remote_device_property = get_remote_device_property,
    .set_remote_device_property = set_remote_device_property,
    .get_remote_service_record = nullptr,
    .get_remote_services = get_remote_services,
    .start_discovery = start_discovery,
    .cancel_discovery = cancel_discovery,
    .create_bond = create_bond,
    .create_bond_le = create_bond_le,
    .create_bond_out_of_band = create_bond_out_of_band,
    .remove_bond = remove_bond,
    .cancel_bond = cancel_bond,
    .pairing_is_busy = pairing_is_busy,
    .get_connection_state = get_connection_state,
    .pin_reply = pin_reply,
    .ssp_reply = ssp_reply,
    .get_profile_interface = get_profile_interface,
    .dut_mode_configure = dut_mode_configure,
    .dut_mode_send = dut_mode_send,
    .le_test_mode = le_test_mode,
    .set_os_callouts = set_os_callouts,
    .read_energy_info = read_energy_info,
    .dump = dump,
    .dumpMetrics = dumpMetrics,
    .config_clear = config_clear,
    .interop_database_clear = interop_database_clear,
    .interop_database_add = interop_database_add,
    .get_avrcp_service = get_avrcp_service,
    .obfuscate_address = obfuscate_address,
    .get_metric_id = get_metric_id,
    .set_dynamic_audio_buffer_size = set_dynamic_audio_buffer_size,
    .generate_local_oob_data = generate_local_oob_data,
    .allow_low_latency_audio = allow_low_latency_audio,
    .clear_event_filter = clear_event_filter,
    .clear_event_mask = clear_event_mask,
    .clear_filter_accept_list = clear_filter_accept_list,
    .disconnect_all_acls = disconnect_all_acls,
    .le_rand = le_rand,
    .set_event_filter_inquiry_result_all_devices =
        set_event_filter_inquiry_result_all_devices,
    .set_default_event_mask_except = set_default_event_mask_except,
    .restore_filter_accept_list = restore_filter_accept_list,
    .allow_wake_by_hid = allow_wake_by_hid,
    .set_event_filter_connection_setup_all_devices =
        set_event_filter_connection_setup_all_devices,
    .get_wbs_supported = get_wbs_supported,
    .get_swb_supported = get_swb_supported,
    .is_coding_format_supported = is_coding_format_supported,
    .metadata_changed = metadata_changed,
    .interop_match_addr = interop_match_addr,
    .interop_match_name = interop_match_name,
    .interop_match_addr_or_name = interop_match_addr_or_name,
    .interop_database_add_remove_addr = interop_database_add_remove_addr,
    .interop_database_add_remove_name = interop_database_add_remove_name,
    .get_remote_pbap_pce_version = get_remote_pbap_pce_version,
    .pbap_pse_dynamic_version_upgrade_is_enabled =
        pbap_pse_dynamic_version_upgrade_is_enabled,
};

// callback reporting helpers

bt_property_t* property_deep_copy_array(int num_properties,
                                        bt_property_t* properties) {
  bt_property_t* copy = nullptr;
  if (num_properties > 0) {
    size_t content_len = 0;
    for (int i = 0; i < num_properties; i++) {
      auto len = properties[i].len;
      if (len > 0) {
        content_len += len;
      }
    }

    copy = (bt_property_t*)osi_calloc((sizeof(bt_property_t) * num_properties) +
                                      content_len);
    ASSERT(copy != nullptr);
    uint8_t* content = (uint8_t*)(copy + num_properties);

    for (int i = 0; i < num_properties; i++) {
      auto len = properties[i].len;
      copy[i].type = properties[i].type;
      copy[i].len = len;
      if (len <= 0) {
        continue;
      }
      copy[i].val = content;
      memcpy(content, properties[i].val, len);
      content += len;
    }
  }
  return copy;
}

void invoke_adapter_state_changed_cb(bt_state_t state) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bt_state_t state) {
                                    HAL_CBACK(bt_hal_cbacks,
                                              adapter_state_changed_cb, state);
                                  },
                                  state));
}

void invoke_adapter_properties_cb(bt_status_t status, int num_properties,
                                  bt_property_t* properties) {
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(
                       [](bt_status_t status, int num_properties,
                          bt_property_t* properties) {
                         HAL_CBACK(bt_hal_cbacks, adapter_properties_cb, status,
                                   num_properties, properties);
                         if (properties) {
                           osi_free(properties);
                         }
                       },
                       status, num_properties,
                       property_deep_copy_array(num_properties, properties)));
}

void invoke_remote_device_properties_cb(bt_status_t status, RawAddress bd_addr,
                                        int num_properties,
                                        bt_property_t* properties) {
  do_in_jni_thread(
      FROM_HERE, base::BindOnce(
                     [](bt_status_t status, RawAddress bd_addr,
                        int num_properties, bt_property_t* properties) {
                       HAL_CBACK(bt_hal_cbacks, remote_device_properties_cb,
                                 status, &bd_addr, num_properties, properties);
                       if (properties) {
                         osi_free(properties);
                       }
                     },
                     status, bd_addr, num_properties,
                     property_deep_copy_array(num_properties, properties)));
}

void invoke_device_found_cb(int num_properties, bt_property_t* properties) {
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(
                       [](int num_properties, bt_property_t* properties) {
                         HAL_CBACK(bt_hal_cbacks, device_found_cb,
                                   num_properties, properties);
                         if (properties) {
                           osi_free(properties);
                         }
                       },
                       num_properties,
                       property_deep_copy_array(num_properties, properties)));
}

void invoke_discovery_state_changed_cb(bt_discovery_state_t state) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bt_discovery_state_t state) {
                                    HAL_CBACK(bt_hal_cbacks,
                                              discovery_state_changed_cb,
                                              state);
                                  },
                                  state));
}

void invoke_pin_request_cb(RawAddress bd_addr, bt_bdname_t bd_name,
                           uint32_t cod, bool min_16_digit) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](RawAddress bd_addr, bt_bdname_t bd_name,
                                     uint32_t cod, bool min_16_digit) {
                                    HAL_CBACK(bt_hal_cbacks, pin_request_cb,
                                              &bd_addr, &bd_name, cod,
                                              min_16_digit);
                                  },
                                  bd_addr, bd_name, cod, min_16_digit));
}

void invoke_ssp_request_cb(RawAddress bd_addr, bt_bdname_t bd_name,
                           uint32_t cod, bt_ssp_variant_t pairing_variant,
                           uint32_t pass_key) {
  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(
                       [](RawAddress bd_addr, bt_bdname_t bd_name, uint32_t cod,
                          bt_ssp_variant_t pairing_variant, uint32_t pass_key) {
                         HAL_CBACK(bt_hal_cbacks, ssp_request_cb, &bd_addr,
                                   &bd_name, cod, pairing_variant, pass_key);
                       },
                       bd_addr, bd_name, cod, pairing_variant, pass_key));
}

void invoke_oob_data_request_cb(tBT_TRANSPORT t, bool valid, Octet16 c,
                                Octet16 r, RawAddress raw_address,
                                uint8_t address_type) {
  log::info("");
  bt_oob_data_t oob_data = {};
  const char* local_name;
  BTM_ReadLocalDeviceName(&local_name);
  for (int i = 0; i < BTM_MAX_LOC_BD_NAME_LEN; i++) {
    oob_data.device_name[i] = local_name[i];
  }

  // Set the local address
  int j = 5;
  for (int i = 0; i < 6; i++) {
    oob_data.address[i] = raw_address.address[j];
    j--;
  }
  oob_data.address[6] = address_type;

  // Each value (for C and R) is 16 octets in length
  bool c_empty = true;
  for (int i = 0; i < 16; i++) {
    // C cannot be all 0s, if so then we want to fail
    if (c[i] != 0) c_empty = false;
    oob_data.c[i] = c[i];
    // R is optional and may be empty
    oob_data.r[i] = r[i];
  }
  oob_data.is_valid = valid && !c_empty;
  // The oob_data_length is 2 octects in length.  The value includes the length
  // of itself. 16 + 16 + 2 = 34 Data 0x0022 Little Endian order 0x2200
  oob_data.oob_data_length[0] = 0;
  oob_data.oob_data_length[1] = 34;
  bt_status_t status = do_in_jni_thread(
      FROM_HERE, base::BindOnce(
                     [](tBT_TRANSPORT t, bt_oob_data_t oob_data) {
                       HAL_CBACK(bt_hal_cbacks, generate_local_oob_data_cb, t,
                                 oob_data);
                     },
                     t, oob_data));
  if (status != BT_STATUS_SUCCESS) {
    log::error("Failed to call callback!");
  }
}

void invoke_bond_state_changed_cb(bt_status_t status, RawAddress bd_addr,
                                  bt_bond_state_t state, int fail_reason) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bt_status_t status, RawAddress bd_addr,
                                     bt_bond_state_t state, int fail_reason) {
                                    HAL_CBACK(bt_hal_cbacks,
                                              bond_state_changed_cb, status,
                                              &bd_addr, state, fail_reason);
                                  },
                                  status, bd_addr, state, fail_reason));
}

void invoke_address_consolidate_cb(RawAddress main_bd_addr,
                                   RawAddress secondary_bd_addr) {
  do_in_jni_thread(
      FROM_HERE, base::BindOnce(
                     [](RawAddress main_bd_addr, RawAddress secondary_bd_addr) {
                       HAL_CBACK(bt_hal_cbacks, address_consolidate_cb,
                                 &main_bd_addr, &secondary_bd_addr);
                     },
                     main_bd_addr, secondary_bd_addr));
}

void invoke_le_address_associate_cb(RawAddress main_bd_addr,
                                    RawAddress secondary_bd_addr) {
  do_in_jni_thread(
      FROM_HERE, base::BindOnce(
                     [](RawAddress main_bd_addr, RawAddress secondary_bd_addr) {
                       HAL_CBACK(bt_hal_cbacks, le_address_associate_cb,
                                 &main_bd_addr, &secondary_bd_addr);
                     },
                     main_bd_addr, secondary_bd_addr));
}
void invoke_acl_state_changed_cb(bt_status_t status, RawAddress bd_addr,
                                 bt_acl_state_t state, int transport_link_type,
                                 bt_hci_error_code_t hci_reason,
                                 bt_conn_direction_t direction,
                                 uint16_t acl_handle) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(
          [](bt_status_t status, RawAddress bd_addr, bt_acl_state_t state,
             int transport_link_type, bt_hci_error_code_t hci_reason,
             bt_conn_direction_t direction, uint16_t acl_handle) {
            HAL_CBACK(bt_hal_cbacks, acl_state_changed_cb, status, &bd_addr,
                      state, transport_link_type, hci_reason, direction,
                      acl_handle);
          },
          status, bd_addr, state, transport_link_type, hci_reason, direction,
          acl_handle));
}

void invoke_thread_evt_cb(bt_cb_thread_evt event) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bt_cb_thread_evt event) {
                                    HAL_CBACK(bt_hal_cbacks, thread_evt_cb,
                                              event);
                                    if (event == DISASSOCIATE_JVM) {
                                      bt_hal_cbacks = NULL;
                                    }
                                  },
                                  event));
}

void invoke_le_test_mode_cb(bt_status_t status, uint16_t count) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bt_status_t status, uint16_t count) {
                                    HAL_CBACK(bt_hal_cbacks, le_test_mode_cb,
                                              status, count);
                                  },
                                  status, count));
}

// takes ownership of |uid_data|
void invoke_energy_info_cb(bt_activity_energy_info energy_info,
                           bt_uid_traffic_t* uid_data) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(
          [](bt_activity_energy_info energy_info, bt_uid_traffic_t* uid_data) {
            HAL_CBACK(bt_hal_cbacks, energy_info_cb, &energy_info, uid_data);
            osi_free(uid_data);
          },
          energy_info, uid_data));
}

void invoke_link_quality_report_cb(uint64_t timestamp, int report_id, int rssi,
                                   int snr, int retransmission_count,
                                   int packets_not_receive_count,
                                   int negative_acknowledgement_count) {
  do_in_jni_thread(
      FROM_HERE,
      base::BindOnce(
          [](uint64_t timestamp, int report_id, int rssi, int snr,
             int retransmission_count, int packets_not_receive_count,
             int negative_acknowledgement_count) {
            HAL_CBACK(bt_hal_cbacks, link_quality_report_cb, timestamp,
                      report_id, rssi, snr, retransmission_count,
                      packets_not_receive_count,
                      negative_acknowledgement_count);
          },
          timestamp, report_id, rssi, snr, retransmission_count,
          packets_not_receive_count, negative_acknowledgement_count));
}

void invoke_switch_buffer_size_cb(bool is_low_latency_buffer_size) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bool is_low_latency_buffer_size) {
                                    HAL_CBACK(bt_hal_cbacks,
                                              switch_buffer_size_cb,
                                              is_low_latency_buffer_size);
                                  },
                                  is_low_latency_buffer_size));
}

void invoke_switch_codec_cb(bool is_low_latency_buffer_size) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](bool is_low_latency_buffer_size) {
                                    HAL_CBACK(bt_hal_cbacks, switch_codec_cb,
                                              is_low_latency_buffer_size);
                                  },
                                  is_low_latency_buffer_size));
}

void invoke_key_missing_cb(RawAddress bd_addr) {
  do_in_jni_thread(FROM_HERE, base::BindOnce(
                                  [](RawAddress bd_addr) {
                                    HAL_CBACK(bt_hal_cbacks, key_missing_cb,
                                              bd_addr);
                                  },
                                  bd_addr));
}
