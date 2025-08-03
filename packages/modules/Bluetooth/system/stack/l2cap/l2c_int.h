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
 *  This file contains L2CAP internal definitions
 *
 ******************************************************************************/
#ifndef L2C_INT_H
#define L2C_INT_H

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>
#include <stdbool.h>

#include <string>

#include "internal_include/bt_target.h"
#include "l2c_api.h"
#include "l2cdefs.h"
#include "macros.h"
#include "osi/include/alarm.h"
#include "osi/include/fixed_queue.h"
#include "osi/include/list.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/hci_error_code.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

#define L2CAP_MIN_MTU 48 /* Minimum acceptable MTU is 48 bytes */

#define MAX_ACTIVE_AVDT_CONN 2

constexpr uint16_t L2CAP_CREDIT_BASED_MIN_MTU = 64;
constexpr uint16_t L2CAP_CREDIT_BASED_MIN_MPS = 64;

/*
 * Timeout values (in milliseconds).
 */
#define L2CAP_LINK_ROLE_SWITCH_TIMEOUT_MS (10 * 1000)  /* 10 seconds */
#define L2CAP_LINK_CONNECT_TIMEOUT_MS (60 * 1000)      /* 30 seconds */
#define L2CAP_LINK_CONNECT_EXT_TIMEOUT_MS (120 * 1000) /* 120 seconds */
#define L2CAP_LINK_FLOW_CONTROL_TIMEOUT_MS (2 * 1000)  /* 2 seconds */
#define L2CAP_LINK_DISCONNECT_TIMEOUT_MS (30 * 1000)   /* 30 seconds */
#define L2CAP_CHNL_CONNECT_TIMEOUT_MS (60 * 1000)      /* 60 seconds */
#define L2CAP_CHNL_CONNECT_EXT_TIMEOUT_MS (120 * 1000) /* 120 seconds */
#define L2CAP_CHNL_CFG_TIMEOUT_MS (30 * 1000)          /* 30 seconds */
#define L2CAP_CHNL_DISCONNECT_TIMEOUT_MS (10 * 1000)   /* 10 seconds */
#define L2CAP_DELAY_CHECK_SM4_TIMEOUT_MS (2 * 1000)    /* 2 seconds */
#define L2CAP_WAIT_INFO_RSP_TIMEOUT_MS (3 * 1000)      /* 3 seconds */
#define L2CAP_BLE_LINK_CONNECT_TIMEOUT_MS (30 * 1000)  /* 30 seconds */
#define L2CAP_FCR_ACK_TIMEOUT_MS 200                   /* 200 milliseconds */

/* Define the possible L2CAP channel states. The names of
 * the states may seem a bit strange, but they are taken from
 * the Bluetooth specification.
*/
typedef enum {
  CST_CLOSED,                  /* Channel is in closed state */
  CST_ORIG_W4_SEC_COMP,        /* Originator waits security clearence */
  CST_TERM_W4_SEC_COMP,        /* Acceptor waits security clearence */
  CST_W4_L2CAP_CONNECT_RSP,    /* Waiting for peer conenct response */
  CST_W4_L2CA_CONNECT_RSP,     /* Waiting for upper layer connect rsp */
  CST_CONFIG,                  /* Negotiating configuration */
  CST_OPEN,                    /* Data transfer state */
  CST_W4_L2CAP_DISCONNECT_RSP, /* Waiting for peer disconnect rsp */
  CST_W4_L2CA_DISCONNECT_RSP   /* Waiting for upper layer disc rsp */
} tL2C_CHNL_STATE;

inline std::string channel_state_text(const tL2C_CHNL_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(CST_CLOSED);
    CASE_RETURN_TEXT(CST_ORIG_W4_SEC_COMP);
    CASE_RETURN_TEXT(CST_TERM_W4_SEC_COMP);
    CASE_RETURN_TEXT(CST_W4_L2CAP_CONNECT_RSP);
    CASE_RETURN_TEXT(CST_W4_L2CA_CONNECT_RSP);
    CASE_RETURN_TEXT(CST_CONFIG);
    CASE_RETURN_TEXT(CST_OPEN);
    CASE_RETURN_TEXT(CST_W4_L2CAP_DISCONNECT_RSP);
    CASE_RETURN_TEXT(CST_W4_L2CA_DISCONNECT_RSP);
    default:
      return base::StringPrintf("UNKNOWN[%d]", state);
  }
}

/* Define the possible L2CAP link states
*/
typedef enum {
  LST_DISCONNECTED,
  LST_CONNECT_HOLDING,
  LST_CONNECTING_WAIT_SWITCH,
  LST_CONNECTING,
  LST_CONNECTED,
  LST_DISCONNECTING
} tL2C_LINK_STATE;

inline std::string link_state_text(const tL2C_LINK_STATE& state) {
  switch (state) {
    case LST_DISCONNECTED:
      return std::string("LST_DISCONNECTED");
    case LST_CONNECT_HOLDING:
      return std::string("LST_CONNECT_HOLDING");
    case LST_CONNECTING_WAIT_SWITCH:
      return std::string("LST_CONNECTING_WAIT_SWITCH");
    case LST_CONNECTING:
      return std::string("LST_CONNECTING");
    case LST_CONNECTED:
      return std::string("LST_CONNECTED");
    case LST_DISCONNECTING:
      return std::string("LST_DISCONNECTING");
    default:
      return std::string("UNKNOWN");
  }
}

/* Define input events to the L2CAP link and channel state machines. The names
 * of the events may seem a bit strange, but they are taken from
 * the Bluetooth specification.
*/
typedef enum : uint16_t {
  /* Lower layer */
  L2CEVT_LP_CONNECT_CFM = 0,     /* connect confirm */
  L2CEVT_LP_CONNECT_CFM_NEG = 1, /* connect confirm (failed) */
  L2CEVT_LP_CONNECT_IND = 2,     /* connect indication */
  L2CEVT_LP_DISCONNECT_IND = 3,  /* disconnect indication */

  /* Security */
  L2CEVT_SEC_COMP = 7,     /* cleared successfully */
  L2CEVT_SEC_COMP_NEG = 8, /* procedure failed */

  /* Peer connection */
  L2CEVT_L2CAP_CONNECT_REQ = 10,     /* request */
  L2CEVT_L2CAP_CONNECT_RSP = 11,     /* response */
  L2CEVT_L2CAP_CONNECT_RSP_PND = 12, /* response pending */
  L2CEVT_L2CAP_CONNECT_RSP_NEG = 13, /* response (failed) */

  /* Peer configuration */
  L2CEVT_L2CAP_CONFIG_REQ = 14,     /* request */
  L2CEVT_L2CAP_CONFIG_RSP = 15,     /* response */
  L2CEVT_L2CAP_CONFIG_RSP_NEG = 16, /* response (failed) */

  L2CEVT_L2CAP_DISCONNECT_REQ = 17, /* Peer disconnect request */
  L2CEVT_L2CAP_DISCONNECT_RSP = 18, /* Peer disconnect response */
  L2CEVT_L2CAP_INFO_RSP = 19,       /* Peer information response */
  L2CEVT_L2CAP_DATA = 20,           /* Peer data */

  /* Upper layer */
  L2CEVT_L2CA_CONNECT_REQ = 21,     /* connect request */
  L2CEVT_L2CA_CONNECT_RSP = 22,     /* connect response */
  L2CEVT_L2CA_CONNECT_RSP_NEG = 23, /* connect response (failed)*/
  L2CEVT_L2CA_CONFIG_REQ = 24,      /* config request */
  L2CEVT_L2CA_CONFIG_RSP = 25,      /* config response */
  L2CEVT_L2CA_DISCONNECT_REQ = 27,  /* disconnect request */
  L2CEVT_L2CA_DISCONNECT_RSP = 28,  /* disconnect response */
  L2CEVT_L2CA_DATA_READ = 29,       /* data read */
  L2CEVT_L2CA_DATA_WRITE = 30,      /* data write */

  L2CEVT_TIMEOUT = 32,         /* Timeout */
  L2CEVT_SEC_RE_SEND_CMD = 33, /* btm_sec has enough info to proceed */

  L2CEVT_ACK_TIMEOUT = 34, /* RR delay timeout */

  L2CEVT_L2CA_SEND_FLOW_CONTROL_CREDIT = 35, /* Upper layer credit packet \
                                              */
  /* Peer credit based connection */
  L2CEVT_L2CAP_RECV_FLOW_CONTROL_CREDIT = 36, /* credit packet */
  L2CEVT_L2CAP_CREDIT_BASED_CONNECT_REQ =
      37, /* credit based connection request */
  L2CEVT_L2CAP_CREDIT_BASED_CONNECT_RSP =
      38, /* accepted credit based connection */
  L2CEVT_L2CAP_CREDIT_BASED_CONNECT_RSP_NEG =
      39, /* rejected credit based connection */
  L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_REQ =
      40, /* credit based reconfig request*/
  L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_RSP =
      41, /* credit based reconfig response */

  /* Upper layer credit based connection */
  L2CEVT_L2CA_CREDIT_BASED_CONNECT_REQ = 42,     /* connect request */
  L2CEVT_L2CA_CREDIT_BASED_CONNECT_RSP = 43,     /* connect response */
  L2CEVT_L2CA_CREDIT_BASED_CONNECT_RSP_NEG = 44, /* connect response (failed)*/
  L2CEVT_L2CA_CREDIT_BASED_RECONFIG_REQ = 45,    /* reconfig request */
} tL2CEVT;

/* Constants for LE Dynamic PSM values */
#define LE_DYNAMIC_PSM_START 0x0080
#define LE_DYNAMIC_PSM_END 0x00FF
#define LE_DYNAMIC_PSM_RANGE (LE_DYNAMIC_PSM_END - LE_DYNAMIC_PSM_START + 1)

/* Return values for l2cu_process_peer_cfg_req() */
#define L2CAP_PEER_CFG_UNACCEPTABLE 0
#define L2CAP_PEER_CFG_OK 1
#define L2CAP_PEER_CFG_DISCONNECT 2

/* eL2CAP option constants */
/* Min retransmission timeout if no flush timeout or PBF */
#define L2CAP_MIN_RETRANS_TOUT 2000
/* Min monitor timeout if no flush timeout or PBF */
#define L2CAP_MIN_MONITOR_TOUT 12000

#define L2CAP_MAX_FCR_CFG_TRIES 2 /* Config attempts before disconnecting */

typedef uint8_t tL2C_BLE_FIXED_CHNLS_MASK;

typedef struct {
  uint8_t next_tx_seq;       /* Next sequence number to be Tx'ed */
  uint8_t last_rx_ack;       /* Last sequence number ack'ed by the peer */
  uint8_t next_seq_expected; /* Next peer sequence number expected */
  uint8_t last_ack_sent;     /* Last peer sequence number ack'ed */
  uint8_t num_tries;         /* Number of retries to send a packet */
  uint8_t max_held_acks;     /* Max acks we can hold before sending */

  bool remote_busy; /* true if peer has flowed us off */

  bool rej_sent;       /* Reject was sent */
  bool srej_sent;      /* Selective Reject was sent */
  bool wait_ack;       /* Transmitter is waiting ack (poll sent) */
  bool rej_after_srej; /* Send a REJ when SREJ clears */

  bool send_f_rsp; /* We need to send an F-bit response */

  uint16_t rx_sdu_len; /* Length of the SDU being received */
  BT_HDR* p_rx_sdu;    /* Buffer holding the SDU being received */
  fixed_queue_t*
      waiting_for_ack_q;          /* Buffers sent and waiting for peer to ack */
  fixed_queue_t* srej_rcv_hold_q; /* Buffers rcvd but held pending SREJ rsp */
  fixed_queue_t* retrans_q;       /* Buffers being retransmitted */

  alarm_t* ack_timer;         /* Timer delaying RR */
  alarm_t* mon_retrans_timer; /* Timer Monitor or Retransmission */

} tL2C_FCRB;

typedef struct {
  bool in_use;
  bool log_packets;
  uint16_t psm;
  uint16_t real_psm; /* This may be a dummy RCB for an o/b connection but */
                     /* this is the real PSM that we need to connect to */
  tL2CAP_APPL_INFO api;
  tL2CAP_ERTM_INFO ertm_info;
  tL2CAP_LE_CFG_INFO coc_cfg;
  uint16_t my_mtu;
  uint16_t required_remote_mtu;
} tL2C_RCB;

#ifndef L2CAP_CBB_DEFAULT_DATA_RATE_BUFF_QUOTA
#define L2CAP_CBB_DEFAULT_DATA_RATE_BUFF_QUOTA 100
#endif

typedef struct {
  uint16_t psm;
  tBT_TRANSPORT transport;
  bool is_originator;
  tBTM_SEC_CALLBACK* p_callback;
  void* p_ref_data;
} tL2CAP_SEC_DATA;

/* Define a channel control block (CCB). There may be many channel control
 * blocks between the same two Bluetooth devices (i.e. on the same link).
 * Each CCB has unique local and remote CIDs. All channel control blocks on
 * the same physical link and are chained together.
*/
typedef struct t_l2c_ccb {
  bool in_use;                /* true when in use, false when not */
  tL2C_CHNL_STATE chnl_state; /* Channel state */
  tL2CAP_LE_CFG_INFO
      local_conn_cfg; /* Our config for ble conn oriented channel */
  tL2CAP_LE_CFG_INFO
      peer_conn_cfg;       /* Peer device config ble conn oriented channel */
  bool is_first_seg;       /* Dtermine whether the received packet is the first
                              segment or not */
  BT_HDR* ble_sdu;         /* Buffer for storing unassembled sdu*/
  uint16_t ble_sdu_length; /* Length of unassembled sdu length*/
  struct t_l2c_ccb* p_next_ccb; /* Next CCB in the chain */
  struct t_l2c_ccb* p_prev_ccb; /* Previous CCB in the chain */
  struct t_l2c_linkcb* p_lcb;   /* Link this CCB is assigned to */

  uint16_t local_cid;  /* Local CID */
  uint16_t remote_cid; /* Remote CID */

  alarm_t* l2c_ccb_timer; /* CCB Timer Entry */

  tL2C_RCB* p_rcb;      /* Registration CB for this Channel */

#define IB_CFG_DONE 0x01
#define OB_CFG_DONE 0x02
#define RECONFIG_FLAG 0x04 /* True after initial configuration */

  uint8_t config_done; /* Configuration flag word */
  uint16_t remote_config_rsp_result; /* The config rsp result from remote */
  uint8_t local_id;    /* Transaction ID for local trans */
  uint8_t remote_id;   /* Transaction ID for local */

#define CCB_FLAG_NO_RETRY 0x01     /* no more retry */
#define CCB_FLAG_SENT_PENDING 0x02 /* already sent pending response */
  uint8_t flags;

  bool connection_initiator; /* true if we sent ConnectReq */

  tL2CAP_CFG_INFO our_cfg;          /* Our saved configuration options */
  tL2CAP_CFG_INFO peer_cfg;         /* Peer's saved configuration options */

  fixed_queue_t* xmit_hold_q; /* Transmit data hold queue */
  bool cong_sent;             /* Set when congested status sent */
  uint16_t buff_quota;        /* Buffer quota before sending congestion */

  tL2CAP_CHNL_PRIORITY ccb_priority;  /* Channel priority */
  tL2CAP_CHNL_DATA_RATE tx_data_rate; /* Channel Tx data rate */
  tL2CAP_CHNL_DATA_RATE rx_data_rate; /* Channel Rx data rate */

  /* Fields used for eL2CAP */
  tL2CAP_ERTM_INFO ertm_info;
  tL2C_FCRB fcrb;
  uint16_t tx_mps; /* TX MPS adjusted based on current controller */
  uint16_t max_rx_mtu;
  uint8_t fcr_cfg_tries;          /* Max number of negotiation attempts */
  bool peer_cfg_already_rejected; /* If mode rejected once, set to true */
  bool out_cfg_fcr_present; /* true if cfg response should include fcr options
                             */

  bool is_flushable; /* true if channel is flushable */

  uint16_t fixed_chnl_idle_tout; /* Idle timeout to use for the fixed channel */
  uint16_t tx_data_len;

  /* Number of LE frames that the remote can send to us (credit count in
   * remote). Valid only for LE CoC */
  uint16_t remote_credit_count;

  /* used to indicate that ECOC is used */
  bool ecoc{false};
  bool reconfig_started;

  struct {
    struct {
      unsigned bytes{0};
      unsigned packets{0};
      void operator()(unsigned bytes) {
        this->bytes += bytes;
        this->packets++;
      }
    } rx, tx;
    struct {
      struct {
        unsigned bytes{0};
        unsigned packets{0};
        void operator()(unsigned bytes) {
          this->bytes += bytes;
          this->packets++;
        }
      } rx, tx;
    } dropped;
  } metrics;

} tL2C_CCB;

/***********************************************************************
 * Define a queue of linked CCBs.
*/
typedef struct {
  tL2C_CCB* p_first_ccb; /* The first channel in this queue */
  tL2C_CCB* p_last_ccb;  /* The last  channel in this queue */
} tL2C_CCB_Q;

/* Round-Robin service for the same priority channels */
#define L2CAP_NUM_CHNL_PRIORITY \
  3 /* Total number of priority group (high, medium, low)*/
#define L2CAP_CHNL_PRIORITY_WEIGHT \
  5 /* weight per priority for burst transmission quota */
#define L2CAP_GET_PRIORITY_QUOTA(pri) \
  ((L2CAP_NUM_CHNL_PRIORITY - (pri)) * L2CAP_CHNL_PRIORITY_WEIGHT)

/* CCBs within the same LCB are served in round robin with priority It will make
 * sure that low priority channel (for example, HF signaling on RFCOMM) can be
 * sent to the headset even if higher priority channel (for example, AV media
 * channel) is congested.
 */

typedef struct {
  tL2C_CCB* p_serve_ccb; /* current serving ccb within priority group */
  tL2C_CCB* p_first_ccb; /* first ccb of priority group */
  uint8_t num_ccb;       /* number of channels in priority group */
  uint8_t quota;         /* burst transmission quota */
} tL2C_RR_SERV;

typedef enum : uint8_t {
  /* disable update connection parameters */
  L2C_BLE_CONN_UPDATE_DISABLE = (1u << 0),
  /* new connection parameter to be set */
  L2C_BLE_NEW_CONN_PARAM = (1u << 1),
  /* waiting for connection update finished */
  L2C_BLE_UPDATE_PENDING = (1u << 2),
  /* not using default connection parameters */
  L2C_BLE_NOT_DEFAULT_PARAM = (1u << 3),
} tCONN_UPDATE_MASK;

/* Define a link control block. There is one link control block between
 * this device and any other device (i.e. BD ADDR).
*/
typedef struct t_l2c_linkcb {
  bool in_use; /* true when in use, false when not */
  tL2C_LINK_STATE link_state;

  alarm_t* l2c_lcb_timer; /* Timer entry for timeout evt */

  //  This tracks if the link has ever either (a)
  //  been used for a dynamic channel (EATT or L2CAP CoC), or (b) has been a
  //  GATT client. If false, the local device is just a GATT server, so for
  //  backwards compatibility we never do a link timeout.
  bool with_active_local_clients{false};

 private:
  uint16_t handle_; /* The handle used with LM */
  friend void l2cu_set_lcb_handle(struct t_l2c_linkcb& p_lcb, uint16_t handle);
  void SetHandle(uint16_t handle) { handle_ = handle; }

 public:
  uint16_t Handle() const { return handle_; }
  void InvalidateHandle() { handle_ = HCI_INVALID_HANDLE; }

  tL2C_CCB_Q ccb_queue; /* Queue of CCBs on this LCB */

  tL2C_CCB* p_pending_ccb;  /* ccb of waiting channel during link disconnect */
  alarm_t* info_resp_timer; /* Timer entry for info resp timeout evt */
  RawAddress remote_bd_addr; /* The BD address of the remote */

 private:
  tHCI_ROLE link_role_{HCI_ROLE_CENTRAL}; /* Central or peripheral */
 public:
  tHCI_ROLE LinkRole() const { return link_role_; }
  bool IsLinkRoleCentral() const { return link_role_ == HCI_ROLE_CENTRAL; }
  bool IsLinkRolePeripheral() const {
    return link_role_ == HCI_ROLE_PERIPHERAL;
  }
  void SetLinkRoleAsCentral() { link_role_ = HCI_ROLE_CENTRAL; }
  void SetLinkRoleAsPeripheral() { link_role_ = HCI_ROLE_PERIPHERAL; }

  uint8_t signal_id;                /* Signalling channel id */
  uint8_t cur_echo_id;              /* Current id value for echo request */
  uint16_t idle_timeout;            /* Idle timeout */
 private:
  bool is_bonding_{false};          /* True - link active only for bonding */
 public:
  bool IsBonding() const { return is_bonding_; }
  void SetBonding() { is_bonding_ = true; }
  void ResetBonding() { is_bonding_ = false; }

  uint16_t link_xmit_quota; /* Num outstanding pkts allowed */
  bool is_round_robin_scheduling() const { return link_xmit_quota == 0; }

  uint16_t sent_not_acked;  /* Num packets sent but not acked */
  void update_outstanding_packets(uint16_t packets_acked) {
    if (sent_not_acked > packets_acked)
      sent_not_acked -= packets_acked;
    else
      sent_not_acked = 0;
  }

  bool w4_info_rsp;                /* true when info request is active */
  uint32_t peer_ext_fea;           /* Peer's extended features mask */
  list_t* link_xmit_data_q;        /* Link transmit data buffer queue */

  uint8_t peer_chnl_mask[L2CAP_FIXED_CHNL_ARRAY_SIZE];

  tL2CAP_PRIORITY acl_priority;
  bool is_normal_priority() const {
    return acl_priority == L2CAP_PRIORITY_NORMAL;
  }
  bool is_high_priority() const { return acl_priority == L2CAP_PRIORITY_HIGH; }
  bool set_priority(tL2CAP_PRIORITY priority) {
    if (acl_priority != priority) {
      acl_priority = priority;
      return true;
    }
    return false;
  }

  bool use_latency_mode = false;
  tL2CAP_LATENCY preset_acl_latency = L2CAP_LATENCY_NORMAL;
  tL2CAP_LATENCY acl_latency = L2CAP_LATENCY_NORMAL;
  bool is_normal_latency() const { return acl_latency == L2CAP_LATENCY_NORMAL; }
  bool is_low_latency() const { return acl_latency == L2CAP_LATENCY_LOW; }
  bool set_latency(tL2CAP_LATENCY latency) {
    if (acl_latency != latency) {
      acl_latency = latency;
      return true;
    }
    return false;
  }

  tL2C_CCB* p_fixed_ccbs[L2CAP_NUM_FIXED_CHNLS];

 private:
  tHCI_REASON disc_reason_{HCI_ERR_UNDEFINED};

 public:
  tHCI_REASON DisconnectReason() const { return disc_reason_; }
  void SetDisconnectReason(tHCI_REASON disc_reason) {
    disc_reason_ = disc_reason;
  }

  tBT_TRANSPORT transport;
  bool is_transport_br_edr() const { return transport == BT_TRANSPORT_BR_EDR; }
  bool is_transport_ble() const { return transport == BT_TRANSPORT_LE; }

  uint16_t tx_data_len; /* tx data length used in data length extension */
  fixed_queue_t* le_sec_pending_q; /* LE coc channels waiting for security check
                                      completion */
  uint8_t sec_act;

  uint8_t conn_update_mask;

  bool conn_update_blocked_by_service_discovery;
  bool conn_update_blocked_by_profile_connection;

  uint16_t min_interval; /* parameters as requested by peripheral */
  uint16_t max_interval;
  uint16_t latency;
  uint16_t timeout;
  uint16_t min_ce_len;
  uint16_t max_ce_len;

#define L2C_BLE_SUBRATE_REQ_DISABLE 0x1  // disable subrate req
#define L2C_BLE_NEW_SUBRATE_PARAM 0x2    // new subrate req parameter to be set
#define L2C_BLE_SUBRATE_REQ_PENDING 0x4  // waiting for subrate to be completed

  /* subrate req params */
  uint16_t subrate_min;
  uint16_t subrate_max;
  uint16_t max_latency;
  uint16_t cont_num;
  uint16_t supervision_tout;

  uint8_t subrate_req_mask;

  /* each priority group is limited burst transmission */
  /* round robin service for the same priority channels */
  tL2C_RR_SERV rr_serv[L2CAP_NUM_CHNL_PRIORITY];
  uint8_t rr_pri; /* current serving priority group */

  /* Pending ECOC reconfiguration data */
  tL2CAP_LE_CFG_INFO pending_ecoc_reconfig_cfg;
  uint8_t pending_ecoc_reconfig_cnt;

  /* This is to keep list of local cids use in the
   * credit based connection response.
   */
  uint16_t pending_ecoc_connection_cids[L2CAP_CREDIT_BASED_MAX_CIDS];
  uint8_t pending_ecoc_conn_cnt;

  uint16_t pending_lead_cid;
  uint16_t pending_l2cap_result;

  unsigned number_of_active_dynamic_channels() const {
    unsigned cnt = 0;
    const tL2C_CCB* cur = ccb_queue.p_first_ccb;
    while (cur != nullptr) {
      cnt++;
      cur = cur->p_next_ccb;
    }
    return cnt;
  }
} tL2C_LCB;

/* Define the L2CAP control structure
*/
typedef struct {
  uint16_t controller_xmit_window; /* Total ACL window for all links */

  uint16_t round_robin_quota;   /* Round-robin link quota */
  uint16_t round_robin_unacked; /* Round-robin unacked */
  bool is_classic_round_robin_quota_available() const {
    return round_robin_unacked < round_robin_quota;
  }
  void update_outstanding_classic_packets(uint16_t num_packets_acked) {
    if (round_robin_unacked > num_packets_acked)
      round_robin_unacked -= num_packets_acked;
    else
      round_robin_unacked = 0;
  }

  bool check_round_robin;       /* Do a round robin check */

  bool is_cong_cback_context;

  tL2C_LCB lcb_pool[MAX_L2CAP_LINKS];    /* Link Control Block pool */
  tL2C_CCB ccb_pool[MAX_L2CAP_CHANNELS]; /* Channel Control Block pool */
  tL2C_RCB rcb_pool[MAX_L2CAP_CLIENTS];  /* Registration info pool */

  tL2C_CCB* p_free_ccb_first; /* Pointer to first free CCB */
  tL2C_CCB* p_free_ccb_last;  /* Pointer to last  free CCB */

  bool disallow_switch;     /* false, to allow switch at create conn */
  uint16_t num_lm_acl_bufs; /* # of ACL buffers on controller */
  uint16_t idle_timeout;    /* Idle timeout */

  tL2C_LCB* p_cur_hcit_lcb;  /* Current HCI Transport buffer */
  uint16_t num_used_lcbs;    /* Number of active link control blocks */

  uint16_t non_flushable_pbf; /* L2CAP_PKT_START_NON_FLUSHABLE if controller
                                 supports */
  /* Otherwise, L2CAP_PKT_START */

#if (L2CAP_CONFORMANCE_TESTING == TRUE)
  uint32_t test_info_resp; /* Conformance testing needs a dynamic response */
#endif

  tL2CAP_FIXED_CHNL_REG
      fixed_reg[L2CAP_NUM_FIXED_CHNLS]; /* Reg info for fixed channels */

  uint16_t num_ble_links_active; /* Number of LE links active */
  uint16_t controller_le_xmit_window; /* Total ACL window for all links */
  tL2C_BLE_FIXED_CHNLS_MASK l2c_ble_fixed_chnls_mask;  // LE fixed channels mask
  uint16_t num_lm_ble_bufs;         /* # of ACL buffers on controller */
  uint16_t ble_round_robin_quota;   /* Round-robin link quota */
  uint16_t ble_round_robin_unacked; /* Round-robin unacked */
  bool is_ble_round_robin_quota_available() const {
    return ble_round_robin_unacked < ble_round_robin_quota;
  }
  void update_outstanding_le_packets(uint16_t num_packets_acked) {
    if (ble_round_robin_unacked > num_packets_acked)
      ble_round_robin_unacked -= num_packets_acked;
    else
      ble_round_robin_unacked = 0;
  }

  bool ble_check_round_robin;       /* Do a round robin check */
  tL2C_RCB ble_rcb_pool[BLE_MAX_L2CAP_CLIENTS]; /* Registration info pool */

  uint16_t le_dyn_psm; /* Next LE dynamic PSM value to try to assign */
  bool le_dyn_psm_assigned[LE_DYNAMIC_PSM_RANGE]; /* Table of assigned LE PSM */

} tL2C_CB;

/* Define a structure that contains the information about a connection.
 * This structure is used to pass between functions, and not all the
 * fields will always be filled in.
*/
typedef struct {
  RawAddress bd_addr;    /* Remote BD address */
  uint8_t status;        /* Connection status */
  uint16_t psm;          /* PSM of the connection */
  uint16_t l2cap_result; /* L2CAP result */
  uint16_t l2cap_status; /* L2CAP status */
  uint16_t remote_cid;   /* Remote CID */
  std::vector<uint16_t> lcids; /* Used when credit based is used*/
  uint16_t peer_mtu;     /* Peer MTU */
} tL2C_CONN_INFO;

typedef struct {
  bool is_active;     /* is channel active */
  uint16_t local_cid; /* Remote CID */
  tL2C_CCB* p_ccb;    /* CCB */
} tL2C_AVDT_CHANNEL_INFO;

typedef void(tL2C_FCR_MGMT_EVT_HDLR)(uint8_t, tL2C_CCB*);

/* Necessary info for postponed TX completion callback
 */
typedef struct {
  uint16_t local_cid;
  uint16_t num_sdu;
  tL2CA_TX_COMPLETE_CB* cb;
} tL2C_TX_COMPLETE_CB_INFO;

/* The offset in a buffer that L2CAP will use when building commands.
*/
#define L2CAP_SEND_CMD_OFFSET 0

/* Number of ACL buffers to use for high priority channel
*/
#define L2CAP_HIGH_PRI_MIN_XMIT_QUOTA_A (L2CAP_HIGH_PRI_MIN_XMIT_QUOTA)

/* L2CAP global data
 ***********************************
*/
extern tL2C_CB l2cb;

/* Functions provided by l2c_main.cc
 ***********************************
*/

void l2c_receive_hold_timer_timeout(void* data);
void l2c_ccb_timer_timeout(void* data);
void l2c_lcb_timer_timeout(void* data);
void l2c_fcrb_ack_timer_timeout(void* data);
uint8_t l2c_data_write(uint16_t cid, BT_HDR* p_data, uint16_t flag);

tL2C_LCB* l2cu_allocate_lcb(const RawAddress& p_bd_addr, bool is_bonding,
                            tBT_TRANSPORT transport);
void l2cu_release_lcb(tL2C_LCB* p_lcb);
tL2C_LCB* l2cu_find_lcb_by_bd_addr(const RawAddress& p_bd_addr,
                                   tBT_TRANSPORT transport);
tL2C_LCB* l2cu_find_lcb_by_handle(uint16_t handle);

bool l2cu_set_acl_priority(const RawAddress& bd_addr, tL2CAP_PRIORITY priority,
                           bool reset_after_rs);
bool l2cu_set_acl_latency(const RawAddress& bd_addr, tL2CAP_LATENCY latency);

void l2cu_enqueue_ccb(tL2C_CCB* p_ccb);
void l2cu_dequeue_ccb(tL2C_CCB* p_ccb);
void l2cu_change_pri_ccb(tL2C_CCB* p_ccb, tL2CAP_CHNL_PRIORITY priority);

tL2C_CCB* l2cu_allocate_ccb(tL2C_LCB* p_lcb, uint16_t cid,
                            bool is_eatt = false);
void l2cu_release_ccb(tL2C_CCB* p_ccb);
tL2C_CCB* l2cu_find_ccb_by_cid(tL2C_LCB* p_lcb, uint16_t local_cid);
tL2C_CCB* l2cu_find_ccb_by_remote_cid(tL2C_LCB* p_lcb, uint16_t remote_cid);
bool l2c_is_cmd_rejected(uint8_t cmd_code, uint8_t id, tL2C_LCB* p_lcb);

void l2cu_send_peer_cmd_reject(tL2C_LCB* p_lcb, uint16_t reason, uint8_t rem_id,
                               uint16_t p1, uint16_t p2);
void l2cu_send_peer_connect_req(tL2C_CCB* p_ccb);
void l2cu_send_peer_connect_rsp(tL2C_CCB* p_ccb, uint16_t result,
                                uint16_t status);
void l2cu_send_peer_config_req(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
void l2cu_send_peer_config_rsp(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
void l2cu_send_peer_config_rej(tL2C_CCB* p_ccb, uint8_t* p_data,
                               uint16_t data_len, uint16_t rej_len);
void l2cu_send_peer_disc_req(tL2C_CCB* p_ccb);
void l2cu_send_peer_disc_rsp(tL2C_LCB* p_lcb, uint8_t remote_id,
                             uint16_t local_cid, uint16_t remote_cid);
void l2cu_send_peer_echo_rsp(tL2C_LCB* p_lcb, uint8_t id, uint8_t* p_data,
                             uint16_t data_len);
void l2cu_send_peer_info_rsp(tL2C_LCB* p_lcb, uint8_t id, uint16_t info_type);
void l2cu_reject_connection(tL2C_LCB* p_lcb, uint16_t remote_cid,
                            uint8_t rem_id, uint16_t result);
void l2cu_send_peer_info_req(tL2C_LCB* p_lcb, uint16_t info_type);
void l2cu_set_acl_hci_header(BT_HDR* p_buf, tL2C_CCB* p_ccb);
void l2cu_check_channel_congestion(tL2C_CCB* p_ccb);
void l2cu_disconnect_chnl(tL2C_CCB* p_ccb);

void l2cu_tx_complete(tL2C_TX_COMPLETE_CB_INFO* p_cbi);

void l2cu_send_peer_ble_par_req(tL2C_LCB* p_lcb, uint16_t min_int,
                                uint16_t max_int, uint16_t latency,
                                uint16_t timeout);
void l2cu_send_peer_ble_par_rsp(tL2C_LCB* p_lcb, uint16_t reason,
                                uint8_t rem_id);
void l2cu_reject_ble_connection(tL2C_CCB* p_ccb, uint8_t rem_id,
                                uint16_t result);
void l2cu_reject_credit_based_conn_req(tL2C_LCB* p_lcb, uint8_t rem_id,
                                       uint8_t num_of_channels,
                                       uint16_t result);
void l2cu_reject_ble_coc_connection(tL2C_LCB* p_lcb, uint8_t rem_id,
                                    uint16_t result);
void l2cu_send_peer_ble_credit_based_conn_res(tL2C_CCB* p_ccb, uint16_t result);
void l2cu_send_peer_credit_based_conn_res(tL2C_CCB* p_ccb,
                                          std::vector<uint16_t>& accepted_lcids,
                                          uint16_t result);

void l2cu_send_peer_ble_credit_based_conn_req(tL2C_CCB* p_ccb);
void l2cu_send_peer_credit_based_conn_req(tL2C_CCB* p_ccb);

void l2cu_send_ble_reconfig_rsp(tL2C_LCB* p_lcb, uint8_t rem_id,
                                uint16_t result);
void l2cu_send_credit_based_reconfig_req(tL2C_CCB* p_ccb,
                                         tL2CAP_LE_CFG_INFO* p_data);

void l2cu_send_peer_ble_flow_control_credit(tL2C_CCB* p_ccb,
                                            uint16_t credit_value);
void l2cu_send_peer_ble_credit_based_disconn_req(tL2C_CCB* p_ccb);

bool l2cu_initialize_fixed_ccb(tL2C_LCB* p_lcb, uint16_t fixed_cid);
void l2cu_no_dynamic_ccbs(tL2C_LCB* p_lcb);
void l2cu_process_fixed_chnl_resp(tL2C_LCB* p_lcb);
bool l2cu_is_ccb_active(tL2C_CCB* p_ccb);
uint16_t le_result_to_l2c_conn(uint16_t result);

/* Functions provided for Broadcom Aware
 ***************************************
*/

tL2C_RCB* l2cu_allocate_rcb(uint16_t psm);
tL2C_RCB* l2cu_find_rcb_by_psm(uint16_t psm);
void l2cu_release_rcb(tL2C_RCB* p_rcb);
void l2cu_release_ble_rcb(tL2C_RCB* p_rcb);
tL2C_RCB* l2cu_allocate_ble_rcb(uint16_t psm);
tL2C_RCB* l2cu_find_ble_rcb_by_psm(uint16_t psm);

uint8_t l2cu_process_peer_cfg_req(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
void l2cu_process_peer_cfg_rsp(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
void l2cu_process_our_cfg_req(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
void l2cu_process_our_cfg_rsp(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);

tL2C_LCB* l2cu_find_lcb_by_state(tL2C_LINK_STATE state);
bool l2cu_lcb_disconnecting(void);

void l2cu_create_conn_br_edr(tL2C_LCB* p_lcb);
bool l2cu_create_conn_le(tL2C_LCB* p_lcb);
void l2cu_create_conn_after_switch(tL2C_LCB* p_lcb);
void l2cu_adjust_out_mps(tL2C_CCB* p_ccb);

/* Functions provided by l2c_link.cc
 ***********************************
*/
void l2c_link_timeout(tL2C_LCB* p_lcb);
void l2c_info_resp_timer_timeout(void* data);
void l2c_link_check_send_pkts(tL2C_LCB* p_lcb, uint16_t local_cid,
                              BT_HDR* p_buf);
void l2c_link_adjust_allocation(void);

void l2c_link_sec_comp(const RawAddress* p_bda, tBT_TRANSPORT trasnport,
                       void* p_ref_data, tBTM_STATUS status);
void l2c_link_adjust_chnl_allocation(void);

#if (L2CAP_CONFORMANCE_TESTING == TRUE)
/* Used only for conformance testing */
void l2cu_set_info_rsp_mask(uint32_t mask);
#endif

/* Functions provided by l2c_csm.cc
 ***********************************
*/
void l2c_csm_execute(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data);

void l2c_enqueue_peer_data(tL2C_CCB* p_ccb, BT_HDR* p_buf);

/* Functions provided by l2c_fcr.cc
 ***********************************
*/
void l2c_fcr_cleanup(tL2C_CCB* p_ccb);
void l2c_fcr_proc_pdu(tL2C_CCB* p_ccb, BT_HDR* p_buf);
void l2c_fcr_proc_tout(tL2C_CCB* p_ccb);
void l2c_fcr_proc_ack_tout(tL2C_CCB* p_ccb);
void l2c_fcr_send_S_frame(tL2C_CCB* p_ccb, uint16_t function_code,
                          uint16_t pf_bit);
BT_HDR* l2c_fcr_clone_buf(BT_HDR* p_buf, uint16_t new_offset,
                          uint16_t no_of_bytes);
bool l2c_fcr_is_flow_controlled(tL2C_CCB* p_ccb);
BT_HDR* l2c_fcr_get_next_xmit_sdu_seg(tL2C_CCB* p_ccb,
                                      uint16_t max_packet_length);
void l2c_fcr_start_timer(tL2C_CCB* p_ccb);
void l2c_lcc_proc_pdu(tL2C_CCB* p_ccb, BT_HDR* p_buf);
BT_HDR* l2c_lcc_get_next_xmit_sdu_seg(tL2C_CCB* p_ccb, bool* last_piece_of_sdu);

/* Configuration negotiation */
uint8_t l2c_fcr_chk_chan_modes(tL2C_CCB* p_ccb);

void l2c_fcr_adj_our_rsp_options(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_peer_cfg);
bool l2c_fcr_renegotiate_chan(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
uint8_t l2c_fcr_process_peer_cfg_req(tL2C_CCB* p_ccb, tL2CAP_CFG_INFO* p_cfg);
void l2c_fcr_adj_monitor_retran_timeout(tL2C_CCB* p_ccb);
void l2c_fcr_stop_timer(tL2C_CCB* p_ccb);

/* Functions provided by l2c_ble.cc
 ***********************************
*/
bool l2cble_create_conn(tL2C_LCB* p_lcb);
void l2cble_process_sig_cmd(tL2C_LCB* p_lcb, uint8_t* p, uint16_t pkt_len);
void l2c_ble_link_adjust_allocation(void);

void l2cble_credit_based_conn_req(tL2C_CCB* p_ccb);
void l2cble_credit_based_conn_res(tL2C_CCB* p_ccb, uint16_t result);
void l2cble_send_peer_disc_req(tL2C_CCB* p_ccb);
void l2cble_send_flow_control_credit(tL2C_CCB* p_ccb, uint16_t credit_value);
tL2CAP_LE_RESULT_CODE l2ble_sec_access_req(const RawAddress& bd_addr,
                                           uint16_t psm, bool is_originator,
                                           tBTM_SEC_CALLBACK* p_callback,
                                           void* p_ref_data);

void l2cble_update_data_length(tL2C_LCB* p_lcb);

void l2cu_process_fixed_disc_cback(tL2C_LCB* p_lcb);

void l2cble_process_subrate_change_evt(uint16_t handle, uint8_t status,
                                       uint16_t subrate_factor,
                                       uint16_t peripheral_latency,
                                       uint16_t cont_num, uint16_t timeout);

namespace fmt {
template <>
struct formatter<tL2C_LINK_STATE> : enum_formatter<tL2C_LINK_STATE> {};
template <>
struct formatter<tL2CEVT> : enum_formatter<tL2CEVT> {};
template <>
struct formatter<tL2C_CHNL_STATE> : enum_formatter<tL2C_CHNL_STATE> {};
}  // namespace fmt

#endif
