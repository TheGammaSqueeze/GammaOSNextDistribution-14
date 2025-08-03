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

#include "AudioProxyImpl.h"

#include <android-base/logging.h>

#include "AudioProxyError.h"

namespace audio_proxy::service {

AudioProxyImpl::AudioProxyImpl()
    : mDeathRecipient(
          AIBinder_DeathRecipient_new(AudioProxyImpl::onStreamProviderDied)) {}

ndk::ScopedAStatus AudioProxyImpl::start(
    const std::shared_ptr<IStreamProvider>& provider) {
  if (mBusStreamProvider.getStreamProvider()) {
    LOG(ERROR) << "Service is already started.";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        ERROR_STREAM_PROVIDER_EXIST);
  }

  binder_status_t binder_status = AIBinder_linkToDeath(
      provider->asBinder().get(), mDeathRecipient.get(), this);
  if (binder_status != STATUS_OK) {
    LOG(ERROR) << "Failed to linkToDeath " << static_cast<int>(binder_status);
    return ndk::ScopedAStatus::fromServiceSpecificError(ERROR_AIDL_FAILURE);
  }

  mBusStreamProvider.setStreamProvider(provider);
  return ndk::ScopedAStatus::ok();
}

BusStreamProvider& AudioProxyImpl::getBusStreamProvider() {
  return mBusStreamProvider;
}

void AudioProxyImpl::resetStreamProvider() {
  mBusStreamProvider.setStreamProvider(nullptr);
}

void AudioProxyImpl::onStreamProviderDied(void* cookie) {
  // AudioProxyImpl lives longer than the death handler. The reinterpret_cast
  // here is safe.
  auto* audioProxy = reinterpret_cast<AudioProxyImpl*>(cookie);
  audioProxy->resetStreamProvider();
}

}  // namespace audio_proxy::service
