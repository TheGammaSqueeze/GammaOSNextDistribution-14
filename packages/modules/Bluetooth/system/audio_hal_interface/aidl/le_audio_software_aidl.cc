/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#define LOG_TAG "BTAudioLeAudioAIDL"

#include "le_audio_software_aidl.h"

#include <atomic>
#include <unordered_map>
#include <vector>

#include "codec_status_aidl.h"
#include "hal_version_manager.h"
#include "os/log.h"

namespace bluetooth {
namespace audio {
namespace aidl {
namespace le_audio {

using ::aidl::android::hardware::bluetooth::audio::AudioConfiguration;
using ::aidl::android::hardware::bluetooth::audio::AudioLocation;
using ::aidl::android::hardware::bluetooth::audio::ChannelMode;
using ::aidl::android::hardware::bluetooth::audio::CodecType;
using ::aidl::android::hardware::bluetooth::audio::Lc3Configuration;
using ::aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration;
using ::aidl::android::hardware::bluetooth::audio::PcmConfiguration;
using ::bluetooth::audio::aidl::AudioConfiguration;
using ::bluetooth::audio::aidl::BluetoothAudioCtrlAck;
using ::bluetooth::audio::le_audio::LeAudioClientInterface;
using ::bluetooth::audio::le_audio::StartRequestState;
using ::bluetooth::audio::le_audio::StreamCallbacks;
using ::le_audio::set_configurations::SetConfiguration;
using ::le_audio::types::LeAudioCoreCodecConfig;

static ChannelMode le_audio_channel_mode2audio_hal(uint8_t channels_count) {
  switch (channels_count) {
    case 1:
      return ChannelMode::MONO;
    case 2:
      return ChannelMode::STEREO;
  }
  return ChannelMode::UNKNOWN;
}

LeAudioTransport::LeAudioTransport(void (*flush)(void),
                                   StreamCallbacks stream_cb,
                                   PcmConfiguration pcm_config)
    : flush_(std::move(flush)),
      stream_cb_(std::move(stream_cb)),
      remote_delay_report_ms_(0),
      total_bytes_processed_(0),
      data_position_({}),
      pcm_config_(std::move(pcm_config)),
      start_request_state_(StartRequestState::IDLE),
      dsa_mode_(DsaMode::DISABLED){};

BluetoothAudioCtrlAck LeAudioTransport::StartRequest(bool is_low_latency) {
  // Check if operation is pending already
  if (GetStartRequestState() == StartRequestState::PENDING_AFTER_RESUME) {
    LOG_INFO("Start request is already pending. Ignore the request");
    return BluetoothAudioCtrlAck::PENDING;
  }

  SetStartRequestState(StartRequestState::PENDING_BEFORE_RESUME);
  if (stream_cb_.on_resume_(true)) {
    auto expected = StartRequestState::CONFIRMED;
    if (std::atomic_compare_exchange_strong(&start_request_state_, &expected,
                                            StartRequestState::IDLE)) {
      LOG_INFO("Start completed.");
      return BluetoothAudioCtrlAck::SUCCESS_FINISHED;
    }

    expected = StartRequestState::CANCELED;
    if (std::atomic_compare_exchange_strong(&start_request_state_, &expected,
                                            StartRequestState::IDLE)) {
      LOG_INFO("Start request failed.");
      return BluetoothAudioCtrlAck::FAILURE;
    }

    expected = StartRequestState::PENDING_BEFORE_RESUME;
    if (std::atomic_compare_exchange_strong(
            &start_request_state_, &expected,
            StartRequestState::PENDING_AFTER_RESUME)) {
      LOG_INFO("Start pending.");
      return BluetoothAudioCtrlAck::PENDING;
    }
  }

  LOG_ERROR("Start request failed.");
  auto expected = StartRequestState::PENDING_BEFORE_RESUME;
  std::atomic_compare_exchange_strong(&start_request_state_, &expected,
                                      StartRequestState::IDLE);
  return BluetoothAudioCtrlAck::FAILURE;
}

BluetoothAudioCtrlAck LeAudioTransport::StartRequestV2(bool is_low_latency) {
  // Check if operation is pending already
  if (GetStartRequestState() == StartRequestState::PENDING_AFTER_RESUME) {
    LOG_INFO("Start request is already pending. Ignore the request");
    return BluetoothAudioCtrlAck::PENDING;
  }

  SetStartRequestState(StartRequestState::PENDING_BEFORE_RESUME);
  if (stream_cb_.on_resume_(true)) {
    std::lock_guard<std::mutex> guard(start_request_state_mutex_);

    switch (start_request_state_) {
      case StartRequestState::CONFIRMED:
        LOG_INFO("Start completed.");
        SetStartRequestState(StartRequestState::IDLE);
        return BluetoothAudioCtrlAck::SUCCESS_FINISHED;
      case StartRequestState::CANCELED:
        LOG_INFO("Start request failed.");
        SetStartRequestState(StartRequestState::IDLE);
        return BluetoothAudioCtrlAck::FAILURE;
      case StartRequestState::PENDING_BEFORE_RESUME:
        LOG_INFO("Start pending.");
        SetStartRequestState(StartRequestState::PENDING_AFTER_RESUME);
        return BluetoothAudioCtrlAck::PENDING;
      default:
        SetStartRequestState(StartRequestState::IDLE);
        LOG_ERROR("Unexpected state %d",
                  static_cast<int>(start_request_state_.load()));
        return BluetoothAudioCtrlAck::FAILURE;
    }
  }

  SetStartRequestState(StartRequestState::IDLE);
  LOG_INFO("On resume failed.");
  return BluetoothAudioCtrlAck::FAILURE;
}

BluetoothAudioCtrlAck LeAudioTransport::SuspendRequest() {
  LOG(INFO) << __func__;
  if (stream_cb_.on_suspend_()) {
    flush_();
    return BluetoothAudioCtrlAck::SUCCESS_FINISHED;
  } else {
    return BluetoothAudioCtrlAck::FAILURE;
  }
}

void LeAudioTransport::StopRequest() {
  LOG(INFO) << __func__;
  if (stream_cb_.on_suspend_()) {
    flush_();
  }
}

void LeAudioTransport::SetLatencyMode(LatencyMode latency_mode) {
  LOG_DEBUG("Latency mode: %s",
            ::aidl::android::hardware::bluetooth::audio::toString(latency_mode)
                .c_str());
  switch (latency_mode) {
    case LatencyMode::FREE:
      dsa_mode_ = DsaMode::DISABLED;
      break;
    case LatencyMode::LOW_LATENCY:
      dsa_mode_ = DsaMode::ACL;
      break;
    case LatencyMode::DYNAMIC_SPATIAL_AUDIO_SOFTWARE:
      dsa_mode_ = DsaMode::ISO_SW;
      break;
    case LatencyMode::DYNAMIC_SPATIAL_AUDIO_HARDWARE:
      dsa_mode_ = DsaMode::ISO_HW;
      break;
    default:
      LOG(WARNING) << ", invalid latency mode: " << (int)latency_mode;
      break;
  }
}

bool LeAudioTransport::GetPresentationPosition(uint64_t* remote_delay_report_ns,
                                               uint64_t* total_bytes_processed,
                                               timespec* data_position) {
  VLOG(2) << __func__ << ": data=" << total_bytes_processed_
          << " byte(s), timestamp=" << data_position_.tv_sec << "."
          << data_position_.tv_nsec
          << "s, delay report=" << remote_delay_report_ms_ << " msec.";
  if (remote_delay_report_ns != nullptr) {
    *remote_delay_report_ns = remote_delay_report_ms_ * 1000000u;
  }
  if (total_bytes_processed != nullptr)
    *total_bytes_processed = total_bytes_processed_;
  if (data_position != nullptr) *data_position = data_position_;

  return true;
}

void LeAudioTransport::SourceMetadataChanged(
    const source_metadata_v7_t& source_metadata) {
  auto track_count = source_metadata.track_count;

  if (track_count == 0) {
    LOG(WARNING) << ", invalid number of metadata changed tracks";
    return;
  }

  stream_cb_.on_metadata_update_(source_metadata, dsa_mode_);
}

void LeAudioTransport::SinkMetadataChanged(
    const sink_metadata_v7_t& sink_metadata) {
  auto track_count = sink_metadata.track_count;

  if (track_count == 0) {
    LOG(WARNING) << ", invalid number of metadata changed tracks";
    return;
  }

  if (stream_cb_.on_sink_metadata_update_)
    stream_cb_.on_sink_metadata_update_(sink_metadata);
}

void LeAudioTransport::ResetPresentationPosition() {
  VLOG(2) << __func__ << ": called.";
  remote_delay_report_ms_ = 0;
  total_bytes_processed_ = 0;
  data_position_ = {};
}

void LeAudioTransport::LogBytesProcessed(size_t bytes_processed) {
  if (bytes_processed) {
    total_bytes_processed_ += bytes_processed;
    clock_gettime(CLOCK_MONOTONIC, &data_position_);
  }
}

void LeAudioTransport::SetRemoteDelay(uint16_t delay_report_ms) {
  LOG(INFO) << __func__ << ": delay_report=" << delay_report_ms << " msec";
  remote_delay_report_ms_ = delay_report_ms;
}

const PcmConfiguration& LeAudioTransport::LeAudioGetSelectedHalPcmConfig() {
  return pcm_config_;
}

void LeAudioTransport::LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz,
                                                      uint8_t bit_rate,
                                                      uint8_t channels_count,
                                                      uint32_t data_interval) {
  pcm_config_.sampleRateHz = (sample_rate_hz);
  pcm_config_.bitsPerSample = (bit_rate);
  pcm_config_.channelMode = le_audio_channel_mode2audio_hal(channels_count);
  pcm_config_.dataIntervalUs = data_interval;
}

void LeAudioTransport::LeAudioSetBroadcastConfig(
    const ::le_audio::broadcast_offload_config& offload_config) {
  broadcast_config_.streamMap.resize(0);
  for (auto& [handle, location] : offload_config.stream_map) {
    Lc3Configuration lc3_config{
        .pcmBitDepth = static_cast<int8_t>(offload_config.bits_per_sample),
        .samplingFrequencyHz =
            static_cast<int32_t>(offload_config.sampling_rate),
        .frameDurationUs = static_cast<int32_t>(offload_config.frame_duration),
        .octetsPerFrame = static_cast<int32_t>(offload_config.octets_per_frame),
        .blocksPerSdu = static_cast<int8_t>(offload_config.blocks_per_sdu),
    };
    broadcast_config_.streamMap.push_back({
        .streamHandle = handle,
        .audioChannelAllocation = static_cast<int32_t>(location),
        .leAudioCodecConfig = std::move(lc3_config),
    });
  }
}

const LeAudioBroadcastConfiguration&
LeAudioTransport::LeAudioGetBroadcastConfig() {
  return broadcast_config_;
}

bool LeAudioTransport::IsRequestCompletedAfterUpdate(
    const std::function<std::pair<StartRequestState, bool>(StartRequestState)>&
        lambda) {
  std::lock_guard<std::mutex> guard(start_request_state_mutex_);
  auto result = lambda(start_request_state_);
  auto new_state = std::get<0>(result);
  if (new_state != start_request_state_) {
    start_request_state_ = new_state;
  }

  auto ret = std::get<1>(result);
  LOG_VERBOSE(" new state: %d, return %s", (int)(start_request_state_.load()),
              ret ? "true" : "false");

  return ret;
}

StartRequestState LeAudioTransport::GetStartRequestState(void) {
  return start_request_state_;
}
void LeAudioTransport::ClearStartRequestState(void) {
  start_request_state_ = StartRequestState::IDLE;
}
void LeAudioTransport::SetStartRequestState(StartRequestState state) {
  start_request_state_ = state;
}

inline void flush_unicast_sink() {
  if (LeAudioSinkTransport::interface_unicast_ == nullptr) return;

  LeAudioSinkTransport::interface_unicast_->FlushAudioData();
}

inline void flush_broadcast_sink() {
  if (LeAudioSinkTransport::interface_broadcast_ == nullptr) return;

  LeAudioSinkTransport::interface_broadcast_->FlushAudioData();
}

inline bool is_broadcaster_session(SessionType session_type) {
  if (session_type ==
          SessionType::LE_AUDIO_BROADCAST_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
      session_type ==
          SessionType::LE_AUDIO_BROADCAST_SOFTWARE_ENCODING_DATAPATH) {
    return true;
  }

  return false;
}

LeAudioSinkTransport::LeAudioSinkTransport(SessionType session_type,
                                           StreamCallbacks stream_cb)
    : IBluetoothSinkTransportInstance(session_type, (AudioConfiguration){}) {
  transport_ = new LeAudioTransport(
      is_broadcaster_session(session_type) ? flush_broadcast_sink
                                           : flush_unicast_sink,
      std::move(stream_cb), {16000, ChannelMode::STEREO, 16, 0});
};

LeAudioSinkTransport::~LeAudioSinkTransport() { delete transport_; }

BluetoothAudioCtrlAck LeAudioSinkTransport::StartRequest(bool is_low_latency) {
  if (IS_FLAG_ENABLED(leaudio_start_stream_race_fix)) {
    return transport_->StartRequestV2(is_low_latency);
  }
  return transport_->StartRequest(is_low_latency);
}

BluetoothAudioCtrlAck LeAudioSinkTransport::SuspendRequest() {
  return transport_->SuspendRequest();
}

void LeAudioSinkTransport::StopRequest() { transport_->StopRequest(); }

void LeAudioSinkTransport::SetLatencyMode(LatencyMode latency_mode) {
  transport_->SetLatencyMode(latency_mode);
}

bool LeAudioSinkTransport::GetPresentationPosition(
    uint64_t* remote_delay_report_ns, uint64_t* total_bytes_read,
    timespec* data_position) {
  return transport_->GetPresentationPosition(remote_delay_report_ns,
                                             total_bytes_read, data_position);
}

void LeAudioSinkTransport::SourceMetadataChanged(
    const source_metadata_v7_t& source_metadata) {
  transport_->SourceMetadataChanged(source_metadata);
}

void LeAudioSinkTransport::SinkMetadataChanged(
    const sink_metadata_v7_t& sink_metadata) {
  transport_->SinkMetadataChanged(sink_metadata);
}

void LeAudioSinkTransport::ResetPresentationPosition() {
  transport_->ResetPresentationPosition();
}

void LeAudioSinkTransport::LogBytesRead(size_t bytes_read) {
  transport_->LogBytesProcessed(bytes_read);
}

void LeAudioSinkTransport::SetRemoteDelay(uint16_t delay_report_ms) {
  transport_->SetRemoteDelay(delay_report_ms);
}

const PcmConfiguration& LeAudioSinkTransport::LeAudioGetSelectedHalPcmConfig() {
  return transport_->LeAudioGetSelectedHalPcmConfig();
}

void LeAudioSinkTransport::LeAudioSetSelectedHalPcmConfig(
    uint32_t sample_rate_hz, uint8_t bit_rate, uint8_t channels_count,
    uint32_t data_interval) {
  transport_->LeAudioSetSelectedHalPcmConfig(sample_rate_hz, bit_rate,
                                             channels_count, data_interval);
}

void LeAudioSinkTransport::LeAudioSetBroadcastConfig(
    const ::le_audio::broadcast_offload_config& offload_config) {
  transport_->LeAudioSetBroadcastConfig(offload_config);
}

const LeAudioBroadcastConfiguration&
LeAudioSinkTransport::LeAudioGetBroadcastConfig() {
  return transport_->LeAudioGetBroadcastConfig();
}

bool LeAudioSinkTransport::IsRequestCompletedAfterUpdate(
    const std::function<std::pair<StartRequestState, bool>(StartRequestState)>&
        lambda) {
  return transport_->IsRequestCompletedAfterUpdate(lambda);
}

StartRequestState LeAudioSinkTransport::GetStartRequestState(void) {
  return transport_->GetStartRequestState();
}
void LeAudioSinkTransport::ClearStartRequestState(void) {
  transport_->ClearStartRequestState();
}
void LeAudioSinkTransport::SetStartRequestState(StartRequestState state) {
  transport_->SetStartRequestState(state);
}

void flush_source() {
  if (LeAudioSourceTransport::interface == nullptr) return;

  LeAudioSourceTransport::interface->FlushAudioData();
}

LeAudioSourceTransport::LeAudioSourceTransport(SessionType session_type,
                                               StreamCallbacks stream_cb)
    : IBluetoothSourceTransportInstance(session_type, (AudioConfiguration){}) {
  transport_ = new LeAudioTransport(flush_source, std::move(stream_cb),
                                    {16000, ChannelMode::STEREO, 16, 0});
};

LeAudioSourceTransport::~LeAudioSourceTransport() { delete transport_; }

BluetoothAudioCtrlAck LeAudioSourceTransport::StartRequest(
    bool is_low_latency) {
  if (IS_FLAG_ENABLED(leaudio_start_stream_race_fix)) {
    return transport_->StartRequestV2(is_low_latency);
  }
  return transport_->StartRequest(is_low_latency);
}

BluetoothAudioCtrlAck LeAudioSourceTransport::SuspendRequest() {
  return transport_->SuspendRequest();
}

void LeAudioSourceTransport::StopRequest() { transport_->StopRequest(); }

void LeAudioSourceTransport::SetLatencyMode(LatencyMode latency_mode) {
  transport_->SetLatencyMode(latency_mode);
}

bool LeAudioSourceTransport::GetPresentationPosition(
    uint64_t* remote_delay_report_ns, uint64_t* total_bytes_written,
    timespec* data_position) {
  return transport_->GetPresentationPosition(
      remote_delay_report_ns, total_bytes_written, data_position);
}

void LeAudioSourceTransport::SourceMetadataChanged(
    const source_metadata_v7_t& source_metadata) {
  transport_->SourceMetadataChanged(source_metadata);
}

void LeAudioSourceTransport::SinkMetadataChanged(
    const sink_metadata_v7_t& sink_metadata) {
  transport_->SinkMetadataChanged(sink_metadata);
}

void LeAudioSourceTransport::ResetPresentationPosition() {
  transport_->ResetPresentationPosition();
}

void LeAudioSourceTransport::LogBytesWritten(size_t bytes_written) {
  transport_->LogBytesProcessed(bytes_written);
}

void LeAudioSourceTransport::SetRemoteDelay(uint16_t delay_report_ms) {
  transport_->SetRemoteDelay(delay_report_ms);
}

const PcmConfiguration&
LeAudioSourceTransport::LeAudioGetSelectedHalPcmConfig() {
  return transport_->LeAudioGetSelectedHalPcmConfig();
}

void LeAudioSourceTransport::LeAudioSetSelectedHalPcmConfig(
    uint32_t sample_rate_hz, uint8_t bit_rate, uint8_t channels_count,
    uint32_t data_interval) {
  transport_->LeAudioSetSelectedHalPcmConfig(sample_rate_hz, bit_rate,
                                             channels_count, data_interval);
}

bool LeAudioSourceTransport::IsRequestCompletedAfterUpdate(
    const std::function<std::pair<StartRequestState, bool>(StartRequestState)>&
        lambda) {
  return transport_->IsRequestCompletedAfterUpdate(lambda);
}

StartRequestState LeAudioSourceTransport::GetStartRequestState(void) {
  return transport_->GetStartRequestState();
}
void LeAudioSourceTransport::ClearStartRequestState(void) {
  transport_->ClearStartRequestState();
}

void LeAudioSourceTransport::SetStartRequestState(StartRequestState state) {
  transport_->SetStartRequestState(state);
}

std::unordered_map<int32_t, uint8_t> sampling_freq_map{
    {8000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq8000Hz},
    {16000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq16000Hz},
    {24000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq24000Hz},
    {32000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq32000Hz},
    {44100, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq44100Hz},
    {48000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq48000Hz},
    {88200, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq88200Hz},
    {96000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq96000Hz},
    {176400, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq176400Hz},
    {192000, ::le_audio::codec_spec_conf::kLeAudioSamplingFreq192000Hz}};

std::unordered_map<int32_t, uint8_t> frame_duration_map{
    {7500, ::le_audio::codec_spec_conf::kLeAudioCodecFrameDur7500us},
    {10000, ::le_audio::codec_spec_conf::kLeAudioCodecFrameDur10000us}};

std::unordered_map<int32_t, uint16_t> octets_per_frame_map{
    {30, ::le_audio::codec_spec_conf::kLeAudioCodecFrameLen30},
    {40, ::le_audio::codec_spec_conf::kLeAudioCodecFrameLen40},
    {60, ::le_audio::codec_spec_conf::kLeAudioCodecFrameLen60},
    {80, ::le_audio::codec_spec_conf::kLeAudioCodecFrameLen80},
    {100, ::le_audio::codec_spec_conf::kLeAudioCodecFrameLen100},
    {120, ::le_audio::codec_spec_conf::kLeAudioCodecFrameLen120}};

std::unordered_map<AudioLocation, uint32_t> audio_location_map{
    {AudioLocation::UNKNOWN,
     ::le_audio::codec_spec_conf::kLeAudioLocationFrontCenter},
    {AudioLocation::FRONT_LEFT,
     ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft},
    {AudioLocation::FRONT_RIGHT,
     ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight},
    {static_cast<AudioLocation>(
         static_cast<uint8_t>(AudioLocation::FRONT_LEFT) |
         static_cast<uint8_t>(AudioLocation::FRONT_RIGHT)),
     ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft |
         ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight}};

bool hal_ucast_capability_to_stack_format(
    const UnicastCapability& hal_capability,
    CodecConfigSetting& stack_capability) {
  if (hal_capability.codecType != CodecType::LC3) {
    LOG(WARNING) << "Unsupported codecType: "
                 << toString(hal_capability.codecType);
    return false;
  }
  if (hal_capability.leAudioCodecCapabilities.getTag() !=
      UnicastCapability::LeAudioCodecCapabilities::lc3Capabilities) {
    LOG(WARNING) << "Unknown LE Audio capabilities(vendor proprietary?)";
    return false;
  }

  auto& hal_lc3_capability =
      hal_capability.leAudioCodecCapabilities
          .get<UnicastCapability::LeAudioCodecCapabilities::lc3Capabilities>();
  auto supported_channel = hal_capability.supportedChannel;
  auto sample_rate_hz = hal_lc3_capability.samplingFrequencyHz[0];
  auto frame_duration_us = hal_lc3_capability.frameDurationUs[0];
  auto octets_per_frame = hal_lc3_capability.octetsPerFrame[0];
  auto channel_count = hal_capability.channelCountPerDevice;

  if (sampling_freq_map.find(sample_rate_hz) == sampling_freq_map.end() ||
      frame_duration_map.find(frame_duration_us) == frame_duration_map.end() ||
      octets_per_frame_map.find(octets_per_frame) ==
          octets_per_frame_map.end() ||
      audio_location_map.find(supported_channel) == audio_location_map.end()) {
    LOG(ERROR) << __func__ << ": Failed to convert HAL format to stack format"
               << "\nsample rate hz = " << sample_rate_hz
               << "\nframe duration us = " << frame_duration_us
               << "\noctets per frame= " << octets_per_frame
               << "\nsupported channel = " << toString(supported_channel)
               << "\nchannel count per device = " << channel_count
               << "\ndevice count = " << hal_capability.deviceCount;

    return false;
  }

  stack_capability.id = ::le_audio::set_configurations::LeAudioCodecIdLc3;
  stack_capability.channel_count_per_iso_stream = channel_count;

  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeSamplingFreq,
      sampling_freq_map[sample_rate_hz]);
  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeFrameDuration,
      frame_duration_map[frame_duration_us]);
  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
      audio_location_map[supported_channel]);
  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame,
      octets_per_frame_map[octets_per_frame]);
  return true;
}

bool hal_bcast_capability_to_stack_format(
    const BroadcastCapability& hal_bcast_capability,
    CodecConfigSetting& stack_capability) {
  if (hal_bcast_capability.codecType != CodecType::LC3) {
    LOG(WARNING) << "Unsupported codecType: "
                 << toString(hal_bcast_capability.codecType);
    return false;
  }
  if (hal_bcast_capability.leAudioCodecCapabilities.getTag() !=
      BroadcastCapability::LeAudioCodecCapabilities::lc3Capabilities) {
    LOG(WARNING) << "Unknown LE Audio capabilities(vendor proprietary?)";
    return false;
  }

  auto& hal_lc3_capabilities =
      hal_bcast_capability.leAudioCodecCapabilities.get<
          BroadcastCapability::LeAudioCodecCapabilities::lc3Capabilities>();

  if (hal_lc3_capabilities->size() != 1) {
    LOG(WARNING) << __func__ << ": The number of config is not supported yet.";
  }

  auto supported_channel = hal_bcast_capability.supportedChannel;
  auto sample_rate_hz = (*hal_lc3_capabilities)[0]->samplingFrequencyHz[0];
  auto frame_duration_us = (*hal_lc3_capabilities)[0]->frameDurationUs[0];
  auto octets_per_frame = (*hal_lc3_capabilities)[0]->octetsPerFrame[0];
  auto channel_count = hal_bcast_capability.channelCountPerStream;

  if (sampling_freq_map.find(sample_rate_hz) == sampling_freq_map.end() ||
      frame_duration_map.find(frame_duration_us) == frame_duration_map.end() ||
      octets_per_frame_map.find(octets_per_frame) ==
          octets_per_frame_map.end() ||
      audio_location_map.find(supported_channel) == audio_location_map.end()) {
    LOG(WARNING) << __func__
                 << " : Failed to convert HAL format to stack format"
                 << "\nsample rate hz = " << sample_rate_hz
                 << "\nframe duration us = " << frame_duration_us
                 << "\noctets per frame= " << octets_per_frame
                 << "\nsupported channel = " << toString(supported_channel)
                 << "\nchannel count per stream = " << channel_count;

    return false;
  }

  stack_capability.id = ::le_audio::set_configurations::LeAudioCodecIdLc3;
  stack_capability.channel_count_per_iso_stream = channel_count;

  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeSamplingFreq,
      sampling_freq_map[sample_rate_hz]);
  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeFrameDuration,
      frame_duration_map[frame_duration_us]);
  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeAudioChannelAllocation,
      audio_location_map[supported_channel]);
  stack_capability.params.Add(
      ::le_audio::codec_spec_conf::kLeAudioLtvTypeOctetsPerCodecFrame,
      octets_per_frame_map[octets_per_frame]);
  return true;
}

std::vector<AudioSetConfiguration> get_offload_capabilities() {
  LOG(INFO) << __func__;
  std::vector<AudioSetConfiguration> offload_capabilities;
  std::vector<AudioCapabilities> le_audio_hal_capabilities =
      BluetoothAudioSinkClientInterface::GetAudioCapabilities(
          SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH);
  std::string str_capability_log;

  for (auto hal_cap : le_audio_hal_capabilities) {
    CodecConfigSetting encode_cap, decode_cap, bcast_cap;
    UnicastCapability hal_encode_cap =
        hal_cap.get<AudioCapabilities::leAudioCapabilities>()
            .unicastEncodeCapability;
    UnicastCapability hal_decode_cap =
        hal_cap.get<AudioCapabilities::leAudioCapabilities>()
            .unicastDecodeCapability;
    BroadcastCapability hal_bcast_cap =
        hal_cap.get<AudioCapabilities::leAudioCapabilities>()
            .broadcastCapability;
    AudioSetConfiguration audio_set_config = {.name = "offload capability"};
    str_capability_log.clear();

    if (hal_ucast_capability_to_stack_format(hal_encode_cap, encode_cap)) {
      audio_set_config.confs.push_back(SetConfiguration(
          ::le_audio::types::kLeAudioDirectionSink, hal_encode_cap.deviceCount,
          hal_encode_cap.deviceCount * hal_encode_cap.channelCountPerDevice,
          encode_cap));
      str_capability_log = " Encode Capability: " + hal_encode_cap.toString();
    }

    if (hal_ucast_capability_to_stack_format(hal_decode_cap, decode_cap)) {
      audio_set_config.confs.push_back(SetConfiguration(
          ::le_audio::types::kLeAudioDirectionSource,
          hal_decode_cap.deviceCount,
          hal_decode_cap.deviceCount * hal_decode_cap.channelCountPerDevice,
          decode_cap));
      str_capability_log += " Decode Capability: " + hal_decode_cap.toString();
    }

    if (hal_bcast_capability_to_stack_format(hal_bcast_cap, bcast_cap)) {
      // Set device_cnt, ase_cnt to zero to ignore these fields for broadcast
      audio_set_config.confs.push_back(SetConfiguration(
          ::le_audio::types::kLeAudioDirectionSink, 0, 0, bcast_cap));
      str_capability_log +=
          " Broadcast Capability: " + hal_bcast_cap.toString();
    }

    if (!audio_set_config.confs.empty()) {
      offload_capabilities.push_back(audio_set_config);
      LOG(INFO) << __func__
                << ": Supported codec capability =" << str_capability_log;

    } else {
      LOG(INFO) << __func__
                << ": Unknown codec capability =" << hal_cap.toString();
    }
  }

  return offload_capabilities;
}

AudioConfiguration offload_config_to_hal_audio_config(
    const ::le_audio::offload_config& offload_config) {
  Lc3Configuration lc3_config{
      .pcmBitDepth = static_cast<int8_t>(offload_config.bits_per_sample),
      .samplingFrequencyHz = static_cast<int32_t>(offload_config.sampling_rate),
      .frameDurationUs = static_cast<int32_t>(offload_config.frame_duration),
      .octetsPerFrame = static_cast<int32_t>(offload_config.octets_per_frame),
      .blocksPerSdu = static_cast<int8_t>(offload_config.blocks_per_sdu),
  };
  LeAudioConfiguration ucast_config = {
      .peerDelayUs = static_cast<int32_t>(offload_config.peer_delay_ms * 1000),
      .leAudioCodecConfig = LeAudioCodecConfiguration(lc3_config)};

  for (auto& [handle, location, state] : offload_config.stream_map) {
    ucast_config.streamMap.push_back({
        .streamHandle = handle,
        .audioChannelAllocation = static_cast<int32_t>(location),
        .isStreamActive = state,
    });
  }

  return AudioConfiguration(ucast_config);
}

}  // namespace le_audio
}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
