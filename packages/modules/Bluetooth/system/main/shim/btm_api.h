/*
 * Copyright 2019 The Android Open Source Project
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

#include <vector>

#include "base/functional/callback.h"
#include "device/include/esco_parameters.h"
#include "stack/btm/neighbor_inquiry.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_ble_api_types.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace shim {

/*******************************************************************************
 *
 * Function         BTM_SetDiscoverability
 *
 * Description      This function is called to set the device into or out of
 *                  discoverable mode. Discoverable mode means inquiry
 *                  scans are enabled.  If a value of '0' is entered for window
 *                  or interval, the default values are used.
 *
 * Returns          BTM_SUCCESS if successful
 *                  BTM_BUSY if a setting of the filter is already in progress
 *                  BTM_NO_RESOURCES if couldn't get a memory pool buffer
 *                  BTM_ILLEGAL_VALUE if a bad parameter was detected
 *                  BTM_WRONG_MODE if the device is not up.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetDiscoverability(uint16_t inq_mode, uint16_t window,
                                   uint16_t interval);

/*******************************************************************************
 *
 * Function         BTM_BleObserve
 *
 * Description      This procedure keep the device listening for advertising
 *                  events from a broadcast device.
 *
 * Parameters       start: start or stop observe.
 *
 * Returns          void
 *
 ******************************************************************************/
tBTM_STATUS BTM_BleObserve(bool start, uint8_t duration,
                           tBTM_INQ_RESULTS_CB* p_results_cb,
                           tBTM_CMPL_CB* p_cmpl_cb);

/*******************************************************************************
 *
 * Function         BTM_BleOpportunisticObserve
 *
 * Description      Register/unregister opportunistic scan callback. This method
 *                  does not trigger scan start/stop, but if scan is ever started,
 *                  this callback would get called with scan results. Additionally,
 *                  this callback is not reset on each scan start/stop. It's
 *                  intended to be used by LE Audio related profiles, that would
 *                  find yet unpaired members of CSIS set, or broadcasts.
 *
 * Parameters       enable: enable/disable observing.
 *                  p_results_cb: callback for results.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleOpportunisticObserve(bool enable,
                                 tBTM_INQ_RESULTS_CB* p_results_cb);

/*******************************************************************************
 *
 * Function         BTM_BleTargetAnnouncementObserve
 *
 * Description      Register/Unregister client interested in the targeted
 *                  announcements. Not that it is client responsible for parsing
 *                  advertising data.
 *
 * Parameters       start: start or stop observe.
 *                  p_results_cb: callback for results.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleTargetAnnouncementObserve(bool enable,
                                      tBTM_INQ_RESULTS_CB* p_results_cb);

void BTM_EnableInterlacedInquiryScan();

void BTM_EnableInterlacedPageScan();

/*******************************************************************************
 *
 * Function         BTM_SetInquiryMode
 *
 * Description      This function is called to set standard, with RSSI
 *                  mode or extended of the inquiry for local device.
 *
 * Input Params:    BTM_INQ_RESULT_STANDARD, BTM_INQ_RESULT_WITH_RSSI or
 *                  BTM_INQ_RESULT_EXTENDED
 *
 * Returns          BTM_SUCCESS if successful
 *                  BTM_NO_RESOURCES if couldn't get a memory pool buffer
 *                  BTM_ILLEGAL_VALUE if a bad parameter was detected
 *                  BTM_WRONG_MODE if the device is not up.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetInquiryMode(uint8_t mode);

/*******************************************************************************
 *
 * Function         BTM_SetConnectability
 *
 * Description      This function is called to set the device into or out of
 *                  connectable mode. Discoverable mode means page scans are
 *                  enabled.
 *
 * Returns          BTM_SUCCESS if successful
 *                  BTM_ILLEGAL_VALUE if a bad parameter is detected
 *                  BTM_NO_RESOURCES if could not allocate a message buffer
 *                  BTM_WRONG_MODE if the device is not up.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetConnectability(uint16_t page_mode, uint16_t window,
                                  uint16_t interval);

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteDeviceName
 *
 * Description      This function initiates a remote device HCI command to the
 *                  controller and calls the callback when the process has
 *                  completed.
 *
 * Input Params:    remote_bda      - device address of name to retrieve
 *                  p_cb            - callback function called when
 *                                    BTM_CMD_STARTED is returned.
 *                                    A pointer to tBTM_REMOTE_DEV_NAME is
 *                                    passed to the callback.
 *
 * Returns
 *                  BTM_CMD_STARTED is returned if the request was successfully
 *                                  sent to HCI.
 *                  BTM_BUSY if already in progress
 *                  BTM_UNKNOWN_ADDR if device address is bad
 *                  BTM_NO_RESOURCES if resources could not be allocated to
 *                                   start the command
 *                  BTM_WRONG_MODE if the device is not up.
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadRemoteDeviceName(const RawAddress& remote_bda,
                                     tBTM_NAME_CMPL_CB* p_cb,
                                     tBT_TRANSPORT transport);

/*******************************************************************************
 *
 * Function         BTM_CancelRemoteDeviceName
 *
 * Description      This function initiates the cancel request for the specified
 *                  remote device.
 *
 * Input Params:    None
 *
 * Returns
 *                  BTM_CMD_STARTED is returned if the request was successfully
 *                                  sent to HCI.
 *                  BTM_NO_RESOURCES if resources could not be allocated to
 *                                   start the command
 *                  BTM_WRONG_MODE if there is no active remote name request.
 *
 ******************************************************************************/
tBTM_STATUS BTM_CancelRemoteDeviceName(void);

/*******************************************************************************
 *
 * Function         BTM_InqDbRead
 *
 * Description      This function looks through the inquiry database for a match
 *                  based on Bluetooth Device Address. This is the application's
 *                  interface to get the inquiry details of a specific BD
 *                  address.
 *
 * Returns          pointer to entry, or NULL if not found
 *
 ******************************************************************************/
tBTM_INQ_INFO* BTM_InqDbRead(const RawAddress& p_bda);

/*******************************************************************************
 *
 * Function         BTM_InqDbFirst
 *
 * Description      This function looks through the inquiry database for the
 *                  first used entry, and returns that. This is used in
 *                  conjunction with BTM_InqDbNext by applications as a way to
 *                  walk through the inquiry database.
 *
 * Returns          pointer to first in-use entry, or NULL if DB is empty
 *
 ******************************************************************************/
tBTM_INQ_INFO* BTM_InqDbFirst(void);

/*******************************************************************************
 *
 * Function         BTM_InqDbNext
 *
 * Description      This function looks through the inquiry database for the
 *                  next used entry, and returns that.  If the input parameter
 *                  is NULL, the first entry is returned.
 *
 * Returns          pointer to next in-use entry, or NULL if no more found.
 *
 ******************************************************************************/
tBTM_INQ_INFO* BTM_InqDbNext(tBTM_INQ_INFO* p_cur);

/*******************************************************************************
 *
 * Function         BTM_ClearInqDb
 *
 * Description      This function is called to clear out a device or all devices
 *                  from the inquiry database.
 *
 * Parameter        p_bda - (input) BD_ADDR ->  Address of device to clear
 *                                              (NULL clears all entries)
 *
 * Returns          BTM_BUSY if an inquiry, get remote name, or event filter
 *                          is active, otherwise BTM_SUCCESS
 *
 ******************************************************************************/
tBTM_STATUS BTM_ClearInqDb(const RawAddress* p_bda);

/*******************************************************************************
 *
 * Function         BTM_HasEirService
 *
 * Description      This function is called to know if UUID in bit map of UUID.
 *
 * Parameters       p_eir_uuid - bit map of UUID list
 *                  uuid16 - UUID 16-bit
 *
 * Returns          true - if found
 *                  false - if not found
 *
 ******************************************************************************/
bool BTM_HasEirService(const uint32_t* p_eir_uuid, uint16_t uuid16);

/*******************************************************************************
 *
 * Function         BTM_AddEirService
 *
 * Description      This function is called to add a service in the bit map UUID
 *                  list.
 *
 * Parameters       p_eir_uuid - bit mask of UUID list for EIR
 *                  uuid16 - UUID 16-bit
 *
 * Returns          None
 *
 ******************************************************************************/
void BTM_AddEirService(uint32_t* p_eir_uuid, uint16_t uuid16);

/*******************************************************************************
 *
 * Function         BTM_ReadConnectionAddr
 *
 * Description      Read the local device address.
 *
 *                  pseudo_addr - pseudo address used by the stack
 *                  conn_addr   - returned addresss
 *                  p_addr_type - returned address type
 *                  ota_address - if set to true, function will provide RPA address
 *                                if it was used during connection. e.g. It should
 *                                be set to true by SMP module.
 * Returns          void
 *
 ******************************************************************************/
void BTM_ReadConnectionAddr(const RawAddress& pseudo_addr,
                            RawAddress& local_conn_addr,
                            tBLE_ADDR_TYPE* p_addr_type,
                            bool ota_address = false);

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteConnectionAddr
 *
 * Description      Read the remote device address.
 *                  pseudo_addr - pseudo address used by the stack
 *                  conn_addr   - returned addresss
 *                  p_addr_type - returned address type
 *                  ota_address - if set to true, function will provide RPA address
 *                                if it was used during connection. It should be set
 *                                to true by SMP module.
 *
 * Returns          true if remote address found, false otherwise.
 *
 ******************************************************************************/
bool BTM_ReadRemoteConnectionAddr(const RawAddress& pseudo_addr,
                                  RawAddress& conn_addr,
                                  tBLE_ADDR_TYPE* p_addr_type,
                                  bool ota_address = false);

/******************************************************************************
 *
 * Function         BTM_BleSetConnScanParams
 *
 * Description      Set scan parameters used in BLE connection request
 *
 * Parameters:      scan_interval    - scan interval
 *                  scan_window      - scan window
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleSetConnScanParams(uint32_t scan_interval, uint32_t scan_window);

/********************************************************
 *
 * Function         BTM_BleSetPrefConnParams
 *
 * Description      Set a peripheral's preferred connection parameters. When
 *                  any of the value does not want to be updated while others
 *                  do, use BTM_BLE_CONN_PARAM_UNDEF for the ones want to
 *                  leave untouched.
 *
 * Parameters:      bd_addr          - BD address of the peripheral
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
                              uint16_t supervision_tout);

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
                                       tBT_TRANSPORT transport);

/*******************************************************************************
 *
 * Function         BTM_BleAdvFilterParamSetup
 *
 * Description      This function is called to setup the adv data payload filter
 *                  condition.
 *
 ******************************************************************************/
void BTM_BleAdvFilterParamSetup(
    tBTM_BLE_SCAN_COND_OP action, tBTM_BLE_PF_FILT_INDEX filt_index,
    std::unique_ptr<btgatt_filt_param_setup_t> p_filt_params,
    tBTM_BLE_PF_PARAM_CB cb);

/*******************************************************************************
 *
 * Function          BTM_BleMaxMultiAdvInstanceCount
 *
 * Description      Returns the maximum number of multi adv instances supported
 *                  by the controller.
 *
 * Returns          Max multi adv instance count
 *
 ******************************************************************************/
uint8_t BTM_BleMaxMultiAdvInstanceCount();

void BTM_reset_complete();

/*******************************************************************************
 *
 * Function         BTM_IsDeviceUp
 *
 * Description      This function is called to check if the device is up.
 *
 * Returns          true if device is up, else false
 *
 ******************************************************************************/
bool BTM_IsDeviceUp(void);

/*******************************************************************************
 *
 * Function         BTM_SetLocalDeviceName
 *
 * Description      This function is called to set the local device name.
 *
 * Returns          BTM_CMD_STARTED if successful, otherwise an error
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetLocalDeviceName(const char* p_name);

/*******************************************************************************
 *
 * Function         BTM_SetDeviceClass
 *
 * Description      This function is called to set the local device class
 *
 * Returns          BTM_SUCCESS if successful, otherwise an error
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetDeviceClass(DEV_CLASS dev_class);

/*******************************************************************************
 *
 * Function         BTM_ReadLocalDeviceName
 *
 * Description      This function is called to read the local device name.
 *
 * Returns          status of the operation
 *                  If success, BTM_SUCCESS is returned and p_name points stored
 *                              local device name
 *                  If BTM doesn't store local device name, BTM_NO_RESOURCES is
 *                              is returned and p_name is set to NULL
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadLocalDeviceName(const char** p_name);

/*******************************************************************************
 *
 * Function         BTM_ReadLocalDeviceNameFromController
 *
 * Description      Get local device name from controller. Do not use cached
 *                  name (used to get chip-id prior to btm reset complete).
 *
 * Returns          BTM_CMD_STARTED if successful, otherwise an error
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadLocalDeviceNameFromController(
    tBTM_CMPL_CB* p_rln_cmpl_cback);

/*******************************************************************************
 *
 * Function         BTM_ReadDeviceClass
 *
 * Description      This function is called to read the local device class
 *
 * Returns          the device class
 *
 ******************************************************************************/
DEV_CLASS BTM_ReadDeviceClass(void);

/*******************************************************************************
 *
 * Function         BTM_RegisterForVSEvents
 *
 * Description      This function is called to register/deregister for vendor
 *                  specific HCI events.
 *
 *                  If is_register=true, then the function will be registered;
 *                  otherwise the function will be deregistered.
 *
 * Returns          BTM_SUCCESS if successful,
 *                  BTM_BUSY if maximum number of callbacks have already been
 *                           registered.
 *
 ******************************************************************************/
tBTM_STATUS BTM_RegisterForVSEvents(tBTM_VS_EVT_CB* p_cb, bool is_register);

/*******************************************************************************
 *
 * Function         BTM_VendorSpecificCommand
 *
 * Description      Send a vendor specific HCI command to the controller.
 *
 ******************************************************************************/
void BTM_VendorSpecificCommand(uint16_t opcode, uint8_t param_len,
                               uint8_t* p_param_buf, tBTM_VSC_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         BTM_WritePageTimeout
 *
 * Description      Send HCI Wite Page Timeout.
 *
 ******************************************************************************/
void BTM_WritePageTimeout(uint16_t timeout);

/*******************************************************************************
 *
 * Function         BTM_WriteVoiceSettings
 *
 * Description      Send HCI Write Voice Settings command.
 *                  See hcidefs.h for settings bitmask values.
 *
 ******************************************************************************/
void BTM_WriteVoiceSettings(uint16_t settings);

/*******************************************************************************
 *
 * Function         BTM_EnableTestMode
 *
 * Description      Send HCI the enable device under test command.
 *
 *                  Note: Controller can only be taken out of this mode by
 *                      resetting the controller.
 *
 * Returns
 *      BTM_SUCCESS         Command sent.
 *      BTM_NO_RESOURCES    If out of resources to send the command.
 *
 *
 ******************************************************************************/
tBTM_STATUS BTM_EnableTestMode(void);

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteVersion
 *
 * Description      This function is called to read a remote device's version
 *
 * Returns          true if valid, false otherwise
 *
 ******************************************************************************/
bool BTM_ReadRemoteVersion(const RawAddress& addr, uint8_t* lmp_version,
                           uint16_t* manufacturer, uint16_t* lmp_sub_version);

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteFeatures
 *
 * Description      This function is called to read a remote device's
 *                  supported features mask (features mask located at page 0)
 *
 * Returns          pointer to the remote supported features mask
 *                  The size of device features mask page is
 *                  HCI_FEATURE_BYTES_PER_PAGE bytes.
 *
 ******************************************************************************/
uint8_t* BTM_ReadRemoteFeatures(const RawAddress& addr);

/*****************************************************************************
 *  ACL CHANNEL MANAGEMENT FUNCTIONS
 ****************************************************************************/
void BTM_unblock_sniff_mode_for(const RawAddress& peer_addr);
void BTM_block_sniff_mode_for(const RawAddress& peer_addr);
void BTM_unblock_role_switch_for(const RawAddress& peer_addr);
void BTM_block_role_switch_for(const RawAddress& peer_addr);

void BTM_default_unblock_role_switch();
void BTM_default_block_role_switch();

/*******************************************************************************
 *
 * Function         BTM_SetDefaultLinkSuperTout
 *
 * Description      Set the default value for HCI "Write Link Supervision
 *                  Timeout" command to use when an ACL link is created.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_SetDefaultLinkSuperTout(uint16_t timeout);

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
 *                  to a specific remote BD Address.
 *
 * Returns          true if connection is up, else false.
 *
 ******************************************************************************/
bool BTM_IsAclConnectionUp(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport);

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
 * Function         BTM_SwitchRole
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
tBTM_STATUS BTM_SwitchRole(const RawAddress& remote_bd_addr, uint8_t new_role);

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
 * Function         BTM_ReadAutomaticFlushTimeout
 *
 * Description      This function is called to read the automatic flush timeout.
 *                  The result is returned in the callback.
 *                  (tBTM_AUTOMATIC_FLUSH_TIMEOUT_RESULT)
 *
 * Returns          BTM_CMD_STARTED if command issued to controller.
 *                  BTM_NO_RESOURCES if memory couldn't be allocated to issue
 *                                   the command
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *                  BTM_BUSY if command is already in progress
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadAutomaticFlushTimeout(const RawAddress& remote_bda,
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

/*****************************************************************************
 *  (e)SCO CHANNEL MANAGEMENT FUNCTIONS
 ****************************************************************************/
/*******************************************************************************
 *
 * Function         BTM_CreateSco
 *
 * Description      This function is called to create an SCO connection. If the
 *                  "is_orig" flag is true, the connection will be originated,
 *                  otherwise BTM will wait for the other side to connect.
 *
 * Returns          BTM_UNKNOWN_ADDR if the ACL connection is not up
 *                  BTM_BUSY         if another SCO being set up to
 *                                   the same BD address
 *                  BTM_NO_RESOURCES if the max SCO limit has been reached
 *                  BTM_CMD_STARTED  if the connection establishment is started.
 *                                   In this case, "*p_sco_inx" is filled in
 *                                   with the sco index used for the connection.
 *
 ******************************************************************************/
tBTM_STATUS BTM_CreateSco(const RawAddress* remote_bda, bool is_orig,
                          uint16_t pkt_types, uint16_t* p_sco_inx,
                          tBTM_SCO_CB* p_conn_cb, tBTM_SCO_CB* p_disc_cb);

/*******************************************************************************
 *
 * Function         BTM_RemoveSco
 *
 * Description      This function is called to remove a specific SCO connection.
 *
 * Returns          BTM_CMD_STARTED if successfully initiated, otherwise error
 *
 ******************************************************************************/
tBTM_STATUS BTM_RemoveSco(uint16_t sco_inx);

/*******************************************************************************
 *
 * Function         BTM_ReadScoBdAddr
 *
 * Description      This function is read the remote BD Address for a specific
 *                  SCO connection,
 *
 * Returns          pointer to BD address or NULL if not known
 *
 ******************************************************************************/
const RawAddress* BTM_ReadScoBdAddr(uint16_t sco_inx);

/*******************************************************************************
 *
 * Function         BTM_SetEScoMode
 *
 * Description      This function sets up the negotiated parameters for SCO or
 *                  eSCO, and sets as the default mode used for calls to
 *                  BTM_CreateSco.  It can be called only when there are no
 *                  active (e)SCO links.
 *
 * Returns          BTM_SUCCESS if the successful.
 *                  BTM_BUSY if there are one or more active (e)SCO links.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetEScoMode(enh_esco_params_t* p_parms);

/*******************************************************************************
 *
 * Function         BTM_RegForEScoEvts
 *
 * Description      This function registers a SCO event callback with the
 *                  specified instance.  It should be used to received
 *                  connection indication events and change of link parameter
 *                  events.
 *
 * Returns          BTM_SUCCESS if the successful.
 *                  BTM_ILLEGAL_VALUE if there is an illegal sco_inx
 *
 ******************************************************************************/
tBTM_STATUS BTM_RegForEScoEvts(uint16_t sco_inx, tBTM_ESCO_CBACK* p_esco_cback);

/*******************************************************************************
 *
 * Function         BTM_EScoConnRsp
 *
 * Description      This function is called upon receipt of an (e)SCO connection
 *                  request event (BTM_ESCO_CONN_REQ_EVT) to accept or reject
 *                  the request. Parameters used to negotiate eSCO links.
 *                  If p_parms is NULL, then values set through BTM_SetEScoMode
 *                  are used.
 *                  If the link type of the incoming request is SCO, then only
 *                  the tx_bw, max_latency, content format, and packet_types are
 *                  valid.  The hci_status parameter should be
 *                  ([0x0] to accept, [0x0d..0x0f] to reject)
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_EScoConnRsp(uint16_t sco_inx, uint8_t hci_status,
                     enh_esco_params_t* p_parms);

/*******************************************************************************
 *
 * Function         BTM_GetNumScoLinks
 *
 * Description      This function returns the number of active SCO links.
 *
 * Returns          uint8_t
 *
 ******************************************************************************/
uint8_t BTM_GetNumScoLinks(void);

/*******************************************************************************
 *
 * Function         BTM_GetScoDebugDump
 *
 * Description      Get the status of SCO. This function is only used for
 *                  testing and debugging purposes.
 *
 * Returns          Data with SCO related debug dump.
 *
 ******************************************************************************/
tBTM_SCO_DEBUG_DUMP BTM_GetScoDebugDump(void);

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
tBT_DEVICE_TYPE BTM_GetPeerDeviceTypeFromFeatures(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTM_GetHCIConnHandle
 *
 * Description      This function is called to get the handle for an ACL
 *                  connection to a specific remote BD Address.
 *
 * Returns          the handle of the connection, or 0xFFFF if none.
 *
 ******************************************************************************/
uint16_t BTM_GetHCIConnHandle(const RawAddress& remote_bda,
                              tBT_TRANSPORT transport);

/**
 *
 * BLE API
 */

/**
 * This function is called to set scan parameters. |cb| is called with operation
 * status
 **/
void BTM_BleSetScanParams(uint32_t scan_interval, uint32_t scan_window,
                          tBLE_SCAN_MODE scan_type,
                          base::Callback<void(uint8_t)> cb);

/*******************************************************************************
 *
 * Function         BTM_BleGetVendorCapabilities
 *
 * Description      This function reads local LE features
 *
 * Parameters       p_cmn_vsc_cb : Locala LE capability structure
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleGetVendorCapabilities(tBTM_BLE_VSC_CB* p_cmn_vsc_cb);

/*******************************************************************************
 *
 * Function         BTM_BleSetStorageConfig
 *
 * Description      This function is called to setup storage configuration and
 *                  setup callbacks.
 *
 * Parameters       uint8_t batch_scan_full_max -Batch scan full maximum
 *                  uint8_t batch_scan_trunc_max - Batch scan truncated value
 *maximum uint8_t batch_scan_notify_threshold - Threshold value cb - Setup
 *callback tBTM_BLE_SCAN_THRESHOLD_CBACK *p_thres_cback -Threshold callback void
 **p_ref - Reference value
 *
 *
 ******************************************************************************/
void BTM_BleSetStorageConfig(uint8_t batch_scan_full_max,
                             uint8_t batch_scan_trunc_max,
                             uint8_t batch_scan_notify_threshold,
                             base::Callback<void(uint8_t /* status */)> cb,
                             tBTM_BLE_SCAN_THRESHOLD_CBACK* p_thres_cback,
                             tBTM_BLE_REF_VALUE ref_value);

/* This function is called to enable batch scan */
void BTM_BleEnableBatchScan(tBTM_BLE_BATCH_SCAN_MODE scan_mode,
                            uint32_t scan_interval, uint32_t scan_window,
                            tBTM_BLE_DISCARD_RULE discard_rule,
                            tBLE_ADDR_TYPE addr_type,
                            base::Callback<void(uint8_t /* status */)> cb);

/* This function is called to disable batch scanning */
void BTM_BleDisableBatchScan(base::Callback<void(uint8_t /* status */)> cb);

/* This function is called to read batch scan reports */
void BTM_BleReadScanReports(tBLE_SCAN_MODE scan_mode,
                            tBTM_BLE_SCAN_REP_CBACK cb);

/* This function is called to setup the callback for tracking */
void BTM_BleTrackAdvertiser(tBTM_BLE_TRACK_ADV_CBACK* p_track_cback,
                            tBTM_BLE_REF_VALUE ref_value);

/******************************************************************************
 *
 * Function         BTM_BleReadControllerFeatures
 *
 * Description      Reads BLE specific controller features
 *
 * Parameters:      tBTM_BLE_CTRL_FEATURES_CBACK : Callback to notify when
 *                  features are read
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleReadControllerFeatures(tBTM_BLE_CTRL_FEATURES_CBACK* p_vsc_cback);

/*******************************************************************************
 *
 * Function         BTM__BLEReadDiscoverability
 *
 * Description      This function is called to read the current LE
 *                  discoverability mode of the device.
 *
 * Returns          BTM_BLE_NON_DISCOVERABLE ,BTM_BLE_LIMITED_DISCOVERABLE or
 *                     BTM_BLE_GENRAL_DISCOVERABLE
 *
 ******************************************************************************/
uint16_t BTM_BleReadDiscoverability();

/*******************************************************************************
 *
 * Function         BTM__BLEReadConnectability
 *
 * Description      This function is called to read the current LE
 *                  connectibility mode of the device.
 *
 * Returns          BTM_BLE_NON_CONNECTABLE or BTM_BLE_CONNECTABLE
 *
 ******************************************************************************/
uint16_t BTM_BleReadConnectability();

/**
 * This functions are called to configure the adv data payload filter condition
 */
/*******************************************************************************
 *
 * Function         BTM_BleGetEnergyInfo
 *
 * Description      This function obtains the energy info
 *
 * Parameters       p_ener_cback - Callback pointer
 *
 * Returns          status
 *
 ******************************************************************************/
tBTM_STATUS BTM_BleGetEnergyInfo(tBTM_BLE_ENERGY_INFO_CBACK* p_ener_cback);

/*******************************************************************************
 *
 * Function         BTM_ClearEventFilter
 *
 * Description      Clears the event filter in the controller
 *
 * Returns          Return btm status
 *
 ******************************************************************************/
tBTM_STATUS BTM_ClearEventFilter(void);

/*******************************************************************************
 *
 * Function         BTM_ClearEventMask
 *
 * Description      Clears the event mask in the controller
 *
 * Returns          Return btm status
 *
 ******************************************************************************/
tBTM_STATUS BTM_ClearEventMask(void);

/*******************************************************************************
 *
 * Function         BTM_ClearFilterAcceptList
 *
 * Description      Clears the connect list in the controller
 *
 * Returns          Return btm status
 *
 ******************************************************************************/
tBTM_STATUS BTM_ClearFilterAcceptList(void);

/*******************************************************************************
 *
 * Function         BTM_DisconnectAllAcls
 *
 * Description      Disconnects all of the ACL connections
 *
 * Returns          Return btm status
 *
 ******************************************************************************/
tBTM_STATUS BTM_DisconnectAllAcls(void);

/*******************************************************************************
 *
 * Function         BTM_LeRand
 *
 * Description      Retrieves a random number from the controller
 *
 * Parameters       cb - The callback to receive the random number
 *
 * Returns          Return btm status
 *
 ******************************************************************************/
using LeRandCallback = base::OnceCallback<void(uint64_t)>;
tBTM_STATUS BTM_LeRand(LeRandCallback);

/*******************************************************************************
 *
 * Function        BTM_SetEventFilterConnectionSetupAllDevices
 *
 * Description    Tell the controller to allow all devices
 *
 * Parameters
 *
 *******************************************************************************/
tBTM_STATUS BTM_SetEventFilterConnectionSetupAllDevices(void);

/*******************************************************************************
 *
 * Function        BTM_AllowWakeByHid
 *
 * Description     Allow the device to be woken by HID devices
 *
 * Parameters      std::vector of RawAddress
 *
 *******************************************************************************/
tBTM_STATUS BTM_AllowWakeByHid(
    std::vector<RawAddress> classic_hid_devices,
    std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices);

/*******************************************************************************
 *
 * Function        BTM_RestoreFilterAcceptList
 *
 * Description    Floss: Restore the state of the for the filter accept list
 *
 * Parameters
 *
 *******************************************************************************/
tBTM_STATUS BTM_RestoreFilterAcceptList(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices);

/*******************************************************************************
 *
 * Function        BTM_SetDefaultEventMaskExcept
 *
 * Description    Floss: Set the default event mask for Classic and LE except
 *                the given values (they will be disabled in the final set
 *                mask).
 *
 * Parameters     Bits set for event mask and le event mask that should be
 *                disabled in the final value.
 *
 *******************************************************************************/
tBTM_STATUS BTM_SetDefaultEventMaskExcept(uint64_t mask, uint64_t le_mask);

/*******************************************************************************
 *
 * Function        BTM_SetEventFilterInquiryResultAllDevices
 *
 * Description    Floss: Set the event filter to inquiry result device all
 *
 * Parameters
 *
 *******************************************************************************/
tBTM_STATUS BTM_SetEventFilterInquiryResultAllDevices(void);

/*******************************************************************************
 *
 * Function         BTM_BleResetId
 *
 * Description      Resets the local BLE keys
 *
 *******************************************************************************/
tBTM_STATUS BTM_BleResetId(void);

/*******************************************************************************
 *
 * Function         BTM_BleGetNumberOfAdvertisingInstancesInUse
 *
 * Description      Obtains the number of BLE advertising instances in use
 *
 * Returns          Return the number of BLE advertising instances in use
 *******************************************************************************/
size_t BTM_BleGetNumberOfAdvertisingInstancesInUse(void);

/**
 * Send remote name request to GD shim Name module
 */
void SendRemoteNameRequest(const RawAddress& raw_address);

}  // namespace shim
}  // namespace bluetooth
