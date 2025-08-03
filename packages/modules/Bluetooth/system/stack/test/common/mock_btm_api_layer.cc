/*
 * Copyright 2020 HIMSA II K/S - www.himsa.dk.
 * Represented by EHIMA - www.ehima.com
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

#include "mock_btm_api_layer.h"

static bluetooth::manager::MockBtmApiInterface* btm_api_interface = nullptr;

void bluetooth::manager::SetMockBtmApiInterface(
    MockBtmApiInterface* mock_btm_api_interface) {
  btm_api_interface = mock_btm_api_interface;
}

bool BTM_SetSecurityLevel(bool is_originator, const char* p_name,
                          uint8_t service_id, uint16_t sec_level, uint16_t psm,
                          uint32_t mx_proto_id, uint32_t mx_chan_id) {
  return btm_api_interface->SetSecurityLevel(is_originator, p_name, service_id,
                                             sec_level, psm, mx_proto_id,
                                             mx_chan_id);
}

bool BTM_IsEncrypted(const RawAddress& remote_bd_addr,
                     tBT_TRANSPORT transport) {
  return btm_api_interface->IsEncrypted(remote_bd_addr, transport);
}

bool BTM_IsLinkKeyKnown(const RawAddress& remote_bd_addr,
                        tBT_TRANSPORT transport) {
  return btm_api_interface->IsLinkKeyKnown(remote_bd_addr, transport);
}

uint8_t btm_ble_read_sec_key_size(const RawAddress& bd_addr) {
  return btm_api_interface->ReadSecKeySize(bd_addr);
}