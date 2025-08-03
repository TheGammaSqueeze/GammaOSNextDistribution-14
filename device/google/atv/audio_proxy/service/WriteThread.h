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

#include <atomic>
#include <mutex>

// clang-format off
#include PATH(android/hardware/audio/FILE_VERSION/IStreamOut.h)
// clang-format on

#include <android-base/thread_annotations.h>
#include <fmq/EventFlag.h>
#include <fmq/MessageQueue.h>
#include <hidl/MQDescriptor.h>
#include <inttypes.h>
#include <utils/Thread.h>

#include "AidlTypes.h"

using android::sp;
using android::Thread;
using android::hardware::EventFlag;
using android::hardware::kSynchronizedReadWrite;
using android::hardware::MessageQueue;
using namespace android::hardware::audio::common::CPP_VERSION;
using namespace android::hardware::audio::CPP_VERSION;

namespace audio_proxy::service {

class BusOutputStream;

class WriteThread : public Thread {
 public:
  using CommandMQ =
      MessageQueue<IStreamOut::WriteCommand, kSynchronizedReadWrite>;
  using DataMQ = MessageQueue<uint8_t, kSynchronizedReadWrite>;
  using StatusMQ =
      MessageQueue<IStreamOut::WriteStatus, kSynchronizedReadWrite>;

  // WriteThread's lifespan never exceeds StreamOut's lifespan.
  WriteThread(std::shared_ptr<BusOutputStream> stream, CommandMQ* commandMQ,
              DataMQ* dataMQ, StatusMQ* statusMQ, EventFlag* eventFlag,
              uint32_t latencyMs);

  ~WriteThread() override;

  void stop();

  void updateOutputStream(std::shared_ptr<BusOutputStream> stream);

  std::pair<uint64_t, TimeSpec> getPresentationPosition();

 private:
  bool threadLoop() override;

  // The following function is called on the thread and it will modify the
  // variables which may be read from another thread.
  IStreamOut::WriteStatus doWrite(BusOutputStream* stream);

  // The following function is called on the thread and only read variable
  // that is written on the same thread, so there's no need to lock the
  // resources.
  IStreamOut::WriteStatus doGetPresentationPosition() const
      NO_THREAD_SAFETY_ANALYSIS;

  IStreamOut::WriteStatus doGetLatency() const;

  // Write 0 buffer to {@param stream} for latest presentation info.
  void queryPresentationPosition(BusOutputStream* stream);

  // Update presentation position info after writing to {@param stream}. Caller
  // should validate the {@param status}.
  void updatePresentationPosition(const AidlWriteStatus& status,
                                  BusOutputStream* stream);

  std::atomic<bool> mStop = false;

  std::mutex mStreamLock;
  std::shared_ptr<BusOutputStream> mStream GUARDED_BY(mStreamLock);

  CommandMQ* const mCommandMQ;
  DataMQ* const mDataMQ;
  StatusMQ* const mStatusMQ;
  EventFlag* const mEventFlag;

  // Latency in ms, used in HIDL API getLatency.
  const uint32_t mLatencyMs;

  // Count for consecutive FMQ command that is not WRITE.
  int64_t mNonWriteCommandCount = 0;

  // Presentation position information.
  std::mutex mPositionLock;
  uint64_t mPresentationFramesOffset GUARDED_BY(mPositionLock) = 0;
  uint64_t mPresentationFrames GUARDED_BY(mPositionLock) = 0;
  TimeSpec mPresentationTimestamp GUARDED_BY(mPositionLock) = {0, 0};
  uint64_t mTotalWrittenFrames GUARDED_BY(mPositionLock) = 0;
};

}  // namespace audio_proxy::service