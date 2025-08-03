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
 *  this file contains GATT interface functions
 *
 ******************************************************************************/
#define LOG_TAG "gatt_api"

#include "stack/include/gatt_api.h"

#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>

#include <string>

#include "device/include/controller.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "internal_include/stack_config.h"
#include "l2c_api.h"
#include "os/log.h"
#include "os/system_properties.h"
#include "osi/include/allocator.h"
#include "rust/src/connection/ffi/connection_shim.h"
#include "stack/arbiter/acl_arbiter.h"
#include "stack/btm/btm_dev.h"
#include "stack/gatt/connection_manager.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

using bluetooth::Uuid;

/**
 * Add an service handle range to the list in decending order of the start
 * handle. Return reference to the newly added element.
 **/
tGATT_HDL_LIST_ELEM& gatt_add_an_item_to_list(uint16_t s_handle) {
  auto lst_ptr = gatt_cb.hdl_list_info;
  auto it = lst_ptr->begin();
  for (; it != lst_ptr->end(); it++) {
    if (s_handle > it->asgn_range.s_handle) break;
  }

  auto rit = lst_ptr->emplace(it);
  return *rit;
}

/*****************************************************************************
 *
 *                  GATT SERVER API
 *
 *****************************************************************************/
/*******************************************************************************
 *
 * Function         GATTS_NVRegister
 *
 * Description      Application manager calls this function to register for
 *                  NV save callback function.  There can be one and only one
 *                  NV save callback function.
 *
 * Parameter        p_cb_info : callback informaiton
 *
 * Returns          true if registered OK, else false
 *
 ******************************************************************************/
bool GATTS_NVRegister(tGATT_APPL_INFO* p_cb_info) {
  bool status = false;
  if (p_cb_info) {
    gatt_cb.cb_info = *p_cb_info;
    status = true;
    gatt_init_srv_chg();
  }

  return status;
}

static uint16_t compute_service_size(btgatt_db_element_t* service, int count) {
  int db_size = 0;
  btgatt_db_element_t* el = service;

  for (int i = 0; i < count; i++, el++)
    if (el->type == BTGATT_DB_PRIMARY_SERVICE ||
        el->type == BTGATT_DB_SECONDARY_SERVICE ||
        el->type == BTGATT_DB_DESCRIPTOR ||
        el->type == BTGATT_DB_INCLUDED_SERVICE) {
      db_size += 1;
    } else if (el->type == BTGATT_DB_CHARACTERISTIC) {
      db_size += 2;

      // if present, Characteristic Extended Properties takes one handle
      if (el->properties & GATT_CHAR_PROP_BIT_EXT_PROP) db_size++;
    } else {
      log::error("Unknown element type: {}", el->type);
    }

  return db_size;
}

static bool is_gatt_attr_type(const Uuid& uuid) {
  if (uuid == Uuid::From16Bit(GATT_UUID_PRI_SERVICE) ||
      uuid == Uuid::From16Bit(GATT_UUID_SEC_SERVICE) ||
      uuid == Uuid::From16Bit(GATT_UUID_INCLUDE_SERVICE) ||
      uuid == Uuid::From16Bit(GATT_UUID_CHAR_DECLARE)) {
    return true;
  }
  return false;
}

/** Update the the last service info for the service list info */
static void gatt_update_last_srv_info() {
  gatt_cb.last_service_handle = 0;

  for (tGATT_SRV_LIST_ELEM& el : *gatt_cb.srv_list_info) {
    gatt_cb.last_service_handle = el.s_hdl;
  }
}

/** Update database hash and client status */
static void gatt_update_for_database_change() {
  gatt_cb.database_hash = gatts_calculate_database_hash(gatt_cb.srv_list_info);

  uint8_t i = 0;
  for (i = 0; i < GATT_MAX_PHY_CHANNEL; i++) {
    tGATT_TCB& tcb = gatt_cb.tcb[i];
    if (tcb.in_use) gatt_sr_update_cl_status(tcb, /* chg_aware= */ false);
  }
}

/*******************************************************************************
 *
 * Function         GATTS_AddService
 *
 * Description      This function is called to add GATT service.
 *
 * Parameter        gatt_if : application if
 *                  service : pseudo-representation of service and it's content
 *                  count   : size of service
 *
 * Returns          on success GATT_SERVICE_STARTED is returned, and
 *                  attribute_handle field inside service elements are filled.
 *                  on error error status is returned.
 *
 ******************************************************************************/
tGATT_STATUS GATTS_AddService(tGATT_IF gatt_if, btgatt_db_element_t* service,
                              int count) {
  uint16_t s_hdl = 0;
  bool save_hdl = false;
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  bool is_pri = (service->type == BTGATT_DB_PRIMARY_SERVICE) ? true : false;
  Uuid svc_uuid = service->uuid;

  log::info("");

  if (!p_reg) {
    log::error("Inavlid gatt_if={}", gatt_if);
    return GATT_INTERNAL_ERROR;
  }

  uint16_t num_handles = compute_service_size(service, count);

  if (svc_uuid == Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER)) {
    s_hdl = gatt_cb.hdl_cfg.gatt_start_hdl;
  } else if (svc_uuid == Uuid::From16Bit(UUID_SERVCLASS_GAP_SERVER)) {
    s_hdl = gatt_cb.hdl_cfg.gap_start_hdl;
  } else if (svc_uuid == Uuid::From16Bit(UUID_SERVCLASS_GMCS_SERVER)) {
    s_hdl = gatt_cb.hdl_cfg.gmcs_start_hdl;
  } else if (svc_uuid == Uuid::From16Bit(UUID_SERVCLASS_GTBS_SERVER)) {
    s_hdl = gatt_cb.hdl_cfg.gtbs_start_hdl;
  } else if (svc_uuid == Uuid::From16Bit(UUID_SERVCLASS_TMAS_SERVER)) {
    s_hdl = gatt_cb.hdl_cfg.tmas_start_hdl;
  } else {
    if (!gatt_cb.hdl_list_info->empty()) {
      s_hdl = gatt_cb.hdl_list_info->front().asgn_range.e_handle + 1;
    }

    if (s_hdl < gatt_cb.hdl_cfg.app_start_hdl)
      s_hdl = gatt_cb.hdl_cfg.app_start_hdl;

    save_hdl = true;
  }

  /* check for space */
  if (num_handles > (0xFFFF - s_hdl + 1)) {
    log::error("no handles, s_hdl={} needed={}", s_hdl, num_handles);
    return GATT_INTERNAL_ERROR;
  }

  tGATT_HDL_LIST_ELEM& list = gatt_add_an_item_to_list(s_hdl);
  list.asgn_range.app_uuid128 = p_reg->app_uuid128;
  list.asgn_range.svc_uuid = svc_uuid;
  list.asgn_range.s_handle = s_hdl;
  list.asgn_range.e_handle = s_hdl + num_handles - 1;
  list.asgn_range.is_primary = is_pri;

  if (save_hdl) {
    if (gatt_cb.cb_info.p_nv_save_callback)
      (*gatt_cb.cb_info.p_nv_save_callback)(true, &list.asgn_range);
  }

  gatts_init_service_db(list.svc_db, svc_uuid, is_pri, s_hdl, num_handles);

  log::verbose("handles needed={}, s_hdl={}, e_hdl={}, uuid={}, is_primary={}",
               num_handles, loghex(list.asgn_range.s_handle),
               loghex(list.asgn_range.e_handle), list.asgn_range.svc_uuid,
               list.asgn_range.is_primary);

  service->attribute_handle = s_hdl;

  btgatt_db_element_t* el = service + 1;
  for (int i = 0; i < count - 1; i++, el++) {
    const Uuid& uuid = el->uuid;

    if (el->type == BTGATT_DB_CHARACTERISTIC) {
      /* data validity checking */
      if (((el->properties & GATT_CHAR_PROP_BIT_AUTH) &&
           !(el->permissions & GATT_WRITE_SIGNED_PERM)) ||
          ((el->permissions & GATT_WRITE_SIGNED_PERM) &&
           !(el->properties & GATT_CHAR_PROP_BIT_AUTH))) {
        log::verbose("Invalid configuration property={}, perm={}",
                     loghex(el->properties), loghex(el->permissions));
        return GATT_INTERNAL_ERROR;
      }

      if (is_gatt_attr_type(uuid)) {
        log::error(
            "attept to add characteristic with UUID equal to GATT Attribute "
            "Type {}",
            uuid);
        return GATT_INTERNAL_ERROR;
      }

      el->attribute_handle = gatts_add_characteristic(
          list.svc_db, el->permissions, el->properties, uuid);

      // add characteristic extended properties descriptor if needed
      if (el->properties & GATT_CHAR_PROP_BIT_EXT_PROP) {
        gatts_add_char_ext_prop_descr(list.svc_db, el->extended_properties);
      }

    } else if (el->type == BTGATT_DB_DESCRIPTOR) {
      if (is_gatt_attr_type(uuid)) {
        log::error(
            "attept to add descriptor with UUID equal to GATT Attribute Type "
            "{}",
            uuid);
        return GATT_INTERNAL_ERROR;
      }

      el->attribute_handle =
          gatts_add_char_descr(list.svc_db, el->permissions, uuid);
    } else if (el->type == BTGATT_DB_INCLUDED_SERVICE) {
      tGATT_HDL_LIST_ELEM* p_incl_decl;
      p_incl_decl = gatt_find_hdl_buffer_by_handle(el->attribute_handle);
      if (p_incl_decl == nullptr) {
        log::verbose("Included Service not created");
        return GATT_INTERNAL_ERROR;
      }

      el->attribute_handle = gatts_add_included_service(
          list.svc_db, p_incl_decl->asgn_range.s_handle,
          p_incl_decl->asgn_range.e_handle, p_incl_decl->asgn_range.svc_uuid);
    }
  }

  log::info("service parsed correctly, now starting");

  /*this is a new application service start */

  // find a place for this service in the list
  auto lst_ptr = gatt_cb.srv_list_info;
  auto it = lst_ptr->begin();
  for (; it != lst_ptr->end(); it++) {
    if (list.asgn_range.s_handle < it->s_hdl) break;
  }
  auto rit = lst_ptr->emplace(it);

  tGATT_SRV_LIST_ELEM& elem = *rit;
  elem.gatt_if = gatt_if;
  elem.s_hdl = list.asgn_range.s_handle;
  elem.e_hdl = list.asgn_range.e_handle;
  elem.p_db = &list.svc_db;
  elem.is_primary = list.asgn_range.is_primary;

  elem.app_uuid = list.asgn_range.app_uuid128;
  elem.type = list.asgn_range.is_primary ? GATT_UUID_PRI_SERVICE
                                         : GATT_UUID_SEC_SERVICE;

  if (elem.type == GATT_UUID_PRI_SERVICE && gatt_cb.over_br_enabled) {
    Uuid* p_uuid = gatts_get_service_uuid(elem.p_db);
    if (*p_uuid != Uuid::From16Bit(UUID_SERVCLASS_GMCS_SERVER) &&
        *p_uuid != Uuid::From16Bit(UUID_SERVCLASS_GTBS_SERVER)) {
      elem.sdp_handle = gatt_add_sdp_record(*p_uuid, elem.s_hdl, elem.e_hdl);
    } else {
      elem.sdp_handle = 0;
    }
  } else {
    elem.sdp_handle = 0;
  }

  gatt_update_last_srv_info();

  log::verbose("allocated el s_hdl={}, e_hdl={}, type={}, sdp_hdl={}",
               loghex(elem.s_hdl), loghex(elem.e_hdl), loghex(elem.type),
               loghex(elem.sdp_handle));

  gatt_update_for_database_change();
  gatt_proc_srv_chg();

  return GATT_SERVICE_STARTED;
}

bool is_active_service(const Uuid& app_uuid128, Uuid* p_svc_uuid,
                       uint16_t start_handle) {
  for (auto& info : *gatt_cb.srv_list_info) {
    Uuid* p_this_uuid = gatts_get_service_uuid(info.p_db);

    if (p_this_uuid && app_uuid128 == info.app_uuid &&
        *p_svc_uuid == *p_this_uuid && (start_handle == info.s_hdl)) {
      log::error("Active Service Found: {}", *p_svc_uuid);
      return true;
    }
  }
  return false;
}

/*******************************************************************************
 *
 * Function         GATTS_DeleteService
 *
 * Description      This function is called to delete a service.
 *
 * Parameter        gatt_if       : application interface
 *                  p_svc_uuid    : service UUID
 *                  start_handle  : start handle of the service
 *
 * Returns          true if the operation succeeded, false if the handle block
 *                  was not found.
 *
 ******************************************************************************/
bool GATTS_DeleteService(tGATT_IF gatt_if, Uuid* p_svc_uuid,
                         uint16_t svc_inst) {
  log::verbose("");

  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  if (p_reg == NULL) {
    log::error("Applicaiton not foud");
    return false;
  }

  auto it =
      gatt_find_hdl_buffer_by_app_id(p_reg->app_uuid128, p_svc_uuid, svc_inst);
  if (it == gatt_cb.hdl_list_info->end()) {
    log::error("No Service found");
    return false;
  }

  if (is_active_service(p_reg->app_uuid128, p_svc_uuid, svc_inst)) {
    GATTS_StopService(it->asgn_range.s_handle);
  }

  gatt_update_for_database_change();
  gatt_proc_srv_chg();

  log::verbose("released handles s_hdl={}, e_hdl={}",
               loghex(it->asgn_range.s_handle),
               loghex(it->asgn_range.e_handle));

  if ((it->asgn_range.s_handle >= gatt_cb.hdl_cfg.app_start_hdl) &&
      gatt_cb.cb_info.p_nv_save_callback)
    (*gatt_cb.cb_info.p_nv_save_callback)(false, &it->asgn_range);

  gatt_cb.hdl_list_info->erase(it);
  return true;
}

/*******************************************************************************
 *
 * Function         GATTS_StopService
 *
 * Description      This function is called to stop a service
 *
 * Parameter         service_handle : this is the start handle of a service
 *
 * Returns          None.
 *
 ******************************************************************************/
void GATTS_StopService(uint16_t service_handle) {
  log::info("service = {}", loghex(service_handle));

  auto it = gatt_sr_find_i_rcb_by_handle(service_handle);
  if (it == gatt_cb.srv_list_info->end()) {
    log::error("service_handle={} is not in use", loghex(service_handle));
    return;
  }

  if (it->sdp_handle) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(it->sdp_handle);
  }

  gatt_cb.srv_list_info->erase(it);
  gatt_update_last_srv_info();
}
/*******************************************************************************
 *
 * Function         GATTs_HandleValueIndication
 *
 * Description      This function sends a handle value indication to a client.
 *
 * Parameter        conn_id: connection identifier.
 *                  attr_handle: Attribute handle of this handle value
 *                               indication.
 *                  val_len: Length of the indicated attribute value.
 *                  p_val: Pointer to the indicated attribute value data.
 *
 * Returns          GATT_SUCCESS if sucessfully sent or queued; otherwise error
 *                  code.
 *
 ******************************************************************************/
tGATT_STATUS GATTS_HandleValueIndication(uint16_t conn_id, uint16_t attr_handle,
                                         uint16_t val_len, uint8_t* p_val) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);

  log::verbose("");
  if ((p_reg == NULL) || (p_tcb == NULL)) {
    log::error("Unknown  conn_id={}", loghex(conn_id));
    return (tGATT_STATUS)GATT_INVALID_CONN_ID;
  }

  if (!GATT_HANDLE_IS_VALID(attr_handle)) return GATT_ILLEGAL_PARAMETER;

  tGATT_VALUE indication;
  indication.conn_id = conn_id;
  indication.handle = attr_handle;
  indication.len = val_len;
  memcpy(indication.value, p_val, val_len);
  indication.auth_req = GATT_AUTH_REQ_NONE;

  uint16_t* indicate_handle_p = NULL;
  uint16_t cid;

  if (!gatt_tcb_get_cid_available_for_indication(p_tcb, p_reg->eatt_support,
                                                 &indicate_handle_p, &cid)) {
    log::verbose("Add a pending indication");
    gatt_add_pending_ind(p_tcb, &indication);
    return GATT_SUCCESS;
  }

  tGATT_SR_MSG gatt_sr_msg;
  gatt_sr_msg.attr_value = indication;

  uint16_t payload_size = gatt_tcb_get_payload_size(*p_tcb, cid);
  BT_HDR* p_msg = attp_build_sr_msg(*p_tcb, GATT_HANDLE_VALUE_IND, &gatt_sr_msg,
                                    payload_size);
  if (!p_msg) return GATT_NO_RESOURCES;

  tGATT_STATUS cmd_status = attp_send_sr_msg(*p_tcb, cid, p_msg);
  if (cmd_status == GATT_SUCCESS || cmd_status == GATT_CONGESTED) {
    *indicate_handle_p = indication.handle;
    gatt_start_conf_timer(p_tcb, cid);
  }
  return cmd_status;
}

#if (GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_NOTIF == TRUE)
static tGATT_STATUS GATTS_HandleMultileValueNotification(
    tGATT_TCB* p_tcb, std::vector<tGATT_VALUE> gatt_notif_vector) {
  log::info("");

  uint16_t cid = gatt_tcb_get_att_cid(*p_tcb, true /* eatt support */);
  uint16_t payload_size = gatt_tcb_get_payload_size(*p_tcb, cid);

  /* TODO Handle too big packet size here. Not needed now for testing. */
  /* Just build the message. */
  BT_HDR* p_buf =
      (BT_HDR*)osi_malloc(sizeof(BT_HDR) + payload_size + L2CAP_MIN_OFFSET);

  uint8_t* p = (uint8_t*)(p_buf + 1) + L2CAP_MIN_OFFSET;
  UINT8_TO_STREAM(p, GATT_HANDLE_MULTI_VALUE_NOTIF);
  p_buf->offset = L2CAP_MIN_OFFSET;
  p_buf->len = 1;
  for (auto notif : gatt_notif_vector) {
    log::info("Adding handle: 0x{:04x}, val len {}", notif.handle, notif.len);
    UINT16_TO_STREAM(p, notif.handle);
    p_buf->len += 2;
    UINT16_TO_STREAM(p, notif.len);
    p_buf->len += 2;
    ARRAY_TO_STREAM(p, notif.value, notif.len);
    p_buf->len += notif.len;
  }

  log::info("Total len: {}", p_buf->len);

  return attp_send_sr_msg(*p_tcb, cid, p_buf);
}
#endif
/*******************************************************************************
 *
 * Function         GATTS_HandleValueNotification
 *
 * Description      This function sends a handle value notification to a client.
 *
 * Parameter        conn_id: connection identifier.
 *                  attr_handle: Attribute handle of this handle value
 *                               indication.
 *                  val_len: Length of the indicated attribute value.
 *                  p_val: Pointer to the indicated attribute value data.
 *
 * Returns          GATT_SUCCESS if sucessfully sent; otherwise error code.
 *
 ******************************************************************************/
tGATT_STATUS GATTS_HandleValueNotification(uint16_t conn_id,
                                           uint16_t attr_handle,
                                           uint16_t val_len, uint8_t* p_val) {
  tGATT_VALUE notif;
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
#if (GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_NOTIF == TRUE)
  static uint8_t cached_tcb_idx = 0xFF;
  static std::vector<tGATT_VALUE> gatt_notif_vector(2);
  tGATT_VALUE* p_gatt_notif;
#endif

  log::verbose("");

  if ((p_reg == NULL) || (p_tcb == NULL)) {
    log::error("Unknown  conn_id: {}", conn_id);
    return (tGATT_STATUS)GATT_INVALID_CONN_ID;
  }

  if (!GATT_HANDLE_IS_VALID(attr_handle)) {
    return GATT_ILLEGAL_PARAMETER;
  }

#if (GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_NOTIF == TRUE)
  /* Upper tester for Multiple Value length notifications */
  if (stack_config_get_interface()->get_pts_force_eatt_for_notifications() &&
      gatt_sr_is_cl_multi_variable_len_notif_supported(*p_tcb)) {
    if (cached_tcb_idx == 0xFF) {
      log::info("Storing first notification");
      p_gatt_notif = &gatt_notif_vector[0];

      p_gatt_notif->handle = attr_handle;
      p_gatt_notif->len = val_len;
      std::copy(p_val, p_val + val_len, p_gatt_notif->value);

      notif.auth_req = GATT_AUTH_REQ_NONE;

      cached_tcb_idx = tcb_idx;
      return GATT_SUCCESS;
    }

    if (cached_tcb_idx == tcb_idx) {
      log::info("Storing second notification");
      cached_tcb_idx = 0xFF;
      p_gatt_notif = &gatt_notif_vector[1];

      p_gatt_notif->handle = attr_handle;
      p_gatt_notif->len = val_len;
      std::copy(p_val, p_val + val_len, p_gatt_notif->value);

      notif.auth_req = GATT_AUTH_REQ_NONE;

      return GATTS_HandleMultileValueNotification(p_tcb, gatt_notif_vector);
    }

    log::error("PTS Mode: Invalid tcb_idx: {}, cached_tcb_idx: {}", tcb_idx,
               cached_tcb_idx);
  }
#endif

  memset(&notif, 0, sizeof(notif));
  notif.handle = attr_handle;
  notif.len = val_len;
  memcpy(notif.value, p_val, val_len);
  notif.auth_req = GATT_AUTH_REQ_NONE;

  tGATT_STATUS cmd_sent;
  tGATT_SR_MSG gatt_sr_msg;
  gatt_sr_msg.attr_value = notif;

  uint16_t cid = gatt_tcb_get_att_cid(*p_tcb, p_reg->eatt_support);
  uint16_t payload_size = gatt_tcb_get_payload_size(*p_tcb, cid);
  BT_HDR* p_buf = attp_build_sr_msg(*p_tcb, GATT_HANDLE_VALUE_NOTIF,
                                    &gatt_sr_msg, payload_size);

  if (p_buf != NULL) {
    cmd_sent = attp_send_sr_msg(*p_tcb, cid, p_buf);
  } else {
    cmd_sent = GATT_NO_RESOURCES;
  }
  return cmd_sent;
}

/*******************************************************************************
 *
 * Function         GATTS_SendRsp
 *
 * Description      This function sends the server response to client.
 *
 * Parameter        conn_id: connection identifier.
 *                  trans_id: transaction id
 *                  status: response status
 *                  p_msg: pointer to message parameters structure.
 *
 * Returns          GATT_SUCCESS if sucessfully sent; otherwise error code.
 *
 ******************************************************************************/
tGATT_STATUS GATTS_SendRsp(uint16_t conn_id, uint32_t trans_id,
                           tGATT_STATUS status, tGATTS_RSP* p_msg) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);

  log::verbose("conn_id={}, trans_id={}, status={}", loghex(conn_id),
               loghex(trans_id), loghex(static_cast<uint8_t>(status)));

  if ((p_reg == NULL) || (p_tcb == NULL)) {
    log::error("Unknown  conn_id={}", loghex(conn_id));
    return (tGATT_STATUS)GATT_INVALID_CONN_ID;
  }

  tGATT_SR_CMD* sr_res_p = gatt_sr_get_cmd_by_trans_id(p_tcb, trans_id);

  if (!sr_res_p) {
    log::error("conn_id={} waiting for other op_code ", loghex(conn_id));
    return (GATT_WRONG_STATE);
  }

  /* Process App response */
  return gatt_sr_process_app_rsp(*p_tcb, gatt_if, trans_id, sr_res_p->op_code,
                                 status, p_msg, sr_res_p);
}

/******************************************************************************/
/* GATT Profile Srvr Functions */
/******************************************************************************/

/******************************************************************************/
/*                                                                            */
/*                  GATT CLIENT APIs                                          */
/*                                                                            */
/******************************************************************************/

/*******************************************************************************
 *
 * Function         GATTC_ConfigureMTU
 *
 * Description      This function is called to configure the ATT MTU size.
 *
 * Parameters       conn_id: connection identifier.
 *                  mtu    - attribute MTU size..
 *
 * Returns          GATT_SUCCESS if command started successfully.
 *
 ******************************************************************************/
tGATT_STATUS GATTC_ConfigureMTU(uint16_t conn_id, uint16_t mtu) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  if ((p_tcb == NULL) || (p_reg == NULL) || (mtu < GATT_DEF_BLE_MTU_SIZE) ||
      (mtu > GATT_MAX_MTU_SIZE)) {
    log::warn(
        "Unable to configure ATT mtu size illegal parameter conn_id:{} mtu:{} "
        "tcb:{} reg:{}",
        conn_id, mtu, (p_tcb == nullptr) ? "BAD" : "ok",
        (p_reg == nullptr) ? "BAD" : "ok");
    return GATT_ILLEGAL_PARAMETER;
  }

  /* Validate that the link is BLE, not BR/EDR */
  if (p_tcb->transport != BT_TRANSPORT_LE) {
    return GATT_REQ_NOT_SUPPORTED;
  }

  tGATT_CLCB* p_clcb = gatt_clcb_alloc(conn_id);
  if (!p_clcb) {
    log::warn("Unable to allocate connection link control block");
    return GATT_NO_RESOURCES;
  }

  /* For this request only ATT CID is valid */
  p_clcb->cid = L2CAP_ATT_CID;
  p_clcb->operation = GATTC_OPTYPE_CONFIG;
  tGATT_CL_MSG gatt_cl_msg;

  bluetooth::shim::arbiter::GetArbiter().OnOutgoingMtuReq(tcb_idx);

  /* Since GATT MTU Exchange can be done only once, and it is impossible to
   * predict what MTU will be requested by other applications, let's use
   * default MTU in the request. */
  gatt_cl_msg.mtu = gatt_get_local_mtu();

  log::info("Configuring ATT mtu size conn_id:{} mtu:{} user mtu {}", conn_id,
            gatt_cl_msg.mtu, mtu);

  auto result =
      attp_send_cl_msg(*p_clcb->p_tcb, p_clcb, GATT_REQ_MTU, &gatt_cl_msg);
  if (result == GATT_SUCCESS) {
    p_clcb->p_tcb->pending_user_mtu_exchange_value = mtu;
  }
  return result;
}

/******************************************************************************
 *
 * Function         GATTC_TryMtuRequest
 *
 * Description      This function shall be called before calling
 *                  GATTC_ConfgureMTU in order to check if operation is
 *                  available to do.
 *
 * Parameters        remote_bda : peer device address. (input)
 *                   transport  : physical transport of the GATT connection
 *                                 (BR/EDR or LE) (input)
 *                   conn_id    : connection id  (input)
 *                   current_mtu: current mtu on the link (output)
 *
 * Returns          tGATTC_TryMtuRequestResult:
 *                  - MTU_EXCHANGE_NOT_DONE_YET: There was no MTU Exchange
 *                      procedure on the link. User can call GATTC_ConfigureMTU
 *                      now.
 *                  - MTU_EXCHANGE_NOT_ALLOWED : Not allowed for BR/EDR or if
 *                      link does not exist
 *                  - MTU_EXCHANGE_ALREADY_DONE: MTU Exchange is done. MTU
 *                      should be taken from current_mtu
 *                  - MTU_EXCHANGE_IN_PROGRESS : Other use is doing MTU
 *                      Exchange. Conn_id is stored for result.
 *
 ******************************************************************************/
tGATTC_TryMtuRequestResult GATTC_TryMtuRequest(const RawAddress& remote_bda,
                                               tBT_TRANSPORT transport,
                                               uint16_t conn_id,
                                               uint16_t* current_mtu) {
  log::info("{} conn_id=0x{:04x}", remote_bda.ToString(), conn_id);
  *current_mtu = GATT_DEF_BLE_MTU_SIZE;

  if (transport == BT_TRANSPORT_BR_EDR) {
    log::error("Device {} connected over BR/EDR",
               ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return MTU_EXCHANGE_NOT_ALLOWED;
  }

  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(remote_bda, transport);
  if (!p_tcb) {
    log::error("Device {} is not connected ",
               ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return MTU_EXCHANGE_DEVICE_DISCONNECTED;
  }

  if (gatt_is_pending_mtu_exchange(p_tcb)) {
    log::debug("Continue MTU pending for other client.");
    /* MTU Exchange is in progress, started by other GATT Client.
     * Wait until it is completed.
     */
    gatt_set_conn_id_waiting_for_mtu_exchange(p_tcb, conn_id);
    return MTU_EXCHANGE_IN_PROGRESS;
  }

  uint16_t mtu = gatt_get_mtu(remote_bda, transport);
  if (mtu == GATT_DEF_BLE_MTU_SIZE || mtu == 0) {
    log::debug("MTU not yet updated for {}",
               ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return MTU_EXCHANGE_NOT_DONE_YET;
  }

  *current_mtu = mtu;
  return MTU_EXCHANGE_ALREADY_DONE;
}

/*******************************************************************************
 * Function         GATTC_UpdateUserAttMtuIfNeeded
 *
 * Description      This function to be called when user requested MTU after
 *                  MTU Exchange has been already done. This will update data
 *                  length in the controller.
 *
 * Parameters        remote_bda : peer device address. (input)
 *                   transport  : physical transport of the GATT connection
 *                                 (BR/EDR or LE) (input)
 *                   user_mtu: user request mtu
 *
 ******************************************************************************/
void GATTC_UpdateUserAttMtuIfNeeded(const RawAddress& remote_bda,
                                    tBT_TRANSPORT transport,
                                    uint16_t user_mtu) {
  log::info("{}, mtu={}", ADDRESS_TO_LOGGABLE_CSTR(remote_bda), user_mtu);
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(remote_bda, transport);
  if (!p_tcb) {
    log::warn("Transport control block not found");
    return;
  }

  log::info("{}, current mtu: {}, max_user_mtu:{}, user_mtu: {}",
            ADDRESS_TO_LOGGABLE_CSTR(remote_bda), p_tcb->payload_size,
            p_tcb->max_user_mtu, user_mtu);

  if (p_tcb->payload_size < user_mtu) {
    log::info("User requested more than what GATT can handle. Trim it.");
    user_mtu = p_tcb->payload_size;
  }

  if (p_tcb->max_user_mtu >= user_mtu) {
    return;
  }

  p_tcb->max_user_mtu = user_mtu;
  BTM_SetBleDataLength(remote_bda, user_mtu);
}

std::list<uint16_t> GATTC_GetAndRemoveListOfConnIdsWaitingForMtuRequest(
    const RawAddress& remote_bda) {
  std::list result = std::list<uint16_t>();

  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(remote_bda, BT_TRANSPORT_LE);
  if (!p_tcb || p_tcb->conn_ids_waiting_for_mtu_exchange.empty()) {
    return result;
  }

  result.swap(p_tcb->conn_ids_waiting_for_mtu_exchange);
  return result;
}

/*******************************************************************************
 *
 * Function         GATTC_Discover
 *
 * Description      This function is called to do a discovery procedure on ATT
 *                  server.
 *
 * Parameters       conn_id: connection identifier.
 *                  disc_type:discovery type.
 *                  start_handle and end_handle: range of handles for discovery
 *                  uuid: uuid to discovery. set to Uuid::kEmpty for requests
 *                        that don't need it
 *
 * Returns          GATT_SUCCESS if command received/sent successfully.
 *
 ******************************************************************************/
tGATT_STATUS GATTC_Discover(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                            uint16_t start_handle, uint16_t end_handle,
                            const Uuid& uuid) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  if ((p_tcb == NULL) || (p_reg == NULL) || (disc_type >= GATT_DISC_MAX)) {
    log::error("Illegal param: disc_type={} conn_id={}", disc_type,
               loghex(conn_id));
    return GATT_ILLEGAL_PARAMETER;
  }

  if (!GATT_HANDLE_IS_VALID(start_handle) ||
      !GATT_HANDLE_IS_VALID(end_handle) ||
      /* search by type does not have a valid UUID param */
      (disc_type == GATT_DISC_SRVC_BY_UUID && uuid.IsEmpty())) {
    log::warn(
        "Illegal parameter conn_id={}, disc_type={}, s_handle={}, e_handle={}",
        loghex(conn_id), disc_type, loghex(start_handle), loghex(end_handle));
    return GATT_ILLEGAL_PARAMETER;
  }

  tGATT_CLCB* p_clcb = gatt_clcb_alloc(conn_id);
  if (!p_clcb) {
    log::warn("No resources conn_id={}, disc_type={}, s_handle={}, e_handle={}",
              loghex(conn_id), disc_type, loghex(start_handle),
              loghex(end_handle));
    return GATT_NO_RESOURCES;
  }

  p_clcb->operation = GATTC_OPTYPE_DISCOVERY;
  p_clcb->op_subtype = disc_type;
  p_clcb->s_handle = start_handle;
  p_clcb->e_handle = end_handle;
  p_clcb->uuid = uuid;

  log::info("conn_id={}, disc_type={}, s_handle={}, e_handle={}",
            loghex(conn_id), disc_type, loghex(start_handle),
            loghex(end_handle));

  gatt_act_discovery(p_clcb);
  return GATT_SUCCESS;
}

tGATT_STATUS GATTC_Discover(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                            uint16_t start_handle, uint16_t end_handle) {
  return GATTC_Discover(conn_id, disc_type, start_handle, end_handle,
                        Uuid::kEmpty);
}

/*******************************************************************************
 *
 * Function         GATTC_Read
 *
 * Description      This function is called to read the value of an attribute
 *                  from the server.
 *
 * Parameters       conn_id: connection identifier.
 *                  type    - attribute read type.
 *                  p_read  - read operation parameters.
 *
 * Returns          GATT_SUCCESS if command started successfully.
 *
 ******************************************************************************/
tGATT_STATUS GATTC_Read(uint16_t conn_id, tGATT_READ_TYPE type,
                        tGATT_READ_PARAM* p_read) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
#if (GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_READ == TRUE)
  static uint16_t cached_read_handle;
  static int cached_tcb_idx = -1;
#endif

  log::verbose("conn_id={}, type={}", loghex(conn_id), loghex(type));

  if ((p_tcb == NULL) || (p_reg == NULL) || (p_read == NULL) ||
      ((type >= GATT_READ_MAX) || (type == 0))) {
    log::error("illegal param: conn_id={}, type={}", loghex(conn_id),
               loghex(type));
    return GATT_ILLEGAL_PARAMETER;
  }

  tGATT_CLCB* p_clcb = gatt_clcb_alloc(conn_id);
  if (!p_clcb) return GATT_NO_RESOURCES;

  p_clcb->operation = GATTC_OPTYPE_READ;
  p_clcb->op_subtype = type;
  p_clcb->auth_req = p_read->by_handle.auth_req;
  p_clcb->counter = 0;
  p_clcb->read_req_current_mtu = gatt_tcb_get_payload_size(*p_tcb, p_clcb->cid);

  switch (type) {
    case GATT_READ_BY_TYPE:
    case GATT_READ_CHAR_VALUE:
      p_clcb->s_handle = p_read->service.s_handle;
      p_clcb->e_handle = p_read->service.e_handle;
      p_clcb->uuid = p_read->service.uuid;
      break;
    case GATT_READ_MULTIPLE:
    case GATT_READ_MULTIPLE_VAR_LEN: {
      p_clcb->s_handle = 0;
      /* copy multiple handles in CB */
      tGATT_READ_MULTI* p_read_multi =
          (tGATT_READ_MULTI*)osi_malloc(sizeof(tGATT_READ_MULTI));
      p_clcb->p_attr_buf = (uint8_t*)p_read_multi;
      memcpy(p_read_multi, &p_read->read_multiple, sizeof(tGATT_READ_MULTI));
      break;
    }
    case GATT_READ_BY_HANDLE:
#if (GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_READ == TRUE)
      log::info("Upper tester: Handle read 0x{:04x}", p_read->by_handle.handle);
      /* This is upper tester for the  Multi Read stuff as this is mandatory for
       * EATT, even Android is not making use of this operation :/ */
      if (cached_tcb_idx < 0) {
        cached_tcb_idx = tcb_idx;
        log::info("Upper tester: Read multiple  - first read");
        cached_read_handle = p_read->by_handle.handle;
      } else if (cached_tcb_idx == tcb_idx) {
        log::info("Upper tester: Read multiple  - second read");
        cached_tcb_idx = -1;
        tGATT_READ_MULTI* p_read_multi =
            (tGATT_READ_MULTI*)osi_malloc(sizeof(tGATT_READ_MULTI));
        p_read_multi->num_handles = 2;
        p_read_multi->handles[0] = cached_read_handle;
        p_read_multi->handles[1] = p_read->by_handle.handle;
        p_read_multi->variable_len = true;

        p_clcb->s_handle = 0;
        p_clcb->op_subtype = GATT_READ_MULTIPLE_VAR_LEN;
        p_clcb->p_attr_buf = (uint8_t*)p_read_multi;
        p_clcb->cid = gatt_tcb_get_att_cid(*p_tcb, true /* eatt support */);

        break;
      }

      FALLTHROUGH_INTENDED;
#endif
    case GATT_READ_PARTIAL:
      p_clcb->uuid = Uuid::kEmpty;
      p_clcb->s_handle = p_read->by_handle.handle;

      if (type == GATT_READ_PARTIAL) {
        p_clcb->counter = p_read->partial.offset;
      }

      break;
    default:
      break;
  }

  /* start security check */
  if (gatt_security_check_start(p_clcb))
    p_tcb->pending_enc_clcb.push_back(p_clcb);
  return GATT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         GATTC_Write
 *
 * Description      This function is called to write the value of an attribute
 *                  to the server.
 *
 * Parameters       conn_id: connection identifier.
 *                  type    - attribute write type.
 *                  p_write  - write operation parameters.
 *
 * Returns          GATT_SUCCESS if command started successfully.
 *
 ******************************************************************************/
tGATT_STATUS GATTC_Write(uint16_t conn_id, tGATT_WRITE_TYPE type,
                         tGATT_VALUE* p_write) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  if ((p_tcb == NULL) || (p_reg == NULL) || (p_write == NULL) ||
      ((type != GATT_WRITE) && (type != GATT_WRITE_PREPARE) &&
       (type != GATT_WRITE_NO_RSP))) {
    log::error("Illegal param: conn_id={}, type={}", loghex(conn_id),
               loghex(type));
    return GATT_ILLEGAL_PARAMETER;
  }

  tGATT_CLCB* p_clcb = gatt_clcb_alloc(conn_id);
  if (!p_clcb) return GATT_NO_RESOURCES;

  p_clcb->operation = GATTC_OPTYPE_WRITE;
  p_clcb->op_subtype = type;
  p_clcb->auth_req = p_write->auth_req;

  p_clcb->p_attr_buf = (uint8_t*)osi_malloc(sizeof(tGATT_VALUE));
  memcpy(p_clcb->p_attr_buf, (void*)p_write, sizeof(tGATT_VALUE));

  tGATT_VALUE* p = (tGATT_VALUE*)p_clcb->p_attr_buf;
  if (type == GATT_WRITE_PREPARE) {
    p_clcb->start_offset = p_write->offset;
    p->offset = 0;
  }

  if (gatt_security_check_start(p_clcb))
    p_tcb->pending_enc_clcb.push_back(p_clcb);
  return GATT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         GATTC_ExecuteWrite
 *
 * Description      This function is called to send an Execute write request to
 *                  the server.
 *
 * Parameters       conn_id: connection identifier.
 *                  is_execute - to execute or cancel the prepared write
 *                               request(s)
 *
 * Returns          GATT_SUCCESS if command started successfully.
 *
 ******************************************************************************/
tGATT_STATUS GATTC_ExecuteWrite(uint16_t conn_id, bool is_execute) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  log::verbose("conn_id={}, is_execute={}", loghex(conn_id), is_execute);

  if ((p_tcb == NULL) || (p_reg == NULL)) {
    log::error(" Illegal param: conn_id={}", loghex(conn_id));
    return GATT_ILLEGAL_PARAMETER;
  }

  tGATT_CLCB* p_clcb = gatt_clcb_alloc(conn_id);
  if (!p_clcb) return GATT_NO_RESOURCES;

  p_clcb->operation = GATTC_OPTYPE_EXE_WRITE;
  tGATT_EXEC_FLAG flag =
      is_execute ? GATT_PREP_WRITE_EXEC : GATT_PREP_WRITE_CANCEL;
  gatt_send_queue_write_cancel(*p_clcb->p_tcb, p_clcb, flag);
  return GATT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         GATTC_SendHandleValueConfirm
 *
 * Description      This function is called to send a handle value confirmation
 *                  as response to a handle value notification from server.
 *
 * Parameters       conn_id: connection identifier.
 *                  cid: channel id.
 *
 * Returns          GATT_SUCCESS if command started successfully.
 *
 ******************************************************************************/
tGATT_STATUS GATTC_SendHandleValueConfirm(uint16_t conn_id, uint16_t cid) {
  log::info("conn_id=0x{:04x} , cid=0x{:04x}", conn_id, cid);

  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(GATT_GET_TCB_IDX(conn_id));
  if (!p_tcb) {
    log::error("Unknown conn_id={}", loghex(conn_id));
    return GATT_ILLEGAL_PARAMETER;
  }

  if (p_tcb->ind_count == 0) {
    log::info("conn_id: 0x{:04x} ignored not waiting for indicaiton ack",
              conn_id);
    return GATT_SUCCESS;
  }

  log::info("Received confirmation, ind_count= {}, sending confirmation",
            p_tcb->ind_count);

  /* Just wait for first confirmation.*/
  p_tcb->ind_count = 0;
  gatt_stop_ind_ack_timer(p_tcb, cid);

  /* send confirmation now */
  return attp_send_cl_confirmation_msg(*p_tcb, cid);
}

/******************************************************************************/
/*                                                                            */
/*                  GATT  APIs                                                */
/*                                                                            */
/******************************************************************************/
/*******************************************************************************
 *
 * Function         GATT_SetIdleTimeout
 *
 * Description      This function (common to both client and server) sets the
 *                  idle timeout for a tansport connection
 *
 * Parameter        bd_addr:   target device bd address.
 *                  idle_tout: timeout value in seconds.
 *                  transport: transport option.
 *                  is_active: whether we should use this as a signal that an
 *                             active client now exists (which changes link
 *                             timeout logic, see
 *                             t_l2c_linkcb.with_active_local_clients for
 *                             details).
 *
 * Returns          void
 *
 ******************************************************************************/
void GATT_SetIdleTimeout(const RawAddress& bd_addr, uint16_t idle_tout,
                         tBT_TRANSPORT transport, bool is_active) {
  bool status = false;

  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, transport);
  if (p_tcb != nullptr) {
    status = L2CA_SetLeGattTimeout(bd_addr, idle_tout);

    if (is_active) {
      status &= L2CA_MarkLeLinkAsActive(bd_addr);
    }

    if (idle_tout == GATT_LINK_IDLE_TIMEOUT_WHEN_NO_APP) {
      L2CA_SetIdleTimeoutByBdAddr(
          p_tcb->peer_bda, GATT_LINK_IDLE_TIMEOUT_WHEN_NO_APP, BT_TRANSPORT_LE);
    }
  }

  log::info("idle_timeout={}, is_active={}, status={} (1-OK 0-not performed)",
            idle_tout, is_active, status);
}

/*******************************************************************************
 *
 * Function         GATT_Register
 *
 * Description      This function is called to register an  application
 *                  with GATT
 *
 * Parameter        p_app_uuid128: Application UUID
 *                  p_cb_info: callback functions.
 *                  eatt_support: indicate eatt support.
 *
 * Returns          0 for error, otherwise the index of the client registered
 *                  with GATT
 *
 ******************************************************************************/
tGATT_IF GATT_Register(const Uuid& app_uuid128, const std::string& name,
                       tGATT_CBACK* p_cb_info, bool eatt_support) {
  tGATT_REG* p_reg;
  uint8_t i_gatt_if = 0;
  tGATT_IF gatt_if = 0;

  for (i_gatt_if = 0, p_reg = gatt_cb.cl_rcb; i_gatt_if < GATT_MAX_APPS;
       i_gatt_if++, p_reg++) {
    if (p_reg->in_use && p_reg->app_uuid128 == app_uuid128) {
      log::error("Application already registered, uuid={}",
                 app_uuid128.ToString());
      return 0;
    }
  }

  if (stack_config_get_interface()->get_pts_use_eatt_for_all_services()) {
    log::info("PTS: Force to use EATT for servers");
    eatt_support = true;
  }

  for (i_gatt_if = 0, p_reg = gatt_cb.cl_rcb; i_gatt_if < GATT_MAX_APPS;
       i_gatt_if++, p_reg++) {
    if (!p_reg->in_use) {
      *p_reg = {};
      i_gatt_if++; /* one based number */
      p_reg->app_uuid128 = app_uuid128;
      gatt_if = p_reg->gatt_if = (tGATT_IF)i_gatt_if;
      p_reg->app_cb = *p_cb_info;
      p_reg->in_use = true;
      p_reg->eatt_support = eatt_support;
      p_reg->name = name;
      log::info("Allocated name:{} uuid:{} gatt_if:{} eatt_support:{}", name,
                app_uuid128.ToString(), gatt_if, eatt_support);
      return gatt_if;
    }
  }

  log::error("Unable to register GATT client, MAX client reached: {}",
             GATT_MAX_APPS);
  return 0;
}

/*******************************************************************************
 *
 * Function         GATT_Deregister
 *
 * Description      This function deregistered the application from GATT.
 *
 * Parameters       gatt_if: applicaiton interface.
 *
 * Returns          None.
 *
 ******************************************************************************/
void GATT_Deregister(tGATT_IF gatt_if) {
  log::info("gatt_if={}", +gatt_if);

  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  /* Index 0 is GAP and is never deregistered */
  if ((gatt_if == 0) || (p_reg == NULL)) {
    log::error("Unable to deregister client with invalid gatt_if={}", gatt_if);
    return;
  }

  /* stop all services  */
  /* todo an applcaiton can not be deregistered if its services is also used by
    other application
    deregisteration need to bed performed in an orderly fashion
    no check for now */
  for (auto it = gatt_cb.srv_list_info->begin();
       it != gatt_cb.srv_list_info->end();) {
    if (it->gatt_if == gatt_if) {
      GATTS_StopService(it++->s_hdl);
    } else {
      ++it;
    }
  }

  /* free all services db buffers if owned by this application */
  gatt_free_srvc_db_buffer_app_id(p_reg->app_uuid128);

  /* When an application deregisters, check remove the link associated with the
   * app */
  tGATT_TCB* p_tcb;
  int i;
  for (i = 0, p_tcb = gatt_cb.tcb; i < GATT_MAX_PHY_CHANNEL; i++, p_tcb++) {
    if (!p_tcb->in_use) continue;

    if (gatt_get_ch_state(p_tcb) != GATT_CH_CLOSE) {
      gatt_update_app_use_link_flag(gatt_if, p_tcb, false, true);
    }

    for (auto clcb_it = gatt_cb.clcb_queue.begin();
         clcb_it != gatt_cb.clcb_queue.end();) {
      if ((clcb_it->p_reg->gatt_if == gatt_if) &&
          (clcb_it->p_tcb->tcb_idx == p_tcb->tcb_idx)) {
        alarm_cancel(clcb_it->gatt_rsp_timer_ent);
        gatt_clcb_invalidate(p_tcb, &(*clcb_it));
        clcb_it = gatt_cb.clcb_queue.erase(clcb_it);
      } else {
        clcb_it++;
      }
    }
  }

  if (bluetooth::common::init_flags::
          use_unified_connection_manager_is_enabled()) {
    bluetooth::connection::GetConnectionManager().remove_client(gatt_if);
  } else {
    connection_manager::on_app_deregistered(gatt_if);
  }

  *p_reg = {};
}

/*******************************************************************************
 *
 * Function         GATT_StartIf
 *
 * Description      This function is called after registration to start
 *                  receiving callbacks for registered interface.  Function may
 *                  call back with connection status and queued notifications
 *
 * Parameter        gatt_if: applicaiton interface.
 *
 * Returns          None.
 *
 ******************************************************************************/
void GATT_StartIf(tGATT_IF gatt_if) {
  tGATT_REG* p_reg;
  tGATT_TCB* p_tcb;
  RawAddress bda = {};
  uint8_t start_idx, found_idx;
  uint16_t conn_id;
  tBT_TRANSPORT transport;

  log::debug("Starting GATT interface gatt_if_:{}", gatt_if);

  p_reg = gatt_get_regcb(gatt_if);
  if (p_reg != NULL) {
    start_idx = 0;
    while (
        gatt_find_the_connected_bda(start_idx, bda, &found_idx, &transport)) {
      p_tcb = gatt_find_tcb_by_addr(bda, transport);
      log::info("GATT interface {} already has connected device {}", gatt_if,
                ADDRESS_TO_LOGGABLE_CSTR(bda));
      if (p_reg->app_cb.p_conn_cb && p_tcb) {
        conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, gatt_if);
        log::info("Invoking callback with connection id {}", conn_id);
        (*p_reg->app_cb.p_conn_cb)(gatt_if, bda, conn_id, true, GATT_CONN_OK,
                                   transport);
      } else {
        log::info("Skipping callback as none is registered");
      }
      start_idx = ++found_idx;
    }
  }
}

/*******************************************************************************
 *
 * Function         GATT_Connect
 *
 * Description      This function initiate a connecttion to a remote device on
 *                  GATT channel.
 *
 * Parameters       gatt_if: applicaiton interface
 *                  bd_addr: peer device address.
 *                  connection_type: is a direct conenection or a background
 *                  auto connection or targeted announcements
 *
 * Returns          true if connection started; false if connection start
 *                  failure.
 *
 ******************************************************************************/
bool GATT_Connect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                  tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
                  bool opportunistic) {
  uint8_t phy = controller_get_interface()->get_le_all_initiating_phys();
  return GATT_Connect(gatt_if, bd_addr, connection_type, transport,
                      opportunistic, phy);
}

bool GATT_Connect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                  tBLE_ADDR_TYPE addr_type, tBTM_BLE_CONN_TYPE connection_type,
                  tBT_TRANSPORT transport, bool opportunistic,
                  uint8_t initiating_phys) {
  /* Make sure app is registered */
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  if (!p_reg) {
    log::error("Unable to find registered app gatt_if={}", gatt_if);
    return false;
  }

  bool is_direct = (connection_type == BTM_BLE_DIRECT_CONNECTION);

  if (!is_direct && transport != BT_TRANSPORT_LE) {
    log::warn("Unsupported transport for background connection gatt_if={}",
              gatt_if);
    return false;
  }

  if (opportunistic) {
    log::info("Registered for opportunistic connection gatt_if={}", gatt_if);
    return true;
  }

  bool ret = false;
  if (is_direct) {
    log::debug("Starting direct connect gatt_if={} address={}", gatt_if,
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    ret =
        gatt_act_connect(p_reg, bd_addr, addr_type, transport, initiating_phys);
  } else {
    log::debug("Starting background connect gatt_if={} address={}", gatt_if,
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    if (!BTM_Sec_AddressKnown(bd_addr)) {
      //  RPA can rotate, causing address to "expire" in the background
      //  connection list. RPA is allowed for direct connect, as such request
      //  times out after 30 seconds
      log::warn("Unable to add RPA {} to background connection gatt_if={}",
                ADDRESS_TO_LOGGABLE_CSTR(bd_addr), gatt_if);
      ret = false;
    } else {
      log::debug("Adding to background connect to device:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      if (bluetooth::common::init_flags::
              use_unified_connection_manager_is_enabled()) {
        if (connection_type == BTM_BLE_BKG_CONNECT_ALLOW_LIST) {
          bluetooth::connection::GetConnectionManager()
              .add_background_connection(
                  gatt_if, bluetooth::connection::ResolveRawAddress(bd_addr));
          ret = true;  // TODO(aryarahul): error handling
        } else {
          log::fatal("unimplemented, TODO(aryarahul)");
        }
      } else {
        if (connection_type == BTM_BLE_BKG_CONNECT_ALLOW_LIST) {
          ret = connection_manager::background_connect_add(gatt_if, bd_addr);
        } else {
          ret =
              connection_manager::background_connect_targeted_announcement_add(
                  gatt_if, bd_addr);
        }
      }
    }
  }

  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, transport);
  // background connections don't necessarily create tcb
  if (p_tcb && ret) {
    gatt_update_app_use_link_flag(p_reg->gatt_if, p_tcb, true, !is_direct);
  } else {
    if (p_tcb == nullptr) {
      log::debug("p_tcb is null");
    }
    if (!ret) {
      log::debug("Previous step returned false");
    }
  }

  return ret;
}

bool GATT_Connect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                  tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
                  bool opportunistic, uint8_t initiating_phys) {
  return GATT_Connect(gatt_if, bd_addr, BLE_ADDR_PUBLIC, connection_type,
                      transport, opportunistic, initiating_phys);
}

/*******************************************************************************
 *
 * Function         GATT_CancelConnect
 *
 * Description      This function terminate the connection initaition to a
 *                  remote device on GATT channel.
 *
 * Parameters       gatt_if: client interface. If 0 used as unconditionally
 *                           disconnect, typically used for direct connection
 *                           cancellation.
 *                  bd_addr: peer device address.
 *
 * Returns          true if the connection started; false otherwise.
 *
 ******************************************************************************/
bool GATT_CancelConnect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                        bool is_direct) {
  log::info("gatt_if:{}, address: {}, direct:{}", gatt_if,
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr), is_direct);

  tGATT_REG* p_reg;
  if (gatt_if) {
    p_reg = gatt_get_regcb(gatt_if);
    if (!p_reg) {
      log::error("gatt_if={} is not registered", gatt_if);
      return false;
    }

    if (is_direct) {
      return gatt_cancel_open(gatt_if, bd_addr);
    } else {
      return gatt_auto_connect_dev_remove(p_reg->gatt_if, bd_addr);
    }
  }

  log::verbose(" unconditional");

  /* only LE connection can be cancelled */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, BT_TRANSPORT_LE);
  if (p_tcb && !p_tcb->app_hold_link.empty()) {
    for (auto it = p_tcb->app_hold_link.begin();
         it != p_tcb->app_hold_link.end();) {
      auto next = std::next(it);
      // gatt_cancel_open modifies the app_hold_link.
      gatt_cancel_open(*it, bd_addr);

      it = next;
    }
  }

  if (bluetooth::common::init_flags::
          use_unified_connection_manager_is_enabled()) {
    bluetooth::connection::GetConnectionManager()
        .stop_all_connections_to_device(
            bluetooth::connection::ResolveRawAddress(bd_addr));
  } else {
    if (!connection_manager::remove_unconditional(bd_addr)) {
      log::error(
          "no app associated with the bg device for unconditional removal");
      return false;
    }
  }

  return true;
}

/*******************************************************************************
 *
 * Function         GATT_Disconnect
 *
 * Description      This function disconnects the GATT channel for this
 *                  registered application.
 *
 * Parameters       conn_id: connection identifier.
 *
 * Returns          GATT_SUCCESS if disconnected.
 *
 ******************************************************************************/
tGATT_STATUS GATT_Disconnect(uint16_t conn_id) {
  log::info("conn_id={}", conn_id);

  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  if (!p_tcb) {
    log::warn("Cannot find TCB for connection {}", conn_id);
    return GATT_ILLEGAL_PARAMETER;
  }

  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  gatt_update_app_use_link_flag(gatt_if, p_tcb, false, true);
  return GATT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         GATT_GetConnectionInfor
 *
 * Description      This function uses conn_id to find its associated BD address
 *                  and application interface
 *
 * Parameters        conn_id: connection id  (input)
 *                   p_gatt_if: applicaiton interface (output)
 *                   bd_addr: peer device address. (output)
 *
 * Returns          true the ligical link information is found for conn_id
 *
 ******************************************************************************/
bool GATT_GetConnectionInfor(uint16_t conn_id, tGATT_IF* p_gatt_if,
                             RawAddress& bd_addr, tBT_TRANSPORT* p_transport) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);

  log::verbose("conn_id={}", loghex(conn_id));

  if (!p_tcb || !p_reg) return false;

  bd_addr = p_tcb->peer_bda;
  *p_gatt_if = gatt_if;
  *p_transport = p_tcb->transport;
  return true;
}

/*******************************************************************************
 *
 * Function         GATT_GetConnIdIfConnected
 *
 * Description      This function find the conn_id if the logical link for BD
 *                  address and applciation interface is connected
 *
 * Parameters        gatt_if: applicaiton interface (input)
 *                   bd_addr: peer device address. (input)
 *                   p_conn_id: connection id  (output)
 *                   transport: transport option
 *
 * Returns          true the logical link is connected
 *
 ******************************************************************************/
bool GATT_GetConnIdIfConnected(tGATT_IF gatt_if, const RawAddress& bd_addr,
                               uint16_t* p_conn_id, tBT_TRANSPORT transport) {
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, transport);
  bool status = false;

  if (p_reg && p_tcb && (gatt_get_ch_state(p_tcb) == GATT_CH_OPEN)) {
    *p_conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, gatt_if);
    status = true;
  }

  log::debug("status={}", status);
  return status;
}

static void gatt_bonded_check_add_address(const RawAddress& bda) {
  if (!gatt_is_bda_in_the_srv_chg_clt_list(bda)) {
    gatt_add_a_bonded_dev_for_srv_chg(bda);
  }
}

std::optional<bool> OVERRIDE_GATT_LOAD_BONDED = std::nullopt;

static bool gatt_load_bonded_is_enabled() {
  static const bool sGATT_LOAD_BONDED = bluetooth::os::GetSystemPropertyBool(
      "bluetooth.gatt.load_bonded.enabled", false);
  if (OVERRIDE_GATT_LOAD_BONDED.has_value()) {
    return OVERRIDE_GATT_LOAD_BONDED.value();
  }
  return sGATT_LOAD_BONDED;
}

/* Initialize GATTS list of bonded device service change updates.
 *
 * Addresses for bonded devices (publict for BR/EDR or pseudo for BLE) are added
 * to GATTS service change control list so that updates are sent to bonded
 * devices on next connect after any handles for GATTS services change due to
 * services added/removed.
 */
void gatt_load_bonded(void) {
  const bool load_bonded = gatt_load_bonded_is_enabled();
  log::info("load bonded: {}", load_bonded ? "True" : "False");
  if (!load_bonded) {
    return;
  }
  for (tBTM_SEC_DEV_REC* p_dev_rec : btm_get_sec_dev_rec()) {
    if (p_dev_rec->sec_rec.is_link_key_known()) {
      log::verbose("Add bonded BR/EDR transport {}",
                   ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->bd_addr));
      gatt_bonded_check_add_address(p_dev_rec->bd_addr);
    }
    if (p_dev_rec->sec_rec.is_le_link_key_known()) {
      log::verbose("Add bonded BLE {}",
                   ADDRESS_TO_LOGGABLE_CSTR(p_dev_rec->ble.pseudo_addr));
      gatt_bonded_check_add_address(p_dev_rec->ble.pseudo_addr);
    }
  }
}
