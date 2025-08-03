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

#include "stack/include/bt_hdr.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// This header contains functions for L2cap-ACL to invoke
//
void acl_create_classic_connection(const RawAddress& bd_addr,
                                   bool there_are_high_priority_channels,
                                   bool is_bonding);
bool acl_create_le_connection(const RawAddress& bd_addr);
bool acl_create_le_connection_with_id(uint8_t id, const RawAddress& bd_addr);
bool acl_create_le_connection_with_id(uint8_t id, const RawAddress& bd_addr,
                                      tBLE_ADDR_TYPE addr_type);
void acl_send_data_packet_br_edr(const RawAddress& bd_addr, BT_HDR* p_buf);
void acl_send_data_packet_ble(const RawAddress& bd_addr, BT_HDR* p_buf);
void acl_write_automatic_flush_timeout(const RawAddress& bd_addr,
                                       uint16_t flush_timeout);

// ACL data received from HCI-ACL
void l2c_rcv_acl_data(BT_HDR* p_msg);

// Segments is sent to HCI-ACL
void l2c_link_segments_xmitted(BT_HDR* p_msg);

void l2cu_resubmit_pending_sec_req(const RawAddress* p_bda);

void l2c_packets_completed(uint16_t handle, uint16_t num_sent);
