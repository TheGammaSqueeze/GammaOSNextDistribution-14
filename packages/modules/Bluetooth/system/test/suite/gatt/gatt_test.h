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

#include "adapter/bluetooth_test.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bttest {

// This class represents the Bluetooth GATT testing framework and provides
// helpers and callbacks for GUnit to use for testing gatt.
class GattTest : public BluetoothTest {
 protected:
  GattTest() = default;
  GattTest(const GattTest&) = delete;
  GattTest& operator=(const GattTest&) = delete;

  virtual ~GattTest() = default;

  // Gets the gatt_scanner_interface
  const BleScannerInterface* gatt_scanner_interface();

  // Gets the gatt_client_interface
  const btgatt_client_interface_t* gatt_client_interface();

  // Gets the gatt_server_interface
  const btgatt_server_interface_t* gatt_server_interface();

  // Getters for variables that track GATT-related state
  int client_interface_id() const { return client_interface_id_; }
  int server_interface_id() const { return server_interface_id_; }
  int service_handle() const { return service_handle_; }
  int characteristic_handle() const { return characteristic_handle_; }
  int descriptor_handle() const { return descriptor_handle_; }
  int status() const { return status_; }

  // SetUp initializes the Bluetooth interfaces and the GATT Interface as well
  // as registers the callbacks and initializes the semaphores before every test
  virtual void SetUp();

  // TearDown cleans up the Bluetooth and GATT interfaces and destroys the
  // callback semaphores at the end of every test
  virtual void TearDown();

  friend void RegisterClientCallback(int status, int clientIf,
                                     const bluetooth::Uuid& app_uuid);
  friend void ScanResultCallback(uint16_t ble_evt_type, uint8_t addr_type,
                                 RawAddress* bda, uint8_t ble_primary_phy,
                                 uint8_t ble_secondary_phy,
                                 uint8_t ble_advertising_sid,
                                 int8_t ble_tx_power, int8_t rssi,
                                 uint16_t ble_periodic_adv_int,
                                 std::vector<uint8_t> adv_data,
                                 RawAddress* original_bda);

  friend void RegisterServerCallback(int status, int server_if,
                                     const bluetooth::Uuid& uuid);
  friend void ServiceAddedCallback(int status, int server_if,
                                   const btgatt_db_element_t* service,
                                   size_t service_count);
  friend void ServiceStoppedCallback(int status, int server_if,
                                     int srvc_handle);
  friend void ServiceDeletedCallback(int status, int server_if,
                                     int srvc_handle);

  // Semaphores used to wait for specific callback execution. Each callback
  // has its own semaphore associated with it
  btsemaphore register_client_callback_sem_;
  btsemaphore scan_result_callback_sem_;
  btsemaphore listen_callback_sem_;

  btsemaphore register_server_callback_sem_;
  btsemaphore service_added_callback_sem_;
  btsemaphore characteristic_added_callback_sem_;
  btsemaphore descriptor_added_callback_sem_;
  btsemaphore service_started_callback_sem_;
  btsemaphore service_stopped_callback_sem_;
  btsemaphore service_deleted_callback_sem_;

 private:
  const btgatt_interface_t* gatt_interface_;

  // No mutex needed for these as the semaphores should ensure
  // synchronous access

  // An ID that is used as a handle for each gatt client.
  int client_interface_id_;

  // An ID that is used as a handle for each gatt server.
  int server_interface_id_;

  // A handle to the last used service.
  int service_handle_;

  // A handle to the last characteristic added.
  int characteristic_handle_;

  // A handle to the last descriptor added.
  int descriptor_handle_;

  // The status of the last callback. Is BT_STATUS_SUCCESS if no issues.
  int status_;
};

}  // bttest
