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
#define LOG_TAG "bt_btif_profile_storage"

#include "btif_profile_storage.h"

#include <alloca.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <vector>

#include "bta_csis_api.h"
#include "bta_groups.h"
#include "bta_has_api.h"
#include "bta_hd_api.h"
#include "bta_hearing_aid_api.h"
#include "bta_hh_api.h"
#include "bta_le_audio_api.h"
#include "bta_vc_api.h"
#include "btif/include/btif_dm.h"
#include "btif/include/btif_jni_task.h"
#include "btif_config.h"
#include "btif_hh.h"
#include "btif_storage.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/main_thread.h"
#include "storage/config_keys.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using base::Bind;
using bluetooth::Uuid;
using bluetooth::csis::CsisClient;
using bluetooth::groups::DeviceGroups;
using namespace bluetooth;

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/

#define STORAGE_HID_ATRR_MASK_SIZE (4)
#define STORAGE_HID_SUB_CLASS_SIZE (2)
#define STORAGE_HID_APP_ID_SIZE (2)
#define STORAGE_HID_VENDOR_ID_SIZE (4)
#define STORAGE_HID_PRODUCT_ID_SIZE (4)
#define STORAGE_HID_VERSION_SIZE (4)
#define STORAGE_HID_CTRY_CODE_SIZE (2)
#define STORAGE_HID_DESC_LEN_SIZE (4)
#define STORAGE_HID_DESC_MAX_SIZE (2 * 512)

/* <18 char bd addr> <space>LIST <attr_mask> <space> > <sub_class> <space>
   <app_id> <space>
                                <vendor_id> <space> > <product_id> <space>
   <version> <space>
                                <ctry_code> <space> > <desc_len> <space>
   <desc_list> <space> */
#define BTIF_HID_INFO_ENTRY_SIZE_MAX                                  \
  (STORAGE_BDADDR_STRING_SZ + 1 + STORAGE_HID_ATRR_MASK_SIZE + 1 +    \
   STORAGE_HID_SUB_CLASS_SIZE + 1 + STORAGE_HID_APP_ID_SIZE + 1 +     \
   STORAGE_HID_VENDOR_ID_SIZE + 1 + STORAGE_HID_PRODUCT_ID_SIZE + 1 + \
   STORAGE_HID_VERSION_SIZE + 1 + STORAGE_HID_CTRY_CODE_SIZE + 1 +    \
   STORAGE_HID_DESC_LEN_SIZE + 1 + STORAGE_HID_DESC_MAX_SIZE + 1)

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
                                                   bool reconnect_allowed) {
  std::string bdstr = addr.ToString();

  if (btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_RECONNECT_ALLOWED,
                          reconnect_allowed)) {
    return BT_STATUS_SUCCESS;
  } else {
    return BT_STATUS_FAIL;
  }
}

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
                                                   bool* reconnect_allowed) {
  std::string bdstr = addr.ToString();

  // For backward compatibility, assume that the reconnection is allowed in the
  // absence of the key
  int value = 1;
  btif_config_get_int(bdstr, BTIF_STORAGE_KEY_HID_RECONNECT_ALLOWED, &value);
  *reconnect_allowed = (value != 0);

  return BT_STATUS_SUCCESS;
}

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
    uint16_t dl_len, uint8_t* dsc_list) {
  log::verbose("btif_storage_add_hid_device_info:");
  std::string bdstr = remote_bd_addr->ToString();
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_ATTR_MASK, attr_mask);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_SUB_CLASS, sub_class);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_APP_ID, app_id);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_VENDOR_ID, vendor_id);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_PRODUCT_ID, product_id);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_VERSION, version);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_COUNTRY_CODE, ctry_code);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_SSR_MAX_LATENCY,
                      ssr_max_latency);
  btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HID_SSR_MIN_TIMEOUT,
                      ssr_min_tout);
  if (dl_len > 0)
    btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_HID_DESCRIPTOR, dsc_list,
                        dl_len);
  return BT_STATUS_SUCCESS;
}

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
bt_status_t btif_storage_load_bonded_hid_info(void) {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();
    tAclLinkSpec link_spec;

    log::verbose("Remote device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    int value;
    if (!btif_config_get_int(name, BTIF_STORAGE_KEY_HID_ATTR_MASK, &value))
      continue;
    uint16_t attr_mask = (uint16_t)value;

    if (btif_in_fetch_bonded_device(name) != BT_STATUS_SUCCESS) {
      btif_storage_remove_hid_info(bd_addr);
      continue;
    }

    tBTA_HH_DEV_DSCP_INFO dscp_info;
    memset(&dscp_info, 0, sizeof(dscp_info));

    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_SUB_CLASS, &value);
    uint8_t sub_class = (uint8_t)value;

    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_APP_ID, &value);
    uint8_t app_id = (uint8_t)value;

    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_VENDOR_ID, &value);
    dscp_info.vendor_id = (uint16_t)value;

    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_PRODUCT_ID, &value);
    dscp_info.product_id = (uint16_t)value;

    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_VERSION, &value);
    dscp_info.version = (uint16_t)value;

    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_COUNTRY_CODE, &value);
    dscp_info.ctry_code = (uint8_t)value;

    value = 0;
    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_SSR_MAX_LATENCY, &value);
    dscp_info.ssr_max_latency = (uint16_t)value;

    value = 0;
    btif_config_get_int(name, BTIF_STORAGE_KEY_HID_SSR_MIN_TIMEOUT, &value);
    dscp_info.ssr_min_tout = (uint16_t)value;

    size_t len =
        btif_config_get_bin_length(name, BTIF_STORAGE_KEY_HID_DESCRIPTOR);
    if (len > 0) {
      dscp_info.descriptor.dl_len = (uint16_t)len;
      dscp_info.descriptor.dsc_list = (uint8_t*)alloca(len);
      btif_config_get_bin(name, BTIF_STORAGE_KEY_HID_DESCRIPTOR,
                          (uint8_t*)dscp_info.descriptor.dsc_list, &len);
    }

    bool reconnect_allowed = false;
    btif_storage_get_hid_connection_policy(bd_addr, &reconnect_allowed);

    // add extracted information to BTA HH
    link_spec.addrt.bda = bd_addr;
    link_spec.addrt.type = BLE_ADDR_PUBLIC;
    link_spec.transport = BT_TRANSPORT_AUTO;
    if (btif_hh_add_added_dev(link_spec, attr_mask, reconnect_allowed)) {
      BTA_HhAddDev(link_spec, attr_mask, sub_class, app_id, dscp_info);
    }
  }

  return BT_STATUS_SUCCESS;
}

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
bt_status_t btif_storage_remove_hid_info(const RawAddress& remote_bd_addr) {
  std::string bdstr = remote_bd_addr.ToString();

  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_ATTR_MASK);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_SUB_CLASS);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_APP_ID);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_VENDOR_ID);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_PRODUCT_ID);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_VERSION);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_COUNTRY_CODE);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_SSR_MAX_LATENCY);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_SSR_MIN_TIMEOUT);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_DESCRIPTOR);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_RECONNECT_ALLOWED);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_REPORT);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_HID_REPORT_VERSION);
  return BT_STATUS_SUCCESS;
}

// Check if a given profile is supported.
static bool btif_device_supports_profile(const std::string& device,
                                         const Uuid& profile) {
  int size = STORAGE_UUID_STRING_SIZE * BT_MAX_NUM_UUIDS;
  char uuid_str[size];
  if (btif_config_get_str(device, BTIF_STORAGE_KEY_REMOTE_SERVICE, uuid_str,
                          &size)) {
    Uuid p_uuid[BT_MAX_NUM_UUIDS];
    size_t num_uuids =
        btif_split_uuids_string(uuid_str, p_uuid, BT_MAX_NUM_UUIDS);
    for (size_t i = 0; i < num_uuids; i++) {
      if (p_uuid[i] == profile) {
        return true;
      }
    }
  }

  return false;
}

static bool btif_device_supports_hogp(const std::string& device) {
  return btif_device_supports_profile(device,
                                      Uuid::From16Bit(UUID_SERVCLASS_LE_HID));
}

static bool btif_device_supports_classic_hid(const std::string& device) {
  return btif_device_supports_profile(
      device, Uuid::From16Bit(UUID_SERVCLASS_HUMAN_INTERFACE));
}

/*******************************************************************************
 *
 * Function         btif_storage_get_le_hid_devices
 *
 * Description      BTIF storage API - Finds all bonded LE HID devices
 *
 * Returns          std::vector of (RawAddress, AddressType)
 *
 ******************************************************************************/

bool btif_get_address_type(const RawAddress& bda, tBLE_ADDR_TYPE* p_addr_type);

std::vector<std::pair<RawAddress, uint8_t>> btif_storage_get_le_hid_devices(
    void) {
  std::vector<std::pair<RawAddress, uint8_t>> hid_addresses;
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();
    if (btif_device_supports_hogp(name)) {
      tBLE_ADDR_TYPE type = BLE_ADDR_PUBLIC;
      btif_get_address_type(bd_addr, &type);

      hid_addresses.push_back({bd_addr, type});
      log::debug("Remote device: {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }

  return hid_addresses;
}

std::vector<RawAddress> btif_storage_get_wake_capable_classic_hid_devices(
    void) {
  std::vector<RawAddress> hid_addresses;
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();
    if (btif_device_supports_classic_hid(name)) {
      // Filter out devices that aren't keyboards or pointing devices.
      // 0x500 = HID Major
      // 0x080 = Pointing device
      // 0x040 = Keyboard
      constexpr int kHidMask = COD_HID_MAJOR;
      constexpr int kKeyboardMouseMask = COD_HID_COMBO & ~COD_HID_MAJOR;
      int cod_value;
      if (!btif_config_get_int(name, BTIF_STORAGE_KEY_DEV_CLASS, &cod_value) ||
          (cod_value & kHidMask) != kHidMask ||
          (cod_value & kKeyboardMouseMask) == 0) {
        continue;
      }

      hid_addresses.push_back(bd_addr);
      log::debug("Remote device: {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }

  return hid_addresses;
}

void btif_storage_add_hearing_aid(const HearingDevice& dev_info) {
  do_in_jni_thread(
      FROM_HERE,
      Bind(
          [](const HearingDevice& dev_info) {
            std::string bdstr = dev_info.address.ToString();
            log::verbose("saving hearing aid device: {}",
                         ADDRESS_TO_LOGGABLE_STR(dev_info.address));
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_HEARING_AID_SERVICE_CHANGED_CCC_HANDLE,
                dev_info.service_changed_ccc_handle);
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_HEARING_AID_READ_PSM_HANDLE,
                                dev_info.read_psm_handle);
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_HEARING_AID_CAPABILITIES,
                                dev_info.capabilities);
            btif_config_set_int(bdstr, BTIF_STORAGE_KEY_HEARING_AID_CODECS,
                                dev_info.codecs);
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_CONTROL_POINT,
                dev_info.audio_control_point_handle);
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_HEARING_AID_VOLUME_HANDLE,
                                dev_info.volume_handle);
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_STATUS_HANDLE,
                dev_info.audio_status_handle);
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_STATUS_CCC_HANDLE,
                dev_info.audio_status_ccc_handle);
            btif_config_set_uint64(bdstr, BTIF_STORAGE_KEY_HEARING_AID_SYNC_ID,
                                   dev_info.hi_sync_id);
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_HEARING_AID_RENDER_DELAY,
                                dev_info.render_delay);
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_HEARING_AID_PREPARATION_DELAY,
                                dev_info.preparation_delay);
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_HEARING_AID_IS_ACCEPTLISTED, true);
          },
          dev_info));
}

/** Loads information about bonded hearing aid devices */
void btif_storage_load_bonded_hearing_aids() {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    const std::string& name = bd_addr.ToString();

    int size = STORAGE_UUID_STRING_SIZE * BT_MAX_NUM_UUIDS;
    char uuid_str[size];
    bool isHearingaidDevice = false;
    if (btif_config_get_str(name, BTIF_STORAGE_KEY_REMOTE_SERVICE, uuid_str,
                            &size)) {
      Uuid p_uuid[BT_MAX_NUM_UUIDS];
      size_t num_uuids =
          btif_split_uuids_string(uuid_str, p_uuid, BT_MAX_NUM_UUIDS);
      for (size_t i = 0; i < num_uuids; i++) {
        if (p_uuid[i] == Uuid::FromString("FDF0")) {
          isHearingaidDevice = true;
          break;
        }
      }
    }
    if (!isHearingaidDevice) {
      continue;
    }

    log::verbose("Remote device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    if (btif_in_fetch_bonded_device(name) != BT_STATUS_SUCCESS) {
      btif_storage_remove_hearing_aid(bd_addr);
      continue;
    }

    int value;
    uint8_t capabilities = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_HEARING_AID_CAPABILITIES,
                            &value))
      capabilities = value;

    uint16_t codecs = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_HEARING_AID_CODECS, &value))
      codecs = value;

    uint16_t audio_control_point_handle = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_CONTROL_POINT, &value))
      audio_control_point_handle = value;

    uint16_t audio_status_handle = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_STATUS_HANDLE, &value))
      audio_status_handle = value;

    uint16_t audio_status_ccc_handle = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_STATUS_CCC_HANDLE, &value))
      audio_status_ccc_handle = value;

    uint16_t service_changed_ccc_handle = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_HEARING_AID_SERVICE_CHANGED_CCC_HANDLE,
            &value))
      service_changed_ccc_handle = value;

    uint16_t volume_handle = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_HEARING_AID_VOLUME_HANDLE,
                            &value))
      volume_handle = value;

    uint16_t read_psm_handle = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_HEARING_AID_READ_PSM_HANDLE,
                            &value))
      read_psm_handle = value;

    uint64_t lvalue;
    uint64_t hi_sync_id = 0;
    if (btif_config_get_uint64(name, BTIF_STORAGE_KEY_HEARING_AID_SYNC_ID,
                               &lvalue))
      hi_sync_id = lvalue;

    uint16_t render_delay = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_HEARING_AID_RENDER_DELAY,
                            &value))
      render_delay = value;

    uint16_t preparation_delay = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_HEARING_AID_PREPARATION_DELAY, &value))
      preparation_delay = value;

    bool is_acceptlisted = false;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_HEARING_AID_IS_ACCEPTLISTED,
                            &value))
      is_acceptlisted = value;

    // add extracted information to BTA Hearing Aid
    do_in_main_thread(
        FROM_HERE,
        Bind(&HearingAid::AddFromStorage,
             HearingDevice(bd_addr, capabilities, codecs,
                           audio_control_point_handle, audio_status_handle,
                           audio_status_ccc_handle, service_changed_ccc_handle,
                           volume_handle, read_psm_handle, hi_sync_id,
                           render_delay, preparation_delay),
             is_acceptlisted));
  }
}

/** Deletes the bonded hearing aid device info from NVRAM */
void btif_storage_remove_hearing_aid(const RawAddress& address) {
  std::string addrstr = address.ToString();
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_READ_PSM_HANDLE);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_CAPABILITIES);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_CODECS);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_CONTROL_POINT);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_VOLUME_HANDLE);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_AUDIO_STATUS_HANDLE);
  btif_config_remove(addrstr,
                     BTIF_STORAGE_KEY_HEARING_AID_AUDIO_STATUS_CCC_HANDLE);
  btif_config_remove(addrstr,
                     BTIF_STORAGE_KEY_HEARING_AID_SERVICE_CHANGED_CCC_HANDLE);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_SYNC_ID);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_RENDER_DELAY);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_PREPARATION_DELAY);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_HEARING_AID_IS_ACCEPTLISTED);
}

/** Set/Unset the hearing aid device HEARING_AID_IS_ACCEPTLISTED flag. */
void btif_storage_set_hearing_aid_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist) {
  std::string addrstr = address.ToString();

  btif_config_set_int(addrstr, BTIF_STORAGE_KEY_HEARING_AID_IS_ACCEPTLISTED,
                      add_to_acceptlist);
}

/** Get the hearing aid device properties. */
bool btif_storage_get_hearing_aid_prop(
    const RawAddress& address, uint8_t* capabilities, uint64_t* hi_sync_id,
    uint16_t* render_delay, uint16_t* preparation_delay, uint16_t* codecs) {
  std::string addrstr = address.ToString();

  int value;
  if (btif_config_get_int(addrstr, BTIF_STORAGE_KEY_HEARING_AID_CAPABILITIES,
                          &value)) {
    *capabilities = value;
  } else {
    return false;
  }

  if (btif_config_get_int(addrstr, BTIF_STORAGE_KEY_HEARING_AID_CODECS,
                          &value)) {
    *codecs = value;
  } else {
    return false;
  }

  if (btif_config_get_int(addrstr, BTIF_STORAGE_KEY_HEARING_AID_RENDER_DELAY,
                          &value)) {
    *render_delay = value;
  } else {
    return false;
  }

  if (btif_config_get_int(
          addrstr, BTIF_STORAGE_KEY_HEARING_AID_PREPARATION_DELAY, &value)) {
    *preparation_delay = value;
  } else {
    return false;
  }

  uint64_t lvalue;
  if (btif_config_get_uint64(addrstr, BTIF_STORAGE_KEY_HEARING_AID_SYNC_ID,
                             &lvalue)) {
    *hi_sync_id = lvalue;
  } else {
    return false;
  }

  return true;
}

/** Set autoconnect information for LeAudio device */
void btif_storage_set_leaudio_autoconnect(const RawAddress& addr,
                                          bool autoconnect) {
  do_in_jni_thread(FROM_HERE, Bind(
                                  [](const RawAddress& addr, bool autoconnect) {
                                    std::string bdstr = addr.ToString();
                                    log::verbose(
                                        "saving le audio device: {}",
                                        ADDRESS_TO_LOGGABLE_CSTR(addr));
                                    btif_config_set_int(
                                        bdstr,
                                        BTIF_STORAGE_KEY_LEAUDIO_AUTOCONNECT,
                                        autoconnect);
                                  },
                                  addr, autoconnect));
}

/** Store ASEs information */
void btif_storage_leaudio_update_handles_bin(const RawAddress& addr) {
  std::vector<uint8_t> handles;

  if (LeAudioClient::GetHandlesForStorage(addr, handles)) {
    do_in_jni_thread(
        FROM_HERE,
        Bind(
            [](const RawAddress& bd_addr, std::vector<uint8_t> handles) {
              auto bdstr = bd_addr.ToString();
              btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_LEAUDIO_HANDLES_BIN,
                                  handles.data(), handles.size());
            },
            addr, std::move(handles)));
  }
}

/** Store PACs information */
void btif_storage_leaudio_update_pacs_bin(const RawAddress& addr) {
  std::vector<uint8_t> sink_pacs;

  if (LeAudioClient::GetSinkPacsForStorage(addr, sink_pacs)) {
    do_in_jni_thread(
        FROM_HERE,
        Bind(
            [](const RawAddress& bd_addr, std::vector<uint8_t> sink_pacs) {
              auto bdstr = bd_addr.ToString();
              btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_LEAUDIO_SINK_PACS_BIN,
                                  sink_pacs.data(), sink_pacs.size());
            },
            addr, std::move(sink_pacs)));
  }

  std::vector<uint8_t> source_pacs;
  if (LeAudioClient::GetSourcePacsForStorage(addr, source_pacs)) {
    do_in_jni_thread(
        FROM_HERE,
        Bind(
            [](const RawAddress& bd_addr, std::vector<uint8_t> source_pacs) {
              auto bdstr = bd_addr.ToString();
              btif_config_set_bin(bdstr,
                                  BTIF_STORAGE_KEY_LEAUDIO_SOURCE_PACS_BIN,
                                  source_pacs.data(), source_pacs.size());
            },
            addr, std::move(source_pacs)));
  }
}

/** Store ASEs information */
void btif_storage_leaudio_update_ase_bin(const RawAddress& addr) {
  std::vector<uint8_t> ases;

  if (LeAudioClient::GetAsesForStorage(addr, ases)) {
    do_in_jni_thread(
        FROM_HERE,
        Bind(
            [](const RawAddress& bd_addr, std::vector<uint8_t> ases) {
              auto bdstr = bd_addr.ToString();
              btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_LEAUDIO_ASES_BIN,
                                  ases.data(), ases.size());
            },
            addr, std::move(ases)));
  }
}

/** Store Le Audio device audio locations */
void btif_storage_set_leaudio_audio_location(const RawAddress& addr,
                                             uint32_t sink_location,
                                             uint32_t source_location) {
  do_in_jni_thread(
      FROM_HERE,
      Bind(
          [](const RawAddress& addr, int sink_location, int source_location) {
            std::string bdstr = addr.ToString();
            log::debug("saving le audio device: {}",
                       ADDRESS_TO_LOGGABLE_CSTR(addr));
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_LEAUDIO_SINK_AUDIOLOCATION,
                                sink_location);
            btif_config_set_int(bdstr,
                                BTIF_STORAGE_KEY_LEAUDIO_SOURCE_AUDIOLOCATION,
                                source_location);
          },
          addr, sink_location, source_location));
}

/** Store Le Audio device context types */
void btif_storage_set_leaudio_supported_context_types(
    const RawAddress& addr, uint16_t sink_supported_context_type,
    uint16_t source_supported_context_type) {
  do_in_jni_thread(
      FROM_HERE,
      Bind(
          [](const RawAddress& addr, int sink_supported_context_type,
             int source_supported_context_type) {
            std::string bdstr = addr.ToString();
            log::debug("saving le audio device: {}",
                       ADDRESS_TO_LOGGABLE_CSTR(addr));
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_LEAUDIO_SINK_SUPPORTED_CONTEXT_TYPE,
                sink_supported_context_type);
            btif_config_set_int(
                bdstr, BTIF_STORAGE_KEY_LEAUDIO_SOURCE_SUPPORTED_CONTEXT_TYPE,
                source_supported_context_type);
          },
          addr, sink_supported_context_type, source_supported_context_type));
}

/** Loads information about bonded Le Audio devices */
void btif_storage_load_bonded_leaudio() {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();

    int size = STORAGE_UUID_STRING_SIZE * BT_MAX_NUM_UUIDS;
    char uuid_str[size];
    bool isLeAudioDevice = false;
    if (btif_config_get_str(name, BTIF_STORAGE_KEY_REMOTE_SERVICE, uuid_str,
                            &size)) {
      Uuid p_uuid[BT_MAX_NUM_UUIDS];
      size_t num_uuids =
          btif_split_uuids_string(uuid_str, p_uuid, BT_MAX_NUM_UUIDS);
      for (size_t i = 0; i < num_uuids; i++) {
        if (p_uuid[i] == Uuid::FromString("184E")) {
          isLeAudioDevice = true;
          break;
        }
      }
    }
    if (!isLeAudioDevice) {
      continue;
    }

    log::verbose("Remote device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    int value;
    bool autoconnect = false;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_AUTOCONNECT, &value))
      autoconnect = !!value;

    int sink_audio_location = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_SINK_AUDIOLOCATION,
                            &value))
      sink_audio_location = value;

    int source_audio_location = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_SOURCE_AUDIOLOCATION,
                            &value))
      source_audio_location = value;

    int sink_supported_context_type = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_LEAUDIO_SINK_SUPPORTED_CONTEXT_TYPE, &value))
      sink_supported_context_type = value;

    int source_supported_context_type = 0;
    if (btif_config_get_int(
            name, BTIF_STORAGE_KEY_LEAUDIO_SOURCE_SUPPORTED_CONTEXT_TYPE,
            &value))
      source_supported_context_type = value;

    size_t buffer_size =
        btif_config_get_bin_length(name, BTIF_STORAGE_KEY_LEAUDIO_HANDLES_BIN);
    std::vector<uint8_t> handles(buffer_size);
    if (buffer_size > 0) {
      btif_config_get_bin(name, BTIF_STORAGE_KEY_LEAUDIO_HANDLES_BIN,
                          handles.data(), &buffer_size);
    }

    buffer_size = btif_config_get_bin_length(
        name, BTIF_STORAGE_KEY_LEAUDIO_SINK_PACS_BIN);
    std::vector<uint8_t> sink_pacs(buffer_size);
    if (buffer_size > 0) {
      btif_config_get_bin(name, BTIF_STORAGE_KEY_LEAUDIO_SINK_PACS_BIN,
                          sink_pacs.data(), &buffer_size);
    }

    buffer_size = btif_config_get_bin_length(
        name, BTIF_STORAGE_KEY_LEAUDIO_SOURCE_PACS_BIN);
    std::vector<uint8_t> source_pacs(buffer_size);
    if (buffer_size > 0) {
      btif_config_get_bin(name, BTIF_STORAGE_KEY_LEAUDIO_SOURCE_PACS_BIN,
                          source_pacs.data(), &buffer_size);
    }

    buffer_size =
        btif_config_get_bin_length(name, BTIF_STORAGE_KEY_LEAUDIO_ASES_BIN);
    std::vector<uint8_t> ases(buffer_size);
    if (buffer_size > 0) {
      btif_config_get_bin(name, BTIF_STORAGE_KEY_LEAUDIO_ASES_BIN, ases.data(),
                          &buffer_size);
    }

    do_in_main_thread(
        FROM_HERE,
        Bind(&LeAudioClient::AddFromStorage, bd_addr, autoconnect,
             sink_audio_location, source_audio_location,
             sink_supported_context_type, source_supported_context_type,
             std::move(handles), std::move(sink_pacs), std::move(source_pacs),
             std::move(ases)));
  }
}

void btif_storage_leaudio_clear_service_data(const RawAddress& address) {
  auto bdstr = address.ToString();
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_LEAUDIO_HANDLES_BIN);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_LEAUDIO_SINK_PACS_BIN);
  btif_config_remove(bdstr, BTIF_STORAGE_KEY_LEAUDIO_ASES_BIN);
}

/** Remove the Le Audio device from storage */
void btif_storage_remove_leaudio(const RawAddress& address) {
  std::string addrstr = address.ToString();
  btif_config_set_int(addrstr, BTIF_STORAGE_KEY_LEAUDIO_AUTOCONNECT, false);
}

void btif_storage_add_leaudio_has_device(const RawAddress& address,
                                         std::vector<uint8_t> presets_bin,
                                         uint8_t features,
                                         uint8_t active_preset) {
  do_in_jni_thread(
      FROM_HERE,
      Bind(
          [](const RawAddress& address, std::vector<uint8_t> presets_bin,
             uint8_t features, uint8_t active_preset) {
            const std::string& name = address.ToString();

            btif_config_set_int(name, BTIF_STORAGE_KEY_LEAUDIO_HAS_FLAGS,
                                features);
            btif_config_set_int(name,
                                BTIF_STORAGE_KEY_LEAUDIO_HAS_ACTIVE_PRESET,
                                active_preset);
            btif_config_set_bin(name,
                                BTIF_STORAGE_KEY_LEAUDIO_HAS_SERIALIZED_PRESETS,
                                presets_bin.data(), presets_bin.size());

            btif_config_set_int(
                name, BTIF_STORAGE_KEY_LEAUDIO_HAS_IS_ACCEPTLISTED, true);
          },
          address, std::move(presets_bin), features, active_preset));
}

void btif_storage_set_leaudio_has_active_preset(const RawAddress& address,
                                                uint8_t active_preset) {
  do_in_jni_thread(FROM_HERE,
                   Bind(
                       [](const RawAddress& address, uint8_t active_preset) {
                         const std::string& name = address.ToString();

                         btif_config_set_int(
                             name, BTIF_STORAGE_KEY_LEAUDIO_HAS_ACTIVE_PRESET,
                             active_preset);
                       },
                       address, active_preset));
}

bool btif_storage_get_leaudio_has_features(const RawAddress& address,
                                           uint8_t& features) {
  std::string name = address.ToString();

  int value;
  if (!btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_HAS_FLAGS, &value))
    return false;

  features = value;
  return true;
}

void btif_storage_set_leaudio_has_features(const RawAddress& address,
                                           uint8_t features) {
  do_in_jni_thread(FROM_HERE,
                   Bind(
                       [](const RawAddress& address, uint8_t features) {
                         const std::string& name = address.ToString();

                         btif_config_set_int(name,
                                             BTIF_STORAGE_KEY_LEAUDIO_HAS_FLAGS,
                                             features);
                       },
                       address, features));
}

void btif_storage_load_bonded_leaudio_has_devices() {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    const std::string& name = bd_addr.ToString();

    if (!btif_config_exist(name,
                           BTIF_STORAGE_KEY_LEAUDIO_HAS_IS_ACCEPTLISTED) &&
        !btif_config_exist(name, BTIF_STORAGE_KEY_LEAUDIO_HAS_FLAGS))
      continue;

#ifndef TARGET_FLOSS
    int value;
    uint16_t is_acceptlisted = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_HAS_IS_ACCEPTLISTED,
                            &value))
      is_acceptlisted = value;

    uint8_t features = 0;
    if (btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_HAS_FLAGS, &value))
      features = value;

    do_in_main_thread(FROM_HERE,
                      Bind(&::le_audio::has::HasClient::AddFromStorage, bd_addr,
                           features, is_acceptlisted));
#else
    ASSERT_LOG(false, "TODO - Fix LE audio build.");
#endif
  }
}

void btif_storage_remove_leaudio_has(const RawAddress& address) {
  std::string addrstr = address.ToString();
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_LEAUDIO_HAS_IS_ACCEPTLISTED);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_LEAUDIO_HAS_FLAGS);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_LEAUDIO_HAS_ACTIVE_PRESET);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_LEAUDIO_HAS_SERIALIZED_PRESETS);
}

void btif_storage_set_leaudio_has_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist) {
  std::string addrstr = address.ToString();

  btif_config_set_int(addrstr, BTIF_STORAGE_KEY_LEAUDIO_HAS_IS_ACCEPTLISTED,
                      add_to_acceptlist);
}

void btif_storage_set_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t> presets_bin) {
  do_in_jni_thread(
      FROM_HERE,
      Bind(
          [](const RawAddress& address, std::vector<uint8_t> presets_bin) {
            const std::string& name = address.ToString();

            btif_config_set_bin(name,
                                BTIF_STORAGE_KEY_LEAUDIO_HAS_SERIALIZED_PRESETS,
                                presets_bin.data(), presets_bin.size());
          },
          address, std::move(presets_bin)));
}

bool btif_storage_get_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t>& presets_bin,
                                          uint8_t& active_preset) {
  std::string name = address.ToString();

  int value;
  if (!btif_config_get_int(name, BTIF_STORAGE_KEY_LEAUDIO_HAS_ACTIVE_PRESET,
                           &value))
    return false;
  active_preset = value;

  auto bin_sz = btif_config_get_bin_length(
      name, BTIF_STORAGE_KEY_LEAUDIO_HAS_SERIALIZED_PRESETS);
  presets_bin.resize(bin_sz);
  if (!btif_config_get_bin(name,
                           BTIF_STORAGE_KEY_LEAUDIO_HAS_SERIALIZED_PRESETS,
                           presets_bin.data(), &bin_sz))
    return false;

  return true;
}

/** Adds the bonded Le Audio device grouping info into the NVRAM */
void btif_storage_add_groups(const RawAddress& addr) {
  std::vector<uint8_t> group_info;
  auto not_empty = DeviceGroups::GetForStorage(addr, group_info);

  if (not_empty)
    do_in_jni_thread(
        FROM_HERE,
        Bind(
            [](const RawAddress& bd_addr, std::vector<uint8_t> group_info) {
              auto bdstr = bd_addr.ToString();
              btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_DEVICE_GROUP_BIN,
                                  group_info.data(), group_info.size());
            },
            addr, std::move(group_info)));
}

/** Deletes the bonded Le Audio device grouping info from the NVRAM */
void btif_storage_remove_groups(const RawAddress& address) {
  std::string addrstr = address.ToString();
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_DEVICE_GROUP_BIN);
}

/** Loads information about bonded group devices */
void btif_storage_load_bonded_groups(void) {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();
    size_t buffer_size =
        btif_config_get_bin_length(name, BTIF_STORAGE_KEY_DEVICE_GROUP_BIN);
    if (buffer_size == 0) continue;

    log::verbose("Grouped device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    std::vector<uint8_t> in(buffer_size);
    if (btif_config_get_bin(name, BTIF_STORAGE_KEY_DEVICE_GROUP_BIN, in.data(),
                            &buffer_size)) {
      do_in_main_thread(FROM_HERE, Bind(&DeviceGroups::AddFromStorage, bd_addr,
                                        std::move(in)));
    }
  }
}

/** Loads information about bonded group devices */
void btif_storage_load_bonded_volume_control_devices(void) {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto device = bd_addr.ToString();
    if (btif_device_supports_profile(
            device, Uuid::From16Bit(UUID_SERVCLASS_VOLUME_CONTROL_SERVER))) {
      do_in_main_thread(FROM_HERE,
                        Bind(&VolumeControl::AddFromStorage, bd_addr));
    }
  }
}

/** Stores information about the bonded CSIS device */
void btif_storage_update_csis_info(const RawAddress& addr) {
  std::vector<uint8_t> set_info;
  auto not_empty = CsisClient::GetForStorage(addr, set_info);

  if (not_empty)
    do_in_jni_thread(
        FROM_HERE,
        Bind(
            [](const RawAddress& bd_addr, std::vector<uint8_t> set_info) {
              auto bdstr = bd_addr.ToString();
              btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_CSIS_SET_INFO_BIN,
                                  set_info.data(), set_info.size());
            },
            addr, std::move(set_info)));
}

/** Loads information about the bonded CSIS device */
void btif_storage_load_bonded_csis_devices(void) {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();

    log::verbose("Loading CSIS device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    size_t buffer_size =
        btif_config_get_bin_length(name, BTIF_STORAGE_KEY_CSIS_SET_INFO_BIN);
    std::vector<uint8_t> in(buffer_size);
    if (buffer_size != 0)
      btif_config_get_bin(name, BTIF_STORAGE_KEY_CSIS_SET_INFO_BIN, in.data(),
                          &buffer_size);

    if (buffer_size != 0)
      do_in_main_thread(
          FROM_HERE, Bind(&CsisClient::AddFromStorage, bd_addr, std::move(in)));
  }
}

/** Removes information about the bonded CSIS device */
void btif_storage_remove_csis_device(const RawAddress& address) {
  std::string addrstr = address.ToString();
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_CSIS_AUTOCONNECT);
  btif_config_remove(addrstr, BTIF_STORAGE_KEY_CSIS_SET_INFO_BIN);
}

/*******************************************************************************
 * Function         btif_storage_load_hidd
 *
 * Description      Loads hidd bonded device and "plugs" it into hidd
 *
 * Returns          BT_STATUS_SUCCESS if successful, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_load_hidd(void) {
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();

    log::verbose("Remote device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    int value;
    if (btif_in_fetch_bonded_device(name) == BT_STATUS_SUCCESS) {
      if (btif_config_get_int(name, BTIF_STORAGE_KEY_HID_DEVICE_CABLED,
                              &value)) {
        BTA_HdAddDevice(bd_addr);
        break;
      }
    }
  }

  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_storage_set_hidd
 *
 * Description      Stores currently used HIDD device info in nvram and remove
 *                  the "HidDeviceCabled" flag from unused devices
 *
 * Returns          BT_STATUS_SUCCESS
 *
 ******************************************************************************/
bt_status_t btif_storage_set_hidd(const RawAddress& remote_bd_addr) {
  std::string remote_device_address_string = remote_bd_addr.ToString();
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();
    if (bd_addr == remote_bd_addr) continue;
    if (btif_in_fetch_bonded_device(name) == BT_STATUS_SUCCESS) {
      btif_config_remove(name, BTIF_STORAGE_KEY_HID_DEVICE_CABLED);
    }
  }

  btif_config_set_int(remote_device_address_string,
                      BTIF_STORAGE_KEY_HID_DEVICE_CABLED, 1);
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_storage_remove_hidd
 *
 * Description      Removes hidd bonded device info from nvram
 *
 * Returns          BT_STATUS_SUCCESS
 *
 ******************************************************************************/
bt_status_t btif_storage_remove_hidd(RawAddress* remote_bd_addr) {
  btif_config_remove(remote_bd_addr->ToString(),
                     BTIF_STORAGE_KEY_HID_DEVICE_CABLED);

  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 *Function : btif_storage_set_pce_profile_version
 *
 * Description :
 *    This function store remote PCE profile version in config file
 *
 ******************************************************************************/
void btif_storage_set_pce_profile_version(const RawAddress& remote_bd_addr,
                                          uint16_t peer_pce_version) {
  log::verbose("peer_pce_version : 0x{:x}", peer_pce_version);

  if (btif_config_set_bin(
          remote_bd_addr.ToString(), BTIF_STORAGE_KEY_PBAP_PCE_VERSION,
          (const uint8_t*)&peer_pce_version, sizeof(peer_pce_version))) {
  } else {
    log::warn("Failed to store  peer_pce_version for {}",
              ADDRESS_TO_LOGGABLE_CSTR(remote_bd_addr));
  }
}

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
bool btif_storage_is_pce_version_102(const RawAddress& remote_bd_addr) {
  bool entry_found = false;
  // Read and restore the PBAP PCE version from local storage
  uint16_t pce_version = 0;
  size_t version_value_size = sizeof(pce_version);
  if (!btif_config_get_bin(remote_bd_addr.ToString(),
                           BTIF_STORAGE_KEY_PBAP_PCE_VERSION,
                           (uint8_t*)&pce_version, &version_value_size)) {
    log::verbose("Failed to read cached peer PCE version for {}",
                 ADDRESS_TO_LOGGABLE_CSTR(remote_bd_addr));
    return entry_found;
  }

  if (pce_version == 0x0102) {
    entry_found = true;
  }

  log::verbose("read cached peer PCE version 0x{:04x} for {}", pce_version,
               ADDRESS_TO_LOGGABLE_CSTR(remote_bd_addr));

  return entry_found;
}
