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

#pragma once

#include <cstdint>

#include "stack/include/bt_dev_class.h"
#include "stack/include/bt_name.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_status.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  SECURITY MANAGEMENT FUNCTIONS
 ****************************************************************************/

/*******************************************************************************
 *
 * Function         BTM_SecAddDevice
 *
 * Description      Add/modify device.  This function will be normally called
 *                  during host startup to restore all required information
 *                  stored in the NVRAM.
 *                  dev_class, bd_name, link_key, and features are NULL if
 *                  unknown
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool BTM_SecAddDevice(const RawAddress& bd_addr, const DEV_CLASS dev_class,
                      const BD_NAME& bd_name, uint8_t* features,
                      LinkKey* link_key, uint8_t key_type, uint8_t pin_length);

/** Free resources associated with the device associated with |bd_addr| address.
 *
 * *** WARNING ***
 * tBTM_SEC_DEV_REC associated with bd_addr becomes invalid after this function
 * is called, also any of its fields. i.e. if you use p_dev_rec->bd_addr, it is
 * no longer valid!
 * *** WARNING ***
 *
 * Returns true if removed OK, false if not found or ACL link is active.
 */
bool BTM_SecDeleteDevice(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTM_SecClearSecurityFlags
 *
 * Description      Reset the security flags (mark as not-paired) for a given
 *                  remove device.
 *
 ******************************************************************************/
void BTM_SecClearSecurityFlags(const RawAddress& bd_addr);

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
 * Function         BTM_DeleteStoredLinkKey
 *
 * Description      This function is called to delete link key for the specified
 *                  device addresses from the NVRAM storage attached to the
 *                  Bluetooth controller.
 *
 * Parameters:      bd_addr      - Addresses of the devices
 *                  p_cb         - Call back function to be called to return
 *                                 the results
 *
 ******************************************************************************/
tBTM_STATUS BTM_DeleteStoredLinkKey(const RawAddress* bd_addr,
                                    tBTM_CMPL_CB* p_cb);

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
void BTM_BleSirkConfirmDeviceReply(const RawAddress& bd_addr, uint8_t res);

uint8_t btm_ble_read_sec_key_size(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTM_IsRemoteNameKnown
 *
 * Description      This function checks if the remote name is known.
 *
 * Input Params:    bd_addr: Address of remote
 *                  transport: Transport, auto if unknown
 *
 * Returns
 *                  true if name is known, false otherwise
 *
 ******************************************************************************/
bool BTM_IsRemoteNameKnown(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport);
