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

#include <base/functional/callback.h>
#include <gmock/gmock.h>

#include "bta_gatt_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace gatt {

class BtaGattInterface {
 public:
  virtual void AppRegister(tBTA_GATTC_CBACK* p_client_cb,
                           BtaAppRegisterCallback cb, bool eatt_support) = 0;
  virtual void AppDeregister(tGATT_IF client_if) = 0;
  virtual void Open(tGATT_IF client_if, const RawAddress& remote_bda,
                    tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
                    bool opportunistic, uint8_t initiating_phys) = 0;
  virtual void Open(tGATT_IF client_if, const RawAddress& remote_bda,
                    tBTM_BLE_CONN_TYPE connection_type, bool opportunistic) = 0;
  virtual void CancelOpen(tGATT_IF client_if, const RawAddress& remote_bda,
                          bool is_direct) = 0;
  virtual void Close(uint16_t conn_id) = 0;
  virtual void ServiceSearchRequest(uint16_t conn_id,
                                    const bluetooth::Uuid* p_srvc_uuid) = 0;
  virtual void SendIndConfirm(uint16_t conn_id, uint16_t cid) = 0;
  virtual const std::list<Service>* GetServices(uint16_t conn_id) = 0;
  virtual const Characteristic* GetCharacteristic(uint16_t conn_id,
                                                  uint16_t handle) = 0;
  virtual const Service* GetOwningService(uint16_t conn_id,
                                          uint16_t handle) = 0;
  virtual tGATT_STATUS RegisterForNotifications(tGATT_IF client_if,
                                                const RawAddress& remote_bda,
                                                uint16_t handle) = 0;
  virtual tGATT_STATUS DeregisterForNotifications(tGATT_IF client_if,
                                                  const RawAddress& remote_bda,
                                                  uint16_t handle) = 0;
  virtual ~BtaGattInterface() = default;
};

class MockBtaGattInterface : public BtaGattInterface {
 public:
  MOCK_METHOD((void), AppRegister,
              (tBTA_GATTC_CBACK * p_client_cb, BtaAppRegisterCallback cb,
               bool eatt_support),
              (override));
  MOCK_METHOD((void), AppDeregister, (tGATT_IF client_if), (override));
  MOCK_METHOD((void), Open,
              (tGATT_IF client_if, const RawAddress& remote_bda,
               tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
               bool opportunistic, uint8_t initiating_phys),
              (override));
  MOCK_METHOD((void), Open,
              (tGATT_IF client_if, const RawAddress& remote_bda,
               tBTM_BLE_CONN_TYPE connection_type, bool opportunistic));
  MOCK_METHOD((void), CancelOpen,
              (tGATT_IF client_if, const RawAddress& remote_bda,
               bool is_direct));
  MOCK_METHOD((void), Close, (uint16_t conn_id));
  MOCK_METHOD((void), ServiceSearchRequest,
              (uint16_t conn_id, const bluetooth::Uuid* p_srvc_uuid));
  MOCK_METHOD((void), SendIndConfirm, (uint16_t conn_id, uint16_t cid),
              (override));
  MOCK_METHOD((std::list<Service>*), GetServices, (uint16_t conn_id));
  MOCK_METHOD((const Characteristic*), GetCharacteristic,
              (uint16_t conn_id, uint16_t handle));
  MOCK_METHOD((const Service*), GetOwningService,
              (uint16_t conn_id, uint16_t handle));
  MOCK_METHOD((tGATT_STATUS), RegisterForNotifications,
              (tGATT_IF client_if, const RawAddress& remote_bda,
               uint16_t handle));
  MOCK_METHOD((tGATT_STATUS), DeregisterForNotifications,
              (tGATT_IF client_if, const RawAddress& remote_bda,
               uint16_t handle));
};

/**
 * Set the {@link MockBtaGattInterface} for testing
 *
 * @param mock_bta_gatt_interface pointer to mock bta gatt interface,
 * could be null
 */
void SetMockBtaGattInterface(MockBtaGattInterface* mock_bta_gatt_interface);

}  // namespace gatt
