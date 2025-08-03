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

#include <aidl/device/google/atv/audio_proxy/BnAudioProxy.h>

#include "BusStreamProvider.h"

namespace audio_proxy::service {

using aidl::device::google::atv::audio_proxy::IStreamProvider;

class AudioProxyImpl
    : public aidl::device::google::atv::audio_proxy::BnAudioProxy {
 public:
  AudioProxyImpl();
  ~AudioProxyImpl() override = default;

  ndk::ScopedAStatus start(
      const std::shared_ptr<IStreamProvider>& provider) override;

  BusStreamProvider& getBusStreamProvider();

 private:
  static void onStreamProviderDied(void* cookie);
  void resetStreamProvider();

  BusStreamProvider mBusStreamProvider;
  ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
  std::shared_ptr<IStreamProvider> mStreamProvider;
};

}  // namespace audio_proxy::service