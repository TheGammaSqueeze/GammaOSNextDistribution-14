/*
 * Copyright 2021 The Android Open Source Project
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

#include "BlipAudioSource.h"

BlipAudioSource::BlipAudioSource()
    : mNumPendingPulseFrames(0),
      mRequestCount(0),
      mAcknowledgeCount(0)
 {
    float* waveTbl = new float[DEFAULT_WAVETABLE_LENGTH];
    WaveTableSource::genSinWave(waveTbl, DEFAULT_WAVETABLE_LENGTH);

    WaveTableSource::setWaveTable(waveTbl, DEFAULT_WAVETABLE_LENGTH);
}

void BlipAudioSource::trigger() {
    mRequestCount++;
}

int BlipAudioSource::pull(float* buffer, int numFrames, int numChans) {
    if (mRequestCount.load() > mAcknowledgeCount.load()) {
        mAcknowledgeCount++;
        mNumPendingPulseFrames = NUM_PULSE_FRAMES;
    }

    if (mNumPendingPulseFrames <= 0) {
        memset(buffer, 0, numFrames * numChans * sizeof(float));
    } else {
        WaveTableSource::pull(buffer, numFrames, numChans);
        mNumPendingPulseFrames -= numFrames;
    }
    return numFrames;
}

//
// JNI
//
#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL
Java_com_android_cts_verifier_audio_sources_BlipAudioSourceProvider_allocNativeSource(
        JNIEnv *env, jobject thiz) {
    return (jlong)new BlipAudioSource;
}

}   // extern "C"
