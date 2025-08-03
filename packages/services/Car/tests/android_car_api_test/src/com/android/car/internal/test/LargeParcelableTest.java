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
import android.car.test.mocks.JavaMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.car.internal.LargeParcelable;
import com.android.compatibility.common.util.NonApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

@SmallTest
public final class LargeParcelableTest extends CarLessApiTestBase {

    private static final String TAG = LargeParcelableTest.class.getSimpleName();

    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final int ARRAY_LENGTH_SMALL = 2048;
    // The current threshold is 4096.
    private static final int ARRAY_LENGTH_BIG = 4099;

    private final TestServiceConnection mServiceConnection = new TestServiceConnection();

    private IJavaTestBinder mBinder;

    @Before
    public void setUp() throws Exception {
        LargeParcelable.setClassLoader(mContext.getClassLoader());
        Intent intent = new Intent();
        intent.setClassName(mContext, LargeParcelableTestService.class.getName());
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
    public void testLocalSerializationDeserializationSmallPayload() throws Exception {
        doTestLocalSerializationDeserialization(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testLocalSerializationDeserializationBigPayload() throws Exception {
        doTestLocalSerializationDeserialization(ARRAY_LENGTH_BIG);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testLocalSerializationDeserializationNullPayload() throws Exception {
        TestLargeParcelable origParcelable = new TestLargeParcelable();
        Parcel dest = Parcel.obtain();
        origParcelable.writeToParcel(dest, 0);
        dest.setDataPosition(0);

        TestLargeParcelable newPaecelable = new TestLargeParcelable(dest);

        assertThat(newPaecelable.byteData).isNull();
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testRemoteNullPayload() throws Exception {
        TestLargeParcelable origParcelable = new TestLargeParcelable();

        TestLargeParcelable r = mBinder.echoTestLargeParcelable(origParcelable);

        assertThat(r).isNotNull();
        assertThat(r.byteData).isNull();
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testTestParcelableSmallPayload() throws Exception {
        doTestLargeParcelable(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testTestParcelableBigPayload() throws Exception {
        doTestLargeParcelable(ARRAY_LENGTH_BIG);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testLargeParcelableSmallPayload() throws Exception {
        doTestTestLargeParcelable(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testLargeParcelableBigPayload() throws Exception {
        doTestTestLargeParcelable(ARRAY_LENGTH_BIG);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testMultiArgsWithNullPayload() throws Exception {
        TestLargeParcelable origParcelable = new TestLargeParcelable();
        long argValue = 0x12345678;

        long r = mBinder.echoLongWithTestLargeParcelable(origParcelable, argValue);

        assertThat(r).isEqualTo(argValue);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testMultiArgsSmallPayload() throws Exception {
        doTestMultipleArgs(ARRAY_LENGTH_SMALL);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "Testing large parcelable, which is a "
            + "hidden API")
    public void testMultiArgsBigPayload() throws Exception {
        doTestMultipleArgs(ARRAY_LENGTH_BIG);
    }

    private void doTestLargeParcelable(int payloadSize) throws Exception {
        byte[] origArray = createByteArray(payloadSize);
        TestParcelable origParcelable = new TestParcelable(origArray);

        LargeParcelable r = mBinder.echoLargeParcelable(new LargeParcelable(origParcelable));
        assertThat(r).isNotNull();

        TestParcelable receivedParcelable = (TestParcelable) r.getParcelable();

        assertThat(receivedParcelable).isNotNull();
        assertThat(receivedParcelable.byteData).isEqualTo(origArray);
    }

    private void doTestTestLargeParcelable(int payloadSize) throws Exception {
        byte[] origArray = createByteArray(payloadSize);
        TestLargeParcelable origParcelable = new TestLargeParcelable(origArray);

        TestLargeParcelable r = mBinder.echoTestLargeParcelable(origParcelable);

        assertThat(r).isNotNull();
        assertThat(r.byteData).isNotNull();
        assertThat(r.byteData).isEqualTo(origArray);
    }

    private void doTestLocalSerializationDeserialization(int payloadSize) throws Exception {
        byte[] origArray = createByteArray(payloadSize);
        TestLargeParcelable origParcelable = new TestLargeParcelable(origArray);
        Parcel dest = Parcel.obtain();

        origParcelable.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        TestLargeParcelable newParcelable = new TestLargeParcelable(dest);

        assertThat(newParcelable.byteData).isNotNull();
        assertThat(newParcelable.byteData).isEqualTo(origArray);
    }

    private void doTestMultipleArgs(int payloadSize) throws Exception {
        byte[] origArray = createByteArray(payloadSize);
        TestLargeParcelable origParcelable = new TestLargeParcelable(origArray);
        long argValue = 0x12345678;
        long expectedRet = argValue + LargeParcelableTestService.calcByteSum(origParcelable);

        long r = mBinder.echoLongWithTestLargeParcelable(origParcelable, argValue);

        assertThat(r).isEqualTo(expectedRet);
    }

    public static byte[] createByteArray(int length) {
        byte[] array = new byte[length];
        byte val = 0x7f;
        for (int i = 0; i < length; i++) {
            array[i] = val;
            val++;
        }
        return array;
    }

    private final class TestServiceConnection implements ServiceConnection {
        public final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = IJavaTestBinder.Stub.asInterface(service);
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }
}
