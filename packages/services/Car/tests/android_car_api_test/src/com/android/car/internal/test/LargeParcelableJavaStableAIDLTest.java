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

package com.android.car.internal.test;

import static com.google.common.truth.Truth.assertThat;

import android.car.apitest.CarLessApiTestBase;
import android.car.apitest.IStableAIDLTestBinder;
import android.car.apitest.IStableAIDLTestCallback;
import android.car.apitest.StableAIDLTestLargeParcelable;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcelable;

import androidx.test.filters.SmallTest;

import com.android.car.internal.LargeParcelable;
import com.android.compatibility.common.util.NonApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

@SmallTest
public final class LargeParcelableJavaStableAIDLTest extends CarLessApiTestBase {

    private static final String TAG = LargeParcelableJavaStableAIDLTest.class.getSimpleName();

    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final int ARRAY_LENGTH_SMALL = 2048;
    // The current threshold is 4096.
    private static final int ARRAY_LENGTH_BIG = 4099;

    private final TestServiceConnection mServiceConnection = new TestServiceConnection();

    private IStableAIDLTestBinder mBinder;

    private static class TestCallback extends IStableAIDLTestCallback.Stub {
        @Override
        public void reply(StableAIDLTestLargeParcelable p) {
            mResult = p;
        }

        public StableAIDLTestLargeParcelable getResult() {
            return mResult;
        }

        private StableAIDLTestLargeParcelable mResult;
    }

    @Before
    public void setUp() throws Exception {
        LargeParcelable.setClassLoader(mContext.getClassLoader());
        Intent intent = new Intent();
        intent.setClassName(mContext, IStableAIDLBinderTestService.class.getName());
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        JavaMockitoHelper.await(mServiceConnection.latch, DEFAULT_TIMEOUT_MS);
    }

    @After
    public void tearDown() {
        mContext.unbindService(mServiceConnection);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoSmallPayload() throws Exception {
        doTestLEcho(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoBigPayload() throws Exception {
        doTestLEcho(ARRAY_LENGTH_BIG);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoSmallPayloadPerfTest() throws Exception {
        for (int i = 0; i < 1000; i++) {
            doTestLEcho(ARRAY_LENGTH_SMALL);
        }
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoBigPayloadPerfTest() throws Exception {
        for (int i = 0; i < 1000; i++) {
            doTestLEcho(ARRAY_LENGTH_BIG);
        }
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoMultipleArgsSmallPayload() throws Exception {
        doTestMultipleArgs(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoMultipleArgsBigPayload() throws Exception {
        doTestMultipleArgs(ARRAY_LENGTH_BIG);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testNullParcelable() throws Exception {
        StableAIDLTestLargeParcelable r = mBinder.echo(null);

        assertThat(r).isNull();

        long argValue = 0x12345678;

        long rValue = mBinder.echoWithLong(null, argValue);

        assertThat(argValue).isEqualTo(rValue);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoWithCallbackSmallPayload() throws Exception {
        doTestEchoWithCallback(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testEchoWithCallbackBigPayload() throws Exception {
        doTestEchoWithCallback(ARRAY_LENGTH_BIG);
    }

    public StableAIDLTestLargeParcelable prepareParcelable(StableAIDLTestLargeParcelable in) {
        Parcelable sendableParcelable = LargeParcelable.toLargeParcelable(in, () -> {
            StableAIDLTestLargeParcelable o = new StableAIDLTestLargeParcelable();
            o.payload = new byte[0];
            return o;
        });
        return (StableAIDLTestLargeParcelable) sendableParcelable;
    }

    private void doTestLEcho(int payloadSize) throws Exception {
        StableAIDLTestLargeParcelable orig = new StableAIDLTestLargeParcelable();
        byte[] payload = LargeParcelableTest.createByteArray(payloadSize);
        orig.payload = payload;
        orig = prepareParcelable(orig);

        StableAIDLTestLargeParcelable r = mBinder.echo(orig);
        r = (StableAIDLTestLargeParcelable)
                LargeParcelable.reconstructStableAIDLParcelable(r, true);

        assertThat(r).isNotNull();
        assertThat(r.payload).isNotNull();
        assertThat(r.payload).isEqualTo(payload);
        if (payloadSize > LargeParcelable.MAX_DIRECT_PAYLOAD_SIZE) {
            assertThat(orig.sharedMemoryFd).isNotNull();
            assertThat(orig.payload.length).isEqualTo(0);
            assertThat(r.sharedMemoryFd).isNotNull();
        } else {
            assertThat(orig.sharedMemoryFd).isNull();
            assertThat(orig.payload.length).isNotEqualTo(0);
            assertThat(r.sharedMemoryFd).isNull();
        }
    }

    private void doTestMultipleArgs(int payloadSize) throws Exception {
        StableAIDLTestLargeParcelable orig = new StableAIDLTestLargeParcelable();
        byte[] payload = LargeParcelableTest.createByteArray(payloadSize);
        orig.payload = payload;
        long argValue = 0x12345678;
        long expectedRet = argValue + IStableAIDLBinderTestService.calcByteSum(orig);
        orig = prepareParcelable(orig);

        long r = mBinder.echoWithLong(orig, argValue);

        assertThat(r).isEqualTo(expectedRet);
        if (payloadSize > LargeParcelable.MAX_DIRECT_PAYLOAD_SIZE) {
            assertThat(orig.sharedMemoryFd).isNotNull();
            assertThat(orig.payload.length).isEqualTo(0);
        } else {
            assertThat(orig.sharedMemoryFd).isNull();
            assertThat(orig.payload.length).isNotEqualTo(0);
        }
    }

    private void doTestEchoWithCallback(int payloadSize) throws Exception {
        StableAIDLTestLargeParcelable orig = new StableAIDLTestLargeParcelable();
        byte[] payload = LargeParcelableTest.createByteArray(payloadSize);
        orig.payload = payload;
        TestCallback callback = new TestCallback();
        orig = prepareParcelable(orig);

        mBinder.echoWithCallback(callback, orig);

        StableAIDLTestLargeParcelable r = callback.getResult();
        r = (StableAIDLTestLargeParcelable)
                LargeParcelable.reconstructStableAIDLParcelable(r, true);

        assertThat(r).isNotNull();
        assertThat(r.payload).isNotNull();
        assertThat(r.payload).isEqualTo(payload);
        if (payloadSize > LargeParcelable.MAX_DIRECT_PAYLOAD_SIZE) {
            assertThat(orig.sharedMemoryFd).isNotNull();
            assertThat(orig.payload.length).isEqualTo(0);
            assertThat(r.sharedMemoryFd).isNotNull();
        } else {
            assertThat(orig.sharedMemoryFd).isNull();
            assertThat(orig.payload.length).isNotEqualTo(0);
            assertThat(r.sharedMemoryFd).isNull();
        }
    }

    private final class TestServiceConnection implements ServiceConnection {
        public final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = IStableAIDLTestBinder.Stub.asInterface(service);
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }
}
