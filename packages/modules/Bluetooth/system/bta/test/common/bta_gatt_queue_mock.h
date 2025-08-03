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

#pragma once

#include <gmock/gmock.h>

#include "bta_gatt_queue.h"

namespace gatt {

class MockBtaGattQueue {
 public:
  MOCK_METHOD((void), Clean, (uint16_t conn_id));
  MOCK_METHOD((void), ReadCharacteristic,
              (uint16_t conn_id, uint16_t handle, GATT_READ_OP_CB cb,
               void* cb_data));
  MOCK_METHOD((void), WriteCharacteristic,
              (uint16_t conn_id, uint16_t handle, std::vector<uint8_t> value,
               tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
               void* cb_data));
  MOCK_METHOD((void), WriteDescriptor,
              (uint16_t conn_id, uint16_t handle, std::vector<uint8_t> value,
               tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
               void* cb_data));
  MOCK_METHOD((void), ConfigureMtu, (uint16_t conn_id, uint16_t mtu));
  MOCK_METHOD((void), ReadMultiCharacteristic,
              (uint16_t conn_id, tBTA_GATTC_MULTI& p_read_multi,
               bool variable_len, GATT_READ_MULTI_OP_CB cb, void* cb_data));
};

/**
 * Set the {@link MockBtaGattQueue} for testing
 *
 * @param mock_bta_gatt_queue pointer to mock bta gatt queue, could be null
 */
void SetMockBtaGattQueue(MockBtaGattQueue* mock_bta_gatt_queue);

}  // namespace gatt
