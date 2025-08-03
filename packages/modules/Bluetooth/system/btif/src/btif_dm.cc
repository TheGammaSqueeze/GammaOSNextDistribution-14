/******************************************************************************
 *
 *  Copyright (C) 2016-2017 The Linux Foundation
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
 *  Filename:      btif_dm.c
 *
 *  Description:   Contains Device Management (DM) related functionality
 *
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif_dm"

#include "btif_dm.h"

#include <android_bluetooth_flags.h>
#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>
#include <bluetooth/uuid.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_csis.h>
#include <hardware/bt_hearing_aid.h>
#include <hardware/bt_le_audio.h>
#include <hardware/bt_vc.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <mutex>
#include <optional>

#include "advertise_data_parser.h"
#include "bt_dev_class.h"
#include "bta/dm/bta_dm_disc.h"
#include "bta/include/bta_api.h"
#include "btif/include/stack_manager_t.h"
#include "btif_api.h"
#include "btif_bqr.h"
#include "btif_config.h"
#include "btif_dm.h"
#include "btif_metrics_logging.h"
#include "btif_profile_storage.h"
#include "btif_storage.h"
#include "btif_util.h"
#include "common/init_flags.h"
#include "common/lru_cache.h"
#include "common/metrics.h"
#include "device/include/controller.h"
#include "device/include/interop.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "internal_include/stack_config.h"
#include "main/shim/le_advertising_manager.h"
#include "main_thread.h"
#include "os/log.h"
#include "os/logging/log_adapter.h"
#include "osi/include/allocator.h"
#include "osi/include/properties.h"
#include "osi/include/stack_power_telemetry.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_api_types.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_ble_addr.h"
#include "stack/include/btm_ble_api.h"
#include "stack/include/btm_ble_sec_api.h"
#include "stack/include/btm_ble_sec_api_types.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/btm_sec_api.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/smp_api.h"
#include "stack/sdp/sdpint.h"
#include "storage/config_keys.h"
#include "types/raw_address.h"

#ifdef __ANDROID__
#include <android/sysprop/BluetoothProperties.sysprop.h>
#endif

bool btif_get_device_type(const RawAddress& bda, int* p_device_type);

using bluetooth::Uuid;
using namespace bluetooth;

namespace {
constexpr char kBtmLogTag[] = "API";
constexpr char kBtmLogTagCallback[] = "CBACK";
constexpr char kBtmLogTagSdp[] = "SDP";
}

/******************************************************************************
 *  Constants & Macros
 *****************************************************************************/

const Uuid UUID_HEARING_AID = Uuid::FromString("FDF0");
const Uuid UUID_VC = Uuid::FromString("1844");
const Uuid UUID_CSIS = Uuid::FromString("1846");
const Uuid UUID_LE_AUDIO = Uuid::FromString("184E");
const Uuid UUID_LE_MIDI =
    Uuid::FromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700");
const Uuid UUID_HAS = Uuid::FromString("1854");
const Uuid UUID_BASS = Uuid::FromString("184F");
const Uuid UUID_BATTERY = Uuid::FromString("180F");
const Uuid UUID_A2DP_SINK = Uuid::FromString("110B");

#define BTIF_DM_MAX_SDP_ATTEMPTS_AFTER_PAIRING 2

#ifndef PROPERTY_CLASS_OF_DEVICE
#define PROPERTY_CLASS_OF_DEVICE "bluetooth.device.class_of_device"
#endif

#define NUM_TIMEOUT_RETRIES 5
#ifndef PROPERTY_DEFAULT_DEVICE_NAME
#define PROPERTY_DEFAULT_DEVICE_NAME "bluetooth.device.default_name"
#endif
#ifndef PROPERTY_PRODUCT_MODEL
#define PROPERTY_PRODUCT_MODEL "ro.product.model"
#endif
#define DEFAULT_LOCAL_NAME_MAX 31
#if (DEFAULT_LOCAL_NAME_MAX > BTM_MAX_LOC_BD_NAME_LEN)
#error "default btif local name size exceeds stack supported length"
#endif

#ifndef PROPERTY_BLE_PRIVACY_ENABLED
#define PROPERTY_BLE_PRIVACY_ENABLED "bluetooth.core.gap.le.privacy.enabled"
#endif

#define ENCRYPTED_BREDR 2
#define ENCRYPTED_LE 4

struct btif_dm_pairing_cb_t {
  bt_bond_state_t state;
  RawAddress static_bdaddr;
  RawAddress bd_addr;
  tBTM_BOND_TYPE bond_type;
  uint8_t pin_code_len;
  uint8_t is_ssp;
  uint8_t auth_req;
  uint8_t io_cap;
  uint8_t autopair_attempts;
  uint8_t timeout_retries;
  uint8_t is_local_initiated;
  uint8_t sdp_attempts;
  bool is_le_only;
  bool is_le_nc; /* LE Numeric comparison */
  btif_dm_ble_cb_t ble;
  uint8_t fail_reason;

  enum ServiceDiscoveryState { NOT_STARTED, SCHEDULED, FINISHED };

  ServiceDiscoveryState gatt_over_le;
  ServiceDiscoveryState sdp_over_classic;
};

namespace fmt {
template <>
struct formatter<btif_dm_pairing_cb_t::ServiceDiscoveryState>
    : enum_formatter<btif_dm_pairing_cb_t::ServiceDiscoveryState> {};
}  // namespace fmt

// TODO(jpawlowski): unify ?
// btif_dm_local_key_id_t == tBTM_BLE_LOCAL_ID_KEYS == tBTA_BLE_LOCAL_ID_KEYS
typedef struct {
  Octet16 ir;
  Octet16 irk;
  Octet16 dhk;
} btif_dm_local_key_id_t;

typedef struct {
  bool is_er_rcvd;
  Octet16 er;
  bool is_id_keys_rcvd;
  btif_dm_local_key_id_t id_keys; /* ID kyes */

} btif_dm_local_key_cb_t;

/* this structure holds optional OOB data for remote device */
typedef struct {
  RawAddress bdaddr;       /* peer bdaddr */
  tBT_TRANSPORT transport; /* BR/EDR or LE */
  int data_present;        /* What type(s) of OOB Data present */
  bt_oob_data_t p192_data; /* P192 Data or empty */
  bt_oob_data_t p256_data; /* P256 Data or empty */
} btif_dm_oob_cb_t;

typedef struct {
  unsigned int manufact_id;
} skip_sdp_entry_t;

typedef struct {
  bluetooth::common::LruCache<RawAddress, std::vector<uint8_t>> le_audio_cache;
} btif_dm_metadata_cb_t;

typedef enum {
  BTIF_DM_FUNC_CREATE_BOND,
  BTIF_DM_FUNC_CANCEL_BOND,
  BTIF_DM_FUNC_REMOVE_BOND,
  BTIF_DM_FUNC_BOND_STATE_CHANGED,
} bt_bond_function_t;

typedef struct {
  RawAddress bd_addr;
  bt_bond_function_t function;
  bt_bond_state_t state;
  struct timespec timestamp;
} btif_bond_event_t;

#define BTA_SERVICE_ID_TO_SERVICE_MASK(id) (1 << (id))

#define MAX_BTIF_BOND_EVENT_ENTRIES 15

#define MAX_NUM_DEVICES_IN_EIR_UUID_CACHE 128

static bluetooth::common::LruCache<RawAddress, std::set<Uuid>> eir_uuids_cache(
    MAX_NUM_DEVICES_IN_EIR_UUID_CACHE);

static skip_sdp_entry_t sdp_rejectlist[] = {{76}};  // Apple Mouse and Keyboard

/* This flag will be true if HCI_Inquiry is in progress */
static bool btif_dm_inquiry_in_progress = false;

/*******************************************************************************
 *  Static variables
 ******************************************************************************/
static char btif_default_local_name[DEFAULT_LOCAL_NAME_MAX + 1] = {'\0'};
static uid_set_t* uid_set = NULL;

/* A circular array to keep track of the most recent bond events */
static btif_bond_event_t btif_dm_bond_events[MAX_BTIF_BOND_EVENT_ENTRIES + 1];

static std::mutex bond_event_lock;

/* |btif_num_bond_events| keeps track of the total number of events and can be
   greater than |MAX_BTIF_BOND_EVENT_ENTRIES| */
static size_t btif_num_bond_events = 0;
static size_t btif_events_start_index = 0;
static size_t btif_events_end_index = 0;

/******************************************************************************
 *  Static functions
 *****************************************************************************/
static void btif_dm_ble_sec_req_evt(tBTA_DM_BLE_SEC_REQ* p_ble_req,
                                    bool is_consent);
static void btif_dm_remove_ble_bonding_keys(void);
static void btif_dm_save_ble_bonding_keys(RawAddress& bd_addr);
static btif_dm_pairing_cb_t pairing_cb;
static btif_dm_oob_cb_t oob_cb;
static btif_dm_metadata_cb_t metadata_cb{.le_audio_cache{40}};
static void btif_dm_cb_create_bond(const RawAddress bd_addr,
                                   tBT_TRANSPORT transport);
static void btif_dm_cb_create_bond_le(const RawAddress bd_addr,
                                      tBLE_ADDR_TYPE addr_type);
static void btif_update_remote_properties(const RawAddress& bd_addr,
                                          BD_NAME bd_name, DEV_CLASS dev_class,
                                          tBT_DEVICE_TYPE dev_type);
static btif_dm_local_key_cb_t ble_local_key_cb;
static void btif_dm_ble_key_notif_evt(tBTA_DM_SP_KEY_NOTIF* p_ssp_key_notif);
static void btif_dm_ble_auth_cmpl_evt(tBTA_DM_AUTH_CMPL* p_auth_cmpl);
static void btif_dm_ble_passkey_req_evt(tBTA_DM_PIN_REQ* p_pin_req);
static void btif_dm_ble_key_nc_req_evt(tBTA_DM_SP_KEY_NOTIF* p_notif_req);
static void btif_dm_ble_oob_req_evt(tBTA_DM_SP_RMT_OOB* req_oob_type);
static void btif_dm_ble_sc_oob_req_evt(tBTA_DM_SP_RMT_OOB* req_oob_type);

static const char* btif_get_default_local_name();

static void btif_stats_add_bond_event(const RawAddress& bd_addr,
                                      bt_bond_function_t function,
                                      bt_bond_state_t state);

/******************************************************************************
 *  Externs
 *****************************************************************************/
bt_status_t btif_sdp_execute_service(bool b_enable);
void btif_iot_update_remote_info(tBTA_DM_AUTH_CMPL* p_auth_cmpl, bool is_ble,
                                 bool is_ssp);

/******************************************************************************
 *  Functions
 *****************************************************************************/

static bool is_empty_128bit(uint8_t* data) {
  static const uint8_t zero[16] = {0};
  return !memcmp(zero, data, sizeof(zero));
}

static bool is_bonding_or_sdp() {
  return pairing_cb.state == BT_BOND_STATE_BONDING ||
         (pairing_cb.state == BT_BOND_STATE_BONDED && pairing_cb.sdp_attempts);
}

void btif_dm_init(uid_set_t* set) { uid_set = set; }

void btif_dm_cleanup(void) {
  if (uid_set) {
    uid_set_destroy(uid_set);
    uid_set = NULL;
  }
}

bt_status_t btif_in_execute_service_request(tBTA_SERVICE_ID service_id,
                                            bool b_enable) {
  log::verbose("service_id:{}", service_id);

  if (service_id == BTA_SDP_SERVICE_ID) {
    btif_sdp_execute_service(b_enable);
    return BT_STATUS_SUCCESS;
  }

  return GetInterfaceToProfiles()->toggleProfile(service_id, b_enable);
}

/**
 * Helper method to get asha advertising service data
 * @param inq_res {@code tBTA_DM_INQ_RES} inquiry result
 * @param asha_capability value will be updated as non-negative if found,
 * otherwise return -1
 * @param asha_truncated_hi_sync_id value will be updated if found, otherwise no
 * change
 */
static void get_asha_service_data(const tBTA_DM_INQ_RES& inq_res,
                                  int16_t& asha_capability,
                                  uint32_t& asha_truncated_hi_sync_id) {
  asha_capability = -1;
  if (inq_res.p_eir) {
    const RawAddress& bdaddr = inq_res.bd_addr;

    // iterate through advertisement service data
    const uint8_t* p_service_data = inq_res.p_eir;
    uint8_t service_data_len = 0;
    while ((p_service_data = AdvertiseDataParser::GetFieldByType(
                p_service_data + service_data_len,
                inq_res.eir_len - (p_service_data - inq_res.p_eir) -
                    service_data_len,
                BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE, &service_data_len))) {
      if (service_data_len < 2) {
        continue;
      }
      uint16_t uuid;
      const uint8_t* p_uuid = p_service_data;
      STREAM_TO_UINT16(uuid, p_uuid);

      if (uuid == 0xfdf0 /* ASHA service*/) {
        log::info("ASHA found in {}", ADDRESS_TO_LOGGABLE_CSTR(bdaddr));

        // ASHA advertisement service data length should be at least 8
        if (service_data_len < 8) {
          log::warn("ASHA device service_data_len too short");
        } else {
          // It is intended to save ASHA capability byte to int16_t
          asha_capability = p_service_data[3];
          log::info("asha_capability: {}", asha_capability);

          const uint8_t* p_truncated_hisyncid = &(p_service_data[4]);
          STREAM_TO_UINT32(asha_truncated_hi_sync_id, p_truncated_hisyncid);
        }
        break;
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         check_eir_remote_name
 *
 * Description      Check if remote name is in the EIR data
 *
 * Returns          true if remote name found
 *                  Populate p_remote_name, if provided and remote name found
 *
 ******************************************************************************/
static bool check_eir_remote_name(tBTA_DM_SEARCH* p_search_data,
                                  uint8_t* p_remote_name,
                                  uint8_t* p_remote_name_len) {
  const uint8_t* p_eir_remote_name = NULL;
  uint8_t remote_name_len = 0;

  /* Check EIR for remote name and services */
  if (p_search_data->inq_res.p_eir) {
    p_eir_remote_name = AdvertiseDataParser::GetFieldByType(
        p_search_data->inq_res.p_eir, p_search_data->inq_res.eir_len,
        HCI_EIR_COMPLETE_LOCAL_NAME_TYPE, &remote_name_len);
    if (!p_eir_remote_name) {
      p_eir_remote_name = AdvertiseDataParser::GetFieldByType(
          p_search_data->inq_res.p_eir, p_search_data->inq_res.eir_len,
          HCI_EIR_SHORTENED_LOCAL_NAME_TYPE, &remote_name_len);
    }

    if (p_eir_remote_name) {
      if (remote_name_len > BD_NAME_LEN) remote_name_len = BD_NAME_LEN;

      if (p_remote_name && p_remote_name_len) {
        memcpy(p_remote_name, p_eir_remote_name, remote_name_len);
        *(p_remote_name + remote_name_len) = 0;
        *p_remote_name_len = remote_name_len;
      }

      return true;
    }
  }

  return false;
}

/*******************************************************************************
 *
 * Function         check_eir_appearance
 *
 * Description      Check if appearance is in the EIR data
 *
 * Returns          true if appearance found
 *                  Populate p_appearance, if provided and appearance found
 *
 ******************************************************************************/
static bool check_eir_appearance(tBTA_DM_SEARCH* p_search_data,
                                 uint16_t* p_appearance) {
  const uint8_t* p_eir_appearance = NULL;
  uint8_t appearance_len = 0;

  /* Check EIR for remote name and services */
  if (p_search_data->inq_res.p_eir) {
    p_eir_appearance = AdvertiseDataParser::GetFieldByType(
        p_search_data->inq_res.p_eir, p_search_data->inq_res.eir_len,
        HCI_EIR_APPEARANCE_TYPE, &appearance_len);

    if (p_eir_appearance && appearance_len >= 2) {
      if (p_appearance) {
        *p_appearance = *((uint16_t*)p_eir_appearance);
      }

      return true;
    }
  }

  return false;
}

/*******************************************************************************
 *
 * Function         check_cached_remote_name
 *
 * Description      Check if remote name is in the NVRAM cache
 *
 * Returns          true if remote name found
 *                  Populate p_remote_name, if provided and remote name found
 *
 ******************************************************************************/
static bool check_cached_remote_name(tBTA_DM_SEARCH* p_search_data,
                                     uint8_t* p_remote_name,
                                     uint8_t* p_remote_name_len) {
  bt_bdname_t bdname;
  bt_property_t prop_name;

  /* check if we already have it in our btif_storage cache */

  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_BDNAME,
                             sizeof(bt_bdname_t), &bdname);
  if (btif_storage_get_remote_device_property(
          &p_search_data->inq_res.bd_addr, &prop_name) == BT_STATUS_SUCCESS) {
    if (p_remote_name && p_remote_name_len) {
      strcpy((char*)p_remote_name, (char*)bdname.name);
      *p_remote_name_len = strlen((char*)p_remote_name);
    }
    return true;
  }

  return false;
}

static uint32_t get_cod(const RawAddress* remote_bdaddr) {
  uint32_t remote_cod;
  bt_property_t prop_name;

  /* check if we already have it in our btif_storage cache */
  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_CLASS_OF_DEVICE,
                             sizeof(uint32_t), &remote_cod);
  if (btif_storage_get_remote_device_property(
          (RawAddress*)remote_bdaddr, &prop_name) == BT_STATUS_SUCCESS) {
    log::info("remote_cod=0x{:08x}", remote_cod);
    return remote_cod;
  }

  return 0;
}

bool check_cod(const RawAddress* remote_bdaddr, uint32_t cod) {
  return (get_cod(remote_bdaddr) & COD_DEVICE_MASK) == cod;
}

bool check_cod_hid(const RawAddress* remote_bdaddr) {
  return (get_cod(remote_bdaddr) & COD_HID_MASK) == COD_HID_MAJOR;
}

bool check_cod_hid(const RawAddress& bd_addr) {
  return (get_cod(&bd_addr) & COD_HID_MASK) == COD_HID_MAJOR;
}

bool check_cod_hid_major(const RawAddress& bd_addr, uint32_t cod) {
  uint32_t remote_cod = get_cod(&bd_addr);
  return (remote_cod & COD_HID_MASK) == COD_HID_MAJOR &&
         (remote_cod & COD_HID_SUB_MAJOR) == (cod & COD_HID_SUB_MAJOR);
}

bool check_cod_le_audio(const RawAddress& bd_addr) {
  return (get_cod(&bd_addr) & COD_CLASS_LE_AUDIO) == COD_CLASS_LE_AUDIO;
}
/*****************************************************************************
 *
 * Function        check_sdp_bl
 *
 * Description     Checks if a given device is rejectlisted to skip sdp
 *
 * Parameters     skip_sdp_entry
 *
 * Returns         true if the device is present in rejectlist, else false
 *
 ******************************************************************************/
static bool check_sdp_bl(const RawAddress* remote_bdaddr) {
  bt_property_t prop_name;
  bt_remote_version_t info;

  if (remote_bdaddr == NULL) return false;

  /* if not available yet, try fetching from config database */
  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_REMOTE_VERSION_INFO,
                             sizeof(bt_remote_version_t), &info);

  if (btif_storage_get_remote_device_property(remote_bdaddr, &prop_name) !=
      BT_STATUS_SUCCESS) {
    return false;
  }
  uint16_t manufacturer = info.manufacturer;

  for (unsigned int i = 0; i < ARRAY_SIZE(sdp_rejectlist); i++) {
    if (manufacturer == sdp_rejectlist[i].manufact_id) return true;
  }
  return false;
}

static void bond_state_changed(bt_status_t status, const RawAddress& bd_addr,
                               bt_bond_state_t state) {
  btif_stats_add_bond_event(bd_addr, BTIF_DM_FUNC_BOND_STATE_CHANGED, state);

  if ((pairing_cb.state == state) && (state == BT_BOND_STATE_BONDING)) {
    // Cross key pairing so send callback for static address
    if (!pairing_cb.static_bdaddr.IsEmpty()) {
      BTM_LogHistory(
          kBtmLogTagCallback, bd_addr, "Bond state changed",
          base::StringPrintf(
              "Crosskey bt_status:%s bond_state:%u reason:%s",
              bt_status_text(status).c_str(), state,
              hci_reason_code_text(to_hci_reason_code(pairing_cb.fail_reason))
                  .c_str()));
      GetInterfaceToProfiles()->events->invoke_bond_state_changed_cb(
          status, bd_addr, state, pairing_cb.fail_reason);
    }
    return;
  }

  if (pairing_cb.bond_type == BOND_TYPE_TEMPORARY) {
    state = BT_BOND_STATE_NONE;
  }

  log::info(
      "Bond state changed to state={}[0:none, 1:bonding, "
      "2:bonded],prev_state={}, sdp_attempts={}",
      state, pairing_cb.state, pairing_cb.sdp_attempts);

  if (state == BT_BOND_STATE_NONE) {
    forget_device_from_metric_id_allocator(bd_addr);

    if (bluetooth::common::init_flags::
            pbap_pse_dynamic_version_upgrade_is_enabled()) {
      if (btif_storage_is_pce_version_102(bd_addr)) {
        update_pce_entry_to_interop_database(bd_addr);
      }
    }
  } else if (state == BT_BOND_STATE_BONDED) {
    allocate_metric_id_from_metric_id_allocator(bd_addr);
    if (!save_metric_id_from_metric_id_allocator(bd_addr)) {
      log::error("Fail to save metric id for device:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }
  BTM_LogHistory(
      kBtmLogTagCallback, bd_addr, "Bond state changed",
      base::StringPrintf(
          "bt_status:%s bond_state:%u reason:%s",
          bt_status_text(status).c_str(), state,
          hci_reason_code_text(to_hci_reason_code(pairing_cb.fail_reason))
              .c_str()));
  GetInterfaceToProfiles()->events->invoke_bond_state_changed_cb(
      status, bd_addr, state, pairing_cb.fail_reason);

  if ((state == BT_BOND_STATE_NONE) && (pairing_cb.bd_addr != bd_addr)
      && is_bonding_or_sdp()) {
    log::warn(
        "Ignoring bond state changed for unexpected device: {} pairing: {}",
        ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
        ADDRESS_TO_LOGGABLE_CSTR(pairing_cb.bd_addr));
    return;
  }

  if (state == BT_BOND_STATE_BONDING ||
      (state == BT_BOND_STATE_BONDED &&
       (pairing_cb.sdp_attempts > 0 ||
        pairing_cb.gatt_over_le ==
            btif_dm_pairing_cb_t::ServiceDiscoveryState::SCHEDULED))) {
    // Save state for the device is bonding or SDP or GATT over LE discovery
    pairing_cb.state = state;
    pairing_cb.bd_addr = bd_addr;
  } else {
    log::debug("clearing btif pairing_cb");
    pairing_cb = {};
  }
}

/* store remote version in bt config to always have access
   to it post pairing*/
static void btif_update_remote_version_property(RawAddress* p_bd) {
  bt_property_t property;
  uint8_t lmp_ver = 0;
  uint16_t lmp_subver = 0;
  uint16_t mfct_set = 0;
  bt_remote_version_t info;
  bt_status_t status;

  CHECK(p_bd != nullptr);

  const bool version_info_valid =
      BTM_ReadRemoteVersion(*p_bd, &lmp_ver, &mfct_set, &lmp_subver);

  log::info("Remote version info valid:{} [{}]:0x{:x},0x{:x},0x{:x}",
            logbool(version_info_valid), ADDRESS_TO_LOGGABLE_CSTR((*p_bd)),
            lmp_ver, mfct_set, lmp_subver);

  if (version_info_valid) {
    // Always update cache to ensure we have availability whenever BTM API is
    // not populated
    info.manufacturer = mfct_set;
    info.sub_ver = lmp_subver;
    info.version = lmp_ver;
    BTIF_STORAGE_FILL_PROPERTY(&property, BT_PROPERTY_REMOTE_VERSION_INFO,
                               sizeof(bt_remote_version_t), &info);
    status = btif_storage_set_remote_device_property(p_bd, &property);
    ASSERTC(status == BT_STATUS_SUCCESS, "failed to save remote version",
            status);
  }
}

static void btif_update_remote_properties(const RawAddress& bdaddr,
                                          BD_NAME bd_name, DEV_CLASS dev_class,
                                          tBT_DEVICE_TYPE device_type) {
  int num_properties = 0;
  bt_property_t properties[3];
  bt_status_t status = BT_STATUS_UNHANDLED;
  uint32_t cod;
  uint32_t dev_type;

  memset(properties, 0, sizeof(properties));

  /* remote name */
  if (strlen((const char*)bd_name)) {
    BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties], BT_PROPERTY_BDNAME,
                               strlen((char*)bd_name), bd_name);
    status = btif_storage_set_remote_device_property(
        &bdaddr, &properties[num_properties]);
    ASSERTC(status == BT_STATUS_SUCCESS, "failed to save remote device name",
            status);
    num_properties++;
  }

  /* class of device */
  cod = devclass2uint(dev_class);
  if ((cod == 0) || (cod == COD_UNCLASSIFIED)) {
    /* Try to retrieve cod from storage */
    log::verbose("class of device (cod) is unclassified, checking storage");
    BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                               BT_PROPERTY_CLASS_OF_DEVICE, sizeof(cod), &cod);
    status = btif_storage_get_remote_device_property(
        &bdaddr, &properties[num_properties]);
    log::verbose("cod retrieved from storage is 0x{:06x}", cod);
    if (cod == 0) {
      log::info("cod from storage is also unclassified");
      cod = COD_UNCLASSIFIED;
    }
  } else {
    log::info("class of device (cod) is 0x{:06x}", cod);
  }

  BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                             BT_PROPERTY_CLASS_OF_DEVICE, sizeof(cod), &cod);

  status = btif_storage_set_remote_device_property(&bdaddr,
                                                   &properties[num_properties]);
  ASSERTC(status == BT_STATUS_SUCCESS, "failed to save remote device class",
          status);
  num_properties++;

  /* device type */
  bt_property_t prop_name;
  uint32_t remote_dev_type;
  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_TYPE_OF_DEVICE,
                             sizeof(uint32_t), &remote_dev_type);
  if (btif_storage_get_remote_device_property(&bdaddr, &prop_name) ==
      BT_STATUS_SUCCESS) {
    dev_type = remote_dev_type | device_type;
  } else {
    dev_type = device_type;
  }

  BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                             BT_PROPERTY_TYPE_OF_DEVICE, sizeof(dev_type),
                             &dev_type);
  status = btif_storage_set_remote_device_property(&bdaddr,
                                                   &properties[num_properties]);
  ASSERTC(status == BT_STATUS_SUCCESS, "failed to save remote device type",
          status);
  num_properties++;

  GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
      status, bdaddr, num_properties, properties);
}

/* If device is LE Audio capable, we prefer LE connection first, this speeds
 * up LE profile connection, and limits all possible service discovery
 * ordering issues (first Classic, GATT over SDP, etc) */
bool is_device_le_audio_capable(const RawAddress bd_addr) {
  if (!GetInterfaceToProfiles()
           ->profileSpecific_HACK->IsLeAudioClientRunning()) {
    /* If LE Audio profile is not enabled, do nothing. */
    return false;
  }

  if (!check_cod_le_audio(bd_addr) && !BTA_DmCheckLeAudioCapable(bd_addr)) {
    /* LE Audio not present in CoD or in LE Advertisement, do nothing.*/
    return false;
  }

  /* First try reading device type from BTIF - it persists over multiple
   * inquiry sessions */
  int dev_type = 0;
  if (IS_FLAG_ENABLED(le_audio_dev_type_detection_fix) &&
      (btif_get_device_type(bd_addr, &dev_type) &&
       (dev_type & BT_DEVICE_TYPE_BLE) == BT_DEVICE_TYPE_BLE)) {
    /* LE Audio capable device is discoverable over both LE and Classic using
     * same address. Prefer to use LE transport, as we don't know if it can do
     * CTKD from Classic to LE */
    return true;
  }

  tBT_DEVICE_TYPE tmp_dev_type;
  tBLE_ADDR_TYPE addr_type = BLE_ADDR_PUBLIC;
  BTM_ReadDevInfo(bd_addr, &tmp_dev_type, &addr_type);
  if (tmp_dev_type & BT_DEVICE_TYPE_BLE) {
    /* LE Audio capable device is discoverable over both LE and Classic using
     * same address. Prefer to use LE transport, as we don't know if it can do
     * CTKD from Classic to LE */
    return true;
  }

  return false;
}

/* use to check if device is LE Audio Capable during bonding */
bool is_le_audio_capable_during_service_discovery(const RawAddress& bd_addr) {
  if (!GetInterfaceToProfiles()
           ->profileSpecific_HACK->IsLeAudioClientRunning()) {
    /* If LE Audio profile is not enabled, do nothing. */
    return false;
  }

  if (bd_addr != pairing_cb.bd_addr && bd_addr != pairing_cb.static_bdaddr) {
    return false;
  }

  if (check_cod_le_audio(bd_addr) ||
      metadata_cb.le_audio_cache.contains(bd_addr) ||
      metadata_cb.le_audio_cache.contains(pairing_cb.bd_addr) ||
      BTA_DmCheckLeAudioCapable(bd_addr)) {
    return true;
  }

  return false;
}

/*******************************************************************************
 *
 * Function         btif_dm_cb_create_bond
 *
 * Description      Create bond initiated from the BTIF thread context
 *                  Special handling for HID devices
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_cb_create_bond(const RawAddress bd_addr,
                                   tBT_TRANSPORT transport) {
  bool is_hid = check_cod_hid_major(bd_addr, COD_HID_POINTING);
  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);

  if (transport == BT_TRANSPORT_AUTO && is_device_le_audio_capable(bd_addr)) {
    log::debug("LE Audio capable, forcing LE transport for Bonding");
    transport = BT_TRANSPORT_LE;
  }

  int device_type = 0;
  tBLE_ADDR_TYPE addr_type = BLE_ADDR_PUBLIC;
  std::string addrstr = bd_addr.ToString();
  const char* bdstr = addrstr.c_str();
  if (transport == BT_TRANSPORT_LE) {
    if (!btif_config_get_int(bdstr, BTIF_STORAGE_KEY_DEV_TYPE, &device_type)) {
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_DEV_TYPE, BT_DEVICE_TYPE_BLE);
    }
    if (btif_storage_get_remote_addr_type(&bd_addr, &addr_type) !=
        BT_STATUS_SUCCESS) {
      // Try to read address type. OOB pairing might have set it earlier, but
      // didn't store it, it defaults to BLE_ADDR_PUBLIC
      uint8_t tmp_dev_type;
      tBLE_ADDR_TYPE tmp_addr_type = BLE_ADDR_PUBLIC;
      BTM_ReadDevInfo(bd_addr, &tmp_dev_type, &tmp_addr_type);
      addr_type = tmp_addr_type;

      btif_storage_set_remote_addr_type(&bd_addr, addr_type);
    }
  }
  if ((btif_config_get_int(bdstr, BTIF_STORAGE_KEY_DEV_TYPE, &device_type) &&
       (btif_storage_get_remote_addr_type(&bd_addr, &addr_type) ==
        BT_STATUS_SUCCESS) &&
       (device_type & BT_DEVICE_TYPE_BLE) == BT_DEVICE_TYPE_BLE) ||
      (transport == BT_TRANSPORT_LE)) {
    BTA_DmAddBleDevice(bd_addr, addr_type,
                       static_cast<tBT_DEVICE_TYPE>(device_type));
  }

  if (!IS_FLAG_ENABLED(connect_hid_after_service_discovery) &&
      is_hid && (device_type & BT_DEVICE_TYPE_BLE) == 0) {
    tAclLinkSpec link_spec;
    link_spec.addrt.bda = bd_addr;
    link_spec.addrt.type = addr_type;
    link_spec.transport = transport;
    const bt_status_t status =
        GetInterfaceToProfiles()->profileSpecific_HACK->btif_hh_connect(
            &link_spec);
    if (status != BT_STATUS_SUCCESS)
      bond_state_changed(status, bd_addr, BT_BOND_STATE_NONE);
  } else {
    BTA_DmBond(bd_addr, addr_type, transport, device_type);
  }
  /*  Track  originator of bond creation  */
  pairing_cb.is_local_initiated = true;
}

/*******************************************************************************
 *
 * Function         btif_dm_cb_create_bond_le
 *
 * Description      Create bond initiated with le device from the BTIF thread
 *                  context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_cb_create_bond_le(const RawAddress bd_addr,
                                      tBLE_ADDR_TYPE addr_type) {
  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  /* Handle only LE create bond with random address case */
  BTA_DmAddBleDevice(bd_addr, addr_type, BT_DEVICE_TYPE_BLE);
  BTA_DmBond(bd_addr, addr_type, BT_TRANSPORT_LE, BT_DEVICE_TYPE_BLE);
  /*  Track  originator of bond creation  */
  pairing_cb.is_local_initiated = true;
}

/*******************************************************************************
 *
 * Function         btif_dm_get_connection_state
 *
 * Description      Returns whether the remote device is currently connected
 *                  and whether encryption is active for the connection
 *
 * Returns          0 if not connected; 1 if connected and > 1 if connection is
 *                  encrypted
 *
 ******************************************************************************/
uint16_t btif_dm_get_connection_state(const RawAddress& bd_addr) {
  uint16_t rc = 0;
  if (BTA_DmGetConnectionState(bd_addr)) {
    rc = (uint16_t) true;
    if (BTM_IsEncrypted(bd_addr, BT_TRANSPORT_BR_EDR)) {
      rc |= ENCRYPTED_BREDR;
    }
    if (BTM_IsEncrypted(bd_addr, BT_TRANSPORT_LE)) {
      rc |= ENCRYPTED_LE;
    }
  } else {
    log::info("Acl is not connected to peer:{}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  }

  BTM_LogHistory(
      kBtmLogTag, bd_addr, "Get connection state",
      base::StringPrintf("connected:%c classic_encrypted:%c le_encrypted:%c",
                         (rc & (int)true) ? 'T' : 'F',
                         (rc & ENCRYPTED_BREDR) ? 'T' : 'F',
                         (rc & ENCRYPTED_LE) ? 'T' : 'F'));
  return rc;
}

static uint16_t btif_dm_get_resolved_connection_state(
    tBLE_BD_ADDR ble_bd_addr) {
  uint16_t rc = 0;
  if (maybe_resolve_address(&ble_bd_addr.bda, &ble_bd_addr.type)) {
    if (BTA_DmGetConnectionState(ble_bd_addr.bda)) {
      rc = 0x0001;
      if (BTM_IsEncrypted(ble_bd_addr.bda, BT_TRANSPORT_BR_EDR)) {
        rc |= ENCRYPTED_BREDR;
      }
      if (BTM_IsEncrypted(ble_bd_addr.bda, BT_TRANSPORT_LE)) {
        rc |= ENCRYPTED_LE;
      }

      BTM_LogHistory(
          kBtmLogTag, ble_bd_addr.bda, "RESOLVED connection state",
          base::StringPrintf(
              "connected:%c classic_encrypted:%c le_encrypted:%c",
              (rc & 0x0001) ? 'T' : 'F', (rc & ENCRYPTED_BREDR) ? 'T' : 'F',
              (rc & ENCRYPTED_LE) ? 'T' : 'F'));
    }
  }
  return rc;
}

uint16_t btif_dm_get_connection_state_sync(const RawAddress& bd_addr) {
  std::promise<uint16_t> promise;
  std::future future = promise.get_future();

  ASSERT(BT_STATUS_SUCCESS ==
         do_in_main_thread(
             FROM_HERE,
             base::BindOnce(
                 [](const RawAddress bd_addr, std::promise<uint16_t> promise) {
                   // Experiment to try with maybe resolved address
                   uint16_t state = btif_dm_get_resolved_connection_state({
                       .type = BLE_ADDR_RANDOM,
                       .bda = bd_addr,
                   });
                   state |= btif_dm_get_connection_state(bd_addr);
                   promise.set_value(state);
                 },
                 bd_addr, std::move(promise))));
  return future.get();
}

/******************************************************************************
 *
 *  BTIF DM callback events
 *
 ****************************************************************************/

/*******************************************************************************
 *
 * Function         btif_dm_pin_req_evt
 *
 * Description      Executes pin request event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_pin_req_evt(tBTA_DM_PIN_REQ* p_pin_req) {
  bt_bdname_t bd_name;
  uint32_t cod;
  bt_pin_code_t pin_code;
  int dev_type;

  /* Remote properties update */
  if (BTM_GetPeerDeviceTypeFromFeatures(p_pin_req->bd_addr) ==
      BT_DEVICE_TYPE_DUMO) {
    dev_type = BT_DEVICE_TYPE_DUMO;
  } else if (!btif_get_device_type(p_pin_req->bd_addr, &dev_type)) {
    // Failed to get device type, defaulting to BR/EDR.
    dev_type = BT_DEVICE_TYPE_BREDR;
  }
  btif_update_remote_properties(p_pin_req->bd_addr, p_pin_req->bd_name,
                                p_pin_req->dev_class,
                                (tBT_DEVICE_TYPE)dev_type);

  const RawAddress& bd_addr = p_pin_req->bd_addr;
  memcpy(bd_name.name, p_pin_req->bd_name, BD_NAME_LEN);
  bd_name.name[BD_NAME_LEN] = '\0';

  if (pairing_cb.state == BT_BOND_STATE_BONDING &&
      bd_addr != pairing_cb.bd_addr) {
    log::warn("already in bonding state, reject request");
    return;
  }

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);

  cod = devclass2uint(p_pin_req->dev_class);

  if (cod == 0) {
    log::warn("cod is 0, set as unclassified");
    cod = COD_UNCLASSIFIED;
  }

  /* check for auto pair possiblity only if bond was initiated by local device
   */
  if (pairing_cb.is_local_initiated && !p_pin_req->min_16_digit) {
    if (check_cod(&bd_addr, COD_AV_HEADSETS) ||
        check_cod(&bd_addr, COD_AV_HEADPHONES) ||
        check_cod(&bd_addr, COD_AV_PORTABLE_AUDIO) ||
        check_cod(&bd_addr, COD_AV_HIFI_AUDIO) ||
        check_cod_hid_major(bd_addr, COD_HID_POINTING)) {
      /*  Check if this device can be auto paired  */
      if (!interop_match_addr(INTEROP_DISABLE_AUTO_PAIRING, &bd_addr) &&
          !interop_match_name(INTEROP_DISABLE_AUTO_PAIRING,
                              (const char*)bd_name.name) &&
          (pairing_cb.autopair_attempts == 0)) {
        log::debug("Attempting auto pair w/ IOP");
        pin_code.pin[0] = 0x30;
        pin_code.pin[1] = 0x30;
        pin_code.pin[2] = 0x30;
        pin_code.pin[3] = 0x30;

        pairing_cb.autopair_attempts++;
        BTA_DmPinReply(bd_addr, true, 4, pin_code.pin);
        return;
      }
    } else if (check_cod_hid_major(bd_addr, COD_HID_KEYBOARD) ||
               check_cod_hid_major(bd_addr, COD_HID_COMBO)) {
      if ((interop_match_addr(INTEROP_KEYBOARD_REQUIRES_FIXED_PIN, &bd_addr) ==
           true) &&
          (pairing_cb.autopair_attempts == 0)) {
        log::debug("Attempting auto pair w/ IOP");
        pin_code.pin[0] = 0x30;
        pin_code.pin[1] = 0x30;
        pin_code.pin[2] = 0x30;
        pin_code.pin[3] = 0x30;

        pairing_cb.autopair_attempts++;
        BTA_DmPinReply(bd_addr, true, 4, pin_code.pin);
        return;
      }
    }
  }
  BTM_LogHistory(
      kBtmLogTagCallback, bd_addr, "Pin request",
      base::StringPrintf("name:\"%s\" min16:%c", PRIVATE_NAME(bd_name.name),
                         (p_pin_req->min_16_digit) ? 'T' : 'F'));
  GetInterfaceToProfiles()->events->invoke_pin_request_cb(
      bd_addr, bd_name, cod, p_pin_req->min_16_digit);
}

/*******************************************************************************
 *
 * Function         btif_dm_ssp_cfm_req_evt
 *
 * Description      Executes SSP confirm request event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_ssp_cfm_req_evt(tBTA_DM_SP_CFM_REQ* p_ssp_cfm_req) {
  bt_bdname_t bd_name;
  bool is_incoming = !(pairing_cb.state == BT_BOND_STATE_BONDING);
  uint32_t cod;
  int dev_type;

  log::verbose("addr:{}, just_works:{}, loc_auth_req={}, rmt_auth_req={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_ssp_cfm_req->bd_addr),
               p_ssp_cfm_req->just_works, p_ssp_cfm_req->loc_auth_req,
               p_ssp_cfm_req->rmt_auth_req);
  /* Remote properties update */
  if (BTM_GetPeerDeviceTypeFromFeatures(p_ssp_cfm_req->bd_addr) ==
      BT_DEVICE_TYPE_DUMO) {
    dev_type = BT_DEVICE_TYPE_DUMO;
  } else if (!btif_get_device_type(p_ssp_cfm_req->bd_addr, &dev_type)) {
    // Failed to get device type, defaulting to BR/EDR.
    dev_type = BT_DEVICE_TYPE_BREDR;
  }
  btif_update_remote_properties(p_ssp_cfm_req->bd_addr, p_ssp_cfm_req->bd_name,
                                p_ssp_cfm_req->dev_class,
                                (tBT_DEVICE_TYPE)dev_type);

  RawAddress bd_addr = p_ssp_cfm_req->bd_addr;
  memcpy(bd_name.name, p_ssp_cfm_req->bd_name, BD_NAME_LEN);

  if (pairing_cb.state == BT_BOND_STATE_BONDING &&
      bd_addr != pairing_cb.bd_addr) {
    log::warn("already in bonding state, reject request");
    btif_dm_ssp_reply(bd_addr, BT_SSP_VARIANT_PASSKEY_CONFIRMATION, 0);
    return;
  }

  /* Set the pairing_cb based on the local & remote authentication requirements
   */
  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);

  /* if just_works and bonding bit is not set treat this as temporary */
  if (p_ssp_cfm_req->just_works &&
      !(p_ssp_cfm_req->loc_auth_req & BTM_AUTH_BONDS) &&
      !(p_ssp_cfm_req->rmt_auth_req & BTM_AUTH_BONDS) &&
      !(check_cod_hid_major(p_ssp_cfm_req->bd_addr, COD_HID_POINTING)))
    pairing_cb.bond_type = BOND_TYPE_TEMPORARY;
  else
    pairing_cb.bond_type = BOND_TYPE_PERSISTENT;

  btm_set_bond_type_dev(p_ssp_cfm_req->bd_addr, pairing_cb.bond_type);

  pairing_cb.is_ssp = true;

  /* If JustWorks auto-accept */
  if (p_ssp_cfm_req->just_works) {
    /* Pairing consent for JustWorks NOT needed if:
     * Incoming temporary pairing is detected
     */
    if (is_incoming && pairing_cb.bond_type == BOND_TYPE_TEMPORARY) {
      log::debug(
          "Auto-accept JustWorks incoming pairing for temporary bonding");
      btif_dm_ssp_reply(bd_addr, BT_SSP_VARIANT_CONSENT, true);
      return;
    }
  }

  cod = devclass2uint(p_ssp_cfm_req->dev_class);

  if (cod == 0) {
    log::warn("cod is 0, set as unclassified");
    cod = COD_UNCLASSIFIED;
  }

  pairing_cb.sdp_attempts = 0;
  BTM_LogHistory(kBtmLogTagCallback, bd_addr, "Ssp request",
                 base::StringPrintf("name:\"%s\" just_works:%c pin:%u",
                                    PRIVATE_NAME(bd_name.name),
                                    (p_ssp_cfm_req->just_works) ? 'T' : 'F',
                                    p_ssp_cfm_req->num_val));
  GetInterfaceToProfiles()->events->invoke_ssp_request_cb(
      bd_addr, bd_name, cod,
      (p_ssp_cfm_req->just_works ? BT_SSP_VARIANT_CONSENT
                                 : BT_SSP_VARIANT_PASSKEY_CONFIRMATION),
      p_ssp_cfm_req->num_val);
}

static void btif_dm_ssp_key_notif_evt(tBTA_DM_SP_KEY_NOTIF* p_ssp_key_notif) {
  bt_bdname_t bd_name;
  uint32_t cod;
  int dev_type;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_ssp_key_notif->bd_addr));

  /* Remote properties update */
  if (BTM_GetPeerDeviceTypeFromFeatures(p_ssp_key_notif->bd_addr) ==
      BT_DEVICE_TYPE_DUMO) {
    dev_type = BT_DEVICE_TYPE_DUMO;
  } else if (!btif_get_device_type(p_ssp_key_notif->bd_addr, &dev_type)) {
    // Failed to get device type, defaulting to BR/EDR.
    dev_type = BT_DEVICE_TYPE_BREDR;
  }
  btif_update_remote_properties(
      p_ssp_key_notif->bd_addr, p_ssp_key_notif->bd_name,
      p_ssp_key_notif->dev_class, (tBT_DEVICE_TYPE)dev_type);

  RawAddress bd_addr = p_ssp_key_notif->bd_addr;
  memcpy(bd_name.name, p_ssp_key_notif->bd_name, BD_NAME_LEN);
  bd_name.name[BD_NAME_LEN] = '\0';

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  pairing_cb.is_ssp = true;
  cod = devclass2uint(p_ssp_key_notif->dev_class);

  if (cod == 0) {
    log::warn("cod is 0, set as unclassified");
    cod = COD_UNCLASSIFIED;
  }

  BTM_LogHistory(
      kBtmLogTagCallback, bd_addr, "Ssp request",
      base::StringPrintf("name:'%s' passkey:%u", PRIVATE_NAME(bd_name.name),
                         p_ssp_key_notif->passkey));
  GetInterfaceToProfiles()->events->invoke_ssp_request_cb(
      bd_addr, bd_name, cod, BT_SSP_VARIANT_PASSKEY_NOTIFICATION,
      p_ssp_key_notif->passkey);
}
/*******************************************************************************
 *
 * Function         btif_dm_auth_cmpl_evt
 *
 * Description      Executes authentication complete event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_auth_cmpl_evt(tBTA_DM_AUTH_CMPL* p_auth_cmpl) {
  /* Save link key, if not temporary */
  bt_status_t status = BT_STATUS_FAIL;
  bt_bond_state_t state = BT_BOND_STATE_NONE;
  bool skip_sdp = false;

  log::info("bond state={}, success={}, key_present={}", pairing_cb.state,
            p_auth_cmpl->success, p_auth_cmpl->key_present);

  pairing_cb.fail_reason = p_auth_cmpl->fail_reason;

  RawAddress bd_addr = p_auth_cmpl->bd_addr;
  tBLE_ADDR_TYPE addr_type = p_auth_cmpl->addr_type;
  if ((p_auth_cmpl->success) && (p_auth_cmpl->key_present)) {
    if ((p_auth_cmpl->key_type < HCI_LKEY_TYPE_DEBUG_COMB) ||
        (p_auth_cmpl->key_type == HCI_LKEY_TYPE_AUTH_COMB) ||
        (p_auth_cmpl->key_type == HCI_LKEY_TYPE_CHANGED_COMB) ||
        (p_auth_cmpl->key_type == HCI_LKEY_TYPE_AUTH_COMB_P_256) ||
        pairing_cb.bond_type == BOND_TYPE_PERSISTENT) {
      bt_status_t ret;

      if (!bd_addr.IsEmpty()) {
        log::debug("Storing link key. key_type=0x{:x}, bond_type={}",
                   p_auth_cmpl->key_type, pairing_cb.bond_type);
        ret = btif_storage_add_bonded_device(&bd_addr, p_auth_cmpl->key,
                                             p_auth_cmpl->key_type,
                                             pairing_cb.pin_code_len);
      } else {
        log::warn("bd_addr is empty");
        ret = BT_STATUS_FAIL;
      }
      ASSERTC(ret == BT_STATUS_SUCCESS, "storing link key failed", ret);
    } else {
      log::debug("Temporary key. Not storing. key_type=0x{:x}, bond_type={}",
                 p_auth_cmpl->key_type, pairing_cb.bond_type);
      if (pairing_cb.bond_type == BOND_TYPE_TEMPORARY) {
        log::debug("sending BT_BOND_STATE_NONE for Temp pairing");
        btif_storage_remove_bonded_device(&bd_addr);
        bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_NONE);
        return;
      }
    }
  }

  if (p_auth_cmpl->success) {
    // save remote info to iot conf file
    btif_iot_update_remote_info(p_auth_cmpl, false, pairing_cb.is_ssp);

    // We could have received a new link key without going through the pairing
    // flow.  If so, we don't want to perform SDP or any other operations on the
    // authenticated device. Also, make sure that the link key is not derived
    // from secure LTK, because we will need to perform SDP in case of link key
    // derivation to allow bond state change notification for the BR/EDR
    // transport so that the subsequent BR/EDR connections to the remote can use
    // the derived link key.
    if (p_auth_cmpl->bd_addr != pairing_cb.bd_addr &&
        (!pairing_cb.ble.is_penc_key_rcvd)) {
      log::warn("skipping SDP for unknown device {}",
                ADDRESS_TO_LOGGABLE_CSTR(p_auth_cmpl->bd_addr));
      return;
    }

    btif_storage_set_remote_addr_type(&bd_addr, p_auth_cmpl->addr_type);

    int dev_type;
    if (BTM_GetPeerDeviceTypeFromFeatures(bd_addr) == BT_DEVICE_TYPE_DUMO) {
      dev_type = BT_DEVICE_TYPE_DUMO;
    } else {
      dev_type = p_auth_cmpl->dev_type;
    }

    bool is_crosskey = false;
    if (pairing_cb.state == BT_BOND_STATE_BONDING && p_auth_cmpl->is_ctkd) {
      log::debug("bonding initiated due to cross key pairing");
      is_crosskey = true;
    }

    if (!is_crosskey) {
      btif_update_remote_properties(p_auth_cmpl->bd_addr, p_auth_cmpl->bd_name,
                                    kDevClassEmpty, dev_type);
    }

    pairing_cb.timeout_retries = 0;
    status = BT_STATUS_SUCCESS;
    state = BT_BOND_STATE_BONDED;
    bd_addr = p_auth_cmpl->bd_addr;

    if (check_sdp_bl(&bd_addr) && check_cod_hid(&bd_addr)) {
      log::warn("skip SDP");
      skip_sdp = true;
    }
    if (!pairing_cb.is_local_initiated && skip_sdp) {
      bond_state_changed(status, bd_addr, state);

      log::warn("Incoming HID Connection");
      bt_property_t prop;
      Uuid uuid = Uuid::From16Bit(UUID_SERVCLASS_HUMAN_INTERFACE);

      prop.type = BT_PROPERTY_UUIDS;
      prop.val = &uuid;
      prop.len = Uuid::kNumBytes128;

      GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
          BT_STATUS_SUCCESS, bd_addr, 1, &prop);
    } else {
      /* If bonded due to cross-key, save the static address too*/
      if (is_crosskey) {
        log::debug("bonding initiated due to cross key, adding static address");
        pairing_cb.static_bdaddr = bd_addr;
      }
      if (!is_crosskey ||
          !(stack_config_get_interface()->get_pts_crosskey_sdp_disable())) {
        // Ensure inquiry is stopped before attempting service discovery
        btif_dm_cancel_discovery();

        /* Trigger SDP on the device */
        pairing_cb.sdp_attempts = 1;

        if (is_crosskey) {
          // If bonding occurred due to cross-key pairing, send address
          // consolidate callback
          BTM_LogHistory(
              kBtmLogTagCallback, bd_addr, "Consolidate",
              base::StringPrintf(" <=> %s",
                                 ADDRESS_TO_LOGGABLE_CSTR(pairing_cb.bd_addr)));
          GetInterfaceToProfiles()->events->invoke_address_consolidate_cb(
              pairing_cb.bd_addr, bd_addr);
        } else {
          bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDED);
        }

        if (pairing_cb.sdp_over_classic ==
            btif_dm_pairing_cb_t::ServiceDiscoveryState::NOT_STARTED) {
          log::info("scheduling SDP for {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
          pairing_cb.sdp_over_classic =
              btif_dm_pairing_cb_t::ServiceDiscoveryState::SCHEDULED;
          btif_dm_get_remote_services(bd_addr, BT_TRANSPORT_BR_EDR);
        }
      }
    }
    // Do not call bond_state_changed_cb yet. Wait until remote service
    // discovery is complete
  } else {
    log::warn("Bonding failed with failure reason:{}",
              hci_reason_code_text(p_auth_cmpl->fail_reason));
    bool is_bonded_device_removed = false;
    // Map the HCI fail reason  to  bt status
    switch (p_auth_cmpl->fail_reason) {
      case HCI_ERR_PAGE_TIMEOUT:
      case HCI_ERR_LMP_RESPONSE_TIMEOUT:
        if (interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &bd_addr) &&
            pairing_cb.timeout_retries) {
          log::warn("Pairing timeout; retrying ({}) ...",
                    pairing_cb.timeout_retries);
          --pairing_cb.timeout_retries;
          if (addr_type == BLE_ADDR_RANDOM) {
            btif_dm_cb_create_bond_le(bd_addr, addr_type);
          } else {
            btif_dm_cb_create_bond(bd_addr, BT_TRANSPORT_AUTO);
          }
          return;
        }
        FALLTHROUGH_INTENDED; /* FALLTHROUGH */
      case HCI_ERR_CONNECTION_TOUT:
        status = BT_STATUS_RMT_DEV_DOWN;
        break;

      case HCI_ERR_PAIRING_NOT_ALLOWED:
        is_bonded_device_removed = false;
        status = BT_STATUS_AUTH_REJECTED;
        break;

      /* map the auth failure codes, so we can retry pairing if necessary */
      case HCI_ERR_AUTH_FAILURE:
      case HCI_ERR_KEY_MISSING:
        is_bonded_device_removed = false;
        [[fallthrough]];
      case HCI_ERR_HOST_REJECT_SECURITY:
      case HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE:
      case HCI_ERR_UNIT_KEY_USED:
      case HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED:
      case HCI_ERR_INSUFFCIENT_SECURITY:
      case HCI_ERR_PEER_USER:
      case HCI_ERR_UNSPECIFIED:
        log::warn("Authentication fail:{}",
                  hci_reason_code_text(p_auth_cmpl->fail_reason));
        if (pairing_cb.autopair_attempts == 1) {
          /* Create the Bond once again */
          log::warn("auto pair failed. Reinitiate Bond");
          if (addr_type == BLE_ADDR_RANDOM) {
            btif_dm_cb_create_bond_le(bd_addr, addr_type);
          } else {
            btif_dm_cb_create_bond(bd_addr, BT_TRANSPORT_AUTO);
          }
          return;
        } else {
          /* if autopair attempts are more than 1, or not attempted */
          status = BT_STATUS_AUTH_FAILURE;
        }
        break;

      default:
        status = BT_STATUS_FAIL;
    }
    /* Special Handling for HID Devices */
    if (check_cod_hid_major(bd_addr, COD_HID_POINTING)) {
      /* Remove Device as bonded in nvram as authentication failed */
      log::verbose("removing hid pointing device from nvram");
      is_bonded_device_removed = false;
    }
    // Report bond state change to java only if we are bonding to a device or
    // a device is removed from the pairing list.
    if (pairing_cb.state == BT_BOND_STATE_BONDING || is_bonded_device_removed) {
      bond_state_changed(status, bd_addr, state);
    }
  }
}

/******************************************************************************
 *
 * Function         btif_dm_search_devices_evt
 *
 * Description      Executes search devices callback events in btif context
 *
 * Returns          void
 *
 *****************************************************************************/
static void btif_dm_search_devices_evt(tBTA_DM_SEARCH_EVT event,
                                       tBTA_DM_SEARCH* p_search_data) {
  log::verbose("event={}", dump_dm_search_event(event));

  switch (event) {
    case BTA_DM_NAME_READ_EVT: {
      /* Remote name update */
      if (strlen((const char*)p_search_data->disc_res.bd_name)) {
        /** Fix inquiry time too long @{ */
        bt_property_t properties[3];
        /** @} */
        bt_status_t status;

        properties[0].type = BT_PROPERTY_BDNAME;
        properties[0].val = p_search_data->disc_res.bd_name;
        properties[0].len = strlen((char*)p_search_data->disc_res.bd_name);
        RawAddress& bdaddr = p_search_data->disc_res.bd_addr;

        status =
            btif_storage_set_remote_device_property(&bdaddr, &properties[0]);
        ASSERTC(status == BT_STATUS_SUCCESS,
                "failed to save remote device property", status);
        GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
            status, bdaddr, 1, properties);
        /** Fix inquiry time too long @{ */
        uint32_t cod = 0;
        /* Check if we already have cod in our btif_storage cache */
        BTIF_STORAGE_FILL_PROPERTY(&properties[2], BT_PROPERTY_CLASS_OF_DEVICE, sizeof(uint32_t), &cod);
        if (btif_storage_get_remote_device_property(
                        &bdaddr, &properties[2]) == BT_STATUS_SUCCESS) {
          log::verbose("BTA_DM_NAME_READ_EVT, cod in storage=0x{:08x}", cod);
        } else {
          log::info("BTA_DM_NAME_READ_EVT, no cod in storage");
          cod = 0;
        }
        if (cod != 0) {
          BTIF_STORAGE_FILL_PROPERTY(&properties[1], BT_PROPERTY_BDADDR, sizeof(bdaddr), &bdaddr);
          BTIF_STORAGE_FILL_PROPERTY(&properties[2], BT_PROPERTY_CLASS_OF_DEVICE, sizeof(uint32_t), &cod);
          log::debug("report new device to JNI");
          GetInterfaceToProfiles()->events->invoke_device_found_cb(3, properties);
        } else {
          log::info("Skipping RNR callback because cod is zero addr:{} name:{}",
                    ADDRESS_TO_LOGGABLE_CSTR(bdaddr),
                    PRIVATE_NAME(reinterpret_cast<char const*>(
                        p_search_data->disc_res.bd_name)));
        }
        /** @} */
      }
      /* TODO: Services? */
    } break;

    case BTA_DM_INQ_RES_EVT: {
      /* inquiry result */
      bt_bdname_t bdname;
      uint8_t remote_name_len;
      uint8_t num_uuids = 0, max_num_uuid = 32;
      uint8_t uuid_list[32 * Uuid::kNumBytes16];

      if (p_search_data->inq_res.inq_result_type != BT_DEVICE_TYPE_BLE) {
        p_search_data->inq_res.remt_name_not_required =
            check_eir_remote_name(p_search_data, NULL, NULL);
      }
      RawAddress& bdaddr = p_search_data->inq_res.bd_addr;

      log::verbose("addr:{} device_type=0x{:x}",
                   ADDRESS_TO_LOGGABLE_CSTR(bdaddr),
                   p_search_data->inq_res.device_type);
      bdname.name[0] = 0;

      if (!check_eir_remote_name(p_search_data, bdname.name, &remote_name_len))
        check_cached_remote_name(p_search_data, bdname.name, &remote_name_len);

      /* Check EIR for services */
      if (p_search_data->inq_res.p_eir) {
        BTM_GetEirUuidList(p_search_data->inq_res.p_eir,
                           p_search_data->inq_res.eir_len, Uuid::kNumBytes16,
                           &num_uuids, uuid_list, max_num_uuid);
      }

      {
        bt_property_t properties[10];  // increase when properties are added
        uint32_t dev_type;
        uint32_t num_properties = 0;
        bt_status_t status;
        tBLE_ADDR_TYPE addr_type = BLE_ADDR_PUBLIC;

        memset(properties, 0, sizeof(properties));
        /* RawAddress */
        BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                   BT_PROPERTY_BDADDR, sizeof(bdaddr), &bdaddr);
        num_properties++;
        /* BD_NAME */
        /* Don't send BDNAME if it is empty */
        if (bdname.name[0]) {
          BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                     BT_PROPERTY_BDNAME,
                                     strlen((char*)bdname.name), &bdname);
          num_properties++;
        }

        /* DEV_CLASS */
        uint32_t cod = devclass2uint(p_search_data->inq_res.dev_class);
        log::verbose("cod is 0x{:06x}", cod);
        if (cod != 0) {
          BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                     BT_PROPERTY_CLASS_OF_DEVICE, sizeof(cod),
                                     &cod);
          num_properties++;
        }

        log::verbose("clock_offset is 0x{:x}",
                     p_search_data->inq_res.clock_offset);
        if (p_search_data->inq_res.clock_offset & BTM_CLOCK_OFFSET_VALID) {
          btif_set_device_clockoffset(bdaddr, (int)p_search_data->inq_res.clock_offset);
        }

        /* DEV_TYPE */
        /* FixMe: Assumption is that bluetooth.h and BTE enums match */

        /* Verify if the device is dual mode in NVRAM */
        int stored_device_type = 0;
        if (btif_get_device_type(bdaddr, &stored_device_type) &&
            ((stored_device_type != BT_DEVICE_TYPE_BREDR &&
              p_search_data->inq_res.device_type == BT_DEVICE_TYPE_BREDR) ||
             (stored_device_type != BT_DEVICE_TYPE_BLE &&
              p_search_data->inq_res.device_type == BT_DEVICE_TYPE_BLE))) {
          dev_type = (bt_device_type_t)BT_DEVICE_TYPE_DUMO;
        } else {
          dev_type = (bt_device_type_t)p_search_data->inq_res.device_type;
        }

        if (p_search_data->inq_res.device_type == BT_DEVICE_TYPE_BLE)
          addr_type = p_search_data->inq_res.ble_addr_type;
        BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                   BT_PROPERTY_TYPE_OF_DEVICE, sizeof(dev_type),
                                   &dev_type);
        num_properties++;
        /* RSSI */
        BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                   BT_PROPERTY_REMOTE_RSSI, sizeof(int8_t),
                                   &(p_search_data->inq_res.rssi));
        num_properties++;

        /* CSIP supported device */
        BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                   BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER,
                                   sizeof(bool),
                                   &(p_search_data->inq_res.include_rsi));
        num_properties++;

        // The default negative value means ASHA capability not found.
        // A non-negative value represents ASHA capability information is valid.
        // Because ASHA's capability is 1 byte, so int16_t is large enough.
        int16_t asha_capability = -1;

        // contains ASHA truncated HiSyncId if asha_capability is non-negative
        uint32_t asha_truncated_hi_sync_id = 0;

        get_asha_service_data(p_search_data->inq_res, asha_capability,
                              asha_truncated_hi_sync_id);

        BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                   BT_PROPERTY_REMOTE_ASHA_CAPABILITY,
                                   sizeof(int16_t), &asha_capability);
        num_properties++;

        BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                   BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID,
                                   sizeof(uint32_t),
                                   &asha_truncated_hi_sync_id);
        num_properties++;

        // Floss expects that EIR uuids are immediately reported when the
        // device is found and doesn't wait for the pairing intent.
        //
        // If a subsequent SDP is completed, the new UUIDs should replace
        // the existing UUIDs.
#if TARGET_FLOSS
        bool report_eir_uuids = true;
#else
        bool report_eir_uuids = false;
#endif
        // Scope needs to persist until `invoke_device_found_cb` below.
        std::vector<uint8_t> property_value;
        /* Cache EIR queried services */
        if (num_uuids > 0) {
          uint16_t* p_uuid16 = (uint16_t*)uuid_list;
          auto uuid_iter = eir_uuids_cache.find(bdaddr);
          if (uuid_iter == eir_uuids_cache.end()) {
            auto triple = eir_uuids_cache.try_emplace(bdaddr, std::set<Uuid>{});
            uuid_iter = std::get<0>(triple);
          }
          log::info("EIR UUIDs for {}:", ADDRESS_TO_LOGGABLE_CSTR(bdaddr));
          for (int i = 0; i < num_uuids; ++i) {
            Uuid uuid = Uuid::From16Bit(p_uuid16[i]);
            log::info("{}", uuid.ToString());
            uuid_iter->second.insert(uuid);
          }

          if (report_eir_uuids) {
            for (auto uuid : uuid_iter->second) {
              auto uuid_128bit = uuid.To128BitBE();
              property_value.insert(property_value.end(), uuid_128bit.begin(),
                                    uuid_128bit.end());
            }

            BTIF_STORAGE_FILL_PROPERTY(
                &properties[num_properties], BT_PROPERTY_UUIDS,
                uuid_iter->second.size() * Uuid::kNumBytes128,
                (void*)property_value.data());
            num_properties++;
          }
        }

        // Floss needs appearance for metrics purposes
        uint16_t appearance = 0;
        if (check_eir_appearance(p_search_data, &appearance)) {
          BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                                     BT_PROPERTY_APPEARANCE, sizeof(appearance),
                                     &appearance);
          num_properties++;
        }

        status =
            btif_storage_add_remote_device(&bdaddr, num_properties, properties);
        ASSERTC(status == BT_STATUS_SUCCESS,
                "failed to save remote device (inquiry)", status);
        status = btif_storage_set_remote_addr_type(&bdaddr, addr_type);
        ASSERTC(status == BT_STATUS_SUCCESS,
                "failed to save remote addr type (inquiry)", status);

        bool restrict_report = osi_property_get_bool(
            "bluetooth.restrict_discovered_device.enabled", false);
        if (restrict_report &&
            p_search_data->inq_res.device_type == BT_DEVICE_TYPE_BLE &&
            !(p_search_data->inq_res.ble_evt_type & BTM_BLE_CONNECTABLE_MASK)) {
          log::debug("Ble device {} is not connectable",
                     ADDRESS_TO_LOGGABLE_CSTR(bdaddr));
          break;
        }

        /* Callback to notify upper layer of device */
        GetInterfaceToProfiles()->events->invoke_device_found_cb(num_properties,
                                                                 properties);
      }
    } break;

    case BTA_DM_INQ_CMPL_EVT: {
      /* do nothing */
    } break;
    case BTA_DM_DISC_CMPL_EVT: {
      GetInterfaceToProfiles()->events->invoke_discovery_state_changed_cb(
          BT_DISCOVERY_STOPPED);
    } break;
    case BTA_DM_SEARCH_CANCEL_CMPL_EVT: {
      /* if inquiry is not in progress and we get a cancel event, then
       * it means we are done with inquiry, but remote_name fetches are in
       * progress
       *
       * if inquiry  is in progress, then we don't want to act on this
       * cancel_cmpl_evt
       * but instead wait for the cancel_cmpl_evt via the Busy Level
       *
       */
      if (!btif_dm_inquiry_in_progress) {
        GetInterfaceToProfiles()->events->invoke_discovery_state_changed_cb(
            BT_DISCOVERY_STOPPED);
      }
    } break;
    case BTA_DM_GATT_OVER_LE_RES_EVT:
    case BTA_DM_DID_RES_EVT:
    case BTA_DM_GATT_OVER_SDP_RES_EVT:
    default:
      log::warn("Unhandled event:{}", bta_dm_search_evt_text(event));
      break;
  }
}

/* Returns true if |uuid| should be passed as device property */
static bool btif_is_interesting_le_service(bluetooth::Uuid uuid) {
  return (uuid.As16Bit() == UUID_SERVCLASS_LE_HID || uuid == UUID_HEARING_AID ||
          uuid == UUID_VC || uuid == UUID_CSIS || uuid == UUID_LE_AUDIO ||
          uuid == UUID_LE_MIDI || uuid == UUID_HAS || uuid == UUID_BASS ||
          uuid == UUID_BATTERY);
}

static bt_status_t btif_get_existing_uuids(RawAddress* bd_addr,
                                           Uuid* existing_uuids) {
  bt_property_t tmp_prop;
  BTIF_STORAGE_FILL_PROPERTY(&tmp_prop, BT_PROPERTY_UUIDS,
                             sizeof(existing_uuids), existing_uuids);

  return btif_storage_get_remote_device_property(bd_addr, &tmp_prop);
}

static bool btif_should_ignore_uuid(const Uuid& uuid) {
  return uuid.IsEmpty() || uuid.IsBase();
}

static bool btif_is_gatt_service_discovery_post_pairing(const RawAddress bd_addr) {
  if (!IS_FLAG_ENABLED(reset_pairing_only_for_related_service_discovery)) {
    if (bd_addr == pairing_cb.bd_addr || bd_addr == pairing_cb.static_bdaddr) {
      if (pairing_cb.gatt_over_le !=
          btif_dm_pairing_cb_t::ServiceDiscoveryState::SCHEDULED) {
        log::error(
            "gatt_over_le should be SCHEDULED, did someone clear the control "
            "block for {} ?",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      }

      return true;
    }

    return false;
  }

 return ((bd_addr == pairing_cb.bd_addr ||
          bd_addr == pairing_cb.static_bdaddr) &&
         (pairing_cb.gatt_over_le ==
          btif_dm_pairing_cb_t::ServiceDiscoveryState::SCHEDULED));
}

/*******************************************************************************
 *
 * Function         btif_dm_search_services_evt
 *
 * Description      Executes search services event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_search_services_evt(tBTA_DM_SEARCH_EVT event,
                                        tBTA_DM_SEARCH* p_data) {
  switch (event) {
    case BTA_DM_DISC_RES_EVT: {
      bt_property_t prop;
      uint32_t i = 0;
      std::vector<uint8_t> property_value;
      std::set<Uuid> uuids;
      bool a2dp_sink_capable = false;

      RawAddress& bd_addr = p_data->disc_res.bd_addr;

      log::verbose("result=0x{:x}, services 0x{:x}", p_data->disc_res.result,
                   p_data->disc_res.services);
      if (p_data->disc_res.result != BTA_SUCCESS &&
          pairing_cb.state == BT_BOND_STATE_BONDED &&
          pairing_cb.sdp_attempts < BTIF_DM_MAX_SDP_ATTEMPTS_AFTER_PAIRING) {
        if (pairing_cb.sdp_attempts) {
          log::warn("SDP failed after bonding re-attempting for {}",
                    ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
          pairing_cb.sdp_attempts++;
          if (IS_FLAG_ENABLED(force_bredr_for_sdp_retry)) {
            btif_dm_get_remote_services(bd_addr, BT_TRANSPORT_BR_EDR);
          } else {
            btif_dm_get_remote_services(bd_addr, BT_TRANSPORT_AUTO);
          }
        } else {
          log::warn("SDP triggered by someone failed when bonding");
        }
        return;
      }

      if ((bd_addr == pairing_cb.bd_addr ||
           bd_addr == pairing_cb.static_bdaddr)) {
        log::info("SDP finished for {}:", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        pairing_cb.sdp_over_classic =
            btif_dm_pairing_cb_t::ServiceDiscoveryState::FINISHED;
      }

      prop.type = BT_PROPERTY_UUIDS;
      prop.len = 0;
      if ((p_data->disc_res.result == BTA_SUCCESS) &&
          (p_data->disc_res.num_uuids > 0)) {
        log::info("New UUIDs for {}:", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        for (i = 0; i < p_data->disc_res.num_uuids; i++) {
          auto uuid = p_data->disc_res.p_uuid_list + i;
          if (btif_should_ignore_uuid(*uuid)) {
            continue;
          }
          log::info("index:{} uuid:{}", i, uuid->ToString());
          uuids.insert(*uuid);
        }

        Uuid existing_uuids[BT_MAX_NUM_UUIDS] = {};
        btif_get_existing_uuids(&bd_addr, existing_uuids);

        for (int i = 0; i < BT_MAX_NUM_UUIDS; i++) {
          Uuid uuid = existing_uuids[i];
          if (btif_should_ignore_uuid(uuid)) {
            continue;
          }
          if (btif_is_interesting_le_service(uuid)) {
            log::info("interesting le service {} insert", uuid.ToString());
            uuids.insert(uuid);
          }
        }
        for (auto& uuid : uuids) {
          auto uuid_128bit = uuid.To128BitBE();
          property_value.insert(property_value.end(), uuid_128bit.begin(),
                                uuid_128bit.end());
          if (uuid == UUID_A2DP_SINK) {
            a2dp_sink_capable = true;
          }
        }
        prop.val = (void*)property_value.data();
        prop.len = Uuid::kNumBytes128 * uuids.size();
      }

      bool skip_reporting_wait_for_le = false;
      /* If we are doing service discovery for device that just bonded, that is
       * capable of a2dp, and both sides can do LE Audio, and it haven't
       * finished GATT over LE yet, then wait for LE service discovery to finish
       * before before passing services to upper layers. */
      if (a2dp_sink_capable &&
          pairing_cb.gatt_over_le !=
              btif_dm_pairing_cb_t::ServiceDiscoveryState::FINISHED &&
          is_le_audio_capable_during_service_discovery(bd_addr)) {
        skip_reporting_wait_for_le = true;
      }

      /* onUuidChanged requires getBondedDevices to be populated.
      ** bond_state_changed needs to be sent prior to remote_device_property
      */
      size_t num_eir_uuids = 0U;
      Uuid uuid = {};
      if (pairing_cb.state == BT_BOND_STATE_BONDED && pairing_cb.sdp_attempts &&
          (p_data->disc_res.bd_addr == pairing_cb.bd_addr ||
           p_data->disc_res.bd_addr == pairing_cb.static_bdaddr)) {
        log::info("SDP search done for {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        pairing_cb.sdp_attempts = 0;

        // Send UUIDs discovered through EIR to Java to unblock pairing intent
        // when SDP failed
        if (p_data->disc_res.result != BTA_SUCCESS) {
          auto uuids_iter = eir_uuids_cache.find(bd_addr);
          if (uuids_iter != eir_uuids_cache.end()) {
            num_eir_uuids = uuids_iter->second.size();
            log::info("SDP failed, send {} EIR UUIDs to unblock bonding {}",
                      num_eir_uuids, ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
            for (auto eir_uuid : uuids_iter->second) {
              auto uuid_128bit = eir_uuid.To128BitBE();
              property_value.insert(property_value.end(), uuid_128bit.begin(),
                                    uuid_128bit.end());
            }
            eir_uuids_cache.erase(uuids_iter);
          }
          if (num_eir_uuids > 0) {
            prop.val = (void*)property_value.data();
            prop.len = num_eir_uuids * Uuid::kNumBytes128;
          } else {
            log::warn("SDP failed and we have no EIR UUIDs to report either");
            prop.val = &uuid;
            prop.len = Uuid::kNumBytes128;
          }
        }

        if (!skip_reporting_wait_for_le) {
          // Both SDP and bonding are done, clear pairing control block in case
          // it is not already cleared
          pairing_cb = {};
          log::debug("clearing btif pairing_cb");
        }
      }

      const tBTA_STATUS bta_status = p_data->disc_res.result;
      BTM_LogHistory(
          kBtmLogTagSdp, bd_addr, "Discovered services",
          base::StringPrintf("bta_status:%s sdp_uuids:%zu eir_uuids:%zu",
                             bta_status_text(bta_status).c_str(),
                             p_data->disc_res.num_uuids, num_eir_uuids));

      if (p_data->disc_res.num_uuids != 0 || num_eir_uuids != 0) {
        /* Also write this to the NVRAM */
        const bt_status_t ret =
            btif_storage_set_remote_device_property(&bd_addr, &prop);
        ASSERTC(ret == BT_STATUS_SUCCESS, "storing remote services failed",
                ret);

        if (skip_reporting_wait_for_le) {
          log::info(
              "Bonding LE Audio sink - must wait for le services discovery to "
              "pass all services to java {}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
          /* For LE Audio capable devices, we care more about passing GATT LE
           * services than about just finishing pairing. Service discovery
           * should be scheduled when LE pairing finishes, by call to
           * btif_dm_get_remote_services(bd_addr, BT_TRANSPORT_LE) */
          return;
        }

        /* Send the event to the BTIF */
        GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
            BT_STATUS_SUCCESS, bd_addr, 1, &prop);
      }
    } break;

    case BTA_DM_DISC_CMPL_EVT:
      /* fixme */
      break;

    case BTA_DM_SEARCH_CANCEL_CMPL_EVT:
      /* no-op */
      break;

    case BTA_DM_GATT_OVER_SDP_RES_EVT:
    case BTA_DM_GATT_OVER_LE_RES_EVT: {
      int num_properties = 0;
      bt_property_t prop[2];
      std::vector<uint8_t> property_value;
      std::set<Uuid> uuids;
      RawAddress& bd_addr = p_data->disc_ble_res.bd_addr;
      RawAddress static_addr_copy = pairing_cb.static_bdaddr;
      bool lea_supported =
          is_le_audio_capable_during_service_discovery(bd_addr);

      if (event == BTA_DM_GATT_OVER_LE_RES_EVT) {
        log::info("New GATT over LE UUIDs for {}:",
                  ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        BTM_LogHistory(kBtmLogTag, bd_addr,
                       "Discovered GATT services using LE transport");
        if (btif_is_gatt_service_discovery_post_pairing(bd_addr)) {
          pairing_cb.gatt_over_le =
              btif_dm_pairing_cb_t::ServiceDiscoveryState::FINISHED;

          if (pairing_cb.sdp_over_classic !=
              btif_dm_pairing_cb_t::ServiceDiscoveryState::SCHEDULED) {
            // Both SDP and bonding are either done, or not scheduled,
            // we are safe to clear the service discovery part of CB.
            log::debug("clearing pairing_cb");
            pairing_cb = {};
          }

          if (IS_FLAG_ENABLED(le_audio_fast_bond_params) && lea_supported) {
            /* LE Audio profile should relax parameters when it connects. If
             * profile is not enabled, relax parameters after timeout. */
            log::debug("Scheduling conn params unlock for {}",
                       ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
            do_in_main_thread_delayed(
                FROM_HERE,
                base::BindOnce(
                    [](RawAddress bd_addr) {
                      L2CA_LockBleConnParamsForProfileConnection(bd_addr,
                                                                 false);
                    },
                    bd_addr),
                std::chrono::seconds(15));
          }
        }
      } else {
        log::debug("New GATT over SDP UUIDs for {}:",
                   ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        BTM_LogHistory(kBtmLogTag, bd_addr,
                       "Discovered GATT services using SDP transport");
      }

      for (Uuid uuid : *p_data->disc_ble_res.services) {
        if (btif_is_interesting_le_service(uuid)) {
          if (btif_should_ignore_uuid(uuid)) {
            continue;
          }
          log::info("index:{} uuid:{}", static_cast<int>(uuids.size()),
                    uuid.ToString());
          uuids.insert(uuid);
        }
      }

      if (uuids.empty()) {
        log::info("No well known GATT services discovered");

        /* If services were returned as part of SDP discovery, we will
         * immediately send them with rest of SDP results in BTA_DM_DISC_RES_EVT
         */
        if (event == BTA_DM_GATT_OVER_SDP_RES_EVT) {
          return;
        }

        if (lea_supported) {
          if (bluetooth::common::init_flags::
                  sdp_return_classic_services_when_le_discovery_fails_is_enabled()) {
            log::info(
                "Will return Classic SDP results, if done, to unblock bonding");
          } else {
            // LEA device w/o this flag
            // TODO: we might want to remove bond or do some action on
            // half-discovered device
            log::warn("No GATT service found for the LE Audio device {}",
                      ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
            return;
          }
        } else {
          log::info("LE audio not supported, no need to report any UUIDs");
          return;
        }
      }

      Uuid existing_uuids[BT_MAX_NUM_UUIDS] = {};

      // Look up UUIDs using pseudo address (either RPA or static address)
      bt_status_t existing_lookup_result =
          btif_get_existing_uuids(&bd_addr, existing_uuids);

      if (existing_lookup_result != BT_STATUS_FAIL) {
        log::info("Got some existing UUIDs by address {}",
                  ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

        for (int i = 0; i < BT_MAX_NUM_UUIDS; i++) {
          Uuid uuid = existing_uuids[i];
          if (uuid.IsEmpty()) {
            continue;
          }
          uuids.insert(uuid);
        }
      }

      if (bd_addr != static_addr_copy) {
        // Look up UUID using static address, if different than sudo address
        existing_lookup_result =
            btif_get_existing_uuids(&static_addr_copy, existing_uuids);
        if (existing_lookup_result != BT_STATUS_FAIL) {
          log::info("Got some existing UUIDs by static address {}",
                    ADDRESS_TO_LOGGABLE_CSTR(static_addr_copy));
          for (int i = 0; i < BT_MAX_NUM_UUIDS; i++) {
            Uuid uuid = existing_uuids[i];
            if (uuid.IsEmpty()) {
              continue;
            }
            uuids.insert(uuid);
          }
        }
      }

      for (auto& uuid : uuids) {
        auto uuid_128bit = uuid.To128BitBE();
        property_value.insert(property_value.end(), uuid_128bit.begin(),
                              uuid_128bit.end());
      }

      prop[0].type = BT_PROPERTY_UUIDS;
      prop[0].val = (void*)property_value.data();
      prop[0].len = Uuid::kNumBytes128 * uuids.size();

      /* Also write this to the NVRAM */
      bt_status_t ret =
          btif_storage_set_remote_device_property(&bd_addr, &prop[0]);
      ASSERTC(ret == BT_STATUS_SUCCESS, "storing remote services failed", ret);
      num_properties++;

      /* Remote name update */
      if (strnlen((const char*)p_data->disc_ble_res.bd_name, BD_NAME_LEN)) {
        prop[1].type = BT_PROPERTY_BDNAME;
        prop[1].val = p_data->disc_ble_res.bd_name;
        prop[1].len = strnlen((char*)p_data->disc_ble_res.bd_name, BD_NAME_LEN);

        ret = btif_storage_set_remote_device_property(&bd_addr, &prop[1]);
        ASSERTC(ret == BT_STATUS_SUCCESS,
                "failed to save remote device property", ret);
        num_properties++;
      }

      /* If services were returned as part of SDP discovery, we will immediately
       * send them with rest of SDP results in BTA_DM_DISC_RES_EVT */
      if (event == BTA_DM_GATT_OVER_SDP_RES_EVT) {
        return;
      }

      /* Send the event to the BTIF */
      GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
          BT_STATUS_SUCCESS, bd_addr, num_properties, prop);
    } break;

    case BTA_DM_DID_RES_EVT: {
      bt_property_t prop_did;
      RawAddress& bd_addr = p_data->did_res.bd_addr;
      bt_vendor_product_info_t vp_info;

      vp_info.vendor_id_src = p_data->did_res.vendor_id_src;
      vp_info.vendor_id = p_data->did_res.vendor_id;
      vp_info.product_id = p_data->did_res.product_id;
      vp_info.version = p_data->did_res.version;

      prop_did.type = BT_PROPERTY_VENDOR_PRODUCT_INFO;
      prop_did.val = &vp_info;
      prop_did.len = sizeof(vp_info);

      bt_status_t ret =
          btif_storage_set_remote_device_property(&bd_addr, &prop_did);
      ASSERTC(ret == BT_STATUS_SUCCESS, "storing remote services failed", ret);

      /* Send the event to the BTIF */
      GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
          BT_STATUS_SUCCESS, bd_addr, 1, &prop_did);
    } break;

    case BTA_DM_NAME_READ_EVT: {
      if (IS_FLAG_ENABLED(rnr_present_during_service_discovery)) {
        const tBTA_DM_DISC_RES& disc_res = p_data->disc_res;
        if (disc_res.hci_status != HCI_SUCCESS) {
          log::warn("Received RNR event with bad status addr:{} hci_status:{}",
                    ADDRESS_TO_LOGGABLE_CSTR(disc_res.bd_addr),
                    hci_error_code_text(disc_res.hci_status));
          break;
        }
        if (disc_res.bd_name[0] == '\0') {
          log::warn("Received RNR event without valid name addr:{}",
                    ADDRESS_TO_LOGGABLE_CSTR(disc_res.bd_addr));
          break;
        }
        bt_property_t properties[] = {{
            .type = BT_PROPERTY_BDNAME,
            .len = (int)strlen((char*)disc_res.bd_name),
            .val = (void*)disc_res.bd_name,
        }};
        const bt_status_t status = btif_storage_set_remote_device_property(
            &disc_res.bd_addr, properties);
        ASSERT_LOG(status == BT_STATUS_SUCCESS,
                   "Failed to save remote device property status:%s",
                   bt_status_text(status).c_str());
        const size_t num_props = sizeof(properties) / sizeof(bt_property_t);
        GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
            status, disc_res.bd_addr, (int)num_props, properties);
        log::info(
            "Callback for read name event addr:{} name:{}",
            ADDRESS_TO_LOGGABLE_CSTR(disc_res.bd_addr),
            PRIVATE_NAME(reinterpret_cast<char const*>(disc_res.bd_name)));
      } else {
        log::info("Skipping name read event - called on bad callback.");
      }
    } break;

    default: {
      ASSERTC(0, "unhandled search services event", event);
    } break;
  }
}

static void btif_dm_update_allowlisted_media_players() {
  uint8_t i = 0, buf_len = 0;
  bt_property_t wlplayers_prop;
  list_t* wl_players = list_new(nullptr);
  if (!wl_players) {
    log::error("Unable to allocate space for allowlist players");
    return;
  }
  log::debug("btif_dm_update_allowlisted_media_players");

  wlplayers_prop.len = 0;
  if (!interop_get_allowlisted_media_players_list(wl_players)) {
    log::debug("Allowlisted media players not found");
    list_free(wl_players);
    return;
  }

  /*find the total number of bytes and allocate memory */
  for (list_node_t* node = list_begin(wl_players); node != list_end(wl_players);
       node = list_next(node)) {
    buf_len += (strlen((char*)list_node(node)) + 1);
  }
  char* players_list = (char*)osi_malloc(buf_len);
  for (list_node_t* node = list_begin(wl_players); node != list_end(wl_players);
       node = list_next(node)) {
    char* name;
    name = (char*)list_node(node);
    memcpy(&players_list[i], list_node(node), strlen(name) + 1);
    i += (strlen(name) + 1);
  }
  wlplayers_prop.type = BT_PROPERTY_WL_MEDIA_PLAYERS_LIST;
  wlplayers_prop.len = buf_len;
  wlplayers_prop.val = players_list;

  GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(
      BT_STATUS_SUCCESS, 1, &wlplayers_prop);
  list_free(wl_players);
}

void BTIF_dm_report_inquiry_status_change(tBTM_INQUIRY_STATE status) {
  btif_dm_inquiry_in_progress =
      (status == tBTM_INQUIRY_STATE::BTM_INQUIRY_STARTED);

  if (status == tBTM_INQUIRY_STATE::BTM_INQUIRY_STARTED) {
    GetInterfaceToProfiles()->events->invoke_discovery_state_changed_cb(
        BT_DISCOVERY_STARTED);
  } else if (status == tBTM_INQUIRY_STATE::BTM_INQUIRY_CANCELLED) {
    GetInterfaceToProfiles()->events->invoke_discovery_state_changed_cb(
        BT_DISCOVERY_STOPPED);
  }
}

void BTIF_dm_enable() {
  BD_NAME bdname;
  bt_status_t status;
  bt_property_t prop;
  prop.type = BT_PROPERTY_BDNAME;
  prop.len = BD_NAME_LEN;
  prop.val = (void*)bdname;

  status = btif_storage_get_adapter_property(&prop);
  if (status == BT_STATUS_SUCCESS) {
    /* A name exists in the storage. Make this the device name */
    BTA_DmSetDeviceName((const char*)prop.val);
  } else {
    /* Storage does not have a name yet.
     * Use the default name and write it to the chip
     */
    BTA_DmSetDeviceName(btif_get_default_local_name());
  }

  /* Enable or disable local privacy */
  bool ble_privacy_enabled =
      osi_property_get_bool(PROPERTY_BLE_PRIVACY_ENABLED, /*default=*/true);

  log::info("Local BLE Privacy enabled:{}", ble_privacy_enabled);
  BTA_DmBleConfigLocalPrivacy(ble_privacy_enabled);

  /* for each of the enabled services in the mask, trigger the profile
   * enable */
  tBTA_SERVICE_MASK service_mask = btif_get_enabled_services_mask();
  for (uint32_t i = 0; i <= BTA_MAX_SERVICE_ID; i++) {
    if (service_mask & (tBTA_SERVICE_MASK)(BTA_SERVICE_ID_TO_SERVICE_MASK(i))) {
      btif_in_execute_service_request(i, true);
    }
  }
  /* clear control blocks */
  pairing_cb = {};
  pairing_cb.bond_type = BOND_TYPE_PERSISTENT;

  // Enable address consolidation.
  btif_storage_load_le_devices();

  /* This function will also trigger the adapter_properties_cb
  ** and bonded_devices_info_cb
  */
  btif_storage_load_bonded_devices();
  bluetooth::bqr::EnableBtQualityReport(true);
  btif_dm_update_allowlisted_media_players();
  btif_enable_bluetooth_evt();
}

void BTIF_dm_disable() {
  /* for each of the enabled services in the mask, trigger the profile
   * disable */
  tBTA_SERVICE_MASK service_mask = btif_get_enabled_services_mask();
  for (uint32_t i = 0; i <= BTA_MAX_SERVICE_ID; i++) {
    if (service_mask & (tBTA_SERVICE_MASK)(BTA_SERVICE_ID_TO_SERVICE_MASK(i))) {
      btif_in_execute_service_request(i, false);
    }
  }
  bluetooth::bqr::EnableBtQualityReport(false);
  log::info("Stack device manager shutdown finished");
  future_ready(stack_manager_get_hack_future(), FUTURE_SUCCESS);
}

/*******************************************************************************
 *
 * Function         btif_dm_sec_evt
 *
 * Description      Executes security related events
 *
 * Returns          void
 *
 ******************************************************************************/
void btif_dm_sec_evt(tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data) {
  RawAddress bd_addr;

  log::verbose("ev:{}", dump_dm_event(event));

  switch (event) {
    case BTA_DM_PIN_REQ_EVT:
      btif_dm_pin_req_evt(&p_data->pin_req);
      break;

    case BTA_DM_AUTH_CMPL_EVT:
      btif_dm_auth_cmpl_evt(&p_data->auth_cmpl);
      break;

    case BTA_DM_BOND_CANCEL_CMPL_EVT:
      if (is_bonding_or_sdp()) {
        bd_addr = pairing_cb.bd_addr;
        btm_set_bond_type_dev(pairing_cb.bd_addr, BOND_TYPE_UNKNOWN);
        bond_state_changed((bt_status_t)p_data->bond_cancel_cmpl.result,
                           bd_addr, BT_BOND_STATE_NONE);
      }
      break;

    case BTA_DM_SP_CFM_REQ_EVT:
      btif_dm_ssp_cfm_req_evt(&p_data->cfm_req);
      break;
    case BTA_DM_SP_KEY_NOTIF_EVT:
      btif_dm_ssp_key_notif_evt(&p_data->key_notif);
      break;

    case BTA_DM_DEV_UNPAIRED_EVT:
      bd_addr = p_data->dev_unpair.bd_addr;
      btm_set_bond_type_dev(p_data->dev_unpair.bd_addr, BOND_TYPE_UNKNOWN);

      GetInterfaceToProfiles()->removeDeviceFromProfiles(bd_addr);
      btif_storage_remove_bonded_device(&bd_addr);
      bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_NONE);
      break;

    case BTA_DM_BLE_KEY_EVT:
      log::verbose("BTA_DM_BLE_KEY_EVT key_type=0x{:02x}",
                   p_data->ble_key.key_type);

      /* If this pairing is by-product of local initiated GATT client Read or
      Write,
      BTA would not have sent BTA_DM_BLE_SEC_REQ_EVT event and Bond state would
      not
      have setup properly. Setup pairing_cb and notify App about Bonding state
      now*/
      if (pairing_cb.state != BT_BOND_STATE_BONDING) {
        log::verbose("Bond state not sent to App so far.Notify the app now");
        bond_state_changed(BT_STATUS_SUCCESS, p_data->ble_key.bd_addr,
                           BT_BOND_STATE_BONDING);
      } else if (pairing_cb.bd_addr != p_data->ble_key.bd_addr) {
        log::error("BD mismatch discard BLE key_type={}",
                   p_data->ble_key.key_type);
        break;
      }

      switch (p_data->ble_key.key_type) {
        case BTM_LE_KEY_PENC:
          log::verbose("Rcv BTM_LE_KEY_PENC");
          pairing_cb.ble.is_penc_key_rcvd = true;
          pairing_cb.ble.penc_key = p_data->ble_key.p_key_value->penc_key;
          break;

        case BTM_LE_KEY_PID:
          log::verbose("Rcv BTM_LE_KEY_PID");
          pairing_cb.ble.is_pid_key_rcvd = true;
          pairing_cb.ble.pid_key = p_data->ble_key.p_key_value->pid_key;
          break;

        case BTM_LE_KEY_PCSRK:
          log::verbose("Rcv BTM_LE_KEY_PCSRK");
          pairing_cb.ble.is_pcsrk_key_rcvd = true;
          pairing_cb.ble.pcsrk_key = p_data->ble_key.p_key_value->pcsrk_key;
          break;

        case BTM_LE_KEY_LENC:
          log::verbose("Rcv BTM_LE_KEY_LENC");
          pairing_cb.ble.is_lenc_key_rcvd = true;
          pairing_cb.ble.lenc_key = p_data->ble_key.p_key_value->lenc_key;
          break;

        case BTM_LE_KEY_LCSRK:
          log::verbose("Rcv BTM_LE_KEY_LCSRK");
          pairing_cb.ble.is_lcsrk_key_rcvd = true;
          pairing_cb.ble.lcsrk_key = p_data->ble_key.p_key_value->lcsrk_key;
          break;

        case BTM_LE_KEY_LID:
          log::verbose("Rcv BTM_LE_KEY_LID");
          pairing_cb.ble.is_lidk_key_rcvd = true;
          break;

        default:
          log::error("unknown BLE key type (0x{:02x})",
                     p_data->ble_key.key_type);
          break;
      }
      break;
    case BTA_DM_BLE_CONSENT_REQ_EVT:
      log::verbose("BTA_DM_BLE_CONSENT_REQ_EVT");
      btif_dm_ble_sec_req_evt(&p_data->ble_req, true);
      break;
    case BTA_DM_BLE_SEC_REQ_EVT:
      log::verbose("BTA_DM_BLE_SEC_REQ_EVT");
      btif_dm_ble_sec_req_evt(&p_data->ble_req, false);
      break;
    case BTA_DM_BLE_PASSKEY_NOTIF_EVT:
      log::verbose("BTA_DM_BLE_PASSKEY_NOTIF_EVT");
      btif_dm_ble_key_notif_evt(&p_data->key_notif);
      break;
    case BTA_DM_BLE_PASSKEY_REQ_EVT:
      log::verbose("BTA_DM_BLE_PASSKEY_REQ_EVT");
      btif_dm_ble_passkey_req_evt(&p_data->pin_req);
      break;
    case BTA_DM_BLE_NC_REQ_EVT:
      log::verbose("BTA_DM_BLE_PASSKEY_REQ_EVT");
      btif_dm_ble_key_nc_req_evt(&p_data->key_notif);
      break;
    case BTA_DM_BLE_OOB_REQ_EVT:
      log::verbose("BTA_DM_BLE_OOB_REQ_EVT");
      btif_dm_ble_oob_req_evt(&p_data->rmt_oob);
      break;
    case BTA_DM_BLE_SC_OOB_REQ_EVT:
      log::verbose("BTA_DM_BLE_SC_OOB_REQ_EVT");
      btif_dm_ble_sc_oob_req_evt(&p_data->rmt_oob);
      break;
    case BTA_DM_BLE_SC_CR_LOC_OOB_EVT:
      log::verbose("BTA_DM_BLE_SC_CR_LOC_OOB_EVT");
      btif_dm_proc_loc_oob(BT_TRANSPORT_LE, true,
                           p_data->local_oob_data.local_oob_c,
                           p_data->local_oob_data.local_oob_r);
      break;

    case BTA_DM_BLE_LOCAL_IR_EVT:
      log::verbose("BTA_DM_BLE_LOCAL_IR_EVT");
      ble_local_key_cb.is_id_keys_rcvd = true;
      ble_local_key_cb.id_keys.irk = p_data->ble_id_keys.irk;
      ble_local_key_cb.id_keys.ir = p_data->ble_id_keys.ir;
      ble_local_key_cb.id_keys.dhk = p_data->ble_id_keys.dhk;
      btif_storage_add_ble_local_key(ble_local_key_cb.id_keys.irk,
                                     BTIF_DM_LE_LOCAL_KEY_IRK);
      btif_storage_add_ble_local_key(ble_local_key_cb.id_keys.ir,
                                     BTIF_DM_LE_LOCAL_KEY_IR);
      btif_storage_add_ble_local_key(ble_local_key_cb.id_keys.dhk,
                                     BTIF_DM_LE_LOCAL_KEY_DHK);
      break;
    case BTA_DM_BLE_LOCAL_ER_EVT:
      log::verbose("BTA_DM_BLE_LOCAL_ER_EVT");
      ble_local_key_cb.is_er_rcvd = true;
      ble_local_key_cb.er = p_data->ble_er;
      btif_storage_add_ble_local_key(ble_local_key_cb.er,
                                     BTIF_DM_LE_LOCAL_KEY_ER);
      break;

    case BTA_DM_BLE_AUTH_CMPL_EVT:
      log::verbose("BTA_DM_BLE_AUTH_CMPL_EVT");
      btif_dm_ble_auth_cmpl_evt(&p_data->auth_cmpl);
      break;

    case BTA_DM_LE_ADDR_ASSOC_EVT:
      GetInterfaceToProfiles()->events->invoke_le_address_associate_cb(
          p_data->proc_id_addr.pairing_bda, p_data->proc_id_addr.id_addr);
      break;

    case BTA_DM_SIRK_VERIFICATION_REQ_EVT:
      GetInterfaceToProfiles()->events->invoke_le_address_associate_cb(
          p_data->proc_id_addr.pairing_bda, p_data->proc_id_addr.id_addr);
      break;

    case BTA_DM_KEY_MISSING_EVT:
      GetInterfaceToProfiles()->events->invoke_key_missing_cb(
          p_data->key_missing.bd_addr);
      break;

    default:
      log::warn("unhandled event({})", event);
      break;
  }
}

/*******************************************************************************
 *
 * Function         bte_dm_acl_evt
 *
 * Description      BTIF handler for ACL up/down, identity address report events
 *
 * Returns          void
 *
 ******************************************************************************/
void btif_dm_acl_evt(tBTA_DM_ACL_EVT event, tBTA_DM_ACL* p_data) {
  RawAddress bd_addr;

  switch (event) {
    case BTA_DM_LINK_UP_EVT:
      bd_addr = p_data->link_up.bd_addr;
      log::verbose("BTA_DM_LINK_UP_EVT. Sending BT_ACL_STATE_CONNECTED");

      btif_update_remote_version_property(&bd_addr);

      GetInterfaceToProfiles()->events->invoke_acl_state_changed_cb(
          BT_STATUS_SUCCESS, bd_addr, BT_ACL_STATE_CONNECTED,
          (int)p_data->link_up.transport_link_type, HCI_SUCCESS,
          btm_is_acl_locally_initiated()
              ? bt_conn_direction_t::BT_CONN_DIRECTION_OUTGOING
              : bt_conn_direction_t::BT_CONN_DIRECTION_INCOMING,
          p_data->link_up.acl_handle);

      if (IS_FLAG_ENABLED(le_audio_fast_bond_params) &&
          p_data->link_up.transport_link_type == BT_TRANSPORT_LE &&
          pairing_cb.bd_addr == bd_addr &&
          is_device_le_audio_capable(bd_addr)) {
        L2CA_LockBleConnParamsForProfileConnection(bd_addr, true);
      }
      break;

    case BTA_DM_LINK_UP_FAILED_EVT:
      GetInterfaceToProfiles()->events->invoke_acl_state_changed_cb(
          BT_STATUS_FAIL, p_data->link_up_failed.bd_addr,
          BT_ACL_STATE_DISCONNECTED, p_data->link_up_failed.transport_link_type,
          p_data->link_up_failed.status,
          btm_is_acl_locally_initiated()
              ? bt_conn_direction_t::BT_CONN_DIRECTION_OUTGOING
              : bt_conn_direction_t::BT_CONN_DIRECTION_INCOMING,
          INVALID_ACL_HANDLE);
      break;

    case BTA_DM_LINK_DOWN_EVT: {
      bd_addr = p_data->link_down.bd_addr;
      btm_set_bond_type_dev(p_data->link_down.bd_addr, BOND_TYPE_UNKNOWN);
      GetInterfaceToProfiles()->onLinkDown(bd_addr);

      bt_conn_direction_t direction;
      switch (btm_get_acl_disc_reason_code()) {
        case HCI_ERR_PEER_USER:
        case HCI_ERR_REMOTE_LOW_RESOURCE:
        case HCI_ERR_REMOTE_POWER_OFF:
          direction = bt_conn_direction_t::BT_CONN_DIRECTION_INCOMING;
          break;
        case HCI_ERR_CONN_CAUSE_LOCAL_HOST:
        case HCI_ERR_HOST_REJECT_SECURITY:
          direction = bt_conn_direction_t::BT_CONN_DIRECTION_OUTGOING;
          break;
        default:
          direction = bt_conn_direction_t::BT_CONN_DIRECTION_UNKNOWN;
      }
      GetInterfaceToProfiles()->events->invoke_acl_state_changed_cb(
          BT_STATUS_SUCCESS, bd_addr, BT_ACL_STATE_DISCONNECTED,
          (int)p_data->link_down.transport_link_type,
          static_cast<bt_hci_error_code_t>(btm_get_acl_disc_reason_code()),
          direction, INVALID_ACL_HANDLE);
      log::debug(
          "Sent BT_ACL_STATE_DISCONNECTED upward as ACL link down event "
          "device:{} reason:{}",
          ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
          hci_reason_code_text(
              static_cast<tHCI_REASON>(btm_get_acl_disc_reason_code())));
    } break;
    case BTA_DM_LE_FEATURES_READ:
      btif_get_adapter_property(BT_PROPERTY_LOCAL_LE_FEATURES);
      break;


  default: {
    log::error("Unexpected tBTA_DM_ACL_EVT:{}", event);
    } break;

  }
}

/*******************************************************************************
 *
 * Function         bta_energy_info_cb
 *
 * Description      Switches context from BTE to BTIF for DM energy info event
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_energy_info_cb(tBTM_BLE_TX_TIME_MS tx_time,
                               tBTM_BLE_RX_TIME_MS rx_time,
                               tBTM_BLE_IDLE_TIME_MS idle_time,
                               tBTM_BLE_ENERGY_USED energy_used,
                               tBTM_CONTRL_STATE ctrl_state,
                               tBTA_STATUS status) {
  log::verbose(
      "energy_info_cb-Status:{},state={},tx_t={}, rx_t={}, "
      "idle_time={},used={}",
      status, ctrl_state, tx_time, rx_time, idle_time, energy_used);

  if (uid_set != nullptr) {
    bt_activity_energy_info energy_info;
    energy_info.status = status;
    energy_info.ctrl_state = ctrl_state;
    energy_info.rx_time = rx_time;
    energy_info.tx_time = tx_time;
    energy_info.idle_time = idle_time;
    energy_info.energy_used = energy_used;

    bt_uid_traffic_t* data = uid_set_read_and_clear(uid_set);
    GetInterfaceToProfiles()->events->invoke_energy_info_cb(energy_info, data);
  } else {
    log::warn("Energy info event dropped as module is inactive");
  }
}

/*****************************************************************************
 *
 *   btif api functions (no context switch)
 *
 ****************************************************************************/

/*******************************************************************************
 *
 * Function         btif_dm_start_discovery
 *
 * Description      Start device discovery/inquiry
 *
 ******************************************************************************/
void btif_dm_start_discovery(void) {
  log::verbose("start device discover/inquiry");

  BTM_LogHistory(
      kBtmLogTag, RawAddress::kEmpty, "Device discovery",
      base::StringPrintf("is_request_queued:%c",
                         bta_dm_is_search_request_queued() ? 'T' : 'F'));

  /* no race here because we're guaranteed to be in the main thread */
  if (bta_dm_is_search_request_queued()) {
    log::info("skipping start discovery because a request is queued");
    return;
  }

  /* Will be enabled to true once inquiry busy level has been received */
  btif_dm_inquiry_in_progress = false;
  /* find nearby devices */
  BTA_DmSearch(btif_dm_search_devices_evt);
  power_telemetry::GetInstance().LogScanStarted();
}

/*******************************************************************************
 *
 * Function         btif_dm_cancel_discovery
 *
 * Description      Cancels search
 *
 ******************************************************************************/
void btif_dm_cancel_discovery(void) {
  log::info("Cancel search");
  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Cancel discovery");

  BTA_DmSearchCancel();
}

bool btif_dm_pairing_is_busy() {
  return pairing_cb.state != BT_BOND_STATE_NONE;
}

/*******************************************************************************
 *
 * Function         btif_dm_create_bond
 *
 * Description      Initiate bonding with the specified device
 *
 ******************************************************************************/
void btif_dm_create_bond(const RawAddress bd_addr, int transport) {
  log::verbose("bd_addr={}, transport={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               transport);

  BTM_LogHistory(
      kBtmLogTag, bd_addr, "Create bond",
      base::StringPrintf("transport:%s", bt_transport_text(transport).c_str()));

  btif_stats_add_bond_event(bd_addr, BTIF_DM_FUNC_CREATE_BOND,
                            pairing_cb.state);

  pairing_cb.timeout_retries = NUM_TIMEOUT_RETRIES;
  btif_dm_cb_create_bond(bd_addr, transport);
}

/*******************************************************************************
 *
 * Function         btif_dm_create_bond_le
 *
 * Description      Initiate bonding with the specified device over le transport
 *
 ******************************************************************************/
void btif_dm_create_bond_le(const RawAddress bd_addr,
                            tBLE_ADDR_TYPE addr_type) {
  log::verbose("bd_addr={}, addr_type={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               addr_type);
  const tBLE_BD_ADDR ble_bd_addr{
      .type = addr_type,
      .bda = bd_addr,
  };
  BTM_LogHistory(
      kBtmLogTag, ble_bd_addr, "Create bond",
      base::StringPrintf("transport:%s",
                         bt_transport_text(BT_TRANSPORT_LE).c_str()));

  btif_stats_add_bond_event(bd_addr, BTIF_DM_FUNC_CREATE_BOND,
                            pairing_cb.state);

  pairing_cb.timeout_retries = NUM_TIMEOUT_RETRIES;
  btif_dm_cb_create_bond_le(bd_addr, addr_type);
}

/*******************************************************************************
 *
 * Function         btif_dm_create_bond_out_of_band
 *
 * Description      Initiate bonding with the specified device using out of band
 *                  data
 *
 ******************************************************************************/
void btif_dm_create_bond_out_of_band(const RawAddress bd_addr,
                                     tBT_TRANSPORT transport,
                                     const bt_oob_data_t p192_data,
                                     const bt_oob_data_t p256_data) {
  bt_oob_data_t empty_data;
  memset(&empty_data, 0, sizeof(empty_data));

  oob_cb.bdaddr = bd_addr;
  oob_cb.transport = transport;
  oob_cb.data_present = (int)BTM_OOB_NONE;
  if (memcmp(&p192_data, &empty_data, sizeof(p192_data)) != 0) {
    memcpy(&oob_cb.p192_data, &p192_data, sizeof(bt_oob_data_t));
    oob_cb.data_present = (int)BTM_OOB_PRESENT_192;
  }

  if (memcmp(&p256_data, &empty_data, sizeof(p256_data)) != 0) {
    memcpy(&oob_cb.p256_data, &p256_data, sizeof(bt_oob_data_t));
    if (oob_cb.data_present == (int)BTM_OOB_PRESENT_192) {
      oob_cb.data_present = (int)BTM_OOB_PRESENT_192_AND_256;
    } else {
      oob_cb.data_present = (int)BTM_OOB_PRESENT_256;
    }
  }

  BTM_LogHistory(
      kBtmLogTag, bd_addr, "Create bond",
      base::StringPrintf("transport:%s oob:%s",
                         bt_transport_text(transport).c_str(),
                         btm_oob_data_text(oob_cb.data_present).c_str()));

  uint8_t empty[] = {0, 0, 0, 0, 0, 0, 0};
  switch (transport) {
    case BT_TRANSPORT_BR_EDR:
      // TODO(182162589): Flesh out classic impl in legacy BTMSec
      // Nothing to do yet, but not an error

      // The controller only supports P192
      switch (oob_cb.data_present) {
        case BTM_OOB_PRESENT_192_AND_256:
          log::info("Have both P192 and  P256");
          [[fallthrough]];
        case BTM_OOB_PRESENT_192:
          log::info("Using P192");
          break;
        case BTM_OOB_PRESENT_256:
          log::info("Using P256");
          // TODO(181889116):
          // Upgrade to support p256 (for now we just ignore P256)
          // because the controllers do not yet support it.
          bond_state_changed(BT_STATUS_UNSUPPORTED, bd_addr,
                             BT_BOND_STATE_NONE);
          return;
        default:
          log::error("Invalid data present for controller:{}",
                     oob_cb.data_present);
          bond_state_changed(BT_STATUS_PARM_INVALID, bd_addr,
                             BT_BOND_STATE_NONE);
          return;
      }
      pairing_cb.is_local_initiated = true;
      log::error("Classic not implemented yet");
      bond_state_changed(BT_STATUS_UNSUPPORTED, bd_addr, BT_BOND_STATE_NONE);
      return;
    case BT_TRANSPORT_LE: {
      // Guess default RANDOM for address type for LE
      tBLE_ADDR_TYPE address_type = BLE_ADDR_RANDOM;
      log::info("Using LE Transport");
      switch (oob_cb.data_present) {
        case BTM_OOB_PRESENT_192_AND_256:
          log::info("Have both P192 and  P256");
          [[fallthrough]];
        // Always prefer 256 for LE
        case BTM_OOB_PRESENT_256:
          log::info("Using P256");
          // If we have an address, lets get the type
          if (memcmp(p256_data.address, empty, 7) != 0) {
            /* byte no 7 is address type in LE Bluetooth Address OOB data */
            address_type = static_cast<tBLE_ADDR_TYPE>(p256_data.address[6]);
          }
          break;
        case BTM_OOB_PRESENT_192:
          log::info("Using P192");
          // If we have an address, lets get the type
          if (memcmp(p192_data.address, empty, 7) != 0) {
            /* byte no 7 is address type in LE Bluetooth Address OOB data */
            address_type = static_cast<tBLE_ADDR_TYPE>(p192_data.address[6]);
          }
          break;
      }
      pairing_cb.is_local_initiated = true;
      get_btm_client_interface().security.BTM_SecAddBleDevice(
          bd_addr, BT_DEVICE_TYPE_BLE, address_type);
      BTA_DmBond(bd_addr, address_type, transport, BT_DEVICE_TYPE_BLE);
      break;
    }
    default:
      log::error("Invalid transport: {}", transport);
      bond_state_changed(BT_STATUS_PARM_INVALID, bd_addr, BT_BOND_STATE_NONE);
      return;
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_cancel_bond
 *
 * Description      Initiate bonding with the specified device
 *
 ******************************************************************************/
void btif_dm_cancel_bond(const RawAddress bd_addr) {
  log::verbose("bd_addr={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  BTM_LogHistory(kBtmLogTag, bd_addr, "Cancel bond");

  btif_stats_add_bond_event(bd_addr, BTIF_DM_FUNC_CANCEL_BOND,
                            pairing_cb.state);

  /* TODO:
  **  1. Restore scan modes
  **  2. special handling for HID devices
  */
  if (is_bonding_or_sdp()) {
    if (pairing_cb.is_ssp) {
      if (pairing_cb.is_le_only) {
        BTA_DmBleSecurityGrant(bd_addr, BTA_DM_SEC_PAIR_NOT_SPT);
      } else {
        BTA_DmConfirm(bd_addr, false);
        BTA_DmBondCancel(bd_addr);
        btif_storage_remove_bonded_device(&bd_addr);
      }
    } else {
      if (pairing_cb.is_le_only) {
        BTA_DmBondCancel(bd_addr);
      } else {
        BTA_DmPinReply(bd_addr, false, 0, NULL);
      }
      /* Cancel bonding, in case it is in ACL connection setup state */
      BTA_DmBondCancel(bd_addr);
    }
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_hh_open_failed
 *
 * Description      informs the upper layers if the HH have failed during
 *                  bonding
 *
 * Returns          none
 *
 ******************************************************************************/

void btif_dm_hh_open_failed(RawAddress* bdaddr) {
  if (pairing_cb.state == BT_BOND_STATE_BONDING &&
      *bdaddr == pairing_cb.bd_addr) {
    bond_state_changed(BT_STATUS_RMT_DEV_DOWN, *bdaddr, BT_BOND_STATE_NONE);
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_remove_bond
 *
 * Description      Removes bonding with the specified device
 *
 ******************************************************************************/

void btif_dm_remove_bond(const RawAddress bd_addr) {
  log::verbose("bd_addr={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  BTM_LogHistory(kBtmLogTag, bd_addr, "Remove bond");

  btif_stats_add_bond_event(bd_addr, BTIF_DM_FUNC_REMOVE_BOND,
                            pairing_cb.state);

  // special handling for HID devices
  // VUP needs to be sent if its a HID Device. The HID HOST module will check if
  // there is a valid hid connection with this bd_addr. If yes VUP will be
  // issued.
#if (BTA_HH_INCLUDED == TRUE)
  tAclLinkSpec link_spec;
  link_spec.addrt.bda = bd_addr;
  link_spec.transport = BT_TRANSPORT_AUTO;
  link_spec.addrt.type = BLE_ADDR_PUBLIC;

  if (GetInterfaceToProfiles()->profileSpecific_HACK->btif_hh_virtual_unplug(
          &link_spec) != BT_STATUS_SUCCESS)
#endif
  {
    log::debug("Removing HH device");
    BTA_DmRemoveDevice(bd_addr);
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_pin_reply
 *
 * Description      BT legacy pairing - PIN code reply
 *
 ******************************************************************************/

void btif_dm_pin_reply(const RawAddress bd_addr, uint8_t accept,
                       uint8_t pin_len, bt_pin_code_t pin_code) {
  log::verbose("accept={}", accept);

  if (pairing_cb.is_le_only) {
    int i;
    uint32_t passkey = 0;
    int multi[] = {100000, 10000, 1000, 100, 10, 1};
    for (i = 0; i < 6; i++) {
      passkey += (multi[i] * (pin_code.pin[i] - '0'));
    }
    // TODO:
    // FIXME: should we hide part of passkey here?
    log::verbose("btif_dm_pin_reply: passkey: {}", passkey);
    BTA_DmBlePasskeyReply(bd_addr, accept, passkey);

  } else {
    BTA_DmPinReply(bd_addr, accept, pin_len, pin_code.pin);
    if (accept) pairing_cb.pin_code_len = pin_len;
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_ssp_reply
 *
 * Description      BT SSP Reply - Just Works, Numeric Comparison & Passkey
 *                  Entry
 *
 ******************************************************************************/
void btif_dm_ssp_reply(const RawAddress bd_addr, bt_ssp_variant_t variant,
                       uint8_t accept) {
  log::verbose("accept={}", accept);
  BTM_LogHistory(
      kBtmLogTag, bd_addr, "Ssp reply",
      base::StringPrintf(
          "originator:%s variant:%d accept:%c le:%c numeric_comparison:%c",
          (pairing_cb.is_local_initiated) ? "local" : "remote", variant,
          (accept) ? 'Y' : 'N', (pairing_cb.is_le_only) ? 'T' : 'F',
          (pairing_cb.is_le_nc) ? 'T' : 'F'));
  if (pairing_cb.is_le_only) {
    if (pairing_cb.is_le_nc) {
      BTA_DmBleConfirmReply(bd_addr, accept);
    } else {
      if (accept)
        BTA_DmBleSecurityGrant(bd_addr, BTA_DM_SEC_GRANTED);
      else
        BTA_DmBleSecurityGrant(bd_addr, BTA_DM_SEC_PAIR_NOT_SPT);
    }
  } else {
    BTA_DmConfirm(bd_addr, accept);
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_get_local_class_of_device
 *
 * Description      Reads the system property configured class of device
 *
 * Returns          A DEV_CLASS containing the current class of device.
 *                  If no value is present, or the value is malformed
 *                  the default kEmpty value will be used
 *
 ******************************************************************************/
DEV_CLASS btif_dm_get_local_class_of_device() {
  /* A class of device is a {SERVICE_CLASS, MAJOR_CLASS, MINOR_CLASS}
   *
   * The input is expected to be a string of the following format:
   * <decimal number>,<decimal number>,<decimal number>
   *
   * For example, "90,2,12" (Hex: 0x5A, 0x2, 0xC)
   *
   * Notice there is always two commas and no spaces.
   */
  char prop_cod[PROPERTY_VALUE_MAX];
  osi_property_get(PROPERTY_CLASS_OF_DEVICE, prop_cod, "");

  // If the property is empty, use the default
  if (prop_cod[0] == '\0') {
    log::error("COD property is empty");
    return kDevClassUnclassified;
  }

  // Start reading the contents of the property string. If at any point anything
  // is malformed, use the default.
  DEV_CLASS temp_device_class;
  int i = 0;
  int j = 0;
  for (;;) {
    // Build a string of all the chars until the next comma, null, or end of the
    // buffer is reached. If any char is not a digit, then return the default.
    std::string value;
    while (i < PROPERTY_VALUE_MAX && prop_cod[i] != ',' &&
           prop_cod[i] != '\0') {
      char c = prop_cod[i++];
      if (!std::isdigit(c)) {
        log::error("COD malformed, '{:c}' is a non-digit", c);
        return kDevClassUnclassified;
      }
      value += c;
    }

    // If we hit the end and it wasn't null terminated then return the default
    if (i == PROPERTY_VALUE_MAX && prop_cod[PROPERTY_VALUE_MAX - 1] != '\0') {
      log::error("COD malformed, value was truncated");
      return kDevClassUnclassified;
    }

    // Each number in the list must be one byte, meaning 0 (0x00) -> 255 (0xFF)
    if (value.size() > 3 || value.size() == 0) {
      log::error("COD malformed, '{}' must be between [0, 255]", value);
      return kDevClassUnclassified;
    }

    // Grab the value. If it's too large, then return the default
    uint32_t uint32_val = static_cast<uint32_t>(std::stoul(value.c_str()));
    if (uint32_val > 0xFF) {
      log::error("COD malformed, '{}' must be between [0, 255]", value);
      return kDevClassUnclassified;
    }

    // Otherwise, it's safe to use
    temp_device_class[j++] = uint32_val;

    // If we've reached 3 numbers then make sure we're at a null terminator
    if (j >= 3) {
      if (prop_cod[i] != '\0') {
        log::error("COD malformed, more than three numbers");
        return kDevClassUnclassified;
      }
      break;
    }

    // If we're at a null terminator then we're done
    if (prop_cod[i] == '\0') {
      break;
    }

    // Otherwise, skip over the comma
    ++i;
  }

  // We must have read exactly 3 numbers
  DEV_CLASS device_class = kDevClassUnclassified;
  if (j == 3) {
    device_class[0] = temp_device_class[0];
    device_class[1] = temp_device_class[1];
    device_class[2] = temp_device_class[2];
  } else {
    log::error("COD malformed, fewer than three numbers");
  }

  log::debug(
      "Using class of device '0x{:x}, 0x{:x}, 0x{:x}' from CoD system property",
      device_class[0], device_class[1], device_class[2]);

#ifdef __ANDROID__
  // Per BAP 1.0.1, 8.2.3. Device discovery, the stack needs to set Class of
  // Device (CoD) field Major Service Class bit 14 to 0b1 when Unicast Server,
  // Unicast Client, Broadcast Source, Broadcast Sink, Scan Delegator, or
  // Broadcast Assistant is supported on this device
  if (android::sysprop::BluetoothProperties::isProfileBapUnicastClientEnabled()
          .value_or(false) ||
      android::sysprop::BluetoothProperties::
          isProfileBapBroadcastAssistEnabled()
              .value_or(false) ||
      android::sysprop::BluetoothProperties::
          isProfileBapBroadcastSourceEnabled()
              .value_or(false)) {
    device_class[1] |= 0x01 << 6;
  } else {
    device_class[1] &= ~(0x01 << 6);
  }
  log::debug(
      "Check LE audio enabled status, update class of device to '0x{:x}, "
      "0x{:x}, 0x{:x}'",
      device_class[0], device_class[1], device_class[2]);
#endif
  return device_class;
}

/*******************************************************************************
 *
 * Function         btif_dm_get_adapter_property
 *
 * Description     Queries the BTA for the adapter property
 *
 * Returns          bt_status_t
 *
 ******************************************************************************/
bt_status_t btif_dm_get_adapter_property(bt_property_t* prop) {
  log::verbose("type=0x{:x}", prop->type);
  switch (prop->type) {
    case BT_PROPERTY_BDNAME: {
      bt_bdname_t* bd_name = (bt_bdname_t*)prop->val;
      strncpy((char*)bd_name->name, (char*)btif_get_default_local_name(),
              sizeof(bd_name->name) - 1);
      bd_name->name[sizeof(bd_name->name) - 1] = 0;
      prop->len = strlen((char*)bd_name->name);
    } break;

    case BT_PROPERTY_ADAPTER_SCAN_MODE: {
      /* if the storage does not have it. Most likely app never set it. Default
       * is NONE */
      bt_scan_mode_t* mode = (bt_scan_mode_t*)prop->val;
      *mode = BT_SCAN_MODE_NONE;
      prop->len = sizeof(bt_scan_mode_t);
    } break;

    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT: {
      uint32_t* tmt = (uint32_t*)prop->val;
      *tmt = 120; /* default to 120s, if not found in NV */
      prop->len = sizeof(uint32_t);
    } break;

      // While fetching IO_CAP* values for the local device, we maintain
      // backward compatibility by using the value from #define macros
      // BTM_LOCAL_IO_CAPS, BTM_LOCAL_IO_CAPS_BLE if the values have never been
      // explicitly set.

    case BT_PROPERTY_LOCAL_IO_CAPS: {
      *(bt_io_cap_t*)prop->val = (bt_io_cap_t)BTM_LOCAL_IO_CAPS;
      prop->len = sizeof(bt_io_cap_t);
    } break;

    default:
      prop->len = 0;
      return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_dm_get_remote_services
 *
 * Description      Start SDP to get remote services by transport
 *
 * Returns          bt_status_t
 *
 ******************************************************************************/
void btif_dm_get_remote_services(RawAddress remote_addr, const int transport) {
  log::verbose("transport={}, remote_addr={}", bt_transport_text(transport),
               ADDRESS_TO_LOGGABLE_CSTR(remote_addr));

  BTM_LogHistory(
      kBtmLogTag, remote_addr, "Service discovery",
      base::StringPrintf("transport:%s", bt_transport_text(transport).c_str()));

  BTA_DmDiscover(remote_addr, btif_dm_search_services_evt, transport);
}

void btif_dm_enable_service(tBTA_SERVICE_ID service_id, bool enable) {
  bt_status_t status = btif_in_execute_service_request(service_id, enable);
  if (status == BT_STATUS_SUCCESS) {
    bt_property_t property;
    Uuid local_uuids[BT_MAX_NUM_UUIDS];

    /* Now send the UUID_PROPERTY_CHANGED event to the upper layer */
    BTIF_STORAGE_FILL_PROPERTY(&property, BT_PROPERTY_UUIDS,
                               sizeof(local_uuids), local_uuids);
    btif_storage_get_adapter_property(&property);
    GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(
        BT_STATUS_SUCCESS, 1, &property);
  }
  return;
}

void btif_dm_proc_io_req(tBTM_AUTH_REQ* p_auth_req, bool is_orig) {
  uint8_t yes_no_bit = BTA_AUTH_SP_YES & *p_auth_req;
  /* if local initiated:
  **      1. set DD + MITM
  ** if remote initiated:
  **      1. Copy over the auth_req from peer's io_rsp
  **      2. Set the MITM if peer has it set or if peer has DisplayYesNo
  *(iPhone)
  ** as a fallback set MITM+GB if peer had MITM set
  */

  log::verbose("original p_auth_req={}", *p_auth_req);
  if (pairing_cb.is_local_initiated) {
    /* if initing/responding to a dedicated bonding, use dedicate bonding bit */
    *p_auth_req = BTA_AUTH_DD_BOND | BTA_AUTH_SP_YES;
  } else if (!is_orig) {
    /* peer initiated paring. They probably know what they want.
    ** Copy the mitm from peer device.
    */
    log::debug("peer initiated, setting p_auth_req to peer's: {}",
               pairing_cb.auth_req);
    *p_auth_req = (pairing_cb.auth_req & BTA_AUTH_BONDS);

    /* copy over the MITM bit as well. In addition if the peer has DisplayYesNo,
     * force MITM */
    if ((yes_no_bit) || (pairing_cb.io_cap & BTM_IO_CAP_IO))
      *p_auth_req |= BTA_AUTH_SP_YES;
  } else if (yes_no_bit) {
    /* set the general bonding bit for stored device */
    *p_auth_req = BTA_AUTH_GEN_BOND | yes_no_bit;
  }
  log::verbose("updated p_auth_req={}", *p_auth_req);
}

void btif_dm_proc_io_rsp(const RawAddress& /* bd_addr */, tBTM_IO_CAP io_cap,
                         tBTM_OOB_DATA /* oob_data */, tBTM_AUTH_REQ auth_req) {
  if (auth_req & BTA_AUTH_BONDS) {
    log::debug("auth_req:{}", auth_req);
    pairing_cb.auth_req = auth_req;
    pairing_cb.io_cap = io_cap;
  }
}

void btif_dm_set_oob_for_io_req(tBTM_OOB_DATA* p_has_oob_data) {
  if (is_empty_128bit(oob_cb.p192_data.c)) {
    *p_has_oob_data = false;
  } else {
    *p_has_oob_data = true;
  }
  log::verbose("*p_has_oob_data={}", *p_has_oob_data);
}

void btif_dm_set_oob_for_le_io_req(const RawAddress& bd_addr,
                                   tBTM_OOB_DATA* p_has_oob_data,
                                   tBTM_LE_AUTH_REQ* p_auth_req) {
  switch (oob_cb.data_present) {
    case BTM_OOB_PRESENT_192_AND_256:
      log::info("Have both P192 and  P256");
      [[fallthrough]];
    // Always prefer 256 for LE
    case BTM_OOB_PRESENT_256:
      log::info("Using P256");
      if (!is_empty_128bit(oob_cb.p256_data.c) &&
          !is_empty_128bit(oob_cb.p256_data.r)) {
        /* make sure OOB data is for this particular device */
        if (bd_addr == oob_cb.bdaddr) {
          *p_auth_req = ((*p_auth_req) | BTM_LE_AUTH_REQ_SC_ONLY);
          *p_has_oob_data = true;
        } else {
          *p_has_oob_data = false;
          log::warn("P256-1: Remote address didn't match OOB data address");
        }
      } else if (!is_empty_128bit(oob_cb.p256_data.sm_tk)) {
        /* We have security manager TK */

        /* make sure OOB data is for this particular device */
        if (bd_addr == oob_cb.bdaddr) {
          // When using OOB with TK, SC Secure Connections bit must be disabled.
          tBTM_LE_AUTH_REQ mask = ~BTM_LE_AUTH_REQ_SC_ONLY;
          *p_auth_req = ((*p_auth_req) & mask);
          *p_has_oob_data = true;
        } else {
          *p_has_oob_data = false;
          log::warn("P256-2: Remote address didn't match OOB data address");
        }
      } else {
        *p_has_oob_data = false;
      }
      break;
    case BTM_OOB_PRESENT_192:
      log::info("Using P192");
      if (!is_empty_128bit(oob_cb.p192_data.c) &&
          !is_empty_128bit(oob_cb.p192_data.r)) {
        /* make sure OOB data is for this particular device */
        if (bd_addr == oob_cb.bdaddr) {
          *p_auth_req = ((*p_auth_req) | BTM_LE_AUTH_REQ_SC_ONLY);
          *p_has_oob_data = true;
        } else {
          *p_has_oob_data = false;
          log::warn("P192-1: Remote address didn't match OOB data address");
        }
      } else if (!is_empty_128bit(oob_cb.p192_data.sm_tk)) {
        /* We have security manager TK */

        /* make sure OOB data is for this particular device */
        if (bd_addr == oob_cb.bdaddr) {
          // When using OOB with TK, SC Secure Connections bit must be disabled.
          tBTM_LE_AUTH_REQ mask = ~BTM_LE_AUTH_REQ_SC_ONLY;
          *p_auth_req = ((*p_auth_req) & mask);
          *p_has_oob_data = true;
        } else {
          *p_has_oob_data = false;
          log::warn("P192-2: Remote address didn't match OOB data address");
        }
      } else {
        *p_has_oob_data = false;
      }
      break;
  }
  log::verbose("*p_has_oob_data={}", *p_has_oob_data);
}

void btif_dm_load_local_oob(void) {
  char prop_oob[PROPERTY_VALUE_MAX];
  osi_property_get("service.brcm.bt.oob", prop_oob, "3");
  log::verbose("prop_oob = {}", prop_oob);
  if (prop_oob[0] != '3') {
    if (is_empty_128bit(oob_cb.p192_data.c)) {
      log::verbose("read OOB, call BTA_DmLocalOob()");
      BTA_DmLocalOob();
    }
  }
}

static bool waiting_on_oob_advertiser_start = false;
static std::optional<uint8_t> oob_advertiser_id_;
static void stop_oob_advertiser() {
  // For chasing an advertising bug b/237023051
  log::debug("oob_advertiser_id: {}", oob_advertiser_id_.value());
  auto advertiser = bluetooth::shim::get_ble_advertiser_instance();
  advertiser->Unregister(oob_advertiser_id_.value());
  oob_advertiser_id_ = {};
}

/*******************************************************************************
 *
 * Function         btif_dm_generate_local_oob_data
 *
 * Description      Initiate oob data fetch from controller
 *
 * Parameters       transport; Classic or LE
 *
 ******************************************************************************/
void btif_dm_generate_local_oob_data(tBT_TRANSPORT transport) {
  log::debug("Transport {}", bt_transport_text(transport));
  if (transport == BT_TRANSPORT_BR_EDR) {
    BTM_ReadLocalOobData();
  } else if (transport == BT_TRANSPORT_LE) {
    // Call create data first, so we don't have to hold on to the address for
    // the state machine lifecycle.  Rather, lets create the data, then start
    // advertising then request the address.
    if (!waiting_on_oob_advertiser_start) {
      // For chasing an advertising bug b/237023051
      log::debug("oob_advertiser_id: {}", oob_advertiser_id_.value_or(255));
      if (oob_advertiser_id_.has_value()) {
        stop_oob_advertiser();
      }
      waiting_on_oob_advertiser_start = true;
      if (!SMP_CrLocScOobData()) {
        waiting_on_oob_advertiser_start = false;
        GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
            transport, false, Octet16{}, Octet16{}, RawAddress{}, 0x00);
      }
    } else {
      GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
          transport, false, Octet16{}, Octet16{}, RawAddress{}, 0x00);
    }
  }
}

// Step Four: CallBack from Step Three
static void get_address_callback(tBT_TRANSPORT transport, bool is_valid,
                                 const Octet16& c, const Octet16& r,
                                 uint8_t address_type, RawAddress address) {
  GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
      transport, is_valid, c, r, address, address_type);
  waiting_on_oob_advertiser_start = false;
}

// Step Three: CallBack from Step Two, advertise and get address
static void start_advertising_callback(uint8_t id, tBT_TRANSPORT transport,
                                       bool is_valid, const Octet16& c,
                                       const Octet16& r, tBTM_STATUS status) {
  if (status != 0) {
    log::info("OOB get advertiser ID failed with status {}", status);
    GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
        transport, false, c, r, RawAddress{}, 0x00);
    SMP_ClearLocScOobData();
    waiting_on_oob_advertiser_start = false;
    oob_advertiser_id_ = {};
    return;
  }
  log::debug("OOB advertiser with id {}", id);
  auto advertiser = bluetooth::shim::get_ble_advertiser_instance();
  advertiser->GetOwnAddress(
      id, base::Bind(&get_address_callback, transport, is_valid, c, r));
}

static void timeout_cb(uint8_t id, tBTM_STATUS status) {
  log::info("OOB advertiser with id {} timed out with status {}", id, status);
  auto advertiser = bluetooth::shim::get_ble_advertiser_instance();
  advertiser->Unregister(id);
  SMP_ClearLocScOobData();
  waiting_on_oob_advertiser_start = false;
  oob_advertiser_id_ = {};
}

// Step Two: CallBack from Step One, advertise and get address
static void id_status_callback(tBT_TRANSPORT transport, bool is_valid,
                               const Octet16& c, const Octet16& r, uint8_t id,
                               tBTM_STATUS status) {
  if (status != 0) {
    log::info("OOB get advertiser ID failed with status {}", status);
    GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
        transport, false, c, r, RawAddress{}, 0x00);
    SMP_ClearLocScOobData();
    waiting_on_oob_advertiser_start = false;
    oob_advertiser_id_ = {};
    return;
  }

  oob_advertiser_id_ = id;
  log::info("oob_advertiser_id: {}", id);

  auto advertiser = bluetooth::shim::get_ble_advertiser_instance();
  AdvertiseParameters parameters{};
  parameters.advertising_event_properties =
      0x0045 /* connectable, discoverable, tx power */;
  parameters.min_interval = 0xa0;   // 100 ms
  parameters.max_interval = 0x500;  // 800 ms
  parameters.channel_map = 0x7;     // Use all the channels
  parameters.tx_power = 0;          // 0 dBm
  parameters.primary_advertising_phy = 1;
  parameters.secondary_advertising_phy = 2;
  parameters.scan_request_notification_enable = 0;
  parameters.own_address_type = BLE_ADDR_RANDOM;

  std::vector<uint8_t> advertisement{0x02, 0x01 /* Flags */,
                                     0x02 /* Connectable */};
  std::vector<uint8_t> scan_data{};

  advertiser->StartAdvertising(
      id,
      base::Bind(&start_advertising_callback, id, transport, is_valid, c, r),
      parameters, advertisement, scan_data, 120 /* timeout_s */,
      base::Bind(&timeout_cb, id));
}

// Step One: Start the advertiser
static void start_oob_advertiser(tBT_TRANSPORT transport, bool is_valid,
                                 const Octet16& c, const Octet16& r) {
  auto advertiser = bluetooth::shim::get_ble_advertiser_instance();
  advertiser->RegisterAdvertiser(
      base::Bind(&id_status_callback, transport, is_valid, c, r));
}

void btif_dm_proc_loc_oob(tBT_TRANSPORT transport, bool is_valid,
                          const Octet16& c, const Octet16& r) {
  // is_valid is important for deciding which OobDataCallback function to use
  if (!is_valid) {
    GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
        transport, false, c, r, RawAddress{}, 0x00);
    waiting_on_oob_advertiser_start = false;
    return;
  }
  if (transport == BT_TRANSPORT_LE) {
    // Now that we have the data, lets start advertising and get the address.
    start_oob_advertiser(transport, is_valid, c, r);
  } else {
    GetInterfaceToProfiles()->events->invoke_oob_data_request_cb(
        transport, is_valid, c, r, *controller_get_interface()->get_address(),
        0x00);
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_get_smp_config
 *
 * Description      Retrieve the SMP pairing options from the bt_stack.conf
 *                  file. To provide specific pairing options for the host
 *                  add a node with label "SmpOptions" to the config file
 *                  and assign it a comma separated list of 5 values in the
 *                  format: auth, io, ikey, rkey, ksize, oob
 *                  eg: PTS_SmpOptions=0xD,0x4,0xf,0xf,0x10
 *
 * Parameters:      tBTE_APPL_CFG*: pointer to struct defining pairing options
 *
 * Returns          true if the options were successfully read, else false
 *
 ******************************************************************************/
bool btif_dm_get_smp_config(tBTE_APPL_CFG* p_cfg) {
  const std::string* recv = stack_config_get_interface()->get_pts_smp_options();
  if (!recv) {
    log::warn("SMP pairing options not found in stack configuration");
    return false;
  }

  char conf[64];
  char* pch;
  char* endptr;

  strncpy(conf, recv->c_str(), 64);
  conf[63] = 0;  // null terminate

  pch = strtok(conf, ",");
  if (pch != NULL)
    p_cfg->ble_auth_req = (uint8_t)strtoul(pch, &endptr, 16);
  else
    return false;

  pch = strtok(NULL, ",");
  if (pch != NULL)
    p_cfg->ble_io_cap = (uint8_t)strtoul(pch, &endptr, 16);
  else
    return false;

  pch = strtok(NULL, ",");
  if (pch != NULL)
    p_cfg->ble_init_key = (uint8_t)strtoul(pch, &endptr, 16);
  else
    return false;

  pch = strtok(NULL, ",");
  if (pch != NULL)
    p_cfg->ble_resp_key = (uint8_t)strtoul(pch, &endptr, 16);
  else
    return false;

  pch = strtok(NULL, ",");
  if (pch != NULL)
    p_cfg->ble_max_key_size = (uint8_t)strtoul(pch, &endptr, 16);
  else
    return false;

  return true;
}

bool btif_dm_proc_rmt_oob(const RawAddress& bd_addr, Octet16* p_c,
                          Octet16* p_r) {
  const char* path_a = "/data/misc/bluedroid/LOCAL/a.key";
  const char* path_b = "/data/misc/bluedroid/LOCAL/b.key";
  const char* path = NULL;
  char prop_oob[PROPERTY_VALUE_MAX];
  osi_property_get("service.brcm.bt.oob", prop_oob, "3");
  log::debug("prop_oob = {}", prop_oob);
  if (prop_oob[0] == '1')
    path = path_b;
  else if (prop_oob[0] == '2')
    path = path_a;
  if (!path) {
    log::debug("can't open path!");
    return false;
  }

  FILE* fp = fopen(path, "rb");
  if (fp == NULL) {
    log::debug("failed to read OOB keys from {}", path);
    return false;
  }

  log::verbose("read OOB data from {}", path);
  (void)fread(p_c->data(), 1, OCTET16_LEN, fp);
  (void)fread(p_r->data(), 1, OCTET16_LEN, fp);
  fclose(fp);

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  return true;
}

static void btif_dm_ble_key_notif_evt(tBTA_DM_SP_KEY_NOTIF* p_ssp_key_notif) {
  RawAddress bd_addr;
  bt_bdname_t bd_name;
  uint32_t cod;
  int dev_type;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_ssp_key_notif->bd_addr));

  /* Remote name update */
  if (!btif_get_device_type(p_ssp_key_notif->bd_addr, &dev_type)) {
    dev_type = BT_DEVICE_TYPE_BLE;
  }
  btif_dm_update_ble_remote_properties(p_ssp_key_notif->bd_addr,
                                       p_ssp_key_notif->bd_name, kDevClassEmpty,
                                       (tBT_DEVICE_TYPE)dev_type);
  bd_addr = p_ssp_key_notif->bd_addr;
  memcpy(bd_name.name, p_ssp_key_notif->bd_name, BD_NAME_LEN);
  bd_name.name[BD_NAME_LEN] = '\0';

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  pairing_cb.is_ssp = false;
  cod = COD_UNCLASSIFIED;

  BTM_LogHistory(
      kBtmLogTagCallback, bd_addr, "Ssp request",
      base::StringPrintf("name:'%s' passkey:%u", PRIVATE_NAME(bd_name.name),
                         p_ssp_key_notif->passkey));

  GetInterfaceToProfiles()->events->invoke_ssp_request_cb(
      bd_addr, bd_name, cod, BT_SSP_VARIANT_PASSKEY_NOTIFICATION,
      p_ssp_key_notif->passkey);
}

static bool btif_dm_ble_is_temp_pairing(RawAddress& bd_addr, bool ctkd) {
  if (btm_get_bond_type_dev(bd_addr) == BOND_TYPE_TEMPORARY) {
    if (!IS_FLAG_ENABLED(ignore_bond_type_for_le)) {
      return true;
    }

    return ctkd;
  }

  return false;
}

/*******************************************************************************
 *
 * Function         btif_dm_ble_auth_cmpl_evt
 *
 * Description      Executes authentication complete event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_ble_auth_cmpl_evt(tBTA_DM_AUTH_CMPL* p_auth_cmpl) {
  /* Save link key, if not temporary */
  bt_status_t status = BT_STATUS_FAIL;
  bt_bond_state_t state = BT_BOND_STATE_NONE;

  RawAddress bd_addr = p_auth_cmpl->bd_addr;

  /* Clear OOB data */
  memset(&oob_cb, 0, sizeof(oob_cb));

  if ((p_auth_cmpl->success) && (p_auth_cmpl->key_present)) {
    /* store keys */
  }
  if (p_auth_cmpl->success) {
    status = BT_STATUS_SUCCESS;
    state = BT_BOND_STATE_BONDED;
    tBLE_ADDR_TYPE addr_type;

    if (btif_storage_get_remote_addr_type(&bd_addr, &addr_type) !=
        BT_STATUS_SUCCESS)
      btif_storage_set_remote_addr_type(&bd_addr, p_auth_cmpl->addr_type);

    /* Test for temporary bonding */
    if (btif_dm_ble_is_temp_pairing(bd_addr, p_auth_cmpl->is_ctkd)) {
      log::debug("sending BT_BOND_STATE_NONE for Temp pairing");
      btif_storage_remove_bonded_device(&bd_addr);
      state = BT_BOND_STATE_NONE;
    } else {
      btif_dm_save_ble_bonding_keys(bd_addr);

      if (pairing_cb.gatt_over_le ==
          btif_dm_pairing_cb_t::ServiceDiscoveryState::NOT_STARTED) {
        log::info("scheduling GATT discovery over LE for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
        pairing_cb.gatt_over_le =
            btif_dm_pairing_cb_t::ServiceDiscoveryState::SCHEDULED;
        btif_dm_get_remote_services(bd_addr, BT_TRANSPORT_LE);
      } else {
        log::info(
            "skipping GATT discovery over LE - was already scheduled or "
            "finished for {}, state: {}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr), pairing_cb.gatt_over_le);
      }
    }
  } else {
    /* Map the HCI fail reason  to  bt status  */
    // TODO This is not a proper use of the type
    uint8_t fail_reason = static_cast<uint8_t>(p_auth_cmpl->fail_reason);
    log::error("LE authentication for {} failed with reason {}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), p_auth_cmpl->fail_reason);
    switch (fail_reason) {
      case BTA_DM_AUTH_SMP_PAIR_AUTH_FAIL:
      case BTA_DM_AUTH_SMP_CONFIRM_VALUE_FAIL:
      case BTA_DM_AUTH_SMP_UNKNOWN_ERR:
        btif_dm_remove_ble_bonding_keys();
        status = BT_STATUS_AUTH_FAILURE;
        break;

      case BTA_DM_AUTH_SMP_CONN_TOUT: {
        if (!p_auth_cmpl->is_ctkd && btm_sec_is_a_bonded_dev(bd_addr)) {
          log::warn(
              "Bonded device addr={}, timed out - will not remove the keys",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
          // Don't send state change to upper layers - otherwise Java think we
          // unbonded, and will disconnect HID profile.
          return;
        }
        log::info(
            "Removing ble bonding keys on SMP_CONN_TOUT during crosskey: {}",
            p_auth_cmpl->is_ctkd);
        btif_dm_remove_ble_bonding_keys();
        status = BT_STATUS_AUTH_FAILURE;
        break;
      }
      case BTA_DM_AUTH_SMP_PAIR_NOT_SUPPORT:
        status = BT_STATUS_AUTH_REJECTED;
        break;
      default:
        btif_dm_remove_ble_bonding_keys();
        status = BT_STATUS_FAIL;
        break;
    }
  }
  if (state == BT_BOND_STATE_BONDED && !pairing_cb.static_bdaddr.IsEmpty() &&
      bd_addr != pairing_cb.static_bdaddr) {
    // Report RPA bonding state to Java in crosskey paring
    bond_state_changed(status, bd_addr, BT_BOND_STATE_BONDING);
  }
  bond_state_changed(status, bd_addr, state);
  // TODO(240451061): Calling `stop_oob_advertiser();` gets command
  // disallowed...
}

void btif_dm_load_ble_local_keys(void) {
  memset(&ble_local_key_cb, 0, sizeof(btif_dm_local_key_cb_t));

  if (btif_storage_get_ble_local_key(
          BTIF_DM_LE_LOCAL_KEY_ER, &ble_local_key_cb.er) == BT_STATUS_SUCCESS) {
    ble_local_key_cb.is_er_rcvd = true;
    log::verbose("BLE ER key loaded");
  }

  if ((btif_storage_get_ble_local_key(BTIF_DM_LE_LOCAL_KEY_IR,
                                      &ble_local_key_cb.id_keys.ir) ==
       BT_STATUS_SUCCESS) &&
      (btif_storage_get_ble_local_key(BTIF_DM_LE_LOCAL_KEY_IRK,
                                      &ble_local_key_cb.id_keys.irk) ==
       BT_STATUS_SUCCESS) &&
      (btif_storage_get_ble_local_key(BTIF_DM_LE_LOCAL_KEY_DHK,
                                      &ble_local_key_cb.id_keys.dhk) ==
       BT_STATUS_SUCCESS)) {
    ble_local_key_cb.is_id_keys_rcvd = true;
    log::verbose("BLE ID keys loaded");
  }
}
void btif_dm_get_ble_local_keys(tBTA_DM_BLE_LOCAL_KEY_MASK* p_key_mask,
                                Octet16* p_er,
                                tBTA_BLE_LOCAL_ID_KEYS* p_id_keys) {
  ASSERT(p_key_mask != nullptr);
  if (ble_local_key_cb.is_er_rcvd) {
    ASSERT(p_er != nullptr);
    *p_er = ble_local_key_cb.er;
    *p_key_mask |= BTA_BLE_LOCAL_KEY_TYPE_ER;
  }

  if (ble_local_key_cb.is_id_keys_rcvd) {
    ASSERT(p_id_keys != nullptr);
    p_id_keys->ir = ble_local_key_cb.id_keys.ir;
    p_id_keys->irk = ble_local_key_cb.id_keys.irk;
    p_id_keys->dhk = ble_local_key_cb.id_keys.dhk;
    *p_key_mask |= BTA_BLE_LOCAL_KEY_TYPE_ID;
  }
  log::verbose("*p_key_mask=0x{:02x}", *p_key_mask);
}

static void btif_dm_save_ble_bonding_keys(RawAddress& bd_addr) {
  log::verbose("{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  if (bd_addr.IsEmpty()) {
    log::warn("bd_addr is empty");
    return;
  }

  if (pairing_cb.ble.is_penc_key_rcvd) {
    btif_storage_add_ble_bonding_key(
        &bd_addr, (uint8_t*)&pairing_cb.ble.penc_key, BTM_LE_KEY_PENC,
        sizeof(tBTM_LE_PENC_KEYS));
  }

  if (pairing_cb.ble.is_pid_key_rcvd) {
    btif_storage_add_ble_bonding_key(&bd_addr,
                                     (uint8_t*)&pairing_cb.ble.pid_key,
                                     BTM_LE_KEY_PID, sizeof(tBTM_LE_PID_KEYS));
  }

  if (pairing_cb.ble.is_pcsrk_key_rcvd) {
    btif_storage_add_ble_bonding_key(
        &bd_addr, (uint8_t*)&pairing_cb.ble.pcsrk_key, BTM_LE_KEY_PCSRK,
        sizeof(tBTM_LE_PCSRK_KEYS));
  }

  if (pairing_cb.ble.is_lenc_key_rcvd) {
    btif_storage_add_ble_bonding_key(
        &bd_addr, (uint8_t*)&pairing_cb.ble.lenc_key, BTM_LE_KEY_LENC,
        sizeof(tBTM_LE_LENC_KEYS));
  }

  if (pairing_cb.ble.is_lcsrk_key_rcvd) {
    btif_storage_add_ble_bonding_key(
        &bd_addr, (uint8_t*)&pairing_cb.ble.lcsrk_key, BTM_LE_KEY_LCSRK,
        sizeof(tBTM_LE_LCSRK_KEYS));
  }

  if (pairing_cb.ble.is_lidk_key_rcvd) {
    uint8_t empty[] = {};
    btif_storage_add_ble_bonding_key(&bd_addr, empty, BTM_LE_KEY_LID, 0);
  }
}

static void btif_dm_remove_ble_bonding_keys(void) {
  log::verbose("removing ble bonding keys");

  RawAddress bd_addr = pairing_cb.bd_addr;
  btif_storage_remove_ble_bonding_keys(&bd_addr);
}

/*******************************************************************************
 *
 * Function         btif_dm_ble_sec_req_evt
 *
 * Description      Eprocess security request event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_ble_sec_req_evt(tBTA_DM_BLE_SEC_REQ* p_ble_req,
                                    bool is_consent) {
  bt_bdname_t bd_name;
  uint32_t cod;
  int dev_type;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_ble_req->bd_addr));

  if (!is_consent && pairing_cb.state == BT_BOND_STATE_BONDING) {
    log::warn("Discard security request");
    return;
  }

  /* Remote name update */
  if (!btif_get_device_type(p_ble_req->bd_addr, &dev_type)) {
    dev_type = BT_DEVICE_TYPE_BLE;
  }
  btif_dm_update_ble_remote_properties(p_ble_req->bd_addr, p_ble_req->bd_name,
                                       kDevClassEmpty,
                                       (tBT_DEVICE_TYPE)dev_type);

  RawAddress bd_addr = p_ble_req->bd_addr;
  memcpy(bd_name.name, p_ble_req->bd_name, BD_NAME_LEN);
  bd_name.name[BD_NAME_LEN] = '\0';

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);

  pairing_cb.bond_type = BOND_TYPE_PERSISTENT;
  pairing_cb.is_le_only = true;
  pairing_cb.is_le_nc = false;
  pairing_cb.is_ssp = true;
  btm_set_bond_type_dev(p_ble_req->bd_addr, pairing_cb.bond_type);

  cod = COD_UNCLASSIFIED;

  BTM_LogHistory(kBtmLogTagCallback, bd_addr, "SSP ble request",
                 base::StringPrintf("name:'%s' BT_SSP_VARIANT_CONSENT",
                                    PRIVATE_NAME(bd_name.name)));

  GetInterfaceToProfiles()->events->invoke_ssp_request_cb(
      bd_addr, bd_name, cod, BT_SSP_VARIANT_CONSENT, 0);
}

/*******************************************************************************
 *
 * Function         btif_dm_ble_passkey_req_evt
 *
 * Description      Executes pin request event in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_dm_ble_passkey_req_evt(tBTA_DM_PIN_REQ* p_pin_req) {
  bt_bdname_t bd_name;
  uint32_t cod;
  int dev_type;

  /* Remote name update */
  if (!btif_get_device_type(p_pin_req->bd_addr, &dev_type)) {
    dev_type = BT_DEVICE_TYPE_BLE;
  }
  btif_dm_update_ble_remote_properties(p_pin_req->bd_addr, p_pin_req->bd_name,
                                       kDevClassEmpty,
                                       (tBT_DEVICE_TYPE)dev_type);

  RawAddress bd_addr = p_pin_req->bd_addr;
  memcpy(bd_name.name, p_pin_req->bd_name, BD_NAME_LEN);
  bd_name.name[BD_NAME_LEN] = '\0';

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  pairing_cb.is_le_only = true;

  cod = COD_UNCLASSIFIED;

  BTM_LogHistory(kBtmLogTagCallback, bd_addr, "PIN request",
                 base::StringPrintf("name:'%s'", PRIVATE_NAME(bd_name.name)));

  GetInterfaceToProfiles()->events->invoke_pin_request_cb(bd_addr, bd_name, cod,
                                                          false);
}
static void btif_dm_ble_key_nc_req_evt(tBTA_DM_SP_KEY_NOTIF* p_notif_req) {
  /* TODO implement key notification for numeric comparison */
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_notif_req->bd_addr));

  /* Remote name update */
  btif_update_remote_properties(p_notif_req->bd_addr, p_notif_req->bd_name,
                                kDevClassEmpty, BT_DEVICE_TYPE_BLE);

  RawAddress bd_addr = p_notif_req->bd_addr;

  bt_bdname_t bd_name;
  memcpy(bd_name.name, p_notif_req->bd_name, BD_NAME_LEN);
  bd_name.name[BD_NAME_LEN] = '\0';

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  pairing_cb.is_ssp = false;
  pairing_cb.is_le_only = true;
  pairing_cb.is_le_nc = true;

  BTM_LogHistory(
      kBtmLogTagCallback, bd_addr, "Ssp request",
      base::StringPrintf("name:'%s' passkey:%u", PRIVATE_NAME(bd_name.name),
                         p_notif_req->passkey));

  GetInterfaceToProfiles()->events->invoke_ssp_request_cb(
      bd_addr, bd_name, COD_UNCLASSIFIED, BT_SSP_VARIANT_PASSKEY_CONFIRMATION,
      p_notif_req->passkey);
}

static void btif_dm_ble_oob_req_evt(tBTA_DM_SP_RMT_OOB* req_oob_type) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(req_oob_type->bd_addr));

  RawAddress bd_addr = req_oob_type->bd_addr;
  /* We already checked if OOB data is present in
   * btif_dm_set_oob_for_le_io_req, but check here again. If it's not present
   * do nothing, pairing will timeout.
   */
  if (is_empty_128bit(oob_cb.p192_data.sm_tk)) {
    return;
  }

  /* make sure OOB data is for this particular device */
  if (req_oob_type->bd_addr != oob_cb.bdaddr) {
    log::warn("remote address didn't match OOB data address");
    return;
  }

  /* Remote name update */
  btif_update_remote_properties(req_oob_type->bd_addr, req_oob_type->bd_name,
                                kDevClassEmpty, BT_DEVICE_TYPE_BLE);

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  pairing_cb.is_ssp = false;
  pairing_cb.is_le_only = true;
  pairing_cb.is_le_nc = false;

  BTM_BleOobDataReply(req_oob_type->bd_addr, 0, 16, oob_cb.p192_data.sm_tk);
}

static void btif_dm_ble_sc_oob_req_evt(tBTA_DM_SP_RMT_OOB* req_oob_type) {
  RawAddress bd_addr = req_oob_type->bd_addr;
  log::verbose("bd_addr: {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  log::verbose("oob_cb.bdaddr: {}", ADDRESS_TO_LOGGABLE_CSTR(oob_cb.bdaddr));

  /* make sure OOB data is for this particular device */
  if (req_oob_type->bd_addr != oob_cb.bdaddr) {
    log::error("remote address didn't match OOB data address");
    return;
  }

  /* We already checked if OOB data is present in
   * btif_dm_set_oob_for_le_io_req, but check here again. If it's not present
   * do nothing, pairing will timeout.
   */
  bt_oob_data_t oob_data_to_use = {};
  switch (oob_cb.data_present) {
    case BTM_OOB_PRESENT_192_AND_256:
      log::info("Have both P192 and  P256");
      [[fallthrough]];
    // Always prefer 256 for LE
    case BTM_OOB_PRESENT_256:
      log::info("Using P256");
      if (is_empty_128bit(oob_cb.p256_data.c) &&
          is_empty_128bit(oob_cb.p256_data.r)) {
        log::warn("P256 LE SC OOB data is empty");
        return;
      }
      oob_data_to_use = oob_cb.p256_data;
      break;
    case BTM_OOB_PRESENT_192:
      log::info("Using P192");
      if (is_empty_128bit(oob_cb.p192_data.c) &&
          is_empty_128bit(oob_cb.p192_data.r)) {
        log::warn("P192 LE SC OOB data is empty");
        return;
      }
      oob_data_to_use = oob_cb.p192_data;
      break;
  }

  /* Remote name update */
  btif_update_remote_properties(req_oob_type->bd_addr,
                                oob_data_to_use.device_name, kDevClassEmpty,
                                BT_DEVICE_TYPE_BLE);

  bond_state_changed(BT_STATUS_SUCCESS, bd_addr, BT_BOND_STATE_BONDING);
  pairing_cb.is_ssp = false;
  // TODO: we can derive classic pairing from this one
  pairing_cb.is_le_only = true;
  pairing_cb.is_le_nc = false;
  BTM_BleSecureConnectionOobDataReply(req_oob_type->bd_addr, oob_data_to_use.c,
                                      oob_data_to_use.r);
}

void btif_dm_update_ble_remote_properties(const RawAddress& bd_addr,
                                          BD_NAME bd_name, DEV_CLASS dev_class,
                                          tBT_DEVICE_TYPE dev_type) {
  btif_update_remote_properties(bd_addr, bd_name, dev_class, dev_type);
}

static void btif_dm_ble_tx_test_cback(void* p) {
  char* p_param = (char*)p;
  uint8_t status;
  STREAM_TO_UINT8(status, p_param);
  GetInterfaceToProfiles()->events->invoke_le_test_mode_cb(
      (status == 0) ? BT_STATUS_SUCCESS : BT_STATUS_FAIL, 0);
}

static void btif_dm_ble_rx_test_cback(void* p) {
  char* p_param = (char*)p;
  uint8_t status;
  STREAM_TO_UINT8(status, p_param);
  GetInterfaceToProfiles()->events->invoke_le_test_mode_cb(
      (status == 0) ? BT_STATUS_SUCCESS : BT_STATUS_FAIL, 0);
}

static void btif_dm_ble_test_end_cback(void* p) {
  char* p_param = (char*)p;
  uint8_t status;
  uint16_t count = 0;
  STREAM_TO_UINT8(status, p_param);
  if (status == 0) STREAM_TO_UINT16(count, p_param);
  GetInterfaceToProfiles()->events->invoke_le_test_mode_cb(
      (status == 0) ? BT_STATUS_SUCCESS : BT_STATUS_FAIL, count);
}

void btif_ble_transmitter_test(uint8_t tx_freq, uint8_t test_data_len,
                               uint8_t packet_payload) {
  BTM_BleTransmitterTest(tx_freq, test_data_len, packet_payload,
                         btif_dm_ble_tx_test_cback);
}

void btif_ble_receiver_test(uint8_t rx_freq) {
  BTM_BleReceiverTest(rx_freq, btif_dm_ble_rx_test_cback);
}

void btif_ble_test_end() { BTM_BleTestEnd(btif_dm_ble_test_end_cback); }

void btif_dm_on_disable() {
  /* cancel any pending pairing requests */
  if (is_bonding_or_sdp()) {
    log::verbose("Cancel pending pairing request");
    btif_dm_cancel_bond(pairing_cb.bd_addr);
  }
}

/*******************************************************************************
 *
 * Function         btif_dm_read_energy_info
 *
 * Description     Reads the energy info from controller
 *
 * Returns         void
 *
 ******************************************************************************/
void btif_dm_read_energy_info() { BTA_DmBleGetEnergyInfo(bta_energy_info_cb); }

static const char* btif_get_default_local_name() {
  if (btif_default_local_name[0] == '\0') {
    int max_len = sizeof(btif_default_local_name) - 1;

    char prop_name[PROPERTY_VALUE_MAX];
    osi_property_get(PROPERTY_DEFAULT_DEVICE_NAME, prop_name, "");
    strncpy(btif_default_local_name, prop_name, max_len);

    // If no value was placed in the btif_default_local_name then use model name
    if (btif_default_local_name[0] == '\0') {
      char prop_model[PROPERTY_VALUE_MAX];
      osi_property_get(PROPERTY_PRODUCT_MODEL, prop_model, "");
      strncpy(btif_default_local_name, prop_model, max_len);
    }
    btif_default_local_name[max_len] = '\0';
  }
  return btif_default_local_name;
}

static void btif_stats_add_bond_event(const RawAddress& bd_addr,
                                      bt_bond_function_t function,
                                      bt_bond_state_t state) {
  std::unique_lock<std::mutex> lock(bond_event_lock);

  btif_bond_event_t* event = &btif_dm_bond_events[btif_events_end_index];
  event->bd_addr = bd_addr;
  event->function = function;
  event->state = state;
  clock_gettime(CLOCK_REALTIME, &event->timestamp);

  btif_num_bond_events++;
  btif_events_end_index =
      (btif_events_end_index + 1) % (MAX_BTIF_BOND_EVENT_ENTRIES + 1);
  if (btif_events_end_index == btif_events_start_index) {
    btif_events_start_index =
        (btif_events_start_index + 1) % (MAX_BTIF_BOND_EVENT_ENTRIES + 1);
  }

  int type;
  btif_get_device_type(bd_addr, &type);

  bluetooth::common::device_type_t device_type;
  switch (type) {
    case BT_DEVICE_TYPE_BREDR:
      device_type = bluetooth::common::DEVICE_TYPE_BREDR;
      break;
    case BT_DEVICE_TYPE_BLE:
      device_type = bluetooth::common::DEVICE_TYPE_LE;
      break;
    case BT_DEVICE_TYPE_DUMO:
      device_type = bluetooth::common::DEVICE_TYPE_DUMO;
      break;
    default:
      device_type = bluetooth::common::DEVICE_TYPE_UNKNOWN;
      break;
  }

  uint32_t cod = get_cod(&bd_addr);
  uint64_t ts =
      event->timestamp.tv_sec * 1000 + event->timestamp.tv_nsec / 1000000;
  bluetooth::common::BluetoothMetricsLogger::GetInstance()->LogPairEvent(
      0, ts, cod, device_type);
}

void btif_debug_bond_event_dump(int fd) {
  std::unique_lock<std::mutex> lock(bond_event_lock);
  dprintf(fd, "\nBond Events: \n");
  dprintf(fd, "  Total Number of events: %zu\n", btif_num_bond_events);
  if (btif_num_bond_events > 0)
    dprintf(fd,
            "  Time          address            Function             State\n");

  for (size_t i = btif_events_start_index; i != btif_events_end_index;
       i = (i + 1) % (MAX_BTIF_BOND_EVENT_ENTRIES + 1)) {
    btif_bond_event_t* event = &btif_dm_bond_events[i];

    char eventtime[20];
    char temptime[20];
    struct tm* tstamp = localtime(&event->timestamp.tv_sec);
    strftime(temptime, sizeof(temptime), "%H:%M:%S", tstamp);
    snprintf(eventtime, sizeof(eventtime), "%s.%03ld", temptime,
             event->timestamp.tv_nsec / 1000000);

    const char* func_name;
    switch (event->function) {
      case BTIF_DM_FUNC_CREATE_BOND:
        func_name = "btif_dm_create_bond";
        break;
      case BTIF_DM_FUNC_REMOVE_BOND:
        func_name = "btif_dm_remove_bond";
        break;
      case BTIF_DM_FUNC_BOND_STATE_CHANGED:
        func_name = "bond_state_changed ";
        break;
      case BTIF_DM_FUNC_CANCEL_BOND:
        func_name = "btif_dm_cancel_bond";
        break;
      default:
        func_name = "Invalid value      ";
        break;
    }

    const char* bond_state;
    switch (event->state) {
      case BT_BOND_STATE_NONE:
        bond_state = "BOND_STATE_NONE";
        break;
      case BT_BOND_STATE_BONDING:
        bond_state = "BOND_STATE_BONDING";
        break;
      case BT_BOND_STATE_BONDED:
        bond_state = "BOND_STATE_BONDED";
        break;
      default:
        bond_state = "Invalid bond state";
        break;
    }

    dprintf(fd, "  %s  %s  %s  %s\n", eventtime,
            ADDRESS_TO_LOGGABLE_CSTR(event->bd_addr), func_name, bond_state);
  }
}

bool btif_get_device_type(const RawAddress& bda, int* p_device_type) {
  if (p_device_type == NULL) return false;

  std::string addrstr = bda.ToString();
  const char* bd_addr_str = addrstr.c_str();

  if (!btif_config_get_int(bd_addr_str, BTIF_STORAGE_KEY_DEV_TYPE,
                           p_device_type))
    return false;
  tBT_DEVICE_TYPE device_type = static_cast<tBT_DEVICE_TYPE>(*p_device_type);
  log::debug("bd_addr:{} device_type:{}", ADDRESS_TO_LOGGABLE_CSTR(bda),
             DeviceTypeText(device_type));

  return true;
}

bool btif_get_address_type(const RawAddress& bda, tBLE_ADDR_TYPE* p_addr_type) {
  if (p_addr_type == NULL) return false;

  std::string addrstr = bda.ToString();
  const char* bd_addr_str = addrstr.c_str();

  int val = 0;
  if (!btif_config_get_int(bd_addr_str, BTIF_STORAGE_KEY_ADDR_TYPE, &val))
    return false;
  *p_addr_type = static_cast<tBLE_ADDR_TYPE>(val);
  log::debug("bd_addr:{}[{}]", ADDRESS_TO_LOGGABLE_CSTR(bda),
             AddressTypeText(*p_addr_type));
  return true;
}

void btif_dm_clear_event_filter() { BTA_DmClearEventFilter(); }

void btif_dm_clear_event_mask() { BTA_DmClearEventMask(); }

void btif_dm_clear_filter_accept_list() { BTA_DmClearFilterAcceptList(); }

void btif_dm_disconnect_all_acls() { BTA_DmDisconnectAllAcls(); }

void btif_dm_le_rand(LeRandCallback callback) {
  BTA_DmLeRand(std::move(callback));
}

void btif_dm_set_event_filter_connection_setup_all_devices() {
  // Autoplumbed
  BTA_DmSetEventFilterConnectionSetupAllDevices();
}

void btif_dm_allow_wake_by_hid(
    std::vector<RawAddress> classic_addrs,
    std::vector<std::pair<RawAddress, uint8_t>> le_addrs) {
  BTA_DmAllowWakeByHid(std::move(classic_addrs), std::move(le_addrs));
}

void btif_dm_restore_filter_accept_list(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices) {
  // Autoplumbed
  BTA_DmRestoreFilterAcceptList(std::move(le_devices));
}

void btif_dm_set_default_event_mask_except(uint64_t mask, uint64_t le_mask) {
  // Autoplumbed
  BTA_DmSetDefaultEventMaskExcept(mask, le_mask);
}

void btif_dm_set_event_filter_inquiry_result_all_devices() {
  // Autoplumbed
  BTA_DmSetEventFilterInquiryResultAllDevices();
}

void btif_dm_metadata_changed(const RawAddress& remote_bd_addr, int key,
                              std::vector<uint8_t> value) {
  static const int METADATA_LE_AUDIO = 26;
  /* If METADATA_LE_AUDIO is present, device is LE Audio capable */
  if (key == METADATA_LE_AUDIO) {
    log::info("Device is LE Audio Capable {}",
              ADDRESS_TO_LOGGABLE_CSTR(remote_bd_addr));
    metadata_cb.le_audio_cache.insert_or_assign(remote_bd_addr, value);
  }
}

namespace bluetooth {
namespace legacy {
namespace testing {

void bta_energy_info_cb(tBTM_BLE_TX_TIME_MS tx_time,
                        tBTM_BLE_RX_TIME_MS rx_time,
                        tBTM_BLE_IDLE_TIME_MS idle_time,
                        tBTM_BLE_ENERGY_USED energy_used,
                        tBTM_CONTRL_STATE ctrl_state, tBTA_STATUS status) {
  ::bta_energy_info_cb(tx_time, rx_time, idle_time, energy_used, ctrl_state,
                       status);
}

void btif_dm_search_services_evt(tBTA_DM_SEARCH_EVT event,
                                 tBTA_DM_SEARCH* p_data) {
  ::btif_dm_search_services_evt(event, p_data);
}

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth
