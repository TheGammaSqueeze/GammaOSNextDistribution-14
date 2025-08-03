/******************************************************************************
 *
 * Copyright 2019 HIMSA II K/S - www.himsa.com.Represented by EHIMA -
 * www.ehima.com
 * Copyright (c) 2022 The Android Open Source Project
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
 ******************************************************************************/

#include "audio_hal_client.h"
#include "audio_hal_interface/le_audio_software.h"
#include "bta/le_audio/codec_manager.h"
#include "common/time_util.h"
#include "os/log.h"
#include "osi/include/wakelock.h"
#include "stack/include/main_thread.h"

using bluetooth::audio::le_audio::LeAudioClientInterface;

namespace le_audio {
namespace {
// TODO: HAL state should be in the HAL implementation
enum {
  HAL_UNINITIALIZED,
  HAL_STOPPED,
  HAL_STARTED,
} le_audio_source_hal_state;

class SinkImpl : public LeAudioSinkAudioHalClient {
 public:
  // Interface implementation
  bool Start(const LeAudioCodecConfiguration& codecConfiguration,
             LeAudioSinkAudioHalClient::Callbacks* audioReceiver,
             DsaModes dsa_modes) override;
  void Stop();
  size_t SendData(uint8_t* data, uint16_t size) override;
  void ConfirmStreamingRequest() override;
  void CancelStreamingRequest() override;
  void UpdateRemoteDelay(uint16_t remote_delay_ms) override;
  void UpdateAudioConfigToHal(
      const ::le_audio::offload_config& config) override;
  void SuspendedForReconfiguration() override;
  void ReconfigurationComplete() override;

  // Internal functionality
  SinkImpl() = default;
  ~SinkImpl() override {
    if (le_audio_source_hal_state != HAL_UNINITIALIZED) Release();
  }

  bool OnResumeReq(bool start_media_task);
  bool OnSuspendReq();
  bool OnMetadataUpdateReq(const sink_metadata_v7_t& sink_metadata);
  bool Acquire();
  void Release();

  bluetooth::audio::le_audio::LeAudioClientInterface::Source*
      halSourceInterface_ = nullptr;
  LeAudioSinkAudioHalClient::Callbacks* audioSinkCallbacks_ = nullptr;
};

bool SinkImpl::Acquire() {
  auto source_stream_cb = bluetooth::audio::le_audio::StreamCallbacks{
      .on_resume_ =
          std::bind(&SinkImpl::OnResumeReq, this, std::placeholders::_1),
      .on_suspend_ = std::bind(&SinkImpl::OnSuspendReq, this),
      .on_sink_metadata_update_ = std::bind(&SinkImpl::OnMetadataUpdateReq,
                                            this, std::placeholders::_1),
  };

  auto halInterface = LeAudioClientInterface::Get();
  if (halInterface == nullptr) {
    LOG_ERROR("Can't get LE Audio HAL interface");
    return false;
  }

  halSourceInterface_ =
      halInterface->GetSource(source_stream_cb, get_main_thread());

  if (halSourceInterface_ == nullptr) {
    LOG_ERROR("Can't get Audio HAL Audio source interface");
    return false;
  }

  LOG_INFO();
  le_audio_source_hal_state = HAL_STOPPED;
  return true;
}

void SinkImpl::Release() {
  if (le_audio_source_hal_state == HAL_UNINITIALIZED) {
    LOG_WARN("Audio HAL Audio source is not running");
    return;
  }

  LOG_INFO();
  if (halSourceInterface_) {
    halSourceInterface_->Cleanup();

    auto halInterface = LeAudioClientInterface::Get();
    if (halInterface != nullptr) {
      halInterface->ReleaseSource(halSourceInterface_);
    } else {
      LOG_ERROR("Can't get LE Audio HAL interface");
    }

    le_audio_source_hal_state = HAL_UNINITIALIZED;
    halSourceInterface_ = nullptr;
  }
}

bool SinkImpl::OnResumeReq(bool start_media_task) {
  if (audioSinkCallbacks_ == nullptr) {
    LOG_ERROR("audioSinkCallbacks_ not set");
    return false;
  }

  bt_status_t status = do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioSinkAudioHalClient::Callbacks::OnAudioResume,
                     audioSinkCallbacks_->weak_factory_.GetWeakPtr()));
  if (status == BT_STATUS_SUCCESS) {
    return true;
  }

  LOG_ERROR("do_in_main_thread err=%d", status);
  return false;
}

bool SinkImpl::OnSuspendReq() {
  if (audioSinkCallbacks_ == nullptr) {
    LOG_ERROR("audioSinkCallbacks_ not set");
    return false;
  }

  bt_status_t status = do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioSinkAudioHalClient::Callbacks::OnAudioSuspend,
                     audioSinkCallbacks_->weak_factory_.GetWeakPtr()));
  if (status == BT_STATUS_SUCCESS) {
    return true;
  }

  LOG_ERROR("do_in_main_thread err=%d", status);
  return false;
}

bool SinkImpl::OnMetadataUpdateReq(const sink_metadata_v7_t& sink_metadata) {
  if (audioSinkCallbacks_ == nullptr) {
    LOG_ERROR("audioSinkCallbacks_ not set");
    return false;
  }

  std::vector<struct record_track_metadata_v7> metadata(
      sink_metadata.tracks, sink_metadata.tracks + sink_metadata.track_count);

  bt_status_t status = do_in_main_thread(
      FROM_HERE,
      base::BindOnce(
          &LeAudioSinkAudioHalClient::Callbacks::OnAudioMetadataUpdate,
          audioSinkCallbacks_->weak_factory_.GetWeakPtr(),
          std::move(metadata)));
  if (status == BT_STATUS_SUCCESS) {
    return true;
  }

  LOG_ERROR("do_in_main_thread err=%d", status);
  return false;
}

bool SinkImpl::Start(const LeAudioCodecConfiguration& codec_configuration,
                     LeAudioSinkAudioHalClient::Callbacks* audioReceiver,
                     DsaModes dsa_modes) {
  if (!halSourceInterface_) {
    LOG_ERROR("Audio HAL Audio source interface not acquired");
    return false;
  }

  if (le_audio_source_hal_state == HAL_STARTED) {
    LOG_ERROR("Audio HAL Audio source is already in use");
    return false;
  }

  LOG_INFO("bit rate: %d, num channels: %d, sample rate: %d, data interval: %d",
           codec_configuration.bits_per_sample,
           codec_configuration.num_channels, codec_configuration.sample_rate,
           codec_configuration.data_interval_us);

  LeAudioClientInterface::PcmParameters pcmParameters = {
      .data_interval_us = codec_configuration.data_interval_us,
      .sample_rate = codec_configuration.sample_rate,
      .bits_per_sample = codec_configuration.bits_per_sample,
      .channels_count = codec_configuration.num_channels};

  halSourceInterface_->SetPcmParameters(pcmParameters);
  LeAudioClientInterface::Get()->SetAllowedDsaModes(dsa_modes);
  halSourceInterface_->StartSession();

  audioSinkCallbacks_ = audioReceiver;
  le_audio_source_hal_state = HAL_STARTED;
  return true;
}

void SinkImpl::Stop() {
  if (!halSourceInterface_) {
    LOG_ERROR("Audio HAL Audio source interface already stopped");
    return;
  }

  if (le_audio_source_hal_state != HAL_STARTED) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();

  halSourceInterface_->StopSession();
  le_audio_source_hal_state = HAL_STOPPED;
  audioSinkCallbacks_ = nullptr;
}

size_t SinkImpl::SendData(uint8_t* data, uint16_t size) {
  size_t bytes_written;
  if (!halSourceInterface_) {
    LOG_ERROR("Audio HAL Audio source interface not initialized");
    return 0;
  }

  if (le_audio_source_hal_state != HAL_STARTED) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return 0;
  }

  /* TODO: What to do if not all data is written ? */
  bytes_written = halSourceInterface_->Write(data, size);
  if (bytes_written != size) {
    LOG_ERROR(
        "Not all data is written to source HAL. Bytes written: %zu, total: %d",
        bytes_written, size);
  }

  return bytes_written;
}

void SinkImpl::ConfirmStreamingRequest() {
  if ((halSourceInterface_ == nullptr) ||
      (le_audio_source_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();
  if (IS_FLAG_ENABLED(leaudio_start_stream_race_fix)) {
    halSourceInterface_->ConfirmStreamingRequestV2();
  } else {
    halSourceInterface_->ConfirmStreamingRequest();
  }
}

void SinkImpl::SuspendedForReconfiguration() {
  if ((halSourceInterface_ == nullptr) ||
      (le_audio_source_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();
  halSourceInterface_->SuspendedForReconfiguration();
}

void SinkImpl::ReconfigurationComplete() {
  if ((halSourceInterface_ == nullptr) ||
      (le_audio_source_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();
  halSourceInterface_->ReconfigurationComplete();
}

void SinkImpl::CancelStreamingRequest() {
  if ((halSourceInterface_ == nullptr) ||
      (le_audio_source_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();
  if (IS_FLAG_ENABLED(leaudio_start_stream_race_fix)) {
    halSourceInterface_->CancelStreamingRequestV2();
  } else {
    halSourceInterface_->CancelStreamingRequest();
  }
}

void SinkImpl::UpdateRemoteDelay(uint16_t remote_delay_ms) {
  if ((halSourceInterface_ == nullptr) ||
      (le_audio_source_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();
  halSourceInterface_->SetRemoteDelay(remote_delay_ms);
}

void SinkImpl::UpdateAudioConfigToHal(
    const ::le_audio::offload_config& config) {
  if ((halSourceInterface_ == nullptr) ||
      (le_audio_source_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio source was not started!");
    return;
  }

  LOG_INFO();
  halSourceInterface_->UpdateAudioConfigToHal(config);
}
}  // namespace

std::unique_ptr<LeAudioSinkAudioHalClient>
LeAudioSinkAudioHalClient::AcquireUnicast() {
  std::unique_ptr<SinkImpl> impl(new SinkImpl());
  if (!impl->Acquire()) {
    LOG_ERROR("Could not acquire Unicast Sink on LE Audio HAL enpoint");
    impl.reset();
    return nullptr;
  }

  LOG_INFO();
  return std::move(impl);
}

void LeAudioSinkAudioHalClient::DebugDump(int fd) {
  /* TODO: Add some statistic for LeAudioSink Audio HAL interface */
}
}  // namespace le_audio
