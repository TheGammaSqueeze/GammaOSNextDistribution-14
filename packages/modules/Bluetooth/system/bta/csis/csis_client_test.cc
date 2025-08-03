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

#include <base/functional/bind.h>
#include <bluetooth/log.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <vector>

#include "bind_helpers.h"
#include "bta_csis_api.h"
#include "bta_dm_api_mock.h"
#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "bta_le_audio_uuids.h"
#include "btif/include/btif_profile_storage.h"
#include "btm_api_mock.h"
#include "csis_types.h"
#include "gatt/database_builder.h"
#include "hardware/bt_gatt_types.h"
#include "stack/include/bt_uuid16.h"
#include "test/common/mock_functions.h"

bool gatt_cl_read_sirk_req(
    const RawAddress& peer_bda,
    base::OnceCallback<void(tGATT_STATUS status, const RawAddress&,
                            uint8_t sirk_type, Octet16& sirk)>
        cb) {
  return true;
}

namespace bluetooth {
namespace csis {
namespace internal {
namespace {

using base::Bind;
using base::Closure;
using base::Unretained;

using bluetooth::csis::ConnectionState;
using bluetooth::csis::CsisClient;
using bluetooth::csis::CsisClientCallbacks;
using bluetooth::csis::CsisClientInterface;
using bluetooth::csis::CsisGroupLockStatus;
using bluetooth::groups::DeviceGroups;

using testing::_;
using testing::AtLeast;
using testing::DoAll;
using testing::DoDefault;
using testing::Invoke;
using testing::Mock;
using testing::NotNull;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::WithArg;

// Disables most likely false-positives from base::SplitString()
extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

RawAddress GetTestAddress(int index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {
      {0xC0, 0xDE, 0xC0, 0xDE, 0x00, static_cast<uint8_t>(index)}};
  return result;
}

/* Csis lock callback */
class MockCsisLockCallback {
 public:
  MockCsisLockCallback() = default;
  MockCsisLockCallback(const MockCsisLockCallback&) = delete;
  MockCsisLockCallback& operator=(const MockCsisLockCallback&) = delete;

  ~MockCsisLockCallback() = default;
  MOCK_METHOD((void), CsisGroupLockCb,
              (int group_id, bool locked, CsisGroupLockStatus status));
};

static MockCsisLockCallback* csis_lock_callback_mock;

void SetMockCsisLockCallback(MockCsisLockCallback* mock) {
  csis_lock_callback_mock = mock;
}

/* Csis callbacks to JNI */
class MockCsisCallbacks : public CsisClientCallbacks {
 public:
  MockCsisCallbacks() = default;
  MockCsisCallbacks(const MockCsisCallbacks&) = delete;
  MockCsisCallbacks& operator=(const MockCsisCallbacks&) = delete;

  ~MockCsisCallbacks() override = default;

  MOCK_METHOD((void), OnConnectionState,
              (const RawAddress& address, ConnectionState state), (override));
  MOCK_METHOD((void), OnDeviceAvailable,
              (const RawAddress& address, int group_id, int group_size,
               int rank, const bluetooth::Uuid& uuid),
              (override));
  MOCK_METHOD((void), OnSetMemberAvailable,
              (const RawAddress& address, int group_id), (override));
  MOCK_METHOD((void), OnGroupLockChanged,
              (int group_id, bool locked,
               bluetooth::csis::CsisGroupLockStatus status),
              (override));
  MOCK_METHOD((void), OnGattCsisWriteLockRsp,
              (uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
               void* data));
};

class CsisClientTest : public ::testing::Test {
 private:
  void set_sample_cap_included_database(uint16_t conn_id, bool csis,
                                        bool csis_broken, uint8_t rank,
                                        uint8_t sirk_msb = 1) {
    gatt::DatabaseBuilder builder;
    builder.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    builder.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                              GATT_CHAR_PROP_BIT_READ);
    if (csis) {
      builder.AddService(0x0005, 0x0009,
                         bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE),
                         true);
      builder.AddIncludedService(0x0006, kCsisServiceUuid, 0x0010, 0x0030);

      builder.AddService(0x0010, 0x0030, kCsisServiceUuid, true);
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(
          0x0023, 0x0024, kCsisSizeUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
      builder.AddDescriptor(0x0025,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(0x0026, 0x0027, kCsisLockUuid,
                                GATT_CHAR_PROP_BIT_READ |
                                    GATT_CHAR_PROP_BIT_NOTIFY |
                                    GATT_CHAR_PROP_BIT_WRITE);
      builder.AddDescriptor(0x0028,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(0x0029, 0x0030, kCsisRankUuid,
                                GATT_CHAR_PROP_BIT_READ);
    }
    if (csis_broken) {
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    }
    builder.AddService(0x0090, 0x0093,
                       Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
    builder.AddCharacteristic(0x0091, 0x0092,
                              Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                              GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0093,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    services_map[conn_id] = builder.Build().Services();

    ON_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillByDefault(
            Invoke([rank, sirk_msb](uint16_t conn_id, uint16_t handle,
                                    GATT_READ_OP_CB cb, void* cb_data) -> void {
              std::vector<uint8_t> value;

              switch (handle) {
                case 0x0003:
                  /* device name */
                  value.resize(20);
                  break;
                case 0x0021:
                  value.assign(17, 1);
                  value[16] = sirk_msb;
                  break;
                case 0x0024:
                  value.resize(1);
                  break;
                case 0x0027:
                  value.resize(1);
                  break;
                case 0x0030:
                  value.resize(1);
                  value.assign(1, rank);
                  break;
                default:
                  FAIL();
                  return;
              }

              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
            }));
  }

  void set_sample_database(uint16_t conn_id, bool csis, bool csis_broken,
                           uint8_t rank, uint8_t sirk_msb = 1) {
    gatt::DatabaseBuilder builder;
    builder.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    builder.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                              GATT_CHAR_PROP_BIT_READ);
    if (csis) {
      builder.AddService(0x0010, 0x0030, kCsisServiceUuid, true);
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(
          0x0023, 0x0024, kCsisSizeUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
      builder.AddDescriptor(0x0025,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(0x0026, 0x0027, kCsisLockUuid,
                                GATT_CHAR_PROP_BIT_READ |
                                    GATT_CHAR_PROP_BIT_NOTIFY |
                                    GATT_CHAR_PROP_BIT_WRITE);
      builder.AddDescriptor(0x0028,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(0x0029, 0x0030, kCsisRankUuid,
                                GATT_CHAR_PROP_BIT_READ);
    }
    if (csis_broken) {
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    }
    builder.AddService(0x0090, 0x0093,
                       Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
    builder.AddCharacteristic(0x0091, 0x0092,
                              Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                              GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0093,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    services_map[conn_id] = builder.Build().Services();

    ON_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillByDefault(
            Invoke([rank, sirk_msb](uint16_t conn_id, uint16_t handle,
                                    GATT_READ_OP_CB cb, void* cb_data) -> void {
              std::vector<uint8_t> value;

              switch (handle) {
                case 0x0003:
                  /* device name */
                  value.resize(20);
                  break;
                case 0x0021:
                  value.assign(17, 1);
                  value[16] = sirk_msb;
                  break;
                case 0x0024:
                  value.resize(1);
                  break;
                case 0x0027:
                  value.resize(1);
                  break;
                case 0x0030:
                  value.resize(1);
                  value.assign(1, rank);
                  break;
                default:
                  ASSERT_TRUE(false);
                  return;
              }

              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
            }));
  }

  void set_sample_database_double_csis(uint16_t conn_id, uint8_t rank_1,
                                       uint8_t rank_2, bool broken,
                                       uint8_t sirk1_infill = 1,
                                       uint8_t sirk2_infill = 2) {
    gatt::DatabaseBuilder builder;

    builder.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    builder.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                              GATT_CHAR_PROP_BIT_READ);
    builder.AddService(0x0010, 0x0026, bluetooth::Uuid::From16Bit(0x1850),
                       true);
    builder.AddIncludedService(0x0011, kCsisServiceUuid, 0x0031, 0x0041);
    builder.AddCharacteristic(
        0x0031, 0x0032, kCsisSirkUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

    builder.AddDescriptor(0x0033,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(
        0x0034, 0x0035, kCsisSizeUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0036,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0037, 0x0038, kCsisLockUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_NOTIFY |
                                  GATT_CHAR_PROP_BIT_WRITE);
    builder.AddDescriptor(0x0039,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0040, 0x0041, kCsisRankUuid,
                              GATT_CHAR_PROP_BIT_READ);

    if (broken) {
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    }

    builder.AddService(0x0042, 0x0044, bluetooth::Uuid::From16Bit(0x1860),
                       true);
    builder.AddIncludedService(0x0043, kCsisServiceUuid, 0x0045, 0x0055);

    builder.AddCharacteristic(
        0x0045, 0x0046, kCsisSirkUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

    builder.AddDescriptor(0x0047,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(
        0x0048, 0x0049, kCsisSizeUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0050,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0051, 0x0052, kCsisLockUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_NOTIFY |
                                  GATT_CHAR_PROP_BIT_WRITE);
    builder.AddDescriptor(0x0053,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0054, 0x0055, kCsisRankUuid,
                              GATT_CHAR_PROP_BIT_READ);

    builder.AddService(0x0090, 0x0093,
                       Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
    builder.AddCharacteristic(0x0091, 0x0092,
                              Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                              GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0093,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    services_map[conn_id] = builder.Build().Services();

    ON_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillByDefault(Invoke([sirk1_infill, sirk2_infill, rank_1, rank_2](
                                  uint16_t conn_id, uint16_t handle,
                                  GATT_READ_OP_CB cb, void* cb_data) -> void {
          std::vector<uint8_t> value;

          switch (handle) {
            case 0x0003:
              /* device name */
              value.resize(20);
              break;
            case 0x0032:
              value.resize(17);
              value.assign(17, sirk1_infill);
              value[0] = 1;  // Plain text SIRK
              break;
            case 0x0035:
              value.resize(1);
              value.assign(1, 2);
              break;
            case 0x0038:
              value.resize(1);
              break;
            case 0x0041:
              value.resize(1);
              value.assign(1, rank_1);
              break;
            case 0x0046:
              value.resize(17);
              value.assign(17, sirk2_infill);
              value[0] = 1;  // Plain text SIRK
              break;
            case 0x0049:
              value.resize(1);
              value.assign(1, 2);
              break;
            case 0x0052:
              value.resize(1);
              break;
            case 0x0055:
              value.resize(1);
              value.assign(1, rank_2);
              break;
            default:
              log::error("Unknown handle? {}", handle);
              ASSERT_TRUE(false);
              return;
          }

          cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
             cb_data);
        }));
  }

 protected:
  void SetUp(void) override {
    reset_mock_function_count_map();
    bluetooth::manager::SetMockBtmInterface(&btm_interface);
    dm::SetMockBtaDmInterface(&dm_interface);
    gatt::SetMockBtaGattInterface(&gatt_interface);
    gatt::SetMockBtaGattQueue(&gatt_queue);
    SetMockCsisLockCallback(&csis_lock_cb);
    callbacks.reset(new MockCsisCallbacks());

    ON_CALL(btm_interface, BTM_IsEncrypted(_, _))
        .WillByDefault(DoAll(Return(true)));

    ON_CALL(gatt_interface, GetCharacteristic(_, _))
        .WillByDefault(
            Invoke([&](uint16_t conn_id,
                       uint16_t handle) -> const gatt::Characteristic* {
              std::list<gatt::Service>& services = services_map[conn_id];
              for (auto const& service : services) {
                for (auto const& characteristic : service.characteristics) {
                  if (characteristic.value_handle == handle) {
                    return &characteristic;
                  }
                }
              }

              return nullptr;
            }));

    // default action for GetOwningService function call
    ON_CALL(gatt_interface, GetOwningService(_, _))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id, uint16_t handle) -> const gatt::Service* {
              std::list<gatt::Service>& services = services_map[conn_id];
              for (auto const& service : services) {
                if (service.handle <= handle && service.end_handle >= handle) {
                  return &service;
                }
              }

              return nullptr;
            }));

    // default action for GetServices function call
    ON_CALL(gatt_interface, GetServices(_))
        .WillByDefault(WithArg<0>(
            Invoke([&](uint16_t conn_id) -> std::list<gatt::Service>* {
              return &services_map[conn_id];
            })));

    // default action for RegisterForNotifications function call
    ON_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for DeregisterForNotifications function call
    ON_CALL(gatt_interface, DeregisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for WriteDescriptor function call
    ON_CALL(gatt_queue, WriteDescriptor(_, _, _, _, _, _))
        .WillByDefault(
            Invoke([](uint16_t conn_id, uint16_t handle,
                      std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                      GATT_WRITE_OP_CB cb, void* cb_data) -> void {
              if (cb)
                cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                   cb_data);
            }));
  }

  void TearDown(void) override {
    services_map.clear();
    callbacks.reset();
    CsisClient::CleanUp();
    gatt::SetMockBtaGattInterface(nullptr);
    bluetooth::manager::SetMockBtmInterface(nullptr);
  }

  void TestAppRegister(void) {
    BtaAppRegisterCallback app_register_callback;
    EXPECT_CALL(gatt_interface, AppRegister(_, _, _))
        .WillOnce(DoAll(SaveArg<0>(&gatt_callback),
                        SaveArg<1>(&app_register_callback)));
    CsisClient::Initialize(callbacks.get(),
                           Bind(&btif_storage_load_bonded_csis_devices));
    ASSERT_TRUE(gatt_callback);
    ASSERT_TRUE(app_register_callback);
    app_register_callback.Run(gatt_if, GATT_SUCCESS);
    ASSERT_TRUE(CsisClient::IsCsisClientRunning());
  }

  void TestAppUnregister(void) {
    EXPECT_CALL(gatt_interface, AppDeregister(gatt_if));
    CsisClient::CleanUp();
    ASSERT_FALSE(CsisClient::IsCsisClientRunning());
    gatt_callback = nullptr;
  }

  void TestConnect(const RawAddress& address, bool encrypted = true) {
    // by default indicate link as encrypted
    ON_CALL(btm_interface, GetSecurityFlagsByTransport(address, NotNull(), _))
        .WillByDefault(
            DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(encrypted)));

    EXPECT_CALL(gatt_interface,
                Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, _));
    CsisClient::Get()->Connect(address);
    Mock::VerifyAndClearExpectations(&gatt_interface);
    Mock::VerifyAndClearExpectations(&btm_interface);
  }

  void TestDisconnect(const RawAddress& address, uint16_t conn_id) {
    if (conn_id != GATT_INVALID_CONN_ID) {
      EXPECT_CALL(gatt_interface, Close(conn_id));
      EXPECT_CALL(*callbacks, OnConnectionState(test_address,
                                                ConnectionState::DISCONNECTED));
    } else {
      EXPECT_CALL(gatt_interface, CancelOpen(_, address, _));
    }
    CsisClient::Get()->Disconnect(address);
  }

  void TestAddFromStorage(const RawAddress& address, uint16_t conn_id,
                          std::vector<uint8_t>& storage_group_buf,
                          std::vector<uint8_t>& storage_buf) {
    EXPECT_CALL(*callbacks,
                OnConnectionState(address, ConnectionState::CONNECTED))
        .Times(1);
    EXPECT_CALL(*callbacks, OnDeviceAvailable(address, _, _, _, _))
        .Times(AtLeast(1));

    EXPECT_CALL(gatt_interface,
                Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, true))
        .WillOnce(Invoke([this, conn_id](tGATT_IF client_if,
                                         const RawAddress& remote_bda,
                                         bool is_direct, bool opportunistic) {
          InjectConnectedEvent(remote_bda, conn_id);
          GetSearchCompleteEvent(conn_id);
        }));

    DeviceGroups::AddFromStorage(address, storage_group_buf);
    CsisClient::AddFromStorage(address, storage_buf);
  }

  void InjectEncryptionEvent(const RawAddress& test_address, uint16_t conn_id) {
    tBTA_GATTC_ENC_CMPL_CB event_data = {
        .client_if = static_cast<tGATT_IF>(conn_id),
        .remote_bda = test_address,
    };

    gatt_callback(BTA_GATTC_ENC_CMPL_CB_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectConnectedEvent(const RawAddress& address, uint16_t conn_id) {
    tBTA_GATTC_OPEN event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .transport = GATT_TRANSPORT_LE,
        .mtu = 240,
    };

    gatt_callback(BTA_GATTC_OPEN_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectDisconnectedEvent(
      const RawAddress& address, uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_PEER_USER) {
    tBTA_GATTC_CLOSE event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
        .client_if = gatt_if,
        .remote_bda = address,
        .reason = reason,
    };

    gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
  }

  void GetSearchCompleteEvent(uint16_t conn_id) {
    tBTA_GATTC_SEARCH_CMPL event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
    };

    gatt_callback(BTA_GATTC_SEARCH_CMPL_EVT, (tBTA_GATTC*)&event_data);
  }

  void TestReadCharacteristic(const RawAddress& address, uint16_t conn_id,
                              std::vector<uint16_t> handles) {
    SetSampleDatabaseCsis(conn_id, 1);
    TestAppRegister();
    TestConnect(address);
    InjectConnectedEvent(address, conn_id);

    EXPECT_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillRepeatedly(DoDefault());
    for (auto const& handle : handles) {
      EXPECT_CALL(gatt_queue, ReadCharacteristic(conn_id, handle, _, _))
          .WillOnce(DoDefault());
    }

    GetSearchCompleteEvent(conn_id);
    TestAppUnregister();
  }

  void TestGattWriteCCC(uint16_t ccc_handle, GattStatus status,
                        int deregister_times) {
    SetSampleDatabaseCsis(1, 1);
    TestAppRegister();
    TestConnect(test_address);
    InjectConnectedEvent(test_address, 1);

    auto WriteDescriptorCbGenerator = [](tGATT_STATUS status,
                                         uint16_t ccc_handle) {
      return [status, ccc_handle](uint16_t conn_id, uint16_t handle,
                                  std::vector<uint8_t> value,
                                  tGATT_WRITE_TYPE write_type,
                                  GATT_WRITE_OP_CB cb, void* cb_data) -> void {
        if (cb) {
          if (ccc_handle) {
            handle = ccc_handle;
          }
          cb(conn_id, status, handle, value.size(), value.data(), cb_data);
        }
      };
    };

    // sirk, size, lock
    EXPECT_CALL(gatt_queue, WriteDescriptor(_, _, _, _, _, _))
        .Times(3)
        .WillOnce(Invoke(WriteDescriptorCbGenerator(GATT_SUCCESS, 0)))
        .WillOnce(Invoke(WriteDescriptorCbGenerator(GATT_SUCCESS, 0)))
        .WillOnce(Invoke(WriteDescriptorCbGenerator(status, ccc_handle)));

    EXPECT_CALL(gatt_interface, DeregisterForNotifications(_, _, _))
        .Times(deregister_times);

    GetSearchCompleteEvent(1);
    Mock::VerifyAndClearExpectations(&gatt_interface);
  }

  void GetDisconnectedEvent(const RawAddress& address, uint16_t conn_id) {
    tBTA_GATTC_CLOSE event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
        .client_if = gatt_if,
        .remote_bda = address,
        .reason = GATT_CONN_TERMINATE_PEER_USER,
    };

    gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
  }

  void SetSampleCapIncludedDatabaseCsis(uint16_t conn_id, uint8_t rank,
                                        uint8_t sirk_msb = 1) {
    set_sample_cap_included_database(conn_id, true, false, rank, sirk_msb);
  }
  void SetSampleDatabaseCsis(uint16_t conn_id, uint8_t rank,
                             uint8_t sirk_msb = 1) {
    set_sample_database(conn_id, true, false, rank, sirk_msb);
  }
  void SetSampleDatabaseNoCsis(uint16_t conn_id, uint8_t rank) {
    set_sample_database(conn_id, false, false, rank);
  }
  void SetSampleDatabaseCsisBroken(uint16_t conn_id, uint rank) {
    set_sample_database(conn_id, false, true, rank);
  }
  void SetSampleDatabaseDoubleCsis(uint16_t conn_id, uint8_t rank_1,
                                   uint8_t rank_2) {
    set_sample_database_double_csis(conn_id, rank_1, rank_2, false);
  }
  void SetSampleDatabaseDoubleCsisBroken(uint16_t conn_id, uint8_t rank_1,
                                         uint8_t rank_2) {
    set_sample_database_double_csis(conn_id, rank_1, rank_2, true);
  }

  std::unique_ptr<MockCsisCallbacks> callbacks;
  std::unique_ptr<MockCsisCallbacks> lock_callback;
  bluetooth::manager::MockBtmInterface btm_interface;
  dm::MockBtaDmInterface dm_interface;
  gatt::MockBtaGattInterface gatt_interface;
  gatt::MockBtaGattQueue gatt_queue;
  MockCsisLockCallback csis_lock_cb;
  tBTA_GATTC_CBACK* gatt_callback;
  const uint8_t gatt_if = 0xff;
  std::map<uint16_t, std::list<gatt::Service>> services_map;

  const RawAddress test_address = GetTestAddress(0);
  const RawAddress test_address2 = GetTestAddress(1);
};

TEST_F(CsisClientTest, test_get_uninitialized) {
  ASSERT_DEATH(CsisClient::Get(), "");
}

TEST_F(CsisClientTest, test_initialize) {
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  ASSERT_TRUE(CsisClient::IsCsisClientRunning());
  CsisClient::CleanUp();
}

TEST_F(CsisClientTest, test_initialize_twice) {
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  CsisClient* csis_p = CsisClient::Get();
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  ASSERT_EQ(csis_p, CsisClient::Get());
  CsisClient::CleanUp();
}

TEST_F(CsisClientTest, test_cleanup_initialized) {
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  CsisClient::CleanUp();
  ASSERT_FALSE(CsisClient::IsCsisClientRunning());
}

TEST_F(CsisClientTest, test_cleanup_uninitialized) {
  CsisClient::CleanUp();
  ASSERT_FALSE(CsisClient::IsCsisClientRunning());
}

TEST_F(CsisClientTest, test_app_registration) {
  TestAppRegister();
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_connect) {
  TestAppRegister();
  TestConnect(GetTestAddress(0));
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnect_non_connected) {
  TestAppRegister();
  TestConnect(test_address);
  TestDisconnect(test_address, GATT_INVALID_CONN_ID);
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnect_connected) {
  TestAppRegister();
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  TestDisconnect(test_address, 1);
  InjectDisconnectedEvent(test_address, 1);
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnected) {
  TestAppRegister();
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::DISCONNECTED));
  InjectDisconnectedEvent(test_address, 1);

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_discovery_csis_found) {
  SetSampleDatabaseCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED));
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_discovery_csis_not_found) {
  SetSampleDatabaseNoCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(gatt_interface, Close(1));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_discovery_csis_broken) {
  SetSampleDatabaseCsisBroken(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(gatt_interface, Close(1));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_ccc_reg_fail_handle_not_found) {
  // service handle range: 0x0001 ~ 0x0030
  uint16_t not_existed_ccc_handle = 0x0031;
  TestGattWriteCCC(not_existed_ccc_handle, GATT_INVALID_HANDLE, 0);
}

TEST_F(CsisClientTest, test_ccc_reg_fail_handle_found) {
  // kCsisLockUuid ccc handle
  uint16_t existed_ccc_hande = 0x0028;
  TestGattWriteCCC(existed_ccc_hande, GATT_INVALID_HANDLE, 1);
}

TEST_F(CsisClientTest, test_ccc_reg_fail_out_of_sync) {
  // kCsisLockUuid ccc handle
  uint16_t ccc_handle = 0x0028;
  TestGattWriteCCC(ccc_handle, GATT_DATABASE_OUT_OF_SYNC, 0);
}

class CsisClientCallbackTest : public CsisClientTest {
 protected:
  const RawAddress test_address = GetTestAddress(0);
  uint16_t conn_id = 22;

  void SetUp(void) override {
    CsisClientTest::SetUp();
    SetSampleDatabaseCsis(conn_id, 1);
    TestAppRegister();
    TestConnect(test_address);
    InjectConnectedEvent(test_address, conn_id);
    GetSearchCompleteEvent(conn_id);
  }

  void TearDown(void) override {
    TestAppUnregister();
    CsisClientTest::TearDown();
  }

  void GetNotificationEvent(uint16_t handle, std::vector<uint8_t>& value) {
    tBTA_GATTC_NOTIFY event_data = {
        .conn_id = conn_id,
        .bda = test_address,
        .handle = handle,
        .len = (uint8_t)value.size(),
        .is_notify = true,
    };

    std::copy(value.begin(), value.end(), event_data.value);
    gatt_callback(BTA_GATTC_NOTIF_EVT, (tBTA_GATTC*)&event_data);
  }
};

TEST_F(CsisClientCallbackTest, test_on_group_lock_changed_group_not_found) {
  bool callback_called = false;
  EXPECT_CALL(
      *callbacks,
      OnGroupLockChanged(2, false, CsisGroupLockStatus::FAILED_INVALID_GROUP));
  CsisClient::Get()->LockGroup(
      2, true,
      base::BindOnce(
          [](bool* callback_called, int group_id, bool locked,
             CsisGroupLockStatus status) {
            if ((group_id == 2) &&
                (status == CsisGroupLockStatus::FAILED_INVALID_GROUP))
              *callback_called = true;
          },
          &callback_called));
  ASSERT_TRUE(callback_called);
}

TEST_F(CsisClientTest, test_get_group_id) {
  SetSampleDatabaseCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED));
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  int group_id = CsisClient::Get()->GetGroupId(test_address);
  ASSERT_TRUE(group_id == 1);
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_search_complete_before_encryption) {
  SetSampleDatabaseCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address, false);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED))
      .Times(0);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _)).Times(0);

  ON_CALL(btm_interface, BTM_IsEncrypted(test_address, _))
      .WillByDefault(DoAll(Return(false)));

  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());

  /* Incject encryption and expect device connection */
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED))
      .Times(1);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _)).Times(1);

  ON_CALL(btm_interface, BTM_IsEncrypted(test_address, _))
      .WillByDefault(DoAll(Return(true)));
  EXPECT_CALL(gatt_interface, ServiceSearchRequest(_, _)).Times(1);

  InjectEncryptionEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  Mock::VerifyAndClearExpectations(&gatt_interface);
  Mock::VerifyAndClearExpectations(callbacks.get());

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnect_when_link_key_is_gone) {
  SetSampleDatabaseCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address, false);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED))
      .Times(0);

  ON_CALL(btm_interface, BTM_IsEncrypted(test_address, _))
      .WillByDefault(DoAll(Return(false)));
  ON_CALL(btm_interface, SetEncryption(test_address, _, _, _, _))
      .WillByDefault(Return(BTM_ERR_KEY_MISSING));

  EXPECT_CALL(gatt_interface, Close(1));

  InjectConnectedEvent(test_address, 1);

  Mock::VerifyAndClearExpectations(&gatt_interface);
  Mock::VerifyAndClearExpectations(callbacks.get());

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_is_group_empty) {
  std::list<std::shared_ptr<CsisGroup>> csis_groups_;
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  csis_groups_.push_back(g_1);

  ASSERT_TRUE(g_1->IsEmpty());
}

TEST_F(CsisClientTest, test_add_device_to_group) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>();

  ASSERT_TRUE(g_1->IsEmpty());
  g_1->AddDevice(d_1);
  ASSERT_FALSE(g_1->IsEmpty());
}

TEST_F(CsisClientTest, test_get_set_desired_size) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDesiredSize(10);
  ASSERT_EQ(g_1->GetDesiredSize(), 10);
}

TEST_F(CsisClientTest, test_is_device_in_the_group) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>();
  g_1->AddDevice(d_1);
  g_1->IsDeviceInTheGroup(d_1);
}

TEST_F(CsisClientTest, test_get_current_size) {
  const RawAddress test_address_1 = GetTestAddress(0);
  const RawAddress test_address_2 = GetTestAddress(1);
  const RawAddress test_address_3 = GetTestAddress(2);
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>(test_address_1, true);
  auto d_2 = std::make_shared<CsisDevice>(test_address_2, true);
  auto d_3 = std::make_shared<CsisDevice>(test_address_3, true);
  g_1->AddDevice(d_1);
  g_1->AddDevice(d_2);
  g_1->AddDevice(d_3);
  ASSERT_EQ(3, g_1->GetCurrentSize());
}

TEST_F(CsisClientTest, test_set_current_lock_state_unset) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNSET);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNSET);
}

TEST_F(CsisClientTest, test_set_current_lock_state_locked) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_LOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_LOCKED);
}

TEST_F(CsisClientTest, test_set_current_lock_state_unlocked) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNLOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNLOCKED);
}

TEST_F(CsisClientTest, test_set_various_lock_states) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNLOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNLOCKED);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_LOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_LOCKED);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNSET);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNSET);
}

TEST_F(CsisClientTest, test_set_discovery_state_completed) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
}

TEST_F(CsisClientTest, test_set_discovery_state_idle) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
  ASSERT_EQ(g_1->GetDiscoveryState(), CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
}

TEST_F(CsisClientTest, test_set_discovery_state_ongoing) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
}

TEST_F(CsisClientTest, test_set_various_discovery_states) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
  ASSERT_EQ(g_1->GetDiscoveryState(), CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
}

TEST_F(CsisClientTest, test_get_first_last_device) {
  const RawAddress test_address_3 = GetTestAddress(3);
  const RawAddress test_address_4 = GetTestAddress(4);
  const RawAddress test_address_5 = GetTestAddress(5);
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>(test_address_3, true);
  auto d_2 = std::make_shared<CsisDevice>(test_address_4, true);
  auto d_3 = std::make_shared<CsisDevice>(test_address_5, true);
  g_1->AddDevice(d_1);
  g_1->AddDevice(d_2);
  g_1->AddDevice(d_3);
  ASSERT_EQ(g_1->GetLastDevice(), d_3);
  ASSERT_EQ(g_1->GetFirstDevice(), d_1);
}

TEST_F(CsisClientTest, test_get_set_sirk) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  Octet16 sirk = {1};
  g_1->SetSirk(sirk);
  ASSERT_EQ(g_1->GetSirk(), sirk);
}

TEST_F(CsisClientTest,
       test_not_open_duplicate_active_scan_while_bonding_set_member) {
  uint16_t conn_id = 0x0001;
  EXPECT_CALL(dm_interface, BTA_DmBleCsisObserve(true, _)).Times(1);
  SetSampleDatabaseCsis(conn_id, 1, 1);
  TestAppRegister();

  // Here we handle background scan request
  Mock::VerifyAndClearExpectations(&dm_interface);

  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);

  auto ReadCharacteristicCbGenerator = []() {
    return [](uint16_t conn_id, uint16_t handle, GATT_READ_OP_CB cb,
              void* cb_data) -> void {
      std::vector<uint8_t> value;
      switch (handle) {
        case 0x0003:
          // device name
          value.resize(20);
          break;
        case 0x0021:
          // plain sirk
          value.resize(17);
          value.assign(17, 1);
          break;
        case 0x0024:
          // size
          value.resize(1);
          value.assign(1, 2);
          break;
        case 0x0027:
          // lock
          value.resize(2);
          break;
        case 0x0030:
          // rank
          value.resize(1);
          value.assign(1, 1);
          break;
        default:
          FAIL();
          return;
      }
      if (cb) {
        cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(), cb_data);
      }
    };
  };

  // We should read 4 times for sirk, rank, size, and lock characteristics
  EXPECT_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
      .Times(4)
      .WillOnce(Invoke(ReadCharacteristicCbGenerator()))
      .WillOnce(Invoke(ReadCharacteristicCbGenerator()))
      .WillOnce(Invoke(ReadCharacteristicCbGenerator()))
      .WillOnce(Invoke(ReadCharacteristicCbGenerator()));

  // Here is actual active scan request for the first device
  tBTA_DM_SEARCH_CBACK* p_results_cb = nullptr;
  EXPECT_CALL(dm_interface, BTA_DmBleCsisObserve(true, _))
      .Times(1)
      .WillOnce(DoAll(SaveArg<1>(&p_results_cb)));

  GetSearchCompleteEvent(conn_id);

  Mock::VerifyAndClearExpectations(&dm_interface);

  // Simulate we find the set member
  tBTA_DM_SEARCH result;
  result.inq_res.include_rsi = true;
  std::vector<uint8_t> rsi = {0x07, 0x2e, 0x00, 0xed, 0x1a, 0x00, 0x00, 0x00};
  result.inq_res.p_eir = rsi.data();
  result.inq_res.eir_len = 8;
  result.inq_res.bd_addr = test_address2;

  // CSIS client should process set member event to JNI
  EXPECT_CALL(*callbacks, OnSetMemberAvailable(test_address2, 1));

  p_results_cb(BTA_DM_INQ_RES_EVT, &result);

  Mock::VerifyAndClearExpectations(&dm_interface);

  EXPECT_CALL(dm_interface, BTA_DmBleCsisObserve(true, _)).Times(0);

  // Simulate getting duplicate response from remote for the first device
  // At this momoment we should not open second active scan because the set
  // member is already cached and waiting for bonding
  GetSearchCompleteEvent(conn_id);

  Mock::VerifyAndClearExpectations(&dm_interface);
}

TEST_F(CsisClientTest, test_csis_member_not_found) {
  EXPECT_CALL(dm_interface, BTA_DmBleCsisObserve(true, _)).Times(1);
  SetSampleDatabaseDoubleCsis(0x001, 1, 2);
  TestAppRegister();

  /* Here we handle Background Scan request */
  Mock::VerifyAndClearExpectations(&dm_interface);

  tBTA_DM_SEARCH_CBACK* p_results_cb = nullptr;
  /* Here is actual Active Scan request  */
  EXPECT_CALL(dm_interface, BTA_DmBleCsisObserve(true, _))
      .WillOnce(DoAll(SaveArg<1>(&p_results_cb)));

  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  Mock::VerifyAndClearExpectations(&dm_interface);
  /* Verify that scanner has been called to start filtering  */
  ASSERT_EQ(1, get_func_call_count("set_empty_filter"));

  /* Check callback is not null and simulate no member found and scan
   * completed*/
  ASSERT_NE(p_results_cb, nullptr);

  tBTA_DM_SEARCH result;
  result.inq_cmpl.num_resps = 80;

  p_results_cb(BTA_DM_INQ_CMPL_EVT, &result);

  /* Verify that scanner has been called to stop filtering  */
  ASSERT_EQ(2, get_func_call_count("set_empty_filter"));
}

class CsisMultiClientTest : public CsisClientTest {
 protected:
  const RawAddress test_address_1 = GetTestAddress(1);
  const RawAddress test_address_2 = GetTestAddress(2);

  void SetUp(void) override {
    CsisClientTest::SetUp();
    TestAppRegister();
    SetSampleDatabaseDoubleCsis(0x001, 1, 2);
  }
};

class CsisMultiClientTestBroken : public CsisClientTest {
 protected:
  const RawAddress test_address_1 = GetTestAddress(1);
  const RawAddress test_address_2 = GetTestAddress(2);

  void SetUp(void) override {
    CsisClientTest::SetUp();
    TestAppRegister();
    SetSampleDatabaseDoubleCsisBroken(0x001, 1, 2);
  }
};

TEST_F(CsisMultiClientTest, test_add_multiple_instances) {
  TestAppUnregister();
  CsisClientTest::TearDown();
}

TEST_F(CsisMultiClientTest, test_cleanup_multiple_instances) {
  CsisClient::CleanUp();
  CsisClient::IsCsisClientRunning();
}

TEST_F(CsisMultiClientTest, test_connect_multiple_instances) {
  TestConnect(GetTestAddress(0));
  TestAppUnregister();
}

TEST_F(CsisMultiClientTest, test_disconnect_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::DISCONNECTED));
  InjectDisconnectedEvent(test_address, 1);

  TestAppUnregister();
  CsisClientTest::TearDown();
}

TEST_F(CsisMultiClientTest, test_lock_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(1, true, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(1, true, CsisGroupLockStatus::SUCCESS));
  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([](uint16_t conn_id, uint16_t handle,
                    std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                    GATT_WRITE_OP_CB cb, void* cb_data) -> void {
            if (cb)
              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
          }));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(2, true, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(2, true, CsisGroupLockStatus::SUCCESS));
  CsisClient::Get()->LockGroup(
      2, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));
}

TEST_F(CsisMultiClientTest, test_unlock_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([](uint16_t conn_id, uint16_t handle,
                    std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                    GATT_WRITE_OP_CB cb, void* cb_data) -> void {
            if (cb)
              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
          }));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(1, false, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(1, false, CsisGroupLockStatus::SUCCESS));
  CsisClient::Get()->LockGroup(
      1, false,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));
}

TEST_F(CsisMultiClientTest, test_disconnect_locked_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  TestConnect(test_address2);
  InjectConnectedEvent(test_address2, 2);
  GetSearchCompleteEvent(2);

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(1, true, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(1, true, CsisGroupLockStatus::SUCCESS));
  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([](uint16_t conn_id, uint16_t handle,
                    std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                    GATT_WRITE_OP_CB cb, void* cb_data) -> void {
            if (cb)
              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
          }));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(
                  1, false, CsisGroupLockStatus::LOCKED_GROUP_MEMBER_LOST));
  InjectDisconnectedEvent(test_address, 2, GATT_CONN_TIMEOUT);
}

TEST_F(CsisMultiClientTest, test_discover_multiple_instances) {
  TestConnect(test_address);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED))
      .Times(1);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _)).Times(2);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_storage_calls) {
  SetSampleDatabaseCsis(1, 1);

  ASSERT_EQ(0, get_func_call_count("btif_storage_load_bonded_csis_devices"));
  TestAppRegister();
  ASSERT_EQ(1, get_func_call_count("btif_storage_load_bonded_csis_devices"));

  ASSERT_EQ(0, get_func_call_count("btif_storage_update_csis_info"));
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  ASSERT_EQ(1, get_func_call_count("btif_storage_update_csis_info"));

  ASSERT_EQ(0, get_func_call_count("btif_storage_remove_csis_device"));
  CsisClient::Get()->RemoveDevice(test_address);
  /* It is 0 because btif_csis_client.cc calls that */
  ASSERT_EQ(0, get_func_call_count("btif_storage_remove_csis_device"));

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_storage_content) {
  // Two devices in one set
  SetSampleCapIncludedDatabaseCsis(1, 1);
  SetSampleCapIncludedDatabaseCsis(2, 2);
  // Devices in the other set
  SetSampleCapIncludedDatabaseCsis(3, 1, 2);
  SetSampleCapIncludedDatabaseCsis(4, 1, 3);

  TestAppRegister();
  TestConnect(GetTestAddress(1));
  InjectConnectedEvent(GetTestAddress(1), 1);
  GetSearchCompleteEvent(1);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(1),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  TestConnect(GetTestAddress(2));
  InjectConnectedEvent(GetTestAddress(2), 2);
  GetSearchCompleteEvent(2);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(2),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  TestConnect(GetTestAddress(3));
  InjectConnectedEvent(GetTestAddress(3), 3);
  GetSearchCompleteEvent(3);
  ASSERT_EQ(2, CsisClient::Get()->GetGroupId(
                   GetTestAddress(3),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  std::vector<uint8_t> dev1_storage;
  std::vector<uint8_t> dev2_storage;
  std::vector<uint8_t> dev3_storage;

  // Store to byte buffer
  CsisClient::GetForStorage(GetTestAddress(1), dev1_storage);
  CsisClient::GetForStorage(GetTestAddress(2), dev2_storage);
  CsisClient::GetForStorage(GetTestAddress(3), dev3_storage);

  ASSERT_NE(0u, dev1_storage.size());
  ASSERT_NE(0u, dev2_storage.size());
  ASSERT_NE(0u, dev3_storage.size());

  std::vector<uint8_t> dev1_group_storage;
  std::vector<uint8_t> dev2_group_storage;
  std::vector<uint8_t> dev3_group_storage;

  DeviceGroups::GetForStorage(GetTestAddress(1), dev1_group_storage);
  DeviceGroups::GetForStorage(GetTestAddress(2), dev2_group_storage);
  DeviceGroups::GetForStorage(GetTestAddress(3), dev3_group_storage);

  ASSERT_NE(0u, dev1_group_storage.size());
  ASSERT_NE(0u, dev2_group_storage.size());
  ASSERT_NE(0u, dev3_group_storage.size());

  // Clean it up
  TestAppUnregister();

  // Reinitialize service
  TestAppRegister();

  // Restore dev1 from the byte buffer
  TestAddFromStorage(GetTestAddress(1), 1, dev1_group_storage, dev1_storage);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(1),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  // Restore dev2 from the byte buffer
  TestAddFromStorage(GetTestAddress(2), 2, dev2_group_storage, dev2_storage);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(2),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  // Restore dev3 from the byte buffer
  TestAddFromStorage(GetTestAddress(3), 3, dev3_group_storage, dev3_storage);
  ASSERT_EQ(2, CsisClient::Get()->GetGroupId(
                   GetTestAddress(3),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  // Restore not inerrogated dev4 - empty buffer but valid sirk for group 2
  std::vector<uint8_t> no_set_info;
  TestAddFromStorage(GetTestAddress(4), 4, no_set_info, no_set_info);
  ASSERT_EQ(3, CsisClient::Get()->GetGroupId(
                   GetTestAddress(4),
                   bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE)));

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_database_out_of_sync) {
  auto test_address = GetTestAddress(0);
  auto conn_id = 1;

  TestAppRegister();
  SetSampleDatabaseCsis(conn_id, 1);
  TestConnect(test_address);
  InjectConnectedEvent(test_address, conn_id);
  GetSearchCompleteEvent(conn_id);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   test_address, bluetooth::Uuid::From16Bit(0x0000)));

  // Simulated database changed on the remote side.
  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([this](uint16_t conn_id, uint16_t handle,
                        std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                        GATT_WRITE_OP_CB cb, void* cb_data) {
            auto* svc = gatt::FindService(services_map[conn_id], handle);
            if (svc == nullptr) return;

            tGATT_STATUS status = GATT_DATABASE_OUT_OF_SYNC;
            if (cb)
              cb(conn_id, status, handle, value.size(), value.data(), cb_data);
          }));

  ON_CALL(gatt_interface, ServiceSearchRequest(_, _)).WillByDefault(Return());
  EXPECT_CALL(gatt_interface, ServiceSearchRequest(_, _));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));
  TestAppUnregister();
}

}  // namespace
}  // namespace internal
}  // namespace csis
}  // namespace bluetooth
