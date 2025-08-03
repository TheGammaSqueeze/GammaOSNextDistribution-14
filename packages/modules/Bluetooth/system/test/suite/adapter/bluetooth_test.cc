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

#include <binder/ProcessState.h>
#include <stdio.h>

#include <mutex>

#include "btcore/include/property.h"
#include "types/raw_address.h"

extern bt_interface_t bluetoothInterface;

void semaphore_wait(btsemaphore &s) {
  s.wait();
}
void semaphore_post(btsemaphore &s) {
  s.post();
}
void semaphore_try_wait(btsemaphore &s) {
  s.try_wait();
}

namespace bttest {

static BluetoothTest* instance = nullptr;

void AdapterStateChangedCallback(bt_state_t new_state) {
  instance->state_ = new_state;
  semaphore_post(instance->adapter_state_changed_callback_sem_);
}

void AdapterPropertiesCallback(bt_status_t status, int num_properties,
                               bt_property_t* new_properties) {
  property_free_array(instance->last_changed_properties_,
                      instance->properties_changed_count_);
  instance->last_changed_properties_ =
      property_copy_array(new_properties, num_properties);
  instance->properties_changed_count_ = num_properties;
  semaphore_post(instance->adapter_properties_callback_sem_);
}

void RemoteDevicePropertiesCallback(bt_status_t status,
                                    RawAddress* remote_bd_addr,
                                    int num_properties,
                                    bt_property_t* properties) {
  instance->curr_remote_device_ = *remote_bd_addr;
  property_free_array(instance->remote_device_last_changed_properties_,
                      instance->remote_device_properties_changed_count_);
  instance->remote_device_last_changed_properties_ =
      property_copy_array(properties, num_properties);
  instance->remote_device_properties_changed_count_ = num_properties;
  semaphore_post(instance->remote_device_properties_callback_sem_);
}

void DiscoveryStateChangedCallback(bt_discovery_state_t state) {
  instance->discovery_state_ = state;
  semaphore_post(instance->discovery_state_changed_callback_sem_);
}

static bt_callbacks_t callbacks = {
    .size = sizeof(bt_callbacks_t),
    .adapter_state_changed_cb = AdapterStateChangedCallback,
    .adapter_properties_cb = AdapterPropertiesCallback,
    .remote_device_properties_cb = RemoteDevicePropertiesCallback,
    .discovery_state_changed_cb = DiscoveryStateChangedCallback,
};

void BluetoothTest::SetUp() {
  android::ProcessState::self()->startThreadPool();
  state_ = BT_STATE_OFF;
  properties_changed_count_ = 0;
  last_changed_properties_ = nullptr;
  remote_device_properties_changed_count_ = 0;
  remote_device_last_changed_properties_ = nullptr;
  discovery_state_ = BT_DISCOVERY_STOPPED;
  acl_state_ = BT_ACL_STATE_DISCONNECTED;
  bond_state_ = BT_BOND_STATE_NONE;

  remove("/data/misc/bluedroid/bt_config.conf.encrypted-checksum");
  remove("/data/misc/bluedroid/bt_config.bak.encrypted-checksum");

  instance = this;
  int status = bluetoothInterface.init(&callbacks, false, false, 0, nullptr,
                                       false, nullptr);
  ASSERT_EQ(status, BT_STATUS_SUCCESS);
}

void BluetoothTest::TearDown() {
  bluetoothInterface.cleanup();
  instance = nullptr;
}

void BluetoothTest::ClearSemaphore(btsemaphore& sem) {
  while (sem.try_wait())
    ;
}

const bt_interface_t* BluetoothTest::bt_interface() {
  return &bluetoothInterface;
}

bt_callbacks_t* BluetoothTest::bt_callbacks() { return &callbacks; }

bt_state_t BluetoothTest::GetState() { return state_; }

int BluetoothTest::GetPropertiesChangedCount() {
  return properties_changed_count_;
}

bt_property_t* BluetoothTest::GetProperty(bt_property_type_t type) {
  for (int i = 0; i < properties_changed_count_; ++i) {
    if (last_changed_properties_[i].type == type) {
      return &last_changed_properties_[i];
    }
  }
  return nullptr;
}

bt_property_t* BluetoothTest::GetRemoteDeviceProperty(const RawAddress* addr,
                                                      bt_property_type_t type) {
  if (curr_remote_device_ != *addr) return nullptr;

  for (int i = 0; i < remote_device_properties_changed_count_; i++) {
    if (remote_device_last_changed_properties_[i].type == type) {
      return &remote_device_last_changed_properties_[i];
    }
  }
  return nullptr;
}

bt_discovery_state_t BluetoothTest::GetDiscoveryState() {
  return discovery_state_;
}

bt_acl_state_t BluetoothTest::GetAclState() { return acl_state_; }

// Returns the device bond state.
bt_bond_state_t BluetoothTest::GetBondState() { return bond_state_; }

}  // namespace bttest
