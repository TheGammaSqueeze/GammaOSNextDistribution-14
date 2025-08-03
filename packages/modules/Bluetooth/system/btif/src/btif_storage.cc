/******************************************************************************
 *
 *  Copyright (c) 2014 The Android Open Source Project
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
 *  Filename:      btif_storage.c
 *
 *  Description:   Stores the local BT adapter and remote device properties in
 *                 NVRAM storage, typically as xml file in the
 *                 mobile's filesystem
 *
 *
 */

#define LOG_TAG "bt_btif_storage"

#include "btif_storage.h"

#include <alloca.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <unordered_set>
#include <vector>

#include "btif/include/stack_manager_t.h"
#include "btif_api.h"
#include "btif_config.h"
#include "btif_storage.h"
#include "btif_util.h"
#include "common/init_flags.h"
#include "core_callbacks.h"
#include "device/include/controller.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_uuid16.h"
#include "storage/config_keys.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/* This is a local property to add a device found */
#define BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP 0xFF

using base::Bind;
using bluetooth::Uuid;
using namespace bluetooth;

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/

struct BtifStorageKey {
  uint8_t type;
  const std::string& name;
  uint8_t size;
};
static const BtifStorageKey BTIF_STORAGE_LE_KEYS[] = {
    {BTM_LE_KEY_PENC, BTIF_STORAGE_KEY_LE_KEY_PENC, sizeof(tBTM_LE_PENC_KEYS)},
    {BTM_LE_KEY_PID, BTIF_STORAGE_KEY_LE_KEY_PID, sizeof(tBTM_LE_PID_KEYS)},
    {BTM_LE_KEY_PCSRK, BTIF_STORAGE_KEY_LE_KEY_PCSRK,
     sizeof(tBTM_LE_PCSRK_KEYS)},
    {BTM_LE_KEY_LENC, BTIF_STORAGE_KEY_LE_KEY_LENC, sizeof(tBTM_LE_LENC_KEYS)},
    {BTM_LE_KEY_LCSRK, BTIF_STORAGE_KEY_LE_KEY_LCSRK,
     sizeof(tBTM_LE_LCSRK_KEYS)},
    {BTM_LE_KEY_LID, BTIF_STORAGE_KEY_LE_KEY_LID, sizeof(tBTM_LE_PID_KEYS)},
};
static const BtifStorageKey BTIF_STORAGE_LOCAL_LE_KEYS[] = {
    {BTIF_DM_LE_LOCAL_KEY_IR, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IR,
     sizeof(Octet16)},
    {BTIF_DM_LE_LOCAL_KEY_IRK, BTIF_STORAGE_KEY_LE_LOCAL_KEY_IRK,
     sizeof(Octet16)},
    {BTIF_DM_LE_LOCAL_KEY_DHK, BTIF_STORAGE_KEY_LE_LOCAL_KEY_DHK,
     sizeof(Octet16)},
    {BTIF_DM_LE_LOCAL_KEY_ER, BTIF_STORAGE_KEY_LE_LOCAL_KEY_ER,
     sizeof(Octet16)},
};

/*******************************************************************************
 *  External functions
 ******************************************************************************/

void btif_gatts_add_bonded_dev_from_nv(const RawAddress& bda);

/*******************************************************************************
 *  Internal Functions
 ******************************************************************************/

static bool btif_has_ble_keys(const std::string& bdstr);

/*******************************************************************************
 *  Static functions
 ******************************************************************************/

static void btif_storage_set_mode(RawAddress* remote_bd_addr) {
  std::string bdstr = remote_bd_addr->ToString();
  if (GetInterfaceToProfiles()->config->isRestrictedMode()) {
    log::info("{} will be removed exiting restricted mode",
              ADDRESS_TO_LOGGABLE_CSTR(*remote_bd_addr));
    btif_config_set_int(bdstr, BTIF_STORAGE_KEY_RESTRICTED, 1);
  }
}

static bool prop2cfg(const RawAddress* remote_bd_addr, bt_property_t* prop) {
  std::string bdstr;
  if (remote_bd_addr) {
    bdstr = remote_bd_addr->ToString();
  }

  char value[1024];
  if (prop->len <= 0 || prop->len > (int)sizeof(value) - 1) {
    log::warn(
        "Unable to save property to configuration file type:{},  len:{} is "
        "invalid",
        prop->type, prop->len);
    return false;
  }
  switch (prop->type) {
    case BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP:
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_TIMESTAMP, (int)time(NULL));
      break;
    case BT_PROPERTY_BDNAME: {
      int name_length = prop->len > BTM_MAX_LOC_BD_NAME_LEN
                            ? BTM_MAX_LOC_BD_NAME_LEN
                            : prop->len;
      strncpy(value, (char*)prop->val, name_length);
      value[name_length] = '\0';
      if (remote_bd_addr) {
        btif_config_set_str(bdstr, BTIF_STORAGE_KEY_NAME, value);
      } else {
        btif_config_set_str(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_NAME,
                            value);
      }
      break;
    }
    case BT_PROPERTY_REMOTE_FRIENDLY_NAME:
      strncpy(value, (char*)prop->val, prop->len);
      value[prop->len] = '\0';
      btif_config_set_str(bdstr, BTIF_STORAGE_KEY_ALIAS, value);
      break;
    case BT_PROPERTY_ADAPTER_SCAN_MODE:
      btif_config_set_int(BTIF_STORAGE_SECTION_ADAPTER,
                          BTIF_STORAGE_KEY_SCANMODE, *(int*)prop->val);
      break;
    case BT_PROPERTY_LOCAL_IO_CAPS:
      btif_config_set_int(BTIF_STORAGE_SECTION_ADAPTER,
                          BTIF_STORAGE_KEY_LOCAL_IO_CAPS, *(int*)prop->val);
      break;
    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT:
      btif_config_set_int(BTIF_STORAGE_SECTION_ADAPTER,
                          BTIF_STORAGE_KEY_DISC_TIMEOUT, *(int*)prop->val);
      break;
    case BT_PROPERTY_CLASS_OF_DEVICE:
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_DEV_CLASS, *(int*)prop->val);
      break;
    case BT_PROPERTY_TYPE_OF_DEVICE:
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_DEV_TYPE, *(int*)prop->val);
      break;
    case BT_PROPERTY_UUIDS: {
      std::string val;
      size_t cnt = (prop->len) / sizeof(Uuid);
      for (size_t i = 0; i < cnt; i++) {
        val += (reinterpret_cast<Uuid*>(prop->val) + i)->ToString() + " ";
      }
      btif_config_set_str(bdstr, BTIF_STORAGE_KEY_REMOTE_SERVICE, val);
      break;
    }
    case BT_PROPERTY_REMOTE_VERSION_INFO: {
      bt_remote_version_t* info = (bt_remote_version_t*)prop->val;

      if (!info) return false;

      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_REMOTE_VER_MFCT,
                          info->manufacturer);
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_REMOTE_VER_VER,
                          info->version);
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_REMOTE_VER_SUBVER,
                          info->sub_ver);
    } break;
    case BT_PROPERTY_APPEARANCE: {
      int val = *(uint16_t*)prop->val;
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_APPEARANCE, val);
    } break;
    case BT_PROPERTY_VENDOR_PRODUCT_INFO: {
      bt_vendor_product_info_t* info = (bt_vendor_product_info_t*)prop->val;
      if (!info) return false;

      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_VENDOR_ID_SOURCE,
                          info->vendor_id_src);
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_VENDOR_ID, info->vendor_id);
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_PRODUCT_ID, info->product_id);
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_VERSION, info->version);
    } break;
    case BT_PROPERTY_REMOTE_MODEL_NUM: {
      strncpy(value, (char*)prop->val, prop->len);
      value[prop->len] = '\0';
      btif_config_set_str(bdstr, BTIF_STORAGE_KEY_DIS_MODEL_NUM, value);
    } break;
    case BT_PROPERTY_REMOTE_SECURE_CONNECTIONS_SUPPORTED:
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_SECURE_CONNECTIONS_SUPPORTED,
                          *(uint8_t*)prop->val);
      break;
    case BT_PROPERTY_REMOTE_MAX_SESSION_KEY_SIZE:
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_MAX_SESSION_KEY_SIZE,
                          *(uint8_t*)prop->val);
      break;
    default:
      log::error("Unknown prop type:{}", prop->type);
      return false;
  }

  return true;
}

static bool cfg2prop(const RawAddress* remote_bd_addr, bt_property_t* prop) {
  std::string bdstr;
  if (remote_bd_addr) {
    bdstr = remote_bd_addr->ToString();
  }
  if (prop->len <= 0) {
    log::warn("Invalid property read from configuration file type:{}, len:{}",
              prop->type, prop->len);
    return false;
  }
  bool ret = false;
  switch (prop->type) {
    case BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP:
      if (prop->len >= (int)sizeof(int))
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_TIMESTAMP,
                                  (int*)prop->val);
      break;
    case BT_PROPERTY_BDNAME: {
      int len = prop->len;
      if (remote_bd_addr)
        ret = btif_config_get_str(bdstr, BTIF_STORAGE_KEY_NAME,
                                  (char*)prop->val, &len);
      else
        ret =
            btif_config_get_str(BTIF_STORAGE_SECTION_ADAPTER,
                                BTIF_STORAGE_KEY_NAME, (char*)prop->val, &len);
      if (ret && len && len <= prop->len)
        prop->len = len - 1;
      else {
        prop->len = 0;
        ret = false;
      }
      break;
    }
    case BT_PROPERTY_REMOTE_FRIENDLY_NAME: {
      int len = prop->len;
      ret = btif_config_get_str(bdstr, BTIF_STORAGE_KEY_ALIAS, (char*)prop->val,
                                &len);
      if (ret && len && len <= prop->len)
        prop->len = len - 1;
      else {
        prop->len = 0;
        ret = false;
      }
      break;
    }
    case BT_PROPERTY_ADAPTER_SCAN_MODE:
      if (prop->len >= (int)sizeof(int))
        ret = btif_config_get_int(BTIF_STORAGE_SECTION_ADAPTER,
                                  BTIF_STORAGE_KEY_SCANMODE, (int*)prop->val);
      break;

    case BT_PROPERTY_LOCAL_IO_CAPS:
      if (prop->len >= (int)sizeof(int))
        ret = btif_config_get_int(BTIF_STORAGE_SECTION_ADAPTER,
                                  BTIF_STORAGE_KEY_LOCAL_IO_CAPS,
                                  (int*)prop->val);
      break;
    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT:
      if (prop->len >= (int)sizeof(int))
        ret =
            btif_config_get_int(BTIF_STORAGE_SECTION_ADAPTER,
                                BTIF_STORAGE_KEY_DISC_TIMEOUT, (int*)prop->val);
      break;
    case BT_PROPERTY_CLASS_OF_DEVICE:
      if (prop->len >= (int)sizeof(int))
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_DEV_CLASS,
                                  (int*)prop->val);
      break;
    case BT_PROPERTY_TYPE_OF_DEVICE:
      if (prop->len >= (int)sizeof(int))
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_DEV_TYPE,
                                  (int*)prop->val);
      break;
    case BT_PROPERTY_UUIDS: {
      char value[1280];
      int size = sizeof(value);
      if (btif_config_get_str(bdstr, BTIF_STORAGE_KEY_REMOTE_SERVICE, value,
                              &size)) {
        Uuid* p_uuid = reinterpret_cast<Uuid*>(prop->val);
        size_t num_uuids =
            btif_split_uuids_string(value, p_uuid, BT_MAX_NUM_UUIDS);
        prop->len = num_uuids * sizeof(Uuid);
        ret = true;
      } else {
        prop->val = NULL;
        prop->len = 0;
      }
    } break;

    case BT_PROPERTY_REMOTE_VERSION_INFO: {
      bt_remote_version_t* info = (bt_remote_version_t*)prop->val;

      if (prop->len >= (int)sizeof(bt_remote_version_t)) {
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_REMOTE_VER_MFCT,
                                  &info->manufacturer);

        if (ret)
          ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_REMOTE_VER_VER,
                                    &info->version);

        if (ret)
          ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_REMOTE_VER_SUBVER,
                                    &info->sub_ver);
      }
    } break;

    case BT_PROPERTY_APPEARANCE: {
      int val;

      if (prop->len >= (int)sizeof(uint16_t)) {
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_APPEARANCE, &val);
        *(uint16_t*)prop->val = (uint16_t)val;
      }
    } break;

    case BT_PROPERTY_VENDOR_PRODUCT_INFO: {
      bt_vendor_product_info_t* info = (bt_vendor_product_info_t*)prop->val;
      int val;

      if (prop->len >= (int)sizeof(bt_vendor_product_info_t)) {
        ret =
            btif_config_get_int(bdstr, BTIF_STORAGE_KEY_VENDOR_ID_SOURCE, &val);
        info->vendor_id_src = (uint8_t)val;

        if (ret) {
          ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_VENDOR_ID, &val);
          info->vendor_id = (uint16_t)val;
        }
        if (ret) {
          ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_PRODUCT_ID, &val);
          info->product_id = (uint16_t)val;
        }
        if (ret) {
          ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_VERSION, &val);
          info->version = (uint16_t)val;
        }
      }
    } break;

    case BT_PROPERTY_REMOTE_MODEL_NUM: {
      int len = prop->len;
      ret = btif_config_get_str(bdstr, BTIF_STORAGE_KEY_DIS_MODEL_NUM,
                                (char*)prop->val, &len);
      if (ret && len && len <= prop->len)
        prop->len = len - 1;
      else {
        prop->len = 0;
        ret = false;
      }
    } break;

    case BT_PROPERTY_REMOTE_ADDR_TYPE: {
      int val;

      if (prop->len >= (int)sizeof(uint8_t)) {
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_ADDR_TYPE, &val);
        *(uint8_t*)prop->val = (uint8_t)val;
      }
    } break;

    case BT_PROPERTY_REMOTE_SECURE_CONNECTIONS_SUPPORTED: {
      int val;

      if (prop->len >= (int)sizeof(uint8_t)) {
        ret = btif_config_get_int(
            bdstr, BTIF_STORAGE_KEY_SECURE_CONNECTIONS_SUPPORTED, &val);
        *(uint8_t*)prop->val = (uint8_t)val;
      }
    } break;

    case BT_PROPERTY_REMOTE_MAX_SESSION_KEY_SIZE: {
      int val;

      if (prop->len >= (int)sizeof(uint8_t)) {
        ret = btif_config_get_int(bdstr, BTIF_STORAGE_KEY_MAX_SESSION_KEY_SIZE,
                                  &val);
        *(uint8_t*)prop->val = (uint8_t)val;
      }
    } break;

    default:
      log::error("Unknown prop type:{}", prop->type);
      return false;
  }
  return ret;
}

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
bt_status_t btif_in_fetch_bonded_device(const std::string& bdstr) {
  bool bt_linkkey_file_found = false;

  LinkKey link_key;
  size_t size = link_key.size();
  if (btif_config_get_bin(bdstr, BTIF_STORAGE_KEY_LINK_KEY, link_key.data(),
                          &size)) {
    int linkkey_type;
    if (btif_config_get_int(bdstr, BTIF_STORAGE_KEY_LINK_KEY_TYPE,
                            &linkkey_type)) {
      bt_linkkey_file_found = true;
    } else {
      bt_linkkey_file_found = false;
    }
  }
  if ((btif_in_fetch_bonded_ble_device(bdstr, false, NULL) !=
       BT_STATUS_SUCCESS) &&
      (!bt_linkkey_file_found)) {
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_in_fetch_bonded_devices
 *
 * Description      Internal helper function to fetch the bonded devices
 *                  from NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if successful, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
static bt_status_t btif_in_fetch_bonded_devices(
    btif_bonded_devices_t* p_bonded_devices, int add) {
  memset(p_bonded_devices, 0, sizeof(btif_bonded_devices_t));

  bool bt_linkkey_file_found = false;
  int device_type;

  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();

    log::verbose("Remote device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    LinkKey link_key;
    size_t size = sizeof(link_key);
    if (btif_config_get_bin(name, BTIF_STORAGE_KEY_LINK_KEY, link_key.data(),
                            &size)) {
      int linkkey_type;
      if (btif_config_get_int(name, BTIF_STORAGE_KEY_LINK_KEY_TYPE,
                              &linkkey_type)) {
        if (add) {
          DEV_CLASS dev_class = {0, 0, 0};
          int cod;
          int pin_length = 0;
          if (btif_config_get_int(name, BTIF_STORAGE_KEY_DEV_CLASS, &cod))
            dev_class = uint2devclass((uint32_t)cod);
          btif_config_get_int(name, BTIF_STORAGE_KEY_PIN_LENGTH, &pin_length);
          BTA_DmAddDevice(bd_addr, dev_class, link_key, (uint8_t)linkkey_type,
                          pin_length);

          if (btif_config_get_int(name, BTIF_STORAGE_KEY_DEV_TYPE,
                                  &device_type) &&
              (device_type == BT_DEVICE_TYPE_DUMO)) {
            btif_gatts_add_bonded_dev_from_nv(bd_addr);
          }
        }
        bt_linkkey_file_found = true;
        if (p_bonded_devices->num_devices < BTM_SEC_MAX_DEVICE_RECORDS) {
          p_bonded_devices->devices[p_bonded_devices->num_devices++] = bd_addr;
        } else {
          log::warn("Exceed the max number of bonded devices");
        }
      } else {
        bt_linkkey_file_found = false;
      }
    }
    if (!btif_in_fetch_bonded_ble_device(name, add, p_bonded_devices) &&
        !bt_linkkey_file_found) {
      log::verbose("No link key or ble key found for device:{}",
                   ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }
  return BT_STATUS_SUCCESS;
}

static void btif_read_le_key(const uint8_t key_type, const size_t key_len,
                             RawAddress bd_addr, const tBLE_ADDR_TYPE addr_type,
                             const bool add_key, bool* device_added,
                             bool* key_found) {
  CHECK(device_added);
  CHECK(key_found);

  tBTA_LE_KEY_VALUE key;
  memset(&key, 0, sizeof(key));

  if (btif_storage_get_ble_bonding_key(bd_addr, key_type, (uint8_t*)&key,
                                       key_len) == BT_STATUS_SUCCESS) {
    if (add_key) {
      if (!*device_added) {
        BTA_DmAddBleDevice(bd_addr, addr_type, BT_DEVICE_TYPE_BLE);
        *device_added = true;
      }

      log::verbose("Adding key type {} for {}", key_type,
                   ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      BTA_DmAddBleKey(bd_addr, &key, key_type);
    }

    *key_found = true;
  }
}

/*******************************************************************************
 * Functions
 *
 * Functions are synchronous and can be called by both from internal modules
 * such as BTIF_DM and by external entiries from HAL via BTIF_context_switch.
 * For OUT parameters, the caller is expected to provide the memory.
 * Caller is expected to provide a valid pointer to 'property->value' based on
 * the property->type.
 ******************************************************************************/

/*******************************************************************************
 *
 * Function         btif_split_uuids_string
 *
 * Description      Internal helper function to split the string of UUIDs
 *                  read from the NVRAM to an array
 *
 * Returns          Number of UUIDs parsed from the supplied string
 *
 ******************************************************************************/
size_t btif_split_uuids_string(const char* str, bluetooth::Uuid* p_uuid,
                               size_t max_uuids) {
  CHECK(str);
  CHECK(p_uuid);

  size_t num_uuids = 0;
  while (str && num_uuids < max_uuids) {
    bool is_valid;
    bluetooth::Uuid tmp =
        Uuid::FromString(std::string(str, Uuid::kString128BitLen), &is_valid);
    if (!is_valid) break;

    *p_uuid = tmp;
    p_uuid++;

    num_uuids++;
    str = strchr(str, ' ');
    if (str) str++;
  }

  return num_uuids;
}

/**
 * Helper function for fetching a local Input/Output capability property. If not
 * set, it returns the default value.
 */
static uint8_t btif_storage_get_io_cap_property(bt_property_type_t type,
                                                uint8_t default_value) {
  char buf[sizeof(int)];

  bt_property_t property;
  property.type = type;
  property.val = (void*)buf;
  property.len = sizeof(int);

  bt_status_t ret = btif_storage_get_adapter_property(&property);

  return (ret == BT_STATUS_SUCCESS) ? (uint8_t)(*(int*)property.val)
                                    : default_value;
}

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
tBTM_IO_CAP btif_storage_get_local_io_caps() {
  return static_cast<tBTM_IO_CAP>(btif_storage_get_io_cap_property(
      BT_PROPERTY_LOCAL_IO_CAPS, BTM_LOCAL_IO_CAPS));
}

/** Helper function for fetching a bt_property of the adapter. */
bt_status_t btif_storage_get_adapter_prop(bt_property_type_t type, void* buf,
                                          int size, bt_property_t* property) {
  property->type = type;
  property->val = buf;
  property->len = size;
  return btif_storage_get_adapter_property(property);
}

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
bt_status_t btif_storage_get_adapter_property(bt_property_t* property) {
  /* Special handling for adapter address and BONDED_DEVICES */
  if (property->type == BT_PROPERTY_BDADDR) {
    RawAddress* bd_addr = (RawAddress*)property->val;
    /* Fetch the local BD ADDR */
    const controller_t* controller = controller_get_interface();
    if (!controller->get_is_ready()) {
      log::error("Controller not ready! Unable to return Bluetooth Address");
      *bd_addr = RawAddress::kEmpty;
      return BT_STATUS_FAIL;
    } else {
      log::info("Controller ready!");
      *bd_addr = *controller->get_address();
    }
    property->len = RawAddress::kLength;
    return BT_STATUS_SUCCESS;
  } else if (property->type == BT_PROPERTY_ADAPTER_BONDED_DEVICES) {
    btif_bonded_devices_t bonded_devices;

    btif_in_fetch_bonded_devices(&bonded_devices, 0);

    log::verbose(
        "BT_PROPERTY_ADAPTER_BONDED_DEVICES: Number of bonded devices={}",
        bonded_devices.num_devices);

    property->len = bonded_devices.num_devices * RawAddress::kLength;
    memcpy(property->val, bonded_devices.devices, property->len);

    /* if there are no bonded_devices, then length shall be 0 */
    return BT_STATUS_SUCCESS;
  } else if (property->type == BT_PROPERTY_UUIDS) {
    /* publish list of local supported services */
    Uuid* p_uuid = reinterpret_cast<Uuid*>(property->val);
    uint32_t num_uuids = 0;
    uint32_t i;

    tBTA_SERVICE_MASK service_mask = btif_get_enabled_services_mask();
    log::info("Service_mask=0x{:x}", service_mask);
    for (i = 0; i < BTA_MAX_SERVICE_ID; i++) {
      /* This should eventually become a function when more services are enabled
       */
      if (service_mask & (tBTA_SERVICE_MASK)(1 << i)) {
        switch (i) {
          case BTA_HFP_SERVICE_ID: {
            *(p_uuid + num_uuids) =
                Uuid::From16Bit(UUID_SERVCLASS_AG_HANDSFREE);
            num_uuids++;
          }
            FALLTHROUGH_INTENDED; /* FALLTHROUGH */
          /* intentional fall through: Send both BFP & HSP UUIDs if HFP is
           * enabled */
          case BTA_HSP_SERVICE_ID: {
            *(p_uuid + num_uuids) =
                Uuid::From16Bit(UUID_SERVCLASS_HEADSET_AUDIO_GATEWAY);
            num_uuids++;
          } break;
          case BTA_A2DP_SOURCE_SERVICE_ID: {
            *(p_uuid + num_uuids) =
                Uuid::From16Bit(UUID_SERVCLASS_AUDIO_SOURCE);
            num_uuids++;
          } break;
          case BTA_A2DP_SINK_SERVICE_ID: {
            *(p_uuid + num_uuids) = Uuid::From16Bit(UUID_SERVCLASS_AUDIO_SINK);
            num_uuids++;
          } break;
          case BTA_PBAP_SERVICE_ID: {
            *(p_uuid + num_uuids) = Uuid::From16Bit(UUID_SERVCLASS_PBAP_PSE);
            num_uuids++;
          } break;
          case BTA_HFP_HS_SERVICE_ID: {
            *(p_uuid + num_uuids) =
                Uuid::From16Bit(UUID_SERVCLASS_HF_HANDSFREE);
            num_uuids++;
          } break;
          case BTA_MAP_SERVICE_ID: {
            *(p_uuid + num_uuids) =
                Uuid::From16Bit(UUID_SERVCLASS_MESSAGE_ACCESS);
            num_uuids++;
          } break;
          case BTA_MN_SERVICE_ID: {
            *(p_uuid + num_uuids) =
                Uuid::From16Bit(UUID_SERVCLASS_MESSAGE_NOTIFICATION);
            num_uuids++;
          } break;
          case BTA_PCE_SERVICE_ID: {
            *(p_uuid + num_uuids) = Uuid::From16Bit(UUID_SERVCLASS_PBAP_PCE);
            num_uuids++;
          } break;
        }
      }
    }
    property->len = (num_uuids) * sizeof(Uuid);
    return BT_STATUS_SUCCESS;
  }

  /* fall through for other properties */
  if (!cfg2prop(NULL, property)) {
    return btif_dm_get_adapter_property(property);
  }
  return BT_STATUS_SUCCESS;
}

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
bt_status_t btif_storage_set_adapter_property(bt_property_t* property) {
  return prop2cfg(NULL, property) ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
}

/** Helper function for fetching a bt_property of a remote device. */
bt_status_t btif_storage_get_remote_prop(RawAddress* remote_addr,
                                         bt_property_type_t type, void* buf,
                                         int size, bt_property_t* property) {
  property->type = type;
  property->val = buf;
  property->len = size;
  return btif_storage_get_remote_device_property(remote_addr, property);
}

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
    const RawAddress* remote_bd_addr, bt_property_t* property) {
  return cfg2prop(remote_bd_addr, property) ? BT_STATUS_SUCCESS
                                            : BT_STATUS_FAIL;
}
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
    const RawAddress* remote_bd_addr, bt_property_t* property) {
  return prop2cfg(remote_bd_addr, property) ? BT_STATUS_SUCCESS
                                            : BT_STATUS_FAIL;
}

/*******************************************************************************
 *
 * Function         btif_storage_add_remote_device
 *
 * Description      BTIF storage API - Adds a newly discovered device to NVRAM
 *                  along with the timestamp. Also, stores the various
 *                  properties - RSSI, BDADDR, NAME (if found in EIR)
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_add_remote_device(const RawAddress* remote_bd_addr,
                                           uint32_t num_properties,
                                           bt_property_t* properties) {
  uint32_t i = 0;
  /* TODO: If writing a property, fails do we go back undo the earlier
   * written properties? */
  for (i = 0; i < num_properties; i++) {
    /* Ignore properties that are not stored in DB */
    if (properties[i].type == BT_PROPERTY_REMOTE_RSSI ||
        properties[i].type == BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER ||
        properties[i].type == BT_PROPERTY_REMOTE_ASHA_CAPABILITY ||
        properties[i].type == BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID) {
      continue;
    }

    /* address for remote device needs special handling as we also store
     * timestamp */
    if (properties[i].type == BT_PROPERTY_BDADDR) {
      bt_property_t addr_prop;
      memcpy(&addr_prop, &properties[i], sizeof(bt_property_t));
      addr_prop.type = (bt_property_type_t)BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP;
      btif_storage_set_remote_device_property(remote_bd_addr, &addr_prop);
    } else {
      btif_storage_set_remote_device_property(remote_bd_addr, &properties[i]);
    }
  }
  return BT_STATUS_SUCCESS;
}

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
                                           uint8_t pin_length) {
  std::string bdstr = remote_bd_addr->ToString();
  bool ret =
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_LINK_KEY_TYPE, (int)key_type);
  ret &=
      btif_config_set_int(bdstr, BTIF_STORAGE_KEY_PIN_LENGTH, (int)pin_length);
  ret &= btif_config_set_bin(bdstr, BTIF_STORAGE_KEY_LINK_KEY, link_key.data(),
                             link_key.size());

  if (ret) {
    btif_storage_set_mode(remote_bd_addr);
  }
  return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
}

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
bt_status_t btif_storage_remove_bonded_device(
    const RawAddress* remote_bd_addr) {
  std::string bdstr = remote_bd_addr->ToString();
  log::info("Removing bonded device addr={}",
            ADDRESS_TO_LOGGABLE_CSTR(*remote_bd_addr));

  btif_config_remove_device(bdstr);

  /* Check the length of the paired devices, and if 0 then reset IRK */
  auto paired_devices = btif_config_get_paired_devices();
  if (paired_devices.empty() &&
      bluetooth::common::init_flags::irk_rotation_is_enabled()) {
    log::info("Last paired device removed, resetting IRK");
    BTA_DmBleResetId();
  }
  return BT_STATUS_SUCCESS;
}

/* Some devices hardcode sample LTK value from spec, instead of generating one.
 * Treat such devices as insecure, and remove such bonds when bluetooth
 * restarts. Removing them after disconnection is handled separately.
 *
 * We still allow such devices to bond in order to give the user a chance to
 * update firmware.
 */
static void remove_devices_with_sample_ltk() {
  std::vector<RawAddress> bad_ltk;
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto name = bd_addr.ToString();

    tBTA_LE_KEY_VALUE key;
    memset(&key, 0, sizeof(key));

    if (btif_storage_get_ble_bonding_key(
            bd_addr, BTM_LE_KEY_PENC, (uint8_t*)&key,
            sizeof(tBTM_LE_PENC_KEYS)) == BT_STATUS_SUCCESS) {
      if (is_sample_ltk(key.penc_key.ltk)) {
        bad_ltk.push_back(bd_addr);
      }
    }
  }

  for (RawAddress address : bad_ltk) {
    log::error("Removing bond to device using test TLK: {}",
               ADDRESS_TO_LOGGABLE_CSTR(address));

    btif_storage_remove_bonded_device(&address);
  }
}

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
void btif_storage_load_le_devices(void) {
  btif_bonded_devices_t bonded_devices;
  btif_in_fetch_bonded_devices(&bonded_devices, 1);
  std::unordered_set<RawAddress> bonded_addresses;
  for (uint16_t i = 0; i < bonded_devices.num_devices; i++) {
    bonded_addresses.insert(bonded_devices.devices[i]);
  }

  std::vector<std::pair<RawAddress, RawAddress>> consolidated_devices;
  for (uint16_t i = 0; i < bonded_devices.num_devices; i++) {
    // RawAddress* p_remote_addr;
    tBTA_LE_KEY_VALUE key = {};
    if (btif_storage_get_ble_bonding_key(
            bonded_devices.devices[i], BTM_LE_KEY_PID, (uint8_t*)&key,
            sizeof(tBTM_LE_PID_KEYS)) == BT_STATUS_SUCCESS) {
      if (bonded_devices.devices[i] != key.pid_key.identity_addr) {
        log::info("Found device with a known identity address {} {}",
                  ADDRESS_TO_LOGGABLE_CSTR(bonded_devices.devices[i]),
                  ADDRESS_TO_LOGGABLE_CSTR(key.pid_key.identity_addr));

        if (bonded_devices.devices[i].IsEmpty() ||
            key.pid_key.identity_addr.IsEmpty()) {
          log::warn("Address is empty! Skip");
        } else {
          consolidated_devices.emplace_back(bonded_devices.devices[i],
                                            key.pid_key.identity_addr);
        }
      }
    }
  }

  bt_property_t adapter_prop = {};
  /* Send the adapter_properties_cb with bonded consolidated device */
  {
    /* BONDED_DEVICES */
    auto devices_list =
        std::make_unique<RawAddress[]>(consolidated_devices.size());
    adapter_prop.type = BT_PROPERTY_ADAPTER_BONDED_DEVICES;
    adapter_prop.len = consolidated_devices.size() * sizeof(RawAddress);
    adapter_prop.val = devices_list.get();
    for (uint16_t i = 0; i < consolidated_devices.size(); i++) {
      devices_list[i] = consolidated_devices[i].first;
    }
    btif_adapter_properties_evt(BT_STATUS_SUCCESS, /* num_props */ 1,
                                &adapter_prop);
  }

  for (const auto& device : consolidated_devices) {
    if (bonded_addresses.find(device.second) != bonded_addresses.end()) {
      // Invokes address consolidation for DuMo devices
      GetInterfaceToProfiles()->events->invoke_address_consolidate_cb(
          device.first, device.second);
    } else {
      // Associates RPA & identity address for LE-only devices
      GetInterfaceToProfiles()->events->invoke_le_address_associate_cb(
          device.first, device.second);
    }
  }
}

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
bt_status_t btif_storage_load_bonded_devices(void) {
  btif_bonded_devices_t bonded_devices;
  uint32_t i = 0;
  bt_property_t adapter_props[6];
  uint32_t num_props = 0;
  bt_property_t remote_properties[10];
  RawAddress addr;
  bt_bdname_t name, alias, model_name;
  bt_scan_mode_t mode;
  uint32_t disc_timeout;
  Uuid local_uuids[BT_MAX_NUM_UUIDS];
  Uuid remote_uuids[BT_MAX_NUM_UUIDS];
  bt_status_t status;

  remove_devices_with_sample_ltk();

  btif_in_fetch_bonded_devices(&bonded_devices, 1);

  /* Now send the adapter_properties_cb with all adapter_properties */
  {
    memset(adapter_props, 0, sizeof(adapter_props));

    /* address */
    status = btif_storage_get_adapter_prop(
        BT_PROPERTY_BDADDR, &addr, sizeof(addr), &adapter_props[num_props]);
    // Add BT_PROPERTY_BDADDR property into list only when successful.
    // Otherwise, skip this property entry.
    if (status == BT_STATUS_SUCCESS) {
      num_props++;
    }

    /* BD_NAME */
    btif_storage_get_adapter_prop(BT_PROPERTY_BDNAME, &name, sizeof(name),
                                  &adapter_props[num_props]);
    num_props++;

    /* SCAN_MODE */
    /* TODO: At the time of BT on, always report the scan mode as 0 irrespective
     of the scan_mode during the previous enable cycle.
     This needs to be re-visited as part of the app/stack enable sequence
     synchronization */
    mode = BT_SCAN_MODE_NONE;
    adapter_props[num_props].type = BT_PROPERTY_ADAPTER_SCAN_MODE;
    adapter_props[num_props].len = sizeof(mode);
    adapter_props[num_props].val = &mode;
    num_props++;

    /* DISC_TIMEOUT */
    btif_storage_get_adapter_prop(BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT,
                                  &disc_timeout, sizeof(disc_timeout),
                                  &adapter_props[num_props]);
    num_props++;

    /* BONDED_DEVICES */
    RawAddress* devices_list = (RawAddress*)osi_malloc(
        sizeof(RawAddress) * bonded_devices.num_devices);
    adapter_props[num_props].type = BT_PROPERTY_ADAPTER_BONDED_DEVICES;
    adapter_props[num_props].len =
        bonded_devices.num_devices * sizeof(RawAddress);
    adapter_props[num_props].val = devices_list;
    for (i = 0; i < bonded_devices.num_devices; i++) {
      devices_list[i] = bonded_devices.devices[i];
    }
    num_props++;

    /* LOCAL UUIDs */
    btif_storage_get_adapter_prop(BT_PROPERTY_UUIDS, local_uuids,
                                  sizeof(local_uuids),
                                  &adapter_props[num_props]);
    num_props++;

    btif_adapter_properties_evt(BT_STATUS_SUCCESS, num_props, adapter_props);

    osi_free(devices_list);
  }

  log::verbose("Number of bonded devices found={}", bonded_devices.num_devices);

  {
    for (i = 0; i < bonded_devices.num_devices; i++) {
      RawAddress* p_remote_addr;

      /*
       * TODO: improve handling of missing fields in NVRAM.
       */
      uint32_t cod = 0;
      uint32_t devtype = 0;

      num_props = 0;
      p_remote_addr = &bonded_devices.devices[i];
      memset(remote_properties, 0, sizeof(remote_properties));
      btif_storage_get_remote_prop(p_remote_addr, BT_PROPERTY_BDNAME, &name,
                                   sizeof(name), &remote_properties[num_props]);
      num_props++;

      btif_storage_get_remote_prop(
          p_remote_addr, BT_PROPERTY_REMOTE_FRIENDLY_NAME, &alias,
          sizeof(alias), &remote_properties[num_props]);
      num_props++;

      btif_storage_get_remote_prop(p_remote_addr, BT_PROPERTY_CLASS_OF_DEVICE,
                                   &cod, sizeof(cod),
                                   &remote_properties[num_props]);
      num_props++;

      btif_storage_get_remote_prop(p_remote_addr, BT_PROPERTY_TYPE_OF_DEVICE,
                                   &devtype, sizeof(devtype),
                                   &remote_properties[num_props]);
      num_props++;

      btif_storage_get_remote_prop(p_remote_addr, BT_PROPERTY_UUIDS,
                                   remote_uuids, sizeof(remote_uuids),
                                   &remote_properties[num_props]);
      num_props++;

      // Floss needs appearance for metrics purposes
      uint16_t appearance = 0;
      if (btif_storage_get_remote_prop(p_remote_addr, BT_PROPERTY_APPEARANCE,
                                       &appearance, sizeof(appearance),
                                       &remote_properties[num_props]) ==
          BT_STATUS_SUCCESS) {
        num_props++;
      }

#if TARGET_FLOSS
      // Floss needs VID:PID for metrics purposes
      bt_vendor_product_info_t vp_info;
      if (btif_storage_get_remote_prop(
              p_remote_addr, BT_PROPERTY_VENDOR_PRODUCT_INFO, &vp_info,
              sizeof(vp_info),
              &remote_properties[num_props]) == BT_STATUS_SUCCESS) {
        num_props++;
      }

      // Floss needs address type for diagnosis API
      uint8_t addr_type;
      if (btif_storage_get_remote_prop(
              p_remote_addr, BT_PROPERTY_REMOTE_ADDR_TYPE, &addr_type,
              sizeof(addr_type),
              &remote_properties[num_props]) == BT_STATUS_SUCCESS) {
        num_props++;
      }
#endif

      btif_storage_get_remote_prop(p_remote_addr, BT_PROPERTY_REMOTE_MODEL_NUM,
                                   &model_name, sizeof(model_name),
                                   &remote_properties[num_props]);
      num_props++;

      btif_remote_properties_evt(BT_STATUS_SUCCESS, p_remote_addr, num_props,
                                 remote_properties);
    }
  }
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_storage_add_ble_bonding_key
 *
 * Description      BTIF storage API - Adds the newly bonded device to NVRAM
 *                  along with the ble-key, Key type and Pin key length
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/

bt_status_t btif_storage_add_ble_bonding_key(RawAddress* remote_bd_addr,
                                             const uint8_t* key_value,
                                             uint8_t key_type,
                                             uint8_t key_length) {
  for (size_t i = 0; i < std::size(BTIF_STORAGE_LE_KEYS); i++) {
    auto key = BTIF_STORAGE_LE_KEYS[i];
    if (key.type == key_type) {
      bool ret = btif_config_set_bin(remote_bd_addr->ToString(), key.name,
                                     key_value, key_length);

      if (ret) {
        btif_storage_set_mode(remote_bd_addr);
      }
      return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
    }
  }

  log::warn("Unknown LE key type: {}", key_type);
  return BT_STATUS_FAIL;
}

/*******************************************************************************
 *
 * Function         btif_storage_get_ble_bonding_key
 *
 * Description
 *
 * Returns          BT_STATUS_SUCCESS if the fetch was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_get_ble_bonding_key(const RawAddress& remote_bd_addr,
                                             uint8_t key_type,
                                             uint8_t* key_value,
                                             int key_length) {
  for (size_t i = 0; i < std::size(BTIF_STORAGE_LE_KEYS); i++) {
    auto key = BTIF_STORAGE_LE_KEYS[i];
    if (key.type == key_type) {
      size_t length = key_length;
      bool ret = btif_config_get_bin(remote_bd_addr.ToString(), key.name,
                                     key_value, &length);
      return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
    }
  }

  log::warn("Unknown LE key type: {}", key_type);
  return BT_STATUS_FAIL;
}

/*******************************************************************************
 *
 * Function         btif_storage_remove_ble_keys
 *
 * Description      BTIF storage API - Deletes the bonded device from NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the deletion was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_remove_ble_bonding_keys(
    const RawAddress* remote_bd_addr) {
  std::string bdstr = remote_bd_addr->ToString();
  log::info("Removing bonding keys for bd addr:{}",
            ADDRESS_TO_LOGGABLE_CSTR(*remote_bd_addr));
  bool ret = true;
  for (size_t i = 0; i < std::size(BTIF_STORAGE_LE_KEYS); i++) {
    auto key_name = BTIF_STORAGE_LE_KEYS[i].name;
    if (btif_config_exist(bdstr, key_name))
      ret &= btif_config_remove(bdstr, key_name);
  }

  return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
}

/*******************************************************************************
 *
 * Function         btif_storage_add_ble_local_key
 *
 * Description      BTIF storage API - Adds the ble key to NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the store was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_add_ble_local_key(const Octet16& key_value,
                                           uint8_t key_type) {
  for (size_t i = 0; i < std::size(BTIF_STORAGE_LOCAL_LE_KEYS); i++) {
    auto key = BTIF_STORAGE_LOCAL_LE_KEYS[i];
    if (key.type == key_type) {
      bool ret = btif_config_set_bin(BTIF_STORAGE_SECTION_ADAPTER, key.name,
                                     key_value.data(), key_value.size());

      return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
    }
  }
  log::warn("Unknown LE key type: {}", key_type);
  return BT_STATUS_FAIL;
}

/** Stores local key of |key_type| into |key_value|
 * Returns BT_STATUS_SUCCESS if the fetch was successful, BT_STATUS_FAIL
 * otherwise
 */
bt_status_t btif_storage_get_ble_local_key(uint8_t key_type,
                                           Octet16* key_value) {
  for (size_t i = 0; i < std::size(BTIF_STORAGE_LOCAL_LE_KEYS); i++) {
    auto key = BTIF_STORAGE_LOCAL_LE_KEYS[i];
    if (key.type == key_type) {
      size_t length = key_value->size();
      bool ret = btif_config_get_bin(BTIF_STORAGE_SECTION_ADAPTER, key.name,
                                     key_value->data(), &length);

      return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
    }
  }
  log::warn("Unknown LE key type: {}", key_type);
  return BT_STATUS_FAIL;
}

/*******************************************************************************
 *
 * Function         btif_storage_remove_ble_local_keys
 *
 * Description      BTIF storage API - Deletes the bonded device from NVRAM
 *
 * Returns          BT_STATUS_SUCCESS if the deletion was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_remove_ble_local_keys(void) {
  bool ret = true;
  for (size_t i = 0; i < std::size(BTIF_STORAGE_LOCAL_LE_KEYS); i++) {
    auto key = BTIF_STORAGE_LOCAL_LE_KEYS[i];
    if (btif_config_exist(BTIF_STORAGE_SECTION_ADAPTER, key.name))
      ret &= btif_config_remove(BTIF_STORAGE_SECTION_ADAPTER, key.name);
  }
  return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
}

bt_status_t btif_in_fetch_bonded_ble_device(
    const std::string& remote_bd_addr, int add,
    btif_bonded_devices_t* p_bonded_devices) {
  int device_type;
  tBLE_ADDR_TYPE addr_type;
  bool device_added = false;
  bool key_found = false;
  RawAddress bd_addr;

  RawAddress::FromString(remote_bd_addr, bd_addr);

  if (!btif_config_get_int(remote_bd_addr, BTIF_STORAGE_KEY_DEV_TYPE,
                           &device_type))
    return BT_STATUS_FAIL;

  if ((device_type & BT_DEVICE_TYPE_BLE) == BT_DEVICE_TYPE_BLE ||
      btif_has_ble_keys(remote_bd_addr)) {
    log::verbose("Found a LE device: {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    if (btif_storage_get_remote_addr_type(&bd_addr, &addr_type) !=
        BT_STATUS_SUCCESS) {
      addr_type = BLE_ADDR_PUBLIC;
      btif_storage_set_remote_addr_type(&bd_addr, BLE_ADDR_PUBLIC);
    }

    for (size_t i = 0; i < std::size(BTIF_STORAGE_LE_KEYS); i++) {
      auto key = BTIF_STORAGE_LE_KEYS[i];
      btif_read_le_key(key.type, key.size, bd_addr, addr_type, add,
                       &device_added, &key_found);
    }

    // Fill in the bonded devices
    if (device_added) {
      if (p_bonded_devices->num_devices < BTM_SEC_MAX_DEVICE_RECORDS) {
        p_bonded_devices->devices[p_bonded_devices->num_devices++] = bd_addr;
      } else {
        log::warn("Exceed the max number of bonded devices");
      }
      btif_gatts_add_bonded_dev_from_nv(bd_addr);
    }

    if (key_found) return BT_STATUS_SUCCESS;
  }
  return BT_STATUS_FAIL;
}

void btif_storage_invoke_addr_type_update(const RawAddress& remote_bd_addr,
                                          const tBLE_ADDR_TYPE& addr_type) {
  bt_property_t prop;
  prop.type = BT_PROPERTY_REMOTE_ADDR_TYPE;
  prop.val = (tBLE_ADDR_TYPE*)&addr_type;
  prop.len = sizeof(tBLE_ADDR_TYPE);
  GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
      BT_STATUS_SUCCESS, remote_bd_addr, 1, &prop);
}

bt_status_t btif_storage_set_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE addr_type) {
  bool ret = btif_config_set_int(remote_bd_addr->ToString(),
                                 BTIF_STORAGE_KEY_ADDR_TYPE, (int)addr_type);

#if TARGET_FLOSS
  // Floss needs to get address type for diagnosis API.
  btif_storage_invoke_addr_type_update(*remote_bd_addr, addr_type);
#endif

  return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
}

void btif_storage_set_remote_addr_type(const RawAddress& remote_bd_addr,
                                       const tBLE_ADDR_TYPE& addr_type) {
  if (!btif_config_set_int(remote_bd_addr.ToString(),
                           BTIF_STORAGE_KEY_ADDR_TYPE,
                           static_cast<int>(addr_type)))
    log::error("Unable to set storage property");
  else {
#if TARGET_FLOSS
    // Floss needs to get address type for diagnosis API.
    btif_storage_invoke_addr_type_update(remote_bd_addr, addr_type);
#endif
  }
}

void btif_storage_set_remote_device_type(const RawAddress& remote_bd_addr,
                                         const tBT_DEVICE_TYPE& device_type) {
  if (!btif_config_set_int(remote_bd_addr.ToString(), BTIF_STORAGE_KEY_DEV_TYPE,
                           static_cast<int>(device_type)))
    log::error("Unable to set storage property");
}

bool btif_has_ble_keys(const std::string& bdstr) {
  return btif_config_exist(bdstr, BTIF_STORAGE_KEY_LE_KEY_PENC);
}

/*******************************************************************************
 *
 * Function         btif_storage_get_remote_addr_type
 *
 * Description      BTIF storage API - Fetches the remote addr type
 *
 * Returns          BT_STATUS_SUCCESS if the fetch was successful,
 *                  BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t btif_storage_get_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE* addr_type) {
  int val;
  bool ret = btif_config_get_int(remote_bd_addr->ToString(),
                                 BTIF_STORAGE_KEY_ADDR_TYPE, &val);
  *addr_type = static_cast<tBLE_ADDR_TYPE>(val);
  return ret ? BT_STATUS_SUCCESS : BT_STATUS_FAIL;
}

bool btif_storage_get_remote_addr_type(const RawAddress& remote_bd_addr,
                                       tBLE_ADDR_TYPE& addr_type) {
  int val;
  bool ret = btif_config_get_int(remote_bd_addr.ToString(),
                                 BTIF_STORAGE_KEY_ADDR_TYPE, &val);
  addr_type = static_cast<tBLE_ADDR_TYPE>(val);
  return ret;
}

bool btif_storage_get_remote_device_type(const RawAddress& remote_bd_addr,
                                         tBT_DEVICE_TYPE& device_type) {
  int val;
  bool ret = btif_config_get_int(remote_bd_addr.ToString(),
                                 BTIF_STORAGE_KEY_DEV_TYPE, &val);
  device_type = static_cast<tBT_DEVICE_TYPE>(val);
  return ret;
}

/** Stores information about GATT server supported features */
void btif_storage_set_gatt_sr_supp_feat(const RawAddress& addr, uint8_t feat) {
  do_in_jni_thread(
      FROM_HERE, Bind(
                     [](const RawAddress& addr, uint8_t feat) {
                       std::string bdstr = addr.ToString();
                       log::verbose(
                           "GATT server supported features for: {} features: "
                           "{}",
                           ADDRESS_TO_LOGGABLE_STR(addr), feat);
                       btif_config_set_int(
                           bdstr, BTIF_STORAGE_KEY_GATT_SERVER_SUPPORTED, feat);
                     },
                     addr, feat));
}

/** Gets information about GATT server supported features */
uint8_t btif_storage_get_sr_supp_feat(const RawAddress& bd_addr) {
  auto name = bd_addr.ToString();

  int value = 0;
  btif_config_get_int(name, BTIF_STORAGE_KEY_GATT_SERVER_SUPPORTED, &value);
  log::verbose("Remote device: {} GATT server supported features 0x{:02x}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), value);

  return value;
}

/*******************************************************************************
 *
 * Function         btif_storage_is_restricted_device
 *
 * Description      BTIF storage API - checks if this device is a restricted
 *                  device
 *
 * Returns          true  if the device is labeled as restricted
 *                  false otherwise
 *
 ******************************************************************************/
bool btif_storage_is_restricted_device(const RawAddress* remote_bd_addr) {
  int val;
  return btif_config_get_int(remote_bd_addr->ToString(),
                             BTIF_STORAGE_KEY_RESTRICTED, &val);
}

int btif_storage_get_num_bonded_devices(void) {
  btif_bonded_devices_t bonded_devices;
  btif_in_fetch_bonded_devices(&bonded_devices, 0);
  return bonded_devices.num_devices;
}

// Get the name of a device from btif for interop database matching.
bool btif_storage_get_stored_remote_name(const RawAddress& bd_addr,
                                         char* name) {
  bt_property_t property;
  property.type = BT_PROPERTY_BDNAME;
  property.len = BTM_MAX_REM_BD_NAME_LEN;
  property.val = name;

  return (btif_storage_get_remote_device_property(&bd_addr, &property) ==
          BT_STATUS_SUCCESS);
}

/** Stores information about GATT Client supported features support */
void btif_storage_set_gatt_cl_supp_feat(const RawAddress& bd_addr,
                                        uint8_t feat) {
  do_in_jni_thread(
      FROM_HERE, Bind(
                     [](const RawAddress& bd_addr, uint8_t feat) {
                       std::string bdstr = bd_addr.ToString();
                       log::verbose("saving gatt client supported feat: {}",
                                    ADDRESS_TO_LOGGABLE_STR(bd_addr));
                       btif_config_set_int(
                           bdstr, BTIF_STORAGE_KEY_GATT_CLIENT_SUPPORTED, feat);
                     },
                     bd_addr, feat));
}

/** Get client supported features */
uint8_t btif_storage_get_gatt_cl_supp_feat(const RawAddress& bd_addr) {
  auto name = bd_addr.ToString();

  int value = 0;
  btif_config_get_int(name, BTIF_STORAGE_KEY_GATT_CLIENT_SUPPORTED, &value);
  log::verbose("Remote device: {} GATT client supported features 0x{:02x}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), value);

  return value;
}

/** Remove client supported features */
void btif_storage_remove_gatt_cl_supp_feat(const RawAddress& bd_addr) {
  do_in_jni_thread(
      FROM_HERE, Bind(
                     [](const RawAddress& bd_addr) {
                       auto bdstr = bd_addr.ToString();
                       if (btif_config_exist(
                               bdstr, BTIF_STORAGE_KEY_GATT_CLIENT_SUPPORTED)) {
                         btif_config_remove(
                             bdstr, BTIF_STORAGE_KEY_GATT_CLIENT_SUPPORTED);
                       }
                     },
                     bd_addr));
}

/** Store last server database hash for remote client */
void btif_storage_set_gatt_cl_db_hash(const RawAddress& bd_addr, Octet16 hash) {
  do_in_jni_thread(FROM_HERE, Bind(
                                  [](const RawAddress& bd_addr, Octet16 hash) {
                                    auto bdstr = bd_addr.ToString();
                                    btif_config_set_bin(
                                        bdstr,
                                        BTIF_STORAGE_KEY_GATT_CLIENT_DB_HASH,
                                        hash.data(), hash.size());
                                  },
                                  bd_addr, hash));
}

/** Get last server database hash for remote client */
Octet16 btif_storage_get_gatt_cl_db_hash(const RawAddress& bd_addr) {
  auto bdstr = bd_addr.ToString();

  Octet16 hash;
  size_t size = hash.size();
  btif_config_get_bin(bdstr, BTIF_STORAGE_KEY_GATT_CLIENT_DB_HASH, hash.data(),
                      &size);

  return hash;
}

/** Remove las server database hash for remote client */
void btif_storage_remove_gatt_cl_db_hash(const RawAddress& bd_addr) {
  do_in_jni_thread(FROM_HERE,
                   Bind(
                       [](const RawAddress& bd_addr) {
                         auto bdstr = bd_addr.ToString();
                         if (btif_config_exist(
                                 bdstr, BTIF_STORAGE_KEY_GATT_CLIENT_DB_HASH)) {
                           btif_config_remove(
                               bdstr, BTIF_STORAGE_KEY_GATT_CLIENT_DB_HASH);
                         }
                       },
                       bd_addr));
}

void btif_debug_linkkey_type_dump(int fd) {
  dprintf(fd, "\nLink Key Types:\n");
  for (const auto& bd_addr : btif_config_get_paired_devices()) {
    auto bdstr = bd_addr.ToString();
    int linkkey_type;
    dprintf(fd, "  %s\n", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    dprintf(fd, "    BR: ");
    if (btif_config_get_int(bdstr, BTIF_STORAGE_KEY_LINK_KEY_TYPE,
                            &linkkey_type)) {
      dprintf(fd, "%s", linkkey_type_text(linkkey_type).c_str());
    }
    dprintf(fd, "\n");

    dprintf(fd, "    LE:");
    for (size_t i = 0; i < std::size(BTIF_STORAGE_LE_KEYS); i++) {
      const std::string& key_name = BTIF_STORAGE_LE_KEYS[i].name;
      if (btif_config_exist(bdstr, key_name))
        dprintf(fd, " %s", key_name.c_str());
    }

    dprintf(fd, "\n");
  }
}
