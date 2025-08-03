/*
 * Copyright 2023 The Android Open Source Project
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

#define LOG_TAG "bt_bta_dm_sec"

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/dm/bta_dm_act.h"
#include "bta/dm/bta_dm_disc.h"
#include "bta/dm/bta_dm_int.h"
#include "bta/dm/bta_dm_sec_int.h"
#include "bta/include/bta_dm_ci.h"  // bta_dm_ci_rmt_oob
#include "btif/include/btif_dm.h"
#include "btif/include/btif_storage.h"
#include "internal_include/bt_target.h"
#include "osi/include/compat.h"  // strlcpy
#include "osi/include/osi.h"     // UNUSED_ATTR
#include "stack/include/bt_dev_class.h"
#include "stack/include/btm_ble_sec_api_types.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/btm_sec_api.h"
#include "stack/include/gatt_api.h"
#include "stack/include/security_client_callbacks.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

using namespace bluetooth;

static tBTM_STATUS bta_dm_sp_cback(tBTM_SP_EVT event, tBTM_SP_EVT_DATA* p_data);
static uint8_t bta_dm_ble_smp_cback(tBTM_LE_EVT event, const RawAddress& bda,
                                    tBTM_LE_EVT_DATA* p_data);
static uint8_t bta_dm_new_link_key_cback(const RawAddress& bd_addr,
                                         DEV_CLASS dev_class,
                                         tBTM_BD_NAME bd_name,
                                         const LinkKey& key, uint8_t key_type,
                                         bool is_ctkd);
static uint8_t bta_dm_pin_cback(const RawAddress& bd_addr, DEV_CLASS dev_class,
                                const tBTM_BD_NAME bd_name, bool min_16_digit);
static uint8_t bta_dm_sirk_verifiction_cback(const RawAddress& bd_addr);
static void bta_dm_authentication_complete_cback(const RawAddress& bd_addr,
                                                 DEV_CLASS dev_class,
                                                 tBTM_BD_NAME bd_name,
                                                 tHCI_REASON result);
static void bta_dm_ble_id_key_cback(uint8_t key_type,
                                    tBTM_BLE_LOCAL_KEYS* p_key);
static void bta_dm_bond_cancel_complete_cback(tBTM_STATUS result);
static void bta_dm_remove_sec_dev_entry(const RawAddress& remote_bd_addr);
static void bta_dm_reset_sec_dev_pending(const RawAddress& remote_bd_addr);

/* bta security callback */
const tBTM_APPL_INFO bta_security = {
    .p_pin_callback = &bta_dm_pin_cback,
    .p_link_key_callback = &bta_dm_new_link_key_cback,
    .p_auth_complete_callback = &bta_dm_authentication_complete_cback,
    .p_bond_cancel_cmpl_callback = &bta_dm_bond_cancel_complete_cback,
    .p_sp_callback = &bta_dm_sp_cback,
    .p_le_callback = &bta_dm_ble_smp_cback,
    .p_le_key_callback = &bta_dm_ble_id_key_cback,
    .p_sirk_verification_callback = &bta_dm_sirk_verifiction_cback};

// Stores the local Input/Output Capabilities of the Bluetooth device.
static uint8_t btm_local_io_caps;

void btm_sec_on_hw_on() {
  tBTA_DM_SEC_CBACK* temp_sec_cback = bta_dm_sec_cb.p_sec_cback;
  bta_dm_sec_cb = {};
  bta_dm_sec_cb.p_sec_cback = temp_sec_cback;
}

void bta_dm_ble_sirk_sec_cb_register(tBTA_DM_SEC_CBACK* p_cback) {
  /* Save the callback to be called when a request of member validation will be
   * needed. */
  bta_dm_sec_cb.p_sec_sirk_cback = p_cback;
}

void bta_dm_ble_sirk_confirm_device_reply(const RawAddress& bd_addr,
                                          bool accept) {
  log::debug("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  get_btm_client_interface().security.BTM_BleSirkConfirmDeviceReply(
      bd_addr, accept ? BTM_SUCCESS : BTM_NOT_AUTHORIZED);
}

void bta_dm_consolidate(const RawAddress& identity_addr,
                        const RawAddress& rpa) {
  for (auto i = 0; i < bta_dm_cb.device_list.count; i++) {
    if (bta_dm_cb.device_list.peer_device[i].peer_bdaddr != rpa) continue;

    log::info("consolidating bda_dm_cb record {} -> {}",
              ADDRESS_TO_LOGGABLE_CSTR(rpa),
              ADDRESS_TO_LOGGABLE_CSTR(identity_addr));
    bta_dm_cb.device_list.peer_device[i].peer_bdaddr = identity_addr;
  }
}

void btm_dm_sec_init() {
  get_btm_client_interface().security.BTM_SecRegister(&bta_security);
}

/** Initialises the BT device security manager */
void bta_dm_sec_enable(tBTA_DM_SEC_CBACK* p_sec_cback) {
  /* make sure security callback is saved - if no callback, do not erase the
  previous one,
  it could be an error recovery mechanism */
  if (p_sec_cback != NULL) bta_dm_sec_cb.p_sec_cback = p_sec_cback;

  btm_local_io_caps = btif_storage_get_local_io_caps();
}

void bta_dm_remote_key_missing(const RawAddress bd_addr) {
  if (bta_dm_sec_cb.p_sec_cback) {
    tBTA_DM_SEC sec_event;
    sec_event.key_missing.bd_addr = bd_addr;
    bta_dm_sec_cb.p_sec_cback(BTA_DM_KEY_MISSING_EVT, &sec_event);
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_add_device
 *
 * Description      This function adds a Link Key to an security database entry.
 *                  It is normally called during host startup to restore all
 *                  required information stored in the NVRAM.
 ******************************************************************************/
void bta_dm_add_device(std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg) {
  DEV_CLASS dc = kDevClassEmpty;
  LinkKey* p_lc = NULL;

  /* If not all zeros, the device class has been specified */
  if (msg->dc_known) dc = msg->dc;

  if (msg->link_key_known) p_lc = &msg->link_key;

  auto add_result = get_btm_client_interface().security.BTM_SecAddDevice(
      msg->bd_addr, dc, msg->bd_name, nullptr, p_lc, msg->key_type,
      msg->pin_length);
  if (!add_result) {
    log::error("Error adding device:{}",
               ADDRESS_TO_LOGGABLE_CSTR(msg->bd_addr));
  }
}

/** Bonds with peer device */
void bta_dm_bond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                 tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  log::debug("Bonding with peer device:{} type:{} transport:{} type:{}",
             ADDRESS_TO_LOGGABLE_CSTR(bd_addr), AddressTypeText(addr_type),
             bt_transport_text(transport), DeviceTypeText(device_type));

  tBTA_DM_SEC sec_event;
  const char* p_name;

  tBTM_STATUS status = get_btm_client_interface().security.BTM_SecBond(
      bd_addr, addr_type, transport, device_type);

  if (bta_dm_sec_cb.p_sec_cback && (status != BTM_CMD_STARTED)) {
    memset(&sec_event, 0, sizeof(tBTA_DM_SEC));
    sec_event.auth_cmpl.bd_addr = bd_addr;
    p_name = get_btm_client_interface().security.BTM_SecReadDevName(bd_addr);
    if (p_name != NULL) {
      memcpy(sec_event.auth_cmpl.bd_name, p_name, BD_NAME_LEN);
      sec_event.auth_cmpl.bd_name[BD_NAME_LEN] = 0;
    }

    /*      taken care of by memset [above]
            sec_event.auth_cmpl.key_present = false;
            sec_event.auth_cmpl.success = false;
    */
    sec_event.auth_cmpl.fail_reason = HCI_ERR_ILLEGAL_COMMAND;
    if (status == BTM_SUCCESS) {
      sec_event.auth_cmpl.success = true;
    } else {
      /* delete this device entry from Sec Dev DB */
      bta_dm_remove_sec_dev_entry(bd_addr);
    }
    bta_dm_sec_cb.p_sec_cback(BTA_DM_AUTH_CMPL_EVT, &sec_event);
  }
}

/** Cancels bonding with a peer device */
void bta_dm_bond_cancel(const RawAddress& bd_addr) {
  tBTM_STATUS status;
  tBTA_DM_SEC sec_event;

  log::debug("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  status = get_btm_client_interface().security.BTM_SecBondCancel(bd_addr);

  if (bta_dm_sec_cb.p_sec_cback &&
      (status != BTM_CMD_STARTED && status != BTM_SUCCESS)) {
    sec_event.bond_cancel_cmpl.result = BTA_FAILURE;

    bta_dm_sec_cb.p_sec_cback(BTA_DM_BOND_CANCEL_CMPL_EVT, &sec_event);
  }
}

/** Send the pin_reply to a request from BTM */
void bta_dm_pin_reply(std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg) {
  if (msg->accept) {
    get_btm_client_interface().security.BTM_PINCodeReply(
        msg->bd_addr, BTM_SUCCESS, msg->pin_len, msg->p_pin);
  } else {
    get_btm_client_interface().security.BTM_PINCodeReply(
        msg->bd_addr, BTM_NOT_AUTHORIZED, 0, NULL);
  }
}

/** Send the user confirm request reply in response to a request from BTM */
void bta_dm_confirm(const RawAddress& bd_addr, bool accept) {
  get_btm_client_interface().security.BTM_SecConfirmReqReply(
      accept ? BTM_SUCCESS : BTM_NOT_AUTHORIZED, BT_TRANSPORT_BR_EDR, bd_addr);
}

/** respond to the OOB data request for the remote device from BTM */
void bta_dm_ci_rmt_oob_act(std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg) {
  get_btm_client_interface().security.BTM_RemoteOobDataReply(
      msg->accept ? BTM_SUCCESS : BTM_NOT_AUTHORIZED, msg->bd_addr, msg->c,
      msg->r);
}

/*******************************************************************************
 *
 * Function         bta_dm_pinname_cback
 *
 * Description      Callback requesting pin_key
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_pinname_cback(const tBTM_REMOTE_DEV_NAME* p_data) {
  tBTM_REMOTE_DEV_NAME* p_result = (tBTM_REMOTE_DEV_NAME*)p_data;
  tBTA_DM_SEC sec_event;
  uint32_t bytes_to_copy;
  tBTA_DM_SEC_EVT event = bta_dm_sec_cb.pin_evt;

  if (BTA_DM_SP_CFM_REQ_EVT == event) {
    /* Retrieved saved device class and bd_addr */
    sec_event.cfm_req.bd_addr = bta_dm_sec_cb.pin_bd_addr;
    sec_event.cfm_req.dev_class = bta_dm_sec_cb.pin_dev_class;

    if (p_result && p_result->status == BTM_SUCCESS) {
      bytes_to_copy =
          (p_result->length < BD_NAME_LEN) ? p_result->length : BD_NAME_LEN;
      memcpy(sec_event.cfm_req.bd_name, p_result->remote_bd_name,
             bytes_to_copy);
      sec_event.pin_req.bd_name[BD_NAME_LEN] = 0;
    } else /* No name found */
      sec_event.cfm_req.bd_name[0] = 0;

    sec_event.key_notif.passkey =
        bta_dm_sec_cb.num_val; /* get PIN code numeric number */

    /* 1 additional event data fields for this event */
    sec_event.cfm_req.just_works = bta_dm_sec_cb.just_works;
    /* retrieve the loc and rmt caps */
    sec_event.cfm_req.loc_io_caps = bta_dm_sec_cb.loc_io_caps;
    sec_event.cfm_req.rmt_io_caps = bta_dm_sec_cb.rmt_io_caps;
    sec_event.cfm_req.loc_auth_req = bta_dm_sec_cb.loc_auth_req;
    sec_event.cfm_req.rmt_auth_req = bta_dm_sec_cb.rmt_auth_req;

  } else {
    /* Retrieved saved device class and bd_addr */
    sec_event.pin_req.bd_addr = bta_dm_sec_cb.pin_bd_addr;
    sec_event.pin_req.dev_class = bta_dm_sec_cb.pin_dev_class;

    if (p_result && p_result->status == BTM_SUCCESS) {
      bytes_to_copy = (p_result->length < BD_NAME_LEN) ? p_result->length
                                                       : (BD_NAME_LEN - 1);
      memcpy(sec_event.pin_req.bd_name, p_result->remote_bd_name,
             bytes_to_copy);
      sec_event.pin_req.bd_name[BD_NAME_LEN] = 0;
    } else /* No name found */
      sec_event.pin_req.bd_name[0] = 0;

    event = bta_dm_sec_cb.pin_evt;
    sec_event.key_notif.passkey =
        bta_dm_sec_cb.num_val; /* get PIN code numeric number */
  }

  if (bta_dm_sec_cb.p_sec_cback) bta_dm_sec_cb.p_sec_cback(event, &sec_event);
}

/*******************************************************************************
 *
 * Function         bta_dm_pin_cback
 *
 * Description      Callback requesting pin_key
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t bta_dm_pin_cback(const RawAddress& bd_addr, DEV_CLASS dev_class,
                                const tBTM_BD_NAME bd_name, bool min_16_digit) {
  if (!bta_dm_sec_cb.p_sec_cback) return BTM_NOT_AUTHORIZED;

  /* If the device name is not known, save bdaddr and devclass and initiate a
   * name request */
  if (bd_name[0] == 0) {
    bta_dm_sec_cb.pin_evt = BTA_DM_PIN_REQ_EVT;
    bta_dm_sec_cb.pin_bd_addr = bd_addr;
    bta_dm_sec_cb.pin_dev_class = dev_class;
    if ((get_btm_client_interface().peer.BTM_ReadRemoteDeviceName(
            bd_addr, bta_dm_pinname_cback, BT_TRANSPORT_BR_EDR)) ==
        BTM_CMD_STARTED)
      return BTM_CMD_STARTED;

    log::warn("Failed to start Remote Name Request, addr:{}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  }

  tBTA_DM_SEC sec_event = {.pin_req = {
                               .bd_addr = bd_addr,
                               .dev_class = dev_class,
                               .bd_name = "",
                               .min_16_digit = min_16_digit,
                           }};
  strlcpy((char*)sec_event.pin_req.bd_name, (char*)bd_name, BD_NAME_LEN + 1);

  bta_dm_sec_cb.p_sec_cback(BTA_DM_PIN_REQ_EVT, &sec_event);
  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         bta_dm_new_link_key_cback
 *
 * Description      Callback from BTM to notify new link key
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t bta_dm_new_link_key_cback(const RawAddress& bd_addr,
                                         UNUSED_ATTR DEV_CLASS dev_class,
                                         tBTM_BD_NAME bd_name,
                                         const LinkKey& key, uint8_t key_type,
                                         bool is_ctkd) {
  tBTA_DM_SEC sec_event;
  tBTA_DM_AUTH_CMPL* p_auth_cmpl;
  tBTA_DM_SEC_EVT event = BTA_DM_AUTH_CMPL_EVT;

  memset(&sec_event, 0, sizeof(tBTA_DM_SEC));

  p_auth_cmpl = &sec_event.auth_cmpl;

  p_auth_cmpl->bd_addr = bd_addr;

  memcpy(p_auth_cmpl->bd_name, bd_name, BD_NAME_LEN);
  p_auth_cmpl->bd_name[BD_NAME_LEN] = 0;
  p_auth_cmpl->key_present = true;
  p_auth_cmpl->key_type = key_type;
  p_auth_cmpl->success = true;
  p_auth_cmpl->key = key;
  p_auth_cmpl->is_ctkd = is_ctkd;

  sec_event.auth_cmpl.fail_reason = HCI_SUCCESS;

  // Report the BR link key based on the BR/EDR address and type
  get_btm_client_interface().peer.BTM_ReadDevInfo(
      bd_addr, &sec_event.auth_cmpl.dev_type, &sec_event.auth_cmpl.addr_type);
  if (bta_dm_sec_cb.p_sec_cback) bta_dm_sec_cb.p_sec_cback(event, &sec_event);

  // Setting remove_dev_pending flag to false, where it will avoid deleting
  // the
  // security device record when the ACL connection link goes down in case of
  // reconnection.
  if (bta_dm_cb.device_list.count)
    bta_dm_reset_sec_dev_pending(p_auth_cmpl->bd_addr);

  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         bta_dm_authentication_complete_cback
 *
 * Description      Authentication complete callback from BTM
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_authentication_complete_cback(
    const RawAddress& bd_addr, UNUSED_ATTR DEV_CLASS dev_class,
    tBTM_BD_NAME bd_name, tHCI_REASON reason) {
  if (reason != HCI_SUCCESS) {
    if (bta_dm_sec_cb.p_sec_cback) {
      // Build out the security event data structure
      tBTA_DM_SEC sec_event = {
          .auth_cmpl =
              {
                  .bd_addr = bd_addr,
              },
      };
      memcpy(sec_event.auth_cmpl.bd_name, bd_name, BD_NAME_LEN);
      sec_event.auth_cmpl.bd_name[BD_NAME_LEN] = 0;

      // Report the BR link key based on the BR/EDR address and type
      get_btm_client_interface().peer.BTM_ReadDevInfo(
          bd_addr, &sec_event.auth_cmpl.dev_type,
          &sec_event.auth_cmpl.addr_type);
      sec_event.auth_cmpl.fail_reason = reason;

      bta_dm_sec_cb.p_sec_cback(BTA_DM_AUTH_CMPL_EVT, &sec_event);
    }

    switch (reason) {
      case HCI_ERR_AUTH_FAILURE:
      case HCI_ERR_KEY_MISSING:
      case HCI_ERR_HOST_REJECT_SECURITY:
      case HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE:
        log::warn("authentication failed entry:{}, reason:{}",
                  ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
                  hci_reason_code_text(reason));
        break;

      default:
        break;
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_sp_cback
 *
 * Description      simple pairing callback from BTM
 *
 * Returns          void
 *
 ******************************************************************************/
static tBTM_STATUS bta_dm_sp_cback(tBTM_SP_EVT event,
                                   tBTM_SP_EVT_DATA* p_data) {
  tBTM_STATUS status = BTM_CMD_STARTED;
  tBTA_DM_SEC sec_event = {};
  tBTA_DM_SEC_EVT pin_evt = BTA_DM_SP_KEY_NOTIF_EVT;

  log::verbose("event:{}", sp_evt_to_text(event));
  if (!bta_dm_sec_cb.p_sec_cback) return BTM_NOT_AUTHORIZED;

  bool sp_rmt_result = false;
  /* TODO_SP */
  switch (event) {
    case BTM_SP_IO_REQ_EVT:
      if (btm_local_io_caps != BTM_IO_CAP_NONE) {
        /* translate auth_req */
        btif_dm_set_oob_for_io_req(&p_data->io_req.oob_data);
        btif_dm_proc_io_req(&p_data->io_req.auth_req, p_data->io_req.is_orig);
      }
      log::verbose("io mitm: {} oob_data:{}", p_data->io_req.auth_req,
                   p_data->io_req.oob_data);
      break;
    case BTM_SP_IO_RSP_EVT:
      if (btm_local_io_caps != BTM_IO_CAP_NONE) {
        btif_dm_proc_io_rsp(p_data->io_rsp.bd_addr, p_data->io_rsp.io_cap,
                            p_data->io_rsp.oob_data, p_data->io_rsp.auth_req);
      }
      break;

    case BTM_SP_CFM_REQ_EVT:
      pin_evt = BTA_DM_SP_CFM_REQ_EVT;
      bta_dm_sec_cb.just_works = sec_event.cfm_req.just_works =
          p_data->cfm_req.just_works;
      sec_event.cfm_req.loc_auth_req = p_data->cfm_req.loc_auth_req;
      sec_event.cfm_req.rmt_auth_req = p_data->cfm_req.rmt_auth_req;
      sec_event.cfm_req.loc_io_caps = p_data->cfm_req.loc_io_caps;
      sec_event.cfm_req.rmt_io_caps = p_data->cfm_req.rmt_io_caps;

      [[fallthrough]];
    /* Passkey entry mode, mobile device with output capability is very
        unlikely to receive key request, so skip this event */
    /*case BTM_SP_KEY_REQ_EVT: */
    case BTM_SP_KEY_NOTIF_EVT:
      if (btm_local_io_caps == BTM_IO_CAP_NONE &&
          BTM_SP_KEY_NOTIF_EVT == event) {
        status = BTM_NOT_AUTHORIZED;
        break;
      }

      // TODO PleaseFix: This assignment only works with event
      // BTM_SP_KEY_NOTIF_EVT
      bta_dm_sec_cb.num_val = sec_event.key_notif.passkey =
          p_data->key_notif.passkey;

      if (BTM_SP_CFM_REQ_EVT == event) {
        /* Due to the switch case falling through below to
           BTM_SP_KEY_NOTIF_EVT,
           copy these values into key_notif from cfm_req */
        sec_event.key_notif.bd_addr = p_data->cfm_req.bd_addr;
        sec_event.key_notif.dev_class = p_data->cfm_req.dev_class;
        bd_name_copy(sec_event.key_notif.bd_name, p_data->cfm_req.bd_name);
        /* Due to the switch case falling through below to BTM_SP_KEY_NOTIF_EVT,
           call remote name request using values from cfm_req */
        if (p_data->cfm_req.bd_name[0] == 0) {
          bta_dm_sec_cb.pin_evt = pin_evt;
          bta_dm_sec_cb.pin_bd_addr = p_data->cfm_req.bd_addr;
          bta_dm_sec_cb.rmt_io_caps = sec_event.cfm_req.rmt_io_caps;
          bta_dm_sec_cb.loc_io_caps = sec_event.cfm_req.loc_io_caps;
          bta_dm_sec_cb.rmt_auth_req = sec_event.cfm_req.rmt_auth_req;
          bta_dm_sec_cb.loc_auth_req = sec_event.cfm_req.loc_auth_req;

          bta_dm_sec_cb.pin_dev_class = p_data->cfm_req.dev_class;
          {
            const tBTM_STATUS btm_status =
                get_btm_client_interface().peer.BTM_ReadRemoteDeviceName(
                    p_data->cfm_req.bd_addr, bta_dm_pinname_cback,
                    BT_TRANSPORT_BR_EDR);
            switch (btm_status) {
              case BTM_CMD_STARTED:
                return btm_status;
              default:
                // NOTE: This will issue callback on this failure path
                log::warn("Failed to start Remote Name Request btm_status:{}",
                          btm_status_text(btm_status));
            };
          }
        }
      }

      if (BTM_SP_KEY_NOTIF_EVT == event) {
        /* If the device name is not known, save bdaddr and devclass
           and initiate a name request with values from key_notif */
        if (p_data->key_notif.bd_name[0] == 0) {
          bta_dm_sec_cb.pin_evt = pin_evt;
          bta_dm_sec_cb.pin_bd_addr = p_data->key_notif.bd_addr;
          bta_dm_sec_cb.pin_dev_class = p_data->key_notif.dev_class;
          if ((get_btm_client_interface().peer.BTM_ReadRemoteDeviceName(
                  p_data->key_notif.bd_addr, bta_dm_pinname_cback,
                  BT_TRANSPORT_BR_EDR)) == BTM_CMD_STARTED)
            return BTM_CMD_STARTED;
          log::warn("Failed to start Remote Name Request, addr:{}",
                    ADDRESS_TO_LOGGABLE_CSTR(p_data->key_notif.bd_addr));
        } else {
          sec_event.key_notif.bd_addr = p_data->key_notif.bd_addr;
          sec_event.key_notif.dev_class = p_data->key_notif.dev_class;
          strlcpy((char*)sec_event.key_notif.bd_name,
                  (char*)p_data->key_notif.bd_name, BD_NAME_LEN + 1);
          sec_event.key_notif.bd_name[BD_NAME_LEN] = 0;
        }
      }

      bta_dm_sec_cb.p_sec_cback(pin_evt, &sec_event);

      break;

    case BTM_SP_LOC_OOB_EVT:
      btif_dm_proc_loc_oob(BT_TRANSPORT_BR_EDR,
                           (bool)(p_data->loc_oob.status == BTM_SUCCESS),
                           p_data->loc_oob.c, p_data->loc_oob.r);
      break;

    case BTM_SP_RMT_OOB_EVT: {
      Octet16 c;
      Octet16 r;
      sp_rmt_result = false;
      sp_rmt_result = btif_dm_proc_rmt_oob(p_data->rmt_oob.bd_addr, &c, &r);
      log::verbose("result={}", sp_rmt_result);
      bta_dm_ci_rmt_oob(sp_rmt_result, p_data->rmt_oob.bd_addr, c, r);
      break;
    }

    default:
      status = BTM_NOT_AUTHORIZED;
      break;
  }
  log::verbose("dm status:{}", status);
  return status;
}

/*******************************************************************************
 *
 * Function         bta_dm_reset_sec_dev_pending
 *
 * Description      Setting the remove device pending status to false from
 *                  security device DB, when the link key notification
 *                  event comes.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_reset_sec_dev_pending(const RawAddress& remote_bd_addr) {
  for (size_t i = 0; i < bta_dm_cb.device_list.count; i++) {
    auto& dev = bta_dm_cb.device_list.peer_device[i];
    if (dev.peer_bdaddr == remote_bd_addr) {
      if (dev.remove_dev_pending) {
        log::info("Clearing remove_dev_pending for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(dev.peer_bdaddr));
        dev.remove_dev_pending = false;
      }
      return;
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_remove_sec_dev_entry
 *
 * Description      Removes device entry from Security device DB if ACL
 connection with
 *                  remtoe device does not exist, else schedule for dev entry
 removal upon
                     ACL close
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_remove_sec_dev_entry(const RawAddress& remote_bd_addr) {
  if (get_btm_client_interface().peer.BTM_IsAclConnectionUp(remote_bd_addr,
                                                            BT_TRANSPORT_LE) ||
      get_btm_client_interface().peer.BTM_IsAclConnectionUp(
          remote_bd_addr, BT_TRANSPORT_BR_EDR)) {
    log::debug("ACL is not down. Schedule for Dev Removal when ACL closes:{}",
               ADDRESS_TO_LOGGABLE_CSTR(remote_bd_addr));
    get_btm_client_interface().security.BTM_SecClearSecurityFlags(
        remote_bd_addr);
    for (int i = 0; i < bta_dm_cb.device_list.count; i++) {
      auto& dev = bta_dm_cb.device_list.peer_device[i];
      if (dev.peer_bdaddr == remote_bd_addr) {
        log::info("Setting remove_dev_pending for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(dev.peer_bdaddr));
        dev.remove_dev_pending = TRUE;
        break;
      }
    }
  } else {
    // remote_bd_addr comes from security record, which is removed in
    // BTM_SecDeleteDevice.
    RawAddress addr_copy = remote_bd_addr;
    bta_dm_process_remove_device_no_callback(addr_copy);
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_bond_cancel_complete_cback
 *
 * Description      Authentication complete callback from BTM
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_bond_cancel_complete_cback(tBTM_STATUS result) {
  tBTA_DM_SEC sec_event;

  if (result == BTM_SUCCESS)
    sec_event.bond_cancel_cmpl.result = BTA_SUCCESS;
  else
    sec_event.bond_cancel_cmpl.result = BTA_FAILURE;

  if (bta_dm_sec_cb.p_sec_cback) {
    bta_dm_sec_cb.p_sec_cback(BTA_DM_BOND_CANCEL_CMPL_EVT, &sec_event);
  }
}

static void ble_io_req(const RawAddress& bd_addr, tBTM_IO_CAP* p_io_cap,
                       tBTM_OOB_DATA* p_oob_data, tBTM_LE_AUTH_REQ* p_auth_req,
                       uint8_t* p_max_key_size, tBTM_LE_KEY_TYPE* p_init_key,
                       tBTM_LE_KEY_TYPE* p_resp_key) {
  /* Retrieve the properties from file system if possible */
  tBTE_APPL_CFG nv_config;
  if (btif_dm_get_smp_config(&nv_config)) bte_appl_cfg = nv_config;

  /* *p_auth_req by default is false for devices with NoInputNoOutput; true for
   * other devices. */

  if (bte_appl_cfg.ble_auth_req)
    *p_auth_req = bte_appl_cfg.ble_auth_req |
                  (bte_appl_cfg.ble_auth_req & 0x04) | ((*p_auth_req) & 0x04);

  /* if OOB is not supported, this call-out function does not need to do
   * anything
   * otherwise, look for the OOB data associated with the address and set
   * *p_oob_data accordingly.
   * If the answer can not be obtained right away,
   * set *p_oob_data to BTA_OOB_UNKNOWN and call bta_dm_ci_io_req() when the
   * answer is available.
   */

  btif_dm_set_oob_for_le_io_req(bd_addr, p_oob_data, p_auth_req);

  if (bte_appl_cfg.ble_io_cap <= 4)
    *p_io_cap = static_cast<tBTM_IO_CAP>(bte_appl_cfg.ble_io_cap);

  if (bte_appl_cfg.ble_init_key <= BTM_BLE_INITIATOR_KEY_SIZE)
    *p_init_key = bte_appl_cfg.ble_init_key;

  if (bte_appl_cfg.ble_resp_key <= BTM_BLE_RESPONDER_KEY_SIZE)
    *p_resp_key = bte_appl_cfg.ble_resp_key;

  if (bte_appl_cfg.ble_max_key_size > 7 && bte_appl_cfg.ble_max_key_size <= 16)
    *p_max_key_size = bte_appl_cfg.ble_max_key_size;
}

/*******************************************************************************
 *
 * Function         bta_dm_ble_smp_cback
 *
 * Description      Callback for BLE SMP
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t bta_dm_ble_smp_cback(tBTM_LE_EVT event, const RawAddress& bda,
                                    tBTM_LE_EVT_DATA* p_data) {
  tBTM_STATUS status = BTM_SUCCESS;
  tBTA_DM_SEC sec_event;
  const char* p_name = NULL;

  log::debug("addr:{},event:{}", ADDRESS_TO_LOGGABLE_CSTR(bda),
             ble_evt_to_text(event));

  if (!bta_dm_sec_cb.p_sec_cback) return BTM_NOT_AUTHORIZED;

  memset(&sec_event, 0, sizeof(tBTA_DM_SEC));
  switch (event) {
    case BTM_LE_IO_REQ_EVT:
      ble_io_req(bda, &p_data->io_req.io_cap, &p_data->io_req.oob_data,
                 &p_data->io_req.auth_req, &p_data->io_req.max_key_size,
                 &p_data->io_req.init_keys, &p_data->io_req.resp_keys);
      log::info("io mitm:{} oob_data:{}", p_data->io_req.auth_req,
                p_data->io_req.oob_data);
      break;

    case BTM_LE_CONSENT_REQ_EVT:
      sec_event.ble_req.bd_addr = bda;
      p_name = get_btm_client_interface().security.BTM_SecReadDevName(bda);
      if (p_name != NULL)
        strlcpy((char*)sec_event.ble_req.bd_name, p_name, BD_NAME_LEN);
      else
        sec_event.ble_req.bd_name[0] = 0;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_CONSENT_REQ_EVT, &sec_event);
      break;

    case BTM_LE_SEC_REQUEST_EVT:
      sec_event.ble_req.bd_addr = bda;
      p_name = get_btm_client_interface().security.BTM_SecReadDevName(bda);
      if (p_name != NULL)
        strlcpy((char*)sec_event.ble_req.bd_name, p_name, BD_NAME_LEN + 1);
      else
        sec_event.ble_req.bd_name[0] = 0;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_SEC_REQ_EVT, &sec_event);
      break;

    case BTM_LE_KEY_NOTIF_EVT:
      sec_event.key_notif.bd_addr = bda;
      p_name = get_btm_client_interface().security.BTM_SecReadDevName(bda);
      if (p_name != NULL)
        strlcpy((char*)sec_event.key_notif.bd_name, p_name, BD_NAME_LEN + 1);
      else
        sec_event.key_notif.bd_name[0] = 0;
      sec_event.key_notif.passkey = p_data->key_notif;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_PASSKEY_NOTIF_EVT, &sec_event);
      break;

    case BTM_LE_KEY_REQ_EVT:
      sec_event.ble_req.bd_addr = bda;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_PASSKEY_REQ_EVT, &sec_event);
      break;

    case BTM_LE_OOB_REQ_EVT:
      sec_event.ble_req.bd_addr = bda;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_OOB_REQ_EVT, &sec_event);
      break;

    case BTM_LE_NC_REQ_EVT:
      sec_event.key_notif.bd_addr = bda;
      // TODO: get rid of this
      strlcpy((char*)sec_event.key_notif.bd_name, bta_dm_get_remname(),
              (BD_NAME_LEN + 1));
      sec_event.key_notif.passkey = p_data->key_notif;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_NC_REQ_EVT, &sec_event);
      break;

    case BTM_LE_SC_OOB_REQ_EVT:
      sec_event.ble_req.bd_addr = bda;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_SC_OOB_REQ_EVT, &sec_event);
      break;

    case BTM_LE_SC_LOC_OOB_EVT:
      tBTA_DM_LOC_OOB_DATA local_oob_data;
      local_oob_data.local_oob_c = p_data->local_oob_data.commitment;
      local_oob_data.local_oob_r = p_data->local_oob_data.randomizer;
      sec_event.local_oob_data = local_oob_data;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_SC_CR_LOC_OOB_EVT, &sec_event);
      break;

    case BTM_LE_KEY_EVT:
      sec_event.ble_key.bd_addr = bda;
      sec_event.ble_key.key_type = p_data->key.key_type;
      sec_event.ble_key.p_key_value = p_data->key.p_key_value;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_KEY_EVT, &sec_event);
      break;

    case BTM_LE_COMPLT_EVT:
      sec_event.auth_cmpl.bd_addr = bda;
      get_btm_client_interface().peer.BTM_ReadDevInfo(
          bda, &sec_event.auth_cmpl.dev_type, &sec_event.auth_cmpl.addr_type);
      p_name = get_btm_client_interface().security.BTM_SecReadDevName(bda);
      if (p_name != NULL)
        strlcpy((char*)sec_event.auth_cmpl.bd_name, p_name, (BD_NAME_LEN + 1));
      else
        sec_event.auth_cmpl.bd_name[0] = 0;

      if (p_data->complt.reason != HCI_SUCCESS) {
        // TODO This is not a proper use of this type
        sec_event.auth_cmpl.fail_reason =
            static_cast<tHCI_STATUS>(BTA_DM_AUTH_CONVERT_SMP_CODE(
                (static_cast<uint8_t>(p_data->complt.reason))));

        if (btm_sec_is_a_bonded_dev(bda) &&
            p_data->complt.reason == SMP_CONN_TOUT &&
            !p_data->complt.smp_over_br) {
          // Bonded device failed to encrypt - to test this remove battery from
          // HID device right after connection, but before encryption is
          // established
          log::warn(
              "bonded device disconnected when encrypting - no reason to "
              "unbond");
        } else {
          /* delete this device entry from Sec Dev DB */
          bta_dm_remove_sec_dev_entry(bda);
        }

      } else {
        sec_event.auth_cmpl.success = true;
        if (!p_data->complt.smp_over_br)
          GATT_ConfigServiceChangeCCC(bda, true, BT_TRANSPORT_LE);
      }

      if (bta_dm_sec_cb.p_sec_cback) {
        // bta_dm_sec_cb.p_sec_cback(BTA_DM_AUTH_CMPL_EVT, &sec_event);
        bta_dm_sec_cb.p_sec_cback(BTA_DM_BLE_AUTH_CMPL_EVT, &sec_event);
      }
      break;

    case BTM_LE_ADDR_ASSOC_EVT:
      sec_event.proc_id_addr.pairing_bda = bda;
      sec_event.proc_id_addr.id_addr = p_data->id_addr;
      bta_dm_sec_cb.p_sec_cback(BTA_DM_LE_ADDR_ASSOC_EVT, &sec_event);
      break;

    default:
      status = BTM_NOT_AUTHORIZED;
      break;
  }
  return status;
}

/*******************************************************************************
 *
 * Function         bta_dm_encrypt_cback
 *
 * Description      link encryption complete callback.
 *
 * Returns         None
 *
 ******************************************************************************/
void bta_dm_encrypt_cback(const RawAddress* bd_addr, tBT_TRANSPORT transport,
                          UNUSED_ATTR void* p_ref_data, tBTM_STATUS result) {
  tBTA_DM_ENCRYPT_CBACK* p_callback = nullptr;
  tBTA_DM_PEER_DEVICE* device = find_connected_device(*bd_addr, transport);
  if (device != nullptr) {
    p_callback = device->p_encrypt_cback;
    device->p_encrypt_cback = nullptr;
  }

  log::debug("Encrypted:{:c}, peer:{} transport:{} status:{} callback:{:c}",
             result == BTM_SUCCESS ? 'T' : 'F',
             ADDRESS_TO_LOGGABLE_CSTR((*bd_addr)), bt_transport_text(transport),
             btm_status_text(result), (p_callback) ? 'T' : 'F');

  tBTA_STATUS bta_status = BTA_SUCCESS;
  switch (result) {
    case BTM_SUCCESS:
      break;
    case BTM_WRONG_MODE:
      bta_status = BTA_WRONG_MODE;
      break;
    case BTM_NO_RESOURCES:
      bta_status = BTA_NO_RESOURCES;
      break;
    case BTM_BUSY:
      bta_status = BTA_BUSY;
      break;
    default:
      bta_status = BTA_FAILURE;
      break;
  }

  if (p_callback) {
    (*p_callback)(*bd_addr, transport, bta_status);
  }
}

/**This function to encrypt the link */
void bta_dm_set_encryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                           tBTA_DM_ENCRYPT_CBACK* p_callback,
                           tBTM_BLE_SEC_ACT sec_act) {
  if (p_callback == nullptr) {
    log::error("callback is not provided,addr:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }

  tBTA_DM_PEER_DEVICE* device = find_connected_device(bd_addr, transport);
  if (device == nullptr) {
    log::error("Unable to find active ACL connection device:{} transport:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), bt_transport_text(transport));
    return;
  }

  if (device->p_encrypt_cback) {
    log::error(
        "Unable to start encryption as already in progress peer:{} "
        "transport:{}",
        ADDRESS_TO_LOGGABLE_CSTR(bd_addr), bt_transport_text(transport));
    (*p_callback)(bd_addr, transport, BTA_BUSY);
    return;
  }

  if (get_btm_client_interface().security.BTM_SetEncryption(
          bd_addr, transport, bta_dm_encrypt_cback, NULL, sec_act) ==
      BTM_CMD_STARTED) {
    device->p_encrypt_cback = p_callback;
    log::debug("Started encryption peer:{} transport:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), bt_transport_text(transport));
  } else {
    log::error("Unable to start encryption process peer:{} transport:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), bt_transport_text(transport));
  }
}

/*******************************************************************************
 *
 * Function         bta_dm_ble_id_key_cback
 *
 * Description      Callback for BLE local ID keys
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_dm_ble_id_key_cback(uint8_t key_type,
                                    tBTM_BLE_LOCAL_KEYS* p_key) {
  switch (key_type) {
    case BTM_BLE_KEY_TYPE_ID:
    case BTM_BLE_KEY_TYPE_ER:
      if (bta_dm_sec_cb.p_sec_cback) {
        tBTA_DM_SEC dm_key = {
            .ble_id_keys = {},
        };
        memcpy(&dm_key.ble_id_keys, p_key, sizeof(tBTM_BLE_LOCAL_KEYS));

        tBTA_DM_SEC_EVT evt = (key_type == BTM_BLE_KEY_TYPE_ID)
                                  ? BTA_DM_BLE_LOCAL_IR_EVT
                                  : BTA_DM_BLE_LOCAL_ER_EVT;
        bta_dm_sec_cb.p_sec_cback(evt, &dm_key);
      }
      break;

    default:
      log::verbose("Unknown key type {}", key_type);
      break;
  }
  return;
}

/*******************************************************************************
 *
 * Function         bta_dm_sirk_verifiction_cback
 *
 * Description      SIRK verification when pairing CSIP set member.
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t bta_dm_sirk_verifiction_cback(const RawAddress& bd_addr) {
  tBTA_DM_SEC sec_event = {.ble_req = {
                               .bd_addr = bd_addr,
                           }};

  if (bta_dm_sec_cb.p_sec_sirk_cback) {
    log::debug("callback called");
    bta_dm_sec_cb.p_sec_sirk_cback(BTA_DM_SIRK_VERIFICATION_REQ_EVT, &sec_event);
    return BTM_CMD_STARTED;
  }

  log::debug("no callback registered");

  return BTM_SUCCESS_NO_SECURITY;
}

/*******************************************************************************
 *
 * Function         bta_dm_add_blekey
 *
 * Description      This function adds a BLE Key to an security database entry.
 *                  This function shall only be called AFTER BTA_DmAddBleDevice
 *                  has been called.
 *                  It is normally called during host startup to restore all
 *                  required information stored in the NVRAM.
 *
 * Parameters:
 *
 ******************************************************************************/
void bta_dm_add_blekey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey,
                       tBTM_LE_KEY_TYPE key_type) {
  get_btm_client_interface().security.BTM_SecAddBleKey(
      bd_addr, (tBTM_LE_KEY_VALUE*)&blekey, key_type);
}

/*******************************************************************************
 *
 * Function         bta_dm_add_ble_device
 *
 * Description      This function adds a BLE device to an security database
 *                  entry.
 *                  It is normally called during host startup to restore all
 *                  required information stored in the NVRAM.
 *
 * Parameters:
 *
 ******************************************************************************/
void bta_dm_add_ble_device(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                           tBT_DEVICE_TYPE dev_type) {
  get_btm_client_interface().security.BTM_SecAddBleDevice(bd_addr, dev_type,
                                                          addr_type);
}

/*******************************************************************************
 *
 * Function         bta_dm_add_ble_device
 *
 * Description      This function adds a BLE device to an security database
 *                  entry.
 *                  It is normally called during host startup to restore all
 *                  required information stored in the NVRAM.
 *
 * Parameters:
 *
 ******************************************************************************/
void bta_dm_ble_passkey_reply(const RawAddress& bd_addr, bool accept,
                              uint32_t passkey) {
  get_btm_client_interface().security.BTM_BlePasskeyReply(
      bd_addr, accept ? BTM_SUCCESS : BTM_NOT_AUTHORIZED, passkey);
}

/** This is response to SM numeric comparison request submitted to application.
 */
void bta_dm_ble_confirm_reply(const RawAddress& bd_addr, bool accept) {
  get_btm_client_interface().security.BTM_SecConfirmReqReply(
      accept ? BTM_SUCCESS : BTM_NOT_AUTHORIZED, BT_TRANSPORT_LE, bd_addr);
}

/** This function set the local device LE privacy settings. */
void bta_dm_ble_config_local_privacy(bool privacy_enable) {
  BTM_BleConfigPrivacy(privacy_enable);
}

namespace bluetooth {
namespace legacy {
namespace testing {
void btm_set_local_io_caps(uint8_t io_caps) { ::btm_local_io_caps = io_caps; }

tBTM_STATUS bta_dm_sp_cback(tBTM_SP_EVT event, tBTM_SP_EVT_DATA* p_data) {
  return ::bta_dm_sp_cback(event, p_data);
}

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth
