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

#include "SinAudioSource.h"

SinAudioSource::SinAudioSource() : WaveTableSource() {
    float* waveTbl = new float[DEFAULT_WAVETABLE_LENGTH];
    WaveTableSource::genSinWave(waveTbl, DEFAULT_WAVETABLE_LENGTH);

    WaveTableSource::setWaveTable(waveTbl, DEFAULT_WAVETABLE_LENGTH);
}

//
// JNI functions
//
#include <jni.h>

#include <android/log.h>

extern "C" {
JNIEXPORT JNICALL jlong Java_org_hyphonate_megaaudio_player_sources_SinAudioSourceProvider_allocNativeSource(
        JNIEnv *env, jobject thiz) {
    return (jlong)new SinAudioSource();
}

} // extern "C"
