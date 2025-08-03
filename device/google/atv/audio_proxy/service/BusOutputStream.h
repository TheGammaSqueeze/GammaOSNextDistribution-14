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

#include <inttypes.h>

#include "AidlTypes.h"

namespace audio_proxy::service {

// Interface for audio playback. It has similar APIs to the AIDL IOutputStream.
class BusOutputStream {
 public:
  BusOutputStream(const std::string& address, const AidlAudioConfig& config,
                  int32_t flags);
  virtual ~BusOutputStream();

  const std::string& getAddress() const;
  const AidlAudioConfig& getConfig() const;
  int32_t getFlags() const;
  int getFrameSize() const;

  bool prepareForWriting(uint32_t frameSize, uint32_t frameCount);
  uint32_t getWritingFrameSize() const;
  uint32_t getWritingFrameCount() const;

  virtual bool standby() = 0;
  virtual bool pause() = 0;
  virtual bool resume() = 0;
  virtual bool drain(AidlAudioDrain drain) = 0;
  virtual bool flush() = 0;
  virtual bool close() = 0;
  virtual bool setVolume(float left, float right) = 0;

  virtual size_t availableToWrite() = 0;
  virtual AidlWriteStatus writeRingBuffer(const uint8_t* firstMem,
                                          size_t firstLength,
                                          const uint8_t* secondMem,
                                          size_t secondLength) = 0;

  virtual bool start() = 0;
  virtual bool stop() = 0;
  virtual AidlMmapBufferInfo createMmapBuffer(int32_t minBufferSizeFrames) = 0;
  virtual AidlPresentationPosition getMmapPosition() = 0;

 protected:
  virtual bool prepareForWritingImpl(uint32_t frameSize,
                                     uint32_t frameCount) = 0;

  const std::string mAddress;
  const AidlAudioConfig mConfig;
  const int32_t mFlags;

  uint32_t mWritingFrameSize = 0;
  uint32_t mWritingFrameCount = 0;
};

}  // namespace audio_proxy::service