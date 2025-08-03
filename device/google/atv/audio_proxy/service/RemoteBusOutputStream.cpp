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

#include "RemoteBusOutputStream.h"

#include <aidl/device/google/atv/audio_proxy/MessageQueueFlag.h>
#include <android-base/logging.h>

#include "RingBufferUtil.h"

using aidl::device::google::atv::audio_proxy::MessageQueueFlag;
using android::status_t;

namespace audio_proxy {
namespace service {
namespace {

// Time out for FMQ read in ns -- 1s.
constexpr int64_t kFmqReadTimeoutNs = 1'000'000'000;

void deleteEventFlag(EventFlag* obj) {
  if (!obj) {
    return;
  }

  status_t status = EventFlag::deleteEventFlag(&obj);
  if (status != android::OK) {
    LOG(ERROR) << "write MQ event flag deletion error: " << strerror(-status);
  }
}

}  // namespace

RemoteBusOutputStream::RemoteBusOutputStream(
    std::shared_ptr<IOutputStream> stream, const std::string& address,
    const AidlAudioConfig& config, int32_t flags)
    : BusOutputStream(address, config, flags),
      mStream(std::move(stream)),
      mEventFlag(nullptr, deleteEventFlag) {}
RemoteBusOutputStream::~RemoteBusOutputStream() = default;

bool RemoteBusOutputStream::standby() { return mStream->standby().isOk(); }

bool RemoteBusOutputStream::pause() { return mStream->pause().isOk(); }

bool RemoteBusOutputStream::resume() { return mStream->resume().isOk(); }

bool RemoteBusOutputStream::drain(AidlAudioDrain drain) {
  return mStream->drain(drain).isOk();
}

bool RemoteBusOutputStream::flush() { return mStream->flush().isOk(); }

bool RemoteBusOutputStream::close() { return mStream->close().isOk(); }

bool RemoteBusOutputStream::setVolume(float left, float right) {
  return mStream->setVolume(left, right).isOk();
}

size_t RemoteBusOutputStream::availableToWrite() {
  return mDataMQ->availableToWrite();
}

AidlWriteStatus RemoteBusOutputStream::writeRingBuffer(const uint8_t* firstMem,
                                                       size_t firstLength,
                                                       const uint8_t* secondMem,
                                                       size_t secondLength) {
  DCHECK(mDataMQ);
  DCHECK(mStatusMQ);
  DCHECK(mEventFlag);
  AidlWriteStatus status;
  DataMQ::MemTransaction tx;
  if (!mDataMQ->beginWrite(firstLength + secondLength, &tx)) {
    LOG(ERROR) << "Failed to begin write.";
    return status;
  }

  const DataMQ::MemRegion& firstRegion = tx.getFirstRegion();
  const DataMQ::MemRegion& secondRegion = tx.getSecondRegion();

  copyRingBuffer(firstRegion.getAddress(), firstRegion.getLength(),
                 secondRegion.getAddress(), secondRegion.getLength(),
                 reinterpret_cast<const int8_t*>(firstMem), firstLength,
                 reinterpret_cast<const int8_t*>(secondMem), secondLength);
  if (!mDataMQ->commitWrite(firstLength + secondLength)) {
    LOG(ERROR) << "Failed to commit write.";
    return status;
  }

  mEventFlag->wake(static_cast<uint32_t>(MessageQueueFlag::NOT_EMPTY));

  // readNotification is used to "wake" after successful read, hence we don't
  // need it. writeNotification is used to "wait" for the other end to write
  // enough data.
  // It's fine to use readBlocking here because:
  // 1. We don't wake without writing mStatusMQ.
  // 2. The other end will always write mStatusMQ before wake mEventFlag.
  if (!mStatusMQ->readBlocking(
          &status, 1 /* count */, 0 /* readNotification */,
          static_cast<uint32_t>(
              MessageQueueFlag::NOT_FULL) /* writeNotification */,
          kFmqReadTimeoutNs, mEventFlag.get())) {
    LOG(ERROR) << "Failed to read status!";
    return status;
  }

  return status;
}

bool RemoteBusOutputStream::prepareForWritingImpl(uint32_t frameSize,
                                                  uint32_t frameCount) {
  DataMQDesc dataMQDesc;
  StatusMQDesc statusMQDesc;
  ndk::ScopedAStatus status = mStream->prepareForWriting(
      frameSize, frameCount, &dataMQDesc, &statusMQDesc);
  if (!status.isOk()) {
    LOG(ERROR) << "prepareForWriting fails.";
    return false;
  }

  auto dataMQ = std::make_unique<DataMQ>(dataMQDesc);
  if (!dataMQ->isValid()) {
    LOG(ERROR) << "invalid data mq.";
    return false;
  }

  EventFlag* rawEventFlag = nullptr;
  status_t eventFlagStatus =
      EventFlag::createEventFlag(dataMQ->getEventFlagWord(), &rawEventFlag);
  std::unique_ptr<EventFlag, EventFlagDeleter> eventFlag(rawEventFlag,
                                                         deleteEventFlag);
  if (eventFlagStatus != android::OK || !eventFlag) {
    LOG(ERROR) << "failed creating event flag for data MQ: "
               << strerror(-eventFlagStatus);
    return false;
  }

  auto statusMQ = std::make_unique<StatusMQ>(statusMQDesc);
  if (!statusMQ->isValid()) {
    LOG(ERROR) << "invalid status mq.";
    return false;
  }

  mDataMQ = std::move(dataMQ);
  mStatusMQ = std::move(statusMQ);
  mEventFlag = std::move(eventFlag);
  return true;
}

bool RemoteBusOutputStream::start() { return mStream->start().isOk(); }

bool RemoteBusOutputStream::stop() { return mStream->stop().isOk(); };

AidlMmapBufferInfo RemoteBusOutputStream::createMmapBuffer(
    int32_t minBufferSizeFrames) {
  AidlMmapBufferInfo info;
  mStream->createMmapBuffer(minBufferSizeFrames, &info);
  return info;
}

AidlPresentationPosition RemoteBusOutputStream::getMmapPosition() {
  AidlPresentationPosition position;
  mStream->getMmapPosition(&position);
  return position;
}

}  // namespace service
}  // namespace audio_proxy