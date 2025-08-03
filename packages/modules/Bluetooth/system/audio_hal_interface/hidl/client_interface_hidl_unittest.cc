/*
 * Copyright 2019 The Android Open Source Project
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

#define LOG_TAG "bluetooth"

#include "client_interface_hidl.h"

#include <gtest/gtest.h>

#include <vector>

#include "codec_status_hidl.h"

namespace {

using ::android::hardware::bluetooth::audio::V2_0::AacObjectType;
using ::android::hardware::bluetooth::audio::V2_0::AacParameters;
using ::android::hardware::bluetooth::audio::V2_0::AacVariableBitRate;
using ::android::hardware::bluetooth::audio::V2_0::AptxParameters;
using ::android::hardware::bluetooth::audio::V2_0::CodecCapabilities;
using ::android::hardware::bluetooth::audio::V2_0::CodecType;
using ::android::hardware::bluetooth::audio::V2_0::LdacChannelMode;
using ::android::hardware::bluetooth::audio::V2_0::LdacParameters;
using ::android::hardware::bluetooth::audio::V2_0::LdacQualityIndex;
using ::android::hardware::bluetooth::audio::V2_0::SbcAllocMethod;
using ::android::hardware::bluetooth::audio::V2_0::SbcBlockLength;
using ::android::hardware::bluetooth::audio::V2_0::SbcChannelMode;
using ::android::hardware::bluetooth::audio::V2_0::SbcNumSubbands;
using ::android::hardware::bluetooth::audio::V2_0::SbcParameters;

using ::bluetooth::audio::hidl::AudioCapabilities;
using ::bluetooth::audio::hidl::AudioCapabilities_2_1;
using ::bluetooth::audio::hidl::AudioConfiguration;
using ::bluetooth::audio::hidl::AudioConfiguration_2_1;
using ::bluetooth::audio::hidl::BluetoothAudioClientInterface;
using ::bluetooth::audio::hidl::BluetoothAudioSinkClientInterface;
using ::bluetooth::audio::hidl::BluetoothAudioSourceClientInterface;
using ::bluetooth::audio::hidl::BluetoothAudioStatus;
using ::bluetooth::audio::hidl::PcmParameters;
using ::bluetooth::audio::hidl::PcmParameters_2_1;
using ::bluetooth::audio::hidl::SampleRate;
using ::bluetooth::audio::hidl::SampleRate_2_1;
using ::bluetooth::audio::hidl::SessionType;
using ::bluetooth::audio::hidl::SessionType_2_1;
using ::bluetooth::audio::hidl::codec::A2dpCodecToHalBitsPerSample;
using ::bluetooth::audio::hidl::codec::A2dpCodecToHalChannelMode;
using ::bluetooth::audio::hidl::codec::A2dpCodecToHalSampleRate;
using ::bluetooth::audio::hidl::codec::BitsPerSample;
using ::bluetooth::audio::hidl::codec::ChannelMode;
using ::bluetooth::audio::hidl::codec::CodecConfiguration;
using ::bluetooth::audio::hidl::codec::IsCodecOffloadingEnabled;
using ::bluetooth::audio::hidl::codec::UpdateOffloadingCapabilities;
using ::testing::Test;

struct SampleRatePair {
  SampleRate hal_sample_rate_;
  btav_a2dp_codec_sample_rate_t btav_sample_rate_;
};
constexpr SampleRatePair kSampleRatePairs[9] = {
    {.hal_sample_rate_ = SampleRate::RATE_UNKNOWN,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE},
    {.hal_sample_rate_ = SampleRate::RATE_44100,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_44100},
    {.hal_sample_rate_ = SampleRate::RATE_48000,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_48000},
    {.hal_sample_rate_ = SampleRate::RATE_88200,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_88200},
    {.hal_sample_rate_ = SampleRate::RATE_96000,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_96000},
    {.hal_sample_rate_ = SampleRate::RATE_176400,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_176400},
    {.hal_sample_rate_ = SampleRate::RATE_192000,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_192000},
    {.hal_sample_rate_ = SampleRate::RATE_16000,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_16000},
    {.hal_sample_rate_ = SampleRate::RATE_24000,
     .btav_sample_rate_ = BTAV_A2DP_CODEC_SAMPLE_RATE_24000}};

constexpr SampleRate_2_1 kSampleRates_2_1[] = {
    SampleRate_2_1::RATE_UNKNOWN, SampleRate_2_1::RATE_8000,
    SampleRate_2_1::RATE_16000,   SampleRate_2_1::RATE_24000,
    SampleRate_2_1::RATE_32000,   SampleRate_2_1::RATE_44100,
    SampleRate_2_1::RATE_48000};

constexpr uint32_t kDataIntervalUs[] = {0 /* Invalid */,
                                        10000 /* Valid 10ms */};

struct BitsPerSamplePair {
  BitsPerSample hal_bits_per_sample_;
  btav_a2dp_codec_bits_per_sample_t btav_bits_per_sample_;
};
constexpr BitsPerSamplePair kBitsPerSamplePairs[4] = {
    {.hal_bits_per_sample_ = BitsPerSample::BITS_UNKNOWN,
     .btav_bits_per_sample_ = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE},
    {.hal_bits_per_sample_ = BitsPerSample::BITS_16,
     .btav_bits_per_sample_ = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16},
    {.hal_bits_per_sample_ = BitsPerSample::BITS_24,
     .btav_bits_per_sample_ = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24},
    {.hal_bits_per_sample_ = BitsPerSample::BITS_32,
     .btav_bits_per_sample_ = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32}};

struct ChannelModePair {
  ChannelMode hal_channel_mode_;
  btav_a2dp_codec_channel_mode_t btav_channel_mode_;
};
constexpr ChannelModePair kChannelModePairs[3] = {
    {.hal_channel_mode_ = ChannelMode::UNKNOWN,
     .btav_channel_mode_ = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE},
    {.hal_channel_mode_ = ChannelMode::MONO,
     .btav_channel_mode_ = BTAV_A2DP_CODEC_CHANNEL_MODE_MONO},
    {.hal_channel_mode_ = ChannelMode::STEREO,
     .btav_channel_mode_ = BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO}};

constexpr btav_a2dp_codec_index_t codec_indexes[] = {
    BTAV_A2DP_CODEC_INDEX_SOURCE_SBC,  BTAV_A2DP_CODEC_INDEX_SOURCE_AAC,
    BTAV_A2DP_CODEC_INDEX_SOURCE_APTX, BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD,
    BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC, BTAV_A2DP_CODEC_INDEX_SINK_SBC,
    BTAV_A2DP_CODEC_INDEX_SINK_AAC,    BTAV_A2DP_CODEC_INDEX_SINK_LDAC};
constexpr uint16_t kPeerMtus[5] = {660, 663, 883, 1005, 1500};

class TestSinkTransport
    : public bluetooth::audio::hidl::IBluetoothSinkTransportInstance {
 private:
  static constexpr uint64_t kRemoteDelayReportMs = 200;

 public:
  TestSinkTransport(SessionType session_type)
      : bluetooth::audio::hidl::IBluetoothSinkTransportInstance(session_type,
                                                                {}){};
  TestSinkTransport(SessionType_2_1 session_type_2_1)
      : bluetooth::audio::hidl::IBluetoothSinkTransportInstance(
            session_type_2_1, (AudioConfiguration_2_1){}){};
  bluetooth::audio::hidl::BluetoothAudioCtrlAck StartRequest() override {
    return bluetooth::audio::hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED;
  }
  bluetooth::audio::hidl::BluetoothAudioCtrlAck SuspendRequest() override {
    return bluetooth::audio::hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED;
  }
  void StopRequest() override {}
  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_readed,
                               timespec* data_position) override {
    if (remote_delay_report_ns) {
      *remote_delay_report_ns = kRemoteDelayReportMs * 1000000;
    }
    if (total_bytes_readed) {
      *total_bytes_readed = 0;
    }
    if (data_position) {
      clock_gettime(CLOCK_MONOTONIC, data_position);
    }
    return true;
  }
  void MetadataChanged(
      const source_metadata_t& source_metadata __unused) override {}
  void ResetPresentationPosition() override{};
  void LogBytesRead(size_t bytes_readed __unused) override{};
};

class TestSourceTransport
    : public bluetooth::audio::hidl::IBluetoothSourceTransportInstance {
 private:
  static constexpr uint64_t kRemoteDelayReportMs = 200;

 public:
  TestSourceTransport(SessionType session_type)
      : bluetooth::audio::hidl::IBluetoothSourceTransportInstance(session_type,
                                                                  {}){};
  TestSourceTransport(SessionType_2_1 session_type_2_1)
      : bluetooth::audio::hidl::IBluetoothSourceTransportInstance(
            session_type_2_1, (AudioConfiguration_2_1){}){};
  bluetooth::audio::hidl::BluetoothAudioCtrlAck StartRequest() override {
    return bluetooth::audio::hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED;
  }
  bluetooth::audio::hidl::BluetoothAudioCtrlAck SuspendRequest() override {
    return bluetooth::audio::hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED;
  }
  void StopRequest() override {}
  bool GetPresentationPosition(uint64_t* remote_delay_report_ns,
                               uint64_t* total_bytes_written,
                               timespec* data_position) override {
    if (remote_delay_report_ns) {
      *remote_delay_report_ns = kRemoteDelayReportMs * 1000000;
    }
    if (total_bytes_written) {
      *total_bytes_written = 0;
    }
    if (data_position) {
      clock_gettime(CLOCK_MONOTONIC, data_position);
    }
    return true;
  }
  void MetadataChanged(
      const source_metadata_t& source_metadata __unused) override {}
  void ResetPresentationPosition() override{};
  void LogBytesWritten(size_t bytes_written __unused) override{};
};

class BluetoothAudioClientInterfaceTest : public Test {
 protected:
  TestSinkTransport* test_sink_transport_ = nullptr;
  TestSourceTransport* test_source_transport_ = nullptr;
  BluetoothAudioSinkClientInterface* clientif_sink_ = nullptr;
  BluetoothAudioSourceClientInterface* clientif_source_ = nullptr;

  static constexpr int kClientIfReturnSuccess = 0;

  void SetUp() override {}

  void TearDown() override {
    if (clientif_sink_ != nullptr) delete clientif_sink_;
    clientif_sink_ = nullptr;
    if (test_sink_transport_ != nullptr) delete test_sink_transport_;
    test_sink_transport_ = nullptr;

    if (clientif_source_ != nullptr) delete clientif_source_;
    clientif_source_ = nullptr;
    if (test_source_transport_ != nullptr) delete test_source_transport_;
    test_source_transport_ = nullptr;
  }

  bool IsSoftwarePcmParametersSupported(const PcmParameters& pcm_config) {
    const std::vector<AudioCapabilities>& capabilities =
        clientif_sink_->GetAudioCapabilities();
    PcmParameters pcm_capabilities = capabilities[0].pcmCapabilities();
    bool is_pcm_config_valid =
        (pcm_config.sampleRate != SampleRate::RATE_UNKNOWN &&
         pcm_config.bitsPerSample != BitsPerSample::BITS_UNKNOWN &&
         pcm_config.channelMode != ChannelMode::UNKNOWN);
    bool is_pcm_config_supported =
        (pcm_config.sampleRate & pcm_capabilities.sampleRate &&
         pcm_config.bitsPerSample & pcm_capabilities.bitsPerSample &&
         pcm_config.channelMode & pcm_capabilities.channelMode);
    return (is_pcm_config_valid && is_pcm_config_supported);
  }

  bool IsSinkSoftwarePcmParameters_2_1_Supported(
      const PcmParameters_2_1& pcm_config) {
    return IsSoftwarePcmParameters_2_1_Supported(pcm_config, clientif_sink_);
  }

  bool IsSourceSoftwarePcmParameters_2_1_Supported(
      const PcmParameters_2_1& pcm_config) {
    return IsSoftwarePcmParameters_2_1_Supported(pcm_config, clientif_source_);
  }

  bool IsCodecOffloadingSupported(const CodecConfiguration& codec_config) {
    CodecCapabilities codec_capability = {};
    for (auto audio_capability : clientif_sink_->GetAudioCapabilities()) {
      if (audio_capability.codecCapabilities().codecType ==
          codec_config.codecType) {
        codec_capability = audio_capability.codecCapabilities();
      }
    }
    if (codec_capability.codecType != codec_config.codecType) {
      // codec is unsupported
      return false;
    }
    bool is_codec_config_supported = false;
    switch (codec_config.codecType) {
      case CodecType::SBC: {
        SbcParameters sbc_config = codec_config.config.sbcConfig();
        SbcParameters sbc_capability =
            codec_capability.capabilities.sbcCapabilities();
        is_codec_config_supported =
            (sbc_config.sampleRate & sbc_capability.sampleRate &&
             sbc_config.channelMode & sbc_capability.channelMode &&
             sbc_config.blockLength & sbc_capability.blockLength &&
             sbc_config.numSubbands & sbc_capability.numSubbands &&
             sbc_config.allocMethod & sbc_capability.allocMethod &&
             sbc_config.bitsPerSample & sbc_capability.bitsPerSample &&
             (sbc_capability.minBitpool <= sbc_config.minBitpool &&
              sbc_config.minBitpool <= sbc_config.maxBitpool &&
              sbc_config.maxBitpool <= sbc_capability.maxBitpool));
        return is_codec_config_supported;
      }
      case CodecType::AAC: {
        AacParameters aac_config = codec_config.config.aacConfig();
        AacParameters aac_capability =
            codec_capability.capabilities.aacCapabilities();
        is_codec_config_supported =
            (aac_config.objectType & aac_capability.objectType &&
             aac_config.sampleRate & aac_capability.sampleRate &&
             aac_config.channelMode & aac_capability.channelMode &&
             (aac_config.variableBitRateEnabled ==
                  AacVariableBitRate::DISABLED ||
              aac_capability.variableBitRateEnabled ==
                  AacVariableBitRate::ENABLED) &&
             aac_config.bitsPerSample & aac_capability.bitsPerSample);
        return is_codec_config_supported;
      }
      case CodecType::LDAC: {
        LdacParameters ldac_config = codec_config.config.ldacConfig();
        LdacParameters ldac_capability =
            codec_capability.capabilities.ldacCapabilities();
        is_codec_config_supported =
            (ldac_config.sampleRate & ldac_capability.sampleRate &&
             ldac_config.channelMode & ldac_capability.channelMode &&
             ldac_config.bitsPerSample & ldac_capability.bitsPerSample);
        return is_codec_config_supported;
      }
      case CodecType::APTX:
        [[fallthrough]];
      case CodecType::APTX_HD: {
        AptxParameters aptx_config = codec_config.config.aptxConfig();
        AptxParameters aptx_capability =
            codec_capability.capabilities.aptxCapabilities();
        is_codec_config_supported =
            (aptx_config.sampleRate & aptx_capability.sampleRate &&
             aptx_config.channelMode & aptx_capability.channelMode &&
             aptx_config.bitsPerSample & aptx_capability.bitsPerSample);
        return is_codec_config_supported;
      }
      case CodecType::UNKNOWN:
        return false;
    }
  }

 private:
  bool IsSoftwarePcmParameters_2_1_Supported(
      const PcmParameters_2_1& pcm_config,
      const BluetoothAudioClientInterface* clientif_) {
    const std::vector<AudioCapabilities_2_1>& capabilities =
        clientif_->GetAudioCapabilities_2_1();
    PcmParameters_2_1 pcm_capabilities = capabilities[0].pcmCapabilities();
    bool is_pcm_config_valid =
        (pcm_config.sampleRate != SampleRate_2_1::RATE_UNKNOWN &&
         pcm_config.bitsPerSample != BitsPerSample::BITS_UNKNOWN &&
         pcm_config.channelMode != ChannelMode::UNKNOWN &&
         pcm_config.dataIntervalUs != 0);
    bool is_pcm_config_supported =
        (pcm_config.sampleRate & pcm_capabilities.sampleRate &&
         pcm_config.bitsPerSample & pcm_capabilities.bitsPerSample &&
         pcm_config.channelMode & pcm_capabilities.channelMode);
    return (is_pcm_config_valid && is_pcm_config_supported);
  }
};

}  // namespace

TEST_F(BluetoothAudioClientInterfaceTest, A2dpCodecToHalPcmConfig) {
  btav_a2dp_codec_config_t a2dp_codec_config = {};
  for (auto sample_rate_pair : kSampleRatePairs) {
    a2dp_codec_config.sample_rate = sample_rate_pair.btav_sample_rate_;
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      a2dp_codec_config.bits_per_sample =
          bits_per_sample_pair.btav_bits_per_sample_;
      for (auto channel_mode_pair : kChannelModePairs) {
        a2dp_codec_config.channel_mode = channel_mode_pair.btav_channel_mode_;
        ASSERT_EQ(A2dpCodecToHalSampleRate(a2dp_codec_config),
                  sample_rate_pair.hal_sample_rate_);
        ASSERT_EQ(A2dpCodecToHalBitsPerSample(a2dp_codec_config),
                  bits_per_sample_pair.hal_bits_per_sample_);
        ASSERT_EQ(A2dpCodecToHalChannelMode(a2dp_codec_config),
                  channel_mode_pair.hal_channel_mode_);
      }  // ChannelMode
    }    // BitsPerSampple
  }      // SampleRate
}

TEST_F(BluetoothAudioClientInterfaceTest, StartAndEndA2dpSoftwareSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_SOFTWARE_ENCODING_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  PcmParameters pcm_config = {};
  for (auto sample_rate_pair : kSampleRatePairs) {
    pcm_config.sampleRate = sample_rate_pair.hal_sample_rate_;
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      pcm_config.bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_;
      for (auto channel_mode_pair : kChannelModePairs) {
        pcm_config.channelMode = channel_mode_pair.hal_channel_mode_;
        audio_config.pcmConfig(pcm_config);
        clientif_sink_->UpdateAudioConfig(audio_config);
        if (IsSoftwarePcmParametersSupported(pcm_config)) {
          ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
        } else {
          ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
        }
        ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
      }  // ChannelMode
    }    // BitsPerSampple
  }      // SampleRate
}

struct CodecOffloadingPreference {
  bool is_target_codec_included_;
  std::vector<btav_a2dp_codec_config_t> preference_;
};

std::vector<CodecOffloadingPreference> CodecOffloadingPreferenceGenerator(
    btav_a2dp_codec_index_t target_codec_index) {
  std::vector<CodecOffloadingPreference> codec_offloading_preferences = {
      {.is_target_codec_included_ = false,
       .preference_ = std::vector<btav_a2dp_codec_config_t>(0)}};
  btav_a2dp_codec_config_t a2dp_codec_config = {};
  for (auto codec_index : codec_indexes) {
    a2dp_codec_config.codec_type = codec_index;
    auto duplicated_preferences = codec_offloading_preferences;
    for (auto iter = duplicated_preferences.begin();
         iter != duplicated_preferences.end(); ++iter) {
      if (codec_index == target_codec_index) {
        iter->is_target_codec_included_ = true;
      }
      iter->preference_.push_back(a2dp_codec_config);
    }
    codec_offloading_preferences.insert(codec_offloading_preferences.end(),
                                        duplicated_preferences.begin(),
                                        duplicated_preferences.end());
  }
  return codec_offloading_preferences;
}

std::vector<CodecConfiguration> SbcCodecConfigurationsGenerator() {
  std::vector<CodecConfiguration> sbc_codec_configs;
  CodecConfiguration codec_config = {};
  SbcBlockLength block_lengths[4] = {
      SbcBlockLength::BLOCKS_4, SbcBlockLength::BLOCKS_8,
      SbcBlockLength::BLOCKS_12, SbcBlockLength::BLOCKS_16};
  SbcNumSubbands num_subbands[2] = {SbcNumSubbands::SUBBAND_4,
                                    SbcNumSubbands::SUBBAND_8};
  SbcAllocMethod alloc_methods[2] = {SbcAllocMethod::ALLOC_MD_S,
                                     SbcAllocMethod::ALLOC_MD_L};
  for (auto sample_rate_pair : kSampleRatePairs) {
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      for (auto channel_mode_pair : kChannelModePairs) {
        for (auto peer_mtu : kPeerMtus) {
          for (auto block_length : block_lengths) {
            for (auto num_subband : num_subbands) {
              for (auto alloc_method : alloc_methods) {
                codec_config.codecType = CodecType::SBC;
                codec_config.peerMtu = peer_mtu;
                codec_config.isScmstEnabled = false;
                // A2DP_SBC_DEFAULT_BITRATE
                codec_config.encodedAudioBitrate = 328000;
                SbcParameters sbc = {
                    .sampleRate = sample_rate_pair.hal_sample_rate_,
                    .channelMode = (channel_mode_pair.hal_channel_mode_ ==
                                            ChannelMode::MONO
                                        ? SbcChannelMode::MONO
                                        : SbcChannelMode::JOINT_STEREO),
                    .blockLength = block_length,
                    .numSubbands = num_subband,
                    .allocMethod = alloc_method,
                    .bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_,
                    .minBitpool = 2,
                    .maxBitpool = 53};
                codec_config.config.sbcConfig(sbc);
                sbc_codec_configs.push_back(codec_config);
              }  // SbcAllocMethod
            }    // SbcNumSubbands
          }      // SbcBlockLength
        }        // peerMtu
      }          // ChannelMode
    }            // BitsPerSampple
  }              // SampleRate
  return sbc_codec_configs;
}

TEST_F(BluetoothAudioClientInterfaceTest, A2dpSbcCodecOffloadingState) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  auto sbc_codec_configs = SbcCodecConfigurationsGenerator();
  for (auto codec_offloading_preference :
       CodecOffloadingPreferenceGenerator(BTAV_A2DP_CODEC_INDEX_SOURCE_SBC)) {
    UpdateOffloadingCapabilities(codec_offloading_preference.preference_);
    for (CodecConfiguration codec_config : sbc_codec_configs) {
      if (IsCodecOffloadingSupported(codec_config) &&
          codec_offloading_preference.is_target_codec_included_) {
        ASSERT_TRUE(IsCodecOffloadingEnabled(codec_config));
      } else {
        ASSERT_FALSE(IsCodecOffloadingEnabled(codec_config));
      }
    }
  }
}

TEST_F(BluetoothAudioClientInterfaceTest, StartAndEndA2dpOffloadSbcSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  for (CodecConfiguration codec_config : SbcCodecConfigurationsGenerator()) {
    audio_config.codecConfig(codec_config);
    clientif_sink_->UpdateAudioConfig(audio_config);
    if (IsCodecOffloadingSupported(codec_config)) {
      ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    } else {
      ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    }
    ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
  }
}

std::vector<CodecConfiguration> AacCodecConfigurationsGenerator() {
  std::vector<CodecConfiguration> aac_codec_configs;
  CodecConfiguration codec_config = {};
  AacObjectType object_types[4] = {
      AacObjectType::MPEG2_LC, AacObjectType::MPEG4_LC,
      AacObjectType::MPEG4_LTP, AacObjectType::MPEG4_SCALABLE};
  AacVariableBitRate variable_bitrates[2] = {AacVariableBitRate::DISABLED,
                                             AacVariableBitRate::ENABLED};
  for (auto sample_rate_pair : kSampleRatePairs) {
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      for (auto channel_mode_pair : kChannelModePairs) {
        for (auto peer_mtu : kPeerMtus) {
          for (auto object_type : object_types) {
            for (auto variable_bitrate : variable_bitrates) {
              codec_config.codecType = CodecType::AAC;
              codec_config.peerMtu = peer_mtu;
              codec_config.isScmstEnabled = false;
              // A2DP_AAC_DEFAULT_BITRATE
              codec_config.encodedAudioBitrate = 320000;
              AacParameters aac = {
                  .objectType = object_type,
                  .sampleRate = sample_rate_pair.hal_sample_rate_,
                  .channelMode = channel_mode_pair.hal_channel_mode_,
                  .variableBitRateEnabled = variable_bitrate,
                  .bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_};
              codec_config.config.aacConfig(aac);
              aac_codec_configs.push_back(codec_config);
            }  // AacVariableBitRate
          }    // AacObjectType
        }      // peerMtu
      }        // ChannelMode
    }          // BitsPerSampple
  }            // SampleRate
  return aac_codec_configs;
}

TEST_F(BluetoothAudioClientInterfaceTest, A2dpAacCodecOffloadingState) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  auto aac_codec_configs = AacCodecConfigurationsGenerator();
  for (auto codec_offloading_preference :
       CodecOffloadingPreferenceGenerator(BTAV_A2DP_CODEC_INDEX_SOURCE_AAC)) {
    UpdateOffloadingCapabilities(codec_offloading_preference.preference_);
    for (CodecConfiguration codec_config : aac_codec_configs) {
      if (IsCodecOffloadingSupported(codec_config) &&
          codec_offloading_preference.is_target_codec_included_) {
        ASSERT_TRUE(IsCodecOffloadingEnabled(codec_config));
      } else {
        ASSERT_FALSE(IsCodecOffloadingEnabled(codec_config));
      }
    }
  }
}

TEST_F(BluetoothAudioClientInterfaceTest, StartAndEndA2dpOffloadAacSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  for (CodecConfiguration codec_config : AacCodecConfigurationsGenerator()) {
    audio_config.codecConfig(codec_config);
    clientif_sink_->UpdateAudioConfig(audio_config);
    if (IsCodecOffloadingSupported(codec_config)) {
      ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    } else {
      ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    }
    ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
  }
}

std::vector<CodecConfiguration> LdacCodecConfigurationsGenerator() {
  std::vector<CodecConfiguration> ldac_codec_configs;
  CodecConfiguration codec_config = {};
  LdacQualityIndex quality_indexes[4] = {
      LdacQualityIndex::QUALITY_HIGH, LdacQualityIndex::QUALITY_MID,
      LdacQualityIndex::QUALITY_LOW, LdacQualityIndex::QUALITY_ABR};
  for (auto sample_rate_pair : kSampleRatePairs) {
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      for (auto channel_mode_pair : kChannelModePairs) {
        for (auto peer_mtu : kPeerMtus) {
          for (auto quality_index : quality_indexes) {
            codec_config.codecType = CodecType::LDAC;
            codec_config.peerMtu = peer_mtu;
            codec_config.isScmstEnabled = false;
            codec_config.encodedAudioBitrate = 990000;
            LdacParameters ldac = {
                .sampleRate = sample_rate_pair.hal_sample_rate_,
                .channelMode =
                    (channel_mode_pair.hal_channel_mode_ == ChannelMode::MONO
                         ? LdacChannelMode::MONO
                         : LdacChannelMode::STEREO),
                .qualityIndex = quality_index,
                .bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_};
            codec_config.config.ldacConfig(ldac);
            ldac_codec_configs.push_back(codec_config);
          }  // LdacQualityIndex
        }    // peerMtu
      }      // ChannelMode
    }        // BitsPerSampple
  }          // SampleRate
  return ldac_codec_configs;
}

TEST_F(BluetoothAudioClientInterfaceTest, A2dpLdacCodecOffloadingState) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  auto ldac_codec_configs = LdacCodecConfigurationsGenerator();
  for (auto codec_offloading_preference :
       CodecOffloadingPreferenceGenerator(BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC)) {
    UpdateOffloadingCapabilities(codec_offloading_preference.preference_);
    for (CodecConfiguration codec_config : ldac_codec_configs) {
      if (IsCodecOffloadingSupported(codec_config) &&
          codec_offloading_preference.is_target_codec_included_) {
        ASSERT_TRUE(IsCodecOffloadingEnabled(codec_config));
      } else {
        ASSERT_FALSE(IsCodecOffloadingEnabled(codec_config));
      }
    }
  }
}

TEST_F(BluetoothAudioClientInterfaceTest, StartAndEndA2dpOffloadLdacSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  for (CodecConfiguration codec_config : LdacCodecConfigurationsGenerator()) {
    audio_config.codecConfig(codec_config);
    clientif_sink_->UpdateAudioConfig(audio_config);
    if (IsCodecOffloadingSupported(codec_config)) {
      ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    } else {
      ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    }
    ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
  }
}

std::vector<CodecConfiguration> AptxCodecConfigurationsGenerator(
    CodecType codec_type) {
  std::vector<CodecConfiguration> aptx_codec_configs;
  if (codec_type != CodecType::APTX && codec_type != CodecType::APTX_HD)
    return aptx_codec_configs;
  CodecConfiguration codec_config = {};
  for (auto sample_rate_pair : kSampleRatePairs) {
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      for (auto channel_mode_pair : kChannelModePairs) {
        for (auto peer_mtu : kPeerMtus) {
          codec_config.codecType = codec_type;
          codec_config.peerMtu = peer_mtu;
          codec_config.isScmstEnabled = false;
          codec_config.encodedAudioBitrate =
              (codec_type == CodecType::APTX ? 352000 : 576000);
          AptxParameters aptx = {
              .sampleRate = sample_rate_pair.hal_sample_rate_,
              .channelMode = channel_mode_pair.hal_channel_mode_,
              .bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_};
          codec_config.config.aptxConfig(aptx);
          aptx_codec_configs.push_back(codec_config);
        }  // peerMtu
      }    // ChannelMode
    }      // BitsPerSampple
  }        // SampleRate
  return aptx_codec_configs;
}

TEST_F(BluetoothAudioClientInterfaceTest, A2dpAptxCodecOffloadingState) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  auto aptx_codec_configs = AptxCodecConfigurationsGenerator(CodecType::APTX);
  for (auto codec_offloading_preference :
       CodecOffloadingPreferenceGenerator(BTAV_A2DP_CODEC_INDEX_SOURCE_APTX)) {
    UpdateOffloadingCapabilities(codec_offloading_preference.preference_);
    for (CodecConfiguration codec_config : aptx_codec_configs) {
      if (IsCodecOffloadingSupported(codec_config) &&
          codec_offloading_preference.is_target_codec_included_) {
        ASSERT_TRUE(IsCodecOffloadingEnabled(codec_config));
      } else {
        ASSERT_FALSE(IsCodecOffloadingEnabled(codec_config));
      }
    }
  }
}

TEST_F(BluetoothAudioClientInterfaceTest, StartAndEndA2dpOffloadAptxSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  for (CodecConfiguration codec_config :
       AptxCodecConfigurationsGenerator(CodecType::APTX)) {
    audio_config.codecConfig(codec_config);
    clientif_sink_->UpdateAudioConfig(audio_config);
    if (IsCodecOffloadingSupported(codec_config)) {
      ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    } else {
      ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    }
    ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
  }
}

TEST_F(BluetoothAudioClientInterfaceTest, A2dpAptxHdCodecOffloadingState) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  auto aptx_hd_codec_configs =
      AptxCodecConfigurationsGenerator(CodecType::APTX_HD);
  for (auto codec_offloading_preference : CodecOffloadingPreferenceGenerator(
           BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD)) {
    UpdateOffloadingCapabilities(codec_offloading_preference.preference_);
    for (CodecConfiguration codec_config : aptx_hd_codec_configs) {
      if (IsCodecOffloadingSupported(codec_config) &&
          codec_offloading_preference.is_target_codec_included_) {
        ASSERT_TRUE(IsCodecOffloadingEnabled(codec_config));
      } else {
        ASSERT_FALSE(IsCodecOffloadingEnabled(codec_config));
      }
    }
  }
}

TEST_F(BluetoothAudioClientInterfaceTest, StartAndEndA2dpOffloadAptxHdSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  for (CodecConfiguration codec_config :
       AptxCodecConfigurationsGenerator(CodecType::APTX_HD)) {
    audio_config.codecConfig(codec_config);
    clientif_sink_->UpdateAudioConfig(audio_config);
    if (IsCodecOffloadingSupported(codec_config)) {
      ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    } else {
      ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
    }
    ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
  }
}

TEST_F(BluetoothAudioClientInterfaceTest,
       StartAndEndA2dpOffloadUnknownSession) {
  test_sink_transport_ =
      new TestSinkTransport(SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  CodecConfiguration codec_config = {};
  codec_config.codecType = CodecType::UNKNOWN;
  codec_config.peerMtu = 1005;
  codec_config.isScmstEnabled = false;
  codec_config.encodedAudioBitrate = 328000;
  codec_config.config = {};
  audio_config.codecConfig(codec_config);
  clientif_sink_->UpdateAudioConfig(audio_config);
  if (IsCodecOffloadingSupported(codec_config)) {
    ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
  } else {
    ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
  }
  ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
}

TEST_F(BluetoothAudioClientInterfaceTest,
       StartAndEndHearingAidSoftwareSession) {
  test_sink_transport_ = new TestSinkTransport(
      SessionType::HEARING_AID_SOFTWARE_ENCODING_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration audio_config = {};
  PcmParameters pcm_config = {};
  for (auto sample_rate_pair : kSampleRatePairs) {
    pcm_config.sampleRate = sample_rate_pair.hal_sample_rate_;
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      pcm_config.bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_;
      for (auto channel_mode_pair : kChannelModePairs) {
        pcm_config.channelMode = channel_mode_pair.hal_channel_mode_;
        audio_config.pcmConfig(pcm_config);
        clientif_sink_->UpdateAudioConfig(audio_config);
        if (IsSoftwarePcmParametersSupported(pcm_config)) {
          ASSERT_EQ(clientif_sink_->StartSession(), kClientIfReturnSuccess);
        } else {
          ASSERT_NE(clientif_sink_->StartSession(), kClientIfReturnSuccess);
        }
        ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
      }  // ChannelMode
    }    // BitsPerSampple
  }      // SampleRate
}

TEST_F(BluetoothAudioClientInterfaceTest,
       StartAndEndLeAudioEncodingSoftwareSession) {
  test_sink_transport_ = new TestSinkTransport(
      SessionType_2_1::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH);
  clientif_sink_ =
      new BluetoothAudioSinkClientInterface(test_sink_transport_, nullptr);
  AudioConfiguration_2_1 audio_config = {};
  PcmParameters_2_1 pcm_config = {};
  for (auto sample_rate : kSampleRates_2_1) {
    pcm_config.sampleRate = sample_rate;
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      pcm_config.bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_;
      for (auto channel_mode_pair : kChannelModePairs) {
        pcm_config.channelMode = channel_mode_pair.hal_channel_mode_;
        for (auto data_interval_us : kDataIntervalUs) {
          pcm_config.dataIntervalUs = data_interval_us;
          audio_config.pcmConfig(pcm_config);
          clientif_sink_->UpdateAudioConfig_2_1(audio_config);
          if (IsSinkSoftwarePcmParameters_2_1_Supported(pcm_config)) {
            ASSERT_EQ(clientif_sink_->StartSession_2_1(),
                      kClientIfReturnSuccess);
          } else {
            ASSERT_NE(clientif_sink_->StartSession_2_1(),
                      kClientIfReturnSuccess);
          }
          ASSERT_EQ(clientif_sink_->EndSession(), kClientIfReturnSuccess);
        }  // dataIntervalUs
      }    // ChannelMode
    }      // BitsPerSampple
  }        // SampleRate
}

TEST_F(BluetoothAudioClientInterfaceTest,
       StartAndEndLeAudioDecodedSoftwareSession) {
  test_source_transport_ = new TestSourceTransport(
      SessionType_2_1::LE_AUDIO_SOFTWARE_DECODED_DATAPATH);
  clientif_source_ =
      new BluetoothAudioSourceClientInterface(test_source_transport_, nullptr);
  AudioConfiguration_2_1 audio_config = {};
  PcmParameters_2_1 pcm_config = {};
  for (auto sample_rate : kSampleRates_2_1) {
    pcm_config.sampleRate = sample_rate;
    for (auto bits_per_sample_pair : kBitsPerSamplePairs) {
      pcm_config.bitsPerSample = bits_per_sample_pair.hal_bits_per_sample_;
      for (auto channel_mode_pair : kChannelModePairs) {
        pcm_config.channelMode = channel_mode_pair.hal_channel_mode_;
        for (auto data_interval_us : kDataIntervalUs) {
          pcm_config.dataIntervalUs = data_interval_us;
          audio_config.pcmConfig(pcm_config);
          clientif_source_->UpdateAudioConfig_2_1(audio_config);
          if (IsSourceSoftwarePcmParameters_2_1_Supported(pcm_config)) {
            ASSERT_EQ(clientif_source_->StartSession_2_1(),
                      kClientIfReturnSuccess);
          } else {
            ASSERT_NE(clientif_source_->StartSession_2_1(),
                      kClientIfReturnSuccess);
          }
          ASSERT_EQ(clientif_source_->EndSession(), kClientIfReturnSuccess);
        }  // dataIntervalUs
      }    // ChannelMode
    }      // BitsPerSampple
  }        // SampleRate
}
