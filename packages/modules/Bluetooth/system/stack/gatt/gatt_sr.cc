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
 *  this file contains the GATT server functions
 *
 ******************************************************************************/
#include <base/logging.h>
#include <bluetooth/log.h>
#include <string.h>

#include <algorithm>

#include "gatt_int.h"
#include "hardware/bt_gatt_types.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "l2c_api.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/arbiter/acl_arbiter.h"
#include "stack/eatt/eatt.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "types/bluetooth/uuid.h"

#define GATT_MTU_REQ_MIN_LEN 2
#define L2CAP_PKT_OVERHEAD 4

using bluetooth::Uuid;
using bluetooth::eatt::EattExtension;
using bluetooth::eatt::EattChannel;
using namespace bluetooth;

/*******************************************************************************
 *
 * Function         gatt_sr_enqueue_cmd
 *
 * Description      This function enqueue the request from client which needs a
 *                  application response, and update the transaction ID.
 *
 * Returns          void
 *
 ******************************************************************************/
uint32_t gatt_sr_enqueue_cmd(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
                             uint16_t handle) {
  tGATT_SR_CMD* p_cmd;

  if (cid == tcb.att_lcid) {
    p_cmd = &tcb.sr_cmd;
  } else {
    EattChannel* channel =
        EattExtension::GetInstance()->FindEattChannelByCid(tcb.peer_bda, cid);
    if (channel == nullptr) {
      log::warn("{}, cid 0x{:02x} already disconnected",
                ADDRESS_TO_LOGGABLE_CSTR(tcb.peer_bda), cid);
      return 0;
    }

    p_cmd = &channel->server_outstanding_cmd_;
  }

  uint32_t trans_id = 0;

  p_cmd->cid = cid;

  if ((p_cmd->op_code == 0) ||
      (op_code == GATT_HANDLE_VALUE_CONF)) /* no pending request */
  {
    if (op_code == GATT_CMD_WRITE || op_code == GATT_SIGN_CMD_WRITE ||
        op_code == GATT_REQ_MTU || op_code == GATT_HANDLE_VALUE_CONF) {
      trans_id = ++tcb.trans_id;
    } else {
      p_cmd->trans_id = ++tcb.trans_id;
      p_cmd->op_code = op_code;
      p_cmd->handle = handle;
      p_cmd->status = GATT_NOT_FOUND;
      tcb.trans_id %= GATT_TRANS_ID_MAX;
      trans_id = p_cmd->trans_id;
    }
  }

  return trans_id;
}

/*******************************************************************************
 *
 * Function         gatt_sr_cmd_empty
 *
 * Description      This function checks if the server command queue is empty.
 *
 * Returns          true if empty, false if there is pending command.
 *
 ******************************************************************************/
bool gatt_sr_cmd_empty(tGATT_TCB& tcb, uint16_t cid) {
  if (cid == tcb.att_lcid) return (tcb.sr_cmd.op_code == 0);

  EattChannel* channel =
      EattExtension::GetInstance()->FindEattChannelByCid(tcb.peer_bda, cid);
  if (channel == nullptr) {
    log::warn("{}, cid 0x{:02x} already disconnected",
              ADDRESS_TO_LOGGABLE_CSTR(tcb.peer_bda), cid);
    return false;
  }

  return (channel->server_outstanding_cmd_.op_code == 0);
}

/*******************************************************************************
 *
 * Function         gatt_dequeue_sr_cmd
 *
 * Description      This function dequeue the request from command queue.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_dequeue_sr_cmd(tGATT_TCB& tcb, uint16_t cid) {
  tGATT_SR_CMD* p_cmd;

  if (cid == tcb.att_lcid) {
    p_cmd = &tcb.sr_cmd;
  } else {
    EattChannel* channel =
        EattExtension::GetInstance()->FindEattChannelByCid(tcb.peer_bda, cid);
    if (channel == nullptr) {
      log::warn("{}, cid 0x{:02x} already disconnected",
                ADDRESS_TO_LOGGABLE_CSTR(tcb.peer_bda), cid);
      return;
    }

    p_cmd = &channel->server_outstanding_cmd_;
  }

  /* Double check in case any buffers are queued */
  log::verbose("gatt_dequeue_sr_cmd cid: {}", loghex(cid));
  if (p_cmd->p_rsp_msg)
    log::error("free tcb.sr_cmd.p_rsp_msg = {}", fmt::ptr(p_cmd->p_rsp_msg));
  osi_free_and_reset((void**)&p_cmd->p_rsp_msg);

  while (!fixed_queue_is_empty(p_cmd->multi_rsp_q))
    osi_free(fixed_queue_try_dequeue(p_cmd->multi_rsp_q));
  fixed_queue_free(p_cmd->multi_rsp_q, NULL);
  memset(p_cmd, 0, sizeof(tGATT_SR_CMD));
}

static void build_read_multi_rsp(tGATT_SR_CMD* p_cmd, uint16_t mtu) {
  uint16_t ii;
  size_t total_len, len;
  uint8_t* p;
  bool is_overflow = false;

  // We need at least one extra byte for the opcode
  if (mtu == 0) {
    LOG(ERROR) << "Invalid MTU";
    p_cmd->status = GATT_ILLEGAL_PARAMETER;
    return;
  }

  len = sizeof(BT_HDR) + L2CAP_MIN_OFFSET + mtu;
  BT_HDR* p_buf = (BT_HDR*)osi_calloc(len);
  p_buf->offset = L2CAP_MIN_OFFSET;
  p = (uint8_t*)(p_buf + 1) + p_buf->offset;

  /* First byte in the response is the opcode */
  if (p_cmd->multi_req.variable_len)
    *p++ = GATT_RSP_READ_MULTI_VAR;
  else
    *p++ = GATT_RSP_READ_MULTI;

  p_buf->len = 1;

  /* Now walk through the buffers putting the data into the response in order
   */
  list_t* list = NULL;
  const list_node_t* node = NULL;
  if (!fixed_queue_is_empty(p_cmd->multi_rsp_q))
    list = fixed_queue_get_list(p_cmd->multi_rsp_q);
  for (ii = 0; ii < p_cmd->multi_req.num_handles; ii++) {
    tGATTS_RSP* p_rsp = NULL;

    if (list != NULL) {
      if (ii == 0)
        node = list_begin(list);
      else
        node = list_next(node);
      if (node != list_end(list)) p_rsp = (tGATTS_RSP*)list_node(node);
    }

    if (p_rsp != NULL) {
      total_len = p_buf->len;
      if (p_cmd->multi_req.variable_len) {
        total_len += 2;
      }

      if (total_len > mtu) {
        log::verbose("Buffer space not enough for this data item, skipping");
        break;
      }

      len = std::min((size_t) p_rsp->attr_value.len, mtu - total_len);

      if (total_len == mtu && p_rsp->attr_value.len > 0) {
        log::verbose("Buffer space not enough for this data item, skipping");
        break;
      }

      if (len < p_rsp->attr_value.len) {
        is_overflow = true;
        log::verbose("multi read overflow available len={} val_len={}", len,
                     p_rsp->attr_value.len);
      }

      if (p_cmd->multi_req.variable_len) {
        UINT16_TO_STREAM(p, (uint16_t) len);
        p_buf->len += 2;
      }

      if (p_rsp->attr_value.handle == p_cmd->multi_req.handles[ii]) {
        ARRAY_TO_STREAM(p, p_rsp->attr_value.value, (uint16_t) len);
        p_buf->len += (uint16_t) len;
      } else {
        p_cmd->status = GATT_NOT_FOUND;
        break;
      }

      if (is_overflow) break;

    } else {
      p_cmd->status = GATT_NOT_FOUND;
      break;
    }

  } /* loop through all handles*/

  /* Sanity check on the buffer length */
  if (p_buf->len == 0) {
    log::error("nothing found!!");
    p_cmd->status = GATT_NOT_FOUND;
    osi_free(p_buf);
    log::verbose("osi_free(p_buf)");
  } else if (p_cmd->p_rsp_msg != NULL) {
    osi_free(p_buf);
  } else {
    p_cmd->p_rsp_msg = p_buf;
  }
}

/*******************************************************************************
 *
 * Function         process_read_multi_rsp
 *
 * Description      This function check the read multiple response.
 *
 * Returns          bool    if all replies have been received
 *
 ******************************************************************************/
static bool process_read_multi_rsp(tGATT_SR_CMD* p_cmd, tGATT_STATUS status,
                                   tGATTS_RSP* p_msg, uint16_t mtu) {
  log::verbose("status={} mtu={}", status, mtu);

  if (p_cmd->multi_rsp_q == NULL)
    p_cmd->multi_rsp_q = fixed_queue_new(SIZE_MAX);

  /* Enqueue the response */
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(sizeof(tGATTS_RSP));
  memcpy((void*)p_buf, (const void*)p_msg, sizeof(tGATTS_RSP));
  fixed_queue_enqueue(p_cmd->multi_rsp_q, p_buf);

  p_cmd->status = status;
  if (status == GATT_SUCCESS) {
    log::verbose("Multi read count={} num_hdls={} variable={}",
                 fixed_queue_length(p_cmd->multi_rsp_q),
                 p_cmd->multi_req.num_handles, p_cmd->multi_req.variable_len);
    /* Wait till we get all the responses */
    if (fixed_queue_length(p_cmd->multi_rsp_q) ==
        p_cmd->multi_req.num_handles) {
      build_read_multi_rsp(p_cmd, mtu);
      return (true);
    }
  } else /* any handle read exception occurs, return error */
  {
    return (true);
  }

  /* If here, still waiting */
  return (false);
}

/*******************************************************************************
 *
 * Function         gatt_sr_process_app_rsp
 *
 * Description      This function checks whether the response message from
 *                  application matches any pending request.
 *
 * Returns          void
 *
 ******************************************************************************/
tGATT_STATUS gatt_sr_process_app_rsp(tGATT_TCB& tcb, tGATT_IF gatt_if,
                                     UNUSED_ATTR uint32_t trans_id,
                                     uint8_t op_code, tGATT_STATUS status,
                                     tGATTS_RSP* p_msg,
                                     tGATT_SR_CMD* sr_res_p) {
  tGATT_STATUS ret_code = GATT_SUCCESS;
  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, sr_res_p->cid);

  log::verbose("gatt_if={}", gatt_if);

  gatt_sr_update_cback_cnt(tcb, sr_res_p->cid, gatt_if, false, false);

  if ((op_code == GATT_REQ_READ_MULTI) ||
      (op_code == GATT_REQ_READ_MULTI_VAR)) {
    /* If no error and still waiting, just return */
    if (!process_read_multi_rsp(sr_res_p, status, p_msg, payload_size))
      return (GATT_SUCCESS);
  } else {
    if (op_code == GATT_REQ_PREPARE_WRITE && status == GATT_SUCCESS)
      gatt_sr_update_prep_cnt(tcb, gatt_if, true, false);

    if (op_code == GATT_REQ_EXEC_WRITE && status != GATT_SUCCESS)
      gatt_sr_reset_cback_cnt(tcb, sr_res_p->cid);

    sr_res_p->status = status;

    if (gatt_sr_is_cback_cnt_zero(tcb) && status == GATT_SUCCESS) {
      if (sr_res_p->p_rsp_msg == NULL) {
        sr_res_p->p_rsp_msg = attp_build_sr_msg(
            tcb, (uint8_t)(op_code + 1), (tGATT_SR_MSG*)p_msg, payload_size);
      } else {
        log::error("Exception!!! already has respond message");
      }
    }
  }
  if (gatt_sr_is_cback_cnt_zero(tcb)) {
    if ((sr_res_p->status == GATT_SUCCESS) && (sr_res_p->p_rsp_msg)) {
      ret_code = attp_send_sr_msg(tcb, sr_res_p->cid, sr_res_p->p_rsp_msg);
      sr_res_p->p_rsp_msg = NULL;
    } else {
      ret_code = gatt_send_error_rsp(tcb, sr_res_p->cid, status, op_code,
                                     sr_res_p->handle, false);
    }

    gatt_dequeue_sr_cmd(tcb, sr_res_p->cid);
  }

  log::verbose("ret_code={}", ret_code);

  return ret_code;
}

/*******************************************************************************
 *
 * Function         gatt_process_exec_write_req
 *
 * Description      This function is called to process the execute write request
 *                  from client.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_exec_write_req(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
                                 uint16_t len, uint8_t* p_data) {
  uint8_t *p = p_data, flag, i = 0;
  uint32_t trans_id = 0;
  tGATT_IF gatt_if;
  uint16_t conn_id;

#if (GATT_CONFORMANCE_TESTING == TRUE)
  if (gatt_cb.enable_err_rsp && gatt_cb.req_op_code == op_code) {
    log::verbose(
        "Conformance tst: forced err rspv for Execute Write: error status={}",
        gatt_cb.err_status);

    gatt_send_error_rsp(tcb, cid, gatt_cb.err_status, gatt_cb.req_op_code,
                        gatt_cb.handle, false);

    return;
  }
#endif

  if (len < sizeof(flag)) {
    log::error("invalid length");
    gatt_send_error_rsp(tcb, cid, GATT_INVALID_PDU, GATT_REQ_EXEC_WRITE, 0,
                        false);
    return;
  }

  STREAM_TO_UINT8(flag, p);

  /* mask the flag */
  flag &= GATT_PREP_WRITE_EXEC;

  /* no prep write is queued */
  if (!gatt_sr_is_prep_cnt_zero(tcb)) {
    trans_id = gatt_sr_enqueue_cmd(tcb, cid, op_code, 0);
    gatt_sr_copy_prep_cnt_to_cback_cnt(tcb);

    for (i = 0; i < GATT_MAX_APPS; i++) {
      if (tcb.prep_cnt[i]) {
        gatt_if = (tGATT_IF)(i + 1);
        conn_id = GATT_CREATE_CONN_ID(tcb.tcb_idx, gatt_if);
        tGATTS_DATA gatts_data;
        gatts_data.exec_write = flag;
        gatt_sr_send_req_callback(conn_id, trans_id, GATTS_REQ_TYPE_WRITE_EXEC,
                                  &gatts_data);
        tcb.prep_cnt[i] = 0;
      }
    }
  } else /* nothing needs to be executed , send response now */
  {
    log::error("gatt_process_exec_write_req: no prepare write pending");
    gatt_send_error_rsp(tcb, cid, GATT_ERROR, GATT_REQ_EXEC_WRITE, 0, false);
  }
}

/*******************************************************************************
 *
 * Function         gatt_process_read_multi_req
 *
 * Description      This function is called to process the read multiple request
 *                  from client.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_read_multi_req(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
                                 uint16_t len, uint8_t* p_data) {
  uint32_t trans_id;
  uint16_t handle = 0, ll = len;
  uint8_t* p = p_data;
  tGATT_STATUS err = GATT_SUCCESS;
  tGATT_SEC_FLAG sec_flag;
  uint8_t key_size;

  log::verbose("");

  tGATT_READ_MULTI* multi_req = gatt_sr_get_read_multi(tcb, cid);
  if (multi_req == nullptr) {
    log::error("Could not proceed request. {}, 0x{:02x}",
               ADDRESS_TO_LOGGABLE_CSTR(tcb.peer_bda), cid);
    return;
  }
  multi_req->num_handles = 0;
  multi_req->variable_len = (op_code == GATT_REQ_READ_MULTI_VAR);
  gatt_sr_get_sec_info(tcb.peer_bda, tcb.transport, &sec_flag, &key_size);

#if (GATT_CONFORMANCE_TESTING == TRUE)
  if (gatt_cb.enable_err_rsp && gatt_cb.req_op_code == op_code) {
    log::verbose(
        "Conformance tst: forced err rspvofr ReadMultiple: error status={}",
        gatt_cb.err_status);

    STREAM_TO_UINT16(handle, p);

    gatt_send_error_rsp(tcb, cid, gatt_cb.err_status, gatt_cb.req_op_code,
                        handle, false);

    return;
  }
#endif

  while (ll >= 2 && multi_req->num_handles < GATT_MAX_READ_MULTI_HANDLES) {
    STREAM_TO_UINT16(handle, p);

    auto it = gatt_sr_find_i_rcb_by_handle(handle);
    if (it != gatt_cb.srv_list_info->end()) {
      multi_req->handles[multi_req->num_handles++] = handle;

      /* check read permission */
      err = gatts_read_attr_perm_check(it->p_db, false, handle, sec_flag,
                                       key_size);
      if (err != GATT_SUCCESS) {
        log::verbose("read permission denied : 0x{:02x}", err);
        break;
      }
    } else {
      /* invalid handle */
      err = GATT_INVALID_HANDLE;
      break;
    }
    ll -= 2;
  }

  if (ll != 0) {
    log::error("max attribute handle reached in ReadMultiple Request.");
  }

  if (multi_req->num_handles == 0) err = GATT_INVALID_HANDLE;

  if (err == GATT_SUCCESS) {
    trans_id = gatt_sr_enqueue_cmd(tcb, cid, op_code, multi_req->handles[0]);
    if (trans_id != 0) {
      tGATT_SR_CMD* sr_cmd_p = gatt_sr_get_cmd_by_cid(tcb, cid);
      if (sr_cmd_p == nullptr) {
        log::error(
            "Could not send response on CID were request arrived. {}, 0x{:02x}",
            ADDRESS_TO_LOGGABLE_CSTR(tcb.peer_bda), cid);
        return;
      }
      gatt_sr_reset_cback_cnt(tcb,
                              cid); /* read multiple use multi_rsp_q's count*/

      for (ll = 0; ll < multi_req->num_handles; ll++) {
        tGATTS_RSP* p_msg = (tGATTS_RSP*)osi_calloc(sizeof(tGATTS_RSP));
        handle = multi_req->handles[ll];
        auto it = gatt_sr_find_i_rcb_by_handle(handle);

        p_msg->attr_value.handle = handle;
        err = gatts_read_attr_value_by_handle(
            tcb, cid, it->p_db, op_code, handle, 0, p_msg->attr_value.value,
            &p_msg->attr_value.len, GATT_MAX_ATTR_LEN, sec_flag, key_size,
            trans_id);

        if (err == GATT_SUCCESS) {
          gatt_sr_process_app_rsp(tcb, it->gatt_if, trans_id, op_code,
                                  GATT_SUCCESS, p_msg, sr_cmd_p);
        }
        /* either not using or done using the buffer, release it now */
        osi_free(p_msg);
      }
    } else
      err = GATT_NO_RESOURCES;
  }

  /* in theroy BUSY is not possible(should already been checked), protected
   * check */
  if (err != GATT_SUCCESS && err != GATT_PENDING && err != GATT_BUSY)
    gatt_send_error_rsp(tcb, cid, err, op_code, handle, false);
}

/*******************************************************************************
 *
 * Function         gatt_build_primary_service_rsp
 *
 * Description      Primamry service request processed internally. Theretically
 *                  only deal with ReadByTypeVAlue and ReadByGroupType.
 *
 * Returns          void
 *
 ******************************************************************************/
static tGATT_STATUS gatt_build_primary_service_rsp(
    BT_HDR* p_msg, tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
    uint16_t s_hdl, uint16_t e_hdl, UNUSED_ATTR uint8_t* p_data,
    const Uuid& value) {
  tGATT_STATUS status = GATT_NOT_FOUND;
  uint8_t handle_len = 4;

  uint8_t* p = (uint8_t*)(p_msg + 1) + L2CAP_MIN_OFFSET;

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);

  for (tGATT_SRV_LIST_ELEM& el : *gatt_cb.srv_list_info) {
    if (el.s_hdl < s_hdl || el.s_hdl > e_hdl ||
        el.type != GATT_UUID_PRI_SERVICE) {
      continue;
    }

    Uuid* p_uuid = gatts_get_service_uuid(el.p_db);
    if (!p_uuid) continue;

    if (op_code == GATT_REQ_READ_BY_GRP_TYPE)
      handle_len = 4 + gatt_build_uuid_to_stream_len(*p_uuid);

    /* get the length byte in the repsonse */
    if (p_msg->offset == 0) {
      *p++ = op_code + 1;
      p_msg->len++;
      p_msg->offset = handle_len;

      if (op_code == GATT_REQ_READ_BY_GRP_TYPE) {
        *p++ = (uint8_t)p_msg->offset; /* length byte */
        p_msg->len++;
      }
    }

    if (p_msg->len + p_msg->offset > payload_size ||
        handle_len != p_msg->offset) {
      break;
    }

    if (op_code == GATT_REQ_FIND_TYPE_VALUE && value != *p_uuid) continue;

    UINT16_TO_STREAM(p, el.s_hdl);

    if (gatt_cb.last_service_handle &&
        gatt_cb.last_service_handle == el.s_hdl) {
      log::verbose("Use 0xFFFF for the last primary attribute");
      /* see GATT ERRATA 4065, 4063, ATT ERRATA 4062 */
      UINT16_TO_STREAM(p, 0xFFFF);
    } else {
      UINT16_TO_STREAM(p, el.e_hdl);
    }

    if (op_code == GATT_REQ_READ_BY_GRP_TYPE)
      gatt_build_uuid_to_stream(&p, *p_uuid);

    status = GATT_SUCCESS;
    p_msg->len += p_msg->offset;
  }
  p_msg->offset = L2CAP_MIN_OFFSET;

  return status;
}

/**
 * fill the find information response information in the given buffer.
 *
 * Returns          true: if data filled sucessfully.
 *                  false: packet full, or format mismatch.
 */
static tGATT_STATUS gatt_build_find_info_rsp(tGATT_SRV_LIST_ELEM& el,
                                             BT_HDR* p_msg, uint16_t& len,
                                             uint16_t s_hdl, uint16_t e_hdl) {
  uint8_t info_pair_len[2] = {4, 18};

  if (!el.p_db) return GATT_NOT_FOUND;

  /* check the attribute database */

  uint8_t* p = (uint8_t*)(p_msg + 1) + L2CAP_MIN_OFFSET + p_msg->len;

  for (auto& attr : el.p_db->attr_list) {
    if (attr.handle > e_hdl) break;

    if (attr.handle < s_hdl) continue;

    uint8_t uuid_len = attr.uuid.GetShortestRepresentationSize();
    if (p_msg->offset == 0)
      p_msg->offset = (uuid_len == Uuid::kNumBytes16) ? GATT_INFO_TYPE_PAIR_16
                                                      : GATT_INFO_TYPE_PAIR_128;

    if (len < info_pair_len[p_msg->offset - 1]) return GATT_NO_RESOURCES;

    if (p_msg->offset == GATT_INFO_TYPE_PAIR_16 &&
        uuid_len == Uuid::kNumBytes16) {
      UINT16_TO_STREAM(p, attr.handle);
      UINT16_TO_STREAM(p, attr.uuid.As16Bit());
    } else if (p_msg->offset == GATT_INFO_TYPE_PAIR_128 &&
               uuid_len == Uuid::kNumBytes128) {
      UINT16_TO_STREAM(p, attr.handle);
      ARRAY_TO_STREAM(p, attr.uuid.To128BitLE(), (int)Uuid::kNumBytes128);
    } else if (p_msg->offset == GATT_INFO_TYPE_PAIR_128 &&
               uuid_len == Uuid::kNumBytes32) {
      UINT16_TO_STREAM(p, attr.handle);
      ARRAY_TO_STREAM(p, attr.uuid.To128BitLE(), (int)Uuid::kNumBytes128);
    } else {
      log::error("format mismatch");
      return GATT_NO_RESOURCES;
      /* format mismatch */
    }
    p_msg->len += info_pair_len[p_msg->offset - 1];
    len -= info_pair_len[p_msg->offset - 1];
    return GATT_SUCCESS;
  }

  return GATT_NOT_FOUND;
}

static tGATT_STATUS read_handles(uint16_t& len, uint8_t*& p, uint16_t& s_hdl,
                                 uint16_t& e_hdl) {
  if (len < 4) return GATT_INVALID_PDU;

  /* obtain starting handle, and ending handle */
  STREAM_TO_UINT16(s_hdl, p);
  STREAM_TO_UINT16(e_hdl, p);
  len -= 4;

  if (s_hdl > e_hdl || !GATT_HANDLE_IS_VALID(s_hdl) ||
      !GATT_HANDLE_IS_VALID(e_hdl)) {
    return GATT_INVALID_HANDLE;
  }

  return GATT_SUCCESS;
}

static tGATT_STATUS gatts_validate_packet_format(uint8_t op_code, uint16_t& len,
                                                 uint8_t*& p, Uuid* p_uuid,
                                                 uint16_t& s_hdl,
                                                 uint16_t& e_hdl) {
  tGATT_STATUS ret = read_handles(len, p, s_hdl, e_hdl);
  if (ret != GATT_SUCCESS) return ret;

  if (len < 2) return GATT_INVALID_PDU;

  /* parse uuid now */
  CHECK(p_uuid);
  uint16_t uuid_len = (op_code == GATT_REQ_FIND_TYPE_VALUE) ? 2 : len;
  if (!gatt_parse_uuid_from_cmd(p_uuid, uuid_len, &p)) {
    log::verbose("Bad UUID");
    return GATT_INVALID_PDU;
  }

  len -= uuid_len;
  return GATT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         gatts_process_primary_service_req
 *
 * Description      Process ReadByGroupType/ReadByTypeValue request, for
 *                  discovering all primary services or discover primary service
 *                  by UUID request.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatts_process_primary_service_req(tGATT_TCB& tcb, uint16_t cid,
                                       uint8_t op_code, uint16_t len,
                                       uint8_t* p_data) {
  uint16_t s_hdl = 0, e_hdl = 0;
  Uuid uuid = Uuid::kEmpty;

  uint8_t reason =
      gatts_validate_packet_format(op_code, len, p_data, &uuid, s_hdl, e_hdl);
  if (reason != GATT_SUCCESS) {
    gatt_send_error_rsp(tcb, cid, reason, op_code, s_hdl, false);
    return;
  }

  if (uuid != Uuid::From16Bit(GATT_UUID_PRI_SERVICE)) {
    if (op_code == GATT_REQ_READ_BY_GRP_TYPE) {
      gatt_send_error_rsp(tcb, cid, GATT_UNSUPPORT_GRP_TYPE, op_code, s_hdl,
                          false);
      log::verbose("unexpected ReadByGrpType Group: {}", uuid.ToString());
      return;
    }

    // we do not support ReadByTypeValue with any non-primamry_service type
    gatt_send_error_rsp(tcb, cid, GATT_NOT_FOUND, op_code, s_hdl, false);
    log::verbose("unexpected ReadByTypeValue type: {}", uuid.ToString());
    return;
  }

  // TODO: we assume theh value is UUID, there is no such requirement in spec
  Uuid value = Uuid::kEmpty;
  if (op_code == GATT_REQ_FIND_TYPE_VALUE) {
    if (!gatt_parse_uuid_from_cmd(&value, len, &p_data)) {
      gatt_send_error_rsp(tcb, cid, GATT_INVALID_PDU, op_code, s_hdl, false);
    }
  }

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);

  // This can happen if the channel is already closed.
  if (payload_size == 0) {
    return;
  }

  uint16_t msg_len =
      (uint16_t)(sizeof(BT_HDR) + payload_size + L2CAP_MIN_OFFSET);
  BT_HDR* p_msg = (BT_HDR*)osi_calloc(msg_len);
  reason = gatt_build_primary_service_rsp(p_msg, tcb, cid, op_code, s_hdl,
                                          e_hdl, p_data, value);
  if (reason != GATT_SUCCESS) {
    osi_free(p_msg);
    gatt_send_error_rsp(tcb, cid, reason, op_code, s_hdl, false);
    return;
  }

  attp_send_sr_msg(tcb, cid, p_msg);
}

/*******************************************************************************
 *
 * Function         gatts_process_find_info
 *
 * Description      process find information request, for discover character
 *                  descriptors.
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatts_process_find_info(tGATT_TCB& tcb, uint16_t cid,
                                    uint8_t op_code, uint16_t len,
                                    uint8_t* p_data) {
  uint16_t s_hdl = 0, e_hdl = 0;
  uint8_t reason = read_handles(len, p_data, s_hdl, e_hdl);
  if (reason != GATT_SUCCESS) {
    gatt_send_error_rsp(tcb, cid, reason, op_code, s_hdl, false);
    return;
  }

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);

  // This can happen if the channel is already closed.
  if (payload_size == 0) {
    return;
  }

  uint16_t buf_len =
      (uint16_t)(sizeof(BT_HDR) + payload_size + L2CAP_MIN_OFFSET);

  BT_HDR* p_msg = (BT_HDR*)osi_calloc(buf_len);
  reason = GATT_NOT_FOUND;

  uint8_t* p = (uint8_t*)(p_msg + 1) + L2CAP_MIN_OFFSET;
  *p++ = op_code + 1;
  p_msg->len = 2;

  buf_len = payload_size - 2;

  for (tGATT_SRV_LIST_ELEM& el : *gatt_cb.srv_list_info) {
    if (el.s_hdl <= e_hdl && el.e_hdl >= s_hdl) {
      reason = gatt_build_find_info_rsp(el, p_msg, buf_len, s_hdl, e_hdl);
      if (reason == GATT_NO_RESOURCES) {
        reason = GATT_SUCCESS;
        break;
      }
    }
  }

  *p = (uint8_t)p_msg->offset;

  p_msg->offset = L2CAP_MIN_OFFSET;

  if (reason != GATT_SUCCESS) {
    osi_free(p_msg);
    gatt_send_error_rsp(tcb, cid, reason, op_code, s_hdl, false);
  } else
    attp_send_sr_msg(tcb, cid, p_msg);
}

/*******************************************************************************
 *
 * Function         gatts_process_mtu_req
 *
 * Description      This function is called to process excahnge MTU request.
 *                  Only used on LE.
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatts_process_mtu_req(tGATT_TCB& tcb, uint16_t cid, uint16_t len,
                                  uint8_t* p_data) {
  /* BR/EDR conenction, send error response */
  if (cid != L2CAP_ATT_CID) {
    gatt_send_error_rsp(tcb, cid, GATT_REQ_NOT_SUPPORTED, GATT_REQ_MTU, 0,
                        false);
    return;
  }

  if (len < GATT_MTU_REQ_MIN_LEN) {
    log::error("invalid MTU request PDU received.");
    gatt_send_error_rsp(tcb, cid, GATT_INVALID_PDU, GATT_REQ_MTU, 0, false);
    return;
  }

  tGATT_SR_MSG gatt_sr_msg;

  uint16_t mtu = 0;
  uint8_t* p = p_data;
  STREAM_TO_UINT16(mtu, p);
  /* mtu must be greater than default MTU which is 23/48 */
  if (mtu < GATT_DEF_BLE_MTU_SIZE) {
    tcb.payload_size = GATT_DEF_BLE_MTU_SIZE;
  } else {
    tcb.payload_size = std::min(mtu, (uint16_t)(gatt_get_local_mtu()));
  }

  /* Always say to remote our default MTU. */
  gatt_sr_msg.mtu = gatt_get_local_mtu();

  log::info("MTU {} request from remote ({}), resulted MTU {}", mtu,
            tcb.peer_bda.ToString(), tcb.payload_size);

  BTM_SetBleDataLength(tcb.peer_bda, tcb.payload_size + L2CAP_PKT_OVERHEAD);

  BT_HDR* p_buf =
      attp_build_sr_msg(tcb, GATT_RSP_MTU, &gatt_sr_msg, GATT_DEF_BLE_MTU_SIZE);
  attp_send_sr_msg(tcb, cid, p_buf);

  bluetooth::shim::arbiter::GetArbiter().OnIncomingMtuReq(tcb.tcb_idx,
                                                          tcb.payload_size);

  tGATTS_DATA gatts_data;
  gatts_data.mtu = tcb.payload_size;
  /* Notify all registered applicaiton with new MTU size. Us a transaction ID */
  /* of 0, as no response is allowed from applcations                    */
  for (int i = 0; i < GATT_MAX_APPS; i++) {
    if (gatt_cb.cl_rcb[i].in_use) {
      uint16_t conn_id =
          GATT_CREATE_CONN_ID(tcb.tcb_idx, gatt_cb.cl_rcb[i].gatt_if);
      gatt_sr_send_req_callback(conn_id, 0, GATTS_REQ_TYPE_MTU, &gatts_data);
    }
  }
}

/*******************************************************************************
 *
 * Function         gatts_process_read_by_type_req
 *
 * Description      process Read By type request.
 *                  This PDU can be used to perform:
 *                  - read characteristic value
 *                  - read characteristic descriptor value
 *                  - discover characteristic
 *                  - discover characteristic by UUID
 *                  - relationship discovery
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatts_process_read_by_type_req(tGATT_TCB& tcb, uint16_t cid,
                                           uint8_t op_code, uint16_t len,
                                           uint8_t* p_data) {
  Uuid uuid = Uuid::kEmpty;
  uint16_t s_hdl = 0, e_hdl = 0, err_hdl = 0;
  tGATT_STATUS reason =
      gatts_validate_packet_format(op_code, len, p_data, &uuid, s_hdl, e_hdl);

#if (GATT_CONFORMANCE_TESTING == TRUE)
  if (gatt_cb.enable_err_rsp && gatt_cb.req_op_code == op_code) {
    log::verbose(
        "Conformance tst: forced err rsp for ReadByType: error status={}",
        gatt_cb.err_status);

    gatt_send_error_rsp(tcb, cid, gatt_cb.err_status, gatt_cb.req_op_code,
                        s_hdl, false);

    return;
  }
#endif

  if (reason != GATT_SUCCESS) {
    gatt_send_error_rsp(tcb, cid, reason, op_code, s_hdl, false);
    return;
  }

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);

  // This can happen if the channel is already closed.
  if (payload_size == 0) {
    return;
  }

  size_t msg_len = sizeof(BT_HDR) + payload_size + L2CAP_MIN_OFFSET;
  BT_HDR* p_msg = (BT_HDR*)osi_calloc(msg_len);
  uint8_t* p = (uint8_t*)(p_msg + 1) + L2CAP_MIN_OFFSET;

  *p++ = op_code + 1;
  /* reserve length byte */
  p_msg->len = 2;
  uint16_t buf_len = payload_size - 2;

  reason = GATT_NOT_FOUND;
  for (tGATT_SRV_LIST_ELEM& el : *gatt_cb.srv_list_info) {
    if (el.s_hdl <= e_hdl && el.e_hdl >= s_hdl) {
      tGATT_SEC_FLAG sec_flag;
      uint8_t key_size;
      gatt_sr_get_sec_info(tcb.peer_bda, tcb.transport, &sec_flag, &key_size);

      tGATT_STATUS ret = gatts_db_read_attr_value_by_type(
          tcb, cid, el.p_db, op_code, p_msg, s_hdl, e_hdl, uuid, &buf_len,
          sec_flag, key_size, 0, &err_hdl);
      if (ret != GATT_NOT_FOUND) {
        reason = ret;
        if (ret == GATT_NO_RESOURCES) reason = GATT_SUCCESS;
      }

      if (ret != GATT_SUCCESS && ret != GATT_NOT_FOUND) {
        s_hdl = err_hdl;
        break;
      }
    }
  }
  *p = (uint8_t)p_msg->offset;
  p_msg->offset = L2CAP_MIN_OFFSET;

  if (reason != GATT_SUCCESS) {
    osi_free(p_msg);

    /* in theroy BUSY is not possible(should already been checked), protected
     * check */
    if (reason != GATT_PENDING && reason != GATT_BUSY)
      gatt_send_error_rsp(tcb, cid, reason, op_code, s_hdl, false);

    return;
  }

  attp_send_sr_msg(tcb, cid, p_msg);
}

/**
 * This function is called to process the write request from client.
 */
static void gatts_process_write_req(tGATT_TCB& tcb, uint16_t cid,
                                    tGATT_SRV_LIST_ELEM& el, uint16_t handle,
                                    uint8_t op_code, uint16_t len,
                                    uint8_t* p_data,
                                    bt_gatt_db_attribute_type_t gatt_type) {
  tGATTS_DATA sr_data;
  uint32_t trans_id;
  tGATT_STATUS status;
  tGATT_SEC_FLAG sec_flag;
  uint8_t key_size, *p = p_data;
  uint16_t conn_id;

  memset(&sr_data, 0, sizeof(tGATTS_DATA));

  switch (op_code) {
    case GATT_REQ_PREPARE_WRITE:
      if (len < 2 || p == nullptr) {
        log::error(
            "Prepare write request was invalid - missing offset, sending error "
            "response");
        gatt_send_error_rsp(tcb, cid, GATT_INVALID_PDU, op_code, handle, false);
        return;
      }
      sr_data.write_req.is_prep = true;
      STREAM_TO_UINT16(sr_data.write_req.offset, p);
      len -= 2;
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    case GATT_SIGN_CMD_WRITE:
      if (op_code == GATT_SIGN_CMD_WRITE) {
        log::verbose("Write CMD with data sigining");
        len -= GATT_AUTH_SIGN_LEN;
      }
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    case GATT_CMD_WRITE:
    case GATT_REQ_WRITE:
      if (op_code == GATT_REQ_WRITE || op_code == GATT_REQ_PREPARE_WRITE)
        sr_data.write_req.need_rsp = true;
      sr_data.write_req.handle = handle;
      if (len > GATT_MAX_ATTR_LEN) len = GATT_MAX_ATTR_LEN;
      sr_data.write_req.len = len;
      if (len != 0 && p != nullptr) {
        memcpy(sr_data.write_req.value, p, len);
      }
      break;
  }

  gatt_sr_get_sec_info(tcb.peer_bda, tcb.transport, &sec_flag, &key_size);

  status = gatts_write_attr_perm_check(el.p_db, op_code, handle,
                                       sr_data.write_req.offset, p, len,
                                       sec_flag, key_size);

  if (status == GATT_SUCCESS) {
    trans_id = gatt_sr_enqueue_cmd(tcb, cid, op_code, handle);
    if (trans_id != 0) {
      conn_id = GATT_CREATE_CONN_ID(tcb.tcb_idx, el.gatt_if);

      uint8_t opcode = 0;
      if (gatt_type == BTGATT_DB_DESCRIPTOR) {
        opcode = GATTS_REQ_TYPE_WRITE_DESCRIPTOR;
      } else if (gatt_type == BTGATT_DB_CHARACTERISTIC) {
        opcode = GATTS_REQ_TYPE_WRITE_CHARACTERISTIC;
      } else {
        log::error(
            "Attempt to write attribute that's not tied with "
            "characteristic or descriptor value.");
        status = GATT_ERROR;
      }

      if (opcode) {
        gatt_sr_send_req_callback(conn_id, trans_id, opcode, &sr_data);
        status = GATT_PENDING;
      }
    } else {
      log::error("max pending command, send error");
      status = GATT_BUSY; /* max pending command, application error */
    }
  }

  /* in theroy BUSY is not possible(should already been checked), protected
   * check */
  if (status != GATT_PENDING && status != GATT_BUSY &&
      (op_code == GATT_REQ_PREPARE_WRITE || op_code == GATT_REQ_WRITE)) {
    gatt_send_error_rsp(tcb, cid, status, op_code, handle, false);
  }
  return;
}

/**
 * This function is called to process the read request from client.
 */
static void gatts_process_read_req(tGATT_TCB& tcb, uint16_t cid,
                                   tGATT_SRV_LIST_ELEM& el, uint8_t op_code,
                                   uint16_t handle, uint16_t len,
                                   uint8_t* p_data) {
  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);

  // This can happen if the channel is already closed.
  if (payload_size == 0) {
    return;
  }

  size_t buf_len = sizeof(BT_HDR) + payload_size + L2CAP_MIN_OFFSET;
  uint16_t offset = 0;

  if (op_code == GATT_REQ_READ_BLOB && len < sizeof(uint16_t)) {
    /* Error: packet length is too short */
    log::error("packet length={} too short. min={}", len, sizeof(uint16_t));
    gatt_send_error_rsp(tcb, cid, GATT_INVALID_PDU, op_code, 0, false);
    return;
  }

  BT_HDR* p_msg = (BT_HDR*)osi_calloc(buf_len);

  if (op_code == GATT_REQ_READ_BLOB) STREAM_TO_UINT16(offset, p_data);

  uint8_t* p = (uint8_t*)(p_msg + 1) + L2CAP_MIN_OFFSET;
  *p++ = op_code + 1;
  p_msg->len = 1;
  buf_len = payload_size - 1;

  tGATT_SEC_FLAG sec_flag;
  uint8_t key_size;
  gatt_sr_get_sec_info(tcb.peer_bda, tcb.transport, &sec_flag, &key_size);

  uint16_t value_len = 0;
  tGATT_STATUS reason = gatts_read_attr_value_by_handle(
      tcb, cid, el.p_db, op_code, handle, offset, p, &value_len,
      (uint16_t)buf_len, sec_flag, key_size, 0);
  p_msg->len += value_len;

  if (reason != GATT_SUCCESS) {
    osi_free(p_msg);

    /* in theory BUSY is not possible(should already been checked), protected
     * check */
    if (reason != GATT_PENDING && reason != GATT_BUSY)
      gatt_send_error_rsp(tcb, cid, reason, op_code, handle, false);

    return;
  }

  attp_send_sr_msg(tcb, cid, p_msg);
}

/*******************************************************************************
 *
 * Function         gatts_process_attribute_req
 *
 * Description      This function is called to process the per attribute handle
 *                  request from client.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatts_process_attribute_req(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
                                 uint16_t len, uint8_t* p_data) {
  uint16_t handle = 0;
  uint8_t* p = p_data;
  tGATT_STATUS status = GATT_INVALID_HANDLE;

  if (len < 2) {
    log::error("Illegal PDU length, discard request");
    status = GATT_INVALID_PDU;
  } else {
    STREAM_TO_UINT16(handle, p);
    len -= 2;
  }

#if (GATT_CONFORMANCE_TESTING == TRUE)
  gatt_cb.handle = handle;
  if (gatt_cb.enable_err_rsp && gatt_cb.req_op_code == op_code) {
    log::verbose("Conformance tst: forced err rsp: error status={}",
                 gatt_cb.err_status);

    gatt_send_error_rsp(tcb, cid, gatt_cb.err_status, cid, gatt_cb.req_op_code,
                        handle, false);

    return;
  }
#endif

  if (GATT_HANDLE_IS_VALID(handle)) {
    for (auto& el : *gatt_cb.srv_list_info) {
      if (el.s_hdl <= handle && el.e_hdl >= handle) {
        for (const auto& attr : el.p_db->attr_list) {
          if (attr.handle == handle) {
            switch (op_code) {
              case GATT_REQ_READ: /* read char/char descriptor value */
              case GATT_REQ_READ_BLOB:
                gatts_process_read_req(tcb, cid, el, op_code, handle, len, p);
                break;

              case GATT_REQ_WRITE: /* write char/char descriptor value */
              case GATT_CMD_WRITE:
              case GATT_SIGN_CMD_WRITE:
              case GATT_REQ_PREPARE_WRITE:
                gatts_process_write_req(tcb, cid, el, handle, op_code, len, p,
                                        attr.gatt_type);
                break;
              default:
                break;
            }
            status = GATT_SUCCESS;
            break;
          }
        }
        break;
      }
    }
  }

  if (status != GATT_SUCCESS && op_code != GATT_CMD_WRITE &&
      op_code != GATT_SIGN_CMD_WRITE)
    gatt_send_error_rsp(tcb, cid, status, op_code, handle, false);
}

/*******************************************************************************
 *
 * Function         gatts_proc_srv_chg_ind_ack
 *
 * Description      This function process the service changed indicaiton ACK
 *
 * Returns          void
 *
 ******************************************************************************/
void gatts_proc_srv_chg_ind_ack(tGATT_TCB tcb) {
  tGATTS_SRV_CHG_REQ req;
  tGATTS_SRV_CHG* p_buf = NULL;

  log::verbose("");

  p_buf = gatt_is_bda_in_the_srv_chg_clt_list(tcb.peer_bda);
  if (p_buf != NULL) {
    log::verbose("NV update set srv chg = false");
    p_buf->srv_changed = false;
    memcpy(&req.srv_chg, p_buf, sizeof(tGATTS_SRV_CHG));
    if (gatt_cb.cb_info.p_srv_chg_callback)
      (*gatt_cb.cb_info.p_srv_chg_callback)(GATTS_SRV_CHG_CMD_UPDATE_CLIENT,
                                            &req, NULL);
  }
}

/*******************************************************************************
 *
 * Function         gatts_chk_pending_ind
 *
 * Description      This function check any pending indication needs to be sent
 *                  if there is a pending indication then sent the indication
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatts_chk_pending_ind(tGATT_TCB& tcb) {
  log::verbose("");

  tGATT_VALUE* p_buf =
      (tGATT_VALUE*)fixed_queue_try_peek_first(tcb.pending_ind_q);
  if (p_buf != NULL) {
    GATTS_HandleValueIndication(p_buf->conn_id, p_buf->handle, p_buf->len,
                                p_buf->value);
    osi_free(fixed_queue_try_remove_from_queue(tcb.pending_ind_q, p_buf));
  }
}

/*******************************************************************************
 *
 * Function         gatts_proc_ind_ack
 *
 * Description      This function processes the Indication ack
 *
 * Returns          true continue to process the indication ack by the
 *                  application if the ACK is not a Service Changed Indication
 *
 ******************************************************************************/
static bool gatts_proc_ind_ack(tGATT_TCB& tcb, uint16_t ack_handle) {
  bool continue_processing = true;

  log::verbose("ack handle={}", ack_handle);

  if (ack_handle == gatt_cb.handle_of_h_r) {
    gatts_proc_srv_chg_ind_ack(tcb);
    /* there is no need to inform the application since srv chg is handled
     * internally by GATT */
    continue_processing = false;

    // After receiving ack of svc_chg_ind, reset client status
    gatt_sr_update_cl_status(tcb, /* chg_aware= */ true);
  }

  gatts_chk_pending_ind(tcb);
  return continue_processing;
}

/*******************************************************************************
 *
 * Function         gatts_process_value_conf
 *
 * Description      This function is called to process the handle value
 *                  confirmation.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatts_process_value_conf(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code) {
  uint16_t handle;

  if (!gatt_tcb_find_indicate_handle(tcb, cid, &handle)) {
    log::error("unexpected handle value confirmation");
    return;
  }

  gatt_stop_conf_timer(tcb, cid);

  bool continue_processing = gatts_proc_ind_ack(tcb, handle);

  if (continue_processing) {
    tGATTS_DATA gatts_data;
    gatts_data.handle = handle;
    for (auto& el : *gatt_cb.srv_list_info) {
      if (el.s_hdl <= handle && el.e_hdl >= handle) {
        uint32_t trans_id = gatt_sr_enqueue_cmd(tcb, cid, op_code, handle);
        uint16_t conn_id = GATT_CREATE_CONN_ID(tcb.tcb_idx, el.gatt_if);
        gatt_sr_send_req_callback(conn_id, trans_id, GATTS_REQ_TYPE_CONF,
                                  &gatts_data);
      }
    }
  }
}

static bool gatts_process_db_out_of_sync(tGATT_TCB& tcb, uint16_t cid,
                                         uint8_t op_code, uint16_t len,
                                         uint8_t* p_data) {
  if (gatt_sr_is_cl_change_aware(tcb)) return false;

  // default value
  bool should_ignore = true;
  bool should_rsp = true;

  switch (op_code) {
    case GATT_REQ_READ_BY_TYPE: {
      // Check if read database hash by UUID
      Uuid uuid = Uuid::kEmpty;
      uint16_t s_hdl = 0, e_hdl = 0;
      uint16_t db_hash_handle = gatt_cb.handle_of_database_hash;
      tGATT_STATUS reason = gatts_validate_packet_format(op_code, len, p_data,
                                                         &uuid, s_hdl, e_hdl);
      if (reason == GATT_SUCCESS &&
          (s_hdl <= db_hash_handle && db_hash_handle <= e_hdl) &&
          (uuid == Uuid::From16Bit(GATT_UUID_DATABASE_HASH)))
        should_ignore = false;

    } break;
    case GATT_REQ_READ: {
      // Check if read database hash by handle
      uint16_t handle = 0;
      uint8_t* p = p_data;
      tGATT_STATUS status = GATT_SUCCESS;

      if (len < 2) {
        status = GATT_INVALID_PDU;
      } else {
        STREAM_TO_UINT16(handle, p);
        len -= 2;
      }

      if (status == GATT_SUCCESS && handle == gatt_cb.handle_of_database_hash)
        should_ignore = false;

    } break;
    case GATT_REQ_READ_BY_GRP_TYPE: /* discover primary services */
    case GATT_REQ_FIND_TYPE_VALUE:  /* discover service by UUID */
    case GATT_REQ_FIND_INFO:        /* discover char descrptor */
    case GATT_REQ_READ_BLOB:        /* read long char */
    case GATT_REQ_READ_MULTI:       /* read multi char*/
    case GATT_REQ_WRITE:            /* write char/char descriptor value */
    case GATT_REQ_PREPARE_WRITE:    /* write long char */
      // Use default value
      break;
    case GATT_CMD_WRITE:      /* cmd */
    case GATT_SIGN_CMD_WRITE: /* sign cmd */
      should_rsp = false;
      break;
    case GATT_REQ_MTU:           /* configure mtu */
    case GATT_REQ_EXEC_WRITE:    /* execute write */
    case GATT_HANDLE_VALUE_CONF: /* confirm for indication */
    default:
      should_ignore = false;
  }

  if (should_ignore) {
    if (should_rsp) {
      gatt_send_error_rsp(tcb, cid, GATT_DATABASE_OUT_OF_SYNC, op_code, 0x0000,
                          false);
    }
    log::info("database out of sync, device={}, op_code={}, should_rsp={}",
              ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda), loghex((uint16_t)op_code),
              should_rsp);
    gatt_sr_update_cl_status(tcb, /* chg_aware= */ should_rsp);
  }

  return should_ignore;
}

/** This function is called to handle the client requests to server */
void gatt_server_handle_client_req(tGATT_TCB& tcb, uint16_t cid,
                                   uint8_t op_code, uint16_t len,
                                   uint8_t* p_data) {
  /* there is pending command, discard this one */
  if (!gatt_sr_cmd_empty(tcb, cid) && op_code != GATT_HANDLE_VALUE_CONF) return;

  /* the size of the message may not be bigger than the local max PDU size*/
  /* The message has to be smaller than the agreed MTU, len does not include op
   * code */

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);
  if (len >= payload_size) {
    log::error("server receive invalid PDU size:{} pdu size:{}", len + 1,
               payload_size);
    /* for invalid request expecting response, send it now */
    if (op_code != GATT_CMD_WRITE && op_code != GATT_SIGN_CMD_WRITE &&
        op_code != GATT_HANDLE_VALUE_CONF) {
      gatt_send_error_rsp(tcb, cid, GATT_INVALID_PDU, op_code, 0, false);
    }
    /* otherwise, ignore the pkt */
  } else {
    // handle database out of sync
    if (gatts_process_db_out_of_sync(tcb, cid, op_code, len, p_data)) return;

    switch (op_code) {
      case GATT_REQ_READ_BY_GRP_TYPE: /* discover primary services */
      case GATT_REQ_FIND_TYPE_VALUE:  /* discover service by UUID */
        gatts_process_primary_service_req(tcb, cid, op_code, len, p_data);
        break;

      case GATT_REQ_FIND_INFO: /* discover char descrptor */
        gatts_process_find_info(tcb, cid, op_code, len, p_data);
        break;

      case GATT_REQ_READ_BY_TYPE: /* read characteristic value, char descriptor
                                     value */
        /* discover characteristic, discover char by UUID */
        gatts_process_read_by_type_req(tcb, cid, op_code, len, p_data);
        break;

      case GATT_REQ_READ: /* read char/char descriptor value */
      case GATT_REQ_READ_BLOB:
      case GATT_REQ_WRITE: /* write char/char descriptor value */
      case GATT_CMD_WRITE:
      case GATT_SIGN_CMD_WRITE:
      case GATT_REQ_PREPARE_WRITE:
        gatts_process_attribute_req(tcb, cid, op_code, len, p_data);
        break;

      case GATT_HANDLE_VALUE_CONF:
        gatts_process_value_conf(tcb, cid, op_code);
        break;

      case GATT_REQ_MTU:
        gatts_process_mtu_req(tcb, cid, len, p_data);
        break;

      case GATT_REQ_EXEC_WRITE:
        gatt_process_exec_write_req(tcb, cid, op_code, len, p_data);
        break;

      case GATT_REQ_READ_MULTI:
      case GATT_REQ_READ_MULTI_VAR:
        gatt_process_read_multi_req(tcb, cid, op_code, len, p_data);
        break;

      default:
        break;
    }
  }
}
