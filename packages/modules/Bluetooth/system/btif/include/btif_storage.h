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

#ifndef BTIF_STORAGE_H
#define BTIF_STORAGE_H

#include <bluetooth/uuid.h>
#include <hardware/bluetooth.h>

#include "internal_include/bt_target.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_sec_api_types.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/
#define BTIF_STORAGE_FILL_PROPERTY(p_prop, t, l, p_v) \
  do {                                                \
    (p_prop)->type = (t);                             \
    (p_prop)->len = (l);                              \
    (p_prop)->val = (p_v);                            \
  } while (0)

#define STORAGE_BDADDR_STRING_SZ (18) /* 00:11:22:33:44:55 */
#define STORAGE_UUID_STRING_SIZE \
  (36 + 1) /* 00001200-0000-1000-8000-00805f9b34fb; */
#define STORAGE_PINLEN_STRING_MAX_SIZE (2)  /* ascii pinlen max chars */
#define STORAGE_KEYTYPE_STRING_MAX_SIZE (1) /* ascii keytype max chars */

#define STORAGE_KEY_TYPE_MAX (10)

/*******************************************************************************
 *  Functions
 ******************************************************************************/

/*******************************************************************************
 *
 * Function         btif_in_fetch_bonded_devices
 *
 * Description      Helper function to fetch the bonded devices
 *                  from NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if successful, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_in_fetch_bonded_device(const std::string& bdstr);

typedef struct {
  uint32_t num_devices;
  RawAddress devices[BTM_SEC_MAX_DEVICE_RECORDS];
} btif_bonded_devices_t;

bt_status_t btif_in_fetch_bonded_ble_device(
    const std::string& remote_bd_addr, int add,
    btif_bonded_devices_t* p_bonded_devices);

/*******************************************************************************
 *
 * Function         btif_storage_get_adapter_property
 *
 * Description      BTIF storage API - Fetches the adapter property->type
 *                  from NVRAM and fills property->val.
 *                  Caller should provide memory for property->val and
 *                  set the property->val
 *
 * Returns          BT_STATUS_SUCCESS if the fetch was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_get_adapter_property(bt_property_t* property);

/*******************************************************************************
 *
 * Function         btif_storage_set_adapter_property
 *
 * Description      BTIF storage API - Stores the adapter property
 *                  to NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_set_adapter_property(bt_property_t* property);

/*******************************************************************************
 *
 * Function         btif_storage_get_remote_device_property
 *
 * Description      BTIF storage API - Fetches the remote device property->type
 *                  from NVRAM and fills property->val.
 *                  Caller should provide memory for property->val and
 *                  set the property->val
 *
 * Returns          BT_STATUS_SUCCESS if the fetch was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_get_remote_device_property(
    const RawAddress* remote_bd_addr, bt_property_t* property);

/*******************************************************************************
 *
 * Function         btif_storage_set_remote_device_property
 *
 * Description      BTIF storage API - Stores the remote device property
 *                  to NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_set_remote_device_property(
    const RawAddress* remote_bd_addr, bt_property_t* property);

/*******************************************************************************
 *
 * Function         btif_storage_get_io_caps
 *
 * Description      BTIF storage API - Fetches the local Input/Output
 *                  capabilities of the device.
 *
 * Returns          Returns local IO Capability of device. If not stored,
 *                  returns BTM_LOCAL_IO_CAPS.
 *
 ******************************************************************************/
tBTM_IO_CAP btif_storage_get_local_io_caps();

/*******************************************************************************
 *
 * Function         btif_storage_add_remote_device
 *
 * Description      BTIF storage API - Adds a newly discovered device to
 *                  track along with the timestamp. Also, stores the various
 *                  properties - RSSI, BDADDR, NAME (if found in EIR)
 *
 * Returns          BT_STATUS_SUCCESS if successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_add_remote_device(const RawAddress* remote_bd_addr,
                                           uint32_t num_properties,
                                           bt_property_t* properties);

/*******************************************************************************
 *
 * Function         btif_storage_add_bonded_device
 *
 * Description      BTIF storage API - Adds the newly bonded device to NVRAM
 *                  along with the link-key, Key type and Pin key length
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_add_bonded_device(RawAddress* remote_bd_addr,
                                           LinkKey link_key, uint8_t key_type,
                                           uint8_t pin_length);

/*******************************************************************************
 *
 * Function         btif_storage_remove_bonded_device
 *
 * Description      BTIF storage API - Deletes the bonded device from NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the deletion was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_remove_bonded_device(const RawAddress* remote_bd_addr);

/*******************************************************************************
 *
 * Function         btif_storage_load_le_devices
 *
 * Description      BTIF storage API - Loads all LE-only and Dual Mode devices
 *                  from NVRAM. This API invokes the adaper_properties_cb.
 *                  It also invokes invoke_address_consolidate_cb
 *                  to consolidate each Dual Mode device and
 *                  invoke_le_address_associate_cb to associate each LE-only
 *                  device between its RPA and identity address.
 *
 ******************************************************************************/
void btif_storage_load_le_devices(void);

/*******************************************************************************
 *
 * Function         btif_storage_load_bonded_devices
 *
 * Description      BTIF storage API - Loads all the bonded devices from NVRAM
 *                  and adds to the BTA.
 *                  Additionally, this API also invokes the adaper_properties_cb
 *                  and remote_device_properties_cb for each of the bonded
 *                  devices.
 *
 * Returns          BT_STATUS_SUCCESS if successful, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_load_bonded_devices(void);

/*******************************************************************************
 *
 * Function         btif_storage_set_hid_connection_policy
 *
 * Description      Stores connection policy info in nvram
 *
 * Returns          BT_STATUS_SUCCESS
 *
 ******************************************************************************/
bt_status_t btif_storage_set_hid_connection_policy(const RawAddress& addr,
                                                   bool reconnect_allowed);
/*******************************************************************************
 *
 * Function         btif_storage_get_hid_connection_policy
 *
 * Description      get connection policy info from nvram
 *
 * Returns          BT_STATUS_SUCCESS
 *
 ******************************************************************************/
bt_status_t btif_storage_get_hid_connection_policy(const RawAddress& addr,
                                                   bool* reconnect_allowed);

/*******************************************************************************
 *
 * Function         btif_storage_add_hid_device_info
 *
 * Description      BTIF storage API - Adds the hid information of bonded hid
 *                  devices-to NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/

bt_status_t btif_storage_add_hid_device_info(
    RawAddress* remote_bd_addr, uint16_t attr_mask, uint8_t sub_class,
    uint8_t app_id, uint16_t vendor_id, uint16_t product_id, uint16_t version,
    uint8_t ctry_code, uint16_t ssr_max_latency, uint16_t ssr_min_tout,
    uint16_t dl_len, uint8_t* dsc_list);

/*******************************************************************************
 *
 * Function         btif_storage_load_bonded_hid_info
 *
 * Description      BTIF storage API - Loads hid info for all the bonded devices
 *                  from NVRAM and adds those devices  to the BTA_HH.
 *
 * Returns          BT_STATUS_SUCCESS if successful, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_load_bonded_hid_info(void);

/*******************************************************************************
 *
 * Function         btif_storage_remove_hid_info
 *
 * Description      BTIF storage API - Deletes the bonded hid device info from
 *                  NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the deletion was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_remove_hid_info(const RawAddress& remote_bd_addr);

/** Loads information about bonded hearing aid devices */
void btif_storage_load_bonded_hearing_aids();

/** Deletes the bonded hearing aid device info from NVRAM */
void btif_storage_remove_hearing_aid(const RawAddress& address);

/** Set/Unset the hearing aid device HEARING_AID_IS_ACCEPTLISTED flag. */
void btif_storage_set_hearing_aid_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist);

/** Stores information about GATT Client supported features support */
void btif_storage_set_gatt_cl_supp_feat(const RawAddress& bd_addr,
                                        uint8_t feat);

/** Get client supported features */
uint8_t btif_storage_get_gatt_cl_supp_feat(const RawAddress& bd_addr);

/** Remove client supported features */
void btif_storage_remove_gatt_cl_supp_feat(const RawAddress& bd_addr);

/** Stores information about GATT server supported features */
void btif_storage_set_gatt_sr_supp_feat(const RawAddress& addr, uint8_t feat);

/** Gets information about GATT server supported features */
uint8_t btif_storage_get_sr_supp_feat(const RawAddress& bd_addr);

/** Store last server database hash for remote client */
void btif_storage_set_gatt_cl_db_hash(const RawAddress& bd_addr, Octet16 hash);

/** Get last server database hash for remote client */
Octet16 btif_storage_get_gatt_cl_db_hash(const RawAddress& bd_addr);

/** Remove last server database hash for remote client */
void btif_storage_remove_gatt_cl_db_hash(const RawAddress& bd_addr);

/** Get the hearing aid device properties. */
bool btif_storage_get_hearing_aid_prop(
    const RawAddress& address, uint8_t* capabilities, uint64_t* hi_sync_id,
    uint16_t* render_delay, uint16_t* preparation_delay, uint16_t* codecs);

/** Store Le Audio device autoconnect flag */
void btif_storage_set_leaudio_autoconnect(const RawAddress& addr,
                                          bool autoconnect);

/** Store PACs information */
void btif_storage_leaudio_update_pacs_bin(const RawAddress& addr);

/** Store ASEs information */
void btif_storage_leaudio_update_ase_bin(const RawAddress& addr);

/** Store Handles information */
void btif_storage_leaudio_update_handles_bin(const RawAddress& addr);

/** Store Le Audio device audio locations */
void btif_storage_set_leaudio_audio_location(const RawAddress& addr,
                                             uint32_t sink_location,
                                             uint32_t source_location);

/** Store Le Audio device context types */
void btif_storage_set_leaudio_supported_context_types(
    const RawAddress& addr, uint16_t sink_supported_context_type,
    uint16_t source_supported_context_type);

/** Remove Le Audio device service data */
void btif_storage_leaudio_clear_service_data(const RawAddress& address);

/** Remove Le Audio device from the storage */
void btif_storage_remove_leaudio(const RawAddress& address);

/** Load bonded Le Audio devices */
void btif_storage_load_bonded_leaudio(void);

/** Loads information about bonded HAS devices */
void btif_storage_load_bonded_leaudio_has_devices(void);

/** Deletes the bonded HAS device info from NVRAM */
void btif_storage_remove_leaudio_has(const RawAddress& address);

/** Set/Unset the HAS device acceptlist flag. */
void btif_storage_set_leaudio_has_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist);

/*******************************************************************************
 *
 * Function         btif_storage_is_retricted_device
 *
 * Description      BTIF storage API - checks if this device is a restricted
 *                  device
 *
 * Returns          true  if the device is labled as restricted
 *                  false otherwise
 *
 ******************************************************************************/
bool btif_storage_is_restricted_device(const RawAddress* remote_bd_addr);

int btif_storage_get_num_bonded_devices(void);

bt_status_t btif_storage_add_ble_bonding_key(RawAddress* remote_bd_addr,
                                             const uint8_t* key,
                                             uint8_t key_type,
                                             uint8_t key_length);
bt_status_t btif_storage_get_ble_bonding_key(const RawAddress& remote_bd_addr,
                                             uint8_t key_type,
                                             uint8_t* key_value,
                                             int key_length);

bt_status_t btif_storage_add_ble_local_key(const Octet16& key,
                                           uint8_t key_type);
bt_status_t btif_storage_remove_ble_bonding_keys(
    const RawAddress* remote_bd_addr);
bt_status_t btif_storage_remove_ble_local_keys(void);
bt_status_t btif_storage_get_ble_local_key(uint8_t key_type,
                                           Octet16* key_value);

bt_status_t btif_storage_get_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE* addr_type);

bt_status_t btif_storage_set_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE addr_type);

bool btif_storage_get_remote_addr_type(const RawAddress& remote_bd_addr,
                                       tBLE_ADDR_TYPE& addr_type);
void btif_storage_set_remote_addr_type(const RawAddress& remote_bd_addr,
                                       const tBLE_ADDR_TYPE& addr_type);
bool btif_storage_get_remote_device_type(const RawAddress& remote_bd_addr,
                                         tBT_DEVICE_TYPE& device_type);
void btif_storage_set_remote_device_type(const RawAddress& remote_bd_addr,
                                         const tBT_DEVICE_TYPE& device_type);

void btif_storage_add_groups(const RawAddress& addr);
void btif_storage_load_bonded_groups(void);
void btif_storage_remove_groups(const RawAddress& address);

void btif_storage_update_csis_info(const RawAddress& addr);
void btif_storage_load_bonded_csis_devices();
void btif_storage_remove_csis_device(const RawAddress& address);

/*******************************************************************************
 * Function         btif_storage_load_hidd
 *
 * Description      Loads hidd bonded device and "plugs" it into hidd
 *
 * Returns          BT_STATUS_SUCCESS if successful, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_load_hidd(void);

/*******************************************************************************
 *
 * Function         btif_storage_set_hidd
 *
 * Description      Stores hidd bonded device info in nvram.
 *
 * Returns          BT_STATUS_SUCCESS
 *
 ******************************************************************************/

bt_status_t btif_storage_set_hidd(const RawAddress& remote_bd_addr);

/*******************************************************************************
 *
 * Function         btif_storage_remove_hidd
 *
 * Description      Removes hidd bonded device info from nvram
 *
 * Returns          BT_STATUS_SUCCESS
 *
 ******************************************************************************/

bt_status_t btif_storage_remove_hidd(RawAddress* remote_bd_addr);

// Gets the device name for a given Bluetooth address |bd_addr|.
// The device name (if found) is stored in |name|.
// Returns true if the device name is found, othervise false.
// Note: |name| should point to a buffer that can store string of length
// |BTM_MAX_REM_BD_NAME_LEN|.
bool btif_storage_get_stored_remote_name(const RawAddress& bd_addr, char* name);

/******************************************************************************
 * Exported for unit tests
 *****************************************************************************/
size_t btif_split_uuids_string(const char* str, bluetooth::Uuid* p_uuid,
                               size_t max_uuids);

#endif /* BTIF_STORAGE_H */
