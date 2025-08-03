/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.cts.verifier.audio.analyzers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a circular buffer for audio data.
 */
public class InfiniteRecording {

    private float[] mData;
    private AtomicInteger mWritten = new AtomicInteger();
    private int     mMaxSamples;

    public InfiniteRecording(int maxSamples) {
        mMaxSamples = maxSamples;
        mData = new float[mMaxSamples];
    }

    /**
     *
     * @param buffer
     * @param position
     * @param count
     * @return
     */
    public int readFrom(float[] buffer, int position, int count) {
        final int maxPosition = mWritten.get();
        position = Math.min(position, maxPosition);
        int numToRead = Math.min(count, mMaxSamples);
        numToRead = Math.min(numToRead, maxPosition - position);
        if (numToRead == 0) {
            return 0;
        }

        // We may need to read in two parts if it wraps.
        final int offset = position % mMaxSamples;
        final int firstReadSize = Math.min(numToRead, mMaxSamples - offset);
        // copy (InputIterator first, InputIterator last, OutputIterator result)
        // std::copy(&mData[offset], &mData[offset + firstReadSize], buffer);
        // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
        System.arraycopy(mData, offset, mData, offset + firstReadSize, firstReadSize);
        if (firstReadSize < numToRead) {
            // Second read needed.
            // std::copy(&mData[0], &mData[numToRead - firstReadSize], &buffer[firstReadSize]);
            System.arraycopy(mData, 0, mData, numToRead - firstReadSize, numToRead - firstReadSize);
        }
        return numToRead;
    }

    /**
     *
     * @param sample
     */
    public void write(float sample) {
        final int position = mWritten.get();
        final int offset = position % mMaxSamples;
        mData[offset] = sample;
        mWritten.incrementAndGet();
    }

    /**
     *
     * @return
     */
    public int getTotalWritten() {
        return mWritten.get();
    }
};
