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

#include <aidl/device/google/atv/audio_proxy/AudioConfig.h>

#include <memory>

#include "public/audio_proxy.h"

namespace audio_proxy {

class AudioProxyStreamOut;

// C++ friendly wrapper of audio_proxy_device. It handles type conversion
// between C type and aidl type.
class AudioProxyDevice final {
 public:
  explicit AudioProxyDevice(audio_proxy_device_t* device);
  ~AudioProxyDevice();

  const char* getServiceName();

  std::unique_ptr<AudioProxyStreamOut> openOutputStream(
      const std::string& address,
      const aidl::device::google::atv::audio_proxy::AudioConfig& config,
      int32_t flags);

 private:
  audio_proxy_device_t* const mDevice;
};

}  // namespace audio_proxy
