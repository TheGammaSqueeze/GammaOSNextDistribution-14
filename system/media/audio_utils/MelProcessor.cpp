/*
 * Copyright 2022 The Android Open Source Project
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

// #define LOG_NDEBUG 0
#define LOG_TAG "audio_utils_MelProcessor"
// #define VERY_VERY_VERBOSE_LOGGING
#ifdef VERY_VERY_VERBOSE_LOGGING
#define ALOGVV ALOGV
#else
#define ALOGVV(a...) do { } while(0)
#endif

#include <audio_utils/MelProcessor.h>

#include <audio_utils/format.h>
#include <audio_utils/power.h>
#include <log/log.h>
#include <sstream>
#include <unordered_map>
#include <utils/threads.h>

namespace android::audio_utils {

constexpr int32_t kSecondsPerMelValue = 1;
constexpr float kMelAdjustmentDb = -3.f;

// Estimated offset defined in Table39 of IEC62368-1 3rd edition
// -30dBFS, -10dBFS should correspond to 80dBSPL, 100dBSPL
constexpr float kMeldBFSTodBSPLOffset = 110.f;

constexpr float kRs1OutputdBFS = 80.f;  // dBA

constexpr float kRs2LowerBound = 80.f;  // dBA
constexpr float kRs2UpperBound = 100.f;  // dBA

// The following arrays contain the IIR biquad filter coefficients for performing A-weighting as
// described in IEC 61672:2003 for multiple sample rates. The format is b0, b1, b2, a1, a2
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs8000 = {{{0.630301, 0.000000, -0.630301, 0.103818, -0.360417},
                      {1.000000, 0.000000, -1.000000, -0.264382, -0.601403},
                      {1.000000, -2.000000, 1.000000, -1.967903, 0.968160}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs11025 = {{{0.601164, 1.202327, 0.601164, 1.106098, 0.305863},
                       {1.000000, -2.000000, 1.000000, -1.593019, 0.613701},
                       {1.000000, -2.000000, 1.000000, -1.976658, 0.976794}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs12000 = {{{0.588344, 1.176688, 0.588344, 1.045901, 0.273477},
                       {1.000000, -2.000000, 1.000000, -1.621383, 0.639134},
                       {1.000000, -2.000000, 1.000000, -1.978544, 0.978660}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs16000 = {{{0.531220, 1.062441, 0.531220, 0.821564, 0.168742},
                       {1.000000, -2.000000, 1.000000, -1.705510, 0.715988},
                       {1.000000, -2.000000, 1.000000, -1.983887, 0.983952}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs22050 = {{{0.449072, 0.898144, 0.449072, 0.538750, 0.072563},
                       {1.000000, -2.000000, 1.000000, -1.779533, 0.785281},
                       {1.000000, -2.000000, 1.000000, -1.988295, 0.988329}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs24000 = {{{0.425411, 0.850821, 0.425411, 0.459298, 0.052739},
                       {1.000000, -2.000000, 1.000000, -1.796051, 0.800946},
                       {1.000000, -2.000000, 1.000000, -1.989243, 0.989272}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs32000 = {{{0.343284, 0.686569, 0.343284, 0.179472, 0.008053},
                       {1.000000, -2.000000, 1.000000, -1.843991, 0.846816},
                       {1.000000, -2.000000, 1.000000, -1.991927, 0.991943}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs44100 = {{{0.255612, 0.511223, 0.255612, -0.140536, 0.004938},
                       {1.000000, -2.000000, 1.000000, -1.884901, 0.886421},
                       {1.000000, -2.000000, 1.000000, -1.994139, 0.994147}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs48000 = {{{0.234183, 0.468366, 0.234183, -0.224558, 0.012607},
                       {1.000000, -2.000000, 1.000000, -1.893870, 0.895160},
                       {1.000000, -2.000000, 1.000000, -1.994614, 0.994622}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs64000 = {{{0.169014, 0.338029, 0.169014, -0.502217, 0.063056},
                       {1.000000, -2.000000, 1.000000, -1.919579, 0.920314},
                       {1.000000, -2.000000, 1.000000, -1.995959, 0.995964}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs88200 = {{{0.111831, 0.223662, 0.111831, -0.788729, 0.155523},
                       {1.000000, -2.000000, 1.000000, -1.941143, 0.941534},
                       {1.000000, -2.000000, 1.000000, -1.997067, 0.997069}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs96000 = {{{0.099469, 0.198937, 0.099469, -0.859073, 0.184502},
                       {1.000000, -2.000000, 1.000000, -1.945825, 0.946156},
                       {1.000000, -2.000000, 1.000000, -1.997305, 0.997307}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs128000 = {{{0.065337, 0.130674, 0.065337, -1.078602, 0.290845},
                        {1.000000, -2.000000, 1.000000, -1.959154, 0.959342},
                        {1.000000, -2.000000, 1.000000, -1.997979, 0.997980}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs176400 = {{{0.039432, 0.078864, 0.039432, -1.286304, 0.413645},
                        {1.000000, -2.000000, 1.000000, -1.970232, 0.970331},
                        {1.000000, -2.000000, 1.000000, -1.998533, 0.998534}}};
constexpr std::array<std::array<float, kBiquadNumCoefs>, MelProcessor::kCascadeBiquadNumber>
    kBqCoeffs192000 = {{{0.034315, 0.068629, 0.034315, -1.334647, 0.445320},
                        {1.000000, -2.000000, 1.000000, -1.972625, 0.972709},
                        {1.000000, -2.000000, 1.000000, -1.998652, 0.998653}}};

MelProcessor::MelProcessor(uint32_t sampleRate,
        uint32_t channelCount,
        audio_format_t format,
        const sp<MelCallback>& callback,
        audio_port_handle_t deviceId,
        float rs2Value,
        size_t maxMelsCallback)
    : mCallback(callback),
      mMelWorker("MelWorker#" + pointerString(), mCallback),
      mSampleRate(sampleRate),
      mFramesPerMelValue(sampleRate * kSecondsPerMelValue),
      mChannelCount(channelCount),
      mFormat(format),
      mAWeightSamples(mFramesPerMelValue * mChannelCount),
      mFloatSamples(mFramesPerMelValue * mChannelCount),
      mCurrentChannelEnergy(channelCount, 0.0f),
      mMelValues(maxMelsCallback),
      mCurrentIndex(0),
      mDeviceId(deviceId),
      mRs2UpperBound(rs2Value),
      mCurrentSamples(0)
{
    createBiquads_l();

    mMelWorker.run();
}

static const std::unordered_map<uint32_t, const std::array<std::array<float, kBiquadNumCoefs>,
        MelProcessor::kCascadeBiquadNumber>*>& getSampleRateBiquadCoeffs() {
    static const std::unordered_map<uint32_t, const std::array<std::array<float, kBiquadNumCoefs>,
                             MelProcessor::kCascadeBiquadNumber>*> sampleRateBiquadCoeffs = {
            {8000, &kBqCoeffs8000},
            {11025, &kBqCoeffs11025},
            {12000, &kBqCoeffs12000},
            {16000, &kBqCoeffs16000},
            {22050, &kBqCoeffs22050},
            {24000, &kBqCoeffs24000},
            {32000, &kBqCoeffs32000},
            {44100, &kBqCoeffs44100},
            {48000, &kBqCoeffs48000},
            {64000, &kBqCoeffs64000},
            {88200, &kBqCoeffs88200},
            {96000, &kBqCoeffs96000},
            {128000, &kBqCoeffs128000},
            {176400, &kBqCoeffs176400},
            {192000, &kBqCoeffs192000},
        };
    return sampleRateBiquadCoeffs;
}

bool MelProcessor::isSampleRateSupported_l() const {
    return getSampleRateBiquadCoeffs().count(mSampleRate) != 0;
}

void MelProcessor::createBiquads_l() {
    if (!isSampleRateSupported_l()) {
        return;
    }

    const auto& biquadCoeffs = getSampleRateBiquadCoeffs().at(mSampleRate); // checked above
    mCascadedBiquads =
              {std::make_unique<DefaultBiquadFilter>(mChannelCount, biquadCoeffs->at(0)),
               std::make_unique<DefaultBiquadFilter>(mChannelCount, biquadCoeffs->at(1)),
               std::make_unique<DefaultBiquadFilter>(mChannelCount, biquadCoeffs->at(2))};
}

status_t MelProcessor::setOutputRs2UpperBound(float rs2Value)
{
    if (rs2Value < kRs2LowerBound || rs2Value > kRs2UpperBound) {
        return BAD_VALUE;
    }

    mRs2UpperBound = rs2Value;

    return NO_ERROR;
}

float MelProcessor::getOutputRs2UpperBound() const
{
    return mRs2UpperBound;
}

void MelProcessor::setDeviceId(audio_port_handle_t deviceId)
{
    mDeviceId = deviceId;
}

audio_port_handle_t MelProcessor::getDeviceId() {
    return mDeviceId;
}

void MelProcessor::pause()
{
    ALOGV("%s", __func__);
    mPaused = true;
}

void MelProcessor::resume()
{
    ALOGV("%s", __func__);
    mPaused = false;
}

void MelProcessor::updateAudioFormat(uint32_t sampleRate,
                                     uint32_t channelCount,
                                     audio_format_t format) {
    ALOGV("%s: update audio format %u, %u, %d", __func__, sampleRate, channelCount, format);

    std::lock_guard l(mLock);

    bool differentSampleRate = (mSampleRate != sampleRate);
    bool differentChannelCount = (mChannelCount != channelCount);

    mSampleRate = sampleRate;
    mFramesPerMelValue = sampleRate * kSecondsPerMelValue;
    mChannelCount = channelCount;
    mFormat = format;

    if (differentSampleRate || differentChannelCount) {
        mAWeightSamples.resize(mFramesPerMelValue * mChannelCount);
        mFloatSamples.resize(mFramesPerMelValue * mChannelCount);
    }
    if (differentChannelCount) {
        mCurrentChannelEnergy.resize(channelCount);
    }

    createBiquads_l();
}

void MelProcessor::applyAWeight_l(const void* buffer, size_t samples)
{
    memcpy_by_audio_format(mFloatSamples.data(), AUDIO_FORMAT_PCM_FLOAT, buffer, mFormat, samples);

    float* tempFloat[2] = { mFloatSamples.data(), mAWeightSamples.data() };
    int inIdx = 1, outIdx = 0;
    const size_t frames = samples / mChannelCount;
    for (const auto& biquad : mCascadedBiquads) {
        outIdx ^= 1;
        inIdx ^= 1;
        biquad->process(tempFloat[outIdx], tempFloat[inIdx], frames);
    }

    // should not be the case since the size is odd
    if (!(mCascadedBiquads.size() & 1)) {
        std::swap(mFloatSamples, mAWeightSamples);
    }
}

float MelProcessor::getCombinedChannelEnergy_l() {
    float combinedEnergy = 0.0f;
    for (auto& energy: mCurrentChannelEnergy) {
        combinedEnergy += energy;
        energy = 0;
    }

    combinedEnergy /= (float) mFramesPerMelValue;
    return combinedEnergy;
}

void MelProcessor::addMelValue_l(float mel) {
    mMelValues[mCurrentIndex] = mel;
    ALOGV("%s: writing MEL %f at index %d for device %d",
          __func__,
          mel,
          mCurrentIndex,
          mDeviceId.load());

    bool notifyWorker = false;

    if (mel > mRs2UpperBound) {
        mMelWorker.momentaryExposure(mel, mDeviceId);
        notifyWorker = true;
    }

    bool reportContinuousValues = false;
    if ((mMelValues[mCurrentIndex] < kRs1OutputdBFS && mCurrentIndex > 0)) {
        reportContinuousValues = true;
    } else if (mMelValues[mCurrentIndex] >= kRs1OutputdBFS) {
        // only store MEL that are above RS1
        ++mCurrentIndex;
    }

    if (reportContinuousValues || (mCurrentIndex > mMelValues.size() - 1)) {
        mMelWorker.newMelValues(mMelValues, mCurrentIndex, mDeviceId);
        notifyWorker = true;
        mCurrentIndex = 0;
    }

    if (notifyWorker) {
        mMelWorker.mCondVar.notify_one();
    }
}

int32_t MelProcessor::process(const void* buffer, size_t bytes) {
    if (mPaused) {
        return 0;
    }

    // should be uncontested and not block if process method is called from a single thread
    std::lock_guard<std::mutex> guard(mLock);

    if (!isSampleRateSupported_l()) {
        return 0;
    }

    const size_t bytes_per_sample = audio_bytes_per_sample(mFormat);
    size_t samples = bytes_per_sample > 0 ? bytes / bytes_per_sample : 0;
    while (samples > 0) {
        const size_t requiredSamples =
            mFramesPerMelValue * mChannelCount - mCurrentSamples;
        size_t processSamples = std::min(requiredSamples, samples);
        processSamples -= processSamples % mChannelCount;

        applyAWeight_l(buffer, processSamples);

        audio_utils_accumulate_energy(mAWeightSamples.data(),
                                      AUDIO_FORMAT_PCM_FLOAT,
                                      processSamples,
                                      mChannelCount,
                                      mCurrentChannelEnergy.data());
        mCurrentSamples += processSamples;

        ALOGVV(
            "required:%zu, process:%zu, mCurrentChannelEnergy[0]:%f, mCurrentSamples:%zu",
            requiredSamples,
            processSamples,
            mCurrentChannelEnergy[0],
            mCurrentSamples.load());
        if (processSamples < requiredSamples) {
            return static_cast<int32_t>(bytes);
        }

        addMelValue_l(fmaxf(
            audio_utils_power_from_energy(getCombinedChannelEnergy_l())
                + kMelAdjustmentDb
                + kMeldBFSTodBSPLOffset
                - mAttenuationDB, 0.0f));

        samples -= processSamples;
        buffer =
            (const uint8_t*) buffer + processSamples * bytes_per_sample;
        mCurrentSamples = 0;
    }

    return static_cast<int32_t>(bytes);
}

void MelProcessor::setAttenuation(float attenuationDB) {
    ALOGV("%s: setting the attenuation %f", __func__, attenuationDB);
    mAttenuationDB = attenuationDB;
}

void MelProcessor::onLastStrongRef(const void* id __attribute__((unused))) {
   mMelWorker.stop();
   ALOGV("%s: Stopped thread: %s for device %d", __func__, mMelWorker.mThreadName.c_str(),
         mDeviceId.load());
}

std::string MelProcessor::pointerString() const {
    const void * address = static_cast<const void*>(this);
    std::stringstream aStream;
    aStream << address;
    return aStream.str();
}

void MelProcessor::MelWorker::run() {
    mThread = std::thread([&]{
        // name the thread to help identification
        androidSetThreadName(mThreadName.c_str());
        ALOGV("%s::run(): Started thread", mThreadName.c_str());

        while (true) {
            std::unique_lock l(mCondVarMutex);
            if (mStopRequested) {
                return;
            }
            mCondVar.wait(l, [&] { return (mRbReadPtr != mRbWritePtr) || mStopRequested; });

            while (mRbReadPtr != mRbWritePtr && !mStopRequested) {
                ALOGV("%s::run(): new callbacks, rb idx read=%zu, write=%zu",
                      mThreadName.c_str(),
                      mRbReadPtr.load(),
                      mRbWritePtr.load());
                auto callback = mCallback.promote();
                if (callback == nullptr) {
                    ALOGW("%s::run(): MelCallback is null, quitting MelWorker",
                          mThreadName.c_str());
                    return;
                }

                MelCallbackData data = mCallbackRingBuffer[mRbReadPtr];
                if (data.mMel != 0.f) {
                    callback->onMomentaryExposure(data.mMel, data.mPort);
                } else if (data.mMelsSize != 0) {
                    callback->onNewMelValues(data.mMels, 0, data.mMelsSize,
                                             data.mPort, /*attenuated=*/true);
                } else {
                    ALOGE("%s::run(): Invalid MEL data. Skipping callback", mThreadName.c_str());
                }
                incRingBufferIndex(mRbReadPtr);
            }
        }
    });
}

void MelProcessor::MelWorker::stop() {
    bool oldValue;
    {
        std::lock_guard l(mCondVarMutex);
        oldValue = mStopRequested;
        mStopRequested = true;
    }
    if (!oldValue) {
        mCondVar.notify_one();
        mThread.join();
    }
}

void MelProcessor::MelWorker::momentaryExposure(float mel, audio_port_handle_t port) {
    ALOGV("%s", __func__);

    if (ringBufferIsFull()) {
        ALOGW("%s: cannot add momentary exposure for port %d, MelWorker buffer is full", __func__,
              port);
        return;
    }

    // worker is thread-safe, no lock since there is only one writer and we take into account
    // spurious wake-ups
    mCallbackRingBuffer[mRbWritePtr].mMel = mel;
    mCallbackRingBuffer[mRbWritePtr].mMelsSize = 0;
    mCallbackRingBuffer[mRbWritePtr].mPort = port;

    incRingBufferIndex(mRbWritePtr);
}

void MelProcessor::MelWorker::newMelValues(const std::vector<float>& mels,
                                           size_t melsSize,
                                           audio_port_handle_t port) {
    ALOGV("%s", __func__);

    if (ringBufferIsFull()) {
        ALOGW("%s: cannot add %zu mel values for port %d, MelWorker buffer is full", __func__,
              melsSize, port);
        return;
    }

    // worker is thread-safe, no lock since there is only one writer and we take into account
    // spurious wake-ups
    std::copy_n(std::begin(mels), melsSize, mCallbackRingBuffer[mRbWritePtr].mMels.begin());
    mCallbackRingBuffer[mRbWritePtr].mMelsSize = melsSize;
    mCallbackRingBuffer[mRbWritePtr].mMel = 0.f;
    mCallbackRingBuffer[mRbWritePtr].mPort = port;

    incRingBufferIndex(mRbWritePtr);
}

bool MelProcessor::MelWorker::ringBufferIsFull() const {
    size_t curIdx = mRbWritePtr.load();
    size_t nextIdx = curIdx >= kRingBufferSize - 1 ? 0 : curIdx + 1;

    return nextIdx == mRbReadPtr;
}

void MelProcessor::MelWorker::incRingBufferIndex(std::atomic_size_t& idx) {
    size_t nextIdx;
    size_t expected;
    do {
        expected = idx.load();
        nextIdx = expected >= kRingBufferSize - 1 ? 0 : expected + 1;
    } while (!idx.compare_exchange_strong(expected, nextIdx));
}

}   // namespace android
