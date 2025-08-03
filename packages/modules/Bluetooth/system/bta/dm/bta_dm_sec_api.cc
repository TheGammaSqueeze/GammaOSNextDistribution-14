/******************************************************************************
 *
 * Copyright 2023 The Android Open Source Project
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
 *  This is the API implementation file for the BTA device manager.
 *
 ******************************************************************************/

#include <android_bluetooth_flags.h>
#include <base/functional/bind.h>
#include <bluetooth/log.h>

#include "bta/dm/bta_dm_sec_int.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_ble_sec_api.h"
#include "stack/include/main_thread.h"
#include "types/raw_address.h"

using namespace bluetooth;

/** This function initiates a bonding procedure with a peer device */
void BTA_DmBond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_bond(bd_addr, addr_type, transport, device_type);
  } else {
    do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_bond, bd_addr, addr_type,
                                                transport, device_type));
  }
}

/** This function cancels the bonding procedure with a peer device
 */
void BTA_DmBondCancel(const RawAddress& bd_addr) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_bond_cancel(bd_addr);
  } else {
    do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_bond_cancel, bd_addr));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmPinReply
 *
 * Description      This function provides a pincode for a remote device when
 *                  one is requested by DM through BTA_DM_PIN_REQ_EVT
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmPinReply(const RawAddress& bd_addr, bool accept, uint8_t pin_len,
                    uint8_t* p_pin) {
  std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg =
      std::make_unique<tBTA_DM_API_PIN_REPLY>();

  msg->bd_addr = bd_addr;
  msg->accept = accept;
  if (accept) {
    msg->pin_len = pin_len;
    memcpy(msg->p_pin, p_pin, pin_len);
  }

  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_pin_reply(std::move(msg));
  } else {
    do_in_main_thread(FROM_HERE,
                      base::Bind(bta_dm_pin_reply, base::Passed(&msg)));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmLocalOob
 *
 * Description      This function retrieves the OOB data from local controller.
 *                  The result is reported by:
 *                  - bta_dm_co_loc_oob_ext() if device supports secure
 *                    connections (SC)
 *                  - bta_dm_co_loc_oob() if device doesn't support SC
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmLocalOob(void) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    BTM_ReadLocalOobData();
  } else {
    do_in_main_thread(FROM_HERE, base::BindOnce(BTM_ReadLocalOobData));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmConfirm
 *
 * Description      This function accepts or rejects the numerical value of the
 *                  Simple Pairing process on BTA_DM_SP_CFM_REQ_EVT
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmConfirm(const RawAddress& bd_addr, bool accept) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_confirm(bd_addr, accept);
  } else {
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(bta_dm_confirm, bd_addr, accept));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmAddDevice
 *
 * Description      This function adds a device to the security database list of
 *                  peer device
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmAddDevice(const RawAddress& bd_addr, DEV_CLASS dev_class,
                     const LinkKey& link_key, uint8_t key_type,
                     uint8_t pin_length) {
  std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg =
      std::make_unique<tBTA_DM_API_ADD_DEVICE>();

  msg->bd_addr = bd_addr;
  msg->link_key_known = true;
  msg->key_type = key_type;
  msg->link_key = link_key;

  /* Load device class if specified */
  if (dev_class != kDevClassEmpty) {
    msg->dc_known = true;
    msg->dc = dev_class;
  }

  memset(msg->bd_name, 0, BD_NAME_LEN + 1);
  msg->pin_length = pin_length;

  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_add_device(std::move(msg));
  } else {
    do_in_main_thread(FROM_HERE,
                      base::Bind(bta_dm_add_device, base::Passed(&msg)));
  }
}

/** This function removes a device fromthe security database list of peer
 * device. It manages unpairing even while connected */
tBTA_STATUS BTA_DmRemoveDevice(const RawAddress& bd_addr) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_remove_device(bd_addr);
  } else {
    do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_remove_device, bd_addr));
  }
  return BTA_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTA_DmAddBleKey
 *
 * Description      Add/modify LE device information.  This function will be
 *                  normally called during host startup to restore all required
 *                  information stored in the NVRAM.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  p_le_key         - LE key values.
 *                  key_type         - LE SMP key type.
 *
 * Returns          BTA_SUCCESS if successful
 *                  BTA_FAIL if operation failed.
 *
 ******************************************************************************/
void BTA_DmAddBleKey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
                     tBTM_LE_KEY_TYPE key_type) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_add_blekey(bd_addr, *p_le_key, key_type);
  } else {
    do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_add_blekey, bd_addr,
                                                *p_le_key, key_type));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmAddBleDevice
 *
 * Description      Add a BLE device.  This function will be normally called
 *                  during host startup to restore all required information
 *                  for a LE device stored in the NVRAM.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  dev_type         - Remote device's device type.
 *                  addr_type        - LE device address type.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmAddBleDevice(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                        tBT_DEVICE_TYPE dev_type) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_add_ble_device(bd_addr, addr_type, dev_type);
  } else {
    do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_add_ble_device, bd_addr,
                                                addr_type, dev_type));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmBlePasskeyReply
 *
 * Description      Send BLE SMP passkey reply.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  accept           - passkey entry successful or declined.
 *                  passkey          - passkey value, must be a 6 digit number,
 *                                     can be lead by 0.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBlePasskeyReply(const RawAddress& bd_addr, bool accept,
                           uint32_t passkey) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_ble_passkey_reply(bd_addr, accept, accept ? passkey : 0);
  } else {
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(bta_dm_ble_passkey_reply, bd_addr, accept,
                                     accept ? passkey : 0));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmBleConfirmReply
 *
 * Description      Send BLE SMP SC user confirmation reply.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  accept           - numbers to compare are the same or
 *                                     different.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleConfirmReply(const RawAddress& bd_addr, bool accept) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_ble_confirm_reply(bd_addr, accept);
  } else {
    do_in_main_thread(
        FROM_HERE, base::BindOnce(bta_dm_ble_confirm_reply, bd_addr, accept));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmBleSecurityGrant
 *
 * Description      Grant security request access.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  res              - security grant status.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleSecurityGrant(const RawAddress& bd_addr,
                            tBTA_DM_BLE_SEC_GRANT res) {
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    BTM_SecurityGrant(bd_addr, res);
  } else {
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(BTM_SecurityGrant, bd_addr, res));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmSetEncryption
 *
 * Description      This function is called to ensure that connection is
 *                  encrypted.  Should be called only on an open connection.
 *                  Typically only needed for connections that first want to
 *                  bring up unencrypted links, then later encrypt them.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *                  transport     - transport of the link to be encruypted
 *                  p_callback    - Pointer to callback function to indicat the
 *                                  link encryption status
 *                  sec_act       - This is the security action to indicate
 *                                  what kind of BLE security level is required
 *                                  for the BLE link if BLE is supported.
 *                                  Note: This parameter is ignored for the
 *                                        BR/EDR or if BLE is not supported.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSetEncryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                         tBTA_DM_ENCRYPT_CBACK* p_callback,
                         tBTM_BLE_SEC_ACT sec_act) {
  log::verbose("");
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_set_encryption(bd_addr, transport, p_callback, sec_act);
  } else {
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(bta_dm_set_encryption, bd_addr, transport,
                                     p_callback, sec_act));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmSirkSecCbRegister
 *
 * Description      This procedure registeres in requested a callback for
 *                  verification by CSIP potential set member.
 *
 * Parameters       p_cback     - callback to member verificator
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSirkSecCbRegister(tBTA_DM_SEC_CBACK* p_cback) {
  log::debug("");
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_ble_sirk_sec_cb_register(p_cback);
  } else {
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(bta_dm_ble_sirk_sec_cb_register, p_cback));
  }
}

/*******************************************************************************
 *
 * Function         BTA_DmSirkConfirmDeviceReply
 *
 * Description      This procedure confirms requested to validate set device.
 *
 * Parameters       bd_addr     - BD address of the peer
 *                  accept      - True if device is authorized by CSIP, false
 *                                otherwise.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSirkConfirmDeviceReply(const RawAddress& bd_addr, bool accept) {
  log::debug("");
  if (IS_FLAG_ENABLED(synchronous_bta_sec)) {
    bta_dm_ble_sirk_confirm_device_reply(bd_addr, accept);
  } else {
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(bta_dm_ble_sirk_confirm_device_reply, bd_addr, accept));
  }
}

