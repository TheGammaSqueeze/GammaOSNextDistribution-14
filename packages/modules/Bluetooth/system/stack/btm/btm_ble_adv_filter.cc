/******************************************************************************
 *
 *  Copyright 2014  Broadcom Corporation
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

#define LOG_TAG "bt_btm_ble"

#include <base/functional/bind.h>
#include <bluetooth/log.h>

#include "btm_ble_api.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_types.h"
#include "stack/include/btu_hcif.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

extern tBTM_CB btm_cb;

using namespace bluetooth;
using base::Bind;
using bluetooth::Uuid;

#define BTM_BLE_ADV_FILT_META_HDR_LENGTH 3
#define BTM_BLE_ADV_FILT_FEAT_SELN_LEN 13
#define BTM_BLE_ADV_FILT_TRACK_NUM 2

#define BTM_BLE_PF_SELECT_NONE 0

/* BLE meta vsc header: 1 bytes of sub_code, 1 byte of PCF action */
#define BTM_BLE_META_HDR_LENGTH 3
#define BTM_BLE_PF_FEAT_SEL_LEN 18
#define BTM_BLE_PCF_ENABLE_LEN 2

#define BTM_BLE_META_ADDR_LEN 7
#define BTM_BLE_META_UUID_LEN 40

#define BTM_BLE_PF_BIT_TO_MASK(x) (uint16_t)(1 << (x))

tBTM_BLE_ADV_FILTER_CB btm_ble_adv_filt_cb;
tBTM_BLE_VSC_CB cmn_ble_vsc_cb;

static uint8_t btm_ble_cs_update_pf_counter(tBTM_BLE_SCAN_COND_OP action,
                                            uint8_t cond_type,
                                            tBLE_BD_ADDR* p_bd_addr,
                                            uint8_t num_available);

#define BTM_BLE_SET_SCAN_PF_OPCODE(x, y) (((x) << 4) | (y))
#define BTM_BLE_GET_SCAN_PF_SUBCODE(x) ((x) >> 4)
#define BTM_BLE_GET_SCAN_PF_ACTION(x) ((x)&0x0f)
#define BTM_BLE_INVALID_COUNTER 0xff

/* length of each multi adv sub command */
#define BTM_BLE_ADV_FILTER_ENB_LEN 3

/* length of each batch scan command */
#define BTM_BLE_ADV_FILTER_CLEAR_LEN 3
#define BTM_BLE_ADV_FILTER_LEN 2

#define BTM_BLE_ADV_FILT_CB_EVT_MASK 0xF0
#define BTM_BLE_ADV_FILT_SUBCODE_MASK 0x0F

static bool is_filtering_supported() {
  return cmn_ble_vsc_cb.filter_support != 0 && cmn_ble_vsc_cb.max_filter != 0;
}

/*******************************************************************************
 *
 * Function         btm_ble_ocf_to_condtype
 *
 * Description      Convert OCF to cond type
 *
 * Returns          Returns condtype value
 *
 ******************************************************************************/
static uint8_t btm_ble_ocf_to_condtype(uint8_t ocf) {
  uint8_t cond_type = 0;

  switch (ocf) {
    case BTM_BLE_META_PF_FEAT_SEL:
      cond_type = BTM_BLE_META_PF_FEAT_SEL;
      break;
    case BTM_BLE_META_PF_ADDR:
      cond_type = BTM_BLE_PF_ADDR_FILTER;
      break;
    case BTM_BLE_META_PF_UUID:
      cond_type = BTM_BLE_PF_SRVC_UUID;
      break;
    case BTM_BLE_META_PF_SOL_UUID:
      cond_type = BTM_BLE_PF_SRVC_SOL_UUID;
      break;
    case BTM_BLE_META_PF_LOCAL_NAME:
      cond_type = BTM_BLE_PF_LOCAL_NAME;
      break;
    case BTM_BLE_META_PF_MANU_DATA:
      cond_type = BTM_BLE_PF_MANU_DATA;
      break;
    case BTM_BLE_META_PF_SRVC_DATA:
      cond_type = BTM_BLE_PF_SRVC_DATA_PATTERN;
      break;
    case BTM_BLE_META_PF_ALL:
      cond_type = BTM_BLE_PF_TYPE_ALL;
      break;
    default:
      cond_type = BTM_BLE_PF_TYPE_MAX;
      break;
  }
  return cond_type;
}

static void btm_flt_update_cb(uint8_t expected_ocf, tBTM_BLE_PF_CFG_CBACK cb,
                              uint8_t* p, uint16_t evt_len) {
  if (evt_len != 4) {
    log::error("bad length: {}", evt_len);
    return;
  }

  uint8_t status, op_subcode, action, num_avail;
  STREAM_TO_UINT8(status, p);
  STREAM_TO_UINT8(op_subcode, p);
  STREAM_TO_UINT8(action, p);
  STREAM_TO_UINT8(num_avail, p);

  if (expected_ocf != op_subcode) {
    log::error("Incorrect opcode: 0x{:02x}, expected: 0x{:02x}", expected_ocf,
               op_subcode);
    return;
  }

  tBTM_STATUS btm_status = (status == 0) ? BTM_SUCCESS : BTM_ERR_PROCESSING;

  if (op_subcode == BTM_BLE_META_PF_FEAT_SEL) {
    cb.Run(num_avail, static_cast<tBTM_BLE_SCAN_COND_OP>(action), btm_status);
    return;
  }

  uint8_t cond_type = btm_ble_ocf_to_condtype(expected_ocf);
  log::verbose("Recd: {}, {}, {}, {}, {}", op_subcode, expected_ocf, action,
               status, num_avail);
  if (HCI_SUCCESS == status) {
    if (btm_ble_adv_filt_cb.cur_filter_target.bda.IsEmpty())
      btm_ble_cs_update_pf_counter(static_cast<tBTM_BLE_SCAN_COND_OP>(action),
                                   cond_type, NULL, num_avail);
    else
      btm_ble_cs_update_pf_counter(
          static_cast<tBTM_BLE_SCAN_COND_OP>(action), cond_type,
          &btm_ble_adv_filt_cb.cur_filter_target, num_avail);
  }

  /* send ADV PF operation complete */
  btm_ble_adv_filt_cb.op_type = 0;

  cb.Run(num_avail, static_cast<tBTM_BLE_SCAN_COND_OP>(action), btm_status);
}

/*******************************************************************************
 *
 * Function         btm_ble_find_addr_filter_counter
 *
 * Description      find the per bd address ADV payload filter counter by
 *                  BD_ADDR.
 *
 * Returns          pointer to the counter if found; NULL otherwise.
 *
 ******************************************************************************/
static tBTM_BLE_PF_COUNT* btm_ble_find_addr_filter_counter(
    tBLE_BD_ADDR* p_le_bda) {
  uint8_t i;
  tBTM_BLE_PF_COUNT* p_addr_filter =
      &btm_ble_adv_filt_cb.p_addr_filter_count[1];

  if (p_le_bda == NULL) return &btm_ble_adv_filt_cb.p_addr_filter_count[0];

  for (i = 0; i < cmn_ble_vsc_cb.max_filter; i++, p_addr_filter++) {
    if (p_addr_filter->in_use && p_le_bda->bda == p_addr_filter->bd_addr) {
      return p_addr_filter;
    }
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function         btm_ble_alloc_addr_filter_counter
 *
 * Description      allocate the per device adv payload filter counter.
 *
 * Returns          pointer to the counter if allocation succeed; NULL
 *                  otherwise.
 *
 ******************************************************************************/
static tBTM_BLE_PF_COUNT* btm_ble_alloc_addr_filter_counter(
    const RawAddress& bd_addr) {
  uint8_t i;
  tBTM_BLE_PF_COUNT* p_addr_filter =
      &btm_ble_adv_filt_cb.p_addr_filter_count[1];

  for (i = 0; i < cmn_ble_vsc_cb.max_filter; i++, p_addr_filter++) {
    if (p_addr_filter->bd_addr.IsEmpty()) {
      p_addr_filter->bd_addr = bd_addr;
      p_addr_filter->in_use = true;
      return p_addr_filter;
    }
  }
  return NULL;
}
/*******************************************************************************
 *
 * Function         btm_ble_dealloc_addr_filter_counter
 *
 * Description      de-allocate the per device adv payload filter counter.
 *
 * Returns          true if deallocation succeed; false otherwise.
 *
 ******************************************************************************/
static bool btm_ble_dealloc_addr_filter_counter(tBLE_BD_ADDR* p_bd_addr,
                                                uint8_t filter_type) {
  uint8_t i;
  tBTM_BLE_PF_COUNT* p_addr_filter =
      &btm_ble_adv_filt_cb.p_addr_filter_count[1];
  bool found = false;

  if (BTM_BLE_PF_TYPE_ALL == filter_type && NULL == p_bd_addr)
    memset(&btm_ble_adv_filt_cb.p_addr_filter_count[0], 0,
           sizeof(tBTM_BLE_PF_COUNT));

  for (i = 0; i < cmn_ble_vsc_cb.max_filter; i++, p_addr_filter++) {
    if (p_addr_filter->in_use &&
        (!p_bd_addr || p_bd_addr->bda == p_addr_filter->bd_addr)) {
      found = true;
      memset(p_addr_filter, 0, sizeof(tBTM_BLE_PF_COUNT));

      if (p_bd_addr) break;
    }
  }
  return found;
}

/*******************************************************************************
 *
 * Function         btm_ble_cs_update_pf_counter
 *
 * Description      this function is to update the adv data payload filter
 *                  counter
 *
 * Returns          current number of the counter; BTM_BLE_INVALID_COUNTER if
 *                  counter update failed.
 *
 ******************************************************************************/
static uint8_t btm_ble_cs_update_pf_counter(tBTM_BLE_SCAN_COND_OP action,
                                            uint8_t cond_type,
                                            tBLE_BD_ADDR* p_bd_addr,
                                            uint8_t num_available) {
  tBTM_BLE_PF_COUNT* p_addr_filter = NULL;
  uint8_t* p_counter = NULL;

  if (cond_type > BTM_BLE_PF_TYPE_ALL) {
    log::error("unknown PF filter condition type {}", cond_type);
    return BTM_BLE_INVALID_COUNTER;
  }

  /* for these three types of filter, always generic */
  if (BTM_BLE_PF_ADDR_FILTER == cond_type ||
      BTM_BLE_PF_MANU_DATA == cond_type || BTM_BLE_PF_LOCAL_NAME == cond_type ||
      BTM_BLE_PF_SRVC_DATA_PATTERN == cond_type)
    p_bd_addr = NULL;

  if ((p_addr_filter = btm_ble_find_addr_filter_counter(p_bd_addr)) == NULL &&
      BTM_BLE_SCAN_COND_ADD == action) {
    p_addr_filter = btm_ble_alloc_addr_filter_counter(p_bd_addr->bda);
  }

  if (NULL != p_addr_filter) {
    /* all filter just cleared */
    if ((BTM_BLE_PF_TYPE_ALL == cond_type &&
         BTM_BLE_SCAN_COND_CLEAR == action) ||
        /* or bd address filter been deleted */
        (BTM_BLE_PF_ADDR_FILTER == cond_type &&
         (BTM_BLE_SCAN_COND_DELETE == action ||
          BTM_BLE_SCAN_COND_CLEAR == action))) {
      btm_ble_dealloc_addr_filter_counter(p_bd_addr, cond_type);
    }
    /* if not feature selection, update new addition/reduction of the filter
       counter */
    else if (cond_type != BTM_BLE_PF_TYPE_ALL) {
      p_counter = p_addr_filter->pf_counter;
      if (num_available > 0) p_counter[cond_type] += 1;

      log::verbose("counter = {}, maxfilt = {}, num_avbl={}",
                   p_counter[cond_type], cmn_ble_vsc_cb.max_filter,
                   num_available);
      return p_counter[cond_type];
    }
  } else {
    log::error("no matching filter counter found");
  }
  /* no matching filter located and updated */
  return BTM_BLE_INVALID_COUNTER;
}

/*******************************************************************************
 *
 * Function         BTM_BleAdvFilterParamSetup
 *
 * Description      This function is called to setup the adv data payload filter
 *                  condition.
 *
 * Parameters       action - Type of action to be performed
 *                       filt_index - Filter index
 *                       p_filt_params - Filter parameters
 *                       cb - Callback
 *
 ******************************************************************************/
void BTM_BleAdvFilterParamSetup(
    tBTM_BLE_SCAN_COND_OP action, tBTM_BLE_PF_FILT_INDEX filt_index,
    std::unique_ptr<btgatt_filt_param_setup_t> p_filt_params,
    tBTM_BLE_PF_PARAM_CB cb) {
  tBTM_BLE_PF_COUNT* p_bda_filter = NULL;
  uint8_t len = BTM_BLE_ADV_FILT_META_HDR_LENGTH +
                BTM_BLE_ADV_FILT_FEAT_SELN_LEN + BTM_BLE_ADV_FILT_TRACK_NUM;
  uint8_t param[len], *p;

  if (!is_filtering_supported()) {
    cb.Run(0, BTM_BLE_PF_ENABLE, btm_status_value(BTM_MODE_UNSUPPORTED));
    return;
  }

  p = param;
  memset(param, 0, len);
  log::verbose("");

  if (BTM_BLE_SCAN_COND_ADD == action) {
    p_bda_filter = btm_ble_find_addr_filter_counter(nullptr);
    if (NULL == p_bda_filter) {
      log::error("BD Address not found!");
      cb.Run(0, BTM_BLE_PF_ENABLE, btm_status_value(BTM_UNKNOWN_ADDR));
      return;
    }

    log::verbose("Feat mask:{}", p_filt_params->feat_seln);
    /* select feature based on control block settings */
    UINT8_TO_STREAM(p, BTM_BLE_META_PF_FEAT_SEL);
    UINT8_TO_STREAM(p, BTM_BLE_SCAN_COND_ADD);

    /* Filter index */
    UINT8_TO_STREAM(p, filt_index);

    /* set PCF selection */
    UINT16_TO_STREAM(p, p_filt_params->feat_seln);
    /* set logic type */
    UINT16_TO_STREAM(p, p_filt_params->list_logic_type);
    /* set logic condition */
    UINT8_TO_STREAM(p, p_filt_params->filt_logic_type);
    /* set RSSI high threshold */
    UINT8_TO_STREAM(p, p_filt_params->rssi_high_thres);
    /* set delivery mode */
    UINT8_TO_STREAM(p, p_filt_params->dely_mode);

    if (0x01 == p_filt_params->dely_mode) {
      /* set onfound timeout */
      UINT16_TO_STREAM(p, p_filt_params->found_timeout);
      /* set onfound timeout count*/
      UINT8_TO_STREAM(p, p_filt_params->found_timeout_cnt);
      /* set RSSI low threshold */
      UINT8_TO_STREAM(p, p_filt_params->rssi_low_thres);
      /* set onlost timeout */
      UINT16_TO_STREAM(p, p_filt_params->lost_timeout);
      /* set num_of_track_entries for firmware greater than L-release version */
      if (cmn_ble_vsc_cb.version_supported > BTM_VSC_CHIP_CAPABILITY_L_VERSION)
        UINT16_TO_STREAM(p, p_filt_params->num_of_tracking_entries);
    }

    if (cmn_ble_vsc_cb.version_supported == BTM_VSC_CHIP_CAPABILITY_L_VERSION)
      len = BTM_BLE_ADV_FILT_META_HDR_LENGTH + BTM_BLE_ADV_FILT_FEAT_SELN_LEN;
    else
      len = BTM_BLE_ADV_FILT_META_HDR_LENGTH + BTM_BLE_ADV_FILT_FEAT_SELN_LEN +
            BTM_BLE_ADV_FILT_TRACK_NUM;

    btu_hcif_send_cmd_with_cb(
        FROM_HERE, HCI_BLE_ADV_FILTER, param, len,
        base::Bind(&btm_flt_update_cb, BTM_BLE_META_PF_FEAT_SEL, cb));
  } else if (BTM_BLE_SCAN_COND_DELETE == action) {
    /* select feature based on control block settings */
    UINT8_TO_STREAM(p, BTM_BLE_META_PF_FEAT_SEL);
    UINT8_TO_STREAM(p, BTM_BLE_SCAN_COND_DELETE);
    /* Filter index */
    UINT8_TO_STREAM(p, filt_index);

    btu_hcif_send_cmd_with_cb(
        FROM_HERE, HCI_BLE_ADV_FILTER, param,
        (uint8_t)(BTM_BLE_ADV_FILT_META_HDR_LENGTH),
        base::Bind(&btm_flt_update_cb, BTM_BLE_META_PF_FEAT_SEL, cb));

  } else if (BTM_BLE_SCAN_COND_CLEAR == action) {
    /* Deallocate all filters here */
    btm_ble_dealloc_addr_filter_counter(NULL, BTM_BLE_PF_TYPE_ALL);

    /* select feature based on control block settings */
    UINT8_TO_STREAM(p, BTM_BLE_META_PF_FEAT_SEL);
    UINT8_TO_STREAM(p, BTM_BLE_SCAN_COND_CLEAR);

    btu_hcif_send_cmd_with_cb(
        FROM_HERE, HCI_BLE_ADV_FILTER, param,
        (uint8_t)(BTM_BLE_ADV_FILT_META_HDR_LENGTH - 1),
        base::Bind(&btm_flt_update_cb, BTM_BLE_META_PF_FEAT_SEL, cb));
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_adv_filter_init
 *
 * Description      This function initializes the adv filter control block
 *
 * Parameters
 *
 * Returns          status
 *
 ******************************************************************************/
void btm_ble_adv_filter_init(void) {
  memset(&btm_ble_adv_filt_cb, 0, sizeof(tBTM_BLE_ADV_FILTER_CB));

  BTM_BleGetVendorCapabilities(&cmn_ble_vsc_cb);

  if (!is_filtering_supported()) return;

  if (cmn_ble_vsc_cb.max_filter > 0) {
    btm_ble_adv_filt_cb.p_addr_filter_count = (tBTM_BLE_PF_COUNT*)osi_malloc(
        sizeof(tBTM_BLE_PF_COUNT) * cmn_ble_vsc_cb.max_filter);
  }
}
