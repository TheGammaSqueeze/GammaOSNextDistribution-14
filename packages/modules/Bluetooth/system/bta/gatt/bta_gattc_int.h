/******************************************************************************
 *
 *  Copyright 2003-2012 Broadcom Corporation
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
 *  This is the private file for the file transfer client (FTC).
 *
 ******************************************************************************/
#ifndef BTA_GATTC_INT_H
#define BTA_GATTC_INT_H

#include <bluetooth/log.h>

#include <cstdint>
#include <deque>

#include "bta/gatt/database.h"
#include "bta/gatt/database_builder.h"
#include "bta/include/bta_gatt_api.h"
#include "bta/sys/bta_sys.h"
#include "internal_include/bt_target.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/gatt_api.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants and data types
 ****************************************************************************/
enum {
  BTA_GATTC_API_OPEN_EVT = BTA_SYS_EVT_START(BTA_ID_GATTC),
  BTA_GATTC_INT_OPEN_FAIL_EVT,
  BTA_GATTC_API_CANCEL_OPEN_EVT,
  BTA_GATTC_INT_CANCEL_OPEN_OK_EVT,

  BTA_GATTC_API_READ_EVT,
  BTA_GATTC_API_WRITE_EVT,
  BTA_GATTC_API_EXEC_EVT,
  BTA_GATTC_API_CFG_MTU_EVT,

  BTA_GATTC_API_CLOSE_EVT,

  BTA_GATTC_API_SEARCH_EVT,
  BTA_GATTC_API_CONFIRM_EVT,
  BTA_GATTC_API_READ_MULTI_EVT,

  BTA_GATTC_INT_CONN_EVT,
  BTA_GATTC_INT_DISCOVER_EVT,
  BTA_GATTC_DISCOVER_CMPL_EVT,
  BTA_GATTC_OP_CMPL_EVT,
  BTA_GATTC_INT_DISCONN_EVT
};
typedef uint16_t tBTA_GATTC_INT_EVT;

#define BTA_GATTC_SERVICE_CHANGED_LEN 4

/* max client application GATTC can support */
#ifndef BTA_GATTC_CL_MAX
#define BTA_GATTC_CL_MAX 32
#endif

/* max known devices GATTC can support in Bluetooth spec */
#ifndef BTA_GATTC_KNOWN_SR_MAX
#define BTA_GATTC_KNOWN_SR_MAX 255
#endif

#ifndef BTA_GATTC_CLCB_MAX
#define BTA_GATTC_CLCB_MAX GATT_CL_MAX_LCB
#endif

#define BTA_GATTC_WRITE_PREPARE GATT_WRITE_PREPARE

/* internal strucutre for GATTC register API  */
typedef struct {
  BT_HDR_RIGID hdr;
  RawAddress remote_bda;
  tGATT_IF client_if;
  tBTM_BLE_CONN_TYPE connection_type;
  tBT_TRANSPORT transport;
  uint8_t initiating_phys;
  bool opportunistic;
  tBT_DEVICE_TYPE remote_addr_type;
} tBTA_GATTC_API_OPEN;

typedef struct {
  BT_HDR_RIGID hdr;
  RawAddress remote_bda;
  tGATT_IF client_if;
  bool is_direct;
  tBT_TRANSPORT transport;
  uint8_t initiating_phys;
  bool opportunistic;
} tBTA_GATTC_API_CANCEL_OPEN;

typedef struct {
  BT_HDR_RIGID hdr;

  /* it is important that is_multi_read field stays at same position between
   * tBTA_GATTC_API_READ and tBTA_GATTC_API_READ_MULTI, as it is read from
   * parent union */
  uint8_t is_multi_read;

  tGATT_AUTH_REQ auth_req;

  // read by handle data
  uint16_t handle;

  // read by UUID data
  bluetooth::Uuid uuid;
  uint16_t s_handle;
  uint16_t e_handle;

  tBTA_GATTC_EVT cmpl_evt;
  GATT_READ_OP_CB read_cb;
  void* read_cb_data;
} tBTA_GATTC_API_READ;

typedef struct {
  BT_HDR_RIGID hdr;
  tGATT_AUTH_REQ auth_req;
  uint16_t handle;
  tGATT_WRITE_TYPE write_type;
  uint16_t offset;
  uint16_t len;
  uint8_t* p_value;
  GATT_WRITE_OP_CB write_cb;
  void* write_cb_data;
} tBTA_GATTC_API_WRITE;

typedef struct {
  BT_HDR_RIGID hdr;
  bool is_execute;
} tBTA_GATTC_API_EXEC;

typedef struct {
  BT_HDR_RIGID hdr;
  uint16_t cid;
} tBTA_GATTC_API_CONFIRM;

typedef struct {
  BT_HDR_RIGID hdr;
  tGATTC_OPTYPE op_code;
  tGATT_STATUS status;
  tGATT_CL_COMPLETE* p_cmpl;
} tBTA_GATTC_OP_CMPL;

typedef struct {
  BT_HDR_RIGID hdr;
  bluetooth::Uuid* p_srvc_uuid;
} tBTA_GATTC_API_SEARCH;

typedef struct {
  BT_HDR_RIGID hdr;

  /* it is important that is_multi_read field stays at same position between
   * tBTA_GATTC_API_READ and tBTA_GATTC_API_READ_MULTI, as it is read from
   * parent union */
  uint8_t is_multi_read;

  tGATT_AUTH_REQ auth_req;
  tBTA_GATTC_MULTI handles;
  uint8_t variable_len;
  GATT_READ_MULTI_OP_CB read_cb;
  void* read_cb_data;
} tBTA_GATTC_API_READ_MULTI;

typedef struct {
  BT_HDR_RIGID hdr;
  uint16_t mtu;
  GATT_CONFIGURE_MTU_OP_CB mtu_cb;
  void* mtu_cb_data;
} tBTA_GATTC_API_CFG_MTU;

typedef struct {
  BT_HDR_RIGID hdr;
  RawAddress remote_bda;
  tGATT_IF client_if;
  uint8_t role;
  tBT_TRANSPORT transport;
  tGATT_DISCONN_REASON reason;
} tBTA_GATTC_INT_CONN;

typedef union {
  BT_HDR_RIGID hdr;
  tBTA_GATTC_API_OPEN api_conn;
  tBTA_GATTC_API_CANCEL_OPEN api_cancel_conn;
  tBTA_GATTC_API_READ api_read;
  tBTA_GATTC_API_SEARCH api_search;
  tBTA_GATTC_API_WRITE api_write;
  tBTA_GATTC_API_CONFIRM api_confirm;
  tBTA_GATTC_API_EXEC api_exec;
  tBTA_GATTC_API_READ_MULTI api_read_multi;
  tBTA_GATTC_API_CFG_MTU api_mtu;
  tBTA_GATTC_OP_CMPL op_cmpl;
  tBTA_GATTC_INT_CONN int_conn;
} tBTA_GATTC_DATA;

enum {
  BTA_GATTC_IDLE_ST = 0, /* Idle  */
  BTA_GATTC_W4_CONN_ST,  /* Wait for connection -  (optional) */
  BTA_GATTC_CONN_ST,     /* connected state */
  BTA_GATTC_DISCOVER_ST  /* discover is in progress */
};
typedef uint8_t tBTA_GATTC_STATE;

typedef struct {
  bool in_use;
  RawAddress server_bda;
  bool connected;

#define BTA_GATTC_SERV_IDLE 0
#define BTA_GATTC_SERV_LOAD 1
#define BTA_GATTC_SERV_SAVE 2
#define BTA_GATTC_SERV_DISC 3
#define BTA_GATTC_SERV_DISC_ACT 4

  uint8_t state;

  gatt::Database gatt_database;
  uint8_t update_count; /* indication received */
  uint8_t num_clcb;     /* number of associated CLCB */

  gatt::DatabaseBuilder pending_discovery;

  /* used only during service discovery, when reading Extended Characteristic
   * Properties */
  bool read_multiple_not_supported;

  uint8_t srvc_hdl_chg; /* service handle change indication pending */
  bool srvc_hdl_db_hash;   /* read db hash pending */
  uint8_t srvc_disc_count; /* current discovery retry count */
  uint16_t attr_index;  /* cahce NV saving/loading attribute index */

  uint16_t mtu;

  bool disc_blocked_waiting_on_version;
  uint16_t blocked_conn_id;
} tBTA_GATTC_SERV;

#ifndef BTA_GATTC_NOTIF_REG_MAX
#define BTA_GATTC_NOTIF_REG_MAX 64
#endif

typedef struct {
  bool in_use;
  bool app_disconnected;
  RawAddress remote_bda;
  uint16_t handle;
} tBTA_GATTC_NOTIF_REG;

typedef struct {
  tBTA_GATTC_CBACK* p_cback;
  bool in_use;
  tGATT_IF client_if; /* client interface with BTE stack for this application */
  uint8_t num_clcb; /* number of associated CLCB */
  bool dereg_pending;
  bluetooth::Uuid app_uuid;
  tBTA_GATTC_NOTIF_REG notif_reg[BTA_GATTC_NOTIF_REG_MAX];
} tBTA_GATTC_RCB;

/* client channel is a mapping between a BTA client(cl_id) and a remote BD
 * address */
typedef struct {
  uint16_t bta_conn_id; /* client channel ID, unique for clcb */
  RawAddress bda;
  tBT_TRANSPORT transport;  /* channel transport */
  tBTA_GATTC_RCB* p_rcb;    /* pointer to the registration CB */
  tBTA_GATTC_SERV* p_srcb;  /* server cache CB */
  const tBTA_GATTC_DATA* p_q_cmd; /* command in queue waiting for execution */
  std::deque<const tBTA_GATTC_DATA*> p_q_cmd_queue;

// request during discover state
#define BTA_GATTC_DISCOVER_REQ_NONE 0
#define BTA_GATTC_DISCOVER_REQ_READ_EXT_PROP_DESC 1
#define BTA_GATTC_DISCOVER_REQ_READ_DB_HASH 2
#define BTA_GATTC_DISCOVER_REQ_READ_DB_HASH_FOR_SVC_CHG 3

  uint8_t request_during_discovery; /* request during discover state */

#define BTA_GATTC_NO_SCHEDULE 0
#define BTA_GATTC_DISC_WAITING 0x01
#define BTA_GATTC_REQ_WAITING 0x10

  uint8_t auto_update; /* auto update is waiting */
  bool disc_active;
  bool in_use;
  tBTA_GATTC_STATE state;
  tGATT_STATUS status;
} tBTA_GATTC_CLCB;

/* back ground connection tracking information */
#if GATT_MAX_APPS <= 8
typedef uint8_t tBTA_GATTC_CIF_MASK;
#elif GATT_MAX_APPS <= 16
typedef uint16_t tBTA_GATTC_CIF_MASK;
#elif GATT_MAX_APPS <= 32
typedef uint32_t tBTA_GATTC_CIF_MASK;
#endif

typedef struct {
  bool in_use;
  RawAddress remote_bda;
  tBTA_GATTC_CIF_MASK cif_mask;

} tBTA_GATTC_BG_TCK;

typedef struct {
  bool in_use;
  RawAddress remote_bda;
} tBTA_GATTC_CONN;

enum {
  BTA_GATTC_STATE_DISABLED,
  BTA_GATTC_STATE_ENABLING,
  BTA_GATTC_STATE_ENABLED,
  BTA_GATTC_STATE_DISABLING
};

typedef struct {
  uint8_t state;

  tBTA_GATTC_CONN conn_track[GATT_MAX_PHY_CHANNEL];
  tBTA_GATTC_BG_TCK bg_track[BTA_GATTC_KNOWN_SR_MAX];
  tBTA_GATTC_RCB cl_rcb[BTA_GATTC_CL_MAX];

  tBTA_GATTC_CLCB clcb[BTA_GATTC_CLCB_MAX];
  tBTA_GATTC_SERV known_server[BTA_GATTC_KNOWN_SR_MAX];
} tBTA_GATTC_CB;

/*****************************************************************************
 *  Global data
 ****************************************************************************/

/* GATTC control block */
extern tBTA_GATTC_CB bta_gattc_cb;

/*****************************************************************************
 *  Function prototypes
 ****************************************************************************/
bool bta_gattc_hdl_event(const BT_HDR_RIGID* p_msg);
bool bta_gattc_sm_execute(tBTA_GATTC_CLCB* p_clcb, uint16_t event,
                          const tBTA_GATTC_DATA* p_data);

/* function processed outside SM */
void bta_gattc_disable();
void bta_gattc_register(const bluetooth::Uuid& app_uuid,
                        tBTA_GATTC_CBACK* p_data, BtaAppRegisterCallback cb,
                        bool eatt_support);
void bta_gattc_process_api_open(const tBTA_GATTC_DATA* p_msg);
void bta_gattc_process_api_open_cancel(const tBTA_GATTC_DATA* p_msg);
void bta_gattc_deregister(tBTA_GATTC_RCB* p_clreg);

/* function within state machine */
void bta_gattc_open(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_open_fail(tBTA_GATTC_CLCB* p_clcb,
                         const tBTA_GATTC_DATA* p_data);
void bta_gattc_open_error(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data);

void bta_gattc_cancel_open(tBTA_GATTC_CLCB* p_clcb,
                           const tBTA_GATTC_DATA* p_data);
void bta_gattc_cancel_open_ok(tBTA_GATTC_CLCB* p_clcb,
                              const tBTA_GATTC_DATA* p_data);
void bta_gattc_cancel_open_error(tBTA_GATTC_CLCB* p_clcb,
                                 const tBTA_GATTC_DATA* p_data);

void bta_gattc_conn(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);

void bta_gattc_close(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_close_fail(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data);
void bta_gattc_disc_close(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data);

void bta_gattc_start_discover(tBTA_GATTC_CLCB* p_clcb,
                              const tBTA_GATTC_DATA* p_data);
void bta_gattc_start_discover_internal(tBTA_GATTC_CLCB* p_clcb);
void bta_gattc_disc_cmpl(tBTA_GATTC_CLCB* p_clcb,
                         const tBTA_GATTC_DATA* p_data);
void bta_gattc_read(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_write(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_op_cmpl(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_q_cmd(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_search(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_fail(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_confirm(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_execute(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_read_multi(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data);
void bta_gattc_ci_open(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_ci_close(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_op_cmpl_during_discovery(tBTA_GATTC_CLCB* p_clcb,
                                        const tBTA_GATTC_DATA* p_data);
void bta_gattc_restart_discover(tBTA_GATTC_CLCB* p_clcb,
                                const tBTA_GATTC_DATA* p_msg);
void bta_gattc_cancel_bk_conn(const tBTA_GATTC_API_CANCEL_OPEN* p_data);
void bta_gattc_send_open_cback(tBTA_GATTC_RCB* p_clreg, tGATT_STATUS status,
                               const RawAddress& remote_bda, uint16_t conn_id,
                               tBT_TRANSPORT transport, uint16_t mtu);
void bta_gattc_process_api_refresh(const RawAddress& remote_bda);
void bta_gattc_cfg_mtu(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data);
void bta_gattc_listen(tBTA_GATTC_DATA* p_msg);
void bta_gattc_broadcast(tBTA_GATTC_DATA* p_msg);

/* utility functions */
tBTA_GATTC_CLCB* bta_gattc_find_clcb_by_cif(uint8_t client_if,
                                            const RawAddress& remote_bda,
                                            tBT_TRANSPORT transport);
tBTA_GATTC_CLCB* bta_gattc_find_clcb_by_conn_id(uint16_t conn_id);
tBTA_GATTC_CLCB* bta_gattc_clcb_alloc(tGATT_IF client_if,
                                      const RawAddress& remote_bda,
                                      tBT_TRANSPORT transport);
void bta_gattc_clcb_dealloc(tBTA_GATTC_CLCB* p_clcb);
void bta_gattc_server_disconnected(tBTA_GATTC_SERV* p_srcb);
tBTA_GATTC_CLCB* bta_gattc_find_alloc_clcb(tGATT_IF client_if,
                                           const RawAddress& remote_bda,
                                           tBT_TRANSPORT transport);
tBTA_GATTC_RCB* bta_gattc_cl_get_regcb(uint8_t client_if);
tBTA_GATTC_SERV* bta_gattc_find_srcb(const RawAddress& bda);
tBTA_GATTC_SERV* bta_gattc_srcb_alloc(const RawAddress& bda);
tBTA_GATTC_SERV* bta_gattc_find_scb_by_cid(uint16_t conn_id);
tBTA_GATTC_CLCB* bta_gattc_find_int_conn_clcb(tBTA_GATTC_DATA* p_msg);
tBTA_GATTC_CLCB* bta_gattc_find_int_disconn_clcb(tBTA_GATTC_DATA* p_msg);

enum BtaEnqueuedResult_t {
  ENQUEUED_READY_TO_SEND,
  ENQUEUED_FOR_LATER,
};

BtaEnqueuedResult_t bta_gattc_enqueue(tBTA_GATTC_CLCB* p_clcb,
                                      const tBTA_GATTC_DATA* p_data);
bool bta_gattc_is_data_queued(tBTA_GATTC_CLCB* p_clcb,
                              const tBTA_GATTC_DATA* p_data);
void bta_gattc_continue(tBTA_GATTC_CLCB* p_clcb);
void bta_gattc_send_mtu_response(tBTA_GATTC_CLCB* p_clcb,
                                 const tBTA_GATTC_DATA* p_data,
                                 uint16_t current_mtu);
void bta_gattc_cmpl_sendmsg(uint16_t conn_id, tGATTC_OPTYPE op,
                            tGATT_STATUS status, tGATT_CL_COMPLETE* p_data);

bool bta_gattc_check_notif_registry(tBTA_GATTC_RCB* p_clreg,
                                    tBTA_GATTC_SERV* p_srcb,
                                    tBTA_GATTC_NOTIFY* p_notify);
bool bta_gattc_mark_bg_conn(tGATT_IF client_if, const RawAddress& remote_bda,
                            bool add);
bool bta_gattc_check_bg_conn(tGATT_IF client_if, const RawAddress& remote_bda,
                             uint8_t role);
uint8_t bta_gattc_num_reg_app(void);
void bta_gattc_clear_notif_registration(tBTA_GATTC_SERV* p_srcb,
                                        uint16_t conn_id, uint16_t start_handle,
                                        uint16_t end_handle);
tBTA_GATTC_SERV* bta_gattc_find_srvr_cache(const RawAddress& bda);
bool bta_gattc_is_robust_caching_enabled();

/* discovery functions */
void bta_gattc_disc_res_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                              tGATT_DISC_RES* p_data);
void bta_gattc_disc_cmpl_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                               tGATT_STATUS status);
tGATT_STATUS bta_gattc_discover_pri_service(uint16_t conn_id,
                                            tBTA_GATTC_SERV* p_server_cb,
                                            tGATT_DISC_TYPE disc_type);
void bta_gattc_search_service(tBTA_GATTC_CLCB* p_clcb, bluetooth::Uuid* p_uuid);
const std::list<gatt::Service>* bta_gattc_get_services(uint16_t conn_id);
const gatt::Service* bta_gattc_get_service_for_handle(uint16_t conn_id,
                                                      uint16_t handle);
const gatt::Characteristic* bta_gattc_get_characteristic_srcb(
    tBTA_GATTC_SERV* p_srcb, uint16_t handle);
const gatt::Service* bta_gattc_get_service_for_handle_srcb(
    tBTA_GATTC_SERV* p_srcb, uint16_t handle);
const gatt::Characteristic* bta_gattc_get_characteristic(uint16_t conn_id,
                                                         uint16_t handle);
const gatt::Descriptor* bta_gattc_get_descriptor(uint16_t conn_id,
                                                 uint16_t handle);
const gatt::Characteristic* bta_gattc_get_owning_characteristic(
    uint16_t conn_id, uint16_t handle);
void bta_gattc_get_gatt_db(uint16_t conn_id, uint16_t start_handle,
                           uint16_t end_handle, btgatt_db_element_t** db,
                           int* count);
void bta_gattc_init_cache(tBTA_GATTC_SERV* p_srvc_cb);

enum class RobustCachingSupport {
  UNSUPPORTED,
  SUPPORTED,
  UNKNOWN,
  W4_REMOTE_VERSION
};
RobustCachingSupport GetRobustCachingSupport(const tBTA_GATTC_CLCB* p_clcb,
                                             const gatt::Database& db);

void bta_gattc_reset_discover_st(tBTA_GATTC_SERV* p_srcb, tGATT_STATUS status);

tBTA_GATTC_CONN* bta_gattc_conn_alloc(const RawAddress& remote_bda);
tBTA_GATTC_CONN* bta_gattc_conn_find(const RawAddress& remote_bda);
tBTA_GATTC_CONN* bta_gattc_conn_find_alloc(const RawAddress& remote_bda);
bool bta_gattc_conn_dealloc(const RawAddress& remote_bda);

/* bta_gattc_cache */
bool bta_gattc_read_db_hash(tBTA_GATTC_CLCB* p_clcb, bool is_svc_chg);

/* bta_gattc_db_storage */
gatt::Database bta_gattc_hash_load(const Octet16& hash);
bool bta_gattc_hash_write(const Octet16& hash, const gatt::Database& database);
gatt::Database bta_gattc_cache_load(const RawAddress& server_bda);
void bta_gattc_cache_write(const RawAddress& server_bda,
                           const gatt::Database& database);
void bta_gattc_cache_link(const RawAddress& server_bda, const Octet16& hash);
void bta_gattc_cache_reset(const RawAddress& server_bda);

namespace fmt {
template <>
struct formatter<RobustCachingSupport> : enum_formatter<RobustCachingSupport> {
};
}  // namespace fmt

#endif /* BTA_GATTC_INT_H */
