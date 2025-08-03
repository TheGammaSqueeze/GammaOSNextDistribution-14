/******************************************************************************
 *
 *  Copyright 2004-2012 Broadcom Corporation
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
 *  This is the implementation of the API for PAN subsystem of BTA,
 *  Broadcom's Bluetooth application layer for mobile phones.
 *
 ******************************************************************************/

#include <cstdint>
#include <cstring>

#include "bta/pan/bta_pan_int.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "osi/include/compat.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/main_thread.h"
#include "types/raw_address.h"

static const tBTA_SYS_REG bta_pan_reg = {bta_pan_hdl_event, BTA_PanDisable};
void bta_pan_api_disable(tBTA_PAN_DATA* p_data);
void bta_pan_api_enable(tBTA_PAN_DATA* p_data);
void bta_pan_api_open(tBTA_PAN_DATA* p_data);
void bta_pan_sm_execute(tBTA_PAN_SCB* p_scb, uint16_t event,
                        tBTA_PAN_DATA* p_data);

std::string user_service_name; /* Service name for PANU role */
std::string gn_service_name;   /* Service name for GN role */
std::string nap_service_name;  /* Service name for NAP role */

#ifndef PAN_SECURITY
#define PAN_SECURITY                                                         \
  (BTM_SEC_IN_AUTHENTICATE | BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_IN_ENCRYPT | \
   BTM_SEC_OUT_ENCRYPT)
#endif

/*******************************************************************************
 *
 * Function         BTA_PanEnable
 *
 * Description      Enable PAN service.  This function must be
 *                  called before any other functions in the PAN API are called.
 *                  When the enable operation is complete the callback function
 *                  will be called with a BTA_PAN_ENABLE_EVT.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_PanEnable(tBTA_PAN_CBACK p_cback) {
  tBTA_PAN_API_ENABLE* p_buf =
      (tBTA_PAN_API_ENABLE*)osi_malloc(sizeof(tBTA_PAN_API_ENABLE));

  /* register with BTA system manager */
  bta_sys_register(BTA_ID_PAN, &bta_pan_reg);

  p_buf->hdr.event = BTA_PAN_API_ENABLE_EVT;
  p_buf->p_cback = p_cback;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_PanDisable
 *
 * Description      Disables PAN service.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_PanDisable(void) {
  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));

  bta_sys_deregister(BTA_ID_PAN);
  p_buf->event = BTA_PAN_API_DISABLE_EVT;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_PanSetRole
 *
 * Description      Sets PAN roles. When the enable operation is complete
 *                  the callback function will be called with a
 *                  BTA_PAN_SET_ROLE_EVT.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_PanSetRole(tBTA_PAN_ROLE role, const tBTA_PAN_ROLE_INFO user_info,
                    const tBTA_PAN_ROLE_INFO nap_info) {
  post_on_bt_main([=]() {
    tBTA_PAN_DATA data = {
        .api_set_role =
            {
                .hdr =
                    {
                        .event = BTA_PAN_API_SET_ROLE_EVT,
                    },
                .user_name = {},
                .nap_name = {},
                .role = role,
            },
    };
    if (role & BTA_PAN_ROLE_PANU) {
      if (!user_info.p_srv_name.empty())
        strncpy(data.api_set_role.user_name, user_info.p_srv_name.data(),
                BTA_SERVICE_NAME_LEN);
      data.api_set_role.user_app_id = user_info.app_id;
    }

    if (role & BTA_PAN_ROLE_NAP) {
      if (!nap_info.p_srv_name.empty())
        strncpy(data.api_set_role.nap_name, nap_info.p_srv_name.data(),
                BTA_SERVICE_NAME_LEN);
      data.api_set_role.nap_app_id = nap_info.app_id;
    }
    bta_pan_set_role(&data);
  });
}

/*******************************************************************************
 *
 * Function         BTA_PanOpen
 *
 * Description      Opens a connection to a peer device.
 *                  When connection is open callback function is called
 *                  with a BTA_PAN_OPEN_EVT.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_PanOpen(const RawAddress& bd_addr, tBTA_PAN_ROLE local_role,
                 tBTA_PAN_ROLE peer_role) {
  tBTA_PAN_API_OPEN* p_buf =
      (tBTA_PAN_API_OPEN*)osi_malloc(sizeof(tBTA_PAN_API_OPEN));

  p_buf->hdr.event = BTA_PAN_API_OPEN_EVT;
  p_buf->local_role = local_role;
  p_buf->peer_role = peer_role;
  p_buf->bd_addr = bd_addr;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_PanClose
 *
 * Description      Close a PAN  connection to a peer device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_PanClose(uint16_t handle) {
  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));

  p_buf->event = BTA_PAN_API_CLOSE_EVT;
  p_buf->layer_specific = handle;

  bta_sys_sendmsg(p_buf);
}
