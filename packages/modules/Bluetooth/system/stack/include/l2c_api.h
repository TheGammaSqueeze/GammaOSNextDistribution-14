/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

/******************************************************************************
 *
 *  this file contains the L2CAP API definitions
 *
 ******************************************************************************/
#ifndef L2C_API_H
#define L2C_API_H

#include <bluetooth/log.h>
#include <stdbool.h>

#include <cstdint>
#include <vector>

#include "hcidefs.h"
#include "internal_include/bt_target.h"
#include "l2cdefs.h"
#include "stack/include/bt_hdr.h"
#include "types/bt_transport.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants
 ****************************************************************************/

/* Define the minimum offset that L2CAP needs in a buffer. This is made up of
 * HCI type(1), len(2), handle(2), L2CAP len(2) and CID(2) => 9
 */
#define L2CAP_MIN_OFFSET 13 /* plus control(2), SDU length(2) */

#define L2CAP_LCC_SDU_LENGTH 2
#define L2CAP_LCC_OFFSET \
  (L2CAP_MIN_OFFSET + L2CAP_LCC_SDU_LENGTH) /* plus SDU length(2) */

#define L2CAP_FCS_LENGTH 2

/* result code for L2CA_DataWrite() */
#define L2CAP_DW_FAILED false
#define L2CAP_DW_SUCCESS true
#define L2CAP_DW_CONGESTED 2

/* Values for priority parameter to L2CA_SetAclPriority */
typedef enum : uint8_t {
  L2CAP_PRIORITY_NORMAL = 0,
  L2CAP_PRIORITY_HIGH = 1,
} tL2CAP_PRIORITY;

/* Values for priority parameter to L2CA_SetAclLatency */
typedef enum : uint8_t {
  L2CAP_LATENCY_NORMAL = 0,
  L2CAP_LATENCY_LOW = 1,
} tL2CAP_LATENCY;

/* Values for priority parameter to L2CA_SetTxPriority */
#define L2CAP_CHNL_PRIORITY_HIGH 0
#define L2CAP_CHNL_PRIORITY_LOW 2

typedef uint8_t tL2CAP_CHNL_PRIORITY;

/* Values for Tx/Rx data rate parameter to L2CA_SetChnlDataRate */
#define L2CAP_CHNL_DATA_RATE_LOW 1

typedef uint8_t tL2CAP_CHNL_DATA_RATE;

/* Data Packet Flags  (bits 2-15 are reserved) */
/* layer specific 14-15 bits are used for FCR SAR */
#define L2CAP_FLUSHABLE_MASK 0x0003
#define L2CAP_FLUSHABLE_CH_BASED 0x0000
#define L2CAP_FLUSHABLE_PKT 0x0001
#define L2CAP_NON_FLUSHABLE_PKT 0x0002

/* L2CA_FlushChannel num_to_flush definitions */
#define L2CAP_FLUSH_CHANS_ALL 0xffff
#define L2CAP_FLUSH_CHANS_GET 0x0000

/* Values for 'allowed_modes' field passed in structure tL2CAP_ERTM_INFO
 */
#define L2CAP_FCR_CHAN_OPT_BASIC (1 << L2CAP_FCR_BASIC_MODE)
#define L2CAP_FCR_CHAN_OPT_ERTM (1 << L2CAP_FCR_ERTM_MODE)

#define L2CAP_FCR_CHAN_OPT_ALL_MASK \
  (L2CAP_FCR_CHAN_OPT_BASIC | L2CAP_FCR_CHAN_OPT_ERTM)

/* Validity check for PSM.  PSM values must be odd.  Also, all PSM values must
 * be assigned such that the least significant bit of the most sigificant
 * octet equals zero.
 */
#define L2C_INVALID_PSM(psm) (((psm)&0x0101) != 0x0001)
#define L2C_IS_VALID_PSM(psm) (((psm)&0x0101) == 0x0001)
#define L2C_IS_VALID_LE_PSM(psm) (((psm) > 0x0000) && ((psm) < 0x0100))

#define L2CAP_NO_IDLE_TIMEOUT 0xFFFF

/*****************************************************************************
 *  Type Definitions
 ****************************************************************************/

typedef struct {
#define L2CAP_FCR_BASIC_MODE 0x00
#define L2CAP_FCR_ERTM_MODE 0x03
#define L2CAP_FCR_LE_COC_MODE 0x05

  uint8_t mode;

  uint8_t tx_win_sz;
  uint8_t max_transmit;
  uint16_t rtrans_tout;
  uint16_t mon_tout;
  uint16_t mps;
} tL2CAP_FCR_OPTS;

/* default options for ERTM mode */
constexpr tL2CAP_FCR_OPTS kDefaultErtmOptions = {
    L2CAP_FCR_ERTM_MODE,
    10,    /* Tx window size */
    20,    /* Maximum transmissions before disconnecting */
    2000,  /* Retransmission timeout (2 secs) */
    12000, /* Monitor timeout (12 secs) */
    1010   /* MPS segment size */
};

typedef struct {
  uint8_t qos_flags;          /* TBD */
  uint8_t service_type;       /* see below */
  uint32_t token_rate;        /* bytes/second */
  uint32_t token_bucket_size; /* bytes */
  uint32_t peak_bandwidth;    /* bytes/second */
  uint32_t latency;           /* microseconds */
  uint32_t delay_variation;   /* microseconds */
} FLOW_SPEC;

/* Values for service_type */
#define SVC_TYPE_BEST_EFFORT 1
#define SVC_TYPE_GUARANTEED 2

/* Define a structure to hold the configuration parameters. Since the
 * parameters are optional, for each parameter there is a boolean to
 * use to signify its presence or absence.
 */
typedef struct {
  uint16_t result; /* Only used in confirm messages */
  bool mtu_present;
  uint16_t mtu;
  bool qos_present;
  FLOW_SPEC qos;
  bool flush_to_present;
  uint16_t flush_to;
  bool fcr_present;
  tL2CAP_FCR_OPTS fcr;
  bool fcs_present; /* Optionally bypasses FCS checks */
  uint8_t fcs;      /* '0' if desire is to bypass FCS, otherwise '1' */
  bool ext_flow_spec_present;
  tHCI_EXT_FLOW_SPEC ext_flow_spec;
  uint16_t flags; /* bit 0: 0-no continuation, 1-continuation */
} tL2CAP_CFG_INFO;

/* LE credit based L2CAP connection parameters */
constexpr uint16_t L2CAP_LE_MIN_MTU = 23;  // Minimum SDU size
constexpr uint16_t L2CAP_LE_MIN_MPS = 23;
constexpr uint16_t L2CAP_LE_MAX_MPS = 65533;
constexpr uint16_t L2CAP_LE_CREDIT_MAX = 65535;

// This is initial amout of credits we send, and amount to which we increase
// credits once they fall below threshold
uint16_t L2CA_LeCreditDefault();

// If credit count on remote fall below this value, we send back credits to
// reach default value.
uint16_t L2CA_LeCreditThreshold();

// Max number of CIDs in the L2CAP CREDIT BASED CONNECTION REQUEST
constexpr uint16_t L2CAP_CREDIT_BASED_MAX_CIDS = 5;

/* Define a structure to hold the configuration parameter for LE L2CAP
 * connection oriented channels.
 */
struct tL2CAP_LE_CFG_INFO {
  uint16_t result; /* Only used in confirm messages */
  uint16_t mtu = 100;
  uint16_t mps = 100;
  uint16_t credits = L2CA_LeCreditDefault();
  uint8_t number_of_channels = L2CAP_CREDIT_BASED_MAX_CIDS;
};

/*********************************
 *  Callback Functions Prototypes
 *********************************/

/* Connection indication callback prototype. Parameters are
 *              BD Address of remote
 *              Local CID assigned to the connection
 *              PSM that the remote wants to connect to
 *              Identifier that the remote sent
 */
typedef void(tL2CA_CONNECT_IND_CB)(const RawAddress&, uint16_t, uint16_t,
                                   uint8_t);

/* Connection confirmation callback prototype. Parameters are
 *              Local CID
 *              Result - 0 = connected
 *              If there is an error, tL2CA_ERROR_CB is invoked
 */
typedef void(tL2CA_CONNECT_CFM_CB)(uint16_t, uint16_t);

/* Configuration indication callback prototype. Parameters are
 *              Local CID assigned to the connection
 *              Pointer to configuration info
 */
typedef void(tL2CA_CONFIG_IND_CB)(uint16_t, tL2CAP_CFG_INFO*);

constexpr uint16_t L2CAP_INITIATOR_LOCAL = 1;
constexpr uint16_t L2CAP_INITIATOR_REMOTE = 0;
/* Configuration confirm callback prototype. Parameters are
 *              Local CID assigned to the connection
 *              Initiator (1 for local, 0 for remote)
 *              Initial config from remote
 * If there is an error, tL2CA_ERROR_CB is invoked
 */
typedef void(tL2CA_CONFIG_CFM_CB)(uint16_t, uint16_t, tL2CAP_CFG_INFO*);

/* Disconnect indication callback prototype. Parameters are
 *              Local CID
 *              Boolean whether upper layer should ack this
 */
typedef void(tL2CA_DISCONNECT_IND_CB)(uint16_t, bool);

/* Disconnect confirm callback prototype. Parameters are
 *              Local CID
 *              Result
 */
typedef void(tL2CA_DISCONNECT_CFM_CB)(uint16_t, uint16_t);

/* Disconnect confirm callback prototype. Parameters are
 *              Local CID
 *              Result
 */
typedef void(tL2CA_DATA_IND_CB)(uint16_t, BT_HDR*);

/* Congestion status callback protype. This callback is optional. If
 * an application tries to send data when the transmit queue is full,
 * the data will anyways be dropped. The parameter is:
 *              Local CID
 *              true if congested, false if uncongested
 */
typedef void(tL2CA_CONGESTION_STATUS_CB)(uint16_t, bool);

/* Transmit complete callback protype. This callback is optional. If
 * set, L2CAP will call it when packets are sent or flushed. If the
 * count is 0xFFFF, it means all packets are sent for that CID (eRTM
 * mode only). The parameters are:
 *              Local CID
 *              Number of SDUs sent or dropped
 */
typedef void(tL2CA_TX_COMPLETE_CB)(uint16_t, uint16_t);

/*
 * Notify the user when the remote send error result on ConnectRsp or ConfigRsp
 * The parameters are:
 *              Local CID
 *              Error type (L2CAP_CONN_OTHER_ERROR for ConnectRsp,
 *                          L2CAP_CFG_FAILED_NO_REASON for ConfigRsp)
 */
typedef void(tL2CA_ERROR_CB)(uint16_t, uint16_t);

/* Create credit based connection request callback prototype. Parameters are
 *              BD Address of remote
 *              Vector of allocated local cids to accept
 *              PSM
 *              Peer MTU
 *              Identifier that the remote sent
 */
typedef void(tL2CA_CREDIT_BASED_CONNECT_IND_CB)(const RawAddress& bdaddr,
                                                std::vector<uint16_t>& lcids,
                                                uint16_t psm, uint16_t peer_mtu,
                                                uint8_t identifier);

/* Collision Indication callback prototype. Used to notify upper layer that
 * remote devices sent Credit Based Connection Request but it was rejected due
 * to ongoing local request. Upper layer might want to sent another request when
 * local request is completed. Parameters are:
 *              BD Address of remote
 */
typedef void(tL2CA_CREDIT_BASED_COLLISION_IND_CB)(const RawAddress& bdaddr);

/* Credit based connection confirmation callback prototype. Parameters are
 *              BD Address of remote
 *              Connected Local CIDs
 *              Peer MTU
 *              Result - 0 = connected, non-zero means CID is not connected
 */
typedef void(tL2CA_CREDIT_BASED_CONNECT_CFM_CB)(const RawAddress& bdaddr,
                                                uint16_t lcid,
                                                uint16_t peer_mtu,
                                                uint16_t result);

/* Credit based reconfiguration confirm callback prototype. Parameters are
 *              BD Address of remote
 *              Local CID assigned to the connection
 *              Flag indicating if this is local or peer configuration
 *              Pointer to configuration info
 */
typedef void(tL2CA_CREDIT_BASED_RECONFIG_COMPLETED_CB)(
    const RawAddress& bdaddr, uint16_t lcid, bool is_local_cfg,
    tL2CAP_LE_CFG_INFO* p_cfg);

/* Define the structure that applications use to register with
 * L2CAP. This structure includes callback functions. All functions
 * MUST be provided, with the exception of the "connect pending"
 * callback and "congestion status" callback.
 */
typedef struct {
  tL2CA_CONNECT_IND_CB* pL2CA_ConnectInd_Cb;
  tL2CA_CONNECT_CFM_CB* pL2CA_ConnectCfm_Cb;
  tL2CA_CONFIG_IND_CB* pL2CA_ConfigInd_Cb;
  tL2CA_CONFIG_CFM_CB* pL2CA_ConfigCfm_Cb;
  tL2CA_DISCONNECT_IND_CB* pL2CA_DisconnectInd_Cb;
  tL2CA_DISCONNECT_CFM_CB* pL2CA_DisconnectCfm_Cb;
  tL2CA_DATA_IND_CB* pL2CA_DataInd_Cb;
  tL2CA_CONGESTION_STATUS_CB* pL2CA_CongestionStatus_Cb;
  tL2CA_TX_COMPLETE_CB* pL2CA_TxComplete_Cb;
  tL2CA_ERROR_CB* pL2CA_Error_Cb;
  tL2CA_CREDIT_BASED_CONNECT_IND_CB* pL2CA_CreditBasedConnectInd_Cb;
  tL2CA_CREDIT_BASED_CONNECT_CFM_CB* pL2CA_CreditBasedConnectCfm_Cb;
  tL2CA_CREDIT_BASED_RECONFIG_COMPLETED_CB*
      pL2CA_CreditBasedReconfigCompleted_Cb;
  tL2CA_CREDIT_BASED_COLLISION_IND_CB* pL2CA_CreditBasedCollisionInd_Cb;
} tL2CAP_APPL_INFO;

/* Define the structure that applications use to create or accept
 * connections with enhanced retransmission mode.
 */
typedef struct {
  uint8_t preferred_mode;
} tL2CAP_ERTM_INFO;

/**
 * Stack management declarations
 */
void l2c_init();
void l2c_free();

/*****************************************************************************
 *  External Function Declarations
 ****************************************************************************/

// Also does security for you
uint16_t L2CA_Register2(uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info,
                        bool enable_snoop, tL2CAP_ERTM_INFO* p_ertm_info,
                        uint16_t my_mtu, uint16_t required_remote_mtu,
                        uint16_t sec_level);

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

uint16_t L2CA_ConnectReq2(uint16_t psm, const RawAddress& p_bd_addr,
                          uint16_t sec_level);
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
                              tL2CAP_LE_CFG_INFO* p_cfg, uint16_t sec_level);

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
 *  Function         L2CA_GetPeerLECocCredit
 *
 *  Description      Get peers current credit for LE Connection Oriented
 *                   Channel.
 *
 *  Return value:    Number of the peer current credit
 *
 ******************************************************************************/
uint16_t L2CA_GetPeerLECocCredit(const RawAddress& bd_addr, uint16_t lcid);

/*******************************************************************************
 *
 *  Function         L2CA_ReconfigCreditBasedConnsReq
 *
 *  Description      Start reconfigure procedure on Connection Oriented Channel.
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

std::vector<uint16_t> L2CA_ConnectCreditBasedReq(uint16_t psm,
                                                 const RawAddress& p_bd_addr,
                                                 tL2CAP_LE_CFG_INFO* p_cfg);

/*******************************************************************************
 *
 *  Function         L2CA_ConnectCreditBasedRsp
 *
 *  Description      Response for the pL2CA_CreditBasedConnectInd_Cb which is the
 *                   indication for peer requesting credit based connection.
 *
 *  Return value: true if peer is connected
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
 * Function         L2CA_UseLatencyMode
 *
 * Description      Sets use latency mode for an ACL channel.
 *
 * Returns          true if a valid channel, else false
 *
 ******************************************************************************/
bool L2CA_UseLatencyMode(const RawAddress& bd_addr, bool use_latency_mode);

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
 * Function         L2CA_SetAclLatency
 *
 * Description      Sets the transmission latency for a channel.
 *
 * Returns          true if a valid channel, else false
 *
 ******************************************************************************/
bool L2CA_SetAclLatency(const RawAddress& bd_addr, tL2CAP_LATENCY latency);

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
 *                      Fixed Channel callback prototypes
 *
 ******************************************************************************/

/* Fixed channel connected and disconnected. Parameters are
 *      channel
 *      BD Address of remote
 *      true if channel is connected, false if disconnected
 *      Reason for connection failure
 *      transport : physical transport, BR/EDR or LE
 */
typedef void(tL2CA_FIXED_CHNL_CB)(uint16_t, const RawAddress&, bool, uint16_t,
                                  tBT_TRANSPORT);

/* Signalling data received. Parameters are
 *      channel
 *      BD Address of remote
 *      Pointer to buffer with data
 */
typedef void(tL2CA_FIXED_DATA_CB)(uint16_t, const RawAddress&, BT_HDR*);

/* Congestion status callback protype. This callback is optional. If
 * an application tries to send data when the transmit queue is full,
 * the data will anyways be dropped. The parameter is:
 *      remote BD_ADDR
 *      true if congested, false if uncongested
 */
typedef void(tL2CA_FIXED_CONGESTION_STATUS_CB)(const RawAddress&, bool);

/* Fixed channel registration info (the callback addresses and channel config)
 */
typedef struct {
  tL2CA_FIXED_CHNL_CB* pL2CA_FixedConn_Cb;
  tL2CA_FIXED_DATA_CB* pL2CA_FixedData_Cb;
  tL2CA_FIXED_CONGESTION_STATUS_CB* pL2CA_FixedCong_Cb;

  uint16_t default_idle_tout;
  tL2CA_TX_COMPLETE_CB*
      pL2CA_FixedTxComplete_Cb; /* fixed channel tx complete callback */
} tL2CAP_FIXED_CHNL_REG;

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
void L2CA_Consolidate(const RawAddress& identity_addr, const RawAddress& rpa);
tHCI_ROLE L2CA_GetBleConnRole(const RawAddress& bd_addr);

void L2CA_AdjustConnectionIntervals(uint16_t* min_interval,
                                    uint16_t* max_interval,
                                    uint16_t floor_interval);

/**
 * Check whether an ACL or LE link to the remote device is established
 */
bool L2CA_IsLinkEstablished(const RawAddress& bd_addr, tBT_TRANSPORT transport);

/*******************************************************************************
 *
 *  Function        L2CA_SetDefaultSubrate
 *
 *  Description     BLE Set Default Subrate.
 *
 *  Parameters:     Subrate parameters
 *
 *  Return value:   void
 *
 ******************************************************************************/
void L2CA_SetDefaultSubrate(uint16_t subrate_min, uint16_t subrate_max,
                            uint16_t max_latency, uint16_t cont_num,
                            uint16_t timeout);

/*******************************************************************************
 *
 *  Function        L2CA_SubrateRequest
 *
 *  Description     BLE Subrate request.
 *
 *  Parameters:     Subrate parameters
 *
 *  Return value:   true if update started
 *
 ******************************************************************************/
bool L2CA_SubrateRequest(const RawAddress& rem_bda, uint16_t subrate_min,
                         uint16_t subrate_max, uint16_t max_latency,
                         uint16_t cont_num, uint16_t timeout);

/*******************************************************************************
**
** Function         L2CA_SetMediaStreamChannel
**
** Description      This function is called to set/reset the ccb of active media
**                      streaming channel
**
**  Parameters:     local_media_cid: The local cid provided to A2DP to be used
**                      for streaming
**                  status: The status of media streaming on this channel
**
** Returns          void
**
*******************************************************************************/
void L2CA_SetMediaStreamChannel(uint16_t local_media_cid, bool status);

/*******************************************************************************
**
** Function         L2CA_isMediaChannel
**
** Description      This function returns if the channel id passed as parameter
**                      is an A2DP streaming channel
**
**  Parameters:     handle: Connection handle with the remote device
**                  channel_id: Channel ID
**                  is_local_cid: Signifies if the channel id passed is local
**                      cid or remote cid (true if local, remote otherwise)
**
** Returns          bool
**
*******************************************************************************/
bool L2CA_isMediaChannel(uint16_t handle, uint16_t channel_id,
                         bool is_local_cid);

namespace fmt {
template <>
struct formatter<tL2CAP_LATENCY> : enum_formatter<tL2CAP_LATENCY> {};
template <>
struct formatter<tL2CAP_PRIORITY> : enum_formatter<tL2CAP_PRIORITY> {};
}  // namespace fmt

#endif /* L2C_API_H */
