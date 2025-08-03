/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_TAG "a2dp_aac_encoder"

#include "a2dp_aac_encoder.h"

#include <aacenc_lib.h>
#include <bluetooth/log.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>

#include "a2dp_aac.h"
#include "common/time_util.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"

//
// Encoder for AAC Source Codec
//

// A2DP AAC encoder interval in milliseconds
#define A2DP_AAC_ENCODER_INTERVAL_MS 20

// offset
#define A2DP_AAC_OFFSET AVDT_MEDIA_OFFSET

using namespace bluetooth;

namespace fmt {
template <>
struct formatter<AACENC_ERROR> : enum_formatter<AACENC_ERROR> {};
}  // namespace fmt

typedef struct {
  uint32_t sample_rate;
  uint8_t channel_mode;
  uint8_t bits_per_sample;
  uint32_t frame_length;         // Samples per channel in a frame
  uint8_t input_channels_n;      // Number of channels
  int max_encoded_buffer_bytes;  // Max encoded bytes per frame
} tA2DP_AAC_ENCODER_PARAMS;

typedef struct {
  float counter;
  uint32_t bytes_per_tick; /* pcm bytes read each media task tick */
  uint64_t last_frame_us;
} tA2DP_AAC_FEEDING_STATE;

typedef struct {
  uint64_t session_start_us;

  size_t media_read_total_expected_packets;
  size_t media_read_total_expected_reads_count;
  size_t media_read_total_expected_read_bytes;

  size_t media_read_total_dropped_packets;
  size_t media_read_total_actual_reads_count;
  size_t media_read_total_actual_read_bytes;
} a2dp_aac_encoder_stats_t;

typedef struct {
  a2dp_source_read_callback_t read_callback;
  a2dp_source_enqueue_callback_t enqueue_callback;
  uint16_t TxAaMtuSize;

  bool use_SCMS_T;
  tA2DP_ENCODER_INIT_PEER_PARAMS peer_params;
  uint32_t timestamp;        // Timestamp for the A2DP frames

  HANDLE_AACENCODER aac_handle;
  bool has_aac_handle;  // True if aac_handle is valid

  tA2DP_FEEDING_PARAMS feeding_params;
  tA2DP_AAC_ENCODER_PARAMS aac_encoder_params;
  tA2DP_AAC_FEEDING_STATE aac_feeding_state;

  a2dp_aac_encoder_stats_t stats;
} tA2DP_AAC_ENCODER_CB;

static tA2DP_AAC_ENCODER_CB a2dp_aac_encoder_cb;

static uint32_t a2dp_aac_encoder_interval_ms = A2DP_AAC_ENCODER_INTERVAL_MS;

static void a2dp_aac_encoder_update(A2dpCodecConfig* a2dp_codec_config,
                                    bool* p_restart_input,
                                    bool* p_restart_output,
                                    bool* p_config_updated);
static void a2dp_aac_get_num_frame_iteration(uint8_t* num_of_iterations,
                                             uint8_t* num_of_frames,
                                             uint64_t timestamp_us);
static void a2dp_aac_encode_frames(uint8_t nb_frame);
static bool a2dp_aac_read_feeding(uint8_t* read_buffer, uint32_t* bytes_read);
static uint16_t adjust_effective_mtu(
    const tA2DP_ENCODER_INIT_PEER_PARAMS& peer_params);

bool A2DP_LoadEncoderAac(void) {
  // Nothing to do - the library is statically linked
  return true;
}

void A2DP_UnloadEncoderAac(void) {
  // Nothing to do - the library is statically linked
  if (a2dp_aac_encoder_cb.has_aac_handle)
    aacEncClose(&a2dp_aac_encoder_cb.aac_handle);
  memset(&a2dp_aac_encoder_cb, 0, sizeof(a2dp_aac_encoder_cb));
}

void a2dp_aac_encoder_init(const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
                           A2dpCodecConfig* a2dp_codec_config,
                           a2dp_source_read_callback_t read_callback,
                           a2dp_source_enqueue_callback_t enqueue_callback) {
  if (a2dp_aac_encoder_cb.has_aac_handle)
    aacEncClose(&a2dp_aac_encoder_cb.aac_handle);
  memset(&a2dp_aac_encoder_cb, 0, sizeof(a2dp_aac_encoder_cb));

  a2dp_aac_encoder_cb.stats.session_start_us =
      bluetooth::common::time_get_os_boottime_us();

  a2dp_aac_encoder_cb.read_callback = read_callback;
  a2dp_aac_encoder_cb.enqueue_callback = enqueue_callback;
  a2dp_aac_encoder_cb.peer_params = *p_peer_params;
  a2dp_aac_encoder_cb.timestamp = 0;

  a2dp_aac_encoder_cb.use_SCMS_T = false;

  // NOTE: Ignore the restart_input / restart_output flags - this initization
  // happens when the audio session is (re)started.
  bool restart_input = false;
  bool restart_output = false;
  bool config_updated = false;
  a2dp_aac_encoder_update(a2dp_codec_config, &restart_input, &restart_output,
                          &config_updated);
}

// Update the A2DP AAC encoder.
// |a2dp_codec_config| is the A2DP codec to use for the update.
static void a2dp_aac_encoder_update(A2dpCodecConfig* a2dp_codec_config,
                                    bool* p_restart_input,
                                    bool* p_restart_output,
                                    bool* p_config_updated) {
  tA2DP_AAC_ENCODER_PARAMS* p_encoder_params =
      &a2dp_aac_encoder_cb.aac_encoder_params;
  uint8_t codec_info[AVDT_CODEC_SIZE];
  AACENC_ERROR aac_error;
  int aac_param_value, aac_sampling_freq, aac_peak_bit_rate;

  *p_restart_input = false;
  *p_restart_output = false;
  *p_config_updated = false;

  if (!a2dp_aac_encoder_cb.has_aac_handle) {
    AACENC_ERROR aac_error = aacEncOpen(&a2dp_aac_encoder_cb.aac_handle, 0,
                                        2 /* max 2 channels: stereo */);
    if (aac_error != AACENC_OK) {
      log::error("Cannot open AAC encoder handle: AAC error 0x{:x}", aac_error);
      return;  // TODO: Return an error?
    }
    a2dp_aac_encoder_cb.has_aac_handle = true;
  }

  if (!a2dp_codec_config->copyOutOtaCodecConfig(codec_info)) {
    log::error("Cannot update the codec encoder for {}: invalid codec config",
               a2dp_codec_config->name().c_str());
    return;
  }
  const uint8_t* p_codec_info = codec_info;

  // The feeding parameters
  tA2DP_FEEDING_PARAMS* p_feeding_params = &a2dp_aac_encoder_cb.feeding_params;
  p_feeding_params->sample_rate = A2DP_GetTrackSampleRateAac(p_codec_info);
  p_feeding_params->bits_per_sample =
      a2dp_codec_config->getAudioBitsPerSample();
  p_feeding_params->channel_count = A2DP_GetTrackChannelCountAac(p_codec_info);
  log::info("sample_rate={} bits_per_sample={} channel_count={}",
            p_feeding_params->sample_rate, p_feeding_params->bits_per_sample,
            p_feeding_params->channel_count);

  // The codec parameters
  p_encoder_params->sample_rate =
      a2dp_aac_encoder_cb.feeding_params.sample_rate;
  p_encoder_params->channel_mode = A2DP_GetChannelModeCodeAac(p_codec_info);

  const tA2DP_ENCODER_INIT_PEER_PARAMS& peer_params =
      a2dp_aac_encoder_cb.peer_params;
  a2dp_aac_encoder_cb.TxAaMtuSize = adjust_effective_mtu(peer_params);
  log::info("MTU={}, peer_mtu={}", a2dp_aac_encoder_cb.TxAaMtuSize,
            peer_params.peer_mtu);
  log::info("sample_rate: {} channel_mode: {} ", p_encoder_params->sample_rate,
            p_encoder_params->channel_mode);

  // Set the encoder's parameters: Audio Object Type - MANDATORY
  // A2DP_AAC_OBJECT_TYPE_MPEG2_LC -> AOT_AAC_LC
  // A2DP_AAC_OBJECT_TYPE_MPEG4_LC -> AOT_AAC_LC
  // A2DP_AAC_OBJECT_TYPE_MPEG4_LTP -> AOT_AAC_LTP
  // A2DP_AAC_OBJECT_TYPE_MPEG4_SCALABLE -> AOT_AAC_SCAL
  aac_param_value = AOT_AAC_LC;
  int object_type = A2DP_GetObjectTypeCodeAac(p_codec_info);
  switch (object_type) {
    case A2DP_AAC_OBJECT_TYPE_MPEG2_LC:
      aac_param_value = AOT_AAC_LC;
      break;
    case A2DP_AAC_OBJECT_TYPE_MPEG4_LC:
      aac_param_value = AOT_AAC_LC;
      break;
    case A2DP_AAC_OBJECT_TYPE_MPEG4_LTP:
      aac_param_value = AOT_AAC_LTP;
      break;
    case A2DP_AAC_OBJECT_TYPE_MPEG4_SCALABLE:
      aac_param_value = AOT_AAC_SCAL;
      break;
    default:
      log::error("Cannot set AAC parameter AACENC_AOT: invalid object type {}",
                 object_type);
      return;  // TODO: Return an error?
  }
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle, AACENC_AOT,
                                  aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error("Cannot set AAC parameter AACENC_AOT to {}: AAC error 0x{:x}",
               aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: audioMuxVersion
  aac_param_value = 2;  // audioMuxVersion = "2"
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_AUDIOMUXVER, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_AUDIOMUXVER to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: Signaling mode of the extension AOT
  aac_param_value = 1;  // Signaling mode of the extension AOT = 1
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_SIGNALING_MODE, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_SIGNALING_MODE to {}: AAC error "
        "0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: Sample Rate - MANDATORY
  aac_param_value = A2DP_GetTrackSampleRateAac(p_codec_info);
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_SAMPLERATE, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_SAMPLERATE to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }
  aac_sampling_freq = aac_param_value;  // Save for extra usage below

  // Set the encoder's parameters: Bit Rate - MANDATORY
  aac_param_value = A2DP_GetBitRateAac(p_codec_info);
  // Calculate the bit rate from MTU and sampling frequency
  aac_peak_bit_rate =
      A2DP_ComputeMaxBitRateAac(p_codec_info, a2dp_aac_encoder_cb.TxAaMtuSize);
  aac_param_value = std::min(aac_param_value, aac_peak_bit_rate);
  log::info("MTU = {} Sampling Frequency = {} Bit Rate = {}",
            a2dp_aac_encoder_cb.TxAaMtuSize, aac_sampling_freq,
            aac_param_value);
  if (aac_param_value == -1) {
    log::error(
        "Cannot set AAC parameter AACENC_BITRATE: invalid codec bit rate");
    return;  // TODO: Return an error?
  }
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_BITRATE, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_BITRATE to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: PEAK Bit Rate
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_PEAK_BITRATE, aac_peak_bit_rate);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_PEAK_BITRATE to {}: AAC error 0x{:x}",
        aac_peak_bit_rate, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: Channel Mode - MANDATORY
  if (A2DP_GetTrackChannelCountAac(p_codec_info) == 1) {
    aac_param_value = MODE_1;  // Mono
  } else {
    aac_param_value = MODE_2;  // Stereo
  }
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_CHANNELMODE, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_CHANNELMODE to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: Transport Type
  aac_param_value = TT_MP4_LATM_MCP1;  // muxConfigPresent = 1
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_TRANSMUX, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_TRANSMUX to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: Header Period
  aac_param_value = 1;
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_HEADER_PERIOD, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_HEADER_PERIOD to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Set the encoder's parameters: Variable Bit Rate Support
  aac_param_value = A2DP_GetVariableBitRateSupportAac(p_codec_info);
  if (aac_param_value == -1) {
    log::error(
        "Cannot set AAC parameter AACENC_BITRATEMODE: invalid codec bit rate "
        "mode");
    return;  // TODO: Return an error?
  } else if (aac_param_value == A2DP_AAC_VARIABLE_BIT_RATE_ENABLED) {
    // VBR has 5 modes defined in external/aac/libAACenc/src/aacenc.h
    // A2DP_AAC_VARIABLE_BIT_RATE_DISABLED is equal to AACENC_BR_MODE_CBR
    auto bitrate_mode = a2dp_codec_config->getCodecConfig().codec_specific_1;
    switch (static_cast<AacEncoderBitrateMode>(bitrate_mode)) {
      case AacEncoderBitrateMode::AACENC_BR_MODE_VBR_1:
        [[fallthrough]];
      case AacEncoderBitrateMode::AACENC_BR_MODE_VBR_2:
        [[fallthrough]];
      case AacEncoderBitrateMode::AACENC_BR_MODE_VBR_3:
        [[fallthrough]];
      case AacEncoderBitrateMode::AACENC_BR_MODE_VBR_4:
        [[fallthrough]];
      case AacEncoderBitrateMode::AACENC_BR_MODE_VBR_5:
        break;
      default:
        bitrate_mode =
            static_cast<int64_t>(AacEncoderBitrateMode::AACENC_BR_MODE_VBR_5);
    }
    aac_param_value =
        static_cast<uint8_t>(bitrate_mode) & ~A2DP_AAC_VARIABLE_BIT_RATE_MASK;
  }
  log::info("AACENC_BITRATEMODE: {}", aac_param_value);
  aac_error = aacEncoder_SetParam(a2dp_aac_encoder_cb.aac_handle,
                                  AACENC_BITRATEMODE, aac_param_value);
  if (aac_error != AACENC_OK) {
    log::error(
        "Cannot set AAC parameter AACENC_BITRATEMODE to {}: AAC error 0x{:x}",
        aac_param_value, aac_error);
    return;  // TODO: Return an error?
  }

  // Mark the end of setting the encoder's parameters
  aac_error =
      aacEncEncode(a2dp_aac_encoder_cb.aac_handle, NULL, NULL, NULL, NULL);
  if (aac_error != AACENC_OK) {
    log::error("Cannot complete setting the AAC parameters: AAC error 0x{:x}",
               aac_error);
    return;  // TODO: Return an error?
  }

  // Retrieve the encoder info so we can save the frame length
  AACENC_InfoStruct aac_info;
  aac_error = aacEncInfo(a2dp_aac_encoder_cb.aac_handle, &aac_info);
  if (aac_error != AACENC_OK) {
    log::error("Cannot retrieve the AAC encoder info: AAC error 0x{:x}",
               aac_error);
    return;  // TODO: Return an error?
  }
  p_encoder_params->frame_length = aac_info.frameLength;
  p_encoder_params->input_channels_n = aac_info.inputChannels;
  p_encoder_params->max_encoded_buffer_bytes = aac_info.maxOutBufBytes;
  log::info(
      "AAC frame_length = {} input_channels_n = {} max_encoded_buffer_bytes = "
      "{}",
      p_encoder_params->frame_length, p_encoder_params->input_channels_n,
      p_encoder_params->max_encoded_buffer_bytes);

  // After encoder params ready, reset the feeding state and its interval.
  a2dp_aac_feeding_reset();
}

void a2dp_aac_encoder_cleanup(void) {
  if (a2dp_aac_encoder_cb.has_aac_handle)
    aacEncClose(&a2dp_aac_encoder_cb.aac_handle);
  memset(&a2dp_aac_encoder_cb, 0, sizeof(a2dp_aac_encoder_cb));
}

void a2dp_aac_feeding_reset(void) {
  auto frame_length = a2dp_aac_encoder_cb.aac_encoder_params.frame_length;
  auto sample_rate = a2dp_aac_encoder_cb.feeding_params.sample_rate;
  if (frame_length == 0 || sample_rate == 0) {
    log::warn("AAC encoder is not configured");
    a2dp_aac_encoder_interval_ms = A2DP_AAC_ENCODER_INTERVAL_MS;
  } else {
    // PCM data size per AAC frame (bits)
    // = aac_encoder_params.frame_length * feeding_params.bits_per_sample
    //   * feeding_params.channel_count
    // = feeding_params.sample_rate * feeding_params.bits_per_sample
    //   * feeding_params.channel_count * (T_interval_ms / 1000);
    // Here we use the nearest integer not greater than the value.
    a2dp_aac_encoder_interval_ms = frame_length * 1000 / sample_rate;
    if (a2dp_aac_encoder_interval_ms < A2DP_AAC_ENCODER_INTERVAL_MS)
      a2dp_aac_encoder_interval_ms = A2DP_AAC_ENCODER_INTERVAL_MS;
  }

  /* By default, just clear the entire state */
  memset(&a2dp_aac_encoder_cb.aac_feeding_state, 0,
         sizeof(a2dp_aac_encoder_cb.aac_feeding_state));

  a2dp_aac_encoder_cb.aac_feeding_state.bytes_per_tick =
      (a2dp_aac_encoder_cb.feeding_params.sample_rate *
       a2dp_aac_encoder_cb.feeding_params.bits_per_sample / 8 *
       a2dp_aac_encoder_cb.feeding_params.channel_count *
       a2dp_aac_encoder_interval_ms) /
      1000;

  log::info("PCM bytes {} per tick {} ms",
            a2dp_aac_encoder_cb.aac_feeding_state.bytes_per_tick,
            a2dp_aac_encoder_interval_ms);
}

void a2dp_aac_feeding_flush(void) {
  a2dp_aac_encoder_cb.aac_feeding_state.counter = 0.0f;
}

uint64_t a2dp_aac_get_encoder_interval_ms(void) {
  return a2dp_aac_encoder_interval_ms;
}

int a2dp_aac_get_effective_frame_size() {
  return a2dp_aac_encoder_cb.TxAaMtuSize;
}

void a2dp_aac_send_frames(uint64_t timestamp_us) {
  uint8_t nb_frame = 0;
  uint8_t nb_iterations = 0;

  a2dp_aac_get_num_frame_iteration(&nb_iterations, &nb_frame, timestamp_us);
  log::verbose("Sending {} frames per iteration, {} iterations", nb_frame,
               nb_iterations);
  if (nb_frame == 0) return;

  for (uint8_t counter = 0; counter < nb_iterations; counter++) {
    // Transcode frame and enqueue
    a2dp_aac_encode_frames(nb_frame);
  }
}

// Obtains the number of frames to send and number of iterations
// to be used. |num_of_iterations| and |num_of_frames| parameters
// are used as output param for returning the respective values.
static void a2dp_aac_get_num_frame_iteration(uint8_t* num_of_iterations,
                                             uint8_t* num_of_frames,
                                             uint64_t timestamp_us) {
  uint32_t result = 0;
  uint8_t nof = 0;
  uint8_t noi = 1;

  uint32_t pcm_bytes_per_frame =
      a2dp_aac_encoder_cb.aac_encoder_params.frame_length *
      a2dp_aac_encoder_cb.feeding_params.channel_count *
      a2dp_aac_encoder_cb.feeding_params.bits_per_sample / 8;
  log::verbose("pcm_bytes_per_frame {}", pcm_bytes_per_frame);

  uint32_t us_this_tick = a2dp_aac_encoder_interval_ms * 1000;
  uint64_t now_us = timestamp_us;
  if (a2dp_aac_encoder_cb.aac_feeding_state.last_frame_us != 0)
    us_this_tick =
        (now_us - a2dp_aac_encoder_cb.aac_feeding_state.last_frame_us);
  a2dp_aac_encoder_cb.aac_feeding_state.last_frame_us = now_us;

  a2dp_aac_encoder_cb.aac_feeding_state.counter +=
      (float)a2dp_aac_encoder_cb.aac_feeding_state.bytes_per_tick *
      us_this_tick / (a2dp_aac_encoder_interval_ms * 1000);

  result = a2dp_aac_encoder_cb.aac_feeding_state.counter / pcm_bytes_per_frame;
  a2dp_aac_encoder_cb.aac_feeding_state.counter -= result * pcm_bytes_per_frame;
  nof = result;

  log::verbose("effective num of frames {}, iterations {}", nof, noi);

  *num_of_frames = nof;
  *num_of_iterations = noi;
}

static void a2dp_aac_encode_frames(uint8_t nb_frame) {
  tA2DP_AAC_ENCODER_PARAMS* p_encoder_params =
      &a2dp_aac_encoder_cb.aac_encoder_params;
  tA2DP_FEEDING_PARAMS* p_feeding_params = &a2dp_aac_encoder_cb.feeding_params;
  uint8_t remain_nb_frame = nb_frame;
  uint8_t read_buffer[BT_DEFAULT_BUFFER_SIZE];
  int pcm_bytes_per_frame = p_encoder_params->frame_length *
                            p_feeding_params->channel_count *
                            p_feeding_params->bits_per_sample / 8;
  CHECK(pcm_bytes_per_frame <= static_cast<int>(sizeof(read_buffer)));

  // Setup the input buffer
  AACENC_BufDesc in_buf_desc;
  void* in_buf_vector[1] = {nullptr};
  int in_buf_identifiers[1] = {IN_AUDIO_DATA};
  int in_buf_sizes[1] = {pcm_bytes_per_frame};
  int in_buf_element_sizes[1] = {p_feeding_params->bits_per_sample / 8};
  in_buf_desc.numBufs = 1;
  in_buf_desc.bufs = in_buf_vector;
  in_buf_desc.bufferIdentifiers = in_buf_identifiers;
  in_buf_desc.bufSizes = in_buf_sizes;
  in_buf_desc.bufElSizes = in_buf_element_sizes;

  // Setup the output buffer (partially)
  AACENC_BufDesc out_buf_desc;
  void* out_buf_vector[1] = {nullptr};
  int out_buf_identifiers[1] = {OUT_BITSTREAM_DATA};
  int out_buf_sizes[1] = {p_encoder_params->max_encoded_buffer_bytes};
  // NOTE: out_buf_element_sizes below is probably unused by the encoder
  int out_buf_element_sizes[1] = {p_feeding_params->bits_per_sample / 8};
  out_buf_desc.numBufs = 1;
  out_buf_desc.bufs = out_buf_vector;
  out_buf_desc.bufferIdentifiers = out_buf_identifiers;
  out_buf_desc.bufSizes = out_buf_sizes;
  out_buf_desc.bufElSizes = out_buf_element_sizes;
  CHECK(p_encoder_params->max_encoded_buffer_bytes <=
        static_cast<int>(BT_DEFAULT_BUFFER_SIZE - sizeof(BT_HDR)));

  AACENC_InArgs aac_in_args;
  aac_in_args.numInSamples =
      p_encoder_params->frame_length * p_feeding_params->channel_count;
  aac_in_args.numAncBytes = 0;

  AACENC_OutArgs aac_out_args = {
      .numOutBytes = 0, .numInSamples = 0, .numAncBytes = 0};

  uint32_t count;
  uint32_t total_bytes_read = 0;
  int written = 0;

  while (nb_frame) {
    BT_HDR* p_buf = (BT_HDR*)osi_malloc(BT_DEFAULT_BUFFER_SIZE);
    p_buf->offset = A2DP_AAC_OFFSET;
    p_buf->len = 0;
    p_buf->layer_specific = 0;
    a2dp_aac_encoder_cb.stats.media_read_total_expected_packets++;

    count = 0;
    do {
      //
      // Read the PCM data and encode it
      //
      uint32_t bytes_read = 0;
      if (a2dp_aac_read_feeding(read_buffer, &bytes_read)) {
        uint8_t* packet = (uint8_t*)(p_buf + 1) + p_buf->offset + p_buf->len;
        if (!a2dp_aac_encoder_cb.has_aac_handle) {
          log::error("invalid AAC handle");
          a2dp_aac_encoder_cb.stats.media_read_total_dropped_packets++;
          osi_free(p_buf);
          return;
        }
        in_buf_vector[0] = read_buffer;
        out_buf_vector[0] = packet + count;
        AACENC_ERROR aac_error =
            aacEncEncode(a2dp_aac_encoder_cb.aac_handle, &in_buf_desc,
                         &out_buf_desc, &aac_in_args, &aac_out_args);
        if (aac_error != AACENC_OK) {
          log::error("AAC encoding error: 0x{:x}", aac_error);
          a2dp_aac_encoder_cb.stats.media_read_total_dropped_packets++;
          osi_free(p_buf);
          return;
        }
        written = aac_out_args.numOutBytes;
        count += written;
        p_buf->len += written;
        nb_frame--;
        p_buf->layer_specific++;  // added a frame to the buffer
      } else {
        log::warn("underflow {}", nb_frame);
        a2dp_aac_encoder_cb.aac_feeding_state.counter +=
            nb_frame * p_encoder_params->frame_length *
            p_feeding_params->channel_count *
            p_feeding_params->bits_per_sample / 8;

        // no more pcm to read
        nb_frame = 0;
      }
      total_bytes_read += bytes_read;
    } while ((written == 0) && nb_frame);

    // NOTE: We don't check whether the packet will fit in the MTU,
    // because AAC doesn't give us control over the encoded frame size.
    // If the packet is larger than the MTU, it will be fragmented before
    // transmission.
    if (p_buf->len) {
      /*
       * Timestamp of the media packet header represent the TS of the
       * first frame, i.e the timestamp before including this frame.
       */
      *((uint32_t*)(p_buf + 1)) = a2dp_aac_encoder_cb.timestamp;

      a2dp_aac_encoder_cb.timestamp +=
          p_buf->layer_specific * p_encoder_params->frame_length;

      uint8_t done_nb_frame = remain_nb_frame - nb_frame;
      remain_nb_frame = nb_frame;
      if (!a2dp_aac_encoder_cb.enqueue_callback(p_buf, done_nb_frame,
                                                total_bytes_read))
        return;
    } else {
      a2dp_aac_encoder_cb.stats.media_read_total_dropped_packets++;
      osi_free(p_buf);
    }
  }
}

static bool a2dp_aac_read_feeding(uint8_t* read_buffer, uint32_t* bytes_read) {
  uint32_t read_size = a2dp_aac_encoder_cb.aac_encoder_params.frame_length *
                       a2dp_aac_encoder_cb.feeding_params.channel_count *
                       a2dp_aac_encoder_cb.feeding_params.bits_per_sample / 8;

  a2dp_aac_encoder_cb.stats.media_read_total_expected_reads_count++;
  a2dp_aac_encoder_cb.stats.media_read_total_expected_read_bytes += read_size;

  /* Read Data from UIPC channel */
  uint32_t nb_byte_read =
      a2dp_aac_encoder_cb.read_callback(read_buffer, read_size);
  a2dp_aac_encoder_cb.stats.media_read_total_actual_read_bytes += nb_byte_read;
  *bytes_read = nb_byte_read;

  if (nb_byte_read < read_size) {
    if (nb_byte_read == 0) return false;

    /* Fill the unfilled part of the read buffer with silence (0) */
    memset(((uint8_t*)read_buffer) + nb_byte_read, 0, read_size - nb_byte_read);
    nb_byte_read = read_size;
  }
  a2dp_aac_encoder_cb.stats.media_read_total_actual_reads_count++;

  return true;
}

static uint16_t adjust_effective_mtu(
    const tA2DP_ENCODER_INIT_PEER_PARAMS& peer_params) {
  uint16_t mtu_size = BT_DEFAULT_BUFFER_SIZE - A2DP_AAC_OFFSET - sizeof(BT_HDR);
  if (mtu_size > peer_params.peer_mtu) {
    mtu_size = peer_params.peer_mtu;
  }
  log::verbose("original AVDTP MTU size: {}", mtu_size);
  if (peer_params.is_peer_edr && !peer_params.peer_supports_3mbps) {
    // This condition would be satisfied only if the remote device is
    // EDR and supports only 2 Mbps, but the effective AVDTP MTU size
    // exceeds the 2DH5 packet size.
    log::verbose("The remote device is EDR but does not support 3 Mbps");
    if (mtu_size > MAX_2MBPS_AVDTP_MTU) {
      log::warn("Restricting AVDTP MTU size from {} to {}", mtu_size,
                MAX_2MBPS_AVDTP_MTU);
      mtu_size = MAX_2MBPS_AVDTP_MTU;
    }
  }
  return mtu_size;
}

void A2dpCodecConfigAacSource::debug_codec_dump(int fd) {
  a2dp_aac_encoder_stats_t* stats = &a2dp_aac_encoder_cb.stats;

  A2dpCodecConfig::debug_codec_dump(fd);

  auto codec_specific_1 = getCodecConfig().codec_specific_1;
  dprintf(
      fd,
      "  AAC bitrate mode                                        : %s "
      "(0x%" PRIx64 ")\n",
      ((codec_specific_1 & ~A2DP_AAC_VARIABLE_BIT_RATE_MASK) == 0 ? "Constant"
                                                                  : "Variable"),
      codec_specific_1);
  dprintf(fd, "  Encoder interval (ms): %" PRIu64 "\n",
          a2dp_aac_get_encoder_interval_ms());
  dprintf(fd, "  Effective MTU: %d\n", a2dp_aac_get_effective_frame_size());
  dprintf(fd,
          "  Packet counts (expected/dropped)                        : %zu / "
          "%zu\n",
          stats->media_read_total_expected_packets,
          stats->media_read_total_dropped_packets);

  dprintf(fd,
          "  PCM read counts (expected/actual)                       : %zu / "
          "%zu\n",
          stats->media_read_total_expected_reads_count,
          stats->media_read_total_actual_reads_count);

  dprintf(fd,
          "  PCM read bytes (expected/actual)                        : %zu / "
          "%zu\n",
          stats->media_read_total_expected_read_bytes,
          stats->media_read_total_actual_read_bytes);
}
