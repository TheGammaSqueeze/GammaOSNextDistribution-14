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

#include <cassert>
#include <cstring>
#include <jni.h>
#include <stdint.h>

// If the NDK is before O then define this in your build
// so that AAudio.h will not be included.
// #define OBOE_NO_INCLUDE_AAUDIO

// Oboe Includes
//#include <oboe/Oboe.h>
#include <AAudioExtensions.h>

#include "NativeAudioAnalyzer.h"

extern "C" {

//
// com.android.cts.verifier.audio.NativeAnalyzerThread
//
JNIEXPORT jlong JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_openAudio
  (JNIEnv * /*env */, jobject /* obj */, jint inputDeviceId, jint outputDeviceId) {
    // It is OK to use a raw pointer here because the pointer will be passed back
    // to Java and only used from one thread.
    // Java then deletes it from that same thread by calling _closeAudio() below.
    NativeAudioAnalyzer * analyzer = new NativeAudioAnalyzer();
    aaudio_result_t result = analyzer->openAudio(inputDeviceId, outputDeviceId);
    if (result != AAUDIO_OK) {
        delete analyzer;
        analyzer = nullptr;
    }
    return (jlong) analyzer;
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_startAudio
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    int result = AAUDIO_ERROR_NULL;
    if (analyzer != nullptr) {
        result = analyzer->startAudio();
    }
    return result;
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_stopAudio
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->stopAudio();
    }
    return AAUDIO_ERROR_NULL;
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_closeAudio
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    int result = AAUDIO_ERROR_NULL;
    if (analyzer != nullptr) {
        result = analyzer->closeAudio();
        delete analyzer;
    }
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_isRecordingComplete
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->isRecordingComplete();
    }
    return false;
}

JNIEXPORT jboolean JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_isLowlatency
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->isLowLatencyStream();
    }
    return false;
}

JNIEXPORT jboolean JNICALL
    Java_com_android_cts_verifier_audio_NativeAnalyzerThread_has24BitHardwareSupport
        (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->has24BitHardwareSupport();
    }
    return false;
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_NativeAnalyzerThread_getHardwareFormat
        (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->getHardwareFormat();
    }
    return (jint) AAUDIO_FORMAT_INVALID;
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_getError
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return (jint) analyzer->getError();
    }
    return (jint) AAUDIO_ERROR_NULL;
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_analyze
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->analyze();
    }
    return AAUDIO_ERROR_NULL;
}

JNIEXPORT jdouble JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_getLatencyMillis
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->getLatencyMillis();
    }
    return -1.0;
}

JNIEXPORT jdouble JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_getConfidence
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->getConfidence();
    }
    return 0.0;
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_NativeAnalyzerThread_getSampleRate
  (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->getSampleRate();
    }
    return 0;
}

JNIEXPORT jdouble JNICALL
        Java_com_android_cts_verifier_audio_NativeAnalyzerThread_measureTimestampLatencyMillis
        (JNIEnv *env __unused, jobject obj __unused, jlong pAnalyzer) {
    NativeAudioAnalyzer * analyzer = (NativeAudioAnalyzer *) pAnalyzer;
    if (analyzer != nullptr) {
        return analyzer->measureTimestampLatencyMillis();
    }
    return 0;
}

//
// com.android.cts.verifier.audio.audiolib.AudioUtils
//
JNIEXPORT jboolean JNICALL
    Java_com_android_cts_verifier_audio_audiolib_AudioUtils_isMMapSupported(JNIEnv *env __unused) {

    return oboe::AAudioExtensions().isMMapSupported();
}

JNIEXPORT jboolean JNICALL
    Java_com_android_cts_verifier_audio_audiolib_AudioUtils_isMMapExclusiveSupported(
        JNIEnv *env __unused) {

    return oboe::AAudioExtensions().isMMapExclusiveSupported();
}

}
