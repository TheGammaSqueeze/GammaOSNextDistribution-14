/*
 * Copyright 2021 The Android Open Source Project
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

#pragma once

#ifdef TARGET_FLOSS
#include <audio_hal_interface/audio_linux.h>
#else
#include <hardware/audio.h>
#endif

#include <android_bluetooth_flags.h>

#include <functional>

#include "bta/le_audio/codec_manager.h"
#include "bta/le_audio/le_audio_types.h"
#include "common/message_loop_thread.h"

namespace bluetooth {
namespace audio {
namespace le_audio {

using ::le_audio::DsaMode;
using ::le_audio::DsaModes;

enum class StartRequestState {
  IDLE = 0x00,
  PENDING_BEFORE_RESUME,
  PENDING_AFTER_RESUME,
  CONFIRMED,
  CANCELED,
};

constexpr uint8_t kChannelNumberMono = 1;
constexpr uint8_t kChannelNumberStereo = 2;

constexpr uint32_t kSampleRate48000 = 48000;
constexpr uint32_t kSampleRate44100 = 44100;
constexpr uint32_t kSampleRate32000 = 32000;
constexpr uint32_t kSampleRate24000 = 24000;
constexpr uint32_t kSampleRate16000 = 16000;
constexpr uint32_t kSampleRate8000 = 8000;

constexpr uint8_t kBitsPerSample16 = 16;
constexpr uint8_t kBitsPerSample24 = 24;
constexpr uint8_t kBitsPerSample32 = 32;

struct StreamCallbacks {
  std::function<bool(bool start_media_task)> on_resume_;
  std::function<bool(void)> on_suspend_;
  std::function<bool(const source_metadata_v7_t&, DsaMode)> on_metadata_update_;
  std::function<bool(const sink_metadata_v7_t&)> on_sink_metadata_update_;
};

std::vector<::le_audio::set_configurations::AudioSetConfiguration>
get_offload_capabilities();

class LeAudioClientInterface {
 public:
  struct PcmParameters {
    uint32_t data_interval_us;
    uint32_t sample_rate;
    uint8_t bits_per_sample;
    uint8_t channels_count;
  };

 private:
  class IClientInterfaceEndpoint {
   public:
    virtual ~IClientInterfaceEndpoint() = default;
    virtual void Cleanup() = 0;
    virtual void SetPcmParameters(const PcmParameters& params) = 0;
    virtual void SetRemoteDelay(uint16_t delay_report_ms) = 0;
    virtual void StartSession() = 0;
    virtual void StopSession() = 0;
    virtual void ConfirmStreamingRequest() = 0;
    virtual void CancelStreamingRequest() = 0;
    virtual void ConfirmStreamingRequestV2() = 0;
    virtual void CancelStreamingRequestV2() = 0;
    virtual void UpdateAudioConfigToHal(
        const ::le_audio::offload_config& config) = 0;
    virtual void SuspendedForReconfiguration() = 0;
    virtual void ReconfigurationComplete() = 0;
  };

 public:
  class Sink : public IClientInterfaceEndpoint {
   public:
    Sink(bool is_broadcaster = false) : is_broadcaster_(is_broadcaster){};
    virtual ~Sink() = default;

    void Cleanup() override;
    void SetPcmParameters(const PcmParameters& params) override;
    void SetRemoteDelay(uint16_t delay_report_ms) override;
    void StartSession() override;
    void StopSession() override;
    void ConfirmStreamingRequest() override;
    void CancelStreamingRequest() override;
    void ConfirmStreamingRequestV2() override;
    void CancelStreamingRequestV2() override;
    void UpdateAudioConfigToHal(
        const ::le_audio::offload_config& config) override;
    void UpdateBroadcastAudioConfigToHal(
        const ::le_audio::broadcast_offload_config& config);
    void SuspendedForReconfiguration() override;
    void ReconfigurationComplete() override;
    // Read the stream of bytes sinked to us by the upper layers
    size_t Read(uint8_t* p_buf, uint32_t len);
    bool IsBroadcaster() { return is_broadcaster_; }

   private:
    bool is_broadcaster_ = false;
  };
  class Source : public IClientInterfaceEndpoint {
   public:
    virtual ~Source() = default;

    void Cleanup() override;
    void SetPcmParameters(const PcmParameters& params) override;
    void SetRemoteDelay(uint16_t delay_report_ms) override;
    void StartSession() override;
    void StopSession() override;
    void ConfirmStreamingRequest() override;
    void CancelStreamingRequest() override;
    void ConfirmStreamingRequestV2() override;
    void CancelStreamingRequestV2() override;
    void UpdateAudioConfigToHal(
        const ::le_audio::offload_config& config) override;
    void SuspendedForReconfiguration() override;
    void ReconfigurationComplete() override;
    // Source the given stream of bytes to be sinked into the upper layers
    size_t Write(const uint8_t* p_buf, uint32_t len);
  };

  // Get LE Audio sink client interface if it's not previously acquired and not
  // yet released.
  Sink* GetSink(StreamCallbacks stream_cb,
                bluetooth::common::MessageLoopThread* message_loop,
                bool is_broadcasting_session_type);
  // This should be called before trying to get unicast sink interface
  bool IsUnicastSinkAcquired();
  // This should be called before trying to get broadcast sink interface
  bool IsBroadcastSinkAcquired();
  // Release sink interface if belongs to LE audio client interface
  bool ReleaseSink(Sink* sink);

  // Get LE Audio source client interface if it's not previously acquired and
  // not yet released.
  Source* GetSource(StreamCallbacks stream_cb,
                    bluetooth::common::MessageLoopThread* message_loop);
  // This should be called before trying to get source interface
  bool IsSourceAcquired();
  // Release source interface if belongs to LE audio client interface
  bool ReleaseSource(Source* source);

  // Sets Dynamic Spatial Audio modes supported by the remote device
  void SetAllowedDsaModes(DsaModes dsa_modes);

  // Get interface, if previously not initialized - it'll initialize
  // singleton.
  static LeAudioClientInterface* Get();

 private:
  static LeAudioClientInterface* interface;
  Sink* unicast_sink_ = nullptr;
  Sink* broadcast_sink_ = nullptr;
  Source* source_ = nullptr;
};

}  // namespace le_audio
}  // namespace audio
}  // namespace bluetooth
