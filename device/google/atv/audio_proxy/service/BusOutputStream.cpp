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

#include "BusOutputStream.h"

#include <android-base/logging.h>

#include "AudioUtil.h"

namespace audio_proxy::service {

BusOutputStream::BusOutputStream(const std::string& address,
                                 const AidlAudioConfig& config, int32_t flags)
    : mAddress(address), mConfig(config), mFlags(flags) {}
BusOutputStream::~BusOutputStream() = default;

const std::string& BusOutputStream::getAddress() const { return mAddress; }
const AidlAudioConfig& BusOutputStream::getConfig() const { return mConfig; }
int32_t BusOutputStream::getFlags() const { return mFlags; }

int BusOutputStream::getFrameSize() const { return computeFrameSize(mConfig); }

bool BusOutputStream::prepareForWriting(uint32_t frameSize,
                                        uint32_t frameCount) {
  DCHECK_EQ(mWritingFrameSize, 0);
  DCHECK_EQ(mWritingFrameCount, 0);

  if (!prepareForWritingImpl(frameSize, frameCount)) {
    return false;
  }

  mWritingFrameSize = frameSize;
  mWritingFrameCount = frameCount;
  return true;
}

uint32_t BusOutputStream::getWritingFrameSize() const {
  return mWritingFrameSize;
}

uint32_t BusOutputStream::getWritingFrameCount() const {
  return mWritingFrameCount;
}

}  // namespace audio_proxy::service