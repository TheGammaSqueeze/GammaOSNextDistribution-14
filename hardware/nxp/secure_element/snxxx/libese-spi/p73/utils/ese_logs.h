/*
 * Copyright 2022 NXP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#if !defined(NXPESELOG__H_INCLUDED)
#define NXPESELOG__H_INCLUDED
#include <stdint.h>

extern uint8_t ese_log_level;

/* ####################### LOG LEVELS ################### */

#define NXPESE_LOGLEVEL_SILENT 0x00
#define NXPESE_LOGLEVEL_ERROR 0x01
#define NXPESE_LOGLEVEL_WARN 0x02
#define NXPESE_LOGLEVEL_INFO 0x03
#define NXPESE_LOGLEVEL_DEBUG 0x04

/* ########################################################
 */
/* Macros  */

#define NXP_LOG_ESE_D(...) \
  { ALOGD_IF(ese_log_level >= NXPESE_LOGLEVEL_DEBUG, __VA_ARGS__); }

#define NXP_LOG_ESE_I(...) \
  { ALOGI_IF(ese_log_level >= NXPESE_LOGLEVEL_INFO, __VA_ARGS__); }

#define NXP_LOG_ESE_W(...) \
  { ALOGW_IF(ese_log_level >= NXPESE_LOGLEVEL_WARN, __VA_ARGS__); }

#define NXP_LOG_ESE_E(...) \
  { ALOGE_IF(ese_log_level >= NXPESE_LOGLEVEL_ERROR, __VA_ARGS__); }

#endif /* NXPESELOG__H_INCLUDED */
