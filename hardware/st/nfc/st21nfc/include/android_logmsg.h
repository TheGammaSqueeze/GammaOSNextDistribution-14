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

#ifdef __cplusplus
extern "C" {
#endif

#include <cutils/properties.h>
#include <log/log.h>
#include "data_types.h"

#define DISP_NCI ProtoDispAdapterDisplayNciPacket
#define HAL_LOG_TAG "StNfcHal"

extern unsigned char hal_trace_level;
extern unsigned char hal_conf_trace_level;
extern int GetNumValue(const char* name, void* p_value, unsigned long len);
extern int GetByteArrayValue(const char* name, char* pValue, long bufflen,
                             long* len);
extern int GetStrValue(const char* name, char* pValue, unsigned long l);

/* #######################
 * Set the log module name in .conf file
 * ########################## */
#define NAME_STNFC_HAL_LOGLEVEL "STNFC_HAL_LOGLEVEL"
#define NAME_CE_ON_SWITCH_OFF_STATE "CE_ON_SWITCH_OFF_STATE"
#define NAME_STNFC_FW_PATH_STORAGE "STNFC_FW_PATH_STORAGE"
#define NAME_STNFC_FW_BIN_NAME "STNFC_FW_BIN_NAME"
#define NAME_STNFC_FW_CONF_NAME "STNFC_FW_CONF_NAME"
#define NAME_STNFC_FW_CONF_NAME_SS "STNFC_FW_CONF_NAME_SS"
#define NAME_STNFC_FW_CONF_NAME_DS "STNFC_FW_CONF_NAME_DS"
#define NAME_STNFC_FW_BIN_NAME "STNFC_FW_BIN_NAME"
#define NAME_STNFC_FW_DEBUG_ENABLED "STNFC_FW_DEBUG_ENABLED"
#define NAME_CORE_CONF_PROP "CORE_CONF_PROP"
#define NAME_STNFC_CONTROL_CLK "STNFC_CONTROL_CLK"
#define NAME_STNFC_UWB_LIB_NAME "STNFC_UWB_LIB_NAME"
#define NAME_STNFC_ACTIVERW_TIMER "STNFC_ACTIVERW_TIMER"
#define NAME_STNFC_FW_SWP_LOG_SIZE "STNFC_FW_SWP_LOG_SIZE"
#define NAME_STNFC_FW_RF_LOG_SIZE "STNFC_FW_RF_LOG_SIZE"
#define NAME_STNFC_REMOTE_FIELD_TIMER "STNFC_REMOTE_FIELD_TIMER"

/* #######################
 * Set the logging level
 * ######################## */
#define STNFC_TRACE_LEVEL_NONE 0x00
#define STNFC_TRACE_LEVEL_ERROR 0x01
#define STNFC_TRACE_LEVEL_WARNING 0x02
#define STNFC_TRACE_LEVEL_DEBUG 0x03
#define STNFC_TRACE_LEVEL_VERBOSE 0x04
#define STNFC_TRACE_LEVEL_MASK 0x0F
#define STNFC_TRACE_FLAG_PRIVACY 0x10

#define STLOG_HAL_V(...)                                    \
  {                                                         \
    if ((hal_trace_level & STNFC_TRACE_LEVEL_MASK) >=       \
        STNFC_TRACE_LEVEL_VERBOSE)                          \
      LOG_PRI(ANDROID_LOG_DEBUG, HAL_LOG_TAG, __VA_ARGS__); \
  }
#define STLOG_HAL_D(...)                                                       \
  {                                                                            \
    if ((hal_trace_level & STNFC_TRACE_LEVEL_MASK) >= STNFC_TRACE_LEVEL_DEBUG) \
      LOG_PRI(ANDROID_LOG_DEBUG, HAL_LOG_TAG, __VA_ARGS__);                    \
  }
#define STLOG_HAL_W(...)                                   \
  {                                                        \
    if ((hal_trace_level & STNFC_TRACE_LEVEL_MASK) >=      \
        STNFC_TRACE_LEVEL_WARNING)                         \
      LOG_PRI(ANDROID_LOG_WARN, HAL_LOG_TAG, __VA_ARGS__); \
  }
#define STLOG_HAL_E(...)                                                       \
  {                                                                            \
    if ((hal_trace_level & STNFC_TRACE_LEVEL_MASK) >= STNFC_TRACE_LEVEL_ERROR) \
      LOG_PRI(ANDROID_LOG_ERROR, HAL_LOG_TAG, __VA_ARGS__);                    \
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

void deInitializeHalLog();

#ifdef __cplusplus
};
#endif
