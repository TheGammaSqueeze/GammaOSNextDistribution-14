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
package org.hyphonate.megaaudio.recorder;

public class NativeAudioSink extends AudioSink {
    private long mNativeSinkPtr;

    public NativeAudioSink(long nativeSinkPtr) {
        mNativeSinkPtr = nativeSinkPtr;
    }

    // Use this to call the AudioSource methods in C++ directly
    public long getNativeObject() { return mNativeSinkPtr; }

    @Override
    public void init(int numFrames, int numChans) {
        initN(mNativeSinkPtr, numFrames, numChans);
    }

    @Override
    public void start() {
        startN(mNativeSinkPtr);
    }

    @Override
    public void stop(int lastBufferFrames) {
        stopN(mNativeSinkPtr);
    }

    @Override
    public void push(float[] audioData, int numFrames, int numChans) {
        pushN(mNativeSinkPtr, audioData, numFrames, numChans);
    }

    private native void initN(long nativeSinkPtr, int numFrames, int numChans);
    private native void startN(long nativeSinkPtr);
    private native void stopN(long nativeSinkPtr);
    private native void pushN(long nativeSinkPtr, float[] audioData, int numFrames, int numChans);
}
