/******************************************************************************
 *
 *  Copyright 2018-2020,2022-2023 NXP
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

#pragma once

#include <string>
#include <vector>

#include <config.h>

#ifndef __CONFIG_H
#define __CONFIG_H

#define NAME_SE_LOG_LEVEL "SE_LOG_LEVEL"
#define NAME_SE_KPI_MEASUREMENT_ENABLED "SE_KPI_MEASUREMENT_ENABLED"
#define NAME_NXP_JCOPDL_AT_BOOT_ENABLE "NXP_JCOPDL_AT_BOOT_ENABLE"
#define NAME_NXP_WTX_COUNT_VALUE "NXP_WTX_COUNT_VALUE"
#define NAME_NXP_MAX_RSP_TIMEOUT "NXP_MAX_RSP_TIMEOUT"
#define NAME_NXP_POWER_SCHEME "NXP_POWER_SCHEME"
#define NAME_NXP_SOF_WRITE "NXP_SOF_WRITE"
#define NAME_NXP_TP_MEASUREMENT "NXP_TP_MEASUREMENT"
#define NAME_NXP_SPI_INTF_RST_ENABLE "NXP_SPI_INTF_RST_ENABLE"
#define NAME_NXP_MAX_RNACK_RETRY "NXP_MAX_RNACK_RETRY"
#define NAME_NXP_SPI_WRITE_TIMEOUT "NXP_SPI_WRITE_TIMEOUT"
#define NAME_NXP_ESE_DEV_NODE "NXP_ESE_DEV_NODE"
#define NAME_NXP_VISO_DPD_ENABLED "NXP_VISO_DPD_ENABLED"
#define NAME_NXP_NAD_POLL_RETRY_TIME "NXP_NAD_POLL_RETRY_TIME"
#define NAME_RNACK_RETRY_DELAY "RNACK_RETRY_DELAY"
#define NAME_NXP_ESE_IFSD_VALUE "NXP_ESE_IFSD_VALUE"
#define NAME_NXP_EUICC_IFSD_VALUE "NXP_EUICC_IFSD_VALUE"
#define NAME_NXP_P61_COLD_RESET_INTERFACE "NXP_P61_COLD_RESET_INTERFACE"
#define NAME_NXP_ESE_GPIO_RESET "NXP_ESE_GPIO_RESET"
#define NAME_NXP_OS_VERSION "NXP_OS_VERSION"
#define NAME_NXP_WTX_NTF_COUNT "NXP_WTX_NTF_COUNT"
#define NAME_NXP_OSU_MAX_WTX_COUNT "NXP_OSU_MAX_WTX_COUNT"
#define NAME_NXP_TRANSPORT "NXP_TRANSPORT"
#define NAME_NXP_SE_PRIORITY_ACCESS "NXP_SE_PRIORITY_ACCESS"
#endif

class EseConfig {
 public:
  static bool hasKey(const std::string& key);
  static std::string getString(const std::string& key);
  static std::string getString(const std::string& key,
                               std::string default_value);
  static unsigned getUnsigned(const std::string& key);
  static unsigned getUnsigned(const std::string& key, unsigned default_value);

 private:
  static EseConfig& getInstance();
  EseConfig();

  ConfigFile config_;
};
