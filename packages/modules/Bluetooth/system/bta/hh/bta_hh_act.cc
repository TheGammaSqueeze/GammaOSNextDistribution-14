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
 *  This file contains the HID host action functions.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_hh"

#include <bluetooth/log.h>

#include <cstdint>
#include <string>

#include "bta/hh/bta_hh_int.h"
#include "bta/include/bta_hh_api.h"
#include "bta/include/bta_hh_co.h"
#include "bta/sys/bta_sys.h"
#include "btif/include/btif_storage.h"
#include "include/check.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/hiddefs.h"
#include "stack/include/hidh_api.h"
#include "stack/include/sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

/*****************************************************************************
 *  Constants
 ****************************************************************************/

namespace {

constexpr char kBtmLogTag[] = "HIDH";

}

/*****************************************************************************
 *  Local Function prototypes
 ****************************************************************************/
static void bta_hh_cback(uint8_t dev_handle, const RawAddress& addr,
                         uint8_t event, uint32_t data, BT_HDR* pdata);
static tBTA_HH_STATUS bta_hh_get_trans_status(uint32_t result);

static const char* bta_hh_get_w4_event(uint16_t event);
static const char* bta_hh_hid_event_name(uint16_t event);

/*****************************************************************************
 *  Action Functions
 ****************************************************************************/
/*******************************************************************************
 *
 * Function         bta_hh_api_enable
 *
 * Description      Perform necessary operations to enable HID host.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_api_enable(tBTA_HH_CBACK* p_cback, bool enable_hid, bool enable_hogp) {
  tBTA_HH_STATUS status = BTA_HH_OK;
  uint8_t xx;

  /* initialize BTE HID */
  HID_HostInit();

  memset(&bta_hh_cb, 0, sizeof(tBTA_HH_CB));

  /* store parameters */
  bta_hh_cb.p_cback = p_cback;
  /* initialize device CB */
  for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
    bta_hh_cb.kdev[xx].state = BTA_HH_IDLE_ST;
    bta_hh_cb.kdev[xx].hid_handle = BTA_HH_INVALID_HANDLE;
    bta_hh_cb.kdev[xx].index = xx;
  }

  /* initialize control block map */
  for (xx = 0; xx < BTA_HH_MAX_KNOWN; xx++) {
    bta_hh_cb.cb_index[xx] = BTA_HH_IDX_INVALID;
  }

  if (enable_hid) {
    /* Register with L2CAP */
    if (HID_HostRegister(bta_hh_cback) != HID_SUCCESS) {
      status = BTA_HH_ERR;
    }
  }

  if (status == BTA_HH_OK && enable_hogp) {
    bta_hh_le_enable();
  } else {
    /* signal BTA call back event */
    tBTA_HH bta_hh;
    bta_hh.status = status;
    if (status != BTA_HH_OK) {
      log::error("Failed to register, status:{}", status);
    }
    if (bta_hh_cb.p_cback) {
      (*bta_hh_cb.p_cback)(BTA_HH_ENABLE_EVT, &bta_hh);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_hh_api_disable
 *
 * Description      Perform necessary operations to disable HID host.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_api_disable(void) {
  uint8_t xx;

  /* service is not enabled */
  if (bta_hh_cb.p_cback == NULL) return;

  /* no live connection, signal DISC_CMPL_EVT directly */
  if (!bta_hh_cb.cnt_num) {
    bta_hh_disc_cmpl();
  } else /* otherwise, disconnect all live connections */
  {
    bta_hh_cb.w4_disable = true;

    for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
      /* send API_CLOSE event to every connected device */
      if (bta_hh_cb.kdev[xx].state == BTA_HH_CONN_ST) {
        /* disconnect all connected devices */
        bta_hh_sm_execute(&bta_hh_cb.kdev[xx], BTA_HH_API_CLOSE_EVT, NULL);
      }
    }
  }

  return;
}

/*******************************************************************************
 *
 * Function         bta_hh_disc_cmpl
 *
 * Description      All connections have been closed, disable service.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_disc_cmpl(void) {
  log::debug("Disconnect complete");
  tBTA_HH_STATUS status = BTA_HH_OK;

  /* Deregister with lower layer */
  if (HID_HostDeregister() != HID_SUCCESS) status = BTA_HH_ERR;

  if (bta_hh_cb.gatt_if != BTA_GATTS_INVALID_IF) {
    log::debug("Deregister HOGP host before cleanup");
    bta_hh_le_deregister();
  } else {
    bta_hh_cleanup_disable(status);
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_sdp_cback
 *
 * Description      SDP callback function.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hh_sdp_cback(uint16_t result, uint16_t attr_mask,
                             tHID_DEV_SDP_INFO* sdp_rec) {
  tBTA_HH_DEV_CB* p_cb = bta_hh_cb.p_cur;
  uint8_t hdl = 0;
  tBTA_HH_STATUS status = BTA_HH_ERR_SDP;

  /* make sure sdp succeeded and hh has not been disabled */
  if ((result == SDP_SUCCESS) && (p_cb != NULL)) {
    /* security is required for the connection, add attr_mask bit*/
    attr_mask |= HID_SEC_REQUIRED;

    log::verbose("p_cb:{} result:0x{:02x}, attr_mask:0x{:02x}, handle:0x{:x}",
                 fmt::ptr(p_cb), result, attr_mask, p_cb->hid_handle);

    /* check to see type of device is supported , and should not been added
     * before */
    if (bta_hh_tod_spt(p_cb, sdp_rec->sub_class)) {
      /* if not added before */
      if (p_cb->hid_handle == BTA_HH_INVALID_HANDLE) {
        /*  add device/update attr_mask information */
        if (HID_HostAddDev(p_cb->link_spec.addrt.bda, attr_mask, &hdl) ==
            HID_SUCCESS) {
          status = BTA_HH_OK;
          /* update cb_index[] map */
          bta_hh_cb.cb_index[hdl] = p_cb->index;
        } else {
          p_cb->app_id = 0;
        }
      } else {
        hdl = p_cb->hid_handle;
      }
      /* else : incoming connection after SDP should update the SDP information
       * as well */

      if (p_cb->app_id != 0) {
        /* update cb information with attr_mask, dscp_info etc. */
        bta_hh_add_device_to_list(p_cb, hdl, attr_mask, &sdp_rec->dscp_info,
                                  sdp_rec->sub_class, sdp_rec->ssr_max_latency,
                                  sdp_rec->ssr_min_tout, p_cb->app_id);

        p_cb->dscp_info.ctry_code = sdp_rec->ctry_code;

        status = BTA_HH_OK;
      }

    } else /* type of device is not supported */
      status = BTA_HH_ERR_TOD_UNSPT;
  }

  /* free disc_db when SDP is completed */
  osi_free_and_reset((void**)&bta_hh_cb.p_disc_db);

  /* send SDP_CMPL_EVT into state machine */
  tBTA_HH_DATA bta_hh_data;
  bta_hh_data.status = status;
  bta_hh_sm_execute(p_cb, BTA_HH_SDP_CMPL_EVT, &bta_hh_data);

  return;
}
/*******************************************************************************
 *
 * Function         bta_hh_di_sdp_cback
 *
 * Description      SDP DI callback function.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hh_di_sdp_cback(UNUSED_ATTR const RawAddress& bd_addr,
                                tSDP_RESULT result) {
  tBTA_HH_DEV_CB* p_cb = bta_hh_cb.p_cur;
  tBTA_HH_STATUS status = BTA_HH_ERR_SDP;
  tSDP_DI_GET_RECORD di_rec;
  tHID_STATUS ret;
  log::verbose("p_cb:{} result:0x{:02x}", fmt::ptr(p_cb), result);

  /* if DI record does not exist on remote device, vendor_id in
   * tBTA_HH_DEV_DSCP_INFO will be set to 0xffff and we will allow the
   * connection to go through. Spec mandates that DI record be set, but many
   * HID devices do not set this. So for IOP purposes, we allow the connection
   * to go through and update the DI record to invalid DI entry.
   */
  if (((result == SDP_SUCCESS) || (result == SDP_NO_RECS_MATCH)) &&
      (p_cb != NULL)) {
    if (result == SDP_SUCCESS &&
        get_legacy_stack_sdp_api()->device_id.SDP_GetNumDiRecords(
            bta_hh_cb.p_disc_db) != 0) {
      /* always update information with primary DI record */
      if (get_legacy_stack_sdp_api()->device_id.SDP_GetDiRecord(
              1, &di_rec, bta_hh_cb.p_disc_db) == SDP_SUCCESS) {
        bta_hh_update_di_info(p_cb, di_rec.rec.vendor, di_rec.rec.product,
                              di_rec.rec.version, 0, 0);
      }

    } else /* no DI recrod available */
    {
      bta_hh_update_di_info(p_cb, BTA_HH_VENDOR_ID_INVALID, 0, 0, 0, 0);
    }

    ret = HID_HostGetSDPRecord(p_cb->link_spec.addrt.bda, bta_hh_cb.p_disc_db,
                               p_bta_hh_cfg->sdp_db_size, bta_hh_sdp_cback);
    if (ret == HID_SUCCESS) {
      status = BTA_HH_OK;
    } else {
      log::verbose("failure Status 0x{:2x}", ret);
    }
  }

  if (status != BTA_HH_OK) {
    osi_free_and_reset((void**)&bta_hh_cb.p_disc_db);
    /* send SDP_CMPL_EVT into state machine */
    tBTA_HH_DATA bta_hh_data;
    bta_hh_data.status = status;
    bta_hh_sm_execute(p_cb, BTA_HH_SDP_CMPL_EVT, &bta_hh_data);
  }
  return;
}

/*******************************************************************************
 *
 * Function         bta_hh_start_sdp
 *
 * Description      Start SDP service search, and obtain necessary SDP records.
 *                  Only one SDP service search request is allowed at the same
 *                  time. For every BTA_HhOpen API call, do SDP first unless SDP
 *                  has been done previously.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hh_start_sdp(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  if (!bta_hh_cb.p_disc_db) {
    bta_hh_cb.p_disc_db =
        (tSDP_DISCOVERY_DB*)osi_malloc(p_bta_hh_cfg->sdp_db_size);

    /* Do DI discovery first */
    if (get_legacy_stack_sdp_api()->device_id.SDP_DiDiscover(
            p_data->api_conn.link_spec.addrt.bda, bta_hh_cb.p_disc_db,
            p_bta_hh_cfg->sdp_db_size, bta_hh_di_sdp_cback) == SDP_SUCCESS) {
      /* SDP search started successfully
       * Connection will be triggered at the end of successful SDP search
       */
    } else {
      log::error("SDP_DiDiscover failed");

      osi_free_and_reset((void**)&bta_hh_cb.p_disc_db);

      tBTA_HH_DATA bta_hh_data;
      bta_hh_data.status = BTA_HH_ERR_SDP;
      bta_hh_sm_execute(p_cb, BTA_HH_SDP_CMPL_EVT, &bta_hh_data);
    }
  } else if (bta_hh_cb.p_disc_db) {
    /* Incoming/outgoing collision case. DUT initiated HID connection at the
     * same time as the remote connected HID control channel.
     * When flow reaches here due to remote initiated connection, DUT may be
     * doing SDP. In such case, just do nothing and the ongoing SDP completion
     * or failure will handle this case.
     */
    log::warn("Ignoring as SDP already in progress");
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_sdp_cmpl
 *
 * Description      When SDP completes, initiate a connection or report an error
 *                  depending on the SDP result.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_sdp_cmpl(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  CHECK(p_data != nullptr);

  tBTA_HH_CONN conn_dat;
  tBTA_HH_STATUS status = p_data->status;

  log::verbose("status 0x{:2X}", p_data->status);

  /* initialize call back data */
  memset((void*)&conn_dat, 0, sizeof(tBTA_HH_CONN));
  conn_dat.handle = p_cb->hid_handle;
  conn_dat.link_spec = p_cb->link_spec;

  /* if SDP compl success */
  if (status == BTA_HH_OK) {
    /* not incoming connection doing SDP, initiate a HID connection */
    if (!p_cb->incoming_conn) {
      tHID_STATUS ret;

      /* open HID connection */
      ret = HID_HostOpenDev(p_cb->hid_handle);
      log::verbose("HID_HostOpenDev returned={}", ret);
      if (ret == HID_SUCCESS || ret == HID_ERR_ALREADY_CONN) {
        status = BTA_HH_OK;
      } else if (ret == HID_ERR_CONN_IN_PROCESS) {
        /* Connection already in progress, return from here, SDP
         * will be performed after connection is completed.
         */
        log::verbose("connection already in progress");
        return;
      } else {
        log::verbose("HID_HostOpenDev failed: Status 0x{:2X}", ret);
        /* open fail, remove device from management device list */
        HID_HostRemoveDev(p_cb->hid_handle);
        status = BTA_HH_ERR;
      }
    } else /* incoming connection SDP finish */
    {
      bta_hh_sm_execute(p_cb, BTA_HH_OPEN_CMPL_EVT, NULL);
    }
  }

  if (status != BTA_HH_OK) {
    /* Check if this was incoming connection request  from an unknown device
     * and connection failed due to missing HID Device SDP UUID
     * In above condition, disconnect the link as well as remove the
     * device from list of HID devices
     */
    if ((status == BTA_HH_ERR_SDP) && (p_cb->incoming_conn) &&
        (p_cb->app_id == 0)) {
      log::error("SDP failed for  incoming conn hndl:{}",
                 p_cb->incoming_hid_handle);
      HID_HostRemoveDev(p_cb->incoming_hid_handle);
    }
    conn_dat.status = status;
    (*bta_hh_cb.p_cback)(BTA_HH_OPEN_EVT, (tBTA_HH*)&conn_dat);

    /* move state machine W4_CONN ->IDLE */
    bta_hh_sm_execute(p_cb, BTA_HH_API_CLOSE_EVT, NULL);

    /* if this is an outgoing connection to an unknown device, clean up cb */
    if (p_cb->app_id == 0 && !p_cb->incoming_conn) {
      /* clean up device control block */
      bta_hh_clean_up_kdev(p_cb);
    }
    bta_hh_trace_dev_db();
  }
  p_cb->incoming_conn = false;
  p_cb->incoming_hid_handle = BTA_HH_INVALID_HANDLE;
  return;
}

/*******************************************************************************
 *
 * Function         bta_hh_bredr_conn
 *
 * Description      Initiate BR/EDR HID connection. This may be triggered by
 *                  the local application or as a result of remote initiated
 *                  HID connection.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hh_bredr_conn(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  bta_hh_cb.p_cur = p_cb;

  /* If previously virtually cabled device */
  if (p_cb->app_id) {
    tBTA_HH_DATA bta_hh_data;
    bta_hh_data.status = BTA_HH_OK;

    log::verbose("skip SDP for known devices");

    if (p_cb->hid_handle == BTA_HH_INVALID_HANDLE) {
      uint8_t hdl;
      if (HID_HostAddDev(p_cb->link_spec.addrt.bda, p_cb->attr_mask, &hdl) ==
          HID_SUCCESS) {
        /* update device CB with newly register device handle */
        bta_hh_add_device_to_list(p_cb, hdl, p_cb->attr_mask, NULL,
                                  p_cb->sub_class,
                                  p_cb->dscp_info.ssr_max_latency,
                                  p_cb->dscp_info.ssr_min_tout, p_cb->app_id);
        /* update cb_index[] map */
        bta_hh_cb.cb_index[hdl] = p_cb->index;
      } else {
        bta_hh_data.status = BTA_HH_ERR_NO_RES;
      }
    }

    bta_hh_sm_execute(p_cb, BTA_HH_SDP_CMPL_EVT, &bta_hh_data);
  }
  else { /* First time connection, start SDP */
    bta_hh_start_sdp(p_cb, p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_connect
 *
 * Description      Start HID host connection.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_connect(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  bool hid_available = false;
  bool hogp_available = false;
  bluetooth::Uuid remote_uuids[BT_MAX_NUM_UUIDS] = {};
  bt_property_t remote_properties = {BT_PROPERTY_UUIDS, sizeof(remote_uuids),
                                     &remote_uuids};
  const RawAddress& bd_addr = p_data->api_conn.link_spec.addrt.bda;

  // Find the device type
  tBT_DEVICE_TYPE dev_type;
  tBLE_ADDR_TYPE addr_type;
  BTM_ReadDevInfo(bd_addr, &dev_type, &addr_type);

  // Find which transports are already connected
  bool bredr = BTM_IsAclConnectionUp(bd_addr, BT_TRANSPORT_BR_EDR);
  bool le_acl = BTM_IsAclConnectionUp(bd_addr, BT_TRANSPORT_LE);

  // Find which services known to be available
  if (btif_storage_get_remote_device_property(&bd_addr,
                                              &remote_properties) == BT_STATUS_SUCCESS) {
    int count = remote_properties.len / sizeof(remote_uuids[0]);
    for (int i = 0; i < count; i++) {
      if (remote_uuids[i].Is16Bit()) {
        if (remote_uuids[i].As16Bit() == UUID_SERVCLASS_HUMAN_INTERFACE) {
          hid_available = true;
        } else if (remote_uuids[i].As16Bit() == UUID_SERVCLASS_LE_HID) {
          hogp_available = true;
        }
      }

      if (hid_available && hogp_available) {
        break;
      }
    }
  }

  /* Decide whether to connect HID or HOGP */
  if (bredr && hid_available) {
    p_cb->is_le_device = false;
  } else if (le_acl && hogp_available) {
    p_cb->is_le_device = true;
  } else if (hid_available) {
    p_cb->is_le_device = false;
  } else if (hogp_available) {
    p_cb->is_le_device = true;
  } else if (bredr) {
    p_cb->is_le_device = false;
  } else if (le_acl || dev_type == BT_DEVICE_TYPE_BLE) {
    p_cb->is_le_device = true;
  } else {
    p_cb->is_le_device = false;
  }

  log::debug(
      "bd_addr:{}, bredr:{}, hid_available:{}, le_acl:{}, hogp_available:{}, "
      "dev_type:{}, is_le_device:{}",
      ADDRESS_TO_LOGGABLE_CSTR(bd_addr), bredr, hid_available, le_acl,
      hogp_available, dev_type, p_cb->is_le_device);

  p_cb->mode = p_data->api_conn.mode;
  bta_hh_cb.p_cur = p_cb;

  // Initiate HID host connection
  if (p_cb->is_le_device) {
    bta_hh_le_open_conn(p_cb, p_data->api_conn.link_spec);
  } else {
    bta_hh_bredr_conn(p_cb, p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_api_disc_act
 *
 * Description      HID Host initiate a disconnection.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void btif_hh_remove_device(tAclLinkSpec link_spec);
void bta_hh_api_disc_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  CHECK(p_cb != nullptr);

  if (p_cb->is_le_device) {
    log::debug("Host initiating close to le device:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_cb->link_spec));

    bta_hh_le_api_disc_act(p_cb);

  } else {
    const uint8_t hid_handle =
        (p_data != nullptr) ? static_cast<uint8_t>(p_data->hdr.layer_specific)
                            : p_cb->hid_handle;
    tHID_STATUS status = HID_HostCloseDev(hid_handle);
    if (status != HID_SUCCESS) {
      log::warn("Failed closing classic device:{} status:{}",
                ADDRESS_TO_LOGGABLE_CSTR(p_cb->link_spec),
                hid_status_text(status));
    } else {
      log::debug("Host initiated close to classic device:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_cb->link_spec));
    }
    tBTA_HH bta_hh = {
        .dev_status = {.status =
                           (status == HID_SUCCESS) ? BTA_HH_OK : BTA_HH_ERR,
                       .handle = hid_handle},
    };
    (*bta_hh_cb.p_cback)(BTA_HH_CLOSE_EVT, &bta_hh);
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_open_cmpl_act
 *
 * Description      HID host connection completed
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_open_cmpl_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  tBTA_HH_CONN conn;
  uint8_t dev_handle =
      p_data ? (uint8_t)p_data->hid_cback.hdr.layer_specific : p_cb->hid_handle;

  memset((void*)&conn, 0, sizeof(tBTA_HH_CONN));
  conn.handle = dev_handle;
  conn.link_spec = p_cb->link_spec;

  /* increase connection number */
  bta_hh_cb.cnt_num++;

  conn.status = p_cb->status;
  conn.le_hid = p_cb->is_le_device;
  conn.scps_supported = p_cb->scps_supported;
  conn.sub_class = p_cb->sub_class;
  conn.attr_mask = p_cb->attr_mask;
  conn.app_id = p_cb->app_id;

  BTM_LogHistory(kBtmLogTag, p_cb->link_spec.addrt.bda, "Opened",
                 base::StringPrintf(
                     "%s initiator:%s", (p_cb->is_le_device) ? "le" : "classic",
                     (p_cb->incoming_conn) ? "remote" : "local"));

  if (!p_cb->is_le_device)
  {
    /* inform role manager */
    bta_sys_conn_open(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);

    /* set protocol mode when not default report mode */
    if (p_cb->mode != BTA_HH_PROTO_RPT_MODE) {
      tHID_STATUS status =
          HID_HostWriteDev(dev_handle, HID_TRANS_SET_PROTOCOL,
                           HID_PAR_PROTOCOL_BOOT_MODE, 0, 0, NULL);

      if (status == HID_SUCCESS) {
        p_cb->w4_evt = BTA_HH_SET_PROTO_EVT;
      } else {
        /* HID connection is up, while SET_PROTO fail */
        conn.status = BTA_HH_ERR_PROTO;
      }
    }
  }
  p_cb->incoming_conn = false;
  p_cb->incoming_hid_handle = BTA_HH_INVALID_HANDLE;

  (*bta_hh_cb.p_cback)(BTA_HH_OPEN_EVT, (tBTA_HH*)&conn);
}
/*******************************************************************************
 *
 * Function         bta_hh_open_act
 *
 * Description      HID host receive HID_OPEN_EVT .
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_open_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  tBTA_HH_API_CONN conn_data;

  uint8_t dev_handle =
      p_data ? (uint8_t)p_data->hid_cback.hdr.layer_specific : p_cb->hid_handle;

  log::verbose("Device[{}] connected", dev_handle);

  /* SDP has been done */
  if (p_cb->app_id != 0) {
    bta_hh_sm_execute(p_cb, BTA_HH_OPEN_CMPL_EVT, p_data);
  } else
  /*  app_id == 0 indicates an incoming conenction request arrives without SDP
   *  performed, do it first
   */
  {
    p_cb->incoming_conn = true;
    /* store the handle here in case sdp fails - need to disconnect */
    p_cb->incoming_hid_handle = dev_handle;

    memset(&conn_data, 0, sizeof(tBTA_HH_API_CONN));
    conn_data.link_spec = p_cb->link_spec;
    bta_hh_cb.p_cur = p_cb;
    bta_hh_bredr_conn(p_cb, (tBTA_HH_DATA*)&conn_data);
  }

  return;
}

/*******************************************************************************
 *
 * Function         bta_hh_data_act
 *
 * Description      HID Host process a data report
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_data_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  BT_HDR* pdata = p_data->hid_cback.p_data;
  uint8_t* p_rpt = (uint8_t*)(pdata + 1) + pdata->offset;

  bta_hh_co_data((uint8_t)p_data->hid_cback.hdr.layer_specific, p_rpt,
                 pdata->len, p_cb->mode, p_cb->sub_class,
                 p_cb->dscp_info.ctry_code, p_cb->link_spec, p_cb->app_id);

  osi_free_and_reset((void**)&pdata);
}

/*******************************************************************************
 *
 * Function         bta_hh_handsk_act
 *
 * Description      HID Host process a handshake acknowledgement.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_handsk_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  log::verbose("HANDSHAKE received for: event={} data={}",
               bta_hh_get_w4_event(p_cb->w4_evt), p_data->hid_cback.data);

  tBTA_HH bta_hh;
  memset(&bta_hh, 0, sizeof(tBTA_HH));

  switch (p_cb->w4_evt) {
    /* GET_ transsaction, handshake indicate unsupported request */
    case BTA_HH_GET_PROTO_EVT:
      bta_hh.hs_data.rsp_data.proto_mode = BTA_HH_PROTO_UNKNOWN;
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    case BTA_HH_GET_RPT_EVT:
    case BTA_HH_GET_IDLE_EVT:
      bta_hh.hs_data.handle = p_cb->hid_handle;
      /* if handshake gives an OK code for these transaction, fill in UNSUPT */
      bta_hh.hs_data.status = bta_hh_get_trans_status(p_data->hid_cback.data);
      if (bta_hh.hs_data.status == BTA_HH_OK)
        bta_hh.hs_data.status = BTA_HH_HS_TRANS_NOT_SPT;
      (*bta_hh_cb.p_cback)(p_cb->w4_evt, &bta_hh);
      p_cb->w4_evt = 0;
      break;

    /* acknoledgement from HID device for SET_ transaction */
    case BTA_HH_SET_RPT_EVT:
    case BTA_HH_SET_PROTO_EVT:
    case BTA_HH_SET_IDLE_EVT:
      bta_hh.dev_status.handle = p_cb->hid_handle;
      bta_hh.dev_status.status =
          bta_hh_get_trans_status(p_data->hid_cback.data);
      (*bta_hh_cb.p_cback)(p_cb->w4_evt, &bta_hh);
      p_cb->w4_evt = 0;
      break;

    /* SET_PROTOCOL when open connection */
    case BTA_HH_OPEN_EVT:
      bta_hh.conn.status =
          p_data->hid_cback.data ? BTA_HH_ERR_PROTO : BTA_HH_OK;
      bta_hh.conn.handle = p_cb->hid_handle;
      bta_hh.conn.link_spec = p_cb->link_spec;
      (*bta_hh_cb.p_cback)(p_cb->w4_evt, &bta_hh);
      bta_hh_trace_dev_db();
      p_cb->w4_evt = 0;
      break;

    default:
      /* unknow transaction handshake response */
      log::verbose("unknown transaction type");
      break;
  }

  /* transaction achknoledgement received, inform PM for mode change */
  bta_sys_idle(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
  return;
}
/*******************************************************************************
 *
 * Function         bta_hh_ctrl_dat_act
 *
 * Description      HID Host process a data report from control channel.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_ctrl_dat_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  BT_HDR* pdata = p_data->hid_cback.p_data;
  uint8_t* data = (uint8_t*)(pdata + 1) + pdata->offset;
  tBTA_HH_HSDATA hs_data;

  log::verbose("Ctrl DATA received w4: event[{}]",
               bta_hh_get_w4_event(p_cb->w4_evt));
  if (pdata->len == 0) {
    p_cb->w4_evt = 0;
    osi_free_and_reset((void**)&pdata);
    return;
  }
  hs_data.status = BTA_HH_OK;
  hs_data.handle = p_cb->hid_handle;

  switch (p_cb->w4_evt) {
    case BTA_HH_GET_IDLE_EVT:
      hs_data.rsp_data.idle_rate = *data;
      break;
    case BTA_HH_GET_RPT_EVT:
      hs_data.rsp_data.p_rpt_data = pdata;
      break;
    case BTA_HH_GET_PROTO_EVT:
      /* match up BTE/BTA report/boot mode def*/
      hs_data.rsp_data.proto_mode = ((*data) == HID_PAR_PROTOCOL_REPORT)
                                        ? BTA_HH_PROTO_RPT_MODE
                                        : BTA_HH_PROTO_BOOT_MODE;
      log::verbose("GET_PROTOCOL Mode = [{}]",
                   (hs_data.rsp_data.proto_mode == BTA_HH_PROTO_RPT_MODE)
                       ? "Report"
                       : "Boot");
      break;
    /* should not expect control DATA for SET_ transaction */
    case BTA_HH_SET_PROTO_EVT:
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    case BTA_HH_SET_RPT_EVT:
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    case BTA_HH_SET_IDLE_EVT:
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */
    default:
      log::verbose("invalid  transaction type for DATA payload:4_evt[{}]",
                   bta_hh_get_w4_event(p_cb->w4_evt));
      break;
  }

  /* inform PM for mode change */
  bta_sys_busy(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
  bta_sys_idle(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);

  (*bta_hh_cb.p_cback)(p_cb->w4_evt, (tBTA_HH*)&hs_data);

  p_cb->w4_evt = 0;
  osi_free_and_reset((void**)&pdata);
}

/*******************************************************************************
 *
 * Function         bta_hh_open_failure
 *
 * Description      report HID open failure when at wait for connection state
 *                  and receive device close event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_open_failure(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  tBTA_HH_CONN conn_dat;
  uint32_t reason = p_data->hid_cback.data; /* Reason for closing (32-bit) */

  memset(&conn_dat, 0, sizeof(tBTA_HH_CONN));
  conn_dat.handle = p_cb->hid_handle;
  conn_dat.status =
      (reason == HID_ERR_AUTH_FAILED) ? BTA_HH_ERR_AUTH_FAILED : BTA_HH_ERR;
  conn_dat.link_spec = p_cb->link_spec;
  HID_HostCloseDev(p_cb->hid_handle);

  /* Report OPEN fail event */
  (*bta_hh_cb.p_cback)(BTA_HH_OPEN_EVT, (tBTA_HH*)&conn_dat);

  bta_hh_trace_dev_db();
  /* clean up control block, but retain SDP info and device handle */
  p_cb->vp = false;
  p_cb->w4_evt = 0;

  /* if no connection is active and HH disable is signaled, disable service */
  if (bta_hh_cb.cnt_num == 0 && bta_hh_cb.w4_disable) {
    bta_hh_disc_cmpl();
  }

  /* Error in opening hid connection, reset flags */
  p_cb->incoming_conn = false;
  p_cb->incoming_hid_handle = BTA_HH_INVALID_HANDLE;
}

/*******************************************************************************
 *
 * Function         bta_hh_close_act
 *
 * Description      HID Host process a close event
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_close_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  tBTA_HH_CBDATA disc_dat = {BTA_HH_OK, 0};

  uint32_t reason = p_data->hid_cback.data; /* Reason for closing (32-bit) */
  const bool l2cap_conn_fail = reason & HID_L2CAP_CONN_FAIL;
  const bool l2cap_req_fail = reason & HID_L2CAP_REQ_FAIL;
  const bool l2cap_cfg_fail = reason & HID_L2CAP_CFG_FAIL;
  const tHID_STATUS hid_status = static_cast<tHID_STATUS>(reason & 0xff);

  /* if HID_HDEV_EVT_VC_UNPLUG was received, report BTA_HH_VC_UNPLUG_EVT */
  uint16_t event = p_cb->vp ? BTA_HH_VC_UNPLUG_EVT : BTA_HH_CLOSE_EVT;

  disc_dat.handle = p_cb->hid_handle;
  disc_dat.status = to_bta_hh_status(p_data->hid_cback.data);

  std::string overlay_fail =
      base::StringPrintf("%s %s %s", (l2cap_conn_fail) ? "l2cap_conn_fail" : "",
                         (l2cap_req_fail) ? "l2cap_req_fail" : "",
                         (l2cap_cfg_fail) ? "l2cap_cfg_fail" : "");
  BTM_LogHistory(kBtmLogTag, p_cb->link_spec.addrt.bda, "Closed",
                 base::StringPrintf("%s reason %s %s",
                                    (p_cb->is_le_device) ? "le" : "classic",
                                    hid_status_text(hid_status).c_str(),
                                    overlay_fail.c_str()));

  /* inform role manager */
  bta_sys_conn_close(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
  /* update total conn number */
  bta_hh_cb.cnt_num--;

  if (disc_dat.status) disc_dat.status = BTA_HH_ERR;

  (*bta_hh_cb.p_cback)(event, (tBTA_HH*)&disc_dat);

  /* if virtually unplug, remove device */
  if (p_cb->vp) {
    HID_HostRemoveDev(p_cb->hid_handle);
    bta_hh_clean_up_kdev(p_cb);
  }

  bta_hh_trace_dev_db();

  /* clean up control block, but retain SDP info and device handle */
  p_cb->vp = false;
  p_cb->w4_evt = 0;

  /* if no connection is active and HH disable is signaled, disable service */
  if (bta_hh_cb.cnt_num == 0 && bta_hh_cb.w4_disable) {
    bta_hh_disc_cmpl();
  }

  return;
}

/*******************************************************************************
 *
 * Function         bta_hh_get_dscp_act
 *
 * Description      Get device report descriptor
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_get_dscp_act(tBTA_HH_DEV_CB* p_cb,
                         UNUSED_ATTR const tBTA_HH_DATA* p_data) {
  if (p_cb->is_le_device) {
    if (p_cb->hid_srvc.state >= BTA_HH_SERVICE_DISCOVERED) {
      p_cb->dscp_info.hid_handle = p_cb->hid_handle;
    }
    bta_hh_le_get_dscp_act(p_cb);
  } else {
    p_cb->dscp_info.hid_handle = p_cb->hid_handle;
    (*bta_hh_cb.p_cback)(BTA_HH_GET_DSCP_EVT, (tBTA_HH*)&p_cb->dscp_info);
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_maint_dev_act
 *
 * Description      HID Host maintain device list.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_maint_dev_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  const tBTA_HH_MAINT_DEV* p_dev_info = &p_data->api_maintdev;
  tBTA_HH_DEV_INFO dev_info;
  uint8_t dev_handle;

  dev_info.status = BTA_HH_ERR;
  dev_info.handle = BTA_HH_INVALID_HANDLE;

  switch (p_dev_info->sub_event) {
    case BTA_HH_ADD_DEV_EVT: /* add a device */
      dev_info.link_spec = p_dev_info->link_spec;
      /* initialize callback data */
      if (p_cb->hid_handle == BTA_HH_INVALID_HANDLE) {
        if (BTM_UseLeLink(p_data->api_conn.link_spec.addrt.bda)) {
          p_cb->is_le_device = true;
          dev_info.handle = bta_hh_le_add_device(p_cb, p_dev_info);
          if (dev_info.handle != BTA_HH_INVALID_HANDLE)
            dev_info.status = BTA_HH_OK;
        } else

            if (HID_HostAddDev(p_dev_info->link_spec.addrt.bda,
                               p_dev_info->attr_mask,
                               &dev_handle) == HID_SUCCESS) {
          dev_info.handle = dev_handle;
          dev_info.status = BTA_HH_OK;

          /* update DI information */
          bta_hh_update_di_info(
              p_cb, p_dev_info->dscp_info.vendor_id,
              p_dev_info->dscp_info.product_id, p_dev_info->dscp_info.version,
              p_dev_info->dscp_info.flag, p_dev_info->dscp_info.ctry_code);

          /* add to BTA device list */
          bta_hh_add_device_to_list(
              p_cb, dev_handle, p_dev_info->attr_mask,
              &p_dev_info->dscp_info.descriptor, p_dev_info->sub_class,
              p_dev_info->dscp_info.ssr_max_latency,
              p_dev_info->dscp_info.ssr_min_tout, p_dev_info->app_id);
          /* update cb_index[] map */
          bta_hh_cb.cb_index[dev_handle] = p_cb->index;
        }
      } else /* device already been added */
      {
        dev_info.handle = p_cb->hid_handle;
        dev_info.status = BTA_HH_OK;
      }
      bta_hh_trace_dev_db();

      break;
    case BTA_HH_RMV_DEV_EVT: /* remove device */
      dev_info.handle = (uint8_t)p_dev_info->hdr.layer_specific;
      dev_info.link_spec = p_cb->link_spec;

      if (p_cb->is_le_device) {
        bta_hh_le_remove_dev_bg_conn(p_cb);
        bta_hh_sm_execute(p_cb, BTA_HH_API_CLOSE_EVT, NULL);
        bta_hh_clean_up_kdev(p_cb);
      } else
      {
        if (HID_HostRemoveDev(dev_info.handle) == HID_SUCCESS) {
          dev_info.status = BTA_HH_OK;

          /* remove from known device list in BTA */
          bta_hh_clean_up_kdev(p_cb);
        }
      }
      break;

    default:
      log::verbose("invalid command");
      break;
  }

  (*bta_hh_cb.p_cback)(p_dev_info->sub_event, (tBTA_HH*)&dev_info);
}
/*******************************************************************************
 *
 * Function         bta_hh_write_dev_act
 *
 * Description      Write device action. can be SET/GET/DATA transaction.
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t convert_api_sndcmd_param(const tBTA_HH_CMD_DATA& api_sndcmd) {
  uint8_t api_sndcmd_param = api_sndcmd.param;
  if (api_sndcmd.t_type == HID_TRANS_SET_PROTOCOL) {
    api_sndcmd_param = (api_sndcmd.param == BTA_HH_PROTO_RPT_MODE)
                           ? HID_PAR_PROTOCOL_REPORT
                           : HID_PAR_PROTOCOL_BOOT_MODE;
  }
  return api_sndcmd_param;
}

void bta_hh_write_dev_act(tBTA_HH_DEV_CB* p_cb, const tBTA_HH_DATA* p_data) {
  uint16_t event =
      (p_data->api_sndcmd.t_type - HID_TRANS_GET_REPORT) + BTA_HH_GET_RPT_EVT;

  if (p_cb->is_le_device)
    bta_hh_le_write_dev_act(p_cb, p_data);
  else {
    /* match up BTE/BTA report/boot mode def */
    const uint8_t api_sndcmd_param =
        convert_api_sndcmd_param(p_data->api_sndcmd);

    tHID_STATUS status = HID_HostWriteDev(p_cb->hid_handle,
                                          p_data->api_sndcmd.t_type,
                                          api_sndcmd_param,
                                          p_data->api_sndcmd.data,
                                          p_data->api_sndcmd.rpt_id,
                                          p_data->api_sndcmd.p_data);
    if (status != HID_SUCCESS) {
      log::error("HID_HostWriteDev Error, status:{}", status);

      if (p_data->api_sndcmd.t_type != HID_TRANS_CONTROL &&
          p_data->api_sndcmd.t_type != HID_TRANS_DATA) {
        BT_HDR cbhdr = {
          .event = BTA_HH_GET_RPT_EVT,
          .len = 0,
          .offset = 0,
          .layer_specific = 0,
        };
        tBTA_HH cbdata = {
          .hs_data = {
            .status = BTA_HH_ERR,
            .handle = p_cb->hid_handle,
            .rsp_data = {
              .p_rpt_data = &cbhdr,
            },
          },
        };
        (*bta_hh_cb.p_cback)(event, &cbdata);
      } else if (api_sndcmd_param == BTA_HH_CTRL_VIRTUAL_CABLE_UNPLUG) {
        tBTA_HH cbdata = {
          .dev_status = {
            .status = BTA_HH_ERR,
            .handle = p_cb->hid_handle,
          },
        };
        (*bta_hh_cb.p_cback)(BTA_HH_VC_UNPLUG_EVT, &cbdata);
      } else {
        log::error(
            "skipped executing callback in hid host error handling. command "
            "type:{}, param:{}",
            p_data->api_sndcmd.t_type, p_data->api_sndcmd.param);
      }
    } else {
      switch (p_data->api_sndcmd.t_type) {
        case HID_TRANS_SET_PROTOCOL:
          FALLTHROUGH_INTENDED; /* FALLTHROUGH */
        case HID_TRANS_GET_REPORT:
          FALLTHROUGH_INTENDED; /* FALLTHROUGH */
        case HID_TRANS_SET_REPORT:
          FALLTHROUGH_INTENDED; /* FALLTHROUGH */
        case HID_TRANS_GET_PROTOCOL:
          FALLTHROUGH_INTENDED; /* FALLTHROUGH */
        case HID_TRANS_GET_IDLE:
          FALLTHROUGH_INTENDED;  /* FALLTHROUGH */
        case HID_TRANS_SET_IDLE: /* set w4_handsk event name for callback
                                    function use */
          p_cb->w4_evt = event;
          break;
        case HID_TRANS_DATA: /* output report */
          FALLTHROUGH_INTENDED; /* FALLTHROUGH */
        case HID_TRANS_CONTROL:
          /* no handshake event will be generated */
          /* if VC_UNPLUG is issued, set flag */
          if (api_sndcmd_param == BTA_HH_CTRL_VIRTUAL_CABLE_UNPLUG)
            p_cb->vp = true;

          break;
        /* currently not expected */
        case HID_TRANS_DATAC:
        default:
          log::verbose("cmd type={}", p_data->api_sndcmd.t_type);
          break;
      }

      /* if not control type transaction, notify PM for energy control */
      if (p_data->api_sndcmd.t_type != HID_TRANS_CONTROL) {
        /* inform PM for mode change */
        bta_sys_busy(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
        bta_sys_idle(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
      } else if (api_sndcmd_param == BTA_HH_CTRL_SUSPEND) {
        bta_sys_sco_close(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
      } else if (api_sndcmd_param == BTA_HH_CTRL_EXIT_SUSPEND) {
        bta_sys_busy(BTA_ID_HH, p_cb->app_id, p_cb->link_spec.addrt.bda);
      }
    }
  }
  return;
}

/*****************************************************************************
 *  Static Function
 ****************************************************************************/
/*******************************************************************************
 *
 * Function         bta_hh_cback
 *
 * Description      BTA HH callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hh_cback(uint8_t dev_handle, const RawAddress& addr,
                         uint8_t event, uint32_t data, BT_HDR* pdata) {
  uint16_t sm_event = BTA_HH_INVALID_EVT;
  uint8_t xx = 0;

  log::verbose("HID_event [{}]", bta_hh_hid_event_name(event));

  switch (event) {
    case HID_HDEV_EVT_OPEN:
      sm_event = BTA_HH_INT_OPEN_EVT;
      break;
    case HID_HDEV_EVT_CLOSE:
      sm_event = BTA_HH_INT_CLOSE_EVT;
      break;
    case HID_HDEV_EVT_INTR_DATA:
      sm_event = BTA_HH_INT_DATA_EVT;
      break;
    case HID_HDEV_EVT_HANDSHAKE:
      sm_event = BTA_HH_INT_HANDSK_EVT;
      break;
    case HID_HDEV_EVT_CTRL_DATA:
      sm_event = BTA_HH_INT_CTRL_DATA;
      break;
    case HID_HDEV_EVT_RETRYING:
      break;
    case HID_HDEV_EVT_INTR_DATC:
    case HID_HDEV_EVT_CTRL_DATC:
      /* Unhandled events: Free buffer for DATAC */
      osi_free_and_reset((void**)&pdata);
      break;
    case HID_HDEV_EVT_VC_UNPLUG:
      for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
        if (bta_hh_cb.kdev[xx].hid_handle == dev_handle) {
          bta_hh_cb.kdev[xx].vp = true;
          break;
        }
      }
      break;
  }

  if (sm_event != BTA_HH_INVALID_EVT) {
    tBTA_HH_CBACK_DATA* p_buf = (tBTA_HH_CBACK_DATA*)osi_malloc(
        sizeof(tBTA_HH_CBACK_DATA) + sizeof(BT_HDR));
    p_buf->hdr.event = sm_event;
    p_buf->hdr.layer_specific = (uint16_t)dev_handle;
    p_buf->data = data;
    p_buf->link_spec.addrt.bda = addr;
    p_buf->link_spec.addrt.type = BLE_ADDR_PUBLIC;
    p_buf->link_spec.transport = BT_TRANSPORT_BR_EDR;
    p_buf->p_data = pdata;

    bta_sys_sendmsg(p_buf);
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_get_trans_status
 *
 * Description      translate a handshake result code into BTA HH
 *                  status code
 *
 ******************************************************************************/
static tBTA_HH_STATUS bta_hh_get_trans_status(uint32_t result) {
  switch (result) {
    case HID_PAR_HANDSHAKE_RSP_SUCCESS: /*   (0) */
      return BTA_HH_OK;
    case HID_PAR_HANDSHAKE_RSP_NOT_READY:           /*   (1) */
    case HID_PAR_HANDSHAKE_RSP_ERR_INVALID_REP_ID:  /*   (2) */
    case HID_PAR_HANDSHAKE_RSP_ERR_UNSUPPORTED_REQ: /*   (3) */
    case HID_PAR_HANDSHAKE_RSP_ERR_INVALID_PARAM:   /*   (4) */
      return (tBTA_HH_STATUS)result;
    case HID_PAR_HANDSHAKE_RSP_ERR_UNKNOWN: /*   (14) */
    case HID_PAR_HANDSHAKE_RSP_ERR_FATAL:   /*   (15) */
    default:
      return BTA_HH_HS_ERROR;
      break;
  }
}
/*****************************************************************************
 *  Debug Functions
 ****************************************************************************/

static const char* bta_hh_get_w4_event(uint16_t event) {
  switch (event) {
    case BTA_HH_GET_RPT_EVT:
      return "BTA_HH_GET_RPT_EVT";
    case BTA_HH_SET_RPT_EVT:
      return "BTA_HH_SET_RPT_EVT";
    case BTA_HH_GET_PROTO_EVT:
      return "BTA_HH_GET_PROTO_EVT";
    case BTA_HH_SET_PROTO_EVT:
      return "BTA_HH_SET_PROTO_EVT";
    case BTA_HH_GET_IDLE_EVT:
      return "BTA_HH_GET_IDLE_EVT";
    case BTA_HH_SET_IDLE_EVT:
      return "BTA_HH_SET_IDLE_EVT";
    case BTA_HH_OPEN_EVT:
      return "BTA_HH_OPEN_EVT";
    default:
      return "Unknown event";
  }
}

static const char* bta_hh_hid_event_name(uint16_t event) {
  switch (event) {
    case HID_HDEV_EVT_OPEN:
      return "HID_HDEV_EVT_OPEN";
    case HID_HDEV_EVT_CLOSE:
      return "HID_HDEV_EVT_CLOSE";
    case HID_HDEV_EVT_RETRYING:
      return "HID_HDEV_EVT_RETRYING";
    case HID_HDEV_EVT_INTR_DATA:
      return "HID_HDEV_EVT_INTR_DATA";
    case HID_HDEV_EVT_INTR_DATC:
      return "HID_HDEV_EVT_INTR_DATC";
    case HID_HDEV_EVT_CTRL_DATA:
      return "HID_HDEV_EVT_CTRL_DATA";
    case HID_HDEV_EVT_CTRL_DATC:
      return "HID_HDEV_EVT_CTRL_DATC";
    case HID_HDEV_EVT_HANDSHAKE:
      return "HID_HDEV_EVT_HANDSHAKE";
    case HID_HDEV_EVT_VC_UNPLUG:
      return "HID_HDEV_EVT_VC_UNPLUG";
    default:
      return "Unknown HID event";
  }
}
