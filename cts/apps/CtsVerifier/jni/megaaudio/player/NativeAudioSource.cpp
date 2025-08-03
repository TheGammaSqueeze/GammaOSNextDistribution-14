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

#include "AudioSource.h"

//TODO - Probably wrap the JNI handling in a class with a pointer held in the Java Object
// so as to support multiple instances... maybe.

// JNI Stuff
static float* sAudioBuffer;

extern "C" {

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_player_NativeAudioSource_initN(
        JNIEnv *env, jobject thiz, jlong native_source_ptr, jint num_frames, jint num_chans) {
    sAudioBuffer = new float[num_frames * num_chans];
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_player_NativeAudioSource_setSampleRateN(
        JNIEnv *env, jobject thiz, jlong native_source_ptr, jint sample_rate) {
    AudioSource* audioSource = (AudioSource*)native_source_ptr;
    audioSource->setSampleRate(sample_rate);
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_player_NativeAudioSource_setFreqN(
        JNIEnv *env, jobject thiz, jlong native_source_ptr, jfloat freq) {
    AudioSource* audioSource = (AudioSource*)native_source_ptr;
    audioSource->setFreq(freq);
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_player_NativeAudioSource_resetN(
        JNIEnv *env, jobject thiz, jlong native_source_ptr) {
    // TODO: implement reset()
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_player_NativeAudioSource_triggerN(
        JNIEnv *env, jobject thiz, jlong native_source_ptr) {
    AudioSource* audioSource = (AudioSource*)native_source_ptr;
    audioSource->trigger();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_player_NativeAudioSource_pullN(
        JNIEnv *env, jobject thiz, jlong native_source_ptr,
        jfloatArray audio_data, jint num_frames, jint num_chans) {
    AudioSource* audioSource = (AudioSource*)native_source_ptr;
    int numFrames = audioSource->pull(sAudioBuffer, num_frames, num_chans);

    // Convert to Java float[]
    env->SetFloatArrayRegion(audio_data, 0, numFrames * num_chans, sAudioBuffer);

    return numFrames;
}

} // extern "C"
