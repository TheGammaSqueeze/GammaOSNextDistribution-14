/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.hardware.HardwareBuffer;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Parcel;
import android.os.SystemProperties;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link HardwareBuffer}.
 */
@RunWith(JUnitParamsRunner.class)
public class HardwareBufferTest {
    private static native HardwareBuffer nativeCreateHardwareBuffer(int width, int height,
            int format, int layers, long usage);

    private static native HardwareBuffer nativeReadHardwareBuffer(Parcel parcel);
    private static native void nativeWriteHardwareBuffer(HardwareBuffer buffer, Parcel parcel);

    static {
        System.loadLibrary("ctshardware_jni");
    }

    @Test
    public void testCreate() {
        HardwareBuffer buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertTrue(buffer != null);
        assertEquals(2, buffer.getWidth());
        assertEquals(4, buffer.getHeight());
        assertEquals(HardwareBuffer.RGBA_8888, buffer.getFormat());
        assertEquals(1, buffer.getLayers());
        assertEquals(HardwareBuffer.USAGE_CPU_READ_RARELY, buffer.getUsage());

        buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBX_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.RGBX_8888, buffer.getFormat());
        buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGB_888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.RGB_888, buffer.getFormat());
        buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGB_565, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.RGB_565, buffer.getFormat());
        buffer = HardwareBuffer.create(2, 1, HardwareBuffer.BLOB, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.BLOB, buffer.getFormat());
    }

    private static Object[] paramsForTestCreateOptionalFormats() {
        return new Integer[]{
            HardwareBuffer.RGBA_FP16,
            HardwareBuffer.RGBA_1010102,
            HardwareBuffer.YCBCR_420_888,
            HardwareBuffer.D_16,
            HardwareBuffer.D_24,
            HardwareBuffer.DS_24UI8,
            HardwareBuffer.D_FP32,
            HardwareBuffer.DS_FP32UI8,
            HardwareBuffer.S_UI8,
            HardwareBuffer.YCBCR_P010,
            HardwareBuffer.R_8,
            HardwareBuffer.R_16,
            HardwareBuffer.RG_1616,
            HardwareBuffer.RGBA_10101010
        };
    }

    @Test
    @Parameters(method = "paramsForTestCreateOptionalFormats")
    public void testCreateOptionalFormats(int format) {
        if (HardwareBuffer.isSupported(2, 4, format, 1, HardwareBuffer.USAGE_CPU_READ_RARELY)) {
            HardwareBuffer buffer =
                    HardwareBuffer.create(2, 4, format, 1, HardwareBuffer.USAGE_CPU_READ_RARELY);
            assertTrue(buffer != null);
            assertEquals(2, buffer.getWidth());
            assertEquals(4, buffer.getHeight());
            assertEquals(format, buffer.getFormat());
            assertEquals(1, buffer.getLayers());
            assertEquals(HardwareBuffer.USAGE_CPU_READ_RARELY, buffer.getUsage());
        }
    }


    @Test
    public void testCreateFailsWithInvalidArguments() {
        HardwareBuffer buffer = null;
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(0, 4, HardwareBuffer.RGB_888, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 0, HardwareBuffer.RGB_888, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 4, 0, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGB_888, -1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 2, HardwareBuffer.BLOB, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);

    }

    @Test
    public void testCreateFromNativeObject() {
        HardwareBuffer buffer = nativeCreateHardwareBuffer(2, 4, HardwareBuffer.RGBA_8888, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertTrue(buffer != null);
        assertEquals(2, buffer.getWidth());
        assertEquals(4, buffer.getHeight());
        assertEquals(HardwareBuffer.RGBA_8888, buffer.getFormat());
        assertEquals(1, buffer.getLayers());
        assertEquals(HardwareBuffer.USAGE_CPU_READ_RARELY, buffer.getUsage());
    }

    @Test
    public void testIsSupported() {
        assertTrue(HardwareBuffer.isSupported(1, 1, HardwareBuffer.RGBA_8888,
                1, HardwareBuffer.USAGE_CPU_READ_RARELY));
        assertTrue(HardwareBuffer.isSupported(1, 1, HardwareBuffer.RGBA_8888,
                1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE));
        assertFalse(HardwareBuffer.isSupported(1, 1, HardwareBuffer.BLOB,
                1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT));
    }

    @Test
    public void testInvalidUsage() {
        if (SystemProperties.getInt("ro.vendor.build.version.sdk", 0)
                < Build.VERSION_CODES.TIRAMISU) {
            // Legacy grallocs may have a mismatch here.
            return;
        }

        final int dimen = 100;
        final long usage = HardwareBuffer.USAGE_CPU_READ_RARELY | (1L << 46);
        final boolean supported = HardwareBuffer.isSupported(dimen, dimen, HardwareBuffer.RGBA_8888,
                1, usage);

        try {
            HardwareBuffer buffer = HardwareBuffer.create(dimen, dimen, HardwareBuffer.RGBA_8888,
                    1, usage);
            if (!supported) {
                fail("Allocation should have failed (isSupported returned false); instead got "
                        + buffer);
            }
        } catch (IllegalArgumentException ex) {
            if (supported) {
                fail("Allocation should have succeeded (isSupported returned true)");
            }
        }

    }

    @Test
    public void testGetId() {
        HardwareBuffer buffer1 = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertNotNull(buffer1);
        HardwareBuffer buffer2 = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertNotNull(buffer2);
        assertNotEquals(0, buffer1.getId());
        assertNotEquals(0, buffer2.getId());
        assertNotEquals(buffer1.getId(), buffer2.getId());
        buffer1.close();
        buffer2.close();
    }

    @Test
    public void testClosedFails() {
        HardwareBuffer buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertNotNull(buffer);
        assertFalse(buffer.isClosed());
        assertEquals(2, buffer.getWidth());
        buffer.close();
        assertTrue(buffer.isClosed());
        assertThrows(IllegalStateException.class, buffer::getWidth);
        assertThrows(IllegalStateException.class, buffer::getHeight);
        assertThrows(IllegalStateException.class, buffer::getId);
        assertTrue(buffer.isClosed());
    }

    @Test
    public void testWriteJavaReadNativeParcel() {
        Parcel parcel = Parcel.obtain();
        HardwareBuffer inBuffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertNotNull(inBuffer);
        long beforeId = inBuffer.getId();
        assertEquals(2, inBuffer.getWidth());
        assertNotEquals(0, beforeId);
        assertEquals(0, parcel.dataPosition());
        assertEquals(0, parcel.dataAvail());
        inBuffer.writeToParcel(parcel, 0);
        assertNotEquals(0, parcel.dataPosition());
        parcel.setDataPosition(0);
        HardwareBuffer outBuffer = nativeReadHardwareBuffer(parcel);
        assertNotNull(outBuffer);
        // Spot check it's the same thing, and also the input buffer wasn't clobbered
        assertEquals(2, inBuffer.getWidth());
        assertEquals(2, outBuffer.getWidth());
        assertNotEquals(0, outBuffer.getId());
        assertEquals(beforeId, outBuffer.getId());
        parcel.recycle();
    }

    @Test
    public void testWriteNativeReadJavaParcel() {
        Parcel parcel = Parcel.obtain();
        final HardwareBuffer inBuffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertNotNull(inBuffer);
        long beforeId = inBuffer.getId();
        assertEquals(2, inBuffer.getWidth());
        assertNotEquals(0, beforeId);
        assertEquals(0, parcel.dataPosition());
        assertEquals(0, parcel.dataAvail());
        nativeWriteHardwareBuffer(inBuffer, parcel);
        assertNotEquals(0, parcel.dataPosition());
        parcel.setDataPosition(0);
        final HardwareBuffer outBuffer = HardwareBuffer.CREATOR.createFromParcel(parcel);
        assertNotNull(outBuffer);
        // Spot check it's the same thing, and also the input buffer wasn't clobbered
        assertEquals(2, inBuffer.getWidth());
        assertEquals(2, outBuffer.getWidth());
        assertNotEquals(0, outBuffer.getId());
        assertEquals(beforeId, outBuffer.getId());
        parcel.recycle();
    }

    @Test
    public void testReadInvalidParcelFails() {
        Parcel parcel = Parcel.obtain();
        assertNotNull(parcel);
        assertThrows(BadParcelableException.class, () -> {
            HardwareBuffer.CREATOR.createFromParcel(parcel);
        });
        parcel.recycle();
    }
}
