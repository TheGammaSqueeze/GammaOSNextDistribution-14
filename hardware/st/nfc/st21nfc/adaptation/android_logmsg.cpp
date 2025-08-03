/******************************************************************************
 *
 * Copyright (C) 1999-2012 Broadcom Corporation
 * Copyright (C) 2016 ST Microelectronics S.A.
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
#include "android_logmsg.h"
#include <pthread.h>
#include <stdio.h>

void DispHal(const char* title, const void* data, size_t length);
unsigned char hal_trace_level = STNFC_TRACE_LEVEL_DEBUG;
unsigned char hal_conf_trace_level = STNFC_TRACE_LEVEL_DEBUG;
uint16_t hal_log_cnt = 0;
pthread_mutex_t halLogMutex;

/*******************************************************************************
**
** Function:        InitializeGlobalAppLogLevel
**
** Description:     Initialize and get global logging level from
**                  Android property nfc.app_log_level.
**
** Returns:         Global log level:
**                  STNFC_TRACE_LEVEL_NONE    0     * No trace messages to be
*                                                     generated
**                  STNFC_TRACE_LEVEL_ERROR   1     * Error condition trace
*                                                     messages
**                  STNFC_TRACE_LEVEL_WARNING 2     * Warning condition trace
*                                                     messages
**                  STNFC_TRACE_LEVEL_DEBUG   3     * Debug messages (general)
**
**                  STNFC_TRACE_LEVEL_VERBOSE 4     * Verbose messages
**
*******************************************************************************/
unsigned char InitializeSTLogLevel() {
  unsigned long num = 0;
  int ret;

  num = 1;
  if (GetNumValue(NAME_STNFC_HAL_LOGLEVEL, &num, sizeof(num))) {
    hal_conf_trace_level = (unsigned char)num;
    hal_trace_level = hal_conf_trace_level;
  }

  STLOG_HAL_D("%s: HAL log level=%u, hal_log_cnt (before reset): #%04X",
              __func__, hal_trace_level, hal_log_cnt);

  hal_log_cnt = 0x00;

  ret = pthread_mutex_init(&halLogMutex, NULL);
  if (ret != 0) {
    STLOG_HAL_E("HAL: %s pthread_mutex_init failed", __func__);
  }

  return hal_trace_level;
}

void DispHal(const char* title, const void* data, size_t length) {
  uint8_t* d = (uint8_t*)data;
  char line[100];
  size_t i, k;
  bool first_line = true;
  bool privacy = false;
  uint16_t frame_nb;

  pthread_mutex_lock(&halLogMutex);
  frame_nb = hal_log_cnt;

  if (hal_log_cnt == 0xFFFF) {
    hal_log_cnt = 0;
  } else {
    hal_log_cnt++;
  }

  if (hal_trace_level & STNFC_TRACE_FLAG_PRIVACY) {
    if ((length > 3) &&
        // DATA message
        (((d[0] & 0xE0) == 0) ||
         // routing table contains the AIDs
         ((d[0] == 0x21) && (d[1] == 0x01)) ||
         // NTF showing which AID was selected
         ((d[0] == 0x61) && (d[1] == 0x09)))) {
      // We hide the payload for GSMA TS27 15.9.3.2.*
      privacy = true;
    }
  }

  line[0] = 0;
  if (length == 0) {
    STLOG_HAL_D("%s", title);
    pthread_mutex_unlock(&halLogMutex);
    return;
  }
  for (i = 0, k = 0; i < (privacy ? 3 : length); i++, k++) {
    if (k > 31) {
      k = 0;
      if (first_line == true) {
        first_line = false;
        if (title[0] == 'R') {
          STLOG_HAL_D("(#0%04X) Rx %s\n", frame_nb, line);
        } else if (title[0] == 'T') {
          STLOG_HAL_D("(#0%04X) Tx %s\n", frame_nb, line);
        } else {
          STLOG_HAL_D("%s\n", line);
        }
        pthread_mutex_unlock(&halLogMutex);
      } else {
        if (title[0] == 'R') {
          STLOG_HAL_D("(#0%04X) rx %s\n", frame_nb, line);
        } else if (title[0] == 'T') {
          STLOG_HAL_D("(#0%04X) tx %s\n", frame_nb, line);
        } else {
          STLOG_HAL_D("%s\n", line);
        }
      }
      if (k < 100) {
        line[k] = 0;
      }
    }
    snprintf(&line[k * 3], sizeof(line) - (k * 3), "%02x ", d[i]);
  }

  if (privacy) {
    snprintf(&line[k * 3], sizeof(line) - (k * 3), "(hidden)");
  }

  if (first_line == true) {
    if (title[0] == 'R') {
      STLOG_HAL_D("(#0%04X) Rx %s\n", frame_nb, line);
    } else if (title[0] == 'T') {
      STLOG_HAL_D("(#0%04X) Tx %s\n", frame_nb, line);
    } else {
      STLOG_HAL_D("%s\n", line);
    }
    pthread_mutex_unlock(&halLogMutex);
  } else {
    if (title[0] == 'R') {
      STLOG_HAL_D("(#0%04X) rx %s\n", frame_nb, line);
    } else if (title[0] == 'T') {
      STLOG_HAL_D("(#0%04X) tx %s\n", frame_nb, line);
    } else {
      STLOG_HAL_D("%s\n", line);
    }
  }
}

void deInitializeHalLog() { pthread_mutex_destroy(&halLogMutex); }
