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

#include "AudioSink.h"

//TODO - Probably wrap the JNI handling in a class with a pointer held in the Java Object
// so as to support multiple instances... maybe.

// JNI Stuff
extern "C" {

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_NativeAudioSink_initN(JNIEnv * env , jobject thiz,
        jlong native_sink_ptr , jint num_frames, jint num_chans ) {
    // this is in the wrong place, or rather we need an init() method of AudioSink to call.
    AudioSink* sink = (AudioSink*)native_sink_ptr;
    sink->init(num_frames, num_chans);
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_NativeAudioSink_startN(JNIEnv *env, jobject thiz, jlong native_sink_ptr) {
    AudioSink* sink = (AudioSink*)native_sink_ptr;
    sink->start();
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_NativeAudioSink_stopN(JNIEnv *env, jobject thiz, jlong native_sink_ptr) {
    AudioSink* sink = (AudioSink*)native_sink_ptr;
    sink->stop();
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_NativeAudioSink_pushN(
        JNIEnv *env, jobject thiz, jlong native_sink_ptr,
        jfloatArray audio_data, jint num_frames, jint num_chans) {
        AudioSink * audioSink = (AudioSink*)native_sink_ptr;

    // convert to float[]
    float* nativeAudioData = env->GetFloatArrayElements(audio_data, 0);

    audioSink->push(nativeAudioData, num_frames, num_chans);
}

} // extern "C"
