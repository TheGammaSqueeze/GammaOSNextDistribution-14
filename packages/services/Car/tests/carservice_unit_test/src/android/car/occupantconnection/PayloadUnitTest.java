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

package android.car.occupantconnection;

import static com.android.car.internal.LargeParcelableBase.MAX_DIRECT_PAYLOAD_SIZE;

import static com.google.common.truth.Truth.assertThat;

import android.car.test.mocks.JavaMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

@SmallTest
public final class PayloadUnitTest {

    private static final int ARRAY_LENGTH_SMALL = MAX_DIRECT_PAYLOAD_SIZE - 1;
    private static final int ARRAY_LENGTH_BIG = MAX_DIRECT_PAYLOAD_SIZE + 1;
    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final TestServiceConnection mServiceConnection = new TestServiceConnection();

    private IPayloadTestBinder mBinder;

    @Before
    public void setUp() throws InterruptedException {
        Intent intent = new Intent();
        intent.setClassName(mContext, PayloadTestBinderService.class.getName());
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        JavaMockitoHelper.await(mServiceConnection.latch, DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testLocalSerializeByteArray() {
        doTestLocalSerializationDeserializationByteArray(ARRAY_LENGTH_SMALL);
        doTestLocalSerializationDeserializationByteArray(ARRAY_LENGTH_BIG);
    }

    @Test
    public void testRemoteSerializeByteArray() throws RemoteException {
        doTestRemoteSerializationDeserializationByteArray(ARRAY_LENGTH_SMALL);
        doTestRemoteSerializationDeserializationByteArray(ARRAY_LENGTH_BIG);
    }

    @Test
    public void testLocalSerializeBinder() {
        IBinder origBinder = new Binder();
        Payload origPayload = new Payload(origBinder);
        Parcel dest = Parcel.obtain();

        origPayload.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        Payload newPayload = Payload.CREATOR.createFromParcel(dest);

        assertThat(newPayload.getBinder()).isEqualTo(origBinder);
    }

    @Test
    public void testRemoteSerializeBinder() throws RemoteException {
        IBinder origBinder = new Binder();
        Payload origPayload = new Payload(origBinder);
        Payload newPayload = mBinder.echoPayload(origPayload);

        assertThat(newPayload.getBinder()).isEqualTo(origBinder);
    }

    private static void doTestLocalSerializationDeserializationByteArray(int payloadSize) {
        byte[] origArray = createByteArray(payloadSize);
        Payload origPayload = new Payload(origArray);
        Parcel dest = Parcel.obtain();

        origPayload.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        Payload newPayload = Payload.CREATOR.createFromParcel(dest);

        assertThat(newPayload.getBytes()).isEqualTo(origArray);
    }

    private void doTestRemoteSerializationDeserializationByteArray(int payloadSize)
            throws RemoteException {
        byte[] origArray = createByteArray(payloadSize);
        Payload origPayload = new Payload(origArray);
        Payload newPayload = mBinder.echoPayload(origPayload);

        assertThat(newPayload.getBytes()).isEqualTo(origArray);
    }

    private static byte[] createByteArray(int length) {
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
            mBinder = IPayloadTestBinder.Stub.asInterface(service);
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }
}
