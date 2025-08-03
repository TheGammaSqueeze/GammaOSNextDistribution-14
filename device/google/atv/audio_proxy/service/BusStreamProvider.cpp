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

#include "BusStreamProvider.h"

#include <android-base/logging.h>

#include <algorithm>

#include "DummyBusOutputStream.h"
#include "RemoteBusOutputStream.h"

using aidl::device::google::atv::audio_proxy::IOutputStream;

namespace audio_proxy::service {

void BusStreamProvider::setStreamProvider(
    std::shared_ptr<IStreamProvider> provider) {
  std::lock_guard<std::mutex> lock(mLock);
  cleanStreamOutList_Locked();
  mStreamProvider = std::move(provider);

  for (auto& weakStream : mStreamOutList) {
    if (sp<StreamOutImpl> stream = weakStream.promote()) {
      auto oldOutputStream = stream->getOutputStream();
      auto outputStream = openOutputStream_Locked(
          oldOutputStream->getAddress(), oldOutputStream->getConfig(),
          oldOutputStream->getFlags(),
          oldOutputStream->getConfig().bufferSizeBytes,
          oldOutputStream->getConfig().latencyMs);
      stream->updateOutputStream(std::move(outputStream));
    }
  }
}

std::shared_ptr<IStreamProvider> BusStreamProvider::getStreamProvider() {
  std::lock_guard<std::mutex> lock(mLock);
  return mStreamProvider;
}

std::shared_ptr<BusOutputStream> BusStreamProvider::openOutputStream(
    const std::string& address, const AidlAudioConfig& config, int32_t flags,
    int64_t bufferSizeBytes, int32_t latencyMs) {
  std::lock_guard<std::mutex> lock(mLock);
  return openOutputStream_Locked(address, config, flags, bufferSizeBytes,
                                 latencyMs);
}

void BusStreamProvider::onStreamOutCreated(wp<StreamOutImpl> stream) {
  std::lock_guard<std::mutex> lock(mLock);
  cleanStreamOutList_Locked();
  mStreamOutList.emplace_back(std::move(stream));
}

std::shared_ptr<BusOutputStream> BusStreamProvider::openOutputStream_Locked(
    const std::string& address, const AidlAudioConfig& config, int32_t flags,
    int64_t bufferSizeBytes, int32_t latencyMs) {
  AidlAudioConfig newConfig = config;
  newConfig.bufferSizeBytes = bufferSizeBytes;
  newConfig.latencyMs = latencyMs;

  if (!mStreamProvider) {
    return std::make_shared<DummyBusOutputStream>(address, newConfig, flags);
  }

  std::shared_ptr<IOutputStream> stream;
  ndk::ScopedAStatus status =
      mStreamProvider->openOutputStream(address, config, flags, &stream);
  if (!status.isOk() || !stream) {
    LOG(ERROR) << "Failed to open output stream, status " << status.getStatus();
    return std::make_shared<DummyBusOutputStream>(address, newConfig, flags);
  }

  int64_t aidlBufferSizeInBytes = -1;
  if (stream->getBufferSizeBytes(&aidlBufferSizeInBytes).isOk() &&
      aidlBufferSizeInBytes > 0) {
    newConfig.bufferSizeBytes = aidlBufferSizeInBytes;
  }

  int32_t aidlLatencyMs = -1;
  if (stream->getLatencyMs(&aidlLatencyMs).isOk() && aidlLatencyMs > 0) {
    newConfig.latencyMs = aidlLatencyMs;
  }

  return std::make_shared<RemoteBusOutputStream>(std::move(stream), address,
                                                 newConfig, flags);
}

size_t BusStreamProvider::cleanAndCountStreamOuts() {
  std::lock_guard<std::mutex> lock(mLock);
  cleanStreamOutList_Locked();
  return mStreamOutList.size();
}

void BusStreamProvider::cleanStreamOutList_Locked() {
  auto it = mStreamOutList.begin();
  while (it != mStreamOutList.end()) {
    if (!it->promote()) {
      it = mStreamOutList.erase(it);
    } else {
      ++it;
    }
  }
}

}  // namespace audio_proxy::service