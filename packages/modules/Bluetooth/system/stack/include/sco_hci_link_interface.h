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
#include "stack/include/bt_dev_class.h"
#include "stack/include/hci_error_code.h"
#include "types/raw_address.h"

struct tBTM_ESCO_DATA;

bool btm_is_sco_active(uint16_t handle);
void btm_sco_chk_pend_unpark(tHCI_STATUS hci_status, uint16_t hci_handle);
void btm_sco_conn_req(const RawAddress& bda, const DEV_CLASS& dev_class,
                      uint8_t link_type);
void btm_sco_connected(const RawAddress& bda, uint16_t hci_handle,
                       tBTM_ESCO_DATA* p_esco_data);
void btm_sco_connection_failed(tHCI_STATUS hci_status, const RawAddress& bda,
                               uint16_t hci_handle,
                               tBTM_ESCO_DATA* p_esco_data);
bool btm_sco_removed(uint16_t hci_handle, tHCI_REASON reason);

void btm_sco_on_disconnected(uint16_t hci_handle, tHCI_REASON reason);
void btm_sco_on_esco_connect_request(const RawAddress&,
                                     const bluetooth::hci::ClassOfDevice&);
void btm_sco_on_sco_connect_request(const RawAddress&,
                                    const bluetooth::hci::ClassOfDevice&);
