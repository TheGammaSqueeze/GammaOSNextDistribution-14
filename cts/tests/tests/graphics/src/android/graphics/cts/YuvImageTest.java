/*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.media.ExifInterface;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BitmapUtils;
import com.android.graphics.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class YuvImageTest {
    // Coefficients are taken from jcolor.c in libjpeg.
    private static final int CSHIFT = 16;
    private static final int CYR = 19595;
    private static final int CYG = 38470;
    private static final int CYB = 7471;
    private static final int CUR = -11059;
    private static final int CUG = -21709;
    private static final int CUB = 32768;
    private static final int CVR = 32768;
    private static final int CVG = -27439;
    private static final int CVB = -5329;

    private static final String TAG = "YuvImageTest";

    private static final int[] FORMATS = { ImageFormat.NV21, ImageFormat.YUY2,
                                           ImageFormat.YCBCR_P010, ImageFormat.YUV_420_888 };
    private static final int[] JPEG_FORMATS = { ImageFormat.NV21, ImageFormat.YUY2 };

    private static final int WIDTH = 256;
    private static final int HEIGHT = 128;

    private static final int[] RECT_WIDTHS = { 128, 124, 123 };
    private static final int[] RECT_HEIGHTS = { 64, 60, 59 };

    // Various rectangles:
    // mRects[0] : a normal one.
    // mRects[1] : same size to that of mRects[1], but its left-top point is shifted
    // mRects[2] : sides are not multiples of 16
    // mRects[3] : the left-top point is at an odd position
    private static final Rect[] RECTS = { new Rect(0, 0, 0 + RECT_WIDTHS[0],  0 + RECT_HEIGHTS[0]),
            new Rect(10, 10, 10 + RECT_WIDTHS[0], 10 + RECT_HEIGHTS[0]),
            new Rect(0, 0, 0 + RECT_WIDTHS[1], 0 + RECT_HEIGHTS[1]),
            new Rect(11, 11, 11 + RECT_WIDTHS[1], 11 + RECT_HEIGHTS[1]) };

    // Two rectangles of same size but at different positions
    private static final Rect[] RECTS_SHIFTED = { RECTS[0], RECTS[1] };

    // A rect whose side lengths are odd.
    private static final Rect RECT_ODD_SIDES = new Rect(10, 10, 10 + RECT_WIDTHS[2],
            10 + RECT_HEIGHTS[2]);

    private static final int[] PADDINGS = { 0, 32 };

    // There are three color components and
    // each should be within a square difference of 15 * 15.
    private static final int MSE_MARGIN = 3 * (15 * 15);

    private Bitmap[] mTestBitmaps = new Bitmap[1];

    @Test
    public void testYuvImage() {
        int width = 100;
        int height = 100;
        byte[] yuv = new byte[width * height * 2];
        YuvImage image;

        // normal case: test that the required formats are all supported
        for (int i = 0; i < FORMATS.length; ++i) {
            try {
                new YuvImage(yuv, FORMATS[i], width, height, null);
            } catch (Exception e) {
                Log.e(TAG, "unexpected exception", e);
                fail("unexpected exception");
            }
        }

        // normal case: test that default strides are returned correctly
        for (int i = 0; i < FORMATS.length; ++i) {
            int[] expected = null;
            int[] actual = null;
            switch (FORMATS[i]) {
                case ImageFormat.NV21:
                    expected = new int[]{width, width};
                    break;
                case ImageFormat.YCBCR_P010:
                    expected = new int[]{width * 2, width * 2};
                    break;
                case ImageFormat.YUV_420_888:
                    expected = new int[]{width, (width + 1) / 2, (width + 1) / 2};
                    break;
                case ImageFormat.YUY2:
                    expected = new int[]{width * 2};
                    break;
                default:
                    // We shouldn't hit here.
            }

            try {
                image = new YuvImage(yuv, FORMATS[i], width, height, null);
                actual = image.getStrides();
                assertTrue("default strides not calculated correctly",
                        Arrays.equals(expected, actual));
            } catch (Exception e) {
                Log.e(TAG, "unexpected exception", e);
                fail("unexpected exception");
            }
        }

        // abnormal case: pass a null ColorSpace, should throw IllegalArgumentException
        try{
            image = new YuvImage(yuv, FORMATS[0], width, height, null, null);
            fail("not catching hdr empty");
        } catch (IllegalArgumentException e){
            // expected
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testYuvImageNegativeWidth(){
        new YuvImage(new byte[100 * 100 * 2], FORMATS[0], -1, 100, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testYuvImageNegativeHeight(){
        new YuvImage(new byte[100 * 100 * 2], FORMATS[0], 100, -1, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testYuvImageNullArray(){
        new YuvImage(null, FORMATS[0], 100, 100, null);
   }

    @Test
    public void testCompressYuvToJpeg() {
        generateTestBitmaps(WIDTH, HEIGHT);

        // test if handling compression parameters correctly
        verifyParameters();

        // test various cases by varing
        // <ImageFormat, Bitmap, HasPaddings, Rect>
        for (int i = 0; i < JPEG_FORMATS.length; ++i) {
            for (int j = 0; j < mTestBitmaps.length; ++j) {
                for (int k = 0; k < PADDINGS.length; ++k) {
                    YuvImage image = generateYuvImage(JPEG_FORMATS[i],
                        mTestBitmaps[j], PADDINGS[k], null);
                    for (int l = 0; l < RECTS.length; ++l) {

                        // test compressing the same rect in
                        // mTestBitmaps[j] and image.
                        compressRects(mTestBitmaps[j], image,
                                RECTS[l], RECTS[l]);
                    }

                    // test compressing different rects in
                    // mTestBitmap[j] and image.
                    compressRects(mTestBitmaps[j], image, RECTS_SHIFTED[0],
                            RECTS_SHIFTED[1]);

                    // test compressing a rect whose side lengths are odd.
                    compressOddRect(mTestBitmaps[j], image, RECT_ODD_SIDES);
                }
            }
        }

    }

    @Test
    public void testGetHeight() {
        generateTestBitmaps(WIDTH, HEIGHT);
        YuvImage image = generateYuvImage(ImageFormat.YUY2, mTestBitmaps[0], 0, null);
        assertEquals(mTestBitmaps[0].getHeight(), image.getHeight());
        assertEquals(mTestBitmaps[0].getWidth(), image.getWidth());
    }

    @Test
    public void testGetColorSpace() {
        generateTestBitmaps(WIDTH, HEIGHT);
        ColorSpace defaultColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        ColorSpace expectedColorSpace = ColorSpace.get(ColorSpace.Named.BT709);
        YuvImage imageSRGB = generateYuvImage(ImageFormat.YUY2, mTestBitmaps[0], 0, null);
        YuvImage imageBT709 = generateYuvImage(
                ImageFormat.YUY2, mTestBitmaps[0], 0, expectedColorSpace);
        assertEquals(defaultColorSpace, imageSRGB.getColorSpace());
        assertEquals(expectedColorSpace, imageBT709.getColorSpace());
    }

    @Test
    public void testGetYuvData() {
        generateTestBitmaps(WIDTH, HEIGHT);
        int width = mTestBitmaps[0].getWidth();
        int height = mTestBitmaps[0].getHeight();
        int stride = width;
        int[] argb = new int[stride * height];
        mTestBitmaps[0].getPixels(argb, 0, stride, 0, 0, width, height);
        byte[] yuv = convertArgbsToYuvs(argb, stride, height, ImageFormat.NV21);
        int[] strides = new int[] {
                stride, stride
        };
        YuvImage image = new YuvImage(yuv, ImageFormat.NV21, width, height, strides);
        assertEquals(yuv, image.getYuvData());
    }

    @Test
    public void testGetYuvFormat() {
        generateTestBitmaps(WIDTH, HEIGHT);
        YuvImage image = generateYuvImage(ImageFormat.YUY2, mTestBitmaps[0], 0, null);
        assertEquals(ImageFormat.YUY2, image.getYuvFormat());
    }

    @Test
    public void testCompressYuvToJpegRWithBadInputs() {
        String hdrInput = Utils.obtainPath(R.raw.raw_p010_image, 0);
        String sdrInput = Utils.obtainPath(R.raw.raw_yuv420_image, 0);

        final int width = 1280;
        final int height = 720;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            byte[] emptyData = new byte[0];
            byte[] hdrData = Files.readAllBytes(Paths.get(hdrInput));
            byte[] sdrData = Files.readAllBytes(Paths.get(sdrInput));

            YuvImage emptyHdr = new YuvImage(
                    emptyData, ImageFormat.YCBCR_P010, width, height, null);
            YuvImage emptySdr = new YuvImage(
                    emptyData, ImageFormat.YUV_420_888, width, height, null);
            YuvImage supportedHdr = new YuvImage(
                    hdrData, ImageFormat.YCBCR_P010, width, height, null);
            YuvImage supportedSdr = new YuvImage(
                    sdrData, ImageFormat.YUV_420_888, width, height, null);
            YuvImage unsupportedHdr = new YuvImage(
                    hdrData, ImageFormat.YUV_420_888, width, height, null);
            YuvImage unsupportedSdr = new YuvImage(
                    sdrData, ImageFormat.NV21, width, height, null);
            YuvImage sdrWithDifferentRes = new YuvImage(
                    sdrData, ImageFormat.YUV_420_888, 720, 480, null);
            YuvImage hdrWithNotSupportedColorSpace = new YuvImage(
                    hdrData, ImageFormat.YCBCR_P010, 720, 480, null,
                    ColorSpace.get(ColorSpace.Named.CIE_LAB));
            YuvImage sdrWithNotSupportedColorSpace = new YuvImage(
                    sdrData, ImageFormat.YUV_420_888, 720, 480, null,
                    ColorSpace.get(ColorSpace.Named.BT709));

            // abnormal case: hdr is empty
            try{
                emptyHdr.compressToJpegR(supportedSdr, 90, stream);
                fail("not catching hdr empty");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: sdr is empty
            try{
                supportedHdr.compressToJpegR(emptySdr, 90, stream);
                fail("not catching sdr empty");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: sdr is unsupported color format
            try{
                supportedHdr.compressToJpegR(unsupportedSdr, 90, stream);
                fail("not catching sdr is unsupported color format");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: sdr is null
            try{
                supportedHdr.compressToJpegR(null, 90, stream);
                fail("not catching sdr is null");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: quality < 0
            try{
                supportedHdr.compressToJpegR(supportedSdr, -1, stream);
                fail("not catching illegal compression quality");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: quality > 100
            try{
                supportedHdr.compressToJpegR(supportedSdr, 101, stream);
                fail("not catching illegal compression quality");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: stream is null
            try{
                supportedHdr.compressToJpegR(supportedSdr, 90, null);
                fail("not catching null stream");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: resolution mismatch
            try{
                supportedHdr.compressToJpegR(sdrWithDifferentRes, 90, stream);
                fail("not catching resolution mismatch");
            } catch (IllegalArgumentException e){
                // expected
            }

            // abnormal case: not supported color space
            try{
                supportedHdr.compressToJpegR(sdrWithNotSupportedColorSpace, 90, stream);
                fail("not catching resolution mismatch");
            } catch (IllegalArgumentException e){
                // expected
            }
            try{
                hdrWithNotSupportedColorSpace.compressToJpegR(supportedSdr, 90, stream);
                fail("not catching resolution mismatch");
            } catch (IllegalArgumentException e){
                // expected
            }
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception", e);
            fail("unexpected exception");
        }
    }

    @Test
    public void testCompressYuvToJpegR() {
        String hdrInput = Utils.obtainPath(R.raw.raw_p010_image, 0);
        String sdrInput = Utils.obtainPath(R.raw.raw_yuv420_image, 0);

        final int width = 1280;
        final int height = 720;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            byte[] hdrData = Files.readAllBytes(Paths.get(hdrInput));
            byte[] sdrData = Files.readAllBytes(Paths.get(sdrInput));

            YuvImage hdrImage = new YuvImage(hdrData, ImageFormat.YCBCR_P010,
                                             width, height, null,
                                             ColorSpace.get(ColorSpace.Named.BT2020_HLG));
            YuvImage sdrImage = new YuvImage(sdrData, ImageFormat.YUV_420_888,
                                             width, height, null);

            assertTrue("Fail in JPEG/R compression.",
                    hdrImage.compressToJpegR(sdrImage, 90, stream));
            byte[] jpegRData = stream.toByteArray();
            Bitmap decoded = BitmapFactory.decodeByteArray(jpegRData, 0, jpegRData.length);
            assertNotNull(decoded);
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception", e);
            fail("unexpected exception");
        }
    }

    @ApiTest(apis = {"android.graphics.YuvImage#compressToJpegR"})
    @RequiresFlagsEnabled(Flags.FLAG_YUV_IMAGE_COMPRESS_TO_ULTRA_HDR)
    @Test
    public void testCompressYuvToJpegRWithExif() {
        String hdrInput = Utils.obtainPath(R.raw.raw_p010_image, 0);
        String sdrInput = Utils.obtainPath(R.raw.raw_yuv420_image, 0);
        String exifInput = Utils.obtainPath(R.raw.sample_exif, 0);

        final int width = 1280;
        final int height = 720;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            byte[] hdrData = Files.readAllBytes(Paths.get(hdrInput));
            byte[] sdrData = Files.readAllBytes(Paths.get(sdrInput));
            byte[] exifData = Files.readAllBytes(Paths.get(exifInput));

            YuvImage hdrImage = new YuvImage(hdrData, ImageFormat.YCBCR_P010,
                width, height, null,
                ColorSpace.get(ColorSpace.Named.BT2020_HLG));
            YuvImage sdrImage = new YuvImage(sdrData, ImageFormat.YUV_420_888,
                width, height, null);

            assertTrue("Fail in JPEG/R compression.",
                    hdrImage.compressToJpegR(sdrImage, 90, stream, exifData));
            byte[] jpegRData = stream.toByteArray();
            Bitmap decoded = BitmapFactory.decodeByteArray(jpegRData, 0, jpegRData.length);
            assertNotNull(decoded);

            ExifInterface exifInterface = new ExifInterface(
                    new ByteArrayInputStream(stream.toByteArray()));
            assertEquals(exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH),
                    String.valueOf(width));
            assertEquals(exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH),
                    String.valueOf(height));
            assertEquals(exifInterface.getAttribute(ExifInterface.TAG_DATETIME),
                    "2022:11:22 23:05:32");
            assertEquals(exifInterface.getAttribute(ExifInterface.TAG_MAKE),
                    "Google");
            assertEquals(exifInterface.getAttribute(ExifInterface.TAG_MODEL),
                    "Cuttlefish GMS x86_64");
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception", e);
            fail("unexpected exception");
        }
    }

    @Test
    public void testCompressYuvToJpegRWithStrides() throws Exception {
        String hdrInput = Utils.obtainPath(R.raw.raw_p010_image, 0);
        String sdrInput = Utils.obtainPath(R.raw.raw_yuv420_image, 0);

        final int stride = 1280;
        final int padding = 20;
        final int width = stride - padding;
        final int height = 720;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        byte[] hdrData = Files.readAllBytes(Paths.get(hdrInput));
        byte[] sdrData = Files.readAllBytes(Paths.get(sdrInput));

        YuvImage hdrImage = new YuvImage(hdrData, ImageFormat.YCBCR_P010,
                width, height, new int[] {stride * 2, stride * 2},
                ColorSpace.get(ColorSpace.Named.BT2020_HLG));
        YuvImage sdrImage = new YuvImage(sdrData, ImageFormat.YUV_420_888,
                width, height, new int[] {stride, stride / 2, stride / 2});

        assertTrue("Fail in JPEG/R compression.",
                hdrImage.compressToJpegR(sdrImage, 90, stream));
        byte[] jpegRData = stream.toByteArray();
        Bitmap decoded = BitmapFactory.decodeByteArray(jpegRData, 0, jpegRData.length);
        assertNotNull(decoded);
        assertEquals(decoded.getWidth(), width);
    }

    private void generateTestBitmaps(int width, int height) {
        Bitmap dst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(dst);

        // mTestBitmap[0] = scaled testimage.jpg
        Resources res = InstrumentationRegistry.getTargetContext().getResources();
        Bitmap src = BitmapFactory.decodeResource(res, R.drawable.testimage);
        c.drawBitmap(src, null, new Rect(0, 0, WIDTH, HEIGHT), null);
        mTestBitmaps[0] = dst;
    }

    // Generate YuvImage based on the content in bitmap. If paddings > 0, the
    // strides of YuvImage are calculated by adding paddings to bitmap.getWidth().
    // If colorSpace is null, this method will return a YuvImage with the default
    // ColorSpace (SRGB). Otherwise, it will return a YuvImage with configured ColorSpace.
    private YuvImage generateYuvImage(int format, Bitmap bitmap, int paddings,
                ColorSpace colorSpace) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int stride = width + paddings;

        YuvImage image = null;
        int[] argb = new int [stride * height];
        bitmap.getPixels(argb, 0, stride, 0, 0, width, height);
        byte[] yuv = convertArgbsToYuvs(argb, stride, height, format);

        int[] strides = null;
        if (format == ImageFormat.NV21) {
            strides = new int[] {stride, stride};
        } else if (format == ImageFormat.YUY2) {
           strides = new int[] {stride * 2};
        }

        if (colorSpace != null) {
            image = new YuvImage(yuv, format, width, height, strides, colorSpace);
        } else {
            image = new YuvImage(yuv, format, width, height, strides);
        }
        return image;
    }

    // Compress rect1 in testBitmap and rect2 in image.
    // Then, compare the two resutls to check their MSE.
    private void compressRects(Bitmap testBitmap, YuvImage image,
            Rect rect1, Rect rect2) {
        Bitmap expected = null;
        Bitmap actual = null;
        boolean sameRect = rect1.equals(rect2) ? true : false;

        Rect actualRect = new Rect(rect2);
        actual = compressDecompress(image, actualRect);

        Rect expectedRect = sameRect ? actualRect : rect1;
        expected = Bitmap.createBitmap(testBitmap, expectedRect.left, expectedRect.top,
                expectedRect.width(), expectedRect.height());
        BitmapUtils.assertBitmapsMse(expected, actual, MSE_MARGIN, sameRect, false);
    }

    // Compress rect in image.
    // If side lengths of rect are odd, the rect might be modified by image,
    // We use the modified one to get pixels from testBitmap.
    private void compressOddRect(Bitmap testBitmap, YuvImage image,
            Rect rect) {
        Bitmap expected = null;
        Bitmap actual = null;
        actual = compressDecompress(image, rect);

        Rect newRect = rect;
        expected = Bitmap.createBitmap(testBitmap, newRect.left, newRect.top,
              newRect.width(), newRect.height());

        BitmapUtils.assertBitmapsMse(expected, actual, MSE_MARGIN, true, false);
    }

    // Compress rect in image to a jpeg and then decode the jpeg to a bitmap.
    private Bitmap compressDecompress(YuvImage image, Rect rect) {
        Bitmap result = null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            boolean rt = image.compressToJpeg(rect, 90, stream);
            assertTrue("fail in compression", rt);
            byte[] jpegData = stream.toByteArray();
            result = BitmapFactory.decodeByteArray(jpegData, 0,
                    jpegData.length);
        } catch (Exception e){
            Log.e(TAG, "unexpected exception", e);
            fail("unexpected exception");
        }
        return result;
    }

    private byte[] convertArgbsToYuvs(int[] argb, int width, int height,
            int format) {
        byte[] yuv = new byte[width * height *
                ImageFormat.getBitsPerPixel(format)];
        if (format == ImageFormat.NV21) {
            int vuStart = width * height;
            byte[] yuvColor = new byte[3];
            for (int row = 0; row < height; ++row) {
                for (int col = 0; col < width; ++col) {
                    int idx = row * width + col;
                    argb2yuv(argb[idx], yuvColor);
                    yuv[idx] = yuvColor[0];
                    if ((row & 1) == 0 && (col & 1) == 0) {
                        int offset = row / 2 * width + col / 2 * 2;
                        yuv[vuStart + offset] = yuvColor[2];
                        yuv[vuStart + offset + 1] = yuvColor[1];
                    }
                }
            }
        } else if (format == ImageFormat.YUY2) {
            byte[] yuvColor0 = new byte[3];
            byte[] yuvColor1 = new byte[3];
            for (int row = 0; row < height; ++row) {
                for (int col = 0; col < width; col += 2) {
                    int idx = row * width + col;
                    argb2yuv(argb[idx], yuvColor0);
                    argb2yuv(argb[idx + 1], yuvColor1);
                    int offset = idx / 2 * 4;
                    yuv[offset] = yuvColor0[0];
                    yuv[offset + 1] = yuvColor0[1];
                    yuv[offset + 2] = yuvColor1[0];
                    yuv[offset + 3] = yuvColor0[2];
                }
            }
        }

        return yuv;
    }

    private void argb2yuv(int argb, byte[] yuv) {
        int r = Color.red(argb);
        int g = Color.green(argb);
        int b = Color.blue(argb);
        yuv[0] = (byte) ((CYR * r + CYG * g + CYB * b) >> CSHIFT);
        yuv[1] = (byte) (((CUR * r + CUG * g + CUB * b) >> CSHIFT) + 128);
        yuv[2] = (byte) (((CVR * r + CVG * g + CVB * b) >> CSHIFT) + 128);
    }

    private void verifyParameters() {
        int format = ImageFormat.NV21;
        int[] argb = new int[WIDTH * HEIGHT];
        mTestBitmaps[0].getPixels(argb, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        byte[] yuv = convertArgbsToYuvs(argb, WIDTH, HEIGHT, format);

        YuvImage image = new YuvImage(yuv, format, WIDTH, HEIGHT, null);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        // abnormal case: quality > 100
        try{
            Rect rect = new Rect(0, 0, WIDTH, HEIGHT);
            image.compressToJpeg(rect, 101, stream);
            fail("not catching illegal compression quality");
        } catch (IllegalArgumentException e){
            // expected
        }

        // abnormal case: quality < 0
        try{
            Rect rect = new Rect(0, 0, WIDTH, HEIGHT);
            image.compressToJpeg(rect, -1, stream);
            fail("not catching illegal compression quality");
        } catch (IllegalArgumentException e){
            // expected
        }

        // abnormal case: stream is null
        try {
            Rect rect = new Rect(0, 0, WIDTH, HEIGHT);
            image.compressToJpeg(rect, 80, null);
            fail("not catching null stream");
        } catch (IllegalArgumentException e){
            // expected
        }

        // abnormal case: rectangle not within the whole image
        try {
            Rect rect = new Rect(10, 0, WIDTH, HEIGHT + 5);
            image.compressToJpeg(rect, 80, stream);
            fail("not catching illegal rectangular region");
        } catch (IllegalArgumentException e){
            // expected
        }

        // abnormal case: unsupported color space
        try {
            Rect rect = new Rect(0, 0, WIDTH, HEIGHT);
            YuvImage image2 = new YuvImage(yuv, format, WIDTH, HEIGHT, null,
                    ColorSpace.get(ColorSpace.Named.BT709));
            image2.compressToJpeg(rect, 80, stream);
            fail("not catching illegal color Space");
        } catch (IllegalArgumentException e){
            // expected
        }
    }
}
