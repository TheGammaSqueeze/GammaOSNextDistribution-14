/*
 * Copyright 2024 The Android Open Source Project
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

#define LOG_TAG "TestAIDLA2dpProviderInfo"

#include "a2dp_provider_info.h"

#include <com_android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "a2dp_constants.h"
#include "a2dp_vendor.h"
#include "a2dp_vendor_opus_constants.h"
#include "avdt_api.h"
#include "client_interface_aidl.h"

#define TEST_BT com::android::bluetooth::flags

using aidl::android::hardware::bluetooth::audio::ChannelMode;
using aidl::android::hardware::bluetooth::audio::CodecId;
using aidl::android::hardware::bluetooth::audio::CodecInfo;
using bluetooth::audio::aidl::BluetoothAudioClientInterface;
using bluetooth::audio::aidl::IBluetoothAudioProviderFactory;
using bluetooth::audio::aidl::SessionType;
using bluetooth::audio::aidl::a2dp::ProviderInfo;
using ::testing::_;
using ::testing::Return;
using ::testing::Test;

tA2DP_CODEC_TYPE A2DP_GetCodecType(const uint8_t* p_codec_info) {
  return (tA2DP_CODEC_TYPE)(p_codec_info[AVDT_CODEC_TYPE_INDEX]);
}

uint16_t A2DP_VendorCodecGetCodecId(const uint8_t* p_codec_info) {
  const uint8_t* p = &p_codec_info[A2DP_VENDOR_CODEC_CODEC_ID_START_IDX];

  uint16_t codec_id = (p[0] & 0x00ff) | ((p[1] << 8) & 0xff00);

  return codec_id;
}

uint32_t A2DP_VendorCodecGetVendorId(const uint8_t* p_codec_info) {
  const uint8_t* p = &p_codec_info[A2DP_VENDOR_CODEC_VENDOR_ID_START_IDX];

  uint32_t vendor_id = (p[0] & 0x000000ff) | ((p[1] << 8) & 0x0000ff00) |
                       ((p[2] << 16) & 0x00ff0000) |
                       ((p[3] << 24) & 0xff000000);

  return vendor_id;
}

class MockBluetoothAudioClientInterface {
 public:
  MOCK_METHOD(
      std::optional<IBluetoothAudioProviderFactory::ProviderInfo>,
      GetProviderInfo,
      (SessionType session_type,
       std::shared_ptr<IBluetoothAudioProviderFactory> provider_factory));
};

static MockBluetoothAudioClientInterface* mock_bt_audio_client_itf = nullptr;

namespace bluetooth {
namespace audio {
namespace aidl {

std::optional<IBluetoothAudioProviderFactory::ProviderInfo>
BluetoothAudioClientInterface::GetProviderInfo(
    SessionType session_type,
    std::shared_ptr<IBluetoothAudioProviderFactory> provider_factory) {
  return mock_bt_audio_client_itf->GetProviderInfo(session_type,
                                                   provider_factory);
}

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth

namespace {

std::vector<uint8_t> test_sbc_codec_info = {0x06, 0x00, 0x00, 0x3f,
                                            0xff, 0x02, 0x25};
std::vector<uint8_t> test_aac_codec_info = {0x08, 0x00, 0x02, 0x80, 0x01,
                                            0x8c, 0x83, 0xe8, 0x00};
std::vector<uint8_t> test_opus_codec_info = {0x09, 0x00, 0xff, 0xe0, 0x00,
                                             0x00, 0x00, 0x01, 0x00, 0x3c};
std::vector<uint8_t> test_foobar_codec_info = {0x09, 0x00, 0xff, 0x44, 0x33,
                                               0x00, 0x00, 0x22, 0x11, 0x3c};

CodecId::Vendor test_opus_codec_id = {.id = A2DP_OPUS_VENDOR_ID,
                                      .codecId = A2DP_OPUS_CODEC_ID};
CodecId::Vendor test_foobar_codec_id = {.id = 0x00003344, .codecId = 0x1122};
CodecId::Vendor test_unknown_vendor_codec_id = {.id = 0x12345678,
                                                .codecId = 0x1234};

IBluetoothAudioProviderFactory::ProviderInfo test_source_provider_info = {
    .name = "TEST_PROVIDER_SOURCE_CODECS",
};

IBluetoothAudioProviderFactory::ProviderInfo test_sink_provider_info = {
    .name = "TEST_PROVIDER_SINK_CODECS",
};

class ProviderInfoTest : public Test {
 public:
  std::unique_ptr<ProviderInfo> provider_info;
  MockBluetoothAudioClientInterface client_itf_mock;

  void CreateTestA2dpCodecInfo(CodecInfo& codecInfo, CodecId codecId,
                               std::string codecName,
                               std::vector<uint8_t> capabilities,
                               std::vector<ChannelMode> channelMode,
                               std::vector<int32_t> samplingFrequencyHz,
                               std::vector<int32_t> bitdepth, bool lossless) {
    codecInfo.id = codecId;
    codecInfo.name = codecName;
    codecInfo.transport.set<CodecInfo::Transport::Tag::a2dp>();
    auto& a2dpInfo = codecInfo.transport.get<CodecInfo::Transport::Tag::a2dp>();
    a2dpInfo.capabilities.resize(capabilities.size());
    a2dpInfo.capabilities = capabilities;
    a2dpInfo.channelMode = channelMode;
    a2dpInfo.samplingFrequencyHz = samplingFrequencyHz;
    a2dpInfo.bitdepth = bitdepth;
    a2dpInfo.lossless = lossless;
  }

  void GetProviderInfoForTesting(bool include_source_codecs,
                                 bool include_sink_codecs) {
    if (include_source_codecs) {
      EXPECT_CALL(client_itf_mock,
                  GetProviderInfo(
                      SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH, _))
          .WillOnce(Return(std::make_optional(test_source_provider_info)));
    } else {
      EXPECT_CALL(client_itf_mock,
                  GetProviderInfo(
                      SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH, _));
    }

    if (include_sink_codecs) {
      EXPECT_CALL(client_itf_mock,
                  GetProviderInfo(
                      SessionType::A2DP_HARDWARE_OFFLOAD_DECODING_DATAPATH, _))
          .WillOnce(Return(std::make_optional(test_sink_provider_info)));
    } else {
      EXPECT_CALL(client_itf_mock,
                  GetProviderInfo(
                      SessionType::A2DP_HARDWARE_OFFLOAD_DECODING_DATAPATH, _));
    }

    provider_info = ProviderInfo::GetProviderInfo(true);
    if (include_source_codecs || include_sink_codecs) {
      ASSERT_NE(provider_info, nullptr);
    } else {
      ASSERT_EQ(provider_info, nullptr);
    }
  }

 protected:
  void SetUp() override {
    mock_bt_audio_client_itf = &client_itf_mock;

    auto& codec_info_sbc = test_source_provider_info.codecInfos.emplace_back();
    CreateTestA2dpCodecInfo(
        codec_info_sbc, CodecId::A2dp::SBC, std::string("SBC"),
        std::vector<uint8_t>{0x3f, 0xff, 0x02, 0x25}, /* capabilities */
        std::vector<ChannelMode>{ChannelMode::MONO, ChannelMode::STEREO,
                                 ChannelMode::DUALMONO}, /* channelMode */
        std::vector<int32_t>{44100, 48000}, /* samplingFrequencyHz */
        std::vector<int32_t>{16, 24, 32},   /* bitdepth */
        false                               /* lossless */
    );

    auto& codec_info_aac = test_source_provider_info.codecInfos.emplace_back();
    CreateTestA2dpCodecInfo(
        codec_info_aac, CodecId::A2dp::AAC, std::string("AAC"),
        std::vector<uint8_t>{0x80, 0x01, 0x8c, 0x83, 0xe8,
                             0x00}, /* capabilities */
        std::vector<ChannelMode>{ChannelMode::MONO, ChannelMode::STEREO,
                                 ChannelMode::DUALMONO}, /* channelMode */
        std::vector<int32_t>{44100, 48000}, /* samplingFrequencyHz */
        std::vector<int32_t>{16, 24, 32},   /* bitdepth */
        false                               /* lossless */
    );

    auto& codec_info_opus = test_source_provider_info.codecInfos.emplace_back();
    CreateTestA2dpCodecInfo(
        codec_info_opus, test_opus_codec_id, std::string("Opus"),
        std::vector<uint8_t>{0x3c}, /* capabilities */
        std::vector<ChannelMode>{ChannelMode::MONO, ChannelMode::STEREO,
                                 ChannelMode::DUALMONO}, /* channelMode */
        std::vector<int32_t>{44100, 48000}, /* samplingFrequencyHz */
        std::vector<int32_t>{16, 24, 32},   /* bitdepth */
        false                               /* lossless */
    );

    auto& codec_info_foobar =
        test_source_provider_info.codecInfos.emplace_back();
    CreateTestA2dpCodecInfo(
        codec_info_foobar, test_foobar_codec_id, std::string("FooBar"),
        std::vector<uint8_t>{0x3c}, /* capabilities */
        std::vector<ChannelMode>{ChannelMode::MONO, ChannelMode::STEREO,
                                 ChannelMode::DUALMONO}, /* channelMode */
        std::vector<int32_t>{44100, 48000}, /* samplingFrequencyHz */
        std::vector<int32_t>{16, 24, 32},   /* bitdepth */
        false                               /* lossless */
    );

    test_sink_provider_info.codecInfos = test_source_provider_info.codecInfos;
  }

  void TearDown() override {
    test_source_provider_info.codecInfos.clear();
    test_sink_provider_info.codecInfos.clear();
  }
};
}  // namespace

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetProviderInfoFlagDisabled,
                  REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  EXPECT_CALL(
      client_itf_mock,
      GetProviderInfo(SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH, _))
      .Times(0);
  EXPECT_CALL(
      client_itf_mock,
      GetProviderInfo(SessionType::A2DP_HARDWARE_OFFLOAD_DECODING_DATAPATH, _))
      .Times(0);

  provider_info = ProviderInfo::GetProviderInfo(true);
  ASSERT_EQ(provider_info, nullptr);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetProviderInfoEmptyProviderInfo,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(false, false);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetProviderInfo,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetCodecSbc,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  auto received_codec_info_sbc =
      provider_info->GetCodec(BTAV_A2DP_CODEC_INDEX_SOURCE_SBC);
  ASSERT_TRUE(received_codec_info_sbc.has_value());
  auto codec_info = received_codec_info_sbc.value();
  LOG(ERROR) << codec_info->toString();
  LOG(ERROR) << test_source_provider_info.codecInfos[0].toString();
  ASSERT_EQ(*codec_info, test_source_provider_info.codecInfos[0]);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetCodecAac,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  auto received_codec_info_aac =
      provider_info->GetCodec(BTAV_A2DP_CODEC_INDEX_SOURCE_AAC);
  ASSERT_TRUE(received_codec_info_aac.has_value());
  auto codec_info = received_codec_info_aac.value();
  LOG(ERROR) << codec_info->toString();
  LOG(ERROR) << test_source_provider_info.codecInfos[1].toString();
  ASSERT_EQ(*codec_info, test_source_provider_info.codecInfos[1]);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetCodecOpus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  auto received_codec_info_opus =
      provider_info->GetCodec(BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS);
  ASSERT_TRUE(received_codec_info_opus.has_value());
  auto codec_info = received_codec_info_opus.value();
  LOG(ERROR) << codec_info->toString();
  LOG(ERROR) << test_source_provider_info.codecInfos[2].toString();
  ASSERT_EQ(*codec_info, test_source_provider_info.codecInfos[2]);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetCodecFoobar,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  auto received_codec_info_foobar =
      provider_info->GetCodec(BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN);
  ASSERT_TRUE(received_codec_info_foobar.has_value());
  auto codec_info = received_codec_info_foobar.value();
  LOG(ERROR) << codec_info->toString();
  LOG(ERROR) << test_source_provider_info.codecInfos[3].toString();
  ASSERT_EQ(*codec_info, test_source_provider_info.codecInfos[3]);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestGetCodecNotSupported,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  auto received_codec_info_not_supported_codec =
      provider_info->GetCodec(BTAV_A2DP_CODEC_INDEX_SINK_LDAC);
  ASSERT_FALSE(received_codec_info_not_supported_codec.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestSourceCodecIndexByCodecId,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;
  auto codecInfoArray = test_source_provider_info.codecInfos;

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[0].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_SBC);

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[1].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_AAC);

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[2].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS);

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[3].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN);

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_unknown_vendor_codec_id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestSourceCodecIndexByVendorAndCodecId,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;
  auto codecInfoArray = test_source_provider_info.codecInfos;

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[2].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS);

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[3].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN);

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_unknown_vendor_codec_id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestSourceCodecIndexByCapabilities,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_sbc_codec_info.data());
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_SBC);

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_aac_codec_info.data());
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_AAC);

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_opus_codec_info.data());
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS);

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_foobar_codec_info.data());
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN);

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(
      std::vector<uint8_t>({0xde, 0xad, 0xbe, 0xef}).data());
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest,
                  TestSourceCodecIndexByCodecIdAssertNoSources,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(false, true);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;
  auto codecInfoArray = test_source_provider_info.codecInfos;

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[0].id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[1].id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[2].id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(codecInfoArray[3].id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt =
      provider_info->SourceCodecIndex(test_unknown_vendor_codec_id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest,
                  TestSourceCodecIndexByVendorAndCodecIdAssertNoSources,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(false, true);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(
      0, static_cast<uint16_t>(CodecId::A2dp::SBC));
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(
      0, static_cast<uint16_t>(CodecId::A2dp::AAC));
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(
      test_opus_codec_id.id, test_opus_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(
      test_foobar_codec_id.id, test_foobar_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SourceCodecIndex(
      test_unknown_vendor_codec_id.id, test_unknown_vendor_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestSinkCodecIndexByCodecId,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(false, true);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;
  auto codecInfoArray = test_sink_provider_info.codecInfos;

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(codecInfoArray[0].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SINK_SBC);

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(codecInfoArray[1].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SINK_AAC);

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(codecInfoArray[2].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SINK_OPUS);

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(codecInfoArray[3].id);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SINK_EXT_MIN);

  a2dp_codec_index_opt =
      provider_info->SinkCodecIndex(test_unknown_vendor_codec_id);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestSinkCodecIndexByVendorAndCodecId,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(false, true);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      test_opus_codec_id.id, test_opus_codec_id.codecId);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SINK_OPUS);

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      test_foobar_codec_id.id, test_foobar_codec_id.codecId);
  ASSERT_TRUE(a2dp_codec_index_opt.has_value());
  ASSERT_EQ(a2dp_codec_index_opt.value(), BTAV_A2DP_CODEC_INDEX_SINK_EXT_MIN);

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      test_unknown_vendor_codec_id.id, test_unknown_vendor_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest,
                  TestSinkCodecIndexByVendorAndCodecIdAssertNoSinks,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  std::optional<btav_a2dp_codec_index_t> a2dp_codec_index_opt;
  auto codecInfoArray = test_sink_provider_info.codecInfos;

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      0, static_cast<uint16_t>(CodecId::A2dp::SBC));
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      0, static_cast<uint16_t>(CodecId::A2dp::AAC));
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      test_opus_codec_id.id, test_opus_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      test_foobar_codec_id.id, test_foobar_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());

  a2dp_codec_index_opt = provider_info->SinkCodecIndex(
      test_unknown_vendor_codec_id.id, test_unknown_vendor_codec_id.codecId);
  ASSERT_FALSE(a2dp_codec_index_opt.has_value());
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestCodecIndexStr,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  auto codecInfoArray = test_source_provider_info.codecInfos;

  ASSERT_EQ(provider_info->CodecIndexStr(BTAV_A2DP_CODEC_INDEX_SOURCE_SBC),
            codecInfoArray[0].name);

  ASSERT_EQ(provider_info->CodecIndexStr(BTAV_A2DP_CODEC_INDEX_SOURCE_AAC),
            codecInfoArray[1].name);

  ASSERT_EQ(provider_info->CodecIndexStr(BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS),
            codecInfoArray[2].name);

  ASSERT_EQ(provider_info->CodecIndexStr(BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN),
            codecInfoArray[3].name);

  ASSERT_EQ(provider_info->CodecIndexStr(static_cast<btav_a2dp_codec_index_t>(
                test_unknown_vendor_codec_id.id)),
            std::nullopt);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestSupportsCodec,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, true);

  for (int i = static_cast<int>(BTAV_A2DP_CODEC_INDEX_SOURCE_MIN);
       i <= static_cast<int>(BTAV_A2DP_CODEC_INDEX_MAX); i++) {
    switch (i) {
      case BTAV_A2DP_CODEC_INDEX_SOURCE_SBC:
      case BTAV_A2DP_CODEC_INDEX_SOURCE_AAC:
      case BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS:
      case BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN:
      case BTAV_A2DP_CODEC_INDEX_SINK_SBC:
      case BTAV_A2DP_CODEC_INDEX_SINK_AAC:
      case BTAV_A2DP_CODEC_INDEX_SINK_OPUS:
      case BTAV_A2DP_CODEC_INDEX_SINK_EXT_MIN:
        ASSERT_TRUE(provider_info->SupportsCodec(
            static_cast<btav_a2dp_codec_index_t>(i)));
        break;
      default:
        ASSERT_FALSE(provider_info->SupportsCodec(
            static_cast<btav_a2dp_codec_index_t>(i)));
        break;
    }
  }
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestBuildCodecCapabilitiesSbc,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  std::vector<uint8_t> sbc_caps = {0x3f, 0xff, 0x02, 0x25};
  uint8_t result_sbc_codec_info[7];

  ASSERT_TRUE(ProviderInfo::BuildCodecCapabilities(
      CodecId::A2dp(CodecId::A2dp::SBC), sbc_caps, result_sbc_codec_info));
  ASSERT_EQ(std::memcmp(result_sbc_codec_info, test_sbc_codec_info.data(),
                        test_sbc_codec_info.size()),
            0);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestBuildCodecCapabilitiesAac,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);
  std::vector<uint8_t> aac_caps = {0x80, 0x01, 0x8c, 0x83, 0xe8, 0x00};
  uint8_t result_aac_codec_info[9];

  ASSERT_TRUE(ProviderInfo::BuildCodecCapabilities(
      CodecId::A2dp(CodecId::A2dp::AAC), aac_caps, result_aac_codec_info));
  ASSERT_EQ(std::memcmp(result_aac_codec_info, test_aac_codec_info.data(),
                        test_aac_codec_info.size()),
            0);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestBuildCodecCapabilitiesOpus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  std::vector<uint8_t> opus_caps = {0x3c};
  uint8_t result_opus_codec_info[10];

  ASSERT_TRUE(ProviderInfo::BuildCodecCapabilities(
      test_opus_codec_id, opus_caps, result_opus_codec_info));
  ASSERT_EQ(std::memcmp(result_opus_codec_info, test_opus_codec_info.data(),
                        test_opus_codec_info.size()),
            0);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestBuildCodecCapabilitiesFoobar,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  std::vector<uint8_t> foobar_caps = {0x3c};
  uint8_t result_foobar_codec_info[10];

  ASSERT_TRUE(ProviderInfo::BuildCodecCapabilities(
      test_foobar_codec_id, foobar_caps, result_foobar_codec_info));
  ASSERT_EQ(std::memcmp(result_foobar_codec_info, test_foobar_codec_info.data(),
                        test_foobar_codec_info.size()),
            0);
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestBuildCodecCapabilitiesNotSupported,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  std::vector<uint8_t> foobar_caps = {0x3c};
  uint8_t result_foobar_codec_info[10];

  ASSERT_FALSE(ProviderInfo::BuildCodecCapabilities(
      CodecId::Core(CodecId::Core::CVSD), foobar_caps,
      result_foobar_codec_info));
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestCodecCapabilitiesSbc,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  uint8_t result_codec_info[20];
  btav_a2dp_codec_config_t result_codec_config;
  uint64_t result_codec_id;

  ASSERT_TRUE(provider_info->CodecCapabilities(
      BTAV_A2DP_CODEC_INDEX_SOURCE_SBC, &result_codec_id, result_codec_info,
      &result_codec_config));
  ASSERT_EQ(result_codec_id, A2DP_CODEC_ID_SBC);
  ASSERT_EQ(std::memcmp(result_codec_info, test_sbc_codec_info.data(),
                        test_sbc_codec_info.size()),
            0);
  ASSERT_TRUE(result_codec_config.channel_mode ==
              (BTAV_A2DP_CODEC_CHANNEL_MODE_MONO |
               BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO));
  ASSERT_TRUE(
      result_codec_config.sample_rate ==
      (BTAV_A2DP_CODEC_SAMPLE_RATE_44100 | BTAV_A2DP_CODEC_SAMPLE_RATE_48000));
  ASSERT_TRUE(result_codec_config.bits_per_sample ==
              (BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32));
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestCodecCapabilitiesAac,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  uint8_t result_codec_info[20];
  btav_a2dp_codec_config_t result_codec_config;
  uint64_t result_codec_id;

  ASSERT_TRUE(provider_info->CodecCapabilities(
      BTAV_A2DP_CODEC_INDEX_SOURCE_AAC, &result_codec_id, result_codec_info,
      &result_codec_config));
  ASSERT_EQ(result_codec_id, A2DP_CODEC_ID_AAC);
  ASSERT_EQ(std::memcmp(result_codec_info, test_aac_codec_info.data(),
                        test_aac_codec_info.size()),
            0);
  ASSERT_TRUE(result_codec_config.channel_mode ==
              (BTAV_A2DP_CODEC_CHANNEL_MODE_MONO |
               BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO));
  ASSERT_TRUE(
      result_codec_config.sample_rate ==
      (BTAV_A2DP_CODEC_SAMPLE_RATE_44100 | BTAV_A2DP_CODEC_SAMPLE_RATE_48000));
  ASSERT_TRUE(result_codec_config.bits_per_sample ==
              (BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32));
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestCodecCapabilitiesOpus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  uint8_t result_codec_info[20];
  btav_a2dp_codec_config_t result_codec_config;
  uint64_t result_codec_id;

  ASSERT_TRUE(provider_info->CodecCapabilities(
      BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS, &result_codec_id, result_codec_info,
      &result_codec_config));
  ASSERT_EQ(result_codec_id, A2DP_CODEC_ID_OPUS);
  ASSERT_EQ(std::memcmp(result_codec_info, test_opus_codec_info.data(),
                        test_opus_codec_info.size()),
            0);
  ASSERT_TRUE(result_codec_config.channel_mode ==
              (BTAV_A2DP_CODEC_CHANNEL_MODE_MONO |
               BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO));
  ASSERT_TRUE(
      result_codec_config.sample_rate ==
      (BTAV_A2DP_CODEC_SAMPLE_RATE_44100 | BTAV_A2DP_CODEC_SAMPLE_RATE_48000));
  ASSERT_TRUE(result_codec_config.bits_per_sample ==
              (BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32));
}

TEST_F_WITH_FLAGS(ProviderInfoTest, TestCodecCapabilitiesFoobar,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, a2dp_offload_codec_extensibility))) {
  GetProviderInfoForTesting(true, false);

  uint8_t result_codec_info[20];
  btav_a2dp_codec_config_t result_codec_config;
  uint64_t result_codec_id;

  ASSERT_TRUE(provider_info->CodecCapabilities(
      BTAV_A2DP_CODEC_INDEX_SOURCE_EXT_MIN, &result_codec_id, result_codec_info,
      &result_codec_config));
  ASSERT_EQ(result_codec_id, static_cast<uint64_t>(0x11223344ff));
  ASSERT_EQ(std::memcmp(result_codec_info, test_foobar_codec_info.data(),
                        test_foobar_codec_info.size()),
            0);
  ASSERT_TRUE(result_codec_config.channel_mode ==
              (BTAV_A2DP_CODEC_CHANNEL_MODE_MONO |
               BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO));
  ASSERT_TRUE(
      result_codec_config.sample_rate ==
      (BTAV_A2DP_CODEC_SAMPLE_RATE_44100 | BTAV_A2DP_CODEC_SAMPLE_RATE_48000));
  ASSERT_TRUE(result_codec_config.bits_per_sample ==
              (BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24 |
               BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32));
}