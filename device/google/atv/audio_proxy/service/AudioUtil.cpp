// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "AudioUtil.h"

#include <system/audio.h>

namespace audio_proxy::service {
int computeFrameSize(const AidlAudioConfig& config) {
  audio_format_t format = static_cast<audio_format_t>(config.format);

  if (!audio_has_proportional_frames(format)) {
    return sizeof(int8_t);
  }

  size_t channelSampleSize = audio_bytes_per_sample(format);
  return audio_channel_count_from_out_mask(
             static_cast<audio_channel_mask_t>(config.channelMask)) *
         channelSampleSize;
}

int64_t computeBufferSizeBytes(const AidlAudioConfig& config,
                               int32_t bufferSizeMs) {
  return static_cast<int64_t>(bufferSizeMs) * config.sampleRateHz *
         computeFrameSize(config) / 1000;
}
}  // namespace audio_proxy::service