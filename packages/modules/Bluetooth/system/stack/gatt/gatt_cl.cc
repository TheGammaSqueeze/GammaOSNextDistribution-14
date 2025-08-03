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
 *  this file contains the main GATT client functions
 *
 ******************************************************************************/

#define LOG_TAG "bluetooth"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <string.h>

#include "gatt_int.h"
#include "hardware/bt_gatt_types.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/arbiter/acl_arbiter.h"
#include "stack/eatt/eatt.h"
#include "stack/include/bt_types.h"
#include "types/bluetooth/uuid.h"

#define GATT_WRITE_LONG_HDR_SIZE 5 /* 1 opcode + 2 handle + 2 offset */
#define GATT_READ_CHAR_VALUE_HDL (GATT_READ_CHAR_VALUE | 0x80)
#define GATT_READ_INC_SRV_UUID128 (GATT_DISC_INC_SRVC | 0x90)

#define GATT_PREP_WRITE_RSP_MIN_LEN 4
#define GATT_NOTIFICATION_MIN_LEN 2
#define GATT_WRITE_RSP_MIN_LEN 2
#define GATT_INFO_RSP_MIN_LEN 1
#define GATT_MTU_RSP_MIN_LEN 2
#define GATT_READ_BY_TYPE_RSP_MIN_LEN 1

#define L2CAP_PKT_OVERHEAD 4

using namespace bluetooth;
using bluetooth::Uuid;
using bluetooth::eatt::EattExtension;
using bluetooth::eatt::EattChannel;

/*******************************************************************************
 *                      G L O B A L      G A T T       D A T A                 *
 ******************************************************************************/
void gatt_send_prepare_write(tGATT_TCB& tcb, tGATT_CLCB* p_clcb);

uint8_t disc_type_to_att_opcode[GATT_DISC_MAX] = {
    0,
    GATT_REQ_READ_BY_GRP_TYPE, /*  GATT_DISC_SRVC_ALL = 1, */
    GATT_REQ_FIND_TYPE_VALUE,  /*  GATT_DISC_SRVC_BY_UUID,  */
    GATT_REQ_READ_BY_TYPE,     /*  GATT_DISC_INC_SRVC,      */
    GATT_REQ_READ_BY_TYPE,     /*  GATT_DISC_CHAR,          */
    GATT_REQ_FIND_INFO         /*  GATT_DISC_CHAR_DSCPT,    */
};

uint16_t disc_type_to_uuid[GATT_DISC_MAX] = {
    0,                         /* reserved */
    GATT_UUID_PRI_SERVICE,     /* <service> DISC_SRVC_ALL */
    GATT_UUID_PRI_SERVICE,     /* <service> for DISC_SERVC_BY_UUID */
    GATT_UUID_INCLUDE_SERVICE, /* <include_service> for DISC_INC_SRVC */
    GATT_UUID_CHAR_DECLARE,    /* <characteristic> for DISC_CHAR */
    0                          /* no type filtering for DISC_CHAR_DSCPT */
};

/*******************************************************************************
 *
 * Function         gatt_act_discovery
 *
 * Description      GATT discovery operation.
 *
 * Returns          void.
 *
 ******************************************************************************/
void gatt_act_discovery(tGATT_CLCB* p_clcb) {
  uint8_t op_code = disc_type_to_att_opcode[p_clcb->op_subtype];

  if (p_clcb->s_handle > p_clcb->e_handle || p_clcb->s_handle == 0) {
    log::debug("Completed GATT discovery of all handle ranges");
    gatt_end_operation(p_clcb, GATT_SUCCESS, NULL);
    return;
  }

  tGATT_CL_MSG cl_req;
  memset(&cl_req, 0, sizeof(tGATT_CL_MSG));

  cl_req.browse.s_handle = p_clcb->s_handle;
  cl_req.browse.e_handle = p_clcb->e_handle;

  if (disc_type_to_uuid[p_clcb->op_subtype] != 0) {
    cl_req.browse.uuid =
        bluetooth::Uuid::From16Bit(disc_type_to_uuid[p_clcb->op_subtype]);
  }

  if (p_clcb->op_subtype ==
      GATT_DISC_SRVC_BY_UUID) /* fill in the FindByTypeValue request info*/
  {
    cl_req.find_type_value.uuid =
        bluetooth::Uuid::From16Bit(disc_type_to_uuid[p_clcb->op_subtype]);
    cl_req.find_type_value.s_handle = p_clcb->s_handle;
    cl_req.find_type_value.e_handle = p_clcb->e_handle;

    size_t size = p_clcb->uuid.GetShortestRepresentationSize();
    cl_req.find_type_value.value_len = size;
    if (size == Uuid::kNumBytes16) {
      uint8_t* p = cl_req.find_type_value.value;
      UINT16_TO_STREAM(p, p_clcb->uuid.As16Bit());
    } else if (size == Uuid::kNumBytes32) {
      /* if service type is 32 bits UUID, convert it now */
      memcpy(cl_req.find_type_value.value, p_clcb->uuid.To128BitLE().data(),
            Uuid::kNumBytes128);
      cl_req.find_type_value.value_len = Uuid::kNumBytes128;
    } else
      memcpy(cl_req.find_type_value.value, p_clcb->uuid.To128BitLE().data(),
             size);
  }

  tGATT_STATUS st = attp_send_cl_msg(*p_clcb->p_tcb, p_clcb, op_code, &cl_req);
  if (st != GATT_SUCCESS && st != GATT_CMD_STARTED) {
    log::warn("Unable to send ATT message");
    gatt_end_operation(p_clcb, GATT_ERROR, NULL);
  }
}

/*******************************************************************************
 *
 * Function         gatt_act_read
 *
 * Description      GATT read operation.
 *
 * Returns          void.
 *
 ******************************************************************************/
void gatt_act_read(tGATT_CLCB* p_clcb, uint16_t offset) {
  tGATT_TCB& tcb = *p_clcb->p_tcb;
  tGATT_STATUS rt = GATT_INTERNAL_ERROR;
  tGATT_CL_MSG msg;
  uint8_t op_code = 0;

  memset(&msg, 0, sizeof(tGATT_CL_MSG));

  switch (p_clcb->op_subtype) {
    case GATT_READ_CHAR_VALUE:
    case GATT_READ_BY_TYPE:
      op_code = GATT_REQ_READ_BY_TYPE;
      msg.browse.s_handle = p_clcb->s_handle;
      msg.browse.e_handle = p_clcb->e_handle;
      if (p_clcb->op_subtype == GATT_READ_BY_TYPE)
        msg.browse.uuid = p_clcb->uuid;
      else {
        msg.browse.uuid = bluetooth::Uuid::From16Bit(GATT_UUID_CHAR_DECLARE);
      }
      break;

    case GATT_READ_CHAR_VALUE_HDL:
    case GATT_READ_BY_HANDLE:
      if (!p_clcb->counter) {
        op_code = GATT_REQ_READ;
        msg.handle = p_clcb->s_handle;
      } else {
        if (!p_clcb->first_read_blob_after_read)
          p_clcb->first_read_blob_after_read = true;
        else
          p_clcb->first_read_blob_after_read = false;

        log::verbose("first_read_blob_after_read={}",
                     p_clcb->first_read_blob_after_read);
        op_code = GATT_REQ_READ_BLOB;
        msg.read_blob.offset = offset;
        msg.read_blob.handle = p_clcb->s_handle;
      }
      p_clcb->op_subtype &= ~0x80;
      break;

    case GATT_READ_PARTIAL:
      op_code = GATT_REQ_READ_BLOB;
      msg.read_blob.handle = p_clcb->s_handle;
      msg.read_blob.offset = offset;
      break;

    case GATT_READ_MULTIPLE:
      op_code = GATT_REQ_READ_MULTI;
      memcpy(&msg.read_multi, p_clcb->p_attr_buf, sizeof(tGATT_READ_MULTI));
      break;

    case GATT_READ_MULTIPLE_VAR_LEN:
      op_code = GATT_REQ_READ_MULTI_VAR;
      memcpy(&msg.read_multi, p_clcb->p_attr_buf, sizeof(tGATT_READ_MULTI));
      break;

    case GATT_READ_INC_SRV_UUID128:
      op_code = GATT_REQ_READ;
      msg.handle = p_clcb->s_handle;
      p_clcb->op_subtype &= ~0x90;
      break;

    default:
      log::error("Unknown read type:{}", p_clcb->op_subtype);
      break;
  }

  if (op_code != 0) rt = attp_send_cl_msg(tcb, p_clcb, op_code, &msg);

  if (op_code == 0 || (rt != GATT_SUCCESS && rt != GATT_CMD_STARTED)) {
    gatt_end_operation(p_clcb, rt, NULL);
  }
}

/** GATT write operation */
void gatt_act_write(tGATT_CLCB* p_clcb, uint8_t sec_act) {
  tGATT_TCB& tcb = *p_clcb->p_tcb;

  CHECK(p_clcb->p_attr_buf);
  tGATT_VALUE& attr = *((tGATT_VALUE*)p_clcb->p_attr_buf);

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, p_clcb->cid);

  switch (p_clcb->op_subtype) {
    case GATT_WRITE_NO_RSP: {
      p_clcb->s_handle = attr.handle;
      uint8_t op_code = (sec_act == GATT_SEC_SIGN_DATA) ? GATT_SIGN_CMD_WRITE
                                                        : GATT_CMD_WRITE;
      tGATT_STATUS rt = gatt_send_write_msg(tcb, p_clcb, op_code, attr.handle,
                                            attr.len, 0, attr.value);
      if (rt != GATT_CMD_STARTED) {
        if (rt != GATT_SUCCESS) {
          log::error("gatt_act_write() failed op_code=0x{:x} rt={}", op_code,
                     rt);
        }
        gatt_end_operation(p_clcb, rt, NULL);
      }
      return;
    }

    case GATT_WRITE: {
      if (attr.len <= (payload_size - GATT_HDR_SIZE)) {
        p_clcb->s_handle = attr.handle;

        tGATT_STATUS rt = gatt_send_write_msg(
            tcb, p_clcb, GATT_REQ_WRITE, attr.handle, attr.len, 0, attr.value);
        if (rt != GATT_SUCCESS && rt != GATT_CMD_STARTED &&
            rt != GATT_CONGESTED) {
          if (rt != GATT_SUCCESS) {
            log::error("gatt_act_write() failed op_code=0x{:x} rt={}",
                       GATT_REQ_WRITE, rt);
          }
          gatt_end_operation(p_clcb, rt, NULL);
        }

      } else {
        /* prepare write for long attribute */
        gatt_send_prepare_write(tcb, p_clcb);
      }
      return;
    }

    case GATT_WRITE_PREPARE:
      gatt_send_prepare_write(tcb, p_clcb);
      return;

    default:
      CHECK(false) << "Unknown write type" << p_clcb->op_subtype;
      return;
  }
}
/*******************************************************************************
 *
 * Function         gatt_send_queue_write_cancel
 *
 * Description      send queue write cancel
 *
 * Returns          void.
 *
 ******************************************************************************/
void gatt_send_queue_write_cancel(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                                  tGATT_EXEC_FLAG flag) {
  tGATT_STATUS rt;

  log::verbose("");

  tGATT_CL_MSG gatt_cl_msg;
  gatt_cl_msg.exec_write = flag;
  rt = attp_send_cl_msg(tcb, p_clcb, GATT_REQ_EXEC_WRITE, &gatt_cl_msg);

  if (rt != GATT_SUCCESS) {
    gatt_end_operation(p_clcb, rt, NULL);
  }
}
/*******************************************************************************
 *
 * Function         gatt_check_write_long_terminate
 *
 * Description      To terminate write long or not.
 *
 * Returns          true: write long is terminated; false keep sending.
 *
 ******************************************************************************/
bool gatt_check_write_long_terminate(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                                     tGATT_VALUE* p_rsp_value) {
  tGATT_VALUE* p_attr = (tGATT_VALUE*)p_clcb->p_attr_buf;
  bool terminate = false;
  tGATT_EXEC_FLAG flag = GATT_PREP_WRITE_EXEC;

  log::verbose("");
  /* check the first write response status */
  if (p_rsp_value != NULL) {
    if (p_rsp_value->handle != p_attr->handle ||
        p_rsp_value->len != p_clcb->counter ||
        memcmp(p_rsp_value->value, p_attr->value + p_attr->offset,
               p_rsp_value->len)) {
      /* data does not match    */
      p_clcb->status = GATT_ERROR;
      flag = GATT_PREP_WRITE_CANCEL;
      terminate = true;
    } else /* response checking is good */
    {
      p_clcb->status = GATT_SUCCESS;
      /* update write offset and check if end of attribute value */
      if ((p_attr->offset += p_rsp_value->len) >= p_attr->len) terminate = true;
    }
  }
  if (terminate && p_clcb->op_subtype != GATT_WRITE_PREPARE) {
    gatt_send_queue_write_cancel(tcb, p_clcb, flag);
  }
  return terminate;
}

/** Send prepare write */
void gatt_send_prepare_write(tGATT_TCB& tcb, tGATT_CLCB* p_clcb) {
  tGATT_VALUE* p_attr = (tGATT_VALUE*)p_clcb->p_attr_buf;
  uint8_t type = p_clcb->op_subtype;

  log::verbose("type=0x{:x}", type);
  uint16_t to_send = p_attr->len - p_attr->offset;

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, p_clcb->cid);
  if (to_send > (payload_size -
                 GATT_WRITE_LONG_HDR_SIZE)) /* 2 = uint16_t offset bytes  */
    to_send = payload_size - GATT_WRITE_LONG_HDR_SIZE;

  p_clcb->s_handle = p_attr->handle;

  uint16_t offset = p_attr->offset;
  if (type == GATT_WRITE_PREPARE) {
    offset += p_clcb->start_offset;
  }

  log::verbose("offset =0x{:x} len={}", offset, to_send);

  tGATT_STATUS rt = gatt_send_write_msg(
      tcb, p_clcb, GATT_REQ_PREPARE_WRITE, p_attr->handle, to_send, /* length */
      offset,                          /* used as offset */
      p_attr->value + p_attr->offset); /* data */

  /* remember the write long attribute length */
  p_clcb->counter = to_send;

  if (rt != GATT_SUCCESS && rt != GATT_CMD_STARTED && rt != GATT_CONGESTED) {
    gatt_end_operation(p_clcb, rt, NULL);
  }
}

/*******************************************************************************
 *
 * Function         gatt_process_find_type_value_rsp
 *
 * Description      This function handles the find by type value response.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_find_type_value_rsp(UNUSED_ATTR tGATT_TCB& tcb,
                                      tGATT_CLCB* p_clcb, uint16_t len,
                                      uint8_t* p_data) {
  tGATT_DISC_RES result;
  uint8_t* p = p_data;

  log::verbose("");
  /* unexpected response */
  if (p_clcb->operation != GATTC_OPTYPE_DISCOVERY ||
      p_clcb->op_subtype != GATT_DISC_SRVC_BY_UUID)
    return;

  memset(&result, 0, sizeof(tGATT_DISC_RES));
  result.type = bluetooth::Uuid::From16Bit(GATT_UUID_PRI_SERVICE);

  /* returns a series of handle ranges */
  while (len >= 4) {
    STREAM_TO_UINT16(result.handle, p);
    STREAM_TO_UINT16(result.value.group_value.e_handle, p);
    result.value.group_value.service_type = p_clcb->uuid;

    len -= 4;

    if (p_clcb->p_reg->app_cb.p_disc_res_cb)
      (*p_clcb->p_reg->app_cb.p_disc_res_cb)(
          p_clcb->conn_id, static_cast<tGATT_DISC_TYPE>(p_clcb->op_subtype),
          &result);
  }

  /* last handle  + 1 */
  p_clcb->s_handle = (result.value.group_value.e_handle == 0)
                         ? 0
                         : (result.value.group_value.e_handle + 1);
  /* initiate another request */
  gatt_act_discovery(p_clcb);
}
/*******************************************************************************
 *
 * Function         gatt_process_read_info_rsp
 *
 * Description      This function is called to handle the read information
 *                  response.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_read_info_rsp(UNUSED_ATTR tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                                UNUSED_ATTR uint8_t op_code, uint16_t len,
                                uint8_t* p_data) {
  tGATT_DISC_RES result;
  uint8_t *p = p_data, uuid_len = 0, type;

  if (len < GATT_INFO_RSP_MIN_LEN) {
    log::error("invalid Info Response PDU received, discard.");
    gatt_end_operation(p_clcb, GATT_INVALID_PDU, NULL);
    return;
  }
  /* unexpected response */
  if (p_clcb->operation != GATTC_OPTYPE_DISCOVERY ||
      p_clcb->op_subtype != GATT_DISC_CHAR_DSCPT)
    return;

  STREAM_TO_UINT8(type, p);
  len -= 1;

  if (type == GATT_INFO_TYPE_PAIR_16)
    uuid_len = Uuid::kNumBytes16;
  else if (type == GATT_INFO_TYPE_PAIR_128)
    uuid_len = Uuid::kNumBytes128;

  while (len >= uuid_len + 2) {
    STREAM_TO_UINT16(result.handle, p);

    if (uuid_len > 0) {
      if (!gatt_parse_uuid_from_cmd(&result.type, uuid_len, &p)) break;
    } else
      result.type = p_clcb->uuid;

    len -= (uuid_len + 2);

    if (p_clcb->p_reg->app_cb.p_disc_res_cb)
      (*p_clcb->p_reg->app_cb.p_disc_res_cb)(
          p_clcb->conn_id, static_cast<tGATT_DISC_TYPE>(p_clcb->op_subtype),
          &result);
  }

  p_clcb->s_handle = (result.handle == 0) ? 0 : (result.handle + 1);
  /* initiate another request */
  gatt_act_discovery(p_clcb);
}
/*******************************************************************************
 *
 * Function         gatt_proc_disc_error_rsp
 *
 * Description      Process the read by type response and send another request
 *                  if needed.
 *
 * Returns          void.
 *
 ******************************************************************************/
void gatt_proc_disc_error_rsp(UNUSED_ATTR tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                              uint8_t opcode, UNUSED_ATTR uint16_t handle,
                              uint8_t reason) {
  tGATT_STATUS status = (tGATT_STATUS)reason;

  log::verbose("reason: {:02x} cmd_code {:04x}", reason, opcode);

  switch (opcode) {
    case GATT_REQ_READ_BY_GRP_TYPE:
    case GATT_REQ_FIND_TYPE_VALUE:
    case GATT_REQ_READ_BY_TYPE:
    case GATT_REQ_FIND_INFO:
      if (reason == GATT_NOT_FOUND) {
        status = GATT_SUCCESS;
        log::verbose("Discovery completed");
      }
      break;
    default:
      log::error("Incorrect discovery opcode {:04x}", opcode);
      break;
  }

  gatt_end_operation(p_clcb, status, NULL);
}

/*******************************************************************************
 *
 * Function         gatt_process_error_rsp
 *
 * Description      This function is called to handle the error response
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_error_rsp(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                            UNUSED_ATTR uint8_t op_code,
                            UNUSED_ATTR uint16_t len, uint8_t* p_data) {
  uint8_t opcode, *p = p_data;
  uint8_t reason;
  uint16_t handle;
  tGATT_VALUE* p_attr = (tGATT_VALUE*)p_clcb->p_attr_buf;

  log::verbose("");

  if (len < 4) {
    log::error("Error response too short");
    // Specification does not clearly define what should happen if error
    // response is too short. General rule in BT Spec 5.0 Vol 3, Part F 3.4.1.1
    // is: "If an error code is received in the Error Response that is not
    // understood by the client, for example an error code that was reserved for
    // future use that is now being used in a future version of this
    // specification, then the Error Response shall still be considered to state
    // that the given request cannot be performed for an unknown reason."
    opcode = handle = 0;
    reason = static_cast<tGATT_STATUS>(0x7f);
  } else {
    STREAM_TO_UINT8(opcode, p);
    STREAM_TO_UINT16(handle, p);
    STREAM_TO_UINT8(reason, p);
  }

  if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY) {
    gatt_proc_disc_error_rsp(tcb, p_clcb, opcode, handle,
                             static_cast<tGATT_STATUS>(reason));
  } else {
    if ((p_clcb->operation == GATTC_OPTYPE_WRITE) &&
        (p_clcb->op_subtype == GATT_WRITE) &&
        (opcode == GATT_REQ_PREPARE_WRITE) && (p_attr) &&
        (handle == p_attr->handle)) {
      p_clcb->status = static_cast<tGATT_STATUS>(reason);
      gatt_send_queue_write_cancel(tcb, p_clcb, GATT_PREP_WRITE_CANCEL);
    } else if ((p_clcb->operation == GATTC_OPTYPE_READ) &&
               ((p_clcb->op_subtype == GATT_READ_CHAR_VALUE_HDL) ||
                (p_clcb->op_subtype == GATT_READ_BY_HANDLE)) &&
               (opcode == GATT_REQ_READ_BLOB) &&
               p_clcb->first_read_blob_after_read &&
               (reason == GATT_NOT_LONG)) {
      gatt_end_operation(p_clcb, GATT_SUCCESS, (void*)p_clcb->p_attr_buf);
    } else
      gatt_end_operation(p_clcb, static_cast<tGATT_STATUS>(reason), NULL);
  }
}
/*******************************************************************************
 *
 * Function         gatt_process_prep_write_rsp
 *
 * Description      This function is called to handle the read response
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_prep_write_rsp(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                                 uint8_t op_code, uint16_t len,
                                 uint8_t* p_data) {
  uint8_t* p = p_data;

  tGATT_VALUE value = {
      .conn_id = p_clcb->conn_id, .auth_req = GATT_AUTH_REQ_NONE,
  };

  log::verbose("value resp op_code = {} len = {}", gatt_dbg_op_name(op_code),
               len);

  if (len < GATT_PREP_WRITE_RSP_MIN_LEN ||
      len > GATT_PREP_WRITE_RSP_MIN_LEN + sizeof(value.value)) {
    log::error("illegal prepare write response length, discard");
    gatt_end_operation(p_clcb, GATT_INVALID_PDU, &value);
    return;
  }

  STREAM_TO_UINT16(value.handle, p);
  STREAM_TO_UINT16(value.offset, p);

  value.len = len - GATT_PREP_WRITE_RSP_MIN_LEN;

  memcpy(value.value, p, value.len);

  bool subtype_is_write_prepare = (p_clcb->op_subtype == GATT_WRITE_PREPARE);

  if (!gatt_check_write_long_terminate(tcb, p_clcb, &value)) {
    gatt_send_prepare_write(tcb, p_clcb);
    return;
  }

  // We now know that we have not terminated, or else we would have returned
  // early.  We free the buffer only if the subtype is not equal to
  // GATT_WRITE_PREPARE, so checking here is adequate to prevent UAF.
  if (subtype_is_write_prepare) {
    /* application should verify handle offset
       and value are matched or not */
    gatt_end_operation(p_clcb, p_clcb->status, &value);
  }
}

/*******************************************************************************
 *
 * Function         gatt_process_notification
 *
 * Description      Handle the handle value indication/notification.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_notification(tGATT_TCB& tcb, uint16_t cid, uint8_t op_code,
                               uint16_t len, uint8_t* p_data) {
  tGATT_VALUE value = {};
  tGATT_REG* p_reg;
  uint16_t conn_id;
  tGATT_STATUS encrypt_status = {};
  uint8_t* p = p_data;
  uint8_t i;
  tGATTC_OPTYPE event = (op_code == GATT_HANDLE_VALUE_IND)
                            ? GATTC_OPTYPE_INDICATION
                            : GATTC_OPTYPE_NOTIFICATION;

  log::verbose("");

  // Ensure our packet has enough data (2 bytes)
  if (len < GATT_NOTIFICATION_MIN_LEN) {
    log::error("illegal notification PDU length, discard");
    return;
  }

  // Get 2 byte handle
  STREAM_TO_UINT16(value.handle, p);

  // Fail early if the GATT handle is not valid
  if (!GATT_HANDLE_IS_VALID(value.handle)) {
    /* illegal handle, send ack now */
    if (op_code == GATT_HANDLE_VALUE_IND)
      attp_send_cl_confirmation_msg(tcb, cid);
    return;
  }

  // Calculate value length based on opcode
  if (op_code == GATT_HANDLE_MULTI_VALUE_NOTIF) {
    // Ensure our packet has enough data; MIN + 2 more bytes for len value
    if (len < GATT_NOTIFICATION_MIN_LEN + 2) {
      log::error("illegal notification PDU length, discard");
      return;
    }

    // Allow multi value opcode to set value len from the packet
    STREAM_TO_UINT16(value.len, p);

    if (value.len > len - 4) {
      log::error("value.len ({}) greater than length ({})", value.len,
                 (len - 4));
      return;
    }

  } else {
    // For single value, just use the passed in len minus opcode length (2)
    value.len = len - 2;
  }

  // Verify the new calculated length
  if (value.len > GATT_MAX_ATTR_LEN) {
    log::error("value.len larger than GATT_MAX_ATTR_LEN, discard");
    return;
  }

  // Handle indications differently
  if (event == GATTC_OPTYPE_INDICATION) {
    if (tcb.ind_count) {
      /* this is an error case that receiving an indication but we
         still has an indication not being acked yet.
         For now, just log the error reset the counter.
         Later we need to disconnect the link unconditionally.
      */
      log::error("rcv Ind. but ind_count={} (will reset ind_count)",
                 tcb.ind_count);
    }

    // Zero out the ind_count
    tcb.ind_count = 0;

    // Notify all registered clients with the handle value
    // notification/indication
    // Note: need to do the indication count and start timer first then do
    // callback
    for (i = 0, p_reg = gatt_cb.cl_rcb; i < GATT_MAX_APPS; i++, p_reg++) {
      if (p_reg->in_use && p_reg->app_cb.p_cmpl_cb) tcb.ind_count++;
    }

    /* start a timer for app confirmation */
    if (tcb.ind_count > 0) {
      gatt_start_ind_ack_timer(tcb, cid);
    } else { /* no app to indicate, or invalid handle */
      attp_send_cl_confirmation_msg(tcb, cid);
    }
  }

  encrypt_status = gatt_get_link_encrypt_status(tcb);

  STREAM_TO_ARRAY(value.value, p, value.len);

  tGATT_CL_COMPLETE gatt_cl_complete;
  gatt_cl_complete.att_value = value;
  gatt_cl_complete.cid = cid;

  for (i = 0, p_reg = gatt_cb.cl_rcb; i < GATT_MAX_APPS; i++, p_reg++) {
    if (p_reg->in_use && p_reg->app_cb.p_cmpl_cb) {
      conn_id = GATT_CREATE_CONN_ID(tcb.tcb_idx, p_reg->gatt_if);
      (*p_reg->app_cb.p_cmpl_cb)(conn_id, event, encrypt_status,
                                 &gatt_cl_complete);
    }
  }

  // If this is single value, then nothing is left to do
  if (op_code != GATT_HANDLE_MULTI_VALUE_NOTIF) return;

  // Need a signed type to check if the value is below 0
  // as uint16_t doesn't have negatives so the negatives register as a number
  // thus anything less than zero won't trigger the conditional and it is not
  // always 0
  // when done looping as value.len is arbitrary.
  int16_t rem_len = (int16_t)len - (4 /* octets */ + value.len);

  // Already streamed the first value and sent it, lets send the rest
  while (rem_len > 4 /* octets */) {
    // 2
    STREAM_TO_UINT16(value.handle, p);
    // + 2 = 4
    STREAM_TO_UINT16(value.len, p);
    // Accounting
    rem_len -= 4;
    // Make sure we don't read past the remaining data even if the length says
    // we can Also need to watch comparing the int16_t with the uint16_t
    value.len = std::min((uint16_t)rem_len, value.len);
    if (value.len > sizeof(value.value)) {
      log::error("Unexpected value.len (>GATT_MAX_ATTR_LEN), stop");
      return ;
    }
    STREAM_TO_ARRAY(value.value, p, value.len);
    // Accounting
    rem_len -= value.len;

    gatt_cl_complete.att_value = value;
    gatt_cl_complete.cid = cid;

    for (i = 0, p_reg = gatt_cb.cl_rcb; i < GATT_MAX_APPS; i++, p_reg++) {
      if (p_reg->in_use && p_reg->app_cb.p_cmpl_cb) {
        conn_id = GATT_CREATE_CONN_ID(tcb.tcb_idx, p_reg->gatt_if);
        (*p_reg->app_cb.p_cmpl_cb)(conn_id, event, encrypt_status,
                                   &gatt_cl_complete);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         gatt_process_read_by_type_rsp
 *
 * Description      This function is called to handle the read by type response.
 *                  read by type can be used for discovery, or read by type or
 *                  read characteristic value.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_read_by_type_rsp(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                                   uint8_t op_code, uint16_t len,
                                   uint8_t* p_data) {
  tGATT_DISC_RES result;
  tGATT_DISC_VALUE record_value;
  uint8_t *p = p_data, value_len, handle_len = 2;
  uint16_t handle = 0;

  /* discovery procedure and no callback function registered */
  if (((!p_clcb->p_reg) || (!p_clcb->p_reg->app_cb.p_disc_res_cb)) &&
      (p_clcb->operation == GATTC_OPTYPE_DISCOVERY))
    return;

  if (len < GATT_READ_BY_TYPE_RSP_MIN_LEN) {
    log::error("Illegal ReadByType/ReadByGroupType Response length, discard");
    gatt_end_operation(p_clcb, GATT_INVALID_PDU, NULL);
    return;
  }

  STREAM_TO_UINT8(value_len, p);
  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, p_clcb->cid);
  if ((value_len > (payload_size - 2)) || (value_len > (len - 1))) {
    /* this is an error case that server's response containing a value length
       which is larger than MTU-2
       or value_len > message total length -1 */
    log::error(
        "Discard response op_code={} vale_len={} > (MTU-2={} or msg_len-1={})",
        op_code, value_len, (payload_size - 2), (len - 1));
    gatt_end_operation(p_clcb, GATT_ERROR, NULL);
    return;
  }

  if (op_code == GATT_RSP_READ_BY_GRP_TYPE) handle_len = 4;

  value_len -= handle_len; /* subtract the handle pairs bytes */
  len -= 1;

  while (len >= (handle_len + value_len)) {
    STREAM_TO_UINT16(handle, p);

    if (!GATT_HANDLE_IS_VALID(handle)) {
      gatt_end_operation(p_clcb, GATT_INVALID_HANDLE, NULL);
      return;
    }

    memset(&result, 0, sizeof(tGATT_DISC_RES));
    memset(&record_value, 0, sizeof(tGATT_DISC_VALUE));

    result.handle = handle;
    result.type =
        bluetooth::Uuid::From16Bit(disc_type_to_uuid[p_clcb->op_subtype]);

    /* discover all services */
    if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY &&
        p_clcb->op_subtype == GATT_DISC_SRVC_ALL &&
        op_code == GATT_RSP_READ_BY_GRP_TYPE) {
      STREAM_TO_UINT16(handle, p);

      if (!GATT_HANDLE_IS_VALID(handle)) {
        gatt_end_operation(p_clcb, GATT_INVALID_HANDLE, NULL);
        return;
      } else {
        record_value.group_value.e_handle = handle;
        if (!gatt_parse_uuid_from_cmd(&record_value.group_value.service_type,
                                      value_len, &p)) {
          log::error("discover all service response parsing failure");
          break;
        }
      }
    }
    /* discover included service */
    else if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY &&
             p_clcb->op_subtype == GATT_DISC_INC_SRVC) {
      if (value_len < 4) {
        log::error("Illegal Response length, must be at least 4.");
        gatt_end_operation(p_clcb, GATT_INVALID_PDU, NULL);
        return;
      }
      STREAM_TO_UINT16(record_value.incl_service.s_handle, p);
      STREAM_TO_UINT16(record_value.incl_service.e_handle, p);

      if (!GATT_HANDLE_IS_VALID(record_value.incl_service.s_handle) ||
          !GATT_HANDLE_IS_VALID(record_value.incl_service.e_handle)) {
        gatt_end_operation(p_clcb, GATT_INVALID_HANDLE, NULL);
        return;
      }

      if (value_len == 6) {
        uint16_t tmp;
        STREAM_TO_UINT16(tmp, p);
        record_value.incl_service.service_type =
            bluetooth::Uuid::From16Bit(tmp);
      } else if (value_len == 4) {
        p_clcb->s_handle = record_value.incl_service.s_handle;
        p_clcb->read_uuid128.wait_for_read_rsp = true;
        p_clcb->read_uuid128.next_disc_start_hdl = handle + 1;
        memcpy(&p_clcb->read_uuid128.result, &result, sizeof(result));
        memcpy(&p_clcb->read_uuid128.result.value, &record_value,
               sizeof(result.value));
        p_clcb->op_subtype |= 0x90;
        gatt_act_read(p_clcb, 0);
        return;
      } else {
        log::error("INCL_SRVC failed with invalid data value_len={}",
                   value_len);
        gatt_end_operation(p_clcb, GATT_INVALID_PDU, (void*)p);
        return;
      }
    }
    /* read by type */
    else if (p_clcb->operation == GATTC_OPTYPE_READ &&
             p_clcb->op_subtype == GATT_READ_BY_TYPE) {
      p_clcb->counter = len - 2;
      p_clcb->s_handle = handle;
      if (p_clcb->counter == (payload_size - 4)) {
        p_clcb->op_subtype = GATT_READ_BY_HANDLE;
        if (!p_clcb->p_attr_buf)
          p_clcb->p_attr_buf = (uint8_t*)osi_malloc(GATT_MAX_ATTR_LEN);
        if (p_clcb->counter <= GATT_MAX_ATTR_LEN) {
          memcpy(p_clcb->p_attr_buf, p, p_clcb->counter);
          gatt_act_read(p_clcb, p_clcb->counter);
        } else {
          gatt_end_operation(p_clcb, GATT_INTERNAL_ERROR, (void*)p);
        }
      } else {
        gatt_end_operation(p_clcb, GATT_SUCCESS, (void*)p);
      }
      return;
    } else /* discover characteristic */
    {
      if (value_len < 3) {
        log::error("Illegal Response length, must be at least 3.");
        gatt_end_operation(p_clcb, GATT_INVALID_PDU, NULL);
        return;
      }
      STREAM_TO_UINT8(record_value.dclr_value.char_prop, p);
      STREAM_TO_UINT16(record_value.dclr_value.val_handle, p);
      if (!GATT_HANDLE_IS_VALID(record_value.dclr_value.val_handle)) {
        gatt_end_operation(p_clcb, GATT_INVALID_HANDLE, NULL);
        return;
      }
      if (!gatt_parse_uuid_from_cmd(&record_value.dclr_value.char_uuid,
                                    (uint16_t)(value_len - 3), &p)) {
        gatt_end_operation(p_clcb, GATT_SUCCESS, NULL);
        /* invalid format, and skip the result */
        return;
      }

      /* UUID not matching */
      if (!p_clcb->uuid.IsEmpty() &&
          !record_value.dclr_value.char_uuid.IsEmpty() &&
          record_value.dclr_value.char_uuid != p_clcb->uuid) {
        len -= (value_len + 2);
        continue; /* skip the result, and look for next one */
      }

      if (p_clcb->operation == GATTC_OPTYPE_READ)
      /* UUID match for read characteristic value */
      {
        /* only read the first matching UUID characteristic value, and
          discard the rest results */
        p_clcb->s_handle = record_value.dclr_value.val_handle;
        p_clcb->op_subtype |= 0x80;
        gatt_act_read(p_clcb, 0);
        return;
      }
    }
    len -= (value_len + handle_len);

    /* result is (handle, 16bits UUID) pairs */
    memcpy(&result.value, &record_value, sizeof(result.value));

    /* send callback if is discover procedure */
    if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY &&
        p_clcb->p_reg->app_cb.p_disc_res_cb)
      (*p_clcb->p_reg->app_cb.p_disc_res_cb)(
          p_clcb->conn_id, static_cast<tGATT_DISC_TYPE>(p_clcb->op_subtype),
          &result);
  }

  p_clcb->s_handle = (handle == 0) ? 0 : (handle + 1);

  if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY) {
    /* initiate another request */
    gatt_act_discovery(p_clcb);
  } else /* read characteristic value */
  {
    gatt_act_read(p_clcb, 0);
  }
}

/*******************************************************************************
 *
 * Function         gatt_process_read_rsp
 *
 * Description      This function is called to handle the read BLOB response
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_read_rsp(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                           UNUSED_ATTR uint8_t op_code, uint16_t len,
                           uint8_t* p_data) {
  uint16_t offset = p_clcb->counter;
  uint8_t* p = p_data;

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, p_clcb->cid);

  if (p_clcb->operation == GATTC_OPTYPE_READ) {
    if (p_clcb->op_subtype != GATT_READ_BY_HANDLE) {
      p_clcb->counter = len;
      gatt_end_operation(p_clcb, GATT_SUCCESS, (void*)p);
    } else {
      /* allocate GKI buffer holding up long attribute value  */
      if (!p_clcb->p_attr_buf)
        p_clcb->p_attr_buf = (uint8_t*)osi_malloc(GATT_MAX_ATTR_LEN);

      /* copy attribute value into cb buffer  */
      if (offset < GATT_MAX_ATTR_LEN) {
        if ((len + offset) > GATT_MAX_ATTR_LEN)
          len = GATT_MAX_ATTR_LEN - offset;

        p_clcb->counter += len;

        memcpy(p_clcb->p_attr_buf + offset, p, len);

        /* full packet for read or read blob rsp */
        bool packet_is_full;
        if (payload_size == p_clcb->read_req_current_mtu) {
          packet_is_full = (len == (payload_size - 1));
        } else {
          packet_is_full = (len == (p_clcb->read_req_current_mtu - 1) ||
                            len == (payload_size - 1));
          p_clcb->read_req_current_mtu = payload_size;
        }

        /* send next request if needed  */
        if (packet_is_full && (len + offset < GATT_MAX_ATTR_LEN)) {
          log::verbose(
              "full pkt issue read blob for remaining bytes old offset={} "
              "len={} new offset={}",
              offset, len, p_clcb->counter);
          gatt_act_read(p_clcb, p_clcb->counter);
        } else /* end of request, send callback */
        {
          gatt_end_operation(p_clcb, GATT_SUCCESS, (void*)p_clcb->p_attr_buf);
        }
      } else /* exception, should not happen */
      {
        log::error("attr offset = {} p_attr_buf = {}", offset,
                   fmt::ptr(p_clcb->p_attr_buf));
        gatt_end_operation(p_clcb, GATT_NO_RESOURCES,
                           (void*)p_clcb->p_attr_buf);
      }
    }
  } else {
    if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY &&
        p_clcb->op_subtype == GATT_DISC_INC_SRVC &&
        p_clcb->read_uuid128.wait_for_read_rsp) {
      p_clcb->s_handle = p_clcb->read_uuid128.next_disc_start_hdl;
      p_clcb->read_uuid128.wait_for_read_rsp = false;
      if (len == Uuid::kNumBytes128) {
        p_clcb->read_uuid128.result.value.incl_service.service_type =
            bluetooth::Uuid::From128BitLE(p);
        if (p_clcb->p_reg->app_cb.p_disc_res_cb)
          (*p_clcb->p_reg->app_cb.p_disc_res_cb)(
              p_clcb->conn_id, static_cast<tGATT_DISC_TYPE>(p_clcb->op_subtype),
              &p_clcb->read_uuid128.result);
        gatt_act_discovery(p_clcb);
      } else {
        gatt_end_operation(p_clcb, GATT_INVALID_PDU, (void*)p);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         gatt_process_handle_rsp
 *
 * Description      This function is called to handle the write response
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_handle_rsp(tGATT_CLCB* p_clcb) {
  gatt_end_operation(p_clcb, GATT_SUCCESS, NULL);
}
/*******************************************************************************
 *
 * Function         gatt_process_mtu_rsp
 *
 * Description      Process the configure MTU response.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_process_mtu_rsp(tGATT_TCB& tcb, tGATT_CLCB* p_clcb, uint16_t len,
                          uint8_t* p_data) {
  uint16_t mtu;
  tGATT_STATUS status = GATT_SUCCESS;

  if (len < GATT_MTU_RSP_MIN_LEN) {
    log::error("invalid MTU response PDU received, discard.");
    status = GATT_INVALID_PDU;
  } else {
    STREAM_TO_UINT16(mtu, p_data);

    log::info("Local pending MTU {}, Remote ({}) MTU {}",
              tcb.pending_user_mtu_exchange_value,
              ADDRESS_TO_LOGGABLE_STR(tcb.peer_bda), mtu);

    /* Aim for default as we did in the request */
    if (mtu < GATT_DEF_BLE_MTU_SIZE) {
      tcb.payload_size = GATT_DEF_BLE_MTU_SIZE;
    } else {
      tcb.payload_size = std::min(mtu, (uint16_t)(gatt_get_local_mtu()));
    }

    bluetooth::shim::arbiter::GetArbiter().OnIncomingMtuResp(tcb.tcb_idx,
                                                             tcb.payload_size);

    /* This is just to track the biggest MTU requested by the user.
     * This value will be used in the BTM_SetBleDataLength */
    if (tcb.pending_user_mtu_exchange_value > tcb.max_user_mtu) {
      tcb.max_user_mtu =
          std::min(tcb.pending_user_mtu_exchange_value, tcb.payload_size);
    } else if (tcb.pending_user_mtu_exchange_value == 0) {
      tcb.max_user_mtu = tcb.payload_size;
    }
    tcb.pending_user_mtu_exchange_value = 0;

    log::info("MTU Exchange resulted in: {}", tcb.payload_size);

    BTM_SetBleDataLength(tcb.peer_bda, tcb.max_user_mtu + L2CAP_PKT_OVERHEAD);
  }

  gatt_end_operation(p_clcb, status, NULL);
}
/*******************************************************************************
 *
 * Function         gatt_cmd_to_rsp_code
 *
 * Description      Convert an ATT command op code into the corresponding
 *                  response code assume no error occurs.
 *
 * Returns          response code.
 *
 ******************************************************************************/
uint8_t gatt_cmd_to_rsp_code(uint8_t cmd_code) {
  uint8_t rsp_code = 0;

  if (cmd_code > 1 && cmd_code != GATT_CMD_WRITE) {
    rsp_code = cmd_code + 1;
  }
  return rsp_code;
}

/** Find next command in queue and sent to server */
bool gatt_cl_send_next_cmd_inq(tGATT_TCB& tcb) {
  std::deque<tGATT_CMD_Q>* cl_cmd_q = nullptr;

  while (
      gatt_is_outstanding_msg_in_att_send_queue(tcb) ||
      EattExtension::GetInstance()->IsOutstandingMsgInSendQueue(tcb.peer_bda)) {
    if (gatt_is_outstanding_msg_in_att_send_queue(tcb)) {
      cl_cmd_q = &tcb.cl_cmd_q;
    } else {
      EattChannel* channel =
          EattExtension::GetInstance()->GetChannelWithQueuedDataToSend(
              tcb.peer_bda);
      cl_cmd_q = &channel->cl_cmd_q_;
    }

    tGATT_CMD_Q& cmd = cl_cmd_q->front();
    if (!cmd.to_send || cmd.p_cmd == NULL) {
      return false;
    }

    tGATT_STATUS att_ret;
    att_ret = attp_send_msg_to_l2cap(tcb, cmd.cid, cmd.p_cmd);

    if (att_ret != GATT_SUCCESS && att_ret != GATT_CONGESTED) {
      log::error("L2CAP sent error");
      cl_cmd_q->pop_front();
      continue;
    }

    cmd.to_send = false;
    cmd.p_cmd = NULL;

    if (cmd.op_code == GATT_CMD_WRITE || cmd.op_code == GATT_SIGN_CMD_WRITE) {
      /* dequeue the request if is write command or sign write */
      uint8_t rsp_code;
      tGATT_CLCB* p_clcb = gatt_cmd_dequeue(tcb, cmd.cid, &rsp_code);

      /* send command complete callback here */
      gatt_end_operation(p_clcb, att_ret, NULL);

      /* if no ack needed, keep sending */
      if (att_ret == GATT_SUCCESS) continue;

      return true;
    }

    gatt_start_rsp_timer(cmd.p_clcb);
    return true;
  }

  return false;
}

/** This function is called to handle the server response to client */
void gatt_client_handle_server_rsp(tGATT_TCB& tcb, uint16_t cid,
                                   uint8_t op_code, uint16_t len,
                                   uint8_t* p_data) {
  log::verbose("opcode: {} cid{}", loghex(op_code), cid);

  uint16_t payload_size = gatt_tcb_get_payload_size(tcb, cid);

  if (op_code == GATT_HANDLE_VALUE_IND || op_code == GATT_HANDLE_VALUE_NOTIF ||
      op_code == GATT_HANDLE_MULTI_VALUE_NOTIF) {
    if (len >= payload_size) {
      log::error("invalid indicate pkt size: {}, PDU size: {}", len + 1,
                 payload_size);
      return;
    }

    gatt_process_notification(tcb, cid, op_code, len, p_data);
    return;
  }

  uint8_t cmd_code = 0;
  tGATT_CLCB* p_clcb = gatt_cmd_dequeue(tcb, cid, &cmd_code);
  if (!p_clcb) {
    log::warn("ATT - clcb already not in use, ignoring response");
    gatt_cl_send_next_cmd_inq(tcb);
    return;
  }

  uint8_t rsp_code = gatt_cmd_to_rsp_code(cmd_code);
  if (!p_clcb) {
    log::warn("ATT - clcb already not in use, ignoring response");
    gatt_cl_send_next_cmd_inq(tcb);
    return;
  }

  if (rsp_code != op_code && op_code != GATT_RSP_ERROR) {
    log::warn(
        "ATT - Ignore wrong response. Receives ({:02x}) Request({:02x}) "
        "Ignored",
        op_code, rsp_code);
    return;
  }

  gatt_stop_rsp_timer(p_clcb);
  p_clcb->retry_count = 0;

  /* the size of the message may not be bigger than the local max PDU size*/
  /* The message has to be smaller than the agreed MTU, len does not count
   * op_code */
  if (len >= payload_size) {
    log::error("invalid response pkt size: {}, PDU size: {}", len + 1,
               payload_size);
    gatt_end_operation(p_clcb, GATT_ERROR, NULL);
  } else {
    switch (op_code) {
      case GATT_RSP_ERROR:
        gatt_process_error_rsp(tcb, p_clcb, op_code, len, p_data);
        break;

      case GATT_RSP_MTU: /* 2 bytes mtu */
        gatt_process_mtu_rsp(tcb, p_clcb, len, p_data);
        break;

      case GATT_RSP_FIND_INFO:
        gatt_process_read_info_rsp(tcb, p_clcb, op_code, len, p_data);
        break;

      case GATT_RSP_READ_BY_TYPE:
      case GATT_RSP_READ_BY_GRP_TYPE:
        gatt_process_read_by_type_rsp(tcb, p_clcb, op_code, len, p_data);
        break;

      case GATT_RSP_READ:
      case GATT_RSP_READ_BLOB:
      case GATT_RSP_READ_MULTI:
      case GATT_RSP_READ_MULTI_VAR:
        gatt_process_read_rsp(tcb, p_clcb, op_code, len, p_data);
        break;

      case GATT_RSP_FIND_TYPE_VALUE: /* disc service with UUID */
        gatt_process_find_type_value_rsp(tcb, p_clcb, len, p_data);
        break;

      case GATT_RSP_WRITE:
        gatt_process_handle_rsp(p_clcb);
        break;

      case GATT_RSP_PREPARE_WRITE:
        gatt_process_prep_write_rsp(tcb, p_clcb, op_code, len, p_data);
        break;

      case GATT_RSP_EXEC_WRITE:
        gatt_end_operation(p_clcb, p_clcb->status, NULL);
        break;

      default:
        log::error("Unknown opcode = {:x}", op_code);
        break;
    }
  }

  gatt_cl_send_next_cmd_inq(tcb);
}
