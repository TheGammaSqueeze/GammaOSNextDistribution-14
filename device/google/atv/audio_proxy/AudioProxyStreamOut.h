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

#pragma once

#include <aidl/device/google/atv/audio_proxy/AudioDrain.h>
#include <aidl/device/google/atv/audio_proxy/MmapBufferInfo.h>
#include <aidl/device/google/atv/audio_proxy/PresentationPosition.h>
#include <aidl/device/google/atv/audio_proxy/TimeSpec.h>

#include <memory>

#include "public/audio_proxy.h"

namespace audio_proxy {

using aidl::device::google::atv::audio_proxy::AudioDrain;
using aidl::device::google::atv::audio_proxy::MmapBufferInfo;
using aidl::device::google::atv::audio_proxy::PresentationPosition;
using aidl::device::google::atv::audio_proxy::TimeSpec;

// C++ friendly wrapper of audio_proxy_stream_out. It handles type conversion
// between C type and aidl type.
class AudioProxyStreamOut final {
 public:
  AudioProxyStreamOut(audio_proxy_stream_out_t* stream,
                      audio_proxy_device_t* device);
  ~AudioProxyStreamOut();

  ssize_t write(const void* buffer, size_t bytes);
  void getPresentationPosition(int64_t* frames, TimeSpec* timestamp) const;

  void standby();
  void pause();
  void resume();
  void drain(AudioDrain type);
  void flush();

  void setVolume(float left, float right);

  int64_t getBufferSizeBytes();
  int32_t getLatencyMs();

  void start();
  void stop();
  MmapBufferInfo createMmapBuffer(int32_t minBufferSizeFrames);
  PresentationPosition getMmapPosition();

 private:
  audio_proxy_stream_out_t* const mStream;
  audio_proxy_device_t* const mDevice;
};

}  // namespace audio_proxy
