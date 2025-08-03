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

#include "state_machine.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "../le_audio_types.h"
#include "btm_iso_api.h"
#include "mock_iso_manager.h"
#include "stack/include/btm_ble_api_types.h"
#include "state_machine.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_main_shim_le_advertising_manager.h"

using namespace bluetooth::hci::iso_manager;

using bluetooth::hci::IsoManager;
using bluetooth::le_audio::BasicAudioAnnouncementData;
using testing::_;
using testing::Mock;
using testing::SaveArg;
using testing::Test;

// Disables most likely false-positives from base::SplitString()
extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

void btsnd_hcic_ble_rand(base::Callback<void(BT_OCTET8)> cb) {}

namespace le_audio {
namespace broadcaster {
namespace {
// bit 0: encrypted, bit 1: standard quality present
static const uint8_t test_public_broadcast_features = 0x3;
static const std::string test_broadcast_name = "Test";
static const std::vector<uint8_t> default_public_metadata = {
    5, le_audio::types::kLeAudioMetadataTypeProgramInfo, 0x1, 0x2, 0x3, 0x4};

class MockBroadcastStatMachineCallbacks
    : public IBroadcastStateMachineCallbacks {
 public:
  MockBroadcastStatMachineCallbacks() = default;
  MockBroadcastStatMachineCallbacks(const MockBroadcastStatMachineCallbacks&) =
      delete;
  MockBroadcastStatMachineCallbacks& operator=(
      const MockBroadcastStatMachineCallbacks&) = delete;

  ~MockBroadcastStatMachineCallbacks() override = default;

  MOCK_METHOD((void), OnStateMachineCreateStatus,
              (uint32_t broadcast_id, bool initialized), (override));
  MOCK_METHOD((void), OnStateMachineDestroyed, (uint32_t broadcast_id),
              (override));
  MOCK_METHOD((void), OnStateMachineEvent,
              (uint32_t broadcast_id, BroadcastStateMachine::State state,
               const void* data),
              (override));
  MOCK_METHOD((void), OnOwnAddressResponse,
              (uint32_t broadcast_id, uint8_t addr_type, RawAddress addr),
              (override));
  MOCK_METHOD((void), OnBigCreated, (const std::vector<uint16_t>& conn_handle),
              (override));
};

class MockBroadcastAdvertisingCallbacks : public AdvertisingCallbacks {
 public:
  MockBroadcastAdvertisingCallbacks() = default;
  MockBroadcastAdvertisingCallbacks(const MockBroadcastAdvertisingCallbacks&) =
      delete;
  MockBroadcastAdvertisingCallbacks& operator=(
      const MockBroadcastAdvertisingCallbacks&) = delete;

  ~MockBroadcastAdvertisingCallbacks() override = default;

  MOCK_METHOD((void), OnAdvertisingSetStarted,
              (int reg_id, uint8_t advertiser_id, int8_t tx_power,
               uint8_t status),
              (override));
  MOCK_METHOD((void), OnAdvertisingEnabled,
              (uint8_t advertiser_id, bool enable, uint8_t status), (override));
  MOCK_METHOD((void), OnAdvertisingDataSet,
              (uint8_t advertiser_id, uint8_t status), (override));
  MOCK_METHOD((void), OnScanResponseDataSet,
              (uint8_t advertiser_id, uint8_t status), (override));
  MOCK_METHOD((void), OnAdvertisingParametersUpdated,
              (uint8_t advertiser_id, int8_t tx_power, uint8_t status),
              (override));
  MOCK_METHOD((void), OnPeriodicAdvertisingParametersUpdated,
              (uint8_t advertiser_id, uint8_t status), (override));
  MOCK_METHOD((void), OnPeriodicAdvertisingDataSet,
              (uint8_t advertiser_id, uint8_t status), (override));
  MOCK_METHOD((void), OnPeriodicAdvertisingEnabled,
              (uint8_t advertiser_id, bool enable, uint8_t status), (override));
  MOCK_METHOD((void), OnOwnAddressRead,
              (uint8_t advertiser_id, uint8_t address_type, RawAddress address),
              (override));
};

class StateMachineTest : public Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    MockBleAdvertisingManager::Initialize();

    mock_ble_advertising_manager_ = MockBleAdvertisingManager::Get();

    sm_callbacks_.reset(new MockBroadcastStatMachineCallbacks());
    adv_callbacks_.reset(new MockBroadcastAdvertisingCallbacks());
    BroadcastStateMachine::Initialize(sm_callbacks_.get(),
                                      adv_callbacks_.get());

    ON_CALL(*mock_ble_advertising_manager_, StartAdvertisingSet)
        .WillByDefault(
            [this](uint8_t client_id, int reg_id,
                   BleAdvertiserInterface::IdTxPowerStatusCallback register_cb,
                   AdvertiseParameters params,
                   std::vector<uint8_t> advertise_data,
                   std::vector<uint8_t> scan_response_data,
                   PeriodicAdvertisingParameters periodic_params,
                   std::vector<uint8_t> periodic_data, uint16_t duration,
                   uint8_t maxExtAdvEvents,
                   BleAdvertiserInterface::IdStatusCallback timeout_cb) {
              static uint8_t advertiser_id = 1;
              uint8_t tx_power = 32;
              uint8_t status = 0;
              this->adv_callbacks_->OnAdvertisingSetStarted(
                  BroadcastStateMachine::kLeAudioBroadcastRegId,
                  advertiser_id++, tx_power, status);
            });

    ON_CALL(*mock_ble_advertising_manager_, Enable)
        .WillByDefault(
            [this](uint8_t advertiser_id, bool enable,
                   BleAdvertiserInterface::StatusCallback cb, uint16_t duration,
                   uint8_t maxExtAdvEvents,
                   BleAdvertiserInterface::StatusCallback timeout_cb) {
              uint8_t status = 0;
              this->adv_callbacks_->OnAdvertisingEnabled(advertiser_id, enable,
                                                         status);
            });

    ON_CALL(*mock_ble_advertising_manager_, GetOwnAddress)
        .WillByDefault(
            [](uint8_t inst_id, BleAdvertiserInterface::GetAddressCallback cb) {
              uint8_t address_type = 0x02;
              RawAddress address;
              const uint8_t addr[] = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
              address.FromOctets(addr);
              cb.Run(address_type, address);
            });

    ON_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus)
        .WillByDefault([this](uint32_t broadcast_id, bool initialized) {
          auto instance_it =
              std::find_if(pending_broadcasts_.begin(),
                           pending_broadcasts_.end(), [broadcast_id](auto& up) {
                             return (up->GetBroadcastId() == broadcast_id);
                           });
          if (instance_it != pending_broadcasts_.end()) {
            if (initialized) {
              broadcasts_[broadcast_id] = std::move(*instance_it);
            }
            pending_broadcasts_.erase(instance_it);
          }
          instance_creation_promise_.set_value(broadcast_id);
        });

    ON_CALL(*(sm_callbacks_.get()), OnStateMachineDestroyed)
        .WillByDefault([this](uint32_t broadcast_id) {
          if (broadcasts_.count(broadcast_id)) {
            instance_destruction_promise_.set_value(broadcast_id);
          }
        });

    ON_CALL(*(adv_callbacks_.get()), OnAdvertisingSetStarted)
        .WillByDefault([this](int reg_id, uint8_t advertiser_id,
                              int8_t tx_power, uint8_t status) {
          pending_broadcasts_.back()->OnCreateAnnouncement(advertiser_id,
                                                           tx_power, status);
        });

    ON_CALL(*(adv_callbacks_.get()), OnAdvertisingEnabled)
        .WillByDefault(
            [this](uint8_t advertiser_id, bool enable, uint8_t status) {
              auto const& iter = std::find_if(
                  broadcasts_.cbegin(), broadcasts_.cend(),
                  [advertiser_id](auto const& sm) {
                    return sm.second->GetAdvertisingSid() == advertiser_id;
                  });
              if (iter != broadcasts_.cend()) {
                iter->second->OnEnableAnnouncement(enable, status);
              }
            });
    ConfigureIsoManagerMock();
  }

  void ConfigureIsoManagerMock() {
    iso_manager_ = IsoManager::GetInstance();
    ASSERT_NE(iso_manager_, nullptr);
    iso_manager_->Start();

    mock_iso_manager_ = MockIsoManager::GetInstance();
    ASSERT_NE(mock_iso_manager_, nullptr);

    ON_CALL(*mock_iso_manager_, CreateBig)
        .WillByDefault([this](uint8_t big_id, big_create_params p) {
          auto bit =
              std::find_if(broadcasts_.begin(), broadcasts_.end(),
                           [big_id](auto const& entry) {
                             return entry.second->GetAdvertisingSid() == big_id;
                           });
          if (bit == broadcasts_.end()) return;

          big_create_cmpl_evt evt;
          evt.big_id = big_id;

          // For test convenience lets encode big_id into conn_hdl MSB.
          // NOTE: In current implementation big_id is equal to advertising SID.
          //       This is an important detail exploited by the IsoManager mock
          static uint8_t conn_lsb = 1;
          uint16_t conn_msb = ((uint16_t)big_id) << 8;
          for (auto i = 0; i < p.num_bis; ++i) {
            evt.conn_handles.push_back(conn_msb | conn_lsb++);
          }

          bit->second->HandleHciEvent(HCI_BLE_CREATE_BIG_CPL_EVT, &evt);
        });

    ON_CALL(*mock_iso_manager_, SetupIsoDataPath)
        .WillByDefault([this](uint16_t conn_handle, iso_data_path_params p) {
          // Get the big_id encoded in conn_handle's MSB
          uint8_t big_id = conn_handle >> 8;
          auto bit =
              std::find_if(broadcasts_.begin(), broadcasts_.end(),
                           [big_id](auto const& entry) {
                             return entry.second->GetAdvertisingSid() == big_id;
                           });
          if (bit == broadcasts_.end()) return;
          bit->second->OnSetupIsoDataPath(0, conn_handle);
        });

    ON_CALL(*mock_iso_manager_, RemoveIsoDataPath)
        .WillByDefault([this](uint16_t conn_handle, uint8_t iso_direction) {
          // Get the big_id encoded in conn_handle's MSB
          uint8_t big_id = conn_handle >> 8;
          auto bit =
              std::find_if(broadcasts_.begin(), broadcasts_.end(),
                           [big_id](auto const& entry) {
                             return entry.second->GetAdvertisingSid() == big_id;
                           });
          if (bit == broadcasts_.end()) return;
          bit->second->OnRemoveIsoDataPath(0, conn_handle);
        });

    ON_CALL(*mock_iso_manager_, TerminateBig)
        .WillByDefault([this](uint8_t big_id, uint8_t reason) {
          // Get the big_id encoded in conn_handle's MSB
          auto bit =
              std::find_if(broadcasts_.begin(), broadcasts_.end(),
                           [big_id](auto const& entry) {
                             return entry.second->GetAdvertisingSid() == big_id;
                           });
          if (bit == broadcasts_.end()) return;

          big_terminate_cmpl_evt evt;
          evt.big_id = big_id;
          evt.reason = reason;

          bit->second->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT, &evt);
        });
  }

  void TearDown() override {
    iso_manager_->Stop();
    mock_iso_manager_ = nullptr;
    Mock::VerifyAndClearExpectations(sm_callbacks_.get());
    Mock::VerifyAndClearExpectations(adv_callbacks_.get());

    pending_broadcasts_.clear();
    broadcasts_.clear();
    sm_callbacks_.reset();
    adv_callbacks_.reset();

    MockBleAdvertisingManager::CleanUp();
    mock_ble_advertising_manager_ = nullptr;
  }

  uint32_t InstantiateStateMachine(
      le_audio::types::LeAudioContextType context =
          le_audio::types::LeAudioContextType::UNSPECIFIED) {
    // We will get the state machine create status update in an async callback
    // so let's wait for it here.
    instance_creation_promise_ = std::promise<uint32_t>();
    std::future<uint32_t> instance_future =
        instance_creation_promise_.get_future();

    static uint8_t broadcast_id_lsb = 1;

    auto codec_qos_pair =
        getStreamConfigForContext(types::AudioContexts(context));
    auto broadcast_id = broadcast_id_lsb++;
    pending_broadcasts_.push_back(BroadcastStateMachine::CreateInstance({
        .is_public = true,
        .broadcast_id = broadcast_id,
        .broadcast_name = test_broadcast_name,
        // .streaming_phy = ,
        .codec_wrapper = codec_qos_pair.first,
        .qos_config = codec_qos_pair.second,
        // .announcement = ,
        // .broadcast_code = ,
    }));
    pending_broadcasts_.back()->Initialize();
    return instance_future.get();
  }

  MockBleAdvertisingManager* mock_ble_advertising_manager_;
  IsoManager* iso_manager_;
  MockIsoManager* mock_iso_manager_;

  std::map<uint32_t, std::unique_ptr<BroadcastStateMachine>> broadcasts_;
  std::vector<std::unique_ptr<BroadcastStateMachine>> pending_broadcasts_;
  std::unique_ptr<MockBroadcastStatMachineCallbacks> sm_callbacks_;
  std::unique_ptr<MockBroadcastAdvertisingCallbacks> adv_callbacks_;
  std::promise<uint32_t> instance_creation_promise_;
  std::promise<uint8_t> instance_destruction_promise_;
};

TEST_F(StateMachineTest, CreateInstanceFailed) {
  EXPECT_CALL(*mock_ble_advertising_manager_, StartAdvertisingSet)
      .WillOnce(
          [this](uint8_t client_id, int reg_id,
                 BleAdvertiserInterface::IdTxPowerStatusCallback register_cb,
                 AdvertiseParameters params,
                 std::vector<uint8_t> advertise_data,
                 std::vector<uint8_t> scan_response_data,
                 PeriodicAdvertisingParameters periodic_params,
                 std::vector<uint8_t> periodic_data, uint16_t duration,
                 uint8_t maxExtAdvEvents,
                 BleAdvertiserInterface::IdStatusCallback timeout_cb) {
            uint8_t advertiser_id = 1;
            uint8_t tx_power = 0;
            uint8_t status = 1;
            this->adv_callbacks_->OnAdvertisingSetStarted(
                BroadcastStateMachine::kLeAudioBroadcastRegId, advertiser_id,
                tx_power, status);
          });

  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, false))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_NE(broadcast_id, BroadcastStateMachine::kAdvSidUndefined);
  ASSERT_TRUE(pending_broadcasts_.empty());
  ASSERT_TRUE(broadcasts_.empty());
}

TEST_F(StateMachineTest, CreateInstanceSuccess) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_NE(broadcast_id, BroadcastStateMachine::kAdvSidUndefined);
  ASSERT_TRUE(pending_broadcasts_.empty());
  ASSERT_FALSE(broadcasts_.empty());
  ASSERT_EQ(broadcasts_[broadcast_id]->GetBroadcastId(), broadcast_id);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);
}

TEST_F(StateMachineTest, DestroyInstanceSuccess) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_NE(broadcast_id, BroadcastStateMachine::kAdvSidUndefined);
  ASSERT_FALSE(broadcasts_.empty());

  instance_destruction_promise_ = std::promise<uint8_t>();
  std::future<uint8_t> instance_future =
      instance_destruction_promise_.get_future();

  broadcasts_.clear();
  EXPECT_EQ(instance_future.get(), broadcast_id);
}

TEST_F(StateMachineTest, GetAdvertisingAddress) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  EXPECT_CALL(*(sm_callbacks_.get()), OnOwnAddressResponse(broadcast_id, _, _))
      .Times(1);
  broadcasts_[broadcast_id]->RequestOwnAddress();
}

TEST_F(StateMachineTest, Mute) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_TRUE(pending_broadcasts_.empty());
  ASSERT_FALSE(broadcasts_.empty());

  ASSERT_FALSE(broadcasts_[broadcast_id]->IsMuted());
  broadcasts_[broadcast_id]->SetMuted(true);
  ASSERT_TRUE(broadcasts_[broadcast_id]->IsMuted());
  broadcasts_[broadcast_id]->SetMuted(false);
  ASSERT_FALSE(broadcasts_[broadcast_id]->IsMuted());
}

static BasicAudioAnnouncementData prepareAnnouncement(
    const BroadcastCodecWrapper& codec_config,
    std::map<uint8_t, std::vector<uint8_t>> metadata) {
  BasicAudioAnnouncementData announcement;

  announcement.presentation_delay_us = 40000;
  auto const& codec_id = codec_config.GetLeAudioCodecId();

  announcement.subgroup_configs = {{
      .codec_config =
          {
              .codec_id = codec_id.coding_format,
              .vendor_company_id = codec_id.vendor_company_id,
              .vendor_codec_id = codec_id.vendor_codec_id,
              .codec_specific_params =
                  codec_config.GetSubgroupCodecSpecData().Values(),
          },
      .metadata = std::move(metadata),
      .bis_configs = {},
  }};

  for (uint8_t i = 0; i < codec_config.GetNumChannels(); ++i) {
    announcement.subgroup_configs[0].bis_configs.push_back(
        {.codec_specific_params =
             codec_config.GetBisCodecSpecData(i + 1).Values(),
         .bis_index = static_cast<uint8_t>(i + 1)});
  }

  return announcement;
}

TEST_F(StateMachineTest, UpdateAnnouncement) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  std::map<uint8_t, std::vector<uint8_t>> metadata = {};
  BroadcastCodecWrapper codec_config(
      {.coding_format = le_audio::types::kLeAudioCodingFormatLC3,
       .vendor_company_id = le_audio::types::kLeAudioVendorCompanyIdUndefined,
       .vendor_codec_id = le_audio::types::kLeAudioVendorCodecIdUndefined},
      {.num_channels = LeAudioCodecConfiguration::kChannelNumberMono,
       .sample_rate = LeAudioCodecConfiguration::kSampleRate16000,
       .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
       .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
      32000, 40);
  auto announcement = prepareAnnouncement(codec_config, metadata);

  auto adv_sid = broadcasts_[broadcast_id]->GetAdvertisingSid();
  std::vector<uint8_t> data;
  EXPECT_CALL(*mock_ble_advertising_manager_,
              SetPeriodicAdvertisingData(adv_sid, _, _))
      .Times(2)
      .WillRepeatedly(SaveArg<1>(&data));
  broadcasts_[broadcast_id]->UpdateBroadcastAnnouncement(
      std::move(announcement));

  uint8_t first_len = data.size();
  ASSERT_NE(first_len, 0);  // Non-zero length
  ASSERT_EQ(data[1], BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE);
  ASSERT_EQ(data[2], (kBasicAudioAnnouncementServiceUuid & 0x00FF));
  ASSERT_EQ(data[3], ((kBasicAudioAnnouncementServiceUuid >> 8) & 0x00FF));
  // The rest of the packet data is already covered by the announcement tests

  // Verify that changes in the announcement makes a difference
  metadata = {{0x01, {0x03}}};
  announcement = prepareAnnouncement(codec_config, metadata);
  broadcasts_[broadcast_id]->UpdateBroadcastAnnouncement(
      std::move(announcement));
  uint8_t second_len = data.size();

  // These should differ by the difference in metadata
  ASSERT_EQ(first_len + types::LeAudioLtvMap(metadata).RawPacketSize(),
            second_len);
}

TEST_F(StateMachineTest, ProcessMessageStartWhenConfigured) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto sound_context = le_audio::types::LeAudioContextType::MEDIA;
  uint8_t num_channels = 2;

  auto broadcast_id = InstantiateStateMachine(sound_context);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  uint8_t num_bises = 0;
  EXPECT_CALL(*mock_iso_manager_, CreateBig)
      .WillOnce([this, &num_bises](uint8_t big_id, big_create_params p) {
        auto bit =
            std::find_if(broadcasts_.begin(), broadcasts_.end(),
                         [big_id](auto const& entry) {
                           return entry.second->GetAdvertisingSid() == big_id;
                         });
        if (bit == broadcasts_.end()) return;

        num_bises = p.num_bis;

        big_create_cmpl_evt evt;
        evt.big_id = big_id;

        // For test convenience lets encode big_id into conn_hdl's
        // MSB
        static uint8_t conn_lsb = 1;
        uint16_t conn_msb = ((uint16_t)big_id) << 8;
        for (auto i = 0; i < p.num_bis; ++i) {
          evt.conn_handles.push_back(conn_msb | conn_lsb++);
        }

        bit->second->HandleHciEvent(HCI_BLE_CREATE_BIG_CPL_EVT, &evt);
      });

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(num_channels);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::STREAMING, _))
      .Times(1);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);

  // Verify the right number of BISes in the BIG being created
  ASSERT_EQ(num_bises, num_channels);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);
}

TEST_F(StateMachineTest, ProcessMessageStopWhenConfigured) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::STOPPING, _))
      .Times(1);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::STOPPED, _))
      .Times(1);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::STOP);

  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);
}

TEST_F(StateMachineTest, ProcessMessageSuspendWhenConfigured) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineEvent(broadcast_id, _, _))
      .Times(0);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::SUSPEND);
  // There shall be no change in state
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);
}

TEST_F(StateMachineTest, ProcessMessageStartWhenStreaming) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineEvent(broadcast_id, _, _))
      .Times(0);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);

  // There shall be no change in state
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);
}

TEST_F(StateMachineTest, ProcessMessageStopWhenStreaming) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(2);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::STOPPING, _))
      .Times(1);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::STOPPED, _))
      .Times(1);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::STOP);

  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);
}

TEST_F(StateMachineTest, ProcessMessageSuspendWhenStreaming) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(2);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::CONFIGURED, _))
      .Times(1);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::SUSPEND);

  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);
}

TEST_F(StateMachineTest, ProcessMessageStartWhenStopped) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::STOP);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::CONFIGURING, _))
      .Times(1);
  EXPECT_CALL(*(sm_callbacks_.get()),
              OnStateMachineEvent(broadcast_id,
                                  BroadcastStateMachine::State::STREAMING, _))
      .Times(1);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);

  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);
}

TEST_F(StateMachineTest, ProcessMessageStopWhenStopped) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::STOP);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineEvent(broadcast_id, _, _))
      .Times(0);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::STOP);

  // There shall be no change in state
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);
}

TEST_F(StateMachineTest, ProcessMessageSuspendWhenStopped) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::STOP);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath).Times(0);
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineEvent(broadcast_id, _, _))
      .Times(0);
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::SUSPEND);

  // There shall be no change in state
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STOPPED);
}

TEST_F(StateMachineTest, OnSetupIsoDataPathError) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath)
      .WillOnce([this](uint16_t conn_handle, iso_data_path_params p) {
        // Get the big_id encoded in conn_handle's MSB
        uint8_t big_id = conn_handle >> 8;
        auto bit =
            std::find_if(broadcasts_.begin(), broadcasts_.end(),
                         [big_id](auto const& entry) {
                           return entry.second->GetAdvertisingSid() == big_id;
                         });
        if (bit == broadcasts_.end()) return;
        bit->second->OnSetupIsoDataPath(0, conn_handle);
      })
      .WillOnce([this](uint16_t conn_handle, iso_data_path_params p) {
        // Get the big_id encoded in conn_handle's MSB
        uint8_t big_id = conn_handle >> 8;
        auto bit =
            std::find_if(broadcasts_.begin(), broadcasts_.end(),
                         [big_id](auto const& entry) {
                           return entry.second->GetAdvertisingSid() == big_id;
                         });
        if (bit == broadcasts_.end()) return;
        bit->second->OnSetupIsoDataPath(1, conn_handle);
      })
      .RetiresOnSaturation();
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);

  // On datapath setup failure we should go back to configured with BIG being
  // destroyed. Maybe it will work out next time for the new BIG.
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  // And still be able to start again
  ON_CALL(*mock_iso_manager_, SetupIsoDataPath)
      .WillByDefault([this](uint16_t conn_handle, iso_data_path_params p) {
        // Get the big_id encoded in conn_handle's MSB
        uint8_t big_id = conn_handle >> 8;
        auto bit =
            std::find_if(broadcasts_.begin(), broadcasts_.end(),
                         [big_id](auto const& entry) {
                           return entry.second->GetAdvertisingSid() == big_id;
                         });
        if (bit == broadcasts_.end()) return;
        bit->second->OnSetupIsoDataPath(0, conn_handle);
      });
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath).Times(2);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);
}

TEST_F(StateMachineTest, OnRemoveIsoDataPathError) {
  auto broadcast_id =
      InstantiateStateMachine(le_audio::types::LeAudioContextType::MEDIA);

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);

  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath)
      .WillOnce([this](uint16_t conn_handle, uint8_t iso_direction) {
        // Get the big_id encoded in conn_handle's MSB
        uint8_t big_id = conn_handle >> 8;
        auto bit =
            std::find_if(broadcasts_.begin(), broadcasts_.end(),
                         [big_id](auto const& entry) {
                           return entry.second->GetAdvertisingSid() == big_id;
                         });
        if (bit == broadcasts_.end()) return;
        bit->second->OnRemoveIsoDataPath(0, conn_handle);
      })
      .WillOnce([this](uint16_t conn_handle, uint8_t iso_direction) {
        // Get the big_id encoded in conn_handle's MSB
        uint8_t big_id = conn_handle >> 8;
        auto bit =
            std::find_if(broadcasts_.begin(), broadcasts_.end(),
                         [big_id](auto const& entry) {
                           return entry.second->GetAdvertisingSid() == big_id;
                         });
        if (bit == broadcasts_.end()) return;
        bit->second->OnRemoveIsoDataPath(1, conn_handle);
      })
      .RetiresOnSaturation();
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::SUSPEND);

  // On datapath teardown failure we should stay in CONFIGURED with BIG being
  // destroyed.
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  // And still be able to start again
  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);
}

TEST_F(StateMachineTest, GetConfig) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto sound_context = le_audio::types::LeAudioContextType::MEDIA;
  uint8_t num_channels = 2;

  auto broadcast_id = InstantiateStateMachine(sound_context);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);

  std::optional<BigConfig> const& big_cfg =
      broadcasts_[broadcast_id]->GetBigConfig();
  ASSERT_FALSE(big_cfg.has_value());

  broadcasts_[broadcast_id]->ProcessMessage(
      BroadcastStateMachine::Message::START);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::STREAMING);

  ASSERT_TRUE(big_cfg.has_value());
  ASSERT_EQ(big_cfg->status, 0);
  // This is an implementation specific thing
  ASSERT_EQ(big_cfg->big_id, broadcasts_[broadcast_id]->GetAdvertisingSid());
  ASSERT_EQ(big_cfg->connection_handles.size(), num_channels);
}

TEST_F(StateMachineTest, GetBroadcastId) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_NE(bluetooth::le_audio::kBroadcastIdInvalid, broadcast_id);
  ASSERT_EQ(broadcasts_[broadcast_id]->GetState(),
            BroadcastStateMachine::State::CONFIGURED);
}

TEST_F(StateMachineTest, IsPublicBroadcast) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_EQ(broadcasts_[broadcast_id]->IsPublicBroadcast(), true);
}

TEST_F(StateMachineTest, GetBroadcastName) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_EQ(broadcasts_[broadcast_id]->GetBroadcastName(), test_broadcast_name);
}

TEST_F(StateMachineTest, GetBroadcastAnnouncement) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  std::map<uint8_t, std::vector<uint8_t>> metadata = {};
  BroadcastCodecWrapper codec_config(
      {.coding_format = le_audio::types::kLeAudioCodingFormatLC3,
       .vendor_company_id = le_audio::types::kLeAudioVendorCompanyIdUndefined,
       .vendor_codec_id = le_audio::types::kLeAudioVendorCodecIdUndefined},
      {.num_channels = LeAudioCodecConfiguration::kChannelNumberMono,
       .sample_rate = LeAudioCodecConfiguration::kSampleRate16000,
       .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
       .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
      32000, 40);
  auto announcement = prepareAnnouncement(codec_config, metadata);
  broadcasts_[broadcast_id]->UpdateBroadcastAnnouncement(announcement);

  ASSERT_EQ(announcement,
            broadcasts_[broadcast_id]->GetBroadcastAnnouncement());
}

TEST_F(StateMachineTest, GetPublicBroadcastAnnouncement) {
  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  bool is_public_metadata_valid;
  types::LeAudioLtvMap public_ltv = types::LeAudioLtvMap::Parse(
      default_public_metadata.data(), default_public_metadata.size(),
      is_public_metadata_valid);
  bluetooth::le_audio::PublicBroadcastAnnouncementData pb_announcement = {
      .features = test_public_broadcast_features,
      .metadata = public_ltv.Values()};

  broadcasts_[broadcast_id]->UpdatePublicBroadcastAnnouncement(
      broadcast_id, test_broadcast_name, pb_announcement);

  ASSERT_EQ(pb_announcement,
            broadcasts_[broadcast_id]->GetPublicBroadcastAnnouncement());
}

TEST_F(StateMachineTest, AnnouncementTest) {
  AdvertiseParameters adv_params;
  std::vector<uint8_t> a_data;
  std::vector<uint8_t> p_data;

  EXPECT_CALL(*mock_ble_advertising_manager_, StartAdvertisingSet)
      .WillOnce([this, &p_data, &a_data, &adv_params](
                    uint8_t client_id, int reg_id,
                    BleAdvertiserInterface::IdTxPowerStatusCallback register_cb,
                    AdvertiseParameters params,
                    std::vector<uint8_t> advertise_data,
                    std::vector<uint8_t> scan_response_data,
                    PeriodicAdvertisingParameters periodic_params,
                    std::vector<uint8_t> periodic_data, uint16_t duration,
                    uint8_t maxExtAdvEvents,
                    BleAdvertiserInterface::IdStatusCallback timeout_cb) {
        uint8_t advertiser_id = 1;
        uint8_t tx_power = 0;
        uint8_t status = 0;

        // Since we are not using these buffers in this callback it is safe to
        // move them.
        a_data = std::move(advertise_data);
        p_data = std::move(periodic_data);

        adv_params = params;

        this->adv_callbacks_->OnAdvertisingSetStarted(
            BroadcastStateMachine::kLeAudioBroadcastRegId, advertiser_id,
            tx_power, status);
      });

  EXPECT_CALL(*(sm_callbacks_.get()), OnStateMachineCreateStatus(_, true))
      .Times(1);

  auto broadcast_id = InstantiateStateMachine();
  ASSERT_NE(broadcast_id, BroadcastStateMachine::kAdvSidUndefined);

  // Check ext. advertising data for Broadcast Announcement UUID
  ASSERT_NE(a_data[0], 0);     // size
  ASSERT_EQ(a_data[1], 0x16);  // BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE
  ASSERT_EQ(a_data[2], (kBroadcastAudioAnnouncementServiceUuid & 0x00FF));
  ASSERT_EQ(a_data[3],
            ((kBroadcastAudioAnnouncementServiceUuid >> 8) & 0x00FF));
  ASSERT_EQ(a_data[4], (broadcast_id & 0x0000FF));
  ASSERT_EQ(a_data[5], ((broadcast_id >> 8) & 0x0000FF));
  ASSERT_EQ(a_data[6], ((broadcast_id >> 16) & 0x0000FF));

  ASSERT_NE(a_data[7], 0);     // size
  ASSERT_EQ(a_data[8], 0x16);  // BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE
  ASSERT_EQ(a_data[9], (kPublicBroadcastAnnouncementServiceUuid & 0x00FF));
  ASSERT_EQ(a_data[10],
            ((kPublicBroadcastAnnouncementServiceUuid >> 8) & 0x00FF));

  // Check periodic data for Basic Announcement UUID
  ASSERT_NE(p_data[0], 0);     // size
  ASSERT_EQ(p_data[1], 0x16);  // BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE
  ASSERT_EQ(p_data[2], (kBasicAudioAnnouncementServiceUuid & 0x00FF));
  ASSERT_EQ(p_data[3], ((kBasicAudioAnnouncementServiceUuid >> 8) & 0x00FF));

  // Check advertising parameters
  ASSERT_EQ(adv_params.own_address_type,
            BroadcastStateMachine::kBroadcastAdvertisingType);
}

}  // namespace
}  // namespace broadcaster
}  // namespace le_audio
