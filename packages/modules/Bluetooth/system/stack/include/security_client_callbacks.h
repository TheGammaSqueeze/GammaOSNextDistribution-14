/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <cstdint>

#include "stack/include/bt_dev_class.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_name.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_ble_sec_api_types.h"
#include "stack/include/hci_error_code.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

/****************************************
 *  Security Manager Callback Functions
 ****************************************/
/* Authorize device for service.  Parameters are
 *              Service Id (NULL - unknown service or unused
 *                                 [BTM_SEC_SERVICE_NAME_LEN set to 0])
 */
typedef uint8_t(tBTM_AUTHORIZE_CALLBACK)(uint8_t service_id);

/* Get PIN for the connection.  Parameters are
 *              BD Address of remote
 *              Device Class of remote
 *              BD Name of remote
 *              Flag indicating the minimum pin code length to be 16 digits
 */
typedef uint8_t(tBTM_PIN_CALLBACK)(const RawAddress& bd_addr,
                                   DEV_CLASS dev_class,
                                   const tBTM_BD_NAME bd_name,
                                   bool min_16_digit);

/* New Link Key for the connection.  Parameters are
 *              BD Address of remote
 *              Link Key
 *              Key Type: Combination, Local Unit, or Remote Unit
 */
typedef uint8_t(tBTM_LINK_KEY_CALLBACK)(const RawAddress& bd_addr,
                                        DEV_CLASS dev_class,
                                        tBTM_BD_NAME bd_name,
                                        const LinkKey& key, uint8_t key_type,
                                        bool is_ctkd);

/* Remote Name Resolved.  Parameters are
 *              BD Address of remote
 *              BD Name of remote
 */
typedef void(tBTM_RMT_NAME_CALLBACK)(const RawAddress& bd_addr, DEV_CLASS dc,
                                     tBTM_BD_NAME bd_name);

/* Authentication complete for the connection.  Parameters are
 *              BD Address of remote
 *              Device Class of remote
 *              BD Name of remote
 *
 */
typedef void(tBTM_AUTH_COMPLETE_CALLBACK)(const RawAddress& bd_addr,
                                          DEV_CLASS dev_class,
                                          tBTM_BD_NAME bd_name,
                                          tHCI_REASON reason);

/* Request SIRK verification for found member. Parameters are
 *              BD Address of remote
 */
typedef uint8_t(tBTM_SIRK_VERIFICATION_CALLBACK)(const RawAddress& bd_addr);

struct tBTM_APPL_INFO {
  tBTM_PIN_CALLBACK* p_pin_callback{nullptr};
  tBTM_LINK_KEY_CALLBACK* p_link_key_callback{nullptr};
  tBTM_AUTH_COMPLETE_CALLBACK* p_auth_complete_callback{nullptr};
  tBTM_BOND_CANCEL_CMPL_CALLBACK* p_bond_cancel_cmpl_callback{nullptr};
  tBTM_SP_CALLBACK* p_sp_callback{nullptr};
  tBTM_LE_CALLBACK* p_le_callback{nullptr};
  tBTM_LE_KEY_CALLBACK* p_le_key_callback{nullptr};
  tBTM_SIRK_VERIFICATION_CALLBACK* p_sirk_verification_callback{nullptr};
};

typedef struct {
  void (*BTM_Sec_Init)();
  void (*BTM_Sec_Free)();

  bool (*BTM_SecRegister)(const tBTM_APPL_INFO* p_cb_info);

  void (*BTM_BleLoadLocalKeys)(uint8_t key_type, tBTM_BLE_LOCAL_KEYS* p_key);

  // Update/Query in-memory device records
  bool (*BTM_SecAddDevice)(const RawAddress& bd_addr, const DEV_CLASS dev_class,
                           const BD_NAME& bd_name, uint8_t* features,
                           LinkKey* link_key, uint8_t key_type,
                           uint8_t pin_length);
  void (*BTM_SecAddBleDevice)(const RawAddress& bd_addr,
                              tBT_DEVICE_TYPE dev_type,
                              tBLE_ADDR_TYPE addr_type);

  bool (*BTM_SecDeleteDevice)(const RawAddress& bd_addr);

  void (*BTM_SecAddBleKey)(const RawAddress& bd_addr,
                           tBTM_LE_KEY_VALUE* p_le_key,
                           tBTM_LE_KEY_TYPE key_type);

  void (*BTM_SecClearSecurityFlags)(const RawAddress& bd_addr);

  tBTM_STATUS (*BTM_SetEncryption)(const RawAddress& bd_addr,
                                   tBT_TRANSPORT transport,
                                   tBTM_SEC_CALLBACK* p_callback,
                                   void* p_ref_data, tBTM_BLE_SEC_ACT sec_act);
  bool (*BTM_IsEncrypted)(const RawAddress& bd_addr, tBT_TRANSPORT transport);
  bool (*BTM_SecIsSecurityPending)(const RawAddress& bd_addr);
  bool (*BTM_IsLinkKeyKnown)(const RawAddress& bd_addr,
                             tBT_TRANSPORT transport);

  // Secure service management
  bool (*BTM_SetSecurityLevel)(bool is_originator, const char* p_name,
                               uint8_t service_id, uint16_t sec_level,
                               uint16_t psm, uint32_t mx_proto_id,
                               uint32_t mx_chan_id);
  uint8_t (*BTM_SecClrService)(uint8_t service_id);
  uint8_t (*BTM_SecClrServiceByPsm)(uint16_t psm);

  // Pairing related APIs
  tBTM_STATUS (*BTM_SecBond)(const RawAddress& bd_addr,
                             tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT transport,
                             tBT_DEVICE_TYPE device_type);
  tBTM_STATUS (*BTM_SecBondCancel)(const RawAddress& bd_addr);

  void (*BTM_RemoteOobDataReply)(tBTM_STATUS res, const RawAddress& bd_addr,
                                 const Octet16& c, const Octet16& r);
  void (*BTM_PINCodeReply)(const RawAddress& bd_addr, tBTM_STATUS res,
                           uint8_t pin_len, uint8_t* p_pin);
  void (*BTM_SecConfirmReqReply)(tBTM_STATUS res, tBT_TRANSPORT transport,
                                 const RawAddress bd_addr);
  void (*BTM_BleSirkConfirmDeviceReply)(const RawAddress& bd_addr, uint8_t res);

  void (*BTM_BlePasskeyReply)(const RawAddress& bd_addr, uint8_t res,
                              uint32_t passkey);

  // other misc APIs
  uint8_t (*BTM_GetSecurityMode)();

  // remote name request related APIs
  // TODO: remove them from this structure
  const char* (*BTM_SecReadDevName)(const RawAddress& bd_addr);
  bool (*BTM_SecAddRmtNameNotifyCallback)(tBTM_RMT_NAME_CALLBACK* p_callback);
  bool (*BTM_SecDeleteRmtNameNotifyCallback)(
      tBTM_RMT_NAME_CALLBACK* p_callback);
} SecurityClientInterface;

const SecurityClientInterface& get_security_client_interface();
