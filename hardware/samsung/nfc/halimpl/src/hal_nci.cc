/*
 *    Copyright (C) 2013 SAMSUNG S.LSI
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 */

#include <hardware/nfc.h>
#include <string.h>

#include "device.h"
#include "hal.h"
#include "hal_msg.h"
#include "osi.h"
#include "util.h"

int hal_nci_send(tNFC_NCI_PKT* pkt) {
  size_t len = (size_t)(pkt->len + NCI_HDR_SIZE);
  int ret;

  ret = __send_to_device((uint8_t*)pkt, len);
  if (ret != (int)len
      /* workaround for retry; F/W I2C issue */
      && (nfc_hal_info.flag & HAL_FLAG_NTF_TRNS_ERROR)) {
    OSI_loge("NCI message send failed");
    OSI_logd("set flag to 0x%06X", nfc_hal_info.flag);
  } else {
    util_nci_analyzer(pkt);
  }

  return ret;
}

void hal_nci_send_reset(void) {
  tNFC_NCI_PKT nci_pkt;

  memset(&nci_pkt, 0, sizeof(tNFC_NCI_PKT));
  nci_pkt.oct0 = NCI_MT_CMD | NCI_PBF_LAST | NCI_GID_CORE;
  nci_pkt.oid = NCI_CORE_RESET;
  nci_pkt.len = 0x01;
  nci_pkt.payload[0] = 0x01;  // Reset config

  hal_nci_send(&nci_pkt);
}

/* START [181106] Patch for supporting NCI v2.0 */
// [3. CORE_INIT Changes]
void hal_nci_send_init(int version) {
  /* END [181106] Patch for supporting NCI v2.0 */
  tNFC_NCI_PKT nci_pkt;

  /* START [181106] Patch for supporting NCI v2.0 */
  //[3. CORE_INIT Changes]
  memset(&nci_pkt, 0, sizeof(tNFC_NCI_PKT));
  nci_pkt.oct0 = NCI_MT_CMD | NCI_PBF_LAST | NCI_GID_CORE;
  nci_pkt.oid = NCI_CORE_INIT;
  nci_pkt.len = 0x00;

  if (version == NCI_VER_2_0) {
    nci_pkt.len = 0x02;
    nci_pkt.payload[0] = 0x00;
    nci_pkt.payload[1] = 0x00;
  }
  /* END [181106] Patch for supporting NCI v2.0 */

  hal_nci_send(&nci_pkt);
}

/* Workaround: Initialization flash of LMRT */
void hal_nci_send_clearLmrt(void) {
  tNFC_NCI_PKT nci_pkt;

  memset(&nci_pkt, 0, sizeof(tNFC_NCI_PKT));
  nci_pkt.oct0 = NCI_MT_CMD | NCI_PBF_LAST | NCI_GID_RF_MANAGE;
  nci_pkt.oid = 0x01;  // RF_SET_LMRT
  nci_pkt.len = 0x02;
  nci_pkt.payload[0] = 0x00;
  nci_pkt.payload[1] = 0x00;

  hal_nci_send(&nci_pkt);
}
/* END WA */

void get_clock_info(int rev, int field_name, int* buffer) {
  char rev_field[50] = {
      '\0',
  };
  int isRevField = 0;

  sprintf(rev_field, "%s_REV%d", cfg_name_table[field_name], rev);
  isRevField = get_config_count(rev_field);
  if (rev >= 0 && isRevField) {
    if (!get_config_int(rev_field, buffer)) *buffer = 0;
  } else if (!get_config_int(cfg_name_table[field_name], buffer))
    *buffer = 0;
}

void hal_nci_send_prop_fw_cfg(void) {
  tNFC_NCI_PKT nci_pkt;
  int rev = get_hw_rev();

  memset(&nci_pkt, 0, sizeof(tNFC_NCI_PKT));
  nci_pkt.oct0 = NCI_MT_CMD | NCI_PBF_LAST | NCI_GID_PROP;
  nci_pkt.oid = NCI_PROP_FW_CFG;

  nci_pkt.len = 0x01;
  get_clock_info(rev, CFG_FW_CLK_SPEED, (int*)&nci_pkt.payload[0]);
  if (nci_pkt.payload[0] == 0xff) {
    OSI_loge("Set a different value! Current Clock Speed Value : 0x%x",
             nci_pkt.payload[0]);
    return;
  }
  hal_nci_send(&nci_pkt);
}

int nci_read_payload(tNFC_HAL_MSG* msg) {
  tNFC_NCI_PKT* pkt = &msg->nci_packet;
  int ret;

  ret = device_read(NCI_PAYLOAD(pkt), NCI_LEN(pkt));
  if (ret != (int)NCI_LEN(pkt)) {
    OSI_mem_free((tOSI_MEM_HANDLER)msg);
    OSI_loge("Failed to read payload");
    return ret;
  }

  data_trace("Recv", NCI_HDR_SIZE + ret, msg->param);
  return ret;
}

void fw_force_update(__attribute__((unused)) void* param) {
  OSI_loge("need to F/W update!");
}

void nci_init_timeout(__attribute__((unused)) void* param) {
  OSI_loge("need to retry!");
}

bool nfc_hal_prehandler(tNFC_NCI_PKT* pkt) {
  if (NCI_MT(pkt) == NCI_MT_NTF) {
    if (NCI_GID(pkt) == NCI_GID_PROP) {
      /* Again procedure. only for N3 isN3group */
      if (NCI_OID(pkt) == NCI_PROP_AGAIN) {
        if (nfc_hal_info.nci_last_pkt) {
          OSI_logd("NFC requests sending last message again!");
          hal_update_sleep_timer();
          device_write((uint8_t*)nfc_hal_info.nci_last_pkt,
                       (size_t)(nfc_hal_info.nci_last_pkt->len + NCI_HDR_SIZE));
          return false;
        }
      }
    }
  }

  if (NCI_MT(pkt) == NCI_MT_CMD) {
    if (NCI_GID(pkt) == NCI_GID_PROP) {
      if (NCI_OID(pkt) == NCI_PROP_WR_RESET) {
        hal_nci_send_reset();
        nfc_hal_info.flag |= HAL_FLAG_PROP_RESET;
        return false;
      }

      if (NCI_OID(pkt) == NCI_PROP_SET_SLEEP_TIME) {
        tNFC_NCI_PKT dummy_rsp;
        dummy_rsp.oct0 = NCI_MT_RSP | NCI_PBF_LAST | NCI_GID_PROP;
        dummy_rsp.oid = NCI_OID(pkt);
        dummy_rsp.len = 1;
        dummy_rsp.payload[0] = NCI_STATUS_OK;

        if (NCI_LEN(pkt) == 0) {
          setSleepTimeout(SET_SLEEP_TIME_CFG, 5000);
        } else {
          uint32_t timeout = NCI_PAYLOAD(pkt)[0] * 1000;  // sec
          int option = SET_SLEEP_TIME_ONCE;

          if (NCI_LEN(pkt) > 1)
            timeout += NCI_PAYLOAD(pkt)[1] * 1000 * 60;  // min

          if (NCI_LEN(pkt) > 2) option = NCI_PAYLOAD(pkt)[2];

          setSleepTimeout(option, timeout);
        }

        hal_update_sleep_timer();
        nfc_data_callback(&dummy_rsp);
        return false;
      }
    }
  }

  if (NCI_MT(pkt) == NCI_MT_RSP) {
    if (NCI_GID(pkt) == NCI_GID_CORE) {
      if (NCI_OID(pkt) == NCI_CORE_RESET) {
        pkt->oct0 = NCI_MT_RSP | NCI_PBF_LAST | NCI_GID_PROP;
        pkt->oid = NCI_PROP_WR_RESET;
      }
    }
  }
  return true;
}
