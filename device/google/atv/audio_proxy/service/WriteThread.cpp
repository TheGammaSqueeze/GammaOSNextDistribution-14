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

#include "WriteThread.h"

#include <android-base/logging.h>
#include <time.h>

#include <atomic>

#include "AidlTypes.h"
#include "BusOutputStream.h"

namespace audio_proxy::service {

WriteThread::WriteThread(std::shared_ptr<BusOutputStream> stream,
                         CommandMQ* commandMQ, DataMQ* dataMQ,
                         StatusMQ* statusMQ, EventFlag* eventFlag,
                         uint32_t latencyMs)
    : Thread(false /*canCallJava*/),
      mStream(std::move(stream)),
      mCommandMQ(commandMQ),
      mDataMQ(dataMQ),
      mStatusMQ(statusMQ),
      mEventFlag(eventFlag),
      mLatencyMs(latencyMs) {}

WriteThread::~WriteThread() = default;

void WriteThread::stop() {
  if (mStop.load(std::memory_order_relaxed)) {
    return;
  }

  mStop.store(true, std::memory_order_release);
  mEventFlag->wake(static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY));
}

void WriteThread::updateOutputStream(std::shared_ptr<BusOutputStream> stream) {
  {
    std::scoped_lock<std::mutex> lock(mStreamLock);
    mStream = std::move(stream);
  }

  // Assume all the written frames are already played out by the old stream.
  std::scoped_lock<std::mutex> lock(mPositionLock);
  mPresentationFramesOffset = mTotalWrittenFrames;
}

std::pair<uint64_t, TimeSpec> WriteThread::getPresentationPosition() {
  std::scoped_lock<std::mutex> lock(mPositionLock);
  return std::make_pair(mPresentationFrames, mPresentationTimestamp);
}

IStreamOut::WriteStatus WriteThread::doWrite(BusOutputStream* stream) {
  IStreamOut::WriteStatus status;
  status.replyTo = IStreamOut::WriteCommand::WRITE;
  status.retval = Result::INVALID_STATE;
  status.reply.written = 0;

  const size_t availToRead = mDataMQ->availableToRead();
  if (stream->availableToWrite() < availToRead) {
    LOG(WARNING) << "No space to write, wait...";
    return status;
  }

  DataMQ::MemTransaction tx;
  if (mDataMQ->beginRead(availToRead, &tx)) {
    status.retval = Result::OK;
    AidlWriteStatus writeStatus = stream->writeRingBuffer(
        tx.getFirstRegion().getAddress(), tx.getFirstRegion().getLength(),
        tx.getSecondRegion().getAddress(), tx.getSecondRegion().getLength());
    if (writeStatus.written < availToRead) {
      LOG(WARNING) << "Failed to write all the bytes to client. Written "
                   << writeStatus.written << ", available " << availToRead;
    }

    if (writeStatus.written < 0) {
      writeStatus.written = 0;
    }

    status.reply.written = writeStatus.written;
    mDataMQ->commitRead(writeStatus.written);

    if (writeStatus.position.frames < 0 ||
        writeStatus.position.timestamp.tvSec < 0 ||
        writeStatus.position.timestamp.tvNSec < 0) {
      LOG(WARNING) << "Invalid latency info.";
      return status;
    }

    updatePresentationPosition(writeStatus, stream);
  }

  return status;
}

IStreamOut::WriteStatus WriteThread::doGetPresentationPosition() const {
  IStreamOut::WriteStatus status;
  status.replyTo = IStreamOut::WriteCommand::GET_PRESENTATION_POSITION;
  status.retval = Result::OK;
  // Write always happens on the same thread, there's no need to lock.
  status.reply.presentationPosition = {mPresentationFrames,
                                       mPresentationTimestamp};
  return status;
}

IStreamOut::WriteStatus WriteThread::doGetLatency() const {
  IStreamOut::WriteStatus status;
  status.replyTo = IStreamOut::WriteCommand::GET_LATENCY;
  status.retval = Result::OK;
  // Write always happens on the same thread, there's no need to lock.
  status.reply.latencyMs = mLatencyMs;
  return status;
}

bool WriteThread::threadLoop() {
  // This implementation doesn't return control back to the Thread until the
  // parent thread decides to stop, as the Thread uses mutexes, and this can
  // lead to priority inversion.
  while (!mStop.load(std::memory_order_acquire)) {
    std::shared_ptr<BusOutputStream> stream;
    {
      std::scoped_lock<std::mutex> lock(mStreamLock);
      stream = mStream;
    }

    // Read command. Don't use readBlocking, because readBlocking will block
    // when there's no data. When stopping the thread, there's a chance that we
    // only wake the mEventFlag without writing any data to FMQ. In this case,
    // readBlocking will block until timeout.
    IStreamOut::WriteCommand replyTo;
    uint32_t efState = 0;
    mEventFlag->wait(static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY),
                     &efState);
    if (!(efState & static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY))) {
      continue;  // Nothing to do.
    }
    if (!mCommandMQ->read(&replyTo)) {
      continue;  // Nothing to do.
    }

    if (replyTo == IStreamOut::WriteCommand::WRITE) {
      mNonWriteCommandCount = 0;
    } else {
      mNonWriteCommandCount++;
    }

    IStreamOut::WriteStatus status;
    switch (replyTo) {
      case IStreamOut::WriteCommand::WRITE:
        status = doWrite(stream.get());
        break;
      case IStreamOut::WriteCommand::GET_PRESENTATION_POSITION:
        // If we don't write data for a while, the presentation position info
        // may not be accurate. Write 0 bytes data to the client to get the
        // latest presentation position info.
        if (mNonWriteCommandCount >= 3 || mNonWriteCommandCount < 0) {
          queryPresentationPosition(stream.get());
        }
        status = doGetPresentationPosition();
        break;
      case IStreamOut::WriteCommand::GET_LATENCY:
        status = doGetLatency();
        break;
      default:
        LOG(ERROR) << "Unknown write thread command code "
                   << static_cast<int>(replyTo);
        status.retval = Result::NOT_SUPPORTED;
        break;
    }

    if (!mStatusMQ->write(&status)) {
      LOG(ERROR) << "Status message queue write failed";
    }
    mEventFlag->wake(static_cast<uint32_t>(MessageQueueFlagBits::NOT_FULL));
  }

  return false;
}

void WriteThread::queryPresentationPosition(BusOutputStream* stream) {
    AidlWriteStatus writeStatus =
        stream->writeRingBuffer(nullptr, 0, nullptr, 0);
    updatePresentationPosition(writeStatus, stream);
}

void WriteThread::updatePresentationPosition(const AidlWriteStatus& writeStatus,
                                             BusOutputStream* stream) {
  std::scoped_lock<std::mutex> lock(mPositionLock);
  mPresentationFrames = mPresentationFramesOffset + writeStatus.position.frames;
  mPresentationTimestamp = {
    .tvSec = static_cast<uint64_t>(writeStatus.position.timestamp.tvSec),
    .tvNSec = static_cast<uint64_t>(writeStatus.position.timestamp.tvNSec),
  };

  mTotalWrittenFrames += writeStatus.written / stream->getFrameSize();
}

}  // namespace audio_proxy::service
