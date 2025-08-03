/*
 * Copyright 2017 The Android Open Source Project
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

#include <cstring>
#include <sstream>
#include <utility>

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <android-base/properties.h>
#include <gtest/gtest.h>
#include <system/audio.h> /* FCC_LIMIT */

#include "utils.h"

/**
 * See https://source.android.com/devices/tech/perf/low-ram
 * for more details.
 *
 * @return true if running on low memory device
 */
static bool isLowRamDevice() {
    return android::base::GetBoolProperty("ro.config.low_ram", false);
}

// Creates a builder, the caller takes ownership
static void create_stream_builder(AAudioStreamBuilder** aaudioBuilder) {
    aaudio_result_t result = AAudio_createStreamBuilder(aaudioBuilder);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_NE(nullptr, *aaudioBuilder);
}

enum class Expect { FAIL, SUCCEED, NOT_CRASH };

// Tries to open an audio stream using a primed Builder.
// Takes ownership of the Builder.
static void try_opening_audio_stream(AAudioStreamBuilder *aaudioBuilder, Expect expect) {
    // Create an AAudioStream using the Builder.
    AAudioStream *aaudioStream = nullptr;
    int64_t beforeTimeNanos = getNanoseconds();
    aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);
    if (expect == Expect::FAIL) {
        ASSERT_NE(AAUDIO_OK, result);
        ASSERT_EQ(nullptr, aaudioStream);
    } else if (expect == Expect::SUCCEED) {
        ASSERT_EQ(AAUDIO_OK, result);
        ASSERT_NE(nullptr, aaudioStream);
    } else { // NOT_CRASH
        ASSERT_TRUE(((result < 0) && (aaudioStream == nullptr))
                || ((result == AAUDIO_OK) && (aaudioStream != nullptr)));
    }

    // The stream should be open within one second.
    static const int64_t kNanosPerSecond = 1e9;
    ASSERT_LT(getNanoseconds() - beforeTimeNanos, kNanosPerSecond)
            << "It took more than one second to open stream";

    // Cleanup
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    if (aaudioStream != nullptr) {
        beforeTimeNanos = getNanoseconds();
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
        // The stream should be closed within one second.
        ASSERT_LT(getNanoseconds() - beforeTimeNanos, kNanosPerSecond)
                << "It took more than one second to close stream";
    }
}

// Test creating a default stream with specific devices
static void runtest_aaudio_devices(int32_t deviceId, Expect expect) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setDeviceId(aaudioBuilder, deviceId);
    try_opening_audio_stream(aaudioBuilder, expect);
}

class AAudioTest : public AAudioCtsBase {};

TEST_F(AAudioTest, aaudio_stream_device_unspecified) {
    runtest_aaudio_devices(AAUDIO_UNSPECIFIED, Expect::NOT_CRASH);
}

/* FIXME - why can we open this device? What is an illegal deviceId?
TEST_F(AAudioTest, aaudio_stream_device_absurd) {
    runtest_aaudio_devices(19736459, true);
}
*/
/* FIXME review
TEST_F(AAudioTest, aaudio_stream_device_reasonable) {
    runtest_aaudio_devices(1, false);
}
*/

/* FIXME - why can we open this device? What is an illegal deviceId?
TEST_F(AAudioTest, aaudio_stream_device_negative) {
    runtest_aaudio_devices(-765, true);
}
*/

// Test creating a default stream with everything unspecified.
TEST_F(AAudioTest, aaudio_stream_unspecified) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);

    // Create an AAudioStream using the Builder.
    AAudioStream *aaudioStream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
    ASSERT_NE(nullptr, aaudioStream);

    // Cleanup
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
}

class AAudioStreamBuilderSamplingRateTest : public AAudioCtsBase,
                                            public ::testing::WithParamInterface<int32_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<int32_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidSamplingRate(int32_t sr) {
        return sr == AAUDIO_UNSPECIFIED || (sr >= 8000 && sr <= 1000000);
    }
};

TEST_P(AAudioStreamBuilderSamplingRateTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    const int32_t sampleRate = GetParam();
    const bool isSampleRateValid = isValidSamplingRate(sampleRate);
    // Opening a stream with a high sample rates can fail because the required buffer size
    // is bigger than the heap size. This is a limitation in AudioFlinger.  b/112528380
    if (isSampleRateValid && isLowRamDevice() && (sampleRate > 192000)) {
        return; // skip this test
    }
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setSampleRate(aaudioBuilder, sampleRate);
    try_opening_audio_stream(
            aaudioBuilder, isSampleRateValid ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(SR, AAudioStreamBuilderSamplingRateTest,
        ::testing::Values(
                // Commonly used values
                AAUDIO_UNSPECIFIED, 8000, 11025, 16000, 22050, 44100, 48000, 88200, 96000,
                176400, 192000, 384000,
                // Odd values
                AAUDIO_UNSPECIFIED - 1, AAUDIO_UNSPECIFIED + 1, 1234, 10000000),
        &AAudioStreamBuilderSamplingRateTest::getTestName);

class AAudioStreamBuilderChannelCountTest : public AAudioCtsBase,
                                            public ::testing::WithParamInterface<int32_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<int32_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidChannelCount(int32_t cc) {
        return cc == AAUDIO_UNSPECIFIED || (cc >= 1 && cc <= FCC_LIMIT);
    }
};

TEST_P(AAudioStreamBuilderChannelCountTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setChannelCount(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidChannelCount(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(CC, AAudioStreamBuilderChannelCountTest,
        ::testing::Values(
                // Reasonable values that should work OK.
                AAUDIO_UNSPECIFIED, 1, 2, 3, 4, 5, 6, 7, 8, FCC_LIMIT,
                // These values should fail.
                AAUDIO_UNSPECIFIED - 1, (FCC_LIMIT + 1), 1000, 1000000),
        &AAudioStreamBuilderChannelCountTest::getTestName);

class AAudioStreamBuilderFormatTest : public AAudioCtsBase,
                                      public ::testing::WithParamInterface<aaudio_format_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_format_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidFormat(aaudio_format_t f) {
        switch (f) {
            case AAUDIO_FORMAT_UNSPECIFIED:
            case AAUDIO_FORMAT_PCM_I16:
            case AAUDIO_FORMAT_PCM_FLOAT:
            case AAUDIO_FORMAT_IEC61937:
            case AAUDIO_FORMAT_PCM_I24_PACKED:
            case AAUDIO_FORMAT_PCM_I32:
                return true;
        }
        return false;
    }
};

TEST_P(AAudioStreamBuilderFormatTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setFormat(aaudioBuilder, GetParam());
    const aaudio_format_t format = GetParam();
    Expect expectedResult = isValidFormat(format) ? Expect::SUCCEED : Expect::FAIL;
    if (format == AAUDIO_FORMAT_IEC61937) {
        expectedResult = isIEC61937Supported() ? Expect::SUCCEED : Expect::FAIL;
        // For IEC61937, sample rate and channel mask should be specified.
        AAudioStreamBuilder_setSampleRate(aaudioBuilder, 48000);
        AAudioStreamBuilder_setChannelMask(aaudioBuilder, AAUDIO_CHANNEL_STEREO);
    }
    try_opening_audio_stream(aaudioBuilder, expectedResult);
}

INSTANTIATE_TEST_CASE_P(F, AAudioStreamBuilderFormatTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_FORMAT_UNSPECIFIED, AAUDIO_FORMAT_PCM_I16, AAUDIO_FORMAT_PCM_FLOAT,
                AAUDIO_FORMAT_PCM_I24_PACKED, AAUDIO_FORMAT_PCM_I32, AAUDIO_FORMAT_IEC61937,
                // Odd values
                AAUDIO_FORMAT_INVALID, AAUDIO_FORMAT_INVALID - 1, 100, 1000000, 10000000),
        &AAudioStreamBuilderFormatTest::getTestName);

class AAudioStreamBuilderSharingModeTest :
        public AAudioCtsBase, public ::testing::WithParamInterface<aaudio_sharing_mode_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_sharing_mode_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidSharingMode(aaudio_sharing_mode_t f) {
        return f == AAUDIO_SHARING_MODE_SHARED || f == AAUDIO_SHARING_MODE_EXCLUSIVE;
    }
};

TEST_P(AAudioStreamBuilderSharingModeTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setSharingMode(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidSharingMode(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(SM, AAudioStreamBuilderSharingModeTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_SHARING_MODE_SHARED, AAUDIO_SHARING_MODE_EXCLUSIVE,
                // Odd values
                -1, 100, 1000000, 10000000),
        &AAudioStreamBuilderSharingModeTest::getTestName);

class AAudioStreamBuilderDirectionTest : public AAudioCtsBase,
                                         public ::testing::WithParamInterface<aaudio_direction_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_direction_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidDirection(aaudio_direction_t f) {
        return f == AAUDIO_DIRECTION_OUTPUT || f == AAUDIO_DIRECTION_INPUT;
    }
};

TEST_P(AAudioStreamBuilderDirectionTest, openStream) {
    if (GetParam() == AAUDIO_DIRECTION_OUTPUT
            && !deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    if (GetParam() == AAUDIO_DIRECTION_INPUT
            && !deviceSupportsFeature(FEATURE_RECORDING)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setDirection(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidDirection(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(SD, AAudioStreamBuilderDirectionTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_DIRECTION_OUTPUT, AAUDIO_DIRECTION_INPUT,
                // Odd values
                -1, 100, 1000000, 10000000),
        &AAudioStreamBuilderDirectionTest::getTestName);

class AAudioStreamBuilderBufferCapacityTest : public AAudioCtsBase,
                                              public ::testing::WithParamInterface<int32_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<int32_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    // There is no hard defined limit, the actual maximum capacity depends
    // on the implementation.
    static bool isValidCapacity(int32_t bc) {
        return bc == AAUDIO_UNSPECIFIED || bc >= 0;
    }
};

TEST_P(AAudioStreamBuilderBufferCapacityTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setBufferCapacityInFrames(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidCapacity(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(BC, AAudioStreamBuilderBufferCapacityTest,
        ::testing::Values(
                // Reasonable values that should not fail
                AAUDIO_UNSPECIFIED, 8 * 192, 2 * 1024,
                // Some large number, should not fail
                8192, 512 * 1024,
                // Odd values
                AAUDIO_UNSPECIFIED - 1),
        &AAudioStreamBuilderBufferCapacityTest::getTestName);

class AAudioStreamBuilderPerfModeTest :
        public AAudioCtsBase, public ::testing::WithParamInterface<aaudio_performance_mode_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_performance_mode_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidPerfMode(aaudio_performance_mode_t pm) {
        switch (pm) {
            case AAUDIO_PERFORMANCE_MODE_NONE:
            case AAUDIO_PERFORMANCE_MODE_POWER_SAVING:
            case AAUDIO_PERFORMANCE_MODE_LOW_LATENCY:
                return true;
        }
        return false;
    }
};

TEST_P(AAudioStreamBuilderPerfModeTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidPerfMode(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(PM, AAudioStreamBuilderPerfModeTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_PERFORMANCE_MODE_NONE,
                AAUDIO_PERFORMANCE_MODE_POWER_SAVING,
                AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                // Odd values
                AAUDIO_UNSPECIFIED - 1, AAUDIO_UNSPECIFIED, 100, 1000000, 10000000),
        &AAudioStreamBuilderPerfModeTest::getTestName);

class AAudioStreamBuilderChannelMaskTest :
        public AAudioCtsBase, public ::testing::WithParamInterface<aaudio_channel_mask_t> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_channel_mask_t>& info) {
        std::stringstream ss;
        ss << "0x" << std::hex << info.param;
        return ss.str();
    }
protected:

    static bool isValidChannelMask(aaudio_channel_mask_t channelMask, bool isInput) {
        if (channelMask == AAUDIO_UNSPECIFIED) {
            return true;
        }

        if (__builtin_popcount(channelMask) > FCC_LIMIT) {
            return false;
        }

        if (isInput) {
            switch (channelMask) {
                case AAUDIO_CHANNEL_MONO:
                case AAUDIO_CHANNEL_STEREO:
                case AAUDIO_CHANNEL_FRONT_BACK:
                case AAUDIO_CHANNEL_2POINT0POINT2:
                case AAUDIO_CHANNEL_2POINT1POINT2:
                case AAUDIO_CHANNEL_3POINT0POINT2:
                case AAUDIO_CHANNEL_3POINT1POINT2:
                case AAUDIO_CHANNEL_5POINT1:
                    return true;
            }
            return false;
        } else {
            switch (channelMask) {
                case AAUDIO_CHANNEL_MONO:
                case AAUDIO_CHANNEL_STEREO:
                case AAUDIO_CHANNEL_2POINT1:
                case AAUDIO_CHANNEL_TRI:
                case AAUDIO_CHANNEL_TRI_BACK:
                case AAUDIO_CHANNEL_3POINT1:
                case AAUDIO_CHANNEL_2POINT0POINT2:
                case AAUDIO_CHANNEL_2POINT1POINT2:
                case AAUDIO_CHANNEL_3POINT0POINT2:
                case AAUDIO_CHANNEL_3POINT1POINT2:
                case AAUDIO_CHANNEL_QUAD:
                case AAUDIO_CHANNEL_QUAD_SIDE:
                case AAUDIO_CHANNEL_SURROUND:
                case AAUDIO_CHANNEL_PENTA:
                case AAUDIO_CHANNEL_5POINT1:
                case AAUDIO_CHANNEL_5POINT1_SIDE:
                case AAUDIO_CHANNEL_5POINT1POINT2:
                case AAUDIO_CHANNEL_5POINT1POINT4:
                case AAUDIO_CHANNEL_6POINT1:
                case AAUDIO_CHANNEL_7POINT1:
                case AAUDIO_CHANNEL_7POINT1POINT2:
                case AAUDIO_CHANNEL_7POINT1POINT4:
                case AAUDIO_CHANNEL_9POINT1POINT4:
                case AAUDIO_CHANNEL_9POINT1POINT6:
                    return true;
            }
            return false;
        }
    }

    void testChannelMask(aaudio_channel_mask_t channelMask, aaudio_direction_t direction);
};

void AAudioStreamBuilderChannelMaskTest::testChannelMask(aaudio_channel_mask_t channelMask,
                                                         aaudio_direction_t direction) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setDirection(aaudioBuilder, direction);
    AAudioStreamBuilder_setChannelMask(aaudioBuilder, channelMask);
    const Expect expect =
            isValidChannelMask(channelMask, direction) ? Expect::SUCCEED : Expect::FAIL;
    AAudioStream *aaudioStream = nullptr;
    aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);
    if (expect == Expect::FAIL) {
        ASSERT_NE(AAUDIO_OK, result);
        ASSERT_EQ(nullptr, aaudioStream);
    } else if (expect == Expect::SUCCEED) {
        ASSERT_EQ(AAUDIO_OK, result);
        ASSERT_NE(nullptr, aaudioStream);
        ASSERT_NE(0, AAudioStream_getChannelCount(aaudioStream));
        ASSERT_NE(0, AAudioStream_getHardwareChannelCount(aaudioStream));
        ASSERT_NE(AAUDIO_UNSPECIFIED, AAudioStream_getChannelMask(aaudioStream));
        ASSERT_NE(AAUDIO_CHANNEL_INVALID, AAudioStream_getChannelMask(aaudioStream));
    } else { // NOT_CRASH
        ASSERT_TRUE(((result < 0) && (aaudioStream == nullptr))
                || ((result == AAUDIO_OK) && (aaudioStream != nullptr)));
    }

    // Cleanup
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    if (aaudioStream != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    }
}

TEST_P(AAudioStreamBuilderChannelMaskTest, openInputStream) {
    if (!deviceSupportsFeature(FEATURE_RECORDING)) {
        return;
    }
    testChannelMask(GetParam(), AAUDIO_DIRECTION_INPUT);
}

TEST_P(AAudioStreamBuilderChannelMaskTest, openOutputStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) {
        return;
    }
    testChannelMask(GetParam(), AAUDIO_DIRECTION_OUTPUT);
}

INSTANTIATE_TEST_CASE_P(
        CM, AAudioStreamBuilderChannelMaskTest,
        ::testing::Values(
                // UNSPECIFIED is valid channel mask
                AAUDIO_UNSPECIFIED,
                AAUDIO_CHANNEL_INVALID,
                // Channel mask listed in audio.h
                // AAUDIO_CHANNEL_FRONT_LEFT,
                AAUDIO_CHANNEL_FRONT_RIGHT,
                AAUDIO_CHANNEL_FRONT_CENTER,
                AAUDIO_CHANNEL_LOW_FREQUENCY,
                AAUDIO_CHANNEL_BACK_LEFT,
                AAUDIO_CHANNEL_BACK_RIGHT,
                AAUDIO_CHANNEL_FRONT_LEFT_OF_CENTER,
                AAUDIO_CHANNEL_FRONT_RIGHT_OF_CENTER,
                AAUDIO_CHANNEL_BACK_CENTER,
                AAUDIO_CHANNEL_SIDE_LEFT,
                AAUDIO_CHANNEL_SIDE_RIGHT,
                AAUDIO_CHANNEL_TOP_CENTER,
                AAUDIO_CHANNEL_TOP_FRONT_LEFT,
                AAUDIO_CHANNEL_TOP_FRONT_CENTER,
                AAUDIO_CHANNEL_TOP_FRONT_RIGHT,
                AAUDIO_CHANNEL_TOP_BACK_LEFT,
                AAUDIO_CHANNEL_TOP_BACK_CENTER,
                AAUDIO_CHANNEL_TOP_BACK_RIGHT,
                AAUDIO_CHANNEL_TOP_SIDE_LEFT,
                AAUDIO_CHANNEL_TOP_SIDE_RIGHT,
                AAUDIO_CHANNEL_BOTTOM_FRONT_LEFT,
                AAUDIO_CHANNEL_BOTTOM_FRONT_CENTER,
                AAUDIO_CHANNEL_BOTTOM_FRONT_RIGHT,
                AAUDIO_CHANNEL_LOW_FREQUENCY_2,
                AAUDIO_CHANNEL_FRONT_WIDE_LEFT,
                AAUDIO_CHANNEL_FRONT_WIDE_RIGHT,
                AAUDIO_CHANNEL_MONO,
                AAUDIO_CHANNEL_STEREO,
                AAUDIO_CHANNEL_2POINT1,
                AAUDIO_CHANNEL_TRI,
                AAUDIO_CHANNEL_TRI_BACK,
                AAUDIO_CHANNEL_3POINT1,
                AAUDIO_CHANNEL_2POINT0POINT2,
                AAUDIO_CHANNEL_2POINT1POINT2,
                AAUDIO_CHANNEL_3POINT0POINT2,
                AAUDIO_CHANNEL_3POINT1POINT2,
                AAUDIO_CHANNEL_QUAD,
                AAUDIO_CHANNEL_QUAD_SIDE,
                AAUDIO_CHANNEL_SURROUND,
                AAUDIO_CHANNEL_PENTA,
                AAUDIO_CHANNEL_5POINT1,
                AAUDIO_CHANNEL_5POINT1_SIDE,
                AAUDIO_CHANNEL_6POINT1,
                AAUDIO_CHANNEL_7POINT1,
                AAUDIO_CHANNEL_5POINT1POINT2,
                AAUDIO_CHANNEL_5POINT1POINT4,
                AAUDIO_CHANNEL_7POINT1POINT2,
                AAUDIO_CHANNEL_7POINT1POINT4,
                AAUDIO_CHANNEL_9POINT1POINT4,
                AAUDIO_CHANNEL_9POINT1POINT6,
                AAUDIO_CHANNEL_FRONT_BACK,
                // Odd value
                0x20000000,
                0x30000000,
                0x40000005),
        &AAudioStreamBuilderChannelMaskTest::getTestName);

using ChannelMaskAndCountParams = std::pair<aaudio_direction_t, aaudio_channel_mask_t>;
class AAudioStreamBuilderChannelMaskAndCountTest :
        public AAudioCtsBase, public ::testing::WithParamInterface<ChannelMaskAndCountParams> {
public:
    static std::string getTestName(
            const ::testing::TestParamInfo<ChannelMaskAndCountParams>& info) {
        std::stringstream ss;
        ss << (info.param.first == AAUDIO_DIRECTION_INPUT ? "INPUT_0x" : "OUTPUT_0x")
           << std::hex << info.param.second;
        return ss.str();
    }

protected:
    void testSetChannelMaskAndCount(aaudio_direction_t direction,
                                    aaudio_channel_mask_t channelMask,
                                    int32_t channelCount,
                                    bool channelMaskFirst);
};

void AAudioStreamBuilderChannelMaskAndCountTest::testSetChannelMaskAndCount(
        aaudio_direction_t direction, aaudio_channel_mask_t channelMask,
        int32_t channelCount, bool setChannelMaskFirst) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setDirection(aaudioBuilder, direction);
    if (setChannelMaskFirst) {
        AAudioStreamBuilder_setChannelMask(aaudioBuilder, channelMask);
        AAudioStreamBuilder_setChannelCount(aaudioBuilder, channelCount);
    } else {
        AAudioStreamBuilder_setChannelCount(aaudioBuilder, channelCount);
        AAudioStreamBuilder_setChannelMask(aaudioBuilder, channelMask);
    }
    AAudioStream *aaudioStream = nullptr;
    aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_NE(nullptr, aaudioStream);
    if (setChannelMaskFirst) {
        ASSERT_EQ(channelCount, AAudioStream_getChannelCount(aaudioStream));
        ASSERT_EQ(AAUDIO_UNSPECIFIED, AAudioStream_getChannelMask(aaudioStream));
    } else {
        // If channel mask is unspecified, stereo will be returned.
        ASSERT_EQ(channelMask == AAUDIO_UNSPECIFIED ? AAUDIO_CHANNEL_STEREO : channelMask,
                  AAudioStream_getChannelMask(aaudioStream));
        ASSERT_EQ(channelMask == AAUDIO_UNSPECIFIED ? 2 : __builtin_popcount(channelMask),
                  AAudioStream_getChannelCount(aaudioStream));
    }
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    if (aaudioStream != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    }
}

TEST_P(AAudioStreamBuilderChannelMaskAndCountTest, channelMaskAndCount) {
    const aaudio_direction_t direction = GetParam().first;
    if ((direction == AAUDIO_DIRECTION_OUTPUT && !deviceSupportsFeature(FEATURE_PLAYBACK)) ||
        (direction == AAUDIO_DIRECTION_INPUT && !deviceSupportsFeature(FEATURE_RECORDING))) {
        return;
    }
    const aaudio_channel_mask_t channelMask = GetParam().second;

    testSetChannelMaskAndCount(direction, channelMask,
                               2 /*channelCount*/, true /*setChannelMaskFirst*/);
    testSetChannelMaskAndCount(direction, channelMask,
                               2 /*channelCount*/, false /*setChannelMaskFirst*/);

    testSetChannelMaskAndCount(direction, AAUDIO_CHANNEL_5POINT1,
                               2 /*channelCount*/, true /*setChannelMaskFirst*/);
    testSetChannelMaskAndCount(direction, AAUDIO_CHANNEL_5POINT1,
                               2 /*channelCount*/, false /*setChannelMaskFirst*/);
}

INSTANTIATE_TEST_CASE_P(CMC, AAudioStreamBuilderChannelMaskAndCountTest,
        ::testing::Values(
                std::make_pair(AAUDIO_DIRECTION_OUTPUT, AAUDIO_CHANNEL_MONO),
                std::make_pair(AAUDIO_DIRECTION_OUTPUT, AAUDIO_CHANNEL_5POINT1),
                std::make_pair(AAUDIO_DIRECTION_OUTPUT, AAUDIO_UNSPECIFIED),
                std::make_pair(AAUDIO_DIRECTION_INPUT, AAUDIO_CHANNEL_MONO),
                std::make_pair(AAUDIO_DIRECTION_INPUT, AAUDIO_CHANNEL_5POINT1),
                std::make_pair(AAUDIO_DIRECTION_INPUT, AAUDIO_UNSPECIFIED)),
        &AAudioStreamBuilderChannelMaskAndCountTest::getTestName);

using CommonCombinationTestParams = std::tuple<aaudio_direction_t,
                                               aaudio_sharing_mode_t,
                                               aaudio_performance_mode_t,
                                               int32_t /*sample rate*/,
                                               aaudio_format_t,
                                               aaudio_channel_mask_t>;
enum {
    PARAM_DIRECTION = 0,
    PARAM_SHARING_MODE,
    PARAM_PERFORMANCE_MODE,
    PARAM_SAMPLE_RATE,
    PARAM_FORMAT,
    PARAM_CHANNEL_MASK
};
class AAudioStreamBuilderCommonCombinationTest :
        public AAudioCtsBase, public ::testing::WithParamInterface<CommonCombinationTestParams> {
  public:
    static std::string getTestName(
            const ::testing::TestParamInfo<CommonCombinationTestParams>& info) {
        std::stringstream ss;
        ss << (std::get<PARAM_DIRECTION>(info.param) == AAUDIO_DIRECTION_INPUT ? "INPUT_"
                                                                               : "OUTPUT_")
           << sharingModeToString(std::get<PARAM_SHARING_MODE>(info.param)) << "_"
           << performanceModeToString(std::get<PARAM_PERFORMANCE_MODE>(info.param)) << "_"
           << "sampleRate_" << std::get<PARAM_SAMPLE_RATE>(info.param) << "_"
           << "format_0x" << std::hex << std::get<PARAM_FORMAT>(info.param) << "_"
           << "channelMask_0x" << std::get<PARAM_CHANNEL_MASK>(info.param) << "";
        return ss.str();
    }
};

TEST_P(AAudioStreamBuilderCommonCombinationTest, openStream) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    const auto param = GetParam();
    AAudioStreamBuilder_setDirection(aaudioBuilder, std::get<PARAM_DIRECTION>(param));
    AAudioStreamBuilder_setSharingMode(aaudioBuilder, std::get<PARAM_SHARING_MODE>(param));
    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, std::get<PARAM_PERFORMANCE_MODE>(param));
    AAudioStreamBuilder_setSampleRate(aaudioBuilder, std::get<PARAM_SAMPLE_RATE>(param));
    AAudioStreamBuilder_setFormat(aaudioBuilder, std::get<PARAM_FORMAT>(param));
    AAudioStreamBuilder_setChannelMask(aaudioBuilder, std::get<PARAM_CHANNEL_MASK>(param));
    // All the test parameters all reasonable values with different combination. In that case,
    // it is expected that the opening will be successful.
    try_opening_audio_stream(aaudioBuilder, Expect::SUCCEED);
}

INSTANTIATE_TEST_CASE_P(CommonComb, AAudioStreamBuilderCommonCombinationTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_DIRECTION_OUTPUT, AAUDIO_DIRECTION_INPUT),
                ::testing::Values(AAUDIO_SHARING_MODE_SHARED, AAUDIO_SHARING_MODE_EXCLUSIVE),
                ::testing::Values(
                        AAUDIO_PERFORMANCE_MODE_NONE,
                        AAUDIO_PERFORMANCE_MODE_POWER_SAVING,
                        AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(// Sample rate
                        AAUDIO_UNSPECIFIED, 8000, 16000, 44100, 48000, 96000, 192000),
                ::testing::Values(
                        AAUDIO_UNSPECIFIED,
                        AAUDIO_FORMAT_PCM_I16,
                        AAUDIO_FORMAT_PCM_FLOAT),
                ::testing::Values(AAUDIO_CHANNEL_MONO, AAUDIO_CHANNEL_STEREO)),
        &AAudioStreamBuilderCommonCombinationTest::getTestName);
