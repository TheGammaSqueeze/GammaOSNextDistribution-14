/******************************************************************************
 *
 *  Copyright 2020-2023 NXP
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
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <string.h>

#include "phNxpEse_Api.h"

#define OSU_PROP_CLA 0x80
#define OSU_PROP_INS 0xDF
#define OSU_PROP_RST_P1 0xEF

#define ISO7816_BASIC_CHANNEL 0x00
#define ISO7816_CLA_OFFSET 0
#define ISO7816_INS_OFFSET 1
#define ISO7816_P1_OFFSET 2
#define ISO7816_P2_OFFSET 3
#define ISO7816_LC_OFFSET 4
#define ISO7816_EXTENDED_LC_VALUE 0
#define ISO7816_SHORT_APDU_HEADER 5
#define ISO7816_EXTENDED_APDU_HEADER 7
#define ISO7816_CLA_CHN_MASK 0x03
#define IS_OSU_MODE(...) (OsuHalExtn::getInstance().isOsuMode(__VA_ARGS__))

class OsuHalExtn {
 public:
  typedef enum {
    NON_OSU_MODE,      // Normal Mode  : All HAL APIs allowed
    OSU_PROP_MODE,     // OSU mode     : Only OSU JAR command allowed
    OSU_GP_MODE,       // OSU Mode     : GP command in the OSU mode
    OSU_RST_MODE,      // OSU mode     : Proprietary HARD reset
    OSU_BLOCKED_MODE,  // OSU mode     : Command not allowed return error
  } OsuApduMode;

  typedef enum {
    INIT,
    OPENBASIC,
    OPENLOGICAL,
    GETATR,
    TRANSMIT,
    CLOSE,
  } SecureElementAPI;

  static OsuHalExtn& getInstance();

  OsuApduMode isOsuMode(const std::vector<uint8_t>& evt, uint8_t type,
                        phNxpEse_data* pCmdData = nullptr);
  bool isOsuMode(uint8_t type, uint8_t channel = 0xFF);
  virtual ~OsuHalExtn();
  OsuHalExtn() noexcept;
  void checkAndUpdateOsuMode();
  unsigned long int getOSUMaxWtxCount();

 private:
  bool isAppOSUMode;
  bool isJcopOSUMode;
  static const std::vector<uint8_t> osu_aid[10];
  OsuApduMode checkTransmit(uint8_t* input, uint32_t* outLength,
                            const std::vector<uint8_t>& data);
  bool isOsuMode();
};
