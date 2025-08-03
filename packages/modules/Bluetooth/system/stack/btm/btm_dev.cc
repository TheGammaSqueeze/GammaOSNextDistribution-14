/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

/******************************************************************************
 *
 *  This file contains functions for the Bluetooth Device Manager
 *
 ******************************************************************************/

#define LOG_TAG "btm_dev"

#include "stack/btm/btm_dev.h"

#include <android_bluetooth_flags.h>
#include <bluetooth/log.h>

#include <string>

#include "btm_api.h"
#include "btm_int_types.h"
#include "btm_sec_api.h"
#include "btm_sec_cb.h"
#include "common/init_flags.h"
#include "hci/controller_interface.h"
#include "internal_include/bt_target.h"
#include "l2c_api.h"
#include "main/shim/entry.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/compat.h"
#include "rust/src/connection/ffi/connection_shim.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_ble_privacy.h"
#include "stack/include/btm_log_history.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;
void gatt_consolidate(const RawAddress& identity_addr, const RawAddress& rpa);

namespace {

constexpr char kBtmLogTag[] = "BOND";

}

static void wipe_secrets_and_remove(tBTM_SEC_DEV_REC* p_dev_rec) {
  p_dev_rec->sec_rec.link_key.fill(0);
  memset(&p_dev_rec->sec_rec.ble_keys, 0, sizeof(tBTM_SEC_BLE_KEYS));
  list_remove(btm_sec_cb.sec_dev_rec, p_dev_rec);
}

/*******************************************************************************
 *
 * Function         BTM_SecAddDevice
 *
 * Description      Add/modify device.  This function will be normally called
 *                  during host startup to restore all required information
 *                  stored in the NVRAM.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  dev_class        - Device Class
 *                  bd_name          - Name of the peer device. NULL if unknown.
 *                  features         - Remote device's features (up to 3 pages).
 *                                     NULL if not known
 *                  link_key         - Connection link key. NULL if unknown.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool BTM_SecAddDevice(const RawAddress& bd_addr, DEV_CLASS dev_class,
                      const BD_NAME& bd_name, uint8_t* features,
                      LinkKey* p_link_key, uint8_t key_type,
                      uint8_t pin_length) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (!p_dev_rec) {
    p_dev_rec = btm_sec_allocate_dev_rec();
    log::debug(
        "Caching new record from config file device:{} link_key_type:{:x} "
        "name:{}",
        ADDRESS_TO_LOGGABLE_STR(bd_addr), key_type,
        reinterpret_cast<const char*>(bd_name));

    p_dev_rec->bd_addr = bd_addr;
    p_dev_rec->hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_BR_EDR);

    /* use default value for background connection params */
    /* update conn params, use default value for background connection params */
    memset(&p_dev_rec->conn_params, 0xff, sizeof(tBTM_LE_CONN_PRAMS));
  } else {
    log::debug(
        "Caching existing record from config file device:{} link_key_type:{:x}",
        ADDRESS_TO_LOGGABLE_STR(bd_addr), key_type);

    /* "Bump" timestamp for existing record */
    p_dev_rec->timestamp = btm_sec_cb.dev_rec_count++;

    /* TODO(eisenbach):
     * Small refactor, but leaving original logic for now.
     * On the surface, this does not make any sense at all. Why change the
     * bond state for an existing device here? This logic should be verified
     * as part of a larger refactor.
     */
    p_dev_rec->sec_rec.bond_type = BOND_TYPE_UNKNOWN;
  }

  if (dev_class != kDevClassEmpty) p_dev_rec->dev_class = dev_class;

  memset(p_dev_rec->sec_bd_name, 0, sizeof(tBTM_BD_NAME));

  if (bd_name && bd_name[0]) {
    log::debug("  Remote name known for device:{} name:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               reinterpret_cast<const char*>(bd_name));
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_NAME_KNOWN;
    strlcpy((char*)p_dev_rec->sec_bd_name, (char*)bd_name,
            BTM_MAX_REM_BD_NAME_LEN + 1);
  }

  if (p_link_key) {
    log::debug("  Link key known for device:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LINK_KEY_KNOWN;
    p_dev_rec->sec_rec.link_key = *p_link_key;
    p_dev_rec->sec_rec.link_key_type = key_type;
    p_dev_rec->sec_rec.pin_code_length = pin_length;

    if (IS_FLAG_ENABLED(correct_bond_type_of_loaded_devices)) {
      p_dev_rec->sec_rec.bond_type = BOND_TYPE_PERSISTENT;
    }

    if (pin_length >= 16 || key_type == BTM_LKEY_TYPE_AUTH_COMB ||
        key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256) {
      // Set the flag if the link key was made by using either a 16 digit
      // pin or MITM.
      p_dev_rec->sec_rec.sec_flags |=
          BTM_SEC_16_DIGIT_PIN_AUTHED | BTM_SEC_LINK_KEY_AUTHED;
    }
  }

  p_dev_rec->sec_rec.rmt_io_caps = BTM_IO_CAP_OUT;
  p_dev_rec->device_type |= BT_DEVICE_TYPE_BREDR;

  return true;
}

/** Removes the device from acceptlist */
void BTM_AcceptlistRemove(const RawAddress& address);

/** Free resources associated with the device associated with |bd_addr| address.
 *
 * *** WARNING ***
 * tBTM_SEC_DEV_REC associated with bd_addr becomes invalid after this function
 * is called, also any of it's fields. i.e. if you use p_dev_rec->bd_addr, it is
 * no longer valid!
 * *** WARNING ***
 *
 * Returns true if removed OK, false if not found or ACL link is active.
 */
bool BTM_SecDeleteDevice(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::warn("Unable to delete link key for unknown device {}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return true;
  }

  /* Invalidate bonded status */
  p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LINK_KEY_KNOWN;
  p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LE_LINK_KEY_KNOWN;

  if (BTM_IsAclConnectionUp(bd_addr, BT_TRANSPORT_LE) ||
      BTM_IsAclConnectionUp(bd_addr, BT_TRANSPORT_BR_EDR)) {
    log::warn("FAILED: Cannot Delete when connection to {} is active",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return false;
  }

  RawAddress bda = p_dev_rec->bd_addr;

  log::info("Remove device {} from filter accept list before delete record",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  if (bluetooth::common::init_flags::
          use_unified_connection_manager_is_enabled()) {
    bluetooth::connection::GetConnectionManager()
        .stop_all_connections_to_device(
            bluetooth::connection::ResolveRawAddress(p_dev_rec->bd_addr));
  } else {
    BTM_AcceptlistRemove(p_dev_rec->bd_addr);
  }

  const auto device_type = p_dev_rec->device_type;
  const auto bond_type = p_dev_rec->sec_rec.bond_type;

  /* Clear out any saved BLE keys */
  btm_sec_clear_ble_keys(p_dev_rec);
  wipe_secrets_and_remove(p_dev_rec);
  /* Tell controller to get rid of the link key, if it has one stored */
  BTM_DeleteStoredLinkKey(&bda, NULL);
  log::info("{} complete", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  BTM_LogHistory(kBtmLogTag, bd_addr, "Device removed",
                 base::StringPrintf("device_type:%s bond_type:%s",
                                    DeviceTypeText(device_type).c_str(),
                                    bond_type_text(bond_type).c_str()));

  return true;
}

/*******************************************************************************
 *
 * Function         BTM_SecClearSecurityFlags
 *
 * Description      Reset the security flags (mark as not-paired) for a given
 *                  remove device.
 *
 ******************************************************************************/
void BTM_SecClearSecurityFlags(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) return;

  p_dev_rec->sec_rec.sec_flags = 0;
  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
  p_dev_rec->sm4 = BTM_SM4_UNKNOWN;
}

/*******************************************************************************
 *
 * Function         BTM_SecReadDevName
 *
 * Description      Looks for the device name in the security database for the
 *                  specified BD address.
 *
 * Returns          Pointer to the name or NULL
 *
 ******************************************************************************/
const char* BTM_SecReadDevName(const RawAddress& bd_addr) {
  const char* p_name = NULL;
  const tBTM_SEC_DEV_REC* p_srec;

  p_srec = btm_find_dev(bd_addr);
  if (p_srec != NULL) p_name = (const char*)p_srec->sec_bd_name;

  return (p_name);
}

/*******************************************************************************
 *
 * Function         btm_sec_alloc_dev
 *
 * Description      Allocate a security device record with specified address,
 *                  fill device type and device class from inquiry database or
 *                  btm_sec_cb (if the address is the connecting device)
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_sec_alloc_dev(const RawAddress& bd_addr) {
  tBTM_INQ_INFO* p_inq_info;

  tBTM_SEC_DEV_REC* p_dev_rec = btm_sec_allocate_dev_rec();

  log::debug("Allocated device record bd_addr:{}",
             ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  /* Check with the BT manager if details about remote device are known */
  /* outgoing connection */
  p_inq_info = BTM_InqDbRead(bd_addr);
  if (p_inq_info != NULL) {
    p_dev_rec->dev_class = p_inq_info->results.dev_class;

    p_dev_rec->device_type = p_inq_info->results.device_type;
    if (is_ble_addr_type_known(p_inq_info->results.ble_addr_type))
      p_dev_rec->ble.SetAddressType(p_inq_info->results.ble_addr_type);
    else
      log::warn(
          "Please do not update device record from anonymous le advertisement");

  } else if (bd_addr == btm_sec_cb.connecting_bda)
    p_dev_rec->dev_class = btm_sec_cb.connecting_dc;

  /* update conn params, use default value for background connection params */
  memset(&p_dev_rec->conn_params, 0xff, sizeof(tBTM_LE_CONN_PRAMS));

  p_dev_rec->bd_addr = bd_addr;

  p_dev_rec->ble_hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);
  p_dev_rec->hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_BR_EDR);

  return (p_dev_rec);
}

/*******************************************************************************
 *
 * Function         btm_dev_support_role_switch
 *
 * Description      This function is called by the L2CAP to check if remote
 *                  device supports role switch
 *
 * Parameters:      bd_addr       - Address of the peer device
 *
 * Returns          true if device is known and role switch is supported
 *                  for the link.
 *
 ******************************************************************************/
bool btm_dev_support_role_switch(const RawAddress& bd_addr) {
  if (BTM_IsScoActiveByBdaddr(bd_addr)) {
    log::verbose("Role switch is not allowed if a SCO is up");
    return false;
  }

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == nullptr) {
    log::verbose("Unknown address for role switch");
    return false;
  }

  if (!bluetooth::shim::GetController()->SupportsRoleSwitch()) {
    log::verbose("Local controller does not support role switch");
    return false;
  }

  if (p_dev_rec->remote_supports_hci_role_switch) {
    log::verbose("Peer controller supports role switch");
    return true;
  }

  if (!p_dev_rec->remote_feature_received) {
    log::verbose(
        "Unknown peer capabilities, assuming peer supports role switch");
    return true;
  }

  log::verbose("Peer controller does not support role switch");
  return false;
}

static bool is_handle_equal(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);
  uint16_t* handle = static_cast<uint16_t*>(context);

  if (p_dev_rec->hci_handle == *handle || p_dev_rec->ble_hci_handle == *handle)
    return false;

  return true;
}

/*******************************************************************************
 *
 * Function         btm_find_dev_by_handle
 *
 * Description      Look for the record in the device database for the record
 *                  with specified handle
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_dev_by_handle(uint16_t handle) {
  if (btm_sec_cb.sec_dev_rec == nullptr) return nullptr;

  list_node_t* n =
      list_foreach(btm_sec_cb.sec_dev_rec, is_handle_equal, &handle);
  if (n) return static_cast<tBTM_SEC_DEV_REC*>(list_node(n));

  return NULL;
}

static bool is_address_equal(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);
  const RawAddress* bd_addr = ((RawAddress*)context);

  if (p_dev_rec->bd_addr == *bd_addr) return false;
  // If a LE random address is looking for device record
  if (p_dev_rec->ble.pseudo_addr == *bd_addr) return false;

  if (btm_ble_addr_resolvable(*bd_addr, p_dev_rec)) return false;
  return true;
}

/*******************************************************************************
 *
 * Function         btm_find_dev
 *
 * Description      Look for the record in the device database for the record
 *                  with specified BD address
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_dev(const RawAddress& bd_addr) {
  if (btm_sec_cb.sec_dev_rec == nullptr) return nullptr;

  list_node_t* n =
      list_foreach(btm_sec_cb.sec_dev_rec, is_address_equal, (void*)&bd_addr);
  if (n) return static_cast<tBTM_SEC_DEV_REC*>(list_node(n));

  return NULL;
}

static bool has_lenc_and_address_is_equal(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);
  if (!(p_dev_rec->sec_rec.ble_keys.key_type & BTM_LE_KEY_LENC)) return true;

  return is_address_equal(data, context);
}

/*******************************************************************************
 *
 * Function         btm_find_dev_with_lenc
 *
 * Description      Look for the record in the device database with LTK and
 *                  specified BD address
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_dev_with_lenc(const RawAddress& bd_addr) {
  if (btm_sec_cb.sec_dev_rec == nullptr) return nullptr;

  list_node_t* n = list_foreach(btm_sec_cb.sec_dev_rec,
                                has_lenc_and_address_is_equal, (void*)&bd_addr);
  if (n) return static_cast<tBTM_SEC_DEV_REC*>(list_node(n));

  return NULL;
}
/*******************************************************************************
 *
 * Function         btm_consolidate_dev
 *
 * Description      combine security records if identified as same peer
 *
 * Returns          none
 *
 ******************************************************************************/
void btm_consolidate_dev(tBTM_SEC_DEV_REC* p_target_rec) {
  tBTM_SEC_DEV_REC temp_rec = *p_target_rec;

  log::verbose("");

  list_node_t* end = list_end(btm_sec_cb.sec_dev_rec);
  list_node_t* node = list_begin(btm_sec_cb.sec_dev_rec);
  while (node != end) {
    tBTM_SEC_DEV_REC* p_dev_rec =
        static_cast<tBTM_SEC_DEV_REC*>(list_node(node));

    // we do list_remove in some cases, must grab next before removing
    node = list_next(node);

    if (p_target_rec == p_dev_rec) continue;

    if (p_dev_rec->bd_addr == p_target_rec->bd_addr) {
      memcpy(p_target_rec, p_dev_rec, sizeof(tBTM_SEC_DEV_REC));
      p_target_rec->ble = temp_rec.ble;
      p_target_rec->sec_rec.ble_keys = temp_rec.sec_rec.ble_keys;
      p_target_rec->ble_hci_handle = temp_rec.ble_hci_handle;
      p_target_rec->sec_rec.enc_key_size = temp_rec.sec_rec.enc_key_size;
      p_target_rec->conn_params = temp_rec.conn_params;
      p_target_rec->device_type |= temp_rec.device_type;
      p_target_rec->sec_rec.sec_flags |= temp_rec.sec_rec.sec_flags;

      p_target_rec->sec_rec.new_encryption_key_is_p256 =
          temp_rec.sec_rec.new_encryption_key_is_p256;
      p_target_rec->sec_rec.bond_type = temp_rec.sec_rec.bond_type;

      /* remove the combined record */
      wipe_secrets_and_remove(p_dev_rec);
      // p_dev_rec gets freed in list_remove, we should not  access it further
      continue;
    }

    /* an RPA device entry is a duplicate of the target record */
    if (btm_ble_addr_resolvable(p_dev_rec->bd_addr, p_target_rec)) {
      if (p_target_rec->ble.pseudo_addr == p_dev_rec->bd_addr) {
        p_target_rec->ble.SetAddressType(p_dev_rec->ble.AddressType());
        p_target_rec->device_type |= p_dev_rec->device_type;

        /* remove the combined record */
        wipe_secrets_and_remove(p_dev_rec);
      }
    }
  }
}

static BTM_CONSOLIDATION_CB* btm_consolidate_cb = nullptr;

void BTM_SetConsolidationCallback(BTM_CONSOLIDATION_CB* cb) {
  btm_consolidate_cb = cb;
}

/* combine security records of established LE connections after Classic pairing
 * succeeded. */
void btm_dev_consolidate_existing_connections(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_target_rec = btm_find_dev(bd_addr);
  if (!p_target_rec) {
    log::error("No security record for just bonded device!?!?");
    return;
  }

  if (p_target_rec->ble_hci_handle != HCI_INVALID_HANDLE) {
    log::info("Not consolidating - already have LE connection");
    return;
  }

  log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  list_node_t* end = list_end(btm_sec_cb.sec_dev_rec);
  list_node_t* node = list_begin(btm_sec_cb.sec_dev_rec);
  while (node != end) {
    tBTM_SEC_DEV_REC* p_dev_rec =
        static_cast<tBTM_SEC_DEV_REC*>(list_node(node));

    // we do list_remove in some cases, must grab next before removing
    node = list_next(node);

    if (p_target_rec == p_dev_rec) continue;

    /* an RPA device entry is a duplicate of the target record */
    if (btm_ble_addr_resolvable(p_dev_rec->bd_addr, p_target_rec)) {
      if (p_dev_rec->ble_hci_handle == HCI_INVALID_HANDLE) {
        log::info("already disconnected - erasing entry {}",
                  ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));
        wipe_secrets_and_remove(p_dev_rec);
        continue;
      }

      log::info(
          "Found existing LE connection to just bonded device on {} handle "
          "0x{:04x}",
          ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr),
          p_dev_rec->ble_hci_handle);

      RawAddress ble_conn_addr = p_dev_rec->bd_addr;
      p_target_rec->ble_hci_handle = p_dev_rec->ble_hci_handle;

      /* remove the old LE record */
      wipe_secrets_and_remove(p_dev_rec);

      btm_acl_consolidate(bd_addr, ble_conn_addr);
      L2CA_Consolidate(bd_addr, ble_conn_addr);
      gatt_consolidate(bd_addr, ble_conn_addr);
      if (btm_consolidate_cb) btm_consolidate_cb(bd_addr, ble_conn_addr);

      /* To avoid race conditions between central/peripheral starting encryption
       * at same time, initiate it just from central. */
      if (L2CA_GetBleConnRole(ble_conn_addr) == HCI_ROLE_CENTRAL) {
        log::info("Will encrypt existing connection");
        BTM_SetEncryption(bd_addr, BT_TRANSPORT_LE, nullptr, nullptr,
                          BTM_BLE_SEC_ENCRYPT);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_find_or_alloc_dev
 *
 * Description      Look for the record in the device database for the record
 *                  with specified BD address, if not found, allocate a new
 *                  record
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_or_alloc_dev(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  log::verbose("btm_find_or_alloc_dev");
  p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    /* Allocate a new device record or reuse the oldest one */
    p_dev_rec = btm_sec_alloc_dev(bd_addr);
  }
  return (p_dev_rec);
}

/*******************************************************************************
 *
 * Function         btm_find_oldest_dev_rec
 *
 * Description      Locates the oldest device record in use. It first looks for
 *                  the oldest non-paired device.  If all devices are paired it
 *                  returns the oldest paired device.
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
static tBTM_SEC_DEV_REC* btm_find_oldest_dev_rec(void) {
  tBTM_SEC_DEV_REC* p_oldest = NULL;
  uint32_t ts_oldest = 0xFFFFFFFF;
  tBTM_SEC_DEV_REC* p_oldest_paired = NULL;
  uint32_t ts_oldest_paired = 0xFFFFFFFF;

  list_node_t* end = list_end(btm_sec_cb.sec_dev_rec);
  for (list_node_t* node = list_begin(btm_sec_cb.sec_dev_rec); node != end;
       node = list_next(node)) {
    tBTM_SEC_DEV_REC* p_dev_rec =
        static_cast<tBTM_SEC_DEV_REC*>(list_node(node));

    if ((p_dev_rec->sec_rec.sec_flags &
         (BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_LE_LINK_KEY_KNOWN)) == 0) {
      // Device is not paired
      if (p_dev_rec->timestamp < ts_oldest) {
        p_oldest = p_dev_rec;
        ts_oldest = p_dev_rec->timestamp;
      }
    } else {
      // Paired device
      if (p_dev_rec->timestamp < ts_oldest_paired) {
        p_oldest_paired = p_dev_rec;
        ts_oldest_paired = p_dev_rec->timestamp;
      }
    }
  }

  // If we did not find any non-paired devices, use the oldest paired one...
  if (ts_oldest == 0xFFFFFFFF) p_oldest = p_oldest_paired;

  return p_oldest;
}

/*******************************************************************************
 *
 * Function         btm_sec_allocate_dev_rec
 *
 * Description      Attempts to allocate a new device record. If we have
 *                  exceeded the maximum number of allowable records to
 *                  allocate, the oldest record will be deleted to make room
 *                  for the new record.
 *
 * Returns          Pointer to the newly allocated record
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_sec_allocate_dev_rec(void) {
  tBTM_SEC_DEV_REC* p_dev_rec = NULL;

  if (btm_sec_cb.sec_dev_rec == nullptr) {
    log::warn(
        "Unable to allocate device record with destructed device record list");
    return nullptr;
  }

  if (list_length(btm_sec_cb.sec_dev_rec) > BTM_SEC_MAX_DEVICE_RECORDS) {
    p_dev_rec = btm_find_oldest_dev_rec();
    wipe_secrets_and_remove(p_dev_rec);
  }

  p_dev_rec =
      static_cast<tBTM_SEC_DEV_REC*>(osi_calloc(sizeof(tBTM_SEC_DEV_REC)));
  list_append(btm_sec_cb.sec_dev_rec, p_dev_rec);

  // Initialize defaults
  p_dev_rec->sec_rec.sec_flags = BTM_SEC_IN_USE;
  p_dev_rec->sec_rec.bond_type = BOND_TYPE_UNKNOWN;
  p_dev_rec->timestamp = btm_sec_cb.dev_rec_count++;
  p_dev_rec->sec_rec.rmt_io_caps = BTM_IO_CAP_UNKNOWN;
  p_dev_rec->suggested_tx_octets = 0;

  return p_dev_rec;
}

/*******************************************************************************
 *
 * Function         btm_get_bond_type_dev
 *
 * Description      Get the bond type for a device in the device database
 *                  with specified BD address
 *
 * Returns          The device bond type if known, otherwise BOND_TYPE_UNKNOWN
 *
 ******************************************************************************/
tBTM_BOND_TYPE btm_get_bond_type_dev(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);

  if (p_dev_rec == NULL) return BOND_TYPE_UNKNOWN;

  return p_dev_rec->sec_rec.bond_type;
}

/*******************************************************************************
 *
 * Function         btm_set_bond_type_dev
 *
 * Description      Set the bond type for a device in the device database
 *                  with specified BD address
 *
 * Returns          true on success, otherwise false
 *
 ******************************************************************************/
bool btm_set_bond_type_dev(const RawAddress& bd_addr,
                           tBTM_BOND_TYPE bond_type) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);

  if (p_dev_rec == NULL) return false;

  p_dev_rec->sec_rec.bond_type = bond_type;
  return true;
}

/*******************************************************************************
 *
 * Function         btm_get_sec_dev_rec
 *
 * Description      Get all security device records
 *
 * Returns          A vector containing pointers to all security device records
 *
 ******************************************************************************/
std::vector<tBTM_SEC_DEV_REC*> btm_get_sec_dev_rec() {
  std::vector<tBTM_SEC_DEV_REC*> result{};

  list_node_t* end = list_end(btm_sec_cb.sec_dev_rec);
  for (list_node_t* node = list_begin(btm_sec_cb.sec_dev_rec); node != end;
       node = list_next(node)) {
    tBTM_SEC_DEV_REC* p_dev_rec =
        static_cast<tBTM_SEC_DEV_REC*>(list_node(node));
    result.push_back(p_dev_rec);
  }
  return result;
}

/*******************************************************************************
 *
 * Function         BTM_Sec_AddressKnown
 *
 * Description      Query the secure device database and check
 *                  whether the device associated with address has
 *                  its address resolved
 *
 * Returns          True if
 *                     - the device is unknown, or
 *                     - the device is classic, or
 *                     - the device is ble and has a public address
 *                     - the device is ble with a resolved identity address
 *                  False, otherwise
 *
 ******************************************************************************/
bool BTM_Sec_AddressKnown(const RawAddress& address) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(address);

  //  not a known device, or a classic device, we assume public address
  if (p_dev_rec == NULL || (p_dev_rec->device_type & BT_DEVICE_TYPE_BLE) == 0)
    return true;

  log::warn("{}, device type not BLE: 0x{:02x}",
            ADDRESS_TO_LOGGABLE_CSTR(address), p_dev_rec->device_type);

  // bonded device with identity address known
  if (!p_dev_rec->ble.identity_address_with_type.bda.IsEmpty()) {
    return true;
  }

  // Public address, Random Static, or Random Non-Resolvable Address known
  if (p_dev_rec->ble.AddressType() == BLE_ADDR_PUBLIC ||
      !BTM_BLE_IS_RESOLVE_BDA(address)) {
    return true;
  }

  log::warn("{}, the address type is 0x{:02x}",
            ADDRESS_TO_LOGGABLE_CSTR(address), p_dev_rec->ble.AddressType());

  // Only Resolvable Private Address (RPA) is known, we don't allow it into
  // the background connection procedure.
  return false;
}

const tBLE_BD_ADDR BTM_Sec_GetAddressWithType(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == nullptr || !p_dev_rec->is_device_type_has_ble()) {
    return {
        .type = BLE_ADDR_PUBLIC,
        .bda = bd_addr,
    };
  }

  if (p_dev_rec->ble.identity_address_with_type.bda.IsEmpty()) {
    return {
        .type = p_dev_rec->ble.AddressType(),
        .bda = bd_addr,
    };
  } else {
    // Floss doesn't support LL Privacy (yet). To expedite ARC testing, always
    // connect to the latest LE random address (if available and LL Privacy is
    // not enabled) rather than redesign.
    // TODO(b/235218533): Remove when LL Privacy is implemented.
#if TARGET_FLOSS
    if (!p_dev_rec->ble.cur_rand_addr.IsEmpty() &&
        btm_cb.ble_ctr_cb.privacy_mode < BTM_PRIVACY_1_2) {
      return {
          .type = BLE_ADDR_RANDOM,
          .bda = p_dev_rec->ble.cur_rand_addr,
      };
    }
#endif
    return p_dev_rec->ble.identity_address_with_type;
  }
}

bool BTM_IsRemoteNameKnown(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  return (p_dev_rec == nullptr) ? false : p_dev_rec->sec_rec.is_name_known();
}

namespace bluetooth {
namespace testing {
namespace legacy {

void wipe_secrets_and_remove(tBTM_SEC_DEV_REC* p_dev_rec) {
  ::wipe_secrets_and_remove(p_dev_rec);
}

}  // namespace legacy
}  // namespace testing
}  // namespace bluetooth
