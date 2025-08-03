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

#define LOG_TAG "BTAudioLeAudioHIDL"

#include "le_audio_software_hidl.h"

#include "os/log.h"

namespace bluetooth {
namespace audio {
namespace hidl {
namespace le_audio {

using ::android::hardware::bluetooth::audio::V2_0::BitsPerSample;
using ::android::hardware::bluetooth::audio::V2_0::ChannelMode;
using ::android::hardware::bluetooth::audio::V2_0::CodecType;
using ::bluetooth::audio::hidl::SampleRate_2_1;
using ::bluetooth::audio::hidl::SessionType;
using ::bluetooth::audio::hidl::SessionType_2_1;

using ::bluetooth::audio::le_audio::LeAudioClientInterface;
using ::bluetooth::audio::le_audio::StartRequestState;
using ::le_audio::DsaMode;

/**
 * Helper utils
 **/

static SampleRate_2_1 le_audio_sample_rate2audio_hal(uint32_t sample_rate_2_1) {
  switch (sample_rate_2_1) {
    case 8000:
      return SampleRate_2_1::RATE_8000;
    case 16000:
      return SampleRate_2_1::RATE_16000;
    case 24000:
      return SampleRate_2_1::RATE_24000;
    case 32000:
      return SampleRate_2_1::RATE_32000;
    case 44100:
      return SampleRate_2_1::RATE_44100;
    case 48000:
      return SampleRate_2_1::RATE_48000;
    case 88200:
      return SampleRate_2_1::RATE_88200;
    case 96000:
      return SampleRate_2_1::RATE_96000;
    case 176400:
      return SampleRate_2_1::RATE_176400;
    case 192000:
      return SampleRate_2_1::RATE_192000;
  };
  return SampleRate_2_1::RATE_UNKNOWN;
}

static BitsPerSample le_audio_bits_per_sample2audio_hal(
    uint8_t bits_per_sample) {
  switch (bits_per_sample) {
    case 16:
      return BitsPerSample::BITS_16;
    case 24:
      return BitsPerSample::BITS_24;
    case 32:
      return BitsPerSample::BITS_32;
  };
  return BitsPerSample::BITS_UNKNOWN;
}

static ChannelMode le_audio_channel_mode2audio_hal(uint8_t channels_count) {
  switch (channels_count) {
    case 1:
      return ChannelMode::MONO;
    case 2:
      return ChannelMode::STEREO;
  }
  return ChannelMode::UNKNOWN;
}

bool is_source_hal_enabled() {
  return LeAudioSourceTransport::interface != nullptr;
}

bool is_sink_hal_enabled() {
  return LeAudioSinkTransport::interface != nullptr;
}

LeAudioTransport::LeAudioTransport(void (*flush)(void),
                                   StreamCallbacks stream_cb,
                                   PcmParameters pcm_config)
    : flush_(std::move(flush)),
      stream_cb_(std::move(stream_cb)),
      remote_delay_report_ms_(0),
      total_bytes_processed_(0),
      data_position_({}),
      pcm_config_(std::move(pcm_config)),
      start_request_state_(StartRequestState::IDLE){};

BluetoothAudioCtrlAck LeAudioTransport::StartRequest() {
  SetStartRequestState(StartRequestState::PENDING_BEFORE_RESUME);
  if (stream_cb_.on_resume_(true)) {
    if (start_request_state_ == StartRequestState::CONFIRMED) {
      LOG_INFO("Start completed.");
      SetStartRequestState(StartRequestState::IDLE);
      return BluetoothAudioCtrlAck::SUCCESS_FINISHED;
    }

    if (start_request_state_ == StartRequestState::CANCELED) {
      LOG_INFO("Start request failed.");
      SetStartRequestState(StartRequestState::IDLE);
      return BluetoothAudioCtrlAck::FAILURE;
    }

    LOG_INFO("Start pending.");
    SetStartRequestState(StartRequestState::PENDING_AFTER_RESUME);
    return BluetoothAudioCtrlAck::PENDING;
  }

  LOG_ERROR("Start request failed.");
  SetStartRequestState(StartRequestState::IDLE);
  return BluetoothAudioCtrlAck::FAILURE;
}

BluetoothAudioCtrlAck LeAudioTransport::StartRequestV2() {
  SetStartRequestState(StartRequestState::PENDING_BEFORE_RESUME);
  if (stream_cb_.on_resume_(true)) {
    std::lock_guard<std::mutex> guard(start_request_state_mutex_);
    if (start_request_state_ == StartRequestState::CONFIRMED) {
      LOG_INFO("Start completed.");
      SetStartRequestState(StartRequestState::IDLE);
      return BluetoothAudioCtrlAck::SUCCESS_FINISHED;
    }

    if (start_request_state_ == StartRequestState::CANCELED) {
      LOG_INFO("Start request failed.");
      SetStartRequestState(StartRequestState::IDLE);
      return BluetoothAudioCtrlAck::FAILURE;
    }

    LOG_INFO("Start pending.");
    SetStartRequestState(StartRequestState::PENDING_AFTER_RESUME);
    return BluetoothAudioCtrlAck::PENDING;
  }

  LOG_ERROR("Start request failed.");
  SetStartRequestState(StartRequestState::IDLE);
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

void LeAudioTransport::MetadataChanged(
    const source_metadata_t& source_metadata) {
  auto track_count = source_metadata.track_count;

  if (track_count == 0) {
    LOG(WARNING) << ", invalid number of metadata changed tracks";
    return;
  }
  std::vector<playback_track_metadata_v7> tracks_vec;
  tracks_vec.reserve(track_count);
  for (size_t i = 0; i < track_count; i++) {
    tracks_vec.push_back({
        .base =
            {
                .usage = source_metadata.tracks[i].usage,
                .content_type = source_metadata.tracks[i].content_type,
                .gain = source_metadata.tracks[i].gain,
            },
    });
  }
  const source_metadata_v7_t source_metadata_v7 = {
      .track_count = tracks_vec.size(), .tracks = tracks_vec.data()};

  stream_cb_.on_metadata_update_(source_metadata_v7, DsaMode::DISABLED);
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

const PcmParameters& LeAudioTransport::LeAudioGetSelectedHalPcmConfig() {
  return pcm_config_;
}

void LeAudioTransport::LeAudioSetSelectedHalPcmConfig(uint32_t sample_rate_hz,
                                                      uint8_t bit_rate,
                                                      uint8_t channels_count,
                                                      uint32_t data_interval) {
  pcm_config_.sampleRate = le_audio_sample_rate2audio_hal(sample_rate_hz);
  pcm_config_.bitsPerSample = le_audio_bits_per_sample2audio_hal(bit_rate);
  pcm_config_.channelMode = le_audio_channel_mode2audio_hal(channels_count);
  pcm_config_.dataIntervalUs = data_interval;
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
  LOG_VERBOSE("new state: %d, return: %s",
              static_cast<int>(start_request_state_.load()),
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

void flush_sink() {
  if (!is_sink_hal_enabled()) return;

  LeAudioSinkTransport::interface->FlushAudioData();
}

LeAudioSinkTransport::LeAudioSinkTransport(SessionType_2_1 session_type,
                                           StreamCallbacks stream_cb)
    : IBluetoothSinkTransportInstance(session_type, {}) {
  transport_ =
      new LeAudioTransport(flush_sink, std::move(stream_cb),
                           {SampleRate_2_1::RATE_16000, ChannelMode::STEREO,
                            BitsPerSample::BITS_16, 0});
};

LeAudioSinkTransport::~LeAudioSinkTransport() { delete transport_; }

BluetoothAudioCtrlAck LeAudioSinkTransport::StartRequest() {
  if (IS_FLAG_ENABLED(leaudio_start_stream_race_fix)) {
    return transport_->StartRequestV2();
  }
  return transport_->StartRequest();
}

BluetoothAudioCtrlAck LeAudioSinkTransport::SuspendRequest() {
  return transport_->SuspendRequest();
}

void LeAudioSinkTransport::StopRequest() { transport_->StopRequest(); }

bool LeAudioSinkTransport::GetPresentationPosition(
    uint64_t* remote_delay_report_ns, uint64_t* total_bytes_read,
    timespec* data_position) {
  return transport_->GetPresentationPosition(remote_delay_report_ns,
                                             total_bytes_read, data_position);
}

void LeAudioSinkTransport::MetadataChanged(
    const source_metadata_t& source_metadata) {
  transport_->MetadataChanged(source_metadata);
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

const PcmParameters& LeAudioSinkTransport::LeAudioGetSelectedHalPcmConfig() {
  return transport_->LeAudioGetSelectedHalPcmConfig();
}

void LeAudioSinkTransport::LeAudioSetSelectedHalPcmConfig(
    uint32_t sample_rate_hz, uint8_t bit_rate, uint8_t channels_count,
    uint32_t data_interval) {
  transport_->LeAudioSetSelectedHalPcmConfig(sample_rate_hz, bit_rate,
                                             channels_count, data_interval);
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

LeAudioSourceTransport::LeAudioSourceTransport(SessionType_2_1 session_type,
                                               StreamCallbacks stream_cb)
    : IBluetoothSourceTransportInstance(session_type, {}) {
  transport_ =
      new LeAudioTransport(flush_source, std::move(stream_cb),
                           {SampleRate_2_1::RATE_16000, ChannelMode::MONO,
                            BitsPerSample::BITS_16, 0});
};

LeAudioSourceTransport::~LeAudioSourceTransport() { delete transport_; }

BluetoothAudioCtrlAck LeAudioSourceTransport::StartRequest() {
  if (IS_FLAG_ENABLED(leaudio_start_stream_race_fix)) {
    return transport_->StartRequestV2();
  }
  return transport_->StartRequest();
}

BluetoothAudioCtrlAck LeAudioSourceTransport::SuspendRequest() {
  return transport_->SuspendRequest();
}

void LeAudioSourceTransport::StopRequest() { transport_->StopRequest(); }

bool LeAudioSourceTransport::GetPresentationPosition(
    uint64_t* remote_delay_report_ns, uint64_t* total_bytes_written,
    timespec* data_position) {
  return transport_->GetPresentationPosition(
      remote_delay_report_ns, total_bytes_written, data_position);
}

void LeAudioSourceTransport::MetadataChanged(
    const source_metadata_t& source_metadata) {
  transport_->MetadataChanged(source_metadata);
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

const PcmParameters& LeAudioSourceTransport::LeAudioGetSelectedHalPcmConfig() {
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
}  // namespace le_audio
}  // namespace hidl
}  // namespace audio
}  // namespace bluetooth
