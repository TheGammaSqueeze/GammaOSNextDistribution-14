/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
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

#include <hardware/bt_le_audio.h>

#include <bitset>
#include <vector>

#include "le_audio_types.h"

namespace le_audio {
namespace utils {
types::LeAudioContextType AudioContentToLeAudioContext(
    audio_content_type_t content_type, audio_usage_t usage);
types::AudioContexts GetAudioContextsFromSourceMetadata(
    const std::vector<struct playback_track_metadata_v7>& source_metadata);
types::AudioContexts GetAudioContextsFromSinkMetadata(
    const std::vector<struct record_track_metadata_v7>& sink_metadata);

/* Helpers to get btle_audio_codec_config_t for Java */
bluetooth::le_audio::btle_audio_codec_index_t
translateBluetoothCodecFormatToCodecType(uint8_t codec_format);

bluetooth::le_audio::btle_audio_sample_rate_index_t
translateToBtLeAudioCodecConfigSampleRate(uint32_t sample_rate_capa);
bluetooth::le_audio::btle_audio_bits_per_sample_index_t
translateToBtLeAudioCodecConfigBitPerSample(uint8_t bits_per_sample);
bluetooth::le_audio::btle_audio_channel_count_index_t
translateToBtLeAudioCodecConfigChannelCount(uint8_t channel_count);
bluetooth::le_audio::btle_audio_frame_duration_index_t
translateToBtLeAudioCodecConfigFrameDuration(int frame_duration);
void fillStreamParamsToBtLeAudioCodecConfig(
    types::LeAudioCodecId codec_id, const stream_parameters* stream_params,
    bluetooth::le_audio::btle_audio_codec_config_t& out_config);

std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
GetRemoteBtLeAudioCodecConfigFromPac(
    const types::PublishedAudioCapabilities& group_pacs);
}  // namespace utils
}  // namespace le_audio
