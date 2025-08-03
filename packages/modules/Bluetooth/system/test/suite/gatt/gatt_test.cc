/******************************************************************************
 *
 *  Copyright 2015 Google, Inc.
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

#include "gatt/gatt_test.h"

#include "adapter/bluetooth_test.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bttest {

static GattTest* instance = nullptr;

void RegisterClientCallback(int status, int clientIf,
                            const bluetooth::Uuid& app_uuid) {
  instance->status_ = status;
  instance->client_interface_id_ = clientIf;
  semaphore_post(instance->register_client_callback_sem_);
}

void ScanResultCallback(uint16_t ble_evt_type, uint8_t addr_type,
                        RawAddress* bda, uint8_t ble_primary_phy,
                        uint8_t ble_secondary_phy, uint8_t ble_advertising_sid,
                        int8_t ble_tx_power, int8_t rssi,
                        uint16_t ble_periodic_adv_int,
                        std::vector<uint8_t> adv_data,
                        RawAddress* original_bda) {
  semaphore_post(instance->scan_result_callback_sem_);
}

// GATT server callbacks
void RegisterServerCallback(int status, int server_if,
                            const bluetooth::Uuid& uuid) {
  instance->status_ = status;
  instance->server_interface_id_ = server_if;
  semaphore_post(instance->register_server_callback_sem_);
}

void ServiceAddedCallback(int status, int server_if,
                          const btgatt_db_element_t* service,
                          size_t service_count) {
  instance->status_ = status;
  instance->server_interface_id_ = server_if;
  instance->service_handle_ = service[0].attribute_handle;
  semaphore_post(instance->service_added_callback_sem_);
}

void ServiceStoppedCallback(int status, int server_if, int srvc_handle) {
  instance->status_ = status;
  instance->server_interface_id_ = server_if;
  instance->service_handle_ = srvc_handle;
  semaphore_post(instance->service_stopped_callback_sem_);
}

void ServiceDeletedCallback(int status, int server_if, int srvc_handle) {
  instance->status_ = status;
  instance->server_interface_id_ = server_if;
  instance->service_handle_ = srvc_handle;
  semaphore_post(instance->service_deleted_callback_sem_);
}

static const btgatt_scanner_callbacks_t scanner_callbacks = {
    .scan_result_cb = ScanResultCallback,
};

static const btgatt_client_callbacks_t client_callbacks = {
    .register_client_cb = RegisterClientCallback,
};

static const btgatt_server_callbacks_t server_callbacks = {
    .register_server_cb = RegisterServerCallback,
    .service_added_cb = ServiceAddedCallback,
    .service_stopped_cb = ServiceStoppedCallback,
    .service_deleted_cb = ServiceDeletedCallback,
};

static const btgatt_callbacks_t callbacks = {
    sizeof(btgatt_callbacks_t),
    &client_callbacks,
    &server_callbacks,
    &scanner_callbacks,
};

void GattTest::SetUp() {
  gatt_interface_ = nullptr;

  client_interface_id_ = 0;
  server_interface_id_ = 0;
  service_handle_ = 0;
  characteristic_handle_ = 0;
  descriptor_handle_ = 0;
  status_ = 0;

  BluetoothTest::SetUp();
  ASSERT_EQ(bt_interface()->enable(), BT_STATUS_SUCCESS);
  semaphore_wait(adapter_state_changed_callback_sem_);
  EXPECT_TRUE(GetState() == BT_STATE_ON);

  gatt_interface_ = reinterpret_cast<const btgatt_interface_t*>(
      bt_interface()->get_profile_interface(BT_PROFILE_GATT_ID));
  ASSERT_NE(nullptr, gatt_interface_);
  instance = this;
  auto status = gatt_interface_->init(&callbacks);
  ASSERT_EQ(status, BT_STATUS_SUCCESS);
}

void GattTest::TearDown() {
  instance = nullptr;
  gatt_interface_ = nullptr;

  ASSERT_EQ(bt_interface()->disable(), BT_STATUS_SUCCESS);
  semaphore_wait(adapter_state_changed_callback_sem_);
  BluetoothTest::TearDown();
}

const BleScannerInterface* GattTest::gatt_scanner_interface() {
  return gatt_interface_->scanner;
}

const btgatt_client_interface_t* GattTest::gatt_client_interface() {
  return gatt_interface_->client;
}

const btgatt_server_interface_t* GattTest::gatt_server_interface() {
  return gatt_interface_->server;
}

}  // bttest
