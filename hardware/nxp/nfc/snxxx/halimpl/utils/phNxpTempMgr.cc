/*
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2023 NXP
 **
 */
#include "phNxpTempMgr.h"

#include <phNxpConfig.h>
#include <phNxpLog.h>
#include <phOsalNfc_Timer.h>
#include <unistd.h>

#include <mutex>

#define PH_NFC_TIMER_ID_INVALID (0xFFFF)
#define PH_NXP_TEMPMGR_TOTAL_DELAY (11)

static void tempNTf_timeout_cb(uint32_t TimerId, void* pContext) {
  (void)TimerId;
  NXPLOG_NCIHAL_D("phNxpTempMgr::tempNTf_timeout_cb timedout");
  phNxpTempMgr* tMgr = (phNxpTempMgr*)pContext;
  tMgr->Reset(false /*reset timer*/);
}

phNxpTempMgr::phNxpTempMgr() {
  timeout_timer_id_ = PH_NFC_TIMER_ID_INVALID;
  is_ic_temp_ok_ = true;
  total_delay_ms_ = PH_NXP_TEMPMGR_TOTAL_DELAY * 1000;  // 11 sec
}

phNxpTempMgr& phNxpTempMgr::GetInstance() {
  static phNxpTempMgr nxpTempmgr;
  return nxpTempmgr;
}

void phNxpTempMgr::UpdateTempStatusLocked(bool temp_status) {
  std::lock_guard<std::mutex> lock(ic_temp_mutex_);
  is_ic_temp_ok_ = temp_status;
}
void phNxpTempMgr::UpdateICTempStatus(uint8_t* p_ntf, uint16_t p_len) {
  (void)p_len;
  NFCSTATUS status = NFCSTATUS_FAILED;
  bool temp_status = p_ntf[03] == 0x00 ? true : false;
  UpdateTempStatusLocked(temp_status);
  NXPLOG_NCIHAL_D("phNxpTempMgr: IC temp state is %d", IsICTempOk());
  // Temperature status will be notified for only one module at a time.
  // If temp NOK status was notified for eSE first, next temp status
  // NTF (OK/NOK) will also be notified for the same module i.e. eSE in this
  // case
  if (!IsICTempOk()) {
    if (timeout_timer_id_ == PH_NFC_TIMER_ID_INVALID)
      timeout_timer_id_ = phOsalNfc_Timer_Create();
    if (timeout_timer_id_ != PH_NFC_TIMER_ID_INVALID) {
      /* Start timer */
      status = phOsalNfc_Timer_Start(timeout_timer_id_, total_delay_ms_,
                                     &tempNTf_timeout_cb, this);
      if (NFCSTATUS_SUCCESS != status) {
        NXPLOG_NCIHAL_D("tempNtf timer not started!!!");
      }
    } else {
      NXPLOG_NCIHAL_E("tempNtf timer creation failed");
    }
  } else {
    if (timeout_timer_id_ != PH_NFC_TIMER_ID_INVALID) {
      /* Stop Timer */
      status = phOsalNfc_Timer_Stop(timeout_timer_id_);
      if (NFCSTATUS_SUCCESS != status) {
        NXPLOG_NCIHAL_D("tempNtf timer stop ERROR!!!");
      }
    }
  }
}

void phNxpTempMgr::Wait() {
  if (!IsICTempOk()) {
    NXPLOG_NCIHAL_D("Wait for %d seconds", total_delay_ms_ / 1000);
    uint16_t delay_per_try = 500;  // millisec
    while (!IsICTempOk()) {
      usleep(delay_per_try * 1000);  // 500 millisec
    }
  }
}
void phNxpTempMgr::Reset(bool reset_timer) {
  NXPLOG_NCIHAL_D("phNxpTempMgr::Reset ");
  UpdateTempStatusLocked(true /*Temp OK*/);
  if (reset_timer) {
    timeout_timer_id_ = PH_NFC_TIMER_ID_INVALID;
  }
}
