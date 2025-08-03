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

package android.os.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import android.system.OsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
public final class SharedMemoryFileDescriptorTest {

    private static final String TAG = SharedMemoryFileDescriptorTest.class.getSimpleName();

    @Test
    public void testCreation() throws Exception {
        // setup
        int memSize = 32 * 1024;
        int bufSize = memSize / 4;
        SharedMemory sharedMem1 = SharedMemory.create(/* name= */null, memSize);
        try (ByteBufferSession buffer1 =
                new ByteBufferSession(sharedMem1, /* offset= */ 0, bufSize)) {
            buffer1.fillSequentialPattern();
        }

        Parcel p = Parcel.obtain();
        sharedMem1.writeToParcel(p, 0);
        p.setDataPosition(0);
        ParcelFileDescriptor fileDescriptor = p.readFileDescriptor();

        // execution
        SharedMemory sharedMem2 = SharedMemory.fromFileDescriptor(fileDescriptor);

        // assertion
        assertFalse(fileDescriptor.getFileDescriptor().valid());
        try (ByteBufferSession buffer2 =
                new ByteBufferSession(sharedMem2, /* offset= */ 0, bufSize)) {
            assertTrue(buffer2.checkSequentialPattern());
        }
    }

    @Test
    public void testDuplication() throws Exception {
        // setup
        int memSize = 32 * 1024;
        int bufSize = memSize / 4;
        SharedMemory sharedMem1 = SharedMemory.create(/* name= */ null, memSize);
        try (ByteBufferSession buffer1 =
                new ByteBufferSession(sharedMem1, /* offset= */ 0, bufSize)) {
            buffer1.fillSequentialPattern();
        }

        Parcel p = Parcel.obtain();
        sharedMem1.writeToParcel(p, 0);
        p.setDataPosition(0);
        ParcelFileDescriptor fileDescriptor = p.readFileDescriptor();
        ParcelFileDescriptor dupFileDescriptor = fileDescriptor.dup();

        // execution
        SharedMemory sharedMem2 = SharedMemory.fromFileDescriptor(dupFileDescriptor);

        // assertion
        assertTrue(fileDescriptor.getFileDescriptor().valid());
        try (ByteBufferSession buffer2 =
                new ByteBufferSession(sharedMem2, /* offset= */ 0, bufSize)) {
            assertTrue(buffer2.checkSequentialPattern());
            buffer2.clearMemory();
        }
        try (ByteBufferSession buffer1 =
                new ByteBufferSession(sharedMem1, /* offset= */ 0, bufSize)) {
            // Clearing the shared RAM via sharedMem2 should be visible to sharedMem1
            assertTrue(buffer1.hasCleanMemory());
        }
    }

    private static final class ByteBufferSession implements AutoCloseable {
        private final ByteBuffer mBuf;
        private final int mSize;

        ByteBufferSession(SharedMemory mem, int offset, int length)
                throws Exception {
            mBuf = mem.map(OsConstants.PROT_READ | OsConstants.PROT_WRITE, offset, length);
            mSize = length;
        }

        @Override
        public void close() {
            SharedMemory.unmap(mBuf);
        }

        public void clearMemory() {
            mBuf.clear();
            for (int i = 0; i < mSize; i++) {
                mBuf.put((byte) 0);
            }
        }

        public boolean hasCleanMemory() throws Exception {
            for (int i = 0; i < mSize; i++) {
                if (mBuf.get() != 0) {
                    return false;
                }
            }
            return true;
        }

        public void fillSequentialPattern() {
            for (int fillPos = 0; fillPos < mSize; fillPos++) {
                mBuf.put(fillPos, (byte) fillPos);
            }
        }

        public boolean checkSequentialPattern() {
            for (int fillPos = 0; fillPos < mSize; fillPos++) {
                if (mBuf.get() != (byte) fillPos) {
                    return false;
                }
            }

            return true;
        }
    }
}
