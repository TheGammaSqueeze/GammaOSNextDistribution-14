/******************************************************************************
 *  2018 ST Microelectronics S.A.
 *  Copyright 2018 NXP
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

/* #######################
 * Set the log module name in .conf file
 * ########################## */
#define NAME_STESE_HAL_LOGLEVEL "STESE_HAL_LOGLEVEL"
#define NAME_ST_ESE_DEV_NODE "ST_ESE_DEV_NODE"
#define NAME_ST_ESE_DEV_BGT "ST_ESE_DEV_BGT"
#define NAME_ST_ESE_DEV_POLLING_INTERVAL "ST_ESE_DEV_POLLING_INTERVAL"

class EseConfig {
 public:
  static bool hasKey(const std::string& key);
  static std::string getString(const std::string& key);
  static std::string getString(const std::string& key,
                               std::string default_value);
  static unsigned getUnsigned(const std::string& key);
  static unsigned getUnsigned(const std::string& key, unsigned default_value);
  static std::vector<uint8_t> getBytes(const std::string& key);
  static void clear();

 private:
  static EseConfig& getInstance();
  EseConfig();

  ConfigFile config_;
};
