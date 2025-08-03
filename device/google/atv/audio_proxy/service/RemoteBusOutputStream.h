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

#include <aidl/device/google/atv/audio_proxy/IOutputStream.h>
#include <fmq/AidlMessageQueue.h>
#include <fmq/EventFlag.h>

#include "BusOutputStream.h"

namespace audio_proxy {
namespace service {

using aidl::android::hardware::common::fmq::MQDescriptor;
using aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using aidl::device::google::atv::audio_proxy::IOutputStream;
using android::AidlMessageQueue;
using android::hardware::EventFlag;

class RemoteBusOutputStream : public BusOutputStream {
 public:
  RemoteBusOutputStream(std::shared_ptr<IOutputStream> stream,
                        const std::string& address,
                        const AidlAudioConfig& config, int32_t flags);
  ~RemoteBusOutputStream() override;

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
  using DataMQ = AidlMessageQueue<int8_t, SynchronizedReadWrite>;
  using DataMQDesc = MQDescriptor<int8_t, SynchronizedReadWrite>;
  using StatusMQ = AidlMessageQueue<AidlWriteStatus, SynchronizedReadWrite>;
  using StatusMQDesc = MQDescriptor<AidlWriteStatus, SynchronizedReadWrite>;

  typedef void (*EventFlagDeleter)(EventFlag*);

  std::shared_ptr<IOutputStream> mStream;

  std::unique_ptr<DataMQ> mDataMQ;
  std::unique_ptr<StatusMQ> mStatusMQ;
  std::unique_ptr<EventFlag, EventFlagDeleter> mEventFlag;
};

}  // namespace service
}  // namespace audio_proxy