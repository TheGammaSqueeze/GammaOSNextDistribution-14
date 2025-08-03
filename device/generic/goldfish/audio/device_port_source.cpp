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

#include <android-base/properties.h>
#include <cmath>
#include <chrono>
#include <thread>
#include <unistd.h>
#include <audio_utils/channels.h>
#include <audio_utils/format.h>
#include <log/log.h>
#include <utils/Mutex.h>
#include <utils/ThreadDefs.h>
#include <utils/Timers.h>
#include PATH(APM_XSD_ENUMS_H_FILENAME)
#include "device_port_source.h"
#include "talsa.h"
#include "ring_buffer.h"
#include "audio_ops.h"
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

struct TinyalsaSource : public DevicePortSource {
    TinyalsaSource(unsigned pcmCard, unsigned pcmDevice,
                   const AudioConfig &cfg, uint64_t &frames)
            : mStartNs(systemTime(SYSTEM_TIME_MONOTONIC))
            , mSampleRateHz(cfg.base.sampleRateHz)
            , mFrameSize(util::countChannels(cfg.base.channelMask) * sizeof(int16_t))
            , mReadSizeFrames(cfg.frameCount)
            , mFrames(frames)
            , mRingBuffer(mFrameSize * cfg.frameCount * 3)
            , mMixer(pcmCard)
            , mPcm(talsa::pcmOpen(pcmCard, pcmDevice,
                                  util::countChannels(cfg.base.channelMask),
                                  cfg.base.sampleRateHz,
                                  cfg.frameCount,
                                  false /* isOut */)) {
        if (mPcm) {
            mProduceThread = std::thread(&TinyalsaSource::producerThread, this);
        } else {
            mProduceThread = std::thread([](){});
        }
    }

    ~TinyalsaSource() {
        mProduceThreadRunning = false;
        mProduceThread.join();
    }

    Result getCapturePosition(uint64_t &frames, uint64_t &time) override {
        const AutoMutex lock(mFrameCountersMutex);

        const nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        const uint64_t nowFrames = getCaptureFramesLocked(nowNs);
        mFrames += (nowFrames - mPreviousFrames);
        mPreviousFrames = nowFrames;

        frames = mFrames;
        time = nowNs;
        return Result::OK;
    }

    uint64_t getCaptureFramesLocked(const nsecs_t nowNs) const {
        return uint64_t(mSampleRateHz) * ns2us(nowNs - mStartNs) / 1000000;
    }

    uint64_t getAvailableFramesLocked(const nsecs_t nowNs) const {
        return getCaptureFramesLocked(nowNs) - mSentFrames;
    }

    uint64_t getAvailableFramesNowLocked() const {
        return getAvailableFramesLocked(systemTime(SYSTEM_TIME_MONOTONIC));
    }

    size_t getWaitFramesNowLocked(const size_t requestedFrames) const {
        const size_t availableFrames = getAvailableFramesNowLocked();
        return (requestedFrames > availableFrames)
            ? (requestedFrames - availableFrames) : 0;
    }

    size_t read(float volume, size_t bytesToRead, IWriter &writer) override {
        const AutoMutex lock(mFrameCountersMutex);

        const size_t waitFrames = getWaitFramesNowLocked(bytesToRead / mFrameSize);
        const auto blockUntil =
            std::chrono::high_resolution_clock::now() +
                + std::chrono::microseconds(waitFrames * 1000000 / mSampleRateHz);

        while (bytesToRead > 0) {
            if (mRingBuffer.waitForConsumeAvailable(blockUntil
                    + std::chrono::microseconds(kMaxJitterUs))) {
                if (mRingBuffer.availableToConsume() >= bytesToRead) {
                    // Since the ring buffer has all bytes we need, make sure we
                    // are not too early here: tinyalsa is jittery, we don't
                    // want to go faster than SYSTEM_TIME_MONOTONIC
                    std::this_thread::sleep_until(blockUntil);
                }

                auto chunk = mRingBuffer.getConsumeChunk();
                const size_t writeBufSzBytes = std::min(chunk.size, bytesToRead);

                aops::multiplyByVolume(volume,
                                       static_cast<int16_t *>(chunk.data),
                                       writeBufSzBytes / sizeof(int16_t));

                writer(chunk.data, writeBufSzBytes);
                LOG_ALWAYS_FATAL_IF(mRingBuffer.consume(chunk, writeBufSzBytes) < writeBufSzBytes);

                bytesToRead -= writeBufSzBytes;
                mSentFrames += writeBufSzBytes / mFrameSize;
            } else {
                ALOGD("TinyalsaSource::%s:%d pcm_readi was late delivering "
                      "frames, inserting %zu us of silence",
                      __func__, __LINE__,
                      size_t(1000000 * bytesToRead / mFrameSize / mSampleRateHz));

                static const uint8_t zeroes[256] = {0};

                while (bytesToRead > 0) {
                    const size_t nZeroFrames =
                        std::min(bytesToRead, sizeof(zeroes)) / mFrameSize;
                    const size_t nZeroBytes = nZeroFrames * mFrameSize;

                    writer(zeroes, nZeroBytes);
                    bytesToRead -= nZeroBytes;
                    mSentFrames += nZeroFrames;
                }
                break;
            }
        }

        return mFramesLost.exchange(0);
    }

    void producerThread() {
        util::setThreadPriority(SP_AUDIO_SYS, PRIORITY_AUDIO);
        std::vector<uint8_t> readBuf(mReadSizeFrames * mFrameSize);

        while (mProduceThreadRunning) {
            const size_t bytesLost = mRingBuffer.makeRoomForProduce(readBuf.size());
            mFramesLost += bytesLost / mFrameSize;

            auto produceChunk = mRingBuffer.getProduceChunk();
            if (produceChunk.size < readBuf.size()) {
                const size_t sz = doRead(readBuf.data(), readBuf.size());
                if (sz > 0) {
                    LOG_ALWAYS_FATAL_IF(mRingBuffer.produce(readBuf.data(), sz) < sz);
                }
            } else {
                const size_t sz = doRead(produceChunk.data, readBuf.size());
                if (sz > 0) {
                    LOG_ALWAYS_FATAL_IF(mRingBuffer.produce(readBuf.size()) < sz);
                }
            }
        }
    }

    size_t doRead(void *dst, size_t sz) {
        const int n = talsa::pcmRead(mPcm.get(), dst, sz, mFrameSize);
        if (n > 0) {
            LOG_ALWAYS_FATAL_IF(static_cast<size_t>(n) > sz,
                                "n=%d sz=%zu mFrameSize=%u", n, sz, mFrameSize);
            return n;
        } else {
            return 0;
        }
    }

    static std::unique_ptr<TinyalsaSource> create(unsigned pcmCard,
                                                  unsigned pcmDevice,
                                                  const AudioConfig &cfg,
                                                  size_t writerBufferSizeHint,
                                                  uint64_t &frames) {
        (void)writerBufferSizeHint;

        auto src = std::make_unique<TinyalsaSource>(pcmCard, pcmDevice,
                                                    cfg, frames);
        if (src->mMixer && src->mPcm) {
            return src;
        } else {
            return FAILURE(nullptr);
        }
    }

private:
    const nsecs_t mStartNs;
    const unsigned mSampleRateHz;
    const unsigned mFrameSize;
    const unsigned mReadSizeFrames;
    uint64_t &mFrames GUARDED_BY(mFrameCountersMutex);
    uint64_t mPreviousFrames GUARDED_BY(mFrameCountersMutex) = 0;
    uint64_t mSentFrames GUARDED_BY(mFrameCountersMutex) = 0;
    std::atomic<uint32_t> mFramesLost = 0;
    RingBuffer mRingBuffer;
    talsa::Mixer mMixer;
    talsa::PcmPtr mPcm;
    std::thread mProduceThread;
    std::atomic<bool> mProduceThreadRunning = true;
    mutable Mutex mFrameCountersMutex;
};

template <class G> struct GeneratedSource : public DevicePortSource {
    GeneratedSource(const AudioConfig &cfg,
                    size_t writerBufferSizeHint,
                    uint64_t &frames,
                    G generator)
            : mWriteBuffer(writerBufferSizeHint / sizeof(int16_t))
            , mFrames(frames)
            , mStartNs(systemTime(SYSTEM_TIME_MONOTONIC))
            , mSampleRateHz(cfg.base.sampleRateHz)
            , mNChannels(util::countChannels(cfg.base.channelMask))
            , mGenerator(std::move(generator)) {}

    Result getCapturePosition(uint64_t &frames, uint64_t &time) override {
        const AutoMutex lock(mFrameCountersMutex);

        const nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
        const uint64_t nowFrames = getCaptureFramesLocked(nowNs);
        mFrames += (nowFrames - mPreviousFrames);
        mPreviousFrames = nowFrames;
        frames = mFrames;
        time = nowNs;
        return Result::OK;
    }

    uint64_t getCaptureFramesLocked(const nsecs_t nowNs) const {
        return uint64_t(mSampleRateHz) * ns2us(nowNs - mStartNs) / 1000000;
    }

    uint64_t getAvailableFramesLocked(const nsecs_t nowNs) const {
        return getCaptureFramesLocked(nowNs) - mSentFrames;
    }

    size_t read(float volume, size_t bytesToRead, IWriter &writer) override {
        const AutoMutex lock(mFrameCountersMutex);
        mWriteBuffer.resize(bytesToRead / sizeof(int16_t));

        int16_t *samples = mWriteBuffer.data();
        const unsigned nChannels = mNChannels;
        const unsigned requestedFrames = bytesToRead / nChannels / sizeof(*samples);

        unsigned availableFrames;
        while (true) {
            const nsecs_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
            availableFrames = getAvailableFramesLocked(nowNs);
            if (availableFrames < requestedFrames / 2) {
                const unsigned neededMoreFrames = requestedFrames / 2 - availableFrames;

                using namespace std::chrono_literals;
                std::this_thread::sleep_for(1s * neededMoreFrames / mSampleRateHz);
            } else {
                break;
            }
        }

        const unsigned nFrames = std::min(requestedFrames, availableFrames);
        mGenerator(samples, nFrames);
        const size_t nSamples = nFrames * nChannels;
        if (nChannels > 1) {
            adjust_channels(samples, 1, samples, nChannels,
                            sizeof(*samples), nFrames * sizeof(*samples));
        }

        aops::multiplyByVolume(volume,
                               mWriteBuffer.data(),
                               nSamples);

        writer(mWriteBuffer.data(), nSamples * sizeof(*samples));
        mSentFrames += nFrames;

        return 0;
    }

private:
    std::vector<int16_t> mWriteBuffer;
    uint64_t &mFrames GUARDED_BY(mFrameCountersMutex);
    const nsecs_t mStartNs;
    const unsigned mSampleRateHz;
    const unsigned mNChannels;
    uint64_t mPreviousFrames GUARDED_BY(mFrameCountersMutex) = 0;
    uint64_t mSentFrames GUARDED_BY(mFrameCountersMutex) = 0;
    G mGenerator;
    mutable Mutex mFrameCountersMutex;
};

std::vector<int16_t> convertFloatsToInt16(const std::vector<float> &pcmFloat) {
    std::vector<int16_t> pcmI16(pcmFloat.size());

    memcpy_by_audio_format(pcmI16.data(),   AUDIO_FORMAT_PCM_16_BIT,
                           pcmFloat.data(), AUDIO_FORMAT_PCM_FLOAT,
                           pcmFloat.size());

    return pcmI16;
}

// https://en.wikipedia.org/wiki/Busy_signal
struct BusySignalGenerator {
    explicit BusySignalGenerator(const uint32_t sampleRateHz) : mSampleRateHz(sampleRateHz) {
        // 24/480 = 31/620, mValues must contain 50ms of audio samples
        const size_t sz = sampleRateHz / 20;
        std::vector<float> pcm(sz);
        for (unsigned i = 0; i < sz; ++i) {
            const double a = double(i) * M_PI * 2 / sampleRateHz;
            pcm[i] = .5 * (sin(480 * a) + sin(620 * a));
        }
        mValues = convertFloatsToInt16(pcm);
    }

    void operator()(int16_t* s, size_t n) {
        const unsigned rate = mSampleRateHz;
        const unsigned rateHalf = rate / 2;
        const int16_t *const vals = mValues.data();
        const size_t valsSz = mValues.size();
        size_t i = mI;

        while (n > 0) {
            size_t len;
            if (i < rateHalf) {
                const size_t valsOff = i % valsSz;
                len = std::min(n, std::min(rateHalf - i, valsSz - valsOff));
                memcpy(s, vals + valsOff, len * sizeof(*s));
            } else {
                len = std::min(n, rate - i);
                memset(s, 0, len * sizeof(*s));
            }
            s += len;
            i = (i + len) % rate;
            n -= len;
        }

        mI = i;
    }

private:
    const unsigned mSampleRateHz;
    std::vector<int16_t> mValues;
    size_t mI = 0;
};

struct RepeatGenerator {
    explicit RepeatGenerator(const std::vector<float> &pcm)
            : mValues(convertFloatsToInt16(pcm)) {}

    void operator()(int16_t* s, size_t n) {
        const int16_t *const vals = mValues.data();
        const size_t valsSz = mValues.size();
        size_t i = mI;

        while (n > 0) {
            const size_t len = std::min(n, valsSz - i);
            memcpy(s, vals + i, len * sizeof(*s));
            s += len;
            i = (i + len) % valsSz;
            n -= len;
        }

        mI = i;
    }

private:
    const std::vector<int16_t> mValues;
    size_t mI = 0;
};

std::vector<float> generateSinePattern(uint32_t sampleRateHz,
                                       double freq,
                                       double amp) {
    std::vector<float> result(3 * sampleRateHz / freq + .5);

    for (size_t i = 0; i < result.size(); ++i) {
        const double a = double(i) * M_PI * 2 / sampleRateHz;
        result[i] = amp * sin(a * freq);
    }

    return result;
}

template <class G> std::unique_ptr<GeneratedSource<G>>
createGeneratedSource(const AudioConfig &cfg,
                      size_t writerBufferSizeHint,
                      uint64_t &frames,
                      G generator) {
    return std::make_unique<GeneratedSource<G>>(cfg,
                                                writerBufferSizeHint,
                                                frames,
                                                std::move(generator));
}

}  // namespace

std::unique_ptr<DevicePortSource>
DevicePortSource::create(size_t writerBufferSizeHint,
                         const DeviceAddress &address,
                         const AudioConfig &cfg,
                         const hidl_vec<AudioInOutFlag> &flags,
                         uint64_t &frames) {
    (void)flags;

    if (xsd::stringToAudioFormat(cfg.base.format) != xsd::AudioFormat::AUDIO_FORMAT_PCM_16_BIT) {
        ALOGE("%s:%d, unexpected format: '%s'", __func__, __LINE__, cfg.base.format.c_str());
        return FAILURE(nullptr);
    }

    switch (xsd::stringToAudioDevice(address.deviceType)) {
    case xsd::AudioDevice::AUDIO_DEVICE_IN_DEFAULT:
    case xsd::AudioDevice::AUDIO_DEVICE_IN_BUILTIN_MIC:
        if (GetBoolProperty("ro.boot.audio.tinyalsa.simulate_input", false)) {
            return createGeneratedSource(
                cfg, writerBufferSizeHint, frames,
                RepeatGenerator(generateSinePattern(cfg.base.sampleRateHz, 300.0, 1.0)));
        } else {
            auto sourceptr = TinyalsaSource::create(talsa::kPcmCard, talsa::kPcmDevice,
                                                    cfg, writerBufferSizeHint, frames);
            if (sourceptr != nullptr) {
                return sourceptr;
            } else {
                ALOGW("%s:%d failed to create alsa source for '%s'; creating a tone source instead.",
                      __func__, __LINE__, address.deviceType.c_str());
            }
        }
        break;

    case xsd::AudioDevice::AUDIO_DEVICE_IN_TELEPHONY_RX:
        return createGeneratedSource(cfg, writerBufferSizeHint, frames,
                                     BusySignalGenerator(cfg.base.sampleRateHz));

    case xsd::AudioDevice::AUDIO_DEVICE_IN_FM_TUNER:
        return createGeneratedSource(
            cfg, writerBufferSizeHint, frames,
            RepeatGenerator(generateSinePattern(cfg.base.sampleRateHz, 440.0, 1.0)));

    default:
        ALOGW("%s:%d unsupported device: '%s', creating a tone source",
              __func__, __LINE__, address.deviceType.c_str());
        break;
    }

    return createGeneratedSource(
        cfg, writerBufferSizeHint, frames,
        RepeatGenerator(generateSinePattern(cfg.base.sampleRateHz, 220.0, 1.0)));
}

bool DevicePortSource::validateDeviceAddress(const DeviceAddress& address) {
    switch (xsd::stringToAudioDevice(address.deviceType)) {
    default:
        ALOGW("%s:%d unsupported device: '%s'", __func__, __LINE__, address.deviceType.c_str());
        return FAILURE(false);

    case xsd::AudioDevice::AUDIO_DEVICE_IN_DEFAULT:
    case xsd::AudioDevice::AUDIO_DEVICE_IN_BUILTIN_MIC:
    case xsd::AudioDevice::AUDIO_DEVICE_IN_TELEPHONY_RX:
    case xsd::AudioDevice::AUDIO_DEVICE_IN_FM_TUNER:
    case xsd::AudioDevice::AUDIO_DEVICE_IN_BUS:
        break;
    }

    return true;
}

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
