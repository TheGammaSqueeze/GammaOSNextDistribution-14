/******************************************************************************
 *
 *  Copyright 2015-2018,2020-2023 NXP
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
#ifndef __CAP_H__
#define __CAP_H__
#include "Nxp_Features.h"
#define pConfigFL (capability::getInstance())

class capability {
 private:
  static capability* instance;
  const uint16_t offsetDlRspChipType = 3;
  const uint16_t offsetFwRomCodeVersion = 4;
  const uint16_t offsetFwMinorVersion = 6;
  const uint16_t offsetFwMajorVersion = 7;
  const uint16_t offsetFwMajorVersion_pn557 = 11;
  capability();
  tNFC_chipType determineChipTypeFromNciRsp(uint8_t* msg, uint16_t msg_len);
  tNFC_chipType determineChipTypeFromDLRsp(uint8_t* msg, uint16_t msg_len);

 public:
  /*product[] will be used to print product version and
  should be kept in accordance with tNFC_chipType*/
  const char* product[15] = {"UNKNOWN", "PN547C2", "PN65T", "PN548C2", "PN66T",
                             "PN551",   "PN67T",   "PN553", "PN80T",   "PN557",
                             "PN81T",   "sn100",   "SN220", "pn560",   "SN300"};
  static tNFC_chipType chipType;
  static capability* getInstance();
  tNFC_chipType processChipType(uint8_t* msg, uint16_t msg_len);
  uint32_t getFWVersionInfo(uint8_t* msg, uint16_t msg_len);
};
#endif
