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

#include "devices.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <map>

#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "btm_api_mock.h"
#include "gatt/database_builder.h"
#include "stack/include/bt_uuid16.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace vc {
namespace internal {

using ::testing::_;
using ::testing::DoAll;
using ::testing::Invoke;
using ::testing::Mock;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::Test;

RawAddress GetTestAddress(int index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {
      {0xC0, 0xDE, 0xC0, 0xDE, 0x00, static_cast<uint8_t>(index)}};
  return result;
}

class VolumeControlDevicesTest : public ::testing::Test {
 protected:
  void SetUp() override {
    devices_ = new VolumeControlDevices();
    gatt::SetMockBtaGattInterface(&gatt_interface);
    gatt::SetMockBtaGattQueue(&gatt_queue);
  }

  void TearDown() override {
    gatt::SetMockBtaGattQueue(nullptr);
    gatt::SetMockBtaGattInterface(nullptr);
    delete devices_;
  }

  VolumeControlDevices* devices_ = nullptr;
  gatt::MockBtaGattInterface gatt_interface;
  gatt::MockBtaGattQueue gatt_queue;
};

TEST_F(VolumeControlDevicesTest, test_add) {
  RawAddress test_address_0 = GetTestAddress(0);
  ASSERT_EQ((size_t)0, devices_->Size());
  devices_->Add(test_address_0, true);
  ASSERT_EQ((size_t)1, devices_->Size());
}

TEST_F(VolumeControlDevicesTest, test_add_twice) {
  RawAddress test_address_0 = GetTestAddress(0);
  ASSERT_EQ((size_t)0, devices_->Size());
  devices_->Add(test_address_0, true);
  devices_->Add(test_address_0, true);
  ASSERT_EQ((size_t)1, devices_->Size());
}

TEST_F(VolumeControlDevicesTest, test_remove) {
  RawAddress test_address_0 = GetTestAddress(0);
  RawAddress test_address_1 = GetTestAddress(1);
  devices_->Add(test_address_0, true);
  devices_->Add(test_address_1, true);
  ASSERT_EQ((size_t)2, devices_->Size());
  devices_->Remove(test_address_0);
  ASSERT_EQ((size_t)1, devices_->Size());
}

TEST_F(VolumeControlDevicesTest, test_clear) {
  RawAddress test_address_0 = GetTestAddress(0);
  ASSERT_EQ((size_t)0, devices_->Size());
  devices_->Add(test_address_0, true);
  ASSERT_EQ((size_t)1, devices_->Size());
  devices_->Clear();
  ASSERT_EQ((size_t)0, devices_->Size());
}

TEST_F(VolumeControlDevicesTest, test_find_by_address) {
  RawAddress test_address_0 = GetTestAddress(0);
  RawAddress test_address_1 = GetTestAddress(1);
  RawAddress test_address_2 = GetTestAddress(2);
  devices_->Add(test_address_0, true);
  devices_->Add(test_address_1, false);
  devices_->Add(test_address_2, true);
  VolumeControlDevice* device = devices_->FindByAddress(test_address_1);
  ASSERT_NE(nullptr, device);
  ASSERT_EQ(test_address_1, device->address);
}

TEST_F(VolumeControlDevicesTest, test_find_by_conn_id) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, true);
  VolumeControlDevice* test_device = devices_->FindByAddress(test_address_0);
  test_device->connection_id = 0x0005;
  ASSERT_NE(nullptr, devices_->FindByConnId(test_device->connection_id));
}

TEST_F(VolumeControlDevicesTest, test_disconnect) {
  RawAddress test_address_0 = GetTestAddress(0);
  RawAddress test_address_1 = GetTestAddress(1);
  devices_->Add(test_address_0, true);
  devices_->Add(test_address_1, true);
  VolumeControlDevice* test_device_0 = devices_->FindByAddress(test_address_0);
  test_device_0->connection_id = 0x0005;
  tGATT_IF gatt_if = 8;
  EXPECT_CALL(gatt_interface, Close(test_device_0->connection_id));
  devices_->Disconnect(gatt_if);
}

TEST_F(VolumeControlDevicesTest, test_control_point_operation) {
  uint8_t opcode = 50;
  std::vector<RawAddress> devices;

  for (int i = 5; i > 0; i--) {
    RawAddress test_address = GetTestAddress(i);
    devices.push_back(test_address);
    uint8_t change_counter = 10 * i;
    uint16_t control_point_handle = 0x0020 + i;
    uint16_t connection_id = i;
    devices_->Add(test_address, true);
    VolumeControlDevice* device = devices_->FindByAddress(test_address);
    device->connection_id = connection_id;
    device->change_counter = change_counter;
    device->volume_control_point_handle = control_point_handle;
    std::vector<uint8_t> data_expected({opcode, change_counter});

    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(connection_id, control_point_handle,
                                    data_expected, GATT_WRITE, _, _));
  }

  const std::vector<uint8_t>* arg = nullptr;
  GATT_WRITE_OP_CB cb = nullptr;
  void* cb_data = nullptr;
  devices_->ControlPointOperation(devices, opcode, arg, cb, cb_data);
}

TEST_F(VolumeControlDevicesTest, test_control_point_operation_args) {
  uint8_t opcode = 60;
  uint8_t arg_1 = 0x02;
  uint8_t arg_2 = 0x05;
  std::vector<RawAddress> devices;

  for (int i = 5; i > 0; i--) {
    RawAddress test_address = GetTestAddress(i);
    devices.push_back(test_address);
    uint8_t change_counter = 10 * i;
    uint16_t control_point_handle = 0x0020 + i;
    uint16_t connection_id = i;
    devices_->Add(test_address, true);
    VolumeControlDevice* device = devices_->FindByAddress(test_address);
    device->connection_id = connection_id;
    device->change_counter = change_counter;
    device->volume_control_point_handle = control_point_handle;
    std::vector<uint8_t> data_expected({opcode, change_counter, arg_1, arg_2});

    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(connection_id, control_point_handle,
                                    data_expected, GATT_WRITE, _, _));
  }

  std::vector<uint8_t> arg({arg_1, arg_2});
  GATT_WRITE_OP_CB cb = nullptr;
  void* cb_data = nullptr;
  devices_->ControlPointOperation(devices, opcode, &arg, cb, cb_data);
}

TEST_F(VolumeControlDevicesTest, test_control_point_skip_not_connected) {
  RawAddress test_address = GetTestAddress(1);
  devices_->Add(test_address, true);
  VolumeControlDevice* device = devices_->FindByAddress(test_address);
  device->connection_id = GATT_INVALID_CONN_ID;
  uint16_t control_point_handle = 0x0020;
  device->volume_control_point_handle = control_point_handle;

  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(_, control_point_handle, _, _, _, _))
      .Times(0);

  uint8_t opcode = 5;
  std::vector<RawAddress> devices = {test_address};
  const std::vector<uint8_t>* arg = nullptr;
  GATT_WRITE_OP_CB cb = nullptr;
  void* cb_data = nullptr;
  devices_->ControlPointOperation(devices, opcode, arg, cb, cb_data);
}

class VolumeControlDeviceTest : public ::testing::Test {
 protected:
  void SetUp() override {
    device = new VolumeControlDevice(GetTestAddress(1), true);
    gatt::SetMockBtaGattInterface(&gatt_interface);
    gatt::SetMockBtaGattQueue(&gatt_queue);
    bluetooth::manager::SetMockBtmInterface(&btm_interface);

    ON_CALL(gatt_interface, GetCharacteristic(_, _))
        .WillByDefault(
            Invoke([&](uint16_t conn_id,
                       uint16_t handle) -> const gatt::Characteristic* {
              for (auto const& service : services) {
                for (auto const& characteristic : service.characteristics) {
                  if (characteristic.value_handle == handle) {
                    return &characteristic;
                  }
                }
              }

              return nullptr;
            }));

    ON_CALL(gatt_interface, GetOwningService(_, _))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id, uint16_t handle) -> const gatt::Service* {
              for (auto const& service : services) {
                if (service.handle <= handle && service.end_handle >= handle) {
                  return &service;
                }
              }

              return nullptr;
            }));

    ON_CALL(gatt_interface, GetServices(_)).WillByDefault(Return(&services));
  }

  void TearDown() override {
    bluetooth::manager::SetMockBtmInterface(nullptr);
    gatt::SetMockBtaGattQueue(nullptr);
    gatt::SetMockBtaGattInterface(nullptr);
    delete device;
  }

  /* sample database 1xVCS, 2xAICS, 2xVOCS */
  void SetSampleDatabase1(void) {
    gatt::DatabaseBuilder builder;
    builder.AddService(0x0001, 0x0016, kVolumeControlUuid, true);
    builder.AddIncludedService(0x0004, kVolumeOffsetUuid, 0x0060, 0x0069);
    builder.AddIncludedService(0x0005, kVolumeOffsetUuid, 0x0080, 0x008b);
    builder.AddCharacteristic(
        0x0010, 0x0011, kVolumeControlStateUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0012,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0013, 0x0014, kVolumeControlPointUuid,
                              GATT_CHAR_PROP_BIT_WRITE);
    builder.AddCharacteristic(0x0015, 0x0016, kVolumeFlagsUuid,
                              GATT_CHAR_PROP_BIT_READ);
    builder.AddService(0x0060, 0x0069, kVolumeOffsetUuid, false);
    builder.AddCharacteristic(
        0x0061, 0x0062, kVolumeOffsetStateUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0063,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0064, 0x0065, kVolumeOffsetLocationUuid,
                              GATT_CHAR_PROP_BIT_READ);
    builder.AddCharacteristic(0x0066, 0x0067, kVolumeOffsetControlPointUuid,
                              GATT_CHAR_PROP_BIT_WRITE);
    builder.AddCharacteristic(0x0068, 0x0069,
                              kVolumeOffsetOutputDescriptionUuid,
                              GATT_CHAR_PROP_BIT_READ);
    builder.AddService(0x0080, 0x008b, kVolumeOffsetUuid, false);
    builder.AddCharacteristic(
        0x0081, 0x0082, kVolumeOffsetStateUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0083,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0084, 0x0085, kVolumeOffsetLocationUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_WRITE_NR |
                                  GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0086,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0087, 0x0088, kVolumeOffsetControlPointUuid,
                              GATT_CHAR_PROP_BIT_WRITE);
    builder.AddCharacteristic(
        0x0089, 0x008a, kVolumeOffsetOutputDescriptionUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_WRITE_NR |
            GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x008b,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddService(0x00a0, 0x00a3,
                       Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
    builder.AddCharacteristic(0x00a1, 0x00a2,
                              Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                              GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x00a3,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    services = builder.Build().Services();
    ASSERT_EQ(true, device->UpdateHandles());
  }

  /* sample database no VCS */
  void SetSampleDatabase2(void) {
    gatt::DatabaseBuilder builder;
    builder.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    builder.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                              GATT_CHAR_PROP_BIT_READ);
    services = builder.Build().Services();
    ASSERT_EQ(false, device->UpdateHandles());
  }

  VolumeControlDevice* device = nullptr;
  gatt::MockBtaGattInterface gatt_interface;
  gatt::MockBtaGattQueue gatt_queue;
  bluetooth::manager::MockBtmInterface btm_interface;
  std::list<gatt::Service> services;
};

TEST_F(VolumeControlDeviceTest, test_service_volume_control_not_found) {
  SetSampleDatabase2();
  ASSERT_EQ(false, device->HasHandles());
}

TEST_F(VolumeControlDeviceTest, test_service_volume_control_incomplete) {
  gatt::DatabaseBuilder builder;
  builder.AddService(0x0001, 0x0006, kVolumeControlUuid, true);
  builder.AddCharacteristic(
      0x0002, 0x0003, kVolumeControlStateUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x0004, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(0x0005, 0x0006, kVolumeControlPointUuid,
                            GATT_CHAR_PROP_BIT_WRITE);
  /* no Volume Control Flags characteristic */
  services = builder.Build().Services();
  ASSERT_EQ(false, device->UpdateHandles());
  ASSERT_EQ(0x0000, device->volume_state_handle);
  ASSERT_EQ(0x0000, device->volume_state_ccc_handle);
  ASSERT_EQ(0x0000, device->volume_control_point_handle);
  ASSERT_EQ(0x0000, device->volume_flags_handle);
  ASSERT_EQ(0x0000, device->volume_flags_ccc_handle);
  ASSERT_EQ(false, device->HasHandles());
}

TEST_F(VolumeControlDeviceTest, test_service_vocs_incomplete) {
  gatt::DatabaseBuilder builder;
  builder.AddService(0x0001, 0x000a, kVolumeControlUuid, true);
  builder.AddIncludedService(0x0002, kVolumeOffsetUuid, 0x000b, 0x0013);
  builder.AddCharacteristic(
      0x0003, 0x0004, kVolumeControlStateUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x0005, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(0x0006, 0x0007, kVolumeControlPointUuid,
                            GATT_CHAR_PROP_BIT_WRITE);
  builder.AddCharacteristic(
      0x0008, 0x0009, kVolumeFlagsUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x000a, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddService(0x000b, 0x0013, kVolumeOffsetUuid, false);
  builder.AddCharacteristic(
      0x000c, 0x000d, kVolumeOffsetStateUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x000e, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(
      0x000f, 0x0010, kVolumeOffsetLocationUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x0011, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(0x0012, 0x0013, kVolumeOffsetControlPointUuid,
                            GATT_CHAR_PROP_BIT_WRITE);
  /* no Audio Output Description characteristic */
  services = builder.Build().Services();
  ASSERT_EQ(true, device->UpdateHandles());
  ASSERT_EQ((size_t)0, device->audio_offsets.Size());
  ASSERT_EQ(0x0004, device->volume_state_handle);
  ASSERT_EQ(0x0005, device->volume_state_ccc_handle);
  ASSERT_EQ(0x0007, device->volume_control_point_handle);
  ASSERT_EQ(0x0009, device->volume_flags_handle);
  ASSERT_EQ(0x000a, device->volume_flags_ccc_handle);
  ASSERT_EQ(true, device->HasHandles());
}

TEST_F(VolumeControlDeviceTest, test_service_vocs_found) {
  gatt::DatabaseBuilder builder;
  builder.AddService(0x0001, 0x000a, kVolumeControlUuid, true);
  builder.AddIncludedService(0x0002, kVolumeOffsetUuid, 0x000b, 0x0015);
  builder.AddCharacteristic(
      0x0003, 0x0004, kVolumeControlStateUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x0005, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(0x0006, 0x0007, kVolumeControlPointUuid,
                            GATT_CHAR_PROP_BIT_WRITE);
  builder.AddCharacteristic(
      0x0008, 0x0009, kVolumeFlagsUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x000a, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddService(0x000b, 0x0015, kVolumeOffsetUuid, false);
  builder.AddCharacteristic(
      0x000c, 0x000d, kVolumeOffsetStateUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x000e, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(
      0x000f, 0x0010, kVolumeOffsetLocationUuid,
      GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
  builder.AddDescriptor(0x0011, Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
  builder.AddCharacteristic(0x0012, 0x0013, kVolumeOffsetControlPointUuid,
                            GATT_CHAR_PROP_BIT_WRITE);
  builder.AddCharacteristic(0x0014, 0x0015, kVolumeOffsetOutputDescriptionUuid,
                            GATT_CHAR_PROP_BIT_READ);
  services = builder.Build().Services();
  ASSERT_EQ(true, device->UpdateHandles());
  ASSERT_EQ((size_t)1, device->audio_offsets.Size());
  VolumeOffset* offset = device->audio_offsets.FindByServiceHandle(0x000b);
  ASSERT_NE(nullptr, offset);
  ASSERT_EQ(0x000d, offset->state_handle);
  ASSERT_EQ(0x000e, offset->state_ccc_handle);
  ASSERT_EQ(0x0010, offset->audio_location_handle);
  ASSERT_EQ(0x0011, offset->audio_location_ccc_handle);
  ASSERT_EQ(0x0013, offset->control_point_handle);
  ASSERT_EQ(0x0015, offset->audio_descr_handle);
  ASSERT_EQ(0x0000, offset->audio_descr_ccc_handle);
  ASSERT_EQ(true, device->HasHandles());
}

TEST_F(VolumeControlDeviceTest, test_multiple_services_found) {
  SetSampleDatabase1();
  ASSERT_EQ((size_t)2, device->audio_offsets.Size());
  VolumeOffset* offset_1 = device->audio_offsets.FindById(1);
  VolumeOffset* offset_2 = device->audio_offsets.FindById(2);
  ASSERT_NE(nullptr, offset_1);
  ASSERT_NE(nullptr, offset_2);
  ASSERT_NE(offset_1->service_handle, offset_2->service_handle);
}

TEST_F(VolumeControlDeviceTest, test_services_changed) {
  SetSampleDatabase1();
  ASSERT_NE((size_t)0, device->audio_offsets.Size());
  ASSERT_NE(0, device->volume_state_handle);
  ASSERT_NE(0, device->volume_control_point_handle);
  ASSERT_NE(0, device->volume_flags_handle);
  ASSERT_EQ(true, device->HasHandles());
  SetSampleDatabase2();
  ASSERT_EQ((size_t)0, device->audio_offsets.Size());
  ASSERT_EQ(0, device->volume_state_handle);
  ASSERT_EQ(0, device->volume_control_point_handle);
  ASSERT_EQ(0, device->volume_flags_handle);
  ASSERT_EQ(false, device->HasHandles());
}

TEST_F(VolumeControlDeviceTest, test_enqueue_initial_requests) {
  SetSampleDatabase1();

  tGATT_IF gatt_if = 0x0001;
  std::vector<uint8_t> register_for_notification_data({0x01, 0x00});

  std::map<uint16_t, uint16_t> expected_to_read_write{
      {0x0011, 0x0012} /* volume control state */,
      {0x0062, 0x0063} /* volume offset state 1 */,
      {0x0082, 0x0083} /* volume offset state 2 */};

  for (auto const& handle_pair : expected_to_read_write) {
    EXPECT_CALL(gatt_queue, ReadCharacteristic(_, handle_pair.first, _, _));
    EXPECT_CALL(gatt_queue, WriteDescriptor(_, handle_pair.second,
                                            register_for_notification_data,
                                            GATT_WRITE, _, _));
    EXPECT_CALL(gatt_interface,
                RegisterForNotifications(gatt_if, _, handle_pair.first));
  }

  auto chrc_read_cb = [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                         uint16_t len, uint8_t* value, void* data) {};
  auto cccd_write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                          uint16_t handle, uint16_t len, const uint8_t* value,
                          void* data) {};
  ASSERT_EQ(true, device->EnqueueInitialRequests(gatt_if, chrc_read_cb,
                                                 cccd_write_cb));
};

TEST_F(VolumeControlDeviceTest, test_device_ready) {
  SetSampleDatabase1();

  // grab all the handles requested
  std::vector<uint16_t> requested_handles;
  ON_CALL(gatt_queue, WriteDescriptor(_, _, _, _, _, _))
      .WillByDefault(Invoke(
          [&requested_handles](
              uint16_t conn_id, uint16_t handle, std::vector<uint8_t> value,
              tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
              void* cb_data) -> void { requested_handles.push_back(handle); }));
  ON_CALL(gatt_queue, ReadCharacteristic(_, _, _, _))
      .WillByDefault(Invoke(
          [&requested_handles](uint16_t conn_id, uint16_t handle,
                               GATT_READ_OP_CB cb, void* cb_data) -> void {
            requested_handles.push_back(handle);
          }));

  auto chrc_read_cb = [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                         uint16_t len, uint8_t* value, void* data) {};
  auto cccd_write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                          uint16_t handle, uint16_t len, const uint8_t* value,
                          void* data) {};
  ASSERT_EQ(true, device->EnqueueInitialRequests(0x0001, chrc_read_cb,
                                                 cccd_write_cb));
  ASSERT_NE((size_t)0, requested_handles.size());

  // indicate non-pending requests
  ASSERT_EQ(false, device->device_ready);
  device->VerifyReady(0xffff);

  for (uint16_t handle : requested_handles) {
    ASSERT_EQ(false, device->device_ready);
    device->VerifyReady(handle);
  }

  ASSERT_EQ(true, device->device_ready);
}

TEST_F(VolumeControlDeviceTest, test_enqueue_remaining_requests) {
  SetSampleDatabase1();

  tGATT_IF gatt_if = 0x0001;
  std::vector<uint8_t> register_for_notification_data({0x01, 0x00});

  std::vector<uint16_t> expected_to_read{
      0x0016 /* volume flags */, 0x0065 /* audio location 1 */,
      0x0069 /* audio output description 1 */, 0x0085 /* audio location 1 */,
      0x008a /* audio output description 1 */};

  std::map<uint16_t, uint16_t> expected_to_write_value_ccc_handle_map{
      {0x0085, 0x0086} /* audio location ccc 2 */,
      {0x008a, 0x008b} /* audio output description ccc */
  };

  for (uint16_t handle : expected_to_read) {
    EXPECT_CALL(gatt_queue, ReadCharacteristic(_, handle, _, _));
  }

  for (auto const& handle_pair : expected_to_write_value_ccc_handle_map) {
    EXPECT_CALL(gatt_queue, WriteDescriptor(_, handle_pair.second,
                                            register_for_notification_data,
                                            GATT_WRITE, _, _));
    EXPECT_CALL(gatt_interface,
                RegisterForNotifications(gatt_if, _, handle_pair.first));
  }

  auto chrc_read_cb = [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                         uint16_t len, uint8_t* value, void* data) {};
  auto cccd_write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                          uint16_t handle, uint16_t len, const uint8_t* value,
                          void* data) {};
  device->EnqueueRemainingRequests(gatt_if, chrc_read_cb, cccd_write_cb);
}

TEST_F(VolumeControlDeviceTest, test_check_link_encrypted) {
  ON_CALL(btm_interface, BTM_IsEncrypted(_, _))
      .WillByDefault(DoAll(Return(true)));
  ASSERT_EQ(true, device->IsEncryptionEnabled());

  ON_CALL(btm_interface, BTM_IsEncrypted(_, _))
      .WillByDefault(DoAll(Return(false)));
  ASSERT_NE(true, device->IsEncryptionEnabled());
}

TEST_F(VolumeControlDeviceTest, test_control_point_operation) {
  GATT_WRITE_OP_CB write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                                 uint16_t handle, uint16_t len,
                                 const uint8_t* value, void* data) {};
  SetSampleDatabase1();
  device->change_counter = 0x01;
  std::vector<uint8_t> expected_data({0x03, 0x01});
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, 0x0014, expected_data,
                                              GATT_WRITE, write_cb, nullptr));
  device->ControlPointOperation(0x03, nullptr, write_cb, nullptr);
}

TEST_F(VolumeControlDeviceTest, test_control_point_operation_arg) {
  GATT_WRITE_OP_CB write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                                 uint16_t handle, uint16_t len,
                                 const uint8_t* value, void* data) {};
  SetSampleDatabase1();
  device->change_counter = 0x55;
  std::vector<uint8_t> expected_data({0x01, 0x55, 0x02, 0x03});
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, 0x0014, expected_data,
                                              GATT_WRITE, write_cb, nullptr));
  std::vector<uint8_t> arg({0x02, 0x03});
  device->ControlPointOperation(0x01, &arg, write_cb, nullptr);
}

TEST_F(VolumeControlDeviceTest, test_get_ext_audio_out_volume_offset) {
  GATT_READ_OP_CB read_cb = [](uint16_t conn_id, tGATT_STATUS status,
                               uint16_t handle, uint16_t len, uint8_t* value,
                               void* data) {};
  SetSampleDatabase1();
  EXPECT_CALL(gatt_queue, ReadCharacteristic(_, 0x0062, read_cb, nullptr));
  device->GetExtAudioOutVolumeOffset(1, read_cb, nullptr);
}

TEST_F(VolumeControlDeviceTest, test_get_ext_audio_out_location) {
  GATT_READ_OP_CB read_cb = [](uint16_t conn_id, tGATT_STATUS status,
                               uint16_t handle, uint16_t len, uint8_t* value,
                               void* data) {};
  SetSampleDatabase1();
  EXPECT_CALL(gatt_queue, ReadCharacteristic(_, 0x0085, read_cb, nullptr));
  device->GetExtAudioOutLocation(2, read_cb, nullptr);
}

TEST_F(VolumeControlDeviceTest, test_set_ext_audio_out_location) {
  SetSampleDatabase1();
  std::vector<uint8_t> expected_data({0x44, 0x33, 0x22, 0x11});
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(_, 0x0085, expected_data, GATT_WRITE_NO_RSP,
                                  nullptr, nullptr));
  device->SetExtAudioOutLocation(2, 0x11223344);
}

TEST_F(VolumeControlDeviceTest, test_set_ext_audio_out_location_non_writable) {
  SetSampleDatabase1();
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _)).Times(0);
  device->SetExtAudioOutLocation(1, 0x11223344);
}

TEST_F(VolumeControlDeviceTest, test_get_ext_audio_out_description) {
  GATT_READ_OP_CB read_cb = [](uint16_t conn_id, tGATT_STATUS status,
                               uint16_t handle, uint16_t len, uint8_t* value,
                               void* data) {};
  SetSampleDatabase1();
  EXPECT_CALL(gatt_queue, ReadCharacteristic(_, 0x008a, read_cb, nullptr));
  device->GetExtAudioOutDescription(2, read_cb, nullptr);
}

TEST_F(VolumeControlDeviceTest, test_set_ext_audio_out_description) {
  SetSampleDatabase1();
  std::string descr = "right front";
  std::vector<uint8_t> expected_data(descr.begin(), descr.end());
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(_, 0x008a, expected_data, GATT_WRITE_NO_RSP,
                                  nullptr, nullptr));
  device->SetExtAudioOutDescription(2, descr);
}

TEST_F(VolumeControlDeviceTest,
       test_set_ext_audio_out_description_non_writable) {
  SetSampleDatabase1();
  std::string descr = "left front";
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _)).Times(0);
  device->SetExtAudioOutDescription(1, descr);
}

TEST_F(VolumeControlDeviceTest, test_ext_audio_out_control_point_operation) {
  GATT_WRITE_OP_CB write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                                 uint16_t handle, uint16_t len,
                                 const uint8_t* value, void* data) {};
  SetSampleDatabase1();
  VolumeOffset* offset = device->audio_offsets.FindById(1);
  ASSERT_NE(nullptr, offset);
  offset->change_counter = 0x09;
  std::vector<uint8_t> expected_data({0x0b, 0x09});
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, 0x0067, expected_data,
                                              GATT_WRITE, write_cb, nullptr));
  device->ExtAudioOutControlPointOperation(1, 0x0b, nullptr, write_cb, nullptr);
}

TEST_F(VolumeControlDeviceTest,
       test_ext_audio_out_control_point_operation_arg) {
  GATT_WRITE_OP_CB write_cb = [](uint16_t conn_id, tGATT_STATUS status,
                                 uint16_t handle, uint16_t len,
                                 const uint8_t* value, void* data) {};
  SetSampleDatabase1();
  VolumeOffset* offset = device->audio_offsets.FindById(1);
  ASSERT_NE(nullptr, offset);
  offset->change_counter = 0x09;
  std::vector<uint8_t> expected_data({0x0b, 0x09, 0x01, 0x02, 0x03, 0x04});
  std::vector<uint8_t> arg({0x01, 0x02, 0x03, 0x04});
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, 0x0067, expected_data,
                                              GATT_WRITE, write_cb, nullptr));
  device->ExtAudioOutControlPointOperation(1, 0x0b, &arg, write_cb, nullptr);
}

}  // namespace internal
}  // namespace vc
}  // namespace bluetooth
