/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "bta_gatt_api_mock.h"

#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

static gatt::MockBtaGattInterface* gatt_interface = nullptr;

void gatt::SetMockBtaGattInterface(
    MockBtaGattInterface* mock_bta_gatt_interface) {
  gatt_interface = mock_bta_gatt_interface;
}

void BTA_GATTC_AppRegister(tBTA_GATTC_CBACK* p_client_cb,
                           BtaAppRegisterCallback cb, bool eatt_support) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->AppRegister(p_client_cb, cb, eatt_support);
}

void BTA_GATTC_AppDeregister(tGATT_IF client_if) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->AppDeregister(client_if);
}

void BTA_GATTC_Open(tGATT_IF client_if, const RawAddress& remote_bda,
                    tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
                    bool opportunistic, uint8_t initiating_phys) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->Open(client_if, remote_bda, connection_type, transport,
                       opportunistic, initiating_phys);
}

void BTA_GATTC_Open(tGATT_IF client_if, const RawAddress& remote_bda,
                    tBTM_BLE_CONN_TYPE connection_type, bool opportunistic) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->Open(client_if, remote_bda, connection_type, opportunistic);
}

void BTA_GATTC_CancelOpen(tGATT_IF client_if, const RawAddress& remote_bda,
                          bool is_direct) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->CancelOpen(client_if, remote_bda, is_direct);
}

void BTA_GATTC_Close(uint16_t conn_id) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->Close(conn_id);
}

void BTA_GATTC_ServiceSearchRequest(uint16_t conn_id,
                                    const bluetooth::Uuid* p_srvc_uuid) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->ServiceSearchRequest(conn_id, p_srvc_uuid);
}

void BTA_GATTC_SendIndConfirm(uint16_t conn_id, uint16_t cid) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  gatt_interface->SendIndConfirm(conn_id, cid);
}

const std::list<gatt::Service>* BTA_GATTC_GetServices(uint16_t conn_id) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  return gatt_interface->GetServices(conn_id);
}

const gatt::Characteristic* BTA_GATTC_GetCharacteristic(uint16_t conn_id,
                                                        uint16_t handle) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  return gatt_interface->GetCharacteristic(conn_id, handle);
}

const gatt::Service* BTA_GATTC_GetOwningService(uint16_t conn_id,
                                                uint16_t handle) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  return gatt_interface->GetOwningService(conn_id, handle);
}

tGATT_STATUS BTA_GATTC_RegisterForNotifications(tGATT_IF client_if,
                                                const RawAddress& remote_bda,
                                                uint16_t handle) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  return gatt_interface->RegisterForNotifications(client_if, remote_bda,
                                                  handle);
}

tGATT_STATUS BTA_GATTC_DeregisterForNotifications(tGATT_IF client_if,
                                                  const RawAddress& remote_bda,
                                                  uint16_t handle) {
  LOG_ASSERT(gatt_interface) << "Mock GATT interface not set!";
  return gatt_interface->DeregisterForNotifications(client_if, remote_bda,
                                                    handle);
}
