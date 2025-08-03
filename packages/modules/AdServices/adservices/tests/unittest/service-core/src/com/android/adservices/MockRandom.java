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

package com.android.adservices;

import java.util.Random;

/**
 * <p> A mock implementation of java.util.Random that returns preset values.
 * Useful for making deterministic tests for classes that rely on a random
 * number sequence. </p>
 *
 * <p> The provided array of long or double values is used (in a circular
 * fashion) instead of a random number generator. The arrays are used to
 * implement the Random API through appropriate conversions, as follows: </p>
 *
 * <ul>
 *
 * <li> <code>long[]</code> values are used for <code>next(int)</code>,
 * <code>nextInt()</code>, <code>nextInt(int)</code>,
 * <code>nextBoolean()</code>, <code>nextByte()</code>, and
 * <code>nextLong()</code>. </li>
 *
 * <li> <code>double[]</code> values are used for <code>nextFloat()</code>,
 * <code>nextGaussian()</code>, and <code>nextDouble()</code>. </li>
 *
 * </ul>
 */

// Copied from google3/java/com/google/testing/util/MockRandom.java
public class MockRandom extends Random {

    private int mNextLongIndex = 0;
    private int mNextDoubleIndex = 0;
    private long[] mLongValues;
    private double[] mDoubleValues;

    public MockRandom(long[] longValues, double[] doubleValues) {
        this.mLongValues = longValues;
        this.mDoubleValues = doubleValues;
    }

    public MockRandom(long[] longValues) {
        this(longValues, null);
    }

    public MockRandom(double[] doubleValues) {
        this(null, doubleValues);
    }

    @Override
    protected synchronized int next(int bits) {
        return (int) (nextLong() & ((1L << bits) - 1));
    }

    @Override
    public synchronized boolean nextBoolean() {
        return nextLong() % 2 == 1;
    }

    @Override
    public synchronized void nextBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) nextLong();
        }
    }

    @Override
    public synchronized double nextDouble() {
        if (mDoubleValues == null) {
            throw new IllegalStateException("No double values were provided");
        }
        double next = mDoubleValues[mNextDoubleIndex];
        mNextDoubleIndex = (mNextDoubleIndex + 1) % mDoubleValues.length;
        return next;
    }

    @Override
    public synchronized float nextFloat() {
        return (float) nextDouble();
    }

    @Override
    public synchronized double nextGaussian() {
        return nextDouble();
    }

    @Override
    public synchronized int nextInt() {
        return (int) nextLong();
    }

    @Override
    public synchronized int nextInt(int n) {
        // nextInt() might return a negative number, but this method must not.
        return (nextInt() & 0x7fffffff) % n;
    }

    @Override
    public synchronized long nextLong() {
        if (mLongValues == null) {
            throw new IllegalStateException("No long values were provided");
        }
        long next = mLongValues[mNextLongIndex];
        mNextLongIndex = (mNextLongIndex + 1) % mLongValues.length;
        return next;
    }

    @Override
    public synchronized void setSeed(long seed) {}
}
