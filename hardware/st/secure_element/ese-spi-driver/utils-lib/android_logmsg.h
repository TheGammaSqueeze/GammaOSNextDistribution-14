/******************************************************************************
 *
 *  Copyright (C) 2011-2012 Broadcom Corporation
 *  Copyright (C) 2017 ST Microelectronics S.A.
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
 * Decode NFC packets and print them to ADB log.
 * If protocol decoder is not present, then decode packets into hex numbers.
 ******************************************************************************/

extern "C" {

#include <cutils/properties.h>
#include <log/log.h>

#define DISP_NCI ProtoDispAdapterDisplayNciPacket

extern unsigned char hal_trace_level;
extern int GetNumValue(const char* name, void* p_value, unsigned long len);
extern int GetByteArrayValue(const char* name, char* pValue, long bufflen,
                             long* len);
extern int GetStrValue(const char* name, char* pValue, unsigned long l);

/* #######################
 * Set the logging level
 * ######################## */
#define STESE_TRACE_LEVEL_NONE 0x00
#define STESE_TRACE_LEVEL_ERROR 0x01
#define STESE_TRACE_LEVEL_WARNING 0x02
#define STESE_TRACE_LEVEL_DEBUG 0x03
#define STESE_TRACE_LEVEL_VERBOSE 0x04

#define STLOG_HAL_V(...)                                \
  {                                                     \
    if (hal_trace_level >= STESE_TRACE_LEVEL_VERBOSE)   \
      LOG_PRI(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); \
  }
#define STLOG_HAL_D(...)                                \
  {                                                     \
    if (hal_trace_level >= STESE_TRACE_LEVEL_DEBUG)     \
      LOG_PRI(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); \
  }
#define STLOG_HAL_W(...)                               \
  {                                                    \
    if (hal_trace_level >= STESE_TRACE_LEVEL_WARNING)  \
      LOG_PRI(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__); \
  }
#define STLOG_HAL_E(...)                                \
  {                                                     \
    if (hal_trace_level >= STESE_TRACE_LEVEL_ERROR)     \
      LOG_PRI(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); \
  }
/*******************************************************************************
**
** Function:        InitializeSTLogLevel
**
** Description:     Initialize and get global logging level from
**                  Android property nfc.app_log_level.
**
** Returns:         Global log level:
**                  STNFC_TRACE_LEVEL_NONE    0     * No trace messages to be
**                                                     generated
**                  STNFC_TRACE_LEVEL_ERROR   1     * Error condition trace
**                                                     messages
**                  STNFC_TRACE_LEVEL_WARNING 2     * Warning condition trace
**                                                     messages
**                  STNFC_TRACE_LEVEL_DEBUG   3     * Debug messages (general)
**
*******************************************************************************/
unsigned char InitializeSTLogLevel();

void DispHal(const char* title, const void* data, size_t length);
};
