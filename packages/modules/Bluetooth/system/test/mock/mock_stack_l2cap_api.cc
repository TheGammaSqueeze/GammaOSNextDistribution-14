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
 *   Functions generated:33
 *
 *  mockcify.pl ver 0.2
 */
#include "test/mock/mock_stack_l2cap_api.h"

// Original included files, if any

#include "test/common/mock_functions.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_l2cap_api {

// Function state capture and return values, if needed
struct l2c_get_transport_from_fixed_cid l2c_get_transport_from_fixed_cid;
struct L2CA_Register2 L2CA_Register2;
struct L2CA_Register L2CA_Register;
struct L2CA_Deregister L2CA_Deregister;
struct L2CA_AllocateLePSM L2CA_AllocateLePSM;
struct L2CA_FreeLePSM L2CA_FreeLePSM;
struct L2CA_ConnectReq2 L2CA_ConnectReq2;
struct L2CA_ConnectReq L2CA_ConnectReq;
struct L2CA_RegisterLECoc L2CA_RegisterLECoc;
struct L2CA_DeregisterLECoc L2CA_DeregisterLECoc;
struct L2CA_ConnectLECocReq L2CA_ConnectLECocReq;
struct L2CA_GetPeerLECocConfig L2CA_GetPeerLECocConfig;
struct L2CA_ConnectCreditBasedRsp L2CA_ConnectCreditBasedRsp;
struct L2CA_ConnectCreditBasedReq L2CA_ConnectCreditBasedReq;
struct L2CA_ReconfigCreditBasedConnsReq L2CA_ReconfigCreditBasedConnsReq;
struct L2CA_DisconnectReq L2CA_DisconnectReq;
struct L2CA_DisconnectLECocReq L2CA_DisconnectLECocReq;
struct L2CA_GetRemoteCid L2CA_GetRemoteCid;
struct L2CA_SetIdleTimeoutByBdAddr L2CA_SetIdleTimeoutByBdAddr;
struct L2CA_UseLatencyMode L2CA_UseLatencyMode;
struct L2CA_SetAclPriority L2CA_SetAclPriority;
struct L2CA_SetAclLatency L2CA_SetAclLatency;
struct L2CA_SetTxPriority L2CA_SetTxPriority;
struct L2CA_GetPeerFeatures L2CA_GetPeerFeatures;
struct L2CA_RegisterFixedChannel L2CA_RegisterFixedChannel;
struct L2CA_ConnectFixedChnl L2CA_ConnectFixedChnl;
struct L2CA_SendFixedChnlData L2CA_SendFixedChnlData;
struct L2CA_RemoveFixedChnl L2CA_RemoveFixedChnl;
struct L2CA_SetLeGattTimeout L2CA_SetLeGattTimeout;
struct L2CA_MarkLeLinkAsActive L2CA_MarkLeLinkAsActive;
struct L2CA_DataWrite L2CA_DataWrite;
struct L2CA_LECocDataWrite L2CA_LECocDataWrite;
struct L2CA_SetChnlFlushability L2CA_SetChnlFlushability;
struct L2CA_FlushChannel L2CA_FlushChannel;
struct L2CA_IsLinkEstablished L2CA_IsLinkEstablished;
struct L2CA_SetMediaStreamChannel L2CA_SetMediaStreamChannel;
struct L2CA_isMediaChannel L2CA_isMediaChannel;
struct L2CA_LeCreditDefault L2CA_LeCreditDefault;
struct L2CA_LeCreditThreshold L2CA_LeCreditThreshold;

}  // namespace stack_l2cap_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
tBT_TRANSPORT l2c_get_transport_from_fixed_cid(uint16_t fixed_cid) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::l2c_get_transport_from_fixed_cid(
      fixed_cid);
}
uint16_t L2CA_Register2(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                        bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                        uint16_t my_mtu, uint16_t required_remote_mtu,
                        uint16_t sec_level) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_Register2(
      psm, p_cb_info, enable_snoop, p_ertm_info, my_mtu, required_remote_mtu,
      sec_level);
}
uint16_t L2CA_Register(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                       bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                       uint16_t my_mtu, uint16_t required_remote_mtu,
                       uint16_t sec_level) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_Register(
      psm, p_cb_info, enable_snoop, p_ertm_info, my_mtu, required_remote_mtu,
      sec_level);
}
void L2CA_Deregister(uint16_t psm) {
  inc_func_call_count(__func__);
  test::mock::stack_l2cap_api::L2CA_Deregister(psm);
}
uint16_t L2CA_AllocateLePSM(void) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_AllocateLePSM();
}
void L2CA_FreeLePSM(uint16_t psm) {
  inc_func_call_count(__func__);
  test::mock::stack_l2cap_api::L2CA_FreeLePSM(psm);
}
uint16_t L2CA_ConnectReq2(uint16_t psm, const RawAddress& p_bd_addr,
                          uint16_t sec_level) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ConnectReq2(psm, p_bd_addr,
                                                       sec_level);
}
uint16_t L2CA_ConnectReq(uint16_t psm, const RawAddress& p_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ConnectReq(psm, p_bd_addr);
}
uint16_t L2CA_RegisterLECoc(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                            uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_RegisterLECoc(psm, p_cb_info,
                                                         sec_level, cfg);
}
void L2CA_DeregisterLECoc(uint16_t psm) {
  inc_func_call_count(__func__);
  test::mock::stack_l2cap_api::L2CA_DeregisterLECoc(psm);
}
uint16_t L2CA_ConnectLECocReq(uint16_t psm, const RawAddress& p_bd_addr,
                              tL2CAP_LE_CFG_INFO* p_cfg, uint16_t sec_level) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ConnectLECocReq(psm, p_bd_addr,
                                                           p_cfg, sec_level);
}
bool L2CA_GetPeerLECocConfig(uint16_t lcid, tL2CAP_LE_CFG_INFO* peer_cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_GetPeerLECocConfig(lcid, peer_cfg);
}
bool L2CA_ConnectCreditBasedRsp(const RawAddress& p_bd_addr, uint8_t id,
                                std::vector<uint16_t>& accepted_lcids,
                                uint16_t result, tL2CAP_LE_CFG_INFO* p_cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ConnectCreditBasedRsp(
      p_bd_addr, id, accepted_lcids, result, p_cfg);
}
std::vector<uint16_t> L2CA_ConnectCreditBasedReq(uint16_t psm,
                                                 const RawAddress& p_bd_addr,
                                                 tL2CAP_LE_CFG_INFO* p_cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ConnectCreditBasedReq(psm, p_bd_addr,
                                                                 p_cfg);
}
bool L2CA_ReconfigCreditBasedConnsReq(const RawAddress& bda,
                                      std::vector<uint16_t>& lcids,
                                      tL2CAP_LE_CFG_INFO* p_cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ReconfigCreditBasedConnsReq(
      bda, lcids, p_cfg);
}
bool L2CA_DisconnectReq(uint16_t cid) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_DisconnectReq(cid);
}
bool L2CA_DisconnectLECocReq(uint16_t cid) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_DisconnectLECocReq(cid);
}
bool L2CA_GetRemoteCid(uint16_t lcid, uint16_t* rcid) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_GetRemoteCid(lcid, rcid);
}
bool L2CA_SetIdleTimeoutByBdAddr(const RawAddress& bd_addr, uint16_t timeout,
                                 tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetIdleTimeoutByBdAddr(
      bd_addr, timeout, transport);
}
bool L2CA_UseLatencyMode(const RawAddress& bd_addr, bool use_latency_mode) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_UseLatencyMode(bd_addr,
                                                          use_latency_mode);
}
bool L2CA_SetAclPriority(const RawAddress& bd_addr, tL2CAP_PRIORITY priority) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetAclPriority(bd_addr, priority);
}
bool L2CA_SetAclLatency(const RawAddress& bd_addr, tL2CAP_LATENCY latency) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetAclLatency(bd_addr, latency);
}
bool L2CA_SetTxPriority(uint16_t cid, tL2CAP_CHNL_PRIORITY priority) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetTxPriority(cid, priority);
}
bool L2CA_GetPeerFeatures(const RawAddress& bd_addr, uint32_t* p_ext_feat,
                          uint8_t* p_chnl_mask) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_GetPeerFeatures(bd_addr, p_ext_feat,
                                                           p_chnl_mask);
}
bool L2CA_RegisterFixedChannel(uint16_t fixed_cid,
                               tL2CAP_FIXED_CHNL_REG* p_freg) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_RegisterFixedChannel(fixed_cid,
                                                                p_freg);
}
bool L2CA_ConnectFixedChnl(uint16_t fixed_cid, const RawAddress& rem_bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_ConnectFixedChnl(fixed_cid, rem_bda);
}
uint16_t L2CA_SendFixedChnlData(uint16_t fixed_cid, const RawAddress& rem_bda,
                                BT_HDR* p_buf) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SendFixedChnlData(fixed_cid, rem_bda,
                                                             p_buf);
}
bool L2CA_RemoveFixedChnl(uint16_t fixed_cid, const RawAddress& rem_bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_RemoveFixedChnl(fixed_cid, rem_bda);
}
bool L2CA_SetLeGattTimeout(const RawAddress& rem_bda, uint16_t idle_tout) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetLeGattTimeout(rem_bda, idle_tout);
}
bool L2CA_MarkLeLinkAsActive(const RawAddress& rem_bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_MarkLeLinkAsActive(rem_bda);
}
uint8_t L2CA_DataWrite(uint16_t cid, BT_HDR* p_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_DataWrite(cid, p_data);
}
uint8_t L2CA_LECocDataWrite(uint16_t cid, BT_HDR* p_data) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_LECocDataWrite(cid, p_data);
}
bool L2CA_SetChnlFlushability(uint16_t cid, bool is_flushable) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetChnlFlushability(cid,
                                                               is_flushable);
}
uint16_t L2CA_FlushChannel(uint16_t lcid, uint16_t num_to_flush) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_FlushChannel(lcid, num_to_flush);
}
bool L2CA_IsLinkEstablished(const RawAddress& bd_addr,
                            tBT_TRANSPORT transport) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_IsLinkEstablished(bd_addr,
                                                             transport);
}
void L2CA_SetMediaStreamChannel(uint16_t local_media_cid, bool status) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_SetMediaStreamChannel(
      local_media_cid, status);
}
bool L2CA_isMediaChannel(uint16_t handle, uint16_t channel_id,
                         bool is_local_cid) {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_isMediaChannel(handle, channel_id,
                                                          is_local_cid);
}
uint16_t L2CA_LeCreditDefault() {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_LeCreditDefault();
}
uint16_t L2CA_LeCreditThreshold() {
  inc_func_call_count(__func__);
  return test::mock::stack_l2cap_api::L2CA_LeCreditThreshold();
}

// END mockcify generation
