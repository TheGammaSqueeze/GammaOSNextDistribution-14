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
#include "types/raw_address.h"

void btm_ble_resolving_list_init(uint8_t max_irk_list_sz);

void btm_ble_refresh_peer_resolvable_private_addr(const RawAddress& pseudo_bda,
                                                  const RawAddress& rra,
                                                  tBLE_RAND_ADDR_TYPE type);
bool btm_ble_read_resolving_list_entry(tBTM_SEC_DEV_REC* p_dev_rec);

bool btm_ble_addr_resolvable(const RawAddress& rpa,
                             tBTM_SEC_DEV_REC* p_dev_rec);

void btm_ble_resolving_list_load_dev(tBTM_SEC_DEV_REC& p_dev_rec);
void btm_ble_resolving_list_remove_dev(tBTM_SEC_DEV_REC* p_dev_rec);

uint64_t btm_get_next_private_addrress_interval_ms();
