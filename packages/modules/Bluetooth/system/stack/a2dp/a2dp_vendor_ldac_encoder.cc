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

#define LOG_TAG "a2dp_vendor_ldac_encoder"
#define ATRACE_TAG ATRACE_TAG_AUDIO

#include "a2dp_vendor_ldac_encoder.h"

#ifdef __ANDROID__
#include <cutils/trace.h>
#endif
#include <bluetooth/log.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <ldacBT_abr.h>
#include <stdio.h>
#include <string.h>

#include "a2dp_vendor_ldac.h"
#include "common/time_util.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"

//
// Encoder for LDAC Source Codec
//

// Initial EQMID for ABR mode.
#define LDAC_ABR_MODE_EQMID LDACBT_EQMID_SQ

// A2DP LDAC encoder interval in milliseconds
#define A2DP_LDAC_ENCODER_INTERVAL_MS 20
#define A2DP_LDAC_MEDIA_BYTES_PER_FRAME 128

// offset
#define A2DP_LDAC_OFFSET (AVDT_MEDIA_OFFSET + A2DP_LDAC_MPL_HDR_LEN)

using namespace bluetooth;

namespace fmt {
template <>
struct formatter<LDACBT_SMPL_FMT_T> : enum_formatter<LDACBT_SMPL_FMT_T> {};
}  // namespace fmt

typedef struct {
  uint32_t sample_rate;
  uint8_t channel_mode;
  uint8_t bits_per_sample;
  int quality_mode_index;
  int pcm_wlength;
  LDACBT_SMPL_FMT_T pcm_fmt;
} tA2DP_LDAC_ENCODER_PARAMS;

typedef struct {
  float counter;
  uint32_t bytes_per_tick; /* pcm bytes read each media task tick */
  uint64_t last_frame_us;
} tA2DP_LDAC_FEEDING_STATE;

typedef struct {
  uint64_t session_start_us;

  size_t media_read_total_expected_packets;
  size_t media_read_total_expected_reads_count;
  size_t media_read_total_expected_read_bytes;

  size_t media_read_total_dropped_packets;
  size_t media_read_total_actual_reads_count;
  size_t media_read_total_actual_read_bytes;
} a2dp_ldac_encoder_stats_t;

typedef struct {
  a2dp_source_read_callback_t read_callback;
  a2dp_source_enqueue_callback_t enqueue_callback;
  uint16_t TxAaMtuSize;
  size_t TxQueueLength;

  bool use_SCMS_T;
  tA2DP_ENCODER_INIT_PEER_PARAMS peer_params;
  uint32_t timestamp;        // Timestamp for the A2DP frames

  HANDLE_LDAC_BT ldac_handle;
  bool has_ldac_handle;  // True if ldac_handle is valid

  HANDLE_LDAC_ABR ldac_abr_handle;
  bool has_ldac_abr_handle;
  int last_ldac_abr_eqmid;
  size_t ldac_abr_adjustments;

  tA2DP_FEEDING_PARAMS feeding_params;
  tA2DP_LDAC_ENCODER_PARAMS ldac_encoder_params;
  tA2DP_LDAC_FEEDING_STATE ldac_feeding_state;

  a2dp_ldac_encoder_stats_t stats;
} tA2DP_LDAC_ENCODER_CB;

static bool ldac_abr_loaded = true;  // the library is statically linked

static tA2DP_LDAC_ENCODER_CB a2dp_ldac_encoder_cb;

static void a2dp_vendor_ldac_encoder_update(A2dpCodecConfig* a2dp_codec_config,
                                            bool* p_restart_input,
                                            bool* p_restart_output,
                                            bool* p_config_updated);
static void a2dp_ldac_get_num_frame_iteration(uint8_t* num_of_iterations,
                                              uint8_t* num_of_frames,
                                              uint64_t timestamp_us);
static void a2dp_ldac_encode_frames(uint8_t nb_frame);
static bool a2dp_ldac_read_feeding(uint8_t* read_buffer, uint32_t* bytes_read);
static uint16_t adjust_effective_mtu(
    const tA2DP_ENCODER_INIT_PEER_PARAMS& peer_params);
static std::string quality_mode_index_to_name(int quality_mode_index);

bool A2DP_VendorLoadEncoderLdac(void) {
  // Nothing to do - the library is statically linked
  return true;
}

void A2DP_VendorUnloadEncoderLdac(void) {
  // Cleanup any LDAC-related state
  a2dp_vendor_ldac_encoder_cleanup();
}

void a2dp_vendor_ldac_encoder_init(
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    A2dpCodecConfig* a2dp_codec_config,
    a2dp_source_read_callback_t read_callback,
    a2dp_source_enqueue_callback_t enqueue_callback) {
  a2dp_vendor_ldac_encoder_cleanup();

  a2dp_ldac_encoder_cb.stats.session_start_us =
      bluetooth::common::time_get_os_boottime_us();

  a2dp_ldac_encoder_cb.read_callback = read_callback;
  a2dp_ldac_encoder_cb.enqueue_callback = enqueue_callback;
  a2dp_ldac_encoder_cb.peer_params = *p_peer_params;
  a2dp_ldac_encoder_cb.timestamp = 0;
  a2dp_ldac_encoder_cb.ldac_abr_handle = NULL;
  a2dp_ldac_encoder_cb.has_ldac_abr_handle = false;
  a2dp_ldac_encoder_cb.last_ldac_abr_eqmid = -1;
  a2dp_ldac_encoder_cb.ldac_abr_adjustments = 0;

  a2dp_ldac_encoder_cb.use_SCMS_T = false;

  // NOTE: Ignore the restart_input / restart_output flags - this initization
  // happens when the audio session is (re)started.
  bool restart_input = false;
  bool restart_output = false;
  bool config_updated = false;
  a2dp_vendor_ldac_encoder_update(a2dp_codec_config, &restart_input,
                                  &restart_output, &config_updated);
}

// Update the A2DP LDAC encoder.
// |a2dp_codec_config| is the A2DP codec to use for the update.
static void a2dp_vendor_ldac_encoder_update(A2dpCodecConfig* a2dp_codec_config,
                                            bool* p_restart_input,
                                            bool* p_restart_output,
                                            bool* p_config_updated) {
  tA2DP_LDAC_ENCODER_PARAMS* p_encoder_params =
      &a2dp_ldac_encoder_cb.ldac_encoder_params;
  uint8_t codec_info[AVDT_CODEC_SIZE];

  *p_restart_input = false;
  *p_restart_output = false;
  *p_config_updated = false;

  if (!a2dp_ldac_encoder_cb.has_ldac_handle) {
    a2dp_ldac_encoder_cb.ldac_handle = ldacBT_get_handle();
    if (a2dp_ldac_encoder_cb.ldac_handle == NULL) {
      log::error("Cannot get LDAC encoder handle");
      return;  // TODO: Return an error?
    }
    a2dp_ldac_encoder_cb.has_ldac_handle = true;
  }
  CHECK(a2dp_ldac_encoder_cb.ldac_handle != nullptr);

  if (!a2dp_codec_config->copyOutOtaCodecConfig(codec_info)) {
    log::error("Cannot update the codec encoder for {}: invalid codec config",
               a2dp_codec_config->name().c_str());
    return;
  }
  const uint8_t* p_codec_info = codec_info;
  btav_a2dp_codec_config_t codec_config = a2dp_codec_config->getCodecConfig();

  // The feeding parameters
  tA2DP_FEEDING_PARAMS* p_feeding_params = &a2dp_ldac_encoder_cb.feeding_params;
  p_feeding_params->sample_rate =
      A2DP_VendorGetTrackSampleRateLdac(p_codec_info);
  p_feeding_params->bits_per_sample =
      a2dp_codec_config->getAudioBitsPerSample();
  p_feeding_params->channel_count =
      A2DP_VendorGetTrackChannelCountLdac(p_codec_info);
  log::info("sample_rate={} bits_per_sample={} channel_count={}",
            p_feeding_params->sample_rate, p_feeding_params->bits_per_sample,
            p_feeding_params->channel_count);
  a2dp_vendor_ldac_feeding_reset();

  // The codec parameters
  p_encoder_params->sample_rate =
      a2dp_ldac_encoder_cb.feeding_params.sample_rate;
  p_encoder_params->channel_mode =
      A2DP_VendorGetChannelModeCodeLdac(p_codec_info);

  // Set the quality mode index
  int old_quality_mode_index = p_encoder_params->quality_mode_index;
  if (codec_config.codec_specific_1 != 0) {
    p_encoder_params->quality_mode_index = codec_config.codec_specific_1 % 10;
    log::info("setting quality mode to {}",
              quality_mode_index_to_name(p_encoder_params->quality_mode_index)
                  .c_str());
  } else {
    p_encoder_params->quality_mode_index = A2DP_LDAC_QUALITY_ABR;
    log::info("setting quality mode to default {}",
              quality_mode_index_to_name(p_encoder_params->quality_mode_index)
                  .c_str());
  }

  int ldac_eqmid = LDAC_ABR_MODE_EQMID;
  if (p_encoder_params->quality_mode_index == A2DP_LDAC_QUALITY_ABR) {
    if (!ldac_abr_loaded) {
      p_encoder_params->quality_mode_index = A2DP_LDAC_QUALITY_MID;
      log::warn("LDAC ABR library is not loaded, resetting quality mode to {}",
                quality_mode_index_to_name(p_encoder_params->quality_mode_index)
                    .c_str());
    } else {
      log::info("changing mode from {} to {}",
                quality_mode_index_to_name(old_quality_mode_index).c_str(),
                quality_mode_index_to_name(p_encoder_params->quality_mode_index)
                    .c_str());
      if (a2dp_ldac_encoder_cb.ldac_abr_handle != NULL) {
        log::info("already in LDAC ABR mode, do nothing.");
      } else {
        log::info("get and init LDAC ABR handle.");
        a2dp_ldac_encoder_cb.ldac_abr_handle = ldac_ABR_get_handle();
        if (a2dp_ldac_encoder_cb.ldac_abr_handle != NULL) {
          a2dp_ldac_encoder_cb.has_ldac_abr_handle = true;
          a2dp_ldac_encoder_cb.last_ldac_abr_eqmid = -1;
          a2dp_ldac_encoder_cb.ldac_abr_adjustments = 0;
          ldac_ABR_Init(a2dp_ldac_encoder_cb.ldac_abr_handle,
                        A2DP_LDAC_ENCODER_INTERVAL_MS);
        } else {
          p_encoder_params->quality_mode_index = A2DP_LDAC_QUALITY_MID;
          log::info(
              "get LDAC ABR handle failed, resetting quality mode to {}.",
              quality_mode_index_to_name(p_encoder_params->quality_mode_index)
                  .c_str());
        }
      }
    }
  } else {
    ldac_eqmid = p_encoder_params->quality_mode_index;
    log::info("in {} mode, free LDAC ABR handle.",
              quality_mode_index_to_name(ldac_eqmid).c_str());
    if (a2dp_ldac_encoder_cb.has_ldac_abr_handle) {
      ldac_ABR_free_handle(a2dp_ldac_encoder_cb.ldac_abr_handle);
      a2dp_ldac_encoder_cb.ldac_abr_handle = NULL;
      a2dp_ldac_encoder_cb.has_ldac_abr_handle = false;
      a2dp_ldac_encoder_cb.last_ldac_abr_eqmid = -1;
      a2dp_ldac_encoder_cb.ldac_abr_adjustments = 0;
    }
  }

  if (p_encoder_params->quality_mode_index != old_quality_mode_index)
    *p_config_updated = true;

  p_encoder_params->pcm_wlength =
      a2dp_ldac_encoder_cb.feeding_params.bits_per_sample >> 3;
  // Set the Audio format from pcm_wlength
  p_encoder_params->pcm_fmt = LDACBT_SMPL_FMT_S16;
  if (p_encoder_params->pcm_wlength == 2)
    p_encoder_params->pcm_fmt = LDACBT_SMPL_FMT_S16;
  else if (p_encoder_params->pcm_wlength == 3)
    p_encoder_params->pcm_fmt = LDACBT_SMPL_FMT_S24;
  else if (p_encoder_params->pcm_wlength == 4)
    p_encoder_params->pcm_fmt = LDACBT_SMPL_FMT_S32;

  const tA2DP_ENCODER_INIT_PEER_PARAMS& peer_params =
      a2dp_ldac_encoder_cb.peer_params;
  a2dp_ldac_encoder_cb.TxAaMtuSize = adjust_effective_mtu(peer_params);
  log::info("MTU={}, peer_mtu={}", a2dp_ldac_encoder_cb.TxAaMtuSize,
            peer_params.peer_mtu);
  log::info(
      "sample_rate: {} channel_mode: {} quality_mode_index: {} pcm_wlength: {} "
      "pcm_fmt: {}",
      p_encoder_params->sample_rate, p_encoder_params->channel_mode,
      p_encoder_params->quality_mode_index, p_encoder_params->pcm_wlength,
      p_encoder_params->pcm_fmt);

  // Initialize the encoder.
  // NOTE: MTU in the initialization must include the AVDT media header size.
  int result = ldacBT_init_handle_encode(
      a2dp_ldac_encoder_cb.ldac_handle,
      a2dp_ldac_encoder_cb.TxAaMtuSize + AVDT_MEDIA_HDR_SIZE, ldac_eqmid,
      p_encoder_params->channel_mode, p_encoder_params->pcm_fmt,
      p_encoder_params->sample_rate);
  if (result != 0) {
    int err_code = ldacBT_get_error_code(a2dp_ldac_encoder_cb.ldac_handle);
    log::error(
        "error initializing the LDAC encoder: {} api_error = {} handle_error = "
        "{} block_error = {} error_code = 0x{:x}",
        result, LDACBT_API_ERR(err_code), LDACBT_HANDLE_ERR(err_code),
        LDACBT_BLOCK_ERR(err_code), err_code);
  }
}

void a2dp_vendor_ldac_encoder_cleanup(void) {
  if (a2dp_ldac_encoder_cb.has_ldac_abr_handle) {
    ldac_ABR_free_handle(a2dp_ldac_encoder_cb.ldac_abr_handle);
  }
  if (a2dp_ldac_encoder_cb.has_ldac_handle) {
    ldacBT_free_handle(a2dp_ldac_encoder_cb.ldac_handle);
  }
  memset(&a2dp_ldac_encoder_cb, 0, sizeof(a2dp_ldac_encoder_cb));
}

void a2dp_vendor_ldac_feeding_reset(void) {
  /* By default, just clear the entire state */
  memset(&a2dp_ldac_encoder_cb.ldac_feeding_state, 0,
         sizeof(a2dp_ldac_encoder_cb.ldac_feeding_state));

  a2dp_ldac_encoder_cb.ldac_feeding_state.bytes_per_tick =
      (a2dp_ldac_encoder_cb.feeding_params.sample_rate *
       a2dp_ldac_encoder_cb.feeding_params.bits_per_sample / 8 *
       a2dp_ldac_encoder_cb.feeding_params.channel_count *
       A2DP_LDAC_ENCODER_INTERVAL_MS) /
      1000;

  log::info("PCM bytes per tick {}",
            a2dp_ldac_encoder_cb.ldac_feeding_state.bytes_per_tick);
}

void a2dp_vendor_ldac_feeding_flush(void) {
  a2dp_ldac_encoder_cb.ldac_feeding_state.counter = 0.0f;
}

uint64_t a2dp_vendor_ldac_get_encoder_interval_ms(void) {
  return A2DP_LDAC_ENCODER_INTERVAL_MS;
}

int a2dp_vendor_ldac_get_effective_frame_size() {
  return a2dp_ldac_encoder_cb.TxAaMtuSize;
}

void a2dp_vendor_ldac_send_frames(uint64_t timestamp_us) {
  uint8_t nb_frame = 0;
  uint8_t nb_iterations = 0;

  a2dp_ldac_get_num_frame_iteration(&nb_iterations, &nb_frame, timestamp_us);
  log::verbose("Sending {} frames per iteration, {} iterations", nb_frame,
               nb_iterations);
  if (nb_frame == 0) return;

  for (uint8_t counter = 0; counter < nb_iterations; counter++) {
    if (a2dp_ldac_encoder_cb.has_ldac_abr_handle) {
      int flag_enable = 1;
      int prev_eqmid = a2dp_ldac_encoder_cb.last_ldac_abr_eqmid;
      a2dp_ldac_encoder_cb.last_ldac_abr_eqmid =
          ldac_ABR_Proc(a2dp_ldac_encoder_cb.ldac_handle,
                        a2dp_ldac_encoder_cb.ldac_abr_handle,
                        a2dp_ldac_encoder_cb.TxQueueLength, flag_enable);
      if (prev_eqmid != a2dp_ldac_encoder_cb.last_ldac_abr_eqmid)
        a2dp_ldac_encoder_cb.ldac_abr_adjustments++;
#ifdef __ANDROID__
      ATRACE_INT("LDAC ABR level", a2dp_ldac_encoder_cb.last_ldac_abr_eqmid);
#endif
    }
    // Transcode frame and enqueue
    a2dp_ldac_encode_frames(nb_frame);
  }
}

// Obtains the number of frames to send and number of iterations
// to be used. |num_of_iterations| and |num_of_frames| parameters
// are used as output param for returning the respective values.
static void a2dp_ldac_get_num_frame_iteration(uint8_t* num_of_iterations,
                                              uint8_t* num_of_frames,
                                              uint64_t timestamp_us) {
  uint32_t result = 0;
  uint8_t nof = 0;
  uint8_t noi = 1;

  uint32_t pcm_bytes_per_frame =
      A2DP_LDAC_MEDIA_BYTES_PER_FRAME *
      a2dp_ldac_encoder_cb.feeding_params.channel_count *
      a2dp_ldac_encoder_cb.feeding_params.bits_per_sample / 8;
  log::verbose("pcm_bytes_per_frame {}", pcm_bytes_per_frame);

  uint32_t us_this_tick = A2DP_LDAC_ENCODER_INTERVAL_MS * 1000;
  uint64_t now_us = timestamp_us;
  if (a2dp_ldac_encoder_cb.ldac_feeding_state.last_frame_us != 0)
    us_this_tick =
        (now_us - a2dp_ldac_encoder_cb.ldac_feeding_state.last_frame_us);
  a2dp_ldac_encoder_cb.ldac_feeding_state.last_frame_us = now_us;

  a2dp_ldac_encoder_cb.ldac_feeding_state.counter +=
      (float)a2dp_ldac_encoder_cb.ldac_feeding_state.bytes_per_tick * us_this_tick /
      (A2DP_LDAC_ENCODER_INTERVAL_MS * 1000);

  result =
      a2dp_ldac_encoder_cb.ldac_feeding_state.counter / pcm_bytes_per_frame;
  a2dp_ldac_encoder_cb.ldac_feeding_state.counter -=
      result * pcm_bytes_per_frame;
  nof = result;

  log::verbose("effective num of frames {}, iterations {}", nof, noi);

  *num_of_frames = nof;
  *num_of_iterations = noi;
}

static void a2dp_ldac_encode_frames(uint8_t nb_frame) {
  tA2DP_LDAC_ENCODER_PARAMS* p_encoder_params =
      &a2dp_ldac_encoder_cb.ldac_encoder_params;
  uint8_t remain_nb_frame = nb_frame;
  uint16_t ldac_frame_size;
  uint8_t read_buffer[LDACBT_MAX_LSU * 4 /* byte/sample */ * 2 /* ch */];

  switch (p_encoder_params->sample_rate) {
    case 176400:
    case 192000:
      ldac_frame_size = 512;  // sample/ch
      break;
    case 88200:
    case 96000:
      ldac_frame_size = 256;  // sample/ch
      break;
    case 44100:
    case 48000:
    default:
      ldac_frame_size = 128;  // sample/ch
      break;
  }

  uint32_t count;
  int32_t encode_count = 0;
  int32_t out_frames = 0;
  int written = 0;

  uint32_t bytes_read = 0;
  while (nb_frame) {
    BT_HDR* p_buf = (BT_HDR*)osi_malloc(BT_DEFAULT_BUFFER_SIZE);
    p_buf->offset = A2DP_LDAC_OFFSET;
    p_buf->len = 0;
    p_buf->layer_specific = 0;
    a2dp_ldac_encoder_cb.stats.media_read_total_expected_packets++;

    count = 0;
    do {
      //
      // Read the PCM data and encode it
      //
      uint32_t temp_bytes_read = 0;
      if (a2dp_ldac_read_feeding(read_buffer, &temp_bytes_read)) {
        bytes_read += temp_bytes_read;
        uint8_t* packet = (uint8_t*)(p_buf + 1) + p_buf->offset + p_buf->len;
        if (a2dp_ldac_encoder_cb.ldac_handle == NULL) {
          log::error("invalid LDAC handle");
          a2dp_ldac_encoder_cb.stats.media_read_total_dropped_packets++;
          osi_free(p_buf);
          return;
        }
        int result = ldacBT_encode(
            a2dp_ldac_encoder_cb.ldac_handle, read_buffer, (int*)&encode_count,
            packet + count, (int*)&written, (int*)&out_frames);
        if (result != 0) {
          int err_code =
              ldacBT_get_error_code(a2dp_ldac_encoder_cb.ldac_handle);
          log::error(
              "LDAC encoding error: {} api_error = {} handle_error = {} "
              "block_error = {} error_code = 0x{:x}",
              result, LDACBT_API_ERR(err_code), LDACBT_HANDLE_ERR(err_code),
              LDACBT_BLOCK_ERR(err_code), err_code);
          a2dp_ldac_encoder_cb.stats.media_read_total_dropped_packets++;
          osi_free(p_buf);
          return;
        }
        count += written;
        p_buf->len += written;
        nb_frame--;
        p_buf->layer_specific += out_frames;  // added a frame to the buffer
      } else {
        log::warn("underflow {}", nb_frame);
        a2dp_ldac_encoder_cb.ldac_feeding_state.counter +=
            nb_frame * LDACBT_ENC_LSU *
            a2dp_ldac_encoder_cb.feeding_params.channel_count *
            a2dp_ldac_encoder_cb.feeding_params.bits_per_sample / 8;

        // no more pcm to read
        nb_frame = 0;
      }
    } while ((written == 0) && nb_frame);

    if (p_buf->len) {
      /*
       * Timestamp of the media packet header represent the TS of the
       * first frame, i.e the timestamp before including this frame.
       */
      *((uint32_t*)(p_buf + 1)) = a2dp_ldac_encoder_cb.timestamp;

      a2dp_ldac_encoder_cb.timestamp += p_buf->layer_specific * ldac_frame_size;

      uint8_t done_nb_frame = remain_nb_frame - nb_frame;
      remain_nb_frame = nb_frame;
      if (!a2dp_ldac_encoder_cb.enqueue_callback(p_buf, done_nb_frame,
                                                 bytes_read))
        return;
    } else {
      // NOTE: Unlike the execution path for other codecs, it is normal for
      // LDAC to NOT write encoded data to the last buffer if there wasn't
      // enough data to write to. That data is accumulated internally by
      // the codec and included in the next iteration. Therefore, here we
      // don't increment the "media_read_total_dropped_packets" counter.
      osi_free(p_buf);
    }
  }
}

static bool a2dp_ldac_read_feeding(uint8_t* read_buffer, uint32_t* bytes_read) {
  uint32_t read_size = LDACBT_ENC_LSU *
                       a2dp_ldac_encoder_cb.feeding_params.channel_count *
                       a2dp_ldac_encoder_cb.feeding_params.bits_per_sample / 8;

  a2dp_ldac_encoder_cb.stats.media_read_total_expected_reads_count++;
  a2dp_ldac_encoder_cb.stats.media_read_total_expected_read_bytes += read_size;

  /* Read Data from UIPC channel */
  uint32_t nb_byte_read =
      a2dp_ldac_encoder_cb.read_callback(read_buffer, read_size);
  a2dp_ldac_encoder_cb.stats.media_read_total_actual_read_bytes += nb_byte_read;

  if (nb_byte_read < read_size) {
    if (nb_byte_read == 0) return false;

    /* Fill the unfilled part of the read buffer with silence (0) */
    memset(((uint8_t*)read_buffer) + nb_byte_read, 0, read_size - nb_byte_read);
    nb_byte_read = read_size;
  }
  a2dp_ldac_encoder_cb.stats.media_read_total_actual_reads_count++;

  *bytes_read = nb_byte_read;
  return true;
}

static uint16_t adjust_effective_mtu(
    const tA2DP_ENCODER_INIT_PEER_PARAMS& peer_params) {
  uint16_t mtu_size =
      BT_DEFAULT_BUFFER_SIZE - A2DP_LDAC_OFFSET - sizeof(BT_HDR);
  if (mtu_size > peer_params.peer_mtu) {
    mtu_size = peer_params.peer_mtu;
  }
  log::verbose("original AVDTP MTU size: {}", mtu_size);
  return mtu_size;
}

static std::string quality_mode_index_to_name(int quality_mode_index) {
  switch (quality_mode_index) {
    case A2DP_LDAC_QUALITY_HIGH:
      return "HIGH";
    case A2DP_LDAC_QUALITY_MID:
      return "MID";
    case A2DP_LDAC_QUALITY_LOW:
      return "LOW";
    case A2DP_LDAC_QUALITY_ABR:
      return "ABR";
    default:
      return "Unknown";
  }
}

void a2dp_vendor_ldac_set_transmit_queue_length(size_t transmit_queue_length) {
  a2dp_ldac_encoder_cb.TxQueueLength = transmit_queue_length;
}

void A2dpCodecConfigLdacSource::debug_codec_dump(int fd) {
  a2dp_ldac_encoder_stats_t* stats = &a2dp_ldac_encoder_cb.stats;
  tA2DP_LDAC_ENCODER_PARAMS* p_encoder_params =
      &a2dp_ldac_encoder_cb.ldac_encoder_params;

  A2dpCodecConfig::debug_codec_dump(fd);

  dprintf(
      fd, "  LDAC quality mode                                       : %s\n",
      quality_mode_index_to_name(p_encoder_params->quality_mode_index).c_str());

  dprintf(fd,
          "  LDAC transmission bitrate (Kbps)                        : %d\n",
          ldacBT_get_bitrate(a2dp_ldac_encoder_cb.ldac_handle));

  dprintf(fd,
          "  LDAC saved transmit queue length                        : %zu\n",
          a2dp_ldac_encoder_cb.TxQueueLength);
  if (a2dp_ldac_encoder_cb.has_ldac_abr_handle) {
    dprintf(fd,
            "  LDAC adaptive bit rate encode quality mode index        : %d\n",
            a2dp_ldac_encoder_cb.last_ldac_abr_eqmid);
    dprintf(fd,
            "  LDAC adaptive bit rate adjustments                      : %zu\n",
            a2dp_ldac_encoder_cb.ldac_abr_adjustments);
  }
  dprintf(fd, "  Encoder interval (ms): %" PRIu64 "\n",
          a2dp_vendor_ldac_get_encoder_interval_ms());
  dprintf(fd, "  Effective MTU: %d\n",
          a2dp_vendor_ldac_get_effective_frame_size());
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
