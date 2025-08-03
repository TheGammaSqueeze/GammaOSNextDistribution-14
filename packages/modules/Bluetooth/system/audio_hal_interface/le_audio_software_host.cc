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

#include "audio_hal_interface/le_audio_software.h"
#include "bta/le_audio/codec_manager.h"

namespace bluetooth {
namespace audio {

namespace le_audio {

std::vector<::le_audio::set_configurations::AudioSetConfiguration>
get_offload_capabilities() {
  return std::vector<::le_audio::set_configurations::AudioSetConfiguration>(0);
}

LeAudioClientInterface* LeAudioClientInterface::Get() { return nullptr; }

bool LeAudioClientInterface::IsUnicastSinkAcquired() { return false; }
bool LeAudioClientInterface::IsBroadcastSinkAcquired() { return false; }

bool LeAudioClientInterface::ReleaseSink(LeAudioClientInterface::Sink* sink) {
  return false;
}

LeAudioClientInterface::Sink* LeAudioClientInterface::GetSink(
    StreamCallbacks stream_cb,
    bluetooth::common::MessageLoopThread* message_loop,
    bool is_broadcasting_session_type) {
  return nullptr;
}

void LeAudioClientInterface::Sink::UpdateBroadcastAudioConfigToHal(
    ::le_audio::broadcast_offload_config const& config) {
  return;
}

size_t LeAudioClientInterface::Sink::Read(uint8_t* p_buf, uint32_t len) {
  return 0;
}

LeAudioClientInterface::Source* LeAudioClientInterface::GetSource(
    StreamCallbacks stream_cb,
    bluetooth::common::MessageLoopThread* message_loop) {
  return nullptr;
}

size_t LeAudioClientInterface::Source::Write(const uint8_t* p_buf,
                                             uint32_t len) {
  return 0;
}

bool LeAudioClientInterface::IsSourceAcquired() { return false; }

bool LeAudioClientInterface::ReleaseSource(
    LeAudioClientInterface::Source* source) {
  return false;
}

void LeAudioClientInterface::SetAllowedDsaModes(DsaModes dsa_modes) { return; }

}  // namespace le_audio
}  // namespace audio
}  // namespace bluetooth
