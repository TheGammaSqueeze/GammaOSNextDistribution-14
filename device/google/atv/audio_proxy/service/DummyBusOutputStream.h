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

#include "BusOutputStream.h"

#include <time.h>

namespace audio_proxy::service {

// Impl of BusOutputStream which has a small buffer and consumes the audio data
// in real time.
class DummyBusOutputStream : public BusOutputStream {
 public:
  DummyBusOutputStream(const std::string& address,
                       const AidlAudioConfig& config, int32_t flags);
  ~DummyBusOutputStream() override;

  bool standby() override;
  bool pause() override;
  bool resume() override;
  bool drain(AidlAudioDrain drain) override;
  bool flush() override;
  bool close() override;
  bool setVolume(float left, float right) override;

  size_t availableToWrite() override;
  AidlWriteStatus writeRingBuffer(const uint8_t* firstMem, size_t firstLength,
                                  const uint8_t* secondMem,
                                  size_t secondLength) override;

  bool start() override;
  bool stop() override;
  AidlMmapBufferInfo createMmapBuffer(int32_t minBufferSizeFrames) override;
  AidlPresentationPosition getMmapPosition() override;

 protected:
  bool prepareForWritingImpl(uint32_t frameSize, uint32_t frameCount) override;

 private:
  // Buffer capacity.
  int64_t mMaxBufferUs = 0;

  // Timestamp for the first played frame. Underrun will reset it.
  timespec mStartTime = {0, 0};

  // Total written buffer size in us after `mStartTime` reset.
  int64_t mInputUsSinceStart = 0;

  // Total played buffer size in us before underrun.
  int64_t mPlayedUsBeforeUnderrun = 0;
};

}  // namespace audio_proxy::service