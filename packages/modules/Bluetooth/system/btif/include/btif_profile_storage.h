/******************************************************************************
 *
 *  Copyright 2022 The Android Open Source Project
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

#pragma once

#include <bluetooth/uuid.h>
#include <hardware/bluetooth.h>

#include <vector>

#include "types/raw_address.h"

/*******************************************************************************
 *  Functions
 ******************************************************************************/

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

/** Returns all bonded LE hid devices + their address types. */
std::vector<std::pair<RawAddress, uint8_t>> btif_storage_get_le_hid_devices(
    void);

/** Returns all bonded Classic HID devices capable of waking the system from
 * system suspend (i.e. Keyboards and Pointing devices).
 */
std::vector<RawAddress> btif_storage_get_wake_capable_classic_hid_devices(void);

/** Loads information about bonded hearing aid devices */
void btif_storage_load_bonded_hearing_aids();

/** Deletes the bonded hearing aid device info from NVRAM */
void btif_storage_remove_hearing_aid(const RawAddress& address);

/** Set/Unset the hearing aid device HEARING_AID_IS_ACCEPTLISTED flag. */
void btif_storage_set_hearing_aid_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist);

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

void btif_storage_add_groups(const RawAddress& addr);
void btif_storage_load_bonded_groups(void);
void btif_storage_remove_groups(const RawAddress& address);

void btif_storage_set_csis_autoconnect(const RawAddress& addr,
                                       bool autoconnect);
void btif_storage_update_csis_info(const RawAddress& addr);
void btif_storage_load_bonded_csis_devices();
void btif_storage_remove_csis_device(const RawAddress& address);

void btif_storage_load_bonded_volume_control_devices(void);
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

/*******************************************************************************
 *
 *Function : btif_storage_set_pce_profile_version
 *
 * Description :
 *    This function store remote PCE profile version in config file
 *
 ******************************************************************************/

void btif_storage_set_pce_profile_version(const RawAddress& remote_bd_addr,
                                          uint16_t peer_pce_version);

/*******************************************************************************
 *
 * Function        btif_storage_is_pce_version_102
 *
 * Description     checks if remote supports PBAP 1.2
 *
 * Returns         true/false depending on remote PBAP version support found in
 *file.
 *
 ******************************************************************************/

bool btif_storage_is_pce_version_102(const RawAddress& remote_bd_addr);
