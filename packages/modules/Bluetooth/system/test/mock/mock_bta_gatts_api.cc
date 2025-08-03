/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:13
 */

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/location.h>

#include <cstdint>
#include <vector>

#include "bta/include/bta_gatt_api.h"
#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

void BTA_GATTS_Disable(void) { inc_func_call_count(__func__); }
void BTA_GATTS_AppDeregister(tGATT_IF /* server_if */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_AppRegister(const bluetooth::Uuid& /* app_uuid */,
                           tBTA_GATTS_CBACK* /* p_cback */,
                           bool /* eatt_support */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_CancelOpen(tGATT_IF /* server_if */,
                          const RawAddress& /* remote_bda */,
                          bool /* is_direct */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_Close(uint16_t /* conn_id */) { inc_func_call_count(__func__); }
void BTA_GATTS_AddService(tGATT_IF /* server_if */,
                          std::vector<btgatt_db_element_t> /* service */,
                          BTA_GATTS_AddServiceCb /* cb */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_DeleteService(uint16_t /* service_id */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_HandleValueIndication(uint16_t /* conn_id */,
                                     uint16_t /* attr_id */,
                                     std::vector<uint8_t> /* value */,
                                     bool /* need_confirm */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_Open(tGATT_IF /* server_if */,
                    const RawAddress& /* remote_bda */, bool /* is_direct */,
                    tBT_TRANSPORT /* transport */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_SendRsp(uint16_t /* conn_id */, uint32_t /* trans_id */,
                       tGATT_STATUS /* status */, tGATTS_RSP* /* p_msg */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_StopService(uint16_t /* service_id */) {
  inc_func_call_count(__func__);
}
void bta_gatts_add_service_impl(tGATT_IF /* server_if */,
                                std::vector<btgatt_db_element_t> /* service */,
                                BTA_GATTS_AddServiceCb /* cb */) {
  inc_func_call_count(__func__);
}
void BTA_GATTS_InitBonded(void) { inc_func_call_count(__func__); }
