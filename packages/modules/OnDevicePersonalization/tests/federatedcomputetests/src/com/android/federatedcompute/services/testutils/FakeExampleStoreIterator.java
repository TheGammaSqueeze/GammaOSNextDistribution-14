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

import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESULT;

import static com.google.common.truth.Truth.assertThat;

import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreIteratorCallback;
import android.os.Bundle;
import android.os.RemoteException;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A fake ExampleStoreIterator implementation for testing purpose. */
public final class FakeExampleStoreIterator extends IExampleStoreIterator.Stub {
    private final AtomicInteger mClosed = new AtomicInteger(0);
    private final Iterator<byte[]> mExampleResults;
    private final Integer mFinalError;

    public FakeExampleStoreIterator(List<byte[]> exampleResults) {
        this(exampleResults, 0);
    }

    public FakeExampleStoreIterator(Iterable<byte[]> exampleResults, int finalError) {
        this.mExampleResults = exampleResults.iterator();
        this.mFinalError = finalError;
    }

    @Override
    public void next(IExampleStoreIteratorCallback callback) throws RemoteException {
        assertThat(mClosed.get()).isEqualTo(0);
        synchronized (this) {
            if (mExampleResults.hasNext()) {
                byte[] nextProtoResult = mExampleResults.next();
                Bundle bundle = new Bundle();
                bundle.putByteArray(EXTRA_EXAMPLE_ITERATOR_RESULT, nextProtoResult);
                callback.onIteratorNextSuccess(bundle);
            } else if (mFinalError == 0) {
                callback.onIteratorNextSuccess(null);
            } else {
                callback.onIteratorNextFailure(mFinalError);
            }
        }
    }

    @Override
    public void close() {
        mClosed.getAndIncrement();
    }
}
