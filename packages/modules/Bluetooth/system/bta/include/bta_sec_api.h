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

#pragma once

#include <base/functional/callback.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>

#include "bta_api_data_types.h"
#include "internal_include/bt_target.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_name.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_ble_sec_api_types.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/hci_error_code.h"
#include "types/ble_address_with_type.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

/* Security Setting Mask */
#define BTA_SEC_AUTHENTICATE \
  (BTM_SEC_IN_AUTHENTICATE | \
   BTM_SEC_OUT_AUTHENTICATE) /* Authentication required. */
#define BTA_SEC_ENCRYPT \
  (BTM_SEC_IN_ENCRYPT | BTM_SEC_OUT_ENCRYPT) /* Encryption required. */

typedef uint16_t tBTA_SEC;

typedef enum : uint8_t {
  /* Security Callback Events */
  BTA_DM_PIN_REQ_EVT = 2,          /* PIN request. */
  BTA_DM_AUTH_CMPL_EVT = 3,        /* Authentication complete indication. */
  BTA_DM_AUTHORIZE_EVT = 4,        /* Authorization request. */
  BTA_DM_BOND_CANCEL_CMPL_EVT = 9, /* Bond cancel complete indication */
  BTA_DM_SP_CFM_REQ_EVT = 10,   /* Simple Pairing User Confirmation request */
  BTA_DM_SP_KEY_NOTIF_EVT = 11, /* Simple Pairing Passkey Notification */
  BTA_DM_BLE_KEY_EVT = 15,      /* BLE SMP key event for peer device keys */
  BTA_DM_BLE_SEC_REQ_EVT = 16,  /* BLE SMP security request */
  BTA_DM_BLE_PASSKEY_NOTIF_EVT = 17, /* SMP passkey notification event */
  BTA_DM_BLE_PASSKEY_REQ_EVT = 18,   /* SMP passkey request event */
  BTA_DM_BLE_OOB_REQ_EVT = 19,       /* SMP OOB request event */
  BTA_DM_BLE_LOCAL_IR_EVT = 20,      /* BLE local IR event */
  BTA_DM_BLE_LOCAL_ER_EVT = 21,      /* BLE local ER event */
  BTA_DM_BLE_NC_REQ_EVT = 22,        /* SMP Numeric Comparison request event */
  BTA_DM_SP_RMT_OOB_EXT_EVT =
      23, /* Simple Pairing Remote OOB Extended Data request. */
  BTA_DM_BLE_AUTH_CMPL_EVT = 24, /* BLE Auth complete */
  BTA_DM_DEV_UNPAIRED_EVT = 25,
  BTA_DM_ENER_INFO_READ = 28,        /* Energy info read */
  BTA_DM_BLE_SC_OOB_REQ_EVT = 29,    /* SMP SC OOB request event */
  BTA_DM_BLE_CONSENT_REQ_EVT = 30,   /* SMP consent request event */
  BTA_DM_BLE_SC_CR_LOC_OOB_EVT = 31, /* SMP SC Create Local OOB request event */
  BTA_DM_REPORT_BONDING_EVT = 32,    /*handle for pin or key missing*/
  BTA_DM_LE_ADDR_ASSOC_EVT = 33,     /* identity address association event */
  BTA_DM_SIRK_VERIFICATION_REQ_EVT = 35,
  BTA_DM_KEY_MISSING_EVT = 36,
} tBTA_DM_SEC_EVT;

/* Structure associated with BTA_DM_PIN_REQ_EVT */
typedef struct {
  /* Note: First 3 data members must be, bd_addr, dev_class, and bd_name in
   * order */
  RawAddress bd_addr;  /* BD address peer device. */
  DEV_CLASS dev_class; /* Class of Device */
  BD_NAME bd_name;     /* Name of peer device. */
  bool min_16_digit;   /* true if the pin returned must be at least 16 digits */
} tBTA_DM_PIN_REQ;

/* BLE related definition */

#define BTA_DM_AUTH_FAIL_BASE (HCI_ERR_MAX_ERR + 10)

/* Converts SMP error codes defined in smp_api.h to SMP auth fail reasons below.
 */
#define BTA_DM_AUTH_CONVERT_SMP_CODE(x) (BTA_DM_AUTH_FAIL_BASE + (x))

#define BTA_DM_AUTH_SMP_PAIR_AUTH_FAIL \
  (BTA_DM_AUTH_FAIL_BASE + SMP_PAIR_AUTH_FAIL)
#define BTA_DM_AUTH_SMP_CONFIRM_VALUE_FAIL \
  (BTA_DM_AUTH_FAIL_BASE + SMP_CONFIRM_VALUE_ERR)
#define BTA_DM_AUTH_SMP_PAIR_NOT_SUPPORT \
  (BTA_DM_AUTH_FAIL_BASE + SMP_PAIR_NOT_SUPPORT)
#define BTA_DM_AUTH_SMP_UNKNOWN_ERR \
  (BTA_DM_AUTH_FAIL_BASE + SMP_PAIR_FAIL_UNKNOWN)
#define BTA_DM_AUTH_SMP_CONN_TOUT (BTA_DM_AUTH_FAIL_BASE + SMP_CONN_TOUT)

typedef uint8_t tBTA_LE_KEY_TYPE; /* can be used as a bit mask */

typedef union {
  tBTM_LE_PENC_KEYS penc_key;  /* received peer encryption key */
  tBTM_LE_PCSRK_KEYS psrk_key; /* received peer device SRK */
  tBTM_LE_PID_KEYS pid_key;    /* peer device ID key */
  tBTM_LE_LENC_KEYS
      lenc_key; /* local encryption reproduction keys LTK = = d1(ER,DIV,0)*/
  tBTM_LE_LCSRK_KEYS lcsrk_key; /* local device CSRK = d1(ER,DIV,1)*/
  tBTM_LE_PID_KEYS lid_key; /* local device ID key for the particular remote */
} tBTA_LE_KEY_VALUE;

#define BTA_BLE_LOCAL_KEY_TYPE_ID 1
#define BTA_BLE_LOCAL_KEY_TYPE_ER 2
typedef uint8_t tBTA_DM_BLE_LOCAL_KEY_MASK;

typedef struct {
  Octet16 ir;
  Octet16 irk;
  Octet16 dhk;
} tBTA_BLE_LOCAL_ID_KEYS;

#define BTA_DM_SEC_GRANTED BTA_SUCCESS
#define BTA_DM_SEC_PAIR_NOT_SPT BTA_DM_AUTH_SMP_PAIR_NOT_SUPPORT
typedef uint8_t tBTA_DM_BLE_SEC_GRANT;

/* Structure associated with BTA_DM_BLE_SEC_REQ_EVT */
typedef struct {
  RawAddress bd_addr; /* peer address */
  BD_NAME bd_name; /* peer device name */
} tBTA_DM_BLE_SEC_REQ;

typedef struct {
  RawAddress bd_addr; /* peer address */
  tBTM_LE_KEY_TYPE key_type;
  tBTM_LE_KEY_VALUE* p_key_value;
} tBTA_DM_BLE_KEY;

/* Structure associated with BTA_DM_AUTH_CMPL_EVT */
typedef struct {
  RawAddress bd_addr;  /* BD address peer device. */
  BD_NAME bd_name;     /* Name of peer device. */
  bool key_present;    /* Valid link key value in key element */
  LinkKey key;         /* Link key associated with peer device. */
  uint8_t key_type;    /* The type of Link Key */
  bool success;        /* true of authentication succeeded, false if failed. */
  tHCI_REASON
      fail_reason; /* The HCI reason/error code for when success=false */
  tBLE_ADDR_TYPE addr_type; /* Peer device address type */
  tBT_DEVICE_TYPE dev_type;
  bool is_ctkd; /* True if key is derived using CTKD procedure */
} tBTA_DM_AUTH_CMPL;

/* Structure associated with BTA_DM_DEV_UNPAIRED_EVT */
typedef struct {
  RawAddress bd_addr; /* BD address peer device. */
  tBT_TRANSPORT transport_link_type;
} tBTA_DM_UNPAIR;

#define BTA_AUTH_SP_YES                                                       \
  BTM_AUTH_SP_YES /* 1 MITM Protection Required - Single Profile/non-bonding  \
                    Use IO Capabilities to determine authentication procedure \
                    */

#define BTA_AUTH_DD_BOND \
  BTM_AUTH_DD_BOND /* 2 this bit is set for dedicated bonding */
#define BTA_AUTH_GEN_BOND \
  BTM_AUTH_SPGB_NO /* 4 this bit is set for general bonding */
#define BTA_AUTH_BONDS \
  BTM_AUTH_BONDS /* 6 the general/dedicated bonding bits  */

#define BTA_LE_AUTH_REQ_SC_MITM_BOND BTM_LE_AUTH_REQ_SC_MITM_BOND /* 1101 */

/* Structure associated with BTA_DM_SP_CFM_REQ_EVT */
typedef struct {
  /* Note: First 3 data members must be, bd_addr, dev_class, and bd_name in
   * order */
  RawAddress bd_addr;  /* peer address */
  DEV_CLASS dev_class; /* peer CoD */
  BD_NAME bd_name;     /* peer device name */
  uint32_t num_val; /* the numeric value for comparison. If just_works, do not
                       show this number to UI */
  bool just_works;  /* true, if "Just Works" association model */
  tBTM_AUTH_REQ loc_auth_req; /* Authentication required for local device */
  tBTM_AUTH_REQ rmt_auth_req; /* Authentication required for peer device */
  tBTM_IO_CAP loc_io_caps;    /* IO Capabilities of local device */
  tBTM_IO_CAP rmt_io_caps;    // IO Capabilities of remote device
} tBTA_DM_SP_CFM_REQ;

/* Structure associated with BTA_DM_SP_KEY_NOTIF_EVT */
typedef struct {
  /* Note: First 3 data members must be, bd_addr, dev_class, and bd_name in
   * order */
  RawAddress bd_addr;  /* peer address */
  DEV_CLASS dev_class; /* peer CoD */
  BD_NAME bd_name;     /* peer device name */
  uint32_t passkey; /* the numeric value for comparison. If just_works, do not
                       show this number to UI */
} tBTA_DM_SP_KEY_NOTIF;

/* Structure associated with BTA_DM_SP_RMT_OOB_EVT */
typedef struct {
  /* Note: First 3 data members must be, bd_addr, dev_class, and bd_name in
   * order */
  RawAddress bd_addr;  /* peer address */
  DEV_CLASS dev_class; /* peer CoD */
  BD_NAME bd_name;     /* peer device name */
} tBTA_DM_SP_RMT_OOB;

/* Structure associated with BTA_DM_BOND_CANCEL_CMPL_EVT */
typedef struct {
  tBTA_STATUS result; /* true of bond cancel succeeded, false if failed. */
} tBTA_DM_BOND_CANCEL_CMPL;

/* Add to remove bond of key missing RC */
typedef struct {
  RawAddress bd_addr;
} tBTA_DM_RC_UNPAIR;

typedef struct {
  Octet16 local_oob_c; /* Local OOB Data Confirmation/Commitment */
  Octet16 local_oob_r; /* Local OOB Data Randomizer */
} tBTA_DM_LOC_OOB_DATA;

/* Union of all security callback structures */
typedef union {
  tBTA_DM_PIN_REQ pin_req;        /* PIN request. */
  tBTA_DM_AUTH_CMPL auth_cmpl;    /* Authentication complete indication. */
  tBTA_DM_UNPAIR dev_unpair;      /* Remove bonding complete indication */
  tBTA_DM_SP_CFM_REQ cfm_req;     /* user confirm request */
  tBTA_DM_SP_KEY_NOTIF key_notif; /* passkey notification */
  tBTA_DM_SP_RMT_OOB rmt_oob;     /* remote oob */
  tBTA_DM_BOND_CANCEL_CMPL
      bond_cancel_cmpl;               /* Bond Cancel Complete indication */
  tBTA_DM_BLE_SEC_REQ ble_req;        /* BLE SMP related request */
  tBTA_DM_BLE_KEY ble_key;            /* BLE SMP keys used when pairing */
  tBTA_BLE_LOCAL_ID_KEYS ble_id_keys; /* IR event */
  Octet16 ble_er;                     /* ER event data */
  tBTA_DM_LOC_OOB_DATA local_oob_data; /* Local OOB data generated by us */
  tBTA_DM_RC_UNPAIR delete_key_RC_to_unpair;
  tBTA_DM_PROC_ID_ADDR proc_id_addr; /* Identity address event */
  tBTA_DM_KEY_MISSING key_missing;
} tBTA_DM_SEC;

/* Security callback */
typedef void(tBTA_DM_SEC_CBACK)(tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data);

/* Encryption callback*/
typedef void(tBTA_DM_ENCRYPT_CBACK)(const RawAddress& bd_addr,
                                    tBT_TRANSPORT transport,
                                    tBTA_STATUS result);

/*******************************************************************************
 *
 * Function         BTA_DmBond
 *
 * Description      This function initiates a bonding procedure with a peer
 *                  device by designated transport.  The bonding procedure
 *                  enables authentication and optionally encryption on the
 *                  Bluetooth link.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type);

/*******************************************************************************
 *
 * Function         BTA_DmBondCancel
 *
 * Description      This function cancels a bonding procedure with a peer
 *                  device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBondCancel(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTA_DmPinReply
 *
 * Description      This function provides a PIN when one is requested by DM
 *                  during a bonding procedure.  The application should call
 *                  this function after the security callback is called with
 *                  a BTA_DM_PIN_REQ_EVT.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmPinReply(const RawAddress& bd_addr, bool accept, uint8_t pin_len,
                    uint8_t* p_pin);

/*******************************************************************************
 *
 * Function         BTA_DmLocalOob
 *
 * Description      This function retrieves the OOB data from local controller.
 *                  The result is reported by bta_dm_co_loc_oob().
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmLocalOob(void);

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
void BTA_DmConfirm(const RawAddress& bd_addr, bool accept);

/*******************************************************************************
 *
 * Function         BTA_DmAddDevice
 *
 * Description      This function adds a device to the security database list
 *                  of peer devices. This function would typically be called
 *                  at system startup to initialize the security database with
 *                  known peer devices.  This is a direct execution function
 *                  that may lock task scheduling on some platforms.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmAddDevice(const RawAddress& bd_addr, DEV_CLASS dev_class,
                     const LinkKey& link_key, uint8_t key_type,
                     uint8_t pin_length);

/*******************************************************************************
 *
 * Function         BTA_DmRemoveDevice
 *
 * Description      This function removes a device from the security database.
 *                  This is a direct execution function that may lock task
 *                  scheduling on some platforms.
 *
 *
 * Returns          BTA_SUCCESS if successful.
 *                  BTA_FAIL if operation failed.
 *
 ******************************************************************************/
tBTA_STATUS BTA_DmRemoveDevice(const RawAddress& bd_addr);


/* BLE related API functions */
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
                            tBTA_DM_BLE_SEC_GRANT res);

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
                           uint32_t passkey);

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
void BTA_DmBleConfirmReply(const RawAddress& bd_addr, bool accept);

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
                        tBT_DEVICE_TYPE dev_type);

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
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmAddBleKey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
                     tBTM_LE_KEY_TYPE key_type);

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
 *                                  for the BLE link if BLE is supported
 *                                  Note: This parameter is ignored for
 *                                        BR/EDR or if BLE is not supported.
 *
 * Returns          void
 *
 *
 ******************************************************************************/
void BTA_DmSetEncryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                         tBTA_DM_ENCRYPT_CBACK* p_callback,
                         tBTM_BLE_SEC_ACT sec_act);

/*******************************************************************************
 *
 * Function         BTA_DmSirkSecCbRegister
 *
 * Description      This procedure registeres in requested a callback for
 *                  verification by CSIS potential set member.
 *
 * Parameters       p_cback     - callback to member verificator
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSirkSecCbRegister(tBTA_DM_SEC_CBACK* p_cback);

/*******************************************************************************
 *
 * Function         BTA_DmSirkConfirmDeviceReply
 *
 * Description      This procedure confirms requested to validate set device.
 *
 * Parameters       bd_addr     - BD address of the peer
 *                  accept      - True if device is authorized by CSIS, false
 *                                otherwise.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSirkConfirmDeviceReply(const RawAddress& bd_addr, bool accept);

namespace fmt {
template <>
struct formatter<tBTA_DM_SEC_EVT> : enum_formatter<tBTA_DM_SEC_EVT> {};
}  // namespace fmt
