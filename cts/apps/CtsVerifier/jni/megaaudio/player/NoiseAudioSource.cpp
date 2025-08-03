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

#include <cstdlib>
#include <ctime>

#include "NoiseAudioSource.h"

NoiseAudioSource::NoiseAudioSource() {
    srand (static_cast <unsigned> (time(0)));
}

int NoiseAudioSource::pull(float* buffer, int numFrames, int numChans) {
    int numSamples = numFrames * numChans;
    for(int index = 0; index < numSamples; index++) {
        buffer[index] = (float)((drand48() * 2.0) - 1.0);
    }

    return numFrames;
}

//
// JNI
//
#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_player_sources_NoiseAudioSourceProvider_allocNativeSource(
        JNIEnv *env, jobject thiz) {
    return (jlong)new NoiseAudioSource;
}

}   // extern "C"
