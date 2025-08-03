/******************************************************************************
 *
 *  Copyright 2008-2012 Broadcom Corporation
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
 *  this file contains the main GATT server attributes access request
 *  handling functions.
 *
 ******************************************************************************/

#include <base/logging.h>
#include <bluetooth/log.h>

#include <deque>
#include <map>

#include "base/functional/callback.h"
#include "btif/include/btif_storage.h"
#include "eatt/eatt.h"
#include "gatt_api.h"
#include "gatt_int.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "os/logging/log_adapter.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_sec_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using base::StringPrintf;
using bluetooth::Uuid;
using namespace bluetooth;

#define BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK 0x01

#define BLE_GATT_CL_SUP_FEAT_CACHING_BITMASK 0x01
#define BLE_GATT_CL_SUP_FEAT_EATT_BITMASK 0x02
#define BLE_GATT_CL_SUP_FEAT_MULTI_NOTIF_BITMASK 0x04

#define BLE_GATT_CL_ANDROID_SUP_FEAT \
  (BLE_GATT_CL_SUP_FEAT_EATT_BITMASK | BLE_GATT_CL_SUP_FEAT_MULTI_NOTIF_BITMASK)

using gatt_sr_supported_feat_cb =
    base::OnceCallback<void(const RawAddress&, uint8_t)>;
using gatt_sirk_cb = base::OnceCallback<void(
    tGATT_STATUS status, const RawAddress&, uint8_t sirk_type, Octet16& sirk)>;

typedef struct {
  uint16_t op_uuid;
  gatt_sr_supported_feat_cb cb;
  gatt_sirk_cb sirk_cb;
} gatt_op_cb_data;

static std::map<uint16_t, std::deque<gatt_op_cb_data>> OngoingOps;

static void gatt_request_cback(uint16_t conn_id, uint32_t trans_id,
                               uint8_t op_code, tGATTS_DATA* p_data);
static void gatt_connect_cback(UNUSED_ATTR tGATT_IF gatt_if,
                               const RawAddress& bda, uint16_t conn_id,
                               bool connected, tGATT_DISCONN_REASON reason,
                               tBT_TRANSPORT transport);
static void gatt_disc_res_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                tGATT_DISC_RES* p_data);
static void gatt_disc_cmpl_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                 tGATT_STATUS status);
static void gatt_cl_op_cmpl_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                  tGATT_STATUS status,
                                  tGATT_CL_COMPLETE* p_data);

static void gatt_cl_start_config_ccc(tGATT_PROFILE_CLCB* p_clcb);

static bool gatt_cl_is_robust_caching_enabled();

static bool gatt_sr_is_robust_caching_enabled();

static bool read_sr_supported_feat_req(
    uint16_t conn_id, base::OnceCallback<void(const RawAddress&, uint8_t)> cb);
static bool read_sr_sirk_req(
    uint16_t conn_id,
    base::OnceCallback<void(tGATT_STATUS status, const RawAddress&,
                            uint8_t sirk_type, Octet16& sirk)>
        cb);

static tGATT_STATUS gatt_sr_read_db_hash(uint16_t conn_id,
                                         tGATT_VALUE* p_value);
static tGATT_STATUS gatt_sr_read_cl_supp_feat(uint16_t conn_id,
                                              tGATT_VALUE* p_value);
static tGATT_STATUS gatt_sr_write_cl_supp_feat(uint16_t conn_id,
                                               tGATT_WRITE_REQ* p_data);

static tGATT_CBACK gatt_profile_cback = {
    .p_conn_cb = gatt_connect_cback,
    .p_cmpl_cb = gatt_cl_op_cmpl_cback,
    .p_disc_res_cb = gatt_disc_res_cback,
    .p_disc_cmpl_cb = gatt_disc_cmpl_cback,
    .p_req_cb = gatt_request_cback,
    .p_enc_cmpl_cb = nullptr,
    .p_congestion_cb = nullptr,
    .p_phy_update_cb = nullptr,
    .p_conn_update_cb = nullptr,
    .p_subrate_chg_cb = nullptr,
};

/*******************************************************************************
 *
 * Function         gatt_profile_find_conn_id_by_bd_addr
 *
 * Description      Find the connection ID by remote address
 *
 * Returns          Connection ID
 *
 ******************************************************************************/
uint16_t gatt_profile_find_conn_id_by_bd_addr(const RawAddress& remote_bda) {
  uint16_t conn_id = GATT_INVALID_CONN_ID;
  GATT_GetConnIdIfConnected(gatt_cb.gatt_if, remote_bda, &conn_id,
                            BT_TRANSPORT_LE);
  if (conn_id == GATT_INVALID_CONN_ID)
    GATT_GetConnIdIfConnected(gatt_cb.gatt_if, remote_bda, &conn_id,
                              BT_TRANSPORT_BR_EDR);
  return conn_id;
}

/*******************************************************************************
 *
 * Function         gatt_profile_find_clcb_by_conn_id
 *
 * Description      find clcb by Connection ID
 *
 * Returns          Pointer to the found link conenction control block.
 *
 ******************************************************************************/
static tGATT_PROFILE_CLCB* gatt_profile_find_clcb_by_conn_id(uint16_t conn_id) {
  uint8_t i_clcb;
  tGATT_PROFILE_CLCB* p_clcb = NULL;

  for (i_clcb = 0, p_clcb = gatt_cb.profile_clcb; i_clcb < GATT_MAX_APPS;
       i_clcb++, p_clcb++) {
    if (p_clcb->in_use && p_clcb->conn_id == conn_id) return p_clcb;
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_profile_find_clcb_by_bd_addr
 *
 * Description      The function searches all LCBs with macthing bd address.
 *
 * Returns          Pointer to the found link conenction control block.
 *
 ******************************************************************************/
static tGATT_PROFILE_CLCB* gatt_profile_find_clcb_by_bd_addr(
    const RawAddress& bda, tBT_TRANSPORT transport) {
  uint8_t i_clcb;
  tGATT_PROFILE_CLCB* p_clcb = NULL;

  for (i_clcb = 0, p_clcb = gatt_cb.profile_clcb; i_clcb < GATT_MAX_APPS;
       i_clcb++, p_clcb++) {
    if (p_clcb->in_use && p_clcb->transport == transport && p_clcb->connected &&
        p_clcb->bda == bda)
      return p_clcb;
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_profile_clcb_alloc
 *
 * Description      The function allocates a GATT profile connection link
 *                  control block
 *
 * Returns          NULL if not found. Otherwise pointer to the connection link
 *                  block.
 *
 ******************************************************************************/
tGATT_PROFILE_CLCB* gatt_profile_clcb_alloc(uint16_t conn_id,
                                            const RawAddress& bda,
                                            tBT_TRANSPORT tranport) {
  uint8_t i_clcb = 0;
  tGATT_PROFILE_CLCB* p_clcb = NULL;

  for (i_clcb = 0, p_clcb = gatt_cb.profile_clcb; i_clcb < GATT_MAX_APPS;
       i_clcb++, p_clcb++) {
    if (!p_clcb->in_use) {
      p_clcb->in_use = true;
      p_clcb->conn_id = conn_id;
      p_clcb->connected = true;
      p_clcb->transport = tranport;
      p_clcb->bda = bda;
      break;
    }
  }
  if (i_clcb < GATT_MAX_APPS) return p_clcb;

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_profile_clcb_dealloc
 *
 * Description      The function deallocates a GATT profile connection link
 *                  control block
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_profile_clcb_dealloc(tGATT_PROFILE_CLCB* p_clcb) {
  memset(p_clcb, 0, sizeof(tGATT_PROFILE_CLCB));
}

/** GAP Attributes Database Request callback */
tGATT_STATUS read_attr_value(uint16_t conn_id, uint16_t handle,
                             tGATT_VALUE* p_value, bool is_long) {
  uint8_t* p = p_value->value;

  if (handle == gatt_cb.handle_sr_supported_feat) {
    /* GATT_UUID_SERVER_SUP_FEAT*/
    if (is_long) return GATT_NOT_LONG;

    UINT8_TO_STREAM(p, gatt_cb.gatt_svr_supported_feat_mask);
    p_value->len = sizeof(gatt_cb.gatt_svr_supported_feat_mask);
    return GATT_SUCCESS;
  }

  if (handle == gatt_cb.handle_cl_supported_feat) {
    /*GATT_UUID_CLIENT_SUP_FEAT */
    if (is_long) return GATT_NOT_LONG;

    return gatt_sr_read_cl_supp_feat(conn_id, p_value);
  }

  if (handle == gatt_cb.handle_of_database_hash) {
    /* GATT_UUID_DATABASE_HASH */
    if (is_long) return GATT_NOT_LONG;

    return gatt_sr_read_db_hash(conn_id, p_value);
  }

  if (handle == gatt_cb.handle_of_h_r) {
    /* GATT_UUID_GATT_SRV_CHGD */
    return GATT_READ_NOT_PERMIT;
  }

  return GATT_NOT_FOUND;
}

/** GAP Attributes Database Read/Read Blob Request process */
tGATT_STATUS proc_read_req(uint16_t conn_id, tGATTS_REQ_TYPE,
                           tGATT_READ_REQ* p_data, tGATTS_RSP* p_rsp) {
  if (p_data->is_long) p_rsp->attr_value.offset = p_data->offset;

  p_rsp->attr_value.handle = p_data->handle;

  return read_attr_value(conn_id, p_data->handle, &p_rsp->attr_value,
                         p_data->is_long);
}

/** GAP ATT server process a write request */
tGATT_STATUS proc_write_req(uint16_t conn_id, tGATTS_REQ_TYPE,
                            tGATT_WRITE_REQ* p_data) {
  uint16_t handle = p_data->handle;

  /* GATT_UUID_SERVER_SUP_FEAT*/
  if (handle == gatt_cb.handle_sr_supported_feat) return GATT_WRITE_NOT_PERMIT;

  /* GATT_UUID_CLIENT_SUP_FEAT*/
  if (handle == gatt_cb.handle_cl_supported_feat)
    return gatt_sr_write_cl_supp_feat(conn_id, p_data);

  /* GATT_UUID_DATABASE_HASH */
  if (handle == gatt_cb.handle_of_database_hash) return GATT_WRITE_NOT_PERMIT;

  /* GATT_UUID_GATT_SRV_CHGD */
  if (handle == gatt_cb.handle_of_h_r) return GATT_WRITE_NOT_PERMIT;

  return GATT_NOT_FOUND;
}

/*******************************************************************************
 *
 * Function         gatt_request_cback
 *
 * Description      GATT profile attribute access request callback.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void gatt_request_cback(uint16_t conn_id, uint32_t trans_id,
                               tGATTS_REQ_TYPE type, tGATTS_DATA* p_data) {
  tGATT_STATUS status = GATT_INVALID_PDU;
  tGATTS_RSP rsp_msg;
  bool rsp_needed = true;

  memset(&rsp_msg, 0, sizeof(tGATTS_RSP));

  switch (type) {
    case GATTS_REQ_TYPE_READ_CHARACTERISTIC:
    case GATTS_REQ_TYPE_READ_DESCRIPTOR:
      status = proc_read_req(conn_id, type, &p_data->read_req, &rsp_msg);
      break;

    case GATTS_REQ_TYPE_WRITE_CHARACTERISTIC:
    case GATTS_REQ_TYPE_WRITE_DESCRIPTOR:
    case GATTS_REQ_TYPE_WRITE_EXEC:
    case GATT_CMD_WRITE:
      if (!p_data->write_req.need_rsp) rsp_needed = false;

      status = proc_write_req(conn_id, type, &p_data->write_req);
      break;

    case GATTS_REQ_TYPE_MTU:
      log::verbose("Get MTU exchange new mtu size: {}", p_data->mtu);
      rsp_needed = false;
      break;

    default:
      log::verbose("Unknown/unexpected LE GAP ATT request: {}", loghex(type));
      break;
  }

  if (rsp_needed) GATTS_SendRsp(conn_id, trans_id, status, &rsp_msg);
}

/*******************************************************************************
 *
 * Function         gatt_connect_cback
 *
 * Description      Gatt profile connection callback.
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_connect_cback(UNUSED_ATTR tGATT_IF gatt_if,
                               const RawAddress& bda, uint16_t conn_id,
                               bool connected, tGATT_DISCONN_REASON reason,
                               tBT_TRANSPORT transport) {
  log::verbose("from {} connected: {}, conn_id: {}",
               ADDRESS_TO_LOGGABLE_STR(bda), connected, loghex(conn_id));

  // if the device is not trusted, remove data when the link is disconnected
  if (!connected && !btm_sec_is_a_bonded_dev(bda)) {
    log::info("remove untrusted client status, bda={}",
              ADDRESS_TO_LOGGABLE_STR(bda));
    btif_storage_remove_gatt_cl_supp_feat(bda);
    btif_storage_remove_gatt_cl_db_hash(bda);
  }

  tGATT_PROFILE_CLCB* p_clcb =
      gatt_profile_find_clcb_by_bd_addr(bda, transport);
  if (p_clcb == NULL) return;

  if (connected) {
    p_clcb->conn_id = conn_id;
    p_clcb->connected = true;

    if (p_clcb->ccc_stage == GATT_SVC_CHANGED_CONNECTING) {
      p_clcb->ccc_stage++;
      gatt_cl_start_config_ccc(p_clcb);
    }
  } else {
    gatt_profile_clcb_dealloc(p_clcb);
  }
}

/*******************************************************************************
 *
 * Function         gatt_profile_db_init
 *
 * Description      Initializa the GATT profile attribute database.
 *
 ******************************************************************************/
void gatt_profile_db_init(void) {
  /* Fill our internal UUID with a fixed pattern 0x81 */
  std::array<uint8_t, Uuid::kNumBytes128> tmp;
  tmp.fill(0x81);

  OngoingOps.clear();

  /* Create a GATT profile service */
  gatt_cb.gatt_if = GATT_Register(Uuid::From128BitBE(tmp), "GattProfileDb",
                                  &gatt_profile_cback, false);
  GATT_StartIf(gatt_cb.gatt_if);

  Uuid service_uuid = Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER);

  Uuid srv_changed_char_uuid = Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD);
  Uuid svr_sup_feat_uuid = Uuid::From16Bit(GATT_UUID_SERVER_SUP_FEAT);
  Uuid cl_sup_feat_uuid = Uuid::From16Bit(GATT_UUID_CLIENT_SUP_FEAT);
  Uuid database_hash_uuid = Uuid::From16Bit(GATT_UUID_DATABASE_HASH);

  btgatt_db_element_t service[] = {
      {
          .uuid = service_uuid,
          .type = BTGATT_DB_PRIMARY_SERVICE,
      },
      {
          .uuid = srv_changed_char_uuid,
          .type = BTGATT_DB_CHARACTERISTIC,
          .properties = GATT_CHAR_PROP_BIT_INDICATE,
          .permissions = 0,
      },
      {
          .uuid = svr_sup_feat_uuid,
          .type = BTGATT_DB_CHARACTERISTIC,
          .properties = GATT_CHAR_PROP_BIT_READ,
          .permissions = GATT_PERM_READ,
      },
      {
          .uuid = cl_sup_feat_uuid,
          .type = BTGATT_DB_CHARACTERISTIC,
          .properties = GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_WRITE,
          .permissions = GATT_PERM_READ | GATT_PERM_WRITE,
      },
      {
          .uuid = database_hash_uuid,
          .type = BTGATT_DB_CHARACTERISTIC,
          .properties = GATT_CHAR_PROP_BIT_READ,
          .permissions = GATT_PERM_READ,
      }};

  GATTS_AddService(gatt_cb.gatt_if, service,
                   sizeof(service) / sizeof(btgatt_db_element_t));

  gatt_cb.handle_of_h_r = service[1].attribute_handle;
  gatt_cb.handle_sr_supported_feat = service[2].attribute_handle;
  gatt_cb.handle_cl_supported_feat = service[3].attribute_handle;
  gatt_cb.handle_of_database_hash = service[4].attribute_handle;

  gatt_cb.gatt_svr_supported_feat_mask |= BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK;
  gatt_cb.gatt_cl_supported_feat_mask |= BLE_GATT_CL_ANDROID_SUP_FEAT;

  if (gatt_cl_is_robust_caching_enabled())
    gatt_cb.gatt_cl_supported_feat_mask |= BLE_GATT_CL_SUP_FEAT_CACHING_BITMASK;

  log::verbose("gatt_if={} EATT supported", gatt_cb.gatt_if);
}

/*******************************************************************************
 *
 * Function         gatt_disc_res_cback
 *
 * Description      Gatt profile discovery result callback
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_disc_res_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                tGATT_DISC_RES* p_data) {
  tGATT_PROFILE_CLCB* p_clcb = gatt_profile_find_clcb_by_conn_id(conn_id);

  if (p_clcb == NULL) return;

  switch (disc_type) {
    case GATT_DISC_SRVC_BY_UUID: /* stage 1 */
      p_clcb->e_handle = p_data->value.group_value.e_handle;
      p_clcb->ccc_result++;
      break;

    case GATT_DISC_CHAR: /* stage 2 */
      p_clcb->s_handle = p_data->value.dclr_value.val_handle;
      p_clcb->ccc_result++;
      break;

    case GATT_DISC_CHAR_DSCPT: /* stage 3 */
      if (p_data->type == Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG)) {
        p_clcb->s_handle = p_data->handle;
        p_clcb->ccc_result++;
      }
      break;

    case GATT_DISC_SRVC_ALL:
    case GATT_DISC_INC_SRVC:
    case GATT_DISC_MAX:
      log::error("Illegal discovery item handled");
      break;
  }
}

/*******************************************************************************
 *
 * Function         gatt_disc_cmpl_cback
 *
 * Description      Gatt profile discovery complete callback
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_disc_cmpl_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                 tGATT_STATUS status) {
  tGATT_PROFILE_CLCB* p_clcb = gatt_profile_find_clcb_by_conn_id(conn_id);
  if (p_clcb == NULL) {
    log::warn("Unable to find gatt profile after discovery complete");
    return;
  }

  if (status != GATT_SUCCESS) {
    log::warn("Gatt discovery completed with errors status:{}", status);
    return;
  }
  if (p_clcb->ccc_result == 0) {
    log::warn("Gatt discovery completed but connection was idle id:{}",
              conn_id);
    return;
  }

  p_clcb->ccc_result = 0;
  p_clcb->ccc_stage++;
  gatt_cl_start_config_ccc(p_clcb);
}

static bool gatt_svc_read_cl_supp_feat_req(uint16_t conn_id) {
  tGATT_READ_PARAM param;

  memset(&param, 0, sizeof(tGATT_READ_PARAM));

  param.service.s_handle = 1;
  param.service.e_handle = 0xFFFF;
  param.service.auth_req = 0;

  param.service.uuid = bluetooth::Uuid::From16Bit(GATT_UUID_CLIENT_SUP_FEAT);

  tGATT_STATUS status = GATTC_Read(conn_id, GATT_READ_BY_TYPE, &param);
  if (status != GATT_SUCCESS) {
    log::error("Read failed. Status: {}", loghex(static_cast<uint8_t>(status)));
    return false;
  }

  gatt_op_cb_data cb_data;

  cb_data.cb =
      base::BindOnce([](const RawAddress& bdaddr, uint8_t support) { return; });
  cb_data.op_uuid = GATT_UUID_CLIENT_SUP_FEAT;
  OngoingOps[conn_id].emplace_back(std::move(cb_data));

  return true;
}

static bool gatt_att_write_cl_supp_feat(uint16_t conn_id, uint16_t handle) {
  tGATT_VALUE attr;

  memset(&attr, 0, sizeof(tGATT_VALUE));

  attr.conn_id = conn_id;
  attr.handle = handle;
  attr.len = 1;
  attr.value[0] = gatt_cb.gatt_cl_supported_feat_mask;

  tGATT_STATUS status = GATTC_Write(conn_id, GATT_WRITE, &attr);
  if (status != GATT_SUCCESS) {
    log::error("Write failed. Status: {}",
               loghex(static_cast<uint8_t>(status)));
    return false;
  }

  return true;
}

/*******************************************************************************
 *
 * Function         gatt_cl_op_cmpl_cback
 *
 * Description      Gatt profile client operation complete callback
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_cl_op_cmpl_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                  tGATT_STATUS status,
                                  tGATT_CL_COMPLETE* p_data) {
  auto iter = OngoingOps.find(conn_id);

  log::verbose("opcode: {} status: {} conn id: {}",
               loghex(static_cast<uint8_t>(op)), status,
               loghex(static_cast<uint8_t>(conn_id)));

  if (op != GATTC_OPTYPE_READ && op != GATTC_OPTYPE_WRITE) {
    log::debug("Not interested in opcode {}", op);
    return;
  }

  if (iter == OngoingOps.end() || (iter->second.size() == 0)) {
    /* If OngoingOps is empty it means we are not interested in the result here.
     */
    log::debug("Unexpected read complete");
    return;
  }

  uint16_t cl_op_uuid = iter->second.front().op_uuid;

  if (op == GATTC_OPTYPE_WRITE) {
    if (cl_op_uuid == GATT_UUID_GATT_SRV_CHGD) {
      log::debug("Write response from Service Changed CCC");
      iter->second.pop_front();
      /* Read server supported features here supported */
      read_sr_supported_feat_req(
          conn_id, base::BindOnce([](const RawAddress& bdaddr,
                                     uint8_t support) { return; }));
    } else {
      log::debug("Not interested in that write response");
    }
    return;
  }

  /* Handle Read operations */
  uint8_t* pp = p_data->att_value.value;

  log::verbose("cl_op_uuid {}", loghex(cl_op_uuid));

  switch (cl_op_uuid) {
    case GATT_UUID_SERVER_SUP_FEAT: {
      uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
      tGATT_TCB& tcb = gatt_cb.tcb[tcb_idx];

      auto operation_callback_data = std::move(iter->second.front());
      iter->second.pop_front();

      /* Check if EATT is supported */
      if (status == GATT_SUCCESS) {
        STREAM_TO_UINT8(tcb.sr_supp_feat, pp);
        btif_storage_set_gatt_sr_supp_feat(tcb.peer_bda, tcb.sr_supp_feat);
      }

      /* Notify user about the supported features */
      std::move(operation_callback_data.cb).Run(tcb.peer_bda, tcb.sr_supp_feat);

      /* If server supports EATT lets try to find handle for the
       * client supported features characteristic, where we could write
       * our supported features as a client.
       */
      if (tcb.sr_supp_feat & BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK) {
        gatt_svc_read_cl_supp_feat_req(conn_id);
      }

      break;
    }
    case GATT_UUID_CSIS_SIRK: {
      uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
      tGATT_TCB& tcb = gatt_cb.tcb[tcb_idx];

      auto operation_callback_data = std::move(iter->second.front());
      iter->second.pop_front();
      tcb.gatt_status = status;

      if (status == GATT_SUCCESS) {
        STREAM_TO_UINT8(tcb.sirk_type, pp);
        STREAM_TO_ARRAY(tcb.sirk.data(), pp, 16);
      }

      std::move(operation_callback_data.sirk_cb)
          .Run(tcb.gatt_status, tcb.peer_bda, tcb.sirk_type, tcb.sirk);

      break;
    }
    case GATT_UUID_CLIENT_SUP_FEAT:
      /*We don't need callback data anymore */
      iter->second.pop_front();

      if (status != GATT_SUCCESS) {
        log::info("Client supported features charcteristic not found");
        return;
      }

      /* Write our client supported features to the remote device */
      gatt_att_write_cl_supp_feat(conn_id, p_data->att_value.handle);
      break;
  }
}

/*******************************************************************************
 *
 * Function         gatt_cl_start_config_ccc
 *
 * Description      Gatt profile start configure service change CCC
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_cl_start_config_ccc(tGATT_PROFILE_CLCB* p_clcb) {
  log::verbose("stage: {}", p_clcb->ccc_stage);

  switch (p_clcb->ccc_stage) {
    case GATT_SVC_CHANGED_SERVICE: /* discover GATT service */
      GATTC_Discover(p_clcb->conn_id, GATT_DISC_SRVC_BY_UUID, 0x0001, 0xffff,
                     Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER));
      break;

    case GATT_SVC_CHANGED_CHARACTERISTIC: /* discover service change char */
      GATTC_Discover(p_clcb->conn_id, GATT_DISC_CHAR, 0x0001, p_clcb->e_handle,
                     Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD));
      break;

    case GATT_SVC_CHANGED_DESCRIPTOR: /* discover service change ccc */
      GATTC_Discover(p_clcb->conn_id, GATT_DISC_CHAR_DSCPT, p_clcb->s_handle,
                     p_clcb->e_handle);
      break;

    case GATT_SVC_CHANGED_CONFIGURE_CCCD: /* write ccc */
    {
      tGATT_VALUE ccc_value;
      memset(&ccc_value, 0, sizeof(tGATT_VALUE));
      ccc_value.handle = p_clcb->s_handle;
      ccc_value.len = 2;
      ccc_value.value[0] = GATT_CLT_CONFIG_INDICATION;
      GATTC_Write(p_clcb->conn_id, GATT_WRITE, &ccc_value);

      gatt_op_cb_data cb_data;
      cb_data.cb = base::BindOnce(
          [](const RawAddress& bdaddr, uint8_t support) { return; });
      cb_data.op_uuid = GATT_UUID_GATT_SRV_CHGD;
      OngoingOps[p_clcb->conn_id].emplace_back(std::move(cb_data));

      break;
    }
  }
}

/*******************************************************************************
 *
 * Function         GATT_ConfigServiceChangeCCC
 *
 * Description      Configure service change indication on remote device
 *
 * Returns          none
 *
 ******************************************************************************/
void GATT_ConfigServiceChangeCCC(const RawAddress& remote_bda, bool enable,
                                 tBT_TRANSPORT transport) {
  tGATT_PROFILE_CLCB* p_clcb =
      gatt_profile_find_clcb_by_bd_addr(remote_bda, transport);

  if (p_clcb == NULL)
    p_clcb = gatt_profile_clcb_alloc(0, remote_bda, transport);

  if (p_clcb == NULL) return;

  if (GATT_GetConnIdIfConnected(gatt_cb.gatt_if, remote_bda, &p_clcb->conn_id,
                                transport)) {
    p_clcb->connected = true;
  }
  /* hold the link here */
  GATT_Connect(gatt_cb.gatt_if, remote_bda, BTM_BLE_DIRECT_CONNECTION,
               transport, true);
  p_clcb->ccc_stage = GATT_SVC_CHANGED_CONNECTING;

  if (!p_clcb->connected) {
    /* wait for connection */
    return;
  }

  p_clcb->ccc_stage++;
  gatt_cl_start_config_ccc(p_clcb);
}

/*******************************************************************************
 *
 * Function         gatt_cl_init_sr_status
 *
 * Description      Restore status for trusted GATT Server device
 *
 * Returns          none
 *
 ******************************************************************************/
void gatt_cl_init_sr_status(tGATT_TCB& tcb) {
  tcb.sr_supp_feat = btif_storage_get_sr_supp_feat(tcb.peer_bda);

  if (tcb.sr_supp_feat & BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK)
    bluetooth::eatt::EattExtension::AddFromStorage(tcb.peer_bda);
}

static bool read_sr_supported_feat_req(
    uint16_t conn_id, base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
  tGATT_READ_PARAM param = {};

  param.service.s_handle = 1;
  param.service.e_handle = 0xFFFF;
  param.service.auth_req = 0;

  param.service.uuid = bluetooth::Uuid::From16Bit(GATT_UUID_SERVER_SUP_FEAT);

  if (GATTC_Read(conn_id, GATT_READ_BY_TYPE, &param) != GATT_SUCCESS) {
    log::error("Read GATT Support features GATT_Read Failed");
    return false;
  }

  gatt_op_cb_data cb_data;

  cb_data.cb = std::move(cb);
  cb_data.op_uuid = GATT_UUID_SERVER_SUP_FEAT;
  OngoingOps[conn_id].emplace_back(std::move(cb_data));

  return true;
}

static bool read_sr_sirk_req(
    uint16_t conn_id,
    base::OnceCallback<void(tGATT_STATUS status, const RawAddress&,
                            uint8_t sirk_type, Octet16& sirk)>
        cb) {
  tGATT_READ_PARAM param = {};

  param.service.s_handle = 1;
  param.service.e_handle = 0xFFFF;
  param.service.auth_req = 0;

  param.service.uuid = bluetooth::Uuid::From16Bit(GATT_UUID_CSIS_SIRK);

  if (GATTC_Read(conn_id, GATT_READ_BY_TYPE, &param) != GATT_SUCCESS) {
    log::error("Read GATT Support features GATT_Read Failed, conn_id: {}",
               static_cast<int>(conn_id));
    return false;
  }

  gatt_op_cb_data cb_data;

  cb_data.sirk_cb = std::move(cb);
  cb_data.op_uuid = GATT_UUID_CSIS_SIRK;
  OngoingOps[conn_id].emplace_back(std::move(cb_data));

  return true;
}

/*******************************************************************************
 *
 * Function         gatt_cl_read_sr_supp_feat_req
 *
 * Description      Read remote device supported GATT feature mask.
 *
 * Returns          bool
 *
 ******************************************************************************/
bool gatt_cl_read_sr_supp_feat_req(
    const RawAddress& peer_bda,
    base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
  tGATT_PROFILE_CLCB* p_clcb;
  uint16_t conn_id;

  if (!cb) return false;

  log::verbose("BDA: {} read gatt supported features",
               ADDRESS_TO_LOGGABLE_STR(peer_bda));

  GATT_GetConnIdIfConnected(gatt_cb.gatt_if, peer_bda, &conn_id,
                            BT_TRANSPORT_LE);
  if (conn_id == GATT_INVALID_CONN_ID) return false;

  p_clcb = gatt_profile_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) {
    p_clcb = gatt_profile_clcb_alloc(conn_id, peer_bda, BT_TRANSPORT_LE);
  }

  if (!p_clcb) {
    log::verbose("p_clcb is NULL {}", loghex(conn_id));
    return false;
  }

  auto it = OngoingOps.find(conn_id);
  if (it == OngoingOps.end()) {
    OngoingOps[conn_id] = std::deque<gatt_op_cb_data>();
  }

  return read_sr_supported_feat_req(conn_id, std::move(cb));
}

/*******************************************************************************
 *
 * Function         gatt_cl_read_sirk_req
 *
 * Description      Read remote SIRK if it's a set member device.
 *
 * Returns          bool
 *
 ******************************************************************************/
bool gatt_cl_read_sirk_req(
    const RawAddress& peer_bda,
    base::OnceCallback<void(tGATT_STATUS status, const RawAddress&,
                            uint8_t sirk_type, Octet16& sirk)>
        cb) {
  tGATT_PROFILE_CLCB* p_clcb;
  uint16_t conn_id;

  if (!cb) return false;

  log::debug("BDA: {}, read SIRK", ADDRESS_TO_LOGGABLE_CSTR(peer_bda));

  GATT_GetConnIdIfConnected(gatt_cb.gatt_if, peer_bda, &conn_id,
                            BT_TRANSPORT_LE);
  if (conn_id == GATT_INVALID_CONN_ID) return false;

  p_clcb = gatt_profile_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) {
    p_clcb = gatt_profile_clcb_alloc(conn_id, peer_bda, BT_TRANSPORT_LE);
  }

  if (!p_clcb) {
    log::verbose("p_clcb is NULL, conn_id: {:04x}", conn_id);
    return false;
  }

  auto it = OngoingOps.find(conn_id);

  if (it == OngoingOps.end()) {
    OngoingOps[conn_id] = std::deque<gatt_op_cb_data>();
  }

  return read_sr_sirk_req(conn_id, std::move(cb));
}

/*******************************************************************************
 *
 * Function         gatt_profile_get_eatt_support
 *
 * Description      Check if EATT is supported with remote device.
 *
 * Returns          if EATT is supported.
 *
 ******************************************************************************/
bool gatt_profile_get_eatt_support(const RawAddress& remote_bda) {
  uint16_t conn_id;

  log::verbose("BDA: {} read GATT support",
               ADDRESS_TO_LOGGABLE_STR(remote_bda));

  GATT_GetConnIdIfConnected(gatt_cb.gatt_if, remote_bda, &conn_id,
                            BT_TRANSPORT_LE);

  /* This read is important only when connected */
  if (conn_id == GATT_INVALID_CONN_ID) return false;

  /* Get tcb info */
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB& tcb = gatt_cb.tcb[tcb_idx];
  return tcb.sr_supp_feat & BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK;
}

/*******************************************************************************
 *
 * Function         gatt_cl_is_robust_caching_enabled
 *
 * Description      Check if Robust Caching is enabled on client side.
 *
 * Returns          true if enabled in gd flag, otherwise false
 *
 ******************************************************************************/
static bool gatt_cl_is_robust_caching_enabled() {
  return bluetooth::common::init_flags::gatt_robust_caching_client_is_enabled();
}

/*******************************************************************************
 *
 * Function         gatt_sr_is_robust_caching_enabled
 *
 * Description      Check if Robust Caching is enabled on server side.
 *
 * Returns          true if enabled in gd flag, otherwise false
 *
 ******************************************************************************/
static bool gatt_sr_is_robust_caching_enabled() {
  return bluetooth::common::init_flags::gatt_robust_caching_server_is_enabled();
}

/*******************************************************************************
 *
 * Function         gatt_sr_is_cl_robust_caching_supported
 *
 * Description      Check if Robust Caching is supported for the connection
 *
 * Returns          true if enabled by client side, otherwise false
 *
 ******************************************************************************/
static bool gatt_sr_is_cl_robust_caching_supported(tGATT_TCB& tcb) {
  // if robust caching is not enabled, should always return false
  if (!gatt_sr_is_robust_caching_enabled()) return false;
  return (tcb.cl_supp_feat & BLE_GATT_CL_SUP_FEAT_CACHING_BITMASK);
}

/*******************************************************************************
 *
 * Function         gatt_sr_is_cl_multi_variable_len_notif_supported
 *
 * Description      Check if Multiple Variable Length Notifications
 *                  supported for the connection
 *
 * Returns          true if enabled by client side, otherwise false
 *
 ******************************************************************************/
bool gatt_sr_is_cl_multi_variable_len_notif_supported(tGATT_TCB& tcb) {
  return (tcb.cl_supp_feat & BLE_GATT_CL_SUP_FEAT_MULTI_NOTIF_BITMASK);
}

/*******************************************************************************
 *
 * Function         gatt_sr_is_cl_change_aware
 *
 * Description      Check if the connection is change-aware
 *
 * Returns          true if change aware, otherwise false
 *
 ******************************************************************************/
bool gatt_sr_is_cl_change_aware(tGATT_TCB& tcb) {
  // if robust caching is not supported, should always return true by default
  if (!gatt_sr_is_cl_robust_caching_supported(tcb)) return true;
  return tcb.is_robust_cache_change_aware;
}

/*******************************************************************************
 *
 * Function         gatt_sr_init_cl_status
 *
 * Description      Restore status for trusted device
 *
 * Returns          none
 *
 ******************************************************************************/
void gatt_sr_init_cl_status(tGATT_TCB& tcb) {
  tcb.cl_supp_feat = btif_storage_get_gatt_cl_supp_feat(tcb.peer_bda);
  // This is used to reset bit when robust caching is disabled
  if (!gatt_sr_is_robust_caching_enabled()) {
    tcb.cl_supp_feat &= ~BLE_GATT_CL_SUP_FEAT_CACHING_BITMASK;
  }

  if (gatt_sr_is_cl_robust_caching_supported(tcb)) {
    Octet16 stored_hash = btif_storage_get_gatt_cl_db_hash(tcb.peer_bda);
    tcb.is_robust_cache_change_aware = (stored_hash == gatt_cb.database_hash);
  } else {
    // set default value for untrusted device
    tcb.is_robust_cache_change_aware = true;
  }

  log::info("bda={}, cl_supp_feat={}, aware={}",
            ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda), loghex(tcb.cl_supp_feat),
            tcb.is_robust_cache_change_aware);
}

/*******************************************************************************
 *
 * Function         gatt_sr_update_cl_status
 *
 * Description      Update change-aware status for the remote device
 *
 * Returns          none
 *
 ******************************************************************************/
void gatt_sr_update_cl_status(tGATT_TCB& tcb, bool chg_aware) {
  // if robust caching is not supported, do nothing
  if (!gatt_sr_is_cl_robust_caching_supported(tcb)) return;

  // only when client status is changed from change-unaware to change-aware, we
  // can then store database hash into btif_storage
  if (!tcb.is_robust_cache_change_aware && chg_aware) {
    btif_storage_set_gatt_cl_db_hash(tcb.peer_bda, gatt_cb.database_hash);
  }

  // only when the status is changed, print the log
  if (tcb.is_robust_cache_change_aware != chg_aware) {
    log::info("bda={}, chg_aware={}", ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda),
              chg_aware);
  }

  tcb.is_robust_cache_change_aware = chg_aware;
}

/* handle request for reading database hash */
static tGATT_STATUS gatt_sr_read_db_hash(uint16_t conn_id,
                                         tGATT_VALUE* p_value) {
  log::info("conn_id={}", loghex(conn_id));

  uint8_t* p = p_value->value;
  Octet16& db_hash = gatt_cb.database_hash;
  ARRAY_TO_STREAM(p, db_hash.data(), (uint16_t)db_hash.size());
  p_value->len = (uint16_t)db_hash.size();

  // Every time when database hash is requested, reset flag.
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  gatt_sr_update_cl_status(gatt_cb.tcb[tcb_idx], /* chg_aware= */ true);
  return GATT_SUCCESS;
}

/* handle request for reading client supported features */
static tGATT_STATUS gatt_sr_read_cl_supp_feat(uint16_t conn_id,
                                              tGATT_VALUE* p_value) {
  // Get tcb info
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB& tcb = gatt_cb.tcb[tcb_idx];

  uint8_t* p = p_value->value;
  UINT8_TO_STREAM(p, tcb.cl_supp_feat);
  p_value->len = 1;

  return GATT_SUCCESS;
}

/* handle request for writing client supported features */
static tGATT_STATUS gatt_sr_write_cl_supp_feat(uint16_t conn_id,
                                               tGATT_WRITE_REQ* p_data) {
  std::list<uint8_t> tmp;
  uint16_t len = p_data->len;
  uint8_t value, *p = p_data->value;
  // Read all octets into list
  while (len > 0) {
    STREAM_TO_UINT8(value, p);
    tmp.push_back(value);
    len--;
  }
  // Remove trailing zero octets
  while (!tmp.empty()) {
    if (tmp.back() != 0x00) break;
    tmp.pop_back();
  }

  // Get tcb info
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB& tcb = gatt_cb.tcb[tcb_idx];

  std::list<uint8_t> feature_list;
  feature_list.push_back(tcb.cl_supp_feat);

  // If input length is zero, return value_not_allowed
  if (tmp.empty()) {
    log::info("zero length, conn_id={}, bda={}", loghex(conn_id),
              ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda));
    return GATT_VALUE_NOT_ALLOWED;
  }
  // if original length is longer than new one, it must be the bit reset case.
  if (feature_list.size() > tmp.size()) {
    log::info("shorter length, conn_id={}, bda={}", loghex(conn_id),
              ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda));
    return GATT_VALUE_NOT_ALLOWED;
  }
  // new length is longer or equals to the original, need to check bits
  // one by one. Here we use bit-wise operation.
  // 1. Use XOR to locate the change bit, val_xor is the change bit mask
  // 2. Use AND for val_xor and *it_new to get val_and
  // 3. If val_and != val_xor, it means the change is from 1 to 0
  auto it_old = feature_list.cbegin();
  auto it_new = tmp.cbegin();
  for (; it_old != feature_list.cend(); it_old++, it_new++) {
    uint8_t val_xor = *it_old ^ *it_new;
    uint8_t val_and = val_xor & *it_new;
    if (val_and != val_xor) {
      log::info("bit cannot be reset, conn_id={}, bda={}", loghex(conn_id),
                ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda));
      return GATT_VALUE_NOT_ALLOWED;
    }
  }

  // get current robust caching status before setting new one
  bool curr_caching_state = gatt_sr_is_cl_robust_caching_supported(tcb);

  tcb.cl_supp_feat = tmp.front();
  if (!gatt_sr_is_robust_caching_enabled()) {
    // remove robust caching bit
    tcb.cl_supp_feat &= ~BLE_GATT_CL_SUP_FEAT_CACHING_BITMASK;
    log::info("reset robust caching bit, conn_id={}, bda={}", loghex(conn_id),
              ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda));
  }
  // TODO(hylo): save data as byte array
  btif_storage_set_gatt_cl_supp_feat(tcb.peer_bda, tcb.cl_supp_feat);

  // get new robust caching status after setting new one
  bool new_caching_state = gatt_sr_is_cl_robust_caching_supported(tcb);
  // only when the first time robust caching request, print the log
  if (!curr_caching_state && new_caching_state) {
    log::info("robust caching enabled by client, conn_id={}", loghex(conn_id));
  }

  return GATT_SUCCESS;
}
