// Copyright (C) 2020 The Android Open Source Project
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

#include "AudioProxyDevice.h"

#include <android-base/logging.h>

#include "AudioProxyStreamOut.h"

using aidl::device::google::atv::audio_proxy::AudioConfig;

#define CHECK_API(st, func)                    \
  do {                                         \
    if (!st->func) {                           \
      LOG(ERROR) << "Undefined API " << #func; \
      return false;                            \
    }                                          \
  } while (0)

namespace audio_proxy {
namespace {
bool isValidStreamOut(const audio_proxy_stream_out_t* stream) {
  CHECK_API(stream, standby);
  CHECK_API(stream, pause);
  CHECK_API(stream, resume);
  CHECK_API(stream, flush);
  CHECK_API(stream, drain);
  CHECK_API(stream, write);
  CHECK_API(stream, get_presentation_position);
  CHECK_API(stream, set_volume);
  CHECK_API(stream, get_buffer_size);
  CHECK_API(stream, get_latency);

  if (stream->v2) {
    CHECK_API(stream->v2, start);
    CHECK_API(stream->v2, stop);
    CHECK_API(stream->v2, create_mmap_buffer);
    CHECK_API(stream->v2, get_mmap_position);
  }

  return true;
}
}  // namespace

AudioProxyDevice::AudioProxyDevice(audio_proxy_device_t* device)
    : mDevice(device) {}

AudioProxyDevice::~AudioProxyDevice() = default;

const char* AudioProxyDevice::getServiceName() {
  return mDevice->v2->get_service_name(mDevice->v2);
}

std::unique_ptr<AudioProxyStreamOut> AudioProxyDevice::openOutputStream(
    const std::string& address, const AudioConfig& aidlConfig, int32_t flags) {
  audio_proxy_config_v2_t config_v2 = {
      .buffer_size_bytes = aidlConfig.bufferSizeBytes,
      .latency_ms = aidlConfig.latencyMs,
      .extension = nullptr};

  audio_proxy_config_t config = {
      .format = static_cast<audio_proxy_format_t>(aidlConfig.format),
      .sample_rate = static_cast<uint32_t>(aidlConfig.sampleRateHz),
      .channel_mask =
          static_cast<audio_proxy_channel_mask_t>(aidlConfig.channelMask),
      .frame_count = 0,
      .v2 = &config_v2};

  // TODO(yucliu): Pass address to the app. For now, the only client app
  // (MediaShell) can use flags to distinguish different streams.
  audio_proxy_stream_out_t* stream = nullptr;
  int ret = mDevice->v2->open_output_stream(
      mDevice->v2, address.c_str(),
      static_cast<audio_proxy_output_flags_t>(flags), &config, &stream);

  if (ret || !stream) {
    return nullptr;
  }

  if (!isValidStreamOut(stream)) {
    mDevice->close_output_stream(mDevice, stream);
    return nullptr;
  }

  return std::make_unique<AudioProxyStreamOut>(stream, mDevice);
}

}  // namespace audio_proxy
