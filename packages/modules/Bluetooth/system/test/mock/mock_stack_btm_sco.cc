/*
 * Copyright 2021 The Android Open Source Project
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
 *   Functions generated:23
 */

#include <cstdint>

#include "device/include/esco_parameters.h"
#include "hci/class_of_device.h"
#include "stack/btm/btm_sco.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_status.h"
#include "stack/include/hci_error_code.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

bool BTM_IsScoActiveByBdaddr(const RawAddress& /* remote_bda */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_is_sco_active(uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_sco_removed(uint16_t /* hci_handle */, tHCI_REASON /* reason */) {
  inc_func_call_count(__func__);
  return false;
}
const RawAddress* BTM_ReadScoBdAddr(uint16_t /* sco_inx */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_STATUS BTM_CreateSco(const RawAddress* /* remote_bda */,
                          bool /* is_orig */, uint16_t /* pkt_types */,
                          uint16_t* /* p_sco_inx */,
                          tBTM_SCO_CB* /* p_conn_cb */,
                          tBTM_SCO_CB* /* p_disc_cb */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_RegForEScoEvts(uint16_t /* sco_inx */,
                               tBTM_ESCO_CBACK* /* p_esco_cback */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_RemoveSco(uint16_t /* sco_inx */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_SetEScoMode(enh_esco_params_t* /* p_parms */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
uint8_t BTM_GetNumScoLinks(void) {
  inc_func_call_count(__func__);
  return 0;
}
tBTM_SCO_DEBUG_DUMP BTM_GetScoDebugDump(void) {
  inc_func_call_count(__func__);
  return {};
}
void BTM_EScoConnRsp(uint16_t /* sco_inx */, uint8_t /* hci_status */,
                     enh_esco_params_t* /* p_parms */) {
  inc_func_call_count(__func__);
}
void BTM_RemoveSco(const RawAddress& /* bda */) {
  inc_func_call_count(__func__);
}
void btm_sco_acl_removed(const RawAddress* /* bda */) {
  inc_func_call_count(__func__);
}
void btm_sco_chk_pend_rolechange(uint16_t /* hci_handle */) {
  inc_func_call_count(__func__);
}
void btm_sco_chk_pend_unpark(tHCI_STATUS /* hci_status */,
                             uint16_t /* hci_handle */) {
  inc_func_call_count(__func__);
}
void btm_sco_conn_req(const RawAddress& /* bda */,
                      const DEV_CLASS& /* dev_class */,
                      uint8_t /* link_type */) {
  inc_func_call_count(__func__);
}
void btm_sco_connected(const RawAddress& /* bda */, uint16_t /* hci_handle */,
                       tBTM_ESCO_DATA* /* p_esco_data */) {
  inc_func_call_count(__func__);
}
void btm_sco_connection_failed(tHCI_STATUS /* hci_status */,
                               const RawAddress& /* bda */,
                               uint16_t /* hci_handle */,
                               tBTM_ESCO_DATA* /* p_esco_data */) {
  inc_func_call_count(__func__);
}
void btm_sco_disc_chk_pend_for_modechange(uint16_t /* hci_handle */) {
  inc_func_call_count(__func__);
}
void btm_sco_on_esco_connect_request(
    const RawAddress& /* bda */,
    const bluetooth::hci::ClassOfDevice& /* cod */) {
  inc_func_call_count(__func__);
}
void btm_sco_on_sco_connect_request(
    const RawAddress& /* bda */,
    const bluetooth::hci::ClassOfDevice& /* cod */) {
  inc_func_call_count(__func__);
}
void btm_sco_on_disconnected(uint16_t /* hci_handle */,
                             tHCI_REASON /* reason */) {
  inc_func_call_count(__func__);
}
bool btm_peer_supports_esco_2m_phy(RawAddress /* bd_addr */) {
  inc_func_call_count(__func__);
  return true;
}
bool btm_peer_supports_esco_3m_phy(RawAddress /* bd_addr */) {
  inc_func_call_count(__func__);
  return true;
}
bool btm_peer_supports_esco_ev3(RawAddress /* bd_addr */) {
  inc_func_call_count(__func__);
  return true;
}
