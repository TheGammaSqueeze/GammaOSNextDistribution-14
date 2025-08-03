/* Copyright 2020 The Android Open Source Project
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

#include "device/include/controller.h"
#include "stack/acl/acl.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_status.h"
#include "stack/include/hci_error_code.h"
#include "types/ble_address_with_type.h"
#include "types/bt_transport.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

// Note: From stack/include/btm_api.h

/*****************************************************************************
 *  ACL CHANNEL MANAGEMENT FUNCTIONS
 ****************************************************************************/
bool BTM_is_sniff_allowed_for(const RawAddress& peer_addr);

void BTM_unblock_sniff_mode_for(const RawAddress& peer_addr);
void BTM_block_sniff_mode_for(const RawAddress& peer_addr);
void BTM_unblock_role_switch_for(const RawAddress& peer_addr);
void BTM_block_role_switch_for(const RawAddress& peer_addr);
void BTM_unblock_role_switch_and_sniff_mode_for(const RawAddress& peer_addr);
void BTM_block_role_switch_and_sniff_mode_for(const RawAddress& peer_addr);

void BTM_default_unblock_role_switch();
void BTM_default_block_role_switch();

void BTM_acl_after_controller_started(const controller_t* controller);

/*******************************************************************************
 *
 * Function         BTM_SetLinkSuperTout
 *
 * Description      Create and send HCI "Write Link Supervision Timeout" command
 *
 * Returns          BTM_CMD_STARTED if successfully initiated, otherwise error
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetLinkSuperTout(const RawAddress& remote_bda,
                                 uint16_t timeout);
/*******************************************************************************
 *
 * Function         BTM_GetLinkSuperTout
 *
 * Description      Read the link supervision timeout value of the connection
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
tBTM_STATUS BTM_GetLinkSuperTout(const RawAddress& remote_bda,
                                 uint16_t* p_timeout);

/*******************************************************************************
 *
 * Function         BTM_IsAclConnectionUp
 *
 * Description      This function is called to check if an ACL connection exists
 *                  to a specific remote BD Address.  The second version ensures
 *                  the hci handle is valid (Unsure if needed)
 *
 * Returns          true if connection is up, else false.
 *
 ******************************************************************************/
bool BTM_IsAclConnectionUp(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport);

bool BTM_IsAclConnectionUpAndHandleValid(const RawAddress& remote_bda,
                                         tBT_TRANSPORT transport);

bool BTM_IsAclConnectionUpFromHandle(uint16_t hci_handle);

/*******************************************************************************
 *
 * Function         BTM_GetRole
 *
 * Description      This function is called to get the role of the local device
 *                  for the ACL connection with the specified remote device
 *
 * Returns          BTM_SUCCESS if connection exists.
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *
 ******************************************************************************/
tBTM_STATUS BTM_GetRole(const RawAddress& remote_bd_addr, tHCI_ROLE* p_role);

/*******************************************************************************
 *
 * Function         BTM_SwitchRoleToCentral
 *
 * Description      This function is called to switch role between central and
 *                  peripheral.  If role is already set it will do nothing.
 *
 * Returns          BTM_SUCCESS if already in specified role.
 *                  BTM_CMD_STARTED if command issued to controller.
 *                  BTM_NO_RESOURCES if memory couldn't be allocated to issue
 *                                   the command
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *                  BTM_MODE_UNSUPPORTED if the local device does not support
 *                                       role switching
 *
 ******************************************************************************/
tBTM_STATUS BTM_SwitchRoleToCentral(const RawAddress& remote_bd_addr);

/*******************************************************************************
 *
 * Function         BTM_ReadRSSI
 *
 * Description      This function is called to read the link policy settings.
 *                  The address of link policy results are returned in the
 *                  callback. (tBTM_RSSI_RESULT)
 *
 * Returns          BTM_CMD_STARTED if command issued to controller.
 *                  BTM_NO_RESOURCES if memory couldn't be allocated to issue
 *                                   the command
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *                  BTM_BUSY if command is already in progress
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadRSSI(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         BTM_ReadFailedContactCounter
 *
 * Description      This function is called to read the failed contact counter.
 *                  The result is returned in the callback.
 *                  (tBTM_FAILED_CONTACT_COUNTER_RESULT)
 *
 * Returns          BTM_CMD_STARTED if command issued to controller.
 *                  BTM_NO_RESOURCES if memory couldn't be allocated to issue
 *                                   the command
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *                  BTM_BUSY if command is already in progress
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadFailedContactCounter(const RawAddress& remote_bda,
                                         tBTM_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         BTM_ReadTxPower
 *
 * Description      This function is called to read the current connection
 *                  TX power of the connection. The TX power level results
 *                  are returned in the callback.
 *                  (tBTM_RSSI_RESULT)
 *
 * Returns          BTM_CMD_STARTED if command issued to controller.
 *                  BTM_NO_RESOURCES if memory couldn't be allocated to issue
 *                                   the command
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *                  BTM_BUSY if command is already in progress
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadTxPower(const RawAddress& remote_bda,
                            tBT_TRANSPORT transport, tBTM_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         BTM_GetNumAclLinks
 *
 * Description      This function is called to count the number of
 *                  ACL links that are active.
 *
 * Returns          uint16_t Number of active ACL links
 *
 ******************************************************************************/
uint16_t BTM_GetNumAclLinks(void);

void btm_set_packet_types_from_address(const RawAddress& bda,
                                       uint16_t pkt_types);

#define BLE_RESOLVE_ADDR_MASK 0xc0
#define BLE_RESOLVE_ADDR_MSB 0x40

bool BTM_BLE_IS_RESOLVE_BDA(const RawAddress& x);

bool acl_refresh_remote_address(const RawAddress& identity_address,
                                tBLE_ADDR_TYPE identity_address_type,
                                const RawAddress& remote_bda,
                                tBLE_RAND_ADDR_TYPE rra_type,
                                const RawAddress& rpa);

void btm_establish_continue_from_address(const RawAddress& remote_bda,
                                         tBT_TRANSPORT transport);

bool acl_peer_supports_ble_connection_parameters_request(
    const RawAddress& remote_bda);

bool acl_peer_supports_ble_packet_extension(uint16_t hci_handle);
bool acl_peer_supports_ble_2m_phy(uint16_t hci_handle);
bool acl_peer_supports_ble_coded_phy(uint16_t hci_handle);

bool acl_is_switch_role_idle(const RawAddress& bd_addr,
                             tBT_TRANSPORT transport);

bool acl_peer_supports_ble_packet_extension(uint16_t hci_handle);

/*******************************************************************************
 *
 * Function         BTM_ReadConnectionAddr
 *
 * Description      Read the local device random address.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_ReadConnectionAddr(const RawAddress& remote_bda,
                            RawAddress& local_conn_addr,
                            tBLE_ADDR_TYPE* p_addr_type,
                            bool ota_address = false);

/*******************************************************************************
 *
 * Function         BTM_IsBleConnection
 *
 * Description      This function is called to check if the connection handle
 *                  for an LE link
 *
 * Returns          true if connection is LE link, otherwise false.
 *
 ******************************************************************************/
bool BTM_IsBleConnection(uint16_t hci_handle);

const RawAddress acl_address_from_handle(uint16_t hci_handle);

void btm_cont_rswitch_from_handle(uint16_t hci_handle);

uint8_t acl_link_role_from_handle(uint16_t handle);

void acl_set_disconnect_reason(tHCI_STATUS acl_disc_reason);

void acl_set_locally_initiated(bool is_locally_initiated);

bool acl_is_role_switch_allowed();

uint16_t acl_get_supported_packet_types();

bool acl_set_peer_le_features_from_handle(uint16_t hci_handle,
                                          const uint8_t* p);

tBTM_STATUS btm_read_power_mode_state(const RawAddress& remote_bda,
                                      tBTM_PM_STATE* pmState);

void btm_acl_notif_conn_collision(const RawAddress& bda);

/*******************************************************************************
 *
 * Function         BTM_ReadPowerMode
 *
 * Description      This returns the current mode for a specific
 *                  ACL connection.
 *
 * Input Param      remote_bda - device address of desired ACL connection
 *
 * Output Param     p_mode - address where the current mode is copied into.
 *                          BTM_ACL_MODE_NORMAL
 *                          BTM_ACL_MODE_HOLD
 *                          BTM_ACL_MODE_SNIFF
 *                          BTM_ACL_MODE_PARK
 *                          (valid only if return code is BTM_SUCCESS)
 *
 * Returns          true if successful, false otherwise.
 *
 ******************************************************************************/
bool BTM_ReadPowerMode(const RawAddress& remote_bda, tBTM_PM_MODE* p_mode);

void btm_acl_created(const RawAddress& bda, uint16_t hci_handle,
                     tHCI_ROLE link_role, tBT_TRANSPORT transport);

void btm_acl_create_failed(const RawAddress& bda, tBT_TRANSPORT transport,
                           tHCI_STATUS reason);

void btm_acl_removed(uint16_t handle);

void acl_disconnect_from_handle(uint16_t handle, tHCI_STATUS reason,
                                std::string comment);
void acl_disconnect_after_role_switch(uint16_t conn_handle, tHCI_STATUS reason,
                                      std::string comment);

bool acl_peer_supports_sniff_subrating(const RawAddress& remote_bda);
bool acl_peer_supports_ble_connection_subrating(const RawAddress& remote_bda);
bool acl_peer_supports_ble_connection_subrating_host(
    const RawAddress& remote_bda);

uint8_t btm_handle_to_acl_index(uint16_t hci_handle);

tHCI_REASON btm_get_acl_disc_reason_code(void);

bool btm_is_acl_locally_initiated(void);

tBTM_STATUS btm_remove_acl(const RawAddress& bd_addr, tBT_TRANSPORT transport);

void btm_acl_device_down(void);

void ACL_RegisterClient(struct acl_client_callback_s* callbacks);
void ACL_UnregisterClient(struct acl_client_callback_s* callbacks);
void btm_acl_consolidate(const RawAddress& identity_addr,
                         const RawAddress& rpa);
