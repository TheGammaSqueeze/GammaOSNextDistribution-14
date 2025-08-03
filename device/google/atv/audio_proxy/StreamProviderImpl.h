// Copyright (C) 2021 The Android Open Source Project
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

#include <aidl/device/google/atv/audio_proxy/BnStreamProvider.h>

#include "public/audio_proxy.h"

namespace audio_proxy {

class AudioProxyDevice;

class StreamProviderImpl
    : public aidl::device::google::atv::audio_proxy::BnStreamProvider {
 public:
  explicit StreamProviderImpl(AudioProxyDevice* device);
  ~StreamProviderImpl() override;

  // Methods from IStreamProvider:
  ndk::ScopedAStatus openOutputStream(
      const std::string& addres,
      const aidl::device::google::atv::audio_proxy::AudioConfig& config,
      int32_t flags,
      std::shared_ptr<aidl::device::google::atv::audio_proxy::IOutputStream>*
          outputStream) override;

 private:
  AudioProxyDevice* const mDevice;
};

}  // namespace audio_proxy