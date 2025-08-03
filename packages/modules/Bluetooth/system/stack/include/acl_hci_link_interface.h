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

#include "hci/class_of_device.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hci_mode.h"
#include "stack/include/hcidefs.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

// This header contains functions for HCIF-Acl Management to invoke
//
void btm_connection_request(const RawAddress& bda,
                            const bluetooth::hci::ClassOfDevice& cod);
void btm_acl_connected(const RawAddress& bda, uint16_t handle,
                       tHCI_STATUS status, uint8_t enc_mode);
void on_acl_br_edr_connected(const RawAddress& bda, uint16_t handle,
                             uint8_t enc_mode, bool locally_initiated);
void on_acl_br_edr_failed(const RawAddress& bda, tHCI_STATUS status,
                          bool locally_initiated);
void btm_acl_disconnected(tHCI_STATUS status, uint16_t handle,
                          tHCI_STATUS reason);
void btm_acl_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                          tHCI_ROLE new_role);
void btm_rejectlist_role_change_device(const RawAddress& bd_addr,
                                       uint8_t hci_status);
void btm_pm_proc_cmd_status(tHCI_STATUS status);
void btm_pm_proc_mode_change(tHCI_STATUS hci_status, uint16_t hci_handle,
                             tHCI_MODE mode, uint16_t interval);
void btm_pm_proc_ssr_evt(uint8_t* p, uint16_t evt_len);
void btm_read_automatic_flush_timeout_complete(uint8_t* p);
void btm_read_failed_contact_counter_complete(uint8_t* p);
void btm_read_link_quality_complete(uint8_t* p, uint16_t evt_len);
void btm_read_remote_ext_features_complete_raw(uint8_t* p, uint8_t evt_len);
void btm_read_remote_ext_features_complete(uint16_t handle, uint8_t page_num,
                                           uint8_t max_page, uint8_t* features);
void btm_read_remote_ext_features_failed(uint8_t status, uint16_t handle);
void btm_read_remote_version_complete(tHCI_STATUS status, uint16_t handle,
                                      uint8_t lmp_version,
                                      uint16_t manufacturer,
                                      uint16_t lmp_subversion);
void btm_read_rssi_complete(uint8_t* p, uint16_t evt_len);
void btm_read_tx_power_complete(uint8_t* p, uint16_t evt_len, bool is_ble);

void acl_rcv_acl_data(BT_HDR* p_msg);
void acl_packets_completed(uint16_t handle, uint16_t num_packets);
void acl_process_supported_features(uint16_t handle, uint64_t features);
void acl_process_extended_features(uint16_t handle, uint8_t current_page_number,
                                   uint8_t max_page_number, uint64_t features);
void btm_pm_on_mode_change(tHCI_STATUS status, uint16_t handle,
                           tHCI_MODE current_mode, uint16_t interval);
void btm_pm_on_sniff_subrating(tHCI_STATUS status, uint16_t handle,
                               uint16_t maximum_transmit_latency,
                               uint16_t maximum_receive_latency,
                               uint16_t minimum_remote_timeout,
                               uint16_t minimum_local_timeout);

void acl_cache_role(const RawAddress& bd_addr, tHCI_ROLE new_role,
                    bool overwrite_cache);
