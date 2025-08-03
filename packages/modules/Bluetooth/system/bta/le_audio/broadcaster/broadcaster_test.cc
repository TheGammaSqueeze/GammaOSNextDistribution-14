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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <hardware/audio.h>

#include <chrono>

#include "bta/include/bta_le_audio_api.h"
#include "bta/include/bta_le_audio_broadcaster_api.h"
#include "bta/le_audio/broadcaster/mock_state_machine.h"
#include "bta/le_audio/content_control_id_keeper.h"
#include "bta/le_audio/le_audio_types.h"
#include "bta/le_audio/mock_iso_manager.h"
#include "bta/test/common/mock_controller.h"
#include "device/include/controller.h"
#include "stack/include/btm_iso_api.h"
#include "test/common/mock_functions.h"

using namespace std::chrono_literals;

using le_audio::types::AudioContexts;
using le_audio::types::LeAudioContextType;

using testing::_;
using testing::AtLeast;
using testing::DoAll;
using testing::Matcher;
using testing::Mock;
using testing::NotNull;
using testing::Return;
using testing::ReturnRef;
using testing::SaveArg;
using testing::Test;

using namespace bluetooth::le_audio;

using le_audio::DsaMode;
using le_audio::LeAudioCodecConfiguration;
using le_audio::LeAudioSourceAudioHalClient;
using le_audio::broadcaster::BigConfig;
using le_audio::broadcaster::BroadcastCodecWrapper;

// Disables most likely false-positives from base::SplitString()
extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

static base::Callback<void(BT_OCTET8)> generator_cb;

void btsnd_hcic_ble_rand(base::Callback<void(BT_OCTET8)> cb) {
  generator_cb = cb;
}

std::atomic<int> num_async_tasks;
bluetooth::common::MessageLoopThread message_loop_thread("test message loop");
bluetooth::common::MessageLoopThread* get_main_thread() {
  return &message_loop_thread;
}
void invoke_switch_buffer_size_cb(bool is_low_latency_buffer_size) {}

bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task) {
  // Wrap the task with task counter so we could later know if there are
  // any callbacks scheduled and we should wait before performing some actions
  if (!message_loop_thread.DoInThread(
          from_here,
          base::BindOnce(
              [](base::OnceClosure task, std::atomic<int>& num_async_tasks) {
                std::move(task).Run();
                num_async_tasks--;
              },
              std::move(task), std::ref(num_async_tasks)))) {
    LOG(ERROR) << __func__ << ": failed from " << from_here.ToString();
    return BT_STATUS_FAIL;
  }
  num_async_tasks++;
  return BT_STATUS_SUCCESS;
}

static base::MessageLoop* message_loop_;
base::MessageLoop* get_main_message_loop() { return message_loop_; }

static void init_message_loop_thread() {
  num_async_tasks = 0;
  message_loop_thread.StartUp();
  if (!message_loop_thread.IsRunning()) {
    FAIL() << "unable to create message loop thread.";
  }

  if (!message_loop_thread.EnableRealTimeScheduling())
    LOG(ERROR) << "Unable to set real time scheduling";

  message_loop_ = message_loop_thread.message_loop();
  if (message_loop_ == nullptr) FAIL() << "unable to get message loop.";
}

static void cleanup_message_loop_thread() {
  message_loop_ = nullptr;
  message_loop_thread.ShutDown();
}

namespace le_audio {
class MockAudioHalClientEndpoint;
MockAudioHalClientEndpoint* mock_audio_source_;
bool is_audio_hal_acquired;
void (*iso_active_callback)(bool);

std::unique_ptr<LeAudioSourceAudioHalClient>
LeAudioSourceAudioHalClient::AcquireBroadcast() {
  if (mock_audio_source_) {
    std::unique_ptr<LeAudioSourceAudioHalClient> ptr(
        (LeAudioSourceAudioHalClient*)mock_audio_source_);
    is_audio_hal_acquired = true;
    return std::move(ptr);
  }
  return nullptr;
}

static constexpr uint8_t default_ccid = 0xDE;
static constexpr auto default_context =
    static_cast<std::underlying_type<LeAudioContextType>::type>(
        LeAudioContextType::ALERTS);
static constexpr BroadcastCode default_code = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10};
static const std::vector<uint8_t> default_metadata = {
    le_audio::types::kLeAudioMetadataStreamingAudioContextLen + 1,
    le_audio::types::kLeAudioMetadataTypeStreamingAudioContext,
    default_context & 0x00FF, (default_context & 0xFF00) >> 8};
static const std::vector<uint8_t> default_public_metadata = {
    5, le_audio::types::kLeAudioMetadataTypeProgramInfo, 0x1, 0x2, 0x3, 0x4};
// bit 0: encrypted, bit 1: standard quality present
static const uint8_t test_public_broadcast_features = 0x3;

static constexpr uint8_t default_num_of_groups = 1;
static constexpr uint8_t media_ccid = 0xC0;
static constexpr auto media_context =
    static_cast<std::underlying_type<LeAudioContextType>::type>(
        LeAudioContextType::MEDIA);
static const std::vector<uint8_t> media_metadata = {
    le_audio::types::kLeAudioMetadataStreamingAudioContextLen + 1,
    le_audio::types::kLeAudioMetadataTypeStreamingAudioContext,
    media_context & 0x00FF, (media_context & 0xFF00) >> 8};
static const std::string test_broadcast_name = "Test";

class MockLeAudioBroadcasterCallbacks
    : public bluetooth::le_audio::LeAudioBroadcasterCallbacks {
 public:
  MOCK_METHOD((void), OnBroadcastCreated, (uint32_t broadcast_id, bool success),
              (override));
  MOCK_METHOD((void), OnBroadcastDestroyed, (uint32_t broadcast_id),
              (override));
  MOCK_METHOD((void), OnBroadcastStateChanged,
              (uint32_t broadcast_id,
               bluetooth::le_audio::BroadcastState state),
              (override));
  MOCK_METHOD((void), OnBroadcastMetadataChanged,
              (uint32_t broadcast_id,
               const BroadcastMetadata& broadcast_metadata),
              (override));
};

class MockAudioHalClientEndpoint : public LeAudioSourceAudioHalClient {
 public:
  MockAudioHalClientEndpoint() = default;
  MOCK_METHOD((bool), Start,
              (const LeAudioCodecConfiguration& codecConfiguration,
               LeAudioSourceAudioHalClient::Callbacks* audioReceiver,
               ::le_audio::DsaModes dsa_modes),
              (override));
  MOCK_METHOD((void), Stop, (), (override));
  MOCK_METHOD((void), ConfirmStreamingRequest, (), (override));
  MOCK_METHOD((void), CancelStreamingRequest, (), (override));
  MOCK_METHOD((void), UpdateRemoteDelay, (uint16_t delay), (override));
  MOCK_METHOD((void), UpdateAudioConfigToHal,
              (const ::le_audio::offload_config&), (override));
  MOCK_METHOD((void), UpdateBroadcastAudioConfigToHal,
              (const ::le_audio::broadcast_offload_config&), (override));
  MOCK_METHOD((void), SuspendedForReconfiguration, (), (override));
  MOCK_METHOD((void), ReconfigurationComplete, (), (override));

  MOCK_METHOD((void), OnDestroyed, ());
  virtual ~MockAudioHalClientEndpoint() { OnDestroyed(); }
};

class BroadcasterTest : public Test {
 protected:
  void SetUp() override {
    init_message_loop_thread();

    reset_mock_function_count_map();
    ON_CALL(controller_interface_, SupportsBleIsochronousBroadcaster)
        .WillByDefault(Return(true));

    controller::SetMockControllerInterface(&controller_interface_);
    iso_manager_ = bluetooth::hci::IsoManager::GetInstance();
    ASSERT_NE(iso_manager_, nullptr);
    iso_manager_->Start();

    is_audio_hal_acquired = false;
    mock_audio_source_ = new MockAudioHalClientEndpoint();
    ON_CALL(*mock_audio_source_, Start).WillByDefault(Return(true));
    ON_CALL(*mock_audio_source_, OnDestroyed).WillByDefault([]() {
      mock_audio_source_ = nullptr;
      is_audio_hal_acquired = false;
    });

    EXPECT_CALL(*MockIsoManager::GetInstance(),
                RegisterOnIsoTrafficActiveCallbacks)
        .WillOnce(SaveArg<0>(&iso_active_callback));

    ASSERT_FALSE(LeAudioBroadcaster::IsLeAudioBroadcasterRunning());
    LeAudioBroadcaster::Initialize(&mock_broadcaster_callbacks_,
                                   base::Bind([]() -> bool { return true; }));

    ContentControlIdKeeper::GetInstance()->Start();
    ContentControlIdKeeper::GetInstance()->SetCcid(LeAudioContextType::MEDIA,
                                                   media_ccid);

    /* Simulate random generator */
    uint8_t random[] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    generator_cb.Run(random);
  }

  void TearDown() override {
    // Message loop cleanup should wait for all the 'till now' scheduled calls
    // so it should be called right at the very begginning of teardown.
    cleanup_message_loop_thread();

    // This is required since Stop() and Cleanup() may trigger some callbacks.
    Mock::VerifyAndClearExpectations(&mock_broadcaster_callbacks_);

    LeAudioBroadcaster::Stop();
    LeAudioBroadcaster::Cleanup();
    ASSERT_FALSE(LeAudioBroadcaster::IsLeAudioBroadcasterRunning());

    ContentControlIdKeeper::GetInstance()->Stop();

    iso_active_callback = nullptr;
    delete mock_audio_source_;
    iso_manager_->Stop();

    controller::SetMockControllerInterface(nullptr);
  }

  uint32_t InstantiateBroadcast(
      std::vector<uint8_t> metadata = default_metadata,
      BroadcastCode code = default_code,
      uint8_t num_of_groups = default_num_of_groups, bool is_queued = false) {
    uint32_t broadcast_id = LeAudioBroadcaster::kInstanceIdUndefined;
    if (!is_queued) {
      EXPECT_CALL(mock_broadcaster_callbacks_, OnBroadcastCreated(_, true))
          .WillOnce(SaveArg<0>(&broadcast_id));
    }

    std::vector<uint8_t> quality_array;
    std::vector<std::vector<uint8_t>> metadata_array;

    for (uint8_t i = 0; i < num_of_groups; i++) {
      // set standard quality for each subgroup
      quality_array.push_back(bluetooth::le_audio::QUALITY_STANDARD);
      // use the same default_metadata for each subgroup
      metadata_array.push_back(metadata);
    }
    // Add multiple subgroup settings with the same content
    LeAudioBroadcaster::Get()->CreateAudioBroadcast(
        true, test_broadcast_name, code, default_public_metadata, quality_array,
        metadata_array);

    return broadcast_id;
  }

 protected:
  MockLeAudioBroadcasterCallbacks mock_broadcaster_callbacks_;
  controller::MockControllerInterface controller_interface_;
  bluetooth::hci::IsoManager* iso_manager_;
};

TEST_F(BroadcasterTest, Initialize) {
  ASSERT_NE(LeAudioBroadcaster::Get(), nullptr);
  ASSERT_TRUE(LeAudioBroadcaster::IsLeAudioBroadcasterRunning());
}

TEST_F(BroadcasterTest, GetStreamingPhy) {
  LeAudioBroadcaster::Get()->SetStreamingPhy(1);
  ASSERT_EQ(LeAudioBroadcaster::Get()->GetStreamingPhy(), 1);
  LeAudioBroadcaster::Get()->SetStreamingPhy(2);
  ASSERT_EQ(LeAudioBroadcaster::Get()->GetStreamingPhy(), 2);
}

TEST_F(BroadcasterTest, CreateAudioBroadcast) {
  auto broadcast_id = InstantiateBroadcast();
  ASSERT_NE(broadcast_id, LeAudioBroadcaster::kInstanceIdUndefined);
  ASSERT_EQ(broadcast_id,
            MockBroadcastStateMachine::GetLastInstance()->GetBroadcastId());

  auto& instance_config = MockBroadcastStateMachine::GetLastInstance()->cfg;
  ASSERT_EQ(instance_config.broadcast_code, default_code);
  for (auto& subgroup : instance_config.announcement.subgroup_configs) {
    ASSERT_EQ(types::LeAudioLtvMap(subgroup.metadata).RawPacket(),
              default_metadata);
  }
  // Note: There shall be a separate test to verify audio parameters
}

TEST_F(BroadcasterTest, CreateAudioBroadcastMultiGroups) {
  // Test with two subgroups
  auto broadcast_id = InstantiateBroadcast(default_metadata, default_code, 2);
  ASSERT_NE(broadcast_id, LeAudioBroadcaster::kInstanceIdUndefined);
  ASSERT_EQ(broadcast_id,
            MockBroadcastStateMachine::GetLastInstance()->GetBroadcastId());

  auto& instance_config = MockBroadcastStateMachine::GetLastInstance()->cfg;
  ASSERT_EQ(instance_config.broadcast_code, default_code);
  ASSERT_EQ(instance_config.announcement.subgroup_configs.size(), (uint8_t) 2);
  for (auto& subgroup : instance_config.announcement.subgroup_configs) {
    ASSERT_EQ(types::LeAudioLtvMap(subgroup.metadata).RawPacket(),
              default_metadata);
  }
}

TEST_F(BroadcasterTest, SuspendAudioBroadcast) {
  auto broadcast_id = InstantiateBroadcast();
  LeAudioBroadcaster::Get()->StartAudioBroadcast(broadcast_id);

  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id, BroadcastState::CONFIGURED))
      .Times(1);

  EXPECT_CALL(*mock_audio_source_, Stop).Times(AtLeast(1));
  LeAudioBroadcaster::Get()->SuspendAudioBroadcast(broadcast_id);
}

TEST_F(BroadcasterTest, StartAudioBroadcast) {
  auto broadcast_id = InstantiateBroadcast();
  LeAudioBroadcaster::Get()->StopAudioBroadcast(broadcast_id);

  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id, BroadcastState::STREAMING))
      .Times(1);

  LeAudioSourceAudioHalClient::Callbacks* audio_receiver;
  EXPECT_CALL(*mock_audio_source_, Start)
      .WillOnce(DoAll(SaveArg<1>(&audio_receiver), Return(true)));

  LeAudioBroadcaster::Get()->StartAudioBroadcast(broadcast_id);
  ASSERT_NE(audio_receiver, nullptr);

  // NOTICE: This is really an implementation specific part, we fake the BIG
  //         config as the mocked state machine does not even call the
  //         IsoManager to prepare one (and that's good since IsoManager is also
  //         a mocked one).
  BigConfig big_cfg;
  big_cfg.big_id =
      MockBroadcastStateMachine::GetLastInstance()->GetAdvertisingSid();
  big_cfg.connection_handles = {0x10, 0x12};
  big_cfg.max_pdu = 128;
  MockBroadcastStateMachine::GetLastInstance()->SetExpectedBigConfig(big_cfg);

  // Inject the audio and verify call on the Iso manager side.
  EXPECT_CALL(*MockIsoManager::GetInstance(), SendIsoData).Times(1);
  std::vector<uint8_t> sample_data(320, 0);
  audio_receiver->OnAudioDataReady(sample_data);
}

TEST_F(BroadcasterTest, StartAudioBroadcastMedia) {
  auto broadcast_id = InstantiateBroadcast(media_metadata);
  LeAudioBroadcaster::Get()->StopAudioBroadcast(broadcast_id);

  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id, BroadcastState::STREAMING))
      .Times(1);

  LeAudioSourceAudioHalClient::Callbacks* audio_receiver;
  EXPECT_CALL(*mock_audio_source_, Start)
      .WillOnce(DoAll(SaveArg<1>(&audio_receiver), Return(true)));

  LeAudioBroadcaster::Get()->StartAudioBroadcast(broadcast_id);
  ASSERT_NE(audio_receiver, nullptr);

  // NOTICE: This is really an implementation specific part, we fake the BIG
  //         config as the mocked state machine does not even call the
  //         IsoManager to prepare one (and that's good since IsoManager is also
  //         a mocked one).
  BigConfig big_cfg;
  big_cfg.big_id =
      MockBroadcastStateMachine::GetLastInstance()->GetAdvertisingSid();
  big_cfg.connection_handles = {0x10, 0x12};
  big_cfg.max_pdu = 128;
  MockBroadcastStateMachine::GetLastInstance()->SetExpectedBigConfig(big_cfg);

  // Inject the audio and verify call on the Iso manager side.
  EXPECT_CALL(*MockIsoManager::GetInstance(), SendIsoData).Times(2);
  std::vector<uint8_t> sample_data(1920, 0);
  audio_receiver->OnAudioDataReady(sample_data);
}

TEST_F(BroadcasterTest, StopAudioBroadcast) {
  auto broadcast_id = InstantiateBroadcast();
  LeAudioBroadcaster::Get()->StartAudioBroadcast(broadcast_id);

  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id, BroadcastState::STOPPED))
      .Times(1);

  EXPECT_CALL(*mock_audio_source_, Stop).Times(AtLeast(1));
  LeAudioBroadcaster::Get()->StopAudioBroadcast(broadcast_id);
}

TEST_F(BroadcasterTest, DestroyAudioBroadcast) {
  auto broadcast_id = InstantiateBroadcast();

  EXPECT_CALL(mock_broadcaster_callbacks_, OnBroadcastDestroyed(broadcast_id))
      .Times(1);
  LeAudioBroadcaster::Get()->DestroyAudioBroadcast(broadcast_id);

  // Expect not being able to interact with this Broadcast
  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id, _))
      .Times(0);

  EXPECT_CALL(*mock_audio_source_, Stop).Times(0);
  LeAudioBroadcaster::Get()->StopAudioBroadcast(broadcast_id);

  EXPECT_CALL(*mock_audio_source_, Start).Times(0);
  LeAudioBroadcaster::Get()->StartAudioBroadcast(broadcast_id);

  EXPECT_CALL(*mock_audio_source_, Stop).Times(0);
  LeAudioBroadcaster::Get()->SuspendAudioBroadcast(broadcast_id);
}

TEST_F(BroadcasterTest, GetBroadcastAllStates) {
  auto broadcast_id = InstantiateBroadcast();
  auto broadcast_id2 = InstantiateBroadcast();
  ASSERT_NE(broadcast_id, LeAudioBroadcaster::kInstanceIdUndefined);
  ASSERT_NE(broadcast_id2, LeAudioBroadcaster::kInstanceIdUndefined);
  ASSERT_NE(broadcast_id, broadcast_id2);

  /* In the current implementation state machine switches to the correct state
   * on itself, therefore here when we use mocked state machine this is not
   * being verified.
   */
  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id, _))
      .Times(1);
  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastStateChanged(broadcast_id2, _))
      .Times(1);

  LeAudioBroadcaster::Get()->GetAllBroadcastStates();
}

TEST_F(BroadcasterTest, UpdateMetadata) {
  auto broadcast_id = InstantiateBroadcast();
  std::vector<uint8_t> ccid_list;
  std::vector<uint8_t> expected_public_meta;
  std::string expected_broadcast_name;

  EXPECT_CALL(*MockBroadcastStateMachine::GetLastInstance(),
              UpdateBroadcastAnnouncement)
      .WillOnce(
          [&](bluetooth::le_audio::BasicAudioAnnouncementData announcement) {
            for (auto subgroup : announcement.subgroup_configs) {
              if (subgroup.metadata.count(
                      types::kLeAudioMetadataTypeCcidList)) {
                ccid_list =
                    subgroup.metadata.at(types::kLeAudioMetadataTypeCcidList);
                break;
              }
            }
          });

  EXPECT_CALL(*MockBroadcastStateMachine::GetLastInstance(),
              UpdatePublicBroadcastAnnouncement)
      .WillOnce([&](uint32_t broadcast_id, const std::string& broadcast_name,
                    const bluetooth::le_audio::PublicBroadcastAnnouncementData&
                        announcement) {
        expected_broadcast_name = broadcast_name;
        expected_public_meta =
            types::LeAudioLtvMap(announcement.metadata).RawPacket();
      });

  ContentControlIdKeeper::GetInstance()->SetCcid(LeAudioContextType::ALERTS,
                                                 default_ccid);

  LeAudioBroadcaster::Get()->UpdateMetadata(
      broadcast_id, test_broadcast_name, default_public_metadata,
      {std::vector<uint8_t>({0x02, 0x01, 0x02, 0x03, 0x02, 0x04, 0x04})});

  ASSERT_EQ(2u, ccid_list.size());
  ASSERT_NE(0, std::count(ccid_list.begin(), ccid_list.end(), media_ccid));
  ASSERT_NE(0, std::count(ccid_list.begin(), ccid_list.end(), default_ccid));
  ASSERT_EQ(expected_broadcast_name, test_broadcast_name);
  ASSERT_EQ(expected_public_meta, default_public_metadata);
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

TEST_F(BroadcasterTest, UpdateMetadataFromAudioTrackMetadata) {
  ContentControlIdKeeper::GetInstance()->SetCcid(LeAudioContextType::MEDIA,
                                                 media_ccid);
  auto broadcast_id = InstantiateBroadcast();

  LeAudioSourceAudioHalClient::Callbacks* audio_receiver;
  EXPECT_CALL(*mock_audio_source_, Start)
      .WillOnce(DoAll(SaveArg<1>(&audio_receiver), Return(true)));

  LeAudioBroadcaster::Get()->StartAudioBroadcast(broadcast_id);
  ASSERT_NE(audio_receiver, nullptr);

  auto sm = MockBroadcastStateMachine::GetLastInstance();
  std::vector<uint8_t> ccid_list;
  std::vector<uint8_t> context_types_map;
  EXPECT_CALL(*sm, UpdateBroadcastAnnouncement)
      .WillOnce(
          [&](bluetooth::le_audio::BasicAudioAnnouncementData announcement) {
            for (auto subgroup : announcement.subgroup_configs) {
              if (subgroup.metadata.count(
                      types::kLeAudioMetadataTypeCcidList)) {
                ccid_list =
                    subgroup.metadata.at(types::kLeAudioMetadataTypeCcidList);
              }
              if (subgroup.metadata.count(
                      types::kLeAudioMetadataTypeStreamingAudioContext)) {
                context_types_map = subgroup.metadata.at(
                    types::kLeAudioMetadataTypeStreamingAudioContext);
              }
            }
          });

  std::map<uint8_t, std::vector<uint8_t>> meta = {};
  BroadcastCodecWrapper codec_config(
      {.coding_format = le_audio::types::kLeAudioCodingFormatLC3,
       .vendor_company_id = le_audio::types::kLeAudioVendorCompanyIdUndefined,
       .vendor_codec_id = le_audio::types::kLeAudioVendorCodecIdUndefined},
      {.num_channels = LeAudioCodecConfiguration::kChannelNumberMono,
       .sample_rate = LeAudioCodecConfiguration::kSampleRate16000,
       .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
       .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
      40);
  auto announcement = prepareAnnouncement(codec_config, meta);

  ON_CALL(*sm, GetBroadcastAnnouncement())
      .WillByDefault(ReturnRef(announcement));

  std::vector<struct playback_track_metadata> multitrack_source_metadata = {
      {{AUDIO_USAGE_GAME, AUDIO_CONTENT_TYPE_SONIFICATION, 0},
       {AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, 0},
       {AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING, AUDIO_CONTENT_TYPE_SPEECH,
        0},
       {AUDIO_USAGE_UNKNOWN, AUDIO_CONTENT_TYPE_UNKNOWN, 0}}};

  std::vector<playback_track_metadata_v7> tracks_vec;
  tracks_vec.reserve(multitrack_source_metadata.size());
  for (const auto& track : multitrack_source_metadata) {
    playback_track_metadata_v7 desc_track = {
        .base =
            {
                .usage = static_cast<audio_usage_t>(track.usage),
                .content_type =
                    static_cast<audio_content_type_t>(track.content_type),
                .gain = track.gain,
            },
    };
    tracks_vec.push_back(desc_track);
  }

  audio_receiver->OnAudioMetadataUpdate(std::move(tracks_vec),
                                        DsaMode::DISABLED);

  // Verify ccid
  ASSERT_NE(ccid_list.size(), 0u);
  ASSERT_TRUE(std::find(ccid_list.begin(), ccid_list.end(), media_ccid) !=
              ccid_list.end());

  // Verify context type
  ASSERT_NE(context_types_map.size(), 0u);
  AudioContexts context_type;
  auto pp = context_types_map.data();
  STREAM_TO_UINT16(context_type.value_ref(), pp);
  ASSERT_TRUE(context_type.test_all(LeAudioContextType::MEDIA |
                                    LeAudioContextType::GAME));
}

TEST_F(BroadcasterTest, GetMetadata) {
  auto broadcast_id = InstantiateBroadcast();
  bluetooth::le_audio::BroadcastMetadata metadata;

  static const uint8_t test_adv_sid = 0x14;
  std::optional<bluetooth::le_audio::BroadcastCode> test_broadcast_code =
      bluetooth::le_audio::BroadcastCode({1, 2, 3, 4, 5, 6});

  auto sm = MockBroadcastStateMachine::GetLastInstance();

  std::map<uint8_t, std::vector<uint8_t>> meta = {};
  BroadcastCodecWrapper codec_config(
      {.coding_format = le_audio::types::kLeAudioCodingFormatLC3,
       .vendor_company_id = le_audio::types::kLeAudioVendorCompanyIdUndefined,
       .vendor_codec_id = le_audio::types::kLeAudioVendorCodecIdUndefined},
      {.num_channels = LeAudioCodecConfiguration::kChannelNumberMono,
       .sample_rate = LeAudioCodecConfiguration::kSampleRate16000,
       .bits_per_sample = LeAudioCodecConfiguration::kBitsPerSample16,
       .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us},
      40);
  auto announcement = prepareAnnouncement(codec_config, meta);

  bool is_public_metadata_valid;
  types::LeAudioLtvMap public_ltv = types::LeAudioLtvMap::Parse(
      default_public_metadata.data(), default_public_metadata.size(),
      is_public_metadata_valid);
  PublicBroadcastAnnouncementData pb_announcement = {
      .features = test_public_broadcast_features,
      .metadata = public_ltv.Values()};

  ON_CALL(*sm, IsPublicBroadcast()).WillByDefault(Return(true));
  ON_CALL(*sm, GetBroadcastName()).WillByDefault(Return(test_broadcast_name));
  ON_CALL(*sm, GetBroadcastCode()).WillByDefault(Return(test_broadcast_code));
  ON_CALL(*sm, GetAdvertisingSid()).WillByDefault(Return(test_adv_sid));
  ON_CALL(*sm, GetBroadcastAnnouncement())
      .WillByDefault(ReturnRef(announcement));
  ON_CALL(*sm, GetPublicBroadcastAnnouncement())
      .WillByDefault(ReturnRef(pb_announcement));

  EXPECT_CALL(mock_broadcaster_callbacks_,
              OnBroadcastMetadataChanged(broadcast_id, _))
      .Times(1)
      .WillOnce(SaveArg<1>(&metadata));
  LeAudioBroadcaster::Get()->GetBroadcastMetadata(broadcast_id);

  ASSERT_NE(LeAudioBroadcaster::kInstanceIdUndefined, metadata.broadcast_id);
  ASSERT_EQ(sm->GetBroadcastId(), metadata.broadcast_id);
  ASSERT_EQ(sm->GetBroadcastCode(), metadata.broadcast_code);
  ASSERT_EQ(sm->GetBroadcastAnnouncement(), metadata.basic_audio_announcement);
  ASSERT_EQ(sm->GetPaInterval(), metadata.pa_interval);
  ASSERT_EQ(sm->GetOwnAddress(), metadata.addr);
  ASSERT_EQ(sm->GetOwnAddressType(), metadata.addr_type);
  ASSERT_EQ(sm->GetAdvertisingSid(), metadata.adv_sid);
  ASSERT_EQ(sm->IsPublicBroadcast(), metadata.is_public);
  ASSERT_EQ(sm->GetBroadcastName(), metadata.broadcast_name);
  ASSERT_EQ(sm->GetPublicBroadcastAnnouncement(), metadata.public_announcement);
}

TEST_F(BroadcasterTest, SetStreamingPhy) {
  LeAudioBroadcaster::Get()->SetStreamingPhy(2);
  // From now on new streams should be using Phy = 2.
  InstantiateBroadcast();
  ASSERT_EQ(MockBroadcastStateMachine::GetLastInstance()->cfg.streaming_phy, 2);

  // From now on new streams should be using Phy = 1.
  LeAudioBroadcaster::Get()->SetStreamingPhy(1);
  InstantiateBroadcast();
  ASSERT_EQ(MockBroadcastStateMachine::GetLastInstance()->cfg.streaming_phy, 1);
  ASSERT_EQ(LeAudioBroadcaster::Get()->GetStreamingPhy(), 1);
}

TEST_F(BroadcasterTest, StreamParamsAlerts) {
  uint8_t expected_channels = 1u;
  InstantiateBroadcast();
  auto config = MockBroadcastStateMachine::GetLastInstance()->cfg;

  // Check audio configuration
  ASSERT_EQ(config.codec_wrapper.GetNumChannels(), expected_channels);
  // Matches number of bises in the announcement
  ASSERT_EQ(config.announcement.subgroup_configs[0].bis_configs.size(),
            expected_channels);
  // Note: Num of bises at IsoManager level is verified by state machine tests
}

TEST_F(BroadcasterTest, StreamParamsMedia) {
  uint8_t expected_channels = 2u;
  ContentControlIdKeeper::GetInstance()->SetCcid(LeAudioContextType::MEDIA,
                                                 media_ccid);
  InstantiateBroadcast(media_metadata);
  auto config = MockBroadcastStateMachine::GetLastInstance()->cfg;

  // Check audio configuration
  ASSERT_EQ(config.codec_wrapper.GetNumChannels(), expected_channels);

  auto& subgroup = config.announcement.subgroup_configs[0];

  // Matches number of bises in the announcement
  ASSERT_EQ(subgroup.bis_configs.size(), expected_channels);
  // Verify CCID for Media
  auto ccid_list_opt = types::LeAudioLtvMap(subgroup.metadata)
                           .Find(le_audio::types::kLeAudioMetadataTypeCcidList);
  ASSERT_TRUE(ccid_list_opt.has_value());
  auto ccid_list = ccid_list_opt.value();
  ASSERT_EQ(1u, ccid_list.size());
  ASSERT_EQ(media_ccid, ccid_list[0]);
  // Note: Num of bises at IsoManager level is verified by state machine tests
}

TEST_F(BroadcasterTest, QueuedBroadcast) {
  uint32_t broadcast_id = LeAudioBroadcaster::kInstanceIdUndefined;

  iso_active_callback(true);

  EXPECT_CALL(mock_broadcaster_callbacks_, OnBroadcastCreated(_, true))
      .WillOnce(SaveArg<0>(&broadcast_id));

  /* Trigger broadcast create but due to active ISO, queue request */
  InstantiateBroadcast(default_metadata, default_code, default_num_of_groups,
                       true);

  /* Notify about ISO being free, check if broadcast would be created */
  iso_active_callback(false);
  ASSERT_NE(broadcast_id, LeAudioBroadcaster::kInstanceIdUndefined);
  ASSERT_EQ(broadcast_id,
            MockBroadcastStateMachine::GetLastInstance()->GetBroadcastId());

  auto& instance_config = MockBroadcastStateMachine::GetLastInstance()->cfg;
  ASSERT_EQ(instance_config.broadcast_code, default_code);
  for (auto& subgroup : instance_config.announcement.subgroup_configs) {
    ASSERT_EQ(types::LeAudioLtvMap(subgroup.metadata).RawPacket(),
              default_metadata);
  }
}

TEST_F(BroadcasterTest, QueuedBroadcastBusyIso) {
  iso_active_callback(true);

  EXPECT_CALL(mock_broadcaster_callbacks_, OnBroadcastCreated(_, true))
      .Times(0);

  /* Trigger broadcast create but due to active ISO, queue request */
  InstantiateBroadcast(default_metadata, default_code, default_num_of_groups,
                       true);
}

}  // namespace le_audio
