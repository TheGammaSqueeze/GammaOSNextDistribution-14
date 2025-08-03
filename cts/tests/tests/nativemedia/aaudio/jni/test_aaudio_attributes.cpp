/*
 * Copyright (C) 2018 The Android Open Source Project
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

// Test AAudio attributes such as Usage, ContentType and InputPreset.

#include <cctype>
#include <stdio.h>
#include <sstream>
#include <string>
#include <unistd.h>
#include <utility>

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>

#include "utils.h"

constexpr int64_t kNanosPerSecond = 1000000000;
constexpr int kNumFrames = 256;
constexpr int kChannelCount = 2;

constexpr int32_t DONT_SET = -1000;
constexpr const char *DONT_SET_STR = "do_not_set";

#define IS_SPATIALIZED_FALSE (AAUDIO_UNSPECIFIED + 1)
#define IS_SPATIALIZED_TRUE  (AAUDIO_UNSPECIFIED + 2)

#define IS_PRIVACY_SENSITIVE_FALSE (AAUDIO_UNSPECIFIED + 1)
#define IS_PRIVACY_SENSITIVE_TRUE (AAUDIO_UNSPECIFIED + 2)

static void printPerformanceModeToTestName(aaudio_performance_mode_t performanceMode,
                                                  std::stringstream& ss) {
    ss << "perf_";
    switch (performanceMode) {
        case AAUDIO_PERFORMANCE_MODE_NONE:
            ss << "none";
            break;
        case AAUDIO_PERFORMANCE_MODE_POWER_SAVING:
            ss << "power_saving";
            break;
        case AAUDIO_PERFORMANCE_MODE_LOW_LATENCY:
            ss << "low_latency";
            break;
        default:
            ss << "unknown";
            break;
    }
}

static void printStrToTestName(const char* str, std::stringstream& ss) {
    if (str == nullptr) {
        ss << "null";
        return;
    }
    for (size_t i = 0; i < strlen(str); ++i) {
        ss << (isalnum(str[i]) ? str[i] : '_');
    }
}

class AAudioAttributesTestBase : public AAudioCtsBase {
protected:
    void checkAttributes();

    aaudio_performance_mode_t mPerfMode = AAUDIO_PERFORMANCE_MODE_NONE;
    aaudio_usage_t mUsage = DONT_SET;
    aaudio_content_type_t mContentType = DONT_SET;
    aaudio_spatialization_behavior_t mSpatializationBehavior = DONT_SET;
    int mIsContentSpatialized = DONT_SET;
    aaudio_input_preset_t mPreset = DONT_SET;
    aaudio_allowed_capture_policy_t mCapturePolicy = DONT_SET;
    int mIsPrivacySensitive = DONT_SET;
    aaudio_direction_t mDirection = AAUDIO_DIRECTION_OUTPUT;
    const char *mPackageName = DONT_SET_STR;
    const char *mAttributionTag = DONT_SET_STR;
};

void AAudioAttributesTestBase::checkAttributes() {
    if (mDirection == AAUDIO_DIRECTION_INPUT
            && !deviceSupportsFeature(FEATURE_RECORDING)) return;
    else if (mDirection == AAUDIO_DIRECTION_OUTPUT
            && !deviceSupportsFeature(FEATURE_PLAYBACK)) return;

    std::unique_ptr<float[]> buffer(new float[kNumFrames * kChannelCount]);

    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;

    // Use an AAudioStreamBuilder to contain requested parameters.
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

    // Request stream properties.
    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, mPerfMode);
    AAudioStreamBuilder_setDirection(aaudioBuilder, mDirection);

    // Set the attribute in the builder.
    if (mUsage != DONT_SET) {
        AAudioStreamBuilder_setUsage(aaudioBuilder, mUsage);
    }
    if (mContentType != DONT_SET) {
        AAudioStreamBuilder_setContentType(aaudioBuilder, mContentType);
    }
    if (mSpatializationBehavior != DONT_SET) {
        AAudioStreamBuilder_setSpatializationBehavior(aaudioBuilder, mSpatializationBehavior);
    }
    if (mIsContentSpatialized != DONT_SET) {
        AAudioStreamBuilder_setIsContentSpatialized(aaudioBuilder,
                                                    mIsContentSpatialized == IS_SPATIALIZED_TRUE);
    }
    if (mPreset != DONT_SET) {
        AAudioStreamBuilder_setInputPreset(aaudioBuilder, mPreset);
    }
    if (mCapturePolicy != DONT_SET) {
        AAudioStreamBuilder_setAllowedCapturePolicy(aaudioBuilder, mCapturePolicy);
    }
    if (mIsPrivacySensitive != DONT_SET) {
        AAudioStreamBuilder_setPrivacySensitive(aaudioBuilder,
                                                mIsPrivacySensitive == IS_PRIVACY_SENSITIVE_TRUE);
    }
    if (mPackageName != DONT_SET_STR) {
        AAudioStreamBuilder_setPackageName(aaudioBuilder, mPackageName);
    }
    if (mAttributionTag != DONT_SET_STR) {
        AAudioStreamBuilder_setAttributionTag(aaudioBuilder, mAttributionTag);
    }

    // Create an AAudioStream using the Builder.
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
    AAudioStreamBuilder_delete(aaudioBuilder);

    // Make sure we get the same attributes back from the stream.
    aaudio_usage_t expectedUsage =
            (mUsage == DONT_SET || mUsage == AAUDIO_UNSPECIFIED)
            ? AAUDIO_USAGE_MEDIA // default
            : mUsage;
    EXPECT_EQ(expectedUsage, AAudioStream_getUsage(aaudioStream));

    aaudio_content_type_t expectedContentType =
            (mContentType == DONT_SET || mContentType == AAUDIO_UNSPECIFIED)
            ? AAUDIO_CONTENT_TYPE_MUSIC // default
            : mContentType;
    EXPECT_EQ(expectedContentType, AAudioStream_getContentType(aaudioStream));

    if (mPerfMode == AAUDIO_PERFORMANCE_MODE_NONE) {
        aaudio_spatialization_behavior_t expectedBehavior =
                (mSpatializationBehavior == DONT_SET ||
                 mSpatializationBehavior == AAUDIO_UNSPECIFIED)
                        ? AAUDIO_SPATIALIZATION_BEHAVIOR_AUTO // default
                        : mSpatializationBehavior;
        EXPECT_EQ(expectedBehavior, AAudioStream_getSpatializationBehavior(aaudioStream));

        bool expectedIsContentSpatialized =
                (mIsContentSpatialized == DONT_SET)
                ? false //default
                : mIsContentSpatialized == IS_SPATIALIZED_TRUE;
        EXPECT_EQ(expectedIsContentSpatialized, AAudioStream_isContentSpatialized(aaudioStream));
    }

    aaudio_input_preset_t expectedPreset =
            (mPreset == DONT_SET || mPreset == AAUDIO_UNSPECIFIED)
            ? AAUDIO_INPUT_PRESET_VOICE_RECOGNITION // default
            : mPreset;
    EXPECT_EQ(expectedPreset, AAudioStream_getInputPreset(aaudioStream));

    aaudio_allowed_capture_policy_t expectedCapturePolicy =
            (mCapturePolicy == DONT_SET || mCapturePolicy == AAUDIO_UNSPECIFIED)
            ? AAUDIO_ALLOW_CAPTURE_BY_ALL // default
            : mCapturePolicy;
    EXPECT_EQ(expectedCapturePolicy, AAudioStream_getAllowedCapturePolicy(aaudioStream));

    bool expectedPrivacyMode =
            (mIsPrivacySensitive == DONT_SET)
                ? ((mPreset == AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION
                    || mPreset == AAUDIO_INPUT_PRESET_CAMCORDER) ? true : false)
                : (mIsPrivacySensitive == IS_PRIVACY_SENSITIVE_TRUE);
    EXPECT_EQ(expectedPrivacyMode, AAudioStream_isPrivacySensitive(aaudioStream));

    EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream));

    if (mDirection == AAUDIO_DIRECTION_INPUT) {
        EXPECT_EQ(kNumFrames,
                  AAudioStream_read(aaudioStream, buffer.get(), kNumFrames, kNanosPerSecond));
    } else {
        EXPECT_EQ(kNumFrames,
                  AAudioStream_write(aaudioStream, buffer.get(), kNumFrames, kNanosPerSecond));
    }

    EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream));

    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
}

/******************************************************************************
 * PackageNameTest
 *****************************************************************************/

using PackageNameParam = std::tuple<aaudio_performance_mode_t, const char*>;
class PackageNameTest : public AAudioAttributesTestBase,
                        public ::testing::WithParamInterface<PackageNameParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<PackageNameParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_package_name_";
        printStrToTestName(std::get<1>(info.param), ss);
        return ss.str();
    }

protected:
    void SetUp() override;
};

void PackageNameTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mPackageName = std::get<1>(GetParam());
}

TEST_P(PackageNameTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, PackageNameTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET_STR,
                                  "android.nativemedia.aaudio")),
                &PackageNameTest::getTestName);

/******************************************************************************
 * AttributionTagTest
 *****************************************************************************/

using AttributionTagParam = std::tuple<aaudio_performance_mode_t, const char*>;
class AttributionTagTest : public AAudioAttributesTestBase,
                           public ::testing::WithParamInterface<AttributionTagParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<AttributionTagParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_attribution_tag_";
        printStrToTestName(std::get<1>(info.param), ss);
        return ss.str();
    }

protected:
    void SetUp() override;
};

void AttributionTagTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mAttributionTag = std::get<1>(GetParam());
    mDirection = AAUDIO_DIRECTION_INPUT;
}

TEST_P(AttributionTagTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, AttributionTagTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET_STR,
                                  "validTag",
                                  nullptr)),
                &AttributionTagTest::getTestName);

/******************************************************************************
 * UsageTest
 *****************************************************************************/

using UsageParam = std::tuple<aaudio_performance_mode_t, aaudio_usage_t>;
class UsageTest : public AAudioAttributesTestBase,
                  public ::testing::WithParamInterface<UsageParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<UsageParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_usage_";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case AAUDIO_UNSPECIFIED:
                ss << "unspecified";
                break;
            case AAUDIO_USAGE_MEDIA:
                ss << "media";
                break;
            case AAUDIO_USAGE_VOICE_COMMUNICATION:
                ss << "voicecomm";
                break;
            case AAUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING:
                ss << "voicecommsig";
                break;
            case AAUDIO_USAGE_ALARM:
                ss << "alarm";
                break;
            case AAUDIO_USAGE_NOTIFICATION:
                ss << "notification";
                break;
            case AAUDIO_USAGE_NOTIFICATION_RINGTONE:
                ss << "notiringtone";
                break;
            case AAUDIO_USAGE_NOTIFICATION_EVENT:
                ss << "notievent";
                break;
            case AAUDIO_USAGE_ASSISTANCE_ACCESSIBILITY:
                ss << "assistacc";
                break;
            case AAUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
                ss << "assistnavgui";
                break;
            case AAUDIO_USAGE_ASSISTANCE_SONIFICATION:
                ss << "assistsoni";
                break;
            case AAUDIO_USAGE_GAME:
                ss << "game";
                break;
            case AAUDIO_USAGE_ASSISTANT:
                ss << "assistant";
                break;
            default:
                ss << "unknown";
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void UsageTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mUsage = std::get<1>(GetParam());
}

TEST_P(UsageTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, UsageTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  AAUDIO_UNSPECIFIED,
                                  AAUDIO_USAGE_MEDIA,
                                  AAUDIO_USAGE_VOICE_COMMUNICATION,
                                  AAUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING,
                                  AAUDIO_USAGE_ALARM,
                                  AAUDIO_USAGE_NOTIFICATION,
                                  AAUDIO_USAGE_NOTIFICATION_RINGTONE,
                                  AAUDIO_USAGE_NOTIFICATION_EVENT,
                                  AAUDIO_USAGE_ASSISTANCE_ACCESSIBILITY,
                                  AAUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                                  AAUDIO_USAGE_ASSISTANCE_SONIFICATION,
                                  AAUDIO_USAGE_GAME,
                                  AAUDIO_USAGE_ASSISTANT)),
                &UsageTest::getTestName);

/******************************************************************************
 * ContentTypeTest
 *****************************************************************************/

using ContentTypeParam = std::tuple<aaudio_performance_mode_t, aaudio_content_type_t>;
class ContentTypeTest : public AAudioAttributesTestBase,
                        public ::testing::WithParamInterface<ContentTypeParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<ContentTypeParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_content_type_";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case AAUDIO_UNSPECIFIED:
                ss << "unspecified";
                break;
            case AAUDIO_CONTENT_TYPE_SPEECH:
                ss << "speech";
                break;
            case AAUDIO_CONTENT_TYPE_MUSIC:
                ss << "music";
                break;
            case AAUDIO_CONTENT_TYPE_MOVIE:
                ss << "movie";
                break;
            case AAUDIO_CONTENT_TYPE_SONIFICATION:
                ss << "sonification";
                break;
            default:
                ss << "unknown";
                break;
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void ContentTypeTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mContentType = std::get<1>(GetParam());
}

TEST_P(ContentTypeTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, ContentTypeTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  AAUDIO_UNSPECIFIED,
                                  AAUDIO_CONTENT_TYPE_SPEECH,
                                  AAUDIO_CONTENT_TYPE_MUSIC,
                                  AAUDIO_CONTENT_TYPE_MOVIE,
                                  AAUDIO_CONTENT_TYPE_SONIFICATION)),
                &ContentTypeTest::getTestName);

/******************************************************************************
 * SpatializationBehaviorTest
 *****************************************************************************/

using SpatializationBehaviorParam = std::tuple<aaudio_performance_mode_t,
                                               aaudio_spatialization_behavior_t>;
class SpatializationBehaviorTest : public AAudioAttributesTestBase,
                           public ::testing::WithParamInterface<SpatializationBehaviorParam> {
public:
    static std::string getTestName(
            const ::testing::TestParamInfo<SpatializationBehaviorParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_spatialization_behavior_";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case AAUDIO_UNSPECIFIED:
                ss << "unspecified";
                break;
            case AAUDIO_SPATIALIZATION_BEHAVIOR_AUTO:
                ss << "auto";
                break;
            case AAUDIO_SPATIALIZATION_BEHAVIOR_NEVER:
                ss << "never";
                break;
            default:
                ss << "unknown";
                break;
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void SpatializationBehaviorTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mSpatializationBehavior = std::get<1>(GetParam());
}

TEST_P(SpatializationBehaviorTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, SpatializationBehaviorTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  AAUDIO_UNSPECIFIED,
                                  AAUDIO_SPATIALIZATION_BEHAVIOR_AUTO,
                                  AAUDIO_SPATIALIZATION_BEHAVIOR_NEVER)),
                &SpatializationBehaviorTest::getTestName);

/******************************************************************************
 * IsContentSpatializedTest
 *****************************************************************************/

using IsContentSpatializedParam = std::tuple<aaudio_performance_mode_t, int>;
class IsContentSpatializedTest : public AAudioAttributesTestBase,
                                 public ::testing::WithParamInterface<IsContentSpatializedParam> {
public:
    static std::string getTestName(
            const ::testing::TestParamInfo<IsContentSpatializedParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_is_content_spatialized_";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case IS_SPATIALIZED_TRUE:
                ss << "true";
                break;
            case IS_SPATIALIZED_FALSE:
                ss << "false";
                break;
            default:
                ss << "unknown";
                break;
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void IsContentSpatializedTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mIsContentSpatialized = std::get<1>(GetParam());
}

TEST_P(IsContentSpatializedTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, IsContentSpatializedTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  IS_SPATIALIZED_TRUE,
                                  IS_SPATIALIZED_FALSE)),
                &IsContentSpatializedTest::getTestName);

/******************************************************************************
 * InputPresetTest
 *****************************************************************************/

using InputPresetParam = std::tuple<aaudio_performance_mode_t, aaudio_input_preset_t>;
class InputPresetTest : public AAudioAttributesTestBase,
                        public ::testing::WithParamInterface<InputPresetParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<InputPresetParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_input_preset";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case AAUDIO_UNSPECIFIED:
                ss << "unspecified";
                break;
            case AAUDIO_INPUT_PRESET_GENERIC:
                ss << "generic";
                break;
            case AAUDIO_INPUT_PRESET_CAMCORDER:
                ss << "camcorder";
                break;
            case AAUDIO_INPUT_PRESET_VOICE_RECOGNITION:
                ss << "voice_recognition";
                break;
            case AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION:
                ss << "voice_communication";
                break;
            case AAUDIO_INPUT_PRESET_UNPROCESSED:
                ss << "unprocessed";
                break;
            case AAUDIO_INPUT_PRESET_VOICE_PERFORMANCE:
                ss << "voice_performance";
                break;
            case AAUDIO_INPUT_PRESET_SYSTEM_ECHO_REFERENCE:
                ss << "echo_reference_system";
                break;
            case AAUDIO_INPUT_PRESET_SYSTEM_HOTWORD:
                ss << "hotword_system";
                break;
            default:
                ss << "unknown";
                break;
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void InputPresetTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mPreset = std::get<1>(GetParam());
    mDirection = AAUDIO_DIRECTION_INPUT;
}

TEST_P(InputPresetTest, checkAttributes) {
    switch (mPreset) {
        case AAUDIO_INPUT_PRESET_SYSTEM_ECHO_REFERENCE:
            if (!isEchoReferenceSupported()) return;
            enableAudioOutputPermission();
            break;
        case AAUDIO_INPUT_PRESET_SYSTEM_HOTWORD:
            enableAudioHotwordPermission();
            break;
        default:
            break;
    }
    checkAttributes();
    disablePermissions();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, InputPresetTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  AAUDIO_UNSPECIFIED,
                                  AAUDIO_INPUT_PRESET_GENERIC,
                                  AAUDIO_INPUT_PRESET_CAMCORDER,
                                  AAUDIO_INPUT_PRESET_VOICE_RECOGNITION,
                                  AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION,
                                  AAUDIO_INPUT_PRESET_UNPROCESSED,
                                  AAUDIO_INPUT_PRESET_VOICE_PERFORMANCE,
                                  AAUDIO_INPUT_PRESET_SYSTEM_ECHO_REFERENCE,
                                  AAUDIO_INPUT_PRESET_SYSTEM_HOTWORD)),
                &InputPresetTest::getTestName);

/******************************************************************************
 * AllowCapturePolicyTest
 *****************************************************************************/

using AllowCapturePolicyParam = std::tuple<aaudio_performance_mode_t,
                                           aaudio_allowed_capture_policy_t>;
class AllowCapturePolicyTest : public AAudioAttributesTestBase,
                           public ::testing::WithParamInterface<AllowCapturePolicyParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<AllowCapturePolicyParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_allow_capture_policy_";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case AAUDIO_UNSPECIFIED:
                ss << "unspecified";
                break;
            case AAUDIO_ALLOW_CAPTURE_BY_ALL:
                ss << "all";
                break;
            case AAUDIO_ALLOW_CAPTURE_BY_SYSTEM:
                ss << "system";
                break;
            case AAUDIO_ALLOW_CAPTURE_BY_NONE:
                ss << "none";
                break;
            default:
                ss << "unknown";
                break;
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void AllowCapturePolicyTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mCapturePolicy = std::get<1>(GetParam());
}

TEST_P(AllowCapturePolicyTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, AllowCapturePolicyTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  AAUDIO_UNSPECIFIED,
                                  AAUDIO_ALLOW_CAPTURE_BY_ALL,
                                  AAUDIO_ALLOW_CAPTURE_BY_SYSTEM,
                                  AAUDIO_ALLOW_CAPTURE_BY_NONE)),
                &AllowCapturePolicyTest::getTestName);

/******************************************************************************
 * PrivacyModeTest
 *****************************************************************************/

using PrivacyModeParam = std::tuple<aaudio_performance_mode_t, int>;
class PrivacyModeTest : public AAudioAttributesTestBase,
                        public ::testing::WithParamInterface<PrivacyModeParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<PrivacyModeParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_privacy_mode_";
        switch (std::get<1>(info.param)) {
            case DONT_SET:
                ss << "do_not_set";
                break;
            case IS_PRIVACY_SENSITIVE_TRUE:
                ss << "true";
                break;
            case IS_PRIVACY_SENSITIVE_FALSE:
                ss << "false";
                break;
            default:
                ss << "unknown";
                break;
        }
        return ss.str();
    }

protected:
    void SetUp() override;
};

void PrivacyModeTest::SetUp() {
    mPerfMode = std::get<0>(GetParam());
    mIsPrivacySensitive = std::get<1>(GetParam());
    mDirection = AAUDIO_DIRECTION_INPUT;
}

TEST_P(PrivacyModeTest, checkAttributes) {
    checkAttributes();
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, PrivacyModeTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(DONT_SET,
                                  IS_PRIVACY_SENSITIVE_TRUE,
                                  IS_PRIVACY_SENSITIVE_FALSE)),
                &PrivacyModeTest::getTestName);

/******************************************************************************
 * SystemUsageTest
 *****************************************************************************/

using SystemUsageParam = std::tuple<aaudio_performance_mode_t, aaudio_usage_t>;
class SystemUsageTest : public AAudioCtsBase,
                        public ::testing::WithParamInterface<SystemUsageParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<SystemUsageParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_system_usage_";
        switch (std::get<1>(info.param)) {
            case AAUDIO_SYSTEM_USAGE_EMERGENCY:
                ss << "emergency";
                break;
            case AAUDIO_SYSTEM_USAGE_SAFETY:
                ss << "safety";
                break;
            case AAUDIO_SYSTEM_USAGE_VEHICLE_STATUS:
                ss << "vehicle_status";
                break;
            case AAUDIO_SYSTEM_USAGE_ANNOUNCEMENT:
                ss << "announcement";
                break;
            default:
                break;
        }
        return ss.str();
    }
};

TEST_P(SystemUsageTest, rejected) {
    const auto param = GetParam();
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;

    // Use an AAudioStreamBuilder to contain requested parameters.
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, std::get<0>(param));
    AAudioStreamBuilder_setUsage(aaudioBuilder, std::get<1>(param));

    aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);

    // Get failed status when trying to create an AAudioStream using the Builder. There are two
    // potential failures: one if the device doesn't support the system usage, and the  other
    // if it does but this test doesn't have the MODIFY_AUDIO_ROUTING permission required to
    // use it.
    ASSERT_TRUE(result == AAUDIO_ERROR_ILLEGAL_ARGUMENT
            || result == AAUDIO_ERROR_INTERNAL);
    AAudioStreamBuilder_delete(aaudioBuilder);
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, SystemUsageTest,
        ::testing::Combine(
                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                ::testing::Values(AAUDIO_SYSTEM_USAGE_EMERGENCY,
                                  AAUDIO_SYSTEM_USAGE_SAFETY,
                                  AAUDIO_SYSTEM_USAGE_VEHICLE_STATUS,
                                  AAUDIO_SYSTEM_USAGE_ANNOUNCEMENT)),
                &SystemUsageTest::getTestName);

/******************************************************************************
 * PermissionGatedInputPresetTest
 *****************************************************************************/

using PermissionGatedInputPresetParam = std::tuple<aaudio_performance_mode_t,
                                                   aaudio_input_preset_t>;
class PermissionGatedInputPresetTest : public AAudioCtsBase,
                        public ::testing::WithParamInterface<PermissionGatedInputPresetParam> {
public:
    static std::string getTestName(
            const ::testing::TestParamInfo<PermissionGatedInputPresetParam>& info) {
        std::stringstream ss;
        printPerformanceModeToTestName(std::get<0>(info.param), ss);
        ss << "_input_preset_";
        switch (std::get<1>(info.param)) {
            case AAUDIO_INPUT_PRESET_SYSTEM_ECHO_REFERENCE:
                ss << "echo_reference_system";
                break;
            case AAUDIO_INPUT_PRESET_SYSTEM_HOTWORD:
                ss << "hotword_system";
                break;
            default:
                break;
        }
        return ss.str();
    }
};

TEST_P(PermissionGatedInputPresetTest, openStreamFails) {
    if (!deviceSupportsFeature(FEATURE_RECORDING)) return;

    disablePermissions();

    std::unique_ptr<float[]> buffer(new float[kNumFrames * kChannelCount]);

    const auto param = GetParam();
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;

    // Use an AAudioStreamBuilder to contain requested parameters.
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, std::get<0>(param));
    AAudioStreamBuilder_setInputPreset(aaudioBuilder, std::get<1>(param));
    AAudioStreamBuilder_setDirection(aaudioBuilder, AAUDIO_DIRECTION_INPUT);

    aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);

    // openStream should not return AAUDIO_OK. If it does, close the stream.
    if (result == AAUDIO_OK) {
        AAudioStream_close(aaudioStream);
    }
    AAudioStreamBuilder_delete(aaudioBuilder);

    // The test should not have the correct permission required to use the preset.
    ASSERT_NE(result, AAUDIO_OK);
}

INSTANTIATE_TEST_CASE_P(AAudioTestAttributes, PermissionGatedInputPresetTest,
                        ::testing::Combine(
                                ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                                  AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                                                  AAUDIO_PERFORMANCE_MODE_POWER_SAVING),
                                ::testing::Values(AAUDIO_INPUT_PRESET_SYSTEM_ECHO_REFERENCE,
                                                  AAUDIO_INPUT_PRESET_SYSTEM_HOTWORD)),
                        &PermissionGatedInputPresetTest::getTestName);
