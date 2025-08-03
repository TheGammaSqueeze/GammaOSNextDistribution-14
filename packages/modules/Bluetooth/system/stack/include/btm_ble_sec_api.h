/*
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
 */

#pragma once

#include <base/functional/callback_forward.h>
#include <hardware/bt_common_types.h>

#include <cstdint>
#include <optional>

#include "btm_ble_api_types.h"
#include "btm_ble_sec_api_types.h"
#include "stack/include/bt_device_type.h"
#include "types/raw_address.h"

/*******************************************************************************
 *
 * Function         BTM_SecAddBleDevice
 *
 * Description      Add/modify device.  This function will be normally called
 *                  during host startup to restore all required information
 *                  for a LE device stored in the NVRAM.
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  dev_type         - Remote device's device type.
 *                  addr_type        - LE device address type.
 *
 ******************************************************************************/
void BTM_SecAddBleDevice(const RawAddress& bd_addr, tBT_DEVICE_TYPE dev_type,
                         tBLE_ADDR_TYPE addr_type);

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
 ******************************************************************************/
void BTM_SecAddBleKey(const RawAddress& bd_addr, tBTM_LE_KEY_VALUE* p_le_key,
                      tBTM_LE_KEY_TYPE key_type);

/** Returns local device encryption root (ER) */
const Octet16& BTM_GetDeviceEncRoot();

/** Returns local device identity root (IR) */
const Octet16& BTM_GetDeviceIDRoot();

/** Return local device DHK. */
const Octet16& BTM_GetDeviceDHK();

/*******************************************************************************
 *
 * Function         BTM_SecurityGrant
 *
 * Description      This function is called to grant security process.
 *
 * Parameters       bd_addr - peer device bd address.
 *                  res     - result of the operation BTM_SUCCESS if success.
 *                            Otherwise, BTM_REPEATED_ATTEMPTS is too many
 *                            attempts.
 *
 * Returns          None
 *
 ******************************************************************************/
void BTM_SecurityGrant(const RawAddress& bd_addr, uint8_t res);

/*******************************************************************************
 *
 * Function         BTM_BlePasskeyReply
 *
 * Description      This function is called after Security Manager submitted
 *                  passkey request to the application.
 *
 * Parameters:      bd_addr - Address of the device for which passkey was
 *                            requested
 *                  res     - result of the operation SMP_SUCCESS if success
 *                  passkey - numeric value in the range of
 *                               BTM_MIN_PASSKEY_VAL(0) -
 *                               BTM_MAX_PASSKEY_VAL(999999(0xF423F)).
 *
 ******************************************************************************/
void BTM_BlePasskeyReply(const RawAddress& bd_addr, uint8_t res,
                         uint32_t passkey);

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
void BTM_BleConfirmReply(const RawAddress& bd_addr, uint8_t res);

/*******************************************************************************
 *
 * Function         BTM_LeOobDataReply
 *
 * Description      This function is called to provide the OOB data for
 *                  SMP in response to BTM_LE_OOB_REQ_EVT
 *
 * Parameters:      bd_addr     - Address of the peer device
 *                  res         - result of the operation SMP_SUCCESS if success
 *                  p_data      - simple pairing Randomizer  C.
 *
 ******************************************************************************/
void BTM_BleOobDataReply(const RawAddress& bd_addr, uint8_t res, uint8_t len,
                         uint8_t* p_data);

/*******************************************************************************
 *
 * Function         BTM_BleSecureConnectionOobDataReply
 *
 * Description      This function is called to provide the OOB data for
 *                  SMP in response to BTM_LE_OOB_REQ_EVT when secure connection
 *                  data is available
 *
 * Parameters:      bd_addr     - Address of the peer device
 *                  p_c         - pointer to Confirmation
 *                  p_r         - pointer to Randomizer.
 *
 ******************************************************************************/
void BTM_BleSecureConnectionOobDataReply(const RawAddress& bd_addr,
                                         uint8_t* p_c, uint8_t* p_r);

/*******************************************************************************
 *
 * Function         BTM_BleDataSignature
 *
 * Description      This function is called to sign the data using AES128 CMAC
 *                  algorithm.
 *
 * Parameter        bd_addr: target device the data to be signed for.
 *                  p_text: singing data
 *                  len: length of the signing data
 *                  signature: output parameter where data signature is going to
 *                             be stored.
 *
 * Returns          true if signing sucessul, otherwise false.
 *
 ******************************************************************************/
bool BTM_BleDataSignature(const RawAddress& bd_addr, uint8_t* p_text,
                          uint16_t len, BLE_SIGNATURE signature);

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
                            uint16_t len, uint32_t counter, uint8_t* p_comp);



/*******************************************************************************
 *
 * Function         BTM_BleLoadLocalKeys
 *
 * Description      Local local identity key, encryption root or sign counter.
 *
 * Parameters:      key_type: type of key, can be BTM_BLE_KEY_TYPE_ID,
 *                            BTM_BLE_KEY_TYPE_ER
 *                            or BTM_BLE_KEY_TYPE_COUNTER.
 *                  p_key: pointer to the key.
*
 * Returns          non2.
 *
 ******************************************************************************/
void BTM_BleLoadLocalKeys(uint8_t key_type, tBTM_BLE_LOCAL_KEYS* p_key);

/*******************************************************************************
 *
 * Function         BTM_BleGetPeerLTK
 *
 * Description      This function is used to get the long term key of
 *                  a bonded peer (LE) device.
 *
 * Parameters:      address: address of the peer device
 *
 * Returns          the ltk contained in std::optional if the remote device
 *                  is present in security database
 *                  std::nullopt if the device is not present
 *
 ******************************************************************************/
std::optional<Octet16> BTM_BleGetPeerLTK(const RawAddress address);

/*******************************************************************************
 *
 * Function         BTM_BleGetPeerIRK
 *
 * Description      This function is used to get the IRK of a bonded
 *                  peer (LE) device.
 *
 * Parameters:      address: address of the peer device
 *
 * Returns          the ltk contained in std::optional if the remote device
 *                  is present in security database
 *                  std::nullopt if the device is not present
 *
 ******************************************************************************/
std::optional<Octet16> BTM_BleGetPeerIRK(const RawAddress address);

/*******************************************************************************
 *
 * Function         BTM_BleIsLinkKeyKnown
 *
 * Description      This function is used to check whether the link key
 *                  of a peer (LE) device is known or not
 *
 * Parameters:      address: address of the peer device
 *
 * Returns          true if the link key is known
 *                  false otherwise
 *
 ******************************************************************************/
bool BTM_BleIsLinkKeyKnown(const RawAddress address);

/*******************************************************************************
 *
 * Function         BTM_BleGetIdentityAddress
 *
 * Description      This function is called to get the identity address
 *                  (with type) of a peer (LE) device.
 *
 * Parameters:      address: address of the peer device
 *
 * Returns          the identity address in std::optional if the remote device
 *                  is present in security database
 *                  std::nullopt if the device is not present
 *
 ******************************************************************************/
std::optional<tBLE_BD_ADDR> BTM_BleGetIdentityAddress(const RawAddress address);
