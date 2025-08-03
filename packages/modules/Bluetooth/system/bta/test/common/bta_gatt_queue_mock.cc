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

#include "bta_gatt_queue_mock.h"

static gatt::MockBtaGattQueue* gatt_queue = nullptr;

void gatt::SetMockBtaGattQueue(MockBtaGattQueue* mock_bta_gatt_queue) {
  gatt_queue = mock_bta_gatt_queue;
}

void BtaGattQueue::Clean(uint16_t conn_id) { gatt_queue->Clean(conn_id); }

void BtaGattQueue::ReadCharacteristic(uint16_t conn_id, uint16_t handle,
                                      GATT_READ_OP_CB cb, void* cb_data) {
  gatt_queue->ReadCharacteristic(conn_id, handle, cb, cb_data);
}

void BtaGattQueue::WriteCharacteristic(uint16_t conn_id, uint16_t handle,
                                       std::vector<uint8_t> value,
                                       tGATT_WRITE_TYPE write_type,
                                       GATT_WRITE_OP_CB cb, void* cb_data) {
  gatt_queue->WriteCharacteristic(conn_id, handle, value, write_type, cb,
                                  cb_data);
}

void BtaGattQueue::WriteDescriptor(uint16_t conn_id, uint16_t handle,
                                   std::vector<uint8_t> value,
                                   tGATT_WRITE_TYPE write_type,
                                   GATT_WRITE_OP_CB cb, void* cb_data) {
  gatt_queue->WriteDescriptor(conn_id, handle, value, write_type, cb, cb_data);
}

void BtaGattQueue::ConfigureMtu(uint16_t conn_id, uint16_t mtu) {
  LOG_ASSERT(gatt_queue) << "Mock GATT queue not set!";
  gatt_queue->ConfigureMtu(conn_id, mtu);
}

void BtaGattQueue::ReadMultiCharacteristic(uint16_t conn_id,
                                           tBTA_GATTC_MULTI& p_read_multi,
                                           bool variable_len,
                                           GATT_READ_MULTI_OP_CB cb,
                                           void* cb_data) {
  LOG_ASSERT(gatt_queue) << "Mock GATT queue not set!";
  gatt_queue->ReadMultiCharacteristic(conn_id, p_read_multi, variable_len, cb,
                                      cb_data);
}
