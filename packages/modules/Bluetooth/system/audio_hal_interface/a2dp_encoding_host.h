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

#include <stdint.h>
#include <time.h>

#include "include/hardware/bt_av.h"

namespace bluetooth {
namespace audio {
namespace a2dp {

// Audio config from audio server; PCM format for now
struct AudioConfig {
  btav_a2dp_codec_sample_rate_t sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
  btav_a2dp_codec_bits_per_sample_t bits_per_sample =
      BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
  btav_a2dp_codec_channel_mode_t channel_mode =
      BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO;
};

// Invoked by audio server to set audio config (PCM for now)
bool SetAudioConfig(AudioConfig);

// Invoked by audio server when it has audio data to stream.
// Returns whether the start request has been made successfully.
bool StartRequest();

// Invoked by audio server when audio streaming is done.
bool StopRequest();

// Invoked by audio server when audio streaming is suspended.
bool SuspendRequest();

struct PresentationPosition {
  uint64_t remote_delay_report_ns;
  uint64_t total_bytes_read;
  timespec data_position;
};

// Invoked by audio server to check audio presentation position periodically.
PresentationPosition GetPresentationPosition();

bool is_opus_supported();

}  // namespace a2dp
}  // namespace audio
}  // namespace bluetooth
