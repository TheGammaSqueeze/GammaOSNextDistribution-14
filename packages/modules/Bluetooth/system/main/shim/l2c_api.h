/*
 * Copyright 2019 The Android Open Source Project
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

#pragma once

#include <vector>

#include "stack/include/bt_hdr.h"
#include "stack/include/l2c_api.h"
#include "types/ble_address_with_type.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace shim {

/*******************************************************************************
 *
 * Function         L2CA_Register
 *
 * Description      Other layers call this function to register for L2CAP
 *                  services.
 *
 * Returns          PSM to use or zero if error. Typically, the PSM returned
 *                  is the same as was passed in, but for an outgoing-only
 *                  connection to a dynamic PSM, a "virtual" PSM is returned
 *                  and should be used in the calls to L2CA_ConnectReq() and
 *                  BTM_SetSecurityLevel().
 *
 ******************************************************************************/
uint16_t L2CA_Register(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                       bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                       uint16_t my_mtu, uint16_t required_remote_mtu,
                       uint16_t sec_level);

/*******************************************************************************
 *
 * Function         L2CA_Deregister
 *
 * Description      Other layers call this function to deregister for L2CAP
 *                  services.
 *
 * Returns          void
 *
 ******************************************************************************/
void L2CA_Deregister(uint16_t psm);

/*******************************************************************************
 *
 * Function         L2CA_AllocateLePSM
 *
 * Description      Other layers call this function to find an unused LE PSM for
 *                  L2CAP services.
 *
 * Returns          LE_PSM to use if success. Otherwise returns 0.
 *
 ******************************************************************************/
uint16_t L2CA_AllocateLePSM(void);

/*******************************************************************************
 *
 * Function         L2CA_FreeLePSM
 *
 * Description      Free an assigned LE PSM.
 *
 * Returns          void
 *
 ******************************************************************************/
void L2CA_FreeLePSM(uint16_t psm);

/*******************************************************************************
 *
 * Function         L2CA_ConnectReq
 *
 * Description      Higher layers call this function to create an L2CAP
 *                  connection.
 *                  Note that the connection is not established at this time,
 *                  but connection establishment gets started. The callback
 *                  will be invoked when connection establishes or fails.
 *
 * Returns          the CID of the connection, or 0 if it failed to start
 *
 ******************************************************************************/
uint16_t L2CA_ConnectReq(uint16_t psm, const RawAddress& p_bd_addr);

/*******************************************************************************
 *
 * Function         L2CA_RegisterLECoc
 *
 * Description      Other layers call this function to register for L2CAP
 *                  Connection Oriented Channel.
 *
 * Returns          PSM to use or zero if error. Typically, the PSM returned
 *                  is the same as was passed in, but for an outgoing-only
 *                  connection to a dynamic PSM, a "virtual" PSM is returned
 *                  and should be used in the calls to L2CA_ConnectLECocReq()
 *                  and BTM_SetSecurityLevel().
 *
 ******************************************************************************/
uint16_t L2CA_RegisterLECoc(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                            uint16_t sec_level, tL2CAP_LE_CFG_INFO cfg);

/*******************************************************************************
 *
 * Function         L2CA_DeregisterLECoc
 *
 * Description      Other layers call this function to deregister for L2CAP
 *                  Connection Oriented Channel.
 *
 * Returns          void
 *
 ******************************************************************************/
void L2CA_DeregisterLECoc(uint16_t psm);

/*******************************************************************************
 *
 * Function         L2CA_ConnectLECocReq
 *
 * Description      Higher layers call this function to create an L2CAP LE COC.
 *                  Note that the connection is not established at this time,
 *                  but connection establishment gets started. The callback
 *                  will be invoked when connection establishes or fails.
 *
 * Returns          the CID of the connection, or 0 if it failed to start
 *
 ******************************************************************************/
uint16_t L2CA_ConnectLECocReq(uint16_t psm, const RawAddress& p_bd_addr,
                              tL2CAP_LE_CFG_INFO* p_cfg);

/*******************************************************************************
 *
 *  Function         L2CA_GetPeerLECocConfig
 *
 *  Description      Get peers configuration for LE Connection Oriented Channel.
 *
 *  Return value:    true if peer is connected
 *
 ******************************************************************************/
bool L2CA_GetPeerLECocConfig(uint16_t lcid, tL2CAP_LE_CFG_INFO* peer_cfg);

/*******************************************************************************
 *
 *  Function         L2CA_ReconfigCreditBasedConnsReq
 *
 *  Description      Start reconfigure procedure on Credit Based Connection Oriented
 *                   Channels.
 *
 *  Return value:    true if peer is connected
 *
 ******************************************************************************/

bool L2CA_ReconfigCreditBasedConnsReq(const RawAddress& bd_addr,
                                      std::vector<uint16_t>& lcids,
                                      tL2CAP_LE_CFG_INFO* p_cfg);

/*******************************************************************************
 *
 *  Function         L2CA_ConnectCreditBasedReq
 *
 *  Description      With this function L2CAP will initiate setup of up to 5 credit
 *                   based connections for given psm using provided configuration.
 *                   L2CAP will notify user on the connection result, by calling
 *                   pL2CA_CreditBasedConnectCfm_Cb for each cid with a result.
 *
 *  Return value: vector of allocated local cids for the connection
 *
 ******************************************************************************/

extern std::vector<uint16_t> L2CA_ConnectCreditBasedReq(
    uint16_t psm, const RawAddress& p_bd_addr, tL2CAP_LE_CFG_INFO* p_cfg);

/*******************************************************************************
 *
 *  Function         L2CA_ConnectCreditBasedRsp
 *
 *  Description      Response for the pL2CA_CreditBasedConnectInd_Cb which is the
 *                   indication for peer requesting credit based connection.
 *
 *  Return value:    true if peer is connected
 *
 ******************************************************************************/

bool L2CA_ConnectCreditBasedRsp(const RawAddress& p_bd_addr, uint8_t id,
                                std::vector<uint16_t>& accepted_lcids,
                                uint16_t result, tL2CAP_LE_CFG_INFO* p_cfg);

/*******************************************************************************
 *
 * Function         L2CA_DisconnectReq
 *
 * Description      Higher layers call this function to disconnect a channel.
 *
 * Returns          true if disconnect sent, else false
 *
 ******************************************************************************/
bool L2CA_DisconnectReq(uint16_t cid);

bool L2CA_DisconnectLECocReq(uint16_t cid);

/*******************************************************************************
 *
 * Function         L2CA_DataWrite
 *
 * Description      Higher layers call this function to write data.
 *
 * Returns          L2CAP_DW_SUCCESS, if data accepted, else false
 *                  L2CAP_DW_CONGESTED, if data accepted and the channel is
 *                                      congested
 *                  L2CAP_DW_FAILED, if error
 *
 ******************************************************************************/
uint8_t L2CA_DataWrite(uint16_t cid, BT_HDR* p_data);

uint8_t L2CA_LECocDataWrite(uint16_t cid, BT_HDR* p_data);

// Given a local channel identifier, |lcid|, this function returns the bound
// remote channel identifier, |rcid|. If
// |lcid| is not known or is invalid, this function returns false and does not
// modify the value pointed at by |rcid|. |rcid| may be NULL.
bool L2CA_GetRemoteCid(uint16_t lcid, uint16_t* rcid);

/*******************************************************************************
 *
 * Function         L2CA_SetIdleTimeoutByBdAddr
 *
 * Description      Higher layers call this function to set the idle timeout for
 *                  a connection. The "idle timeout" is the amount of time that
 *                  a connection can remain up with no L2CAP channels on it.
 *                  A timeout of zero means that the connection will be torn
 *                  down immediately when the last channel is removed.
 *                  A timeout of 0xFFFF means no timeout. Values are in seconds.
 *                  A bd_addr is the remote BD address. If bd_addr =
 *                  RawAddress::kAny, then the idle timeouts for all active
 *                  l2cap links will be changed.
 *
 * Returns          true if command succeeded, false if failed
 *
 * NOTE             This timeout applies to all logical channels active on the
 *                  ACL link.
 ******************************************************************************/
bool L2CA_SetIdleTimeoutByBdAddr(const RawAddress& bd_addr, uint16_t timeout,
                                 tBT_TRANSPORT transport);

/*******************************************************************************
 *
 * Function     L2CA_FlushChannel
 *
 * Description  This function flushes none, some or all buffers queued up
 *              for xmission for a particular CID. If called with
 *              L2CAP_FLUSH_CHANS_GET (0), it simply returns the number
 *              of buffers queued for that CID L2CAP_FLUSH_CHANS_ALL (0xffff)
 *              flushes all buffers.  All other values specifies the maximum
 *              buffers to flush.
 *
 * Returns      Number of buffers left queued for that CID
 *
 ******************************************************************************/
uint16_t L2CA_FlushChannel(uint16_t lcid, uint16_t num_to_flush);

/*******************************************************************************
 *
 * Function         L2CA_SetAclPriority
 *
 * Description      Sets the transmission priority for an ACL channel.
 *                  (For initial implementation only two values are valid.
 *                  L2CAP_PRIORITY_NORMAL and L2CAP_PRIORITY_HIGH).
 *
 * Returns          true if a valid channel, else false
 *
 ******************************************************************************/
bool L2CA_SetAclPriority(const RawAddress& bd_addr, tL2CAP_PRIORITY priority);

/*******************************************************************************
 *
 * Function         L2CA_SetTxPriority
 *
 * Description      Sets the transmission priority for a channel. (FCR Mode)
 *
 * Returns          true if a valid channel, else false
 *
 ******************************************************************************/
bool L2CA_SetTxPriority(uint16_t cid, tL2CAP_CHNL_PRIORITY priority);

/*******************************************************************************
 *
 * Function         L2CA_SetChnlFlushability
 *
 * Description      Higher layers call this function to set a channels
 *                  flushability flags
 *
 * Returns          true if CID found, else false
 *
 ******************************************************************************/
bool L2CA_SetChnlFlushability(uint16_t cid, bool is_flushable);

/*******************************************************************************
 *
 *  Function         L2CA_GetPeerFeatures
 *
 *  Description      Get a peers features and fixed channel map
 *
 *  Parameters:      BD address of the peer
 *                   Pointers to features and channel mask storage area
 *
 *  Return value:    true if peer is connected
 *
 ******************************************************************************/
bool L2CA_GetPeerFeatures(const RawAddress& bd_addr, uint32_t* p_ext_feat,
                          uint8_t* p_chnl_mask);

/*******************************************************************************
 *
 *  Function        L2CA_RegisterFixedChannel
 *
 *  Description     Register a fixed channel.
 *
 *  Parameters:     Fixed Channel #
 *                  Channel Callbacks and config
 *
 *  Return value:   true if registered OK
 *
 ******************************************************************************/
bool L2CA_RegisterFixedChannel(uint16_t fixed_cid,
                               tL2CAP_FIXED_CHNL_REG* p_freg);

/*******************************************************************************
 *
 *  Function        L2CA_ConnectFixedChnl
 *
 *  Description     Connect an fixed signalling channel to a remote device.
 *
 *  Parameters:     Fixed CID
 *                  BD Address of remote
 *
 *  Return value:   true if connection started
 *
 ******************************************************************************/
bool L2CA_ConnectFixedChnl(uint16_t fixed_cid, const RawAddress& bd_addr);

/*******************************************************************************
 *
 *  Function        L2CA_SendFixedChnlData
 *
 *  Description     Write data on a fixed signalling channel.
 *
 *  Parameters:     Fixed CID
 *                  BD Address of remote
 *                  Pointer to buffer of type BT_HDR
 *
 * Return value     L2CAP_DW_SUCCESS, if data accepted
 *                  L2CAP_DW_FAILED,  if error
 *
 ******************************************************************************/
uint16_t L2CA_SendFixedChnlData(uint16_t fixed_cid, const RawAddress& rem_bda,
                                BT_HDR* p_buf);

/*******************************************************************************
 *
 *  Function        L2CA_RemoveFixedChnl
 *
 *  Description     Remove a fixed channel to a remote device.
 *
 *  Parameters:     Fixed CID
 *                  BD Address of remote
 *                  Idle timeout to use (or 0xFFFF if don't care)
 *
 *  Return value:   true if channel removed
 *
 ******************************************************************************/
bool L2CA_RemoveFixedChnl(uint16_t fixed_cid, const RawAddress& rem_bda);

uint16_t L2CA_GetLeHandle(const RawAddress& rem_bda);
hci_role_t L2CA_GetBleConnRole(const RawAddress& bd_addr);

void L2CA_LeConnectionUpdate(const RawAddress& rem_bda, uint16_t min_int,
                             uint16_t max_int, uint16_t latency,
                             uint16_t timeout, uint16_t min_ce_len,
                             uint16_t max_ce_len);

/*******************************************************************************
 *
 * Function         L2CA_SetLeGattTimeout
 *
 * Description      Higher layers call this function to set the idle timeout for
 *                  a fixed channel. The "idle timeout" is the amount of time
 *                  that a connection can remain up with no L2CAP channels on
 *                  it. A timeout of zero means that the connection will be torn
 *                  down immediately when the last channel is removed.
 *                  A timeout of 0xFFFF means no timeout. Values are in seconds.
 *                  A bd_addr is the remote BD address. If bd_addr =
 *                  RawAddress::kAny, then the idle timeouts for all active
 *                  l2cap links will be changed.
 *
 * Returns          true if command succeeded, false if failed
 *
 ******************************************************************************/
bool L2CA_SetLeGattTimeout(const RawAddress& rem_bda, uint16_t idle_tout);

bool L2CA_MarkLeLinkAsActive(const RawAddress& rem_bda);

bool L2CA_UpdateBleConnParams(const RawAddress& rem_bda, uint16_t min_int,
                              uint16_t max_int, uint16_t latency,
                              uint16_t timeout, uint16_t min_ce_len,
                              uint16_t max_ce_len);
/* When called with lock=true, LE connection parameters will be locked on
 * fastest value, and we won't accept request to change it from remote. When
 * called with lock=false, parameters are relaxed.
 */
void L2CA_LockBleConnParamsForServiceDiscovery(const RawAddress& rem_bda,
                                               bool lock);

/* When called with lock=true, LE connection parameters will be locked on
 * fastest value, and we won't accept request to change it from remote. When
 * called with lock=false, parameters are relaxed.
 */
void L2CA_LockBleConnParamsForProfileConnection(const RawAddress& rem_bda,
                                                bool lock);

/*******************************************************************************
 *
 * Function         L2CA_GetBleConnRole
 *
 * Description      This function returns the connection role.
 *
 * Returns          link role.
 *
 ******************************************************************************/
hci_role_t L2CA_GetBleConnRole(const RawAddress& bd_addr);

/**
 * Check whether an ACL or LE link to the remote device is established
 */
bool L2CA_IsLinkEstablished(const RawAddress& bd_addr, tBT_TRANSPORT transport);

void L2CA_ConnectForSecurity(const RawAddress& bd_addr);

// Set bonding state to acquire/release link refcount
void L2CA_SetBondingState(const RawAddress& p_bd_addr, bool is_bonding);

void L2CA_SwitchRoleToCentral(const RawAddress& addr);

bool L2CA_ReadRemoteVersion(const RawAddress& addr, uint8_t* lmp_version,
                            uint16_t* manufacturer, uint16_t* lmp_sub_version);

uint8_t* L2CA_ReadRemoteFeatures(const RawAddress& addr);

void L2CA_DisconnectLink(const RawAddress& remote);

uint16_t L2CA_GetNumLinks();

bool L2CA_IsLeLink(uint16_t acl_handle);

void L2CA_ReadConnectionAddr(const RawAddress& pseudo_addr,
                             RawAddress& conn_addr,
                             tBLE_ADDR_TYPE* p_addr_type);

bool L2CA_ReadRemoteConnectionAddr(const RawAddress& pseudo_addr,
                                   RawAddress& conn_addr,
                                   tBLE_ADDR_TYPE* p_addr_type);

}  // namespace shim
}  // namespace bluetooth
