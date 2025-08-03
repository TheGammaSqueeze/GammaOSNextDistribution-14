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

#include <aidl/device/google/atv/audio_proxy/IStreamProvider.h>
#include <android-base/thread_annotations.h>
#include <utils/RefBase.h>

#include <mutex>
#include <vector>

#include "AidlTypes.h"
#include "StreamOutImpl.h"

namespace audio_proxy::service {

using aidl::device::google::atv::audio_proxy::IStreamProvider;
using android::wp;

class BusOutputStream;

// Class to provider BusOutputStream to clients (StreamOutImpl). The public
// functions will be called from either the AIDL thread pool or HIDL thread
// pool. So the public functions are thread safe.
class BusStreamProvider {
 public:
  // Set/unset remote IStreamProvider. It will notify the opened StreamOut in
  // mStreamOutList as well.
  void setStreamProvider(std::shared_ptr<IStreamProvider> streamProvider);

  std::shared_ptr<IStreamProvider> getStreamProvider();

  // Add stream to the list so that they can be notified when the client becomes
  // available.
  void onStreamOutCreated(wp<StreamOutImpl> stream);

  // Returns different BusOutputStream depends on the current status:
  // 1. If mStreamProvider is available and mStreamProvider::openOutputStream
  //    returns valid IOutputStream, returns RemoteBusOutputStream.
  // 2. Returns DummyBusOutputStream otherwise.
  // This function always return a non null BusOutputStream.
  std::shared_ptr<BusOutputStream> openOutputStream(
      const std::string& address, const AidlAudioConfig& config, int32_t flags,
      int64_t bufferSizeBytes, int32_t latencyMs);

  // Clear closed StreamOut and return number of opened StreamOut.
  size_t cleanAndCountStreamOuts();

 private:
  std::shared_ptr<BusOutputStream> openOutputStream_Locked(
      const std::string& address, const AidlAudioConfig& config, int32_t flags,
      int64_t bufferSizeBytes, int32_t latencyMs) REQUIRES(mLock);

  // Remove the dead dead from mStreamOutList.
  void cleanStreamOutList_Locked() REQUIRES(mLock);

  std::mutex mLock;
  std::shared_ptr<IStreamProvider> mStreamProvider GUARDED_BY(mLock);
  std::vector<wp<StreamOutImpl>> mStreamOutList GUARDED_BY(mLock);
};

}  // namespace audio_proxy::service