/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "codec_manager.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "common/init_flags.h"
#include "le_audio_set_configuration_provider.h"
#include "mock_controller.h"
#include "test/mock/mock_legacy_hci_interface.h"

using ::testing::_;
using ::testing::Mock;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::Test;

using bluetooth::hci::iso_manager::kIsoDataPathHci;
using bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault;
using le_audio::set_configurations::AudioSetConfiguration;
using le_audio::types::CodecLocation;
using le_audio::types::kLeAudioDirectionSink;
using le_audio::types::kLeAudioDirectionSource;

void osi_property_set_bool(const char* key, bool value);

template <typename T>
T& le_audio::types::BidirectionalPair<T>::get(uint8_t direction) {
  return (direction == le_audio::types::kLeAudioDirectionSink) ? sink : source;
}

static const std::vector<AudioSetConfiguration> offload_capabilities_none(0);

const std::vector<AudioSetConfiguration>* offload_capabilities =
    &offload_capabilities_none;

static const char* test_flags[] = {
    "INIT_default_log_level_str=LOG_VERBOSE",
    nullptr,
};

namespace bluetooth {
namespace audio {
namespace le_audio {
std::vector<AudioSetConfiguration> get_offload_capabilities() {
  return *offload_capabilities;
}
}  // namespace le_audio
}  // namespace audio
}  // namespace bluetooth

namespace le_audio {
namespace {

void set_mock_offload_capabilities(
    const std::vector<AudioSetConfiguration>& caps) {
  offload_capabilities = &caps;
}

static constexpr char kPropLeAudioOffloadSupported[] =
    "ro.bluetooth.leaudio_offload.supported";
static constexpr char kPropLeAudioOffloadDisabled[] =
    "persist.bluetooth.leaudio_offload.disabled";

class CodecManagerTestBase : public Test {
 public:
  virtual void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags);
    set_mock_offload_capabilities(offload_capabilities_none);

    ON_CALL(controller_interface, SupportsBleIsochronousBroadcaster)
        .WillByDefault(Return(true));
    ON_CALL(controller_interface, SupportsConfigureDataPath)
        .WillByDefault(Return(true));

    controller::SetMockControllerInterface(&controller_interface);
    bluetooth::legacy::hci::testing::SetMock(legacy_hci_mock_);

    codec_manager = CodecManager::GetInstance();
  }

  virtual void TearDown() override {
    codec_manager->Stop();

    controller::SetMockControllerInterface(nullptr);
  }

  NiceMock<controller::MockControllerInterface> controller_interface;
  CodecManager* codec_manager;
  bluetooth::legacy::hci::testing::MockInterface legacy_hci_mock_;
};

/*----------------- ADSP codec manager tests ------------------*/
class CodecManagerTestAdsp : public CodecManagerTestBase {
 public:
  virtual void SetUp() override {
    // Enable the HW offloader
    osi_property_set_bool(kPropLeAudioOffloadSupported, true);
    osi_property_set_bool(kPropLeAudioOffloadDisabled, false);

    CodecManagerTestBase::SetUp();
  }
};

TEST_F(CodecManagerTestAdsp, test_init) {
  ASSERT_EQ(codec_manager, CodecManager::GetInstance());
}

TEST_F(CodecManagerTestAdsp, test_start) {
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                kIsoDataPathPlatformDefault, _))
      .Times(1);
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                kIsoDataPathPlatformDefault, _))
      .Times(1);

  // Verify data path is reset on Stop()
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                kIsoDataPathHci, _))
      .Times(1);
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                kIsoDataPathHci, _))
      .Times(1);

  const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
      offloading_preference(0);
  codec_manager->Start(offloading_preference);

  ASSERT_EQ(codec_manager->GetCodecLocation(), CodecLocation::ADSP);
}

TEST_F(CodecManagerTestAdsp, testStreamConfigurationAdspDownMix) {
  const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
      offloading_preference(0);
  codec_manager->Start(offloading_preference);

  // Current CIS configuration for two earbuds
  std::vector<struct types::cis> cises{
      {
          .id = 0x00,
          .type = types::CisType::CIS_TYPE_BIDIRECTIONAL,
          .conn_handle = 96,
      },
      {
          .id = 0x01,
          .type = types::CisType::CIS_TYPE_BIDIRECTIONAL,
          .conn_handle = 97,
      },
  };

  // Stream parameters
  types::BidirectionalPair<stream_parameters> stream_params{
      .sink =
          {
              .sample_frequency_hz = 16000,
              .frame_duration_us = 10000,
              .octets_per_codec_frame = 40,
              .audio_channel_allocation =
                  codec_spec_conf::kLeAudioLocationFrontLeft,
              .codec_frames_blocks_per_sdu = 1,
              .num_of_channels = 1,
              .num_of_devices = 1,
              .stream_locations =
                  {
                      std::pair<uint16_t, uint32_t>{
                          97 /*conn_handle*/,
                          codec_spec_conf::kLeAudioLocationFrontLeft},
                  },
          },
      .source =
          {
              .sample_frequency_hz = 16000,
              .frame_duration_us = 10000,
              .octets_per_codec_frame = 40,
              .audio_channel_allocation =
                  codec_spec_conf::kLeAudioLocationFrontLeft,
              .codec_frames_blocks_per_sdu = 1,
              .num_of_channels = 1,
              .num_of_devices = 1,
              {
                  std::pair<uint16_t, uint32_t>{
                      97 /*conn_handle*/,
                      codec_spec_conf::kLeAudioLocationBackLeft},
              },
          },
  };

  codec_manager->UpdateCisConfiguration(cises, stream_params.sink,
                                        kLeAudioDirectionSink);
  codec_manager->UpdateCisConfiguration(cises, stream_params.source,
                                        kLeAudioDirectionSource);

  // Verify the offloader config content
  types::BidirectionalPair<std::optional<offload_config>> out_offload_configs;
  codec_manager->UpdateActiveAudioConfig(
      stream_params, {.sink = 44, .source = 44},
      [&out_offload_configs](const offload_config& config, uint8_t direction) {
        out_offload_configs.get(direction) = config;
      });

  // Expect the same configuration for sink and source
  ASSERT_TRUE(out_offload_configs.sink.has_value());
  ASSERT_TRUE(out_offload_configs.source.has_value());
  for (auto direction : {le_audio::types::kLeAudioDirectionSink,
                         le_audio::types::kLeAudioDirectionSource}) {
    uint32_t allocation = 0;
    auto& config = out_offload_configs.get(direction).value();
    ASSERT_EQ(2lu, config.stream_map.size());
    for (const auto& info : config.stream_map) {
      if (info.stream_handle == 96) {
        ASSERT_EQ(codec_spec_conf::kLeAudioLocationFrontRight,
                  info.audio_channel_allocation);
        // The disconnected should be inactive
        ASSERT_FALSE(info.is_stream_active);

      } else if (info.stream_handle == 97) {
        ASSERT_EQ(codec_spec_conf::kLeAudioLocationFrontLeft,
                  info.audio_channel_allocation);
        // The connected should be active
        ASSERT_TRUE(info.is_stream_active);

      } else {
        ASSERT_EQ(97, info.stream_handle);
      }
      allocation |= info.audio_channel_allocation;
    }

    ASSERT_EQ(16, config.bits_per_sample);
    ASSERT_EQ(16000u, config.sampling_rate);
    ASSERT_EQ(10000u, config.frame_duration);
    ASSERT_EQ(40u, config.octets_per_frame);
    ASSERT_EQ(1, config.blocks_per_sdu);
    ASSERT_EQ(44, config.peer_delay_ms);
    ASSERT_EQ(codec_spec_conf::kLeAudioLocationStereo, allocation);
  }

  // Clear the CIS configuration map (no active CISes).
  codec_manager->ClearCisConfiguration(kLeAudioDirectionSink);
  codec_manager->ClearCisConfiguration(kLeAudioDirectionSource);
  out_offload_configs.sink = std::nullopt;
  out_offload_configs.source = std::nullopt;
  codec_manager->UpdateActiveAudioConfig(
      stream_params, {.sink = 44, .source = 44},
      [&out_offload_configs](const offload_config& config, uint8_t direction) {
        out_offload_configs.get(direction) = config;
      });

  // Expect sink & source configurations with empty CIS channel allocation map.
  ASSERT_TRUE(out_offload_configs.sink.has_value());
  ASSERT_TRUE(out_offload_configs.source.has_value());
  for (auto direction : {le_audio::types::kLeAudioDirectionSink,
                         le_audio::types::kLeAudioDirectionSource}) {
    auto& config = out_offload_configs.get(direction).value();
    ASSERT_EQ(0lu, config.stream_map.size());
    ASSERT_EQ(16, config.bits_per_sample);
    ASSERT_EQ(16000u, config.sampling_rate);
    ASSERT_EQ(10000u, config.frame_duration);
    ASSERT_EQ(40u, config.octets_per_frame);
    ASSERT_EQ(1, config.blocks_per_sdu);
    ASSERT_EQ(44, config.peer_delay_ms);
  }
}

TEST_F(CodecManagerTestAdsp, test_capabilities_none) {
  const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
      offloading_preference(0);
  codec_manager->Start(offloading_preference);

  // Verify every context
  for (::le_audio::types::LeAudioContextType ctx_type :
       ::le_audio::types::kLeAudioContextAllTypesArray) {
    ASSERT_EQ(nullptr, codec_manager->GetOffloadCodecConfig(ctx_type));
  }
}

TEST_F(CodecManagerTestAdsp, test_capabilities) {
  for (auto test_context : ::le_audio::types::kLeAudioContextAllTypesArray) {
    // Build the offloader capabilities vector using the configuration provider
    // in HOST mode to get all the .json filce configuration entries.
    std::vector<AudioSetConfiguration> offload_capabilities;
    AudioSetConfigurationProvider::Initialize(
        le_audio::types::CodecLocation::HOST);
    for (auto& cap : *AudioSetConfigurationProvider::Get()->GetConfigurations(
             test_context)) {
      offload_capabilities.push_back(*cap);
    }
    ASSERT_NE(0u, offload_capabilities.size());
    set_mock_offload_capabilities(offload_capabilities);
    // Clean up before the codec manager starts it in ADSP mode.
    AudioSetConfigurationProvider::Cleanup();

    const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
        offloading_preference = {
            {.codec_type =
                 bluetooth::le_audio::LE_AUDIO_CODEC_INDEX_SOURCE_LC3}};
    codec_manager->Start(offloading_preference);

    auto cfg = codec_manager->GetOffloadCodecConfig(test_context);
    ASSERT_NE(nullptr, cfg);
    ASSERT_EQ(offload_capabilities.size(), cfg->size());

    // Clean up the before testing any other offload capabilities.
    codec_manager->Stop();
  }
}

// TODO: Add the unit tests for:
// GetBroadcastOffloadConfig
// UpdateBroadcastConnHandle

/*----------------- HOST codec manager tests ------------------*/
class CodecManagerTestHost : public CodecManagerTestBase {
 public:
  virtual void SetUp() override {
    // Enable the HW offloader
    osi_property_set_bool(kPropLeAudioOffloadSupported, false);
    osi_property_set_bool(kPropLeAudioOffloadDisabled, false);

    CodecManagerTestBase::SetUp();
  }
};

TEST_F(CodecManagerTestHost, test_init) {
  ASSERT_EQ(codec_manager, CodecManager::GetInstance());
}

TEST_F(CodecManagerTestHost, test_start) {
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                kIsoDataPathPlatformDefault, _))
      .Times(0);
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                kIsoDataPathPlatformDefault, _))
      .Times(0);

  // Verify data path is NOT reset on Stop() for the Host encoding session
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                kIsoDataPathHci, _))
      .Times(0);
  EXPECT_CALL(legacy_hci_mock_,
              ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                kIsoDataPathHci, _))
      .Times(0);

  const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
      offloading_preference(0);
  codec_manager->Start(offloading_preference);

  ASSERT_EQ(codec_manager->GetCodecLocation(), CodecLocation::HOST);
}

}  // namespace
}  // namespace le_audio
