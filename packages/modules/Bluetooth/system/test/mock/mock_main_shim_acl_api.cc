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

/*
 * Generated mock file from original source file
 *   Functions generated:5
 */

#include <cstdint>
#include <string>

#include "main/shim/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_octets.h"
#include "test/common/mock_functions.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

void bluetooth::shim::ACL_CreateClassicConnection(
    const RawAddress& /* raw_address */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_CancelClassicConnection(
    const RawAddress& /* raw_address */) {
  inc_func_call_count(__func__);
}
bool bluetooth::shim::ACL_AcceptLeConnectionFrom(
    const tBLE_BD_ADDR& /* legacy_address_with_type */, bool /* is_direct */) {
  inc_func_call_count(__func__);
  return true;
}
void bluetooth::shim::ACL_IgnoreLeConnectionFrom(
    const tBLE_BD_ADDR& /* legacy_address_with_type */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_ConfigureLePrivacy(bool /* is_le_privacy_enabled */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_WriteData(uint16_t /* handle */,
                                    BT_HDR* /* p_buf */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_Disconnect(uint16_t /* handle */,
                                     bool /* is_classic */,
                                     tHCI_STATUS /* reason */,
                                     std::string /* comment */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_IgnoreAllLeConnections() {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_ReadConnectionAddress(
    uint16_t /* handle */, RawAddress& /* conn_addr */,
    tBLE_ADDR_TYPE* /* p_addr_type */, bool /* ota_address */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_ReadPeerConnectionAddress(
    uint16_t /* handle */, RawAddress& /* conn_addr */,
    tBLE_ADDR_TYPE* /* p_addr_type */, bool /* ota_address */) {
  inc_func_call_count(__func__);
}
std::optional<uint8_t> bluetooth::shim::ACL_GetAdvertisingSetConnectedTo(
    const RawAddress& /* addr */) {
  inc_func_call_count(__func__);
  return std::nullopt;
}
void bluetooth::shim::ACL_AddToAddressResolution(
    const tBLE_BD_ADDR& /* legacy_address_with_type */,
    const Octet16& /* peer_irk */, const Octet16& /* local_irk */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::ACL_RemoveFromAddressResolution(
    const tBLE_BD_ADDR& /* legacy_address_with_type */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_ClearAddressResolution() {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_LeSetDefaultSubrate(uint16_t /* subrate_min */,
                                              uint16_t /* subrate_max */,
                                              uint16_t /* max_latency */,
                                              uint16_t /* cont_num */,
                                              uint16_t /* sup_tout */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::ACL_LeSubrateRequest(uint16_t /* hci_handle */,
                                           uint16_t /* subrate_min */,
                                           uint16_t /* subrate_max */,
                                           uint16_t /* max_latency */,
                                           uint16_t /* cont_num */,
                                           uint16_t /* sup_tout */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::ACL_Shutdown() { inc_func_call_count(__func__); }
