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
 *   Functions generated:30
 */

#include <base/functional/bind.h>
#include <base/functional/callback.h>

#include "bta/gatt/database.h"
#include "bta/include/bta_gatt_api.h"
#include "stack/include/gatt_api.h"
#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

void BTA_GATTC_Disable(void) { inc_func_call_count(__func__); }
const gatt::Characteristic* BTA_GATTC_GetCharacteristic(uint16_t /* conn_id */,
                                                        uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
const gatt::Characteristic* BTA_GATTC_GetOwningCharacteristic(
    uint16_t /* conn_id */, uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
const gatt::Descriptor* BTA_GATTC_GetDescriptor(uint16_t /* conn_id */,
                                                uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
const gatt::Service* BTA_GATTC_GetOwningService(uint16_t /* conn_id */,
                                                uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
const std::list<gatt::Service>* BTA_GATTC_GetServices(uint16_t /* conn_id */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tGATT_STATUS BTA_GATTC_DeregisterForNotifications(tGATT_IF /* client_if */,
                                                  const RawAddress& /* bda */,
                                                  uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return GATT_SUCCESS;
}
tGATT_STATUS BTA_GATTC_RegisterForNotifications(tGATT_IF /* client_if */,
                                                const RawAddress& /* bda */,
                                                uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return GATT_SUCCESS;
}
void BTA_GATTC_AppDeregister(tGATT_IF /* client_if */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_AppRegister(tBTA_GATTC_CBACK* /* p_client_cb */,
                           BtaAppRegisterCallback /* cb */,
                           bool /* eatt_support */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_CancelOpen(tGATT_IF /* client_if */,
                          const RawAddress& /* remote_bda */,
                          bool /* is_direct */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_Close(uint16_t /* conn_id */) { inc_func_call_count(__func__); }
void BTA_GATTC_ConfigureMTU(uint16_t /* conn_id */, uint16_t /* mtu */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ConfigureMTU(uint16_t /* conn_id */, uint16_t /* mtu */,
                            GATT_CONFIGURE_MTU_OP_CB /* callback */,
                            void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_DiscoverServiceByUuid(uint16_t /* conn_id */,
                                     const bluetooth::Uuid& /* srvc_uuid */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ExecuteWrite(uint16_t /* conn_id */, bool /* is_execute */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_GetGattDb(uint16_t /* conn_id */, uint16_t /* start_handle */,
                         uint16_t /* end_handle */,
                         btgatt_db_element_t** /* db */, int* /* count */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_Open(tGATT_IF /* client_if */,
                    const RawAddress& /* remote_bda */,
                    tBTM_BLE_CONN_TYPE /* connection_type */,
                    bool /* opportunistic */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_Open(tGATT_IF /* client_if */,
                    const RawAddress& /* remote_bda */,
                    tBTM_BLE_CONN_TYPE /* connection_type */,
                    tBT_TRANSPORT /* transport */, bool /* opportunistic */,
                    uint8_t /* initiating_phys */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_Open(tGATT_IF /* client_if */,
                    const RawAddress& /* remote_bda */,
                    tBLE_ADDR_TYPE /* addr_type */,
                    tBTM_BLE_CONN_TYPE /* connection_type */,
                    tBT_TRANSPORT /* transport */, bool /* opportunistic */,
                    uint8_t /* initiating_phys */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_PrepareWrite(uint16_t /* conn_id */, uint16_t /* handle */,
                            uint16_t /* offset */,
                            std::vector<uint8_t> /* value */,
                            tGATT_AUTH_REQ /* auth_req */,
                            GATT_WRITE_OP_CB /* callback */,
                            void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ReadCharDescr(uint16_t /* conn_id */, uint16_t /* handle */,
                             tGATT_AUTH_REQ /* auth_req */,
                             GATT_READ_OP_CB /* callback */,
                             void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ReadCharacteristic(uint16_t /* conn_id */, uint16_t /* handle */,
                                  tGATT_AUTH_REQ /* auth_req */,
                                  GATT_READ_OP_CB /* callback */,
                                  void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ReadMultiple(uint16_t /* conn_id */,
                            tBTA_GATTC_MULTI& /* handles */,
                            bool /* variable_len */,
                            tGATT_AUTH_REQ /* auth_req */,
                            GATT_READ_MULTI_OP_CB /* callback */,
                            void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ReadUsingCharUuid(uint16_t /* conn_id */,
                                 const bluetooth::Uuid& /* uuid */,
                                 uint16_t /* s_handle */,
                                 uint16_t /* e_handle */,
                                 tGATT_AUTH_REQ /* auth_req */,
                                 GATT_READ_OP_CB /* callback */,
                                 void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_Refresh(const RawAddress& /* remote_bda */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_SendIndConfirm(uint16_t /* conn_id */, uint16_t /* cid */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_ServiceSearchRequest(uint16_t /* conn_id */,
                                    const bluetooth::Uuid* /* p_srvc_uuid */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_WriteCharDescr(uint16_t /* conn_id */, uint16_t /* handle */,
                              std::vector<uint8_t> /* value */,
                              tGATT_AUTH_REQ /* auth_req */,
                              GATT_WRITE_OP_CB /* callback */,
                              void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void BTA_GATTC_WriteCharValue(uint16_t /* conn_id */, uint16_t /* handle */,
                              tGATT_WRITE_TYPE /* write_type */,
                              std::vector<uint8_t> /* value */,
                              tGATT_AUTH_REQ /* auth_req */,
                              GATT_WRITE_OP_CB /* callback */,
                              void* /* cb_data */) {
  inc_func_call_count(__func__);
}
void bta_gattc_continue_discovery_if_needed(const RawAddress& /* bd_addr */,
                                            uint16_t /* acl_handle */) {
  inc_func_call_count(__func__);
}
