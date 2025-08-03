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

package android.hardware.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.hardware.HardwareBuffer;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import cts.android.hardware.IHardwareBufferTestService;

@RunWith(Parameterized.class)
public class HardwareBufferAidlTest {
    private static final String TAG = "HardwareBufferAidlTest";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        // For local interfaces, this test will parcel the data locally.
        // Whenever possible, the desired service should be accessed directly
        // in order to avoid this additional overhead.
        return Arrays.asList(new Object[][] {
                {HardwareBufferTestService.JavaLocal.class},
                {HardwareBufferTestService.JavaRemote.class},
                {HardwareBufferTestService.NativeLocal.class},
                {HardwareBufferTestService.NativeRemote.class},
        });
    }

    private final Class mServiceClass;
    private IHardwareBufferTestService mInterface;

    public HardwareBufferAidlTest(Class serviceClass) {
        mServiceClass = serviceClass;
    }

    @Before
    public void setUp() {
        mInterface = HardwareBufferTestService.connect(
                InstrumentationRegistry.getInstrumentation().getContext(), mServiceClass);
        assertNotNull(mInterface);
    }

    @Test
    public void testRemoteGetId() throws RemoteException {
        try (HardwareBuffer buffer = HardwareBuffer.create(2, 4,
                HardwareBuffer.RGBA_8888, 1, HardwareBuffer.USAGE_CPU_READ_RARELY)) {
            long localId = buffer.getId();
            long remoteId = mInterface.getId(buffer);
            assertEquals(localId, remoteId);
        }
    }

    @Test
    public void testRemoteCreateBuffer() throws RemoteException {
        HardwareBuffer buffer = mInterface.createBuffer(42, 113);
        assertNotNull(buffer);
        assertEquals(42, buffer.getWidth());
        assertEquals(113, buffer.getHeight());
    }
}
