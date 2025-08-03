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
#define LOG_TAG "bt_bta_hh"

#include <bluetooth/log.h>
#include <string.h>  // memset

#include <cstring>

#include "bta/hh/bta_hh_int.h"
#include "btif/include/btif_storage.h"
#include "device/include/interop.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/sdp_api.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

/* if SSR max latency is not defined by remote device, set the default value
   as half of the link supervision timeout */
#define BTA_HH_GET_DEF_SSR_MAX_LAT(x) ((x) >> 1)

/*****************************************************************************
 *  Constants
 ****************************************************************************/

namespace {

constexpr uint16_t kSsrMaxLatency = 18; /* slots * 0.625ms */

}  // namespace

/*******************************************************************************
 *
 * Function         bta_hh_find_cb
 *
 * Description      Find best available control block according to BD address.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
uint8_t bta_hh_find_cb(const tAclLinkSpec& link_spec) {
  uint8_t xx;

  /* See how many active devices there are. */
  for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
    /* check if any active/known devices is a match */
    if ((link_spec.addrt.bda == bta_hh_cb.kdev[xx].link_spec.addrt.bda &&
         !link_spec.addrt.bda.IsEmpty())) {
#if (BTA_HH_DEBUG == TRUE)
      log::verbose("found kdev_cb[{}] hid_handle={}", xx,
                   bta_hh_cb.kdev[xx].hid_handle);
#endif
      return xx;
    }
#if (BTA_HH_DEBUG == TRUE)
    else
      log::verbose("in_use ? [{}] kdev[{}].hid_handle={} state=[{}]",
                   bta_hh_cb.kdev[xx].in_use, xx, bta_hh_cb.kdev[xx].hid_handle,
                   bta_hh_cb.kdev[xx].state);
#endif
  }

  /* if no active device match, find a spot for it */
  for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
    if (!bta_hh_cb.kdev[xx].in_use) {
      bta_hh_cb.kdev[xx].link_spec = link_spec;
      break;
    }
  }
/* If device list full, report BTA_HH_IDX_INVALID */
#if (BTA_HH_DEBUG == TRUE)
  log::verbose("index={} while max={}", xx, BTA_HH_MAX_DEVICE);
#endif

  if (xx == BTA_HH_MAX_DEVICE) xx = BTA_HH_IDX_INVALID;

  return xx;
}

tBTA_HH_DEV_CB* bta_hh_get_cb(const tAclLinkSpec& link_spec) {
  uint8_t idx = bta_hh_find_cb(link_spec);
  if (idx == BTA_HH_IDX_INVALID) {
    return nullptr;
  }
  return &bta_hh_cb.kdev[idx];
}

/*******************************************************************************
 *
 * Function         bta_hh_clean_up_kdev
 *
 * Description      Clean up device control block when device is removed from
 *                  manitainace list, and update control block index map.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_clean_up_kdev(tBTA_HH_DEV_CB* p_cb) {
  uint8_t index;

  if (p_cb->is_le_device) {
    uint8_t le_hid_handle = BTA_HH_GET_LE_CB_IDX(p_cb->hid_handle);
    if (le_hid_handle >= BTA_HH_LE_MAX_KNOWN) {
      log::warn("Invalid LE hid_handle {}", p_cb->hid_handle);
    } else {
      bta_hh_cb.le_cb_index[le_hid_handle] = BTA_HH_IDX_INVALID;
    }
  } else {
    if (p_cb->hid_handle >= BTA_HH_MAX_KNOWN) {
      log::warn("Invalid hid_handle {}", p_cb->hid_handle);
    } else {
      bta_hh_cb.cb_index[p_cb->hid_handle] = BTA_HH_IDX_INVALID;
    }
  }

  /* reset device control block */
  index = p_cb->index; /* Preserve index for this control block */

  /* Free buffer for report descriptor info */
  osi_free_and_reset((void**)&p_cb->dscp_info.descriptor.dsc_list);

  memset(p_cb, 0, sizeof(tBTA_HH_DEV_CB)); /* Reset control block */

  p_cb->index = index; /* Restore index for this control block */
  p_cb->state = BTA_HH_IDLE_ST;
  p_cb->hid_handle = BTA_HH_INVALID_HANDLE;
}
/*******************************************************************************
 *
 * Function         bta_hh_update_di_info
 *
 * Description      Maintain a known device list for BTA HH.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_update_di_info(tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id,
                           uint16_t product_id, uint16_t version, uint8_t flag,
                           uint8_t ctry_code) {
#if (BTA_HH_DEBUG == TRUE)
  log::verbose("vendor_id=0x{:2x} product_id=0x{:2x} version=0x{:2x}",
               vendor_id, product_id, version);
#endif
  p_cb->dscp_info.vendor_id = vendor_id;
  p_cb->dscp_info.product_id = product_id;
  p_cb->dscp_info.version = version;
  p_cb->dscp_info.flag = flag;
  p_cb->dscp_info.ctry_code = ctry_code;
}
/*******************************************************************************
 *
 * Function         bta_hh_add_device_to_list
 *
 * Description      Maintain a known device list for BTA HH.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_add_device_to_list(tBTA_HH_DEV_CB* p_cb, uint8_t handle,
                               uint16_t attr_mask,
                               const tHID_DEV_DSCP_INFO* p_dscp_info,
                               uint8_t sub_class, uint16_t ssr_max_latency,
                               uint16_t ssr_min_tout, uint8_t app_id) {
#if (BTA_HH_DEBUG == TRUE)
  log::verbose("subclass=0x{:2x}", sub_class);
#endif

  p_cb->hid_handle = handle;
  p_cb->in_use = true;
  p_cb->attr_mask = attr_mask;

  p_cb->sub_class = sub_class;
  p_cb->app_id = app_id;

  p_cb->dscp_info.ssr_max_latency = ssr_max_latency;
  p_cb->dscp_info.ssr_min_tout = ssr_min_tout;

  /* store report descriptor info */
  if (p_dscp_info) {
    osi_free_and_reset((void**)&p_cb->dscp_info.descriptor.dsc_list);

    if (p_dscp_info->dl_len) {
      p_cb->dscp_info.descriptor.dsc_list =
          (uint8_t*)osi_malloc(p_dscp_info->dl_len);
      p_cb->dscp_info.descriptor.dl_len = p_dscp_info->dl_len;
      memcpy(p_cb->dscp_info.descriptor.dsc_list, p_dscp_info->dsc_list,
             p_dscp_info->dl_len);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_tod_spt
 *
 * Description      Check to see if this type of device is supported
 *
 * Returns
 *
 ******************************************************************************/
bool bta_hh_tod_spt(tBTA_HH_DEV_CB* p_cb, uint8_t sub_class) {
  uint8_t xx;
  uint8_t cod = (sub_class >> 2); /* lower two bits are reserved */

  for (xx = 0; xx < p_bta_hh_cfg->max_devt_spt; xx++) {
    if (cod == (uint8_t)p_bta_hh_cfg->p_devt_list[xx].tod) {
      p_cb->app_id = p_bta_hh_cfg->p_devt_list[xx].app_id;
#if (BTA_HH_DEBUG == TRUE)
      log::verbose("sub_class:0x{:x} supported", sub_class);
#endif
      return true;
    }
  }
#if (BTA_HH_DEBUG == TRUE)
  log::verbose("sub_class:0x{:x} NOT supported", sub_class);
#endif
  return false;
}


/*******************************************************************************
 *
 * Function         bta_hh_read_ssr_param
 *
 * Description      Read the SSR Parameter for the remote device
 *
 * Returns          tBTA_HH_STATUS  operation status
 *
 ******************************************************************************/
tBTA_HH_STATUS bta_hh_read_ssr_param(const tAclLinkSpec& link_spec,
                                     uint16_t* p_max_ssr_lat,
                                     uint16_t* p_min_ssr_tout) {
  tBTA_HH_DEV_CB* p_cb = bta_hh_get_cb(link_spec);
  if (p_cb == nullptr) {
    log::warn("Unable to find device:{}", ADDRESS_TO_LOGGABLE_CSTR(link_spec));
    return BTA_HH_ERR;
  }

  /* if remote device does not have HIDSSRHostMaxLatency attribute in SDP,
     set SSR max latency default value here.  */
  if (p_cb->dscp_info.ssr_max_latency == HID_SSR_PARAM_INVALID) {
    /* The default is calculated as half of link supervision timeout.*/

    uint16_t ssr_max_latency;
    if (get_btm_client_interface().link_controller.BTM_GetLinkSuperTout(
            p_cb->link_spec.addrt.bda, &ssr_max_latency) != BTM_SUCCESS) {
      log::warn("Unable to get supervision timeout for peer:{}",
                ADDRESS_TO_LOGGABLE_CSTR(p_cb->link_spec));
      return BTA_HH_ERR;
    }
    ssr_max_latency = BTA_HH_GET_DEF_SSR_MAX_LAT(ssr_max_latency);

    /* per 1.1 spec, if the newly calculated max latency is greater than
       BTA_HH_SSR_MAX_LATENCY_DEF which is 500ms, use
       BTA_HH_SSR_MAX_LATENCY_DEF */
    if (ssr_max_latency > BTA_HH_SSR_MAX_LATENCY_DEF)
      ssr_max_latency = BTA_HH_SSR_MAX_LATENCY_DEF;

    char remote_name[BTM_MAX_REM_BD_NAME_LEN] = "";
    if (btif_storage_get_stored_remote_name(link_spec.addrt.bda, remote_name)) {
      if (interop_match_name(INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL,
                             remote_name)) {
        if (ssr_max_latency > kSsrMaxLatency /* slots * 0.625ms */) {
          ssr_max_latency = kSsrMaxLatency;
        }
      }
    }

    *p_max_ssr_lat = ssr_max_latency;
  } else
    *p_max_ssr_lat = p_cb->dscp_info.ssr_max_latency;

  if (p_cb->dscp_info.ssr_min_tout == HID_SSR_PARAM_INVALID)
    *p_min_ssr_tout = BTA_HH_SSR_MIN_TOUT_DEF;
  else
    *p_min_ssr_tout = p_cb->dscp_info.ssr_min_tout;

  return BTA_HH_OK;
}

/*******************************************************************************
 *
 * Function         bta_hh_cleanup_disable
 *
 * Description      when disable finished, cleanup control block and send
 *                  callback
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_cleanup_disable(tBTA_HH_STATUS status) {
  uint8_t xx;
  /* free buffer in CB holding report descriptors */
  for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
    osi_free_and_reset(
        (void**)&bta_hh_cb.kdev[xx].dscp_info.descriptor.dsc_list);
  }

  if (bta_hh_cb.p_disc_db) {
    /* Cancel SDP if it had been started. */
    (void)get_legacy_stack_sdp_api()->service.SDP_CancelServiceSearch(
        bta_hh_cb.p_disc_db);
    osi_free_and_reset((void**)&bta_hh_cb.p_disc_db);
  }

  if (bta_hh_cb.p_cback) {
    tBTA_HH bta_hh;
    bta_hh.status = status;
    (*bta_hh_cb.p_cback)(BTA_HH_DISABLE_EVT, &bta_hh);
    /* all connections are down, no waiting for diconnect */
    memset(&bta_hh_cb, 0, sizeof(tBTA_HH_CB));
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_dev_handle_to_cb_idx
 *
 * Description      convert a HID device handle to the device control block
 *                  index.
 *
 *
 * Returns          uint8_t: index of the device control block.
 *
 ******************************************************************************/
uint8_t bta_hh_dev_handle_to_cb_idx(uint8_t dev_handle) {
  uint8_t index = BTA_HH_IDX_INVALID;

  if (BTA_HH_IS_LE_DEV_HDL(dev_handle)) {
    if (BTA_HH_IS_LE_DEV_HDL_VALID(dev_handle))
      index = bta_hh_cb.le_cb_index[BTA_HH_GET_LE_CB_IDX(dev_handle)];
#if (BTA_HH_DEBUG == TRUE)
    log::verbose("dev_handle={} index={}", dev_handle, index);
#endif
  } else
      /* regular HID device checking */
      if (dev_handle < BTA_HH_MAX_KNOWN)
    index = bta_hh_cb.cb_index[dev_handle];

  return index;
}
#if (BTA_HH_DEBUG == TRUE)
/*******************************************************************************
 *
 * Function         bta_hh_trace_dev_db
 *
 * Description      Check to see if this type of device is supported
 *
 * Returns
 *
 ******************************************************************************/
void bta_hh_trace_dev_db(void) {
  uint8_t xx;

  log::verbose("bta_hh_trace_dev_db:: Device DB list********************");

  for (xx = 0; xx < BTA_HH_MAX_DEVICE; xx++) {
    log::verbose("kdev[{}] in_use[{}]  handle[{}]", xx,
                 bta_hh_cb.kdev[xx].in_use, bta_hh_cb.kdev[xx].hid_handle);

    log::verbose(
        "\t\t\t attr_mask[{:04x}] state [{}] sub_class[{:02x}] index = {}",
        bta_hh_cb.kdev[xx].attr_mask, bta_hh_cb.kdev[xx].state,
        bta_hh_cb.kdev[xx].sub_class, bta_hh_cb.kdev[xx].index);
  }
  log::verbose("*********************************************************");
}
#endif
