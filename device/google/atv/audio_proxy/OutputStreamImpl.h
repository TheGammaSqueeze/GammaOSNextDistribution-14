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

#include <aidl/device/google/atv/audio_proxy/BnOutputStream.h>
#include <fmq/AidlMessageQueue.h>
#include <fmq/EventFlag.h>
#include <utils/Thread.h>

using aidl::android::hardware::common::fmq::MQDescriptor;
using aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using android::AidlMessageQueue;
using android::sp;
using android::Thread;
using android::hardware::EventFlag;

using aidl::device::google::atv::audio_proxy::AudioDrain;
using aidl::device::google::atv::audio_proxy::BnOutputStream;
using aidl::device::google::atv::audio_proxy::MmapBufferInfo;
using aidl::device::google::atv::audio_proxy::PresentationPosition;
using aidl::device::google::atv::audio_proxy::WriteStatus;

namespace audio_proxy {
class AudioProxyStreamOut;

class OutputStreamImpl : public BnOutputStream {
 public:
  using DataMQ = AidlMessageQueue<int8_t, SynchronizedReadWrite>;
  using DataMQDesc = MQDescriptor<int8_t, SynchronizedReadWrite>;
  using StatusMQ = AidlMessageQueue<WriteStatus, SynchronizedReadWrite>;
  using StatusMQDesc = MQDescriptor<WriteStatus, SynchronizedReadWrite>;

  explicit OutputStreamImpl(std::unique_ptr<AudioProxyStreamOut> stream);
  ~OutputStreamImpl() override;

  ndk::ScopedAStatus prepareForWriting(int32_t frameSize, int32_t framesCount,
                                       DataMQDesc* dataMQDesc,
                                       StatusMQDesc* statusMQDesc) override;

  ndk::ScopedAStatus standby() override;
  ndk::ScopedAStatus close() override;
  ndk::ScopedAStatus pause() override;
  ndk::ScopedAStatus resume() override;
  ndk::ScopedAStatus drain(AudioDrain type) override;
  ndk::ScopedAStatus flush() override;

  ndk::ScopedAStatus setVolume(float left, float right) override;

  ndk::ScopedAStatus getBufferSizeBytes(int64_t* bufferSizeBytes) override;
  ndk::ScopedAStatus getLatencyMs(int32_t* latencyMs) override;

  ndk::ScopedAStatus start() override;
  ndk::ScopedAStatus stop() override;
  ndk::ScopedAStatus createMmapBuffer(int32_t minBufferSizeFrames,
                                      MmapBufferInfo* info) override;
  ndk::ScopedAStatus getMmapPosition(PresentationPosition* position) override;

 private:
  typedef void (*EventFlagDeleter)(EventFlag*);

  ndk::ScopedAStatus closeImpl();

  std::unique_ptr<AudioProxyStreamOut> mStream;

  std::unique_ptr<DataMQ> mDataMQ;
  std::unique_ptr<StatusMQ> mStatusMQ;
  std::unique_ptr<EventFlag, EventFlagDeleter> mEventFlag;
  std::atomic<bool> mStopWriteThread = false;
  sp<Thread> mWriteThread;
};

}  // namespace audio_proxy