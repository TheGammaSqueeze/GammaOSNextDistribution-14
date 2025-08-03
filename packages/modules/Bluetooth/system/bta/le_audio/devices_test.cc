/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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

#include "btif_storage_mock.h"
#include "btm_api_mock.h"
#include "device_groups.h"
#include "le_audio_set_configuration_provider.h"
#include "le_audio_types.h"
#include "mock_controller.h"
#include "mock_csis_client.h"
#include "os/log.h"
#include "stack/btm/btm_int_types.h"

tACL_CONN* btm_bda_to_acl(const RawAddress& bda, tBT_TRANSPORT transport) {
  return nullptr;
}

namespace bluetooth {
namespace le_audio {
namespace internal {
namespace {

using ::le_audio::DeviceConnectState;
using ::le_audio::LeAudioDevice;
using ::le_audio::LeAudioDeviceGroup;
using ::le_audio::LeAudioDevices;
using ::le_audio::types::AseState;
using ::le_audio::types::AudioContexts;
using ::le_audio::types::AudioLocations;
using ::le_audio::types::BidirectionalPair;
using ::le_audio::types::CisType;
using ::le_audio::types::LeAudioContextType;
using testing::_;
using testing::Invoke;
using testing::Return;
using testing::Test;

RawAddress GetTestAddress(int index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {
      {0xC0, 0xDE, 0xC0, 0xDE, 0x00, static_cast<uint8_t>(index)}};
  return result;
}

class LeAudioDevicesTest : public Test {
 protected:
  void SetUp() override {
    devices_ = new LeAudioDevices();
    bluetooth::manager::SetMockBtmInterface(&btm_interface);
    controller::SetMockControllerInterface(&controller_interface_);
    bluetooth::storage::SetMockBtifStorageInterface(&mock_btif_storage_);
  }

  void TearDown() override {
    controller::SetMockControllerInterface(nullptr);
    bluetooth::manager::SetMockBtmInterface(nullptr);
    bluetooth::storage::SetMockBtifStorageInterface(nullptr);
    delete devices_;
  }

  LeAudioDevices* devices_ = nullptr;
  bluetooth::manager::MockBtmInterface btm_interface;
  controller::MockControllerInterface controller_interface_;
  bluetooth::storage::MockBtifStorageInterface mock_btif_storage_;
};

TEST_F(LeAudioDevicesTest, test_add) {
  RawAddress test_address_0 = GetTestAddress(0);
  ASSERT_EQ((size_t)0, devices_->Size());
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  ASSERT_EQ((size_t)1, devices_->Size());
  devices_->Add(GetTestAddress(1), DeviceConnectState::CONNECTING_BY_USER, 1);
  ASSERT_EQ((size_t)2, devices_->Size());
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  ASSERT_EQ((size_t)2, devices_->Size());
  devices_->Add(GetTestAddress(1), DeviceConnectState::CONNECTING_BY_USER, 2);
  ASSERT_EQ((size_t)2, devices_->Size());
}

TEST_F(LeAudioDevicesTest, test_remove) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_1 = GetTestAddress(1);
  devices_->Add(test_address_1, DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_2 = GetTestAddress(2);
  devices_->Add(test_address_2, DeviceConnectState::CONNECTING_BY_USER);
  ASSERT_EQ((size_t)3, devices_->Size());
  devices_->Remove(test_address_0);
  ASSERT_EQ((size_t)2, devices_->Size());
  devices_->Remove(GetTestAddress(3));
  ASSERT_EQ((size_t)2, devices_->Size());
  devices_->Remove(test_address_0);
  ASSERT_EQ((size_t)2, devices_->Size());
}

TEST_F(LeAudioDevicesTest, test_find_by_address_success) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_1 = GetTestAddress(1);
  devices_->Add(test_address_1, DeviceConnectState::DISCONNECTED);
  RawAddress test_address_2 = GetTestAddress(2);
  devices_->Add(test_address_2, DeviceConnectState::CONNECTING_BY_USER);
  LeAudioDevice* device = devices_->FindByAddress(test_address_1);
  ASSERT_NE(nullptr, device);
  ASSERT_EQ(test_address_1, device->address_);
}

TEST_F(LeAudioDevicesTest, test_find_by_address_failed) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_2 = GetTestAddress(2);
  devices_->Add(test_address_2, DeviceConnectState::CONNECTING_BY_USER);
  LeAudioDevice* device = devices_->FindByAddress(GetTestAddress(1));
  ASSERT_EQ(nullptr, device);
}

TEST_F(LeAudioDevicesTest, test_get_by_address_success) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_1 = GetTestAddress(1);
  devices_->Add(test_address_1, DeviceConnectState::DISCONNECTED);
  RawAddress test_address_2 = GetTestAddress(2);
  devices_->Add(test_address_2, DeviceConnectState::CONNECTING_BY_USER);
  std::shared_ptr<LeAudioDevice> device =
      devices_->GetByAddress(test_address_1);
  ASSERT_NE(nullptr, device);
  ASSERT_EQ(test_address_1, device->address_);
}

TEST_F(LeAudioDevicesTest, test_get_by_address_failed) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_2 = GetTestAddress(2);
  devices_->Add(test_address_2, DeviceConnectState::CONNECTING_BY_USER);
  std::shared_ptr<LeAudioDevice> device =
      devices_->GetByAddress(GetTestAddress(1));
  ASSERT_EQ(nullptr, device);
}

TEST_F(LeAudioDevicesTest, test_find_by_conn_id_success) {
  devices_->Add(GetTestAddress(1), DeviceConnectState::CONNECTING_BY_USER);
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  devices_->Add(GetTestAddress(4), DeviceConnectState::CONNECTING_BY_USER);
  LeAudioDevice* device = devices_->FindByAddress(test_address_0);
  device->conn_id_ = 0x0005;
  ASSERT_EQ(device, devices_->FindByConnId(0x0005));
}

TEST_F(LeAudioDevicesTest, test_find_by_conn_id_failed) {
  devices_->Add(GetTestAddress(1), DeviceConnectState::CONNECTING_BY_USER);
  devices_->Add(GetTestAddress(0), DeviceConnectState::CONNECTING_BY_USER);
  devices_->Add(GetTestAddress(4), DeviceConnectState::CONNECTING_BY_USER);
  ASSERT_EQ(nullptr, devices_->FindByConnId(0x0006));
}

TEST_F(LeAudioDevicesTest, test_get_device_model_name_success) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  std::shared_ptr<LeAudioDevice> device =
      devices_->GetByAddress(test_address_0);
  ASSERT_NE(nullptr, device);
  device->model_name_ = "Test";
  ON_CALL(mock_btif_storage_, GetRemoteDeviceProperty(_, _))
      .WillByDefault(Return(BT_STATUS_SUCCESS));
  device->GetDeviceModelName();
  ASSERT_EQ("", device->model_name_);
}

TEST_F(LeAudioDevicesTest, test_get_device_model_name_failed) {
  RawAddress test_address_0 = GetTestAddress(0);
  devices_->Add(test_address_0, DeviceConnectState::CONNECTING_BY_USER);
  std::shared_ptr<LeAudioDevice> device =
      devices_->GetByAddress(test_address_0);
  ASSERT_NE(nullptr, device);
  device->model_name_ = "Test";
  ON_CALL(mock_btif_storage_, GetRemoteDeviceProperty(_, _))
      .WillByDefault(Return(BT_STATUS_FAIL));
  device->GetDeviceModelName();
  ASSERT_EQ("Test", device->model_name_);
}

/* TODO: Add FindByCisConnHdl test cases (ASE) */

}  // namespace

namespace {
using namespace ::le_audio::codec_spec_caps;
using namespace ::le_audio::set_configurations;
using namespace ::le_audio::types;

static const hdl_pair hdl_pair_nil = hdl_pair(0x0000, 0x0000);

enum class Lc3SettingId {
  _BEGIN,
  LC3_8_1 = _BEGIN,
  LC3_8_2,
  LC3_16_1,
  LC3_16_2,
  LC3_24_1,
  LC3_24_2,
  LC3_32_1,
  LC3_32_2,
  LC3_441_1,
  LC3_441_2,
  LC3_48_1,
  LC3_48_2,
  LC3_48_3,
  LC3_48_4,
  LC3_48_5,
  LC3_48_6,
  LC3_VND_1,
  _END,
  UNSUPPORTED = _END,
};
static constexpr int Lc3SettingIdBegin = static_cast<int>(Lc3SettingId::_BEGIN);
static constexpr int Lc3SettingIdEnd = static_cast<int>(Lc3SettingId::_END);

bool IsLc3SettingSupported(LeAudioContextType context_type, Lc3SettingId id) {
  /* Update those values, on any change of codec linked with content type */
  switch (context_type) {
    case LeAudioContextType::RINGTONE:
    case LeAudioContextType::CONVERSATIONAL:
      if (id == Lc3SettingId::LC3_16_1 || id == Lc3SettingId::LC3_16_2 ||
          id == Lc3SettingId::LC3_24_1 || id == Lc3SettingId::LC3_24_2 ||
          id == Lc3SettingId::LC3_32_1 || id == Lc3SettingId::LC3_32_2 ||
          id == Lc3SettingId::LC3_48_1 || id == Lc3SettingId::LC3_48_2 ||
          id == Lc3SettingId::LC3_48_3 || id == Lc3SettingId::LC3_48_4 ||
          id == Lc3SettingId::LC3_VND_1)
        return true;

      break;

    case LeAudioContextType::MEDIA:
    case LeAudioContextType::ALERTS:
    case LeAudioContextType::INSTRUCTIONAL:
    case LeAudioContextType::NOTIFICATIONS:
    case LeAudioContextType::EMERGENCYALARM:
    case LeAudioContextType::UNSPECIFIED:
      if (id == Lc3SettingId::LC3_16_1 || id == Lc3SettingId::LC3_16_2 ||
          id == Lc3SettingId::LC3_48_4 || id == Lc3SettingId::LC3_48_1 ||
          id == Lc3SettingId::LC3_48_2 || id == Lc3SettingId::LC3_VND_1 ||
          id == Lc3SettingId::LC3_24_2)
        return true;

      break;

    default:
      if (id == Lc3SettingId::LC3_16_2) return true;

      break;
  };

  return false;
}

static constexpr uint8_t kLeAudioSamplingFreqRfu = 0x0E;
uint8_t GetSamplingFrequency(Lc3SettingId id) {
  switch (id) {
    case Lc3SettingId::LC3_8_1:
    case Lc3SettingId::LC3_8_2:
      return ::le_audio::codec_spec_conf::kLeAudioSamplingFreq8000Hz;
    case Lc3SettingId::LC3_16_1:
    case Lc3SettingId::LC3_16_2:
      return ::le_audio::codec_spec_conf::kLeAudioSamplingFreq16000Hz;
    case Lc3SettingId::LC3_24_1:
    case Lc3SettingId::LC3_24_2:
      return ::le_audio::codec_spec_conf::kLeAudioSamplingFreq24000Hz;
    case Lc3SettingId::LC3_32_1:
    case Lc3SettingId::LC3_32_2:
      return ::le_audio::codec_spec_conf::kLeAudioSamplingFreq32000Hz;
    case Lc3SettingId::LC3_441_1:
    case Lc3SettingId::LC3_441_2:
      return ::le_audio::codec_spec_conf::kLeAudioSamplingFreq44100Hz;
    case Lc3SettingId::LC3_48_1:
    case Lc3SettingId::LC3_48_2:
    case Lc3SettingId::LC3_48_3:
    case Lc3SettingId::LC3_48_4:
    case Lc3SettingId::LC3_48_5:
    case Lc3SettingId::LC3_48_6:
    case Lc3SettingId::LC3_VND_1:
      return ::le_audio::codec_spec_conf::kLeAudioSamplingFreq48000Hz;
    case Lc3SettingId::UNSUPPORTED:
      return kLeAudioSamplingFreqRfu;
  }
}

static constexpr uint8_t kLeAudioCodecFrameDurRfu = 0x02;
uint8_t GetFrameDuration(Lc3SettingId id) {
  switch (id) {
    case Lc3SettingId::LC3_8_1:
    case Lc3SettingId::LC3_16_1:
    case Lc3SettingId::LC3_24_1:
    case Lc3SettingId::LC3_32_1:
    case Lc3SettingId::LC3_441_1:
    case Lc3SettingId::LC3_48_1:
    case Lc3SettingId::LC3_48_3:
    case Lc3SettingId::LC3_48_5:
      return ::le_audio::codec_spec_conf::kLeAudioCodecFrameDur7500us;
    case Lc3SettingId::LC3_8_2:
    case Lc3SettingId::LC3_16_2:
    case Lc3SettingId::LC3_24_2:
    case Lc3SettingId::LC3_32_2:
    case Lc3SettingId::LC3_441_2:
    case Lc3SettingId::LC3_48_2:
    case Lc3SettingId::LC3_48_4:
    case Lc3SettingId::LC3_48_6:
    case Lc3SettingId::LC3_VND_1:
      return ::le_audio::codec_spec_conf::kLeAudioCodecFrameDur10000us;
    case Lc3SettingId::UNSUPPORTED:
      return kLeAudioCodecFrameDurRfu;
  }
}

static constexpr uint8_t kLeAudioCodecLC3OctetsPerCodecFrameInvalid = 0;
uint16_t GetOctetsPerCodecFrame(Lc3SettingId id) {
  switch (id) {
    case Lc3SettingId::LC3_8_1:
      return 26;
    case Lc3SettingId::LC3_8_2:
    case Lc3SettingId::LC3_16_1:
      return 30;
    case Lc3SettingId::LC3_16_2:
      return 40;
    case Lc3SettingId::LC3_24_1:
      return 45;
    case Lc3SettingId::LC3_24_2:
    case Lc3SettingId::LC3_32_1:
      return 60;
    case Lc3SettingId::LC3_32_2:
      return 80;
    case Lc3SettingId::LC3_441_1:
      return 97;
    case Lc3SettingId::LC3_441_2:
      return 130;
    case Lc3SettingId::LC3_48_1:
      return 75;
    case Lc3SettingId::LC3_48_2:
    case Lc3SettingId::LC3_VND_1:
      return 100;
    case Lc3SettingId::LC3_48_3:
      return 90;
    case Lc3SettingId::LC3_48_4:
      return 120;
    case Lc3SettingId::LC3_48_5:
      return 116;
    case Lc3SettingId::LC3_48_6:
      return 155;
    case Lc3SettingId::UNSUPPORTED:
      return kLeAudioCodecLC3OctetsPerCodecFrameInvalid;
  }
}

class PublishedAudioCapabilitiesBuilder {
 public:
  PublishedAudioCapabilitiesBuilder() {}

  void Add(LeAudioCodecId codec_id, uint8_t conf_sampling_frequency,
           uint8_t conf_frame_duration, uint8_t audio_channel_counts,
           uint16_t octets_per_frame, uint8_t codec_frames_per_sdu = 0) {
    uint16_t sampling_frequencies =
        SamplingFreqConfig2Capability(conf_sampling_frequency);
    uint8_t frame_durations =
        FrameDurationConfig2Capability(conf_frame_duration);
    uint8_t max_codec_frames_per_sdu = codec_frames_per_sdu;
    uint32_t octets_per_frame_range =
        octets_per_frame | (octets_per_frame << 16);

    pac_records_.push_back(
        acs_ac_record({.codec_id = codec_id,
                       .codec_spec_caps = LeAudioLtvMap({
                           {kLeAudioLtvTypeSupportedSamplingFrequencies,
                            UINT16_TO_VEC_UINT8(sampling_frequencies)},
                           {kLeAudioLtvTypeSupportedFrameDurations,
                            UINT8_TO_VEC_UINT8(frame_durations)},
                           {kLeAudioLtvTypeSupportedAudioChannelCounts,
                            UINT8_TO_VEC_UINT8(audio_channel_counts)},
                           {kLeAudioLtvTypeSupportedOctetsPerCodecFrame,
                            UINT32_TO_VEC_UINT8(octets_per_frame_range)},
                           {kLeAudioLtvTypeSupportedMaxCodecFramesPerSdu,
                            UINT8_TO_VEC_UINT8(max_codec_frames_per_sdu)},
                       }),
                       .metadata = std::vector<uint8_t>(0)}));
  }

  void Add(LeAudioCodecId codec_id, uint16_t capa_sampling_frequency,
           uint8_t capa_frame_duration, uint8_t audio_channel_counts,
           uint16_t octets_per_frame_min, uint16_t ocets_per_frame_max,
           uint8_t codec_frames_per_sdu = 1) {
    uint32_t octets_per_frame_range =
        octets_per_frame_min | (ocets_per_frame_max << 16);

    pac_records_.push_back(
        acs_ac_record({.codec_id = codec_id,
                       .codec_spec_caps = LeAudioLtvMap({
                           {kLeAudioLtvTypeSupportedSamplingFrequencies,
                            UINT16_TO_VEC_UINT8(capa_sampling_frequency)},
                           {kLeAudioLtvTypeSupportedFrameDurations,
                            UINT8_TO_VEC_UINT8(capa_frame_duration)},
                           {kLeAudioLtvTypeSupportedAudioChannelCounts,
                            UINT8_TO_VEC_UINT8(audio_channel_counts)},
                           {kLeAudioLtvTypeSupportedOctetsPerCodecFrame,
                            UINT32_TO_VEC_UINT8(octets_per_frame_range)},
                           {kLeAudioLtvTypeSupportedMaxCodecFramesPerSdu,
                            UINT8_TO_VEC_UINT8(codec_frames_per_sdu)},
                       }),
                       .metadata = std::vector<uint8_t>(0)}));
  }

  void Add(const CodecConfigSetting& setting, uint8_t audio_channel_counts) {
    if (setting.id != LeAudioCodecIdLc3) return;

    const LeAudioCoreCodecConfig core_config =
        setting.params.GetAsCoreCodecConfig();
    Add(setting.id, *core_config.sampling_frequency,
        *core_config.frame_duration, audio_channel_counts,
        *core_config.octets_per_codec_frame);
  }

  void Reset() { pac_records_.clear(); }

  PublishedAudioCapabilities Get() {
    return PublishedAudioCapabilities({{hdl_pair_nil, pac_records_}});
  }

 private:
  std::vector<acs_ac_record> pac_records_;
};

struct TestGroupAseConfigurationData {
  LeAudioDevice* device;
  uint8_t audio_channel_counts_snk;
  uint8_t audio_channel_counts_src;

  /* Note, do not confuse ASEs with channels num. */
  uint8_t expected_active_channel_num_snk;
  uint8_t expected_active_channel_num_src;
};

class LeAudioAseConfigurationTest : public Test {
 protected:
  void SetUp() override {
    group_ = new LeAudioDeviceGroup(group_id_);
    bluetooth::manager::SetMockBtmInterface(&btm_interface_);
    controller::SetMockControllerInterface(&controller_interface_);
    ::le_audio::AudioSetConfigurationProvider::Initialize(
        ::le_audio::types::CodecLocation::ADSP);
    MockCsisClient::SetMockInstanceForTesting(&mock_csis_client_module_);
    ON_CALL(mock_csis_client_module_, Get())
        .WillByDefault(Return(&mock_csis_client_module_));
    ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
        .WillByDefault(Return(true));
    ON_CALL(mock_csis_client_module_, GetDeviceList(_))
        .WillByDefault(Invoke([this](int group_id) { return addresses_; }));
    ON_CALL(mock_csis_client_module_, GetDesiredSize(_))
        .WillByDefault(
            Invoke([this](int group_id) { return (int)(addresses_.size()); }));
  }

  void TearDown() override {
    controller::SetMockControllerInterface(nullptr);
    bluetooth::manager::SetMockBtmInterface(nullptr);
    devices_.clear();
    addresses_.clear();
    delete group_;
    ::le_audio::AudioSetConfigurationProvider::Cleanup();
  }

  LeAudioDevice* AddTestDevice(int snk_ase_num, int src_ase_num,
                               int snk_ase_num_cached = 0,
                               int src_ase_num_cached = 0,
                               bool invert_ases_emplacement = false,
                               bool out_of_range_device = false) {
    int index = group_->Size() + 1;
    auto device = (std::make_shared<LeAudioDevice>(
        GetTestAddress(index), DeviceConnectState::DISCONNECTED));
    devices_.push_back(device);
    LOG_INFO(" addresses %d", (int)(addresses_.size()));
    addresses_.push_back(device->address_);
    LOG_INFO(" Addresses %d", (int)(addresses_.size()));

    if (out_of_range_device == false) {
      group_->AddNode(device);
    }

    int ase_id = 1;
    for (int i = 0; i < (invert_ases_emplacement ? snk_ase_num : src_ase_num);
         i++) {
      device->ases_.emplace_back(0x0000, 0x0000,
                                 invert_ases_emplacement
                                     ? kLeAudioDirectionSink
                                     : kLeAudioDirectionSource,
                                 ase_id++);
    }

    for (int i = 0; i < (invert_ases_emplacement ? src_ase_num : snk_ase_num);
         i++) {
      device->ases_.emplace_back(0x0000, 0x0000,
                                 invert_ases_emplacement
                                     ? kLeAudioDirectionSource
                                     : kLeAudioDirectionSink,
                                 ase_id++);
    }

    for (int i = 0; i < (invert_ases_emplacement ? snk_ase_num_cached
                                                 : src_ase_num_cached);
         i++) {
      struct ase ase(0x0000, 0x0000,
                     invert_ases_emplacement ? kLeAudioDirectionSink
                                             : kLeAudioDirectionSource,
                     ase_id++);
      ase.state = AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;
      device->ases_.push_back(ase);
    }

    for (int i = 0; i < (invert_ases_emplacement ? src_ase_num_cached
                                                 : snk_ase_num_cached);
         i++) {
      struct ase ase(0x0000, 0x0000,
                     invert_ases_emplacement ? kLeAudioDirectionSource
                                             : kLeAudioDirectionSink,
                     ase_id++);
      ase.state = AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;
      device->ases_.push_back(ase);
    }

    device->SetSupportedContexts(
        {.sink = AudioContexts(kLeAudioContextAllTypes),
         .source = AudioContexts(kLeAudioContextAllTypes)});
    device->SetAvailableContexts(
        {.sink = AudioContexts(kLeAudioContextAllTypes),
         .source = AudioContexts(kLeAudioContextAllTypes)});
    device->snk_audio_locations_ =
        ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft |
        ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
    device->src_audio_locations_ =
        ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft |
        ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;

    device->conn_id_ = index;
    device->SetConnectionState(out_of_range_device
                                   ? DeviceConnectState::DISCONNECTED
                                   : DeviceConnectState::CONNECTED);
    group_->ReloadAudioDirections();
    group_->ReloadAudioLocations();
    return device.get();
  }

  bool TestGroupAseConfigurationVerdict(
      const TestGroupAseConfigurationData& data, uint8_t directions_to_verify) {
    uint8_t active_channel_num_snk = 0;
    uint8_t active_channel_num_src = 0;

    if (directions_to_verify == 0) return false;
    if (data.device->HaveActiveAse() == 0) return false;

    for (ase* ase = data.device->GetFirstActiveAse(); ase;
         ase = data.device->GetNextActiveAse(ase)) {
      auto core_config = ase->codec_config.GetAsCoreCodecConfig();

      if (ase->direction == kLeAudioDirectionSink)
        active_channel_num_snk += core_config.GetChannelCountPerIsoStream();
      else
        active_channel_num_src += core_config.GetChannelCountPerIsoStream();
    }

    bool result = true;
    if (directions_to_verify & kLeAudioDirectionSink) {
      result &=
          (data.expected_active_channel_num_snk == active_channel_num_snk);
    }
    if (directions_to_verify & kLeAudioDirectionSource) {
      result &=
          (data.expected_active_channel_num_src == active_channel_num_src);
    }

    return result;
  }

  void SetCisInformationToActiveAse(void) {
    uint8_t cis_id = 1;
    uint16_t cis_conn_hdl = 0x0060;

    for (auto& device : devices_) {
      for (auto& ase : device->ases_) {
        if (ase.active) {
          ase.cis_id = cis_id++;
          ase.cis_conn_hdl = cis_conn_hdl++;
        }
      }
    }
  }

  void TestSingleAseConfiguration(LeAudioContextType context_type,
                                  TestGroupAseConfigurationData* data,
                                  uint8_t data_size,
                                  const AudioSetConfiguration* audio_set_conf,
                                  uint8_t directions_to_verify) {
    // the configuration should fail if there are no active ases expected
    bool success_expected = data_size > 0;
    uint8_t configuration_directions = 0;

    for (int i = 0; i < data_size; i++) {
      success_expected &= (data[i].expected_active_channel_num_snk +
                           data[i].expected_active_channel_num_src) > 0;

      /* Prepare PAC's */
      PublishedAudioCapabilitiesBuilder snk_pac_builder, src_pac_builder;
      for (const auto& entry : (*audio_set_conf).confs) {
        if (entry.direction == kLeAudioDirectionSink) {
          configuration_directions |= kLeAudioDirectionSink;
          snk_pac_builder.Add(entry.codec, data[i].audio_channel_counts_snk);
        } else {
          configuration_directions |= kLeAudioDirectionSource;
          src_pac_builder.Add(entry.codec, data[i].audio_channel_counts_src);
        }
      }

      data[i].device->snk_pacs_ = snk_pac_builder.Get();
      data[i].device->src_pacs_ = src_pac_builder.Get();
    }

    BidirectionalPair<AudioContexts> group_audio_locations = {
        .sink = AudioContexts(context_type),
        .source = AudioContexts(context_type)};

    /* Stimulate update of available context map */
    group_->UpdateAudioContextAvailability();

    ASSERT_EQ(success_expected,
              group_->Configure(context_type, group_audio_locations));

    bool result = true;
    for (int i = 0; i < data_size; i++) {
      result &= TestGroupAseConfigurationVerdict(
          data[i], directions_to_verify & configuration_directions);
    }
    ASSERT_TRUE(result);
  }

  int getNumOfAses(LeAudioDevice* device, uint8_t direction) {
    return std::count_if(
        device->ases_.begin(), device->ases_.end(),
        [direction](auto& a) { return a.direction == direction; });
  }

  void TestGroupAseConfiguration(
      LeAudioContextType context_type, TestGroupAseConfigurationData* data,
      uint8_t data_size,
      uint8_t directions_to_verify = kLeAudioDirectionSink |
                                     kLeAudioDirectionSource) {
    const auto* configurations =
        ::le_audio::AudioSetConfigurationProvider::Get()->GetConfigurations(
            context_type);

    bool success_expected = directions_to_verify != 0;
    int num_of_matching_configurations = 0;
    for (const auto& audio_set_conf : *configurations) {
      bool interesting_configuration = true;
      uint8_t configuration_directions = 0;

      // the configuration should fail if there are no active ases expected
      PublishedAudioCapabilitiesBuilder snk_pac_builder, src_pac_builder;
      snk_pac_builder.Reset();
      src_pac_builder.Reset();

      /* Let's go thru devices in the group and configure them*/
      for (int i = 0; i < data_size; i++) {
        int num_of_ase_snk_per_dev = 0;
        int num_of_ase_src_per_dev = 0;

        /* Prepare PAC's for each device. Also make sure configuration is in our
         * interest to test */
        for (const auto& entry : (*audio_set_conf).confs) {
          /* We are interested in the configurations which contains exact number
           * of devices and number of ases is same the number of expected ases
           * to active
           */
          if (entry.device_cnt != data_size) {
            interesting_configuration = false;
          }

          /* Make sure the strategy is the expected one */
          if (entry.direction == kLeAudioDirectionSink &&
              group_->GetGroupStrategy(group_->Size()) != entry.strategy) {
            interesting_configuration = false;
          }

          if (entry.direction == kLeAudioDirectionSink) {
            configuration_directions |= kLeAudioDirectionSink;
            num_of_ase_snk_per_dev = entry.ase_cnt / data_size;
            snk_pac_builder.Add(entry.codec, data[i].audio_channel_counts_snk);
          } else {
            configuration_directions |= kLeAudioDirectionSource;
            num_of_ase_src_per_dev = entry.ase_cnt / data_size;
            src_pac_builder.Add(entry.codec, data[i].audio_channel_counts_src);
          }

          data[i].device->snk_pacs_ = snk_pac_builder.Get();
          data[i].device->src_pacs_ = src_pac_builder.Get();
        }

        /* Make sure configuration can satisfy number of expected active ASEs*/
        if (num_of_ase_snk_per_dev >
            data[i].device->GetAseCount(kLeAudioDirectionSink)) {
          interesting_configuration = false;
        }

        if (num_of_ase_src_per_dev >
            data[i].device->GetAseCount(kLeAudioDirectionSource)) {
          interesting_configuration = false;
        }
      }

      BidirectionalPair<AudioContexts> group_audio_locations = {
          .sink = AudioContexts(context_type),
          .source = AudioContexts(context_type)};

      /* Stimulate update of available context map */
      group_->UpdateAudioContextAvailability();
      group_->UpdateAudioSetConfigurationCache(context_type);

      auto configuration_result =
          group_->Configure(context_type, group_audio_locations);

      /* In case of configuration #ase is same as the one we expected to be
       * activated verify, ASEs are actually active */
      if (interesting_configuration &&
          (directions_to_verify == configuration_directions)) {
        ASSERT_TRUE(configuration_result);

        bool matching_conf = true;
        /* Check if each of the devices has activated ASEs as expected */
        for (int i = 0; i < data_size; i++) {
          matching_conf &= TestGroupAseConfigurationVerdict(
              data[i], configuration_directions);
        }

        if (matching_conf) num_of_matching_configurations++;
      }
      group_->Deactivate();

      TestAsesInactive();
    }

    if (success_expected) {
      ASSERT_TRUE((num_of_matching_configurations > 0));
    } else {
      ASSERT_TRUE(num_of_matching_configurations == 0);
    }
  }

  void TestAsesActive(LeAudioCodecId codec_id, uint8_t sampling_frequency,
                      uint8_t frame_duration, uint16_t octets_per_frame) {
    bool active_ase = false;

    for (const auto& device : devices_) {
      for (const auto& ase : device->ases_) {
        if (!ase.active) continue;

        /* Configure may request only partial ases to be activated */
        if (!active_ase && ase.active) active_ase = true;

        ASSERT_EQ(ase.codec_id, codec_id);

        /* FIXME: Validate other codec parameters than LC3 if any */
        ASSERT_EQ(ase.codec_id, LeAudioCodecIdLc3);
        if (ase.codec_id == LeAudioCodecIdLc3) {
          auto core_config = ase.codec_config.GetAsCoreCodecConfig();
          ASSERT_EQ(core_config.sampling_frequency, sampling_frequency);
          ASSERT_EQ(core_config.frame_duration, frame_duration);
          ASSERT_EQ(core_config.octets_per_codec_frame, octets_per_frame);
        }
      }
    }

    ASSERT_TRUE(active_ase);
  }

  void TestActiveAses(void) {
    for (auto& device : devices_) {
      for (const auto& ase : device->ases_) {
        if (ase.active) {
          ASSERT_FALSE(ase.cis_id == ::le_audio::kInvalidCisId);
        }
      }
    }
  }

  void TestAsesInactivated(const LeAudioDevice* device) {
    for (const auto& ase : device->ases_) {
      ASSERT_FALSE(ase.active);
      ASSERT_TRUE(ase.cis_id == ::le_audio::kInvalidCisId);
      ASSERT_TRUE(ase.cis_conn_hdl == 0);
    }
  }

  void TestAsesInactive() {
    for (const auto& device : devices_) {
      for (const auto& ase : device->ases_) {
        ASSERT_FALSE(ase.active);
      }
    }
  }

  void TestLc3CodecConfig(LeAudioContextType context_type) {
    for (int i = Lc3SettingIdBegin; i < Lc3SettingIdEnd; i++) {
      // test each configuration parameter against valid and invalid value
      std::array<Lc3SettingId, 2> test_variants = {static_cast<Lc3SettingId>(i),
                                                   Lc3SettingId::UNSUPPORTED};

      const bool is_lc3_setting_supported =
          IsLc3SettingSupported(context_type, static_cast<Lc3SettingId>(i));

      for (const auto sf_variant : test_variants) {
        uint8_t sampling_frequency = GetSamplingFrequency(sf_variant);
        for (const auto fd_variant : test_variants) {
          uint8_t frame_duration = GetFrameDuration(fd_variant);
          for (const auto opcf_variant : test_variants) {
            uint16_t octets_per_frame = GetOctetsPerCodecFrame(opcf_variant);

            PublishedAudioCapabilitiesBuilder pac_builder;
            pac_builder.Add(LeAudioCodecIdLc3, sampling_frequency,
                            frame_duration,
                            kLeAudioCodecChannelCountSingleChannel |
                                kLeAudioCodecChannelCountTwoChannel,
                            octets_per_frame);
            for (auto& device : devices_) {
              /* For simplicity configure both PACs with the same
              parameters*/
              device->snk_pacs_ = pac_builder.Get();
              device->src_pacs_ = pac_builder.Get();
            }

            bool success_expected = is_lc3_setting_supported;
            if (is_lc3_setting_supported &&
                (sf_variant == Lc3SettingId::UNSUPPORTED ||
                 fd_variant == Lc3SettingId::UNSUPPORTED ||
                 opcf_variant == Lc3SettingId::UNSUPPORTED)) {
              success_expected = false;
            }

            /* Stimulate update of available context map */
            group_->UpdateAudioContextAvailability();
            group_->UpdateAudioSetConfigurationCache(context_type);
            BidirectionalPair<AudioContexts> group_audio_locations = {
                .sink = AudioContexts(context_type),
                .source = AudioContexts(context_type)};
            ASSERT_EQ(success_expected,
                      group_->Configure(context_type, group_audio_locations));
            if (success_expected) {
              TestAsesActive(LeAudioCodecIdLc3, sampling_frequency,
                             frame_duration, octets_per_frame);
              group_->Deactivate();
            }

            TestAsesInactive();
          }
        }
      }
    }
  }

  void SetAsesToCachedConfiguration(LeAudioDevice* device,
                                    LeAudioContextType context_type,
                                    uint8_t directions) {
    for (struct ase& ase : device->ases_) {
      if (ase.direction & directions) {
        ase.state = AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;
        ase.active = false;
        ase.configured_for_context_type = context_type;
      }
    }
  }

  const int group_id_ = 6;
  std::vector<std::shared_ptr<LeAudioDevice>> devices_;
  std::vector<RawAddress> addresses_;
  LeAudioDeviceGroup* group_ = nullptr;
  bluetooth::manager::MockBtmInterface btm_interface_;
  controller::MockControllerInterface controller_interface_;
  MockCsisClient mock_csis_client_module_;
};

/* Helper */
const AudioSetConfiguration* getSpecificConfiguration(
    const char* config_name, LeAudioContextType context) {
  auto all_configurations =
      ::le_audio::AudioSetConfigurationProvider::Get()->GetConfigurations(
          context);

  if (all_configurations == nullptr) return nullptr;
  if (all_configurations->end() == all_configurations->begin()) return nullptr;

  auto iter =
      std::find_if(all_configurations->begin(), all_configurations->end(),
                   [config_name](auto& configuration) {
                     return configuration->name == config_name;
                   });
  if (iter == all_configurations->end()) return nullptr;
  return *iter;
}

TEST_F(LeAudioAseConfigurationTest, test_context_update) {
  LeAudioDevice* left = AddTestDevice(1, 1);
  LeAudioDevice* right = AddTestDevice(1, 1);
  ASSERT_EQ(2, group_->Size());

  /* Change locations */
  left->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  left->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  right->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  right->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  group_->ReloadAudioLocations();

  /* Put the PACS */
  auto conversational_configuration = getSpecificConfiguration(
      "SingleDev_OneChanStereoSnk_OneChanMonoSrc_16_2_Low_Latency",
      LeAudioContextType::CONVERSATIONAL);
  auto media_configuration = getSpecificConfiguration(
      "SingleDev_TwoChanStereoSnk_48_4_High_Reliability",
      LeAudioContextType::MEDIA);
  ASSERT_NE(nullptr, conversational_configuration);
  ASSERT_NE(nullptr, media_configuration);

  /* Create PACs for conversational and media scenarios */
  PublishedAudioCapabilitiesBuilder snk_pac_builder, src_pac_builder;
  for (const auto& entry : (*conversational_configuration).confs) {
    if (entry.direction == kLeAudioDirectionSink) {
      snk_pac_builder.Add(entry.codec, 1);
    } else {
      src_pac_builder.Add(entry.codec, 1);
    }
  }
  for (const auto& entry : (*media_configuration).confs) {
    if (entry.direction == kLeAudioDirectionSink) {
      snk_pac_builder.Add(entry.codec, 2);
    }
  }
  left->snk_pacs_ = snk_pac_builder.Get();
  left->src_pacs_ = src_pac_builder.Get();
  right->snk_pacs_ = snk_pac_builder.Get();
  right->src_pacs_ = src_pac_builder.Get();

  /* UNSPECIFIED must be supported, MEDIA is on the remote sink only... */
  auto remote_snk_supp_contexts = AudioContexts(
      LeAudioContextType::MEDIA | LeAudioContextType::CONVERSATIONAL |
      LeAudioContextType::SOUNDEFFECTS | LeAudioContextType::UNSPECIFIED);
  auto remote_src_supp_contexts = AudioContexts(
      LeAudioContextType::CONVERSATIONAL | LeAudioContextType::UNSPECIFIED);

  left->SetSupportedContexts(
      {.sink = remote_snk_supp_contexts, .source = remote_src_supp_contexts});

  auto right_bud_only_context = LeAudioContextType::ALERTS;
  right->SetSupportedContexts(
      {.sink = remote_snk_supp_contexts | right_bud_only_context,
       .source = remote_src_supp_contexts | right_bud_only_context});

  /* ...but UNSPECIFIED and SOUNDEFFECTS are unavailable */
  auto remote_snk_avail_contexts = AudioContexts(
      LeAudioContextType::MEDIA | LeAudioContextType::CONVERSATIONAL);
  auto remote_src_avail_contexts =
      AudioContexts(LeAudioContextType::CONVERSATIONAL);

  left->SetAvailableContexts(
      {.sink = remote_snk_avail_contexts, .source = remote_src_avail_contexts});
  ASSERT_EQ(left->GetAvailableContexts(),
            remote_snk_avail_contexts | remote_src_avail_contexts);

  // Make an additional context available on the right earbud sink
  right->SetAvailableContexts(
      {.sink = remote_snk_avail_contexts | right_bud_only_context,
       .source = remote_src_avail_contexts});
  ASSERT_EQ(right->GetAvailableContexts(), remote_snk_avail_contexts |
                                               remote_src_avail_contexts |
                                               right_bud_only_context);

  /* Now add the right earbud contexts - mind the extra context on that bud */
  group_->UpdateAudioContextAvailability();
  ASSERT_NE(group_->GetAvailableContexts(), left->GetAvailableContexts());
  ASSERT_EQ(group_->GetAvailableContexts(),
            left->GetAvailableContexts() | right->GetAvailableContexts());

  /* Since no device is being added or removed from the group this should not
   * change the configuration set.
   */
  group_->UpdateAudioContextAvailability();
  ASSERT_EQ(group_->GetAvailableContexts(),
            left->GetAvailableContexts() | right->GetAvailableContexts());

  /* MEDIA Available on remote sink direction only */
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::MEDIA,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_FALSE(group_
                   ->GetCodecConfigurationByDirection(
                       LeAudioContextType::MEDIA,
                       ::le_audio::types::kLeAudioDirectionSource)
                   .has_value());

  /* CONVERSATIONAL Available on both directions */
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::CONVERSATIONAL,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::CONVERSATIONAL,
                      ::le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  /* UNSPECIFIED Unavailable yet supported */
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::UNSPECIFIED,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_FALSE(group_
                   ->GetCodecConfigurationByDirection(
                       LeAudioContextType::UNSPECIFIED,
                       ::le_audio::types::kLeAudioDirectionSource)
                   .has_value());

  /* SOUNDEFFECTS Unavailable yet supported on sink only */
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::SOUNDEFFECTS,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_FALSE(group_
                   ->GetCodecConfigurationByDirection(
                       LeAudioContextType::SOUNDEFFECTS,
                       ::le_audio::types::kLeAudioDirectionSource)
                   .has_value());

  /* INSTRUCTIONAL Unavailable and not supported but scenario is supported */
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::INSTRUCTIONAL,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_FALSE(group_
                   ->GetCodecConfigurationByDirection(
                       LeAudioContextType::INSTRUCTIONAL,
                       ::le_audio::types::kLeAudioDirectionSource)
                   .has_value());

  /* ALERTS on sink only */
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::ALERTS,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_FALSE(group_
                   ->GetCodecConfigurationByDirection(
                       LeAudioContextType::ALERTS,
                       ::le_audio::types::kLeAudioDirectionSource)
                   .has_value());

  /* We should get the config for ALERTS for a single channel as only one earbud
   * has it. */
  auto config = group_->GetCodecConfigurationByDirection(
      LeAudioContextType::ALERTS, ::le_audio::types::kLeAudioDirectionSink);
  ASSERT_TRUE(config.has_value());
  ASSERT_EQ(config->num_channels,
            ::le_audio::LeAudioCodecConfiguration::kChannelNumberMono);
  ASSERT_TRUE(
      group_->IsAudioSetConfigurationAvailable(LeAudioContextType::ALERTS));

  /* Turn off the ALERTS context */
  right->SetAvailableContexts(
      {.sink = right->GetAvailableContexts(
                   ::le_audio::types::kLeAudioDirectionSink) &
               ~AudioContexts(LeAudioContextType::ALERTS),
       .source = right->GetAvailableContexts(
           ::le_audio::types::kLeAudioDirectionSource)});

  /* Right one was changed but the config exist, just not available */
  group_->UpdateAudioContextAvailability();
  ASSERT_EQ(group_->GetAvailableContexts(),
            left->GetAvailableContexts() | right->GetAvailableContexts());
  ASSERT_FALSE(group_->GetAvailableContexts().test(LeAudioContextType::ALERTS));
  ASSERT_TRUE(group_
                  ->GetCodecConfigurationByDirection(
                      LeAudioContextType::ALERTS,
                      ::le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(
      group_->IsAudioSetConfigurationAvailable(LeAudioContextType::ALERTS));
}

TEST_F(LeAudioAseConfigurationTest, test_mono_speaker_ringtone) {
  LeAudioDevice* mono_speaker = AddTestDevice(1, 0);
  TestGroupAseConfigurationData data(
      {mono_speaker, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 0});

  /* mono, change location as by default it is stereo */
  mono_speaker->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  uint8_t direction_to_verify = kLeAudioDirectionSink;

  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, &data, 1,
                            direction_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_mono_speaker_conversational) {
  LeAudioDevice* mono_speaker = AddTestDevice(1, 0);
  TestGroupAseConfigurationData data({mono_speaker,
                                      kLeAudioCodecChannelCountSingleChannel,
                                      kLeAudioCodecChannelCountNone, 1, 0});

  /* mono, change location as by default it is stereo */
  mono_speaker->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  /* Microphone should be used on the phone */
  uint8_t direction_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1,
                            direction_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_mono_speaker_media) {
  LeAudioDevice* mono_speaker = AddTestDevice(1, 0);
  TestGroupAseConfigurationData data({mono_speaker,
                                      kLeAudioCodecChannelCountSingleChannel,
                                      kLeAudioCodecChannelCountNone, 1, 0});

  /* mono, change location as by default it is stereo */
  mono_speaker->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  uint8_t direction_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::MEDIA, &data, 1,
                            direction_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_bounded_headphones_ringtone) {
  LeAudioDevice* bounded_headphones = AddTestDevice(2, 0);
  TestGroupAseConfigurationData data(
      {bounded_headphones, kLeAudioCodecChannelCountTwoChannel,
       kLeAudioCodecChannelCountSingleChannel, 2, 0});

  uint8_t direction_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, &data, 1,
                            direction_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_bounded_headphones_conversational) {
  LeAudioDevice* bounded_headphones = AddTestDevice(2, 0);
  TestGroupAseConfigurationData data({bounded_headphones,
                                      kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountNone, 2, 0});

  uint8_t direction_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1,
                            direction_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_bounded_headphones_media) {
  LeAudioDevice* bounded_headphones = AddTestDevice(2, 0);
  TestGroupAseConfigurationData data({bounded_headphones,
                                      kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountNone, 2, 0});

  uint8_t direction_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::MEDIA, &data, 1,
                            direction_to_verify);
}

TEST_F(LeAudioAseConfigurationTest,
       test_bounded_headset_ringtone_mono_microphone) {
  LeAudioDevice* bounded_headset = AddTestDevice(2, 1);
  TestGroupAseConfigurationData data(
      {bounded_headset, kLeAudioCodecChannelCountTwoChannel,
       kLeAudioCodecChannelCountSingleChannel, 2, 1});

  /* mono, change location as by default it is stereo */
  bounded_headset->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest,
       test_bounded_headset_ringtone_stereo_microphone) {
  LeAudioDevice* bounded_headset = AddTestDevice(2, 2);
  TestGroupAseConfigurationData data({bounded_headset,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      2, 2});

  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest, test_bounded_headset_conversational) {
  LeAudioDevice* bounded_headset = AddTestDevice(2, 1);
  TestGroupAseConfigurationData data(
      {bounded_headset, kLeAudioCodecChannelCountTwoChannel,
       kLeAudioCodecChannelCountSingleChannel, 2, 1});

  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest, test_bounded_headset_media) {
  LeAudioDevice* bounded_headset = AddTestDevice(2, 1);
  TestGroupAseConfigurationData data(
      {bounded_headset, kLeAudioCodecChannelCountTwoChannel,
       kLeAudioCodecChannelCountSingleChannel, 2, 0});

  uint8_t directions_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::MEDIA, &data, 1,
                            directions_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_earbuds_ringtone) {
  LeAudioDevice* left = AddTestDevice(1, 1);
  LeAudioDevice* right = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data[] = {
      {left, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 1},
      {right, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 1}};

  /* Change location as by default it is stereo */
  left->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  left->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  right->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  right->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  group_->ReloadAudioLocations();

  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, data, 2);
}

TEST_F(LeAudioAseConfigurationTest, test_earbuds_conversational) {
  LeAudioDevice* left = AddTestDevice(1, 1);
  LeAudioDevice* right = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data[] = {
      {left, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 1},
      {right, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 1}};

  /* Change location as by default it is stereo */
  left->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  left->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  right->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  right->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  group_->ReloadAudioLocations();

  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, data, 2);
}

TEST_F(LeAudioAseConfigurationTest, test_earbuds_media) {
  LeAudioDevice* left = AddTestDevice(1, 1);
  LeAudioDevice* right = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data[] = {
      {left, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 0},
      {right, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 0}};

  /* Change location as by default it is stereo */
  left->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  left->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  right->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  right->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  group_->ReloadAudioLocations();

  uint8_t directions_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::MEDIA, data, 2,
                            directions_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_handsfree_mono_ringtone) {
  LeAudioDevice* handsfree = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data(
      {handsfree, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 1});

  handsfree->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  handsfree->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest, test_handsfree_stereo_ringtone) {
  LeAudioDevice* handsfree = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data({handsfree,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountSingleChannel, 2,
                                      1});

  TestGroupAseConfiguration(LeAudioContextType::RINGTONE, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest, test_handsfree_mono_conversational) {
  LeAudioDevice* handsfree = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data(
      {handsfree, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 1});

  handsfree->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  handsfree->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest, test_handsfree_stereo_conversational) {
  LeAudioDevice* handsfree = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data({handsfree,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountSingleChannel, 2,
                                      1});

  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest, test_handsfree_full_cached_conversational) {
  LeAudioDevice* handsfree = AddTestDevice(0, 0, 1, 1);
  TestGroupAseConfigurationData data({handsfree,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountSingleChannel, 2,
                                      1});

  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest,
       test_handsfree_partial_cached_conversational) {
  LeAudioDevice* handsfree = AddTestDevice(1, 0, 0, 1);
  TestGroupAseConfigurationData data({handsfree,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountSingleChannel, 2,
                                      1});

  TestGroupAseConfiguration(LeAudioContextType::CONVERSATIONAL, &data, 1);
}

TEST_F(LeAudioAseConfigurationTest,
       test_handsfree_media_two_channels_allocation_stereo) {
  LeAudioDevice* handsfree = AddTestDevice(1, 1);
  TestGroupAseConfigurationData data({handsfree,
                                      kLeAudioCodecChannelCountSingleChannel |
                                          kLeAudioCodecChannelCountTwoChannel,
                                      kLeAudioCodecChannelCountSingleChannel, 2,
                                      0});

  uint8_t directions_to_verify = kLeAudioDirectionSink;
  TestGroupAseConfiguration(LeAudioContextType::MEDIA, &data, 1,
                            directions_to_verify);
}

TEST_F(LeAudioAseConfigurationTest, test_lc3_config_ringtone) {
  AddTestDevice(1, 1);

  TestLc3CodecConfig(LeAudioContextType::RINGTONE);
}

TEST_F(LeAudioAseConfigurationTest, test_lc3_config_conversational) {
  AddTestDevice(1, 1);

  TestLc3CodecConfig(LeAudioContextType::CONVERSATIONAL);
}

TEST_F(LeAudioAseConfigurationTest, test_lc3_config_media) {
  AddTestDevice(1, 1);

  TestLc3CodecConfig(LeAudioContextType::MEDIA);
}

TEST_F(LeAudioAseConfigurationTest, test_unsupported_codec) {
  const LeAudioCodecId UnsupportedCodecId = {
      .coding_format = kLeAudioCodingFormatVendorSpecific,
      .vendor_company_id = 0xBAD,
      .vendor_codec_id = 0xC0DE,
  };

  LeAudioDevice* device = AddTestDevice(1, 0);

  PublishedAudioCapabilitiesBuilder pac_builder;
  pac_builder.Add(UnsupportedCodecId,
                  GetSamplingFrequency(Lc3SettingId::LC3_16_2),
                  GetFrameDuration(Lc3SettingId::LC3_16_2),
                  kLeAudioCodecChannelCountSingleChannel,
                  GetOctetsPerCodecFrame(Lc3SettingId::LC3_16_2));
  device->snk_pacs_ = pac_builder.Get();
  device->src_pacs_ = pac_builder.Get();

  ASSERT_FALSE(
      group_->Configure(LeAudioContextType::RINGTONE,
                        {AudioContexts(LeAudioContextType::RINGTONE),
                         AudioContexts(LeAudioContextType::RINGTONE)}));
  TestAsesInactive();
}

TEST_F(LeAudioAseConfigurationTest, test_reconnection_media) {
  LeAudioDevice* left = AddTestDevice(2, 1);
  LeAudioDevice* right = AddTestDevice(2, 1);

  /* Change location as by default it is stereo */
  left->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  left->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  right->snk_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  right->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
  group_->ReloadAudioLocations();

  TestGroupAseConfigurationData data[] = {
      {left, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 0},
      {right, kLeAudioCodecChannelCountSingleChannel,
       kLeAudioCodecChannelCountSingleChannel, 1, 0}};

  auto all_configurations =
      ::le_audio::AudioSetConfigurationProvider::Get()->GetConfigurations(
          LeAudioContextType::MEDIA);
  ASSERT_NE(nullptr, all_configurations);
  ASSERT_NE(all_configurations->end(), all_configurations->begin());
  auto configuration = *all_configurations->begin();

  uint8_t direction_to_verify = kLeAudioDirectionSink;
  TestSingleAseConfiguration(LeAudioContextType::MEDIA, data, 2, configuration,
                             direction_to_verify);

  /* Generate CISes, symulate CIG creation and assign cis handles to ASEs.*/
  group_->cig.GenerateCisIds(LeAudioContextType::MEDIA);
  std::vector<uint16_t> handles = {0x0012, 0x0013};
  group_->cig.AssignCisConnHandles(handles);
  group_->cig.AssignCisIds(left);
  group_->cig.AssignCisIds(right);

  TestActiveAses();
  /* Left got disconnected */
  left->DeactivateAllAses();

  /* Unassign from the group*/
  group_->cig.UnassignCis(left);

  TestAsesInactivated(left);

  /* Prepare reconfiguration */
  uint8_t number_of_active_ases = 1;  // Right one
  auto* ase = right->GetFirstActiveAseByDirection(kLeAudioDirectionSink);

  auto core_config = ase->codec_config.GetAsCoreCodecConfig();
  BidirectionalPair<AudioLocations> group_audio_locations = {
      .sink = *core_config.audio_channel_allocation,
      .source = *core_config.audio_channel_allocation};

  /* Get entry for the sink direction and use it to set configuration */
  BidirectionalPair<std::vector<uint8_t>> ccid_lists = {{}, {}};
  BidirectionalPair<AudioContexts> audio_contexts = {AudioContexts(),
                                                     AudioContexts()};
  for (auto& ent : configuration->confs) {
    if (ent.direction == ::le_audio::types::kLeAudioDirectionSink) {
      left->ConfigureAses(ent, group_->GetConfigurationContextType(),
                          &number_of_active_ases, group_audio_locations,
                          audio_contexts, ccid_lists, false);
    }
  }

  ASSERT_TRUE(number_of_active_ases == 2);
  ASSERT_TRUE(group_audio_locations.sink == kChannelAllocationStereo);

  uint8_t directions_to_verify = ::le_audio::types::kLeAudioDirectionSink;
  for (int i = 0; i < 2; i++) {
    TestGroupAseConfigurationVerdict(data[i], directions_to_verify);
  }

  /* Before device is rejoining, and group already exist, cis handles are
   * assigned before sending codec config
   */
  group_->cig.AssignCisIds(left);
  group_->AssignCisConnHandlesToAses(left);

  TestActiveAses();
}

/*
 * Failure happens when restarting conversational scenario and when
 * remote device uses caching.
 *
 * Failing scenario.
 * 1. Conversational scenario set up with
 *  - ASE 1 and ASE 5 using bidirectional CIS 0
 *  - ASE 2  being unidirectional on CIS 1
 * 2. Stop stream and go to CONFIGURED STATE.
 * 3. Trying to configure ASES again would end up in incorrectly assigned
 *    CISes
 *  - ASE 1 and ASE 5 set to CIS 0
 *  - ASE 2 stay on CIS 1 but ASE 5 got reassigned to CIS 1 (error)
 *
 * The problem is finding matching_bidir_ase which shall not be just next
 * active ase with different direction, but it shall be also available (Cis
 * not assigned) or assigned to the same CIS ID as the opposite direction.
 */
TEST_F(LeAudioAseConfigurationTest, test_reactivation_conversational) {
  LeAudioDevice* tws_headset = AddTestDevice(0, 0, 2, 1, true);

  /* Change location as by default it is stereo */
  tws_headset->snk_audio_locations_ = kChannelAllocationStereo;
  tws_headset->src_audio_locations_ =
      ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft;
  group_->ReloadAudioLocations();

  auto conversational_configuration = getSpecificConfiguration(
      "SingleDev_OneChanStereoSnk_OneChanMonoSrc_16_2_Low_Latency",
      LeAudioContextType::CONVERSATIONAL);
  ASSERT_NE(nullptr, conversational_configuration);

  // Build PACs for device
  PublishedAudioCapabilitiesBuilder snk_pac_builder, src_pac_builder;
  snk_pac_builder.Reset();
  src_pac_builder.Reset();

  /* Create PACs for conversational scenario which covers also media. Single
   * PAC for each direction is enough.
   */
  for (const auto& entry : (*conversational_configuration).confs) {
    if (entry.direction == kLeAudioDirectionSink) {
      snk_pac_builder.Add(entry.codec, 1);
    } else {
      src_pac_builder.Add(entry.codec, 1);
    }
  }

  tws_headset->snk_pacs_ = snk_pac_builder.Get();
  tws_headset->src_pacs_ = src_pac_builder.Get();

  ::le_audio::types::AudioLocations group_snk_audio_locations = 0;
  ::le_audio::types::AudioLocations group_src_audio_locations = 0;
  uint8_t number_of_already_active_ases = 0;

  BidirectionalPair<AudioLocations> group_audio_locations = {
      .sink = group_snk_audio_locations, .source = group_src_audio_locations};

  /* Get entry for the sink direction and use it to set configuration */
  BidirectionalPair<std::vector<uint8_t>> ccid_lists = {{}, {}};
  BidirectionalPair<AudioContexts> audio_contexts = {AudioContexts(),
                                                     AudioContexts()};

  /* Get entry for the sink direction and use it to set configuration */
  for (auto& ent : conversational_configuration->confs) {
    tws_headset->ConfigureAses(ent, group_->GetConfigurationContextType(),
                               &number_of_already_active_ases,
                               group_audio_locations, audio_contexts,
                               ccid_lists, false);
  }

  /* Generate CISes, simulate CIG creation and assign cis handles to ASEs.*/
  std::vector<uint16_t> handles = {0x0012, 0x0013};
  group_->cig.GenerateCisIds(LeAudioContextType::CONVERSATIONAL);
  group_->cig.AssignCisConnHandles(handles);
  group_->cig.AssignCisIds(tws_headset);

  TestActiveAses();

  /* Simulate stopping stream with caching codec configuration in ASEs */
  group_->cig.UnassignCis(tws_headset);
  SetAsesToCachedConfiguration(tws_headset, LeAudioContextType::CONVERSATIONAL,
                               kLeAudioDirectionSink | kLeAudioDirectionSource);

  /* As context type is the same as previous and no changes were made in PACs
   * the same CIS ID can be used. This would lead to only activating group
   * without reconfiguring CIG.
   */
  group_->Activate(LeAudioContextType::CONVERSATIONAL, audio_contexts,
                   ccid_lists);

  TestActiveAses();

  /* Verify ASEs assigned CISes by counting assigned to bi-directional CISes */
  int bi_dir_ases_count = std::count_if(
      tws_headset->ases_.begin(), tws_headset->ases_.end(), [=](auto& ase) {
        return this->group_->cig.cises[ase.cis_id].type ==
               CisType::CIS_TYPE_BIDIRECTIONAL;
      });

  /* Only two ASEs can be bonded to one bi-directional CIS */
  ASSERT_EQ(bi_dir_ases_count, 2);
}

TEST_F(LeAudioAseConfigurationTest, test_num_of_connected) {
  auto device1 = AddTestDevice(2, 1);
  auto device2 = AddTestDevice(2, 1);
  ASSERT_EQ(2, group_->NumOfConnected());

  // Drop the ACL connection
  device1->conn_id_ = GATT_INVALID_CONN_ID;
  ASSERT_EQ(1, group_->NumOfConnected());

  // Fully disconnect the other device
  device2->SetConnectionState(DeviceConnectState::DISCONNECTING);
  ASSERT_EQ(0, group_->NumOfConnected());
}

/*
 * Failure happens when there is no matching single device scenario for dual
 * device scanario. Stereo location for single earbud seems to be invalid but
 * possible and stack should handle it.
 *
 * Failing scenario:
 * 1. Connect two - stereo location earbuds
 * 2. Disconnect one of earbud
 * 3. CIS generator will look for dual device scenario with matching strategy
 * 4. There is no dual device scenario with strategy stereo channels per device
 */
TEST_F(LeAudioAseConfigurationTest, test_getting_cis_count) {
  LeAudioDevice* left = AddTestDevice(2, 1);
  LeAudioDevice* right = AddTestDevice(0, 0, 0, 0, false, true);

  /* Change location as by default it is stereo */
  left->snk_audio_locations_ = kChannelAllocationStereo;
  right->snk_audio_locations_ = kChannelAllocationStereo;
  group_->ReloadAudioLocations();

  auto media_configuration = getSpecificConfiguration(
      "SingleDev_TwoChanStereoSnk_48_4_High_Reliability",
      LeAudioContextType::MEDIA);
  ASSERT_NE(nullptr, media_configuration);

  // Build PACs for device
  PublishedAudioCapabilitiesBuilder snk_pac_builder;
  snk_pac_builder.Reset();

  /* Create PACs for media. Single PAC for each direction is enough.
   */
  for (const auto& entry : (*media_configuration).confs) {
    if (entry.direction == kLeAudioDirectionSink) {
      snk_pac_builder.Add(LeAudioCodecIdLc3, 0x00b5, 0x03, 0x03, 0x001a, 0x00f0,
                          2);
    }
  }

  left->snk_pacs_ = snk_pac_builder.Get();
  left->snk_pacs_ = snk_pac_builder.Get();

  ::le_audio::types::AudioLocations group_snk_audio_locations = 3;
  ::le_audio::types::AudioLocations group_src_audio_locations = 0;
  uint8_t number_of_already_active_ases = 0;

  BidirectionalPair<AudioLocations> group_audio_locations = {
      .sink = group_snk_audio_locations, .source = group_src_audio_locations};

  /* Get entry for the sink direction and use it to set configuration */
  BidirectionalPair<std::vector<uint8_t>> ccid_lists = {{}, {}};
  BidirectionalPair<AudioContexts> audio_contexts = {AudioContexts(),
                                                     AudioContexts()};

  /* Get entry for the sink direction and use it to set configuration */
  for (auto& ent : media_configuration->confs) {
    left->ConfigureAses(ent, group_->GetConfigurationContextType(),
                        &number_of_already_active_ases, group_audio_locations,
                        audio_contexts, ccid_lists, false);
  }

  /* Generate CIS, simulate CIG creation and assign cis handles to ASEs.*/
  std::vector<uint16_t> handles = {0x0012};
  group_->cig.GenerateCisIds(LeAudioContextType::MEDIA);

  /* Verify prepared CISes by counting generated entries */
  int snk_cis_count =
      std::count_if(this->group_->cig.cises.begin(),
                    this->group_->cig.cises.end(), [](auto& cis) {
                      return cis.type == CisType::CIS_TYPE_UNIDIRECTIONAL_SINK;
                    });

  /* Two CIS should be prepared for dual dev expected set */
  ASSERT_EQ(snk_cis_count, 2);
}

}  // namespace
}  // namespace internal
}  // namespace le_audio
}  // namespace bluetooth
