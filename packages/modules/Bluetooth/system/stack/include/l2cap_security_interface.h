/*
 *  Copyright 2020 The Android Open Source Project
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

#include "stack/include/btm_status.h"
#include "types/raw_address.h"

// This header contains functions for Security Module to invoke

void l2cu_update_lcb_4_bonding(const RawAddress& p_bd_addr, bool is_bonding);

bool l2cu_start_post_bond_timer(uint16_t handle);

void l2c_pin_code_request(const RawAddress& bd_addr);

void l2cu_resubmit_pending_sec_req(const RawAddress* p_bda);

// Establish ACL link to remote device for Security Manager/Pairing.
// Returns BTM_CMD_STARTED if already connecting, BTM_NO_RESOURCES if can't
// allocate lcb, BTM_SUCCESS if initiated the connection
tBTM_STATUS l2cu_ConnectAclForSecurity(const RawAddress& bd_addr);

void l2cble_update_sec_act(const RawAddress& bd_addr, uint16_t sec_act);
