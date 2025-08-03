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

#pragma once
#include <cstdint>
#include <string>

#include "stack/btm/security_device_record.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/btm_status.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/security_client_callbacks.h"
#include "stack/include/smp_api_types.h"
#include "types/ble_address_with_type.h"
#include "types/bt_transport.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

#define BTM_SEC_MAX_COLLISION_DELAY (5000)

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
bool BTM_SecRegister(const tBTM_APPL_INFO* p_cb_info);

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
bool BTM_SecAddRmtNameNotifyCallback(tBTM_RMT_NAME_CALLBACK* p_callback);

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
bool BTM_SecDeleteRmtNameNotifyCallback(tBTM_RMT_NAME_CALLBACK* p_callback);

bool BTM_IsEncrypted(const RawAddress& bd_addr, tBT_TRANSPORT transport);
bool BTM_IsLinkKeyAuthed(const RawAddress& bd_addr, tBT_TRANSPORT transport);
bool BTM_IsLinkKeyKnown(const RawAddress& bd_addr, tBT_TRANSPORT transport);
bool BTM_IsAuthenticated(const RawAddress& bd_addr, tBT_TRANSPORT transport);
bool BTM_CanReadDiscoverableCharacteristics(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTM_SetPinType
 *
 * Description      Set PIN type for the device.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_SetPinType(uint8_t pin_type, PIN_CODE pin_code, uint8_t pin_code_len);

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
                          uint32_t mx_proto_id, uint32_t mx_chan_id);

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
uint8_t BTM_SecClrService(uint8_t service_id);

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
uint8_t BTM_SecClrServiceByPsm(uint16_t psm);

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
                      uint8_t pin_len, uint8_t* p_pin);

/*******************************************************************************
 *
 * Function         btm_sec_bond_by_transport
 *
 * Description      this is the bond function that will start either SSP or SMP.
 *
 * Parameters:      bd_addr      - Address of the device to bond
 *                  pin_len      - length in bytes of the PIN Code
 *                  p_pin        - pointer to array with the PIN Code
 *
 *  Note: After 2.1 parameters are not used and preserved here not to change API
 ******************************************************************************/
tBTM_STATUS btm_sec_bond_by_transport(const RawAddress& bd_addr,
                                      tBLE_ADDR_TYPE addr_type,
                                      tBT_TRANSPORT transport);

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
                        tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type);

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
tBTM_STATUS BTM_SecBondCancel(const RawAddress& bd_addr);

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
tBTM_LINK_KEY_TYPE BTM_SecGetDeviceLinkKeyType(const RawAddress& bd_addr);

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
 *                  p_callback    - Pointer to callback function called if
 *                                  this function returns PENDING after required
 *                                  procedures are completed.  Can be set to
 *                                  NULL if status is not desired.
 *                  p_ref_data    - pointer to any data the caller wishes to
 *                                  receive in the callback function upon
 *                                  completion. can be set to NULL if not used.
 *                  sec_act       - LE security action, unused for BR/EDR
 *
 * Returns          BTM_SUCCESS   - already encrypted
 *                  BTM_PENDING   - command will be returned in the callback
 *                  BTM_WRONG_MODE- connection not up.
 *                  BTM_BUSY      - security procedures are currently active
 *                  BTM_ERR_KEY_MISSING  - link key is missing.
 *                  BTM_MODE_UNSUPPORTED - if security manager not linked in.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetEncryption(const RawAddress& bd_addr,
                              tBT_TRANSPORT transport,
                              tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                              tBTM_BLE_SEC_ACT sec_act);

bool BTM_SecIsSecurityPending(const RawAddress& bd_addr);

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
void BTM_ConfirmReqReply(tBTM_STATUS res, const RawAddress& bd_addr);

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
                         uint32_t passkey);

/*******************************************************************************
 *
 * Function         BTM_ReadLocalOobData
 *
 * Description      This function is called to read the local OOB data from
 *                  LM
 *
 ******************************************************************************/
void BTM_ReadLocalOobData(void);

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
                            const Octet16& c, const Octet16& r);

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
bool BTM_BothEndsSupportSecureConnections(const RawAddress& bd_addr);

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
bool BTM_PeerSupportsSecureConnections(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTM_GetInitialSecurityMode
 *
 * Description      This function is called to retrieve the configured
 *                  security mode.
 *
 ******************************************************************************/
uint8_t BTM_GetSecurityMode();

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
                                     void* p_ref_data);

// Allow enforcing security by specific requirement (from shim layer).
tBTM_STATUS btm_sec_l2cap_access_req_by_requirement(
    const RawAddress& bd_addr, uint16_t security_required, bool is_originator,
    tBTM_SEC_CALLBACK* p_callback, void* p_ref_data);

/*******************************************************************************
 *
 * Function         btm_sec_mx_access_request
 *
 * Description      This function is called by all Multiplexing Protocols
 *during establishing connection to or from peer device to grant permission
 *to establish application connection.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *                  psm           - L2CAP PSM
 *                  is_originator - true if protocol above L2CAP originates
 *                                  connection
 *                  mx_proto_id   - protocol ID of the multiplexer
 *                  mx_chan_id    - multiplexer channel to reach application
 *                  p_callback    - Pointer to callback function called if
 *                                  this function returns PENDING after
 *required procedures are completed p_ref_data    - Pointer to any reference
 *data needed by the the callback function.
 *
 * Returns          BTM_CMD_STARTED
 *
 ******************************************************************************/
tBTM_STATUS btm_sec_mx_access_request(const RawAddress& bd_addr,
                                      bool is_originator,
                                      uint16_t security_requirement,
                                      tBTM_SEC_CALLBACK* p_callback,
                                      void* p_ref_data);

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
void btm_sec_conn_req(const RawAddress& bda, const DEV_CLASS dc);

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
void btm_create_conn_cancel_complete(uint8_t status, const RawAddress bd_addr,
                                     uint32_t value);

/*******************************************************************************
 *
 * Function         btm_sec_dev_reset
 *
 * Description      This function should be called after device reset
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_dev_reset(void);

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
void btm_sec_abort_access_req(const RawAddress& bd_addr);

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
                                       tHCI_STATUS status);

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
                                       uint8_t features_0);

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
void btm_io_capabilities_req(RawAddress p);

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
void btm_io_capabilities_rsp(const tBTM_SP_IO_RSP evt_data);

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
                         uint32_t value);

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
void btm_simple_pair_complete(const RawAddress bd_addr, uint8_t status);

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
void btm_rem_oob_req(const RawAddress bd_addr);

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
void btm_read_local_oob_complete(const tBTM_SP_LOC_OOB evt_data);

/*******************************************************************************
 *
 * Function         btm_sec_auth_complete
 *
 * Description      This function is when authentication of the connection is
 *                  completed by the LM
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_auth_complete(uint16_t handle, tHCI_STATUS status);

/*******************************************************************************
 *
 * Function         btm_sec_encryption_change_evt
 *
 * Description      This function is called to process an encryption change.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_encryption_change_evt(uint16_t handle, tHCI_STATUS status,
                                   uint8_t encr_enable);

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
                            uint8_t encr_enable);

/*******************************************************************************
 *
 * Function         btm_sec_connected
 *
 * Description      This function is when a connection to the peer device is
 *                  established
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_connected(const RawAddress& bda, uint16_t handle,
                       tHCI_STATUS status, uint8_t enc_mode,
                       tHCI_ROLE assigned_role = HCI_ROLE_PERIPHERAL);

/*******************************************************************************
 *
 * Function         btm_sec_disconnect
 *
 * Description      This function is called to disconnect HCI link
 *
 * Returns          btm status
 *
 ******************************************************************************/
tBTM_STATUS btm_sec_disconnect(uint16_t handle, tHCI_STATUS reason,
                               std::string);

/*******************************************************************************
 *
 * Function         btm_sec_disconnected
 *
 * Description      This function is when a connection to the peer device is
 *                  dropped
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_disconnected(uint16_t handle, tHCI_STATUS reason,
                          std::string comment);

/*******************************************************************************
 *
 * Function         btm_sec_role_changed
 *
 * Description      This function is called when receiving an HCI role change
 *                  event
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                          tHCI_ROLE new_role);

/** This function is called when a new connection link key is generated */
void btm_sec_link_key_notification(const RawAddress& p_bda,
                                   const Octet16& link_key, uint8_t key_type);

/** This function is called for each encryption key refresh complete event */
void btm_sec_encryption_key_refresh_complete(uint16_t handle,
                                             tHCI_STATUS status);

/*******************************************************************************
 *
 * Function         btm_sec_link_key_request
 *
 * Description      This function is called when controller requests link key
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
void btm_sec_link_key_request(const RawAddress bda);

/*******************************************************************************
 *
 * Function         btm_sec_pin_code_request
 *
 * Description      This function is called when controller requests PIN code
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
void btm_sec_pin_code_request(const RawAddress bda);

/*******************************************************************************
 *
 * Function         btm_sec_update_clock_offset
 *
 * Description      This function is called to update clock offset
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_update_clock_offset(uint16_t handle, uint16_t clock_offset);

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
void btm_sec_dev_rec_cback_event(tBTM_SEC_DEV_REC* p_dev_rec, tBTM_STATUS res,
                                 bool is_le_transport);

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
void btm_sec_clear_ble_keys(tBTM_SEC_DEV_REC* p_dev_rec);

/*******************************************************************************
 *
 * Function         btm_sec_is_a_bonded_dev
 *
 * Description       Is the specified device is a bonded device
 *
 * Returns          true - dev is bonded
 *
 ******************************************************************************/
bool btm_sec_is_a_bonded_dev(const RawAddress& bda);

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
                               bool br_edr_supported, bool le_supported);

/*******************************************************************************
 *
 * Function         btm_sec_cr_loc_oob_data_cback_event
 *
 * Description      This function is called to pass the local oob up to caller
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_sec_cr_loc_oob_data_cback_event(const RawAddress& address,
                                         tSMP_LOC_OOB_DATA loc_oob_data);

// Return DEV_CLASS of bda. If record doesn't exist, create one.
DEV_CLASS btm_get_dev_class(const RawAddress& bda);
