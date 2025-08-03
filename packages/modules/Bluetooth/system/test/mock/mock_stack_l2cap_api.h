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

#include <functional>
#include <vector>

// Original included files, if any
#include <base/logging.h>
#include <base/strings/stringprintf.h>

#include <cstdint>

#include "stack/include/bt_hdr.h"
#include "stack/include/l2c_api.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_l2cap_api {

// Shared state between mocked functions and tests
// Name: l2c_get_transport_from_fixed_cid
// Params: uint16_t fixed_cid
// Returns: tBT_TRANSPORT
struct l2c_get_transport_from_fixed_cid {
  std::function<tBT_TRANSPORT(uint16_t fixed_cid)> body{
      [](uint16_t /* fixed_cid */) { return 0; }};
  tBT_TRANSPORT operator()(uint16_t fixed_cid) { return body(fixed_cid); };
};
extern struct l2c_get_transport_from_fixed_cid l2c_get_transport_from_fixed_cid;
// Name: L2CA_Register2
// Params: uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info, bool enable_snoop,
// tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu, uint16_t required_remote_mtu,
// uint16_t sec_level Returns: uint16_t
struct L2CA_Register2 {
  std::function<uint16_t(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                         bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                         uint16_t my_mtu, uint16_t required_remote_mtu,
                         uint16_t sec_level)>
      body{[](uint16_t /* psm */, const tL2CAP_APPL_INFO& /* p_cb_info */,
              bool /* enable_snoop */, tL2CAP_ERTM_INFO* /* p_ertm_info */,
              uint16_t /* my_mtu */, uint16_t /* required_remote_mtu */,
              uint16_t /* sec_level */) { return 0; }};
  uint16_t operator()(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                      bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                      uint16_t my_mtu, uint16_t required_remote_mtu,
                      uint16_t sec_level) {
    return body(psm, p_cb_info, enable_snoop, p_ertm_info, my_mtu,
                required_remote_mtu, sec_level);
  };
};
extern struct L2CA_Register2 L2CA_Register2;
// Name: L2CA_Register
// Params: uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info, bool enable_snoop,
// tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu, uint16_t required_remote_mtu,
// uint16_t sec_level Returns: uint16_t
struct L2CA_Register {
  std::function<uint16_t(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                         bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                         uint16_t my_mtu, uint16_t required_remote_mtu,
                         uint16_t sec_level)>
      body{[](uint16_t /* psm */, const tL2CAP_APPL_INFO& /* p_cb_info */,
              bool /* enable_snoop */, tL2CAP_ERTM_INFO* /* p_ertm_info */,
              uint16_t /* my_mtu */, uint16_t /* required_remote_mtu */,
              uint16_t /* sec_level */) { return 0; }};
  uint16_t operator()(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                      bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                      uint16_t my_mtu, uint16_t required_remote_mtu,
                      uint16_t sec_level) {
    return body(psm, p_cb_info, enable_snoop, p_ertm_info, my_mtu,
                required_remote_mtu, sec_level);
  };
};
extern struct L2CA_Register L2CA_Register;
// Name: L2CA_Deregister
// Params: uint16_t psm
// Returns: void
struct L2CA_Deregister {
  std::function<void(uint16_t /* psm */)> body{[](uint16_t /* psm */) {}};
  void operator()(uint16_t psm) { body(psm); };
};
extern struct L2CA_Deregister L2CA_Deregister;
// Name: L2CA_AllocateLePSM
// Params: void
// Returns: uint16_t
struct L2CA_AllocateLePSM {
  std::function<uint16_t(void)> body{[](void) { return 0; }};
  uint16_t operator()(void) { return body(); };
};
extern struct L2CA_AllocateLePSM L2CA_AllocateLePSM;
// Name: L2CA_FreeLePSM
// Params: uint16_t psm
// Returns: void
struct L2CA_FreeLePSM {
  std::function<void(uint16_t /* psm */)> body{[](uint16_t /* psm */) {}};
  void operator()(uint16_t psm) { body(psm); };
};
extern struct L2CA_FreeLePSM L2CA_FreeLePSM;
// Name: L2CA_ConnectReq2
// Params: uint16_t psm, const RawAddress& p_bd_addr, uint16_t sec_level
// Returns: uint16_t
struct L2CA_ConnectReq2 {
  std::function<uint16_t(uint16_t psm, const RawAddress& p_bd_addr,
                         uint16_t sec_level)>
      body{[](uint16_t /* psm */, const RawAddress& /* p_bd_addr */,
              uint16_t /* sec_level */) { return 0; }};
  uint16_t operator()(uint16_t psm, const RawAddress& p_bd_addr,
                      uint16_t sec_level) {
    return body(psm, p_bd_addr, sec_level);
  };
};
extern struct L2CA_ConnectReq2 L2CA_ConnectReq2;
// Name: L2CA_ConnectReq
// Params: uint16_t psm, const RawAddress& p_bd_addr
// Returns: uint16_t
struct L2CA_ConnectReq {
  std::function<uint16_t(uint16_t psm, const RawAddress& p_bd_addr)> body{
      [](uint16_t /* psm */, const RawAddress& /* p_bd_addr */) { return 0; }};
  uint16_t operator()(uint16_t psm, const RawAddress& p_bd_addr) {
    return body(psm, p_bd_addr);
  };
};
extern struct L2CA_ConnectReq L2CA_ConnectReq;
// Name: L2CA_RegisterLECoc
// Params: uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info, uint16_t sec_level,
// tL2CAP_LE_CFG_INFO cfg Returns: uint16_t
struct L2CA_RegisterLECoc {
  std::function<uint16_t(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                         uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg)>
      body{[](uint16_t /* psm */, const tL2CAP_APPL_INFO& /* p_cb_info */,
              uint16_t /* sec_level */,
              tL2CAP_LE_CFG_INFO /* cfg */) { return 0; }};
  uint16_t operator()(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                      uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg) {
    return body(psm, p_cb_info, sec_level, cfg);
  };
};
extern struct L2CA_RegisterLECoc L2CA_RegisterLECoc;
// Name: L2CA_DeregisterLECoc
// Params: uint16_t psm
// Returns: void
struct L2CA_DeregisterLECoc {
  std::function<void(uint16_t psm)> body{[](uint16_t /* psm */) {}};
  void operator()(uint16_t psm) { body(psm); };
};
extern struct L2CA_DeregisterLECoc L2CA_DeregisterLECoc;
// Name: L2CA_ConnectLECocReq
// Params: uint16_t psm, const RawAddress& p_bd_addr, tL2CAP_LE_CFG_INFO* p_cfg,
// uint16_t sec_level Returns: uint16_t
struct L2CA_ConnectLECocReq {
  std::function<uint16_t(uint16_t psm, const RawAddress& p_bd_addr,
                         tL2CAP_LE_CFG_INFO* p_cfg, uint16_t sec_level)>
      body{[](uint16_t /* psm */, const RawAddress& /* p_bd_addr */,
              tL2CAP_LE_CFG_INFO* /* p_cfg */,
              uint16_t /* sec_level */) { return 0; }};
  uint16_t operator()(uint16_t psm, const RawAddress& p_bd_addr,
                      tL2CAP_LE_CFG_INFO* p_cfg, uint16_t sec_level) {
    return body(psm, p_bd_addr, p_cfg, sec_level);
  };
};
extern struct L2CA_ConnectLECocReq L2CA_ConnectLECocReq;
// Name: L2CA_GetPeerLECocConfig
// Params: uint16_t lcid, tL2CAP_LE_CFG_INFO* peer_cfg
// Returns: bool
struct L2CA_GetPeerLECocConfig {
  std::function<bool(uint16_t lcid, tL2CAP_LE_CFG_INFO* peer_cfg)> body{
      [](uint16_t /* lcid */, tL2CAP_LE_CFG_INFO* /* peer_cfg */) {
        return false;
      }};
  bool operator()(uint16_t lcid, tL2CAP_LE_CFG_INFO* peer_cfg) {
    return body(lcid, peer_cfg);
  };
};
extern struct L2CA_GetPeerLECocConfig L2CA_GetPeerLECocConfig;
// Name: L2CA_ConnectCreditBasedRsp
// Params: const RawAddress& p_bd_addr, uint8_t id, std::vector<uint16_t>&
// accepted_lcids, uint16_t result, tL2CAP_LE_CFG_INFO* p_cfg Returns: bool
struct L2CA_ConnectCreditBasedRsp {
  std::function<bool(const RawAddress& p_bd_addr, uint8_t id,
                     std::vector<uint16_t>& accepted_lcids, uint16_t result,
                     tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[](const RawAddress& /* p_bd_addr */, uint8_t /* id */,
              std::vector<uint16_t>& /* accepted_lcids */,
              uint16_t /* result */,
              tL2CAP_LE_CFG_INFO* /* p_cfg */) { return false; }};
  bool operator()(const RawAddress& p_bd_addr, uint8_t id,
                  std::vector<uint16_t>& accepted_lcids, uint16_t result,
                  tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(p_bd_addr, id, accepted_lcids, result, p_cfg);
  };
};
extern struct L2CA_ConnectCreditBasedRsp L2CA_ConnectCreditBasedRsp;
// Name: L2CA_ConnectCreditBasedReq
// Params: uint16_t psm, const RawAddress& p_bd_addr, tL2CAP_LE_CFG_INFO* p_cfg
// Returns: std::vector<uint16_t>
struct L2CA_ConnectCreditBasedReq {
  std::vector<uint16_t> cids;
  std::function<std::vector<uint16_t>(uint16_t psm, const RawAddress& p_bd_addr,
                                      tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[this](uint16_t /* psm */, const RawAddress& /* p_bd_addr */,
                  tL2CAP_LE_CFG_INFO* /* p_cfg */) { return cids; }};
  std::vector<uint16_t> operator()(uint16_t psm, const RawAddress& p_bd_addr,
                                   tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(psm, p_bd_addr, p_cfg);
  };
};
extern struct L2CA_ConnectCreditBasedReq L2CA_ConnectCreditBasedReq;
// Name: L2CA_ReconfigCreditBasedConnsReq
// Params: const RawAddress& bda, std::vector<uint16_t>& lcids,
// tL2CAP_LE_CFG_INFO* p_cfg Returns: bool
struct L2CA_ReconfigCreditBasedConnsReq {
  std::function<bool(const RawAddress& bda, std::vector<uint16_t>& lcids,
                     tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[](const RawAddress& /* bda */, std::vector<uint16_t>& /* lcids */,
              tL2CAP_LE_CFG_INFO* /* p_cfg */) { return false; }};
  bool operator()(const RawAddress& bda, std::vector<uint16_t>& lcids,
                  tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(bda, lcids, p_cfg);
  };
};
extern struct L2CA_ReconfigCreditBasedConnsReq L2CA_ReconfigCreditBasedConnsReq;
// Name: L2CA_DisconnectReq
// Params: uint16_t cid
// Returns: bool
struct L2CA_DisconnectReq {
  std::function<bool(uint16_t cid)> body{
      [](uint16_t /* cid */) { return false; }};
  bool operator()(uint16_t cid) { return body(cid); };
};
extern struct L2CA_DisconnectReq L2CA_DisconnectReq;
// Name: L2CA_DisconnectLECocReq
// Params: uint16_t cid
// Returns: bool
struct L2CA_DisconnectLECocReq {
  std::function<bool(uint16_t cid)> body{
      [](uint16_t /* cid */) { return false; }};
  bool operator()(uint16_t cid) { return body(cid); };
};
extern struct L2CA_DisconnectLECocReq L2CA_DisconnectLECocReq;
// Name: L2CA_GetRemoteCid
// Params: uint16_t lcid, uint16_t* rcid
// Returns: bool
struct L2CA_GetRemoteCid {
  std::function<bool(uint16_t lcid, uint16_t* rcid)> body{
      [](uint16_t /* lcid */, uint16_t* /* rcid */) { return false; }};
  bool operator()(uint16_t lcid, uint16_t* rcid) { return body(lcid, rcid); };
};
extern struct L2CA_GetRemoteCid L2CA_GetRemoteCid;
// Name: L2CA_SetIdleTimeoutByBdAddr
// Params: const RawAddress& bd_addr, uint16_t timeout, tBT_TRANSPORT transport
// Returns: bool
struct L2CA_SetIdleTimeoutByBdAddr {
  std::function<bool(const RawAddress& bd_addr, uint16_t timeout,
                     tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* bd_addr */, uint16_t /* timeout */,
              tBT_TRANSPORT /* transport */) { return false; }};
  bool operator()(const RawAddress& bd_addr, uint16_t timeout,
                  tBT_TRANSPORT transport) {
    return body(bd_addr, timeout, transport);
  };
};
extern struct L2CA_SetIdleTimeoutByBdAddr L2CA_SetIdleTimeoutByBdAddr;
// Name: L2CA_UseLatencyMode
// Params: const RawAddress& bd_addr, bool use_latency_mode
// Returns: bool
struct L2CA_UseLatencyMode {
  std::function<bool(const RawAddress& bd_addr, bool use_latency_mode)> body{
      [](const RawAddress& /* bd_addr */, bool /* use_latency_mode */) {
        return false;
      }};
  bool operator()(const RawAddress& bd_addr, bool use_latency_mode) {
    return body(bd_addr, use_latency_mode);
  };
};
extern struct L2CA_UseLatencyMode L2CA_UseLatencyMode;
// Name: L2CA_SetAclPriority
// Params: const RawAddress& bd_addr, tL2CAP_PRIORITY priority,
// Returns: bool
struct L2CA_SetAclPriority {
  std::function<bool(const RawAddress& bd_addr, tL2CAP_PRIORITY priority)> body{
      [](const RawAddress& /* bd_addr */, tL2CAP_PRIORITY /* priority */) {
        return false;
      }};
  bool operator()(const RawAddress& bd_addr, tL2CAP_PRIORITY priority) {
    return body(bd_addr, priority);
  };
};
extern struct L2CA_SetAclPriority L2CA_SetAclPriority;
// Name: L2CA_SetAclLatency
// Params: const RawAddress& bd_addr, tL2CAP_LATENCY latency
// Returns: bool
struct L2CA_SetAclLatency {
  std::function<bool(const RawAddress& bd_addr, tL2CAP_LATENCY latency)> body{
      [](const RawAddress& /* bd_addr */, tL2CAP_LATENCY /* latency */) {
        return false;
      }};
  bool operator()(const RawAddress& bd_addr, tL2CAP_LATENCY latency) {
    return body(bd_addr, latency);
  };
};
extern struct L2CA_SetAclLatency L2CA_SetAclLatency;
// Name: L2CA_SetTxPriority
// Params: uint16_t cid, tL2CAP_CHNL_PRIORITY priority
// Returns: bool
struct L2CA_SetTxPriority {
  std::function<bool(uint16_t cid, tL2CAP_CHNL_PRIORITY priority)> body{
      [](uint16_t /* cid */, tL2CAP_CHNL_PRIORITY /* priority */) {
        return false;
      }};
  bool operator()(uint16_t cid, tL2CAP_CHNL_PRIORITY priority) {
    return body(cid, priority);
  };
};
extern struct L2CA_SetTxPriority L2CA_SetTxPriority;
// Name: L2CA_GetPeerFeatures
// Params: const RawAddress& bd_addr, uint32_t* p_ext_feat, uint8_t* p_chnl_mask
// Returns: bool
struct L2CA_GetPeerFeatures {
  std::function<bool(const RawAddress& bd_addr, uint32_t* p_ext_feat,
                     uint8_t* p_chnl_mask)>
      body{[](const RawAddress& /* bd_addr */, uint32_t* /* p_ext_feat */,
              uint8_t* /* p_chnl_mask */) { return false; }};
  bool operator()(const RawAddress& bd_addr, uint32_t* p_ext_feat,
                  uint8_t* p_chnl_mask) {
    return body(bd_addr, p_ext_feat, p_chnl_mask);
  };
};
extern struct L2CA_GetPeerFeatures L2CA_GetPeerFeatures;
// Name: L2CA_RegisterFixedChannel
// Params: uint16_t fixed_cid, tL2CAP_FIXED_CHNL_REG* p_freg
// Returns: bool
struct L2CA_RegisterFixedChannel {
  std::function<bool(uint16_t fixed_cid, tL2CAP_FIXED_CHNL_REG* p_freg)> body{
      [](uint16_t /* fixed_cid */, tL2CAP_FIXED_CHNL_REG* /* p_freg */) {
        return false;
      }};
  bool operator()(uint16_t fixed_cid, tL2CAP_FIXED_CHNL_REG* p_freg) {
    return body(fixed_cid, p_freg);
  };
};
extern struct L2CA_RegisterFixedChannel L2CA_RegisterFixedChannel;
// Name: L2CA_ConnectFixedChnl
// Params: uint16_t fixed_cid, const RawAddress& rem_bda
// Returns: bool
struct L2CA_ConnectFixedChnl {
  std::function<bool(uint16_t fixed_cid, const RawAddress& rem_bda)> body{
      [](uint16_t /* fixed_cid */, const RawAddress& /* rem_bda */) {
        return false;
      }};
  bool operator()(uint16_t fixed_cid, const RawAddress& rem_bda) {
    return body(fixed_cid, rem_bda);
  };
};
extern struct L2CA_ConnectFixedChnl L2CA_ConnectFixedChnl;
// Name: L2CA_SendFixedChnlData
// Params: uint16_t fixed_cid, const RawAddress& rem_bda, BT_HDR* p_buf
// Returns: uint16_t
struct L2CA_SendFixedChnlData {
  std::function<uint16_t(uint16_t fixed_cid, const RawAddress& rem_bda,
                         BT_HDR* p_buf)>
      body{[](uint16_t /* fixed_cid */, const RawAddress& /* rem_bda */,
              BT_HDR* /* p_buf */) { return 0; }};
  uint16_t operator()(uint16_t fixed_cid, const RawAddress& rem_bda,
                      BT_HDR* p_buf) {
    return body(fixed_cid, rem_bda, p_buf);
  };
};
extern struct L2CA_SendFixedChnlData L2CA_SendFixedChnlData;
// Name: L2CA_RemoveFixedChnl
// Params: uint16_t fixed_cid, const RawAddress& rem_bda
// Returns: bool
struct L2CA_RemoveFixedChnl {
  std::function<bool(uint16_t fixed_cid, const RawAddress& rem_bda)> body{
      [](uint16_t /* fixed_cid */, const RawAddress& /* rem_bda */) {
        return false;
      }};
  bool operator()(uint16_t fixed_cid, const RawAddress& rem_bda) {
    return body(fixed_cid, rem_bda);
  };
};
extern struct L2CA_RemoveFixedChnl L2CA_RemoveFixedChnl;
// Name: L2CA_SetLeGattTimeout
// Params: const RawAddress& rem_bda, uint16_t idle_tout
// Returns: bool
struct L2CA_SetLeGattTimeout {
  std::function<bool(const RawAddress& rem_bda, uint16_t idle_tout)> body{
      [](const RawAddress& /* rem_bda */, uint16_t /* idle_tout */) {
        return false;
      }};
  bool operator()(const RawAddress& rem_bda, uint16_t idle_tout) {
    return body(rem_bda, idle_tout);
  };
};
extern struct L2CA_SetLeGattTimeout L2CA_SetLeGattTimeout;
// Name: L2CA_MarkLeLinkAsActive
// Params: const RawAddress& rem_bda
// Returns: bool
struct L2CA_MarkLeLinkAsActive {
  std::function<bool(const RawAddress& rem_bda)> body{
      [](const RawAddress& /* rem_bda */) { return false; }};
  bool operator()(const RawAddress& rem_bda) { return body(rem_bda); };
};
extern struct L2CA_MarkLeLinkAsActive L2CA_MarkLeLinkAsActive;
// Name: L2CA_DataWrite
// Params: uint16_t cid, BT_HDR* p_data
// Returns: uint8_t
struct L2CA_DataWrite {
  std::function<uint8_t(uint16_t cid, BT_HDR* p_data)> body{
      [](uint16_t /* cid */, BT_HDR* /* p_data */) { return 0; }};
  uint8_t operator()(uint16_t cid, BT_HDR* p_data) {
    return body(cid, p_data);
  };
};
extern struct L2CA_DataWrite L2CA_DataWrite;
// Name: L2CA_LECocDataWrite
// Params: uint16_t cid, BT_HDR* p_data
// Returns: uint8_t
struct L2CA_LECocDataWrite {
  std::function<uint8_t(uint16_t cid, BT_HDR* p_data)> body{
      [](uint16_t /* cid */, BT_HDR* /* p_data */) { return 0; }};
  uint8_t operator()(uint16_t cid, BT_HDR* p_data) {
    return body(cid, p_data);
  };
};
extern struct L2CA_LECocDataWrite L2CA_LECocDataWrite;
// Name: L2CA_SetChnlFlushability
// Params: uint16_t cid, bool is_flushable
// Returns: bool
struct L2CA_SetChnlFlushability {
  std::function<bool(uint16_t cid, bool is_flushable)> body{
      [](uint16_t /* cid */, bool /* is_flushable */) { return false; }};
  bool operator()(uint16_t cid, bool is_flushable) {
    return body(cid, is_flushable);
  };
};
extern struct L2CA_SetChnlFlushability L2CA_SetChnlFlushability;
// Name: L2CA_FlushChannel
// Params: uint16_t lcid, uint16_t num_to_flush
// Returns: uint16_t
struct L2CA_FlushChannel {
  std::function<uint16_t(uint16_t lcid, uint16_t num_to_flush)> body{
      [](uint16_t /* lcid */, uint16_t /* num_to_flush */) { return 0; }};
  uint16_t operator()(uint16_t lcid, uint16_t num_to_flush) {
    return body(lcid, num_to_flush);
  };
};
extern struct L2CA_FlushChannel L2CA_FlushChannel;
// Name: L2CA_IsLinkEstablished
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Returns: bool
struct L2CA_IsLinkEstablished {
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return false;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct L2CA_IsLinkEstablished L2CA_IsLinkEstablished;
// Name: L2CA_SetMediaStreamChannel
// Params: uint16_t handle, uint16_t channel_id, bool is_local_cid
// Returns: void
struct L2CA_SetMediaStreamChannel {
  std::function<void(uint16_t local_media_cid, bool status)> body{
      [](uint16_t /* local_media_cid */, bool /* status */) {}};
  void operator()(uint16_t local_media_cid, bool status) {
    body(local_media_cid, status);
  };
};
extern struct L2CA_SetMediaStreamChannel L2CA_SetMediaStreamChannel;
// Name: L2CA_isMediaChannel
// Params: uint16_t handle, uint16_t channel_id, bool is_local_cid
// Returns: bool
struct L2CA_isMediaChannel {
  std::function<bool(uint16_t handle, uint16_t channel_id, bool is_local_cid)>
      body{[](uint16_t /* handle */, uint16_t /* channel_id */,
              bool /* is_local_cid */) { return false; }};
  bool operator()(uint16_t handle, uint16_t channel_id, bool is_local_cid) {
    return body(handle, channel_id, is_local_cid);
  };
};
extern struct L2CA_isMediaChannel L2CA_isMediaChannel;
// Name: L2CA_LeCreditDefault
// Params:
// Returns: uint16_t
struct L2CA_LeCreditDefault {
  std::function<uint16_t()> body{[]() { return 0; }};
  uint16_t operator()() { return body(); };
};
extern struct L2CA_LeCreditDefault L2CA_LeCreditDefault;
// Name: L2CA_LeCreditThreshold
// Params:
// Returns: uint16_t
struct L2CA_LeCreditThreshold {
  std::function<uint16_t()> body{[]() { return 0; }};
  uint16_t operator()() { return body(); };
};
extern struct L2CA_LeCreditThreshold L2CA_LeCreditThreshold;

}  // namespace stack_l2cap_api
}  // namespace mock
}  // namespace test

// END mockcify generation
