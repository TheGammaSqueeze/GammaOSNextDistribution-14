/*
 * Copyright 2023 The Android Open Source Project
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

#include "mmc/codec_server/a2dp_aac_mmc_encoder.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/common.h>
#include <libavutil/frame.h>
#include <libavutil/samplefmt.h>
}

#include <base/logging.h>

#include "a2dp_aac.h"
#include "mmc/proto/mmc_config.pb.h"

namespace mmc {
namespace {

const int A2DP_AAC_HEADER_LEN = 9;
const int A2DP_AAC_MAX_LEN_REPR = 4;
const int A2DP_AAC_MAX_PREFIX_SIZE =
    AVDT_MEDIA_HDR_SIZE + A2DP_AAC_HEADER_LEN + A2DP_AAC_MAX_LEN_REPR;

constexpr uint8_t A2DP_AAC_HEADER_44100[A2DP_AAC_HEADER_LEN] = {
    0x47, 0xfc, 0x00, 0x00, 0xb0, 0x90, 0x80, 0x03, 0x00,
};
constexpr uint8_t A2DP_AAC_HEADER_48000[A2DP_AAC_HEADER_LEN] = {
    0x47, 0xfc, 0x00, 0x00, 0xb0, 0x8c, 0x80, 0x03, 0x00,
};
}  // namespace

A2dpAacEncoder::A2dpAacEncoder() : avctx_(nullptr) {}

A2dpAacEncoder::~A2dpAacEncoder() { cleanup(); }

int A2dpAacEncoder::init(ConfigParam config) {
  if (!config.has_a2dp_aac_encoder_param()) {
    LOG(ERROR) << "A2DP AAC Encoder params are not set";
    return -EINVAL;
  }

  const AVCodec* codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
  if (!codec) {
    LOG(ERROR) << "Codec not found";
    return -ENOENT;
  }

  if (!avctx_) {
    avctx_ = avcodec_alloc_context3(codec);
    if (!avctx_) {
      LOG(ERROR) << "Cannot allocate context";
      return -EINVAL;
    }
  }

  param_ = config.a2dp_aac_encoder_param();
  const int channel_count = param_.channel_count();
  const int sample_rate = param_.sample_rate();
  const int bit_rate = param_.bit_rate();

  if (channel_count == 1) {
    AVChannelLayout mono = AV_CHANNEL_LAYOUT_MONO;
    av_channel_layout_copy(&avctx_->ch_layout, &mono);
  } else if (channel_count == 2) {
    AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
    av_channel_layout_copy(&avctx_->ch_layout, &stereo);
  } else {
    LOG(ERROR) << "Invalid number of channels: " << channel_count;
    return -EINVAL;
  }

  if (sample_rate != 44100 && sample_rate != 48000) {
    LOG(ERROR) << "Unsupported sample rate: " << sample_rate;
    return -EINVAL;
  }

  avctx_->sample_rate = sample_rate;
  avctx_->bit_rate = bit_rate;
  avctx_->bit_rate_tolerance = 0;
  avctx_->sample_fmt = AV_SAMPLE_FMT_FLTP;

  int rc = avcodec_open2(avctx_, codec, NULL);
  if (rc < 0) {
    LOG(ERROR) << "Could not open context: " << rc;
    return -EINVAL;
  }

  return avctx_->frame_size;
}

void A2dpAacEncoder::cleanup() {
  if (avctx_) {
    avcodec_free_context(&avctx_);
    avctx_ = nullptr;
  }
}

int A2dpAacEncoder::transcode(uint8_t* i_buf, int i_len, uint8_t* o_buf,
                              int o_len) {
  int rc;

  AVFrame* frame = av_frame_alloc();
  if (!frame) {
    LOG(ERROR) << "Could not alloc frame";
    return -ENOMEM;
  }

  frame->nb_samples = avctx_->frame_size;
  frame->format = avctx_->sample_fmt;
  frame->sample_rate = avctx_->sample_rate;

  rc = av_channel_layout_copy(&frame->ch_layout, &avctx_->ch_layout);
  if (rc < 0) {
    LOG(ERROR) << "Failed to copy channel layout: " << rc;
    av_frame_free(&frame);
    return -EINVAL;
  }

  rc = av_frame_get_buffer(frame, 0);
  if (rc < 0) {
    LOG(ERROR) << "Failed to get buffer for frame: " << rc;
    av_frame_free(&frame);
    return -EIO;
  }

  rc = av_frame_make_writable(frame);
  if (rc < 0) {
    LOG(ERROR) << "Failed to make frame writable: " << rc;
    av_frame_free(&frame);
    return -EIO;
  }

  const int bit_depth = param_.bit_depth();
  const int bytes_per_sample = bit_depth / 8;
  const float scaling_factor = (float)1 / (1 << (bit_depth - 1));

  uint8_t* buff = i_buf;
  float* data[] = {(float*)frame->data[0], (float*)frame->data[1]};

  auto read_pcm = [](uint8_t* buff, int nbits) -> int {
    int pcm = 0;

    switch (nbits) {
      case 16:
        pcm = *((int16_t*)buff);
        break;
      case 24:
        pcm = *buff | *(buff + 1) << 8 | *(buff + 2) << 16;
        pcm |= pcm & 0x00800000 ? 0xff000000 : 0;
        break;
      case 32:
        pcm = *((int32_t*)buff);
        break;
      default:
        LOG_ASSERT(false) << "Attempting to read " << nbits
                          << " bits as bit depth";
    }

    return pcm;
  };

  for (int i = 0; i < i_len / bytes_per_sample; ++i) {
    *data[i & 1]++ = read_pcm(buff, bit_depth) * scaling_factor;
    buff += bytes_per_sample;
  }

  AVPacket* pkt = av_packet_alloc();
  if (!pkt) {
    LOG(ERROR) << "Could not alloc packet";
    return -ENOMEM;
  }

  rc = avcodec_send_frame(avctx_, frame);
  if (rc < 0) {
    LOG(ERROR) << "Failed to send frame: " << rc;
    av_frame_free(&frame);
    av_packet_free(&pkt);
    return -EIO;
  }

  rc = avcodec_receive_packet(avctx_, pkt);
  if (rc < 0 && rc != -EAGAIN) {
    LOG(ERROR) << "Failed to receive packet: " << rc;
    av_frame_free(&frame);
    av_packet_free(&pkt);
    return -EIO;
  }

  uint8_t* dst = o_buf;

  const uint8_t* header = avctx_->sample_rate == 44100 ? A2DP_AAC_HEADER_44100
                                                       : A2DP_AAC_HEADER_48000;

  std::copy(header, header + A2DP_AAC_HEADER_LEN, dst);

  int written = A2DP_AAC_HEADER_LEN;
  dst += written;

  int cap = param_.effective_frame_size();
  if (rc == -EAGAIN || cap < pkt->size + A2DP_AAC_MAX_PREFIX_SIZE) {
    if (rc != -EAGAIN) {
      LOG(WARNING) << "Dropped pkt: size=" << pkt->size << ", cap=" << cap;
    }
    static uint8_t silent_frame[7] = {
        0x06, 0x21, 0x10, 0x04, 0x60, 0x8c, 0x1c,
    };
    std::copy(silent_frame, std::end(silent_frame), dst);
    dst += sizeof(silent_frame);
    written += sizeof(silent_frame);
  } else {
    int fsize = pkt->size;

    while (fsize >= 255) {
      *(dst++) = 0xff;
      fsize -= 255;
      ++written;
    }
    *(dst++) = fsize;
    ++written;

    std::copy(pkt->data, pkt->data + pkt->size, dst);
    written += pkt->size;
  }

  av_packet_unref(pkt);
  av_frame_free(&frame);
  av_packet_free(&pkt);

  return written;
}

}  // namespace mmc
