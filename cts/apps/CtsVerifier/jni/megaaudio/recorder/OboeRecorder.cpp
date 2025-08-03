/*
 * Copyright 2020 The Android Open Source Project
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
#include <android/log.h>

#include "OboeRecorder.h"

#include "AudioSink.h"

static const char * const TAG = "OboeRecorder(native)";

using namespace oboe;

constexpr int32_t kBufferSizeInBursts = 2; // Use 2 bursts as the buffer size (double buffer)

OboeRecorder::OboeRecorder(AudioSink* sink, int32_t subtype)
        : Recorder(sink, subtype),
          mInputPreset(-1)
{}

//
// State
//
StreamBase::Result OboeRecorder::setupStream(int32_t channelCount, int32_t sampleRate,
                        int32_t performanceMode, int32_t sharingMode, int32_t routeDeviceId,
                        int32_t inputPreset)
{
    //TODO much of this could be pulled up into OboeStream.

    std::lock_guard<std::mutex> lock(mStreamLock);

    oboe::Result result = oboe::Result::ErrorInternal;
    if (mAudioStream != nullptr) {
        return ERROR_INVALID_STATE;
    } else {
        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        mRouteDeviceId = routeDeviceId;
        mInputPreset = inputPreset;

        // Create an audio stream
        AudioStreamBuilder builder;
        builder.setChannelCount(mChannelCount);
        builder.setSampleRate(mSampleRate);
        builder.setCallback(this);
        if (mInputPreset != DEFAULT_INPUT_NONE) {
            builder.setInputPreset((enum InputPreset)mInputPreset);
        }
        builder.setPerformanceMode((PerformanceMode) performanceMode);
        builder.setSharingMode((SharingMode) sharingMode);
        builder.setSampleRateConversionQuality(SampleRateConversionQuality::None);
        builder.setDirection(Direction::Input);

        if (mRouteDeviceId != -1) {
            builder.setDeviceId(mRouteDeviceId);
        }

        if (mSubtype == SUB_TYPE_OBOE_AAUDIO) {
            builder.setAudioApi(AudioApi::AAudio);
        } else if (mSubtype == SUB_TYPE_OBOE_OPENSL_ES) {
            builder.setAudioApi(AudioApi::OpenSLES);
        }

        result = builder.openStream(mAudioStream);
        if (result != oboe::Result::OK){
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "openStream failed. Error: %s", convertToText(result));
        } else {
            mBufferSizeInFrames = mAudioStream->getFramesPerBurst();
            mAudioSink->init(mBufferSizeInFrames, mChannelCount);
        }
    }

    return OboeErrorToMegaAudioError(result);
}

StreamBase::Result OboeRecorder::startStream() {
    StreamBase::Result result = Recorder::startStream();
    if (result == OK) {
        mAudioSink->start();
    }
    return result;
}

oboe::DataCallbackResult OboeRecorder::onAudioReady(
        oboe::AudioStream *audioStream, void *audioData, int numFrames) {
    mAudioSink->push((float*)audioData, numFrames, mChannelCount);
    return oboe::DataCallbackResult::Continue;
}

#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_allocNativeRecorder(JNIEnv *env, jobject thiz, jlong native_audio_sink, jint recorderSubtype) {
    OboeRecorder* recorder = new OboeRecorder((AudioSink*)native_audio_sink, recorderSubtype);
    return (jlong)recorder;
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getBufferFrameCountN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->getNumBufferFrames();
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_setInputPresetN(
        JNIEnv *env, jobject thiz, jlong native_recorder, jint input_preset) {
    ((OboeRecorder*)native_recorder)->setInputPreset(input_preset);
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_setupStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder, jint channel_count, jint sample_rate,
        jint performanceMode, jint sharingMode, jint route_device_id, jint input_preset) {
    return ((OboeRecorder*)native_recorder)->setupStream(
            channel_count, sample_rate, performanceMode, sharingMode, route_device_id,
            input_preset);
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_teardownStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->teardownStream();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_startStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder, jint recorder_subtype) {
    return ((OboeRecorder*)native_recorder)->startStream();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_stopN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->stopStream();
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_isRecordingN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    OboeRecorder* nativeRecorder = ((OboeRecorder*)native_recorder);
    return nativeRecorder->isRecording();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getNumBufferFramesN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    OboeRecorder* nativeRecorder = ((OboeRecorder*)native_recorder);
    return nativeRecorder->getNumBufferFrames();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getRoutedDeviceIdN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->getRoutedDeviceId();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getSharingModeN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->getSharingMode();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getChannelCountN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->getChannelCount();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getStreamStateN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return (int)((OboeRecorder*)(native_recorder))->getState();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getLastErrorCallbackResultN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return (int)((OboeRecorder*)(native_recorder))->getLastErrorCallbackResult();
}

}   // extern "C"
