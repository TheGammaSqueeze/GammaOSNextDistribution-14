/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static junit.framework.Assert.*;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.Image.Plane;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

/**
 * <p>
 * Tests for the CameraTestUtils class. Aims to ensure that failures in other
 * testcases are failures of the tested APIs, not the CameraTestUtils code.
 * </p>
 */
public class CtsUtilsTest {
    private static final byte PADBYTE = (byte)0xa5;

    @Test
    public void testCopyYuv() {
        final int w = 14;
        final int h = 10;
        final FakeImage src = FakeImage.createYuv420(w, h, 14,  8, 10);
        final FakeImage dst = FakeImage.createYuv420(w, h, 14, 10,  8);

        drawGradient(src.planes[0], w, h);
        drawGradient(src.planes[1], w/2, h/2);
        drawGradient(src.planes[2], w/2, h/2);

        CameraTestUtils.imageCopy(src, dst);

        checkPlane("Y", w,   h,   src.planes[0], dst.planes[0]);
        checkPlane("U", w/2, h/2, src.planes[1], dst.planes[1]);
        checkPlane("V", w/2, h/2, src.planes[2], dst.planes[2]);
    }

    @Test
    public void testCopyPad0to0() {
        final int w = 21;
        final int h = 9;
        final FakeImage src = FakeImage.createRaw(w, h, 21);
        final FakeImage dst = FakeImage.createRaw(w, h, 21);

        drawGradient(src.planes[0], w, h);
        CameraTestUtils.imageCopy(src, dst);

        checkPlane("RAW", w, h, src.planes[0], dst.planes[0]);
    }

    @Test
    public void testCopyPad3to3() {
        final int w = 21;
        final int h = 9;
        final FakeImage src = FakeImage.createRaw(w, h, 24);
        final FakeImage dst = FakeImage.createRaw(w, h, 24);

        drawGradient(src.planes[0], w, h);
        CameraTestUtils.imageCopy(src, dst);

        checkPlane("RAW", w, h, src.planes[0], dst.planes[0]);
    }

    @Test
    public void testCopyPad3to0() {
        final int w = 21;
        final int h = 9;
        final FakeImage src = FakeImage.createRaw(w, h, 24);
        final FakeImage dst = FakeImage.createRaw(w, h, 21);

        drawGradient(src.planes[0], w, h);
        CameraTestUtils.imageCopy(src, dst);

        checkPlane("RAW", w, h, src.planes[0], dst.planes[0]);
    }

    @Test
    public void testCopyPad0to3() {
        final int w = 21;
        final int h = 9;
        final FakeImage src = FakeImage.createRaw(w, h, 21);
        final FakeImage dst = FakeImage.createRaw(w, h, 24);

        drawGradient(src.planes[0], w, h);
        CameraTestUtils.imageCopy(src, dst);

        checkPlane("RAW", w, h, src.planes[0], dst.planes[0]);
    }

    @Test
    public void testCopyPad5to3() {
        final int w = 21;
        final int h = 9;
        final FakeImage src = FakeImage.createRaw(w, h, 26);
        final FakeImage dst = FakeImage.createRaw(w, h, 24);

        drawGradient(src.planes[0], w, h);
        CameraTestUtils.imageCopy(src, dst);

        checkPlane("RAW", w, h, src.planes[0], dst.planes[0]);
    }

    @Test
    public void testCopyPad3to5() {
        final int w = 21;
        final int h = 9;
        final FakeImage src = FakeImage.createRaw(w, h, 24);
        final FakeImage dst = FakeImage.createRaw(w, h, 26);

        drawGradient(src.planes[0], w, h);
        CameraTestUtils.imageCopy(src, dst);

        checkPlane("RAW", w, h, src.planes[0], dst.planes[0]);
    }

    private static void checkPlane(String planeName, int w, int h, FakePlane src, FakePlane dst) {
        final byte[] srcRow = new byte[w];
        final byte[] dstRow = new byte[w];
        src.buffer.rewind();
        dst.buffer.rewind();
        for (int y=0; y < h; ++y) {
            src.buffer.position(y * src.rowStride);
            dst.buffer.position(y * dst.rowStride);
            src.buffer.get(srcRow, 0, srcRow.length);
            dst.buffer.get(dstRow, 0, dstRow.length);
            for (int x=0; x < w; ++x) {
                if (srcRow[x] != dstRow[x]) {
                    fail("plane " + planeName + " differs on row " + y + ", col " + x + ": " +
                            "src=" + srcRow[x] + " dst=" + dstRow[x]);
                }
            }
            String failmsg = planeName + " plane copied incorrectly";
            assertTrue(failmsg, Arrays.equals(srcRow, dstRow));
        }
        assertEquals("src plane " + planeName + " not exhausted", 0, src.buffer.remaining());
        assertEquals("dst plane " + planeName + " not exhausted", 0, dst.buffer.remaining());
    }

    private static void drawGradient(FakePlane plane, int w, int h) {
        final int diagonal = (w-1)*(w-1) + (h-1)*(h-1);
        final int rowStride = plane.rowStride;
        final ByteBuffer buffer = plane.buffer;
        buffer.rewind();
        for (int y=0; y<h; ++y) {
            int x = 0;
            // fill the image area...
            for (; x < w; ++x) {
                final byte val;
                if (x == w/2) {
                    val = 0; // a vertical black line down the middle
                } else {
                    val = (byte)(255 * (x*x + y*y) / diagonal); // radial gradient
                }
                buffer.put(val);
            }
            // ...and pad the rest of the row stride.
            if (y < h-1) {
                for (; x < rowStride; ++x) {
                    buffer.put(PADBYTE);
                }
            }
        }
    }

}

class FakeImage extends Image {
    public final int format;
    public final int width;
    public final int height;
    public final long timestamp = System.nanoTime();
    public final FakePlane[] planes;

    public FakeImage(int format, int w, int h, FakePlane[] planes) {
        this.format = format;
        this.width = w;
        this.height = h;
        this.planes = planes;
    }

    public static FakeImage createYuv420(int w, int h, int yStride, int uStride, int vStride) {
        return new FakeImage(
                ImageFormat.YUV_420_888,
                w, h,
                new FakePlane[] {
                        FakePlane.createStrict(w,   h,   yStride),
                        FakePlane.createStrict(w/2, h/2, uStride),
                        FakePlane.createStrict(w/2, h/2, vStride),
                }
        );
    }

    public static FakeImage createRaw(int w, int h, int rowStride) {
        return new FakeImage(
                ImageFormat.RAW_SENSOR, w, h,
                new FakePlane[] { FakePlane.createStrict(w, h, rowStride) }
        );
    }

    @Override public int getFormat() { return format; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public long getTimestamp() { return timestamp; }
    @Override public Plane[] getPlanes() { return planes; }
    @Override public void close() { /* no-op */ }
}

class FakePlane extends Image.Plane {
    public final int rowStride;
    public final ByteBuffer buffer;

    public FakePlane(int nbytes, int rowStride) {
        buffer = ByteBuffer.allocateDirect(nbytes);
        this.rowStride = rowStride;
    }

    public static FakePlane createStrict(int w, int h, int rowStride) {
        int nbytes = rowStride * (h - 1) + w; // strictest possible size
        return new FakePlane(nbytes, rowStride);
    }

    @Override public int getRowStride() { return rowStride; }
    @Override public int getPixelStride() { return 1; }
    @Override public ByteBuffer getBuffer() { return buffer; }
}
