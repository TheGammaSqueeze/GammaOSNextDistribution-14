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

#include "stack/btm/security_device_record.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

/*******************************************************************************
 *
 * Function         btm_ble_init_pseudo_addr
 *
 * Description      This function is used to initialize pseudo address.
 *                  If pseudo address is not available, use dummy address
 *
 * Returns          true is updated; false otherwise.
 *
 ******************************************************************************/
bool btm_ble_init_pseudo_addr(tBTM_SEC_DEV_REC* p_dev_rec,
                              const RawAddress& new_pseudo_addr);

/*******************************************************************************
 *
 * Function         btm_identity_addr_to_random_pseudo
 *
 * Description      This function map a static BD address to a pseudo random
 *                  address in security database.
 *
 ******************************************************************************/
bool btm_identity_addr_to_random_pseudo(RawAddress* bd_addr,
                                        tBLE_ADDR_TYPE* p_addr_type,
                                        bool refresh);

bool btm_identity_addr_to_random_pseudo_from_address_with_type(
    tBLE_BD_ADDR* address_with_type, bool refresh);

bool maybe_resolve_address(RawAddress* bda, tBLE_ADDR_TYPE* bda_type);

/* BLE address mapping with CS feature */
bool btm_random_pseudo_to_identity_addr(RawAddress* random_pseudo,
                                        tBLE_ADDR_TYPE* p_identity_addr_type);
