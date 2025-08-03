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

#include PATH(APM_XSD_ENUMS_H_FILENAME)
#include <android-base/properties.h>
#include <chrono>
#include <thread>
#include <log/log.h>
#include <utils/Mutex.h>
#include <utils/Timers.h>
#include <utils/ThreadDefs.h>
#include "device_port_sink.h"
#include "talsa.h"
#include "audio_ops.h"
#include "ring_buffer.h"
#include "util.h"
#include "debug.h"

using ::android::base::GetBoolProperty;

namespace xsd {
using namespace ::android::audio::policy::configuration::CPP_VERSION;
}

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

namespace {

constexpr int kMaxJitterUs = 3000;  // Enforced by CTS, should be <= 6ms

struct TinyalsaSink : public DevicePortSink {
    TinyalsaSink(unsigned pcmCard, unsigned pcmDevice,
                 const AudioConfig &cfg,
                 uint64_t initialFrames)
            : mStartNs(systemTime(SYSTEM_TIME_MONOTONIC))
            , mSampleRateHz(cfg.base.sampleRateHz)
            , mFrameSize(util::countChannels(cfg.base.channelMask) * sizeof(int16_t))
            , mWriteSizeFrames(cfg.frameCount)
            , mInitialFrames(initialFrames)
            , mFrames(initialFrames)
            , mRingBuffer(mFrameSize * cfg.frameCount * 3)
            , mMixer(pcmCard)
            , mPcm(talsa::pcmOpen(pcmCard, pcmDevice,
                                  util::countChannels(cfg.base.channelMask),
                                  cfg.base.sampleRateHz,
                                  cfg.frameCount,
                                  true /* isOut */)) {
        if (mPcm) {
            mConsumeThread = std::thread(&TinyalsaSink::consumeThread, this);
        } else {
            mConsumeThread = std::thread([](){});
        }
    }

    ~TinyalsaSink() {
        mConsumeThreadRunning = false;
        mConsumeThread.join();
    }

    static int getLatencyMs(const AudioConfig &cfg) {
        constexpr size_t inMs = 1000;
        const talsa::PcmPeriodSettings periodSettings =
            talsa::pcmGetPcmPeriodSettings();
        const size_t numerator = periodSettings.periodSizeMultiplier * cfg.frameCount;
        const size_t denominator = periodSettings.periodCount * cfg.base.sampleRateHz / inMs;

        // integer division with rounding
        return (numerator + (denominator >> 1)) / denominator + talsa::pcmGetHostLatencyMs();
    }

    Result getPresentationPosition(uint64_t &frames, TimeSpec &ts) override {
        const AutoMutex lock(mFrameCountersMutex);

        nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        const uint64_t nowFrames = getPresentationFramesLocked(nowNs);
        auto presentedFrames = nowFrames - mMissedFrames;
        if (presentedFrames > mReceivedFrames) {
          // There is another underrun that is not yet accounted for in mMissedFrames
          auto delta = presentedFrames - mReceivedFrames;
          presentedFrames -= delta;
          // The last frame was presented some time ago, reflect that in the result
          nowNs -= delta * 1000000000 / mSampleRateHz;
        }
        mFrames = presentedFrames + mInitialFrames;

        frames = mFrames;
        ts = util::nsecs2TimeSpec(nowNs);
        return Result::OK;
    }

    uint64_t getPresentationFramesLocked(const nsecs_t nowNs) const {
        return uint64_t(mSampleRateHz) * ns2us(nowNs - mStartNs) / 1000000;
    }

    size_t calcAvailableFramesNowLocked() {
        const nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        auto presentationFrames = getPresentationFramesLocked(nowNs);
        if (mReceivedFrames + mMissedFrames < presentationFrames) {
            // There has been an underrun
            mMissedFrames = presentationFrames - mReceivedFrames;
        }
        size_t pendingFrames = mReceivedFrames + mMissedFrames - presentationFrames;
        return mRingBuffer.capacity() / mFrameSize - pendingFrames;
    }

    size_t calcWaitFramesNowLocked(const size_t requestedFrames) {
        const size_t availableFrames = calcAvailableFramesNowLocked();
        return (requestedFrames > availableFrames)
            ? (requestedFrames - availableFrames) : 0;
    }

    size_t write(float volume, size_t bytesToWrite, IReader &reader) override {
        const AutoMutex lock(mFrameCountersMutex);

        size_t framesLost = 0;
        const size_t waitFrames = calcWaitFramesNowLocked(bytesToWrite / mFrameSize);
        const auto blockUntil =
            std::chrono::high_resolution_clock::now() +
                + std::chrono::microseconds(waitFrames * 1000000 / mSampleRateHz);

        while (bytesToWrite > 0) {
            if (mRingBuffer.waitForProduceAvailable(blockUntil
                    + std::chrono::microseconds(kMaxJitterUs))) {
                auto produceChunk = mRingBuffer.getProduceChunk();
                if (produceChunk.size >= bytesToWrite) {
                    // Since the ring buffer has more bytes free than we need,
                    // make sure we are not too early here: tinyalsa is jittery,
                    // we don't want to go faster than SYSTEM_TIME_MONOTONIC
                    std::this_thread::sleep_until(blockUntil);
                }

                const size_t szFrames =
                    std::min(produceChunk.size, bytesToWrite) / mFrameSize;
                const size_t szBytes = szFrames * mFrameSize;
                LOG_ALWAYS_FATAL_IF(reader(produceChunk.data, szBytes) < szBytes);

                aops::multiplyByVolume(volume,
                                       static_cast<int16_t *>(produceChunk.data),
                                       szBytes / sizeof(int16_t));

                LOG_ALWAYS_FATAL_IF(mRingBuffer.produce(szBytes) < szBytes);
                mReceivedFrames += szFrames;
                bytesToWrite -= szBytes;
            } else {
                ALOGV("TinyalsaSink::%s:%d pcm_writei was late reading "
                      "frames, dropping %zu us of audio",
                      __func__, __LINE__,
                      size_t(1000000 * bytesToWrite / mFrameSize / mSampleRateHz));

                // drop old audio to make room for new
                const size_t bytesLost = mRingBuffer.makeRoomForProduce(bytesToWrite);
                framesLost += bytesLost / mFrameSize;

                while (bytesToWrite > 0) {
                    auto produceChunk = mRingBuffer.getProduceChunk();
                    const size_t szFrames =
                        std::min(produceChunk.size, bytesToWrite) / mFrameSize;
                    const size_t szBytes = szFrames * mFrameSize;
                    LOG_ALWAYS_FATAL_IF(reader(produceChunk.data, szBytes) < szBytes);

                    aops::multiplyByVolume(volume,
                                           static_cast<int16_t *>(produceChunk.data),
                                           szBytes / sizeof(int16_t));

                    LOG_ALWAYS_FATAL_IF(mRingBuffer.produce(szBytes) < szBytes);
                    mReceivedFrames += szFrames;
                    bytesToWrite -= szBytes;
                }
                break;
            }
        }

        return framesLost;
    }

    void consumeThread() {
        util::setThreadPriority(SP_AUDIO_SYS, PRIORITY_AUDIO);
        std::vector<uint8_t> writeBuffer(mWriteSizeFrames * mFrameSize);

        while (mConsumeThreadRunning) {
            if (mRingBuffer.waitForConsumeAvailable(
                    std::chrono::high_resolution_clock::now()
                    + std::chrono::microseconds(100000))) {
                size_t szBytes;
                {
                    auto chunk = mRingBuffer.getConsumeChunk();
                    szBytes = std::min(writeBuffer.size(), chunk.size);
                    // We have to memcpy because the consumer holds the lock
                    // into RingBuffer and pcm_write takes too long to hold
                    // this lock.
                    memcpy(writeBuffer.data(), chunk.data, szBytes);
                    LOG_ALWAYS_FATAL_IF(mRingBuffer.consume(chunk, szBytes) < szBytes);
                }

                const uint8_t *data8 = writeBuffer.data();
                while (szBytes > 0) {
                    const int n = talsa::pcmWrite(mPcm.get(), data8, szBytes, mFrameSize);
                    if (n < 0) {
                        break;
                    }
                    LOG_ALWAYS_FATAL_IF(static_cast<size_t>(n) > szBytes,
                                        "n=%d szBytes=%zu mFrameSize=%u",
                                        n, szBytes, mFrameSize);
                    data8 += n;
                    szBytes -= n;
                }
            }
        }
    }

    static std::unique_ptr<TinyalsaSink> create(unsigned pcmCard,
                                                unsigned pcmDevice,
                                                const AudioConfig &cfg,
                                                size_t readerBufferSizeHint,
                                                uint64_t initialFrames) {
        (void)readerBufferSizeHint;
        auto sink = std::make_unique<TinyalsaSink>(pcmCard, pcmDevice,
                                                   cfg, initialFrames);
        if (sink->mMixer && sink->mPcm) {
            return sink;
        } else {
            return FAILURE(nullptr);
        }
    }

private:
    const nsecs_t mStartNs;
    const unsigned mSampleRateHz;
    const unsigned mFrameSize;
    const unsigned mWriteSizeFrames;
    const uint64_t mInitialFrames;
    uint64_t mFrames GUARDED_BY(mFrameCountersMutex);
    uint64_t mMissedFrames GUARDED_BY(mFrameCountersMutex) = 0;
    uint64_t mReceivedFrames GUARDED_BY(mFrameCountersMutex) = 0;
    RingBuffer mRingBuffer;
    talsa::Mixer mMixer;
    talsa::PcmPtr mPcm;
    std::thread mConsumeThread;
    std::atomic<bool> mConsumeThreadRunning = true;
    mutable Mutex mFrameCountersMutex;
};

struct NullSink : public DevicePortSink {
    NullSink(const AudioConfig &cfg, uint64_t initialFrames)
            : mStartNs(systemTime(SYSTEM_TIME_MONOTONIC))
            , mSampleRateHz(cfg.base.sampleRateHz)
            , mFrameSize(util::countChannels(cfg.base.channelMask) * sizeof(int16_t))
            , mInitialFrames(initialFrames)
            , mFrames(initialFrames) {}

    static int getLatencyMs(const AudioConfig &) {
        return 1;
    }

    Result getPresentationPosition(uint64_t &frames, TimeSpec &ts) override {
        const AutoMutex lock(mFrameCountersMutex);

        nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        const uint64_t nowFrames = getPresentationFramesLocked(nowNs);
        auto presentedFrames = nowFrames - mMissedFrames;
        if (presentedFrames > mReceivedFrames) {
          // There is another underrun that is not yet accounted for in mMissedFrames
          auto delta = presentedFrames - mReceivedFrames;
          presentedFrames -= delta;
          // The last frame was presented some time ago, reflect that in the result
          nowNs -= delta * 1000000000 / mSampleRateHz;
        }
        mFrames = presentedFrames + mInitialFrames;

        frames = mFrames;
        ts = util::nsecs2TimeSpec(nowNs);
        return Result::OK;
    }

    uint64_t getPresentationFramesLocked(const nsecs_t nowNs) const {
        return uint64_t(mSampleRateHz) * ns2us(nowNs - mStartNs) / 1000000;
    }

    size_t calcAvailableFramesNowLocked() {
        const nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        auto presentationFrames = getPresentationFramesLocked(nowNs);
        if (mReceivedFrames + mMissedFrames < presentationFrames) {
            // There has been an underrun
            mMissedFrames = presentationFrames - mReceivedFrames;
        }
        size_t pendingFrames = mReceivedFrames + mMissedFrames - presentationFrames;
        return sizeof(mWriteBuffer) / mFrameSize - pendingFrames;
    }

    size_t calcWaitFramesNowLocked(const size_t requestedFrames) {
        const size_t availableFrames = calcAvailableFramesNowLocked();
        return (requestedFrames > availableFrames)
            ? (requestedFrames - availableFrames) : 0;
    }

    size_t write(float volume, size_t bytesToWrite, IReader &reader) override {
        (void)volume;
        const AutoMutex lock(mFrameCountersMutex);

        const size_t waitFrames = calcWaitFramesNowLocked(bytesToWrite / mFrameSize);
        const auto blockUntil =
            std::chrono::high_resolution_clock::now() +
                + std::chrono::microseconds(waitFrames * 1000000 / mSampleRateHz);
        std::this_thread::sleep_until(blockUntil);

        while (bytesToWrite > 0) {
            size_t chunkSize =
                std::min(bytesToWrite, sizeof(mWriteBuffer)) / mFrameSize * mFrameSize;
            chunkSize = reader(mWriteBuffer, chunkSize);
            if (chunkSize > 0) {
                mReceivedFrames += chunkSize / mFrameSize;
                bytesToWrite -= chunkSize;
            } else {
                break; // reader failed
            }
        }

        return 0;
    }

    static std::unique_ptr<NullSink> create(const AudioConfig &cfg,
                                            size_t readerBufferSizeHint,
                                            uint64_t initialFrames) {
        (void)readerBufferSizeHint;
        return std::make_unique<NullSink>(cfg, initialFrames);
    }

private:
    const nsecs_t mStartNs;
    const unsigned mSampleRateHz;
    const unsigned mFrameSize;
    const uint64_t mInitialFrames;
    uint64_t mFrames GUARDED_BY(mFrameCountersMutex);
    uint64_t mMissedFrames GUARDED_BY(mFrameCountersMutex) = 0;
    uint64_t mReceivedFrames GUARDED_BY(mFrameCountersMutex) = 0;
    char mWriteBuffer[1024];
    mutable Mutex mFrameCountersMutex;
};

}  // namespace

std::unique_ptr<DevicePortSink>
DevicePortSink::create(size_t readerBufferSizeHint,
                       const DeviceAddress &address,
                       const AudioConfig &cfg,
                       const hidl_vec<AudioInOutFlag> &flags,
                       uint64_t initialFrames) {
    (void)flags;

    if (xsd::stringToAudioFormat(cfg.base.format) != xsd::AudioFormat::AUDIO_FORMAT_PCM_16_BIT) {
        ALOGE("%s:%d, unexpected format: '%s'", __func__, __LINE__, cfg.base.format.c_str());
        return FAILURE(nullptr);
    }

    if (GetBoolProperty("ro.boot.audio.tinyalsa.ignore_output", false)) {
        goto nullsink;
    }

    switch (xsd::stringToAudioDevice(address.deviceType)) {
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_DEFAULT:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_SPEAKER:
        {
            auto sinkptr = TinyalsaSink::create(talsa::kPcmCard, talsa::kPcmDevice,
                                                cfg, readerBufferSizeHint, initialFrames);
            if (sinkptr != nullptr) {
                return sinkptr;
            } else {
                ALOGW("%s:%d failed to create alsa sink for '%s'; creating NullSink instead.",
                      __func__, __LINE__, address.deviceType.c_str());
            }
        }
        break;

    case xsd::AudioDevice::AUDIO_DEVICE_OUT_TELEPHONY_TX:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_BUS:
        ALOGW("%s:%d creating NullSink for '%s'.", __func__, __LINE__, address.deviceType.c_str());
        break;

    default:
        ALOGW("%s:%d unsupported device: '%s', creating NullSink", __func__, __LINE__, address.deviceType.c_str());
        break;
    }

nullsink:
    return NullSink::create(cfg, readerBufferSizeHint, initialFrames);
}

int DevicePortSink::getLatencyMs(const DeviceAddress &address, const AudioConfig &cfg) {
    switch (xsd::stringToAudioDevice(address.deviceType)) {
    default:
        ALOGW("%s:%d unsupported device: '%s'", __func__, __LINE__, address.deviceType.c_str());
        return FAILURE(-1);

    case xsd::AudioDevice::AUDIO_DEVICE_OUT_DEFAULT:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_SPEAKER:
        return TinyalsaSink::getLatencyMs(cfg);

    case xsd::AudioDevice::AUDIO_DEVICE_OUT_TELEPHONY_TX:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_BUS:
        return NullSink::getLatencyMs(cfg);
    }
}

bool DevicePortSink::validateDeviceAddress(const DeviceAddress& address) {
    switch (xsd::stringToAudioDevice(address.deviceType)) {
    default:
        ALOGW("%s:%d unsupported device: '%s'", __func__, __LINE__, address.deviceType.c_str());
        return FAILURE(false);

    case xsd::AudioDevice::AUDIO_DEVICE_OUT_DEFAULT:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_SPEAKER:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_TELEPHONY_TX:
    case xsd::AudioDevice::AUDIO_DEVICE_OUT_BUS:
        break;
    }

    return true;
}

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
