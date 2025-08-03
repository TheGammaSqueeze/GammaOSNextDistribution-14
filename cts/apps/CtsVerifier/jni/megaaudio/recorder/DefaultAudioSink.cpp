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
#include <jni.h>

#include <android/log.h>

#include "DefaultAudioSink.h"

static const char * const TAG = "DefaultAudioSink";

void DefaultAudioSink::start() {

}

void DefaultAudioSink::stop() {

}

void DefaultAudioSink::push(float* audioData, int numChannels, int numFrames) {
    // __android_log_print(ANDROID_LOG_INFO, TAG, "process()");
}

extern "C" {
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_sinks_NopAudioSinkProvider_allocOboeSinkN(
        JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Java_org_hyphonate_megaaudio_recorder_sinks_NopAudioSinkProvider_allocOboeSinkN");
    DefaultAudioSink* sink = new DefaultAudioSink();
    return (jlong)sink;
}
} // extern "C"
