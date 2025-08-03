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

#pragma once

#include <array>
#include <condition_variable>
#include <mutex>
#include <thread>

#include <android-base/thread_annotations.h>
#include <audio_utils/BiquadFilter.h>
#include <system/audio.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>

namespace android::audio_utils {

/**
 * Class used to capture the MEL (momentary exposure levels) values as defined
 * by IEC62368-1 3rd edition. MELs are computed for each second.
 */
class MelProcessor : public RefBase {
public:

    static constexpr int kCascadeBiquadNumber = 3;
    /** Should represent the minimal value after which a 1% CSD change can occur. */
    static constexpr int32_t kMaxMelValues = 3;

    /**
     * An interface through which the MelProcessor client will be notified about
     * important events.
     */
    class MelCallback : public virtual RefBase {
    public:
        ~MelCallback() override = default;
        /**
         * Called with a time-continuous vector of computed MEL values
         *
         * \param mels     contains MELs (one per second) with values above RS1.
         * \param offset   the offset in mels for new MEL data.
         * \param length   the number of valid MEL values in the vector starting at offset. The
         *                 maximum number of elements in mels is defined in the MelProcessor
         *                 constructor.
         * \param deviceId id of device where the samples were processed
         */
        virtual void onNewMelValues(const std::vector<float>& mels,
                                    size_t offset,
                                    size_t length,
                                    audio_port_handle_t deviceId) const = 0;

        /**
         * Called when the momentary exposure exceeds the RS2 upper bound.
         *
         * Note: RS2 is configurable vie MelProcessor#setOutputRs2UpperBound.
         */
        virtual void onMomentaryExposure(float currentMel, audio_port_handle_t deviceId) const = 0;
    };

    /**
     * \brief Creates a MelProcessor object.
     *
     * \param sampleRate        sample rate of the audio data.
     * \param channelCount      channel count of the audio data.
     * \param format            format of the audio data. It must be allowed by
     *                          audio_utils_is_compute_mel_format_supported()
     *                          else the constructor will abort.
     * \param callback          reports back the new mel values.
     * \param deviceId          the device ID for the MEL callbacks
     * \param rs2Value          initial RS2 upper bound to use
     * \param maxMelsCallback   the number of max elements a callback can have.
     */
    MelProcessor(uint32_t sampleRate,
                 uint32_t channelCount,
                 audio_format_t format,
                 const sp<MelCallback>& callback,
                 audio_port_handle_t deviceId,
                 float rs2Value,
                 size_t maxMelsCallback = kMaxMelValues);

    /**
     * Sets the output RS2 upper bound for momentary exposure warnings. Default value
     * is 100dBA as specified in IEC62368-1 3rd edition. Must not be higher than
     * 100dBA and not lower than 80dBA.
     *
     * \param rs2Value to use for momentary exposure
     * \return NO_ERROR if rs2Value is between 80dBA amd 100dBA or BAD_VALUE
     *   otherwise
     */
    status_t setOutputRs2UpperBound(float rs2Value);

    /** Returns the RS2 upper bound used for momentary exposures. */
    float getOutputRs2UpperBound() const;

    /** Updates the device id. */
    void setDeviceId(audio_port_handle_t deviceId);

    /** Returns the device id. */
    audio_port_handle_t getDeviceId();

    /** Update the format to use for the input frames to process. */
    void updateAudioFormat(uint32_t sampleRate, uint32_t channelCount, audio_format_t newFormat);

    /**
     * \brief Computes the MEL values for the given buffer and triggers a
     * callback with time-continuous MEL values when: MEL buffer is full or if
     * there is a discontinue in MEL calculation (e.g.: MEL is under RS1)
     *
     * \param buffer           pointer to the audio data buffer.
     * \param bytes            buffer size in bytes.
     *
     * \return the number of bytes that were processed. Note: the method will
     *   output 0 if the processor is paused or the sample rate is not supported.
     */
    int32_t process(const void* buffer, size_t bytes);

    /**
     * Pauses the processing of MEL values. Process calls after this will be
     * ignored until resume.
     */
    void pause();

    /** Resumes the processing of MEL values. */
    void resume();

    /**
     * Sets the given attenuation for the MEL calculation. This can be used when
     * the audio framework is operating in absolute volume mode.
     *
     * @param attenuationDB    attenuation to use on computed MEL values
     */
    void setAttenuation(float attenuationDB);

    void onLastStrongRef(const void* id) override;

private:
    /** Struct to store the possible callback data. */
    struct MelCallbackData {
        // used for momentaryExposure callback
        float mMel = 0.f;
        // used for newMelValues callback
        std::vector<float> mMels = std::vector<float>(kMaxMelValues);
        // represents the number of valid MEL values in mMels
        size_t mMelsSize = 0;
        // port of deviceId for this callback
        audio_port_handle_t mPort = AUDIO_PORT_HANDLE_NONE;
    };

    // class used to asynchronously execute all MelProcessor callbacks
    class MelWorker {
    public:
        static constexpr int kRingBufferSize = 32;

        MelWorker(std::string threadName, const wp<MelCallback>& callback)
            : mCallback(callback),
              mThreadName(std::move(threadName)),
              mCallbackRingBuffer(kRingBufferSize) {};

        void run();

        // blocks until the MelWorker thread is stopped
        void stop();

        // callback methods for new MEL values
        void momentaryExposure(float mel, audio_port_handle_t port);
        void newMelValues(const std::vector<float>& mels,
                          size_t melsSize,
                          audio_port_handle_t port);

        static void incRingBufferIndex(std::atomic_size_t& idx);
        bool ringBufferIsFull() const;

        const wp<MelCallback> mCallback;
        const std::string mThreadName;
        std::vector<MelCallbackData> mCallbackRingBuffer GUARDED_BY(mCondVarMutex);

        std::atomic_size_t mRbReadPtr = 0;
        std::atomic_size_t mRbWritePtr = 0;

        std::thread mThread;
        std::condition_variable mCondVar;
        std::mutex mCondVarMutex;
        bool mStopRequested GUARDED_BY(mCondVarMutex) = false;
    };

    std::string pointerString() const;
    void createBiquads_l() REQUIRES(mLock);
    bool isSampleRateSupported_l() const REQUIRES(mLock);
    void applyAWeight_l(const void* buffer, size_t frames) REQUIRES(mLock);
    float getCombinedChannelEnergy_l() REQUIRES(mLock);
    void addMelValue_l(float mel) REQUIRES(mLock);

    const wp<MelCallback> mCallback;           // callback to notify about new MEL values
                                               // and momentary exposure warning
                                               // does not own the callback, must outlive

    MelWorker mMelWorker;                      // spawns thread for asynchronous callbacks,
                                               // worker is thread-safe

    mutable std::mutex mLock;                  // monitor mutex
    // audio data sample rate
    uint32_t mSampleRate GUARDED_BY(mLock);
    // number of audio frames per MEL value
    size_t mFramesPerMelValue GUARDED_BY(mLock);
    // audio data channel count
    uint32_t mChannelCount GUARDED_BY(mLock);
    // audio data format
    audio_format_t mFormat GUARDED_BY(mLock);
    // contains the A-weighted input samples to be processed
    std::vector<float> mAWeightSamples GUARDED_BY(mLock);
    // contains the input samples converted to float
    std::vector<float> mFloatSamples GUARDED_BY(mLock);
    // local energy accumulation
    std::vector<float> mCurrentChannelEnergy GUARDED_BY(mLock);
    // accumulated MEL values
    std::vector<float> mMelValues GUARDED_BY(mLock);
    // current index to store the MEL values
    uint32_t mCurrentIndex GUARDED_BY(mLock);
    using DefaultBiquadFilter = BiquadFilter<float, true, details::DefaultBiquadConstOptions>;
    // Biquads used for the A-weighting
    std::array<std::unique_ptr<DefaultBiquadFilter>, kCascadeBiquadNumber>
        mCascadedBiquads GUARDED_BY(mLock);

    std::atomic<float> mAttenuationDB = 0.f;
    // device id used for the callbacks
    std::atomic<audio_port_handle_t> mDeviceId;
    // Value used for momentary exposure
    std::atomic<float> mRs2UpperBound;
    // number of samples in the energy
    std::atomic_size_t mCurrentSamples;
    std::atomic_bool mPaused;
};

}  // namespace android::audio_utils
