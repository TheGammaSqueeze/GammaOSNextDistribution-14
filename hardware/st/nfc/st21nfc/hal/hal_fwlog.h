/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2023 ST Microelectronics S.A.
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
 *
 *
 ----------------------------------------------------------------------*/
#

#ifndef HAL_FWLOG_H_
#define HAL_FWLOG_H_

#include "halcore.h"

    static const int T_CERx = 0x09;
static const int T_fieldOn = 0x10;
static const int T_fieldOff = 0x11;
static const int T_CERxError = 0x19;

static const uint8_t PROPRIETARY_GID = 0x6F;
static const uint8_t ANDROID_OID = 0x0C;
static const uint8_t TYPE_REMOTE_FIELD = 0x00;
static const uint8_t TYPE_A = 0x01;
static const uint8_t TYPE_B = 0x02;
static const uint8_t TYPE_F = 0x03;
static const uint8_t TYPE_V = 0x04;

static const uint8_t TYPE_UNKNOWN = 0x07;

typedef union timestamp_bytes {
  uint8_t ts1;
  uint8_t ts2;
  uint8_t ts3;
  uint8_t ts4;
} timestamp_bytes;

int notifyPollingLoopFrames(uint8_t *p_data, uint16_t data_len,
                            uint8_t *bufferToSend);
uint8_t handlePollingLoopData(uint8_t *tlvBuffer, uint16_t data_len,
                              uint8_t **NewTlv);

#endif
