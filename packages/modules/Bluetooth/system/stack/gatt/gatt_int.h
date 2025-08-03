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

#ifndef GATT_INT_H
#define GATT_INT_H

#include <base/functional/bind.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <deque>
#include <list>
#include <unordered_set>
#include <vector>

#include "common/init_flags.h"
#include "gatt_api.h"
#include "internal_include/bt_target.h"
#include "macros.h"
#include "osi/include/fixed_queue.h"
#include "stack/include/bt_hdr.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

#define GATT_CREATE_CONN_ID(tcb_idx, gatt_if) \
  ((uint16_t)((((uint8_t)(tcb_idx)) << 8) | ((uint8_t)(gatt_if))))
#define GATT_GET_TCB_IDX(conn_id) ((uint8_t)(((uint16_t)(conn_id)) >> 8))
#define GATT_GET_GATT_IF(conn_id) ((tGATT_IF)((uint8_t)(conn_id)))

#define GATT_TRANS_ID_MAX 0x0fffffff /* 4 MSB is reserved */

/* security action for GATT write and read request */
typedef enum : uint8_t {
  GATT_SEC_NONE = 0,
  GATT_SEC_OK = 1,
  GATT_SEC_SIGN_DATA = 2,       /* compute the signature for the write cmd */
  GATT_SEC_ENCRYPT = 3,         /* encrypt the link with current key */
  GATT_SEC_ENCRYPT_NO_MITM = 4, /* unauthenticated encryption or better */
  GATT_SEC_ENCRYPT_MITM = 5,    /* authenticated encryption */
  GATT_SEC_ENC_PENDING = 6,     /* wait for link encryption pending */
} tGATT_SEC_ACTION;

inline std::string gatt_security_action_text(const tGATT_SEC_ACTION& action) {
  switch (action) {
    CASE_RETURN_TEXT(GATT_SEC_NONE);
    CASE_RETURN_TEXT(GATT_SEC_OK);
    CASE_RETURN_TEXT(GATT_SEC_SIGN_DATA);
    CASE_RETURN_TEXT(GATT_SEC_ENCRYPT);
    CASE_RETURN_TEXT(GATT_SEC_ENCRYPT_NO_MITM);
    CASE_RETURN_TEXT(GATT_SEC_ENCRYPT_MITM);
    CASE_RETURN_TEXT(GATT_SEC_ENC_PENDING);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", action);
  }
}

#define GATT_INDEX_INVALID 0xff

#define GATT_WRITE_CMD_MASK 0xc0 /*0x1100-0000*/
#define GATT_AUTH_SIGN_MASK 0x80 /*0x1000-0000*/
#define GATT_AUTH_SIGN_LEN 12

#define GATT_HDR_SIZE 3 /* 1B opcode + 2B handle */

/* wait for ATT cmd response timeout value */
#define GATT_WAIT_FOR_RSP_TIMEOUT_MS (30 * 1000)
#define GATT_WAIT_FOR_DISC_RSP_TIMEOUT_MS (5 * 1000)
#define GATT_REQ_RETRY_LIMIT 2

typedef struct {
  bool is_link_key_known;
  bool is_link_key_authed;
  bool is_encrypted;
  // whether we connected to the peer, or if it
  // connected to a discoverable advertisement (affects
  // GAP permissions)
  bool can_read_discoverable_characteristics;
} tGATT_SEC_FLAG;

/* Find Information Response Type
*/
#define GATT_INFO_TYPE_PAIR_16 0x01
#define GATT_INFO_TYPE_PAIR_128 0x02

constexpr bool kGattConnected = true;
constexpr bool kGattDisconnected = !kGattConnected;

/*  GATT client FIND_TYPE_VALUE_Request data */
typedef struct {
  bluetooth::Uuid uuid; /* type of attribute to be found */
  uint16_t s_handle;  /* starting handle */
  uint16_t e_handle;  /* ending handle */
  uint16_t value_len; /* length of the attribute value */
  uint8_t
      value[GATT_MAX_MTU_SIZE]; /* pointer to the attribute value to be found */
} tGATT_FIND_TYPE_VALUE;

/* client request message to ATT protocol
*/
typedef union {
  tGATT_READ_BY_TYPE browse;             /* read by type request */
  tGATT_FIND_TYPE_VALUE find_type_value; /* find by type value */
  tGATT_READ_MULTI read_multi;           /* read multiple request */
  tGATT_READ_PARTIAL read_blob;          /* read blob */
  tGATT_VALUE attr_value;                /* write request */
                                         /* prepare write */
  /* write blob */
  uint16_t handle; /* read,  handle value confirmation */
  uint16_t mtu;
  tGATT_EXEC_FLAG exec_write; /* execute write */
} tGATT_CL_MSG;

/* error response strucutre */
typedef struct {
  uint16_t handle;
  uint8_t cmd_code;
  uint8_t reason;
} tGATT_ERROR;

/* server response message to ATT protocol
*/
typedef union {
  /* data type            member          event   */
  tGATT_VALUE attr_value; /* READ, HANDLE_VALUE_IND, PREPARE_WRITE */
                          /* READ_BLOB, READ_BY_TYPE */
  tGATT_ERROR error;      /* ERROR_RSP */
  uint16_t handle;        /* WRITE, WRITE_BLOB */
  uint16_t mtu;           /* exchange MTU request */
} tGATT_SR_MSG;

/* Characteristic declaration attribute value
*/
typedef struct {
  tGATT_CHAR_PROP property;
  uint16_t char_val_handle;
} tGATT_CHAR_DECL;

/* attribute value maintained in the server database
*/
typedef union {
  bluetooth::Uuid uuid;        /* service declaration */
  tGATT_CHAR_DECL char_decl;   /* characteristic declaration */
  tGATT_INCL_SRVC incl_handle; /* included service */
  uint16_t char_ext_prop;      /* Characteristic Extended Properties */
} tGATT_ATTR_VALUE;

/* Attribute UUID type
*/
#define GATT_ATTR_UUID_TYPE_16 0
#define GATT_ATTR_UUID_TYPE_128 1
#define GATT_ATTR_UUID_TYPE_32 2
typedef uint8_t tGATT_ATTR_UUID_TYPE;

/* 16 bits UUID Attribute in server database
*/
typedef struct {
  std::unique_ptr<tGATT_ATTR_VALUE> p_value;
  tGATT_PERM permission;
  uint16_t handle;
  bluetooth::Uuid uuid;
  bt_gatt_db_attribute_type_t gatt_type;
} tGATT_ATTR;

/* Service Database definition
*/
typedef struct {
  std::vector<tGATT_ATTR> attr_list; /* pointer to the attributes */
  uint16_t end_handle;       /* Last handle number           */
  uint16_t next_handle;      /* Next usable handle value     */
} tGATT_SVC_DB;

/* Data Structure used for GATT server */
/* An GATT registration record consists of a handle, and 1 or more attributes */
/* A service registration information record consists of beginning and ending */
/* attribute handle, service UUID and a set of GATT server callback.          */

typedef struct {
  bluetooth::Uuid app_uuid128;
  tGATT_CBACK app_cb{};
  tGATT_IF gatt_if{0}; /* one based */
  bool in_use{false};
  uint8_t listening{0}; /* if adv for all has been enabled */
  bool eatt_support{false};
  std::string name;
} tGATT_REG;

struct tGATT_CLCB;

/* command queue for each connection */
typedef struct {
  BT_HDR* p_cmd;
  tGATT_CLCB* p_clcb;
  uint8_t op_code;
  bool to_send;
  uint16_t cid;
} tGATT_CMD_Q;

#if GATT_MAX_SR_PROFILES <= 8
typedef uint8_t tGATT_APP_MASK;
#elif GATT_MAX_SR_PROFILES <= 16
typedef uint16_t tGATT_APP_MASK;
#elif GATT_MAX_SR_PROFILES <= 32
typedef uint32_t tGATT_APP_MASK;
#endif

/* command details for each connection */
typedef struct {
  BT_HDR* p_rsp_msg;
  uint32_t trans_id;
  tGATT_READ_MULTI multi_req;
  fixed_queue_t* multi_rsp_q;
  uint16_t handle;
  uint8_t op_code;
  uint8_t status;
  uint8_t cback_cnt[GATT_MAX_APPS];
  uint16_t cid;
} tGATT_SR_CMD;

typedef enum : uint8_t {
  GATT_CH_CLOSE = 0,
  GATT_CH_CLOSING = 1,
  GATT_CH_CONN = 2,
  GATT_CH_CFG = 3,
  GATT_CH_OPEN = 4,
} tGATT_CH_STATE;

inline std::string gatt_channel_state_text(const tGATT_CH_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(GATT_CH_CLOSE);
    CASE_RETURN_TEXT(GATT_CH_CLOSING);
    CASE_RETURN_TEXT(GATT_CH_CONN);
    CASE_RETURN_TEXT(GATT_CH_CFG);
    CASE_RETURN_TEXT(GATT_CH_OPEN);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", state);
  }
}

// If you change these values make sure to look at b/262219144 before.
// Some platform rely on this to never changes
#define GATT_GATT_START_HANDLE 1
#define GATT_GAP_START_HANDLE 20
#define GATT_GMCS_START_HANDLE 40
#define GATT_GTBS_START_HANDLE 90
#define GATT_TMAS_START_HANDLE 130
#define GATT_APP_START_HANDLE 134

typedef struct hdl_cfg {
  uint16_t gatt_start_hdl;
  uint16_t gap_start_hdl;
  uint16_t gmcs_start_hdl;
  uint16_t gtbs_start_hdl;
  uint16_t tmas_start_hdl;
  uint16_t app_start_hdl;
} tGATT_HDL_CFG;

typedef struct hdl_list_elem {
  tGATTS_HNDL_RANGE asgn_range; /* assigned handle range */
  tGATT_SVC_DB svc_db;
} tGATT_HDL_LIST_ELEM;

/* Data Structure used for GATT server                                        */
/* A GATT registration record consists of a handle, and 1 or more attributes  */
/* A service registration information record consists of beginning and ending */
/* attribute handle, service UUID and a set of GATT server callback.          */
typedef struct {
  tGATT_SVC_DB* p_db;  /* pointer to the service database */
  bluetooth::Uuid app_uuid; /* application UUID */
  uint32_t sdp_handle; /* primamry service SDP handle */
  uint16_t type;       /* service type UUID, primary or secondary */
  uint16_t s_hdl;      /* service starting handle */
  uint16_t e_hdl;      /* service ending handle */
  tGATT_IF gatt_if;    /* this service is belong to which application */
  bool is_primary;
} tGATT_SRV_LIST_ELEM;

typedef struct {
  std::deque<tGATT_CLCB*> pending_enc_clcb; /* pending encryption channel q */
  tGATT_SEC_ACTION sec_act;
  RawAddress peer_bda;
  tBT_TRANSPORT transport;
  uint32_t trans_id;

  /* Indicates number of available eatt channels */
  uint8_t eatt;

  uint16_t att_lcid; /* L2CAP channel ID for ATT */
  uint16_t payload_size;

  tGATT_CH_STATE ch_state;

  std::unordered_set<tGATT_IF> app_hold_link;

  /* server needs */
  /* server response data */
  tGATT_SR_CMD sr_cmd;
  uint16_t indicate_handle;
  fixed_queue_t* pending_ind_q;

  alarm_t* conf_timer; /* peer confirm to indication timer */

  uint8_t prep_cnt[GATT_MAX_APPS];
  uint8_t ind_count;

  std::deque<tGATT_CMD_Q> cl_cmd_q;
  alarm_t* ind_ack_timer; /* local app confirm to indication timer */

  // TODO(hylo): support byte array data
  /* Client supported feature*/
  uint8_t cl_supp_feat;
  /* Server supported features */
  uint8_t sr_supp_feat;
  /* Use for server. if false, should handle database out of sync. */
  bool is_robust_cache_change_aware;

  /* SIRK read related data */
  tGATT_STATUS gatt_status;
  uint8_t sirk_type;
  Octet16 sirk;

  bool in_use;
  uint8_t tcb_idx;

  /* ATT Exchange MTU data */
  uint16_t pending_user_mtu_exchange_value;
  std::list<uint16_t> conn_ids_waiting_for_mtu_exchange;
  /* Used to set proper TX DATA LEN on the controller*/
  uint16_t max_user_mtu;

} tGATT_TCB;

/* logic channel */
typedef struct {
  uint16_t
      next_disc_start_hdl; /* starting handle for the next inc srvv discovery */
  tGATT_DISC_RES result;
  bool wait_for_read_rsp;
} tGATT_READ_INC_UUID128;
struct tGATT_CLCB {
  tGATT_TCB* p_tcb; /* associated TCB of this CLCB */
  tGATT_REG* p_reg; /* owner of this CLCB */
  uint8_t sccb_idx;
  uint8_t* p_attr_buf; /* attribute buffer for read multiple, prepare write */
  bluetooth::Uuid uuid;
  uint16_t conn_id; /* connection handle */
  uint16_t s_handle; /* starting handle of the active request */
  uint16_t e_handle; /* ending handle of the active request */
  uint16_t counter; /* used as offset, attribute length, num of prepare write */
  uint16_t start_offset;
  tGATT_AUTH_REQ auth_req; /* authentication requirement */
  tGATTC_OPTYPE operation; /* one logic channel can have one operation active */
  uint8_t op_subtype;      /* operation subtype */
  tGATT_STATUS status;     /* operation status */
  bool first_read_blob_after_read;
  tGATT_READ_INC_UUID128 read_uuid128;
  alarm_t* gatt_rsp_timer_ent; /* peer response timer */
  uint8_t retry_count;
  uint16_t read_req_current_mtu; /* This is the MTU value that the read was
                                    initiated with */
  uint16_t cid;
};

typedef struct {
  uint16_t handle;
  uint16_t uuid;
  uint32_t service_change;
} tGATT_SVC_CHG;

#define GATT_SVC_CHANGED_CONNECTING 1     /* wait for connection */
#define GATT_SVC_CHANGED_SERVICE 2        /* GATT service discovery */
#define GATT_SVC_CHANGED_CHARACTERISTIC 3 /* service change char discovery */
#define GATT_SVC_CHANGED_DESCRIPTOR 4     /* service change CCC discoery */
#define GATT_SVC_CHANGED_CONFIGURE_CCCD 5 /* config CCC */

typedef struct {
  uint16_t conn_id;
  bool in_use;
  bool connected;
  RawAddress bda;
  tBT_TRANSPORT transport;

  /* GATT service change CCC related variables */
  uint8_t ccc_stage;
  uint8_t ccc_result;
  uint16_t s_handle;
  uint16_t e_handle;
} tGATT_PROFILE_CLCB;

typedef struct {
  tGATT_TCB tcb[GATT_MAX_PHY_CHANNEL];
  fixed_queue_t* sign_op_queue;

  uint16_t next_handle;     /* next available handle */
  uint16_t last_service_handle; /* handle of last service */
  tGATT_SVC_CHG gattp_attr; /* GATT profile attribute service change */
  tGATT_IF gatt_if;
  std::list<tGATT_HDL_LIST_ELEM>* hdl_list_info;
  std::list<tGATT_SRV_LIST_ELEM>* srv_list_info;

  fixed_queue_t* srv_chg_clt_q; /* service change clients queue */
  tGATT_REG cl_rcb[GATT_MAX_APPS];

  /* list of connection link control blocks.
   * Since clcbs are also keep in the channels (ATT and EATT) queues while
   * processing, we want to make sure that references to elements are not
   * invalidated when elements are added or removed from the list. This is why
   * std::list is used.
   */
  std::list<tGATT_CLCB> clcb_queue;

#if (GATT_CONFORMANCE_TESTING == TRUE)
  bool enable_err_rsp;
  uint8_t req_op_code;
  uint8_t err_status;
  uint16_t handle;
#endif

  tGATT_PROFILE_CLCB profile_clcb[GATT_MAX_APPS];
  uint16_t
      handle_of_h_r; /* Handle of the handles reused characteristic value */
  uint16_t handle_cl_supported_feat;
  uint16_t handle_sr_supported_feat;
  uint8_t
      gatt_svr_supported_feat_mask; /* Local supported features as a server */

  /* Supported features as a client. To be written to remote device.
   * Note this is NOT a value of the characteristic with handle
   * handle_cl_support_feat, as that one should be written by remote device.
   */
  uint8_t gatt_cl_supported_feat_mask;

  uint16_t handle_of_database_hash;
  Octet16 database_hash;

  tGATT_APPL_INFO cb_info;

  tGATT_HDL_CFG hdl_cfg;
  bool over_br_enabled;
} tGATT_CB;

#define GATT_SIZE_OF_SRV_CHG_HNDL_RANGE 4

/* Global GATT data */
extern tGATT_CB gatt_cb;

#if (GATT_CONFORMANCE_TESTING == TRUE)
void gatt_set_err_rsp(bool enable, uint8_t req_op_code, uint8_t err_status);
#endif

/* from gatt_main.cc */
bool gatt_disconnect(tGATT_TCB* p_tcb);
bool gatt_act_connect(tGATT_REG* p_reg, const RawAddress& bd_addr,
                      tBT_TRANSPORT transport, int8_t initiating_phys);
bool gatt_act_connect(tGATT_REG* p_reg, const RawAddress& bd_addr,
                      tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT transport,
                      int8_t initiating_phys);
bool gatt_connect(const RawAddress& rem_bda, tGATT_TCB* p_tcb,
                  tBT_TRANSPORT transport, uint8_t initiating_phys,
                  tGATT_IF gatt_if);
bool gatt_connect(const RawAddress& rem_bda, tGATT_TCB* p_tcb,
                  tBLE_ADDR_TYPE addr_type, tBT_TRANSPORT transport,
                  uint8_t initiating_phys, tGATT_IF gatt_if);
void gatt_data_process(tGATT_TCB& p_tcb, uint16_t cid, BT_HDR* p_buf);
void gatt_update_app_use_link_flag(tGATT_IF gatt_if, tGATT_TCB* p_tcb,
                                   bool is_add, bool check_acl_link);

void gatt_profile_db_init(void);
void gatt_set_ch_state(tGATT_TCB* p_tcb, tGATT_CH_STATE ch_state);
tGATT_CH_STATE gatt_get_ch_state(tGATT_TCB* p_tcb);
void gatt_init_srv_chg(void);
void gatt_proc_srv_chg(void);
void gatt_send_srv_chg_ind(const RawAddress& peer_bda);
void gatt_chk_srv_chg(tGATTS_SRV_CHG* p_srv_chg_clt);
void gatt_add_a_bonded_dev_for_srv_chg(const RawAddress& bda);

/* from gatt_attr.cc */
uint16_t gatt_profile_find_conn_id_by_bd_addr(const RawAddress& bda);

bool gatt_profile_get_eatt_support(const RawAddress& remote_bda);
void gatt_cl_init_sr_status(tGATT_TCB& tcb);
bool gatt_cl_read_sr_supp_feat_req(
    const RawAddress& peer_bda,
    base::OnceCallback<void(const RawAddress&, uint8_t)> cb);
bool gatt_cl_read_sirk_req(
    const RawAddress& peer_bda,
    base::OnceCallback<void(tGATT_STATUS status, const RawAddress&,
                            uint8_t sirk_type, Octet16& sirk)>
        cb);
bool gatt_sr_is_cl_multi_variable_len_notif_supported(tGATT_TCB& tcb);

bool gatt_sr_is_cl_change_aware(tGATT_TCB& tcb);
void gatt_sr_init_cl_status(tGATT_TCB& tcb);
void gatt_sr_update_cl_status(tGATT_TCB& tcb, bool chg_aware);

/* Functions provided by att_protocol.cc */
tGATT_STATUS attp_send_cl_confirmation_msg(tGATT_TCB& tcb, uint16_t cid);
tGATT_STATUS attp_send_cl_msg(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                              uint8_t op_code, tGATT_CL_MSG* p_msg);
BT_HDR* attp_build_sr_msg(tGATT_TCB& tcb, uint8_t op_code, tGATT_SR_MSG* p_msg,
                          uint16_t payload_size);
tGATT_STATUS attp_send_sr_msg(tGATT_TCB& tcb, uint16_t cid, BT_HDR* p_msg);
tGATT_STATUS attp_send_msg_to_l2cap(tGATT_TCB& tcb, uint16_t cid,
                                    BT_HDR* p_toL2CAP);

/* utility functions */
uint16_t gatt_get_local_mtu(void);
char const* gatt_dbg_op_name(uint8_t op_code);
uint32_t gatt_add_sdp_record(const bluetooth::Uuid& uuid, uint16_t start_hdl,
                             uint16_t end_hdl);
bool gatt_parse_uuid_from_cmd(bluetooth::Uuid* p_uuid, uint16_t len,
                              uint8_t** p_data);
uint8_t gatt_build_uuid_to_stream_len(const bluetooth::Uuid& uuid);
uint8_t gatt_build_uuid_to_stream(uint8_t** p_dst, const bluetooth::Uuid& uuid);
void gatt_sr_get_sec_info(const RawAddress& rem_bda, tBT_TRANSPORT transport,
                          tGATT_SEC_FLAG* p_sec_flag, uint8_t* p_key_size);
void gatt_start_rsp_timer(tGATT_CLCB* p_clcb);
void gatt_stop_rsp_timer(tGATT_CLCB* p_clcb);
void gatt_start_conf_timer(tGATT_TCB* p_tcb, uint16_t cid);
void gatt_stop_conf_timer(tGATT_TCB& tcb, uint16_t cid);
void gatt_rsp_timeout(void* data);
void gatt_indication_confirmation_timeout(void* data);
void gatt_ind_ack_timeout(void* data);
void gatt_start_ind_ack_timer(tGATT_TCB& tcb, uint16_t cid);
void gatt_stop_ind_ack_timer(tGATT_TCB* p_tcb, uint16_t cid);
tGATT_STATUS gatt_send_error_rsp(tGATT_TCB& tcb, uint16_t cid, uint8_t err_code,
                                 uint8_t op_code, uint16_t handle, bool deq);

bool gatt_is_srv_chg_ind_pending(tGATT_TCB* p_tcb);
tGATTS_SRV_CHG* gatt_is_bda_in_the_srv_chg_clt_list(const RawAddress& bda);

bool gatt_find_the_connected_bda(uint8_t start_idx, RawAddress& bda,
                                 uint8_t* p_found_idx,
                                 tBT_TRANSPORT* p_transport);
void gatt_set_srv_chg(void);
void gatt_delete_dev_from_srv_chg_clt_list(const RawAddress& bd_addr);
void gatt_add_pending_ind(tGATT_TCB* p_tcb, tGATT_VALUE* p_ind);
void gatt_free_srvc_db_buffer_app_id(const bluetooth::Uuid& app_id);
bool gatt_cl_send_next_cmd_inq(tGATT_TCB& tcb);

/* reserved handle list */
std::list<tGATT_HDL_LIST_ELEM>::iterator gatt_find_hdl_buffer_by_app_id(
    const bluetooth::Uuid& app_uuid128, bluetooth::Uuid* p_svc_uuid,
    uint16_t svc_inst);
tGATT_HDL_LIST_ELEM* gatt_find_hdl_buffer_by_handle(uint16_t handle);
tGATTS_SRV_CHG* gatt_add_srv_chg_clt(tGATTS_SRV_CHG* p_srv_chg);

/* for background connection */
bool gatt_auto_connect_dev_remove(tGATT_IF gatt_if, const RawAddress& bd_addr);

/* server function */
std::list<tGATT_SRV_LIST_ELEM>::iterator gatt_sr_find_i_rcb_by_handle(
    uint16_t handle);
tGATT_STATUS gatt_sr_process_app_rsp(tGATT_TCB& tcb, tGATT_IF gatt_if,
                                     uint32_t trans_id, uint8_t op_code,
                                     tGATT_STATUS status, tGATTS_RSP* p_msg,
                                     tGATT_SR_CMD* sr_res_p);
void gatt_server_handle_client_req(tGATT_TCB& p_tcb, uint16_t cid,
                                   uint8_t op_code, uint16_t len,
                                   uint8_t* p_data);
void gatt_sr_send_req_callback(uint16_t conn_id, uint32_t trans_id,
                               uint8_t op_code, tGATTS_DATA* p_req_data);
uint32_t gatt_sr_enqueue_cmd(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
                             uint16_t handle);
bool gatt_cancel_open(tGATT_IF gatt_if, const RawAddress& bda);
void gatt_notify_phy_updated(tHCI_STATUS status, uint16_t handle,
                             uint8_t tx_phy, uint8_t rx_phy);
void gatt_notify_subrate_change(uint16_t handle, uint16_t subrate_factor,
                                uint16_t latency, uint16_t cont_num,
                                uint16_t timeout, uint8_t status);
/*   */

bool gatt_tcb_is_cid_busy(tGATT_TCB& tcb, uint16_t cid);

tGATT_REG* gatt_get_regcb(tGATT_IF gatt_if);
bool gatt_is_clcb_allocated(uint16_t conn_id);
tGATT_CLCB* gatt_clcb_alloc(uint16_t conn_id);

bool gatt_tcb_get_cid_available_for_indication(tGATT_TCB* p_tcb,
                                               bool eatt_support,
                                               uint16_t** indicate_handle_p,
                                               uint16_t* cid_p);
bool gatt_tcb_find_indicate_handle(tGATT_TCB& tcb, uint16_t cid,
                                   uint16_t* indicated_handle_p);
uint16_t gatt_tcb_get_att_cid(tGATT_TCB& tcb, bool eatt_support);
uint16_t gatt_tcb_get_payload_size(tGATT_TCB& tcb, uint16_t cid);
void gatt_clcb_invalidate(tGATT_TCB* p_tcb, const tGATT_CLCB* p_clcb);
uint16_t gatt_get_mtu(const RawAddress& bda, tBT_TRANSPORT transport);
bool gatt_is_pending_mtu_exchange(tGATT_TCB* p_tcb);
void gatt_set_conn_id_waiting_for_mtu_exchange(tGATT_TCB* p_tcb,
                                               uint16_t conn_id);

void gatt_sr_copy_prep_cnt_to_cback_cnt(tGATT_TCB& p_tcb);
bool gatt_sr_is_cback_cnt_zero(tGATT_TCB& p_tcb);
bool gatt_sr_is_prep_cnt_zero(tGATT_TCB& p_tcb);
void gatt_sr_reset_cback_cnt(tGATT_TCB& p_tcb, uint16_t cid);
void gatt_sr_reset_prep_cnt(tGATT_TCB& tcb);
tGATT_SR_CMD* gatt_sr_get_cmd_by_trans_id(tGATT_TCB* p_tcb, uint32_t trans_id);
tGATT_SR_CMD* gatt_sr_get_cmd_by_cid(tGATT_TCB& tcb, uint16_t cid);
tGATT_READ_MULTI* gatt_sr_get_read_multi(tGATT_TCB& tcb, uint16_t cid);
void gatt_sr_update_cback_cnt(tGATT_TCB& p_tcb, uint16_t cid, tGATT_IF gatt_if,
                              bool is_inc, bool is_reset_first);
void gatt_sr_update_prep_cnt(tGATT_TCB& tcb, tGATT_IF gatt_if, bool is_inc,
                             bool is_reset_first);

uint8_t gatt_num_clcb_by_bd_addr(const RawAddress& bda);
tGATT_TCB* gatt_find_tcb_by_cid(uint16_t lcid);
tGATT_TCB* gatt_allocate_tcb_by_bdaddr(const RawAddress& bda,
                                       tBT_TRANSPORT transport);
tGATT_TCB* gatt_get_tcb_by_idx(uint8_t tcb_idx);
tGATT_TCB* gatt_find_tcb_by_addr(const RawAddress& bda,
                                 tBT_TRANSPORT transport);
bool gatt_send_ble_burst_data(const RawAddress& remote_bda, BT_HDR* p_buf);

/* GATT client functions */
void gatt_dequeue_sr_cmd(tGATT_TCB& tcb, uint16_t cid);
tGATT_STATUS gatt_send_write_msg(tGATT_TCB& p_tcb, tGATT_CLCB* p_clcb,
                                 uint8_t op_code, uint16_t handle, uint16_t len,
                                 uint16_t offset, uint8_t* p_data);
void gatt_cleanup_upon_disc(const RawAddress& bda, tGATT_DISCONN_REASON reason,
                            tBT_TRANSPORT transport);
void gatt_end_operation(tGATT_CLCB* p_clcb, tGATT_STATUS status, void* p_data);

void gatt_act_discovery(tGATT_CLCB* p_clcb);
void gatt_act_read(tGATT_CLCB* p_clcb, uint16_t offset);
void gatt_act_write(tGATT_CLCB* p_clcb, uint8_t sec_act);
tGATT_CLCB* gatt_cmd_dequeue(tGATT_TCB& tcb, uint16_t cid, uint8_t* p_opcode);
bool gatt_cmd_enq(tGATT_TCB& tcb, tGATT_CLCB* p_clcb, bool to_send,
                  uint8_t op_code, BT_HDR* p_buf);
void gatt_client_handle_server_rsp(tGATT_TCB& tcb, uint16_t cid,
                                   uint8_t op_code, uint16_t len,
                                   uint8_t* p_data);
void gatt_send_queue_write_cancel(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                                  tGATT_EXEC_FLAG flag);
bool gatt_is_outstanding_msg_in_att_send_queue(const tGATT_TCB& tcb);

/* gatt_auth.cc */
bool gatt_security_check_start(tGATT_CLCB* p_clcb);
void gatt_verify_signature(tGATT_TCB& tcb, uint16_t cid, BT_HDR* p_buf);
tGATT_STATUS gatt_get_link_encrypt_status(tGATT_TCB& tcb);
tGATT_SEC_ACTION gatt_get_sec_act(tGATT_TCB* p_tcb);
void gatt_set_sec_act(tGATT_TCB* p_tcb, tGATT_SEC_ACTION sec_act);

/* gatt_db.cc */
void gatts_init_service_db(tGATT_SVC_DB& db, const bluetooth::Uuid& service,
                           bool is_pri, uint16_t s_hdl, uint16_t num_handle);
uint16_t gatts_add_included_service(tGATT_SVC_DB& db, uint16_t s_handle,
                                    uint16_t e_handle,
                                    const bluetooth::Uuid& service);
uint16_t gatts_add_characteristic(tGATT_SVC_DB& db, tGATT_PERM perm,
                                  tGATT_CHAR_PROP property,
                                  const bluetooth::Uuid& char_uuid);
uint16_t gatts_add_char_ext_prop_descr(tGATT_SVC_DB& db,
                                       uint16_t extended_properties);
uint16_t gatts_add_char_descr(tGATT_SVC_DB& db, tGATT_PERM perm,
                              const bluetooth::Uuid& dscp_uuid);
tGATT_STATUS gatts_db_read_attr_value_by_type(
    tGATT_TCB& tcb, uint16_t cid, tGATT_SVC_DB* p_db, uint8_t op_code,
    BT_HDR* p_rsp, uint16_t s_handle, uint16_t e_handle,
    const bluetooth::Uuid& type, uint16_t* p_len, tGATT_SEC_FLAG sec_flag,
    uint8_t key_size, uint32_t trans_id, uint16_t* p_cur_handle);
tGATT_STATUS gatts_read_attr_value_by_handle(
    tGATT_TCB& tcb, uint16_t cid, tGATT_SVC_DB* p_db, uint8_t op_code,
    uint16_t handle, uint16_t offset, uint8_t* p_value, uint16_t* p_len,
    uint16_t mtu, tGATT_SEC_FLAG sec_flag, uint8_t key_size, uint32_t trans_id);
tGATT_STATUS gatts_write_attr_perm_check(tGATT_SVC_DB* p_db, uint8_t op_code,
                                         uint16_t handle, uint16_t offset,
                                         uint8_t* p_data, uint16_t len,
                                         tGATT_SEC_FLAG sec_flag,
                                         uint8_t key_size);
tGATT_STATUS gatts_read_attr_perm_check(tGATT_SVC_DB* p_db, bool is_long,
                                        uint16_t handle,
                                        tGATT_SEC_FLAG sec_flag,
                                        uint8_t key_size);
bluetooth::Uuid* gatts_get_service_uuid(tGATT_SVC_DB* p_db);

/* gatt_sr_hash.cc */
Octet16 gatts_calculate_database_hash(std::list<tGATT_SRV_LIST_ELEM>* lst_ptr);

namespace fmt {
template <>
struct formatter<tGATT_CH_STATE> : enum_formatter<tGATT_CH_STATE> {};
}  // namespace fmt

#endif
