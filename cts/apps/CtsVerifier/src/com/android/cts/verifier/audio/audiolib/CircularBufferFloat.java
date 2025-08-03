/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.cts.verifier.audio.audiolib;

/**
 * Implements a circular buffer of float data
 */
public class CircularBufferFloat {
    private float[] mData;
    private int mCursor;
    private int mNumValidSamples;

    public CircularBufferFloat(int numSamples) {
        mData = new float[numSamples];
    }

    /**
     * Adds an array of float values to this buffer.
     * @param buffer    The float values to store.
     * @return The number of values stored.
     */
    public int write(float[] buffer) {
        return write(buffer, 0, buffer.length);
    }

    /**
     * Adds a (subset of a) an array of float values to this buffer
     * @param buffer    The values to store.
     * @param offset    The offset of the first value to store.
     * @param numSamples    The number of values to store.
     * @return The number of values stored.
     */
    public int write(float[] buffer, int offset, int numSamples) {
        if (numSamples > mData.length) {
            throw new IllegalArgumentException("Tried to write more than maxSamples.");
        }
        if ((mCursor + numSamples) > mData.length) {
            // Wraps so write in two parts.
            int numWrite1 = mData.length - mCursor;
            System.arraycopy(buffer, offset, mData, mCursor, numWrite1);
            offset += numWrite1;
            int numWrite2 = numSamples - numWrite1;
            System.arraycopy(buffer, offset, mData, 0, numWrite2);
            mCursor = numWrite2;
        } else {
            System.arraycopy(buffer, offset, mData, mCursor, numSamples);
            mCursor += numSamples;
            if (mCursor == mData.length) {
                mCursor = 0;
            }
        }
        mNumValidSamples += numSamples;
        if (mNumValidSamples > mData.length) {
            mNumValidSamples = mData.length;
        }
        return numSamples;
    }

    /**
     * Reads the most recently written samples into the provided buffer.
     * @param buffer    The buffer to receive the most recent values.
     * @return The number of values retrieved.
     */
    public int readMostRecent(float[] buffer) {
        return readMostRecent(buffer, 0, buffer.length);
    }

    /**
     * Read the most recently written values.
     * @param buffer    The buffer to receive the values
     * @param offset    The offset within the buffer to receive the values
     * @param numSamples    The number of values to retrieve.
     * @return number of values read
     */
    public int readMostRecent(float[] buffer, int offset, int numSamples) {

        if (numSamples > mNumValidSamples) {
            numSamples = mNumValidSamples;
        }
        int cursor = mCursor; // read once in case it gets updated by another thread
        // Read in two parts.
        if ((cursor - numSamples) < 0) {
            int numRead1 = numSamples - cursor;
            System.arraycopy(mData, mData.length - numRead1, buffer, offset, numRead1);
            offset += numRead1;
            int numRead2 = cursor;
            System.arraycopy(mData, 0, buffer, offset, numRead2);
        } else {
            System.arraycopy(mData, cursor - numSamples, buffer, offset, numSamples);
        }

        return numSamples;
    }

    /**
     * (Logically) clears all the values from this circular buffer.
     */
    public void erase() {
        mNumValidSamples = 0;
        mCursor = 0;
    }

    /**
     * @return  The maximum number of values that can be stored.
     * i.e. the capacity of this circular buffer.
     */
    public int getSize() {
        return mData.length;
    }
}
