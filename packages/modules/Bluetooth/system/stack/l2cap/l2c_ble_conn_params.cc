/******************************************************************************
 *
 *  Copyright 2024 The Android Open Source Project
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
 *  this file contains functions relating to BLE connection parameter
 *management.
 *
 ******************************************************************************/

#define LOG_TAG "l2c_ble_conn_params"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <log/log.h>

#include "internal_include/stack_config.h"
#include "main/shim/acl_api.h"
#include "os/log.h"
#include "stack/btm/btm_dev.h"
#include "stack/include/acl_api.h"
#include "stack/include/btm_ble_api_types.h"
#include "stack/include/l2c_api.h"
#include "stack/l2cap/l2c_int.h"
#include "types/raw_address.h"

using namespace bluetooth;

void l2cble_start_conn_update(tL2C_LCB* p_lcb);
static void l2cble_start_subrate_change(tL2C_LCB* p_lcb);

/*******************************************************************************
 *
 *  Function        L2CA_UpdateBleConnParams
 *
 *  Description     Update BLE connection parameters.
 *
 *  Parameters:     BD Address of remote
 *
 *  Return value:   true if update started
 *
 ******************************************************************************/
bool L2CA_UpdateBleConnParams(const RawAddress& rem_bda, uint16_t min_int,
                              uint16_t max_int, uint16_t latency,
                              uint16_t timeout, uint16_t min_ce_len,
                              uint16_t max_ce_len) {
  tL2C_LCB* p_lcb;

  /* See if we have a link control block for the remote device */
  p_lcb = l2cu_find_lcb_by_bd_addr(rem_bda, BT_TRANSPORT_LE);

  /* If we do not have one, create one and accept the connection. */
  if (!p_lcb || !BTM_IsAclConnectionUp(rem_bda, BT_TRANSPORT_LE)) {
    log::warn("- unknown BD_ADDR {}", ADDRESS_TO_LOGGABLE_STR(rem_bda));
    return (false);
  }

  if (p_lcb->transport != BT_TRANSPORT_LE) {
    log::warn("- BD_ADDR {} not LE", ADDRESS_TO_LOGGABLE_STR(rem_bda));
    return (false);
  }

  log::verbose(
      "BD_ADDR={}, min_int={}, max_int={}, min_ce_len={}, max_ce_len={}",
      ADDRESS_TO_LOGGABLE_STR(rem_bda), min_int, max_int, min_ce_len,
      max_ce_len);

  p_lcb->min_interval = min_int;
  p_lcb->max_interval = max_int;
  p_lcb->latency = latency;
  p_lcb->timeout = timeout;
  p_lcb->conn_update_mask |= L2C_BLE_NEW_CONN_PARAM;
  p_lcb->min_ce_len = min_ce_len;
  p_lcb->max_ce_len = max_ce_len;

  l2cble_start_conn_update(p_lcb);

  return (true);
}

static bool l2c_enable_update_ble_conn_params(tL2C_LCB* p_lcb, bool enable);

/* When called with lock=true, LE connection parameters will be locked on
 * fastest value, and we won't accept request to change it from remote. When
 * called with lock=false, parameters are relaxed.
 */
void L2CA_LockBleConnParamsForServiceDiscovery(const RawAddress& rem_bda,
                                               bool lock) {
  if (stack_config_get_interface()->get_pts_conn_updates_disabled()) return;

  tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(rem_bda, BT_TRANSPORT_LE);
  if (!p_lcb) {
    log::warn("unknown address {}", ADDRESS_TO_LOGGABLE_CSTR(rem_bda));
    return;
  }

  if (p_lcb->transport != BT_TRANSPORT_LE) {
    log::warn("{} not LE, link role {}", ADDRESS_TO_LOGGABLE_CSTR(rem_bda),
              p_lcb->LinkRole());
    return;
  }

  if (lock == p_lcb->conn_update_blocked_by_service_discovery) {
    log::warn("{} service discovery already locked/unlocked conn params: {}",
              ADDRESS_TO_LOGGABLE_CSTR(rem_bda), lock);
    return;
  }

  p_lcb->conn_update_blocked_by_service_discovery = lock;

  if (p_lcb->conn_update_blocked_by_profile_connection) {
    log::info("{} conn params stay locked because of audio setup",
              ADDRESS_TO_LOGGABLE_CSTR(rem_bda));
    return;
  }

  log::info("{} Locking/unlocking conn params for service discovery: {}",
            ADDRESS_TO_LOGGABLE_CSTR(rem_bda), lock);
  l2c_enable_update_ble_conn_params(p_lcb, !lock);
}

/* When called with lock=true, LE connection parameters will be locked on
 * fastest value, and we won't accept request to change it from remote. When
 * called with lock=false, parameters are relaxed.
 */
void L2CA_LockBleConnParamsForProfileConnection(const RawAddress& rem_bda,
                                                bool lock) {
  if (stack_config_get_interface()->get_pts_conn_updates_disabled()) return;

  tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(rem_bda, BT_TRANSPORT_LE);
  if (!p_lcb) {
    log::warn("unknown address {}", ADDRESS_TO_LOGGABLE_CSTR(rem_bda));
    return;
  }

  if (p_lcb->transport != BT_TRANSPORT_LE) {
    log::warn("{} not LE, link role {}", ADDRESS_TO_LOGGABLE_CSTR(rem_bda),
              p_lcb->LinkRole());
    return;
  }

  if (lock == p_lcb->conn_update_blocked_by_profile_connection) {
    log::info("{} audio setup already locked/unlocked conn params: {}",
              ADDRESS_TO_LOGGABLE_CSTR(rem_bda), lock);
    return;
  }

  p_lcb->conn_update_blocked_by_profile_connection = lock;

  if (p_lcb->conn_update_blocked_by_service_discovery) {
    log::info("{} conn params stay locked because of service discovery",
              ADDRESS_TO_LOGGABLE_CSTR(rem_bda));
    return;
  }

  log::info("{} Locking/unlocking conn params for audio setup: {}",
            ADDRESS_TO_LOGGABLE_CSTR(rem_bda), lock);
  l2c_enable_update_ble_conn_params(p_lcb, !lock);
}

static bool l2c_enable_update_ble_conn_params(tL2C_LCB* p_lcb, bool enable) {
  log::debug("{} enable {} current upd state 0x{:02x}",
             ADDRESS_TO_LOGGABLE_CSTR(p_lcb->remote_bd_addr), enable,
             p_lcb->conn_update_mask);

  if (enable) {
    p_lcb->conn_update_mask &= ~L2C_BLE_CONN_UPDATE_DISABLE;
    p_lcb->subrate_req_mask &= ~L2C_BLE_SUBRATE_REQ_DISABLE;
  } else {
    p_lcb->conn_update_mask |= L2C_BLE_CONN_UPDATE_DISABLE;
    p_lcb->subrate_req_mask |= L2C_BLE_SUBRATE_REQ_DISABLE;
  }

  l2cble_start_conn_update(p_lcb);

  return (true);
}

/*******************************************************************************
 *
 *  Function        l2cble_start_conn_update
 *
 *  Description     Start the BLE connection parameter update process based on
 *                  status.
 *
 *  Parameters:     lcb : l2cap link control block
 *
 *  Return value:   none
 *
 ******************************************************************************/
void l2cble_start_conn_update(tL2C_LCB* p_lcb) {
  uint16_t min_conn_int, max_conn_int, peripheral_latency, supervision_tout;
  if (!BTM_IsAclConnectionUp(p_lcb->remote_bd_addr, BT_TRANSPORT_LE)) {
    log::error("No known connection ACL for {}",
               ADDRESS_TO_LOGGABLE_STR(p_lcb->remote_bd_addr));
    return;
  }

  // TODO(armansito): The return value of this call wasn't being used but the
  // logic of this function might be depending on its side effects. We should
  // verify if this call is needed at all and remove it otherwise.
  btm_find_or_alloc_dev(p_lcb->remote_bd_addr);

  if ((p_lcb->conn_update_mask & L2C_BLE_UPDATE_PENDING) ||
      (p_lcb->subrate_req_mask & L2C_BLE_SUBRATE_REQ_PENDING)) {
    return;
  }

  if (p_lcb->conn_update_mask & L2C_BLE_CONN_UPDATE_DISABLE) {
    /* application requests to disable parameters update.
       If parameters are already updated, lets set them
       up to what has been requested during connection establishement */
    if (p_lcb->conn_update_mask & L2C_BLE_NOT_DEFAULT_PARAM &&
        /* current connection interval is greater than default min */
        p_lcb->min_interval > BTM_BLE_CONN_INT_MIN) {
      /* use 7.5 ms as fast connection parameter, 0 peripheral latency */
      min_conn_int = max_conn_int = BTM_BLE_CONN_INT_MIN;

      L2CA_AdjustConnectionIntervals(&min_conn_int, &max_conn_int,
                                     BTM_BLE_CONN_INT_MIN);

      peripheral_latency = BTM_BLE_CONN_PERIPHERAL_LATENCY_DEF;
      supervision_tout = BTM_BLE_CONN_TIMEOUT_DEF;

      /* if both side 4.1, or we are central device, send HCI command */
      if (p_lcb->IsLinkRoleCentral() ||
          (controller_get_interface()
               ->SupportsBleConnectionParametersRequest() &&
           acl_peer_supports_ble_connection_parameters_request(
               p_lcb->remote_bd_addr))) {
        btsnd_hcic_ble_upd_ll_conn_params(p_lcb->Handle(), min_conn_int,
                                          max_conn_int, peripheral_latency,
                                          supervision_tout, 0, 0);
        p_lcb->conn_update_mask |= L2C_BLE_UPDATE_PENDING;
      } else {
        l2cu_send_peer_ble_par_req(p_lcb, min_conn_int, max_conn_int,
                                   peripheral_latency, supervision_tout);
      }
      p_lcb->conn_update_mask &= ~L2C_BLE_NOT_DEFAULT_PARAM;
      p_lcb->conn_update_mask |= L2C_BLE_NEW_CONN_PARAM;
    }
  } else {
    /* application allows to do update, if we were delaying one do it now */
    if (p_lcb->conn_update_mask & L2C_BLE_NEW_CONN_PARAM) {
      /* if both side 4.1, or we are central device, send HCI command */
      if (p_lcb->IsLinkRoleCentral() ||
          (controller_get_interface()
               ->SupportsBleConnectionParametersRequest() &&
           acl_peer_supports_ble_connection_parameters_request(
               p_lcb->remote_bd_addr))) {
        btsnd_hcic_ble_upd_ll_conn_params(p_lcb->Handle(), p_lcb->min_interval,
                                          p_lcb->max_interval, p_lcb->latency,
                                          p_lcb->timeout, p_lcb->min_ce_len,
                                          p_lcb->max_ce_len);
        p_lcb->conn_update_mask |= L2C_BLE_UPDATE_PENDING;
      } else {
        l2cu_send_peer_ble_par_req(p_lcb, p_lcb->min_interval,
                                   p_lcb->max_interval, p_lcb->latency,
                                   p_lcb->timeout);
      }
      p_lcb->conn_update_mask &= ~L2C_BLE_NEW_CONN_PARAM;
      p_lcb->conn_update_mask |= L2C_BLE_NOT_DEFAULT_PARAM;
    }
  }
}

/*******************************************************************************
 *
 * Function         l2cble_process_conn_update_evt
 *
 * Description      This function enables the connection update request from
 *                  remote after a successful connection update response is
 *                  received.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2cble_process_conn_update_evt(uint16_t handle, uint8_t status,
                                    uint16_t interval, uint16_t latency,
                                    uint16_t timeout) {
  log::verbose("");

  /* See if we have a link control block for the remote device */
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_handle(handle);
  if (!p_lcb) {
    log::warn("Invalid handle: {}", handle);
    return;
  }

  p_lcb->conn_update_mask &= ~L2C_BLE_UPDATE_PENDING;

  if (status != HCI_SUCCESS) {
    log::warn("Error status: {}", status);
  }

  l2cble_start_conn_update(p_lcb);

  l2cble_start_subrate_change(p_lcb);

  log::verbose("conn_update_mask={} , subrate_req_mask={}",
               p_lcb->conn_update_mask, p_lcb->subrate_req_mask);
}

/*******************************************************************************
 *
 * Function         l2cble_process_rc_param_request_evt
 *
 * Description      process LE Remote Connection Parameter Request Event.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2cble_process_rc_param_request_evt(uint16_t handle, uint16_t int_min,
                                         uint16_t int_max, uint16_t latency,
                                         uint16_t timeout) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_handle(handle);

  if (p_lcb != NULL) {
    p_lcb->min_interval = int_min;
    p_lcb->max_interval = int_max;
    p_lcb->latency = latency;
    p_lcb->timeout = timeout;

    /* if update is enabled, always accept connection parameter update */
    if ((p_lcb->conn_update_mask & L2C_BLE_CONN_UPDATE_DISABLE) == 0) {
      btsnd_hcic_ble_rc_param_req_reply(handle, int_min, int_max, latency,
                                        timeout, 0, 0);
    } else {
      log::verbose("L2CAP - LE - update currently disabled");
      p_lcb->conn_update_mask |= L2C_BLE_NEW_CONN_PARAM;
      btsnd_hcic_ble_rc_param_req_neg_reply(handle,
                                            HCI_ERR_UNACCEPT_CONN_INTERVAL);
    }

  } else {
    log::warn("No link to update connection parameter");
  }
}

void l2cble_use_preferred_conn_params(const RawAddress& bda) {
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_bd_addr(bda, BT_TRANSPORT_LE);
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_or_alloc_dev(bda);

  /* If there are any preferred connection parameters, set them now */
  if ((p_lcb != NULL) && (p_dev_rec != NULL) &&
      (p_dev_rec->conn_params.min_conn_int >= BTM_BLE_CONN_INT_MIN) &&
      (p_dev_rec->conn_params.min_conn_int <= BTM_BLE_CONN_INT_MAX) &&
      (p_dev_rec->conn_params.max_conn_int >= BTM_BLE_CONN_INT_MIN) &&
      (p_dev_rec->conn_params.max_conn_int <= BTM_BLE_CONN_INT_MAX) &&
      (p_dev_rec->conn_params.peripheral_latency <= BTM_BLE_CONN_LATENCY_MAX) &&
      (p_dev_rec->conn_params.supervision_tout >= BTM_BLE_CONN_SUP_TOUT_MIN) &&
      (p_dev_rec->conn_params.supervision_tout <= BTM_BLE_CONN_SUP_TOUT_MAX) &&
      ((p_lcb->min_interval < p_dev_rec->conn_params.min_conn_int &&
        p_dev_rec->conn_params.min_conn_int != BTM_BLE_CONN_PARAM_UNDEF) ||
       (p_lcb->min_interval > p_dev_rec->conn_params.max_conn_int) ||
       (p_lcb->latency > p_dev_rec->conn_params.peripheral_latency) ||
       (p_lcb->timeout > p_dev_rec->conn_params.supervision_tout))) {
    log::verbose(
        "HANDLE={} min_conn_int={} max_conn_int={} peripheral_latency={} "
        "supervision_tout={}",
        p_lcb->Handle(), p_dev_rec->conn_params.min_conn_int,
        p_dev_rec->conn_params.max_conn_int,
        p_dev_rec->conn_params.peripheral_latency,
        p_dev_rec->conn_params.supervision_tout);

    p_lcb->min_interval = p_dev_rec->conn_params.min_conn_int;
    p_lcb->max_interval = p_dev_rec->conn_params.max_conn_int;
    p_lcb->timeout = p_dev_rec->conn_params.supervision_tout;
    p_lcb->latency = p_dev_rec->conn_params.peripheral_latency;

    btsnd_hcic_ble_upd_ll_conn_params(
        p_lcb->Handle(), p_dev_rec->conn_params.min_conn_int,
        p_dev_rec->conn_params.max_conn_int,
        p_dev_rec->conn_params.peripheral_latency,
        p_dev_rec->conn_params.supervision_tout, 0, 0);
  }
}

/*******************************************************************************
 *
 *  Function        l2cble_start_subrate_change
 *
 *  Description     Start the BLE subrate change process based on
 *                  status.
 *
 *  Parameters:     lcb : l2cap link control block
 *
 *  Return value:   none
 *
 ******************************************************************************/
static void l2cble_start_subrate_change(tL2C_LCB* p_lcb) {
  if (!BTM_IsAclConnectionUp(p_lcb->remote_bd_addr, BT_TRANSPORT_LE)) {
    log::error("No known connection ACL for {}",
               ADDRESS_TO_LOGGABLE_STR(p_lcb->remote_bd_addr));
    return;
  }

  btm_find_or_alloc_dev(p_lcb->remote_bd_addr);

  log::verbose("subrate_req_mask={} conn_update_mask={}",
               p_lcb->subrate_req_mask, p_lcb->conn_update_mask);

  if (p_lcb->subrate_req_mask & L2C_BLE_SUBRATE_REQ_PENDING) {
    log::verbose("returning L2C_BLE_SUBRATE_REQ_PENDING ");
    return;
  }

  if (p_lcb->subrate_req_mask & L2C_BLE_SUBRATE_REQ_DISABLE) {
    log::verbose("returning L2C_BLE_SUBRATE_REQ_DISABLE ");
    return;
  }

  /* application allows to do update, if we were delaying one do it now */
  if (!(p_lcb->subrate_req_mask & L2C_BLE_NEW_SUBRATE_PARAM) ||
      (p_lcb->conn_update_mask & L2C_BLE_UPDATE_PENDING) ||
      (p_lcb->conn_update_mask & L2C_BLE_NEW_CONN_PARAM)) {
    log::verbose("returning L2C_BLE_NEW_SUBRATE_PARAM");
    return;
  }

  if (!controller_get_interface()->SupportsBleConnectionSubrating() ||
      !acl_peer_supports_ble_connection_subrating(p_lcb->remote_bd_addr) ||
      !acl_peer_supports_ble_connection_subrating_host(p_lcb->remote_bd_addr)) {
    log::verbose(
        "returning L2C_BLE_NEW_SUBRATE_PARAM local_host_sup={}, "
        "local_conn_subrarte_sup={}, peer_subrate_sup={}, peer_host_sup={}",
        controller_get_interface()->SupportsBleConnectionSubratingHost(),
        controller_get_interface()->SupportsBleConnectionSubrating(),
        acl_peer_supports_ble_connection_subrating(p_lcb->remote_bd_addr),
        acl_peer_supports_ble_connection_subrating_host(p_lcb->remote_bd_addr));
    return;
  }

  log::verbose("Sending HCI cmd for subrate req");
  bluetooth::shim::ACL_LeSubrateRequest(
      p_lcb->Handle(), p_lcb->subrate_min, p_lcb->subrate_max,
      p_lcb->max_latency, p_lcb->cont_num, p_lcb->supervision_tout);

  p_lcb->subrate_req_mask |= L2C_BLE_SUBRATE_REQ_PENDING;
  p_lcb->subrate_req_mask &= ~L2C_BLE_NEW_SUBRATE_PARAM;
  p_lcb->conn_update_mask |= L2C_BLE_NOT_DEFAULT_PARAM;
}

/*******************************************************************************
 *
 *  Function        L2CA_SetDefaultSubrate
 *
 *  Description     BLE Set Default Subrate
 *
 *  Parameters:     Subrate parameters
 *
 *  Return value:   void
 *
 ******************************************************************************/
void L2CA_SetDefaultSubrate(uint16_t subrate_min, uint16_t subrate_max,
                            uint16_t max_latency, uint16_t cont_num,
                            uint16_t timeout) {
  log::verbose(
      "subrate_min={}, subrate_max={}, max_latency={}, cont_num={}, timeout={}",
      subrate_min, subrate_max, max_latency, cont_num, timeout);

  bluetooth::shim::ACL_LeSetDefaultSubrate(subrate_min, subrate_max,
                                           max_latency, cont_num, timeout);
}

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
                         uint16_t cont_num, uint16_t timeout) {
  tL2C_LCB* p_lcb;

  /* See if we have a link control block for the remote device */
  p_lcb = l2cu_find_lcb_by_bd_addr(rem_bda, BT_TRANSPORT_LE);

  /* If we don't have one, create one and accept the connection. */
  if (!p_lcb || !BTM_IsAclConnectionUp(rem_bda, BT_TRANSPORT_LE)) {
    log::warn("unknown BD_ADDR {}", ADDRESS_TO_LOGGABLE_STR(rem_bda));
    return (false);
  }

  if (p_lcb->transport != BT_TRANSPORT_LE) {
    log::warn("BD_ADDR {} not LE", ADDRESS_TO_LOGGABLE_STR(rem_bda));
    return (false);
  }

  log::verbose(
      "BD_ADDR={}, subrate_min={}, subrate_max={}, max_latency={}, "
      "cont_num={}, timeout={}",
      ADDRESS_TO_LOGGABLE_STR(rem_bda), subrate_min, subrate_max, max_latency,
      cont_num, timeout);

  p_lcb->subrate_min = subrate_min;
  p_lcb->subrate_max = subrate_max;
  p_lcb->max_latency = max_latency;
  p_lcb->cont_num = cont_num;
  p_lcb->subrate_req_mask |= L2C_BLE_NEW_SUBRATE_PARAM;
  p_lcb->supervision_tout = timeout;

  l2cble_start_subrate_change(p_lcb);

  return (true);
}

/*******************************************************************************
 *
 * Function         l2cble_process_subrate_change_evt
 *
 * Description      This function enables LE subrating
 *                  after a successful subrate change process is
 *                  done.
 *
 * Parameters:      LE connection handle
 *                  status
 *                  subrate factor
 *                  peripheral latency
 *                  continuation number
 *                  supervision timeout
 *
 * Returns          void
 *
 ******************************************************************************/
void l2cble_process_subrate_change_evt(uint16_t handle, uint8_t status,
                                       uint16_t subrate_factor,
                                       uint16_t peripheral_latency,
                                       uint16_t cont_num, uint16_t timeout) {
  log::verbose("");

  /* See if we have a link control block for the remote device */
  tL2C_LCB* p_lcb = l2cu_find_lcb_by_handle(handle);
  if (!p_lcb) {
    log::warn("Invalid handle: {}", handle);
    return;
  }

  p_lcb->subrate_req_mask &= ~L2C_BLE_SUBRATE_REQ_PENDING;

  if (status != HCI_SUCCESS) {
    log::warn("Error status: {}", status);
  }

  l2cble_start_conn_update(p_lcb);

  l2cble_start_subrate_change(p_lcb);

  log::verbose("conn_update_mask={} , subrate_req_mask={}",
               p_lcb->conn_update_mask, p_lcb->subrate_req_mask);
}
