/******************************************************************************
 *
 *  Copyright 2018-2020, 2022-2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

/*
 * NXP ESE features macros definitions
 */

#ifndef NXP_ESE_FEATURES_H
#define NXP_ESE_FEATURES_H

#define ESE_DEBUG_UTILS_INCLUDED true

#define NXP_POWER_SCHEME_SUPPORT true
#define NXP_ESE_END_OF_SESSION true

#define NXP_ESE_WTX_RES_DELAY true
#define NXP_ESE_P73_ISO_RST true

typedef enum OS_VERSION {
  OS_VERSION_DEFAULT = 0,
  OS_VERSION_4_0 = 1,
  OS_VERSION_5_1 = 2,
  OS_VERSION_5_2 = 3,
  OS_VERSION_5_2_2 = 4,
  OS_VERSION_6_2 = 5,
  OS_VERSION_6_3 = 6,
  OS_VERSION_8_9 = 8,
  INVALID_OS_VERSION = 0xFF,
} phNxpEse_OsVersion_t;

extern phNxpEse_OsVersion_t phNxpEse_getOsVersion();

#define GET_CHIP_OS_VERSION() phNxpEse_getOsVersion()

#endif /* end of #ifndef NXP_ESE_FEATURES_H */
