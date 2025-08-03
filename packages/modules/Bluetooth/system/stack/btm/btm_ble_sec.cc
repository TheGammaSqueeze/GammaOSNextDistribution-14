/*
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
 */

#define LOG_TAG "ble_sec"

#include "stack/btm/btm_ble_sec.h"

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstddef>
#include <cstdint>
#include <optional>

#include "btif/include/btif_storage.h"
#include "crypto_toolbox/crypto_toolbox.h"
#include "device/include/controller.h"
#include "device/include/interop.h"
#include "device/include/interop_config.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/properties.h"
#include "platform_ssl_mem.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/btm/btm_sec_int_types.h"
#include "stack/btm/security_device_record.h"
#include "stack/eatt/eatt.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_name.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_ble_addr.h"
#include "stack/include/btm_ble_privacy.h"
#include "stack/include/btm_ble_sec_api.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/btm_status.h"
#include "stack/include/gatt_api.h"
#include "stack/include/l2cap_security_interface.h"
#include "stack/include/smp_api.h"
#include "stack/include/smp_api_types.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

bool btm_ble_init_pseudo_addr(tBTM_SEC_DEV_REC* p_dev_rec,
                              const RawAddress& new_pseudo_addr);

namespace {
constexpr char kBtmLogTag[] = "SEC";
}

static constexpr char kPropertyCtkdDisableCsrkDistribution[] =
    "bluetooth.core.smp.le.ctkd.quirk_disable_csrk_distribution";

/******************************************************************************/
/* External Function to be called by other modules                            */
/******************************************************************************/
void BTM_SecAddBleDevice(const RawAddress& bd_addr, tBT_DEVICE_TYPE dev_type,
                         tBLE_ADDR_TYPE addr_type) {
  log::debug("dev_type=0x{:x}", dev_type);

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (!p_dev_rec) {
    p_dev_rec = btm_sec_allocate_dev_rec();

    p_dev_rec->bd_addr = bd_addr;
    p_dev_rec->hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_BR_EDR);
    p_dev_rec->ble_hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    /* update conn params, use default value for background connection params */
    p_dev_rec->conn_params.min_conn_int = BTM_BLE_CONN_PARAM_UNDEF;
    p_dev_rec->conn_params.max_conn_int = BTM_BLE_CONN_PARAM_UNDEF;
    p_dev_rec->conn_params.supervision_tout = BTM_BLE_CONN_PARAM_UNDEF;
    p_dev_rec->conn_params.peripheral_latency = BTM_BLE_CONN_PARAM_UNDEF;

    log::debug("Device added, handle=0x{:x}, p_dev_rec={}, bd_addr={}",
               p_dev_rec->ble_hci_handle, fmt::ptr(p_dev_rec),
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  }

  memset(p_dev_rec->sec_bd_name, 0, sizeof(tBTM_BD_NAME));

  p_dev_rec->device_type |= dev_type;
  if (is_ble_addr_type_known(addr_type)) {
    p_dev_rec->ble.SetAddressType(addr_type);
  } else {
    log::warn(
        "Please do not update device record from anonymous le advertisement");
  }

  /* sync up with the Inq Data base*/
  tBTM_INQ_INFO* p_info = BTM_InqDbRead(bd_addr);
  if (p_info) {
    p_info->results.ble_addr_type = p_dev_rec->ble.AddressType();
    p_dev_rec->device_type |= p_info->results.device_type;
    log::debug("InqDb device_type =0x{:x} addr_type=0x{:x}",
               p_dev_rec->device_type, p_info->results.ble_addr_type);
    p_info->results.device_type = p_dev_rec->device_type;
  }
}

/*******************************************************************************
 *
 * Function         BTM_GetRemoteDeviceName
 *
 * Description      This function is called to get the dev name of remote device
 *                  from NV
 *
 * Returns          TRUE if success; otherwise failed.
 *
 ******************************************************************************/
bool BTM_GetRemoteDeviceName(const RawAddress& bd_addr, BD_NAME bd_name) {
  log::verbose("bd_addr:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  bool ret = FALSE;
  bt_bdname_t bdname;
  bt_property_t prop_name;
  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_BDNAME,
                             sizeof(bt_bdname_t), &bdname);

  if (btif_storage_get_remote_device_property(&bd_addr, &prop_name) ==
      BT_STATUS_SUCCESS) {
    log::verbose("NV name={}", reinterpret_cast<const char*>(bdname.name));
    strncpy((char*)bd_name, (char*)bdname.name, BD_NAME_LEN + 1);
    ret = TRUE;
  }
  return ret;
}

/*******************************************************************************
 *
 * Function         BTM_SecAddBleKey
 *
 * Description      Add/modify LE device information.  This function will be
 *                  normally called during host startup to restore all required
 *                  information stored in the NVRAM.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  p_le_key         - LE key values.
 *                  key_type         - LE SMP key type.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
void BTM_SecAddBleKey(const RawAddress& bd_addr, tBTM_LE_KEY_VALUE* p_le_key,
                      tBTM_LE_KEY_TYPE key_type) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (!p_dev_rec || !p_le_key ||
      (key_type != BTM_LE_KEY_PENC && key_type != BTM_LE_KEY_PID &&
       key_type != BTM_LE_KEY_PCSRK && key_type != BTM_LE_KEY_LENC &&
       key_type != BTM_LE_KEY_LCSRK && key_type != BTM_LE_KEY_LID)) {
    log::warn("Wrong Type, or No Device record for bdaddr:{}, Type:0{}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr), key_type);
    return;
  }

  log::debug("Adding BLE key device:{} key_type:{}",
             ADDRESS_TO_LOGGABLE_CSTR(bd_addr), key_type);

  btm_sec_save_le_key(bd_addr, key_type, p_le_key, false);
  // Only set peer irk. Local irk is always the same.
  if (key_type == BTM_LE_KEY_PID) {
    btm_ble_resolving_list_load_dev(*p_dev_rec);
  }
}

/*******************************************************************************
 *
 * Function         BTM_BleLoadLocalKeys
 *
 * Description      Local local identity key, encryption root or sign counter.
 *
 * Parameters:      key_type: type of key, can be BTM_BLE_KEY_TYPE_ID,
 *                                                BTM_BLE_KEY_TYPE_ER
 *                                             or BTM_BLE_KEY_TYPE_COUNTER.
 *                  p_key: pointer to the key.
 *
 * Returns          non2.
 *
 ******************************************************************************/
void BTM_BleLoadLocalKeys(uint8_t key_type, tBTM_BLE_LOCAL_KEYS* p_key) {
  tBTM_SEC_DEVCB* p_devcb = &btm_sec_cb.devcb;
  log::verbose("type:{}", key_type);
  if (p_key != NULL) {
    switch (key_type) {
      case BTM_BLE_KEY_TYPE_ID:
        memcpy(&p_devcb->id_keys, &p_key->id_keys,
               sizeof(tBTM_BLE_LOCAL_ID_KEYS));
        break;

      case BTM_BLE_KEY_TYPE_ER:
        p_devcb->ble_encryption_key_value = p_key->er;
        break;

      default:
        log::error("unknown key type:{}", key_type);
        break;
    }
  }
}

/** Returns local device encryption root (ER) */
const Octet16& BTM_GetDeviceEncRoot() {
  return btm_sec_cb.devcb.ble_encryption_key_value;
}

/** Returns local device identity root (IR). */
const Octet16& BTM_GetDeviceIDRoot() { return btm_sec_cb.devcb.id_keys.irk; }

/** Return local device DHK. */
const Octet16& BTM_GetDeviceDHK() { return btm_sec_cb.devcb.id_keys.dhk; }

/*******************************************************************************
 *
 * Function         BTM_SecurityGrant
 *
 * Description      This function is called to grant security process.
 *
 * Parameters       bd_addr - peer device bd address.
 *                  res     - result of the operation BTM_SUCCESS if success.
 *                            Otherwise, BTM_REPEATED_ATTEMPTS if too many
 *                            attempts.
 *
 * Returns          None
 *
 ******************************************************************************/
void BTM_SecurityGrant(const RawAddress& bd_addr, uint8_t res) {
  const tSMP_STATUS res_smp =
      (res == BTM_SUCCESS) ? SMP_SUCCESS : SMP_REPEATED_ATTEMPTS;
  log::verbose("bd_addr:{}, res:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               smp_status_text(res_smp).c_str());
  BTM_LogHistory(kBtmLogTag, bd_addr, "Granted",
                 base::StringPrintf("passkey_status:%s",
                                    smp_status_text(res_smp).c_str()));

  SMP_SecurityGrant(bd_addr, res_smp);
}

/*******************************************************************************
 *
 * Function         BTM_BlePasskeyReply
 *
 * Description      This function is called after Security Manager submitted
 *                  passkey request to the application.
 *
 * Parameters:      bd_addr - Address of the device for which passkey was
 *                            requested
 *                  res     - result of the operation BTM_SUCCESS if success
 *                  key_len - length in bytes of the Passkey
 *                  p_passkey    - pointer to array with the passkey
 *
 ******************************************************************************/
void BTM_BlePasskeyReply(const RawAddress& bd_addr, uint8_t res,
                         uint32_t passkey) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  log::verbose("bd_addr:{}, res:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr), res);
  if (p_dev_rec == NULL) {
    log::error("Unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }

  const tSMP_STATUS res_smp =
      (res == BTM_SUCCESS) ? SMP_SUCCESS : SMP_PASSKEY_ENTRY_FAIL;
  BTM_LogHistory(kBtmLogTag, bd_addr, "Passkey reply",
                 base::StringPrintf("transport:%s authenticate_status:%s",
                                    bt_transport_text(BT_TRANSPORT_LE).c_str(),
                                    smp_status_text(res_smp).c_str()));

  p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LE_AUTHENTICATED;
  SMP_PasskeyReply(bd_addr, res_smp, passkey);
}

/*******************************************************************************
 *
 * Function         BTM_BleConfirmReply
 *
 * Description      This function is called after Security Manager submitted
 *                  numeric comparison request to the application.
 *
 * Parameters:      bd_addr      - Address of the device with which numeric
 *                                 comparison was requested
 *                  res          - comparison result BTM_SUCCESS if success
 *
 ******************************************************************************/
void BTM_BleConfirmReply(const RawAddress& bd_addr, uint8_t res) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  log::verbose("bd_addr:{}, res:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr), res);
  if (p_dev_rec == NULL) {
    log::error("Unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }
  const tSMP_STATUS res_smp =
      (res == BTM_SUCCESS) ? SMP_SUCCESS : SMP_PASSKEY_ENTRY_FAIL;

  BTM_LogHistory(kBtmLogTag, bd_addr, "Confirm reply",
                 base::StringPrintf(
                     "transport:%s numeric_comparison_authenticate_status:%s",
                     bt_transport_text(BT_TRANSPORT_LE).c_str(),
                     smp_status_text(res_smp).c_str()));

  p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LE_AUTHENTICATED;
  SMP_ConfirmReply(bd_addr, res_smp);
}

/*******************************************************************************
 *
 * Function         BTM_BleOobDataReply
 *
 * Description      This function is called to provide the OOB data for
 *                  SMP in response to BTM_LE_OOB_REQ_EVT
 *
 * Parameters:      bd_addr     - Address of the peer device
 *                  res         - result of the operation SMP_SUCCESS if success
 *                  p_data      - oob data, depending on transport and
 *                                capabilities.
 *                                Might be "Simple Pairing Randomizer", or
 *                                "Security Manager TK Value".
 *
 ******************************************************************************/
void BTM_BleOobDataReply(const RawAddress& bd_addr, uint8_t res, uint8_t len,
                         uint8_t* p_data) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::error("Unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }

  const tSMP_STATUS res_smp = (res == BTM_SUCCESS) ? SMP_SUCCESS : SMP_OOB_FAIL;
  BTM_LogHistory(kBtmLogTag, bd_addr, "Oob data reply",
                 base::StringPrintf("transport:%s authenticate_status:%s",
                                    bt_transport_text(BT_TRANSPORT_LE).c_str(),
                                    smp_status_text(res_smp).c_str()));

  p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LE_AUTHENTICATED;
  SMP_OobDataReply(bd_addr, res_smp, len, p_data);
}

/*******************************************************************************
 *
 * Function         BTM_BleSecureConnectionOobDataReply
 *
 * Description      This function is called to provide the OOB data for
 *                  SMP in response to BTM_LE_OOB_REQ_EVT when secure connection
 *                  data is available
 *
 * Parameters:      bd_addr     - Address of the peer device
 *                  p_c         - pointer to Confirmation.
 *                  p_r         - pointer to Randomizer
 *
 ******************************************************************************/
void BTM_BleSecureConnectionOobDataReply(const RawAddress& bd_addr,
                                         uint8_t* p_c, uint8_t* p_r) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::error("Unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }

  BTM_LogHistory(
      kBtmLogTag, bd_addr, "Oob data reply",
      base::StringPrintf("transport:%s",
                         bt_transport_text(BT_TRANSPORT_LE).c_str()));

  p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LE_AUTHENTICATED;

  tSMP_SC_OOB_DATA oob;
  memset(&oob, 0, sizeof(tSMP_SC_OOB_DATA));

  oob.peer_oob_data.present = true;
  memcpy(&oob.peer_oob_data.randomizer, p_r, OCTET16_LEN);
  memcpy(&oob.peer_oob_data.commitment, p_c, OCTET16_LEN);
  oob.peer_oob_data.addr_rcvd_from.type = p_dev_rec->ble.AddressType();
  oob.peer_oob_data.addr_rcvd_from.bda = bd_addr;

  SMP_SecureConnectionOobDataReply((uint8_t*)&oob);
}

/********************************************************
 *
 * Function         BTM_BleSetPrefConnParams
 *
 * Description      Set a peripheral's preferred connection parameters
 *
 * Parameters:      bd_addr          - BD address of the peripheral
 *                  scan_interval: scan interval
 *                  scan_window: scan window
 *                  min_conn_int     - minimum preferred connection interval
 *                  max_conn_int     - maximum preferred connection interval
 *                  peripheral_latency    - preferred peripheral latency
 *                  supervision_tout - preferred supervision timeout
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleSetPrefConnParams(const RawAddress& bd_addr, uint16_t min_conn_int,
                              uint16_t max_conn_int,
                              uint16_t peripheral_latency,
                              uint16_t supervision_tout) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);

  log::verbose("min:{},max:{},latency:{},tout:{}", min_conn_int, max_conn_int,
               peripheral_latency, supervision_tout);

  if (BTM_BLE_ISVALID_PARAM(min_conn_int, BTM_BLE_CONN_INT_MIN,
                            BTM_BLE_CONN_INT_MAX) &&
      BTM_BLE_ISVALID_PARAM(max_conn_int, BTM_BLE_CONN_INT_MIN,
                            BTM_BLE_CONN_INT_MAX) &&
      BTM_BLE_ISVALID_PARAM(supervision_tout, BTM_BLE_CONN_SUP_TOUT_MIN,
                            BTM_BLE_CONN_SUP_TOUT_MAX) &&
      (peripheral_latency <= BTM_BLE_CONN_LATENCY_MAX ||
       peripheral_latency == BTM_BLE_CONN_PARAM_UNDEF)) {
    if (p_dev_rec) {
      /* expect conn int and stout and peripheral latency to be updated all
       * together
       */
      if (min_conn_int != BTM_BLE_CONN_PARAM_UNDEF ||
          max_conn_int != BTM_BLE_CONN_PARAM_UNDEF) {
        if (min_conn_int != BTM_BLE_CONN_PARAM_UNDEF)
          p_dev_rec->conn_params.min_conn_int = min_conn_int;
        else
          p_dev_rec->conn_params.min_conn_int = max_conn_int;

        if (max_conn_int != BTM_BLE_CONN_PARAM_UNDEF)
          p_dev_rec->conn_params.max_conn_int = max_conn_int;
        else
          p_dev_rec->conn_params.max_conn_int = min_conn_int;

        if (peripheral_latency != BTM_BLE_CONN_PARAM_UNDEF)
          p_dev_rec->conn_params.peripheral_latency = peripheral_latency;
        else
          p_dev_rec->conn_params.peripheral_latency =
              BTM_BLE_CONN_PERIPHERAL_LATENCY_DEF;

        if (supervision_tout != BTM_BLE_CONN_PARAM_UNDEF)
          p_dev_rec->conn_params.supervision_tout = supervision_tout;
        else
          p_dev_rec->conn_params.supervision_tout = BTM_BLE_CONN_TIMEOUT_DEF;
      }

    } else {
      log::error("Unknown Device, setting rejected");
    }
  } else {
    log::error("Illegal Connection Parameters");
  }
}

/*******************************************************************************
 *
 * Function         BTM_ReadDevInfo
 *
 * Description      This function is called to read the device/address type
 *                  of BD address.
 *
 * Parameter        remote_bda: remote device address
 *                  p_dev_type: output parameter to read the device type.
 *                  p_addr_type: output parameter to read the address type.
 *
 ******************************************************************************/
void BTM_ReadDevInfo(const RawAddress& remote_bda, tBT_DEVICE_TYPE* p_dev_type,
                     tBLE_ADDR_TYPE* p_addr_type) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(remote_bda);
  tBTM_INQ_INFO* p_inq_info = BTM_InqDbRead(remote_bda);

  *p_addr_type = BLE_ADDR_PUBLIC;

  if (!p_dev_rec) {
    *p_dev_type = BT_DEVICE_TYPE_BREDR;
    /* Check with the BT manager if details about remote device are known */
    if (p_inq_info != NULL) {
      *p_dev_type = p_inq_info->results.device_type;
      *p_addr_type = p_inq_info->results.ble_addr_type;
    } else {
      /* unknown device, assume BR/EDR */
      log::verbose("unknown device, BR/EDR assumed");
    }
  } else /* there is a security device record existing */
  {
    /* new inquiry result, merge device type in security device record */
    if (p_inq_info) {
      p_dev_rec->device_type |= p_inq_info->results.device_type;
      if (is_ble_addr_type_known(p_inq_info->results.ble_addr_type))
        p_dev_rec->ble.SetAddressType(p_inq_info->results.ble_addr_type);
      else
        log::warn(
            "Please do not update device record from anonymous le "
            "advertisement");
    }

    if (p_dev_rec->bd_addr == remote_bda &&
        p_dev_rec->ble.pseudo_addr == remote_bda) {
      *p_dev_type = p_dev_rec->device_type;
      *p_addr_type = p_dev_rec->ble.AddressType();
    } else if (p_dev_rec->ble.pseudo_addr == remote_bda) {
      *p_dev_type = BT_DEVICE_TYPE_BLE;
      *p_addr_type = p_dev_rec->ble.AddressType();
    } else /* matching static address only */ {
      if (p_dev_rec->device_type != BT_DEVICE_TYPE_UNKNOWN) {
        *p_dev_type = p_dev_rec->device_type;
      } else {
        log::warn("device_type not set; assuming BR/EDR");
        *p_dev_type = BT_DEVICE_TYPE_BREDR;
      }
      *p_addr_type = BLE_ADDR_PUBLIC;
    }
  }
  log::debug("Determined device_type:{} addr_type:{}",
             DeviceTypeText(*p_dev_type).c_str(),
             AddressTypeText(*p_addr_type).c_str());
}

/*******************************************************************************
 *
 * Function         BTM_ReadConnectedTransportAddress
 *
 * Description      This function is called to read the paired device/address
 *                  type of other device paired corresponding to the BD_address
 *
 * Parameter        remote_bda: remote device address, carry out the transport
 *                              address
 *                  transport: active transport
 *
 * Return           true if an active link is identified; false otherwise
 *
 ******************************************************************************/
bool BTM_ReadConnectedTransportAddress(RawAddress* remote_bda,
                                       tBT_TRANSPORT transport) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(*remote_bda);

  /* if no device can be located, return */
  if (p_dev_rec == NULL) return false;

  if (transport == BT_TRANSPORT_BR_EDR) {
    if (BTM_IsAclConnectionUp(p_dev_rec->bd_addr, transport)) {
      *remote_bda = p_dev_rec->bd_addr;
      return true;
    } else if (p_dev_rec->device_type & BT_DEVICE_TYPE_BREDR) {
      *remote_bda = p_dev_rec->bd_addr;
    } else
      *remote_bda = RawAddress::kEmpty;
    return false;
  }

  if (transport == BT_TRANSPORT_LE) {
    *remote_bda = p_dev_rec->ble.pseudo_addr;
    if (BTM_IsAclConnectionUp(p_dev_rec->ble.pseudo_addr, transport))
      return true;
    else
      return false;
  }

  return false;
}

tBTM_STATUS BTM_SetBleDataLength(const RawAddress& bd_addr,
                                 uint16_t tx_pdu_length) {
  if (!controller_get_interface()->SupportsBleDataPacketLengthExtension()) {
    log::info("Local controller does not support le packet extension");
    return BTM_ILLEGAL_VALUE;
  }

  log::info("bd_addr:{}, tx_pdu_length:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
            tx_pdu_length);

  auto p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::error("Device {} not found", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return BTM_UNKNOWN_ADDR;
  }

  if (tx_pdu_length > BTM_BLE_DATA_SIZE_MAX)
    tx_pdu_length = BTM_BLE_DATA_SIZE_MAX;
  else if (tx_pdu_length < BTM_BLE_DATA_SIZE_MIN)
    tx_pdu_length = BTM_BLE_DATA_SIZE_MIN;

  if (p_dev_rec->get_suggested_tx_octets() >= tx_pdu_length) {
    log::info("Suggested TX octect already set to controller {} >= {}",
              p_dev_rec->get_suggested_tx_octets(), tx_pdu_length);
    return BTM_SUCCESS;
  }

  uint16_t tx_time = BTM_BLE_DATA_TX_TIME_MAX_LEGACY;

  if (controller_get_interface()->get_bt_version()->hci_version >=
      HCI_PROTO_VERSION_5_0)
    tx_time = BTM_BLE_DATA_TX_TIME_MAX;

  if (!BTM_IsAclConnectionUp(bd_addr, BT_TRANSPORT_LE)) {
    log::info(
        "Unable to set data length because no le acl link connected to device");
    return BTM_WRONG_MODE;
  }

  uint16_t hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

  if (!acl_peer_supports_ble_packet_extension(hci_handle)) {
    log::info("Remote device unable to support le packet extension");
    return BTM_ILLEGAL_VALUE;
  }

  tx_pdu_length = std::min<uint16_t>(
      tx_pdu_length,
      controller_get_interface()->get_ble_maximum_tx_data_length());
  tx_time = std::min<uint16_t>(
      tx_time, controller_get_interface()->get_ble_maximum_tx_time());

  btsnd_hcic_ble_set_data_length(hci_handle, tx_pdu_length, tx_time);
  p_dev_rec->set_suggested_tx_octect(tx_pdu_length);

  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btm_ble_determine_security_act
 *
 * Description      This function checks the security of current LE link
 *                  and returns the appropriate action that needs to be
 *                  taken to achieve the required security.
 *
 * Parameter        is_originator - True if outgoing connection
 *                  bdaddr: remote device address
 *                  security_required: Security required for the service.
 *
 * Returns          The appropriate security action required.
 *
 ******************************************************************************/
static tBTM_SEC_ACTION btm_ble_determine_security_act(
    bool is_originator, const RawAddress& bdaddr, uint16_t security_required) {
  tBTM_LE_AUTH_REQ auth_req = 0x00;

  if (is_originator) {
    if ((security_required & BTM_SEC_OUT_FLAGS) == 0 &&
        (security_required & BTM_SEC_OUT_MITM) == 0) {
      log::info("No security required for outgoing connection");
      return BTM_SEC_OK;
    }

    if (security_required & BTM_SEC_OUT_MITM) auth_req |= BTM_LE_AUTH_REQ_MITM;
  } else {
    if ((security_required & BTM_SEC_IN_FLAGS) == 0 &&
        (security_required & BTM_SEC_IN_MITM) == 0) {
      log::verbose("No security required for incoming connection");
      return BTM_SEC_OK;
    }

    if (security_required & BTM_SEC_IN_MITM) auth_req |= BTM_LE_AUTH_REQ_MITM;
  }

  tBTM_BLE_SEC_REQ_ACT ble_sec_act = {BTM_BLE_SEC_REQ_ACT_NONE};
  btm_ble_link_sec_check(bdaddr, auth_req, &ble_sec_act);

  log::verbose("ble_sec_act {}", ble_sec_act);

  if (ble_sec_act == BTM_BLE_SEC_REQ_ACT_DISCARD) return BTM_SEC_ENC_PENDING;

  if (ble_sec_act == BTM_BLE_SEC_REQ_ACT_NONE) return BTM_SEC_OK;

  bool is_link_encrypted = BTM_IsEncrypted(bdaddr, BT_TRANSPORT_LE);
  bool is_key_mitm = BTM_IsLinkKeyAuthed(bdaddr, BT_TRANSPORT_LE);

  if (auth_req & BTM_LE_AUTH_REQ_MITM) {
    if (!is_key_mitm) {
      return BTM_SEC_ENCRYPT_MITM;
    } else {
      if (is_link_encrypted)
        return BTM_SEC_OK;
      else
        return BTM_SEC_ENCRYPT;
    }
  } else {
    if (is_link_encrypted)
      return BTM_SEC_OK;
    else
      return BTM_SEC_ENCRYPT_NO_MITM;
  }

  return BTM_SEC_OK;
}

/*******************************************************************************
 *
 * Function         btm_ble_start_sec_check
 *
 * Description      This function is to check and set the security required for
 *                  LE link for LE COC.
 *
 * Parameter        bdaddr: remote device address.
 *                  psm : PSM of the LE COC service.
 *                  is_originator: true if outgoing connection.
 *                  p_callback : Pointer to the callback function.
 *                  p_ref_data : Pointer to be returned along with the callback.
 *
 * Returns          Returns  - tBTM_STATUS
 *
 ******************************************************************************/
tBTM_STATUS btm_ble_start_sec_check(const RawAddress& bd_addr, uint16_t psm,
                                    bool is_originator,
                                    tBTM_SEC_CALLBACK* p_callback,
                                    void* p_ref_data) {
  /* Find the service record for the PSM */
  tBTM_SEC_SERV_REC* p_serv_rec =
      btm_sec_cb.find_first_serv_rec(is_originator, psm);

  /* If there is no application registered with this PSM do not allow connection
   */
  if (!p_serv_rec) {
    log::warn("PSM: {} no application registered", psm);
    (*p_callback)(&bd_addr, BT_TRANSPORT_LE, p_ref_data, BTM_MODE_UNSUPPORTED);
    return BTM_ILLEGAL_VALUE;
  }

  bool is_encrypted = BTM_IsEncrypted(bd_addr, BT_TRANSPORT_LE);
  bool is_link_key_authed = BTM_IsLinkKeyAuthed(bd_addr, BT_TRANSPORT_LE);
  bool is_authenticated = BTM_IsAuthenticated(bd_addr, BT_TRANSPORT_LE);

  if (!is_originator) {
    if ((p_serv_rec->security_flags & BTM_SEC_IN_ENCRYPT) && !is_encrypted) {
      log::error("BTM_NOT_ENCRYPTED. service security_flags=0x{:x}",
                 p_serv_rec->security_flags);
      return BTM_NOT_ENCRYPTED;
    } else if ((p_serv_rec->security_flags & BTM_SEC_IN_AUTHENTICATE) &&
               !(is_link_key_authed || is_authenticated)) {
      log::error("BTM_NOT_AUTHENTICATED. service security_flags=0x{:x}",
                 p_serv_rec->security_flags);
      return BTM_NOT_AUTHENTICATED;
    }
    /* TODO: When security is required, then must check that the key size of our
       service is equal or smaller than the incoming connection key size. */
  }

  tBTM_SEC_ACTION sec_act = btm_ble_determine_security_act(
      is_originator, bd_addr, p_serv_rec->security_flags);

  tBTM_BLE_SEC_ACT ble_sec_act = BTM_BLE_SEC_NONE;

  switch (sec_act) {
    case BTM_SEC_OK:
      log::debug("Security met");
      p_callback(&bd_addr, BT_TRANSPORT_LE, p_ref_data, BTM_SUCCESS);
      break;

    case BTM_SEC_ENCRYPT:
      log::debug("Encryption needs to be done");
      ble_sec_act = BTM_BLE_SEC_ENCRYPT;
      break;

    case BTM_SEC_ENCRYPT_MITM:
      log::debug("Pairing with MITM needs to be done");
      ble_sec_act = BTM_BLE_SEC_ENCRYPT_MITM;
      break;

    case BTM_SEC_ENCRYPT_NO_MITM:
      log::debug("Pairing with No MITM needs to be done");
      ble_sec_act = BTM_BLE_SEC_ENCRYPT_NO_MITM;
      break;

    case BTM_SEC_ENC_PENDING:
      log::debug("Ecryption pending");
      break;
  }

  if (ble_sec_act == BTM_BLE_SEC_NONE && sec_act != BTM_SEC_ENC_PENDING) {
    return BTM_SUCCESS;
  }

  l2cble_update_sec_act(bd_addr, sec_act);

  BTM_SetEncryption(bd_addr, BT_TRANSPORT_LE, p_callback, p_ref_data,
                    ble_sec_act);

  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         increment_sign_counter
 *
 * Description      This method is to increment the (local or peer) sign counter
 * Returns         None
 *
 ******************************************************************************/
void tBTM_SEC_REC::increment_sign_counter(bool local) {
  if (local) {
    ble_keys.local_counter++;
  } else {
    ble_keys.counter++;
  }

  log::verbose("local={} local sign counter={} peer sign counter={}", local,
               ble_keys.local_counter, ble_keys.counter);
}

/*******************************************************************************
 *
 * Function         btm_ble_get_enc_key_type
 *
 * Description      This function is to get the BLE key type that has been
 *                  exchanged between the local device and the peer device.
 *
 * Returns          p_key_type: output parameter to carry the key type value.
 *
 ******************************************************************************/
bool btm_ble_get_enc_key_type(const RawAddress& bd_addr, uint8_t* p_key_types) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  log::verbose("bd_addr:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec != NULL) {
    *p_key_types = p_dev_rec->sec_rec.ble_keys.key_type;
    return true;
  }
  return false;
}

/*******************************************************************************
 *
 * Function         btm_get_local_div
 *
 * Description      This function is called to read the local DIV
 *
 * Returns          TRUE - if a valid DIV is availavle
 ******************************************************************************/
bool btm_get_local_div(const RawAddress& bd_addr, uint16_t* p_div) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  bool status = false;

  *p_div = 0;
  p_dev_rec = btm_find_dev(bd_addr);

  if (p_dev_rec && p_dev_rec->sec_rec.ble_keys.div) {
    status = true;
    *p_div = p_dev_rec->sec_rec.ble_keys.div;
  }
  log::verbose("status={} (1-OK) DIV=0x{:x}", status, *p_div);
  return status;
}

/*******************************************************************************
 *
 * Function         btm_sec_save_le_key
 *
 * Description      This function is called by the SMP to update
 *                  an  BLE key.  SMP is internal, whereas all the keys shall
 *                  be sent to the application.  The function is also called
 *                  when application passes ble key stored in NVRAM to the
 *                  btm_sec.
 *                  pass_to_application parameter is false in this case.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_save_le_key(const RawAddress& bd_addr, tBTM_LE_KEY_TYPE key_type,
                         tBTM_LE_KEY_VALUE* p_keys, bool pass_to_application) {
  tBTM_SEC_DEV_REC* p_rec;
  tBTM_LE_EVT_DATA cb_data;

  log::verbose("key_type=0x{:x} pass_to_application={}", key_type,
               pass_to_application);
  /* Store the updated key in the device database */

  if ((p_rec = btm_find_dev(bd_addr)) != NULL &&
      (p_keys || key_type == BTM_LE_KEY_LID)) {
    btm_ble_init_pseudo_addr(p_rec, bd_addr);

    switch (key_type) {
      case BTM_LE_KEY_PENC:
        p_rec->sec_rec.ble_keys.pltk = p_keys->penc_key.ltk;
        memcpy(p_rec->sec_rec.ble_keys.rand, p_keys->penc_key.rand,
               BT_OCTET8_LEN);
        p_rec->sec_rec.ble_keys.sec_level = p_keys->penc_key.sec_level;
        p_rec->sec_rec.ble_keys.ediv = p_keys->penc_key.ediv;
        p_rec->sec_rec.ble_keys.key_size = p_keys->penc_key.key_size;
        p_rec->sec_rec.ble_keys.key_type |= BTM_LE_KEY_PENC;
        p_rec->sec_rec.sec_flags |= BTM_SEC_LE_LINK_KEY_KNOWN;
        if (p_keys->penc_key.sec_level == SMP_SEC_AUTHENTICATED)
          p_rec->sec_rec.sec_flags |= BTM_SEC_LE_LINK_KEY_AUTHED;
        else
          p_rec->sec_rec.sec_flags &= ~BTM_SEC_LE_LINK_KEY_AUTHED;
        log::verbose(
            "BTM_LE_KEY_PENC key_type=0x{:x} sec_flags=0x{:x} sec_leve=0x{:x}",
            p_rec->sec_rec.ble_keys.key_type, p_rec->sec_rec.sec_flags,
            p_rec->sec_rec.ble_keys.sec_level);
        break;

      case BTM_LE_KEY_PID:
        p_rec->sec_rec.ble_keys.irk = p_keys->pid_key.irk;
        p_rec->ble.identity_address_with_type.bda =
            p_keys->pid_key.identity_addr;
        p_rec->ble.identity_address_with_type.type =
            p_keys->pid_key.identity_addr_type;
        p_rec->sec_rec.ble_keys.key_type |= BTM_LE_KEY_PID;
        log::verbose(
            "BTM_LE_KEY_PID key_type=0x{:x} save peer IRK, change bd_addr={} "
            "to id_addr={} id_addr_type=0x{:x}",
            p_rec->sec_rec.ble_keys.key_type,
            ADDRESS_TO_LOGGABLE_CSTR(p_rec->bd_addr),
            ADDRESS_TO_LOGGABLE_CSTR(p_keys->pid_key.identity_addr),
            p_keys->pid_key.identity_addr_type);
        /* update device record address as identity address */
        p_rec->bd_addr = p_keys->pid_key.identity_addr;
        /* combine DUMO device security record if needed */
        btm_consolidate_dev(p_rec);
        break;

      case BTM_LE_KEY_PCSRK:
        p_rec->sec_rec.ble_keys.pcsrk = p_keys->pcsrk_key.csrk;
        p_rec->sec_rec.ble_keys.srk_sec_level = p_keys->pcsrk_key.sec_level;
        p_rec->sec_rec.ble_keys.counter = p_keys->pcsrk_key.counter;
        p_rec->sec_rec.ble_keys.key_type |= BTM_LE_KEY_PCSRK;
        p_rec->sec_rec.sec_flags |= BTM_SEC_LE_LINK_KEY_KNOWN;
        if (p_keys->pcsrk_key.sec_level == SMP_SEC_AUTHENTICATED)
          p_rec->sec_rec.sec_flags |= BTM_SEC_LE_LINK_KEY_AUTHED;
        else
          p_rec->sec_rec.sec_flags &= ~BTM_SEC_LE_LINK_KEY_AUTHED;

        log::verbose(
            "BTM_LE_KEY_PCSRK key_type=0x{:x} sec_flags=0x{:x} "
            "sec_level=0x{:x} peer_counter={}",
            p_rec->sec_rec.ble_keys.key_type, p_rec->sec_rec.sec_flags,
            p_rec->sec_rec.ble_keys.srk_sec_level,
            p_rec->sec_rec.ble_keys.counter);
        break;

      case BTM_LE_KEY_LENC:
        p_rec->sec_rec.ble_keys.lltk = p_keys->lenc_key.ltk;
        p_rec->sec_rec.ble_keys.div = p_keys->lenc_key.div; /* update DIV */
        p_rec->sec_rec.ble_keys.sec_level = p_keys->lenc_key.sec_level;
        p_rec->sec_rec.ble_keys.key_size = p_keys->lenc_key.key_size;
        p_rec->sec_rec.ble_keys.key_type |= BTM_LE_KEY_LENC;

        log::verbose(
            "BTM_LE_KEY_LENC key_type=0x{:x} DIV=0x{:x} key_size=0x{:x} "
            "sec_level=0x{:x}",
            p_rec->sec_rec.ble_keys.key_type, p_rec->sec_rec.ble_keys.div,
            p_rec->sec_rec.ble_keys.key_size,
            p_rec->sec_rec.ble_keys.sec_level);
        break;

      case BTM_LE_KEY_LCSRK: /* local CSRK has been delivered */
        p_rec->sec_rec.ble_keys.lcsrk = p_keys->lcsrk_key.csrk;
        p_rec->sec_rec.ble_keys.div = p_keys->lcsrk_key.div; /* update DIV */
        p_rec->sec_rec.ble_keys.local_csrk_sec_level =
            p_keys->lcsrk_key.sec_level;
        p_rec->sec_rec.ble_keys.local_counter = p_keys->lcsrk_key.counter;
        p_rec->sec_rec.ble_keys.key_type |= BTM_LE_KEY_LCSRK;
        log::verbose(
            "BTM_LE_KEY_LCSRK key_type=0x{:x} DIV=0x{:x} scrk_sec_level=0x{:x} "
            "local_counter={}",
            p_rec->sec_rec.ble_keys.key_type, p_rec->sec_rec.ble_keys.div,
            p_rec->sec_rec.ble_keys.local_csrk_sec_level,
            p_rec->sec_rec.ble_keys.local_counter);
        break;

      case BTM_LE_KEY_LID:
        p_rec->sec_rec.ble_keys.key_type |= BTM_LE_KEY_LID;
        break;
      default:
        log::warn("btm_sec_save_le_key (Bad key_type 0x{:02x})", key_type);
        return;
    }

    log::verbose("BLE key type 0x{:x}, updated for BDA:{}", key_type,
                 ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

    /* Notify the application that one of the BLE keys has been updated
       If link key is in progress, it will get sent later.*/
    if (pass_to_application && btm_sec_cb.api.p_le_callback) {
      cb_data.key.p_key_value = p_keys;
      cb_data.key.key_type = key_type;

      (*btm_sec_cb.api.p_le_callback)(BTM_LE_KEY_EVT, bd_addr, &cb_data);
    }
    return;
  }

  log::warn("BLE key type 0x{:x}, called for Unknown BDA or type:{}", key_type,
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  if (p_rec) {
    log::verbose("sec_flags=0x{:x}", p_rec->sec_rec.sec_flags);
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_update_sec_key_size
 *
 * Description      update the current lin kencryption key size
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_update_sec_key_size(const RawAddress& bd_addr,
                                 uint8_t enc_key_size) {
  tBTM_SEC_DEV_REC* p_rec;

  log::verbose("bd_addr:{}, enc_key_size={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               enc_key_size);

  p_rec = btm_find_dev(bd_addr);
  if (p_rec != NULL) {
    p_rec->sec_rec.enc_key_size = enc_key_size;
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_read_sec_key_size
 *
 * Description      update the current lin kencryption key size
 *
 * Returns          void
 *
 ******************************************************************************/
uint8_t btm_ble_read_sec_key_size(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_rec;

  p_rec = btm_find_dev(bd_addr);
  if (p_rec != NULL) {
    return p_rec->sec_rec.enc_key_size;
  } else
    return 0;
}

/*******************************************************************************
 *
 * Function         btm_ble_link_sec_check
 *
 * Description      Check BLE link security level match.
 *
 * Returns          true: check is OK and the *p_sec_req_act contain the action
 *
 ******************************************************************************/
void btm_ble_link_sec_check(const RawAddress& bd_addr,
                            tBTM_LE_AUTH_REQ auth_req,
                            tBTM_BLE_SEC_REQ_ACT* p_sec_req_act) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  uint8_t req_sec_level = SMP_SEC_NONE, cur_sec_level = SMP_SEC_NONE;

  log::verbose("bd_addr:{}, auth_req=0x{:x}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               auth_req);

  if (p_dev_rec == NULL) {
    log::error("received for unknown device");
    return;
  }

  if (p_dev_rec->sec_rec.is_security_state_encrypting() ||
      p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING) {
    /* race condition: discard the security request while central is encrypting
     * the link */
    *p_sec_req_act = BTM_BLE_SEC_REQ_ACT_DISCARD;
  } else {
    req_sec_level = SMP_SEC_UNAUTHENTICATE;
    if (auth_req & BTM_LE_AUTH_REQ_MITM) {
      req_sec_level = SMP_SEC_AUTHENTICATED;
    }

    log::verbose("dev_rec sec_flags=0x{:x}", p_dev_rec->sec_rec.sec_flags);

    /* currently encrpted  */
    if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_ENCRYPTED) {
      if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_AUTHENTICATED)
        cur_sec_level = SMP_SEC_AUTHENTICATED;
      else
        cur_sec_level = SMP_SEC_UNAUTHENTICATE;
    } else /* unencrypted link */
    {
      /* if bonded, get the key security level */
      if (p_dev_rec->sec_rec.ble_keys.key_type & BTM_LE_KEY_PENC)
        cur_sec_level = p_dev_rec->sec_rec.ble_keys.sec_level;
      else
        cur_sec_level = SMP_SEC_NONE;
    }

    if (cur_sec_level >= req_sec_level) {
      /* To avoid re-encryption on an encrypted link for an equal condition
       * encryption */
      *p_sec_req_act = BTM_BLE_SEC_REQ_ACT_ENCRYPT;
    } else {
      /* start the pariring process to upgrade the keys*/
      *p_sec_req_act = BTM_BLE_SEC_REQ_ACT_PAIR;
    }
  }

  log::verbose("cur_sec_level={} req_sec_level={} sec_req_act={}",
               cur_sec_level, req_sec_level, *p_sec_req_act);
}

/*******************************************************************************
 *
 * Function         btm_ble_set_encryption
 *
 * Description      This function is called to ensure that LE connection is
 *                  encrypted.  Should be called only on an open connection.
 *                  Typically only needed for connections that first want to
 *                  bring up unencrypted links, then later encrypt them.
 *
 * Returns          void
 *                  the local device ER is copied into er
 *
 ******************************************************************************/
tBTM_STATUS btm_ble_set_encryption(const RawAddress& bd_addr,
                                   tBTM_BLE_SEC_ACT sec_act,
                                   uint8_t link_role) {
  tBTM_STATUS cmd = BTM_NO_RESOURCES;
  tBTM_SEC_DEV_REC* p_rec = btm_find_dev(bd_addr);
  tBTM_BLE_SEC_REQ_ACT sec_req_act;
  tBTM_LE_AUTH_REQ auth_req;

  if (p_rec == NULL) {
    log::warn("NULL device record!! sec_act=0x{:x}", sec_act);
    return (BTM_WRONG_MODE);
  }

  log::verbose("sec_act=0x{:x} role_central={}", sec_act, p_rec->role_central);

  if (sec_act == BTM_BLE_SEC_ENCRYPT_MITM) {
    p_rec->sec_rec.security_required |= BTM_SEC_IN_MITM;
  }

  switch (sec_act) {
    if (p_rec->sec_rec.is_le_device_encrypted()) {
      return BTM_SUCCESS;
    }

    case BTM_BLE_SEC_ENCRYPT:
      if (link_role == HCI_ROLE_CENTRAL) {
        /* start link layer encryption using the security info stored */
        cmd = btm_ble_start_encrypt(bd_addr, false, NULL);
        break;
      }
      /* if salve role then fall through to call SMP_Pair below which will send
         a sec_request to request the central to encrypt the link */
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    case BTM_BLE_SEC_ENCRYPT_NO_MITM:
    case BTM_BLE_SEC_ENCRYPT_MITM:
      auth_req = (sec_act == BTM_BLE_SEC_ENCRYPT_NO_MITM)
                     ? SMP_AUTH_BOND
                     : (SMP_AUTH_BOND | SMP_AUTH_YN_BIT);
      btm_ble_link_sec_check(bd_addr, auth_req, &sec_req_act);
      if (sec_req_act == BTM_BLE_SEC_REQ_ACT_NONE ||
          sec_req_act == BTM_BLE_SEC_REQ_ACT_DISCARD) {
        log::verbose("no action needed. Ignore");
        cmd = BTM_SUCCESS;
        break;
      }
      if (link_role == HCI_ROLE_CENTRAL) {
        if (sec_req_act == BTM_BLE_SEC_REQ_ACT_ENCRYPT) {
          cmd = btm_ble_start_encrypt(bd_addr, false, NULL);
          break;
        }
      }

      if (SMP_Pair(bd_addr) == SMP_STARTED) {
        cmd = BTM_CMD_STARTED;
        p_rec->sec_rec.sec_state = BTM_SEC_STATE_AUTHENTICATING;
      }
      break;

    default:
      cmd = BTM_WRONG_MODE;
      break;
  }
  return cmd;
}

/*******************************************************************************
 *
 * Function         btm_ble_ltk_request
 *
 * Description      This function is called when encryption request is received
 *                  on a peripheral device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_ltk_request(uint16_t handle, BT_OCTET8 rand, uint16_t ediv) {
  tBTM_SEC_CB* p_cb = &btm_sec_cb;
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);

  log::verbose("handle:0x{:x}", handle);

  p_cb->ediv = ediv;

  memcpy(p_cb->enc_rand, rand, BT_OCTET8_LEN);

  if (p_dev_rec != NULL) {
    if (!smp_proc_ltk_request(p_dev_rec->bd_addr)) {
      btm_ble_ltk_request_reply(p_dev_rec->bd_addr, false, Octet16{0});
    }
  }
}

/** This function is called to start LE encryption.
 * Returns BTM_SUCCESS if encryption was started successfully
 */
tBTM_STATUS btm_ble_start_encrypt(const RawAddress& bda, bool use_stk,
                                  Octet16* p_stk) {
  tBTM_SEC_CB* p_cb = &btm_sec_cb;
  tBTM_SEC_DEV_REC* p_rec = btm_find_dev(bda);
  BT_OCTET8 dummy_rand = {0};

  log::verbose("bd_addr:{}, use_stk:{}", ADDRESS_TO_LOGGABLE_CSTR(bda),
               use_stk);

  if (!p_rec) {
    log::error("Link is not active, can not encrypt!");
    return BTM_WRONG_MODE;
  }

  if (p_rec->sec_rec.is_security_state_encrypting()) {
    log::warn("Link Encryption is active, Busy!");
    return BTM_BUSY;
  }

  p_cb->enc_handle = p_rec->ble_hci_handle;

  if (use_stk) {
    btsnd_hcic_ble_start_enc(p_rec->ble_hci_handle, dummy_rand, 0, *p_stk);
  } else if (p_rec->sec_rec.ble_keys.key_type & BTM_LE_KEY_PENC) {
    btsnd_hcic_ble_start_enc(
        p_rec->ble_hci_handle, p_rec->sec_rec.ble_keys.rand,
        p_rec->sec_rec.ble_keys.ediv, p_rec->sec_rec.ble_keys.pltk);
  } else {
    log::error("No key available to encrypt the link");
    return BTM_ERR_KEY_MISSING;
  }

  if (p_rec->sec_rec.sec_state == BTM_SEC_STATE_IDLE)
    p_rec->sec_rec.sec_state = BTM_SEC_STATE_LE_ENCRYPTING;

  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         btm_ble_notify_enc_cmpl
 *
 * Description      This function is called to connect EATT and notify GATT to
 *                  send data if any request is pending. This either happens on
 *                  encryption complete event, or if bond is pending, after SMP
 *                  notifies that bonding is complete.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_notify_enc_cmpl(const RawAddress& bd_addr,
                                    bool encr_enable) {
  if (encr_enable) {
    uint8_t remote_lmp_version = 0;
    if (!BTM_ReadRemoteVersion(bd_addr, &remote_lmp_version, nullptr,
                               nullptr) ||
        remote_lmp_version == 0) {
      log::warn("BLE Unable to determine remote version");
    }

    if (remote_lmp_version == 0 ||
        remote_lmp_version >= HCI_PROTO_VERSION_5_0) {
      /* Link is encrypted, start EATT if remote LMP version is unknown, or 5.2
       * or greater */
      bluetooth::eatt::EattExtension::GetInstance()->Connect(bd_addr);
    }
  }

  /* to notify GATT to send data if any request is pending */
  gatt_notify_enc_cmpl(bd_addr);
}

/*******************************************************************************
 *
 * Function         btm_ble_link_encrypted
 *
 * Description      This function is called when LE link encrption status is
 *                  changed.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_link_encrypted(const RawAddress& bd_addr, uint8_t encr_enable) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  bool enc_cback;

  log::verbose("bd_addr:{}, encr_enable={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               encr_enable);

  if (!p_dev_rec) {
    log::warn("No Device Found!");
    return;
  }

  enc_cback = p_dev_rec->sec_rec.is_security_state_le_encrypting();

  smp_link_encrypted(bd_addr, encr_enable);

  log::verbose("p_dev_rec->sec_rec.sec_flags=0x{:x}",
               p_dev_rec->sec_rec.sec_flags);

  if (encr_enable && p_dev_rec->sec_rec.enc_key_size == 0)
    p_dev_rec->sec_rec.enc_key_size = p_dev_rec->sec_rec.ble_keys.key_size;

  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
  if (p_dev_rec->sec_rec.p_callback && enc_cback) {
    if (encr_enable) btm_sec_dev_rec_cback_event(p_dev_rec, BTM_SUCCESS, true);
    /* LTK missing on peripheral */
    else if (p_dev_rec->role_central &&
             (p_dev_rec->sec_rec.sec_status == HCI_ERR_KEY_MISSING)) {
      btm_sec_dev_rec_cback_event(p_dev_rec, BTM_ERR_KEY_MISSING, true);
    } else if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_LINK_KEY_KNOWN)) {
      btm_sec_dev_rec_cback_event(p_dev_rec, BTM_FAILED_ON_SECURITY, true);
    } else if (p_dev_rec->role_central)
      btm_sec_dev_rec_cback_event(p_dev_rec, BTM_ERR_PROCESSING, true);
  }

  tBTM_BD_NAME remote_name = {};
  /* to notify GATT to send data if any request is pending,
  or if IOP matched, delay notifying until SMP_CMPLT_EVT */
  if (BTM_GetRemoteDeviceName(p_dev_rec->ble.pseudo_addr, remote_name) &&
      interop_match_name(INTEROP_SUSPEND_ATT_TRAFFIC_DURING_PAIRING,
                         (const char*)remote_name) &&
      (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_LE_ACTIVE) &&
      btm_sec_cb.pairing_bda == p_dev_rec->ble.pseudo_addr) {
    log::info(
        "INTEROP_DELAY_ATT_TRAFFIC_DURING_PAIRING: Waiting for bonding to "
        "complete to notify enc complete");
  } else {
    btm_ble_notify_enc_cmpl(p_dev_rec->ble.pseudo_addr, encr_enable);
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_ltk_request_reply
 *
 * Description      This function is called to send a LTK request reply on a
 *                  peripheral
 *                  device.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_ltk_request_reply(const RawAddress& bda, bool use_stk,
                               const Octet16& stk) {
  tBTM_SEC_DEV_REC* p_rec = btm_find_dev(bda);
  tBTM_SEC_CB* p_cb = &btm_sec_cb;

  log::debug("bd_addr:{},use_stk:{}", ADDRESS_TO_LOGGABLE_CSTR(bda), use_stk);

  if (p_rec == NULL) {
    log::error("unknown device");
    return;
  }

  p_cb->enc_handle = p_rec->ble_hci_handle;
  p_cb->key_size = p_rec->sec_rec.ble_keys.key_size;

  log::error("key size={}", p_rec->sec_rec.ble_keys.key_size);
  if (use_stk) {
    btsnd_hcic_ble_ltk_req_reply(btm_sec_cb.enc_handle, stk);
    return;
  }
  /* calculate LTK using peer device  */
  if (p_rec->sec_rec.ble_keys.key_type & BTM_LE_KEY_LENC) {
    btsnd_hcic_ble_ltk_req_reply(btm_sec_cb.enc_handle,
                                 p_rec->sec_rec.ble_keys.lltk);
    return;
  }

  p_rec = btm_find_dev_with_lenc(bda);
  if (!p_rec) {
    btsnd_hcic_ble_ltk_req_neg_reply(btm_sec_cb.enc_handle);
    return;
  }

  log::info("Found second sec_dev_rec for device that have LTK");
  /* This can happen when remote established LE connection using RPA to this
   * device, but then pair with us using Classing transport while still keeping
   * LE connection. If remote attempts to encrypt the LE connection, we might
   * end up here. We will eventually consolidate both entries, this is to avoid
   * race conditions. */

  ASSERT_LOG(p_rec->sec_rec.ble_keys.key_type & BTM_LE_KEY_LENC,
             "local enccryption key not present");
  p_cb->key_size = p_rec->sec_rec.ble_keys.key_size;
  btsnd_hcic_ble_ltk_req_reply(btm_sec_cb.enc_handle,
                               p_rec->sec_rec.ble_keys.lltk);
}

/*******************************************************************************
 *
 * Function         btm_ble_io_capabilities_req
 *
 * Description      This function is called to handle SMP get IO capability
 *                  request.
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t btm_ble_io_capabilities_req(tBTM_SEC_DEV_REC* p_dev_rec,
                                           tBTM_LE_IO_REQ* p_data) {
  uint8_t callback_rc = BTM_SUCCESS;
  log::verbose("p_dev_rec->bd_addr:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));
  if (btm_sec_cb.api.p_le_callback) {
    /* the callback function implementation may change the IO capability... */
    callback_rc = (*btm_sec_cb.api.p_le_callback)(
        BTM_LE_IO_REQ_EVT, p_dev_rec->bd_addr, (tBTM_LE_EVT_DATA*)p_data);
  }
  if ((callback_rc == BTM_SUCCESS) || (BTM_OOB_UNKNOWN != p_data->oob_data)) {
    p_data->auth_req &= BTM_LE_AUTH_REQ_MASK;

    log::verbose("1:p_dev_rec->sec_rec.security_required={}, auth_req:{}",
                 p_dev_rec->sec_rec.security_required, p_data->auth_req);
    log::verbose("2:i_keys=0x{:x} r_keys=0x{:x} (bit 0-LTK 1-IRK 2-CSRK)",
                 p_data->init_keys, p_data->resp_keys);

    /* if authentication requires MITM protection, put on the mask */
    if (p_dev_rec->sec_rec.security_required & BTM_SEC_IN_MITM)
      p_data->auth_req |= BTM_LE_AUTH_REQ_MITM;

    if (!(p_data->auth_req & SMP_AUTH_BOND)) {
      log::verbose("Non bonding: No keys should be exchanged");
      p_data->init_keys = 0;
      p_data->resp_keys = 0;
    }

    log::verbose("3:auth_req:{}", p_data->auth_req);
    log::verbose("4:i_keys=0x{:x} r_keys=0x{:x}", p_data->init_keys,
                 p_data->resp_keys);

    log::verbose("5:p_data->io_cap={} auth_req:{}", p_data->io_cap,
                 p_data->auth_req);

    /* remove MITM protection requirement if IO cap does not allow it */
    if ((p_data->io_cap == BTM_IO_CAP_NONE) && p_data->oob_data == SMP_OOB_NONE)
      p_data->auth_req &= ~BTM_LE_AUTH_REQ_MITM;

    if (!(p_data->auth_req & SMP_SC_SUPPORT_BIT)) {
      /* if Secure Connections are not supported then remove LK derivation,
      ** and keypress notifications.
      */
      log::verbose(
          "SC not supported -> No LK derivation, no keypress notifications");
      p_data->auth_req &= ~SMP_KP_SUPPORT_BIT;
      p_data->init_keys &= ~SMP_SEC_KEY_TYPE_LK;
      p_data->resp_keys &= ~SMP_SEC_KEY_TYPE_LK;
    }

    log::verbose("6:IO_CAP:{} oob_data:{} auth_req:0x{:02x}", p_data->io_cap,
                 p_data->oob_data, p_data->auth_req);
  }
  return callback_rc;
}

/*******************************************************************************
 *
 * Function         btm_ble_br_keys_req
 *
 * Description      This function is called to handle SMP request for keys sent
 *                  over BR/EDR.
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t btm_ble_br_keys_req(tBTM_SEC_DEV_REC* p_dev_rec,
                                   tBTM_LE_IO_REQ* p_data) {
  uint8_t callback_rc = BTM_SUCCESS;
  log::verbose("p_dev_rec->bd_addr:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));
  *p_data = tBTM_LE_IO_REQ{
      .io_cap = BTM_IO_CAP_UNKNOWN,
      .oob_data = false,
      .auth_req = BTM_LE_AUTH_REQ_SC_MITM_BOND,
      .max_key_size = BTM_BLE_MAX_KEY_SIZE,
      .init_keys = SMP_BR_SEC_DEFAULT_KEY,
      .resp_keys = SMP_BR_SEC_DEFAULT_KEY,
  };

  if (osi_property_get_bool(kPropertyCtkdDisableCsrkDistribution, false)) {
    p_data->init_keys &= (~SMP_SEC_KEY_TYPE_CSRK);
    p_data->resp_keys &= (~SMP_SEC_KEY_TYPE_CSRK);
  }

  return callback_rc;
}

/*******************************************************************************
 *
 * Function         btm_ble_connected
 *
 * Description      This function is when a LE connection to the peer device is
 *                  establsihed
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_connected(const RawAddress& bda, uint16_t handle, uint8_t enc_mode,
                       uint8_t role, tBLE_ADDR_TYPE addr_type,
                       bool addr_matched,
                       bool can_read_discoverable_characteristics) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(bda);

  log::info("Update timestamp for ble connection:{}",
            ADDRESS_TO_LOGGABLE_CSTR(bda));
  // TODO() Why is timestamp a counter ?
  p_dev_rec->timestamp = btm_sec_cb.dev_rec_count++;

  if (is_ble_addr_type_known(addr_type))
    p_dev_rec->ble.SetAddressType(addr_type);
  else
    log::warn(
        "Please do not update device record from anonymous le advertisement");

  p_dev_rec->ble.pseudo_addr = bda;
  p_dev_rec->ble_hci_handle = handle;
  p_dev_rec->device_type |= BT_DEVICE_TYPE_BLE;
  p_dev_rec->role_central = (role == HCI_ROLE_CENTRAL) ? true : false;
  p_dev_rec->can_read_discoverable = can_read_discoverable_characteristics;

  if (!addr_matched) {
    p_dev_rec->ble.active_addr_type = BTM_BLE_ADDR_PSEUDO;
    if (p_dev_rec->ble.AddressType() == BLE_ADDR_RANDOM) {
      p_dev_rec->ble.cur_rand_addr = bda;
    }
  }
  btm_cb.ble_ctr_cb.inq_var.directed_conn = BTM_BLE_ADV_IND_EVT;
}

static bool btm_ble_complete_evt_ignore(const tBTM_SEC_DEV_REC* p_dev_rec,
                                        const tSMP_EVT_DATA* p_data) {
  // Encryption request in peripheral role results in SMP Security request. SMP may generate a
  // SMP_COMPLT_EVT failure event cases like below:
  // 1) Some central devices don't handle cross-over between encryption and SMP security request
  // 2) Link may get disconnected after the SMP security request was sent.
  if (p_data->cmplt.reason != SMP_SUCCESS && !p_dev_rec->role_central &&
      btm_sec_cb.pairing_bda != p_dev_rec->bd_addr &&
      btm_sec_cb.pairing_bda != p_dev_rec->ble.pseudo_addr &&
      p_dev_rec->sec_rec.is_le_link_key_known() &&
      p_dev_rec->sec_rec.ble_keys.key_type != BTM_LE_KEY_NONE) {
    if (p_dev_rec->sec_rec.is_le_device_encrypted()) {
      log::warn("Bonded device {} is already encrypted, ignoring SMP failure",
                ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));
      return true;
    } else if (p_data->cmplt.reason == SMP_CONN_TOUT) {
      log::warn("Bonded device {} disconnected while waiting for encryption, ignoring SMP failure",
                ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));
      l2cu_start_post_bond_timer(p_dev_rec->ble_hci_handle);
      return true;
    }
  }

  return false;
}


/*****************************************************************************
 *  Function        btm_proc_smp_cback
 *
 *  Description     This function is the SMP callback handler.
 *
 *****************************************************************************/
tBTM_STATUS btm_proc_smp_cback(tSMP_EVT event, const RawAddress& bd_addr,
                               const tSMP_EVT_DATA* p_data) {
  log::verbose("bd_addr:{}, event={}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               smp_evt_to_text(event).c_str());

  if (event == SMP_SC_LOC_OOB_DATA_UP_EVT) {
    btm_sec_cr_loc_oob_data_cback_event(RawAddress{}, p_data->loc_oob_data);
    return BTM_SUCCESS;
  }

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  tBTM_STATUS res = BTM_SUCCESS;

  if (p_dev_rec != NULL) {
    switch (event) {
      case SMP_IO_CAP_REQ_EVT:
        btm_ble_io_capabilities_req(p_dev_rec,
                                    (tBTM_LE_IO_REQ*)&p_data->io_req);
        break;

      case SMP_BR_KEYS_REQ_EVT:
        btm_ble_br_keys_req(p_dev_rec, (tBTM_LE_IO_REQ*)&p_data->io_req);
        break;

      case SMP_PASSKEY_REQ_EVT:
      case SMP_PASSKEY_NOTIF_EVT:
      case SMP_OOB_REQ_EVT:
      case SMP_NC_REQ_EVT:
      case SMP_SC_OOB_REQ_EVT:
        p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LE_AUTHENTICATED;
        FALLTHROUGH_INTENDED; /* FALLTHROUGH */

      case SMP_CONSENT_REQ_EVT:
      case SMP_SEC_REQUEST_EVT:
        if (event == SMP_SEC_REQUEST_EVT &&
            btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) {
          log::verbose("Ignoring SMP Security request");
          break;
        }
        btm_sec_cb.pairing_bda = bd_addr;
        if (event != SMP_CONSENT_REQ_EVT) {
          p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_AUTHENTICATING;
        }
        btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_LE_ACTIVE;
        FALLTHROUGH_INTENDED; /* FALLTHROUGH */

      case SMP_COMPLT_EVT:
        if (btm_sec_cb.api.p_le_callback) {
          /* the callback function implementation may change the IO
           * capability... */
          log::verbose("btm_sec_cb.api.p_le_callback=0x{}",
                       fmt::ptr(btm_sec_cb.api.p_le_callback));
          (*btm_sec_cb.api.p_le_callback)(static_cast<tBTM_LE_EVT>(event),
                                          bd_addr, (tBTM_LE_EVT_DATA*)p_data);
        }

        if (event == SMP_COMPLT_EVT) {
          p_dev_rec = btm_find_dev(bd_addr);
          if (p_dev_rec == NULL) {
            log::error("p_dev_rec is NULL");
            return BTM_SUCCESS;
          }

          if (btm_ble_complete_evt_ignore(p_dev_rec, p_data)) {
            return BTM_SUCCESS;
          }

          log::verbose("before update sec_level=0x{:x} sec_flags=0x{:x}",
                       p_data->cmplt.sec_level, p_dev_rec->sec_rec.sec_flags);

          res = (p_data->cmplt.reason == SMP_SUCCESS) ? BTM_SUCCESS
                                                      : BTM_ERR_PROCESSING;

          log::verbose(
              "after update result={} sec_level=0x{:x} sec_flags=0x{:x}", res,
              p_data->cmplt.sec_level, p_dev_rec->sec_rec.sec_flags);

          if (p_data->cmplt.is_pair_cancel &&
              btm_sec_cb.api.p_bond_cancel_cmpl_callback) {
            log::verbose("Pairing Cancel completed");
            (*btm_sec_cb.api.p_bond_cancel_cmpl_callback)(BTM_SUCCESS);
          }

          if (res != BTM_SUCCESS && p_data->cmplt.reason != SMP_CONN_TOUT) {
            log::verbose("Pairing failed - prepare to remove ACL");
            l2cu_start_post_bond_timer(p_dev_rec->ble_hci_handle);
          }

          log::verbose(
              "btm_sec_cb.pairing_state={:x} pairing_flags={:x} "
              "pin_code_len={:x}",
              btm_sec_cb.pairing_state, btm_sec_cb.pairing_flags,
              btm_sec_cb.pin_code_len);

          /* Reset btm state only if the callback address matches pairing
           * address*/
          if (bd_addr == btm_sec_cb.pairing_bda) {
            btm_sec_cb.pairing_bda = RawAddress::kAny;
            btm_sec_cb.pairing_state = BTM_PAIR_STATE_IDLE;
            btm_sec_cb.pairing_flags = 0;
          }

          if (res == BTM_SUCCESS) {
            p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;

            if (p_dev_rec->sec_rec.bond_type != BOND_TYPE_TEMPORARY) {
              // Add all bonded device into resolving list if IRK is available.
              btm_ble_resolving_list_load_dev(*p_dev_rec);
            } else if (p_dev_rec->ble_hci_handle == HCI_INVALID_HANDLE) {
              // At this point LTK should have been dropped by btif.
              // Reset the flags here if LE is not connected (over BR),
              // otherwise they would be reset on disconnected.
              log::debug(
                  "SMP over BR triggered by temporary bond has completed, "
                  "resetting the LK flags");
              p_dev_rec->sec_rec.sec_flags &= ~(BTM_SEC_LE_LINK_KEY_KNOWN);
              p_dev_rec->sec_rec.ble_keys.key_type = BTM_LE_KEY_NONE;
            }
          }
          tBTM_BD_NAME remote_name = {};
          if (BTM_GetRemoteDeviceName(p_dev_rec->ble.pseudo_addr,
                                      remote_name) &&
              interop_match_name(INTEROP_SUSPEND_ATT_TRAFFIC_DURING_PAIRING,
                                 (const char*)remote_name)) {
            log::debug("Notifying encryption cmpl delayed due to IOP match");
            btm_ble_notify_enc_cmpl(p_dev_rec->ble.pseudo_addr, true);
          }

          btm_sec_dev_rec_cback_event(p_dev_rec, res, true);
        }
        break;

      case SMP_LE_ADDR_ASSOC_EVT:
        if (btm_sec_cb.api.p_le_callback) {
          log::verbose("btm_sec_cb.api.p_le_callback=0x{}",
                       fmt::ptr(btm_sec_cb.api.p_le_callback));
          (*btm_sec_cb.api.p_le_callback)(static_cast<tBTM_LE_EVT>(event),
                                          bd_addr, (tBTM_LE_EVT_DATA*)p_data);
        }
        break;

      case SMP_SIRK_VERIFICATION_REQ_EVT:
        res = (*btm_sec_cb.api.p_sirk_verification_callback)(bd_addr);
        log::debug("SMP SIRK verification result:{}",
                   btm_status_text(res).c_str());
        if (res != BTM_CMD_STARTED) {
          return res;
        }

        break;

      default:
        log::verbose("unknown event={}", smp_evt_to_text(event).c_str());
        break;
    }
  } else {
    log::warn("Unexpected event '{}' for unknown device.",
              smp_evt_to_text(event).c_str());
  }

  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTM_BleDataSignature
 *
 * Description      This function is called to sign the data using AES128 CMAC
 *                  algorithm.
 *
 * Parameter        bd_addr: target device the data to be signed for.
 *                  p_text: singing data
 *                  len: length of the data to be signed.
 *                  signature: output parameter where data signature is going to
 *                             be stored.
 *
 * Returns          true if signing sucessul, otherwise false.
 *
 ******************************************************************************/
bool BTM_BleDataSignature(const RawAddress& bd_addr, uint8_t* p_text,
                          uint16_t len, BLE_SIGNATURE signature) {
  tBTM_SEC_DEV_REC* p_rec = btm_find_dev(bd_addr);

  if (p_rec == NULL) {
    log::error("data signing can not be done from unknown device");
    return false;
  }

  uint8_t* p_mac = (uint8_t*)signature;
  uint8_t* pp;
  uint8_t* p_buf = (uint8_t*)osi_malloc(len + 4);

  pp = p_buf;
  /* prepare plain text */
  if (p_text) {
    memcpy(p_buf, p_text, len);
    pp = (p_buf + len);
  }

  UINT32_TO_STREAM(pp, p_rec->sec_rec.ble_keys.local_counter);
  UINT32_TO_STREAM(p_mac, p_rec->sec_rec.ble_keys.local_counter);

  crypto_toolbox::aes_cmac(p_rec->sec_rec.ble_keys.lcsrk, p_buf,
                           (uint16_t)(len + 4), BTM_CMAC_TLEN_SIZE, p_mac);
  p_rec->sec_rec.increment_sign_counter(true);

  log::verbose("p_mac = {}", fmt::ptr(p_mac));
  log::verbose(
      "p_mac[0]=0x{:02x} p_mac[1]=0x{:02x} p_mac[2]=0x{:02x} p_mac[3]=0x{:02x}",
      *p_mac, *(p_mac + 1), *(p_mac + 2), *(p_mac + 3));
  log::verbose(
      "p_mac[4]=0x{:02x} p_mac[5]=0x{:02x} p_mac[6]=0x{:02x} p_mac[7]=0x{:02x}",
      *(p_mac + 4), *(p_mac + 5), *(p_mac + 6), *(p_mac + 7));
  osi_free(p_buf);
  return true;
}

/*******************************************************************************
 *
 * Function         BTM_BleVerifySignature
 *
 * Description      This function is called to verify the data signature
 *
 * Parameter        bd_addr: target device the data to be signed for.
 *                  p_orig:  original data before signature.
 *                  len: length of the signing data
 *                  counter: counter used when doing data signing
 *                  p_comp: signature to be compared against.

 * Returns          true if signature verified correctly; otherwise false.
 *
 ******************************************************************************/
bool BTM_BleVerifySignature(const RawAddress& bd_addr, uint8_t* p_orig,
                            uint16_t len, uint32_t counter, uint8_t* p_comp) {
  bool verified = false;
  tBTM_SEC_DEV_REC* p_rec = btm_find_dev(bd_addr);
  uint8_t p_mac[BTM_CMAC_TLEN_SIZE];

  if (p_rec == NULL ||
      (p_rec && !(p_rec->sec_rec.ble_keys.key_type & BTM_LE_KEY_PCSRK))) {
    log::error("can not verify signature for unknown device");
  } else if (counter < p_rec->sec_rec.ble_keys.counter) {
    log::error("signature received with out dated sign counter");
  } else if (p_orig == NULL) {
    log::error("No signature to verify");
  } else {
    log::verbose("rcv_cnt={} >= expected_cnt={}", counter,
                 p_rec->sec_rec.ble_keys.counter);

    crypto_toolbox::aes_cmac(p_rec->sec_rec.ble_keys.pcsrk, p_orig, len,
                             BTM_CMAC_TLEN_SIZE, p_mac);
    if (CRYPTO_memcmp(p_mac, p_comp, BTM_CMAC_TLEN_SIZE) == 0) {
      p_rec->sec_rec.increment_sign_counter(false);
      verified = true;
    }
  }
  return verified;
}

/*******************************************************************************
 *
 * Function         BTM_BleSirkConfirmDeviceReply
 *
 * Description      This procedure confirms requested to validate set device.
 *
 * Parameter        bd_addr     - BD address of the peer
 *                  res         - confirmation result BTM_SUCCESS if success
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleSirkConfirmDeviceReply(const RawAddress& bd_addr, uint8_t res) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  tSMP_STATUS res_smp = (res == BTM_SUCCESS) ? SMP_SUCCESS : SMP_FAIL;

  log::info("bd_addr:{}, result:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
            smp_status_text(res_smp).c_str());

  if (p_dev_rec == NULL) {
    log::error("Confirmation of Unknown device");
    return;
  }

  BTM_LogHistory(
      kBtmLogTag, bd_addr, "SIRK confirmation",
      base::StringPrintf("status:%s", smp_status_text(res_smp).c_str()));
  SMP_SirkConfirmDeviceReply(bd_addr, res_smp);
}

/*******************************************************************************
 *  Utility functions for LE device IR/ER generation
 ******************************************************************************/
/** This function is to notify application new keys have been generated. */
static void btm_notify_new_key(uint8_t key_type) {
  tBTM_BLE_LOCAL_KEYS* p_local_keys = NULL;

  log::verbose("key_type={}", key_type);

  if (btm_sec_cb.api.p_le_key_callback) {
    switch (key_type) {
      case BTM_BLE_KEY_TYPE_ID:
        log::verbose("BTM_BLE_KEY_TYPE_ID");
        p_local_keys = (tBTM_BLE_LOCAL_KEYS*)&btm_sec_cb.devcb.id_keys;
        break;

      case BTM_BLE_KEY_TYPE_ER:
        log::verbose("BTM_BLE_KEY_TYPE_ER");
        p_local_keys =
            (tBTM_BLE_LOCAL_KEYS*)&btm_sec_cb.devcb.ble_encryption_key_value;
        break;

      default:
        log::error("unknown key type: {}", key_type);
        break;
    }
    if (p_local_keys != NULL)
      (*btm_sec_cb.api.p_le_key_callback)(key_type, p_local_keys);
  }
}

/** implementation of btm_ble_reset_id */
static void btm_ble_reset_id_impl(const Octet16& rand1, const Octet16& rand2) {
  /* Regenerate Identity Root */
  btm_sec_cb.devcb.id_keys.ir = rand1;
  Octet16 btm_ble_dhk_pt{};
  btm_ble_dhk_pt[0] = 0x03;

  /* generate DHK= Eir({0x03, 0x00, 0x00 ...}) */
  btm_sec_cb.devcb.id_keys.dhk =
      crypto_toolbox::aes_128(btm_sec_cb.devcb.id_keys.ir, btm_ble_dhk_pt);

  Octet16 btm_ble_irk_pt{};
  btm_ble_irk_pt[0] = 0x01;
  /* IRK = D1(IR, 1) */
  btm_sec_cb.devcb.id_keys.irk =
      crypto_toolbox::aes_128(btm_sec_cb.devcb.id_keys.ir, btm_ble_irk_pt);

  btm_notify_new_key(BTM_BLE_KEY_TYPE_ID);

  /* if privacy is enabled, new RPA should be calculated */
  if (btm_cb.ble_ctr_cb.privacy_mode != BTM_PRIVACY_NONE) {
    btm_gen_resolvable_private_addr(base::Bind(&btm_gen_resolve_paddr_low));
  }

  /* proceed generate ER */
  btm_sec_cb.devcb.ble_encryption_key_value = rand2;
  btm_notify_new_key(BTM_BLE_KEY_TYPE_ER);

  /* if privacy is enabled, update the irk and RPA in the LE address manager */
  if (btm_cb.ble_ctr_cb.privacy_mode != BTM_PRIVACY_NONE) {
    BTM_BleConfigPrivacy(true);
  }
}

struct reset_id_data {
  Octet16 rand1;
  Octet16 rand2;
};

/** This function is called to reset LE device identity. */
void btm_ble_reset_id(void) {
  log::verbose("btm_ble_reset_id");

  /* In order to reset identity, we need four random numbers. Make four nested
   * calls to generate them first, then proceed to perform the actual reset in
   * btm_ble_reset_id_impl. */
  btsnd_hcic_ble_rand(base::Bind([](BT_OCTET8 rand) {
    reset_id_data tmp;
    memcpy(tmp.rand1.data(), rand, BT_OCTET8_LEN);
    btsnd_hcic_ble_rand(base::Bind(
        [](reset_id_data tmp, BT_OCTET8 rand) {
          memcpy(tmp.rand1.data() + 8, rand, BT_OCTET8_LEN);
          btsnd_hcic_ble_rand(base::Bind(
              [](reset_id_data tmp, BT_OCTET8 rand) {
                memcpy(tmp.rand2.data(), rand, BT_OCTET8_LEN);
                btsnd_hcic_ble_rand(base::Bind(
                    [](reset_id_data tmp, BT_OCTET8 rand) {
                      memcpy(tmp.rand2.data() + 8, rand, BT_OCTET8_LEN);
                      // when all random numbers are ready, do the actual reset.
                      btm_ble_reset_id_impl(tmp.rand1, tmp.rand2);
                    },
                    tmp));
              },
              tmp));
        },
        tmp));
  }));
}

/*******************************************************************************
 *
 * Function         btm_ble_get_acl_remote_addr
 *
 * Description      This function reads the active remote address used for the
 *                  connection.
 *
 * Returns          success return true, otherwise false.
 *
 ******************************************************************************/
bool btm_ble_get_acl_remote_addr(uint16_t hci_handle, RawAddress& conn_addr,
                                 tBLE_ADDR_TYPE* p_addr_type) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(hci_handle);
  if (p_dev_rec == nullptr) {
    log::warn("Unable to find security device record hci_handle:{}",
              hci_handle);
    // TODO Release acl resource
    return false;
  }

  bool st = true;

  switch (p_dev_rec->ble.active_addr_type) {
    case BTM_BLE_ADDR_PSEUDO:
      conn_addr = p_dev_rec->bd_addr;
      *p_addr_type = p_dev_rec->ble.AddressType();
      break;

    case BTM_BLE_ADDR_RRA:
      conn_addr = p_dev_rec->ble.cur_rand_addr;
      *p_addr_type = BLE_ADDR_RANDOM;
      break;

    case BTM_BLE_ADDR_STATIC:
      conn_addr = p_dev_rec->ble.identity_address_with_type.bda;
      *p_addr_type = p_dev_rec->ble.identity_address_with_type.type;
      break;

    default:
      log::warn("Unable to find record with active address type:{}",
                p_dev_rec->ble.active_addr_type);
      st = false;
      break;
  }
  return st;
}

std::optional<Octet16> BTM_BleGetPeerLTK(const RawAddress address) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(address);
  if (p_dev_rec == nullptr) {
    return std::nullopt;
  }

  return p_dev_rec->sec_rec.ble_keys.pltk;
}

std::optional<Octet16> BTM_BleGetPeerIRK(const RawAddress address) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(address);
  if (p_dev_rec == nullptr) {
    return std::nullopt;
  }

  return p_dev_rec->sec_rec.ble_keys.irk;
}

bool BTM_BleIsLinkKeyKnown(const RawAddress address) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(address);
  return p_dev_rec != nullptr && p_dev_rec->sec_rec.is_le_link_key_known();
}

std::optional<tBLE_BD_ADDR> BTM_BleGetIdentityAddress(
    const RawAddress address) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(address);
  if (p_dev_rec == nullptr) {
    return std::nullopt;
  }

  return p_dev_rec->ble.identity_address_with_type;
}
