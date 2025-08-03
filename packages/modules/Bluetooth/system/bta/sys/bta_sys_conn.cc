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
 *  Routes connection status callbacks from various sub systems to DM
 *
 ******************************************************************************/

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/sys/bta_sys.h"
#include "bta/sys/bta_sys_int.h"
#include "internal_include/bt_target.h"
#include "main/shim/dumpsys.h"
#include "os/log.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/btm_api.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

using namespace bluetooth;
/*******************************************************************************
 *
 * Function         bta_sys_rm_register
 *
 * Description      Called by BTA DM to register role management callbacks
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_rm_register(tBTA_SYS_CONN_CBACK* p_cback) {
  bta_sys_cb.prm_cb = p_cback;
}

/*******************************************************************************
 *
 * Function         bta_sys_role_chg_register
 *
 * Description      Called by BTA AV to register role change callbacks
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_role_chg_register(tBTA_SYS_ROLE_SWITCH_CBACK* p_cback) {
  bta_sys_cb.p_role_cb = p_cback;
}
/*******************************************************************************
 *
 * Function         bta_sys_ssr_cfg_register
 *
 * Description      Called by BTA DM to register SSR configuration callback
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_ssr_cfg_register(tBTA_SYS_SSR_CFG_CBACK* p_cback) {
  bta_sys_cb.p_ssr_cb = p_cback;
}

/*******************************************************************************
 *
 * Function         bta_sys_role_chg_register
 *
 * Description      Called by BTA AV to register role change callbacks
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_notify_role_chg(const RawAddress& peer_addr, tHCI_ROLE new_role,
                             tHCI_STATUS hci_status) {
  log::debug("Role changed peer:{} new_role:{} hci_status:{}",
             ADDRESS_TO_LOGGABLE_CSTR(peer_addr), RoleText(new_role),
             hci_error_code_text(hci_status));
  if (bta_sys_cb.p_role_cb) {
    bta_sys_cb.p_role_cb(BTA_SYS_ROLE_CHANGE, new_role, hci_status, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_collision_register
 *
 * Description      Called by any BTA module to register for collision event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_collision_register(tBTA_SYS_ID bta_id,
                                tBTA_SYS_CONN_CBACK* p_cback) {
  uint8_t index;

  for (index = 0; index < MAX_COLLISION_REG; index++) {
    if ((bta_sys_cb.colli_reg.id[index] == bta_id) ||
        (bta_sys_cb.colli_reg.id[index] == 0)) {
      bta_sys_cb.colli_reg.id[index] = bta_id;
      bta_sys_cb.colli_reg.p_coll_cback[index] = p_cback;
      return;
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_notify_collision
 *
 * Description      Called by BTA DM to notify collision event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_notify_collision(const RawAddress& peer_addr) {
  uint8_t index;

  for (index = 0; index < MAX_COLLISION_REG; index++) {
    if ((bta_sys_cb.colli_reg.id[index] != 0) &&
        (bta_sys_cb.colli_reg.p_coll_cback[index] != NULL)) {
      bta_sys_cb.colli_reg.p_coll_cback[index](BTA_SYS_CONN_OPEN, BTA_ID_SYS, 0,
                                               peer_addr);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_sco_register
 *
 * Description      Called by BTA AV to register sco connection change callbacks
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_sco_register(tBTA_SYS_CONN_SCO_CBACK* p_cback) {
  bta_sys_cb.p_sco_cb = p_cback;
}

/*******************************************************************************
 *
 * Function         bta_sys_pm_register
 *
 * Description      Called by BTA DM to register power management callbacks
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_pm_register(tBTA_SYS_CONN_CBACK* p_cback) {
  bta_sys_cb.ppm_cb = p_cback;
}

/*******************************************************************************
 *
 * Function         bta_sys_sniff_register
 *
 * Description      Called by BTA DM to register sniff callbacks
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_sniff_register(tBTA_SYS_SNIFF_CBACK* p_cback) {
  bta_sys_cb.sniff_cb = p_cback;
}

/*******************************************************************************
 *
 * Function         bta_sys_reset_sniff
 *
 * Description      Called by BTA subsystems to reset sniff timer
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_reset_sniff(uint8_t id, uint8_t app_id,
                         const RawAddress& peer_addr) {
  if (bta_sys_cb.sniff_cb) {
    bta_sys_cb.sniff_cb(id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_conn_open
 *
 * Description      Called by BTA subsystems when a connection is made to
 *                  the service
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_conn_open(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr) {
  if (bta_sys_cb.prm_cb) {
    bta_sys_cb.prm_cb(BTA_SYS_CONN_OPEN, id, app_id, peer_addr);
  }

  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_CONN_OPEN, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_conn_close
 *
 * Description      Called by BTA subsystems when a connection to the service
 *                  is closed
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_conn_close(tBTA_SYS_ID id, uint8_t app_id,
                        const RawAddress& peer_addr) {
  if (bta_sys_cb.prm_cb) {
    bta_sys_cb.prm_cb(BTA_SYS_CONN_CLOSE, id, app_id, peer_addr);
  }

  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_CONN_CLOSE, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_app_open
 *
 * Description      Called by BTA subsystems when application initiates
 *                  connection to a peer device
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_app_open(tBTA_SYS_ID id, uint8_t app_id,
                      const RawAddress& peer_addr) {
  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_APP_OPEN, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_app_close
 *
 * Description      Called by BTA subsystems when application initiates close
 *                  of connection to peer device
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_app_close(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr) {
  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_APP_CLOSE, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_sco_open
 *
 * Description      Called by BTA subsystems when sco connection for that
 *                  service is open
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_sco_open(tBTA_SYS_ID id, uint8_t app_id,
                      const RawAddress& peer_addr) {
  /* AG triggers p_sco_cb by bta_sys_sco_use. */
  if ((id != BTA_ID_AG) && (bta_sys_cb.p_sco_cb)) {
    /* without querying BTM_GetNumScoLinks() */
    bta_sys_cb.p_sco_cb(BTA_SYS_SCO_OPEN, id, app_id, peer_addr);
  }

  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_SCO_OPEN, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_sco_close
 *
 * Description      Called by BTA subsystems when sco connection for that
 *                  service is closed
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_sco_close(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr) {
  uint8_t num_sco_links;

  if ((id != BTA_ID_AG) && (bta_sys_cb.p_sco_cb)) {
    num_sco_links = BTM_GetNumScoLinks();
    bta_sys_cb.p_sco_cb(BTA_SYS_SCO_CLOSE, num_sco_links, app_id, peer_addr);
  }

  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_SCO_CLOSE, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_sco_use
 *
 * Description      Called by BTA subsystems when that service needs to use sco.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_sco_use(UNUSED_ATTR tBTA_SYS_ID id, uint8_t app_id,
                     const RawAddress& peer_addr) {
  /* AV streaming need to be suspended before SCO is connected. */
  if (bta_sys_cb.p_sco_cb) {
    /* without querying BTM_GetNumScoLinks() */
    bta_sys_cb.p_sco_cb(BTA_SYS_SCO_OPEN, 1, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_sco_unuse
 *
 * Description      Called by BTA subsystems when sco connection for that
 *                  service is no longer needed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_sco_unuse(UNUSED_ATTR tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr) {
  if ((bta_sys_cb.p_sco_cb)) {
    uint8_t num_sco_links = BTM_GetNumScoLinks();
    bta_sys_cb.p_sco_cb(BTA_SYS_SCO_CLOSE, num_sco_links, app_id, peer_addr);
  }
}
/*******************************************************************************
 *
 * Function         bta_sys_chg_ssr_config
 *
 * Description      Called by BTA subsystems to indicate that the given app SSR
 *                  setting needs to be changed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_chg_ssr_config(uint8_t id, uint8_t app_id, uint16_t max_latency,
                            uint16_t min_tout) {
  if (bta_sys_cb.p_ssr_cb) {
    bta_sys_cb.p_ssr_cb(id, app_id, max_latency, min_tout);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_idle
 *
 * Description      Called by BTA subsystems to indicate that the connection to
 *                  peer device is idle
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_idle(tBTA_SYS_ID id, uint8_t app_id, const RawAddress& peer_addr) {
  if (bta_sys_cb.prm_cb) {
    bta_sys_cb.prm_cb(BTA_SYS_CONN_IDLE, id, app_id, peer_addr);
  }

  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_CONN_IDLE, id, app_id, peer_addr);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_busy
 *
 * Description      Called by BTA subsystems to indicate that the connection to
 *                  peer device is busy
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_busy(tBTA_SYS_ID id, uint8_t app_id, const RawAddress& peer_addr) {
  if (bta_sys_cb.prm_cb) {
    bta_sys_cb.prm_cb(BTA_SYS_CONN_BUSY, id, app_id, peer_addr);
  }

  if (bta_sys_cb.ppm_cb) {
    bta_sys_cb.ppm_cb(BTA_SYS_CONN_BUSY, id, app_id, peer_addr);
  }
}

#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
/*******************************************************************************
 *
 * Function         bta_sys_eir_register
 *
 * Description      Called by BTA DM to register EIR utility function that can
 *                  be used by the other BTA modules to add/remove UUID.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_eir_register(tBTA_SYS_EIR_CBACK* p_cback) {
  if (bta_sys_cb.eir_cb != nullptr) {
    log::warn("Already registered extended inquiry result callback");
  }
  bta_sys_cb.eir_cb = p_cback;
}

void bta_sys_eir_unregister() {
  if (bta_sys_cb.eir_cb == nullptr) {
    log::warn("Already unregistered extended inquiry result callback");
  }
  bta_sys_cb.eir_cb = nullptr;
}

/*******************************************************************************
 *
 * Function         bta_sys_cust_eir_register
 *
 * Description      Called by BTA DM to register EIR utility function that can
 *                  be used by the other BTA modules to add/remove custom UUID.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_cust_eir_register(tBTA_SYS_CUST_EIR_CBACK* p_cback) {
  bta_sys_cb.cust_eir_cb = p_cback;
}

/*******************************************************************************
 *
 * Function         bta_sys_add_uuid
 *
 * Description      Called by BTA subsystems to indicate to DM that new service
 *                  class UUID is added.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_add_uuid(uint16_t uuid16) {
  if (bta_sys_cb.eir_cb) {
    bta_sys_cb.eir_cb(uuid16, true);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_remove_uuid
 *
 * Description      Called by BTA subsystems to indicate to DM that the service
 *                  class UUID is removed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_remove_uuid(uint16_t uuid16) {
  if (bta_sys_cb.eir_cb) {
    bta_sys_cb.eir_cb(uuid16, false);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_add_cust_uuid
 *
 * Description      Called by BTA subsystems to indicate to DM that the custom service
 *                  class UUID is removed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_add_cust_uuid(const tBTA_CUSTOM_UUID& curr) {
  if (bta_sys_cb.cust_eir_cb) {
    bta_sys_cb.cust_eir_cb(curr, true);
  }
}

/*******************************************************************************
 *
 * Function         bta_sys_remove_cust_uuid
 *
 * Description      Called by BTA subsystems to indicate to DM that the service
 *                  class UUID is removed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_sys_remove_cust_uuid(const tBTA_CUSTOM_UUID& curr) {
  if (bta_sys_cb.cust_eir_cb) {
    bta_sys_cb.cust_eir_cb(curr, false);
  }
}
#endif
