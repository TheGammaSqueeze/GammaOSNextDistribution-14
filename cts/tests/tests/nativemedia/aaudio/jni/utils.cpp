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

#define LOG_TAG "AAudioTest"

#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>

#include <android/binder_ibinder_jni.h>
#include <android/binder_status.h>
#include <android/log.h>
#include <gtest/gtest.h>
#include <nativetesthelper_jni/utils.h>

#include "test_aaudio.h"
#include "utils.h"

using ::ndk::SpAIBinder;
using ::ndk::ScopedAIBinder_DeathRecipient;

int64_t getNanoseconds(clockid_t clockId) {
    struct timespec time;
    int result = clock_gettime(clockId, &time);
    if (result < 0) {
        return -errno;
    }
    return (time.tv_sec * NANOS_PER_SECOND) + time.tv_nsec;
}

const char* performanceModeToString(aaudio_performance_mode_t mode) {
    switch (mode) {
        case AAUDIO_PERFORMANCE_MODE_NONE: return "DEFAULT";
        case AAUDIO_PERFORMANCE_MODE_POWER_SAVING: return "POWER_SAVING";
        case AAUDIO_PERFORMANCE_MODE_LOW_LATENCY: return "LOW_LATENCY";
    }
    return "UNKNOWN";
}

const char* sharingModeToString(aaudio_sharing_mode_t mode) {
    switch (mode) {
        case AAUDIO_SHARING_MODE_SHARED: return "SHARED";
        case AAUDIO_SHARING_MODE_EXCLUSIVE: return "EXCLUSIVE";
    }
    return "UNKNOWN";
}

// Runs "pm list features" and attempts to find the specified feature in its output.
bool deviceSupportsFeature(const char* feature) {
    bool hasFeature = false;
    FILE *p = popen("/system/bin/pm list features", "re");
    if (p) {
      char* line = NULL;
      size_t len = 0;
      while (getline(&line, &len, p) > 0) {
          if (strstr(line, feature)) {
              hasFeature = true;
              break;
          }
      }
      pclose(p);
    } else {
        __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, "popen failed: %d", errno);
        _exit(EXIT_FAILURE);
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Feature %s: %ssupported",
            feature, hasFeature ? "" : "not ");
    return hasFeature;
}

// These periods are quite generous. They are not designed to put
// any restrictions on the implementation, but only to ensure sanity.
// Use int64_t because 96000 * 30000 is close to int32_t limits.
const std::map<aaudio_performance_mode_t, int64_t> StreamBuilderHelper::sMaxFramesPerBurstMs =
{ { AAUDIO_PERFORMANCE_MODE_NONE, 128 },
  { AAUDIO_PERFORMANCE_MODE_POWER_SAVING, 30 * 1000 },
  { AAUDIO_PERFORMANCE_MODE_LOW_LATENCY, 40 } };

const std::unordered_set<aaudio_format_t> StreamBuilderHelper::sValidStreamFormats =
        {AAUDIO_FORMAT_PCM_I16, AAUDIO_FORMAT_PCM_FLOAT, AAUDIO_FORMAT_PCM_I24_PACKED,
         AAUDIO_FORMAT_PCM_I32, AAUDIO_FORMAT_IEC61937};

StreamBuilderHelper::StreamBuilderHelper(
        aaudio_direction_t direction, int32_t sampleRate,
        int32_t channelCount, aaudio_format_t dataFormat,
        aaudio_sharing_mode_t sharingMode, aaudio_performance_mode_t perfMode)
        : mDirection{direction},
          mRequested{sampleRate, channelCount, dataFormat, sharingMode, perfMode},
          mActual{0, 0, AAUDIO_FORMAT_INVALID, -1, -1}, mFramesPerBurst{-1},
          mBuilder{nullptr}, mStream{nullptr} {}

StreamBuilderHelper::~StreamBuilderHelper() {
    close();
}

void StreamBuilderHelper::initBuilder() {
    ASSERT_EQ(1U, sMaxFramesPerBurstMs.count(mRequested.perfMode));

    // Use an AAudioStreamBuilder to define the stream.
    aaudio_result_t result = AAudio_createStreamBuilder(&mBuilder);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_TRUE(mBuilder != nullptr);

    // Request stream properties.
    AAudioStreamBuilder_setDeviceId(mBuilder, AAUDIO_UNSPECIFIED);
    AAudioStreamBuilder_setDirection(mBuilder, mDirection);
    AAudioStreamBuilder_setSampleRate(mBuilder, mRequested.sampleRate);
    AAudioStreamBuilder_setChannelCount(mBuilder, mRequested.channelCount);
    AAudioStreamBuilder_setFormat(mBuilder, mRequested.dataFormat);
    AAudioStreamBuilder_setSharingMode(mBuilder, mRequested.sharingMode);
    AAudioStreamBuilder_setPerformanceMode(mBuilder, mRequested.perfMode);
}

// Needs to be a 'void' function due to ASSERT requirements.
void StreamBuilderHelper::createAndVerifyStream(bool *success) {
    *success = false;

    aaudio_result_t result = AAudioStreamBuilder_openStream(mBuilder, &mStream);
    if (mRequested.sharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE && result != AAUDIO_OK) {
        __android_log_write(ANDROID_LOG_WARN, LOG_TAG, "Could not open a stream in EXCLUSIVE mode");
        return;
    }
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_TRUE(mStream != nullptr);
    ASSERT_EQ(AAUDIO_STREAM_STATE_OPEN, AAudioStream_getState(mStream));
    ASSERT_EQ(mDirection, AAudioStream_getDirection(mStream));

    mActual.sharingMode = AAudioStream_getSharingMode(mStream);
    if (mActual.sharingMode != mRequested.sharingMode) {
        // Since we are covering all possible values, the "actual" mode
        // will also be tested, so no need to run the same test twice.
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Sharing mode %s is not available",
                sharingModeToString(mRequested.sharingMode));
        return;
    }

    // Check to see what kind of stream we actually got.
    mActual.sampleRate = AAudioStream_getSampleRate(mStream);
    ASSERT_GE(mActual.sampleRate, kMinValidSampleRate);
    ASSERT_LE(mActual.sampleRate, kMaxValidSampleRate);

    ASSERT_GE(AAudioStream_getHardwareSampleRate(mStream), kMinValidSampleRate);
    ASSERT_LE(AAudioStream_getHardwareSampleRate(mStream), kMaxValidSampleRate);

    mActual.channelCount = AAudioStream_getChannelCount(mStream);
    ASSERT_GE(mActual.channelCount, kMinValidChannelCount);
    ASSERT_LE(mActual.channelCount, kMaxValidChannelCount);

    ASSERT_GE(AAudioStream_getHardwareChannelCount(mStream), kMinValidChannelCount);
    ASSERT_LE(AAudioStream_getHardwareChannelCount(mStream), kMaxValidChannelCount);

    mActual.dataFormat = AAudioStream_getFormat(mStream);
    if (mRequested.dataFormat != AAUDIO_FORMAT_UNSPECIFIED) {
        ASSERT_EQ(mRequested.dataFormat, mActual.dataFormat);
    }

    ASSERT_NE(AAudioStream_getHardwareFormat(mStream), AAUDIO_FORMAT_UNSPECIFIED);
    ASSERT_NE(AAudioStream_getHardwareFormat(mStream), AAUDIO_FORMAT_INVALID);
    ASSERT_TRUE(sValidStreamFormats.find(AAudioStream_getHardwareFormat(mStream)) !=
            sValidStreamFormats.end());

    mActual.perfMode = AAudioStream_getPerformanceMode(mStream);
    if (mRequested.perfMode != AAUDIO_PERFORMANCE_MODE_NONE
            && mRequested.perfMode != mActual.perfMode) {
        // Since we are covering all possible values, the "actual" mode
        // will also be tested, so no need to run the same test twice.
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Performance mode %s is not available",
                performanceModeToString(mRequested.sharingMode));
        return;
    }

    mFramesPerBurst = AAudioStream_getFramesPerBurst(mStream);
    ASSERT_GE(mFramesPerBurst, 16);
    const int32_t maxFramesPerBurst =
            mActual.sampleRate * sMaxFramesPerBurstMs.at(mActual.perfMode) / MILLIS_PER_SECOND;
    ASSERT_LE(mFramesPerBurst, maxFramesPerBurst);

    int32_t actualBufferSize = AAudioStream_getBufferSizeInFrames(mStream);
    ASSERT_GT(actualBufferSize, 0);
    ASSERT_GT(AAudioStream_setBufferSizeInFrames(mStream, actualBufferSize), 0);

    *success = true;
}

void StreamBuilderHelper::close() {
    if (mBuilder != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(mBuilder));
    }
    if (mStream != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(mStream));
    }
}

void StreamBuilderHelper::streamCommand(
        StreamCommand cmd, aaudio_stream_state_t fromState, aaudio_stream_state_t toState) {
    ASSERT_EQ(AAUDIO_OK, cmd(mStream));
    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNINITIALIZED;
    ASSERT_EQ(AAUDIO_OK,
            AAudioStream_waitForStateChange(mStream, fromState, &state, DEFAULT_STATE_TIMEOUT));
    ASSERT_EQ(toState, state);
}

InputStreamBuilderHelper::InputStreamBuilderHelper(
        aaudio_sharing_mode_t requestedSharingMode,
        aaudio_performance_mode_t requestedPerfMode,
        aaudio_format_t requestedFormat,
        int32_t requestedSampleRate)
        : StreamBuilderHelper{AAUDIO_DIRECTION_INPUT,
            requestedSampleRate, 1, requestedFormat, requestedSharingMode, requestedPerfMode} {}


OutputStreamBuilderHelper::OutputStreamBuilderHelper(
        aaudio_sharing_mode_t requestedSharingMode,
        aaudio_performance_mode_t requestedPerfMode,
        aaudio_format_t requestedFormat,
        int32_t requestedSampleRate)
        : StreamBuilderHelper{AAUDIO_DIRECTION_OUTPUT,
            requestedSampleRate, 2, requestedFormat, requestedSharingMode, requestedPerfMode} {}

void OutputStreamBuilderHelper::initBuilder() {
    StreamBuilderHelper::initBuilder();
    AAudioStreamBuilder_setBufferCapacityInFrames(mBuilder, kBufferCapacityFrames);
}

AAudioExtensions::AAudioExtensions()
    : mMMapSupported(isPolicyEnabled(getMMapPolicyProperty()))
    , mMMapExclusiveSupported(isPolicyEnabled(getIntegerProperty(
            "aaudio.mmap_exclusive_policy", AAUDIO_POLICY_UNSPECIFIED))) {
    loadLibrary();
}

int AAudioExtensions::getIntegerProperty(const char *name, int defaultValue) {
    int result = defaultValue;
    char valueText[PROP_VALUE_MAX] = {0};
    if (__system_property_get(name, valueText) != 0) {
        result = atoi(valueText);
    }
    return result;
}

// This should only be called once from the constructor.
bool AAudioExtensions::loadLibrary() {
    mLibHandle = dlopen(LIB_AAUDIO_NAME, 0);
    if (mLibHandle == nullptr) {
        //LOGI("%s() could not find " LIB_AAUDIO_NAME, __func__);
        return false;
    }

    mAAudioStream_isMMap = (bool (*)(AAudioStream *stream))
            dlsym(mLibHandle, FUNCTION_IS_MMAP);
    if (mAAudioStream_isMMap == nullptr) {
        //LOGI("%s() could not find " FUNCTION_IS_MMAP, __func__);
        return false;
    }

    mAAudio_setMMapPolicy = (int32_t (*)(aaudio_policy_t policy))
            dlsym(mLibHandle, FUNCTION_SET_MMAP_POLICY);
    if (mAAudio_setMMapPolicy == nullptr) {
        //LOGI("%s() could not find " FUNCTION_SET_MMAP_POLICY, __func__);
        return false;
    }

    mAAudio_getMMapPolicy = (aaudio_policy_t (*)())
            dlsym(mLibHandle, FUNCTION_GET_MMAP_POLICY);
    if (mAAudio_getMMapPolicy == nullptr) {
        //LOGI("%s() could not find " FUNCTION_GET_MMAP_POLICY, __func__);
        return false;
    }

    mFunctionsLoaded = true;
    return mFunctionsLoaded;
}

static std::atomic_int sAudioServerCrashCount = 0;
static int sLastAudioServerCrashCount = 0;

void onBinderDied(void* /*cookie*/) {
    sAudioServerCrashCount += 1;
    AudioServerCrashMonitor::getInstance().onAudioServerCrash();
}

AudioServerCrashMonitor::AudioServerCrashMonitor()
        : mDeathRecipient{ScopedAIBinder_DeathRecipient(
                AIBinder_DeathRecipient_new(onBinderDied))} {
    linkToDeath();
}

AudioServerCrashMonitor::~AudioServerCrashMonitor() {
    if (mDeathRecipientLinked) {
        AIBinder_unlinkToDeath(mAudioFlinger.get(), mDeathRecipient.get(), nullptr /* cookie */);
    }
}

void AudioServerCrashMonitor::linkToDeath() {
    if (getAudioFlinger().get() == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to get audio flinger");
    } else {
        auto ret = AIBinder_linkToDeath(mAudioFlinger.get(), mDeathRecipient.get(),
                                        nullptr /* cookie */);
        if (ret != STATUS_OK) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to link to death, err=%d", ret);
        } else {
            mDeathRecipientLinked = true;
        }
    }
}

void AudioServerCrashMonitor::onAudioServerCrash() {
    mDeathRecipientLinked = false;
    mAudioFlinger.set(nullptr);
}

namespace {

JNIEnv* getJNIEnv() {
    JavaVM* vm = GetJavaVM();
    EXPECT_NE(nullptr, vm);
    JNIEnv* env = nullptr;
    jint attach = vm->AttachCurrentThread(&env, nullptr);
    EXPECT_EQ(JNI_OK, attach);
    EXPECT_NE(nullptr, env);
    return env;
}

#define CALL_JAVA_STATIC_METHOD(_jtype, _jname)                                        \
    _jtype callJavaStatic##_jname##Function(                                           \
            JNIEnv* env, const char* className,                                        \
            const char* funcName, const char* signature, ...) {                        \
        _jtype result;                                                                 \
        if (env == nullptr) {                                                          \
            env = getJNIEnv();                                                         \
        }                                                                              \
        jclass cl = env->FindClass(className);                                         \
        EXPECT_NE(nullptr, cl);                                                        \
        jmethodID mid = env->GetStaticMethodID(cl, funcName, signature);               \
        EXPECT_NE(nullptr, mid);                                                       \
        va_list args;                                                                  \
        va_start(args, signature);                                                     \
        result = env->CallStatic##_jname##Method(cl, mid, args);                       \
        va_end(args);                                                                  \
        return result;                                                                 \
    }                                                                                  \

CALL_JAVA_STATIC_METHOD(jobject, Object)
CALL_JAVA_STATIC_METHOD(jboolean, Boolean)

void callJavaStaticVoidFunction(
        JNIEnv* env, const char* className,
        const char* funcName, const char* signature, ...) {
    if (env == nullptr) {
        env = getJNIEnv();
    }
    jclass cl = env->FindClass(className);
    EXPECT_NE(nullptr, cl);
    jmethodID mid = env->GetStaticMethodID(cl, funcName, signature);
    EXPECT_NE(nullptr, mid);
    va_list args;
    va_start(args, signature);
    env->CallStaticVoidMethod(cl, mid, args);
    va_end(args);
}

} // namespace

SpAIBinder AudioServerCrashMonitor::getAudioFlinger() {
    if (mAudioFlinger.get() != nullptr) {
        return mAudioFlinger;
    }

    JNIEnv *env = getJNIEnv();
    jobject object = callJavaStaticObjectFunction(
            env, "android/nativemedia/aaudio/AAudioTests",
            "getAudioFlinger", "()Landroid/os/IBinder;");
    EXPECT_NE(nullptr, object);

    mAudioFlinger = SpAIBinder(AIBinder_fromJavaBinder(env, object));
    return mAudioFlinger;
}

void AAudioCtsBase::SetUp() {
    checkIfAudioServerCrash();
}

void AAudioCtsBase::TearDown() {
    checkIfAudioServerCrash();
}

void AAudioCtsBase::checkIfAudioServerCrash() {
    EXPECT_EQ(sLastAudioServerCrashCount, sAudioServerCrashCount);
    sLastAudioServerCrashCount = sAudioServerCrashCount;
    EXPECT_TRUE(AudioServerCrashMonitor::getInstance().isDeathRecipientLinked());
    if (!AudioServerCrashMonitor::getInstance().isDeathRecipientLinked()) {
        AudioServerCrashMonitor::getInstance().linkToDeath();
    }
}

bool isIEC61937Supported() {
    return (bool) callJavaStaticBooleanFunction(
            nullptr, "android/nativemedia/aaudio/AAudioTests", "isIEC61937Supported", "()Z");
}

bool isEchoReferenceSupported() {
    return (bool) callJavaStaticBooleanFunction(
            nullptr, "android/nativemedia/aaudio/AAudioTests", "isEchoReferenceSupported", "()Z");
}

void enableAudioOutputPermission() {
    callJavaStaticVoidFunction(
            nullptr, "android/nativemedia/aaudio/AAudioTests", "enableAudioOutputPermission",
            "()V");
}

void enableAudioHotwordPermission() {
    callJavaStaticVoidFunction(
            nullptr, "android/nativemedia/aaudio/AAudioTests", "enableAudioHotwordPermission",
            "()V");
}

void disablePermissions() {
    callJavaStaticVoidFunction(
            nullptr, "android/nativemedia/aaudio/AAudioTests", "disablePermissions", "()V");
}