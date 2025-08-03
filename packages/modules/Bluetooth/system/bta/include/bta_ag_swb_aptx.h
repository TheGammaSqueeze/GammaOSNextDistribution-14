/*
 * Copyright 2023 The Android Open Source Project
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

#ifndef _BTA_AG_SWB_H_
#define _BTA_AG_SWB_H_

#include "bta/ag/bta_ag_int.h"
#include "device/include/esco_parameters.h"
#include "include/hardware/bt_hf.h"

/* Events originated from HF side */
#define BTA_AG_AT_QAC_EVT 253
#define BTA_AG_AT_QCS_EVT 254
#define BTA_AG_LOCAL_RES_QAC 0x108
#define BTA_AG_LOCAL_RES_QCS 0x109

#define SWB_CODECS_SUPPORTED "0,4,6,7"
#define SWB_CODECS_UNSUPPORTED "0xFFFF"
#define SWB_CODECS_NUMBER 4

bool is_hfp_aptx_voice_enabled();

void bta_ag_swb_handle_vs_at_events(tBTA_AG_SCB* p_scb, uint16_t cmd,
                                    int16_t int_arg, tBTA_AG_VAL* val);
tBTA_AG_PEER_CODEC bta_ag_parse_qac(char* p_s);

#endif  //_BTA_AG_SWB_H_
