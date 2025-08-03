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

#pragma once

#include <unordered_map>
#include <vector>

#include "audio_aidl_interfaces.h"
#include "include/hardware/bt_av.h"

namespace bluetooth::audio::aidl::a2dp {

using ::aidl::android::hardware::bluetooth::audio::CodecId;
using ::aidl::android::hardware::bluetooth::audio::CodecInfo;

/***
 * Record the provider info returned by the HAL implementer.
 ***/
class ProviderInfo {
 public:
  /***
   * Reads the provider information from the HAL.
   * May return nullptr if the HAL does not implement
   * getProviderInfo, or if the feature flag for codec
   * extensibility is disabled.
   ***/
  static std::unique_ptr<ProviderInfo> GetProviderInfo(
      bool supports_a2dp_hw_offload_v2);

  ProviderInfo(std::vector<CodecInfo> source_codecs,
               std::vector<CodecInfo> sink_codecs);
  ~ProviderInfo() = default;

  /***
   * Returns the codec with the selected index if supported
   * by the provider.
   ***/
  std::optional<CodecInfo const*> GetCodec(
      btav_a2dp_codec_index_t codec_index) const;

  /***
   * Find the source codec index by codec capabilities.
   ***/
  std::optional<btav_a2dp_codec_index_t> SourceCodecIndex(
      CodecId const& codec_id) const;
  std::optional<btav_a2dp_codec_index_t> SourceCodecIndex(
      uint32_t vendor_id, uint16_t codec_id) const;
  std::optional<btav_a2dp_codec_index_t> SourceCodecIndex(
      uint8_t const* codec_info) const;

  /***
   * Find the sink codec index by codec capabilities.
   ***/
  std::optional<btav_a2dp_codec_index_t> SinkCodecIndex(
      CodecId const& codec_id) const;
  std::optional<btav_a2dp_codec_index_t> SinkCodecIndex(
      uint32_t vendor_id, uint16_t codec_id) const;
  std::optional<btav_a2dp_codec_index_t> SinkCodecIndex(
      uint8_t const* codec_info) const;

  /***
   * Return the name of the codec with the assigned
   * input index.
   ***/
  std::optional<const char*> CodecIndexStr(
      btav_a2dp_codec_index_t codec_index) const;

  /***
   * Return true if the codec is supported by the
   * provider.
   ***/
  bool SupportsCodec(btav_a2dp_codec_index_t codec_index) const;

  /***
   * Helper to convert CodecId and byte[] configuration to
   * the Media Codec Capabilities format.
   * Returns true if the capabilities were successfully converted.
   ***/
  static bool BuildCodecCapabilities(CodecId const& codec_id,
                                     std::vector<uint8_t> const& capabilities,
                                     uint8_t* codec_info);

  /***
   * Return the A2DP capabilities for the selected codec.
   * Returns true if the codec is supported, false otherwise.
   ***/
  bool CodecCapabilities(btav_a2dp_codec_index_t codec_index,
                         uint64_t* codec_id, uint8_t* codec_info,
                         btav_a2dp_codec_config_t* codec_config) const;

  const std::vector<CodecInfo> source_codecs;
  const std::vector<CodecInfo> sink_codecs;

 private:
  std::unordered_map<btav_a2dp_codec_index_t, CodecInfo const*>
      assigned_codec_indexes;
};

}  // namespace bluetooth::audio::aidl::a2dp
