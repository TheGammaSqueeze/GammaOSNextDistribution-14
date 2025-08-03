/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.internal.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * Unit Tests of ByteArrayParceledSlice.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ByteArrayParceledSliceTest {
    public static final String TAG = "ByteArrayParceledSliceTest";
    private static final int SIZE = 10 * 1024 * 1024;
    private static final String INPUT_KEY = "input_key";
    private static final String RESULT_KEY = "result_key";
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private boolean mSuccess;
    private boolean mFailure;
    private Bundle mEchoResult;

    @Test
    public void runTest() throws Exception {
        byte[] data = new byte[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            data[i] = (byte) (i % 256);
        }
        EchoService service = new EchoService();
        Bundle input = new Bundle();
        input.putParcelable(INPUT_KEY, new ByteArrayParceledSlice(data));
        Executors.newSingleThreadExecutor().submit(() -> service.echo(
                input, new EchoServiceCallback()));
        mLatch.await();
        assertTrue(mSuccess);
        assertFalse(mFailure);
        ByteArrayParceledSlice parceledByteArray = mEchoResult.getParcelable(
                RESULT_KEY, ByteArrayParceledSlice.class);
        byte[] result = parceledByteArray.getByteArray();
        assertArrayEquals(data, result);
    }

    @Test
    public void runTestForNullArray() throws Exception {
        EchoService service = new EchoService();
        Bundle input = new Bundle();
        input.putParcelable(INPUT_KEY, new ByteArrayParceledSlice(null));
        Executors.newSingleThreadExecutor().submit(() -> service.echo(
                input, new EchoServiceCallback()));
        mLatch.await();
        assertTrue(mSuccess);
        assertFalse(mFailure);
        ByteArrayParceledSlice parceledByteArray = mEchoResult.getParcelable(
                RESULT_KEY, ByteArrayParceledSlice.class);
        byte[] result = parceledByteArray.getByteArray();
        assertNull(result);
    }

    @Test
    public void runTestForEmptyArray() throws Exception {
        byte[] data = new byte[0];
        EchoService service = new EchoService();
        Bundle input = new Bundle();
        input.putParcelable(INPUT_KEY, new ByteArrayParceledSlice(data));
        Executors.newSingleThreadExecutor().submit(() -> service.echo(
                input, new EchoServiceCallback()));
        mLatch.await();
        assertTrue(mSuccess);
        assertFalse(mFailure);
        ByteArrayParceledSlice parceledByteArray = mEchoResult.getParcelable(
                RESULT_KEY, ByteArrayParceledSlice.class);
        byte[] result = parceledByteArray.getByteArray();
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    class EchoServiceCallback extends IEchoServiceCallback.Stub {
        @Override
        public void onResult(Bundle result) {
            mSuccess = true;
            mEchoResult = result;
            mLatch.countDown();
        }
        @Override
        public void onError() {
            mFailure = true;
            mLatch.countDown();
        }
    }

    class EchoService extends IEchoService.Stub {
        @Override
        public void echo(Bundle input, IEchoServiceCallback callback) {
            Executors.newSingleThreadExecutor().submit(() -> run(input, callback));
        }
        void run(Bundle input, IEchoServiceCallback callback) {
            try {
                ByteArrayParceledSlice parceledByteArray = input.getParcelable(
                        INPUT_KEY, ByteArrayParceledSlice.class);
                Objects.requireNonNull(parceledByteArray);
                byte[] contents = parceledByteArray.getByteArray();
                Bundle result = new Bundle();
                result.putParcelable(RESULT_KEY, new ByteArrayParceledSlice(contents));
                try {
                    callback.onResult(result);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                try {
                    callback.onError();
                } catch (RemoteException r) {
                    Log.e(TAG, "Error", r);
                }
            }
        }
    }
}
