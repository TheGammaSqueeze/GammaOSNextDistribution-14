/******************************************************************************
 *
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

#ifndef BTIF_DM_H
#define BTIF_DM_H

#include "bta/include/bta_api.h"
#include "bta/include/bta_sec_api.h"
#include "btif_uid.h"
#include "internal_include/bt_target.h"
#include "internal_include/bte_appl.h"
#include "types/raw_address.h"

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/
#define COD_MASK 0x07FF
#define COD_UNCLASSIFIED ((0x1F) << 8)
/* Focus on Major and minor device class*/
#define COD_DEVICE_MASK 0x1FFC
#define COD_HID_KEYBOARD 0x0540
#define COD_HID_POINTING 0x0580
#define COD_HID_COMBO 0x05C0
#define COD_HID_MAJOR 0x0500
#define COD_HID_SUB_MAJOR 0x00C0
#define COD_HID_MASK 0x0700
#define COD_AV_HEADSETS 0x0404
#define COD_AV_HANDSFREE 0x0408
#define COD_AV_HEADPHONES 0x0418
#define COD_AV_PORTABLE_AUDIO 0x041C
#define COD_AV_HIFI_AUDIO 0x0428
#define COD_CLASS_LE_AUDIO (1 << 14)

/*******************************************************************************
 *  Functions
 ******************************************************************************/
void btif_dm_init(uid_set_t* set);
void btif_dm_cleanup(void);

/**
 * BTIF callback for security events
 */
void btif_dm_sec_evt(tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data);

/**
 * BTIF callback for ACL up/down and address consolidation events
 */
void btif_dm_acl_evt(tBTA_DM_ACL_EVT event, tBTA_DM_ACL* p_data);

/**
 * Notify BT disable being initiated. DM may chose to abort
 * pending commands, like pairing
 */
void btif_dm_on_disable(void);

/**
 * Callout for handling io_capabilities request
 */
void btif_dm_proc_io_req(tBTM_AUTH_REQ* p_auth_req, bool is_orig);
/**
 * Callout for handling io_capabilities response
 */
void btif_dm_proc_io_rsp(const RawAddress& bd_addr, tBTM_IO_CAP io_cap,
                         tBTM_OOB_DATA oob_data, tBTM_AUTH_REQ auth_req);

/**
 * Device Configuration Queries
 */
DEV_CLASS btif_dm_get_local_class_of_device();

/**
 * Out-of-band functions
 */
void btif_dm_set_oob_for_io_req(tBTM_OOB_DATA* p_oob_data);
void btif_dm_set_oob_for_le_io_req(const RawAddress& bd_addr,
                                   tBTM_OOB_DATA* p_oob_data,
                                   tBTM_LE_AUTH_REQ* p_auth_req);
void btif_dm_load_local_oob(void);
void btif_dm_proc_loc_oob(tBT_TRANSPORT transport, bool is_valid,
                          const Octet16& c, const Octet16& r);
bool btif_dm_proc_rmt_oob(const RawAddress& bd_addr, Octet16* p_c,
                          Octet16* p_r);
void btif_dm_generate_local_oob_data(tBT_TRANSPORT transport);

void btif_dm_clear_event_filter();

void btif_dm_clear_event_mask();

void btif_dm_clear_filter_accept_list();

void btif_dm_disconnect_all_acls();

void btif_dm_le_rand(LeRandCallback callback);
void btif_dm_set_event_filter_connection_setup_all_devices();
void btif_dm_allow_wake_by_hid(
    std::vector<RawAddress> classic_addrs,
    std::vector<std::pair<RawAddress, uint8_t>> le_addrs);
void btif_dm_restore_filter_accept_list(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices);
void btif_dm_set_default_event_mask_except(uint64_t mask, uint64_t le_mask);
void btif_dm_set_event_filter_inquiry_result_all_devices();
void btif_dm_metadata_changed(const RawAddress& remote_bd_addr, int key,
                              std::vector<uint8_t> value);

/*callout for reading SMP properties from Text file*/
bool btif_dm_get_smp_config(tBTE_APPL_CFG* p_cfg);

typedef struct {
  bool is_penc_key_rcvd;
  tBTM_LE_PENC_KEYS penc_key; /* received peer encryption key */
  bool is_pcsrk_key_rcvd;
  tBTM_LE_PCSRK_KEYS pcsrk_key; /* received peer device SRK */
  bool is_pid_key_rcvd;
  tBTM_LE_PID_KEYS pid_key; /* peer device ID key */
  bool is_lenc_key_rcvd;
  tBTM_LE_LENC_KEYS
      lenc_key; /* local encryption reproduction keys LTK = = d1(ER,DIV,0)*/
  bool is_lcsrk_key_rcvd;
  tBTM_LE_LCSRK_KEYS lcsrk_key; /* local device CSRK = d1(ER,DIV,1)*/
  bool is_lidk_key_rcvd;        /* local identity key received */
} btif_dm_ble_cb_t;

#define BTIF_DM_LE_LOCAL_KEY_IR (1 << 0)
#define BTIF_DM_LE_LOCAL_KEY_IRK (1 << 1)
#define BTIF_DM_LE_LOCAL_KEY_DHK (1 << 2)
#define BTIF_DM_LE_LOCAL_KEY_ER (1 << 3)

void btif_dm_load_ble_local_keys(void);
void btif_dm_get_ble_local_keys(tBTA_DM_BLE_LOCAL_KEY_MASK* p_key_mask,
                                Octet16* p_er,
                                tBTA_BLE_LOCAL_ID_KEYS* p_id_keys);
void btif_dm_update_ble_remote_properties(const RawAddress& bd_addr,
                                          BD_NAME bd_name, DEV_CLASS dev_class,
                                          tBT_DEVICE_TYPE dev_type);

bool check_cod_hid(const RawAddress& bd_addr);
bool check_cod_hid_major(const RawAddress& bd_addr, uint32_t cod);
bool is_device_le_audio_capable(const RawAddress bd_addr);
#endif
