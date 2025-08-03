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

#include <base/bind_helpers.h>
#include <base/functional/bind.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <osi/include/alarm.h>
#include <sys/socket.h>

#include <variant>

#include "bta/le_audio/le_audio_types.h"
#include "bta_csis_api.h"
#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "bta_has_api.h"
#include "btif_storage_mock.h"
#include "btm_api_mock.h"
#include "gatt/database_builder.h"
#include "hardware/bt_gatt_types.h"
#include "has_types.h"
#include "mock_controller.h"
#include "mock_csis_client.h"
#include "stack/include/bt_uuid16.h"
#include "test/common/mock_functions.h"

bool gatt_profile_get_eatt_support(const RawAddress& addr) { return true; }
void osi_property_set_bool(const char* key, bool value);

namespace bluetooth {
namespace has {
namespace internal {
namespace {

using base::HexEncode;

using ::bluetooth::csis::CsisClient;
using ::bluetooth::has::ConnectionState;
using ::bluetooth::has::ErrorCode;
using ::bluetooth::has::HasClientCallbacks;
using ::bluetooth::has::PresetInfo;

using ::le_audio::has::HasClient;
using ::le_audio::has::HasCtpGroupOpCoordinator;
using ::le_audio::has::HasCtpOp;
using ::le_audio::has::HasDevice;
using ::le_audio::has::HasPreset;

using ::testing::_;
using ::testing::AnyNumber;
using ::testing::DoAll;
using ::testing::DoDefault;
using ::testing::Invoke;
using ::testing::Mock;
using ::testing::NotNull;
using ::testing::Return;
using ::testing::SaveArg;
using ::testing::Sequence;
using ::testing::SetArgPointee;
using ::testing::WithArg;

// Disables most likely false-positives from base::SplitString()
// extern "C" const char* __asan_default_options() {
//   return "detect_container_overflow=0";
// }

RawAddress GetTestAddress(int index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {
      {0xC0, 0xDE, 0xC0, 0xDE, 0x00, static_cast<uint8_t>(index)}};
  return result;
}

static uint16_t GetTestConnId(const RawAddress& address) {
  return address.address[RawAddress::kLength - 1];
}

class MockHasCallbacks : public HasClientCallbacks {
 public:
  MockHasCallbacks() = default;
  MockHasCallbacks(const MockHasCallbacks&) = delete;
  MockHasCallbacks& operator=(const MockHasCallbacks&) = delete;

  ~MockHasCallbacks() override = default;

  MOCK_METHOD((void), OnConnectionState,
              (ConnectionState state, const RawAddress& address), (override));
  MOCK_METHOD((void), OnDeviceAvailable,
              (const RawAddress& address, uint8_t features), (override));
  MOCK_METHOD((void), OnFeaturesUpdate,
              (const RawAddress& address, uint8_t features), (override));
  MOCK_METHOD((void), OnActivePresetSelected,
              ((std::variant<RawAddress, int> addr_or_group_id),
               uint8_t preset_index),
              (override));
  MOCK_METHOD((void), OnActivePresetSelectError,
              ((std::variant<RawAddress, int> addr_or_group_id),
               ErrorCode result),
              (override));
  MOCK_METHOD((void), OnPresetInfo,
              ((std::variant<RawAddress, int> addr_or_group_id),
               PresetInfoReason change_id,
               std::vector<PresetInfo> preset_change_records),
              (override));
  MOCK_METHOD((void), OnPresetInfoError,
              ((std::variant<RawAddress, int> addr_or_group_id),
               uint8_t preset_index, ErrorCode error_code),
              (override));
  MOCK_METHOD((void), OnSetPresetNameError,
              ((std::variant<RawAddress, int> addr_or_group_id),
               uint8_t preset_index, ErrorCode error_code),
              (override));
};

class HasClientTestBase : public ::testing::Test {
 protected:
  std::map<uint16_t, uint8_t> current_peer_active_preset_idx_;
  std::map<uint16_t, uint8_t> current_peer_features_val_;
  std::map<uint16_t, std::set<HasPreset, HasPreset::ComparatorDesc>>
      current_peer_presets_;

  struct HasDbBuilder {
    bool has;

    static constexpr uint16_t kGapSvcStartHdl = 0x0001;
    static constexpr uint16_t kGapDeviceNameValHdl = 0x0003;
    static constexpr uint16_t kGapSvcEndHdl = kGapDeviceNameValHdl;

    static constexpr uint16_t kSvcStartHdl = 0x0010;
    static constexpr uint16_t kFeaturesValHdl = 0x0012;
    static constexpr uint16_t kPresetsCtpValHdl = 0x0015;
    static constexpr uint16_t kActivePresetIndexValHdl = 0x0018;
    static constexpr uint16_t kSvcEndHdl = 0x001E;

    static constexpr uint16_t kGattSvcStartHdl = 0x0090;
    static constexpr uint16_t kGattSvcChangedValHdl = 0x0092;
    static constexpr uint16_t kGattSvcEndHdl = kGattSvcChangedValHdl + 1;

    bool features;
    bool features_ntf;

    bool preset_cp;
    bool preset_cp_ntf;
    bool preset_cp_ind;

    bool active_preset_idx;
    bool active_preset_idx_ntf;

    const gatt::Database Build() {
      gatt::DatabaseBuilder bob;

      /* Generic Access Service */
      bob.AddService(kGapSvcStartHdl, kGapSvcEndHdl, Uuid::From16Bit(0x1800),
                     true);
      /* Device Name Char. */
      bob.AddCharacteristic(kGapDeviceNameValHdl - 1, kGapDeviceNameValHdl,
                            Uuid::From16Bit(0x2a00), GATT_CHAR_PROP_BIT_READ);

      /* 0x0004-0x000f left empty on purpose */
      if (has) {
        bob.AddService(kSvcStartHdl, kSvcEndHdl,
                       ::le_audio::has::kUuidHearingAccessService, true);

        if (features) {
          bob.AddCharacteristic(
              kFeaturesValHdl - 1, kFeaturesValHdl,
              ::le_audio::has::kUuidHearingAidFeatures,
              GATT_CHAR_PROP_BIT_READ |
                  (features_ntf ? GATT_CHAR_PROP_BIT_NOTIFY : 0));

          if (features_ntf) {
            bob.AddDescriptor(kFeaturesValHdl + 1,
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
          }
        }

        if (preset_cp) {
          bob.AddCharacteristic(
              kPresetsCtpValHdl - 1, kPresetsCtpValHdl,
              ::le_audio::has::kUuidHearingAidPresetControlPoint,
              GATT_CHAR_PROP_BIT_WRITE |
                  (preset_cp_ntf ? GATT_CHAR_PROP_BIT_NOTIFY : 0) |
                  (preset_cp_ind ? GATT_CHAR_PROP_BIT_INDICATE : 0));

          if (preset_cp_ntf || preset_cp_ind) {
            bob.AddDescriptor(kPresetsCtpValHdl + 1,
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
          }
        }

        if (active_preset_idx) {
          bob.AddCharacteristic(
              kActivePresetIndexValHdl - 1, kActivePresetIndexValHdl,
              ::le_audio::has::kUuidActivePresetIndex,
              GATT_CHAR_PROP_BIT_READ |
                  (active_preset_idx_ntf ? GATT_CHAR_PROP_BIT_NOTIFY : 0));

          if (active_preset_idx_ntf)
            bob.AddDescriptor(kActivePresetIndexValHdl + 1,
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
        }
      }

      /* GATTS */
      /* 0x001F-0x0090 left empty on purpose */
      bob.AddService(kGattSvcStartHdl, kGattSvcEndHdl,
                     Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
      bob.AddCharacteristic(kGattSvcChangedValHdl - 1, kGattSvcChangedValHdl,
                            Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                            GATT_CHAR_PROP_BIT_NOTIFY);
      bob.AddDescriptor(kGattSvcChangedValHdl + 1,
                        Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      return bob.Build();
    };
  };

  const gatt::Characteristic* FindCharacteristicByValueHandle(
      const gatt::Service* svc, uint16_t handle) {
    if (svc == nullptr) return nullptr;

    auto it =
        std::find_if(svc->characteristics.cbegin(), svc->characteristics.cend(),
                     [handle](const auto& characteristic) {
                       return characteristic.value_handle == handle;
                     });
    return (it != svc->characteristics.cend()) ? &(*it) : nullptr;
  }

  void set_sample_database(
      const RawAddress& address, HasDbBuilder& builder,
      uint8_t features_val = 0x0,
      std::optional<std::set<HasPreset, HasPreset::ComparatorDesc>> presets_op =
          std::nullopt) {
    uint16_t conn_id = GetTestConnId(address);

    /* For some test cases these defaults are enough */
    if (!presets_op)
      presets_op = {{
          HasPreset(6, HasPreset::kPropertyAvailable, "Universal"),
          HasPreset(
              55, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
              "YourPreset55"),
      }};
    auto& presets = presets_op.value();
    auto const active_preset = presets.begin();

    services_map[conn_id] = builder.Build().Services();
    current_peer_features_val_.insert_or_assign(conn_id, features_val);
    current_peer_active_preset_idx_.insert_or_assign(conn_id,
                                                     active_preset->GetIndex());
    current_peer_presets_.insert_or_assign(conn_id, std::move(presets));

    ON_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillByDefault(Invoke([this](uint16_t conn_id, uint16_t handle,
                                     GATT_READ_OP_CB cb, void* cb_data) {
          auto* svc = gatt::FindService(services_map[conn_id], handle);
          if (svc == nullptr) return;

          std::vector<uint8_t> value;
          tGATT_STATUS status = GATT_SUCCESS;

          switch (handle) {
            case HasDbBuilder::kGapDeviceNameValHdl:
              value.resize(20);
              break;
            case HasDbBuilder::kFeaturesValHdl:
              value.resize(1);
              value[0] = current_peer_features_val_.at(conn_id);
              break;
            case HasDbBuilder::kActivePresetIndexValHdl:
              value.resize(1);
              value[0] = current_peer_active_preset_idx_.at(conn_id);
              break;
            case HasDbBuilder::kPresetsCtpValHdl:
              /* passthrough */
            default:
              status = GATT_READ_NOT_PERMIT;
              break;
          }

          if (cb)
            cb(conn_id, status, handle, value.size(), value.data(), cb_data);
        }));

    /* Default action for the Control Point operation writes */
    ON_CALL(gatt_queue,
            WriteCharacteristic(conn_id, HasDbBuilder::kPresetsCtpValHdl, _,
                                GATT_WRITE, _, _))
        .WillByDefault(Invoke([this, address](uint16_t conn_id, uint16_t handle,
                                              std::vector<uint8_t> value,
                                              tGATT_WRITE_TYPE write_type,
                                              GATT_WRITE_OP_CB cb,
                                              void* cb_data) {
          auto pp = value.data();
          auto len = value.size();
          uint8_t op, index, num_of_indices;

          const bool indicate = false;

          if (len < 1) {
            if (cb)
              cb(conn_id, GATT_INVALID_ATTR_LEN, handle, value.size(),
                 value.data(), cb_data);
            return;
          }

          STREAM_TO_UINT8(op, pp)
          --len;
          if (op >
              static_cast<
                  std::underlying_type_t<::le_audio::has::PresetCtpOpcode>>(
                  ::le_audio::has::PresetCtpOpcode::OP_MAX_)) {
            /* Invalid Opcode */
            if (cb)
              cb(conn_id, (tGATT_STATUS)0x80, handle, value.size(),
                 value.data(), cb_data);
            return;
          }

          switch (static_cast<::le_audio::has::PresetCtpOpcode>(op)) {
            case ::le_audio::has::PresetCtpOpcode::READ_PRESETS:
              if (len < 2) {
                if (cb)
                  cb(conn_id, GATT_INVALID_ATTR_LEN, handle, value.size(),
                     value.data(), cb_data);

              } else {
                STREAM_TO_UINT8(index, pp);
                STREAM_TO_UINT8(num_of_indices, pp);
                len -= 2;
                ASSERT_EQ(0u, len);

                InjectNotifyReadPresetsResponse(conn_id, address, handle, value,
                                                indicate, index, num_of_indices,
                                                cb, cb_data);
              }
              break;

            case ::le_audio::has::PresetCtpOpcode::SET_ACTIVE_PRESET: {
              if (len < 1) {
                if (cb)
                  cb(conn_id, GATT_INVALID_ATTR_LEN, handle, value.size(),
                     value.data(), cb_data);
                break;
              }
              STREAM_TO_UINT8(index, pp);
              --len;
              ASSERT_EQ(0u, len);

              auto presets = current_peer_presets_.at(conn_id);
              if (presets.count(index)) {
                current_peer_active_preset_idx_.insert_or_assign(conn_id,
                                                                 index);
                if (cb)
                  cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                     cb_data);
                InjectActivePresetNotification(conn_id, address, handle, value,
                                               index, cb, cb_data);
              } else {
                /* Preset Operation Not Possible */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(),
                     value.data(), cb_data);
              }
            } break;

            case ::le_audio::has::PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC: {
              auto features = current_peer_features_val_.at(conn_id);
              if ((features & ::bluetooth::has::
                                  kFeatureBitPresetSynchronizationSupported) ==
                  0) {
                /* Synchronization Not Supported */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x82, handle, value.size(),
                     value.data(), cb_data);
                break;
              }

              if (len < 1) {
                if (cb)
                  cb(conn_id, GATT_INVALID_ATTR_LEN, handle, value.size(),
                     value.data(), cb_data);
                break;
              }
              STREAM_TO_UINT8(index, pp);
              --len;
              ASSERT_EQ(0u, len);

              auto csis_api = CsisClient::Get();
              int group_id = bluetooth::groups::kGroupUnknown;
              if (csis_api != nullptr) {
                group_id = csis_api->GetGroupId(
                    address, ::le_audio::uuid::kCapServiceUuid);
              }

              if (group_id != bluetooth::groups::kGroupUnknown) {
                if (cb)
                  cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                     cb_data);
                /* Send notification from all grouped devices */
                auto addresses = csis_api->GetDeviceList(group_id);
                for (auto& addr : addresses) {
                  auto conn = GetTestConnId(addr);
                  InjectActivePresetNotification(conn, addr, handle, value,
                                                 index, cb, cb_data);
                }
              } else {
                /* Preset Operation Not Possible */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(),
                     value.data(), cb_data);
              }
            } break;

            case ::le_audio::has::PresetCtpOpcode::SET_NEXT_PRESET: {
              ASSERT_EQ(0u, len);
              ASSERT_NE(0u, current_peer_active_preset_idx_.count(conn_id));
              ASSERT_NE(0u, current_peer_presets_.count(conn_id));

              auto current_preset = current_peer_active_preset_idx_.at(conn_id);
              auto presets = current_peer_presets_.at(conn_id);
              auto current = presets.find(current_preset);
              if (current != presets.end()) {
                ++current;
                if (current == presets.end()) current = presets.begin();

                current_peer_active_preset_idx_.insert_or_assign(
                    conn_id, current->GetIndex());
                InjectActivePresetNotification(conn_id, address, handle, value,
                                               current->GetIndex(), cb,
                                               cb_data);

              } else {
                /* Preset Operation Not Possible */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(),
                     value.data(), cb_data);
              }
            } break;

            case ::le_audio::has::PresetCtpOpcode::SET_PREV_PRESET: {
              ASSERT_EQ(0u, len);
              ASSERT_NE(0u, current_peer_active_preset_idx_.count(conn_id));
              ASSERT_NE(0u, current_peer_presets_.count(conn_id));

              auto current_preset = current_peer_active_preset_idx_.at(conn_id);
              auto presets = current_peer_presets_.at(conn_id);
              auto rit = presets.rbegin();
              while (rit != presets.rend()) {
                if (rit->GetIndex() == current_preset) {
                  rit++;
                  /* Wrap around */
                  if (rit == presets.rend()) {
                    rit = presets.rbegin();
                  }
                  break;
                }
                rit++;
              }

              if (rit != presets.rend()) {
                if (cb)
                  cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                     cb_data);
                current_peer_active_preset_idx_.insert_or_assign(
                    conn_id, rit->GetIndex());
                InjectActivePresetNotification(conn_id, address, handle, value,
                                               rit->GetIndex(), cb, cb_data);
              } else {
                /* Preset Operation Not Possible */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(),
                     value.data(), cb_data);
              }
            } break;

            case ::le_audio::has::PresetCtpOpcode::SET_NEXT_PRESET_SYNC: {
              ASSERT_EQ(0u, len);
              auto features = current_peer_features_val_.at(conn_id);
              if ((features & ::bluetooth::has::
                                  kFeatureBitPresetSynchronizationSupported) ==
                  0) {
                /* Synchronization Not Supported */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x82, handle, value.size(),
                     value.data(), cb_data);
                break;
              }

              auto current_preset = current_peer_active_preset_idx_.at(conn_id);
              auto presets = current_peer_presets_.at(conn_id);
              auto rit = presets.begin();
              while (rit != presets.end()) {
                if (rit->GetIndex() == current_preset) {
                  rit++;
                  /* Wrap around */
                  if (rit == presets.end()) {
                    rit = presets.begin();
                  }
                  break;
                }
                rit++;
              }

              if (rit != presets.end()) {
                auto synced_group = mock_csis_client_module_.GetGroupId(
                    GetTestAddress(conn_id), ::le_audio::uuid::kCapServiceUuid);
                auto addresses =
                    mock_csis_client_module_.GetDeviceList(synced_group);

                // Emulate locally synced op. - notify from all of the devices
                for (auto addr : addresses) {
                  auto cid = GetTestConnId(addr);
                  if ((cid == conn_id) && (cb != nullptr))
                    cb(cid, GATT_SUCCESS, handle, value.size(), value.data(),
                       cb_data);

                  current_peer_active_preset_idx_.insert_or_assign(
                      conn_id, rit->GetIndex());
                  InjectActivePresetNotification(cid, addr, handle, value,
                                                 rit->GetIndex(), cb, cb_data);
                }
              } else {
                /* Preset Operation Not Possible */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(),
                     value.data(), cb_data);
              }
            } break;

            case ::le_audio::has::PresetCtpOpcode::SET_PREV_PRESET_SYNC: {
              ASSERT_EQ(0u, len);
              auto features = current_peer_features_val_.at(conn_id);
              if ((features & ::bluetooth::has::
                                  kFeatureBitPresetSynchronizationSupported) ==
                  0) {
                /* Synchronization Not Supported */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x82, handle, value.size(),
                     value.data(), cb_data);
                break;
              }

              auto current_preset = current_peer_active_preset_idx_.at(conn_id);
              auto presets = current_peer_presets_.at(conn_id);
              auto rit = presets.rbegin();
              while (rit != presets.rend()) {
                if (rit->GetIndex() == current_preset) {
                  rit++;
                  /* Wrap around */
                  if (rit == presets.rend()) {
                    rit = presets.rbegin();
                  }
                  break;
                }
                rit++;
              }

              if (rit != presets.rend()) {
                auto synced_group = mock_csis_client_module_.GetGroupId(
                    GetTestAddress(conn_id), ::le_audio::uuid::kCapServiceUuid);
                auto addresses =
                    mock_csis_client_module_.GetDeviceList(synced_group);

                // Emulate locally synced op. - notify from all of the devices
                for (auto addr : addresses) {
                  auto cid = GetTestConnId(addr);
                  if ((cid == conn_id) && (cb != nullptr))
                    cb(cid, GATT_SUCCESS, handle, value.size(), value.data(),
                       cb_data);

                  current_peer_active_preset_idx_.insert_or_assign(
                      conn_id, rit->GetIndex());
                  InjectActivePresetNotification(cid, addr, handle, value,
                                                 rit->GetIndex(), cb, cb_data);
                }
              } else {
                /* Preset Operation Not Possible */
                if (cb)
                  cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(),
                     value.data(), cb_data);
              }
            } break;

            case ::le_audio::has::PresetCtpOpcode::WRITE_PRESET_NAME: {
              STREAM_TO_UINT8(index, pp);
              --len;
              auto name = std::string(pp, pp + len);
              len = 0;

              ASSERT_NE(0u, current_peer_presets_.count(conn_id));
              auto presets = current_peer_presets_.at(conn_id);
              auto rit = presets.rbegin();
              auto current = rit;
              while (rit != presets.rend()) {
                if (rit->GetIndex() == index) {
                  current = rit;
                  rit++;
                  break;
                }
                rit++;
              }

              auto prev_index = (rit == presets.rend()) ? 0 : rit->GetIndex();

              ASSERT_NE(current, presets.rend());
              if (cb)
                cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                   cb_data);

              auto new_preset = HasPreset(current->GetIndex(),
                                          current->GetProperties(), name);
              presets.erase(current->GetIndex());
              presets.insert(new_preset);

              InjectPresetChanged(
                  conn_id, address, indicate, new_preset, prev_index,
                  ::le_audio::has::PresetCtpChangeId::PRESET_GENERIC_UPDATE,
                  true);
            } break;

            default:
              if (cb)
                cb(conn_id, GATT_INVALID_HANDLE, handle, value.size(),
                   value.data(), cb_data);
              break;
          }
        }));
  }

  void SetUp(void) override {
    reset_mock_function_count_map();
    controller::SetMockControllerInterface(&controller_interface_);
    bluetooth::manager::SetMockBtmInterface(&btm_interface);
    bluetooth::storage::SetMockBtifStorageInterface(&btif_storage_interface_);
    gatt::SetMockBtaGattInterface(&gatt_interface);
    gatt::SetMockBtaGattQueue(&gatt_queue);
    callbacks.reset(new MockHasCallbacks());

    encryption_result = true;

    ON_CALL(btm_interface, SetEncryption(_, _, _, _, _))
        .WillByDefault(
            Invoke([this](const RawAddress& bd_addr, tBT_TRANSPORT transport,
                          tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                          tBTM_BLE_SEC_ACT sec_act) -> tBTM_STATUS {
              InjectEncryptionEvent(bd_addr);
              return BTM_SUCCESS;
            }));

    MockCsisClient::SetMockInstanceForTesting(&mock_csis_client_module_);
    ON_CALL(mock_csis_client_module_, Get())
        .WillByDefault(Return(&mock_csis_client_module_));
    ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
        .WillByDefault(Return(true));

    /* default action for GetCharacteristic function call */
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

    /* default action for GetOwningService function call */
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

    ON_CALL(gatt_interface, ServiceSearchRequest(_, _))
        .WillByDefault(WithArg<0>(Invoke(
            [&](uint16_t conn_id) { InjectSearchCompleteEvent(conn_id); })));

    /* default action for GetServices function call */
    ON_CALL(gatt_interface, GetServices(_))
        .WillByDefault(WithArg<0>(
            Invoke([&](uint16_t conn_id) -> std::list<gatt::Service>* {
              return &services_map[conn_id];
            })));

    /* default action for RegisterForNotifications function call */
    ON_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    /* default action for DeregisterForNotifications function call */
    ON_CALL(gatt_interface, DeregisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    /* default action for WriteDescriptor function call */
    ON_CALL(gatt_queue, WriteDescriptor(_, _, _, _, _, _))
        .WillByDefault(
            Invoke([](uint16_t conn_id, uint16_t handle,
                      std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                      GATT_WRITE_OP_CB cb, void* cb_data) -> void {
              if (cb)
                cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                   cb_data);
            }));

    /* by default connect only direct connection requests */
    ON_CALL(gatt_interface, Open(_, _, _, _))
        .WillByDefault(
            Invoke([&](tGATT_IF client_if, const RawAddress& remote_bda,
                       tBTM_BLE_CONN_TYPE connection_type, bool opportunistic) {
              if (connection_type == BTM_BLE_DIRECT_CONNECTION)
                InjectConnectedEvent(remote_bda, GetTestConnId(remote_bda));
            }));

    ON_CALL(gatt_interface, Close(_))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id) { InjectDisconnectedEvent(conn_id); }));
  }

  void TearDown(void) override {
    services_map.clear();
    gatt::SetMockBtaGattQueue(nullptr);
    gatt::SetMockBtaGattInterface(nullptr);
    bluetooth::storage::SetMockBtifStorageInterface(nullptr);
    bluetooth::manager::SetMockBtmInterface(nullptr);
    controller::SetMockControllerInterface(nullptr);
    callbacks.reset();

    current_peer_active_preset_idx_.clear();
    current_peer_features_val_.clear();
  }

  void TestAppRegister(void) {
    BtaAppRegisterCallback app_register_callback;
    EXPECT_CALL(gatt_interface, AppRegister(_, _, _))
        .WillOnce(DoAll(SaveArg<0>(&gatt_callback),
                        SaveArg<1>(&app_register_callback)));
    HasClient::Initialize(callbacks.get(), base::DoNothing());
    ASSERT_TRUE(gatt_callback);
    ASSERT_TRUE(app_register_callback);
    app_register_callback.Run(gatt_if, GATT_SUCCESS);
    ASSERT_TRUE(HasClient::IsHasClientRunning());
    Mock::VerifyAndClearExpectations(&gatt_interface);
  }

  void TestAppUnregister(void) {
    EXPECT_CALL(gatt_interface, AppDeregister(gatt_if));
    HasClient::CleanUp();
    ASSERT_FALSE(HasClient::IsHasClientRunning());
    gatt_callback = nullptr;
  }

  void TestConnect(const RawAddress& address) {
    ON_CALL(btm_interface, BTM_IsEncrypted(address, _))
        .WillByDefault(DoAll(Return(encryption_result)));

    EXPECT_CALL(gatt_interface,
                Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, _));
    HasClient::Get()->Connect(address);

    Mock::VerifyAndClearExpectations(&*callbacks);
    Mock::VerifyAndClearExpectations(&gatt_queue);
    Mock::VerifyAndClearExpectations(&gatt_interface);
    Mock::VerifyAndClearExpectations(&btm_interface);
  }

  void TestDisconnect(const RawAddress& address, uint16_t conn_id) {
    EXPECT_CALL(gatt_interface, CancelOpen(_, address, _)).Times(AnyNumber());
    if (conn_id != GATT_INVALID_CONN_ID) {
      assert(0);
      EXPECT_CALL(gatt_interface, Close(conn_id));
    } else {
      EXPECT_CALL(gatt_interface, CancelOpen(gatt_if, address, _));
    }
    HasClient::Get()->Disconnect(address);
  }

  void TestAddFromStorage(const RawAddress& address, uint8_t features,
                          bool auto_connect) {
    if (auto_connect) {
      EXPECT_CALL(gatt_interface,
                  Open(gatt_if, address, BTM_BLE_BKG_CONNECT_ALLOW_LIST, _));
      HasClient::Get()->AddFromStorage(address, features, auto_connect);

      /* Inject connected event for autoconnect/background connection */
      InjectConnectedEvent(address, GetTestConnId(address));
    } else {
      EXPECT_CALL(gatt_interface, Open(gatt_if, address, _, _)).Times(0);
      HasClient::Get()->AddFromStorage(address, features, auto_connect);
    }

    Mock::VerifyAndClearExpectations(&gatt_interface);
  }

  void InjectConnectedEvent(const RawAddress& address, uint16_t conn_id,
                            tGATT_STATUS status = GATT_SUCCESS) {
    tBTA_GATTC_OPEN event_data = {
        .status = status,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .transport = GATT_TRANSPORT_LE,
        .mtu = 240,
    };

    connected_devices[conn_id] = address;
    gatt_callback(BTA_GATTC_OPEN_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectDisconnectedEvent(
      uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_LOCAL_HOST,
      bool allow_fake_conn = false) {
    if (!allow_fake_conn) ASSERT_NE(connected_devices.count(conn_id), 0u);

    tBTA_GATTC_CLOSE event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
        .client_if = gatt_if,
        .remote_bda = connected_devices[conn_id],
        .reason = reason,
    };

    connected_devices.erase(conn_id);
    gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectSearchCompleteEvent(uint16_t conn_id) {
    tBTA_GATTC_SEARCH_CMPL event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
    };

    gatt_callback(BTA_GATTC_SEARCH_CMPL_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectNotificationEvent(const RawAddress& test_address, uint16_t conn_id,
                               uint16_t handle, std::vector<uint8_t> value,
                               bool indicate = false) {
    tBTA_GATTC_NOTIFY event_data = {
        .conn_id = conn_id,
        .bda = test_address,
        .handle = handle,
        .len = (uint8_t)value.size(),
        .is_notify = !indicate,
    };

    ASSERT_TRUE(value.size() < GATT_MAX_ATTR_LEN);
    std::copy(value.begin(), value.end(), event_data.value);
    gatt_callback(BTA_GATTC_NOTIF_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectEncryptionEvent(const RawAddress& test_address) {
    tBTA_GATTC_ENC_CMPL_CB event_data = {
        .client_if = static_cast<tGATT_IF>(GetTestConnId(test_address)),
        .remote_bda = test_address,
    };

    gatt_callback(BTA_GATTC_ENC_CMPL_CB_EVT, (tBTA_GATTC*)&event_data);
  }

  void SetEncryptionResult(const RawAddress& address, bool success) {
    encryption_result = success;

    ON_CALL(btm_interface, BTM_IsEncrypted(address, _))
        .WillByDefault(DoAll(Return(encryption_result)));

    ON_CALL(btm_interface, IsLinkKeyKnown(address, _))
        .WillByDefault(DoAll(Return(true)));
  }

  void InjectNotifyReadPresetResponse(uint16_t conn_id,
                                      RawAddress const& address,
                                      uint16_t handle, const HasPreset& preset,
                                      bool indicate, bool is_last) {
    std::vector<uint8_t> value;

    value.push_back(
        static_cast<std::underlying_type_t<::le_audio::has::PresetCtpOpcode>>(
            ::le_audio::has::PresetCtpOpcode::READ_PRESET_RESPONSE));
    value.push_back(is_last ? 0x01 : 0x00);

    preset.ToCharacteristicValue(value);
    InjectNotificationEvent(address, conn_id, handle, value, indicate);
  }

  void InjectPresetChanged(uint16_t conn_id, RawAddress const& address,
                           bool indicate, const HasPreset& preset,
                           uint8_t prev_index,
                           ::le_audio::has::PresetCtpChangeId change_id,
                           bool is_last) {
    std::vector<uint8_t> value;

    value.push_back(
        static_cast<std::underlying_type_t<::le_audio::has::PresetCtpOpcode>>(
            ::le_audio::has::PresetCtpOpcode::PRESET_CHANGED));
    value.push_back(static_cast<uint8_t>(change_id));
    value.push_back(is_last ? 0x01 : 0x00);

    switch (change_id) {
      case ::le_audio::has::PresetCtpChangeId::PRESET_GENERIC_UPDATE:
        value.push_back(prev_index);
        preset.ToCharacteristicValue(value);
        break;
      case ::le_audio::has::PresetCtpChangeId::PRESET_DELETED:
      case ::le_audio::has::PresetCtpChangeId::PRESET_AVAILABLE:
      case ::le_audio::has::PresetCtpChangeId::PRESET_UNAVAILABLE:
      default:
        value.push_back(preset.GetIndex());
        break;
    }

    InjectNotificationEvent(address, conn_id, HasDbBuilder::kPresetsCtpValHdl,
                            value, indicate);
  }

  void InjectNotifyReadPresetsResponse(
      uint16_t conn_id, RawAddress const& address, uint16_t handle,
      std::vector<uint8_t> value, bool indicate, int index, int num_of_indices,
      GATT_WRITE_OP_CB cb, void* cb_data) {
    auto presets = current_peer_presets_.at(conn_id);
    LOG_ASSERT(!presets.empty()) << __func__ << " Mocking error!";

    /* Index is a start index, not necessary is a valid index for the
     * peer device */
    auto preset = presets.find(index);
    while (preset == presets.end() &&
           index++ <= ::le_audio::has::kMaxNumOfPresets) {
      preset = presets.find(index);
    }

    if (preset == presets.end()) {
      /* operation not possible */
      if (cb)
        cb(conn_id, (tGATT_STATUS)0x83, handle, value.size(), value.data(),
           cb_data);

      return;
    }

    if (cb)
      cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(), cb_data);
    /* Notify presets */
    int num_of_notif = 1;
    while (1) {
      bool last =
          preset == std::prev(presets.end()) || num_of_notif == num_of_indices;
      InjectNotifyReadPresetResponse(conn_id, address, handle, *preset,
                                     indicate, (last));
      if (last) return;

      num_of_notif++;
      preset++;
    }
  }

  void InjectActivePresetNotification(uint16_t conn_id,
                                      RawAddress const& address,
                                      uint16_t handle,
                                      std::vector<uint8_t> wr_value,
                                      uint8_t index, GATT_WRITE_OP_CB cb,
                                      void* cb_data) {
    auto presets = current_peer_presets_.at(conn_id);
    LOG_ASSERT(!presets.empty()) << __func__ << " Mocking error!";

    auto preset = presets.find(index);
    if (preset == presets.end()) {
      /* preset operation not possible */
      if (cb)
        cb(conn_id, (tGATT_STATUS)0x83, handle, wr_value.size(),
           wr_value.data(), cb_data);
      return;
    }

    std::vector<uint8_t> value;
    value.push_back(index);
    InjectNotificationEvent(
        address, conn_id, HasDbBuilder::kActivePresetIndexValHdl, value, false);
  }

  void SetSampleDatabaseHasNoFeatures(const RawAddress& address) {
    HasDbBuilder builder = {
        .has = true,
        .features = false,
        .features_ntf = false,
        .preset_cp = true,
        .preset_cp_ntf = false,
        .preset_cp_ind = true,
        .active_preset_idx = true,
        .active_preset_idx_ntf = true,
    };
    set_sample_database(address, builder);
  }

  void SetSampleDatabaseHasNoPresetChange(const RawAddress& address,
                                          uint8_t features_value = 0x00) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = false,
        .preset_cp = false,
        .preset_cp_ntf = false,
        .preset_cp_ind = false,
        .active_preset_idx = false,
        .active_preset_idx_ntf = false,
    };
    set_sample_database(address, builder, features_value);
  }

  void SetSampleDatabaseHasNoOptionalNtf(const RawAddress& address,
                                         uint8_t features = 0x00) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = false,
        .preset_cp = true,
        .preset_cp_ntf = false,
        .preset_cp_ind = true,
        .active_preset_idx = true,
        .active_preset_idx_ntf = true,
    };
    set_sample_database(address, builder, features);
  }

  void SetSampleDatabaseNoHas(const RawAddress& address,
                              uint8_t features = 0x00) {
    HasDbBuilder builder = {
        .has = false,
        .features = false,
        .features_ntf = false,
        .preset_cp = false,
        .preset_cp_ntf = false,
        .preset_cp_ind = false,
        .active_preset_idx = true,
        .active_preset_idx_ntf = true,
    };
    set_sample_database(address, builder, features);
  }

  void SetSampleDatabaseHasBrokenNoActivePreset(const RawAddress& address,
                                                uint8_t features = 0x00) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = false,
        .preset_cp = true,
        .preset_cp_ntf = true,
        .preset_cp_ind = true,
        .active_preset_idx = false,
        .active_preset_idx_ntf = false,
    };
    set_sample_database(address, builder, features);
  }

  void SetSampleDatabaseHasBrokenNoActivePresetNtf(const RawAddress& address,
                                                   uint8_t features = 0x00) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = false,
        .preset_cp = true,
        .preset_cp_ntf = true,
        .preset_cp_ind = true,
        .active_preset_idx = true,
        .active_preset_idx_ntf = false,
    };
    set_sample_database(address, builder, features);
  }

  void SetSampleDatabaseHasOnlyFeaturesNtf(const RawAddress& address,
                                           uint8_t features = 0x00) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = true,
        .preset_cp = false,
        .preset_cp_ntf = false,
        .preset_cp_ind = false,
        .active_preset_idx = false,
        .active_preset_idx_ntf = false,
    };
    set_sample_database(address, builder, features);
  }

  void SetSampleDatabaseHasOnlyFeaturesNoNtf(const RawAddress& address,
                                             uint8_t features = 0x00) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = false,
        .preset_cp = false,
        .preset_cp_ntf = false,
        .preset_cp_ind = false,
        .active_preset_idx = false,
        .active_preset_idx_ntf = false,
    };
    set_sample_database(address, builder, features);
  }

  void SetSampleDatabaseHasPresetsNtf(
      const RawAddress& address,
      uint8_t features = bluetooth::has::kFeatureBitHearingAidTypeMonaural,
      std::optional<std::set<HasPreset, HasPreset::ComparatorDesc>> presets =
          std::nullopt) {
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = true,
        .preset_cp = true,
        .preset_cp_ntf = true,
        .preset_cp_ind = true,
        .active_preset_idx = true,
        .active_preset_idx_ntf = true,
    };

    set_sample_database(address, builder, features, presets);
  }

  void SetSampleDatabaseHasNoPresetsFlagsOnly(const RawAddress& address) {
    uint8_t features = bluetooth::has::kFeatureBitHearingAidTypeMonaural;
    HasDbBuilder builder = {
        .has = true,
        .features = true,
        .features_ntf = true,
        .preset_cp = false,
        .preset_cp_ntf = false,
        .preset_cp_ind = false,
        .active_preset_idx = false,
        .active_preset_idx_ntf = false,
    };

    set_sample_database(address, builder, features, std::nullopt);
  }

  std::unique_ptr<MockHasCallbacks> callbacks;
  bluetooth::manager::MockBtmInterface btm_interface;
  bluetooth::storage::MockBtifStorageInterface btif_storage_interface_;
  controller::MockControllerInterface controller_interface_;
  gatt::MockBtaGattInterface gatt_interface;
  gatt::MockBtaGattQueue gatt_queue;
  MockCsisClient mock_csis_client_module_;
  tBTA_GATTC_CBACK* gatt_callback;
  const uint8_t gatt_if = 0xfe;
  std::map<uint8_t, RawAddress> connected_devices;
  std::map<uint16_t, std::list<gatt::Service>> services_map;
  bool encryption_result;
};

class HasClientTest : public HasClientTestBase {
  void SetUp(void) override {
    HasClientTestBase::SetUp();
    TestAppRegister();
  }
  void TearDown(void) override {
    TestAppUnregister();
    HasClientTestBase::TearDown();
  }
};

TEST_F(HasClientTestBase, test_get_uninitialized) {
  ASSERT_DEATH(HasClient::Get(), "");
}

TEST_F(HasClientTestBase, test_initialize) {
  HasClient::Initialize(callbacks.get(), base::DoNothing());
  ASSERT_TRUE(HasClient::IsHasClientRunning());
  HasClient::CleanUp();
}

TEST_F(HasClientTestBase, test_initialize_twice) {
  HasClient::Initialize(callbacks.get(), base::DoNothing());
  HasClient* has_p = HasClient::Get();
  HasClient::Initialize(callbacks.get(), base::DoNothing());
  ASSERT_EQ(has_p, HasClient::Get());
  HasClient::CleanUp();
}

TEST_F(HasClientTestBase, test_cleanup_initialized) {
  HasClient::Initialize(callbacks.get(), base::DoNothing());
  HasClient::CleanUp();
  ASSERT_FALSE(HasClient::IsHasClientRunning());
}

TEST_F(HasClientTestBase, test_cleanup_uninitialized) {
  HasClient::CleanUp();
  ASSERT_FALSE(HasClient::IsHasClientRunning());
}

TEST_F(HasClientTestBase, test_app_registration) {
  TestAppRegister();
  TestAppUnregister();
}

TEST_F(HasClientTest, test_connect) { TestConnect(GetTestAddress(1)); }

TEST_F(HasClientTest, test_add_from_storage) {
  TestAddFromStorage(GetTestAddress(1), 0, true);
  TestAddFromStorage(GetTestAddress(2), 0, false);
}

TEST_F(HasClientTest, test_disconnect_non_connected) {
  const RawAddress test_address = GetTestAddress(1);

  /* Override the default action to prevent us sendind the connected event */
  EXPECT_CALL(gatt_interface,
              Open(gatt_if, test_address, BTM_BLE_DIRECT_CONNECTION, _))
      .WillOnce(Return());
  HasClient::Get()->Connect(test_address);
  TestDisconnect(test_address, GATT_INVALID_CONN_ID);
}

TEST_F(HasClientTest, test_has_connected) {
  const RawAddress test_address = GetTestAddress(1);
  /* Minimal possible HA device (only feature flags) */
  SetSampleDatabaseHasNoPresetChange(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  EXPECT_CALL(
      *callbacks,
      OnDeviceAvailable(test_address,
                        bluetooth::has::kFeatureBitHearingAidTypeBinaural));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  TestConnect(test_address);
}

TEST_F(HasClientTest, test_disconnect_connected) {
  const RawAddress test_address = GetTestAddress(1);
  /* Minimal possible HA device (only feature flags) */
  SetSampleDatabaseHasNoPresetChange(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);
  TestConnect(test_address);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(1);
  TestDisconnect(test_address, 1);
}

TEST_F(HasClientTest, test_disconnected_while_autoconnect) {
  const RawAddress test_address = GetTestAddress(1);
  TestAddFromStorage(test_address,
                     bluetooth::has::kFeatureBitHearingAidTypeBinaural, true);
  /* autoconnect - don't indicate disconnection */
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(0);
  /* Verify that the device still can connect in te background */
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER, true);
}

TEST_F(HasClientTest, test_encryption_failed) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasNoPresetChange(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);
  SetEncryptionResult(test_address, false);
  TestConnect(test_address);
}

TEST_F(HasClientTest, test_service_discovery_complete_before_encryption) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(0);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);

  SetEncryptionResult(test_address, false);
  ON_CALL(btm_interface, SetEncryption(_, _, _, _, _))
      .WillByDefault(Return(BTM_SUCCESS));

  TestConnect(test_address);
  auto test_conn_id = GetTestConnId(test_address);
  InjectSearchCompleteEvent(test_conn_id);

  Mock::VerifyAndClearExpectations(callbacks.get());

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);

  SetEncryptionResult(test_address, true);
  InjectEncryptionEvent(test_address);
  Mock::VerifyAndClearExpectations(callbacks.get());
}

TEST_F(HasClientTest, test_disconnect_when_link_key_is_gone) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(0);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);

  ON_CALL(btm_interface, BTM_IsEncrypted(test_address, _))
      .WillByDefault(DoAll(Return(false)));
  ON_CALL(btm_interface, SetEncryption(test_address, _, _, _, _))
      .WillByDefault(Return(BTM_ERR_KEY_MISSING));

  auto test_conn_id = GetTestConnId(test_address);
  EXPECT_CALL(gatt_interface, Close(test_conn_id)).Times(1);
  InjectConnectedEvent(test_address, GetTestConnId(test_address));

  Mock::VerifyAndClearExpectations(callbacks.get());
}

TEST_F(HasClientTest, test_reconnect_after_encryption_failed) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasNoPresetChange(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);
  SetEncryptionResult(test_address, false);
  TestConnect(test_address);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);
  SetEncryptionResult(test_address, true);
  InjectConnectedEvent(test_address, GetTestConnId(test_address));
}

TEST_F(HasClientTest, test_reconnect_after_encryption_failed_from_storage) {
  const RawAddress test_address = GetTestAddress(1);

  SetSampleDatabaseHasNoPresetChange(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBinaural);
  SetEncryptionResult(test_address, false);
  TestAddFromStorage(test_address, 0, true);
  /* autoconnect - don't indicate disconnection */
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address))
      .Times(0);
  Mock::VerifyAndClearExpectations(&btm_interface);

  /* Fake no persistent storage data */
  ON_CALL(btif_storage_interface_, GetLeaudioHasPresets(_, _, _))
      .WillByDefault([]() { return false; });

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);
  SetEncryptionResult(test_address, true);
  InjectConnectedEvent(test_address, GetTestConnId(test_address));
}

TEST_F(HasClientTest, test_load_from_storage_and_connect) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(test_address, kFeatureBitDynamicPresets);
  SetEncryptionResult(test_address, true);

  std::set<HasPreset, HasPreset::ComparatorDesc> has_presets = {{
      HasPreset(5, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "YourWritablePreset5"),
      HasPreset(55, HasPreset::kPropertyAvailable, "YourPreset55"),
  }};

  /* Load persistent storage data */
  ON_CALL(btif_storage_interface_, GetLeaudioHasPresets(test_address, _, _))
      .WillByDefault([&has_presets](const RawAddress& address,
                                    std::vector<uint8_t>& presets_bin,
                                    uint8_t& active_preset) {
        /* Generate presets binary to be used instead the attribute values */
        HasDevice device(address, 0);
        device.has_presets = has_presets;
        active_preset = 55;

        if (device.SerializePresets(presets_bin)) return true;

        return false;
      });

  EXPECT_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
      .Times(1      // preset control point
             + 1    // active preset
             + 1);  // features

  EXPECT_CALL(*callbacks,
              OnDeviceAvailable(test_address,
                                (kFeatureBitWritablePresets |
                                 kFeatureBitPresetSynchronizationSupported |
                                 kFeatureBitHearingAidTypeBanded)));

  std::vector<PresetInfo> loaded_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .WillOnce(SaveArg<2>(&loaded_preset_details));

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address), 55));

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));

  /* Expect no read or write operations when loading from storage */
  EXPECT_CALL(gatt_queue, ReadCharacteristic(1, _, _, _)).Times(0);
  EXPECT_CALL(gatt_queue, WriteDescriptor(1, _, _, _, _, _)).Times(3);

  TestAddFromStorage(test_address,
                     kFeatureBitWritablePresets |
                         kFeatureBitPresetSynchronizationSupported |
                         kFeatureBitHearingAidTypeBanded,
                     true);

  for (auto const& info : loaded_preset_details) {
    auto preset = has_presets.find(info.preset_index);
    ASSERT_NE(preset, has_presets.end());
    if (preset->GetProperties() & HasPreset::kPropertyAvailable)
      ASSERT_TRUE(info.available);
    if (preset->GetProperties() & HasPreset::kPropertyWritable)
      ASSERT_TRUE(info.writable);
    ASSERT_EQ(preset->GetName(), info.preset_name);
  }
}

TEST_F(HasClientTest, test_load_from_storage) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(test_address, kFeatureBitDynamicPresets);
  SetEncryptionResult(test_address, true);

  std::set<HasPreset, HasPreset::ComparatorDesc> has_presets = {{
      HasPreset(5, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "YourWritablePreset5"),
      HasPreset(55, HasPreset::kPropertyAvailable, "YourPreset55"),
  }};

  /* Load persistent storage data */
  ON_CALL(btif_storage_interface_, GetLeaudioHasPresets(test_address, _, _))
      .WillByDefault([&has_presets](const RawAddress& address,
                                    std::vector<uint8_t>& presets_bin,
                                    uint8_t& active_preset) {
        /* Generate presets binary to be used instead the attribute values */
        HasDevice device(address, 0);
        device.has_presets = has_presets;
        active_preset = 55;

        if (device.SerializePresets(presets_bin)) return true;

        return false;
      });

  EXPECT_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
      .Times(0);  // features

  EXPECT_CALL(*callbacks,
              OnDeviceAvailable(test_address,
                                (kFeatureBitWritablePresets |
                                 kFeatureBitPresetSynchronizationSupported |
                                 kFeatureBitHearingAidTypeBanded)));

  std::vector<PresetInfo> loaded_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(0);

  /* Expect no read or write operations when loading from storage */
  EXPECT_CALL(gatt_queue, ReadCharacteristic(1, _, _, _)).Times(0);
  EXPECT_CALL(gatt_queue, WriteDescriptor(1, _, _, _, _, _)).Times(0);

  TestAddFromStorage(test_address,
                     kFeatureBitWritablePresets |
                         kFeatureBitPresetSynchronizationSupported |
                         kFeatureBitHearingAidTypeBanded,
                     false);
}

TEST_F(HasClientTest, test_write_to_storage) {
  const RawAddress test_address = GetTestAddress(1);

  std::set<HasPreset, HasPreset::ComparatorDesc> has_presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      has_presets);

  std::vector<uint8_t> serialized;
  EXPECT_CALL(
      btif_storage_interface_,
      AddLeaudioHasDevice(test_address, _,
                          bluetooth::has::kFeatureBitHearingAidTypeBanded |
                              bluetooth::has::kFeatureBitWritablePresets |
                              bluetooth::has::kFeatureBitDynamicPresets,
                          1))
      .WillOnce(SaveArg<1>(&serialized));
  TestConnect(test_address);

  /* Deserialize the written binary to verify the content */
  HasDevice clone(test_address,
                  bluetooth::has::kFeatureBitHearingAidTypeBanded |
                      bluetooth::has::kFeatureBitWritablePresets |
                      bluetooth::has::kFeatureBitDynamicPresets);
  ASSERT_TRUE(HasDevice::DeserializePresets(serialized.data(),
                                            serialized.size(), clone));
  auto storage_info = clone.GetAllPresetInfo();
  ASSERT_EQ(storage_info.size(), has_presets.size());
  for (auto const& info : storage_info) {
    auto preset = has_presets.find(info.preset_index);
    ASSERT_NE(preset, has_presets.end());
    if (preset->GetProperties() & HasPreset::kPropertyAvailable)
      ASSERT_TRUE(info.available);
    if (preset->GetProperties() & HasPreset::kPropertyWritable)
      ASSERT_TRUE(info.writable);
    ASSERT_EQ(preset->GetName(), info.preset_name);
  }
}

TEST_F(HasClientTest, test_discovery_basic_has_no_opt_ntf) {
  const RawAddress test_address = GetTestAddress(1);
  auto test_conn_id = GetTestConnId(test_address);

  SetSampleDatabaseHasNoOptionalNtf(test_address);

  std::variant<RawAddress, int> addr_or_group = test_address;
  std::vector<PresetInfo> preset_details;
  uint8_t active_preset_index;
  uint8_t has_features;

  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _))
      .WillOnce(SaveArg<1>(&has_features));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks, OnPresetInfo(_, PresetInfoReason::ALL_PRESET_INFO, _))
      .WillOnce(DoAll(SaveArg<0>(&addr_or_group), SaveArg<2>(&preset_details)));
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(
          DoAll(SaveArg<0>(&addr_or_group), SaveArg<1>(&active_preset_index)));
  TestConnect(test_address);

  /* Verify sample database content */
  ASSERT_TRUE(std::holds_alternative<RawAddress>(addr_or_group));
  ASSERT_EQ(std::get<RawAddress>(addr_or_group), test_address);
  ASSERT_EQ(has_features, 0x00);
  ASSERT_EQ(active_preset_index,
            current_peer_presets_.at(test_conn_id).begin()->GetIndex());

  /* Verify presets */
  uint16_t conn_id = GetTestConnId(test_address);
  ASSERT_NE(preset_details.size(), 0u);
  ASSERT_EQ(current_peer_presets_.at(conn_id).size(), preset_details.size());

  for (auto const& preset : current_peer_presets_.at(conn_id)) {
    auto it =
        std::find_if(preset_details.cbegin(), preset_details.cend(),
                     [&preset](auto const& preset_info) {
                       return preset_info.preset_index == preset.GetIndex();
                     });
    ASSERT_NE(it, preset_details.cend());
    ASSERT_EQ(preset.GetName(), it->preset_name);
    ASSERT_EQ(preset.IsAvailable(), it->available);
    ASSERT_EQ(preset.IsWritable(), it->writable);
  }

  /* Verify active preset is there */
  ASSERT_EQ(preset_details.size(),
            current_peer_presets_.at(test_conn_id).size());
  ASSERT_TRUE(std::find_if(preset_details.begin(), preset_details.end(),
                           [active_preset_index](auto const& preset_info) {
                             return preset_info.preset_index ==
                                    active_preset_index;
                           }) != preset_details.end());
}

TEST_F(HasClientTest, test_discovery_has_not_found) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseNoHas(test_address);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _)).Times(0);
  EXPECT_CALL(*callbacks, OnFeaturesUpdate(test_address, _)).Times(0);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address));

  TestConnect(test_address);
}

TEST_F(HasClientTest, test_discovery_has_broken_no_active_preset) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasBrokenNoActivePreset(test_address);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _)).Times(0);
  EXPECT_CALL(*callbacks, OnFeaturesUpdate(test_address, _)).Times(0);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address));

  TestConnect(test_address);
}

TEST_F(HasClientTest, test_discovery_has_broken_no_active_preset_ntf) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasBrokenNoActivePresetNtf(test_address);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(0);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _)).Times(0);
  EXPECT_CALL(*callbacks, OnFeaturesUpdate(test_address, _)).Times(0);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address));

  TestConnect(test_address);
}

TEST_F(HasClientTest, test_discovery_has_features_ntf) {
  const RawAddress test_address = GetTestAddress(1);
  auto test_conn_id = GetTestConnId(test_address);
  uint8_t has_features;

  SetSampleDatabaseHasOnlyFeaturesNtf(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBanded);

  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _))
      .WillOnce(SaveArg<1>(&has_features));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);

  /* Verify subscription to features */
  EXPECT_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(gatt_interface,
              RegisterForNotifications(gatt_if, test_address,
                                       HasDbBuilder::kFeaturesValHdl));

  /* Verify features CCC was written */
  EXPECT_CALL(gatt_queue, WriteDescriptor(test_conn_id, _, _, _, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(gatt_queue,
              WriteDescriptor(test_conn_id, HasDbBuilder::kFeaturesValHdl + 1,
                              std::vector<uint8_t>{0x01, 0x00}, _, _, _));
  TestConnect(test_address);

  /* Verify features */
  ASSERT_EQ(has_features, bluetooth::has::kFeatureBitHearingAidTypeBanded);

  uint8_t new_features;

  /* Verify peer features change notification */
  EXPECT_CALL(*callbacks, OnFeaturesUpdate(test_address, _))
      .WillOnce(SaveArg<1>(&new_features));
  InjectNotificationEvent(test_address, test_conn_id,
                          HasDbBuilder::kFeaturesValHdl,
                          std::vector<uint8_t>({0x00}));
  ASSERT_NE(has_features, new_features);
}

TEST_F(HasClientTest, test_discovery_has_features_no_ntf) {
  const RawAddress test_address = GetTestAddress(1);
  auto test_conn_id = GetTestConnId(test_address);
  uint8_t has_features;

  SetSampleDatabaseHasOnlyFeaturesNoNtf(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBanded);

  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _))
      .WillOnce(SaveArg<1>(&has_features));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);

  /* Verify no subscription to features */
  EXPECT_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(gatt_interface,
              RegisterForNotifications(gatt_if, test_address,
                                       HasDbBuilder::kFeaturesValHdl))
      .Times(0);

  /* Verify no features CCC was written */
  EXPECT_CALL(gatt_queue, WriteDescriptor(test_conn_id, _, _, _, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(gatt_queue,
              WriteDescriptor(test_conn_id, HasDbBuilder::kFeaturesValHdl + 1,
                              _, _, _, _))
      .Times(0);
  TestConnect(test_address);

  /* Verify features */
  ASSERT_EQ(has_features, bluetooth::has::kFeatureBitHearingAidTypeBanded);
}

TEST_F(HasClientTest, test_discovery_has_multiple_presets_ntf) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address, bluetooth::has::kFeatureBitHearingAidTypeBanded);

  std::variant<RawAddress, int> addr_or_group = test_address;
  std::vector<PresetInfo> preset_details;
  uint8_t active_preset_index;
  uint8_t has_features;

  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _))
      .WillOnce(SaveArg<1>(&has_features));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks, OnPresetInfo(_, PresetInfoReason::ALL_PRESET_INFO, _))
      .WillOnce(DoAll(SaveArg<0>(&addr_or_group), SaveArg<2>(&preset_details)));
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(
          DoAll(SaveArg<0>(&addr_or_group), SaveArg<1>(&active_preset_index)));

  /* Verify subscription to control point */
  EXPECT_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(gatt_interface,
              RegisterForNotifications(gatt_if, test_address,
                                       HasDbBuilder::kPresetsCtpValHdl));

  /* Verify features CCC was written */
  EXPECT_CALL(gatt_queue, WriteDescriptor(1, _, _, _, _, _)).Times(AnyNumber());
  EXPECT_CALL(gatt_queue,
              WriteDescriptor(1, HasDbBuilder::kPresetsCtpValHdl + 1,
                              std::vector<uint8_t>{0x03, 0x00}, _, _, _));
  TestConnect(test_address);

  /* Verify features */
  ASSERT_EQ(has_features, bluetooth::has::kFeatureBitHearingAidTypeBanded);
}

TEST_F(HasClientTest, test_active_preset_change) {
  const RawAddress test_address = GetTestAddress(1);
  auto test_conn_id = GetTestConnId(test_address);

  SetSampleDatabaseHasNoOptionalNtf(test_address);

  uint8_t active_preset_index;
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(_, PresetInfoReason::ALL_PRESET_INFO, _));
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(SaveArg<1>(&active_preset_index));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  TestConnect(test_address);

  uint8_t new_active_preset;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address), _))
      .WillOnce(SaveArg<1>(&new_active_preset));
  InjectNotificationEvent(test_address, test_conn_id,
                          HasDbBuilder::kActivePresetIndexValHdl,
                          std::vector<uint8_t>({0x00}));

  ASSERT_NE(active_preset_index, new_active_preset);
  ASSERT_EQ(new_active_preset, 0x00);
}

TEST_F(HasClientTest, test_duplicate_presets) {
  const RawAddress test_address = GetTestAddress(1);
  std::vector<PresetInfo> preset_details;

  /* Handle duplicates gracefully */
  SetSampleDatabaseHasPresetsNtf(
      test_address, kFeatureBitWritablePresets,
      {{HasPreset(5,
                  HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                  "YourWritablePreset5"),
        HasPreset(5,
                  HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                  "YourWritablePreset5")}});

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks, OnPresetInfo(_, PresetInfoReason::ALL_PRESET_INFO, _))
      .WillOnce(SaveArg<2>(&preset_details));
  TestConnect(test_address);

  /* Verify presets - expect 1, no duplicates */
  ASSERT_EQ(preset_details.size(), 1u);
  auto preset = std::find_if(
      preset_details.begin(), preset_details.end(),
      [](auto const& preset_info) { return preset_info.preset_index == 5; });
  ASSERT_TRUE(preset != preset_details.end());
  ASSERT_EQ("YourWritablePreset5", preset->preset_name);
  ASSERT_TRUE(preset->available);
  ASSERT_TRUE(preset->writable);
}

TEST_F(HasClientTest, test_preset_set_name_invalid_index) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(test_address);
  TestConnect(test_address);

  EXPECT_CALL(*callbacks,
              OnSetPresetNameError(std::variant<RawAddress, int>(test_address),
                                   0x40, ErrorCode::INVALID_PRESET_INDEX))
      .Times(1);
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, HasDbBuilder::kPresetsCtpValHdl, _,
                                  GATT_WRITE, _, _))
      .Times(0);

  HasClient::Get()->SetPresetName(test_address, 0x40, "new preset name");
}

TEST_F(HasClientTest, test_preset_set_name_non_writable) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  SetSampleDatabaseHasPresetsNtf(
      test_address, kFeatureBitWritablePresets,
      {{
          HasPreset(5, HasPreset::kPropertyAvailable, "YourPreset5"),
          HasPreset(
              55, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
              "YourWritablePreset55"),
      }});
  TestConnect(test_address);

  EXPECT_CALL(*callbacks,
              OnSetPresetNameError(_, _, ErrorCode::SET_NAME_NOT_ALLOWED))
      .Times(1);
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, HasDbBuilder::kPresetsCtpValHdl, _,
                                  GATT_WRITE, _, _))
      .Times(0);

  HasClient::Get()->SetPresetName(
      test_address, current_peer_presets_.at(test_conn_id).begin()->GetIndex(),
      "new preset name");
}

TEST_F(HasClientTest, test_preset_set_name_to_long) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  SetSampleDatabaseHasPresetsNtf(
      test_address, kFeatureBitWritablePresets,
      {{HasPreset(5,
                  HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                  "YourWritablePreset")}});
  TestConnect(test_address);

  EXPECT_CALL(*callbacks,
              OnSetPresetNameError(_, _, ErrorCode::INVALID_PRESET_NAME_LENGTH))
      .Times(1);
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(test_conn_id, HasDbBuilder::kPresetsCtpValHdl,
                                  _, GATT_WRITE, _, _))
      .Times(0);

  HasClient::Get()->SetPresetName(test_address, 5,
                                  "this name is more than 40 characters long");
}

TEST_F(HasClientTest, test_preset_set_name) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  SetSampleDatabaseHasPresetsNtf(
      test_address, kFeatureBitWritablePresets,
      {{HasPreset(5,
                  HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                  "YourWritablePreset5")}});

  TestConnect(test_address);

  std::vector<uint8_t> value;
  EXPECT_CALL(*callbacks, OnSetPresetNameError(_, _, _)).Times(0);
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(test_conn_id, HasDbBuilder::kPresetsCtpValHdl,
                                  _, GATT_WRITE, _, _));

  std::vector<PresetInfo> updated_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_INFO_UPDATE, _))
      .WillOnce(SaveArg<2>(&updated_preset_details));
  HasClient::Get()->SetPresetName(test_address, 5, "new preset name");

  ASSERT_EQ(1u, updated_preset_details.size());
  ASSERT_EQ(updated_preset_details[0].preset_name, "new preset name");
}

TEST_F(HasClientTest, test_preset_group_set_name) {
  /* None of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural |
                         bluetooth::has::kFeatureBitWritablePresets);

  const RawAddress test_address2 = GetTestAddress(2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2, bluetooth::has::kFeatureBitHearingAidTypeBinaural |
                         bluetooth::has::kFeatureBitWritablePresets);

  TestConnect(test_address1);
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t not_synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(not_synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));

  std::vector<PresetInfo> preset_details;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), 55))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), 55))
      .Times(0);

  /* This should be a group callback */
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(not_synced_group),
                           PresetInfoReason::PRESET_INFO_UPDATE, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));

  /* No locally synced opcodes support so expect both devices getting writes */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address1),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address2),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->SetPresetName(not_synced_group, 55, "new preset name");
  ASSERT_EQ(preset_details.size(), 1u);
  ASSERT_EQ(preset_details[0].preset_name, "new preset name");
  ASSERT_EQ(preset_details[0].preset_index, 55);
}

TEST_F(HasClientTest, test_multiple_presets_get_name) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address, kFeatureBitWritablePresets,
      {{
          HasPreset(
              5, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
              "YourWritablePreset5"),
          HasPreset(55, HasPreset::kPropertyAvailable, "YourPreset55"),
          HasPreset(99, 0, "YourPreset99"),
      }});

  std::vector<PresetInfo> preset_details;

  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks, OnPresetInfo(_, PresetInfoReason::ALL_PRESET_INFO, _))
      .WillOnce(SaveArg<2>(&preset_details));
  TestConnect(test_address);

  /* Get each preset info individually */
  for (auto const& preset : preset_details) {
    std::vector<PresetInfo> new_preset_details;

    EXPECT_CALL(*callbacks,
                OnPresetInfo(std::variant<RawAddress, int>(test_address),
                             PresetInfoReason::PRESET_INFO_REQUEST_RESPONSE, _))
        .Times(1)
        .WillOnce(SaveArg<2>(&new_preset_details));
    HasClient::Get()->GetPresetInfo(test_address, preset.preset_index);

    Mock::VerifyAndClearExpectations(&*callbacks);
    ASSERT_EQ(1u, new_preset_details.size());
    ASSERT_EQ(preset.preset_index, new_preset_details[0].preset_index);
    ASSERT_EQ(preset.preset_name, new_preset_details[0].preset_name);
    ASSERT_EQ(preset.writable, new_preset_details[0].writable);
    ASSERT_EQ(preset.available, new_preset_details[0].available);
  }
}

TEST_F(HasClientTest, test_presets_get_name_invalid_index) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(test_address);
  TestConnect(test_address);

  EXPECT_CALL(*callbacks,
              OnPresetInfoError(std::variant<RawAddress, int>(test_address),
                                128, ErrorCode::INVALID_PRESET_INDEX));
  HasClient::Get()->GetPresetInfo(test_address, 128);

  EXPECT_CALL(*callbacks,
              OnPresetInfoError(std::variant<RawAddress, int>(test_address), 0,
                                ErrorCode::INVALID_PRESET_INDEX));
  HasClient::Get()->GetPresetInfo(test_address, 0);
}

TEST_F(HasClientTest, test_presets_changed_generic_update_no_add_or_delete) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
      HasPreset(4, HasPreset::kPropertyAvailable, "Preset4"),
      HasPreset(7, HasPreset::kPropertyAvailable, "Preset7"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitDynamicPresets |
          bluetooth::has::kFeatureBitWritablePresets,
      presets);

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  TestConnect(test_address);

  std::vector<PresetInfo> preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_INFO_UPDATE, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));

  /* Inject generic update on the first preset */
  auto preset_index = 2;
  auto new_test_preset = HasPreset(preset_index, 0, "props new name");
  ASSERT_NE(*current_peer_presets_.at(test_conn_id).find(preset_index),
            new_test_preset);

  InjectPresetChanged(test_conn_id, test_address, false, new_test_preset,
                      1 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_GENERIC_UPDATE,
                      true /* is_last */);

  /* Verify received preset info update on the 2nd preset */
  ASSERT_EQ(1u, preset_details.size());
  ASSERT_EQ(new_test_preset.GetIndex(), preset_details[0].preset_index);
  ASSERT_EQ(new_test_preset.IsAvailable(), preset_details[0].available);
  ASSERT_EQ(new_test_preset.IsWritable(), preset_details[0].writable);
  ASSERT_EQ(new_test_preset.GetName(), preset_details[0].preset_name);
}

TEST_F(HasClientTest, test_presets_changed_generic_update_add_and_delete) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
      HasPreset(4, HasPreset::kPropertyAvailable, "Preset4"),
      HasPreset(5, HasPreset::kPropertyAvailable, "Preset5"),
      HasPreset(32, HasPreset::kPropertyAvailable, "Preset32"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets,
      presets);

  std::vector<PresetInfo> preset_details;
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  TestConnect(test_address);

  /* Expect more OnPresetInfo call */
  std::vector<PresetInfo> updated_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_INFO_UPDATE, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&updated_preset_details));

  /* Expect more OnPresetInfo call */
  std::vector<PresetInfo> deleted_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_DELETED, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&deleted_preset_details));

  /* Inject generic updates */
  /* First event replaces all the existing presets from 1 to 8 with preset 8
   */
  auto new_test_preset1 =
      HasPreset(8, HasPreset::kPropertyAvailable, "props new name9");
  InjectPresetChanged(test_conn_id, test_address, false, new_test_preset1,
                      1 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_GENERIC_UPDATE,
                      false /* is_last */);

  /* Second event adds preset 9 to the already existing presets 1 and 8 */
  auto new_test_preset2 =
      HasPreset(9, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "props new name11");
  InjectPresetChanged(test_conn_id, test_address, false, new_test_preset2,
                      8 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_GENERIC_UPDATE,
                      true /* is_last */);

  /* Verify received preset info - expect presets 1, 32 unchanged, 8, 9
   * updated, and 2, 4, 5 deleted.
   */
  ASSERT_EQ(2u, updated_preset_details.size());
  ASSERT_EQ(new_test_preset1.GetIndex(),
            updated_preset_details[0].preset_index);
  ASSERT_EQ(new_test_preset1.IsAvailable(),
            updated_preset_details[0].available);
  ASSERT_EQ(new_test_preset1.IsWritable(), updated_preset_details[0].writable);
  ASSERT_EQ(new_test_preset1.GetName(), updated_preset_details[0].preset_name);
  ASSERT_EQ(new_test_preset2.GetIndex(),
            updated_preset_details[1].preset_index);
  ASSERT_EQ(new_test_preset2.IsAvailable(),
            updated_preset_details[1].available);
  ASSERT_EQ(new_test_preset2.IsWritable(), updated_preset_details[1].writable);
  ASSERT_EQ(new_test_preset2.GetName(), updated_preset_details[1].preset_name);

  ASSERT_EQ(3u, deleted_preset_details.size());
  ASSERT_EQ(2, deleted_preset_details[0].preset_index);
  ASSERT_EQ(4, deleted_preset_details[1].preset_index);
  ASSERT_EQ(5, deleted_preset_details[2].preset_index);
}

TEST_F(HasClientTest, test_presets_changed_deleted) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  std::vector<PresetInfo> preset_details;
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  TestConnect(test_address);

  /* Expect second OnPresetInfo call */
  std::vector<PresetInfo> deleted_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_DELETED, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&deleted_preset_details));

  /* Inject preset deletion of index 2 */
  auto deleted_index = preset_details[1].preset_index;
  InjectPresetChanged(test_conn_id, test_address, false,
                      *presets.find(deleted_index), 0 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_DELETED,
                      true /* is_last */);

  ASSERT_EQ(2u, preset_details.size());
  ASSERT_EQ(1u, deleted_preset_details.size());
  ASSERT_EQ(preset_details[1].preset_index,
            deleted_preset_details[0].preset_index);
  ASSERT_EQ(preset_details[1].writable, deleted_preset_details[0].writable);
  ASSERT_EQ(preset_details[1].available, deleted_preset_details[0].available);
  ASSERT_EQ(preset_details[1].preset_name,
            deleted_preset_details[0].preset_name);
}

TEST_F(HasClientTest, test_presets_changed_available) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, 0, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  std::vector<PresetInfo> preset_details;
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  TestConnect(test_address);

  /* Expect second OnPresetInfo call */
  std::vector<PresetInfo> changed_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_AVAILABILITY_CHANGED, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&changed_preset_details));

  /* Inject preset deletion of index 2 */
  auto changed_index = preset_details[0].preset_index;
  InjectPresetChanged(test_conn_id, test_address, false,
                      *presets.find(changed_index), 0 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_AVAILABLE,
                      true /* is_last */);

  ASSERT_EQ(2u, preset_details.size());
  ASSERT_EQ(1u, changed_preset_details.size());
  ASSERT_EQ(preset_details[0].preset_index,
            changed_preset_details[0].preset_index);
  ASSERT_EQ(preset_details[0].writable, changed_preset_details[0].writable);
  ASSERT_EQ(preset_details[0].preset_name,
            changed_preset_details[0].preset_name);
  /* This field should have changed */
  ASSERT_NE(preset_details[0].available, changed_preset_details[0].available);
  ASSERT_TRUE(changed_preset_details[0].available);
}

TEST_F(HasClientTest, test_presets_changed_unavailable) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  std::vector<PresetInfo> preset_details;
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  TestConnect(test_address);

  /* Expect second OnPresetInfo call */
  std::vector<PresetInfo> changed_preset_details;
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::PRESET_AVAILABILITY_CHANGED, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&changed_preset_details));

  /* Inject preset deletion of index 2 */
  auto changed_index = preset_details[0].preset_index;
  InjectPresetChanged(test_conn_id, test_address, false,
                      *presets.find(changed_index), 0 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_UNAVAILABLE,
                      true /* is_last */);

  ASSERT_EQ(2u, preset_details.size());
  ASSERT_EQ(1u, changed_preset_details.size());
  ASSERT_EQ(preset_details[0].preset_index,
            changed_preset_details[0].preset_index);
  ASSERT_EQ(preset_details[0].writable, changed_preset_details[0].writable);
  ASSERT_EQ(preset_details[0].preset_name,
            changed_preset_details[0].preset_name);
  /* This field should have changed */
  ASSERT_NE(preset_details[0].available, changed_preset_details[0].available);
  ASSERT_FALSE(changed_preset_details[0].available);
}

TEST_F(HasClientTest, test_select_preset_valid) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(test_address);

  uint8_t active_preset_index = 0;
  std::vector<PresetInfo> preset_details;

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(SaveArg<1>(&active_preset_index));
  TestConnect(test_address);

  ASSERT_TRUE(preset_details.size() > 1);
  ASSERT_EQ(preset_details.front().preset_index, active_preset_index);

  uint8_t new_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(SaveArg<1>(&new_active_preset_index));

  HasClient::Get()->SelectActivePreset(test_address,
                                       preset_details.back().preset_index);
  Mock::VerifyAndClearExpectations(&*callbacks);

  ASSERT_NE(active_preset_index, new_active_preset_index);
  ASSERT_EQ(preset_details.back().preset_index, new_active_preset_index);
}

TEST_F(HasClientTest, test_select_group_preset_invalid_group) {
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(test_address1);

  const RawAddress test_address2 = GetTestAddress(2);
  SetSampleDatabaseHasPresetsNtf(test_address2);

  TestConnect(test_address1);
  TestConnect(test_address2);

  /* Mock the csis group with no devices */
  uint8_t unlucky_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(unlucky_group))
      .WillByDefault(Return(std::vector<RawAddress>()));

  EXPECT_CALL(*callbacks, OnActivePresetSelectError(
                              std::variant<RawAddress, int>(unlucky_group),
                              ErrorCode::OPERATION_NOT_POSSIBLE))
      .Times(1);

  HasClient::Get()->SelectActivePreset(unlucky_group, 6);
}

TEST_F(HasClientTest, test_select_group_preset_valid_no_preset_sync_supported) {
  /* None of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  const RawAddress test_address2 = GetTestAddress(2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  TestConnect(test_address1);
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t not_synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(not_synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));

  uint8_t group_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), 55))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), 55))
      .Times(0);
  EXPECT_CALL(*callbacks,
              OnActivePresetSelected(
                  std::variant<RawAddress, int>(not_synced_group), _))
      .WillOnce(SaveArg<1>(&group_active_preset_index));

  /* No locally synced opcodes support so expect both devices getting writes */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address1),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address2),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->SelectActivePreset(not_synced_group, 55);
  ASSERT_EQ(group_active_preset_index, 55);
}

TEST_F(HasClientTest, test_select_group_preset_valid_preset_sync_supported) {
  /* Only one of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  uint16_t test_conn_id1 = GetTestConnId(test_address1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  const RawAddress test_address2 = GetTestAddress(2);
  uint16_t test_conn_id2 = GetTestConnId(test_address2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2,
      bluetooth::has::kFeatureBitHearingAidTypeBinaural |
          bluetooth::has::kFeatureBitPresetSynchronizationSupported);

  uint8_t active_preset_index1 = 0;
  uint8_t active_preset_index2 = 0;

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), _))
      .WillOnce(SaveArg<1>(&active_preset_index1));
  TestConnect(test_address1);

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), _))
      .WillOnce(SaveArg<1>(&active_preset_index2));
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(synced_group));

  EXPECT_CALL(*callbacks, OnActivePresetSelectError(
                              _, ErrorCode::GROUP_OPERATION_NOT_SUPPORTED))
      .Times(0);

  /* Expect callback from the group but not from the devices */
  uint8_t group_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), _))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), _))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(synced_group), _))
      .WillOnce(SaveArg<1>(&group_active_preset_index));

  /* Expect Ctp write on on this device which forwards operation to the other */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(test_conn_id1,
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(0);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(test_conn_id2,
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->SelectActivePreset(synced_group, 55);
  ASSERT_EQ(group_active_preset_index, 55);
}

TEST_F(HasClientTest, test_select_preset_invalid) {
  const RawAddress test_address = GetTestAddress(1);
  uint16_t test_conn_id = GetTestConnId(test_address);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  uint8_t active_preset_index = 0;
  std::vector<PresetInfo> preset_details;

  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(SaveArg<1>(&active_preset_index));
  TestConnect(test_address);

  ASSERT_TRUE(preset_details.size() > 1);
  ASSERT_EQ(preset_details.front().preset_index, active_preset_index);

  /* Inject preset deletion of index 2 */
  auto deleted_index = preset_details[1].preset_index;
  InjectPresetChanged(test_conn_id, test_address, false,
                      *presets.find(deleted_index), 0 /* prev_index */,
                      ::le_audio::has::PresetCtpChangeId::PRESET_DELETED,
                      true /* is_last */);

  EXPECT_CALL(*callbacks, OnActivePresetSelectError(
                              std::variant<RawAddress, int>(test_address),
                              ErrorCode::INVALID_PRESET_INDEX))
      .Times(1);

  /* Check if preset was actually deleted - try setting it as an active one */
  HasClient::Get()->SelectActivePreset(test_address,
                                       preset_details[1].preset_index);
}

TEST_F(HasClientTest, test_select_preset_next) {
  const RawAddress test_address = GetTestAddress(1);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  uint8_t active_preset_index = 0;
  std::vector<PresetInfo> preset_details;

  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  EXPECT_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillOnce(SaveArg<1>(&active_preset_index));
  TestConnect(test_address);

  ASSERT_TRUE(preset_details.size() > 1);
  ASSERT_EQ(1, active_preset_index);

  /* Verify active preset change */
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address), 2));
  HasClient::Get()->NextActivePreset(test_address);
}

TEST_F(HasClientTest, test_select_group_preset_next_no_preset_sync_supported) {
  /* None of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  const RawAddress test_address2 = GetTestAddress(2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  TestConnect(test_address1);
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t not_synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(not_synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));

  uint8_t group_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), 55))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), 55))
      .Times(0);
  EXPECT_CALL(*callbacks,
              OnActivePresetSelected(
                  std::variant<RawAddress, int>(not_synced_group), _))
      .WillOnce(SaveArg<1>(&group_active_preset_index));

  /* No locally synced opcodes support so expect both devices getting writes */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address1),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address2),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->NextActivePreset(not_synced_group);
  ASSERT_EQ(group_active_preset_index, 55);
}

TEST_F(HasClientTest, test_select_group_preset_next_preset_sync_supported) {
  /* Only one of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  uint16_t test_conn_id1 = GetTestConnId(test_address1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  const RawAddress test_address2 = GetTestAddress(2);
  uint16_t test_conn_id2 = GetTestConnId(test_address2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2,
      bluetooth::has::kFeatureBitHearingAidTypeBinaural |
          bluetooth::has::kFeatureBitPresetSynchronizationSupported);

  uint8_t active_preset_index1 = 0;
  uint8_t active_preset_index2 = 0;

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), _))
      .WillOnce(SaveArg<1>(&active_preset_index1));
  TestConnect(test_address1);

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), _))
      .WillOnce(SaveArg<1>(&active_preset_index2));
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(synced_group));

  EXPECT_CALL(*callbacks, OnActivePresetSelectError(
                              _, ErrorCode::GROUP_OPERATION_NOT_SUPPORTED))
      .Times(0);

  /* Expect callback from the group but not from the devices */
  uint8_t group_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), _))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), _))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(synced_group), _))
      .WillOnce(SaveArg<1>(&group_active_preset_index));

  /* Expect Ctp write on on this device which forwards operation to the other */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(test_conn_id1,
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(0);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(test_conn_id2,
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->NextActivePreset(synced_group);
  ASSERT_EQ(group_active_preset_index, 55);
}

TEST_F(HasClientTest, test_select_preset_prev) {
  const RawAddress test_address = GetTestAddress(1);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  uint8_t active_preset_index = 0;
  std::vector<PresetInfo> preset_details;

  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  ON_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillByDefault(SaveArg<1>(&active_preset_index));
  TestConnect(test_address);

  HasClient::Get()->SelectActivePreset(test_address, 2);
  ASSERT_TRUE(preset_details.size() > 1);
  ASSERT_EQ(2, active_preset_index);

  /* Verify active preset change */
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address), 1));
  HasClient::Get()->PreviousActivePreset(test_address);
}

TEST_F(HasClientTest, test_select_group_preset_prev_no_preset_sync_supported) {
  /* None of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  const RawAddress test_address2 = GetTestAddress(2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  TestConnect(test_address1);
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t not_synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(not_synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(not_synced_group));

  uint8_t group_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), 55))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), 55))
      .Times(0);
  EXPECT_CALL(*callbacks,
              OnActivePresetSelected(
                  std::variant<RawAddress, int>(not_synced_group), _))
      .WillOnce(SaveArg<1>(&group_active_preset_index));

  /* No locally synced opcodes support so expect both devices getting writes */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address1),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(GetTestConnId(test_address2),
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->PreviousActivePreset(not_synced_group);
  ASSERT_EQ(group_active_preset_index, 55);
}

TEST_F(HasClientTest, test_select_group_preset_prev_preset_sync_supported) {
  /* Only one of these devices support preset syncing */
  const RawAddress test_address1 = GetTestAddress(1);
  uint16_t test_conn_id1 = GetTestConnId(test_address1);
  SetSampleDatabaseHasPresetsNtf(
      test_address1, bluetooth::has::kFeatureBitHearingAidTypeBinaural);

  const RawAddress test_address2 = GetTestAddress(2);
  uint16_t test_conn_id2 = GetTestConnId(test_address2);
  SetSampleDatabaseHasPresetsNtf(
      test_address2,
      bluetooth::has::kFeatureBitHearingAidTypeBinaural |
          bluetooth::has::kFeatureBitPresetSynchronizationSupported);

  uint8_t active_preset_index1 = 0;
  uint8_t active_preset_index2 = 0;

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), _))
      .WillOnce(SaveArg<1>(&active_preset_index1));
  TestConnect(test_address1);

  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), _))
      .WillOnce(SaveArg<1>(&active_preset_index2));
  TestConnect(test_address2);

  /* Mock the csis group with two devices */
  uint8_t synced_group = 13;
  ON_CALL(mock_csis_client_module_, GetDeviceList(synced_group))
      .WillByDefault(
          Return(std::vector<RawAddress>({{test_address1, test_address2}})));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address1, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(synced_group));
  ON_CALL(mock_csis_client_module_,
          GetGroupId(test_address2, ::le_audio::uuid::kCapServiceUuid))
      .WillByDefault(Return(synced_group));

  EXPECT_CALL(*callbacks, OnActivePresetSelectError(
                              _, ErrorCode::GROUP_OPERATION_NOT_SUPPORTED))
      .Times(0);

  /* Expect callback from the group but not from the devices */
  uint8_t group_active_preset_index = 0;
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address1), _))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(test_address2), _))
      .Times(0);
  EXPECT_CALL(*callbacks, OnActivePresetSelected(
                              std::variant<RawAddress, int>(synced_group), _))
      .WillOnce(SaveArg<1>(&group_active_preset_index));

  /* Expect Ctp write on on this device which forwards operation to the other */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(test_conn_id1,
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(0);
  EXPECT_CALL(gatt_queue, WriteCharacteristic(test_conn_id2,
                                              HasDbBuilder::kPresetsCtpValHdl,
                                              _, GATT_WRITE, _, _))
      .Times(1);

  HasClient::Get()->PreviousActivePreset(synced_group);
  ASSERT_EQ(group_active_preset_index, 55);
}

TEST_F(HasClientTest, test_select_has_no_presets) {
  const RawAddress test_address = GetTestAddress(1);
  SetSampleDatabaseHasNoPresetsFlagsOnly(test_address);

  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _)).Times(1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address))
      .Times(1);
  TestConnect(test_address);

  /* Test this not so useful service */
  EXPECT_CALL(*callbacks,
              OnActivePresetSelectError(_, ErrorCode::OPERATION_NOT_SUPPORTED))
      .Times(3);

  HasClient::Get()->SelectActivePreset(test_address, 0x01);
  HasClient::Get()->NextActivePreset(test_address);
  HasClient::Get()->PreviousActivePreset(test_address);
}

static int GetSocketBufferSize(int sockfd) {
  int socket_buffer_size;
  socklen_t optlen = sizeof(socket_buffer_size);
  getsockopt(sockfd, SOL_SOCKET, SO_RCVBUF, (void*)&socket_buffer_size,
             &optlen);
  return socket_buffer_size;
}

bool SimpleJsonValidator(int fd, int* dumpsys_byte_cnt) {
  std::ostringstream ss;

  char buf{0};
  bool within_double_quotes{false};
  int left_bracket{0}, right_bracket{0};
  int left_sq_bracket{0}, right_sq_bracket{0};
  while (read(fd, &buf, 1) != -1) {
    switch (buf) {
      (*dumpsys_byte_cnt)++;
      case '"':
        within_double_quotes = !within_double_quotes;
        break;
      case '{':
        if (!within_double_quotes) {
          left_bracket++;
        }
        break;
      case '}':
        if (!within_double_quotes) {
          right_bracket++;
        }
        break;
      case '[':
        if (!within_double_quotes) {
          left_sq_bracket++;
        }
        break;
      case ']':
        if (!within_double_quotes) {
          right_sq_bracket++;
        }
        break;
      default:
        break;
    }
    ss << buf;
  }
  log::error("{}", ss.str());
  return (left_bracket == right_bracket) &&
         (left_sq_bracket == right_sq_bracket);
}

TEST_F(HasClientTest, test_dumpsys) {
  const RawAddress test_address = GetTestAddress(1);

  std::set<HasPreset, HasPreset::ComparatorDesc> presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      presets);

  uint8_t active_preset_index = 0;
  std::vector<PresetInfo> preset_details;

  EXPECT_CALL(*callbacks,
              OnPresetInfo(std::variant<RawAddress, int>(test_address),
                           PresetInfoReason::ALL_PRESET_INFO, _))
      .Times(1)
      .WillOnce(SaveArg<2>(&preset_details));
  ON_CALL(*callbacks, OnActivePresetSelected(_, _))
      .WillByDefault(SaveArg<1>(&active_preset_index));
  TestConnect(test_address);

  int sv[2];
  ASSERT_EQ(0, socketpair(AF_LOCAL, SOCK_STREAM | SOCK_NONBLOCK, 0, sv));
  int socket_buffer_size = GetSocketBufferSize(sv[0]);

  HasClient::Get()->DebugDump(sv[0]);
  int dumpsys_byte_cnt = 0;
  ASSERT_TRUE(dumpsys_byte_cnt < socket_buffer_size);
  ASSERT_TRUE(SimpleJsonValidator(sv[1], &dumpsys_byte_cnt));
}

TEST_F(HasClientTest, test_connect_database_out_of_sync) {
  osi_property_set_bool("persist.bluetooth.has.always_use_preset_cache", false);

  const RawAddress test_address = GetTestAddress(1);
  std::set<HasPreset, HasPreset::ComparatorDesc> has_presets = {{
      HasPreset(1, HasPreset::kPropertyAvailable, "Universal"),
      HasPreset(2, HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                "Preset2"),
  }};
  SetSampleDatabaseHasPresetsNtf(
      test_address,
      bluetooth::has::kFeatureBitHearingAidTypeBanded |
          bluetooth::has::kFeatureBitWritablePresets |
          bluetooth::has::kFeatureBitDynamicPresets,
      has_presets);

  EXPECT_CALL(*callbacks, OnDeviceAvailable(
                              test_address,
                              bluetooth::has::kFeatureBitHearingAidTypeBanded |
                                  bluetooth::has::kFeatureBitWritablePresets |
                                  bluetooth::has::kFeatureBitDynamicPresets));
  EXPECT_CALL(*callbacks,
              OnConnectionState(ConnectionState::CONNECTED, test_address));
  TestConnect(test_address);

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
  HasClient::Get()->GetPresetInfo(test_address, 1);
}

class HasTypesTest : public ::testing::Test {
 protected:
  void SetUp(void) override { reset_mock_function_count_map(); }

  void TearDown(void) override {}
};  // namespace

TEST_F(HasTypesTest, test_has_preset_serialize) {
  HasPreset preset(0x01,
                   HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                   "My Writable Preset01");

  auto sp_sz = preset.SerializedSize();
  std::vector<uint8_t> serialized(sp_sz);

  ASSERT_EQ(1 +      // preset index
                1 +  // properties
                1 +  // name length
                preset.GetName().length(),
            sp_sz);

  /* Serialize should move the received buffer pointer by the size of data
   */
  ASSERT_EQ(preset.Serialize(serialized.data(), serialized.size()),
            serialized.data() + serialized.size());

  /* Deserialize */
  HasPreset clone;
  ASSERT_EQ(HasPreset::Deserialize(serialized.data(), serialized.size(), clone),
            serialized.data() + serialized.size());

  /* Verify */
  ASSERT_EQ(preset.GetIndex(), clone.GetIndex());
  ASSERT_EQ(preset.GetProperties(), clone.GetProperties());
  ASSERT_EQ(preset.GetName(), clone.GetName());
}

TEST_F(HasTypesTest, test_has_preset_serialize_output_buffer_to_small) {
  HasPreset preset(0x01,
                   HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                   "My Writable Preset01");

  /* On failure, the offset should still point on .data() */
  std::vector<uint8_t> serialized(preset.SerializedSize() - 1);
  ASSERT_EQ(preset.Serialize(serialized.data(), serialized.size()),
            serialized.data());
  ASSERT_EQ(preset.Serialize(serialized.data(), 0), serialized.data());
  ASSERT_EQ(preset.Serialize(serialized.data(), 1), serialized.data());
  ASSERT_EQ(preset.Serialize(serialized.data(), 10), serialized.data());
}

TEST_F(HasTypesTest, test_has_preset_serialize_name_to_long) {
  HasPreset preset(0x01,
                   HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                   "This name is more than 40 characters long");

  /* On failure, the offset should still point on .data() */
  std::vector<uint8_t> serialized(preset.SerializedSize());
  EXPECT_EQ(preset.Serialize(serialized.data(), serialized.size()),
            serialized.data());
}

TEST_F(HasTypesTest, test_has_preset_deserialize_input_buffer_to_small) {
  HasPreset preset(0x01,
                   HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                   "My Writable Preset01");

  std::vector<uint8_t> serialized(preset.SerializedSize());

  /* Serialize should move the received buffer pointer by the size of data
   */
  ASSERT_EQ(preset.Serialize(serialized.data(), serialized.size()),
            serialized.data() + serialized.size());

  /* Deserialize */
  HasPreset clone;
  ASSERT_EQ(HasPreset::Deserialize(serialized.data(), 0, clone),
            serialized.data());
  ASSERT_EQ(HasPreset::Deserialize(serialized.data(), 1, clone),
            serialized.data());
  ASSERT_EQ(HasPreset::Deserialize(serialized.data(), 11, clone),
            serialized.data());
  ASSERT_EQ(
      HasPreset::Deserialize(serialized.data(), serialized.size() - 1, clone),
      serialized.data());
}

TEST_F(HasTypesTest, test_has_presets_serialize) {
  HasPreset preset(0x01,
                   HasPreset::kPropertyAvailable | HasPreset::kPropertyWritable,
                   "My Writable Preset01");

  HasPreset preset2(0x02, 0, "Nonwritable Unavailable Preset");

  HasDevice has_device(GetTestAddress(1));
  has_device.has_presets.insert(preset);
  has_device.has_presets.insert(preset2);

  auto out_buf_sz = has_device.SerializedPresetsSize();
  ASSERT_EQ(out_buf_sz, preset.SerializedSize() + preset2.SerializedSize() + 2);

  /* Serialize should append to the vector */
  std::vector<uint8_t> serialized;
  ASSERT_TRUE(has_device.SerializePresets(serialized));
  ASSERT_EQ(out_buf_sz, serialized.size());

  /* Deserialize */
  HasDevice clone(GetTestAddress(1));
  ASSERT_TRUE(HasDevice::DeserializePresets(serialized.data(),
                                            serialized.size(), clone));

  /* Verify */
  ASSERT_EQ(clone.has_presets.size(), has_device.has_presets.size());
  ASSERT_NE(0u, clone.has_presets.count(0x01));
  ASSERT_NE(0u, clone.has_presets.count(0x02));

  ASSERT_EQ(clone.has_presets.find(0x01)->GetIndex(),
            has_device.has_presets.find(0x01)->GetIndex());
  ASSERT_EQ(clone.has_presets.find(0x01)->GetProperties(),
            has_device.has_presets.find(0x01)->GetProperties());
  ASSERT_EQ(clone.has_presets.find(0x01)->GetName(),
            has_device.has_presets.find(0x01)->GetName());

  ASSERT_EQ(clone.has_presets.find(0x02)->GetIndex(),
            has_device.has_presets.find(0x02)->GetIndex());
  ASSERT_EQ(clone.has_presets.find(0x02)->GetProperties(),
            has_device.has_presets.find(0x02)->GetProperties());
  ASSERT_EQ(clone.has_presets.find(0x02)->GetName(),
            has_device.has_presets.find(0x02)->GetName());
}

TEST_F(HasTypesTest, test_group_op_coordinator_init) {
  HasCtpGroupOpCoordinator::Initialize([](void*) {
    /* Do nothing */
  });
  ASSERT_EQ(0u, HasCtpGroupOpCoordinator::ref_cnt);
  auto address1 = GetTestAddress(1);
  auto address2 = GetTestAddress(2);

  HasCtpGroupOpCoordinator wrapper(
      {address1, address2},
      HasCtpOp(0x01, ::le_audio::has::PresetCtpOpcode::READ_PRESETS, 6));
  ASSERT_EQ(2u, wrapper.ref_cnt);

  HasCtpGroupOpCoordinator::Cleanup();
  ASSERT_EQ(0u, wrapper.ref_cnt);

  ASSERT_EQ(1, get_func_call_count("alarm_free"));
  ASSERT_EQ(1, get_func_call_count("alarm_new"));
}

TEST_F(HasTypesTest, test_group_op_coordinator_copy) {
  HasCtpGroupOpCoordinator::Initialize([](void*) {
    /* Do nothing */
  });
  ASSERT_EQ(0u, HasCtpGroupOpCoordinator::ref_cnt);
  auto address1 = GetTestAddress(1);
  auto address2 = GetTestAddress(2);

  HasCtpGroupOpCoordinator wrapper(
      {address1, address2},
      HasCtpOp(0x01, ::le_audio::has::PresetCtpOpcode::READ_PRESETS, 6));
  HasCtpGroupOpCoordinator wrapper2(
      {address1},
      HasCtpOp(0x01, ::le_audio::has::PresetCtpOpcode::READ_PRESETS, 6));
  ASSERT_EQ(3u, wrapper.ref_cnt);
  HasCtpGroupOpCoordinator wrapper3 = wrapper2;
  auto* wrapper4 =
      new HasCtpGroupOpCoordinator(HasCtpGroupOpCoordinator(wrapper2));
  ASSERT_EQ(5u, wrapper.ref_cnt);

  delete wrapper4;
  ASSERT_EQ(4u, wrapper.ref_cnt);

  HasCtpGroupOpCoordinator::Cleanup();
  ASSERT_EQ(0u, wrapper.ref_cnt);

  ASSERT_EQ(1, get_func_call_count("alarm_free"));
  ASSERT_EQ(1, get_func_call_count("alarm_new"));
}

TEST_F(HasTypesTest, test_group_op_coordinator_completion) {
  HasCtpGroupOpCoordinator::Initialize([](void*) {
    /* Do nothing */
    log::info("callback call");
  });
  ASSERT_EQ(0u, HasCtpGroupOpCoordinator::ref_cnt);
  auto address1 = GetTestAddress(1);
  auto address2 = GetTestAddress(2);
  auto address3 = GetTestAddress(3);

  HasCtpGroupOpCoordinator wrapper(
      {address1, address3},
      HasCtpOp(0x01, ::le_audio::has::PresetCtpOpcode::READ_PRESETS, 6));
  HasCtpGroupOpCoordinator wrapper2(
      {address2},
      HasCtpOp(0x01, ::le_audio::has::PresetCtpOpcode::READ_PRESETS, 6));
  ASSERT_EQ(3u, wrapper.ref_cnt);

  ASSERT_FALSE(wrapper.IsFullyCompleted());

  wrapper.SetCompleted(address1);
  ASSERT_EQ(2u, wrapper.ref_cnt);

  wrapper.SetCompleted(address3);
  ASSERT_EQ(1u, wrapper.ref_cnt);
  ASSERT_FALSE(wrapper.IsFullyCompleted());
  ASSERT_EQ(0, get_func_call_count("alarm_free"));

  /* Non existing address completion */
  wrapper.SetCompleted(address2);
  ASSERT_EQ(0, get_func_call_count("alarm_free"));
  ASSERT_EQ(1u, wrapper.ref_cnt);

  /* Last device address completion */
  wrapper2.SetCompleted(address2);
  ASSERT_TRUE(wrapper.IsFullyCompleted());
  ASSERT_EQ(0u, wrapper.ref_cnt);
  const int alarm_free_count = get_func_call_count("alarm_free");
  ASSERT_EQ(1, alarm_free_count);

  HasCtpGroupOpCoordinator::Cleanup();

  ASSERT_EQ(alarm_free_count, get_func_call_count("alarm_free"));
  ASSERT_EQ(1, get_func_call_count("alarm_new"));
}

}  // namespace
}  // namespace internal
}  // namespace has
}  // namespace bluetooth
