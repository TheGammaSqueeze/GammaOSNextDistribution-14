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

#include <vector>

#include "stack/btm/security_device_record.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

/** Free resources associated with the device associated with |bd_addr| address.
 *
 * *** WARNING ***
 * tBTM_SEC_DEV_REC associated with bd_addr becomes invalid after this function
 * is called, also any of it's fields. i.e. if you use p_dev_rec->bd_addr, it is
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
 * Function         BTM_SecReadDevName
 *
 * Description      Looks for the device name in the security database for the
 *                  specified BD address.
 *
 * Returns          Pointer to the name or NULL
 *
 ******************************************************************************/
const char* BTM_SecReadDevName(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_sec_alloc_dev
 *
 * Description      Allocate a record in the device database
 *                  with specified address
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_sec_alloc_dev(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_dev_support_role_switch
 *
 * Description      This function is called by the L2CAP to check if remote
 *                  device supports role switch
 *
 * Parameters:      bd_addr       - Address of the peer device
 *
 * Returns          true if device is known and role switch is supported
 *
 ******************************************************************************/
bool btm_dev_support_role_switch(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_find_dev_by_handle
 *
 * Description      Look for the record in the device database for the record
 *                  with specified handle
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_dev_by_handle(uint16_t handle);

/*******************************************************************************
 *
 * Function         btm_find_dev
 *
 * Description      Look for the record in the device database for the record
 *                  with specified BD address
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_dev(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_find_dev_with_lenc
 *
 * Description      Look for the record in the device database with LTK and
 *                  specified BD address
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_dev_with_lenc(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_consolidate_dev
 *
 * Description      combine security records if identified as same peer
 *
 * Returns          none
 *
 ******************************************************************************/
void btm_consolidate_dev(tBTM_SEC_DEV_REC* p_target_rec);

/*******************************************************************************
 *
 * Function         btm_consolidate_dev
 *
 * Description      When pairing is finished (i.e. on BR/EDR), this function
 *                  checks if there are existing LE connections to same device
 *                  that can now be encrypted and used for profiles requiring
 *                  encryption.
 *
 * Returns          none
 *
 ******************************************************************************/
void btm_dev_consolidate_existing_connections(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_find_or_alloc_dev
 *
 * Description      Look for the record in the device database for the record
 *                  with specified BD address
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_find_or_alloc_dev(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_sec_allocate_dev_rec
 *
 * Description      Attempts to allocate a new device record. If we have
 *                  exceeded the maximum number of allowable records to
 *                  allocate, the oldest record will be deleted to make room
 *                  for the new record.
 *
 * Returns          Pointer to the newly allocated record
 *
 ******************************************************************************/
tBTM_SEC_DEV_REC* btm_sec_allocate_dev_rec(void);

/*******************************************************************************
 *
 * Function         btm_get_bond_type_dev
 *
 * Description      Get the bond type for a device in the device database
 *                  with specified BD address
 *
 * Returns          The device bond type if known, otherwise BOND_TYPE_UNKNOWN
 *
 ******************************************************************************/
tBTM_BOND_TYPE btm_get_bond_type_dev(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         btm_set_bond_type_dev
 *
 * Description      Set the bond type for a device in the device database
 *                  with specified BD address
 *
 * Returns          true on success, otherwise false
 *
 ******************************************************************************/
bool btm_set_bond_type_dev(const RawAddress& bd_addr, tBTM_BOND_TYPE bond_type);

/*******************************************************************************
 *
 * Function         btm_get_sec_dev_rec
 *
 * Description      Get security device records satisfying given filter
 *
 * Returns          A vector containing pointers of security device records
 *
 ******************************************************************************/
std::vector<tBTM_SEC_DEV_REC*> btm_get_sec_dev_rec();

bool BTM_Sec_AddressKnown(const RawAddress& address);
const tBLE_BD_ADDR BTM_Sec_GetAddressWithType(const RawAddress& bd_addr);

bool BTM_IsRemoteNameKnown(const RawAddress& bd_addr, tBT_TRANSPORT transport);
