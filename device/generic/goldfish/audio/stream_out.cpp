/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <log/log.h>
#include <fmq/EventFlag.h>
#include <fmq/MessageQueue.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/ThreadDefs.h>
#include PATH(APM_XSD_ENUMS_H_FILENAME)
#include <future>
#include <thread>
#include "stream_out.h"
#include "device_port_sink.h"
#include "deleters.h"
#include "audio_ops.h"
#include "util.h"
#include "debug.h"

namespace xsd {
using namespace ::android::audio::policy::configuration::CPP_VERSION;
}

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

using ::android::hardware::Void;
using namespace ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION;
using namespace ::android::hardware::audio::CORE_TYPES_CPP_VERSION;

namespace {

class WriteThread : public IOThread {
    typedef MessageQueue<IStreamOut::WriteCommand, kSynchronizedReadWrite> CommandMQ;
    typedef MessageQueue<IStreamOut::WriteStatus, kSynchronizedReadWrite> StatusMQ;
    typedef MessageQueue<uint8_t, kSynchronizedReadWrite> DataMQ;

public:
    WriteThread(StreamOut *stream, const size_t mqBufferSize)
            : mStream(stream)
            , mCommandMQ(1)
            , mStatusMQ(1)
            , mDataMQ(mqBufferSize, true /* EventFlag */) {
        if (!mCommandMQ.isValid()) {
            ALOGE("WriteThread::%s:%d: mCommandMQ is invalid", __func__, __LINE__);
            return;
        }
        if (!mDataMQ.isValid()) {
            ALOGE("WriteThread::%s:%d: mDataMQ is invalid", __func__, __LINE__);
            return;
        }
        if (!mStatusMQ.isValid()) {
            ALOGE("WriteThread::%s:%d: mStatusMQ is invalid", __func__, __LINE__);
            return;
        }

        status_t status;

        EventFlag* rawEfGroup = nullptr;
        status = EventFlag::createEventFlag(mDataMQ.getEventFlagWord(), &rawEfGroup);
        if (status != OK || !rawEfGroup) {
            ALOGE("WriteThread::%s:%d: rawEfGroup is invalid", __func__, __LINE__);
            return;
        } else {
            mEfGroup.reset(rawEfGroup);
        }

        mThread = std::thread(&WriteThread::threadLoop, this);
    }

    ~WriteThread() {
        if (mThread.joinable()) {
            requestExit();
            mThread.join();
        }
    }

    EventFlag *getEventFlag() override {
        return mEfGroup.get();
    }

    bool isRunning() const {
        return mThread.joinable();
    }

    std::future<pthread_t> getTid() {
        return mTid.get_future();
    }

    Result getPresentationPosition(uint64_t &frames, TimeSpec &ts) const {
        std::lock_guard l(mExternalSinkReadLock);
        if (mSink == nullptr) {
            // this could return a slightly stale position under data race.
            frames = mFrames,
            ts = util::nsecs2TimeSpec(systemTime(SYSTEM_TIME_MONOTONIC));
            return Result::OK;
        } else {
            return mSink->getPresentationPosition(frames, ts);
        }
    }

    auto getDescriptors() const {
        return std::make_tuple(
                mCommandMQ.getDesc(), mDataMQ.getDesc(), mStatusMQ.getDesc());
    }

private:
    void threadLoop() {
        util::setThreadPriority(SP_AUDIO_SYS, PRIORITY_AUDIO);
        mTid.set_value(pthread_self());

        while (true) {
            uint32_t efState = 0;
            mEfGroup->wait(MessageQueueFlagBits::NOT_EMPTY | STAND_BY_REQUEST | EXIT_REQUEST,
                           &efState);
            if (efState & EXIT_REQUEST) {
                return;
            }

            if (efState & STAND_BY_REQUEST) {
                ALOGD("%s: entering standby, frames: %llu", __func__, (unsigned long long)mFrames);
                std::lock_guard l(mExternalSinkReadLock);
                mSink.reset();
            }

            if (efState & (MessageQueueFlagBits::NOT_EMPTY | 0)) {
                if (!mSink) {
                    mFrameSize = mStream->getFrameSize();
                    auto sink = DevicePortSink::create(mDataMQ.getQuantumCount(),
                                                   mStream->getDeviceAddress(),
                                                   mStream->getAudioConfig(),
                                                   mStream->getAudioOutputFlags(),
                                                   mFrames);
                    LOG_ALWAYS_FATAL_IF(!sink);
                    std::lock_guard l(mExternalSinkReadLock);
                    mSink = std::move(sink);
                }

                processCommand();
            }
        }
    }

    void processCommand() {
        IStreamOut::WriteCommand wCommand;

        if (!mCommandMQ.read(&wCommand)) {
            return;  // Nothing to do.
        }

        IStreamOut::WriteStatus wStatus;
        switch (wCommand) {
            case IStreamOut::WriteCommand::WRITE:
                wStatus = doWrite();
                break;

            case IStreamOut::WriteCommand::GET_PRESENTATION_POSITION:
                wStatus = doGetPresentationPosition();
                break;

            case IStreamOut::WriteCommand::GET_LATENCY:
                wStatus = doGetLatency();
                break;

            default:
                ALOGE("WriteThread::%s:%d: Unknown write thread command code %d",
                      __func__, __LINE__, wCommand);
                wStatus.retval = FAILURE(Result::NOT_SUPPORTED);
                break;
        }

        wStatus.replyTo = wCommand;

        if (!mStatusMQ.write(&wStatus)) {
            ALOGE("status message queue write failed");
        }

        mEfGroup->wake(MessageQueueFlagBits::NOT_FULL | 0);
    }

    IStreamOut::WriteStatus doWrite() {
        struct MQReader : public IReader {
            explicit MQReader(DataMQ &mq) : dataMQ(mq) {}

            size_t operator()(void *dst, size_t sz) override {
                if (dataMQ.read(static_cast<uint8_t *>(dst), sz)) {
                    totalRead += sz;
                    return sz;
                } else {
                    ALOGE("WriteThread::%s:%d: DataMQ::read failed",
                          __func__, __LINE__);
                    return 0;
                }
            }

            size_t totalRead = 0;
            DataMQ &dataMQ;
        };

        MQReader reader(mDataMQ);
        mSink->write(mStream->getEffectiveVolume(), mDataMQ.availableToRead(), reader);

        const size_t written = reader.totalRead / mFrameSize;
        mFrames += written;
        ALOGV("%s: mFrames: %llu  %zu", __func__, (unsigned long long) mFrames, written);

        IStreamOut::WriteStatus status;
        status.retval = Result::OK;
        status.reply.written = reader.totalRead;
        return status;
    }

    IStreamOut::WriteStatus doGetPresentationPosition() {
        IStreamOut::WriteStatus status;

        status.retval = mSink->getPresentationPosition(
            status.reply.presentationPosition.frames,
            status.reply.presentationPosition.timeStamp);

        ALOGV("%s: presentation position: %llu  %lld", __func__,
                (unsigned long long) status.reply.presentationPosition.frames,
                (long long) util::timespec2Nsecs( status.reply.presentationPosition.timeStamp));

        return status;
    }

    IStreamOut::WriteStatus doGetLatency() {
        IStreamOut::WriteStatus status;

        const int latencyMs =
            DevicePortSink::getLatencyMs(mStream->getDeviceAddress(),
                                         mStream->getAudioConfig());

        if (latencyMs >= 0) {
            status.retval = Result::OK;
            status.reply.latencyMs = latencyMs;
        } else {
            status.retval = Result::INVALID_STATE;
        }

        return status;
    }

    StreamOut *const mStream;
    CommandMQ mCommandMQ;
    StatusMQ mStatusMQ;
    DataMQ mDataMQ;
    std::unique_ptr<EventFlag, deleters::forEventFlag> mEfGroup;
    std::thread mThread;
    std::promise<pthread_t> mTid;
    size_t mFrameSize = 1;                    // updated when the sink is created.
    std::atomic<uint64_t> mFrames = 0;        // preserve framecount during standby.
    mutable std::mutex mExternalSinkReadLock; // used for external access to mSink.
    std::unique_ptr<DevicePortSink> mSink;
};

} // namespace

StreamOut::StreamOut(sp<Device> dev,
                     int32_t ioHandle,
                     const DeviceAddress& device,
                     const AudioConfig& config,
                     hidl_vec<AudioInOutFlag> flags,
                     const SourceMetadata& sourceMetadata)
        : mDev(std::move(dev))
        , mCommon(ioHandle, device, config, std::move(flags))
        , mSourceMetadata(sourceMetadata) {}

StreamOut::~StreamOut() {
    closeImpl(true);
}

Return<uint64_t> StreamOut::getFrameSize() {
    return mCommon.getFrameSize();
}

Return<uint64_t> StreamOut::getFrameCount() {
    return mCommon.getFrameCount();
}

Return<uint64_t> StreamOut::getBufferSize() {
    return mCommon.getBufferSize();
}

Return<void> StreamOut::getSupportedProfiles(getSupportedProfiles_cb _hidl_cb) {
    mCommon.getSupportedProfiles(_hidl_cb);
    return Void();
}

Return<void> StreamOut::getAudioProperties(getAudioProperties_cb _hidl_cb) {
    mCommon.getAudioProperties(_hidl_cb);
    return Void();
}

Return<Result> StreamOut::setAudioProperties(const AudioConfigBaseOptional& config) {
    (void)config;
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<Result> StreamOut::addEffect(uint64_t effectId) {
    (void)effectId;
    return FAILURE(Result::INVALID_ARGUMENTS);
}

Return<Result> StreamOut::removeEffect(uint64_t effectId) {
    (void)effectId;
    return FAILURE(Result::INVALID_ARGUMENTS);
}

Return<Result> StreamOut::standby() {
    if (mWriteThread) {
        LOG_ALWAYS_FATAL_IF(!mWriteThread->standby());
    }

    return Result::OK;
}

Return<void> StreamOut::getDevices(getDevices_cb _hidl_cb) {
    mCommon.getDevices(_hidl_cb);
    return Void();
}

Return<Result> StreamOut::setDevices(const hidl_vec<DeviceAddress>& devices) {
    return mCommon.setDevices(devices);
}

Return<void> StreamOut::getParameters(const hidl_vec<ParameterValue>& context,
                                      const hidl_vec<hidl_string>& keys,
                                      getParameters_cb _hidl_cb) {
    (void)context;
    _hidl_cb((keys.size() > 0) ? Result::NOT_SUPPORTED : Result::OK, {});
    return Void();
}

Return<Result> StreamOut::setParameters(const hidl_vec<ParameterValue>& context,
                                        const hidl_vec<ParameterValue>& parameters) {
    (void)context;
    (void)parameters;
    return Result::OK;
}

Return<Result> StreamOut::setHwAvSync(uint32_t hwAvSync) {
    (void)hwAvSync;
    return FAILURE(Result::NOT_SUPPORTED);
}

Result StreamOut::closeImpl(const bool fromDctor) {
    if (mDev) {
        mWriteThread.reset();
        mDev->unrefDevice(this);
        mDev = nullptr;
        return Result::OK;
    } else if (fromDctor) {
        // closeImpl is always called from the dctor, it is ok if mDev is null,
        // we don't want to log the error in this case.
        return Result::OK;
    } else {
        return FAILURE(Result::INVALID_STATE);
    }
}

Return<Result> StreamOut::close() {
    return closeImpl(false);
}

Return<Result> StreamOut::start() {
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<Result> StreamOut::stop() {
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<void> StreamOut::createMmapBuffer(int32_t minSizeFrames,
                                         createMmapBuffer_cb _hidl_cb) {
    (void)minSizeFrames;
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), {});
    return Void();
}

Return<void> StreamOut::getMmapPosition(getMmapPosition_cb _hidl_cb) {
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), {});
    return Void();
}

Return<uint32_t> StreamOut::getLatency() {
    const int latencyMs = DevicePortSink::getLatencyMs(getDeviceAddress(), getAudioConfig());

    return (latencyMs >= 0) ? latencyMs :
        (mCommon.getFrameCount() * 1000 / mCommon.getSampleRate());
}

Return<Result> StreamOut::setVolume(float left, float right) {
    if (isnan(left) || left < 0.0f || left > 1.0f
        || right < 0.0f || right > 1.0f || isnan(right)) {
        return FAILURE(Result::INVALID_ARGUMENTS);
    }

    std::lock_guard<std::mutex> guard(mMutex);
    mStreamVolume = (left + right) / 2.0f;
    updateEffectiveVolumeLocked();
    return Result::OK;
}

Return<Result> StreamOut::updateSourceMetadata(const SourceMetadata& sourceMetadata) {
    (void)sourceMetadata;
    return Result::NOT_SUPPORTED;
}

Return<void> StreamOut::prepareForWriting(uint32_t frameSize,
                                          uint32_t framesCount,
                                          prepareForWriting_cb _hidl_cb) {
    if (!frameSize || !framesCount || frameSize > 256 || framesCount > (1u << 20)) {
        _hidl_cb(FAILURE(Result::INVALID_ARGUMENTS), {}, {}, {}, -1);
        return Void();
    }

    if (mWriteThread) {  // INVALID_STATE if the method was already called.
        _hidl_cb(FAILURE(Result::INVALID_STATE), {}, {}, {}, -1);
        return Void();
    }

    auto t = std::make_unique<WriteThread>(this, frameSize * framesCount);

    if (t->isRunning()) {
        const auto [commandDesc, dataDesc, statusDesc ] = t->getDescriptors();
        _hidl_cb(Result::OK,
                 *commandDesc,
                 *dataDesc,
                 *statusDesc,
                 t->getTid().get());

        mWriteThread = std::move(t);
    } else {
        _hidl_cb(FAILURE(Result::INVALID_ARGUMENTS), {}, {}, {}, -1);
    }

    return Void();
}

Return<void> StreamOut::getRenderPosition(getRenderPosition_cb _hidl_cb) {
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), 0);
    return Void();
}

Return<void> StreamOut::getNextWriteTimestamp(getNextWriteTimestamp_cb _hidl_cb) {
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), 0);
    return Void();
}

Return<Result> StreamOut::setCallback(const sp<IStreamOutCallback>& callback) {
    (void)callback;
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<Result> StreamOut::clearCallback() {
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<Result> StreamOut::setEventCallback(const sp<IStreamOutEventCallback>& callback) {
    (void)callback;
    return Result::NOT_SUPPORTED;
}

Return<void> StreamOut::supportsPauseAndResume(supportsPauseAndResume_cb _hidl_cb) {
    _hidl_cb(false, false);
    return Void();
}

Return<Result> StreamOut::pause() {
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<Result> StreamOut::resume() {
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<bool> StreamOut::supportsDrain() {
    return false;
}

Return<Result> StreamOut::drain(AudioDrain type) {
    (void)type;
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<Result> StreamOut::flush() {
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<void> StreamOut::getPresentationPosition(getPresentationPosition_cb _hidl_cb) {
    const auto w = static_cast<WriteThread*>(mWriteThread.get());
    if (!w) {
        _hidl_cb(FAILURE(Result::INVALID_STATE), {}, {});
        return Void();
    }
    uint64_t frames{};
    TimeSpec ts{};
    const Result r = w->getPresentationPosition(frames, ts);
    ALOGV("%s: presentation position: %llu  %lld", __func__,
            (unsigned long long) frames, (long long) util::timespec2Nsecs(ts));
    _hidl_cb(r, frames, ts);
    return Void();
}

Return<Result> StreamOut::selectPresentation(int32_t presentationId,
                                             int32_t programId) {
    (void)presentationId;
    (void)programId;
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<void> StreamOut::getDualMonoMode(getDualMonoMode_cb _hidl_cb) {
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), {});
    return Void();
}

Return<Result> StreamOut::setDualMonoMode(DualMonoMode mode) {
    (void)mode;
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<void> StreamOut::getAudioDescriptionMixLevel(getAudioDescriptionMixLevel_cb _hidl_cb) {
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), 0);
    return Void();
}

Return<Result> StreamOut::setAudioDescriptionMixLevel(float leveldB) {
    (void)leveldB;
    return FAILURE(Result::NOT_SUPPORTED);
}

Return<void> StreamOut::getPlaybackRateParameters(getPlaybackRateParameters_cb _hidl_cb) {
    _hidl_cb(FAILURE(Result::NOT_SUPPORTED), {});
    return Void();
}

Return<Result> StreamOut::setPlaybackRateParameters(const PlaybackRate &playbackRate) {
    (void)playbackRate;
    return FAILURE(Result::NOT_SUPPORTED);
}

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
Return<Result> StreamOut::setLatencyMode(LatencyMode mode __unused) {
    return Result::NOT_SUPPORTED;
};

Return<void> StreamOut::getRecommendedLatencyModes(getRecommendedLatencyModes_cb _hidl_cb) {
    hidl_vec<LatencyMode> hidlModes;
    _hidl_cb(Result::NOT_SUPPORTED, hidlModes);
    return Void();
};

Return<Result> StreamOut::setLatencyModeCallback(
        const sp<IStreamOutLatencyModeCallback>& callback __unused) {
    return Result::NOT_SUPPORTED;
};
#endif

void StreamOut::setMasterVolume(float masterVolume) {
    std::lock_guard<std::mutex> guard(mMutex);
    mMasterVolume = masterVolume;
    updateEffectiveVolumeLocked();
}

void StreamOut::updateEffectiveVolumeLocked() {
    mEffectiveVolume = mMasterVolume * mStreamVolume;
}

bool StreamOut::validateDeviceAddress(const DeviceAddress& device) {
    return DevicePortSink::validateDeviceAddress(device);
}

bool StreamOut::validateFlags(const hidl_vec<AudioInOutFlag>& flags) {
    return std::all_of(flags.begin(), flags.end(), [](const AudioInOutFlag& flag){
        return xsd::stringToAudioInOutFlag(flag) != xsd::AudioInOutFlag::UNKNOWN;
    });
}

bool StreamOut::validateSourceMetadata(const SourceMetadata& sourceMetadata) {
    for (const auto& track : sourceMetadata.tracks) {
        if (xsd::isUnknownAudioUsage(track.usage)
                || xsd::isUnknownAudioContentType(track.contentType)
                || xsd::isUnknownAudioChannelMask(track.channelMask)) {
            return false;
        }
        for (const auto& tag : track.tags) {
            if (!xsd::isVendorExtension(tag)) {
                return false;
            }
        }
    }
    return true;
}

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
