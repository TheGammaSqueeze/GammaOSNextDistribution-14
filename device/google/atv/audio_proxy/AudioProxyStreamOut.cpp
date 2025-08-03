// Copyright (C) 2020 The Android Open Source Project
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

#include "AudioProxyStreamOut.h"

namespace audio_proxy {

AudioProxyStreamOut::AudioProxyStreamOut(audio_proxy_stream_out_t* stream,
                                         audio_proxy_device_t* device)
    : mStream(stream), mDevice(device) {}

AudioProxyStreamOut::~AudioProxyStreamOut() {
  mDevice->close_output_stream(mDevice, mStream);
}

ssize_t AudioProxyStreamOut::write(const void* buffer, size_t bytes) {
  return mStream->write(mStream, buffer, bytes);
}

void AudioProxyStreamOut::getPresentationPosition(int64_t* frames,
                                                  TimeSpec* timestamp) const {
  struct timespec ts;
  mStream->get_presentation_position(mStream,
                                     reinterpret_cast<uint64_t*>(frames), &ts);

  timestamp->tvSec = ts.tv_sec;
  timestamp->tvNSec = ts.tv_nsec;
}

void AudioProxyStreamOut::standby() { mStream->standby(mStream); }

void AudioProxyStreamOut::pause() { mStream->pause(mStream); }

void AudioProxyStreamOut::resume() { mStream->resume(mStream); }

void AudioProxyStreamOut::drain(AudioDrain type) {
  mStream->drain(mStream, static_cast<audio_proxy_drain_type_t>(type));
}

void AudioProxyStreamOut::flush() { mStream->flush(mStream); }

void AudioProxyStreamOut::setVolume(float left, float right) {
  mStream->set_volume(mStream, left, right);
}

int64_t AudioProxyStreamOut::getBufferSizeBytes() {
  return mStream->get_buffer_size(mStream);
}

int32_t AudioProxyStreamOut::getLatencyMs() {
  return mStream->get_latency(mStream);
}

void AudioProxyStreamOut::start() {
  if (mStream->v2) {
    mStream->v2->start(mStream->v2);
  }
}

void AudioProxyStreamOut::stop() {
  if (mStream->v2) {
    mStream->v2->stop(mStream->v2);
  }
}

MmapBufferInfo AudioProxyStreamOut::createMmapBuffer(
    int32_t minBufferSizeFrames) {
  MmapBufferInfo aidlInfo;
  if (!mStream->v2) {
    return aidlInfo;
  }

  audio_proxy_mmap_buffer_info_t info =
      mStream->v2->create_mmap_buffer(mStream->v2, minBufferSizeFrames);
  aidlInfo.sharedMemoryFd.set(info.shared_memory_fd);
  aidlInfo.bufferSizeFrames = info.buffer_size_frames;
  aidlInfo.burstSizeFrames = info.burst_size_frames;
  aidlInfo.flags = info.flags;
  return aidlInfo;
}

PresentationPosition AudioProxyStreamOut::getMmapPosition() {
  PresentationPosition position;
  if (!mStream->v2) {
    return position;
  }

  int64_t frames = 0;
  struct timespec ts = {0, 0};
  mStream->v2->get_mmap_position(mStream->v2, &frames, &ts);
  position.frames = frames;
  position.timestamp = {ts.tv_sec, ts.tv_nsec};
  return position;
}
}  // namespace audio_proxy
