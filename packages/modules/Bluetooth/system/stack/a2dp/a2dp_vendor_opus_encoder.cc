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

#define LOG_TAG "a2dp_vendor_opus_encoder"

#include "a2dp_vendor_opus_encoder.h"

#include <bluetooth/log.h>
#include <dlfcn.h>
#include <opus.h>
#include <stdio.h>
#include <string.h>

#include "a2dp_vendor.h"
#include "a2dp_vendor_opus.h"
#include "common/time_util.h"
#include "include/check.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/include/bt_hdr.h"

using namespace bluetooth;

typedef struct {
  uint32_t sample_rate;
  uint16_t bitrate;
  uint16_t framesize;
  uint8_t channel_mode;
  uint8_t bits_per_sample;
  uint8_t quality_mode_index;
  int pcm_wlength;
  uint8_t pcm_fmt;
} tA2DP_OPUS_ENCODER_PARAMS;

typedef struct {
  float counter;
  uint32_t bytes_per_tick;
  uint64_t last_frame_us;
} tA2DP_OPUS_FEEDING_STATE;

typedef struct {
  uint64_t session_start_us;

  size_t media_read_total_expected_packets;
  size_t media_read_total_expected_reads_count;
  size_t media_read_total_expected_read_bytes;

  size_t media_read_total_dropped_packets;
  size_t media_read_total_actual_reads_count;
  size_t media_read_total_actual_read_bytes;
} a2dp_opus_encoder_stats_t;

typedef struct {
  a2dp_source_read_callback_t read_callback;
  a2dp_source_enqueue_callback_t enqueue_callback;
  uint16_t TxAaMtuSize;
  size_t TxQueueLength;

  bool use_SCMS_T;
  bool is_peer_edr;          // True if the peer device supports EDR
  bool peer_supports_3mbps;  // True if the peer device supports 3Mbps EDR
  uint16_t peer_mtu;         // MTU of the A2DP peer
  uint32_t timestamp;        // Timestamp for the A2DP frames

  OpusEncoder* opus_handle;
  bool has_opus_handle;  // True if opus_handle is valid

  tA2DP_FEEDING_PARAMS feeding_params;
  tA2DP_OPUS_ENCODER_PARAMS opus_encoder_params;
  tA2DP_OPUS_FEEDING_STATE opus_feeding_state;

  a2dp_opus_encoder_stats_t stats;
} tA2DP_OPUS_ENCODER_CB;

static tA2DP_OPUS_ENCODER_CB a2dp_opus_encoder_cb;

static bool a2dp_vendor_opus_encoder_update(uint16_t peer_mtu,
                                            A2dpCodecConfig* a2dp_codec_config,
                                            bool* p_restart_input,
                                            bool* p_restart_output,
                                            bool* p_config_updated);
static void a2dp_opus_get_num_frame_iteration(uint8_t* num_of_iterations,
                                              uint8_t* num_of_frames,
                                              uint64_t timestamp_us);
static void a2dp_opus_encode_frames(uint8_t nb_frame);
static bool a2dp_opus_read_feeding(uint8_t* read_buffer, uint32_t* bytes_read);

void a2dp_vendor_opus_encoder_cleanup(void) {
  if (a2dp_opus_encoder_cb.has_opus_handle) {
    osi_free(a2dp_opus_encoder_cb.opus_handle);
    a2dp_opus_encoder_cb.has_opus_handle = false;
    a2dp_opus_encoder_cb.opus_handle = nullptr;
  }
  memset(&a2dp_opus_encoder_cb, 0, sizeof(a2dp_opus_encoder_cb));

  a2dp_opus_encoder_cb.stats.session_start_us =
      bluetooth::common::time_get_os_boottime_us();

  a2dp_opus_encoder_cb.timestamp = 0;
  a2dp_opus_encoder_cb.use_SCMS_T = false;

  return;
}

void a2dp_vendor_opus_encoder_init(
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    A2dpCodecConfig* a2dp_codec_config,
    a2dp_source_read_callback_t read_callback,
    a2dp_source_enqueue_callback_t enqueue_callback) {
  uint32_t error_val;

  a2dp_vendor_opus_encoder_cleanup();

  a2dp_opus_encoder_cb.read_callback = read_callback;
  a2dp_opus_encoder_cb.enqueue_callback = enqueue_callback;
  a2dp_opus_encoder_cb.is_peer_edr = p_peer_params->is_peer_edr;
  a2dp_opus_encoder_cb.peer_supports_3mbps = p_peer_params->peer_supports_3mbps;
  a2dp_opus_encoder_cb.peer_mtu = p_peer_params->peer_mtu;

  // NOTE: Ignore the restart_input / restart_output flags - this initization
  // happens when the connection is (re)started.
  bool restart_input = false;
  bool restart_output = false;
  bool config_updated = false;

  uint32_t size = opus_encoder_get_size(A2DP_OPUS_CODEC_OUTPUT_CHS);
  a2dp_opus_encoder_cb.opus_handle =
      static_cast<OpusEncoder*>(osi_malloc(size));
  if (a2dp_opus_encoder_cb.opus_handle == nullptr) {
    log::error("failed to allocate opus encoder handle");
    return;
  }

  error_val = opus_encoder_init(
      a2dp_opus_encoder_cb.opus_handle, A2DP_OPUS_CODEC_DEFAULT_SAMPLERATE,
      A2DP_OPUS_CODEC_OUTPUT_CHS, OPUS_APPLICATION_AUDIO);

  if (error_val != OPUS_OK) {
    log::error(
        "failed to init opus encoder (handle size {}, sampling rate {}, output "
        "chs {}, error {})",
        size, A2DP_OPUS_CODEC_DEFAULT_SAMPLERATE, A2DP_OPUS_CODEC_OUTPUT_CHS,
        error_val);
    osi_free(a2dp_opus_encoder_cb.opus_handle);
    return;
  } else {
    a2dp_opus_encoder_cb.has_opus_handle = true;
  }

  a2dp_vendor_opus_encoder_update(a2dp_opus_encoder_cb.peer_mtu,
                                  a2dp_codec_config, &restart_input,
                                  &restart_output, &config_updated);

  return;
}

bool A2dpCodecConfigOpusSource::updateEncoderUserConfig(
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params, bool* p_restart_input,
    bool* p_restart_output, bool* p_config_updated) {
  if (a2dp_opus_encoder_cb.peer_mtu == 0) {
    log::error("Cannot update the codec encoder for {}: invalid peer MTU",
               name().c_str());
    return false;
  }

  return a2dp_vendor_opus_encoder_update(a2dp_opus_encoder_cb.peer_mtu, this,
                                         p_restart_input, p_restart_output,
                                         p_config_updated);
}

static bool a2dp_vendor_opus_encoder_update(uint16_t peer_mtu,
                                            A2dpCodecConfig* a2dp_codec_config,
                                            bool* p_restart_input,
                                            bool* p_restart_output,
                                            bool* p_config_updated) {
  tA2DP_OPUS_ENCODER_PARAMS* p_encoder_params =
      &a2dp_opus_encoder_cb.opus_encoder_params;
  uint8_t codec_info[AVDT_CODEC_SIZE];
  uint32_t error = 0;

  *p_restart_input = false;
  *p_restart_output = false;
  *p_config_updated = false;

  if (!a2dp_opus_encoder_cb.has_opus_handle ||
      a2dp_opus_encoder_cb.opus_handle == NULL) {
    log::error("Cannot get Opus encoder handle");
    return false;
  }
  CHECK(a2dp_opus_encoder_cb.opus_handle != nullptr);

  if (!a2dp_codec_config->copyOutOtaCodecConfig(codec_info)) {
    log::error("Cannot update the codec encoder for {}: invalid codec config",
               a2dp_codec_config->name().c_str());
    return false;
  }
  const uint8_t* p_codec_info = codec_info;
  btav_a2dp_codec_config_t codec_config = a2dp_codec_config->getCodecConfig();

  // The feeding parameters
  tA2DP_FEEDING_PARAMS* p_feeding_params = &a2dp_opus_encoder_cb.feeding_params;
  p_feeding_params->sample_rate =
      A2DP_VendorGetTrackSampleRateOpus(p_codec_info);
  p_feeding_params->bits_per_sample =
      a2dp_codec_config->getAudioBitsPerSample();
  p_feeding_params->channel_count =
      A2DP_VendorGetTrackChannelCountOpus(p_codec_info);
  log::info("sample_rate={} bits_per_sample={} channel_count={}",
            p_feeding_params->sample_rate, p_feeding_params->bits_per_sample,
            p_feeding_params->channel_count);

  // The codec parameters
  p_encoder_params->sample_rate =
      a2dp_opus_encoder_cb.feeding_params.sample_rate;
  p_encoder_params->channel_mode =
      A2DP_VendorGetChannelModeCodeOpus(p_codec_info);
  p_encoder_params->framesize = A2DP_VendorGetFrameSizeOpus(p_codec_info);
  p_encoder_params->bitrate = A2DP_VendorGetBitRateOpus(p_codec_info);

  a2dp_vendor_opus_feeding_reset();

  uint16_t mtu_size =
      BT_DEFAULT_BUFFER_SIZE - A2DP_OPUS_OFFSET - sizeof(BT_HDR);
  if (mtu_size < peer_mtu) {
    a2dp_opus_encoder_cb.TxAaMtuSize = mtu_size;
  } else {
    a2dp_opus_encoder_cb.TxAaMtuSize = peer_mtu;
  }

  // Set the bitrate quality mode index
  if (codec_config.codec_specific_3 != 0) {
    p_encoder_params->quality_mode_index = codec_config.codec_specific_3 % 10;
    log::info("setting bitrate quality mode to {}",
              p_encoder_params->quality_mode_index);
  } else {
    p_encoder_params->quality_mode_index = 5;
    log::info("setting bitrate quality mode to default {}",
              p_encoder_params->quality_mode_index);
  }

  error = opus_encoder_ctl(
      a2dp_opus_encoder_cb.opus_handle,
      OPUS_SET_COMPLEXITY(p_encoder_params->quality_mode_index));

  if (error != OPUS_OK) {
    log::error("failed to set encoder bitrate quality setting");
    return false;
  }

  p_encoder_params->pcm_wlength =
      a2dp_opus_encoder_cb.feeding_params.bits_per_sample >> 3;

  log::info("setting bitrate to {}", p_encoder_params->bitrate);
  error = opus_encoder_ctl(a2dp_opus_encoder_cb.opus_handle,
                           OPUS_SET_BITRATE(p_encoder_params->bitrate));

  if (error != OPUS_OK) {
    log::error("failed to set encoder bitrate");
    return false;
  }

  // Set the Audio format from pcm_wlength
  if (p_encoder_params->pcm_wlength == 2)
    p_encoder_params->pcm_fmt = 16;
  else if (p_encoder_params->pcm_wlength == 3)
    p_encoder_params->pcm_fmt = 24;
  else if (p_encoder_params->pcm_wlength == 4)
    p_encoder_params->pcm_fmt = 32;

  return true;
}

void a2dp_vendor_opus_feeding_reset(void) {
  memset(&a2dp_opus_encoder_cb.opus_feeding_state, 0,
         sizeof(a2dp_opus_encoder_cb.opus_feeding_state));

  a2dp_opus_encoder_cb.opus_feeding_state.bytes_per_tick =
      (a2dp_opus_encoder_cb.feeding_params.sample_rate *
       a2dp_opus_encoder_cb.feeding_params.bits_per_sample / 8 *
       a2dp_opus_encoder_cb.feeding_params.channel_count *
       a2dp_vendor_opus_get_encoder_interval_ms()) /
      1000;

  return;
}

void a2dp_vendor_opus_feeding_flush(void) {
  a2dp_opus_encoder_cb.opus_feeding_state.counter = 0.0f;

  return;
}

uint64_t a2dp_vendor_opus_get_encoder_interval_ms(void) {
  return ((a2dp_opus_encoder_cb.opus_encoder_params.framesize * 1000) /
          a2dp_opus_encoder_cb.opus_encoder_params.sample_rate);
}

void a2dp_vendor_opus_send_frames(uint64_t timestamp_us) {
  uint8_t nb_frame = 0;
  uint8_t nb_iterations = 0;

  a2dp_opus_get_num_frame_iteration(&nb_iterations, &nb_frame, timestamp_us);
  if (nb_frame == 0) return;

  for (uint8_t counter = 0; counter < nb_iterations; counter++) {
    // Transcode frame and enqueue
    a2dp_opus_encode_frames(nb_frame);
  }

  return;
}

// Obtains the number of frames to send and number of iterations
// to be used. |num_of_iterations| and |num_of_frames| parameters
// are used as output param for returning the respective values.
static void a2dp_opus_get_num_frame_iteration(uint8_t* num_of_iterations,
                                              uint8_t* num_of_frames,
                                              uint64_t timestamp_us) {
  uint32_t result = 0;
  uint8_t nof = 0;
  uint8_t noi = 1;

  uint32_t pcm_bytes_per_frame =
      a2dp_opus_encoder_cb.opus_encoder_params.framesize *
      a2dp_opus_encoder_cb.feeding_params.channel_count *
      a2dp_opus_encoder_cb.feeding_params.bits_per_sample / 8;

  uint32_t us_this_tick = a2dp_vendor_opus_get_encoder_interval_ms() * 1000;
  uint64_t now_us = timestamp_us;
  if (a2dp_opus_encoder_cb.opus_feeding_state.last_frame_us != 0)
    us_this_tick =
        (now_us - a2dp_opus_encoder_cb.opus_feeding_state.last_frame_us);
  a2dp_opus_encoder_cb.opus_feeding_state.last_frame_us = now_us;

  a2dp_opus_encoder_cb.opus_feeding_state.counter +=
      (float)a2dp_opus_encoder_cb.opus_feeding_state.bytes_per_tick *
      us_this_tick / (a2dp_vendor_opus_get_encoder_interval_ms() * 1000);

  result =
      a2dp_opus_encoder_cb.opus_feeding_state.counter / pcm_bytes_per_frame;
  a2dp_opus_encoder_cb.opus_feeding_state.counter -=
      result * pcm_bytes_per_frame;
  nof = result;

  *num_of_frames = nof;
  *num_of_iterations = noi;
}

static void a2dp_opus_encode_frames(uint8_t nb_frame) {
  tA2DP_OPUS_ENCODER_PARAMS* p_encoder_params =
      &a2dp_opus_encoder_cb.opus_encoder_params;
  unsigned char* packet;
  uint8_t remain_nb_frame = nb_frame;
  uint16_t opus_frame_size = p_encoder_params->framesize;
  uint8_t read_buffer[p_encoder_params->framesize *
                      p_encoder_params->pcm_wlength *
                      p_encoder_params->channel_mode];

  int32_t out_frames = 0;
  int32_t written = 0;

  uint32_t bytes_read = 0;
  while (nb_frame) {
    BT_HDR* p_buf = (BT_HDR*)osi_malloc(BT_DEFAULT_BUFFER_SIZE);
    p_buf->offset = A2DP_OPUS_OFFSET;
    p_buf->len = 0;
    p_buf->layer_specific = 0;
    a2dp_opus_encoder_cb.stats.media_read_total_expected_packets++;

    do {
      //
      // Read the PCM data and encode it
      //
      uint32_t temp_bytes_read = 0;
      if (a2dp_opus_read_feeding(read_buffer, &temp_bytes_read)) {
        bytes_read += temp_bytes_read;
        packet = (unsigned char*)(p_buf + 1) + p_buf->offset + p_buf->len;

        if (a2dp_opus_encoder_cb.opus_handle == NULL) {
          log::error("invalid OPUS handle");
          a2dp_opus_encoder_cb.stats.media_read_total_dropped_packets++;
          osi_free(p_buf);
          return;
        }

        written =
            opus_encode(a2dp_opus_encoder_cb.opus_handle,
                        (const opus_int16*)&read_buffer[0], opus_frame_size,
                        packet, (BT_DEFAULT_BUFFER_SIZE - p_buf->offset));

        if (written <= 0) {
          log::error("OPUS encoding error");
          a2dp_opus_encoder_cb.stats.media_read_total_dropped_packets++;
          osi_free(p_buf);
          return;
        } else {
          out_frames++;
        }
        p_buf->len += written;
        nb_frame--;
        p_buf->layer_specific += out_frames;  // added a frame to the buffer
      } else {
        log::warn("Opus src buffer underflow {}", nb_frame);
        a2dp_opus_encoder_cb.opus_feeding_state.counter +=
            nb_frame * opus_frame_size *
            a2dp_opus_encoder_cb.feeding_params.channel_count *
            a2dp_opus_encoder_cb.feeding_params.bits_per_sample / 8;

        // no more pcm to read
        nb_frame = 0;
      }
    } while ((written == 0) && nb_frame);

    if (p_buf->len) {
      /*
       * Timestamp of the media packet header represent the TS of the
       * first frame, i.e. the timestamp before including this frame.
       */
      *((uint32_t*)(p_buf + 1)) = a2dp_opus_encoder_cb.timestamp;

      a2dp_opus_encoder_cb.timestamp += p_buf->layer_specific * opus_frame_size;

      uint8_t done_nb_frame = remain_nb_frame - nb_frame;
      remain_nb_frame = nb_frame;

      if (!a2dp_opus_encoder_cb.enqueue_callback(p_buf, done_nb_frame,
                                                 bytes_read))
        return;
    } else {
      a2dp_opus_encoder_cb.stats.media_read_total_dropped_packets++;
      osi_free(p_buf);
    }
  }
}

static bool a2dp_opus_read_feeding(uint8_t* read_buffer, uint32_t* bytes_read) {
  uint32_t read_size = a2dp_opus_encoder_cb.opus_encoder_params.framesize *
                       a2dp_opus_encoder_cb.feeding_params.channel_count *
                       a2dp_opus_encoder_cb.feeding_params.bits_per_sample / 8;

  a2dp_opus_encoder_cb.stats.media_read_total_expected_reads_count++;
  a2dp_opus_encoder_cb.stats.media_read_total_expected_read_bytes += read_size;

  /* Read Data from UIPC channel */
  uint32_t nb_byte_read =
      a2dp_opus_encoder_cb.read_callback(read_buffer, read_size);
  a2dp_opus_encoder_cb.stats.media_read_total_actual_read_bytes += nb_byte_read;

  if (nb_byte_read < read_size) {
    if (nb_byte_read == 0) return false;

    /* Fill the unfilled part of the read buffer with silence (0) */
    memset(((uint8_t*)read_buffer) + nb_byte_read, 0, read_size - nb_byte_read);
    nb_byte_read = read_size;
  }
  a2dp_opus_encoder_cb.stats.media_read_total_actual_reads_count++;

  *bytes_read = nb_byte_read;
  return true;
}

void a2dp_vendor_opus_set_transmit_queue_length(size_t transmit_queue_length) {
  a2dp_opus_encoder_cb.TxQueueLength = transmit_queue_length;

  return;
}

uint64_t A2dpCodecConfigOpusSource::encoderIntervalMs() const {
  return a2dp_vendor_opus_get_encoder_interval_ms();
}

int a2dp_vendor_opus_get_effective_frame_size() {
  return a2dp_opus_encoder_cb.TxAaMtuSize;
}

void A2dpCodecConfigOpusSource::debug_codec_dump(int fd) {
  a2dp_opus_encoder_stats_t* stats = &a2dp_opus_encoder_cb.stats;
  tA2DP_OPUS_ENCODER_PARAMS* p_encoder_params =
      &a2dp_opus_encoder_cb.opus_encoder_params;

  A2dpCodecConfig::debug_codec_dump(fd);

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

  dprintf(fd,
          "  OPUS transmission bitrate (Kbps)                        : %d\n",
          p_encoder_params->bitrate);

  dprintf(fd,
          "  OPUS saved transmit queue length                        : %zu\n",
          a2dp_opus_encoder_cb.TxQueueLength);

  return;
}
