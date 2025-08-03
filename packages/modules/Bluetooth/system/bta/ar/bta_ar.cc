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
 *  This is the implementation for the audio/video registration module.
 *
 ******************************************************************************/

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/ar/bta_ar_int.h"
#include "bta/sys/bta_sys.h"
#include "stack/include/avct_api.h"
#include "stack/include/avrc_api.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdp_api.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

/* AV control block */
tBTA_AR_CB bta_ar_cb;

/*******************************************************************************
 *
 * Function         bta_ar_id
 *
 * Description      This function maps sys_id to ar id mask.
 *
 * Returns          void
 *
 ******************************************************************************/
static uint8_t bta_ar_id(tBTA_SYS_ID sys_id) {
  uint8_t mask = 0;
  if (sys_id == BTA_ID_AV) {
    mask = BTA_AR_AV_MASK;
  } else if (sys_id == BTA_ID_AVK) {
    mask = BTA_AR_AVK_MASK;
  }
  return mask;
}
static void bta_ar_avrc_add_cat(uint16_t categories) {
  uint8_t temp[sizeof(uint16_t)], *p;
  /* Change supported categories on the second one */
  if (bta_ar_cb.sdp_tg_handle != 0) {
    p = temp;
    UINT16_TO_BE_STREAM(p, categories);
    get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        bta_ar_cb.sdp_tg_handle, ATTR_ID_SUPPORTED_FEATURES, UINT_DESC_TYPE,
        sizeof(temp), (uint8_t*)temp);
  }
}

/*******************************************************************************
 *
 * Function         bta_ar_init
 *
 * Description      This function is called to register to AVDTP.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ar_init(void) {
  /* initialize control block */
  memset(&bta_ar_cb, 0, sizeof(tBTA_AR_CB));
}

/*******************************************************************************
 *
 * Function         bta_ar_reg_avdt
 *
 * Description      This function is called to register to AVDTP.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_ar_avdt_cback(uint8_t handle, const RawAddress& bd_addr,
                              uint8_t event, tAVDT_CTRL* p_data,
                              uint8_t scb_index) {
  /* route the AVDT registration callback to av or avk */
  if (bta_ar_cb.p_av_conn_cback)
    (*bta_ar_cb.p_av_conn_cback)(handle, bd_addr, event, p_data, scb_index);
}

/*******************************************************************************
 *
 * Function         bta_ar_reg_avdt
 *
 * Description      AR module registration to AVDT.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ar_reg_avdt(AvdtpRcb* p_reg, tAVDT_CTRL_CBACK* p_cback) {
  bta_ar_cb.p_av_conn_cback = p_cback;
  if (bta_ar_cb.avdt_registered == 0) {
    AVDT_Register(p_reg, bta_ar_avdt_cback);
  } else {
    log::warn("doesn't register again (registered:{})",
              bta_ar_cb.avdt_registered);
  }
  bta_ar_cb.avdt_registered |= BTA_AR_AV_MASK;
}

/*******************************************************************************
 *
 * Function         bta_ar_dereg_avdt
 *
 * Description      This function is called to de-register from AVDTP.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ar_dereg_avdt() {
  bta_ar_cb.p_av_conn_cback = NULL;
  bta_ar_cb.avdt_registered &= ~BTA_AR_AV_MASK;

  if (bta_ar_cb.avdt_registered == 0) AVDT_Deregister();
}

/*******************************************************************************
 *
 * Function         bta_ar_avdt_conn
 *
 * Description      This function is called to let ar know that some AVDTP
 *                  profile is connected for this sys_id.
 *                  If the other sys modules started a timer for PENDING_EVT,
 *                  the timer can be stopped now.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ar_avdt_conn(tBTA_SYS_ID sys_id, const RawAddress& bd_addr,
                      uint8_t scb_index) {
}

/*******************************************************************************
 *
 * Function         bta_ar_reg_avct
 *
 * Description      This function is called to register to AVCTP.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ar_reg_avct() {
  if (bta_ar_cb.avct_registered == 0) {
    AVCT_Register();
  }
  bta_ar_cb.avct_registered |= BTA_AR_AV_MASK;
}

/*******************************************************************************
 *
 * Function         bta_ar_dereg_avct
 *
 * Description      This function is called to deregister from AVCTP.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ar_dereg_avct() {
  bta_ar_cb.avct_registered &= ~BTA_AR_AV_MASK;

  if (bta_ar_cb.avct_registered == 0) AVCT_Deregister();
}

/******************************************************************************
 *
 * Function         bta_ar_reg_avrc
 *
 * Description      This function is called to register an SDP record for AVRCP.
 *
 * Returns          void
 *
 *****************************************************************************/
void bta_ar_reg_avrc(uint16_t service_uuid, const char* service_name,
                     const char* provider_name, uint16_t categories,
                     bool browse_supported, uint16_t profile_version) {
  uint8_t mask = BTA_AR_AV_MASK;
  uint8_t temp[8], *p;

  if (!categories) return;

  if (service_uuid == UUID_SERVCLASS_AV_REM_CTRL_TARGET) {
    if (bta_ar_cb.sdp_tg_handle == 0) {
      bta_ar_cb.tg_registered = mask;
      bta_ar_cb.sdp_tg_handle =
          get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
      AVRC_AddRecord(service_uuid, service_name, provider_name, categories,
                     bta_ar_cb.sdp_tg_handle, browse_supported,
                     profile_version, 0);
      bta_sys_add_uuid(service_uuid);
    }
    /* only one TG is allowed (first-come, first-served).
     * If sdp_tg_handle is non-0, ignore this request */
  } else if ((service_uuid == UUID_SERVCLASS_AV_REMOTE_CONTROL) ||
             (service_uuid == UUID_SERVCLASS_AV_REM_CTRL_CONTROL)) {
    bta_ar_cb.ct_categories[mask - 1] = categories;
    categories = bta_ar_cb.ct_categories[0] | bta_ar_cb.ct_categories[1];
    if (bta_ar_cb.sdp_ct_handle == 0) {
      bta_ar_cb.sdp_ct_handle =
          get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
      AVRC_AddRecord(service_uuid, service_name, provider_name, categories,
                     bta_ar_cb.sdp_ct_handle, browse_supported,
                     profile_version, 0);
      bta_sys_add_uuid(service_uuid);
    } else {
      /* multiple CTs are allowed.
       * Change supported categories on the second one */
      p = temp;
      UINT16_TO_BE_STREAM(p, categories);
      get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
          bta_ar_cb.sdp_ct_handle, ATTR_ID_SUPPORTED_FEATURES, UINT_DESC_TYPE,
          (uint32_t)2, (uint8_t*)temp);
    }
  }
}

/******************************************************************************
 *
 * Function         bta_ar_dereg_avrc
 *
 * Description      This function is called to de-register/delete an SDP record
 *                  for AVRCP.
 *
 * Returns          void
 *
 *****************************************************************************/
void bta_ar_dereg_avrc(uint16_t service_uuid) {
  uint8_t mask = BTA_AR_AV_MASK;
  uint16_t categories = 0;
  uint8_t temp[8], *p;

  if (service_uuid == UUID_SERVCLASS_AV_REM_CTRL_TARGET) {
    if (bta_ar_cb.sdp_tg_handle && mask == bta_ar_cb.tg_registered) {
      bta_ar_cb.tg_registered = 0;
      get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
          bta_ar_cb.sdp_tg_handle);
      bta_ar_cb.sdp_tg_handle = 0;
      bta_sys_remove_uuid(service_uuid);
    }
  } else if (service_uuid == UUID_SERVCLASS_AV_REMOTE_CONTROL) {
    if (bta_ar_cb.sdp_ct_handle) {
      bta_ar_cb.ct_categories[mask - 1] = 0;
      categories = bta_ar_cb.ct_categories[0] | bta_ar_cb.ct_categories[1];
      if (!categories) {
        /* no CT is still registered - cleaup */
        get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
            bta_ar_cb.sdp_ct_handle);
        bta_ar_cb.sdp_ct_handle = 0;
        bta_sys_remove_uuid(service_uuid);
      } else {
        /* change supported categories to the remaning one */
        p = temp;
        UINT16_TO_BE_STREAM(p, categories);
        get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
            bta_ar_cb.sdp_ct_handle, ATTR_ID_SUPPORTED_FEATURES, UINT_DESC_TYPE,
            (uint32_t)2, (uint8_t*)temp);
      }
    }
  }
}

/******************************************************************************
 *
 * Function         bta_ar_reg_avrc_for_src_sink_coexist
 *
 * Description      This function is called to register an SDP record for AVRCP.
 *                  Add sys_id to distinguish src or sink role and add also save
 *tg_categories
 *
 * Returns          void
 *
 *****************************************************************************/
void bta_ar_reg_avrc_for_src_sink_coexist(
    uint16_t service_uuid, const char* service_name, const char* provider_name,
    uint16_t categories, tBTA_SYS_ID sys_id, bool browse_supported,
    uint16_t profile_version) {
  uint8_t mask = bta_ar_id(sys_id);
  uint8_t temp[8], *p;
  uint16_t class_list[2];
  uint16_t count = 1;
  if (!mask || !categories) return;
  if (service_uuid == UUID_SERVCLASS_AV_REM_CTRL_TARGET) {
    bta_ar_cb.tg_categories[mask - 1] = categories;
    categories = bta_ar_cb.tg_categories[0] | bta_ar_cb.tg_categories[1];
    if (bta_ar_cb.sdp_tg_handle == 0) {
      bta_ar_cb.tg_registered = mask;
      bta_ar_cb.sdp_tg_handle =
          get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
      AVRC_AddRecord(service_uuid, service_name, provider_name, categories,
                     bta_ar_cb.sdp_tg_handle, browse_supported, profile_version,
                     0);
      bta_sys_add_uuid(service_uuid);
    }
    /* Change supported categories on the second one */
    bta_ar_avrc_add_cat(categories);
    /* only one TG is allowed (first-come, first-served).
     * If sdp_tg_handle is non-0, ignore this request */
  } else if ((service_uuid == UUID_SERVCLASS_AV_REMOTE_CONTROL) ||
             (service_uuid == UUID_SERVCLASS_AV_REM_CTRL_CONTROL)) {
    bta_ar_cb.ct_categories[mask - 1] = categories;
    categories = bta_ar_cb.ct_categories[0] | bta_ar_cb.ct_categories[1];
    if (bta_ar_cb.sdp_ct_handle == 0) {
      bta_ar_cb.sdp_ct_handle =
          get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
      AVRC_AddRecord(service_uuid, service_name, provider_name, categories,
                     bta_ar_cb.sdp_ct_handle, browse_supported, profile_version,
                     0);
      bta_sys_add_uuid(service_uuid);
      bta_ar_cb.ct_ver = categories;
    } else {
      /* If first reg 1,3 version, reg 1.6 must update class id */
      if (bta_ar_cb.ct_ver < profile_version) {
        log::verbose("ver=0x{:x}", profile_version);
        if (bta_ar_cb.ct_ver <= AVRC_REV_1_3 &&
            profile_version > AVRC_REV_1_3) {
          bta_ar_cb.ct_ver = profile_version;
          /* add service class id list */
          class_list[0] = service_uuid;
          if (service_uuid == UUID_SERVCLASS_AV_REMOTE_CONTROL) {
            class_list[1] = UUID_SERVCLASS_AV_REM_CTRL_CONTROL;
            count = 2;
          }
          get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
              bta_ar_cb.sdp_ct_handle, count, class_list);
        } else {
          bta_ar_cb.ct_ver = profile_version;
        }
        get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
            bta_ar_cb.sdp_ct_handle, service_uuid, profile_version);
      }
      /* multiple CT are allowed.
       * Change supported categories on the second one */
      p = temp;
      UINT16_TO_BE_STREAM(p, categories);
      get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
          bta_ar_cb.sdp_ct_handle, ATTR_ID_SUPPORTED_FEATURES, UINT_DESC_TYPE,
          (uint32_t)2, (uint8_t*)temp);
    }
  }
}
