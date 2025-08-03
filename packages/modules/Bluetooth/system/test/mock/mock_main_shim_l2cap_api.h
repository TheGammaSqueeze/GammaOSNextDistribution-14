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
 *   Functions generated:45
 *
 *  mockcify.pl ver 0.2
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include "stack/include/bt_hdr.h"
#include "stack/include/l2c_api.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace main_shim_l2cap_api {

// Shared state between mocked functions and tests
// Name: L2CA_ReadRemoteVersion
// Params: const RawAddress& addr, uint8_t* lmp_version, uint16_t* manufacturer,
// uint16_t* lmp_sub_version Returns: bool
struct L2CA_ReadRemoteVersion {
  std::function<bool(const RawAddress& addr, uint8_t* lmp_version,
                     uint16_t* manufacturer, uint16_t* lmp_sub_version)>
      body{[](const RawAddress& addr, uint8_t* lmp_version,
              uint16_t* manufacturer,
              uint16_t* lmp_sub_version) { return false; }};
  bool operator()(const RawAddress& addr, uint8_t* lmp_version,
                  uint16_t* manufacturer, uint16_t* lmp_sub_version) {
    return body(addr, lmp_version, manufacturer, lmp_sub_version);
  };
};
extern struct L2CA_ReadRemoteVersion L2CA_ReadRemoteVersion;
// Name: L2CA_ReadRemoteFeatures
// Params: const RawAddress& addr
// Returns: uint8_t*
struct L2CA_ReadRemoteFeatures {
  std::function<uint8_t*(const RawAddress& addr)> body{
      [](const RawAddress& addr) { return nullptr; }};
  uint8_t* operator()(const RawAddress& addr) { return body(addr); };
};
extern struct L2CA_ReadRemoteFeatures L2CA_ReadRemoteFeatures;
// Name: L2CA_Register
// Params: uint16_t client_psm, const tL2CAP_APPL_INFO& callbacks, bool
// enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu, uint16_t
// required_remote_mtu, uint16_t sec_level Returns: uint16_t
struct L2CA_Register {
  std::function<uint16_t(uint16_t client_psm, const tL2CAP_APPL_INFO& callbacks,
                         bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                         uint16_t my_mtu, uint16_t required_remote_mtu,
                         uint16_t sec_level)>
      body{[](uint16_t client_psm, const tL2CAP_APPL_INFO& callbacks,
              bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu,
              uint16_t required_remote_mtu, uint16_t sec_level) { return 0; }};
  uint16_t operator()(uint16_t client_psm, const tL2CAP_APPL_INFO& callbacks,
                      bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                      uint16_t my_mtu, uint16_t required_remote_mtu,
                      uint16_t sec_level) {
    return body(client_psm, callbacks, enable_snoop, p_ertm_info, my_mtu,
                required_remote_mtu, sec_level);
  };
};
extern struct L2CA_Register L2CA_Register;
// Name: L2CA_Deregister
// Params: uint16_t psm
// Returns: void
struct L2CA_Deregister {
  std::function<void(uint16_t psm)> body{[](uint16_t psm) {}};
  void operator()(uint16_t psm) { body(psm); };
};
extern struct L2CA_Deregister L2CA_Deregister;
// Name: L2CA_ConnectReq
// Params: uint16_t psm, const RawAddress& raw_address
// Returns: uint16_t
struct L2CA_ConnectReq {
  std::function<uint16_t(uint16_t psm, const RawAddress& raw_address)> body{
      [](uint16_t psm, const RawAddress& raw_address) { return 0; }};
  uint16_t operator()(uint16_t psm, const RawAddress& raw_address) {
    return body(psm, raw_address);
  };
};
extern struct L2CA_ConnectReq L2CA_ConnectReq;
// Name: L2CA_DisconnectReq
// Params: uint16_t cid
// Returns: bool
struct L2CA_DisconnectReq {
  std::function<bool(uint16_t cid)> body{[](uint16_t cid) { return false; }};
  bool operator()(uint16_t cid) { return body(cid); };
};
extern struct L2CA_DisconnectReq L2CA_DisconnectReq;
// Name: L2CA_DataWrite
// Params: uint16_t cid, BT_HDR* p_data
// Returns: uint8_t
struct L2CA_DataWrite {
  std::function<uint8_t(uint16_t cid, BT_HDR* p_data)> body{
      [](uint16_t cid, BT_HDR* p_data) { return 0; }};
  uint8_t operator()(uint16_t cid, BT_HDR* p_data) {
    return body(cid, p_data);
  };
};
extern struct L2CA_DataWrite L2CA_DataWrite;
// Name: L2CA_ReconfigCreditBasedConnsReq
// Params: const RawAddress& bd_addr, std::vector<uint16_t>& lcids,
// tL2CAP_LE_CFG_INFO* p_cfg Returns: bool
struct L2CA_ReconfigCreditBasedConnsReq {
  std::function<bool(const RawAddress& bd_addr, std::vector<uint16_t>& lcids,
                     tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[](const RawAddress& bd_addr, std::vector<uint16_t>& lcids,
              tL2CAP_LE_CFG_INFO* p_cfg) { return false; }};
  bool operator()(const RawAddress& bd_addr, std::vector<uint16_t>& lcids,
                  tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(bd_addr, lcids, p_cfg);
  };
};
extern struct L2CA_ReconfigCreditBasedConnsReq L2CA_ReconfigCreditBasedConnsReq;
// Name: L2CA_ConnectCreditBasedReq
// Params: uint16_t psm, const RawAddress& p_bd_addr, tL2CAP_LE_CFG_INFO* p_cfg
// Returns: std::vector<uint16_t>
struct L2CA_ConnectCreditBasedReq {
  std::vector<uint16_t> cids;
  std::function<std::vector<uint16_t>(uint16_t psm, const RawAddress& p_bd_addr,
                                      tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[this](uint16_t psm, const RawAddress& p_bd_addr,
                  tL2CAP_LE_CFG_INFO* p_cfg) { return cids; }};
  std::vector<uint16_t> operator()(uint16_t psm, const RawAddress& p_bd_addr,
                                   tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(psm, p_bd_addr, p_cfg);
  };
};
extern struct L2CA_ConnectCreditBasedReq L2CA_ConnectCreditBasedReq;
// Name: L2CA_ConnectCreditBasedRsp
// Params: const RawAddress& bd_addr, uint8_t id, std::vector<uint16_t>&
// accepted_lcids, uint16_t result, tL2CAP_LE_CFG_INFO* p_cfg Returns: bool
struct L2CA_ConnectCreditBasedRsp {
  std::function<bool(const RawAddress& bd_addr, uint8_t id,
                     std::vector<uint16_t>& accepted_lcids, uint16_t result,
                     tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[](const RawAddress& bd_addr, uint8_t id,
              std::vector<uint16_t>& accepted_lcids, uint16_t result,
              tL2CAP_LE_CFG_INFO* p_cfg) { return false; }};
  bool operator()(const RawAddress& bd_addr, uint8_t id,
                  std::vector<uint16_t>& accepted_lcids, uint16_t result,
                  tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(bd_addr, id, accepted_lcids, result, p_cfg);
  };
};
extern struct L2CA_ConnectCreditBasedRsp L2CA_ConnectCreditBasedRsp;
// Name: L2CA_SetIdleTimeoutByBdAddr
// Params: const RawAddress& bd_addr, uint16_t timeout, tBT_TRANSPORT transport
// Returns: bool
struct L2CA_SetIdleTimeoutByBdAddr {
  std::function<bool(const RawAddress& bd_addr, uint16_t timeout,
                     tBT_TRANSPORT transport)>
      body{[](const RawAddress& bd_addr, uint16_t timeout,
              tBT_TRANSPORT transport) { return false; }};
  bool operator()(const RawAddress& bd_addr, uint16_t timeout,
                  tBT_TRANSPORT transport) {
    return body(bd_addr, timeout, transport);
  };
};
extern struct L2CA_SetIdleTimeoutByBdAddr L2CA_SetIdleTimeoutByBdAddr;
// Name: L2CA_SetAclPriority
// Params: uint16_t handle, bool high_priority
// Returns: bool
struct L2CA_SetAclPriority {
  std::function<bool(uint16_t handle, bool high_priority)> body{
      [](uint16_t handle, bool high_priority) { return false; }};
  bool operator()(uint16_t handle, bool high_priority) {
    return body(handle, high_priority);
  };
};
extern struct L2CA_SetAclPriority L2CA_SetAclPriority;
// Name: L2CA_SetAclPriority
// Params: const RawAddress& bd_addr, tL2CAP_PRIORITY priority
// Returns: bool
struct L2CA_SetAclPriority2 {
  std::function<bool(const RawAddress& bd_addr, tL2CAP_PRIORITY priority)> body{
      [](const RawAddress& bd_addr, tL2CAP_PRIORITY priority) {
        return false;
      }};
  bool operator()(const RawAddress& bd_addr, tL2CAP_PRIORITY priority) {
    return body(bd_addr, priority);
  };
};
extern struct L2CA_SetAclPriority2 L2CA_SetAclPriority2;
// Name: L2CA_GetPeerFeatures
// Params: const RawAddress& bd_addr, uint32_t* p_ext_feat, uint8_t* p_chnl_mask
// Returns: bool
struct L2CA_GetPeerFeatures {
  std::function<bool(const RawAddress& bd_addr, uint32_t* p_ext_feat,
                     uint8_t* p_chnl_mask)>
      body{[](const RawAddress& bd_addr, uint32_t* p_ext_feat,
              uint8_t* p_chnl_mask) { return false; }};
  bool operator()(const RawAddress& bd_addr, uint32_t* p_ext_feat,
                  uint8_t* p_chnl_mask) {
    return body(bd_addr, p_ext_feat, p_chnl_mask);
  };
};
extern struct L2CA_GetPeerFeatures L2CA_GetPeerFeatures;
// Name: L2CA_RegisterFixedChannel
// Params: uint16_t cid, tL2CAP_FIXED_CHNL_REG* p_freg
// Returns: bool
struct L2CA_RegisterFixedChannel {
  std::function<bool(uint16_t cid, tL2CAP_FIXED_CHNL_REG* p_freg)> body{
      [](uint16_t cid, tL2CAP_FIXED_CHNL_REG* p_freg) { return false; }};
  bool operator()(uint16_t cid, tL2CAP_FIXED_CHNL_REG* p_freg) {
    return body(cid, p_freg);
  };
};
extern struct L2CA_RegisterFixedChannel L2CA_RegisterFixedChannel;
// Name: L2CA_ConnectFixedChnl
// Params: uint16_t cid, const RawAddress& rem_bda
// Returns: bool
struct L2CA_ConnectFixedChnl {
  std::function<bool(uint16_t cid, const RawAddress& rem_bda)> body{
      [](uint16_t cid, const RawAddress& rem_bda) { return false; }};
  bool operator()(uint16_t cid, const RawAddress& rem_bda) {
    return body(cid, rem_bda);
  };
};
extern struct L2CA_ConnectFixedChnl L2CA_ConnectFixedChnl;
// Name: L2CA_SendFixedChnlData
// Params: uint16_t cid, const RawAddress& rem_bda, BT_HDR* p_buf
// Returns: uint16_t
struct L2CA_SendFixedChnlData {
  std::function<uint16_t(uint16_t cid, const RawAddress& rem_bda,
                         BT_HDR* p_buf)>
      body{[](uint16_t cid, const RawAddress& rem_bda, BT_HDR* p_buf) {
        return 0;
      }};
  uint16_t operator()(uint16_t cid, const RawAddress& rem_bda, BT_HDR* p_buf) {
    return body(cid, rem_bda, p_buf);
  };
};
extern struct L2CA_SendFixedChnlData L2CA_SendFixedChnlData;
// Name: L2CA_RemoveFixedChnl
// Params: uint16_t cid, const RawAddress& rem_bda
// Returns: bool
struct L2CA_RemoveFixedChnl {
  std::function<bool(uint16_t cid, const RawAddress& rem_bda)> body{
      [](uint16_t cid, const RawAddress& rem_bda) { return false; }};
  bool operator()(uint16_t cid, const RawAddress& rem_bda) {
    return body(cid, rem_bda);
  };
};
extern struct L2CA_RemoveFixedChnl L2CA_RemoveFixedChnl;
// Name: L2CA_GetLeHandle
// Params: const RawAddress& rem_bda
// Returns: uint16_t
struct L2CA_GetLeHandle {
  std::function<uint16_t(const RawAddress& rem_bda)> body{
      [](const RawAddress& rem_bda) { return 0; }};
  uint16_t operator()(const RawAddress& rem_bda) { return body(rem_bda); };
};
extern struct L2CA_GetLeHandle L2CA_GetLeHandle;
// Name: L2CA_LeConnectionUpdate
// Params: const RawAddress& rem_bda, uint16_t min_int, uint16_t max_int,
// uint16_t latency, uint16_t timeout, uint16_t min_ce_len, uint16_t max_ce_len
// Returns: void
struct L2CA_LeConnectionUpdate {
  std::function<void(const RawAddress& rem_bda, uint16_t min_int,
                     uint16_t max_int, uint16_t latency, uint16_t timeout,
                     uint16_t min_ce_len, uint16_t max_ce_len)>
      body{[](const RawAddress& rem_bda, uint16_t min_int, uint16_t max_int,
              uint16_t latency, uint16_t timeout, uint16_t min_ce_len,
              uint16_t max_ce_len) {}};
  void operator()(const RawAddress& rem_bda, uint16_t min_int, uint16_t max_int,
                  uint16_t latency, uint16_t timeout, uint16_t min_ce_len,
                  uint16_t max_ce_len) {
    body(rem_bda, min_int, max_int, latency, timeout, min_ce_len, max_ce_len);
  };
};
extern struct L2CA_LeConnectionUpdate L2CA_LeConnectionUpdate;
// Name: L2CA_LockBleConnParamsForServiceDiscovery
// Params: const RawAddress& rem_bda, bool enable
// Returns: void
struct L2CA_LockBleConnParamsForServiceDiscovery {
  std::function<void(const RawAddress& rem_bda, bool enable)> body{
      [](const RawAddress& rem_bda, bool enable) { return false; }};
  void operator()(const RawAddress& rem_bda, bool enable) {
    return body(rem_bda, enable);
  };
};
extern struct L2CA_LockBleConnParamsForServiceDiscovery
    L2CA_LockBleConnParamsForServiceDiscovery;
// Name: L2CA_LockBleConnParamsForProfileConnection
// Params: const RawAddress& rem_bda, bool enable
// Returns: void
struct L2CA_LockBleConnParamsForProfileConnection {
  std::function<void(const RawAddress& rem_bda, bool enable)> body{
      [](const RawAddress& rem_bda, bool enable) { return false; }};
  void operator()(const RawAddress& rem_bda, bool enable) {
    return body(rem_bda, enable);
  };
};
extern struct L2CA_LockBleConnParamsForProfileConnection
    L2CA_LockBleConnParamsForProfileConnection;
// Name: L2CA_GetRemoteCid
// Params: uint16_t lcid, uint16_t* rcid
// Returns: bool
struct L2CA_GetRemoteCid {
  std::function<bool(uint16_t lcid, uint16_t* rcid)> body{
      [](uint16_t lcid, uint16_t* rcid) { return false; }};
  bool operator()(uint16_t lcid, uint16_t* rcid) { return body(lcid, rcid); };
};
extern struct L2CA_GetRemoteCid L2CA_GetRemoteCid;
// Name: L2CA_SetTxPriority
// Params: uint16_t cid, tL2CAP_CHNL_PRIORITY priority
// Returns: bool
struct L2CA_SetTxPriority {
  std::function<bool(uint16_t cid, tL2CAP_CHNL_PRIORITY priority)> body{
      [](uint16_t cid, tL2CAP_CHNL_PRIORITY priority) { return false; }};
  bool operator()(uint16_t cid, tL2CAP_CHNL_PRIORITY priority) {
    return body(cid, priority);
  };
};
extern struct L2CA_SetTxPriority L2CA_SetTxPriority;
// Name: L2CA_SetLeGattTimeout
// Params: const RawAddress& rem_bda, uint16_t idle_tout
// Returns: bool
struct L2CA_SetLeGattTimeout {
  std::function<bool(const RawAddress& rem_bda, uint16_t idle_tout)> body{
      [](const RawAddress& rem_bda, uint16_t idle_tout) { return false; }};
  bool operator()(const RawAddress& rem_bda, uint16_t idle_tout) {
    return body(rem_bda, idle_tout);
  };
};
extern struct L2CA_SetLeGattTimeout L2CA_SetLeGattTimeout;
// Name: L2CA_SetChnlFlushability
// Params: uint16_t cid, bool is_flushable
// Returns: bool
struct L2CA_SetChnlFlushability {
  std::function<bool(uint16_t cid, bool is_flushable)> body{
      [](uint16_t cid, bool is_flushable) { return false; }};
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
      [](uint16_t lcid, uint16_t num_to_flush) { return 0; }};
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
      [](const RawAddress& bd_addr, tBT_TRANSPORT transport) { return false; }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct L2CA_IsLinkEstablished L2CA_IsLinkEstablished;
// Name: L2CA_IsLeLink
// Params: uint16_t acl_handle
// Returns: bool
struct L2CA_IsLeLink {
  std::function<bool(uint16_t acl_handle)> body{
      [](uint16_t acl_handle) { return false; }};
  bool operator()(uint16_t acl_handle) { return body(acl_handle); };
};
extern struct L2CA_IsLeLink L2CA_IsLeLink;
// Name: L2CA_ReadConnectionAddr
// Params: const RawAddress& pseudo_addr, RawAddress& conn_addr, uint8_t*
// p_addr_type Returns: void
struct L2CA_ReadConnectionAddr {
  std::function<void(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type)>
      body{[](const RawAddress& pseudo_addr, RawAddress& conn_addr,
              tBLE_ADDR_TYPE* p_addr_type) {}};
  void operator()(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type) {
    body(pseudo_addr, conn_addr, p_addr_type);
  };
};
extern struct L2CA_ReadConnectionAddr L2CA_ReadConnectionAddr;
// Name: L2CA_ReadRemoteConnectionAddr
// Params: const RawAddress& pseudo_addr, RawAddress& conn_addr, uint8_t*
// p_addr_type Returns: bool
struct L2CA_ReadRemoteConnectionAddr {
  std::function<bool(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type)>
      body{[](const RawAddress& pseudo_addr, RawAddress& conn_addr,
              tBLE_ADDR_TYPE* p_addr_type) { return false; }};
  bool operator()(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type) {
    return body(pseudo_addr, conn_addr, p_addr_type);
  };
};
extern struct L2CA_ReadRemoteConnectionAddr L2CA_ReadRemoteConnectionAddr;
// Name: L2CA_GetBleConnRole
// Params: const RawAddress& bd_addr
// Returns: hci_role_t
struct L2CA_GetBleConnRole {
  std::function<hci_role_t(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) { return HCI_ROLE_CENTRAL; }};
  hci_role_t operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct L2CA_GetBleConnRole L2CA_GetBleConnRole;
// Name: L2CA_ConnectForSecurity
// Params: const RawAddress& bd_addr
// Returns: void
struct L2CA_ConnectForSecurity {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& bd_addr) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct L2CA_ConnectForSecurity L2CA_ConnectForSecurity;
// Name: L2CA_SetBondingState
// Params: const RawAddress& bd_addr, bool is_bonding
// Returns: void
struct L2CA_SetBondingState {
  std::function<void(const RawAddress& bd_addr, bool is_bonding)> body{
      [](const RawAddress& bd_addr, bool is_bonding) {}};
  void operator()(const RawAddress& bd_addr, bool is_bonding) {
    body(bd_addr, is_bonding);
  };
};
extern struct L2CA_SetBondingState L2CA_SetBondingState;
// Name: L2CA_DisconnectLink
// Params: const RawAddress& remote
// Returns: void
struct L2CA_DisconnectLink {
  std::function<void(const RawAddress& remote)> body{
      [](const RawAddress& remote) {}};
  void operator()(const RawAddress& remote) { body(remote); };
};
extern struct L2CA_DisconnectLink L2CA_DisconnectLink;
// Name: L2CA_GetNumLinks
// Params:
// Returns: uint16_t
struct L2CA_GetNumLinks {
  std::function<uint16_t()> body{[]() { return 0; }};
  uint16_t operator()() { return body(); };
};
extern struct L2CA_GetNumLinks L2CA_GetNumLinks;
// Name: L2CA_AllocateLePSM
// Params:
// Returns: uint16_t
struct L2CA_AllocateLePSM {
  std::function<uint16_t()> body{[]() { return 0; }};
  uint16_t operator()() { return body(); };
};
extern struct L2CA_AllocateLePSM L2CA_AllocateLePSM;
// Name: L2CA_FreeLePSM
// Params: uint16_t psm
// Returns: void
struct L2CA_FreeLePSM {
  std::function<void(uint16_t psm)> body{[](uint16_t psm) {}};
  void operator()(uint16_t psm) { body(psm); };
};
extern struct L2CA_FreeLePSM L2CA_FreeLePSM;
// Name: L2CA_RegisterLECoc
// Params: uint16_t psm, const tL2CAP_APPL_INFO& callbacks, uint16_t sec_level,
// tL2CAP_LE_CFG_INFO cfg Returns: uint16_t
struct L2CA_RegisterLECoc {
  std::function<uint16_t(uint16_t psm, const tL2CAP_APPL_INFO& callbacks,
                         uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg)>
      body{[](uint16_t psm, const tL2CAP_APPL_INFO& callbacks,
              uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg) { return 0; }};
  uint16_t operator()(uint16_t psm, const tL2CAP_APPL_INFO& callbacks,
                      uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg) {
    return body(psm, callbacks, sec_level, cfg);
  };
};
extern struct L2CA_RegisterLECoc L2CA_RegisterLECoc;
// Name: L2CA_DeregisterLECoc
// Params: uint16_t psm
// Returns: void
struct L2CA_DeregisterLECoc {
  std::function<void(uint16_t psm)> body{[](uint16_t psm) {}};
  void operator()(uint16_t psm) { body(psm); };
};
extern struct L2CA_DeregisterLECoc L2CA_DeregisterLECoc;
// Name: L2CA_ConnectLECocReq
// Params: uint16_t psm, const RawAddress& p_bd_addr, tL2CAP_LE_CFG_INFO* p_cfg
// Returns: uint16_t
struct L2CA_ConnectLECocReq {
  std::function<uint16_t(uint16_t psm, const RawAddress& p_bd_addr,
                         tL2CAP_LE_CFG_INFO* p_cfg)>
      body{[](uint16_t psm, const RawAddress& p_bd_addr,
              tL2CAP_LE_CFG_INFO* p_cfg) { return 0; }};
  uint16_t operator()(uint16_t psm, const RawAddress& p_bd_addr,
                      tL2CAP_LE_CFG_INFO* p_cfg) {
    return body(psm, p_bd_addr, p_cfg);
  };
};
extern struct L2CA_ConnectLECocReq L2CA_ConnectLECocReq;
// Name: L2CA_GetPeerLECocConfig
// Params: uint16_t cid, tL2CAP_LE_CFG_INFO* peer_cfg
// Returns: bool
struct L2CA_GetPeerLECocConfig {
  std::function<bool(uint16_t cid, tL2CAP_LE_CFG_INFO* peer_cfg)> body{
      [](uint16_t cid, tL2CAP_LE_CFG_INFO* peer_cfg) { return false; }};
  bool operator()(uint16_t cid, tL2CAP_LE_CFG_INFO* peer_cfg) {
    return body(cid, peer_cfg);
  };
};
extern struct L2CA_GetPeerLECocConfig L2CA_GetPeerLECocConfig;
// Name: L2CA_DisconnectLECocReq
// Params: uint16_t cid
// Returns: bool
struct L2CA_DisconnectLECocReq {
  std::function<bool(uint16_t cid)> body{[](uint16_t cid) { return false; }};
  bool operator()(uint16_t cid) { return body(cid); };
};
extern struct L2CA_DisconnectLECocReq L2CA_DisconnectLECocReq;
// Name: L2CA_LECocDataWrite
// Params: uint16_t cid, BT_HDR* p_data
// Returns: uint8_t
struct L2CA_LECocDataWrite {
  std::function<uint8_t(uint16_t cid, BT_HDR* p_data)> body{
      [](uint16_t cid, BT_HDR* p_data) { return 0; }};
  uint8_t operator()(uint16_t cid, BT_HDR* p_data) {
    return body(cid, p_data);
  };
};
extern struct L2CA_LECocDataWrite L2CA_LECocDataWrite;
// Name: L2CA_SwitchRoleToCentral
// Params: const RawAddress& addr
// Returns: void
struct L2CA_SwitchRoleToCentral {
  std::function<void(const RawAddress& addr)> body{
      [](const RawAddress& addr) {}};
  void operator()(const RawAddress& addr) { body(addr); };
};
extern struct L2CA_SwitchRoleToCentral L2CA_SwitchRoleToCentral;

}  // namespace main_shim_l2cap_api
}  // namespace mock
}  // namespace test

// END mockcify generation
