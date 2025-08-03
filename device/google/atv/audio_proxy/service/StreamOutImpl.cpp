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

#include "StreamOutImpl.h"

#include <android-base/logging.h>
#include <inttypes.h>
#include <math.h>
#include <system/audio-hal-enums.h>
#include <time.h>
#include <utils/Log.h>

#include <cstring>

#include "AidlTypes.h"
#include "BusOutputStream.h"
#include "WriteThread.h"

using android::status_t;
using android::hardware::hidl_memory;

namespace audio_proxy::service {

namespace {

// 1GB
constexpr uint32_t kMaxBufferSize = 1 << 30;

constexpr int64_t kOneSecInNs = 1'000'000'000;

void deleteEventFlag(EventFlag* obj) {
  if (!obj) {
    return;
  }

  status_t status = EventFlag::deleteEventFlag(&obj);
  if (status) {
    LOG(ERROR) << "Write MQ event flag deletion error: " << strerror(-status);
  }
}

uint64_t estimatePlayedFramesSince(const TimeSpec& timestamp,
                                   uint32_t sampleRateHz) {
  timespec now = {0, 0};
  clock_gettime(CLOCK_MONOTONIC, &now);
  int64_t deltaSec = 0;
  int64_t deltaNSec = 0;
  if (now.tv_nsec >= timestamp.tvNSec) {
    deltaSec = now.tv_sec - timestamp.tvSec;
    deltaNSec = now.tv_nsec - timestamp.tvNSec;
  } else {
    deltaSec = now.tv_sec - timestamp.tvSec - 1;
    deltaNSec = kOneSecInNs + now.tv_nsec - timestamp.tvNSec;
  }

  if (deltaSec < 0 || deltaNSec < 0) {
    return 0;
  }

  return deltaSec * sampleRateHz + deltaNSec * sampleRateHz / kOneSecInNs;
}

}  // namespace

StreamOutImpl::StreamOutImpl(std::shared_ptr<BusOutputStream> stream,
                             const StreamOutConfig& config)
    : mStream(std::move(stream)),
      mConfig(config),
      mBufferSizeBytes(mStream->getConfig().bufferSizeBytes),
      mLatencyMs(mStream->getConfig().latencyMs),
      mEventFlag(nullptr, deleteEventFlag) {}

StreamOutImpl::~StreamOutImpl() {
  if (mWriteThread) {
    mWriteThread->stop();
    status_t status = mWriteThread->join();
    if (status) {
      LOG(ERROR) << "write thread exit error " << strerror(-status);
    }
  }

  mEventFlag.reset();
}

Return<uint64_t> StreamOutImpl::getFrameSize() {
  return mStream->getFrameSize();
}

Return<uint64_t> StreamOutImpl::getFrameCount() {
  return mBufferSizeBytes / mStream->getFrameSize();
}

Return<uint64_t> StreamOutImpl::getBufferSize() { return mBufferSizeBytes; }

#if MAJOR_VERSION >= 7
Return<void> StreamOutImpl::getSupportedProfiles(
    getSupportedProfiles_cb _hidl_cb) {
  // For devices with fixed configuration, this method can return NOT_SUPPORTED.
  _hidl_cb(Result::NOT_SUPPORTED, {});
  return Void();
}

Return<void> StreamOutImpl::getAudioProperties(getAudioProperties_cb _hidl_cb) {
  _hidl_cb(Result::OK, mConfig);
  return Void();
}

Return<Result> StreamOutImpl::setAudioProperties(
    const AudioConfigBaseOptional& config) {
  return Result::NOT_SUPPORTED;
}
#else
Return<uint32_t> StreamOutImpl::getSampleRate() { return mConfig.sampleRateHz; }

Return<void> StreamOutImpl::getSupportedSampleRates(
    AudioFormat format, getSupportedSampleRates_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, {});
  return Void();
}

Return<void> StreamOutImpl::getSupportedChannelMasks(
    AudioFormat format, getSupportedChannelMasks_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, {});
  return Void();
}

Return<Result> StreamOutImpl::setSampleRate(uint32_t sampleRateHz) {
  return Result::NOT_SUPPORTED;
}

Return<hidl_bitfield<AudioChannelMask>> StreamOutImpl::getChannelMask() {
  return mConfig.channelMask;
}

Return<Result> StreamOutImpl::setChannelMask(
    hidl_bitfield<AudioChannelMask> mask) {
  return Result::NOT_SUPPORTED;
}

Return<AudioFormat> StreamOutImpl::getFormat() { return mConfig.format; }

Return<void> StreamOutImpl::getSupportedFormats(
    getSupportedFormats_cb _hidl_cb) {
#if MAJOR_VERSION >= 6
  _hidl_cb(Result::NOT_SUPPORTED, {});
#else
  _hidl_cb({});
#endif
  return Void();
}

Return<Result> StreamOutImpl::setFormat(AudioFormat format) {
  return Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::getAudioProperties(getAudioProperties_cb _hidl_cb) {
  _hidl_cb(mConfig.sampleRateHz, mConfig.channelMask, mConfig.format);
  return Void();
}
#endif

// We don't support effects. So any effectId is invalid.
Return<Result> StreamOutImpl::addEffect(uint64_t effectId) {
  return Result::INVALID_ARGUMENTS;
}

Return<Result> StreamOutImpl::removeEffect(uint64_t effectId) {
  return Result::INVALID_ARGUMENTS;
}

Return<Result> StreamOutImpl::standby() {
  bool success = mStream->standby();
  if (!success) {
    return Result::INVALID_STATE;
  }

  mTotalPlayedFramesSinceStandby = estimateTotalPlayedFrames();
  return Result::OK;
}

Return<void> StreamOutImpl::getDevices(getDevices_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, {});
  return Void();
}

Return<Result> StreamOutImpl::setDevices(
    const hidl_vec<DeviceAddress>& devices) {
  return Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::getParameters(
    const hidl_vec<ParameterValue>& context, const hidl_vec<hidl_string>& keys,
    getParameters_cb _hidl_cb) {
  _hidl_cb(keys.size() > 0 ? Result::NOT_SUPPORTED : Result::OK, {});
  return Void();
}

Return<Result> StreamOutImpl::setParameters(
    const hidl_vec<ParameterValue>& context,
    const hidl_vec<ParameterValue>& parameters) {
  return Result::OK;
}

Return<Result> StreamOutImpl::setHwAvSync(uint32_t hwAvSync) {
  return Result::NOT_SUPPORTED;
}

Return<Result> StreamOutImpl::close() {
  if (!mStream) {
    return Result::INVALID_STATE;
  }

  if (mWriteThread) {
    mWriteThread->stop();
  }

  if (!mStream->close()) {
    LOG(WARNING) << "Failed to close stream.";
  }

  mStream = nullptr;

  return Result::OK;
}

Return<uint32_t> StreamOutImpl::getLatency() { return mLatencyMs; }

Return<Result> StreamOutImpl::setVolume(float left, float right) {
  if (isnan(left) || left < 0.f || left > 1.f || isnan(right) || right < 0.f ||
      right > 1.f) {
    return Result::INVALID_ARGUMENTS;
  }
  return mStream->setVolume(left, right) ? Result::OK : Result::INVALID_STATE;
}

Return<void> StreamOutImpl::prepareForWriting(uint32_t frameSize,
                                              uint32_t framesCount,
                                              prepareForWriting_cb _hidl_cb) {
#if MAJOR_VERSION >= 7
  int32_t threadInfo = 0;
#else
  ThreadInfo threadInfo = {0, 0};
#endif

  // Wrap the _hidl_cb to return an error
  auto sendError = [&threadInfo, &_hidl_cb](Result result) -> Return<void> {
    _hidl_cb(result, CommandMQ::Descriptor(), DataMQ::Descriptor(),
             StatusMQ::Descriptor(), threadInfo);
    return Void();
  };

  if (mDataMQ) {
    LOG(ERROR) << "The client attempted to call prepareForWriting twice";
    return sendError(Result::INVALID_STATE);
  }

  if (frameSize == 0 || framesCount == 0) {
    LOG(ERROR) << "Invalid frameSize (" << frameSize << ") or framesCount ("
               << framesCount << ")";
    return sendError(Result::INVALID_ARGUMENTS);
  }

  if (frameSize > kMaxBufferSize / framesCount) {
    LOG(ERROR) << "Buffer too big: " << frameSize << "*" << framesCount
               << " bytes > MAX_BUFFER_SIZE (" << kMaxBufferSize << ")";
    return sendError(Result::INVALID_ARGUMENTS);
  }

  auto commandMQ = std::make_unique<CommandMQ>(1);
  if (!commandMQ->isValid()) {
    LOG(ERROR) << "Command MQ is invalid";
    return sendError(Result::INVALID_ARGUMENTS);
  }

  auto dataMQ =
      std::make_unique<DataMQ>(frameSize * framesCount, true /* EventFlag */);
  if (!dataMQ->isValid()) {
    LOG(ERROR) << "Data MQ is invalid";
    return sendError(Result::INVALID_ARGUMENTS);
  }

  auto statusMQ = std::make_unique<StatusMQ>(1);
  if (!statusMQ->isValid()) {
    LOG(ERROR) << "Status MQ is invalid";
    return sendError(Result::INVALID_ARGUMENTS);
  }

  EventFlag* rawEventFlag = nullptr;
  status_t status =
      EventFlag::createEventFlag(dataMQ->getEventFlagWord(), &rawEventFlag);
  std::unique_ptr<EventFlag, EventFlagDeleter> eventFlag(rawEventFlag,
                                                         deleteEventFlag);
  if (status != ::android::OK || !eventFlag) {
    LOG(ERROR) << "Failed creating event flag for data MQ: "
               << strerror(-status);
    return sendError(Result::INVALID_ARGUMENTS);
  }

  if (!mStream->prepareForWriting(frameSize, framesCount)) {
    LOG(ERROR) << "Failed to prepare writing channel.";
    return sendError(Result::INVALID_ARGUMENTS);
  }

  sp<WriteThread> writeThread =
      sp<WriteThread>::make(mStream, commandMQ.get(), dataMQ.get(),
                            statusMQ.get(), eventFlag.get(), mLatencyMs);
  status = writeThread->run("writer", ::android::PRIORITY_URGENT_AUDIO);
  if (status != ::android::OK) {
    LOG(ERROR) << "Failed to start writer thread: " << strerror(-status);
    return sendError(Result::INVALID_ARGUMENTS);
  }

  mCommandMQ = std::move(commandMQ);
  mDataMQ = std::move(dataMQ);
  mStatusMQ = std::move(statusMQ);
  mEventFlag = std::move(eventFlag);
  mWriteThread = std::move(writeThread);

#if MAJOR_VERSION >= 7
  threadInfo = mWriteThread->getTid();
#else
  threadInfo.pid = getpid();
  threadInfo.tid = mWriteThread->getTid();
#endif

  _hidl_cb(Result::OK, *mCommandMQ->getDesc(), *mDataMQ->getDesc(),
           *mStatusMQ->getDesc(), threadInfo);

  return Void();
}

Return<void> StreamOutImpl::getRenderPosition(getRenderPosition_cb _hidl_cb) {
  uint64_t totalPlayedFrames = estimateTotalPlayedFrames();
  if (totalPlayedFrames == 0) {
    _hidl_cb(Result::OK, 0);
    return Void();
  }

  // getRenderPosition returns the number of frames played since the output has
  // exited standby.
  DCHECK_GE(totalPlayedFrames, mTotalPlayedFramesSinceStandby);
  uint64_t position = totalPlayedFrames - mTotalPlayedFramesSinceStandby;

  if (position > std::numeric_limits<uint32_t>::max()) {
    _hidl_cb(Result::INVALID_STATE, 0);
    return Void();
  }

  _hidl_cb(Result::OK, position);
  return Void();
}

Return<void> StreamOutImpl::getNextWriteTimestamp(
    getNextWriteTimestamp_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, 0);
  return Void();
}

Return<Result> StreamOutImpl::setCallback(
    const sp<IStreamOutCallback>& callback) {
  return Result::NOT_SUPPORTED;
}

Return<Result> StreamOutImpl::clearCallback() { return Result::NOT_SUPPORTED; }

Return<void> StreamOutImpl::supportsPauseAndResume(
    supportsPauseAndResume_cb _hidl_cb) {
  _hidl_cb(true, true);
  return Void();
}

// pause should not be called before starting the playback.
Return<Result> StreamOutImpl::pause() {
  if (!mWriteThread) {
    return Result::INVALID_STATE;
  }

  if (!mStream->pause()) {
    return Result::INVALID_STATE;
  }

  mIsPaused = true;
  return Result::OK;
}

// Resume should onl be called after pause.
Return<Result> StreamOutImpl::resume() {
  if (!mIsPaused) {
    return Result::INVALID_STATE;
  }

  if (!mStream->resume()) {
    return Result::INVALID_STATE;
  }

  mIsPaused = false;
  return Result::OK;
}

// Drain and flush should always succeed if supported.
Return<bool> StreamOutImpl::supportsDrain() { return true; }

Return<Result> StreamOutImpl::drain(AudioDrain type) {
  if (!mStream->drain(static_cast<AidlAudioDrain>(type))) {
    LOG(WARNING) << "Failed to drain the stream.";
  }

  return Result::OK;
}

Return<Result> StreamOutImpl::flush() {
  if (!mStream->flush()) {
    LOG(WARNING) << "Failed to flush the stream.";
  }

  return Result::OK;
}

Return<void> StreamOutImpl::getPresentationPosition(
    getPresentationPosition_cb _hidl_cb) {
  if (!mWriteThread) {
    _hidl_cb(Result::INVALID_STATE, 0, {});
    return Void();
  }

  auto [frames, timestamp] = mWriteThread->getPresentationPosition();
  _hidl_cb(Result::OK, frames, timestamp);
  return Void();
}

Return<Result> StreamOutImpl::start() {
  return mStream->start() ? Result::OK : Result::NOT_SUPPORTED;
}

Return<Result> StreamOutImpl::stop() {
  return mStream->stop() ? Result::OK : Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::createMmapBuffer(int32_t minSizeFrames,
                                             createMmapBuffer_cb _hidl_cb) {
  MmapBufferInfo hidlInfo;
  AidlMmapBufferInfo info = mStream->createMmapBuffer(minSizeFrames);
  int sharedMemoryFd = info.sharedMemoryFd.get();
  if (sharedMemoryFd == -1) {
    _hidl_cb(Result::NOT_SUPPORTED, hidlInfo);
    return Void();
  }

  native_handle_t* hidlHandle = nullptr;
  hidlHandle = native_handle_create(1, 0);
  hidlHandle->data[0] = sharedMemoryFd;

  hidlInfo.sharedMemory =
      hidl_memory("audio_proxy_mmap_buffer", hidlHandle,
                  mStream->getFrameSize() * info.bufferSizeFrames);
  hidlInfo.bufferSizeFrames = info.bufferSizeFrames;
  hidlInfo.burstSizeFrames = info.burstSizeFrames;
  hidlInfo.flags = static_cast<hidl_bitfield<MmapBufferFlag>>(info.flags);
  _hidl_cb(Result::OK, hidlInfo);
  return Void();
}

Return<void> StreamOutImpl::getMmapPosition(getMmapPosition_cb _hidl_cb) {
  MmapPosition hidlPosition;

  AidlPresentationPosition position = mStream->getMmapPosition();
  if (position.timestamp.tvSec == 0 && position.timestamp.tvNSec == 0) {
    _hidl_cb(Result::NOT_SUPPORTED, hidlPosition);
    return Void();
  }

  hidlPosition.timeNanoseconds =
      position.timestamp.tvSec * kOneSecInNs + position.timestamp.tvNSec;
  hidlPosition.positionFrames = position.frames;
  _hidl_cb(Result::OK, hidlPosition);
  return Void();
}

#if MAJOR_VERSION >= 7
Return<Result> StreamOutImpl::updateSourceMetadata(
    const SourceMetadata& sourceMetadata) {
  return Result::NOT_SUPPORTED;
}
#else
Return<void> StreamOutImpl::updateSourceMetadata(
    const SourceMetadata& sourceMetadata) {
  return Void();
}
#endif

Return<Result> StreamOutImpl::selectPresentation(int32_t presentationId,
                                                 int32_t programId) {
  return Result::NOT_SUPPORTED;
}

std::shared_ptr<BusOutputStream> StreamOutImpl::getOutputStream() {
  return mStream;
}

void StreamOutImpl::updateOutputStream(
    std::shared_ptr<BusOutputStream> stream) {
  DCHECK(stream);
  DCHECK(mStream);
  if (stream->getConfig() != mStream->getConfig()) {
    LOG(ERROR) << "New stream's config doesn't match the old stream's config.";
    return;
  }

  if (mWriteThread) {
    if (!stream->prepareForWriting(mStream->getWritingFrameSize(),
                                   mStream->getWritingFrameCount())) {
      LOG(ERROR) << "Failed to prepare writing channel.";
      return;
    }

    mWriteThread->updateOutputStream(stream);
  }

  mStream = std::move(stream);
}

uint64_t StreamOutImpl::estimateTotalPlayedFrames() const {
  if (!mWriteThread) {
    return 0;
  }

  auto [frames, timestamp] = mWriteThread->getPresentationPosition();
  if (frames == 0) {
    return 0;
  }

  return frames + estimatePlayedFramesSince(timestamp, mConfig.sampleRateHz);
}

#if MAJOR_VERSION >= 6
Return<Result> StreamOutImpl::setEventCallback(
    const sp<IStreamOutEventCallback>& callback) {
  return Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::getDualMonoMode(getDualMonoMode_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, DualMonoMode::OFF);
  return Void();
}

Return<Result> StreamOutImpl::setDualMonoMode(DualMonoMode mode) {
  return Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::getAudioDescriptionMixLevel(
    getAudioDescriptionMixLevel_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, 0.f);
  return Void();
}

Return<Result> StreamOutImpl::setAudioDescriptionMixLevel(float leveldB) {
  return Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::getPlaybackRateParameters(
    getPlaybackRateParameters_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, {});
  return Void();
}

Return<Result> StreamOutImpl::setPlaybackRateParameters(
    const PlaybackRate& playbackRate) {
  return Result::NOT_SUPPORTED;
}
#endif

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
Return<Result> StreamOutImpl::setLatencyMode(
    android::hardware::audio::V7_1::LatencyMode mode) {
  return Result::NOT_SUPPORTED;
}

Return<void> StreamOutImpl::getRecommendedLatencyModes(
    getRecommendedLatencyModes_cb _hidl_cb) {
  _hidl_cb(Result::NOT_SUPPORTED, {});
  return Void();
}

Return<Result> StreamOutImpl::setLatencyModeCallback(
    const sp<android::hardware::audio::V7_1::IStreamOutLatencyModeCallback>&
        cb) {
  return Result::NOT_SUPPORTED;
}
#endif

}  // namespace audio_proxy::service
