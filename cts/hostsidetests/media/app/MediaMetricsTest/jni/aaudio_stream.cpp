/*
 * Copyright (C) 2021 The Android Open Source Project
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
 *
 */

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioStreamAtom-JNI"

#include <jni.h>

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>

constexpr int kNumFrames = 256;
constexpr int64_t kMillisPerNanos = 1000000;
constexpr int64_t kMillisPerMicros = 1000;

void tryOpeningStream(aaudio_direction_t direction, aaudio_performance_mode_t performanceMode) {
    AAudioStreamBuilder *builder = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&builder));
    ASSERT_NE(nullptr, builder);
    AAudioStreamBuilder_setDirection(builder, direction);
    AAudioStreamBuilder_setPerformanceMode(builder, performanceMode);

    AAudioStream *stream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(builder, &stream));
    ASSERT_NE(nullptr, stream);
    ASSERT_EQ(direction, AAudioStream_getDirection(stream));

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(stream));

    int channelCount = AAudioStream_getChannelCount(stream);
    ASSERT_GT(channelCount, 0);

    std::unique_ptr<float[]> buffer(new float[kNumFrames * channelCount]);

    if (direction == AAUDIO_DIRECTION_INPUT) {
        ASSERT_EQ(kNumFrames,
                  AAudioStream_read(stream, buffer.get(), kNumFrames, 500 * kMillisPerNanos));
    } else {
        ASSERT_EQ(kNumFrames,
                  AAudioStream_write(stream, buffer.get(), kNumFrames, 500 * kMillisPerNanos));
        // Total_frames_transferred is the number of frames consumed by the audio endpoint.
        // Wait until the data is consumed by the audio endpoint.
        // Wait in 10ms increments up to 50 times.
        constexpr int kMaxRetries = 50;
        constexpr int kTimeBetweenRetriesMillis = 10;
        int numRetries = 0;
        int framesRead = AAudioStream_getFramesRead(stream);
        while (numRetries < kMaxRetries && framesRead < kNumFrames) {
            usleep(kTimeBetweenRetriesMillis * kMillisPerMicros);
            framesRead = AAudioStream_getFramesRead(stream);
            numRetries++;
        }
    }

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStop(stream));

    // Cleanup
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(builder));
    ASSERT_EQ(AAUDIO_OK, AAudioStream_close(stream));
}

extern "C" JNIEXPORT void JNICALL
Java_android_media_metrics_cts_MediaMetricsAtomHostSideTests_testAAudioLowLatencyOutputStream(
        JNIEnv *, jobject /* this */) {
    tryOpeningStream(AAUDIO_DIRECTION_OUTPUT, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
}

extern "C" JNIEXPORT void JNICALL
Java_android_media_metrics_cts_MediaMetricsAtomHostSideTests_testAAudioLowLatencyInputStream(
        JNIEnv *, jobject /* this */) {
    tryOpeningStream(AAUDIO_DIRECTION_INPUT, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
}

extern "C" JNIEXPORT void JNICALL
Java_android_media_metrics_cts_MediaMetricsAtomHostSideTests_testAAudioLegacyOutputStream(
        JNIEnv *, jobject /* this */) {
    tryOpeningStream(AAUDIO_DIRECTION_OUTPUT, AAUDIO_PERFORMANCE_MODE_NONE);
}

extern "C" JNIEXPORT void JNICALL
Java_android_media_metrics_cts_MediaMetricsAtomHostSideTests_testAAudioLegacyInputStream(
        JNIEnv *, jobject /* this */) {
    tryOpeningStream(AAUDIO_DIRECTION_INPUT, AAUDIO_PERFORMANCE_MODE_NONE);
}
