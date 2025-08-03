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
#include "mock_l2cap_layer.h"

#include <base/logging.h>
#include <bluetooth/log.h>

#include "stack/include/bt_hdr.h"
#include "stack/l2cap/l2c_int.h"
#include "types/raw_address.h"

static bluetooth::l2cap::MockL2capInterface* l2cap_interface = nullptr;

void bluetooth::l2cap::SetMockInterface(
    MockL2capInterface* mock_l2cap_interface) {
  l2cap_interface = mock_l2cap_interface;
}

tL2C_CCB* l2cu_find_ccb_by_cid(tL2C_LCB* p_lcb, uint16_t local_cid) {
  return nullptr;
}

uint16_t L2CA_Register(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                       bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                       uint16_t my_mtu, uint16_t required_remote_mtu,
                       uint16_t sec_level) {
  bluetooth::log::verbose("psm={}, enable_snoop={}", psm, enable_snoop);
  return l2cap_interface->Register(psm, p_cb_info, enable_snoop, p_ertm_info);
}

uint16_t L2CA_ConnectReq(uint16_t psm, const RawAddress& bd_addr) {
  return l2cap_interface->ConnectRequest(psm, bd_addr);
}

bool L2CA_ConnectRsp(const RawAddress& bd_addr, uint8_t id, uint16_t lcid,
                     uint16_t result, uint16_t status) {
  return l2cap_interface->ConnectResponse(bd_addr, id, lcid, result, status);
}

bool L2CA_DisconnectReq(uint16_t cid) {
  return l2cap_interface->DisconnectRequest(cid);
}

bool L2CA_DisconnectRsp(uint16_t cid) {
  return l2cap_interface->DisconnectResponse(cid);
}

bool L2CA_ConfigReq(uint16_t cid, tL2CAP_CFG_INFO* p_cfg) {
  return l2cap_interface->ConfigRequest(cid, p_cfg);
}

bool L2CA_ConfigRsp(uint16_t cid, tL2CAP_CFG_INFO* p_cfg) {
  return l2cap_interface->ConfigResponse(cid, p_cfg);
}

uint8_t L2CA_DataWrite(uint16_t cid, BT_HDR* p_data) {
  return l2cap_interface->DataWrite(cid, p_data);
}

uint16_t L2CA_RegisterLECoc(uint16_t psm, const tL2CAP_APPL_INFO& cb_info,
                            uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg) {
  return l2cap_interface->RegisterLECoc(psm, cb_info, sec_level);
}

void L2CA_DeregisterLECoc(uint16_t psm) {
  return l2cap_interface->DeregisterLECoc(psm);
}

tHCI_ROLE L2CA_GetBleConnRole(const RawAddress& bd_addr) {
  return to_hci_role(l2cap_interface->GetBleConnRole(bd_addr));
}

std::vector<uint16_t> L2CA_ConnectCreditBasedReq(uint16_t psm,
                                const RawAddress& bd_addr,
                                tL2CAP_LE_CFG_INFO* p_cfg) {
  return l2cap_interface->ConnectCreditBasedReq(psm, bd_addr, p_cfg);
}

bool L2CA_ConnectCreditBasedRsp(const RawAddress& bd_addr, uint8_t id,
                                       std::vector<uint16_t> &lcids,
                                       uint16_t result,
                                       tL2CAP_LE_CFG_INFO* p_cfg) {
  return l2cap_interface->ConnectCreditBasedRsp(bd_addr, id, lcids, result, p_cfg);
}

bool L2CA_ReconfigCreditBasedConnsReq(const RawAddress& bd_addr, std::vector<uint16_t> &lcids,
                                      tL2CAP_LE_CFG_INFO* peer_cfg) {
  return l2cap_interface->ReconfigCreditBasedConnsReq(bd_addr, lcids, peer_cfg);
}
uint16_t L2CA_LeCreditDefault() { return l2cap_interface->LeCreditDefault(); }

uint16_t L2CA_LeCreditThreshold() {
  return l2cap_interface->LeCreditThreshold();
}
