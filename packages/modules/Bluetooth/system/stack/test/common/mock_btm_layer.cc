/******************************************************************************
 *
 *  Copyright 2018 The Android Open Source Project
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
 ******************************************************************************/

#include "mock_btm_layer.h"

#include "stack/include/bt_psm_types.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/rfcdefs.h"
#include "types/raw_address.h"

static bluetooth::manager::MockBtmSecurityInternalInterface*
    btm_security_internal_interface = nullptr;

void bluetooth::manager::SetMockSecurityInternalInterface(
    MockBtmSecurityInternalInterface* mock_btm_security_internal_interface) {
  btm_security_internal_interface = mock_btm_security_internal_interface;
}

void btm_sec_abort_access_req(const RawAddress& bd_addr) {
  btm_security_internal_interface->AbortAccessRequest(bd_addr);
}

tBTM_STATUS btm_sec_mx_access_request(const RawAddress& bd_addr,
                                      bool is_originator, uint16_t requirement,
                                      tBTM_SEC_CALLBACK* p_callback,
                                      void* p_ref_data) {
  return btm_security_internal_interface->MultiplexingProtocolAccessRequest(
      bd_addr, BT_PSM_RFCOMM, is_originator, BTM_SEC_PROTO_RFCOMM, 0,
      p_callback, p_ref_data);
}

bool BTM_SetSecurityLevel(bool is_originator, const char* p_name,
                          uint8_t service_id, uint16_t sec_level, uint16_t psm,
                          uint32_t mx_proto_id, uint32_t mx_chan_id) {
  return true;
}

uint16_t BTM_GetMaxPacketSize(const RawAddress& addr) {
  return RFCOMM_DEFAULT_MTU;
}

bool BTM_IsAclConnectionUp(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport) {
  return true;
}

struct btm_client_interface_t btm_client_interface = {
    .peer =
        {
            .BTM_IsAclConnectionUp = BTM_IsAclConnectionUp,
            .BTM_GetMaxPacketSize = BTM_GetMaxPacketSize,
        },
};

struct btm_client_interface_t& get_btm_client_interface() {
  return btm_client_interface;
}
