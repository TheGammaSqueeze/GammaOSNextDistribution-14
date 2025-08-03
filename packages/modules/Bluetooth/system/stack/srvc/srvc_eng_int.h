/******************************************************************************
 *
 *  Copyright 1999-2013 Broadcom Corporation
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

#ifndef SRVC_ENG_INT_H
#define SRVC_ENG_INT_H

#include "gatt_api.h"
#include "internal_include/bt_target.h"
#include "srvc_api.h"
#include "types/raw_address.h"

#define SRVC_MAX_APPS GATT_CL_MAX_LCB

#define SRVC_ID_NONE 0
#define SRVC_ID_DIS 1
#define SRVC_ID_MAX SRVC_ID_DIS

#define SRVC_ACT_IGNORE 0
#define SRVC_ACT_RSP 1
#define SRVC_ACT_PENDING 2

typedef struct {
  bool in_use;
  uint16_t conn_id;
  bool connected;
  RawAddress bda;
  uint32_t trans_id;
  uint8_t cur_srvc_id;

  tDIS_VALUE dis_value;

} tSRVC_CLCB;

/* service engine control block */
typedef struct {
  tSRVC_CLCB clcb[SRVC_MAX_APPS]; /* connection link*/
  tGATT_IF gatt_if;
  bool enabled;

} tSRVC_ENG_CB;

/* Global GATT data */
extern tSRVC_ENG_CB srvc_eng_cb;

tSRVC_CLCB* srvc_eng_find_clcb_by_conn_id(uint16_t conn_id);

void srvc_eng_release_channel(uint16_t conn_id);
bool srvc_eng_request_channel(const RawAddress& remote_bda, uint8_t srvc_id);
#endif
