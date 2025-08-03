/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services.testutils;

import com.android.federatedcompute.services.examplestore.ExampleIterator;

import com.google.common.collect.ImmutableList;
import com.google.internal.federated.plan.Dataset;
import com.google.internal.federated.plan.Dataset.ClientDataset;
import com.google.protobuf.ByteString;

import java.time.Duration;

/** An ExampleIterator that serves a fixed number of examples. */
public class ResettingExampleIterator implements ExampleIterator {
    private final int mCapacity;
    ImmutableList<ByteString> mExamples;
    Duration mNextLatency;
    private int mNumServed;
    private int mNumHasNextInvocations;
    private int mNumNextInvocations;
    private int mNumCloseInvocations;

    /**
     * Creates an {@link ExampleIterator} for use in tests.
     *
     * @param limit number of examples being served until {@link #hasNext()} fails.
     * @param dataset The Dataset to serve examples from.
     * @param nextLatency amount of time to sleep before returning an example from next().
     */
    public ResettingExampleIterator(int limit, Dataset dataset, Duration nextLatency) {
        this.mCapacity = limit;
        ImmutableList.Builder<ByteString> exampleDatasetBuilder = new ImmutableList.Builder<>();
        for (ClientDataset clientDataset : dataset.getClientDataList()) {
            exampleDatasetBuilder.addAll(clientDataset.getExampleList());
        }
        this.mExamples = exampleDatasetBuilder.build();
        this.mNextLatency = nextLatency;
    }

    public ResettingExampleIterator(int limit, Dataset dataset) {
        this(limit, dataset, Duration.ZERO);
    }

    /**
     * Iterator that always returns the same example. See {@link #ResettingExampleIterator(int,
     * Dataset)} for details.
     */
    public ResettingExampleIterator(int capacity, ByteString example) {
        this.mCapacity = capacity;
        this.mNextLatency = Duration.ZERO;
        this.mExamples = ImmutableList.of(example);
    }

    @Override
    public boolean hasNext() {
        mNumHasNextInvocations++;
        if (mNumServed < mCapacity) {
            return true;
        } else {
            mNumServed = 0;
            return false;
        }
    }

    @Override
    public byte[] next() {
        mNumNextInvocations++;
        mNumServed++;
        // If the end of the provided examples has been reached, wrap around and start serving from
        // the beginning again.
        if (!mNextLatency.isZero()) {
            try {
                Thread.sleep(mNextLatency.toMillis());
            } catch (InterruptedException e) {
                throw new IllegalStateException("error in Thread.sleep()", e);
            }
        }
        return mExamples.get((mNumServed - 1) % mExamples.size()).toByteArray();
    }

    @Override
    public void close() {
        mNumCloseInvocations++;
    }

    public int getNumHasNextInvocations() {
        return mNumHasNextInvocations;
    }

    public int getNumNextInvocations() {
        return mNumNextInvocations;
    }

    public int getNumCloseInvocations() {
        return mNumCloseInvocations;
    }
}
