/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#define LOG_TAG "client_interface_fuzzer"

#include <fuzzer/FuzzedDataProvider.h>

#include "audio_hal_interface/a2dp_encoding.h"
#include "audio_hal_interface/hal_version_manager.h"
#include "audio_hal_interface/hidl/client_interface_hidl.h"
#include "audio_hal_interface/hidl/codec_status_hidl.h"
#include "audio_hal_interface/le_audio_software.h"
#include "include/btif_av_co.h"
#include "osi/include/properties.h"

using ::android::hardware::bluetooth::audio::V2_0::AacObjectType;
using ::android::hardware::bluetooth::audio::V2_0::AacParameters;
using ::android::hardware::bluetooth::audio::V2_0::AacVariableBitRate;
using ::android::hardware::bluetooth::audio::V2_0::AptxParameters;
using ::android::hardware::bluetooth::audio::V2_0::CodecType;
using ::android::hardware::bluetooth::audio::V2_0::LdacChannelMode;
using ::android::hardware::bluetooth::audio::V2_0::LdacParameters;
using ::android::hardware::bluetooth::audio::V2_0::LdacQualityIndex;
using ::android::hardware::bluetooth::audio::V2_0::SbcAllocMethod;
using ::android::hardware::bluetooth::audio::V2_0::SbcBlockLength;
using ::android::hardware::bluetooth::audio::V2_0::SbcChannelMode;
using ::android::hardware::bluetooth::audio::V2_0::SbcNumSubbands;
using ::android::hardware::bluetooth::audio::V2_0::SbcParameters;
using ::bluetooth::audio::a2dp::update_codec_offloading_capabilities;
using ::bluetooth::audio::hidl::AudioConfiguration;
using ::bluetooth::audio::hidl::AudioConfiguration_2_1;
using ::bluetooth::audio::hidl::BluetoothAudioSinkClientInterface;
using ::bluetooth::audio::hidl::BluetoothAudioSourceClientInterface;
using ::bluetooth::audio::hidl::PcmParameters;
using ::bluetooth::audio::hidl::PcmParameters_2_1;
using ::bluetooth::audio::hidl::SampleRate;
using ::bluetooth::audio::hidl::SampleRate_2_1;
using ::bluetooth::audio::hidl::SessionType;
using ::bluetooth::audio::hidl::SessionType_2_1;
using ::bluetooth::audio::hidl::codec::A2dpAacToHalConfig;
using ::bluetooth::audio::hidl::codec::A2dpAptxToHalConfig;
using ::bluetooth::audio::hidl::codec::A2dpCodecToHalBitsPerSample;
using ::bluetooth::audio::hidl::codec::A2dpCodecToHalChannelMode;
using ::bluetooth::audio::hidl::codec::A2dpCodecToHalSampleRate;
using ::bluetooth::audio::hidl::codec::A2dpLdacToHalConfig;
using ::bluetooth::audio::hidl::codec::A2dpSbcToHalConfig;
using ::bluetooth::audio::hidl::codec::BitsPerSample;
using ::bluetooth::audio::hidl::codec::ChannelMode;
using ::bluetooth::audio::hidl::codec::CodecConfiguration;
using ::bluetooth::audio::hidl::codec::IsCodecOffloadingEnabled;
using ::bluetooth::audio::hidl::codec::UpdateOffloadingCapabilities;

extern "C" {
struct android_namespace_t* android_get_exported_namespace(const char*) {
  return nullptr;
}
}

constexpr SessionType kSessionTypes[] = {
    SessionType::UNKNOWN,
    SessionType::A2DP_SOFTWARE_ENCODING_DATAPATH,
    SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH,
    SessionType::HEARING_AID_SOFTWARE_ENCODING_DATAPATH,
};

constexpr bluetooth::audio::hidl::BluetoothAudioCtrlAck
    kBluetoothAudioCtrlAcks[] = {
        bluetooth::audio::hidl::BluetoothAudioCtrlAck::SUCCESS_FINISHED,
        bluetooth::audio::hidl::BluetoothAudioCtrlAck::PENDING,
        bluetooth::audio::hidl::BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED,
        bluetooth::audio::hidl::BluetoothAudioCtrlAck::FAILURE_BUSY,
        bluetooth::audio::hidl::BluetoothAudioCtrlAck::FAILURE_DISCONNECTING,
        bluetooth::audio::hidl::BluetoothAudioCtrlAck::FAILURE};

constexpr SessionType_2_1 kSessionTypes_2_1[] = {
    SessionType_2_1::UNKNOWN,
    SessionType_2_1::A2DP_SOFTWARE_ENCODING_DATAPATH,
    SessionType_2_1::A2DP_HARDWARE_OFFLOAD_DATAPATH,
    SessionType_2_1::HEARING_AID_SOFTWARE_ENCODING_DATAPATH,
    SessionType_2_1::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH,
    SessionType_2_1::LE_AUDIO_SOFTWARE_DECODED_DATAPATH,
    SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH,
    SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH,
};

constexpr SampleRate kSampleRates[] = {
    SampleRate::RATE_UNKNOWN, SampleRate::RATE_44100, SampleRate::RATE_48000,
    SampleRate::RATE_88200,   SampleRate::RATE_96000, SampleRate::RATE_176400,
    SampleRate::RATE_192000,  SampleRate::RATE_16000, SampleRate::RATE_24000};

constexpr btav_a2dp_codec_sample_rate_t kBtavSampleRates[] = {
    BTAV_A2DP_CODEC_SAMPLE_RATE_NONE,   BTAV_A2DP_CODEC_SAMPLE_RATE_44100,
    BTAV_A2DP_CODEC_SAMPLE_RATE_48000,  BTAV_A2DP_CODEC_SAMPLE_RATE_88200,
    BTAV_A2DP_CODEC_SAMPLE_RATE_96000,  BTAV_A2DP_CODEC_SAMPLE_RATE_176400,
    BTAV_A2DP_CODEC_SAMPLE_RATE_192000, BTAV_A2DP_CODEC_SAMPLE_RATE_16000,
    BTAV_A2DP_CODEC_SAMPLE_RATE_24000};

constexpr SampleRate_2_1 kSampleRates_2_1[] = {
    SampleRate_2_1::RATE_UNKNOWN, SampleRate_2_1::RATE_8000,
    SampleRate_2_1::RATE_16000,   SampleRate_2_1::RATE_24000,
    SampleRate_2_1::RATE_32000,   SampleRate_2_1::RATE_44100,
    SampleRate_2_1::RATE_48000};

constexpr BitsPerSample kBitsPerSamples[] = {
    BitsPerSample::BITS_UNKNOWN, BitsPerSample::BITS_16, BitsPerSample::BITS_24,
    BitsPerSample::BITS_32};

constexpr btav_a2dp_codec_bits_per_sample_t kBtavA2dpCodecBitsPerSample[] = {
    BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE, BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16,
    BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24, BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32};

constexpr ChannelMode kChannelModes[] = {
    ChannelMode::UNKNOWN, ChannelMode::MONO, ChannelMode::STEREO};

constexpr btav_a2dp_codec_channel_mode_t kBtavA2dpCodecChannelModes[] = {
    BTAV_A2DP_CODEC_CHANNEL_MODE_NONE, BTAV_A2DP_CODEC_CHANNEL_MODE_MONO,
    BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO};

constexpr uint16_t kPeerMtus[] = {660, 663, 883, 1005, 1500};

constexpr btav_a2dp_codec_index_t kCodecIndices[] = {
    BTAV_A2DP_CODEC_INDEX_SOURCE_SBC,  BTAV_A2DP_CODEC_INDEX_SOURCE_AAC,
    BTAV_A2DP_CODEC_INDEX_SOURCE_APTX, BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD,
    BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC, BTAV_A2DP_CODEC_INDEX_SINK_SBC,
    BTAV_A2DP_CODEC_INDEX_SINK_AAC,    BTAV_A2DP_CODEC_INDEX_SINK_LDAC};

class TestSinkTransport
    : public bluetooth::audio::hidl::IBluetoothSinkTransportInstance {
 private:
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

  bool GetPresentationPosition(uint64_t*, uint64_t*, timespec*) override {
    return true;
  }

  void MetadataChanged(const source_metadata_t&) override {}

  void ResetPresentationPosition() override{};

  void LogBytesRead(size_t) override{};
};

class TestSourceTransport
    : public bluetooth::audio::hidl::IBluetoothSourceTransportInstance {
 private:
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

  bool GetPresentationPosition(uint64_t*, uint64_t*, timespec*) override {
    return true;
  }

  void MetadataChanged(const source_metadata_t&) override {}

  void ResetPresentationPosition() override{};

  void LogBytesWritten(size_t) override{};
};

class ClientInterfaceFuzzer {
 public:
  ~ClientInterfaceFuzzer() {
    delete (mFdp);
    delete (mTestSinkTransport);
    delete (mTestSourceTransport);
    delete (mClientIfSink);
    delete (mClientIfSource);
  }
  void process(const uint8_t* data, size_t size);

 private:
  FuzzedDataProvider* mFdp = nullptr;
  TestSinkTransport* mTestSinkTransport = nullptr;
  TestSourceTransport* mTestSourceTransport = nullptr;
  BluetoothAudioSinkClientInterface* mClientIfSink = nullptr;
  BluetoothAudioSourceClientInterface* mClientIfSource = nullptr;
};

static CodecConfiguration SbcCodecConfigurationsGenerator(
    FuzzedDataProvider* mFdp) {
  SbcNumSubbands numSubbands[] = {SbcNumSubbands::SUBBAND_4,
                                  SbcNumSubbands::SUBBAND_8};

  SbcAllocMethod allocMethods[] = {SbcAllocMethod::ALLOC_MD_S,
                                   SbcAllocMethod::ALLOC_MD_L};

  SbcChannelMode channelModes[] = {
      SbcChannelMode::UNKNOWN, SbcChannelMode::JOINT_STEREO,
      SbcChannelMode::STEREO,  SbcChannelMode::DUAL,
      SbcChannelMode::MONO,
  };

  SbcBlockLength blockLengths[] = {
      SbcBlockLength::BLOCKS_4, SbcBlockLength::BLOCKS_8,
      SbcBlockLength::BLOCKS_12, SbcBlockLength::BLOCKS_16};

  SbcParameters sbc = {};
  sbc.sampleRate = mFdp->PickValueInArray(kSampleRates);
  sbc.channelMode = mFdp->PickValueInArray(channelModes);
  sbc.blockLength = mFdp->PickValueInArray(blockLengths);
  sbc.numSubbands = mFdp->PickValueInArray(numSubbands);
  sbc.allocMethod = mFdp->PickValueInArray(allocMethods);
  sbc.bitsPerSample = mFdp->PickValueInArray(kBitsPerSamples);
  sbc.minBitpool = mFdp->ConsumeIntegral<uint8_t>();
  sbc.maxBitpool = mFdp->ConsumeIntegral<uint8_t>();

  CodecConfiguration codecConfig = {};
  codecConfig.config.sbcConfig(sbc);
  codecConfig.codecType = CodecType::SBC;
  codecConfig.peerMtu = mFdp->PickValueInArray(kPeerMtus);
  codecConfig.isScmstEnabled = mFdp->ConsumeBool();
  codecConfig.encodedAudioBitrate = mFdp->ConsumeIntegral<uint32_t>();

  return codecConfig;
}

static CodecConfiguration AacCodecConfigurationsGenerator(
    FuzzedDataProvider* mFdp) {
  AacObjectType objectTypes[] = {
      AacObjectType::MPEG2_LC, AacObjectType::MPEG4_LC,
      AacObjectType::MPEG4_LTP, AacObjectType::MPEG4_SCALABLE};

  AacVariableBitRate variableBitrates[] = {AacVariableBitRate::DISABLED,
                                           AacVariableBitRate::ENABLED};

  AacParameters aac = {};
  aac.objectType = mFdp->PickValueInArray(objectTypes);
  aac.sampleRate = mFdp->PickValueInArray(kSampleRates);
  aac.channelMode = mFdp->PickValueInArray(kChannelModes);
  aac.variableBitRateEnabled = mFdp->PickValueInArray(variableBitrates);
  aac.bitsPerSample = mFdp->PickValueInArray(kBitsPerSamples);

  CodecConfiguration codecConfig = {};
  codecConfig.config.aacConfig(aac);
  codecConfig.codecType = CodecType::AAC;
  codecConfig.peerMtu = mFdp->PickValueInArray(kPeerMtus);
  codecConfig.isScmstEnabled = mFdp->ConsumeBool();
  codecConfig.encodedAudioBitrate = mFdp->ConsumeIntegral<uint32_t>();

  return codecConfig;
}

static CodecConfiguration LdacCodecConfigurationsGenerator(
    FuzzedDataProvider* mFdp) {
  LdacQualityIndex qualityIndexes[] = {
      LdacQualityIndex::QUALITY_HIGH, LdacQualityIndex::QUALITY_MID,
      LdacQualityIndex::QUALITY_LOW, LdacQualityIndex::QUALITY_ABR};

  LdacChannelMode kChannelModes[] = {
      LdacChannelMode::UNKNOWN,
      LdacChannelMode::STEREO,
      LdacChannelMode::DUAL,
      LdacChannelMode::MONO,
  };

  LdacParameters ldac = {};
  ldac.sampleRate = mFdp->PickValueInArray(kSampleRates);
  ldac.channelMode = mFdp->PickValueInArray(kChannelModes);
  ldac.qualityIndex = mFdp->PickValueInArray(qualityIndexes);
  ldac.bitsPerSample = mFdp->PickValueInArray(kBitsPerSamples);

  CodecConfiguration codecConfig = {};
  codecConfig.config.ldacConfig(ldac);
  codecConfig.codecType = CodecType::LDAC;
  codecConfig.peerMtu = mFdp->PickValueInArray(kPeerMtus);
  codecConfig.isScmstEnabled = mFdp->ConsumeBool();
  codecConfig.encodedAudioBitrate = mFdp->ConsumeIntegral<uint32_t>();

  return codecConfig;
}

static CodecConfiguration AptxCodecConfigurationsGenerator(
    FuzzedDataProvider* mFdp) {
  CodecType codecTypes[] = {CodecType::APTX, CodecType::APTX_HD};

  AptxParameters aptx = {};
  aptx.sampleRate = mFdp->PickValueInArray(kSampleRates);
  aptx.channelMode = mFdp->PickValueInArray(kChannelModes);
  aptx.bitsPerSample = mFdp->PickValueInArray(kBitsPerSamples);

  CodecConfiguration codecConfig = {};
  codecConfig.config.aptxConfig(aptx);
  codecConfig.codecType = mFdp->PickValueInArray(codecTypes);
  codecConfig.peerMtu = mFdp->PickValueInArray(kPeerMtus);
  codecConfig.isScmstEnabled = mFdp->ConsumeBool();
  codecConfig.encodedAudioBitrate = mFdp->ConsumeIntegral<uint32_t>();

  return codecConfig;
}

std::vector<std::vector<btav_a2dp_codec_config_t>>
CodecOffloadingPreferenceGenerator() {
  std::vector<std::vector<btav_a2dp_codec_config_t>> offloadingPreferences = {
      std::vector<btav_a2dp_codec_config_t>(0)};
  btav_a2dp_codec_config_t a2dpCodecConfig = {};
  for (btav_a2dp_codec_index_t i : kCodecIndices) {
    a2dpCodecConfig.codec_type = i;
    auto duplicated_preferences = offloadingPreferences;
    for (auto iter = duplicated_preferences.begin();
         iter != duplicated_preferences.end(); ++iter) {
      iter->push_back(a2dpCodecConfig);
    }
    offloadingPreferences.insert(offloadingPreferences.end(),
                                 duplicated_preferences.begin(),
                                 duplicated_preferences.end());
  }
  return offloadingPreferences;
}

void ClientInterfaceFuzzer::process(const uint8_t* data, size_t size) {
  mFdp = new FuzzedDataProvider(data, size);
  osi_property_set("persist.bluetooth.a2dp_offload.disabled",
                   mFdp->PickValueInArray({"true", "false"}));

  btav_a2dp_codec_config_t a2dpCodecConfig = {};
  a2dpCodecConfig.sample_rate = mFdp->PickValueInArray(kBtavSampleRates);
  a2dpCodecConfig.bits_per_sample =
      mFdp->PickValueInArray(kBtavA2dpCodecBitsPerSample);
  a2dpCodecConfig.channel_mode =
      mFdp->PickValueInArray(kBtavA2dpCodecChannelModes);

  A2dpCodecToHalSampleRate(a2dpCodecConfig);
  A2dpCodecToHalBitsPerSample(a2dpCodecConfig);
  A2dpCodecToHalChannelMode(a2dpCodecConfig);

  SessionType sessionType;
  SessionType_2_1 sessionType_2_1;

  bool isSessionType_2_1 = mFdp->ConsumeBool();
  if (isSessionType_2_1) {
    sessionType_2_1 = mFdp->PickValueInArray(kSessionTypes_2_1);
    mTestSinkTransport = new TestSinkTransport(sessionType_2_1);
    mTestSourceTransport = new TestSourceTransport(sessionType_2_1);
  } else {
    sessionType = mFdp->PickValueInArray(kSessionTypes);
    mTestSinkTransport = new TestSinkTransport(sessionType);
    mTestSourceTransport = new TestSourceTransport(sessionType);
  }

  mClientIfSink =
      new BluetoothAudioSinkClientInterface(mTestSinkTransport, nullptr);
  mClientIfSink->GetTransportInstance();
  mClientIfSink->IsValid();

  mClientIfSource =
      new BluetoothAudioSourceClientInterface(mTestSourceTransport, nullptr);
  mClientIfSource->IsValid();

  CodecConfiguration codecConfig = {};
  switch (mFdp->ConsumeIntegralInRange<int>(1, 4)) {
    case 1:
      codecConfig = SbcCodecConfigurationsGenerator(mFdp);
      break;
    case 2:
      codecConfig = AacCodecConfigurationsGenerator(mFdp);
      break;
    case 3:
      codecConfig = LdacCodecConfigurationsGenerator(mFdp);
      break;
    default:
      codecConfig = AptxCodecConfigurationsGenerator(mFdp);
      break;
  }

  if ((!isSessionType_2_1) &&
      (sessionType == SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH)) {
    for (auto codec_offloading_preference :
         CodecOffloadingPreferenceGenerator()) {
      UpdateOffloadingCapabilities(codec_offloading_preference);
      update_codec_offloading_capabilities(codec_offloading_preference, false);
    }
    IsCodecOffloadingEnabled(codecConfig);
  }

  if (isSessionType_2_1) {
    PcmParameters_2_1 pcmConfig = {};
    pcmConfig.sampleRate = mFdp->PickValueInArray(kSampleRates_2_1);
    pcmConfig.bitsPerSample = mFdp->PickValueInArray(kBitsPerSamples);
    pcmConfig.channelMode = mFdp->PickValueInArray(kChannelModes);

    AudioConfiguration_2_1 audioConfig = {};
    audioConfig.pcmConfig(pcmConfig);
    audioConfig.codecConfig(codecConfig);

    mClientIfSink->StartSession_2_1();
    mClientIfSink->GetAudioCapabilities_2_1();
    mClientIfSink->GetAudioCapabilities_2_1(sessionType_2_1);
    mClientIfSink->UpdateAudioConfig_2_1(audioConfig);
  } else {
    PcmParameters pcmConfig = {};
    pcmConfig.sampleRate = mFdp->PickValueInArray(kSampleRates);
    pcmConfig.bitsPerSample = mFdp->PickValueInArray(kBitsPerSamples);
    pcmConfig.channelMode = mFdp->PickValueInArray(kChannelModes);

    AudioConfiguration audioConfig = {};
    audioConfig.pcmConfig(pcmConfig);
    audioConfig.codecConfig(codecConfig);

    mClientIfSink->StartSession();
    mClientIfSink->GetAudioCapabilities();
    mClientIfSink->GetAudioCapabilities(sessionType);
    mClientIfSink->UpdateAudioConfig(audioConfig);
  }

  if (((bluetooth::audio::HalVersionManager::GetHalVersion() ==
        bluetooth::audio::BluetoothAudioHalVersion::VERSION_2_1) &&
       (mTestSinkTransport->GetSessionType_2_1() !=
        SessionType_2_1::UNKNOWN)) ||
      (mTestSinkTransport->GetSessionType() != SessionType::UNKNOWN)) {
    mClientIfSink->RenewAudioProviderAndSession();
  }

  mClientIfSink->StreamStarted(mFdp->PickValueInArray(kBluetoothAudioCtrlAcks));
  mClientIfSink->StreamSuspended(
      mFdp->PickValueInArray(kBluetoothAudioCtrlAcks));
  mClientIfSink->EndSession();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  ClientInterfaceFuzzer clientInterfaceFuzzer;
  clientInterfaceFuzzer.process(data, size);
  return 0;
}
