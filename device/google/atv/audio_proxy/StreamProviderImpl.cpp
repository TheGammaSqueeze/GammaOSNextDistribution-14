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

#include "StreamProviderImpl.h"

#include <android-base/logging.h>

#include "AudioProxyDevice.h"
#include "AudioProxyStreamOut.h"
#include "OutputStreamImpl.h"

using aidl::device::google::atv::audio_proxy::AudioConfig;
using aidl::device::google::atv::audio_proxy::IOutputStream;

namespace audio_proxy {

StreamProviderImpl::StreamProviderImpl(AudioProxyDevice* device)
    : mDevice(device) {}
StreamProviderImpl::~StreamProviderImpl() = default;

ndk::ScopedAStatus StreamProviderImpl::openOutputStream(
    const std::string& address, const AudioConfig& config, int32_t flags,
    std::shared_ptr<IOutputStream>* outputStream) {
  *outputStream = nullptr;

  std::unique_ptr<AudioProxyStreamOut> stream =
      mDevice->openOutputStream(address, config, flags);
  if (stream) {
    *outputStream =
        ndk::SharedRefBase::make<OutputStreamImpl>(std::move(stream));
  } else {
    LOG(WARNING) << "Failed to open output stream.";
  }

  // Returns OK as this is a recoverable failure. The caller can open a new
  // output stream with different config and flags.
  return ndk::ScopedAStatus::ok();
}

}  // namespace audio_proxy