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

#include "OutputStreamImpl.h"

#include <aidl/device/google/atv/audio_proxy/MessageQueueFlag.h>
#include <aidl/device/google/atv/audio_proxy/PresentationPosition.h>
#include <android-base/logging.h>
#include <time.h>

#include "AudioProxyClientError.h"
#include "AudioProxyStreamOut.h"

using aidl::device::google::atv::audio_proxy::MessageQueueFlag;
using android::status_t;

namespace audio_proxy {
namespace {
// 1GB
constexpr uint32_t kMaxBufferSize = 1 << 30;

void deleteEventFlag(EventFlag* obj) {
  if (!obj) {
    return;
  }

  status_t status = EventFlag::deleteEventFlag(&obj);
  if (status) {
    LOG(ERROR) << "write MQ event flag deletion error: " << strerror(-status);
  }
}

class WriteThread : public Thread {
 public:
  // WriteThread's lifespan never exceeds StreamOut's lifespan.
  WriteThread(std::atomic<bool>* stop, AudioProxyStreamOut* stream,
              OutputStreamImpl::DataMQ* dataMQ,
              OutputStreamImpl::StatusMQ* statusMQ, EventFlag* eventFlag);

  ~WriteThread() override;

 private:
  bool threadLoop() override;

  PresentationPosition doGetPresentationPosition();
  int64_t doWrite();

  std::atomic<bool>* const mStop;
  AudioProxyStreamOut* mStream;
  OutputStreamImpl::DataMQ* const mDataMQ;
  OutputStreamImpl::StatusMQ* const mStatusMQ;
  EventFlag* const mEventFlag;
  const std::unique_ptr<int8_t[]> mBuffer;
};

WriteThread::WriteThread(std::atomic<bool>* stop, AudioProxyStreamOut* stream,
                         OutputStreamImpl::DataMQ* dataMQ,
                         OutputStreamImpl::StatusMQ* statusMQ,
                         EventFlag* eventFlag)
    : Thread(false /*canCallJava*/),
      mStop(stop),
      mStream(stream),
      mDataMQ(dataMQ),
      mStatusMQ(statusMQ),
      mEventFlag(eventFlag),
      mBuffer(new int8_t[mDataMQ->getQuantumCount()]) {}

WriteThread::~WriteThread() = default;

int64_t WriteThread::doWrite() {
  const size_t availToRead = mDataMQ->availableToRead();
  if (availToRead == 0) {
    return 0;
  }

  if (!mDataMQ->read(&mBuffer[0], availToRead)) {
    return 0;
  }

  return mStream->write(&mBuffer[0], availToRead);
}

PresentationPosition WriteThread::doGetPresentationPosition() {
  PresentationPosition position;
  mStream->getPresentationPosition(&position.frames, &position.timestamp);
  return position;
}

bool WriteThread::threadLoop() {
  // This implementation doesn't return control back to the Thread until the
  // parent thread decides to stop, as the Thread uses mutexes, and this can
  // lead to priority inversion.
  while (!std::atomic_load_explicit(mStop, std::memory_order_acquire)) {
    uint32_t efState = 0;
    mEventFlag->wait(static_cast<uint32_t>(MessageQueueFlag::NOT_EMPTY),
                     &efState);
    if (!(efState & static_cast<uint32_t>(MessageQueueFlag::NOT_EMPTY))) {
      continue;  // Nothing to do.
    }

    WriteStatus status;
    status.written = doWrite();
    status.position = doGetPresentationPosition();

    if (!mStatusMQ->write(&status)) {
      LOG(ERROR) << "status message queue write failed.";
    }
    mEventFlag->wake(static_cast<uint32_t>(MessageQueueFlag::NOT_FULL));
  }

  return false;
}

}  // namespace

OutputStreamImpl::OutputStreamImpl(std::unique_ptr<AudioProxyStreamOut> stream)
    : mStream(std::move(stream)), mEventFlag(nullptr, deleteEventFlag) {}

OutputStreamImpl::~OutputStreamImpl() {
  closeImpl();

  if (mWriteThread) {
    status_t status = mWriteThread->join();
    if (status) {
      LOG(ERROR) << "write thread exit error: " << strerror(-status);
    }
  }

  mEventFlag.reset();
}

ndk::ScopedAStatus OutputStreamImpl::standby() {
  mStream->standby();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::close() { return closeImpl(); }

ndk::ScopedAStatus OutputStreamImpl::closeImpl() {
  if (mStopWriteThread.load(
          std::memory_order_relaxed)) {  // only this thread writes
    return ndk::ScopedAStatus::ok();
  }
  mStopWriteThread.store(true, std::memory_order_release);
  if (mEventFlag) {
    mEventFlag->wake(static_cast<uint32_t>(MessageQueueFlag::NOT_EMPTY));
  }

  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::prepareForWriting(
    int32_t frameSize, int32_t framesCount, DataMQDesc* dataMQDesc,
    StatusMQDesc* statusMQDesc) {
  if (mDataMQ) {
    LOG(ERROR) << "the client attempted to call prepareForWriting twice.";
    return ndk::ScopedAStatus::fromServiceSpecificError(ERROR_INVALID_ARGS);
  }

  if (frameSize == 0 || framesCount == 0) {
    LOG(ERROR) << "Invalid frameSize (" << frameSize << ") or framesCount ("
               << framesCount << ")";
    return ndk::ScopedAStatus::fromServiceSpecificError(ERROR_INVALID_ARGS);
  }

  if (frameSize > kMaxBufferSize / framesCount) {
    LOG(ERROR) << "Buffer too big: " << frameSize << "*" << framesCount
               << " bytes > MAX_BUFFER_SIZE (" << kMaxBufferSize << ")";
    return ndk::ScopedAStatus::fromServiceSpecificError(ERROR_INVALID_ARGS);
  }

  auto dataMQ =
      std::make_unique<DataMQ>(frameSize * framesCount, true /* EventFlag */);
  if (!dataMQ->isValid()) {
    LOG(ERROR) << "data MQ is invalid";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        ERROR_FMQ_CREATION_FAILURE);
  }

  auto statusMQ = std::make_unique<StatusMQ>(1);
  if (!statusMQ->isValid()) {
    LOG(ERROR) << "status MQ is invalid";
    return ndk::ScopedAStatus::fromServiceSpecificError(
        ERROR_FMQ_CREATION_FAILURE);
  }

  EventFlag* rawEventFlag = nullptr;
  status_t status =
      EventFlag::createEventFlag(dataMQ->getEventFlagWord(), &rawEventFlag);
  std::unique_ptr<EventFlag, EventFlagDeleter> eventFlag(rawEventFlag,
                                                         deleteEventFlag);
  if (status != ::android::OK || !eventFlag) {
    LOG(ERROR) << "failed creating event flag for data MQ: "
               << strerror(-status);
    return ndk::ScopedAStatus::fromServiceSpecificError(
        ERROR_FMQ_CREATION_FAILURE);
  }

  sp<WriteThread> writeThread =
      new WriteThread(&mStopWriteThread, mStream.get(), dataMQ.get(),
                      statusMQ.get(), eventFlag.get());
  status = writeThread->run("writer", ::android::PRIORITY_URGENT_AUDIO);
  if (status != ::android::OK) {
    LOG(ERROR) << "failed to start writer thread: " << strerror(-status);
    return ndk::ScopedAStatus::fromServiceSpecificError(
        ERROR_FMQ_CREATION_FAILURE);
  }

  mDataMQ = std::move(dataMQ);
  mStatusMQ = std::move(statusMQ);
  mEventFlag = std::move(eventFlag);
  mWriteThread = std::move(writeThread);

  *dataMQDesc = mDataMQ->dupeDesc();
  *statusMQDesc = mStatusMQ->dupeDesc();

  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::pause() {
  mStream->pause();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::resume() {
  mStream->resume();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::drain(AudioDrain type) {
  mStream->drain(type);
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::flush() {
  mStream->flush();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::setVolume(float left, float right) {
  mStream->setVolume(left, right);
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::getBufferSizeBytes(
    int64_t* bufferSizeBytes) {
  *bufferSizeBytes = mStream->getBufferSizeBytes();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::getLatencyMs(int32_t* latencyMs) {
  *latencyMs = mStream->getLatencyMs();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::start() {
  mStream->start();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::stop() {
  mStream->stop();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::createMmapBuffer(
    int32_t minBufferSizeFrames, MmapBufferInfo* info) {
  *info = mStream->createMmapBuffer(minBufferSizeFrames);
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus OutputStreamImpl::getMmapPosition(
    PresentationPosition* position) {
  *position = mStream->getMmapPosition();
  return ndk::ScopedAStatus::ok();
}

}  // namespace audio_proxy