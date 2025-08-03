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
 *  This file contains the Bluetooth Manager (BTM) API function external
 *  definitions.
 *
 ******************************************************************************/
#ifndef BTM_BLE_API_H
#define BTM_BLE_API_H

#include <base/functional/callback_forward.h>
#include <hardware/bt_common_types.h>

#include <cstdint>
#include <memory>

#include "btm_api.h"
#include "btm_ble_api_types.h"
#include "stack/btm/neighbor_inquiry.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

void btm_ble_init();
void btm_ble_free();

/*****************************************************************************
 *  EXTERNAL FUNCTION DECLARATIONS
 ****************************************************************************/

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
 * Function         BTM_BleGetDynamicAudioBuffer
 *
 * Description      This function reads dynamic audio buffer
 *
 * Parameters       p_dynamic_audio_buffer_cb : Dynamic Audio Buffer structure
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleGetDynamicAudioBuffer(
    tBTM_BT_DYNAMIC_AUDIO_BUFFER_CB* p_dynamic_audio_buffer_cb);

/*******************************************************************************
 *
 * Function         BTM_BleObserve
 *
 * Description      This procedure keep the device listening for advertising
 *                  events from a broadcast device.
 *
 * Parameters       start: start or stop observe.
 *                  duration: how long the scan should last, in seconds. 0 means
 *                  scan without timeout. Starting the scan second time without
 *                  timeout will disable the timer.
 *                  low_latency_scan: whether this is a low latency scan,
 *                                    default is false.
 *
 * Returns          void
 *
 ******************************************************************************/
tBTM_STATUS BTM_BleObserve(bool start, uint8_t duration,
                           tBTM_INQ_RESULTS_CB* p_results_cb,
                           tBTM_CMPL_CB* p_cmpl_cb,
                           bool low_latency_scan = false);

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
bool BTM_IsBleConnection(uint16_t conn_handle);

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteConnectionAddr
 *
 * Description      Read the remote device address currently used.
 *
 * Returns          void
 *
 ******************************************************************************/
bool BTM_ReadRemoteConnectionAddr(const RawAddress& pseudo_addr,
                                  RawAddress& conn_addr,
                                  tBLE_ADDR_TYPE* p_addr_type,
                                  bool ota_address);


#include "stack/btm/btm_ble_bgconn.h"

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
                     tBLE_ADDR_TYPE* p_addr_type);

/*******************************************************************************
 *
 * Function         BTM_GetRemoteDeviceName
 *
 * Description      This function is called to get the dev name of remote device
 *                  from NV
 *
 * Returns          true if success; otherwise failed.
 *
 *******************************************************************************/
bool BTM_GetRemoteDeviceName(const RawAddress& bda, BD_NAME bd_name);

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
 * Function          BTM_BleMaxMultiAdvInstanceCount
 *
 * Description      Returns the maximum number of multi adv instances supported
 *                  by the controller.
 *
 * Returns          Max multi adv instance count
 *
 ******************************************************************************/
uint8_t BTM_BleMaxMultiAdvInstanceCount();

/*******************************************************************************
 *
 * Function         BTM_BleReceiverTest
 *
 * Description      This function is called to start the LE Receiver test
 *
 * Parameter       rx_freq - Frequency Range
 *               p_cmd_cmpl_cback - Command Complete callback
 *
 ******************************************************************************/
void BTM_BleReceiverTest(uint8_t rx_freq, tBTM_CMPL_CB* p_cmd_cmpl_cback);

/*******************************************************************************
 *
 * Function         BTM_BleTransmitterTest
 *
 * Description      This function is called to start the LE Transmitter test
 *
 * Parameter       tx_freq - Frequency Range
 *                       test_data_len - Length in bytes of payload data in each
 *                                       packet
 *                       packet_payload - Pattern to use in the payload
 *                       p_cmd_cmpl_cback - Command Complete callback
 *
 ******************************************************************************/
void BTM_BleTransmitterTest(uint8_t tx_freq, uint8_t test_data_len,
                            uint8_t packet_payload,
                            tBTM_CMPL_CB* p_cmd_cmpl_cback);

/*******************************************************************************
 *
 * Function         BTM_BleTestEnd
 *
 * Description     This function is called to stop the in-progress TX or RX test
 *
 * Parameter       p_cmd_cmpl_cback - Command complete callback
 *
 ******************************************************************************/
void BTM_BleTestEnd(tBTM_CMPL_CB* p_cmd_cmpl_cback);

/*******************************************************************************
 *
 * Function         BTM_UseLeLink
 *
 * Description      Select the underlying physical link to use.
 *
 * Returns          true to use LE, false use BR/EDR.
 *
 ******************************************************************************/
bool BTM_UseLeLink(const RawAddress& bd_addr);

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
 * Function         BTM_SetBleDataLength
 *
 * Description      Set the maximum BLE transmission packet size
 *
 * Returns          BTM_SUCCESS if success; otherwise failed.
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetBleDataLength(const RawAddress& bd_addr,
                                 uint16_t tx_pdu_length);

/*******************************************************************************
 *
 * Function         BTM_BleReadPhy
 *
 * Description      To read the current PHYs for specified LE connection
 *
 *
 * Returns          BTM_SUCCESS if success; otherwise failed.
 *
 ******************************************************************************/
void BTM_BleReadPhy(
    const RawAddress& bd_addr,
    base::Callback<void(uint8_t tx_phy, uint8_t rx_phy, uint8_t status)> cb);

/*******************************************************************************
 *
 * Function         BTM_BleSetPhy
 *
 * Description      To set PHY preferences for specified LE connection
 *
 *
 * Returns          BTM_SUCCESS if success; otherwise failed.
 *
 ******************************************************************************/
void BTM_BleSetPhy(const RawAddress& bd_addr, uint8_t tx_phys, uint8_t rx_phys,
                   uint16_t phy_options);

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
                                 tBLE_ADDR_TYPE* p_addr_type);

using StartSyncCb = base::Callback<void(
    uint8_t /*status*/, uint16_t /*sync_handle*/, uint8_t /*advertising_sid*/,
    uint8_t /*address_type*/, RawAddress /*address*/, uint8_t /*phy*/,
    uint16_t /*interval*/)>;
using SyncReportCb = base::Callback<void(
    uint16_t /*sync_handle*/, int8_t /*tx_power*/, int8_t /*rssi*/,
    uint8_t /*status*/, std::vector<uint8_t> /*data*/)>;
using SyncLostCb = base::Callback<void(uint16_t /*sync_handle*/)>;
using BigInfoReportCb = base::Callback<void(uint16_t /*sync_handle*/, bool /*encrypted*/)>;

void btm_ble_periodic_adv_sync_established(uint8_t status, uint16_t sync_handle,
                                           uint8_t adv_sid,
                                           uint8_t address_type,
                                           const RawAddress& addr, uint8_t phy,
                                           uint16_t interval,
                                           uint8_t adv_clock_accuracy);
void btm_ble_periodic_adv_report(uint16_t sync_handle, uint8_t tx_power,
                                 int8_t rssi, uint8_t cte_type,
                                 uint8_t data_status, uint8_t data_len,
                                 const uint8_t* periodic_data);
void btm_ble_periodic_adv_sync_lost(uint16_t sync_handle);

void btm_ble_biginfo_adv_report_rcvd(const uint8_t* param, uint16_t param_len);
void btm_ble_periodic_adv_sync_tx_rcvd(const uint8_t* param,
                                       uint16_t param_len);

/*******************************************************************************
 *
 * Function         BTM_BleConfigPrivacy
 *
 * Description      This function is called to enable or disable the privacy in
 *                  the local device.
 *
 * Parameters       enable: true to enable it; false to disable it.
 *
 * Returns          bool    privacy mode set success; otherwise failed.
 *
 ******************************************************************************/
bool BTM_BleConfigPrivacy(bool enable);

/*******************************************************************************
 *
 * Function         BTM_BleLocalPrivacyEnabled
 *
 * Description        Checks if local device supports private address
 *
 * Returns          Return true if local privacy is enabled else false
 *
 ******************************************************************************/
bool BTM_BleLocalPrivacyEnabled(void);

#endif
