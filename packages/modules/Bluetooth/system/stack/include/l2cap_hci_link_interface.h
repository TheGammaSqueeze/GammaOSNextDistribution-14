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

#include "stack/include/hci_error_code.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// This header contains functions for HCI-LinkManagement to invoke

bool l2c_link_hci_disc_comp(uint16_t handle, tHCI_REASON reason);

void l2c_link_role_changed(const RawAddress* bd_addr, uint8_t new_role,
                           uint8_t hci_status);

bool l2cble_conn_comp(uint16_t handle, uint8_t role, const RawAddress& bda,
                      tBLE_ADDR_TYPE type, uint16_t conn_interval,
                      uint16_t conn_latency, uint16_t conn_timeout);

void l2cble_process_conn_update_evt(uint16_t handle, uint8_t status,
                                    uint16_t interval, uint16_t latency,
                                    uint16_t timeout);

void l2cble_process_data_length_change_event(uint16_t handle,
                                             uint16_t tx_data_len,
                                             uint16_t rx_data_len);

// Notify to L2cap layer that ACL data or remote version is received
void l2cble_notify_le_connection(const RawAddress& bda);

void l2cble_use_preferred_conn_params(const RawAddress& bda);

void l2cble_process_rc_param_request_evt(uint16_t handle, uint16_t int_min,
                                         uint16_t int_max, uint16_t latency,
                                         uint16_t timeout);

// Invoked when HCI mode is changed to HCI_MODE_ACTIVE or HCI_MODE_SNIFF
void l2c_OnHciModeChangeSendPendingPackets(RawAddress remote);

// Invoked when HCI indicates to L2cap to check Security requirement
void l2cu_resubmit_pending_sec_req(const RawAddress* p_bda);
