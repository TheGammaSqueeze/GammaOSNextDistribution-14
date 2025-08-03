/******************************************************************************
 *
 *  Copyright 2005-2012 Broadcom Corporation
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
 *  This file contains BTA HID Host internal definitions
 *
 ******************************************************************************/

#ifndef BTA_HH_INT_H
#define BTA_HH_INT_H

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/include/bta_api.h"
#include "bta/include/bta_gatt_api.h"
#include "bta/include/bta_hh_api.h"
#include "bta/sys/bta_sys.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

/* state machine events, these events are handled by the state machine */
enum tBTA_HH_INT_EVT : uint16_t {
  BTA_HH_API_OPEN_EVT = BTA_SYS_EVT_START(BTA_ID_HH),
  BTA_HH_API_CLOSE_EVT,
  BTA_HH_INT_OPEN_EVT,
  BTA_HH_INT_CLOSE_EVT,
  BTA_HH_INT_DATA_EVT,
  BTA_HH_INT_CTRL_DATA,
  BTA_HH_INT_HANDSK_EVT,
  BTA_HH_SDP_CMPL_EVT,
  BTA_HH_API_WRITE_DEV_EVT,
  BTA_HH_API_GET_DSCP_EVT,
  BTA_HH_API_MAINT_DEV_EVT,
  BTA_HH_OPEN_CMPL_EVT,
  BTA_HH_GATT_CLOSE_EVT,
  BTA_HH_GATT_OPEN_EVT,
  BTA_HH_START_ENC_EVT,
  BTA_HH_ENC_CMPL_EVT,
  BTA_HH_GATT_ENC_CMPL_EVT
}; /* HID host internal events */

#define BTA_HH_INVALID_EVT (BTA_HH_GATT_ENC_CMPL_EVT + 1)

/* state machine states */
enum {
  BTA_HH_NULL_ST,
  BTA_HH_IDLE_ST,
  BTA_HH_W4_CONN_ST,
  BTA_HH_CONN_ST,
  BTA_HH_W4_SEC,
  BTA_HH_INVALID_ST /* Used to check invalid states before executing SM function
                     */

};
typedef uint8_t tBTA_HH_STATE;

/* data structure used to send a command/data to HID device */
typedef struct {
  BT_HDR_RIGID hdr;
  uint8_t t_type;
  uint8_t param;
  uint8_t rpt_id;
  uint16_t data;
  BT_HDR* p_data;
} tBTA_HH_CMD_DATA;

typedef struct {
  BT_HDR_RIGID hdr;
  tAclLinkSpec link_spec;
  tBTA_HH_PROTO_MODE mode;
} tBTA_HH_API_CONN;

/* internal event data from BTE HID callback */
typedef struct {
  BT_HDR_RIGID hdr;
  tAclLinkSpec link_spec;
  uint32_t data;
  BT_HDR* p_data;
} tBTA_HH_CBACK_DATA;

typedef struct {
  BT_HDR_RIGID hdr;
  tAclLinkSpec link_spec;
  uint16_t attr_mask;
  uint16_t sub_event;
  uint8_t sub_class;
  uint8_t app_id;
  tBTA_HH_DEV_DSCP_INFO dscp_info;
} tBTA_HH_MAINT_DEV;

typedef struct {
  BT_HDR_RIGID hdr;
  uint16_t conn_id;
  tGATT_DISCONN_REASON reason;
} tBTA_HH_LE_CLOSE;

typedef struct {
  BT_HDR_RIGID hdr;
  uint16_t scan_int;
  uint16_t scan_win;
} tBTA_HH_SCPP_UPDATE;

/* union of all event data types */
typedef union {
  BT_HDR_RIGID hdr;
  tBTA_HH_API_CONN api_conn;
  tBTA_HH_CMD_DATA api_sndcmd;
  tBTA_HH_CBACK_DATA hid_cback;
  tBTA_HH_STATUS status;
  tBTA_HH_MAINT_DEV api_maintdev;
  tBTA_HH_LE_CLOSE le_close;
  tBTA_GATTC_OPEN le_open;
  tBTA_HH_SCPP_UPDATE le_scpp_update;
  tBTA_GATTC_ENC_CMPL_CB le_enc_cmpl;
} tBTA_HH_DATA;

typedef struct {
  uint8_t index;
  bool in_use;
  uint8_t srvc_inst_id;
  uint16_t char_inst_id;
  tBTA_HH_RPT_TYPE rpt_type;
  uint16_t uuid;
  uint8_t rpt_id;
  bool client_cfg_exist;
  uint16_t client_cfg_value;
} tBTA_HH_LE_RPT;

#ifndef BTA_HH_LE_RPT_MAX
#define BTA_HH_LE_RPT_MAX 20
#endif

enum tBTA_HH_SERVICE_STATE {
  BTA_HH_SERVICE_UNKNOWN,
  BTA_HH_SERVICE_CHANGED,
  BTA_HH_SERVICE_DISCOVERED,
};

typedef struct {
  tBTA_HH_SERVICE_STATE state;
  uint8_t srvc_inst_id;
  tBTA_HH_LE_RPT report[BTA_HH_LE_RPT_MAX];

  uint16_t proto_mode_handle;
  uint8_t control_point_handle;

  uint8_t
      incl_srvc_inst; /* assuming only one included service : battery service */
  uint8_t cur_expl_char_idx; /* currently discovering service index */
  uint8_t* rpt_map;
  uint16_t ext_rpt_ref;
  tBTA_HH_DEV_DESCR descriptor;

} tBTA_HH_LE_HID_SRVC;

/* convert a HID handle to the LE CB index */
#define BTA_HH_GET_LE_CB_IDX(x) (((x) >> 4) - 1)
/* convert a GATT connection ID to HID device handle, it is the hi 4 bits of a
 * uint8_t */
#define BTA_HH_GET_LE_DEV_HDL(x) (uint8_t)(((x) + 1) << 4)
/* check to see if th edevice handle is a LE device handle */
#define BTA_HH_IS_LE_DEV_HDL(x) ((x)&0xf0)
#define BTA_HH_IS_LE_DEV_HDL_VALID(x) (((x) >> 4) <= BTA_HH_LE_MAX_KNOWN)

/* device control block */
typedef struct {
  tBTA_HH_DEV_DSCP_INFO dscp_info; /* report descriptor and DI information */
  tAclLinkSpec link_spec; /* ACL link specification of the HID device */
  uint16_t attr_mask;              /* attribute mask */
  uint16_t w4_evt;                 /* W4_handshake event name */
  uint8_t index;                   /* index number referenced to handle index */
  uint8_t sub_class;               /* Cod sub class */
  uint8_t app_id;                  /* application ID for this connection */
  uint8_t hid_handle;          /* device handle : low 4 bits for regular HID:
                                  HID_HOST_MAX_DEVICES can not exceed 15;
                                                 high 4 bits for LE HID:
                                  GATT_MAX_PHY_CHANNEL can not exceed 15 */
  bool vp;                     /* virtually unplug flag */
  bool in_use;                 /* control block currently in use */
  bool incoming_conn;          /* is incoming connection? */
  uint8_t incoming_hid_handle; /* temporary handle for incoming connection? */
  tBTA_HH_PROTO_MODE mode; /* protocol mode */
  tBTA_HH_STATE state;     /* CB state */

#define BTA_HH_LE_DISC_NONE 0x00
#define BTA_HH_LE_DISC_HIDS 0x01
#define BTA_HH_LE_DISC_DIS 0x02
#define BTA_HH_LE_DISC_SCPS 0x04

  uint8_t disc_active;
  tBTA_HH_STATUS status;
  tBTM_STATUS btm_status;
  bool is_le_device;
  tBTA_HH_LE_HID_SRVC hid_srvc;
  uint16_t conn_id;
  bool in_bg_conn;
  uint8_t clt_cfg_idx;
  bool scps_supported;

#define BTA_HH_LE_SCPS_NOTIFY_NONE 0
#define BTA_HH_LE_SCPS_NOTIFY_SPT 0x01
#define BTA_HH_LE_SCPS_NOTIFY_ENB 0x02
  uint8_t scps_notify; /* scan refresh supported/notification enabled */
  bool security_pending;
} tBTA_HH_DEV_CB;

/******************************************************************************
 * Main Control Block
 ******************************************************************************/
typedef struct {
  tBTA_HH_DEV_CB kdev[BTA_HH_MAX_DEVICE]; /* device control block */
  tBTA_HH_DEV_CB* p_cur;                  /* current device control
                                                 block idx, used in sdp */
  uint8_t cb_index[BTA_HH_MAX_KNOWN];     /* maintain a CB index
                                        map to dev handle */
  uint8_t le_cb_index[BTA_HH_LE_MAX_KNOWN]; /* maintain a CB index map to LE dev
                                             handle */
  tGATT_IF gatt_if;
  tBTA_HH_CBACK* p_cback; /* Application callbacks */
  tSDP_DISCOVERY_DB* p_disc_db;
  uint8_t cnt_num;     /* connected device number */
  bool w4_disable;     /* w4 disable flag */
} tBTA_HH_CB;

extern tBTA_HH_CB bta_hh_cb;

/* from bta_hh_cfg.c */
extern tBTA_HH_CFG* p_bta_hh_cfg;

/*****************************************************************************
 *  Function prototypes
 ****************************************************************************/
bool bta_hh_hdl_event(const BT_HDR_RIGID* p_msg);
void bta_hh_sm_execute(tBTA_HH_DEV_CB* p_cb, uint16_t event,
                       const tBTA_HH_DATA* p_data);

/* action functions */
void bta_hh_api_disc_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_open_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_close_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_data_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_ctrl_dat_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_connect(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_sdp_cmpl(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_write_dev_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_get_dscp_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_handsk_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_maint_dev_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_open_cmpl_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_open_failure(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);

/* utility functions */
uint8_t bta_hh_find_cb(const tAclLinkSpec& link_spec);
tBTA_HH_DEV_CB* bta_hh_get_cb(const tAclLinkSpec& link_spec);
bool bta_hh_tod_spt(tBTA_HH_DEV_CB* p_cb, uint8_t sub_class);
void bta_hh_clean_up_kdev(tBTA_HH_DEV_CB* p_cb);

void bta_hh_add_device_to_list(tBTA_HH_DEV_CB* p_cb, uint8_t handle,
                               uint16_t attr_mask,
                               const tHID_DEV_DSCP_INFO* p_dscp_info,
                               uint8_t sub_class, uint16_t max_latency,
                               uint16_t min_tout, uint8_t app_id);
void bta_hh_update_di_info(tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id,
                           uint16_t product_id, uint16_t version, uint8_t flag,
                           uint8_t ctry_code);
void bta_hh_cleanup_disable(tBTA_HH_STATUS status);

uint8_t bta_hh_dev_handle_to_cb_idx(uint8_t dev_handle);

/* action functions used outside state machine */
void bta_hh_api_enable(tBTA_HH_CBACK* p_cback, bool enable_hid,
                       bool enable_hogp);
void bta_hh_api_disable(void);
void bta_hh_disc_cmpl(void);

tBTA_HH_STATUS bta_hh_read_ssr_param(const tAclLinkSpec& link_spec,
                                     uint16_t* p_max_ssr_lat,
                                     uint16_t* p_min_ssr_tout);

/* functions for LE HID */
void bta_hh_le_enable(void);
void bta_hh_le_deregister(void);
void bta_hh_le_open_conn(tBTA_HH_DEV_CB* p_cb, const tAclLinkSpec& link_spec);
void bta_hh_le_api_disc_act(tBTA_HH_DEV_CB* p_cb);
void bta_hh_le_get_dscp_act(tBTA_HH_DEV_CB* p_cb);
void bta_hh_le_write_dev_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
uint8_t bta_hh_le_add_device(tBTA_HH_DEV_CB* p_cb,
                             const tBTA_HH_MAINT_DEV* p_dev_info);
void bta_hh_le_remove_dev_bg_conn(tBTA_HH_DEV_CB* p_cb);
void bta_hh_le_open_fail(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_gatt_open(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_gatt_close(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data);
void bta_hh_start_security(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_buf);

void bta_hh_start_security(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_buf);
void bta_hh_security_cmpl(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_buf);
void bta_hh_le_notify_enc_cmpl(tBTA_HH_DEV_CB* p_cb,
                               const tBTA_HH_DATA* p_data);

#if (BTA_HH_DEBUG == TRUE)
void bta_hh_trace_dev_db(void);
#endif

namespace fmt {
template <>
struct formatter<tBTA_HH_SERVICE_STATE>
    : enum_formatter<tBTA_HH_SERVICE_STATE> {};
}  // namespace fmt

#endif
