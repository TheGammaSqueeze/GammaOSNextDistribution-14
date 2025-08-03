

/******************************************************************************
 *
 *  Copyright 2014 The Android Open Source Project
 *  Copyright 2003-2012 Broadcom Corporation
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
 *
 *  This is the private interface file for the BTA SDP I/F
 *
 ******************************************************************************/
#ifndef BTA_SDP_INT_H
#define BTA_SDP_INT_H

#include "bta/include/bta_sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants
 ****************************************************************************/

/* SDP control block */
typedef struct {
  bool sdp_active;
  RawAddress remote_addr;
  tBTA_SDP_DM_CBACK* p_dm_cback;
} tBTA_SDP_CB;

/* SDP control block */
extern tBTA_SDP_CB bta_sdp_cb;

/* config struct */
extern tBTA_SDP_CFG* p_bta_sdp_cfg;

void bta_sdp_enable(tBTA_SDP_DM_CBACK* p_cback);
void bta_sdp_search(const RawAddress bd_addr, const bluetooth::Uuid uuid);
void bta_sdp_create_record(void* user_data);
void bta_sdp_remove_record(void* user_data);

#endif /* BTA_SDP_INT_H */
