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
package org.hyphonate.megaaudio.player;

public class NativeAudioSource extends AudioSource {
    private long    mNativeSourcePtr;

    public NativeAudioSource(long nativeSourcePtr) {
        mNativeSourcePtr = nativeSourcePtr;
    }

    // Use this to call the AudioSource methods in C++ directly
    public long getNativeObject() { return mNativeSourcePtr; }

    @Override
    public void init(int numFrames, int numChans) {
        initN(mNativeSourcePtr, numFrames, numChans);
    }

    /**
     * Specifies the sample rate for the source.
     * @param sampleRate The sample rate for this source.
     */
    public void setSampleRate(int sampleRate) {
        setSampleRateN(mNativeSourcePtr, sampleRate);
    }

    /**
     * (for period sources) sets the frequency of the signal
     * @param freq
     */
    public void setFreq(float freq) {
        setFreqN(mNativeSourcePtr, freq);
    };

    // These can be called from Java, but only do so if you don't mind the JNI overhead
    @Override
    public void reset() {
        resetN(mNativeSourcePtr);
    }

    @Override
    public void trigger() {
        triggerN(mNativeSourcePtr);
    }

    @Override
    public int pull(float[] audioData, int numFrames, int numChans) {
        return pullN(mNativeSourcePtr, audioData, numFrames, numChans);
    }

    private native void initN(long nativeSourcePtr, int numFrames, int numChans);
    private native void setSampleRateN(long nativeSourcePtr, int sampleRate);
    private native void setFreqN(long nativeSourcePtr, float freq);
    private native void resetN(long nativeSourcePtr);
    private native void triggerN(long nativeSourcePtr);
    private native int pullN(long nativeSourcePtr, float[] audioData, int numFrames, int numChans);
}
