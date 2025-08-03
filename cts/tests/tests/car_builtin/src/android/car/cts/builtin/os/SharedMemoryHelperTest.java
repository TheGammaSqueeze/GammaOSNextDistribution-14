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

import static org.junit.Assert.assertEquals;

import android.car.builtin.os.SharedMemoryHelper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.SharedMemory;
import android.system.OsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
public final class SharedMemoryHelperTest {
    private static final String TAG = SharedMemoryHelperTest.class.getSimpleName();

    @Test
    public void testCreateParcelFileDescriptor() throws Exception {
        // setup
        int memSize = 32 * 1024;
        int bufSize = memSize / 4;
        SharedMemory sm = SharedMemory.create(/* name */ null, memSize);
        ByteBuffer bb = sm.map(OsConstants.PROT_READ | OsConstants.PROT_WRITE, 0, bufSize);
        populateBufferSequentially(bb, bufSize);

        // execution
        ParcelFileDescriptor pfd = SharedMemoryHelper.createParcelFileDescriptor(sm);

        // assertion
        try (AutoCloseInputStream in = new AutoCloseInputStream(pfd)) {
            bb.rewind();
            for (int i = 0; i < bufSize; i++) {
                assertEquals(bb.get(), (byte) in.read());
            }
        }

        // teardown
        SharedMemory.unmap(bb);
    }

    @Test
    public void testSharedMemoryCreation() throws Exception {
        // setup
        int memSize = 32 * 1024;
        int bufSize = memSize / 4;
        SharedMemory sm1 = SharedMemory.create(/* name */ null, memSize);
        ByteBuffer bb1 = sm1.map(OsConstants.PROT_READ | OsConstants.PROT_WRITE, 0, bufSize);
        populateBufferSequentially(bb1, bufSize);

        // execution
        ParcelFileDescriptor pfd = SharedMemoryHelper.createParcelFileDescriptor(sm1);
        Parcel p = Parcel.obtain();
        p.writeFileDescriptor(pfd.getFileDescriptor());
        p.setDataPosition(0);
        SharedMemory sm2 = SharedMemory.CREATOR.createFromParcel(p);
        ByteBuffer bb2 = sm2.map(OsConstants.PROT_READ | OsConstants.PROT_WRITE, 0, bufSize);

        // assertion
        bb1.rewind();
        assertEquals(bb1, bb2);

        // teardown
        SharedMemory.unmap(bb1);
        SharedMemory.unmap(bb2);
    }

    private void populateBufferSequentially(ByteBuffer buf, int length) {
        for (int i = 0; i < length; i++) {
            buf.put((byte) i);
        }
    }
}
