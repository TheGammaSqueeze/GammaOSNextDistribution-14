/******************************************************************************
 *
 *  Copyright 2000-2012 Broadcom Corporation
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

/*****************************************************************************
 *
 *  This file contains functions that manages ACL link modes.
 *  This includes operations such as active, hold,
 *  park and sniff modes.
 *
 *  This module contains both internal and external (API)
 *  functions. External (API) functions are distinguishable
 *  by their names beginning with uppercase BTM.
 *
 *****************************************************************************/

#include "main/shim/entry.h"
#define LOG_TAG "bt_btm_pm"

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <unordered_map>

#include "device/include/interop.h"
#include "hci/controller_interface.h"
#include "internal_include/bt_target.h"
#include "main/shim/dumpsys.h"
#include "main/shim/entry.h"
#include "os/log.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "osi/include/stack_power_telemetry.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/btm_status.h"
#include "types/raw_address.h"

using namespace bluetooth;

void l2c_OnHciModeChangeSendPendingPackets(RawAddress remote);
void btm_sco_chk_pend_unpark(tHCI_STATUS status, uint16_t handle);
void btm_cont_rswitch_from_handle(uint16_t hci_handle);
extern tBTM_CB btm_cb;

namespace {
uint16_t pm_pend_link = 0;

std::unordered_map<uint16_t /* handle */, tBTM_PM_MCB> pm_mode_db;

tBTM_PM_MCB* btm_pm_get_power_manager_from_address(const RawAddress& bda) {
  for (auto& entry : pm_mode_db) {
    if (entry.second.bda_ == bda) {
      return &entry.second;
    }
  }
  return nullptr;
}

tBTM_PM_RCB pm_reg_db; /* per application/module */

uint8_t pm_pend_id = 0; /* the id pf the module, which has a pending PM cmd */

constexpr char kBtmLogTag[] = "ACL";
}

/*****************************************************************************/
/*      to handle different modes                                            */
/*****************************************************************************/
#define BTM_PM_NUM_SET_MODES 3  /* only hold, sniff & park */

#define BTM_PM_GET_MD1 1
#define BTM_PM_GET_MD2 2
#define BTM_PM_GET_COMP 3

const uint8_t
    btm_pm_md_comp_matrix[BTM_PM_NUM_SET_MODES * BTM_PM_NUM_SET_MODES] = {
        BTM_PM_GET_COMP, BTM_PM_GET_MD2,  BTM_PM_GET_MD2,

        BTM_PM_GET_MD1,  BTM_PM_GET_COMP, BTM_PM_GET_MD1,

        BTM_PM_GET_MD1,  BTM_PM_GET_MD2,  BTM_PM_GET_COMP};

static void send_sniff_subrating(uint16_t handle, const RawAddress& addr,
                                 uint16_t max_lat, uint16_t min_rmt_to,
                                 uint16_t min_loc_to) {
  uint16_t new_max_lat = 0;
  if (interop_match_addr_get_max_lat(INTEROP_UPDATE_HID_SSR_MAX_LAT, &addr,
                                     &new_max_lat)) {
    max_lat = new_max_lat;
  }

  btsnd_hcic_sniff_sub_rate(handle, max_lat, min_rmt_to, min_loc_to);
  BTM_LogHistory(kBtmLogTag, addr, "Sniff subrating",
                 base::StringPrintf(
                     "max_latency:%.2f peer_timeout:%.2f local_timeout:%.2f",
                     ticks_to_seconds(max_lat), ticks_to_seconds(min_rmt_to),
                     ticks_to_seconds(min_loc_to)));
}

static tBTM_STATUS btm_pm_snd_md_req(uint16_t handle, uint8_t pm_id,
                                     int link_ind,
                                     const tBTM_PM_PWR_MD* p_mode);

/*****************************************************************************/
/*                     P U B L I C  F U N C T I O N S                        */
/*****************************************************************************/
/*******************************************************************************
 *
 * Function         BTM_PmRegister
 *
 * Description      register or deregister with power manager
 *
 * Returns          BTM_SUCCESS if successful,
 *                  BTM_NO_RESOURCES if no room to hold registration
 *                  BTM_ILLEGAL_VALUE
 *
 ******************************************************************************/
tBTM_STATUS BTM_PmRegister(uint8_t mask, uint8_t* p_pm_id,
                           tBTM_PM_STATUS_CBACK* p_cb) {
  /* de-register */
  if (mask & BTM_PM_DEREG) {
    if (*p_pm_id >= BTM_MAX_PM_RECORDS) return BTM_ILLEGAL_VALUE;
    pm_reg_db.mask = BTM_PM_REC_NOT_USED;
    return BTM_SUCCESS;
  }

  if (pm_reg_db.mask == BTM_PM_REC_NOT_USED) {
    /* if register for notification, should provide callback routine */
    if (p_cb == NULL) return BTM_ILLEGAL_VALUE;
    pm_reg_db.cback = p_cb;
    pm_reg_db.mask = mask;
    *p_pm_id = 0;
    return BTM_SUCCESS;
  }

  return BTM_NO_RESOURCES;
}

void BTM_PM_OnConnected(uint16_t handle, const RawAddress& remote_bda) {
  if (pm_mode_db.find(handle) != pm_mode_db.end()) {
    log::error("Overwriting power mode db entry handle:{} peer:{}", handle,
               ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
  }
  pm_mode_db[handle] = {};
  pm_mode_db[handle].Init(remote_bda, handle);
}

void BTM_PM_OnDisconnected(uint16_t handle) {
  if (pm_mode_db.find(handle) == pm_mode_db.end()) {
    log::error("Erasing unknown power mode db entry handle:{}", handle);
  }
  pm_mode_db.erase(handle);
  if (handle == pm_pend_link) {
    pm_pend_link = 0;
  }
}

/*******************************************************************************
 *
 * Function         BTM_SetPowerMode
 *
 * Description      store the mode in control block or
 *                  alter ACL connection behavior.
 *
 * Returns          BTM_SUCCESS if successful,
 *                  BTM_UNKNOWN_ADDR if bd addr is not active or bad
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetPowerMode(uint8_t pm_id, const RawAddress& remote_bda,
                             const tBTM_PM_PWR_MD* p_mode) {
  if (pm_id >= BTM_MAX_PM_RECORDS) {
    pm_id = BTM_PM_SET_ONLY_ID;
  }

  if (!p_mode) {
    log::error("pm_id: {}, p_mode is null for {}", unsigned(pm_id),
               ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return BTM_ILLEGAL_VALUE;
  }

  // per ACL link
  auto* p_cb = btm_pm_get_power_manager_from_address(remote_bda);
  if (p_cb == nullptr) {
    log::warn("Unable to find power manager for peer: {}",
              ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return BTM_UNKNOWN_ADDR;
  }
  uint16_t handle = p_cb->handle_;

  tBTM_PM_MODE mode = p_mode->mode;
  if (!is_legal_power_mode(mode)) {
    log::error("Unable to set illegal power mode value:0x{:02x}", mode);
    return BTM_ILLEGAL_VALUE;
  }

  if (p_mode->mode & BTM_PM_MD_FORCE) {
    log::info("Attempting to force into this power mode");
    /* take out the force bit */
    mode &= (~BTM_PM_MD_FORCE);
  }

  if (mode != BTM_PM_MD_ACTIVE) {
    auto controller = bluetooth::shim::GetController();
    if ((mode == BTM_PM_MD_HOLD && !controller->SupportsHoldMode()) ||
        (mode == BTM_PM_MD_SNIFF && !controller->SupportsSniffMode()) ||
        (mode == BTM_PM_MD_PARK && !controller->SupportsParkMode()) ||
        interop_match_addr(INTEROP_DISABLE_SNIFF, &remote_bda)) {
      log::error("pm_id {} mode {} is not supported for {}", pm_id, mode,
                 ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
      return BTM_MODE_UNSUPPORTED;
    }
  }

  if (mode == p_cb->state) {
    /* already in the requested mode and the current interval has less latency
     * than the max */
    if ((mode == BTM_PM_MD_ACTIVE) ||
        ((p_mode->mode & BTM_PM_MD_FORCE) && (p_mode->max >= p_cb->interval) &&
         (p_mode->min <= p_cb->interval)) ||
        ((p_mode->mode & BTM_PM_MD_FORCE) == 0 &&
         (p_mode->max >= p_cb->interval))) {
      log::debug(
          "Device is already in requested mode {}, interval: {}, max: {}, min: "
          "{}",
          p_mode->mode, p_cb->interval, p_mode->max, p_mode->min);
      return BTM_SUCCESS;
    }
  }

  /* update mode database */
  if (((pm_id != BTM_PM_SET_ONLY_ID) && (pm_reg_db.mask & BTM_PM_REG_SET)) ||
      ((pm_id == BTM_PM_SET_ONLY_ID) && (pm_pend_link != 0))) {
    /* Make sure mask is set to BTM_PM_REG_SET */
    pm_reg_db.mask |= BTM_PM_REG_SET;
    *(&p_cb->req_mode) = *p_mode;
    p_cb->chg_ind = true;
  }

  /* if mode == hold or pending, return */
  if ((p_cb->state == BTM_PM_STS_HOLD) || (p_cb->state == BTM_PM_STS_PENDING) ||
      (pm_pend_link != 0)) {
    log::info(
        "Current power mode is hold or pending status or pending links "
        "state:{}[{}] pm_pending_link:{}",
        power_mode_state_text(p_cb->state).c_str(), p_cb->state, pm_pend_link);
    /* command pending */
    if (handle != pm_pend_link) {
      p_cb->state |= BTM_PM_STORED_MASK;
      log::info("Setting stored bitmask for peer:{}",
                ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    }
    return BTM_CMD_STORED;
  }

  log::info(
      "Setting power mode for peer:{} current_mode:{}[{}] new_mode:{}[{}]",
      ADDRESS_TO_LOGGABLE_CSTR(remote_bda),
      power_mode_state_text(p_cb->state).c_str(), p_cb->state,
      power_mode_text(p_mode->mode).c_str(), p_mode->mode);

  return btm_pm_snd_md_req(p_cb->handle_, pm_id, p_cb->handle_, p_mode);
}

bool BTM_SetLinkPolicyActiveMode(const RawAddress& remote_bda) {
  tBTM_PM_PWR_MD settings;
  memset((void*)&settings, 0, sizeof(settings));
  settings.mode = BTM_PM_MD_ACTIVE;

  switch (BTM_SetPowerMode(BTM_PM_SET_ONLY_ID, remote_bda, &settings)) {
    case BTM_CMD_STORED:
    case BTM_SUCCESS:
      return true;
    default:
      return false;
  }
}

bool BTM_ReadPowerMode(const RawAddress& remote_bda, tBTM_PM_MODE* p_mode) {
  if (p_mode == nullptr) {
    log::error("power mode is nullptr");
    return false;
  }
  tBTM_PM_MCB* p_mcb = btm_pm_get_power_manager_from_address(remote_bda);
  if (p_mcb == nullptr) {
    log::warn("Unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return false;
  }
  *p_mode = static_cast<tBTM_PM_MODE>(p_mcb->state);
  return true;
}

/*******************************************************************************
 *
 * Function         BTM_SetSsrParams
 *
 * Description      This sends the given SSR parameters for the given ACL
 *                  connection if it is in ACTIVE mode.
 *
 * Input Param      remote_bda - device address of desired ACL connection
 *                  max_lat    - maximum latency (in 0.625ms)(0-0xFFFE)
 *                  min_rmt_to - minimum remote timeout
 *                  min_loc_to - minimum local timeout
 *
 *
 * Returns          BTM_SUCCESS if the HCI command is issued successful,
 *                  BTM_UNKNOWN_ADDR if bd addr is not active or bad
 *                  BTM_CMD_STORED if the command is stored
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetSsrParams(const RawAddress& remote_bda, uint16_t max_lat,
                             uint16_t min_rmt_to, uint16_t min_loc_to) {
  tBTM_PM_MCB* p_cb = btm_pm_get_power_manager_from_address(remote_bda);
  if (p_cb == nullptr) {
    log::warn("Unable to find power manager for peer:{}",
              ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return BTM_UNKNOWN_ADDR;
  }

  if (!bluetooth::shim::GetController()->SupportsSniffSubrating()) {
    log::info("No controller support for sniff subrating");
    return BTM_SUCCESS;
  }

  if (p_cb->state == BTM_PM_ST_ACTIVE || p_cb->state == BTM_PM_ST_SNIFF) {
    log::info(
        "Set sniff subrating state:{}[{}] max_latency:0x{:04x} "
        "min_remote_timeout:0x{:04x} min_local_timeout:0x{:04x}",
        power_mode_state_text(p_cb->state).c_str(), p_cb->state, max_lat,
        min_rmt_to, min_loc_to);
    send_sniff_subrating(p_cb->handle_, remote_bda, max_lat, min_rmt_to,
                         min_loc_to);
    return BTM_SUCCESS;
  }
  log::info("pm_mode_db state: {}", p_cb->state);
  p_cb->max_lat = max_lat;
  p_cb->min_rmt_to = min_rmt_to;
  p_cb->min_loc_to = min_loc_to;
  return BTM_CMD_STORED;
}

/*******************************************************************************
 *
 * Function         btm_pm_reset
 *
 * Description      as a part of the BTM reset process.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_pm_reset(void) {
  tBTM_PM_STATUS_CBACK* cb = NULL;

  /* clear the pending request for application */
  if ((pm_pend_id != BTM_PM_SET_ONLY_ID) && (pm_reg_db.mask & BTM_PM_REG_SET)) {
    cb = pm_reg_db.cback;
  }

  pm_reg_db.mask = BTM_PM_REC_NOT_USED;

  if (cb != NULL && pm_pend_link != 0) {
    const RawAddress raw_address = pm_mode_db[pm_pend_link].bda_;
    (*cb)(raw_address, BTM_PM_STS_ERROR, BTM_DEV_RESET, HCI_SUCCESS);
  }
  /* no command pending */
  pm_pend_link = 0;
  pm_mode_db.clear();
  pm_pend_id = 0;
  memset(&pm_reg_db, 0, sizeof(pm_reg_db));
  log::info("reset pm");
}

/*******************************************************************************
 *
 * Function     btm_pm_compare_modes
 * Description  get the "more active" mode of the 2
 * Returns      void
 *
 ******************************************************************************/
static tBTM_PM_PWR_MD* btm_pm_compare_modes(const tBTM_PM_PWR_MD* p_md1,
                                            const tBTM_PM_PWR_MD* p_md2,
                                            tBTM_PM_PWR_MD* p_res) {
  uint8_t res;

  if (p_md1 == NULL) {
    *p_res = *p_md2;
    p_res->mode &= ~BTM_PM_MD_FORCE;

    return p_res;
  }

  if (p_md2->mode == BTM_PM_MD_ACTIVE || p_md1->mode == BTM_PM_MD_ACTIVE) {
    return NULL;
  }

  /* check if force bit is involved */
  if (p_md1->mode & BTM_PM_MD_FORCE) {
    *p_res = *p_md1;
    p_res->mode &= ~BTM_PM_MD_FORCE;
    return p_res;
  }

  if (p_md2->mode & BTM_PM_MD_FORCE) {
    *p_res = *p_md2;
    p_res->mode &= ~BTM_PM_MD_FORCE;
    return p_res;
  }

  res = (p_md1->mode - 1) * BTM_PM_NUM_SET_MODES + (p_md2->mode - 1);
  res = btm_pm_md_comp_matrix[res];
  switch (res) {
    case BTM_PM_GET_MD1:
      *p_res = *p_md1;
      return p_res;

    case BTM_PM_GET_MD2:
      *p_res = *p_md2;
      return p_res;

    case BTM_PM_GET_COMP:
      p_res->mode = p_md1->mode;
      /* min of the two */
      p_res->max = (p_md1->max < p_md2->max) ? (p_md1->max) : (p_md2->max);
      /* max of the two */
      p_res->min = (p_md1->min > p_md2->min) ? (p_md1->min) : (p_md2->min);

      /* the intersection is NULL */
      if (p_res->max < p_res->min) return NULL;

      if (p_res->mode == BTM_PM_MD_SNIFF) {
        /* max of the two */
        p_res->attempt = (p_md1->attempt > p_md2->attempt) ? (p_md1->attempt)
                                                           : (p_md2->attempt);
        p_res->timeout = (p_md1->timeout > p_md2->timeout) ? (p_md1->timeout)
                                                           : (p_md2->timeout);
      }
      return p_res;
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function     btm_pm_get_set_mode
 * Description  get the resulting mode from the registered parties, then compare
 *              it with the requested mode, if the command is from an
 *              unregistered party.
 *
 * Returns      void
 *
 ******************************************************************************/
static tBTM_PM_MODE btm_pm_get_set_mode(uint8_t pm_id, tBTM_PM_MCB* p_cb,
                                        const tBTM_PM_PWR_MD* p_mode,
                                        tBTM_PM_PWR_MD* p_res) {
  tBTM_PM_PWR_MD* p_md = NULL;

  if (p_mode != NULL && p_mode->mode & BTM_PM_MD_FORCE) {
    *p_res = *p_mode;
    p_res->mode &= ~BTM_PM_MD_FORCE;
    return p_res->mode;
  }

  /* g through all the registered "set" parties */
  if (pm_reg_db.mask & BTM_PM_REG_SET) {
    if (p_cb->req_mode.mode == BTM_PM_MD_ACTIVE) {
      /* if at least one registered (SET) party says ACTIVE, stay active */
      return BTM_PM_MD_ACTIVE;
    } else {
      /* if registered parties give conflicting information, stay active */
      if ((btm_pm_compare_modes(p_md, &p_cb->req_mode, p_res)) == NULL)
        return BTM_PM_MD_ACTIVE;
      p_md = p_res;
    }
  }

  /* if the resulting mode is NULL(nobody registers SET), use the requested mode
   */
  if (p_md == NULL) {
    if (p_mode)
      *p_res = *((tBTM_PM_PWR_MD*)p_mode);
    else /* p_mode is NULL when btm_pm_snd_md_req is called from
            btm_pm_proc_mode_change */
      return BTM_PM_MD_ACTIVE;
  } else {
    /* if the command is from unregistered party,
       compare the resulting mode from registered party*/
    if ((pm_id == BTM_PM_SET_ONLY_ID) &&
        ((btm_pm_compare_modes(p_mode, p_md, p_res)) == NULL))
      return BTM_PM_MD_ACTIVE;
  }

  return p_res->mode;
}

/*******************************************************************************
 *
 * Function     btm_pm_snd_md_req
 * Description  get the resulting mode and send the resuest to host controller
 * Returns      tBTM_STATUS
 *, bool    *p_chg_ind
 ******************************************************************************/
static tBTM_STATUS btm_pm_snd_md_req(uint16_t handle, uint8_t pm_id,
                                     int link_ind,
                                     const tBTM_PM_PWR_MD* p_mode) {
  ASSERT_LOG(pm_mode_db.count(handle) != 0,
             "Unable to find active acl for handle %d", handle);
  tBTM_PM_PWR_MD md_res;
  tBTM_PM_MODE mode;
  tBTM_PM_MCB* p_cb = &pm_mode_db[handle];
  bool chg_ind = false;

  mode = btm_pm_get_set_mode(pm_id, p_cb, p_mode, &md_res);
  md_res.mode = mode;

  log::debug("Found controller in mode:{}", power_mode_text(mode).c_str());

  if (p_cb->state == mode) {
    log::info("Link already in requested mode pm_id:{} link_ind:{} mode:{}[{}]",
              pm_id, link_ind, power_mode_text(mode).c_str(), mode);

    /* already in the resulting mode */
    if ((mode == BTM_PM_MD_ACTIVE) ||
        ((md_res.max >= p_cb->interval) && (md_res.min <= p_cb->interval))) {
      log::debug("Storing command");
      return BTM_CMD_STORED;
    }
    log::debug("Need to wake then sleep");
    chg_ind = true;
  }
  p_cb->chg_ind = chg_ind;

  /* cannot go directly from current mode to resulting mode. */
  if (mode != BTM_PM_MD_ACTIVE && p_cb->state != BTM_PM_MD_ACTIVE) {
    log::debug("Power mode change delay required");
    p_cb->chg_ind = true; /* needs to wake, then sleep */
  }

  if (p_cb->chg_ind) {
    log::debug("Need to wake first");
    md_res.mode = BTM_PM_MD_ACTIVE;
  } else if (BTM_PM_MD_SNIFF == md_res.mode && p_cb->max_lat) {
    if (bluetooth::shim::GetController()->SupportsSniffSubrating()) {
      log::debug("Sending sniff subrating to controller");
      send_sniff_subrating(handle, p_cb->bda_, p_cb->max_lat, p_cb->min_rmt_to,
                           p_cb->min_loc_to);
    }
    p_cb->max_lat = 0;
  }
  /* Default is failure */
  pm_pend_link = 0;

  /* send the appropriate HCI command */
  pm_pend_id = pm_id;

  log::info("Switching from {}[0x{:02x}] to {}[0x{:02x}]",
            power_mode_state_text(p_cb->state).c_str(), p_cb->state,
            power_mode_state_text(md_res.mode).c_str(), md_res.mode);
  BTM_LogHistory(kBtmLogTag, p_cb->bda_, "Power mode change",
                 base::StringPrintf(
                     "%s[0x%02x] ==> %s[0x%02x]",
                     power_mode_state_text(p_cb->state).c_str(), p_cb->state,
                     power_mode_state_text(md_res.mode).c_str(), md_res.mode));

  switch (md_res.mode) {
    case BTM_PM_MD_ACTIVE:
      switch (p_cb->state) {
        case BTM_PM_MD_SNIFF:
          btsnd_hcic_exit_sniff_mode(handle);
          pm_pend_link = handle;
          break;
        case BTM_PM_MD_PARK:
          btsnd_hcic_exit_park_mode(handle);
          pm_pend_link = handle;
          break;
        default:
          /* Failure pm_pend_link = MAX_L2CAP_LINKS */
          break;
      }
      break;

    case BTM_PM_MD_HOLD:
      btsnd_hcic_hold_mode(handle, md_res.max, md_res.min);
      pm_pend_link = handle;
      break;

    case BTM_PM_MD_SNIFF:
      btsnd_hcic_sniff_mode(handle, md_res.max, md_res.min, md_res.attempt,
                            md_res.timeout);
      pm_pend_link = handle;
      break;

    case BTM_PM_MD_PARK:
      btsnd_hcic_park_mode(handle, md_res.max, md_res.min);
      pm_pend_link = handle;
      break;
    default:
      /* Failure pm_pend_link = MAX_L2CAP_LINKS */
      break;
  }

  if (pm_pend_link == 0) {
    /* the command was not sent */
    log::error("pm_pending_link maxed out");
    return (BTM_NO_RESOURCES);
  }

  return BTM_CMD_STARTED;
}

static void btm_pm_continue_pending_mode_changes() {
  for (auto& entry : pm_mode_db) {
    if (entry.second.state & BTM_PM_STORED_MASK) {
      entry.second.state &= ~BTM_PM_STORED_MASK;
      log::info("Found another link requiring power mode change:{}",
                ADDRESS_TO_LOGGABLE_CSTR(entry.second.bda_));
      btm_pm_snd_md_req(entry.second.handle_, BTM_PM_SET_ONLY_ID,
                        entry.second.handle_, NULL);
      return;
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_pm_proc_cmd_status
 *
 * Description      This function is called when an HCI command status event
 *                  occurs for power manager related commands.
 *
 * Input Parms      status - status of the event (HCI_SUCCESS if no errors)
 *
 * Returns          none.
 *
 ******************************************************************************/
void btm_pm_proc_cmd_status(tHCI_STATUS status) {
  if (pm_pend_link == 0) {
    log::error(
        "There are no links pending power mode changes; try to find other "
        "pending changes");
    btm_pm_continue_pending_mode_changes();
    return;
  }
  if (pm_mode_db.count(pm_pend_link) == 0) {
    log::error(
        "Got PM change status for disconnected link {}; forgot to clean up "
        "pm_pend_link?",
        pm_pend_link);
    btm_pm_continue_pending_mode_changes();
    return;
  }

  tBTM_PM_MCB* p_cb = &pm_mode_db[pm_pend_link];

  // if the command was not successful. Stay in the same state
  tBTM_PM_STATUS pm_status = BTM_PM_STS_ERROR;

  if (status == HCI_SUCCESS) {
    p_cb->state = BTM_PM_ST_PENDING;
    pm_status = BTM_PM_STS_PENDING;
  }

  /* notify the caller is appropriate */
  if ((pm_pend_id != BTM_PM_SET_ONLY_ID) && (pm_reg_db.mask & BTM_PM_REG_SET)) {
    const RawAddress bd_addr = pm_mode_db[pm_pend_link].bda_;
    log::debug("Notifying callback that link power mode is complete peer:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    (*pm_reg_db.cback)(bd_addr, pm_status, 0, status);
  }

  log::info("Clearing pending power mode link state:{}",
            power_mode_state_text(p_cb->state).c_str());
  pm_pend_link = 0;

  btm_pm_continue_pending_mode_changes();
}

/*******************************************************************************
 *
 * Function         btm_process_mode_change
 *
 * Description      This function is called when an HCI mode change event
 *                  occurs.
 *
 * Input Parms      hci_status - status of the event (HCI_SUCCESS if no errors)
 *                  hci_handle - connection handle associated with the change
 *                  mode - HCI_MODE_ACTIVE, HCI_MODE_HOLD, HCI_MODE_SNIFF, or
 *                         HCI_MODE_PARK
 *                  interval - number of baseband slots (meaning depends on
 *                                                       mode)
 *
 * Returns          none.
 *
 ******************************************************************************/
void btm_pm_proc_mode_change(tHCI_STATUS hci_status, uint16_t hci_handle,
                             tHCI_MODE hci_mode, uint16_t interval) {
  tBTM_PM_STATUS mode = static_cast<tBTM_PM_STATUS>(hci_mode);

  /* update control block */
  if (pm_mode_db.count(hci_handle) == 0) {
    log::warn("Unable to find active acl for handle {}", hci_handle);
    return;
  }
  tBTM_PM_MCB* p_cb = &pm_mode_db[hci_handle];

  const tBTM_PM_STATE old_state = p_cb->state;
  p_cb->state = mode;
  p_cb->interval = interval;

  log::info("Power mode switched from {}[{}] to {}[{}]",
            power_mode_state_text(old_state).c_str(), old_state,
            power_mode_state_text(p_cb->state).c_str(), p_cb->state);

  if ((p_cb->state == BTM_PM_ST_ACTIVE) || (p_cb->state == BTM_PM_ST_SNIFF)) {
    l2c_OnHciModeChangeSendPendingPackets(p_cb->bda_);
  }

  (mode != BTM_PM_ST_ACTIVE)
      ? power_telemetry::GetInstance().LogSniffStarted(hci_handle, p_cb->bda_)
      : power_telemetry::GetInstance().LogSniffStopped(hci_handle, p_cb->bda_);

  /* set req_mode  HOLD mode->ACTIVE */
  if ((mode == BTM_PM_MD_ACTIVE) && (p_cb->req_mode.mode == BTM_PM_MD_HOLD))
    p_cb->req_mode.mode = BTM_PM_MD_ACTIVE;

  /* new request has been made. - post a message to BTU task */
  if (old_state & BTM_PM_STORED_MASK) {
    btm_pm_snd_md_req(hci_handle, BTM_PM_SET_ONLY_ID, hci_handle, NULL);
  } else {
    for (auto& entry : pm_mode_db) {
      if (entry.second.chg_ind) {
        btm_pm_snd_md_req(entry.second.handle_, BTM_PM_SET_ONLY_ID,
                          entry.second.handle_, NULL);
        break;
      }
    }
  }

  /* notify registered parties */
  if (pm_reg_db.mask & BTM_PM_REG_SET) {
    (*pm_reg_db.cback)(p_cb->bda_, mode, interval, hci_status);
  }
  /*check if sco disconnect  is waiting for the mode change */
  btm_sco_disc_chk_pend_for_modechange(hci_handle);

  /* If mode change was because of an active role switch or change link key */
  btm_cont_rswitch_from_handle(hci_handle);
}

/*******************************************************************************
 *
 * Function         btm_pm_proc_ssr_evt
 *
 * Description      This function is called when an HCI sniff subrating event
 *                  occurs.
 *
 * Returns          none.
 *
 ******************************************************************************/
void process_ssr_event(tHCI_STATUS status, uint16_t handle,
                       UNUSED_ATTR uint16_t max_tx_lat, uint16_t max_rx_lat) {
  if (pm_mode_db.count(handle) == 0) {
    log::warn("Received sniff subrating event with no active ACL");
    return;
  }
  tBTM_PM_MCB* p_cb = &pm_mode_db[handle];
  auto bd_addr = p_cb->bda_;

  bool use_ssr = true;
  if (p_cb->interval == max_rx_lat) {
    log::debug("Sniff subrating unsupported so dropping to legacy sniff mode");
    use_ssr = false;
  } else {
    log::debug("Sniff subrating enabled");
  }

  int cnt = 0;
  if (pm_reg_db.mask & BTM_PM_REG_SET) {
    (*pm_reg_db.cback)(bd_addr, BTM_PM_STS_SSR, (use_ssr) ? 1 : 0, status);
    cnt++;
  }
  log::debug(
      "Notified sniff subrating registered clients cnt:{} peer:{} use_ssr:{} "
      "status:{}",
      cnt, ADDRESS_TO_LOGGABLE_CSTR(bd_addr), logbool(use_ssr).c_str(),
      hci_error_code_text(status).c_str());
}

void btm_pm_on_sniff_subrating(tHCI_STATUS status, uint16_t handle,
                               uint16_t maximum_transmit_latency,
                               uint16_t maximum_receive_latency,
                               uint16_t minimum_remote_timeout,
                               uint16_t minimum_local_timeout) {
  process_ssr_event(status, handle, maximum_transmit_latency,
                    maximum_receive_latency);
}

void btm_pm_proc_ssr_evt(uint8_t* p, UNUSED_ATTR uint16_t evt_len) {
  uint8_t status;
  uint16_t handle;
  uint16_t max_tx_lat;
  uint16_t max_rx_lat;

  STREAM_TO_UINT8(status, p);
  STREAM_TO_UINT16(handle, p);
  STREAM_TO_UINT16(max_tx_lat, p);
  STREAM_TO_UINT16(max_rx_lat, p);

  process_ssr_event(static_cast<tHCI_STATUS>(status), handle, max_tx_lat,
                    max_rx_lat);
}

/*******************************************************************************
 *
 * Function         btm_pm_device_in_active_or_sniff_mode
 *
 * Description      This function is called to check if in active or sniff mode
 *
 * Returns          true, if in active or sniff mode
 *
 ******************************************************************************/
static bool btm_pm_device_in_active_or_sniff_mode(void) {
  /* The active state is the highest state-includes connected device and sniff
   * mode*/

  /* Covers active and sniff modes */
  if (!pm_mode_db.empty()) {
    return true;
  }

  /* Check BLE states */
  if (!btm_cb.ble_ctr_cb.is_connection_state_idle()) {
    log::verbose("- BLE state is not idle");
    return true;
  }

  return false;
}

/*******************************************************************************
 *
 * Function         BTM_PM_DeviceInScanState
 *
 * Description      This function is called to check if in inquiry
 *
 * Returns          true, if in inquiry
 *
 ******************************************************************************/
bool BTM_PM_DeviceInScanState(void) {
  /* Check for inquiry */
  if ((btm_cb.btm_inq_vars.inq_active &
       (BTM_BR_INQ_ACTIVE_MASK | BTM_BLE_INQ_ACTIVE_MASK)) != 0) {
    log::verbose("BTM_PM_DeviceInScanState- Inq active");
    return true;
  }

  return false;
}

/*******************************************************************************
 *
 * Function         BTM_PM_ReadControllerState
 *
 * Description      This function is called to obtain the controller state
 *
 * Returns          Controller State-BTM_CONTRL_ACTIVE, BTM_CONTRL_SCAN, and
 *                  BTM_CONTRL_IDLE
 *
 ******************************************************************************/
tBTM_CONTRL_STATE BTM_PM_ReadControllerState(void) {
  if (btm_pm_device_in_active_or_sniff_mode())
    return BTM_CONTRL_ACTIVE;
  else if (BTM_PM_DeviceInScanState())
    return BTM_CONTRL_SCAN;
  else
    return BTM_CONTRL_IDLE;
}

/*******************************************************************************
 *
 * Function         BTM_PM_ReadSniffLinkCount
 *
 * Description      Return the number of BT connection in sniff mode
 *
 * Returns          Number of BT connection in sniff mode
 *
 ******************************************************************************/
uint8_t BTM_PM_ReadSniffLinkCount(void) {
  uint8_t count = 0;
  for (auto& entry : pm_mode_db) {
    if (entry.second.state == HCI_MODE_SNIFF) {
      ++count;
    }
  }
  return count;
}

/*******************************************************************************
 *
 * Function         BTM_PM_ReadBleLinkCount
 *
 * Description      Return the number of BLE connection
 *
 * Returns          Number of BLE connection
 *
 ******************************************************************************/
uint8_t BTM_PM_ReadBleLinkCount(void) {
  return btm_cb.ble_ctr_cb.link_count[HCI_ROLE_CENTRAL] +
         btm_cb.ble_ctr_cb.link_count[HCI_ROLE_PERIPHERAL];
}

/*******************************************************************************
 *
 * Function         BTM_PM_ReadBleScanDutyCycle
 *
 * Description      Returns BLE scan duty cycle which is (window * 100) /
 *interval
 *
 * Returns          BLE scan duty cycle
 *
 ******************************************************************************/
uint32_t BTM_PM_ReadBleScanDutyCycle(void) {
  if (!btm_cb.ble_ctr_cb.is_ble_scan_active()) {
    return 0;
  }
  uint32_t scan_window = btm_cb.ble_ctr_cb.inq_var.scan_window;
  uint32_t scan_interval = btm_cb.ble_ctr_cb.inq_var.scan_interval;
  log::debug("LE scan_window:{} scan interval:{}", scan_window, scan_interval);
  return (scan_window * 100) / scan_interval;
}

void btm_pm_on_mode_change(tHCI_STATUS status, uint16_t handle,
                           tHCI_MODE current_mode, uint16_t interval) {
  btm_sco_chk_pend_unpark(status, handle);
  btm_pm_proc_mode_change(status, handle, current_mode, interval);
}
