/******************************************************************************
 *
 * Copyright (c) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

#include "codec_interface.h"

#include <base/logging.h>
#include <lc3.h>

#include <memory>
#include <optional>
#include <vector>

#include "os/log.h"

namespace le_audio {

struct CodecInterface::Impl {
  Impl(const types::LeAudioCodecId& codec_id) : codec_id_(codec_id) {}
  ~Impl() { Cleanup(); }

  bool IsReady() { return pcm_config_.has_value(); };

  CodecInterface::Status InitEncoder(
      const LeAudioCodecConfiguration& pcm_config,
      const LeAudioCodecConfiguration& codec_config) {
    // Output codec configuration
    bt_codec_config_ = codec_config;

    // TODO: For now only blocks_per_sdu = 1 is supported
    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      if (pcm_config_.has_value()) {
        Cleanup();
      }
      pcm_config_ = pcm_config;

      lc3_.pcm_format_ = (pcm_config_->bits_per_sample == 24)
                             ? LC3_PCM_FORMAT_S24
                             : LC3_PCM_FORMAT_S16;

      // Prepare the encoder
      const auto encoder_size = lc3_encoder_size(
          bt_codec_config_.data_interval_us, pcm_config_->sample_rate);
      lc3_.codec_mem_.reset(malloc(encoder_size));
      lc3_.encoder_ = lc3_setup_encoder(
          bt_codec_config_.data_interval_us, bt_codec_config_.sample_rate,
          pcm_config_->sample_rate, lc3_.codec_mem_.get());

      return Status::STATUS_OK;
    }

    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id_.coding_format,
              codec_id_.vendor_company_id, codec_id_.vendor_codec_id);
    return Status::STATUS_ERR_INVALID_CODEC_ID;
  }

  CodecInterface::Status InitDecoder(
      const LeAudioCodecConfiguration& codec_config,
      const LeAudioCodecConfiguration& pcm_config) {
    // Input codec configuration
    bt_codec_config_ = codec_config;

    // TODO: For now only blocks_per_sdu = 1 is supported
    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      if (pcm_config_.has_value()) {
        Cleanup();
      }
      pcm_config_ = pcm_config;

      lc3_.pcm_format_ = (pcm_config_->bits_per_sample == 24)
                             ? LC3_PCM_FORMAT_S24
                             : LC3_PCM_FORMAT_S16;

      // Prepare the decoded output buffer
      output_channel_samples_ = lc3_frame_samples(
          bt_codec_config_.data_interval_us, pcm_config_->sample_rate);
      adjustOutputBufferSizeIfNeeded(&output_channel_data_);

      // Prepare the decoder
      const auto decoder_size = lc3_decoder_size(
          bt_codec_config_.data_interval_us, pcm_config_->sample_rate);
      lc3_.codec_mem_.reset(malloc(decoder_size));
      lc3_.decoder_ = lc3_setup_decoder(
          bt_codec_config_.data_interval_us, bt_codec_config_.sample_rate,
          pcm_config_->sample_rate, lc3_.codec_mem_.get());

      return Status::STATUS_OK;
    }

    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id_.coding_format,
              codec_id_.vendor_company_id, codec_id_.vendor_codec_id);
    return Status::STATUS_ERR_INVALID_CODEC_ID;
  }

  std::vector<int16_t>& GetDecodedSamples() { return output_channel_data_; }
  CodecInterface::Status Decode(uint8_t* data, uint16_t size) {
    if (!IsReady()) {
      LOG_ERROR("decoder not ready");
      return Status::STATUS_ERR_CODEC_NOT_READY;
    }

    // For now only LC3 is supported
    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      adjustOutputBufferSizeIfNeeded(&output_channel_data_);
      auto err = lc3_decode(lc3_.decoder_, data, size, lc3_.pcm_format_,
                            output_channel_data_.data(), 1 /* stride */);
      if (err < 0) {
        LOG(ERROR) << " bad decoding parameters: " << static_cast<int>(err);
        return Status::STATUS_ERR_CODING_ERROR;
      }

      return Status::STATUS_OK;
    }

    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id_.coding_format,
              codec_id_.vendor_company_id, codec_id_.vendor_codec_id);
    return Status::STATUS_ERR_INVALID_CODEC_ID;
  }

  CodecInterface::Status Encode(const uint8_t* data, int stride,
                                uint16_t out_size,
                                std::vector<int16_t>* out_buffer = nullptr,
                                uint16_t out_offset = 0) {
    if (!IsReady()) {
      LOG_ERROR("decoder not ready");
      return Status::STATUS_ERR_CODEC_NOT_READY;
    }

    if (out_size == 0) {
      LOG_ERROR("out_size cannot be 0");
      return Status::STATUS_ERR_CODING_ERROR;
    }

    // For now only LC3 is supported
    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      // Prepare the encoded output buffer
      if (out_buffer == nullptr) {
        out_buffer = &output_channel_data_;
      }

      // We have two bytes per sample in the buffer, while out_size and
      // out_offset are in bytes
      size_t channel_samples = (out_offset + out_size) / 2;
      if (output_channel_samples_ < channel_samples) {
        output_channel_samples_ = channel_samples;
      }
      adjustOutputBufferSizeIfNeeded(out_buffer);

      // Encode
      auto err =
          lc3_encode(lc3_.encoder_, lc3_.pcm_format_, data, stride, out_size,
                     ((uint8_t*)out_buffer->data()) + out_offset);
      if (err < 0) {
        LOG(ERROR) << " bad encoding parameters: " << static_cast<int>(err);
        return Status::STATUS_ERR_CODING_ERROR;
      }

      return Status::STATUS_OK;
    }

    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id_.coding_format,
              codec_id_.vendor_company_id, codec_id_.vendor_codec_id);
    return Status::STATUS_ERR_INVALID_CODEC_ID;
  }

  void Cleanup() {
    pcm_config_ = std::nullopt;
    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      lc3_.Cleanup();
    }
    output_channel_data_.clear();
    output_channel_samples_ = 0;
  }

  uint16_t GetNumOfSamplesPerChannel() {
    if (!IsReady()) {
      LOG_ERROR("decoder not ready");
      return 0;
    }

    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      return lc3_frame_samples(bt_codec_config_.data_interval_us,
                               pcm_config_->sample_rate);
    }

    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id_.coding_format,
              codec_id_.vendor_company_id, codec_id_.vendor_codec_id);
    return 0;
  }

  uint8_t GetNumOfBytesPerSample() {
    if (codec_id_.coding_format == types::kLeAudioCodingFormatLC3) {
      return lc3_.bits_to_bytes_per_sample(bt_codec_config_.bits_per_sample);
    }

    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id_.coding_format,
              codec_id_.vendor_company_id, codec_id_.vendor_codec_id);
    return 0;
  }

 private:
  inline void adjustOutputBufferSizeIfNeeded(std::vector<int16_t>* out_buffer) {
    if (out_buffer->size() < output_channel_samples_) {
      out_buffer->resize(output_channel_samples_);
    }
  }

  // BT codec params set when codec is initialized
  types::LeAudioCodecId codec_id_;
  LeAudioCodecConfiguration bt_codec_config_;
  std::optional<LeAudioCodecConfiguration> pcm_config_;

  // Output buffer
  std::vector<int16_t> output_channel_data_;
  size_t output_channel_samples_ = 0;

  // LC3
  struct lc3_t {
    static inline uint8_t bits_to_bytes_per_sample(uint8_t bits_per_sample) {
      // 24 bit audio stream is sent as unpacked, each sample takes 4 bytes.
      if (bits_per_sample == 24) return 4;
      return bits_per_sample / 8;
    }

    void Cleanup() {
      decoder_ = nullptr;
      encoder_ = nullptr;
      codec_mem_.reset();
    }

    lc3_t() : codec_mem_(nullptr, &std::free) {}
    lc3_pcm_format pcm_format_;
    union {
      lc3_decoder_t decoder_;
      lc3_encoder_t encoder_;
    };
    std::unique_ptr<void, decltype(&std::free)> codec_mem_;
  } lc3_;
};

CodecInterface::CodecInterface(const types::LeAudioCodecId& codec_id) {
  if (codec_id.coding_format == types::kLeAudioCodingFormatLC3) {
    impl = new Impl(codec_id);
  } else {
    LOG_ERROR("Invalid codec ID: [%d:%d:%d]", codec_id.coding_format,
              codec_id.vendor_company_id, codec_id.vendor_codec_id);
  }
}

CodecInterface::~CodecInterface() { delete impl; }

bool CodecInterface::IsReady() { return impl->IsReady(); };
CodecInterface::Status CodecInterface::InitEncoder(
    const LeAudioCodecConfiguration& pcm_config,
    const LeAudioCodecConfiguration& codec_config) {
  return impl->InitEncoder(pcm_config, codec_config);
}
CodecInterface::Status CodecInterface::InitDecoder(
    const LeAudioCodecConfiguration& codec_config,
    const LeAudioCodecConfiguration& pcm_config) {
  return impl->InitDecoder(codec_config, pcm_config);
}
std::vector<int16_t>& CodecInterface::GetDecodedSamples() {
  return impl->GetDecodedSamples();
}
CodecInterface::Status CodecInterface::Decode(uint8_t* data, uint16_t size) {
  return impl->Decode(data, size);
}
CodecInterface::Status CodecInterface::Encode(const uint8_t* data, int stride,
                                              uint16_t out_size,
                                              std::vector<int16_t>* out_buffer,
                                              uint16_t out_offset) {
  return impl->Encode(data, stride, out_size, out_buffer, out_offset);
}
void CodecInterface::Cleanup() { return impl->Cleanup(); }

uint16_t CodecInterface::GetNumOfSamplesPerChannel() {
  return impl->GetNumOfSamplesPerChannel();
};
uint8_t CodecInterface::GetNumOfBytesPerSample() {
  return impl->GetNumOfBytesPerSample();
};

}  // namespace le_audio
