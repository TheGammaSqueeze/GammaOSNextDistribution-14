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
#define LOG_TAG "StEse-logmsg"
#include "android_logmsg.h"
#include <ese_config.h>
#include <stdio.h>

void DispHal(const char* title, const void* data, size_t length);
unsigned char hal_trace_level = STESE_TRACE_LEVEL_DEBUG;

/*******************************************************************************
**
** Function:        InitializeGlobalAppLogLevel
**
** Description:     Initialize and get global logging level from
**                  Android property nfc.app_log_level.
**
** Returns:         Global log level:
**                  STESE_TRACE_LEVEL_NONE    0     * No trace messages to be
*                                                     generated
**                  STESE_TRACE_LEVEL_ERROR   1     * Error condition trace
*                                                     messages
**                  STESE_TRACE_LEVEL_WARNING 2     * Warning condition trace
*                                                     messages
**                  STESE_TRACE_LEVEL_DEBUG   3     * Debug messages (general)
**
**                  STESE_TRACE_LEVEL_VERBOSE 4     * Verbose messages
**
*******************************************************************************/
unsigned char InitializeSTLogLevel() {
  unsigned long num = 0;

  num = 1;
  num = EseConfig::getUnsigned(NAME_STESE_HAL_LOGLEVEL, 0);
  if (num >= 0) hal_trace_level = (unsigned char)num;

  STLOG_HAL_D("%s: level=%u", __func__, hal_trace_level);
  return hal_trace_level;
}

void DispHal(const char* title, const void* data, size_t length) {
  uint8_t* d = (uint8_t*)data;
  char line[100];
  size_t i, k;
  bool first_line = true;

  line[0] = 0;
  if (length == 0) {
    STLOG_HAL_D("%s", title);
    return;
  }
  for (i = 0, k = 0; i < length; i++, k++) {
    if (k > 31) {
      k = 0;
      if (first_line == true) {
        first_line = false;
        if (title[0] == 'R') {
          STLOG_HAL_D("spiRx %s\n", line);
        } else if (title[0] == 'T') {
          STLOG_HAL_D("spiTx %s\n", line);
        } else {
          STLOG_HAL_D("%s\n", line);
        }
      } else {
        STLOG_HAL_D("%s\n", line);
      }
      line[k] = 0;
    }
    sprintf(&line[k * 3], "%02x ", d[i]);
  }

  if (first_line == true) {
    if (title[0] == 'R') {
      STLOG_HAL_D("spiRx %s\n", line);
    } else if (title[0] == 'T') {
      STLOG_HAL_D("spiTx %s\n", line);
    } else {
      STLOG_HAL_D("%s\n", line);
    }
  } else {
    STLOG_HAL_D("%s\n", line);
  }
}
