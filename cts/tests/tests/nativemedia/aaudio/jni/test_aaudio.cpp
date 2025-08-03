/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <memory>
#include <tuple>

#include <unistd.h>

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <gtest/gtest.h>

#include "test_aaudio.h"
#include "utils.h"

using StreamTestParams = std::tuple<aaudio_sharing_mode_t, aaudio_performance_mode_t>;
enum {
    PARAM_SHARING_MODE = 0,
    PARAM_PERF_MODE
};

static const int64_t MAX_LATENCY_RANGE = 200 * NANOS_PER_MILLISECOND;
static const int64_t MAX_LATENCY = 800 * NANOS_PER_MILLISECOND;
static const int NUM_TIMESTAMP_QUERY = 3;

static std::string getTestName(const ::testing::TestParamInfo<StreamTestParams>& info) {
    return std::string() + sharingModeToString(std::get<PARAM_SHARING_MODE>(info.param)) +
            "__" + performanceModeToString(std::get<PARAM_PERF_MODE>(info.param));
}

template<typename T>
class AAudioStreamTest : public AAudioCtsBase,
                         public ::testing::WithParamInterface<StreamTestParams> {
  protected:
    AAudioStreamBuilder* builder() const { return mHelper->builder(); }
    AAudioStream* stream() const { return mHelper->stream(); }
    const StreamBuilderHelper::Parameters& actual() const { return mHelper->actual(); }
    int32_t framesPerBurst() const { return mHelper->framesPerBurst(); }

    // This checks for expected behavior after a stream has been released.
    void checkCallsAfterRelease() {
        // We expect these not to crash.
        AAudioStream_setBufferSizeInFrames(stream(), 0);
        AAudioStream_setBufferSizeInFrames(stream(), 99999999);

        // We should NOT be able to start or change a stream after it has been released.
        EXPECT_EQ(AAUDIO_ERROR_INVALID_STATE,
                  AAudioStream_requestStart(stream()));
        EXPECT_EQ(AAUDIO_STREAM_STATE_CLOSING, AAudioStream_getState(stream()));
        // Pause is only implemented for OUTPUT.
        if (AAudioStream_getDirection(stream()) == AAUDIO_DIRECTION_OUTPUT) {
            EXPECT_EQ(AAUDIO_ERROR_INVALID_STATE,
                      AAudioStream_requestPause(stream()));
        }
        EXPECT_EQ(AAUDIO_STREAM_STATE_CLOSING, AAudioStream_getState(stream()));
        EXPECT_EQ(AAUDIO_ERROR_INVALID_STATE,
                  AAudioStream_requestStop(stream()));
        EXPECT_EQ(AAUDIO_STREAM_STATE_CLOSING, AAudioStream_getState(stream()));

        // Do these return positive integers?
        // Frames read or written may be zero if the stream has not had time to advance.
        EXPECT_GE(AAudioStream_getFramesRead(stream()), 0);
        EXPECT_GE(AAudioStream_getFramesWritten(stream()), 0);
        EXPECT_GT(AAudioStream_getFramesPerBurst(stream()), 0);
        EXPECT_GE(AAudioStream_getXRunCount(stream()), 0);
        EXPECT_GT(AAudioStream_getBufferCapacityInFrames(stream()), 0);
        EXPECT_GT(AAudioStream_getBufferSizeInFrames(stream()), 0);

        int64_t timestampFrames = 0;
        int64_t timestampNanos = 0;
        aaudio_result_t result = AAudioStream_getTimestamp(stream(), CLOCK_MONOTONIC,
                                           &timestampFrames, &timestampNanos);
        EXPECT_TRUE(result == AAUDIO_ERROR_INVALID_STATE
                        || result == AAUDIO_ERROR_UNIMPLEMENTED
                        || result == AAUDIO_OK
                        );

        // Verify Closing State. Does this crash?
        aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNKNOWN;
        EXPECT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(stream(),
                                                             AAUDIO_STREAM_STATE_UNKNOWN,
                                                             &state,
                                                             DEFAULT_STATE_TIMEOUT));
        EXPECT_EQ(AAUDIO_STREAM_STATE_CLOSING, state);
    }

    /**
     * @return buffer with correct size for the stream format.
     */
    void *getDataBuffer() {
        aaudio_format_t format = AAudioStream_getFormat(mHelper->stream());
        switch (format) {
            case AAUDIO_FORMAT_PCM_I16:
                return mShortData.get();
            case AAUDIO_FORMAT_PCM_FLOAT:
                return mFloatData.get();
            default:
                // Other code will check for this error condition.
                return nullptr;
        }
    }

    /**
     * Allocate the correct data buffer based on the stream format.
     */
    void allocateDataBuffer(int32_t numFrames) {
        aaudio_format_t format = AAudioStream_getFormat(mHelper->stream());
        switch (format) {
            case AAUDIO_FORMAT_PCM_I16:
                mShortData.reset(new int16_t[numFrames * actual().channelCount]{});
                break;
            case AAUDIO_FORMAT_PCM_FLOAT:
                mFloatData.reset(new float[numFrames * actual().channelCount]{});
                break;
            default:
                // Other code will check for this error condition.
                break;
        }
    }

    int64_t getLatency(const int64_t presentationTime, const int64_t presentationPosition) const {
        const int64_t frameIndex = isOutput() ? AAudioStream_getFramesWritten(stream())
                                              : AAudioStream_getFramesRead(stream());
        const int64_t nowNs = getNanoseconds();
        const int64_t frameIndexDelta = frameIndex - presentationPosition;
        const int64_t frameTimeDelta = (frameIndexDelta * NANOS_PER_SECOND) / actual().sampleRate;
        const int64_t framePresentationTime = presentationTime + frameTimeDelta;
        return isOutput() ? (framePresentationTime - nowNs) : (nowNs - framePresentationTime);
    }

    void testTimestamp(const int64_t timeoutNanos) {
        // Record for 1 seconds to ensure we can get a valid timestamp
        const int32_t frames = actual().sampleRate;
        mHelper->startStream();
        int64_t maxLatencyNanos = 0;
        int64_t minLatencyNanos = NANOS_PER_SECOND;
        int64_t sumLatencyNanos = 0;
        int64_t lastPresentationPosition = -1;
        // Get the maximum and minimum latency within 3 successfully timestamp query.
        for (int i = 0; i < NUM_TIMESTAMP_QUERY; ++i) {
            aaudio_result_t result;
            int maxRetries = 10; // Try 10 times to get timestamp
            int64_t presentationTime = 0;
            int64_t presentationPosition = 0;
            do {
                processData(frames, timeoutNanos);
                presentationTime = 0;
                presentationPosition = 0;
                result = AAudioStream_getTimestamp(
                        stream(), CLOCK_MONOTONIC, &presentationPosition, &presentationTime);
            } while (result != AAUDIO_OK && --maxRetries > 0 &&
                    lastPresentationPosition == presentationPosition);

            if (result == AAUDIO_OK) {
                const int64_t latencyNanos = getLatency(presentationTime, presentationPosition);
                maxLatencyNanos = std::max(maxLatencyNanos, latencyNanos);
                minLatencyNanos = std::min(minLatencyNanos, latencyNanos);
                sumLatencyNanos += latencyNanos;
            }

            EXPECT_EQ(AAUDIO_OK, result);
            // There should be a new timestamp available in 10s.
            EXPECT_NE(lastPresentationPosition, presentationPosition);
            lastPresentationPosition = presentationPosition;
        }
        mHelper->stopStream();
        // The latency must be consistent.
        EXPECT_LT(maxLatencyNanos - minLatencyNanos, MAX_LATENCY_RANGE);
        EXPECT_LT(sumLatencyNanos / NUM_TIMESTAMP_QUERY, MAX_LATENCY);
    }

    virtual bool isOutput() const = 0;

    virtual void processData(const int32_t frames, const int64_t timeoutNanos) = 0;

    std::unique_ptr<T> mHelper;
    bool mSetupSuccessful = false;

    std::unique_ptr<int16_t[]> mShortData;
    std::unique_ptr<float[]> mFloatData;
};

class AAudioInputStreamTest : public AAudioStreamTest<InputStreamBuilderHelper> {
protected:
    void SetUp() override;

    bool isOutput() const override { return false; }
    void processData(const int32_t frames, const int64_t timeoutNanos) override;

    int32_t mFramesPerRead;
};

void AAudioInputStreamTest::SetUp() {
    AAudioCtsBase::SetUp();

    mSetupSuccessful = false;
    if (!deviceSupportsFeature(FEATURE_RECORDING)) return;
    mHelper.reset(new InputStreamBuilderHelper(
                    std::get<PARAM_SHARING_MODE>(GetParam()),
                    std::get<PARAM_PERF_MODE>(GetParam())));
    mHelper->initBuilder();
    mHelper->createAndVerifyStream(&mSetupSuccessful);
    if (!mSetupSuccessful) return;

    mFramesPerRead = framesPerBurst();
    const int32_t framesPerMsec = actual().sampleRate / MILLIS_PER_SECOND;
    // Some DMA might use very short bursts of 16 frames. We don't need to read such small
    // buffers. But it helps to use a multiple of the burst size for predictable scheduling.
    while (mFramesPerRead < framesPerMsec) {
        mFramesPerRead *= 2;
    }
    allocateDataBuffer(mFramesPerRead);
}

void AAudioInputStreamTest::processData(const int32_t frames, const int64_t timeoutNanos) {
    // See b/62090113. For legacy path, the device is only known after
    // the stream has been started.
    EXPECT_NE(AAUDIO_UNSPECIFIED, AAudioStream_getDeviceId(stream()));
    for (int32_t framesLeft = frames; framesLeft > 0; ) {
        aaudio_result_t result = AAudioStream_read(
                stream(), getDataBuffer(), std::min(frames, mFramesPerRead), timeoutNanos);
        EXPECT_GT(result, 0);
        framesLeft -= result;
    }
}

TEST_P(AAudioInputStreamTest, testReading) {
    if (!mSetupSuccessful) return;

    const int32_t framesToRecord = actual().sampleRate;  // 1 second
    EXPECT_EQ(0, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(0, AAudioStream_getFramesWritten(stream()));
    mHelper->startStream();
    processData(framesToRecord, DEFAULT_READ_TIMEOUT);
    mHelper->stopStream();
    EXPECT_GE(AAudioStream_getFramesRead(stream()), framesToRecord);
    EXPECT_GE(AAudioStream_getFramesWritten(stream()), framesToRecord);
    EXPECT_GE(AAudioStream_getXRunCount(stream()), 0);
}

TEST_P(AAudioInputStreamTest, testGetTimestamp) {
    if (!mSetupSuccessful) return;

    // Disabling timestamp test for input stream due to timestamp will not be available on devices
    // that don't support MMAP. This is caused by b/30557134.
    // testTimestamp(DEFAULT_READ_TIMEOUT);
}

TEST_P(AAudioInputStreamTest, testStartReadStop) {
    if (!mSetupSuccessful) return;

    // Use 1/8 second as start-stops takes more time than just recording. This is 125 ms of data.
    const int32_t framesToRecord = actual().sampleRate / 8;
    // Since starting and stopping streams takes time, stream starts and stops should be limited.
    // For example, if a certain MMAP stream uses 2 ms bursts, there are 125 ms / 2 ms = 63 reads.
    // kFramesPerReadMultiple is 63 / 10 = 6, so open/close will only be called 63 / 6 = 11 times.
    constexpr int32_t kTargetReadCount = 10;
    const int32_t kFramesPerReadMultiple =
            std::max(1, framesToRecord / mFramesPerRead / kTargetReadCount);
    EXPECT_EQ(0, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(0, AAudioStream_getFramesWritten(stream()));
    for (int32_t framesLeft = framesToRecord; framesLeft > 0; ) {
        mHelper->startStream();
        for (int i = 0; i < kFramesPerReadMultiple; i++) {
            aaudio_result_t result = AAudioStream_read(stream(), getDataBuffer(),
                                                       std::min(framesToRecord, mFramesPerRead),
                                                       DEFAULT_READ_TIMEOUT);
            ASSERT_GT(result, 0);
            framesLeft -= result;
        }
        mHelper->stopStream();
    }
    EXPECT_GE(AAudioStream_getFramesRead(stream()), framesToRecord);
    EXPECT_GE(AAudioStream_getFramesWritten(stream()), framesToRecord);
}

TEST_P(AAudioInputStreamTest, testReadCounterFreezeAfterStop) {
    if (!mSetupSuccessful) return;

    const int32_t framesToRecord = actual().sampleRate / 10;  // 1/10 second
    EXPECT_EQ(0, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(0, AAudioStream_getFramesWritten(stream()));
    mHelper->startStream();
    for (int32_t framesLeft = framesToRecord; framesLeft > 0; ) {
        aaudio_result_t result = AAudioStream_read(
                stream(), getDataBuffer(), std::min(framesToRecord, mFramesPerRead),
                DEFAULT_READ_TIMEOUT);
        ASSERT_GT(result, 0);
        framesLeft -= result;
    }
    mHelper->stopStream();
    const int32_t framesReadAtStop = AAudioStream_getFramesRead(stream());
    const int32_t framesWrittenAtStop = AAudioStream_getFramesWritten(stream());
    ASSERT_EQ(0, TEMP_FAILURE_RETRY(usleep(100 * MICROS_PER_MILLISECOND)));
    EXPECT_EQ(framesReadAtStop, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(framesWrittenAtStop, AAudioStream_getFramesWritten(stream()));
}

TEST_P(AAudioInputStreamTest, testPauseAndFlushNotSupported) {
    if (!mSetupSuccessful) return;
    mHelper->startStream();
    EXPECT_EQ(AAUDIO_ERROR_UNIMPLEMENTED, AAudioStream_requestPause(stream()));
    EXPECT_EQ(AAUDIO_ERROR_UNIMPLEMENTED, AAudioStream_requestFlush(stream()));
    mHelper->stopStream();
}

TEST_P(AAudioInputStreamTest, testRelease) {
    if (!mSetupSuccessful) return;

    mHelper->startStream();
    // Force update of states.
    aaudio_result_t result = AAudioStream_read(
            stream(), getDataBuffer(), mFramesPerRead,
            DEFAULT_READ_TIMEOUT);
    ASSERT_GT(result, 0);
    mHelper->stopStream();

    // It should be safe to release multiple times.
    for (int i = 0; i < 3; i++) {
      EXPECT_EQ(AAUDIO_OK, AAudioStream_release(stream()));
      aaudio_stream_state_t state = AAudioStream_getState(stream());
      EXPECT_EQ(AAUDIO_STREAM_STATE_CLOSING, state);
    }

    checkCallsAfterRelease();

}

INSTANTIATE_TEST_CASE_P(SPM, AAudioInputStreamTest,
        ::testing::Values(
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_NONE),
                // Recording in POWER_SAVING mode isn't supported, b/62291775.
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                std::make_tuple(AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_NONE),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_POWER_SAVING),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY)),
        &getTestName);


class AAudioOutputStreamTest : public AAudioStreamTest<OutputStreamBuilderHelper> {
  protected:
    void SetUp() override;

    bool isOutput() const override { return true; }
    void processData(const int32_t frames, const int64_t timeoutNanos) override;
};

void AAudioOutputStreamTest::SetUp() {
    AAudioCtsBase::SetUp();

    mSetupSuccessful = false;
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    mHelper.reset(new OutputStreamBuilderHelper(
                    std::get<PARAM_SHARING_MODE>(GetParam()),
                    std::get<PARAM_PERF_MODE>(GetParam())));
    mHelper->initBuilder();

    mHelper->createAndVerifyStream(&mSetupSuccessful);
    if (!mSetupSuccessful) return;

    allocateDataBuffer(framesPerBurst());
}

void AAudioOutputStreamTest::processData(const int32_t frames, const int64_t timeoutNanos) {
    for (int32_t framesLeft = frames; framesLeft > 0;) {
        aaudio_result_t framesWritten = AAudioStream_write(
                stream(), getDataBuffer(),
                std::min(framesPerBurst(), framesLeft), timeoutNanos);
        EXPECT_GT(framesWritten, 0);
        framesLeft -= framesWritten;
    }
}

TEST_P(AAudioOutputStreamTest, testWriting) {
    if (!mSetupSuccessful) return;

    // Prime the buffer.
    int32_t framesWritten = 0;
    int64_t framesTotal = 0;
    int64_t timeoutNanos = 0;
    do {
        framesWritten = AAudioStream_write(
                stream(), getDataBuffer(), framesPerBurst(), timeoutNanos);
        // There should be some room for priming the buffer.
        framesTotal += framesWritten;
        ASSERT_GE(framesWritten, 0);
        ASSERT_LE(framesWritten, framesPerBurst());
    } while (framesWritten > 0);
    ASSERT_TRUE(framesTotal > 0);

    int writeLoops = 0;
    int64_t aaudioFramesRead = 0;
    int64_t aaudioFramesReadPrev = 0;
    int64_t aaudioFramesReadFinal = 0;
    int64_t aaudioFramesWritten = 0;
    // Start/write/pause more than once to see if it fails after the first time.
    // Write some data and measure the rate to see if the timing is OK.
    for (int numLoops = 0; numLoops < 2; numLoops++) {
        mHelper->startStream();
        // See b/62090113. For legacy path, the device is only known after
        // the stream has been started.
        ASSERT_NE(AAUDIO_UNSPECIFIED, AAudioStream_getDeviceId(stream()));

        // Write some data while we are running. Read counter should be advancing.
        writeLoops = 1 * actual().sampleRate / framesPerBurst(); // 1 second
        ASSERT_LT(2, writeLoops); // detect absurdly high framesPerBurst
        timeoutNanos = 100 * (NANOS_PER_SECOND * framesPerBurst() /
                actual().sampleRate); // N bursts
        framesWritten = 1;
        aaudioFramesRead = AAudioStream_getFramesRead(stream());
        aaudioFramesReadPrev = aaudioFramesRead;
        int64_t beginTime = getNanoseconds(CLOCK_MONOTONIC);
        do {
            framesWritten = AAudioStream_write(
                    stream(), getDataBuffer(), framesPerBurst(), timeoutNanos);
            EXPECT_EQ(framesPerBurst(), framesWritten);

            framesTotal += framesWritten;
            aaudioFramesWritten = AAudioStream_getFramesWritten(stream());
            EXPECT_EQ(framesTotal, aaudioFramesWritten);

            // Try to get a more accurate measure of the sample rate.
            if (beginTime == 0) {
                aaudioFramesRead = AAudioStream_getFramesRead(stream());
                if (aaudioFramesRead > aaudioFramesReadPrev) { // is read pointer advancing
                    beginTime = getNanoseconds(CLOCK_MONOTONIC);
                    aaudioFramesReadPrev = aaudioFramesRead;
                }
            }
        } while (framesWritten > 0 && writeLoops-- > 0);

        aaudioFramesReadFinal = AAudioStream_getFramesRead(stream());
        ASSERT_GT(aaudioFramesReadFinal, 0);
        EXPECT_GT(aaudioFramesReadFinal, aaudioFramesReadPrev);


        // TODO why is AudioTrack path so inaccurate?
        /* See b/38268547, there is no way to specify that MMAP mode needs to be used,
           even EXCLUSIVE mode may fall back to legacy
        const int64_t endTime = getNanoseconds(CLOCK_MONOTONIC);
        const double rateTolerance = 200.0; // arbitrary tolerance for sample rate
        if (std::get<PARAM_SHARING_MODE>(GetParam()) != AAUDIO_SHARING_MODE_SHARED) {
            // Calculate approximate sample rate and compare with stream rate.
            double seconds = (endTime - beginTime) / (double) NANOS_PER_SECOND;
            double measuredRate = (aaudioFramesReadFinal - aaudioFramesReadPrev) / seconds;
            ASSERT_NEAR(actual().sampleRate, measuredRate, rateTolerance);
        }
        */

        mHelper->pauseStream();
    }
    EXPECT_GE(AAudioStream_getXRunCount(stream()), 0);

    // Make sure the read counter is not advancing when we are paused.
    aaudioFramesRead = AAudioStream_getFramesRead(stream());
    ASSERT_GE(aaudioFramesRead, aaudioFramesReadFinal); // monotonic increase
    // Currently not possible to enforce for AAudio over AudioTrack (b/33354715).
    // ASSERT_EQ(0, TEMP_FAILURE_RETRY(usleep(100 * MICROS_PER_MILLISECOND)));
    // EXPECT_EQ(aaudioFramesRead, AAudioStream_getFramesRead(stream()));

    // ------------------- TEST FLUSH -----------------
    // Prime the buffer.
    timeoutNanos = 0;
    writeLoops = 1000;
    do {
        framesWritten = AAudioStream_write(
                stream(), getDataBuffer(), framesPerBurst(), timeoutNanos);
        framesTotal += framesWritten;
    } while (framesWritten > 0 && writeLoops-- > 0);
    EXPECT_EQ(0, framesWritten);

    mHelper->flushStream();

    // After a flush, the read counter should be caught up with the write counter.
    aaudioFramesWritten = AAudioStream_getFramesWritten(stream());
    EXPECT_EQ(framesTotal, aaudioFramesWritten);
    aaudioFramesRead = AAudioStream_getFramesRead(stream());
    EXPECT_EQ(aaudioFramesWritten, aaudioFramesRead);

    sleep(1); // FIXME - The write returns 0 if we remove this sleep! Why?

    // The buffer should be empty after a flush so we should be able to write.
    framesWritten = AAudioStream_write(stream(), getDataBuffer(), framesPerBurst(), timeoutNanos);
    // There should be some room for priming the buffer.
    ASSERT_GT(framesWritten, 0);
    ASSERT_LE(framesWritten, framesPerBurst());
}

// Make sure the read and write frame counters do not diverge by more than the
// capacity of the buffer.
TEST_P(AAudioOutputStreamTest, testWriteStopWrite) {
    if (!mSetupSuccessful) return;

    int32_t framesWritten = 0;
    int64_t framesTotal = 0;
    int64_t timeoutNanos = 0;
    int32_t writeLoops = 0;
    int64_t aaudioFramesRead = 0;
    int64_t aaudioFramesWritten = 0;
    int32_t frameCapacity = AAudioStream_getBufferCapacityInFrames(stream());

    // Start/write/stop more than once to see if it fails after the first time.
    for (int numLoops = 0; numLoops < 2; numLoops++) {
        mHelper->startStream();

        // Write some data while we are running. Read counter should be advancing.
        writeLoops = 1 * actual().sampleRate / framesPerBurst(); // 1 second
        ASSERT_LT(2, writeLoops); // detect absurdly high framesPerBurst

        // Calculate a reasonable timeout value.
        const int32_t timeoutBursts = 20;
        timeoutNanos = timeoutBursts * (NANOS_PER_SECOND * framesPerBurst() /
                              actual().sampleRate);
        // Account for cold start latency.
        timeoutNanos = std::max(timeoutNanos, 400 * NANOS_PER_MILLISECOND);

        do {
            framesWritten = AAudioStream_write(
                    stream(), getDataBuffer(), framesPerBurst(), timeoutNanos);
            EXPECT_EQ(framesPerBurst(), framesWritten);
            framesTotal += framesWritten;

            aaudioFramesWritten = AAudioStream_getFramesWritten(stream());
            EXPECT_EQ(framesTotal, aaudioFramesWritten);
            aaudioFramesRead = AAudioStream_getFramesRead(stream());

            // How many frames are sitting in the buffer?
            int32_t writtenButNotRead = (int32_t)(aaudioFramesWritten - aaudioFramesRead);
            ASSERT_LE(writtenButNotRead, frameCapacity);
            // It is legal for writtenButNotRead to be negative because
            // MMAP HW can underrun the FIFO.
        } while (framesWritten > 0 && writeLoops-- > 0);

        mHelper->stopStream();
    }
}

TEST_P(AAudioOutputStreamTest, testGetTimestamp) {
    if (!mSetupSuccessful) return;

    // Calculate a reasonable timeout value.
    const int32_t timeoutBursts = 20;
    int64_t timeoutNanos =
            timeoutBursts * (NANOS_PER_SECOND * framesPerBurst() / actual().sampleRate);
    // Account for cold start latency.
    timeoutNanos = std::max(timeoutNanos, 400 * NANOS_PER_MILLISECOND);

    testTimestamp(timeoutNanos);
}

TEST_P(AAudioOutputStreamTest, testRelease) {
    if (!mSetupSuccessful) return;

    mHelper->startStream();
    // Write a few times so the device has time to read some of the data
    // and maybe advance the framesRead.
    for (int i = 0; i < 3; i++) {
        aaudio_result_t result = AAudioStream_write(
                stream(), getDataBuffer(), framesPerBurst(),
                DEFAULT_READ_TIMEOUT);
        ASSERT_GT(result, 0);
    }
    mHelper->stopStream();
    EXPECT_GE(AAudioStream_getFramesRead(stream()), 0);

    // It should be safe to release multiple times.
    for (int i = 0; i < 3; i++) {
      EXPECT_EQ(AAUDIO_OK, AAudioStream_release(stream()));
      aaudio_stream_state_t state = AAudioStream_getState(stream());
      EXPECT_EQ(AAUDIO_STREAM_STATE_CLOSING, state);
    }

    checkCallsAfterRelease();

}

// Note that the test for EXCLUSIVE sharing mode may fail gracefully if
// this mode isn't supported by the platform.
INSTANTIATE_TEST_CASE_P(SPM, AAudioOutputStreamTest,
        ::testing::Values(
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_NONE),
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_POWER_SAVING),

                std::make_tuple(AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_NONE),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_POWER_SAVING)),
        &getTestName);

