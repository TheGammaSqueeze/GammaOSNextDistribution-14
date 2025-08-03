/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "a2dp_opus_decoder"

#include "a2dp_vendor_opus_decoder.h"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <opus.h>

#include "a2dp_vendor_opus.h"
#include "os/log.h"
#include "osi/include/allocator.h"

using namespace bluetooth;

typedef struct {
  OpusDecoder* opus_handle = nullptr;
  bool has_opus_handle;
  int16_t* decode_buf = nullptr;
  decoded_data_callback_t decode_callback;
} tA2DP_OPUS_DECODER_CB;

static tA2DP_OPUS_DECODER_CB a2dp_opus_decoder_cb;

void a2dp_vendor_opus_decoder_cleanup(void) {
  if (a2dp_opus_decoder_cb.has_opus_handle) {
    osi_free(a2dp_opus_decoder_cb.opus_handle);

    if (a2dp_opus_decoder_cb.decode_buf != nullptr) {
      memset(a2dp_opus_decoder_cb.decode_buf, 0,
             A2DP_OPUS_DECODE_BUFFER_LENGTH);
      osi_free(a2dp_opus_decoder_cb.decode_buf);
      a2dp_opus_decoder_cb.decode_buf = nullptr;
    }
    a2dp_opus_decoder_cb.has_opus_handle = false;
  }

  return;
}

bool a2dp_vendor_opus_decoder_init(decoded_data_callback_t decode_callback) {
  a2dp_vendor_opus_decoder_cleanup();

  int32_t err_val = OPUS_OK;
  int32_t size = 0;

  size = opus_decoder_get_size(A2DP_OPUS_CODEC_OUTPUT_CHS);
  a2dp_opus_decoder_cb.opus_handle =
      static_cast<OpusDecoder*>(osi_malloc(size));
  if (a2dp_opus_decoder_cb.opus_handle == nullptr) {
    log::error("failed to allocate opus decoder handle");
    return false;
  }
  err_val = opus_decoder_init(a2dp_opus_decoder_cb.opus_handle,
                              A2DP_OPUS_CODEC_DEFAULT_SAMPLERATE,
                              A2DP_OPUS_CODEC_OUTPUT_CHS);
  if (err_val == OPUS_OK) {
    a2dp_opus_decoder_cb.has_opus_handle = true;

    a2dp_opus_decoder_cb.decode_buf =
        static_cast<int16_t*>(osi_malloc(A2DP_OPUS_DECODE_BUFFER_LENGTH));

    memset(a2dp_opus_decoder_cb.decode_buf, 0, A2DP_OPUS_DECODE_BUFFER_LENGTH);

    a2dp_opus_decoder_cb.decode_callback = decode_callback;
    log::info("decoder init success");
    return true;
  } else {
    log::error("failed to initialize Opus Decoder");
    a2dp_opus_decoder_cb.has_opus_handle = false;
    return false;
  }

  return false;
}

void a2dp_vendor_opus_decoder_configure(const uint8_t* p_codec_info) { return; }

bool a2dp_vendor_opus_decoder_decode_packet(BT_HDR* p_buf) {
  uint32_t frameSize;
  uint32_t numChannels;
  uint32_t numFrames;
  int32_t ret_val = 0;
  uint32_t frameLen = 0;

  if (p_buf == nullptr) {
    log::error("Dropping packet with nullptr");
    return false;
  }

  if (p_buf->len == 0) {
    log::error("Empty packet");
    return false;
  }

  auto* pBuffer =
      reinterpret_cast<unsigned char*>(p_buf->data + p_buf->offset + 1);
  int32_t bufferSize = p_buf->len - 1;

  numChannels = opus_packet_get_nb_channels(pBuffer);
  numFrames = opus_packet_get_nb_frames(pBuffer, bufferSize);
  frameSize = opus_packet_get_samples_per_frame(
      pBuffer, A2DP_OPUS_CODEC_DEFAULT_SAMPLERATE);
  frameLen = opus_packet_get_nb_samples(pBuffer, bufferSize,
                                        A2DP_OPUS_CODEC_DEFAULT_SAMPLERATE);
  uint32_t num_frames = pBuffer[0] & 0xf;

  log::error("numframes {} framesize {} framelen {} bufferSize {}", num_frames,
             frameSize, frameLen, bufferSize);
  log::error("numChannels {} numFrames {} offset {}", numChannels, numFrames,
             p_buf->offset);

  for (uint32_t frame = 0; frame < numFrames; ++frame) {
    {
      numChannels = opus_packet_get_nb_channels(pBuffer);

      ret_val = opus_decode(a2dp_opus_decoder_cb.opus_handle,
                            reinterpret_cast<unsigned char*>(pBuffer),
                            bufferSize, a2dp_opus_decoder_cb.decode_buf,
                            A2DP_OPUS_DECODE_BUFFER_LENGTH, 0 /* flags */);

      if (ret_val < OPUS_OK) {
        log::error("Opus DecodeFrame failed {}, applying concealment", ret_val);
        ret_val = opus_decode(a2dp_opus_decoder_cb.opus_handle, NULL, 0,
                              a2dp_opus_decoder_cb.decode_buf,
                              A2DP_OPUS_DECODE_BUFFER_LENGTH, 0 /* flags */);
      }

      if (ret_val < OPUS_OK) {
        log::error("Opus DecodeFrame retry failed with {}, dropping packet",
                   ret_val);
        return false;
      }

      size_t frame_len =
          ret_val * numChannels * sizeof(a2dp_opus_decoder_cb.decode_buf[0]);
      a2dp_opus_decoder_cb.decode_callback(
          reinterpret_cast<uint8_t*>(a2dp_opus_decoder_cb.decode_buf),
          frame_len);
    }
  }
  return true;
}

void a2dp_vendor_opus_decoder_start(void) { return; }

void a2dp_vendor_opus_decoder_suspend(void) {
  int32_t err_val = 0;

  if (a2dp_opus_decoder_cb.has_opus_handle) {
    err_val =
        opus_decoder_ctl(a2dp_opus_decoder_cb.opus_handle, OPUS_RESET_STATE);
    if (err_val != OPUS_OK) {
      log::error("failed to reset decoder");
    }
  }
  return;
}
