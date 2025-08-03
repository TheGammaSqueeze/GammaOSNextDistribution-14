/*
 * Copyright (C) 2022 The Android Open Source Project
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

// Tests basic AAudio input and output.

#include <stdio.h>
#include <unistd.h>

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>

#include "utils.h"

using TestAAudioBasicParams = std::tuple<aaudio_performance_mode_t, aaudio_direction_t>;

enum {
    PARAM_PERFORMANCE_MODE = 0,
    PARAM_DIRECTION,
};

class TestAAudioBasic : public AAudioCtsBase,
                        public ::testing::WithParamInterface<TestAAudioBasicParams> {

protected:
    static void testConfiguration(aaudio_performance_mode_t perfMode,
                                 aaudio_direction_t direction) {
        if (direction == AAUDIO_DIRECTION_INPUT) {
            if (!deviceSupportsFeature(FEATURE_RECORDING)) return;
        } else {
            if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
        }
        float buffer[kNumFrames * kChannelCount] = {};

        AAudioStreamBuilder *aaudioBuilder = nullptr;
        AAudioStream *aaudioStream = nullptr;

        // Use an AAudioStreamBuilder to contain requested parameters.
        ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

        // Request stream properties.
        AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, perfMode);
        AAudioStreamBuilder_setDirection(aaudioBuilder, direction);
        AAudioStreamBuilder_setChannelCount(aaudioBuilder, kChannelCount);
        AAudioStreamBuilder_setFormat(aaudioBuilder, AAUDIO_FORMAT_PCM_FLOAT);

        // Create an AAudioStream using the Builder.
        ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
        AAudioStreamBuilder_delete(aaudioBuilder);

        EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream));

        if (direction == AAUDIO_DIRECTION_INPUT) {
            EXPECT_EQ(kNumFrames,
                      AAudioStream_read(aaudioStream, &buffer, kNumFrames, kNanosPerSecond));
        } else {
            EXPECT_EQ(kNumFrames,
                      AAudioStream_write(aaudioStream, &buffer, kNumFrames, kNanosPerSecond));
        }

        EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream));

        EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    }
    static constexpr int64_t kNanosPerSecond = 1000000000;
    static constexpr int kNumFrames = 256;
    static constexpr int kChannelCount = 2;
};

const char* directionToString(aaudio_sharing_mode_t direction) {
    switch (direction) {
        case AAUDIO_DIRECTION_OUTPUT: return "OUTPUT";
        case AAUDIO_DIRECTION_INPUT: return "INPUT";
    }
    return "UNKNOWN";
}

static std::string getTestName(const ::testing::TestParamInfo<TestAAudioBasicParams>& info) {
    return std::string()
            + performanceModeToString(std::get<PARAM_PERFORMANCE_MODE>(info.param))
            + "__" + directionToString(std::get<PARAM_DIRECTION>(info.param));
}

TEST_P(TestAAudioBasic, TestBasic) {
    testConfiguration(std::get<PARAM_PERFORMANCE_MODE>(GetParam()),
            std::get<PARAM_DIRECTION>(GetParam()));
}

INSTANTIATE_TEST_SUITE_P(
        AAudioBasic,
        TestAAudioBasic,
        ::testing::Values(
                TestAAudioBasicParams({AAUDIO_PERFORMANCE_MODE_NONE, AAUDIO_DIRECTION_OUTPUT}),
                TestAAudioBasicParams({AAUDIO_PERFORMANCE_MODE_NONE, AAUDIO_DIRECTION_INPUT}),
                TestAAudioBasicParams({AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                                       AAUDIO_DIRECTION_OUTPUT}),
                TestAAudioBasicParams({AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                                       AAUDIO_DIRECTION_INPUT})),
        &getTestName
);