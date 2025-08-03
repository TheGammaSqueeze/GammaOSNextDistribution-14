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

#include "DummyBusOutputStream.h"

#include <algorithm>

#include <aidl/device/google/atv/audio_proxy/TimeSpec.h>
#include <android-base/logging.h>
#include <unistd.h>

using aidl::device::google::atv::audio_proxy::TimeSpec;

namespace audio_proxy::service {
namespace {
constexpr int64_t kOneSecInNs = 1'000'000'000;
constexpr int64_t kOneSecInUs = 1'000'000;
constexpr int64_t kOneUSecInNs = 1'000;

int64_t timespecDelta(const timespec& newTime, const timespec& oldTime) {
  int64_t deltaSec = 0;
  int64_t deltaNSec = 0;
  if (newTime.tv_nsec >= oldTime.tv_nsec) {
    deltaSec = newTime.tv_sec - oldTime.tv_sec;
    deltaNSec = newTime.tv_nsec - oldTime.tv_nsec;
  } else {
    deltaSec = newTime.tv_sec - oldTime.tv_sec - 1;
    deltaNSec = kOneSecInNs + newTime.tv_nsec - oldTime.tv_nsec;
  }

  return deltaSec * kOneSecInUs + deltaNSec / kOneUSecInNs;
}
}  // namespace

DummyBusOutputStream::DummyBusOutputStream(const std::string& address,
                                           const AidlAudioConfig& config,
                                           int32_t flags)
    : BusOutputStream(address, config, flags) {}
DummyBusOutputStream::~DummyBusOutputStream() = default;

bool DummyBusOutputStream::standby() { return true; }
bool DummyBusOutputStream::pause() { return true; }
bool DummyBusOutputStream::resume() { return true; }
bool DummyBusOutputStream::drain(AidlAudioDrain drain) { return true; }
bool DummyBusOutputStream::flush() { return true; }
bool DummyBusOutputStream::close() { return true; }
bool DummyBusOutputStream::setVolume(float left, float right) { return true; }

size_t DummyBusOutputStream::availableToWrite() {
  return mWritingFrameSize * mWritingFrameCount;
}

AidlWriteStatus DummyBusOutputStream::writeRingBuffer(const uint8_t* firstMem,
                                                      size_t firstLength,
                                                      const uint8_t* secondMem,
                                                      size_t secondLength) {
  size_t bufferBytes = firstLength + secondLength;
  int64_t numFrames = bufferBytes / getFrameSize();
  int64_t durationUs = numFrames * kOneSecInUs / mConfig.sampleRateHz;

  timespec now = {0, 0};
  clock_gettime(CLOCK_MONOTONIC, &now);
  if (mStartTime.tv_sec == 0) {
    mStartTime = now;
  }

  // Check underrun
  int64_t elapsedTimeUs = timespecDelta(now, mStartTime);
  if (elapsedTimeUs > mInputUsSinceStart) {
    // Underrun
    mPlayedUsBeforeUnderrun += mInputUsSinceStart;
    mStartTime = now;
    mInputUsSinceStart = 0;
  }

  // Wait if buffer full.
  mInputUsSinceStart += durationUs;
  int64_t waitTimeUs = mInputUsSinceStart - elapsedTimeUs - mMaxBufferUs;
  if (waitTimeUs > 0) {
    usleep(waitTimeUs);
    clock_gettime(CLOCK_MONOTONIC, &now);
  }

  // Calculate played frames.
  int64_t playedUs =
      mPlayedUsBeforeUnderrun +
      std::min(timespecDelta(now, mStartTime), mInputUsSinceStart);

  TimeSpec timeSpec = {now.tv_sec, now.tv_nsec};

  AidlWriteStatus status;
  status.written = bufferBytes;
  status.position = {playedUs * mConfig.sampleRateHz / kOneSecInUs, timeSpec};

  return status;
}

bool DummyBusOutputStream::prepareForWritingImpl(uint32_t frameSize,
                                                 uint32_t frameCount) {
  // The `frame` here is not audio frame, it doesn't count the sample format and
  // channel layout.
  mMaxBufferUs = frameSize * frameCount * 10 * kOneSecInUs /
                 (mConfig.sampleRateHz * getFrameSize());
  return true;
}

bool DummyBusOutputStream::start() { return false; }

bool DummyBusOutputStream::stop() { return false; };

AidlMmapBufferInfo DummyBusOutputStream::createMmapBuffer(
    int32_t minBufferSizeFrames) {
  return AidlMmapBufferInfo();
}

AidlPresentationPosition DummyBusOutputStream::getMmapPosition() {
  return AidlPresentationPosition();
}

}  // namespace audio_proxy::service
