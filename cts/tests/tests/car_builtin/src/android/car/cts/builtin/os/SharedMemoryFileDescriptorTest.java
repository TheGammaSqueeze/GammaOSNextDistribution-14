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

package android.car.cts.builtin.os;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.car.builtin.os.SharedMemoryHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.system.ErrnoException;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SharedMemoryFileDescriptorTest {

    private static final int TIMEOUT = 20_000;
    private Instrumentation mInstrumentation;
    private Intent mRemoteIntent;
    private PeerConnection mRemoteConnection;
    private ISharedMemoryTestService mRemote;

    public static class PeerConnection implements ServiceConnection {
        private final CountDownLatch mServiceReadyLatch = new CountDownLatch(1);

        private ISharedMemoryTestService mTestService = null;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mTestService = ISharedMemoryTestService.Stub.asInterface(service);
            mServiceReadyLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        public ISharedMemoryTestService get() throws Exception {
            mServiceReadyLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
            return mTestService;
        }
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = mInstrumentation.getContext();
        // Bring up both remote processes and wire them to each other
        mRemoteIntent = new Intent();
        mRemoteIntent.setComponent(new ComponentName(
                "android.car.cts.builtin", "android.car.cts.builtin.os.SharedMemoryTestService"));
        mRemoteConnection = new PeerConnection();
        getContext().bindService(mRemoteIntent, mRemoteConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);

        mRemote = mRemoteConnection.get();
        assertNotNull(mRemote);
    }

    @After
    public void tearDown() {
        Context context = mInstrumentation.getContext();
        context.unbindService(mRemoteConnection);
    }

    @Test
    public void testReadBufData() throws RemoteException, ErrnoException, IOException {
        // setup
        int memSize = 32 * 1024;

        SharedMemory sharedMem = SharedMemory.create(/* name */ null, memSize);
        ByteBuffer buffer = null;
        try {
            buffer = sharedMem.mapReadWrite();
            int checksum = 0;
            for (int i = 0; i < memSize; i++) {
                buffer.put((byte) i);
                checksum += (byte) i;
            }

            // execution
            ParcelFileDescriptor pfd = SharedMemoryHelper.createParcelFileDescriptor(sharedMem);
            int returnedChecksum = mRemote.readBufData(pfd);

            // assertion
            assertEquals(checksum, returnedChecksum);
        } finally {
            // teardown
            SharedMemory.unmap(buffer);
        }
    }
}
