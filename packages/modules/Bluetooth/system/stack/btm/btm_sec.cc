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
 *  This file contains functions for the Bluetooth Security Manager
 *
 ******************************************************************************/

#define LOG_TAG "bt_btm_sec"

#include "stack/btm/btm_sec.h"

#include <android_bluetooth_flags.h>
#include <base/functional/bind.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <string>

#include "bt_dev_class.h"
#include "btif/include/btif_storage.h"
#include "common/init_flags.h"
#include "common/metrics.h"
#include "common/time_util.h"
#include "device/include/controller.h"
#include "device/include/device_iot_config.h"
#include "device/include/interop.h"
#include "hci/controller_interface.h"
#include "internal_include/bt_target.h"
#include "l2c_api.h"
#include "main/shim/entry.h"
#include "osi/include/allocator.h"
#include "osi/include/properties.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_ble_sec.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/btm/btm_sec_int_types.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_ble_addr.h"
#include "stack/include/btm_ble_api.h"
#include "stack/include/btm_ble_privacy.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/btm_sec_api.h"
#include "stack/include/btm_status.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/l2cap_security_interface.h"
#include "stack/include/main_thread.h"
#include "stack/include/smp_api.h"
#include "stack/include/stack_metrics_logging.h"
#include "types/raw_address.h"

namespace {

constexpr char kBtmLogTag[] = "SEC";

}

using namespace bluetooth;

extern tBTM_CB btm_cb;

#define BTM_SEC_MAX_COLLISION_DELAY (5000)
#define BTM_SEC_START_AUTH_DELAY (200)

#define BTM_SEC_IS_SM4(sm) ((bool)(BTM_SM4_TRUE == ((sm)&BTM_SM4_TRUE)))
#define BTM_SEC_IS_SM4_LEGACY(sm) ((bool)(BTM_SM4_KNOWN == ((sm)&BTM_SM4_TRUE)))
#define BTM_SEC_IS_SM4_UNKNOWN(sm) \
  ((bool)(BTM_SM4_UNKNOWN == ((sm)&BTM_SM4_TRUE)))

#define BTM_SEC_LE_MASK                              \
  (BTM_SEC_LE_AUTHENTICATED | BTM_SEC_LE_ENCRYPTED | \
   BTM_SEC_LE_LINK_KEY_KNOWN | BTM_SEC_LE_LINK_KEY_AUTHED)

void btm_inq_stop_on_ssp(void);
bool btm_ble_init_pseudo_addr(tBTM_SEC_DEV_REC* p_dev_rec,
                              const RawAddress& new_pseudo_addr);
void bta_dm_remove_device(const RawAddress& bd_addr);
void bta_dm_remote_key_missing(const RawAddress bd_addr);
void bta_dm_process_remove_device(const RawAddress& bd_addr);
void btm_inq_clear_ssp(void);

static tBTM_STATUS btm_sec_execute_procedure(tBTM_SEC_DEV_REC* p_dev_rec);
static bool btm_sec_start_get_name(tBTM_SEC_DEV_REC* p_dev_rec);
static void btm_sec_wait_and_start_authentication(tBTM_SEC_DEV_REC* p_dev_rec);
static void btm_sec_auth_timer_timeout(void* data);
static void btm_sec_collision_timeout(void* data);
static void btm_restore_mode(void);
static void btm_sec_pairing_timeout(void* data);
static tBTM_STATUS btm_sec_dd_create_conn(tBTM_SEC_DEV_REC* p_dev_rec);

static void btm_sec_check_pending_reqs(void);
static bool btm_sec_queue_mx_request(const RawAddress& bd_addr, uint16_t psm,
                                     bool is_orig, uint16_t security_required,
                                     tBTM_SEC_CALLBACK* p_callback,
                                     void* p_ref_data);
static void btm_sec_bond_cancel_complete(void);
static void btm_send_link_key_notif(tBTM_SEC_DEV_REC* p_dev_rec);
static bool btm_sec_check_prefetch_pin(tBTM_SEC_DEV_REC* p_dev_rec);

static tBTM_STATUS btm_sec_send_hci_disconnect(tBTM_SEC_DEV_REC* p_dev_rec,
                                               tHCI_STATUS reason,
                                               uint16_t conn_handle,
                                               std::string comment);

static bool btm_dev_authenticated(const tBTM_SEC_DEV_REC* p_dev_rec);
static bool btm_dev_encrypted(const tBTM_SEC_DEV_REC* p_dev_rec);
static uint16_t btm_sec_set_serv_level4_flags(uint16_t cur_security,
                                              bool is_originator);

static void btm_sec_queue_encrypt_request(const RawAddress& bd_addr,
                                          tBT_TRANSPORT transport,
                                          tBTM_SEC_CALLBACK* p_callback,
                                          void* p_ref_data,
                                          tBTM_BLE_SEC_ACT sec_act);
static void btm_sec_check_pending_enc_req(tBTM_SEC_DEV_REC* p_dev_rec,
                                          tBT_TRANSPORT transport,
                                          uint8_t encr_enable);

static bool btm_sec_use_smp_br_chnl(tBTM_SEC_DEV_REC* p_dev_rec);

/* true - authenticated link key is possible */
static const bool btm_sec_io_map[BTM_IO_CAP_MAX][BTM_IO_CAP_MAX] = {
    /*   OUT,    IO,     IN,     NONE */
    /* OUT  */ {false, false, true, false},
    /* IO   */ {false, true, true, false},
    /* IN   */ {true, true, true, false},
    /* NONE */ {false, false, false, false}};
/*  BTM_IO_CAP_OUT      0   DisplayOnly */
/*  BTM_IO_CAP_IO       1   DisplayYesNo */
/*  BTM_IO_CAP_IN       2   KeyboardOnly */
/*  BTM_IO_CAP_NONE     3   NoInputNoOutput */

static void NotifyBondingChange(tBTM_SEC_DEV_REC& p_dev_rec,
                                tHCI_STATUS status) {
  if (btm_sec_cb.api.p_auth_complete_callback != nullptr) {
    (*btm_sec_cb.api.p_auth_complete_callback)(
        p_dev_rec.bd_addr, p_dev_rec.dev_class, p_dev_rec.sec_bd_name, status);
  }
}

static bool concurrentPeerAuthIsEnabled() {
  // Was previously named BTM_DISABLE_CONCURRENT_PEER_AUTH.
  // Renamed to ENABLED for homogeneity with system properties
  static const bool sCONCURRENT_PEER_AUTH_IS_ENABLED = osi_property_get_bool(
      "bluetooth.btm.sec.concurrent_peer_auth.enabled", true);
  return sCONCURRENT_PEER_AUTH_IS_ENABLED;
}

/**
 * Whether we should handle encryption change events from a peer device, while
 * we are in the IDLE state. This matters if we are waiting to retry encryption
 * following an LMP timeout, and then we get an encryption change event from the
 * peer.
 */
static bool handleUnexpectedEncryptionChange() {
  static const bool sHandleUnexpectedEncryptionChange = osi_property_get_bool(
      "bluetooth.btm.sec.handle_unexpected_encryption_change.enabled", false);
  return sHandleUnexpectedEncryptionChange;
}

void NotifyBondingCanceled(tBTM_STATUS btm_status) {
  if (btm_sec_cb.api.p_bond_cancel_cmpl_callback) {
    btm_sec_cb.api.p_bond_cancel_cmpl_callback(BTM_SUCCESS);
  }
}

/*******************************************************************************
 *
 * Function         btm_dev_authenticated
 *
 * Description      check device is authenticated on BR/EDR
 *
 * Returns          bool    true or false
 *
 ******************************************************************************/
static bool btm_dev_authenticated(const tBTM_SEC_DEV_REC* p_dev_rec) {
  return p_dev_rec->sec_rec.sec_flags & BTM_SEC_AUTHENTICATED;
}

/*******************************************************************************
 *
 * Function         btm_dev_encrypted
 *
 * Description      check device is encrypted on BR/EDR
 *
 * Returns          bool    true or false
 *
 ******************************************************************************/
static bool btm_dev_encrypted(const tBTM_SEC_DEV_REC* p_dev_rec) {
  return p_dev_rec->sec_rec.sec_flags & BTM_SEC_ENCRYPTED;
}

/*******************************************************************************
 *
 * Function         btm_dev_16_digit_authenticated
 *
 * Description      check device is authenticated by using 16 digit pin or MITM (BR/EDR)
 *
 * Returns          bool    true or false
 *
 ******************************************************************************/
static bool btm_dev_16_digit_authenticated(const tBTM_SEC_DEV_REC* p_dev_rec) {
  // BTM_SEC_16_DIGIT_PIN_AUTHED is set if MITM or 16 digit pin is used
  return p_dev_rec->sec_rec.sec_flags & BTM_SEC_16_DIGIT_PIN_AUTHED;
}

static bool is_sec_state_equal(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);
  uint8_t* state = static_cast<uint8_t*>(context);

  if (p_dev_rec->sec_rec.sec_state == *state) return false;

  return true;
}

/*******************************************************************************
 *
 * Function         btm_sec_find_dev_by_sec_state
 *
 * Description      Look for the record in the device database for the device
 *                  which is being authenticated or encrypted
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
static tBTM_SEC_DEV_REC* btm_sec_find_dev_by_sec_state(uint8_t state) {
  list_node_t* n =
      list_foreach(btm_sec_cb.sec_dev_rec, is_sec_state_equal, &state);
  if (n) return static_cast<tBTM_SEC_DEV_REC*>(list_node(n));

  return nullptr;
}

/*******************************************************************************
 *
 * Function         btm_sec_is_device_sc_downgrade
 *
 * Description      Check for a stored device record matching the candidate
 *                  device, and return true if the stored device has reported
 *                  that it supports Secure Connections mode and the candidate
 *                  device reports that it does not.  Otherwise, return false.
 *
 * Returns          bool
 *
 ******************************************************************************/
static bool btm_sec_is_device_sc_downgrade(uint16_t hci_handle,
                                           bool secure_connections_supported) {
  if (secure_connections_supported) return false;

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(hci_handle);
  if (p_dev_rec == nullptr) return false;

  uint8_t property_val = 0;
  bt_property_t property = {
      .type = BT_PROPERTY_REMOTE_SECURE_CONNECTIONS_SUPPORTED,
      .len = sizeof(uint8_t),
      .val = &property_val};

  bt_status_t cached =
      btif_storage_get_remote_device_property(&p_dev_rec->bd_addr, &property);

  if (cached == BT_STATUS_FAIL) return false;

  return (bool)property_val;
}

/*******************************************************************************
 *
 * Function         btm_sec_store_device_sc_support
 *
 * Description      Save Secure Connections support for this device to file
 *
 ******************************************************************************/

static void btm_sec_store_device_sc_support(uint16_t hci_handle,
                                            bool secure_connections_supported) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(hci_handle);
  if (p_dev_rec == nullptr) return;

  uint8_t property_val = (uint8_t)secure_connections_supported;
  bt_property_t property = {
      .type = BT_PROPERTY_REMOTE_SECURE_CONNECTIONS_SUPPORTED,
      .len = sizeof(uint8_t),
      .val = &property_val};

  btif_storage_set_remote_device_property(&p_dev_rec->bd_addr, &property);
}

/*******************************************************************************
 *
 * Function         btm_sec_is_session_key_size_downgrade
 *
 * Description      Check if there is a stored device record matching this
 *                  handle, and return true if the stored record has a lower
 *                  session key size than the candidate device.
 *
 * Returns          bool
 *
 ******************************************************************************/
static bool btm_sec_is_session_key_size_downgrade(uint16_t hci_handle,
                                                  uint8_t key_size) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(hci_handle);
  if (p_dev_rec == nullptr) return false;

  uint8_t property_val = 0;
  bt_property_t property = {.type = BT_PROPERTY_REMOTE_MAX_SESSION_KEY_SIZE,
                            .len = sizeof(uint8_t),
                            .val = &property_val};

  bt_status_t cached =
      btif_storage_get_remote_device_property(&p_dev_rec->bd_addr, &property);

  if (cached == BT_STATUS_FAIL) return false;

  return property_val > key_size;
}

/*******************************************************************************
 *
 * Function         btm_sec_update_session_key_size
 *
 * Description      Store the max session key size to disk, if possible.
 *
 ******************************************************************************/
static void btm_sec_update_session_key_size(uint16_t hci_handle,
                                            uint8_t key_size) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(hci_handle);
  if (p_dev_rec == nullptr) return;

  uint8_t property_val = key_size;
  bt_property_t property = {.type = BT_PROPERTY_REMOTE_MAX_SESSION_KEY_SIZE,
                            .len = sizeof(uint8_t),
                            .val = &property_val};

  btif_storage_set_remote_device_property(&p_dev_rec->bd_addr, &property);
}

/*******************************************************************************
 *
 * Function         access_secure_service_from_temp_bond
 *
 * Description      a utility function to test whether an access to
 *                  secure service from temp bonding is happening
 *
 * Returns          true if the aforementioned condition holds,
 *                  false otherwise
 *
 ******************************************************************************/
static bool access_secure_service_from_temp_bond(const tBTM_SEC_DEV_REC* p_dev_rec,
                                                 bool locally_initiated,
                                                 uint16_t security_req) {
  return !locally_initiated && (security_req & BTM_SEC_IN_AUTHENTICATE) &&
         p_dev_rec->sec_rec.is_bond_type_temporary();
}

/*******************************************************************************
 *
 * Function         BTM_SecRegister
 *
 * Description      Application manager calls this function to register for
 *                  security services.  There can be one and only one
 *                  application saving link keys.  BTM allows only first
 *                  registration.
 *
 * Returns          true if registered OK, else false
 *
 ******************************************************************************/
bool BTM_SecRegister(const tBTM_APPL_INFO* p_cb_info) {
  log::info("p_cb_info->p_le_callback == 0x{}",
            fmt::ptr(p_cb_info->p_le_callback));
  if (p_cb_info->p_le_callback) {
    log::verbose("SMP_Register( btm_proc_smp_cback )");
    SMP_Register(btm_proc_smp_cback);
    Octet16 zero{0};
    /* if no IR is loaded, need to regenerate all the keys */
    if (btm_sec_cb.devcb.id_keys.ir == zero) {
      btm_ble_reset_id();
    }
  } else {
    log::warn("p_cb_info->p_le_callback == NULL");
  }

  btm_sec_cb.api = *p_cb_info;
  log::info("btm_sec_cb.api.p_le_callback = 0x{} ",
            fmt::ptr(btm_sec_cb.api.p_le_callback));
  log::verbose("application registered");
  return (true);
}

/*******************************************************************************
 *
 * Function         BTM_SecAddRmtNameNotifyCallback
 *
 * Description      Any profile can register to be notified when name of the
 *                  remote device is resolved.
 *
 * Returns          true if registered OK, else false
 *
 ******************************************************************************/
bool BTM_SecAddRmtNameNotifyCallback(tBTM_RMT_NAME_CALLBACK* p_callback) {
  int i;

  for (i = 0; i < BTM_SEC_MAX_RMT_NAME_CALLBACKS; i++) {
    if (btm_cb.p_rmt_name_callback[i] == NULL) {
      btm_cb.p_rmt_name_callback[i] = p_callback;
      return (true);
    }
  }

  return (false);
}

/*******************************************************************************
 *
 * Function         BTM_SecDeleteRmtNameNotifyCallback
 *
 * Description      Any profile can deregister notification when a new Link Key
 *                  is generated per connection.
 *
 * Returns          true if OK, else false
 *
 ******************************************************************************/
bool BTM_SecDeleteRmtNameNotifyCallback(tBTM_RMT_NAME_CALLBACK* p_callback) {
  int i;

  for (i = 0; i < BTM_SEC_MAX_RMT_NAME_CALLBACKS; i++) {
    if (btm_cb.p_rmt_name_callback[i] == p_callback) {
      btm_cb.p_rmt_name_callback[i] = NULL;
      return (true);
    }
  }

  return (false);
}

bool BTM_IsEncrypted(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  return btm_sec_cb.IsDeviceEncrypted(bd_addr, transport);
}

bool BTM_IsLinkKeyAuthed(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  return btm_sec_cb.IsLinkKeyAuthenticated(bd_addr, transport);
}

bool BTM_IsLinkKeyKnown(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  return btm_sec_cb.IsLinkKeyKnown(bd_addr, transport);
}

bool BTM_IsAuthenticated(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  return btm_sec_cb.IsDeviceAuthenticated(bd_addr, transport);
}

bool BTM_CanReadDiscoverableCharacteristics(const RawAddress& bd_addr) {
  auto p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec != nullptr) {
    return p_dev_rec->can_read_discoverable;
  } else {
    log::error(
        "BTM_CanReadDiscoverableCharacteristics invoked for an invalid "
        "BD_ADDR");
    return false;
  }
}

/*******************************************************************************
 *
 * Function         BTM_SetPinType
 *
 * Description      Set PIN type for the device.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_SetPinType(uint8_t pin_type, PIN_CODE pin_code, uint8_t pin_code_len) {
  log::verbose(
      "BTM_SetPinType: pin type {} [variable-0, fixed-1], code {}, length {}",
      pin_type, (char*)pin_code, pin_code_len);

  /* If device is not up security mode will be set as a part of startup */
  if ((btm_sec_cb.cfg.pin_type != pin_type) &&
      controller_get_interface()->get_is_ready()) {
    btsnd_hcic_write_pin_type(pin_type);
  }

  btm_sec_cb.cfg.pin_type = pin_type;
  btm_sec_cb.cfg.pin_code_len = pin_code_len;
  memcpy(btm_sec_cb.cfg.pin_code, pin_code, pin_code_len);
}

/*******************************************************************************
 *
 * Function         BTM_SetSecurityLevel
 *
 * Description      Register service security level with Security Manager
 *
 * Parameters:      is_originator - true if originating the connection
 *                  p_name      - Name of the service relevant only if
 *                                authorization will show this name to user.
 *                                Ignored if BT_MAX_SERVICE_NAME_LEN is 0.
 *                  service_id  - service ID for the service passed to
 *                                authorization callback
 *                  sec_level   - bit mask of the security features
 *                  psm         - L2CAP PSM
 *                  mx_proto_id - protocol ID of multiplexing proto below
 *                  mx_chan_id  - channel ID of multiplexing proto below
 *
 * Returns          true if registered OK, else false
 *
 ******************************************************************************/
bool BTM_SetSecurityLevel(bool is_originator, const char* p_name,
                          uint8_t service_id, uint16_t sec_level, uint16_t psm,
                          uint32_t mx_proto_id, uint32_t mx_chan_id) {
  return btm_sec_cb.AddService(is_originator, p_name, service_id, sec_level,
                               psm, mx_proto_id, mx_chan_id);
}

/*******************************************************************************
 *
 * Function         BTM_SecClrService
 *
 * Description      Removes specified service record(s) from the security
 *                  database. All service records with the specified name are
 *                  removed. Typically used only by devices with limited RAM so
 *                  that it can reuse an old security service record.
 *
 *                  Note: Unpredictable results may occur if a service is
 *                      cleared that is still in use by an application/profile.
 *
 * Parameters       Service ID - Id of the service to remove. '0' removes all
 *                          service records (except SDP).
 *
 * Returns          Number of records that were freed.
 *
 ******************************************************************************/
uint8_t BTM_SecClrService(uint8_t service_id) {
  return btm_sec_cb.RemoveServiceById(service_id);
}

/*******************************************************************************
 *
 * Function         BTM_SecClrServiceByPsm
 *
 * Description      Removes specified service record from the security database.
 *                  All service records with the specified psm are removed.
 *                  Typically used by L2CAP to free up the service record used
 *                  by dynamic PSM clients when the channel is closed.
 *                  The given psm must be a virtual psm.
 *
 * Parameters       Service ID - Id of the service to remove. '0' removes all
 *                          service records (except SDP).
 *
 * Returns          Number of records that were freed.
 *
 ******************************************************************************/
uint8_t BTM_SecClrServiceByPsm(uint16_t psm) {
  return btm_sec_cb.RemoveServiceByPsm(psm);
}

/*******************************************************************************
 *
 * Function         BTM_PINCodeReply
 *
 * Description      This function is called after Security Manager submitted
 *                  PIN code request to the UI.
 *
 * Parameters:      bd_addr      - Address of the device for which PIN was
 *                                 requested
 *                  res          - result of the operation BTM_SUCCESS
 *                                 if success
 *                  pin_len      - length in bytes of the PIN Code
 *                  p_pin        - pointer to array with the PIN Code
 *
 ******************************************************************************/
void BTM_PINCodeReply(const RawAddress& bd_addr, tBTM_STATUS res,
                      uint8_t pin_len, uint8_t* p_pin) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  log::verbose(
      "BTM_PINCodeReply(): PairState: {}   PairFlags: 0x{:02x}  PinLen:{}  "
      "Result:{}",
      tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
      btm_sec_cb.pairing_flags, pin_len, res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if (btm_sec_cb.pairing_state != BTM_PAIR_STATE_WAIT_LOCAL_PIN) {
    log::warn("BTM_PINCodeReply() - Wrong State: {}", btm_sec_cb.pairing_state);
    return;
  }

  if (bd_addr != btm_sec_cb.pairing_bda) {
    log::error("BTM_PINCodeReply() - Wrong BD Addr");
    return;
  }

  p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::error("BTM_PINCodeReply() - no dev CB");
    return;
  }

  if ((pin_len > PIN_CODE_LEN) || (pin_len == 0) || (p_pin == NULL))
    res = BTM_ILLEGAL_VALUE;

  if (res != BTM_SUCCESS) {
    /* if peer started dd OR we started dd and pre-fetch pin was not used send
     * negative reply */
    if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_PEER_STARTED_DD) ||
        ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) &&
         (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_DISC_WHEN_DONE))) {
      /* use BTM_PAIR_STATE_WAIT_AUTH_COMPLETE to report authentication failed
       * event */
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);
      acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);

      btsnd_hcic_pin_code_neg_reply(bd_addr);
    } else {
      p_dev_rec->sec_rec.security_required = BTM_SEC_NONE;
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
    }
    return;
  }
  p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LINK_KEY_AUTHED;
  p_dev_rec->sec_rec.pin_code_length = pin_len;
  if (pin_len >= 16) {
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_16_DIGIT_PIN_AUTHED;
  }

  if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) &&
      (p_dev_rec->hci_handle == HCI_INVALID_HANDLE) &&
      (!btm_sec_cb.security_mode_changed)) {
    /* This is start of the dedicated bonding if local device is 2.0 */
    btm_sec_cb.pin_code_len = pin_len;
    memcpy(btm_sec_cb.pin_code, p_pin, pin_len);

    btm_sec_cb.security_mode_changed = true;
    btsnd_hcic_write_auth_enable(true);

    acl_set_disconnect_reason(HCI_ERR_UNDEFINED);

    /* if we rejected incoming connection request, we have to wait
     * HCI_Connection_Complete event */
    /*  before originating  */
    if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_REJECTED_CONNECT) {
      log::warn(
          "BTM_PINCodeReply(): waiting HCI_Connection_Complete after rejected "
          "incoming connection");
      /* we change state little bit early so btm_sec_connected() will originate
       * connection */
      /*   when existing ACL link is down completely */
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_PIN_REQ);
    }
    /* if we already accepted incoming connection from pairing device */
    else if (p_dev_rec->sm4 & BTM_SM4_CONN_PEND) {
      log::warn(
          "BTM_PINCodeReply(): link is connecting so wait pin code request "
          "from peer");
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_PIN_REQ);
    } else if (btm_sec_dd_create_conn(p_dev_rec) != BTM_CMD_STARTED) {
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LINK_KEY_AUTHED;

      NotifyBondingChange(*p_dev_rec, HCI_ERR_AUTH_FAILURE);
    }
    return;
  }

  btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);
  acl_set_disconnect_reason(HCI_SUCCESS);

  btsnd_hcic_pin_code_req_reply(bd_addr, pin_len, p_pin);
}

/*******************************************************************************
 *
 * Function         btm_sec_bond_by_transport
 *
 * Description      this is the bond function that will start either SSP or SMP.
 *
 * Parameters:      bd_addr      - Address of the device to bond
 *                  addr_type    - type of the address
 *                  transport    - transport on which to create bond
 *
 *  Note: After 2.1 parameters are not used and preserved here not to change API
 ******************************************************************************/
tBTM_STATUS btm_sec_bond_by_transport(const RawAddress& bd_addr,
                                      tBLE_ADDR_TYPE addr_type,
                                      tBT_TRANSPORT transport) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  tBTM_STATUS status;
  log::info("Transport used {}, bd_addr={}", transport,
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  /* Other security process is in progress */
  if (btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) {
    log::error("BTM_SecBond: already busy in state: {}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state));
    return (BTM_WRONG_MODE);
  }

  p_dev_rec = btm_find_or_alloc_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::error("No memory to allocate new p_dev_rec");
    return (BTM_NO_RESOURCES);
  }

  if (!controller_get_interface()->get_is_ready()) {
    log::error("controller module is not ready");
    return (BTM_NO_RESOURCES);
  }

  log::verbose("before update sec_flags=0x{:x}", p_dev_rec->sec_rec.sec_flags);

  /* Finished if connection is active and already paired */
  if (((p_dev_rec->hci_handle != HCI_INVALID_HANDLE) &&
       transport == BT_TRANSPORT_BR_EDR &&
       (p_dev_rec->sec_rec.sec_flags & BTM_SEC_AUTHENTICATED)) ||
      ((p_dev_rec->ble_hci_handle != HCI_INVALID_HANDLE) &&
       transport == BT_TRANSPORT_LE &&
       (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_AUTHENTICATED))) {
    log::warn("BTM_SecBond -> Already Paired");
    return (BTM_SUCCESS);
  }

  /* Tell controller to get rid of the link key if it has one stored */
  if ((BTM_DeleteStoredLinkKey(&bd_addr, NULL)) != BTM_SUCCESS) {
    log::error("Failed to delete stored link keys");
    return (BTM_NO_RESOURCES);
  }

  btm_sec_cb.pairing_bda = bd_addr;

  btm_sec_cb.pairing_flags = BTM_PAIR_FLAGS_WE_STARTED_DD;

  p_dev_rec->sec_rec.security_required = BTM_SEC_OUT_AUTHENTICATE;
  p_dev_rec->is_originator = true;

  BTM_LogHistory(kBtmLogTag, bd_addr, "Bonding initiated",
                 bt_transport_text(transport));

  if (transport == BT_TRANSPORT_LE) {
    btm_ble_init_pseudo_addr(p_dev_rec, bd_addr);
    p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LE_MASK;

    if (SMP_Pair(bd_addr, addr_type) == SMP_STARTED) {
      btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_LE_ACTIVE;
      p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_AUTHENTICATING;
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);
      return BTM_CMD_STARTED;
    }

    btm_sec_cb.pairing_flags = 0;
    return (BTM_NO_RESOURCES);
  }

  p_dev_rec->sec_rec.sec_flags &=
      ~(BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED |
        BTM_SEC_ROLE_SWITCHED | BTM_SEC_LINK_KEY_AUTHED);

  log::verbose("after update sec_flags=0x{:x}", p_dev_rec->sec_rec.sec_flags);
  if (!bluetooth::shim::GetController()->SupportsSimplePairing()) {
    /* The special case when we authenticate keyboard.  Set pin type to fixed */
    /* It would be probably better to do it from the application, but it is */
    /* complicated */
    if (((p_dev_rec->dev_class[1] & BTM_COD_MAJOR_CLASS_MASK) ==
         BTM_COD_MAJOR_PERIPHERAL) &&
        (p_dev_rec->dev_class[2] & BTM_COD_MINOR_KEYBOARD) &&
        (btm_sec_cb.cfg.pin_type != HCI_PIN_TYPE_FIXED)) {
      btm_sec_cb.pin_type_changed = true;
      btsnd_hcic_write_pin_type(HCI_PIN_TYPE_FIXED);
    }
  }

  log::verbose("BTM_SecBond: Remote sm4: 0x{:x}  HCI Handle: 0x{:04x}",
               p_dev_rec->sm4, p_dev_rec->hci_handle);

#if (BTM_SEC_FORCE_RNR_FOR_DBOND == TRUE)
  p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_NAME_KNOWN;
#endif

  /* If connection already exists... */
  if (BTM_IsAclConnectionUpAndHandleValid(bd_addr, transport)) {
    btm_sec_wait_and_start_authentication(p_dev_rec);

    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_PIN_REQ);

    /* Mark lcb as bonding */
    l2cu_update_lcb_4_bonding(bd_addr, true);
    return (BTM_CMD_STARTED);
  }

  log::verbose("sec mode: {} sm4:x{:x}", btm_sec_cb.security_mode,
               p_dev_rec->sm4);
  if (!bluetooth::shim::GetController()->SupportsSimplePairing() ||
      (p_dev_rec->sm4 == BTM_SM4_KNOWN)) {
    if (btm_sec_check_prefetch_pin(p_dev_rec)) return (BTM_CMD_STARTED);
  }
  if ((btm_sec_cb.security_mode == BTM_SEC_MODE_SP ||
       btm_sec_cb.security_mode == BTM_SEC_MODE_SC) &&
      BTM_SEC_IS_SM4_UNKNOWN(p_dev_rec->sm4)) {
    /* local is 2.1 and peer is unknown */
    if ((p_dev_rec->sm4 & BTM_SM4_CONN_PEND) == 0) {
      /* we are not accepting connection request from peer
       * -> RNR (to learn if peer is 2.1)
       * RNR when no ACL causes HCI_RMT_HOST_SUP_FEAT_NOTIFY_EVT */
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_GET_REM_NAME);
      status = BTM_ReadRemoteDeviceName(bd_addr, NULL, BT_TRANSPORT_BR_EDR);
    } else {
      /* We are accepting connection request from peer */
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_PIN_REQ);
      status = BTM_CMD_STARTED;
    }
    log::verbose("State:{} sm4: 0x{:x} sec_state:{}",
                 tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
                 p_dev_rec->sm4, p_dev_rec->sec_rec.sec_state);
  } else {
    /* both local and peer are 2.1  */
    status = btm_sec_dd_create_conn(p_dev_rec);
  }

  if (status != BTM_CMD_STARTED) {
    log::error(
        "BTM_ReadRemoteDeviceName or btm_sec_dd_create_conn error: 0x{:x}",
        (int)status);
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
  }

  return status;
}

/*******************************************************************************
 *
 * Function         BTM_SecBond
 *
 * Description      This function is called to perform bonding with peer device.
 *                  If the connection is already up, but not secure, pairing
 *                  is attempted.  If already paired BTM_SUCCESS is returned.
 *
 * Parameters:      bd_addr      - Address of the device to bond
 *                  transport    - doing SSP over BR/EDR or SMP over LE
 *
 *  Note: After 2.1 parameters are not used and preserved here not to change API
 ******************************************************************************/
tBTM_STATUS BTM_SecBond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                        tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  if (transport == BT_TRANSPORT_AUTO) {
    if (addr_type == BLE_ADDR_PUBLIC) {
      transport =
          BTM_UseLeLink(bd_addr) ? BT_TRANSPORT_LE : BT_TRANSPORT_BR_EDR;
    } else {
      log::info("Forcing transport LE (was auto) because of the address type");
      transport = BT_TRANSPORT_LE;
    }
  }
  tBT_DEVICE_TYPE dev_type;

  BTM_ReadDevInfo(bd_addr, &dev_type, &addr_type);
  /* LE device, do SMP pairing */
  if ((transport == BT_TRANSPORT_LE && (dev_type & BT_DEVICE_TYPE_BLE) == 0) ||
      (transport == BT_TRANSPORT_BR_EDR &&
       (dev_type & BT_DEVICE_TYPE_BREDR) == 0)) {
    log::warn(
        "Can't start bonding - requested transport and transport we've seen "
        "device on don't match");
    return BTM_ILLEGAL_ACTION;
  }
  return btm_sec_bond_by_transport(bd_addr, addr_type, transport);
}

/*******************************************************************************
 *
 * Function         BTM_SecBondCancel
 *
 * Description      This function is called to cancel ongoing bonding process
 *                  with peer device.
 *
 * Parameters:      bd_addr      - Address of the peer device
 *                  transport    - false for BR/EDR link; true for LE link
 *
 ******************************************************************************/
tBTM_STATUS BTM_SecBondCancel(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  log::verbose("BTM_SecBondCancel()  State: {} flags:0x{:x}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               btm_sec_cb.pairing_flags);
  p_dev_rec = btm_find_dev(bd_addr);
  if (!p_dev_rec || btm_sec_cb.pairing_bda != bd_addr) {
    return BTM_UNKNOWN_ADDR;
  }

  if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_LE_ACTIVE) {
    if (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING) {
      log::verbose("Cancel LE pairing");
      if (SMP_PairCancel(bd_addr)) {
        return BTM_CMD_STARTED;
      }
    }
    return BTM_WRONG_MODE;
  }

  log::verbose("hci_handle:0x{:x} sec_state:{}", p_dev_rec->hci_handle,
               p_dev_rec->sec_rec.sec_state);
  if (BTM_PAIR_STATE_WAIT_LOCAL_PIN == btm_sec_cb.pairing_state &&
      BTM_PAIR_FLAGS_WE_STARTED_DD & btm_sec_cb.pairing_flags) {
    /* pre-fetching pin for dedicated bonding */
    btm_sec_bond_cancel_complete();
    return BTM_SUCCESS;
  }

  /* If this BDA is in a bonding procedure */
  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD)) {
    /* If the HCI link is up */
    if (p_dev_rec->hci_handle != HCI_INVALID_HANDLE) {
      /* If some other thread disconnecting, we do not send second command */
      if ((p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_DISCONNECTING) ||
          (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_DISCONNECTING_BOTH))
        return (BTM_CMD_STARTED);

      /* If the HCI link was set up by Bonding process */
      if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_DISC_WHEN_DONE)
        return btm_sec_send_hci_disconnect(
            p_dev_rec, HCI_ERR_PEER_USER, p_dev_rec->hci_handle,
            "stack::btm::btm_sec::BTM_SecBondCancel");
      else
        l2cu_update_lcb_4_bonding(bd_addr, false);

      return BTM_NOT_AUTHORIZED;
    } else /*HCI link is not up */
    {
      /* If the HCI link creation was started by Bonding process */
      if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_DISC_WHEN_DONE) {
        btsnd_hcic_create_conn_cancel(bd_addr);
        return BTM_CMD_STARTED;
      }
      if (btm_sec_cb.pairing_state == BTM_PAIR_STATE_GET_REM_NAME) {
        BTM_CancelRemoteDeviceName();
        btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_WE_CANCEL_DD;
        return BTM_CMD_STARTED;
      }
      return BTM_NOT_AUTHORIZED;
    }
  }

  return BTM_WRONG_MODE;
}

/*******************************************************************************
 *
 * Function         BTM_SecGetDeviceLinkKeyType
 *
 * Description      This function is called to obtain link key type for the
 *                  device.
 *                  it returns BTM_SUCCESS if link key is available, or
 *                  BTM_UNKNOWN_ADDR if Security Manager does not know about
 *                  the device or device record does not contain link key info
 *
 * Returns          BTM_LKEY_TYPE_IGNORE if link key is unknown, link type
 *                  otherwise.
 *
 ******************************************************************************/
tBTM_LINK_KEY_TYPE BTM_SecGetDeviceLinkKeyType(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);

  if ((p_dev_rec != NULL) &&
      (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN)) {
    return p_dev_rec->sec_rec.link_key_type;
  }
  return BTM_LKEY_TYPE_IGNORE;
}

/*******************************************************************************
 *
 * Function         BTM_SetEncryption
 *
 * Description      This function is called to ensure that connection is
 *                  encrypted.  Should be called only on an open connection.
 *                  Typically only needed for connections that first want to
 *                  bring up unencrypted links, then later encrypt them.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *                  transport     - Link transport
 *                  p_callback    - Pointer to callback function called after
 *                                  required procedures are completed. Can be
 *                                  set to NULL if status is not desired.
 *                  p_ref_data    - pointer to any data the caller wishes to
 *                                  receive in the callback function upon
 *                                  completion. can be set to NULL if not used.
 *                  sec_act       - LE security action, unused for BR/EDR
 *
 * Returns          BTM_SUCCESS   - already encrypted
 *                  BTM_PENDING   - command will be returned in the callback
 *                  BTM_WRONG_MODE- connection not up.
 *                  BTM_BUSY      - security procedures are currently active
 *                  BTM_MODE_UNSUPPORTED - if security manager not linked in.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetEncryption(const RawAddress& bd_addr,
                              tBT_TRANSPORT transport,
                              tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                              tBTM_BLE_SEC_ACT sec_act) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == nullptr) {
    log::error("Unable to set encryption for unknown device");
    return BTM_WRONG_MODE;
  }

  auto owned_bd_addr = base::Owned(new RawAddress(bd_addr));

  switch (transport) {
    case BT_TRANSPORT_BR_EDR:
      if (p_dev_rec->hci_handle == HCI_INVALID_HANDLE) {
        log::warn(
            "Security Manager: BTM_SetEncryption not connected peer:{} "
            "transport:{}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
            bt_transport_text(transport).c_str());
        if (p_callback) {
          do_in_main_thread(
              FROM_HERE, base::BindOnce(p_callback, std::move(owned_bd_addr),
                                        transport, p_ref_data, BTM_WRONG_MODE));
        }
        return BTM_WRONG_MODE;
      }
      if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_ENCRYPTED) {
        log::debug(
            "Security Manager: BTM_SetEncryption already encrypted peer:{} "
            "transport:{}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
            bt_transport_text(transport).c_str());
        if (p_callback) {
          do_in_main_thread(FROM_HERE,
                            base::BindOnce(p_callback, std::move(owned_bd_addr),
                                           transport, p_ref_data, BTM_SUCCESS));
        }
        return BTM_SUCCESS;
      }
      break;

    case BT_TRANSPORT_LE:
      if (p_dev_rec->ble_hci_handle == HCI_INVALID_HANDLE) {
        log::warn(
            "Security Manager: BTM_SetEncryption not connected peer:{} "
            "transport:{}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
            bt_transport_text(transport).c_str());
        if (p_callback) {
          do_in_main_thread(
              FROM_HERE, base::BindOnce(p_callback, std::move(owned_bd_addr),
                                        transport, p_ref_data, BTM_WRONG_MODE));
        }
        return BTM_WRONG_MODE;
      }
      if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_ENCRYPTED) {
        log::debug(
            "Security Manager: BTM_SetEncryption already encrypted peer:{} "
            "transport:{}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
            bt_transport_text(transport).c_str());
        if (p_callback) {
          do_in_main_thread(FROM_HERE,
                            base::BindOnce(p_callback, std::move(owned_bd_addr),
                                           transport, p_ref_data, BTM_SUCCESS));
        }
        return BTM_SUCCESS;
      }
      break;

    default:
      log::error("Unknown transport");
      break;
  }

  /* Enqueue security request if security is active */
  if (bluetooth::common::init_flags::encryption_in_busy_state_is_enabled()) {
    bool enqueue = false;
    switch (p_dev_rec->sec_rec.sec_state) {
      case BTM_SEC_STATE_AUTHENTICATING:
      case BTM_SEC_STATE_DISCONNECTING_BOTH:
        /* Applicable for both transports */
        enqueue = true;
        break;

      case BTM_SEC_STATE_ENCRYPTING:
      case BTM_SEC_STATE_DISCONNECTING:
        if (transport == BT_TRANSPORT_BR_EDR) {
          enqueue = true;
        }
        break;

      case BTM_SEC_STATE_LE_ENCRYPTING:
      case BTM_SEC_STATE_DISCONNECTING_BLE:
        if (transport == BT_TRANSPORT_LE) {
          enqueue = true;
        }
        break;

      default:
        if (p_dev_rec->sec_rec.p_callback != nullptr) {
          enqueue = true;
        }
        break;
    }

    if (enqueue) {
      log::warn("Security Manager: Enqueue request in state:{}",
                security_state_text(p_dev_rec->sec_rec.sec_state).c_str());
      btm_sec_queue_encrypt_request(bd_addr, transport, p_callback, p_ref_data,
                                    sec_act);
      return BTM_CMD_STARTED;
    }
  } else {
    if (p_dev_rec->sec_rec.p_callback ||
        (p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_IDLE)) {
      log::warn("Security Manager: BTM_SetEncryption busy, enqueue request");
      btm_sec_queue_encrypt_request(bd_addr, transport, p_callback, p_ref_data,
                                    sec_act);
      log::info("Queued start encryption");
      return BTM_CMD_STARTED;
    }
  }

  p_dev_rec->sec_rec.p_callback = p_callback;
  p_dev_rec->sec_rec.p_ref_data = p_ref_data;
  p_dev_rec->sec_rec.security_required |=
      (BTM_SEC_IN_AUTHENTICATE | BTM_SEC_IN_ENCRYPT);
  p_dev_rec->is_originator = false;

  log::debug(
      "Security Manager: BTM_SetEncryption classic_handle:0x{:04x} "
      "ble_handle:0x{:04x} state:{} flags:0x{:x} required:0x{:x} "
      "p_callback={:c}",
      p_dev_rec->hci_handle, p_dev_rec->ble_hci_handle,
      p_dev_rec->sec_rec.sec_state, p_dev_rec->sec_rec.sec_flags,
      p_dev_rec->sec_rec.security_required, (p_callback) ? 'T' : 'F');

  tBTM_STATUS rc = BTM_SUCCESS;
  switch (transport) {
    case BT_TRANSPORT_LE:
      if (BTM_IsAclConnectionUp(bd_addr, BT_TRANSPORT_LE)) {
        rc = btm_ble_set_encryption(bd_addr, sec_act,
                                    L2CA_GetBleConnRole(bd_addr));
      } else {
        rc = BTM_WRONG_MODE;
        log::warn("cannot call btm_ble_set_encryption, p is NULL");
      }
      break;

    case BT_TRANSPORT_BR_EDR:
      rc = btm_sec_execute_procedure(p_dev_rec);
      break;

    default:
      log::error("Unknown transport");
      break;
  }

  switch (rc) {
    case BTM_CMD_STARTED:
    case BTM_BUSY:
      break;

    default:
      if (p_callback) {
        log::debug("Executing encryption callback peer:{} transport:{}",
                   ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
                   bt_transport_text(transport).c_str());
        p_dev_rec->sec_rec.p_callback = nullptr;
        do_in_main_thread(
            FROM_HERE,
            base::BindOnce(p_callback, std::move(owned_bd_addr), transport,
                           p_dev_rec->sec_rec.p_ref_data, rc));
      }
      break;
  }
  return rc;
}

bool BTM_SecIsSecurityPending(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  return p_dev_rec &&
         (p_dev_rec->sec_rec.is_security_state_encrypting() ||
          p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING);
}

/*******************************************************************************
 * disconnect the ACL link, if it's not done yet.
 ******************************************************************************/
static tBTM_STATUS btm_sec_send_hci_disconnect(tBTM_SEC_DEV_REC* p_dev_rec,
                                               tHCI_STATUS reason,
                                               uint16_t conn_handle,
                                               std::string comment) {
  const tSECURITY_STATE old_state =
      static_cast<tSECURITY_STATE>(p_dev_rec->sec_rec.sec_state);
  const tBTM_STATUS status = BTM_CMD_STARTED;

  /* send HCI_Disconnect on a transport only once */
  switch (old_state) {
    case BTM_SEC_STATE_DISCONNECTING:
      if (conn_handle == p_dev_rec->hci_handle) {
        // Already sent classic disconnect
        return status;
      }
      // Prepare to send disconnect on le transport
      p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_DISCONNECTING_BOTH;
      break;

    case BTM_SEC_STATE_DISCONNECTING_BLE:
      if (conn_handle == p_dev_rec->ble_hci_handle) {
        // Already sent ble disconnect
        return status;
      }
      // Prepare to send disconnect on classic transport
      p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_DISCONNECTING_BOTH;
      break;

    case BTM_SEC_STATE_DISCONNECTING_BOTH:
      // Already sent disconnect on both transports
      return status;

    default:
      p_dev_rec->sec_rec.sec_state = (conn_handle == p_dev_rec->hci_handle)
                                         ? BTM_SEC_STATE_DISCONNECTING
                                         : BTM_SEC_STATE_DISCONNECTING_BLE;

      break;
  }

  log::debug("Send hci disconnect handle:0x{:04x} reason:{}", conn_handle,
             hci_reason_code_text(reason).c_str());
  acl_disconnect_after_role_switch(conn_handle, reason, comment);

  return status;
}

/*******************************************************************************
 *
 * Function         BTM_ConfirmReqReply
 *
 * Description      This function is called to confirm the numeric value for
 *                  Simple Pairing in response to BTM_SP_CFM_REQ_EVT
 *
 * Parameters:      res           - result of the operation BTM_SUCCESS if
 *                                  success
 *                  bd_addr       - Address of the peer device
 *
 ******************************************************************************/
void BTM_ConfirmReqReply(tBTM_STATUS res, const RawAddress& bd_addr) {
  log::verbose("BTM_ConfirmReqReply() State: {}  Res: {}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_WAIT_NUMERIC_CONFIRM) ||
      (btm_sec_cb.pairing_bda != bd_addr)) {
    log::warn(
        "Unexpected pairing confirm for {}, pairing_state: {}, pairing_bda: {}",
        ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
        ADDRESS_TO_LOGGABLE_CSTR(btm_sec_cb.pairing_bda));
    return;
  }

  BTM_LogHistory(kBtmLogTag, bd_addr, "Confirm reply",
                 base::StringPrintf("status:%s", btm_status_text(res).c_str()));

  btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);

  if ((res == BTM_SUCCESS) || (res == BTM_SUCCESS_NO_SECURITY)) {
    acl_set_disconnect_reason(HCI_SUCCESS);

    btsnd_hcic_user_conf_reply(bd_addr, true);
  } else {
    /* Report authentication failed event from state
     * BTM_PAIR_STATE_WAIT_AUTH_COMPLETE */
    acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);
    btsnd_hcic_user_conf_reply(bd_addr, false);
  }
}

/*******************************************************************************
 *
 * Function         BTM_PasskeyReqReply
 *
 * Description      This function is called to provide the passkey for
 *                  Simple Pairing in response to BTM_SP_KEY_REQ_EVT
 *
 * Parameters:      res     - result of the operation BTM_SUCCESS if success
 *                  bd_addr - Address of the peer device
 *                  passkey - numeric value in the range of
 *                  BTM_MIN_PASSKEY_VAL(0) -
 *                  BTM_MAX_PASSKEY_VAL(999999(0xF423F)).
 *
 ******************************************************************************/
void BTM_PasskeyReqReply(tBTM_STATUS res, const RawAddress& bd_addr,
                         uint32_t passkey) {
  log::verbose("BTM_PasskeyReqReply: State: {}  res:{}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               res);

  if ((btm_sec_cb.pairing_state == BTM_PAIR_STATE_IDLE) ||
      (btm_sec_cb.pairing_bda != bd_addr)) {
    return;
  }

  /* If timeout already expired or has been canceled, ignore the reply */
  if ((btm_sec_cb.pairing_state == BTM_PAIR_STATE_WAIT_AUTH_COMPLETE) &&
      (res != BTM_SUCCESS)) {
    tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
    if (p_dev_rec != NULL) {
      acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);

      if (p_dev_rec->hci_handle != HCI_INVALID_HANDLE)
        btm_sec_send_hci_disconnect(
            p_dev_rec, HCI_ERR_AUTH_FAILURE, p_dev_rec->hci_handle,
            "stack::btm::btm_sec::BTM_PasskeyReqReply Invalid handle");
      else
        BTM_SecBondCancel(bd_addr);

      p_dev_rec->sec_rec.sec_flags &=
          ~(BTM_SEC_LINK_KEY_AUTHED | BTM_SEC_LINK_KEY_KNOWN);

      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      return;
    }
  } else if (btm_sec_cb.pairing_state != BTM_PAIR_STATE_KEY_ENTRY)
    return;

  if (passkey > BTM_MAX_PASSKEY_VAL) res = BTM_ILLEGAL_VALUE;

  btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);

  if (res != BTM_SUCCESS) {
    /* use BTM_PAIR_STATE_WAIT_AUTH_COMPLETE to report authentication failed
     * event */
    acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);
    btsnd_hcic_user_passkey_neg_reply(bd_addr);
  } else {
    acl_set_disconnect_reason(HCI_SUCCESS);
    btsnd_hcic_user_passkey_reply(bd_addr, passkey);
  }
}

/*******************************************************************************
 *
 * Function         BTM_ReadLocalOobData
 *
 * Description      This function is called to read the local OOB data from
 *                  LM
 *
 ******************************************************************************/
void BTM_ReadLocalOobData(void) { btsnd_hcic_read_local_oob_data(); }

/*******************************************************************************
 *
 * Function         BTM_RemoteOobDataReply
 *
 * Description      This function is called to provide the remote OOB data for
 *                  Simple Pairing in response to BTM_SP_RMT_OOB_EVT
 *
 * Parameters:      bd_addr     - Address of the peer device
 *                  c           - simple pairing Hash C.
 *                  r           - simple pairing Randomizer  C.
 *
 ******************************************************************************/
void BTM_RemoteOobDataReply(tBTM_STATUS res, const RawAddress& bd_addr,
                            const Octet16& c, const Octet16& r) {
  log::verbose("State: {} res: {}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if (btm_sec_cb.pairing_state != BTM_PAIR_STATE_WAIT_LOCAL_OOB_RSP) return;

  btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);

  if (res != BTM_SUCCESS) {
    /* use BTM_PAIR_STATE_WAIT_AUTH_COMPLETE to report authentication failed
     * event */
    acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);
    btsnd_hcic_rem_oob_neg_reply(bd_addr);
  } else {
    acl_set_disconnect_reason(HCI_SUCCESS);
    btsnd_hcic_rem_oob_reply(bd_addr, c, r);
  }
}

/*******************************************************************************
 *
 * Function         BTM_BothEndsSupportSecureConnections
 *
 * Description      This function is called to check if both the local device
 *                  and the peer device specified by bd_addr support BR/EDR
 *                  Secure Connections.
 *
 * Parameters:      bd_addr - address of the peer
 *
 * Returns          true if BR/EDR Secure Connections are supported by both
 *                  local and the remote device, else false.
 *
 ******************************************************************************/
bool BTM_BothEndsSupportSecureConnections(const RawAddress& bd_addr) {
  return ((bluetooth::shim::GetController()->SupportsSecureConnections()) &&
          (BTM_PeerSupportsSecureConnections(bd_addr)));
}

/*******************************************************************************
 *
 * Function         BTM_PeerSupportsSecureConnections
 *
 * Description      This function is called to check if the peer supports
 *                  BR/EDR Secure Connections.
 *
 * Parameters:      bd_addr - address of the peer
 *
 * Returns          true if BR/EDR Secure Connections are supported by the peer,
 *                  else false.
 *
 ******************************************************************************/
bool BTM_PeerSupportsSecureConnections(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::warn("unknown BDA: {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return false;
  }

  return (p_dev_rec->SupportsSecureConnections());
}

/*******************************************************************************
 *
 * Function         BTM_GetPeerDeviceTypeFromFeatures
 *
 * Description      This function is called to retrieve the peer device type
 *                  by referencing the remote features.
 *
 * Parameters:      bd_addr - address of the peer
 *
 * Returns          BT_DEVICE_TYPE_DUMO if both BR/EDR and BLE transports are
 *                  supported by the peer,
 *                  BT_DEVICE_TYPE_BREDR if only BR/EDR transport is supported,
 *                  BT_DEVICE_TYPE_BLE if only BLE transport is supported.
 *
 ******************************************************************************/
tBT_DEVICE_TYPE BTM_GetPeerDeviceTypeFromFeatures(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == nullptr) {
    log::warn("Unknown BDA:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  } else {
    if (p_dev_rec->remote_supports_ble && p_dev_rec->remote_supports_bredr) {
      return BT_DEVICE_TYPE_DUMO;
    } else if (p_dev_rec->remote_supports_bredr) {
      return BT_DEVICE_TYPE_BREDR;
    } else if (p_dev_rec->remote_supports_ble) {
      return BT_DEVICE_TYPE_BLE;
    } else {
      log::warn("Device features does not support BR/EDR and BLE:{}",
                ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    }
  }
  return BT_DEVICE_TYPE_BREDR;
}

/*******************************************************************************
 *
 * Function         BTM_GetInitialSecurityMode
 *
 * Description      This function is called to retrieve the configured
 *                  security mode.
 *
 ******************************************************************************/
uint8_t BTM_GetSecurityMode() { return btm_sec_cb.security_mode; }

/************************************************************************
 *              I N T E R N A L     F U N C T I O N S
 ************************************************************************/
/*******************************************************************************
 *
 * Function         btm_sec_is_upgrade_possible
 *
 * Description      This function returns true if the existing link key
 *                  can be upgraded or if the link key does not exist.
 *
 * Returns          bool
 *
 ******************************************************************************/
static bool btm_sec_is_upgrade_possible(tBTM_SEC_DEV_REC* p_dev_rec,
                                        bool is_originator) {
  uint16_t mtm_check = is_originator ? BTM_SEC_OUT_MITM : BTM_SEC_IN_MITM;
  bool is_possible = true;

  if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN) {
    is_possible = false;
    /* Already have a link key to the connected peer. Is the link key secure
     *enough?
     ** Is a link key upgrade even possible?
     */
    if ((p_dev_rec->sec_rec.security_required & mtm_check) /* needs MITM */
        &&
        ((p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_UNAUTH_COMB) ||
         (p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_UNAUTH_COMB_P_256))
        /* has unauthenticated
        link key */
        && (p_dev_rec->sec_rec.rmt_io_caps <
            BTM_IO_CAP_MAX) /* a valid peer IO cap */
        && (btm_sec_io_map[p_dev_rec->sec_rec.rmt_io_caps]
                          [btm_sec_cb.devcb.loc_io_caps]))
    /* authenticated
    link key is possible */
    {
      /* upgrade is possible: check if the application wants the upgrade.
       * If the application is configured to use a global MITM flag,
       * it probably would not want to upgrade the link key based on the
       * security level database */
      is_possible = true;
    }
  }
  log::verbose("is_possible: {} sec_flags: 0x{:x}", is_possible,
               p_dev_rec->sec_rec.sec_flags);
  return is_possible;
}

/*******************************************************************************
 *
 * Function         btm_sec_check_upgrade
 *
 * Description      This function is called to check if the existing link key
 *                  needs to be upgraded.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_sec_check_upgrade(tBTM_SEC_DEV_REC* p_dev_rec,
                                  bool is_originator) {
  log::verbose("verify whether the link key should be upgraded");

  /* Only check if link key already exists */
  if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN)) return;

  if (btm_sec_is_upgrade_possible(p_dev_rec, is_originator)) {
    log::verbose("need upgrade!! sec_flags:0x{:x}",
                 p_dev_rec->sec_rec.sec_flags);
    /* if the application confirms the upgrade, set the upgrade bit */
    p_dev_rec->sm4 |= BTM_SM4_UPGRADE;

    /* Clear the link key known to go through authentication/pairing again */
    p_dev_rec->sec_rec.sec_flags &=
        ~(BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_LINK_KEY_AUTHED);
    p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_AUTHENTICATED;
    log::verbose("sec_flags:0x{:x}", p_dev_rec->sec_rec.sec_flags);
  }
}

tBTM_STATUS btm_sec_l2cap_access_req_by_requirement(
    const RawAddress& bd_addr, uint16_t security_required, bool is_originator,
    tBTM_SEC_CALLBACK* p_callback, void* p_ref_data) {
  log::debug(
      "Checking l2cap access requirements peer:{} security:0x{:x} "
      "is_initiator:{}",
      ADDRESS_TO_LOGGABLE_CSTR(bd_addr), security_required,
      logbool(is_originator).c_str());

  tBTM_STATUS rc = BTM_SUCCESS;
  bool chk_acp_auth_done = false;
  /* should check PSM range in LE connection oriented L2CAP connection */
  constexpr tBT_TRANSPORT transport = BT_TRANSPORT_BR_EDR;

  /* Find or get oldest record */
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(bd_addr);

  p_dev_rec->hci_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_BR_EDR);

  if ((!is_originator) && (security_required & BTM_SEC_MODE4_LEVEL4)) {
    bool local_supports_sc =
        bluetooth::shim::GetController()->SupportsSecureConnections();
    /* acceptor receives L2CAP Channel Connect Request for Secure Connections
     * Only service */
    if (!local_supports_sc || !p_dev_rec->SupportsSecureConnections()) {
      log::warn(
          "Policy requires mode 4 level 4, but local_support_for_sc={}, "
          "rmt_support_for_sc={}, failing connection",
          local_supports_sc,
          logbool(p_dev_rec->SupportsSecureConnections()).c_str());
      if (p_callback) {
        (*p_callback)(&bd_addr, transport, (void*)p_ref_data,
                      BTM_MODE4_LEVEL4_NOT_SUPPORTED);
      }

      return (BTM_MODE4_LEVEL4_NOT_SUPPORTED);
    }
  }

  /* there are some devices (moto KRZR) which connects to several services at
   * the same time */
  /* we will process one after another */
  if ((p_dev_rec->sec_rec.p_callback) ||
      (btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE)) {
    log::debug("security_flags:x{:x}, sec_flags:x{:x}", security_required,
               p_dev_rec->sec_rec.sec_flags);
    rc = BTM_CMD_STARTED;
    if ((btm_sec_cb.security_mode == BTM_SEC_MODE_SERVICE) ||
        (BTM_SM4_KNOWN == p_dev_rec->sm4) ||
        (BTM_SEC_IS_SM4(p_dev_rec->sm4) &&
         (!btm_sec_is_upgrade_possible(p_dev_rec, is_originator)))) {
      /* legacy mode - local is legacy or local is lisbon/peer is legacy
       * or SM4 with no possibility of link key upgrade */
      if (is_originator) {
        if (((security_required & BTM_SEC_OUT_FLAGS) == 0) ||
            ((((security_required & BTM_SEC_OUT_FLAGS) ==
               BTM_SEC_OUT_AUTHENTICATE) &&
              btm_dev_authenticated(p_dev_rec))) ||
            ((((security_required & BTM_SEC_OUT_FLAGS) ==
               (BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_OUT_ENCRYPT)) &&
              btm_dev_encrypted(p_dev_rec)))) {
          rc = BTM_SUCCESS;
        }
      } else {
        if (((security_required & BTM_SEC_IN_FLAGS) == 0) ||
            (((security_required & BTM_SEC_IN_FLAGS) ==
              BTM_SEC_IN_AUTHENTICATE) &&
             btm_dev_authenticated(p_dev_rec)) ||
            (((security_required & BTM_SEC_IN_FLAGS) ==
              (BTM_SEC_IN_AUTHENTICATE | BTM_SEC_IN_ENCRYPT)) &&
             btm_dev_encrypted(p_dev_rec))) {
          // Check for 16 digits (or MITM)
          if (((security_required & BTM_SEC_IN_MIN_16_DIGIT_PIN) == 0) ||
              (((security_required & BTM_SEC_IN_MIN_16_DIGIT_PIN) ==
                BTM_SEC_IN_MIN_16_DIGIT_PIN) &&
               btm_dev_16_digit_authenticated(p_dev_rec))) {
            rc = BTM_SUCCESS;
          }
        }
      }

      if ((rc == BTM_SUCCESS) && (security_required & BTM_SEC_MODE4_LEVEL4) &&
          (p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
        rc = BTM_CMD_STARTED;
      }

      if (rc == BTM_SUCCESS) {
        if (access_secure_service_from_temp_bond(p_dev_rec, is_originator, security_required)) {
          log::error(
              "Trying to access a secure service from a temp bonding, "
              "rejecting");
          rc = BTM_FAILED_ON_SECURITY;
        }

        if (p_callback)
          (*p_callback)(&bd_addr, transport, (void*)p_ref_data, rc);
        return rc;
      }
    }

    btm_sec_cb.sec_req_pending = true;
    return (BTM_CMD_STARTED);
  }

  /* Save the security requirements in case a pairing is needed */
  p_dev_rec->sec_rec.required_security_flags_for_pairing = security_required;

  /* Modify security_required in btm_sec_l2cap_access_req for Lisbon */
  if (btm_sec_cb.security_mode == BTM_SEC_MODE_SP ||
      btm_sec_cb.security_mode == BTM_SEC_MODE_SC) {
    if (BTM_SEC_IS_SM4(p_dev_rec->sm4)) {
      if (is_originator) {
        /* SM4 to SM4 -> always encrypt */
        security_required |= BTM_SEC_OUT_ENCRYPT;
      } else /* acceptor */
      {
        /* SM4 to SM4: the acceptor needs to make sure the authentication is
         * already done */
        chk_acp_auth_done = true;
        /* SM4 to SM4 -> always encrypt */
        security_required |= BTM_SEC_IN_ENCRYPT;
      }
    } else if (!(BTM_SM4_KNOWN & p_dev_rec->sm4)) {
      /* the remote features are not known yet */
      log::debug(
          "Remote features have not yet been received sec_flags:0x{:02x} {}",
          p_dev_rec->sec_rec.sec_flags,
          (is_originator) ? "initiator" : "acceptor");

      p_dev_rec->sm4 |= BTM_SM4_REQ_PEND;
      return (BTM_CMD_STARTED);
    }
  }

  log::verbose("sm4:0x{:x}, sec_flags:0x{:x}, security_required:0x{:x} chk:{}",
               p_dev_rec->sm4, p_dev_rec->sec_rec.sec_flags, security_required,
               chk_acp_auth_done);

  p_dev_rec->sec_rec.security_required = security_required;
  p_dev_rec->sec_rec.p_ref_data = p_ref_data;
  p_dev_rec->is_originator = is_originator;

  if (chk_acp_auth_done) {
    log::verbose(
        "(SM4 to SM4) btm_sec_l2cap_access_req rspd. authenticated: x{:x}, "
        "enc: x{:x}",
        (p_dev_rec->sec_rec.sec_flags & BTM_SEC_AUTHENTICATED),
        (p_dev_rec->sec_rec.sec_flags & BTM_SEC_ENCRYPTED));
    /* SM4, but we do not know for sure which level of security we need.
     * as long as we have a link key, it's OK */
    if ((0 == (p_dev_rec->sec_rec.sec_flags & BTM_SEC_AUTHENTICATED)) ||
        (0 == (p_dev_rec->sec_rec.sec_flags & BTM_SEC_ENCRYPTED))) {
      rc = BTM_DELAY_CHECK;
      /*
      2046 may report HCI_Encryption_Change and L2C Connection Request out of
      sequence
      because of data path issues. Delay this disconnect a little bit
      */
      log::info(
          "peer should have initiated security process by now (SM4 to SM4)");
      p_dev_rec->sec_rec.p_callback = p_callback;
      p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_DELAY_FOR_ENC;
      (*p_callback)(&bd_addr, transport, p_ref_data, rc);

      return BTM_SUCCESS;
    }
  }

  p_dev_rec->sec_rec.p_callback = p_callback;

  if (BTM_SEC_IS_SM4(p_dev_rec->sm4)) {
    if ((p_dev_rec->sec_rec.security_required & BTM_SEC_MODE4_LEVEL4) &&
        (p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
      /* BTM_LKEY_TYPE_AUTH_COMB_P_256 is the only acceptable key in this case
       */
      if ((p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN) != 0) {
        p_dev_rec->sm4 |= BTM_SM4_UPGRADE;
      }
      p_dev_rec->sec_rec.sec_flags &=
          ~(BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_LINK_KEY_AUTHED |
            BTM_SEC_AUTHENTICATED);
      log::verbose("sec_flags:0x{:x}", p_dev_rec->sec_rec.sec_flags);
    } else {
      /* If we already have a link key to the connected peer, is it secure
       * enough? */
      btm_sec_check_upgrade(p_dev_rec, is_originator);
    }
  }

  rc = btm_sec_execute_procedure(p_dev_rec);
  if (rc != BTM_CMD_STARTED) {
    log::verbose("p_dev_rec={}, clearing callback. old p_callback={}",
                 fmt::ptr(p_dev_rec), fmt::ptr(p_dev_rec->sec_rec.p_callback));
    p_dev_rec->sec_rec.p_callback = NULL;
    (*p_callback)(&bd_addr, transport, p_dev_rec->sec_rec.p_ref_data, rc);
  }

  return (rc);
}

/*******************************************************************************
 *
 * Function         btm_sec_l2cap_access_req
 *
 * Description      This function is called by the L2CAP to grant permission to
 *                  establish L2CAP connection to or from the peer device.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *                  psm           - L2CAP PSM
 *                  is_originator - true if protocol above L2CAP originates
 *                                  connection
 *                  p_callback    - Pointer to callback function called if
 *                                  this function returns PENDING after required
 *                                  procedures are complete. MUST NOT BE NULL.
 *
 * Returns          tBTM_STATUS
 *
 ******************************************************************************/
tBTM_STATUS btm_sec_l2cap_access_req(const RawAddress& bd_addr, uint16_t psm,
                                     bool is_originator,
                                     tBTM_SEC_CALLBACK* p_callback,
                                     void* p_ref_data) {
  // should check PSM range in LE connection oriented L2CAP connection
  constexpr tBT_TRANSPORT transport = BT_TRANSPORT_BR_EDR;

  log::debug("is_originator:{}, psm=0x{:04x}", is_originator, psm);

  // Find the service record for the PSM
  tBTM_SEC_SERV_REC* p_serv_rec =
      btm_sec_cb.find_first_serv_rec(is_originator, psm);

  // If there is no application registered with this PSM do not allow connection
  if (!p_serv_rec) {
    log::warn("PSM: 0x{:04x} no application registered", psm);
    (*p_callback)(&bd_addr, transport, p_ref_data, BTM_MODE_UNSUPPORTED);
    return (BTM_MODE_UNSUPPORTED);
  }

  /* Services level0 by default have no security */
  if (psm == BT_PSM_SDP) {
    log::debug("No security required for SDP");
    (*p_callback)(&bd_addr, transport, p_ref_data, BTM_SUCCESS_NO_SECURITY);
    return (BTM_SUCCESS);
  }

  uint16_t security_required;
  if (btm_sec_cb.security_mode == BTM_SEC_MODE_SC) {
    security_required = btm_sec_set_serv_level4_flags(
        p_serv_rec->security_flags, is_originator);
  } else {
    security_required = p_serv_rec->security_flags;
  }

  return btm_sec_l2cap_access_req_by_requirement(
      bd_addr, security_required, is_originator, p_callback, p_ref_data);
}

/*******************************************************************************
 *
 * Function         btm_sec_mx_access_request
 *
 * Description      This function is called by all Multiplexing Protocols during
 *                  establishing connection to or from peer device to grant
 *                  permission to establish application connection.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *                  psm           - L2CAP PSM
 *                  is_originator - true if protocol above L2CAP originates
 *                                  connection
 *                  mx_proto_id   - protocol ID of the multiplexer
 *                  mx_chan_id    - multiplexer channel to reach application
 *                  p_callback    - Pointer to callback function called if
 *                                  this function returns PENDING after required
 *                                  procedures are completed
 *                  p_ref_data    - Pointer to any reference data needed by the
 *                                  the callback function.
 *
 * Returns          BTM_CMD_STARTED
 *
 ******************************************************************************/
tBTM_STATUS btm_sec_mx_access_request(const RawAddress& bd_addr,
                                      bool is_originator,
                                      uint16_t security_required,
                                      tBTM_SEC_CALLBACK* p_callback,
                                      void* p_ref_data) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  tBTM_STATUS rc;
  bool transport = false; /* should check PSM range in LE connection oriented
                             L2CAP connection */
  log::debug("Multiplex access request device:{}",
             ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  /* Find or get oldest record */
  p_dev_rec = btm_find_or_alloc_dev(bd_addr);

  /* there are some devices (moto phone) which connects to several services at
   * the same time */
  /* we will process one after another */
  if ((p_dev_rec->sec_rec.p_callback) ||
      (btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE)) {
    log::debug("Pairing in progress pairing_state:{}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state));

    rc = BTM_CMD_STARTED;

    if ((btm_sec_cb.security_mode == BTM_SEC_MODE_SERVICE) ||
        (BTM_SM4_KNOWN == p_dev_rec->sm4) ||
        (BTM_SEC_IS_SM4(p_dev_rec->sm4) &&
         (!btm_sec_is_upgrade_possible(p_dev_rec, is_originator)))) {
      /* legacy mode - local is legacy or local is lisbon/peer is legacy
       * or SM4 with no possibility of link key upgrade */
      if (is_originator) {
        if (((security_required & BTM_SEC_OUT_FLAGS) == 0) ||
            ((((security_required & BTM_SEC_OUT_FLAGS) ==
               BTM_SEC_OUT_AUTHENTICATE) &&
              btm_dev_authenticated(p_dev_rec))) ||
            ((((security_required & BTM_SEC_OUT_FLAGS) ==
               (BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_OUT_ENCRYPT)) &&
              btm_dev_encrypted(p_dev_rec)))) {
          rc = BTM_SUCCESS;
        }
      } else {
        if (((security_required & BTM_SEC_IN_FLAGS) == 0) ||
            ((((security_required & BTM_SEC_IN_FLAGS) ==
               BTM_SEC_IN_AUTHENTICATE) &&
              btm_dev_authenticated(p_dev_rec))) ||
            ((((security_required & BTM_SEC_IN_FLAGS) ==
               (BTM_SEC_IN_AUTHENTICATE | BTM_SEC_IN_ENCRYPT)) &&
              btm_dev_encrypted(p_dev_rec)))) {
          // Check for 16 digits (or MITM)
          if (((security_required & BTM_SEC_IN_MIN_16_DIGIT_PIN) == 0) ||
              (((security_required & BTM_SEC_IN_MIN_16_DIGIT_PIN) ==
                BTM_SEC_IN_MIN_16_DIGIT_PIN) &&
               btm_dev_16_digit_authenticated(p_dev_rec))) {
            rc = BTM_SUCCESS;
          }
        }
      }
      if ((rc == BTM_SUCCESS) && (security_required & BTM_SEC_MODE4_LEVEL4) &&
          (p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
        rc = BTM_CMD_STARTED;
      }
    }

    /* the new security request */
    if (p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_IDLE) {
      log::debug("A pending security procedure in progress");
      rc = BTM_CMD_STARTED;
    }
    if (rc == BTM_CMD_STARTED) {
      btm_sec_queue_mx_request(bd_addr, BT_PSM_RFCOMM, is_originator,
                               security_required, p_callback, p_ref_data);
    } else /* rc == BTM_SUCCESS */
    {
      if (access_secure_service_from_temp_bond(p_dev_rec,
          is_originator, security_required)) {
        log::error(
            "Trying to access a secure rfcomm service from a temp bonding, "
            "rejecting");
        rc = BTM_FAILED_ON_SECURITY;
      }
      if (p_callback) {
        log::debug("Notifying client that security access has been granted");
        (*p_callback)(&bd_addr, transport, p_ref_data, rc);
      }
    }
    return rc;
  }

  if ((!is_originator) && ((security_required & BTM_SEC_MODE4_LEVEL4) ||
                           (btm_sec_cb.security_mode == BTM_SEC_MODE_SC))) {
    bool local_supports_sc =
        bluetooth::shim::GetController()->SupportsSecureConnections();
    /* acceptor receives service connection establishment Request for */
    /* Secure Connections Only service */
    if (!(local_supports_sc) || !(p_dev_rec->SupportsSecureConnections())) {
      log::debug(
          "Secure Connection only mode unsupported local_SC_support:{} "
          "remote_SC_support:{}",
          logbool(local_supports_sc).c_str(),
          logbool(p_dev_rec->SupportsSecureConnections()).c_str());
      if (p_callback)
        (*p_callback)(&bd_addr, transport, (void*)p_ref_data,
                      BTM_MODE4_LEVEL4_NOT_SUPPORTED);

      return (BTM_MODE4_LEVEL4_NOT_SUPPORTED);
    }
  }

  if (security_required & BTM_SEC_OUT_AUTHENTICATE) {
    security_required |= BTM_SEC_OUT_MITM;
  }
  if (security_required & BTM_SEC_IN_AUTHENTICATE) {
    security_required |= BTM_SEC_IN_MITM;
  }

  p_dev_rec->sec_rec.required_security_flags_for_pairing = security_required;
  p_dev_rec->sec_rec.security_required = security_required;

  if (btm_sec_cb.security_mode == BTM_SEC_MODE_SP ||
      btm_sec_cb.security_mode == BTM_SEC_MODE_SC) {
    if (BTM_SEC_IS_SM4(p_dev_rec->sm4)) {
      if ((p_dev_rec->sec_rec.security_required & BTM_SEC_MODE4_LEVEL4) &&
          (p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
        /* BTM_LKEY_TYPE_AUTH_COMB_P_256 is the only acceptable key in this case
         */
        if ((p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN) != 0) {
          p_dev_rec->sm4 |= BTM_SM4_UPGRADE;
        }

        p_dev_rec->sec_rec.sec_flags &=
            ~(BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_LINK_KEY_AUTHED |
              BTM_SEC_AUTHENTICATED);
        log::verbose("sec_flags:0x{:x}", p_dev_rec->sec_rec.sec_flags);
      } else {
        log::debug("Already have link key; checking if link key is sufficient");
        btm_sec_check_upgrade(p_dev_rec, is_originator);
      }
    }
  }

  p_dev_rec->is_originator = is_originator;
  p_dev_rec->sec_rec.p_callback = p_callback;
  p_dev_rec->sec_rec.p_ref_data = p_ref_data;

  rc = btm_sec_execute_procedure(p_dev_rec);
  log::debug("Started security procedure peer:{} btm_status:{}",
             ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->RemoteAddress()),
             btm_status_text(rc).c_str());
  if (rc != BTM_CMD_STARTED) {
    if (p_callback) {
      p_dev_rec->sec_rec.p_callback = NULL;
      (*p_callback)(&bd_addr, transport, p_ref_data, rc);
    }
  }

  return rc;
}

/*******************************************************************************
 *
 * Function         btm_sec_conn_req
 *
 * Description      This function is when the peer device is requesting
 *                  connection
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_conn_req(const RawAddress& bda, const DEV_CLASS dc) {
  tBTM_SEC_DEV_REC* p_dev_rec = nullptr;

  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) &&
      (btm_sec_cb.pairing_bda == bda)) {
    log::verbose(
        "Security Manager: reject connect request from bonding device");

    /* incoming connection from bonding device is rejected */
    btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_REJECTED_CONNECT;
    btsnd_hcic_reject_conn(bda, HCI_ERR_HOST_REJECT_DEVICE);
    return;
  }

  /* Host is not interested or approved connection.  Save BDA and DC and */
  /* pass request to L2CAP */
  btm_sec_cb.connecting_bda = bda;
  btm_sec_cb.connecting_dc = dc;

  p_dev_rec = btm_find_or_alloc_dev(bda);
  p_dev_rec->sm4 |= BTM_SM4_CONN_PEND;
}

/*******************************************************************************
 *
 * Function         btm_sec_bond_cancel_complete
 *
 * Description      This function is called to report bond cancel complete
 *                  event.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_sec_bond_cancel_complete(void) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_DISC_WHEN_DONE) ||
      (BTM_PAIR_STATE_WAIT_LOCAL_PIN == btm_sec_cb.pairing_state &&
       BTM_PAIR_FLAGS_WE_STARTED_DD & btm_sec_cb.pairing_flags) ||
      (btm_sec_cb.pairing_state == BTM_PAIR_STATE_GET_REM_NAME &&
       BTM_PAIR_FLAGS_WE_CANCEL_DD & btm_sec_cb.pairing_flags)) {
    /* for dedicated bonding in legacy mode, authentication happens at "link
     * level"
     * btm_sec_connected is called with failed status.
     * In theory, the code that handles is_pairing_device/true should clean out
     * security related code.
     * However, this function may clean out the security related flags and
     * btm_sec_connected would not know
     * this function also needs to do proper clean up.
     */
    p_dev_rec = btm_find_dev(btm_sec_cb.pairing_bda);
    if (p_dev_rec != NULL) p_dev_rec->sec_rec.security_required = BTM_SEC_NONE;
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);

    /* Notify application that the cancel succeeded */
    if (btm_sec_cb.api.p_bond_cancel_cmpl_callback)
      btm_sec_cb.api.p_bond_cancel_cmpl_callback(BTM_SUCCESS);
  }
}

/*******************************************************************************
 *
 * Function         btm_create_conn_cancel_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the create connection cancel
 *                  command.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_create_conn_cancel_complete(uint8_t status, const RawAddress bd_addr) {
  log::verbose("btm_create_conn_cancel_complete(): in State: {}  status:{}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               status);
  log_link_layer_connection_event(
      &bd_addr, bluetooth::common::kUnknownConnectionHandle,
      android::bluetooth::DIRECTION_OUTGOING, android::bluetooth::LINK_TYPE_ACL,
      android::bluetooth::hci::CMD_CREATE_CONNECTION_CANCEL,
      android::bluetooth::hci::EVT_COMMAND_COMPLETE,
      android::bluetooth::hci::BLE_EVT_UNKNOWN, status,
      android::bluetooth::hci::STATUS_UNKNOWN);

  /* if the create conn cancel cmd was issued by the bond cancel,
  ** the application needs to be notified that bond cancel succeeded
  */
  switch (status) {
    case HCI_SUCCESS:
      btm_sec_bond_cancel_complete();
      break;
    case HCI_ERR_CONNECTION_EXISTS:
    case HCI_ERR_NO_CONNECTION:
    default:
      /* Notify application of the error */
      if (btm_sec_cb.api.p_bond_cancel_cmpl_callback)
        btm_sec_cb.api.p_bond_cancel_cmpl_callback(BTM_ERR_PROCESSING);
      break;
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_check_pending_reqs
 *
 * Description      This function is called at the end of the security procedure
 *                  to let L2CAP and RFCOMM know to re-submit any pending
 *                  requests
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_check_pending_reqs(void) {
  if (btm_sec_cb.pairing_state == BTM_PAIR_STATE_IDLE) {
    /* First, resubmit L2CAP requests */
    if (btm_sec_cb.sec_req_pending) {
      btm_sec_cb.sec_req_pending = false;
      l2cu_resubmit_pending_sec_req(nullptr);
    }

    /* Now, re-submit anything in the mux queue */
    fixed_queue_t* bq = btm_sec_cb.sec_pending_q;

    btm_sec_cb.sec_pending_q = fixed_queue_new(SIZE_MAX);

    tBTM_SEC_QUEUE_ENTRY* p_e;
    while ((p_e = (tBTM_SEC_QUEUE_ENTRY*)fixed_queue_try_dequeue(bq)) != NULL) {
      /* Check that the ACL is still up before starting security procedures */
      if (BTM_IsAclConnectionUp(p_e->bd_addr, p_e->transport)) {
        if (p_e->psm != 0) {
          log::verbose("PSM:0x{:04x} Is_Orig:{}", p_e->psm, p_e->is_orig);

          btm_sec_mx_access_request(p_e->bd_addr, p_e->is_orig,
                                    p_e->rfcomm_security_requirement,
                                    p_e->p_callback, p_e->p_ref_data);
        } else {
          BTM_SetEncryption(p_e->bd_addr, p_e->transport, p_e->p_callback,
                            p_e->p_ref_data, p_e->sec_act);
        }
      }

      osi_free(p_e);
    }
    fixed_queue_free(bq, NULL);
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_dev_reset
 *
 * Description      This function should be called after device reset
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_dev_reset(void) {
  ASSERT_LOG(bluetooth::shim::GetController()->SupportsSimplePairing(),
             "only controllers with SSP is supported");

  /* set the default IO capabilities */
  btm_sec_cb.devcb.loc_io_caps = btif_storage_get_local_io_caps();
  /* add mx service to use no security */
  BTM_SetSecurityLevel(false, "RFC_MUX", BTM_SEC_SERVICE_RFC_MUX,
                       BTM_SEC_NONE, BT_PSM_RFCOMM, BTM_SEC_PROTO_RFCOMM, 0);
  BTM_SetSecurityLevel(true, "RFC_MUX", BTM_SEC_SERVICE_RFC_MUX, BTM_SEC_NONE,
                       BT_PSM_RFCOMM, BTM_SEC_PROTO_RFCOMM, 0);
  log::verbose("btm_sec_dev_reset sec mode: {}", btm_sec_cb.security_mode);
}

/*******************************************************************************
 *
 * Function         btm_sec_abort_access_req
 *
 * Description      This function is called by the L2CAP or RFCOMM to abort
 *                  the pending operation.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_abort_access_req(const RawAddress& bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);

  if (!p_dev_rec) return;

  if ((p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_AUTHORIZING) &&
      (p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_AUTHENTICATING))
    return;

  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;

  log::verbose("clearing callback. p_dev_rec={}, p_callback={}",
               fmt::ptr(p_dev_rec), fmt::ptr(p_dev_rec->sec_rec.p_callback));
  p_dev_rec->sec_rec.p_callback = NULL;
}

/*******************************************************************************
 *
 * Function         btm_sec_dd_create_conn
 *
 * Description      This function is called to create an ACL connection for
 *                  the dedicated bonding process
 *
 * Returns          BTM_SUCCESS if an ACL connection is already up
 *                  BTM_CMD_STARTED if the ACL connection has been requested
 *                  BTM_NO_RESOURCES if failed to start the ACL connection
 *
 ******************************************************************************/
static tBTM_STATUS btm_sec_dd_create_conn(tBTM_SEC_DEV_REC* p_dev_rec) {
  tBTM_STATUS status = l2cu_ConnectAclForSecurity(p_dev_rec->bd_addr);
  if (status == BTM_CMD_STARTED) {
    /* If already connected, start pending security procedure */
    if (BTM_IsAclConnectionUp(p_dev_rec->bd_addr, BT_TRANSPORT_BR_EDR)) {
      return BTM_SUCCESS;
    }
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_PIN_REQ);
    return BTM_CMD_STARTED;
  } else if (status == BTM_NO_RESOURCES) {
    return BTM_NO_RESOURCES;
  }

  /* set up the control block to indicated dedicated bonding */
  btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_DISC_WHEN_DONE;

  log::info("Security Manager: {}",
            ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));

  btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_PIN_REQ);

  return (BTM_CMD_STARTED);
}

static void call_registered_rmt_name_callbacks(const RawAddress* p_bd_addr,
                                               const DEV_CLASS& dev_class,
                                               uint8_t* p_bd_name,
                                               tHCI_STATUS status) {
  int i;

  if (p_bd_addr == nullptr) {
    // TODO Still need to send status back to get SDP state machine
    // running
    log::error("Unable to issue callback with unknown address status:{}",
               hci_status_code_text(status).c_str());
    return;
  }

  if (p_bd_name == nullptr) {
    p_bd_name = (uint8_t*)kBtmBdNameEmpty;
  }

  /* Notify all clients waiting for name to be resolved even if not found so
   * clients can continue */
  for (i = 0; i < BTM_SEC_MAX_RMT_NAME_CALLBACKS; i++) {
    if (btm_cb.p_rmt_name_callback[i]) {
      (*btm_cb.p_rmt_name_callback[i])(*p_bd_addr, dev_class, p_bd_name);
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_rmt_name_request_complete
 *
 * Description      This function is called when remote name was obtained from
 *                  the peer device
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_rmt_name_request_complete(const RawAddress* p_bd_addr,
                                       const uint8_t* p_bd_name,
                                       tHCI_STATUS status) {
  tBTM_SEC_DEV_REC* p_dev_rec = nullptr;
  uint8_t old_sec_state;

  log::info("btm_sec_rmt_name_request_complete for {}",
            p_bd_addr ? ADDRESS_TO_LOGGABLE_CSTR(*p_bd_addr) : "null");

  if ((!p_bd_addr && !BTM_IsAclConnectionUp(btm_sec_cb.connecting_bda,
                                            BT_TRANSPORT_BR_EDR)) ||
      (p_bd_addr && !BTM_IsAclConnectionUp(*p_bd_addr, BT_TRANSPORT_BR_EDR))) {
    log::warn(
        "Remote read request complete with no underlying link connection");
  }

  /* If remote name request failed, p_bd_addr is null and we need to search */
  /* based on state assuming that we are doing 1 at a time */
  if (p_bd_addr)
    p_dev_rec = btm_find_dev(*p_bd_addr);
  else {
    log::info(
        "Remote read request complete with no address so searching device "
        "database");
    p_dev_rec = btm_sec_find_dev_by_sec_state(BTM_SEC_STATE_GETTING_NAME);
    if (p_dev_rec) {
      p_bd_addr = &p_dev_rec->bd_addr;
    }
  }

  if (!p_bd_name) p_bd_name = (const uint8_t*)"";

  if (p_dev_rec == nullptr) {
    log::debug(
        "Remote read request complete for unknown device pairing_state:{} "
        "status:{} name:{}",
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
        hci_status_code_text(status).c_str(),
        reinterpret_cast<char const*>(p_bd_name));

    call_registered_rmt_name_callbacks(p_bd_addr, kDevClassEmpty, nullptr,
                                       status);
    return;
  }

  old_sec_state = p_dev_rec->sec_rec.sec_state;
  if (status == HCI_SUCCESS) {
    log::debug(
        "Remote read request complete for known device pairing_state:{} "
        "name:{} sec_state:{}",
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
        reinterpret_cast<char const*>(p_bd_name),
        security_state_text(p_dev_rec->sec_rec.sec_state).c_str());

    strlcpy((char*)p_dev_rec->sec_bd_name, (const char*)p_bd_name,
            BTM_MAX_REM_BD_NAME_LEN + 1);
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_NAME_KNOWN;
    log::verbose("setting BTM_SEC_NAME_KNOWN sec_flags:0x{:x}",
                 p_dev_rec->sec_rec.sec_flags);
  } else {
    log::warn(
        "Remote read request failed for known device pairing_state:{} "
        "status:{} name:{} sec_state:{}",
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
        hci_status_code_text(status).c_str(),
        reinterpret_cast<char const*>(p_bd_name),
        security_state_text(p_dev_rec->sec_rec.sec_state).c_str());

    /* Notify all clients waiting for name to be resolved even if it failed so
     * clients can continue */
    p_dev_rec->sec_bd_name[0] = 0;
  }

  if (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_GETTING_NAME)
    p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;

  /* Notify all clients waiting for name to be resolved */
  call_registered_rmt_name_callbacks(p_bd_addr, p_dev_rec->dev_class,
                                     p_dev_rec->sec_bd_name, status);

  /* If we were delaying asking UI for a PIN because name was not resolved,
   * ask now */
  if ((btm_sec_cb.pairing_state == BTM_PAIR_STATE_WAIT_LOCAL_PIN) &&
      p_bd_addr && (btm_sec_cb.pairing_bda == *p_bd_addr)) {
    log::verbose(
        "delayed pin now being requested flags:0x{:x}, (p_pin_callback=0x{})",
        btm_sec_cb.pairing_flags, fmt::ptr(btm_sec_cb.api.p_pin_callback));

    if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_PIN_REQD) == 0 &&
        btm_sec_cb.api.p_pin_callback) {
      log::verbose("calling pin_callback");
      btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_PIN_REQD;
      (*btm_sec_cb.api.p_pin_callback)(
          p_dev_rec->bd_addr, p_dev_rec->dev_class, p_bd_name,
          (p_dev_rec->sec_rec.required_security_flags_for_pairing &
           BTM_SEC_IN_MIN_16_DIGIT_PIN));
    }

    /* Set the same state again to force the timer to be restarted */
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_LOCAL_PIN);
    return;
  }

  /* Check if we were delaying bonding because name was not resolved */
  if (btm_sec_cb.pairing_state == BTM_PAIR_STATE_GET_REM_NAME) {
    if (p_bd_addr && btm_sec_cb.pairing_bda == *p_bd_addr) {
      log::verbose("continue bonding sm4: 0x{:04x}, status:0x{:x}",
                   p_dev_rec->sm4, status);
      if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_CANCEL_DD) {
        btm_sec_bond_cancel_complete();
        return;
      }

      if (status != HCI_SUCCESS) {
        btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);

        return NotifyBondingChange(*p_dev_rec, status);
      }

      /* if peer is very old legacy devices, HCI_RMT_HOST_SUP_FEAT_NOTIFY_EVT is
       * not reported */
      if (BTM_SEC_IS_SM4_UNKNOWN(p_dev_rec->sm4)) {
        /* set the KNOWN flag only if BTM_PAIR_FLAGS_REJECTED_CONNECT is not
         * set.*/
        /* If it is set, there may be a race condition */
        log::verbose("IS_SM4_UNKNOWN Flags:0x{:04x}", btm_sec_cb.pairing_flags);
        if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_REJECTED_CONNECT) == 0)
          p_dev_rec->sm4 |= BTM_SM4_KNOWN;
      }

      log::verbose("SM4 Value: {:x}, Legacy:{},IS SM4:{}, Unknown:{}",
                   p_dev_rec->sm4, BTM_SEC_IS_SM4_LEGACY(p_dev_rec->sm4),
                   BTM_SEC_IS_SM4(p_dev_rec->sm4),
                   BTM_SEC_IS_SM4_UNKNOWN(p_dev_rec->sm4));

      bool await_connection = true;
      /* BT 2.1 or carkit, bring up the connection to force the peer to request
       *PIN.
       ** Else prefetch (btm_sec_check_prefetch_pin will do the prefetching if
       *needed)
       */
      if ((p_dev_rec->sm4 != BTM_SM4_KNOWN) ||
          !btm_sec_check_prefetch_pin(p_dev_rec)) {
        /* if we rejected incoming connection request, we have to wait
         * HCI_Connection_Complete event */
        /*  before originating  */
        if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_REJECTED_CONNECT) {
          log::warn(
              "waiting HCI_Connection_Complete after rejecting connection");
        }
        /* Both we and the peer are 2.1 - continue to create connection */
        else {
          tBTM_STATUS req_status = btm_sec_dd_create_conn(p_dev_rec);
          if (req_status == BTM_SUCCESS) {
            await_connection = false;
          } else if (req_status != BTM_CMD_STARTED) {
            log::warn("failed to start connection");

            btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);

            NotifyBondingChange(*p_dev_rec, HCI_ERR_MEMORY_FULL);
          }
        }
      }

      if (await_connection) {
        log::debug("Wait for connection to begin pairing");
        return;
      }
    } else {
      log::warn("wrong BDA, retry with pairing BDA");
      if (BTM_ReadRemoteDeviceName(btm_sec_cb.pairing_bda, NULL,
                                   BT_TRANSPORT_BR_EDR) != BTM_CMD_STARTED) {
        log::error("failed to start remote name request");
        NotifyBondingChange(*p_dev_rec, HCI_ERR_MEMORY_FULL);
      };
      return;
    }
  }

  /* check if we were delaying link_key_callback because name was not resolved
   */
  if (p_dev_rec->sec_rec.link_key_not_sent) {
    /* If HCI connection complete has not arrived, wait for it */
    if (p_dev_rec->hci_handle == HCI_INVALID_HANDLE) return;

    p_dev_rec->sec_rec.link_key_not_sent = false;
    btm_send_link_key_notif(p_dev_rec);
  }

  /* If this is a bonding procedure can disconnect the link now */
  if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) &&
      (p_dev_rec->sec_rec.sec_flags & BTM_SEC_AUTHENTICATED)) {
    log::warn("btm_sec_rmt_name_request_complete (none/ce)");
    p_dev_rec->sec_rec.security_required &= ~(BTM_SEC_OUT_AUTHENTICATE);
    l2cu_start_post_bond_timer(p_dev_rec->hci_handle);
    return;
  }

  if (old_sec_state != BTM_SEC_STATE_GETTING_NAME) return;

  /* If get name failed, notify the waiting layer */
  if (status != HCI_SUCCESS) {
    btm_sec_dev_rec_cback_event(p_dev_rec, BTM_ERR_PROCESSING, false);
    return;
  }

  if (p_dev_rec->sm4 & BTM_SM4_REQ_PEND) {
    log::verbose("waiting for remote features!!");
    return;
  }

  /* Remote Name succeeded, execute the next security procedure, if any */
  tBTM_STATUS btm_status = btm_sec_execute_procedure(p_dev_rec);

  /* If result is pending reply from the user or from the device is pending */
  if (btm_status == BTM_CMD_STARTED) return;

  /* There is no next procedure or start of procedure failed, notify the waiting
   * layer */
  btm_sec_dev_rec_cback_event(p_dev_rec, btm_status, false);
}

/*******************************************************************************
 *
 * Function         btm_sec_rmt_host_support_feat_evt
 *
 * Description      This function is called when the
 *                  HCI_RMT_HOST_SUP_FEAT_NOTIFY_EVT is received
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_rmt_host_support_feat_evt(const RawAddress bd_addr,
                                       uint8_t features_0) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  p_dev_rec = btm_find_or_alloc_dev(bd_addr);

  log::info("Got btm_sec_rmt_host_support_feat_evt from {}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  log::verbose("btm_sec_rmt_host_support_feat_evt  sm4: 0x{:x}  p[0]: 0x{:x}",
               p_dev_rec->sm4, features_0);

  if (BTM_SEC_IS_SM4_UNKNOWN(p_dev_rec->sm4)) {
    p_dev_rec->sm4 = BTM_SM4_KNOWN;
    if (HCI_SSP_HOST_SUPPORTED((std::array<uint8_t, 1>({features_0})))) {
      p_dev_rec->sm4 = BTM_SM4_TRUE;
    }
    log::verbose(
        "btm_sec_rmt_host_support_feat_evt sm4: 0x{:x} features[0]: 0x{:x}",
        p_dev_rec->sm4, features_0);
  }
}

/*******************************************************************************
 *
 * Function         btm_io_capabilities_req
 *
 * Description      This function is called when LM request for the IO
 *                  capability of the local device and
 *                  if the OOB data is present for the device in the event
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_io_capabilities_req(RawAddress p) {
  if (btm_sec_is_a_bonded_dev(p)) {
    log::warn("Incoming bond request, but {} is already bonded (removing)",
              ADDRESS_TO_LOGGABLE_CSTR(p));
    bta_dm_process_remove_device(p);
  }

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(p);

  if ((btm_sec_cb.security_mode == BTM_SEC_MODE_SC) &&
      (!p_dev_rec->remote_feature_received)) {
    log::verbose(
        "Device security mode is SC only.To continue need to know remote "
        "features.");

    // ACL calls back to btm_sec_set_peer_sec_caps after it gets data
    p_dev_rec->remote_features_needed = true;
    return;
  }

  tBTM_SP_IO_REQ evt_data;
  evt_data.bd_addr = p;

  /* setup the default response according to compile options */
  /* assume that the local IO capability does not change
   * loc_io_caps is initialized with the default value */
  evt_data.io_cap = btm_sec_cb.devcb.loc_io_caps;
  // TODO(optedoblivion): Inject OOB_DATA_PRESENT Flag
  evt_data.oob_data = BTM_OOB_NONE;
  evt_data.auth_req = BTM_AUTH_SP_NO;

  p_dev_rec->sm4 |= BTM_SM4_TRUE;

  log::verbose("State: {}, Security Mode: {}, Device security Flags: 0x{:04x}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               btm_sec_cb.security_mode, btm_sec_cb.pairing_flags);

  uint8_t err_code = 0;
  bool is_orig = true;
  switch (btm_sec_cb.pairing_state) {
    /* initiator connecting */
    case BTM_PAIR_STATE_IDLE:
      // TODO: Handle Idle pairing state
      // security_required = p_dev_rec->sec_rec.security_required;
      break;

    /* received IO capability response already->acceptor */
    case BTM_PAIR_STATE_INCOMING_SSP:
      is_orig = false;

      if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_PEER_STARTED_DD) {
        /* acceptor in dedicated bonding */
        evt_data.auth_req = BTM_AUTH_AP_YES;
      }
      break;

    /* initiator, at this point it is expected to be dedicated bonding
    initiated by local device */
    case BTM_PAIR_STATE_WAIT_PIN_REQ:
      if (evt_data.bd_addr == btm_sec_cb.pairing_bda) {
        evt_data.auth_req = BTM_AUTH_AP_YES;
      } else {
        err_code = HCI_ERR_HOST_BUSY_PAIRING;
      }
      break;

    /* any other state is unexpected */
    default:
      err_code = HCI_ERR_HOST_BUSY_PAIRING;
      log::error("Unexpected Pairing state received {}",
                 btm_sec_cb.pairing_state);
      break;
  }

  if (btm_sec_cb.pairing_disabled) {
    /* pairing is not allowed */
    log::verbose("Pairing is not allowed -> fail pairing.");
    err_code = HCI_ERR_PAIRING_NOT_ALLOWED;
  } else if (btm_sec_cb.security_mode == BTM_SEC_MODE_SC) {
    bool local_supports_sc =
        bluetooth::shim::GetController()->SupportsSecureConnections();
    /* device in Secure Connections Only mode */
    if (!(local_supports_sc) || !(p_dev_rec->SupportsSecureConnections())) {
      log::debug(
          "SC only service, local_support_for_sc:{}, remote_support_for_sc:{} "
          "-> fail pairing",
          logbool(local_supports_sc).c_str(),
          logbool(p_dev_rec->SupportsSecureConnections()).c_str());
      err_code = HCI_ERR_PAIRING_NOT_ALLOWED;
    }
  }

  if (err_code != 0) {
    btsnd_hcic_io_cap_req_neg_reply(evt_data.bd_addr, err_code);
    return;
  }

  evt_data.is_orig = is_orig;

  if (is_orig) {
    /* local device initiated the pairing non-bonding -> use
     * required_security_flags_for_pairing */
    if (!(btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) &&
        (p_dev_rec->sec_rec.required_security_flags_for_pairing &
         BTM_SEC_OUT_AUTHENTICATE)) {
      if (btm_sec_cb.security_mode == BTM_SEC_MODE_SC) {
        /* SC only mode device requires MITM protection */
        evt_data.auth_req = BTM_AUTH_SP_YES;
      } else {
        evt_data.auth_req =
            (p_dev_rec->sec_rec.required_security_flags_for_pairing &
             BTM_SEC_OUT_MITM)
                ? BTM_AUTH_SP_YES
                : BTM_AUTH_SP_NO;
      }
    }
  }

  /* Notify L2CAP to increase timeout */
  l2c_pin_code_request(evt_data.bd_addr);

  btm_sec_cb.pairing_bda = evt_data.bd_addr;

  if (evt_data.bd_addr == btm_sec_cb.connecting_bda)
    p_dev_rec->dev_class = btm_sec_cb.connecting_dc;

  btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_LOCAL_IOCAPS);

  if (p_dev_rec->sm4 & BTM_SM4_UPGRADE) {
    p_dev_rec->sm4 &= ~BTM_SM4_UPGRADE;

    /* link key upgrade: always use SPGB_YES - assuming we want to save the link
     * key */
    evt_data.auth_req = BTM_AUTH_SPGB_YES;
  } else if (btm_sec_cb.api.p_sp_callback) {
    /* the callback function implementation may change the IO capability... */
    (*btm_sec_cb.api.p_sp_callback)(BTM_SP_IO_REQ_EVT,
                                    (tBTM_SP_EVT_DATA*)&evt_data);
  }

  if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD)) {
    evt_data.auth_req =
        (BTM_AUTH_DD_BOND | (evt_data.auth_req & BTM_AUTH_YN_BIT));
  }

  if (btm_sec_cb.security_mode == BTM_SEC_MODE_SC) {
    /* At this moment we know that both sides are SC capable, device in */
    /* SC only mode requires MITM for any service so let's set MITM bit */
    evt_data.auth_req |= BTM_AUTH_YN_BIT;
    log::verbose("for device in \"SC only\" mode set auth_req to 0x{:02x}",
                 evt_data.auth_req);
  }

  /* if the user does not indicate "reply later" by setting the oob_data to
   * unknown */
  /* send the response right now. Save the current IO capability in the
   * control block */
  btm_sec_cb.devcb.loc_auth_req = evt_data.auth_req;
  btm_sec_cb.devcb.loc_io_caps = evt_data.io_cap;

  log::verbose("State: {}  IO_CAP:{} oob_data:{} auth_req:{}",
               tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
               evt_data.io_cap, evt_data.oob_data, evt_data.auth_req);

  btsnd_hcic_io_cap_req_reply(evt_data.bd_addr, evt_data.io_cap,
                              evt_data.oob_data, evt_data.auth_req);
}

/*******************************************************************************
 *
 * Function         btm_io_capabilities_rsp
 *
 * Description      This function is called when the IO capability of the
 *                  specified device is received
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_io_capabilities_rsp(const tBTM_SP_IO_RSP evt_data) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  /* Allocate a new device record or reuse the oldest one */
  p_dev_rec = btm_find_or_alloc_dev(evt_data.bd_addr);

  /* If no security is in progress, this indicates incoming security */
  if (btm_sec_cb.pairing_state == BTM_PAIR_STATE_IDLE) {
    btm_sec_cb.pairing_bda = evt_data.bd_addr;

    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_INCOMING_SSP);

    /* work around for FW bug */
    btm_inq_stop_on_ssp();
  }

  /* Notify L2CAP to increase timeout */
  l2c_pin_code_request(evt_data.bd_addr);

  /* We must have a device record here.
   * Use the connecting device's CoD for the connection */
  if (evt_data.bd_addr == btm_sec_cb.connecting_bda)
    p_dev_rec->dev_class = btm_sec_cb.connecting_dc;

  /* peer sets dedicated bonding bit and we did not initiate dedicated bonding
   */
  if (btm_sec_cb.pairing_state ==
          BTM_PAIR_STATE_INCOMING_SSP /* peer initiated bonding */
      && (evt_data.auth_req &
          BTM_AUTH_DD_BOND)) /* and dedicated bonding bit is set */
  {
    btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_PEER_STARTED_DD;
  }

  /* save the IO capability in the device record */
  p_dev_rec->sec_rec.rmt_io_caps = evt_data.io_cap;
  p_dev_rec->sec_rec.rmt_auth_req = evt_data.auth_req;

  if (btm_sec_cb.api.p_sp_callback)
    (*btm_sec_cb.api.p_sp_callback)(BTM_SP_IO_RSP_EVT,
                                    (tBTM_SP_EVT_DATA*)&evt_data);
}

/*******************************************************************************
 *
 * Function         btm_proc_sp_req_evt
 *
 * Description      This function is called to process/report
 *                  HCI_USER_CONFIRMATION_REQUEST_EVT
 *                  or HCI_USER_PASSKEY_REQUEST_EVT
 *                  or HCI_USER_PASSKEY_NOTIFY_EVT
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_proc_sp_req_evt(tBTM_SP_EVT event, const RawAddress bda,
                         const uint32_t value) {
  tBTM_STATUS status = BTM_ERR_PROCESSING;
  tBTM_SP_EVT_DATA evt_data;
  RawAddress& p_bda = evt_data.cfm_req.bd_addr;
  tBTM_SEC_DEV_REC* p_dev_rec;

  p_bda = bda;
  log::debug("BDA:{}, event:{}, state:{}", ADDRESS_TO_LOGGABLE_CSTR(p_bda),
             sp_evt_to_text(event).c_str(),
             tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state));

  p_dev_rec = btm_find_dev(p_bda);
  if ((p_dev_rec != NULL) &&
      (btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (btm_sec_cb.pairing_bda == p_bda)) {
    evt_data.cfm_req.bd_addr = p_dev_rec->bd_addr;
    evt_data.cfm_req.dev_class = p_dev_rec->dev_class;

    strlcpy((char*)evt_data.cfm_req.bd_name, (char*)p_dev_rec->sec_bd_name,
            BTM_MAX_REM_BD_NAME_LEN + 1);

    switch (event) {
      case BTM_SP_CFM_REQ_EVT:
        /* Numeric confirmation. Need user to conf the passkey */
        btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_NUMERIC_CONFIRM);

        /* The device record must be allocated in the "IO cap exchange" step */
        evt_data.cfm_req.num_val = value;
        log::verbose("num_val:{}", evt_data.cfm_req.num_val);

        evt_data.cfm_req.just_works = true;

        /* process user confirm req in association with the auth_req param */
        if (btm_sec_cb.devcb.loc_io_caps == BTM_IO_CAP_IO) {
          if (p_dev_rec->sec_rec.rmt_io_caps == BTM_IO_CAP_UNKNOWN) {
            log::error(
                "did not receive IO cap response prior to BTM_SP_CFM_REQ_EVT, "
                "failing pairing request");
            status = BTM_WRONG_MODE;
            BTM_ConfirmReqReply(status, p_bda);
            return;
          }

          if ((p_dev_rec->sec_rec.rmt_io_caps == BTM_IO_CAP_IO ||
               p_dev_rec->sec_rec.rmt_io_caps == BTM_IO_CAP_OUT) &&
              (btm_sec_cb.devcb.loc_io_caps == BTM_IO_CAP_IO) &&
              ((p_dev_rec->sec_rec.rmt_auth_req & BTM_AUTH_SP_YES) ||
               (btm_sec_cb.devcb.loc_auth_req & BTM_AUTH_SP_YES))) {
            /* Use Numeric Comparison if
             * 1. Local IO capability is DisplayYesNo,
             * 2. Remote IO capability is DisplayOnly or DiaplayYesNo, and
             * 3. Either of the devices have requested authenticated link key */
            evt_data.cfm_req.just_works = false;
          }
        }

        log::verbose("just_works:{}, io loc:{}, rmt:{}, auth loc:{}, rmt:{}",
                     evt_data.cfm_req.just_works, btm_sec_cb.devcb.loc_io_caps,
                     p_dev_rec->sec_rec.rmt_io_caps,
                     btm_sec_cb.devcb.loc_auth_req,
                     p_dev_rec->sec_rec.rmt_auth_req);

        evt_data.cfm_req.loc_auth_req = btm_sec_cb.devcb.loc_auth_req;
        evt_data.cfm_req.rmt_auth_req = p_dev_rec->sec_rec.rmt_auth_req;
        evt_data.cfm_req.loc_io_caps = btm_sec_cb.devcb.loc_io_caps;
        evt_data.cfm_req.rmt_io_caps = p_dev_rec->sec_rec.rmt_io_caps;
        break;

      case BTM_SP_KEY_NOTIF_EVT:
        /* Passkey notification (other side is a keyboard) */
        evt_data.key_notif.passkey = value;
        log::verbose("passkey:{}", evt_data.key_notif.passkey);

        btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);
        break;

      case BTM_SP_KEY_REQ_EVT:
        if (btm_sec_cb.devcb.loc_io_caps != BTM_IO_CAP_NONE) {
          /* HCI_USER_PASSKEY_REQUEST_EVT */
          btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_KEY_ENTRY);
        }
        break;
      default:
        log::warn("unhandled event:{}", sp_evt_to_text(event).c_str());
        break;
    }

    if (btm_sec_cb.api.p_sp_callback) {
      status = (*btm_sec_cb.api.p_sp_callback)(event, &evt_data);
      if (status != BTM_NOT_AUTHORIZED) {
        return;
      }
      /* else BTM_NOT_AUTHORIZED means when the app wants to reject the req
       * right now */
    } else if ((event == BTM_SP_CFM_REQ_EVT) && (evt_data.cfm_req.just_works)) {
      /* automatically reply with just works if no sp_cback */
      status = BTM_SUCCESS;
    }

    if (event == BTM_SP_CFM_REQ_EVT) {
      log::verbose("calling BTM_ConfirmReqReply with status: {}", status);
      BTM_ConfirmReqReply(status, p_bda);
    } else if (btm_sec_cb.devcb.loc_io_caps != BTM_IO_CAP_NONE &&
               event == BTM_SP_KEY_REQ_EVT) {
      BTM_PasskeyReqReply(status, p_bda, 0);
    }
    return;
  }

  /* Something bad. we can only fail this connection */
  acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);

  if (BTM_SP_CFM_REQ_EVT == event) {
    btsnd_hcic_user_conf_reply(p_bda, false);
  } else if (BTM_SP_KEY_NOTIF_EVT == event) {
    /* do nothing -> it very unlikely to happen.
    This event is most likely to be received by a HID host when it first
    connects to a HID device.
    Usually the Host initiated the connection in this case.
    On Mobile platforms, if there's a security process happening,
    the host probably can not initiate another connection.
    BTW (PC) is another story.  */
    p_dev_rec = btm_find_dev(p_bda);
    if (p_dev_rec != NULL) {
      btm_sec_disconnect(
          p_dev_rec->hci_handle, HCI_ERR_AUTH_FAILURE,
          "stack::btm::btm_sec::btm_proc_sp_req_evt Security failure");
    }
  } else if (btm_sec_cb.devcb.loc_io_caps != BTM_IO_CAP_NONE) {
    btsnd_hcic_user_passkey_neg_reply(p_bda);
  }
}

/*******************************************************************************
 *
 * Function         btm_simple_pair_complete
 *
 * Description      This function is called when simple pairing process is
 *                  complete
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_simple_pair_complete(const RawAddress bd_addr, uint8_t status) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  bool disc = false;

  p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) {
    log::error("unknown BDA: {}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    return;
  }

  log::verbose(
      "btm_simple_pair_complete()  Pair State: {}  Status:{}  sec_state: {}",
      tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state), status,
      p_dev_rec->sec_rec.sec_state);

  if (status == HCI_SUCCESS) {
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_AUTHENTICATED;
  } else if (status == HCI_ERR_PAIRING_NOT_ALLOWED) {
    /* The test spec wants the peer device to get this failure code. */
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_DISCONNECT);

    /* Change the timer to 1 second */
    alarm_set_on_mloop(btm_sec_cb.pairing_timer, BT_1SEC_TIMEOUT_MS,
                       btm_sec_pairing_timeout, NULL);
  } else if (btm_sec_cb.pairing_bda == bd_addr) {
    /* stop the timer */
    alarm_cancel(btm_sec_cb.pairing_timer);

    if (p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_AUTHENTICATING) {
      /* the initiating side: will receive auth complete event. disconnect ACL
       * at that time */
      disc = true;
    }
  } else {
    disc = true;
  }

  if (disc) {
    /* simple pairing failed */
    /* Avoid sending disconnect on HCI_ERR_PEER_USER */
    if ((status != HCI_ERR_PEER_USER) &&
        (status != HCI_ERR_CONN_CAUSE_LOCAL_HOST)) {
      btm_sec_send_hci_disconnect(
          p_dev_rec, HCI_ERR_AUTH_FAILURE, p_dev_rec->hci_handle,
          "stack::btm::btm_sec::btm_simple_pair_complete Auth fail");
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_rem_oob_req
 *
 * Description      This function is called to process/report
 *                  HCI_REMOTE_OOB_DATA_REQUEST_EVT
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_rem_oob_req(const RawAddress bd_addr) {
  tBTM_SP_RMT_OOB evt_data;
  tBTM_SEC_DEV_REC* p_dev_rec;
  Octet16 c;
  Octet16 r;

  evt_data.bd_addr = bd_addr;
  RawAddress& p_bda = evt_data.bd_addr;

  log::verbose("BDA: {}", ADDRESS_TO_LOGGABLE_CSTR(p_bda));
  p_dev_rec = btm_find_dev(p_bda);
  if ((p_dev_rec != NULL) && btm_sec_cb.api.p_sp_callback) {
    evt_data.bd_addr = p_dev_rec->bd_addr;
    evt_data.dev_class = p_dev_rec->dev_class;
    strlcpy((char*)evt_data.bd_name, (char*)p_dev_rec->sec_bd_name,
            BTM_MAX_REM_BD_NAME_LEN + 1);

    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_LOCAL_OOB_RSP);
    if ((*btm_sec_cb.api.p_sp_callback)(BTM_SP_RMT_OOB_EVT,
                                        (tBTM_SP_EVT_DATA*)&evt_data) ==
        BTM_NOT_AUTHORIZED) {
      BTM_RemoteOobDataReply(static_cast<tBTM_STATUS>(true), p_bda, c, r);
    }
    return;
  }

  /* something bad. we can only fail this connection */
  acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);
  btsnd_hcic_rem_oob_neg_reply(p_bda);
}

/*******************************************************************************
 *
 * Function         btm_read_local_oob_complete
 *
 * Description      This function is called when read local oob data is
 *                  completed by the LM
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_local_oob_complete(const tBTM_SP_LOC_OOB evt_data) {
  log::verbose("btm_read_local_oob_complete:{}", evt_data.status);

  if (btm_sec_cb.api.p_sp_callback) {
    tBTM_SP_EVT_DATA btm_sp_evt_data;
    btm_sp_evt_data.loc_oob = evt_data;
    (*btm_sec_cb.api.p_sp_callback)(BTM_SP_LOC_OOB_EVT, &btm_sp_evt_data);
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_auth_collision
 *
 * Description      This function is called when authentication or encryption
 *                  needs to be retried at a later time.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_sec_auth_collision(uint16_t handle) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  if (!btm_sec_cb.collision_start_time)
    btm_sec_cb.collision_start_time =
        bluetooth::common::time_get_os_boottime_ms();

  if ((bluetooth::common::time_get_os_boottime_ms() -
       btm_sec_cb.collision_start_time) < BTM_SEC_MAX_COLLISION_DELAY) {
    if (handle == HCI_INVALID_HANDLE) {
      p_dev_rec = btm_sec_find_dev_by_sec_state(BTM_SEC_STATE_AUTHENTICATING);
      if (p_dev_rec == NULL)
        p_dev_rec = btm_sec_find_dev_by_sec_state(BTM_SEC_STATE_ENCRYPTING);
    } else
      p_dev_rec = btm_find_dev_by_handle(handle);

    if (p_dev_rec != NULL) {
      log::verbose("btm_sec_auth_collision: state {} (retrying in a moment...)",
                   p_dev_rec->sec_rec.sec_state);
      /* We will restart authentication after timeout */
      if (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING ||
          p_dev_rec->sec_rec.is_security_state_bredr_encrypting())
        p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;

      btm_sec_cb.p_collided_dev_rec = p_dev_rec;
      alarm_set_on_mloop(btm_sec_cb.sec_collision_timer, BT_1SEC_TIMEOUT_MS,
                         btm_sec_collision_timeout, NULL);
    }
  }
}

/******************************************************************************
 *
 * Function         btm_sec_auth_retry
 *
 * Description      This function is called when authentication or encryption
 *                  needs to be retried at a later time.
 *
 * Returns          TRUE if a security retry required
 *
 *****************************************************************************/
static bool btm_sec_auth_retry(uint16_t handle, uint8_t status) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);
  if (!p_dev_rec) return false;

  /* keep the old sm4 flag and clear the retry bit in control block */
  uint8_t old_sm4 = p_dev_rec->sm4;
  p_dev_rec->sm4 &= ~BTM_SM4_RETRY;

  if ((btm_sec_cb.pairing_state == BTM_PAIR_STATE_IDLE) &&
      ((old_sm4 & BTM_SM4_RETRY) == 0) && (HCI_ERR_KEY_MISSING == status) &&
      BTM_SEC_IS_SM4(p_dev_rec->sm4)) {
    /* This retry for missing key is for Lisbon or later only.
       Legacy device do not need this. the controller will drive the retry
       automatically
       set the retry bit */
    btm_sec_cb.collision_start_time = 0;
    btm_restore_mode();
    p_dev_rec->sm4 |= BTM_SM4_RETRY;
    p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LINK_KEY_KNOWN;
    log::verbose("Retry for missing key sm4:x{:x} sec_flags:0x{:x}",
                 p_dev_rec->sm4, p_dev_rec->sec_rec.sec_flags);

    /* With BRCM controller, we do not need to delete the stored link key in
       controller.
       If the stack may sit on top of other controller, we may need this
       BTM_DeleteStoredLinkKey (bd_addr, NULL); */
    p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
    btm_sec_execute_procedure(p_dev_rec);
    return true;
  }

  return false;
}

void btm_sec_auth_complete(uint16_t handle, tHCI_STATUS status) {
  tBTM_PAIRING_STATE old_state = btm_sec_cb.pairing_state;
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);
  bool are_bonding = false;
  bool was_authenticating = false;

  if (p_dev_rec) {
    log::verbose(
        "Security Manager: in state: {}, handle: {}, status: {}, "
        "dev->sec_rec.sec_state:{}, bda: {}, RName: {}",
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state), handle,
        status, p_dev_rec->sec_rec.sec_state,
        ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr),
        reinterpret_cast<char const*>(p_dev_rec->sec_bd_name));
  } else {
    log::verbose("Security Manager: in state: {}, handle: {}, status: {}",
                 tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
                 handle, status);
  }

  /* For transaction collision we need to wait and repeat.  There is no need */
  /* for random timeout because only peripheral should receive the result */
  if ((status == HCI_ERR_LMP_ERR_TRANS_COLLISION) ||
      (status == HCI_ERR_DIFF_TRANSACTION_COLLISION)) {
    btm_sec_auth_collision(handle);
    return;
  } else if (btm_sec_auth_retry(handle, status)) {
    return;
  }

  btm_sec_cb.collision_start_time = 0;

  btm_restore_mode();

  /* Check if connection was made just to do bonding.  If we authenticate
     the connection that is up, this is the last event received.
  */
  if (p_dev_rec && (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) &&
      !(btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_DISC_WHEN_DONE)) {
    p_dev_rec->sec_rec.security_required &= ~BTM_SEC_OUT_AUTHENTICATE;

    l2cu_start_post_bond_timer(p_dev_rec->hci_handle);
  }

  if (!p_dev_rec) return;

  if (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING) {
    p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
    was_authenticating = true;
    /* There can be a race condition, when we are starting authentication
     * and the peer device is doing encryption.
     * If first we receive encryption change up, then initiated
     * authentication can not be performed.
     * According to the spec we can not do authentication on the
     * encrypted link, so device is correct.
     */
    if ((status == HCI_ERR_COMMAND_DISALLOWED) &&
        ((p_dev_rec->sec_rec.sec_flags &
          (BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED)) ==
         (BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED))) {
      status = HCI_SUCCESS;
    }
    if (status == HCI_SUCCESS) {
      p_dev_rec->sec_rec.sec_flags |= BTM_SEC_AUTHENTICATED;
    }
  }

  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (p_dev_rec->bd_addr == btm_sec_cb.pairing_bda)) {
    if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) {
      are_bonding = true;
    }
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
  }

  if (was_authenticating == false) {
    if (status != HCI_SUCCESS && old_state != BTM_PAIR_STATE_IDLE) {
      NotifyBondingChange(*p_dev_rec, status);
    }
    return;
  }

  /* Currently we do not notify user if it is a keyboard which connects */
  /* User probably Disabled the keyboard while it was asleap.  Let them try */
  if (btm_sec_cb.api.p_auth_complete_callback) {
    /* report the suthentication status */
    if ((old_state != BTM_PAIR_STATE_IDLE) || (status != HCI_SUCCESS))
      (*btm_sec_cb.api.p_auth_complete_callback)(
          p_dev_rec->bd_addr, p_dev_rec->dev_class, p_dev_rec->sec_bd_name,
          status);
  }

  /* If this is a bonding procedure can disconnect the link now */
  if (are_bonding) {
    p_dev_rec->sec_rec.security_required &= ~BTM_SEC_OUT_AUTHENTICATE;

    if (status != HCI_SUCCESS) {
      if (((status != HCI_ERR_PEER_USER) &&
           (status != HCI_ERR_CONN_CAUSE_LOCAL_HOST)))
        btm_sec_send_hci_disconnect(
            p_dev_rec, HCI_ERR_PEER_USER, p_dev_rec->hci_handle,
            "stack::btm::btm_sec::btm_sec_auth_retry Auth fail while bonding");
    } else {
      BTM_LogHistory(kBtmLogTag, p_dev_rec->bd_addr, "Bonding completed",
                     hci_error_code_text(status));

      tHCI_ROLE role = HCI_ROLE_UNKNOWN;
      BTM_GetRole(p_dev_rec->bd_addr, &role);
      if (role == HCI_ROLE_CENTRAL) {
        // Encryption is required to start SM over BR/EDR
        // indicate that this is encryption after authentication
        BTM_SetEncryption(p_dev_rec->bd_addr, BT_TRANSPORT_BR_EDR, NULL, NULL,
                          BTM_BLE_SEC_NONE);
      } else if (p_dev_rec->IsLocallyInitiated()) {
        // Encryption will be set in role_changed callback
        log::info(
            "auth completed in role=peripheral, try to switch role and "
            "encrypt");
        BTM_SwitchRoleToCentral(p_dev_rec->RemoteAddress());
      }

      l2cu_start_post_bond_timer(p_dev_rec->hci_handle);
    }

    return;
  }

  /* If authentication failed, notify the waiting layer */
  if (status != HCI_SUCCESS) {
    btm_sec_dev_rec_cback_event(p_dev_rec, BTM_ERR_PROCESSING, false);

    if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_DISC_WHEN_DONE) {
      btm_sec_send_hci_disconnect(
          p_dev_rec, HCI_ERR_AUTH_FAILURE, p_dev_rec->hci_handle,
          "stack::btm::btm_sec::btm_sec_auth_retry Auth failed");
    }
    return;
  }

  if (p_dev_rec->sec_rec.pin_code_length >= 16 ||
      p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB ||
      p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256) {
    // If we have MITM protection we have a higher level of security than
    // provided by 16 digits PIN
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_16_DIGIT_PIN_AUTHED;
  }

  /* Authentication succeeded, execute the next security procedure, if any */
  tBTM_STATUS btm_status = btm_sec_execute_procedure(p_dev_rec);

  /* If there is no next procedure, or procedure failed to start, notify the
   * caller */
  if (btm_status != BTM_CMD_STARTED)
    btm_sec_dev_rec_cback_event(p_dev_rec, btm_status, false);
}

/*******************************************************************************
 *
 * Function         btm_sec_encrypt_change
 *
 * Description      This function is when encryption of the connection is
 *                  completed by the LM
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_encrypt_change(uint16_t handle, tHCI_STATUS status,
                            uint8_t encr_enable) {
  /* For transaction collision we need to wait and repeat.  There is no need */
  /* for random timeout because only peripheral should receive the result */
  if ((status == HCI_ERR_LMP_ERR_TRANS_COLLISION) ||
      (status == HCI_ERR_DIFF_TRANSACTION_COLLISION)) {
    log::error("Encryption collision failed status:{}",
               hci_error_code_text(status).c_str());
    btm_sec_auth_collision(handle);
    return;
  }
  btm_sec_cb.collision_start_time = 0;

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);
  if (p_dev_rec == nullptr) {
    log::warn(
        "Received encryption change for unknown device handle:0x{:04x} "
        "status:{} enable:0x{:x}",
        handle, hci_status_code_text(status).c_str(), encr_enable);
    return;
  }

  const tBT_TRANSPORT transport =
      BTM_IsBleConnection(handle) ? BT_TRANSPORT_LE : BT_TRANSPORT_BR_EDR;

  log::debug(
      "Security Manager encryption change request hci_status:{} request:{} "
      "state:{} sec_flags:0x{:x}",
      hci_status_code_text(status).c_str(),
      (encr_enable) ? "encrypt" : "unencrypt",
      (p_dev_rec->sec_rec.sec_state) ? "encrypted" : "unencrypted",
      p_dev_rec->sec_rec.sec_flags);

  if (status == HCI_SUCCESS) {
    if (encr_enable) {
      if (p_dev_rec->hci_handle == handle) {  // classic
        p_dev_rec->sec_rec.sec_flags |=
            (BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED);
        if (p_dev_rec->sec_rec.pin_code_length >= 16 ||
            p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB ||
            p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256) {
          p_dev_rec->sec_rec.sec_flags |= BTM_SEC_16_DIGIT_PIN_AUTHED;
        }
      } else if (p_dev_rec->ble_hci_handle == handle) {  // BLE
        p_dev_rec->sec_rec.set_le_device_encrypted();
        if (p_dev_rec->sec_rec.is_le_link_key_authenticated()) {
          p_dev_rec->sec_rec.set_le_device_authenticated();
        }
      } else {
        log::error(
            "Received encryption change for unknown device handle:0x{:04x} "
            "status:{} enable:0x{:x}",
            handle, hci_status_code_text(status).c_str(), encr_enable);
      }
    } else {
      log::info(
          "Encryption was not enabled locally resetting encryption state");
      /* It is possible that we decrypted the link to perform role switch */
      /* mark link not to be encrypted, so that when we execute security next
       * time it will kick in again */
      if (p_dev_rec->hci_handle == handle) {  // clasic
        p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_ENCRYPTED;
      } else if (p_dev_rec->ble_hci_handle == handle) {  // BLE
        p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LE_ENCRYPTED;
      } else {
        log::error(
            "Received encryption change for unknown device handle:0x{:04x} "
            "status:{} enable:0x{:x}",
            handle, hci_status_code_text(status).c_str(), encr_enable);
      }
    }
  }

  const bool is_encrypted = p_dev_rec->sec_rec.is_le_device_encrypted() ||
                            p_dev_rec->sec_rec.is_device_encrypted();
  BTM_LogHistory(
      kBtmLogTag,
      (transport == BT_TRANSPORT_LE) ? p_dev_rec->ble.pseudo_addr
                                     : p_dev_rec->bd_addr,
      (status == HCI_SUCCESS) ? "Encryption success" : "Encryption failed",
      base::StringPrintf("status:%s transport:%s is_encrypted:%c",
                         hci_status_code_text(status).c_str(),
                         bt_transport_text(transport).c_str(),
                         is_encrypted ? 'T' : 'F'));

  log::debug("after update p_dev_rec->sec_rec.sec_flags=0x{:x}",
             p_dev_rec->sec_rec.sec_flags);

  btm_sec_check_pending_enc_req(p_dev_rec, transport, encr_enable);

  if (transport == BT_TRANSPORT_LE) {
    if (status == HCI_ERR_KEY_MISSING || status == HCI_ERR_AUTH_FAILURE ||
        status == HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE) {
      p_dev_rec->sec_rec.sec_flags &= ~(BTM_SEC_LE_LINK_KEY_KNOWN);
      p_dev_rec->sec_rec.ble_keys.key_type = BTM_LE_KEY_NONE;
    }
    p_dev_rec->sec_rec.sec_status = status;
    btm_ble_link_encrypted(p_dev_rec->ble.pseudo_addr, encr_enable);

    if (status == HCI_ERR_KEY_MISSING) {
      log::info("Remote key missing - will report");
      bta_dm_remote_key_missing(p_dev_rec->ble.pseudo_addr);
      return;
    }

    return;
  } else {
    /* BR/EDR connection, update the encryption key size to be 16 as always */
    p_dev_rec->sec_rec.enc_key_size = 16;
  }

  log::debug("in new_encr_key_256 is {}",
             p_dev_rec->sec_rec.new_encryption_key_is_p256);

  if ((status == HCI_SUCCESS) && encr_enable &&
      (p_dev_rec->hci_handle == handle)) {
    /* if BR key is temporary no need for LE LTK derivation */
    bool derive_ltk = true;
    if (p_dev_rec->sec_rec.rmt_auth_req == BTM_AUTH_SP_NO &&
        btm_sec_cb.devcb.loc_auth_req == BTM_AUTH_SP_NO) {
      derive_ltk = false;
      log::verbose("BR key is temporary, skip derivation of LE LTK");
    }
    tHCI_ROLE role = HCI_ROLE_UNKNOWN;
    BTM_GetRole(p_dev_rec->bd_addr, &role);
    if (p_dev_rec->sec_rec.new_encryption_key_is_p256) {
      if (btm_sec_use_smp_br_chnl(p_dev_rec) && role == HCI_ROLE_CENTRAL &&
          /* if LE key is not known, do deriving */
          (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_LINK_KEY_KNOWN) ||
           /* or BR key is higher security than existing LE keys */
           (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_LINK_KEY_AUTHED) &&
            (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_AUTHED))) &&
          derive_ltk) {
        /* BR/EDR is encrypted with LK that can be used to derive LE LTK */
        p_dev_rec->sec_rec.new_encryption_key_is_p256 = false;

        log::verbose("start SM over BR/EDR");
        SMP_BR_PairWith(p_dev_rec->bd_addr);
      }
    }
  }

  /* If this encryption was started by peer do not need to do anything */
  if (!p_dev_rec->sec_rec.is_security_state_bredr_encrypting()) {
    if (BTM_SEC_STATE_DELAY_FOR_ENC == p_dev_rec->sec_rec.sec_state) {
      p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
      log::verbose("clearing callback. p_dev_rec={}, p_callback={}",
                   fmt::ptr(p_dev_rec),
                   fmt::ptr(p_dev_rec->sec_rec.p_callback));
      p_dev_rec->sec_rec.p_callback = NULL;
      l2cu_resubmit_pending_sec_req(&p_dev_rec->bd_addr);
      return;
    } else if (!concurrentPeerAuthIsEnabled() &&
               p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING) {
      p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
      return;
    }
    if (!handleUnexpectedEncryptionChange()) {
      return;
    }
  }

  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
  /* If encryption setup failed, notify the waiting layer */
  if (status != HCI_SUCCESS) {
    btm_sec_dev_rec_cback_event(p_dev_rec, BTM_ERR_PROCESSING, false);
    return;
  }

  /* Encryption setup succeeded, execute the next security procedure, if any */
  tBTM_STATUS btm_status = btm_sec_execute_procedure(p_dev_rec);
  /* If there is no next procedure, or procedure failed to start, notify the
   * caller */
  if (static_cast<std::underlying_type_t<tHCI_STATUS>>(status) !=
      BTM_CMD_STARTED)
    btm_sec_dev_rec_cback_event(p_dev_rec, btm_status, false);
}

constexpr uint8_t MIN_KEY_SIZE = 7;

static void read_encryption_key_size_complete_after_encryption_change(
    uint8_t status, uint16_t handle, uint8_t key_size) {
  if (status == HCI_ERR_INSUFFCIENT_SECURITY) {
    /* If remote device stop the encryption before we call "Read Encryption Key
     * Size", we might receive Insufficient Security, which means that link is
     * no longer encrypted. */
    log::info("encryption stopped on link:0x{:x}", handle);
    return;
  }

  if (status != HCI_SUCCESS) {
    log::error("disconnecting, status:0x{:x}", status);
    acl_disconnect_from_handle(handle, HCI_ERR_PEER_USER,
                               "stack::btu::btu_hcif::read_encryption_key_size_"
                               "complete_after_encryption_change Bad key size");
    return;
  }

  if (key_size < MIN_KEY_SIZE) {
    log::error(
        "encryption key too short, disconnecting. handle:0x{:x},key_size:{}",
        handle, key_size);

    acl_disconnect_from_handle(
        handle, HCI_ERR_HOST_REJECT_SECURITY,
        "stack::btu::btu_hcif::read_encryption_key_size_complete_after_"
        "encryption_change Key Too Short");
    return;
  }

  if (IS_FLAG_ENABLED(bluffs_mitigation)) {
    if (btm_sec_is_session_key_size_downgrade(handle, key_size)) {
      LOG_ERROR(
          "encryption key size lower than cached value, disconnecting. "
          "handle: 0x%x attempted key size: %d",
          handle, key_size);
      acl_disconnect_from_handle(
          handle, HCI_ERR_HOST_REJECT_SECURITY,
          "stack::btu::btu_hcif::read_encryption_key_size_complete_after_"
          "encryption_change Key Size Downgrade");
      return;
    }

    btm_sec_update_session_key_size(handle, key_size);
  }

  // good key size - succeed
  btm_acl_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                         1 /* enable */);
  btm_sec_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                         1 /* enable */);
}

// TODO: Remove
void smp_cancel_start_encryption_attempt();

/*******************************************************************************
 *
 * Function         btm_encryption_change_evt
 *
 * Description      Process event HCI_ENCRYPTION_CHANGE_EVT
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_encryption_change_evt(uint16_t handle, tHCI_STATUS status,
                                   uint8_t encr_enable) {
  if (IS_FLAG_ENABLED(bluffs_mitigation)) {
    if (status != HCI_SUCCESS || encr_enable == 0 ||
        BTM_IsBleConnection(handle) ||
        !bluetooth::shim::GetController()->IsSupported(
            bluetooth::hci::OpCode::READ_ENCRYPTION_KEY_SIZE)) {
      if (status == HCI_ERR_CONNECTION_TOUT) {
        smp_cancel_start_encryption_attempt();
        return;
      }

      btm_acl_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                             encr_enable);
      btm_sec_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                             encr_enable);
    } else {
      btsnd_hcic_read_encryption_key_size(
          handle,
          base::Bind(
              &read_encryption_key_size_complete_after_encryption_change));
    }
  } else {
    // This block added to ensure matching code flow with the bluffs_mitigation
    // flag off.  The entire block should be removed when the flag is.
    if (status != HCI_SUCCESS || encr_enable == 0 ||
        BTM_IsBleConnection(handle) ||
        !bluetooth::shim::GetController()->IsSupported(
            bluetooth::hci::OpCode::READ_ENCRYPTION_KEY_SIZE) ||
        // Skip encryption key size check when using set_min_encryption_key_size
        (bluetooth::common::init_flags::set_min_encryption_is_enabled() &&
         bluetooth::shim::GetController()->IsSupported(
             bluetooth::hci::OpCode::SET_MIN_ENCRYPTION_KEY_SIZE))) {
      if (status == HCI_ERR_CONNECTION_TOUT) {
        smp_cancel_start_encryption_attempt();
        return;
      }

      btm_acl_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                             encr_enable);
      btm_sec_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                             encr_enable);
    } else {
      btsnd_hcic_read_encryption_key_size(
          handle,
          base::Bind(
              &read_encryption_key_size_complete_after_encryption_change));
    }
  }
}
/*******************************************************************************
 *
 * Function         btm_sec_connect_after_reject_timeout
 *
 * Description      This function is used to re-initiate an outgoing ACL
 *                  connection in case the ACL connection for bonding failed,
 *                  e.g., because of the collision.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_sec_connect_after_reject_timeout(void* /* data */) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_sec_cb.p_collided_dev_rec;

  log::verbose("restarting ACL connection");
  btm_sec_cb.p_collided_dev_rec = 0;

  if (btm_sec_dd_create_conn(p_dev_rec) != BTM_CMD_STARTED) {
    log::warn("Security Manager: failed to start connection");

    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);

    NotifyBondingChange(*p_dev_rec, HCI_ERR_MEMORY_FULL);
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_connected
 *
 * Description      This function is called when a (BR/EDR) ACL connection to
 *                  the peer device is established
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_connected(const RawAddress& bda, uint16_t handle,
                       tHCI_STATUS status, uint8_t enc_mode,
                       tHCI_ROLE assigned_role) {
  tBTM_STATUS res;
  bool is_pairing_device = false;
  bool addr_matched;
  uint8_t bit_shift = 0;

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bda);
  if (!p_dev_rec) {
    log::debug(
        "Connected to new device state:{} handle:0x{:04x} status:{} "
        "enc_mode:{} bda:{}",
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state), handle,
        hci_status_code_text(status).c_str(), enc_mode,
        ADDRESS_TO_LOGGABLE_CSTR(bda));

    if (status == HCI_SUCCESS) {
      p_dev_rec = btm_sec_alloc_dev(bda);
      log::debug("Allocated new device record for new connection peer:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(bda));
    } else {
      /* If the device matches with stored paring address
       * reset the paring state to idle */
      if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
          btm_sec_cb.pairing_bda == bda) {
        log::warn("Connection failed during bonding attempt peer:{} reason:{}",
                  ADDRESS_TO_LOGGABLE_CSTR(bda),
                  hci_error_code_text(status).c_str());
        btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      }

      log::debug("Ignoring failed device connection peer:{} reason:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(bda),
                 hci_error_code_text(status).c_str());
      return;
    }
  } else {
    log::debug(
        "Connected to known device state:{} handle:0x{:04x} status:{} "
        "enc_mode:{} bda:{} RName:{}",
        tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state), handle,
        hci_status_code_text(status).c_str(), enc_mode,
        ADDRESS_TO_LOGGABLE_CSTR(bda),
        reinterpret_cast<char const*>(p_dev_rec->sec_bd_name));

    bit_shift = (handle == p_dev_rec->ble_hci_handle) ? 8 : 0;
    /* Update the timestamp for this device */
    p_dev_rec->timestamp = btm_sec_cb.dev_rec_count++;
    if (p_dev_rec->sm4 & BTM_SM4_CONN_PEND) {
      if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
          (btm_sec_cb.pairing_bda == p_dev_rec->bd_addr) &&
          (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD)) {
        /* if incoming acl connection failed while pairing, then try to connect
         * and continue */
        /* Motorola S9 disconnects without asking pin code */
        if ((status != HCI_SUCCESS) &&
            (btm_sec_cb.pairing_state == BTM_PAIR_STATE_WAIT_PIN_REQ)) {
          log::warn(
              "Security Manager: btm_sec_connected: incoming connection failed "
              "without asking PIN");

          p_dev_rec->sm4 &= ~BTM_SM4_CONN_PEND;
          if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_NAME_KNOWN) {
            /* remote device name is known, start a new acl connection */

            /* Start timer with 0 to initiate connection with new LCB */
            /* because L2CAP will delete current LCB with this event  */
            btm_sec_cb.p_collided_dev_rec = p_dev_rec;
            alarm_set_on_mloop(btm_sec_cb.sec_collision_timer, 0,
                               btm_sec_connect_after_reject_timeout, NULL);
          } else {
            /* remote device name is unknowm, start getting remote name first */

            btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_GET_REM_NAME);
            if (BTM_ReadRemoteDeviceName(p_dev_rec->bd_addr, NULL,
                                         BT_TRANSPORT_BR_EDR) !=
                BTM_CMD_STARTED) {
              log::error("cannot read remote name");
              btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
            }
          }
          return;
        } else {
          /* tell L2CAP it's a bonding connection. */
          l2cu_update_lcb_4_bonding(p_dev_rec->bd_addr, true);
        }
      }
      /* always clear the pending flag */
      p_dev_rec->sm4 &= ~BTM_SM4_CONN_PEND;
    }
  }

  p_dev_rec->device_type |= BT_DEVICE_TYPE_BREDR;

  addr_matched = (btm_sec_cb.pairing_bda == bda);

  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) && addr_matched) {
    /* if we rejected incoming connection from bonding device */
    if ((status == HCI_ERR_HOST_REJECT_DEVICE) &&
        (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_REJECTED_CONNECT)) {
      log::warn(
          "Security Manager: btm_sec_connected: HCI_Conn_Comp Flags:0x{:04x}, "
          "sm4: 0x{:x}",
          btm_sec_cb.pairing_flags, p_dev_rec->sm4);

      btm_sec_cb.pairing_flags &= ~BTM_PAIR_FLAGS_REJECTED_CONNECT;
      if (BTM_SEC_IS_SM4_UNKNOWN(p_dev_rec->sm4)) {
        /* Try again: RNR when no ACL causes HCI_RMT_HOST_SUP_FEAT_NOTIFY_EVT */
        btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_GET_REM_NAME);
        if (BTM_ReadRemoteDeviceName(bda, NULL, BT_TRANSPORT_BR_EDR) !=
            BTM_CMD_STARTED) {
          log::error("cannot read remote name");
          btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
        }
        return;
      }

      /* if we already have pin code */
      if (btm_sec_cb.pairing_state != BTM_PAIR_STATE_WAIT_LOCAL_PIN) {
        /* Start timer with 0 to initiate connection with new LCB */
        /* because L2CAP will delete current LCB with this event  */
        btm_sec_cb.p_collided_dev_rec = p_dev_rec;
        alarm_set_on_mloop(btm_sec_cb.sec_collision_timer, 0,
                           btm_sec_connect_after_reject_timeout, NULL);
      }

      return;
    }
    /* wait for incoming connection without resetting pairing state */
    else if (status == HCI_ERR_CONNECTION_EXISTS) {
      log::warn(
          "Security Manager: btm_sec_connected: Wait for incoming connection");
      return;
    }

    is_pairing_device = true;
  }

  /* If connection was made to do bonding restore link security if changed */
  btm_restore_mode();

  /* if connection fails during pin request, notify application */
  if (status != HCI_SUCCESS) {
    /* If connection failed because of during pairing, need to tell user */
    if (is_pairing_device) {
      p_dev_rec->sec_rec.security_required &= ~BTM_SEC_OUT_AUTHENTICATE;
      p_dev_rec->sec_rec.sec_flags &=
          ~((BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_LINK_KEY_AUTHED) << bit_shift);
      log::verbose("security_required:{:x} ",
                   p_dev_rec->sec_rec.security_required);

      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);

      /* We need to notify host that the key is not known any more */
      NotifyBondingChange(*p_dev_rec, status);
    }
    /*
        Do not send authentication failure, if following conditions hold good
         1.  BTM Sec Pairing state is idle
         2.  Link key for the remote device is present.
         3.  Remote is SSP capable.
     */
    else if ((p_dev_rec->sec_rec.link_key_type <= BTM_LKEY_TYPE_REMOTE_UNIT) &&
             (((status == HCI_ERR_AUTH_FAILURE) ||
               (status == HCI_ERR_KEY_MISSING) ||
               (status == HCI_ERR_HOST_REJECT_SECURITY) ||
               (status == HCI_ERR_PAIRING_NOT_ALLOWED) ||
               (status == HCI_ERR_UNIT_KEY_USED) ||
               (status == HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED) ||
               (status == HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE) ||
               (status == HCI_ERR_REPEATED_ATTEMPTS)))) {
      p_dev_rec->sec_rec.security_required &= ~BTM_SEC_OUT_AUTHENTICATE;
      p_dev_rec->sec_rec.sec_flags &= ~(BTM_SEC_LE_LINK_KEY_KNOWN << bit_shift);

#ifdef BRCM_NOT_4_BTE
      /* If we rejected pairing, pass this special result code */
      if (acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY)) {
        status = HCI_ERR_HOST_REJECT_SECURITY;
      }
#endif

      /* We need to notify host that the key is not known any more */
      NotifyBondingChange(*p_dev_rec, status);
    }

    /* p_auth_complete_callback might have freed the p_dev_rec, ensure it exists
     * before accessing */
    p_dev_rec = btm_find_dev(bda);
    if (!p_dev_rec) {
      /* Don't callback when device security record was removed */
      log::debug(
          "device security record associated with this bda has been removed! "
          "bda={}, do not callback",
          ADDRESS_TO_LOGGABLE_CSTR(bda));
      return;
    }

    if (status == HCI_ERR_CONNECTION_TOUT ||
        status == HCI_ERR_LMP_RESPONSE_TIMEOUT ||
        status == HCI_ERR_UNSPECIFIED || status == HCI_ERR_PAGE_TIMEOUT)
      btm_sec_dev_rec_cback_event(p_dev_rec, BTM_DEVICE_TIMEOUT, false);
    else
      btm_sec_dev_rec_cback_event(p_dev_rec, BTM_ERR_PROCESSING, false);

    return;
  }

  /*
   * The device is still in the pairing state machine and we now have the
   * link key.  If we have not sent the link key, send it now and remove
   * the authenticate requirement bit.  Reset the pairing state machine
   * and inform l2cap if the directed bonding was initiated.
   */
  if (is_pairing_device &&
      (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN)) {
    if (p_dev_rec->sec_rec.link_key_not_sent) {
      p_dev_rec->sec_rec.link_key_not_sent = false;
      btm_send_link_key_notif(p_dev_rec);
    }

    p_dev_rec->sec_rec.security_required &= ~BTM_SEC_OUT_AUTHENTICATE;

    /* remember flag before it is initialized */
    const bool is_pair_flags_we_started_dd =
        btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD;
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);

    if (is_pair_flags_we_started_dd) {
      /* Let l2cap start bond timer */
      l2cu_update_lcb_4_bonding(p_dev_rec->bd_addr, true);
    }
    log::info("Connection complete during pairing process peer:{}",
              ADDRESS_TO_LOGGABLE_CSTR(bda));
    BTM_LogHistory(kBtmLogTag, bda, "Dedicated bonding",
                   base::StringPrintf("Initiated:%c pairing_flag:0x%02x",
                                      (is_pair_flags_we_started_dd) ? 'T' : 'F',
                                      p_dev_rec->sec_rec.sec_flags));
  }

  p_dev_rec->hci_handle = handle;
  btm_acl_created(bda, handle, assigned_role, BT_TRANSPORT_BR_EDR);

  /* role may not be correct here, it will be updated by l2cap, but we need to
   */
  /* notify btm_acl that link is up, so starting of rmt name request will not */
  /* set paging flag up */
  /* whatever is in btm_establish_continue() without reporting the
   * BTM_BL_CONN_EVT event */
  /* For now there are a some devices that do not like sending */
  /* commands events and data at the same time. */
  /* Set the packet types to the default allowed by the device */
  btm_set_packet_types_from_address(bda, acl_get_supported_packet_types());

  /* Initialize security flags.  We need to do that because some            */
  /* authorization complete could have come after the connection is dropped */
  /* and that would set wrong flag that link has been authorized already    */
  p_dev_rec->sec_rec.sec_flags &=
      ~((BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED | BTM_SEC_ROLE_SWITCHED)
        << bit_shift);

  if (enc_mode != HCI_ENCRYPT_MODE_DISABLED)
    p_dev_rec->sec_rec.sec_flags |=
        ((BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED) << bit_shift);

  if (p_dev_rec->sec_rec.pin_code_length >= 16 ||
      p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB ||
      p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256) {
    p_dev_rec->sec_rec.sec_flags |= (BTM_SEC_16_DIGIT_PIN_AUTHED << bit_shift);
  }

  /* After connection is established we perform security if we do not know */
  /* the name, or if we are originator because some procedure can have */
  /* been scheduled while connection was down */
  log::debug("Is connection locally initiated:{}",
             logbool(p_dev_rec->is_originator).c_str());
  if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_NAME_KNOWN) ||
      p_dev_rec->is_originator) {
    res = btm_sec_execute_procedure(p_dev_rec);
    if (res != BTM_CMD_STARTED)
      btm_sec_dev_rec_cback_event(p_dev_rec, res, false);
  }
}

tBTM_STATUS btm_sec_disconnect(uint16_t handle, tHCI_STATUS reason,
                               std::string comment) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);

  /* In some weird race condition we may not have a record */
  if (!p_dev_rec) {
    acl_disconnect_from_handle(
        handle, reason,
        "stack::btm::btm_sec::btm_sec_disconnect No security record");
    return (BTM_SUCCESS);
  }

  /* If we are in the process of bonding we need to tell client that auth failed
   */
  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (btm_sec_cb.pairing_bda == p_dev_rec->bd_addr) &&
      (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD)) {
    /* we are currently doing bonding.  Link will be disconnected when done */
    btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_DISC_WHEN_DONE;
    return (BTM_BUSY);
  }

  return btm_sec_send_hci_disconnect(p_dev_rec, reason, handle, comment);
}

void btm_sec_disconnected(uint16_t handle, tHCI_REASON reason,
                          std::string comment) {
  if ((reason != HCI_ERR_CONN_CAUSE_LOCAL_HOST) &&
      (reason != HCI_ERR_PEER_USER) && (reason != HCI_ERR_REMOTE_POWER_OFF)) {
    log::warn("Got uncommon disconnection reason:{} handle:0x{:04x} comment:{}",
              hci_error_code_text(reason).c_str(), handle, comment.c_str());
  }

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);
  if (p_dev_rec == nullptr) {
    log::warn("Got disconnect for unknown device record handle:0x{:04x}",
              handle);
    return;
  }

  const tBT_TRANSPORT transport =
      (handle == p_dev_rec->hci_handle) ? BT_TRANSPORT_BR_EDR : BT_TRANSPORT_LE;

  /* clear unused flags */
  p_dev_rec->sm4 &= BTM_SM4_TRUE;

  /* If we are in the process of bonding we need to tell client that auth failed
   */
  const uint8_t old_pairing_flags = btm_sec_cb.pairing_flags;
  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (btm_sec_cb.pairing_bda == p_dev_rec->bd_addr)) {
    log::debug("Disconnected while pairing process active handle:0x{:04x}",
               handle);
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
    p_dev_rec->sec_rec.sec_flags &= ~BTM_SEC_LINK_KEY_KNOWN;

    /* If the disconnection reason is REPEATED_ATTEMPTS,
       send this error message to complete callback function
       to display the error message of Repeated attempts.
       All others, send HCI_ERR_AUTH_FAILURE. */
    tHCI_STATUS status = HCI_ERR_AUTH_FAILURE;
    if (reason == HCI_ERR_REPEATED_ATTEMPTS) {
      status = HCI_ERR_REPEATED_ATTEMPTS;
    } else if (old_pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD) {
      status = HCI_ERR_HOST_REJECT_SECURITY;
    } else {
      DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(p_dev_rec->bd_addr,
                                         IOT_CONF_KEY_GAP_DISC_AUTHFAIL_COUNT);
    }

    NotifyBondingChange(*p_dev_rec, status);

    p_dev_rec = btm_find_dev_by_handle(handle);
    if (p_dev_rec == nullptr) {
      // |btm_sec_cb.api.p_auth_complete_callback| may cause |p_dev_rec| to be
      // deallocated.
      log::warn("Device record was deallocated after user callback");
      return;
    }
  }

  log::debug(
      "Disconnection complete device:{} name:{} state:{} reason:{} "
      "sec_req:{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr),
      reinterpret_cast<char const*>(p_dev_rec->sec_bd_name),
      tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state),
      hci_reason_code_text(reason).c_str(),
      p_dev_rec->sec_rec.security_required);

  // TODO Should this be gated by the transport check below ?
  btm_ble_update_mode_operation(HCI_ROLE_UNKNOWN, &p_dev_rec->bd_addr,
                                HCI_SUCCESS);
  /* see sec_flags processing in btm_acl_removed */

  if (transport == BT_TRANSPORT_LE) {
    p_dev_rec->ble_hci_handle = HCI_INVALID_HANDLE;
    p_dev_rec->sec_rec.sec_flags &=
        ~(BTM_SEC_LE_AUTHENTICATED | BTM_SEC_LE_ENCRYPTED |
          BTM_SEC_ROLE_SWITCHED);
    p_dev_rec->sec_rec.enc_key_size = 0;
    p_dev_rec->suggested_tx_octets = 0;

    if ((p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_LINK_KEY_KNOWN) == 0) {
      p_dev_rec->sec_rec.sec_flags &=
          ~(BTM_SEC_LE_LINK_KEY_AUTHED | BTM_SEC_LE_AUTHENTICATED);
    }
  } else {
    p_dev_rec->hci_handle = HCI_INVALID_HANDLE;
    p_dev_rec->sec_rec.sec_flags &=
        ~(BTM_SEC_AUTHENTICATED | BTM_SEC_ENCRYPTED | BTM_SEC_ROLE_SWITCHED |
          BTM_SEC_16_DIGIT_PIN_AUTHED);

    // Remove temporary key.
    if (p_dev_rec->sec_rec.bond_type == BOND_TYPE_TEMPORARY)
      p_dev_rec->sec_rec.sec_flags &= ~(BTM_SEC_LINK_KEY_KNOWN);
  }

  /* Some devices hardcode sample LTK value from spec, instead of generating
   * one. Treat such devices as insecure, and remove such bonds on
   * disconnection.
   */
  if (is_sample_ltk(p_dev_rec->sec_rec.ble_keys.pltk)) {
    log::info("removing bond to device that used sample LTK: {}",
              ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));

    bta_dm_remove_device(p_dev_rec->bd_addr);
    return;
  }

  if (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_DISCONNECTING_BOTH) {
    log::debug("Waiting for other transport to disconnect current:{}",
               bt_transport_text(transport).c_str());
    p_dev_rec->sec_rec.sec_state = (transport == BT_TRANSPORT_LE)
                                       ? BTM_SEC_STATE_DISCONNECTING
                                       : BTM_SEC_STATE_DISCONNECTING_BLE;
    return;
  }
  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
  p_dev_rec->sec_rec.security_required = BTM_SEC_NONE;

  if (p_dev_rec->sec_rec.p_callback != nullptr) {
    tBTM_SEC_CALLBACK* p_callback = p_dev_rec->sec_rec.p_callback;
    /* when the peer device time out the authentication before
       we do, this call back must be reset here */
    p_dev_rec->sec_rec.p_callback = nullptr;
    (*p_callback)(&p_dev_rec->bd_addr, transport, p_dev_rec->sec_rec.p_ref_data,
                  BTM_ERR_PROCESSING);
    log::debug("Cleaned up pending security state device:{} transport:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr),
               bt_transport_text(transport).c_str());
  }
}

void btm_sec_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                          tHCI_ROLE new_role) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);

  if (p_dev_rec == nullptr || hci_status != HCI_SUCCESS) {
    return;
  }
  if (new_role == HCI_ROLE_CENTRAL && btm_dev_authenticated(p_dev_rec) &&
      !btm_dev_encrypted(p_dev_rec)) {
    BTM_SetEncryption(p_dev_rec->bd_addr, BT_TRANSPORT_BR_EDR, NULL, NULL,
                      BTM_BLE_SEC_NONE);
  }
}

static void read_encryption_key_size_complete_after_key_refresh(
    uint8_t status, uint16_t handle, uint8_t key_size) {
  if (status == HCI_ERR_INSUFFCIENT_SECURITY) {
    /* If remote device stop the encryption before we call "Read Encryption Key
     * Size", we might receive Insufficient Security, which means that link is
     * no longer encrypted. */
    log::info("encryption stopped on link: 0x{:x} ", handle);
    return;
  }

  if (status != HCI_SUCCESS) {
    log::info("disconnecting, status: 0x{:x}", status);
    acl_disconnect_from_handle(handle, HCI_ERR_PEER_USER,
                               "stack::btu_hcif Key size fail");
    return;
  }

  if (key_size < MIN_KEY_SIZE) {
    log::error(
        "encryption key too short, disconnecting. handle: 0x{:x} key_size {}",
        handle, key_size);

    acl_disconnect_from_handle(handle, HCI_ERR_HOST_REJECT_SECURITY,
                               "stack::btu::btu_hcif::read_encryption_key_size_"
                               "complete_after_key_refresh Key size too small");
    return;
  }

  btm_sec_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                         1 /* enc_enable */);
}

void btm_sec_encryption_key_refresh_complete(uint16_t handle,
                                             tHCI_STATUS status) {
  if (status != HCI_SUCCESS || BTM_IsBleConnection(handle) ||
      // Skip encryption key size check when using set_min_encryption_key_size
      bluetooth::shim::GetController()->IsSupported(
          bluetooth::hci::OpCode::SET_MIN_ENCRYPTION_KEY_SIZE)) {
    btm_sec_encrypt_change(handle, static_cast<tHCI_STATUS>(status),
                           (status == HCI_SUCCESS) ? 1 : 0);
  } else {
    btsnd_hcic_read_encryption_key_size(
        handle,
        base::Bind(&read_encryption_key_size_complete_after_key_refresh));
  }
}

/** This function is called when a new connection link key is generated */
void btm_sec_link_key_notification(const RawAddress& p_bda,
                                   const Octet16& link_key, uint8_t key_type) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(p_bda);
  bool we_are_bonding = false;
  bool ltk_derived_lk = false;

  log::debug("New link key generated device:{} key_type:{}",
             ADDRESS_TO_LOGGABLE_CSTR(p_bda), key_type);

  if ((key_type >= BTM_LTK_DERIVED_LKEY_OFFSET + BTM_LKEY_TYPE_COMBINATION) &&
      (key_type <=
       BTM_LTK_DERIVED_LKEY_OFFSET + BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
    ltk_derived_lk = true;
    key_type -= BTM_LTK_DERIVED_LKEY_OFFSET;
  }
  /* If connection was made to do bonding restore link security if changed */
  btm_restore_mode();

  if (key_type != BTM_LKEY_TYPE_CHANGED_COMB)
    p_dev_rec->sec_rec.link_key_type = key_type;

  p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LINK_KEY_KNOWN;

  /*
   * Until this point in time, we do not know if MITM was enabled, hence we
   * add the extended security flag here.
   */
  if (p_dev_rec->sec_rec.pin_code_length >= 16 ||
      p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB ||
      p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256) {
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_LINK_KEY_AUTHED;
    p_dev_rec->sec_rec.sec_flags |= BTM_SEC_16_DIGIT_PIN_AUTHED;
  }

  /* BR/EDR connection, update the encryption key size to be 16 as always */
  p_dev_rec->sec_rec.enc_key_size = 16;
  p_dev_rec->sec_rec.link_key = link_key;

  if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) &&
      (btm_sec_cb.pairing_bda == p_bda)) {
    if (btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_WE_STARTED_DD)
      we_are_bonding = true;
    else
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
  }

  /* save LTK derived LK no matter what */
  if (ltk_derived_lk) {
    if (btm_sec_cb.api.p_link_key_callback) {
      log::verbose("Save LTK derived LK (key_type = {})",
                   p_dev_rec->sec_rec.link_key_type);
      (*btm_sec_cb.api.p_link_key_callback)(
          p_bda, p_dev_rec->dev_class, p_dev_rec->sec_bd_name, link_key,
          p_dev_rec->sec_rec.link_key_type, true /* is_ctkd */);
    }
  } else {
    if ((p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_UNAUTH_COMB_P_256) ||
        (p_dev_rec->sec_rec.link_key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
      p_dev_rec->sec_rec.new_encryption_key_is_p256 = true;
      log::verbose("set new_encr_key_256 to {}",
                   p_dev_rec->sec_rec.new_encryption_key_is_p256);
    }
  }

  if (IS_FLAG_ENABLED(bluffs_mitigation) &&
      p_dev_rec->sec_rec.is_bond_type_persistent() &&
      (p_dev_rec->is_device_type_br_edr() ||
       p_dev_rec->is_device_type_dual_mode())) {
    btm_sec_store_device_sc_support(p_dev_rec->get_br_edr_hci_handle(),
                                    p_dev_rec->SupportsSecureConnections());
  }

  /* If name is not known at this point delay calling callback until the name is
   */
  /* resolved. Unless it is a HID Device and we really need to send all link
   * keys. */
  if ((!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_NAME_KNOWN) &&
       ((p_dev_rec->dev_class[1] & BTM_COD_MAJOR_CLASS_MASK) !=
        BTM_COD_MAJOR_PERIPHERAL)) &&
      !ltk_derived_lk) {
    log::verbose("Delayed BDA: {}, Type: {}", ADDRESS_TO_LOGGABLE_CSTR(p_bda),
                 key_type);

    p_dev_rec->sec_rec.link_key_not_sent = true;

    /* If it is for bonding nothing else will follow, so we need to start name
     * resolution */
    if (we_are_bonding) {
      SendRemoteNameRequest(p_bda);
    }

    log::verbose("rmt_io_caps:{}, sec_flags:x{:x}, dev_class[1]:x{:02x}",
                 p_dev_rec->sec_rec.rmt_io_caps, p_dev_rec->sec_rec.sec_flags,
                 p_dev_rec->dev_class[1]);
    return;
  }

/* We will save link key only if the user authorized it - BTE report link key in
 * all cases */
#ifdef BRCM_NONE_BTE
  if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_AUTHED)
#endif
  {
    if (btm_sec_cb.api.p_link_key_callback) {
      if (ltk_derived_lk) {
        log::verbose(
            "btm_sec_link_key_notification()  LTK derived LK is saved already "
            "(key_type = {})",
            p_dev_rec->sec_rec.link_key_type);
      } else {
        (*btm_sec_cb.api.p_link_key_callback)(
            p_bda, p_dev_rec->dev_class, p_dev_rec->sec_bd_name, link_key,
            p_dev_rec->sec_rec.link_key_type, false /* is_ctkd */);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_link_key_request
 *
 * Description      This function is called when controller requests link key
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
void btm_sec_link_key_request(const RawAddress bda) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(bda);

  log::verbose("bda: {}", ADDRESS_TO_LOGGABLE_CSTR(bda));
  if (!concurrentPeerAuthIsEnabled()) {
    p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_AUTHENTICATING;
  }

  if ((btm_sec_cb.pairing_state == BTM_PAIR_STATE_WAIT_PIN_REQ) &&
      (btm_sec_cb.collision_start_time != 0) &&
      (btm_sec_cb.p_collided_dev_rec->bd_addr == bda)) {
    log::verbose(
        "btm_sec_link_key_request() rejecting link key req State: {} "
        "START_TIMEOUT : {}",
        btm_sec_cb.pairing_state, btm_sec_cb.collision_start_time);
    btsnd_hcic_link_key_neg_reply(bda);
    return;
  }
  if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN) {
    btsnd_hcic_link_key_req_reply(bda, p_dev_rec->sec_rec.link_key);
    return;
  }

  /* Notify L2CAP to increase timeout */
  l2c_pin_code_request(bda);

  /* The link key is not in the database and it is not known to the manager */
  btsnd_hcic_link_key_neg_reply(bda);
}

/*******************************************************************************
 *
 * Function         btm_sec_pairing_timeout
 *
 * Description      This function is called when host does not provide PIN
 *                  within requested time
 *
 * Returns          Pointer to the TLE struct
 *
 ******************************************************************************/
static void btm_sec_pairing_timeout(void* /* data */) {
  tBTM_SEC_CB* p_cb = &btm_sec_cb;
  tBTM_SEC_DEV_REC* p_dev_rec;
  tBTM_AUTH_REQ auth_req = (btm_sec_cb.devcb.loc_io_caps == BTM_IO_CAP_NONE)
                               ? BTM_AUTH_AP_NO
                               : BTM_AUTH_AP_YES;
  BD_NAME name;

  p_dev_rec = btm_find_dev(p_cb->pairing_bda);

  log::verbose("State: {}   Flags: {}",
               tBTM_SEC_CB::btm_pair_state_descr(p_cb->pairing_state),
               p_cb->pairing_flags);

  switch (p_cb->pairing_state) {
    case BTM_PAIR_STATE_WAIT_PIN_REQ:
      btm_sec_bond_cancel_complete();
      break;

    case BTM_PAIR_STATE_WAIT_LOCAL_PIN:
      if ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_PRE_FETCH_PIN) == 0)
        btsnd_hcic_pin_code_neg_reply(p_cb->pairing_bda);
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      /* We need to notify the UI that no longer need the PIN */
      if (btm_sec_cb.api.p_auth_complete_callback) {
        if (p_dev_rec == NULL) {
          name[0] = 0;
          (*btm_sec_cb.api.p_auth_complete_callback)(
              p_cb->pairing_bda, kDevClassEmpty, name, HCI_ERR_CONNECTION_TOUT);
        } else
          NotifyBondingChange(*p_dev_rec, HCI_ERR_CONNECTION_TOUT);
      }
      break;

    case BTM_PAIR_STATE_WAIT_NUMERIC_CONFIRM:
      btsnd_hcic_user_conf_reply(p_cb->pairing_bda, false);
      /* btm_sec_cb.change_pairing_state (BTM_PAIR_STATE_IDLE); */
      break;

    case BTM_PAIR_STATE_KEY_ENTRY:
      if (btm_sec_cb.devcb.loc_io_caps != BTM_IO_CAP_NONE) {
        btsnd_hcic_user_passkey_neg_reply(p_cb->pairing_bda);
      } else {
        btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      }
      break;

    case BTM_PAIR_STATE_WAIT_LOCAL_IOCAPS:
      // TODO(optedoblivion): Inject OOB_DATA_PRESENT Flag
      btsnd_hcic_io_cap_req_reply(p_cb->pairing_bda,
                                  btm_sec_cb.devcb.loc_io_caps, BTM_OOB_NONE,
                                  auth_req);
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      break;

    case BTM_PAIR_STATE_WAIT_LOCAL_OOB_RSP:
      btsnd_hcic_rem_oob_neg_reply(p_cb->pairing_bda);
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      break;

    case BTM_PAIR_STATE_WAIT_DISCONNECT:
      /* simple pairing failed. Started a 1-sec timer at simple pairing
       * complete.
       * now it's time to tear down the ACL link*/
      if (p_dev_rec == NULL) {
        log::error("BTM_PAIR_STATE_WAIT_DISCONNECT unknown BDA: {}",
                   ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
        break;
      }
      btm_sec_send_hci_disconnect(
          p_dev_rec, HCI_ERR_AUTH_FAILURE, p_dev_rec->hci_handle,
          "stack::btm::btm_sec::btm_sec_pairing_timeout");
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      break;

    case BTM_PAIR_STATE_WAIT_AUTH_COMPLETE:
    case BTM_PAIR_STATE_GET_REM_NAME:
      /* We need to notify the UI that timeout has happened while waiting for
       * authentication*/
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      if (btm_sec_cb.api.p_auth_complete_callback) {
        if (p_dev_rec == NULL) {
          name[0] = 0;
          (*btm_sec_cb.api.p_auth_complete_callback)(
              p_cb->pairing_bda, kDevClassEmpty, name, HCI_ERR_CONNECTION_TOUT);
        } else {
          NotifyBondingChange(*p_dev_rec, HCI_ERR_CONNECTION_TOUT);
        }
      }
      break;

    default:
      log::warn("not processed state: {}",
                tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state));
      btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_IDLE);
      break;
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_pin_code_request
 *
 * Description      This function is called when controller requests PIN code
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
void btm_sec_pin_code_request(const RawAddress p_bda) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  tBTM_SEC_CB* p_cb = &btm_sec_cb;

  /* Tell L2CAP that there was a PIN code request,  */
  /* it may need to stretch timeouts                */
  l2c_pin_code_request(p_bda);

  log::debug("Controller requests PIN code device:{} state:{}",
             ADDRESS_TO_LOGGABLE_CSTR(p_bda),
             tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state));

  RawAddress local_bd_addr = *controller_get_interface()->get_address();
  if (p_bda == local_bd_addr) {
    btsnd_hcic_pin_code_neg_reply(p_bda);
    return;
  }

  if (btm_sec_cb.pairing_state != BTM_PAIR_STATE_IDLE) {
    if ((p_bda == btm_sec_cb.pairing_bda) &&
        (btm_sec_cb.pairing_state == BTM_PAIR_STATE_WAIT_AUTH_COMPLETE)) {
      btsnd_hcic_pin_code_neg_reply(p_bda);
      return;
    } else if ((btm_sec_cb.pairing_state != BTM_PAIR_STATE_WAIT_PIN_REQ) ||
               p_bda != btm_sec_cb.pairing_bda) {
      log::warn("btm_sec_pin_code_request() rejected - state: {}",
                tBTM_SEC_CB::btm_pair_state_descr(btm_sec_cb.pairing_state));
      btsnd_hcic_pin_code_neg_reply(p_bda);
      return;
    }
  }

  p_dev_rec = btm_find_or_alloc_dev(p_bda);
  /* received PIN code request. must be non-sm4 */
  p_dev_rec->sm4 = BTM_SM4_KNOWN;

  if (btm_sec_cb.pairing_state == BTM_PAIR_STATE_IDLE) {
    btm_sec_cb.pairing_bda = p_bda;

    btm_sec_cb.pairing_flags = BTM_PAIR_FLAGS_PEER_STARTED_DD;
  }

  if (!p_cb->pairing_disabled && (p_cb->cfg.pin_type == HCI_PIN_TYPE_FIXED)) {
    log::verbose("btm_sec_pin_code_request fixed pin replying");
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);
    btsnd_hcic_pin_code_req_reply(p_bda, p_cb->cfg.pin_code_len,
                                  p_cb->cfg.pin_code);
    return;
  }

  /* Use the connecting device's CoD for the connection */
  if ((p_bda == p_cb->connecting_bda) &&
      (p_cb->connecting_dc[0] || p_cb->connecting_dc[1] ||
       p_cb->connecting_dc[2]))
    p_dev_rec->dev_class = p_cb->connecting_dc;

  /* We could have started connection after asking user for the PIN code */
  if (btm_sec_cb.pin_code_len != 0) {
    log::verbose("btm_sec_pin_code_request bonding sending reply");
    btsnd_hcic_pin_code_req_reply(p_bda, btm_sec_cb.pin_code_len,
                                  p_cb->pin_code);

    /* Mark that we forwarded received from the user PIN code */
    btm_sec_cb.pin_code_len = 0;

    /* We can change mode back right away, that other connection being
     * established */
    /* is not forced to be secure - found a FW issue, so we can not do this
    btm_restore_mode(); */

    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_AUTH_COMPLETE);
  }

  /* If pairing disabled OR (no PIN callback and not bonding) */
  /* OR we could not allocate entry in the database reject pairing request */
  else if (p_cb->pairing_disabled ||
           (p_cb->api.p_pin_callback == NULL)

           /* OR Microsoft keyboard can for some reason try to establish
            * connection
            */
           /*  the only thing we can do here is to shut it up.  Normally we will
              be originator */
           /*  for keyboard bonding */
           || (!p_dev_rec->IsLocallyInitiated() &&
               ((p_dev_rec->dev_class[1] & BTM_COD_MAJOR_CLASS_MASK) ==
                BTM_COD_MAJOR_PERIPHERAL) &&
               (p_dev_rec->dev_class[2] & BTM_COD_MINOR_KEYBOARD))) {
    log::warn(
        "btm_sec_pin_code_request(): Pairing disabled:{}; PIN callback:{}, Dev "
        "Rec:{}!",
        p_cb->pairing_disabled, fmt::ptr(p_cb->api.p_pin_callback),
        fmt::ptr(p_dev_rec));

    btsnd_hcic_pin_code_neg_reply(p_bda);
  }
  /* Notify upper layer of PIN request and start expiration timer */
  else {
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_LOCAL_PIN);
    /* Pin code request can not come at the same time as connection request */
    p_cb->connecting_bda = p_bda;
    p_cb->connecting_dc = p_dev_rec->dev_class;

    /* Check if the name is known */
    /* Even if name is not known we might not be able to get one */
    /* this is the case when we are already getting something from the */
    /* device, so HCI level is flow controlled */
    /* Also cannot send remote name request while paging, i.e. connection is not
     * completed */
    if (p_dev_rec->sec_rec.sec_flags & BTM_SEC_NAME_KNOWN) {
      log::verbose("btm_sec_pin_code_request going for callback");

      btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_PIN_REQD;
      if (p_cb->api.p_pin_callback) {
        (*p_cb->api.p_pin_callback)(
            p_bda, p_dev_rec->dev_class, p_dev_rec->sec_bd_name,
            (p_dev_rec->sec_rec.required_security_flags_for_pairing &
             BTM_SEC_IN_MIN_16_DIGIT_PIN));
      }
    } else {
      log::verbose("btm_sec_pin_code_request going for remote name");

      /* We received PIN code request for the device with unknown name */
      /* it is not user friendly just to ask for the PIN without name */
      /* try to get name at first */
      SendRemoteNameRequest(p_dev_rec->bd_addr);
    }
  }

  return;
}

/*******************************************************************************
 *
 * Function         btm_sec_update_clock_offset
 *
 * Description      This function is called to update clock offset
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_update_clock_offset(uint16_t handle, uint16_t clock_offset) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  tBTM_INQ_INFO* p_inq_info;

  p_dev_rec = btm_find_dev_by_handle(handle);
  if (p_dev_rec == NULL) return;

  p_dev_rec->clock_offset = clock_offset | BTM_CLOCK_OFFSET_VALID;

  p_inq_info = BTM_InqDbRead(p_dev_rec->bd_addr);
  if (p_inq_info == NULL) return;

  p_inq_info->results.clock_offset = clock_offset | BTM_CLOCK_OFFSET_VALID;
}

uint16_t BTM_GetClockOffset(const RawAddress& remote_bda) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(remote_bda);
  return (p_dev_rec) ? p_dev_rec->clock_offset : 0;
}

/******************************************************************
 * S T A T I C     F U N C T I O N S
 ******************************************************************/

/*******************************************************************************
 *
 * Function         btm_sec_execute_procedure
 *
 * Description      This function is called to start required security
 *                  procedure.  There is a case when multiplexing protocol
 *                  calls this function on the originating side, connection to
 *                  the peer will not be established.  This function in this
 *                  case performs only authorization.
 *
 * Returns          BTM_SUCCESS     - permission is granted
 *                  BTM_CMD_STARTED - in process
 *                  BTM_NO_RESOURCES  - permission declined
 *
 ******************************************************************************/
tBTM_STATUS btm_sec_execute_procedure(tBTM_SEC_DEV_REC* p_dev_rec) {
  CHECK(p_dev_rec != nullptr);
  log::debug(
      "security_required:0x{:x} security_flags:0x{:x} security_state:{}[{}]",
      p_dev_rec->sec_rec.security_required, p_dev_rec->sec_rec.sec_flags,
      security_state_text(
          static_cast<tSECURITY_STATE>(p_dev_rec->sec_rec.sec_state))
          .c_str(),
      p_dev_rec->sec_rec.sec_state);

  if (p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_IDLE &&
      p_dev_rec->sec_rec.sec_state != BTM_SEC_STATE_LE_ENCRYPTING) {
    log::info("No immediate action taken in busy state: {}",
              security_state_text(p_dev_rec->sec_rec.sec_state).c_str());
    return (BTM_CMD_STARTED);
  }

  /* If any security is required, get the name first */
  if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_NAME_KNOWN) &&
      (p_dev_rec->hci_handle != HCI_INVALID_HANDLE)) {
    log::debug("Security Manager: Start get name");
    if (!btm_sec_start_get_name(p_dev_rec)) {
      log::warn("Unable to start remote name request");
      return (BTM_NO_RESOURCES);
    }
    return (BTM_CMD_STARTED);
  }

  /* If connection is not authenticated and authentication is required */
  /* start authentication and return PENDING to the caller */
  if (p_dev_rec->hci_handle != HCI_INVALID_HANDLE) {
    bool start_auth = false;

    // Check link status of BR/EDR
    if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_AUTHENTICATED)) {
      if (p_dev_rec->IsLocallyInitiated()) {
        if (p_dev_rec->sec_rec.security_required &
            (BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_OUT_ENCRYPT)) {
          log::debug("Outgoing authentication/encryption Required");
          start_auth = true;
        }
      } else {
        if (p_dev_rec->sec_rec.security_required &
            (BTM_SEC_IN_AUTHENTICATE | BTM_SEC_IN_ENCRYPT)) {
          log::debug("Incoming authentication/encryption Required");
          start_auth = true;
        }
      }
    }

    if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_16_DIGIT_PIN_AUTHED)) {
      /*
       * We rely on BTM_SEC_16_DIGIT_PIN_AUTHED being set if MITM is in use,
       * as 16 DIGIT is only needed if MITM is not used. Unfortunately, the
       * BTM_SEC_AUTHENTICATED is used for both MITM and non-MITM
       * authenticated connections, hence we cannot distinguish here.
       */
      if (!p_dev_rec->IsLocallyInitiated()) {
        if (p_dev_rec->sec_rec.security_required &
            BTM_SEC_IN_MIN_16_DIGIT_PIN) {
          log::debug("BTM_SEC_IN_MIN_16_DIGIT_PIN Required");
          start_auth = true;
        }
      }
    }

    if (start_auth) {
      log::debug("Security Manager: Start authentication");

      /*
       * If we do have a link-key, but we end up here because we need an
       * upgrade, then clear the link-key known and authenticated flag before
       * restarting authentication.
       * WARNING: If the controller has link-key, it is optional and
       * recommended for the controller to send a Link_Key_Request.
       * In case we need an upgrade, the only alternative would be to delete
       * the existing link-key. That could lead to very bad user experience
       * or even IOP issues, if a reconnect causes a new connection that
       * requires an upgrade.
       */
      if ((p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_KNOWN) &&
          (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_16_DIGIT_PIN_AUTHED) &&
           (!p_dev_rec->IsLocallyInitiated() &&
            (p_dev_rec->sec_rec.security_required &
             BTM_SEC_IN_MIN_16_DIGIT_PIN)))) {
        p_dev_rec->sec_rec.sec_flags &=
            ~(BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_LINK_KEY_AUTHED |
              BTM_SEC_AUTHENTICATED);
      }

      btm_sec_wait_and_start_authentication(p_dev_rec);
      return (BTM_CMD_STARTED);
    }
  }

  /* If connection is not encrypted and encryption is required */
  /* start encryption and return PENDING to the caller */
  if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_ENCRYPTED) &&
      ((p_dev_rec->IsLocallyInitiated() &&
        (p_dev_rec->sec_rec.security_required & BTM_SEC_OUT_ENCRYPT)) ||
       (!p_dev_rec->IsLocallyInitiated() &&
        (p_dev_rec->sec_rec.security_required & BTM_SEC_IN_ENCRYPT))) &&
      (p_dev_rec->hci_handle != HCI_INVALID_HANDLE)) {
    log::verbose("Security Manager: Start encryption");

    btsnd_hcic_set_conn_encrypt(p_dev_rec->hci_handle, true);
    p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_ENCRYPTING;
    return (BTM_CMD_STARTED);
  } else {
    log::debug("Encryption not required");
  }

  if ((p_dev_rec->sec_rec.security_required & BTM_SEC_MODE4_LEVEL4) &&
      (p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256)) {
    log::verbose(
        "Security Manager: SC only service, but link key type is 0x{:02x} "
        "-security failure",
        p_dev_rec->sec_rec.link_key_type);
    return (BTM_FAILED_ON_SECURITY);
  }

  if (access_secure_service_from_temp_bond(
          p_dev_rec, p_dev_rec->IsLocallyInitiated(),
          p_dev_rec->sec_rec.security_required)) {
    log::error(
        "Trying to access a secure service from a temp bonding, rejecting");
    return (BTM_FAILED_ON_SECURITY);
  }

  /* All required  security procedures already established */
  p_dev_rec->sec_rec.security_required &=
      ~(BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_IN_AUTHENTICATE |
        BTM_SEC_OUT_ENCRYPT | BTM_SEC_IN_ENCRYPT);

  log::verbose("Security Manager: access granted");

  return (BTM_SUCCESS);
}

/*******************************************************************************
 *
 * Function         btm_sec_start_get_name
 *
 * Description      This function is called to start get name procedure
 *
 * Returns          true if started
 *
 ******************************************************************************/
static bool btm_sec_start_get_name(tBTM_SEC_DEV_REC* p_dev_rec) {
  if (!BTM_IsDeviceUp()) return false;

  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_GETTING_NAME;

  /* 0 and NULL are as timeout and callback params because they are not used in
   * security get name case */
  SendRemoteNameRequest(p_dev_rec->bd_addr);
  return true;
}

/*******************************************************************************
 *
 * Function         btm_sec_wait_and_start_authentication
 *
 * Description      This function is called to add an alarm to wait and start
 *                  authentication
 *
 ******************************************************************************/
static void btm_sec_wait_and_start_authentication(tBTM_SEC_DEV_REC* p_dev_rec) {
  auto addr = new RawAddress(p_dev_rec->bd_addr);
  int32_t delay_auth =
      osi_property_get_int32("bluetooth.btm.sec.delay_auth_ms.value", 0);

  /* Overwrite the system-wide authentication delay if device-specific
   * interoperability delay is needed. */
  if (interop_match_addr(INTEROP_DELAY_AUTH, addr)) {
    delay_auth = BTM_SEC_START_AUTH_DELAY;
  }

  bt_status_t status = do_in_main_thread_delayed(
      FROM_HERE, base::Bind(&btm_sec_auth_timer_timeout, addr),
      std::chrono::milliseconds(delay_auth));
  if (status != BT_STATUS_SUCCESS) {
    log::error("do_in_main_thread_delayed failed. directly calling");
    btm_sec_auth_timer_timeout(addr);
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_auth_timer_timeout
 *
 * Description      called after wait timeout to request authentication
 *
 ******************************************************************************/
static void btm_sec_auth_timer_timeout(void* data) {
  RawAddress* p_addr = (RawAddress*)data;
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(*p_addr);
  delete p_addr;
  if (p_dev_rec == NULL) {
    log::info("invalid device or not found");
  } else if (btm_dev_authenticated(p_dev_rec)) {
    log::info("device is already authenticated");
    if (p_dev_rec->sec_rec.p_callback) {
      (*p_dev_rec->sec_rec.p_callback)(&p_dev_rec->bd_addr, BT_TRANSPORT_BR_EDR,
                                       p_dev_rec->sec_rec.p_ref_data,
                                       BTM_SUCCESS);
    }
  } else if (p_dev_rec->sec_rec.sec_state == BTM_SEC_STATE_AUTHENTICATING) {
    log::info("device is in the process of authenticating");
  } else {
    log::info("starting authentication");
    p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_AUTHENTICATING;
    btsnd_hcic_auth_request(p_dev_rec->hci_handle);
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_collision_timeout
 *
 * Description      Encryption could not start because of the collision
 *                  try to do it again
 *
 * Returns          Pointer to the TLE struct
 *
 ******************************************************************************/
static void btm_sec_collision_timeout(void* /* data */) {
  log::verbose("restaring security process after collision");

  tBTM_STATUS status = btm_sec_execute_procedure(btm_sec_cb.p_collided_dev_rec);

  /* If result is pending reply from the user or from the device is pending */
  if (status != BTM_CMD_STARTED) {
    /* There is no next procedure or start of procedure failed, notify the
     * waiting layer */
    btm_sec_dev_rec_cback_event(btm_sec_cb.p_collided_dev_rec, status, false);
  }
}

/*******************************************************************************
 *
 * Function         btm_send_link_key_notif
 *
 * Description      Call the link key callback.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_send_link_key_notif(tBTM_SEC_DEV_REC* p_dev_rec) {
  if (btm_sec_cb.api.p_link_key_callback)
    (*btm_sec_cb.api.p_link_key_callback)(
        p_dev_rec->bd_addr, p_dev_rec->dev_class, p_dev_rec->sec_bd_name,
        p_dev_rec->sec_rec.link_key, p_dev_rec->sec_rec.link_key_type, false);
}

/*******************************************************************************
 *
 * Function         btm_restore_mode
 *
 * Description      This function returns the security mode to previous setting
 *                  if it was changed during bonding.
 *
 *
 * Parameters:      void
 *
 ******************************************************************************/
static void btm_restore_mode(void) {
  if (btm_sec_cb.security_mode_changed) {
    btm_sec_cb.security_mode_changed = false;
    btsnd_hcic_write_auth_enable(false);
  }

  if (btm_sec_cb.pin_type_changed) {
    btm_sec_cb.pin_type_changed = false;
    btsnd_hcic_write_pin_type(btm_sec_cb.cfg.pin_type);
  }
}

/*******************************************************************************
 *
 * Function         change_pairing_state
 *
 * Description      This function is called to change pairing state
 *
 ******************************************************************************/
void tBTM_SEC_CB::change_pairing_state(tBTM_PAIRING_STATE new_state) {
  tBTM_PAIRING_STATE old_state = pairing_state;

  log::debug("Pairing state changed {} => {} pairing_flags:0x{:x}",
             tBTM_SEC_CB::btm_pair_state_descr(pairing_state),
             tBTM_SEC_CB::btm_pair_state_descr(new_state), pairing_flags);

  if (pairing_state != new_state) {
    BTM_LogHistory(
        kBtmLogTag, btm_sec_cb.pairing_bda, "Pairing state changed",
        base::StringPrintf("%s => %s",
                           tBTM_SEC_CB::btm_pair_state_descr(pairing_state),
                           tBTM_SEC_CB::btm_pair_state_descr(new_state)));
  }
  pairing_state = new_state;

  if (new_state == BTM_PAIR_STATE_IDLE) {
    alarm_cancel(pairing_timer);

    pairing_flags = 0;
    pin_code_len = 0;

    /* Make sure the the lcb shows we are not bonding */
    l2cu_update_lcb_4_bonding(pairing_bda, false);

    btm_restore_mode();
    btm_sec_check_pending_reqs();
    btm_inq_clear_ssp();

    pairing_bda = RawAddress::kAny;
  } else {
    /* If transitioning out of idle, mark the lcb as bonding */
    if (old_state == BTM_PAIR_STATE_IDLE)
      l2cu_update_lcb_4_bonding(pairing_bda, true);

    alarm_set_on_mloop(btm_sec_cb.pairing_timer, BTM_SEC_TIMEOUT_VALUE * 1000,
                       btm_sec_pairing_timeout, NULL);
  }
}

/*******************************************************************************
 *
 * Function         btm_pair_state_descr
 *
 * Description      Return state description for tracing
 *
 ******************************************************************************/
const char* tBTM_SEC_CB::btm_pair_state_descr(tBTM_PAIRING_STATE state) {
  switch (state) {
    case BTM_PAIR_STATE_IDLE:
      return ("IDLE");
    case BTM_PAIR_STATE_GET_REM_NAME:
      return ("GET_REM_NAME");
    case BTM_PAIR_STATE_WAIT_PIN_REQ:
      return ("WAIT_PIN_REQ");
    case BTM_PAIR_STATE_WAIT_LOCAL_PIN:
      return ("WAIT_LOCAL_PIN");
    case BTM_PAIR_STATE_WAIT_NUMERIC_CONFIRM:
      return ("WAIT_NUM_CONFIRM");
    case BTM_PAIR_STATE_KEY_ENTRY:
      return ("KEY_ENTRY");
    case BTM_PAIR_STATE_WAIT_LOCAL_OOB_RSP:
      return ("WAIT_LOCAL_OOB_RSP");
    case BTM_PAIR_STATE_WAIT_LOCAL_IOCAPS:
      return ("WAIT_LOCAL_IOCAPS");
    case BTM_PAIR_STATE_INCOMING_SSP:
      return ("INCOMING_SSP");
    case BTM_PAIR_STATE_WAIT_AUTH_COMPLETE:
      return ("WAIT_AUTH_COMPLETE");
    case BTM_PAIR_STATE_WAIT_DISCONNECT:
      return ("WAIT_DISCONNECT");
  }

  return ("???");
}

/*******************************************************************************
 *
 * Function         btm_sec_dev_rec_cback_event
 *
 * Description      This function calls the callback function with the given
 *                  result and clear the callback function.
 *
 * Parameters:      void
 *
 ******************************************************************************/
void btm_sec_dev_rec_cback_event(tBTM_SEC_DEV_REC* p_dev_rec,
                                 tBTM_STATUS btm_status, bool is_le_transport) {
  ASSERT(p_dev_rec != nullptr);
  log::debug("transport={}, btm_status={}", is_le_transport ? "le" : "classic",
             btm_status_text(btm_status).c_str());

  tBTM_SEC_CALLBACK* p_callback = p_dev_rec->sec_rec.p_callback;
  p_dev_rec->sec_rec.p_callback = NULL;
  if (p_callback != nullptr) {
    if (is_le_transport) {
      (*p_callback)(&p_dev_rec->ble.pseudo_addr, BT_TRANSPORT_LE,
                    p_dev_rec->sec_rec.p_ref_data, btm_status);
    } else {
      (*p_callback)(&p_dev_rec->bd_addr, BT_TRANSPORT_BR_EDR,
                    p_dev_rec->sec_rec.p_ref_data, btm_status);
    }
  }

  btm_sec_check_pending_reqs();
}

void btm_sec_cr_loc_oob_data_cback_event(const RawAddress& address,
                                         tSMP_LOC_OOB_DATA loc_oob_data) {
  tBTM_LE_EVT_DATA evt_data = {
      .local_oob_data = loc_oob_data,
  };
  if (btm_sec_cb.api.p_le_callback) {
    (*btm_sec_cb.api.p_le_callback)(BTM_LE_SC_LOC_OOB_EVT, address, &evt_data);
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_queue_mx_request
 *
 * Description      Return state description for tracing
 *
 ******************************************************************************/
static bool btm_sec_queue_mx_request(const RawAddress& bd_addr, uint16_t psm,
                                     bool is_orig, uint16_t security_required,
                                     tBTM_SEC_CALLBACK* p_callback,
                                     void* p_ref_data) {
  tBTM_SEC_QUEUE_ENTRY* p_e =
      (tBTM_SEC_QUEUE_ENTRY*)osi_malloc(sizeof(tBTM_SEC_QUEUE_ENTRY));

  p_e->psm = psm;
  p_e->is_orig = is_orig;
  p_e->p_callback = p_callback;
  p_e->p_ref_data = p_ref_data;
  p_e->transport = BT_TRANSPORT_BR_EDR;
  p_e->sec_act = BTM_BLE_SEC_NONE;
  p_e->bd_addr = bd_addr;
  p_e->rfcomm_security_requirement = security_required;

  log::verbose("PSM: 0x{:04x}  Is_Orig: {}  security_required: 0x{:x}", psm,
               is_orig, security_required);

  fixed_queue_enqueue(btm_sec_cb.sec_pending_q, p_e);

  return true;
}

static bool btm_sec_check_prefetch_pin(tBTM_SEC_DEV_REC* p_dev_rec) {
  uint8_t major = (uint8_t)(p_dev_rec->dev_class[1] & BTM_COD_MAJOR_CLASS_MASK);
  uint8_t minor = (uint8_t)(p_dev_rec->dev_class[2] & BTM_COD_MINOR_CLASS_MASK);
  bool rv = false;

  if ((major == BTM_COD_MAJOR_AUDIO) &&
      ((minor == BTM_COD_MINOR_CONFM_HANDSFREE) ||
       (minor == BTM_COD_MINOR_CAR_AUDIO))) {
    log::verbose(
        "Skipping pre-fetch PIN for carkit COD Major: 0x{:02x} Minor: 0x{:02x}",
        major, minor);

    if (!btm_sec_cb.security_mode_changed) {
      btm_sec_cb.security_mode_changed = true;
      btsnd_hcic_write_auth_enable(true);
    }
  } else {
    btm_sec_cb.change_pairing_state(BTM_PAIR_STATE_WAIT_LOCAL_PIN);

    /* If we got a PIN, use that, else try to get one */
    if (btm_sec_cb.pin_code_len) {
      BTM_PINCodeReply(p_dev_rec->bd_addr, BTM_SUCCESS, btm_sec_cb.pin_code_len,
                       btm_sec_cb.pin_code);
    } else {
      /* pin was not supplied - pre-fetch pin code now */
      if (btm_sec_cb.api.p_pin_callback &&
          ((btm_sec_cb.pairing_flags & BTM_PAIR_FLAGS_PIN_REQD) == 0)) {
        log::verbose("PIN code callback called");
        if (BTM_IsAclConnectionUp(p_dev_rec->bd_addr, BT_TRANSPORT_BR_EDR))
          btm_sec_cb.pairing_flags |= BTM_PAIR_FLAGS_PIN_REQD;
        (btm_sec_cb.api.p_pin_callback)(
            p_dev_rec->bd_addr, p_dev_rec->dev_class, p_dev_rec->sec_bd_name,
            (p_dev_rec->sec_rec.required_security_flags_for_pairing &
             BTM_SEC_IN_MIN_16_DIGIT_PIN));
      }
    }

    rv = true;
  }

  return rv;
}

/*******************************************************************************
 *
 * Function         btm_sec_queue_encrypt_request
 *
 * Description      encqueue encryption request when device has active security
 *                  process pending.
 *
 ******************************************************************************/
static void btm_sec_queue_encrypt_request(const RawAddress& bd_addr,
                                          tBT_TRANSPORT transport,
                                          tBTM_SEC_CALLBACK* p_callback,
                                          void* p_ref_data,
                                          tBTM_BLE_SEC_ACT sec_act) {
  tBTM_SEC_QUEUE_ENTRY* p_e =
      (tBTM_SEC_QUEUE_ENTRY*)osi_malloc(sizeof(tBTM_SEC_QUEUE_ENTRY) + 1);

  p_e->psm = 0; /* if PSM 0, encryption request */
  p_e->p_callback = p_callback;
  p_e->p_ref_data = p_ref_data;
  p_e->transport = transport;
  p_e->sec_act = sec_act;
  p_e->bd_addr = bd_addr;
  fixed_queue_enqueue(btm_sec_cb.sec_pending_q, p_e);
}

/*******************************************************************************
 *
 * Function         btm_sec_check_pending_enc_req
 *
 * Description      This function is called to send pending encryption callback
 *                  if waiting
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_sec_check_pending_enc_req(tBTM_SEC_DEV_REC* p_dev_rec,
                                          tBT_TRANSPORT transport,
                                          uint8_t encr_enable) {
  if (fixed_queue_is_empty(btm_sec_cb.sec_pending_q)) return;

  const tBTM_STATUS res = encr_enable ? BTM_SUCCESS : BTM_ERR_PROCESSING;
  list_t* list = fixed_queue_get_list(btm_sec_cb.sec_pending_q);
  for (const list_node_t* node = list_begin(list); node != list_end(list);) {
    tBTM_SEC_QUEUE_ENTRY* p_e = (tBTM_SEC_QUEUE_ENTRY*)list_node(node);
    node = list_next(node);

    if (p_e->bd_addr == p_dev_rec->bd_addr && p_e->psm == 0 &&
        p_e->transport == transport) {
      if (encr_enable == 0 || transport == BT_TRANSPORT_BR_EDR ||
          p_e->sec_act == BTM_BLE_SEC_ENCRYPT ||
          p_e->sec_act == BTM_BLE_SEC_ENCRYPT_NO_MITM ||
          (p_e->sec_act == BTM_BLE_SEC_ENCRYPT_MITM &&
           p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_AUTHENTICATED)) {
        if (p_e->p_callback)
          (*p_e->p_callback)(&p_dev_rec->bd_addr, transport, p_e->p_ref_data,
                             res);
        fixed_queue_try_remove_from_queue(btm_sec_cb.sec_pending_q, (void*)p_e);
        osi_free(p_e);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_sec_set_serv_level4_flags
 *
 * Description      This function is called to set security mode 4 level 4
 *                  flags.
 *
 * Returns          service security requirements updated to include secure
 *                  connections only mode.
 *
 ******************************************************************************/
static uint16_t btm_sec_set_serv_level4_flags(uint16_t cur_security,
                                              bool is_originator) {
  uint16_t sec_level4_flags =
      is_originator ? BTM_SEC_OUT_LEVEL4_FLAGS : BTM_SEC_IN_LEVEL4_FLAGS;

  return cur_security | sec_level4_flags;
}

/*******************************************************************************
 *
 * Function         btm_sec_clear_ble_keys
 *
 * Description      This function is called to clear out the BLE keys.
 *                  Typically when devices are removed in BTM_SecDeleteDevice,
 *                  or when a new BT Link key is generated.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_clear_ble_keys(tBTM_SEC_DEV_REC* p_dev_rec) {
  log::verbose("Clearing BLE Keys");
  memset(&p_dev_rec->sec_rec.ble_keys, 0, sizeof(tBTM_SEC_BLE_KEYS));

  btm_ble_resolving_list_remove_dev(p_dev_rec);
}

/*******************************************************************************
 *
 * Function         btm_sec_is_a_bonded_dev
 *
 * Description       Is the specified device is a bonded device
 *                   (either on BR/EDR or LE)
 *
 * Returns          true - dev is bonded
 *
 ******************************************************************************/
bool btm_sec_is_a_bonded_dev(const RawAddress& bda) {
  return btm_sec_cb.IsDeviceBonded(bda);
}

/*******************************************************************************
 *
 * Function         btm_sec_use_smp_br_chnl
 *
 * Description      The function checks if SMP BR connection can be used with
 *                  the peer.
 *                  Is called when authentication for dedicated bonding is
 *                  successfully completed.
 *
 * Returns          true - if SMP BR connection can be used (the link key is
 *                         generated from P-256 and the peer supports Security
 *                         Manager over BR).
 *
 ******************************************************************************/
static bool btm_sec_use_smp_br_chnl(tBTM_SEC_DEV_REC* p_dev_rec) {
  uint32_t ext_feat;
  uint8_t chnl_mask[L2CAP_FIXED_CHNL_ARRAY_SIZE];

  log::verbose("link_key_type = 0x{:x}", p_dev_rec->sec_rec.link_key_type);

  if ((p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_UNAUTH_COMB_P_256) &&
      (p_dev_rec->sec_rec.link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256))
    return false;

  if (!L2CA_GetPeerFeatures(p_dev_rec->bd_addr, &ext_feat, chnl_mask))
    return false;

  if (!(chnl_mask[0] & L2CAP_FIXED_CHNL_SMP_BR_BIT)) return false;

  return true;
}

/*******************************************************************************
 *
 * Function         btm_sec_set_peer_sec_caps
 *
 * Description      This function is called to set sm4 and rmt_sec_caps fields
 *                  based on the available peer device features.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_set_peer_sec_caps(uint16_t hci_handle, bool ssp_supported,
                               bool sc_supported,
                               bool hci_role_switch_supported,
                               bool br_edr_supported, bool le_supported) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(hci_handle);
  if (p_dev_rec == nullptr) return;

  if (IS_FLAG_ENABLED(bluffs_mitigation)) {
    // Drop the connection here if the remote attempts to downgrade from Secure
    // Connections mode.
    if (btm_sec_is_device_sc_downgrade(hci_handle, sc_supported)) {
      acl_set_disconnect_reason(HCI_ERR_HOST_REJECT_SECURITY);
      btm_sec_send_hci_disconnect(
          p_dev_rec, HCI_ERR_AUTH_FAILURE, hci_handle,
          "attempted to downgrade from Secure Connections mode");
      return;
    }
  }

  p_dev_rec->remote_feature_received = true;
  p_dev_rec->remote_supports_hci_role_switch = hci_role_switch_supported;

  uint8_t req_pend = (p_dev_rec->sm4 & BTM_SM4_REQ_PEND);

  if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_NAME_KNOWN) ||
      p_dev_rec->is_originator) {
    tBTM_STATUS btm_status = btm_sec_execute_procedure(p_dev_rec);
    if (btm_status != BTM_CMD_STARTED) {
      log::warn("Security procedure not started! status:{}",
                btm_status_text(btm_status).c_str());
      btm_sec_dev_rec_cback_event(p_dev_rec, btm_status, false);
    }
  }

  /* Store the Peer Security Capabilites (in SM4 and rmt_sec_caps) */
  if ((btm_sec_cb.security_mode == BTM_SEC_MODE_SP ||
       btm_sec_cb.security_mode == BTM_SEC_MODE_SC) &&
      ssp_supported) {
    p_dev_rec->sm4 = BTM_SM4_TRUE;
    p_dev_rec->remote_supports_secure_connections = sc_supported;
  } else {
    p_dev_rec->sm4 = BTM_SM4_KNOWN;
    p_dev_rec->remote_supports_secure_connections = false;
  }

  if (p_dev_rec->remote_features_needed) {
    log::debug("Now device in SC Only mode, waiting for peer remote features!");
    btm_io_capabilities_req(p_dev_rec->bd_addr);
    p_dev_rec->remote_features_needed = false;
  }

  if (req_pend) {
    /* Request for remaining Security Features (if any) */
    l2cu_resubmit_pending_sec_req(&p_dev_rec->bd_addr);
  }

  p_dev_rec->remote_supports_bredr = br_edr_supported;
  p_dev_rec->remote_supports_ble = le_supported;
}

// Return DEV_CLASS (uint8_t[3]) of bda. If record doesn't exist, create one.
DEV_CLASS btm_get_dev_class(const RawAddress& bda) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(bda);
  return p_dev_rec->dev_class;
}

void BTM_update_version_info(const RawAddress& bd_addr,
                             const remote_version_info& remote_version_info) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == NULL) return;

  p_dev_rec->remote_version_info = remote_version_info;
}
