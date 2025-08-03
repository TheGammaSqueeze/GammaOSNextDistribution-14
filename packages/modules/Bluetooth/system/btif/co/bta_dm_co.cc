/******************************************************************************
 *
 *  Copyright 2009-2012 Broadcom Corporation
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
#include "bta/include/bta_api.h"
#include "bta/include/bta_sec_api.h"
#include "internal_include/bte_appl.h"
#include "stack/include/btm_api_types.h"

tBTE_APPL_CFG bte_appl_cfg = {
    BTA_LE_AUTH_REQ_SC_MITM_BOND,  // Authentication requirements
    BTM_IO_CAP_KBDISP, BTM_BLE_INITIATOR_KEY_SIZE, BTM_BLE_RESPONDER_KEY_SIZE,
    BTM_BLE_MAX_KEY_SIZE};
